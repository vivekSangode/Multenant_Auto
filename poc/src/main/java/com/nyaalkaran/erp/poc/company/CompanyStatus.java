package com.nyaalkaran.erp.poc.company;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static java.util.Map.entry;

/** Company lifecycle (master §7.1). */
public enum CompanyStatus {
    DRAFT, PROVISIONING, PROVISIONING_FAILED, AWAITING_DATA_IMPORT, ACTIVE, SUSPENDED, DISABLED, ARCHIVED, PURGE_APPROVED;

    private static final Map<CompanyStatus, Set<CompanyStatus>> ALLOWED = Map.ofEntries(
            entry(DRAFT, EnumSet.of(PROVISIONING)),
            entry(PROVISIONING, EnumSet.of(ACTIVE, AWAITING_DATA_IMPORT, PROVISIONING_FAILED)),
            entry(PROVISIONING_FAILED, EnumSet.of(PROVISIONING)),
            entry(AWAITING_DATA_IMPORT, EnumSet.of(ACTIVE)),
            entry(ACTIVE, EnumSet.of(SUSPENDED, DISABLED, ARCHIVED)),
            entry(SUSPENDED, EnumSet.of(ACTIVE, DISABLED, ARCHIVED)),
            entry(DISABLED, EnumSet.of(ACTIVE, ARCHIVED)),
            entry(ARCHIVED, EnumSet.of(ACTIVE, PURGE_APPROVED)),
            entry(PURGE_APPROVED, EnumSet.noneOf(CompanyStatus.class)));

    public boolean canMoveTo(CompanyStatus target) {
        return ALLOWED.get(this).contains(target);
    }

    /** The error code returned to the business API for a company in this state (master §7.1 access table). */
    public String accessDeniedCode() {
        return switch (this) {
            case ACTIVE -> null;
            case SUSPENDED -> "COMPANY_SUSPENDED";
            case DISABLED -> "COMPANY_DISABLED";
            case ARCHIVED, PURGE_APPROVED -> "COMPANY_ARCHIVED";
            default -> "COMPANY_NOT_READY";
        };
    }
}
