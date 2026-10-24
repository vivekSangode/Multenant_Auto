package com.nyaalkaran.erp.poc.catalog;

import com.nyaalkaran.erp.poc.catalog.CatalogEnums.ChargeMode;
import com.nyaalkaran.erp.poc.catalog.CatalogEnums.DependencyType;
import com.nyaalkaran.erp.poc.catalog.CatalogEnums.ModuleType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An immutable, persistence-free view of the catalog and one price list (billing period + currency).
 * The pricing engine works only on this, so it is unit-testable without a database.
 */
public record CatalogSnapshot(
        Map<String, ModuleInfo> modules,
        Map<String, BundleInfo> bundles,
        List<DependencyInfo> dependencies,
        Map<String, PriceInfo> prices) {

    public static final String USER_SEAT = "USER_SEAT";
    public static final String STORAGE = "STORAGE";

    public static String moduleKey(String moduleCode) {
        return "MODULE:" + moduleCode;
    }

    public static String bundleKey(String bundleCode) {
        return "BUNDLE:" + bundleCode;
    }

    public record ModuleInfo(Long id, String code, String name, ModuleType type, String dbToken, boolean active) {
    }

    public record BundleInfo(Long id, String code, String name, Set<String> moduleCodes, boolean active) {
    }

    public record DependencyInfo(String module, String dependsOn, DependencyType type, ChargeMode chargeMode) {
    }

    public record PriceInfo(Long planId, Long planVersionId, Long planPriceId, BigDecimal unitPrice,
                            int unitQuantity, int includedUsers, int includedStorageGb, Integer maxQuantity) {
    }
}
