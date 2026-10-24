package com.nyaalkaran.erp.tenant.context;

import com.nyaalkaran.erp.tenant.web.ServiceException;
import org.springframework.http.HttpStatus;

/** The verified company of the current request. Set by the access gate, cleared after every request. */
public final class TenantContext {

    /**
     * @param companyCode CMP_n from the verified token
     * @param username    preferred_username of the caller (audit columns)
     * @param userToken   the caller's access token, relayed on service-to-service business calls (PHASE_01 §6.6.3)
     */
    public record CurrentTenant(String companyCode, String username, String userToken) {
    }

    private static final ThreadLocal<CurrentTenant> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(CurrentTenant tenant) {
        CURRENT.set(tenant);
    }

    public static void clear() {
        CURRENT.remove();
    }

    /** Fail-closed: business code can never run without a tenant. */
    public static CurrentTenant require() {
        CurrentTenant t = CURRENT.get();
        if (t == null) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "NO_TENANT_CONTEXT",
                    "No tenant context on this thread");
        }
        return t;
    }
}
