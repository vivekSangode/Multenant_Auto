package com.nyaalkaran.erp.poc.tenant;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nyaalkaran.erp.poc.common.PocProperties;
import com.nyaalkaran.erp.poc.common.SecurityConfig;
import com.nyaalkaran.erp.poc.company.Company;
import com.nyaalkaran.erp.poc.company.CompanyRepository;
import com.nyaalkaran.erp.poc.company.CompanyResource.ResourceStatus;
import com.nyaalkaran.erp.poc.company.CompanyResource.ResourceType;
import com.nyaalkaran.erp.poc.company.CompanyResourceRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.regex.Pattern;

/**
 * The issuer allow-list of the tenant API, driven by the registry (PHASE_01 §6.5.3 "Token validation"):
 * issuer `…/realms/co-<n>` -> the VERIFIED IDP_REALM row of tbl_company_resource -> company CMP_n.
 * Unknown issuer -> no AuthenticationManager -> 401. A known issuer gets a decoder for its own JWKS that also
 * requires `aud` = nyaalkaran-erp and `tenant_id` = the company of the issuer (master §14.1 token rules).
 * Only registered issuers are cached (30 s), so a realm verified by provisioning is accepted at once.
 */
@Component
public class CompanyRealmIssuers {

    public static final String TENANT_CLAIM = "tenant_id";
    private static final Pattern COMPANY_REALM = Pattern.compile("^co-[0-9]{1,8}$");

    private final CompanyRepository companyRepository;
    private final CompanyResourceRepository resourceRepository;
    private final PocProperties.Keycloak keycloak;
    private final String realmsPrefix;

    private final Cache<String, String> companyByIssuer = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(30)).maximumSize(10_000).build();
    private final Cache<String, AuthenticationManager> managers = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofHours(1)).maximumSize(10_000).build();

    public CompanyRealmIssuers(CompanyRepository companyRepository, CompanyResourceRepository resourceRepository,
                               PocProperties props) {
        this.companyRepository = companyRepository;
        this.resourceRepository = resourceRepository;
        this.keycloak = props.keycloak();
        this.realmsPrefix = keycloak.issuer("");
    }

    /** @return null for an issuer that is not a registered, VERIFIED company realm */
    public AuthenticationManager authenticationManager(String issuer) {
        String companyCode = companyFor(issuer);
        if (companyCode == null) {
            return null;
        }
        return managers.get(issuer + "|" + companyCode, k -> build(issuer, companyCode));
    }

    public String companyFor(String issuer) {
        String cached = companyByIssuer.getIfPresent(issuer);
        if (cached != null) {
            return cached;
        }
        String found = lookup(issuer);
        if (found != null) {
            companyByIssuer.put(issuer, found);
        }
        return found;
    }

    private String lookup(String issuer) {
        if (issuer == null || !issuer.startsWith(realmsPrefix)) {
            return null;
        }
        String realm = issuer.substring(realmsPrefix.length());
        if (!COMPANY_REALM.matcher(realm).matches()) {
            return null;
        }
        return resourceRepository.findByResourceTypeAndResourceName(ResourceType.IDP_REALM, realm)
                .filter(r -> r.getStatus() == ResourceStatus.VERIFIED)
                .flatMap(r -> companyRepository.findById(r.getCompanyId()))
                .map(Company::getCompanyCode)
                .orElse(null);
    }

    private AuthenticationManager build(String issuer, String companyCode) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(issuer + "/protocol/openid-connect/certs").build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                SecurityConfig.audience(keycloak.audience()),
                new JwtClaimValidator<String>(TENANT_CLAIM, companyCode::equals)));
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(decoder);
        provider.setJwtAuthenticationConverter(SecurityConfig.realmRoleConverter());
        return provider::authenticate;
    }
}
