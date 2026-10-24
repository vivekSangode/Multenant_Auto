package com.nyaalkaran.erp.poc.billing;

import com.nyaalkaran.erp.poc.catalog.CatalogEnums.ChargeMode;
import com.nyaalkaran.erp.poc.catalog.CatalogEnums.DependencyType;
import com.nyaalkaran.erp.poc.catalog.CatalogEnums.ModuleType;
import com.nyaalkaran.erp.poc.catalog.CatalogSnapshot;
import com.nyaalkaran.erp.poc.catalog.CatalogSnapshot.BundleInfo;
import com.nyaalkaran.erp.poc.catalog.CatalogSnapshot.DependencyInfo;
import com.nyaalkaran.erp.poc.catalog.CatalogSnapshot.ModuleInfo;
import com.nyaalkaran.erp.poc.catalog.CatalogSnapshot.PriceInfo;
import com.nyaalkaran.erp.poc.common.ApiException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The order calculation algorithm of master §12.2, steps 1-9 (proration, step 10, is out of POC scope).
 * Pure function over a {@link CatalogSnapshot}: no persistence, deterministic, unit-tested against §12.3.
 * Money: BigDecimal, scale 2, HALF_UP at line level; totals are sums of rounded lines.
 */
public final class PricingEngine {

    public static final String CALCULATION_VERSION = "poc-1";
    private static final int SCALE = 2;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public enum LineType { PLAN, DEPENDENCY, ADDITIONAL_USERS, ADDITIONAL_STORAGE }

    public enum DiscountType { PERCENT, FIXED }

    public record ManualDiscount(DiscountType type, BigDecimal value, String reason) {
    }

    public record Request(List<String> bundles, List<String> modules, int users, int storageGb,
                          Set<String> existingModules, ManualDiscount manualDiscount) {
    }

    public record Line(int lineNo, LineType type, String description, String moduleCode, String bundleCode,
                       PriceInfo price, BigDecimal quantity, BigDecimal unitPrice, BigDecimal grossAmount) {
    }

    public record Warning(String code, String detail) {
    }

    public record Quote(List<Line> lines, List<Warning> warnings, BigDecimal subtotal, BigDecimal discountTotal,
                        BigDecimal taxableTotal, BigDecimal taxTotal, BigDecimal grandTotal,
                        int includedUsers, int additionalUsers, int includedStorageGb, int additionalStorageBlocks,
                        Set<String> newModules) {
    }

    private final BigDecimal taxRatePercent;

    public PricingEngine(BigDecimal taxRatePercent) {
        this.taxRatePercent = taxRatePercent == null ? BigDecimal.ZERO : taxRatePercent;
    }

