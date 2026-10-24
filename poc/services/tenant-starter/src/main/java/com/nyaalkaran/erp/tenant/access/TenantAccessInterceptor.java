package com.nyaalkaran.erp.tenant.access;

import com.nyaalkaran.erp.tenant.TenantProperties;
import com.nyaalkaran.erp.tenant.context.TenantContext;
import com.nyaalkaran.erp.tenant.security.RealmRoles;
import com.nyaalkaran.erp.tenant.web.ServiceException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.regex.Pattern;

/**
 * The tenant gate of every business endpoint. Spring Security has already verified the company-realm token
 * (issuer, signature, audience, tenant_id = issuer's company); this runs the access rules and binds the request
 * to the company. No client header is ever trusted for the company (master §14.3 rule 1).
 */
public class TenantAccessInterceptor implements HandlerInterceptor {

    private static final Pattern COMPANY_CODE = Pattern.compile("^CMP_[0-9]{1,8}$");

    private final String gatewayToken;
    private final List<TenantAccessRule> rules;

    public TenantAccessInterceptor(TenantProperties props, List<TenantAccessRule> rules) {
        this.gatewayToken = props.gatewayToken();
        this.rules = rules;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken token)) {
            throw ServiceException.unauthorized("TENANT_REQUIRED", "A company-realm token is required");
        }
        Jwt jwt = token.getToken();
        String companyCode = jwt.getClaimAsString(RealmRoles.TENANT_CLAIM);
        if (companyCode == null || !COMPANY_CODE.matcher(companyCode).matches()) {
            throw ServiceException.unauthorized("TENANT_REQUIRED", "The token carries no valid tenant_id");
        }
        TenantRequest tenantRequest = new TenantRequest(companyCode, gatewayToken, token.getName(), RealmRoles.of(jwt));
        for (TenantAccessRule rule : rules) {
            rule.verify(tenantRequest);
        }
        TenantContext.set(new TenantContext.CurrentTenant(companyCode, token.getName(), jwt.getTokenValue()));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception ex) {
        TenantContext.clear();
    }
}
