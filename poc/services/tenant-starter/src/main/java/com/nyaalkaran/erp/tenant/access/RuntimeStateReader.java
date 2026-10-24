package com.nyaalkaran.erp.tenant.access;

import java.util.Optional;

/** The runtime projection the control plane publishes (master §10.6): TENANT_STATUS and COMPANY_MODULES. */
public interface RuntimeStateReader {

    /** @return the company status, or empty when the company was never published (not provisioned) */
    Optional<String> companyStatus(String companyCode);

    boolean isEntitled(String companyCode, String gatewayToken);
}
