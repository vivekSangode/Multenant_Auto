package com.nyaalkaran.erp.tenant.access;

import org.springframework.core.Ordered;

/**
 * One check of the tenant gate (PHASE_01 §6.6.2 steps 5-7). The gate runs every rule bean in order; a new check
 * (e.g. @RequiresPermission against TENANT_ROLE_PERMS, step 9) is a new bean, not a change to the gate.
 */
public interface TenantAccessRule extends Ordered {

    /** @throws com.nyaalkaran.erp.tenant.web.ServiceException when access is denied */
    void verify(TenantRequest request);
}
