package com.nyaalkaran.erp.tenant.security;

import java.util.Optional;

/** Issuer allow-list: which company a registered company realm belongs to (master §14.1 token rules). */
public interface CompanyIssuerRegistry {

    Optional<String> companyFor(String issuer);
}
