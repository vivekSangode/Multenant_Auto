package com.nyaalkaran.erp.poc.billing;

import com.nyaalkaran.erp.poc.billing.PricingEngine.DiscountType;
import com.nyaalkaran.erp.poc.billing.PricingEngine.ManualDiscount;
import com.nyaalkaran.erp.poc.billing.PricingEngine.Quote;
import com.nyaalkaran.erp.poc.billing.PricingEngine.Request;
import com.nyaalkaran.erp.poc.catalog.CatalogEnums.ChargeMode;
import com.nyaalkaran.erp.poc.catalog.CatalogEnums.DependencyType;
import com.nyaalkaran.erp.poc.catalog.CatalogEnums.ModuleType;
import com.nyaalkaran.erp.poc.catalog.CatalogSnapshot;
import com.nyaalkaran.erp.poc.catalog.CatalogSnapshot.BundleInfo;
import com.nyaalkaran.erp.poc.catalog.CatalogSnapshot.DependencyInfo;
import com.nyaalkaran.erp.poc.catalog.CatalogSnapshot.ModuleInfo;
import com.nyaalkaran.erp.poc.catalog.CatalogSnapshot.PriceInfo;
import com.nyaalkaran.erp.poc.common.ApiException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The illustrative scenarios of master §12.3 (IU, monthly). */
class PricingEngineTest {

    private static final AtomicLong IDS = new AtomicLong(1);
    private final CatalogSnapshot catalog = catalog();
    private final PricingEngine engine = new PricingEngine(BigDecimal.ZERO);

    @Test
    void s1_assetOnly_withinIncludedUsersAndStorage() {
        Quote q = price(List.of(), List.of("asset"), 3, 5, Set.of());
        assertThat(q.grandTotal()).isEqualByComparingTo("100");
        assertThat(q.newModules()).containsExactly("asset");
    }

    @Test
    void s2_store_addsChargedAccountBookDependency_andExtraUsers() {
        Quote q = price(List.of(), List.of("store"), 8, 10, Set.of());
        assertThat(q.grandTotal()).isEqualByComparingTo("460"); // 150 + 250 + 3 x 20
        assertThat(q.lines()).anyMatch(l -> l.type() == PricingEngine.LineType.DEPENDENCY
                && "account-book".equals(l.moduleCode()));
        assertThat(q.newModules()).containsExactlyInAnyOrder("store", "account-book");
    }

    @Test
    void s3_bundlePlusIncludedModule_dropsDuplicateLine() {
        Quote q = price(List.of("HR_SUITE"), List.of("payroll"), 5, 10, Set.of());
        assertThat(q.grandTotal()).isEqualByComparingTo("380");
        assertThat(q.warnings()).anyMatch(w -> w.code().equals("DUPLICATE_ENTITLEMENT"));
    }

    @Test
    void s4_twoBundlesAssetUsersAndStorage() {
        Quote q = price(List.of("HR_SUITE", "SALES_DISPATCH"), List.of("asset"), 25, 35, Set.of());
        // 380 + 650 + 100 + 20 users x 20 + ceil(25/10) = 3 blocks x 15
        assertThat(q.grandTotal()).isEqualByComparingTo("1575");
        assertThat(q.additionalUsers()).isEqualTo(20);
        assertThat(q.additionalStorageBlocks()).isEqualTo(3);
    }

    @Test
    void s5_logistics_pullsCrm() {
        Quote q = price(List.of(), List.of("logistics"), 5, 0, Set.of());
        assertThat(q.grandTotal()).isEqualByComparingTo("500"); // 200 + crm 300 (crm->production not confirmed)
    }

    @Test
    void upgrade_neverChargesAnAlreadyEntitledModule() {
        Quote q = price(List.of(), List.of("store"), 5, 10, Set.of("account-book"));
        assertThat(q.grandTotal()).isEqualByComparingTo("150");
        assertThatThrownBy(() -> price(List.of(), List.of("asset"), 5, 10, Set.of("asset")))
                .isInstanceOf(ApiException.class).hasMessageContaining("adds no module");
    }

    @Test
    void redundantBundle_isRejected() {
        catalog.bundles().put("HR_CORE", new BundleInfo(IDS.incrementAndGet(), "HR_CORE", "HR Core",
                new TreeSet<>(Set.of("hrms", "attendance")), true));
        catalog.prices().put(CatalogSnapshot.bundleKey("HR_CORE"), price(250));
        assertThatThrownBy(() -> price(List.of("HR_CORE", "HR_SUITE"), List.of(), 5, 10, Set.of()))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("REDUNDANT_BUNDLE");
    }

