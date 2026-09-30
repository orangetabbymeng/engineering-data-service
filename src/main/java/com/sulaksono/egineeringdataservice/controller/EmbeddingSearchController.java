package com.sulaksono.egineeringdataservice.controller;

import com.sulaksono.egineeringdataservice.dto.EmbeddingSearchRequest;
import com.sulaksono.egineeringdataservice.dto.EmbeddingSearchResult;
import com.sulaksono.egineeringdataservice.service.EmbeddingSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/embeddings")
@RequiredArgsConstructor
public class EmbeddingSearchController {

    private final EmbeddingSearchService searchService;

    @PostMapping("/search")
    @Operation(
            summary = "Semantic search over embedded engineering content",
            description = "Embeds the query and returns the nearest stored chunks using cosine similarity."
    )
    @SecurityRequirement(name = "keycloak")
    @PreAuthorize("hasAnyRole('embedding-user','embedding-admin','assistant-admin')")
    public List<EmbeddingSearchResult> search(@Valid @RequestBody EmbeddingSearchRequest request) {
        return searchService.search(request);
    }
}
