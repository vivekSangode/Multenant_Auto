package com.nyaalkaran.erp.poc.tenant;

import com.nyaalkaran.erp.poc.common.ApiException;
import com.nyaalkaran.erp.poc.common.CompanyRoles;
import com.nyaalkaran.erp.poc.common.SecurityConfig;
import com.nyaalkaran.erp.poc.common.TenantResourceNaming;
import com.nyaalkaran.erp.poc.company.CompanyStatus;
import com.nyaalkaran.erp.poc.subscription.RuntimeCacheService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.Locale;

/**
 * The gateway-side checks of master §10.5/§10.6/§14.2, applied in-process for the POC:
 * <ol>
 *   <li>company = `tenant_id` of the verified company-realm JWT; Spring Security already required the issuer to be
 *       a registered company realm whose company equals `tenant_id` (CompanyRealmIssuers). No client header is
 *       trusted;</li>
 *   <li>TENANT_STATUS must be ACTIVE (503 COMPANY_NOT_READY, 403 COMPANY_SUSPENDED / DISABLED / ARCHIVED);</li>
 *   <li>the module must be in COMPANY_MODULES (403 MODULE_NOT_ENTITLED);</li>
 *   <li>the token holds COMPANY_SUPER_ADMIN or a role of the module, e.g. CRM_USER (403 MODULE_ACCESS_DENIED).</li>
 * </ol>
 * Fail-closed: a missing key denies; a Redis error returns 503, never "allow".
 */
@Component
@RequiredArgsConstructor
public class TenantAccessInterceptor implements HandlerInterceptor {

    private static final String PREFIX = "/api/v1/tenant/";

    private final StringRedisTemplate redis;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken token)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "TENANT_REQUIRED", "A company-realm token is required");
        }
        String companyCode = TenantResourceNaming.requireCompanyCode(
                token.getToken().getClaimAsString(CompanyRealmIssuers.TENANT_CLAIM));
        String moduleCode = moduleFromPath(request.getRequestURI());
        String gatewayToken = moduleCode.toUpperCase(Locale.ROOT);

        String status;
        Boolean entitled;
        try {
            status = redis.opsForValue().get(RuntimeCacheService.STATUS_KEY + companyCode);
            entitled = redis.opsForSet().isMember(RuntimeCacheService.MODULES_KEY + companyCode, gatewayToken);
        } catch (DataAccessException ex) {
            throw ApiException.unavailable("ENTITLEMENT_CACHE_UNAVAILABLE", "Access cannot be verified right now");
        }
        if (status == null) {
            throw ApiException.unavailable("COMPANY_NOT_READY", "Company is not provisioned");
        }
        String denied = CompanyStatus.valueOf(status).accessDeniedCode();
        if (denied != null) {
            throw "COMPANY_NOT_READY".equals(denied)
                    ? ApiException.unavailable(denied, "Company is " + status)
                    : ApiException.forbidden(denied, "Company is " + status);
        }
        if (!Boolean.TRUE.equals(entitled)) {
            throw ApiException.forbidden("MODULE_NOT_ENTITLED", gatewayToken + " is not in the company's subscription");
        }
        List<String> roles = SecurityConfig.realmRoles(token.getToken());
        if (!roles.contains(CompanyRoles.COMPANY_SUPER_ADMIN)
                && CompanyRoles.moduleRoles(gatewayToken).stream().noneMatch(roles::contains)) {
            throw ApiException.forbidden("MODULE_ACCESS_DENIED", "The user holds no " + gatewayToken + " role");
        }
        TenantContext.set(new TenantContext.Tenant(companyCode, moduleCode));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception ex) {
        TenantContext.clear();
    }

    private static String moduleFromPath(String uri) {
        int start = uri.indexOf(PREFIX);
        if (start < 0) {
            throw ApiException.badRequest("INVALID_TENANT_PATH", "Not a tenant path");
        }
        String rest = uri.substring(start + PREFIX.length());
        int slash = rest.indexOf('/');
        return slash < 0 ? rest : rest.substring(0, slash);
    }
}
