package com.nyaalkaran.erp.tenant.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nyaalkaran.erp.tenant.TenantProperties;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;

import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

/**
 * issuer -> AuthenticationManager for {@code JwtIssuerAuthenticationManagerResolver} (PHASE_01 §6.6.2 steps 1-4):
 * the issuer must be a company realm `co-<n>` known to the registry; then signature (that realm's JWKS), expiry,
 * `aud` = nyaalkaran-erp and `tenant_id` = the issuer's company. Anything else -> null -> 401. Platform-realm tokens
 * never match, so platform identities cannot call business APIs (master §14.3 rule 2).
 */
public class CompanyRealmAuthenticationManagerResolver implements AuthenticationManagerResolver<String> {

    private static final Pattern COMPANY_REALM = Pattern.compile("^co-[0-9]{1,8}$");

    private final CompanyIssuerRegistry registry;
    private final String realmsPrefix;
    private final String audience;
    private final Cache<String, AuthenticationManager> managers = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofHours(1)).maximumSize(10_000).build();

    public CompanyRealmAuthenticationManagerResolver(CompanyIssuerRegistry registry, TenantProperties props) {
        this.registry = registry;
        this.realmsPrefix = props.keycloak().issuer("");
        this.audience = props.keycloak().audience();
    }

    @Override
    public AuthenticationManager resolve(String issuer) {
        if (issuer == null || !issuer.startsWith(realmsPrefix)
                || !COMPANY_REALM.matcher(issuer.substring(realmsPrefix.length())).matches()) {
            return null;
        }
        return registry.companyFor(issuer)
                .map(company -> managers.get(issuer + "|" + company, k -> build(issuer, company)))
                .orElse(null);
    }

    private AuthenticationManager build(String issuer, String companyCode) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(issuer + "/protocol/openid-connect/certs").build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD, aud -> aud != null && aud.contains(audience)),
                new JwtClaimValidator<String>(RealmRoles.TENANT_CLAIM, companyCode::equals)));
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName("preferred_username");
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(decoder);
        provider.setJwtAuthenticationConverter(converter);
        return provider::authenticate;
    }
}
