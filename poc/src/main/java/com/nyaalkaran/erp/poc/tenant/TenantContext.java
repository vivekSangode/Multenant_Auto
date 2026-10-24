package com.nyaalkaran.erp.poc.tenant;

import com.nyaalkaran.erp.poc.common.ApiException;
import org.springframework.http.HttpStatus;

/** The verified company of the current request. Cleared after every request (no ThreadLocal leaks). */
public final class TenantContext {

    public record Tenant(String companyCode, String moduleCode) {
    }

    private static final ThreadLocal<Tenant> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    static void set(Tenant tenant) {
        CURRENT.set(tenant);
    }

    static void clear() {
        CURRENT.remove();
    }

    /** Fail-closed: business code can never run without a tenant. */
    public static Tenant require() {
        Tenant t = CURRENT.get();
        if (t == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "NO_TENANT_CONTEXT",
                    "No tenant context on this thread");
        }
        return t;
    }
}
