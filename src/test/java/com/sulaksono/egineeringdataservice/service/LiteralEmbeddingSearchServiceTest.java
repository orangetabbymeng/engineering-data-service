package com.sulaksono.egineeringdataservice.service;

import com.sulaksono.egineeringdataservice.dto.LiteralSearchField;
import com.sulaksono.egineeringdataservice.dto.LiteralSearchRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LiteralEmbeddingSearchServiceTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Test
    void searchesAllowlistedColumnAndEscapesWildcards() {
        when(jdbcTemplate.query(any(String.class), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());
        LiteralEmbeddingSearchService service = new LiteralEmbeddingSearchService(jdbcTemplate);

        service.search(new LiteralSearchRequest(" 100%_safe\\path ", LiteralSearchField.PATH, 5, false));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).query(sql.capture(), parameters.capture(), any(RowMapper.class));
        assertThat(sql.getValue()).contains("path ILIKE :pattern");
        assertThat(parameters.getValue().getValue("pattern")).isEqualTo("%100\\%\\_safe\\\\path%");
        assertThat(parameters.getValue().getValue("resultLimit")).isEqualTo(5);
    }
}
