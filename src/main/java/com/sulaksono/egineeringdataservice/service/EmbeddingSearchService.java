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
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmbeddingSearchService {

    private static final String SEARCH_SQL = """
            SELECT id,
                   1 - (embedding <=> CAST(:queryEmbedding AS vector)) AS score,
                   content,
                   file_name,
                   path,
                   module,
                   module_version,
                   file_type,
                   chunk_idx,
                   chunk_of,
                   deprecated
              FROM engineering_reference.file_embeddings
             WHERE (:includeDeprecated OR deprecated = false)
               AND (:module IS NULL OR module = :module)
               AND (:moduleVersion IS NULL OR module_version = :moduleVersion)
             ORDER BY embedding <=> CAST(:queryEmbedding AS vector)
             LIMIT :resultLimit
            """;

    private final EmbeddingService embeddingService;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public List<EmbeddingSearchResult> search(EmbeddingSearchRequest request) {
        float[] queryEmbedding = embeddingService.generateEmbedding(request.query().trim());

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("queryEmbedding", toVectorLiteral(queryEmbedding), Types.VARCHAR)
                .addValue("includeDeprecated", request.shouldIncludeDeprecated(), Types.BOOLEAN)
                .addValue("module", normalize(request.module()), Types.VARCHAR)
                .addValue("moduleVersion", normalize(request.moduleVersion()), Types.VARCHAR)
                .addValue("resultLimit", request.effectiveLimit(), Types.INTEGER);

        return jdbcTemplate.query(SEARCH_SQL, parameters, (rs, rowNum) ->
                new EmbeddingSearchResult(
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
