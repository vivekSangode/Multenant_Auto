package com.nyaalkaran.erp.poc.subscription;

import com.nyaalkaran.erp.poc.catalog.ErpModule;
import com.nyaalkaran.erp.poc.catalog.ErpModuleRepository;
import com.nyaalkaran.erp.poc.company.Company;
import com.nyaalkaran.erp.poc.company.CompanyResource;
import com.nyaalkaran.erp.poc.company.CompanyResourceRepository;
import com.nyaalkaran.erp.poc.company.CompanyService;
import com.nyaalkaran.erp.poc.subscription.SubscriptionRepositories.CompanyAllowanceRepository;
import com.nyaalkaran.erp.poc.subscription.SubscriptionRepositories.CompanyEntitlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** One read model of everything the platform knows about a company (master §13.4 "distinguishing the concepts"). */
@RestController
@RequestMapping("/api/v1/platform/companies")
@RequiredArgsConstructor
public class CompanyOverviewController {

    private final CompanyService companyService;
    private final SubscriptionService subscriptionService;
    private final CompanyEntitlementRepository entitlementRepository;
    private final CompanyAllowanceRepository allowanceRepository;
    private final CompanyResourceRepository resourceRepository;
    private final ErpModuleRepository moduleRepository;
    private final RuntimeCacheService cacheService;

    public record SubscriptionView(Subscription subscription, List<SubscriptionItem> items) {
    }

    public record EntitlementView(String module, SubscriptionEnums.EntitlementSource source, String sourceKey,
                                  boolean charged, SubscriptionEnums.EntitlementStatus status, LocalDate validFrom,
                                  LocalDate validTo) {
    }

    public record ResourceView(String type, String module, String name, String status, String schemaVersion) {
    }

    public record Overview(String companyCode, String status, List<SubscriptionView> subscriptions,
                           List<EntitlementView> entitlements, List<CompanyAllowance> allowances,
                           List<ResourceView> resources, RuntimeCacheService.Projection runtimeCache) {
    }

    @GetMapping("/{companyCode}/overview")
    @Transactional(readOnly = true)
    public Overview overview(@PathVariable String companyCode) {
        Company c = companyService.get(companyCode);
        Map<Long, String> codes = moduleRepository.findAll().stream()
                .collect(Collectors.toMap(ErpModule::getId, ErpModule::getCode));
        List<SubscriptionView> subs = subscriptionService.forCompany(c.getId()).stream()
                .map(s -> new SubscriptionView(s, subscriptionService.items(s.getId()))).toList();
        List<EntitlementView> ents = entitlementRepository.findByCompanyIdOrderByIdAsc(c.getId()).stream()
                .map(e -> new EntitlementView(codes.get(e.getModuleId()), e.getSourceType(), e.getSourceKey(),
                        e.isCharged(), e.getStatus(), e.getValidFrom(), e.getValidTo()))
                .toList();
        List<ResourceView> res = resourceRepository.findByCompanyIdOrderByIdAsc(c.getId()).stream()
                .map((CompanyResource r) -> new ResourceView(r.getResourceType().name(),
                        r.getModuleId() == null ? null : codes.get(r.getModuleId()), r.getResourceName(),
                        r.getStatus().name(), r.getSchemaVersion()))
                .toList();
        return new Overview(c.getCompanyCode(), c.getStatus().name(), subs, ents,
                allowanceRepository.findByCompanyId(c.getId()), res, cacheService.project(c.getId()));
    }
}
