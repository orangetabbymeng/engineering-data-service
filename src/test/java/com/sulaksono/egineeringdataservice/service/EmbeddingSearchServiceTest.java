package com.sulaksono.egineeringdataservice.service;

import com.sulaksono.egineeringdataservice.dto.EmbeddingSearchRequest;
import com.sulaksono.egineeringdataservice.dto.EmbeddingSearchResult;
import com.sulaksono.egineeringdataservice.dto.SearchMode;
import com.sulaksono.egineeringdataservice.model.FileType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmbeddingSearchServiceTest {

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    private EmbeddingSearchService searchService;

    @BeforeEach
    void setUp() {
        searchService = new EmbeddingSearchService(embeddingService, jdbcTemplate);
    }

    @Test
    void defaultsToSemanticSearch() {
        when(embeddingService.generateEmbedding("authentication")).thenReturn(new float[]{1.0f, 2.0f});
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        searchService.search(request(null, null));

        verify(embeddingService).generateEmbedding("authentication");
        assertThat(capturedSql()).contains("embedding <=>").doesNotContain("websearch_to_tsquery");
    }

    @Test
    void lexicalSearchDoesNotGenerateAnEmbedding() {
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        searchService.search(request(SearchMode.LEXICAL, null));

        verify(embeddingService, never()).generateEmbedding(anyString());
        assertThat(capturedSql()).contains("websearch_to_tsquery('simple', :query)");
    }

    @Test
    void hybridSearchFusesRanksAndLimitsResults() {
        EmbeddingSearchResult shared = result(UUID.randomUUID(), "shared");
        EmbeddingSearchResult semanticOnly = result(UUID.randomUUID(), "semantic");
        EmbeddingSearchResult lexicalOnly = result(UUID.randomUUID(), "lexical");
        when(embeddingService.generateEmbedding("authentication")).thenReturn(new float[]{1.0f});
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(shared, semanticOnly))
                .thenReturn(List.of(lexicalOnly, shared));

        List<EmbeddingSearchResult> results = searchService.search(request(SearchMode.HYBRID, 2));

        assertThat(results).extracting(EmbeddingSearchResult::id)
                .containsExactly(shared.id(), lexicalOnly.id());
        assertThat(results.getFirst().score()).isGreaterThan(results.get(1).score());
        verify(jdbcTemplate, times(2))
                .query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class));
    }

    private String capturedSql() {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sql.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
        return sql.getValue();
    }

    private static EmbeddingSearchRequest request(SearchMode mode, Integer limit) {
        return new EmbeddingSearchRequest(" authentication ", limit, null, null, false, mode);
    }

    private static EmbeddingSearchResult result(UUID id, String content) {
        return new EmbeddingSearchResult(
                id, 0.5, content, "Example.java", "src/Example.java", "demo", "1.0",
                FileType.JAVA, 0, 1, false);
    }
}
