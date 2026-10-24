package com.nyaalkaran.erp.tenant.access;

import com.nyaalkaran.erp.tenant.web.ServiceException;

/** Step 5: TENANT_STATUS must be ACTIVE (master §7.1 access table). */
public class CompanyStatusRule implements TenantAccessRule {

    private final RuntimeStateReader state;

    public CompanyStatusRule(RuntimeStateReader state) {
        this.state = state;
    }

    @Override
    public void verify(TenantRequest request) {
        String status = state.companyStatus(request.companyCode())
                .orElseThrow(() -> ServiceException.unavailable("COMPANY_NOT_READY", "Company is not provisioned"));
        switch (status) {
            case "ACTIVE" -> {
            }
            case "SUSPENDED" -> throw ServiceException.forbidden("COMPANY_SUSPENDED", "Company is SUSPENDED");
            case "DISABLED" -> throw ServiceException.forbidden("COMPANY_DISABLED", "Company is DISABLED");
            case "ARCHIVED", "PURGE_APPROVED" -> throw ServiceException.forbidden("COMPANY_ARCHIVED", "Company is " + status);
            default -> throw ServiceException.unavailable("COMPANY_NOT_READY", "Company is " + status);
        }
    }

    @Override
    public int getOrder() {
        return 100;
    }
}
