package com.nyaalkaran.erp.tenant.access;

import com.nyaalkaran.erp.tenant.web.ServiceException;

import java.util.List;
import java.util.Locale;

/**
 * Step 7: role is not entitlement, both are required. The user holds COMPANY_SUPER_ADMIN or one of the predefined
 * module roles `<MODULE>_<LEVEL>` (CD-14), e.g. VEHICLE_USER.
 */
public class ModuleAccessRule implements TenantAccessRule {

    public static final String COMPANY_SUPER_ADMIN = "COMPANY_SUPER_ADMIN";
    public static final List<String> LEVELS = List.of("USER", "TEAM_LEAD", "MANAGER", "ADMIN", "SUPER_ADMIN");

    @Override
    public void verify(TenantRequest request) {
        if (request.roles().contains(COMPANY_SUPER_ADMIN)) {
            return;
        }
        String prefix = request.gatewayToken().toUpperCase(Locale.ROOT).replace('-', '_') + "_";
        if (LEVELS.stream().map(level -> prefix + level).noneMatch(request.roles()::contains)) {
            throw ServiceException.forbidden("MODULE_ACCESS_DENIED",
                    "The user holds no " + request.gatewayToken() + " role");
        }
    }

    @Override
    public int getOrder() {
        return 300;
    }
}
