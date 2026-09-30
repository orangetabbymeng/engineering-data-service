package com.sulaksono.egineeringdataservice.controller;

import com.sulaksono.egineeringdataservice.dto.EmbeddingSearchRequest;
import com.sulaksono.egineeringdataservice.dto.EmbeddingSearchResult;
import com.sulaksono.egineeringdataservice.model.FileType;
import com.sulaksono.egineeringdataservice.service.EmbeddingSearchService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EmbeddingSearchController.class)
@Import(EmbeddingSearchControllerTest.TestSecurityConfig.class)
class EmbeddingSearchControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private EmbeddingSearchService searchService;

    @TestConfiguration
    @EnableWebSecurity
    @EnableMethodSecurity
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/embeddings/search")
                            .hasAnyRole("embedding-user", "embedding-admin", "assistant-admin")
                            .anyRequest().permitAll())
                    .exceptionHandling(ex -> ex
                            .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                            .accessDeniedHandler((request, response, exception) ->
                                    response.sendError(HttpServletResponse.SC_FORBIDDEN)))
                    .build();
        }
    }

    @Test
    @WithMockUser(roles = "embedding-user")
    void returnsSemanticSearchResults() throws Exception {
        UUID id = UUID.randomUUID();
        when(searchService.search(any())).thenReturn(List.of(
                new EmbeddingSearchResult(id, 0.91, "result content", "Example.java",
                        "src/Example.java", "demo", "1.0", FileType.JAVA, 0, 1, false)));

        mvc.perform(post("/api/embeddings/search")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"query":"where is authentication configured?","limit":5,"module":"demo"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].score").value(0.91))
                .andExpect(jsonPath("$[0].content").value("result content"));

        verify(searchService).search(any(EmbeddingSearchRequest.class));
    }

    @Test
    @WithMockUser(roles = "embedding-user")
    void rejectsBlankQuery() throws Exception {
        mvc.perform(post("/api/embeddings/search")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"query\":\"   \"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(searchService);
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(post("/api/embeddings/search")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"query\":\"authentication\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "wrong-role")
    void rejectsUnauthorizedRole() throws Exception {
        mvc.perform(post("/api/embeddings/search")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"query\":\"authentication\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(searchService);
    }
}
