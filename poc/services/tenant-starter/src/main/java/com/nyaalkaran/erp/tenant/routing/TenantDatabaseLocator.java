package com.nyaalkaran.erp.tenant.routing;

/** Resolves the database of THIS service's module for a company, only from the registry (PHASE_01 §6.6.2 step 10). */
public interface TenantDatabaseLocator {

    DatabaseLocation locate(String companyCode);
}
