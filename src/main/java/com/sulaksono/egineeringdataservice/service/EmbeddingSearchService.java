package com.sulaksono.egineeringdataservice.service;

import com.sulaksono.egineeringdataservice.dto.EmbeddingSearchRequest;
import com.sulaksono.egineeringdataservice.dto.EmbeddingSearchResult;
import com.sulaksono.egineeringdataservice.model.FileType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Types;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmbeddingSearchService {

    private static final int HYBRID_RANK_CONSTANT = 60;
    private static final int HYBRID_CANDIDATE_MULTIPLIER = 3;

    private static final String RESULT_COLUMNS = """
            id, content, file_name, path, module, module_version, file_type,
            chunk_idx, chunk_of, deprecated
            """;

    private static final String FILTERS = """
            (CAST(:includeDeprecated AS boolean) OR deprecated = false)
            AND (CAST(:module AS varchar) IS NULL OR module = CAST(:module AS varchar))
            AND (CAST(:moduleVersion AS varchar) IS NULL
                 OR module_version = CAST(:moduleVersion AS varchar))
            """;

    private static final String SEMANTIC_SEARCH_SQL = """
            SELECT %s,
                   1 - (embedding <=> CAST(:queryEmbedding AS vector)) AS score
              FROM engineering_reference.file_embeddings
             WHERE %s
             ORDER BY embedding <=> CAST(:queryEmbedding AS vector)
             LIMIT :resultLimit
            """.formatted(RESULT_COLUMNS, FILTERS);

    private static final String LEXICAL_SEARCH_SQL = """
            SELECT %s,
                   ts_rank_cd(
                       to_tsvector('simple', coalesce(content, '')),
                       websearch_to_tsquery('simple', :query)
                   ) AS score
              FROM engineering_reference.file_embeddings
             WHERE %s
               AND to_tsvector('simple', coalesce(content, ''))
                   @@ websearch_to_tsquery('simple', :query)
             ORDER BY score DESC
             LIMIT :resultLimit
            """.formatted(RESULT_COLUMNS, FILTERS);

    private final EmbeddingService embeddingService;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public List<EmbeddingSearchResult> search(EmbeddingSearchRequest request) {
        return switch (request.effectiveMode()) {
            case SEMANTIC -> semanticSearch(request, request.effectiveLimit());
            case LEXICAL -> lexicalSearch(request, request.effectiveLimit());
            case HYBRID -> hybridSearch(request);
        };
    }

    private List<EmbeddingSearchResult> semanticSearch(EmbeddingSearchRequest request, int limit) {
        float[] queryEmbedding = embeddingService.generateEmbedding(request.query().trim());
        MapSqlParameterSource parameters = commonParameters(request, limit)
                .addValue("queryEmbedding", toVectorLiteral(queryEmbedding), Types.VARCHAR);
        return executeSearch(SEMANTIC_SEARCH_SQL, parameters);
    }

    private List<EmbeddingSearchResult> lexicalSearch(EmbeddingSearchRequest request, int limit) {
        MapSqlParameterSource parameters = commonParameters(request, limit)
                .addValue("query", request.query().trim(), Types.VARCHAR);
        return executeSearch(LEXICAL_SEARCH_SQL, parameters);
    }

    private List<EmbeddingSearchResult> hybridSearch(EmbeddingSearchRequest request) {
        int candidateLimit = request.effectiveLimit() * HYBRID_CANDIDATE_MULTIPLIER;
        List<EmbeddingSearchResult> semanticResults = semanticSearch(request, candidateLimit);
        List<EmbeddingSearchResult> lexicalResults = lexicalSearch(request, candidateLimit);
        return fuseResults(semanticResults, lexicalResults, request.effectiveLimit());
    }

    private MapSqlParameterSource commonParameters(EmbeddingSearchRequest request, int limit) {
        return new MapSqlParameterSource()
                .addValue("includeDeprecated", request.shouldIncludeDeprecated(), Types.BOOLEAN)
                .addValue("module", normalize(request.module()), Types.VARCHAR)
                .addValue("moduleVersion", normalize(request.moduleVersion()), Types.VARCHAR)
                .addValue("resultLimit", limit, Types.INTEGER);
    }

    private List<EmbeddingSearchResult> executeSearch(String sql, MapSqlParameterSource parameters) {
        return jdbcTemplate.query(sql, parameters, (rs, rowNum) -> new EmbeddingSearchResult(
                rs.getObject("id", UUID.class),
                rs.getDouble("score"),
                rs.getString("content"),
                rs.getString("file_name"),
                rs.getString("path"),
                rs.getString("module"),
                rs.getString("module_version"),
                FileType.valueOf(rs.getString("file_type")),
                rs.getInt("chunk_idx"),
                rs.getInt("chunk_of"),
                rs.getBoolean("deprecated")
        ));
    }

    private static List<EmbeddingSearchResult> fuseResults(
            List<EmbeddingSearchResult> semanticResults,
            List<EmbeddingSearchResult> lexicalResults,
            int limit) {
        Map<UUID, EmbeddingSearchResult> resultsById = new HashMap<>();
        Map<UUID, Double> scoresById = new HashMap<>();
        addRankedResults(semanticResults, resultsById, scoresById);
        addRankedResults(lexicalResults, resultsById, scoresById);

        Comparator<Map.Entry<UUID, Double>> ranking = Map.Entry
                .<UUID, Double>comparingByValue(Comparator.reverseOrder())
                .thenComparing(Map.Entry::getKey);
        return scoresById.entrySet().stream()
                .sorted(ranking)
                .limit(limit)
                .map(entry -> withScore(resultsById.get(entry.getKey()), entry.getValue()))
                .toList();
    }

    private static void addRankedResults(
            List<EmbeddingSearchResult> results,
            Map<UUID, EmbeddingSearchResult> resultsById,
            Map<UUID, Double> scoresById) {
        for (int rank = 0; rank < results.size(); rank++) {
            EmbeddingSearchResult result = results.get(rank);
            double reciprocalRank = 1.0 / (HYBRID_RANK_CONSTANT + rank + 1);
            resultsById.putIfAbsent(result.id(), result);
            scoresById.merge(result.id(), reciprocalRank, Double::sum);
        }
    }

    private static EmbeddingSearchResult withScore(EmbeddingSearchResult result, double score) {
        return new EmbeddingSearchResult(
                result.id(), score, result.content(), result.fileName(), result.path(),
                result.module(), result.moduleVersion(), result.fileType(), result.chunkIndex(),
                result.chunkCount(), result.deprecated());
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String toVectorLiteral(float[] vector) {
        StringBuilder literal = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                literal.append(',');
            }
            literal.append(vector[i]);
        }
        return literal.append(']').toString();
    }
}