    @Test
    void manualDiscount_needsReason_andIsApplied() {
        Quote q = engine.price(catalog, new Request(List.of(), List.of("crm"), 5, 10, Set.of(),
                new ManualDiscount(DiscountType.PERCENT, BigDecimal.TEN, "launch offer")));
        assertThat(q.discountTotal()).isEqualByComparingTo("30");
        assertThat(q.grandTotal()).isEqualByComparingTo("270");
        assertThatThrownBy(() -> engine.price(catalog, new Request(List.of(), List.of("crm"), 5, 10, Set.of(),
                new ManualDiscount(DiscountType.FIXED, BigDecimal.ONE, " "))))
                .extracting("code").isEqualTo("DISCOUNT_REASON_REQUIRED");
    }

    @Test
    void tax_isAppliedOnTaxableAmount() {
        Quote q = new PricingEngine(new BigDecimal("18")).price(catalog,
                new Request(List.of(), List.of("crm"), 5, 10, Set.of(), null));
        assertThat(q.taxTotal()).isEqualByComparingTo("54");
        assertThat(q.grandTotal()).isEqualByComparingTo("354");
    }

    @Test
    void platformCoreModules_areNotSellable() {
        assertThatThrownBy(() -> price(List.of(), List.of("notification"), 5, 10, Set.of()))
                .extracting("code").isEqualTo("NOT_SELLABLE");
    }

    private Quote price(List<String> bundles, List<String> modules, int users, int storage, Set<String> existing) {
        return engine.price(catalog, new Request(bundles, modules, users, storage, existing, null));
    }

    private static CatalogSnapshot catalog() {
        Map<String, ModuleInfo> modules = new LinkedHashMap<>();
        Map<String, PriceInfo> prices = new HashMap<>();
        Object[][] m = {{"asset", 100}, {"crm", 300}, {"production", 250}, {"logistics", 200}, {"hrms", 200},
                {"attendance", 80}, {"payroll", 150}, {"account-book", 250}, {"store", 150}};
        for (Object[] row : m) {
            String code = (String) row[0];
            modules.put(code, new ModuleInfo(IDS.incrementAndGet(), code, code, ModuleType.BUSINESS, code, true));
            prices.put(CatalogSnapshot.moduleKey(code), price((Integer) row[1]));
        }
        modules.put("notification", new ModuleInfo(IDS.incrementAndGet(), "notification", "Notifications",
                ModuleType.PLATFORM_CORE, "notification", true));

        Map<String, BundleInfo> bundles = new LinkedHashMap<>();
        bundles.put("HR_SUITE", new BundleInfo(IDS.incrementAndGet(), "HR_SUITE", "HR Suite",
                new TreeSet<>(Set.of("hrms", "attendance", "payroll")), true));
        bundles.put("SALES_DISPATCH", new BundleInfo(IDS.incrementAndGet(), "SALES_DISPATCH", "Sales & Dispatch",
                new TreeSet<>(Set.of("crm", "production", "logistics")), true));
        prices.put(CatalogSnapshot.bundleKey("HR_SUITE"), price(380));
        prices.put(CatalogSnapshot.bundleKey("SALES_DISPATCH"), price(650));

        prices.put(CatalogSnapshot.USER_SEAT, new PriceInfo(1L, 1L, IDS.incrementAndGet(), new BigDecimal("20"),
                1, 5, 0, 500));
        prices.put(CatalogSnapshot.STORAGE, new PriceInfo(2L, 2L, IDS.incrementAndGet(), new BigDecimal("15"),
                10, 0, 10, 1000));

        List<DependencyInfo> deps = List.of(
                new DependencyInfo("store", "account-book", DependencyType.MANDATORY, ChargeMode.CHARGED),
                new DependencyInfo("logistics", "crm", DependencyType.MANDATORY, ChargeMode.CHARGED),
                new DependencyInfo("payroll", "hrms", DependencyType.MANDATORY, ChargeMode.CHARGED),
                new DependencyInfo("attendance", "hrms", DependencyType.MANDATORY, ChargeMode.CHARGED),
                new DependencyInfo("payroll", "attendance", DependencyType.RECOMMENDED, ChargeMode.CHARGED));
        return new CatalogSnapshot(modules, bundles, deps, prices);
    }

    private static PriceInfo price(int amount) {
        long id = IDS.incrementAndGet();
        return new PriceInfo(id, id, id, BigDecimal.valueOf(amount), 1, 0, 0, null);
    }
}
