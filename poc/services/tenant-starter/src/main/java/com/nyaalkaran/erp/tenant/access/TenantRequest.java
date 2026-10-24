package com.nyaalkaran.erp.tenant.access;

import java.util.List;

/** What the access rules see about one request: the verified company, the target module and the caller's roles. */
public record TenantRequest(String companyCode, String gatewayToken, String username, List<String> roles) {
}
