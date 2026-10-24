package com.nyaalkaran.erp.poc.subscription;

/**
 * Published by the EntitlementEngine inside its transaction.
 * pendingProvisioning = true when an ACTIVE company has new entitlements whose resources do not exist yet
 * (the provisioning module then schedules an ADD_MODULE job, master §13.3).
 */
public record EntitlementsChangedEvent(Long companyId, String companyCode, boolean pendingProvisioning) {
}
