package com.nyaalkaran.erp.poc.company;

import com.nyaalkaran.erp.poc.catalog.ErpModule;
import com.nyaalkaran.erp.poc.catalog.ErpModuleRepository;
import com.nyaalkaran.erp.poc.common.ApiException;
import com.nyaalkaran.erp.poc.common.CompanyRoles;
import com.nyaalkaran.erp.poc.common.PocProperties;
import com.nyaalkaran.erp.poc.common.SecurityConfig;
import com.nyaalkaran.erp.poc.common.TenantResourceNaming;
import com.nyaalkaran.erp.poc.company.BrandingService.BrandingView;
import com.nyaalkaran.erp.poc.company.CompanyResource.ResourceStatus;
import com.nyaalkaran.erp.poc.company.CompanyResource.ResourceType;
import com.nyaalkaran.erp.poc.subscription.SubscriptionEnums.EntitlementStatus;
import com.nyaalkaran.erp.poc.subscription.SubscriptionRepositories.CompanyEntitlementRepository;
import com.nyaalkaran.erp.poc.tenant.CompanyRealmIssuers;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * What the two portals need before and right after login.
 * <ul>
 *   <li>/api/v1/public/config and /login-discovery: no token. Company Login Code -> the company's realm and its
 *       branding (CD-15). Unknown codes get one generic error, so codes cannot be told apart from companies.</li>
 *   <li>/api/v1/company/me: a company-realm token. The verified company, its entitled modules (with "can this user
 *       open it"), the user and the branding.</li>
 * </ul>
 */
@RestController
@RequiredArgsConstructor
public class CompanyPortalController {

    public record PublicConfig(String keycloakUrl, String platformRealm, String platformClientId,
                               String companyClientId) {
    }

    public record Discovery(String companyName, String loginCode, String status, String realm, String issuer,
                            String clientId, BrandingView branding) {
    }

    public record ModuleView(String code, String gatewayToken, String name, String category, boolean accessible) {
    }

    public record UserView(String username, String email, String name, List<String> roles) {
    }

    public record CompanyContext(String companyCode, String legalName, String displayName, String loginCode,
                                 String status, List<ModuleView> modules, UserView user, BrandingView branding) {
    }

    private final CompanyRepository companyRepository;
    private final CompanyResourceRepository resourceRepository;
    private final CompanyEntitlementRepository entitlementRepository;
    private final ErpModuleRepository moduleRepository;
    private final BrandingService brandingService;
    private final PocProperties props;

    @GetMapping("/api/v1/public/config")
    public PublicConfig config() {
        PocProperties.Keycloak kc = props.keycloak();
        return new PublicConfig(kc.url(), kc.platformRealm(), "platform-console", "erp-web");
    }

    @GetMapping("/api/v1/public/login-discovery")
    @Transactional(readOnly = true)
    public Discovery discover(@RequestParam String loginCode) {
        String code = loginCode == null ? "" : loginCode.trim().toUpperCase(Locale.ROOT);
        Company company = code.matches("^[A-Z0-9]{4,12}$") ? companyRepository.findByLoginCode(code).orElse(null) : null;
        String realm = company == null ? null : TenantResourceNaming.realmName(company.getCompanyCode());
        boolean realmReady = realm != null && resourceRepository
                .findByResourceTypeAndResourceName(ResourceType.IDP_REALM, realm)
                .filter(r -> r.getStatus() == ResourceStatus.VERIFIED).isPresent();
        if (!realmReady) {
            throw new ApiException(HttpStatus.NOT_FOUND, "LOGIN_CODE_NOT_FOUND",
                    "We could not find an active workspace for this code. Check it with your administrator.");
        }
        return new Discovery(company.getDisplayName(), company.getLoginCode(), company.getStatus().name(), realm,
                props.keycloak().issuer(realm), "erp-web", brandingService.forCompany(company));
    }

    @GetMapping("/api/v1/company/me")
    @Transactional(readOnly = true)
    public CompanyContext me(@AuthenticationPrincipal Jwt jwt) {
        String companyCode = TenantResourceNaming.requireCompanyCode(jwt.getClaimAsString(CompanyRealmIssuers.TENANT_CLAIM));
        Company company = companyRepository.findByCompanyCode(companyCode)
                .orElseThrow(() -> ApiException.forbidden("UNKNOWN_COMPANY", "Unknown company"));
        List<String> roles = SecurityConfig.realmRoles(jwt).stream()
                .filter(r -> !r.startsWith("default-roles-") && !r.equals("offline_access") && !r.equals("uma_authorization"))
                .sorted().toList();
        boolean superAdmin = roles.contains(CompanyRoles.COMPANY_SUPER_ADMIN);
        List<Long> entitled = entitlementRepository.findByCompanyIdAndStatus(company.getId(), EntitlementStatus.ACTIVE)
                .stream().map(e -> e.getModuleId()).distinct().toList();
        List<ModuleView> modules = moduleRepository.findAllById(entitled).stream()
                .filter(m -> !m.isPlatformCore())
                .sorted(Comparator.comparingInt(ErpModule::getSortOrder))
                .map(m -> new ModuleView(m.getCode(), m.getGatewayToken(), m.getName(), m.getCategory(),
                        superAdmin || CompanyRoles.moduleRoles(m.getGatewayToken()).stream().anyMatch(roles::contains)))
                .toList();
        UserView user = new UserView(jwt.getClaimAsString("preferred_username"), jwt.getClaimAsString("email"),
                jwt.getClaimAsString("name"), roles);
        return new CompanyContext(company.getCompanyCode(), company.getLegalName(), company.getDisplayName(),
                company.getLoginCode(), company.getStatus().name(), modules, user, brandingService.forCompany(company));
    }
}
