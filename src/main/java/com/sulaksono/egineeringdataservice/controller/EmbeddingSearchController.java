package com.sulaksono.egineeringdataservice.controller;

import com.sulaksono.egineeringdataservice.dto.EmbeddingSearchRequest;
import com.sulaksono.egineeringdataservice.dto.EmbeddingSearchResult;
import com.sulaksono.egineeringdataservice.service.EmbeddingSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
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
            summary = "Search embedded engineering content",
            description = """
                    Supports semantic, lexical, and hybrid retrieval over stored chunks.

                    Recommended agent flow when the module or version is unknown:
                    1. Omit `module` and `moduleVersion` and run a broad `HYBRID` search.
                    2. Inspect `module` and `moduleVersion` in the returned result metadata.
                    3. Repeat the search with the discovered filters for more focused context.

                    Supplying only `module` searches all versions of that module. Supplying only
                    `moduleVersion` searches that version across all modules. `LEXICAL` is useful
                    for exact identifiers and error text and does not call the embedding provider.
                    """
    )
    @SecurityRequirement(name = "keycloak")
    @PreAuthorize("hasAnyRole('embedding-user','embedding-admin','assistant-admin')")
    public List<EmbeddingSearchResult> search(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Search request. Omit module filters to discover them from results.",
                    content = @Content(examples = {
                            @ExampleObject(
                                    name = "Discover module and version",
                                    summary = "Broad search when source metadata is unknown",
                                    value = """
                                            {
                                              "query": "Where are Keycloak roles mapped?",
                                              "mode": "HYBRID",
                                              "limit": 10
                                            }
                                            """
                            ),
                            @ExampleObject(
                                    name = "Refine discovered source",
                                    summary = "Follow-up search using metadata from broad results",
                                    value = """
                                            {
                                              "query": "How are realm and client roles mapped?",
                                              "mode": "HYBRID",
                                              "limit": 10,
                                              "module": "engineering-data-service",
                                              "moduleVersion": "1.5.1"
                                            }
                                            """
                            )
                    })
            )
            @Valid @RequestBody EmbeddingSearchRequest request) {
        return searchService.search(request);
    }
}