    public Quote price(CatalogSnapshot catalog, Request req) {
        List<Line> lines = new ArrayList<>();
        List<Warning> warnings = new ArrayList<>();
        Set<String> existing = req.existingModules() == null ? Set.of() : req.existingModules();
        List<String> bundleCodes = distinct(req.bundles());
        List<String> moduleCodes = distinct(req.modules());

        // 1. Expand bundles and validate them
        List<BundleInfo> bundles = new ArrayList<>();
        for (String code : bundleCodes) {
            BundleInfo b = catalog.bundles().get(code);
            if (b == null || !b.active()) {
                throw ApiException.badRequest("UNKNOWN_BUNDLE", "Unknown or inactive bundle: " + code);
            }
            bundles.add(b);
        }
        // 3. Bundle overlap: a bundle fully inside another one is redundant; a partial overlap is a warning
        for (BundleInfo b1 : bundles) {
            for (BundleInfo b2 : bundles) {
                if (b1 == b2 || disjoint(b1.moduleCodes(), b2.moduleCodes())) {
                    continue;
                }
                if (b2.moduleCodes().containsAll(b1.moduleCodes())) {
                    throw ApiException.badRequest("REDUNDANT_BUNDLE", b1.code() + " is contained in " + b2.code());
                }
                if (b1.code().compareTo(b2.code()) < 0) {
                    warnings.add(new Warning("BUNDLE_OVERLAP", b1.code() + " and " + b2.code()
                            + " share modules; full bundle prices apply (Q-10)"));
                }
            }
        }

        Set<String> covered = new LinkedHashSet<>(existing);
        Set<String> newModules = new LinkedHashSet<>();
        for (BundleInfo b : bundles) {
            if (existing.containsAll(b.moduleCodes())) {
                throw ApiException.badRequest("REDUNDANT_BUNDLE", b.code() + " is already fully entitled");
            }
            PriceInfo price = requirePrice(catalog, CatalogSnapshot.bundleKey(b.code()));
            lines.add(line(lines, LineType.PLAN, "Bundle " + b.name(), null, b.code(), price, 1));
            for (String m : b.moduleCodes()) {
                if (existing.contains(m)) {
                    warnings.add(new Warning("DUPLICATE_ENTITLEMENT", m + " is already entitled; bundle price still applies"));
                }
                if (!existing.contains(m)) {
                    newModules.add(m);
                }
                covered.add(m);
            }
        }

        // 2. Duplicates: never charge a module twice
        for (String code : moduleCodes) {
            ModuleInfo m = catalog.modules().get(code);
            if (m == null || !m.active()) {
                throw ApiException.badRequest("UNKNOWN_MODULE", "Unknown or inactive module: " + code);
            }
            if (m.type() == ModuleType.PLATFORM_CORE) {
                throw ApiException.badRequest("NOT_SELLABLE", code + " is platform core and always provisioned");
            }
            if (covered.contains(code)) {
                warnings.add(new Warning("DUPLICATE_ENTITLEMENT", code + " is already covered; line dropped"));
                continue;
            }
            PriceInfo price = requirePrice(catalog, CatalogSnapshot.moduleKey(code));
            lines.add(line(lines, LineType.PLAN, "Module " + m.name(), code, null, price, 1));
            covered.add(code);
            newModules.add(code);
        }

        // 4. Mandatory dependency closure; RECOMMENDED dependencies only warn
        Deque<String> queue = new ArrayDeque<>(covered);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (DependencyInfo d : catalog.dependencies()) {
                if (!d.module().equals(current) || covered.contains(d.dependsOn())) {
                    continue;
                }
                if (d.type() == DependencyType.MANDATORY) {
                    ModuleInfo dep = catalog.modules().get(d.dependsOn());
                    PriceInfo price = requirePrice(catalog, CatalogSnapshot.moduleKey(d.dependsOn()));
                    boolean included = d.chargeMode() == ChargeMode.INCLUDED;
                    lines.add(new Line(lines.size() + 1, LineType.DEPENDENCY,
                            "Dependency " + dep.name() + " (required by " + current + ")", d.dependsOn(), null,
                            price, BigDecimal.ONE, included ? zero() : money(price.unitPrice()),
                            included ? zero() : money(price.unitPrice())));
                    covered.add(d.dependsOn());
                    newModules.add(d.dependsOn());
                    queue.add(d.dependsOn());
                } else if (d.type() == DependencyType.RECOMMENDED) {
                    warnings.add(new Warning("RECOMMENDED_DEPENDENCY_MISSING",
                            current + " works best with " + d.dependsOn()));
                }
            }
        }

        if (lines.isEmpty()) {
            throw ApiException.badRequest("EMPTY_ORDER", "The order adds no module or bundle");
        }

        // 6. Users: base included once, then per additional user; enforce the maximum
        PriceInfo seat = requirePrice(catalog, CatalogSnapshot.USER_SEAT);
        int includedUsers = seat.includedUsers();
        if (seat.maxQuantity() != null && req.users() > seat.maxQuantity()) {
            throw ApiException.badRequest("USER_LIMIT_EXCEEDED", "At most " + seat.maxQuantity() + " users");
        }
        int additionalUsers = Math.max(0, req.users() - includedUsers);
        if (additionalUsers > 0) {
            lines.add(line(lines, LineType.ADDITIONAL_USERS, additionalUsers + " additional users", null, null,
                    seat, additionalUsers));
        }

