package com.nyaalkaran.erp.poc.subscription;

import com.nyaalkaran.erp.poc.catalog.BundleModuleRepository;
import com.nyaalkaran.erp.poc.catalog.CatalogService;
import com.nyaalkaran.erp.poc.catalog.CatalogSnapshot;
import com.nyaalkaran.erp.poc.catalog.CatalogSnapshot.PriceInfo;
import com.nyaalkaran.erp.poc.catalog.ErpModule;
import com.nyaalkaran.erp.poc.catalog.ErpModuleRepository;
import com.nyaalkaran.erp.poc.company.Company;
import com.nyaalkaran.erp.poc.company.CompanyRepository;
import com.nyaalkaran.erp.poc.company.CompanyResource.ResourceStatus;
import com.nyaalkaran.erp.poc.company.CompanyResource.ResourceType;
import com.nyaalkaran.erp.poc.company.CompanyResourceRepository;
import com.nyaalkaran.erp.poc.company.CompanyStatus;
import com.nyaalkaran.erp.poc.subscription.SubscriptionEnums.AllowanceType;
import com.nyaalkaran.erp.poc.subscription.SubscriptionEnums.EntitlementSource;
import com.nyaalkaran.erp.poc.subscription.SubscriptionEnums.EntitlementStatus;
import com.nyaalkaran.erp.poc.subscription.SubscriptionEnums.ItemStatus;
import com.nyaalkaran.erp.poc.subscription.SubscriptionEnums.SubscriptionStatus;
import com.nyaalkaran.erp.poc.subscription.SubscriptionRepositories.CompanyAllowanceRepository;
import com.nyaalkaran.erp.poc.subscription.SubscriptionRepositories.CompanyEntitlementRepository;
import com.nyaalkaran.erp.poc.subscription.SubscriptionRepositories.SubscriptionItemRepository;
import com.nyaalkaran.erp.poc.subscription.SubscriptionRepositories.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Recomputes all entitlement and allowance rows of one company in one transaction after any subscription
 * change (master §13.3): diff-apply (only changed rows are touched), PENDING until the module's resources
 * are provisioned, Redis published after commit.
 */
@Service
@RequiredArgsConstructor
public class EntitlementEngine {

    private static final long GIB = 1L << 30;

    private final CompanyRepository companyRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionItemRepository itemRepository;
    private final CompanyEntitlementRepository entitlementRepository;
    private final CompanyAllowanceRepository allowanceRepository;
    private final CompanyResourceRepository resourceRepository;
    private final ErpModuleRepository moduleRepository;
    private final BundleModuleRepository bundleModuleRepository;
    private final CatalogService catalogService;
    private final ApplicationEventPublisher events;

    record Key(Long moduleId, EntitlementSource source, String sourceKey) {
    }

    record Desired(boolean charged, Long itemId) {
    }

