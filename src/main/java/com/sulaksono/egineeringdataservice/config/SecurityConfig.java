package com.sulaksono.egineeringdataservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
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


@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Upload and semantic search: embedding-user + admins
                        .requestMatchers(HttpMethod.POST,
                                "/api/files/upload",
                                "/api/embeddings/search")
                        .hasAnyRole("embedding-user", "embedding-admin", "assistant-admin")

                        // Everything else under /api/**: admins only
                        .requestMatchers("/api/**")
                        .hasAnyRole("embedding-admin", "assistant-admin")

                        // Non-API endpoints (health, swagger, etc.)
                        .anyRequest().permitAll()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                );

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter c = new JwtAuthenticationConverter();
        c.setJwtGrantedAuthoritiesConverter(keycloakRolesConverter());
        return c;
    }

    /**
     * Maps Keycloak roles to Spring Security authorities.
     * Reads from:
     * - realm_access.roles
     * - resource_access.<client>.roles (all clients)
     *
     * Produces authorities like: ROLE_embedding-admin, ROLE_assistant-admin, ROLE_embedding-user
     */
    @Bean
    public Converter<Jwt, Collection<GrantedAuthority>> keycloakRolesConverter() {
        return this::extractKeycloakAuthorities;
    }

    private Collection<GrantedAuthority> extractKeycloakAuthorities(Jwt jwt) {
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