        // 7. Storage (GiB): base included once, then per block of unit_quantity GiB
        PriceInfo storage = requirePrice(catalog, CatalogSnapshot.STORAGE);
        int includedStorage = storage.includedStorageGb();
        if (storage.maxQuantity() != null && req.storageGb() > storage.maxQuantity()) {
            throw ApiException.badRequest("STORAGE_LIMIT_EXCEEDED", "At most " + storage.maxQuantity() + " GiB");
        }
        int extraGb = Math.max(0, req.storageGb() - includedStorage);
        int blocks = (extraGb + storage.unitQuantity() - 1) / storage.unitQuantity();
        if (blocks > 0) {
            lines.add(line(lines, LineType.ADDITIONAL_STORAGE,
                    blocks + " x " + storage.unitQuantity() + " GiB additional storage", null, null, storage, blocks));
        }

        // 8. Subtotal -> manual discount (R1: audited manual adjustment) -> taxable
        BigDecimal subtotal = lines.stream().map(Line::grossAmount).reduce(zero(), BigDecimal::add);
        BigDecimal discount = discount(req.manualDiscount(), subtotal);
        BigDecimal taxable = subtotal.subtract(discount);

        // 9. Tax
        BigDecimal tax = taxable.multiply(taxRatePercent).divide(HUNDRED, SCALE, RoundingMode.HALF_UP);
        if (taxRatePercent.signum() == 0) {
            warnings.add(new Warning("TAX_RULE_NOT_CONFIGURED", "No APPROVED tax rule (Q-04); tax = 0"));
        }
        BigDecimal grand = taxable.add(tax);

        return new Quote(List.copyOf(lines), List.copyOf(warnings), subtotal, discount, taxable, tax, grand,
                includedUsers, additionalUsers, includedStorage, blocks, newModules);
    }

    private static BigDecimal discount(ManualDiscount d, BigDecimal subtotal) {
        if (d == null) {
            return zero();
        }
        if (d.reason() == null || d.reason().isBlank()) {
            throw ApiException.badRequest("DISCOUNT_REASON_REQUIRED", "A manual adjustment needs a reason");
        }
        if (d.value() == null || d.value().signum() < 0) {
            throw ApiException.badRequest("INVALID_DISCOUNT", "The discount value must be >= 0");
        }
        BigDecimal amount = d.type() == DiscountType.PERCENT
                ? subtotal.multiply(d.value()).divide(HUNDRED, SCALE, RoundingMode.HALF_UP)
                : money(d.value());
        if (amount.compareTo(subtotal) > 0) {
            throw ApiException.badRequest("INVALID_DISCOUNT", "The discount exceeds the subtotal");
        }
        return amount;
    }

    private static Line line(List<Line> lines, LineType type, String description, String module, String bundle,
                             PriceInfo price, int quantity) {
        BigDecimal unit = money(price.unitPrice());
        return new Line(lines.size() + 1, type, description, module, bundle, price, BigDecimal.valueOf(quantity),
                unit, money(unit.multiply(BigDecimal.valueOf(quantity))));
    }

    private static PriceInfo requirePrice(CatalogSnapshot catalog, String key) {
        PriceInfo p = catalog.prices().get(key);
        if (p == null) {
            throw ApiException.badRequest("NO_ACTIVE_PRICE", "No active price for " + key);
        }
        return p;
    }

    private static List<String> distinct(List<String> values) {
        return values == null ? List.of() : new ArrayList<>(new LinkedHashSet<>(values));
    }

    private static boolean disjoint(Collection<String> a, Collection<String> b) {
        return a.stream().noneMatch(b::contains);
    }

    private static BigDecimal money(BigDecimal v) {
        return v.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(SCALE);
    }
}