    @Transactional
    public void recompute(Long companyId) {
        Company company = companyRepository.lockById(companyId).orElseThrow();
        List<Subscription> active = subscriptionRepository.findByCompanyIdAndStatusIn(companyId,
                Set.of(SubscriptionStatus.ACTIVE));
        List<SubscriptionItem> items = active.isEmpty() ? List.of() : itemRepository.findBySubscriptionIdInAndStatus(
                active.stream().map(Subscription::getId).toList(), ItemStatus.ACTIVE);

        Map<Long, List<Long>> bundleModules = bundleModuleRepository.findAllWithRefs().stream()
                .collect(Collectors.groupingBy(bm -> bm.getBundle().getId(),
                        Collectors.mapping(bm -> bm.getModule().getId(), Collectors.toList())));
        List<ErpModule> modules = moduleRepository.findAll();
        Map<Long, ErpModule> modulesById = modules.stream().collect(Collectors.toMap(ErpModule::getId, m -> m));

        // Desired rows
        Map<Key, Desired> desired = new LinkedHashMap<>();
        for (SubscriptionItem item : items) {
            String sourceKey = "ITEM:" + item.getId();
            switch (item.getItemType()) {
                case PLAN -> {
                    if (item.getModuleId() != null) {
                        desired.put(new Key(item.getModuleId(), EntitlementSource.PLAN_MODULE, sourceKey),
                                new Desired(true, item.getId()));
                    } else {
                        for (Long moduleId : bundleModules.getOrDefault(item.getBundleId(), List.of())) {
                            desired.put(new Key(moduleId, EntitlementSource.PLAN_BUNDLE, sourceKey),
                                    new Desired(true, item.getId()));
                        }
                    }
                }
                case DEPENDENCY -> desired.put(new Key(item.getModuleId(), EntitlementSource.DEPENDENCY, sourceKey),
                        new Desired(item.getUnitPriceSnapshot().signum() > 0, item.getId()));
                default -> {
                }
            }
        }
        if (!active.isEmpty()) {
            // Platform-core modules are always provisioned and never sold (master §11.1)
            modules.stream().filter(ErpModule::isPlatformCore).forEach(m ->
                    desired.put(new Key(m.getId(), EntitlementSource.PLATFORM_CORE, "CORE"), new Desired(false, null)));
        }

        List<CompanyEntitlement> current = entitlementRepository.findByCompanyIdAndStatusNot(companyId,
                EntitlementStatus.ENDED);
        keepOneChargedRowPerModule(desired, current);

        // Diff-apply against the existing, non-ENDED rows
        LocalDate today = LocalDate.now();
        Map<Key, CompanyEntitlement> existing = new HashMap<>();
        for (CompanyEntitlement e : current) {
            Key key = keyOf(e);
            if (desired.containsKey(key)) {
                existing.put(key, e);
            } else {
                e.end(today);
            }
        }
        entitlementRepository.flush();
        // Move the charge between kept rows in two flushed phases, so UQ (company, module, charged_guard) never
        // sees two charged rows of one module (inserts are flushed before updates otherwise).
        for (boolean charge : new boolean[]{false, true}) {
            existing.forEach((key, e) -> {
                if (desired.get(key).charged() == charge && e.isCharged() != charge) {
                    e.setCharged(charge);
                    e.setChargedGuard(charge ? 1 : null);
                }
            });
            entitlementRepository.flush();
        }
        boolean pendingProvisioning = false;
        for (Map.Entry<Key, Desired> d : desired.entrySet()) {
            if (existing.containsKey(d.getKey())) {
                continue;
            }
            ErpModule module = modulesById.get(d.getKey().moduleId());
            boolean provisioned = isProvisioned(companyId, module);
            CompanyEntitlement e = new CompanyEntitlement();
            e.setCompanyId(companyId);
            e.setModuleId(module.getId());
            e.setSourceType(d.getKey().source());
            e.setSourceKey(d.getKey().sourceKey());
            e.setSourceItemId(d.getValue().itemId());
            e.setCharged(d.getValue().charged());
            e.setChargedGuard(d.getValue().charged() ? 1 : null);
            e.setStatus(provisioned && company.getStatus() == CompanyStatus.ACTIVE
                    ? EntitlementStatus.ACTIVE : EntitlementStatus.PENDING);
            e.setValidFrom(today);
            entitlementRepository.save(e);
            pendingProvisioning |= !provisioned && company.getStatus() == CompanyStatus.ACTIVE;
        }

        recomputeAllowances(company, active, items);
        events.publishEvent(new EntitlementsChangedEvent(companyId, company.getCompanyCode(), pendingProvisioning));
    }

    private static Key keyOf(CompanyEntitlement e) {
        return new Key(e.getModuleId(), e.getSourceType(), e.getSourceKey());
    }

