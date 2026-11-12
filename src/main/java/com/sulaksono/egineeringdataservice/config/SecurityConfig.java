package com.sulaksono.egineeringdataservice.config;

import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {

    private static final String EMBEDDING_USER = "embedding-user";
    private static final String EMBEDDING_ADMIN = "embedding-admin";
    private static final String ASSISTANT_ADMIN = "assistant-admin";

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter) {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Upload and read-only knowledge APIs: embedding-user + admins
                        .requestMatchers(HttpMethod.POST,
                                "/api/files/upload",
                                "/api/embeddings/search",
                                "/api/embeddings/literal-search",
                                "/api/embeddings/generate")
                        .hasAnyRole(EMBEDDING_USER, EMBEDDING_ADMIN, ASSISTANT_ADMIN)
                        .requestMatchers(HttpMethod.GET, "/api/canonical-files/**")
                        .hasAnyRole(EMBEDDING_USER, EMBEDDING_ADMIN, ASSISTANT_ADMIN)

                        // Everything else under /api/**: admins only
                        .requestMatchers("/api/**")
                        .hasAnyRole(EMBEDDING_ADMIN, ASSISTANT_ADMIN)

                        // Non-API endpoints (health, swagger, etc.)
                        .anyRequest().permitAll()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                );

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter(
            Converter<@NonNull Jwt, @NonNull Collection<GrantedAuthority>> keycloakRolesConverter) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(keycloakRolesConverter);
        return converter;
    }

    /**
     * Maps Keycloak roles to Spring Security authorities.
     * Reads from:
     * - realm_access.roles
     * - resource_access.&lt;client&gt;.roles (all clients)
     * <p>
     * Produces authorities like: ROLE_embedding-admin, ROLE_assistant-admin, ROLE_embedding-user
     */
    @Bean
    public Converter<@NonNull Jwt, @NonNull Collection<GrantedAuthority>> keycloakRolesConverter() {
        return this::extractKeycloakAuthorities;
    }

    private @NonNull Collection<GrantedAuthority> extractKeycloakAuthorities(@NonNull Jwt jwt) {
        Set<GrantedAuthority> authorities = new HashSet<>();
        addRolesFromAccessClaim(jwt.getClaim("realm_access"), authorities);
        addResourceRoles(jwt.getClaim("resource_access"), authorities);
        return authorities;
    }

    private void addResourceRoles(Object resourceAccessClaim,
                                  Set<GrantedAuthority> authorities) {
        if (resourceAccessClaim instanceof Map<?, ?> resourceAccess) {
            resourceAccess.values().forEach(
                    accessClaim -> addRolesFromAccessClaim(accessClaim, authorities));
        }
    }

    private void addRolesFromAccessClaim(Object accessClaim,
                                         Set<GrantedAuthority> authorities) {
        if (accessClaim instanceof Map<?, ?> access) {
            addRoles(access.get("roles"), authorities);
        }
    }

    private void addRoles(Object rolesClaim,
                          Set<GrantedAuthority> authorities) {
        if (rolesClaim instanceof Collection<?> roles) {
            roles.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .forEach(authorities::add);
        }
    }
}