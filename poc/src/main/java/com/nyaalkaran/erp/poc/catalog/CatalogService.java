package com.nyaalkaran.erp.poc.catalog;

import com.nyaalkaran.erp.poc.catalog.CatalogEnums.BillingPeriod;
import com.nyaalkaran.erp.poc.catalog.CatalogEnums.CatalogStatus;
import com.nyaalkaran.erp.poc.catalog.CatalogSnapshot.BundleInfo;
import com.nyaalkaran.erp.poc.catalog.CatalogSnapshot.DependencyInfo;
import com.nyaalkaran.erp.poc.catalog.CatalogSnapshot.ModuleInfo;
import com.nyaalkaran.erp.poc.catalog.CatalogSnapshot.PriceInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
public class CatalogService {

    private final ErpModuleRepository moduleRepository;
    private final ModuleDependencyRepository dependencyRepository;
    private final BundleModuleRepository bundleModuleRepository;
    private final PlanPriceRepository planPriceRepository;

    @Transactional(readOnly = true)
    public CatalogSnapshot snapshot(BillingPeriod period, String currency) {
        Map<String, ModuleInfo> modules = new LinkedHashMap<>();
        for (ErpModule m : moduleRepository.findAllByOrderBySortOrderAsc()) {
            modules.put(m.getCode(), new ModuleInfo(m.getId(), m.getCode(), m.getName(), m.getModuleType(),
                    m.getDbToken(), m.getStatus() == CatalogStatus.ACTIVE));
        }

        Map<String, BundleInfo> bundles = new LinkedHashMap<>();
        for (BundleModule bm : bundleModuleRepository.findAllWithRefs()) {
            Bundle b = bm.getBundle();
            BundleInfo info = bundles.computeIfAbsent(b.getCode(), k -> new BundleInfo(b.getId(), b.getCode(),
                    b.getName(), new TreeSet<>(), b.getStatus() == CatalogStatus.ACTIVE));
            info.moduleCodes().add(bm.getModule().getCode());
        }

        List<DependencyInfo> deps = dependencyRepository.findActiveWithModules().stream()
                .map(d -> new DependencyInfo(d.getModule().getCode(), d.getDependsOn().getCode(),
                        d.getDependencyType(), d.getChargeMode()))
                .toList();

        Map<String, PriceInfo> prices = new LinkedHashMap<>();
        for (PlanPrice pp : planPriceRepository.findCurrentPrices(period, currency)) {
            Plan plan = pp.getPlanVersion().getPlan();
            String key = switch (plan.getPlanType()) {
                case MODULE -> CatalogSnapshot.moduleKey(plan.getModule().getCode());
                case BUNDLE -> CatalogSnapshot.bundleKey(plan.getBundle().getCode());
                case USER_SEAT -> CatalogSnapshot.USER_SEAT;
                case STORAGE -> CatalogSnapshot.STORAGE;
            };
            prices.put(key, new PriceInfo(plan.getId(), pp.getPlanVersion().getId(), pp.getId(), pp.getUnitPrice(),
                    pp.getUnitQuantity(), pp.getIncludedUsers(), pp.getIncludedStorageGb(), pp.getMaxQuantity()));
        }
        return new CatalogSnapshot(modules, bundles, deps, prices);
    }
}