    /**
     * The charged-entitlement guard (master §13.3, E6-2) allows one charged row per module. Overlapping bundles are
     * sold at full price (CD-24, warning BUNDLE_OVERLAP), so the same module can be granted by two charged sources:
     * the row that is already charged keeps the charge (stable across recomputes), otherwise the oldest item wins;
     * the other sources are kept uncharged, and take over the charge if the winning source ends.
     */
    static void keepOneChargedRowPerModule(Map<Key, Desired> desired, List<CompanyEntitlement> current) {
        Set<Key> chargedNow = current.stream().filter(CompanyEntitlement::isCharged)
                .map(EntitlementEngine::keyOf).collect(Collectors.toSet());
        Comparator<Map.Entry<Key, Desired>> preference = Comparator
                .comparing((Map.Entry<Key, Desired> d) -> !chargedNow.contains(d.getKey()))
                .thenComparing(d -> d.getValue().itemId(), Comparator.nullsLast(Comparator.naturalOrder()));
        Map<Long, Key> winners = desired.entrySet().stream()
                .filter(d -> d.getValue().charged())
                .collect(Collectors.groupingBy(d -> d.getKey().moduleId(), Collectors.collectingAndThen(
                        Collectors.minBy(preference), w -> w.orElseThrow().getKey())));
        desired.replaceAll((key, d) -> d.charged() && !key.equals(winners.get(key.moduleId()))
                ? new Desired(false, d.itemId()) : d);
    }

    private boolean isProvisioned(Long companyId, ErpModule module) {
        return !module.isRequiresDatabase() || resourceRepository.findByCompanyIdAndResourceTypeAndModuleIdAndStatus(
                companyId, ResourceType.DATABASE, module.getId(), ResourceStatus.VERIFIED).isPresent();
    }

    /** Base counted once (highest), purchased quantities summed over ACTIVE subscriptions (master §13.5). */
    private void recomputeAllowances(Company company, List<Subscription> active, List<SubscriptionItem> items) {
        long includedUsers = 0;
        long includedStorageGb = 0;
        Map<Long, Integer> storageBlockSize = new HashMap<>();
        for (Subscription s : active) {
            CatalogSnapshot snapshot = catalogService.snapshot(s.getBillingPeriod(), s.getCurrency());
            PriceInfo seat = snapshot.prices().get(CatalogSnapshot.USER_SEAT);
            PriceInfo storage = snapshot.prices().get(CatalogSnapshot.STORAGE);
            if (seat != null) {
                includedUsers = Math.max(includedUsers, seat.includedUsers());
            }
            if (storage != null) {
                includedStorageGb = Math.max(includedStorageGb, storage.includedStorageGb());
                storageBlockSize.put(storage.planPriceId(), storage.unitQuantity());
            }
        }
        long purchasedUsers = 0;
        long purchasedStorageGb = 0;
        for (SubscriptionItem item : items) {
            switch (item.getItemType()) {
                case ADDITIONAL_USERS -> purchasedUsers += item.getQuantity().longValue();
                case ADDITIONAL_STORAGE -> purchasedStorageGb += item.getQuantity().longValue()
                        * storageBlockSize.getOrDefault(item.getPlanPriceId(), 1);
                default -> {
                }
            }
        }
        upsertAllowance(company.getId(), AllowanceType.USERS, includedUsers, purchasedUsers);
        upsertAllowance(company.getId(), AllowanceType.STORAGE_BYTES, includedStorageGb * GIB,
                purchasedStorageGb * GIB);
    }

    private void upsertAllowance(Long companyId, AllowanceType type, long included, long purchased) {
        CompanyAllowance a = allowanceRepository.findByCompanyIdAndAllowanceType(companyId, type)
                .orElseGet(() -> {
                    CompanyAllowance n = new CompanyAllowance();
                    n.setCompanyId(companyId);
                    n.setAllowanceType(type);
                    return n;
                });
        a.setIncludedQuantity(included);
        a.setPurchasedQuantity(purchased);
        a.setEffectiveLimit(included + purchased);
        allowanceRepository.save(a);
    }
}
