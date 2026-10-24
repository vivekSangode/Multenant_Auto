package com.nyaalkaran.erp.poc.common;

import com.nyaalkaran.erp.poc.tenant.CompanyRealmIssuers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.security.config.Customizer;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * POC security: every API is a JWT resource server (master §14).
 * <ul>
 *   <li>/api/v1/platform/** : only tokens of the platform realm `erp-platform` (issuer + signature + audience),
 *       role PLATFORM_SUPER_ADMIN from `realm_access.roles`. Platform authority is never read from another
 *       issuer (PHASE_01 §6.5.1 rule 3). Target adds an ACTIVE tbl_platform_admin row (master §8.1).</li>
 *   <li>/api/v1/tenant/** : only tokens of a registered company realm `co-<n>` whose `tenant_id` equals the
 *       company of the issuer (see CompanyRealmIssuers); then TenantAccessInterceptor applies the status,
 *       entitlement and module-role checks. Platform tokens are refused here (§14.3 rule 2).</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain platformApi(HttpSecurity http, PocProperties props) throws Exception {
        stateless(http)
                .securityMatcher("/api/v1/platform/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("PLATFORM_SUPER_ADMIN"))
                .oauth2ResourceServer(o -> o.jwt(jwt -> jwt.decoder(platformDecoder(props))
                        .jwtAuthenticationConverter(realmRoleConverter())));
        return http.build();
    }

    /**
     * Internal registry API for the domain services (PHASE_01 §6.6.1 "S->>CP metadata"): M2M client-credentials
     * tokens of `erp-platform` whose `azp` is on the allow-list (master §15.2). User tokens never qualify.
     */
    @Bean
    @Order(0)
    SecurityFilterChain internalApi(HttpSecurity http, PocProperties props) throws Exception {
        List<String> allowed = props.internal().allowedClients();
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName("azp");
        converter.setJwtGrantedAuthoritiesConverter(jwt -> allowed.contains(jwt.getClaimAsString("azp"))
                ? List.of(new SimpleGrantedAuthority("INTERNAL_CALLER")) : List.of());
        stateless(http)
                .securityMatcher("/api/v1/internal/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().hasAuthority("INTERNAL_CALLER"))
                .oauth2ResourceServer(o -> o.jwt(jwt -> jwt.decoder(platformDecoder(props))
                        .jwtAuthenticationConverter(converter)));
        return http.build();
    }

    private static NimbusJwtDecoder platformDecoder(PocProperties props) {
        PocProperties.Keycloak kc = props.keycloak();
        String issuer = kc.issuer(kc.platformRealm());
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(issuer + "/protocol/openid-connect/certs").build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer), audience(kc.audience())));
        return decoder;
    }

    @Bean
    @Order(2)
    SecurityFilterChain tenantApi(HttpSecurity http, CompanyRealmIssuers companyRealms) throws Exception {
        stateless(http)
                .securityMatcher("/api/v1/tenant/**", "/api/v1/company/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.authenticationManagerResolver(
                        new JwtIssuerAuthenticationManagerResolver(companyRealms::authenticationManager)));
        return http.build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain everythingElse(HttpSecurity http) throws Exception {
        stateless(http)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/error", "/api/v1/public/**",
                                "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().denyAll());
        return http.build();
    }

    /** The portals (poc/frontend) run on their own origin and call the APIs from the browser. */
    @Bean
    CorsConfigurationSource corsConfigurationSource(PocProperties props) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(props.cors().allowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }

    private static HttpSecurity stateless(HttpSecurity http) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    }

    /** `aud` must contain the ERP audience (PHASE_01 §6.5.4; master §14.3 rule 4). */
    public static OAuth2TokenValidator<Jwt> audience(String audience) {
        return new JwtClaimValidator<List<String>>(JwtClaimNames.AUD, aud -> aud != null && aud.contains(audience));
    }

    /** Authorities = ROLE_ + realm roles of the verified token (CD-14); principal = preferred_username. */
    public static Converter<Jwt, AbstractAuthenticationToken> realmRoleConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName("preferred_username");
        converter.setJwtGrantedAuthoritiesConverter(jwt -> realmRoles(jwt).stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                .toList());
        return converter;
    }

    public static List<String> realmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        Object roles = realmAccess == null ? null : realmAccess.get("roles");
        return roles instanceof Collection<?> c ? c.stream().map(String::valueOf).toList() : List.of();
    }
}
