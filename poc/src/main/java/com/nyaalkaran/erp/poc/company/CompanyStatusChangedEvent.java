package com.nyaalkaran.erp.poc.company;

/** Published inside the transaction; the cache publisher reacts AFTER_COMMIT (master §19.3). */
public record CompanyStatusChangedEvent(Long companyId, String companyCode, CompanyStatus status) {
}
