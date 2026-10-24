package com.nyaalkaran.erp.poc.subscription;

import com.nyaalkaran.erp.poc.subscription.EntitlementEngine.Desired;
import com.nyaalkaran.erp.poc.subscription.EntitlementEngine.Key;
import com.nyaalkaran.erp.poc.subscription.SubscriptionEnums.EntitlementSource;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Overlapping bundles (CD-24) may grant one module twice; only one row may carry the charge (E6-2). */
class ChargedGuardTest {

    private static final long PRODUCTION = 7L;

    private static Map<Key, Desired> twoBundlesWithProduction() {
        Map<Key, Desired> desired = new LinkedHashMap<>();
        desired.put(new Key(PRODUCTION, EntitlementSource.PLAN_BUNDLE, "ITEM:12"), new Desired(true, 12L));
        desired.put(new Key(1L, EntitlementSource.PLAN_BUNDLE, "ITEM:11"), new Desired(true, 11L));
        desired.put(new Key(PRODUCTION, EntitlementSource.PLAN_BUNDLE, "ITEM:11"), new Desired(true, 11L));
        desired.put(new Key(99L, EntitlementSource.PLATFORM_CORE, "CORE"), new Desired(false, null));
        return desired;
    }

    private static long charged(Map<Key, Desired> desired, long moduleId) {
        return desired.entrySet().stream().filter(d -> d.getKey().moduleId() == moduleId && d.getValue().charged()).count();
    }

    @Test
    void oldestItemCarriesTheChargeOnFirstMaterialization() {
        Map<Key, Desired> desired = twoBundlesWithProduction();
        EntitlementEngine.keepOneChargedRowPerModule(desired, List.of());
        assertThat(charged(desired, PRODUCTION)).isEqualTo(1);
        assertThat(desired.get(new Key(PRODUCTION, EntitlementSource.PLAN_BUNDLE, "ITEM:11")).charged()).isTrue();
        assertThat(desired.get(new Key(PRODUCTION, EntitlementSource.PLAN_BUNDLE, "ITEM:12")).charged()).isFalse();
        assertThat(charged(desired, 1L)).isEqualTo(1);           // non-overlapping modules are untouched
        assertThat(desired).hasSize(4);                          // both sources are still recorded
    }

    @Test
    void theAlreadyChargedRowKeepsTheCharge() {
        CompanyEntitlement current = new CompanyEntitlement();
        current.setModuleId(PRODUCTION);
        current.setSourceType(EntitlementSource.PLAN_BUNDLE);
        current.setSourceKey("ITEM:12");
        current.setCharged(true);
        Map<Key, Desired> desired = twoBundlesWithProduction();
        EntitlementEngine.keepOneChargedRowPerModule(desired, List.of(current));
        assertThat(desired.get(new Key(PRODUCTION, EntitlementSource.PLAN_BUNDLE, "ITEM:12")).charged()).isTrue();
        assertThat(charged(desired, PRODUCTION)).isEqualTo(1);
    }

    @Test
    void theRemainingSourceTakesOverWhenTheChargedOneEnds() {
        Map<Key, Desired> desired = new LinkedHashMap<>();
        desired.put(new Key(PRODUCTION, EntitlementSource.PLAN_BUNDLE, "ITEM:12"), new Desired(true, 12L));
        CompanyEntitlement endedSource = new CompanyEntitlement();   // ITEM:11 is gone from the desired set
        endedSource.setModuleId(PRODUCTION);
        endedSource.setSourceType(EntitlementSource.PLAN_BUNDLE);
        endedSource.setSourceKey("ITEM:11");
        endedSource.setCharged(true);
        EntitlementEngine.keepOneChargedRowPerModule(desired, List.of(endedSource));
        assertThat(desired.get(new Key(PRODUCTION, EntitlementSource.PLAN_BUNDLE, "ITEM:12")).charged()).isTrue();
    }
}
