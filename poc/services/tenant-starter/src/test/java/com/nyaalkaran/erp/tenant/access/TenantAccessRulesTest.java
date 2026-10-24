package com.nyaalkaran.erp.tenant.access;

import com.nyaalkaran.erp.tenant.web.ServiceException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The fail-closed gate of PHASE_01 §6.6.2 steps 5-7, without Redis (a fake RuntimeStateReader). */
class TenantAccessRulesTest {

    record FakeState(Map<String, String> status, Map<String, Set<String>> modules) implements RuntimeStateReader {
        @Override
        public Optional<String> companyStatus(String companyCode) {
            return Optional.ofNullable(status.get(companyCode));
        }

        @Override
        public boolean isEntitled(String companyCode, String gatewayToken) {
            return modules.getOrDefault(companyCode, Set.of()).contains(gatewayToken);
        }
    }

    private final FakeState state = new FakeState(
            Map.of("CMP_101", "ACTIVE", "CMP_102", "SUSPENDED", "CMP_103", "PROVISIONING"),
            Map.of("CMP_101", Set.of("VEHICLE", "INVENTORY")));

    private static TenantRequest request(String company, String module, String... roles) {
        return new TenantRequest(company, module, "user@test", List.of(roles));
    }

    private static String code(Runnable r) {
        try {
            r.run();
            return null;
        } catch (ServiceException ex) {
            return ex.status().value() + " " + ex.code();
        }
    }

    @Test
    void companyStatus() {
        CompanyStatusRule rule = new CompanyStatusRule(state);
        assertThatCode(() -> rule.verify(request("CMP_101", "VEHICLE"))).doesNotThrowAnyException();
        assertThat(code(() -> rule.verify(request("CMP_102", "VEHICLE")))).isEqualTo("403 COMPANY_SUSPENDED");
        assertThat(code(() -> rule.verify(request("CMP_103", "VEHICLE")))).isEqualTo("503 COMPANY_NOT_READY");
        assertThat(code(() -> rule.verify(request("CMP_999", "VEHICLE")))).isEqualTo("503 COMPANY_NOT_READY");
    }

    @Test
    void moduleEntitlement() {
        ModuleEntitlementRule rule = new ModuleEntitlementRule(state);
        assertThatCode(() -> rule.verify(request("CMP_101", "INVENTORY"))).doesNotThrowAnyException();
        assertThat(code(() -> rule.verify(request("CMP_101", "BOOKING")))).isEqualTo("403 MODULE_NOT_ENTITLED");
    }

    @Test
    void moduleRoleIsRequiredOnTopOfEntitlement() {
        ModuleAccessRule rule = new ModuleAccessRule();
        assertThatCode(() -> rule.verify(request("CMP_101", "VEHICLE", "COMPANY_SUPER_ADMIN")))
                .doesNotThrowAnyException();
        assertThatCode(() -> rule.verify(request("CMP_101", "VEHICLE", "VEHICLE_MANAGER"))).doesNotThrowAnyException();
        assertThat(code(() -> rule.verify(request("CMP_101", "VEHICLE", "INVENTORY_USER", "VEHICLE_X"))))
                .isEqualTo("403 MODULE_ACCESS_DENIED");
        assertThatThrownBy(() -> rule.verify(request("CMP_101", "VEHICLE"))).isInstanceOf(ServiceException.class);
    }

    @Test
    void rulesRunInDocumentedOrder() {
        List<TenantAccessRule> rules = new java.util.ArrayList<>(List.of(
                new ModuleAccessRule(), new CompanyStatusRule(state), new ModuleEntitlementRule(state)));
        rules.sort(java.util.Comparator.comparingInt(TenantAccessRule::getOrder));
        assertThat(rules).extracting(r -> r.getClass().getSimpleName())
                .containsExactly("CompanyStatusRule", "ModuleEntitlementRule", "ModuleAccessRule");
    }
}
