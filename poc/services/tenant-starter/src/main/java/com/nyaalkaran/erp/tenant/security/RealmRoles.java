package com.nyaalkaran.erp.tenant.security;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Roles are read ONLY from `realm_access.roles` of the verified token (CD-14, PHASE_01 §6.5.9). */
public final class RealmRoles {

    public static final String TENANT_CLAIM = "tenant_id";

    private RealmRoles() {
    }

    public static List<String> of(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        Object roles = realmAccess == null ? null : realmAccess.get("roles");
        return roles instanceof Collection<?> c ? c.stream().map(String::valueOf).toList() : List.of();
    }
}
