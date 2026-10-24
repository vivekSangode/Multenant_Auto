package com.nyaalkaran.erp.poc.common;

import java.util.List;
import java.util.Locale;

/**
 * The predefined company realm roles (CD-14, PHASE_01 §6.5.9): `COMPANY_<NAME>` and `<MODULE_CODE>_<LEVEL>`,
 * realm roles of `co-<n>` delivered in `realm_access.roles`.
 */
public final class CompanyRoles {

    public static final String COMPANY_SUPER_ADMIN = "COMPANY_SUPER_ADMIN";
    public static final String COMPANY_ADMIN = "COMPANY_ADMIN";
    public static final List<String> COMPANY_ROLES = List.of(COMPANY_SUPER_ADMIN, COMPANY_ADMIN);
    public static final List<String> MODULE_LEVELS = List.of("USER", "TEAM_LEAD", "MANAGER", "ADMIN", "SUPER_ADMIN");

    private CompanyRoles() {
    }

    /** Gateway token `ACCOUNT-BOOK` -> ACCOUNT_BOOK_USER, ACCOUNT_BOOK_TEAM_LEAD, ... */
    public static List<String> moduleRoles(String gatewayToken) {
        String prefix = gatewayToken.toUpperCase(Locale.ROOT).replace('-', '_') + "_";
        return MODULE_LEVELS.stream().map(level -> prefix + level).toList();
    }
}
