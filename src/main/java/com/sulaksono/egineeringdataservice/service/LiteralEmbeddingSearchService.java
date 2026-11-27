package com.sulaksono.egineeringdataservice.service;

import com.sulaksono.egineeringdataservice.dto.EmbeddingSearchResult;
import com.sulaksono.egineeringdataservice.dto.LiteralSearchField;
import com.sulaksono.egineeringdataservice.dto.LiteralSearchRequest;
import com.sulaksono.egineeringdataservice.model.FileType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Types;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LiteralEmbeddingSearchService {

    private static final String SQL_PREFIX = """
            SELECT id, canonical_file_id, content, file_name, path, module, module_version,
                   file_type, chunk_idx, chunk_of, deprecated
              FROM engineering_reference.file_embeddings
             WHERE (:includeDeprecated OR deprecated IS NOT TRUE)
               AND""";
    private static final String SQL_SUFFIX = """
             ILIKE :pattern ESCAPE '\\'
             ORDER BY module, path, chunk_idx
             LIMIT :resultLimit
            """;
    private static final String CONTENT_SQL = SQL_PREFIX + " content" + SQL_SUFFIX;
    private static final String PATH_SQL = SQL_PREFIX + " path" + SQL_SUFFIX;
    private static final String FILE_NAME_SQL = SQL_PREFIX + " file_name" + SQL_SUFFIX;
    private static final String MODULE_SQL = SQL_PREFIX + " module" + SQL_SUFFIX;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public List<EmbeddingSearchResult> search(LiteralSearchRequest request) {
        String sql = sqlFor(request.effectiveField());
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("includeDeprecated", request.shouldIncludeDeprecated(), Types.BOOLEAN)
                .addValue("pattern", "%" + escapeLike(request.query().trim()) + "%", Types.VARCHAR)
                .addValue("resultLimit", request.effectiveLimit(), Types.INTEGER);

        return jdbcTemplate.query(sql, parameters, (rs, rowNum) -> new EmbeddingSearchResult(
                rs.getObject("id", UUID.class),
                rs.getObject("canonical_file_id", UUID.class),
                0.0,
                null,
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

    private static String sqlFor(LiteralSearchField field) {
        return switch (field) {
            case CONTENT -> CONTENT_SQL;
            case PATH -> PATH_SQL;
            case FILE_NAME -> FILE_NAME_SQL;
            case MODULE -> MODULE_SQL;
        };
    }

    private static String escapeLike(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
