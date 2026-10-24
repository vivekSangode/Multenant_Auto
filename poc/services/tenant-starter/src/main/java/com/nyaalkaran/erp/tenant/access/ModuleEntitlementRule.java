package com.nyaalkaran.erp.tenant.access;

import com.nyaalkaran.erp.tenant.web.ServiceException;

/** Step 6: the company bought this module (its token is in COMPANY_MODULES). */
public class ModuleEntitlementRule implements TenantAccessRule {

    private final RuntimeStateReader state;

    public ModuleEntitlementRule(RuntimeStateReader state) {
        this.state = state;
    }

    @Override
    public void verify(TenantRequest request) {
        if (!state.isEntitled(request.companyCode(), request.gatewayToken())) {
            throw ServiceException.forbidden("MODULE_NOT_ENTITLED",
                    request.gatewayToken() + " is not in the company's subscription");
        }
    }

    @Override
    public int getOrder() {
        return 200;
    }
}
