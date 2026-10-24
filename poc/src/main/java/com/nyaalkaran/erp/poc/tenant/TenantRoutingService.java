package com.nyaalkaran.erp.poc.tenant;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.nyaalkaran.erp.poc.catalog.ErpModule;
import com.nyaalkaran.erp.poc.catalog.ErpModuleRepository;
import com.nyaalkaran.erp.poc.common.ApiException;
import com.nyaalkaran.erp.poc.common.PocProperties;
import com.nyaalkaran.erp.poc.company.Company;
import com.nyaalkaran.erp.poc.company.CompanyRepository;
import com.nyaalkaran.erp.poc.company.CompanyResource.ResourceStatus;
import com.nyaalkaran.erp.poc.company.CompanyResource.ResourceType;
import com.nyaalkaran.erp.poc.company.CompanyResourceRepository;
import com.nyaalkaran.erp.poc.company.CompanyStatus;
import com.nyaalkaran.erp.poc.company.CompanyStatusChangedEvent;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;

/**
 * Dynamic per-company routing (master §6.6): (company, module) -> registry DB name -> pooled DataSource built
 * with the environment credentials. Fail-closed: no VERIFIED registry row, no connection.
 * L1 metadata cache 30 s; pools are kept per DB name and closed when evicted or when the company leaves ACTIVE
 * (closing live pools on suspension is a gap called out in master §4).
 */
@Slf4j
@Service
public class TenantRoutingService {

    private final CompanyRepository companyRepository;
    private final CompanyResourceRepository resourceRepository;
    private final ErpModuleRepository moduleRepository;
    private final TenantDataSourceFactory factory;
    private final int maxPoolSize;

    private final Cache<String, String> dbNameCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(30)).maximumSize(10_000).build();
    private final Cache<String, HikariDataSource> pools = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(30)).maximumSize(500)
            .removalListener((String name, HikariDataSource ds, RemovalCause cause) -> {
                if (ds != null) {
                    ds.close();
                }
            })
            .build();

    public TenantRoutingService(CompanyRepository companyRepository, CompanyResourceRepository resourceRepository,
                                ErpModuleRepository moduleRepository, TenantDataSourceFactory factory,
                                PocProperties props) {
        this.companyRepository = companyRepository;
        this.resourceRepository = resourceRepository;
        this.moduleRepository = moduleRepository;
        this.factory = factory;
        this.maxPoolSize = props.tenantDb().maxPoolSize();
    }

    /** JdbcTemplate bound to the CURRENT tenant's database for its module. */
    public JdbcTemplate currentJdbc() {
        TenantContext.Tenant t = TenantContext.require();
        String dbName = resolveDatabase(t.companyCode(), t.moduleCode());
        return new JdbcTemplate(pools.get(dbName, name -> factory.create(name, maxPoolSize)));
    }

    public String resolveDatabase(String companyCode, String moduleCode) {
        return dbNameCache.get(companyCode + "|" + moduleCode, k -> {
            Company company = companyRepository.findByCompanyCode(companyCode)
                    .orElseThrow(() -> ApiException.forbidden("UNKNOWN_COMPANY", "Unknown company"));
            if (company.getStatus() != CompanyStatus.ACTIVE) {
                throw ApiException.unavailable("COMPANY_NOT_READY", "Company is " + company.getStatus());
            }
            ErpModule module = moduleRepository.findByCode(moduleCode)
                    .orElseThrow(() -> ApiException.notFound("Module", moduleCode));
            return resourceRepository.findByCompanyIdAndResourceTypeAndModuleIdAndStatus(company.getId(),
                            ResourceType.DATABASE, module.getId(), ResourceStatus.VERIFIED)
                    .orElseThrow(() -> ApiException.unavailable("COMPANY_NOT_READY",
                            "No verified " + moduleCode + " database for " + companyCode))
                    .getResourceName();
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStatusChanged(CompanyStatusChangedEvent e) {
        if (e.status().accessDeniedCode() == null || e.status() == CompanyStatus.PROVISIONING) {
            return;
        }
        String prefix = e.companyCode().toLowerCase() + "_";
        dbNameCache.asMap().keySet().removeIf(k -> k.startsWith(e.companyCode() + "|"));
        pools.asMap().keySet().stream().filter(n -> n.startsWith(prefix)).toList().forEach(pools::invalidate);
        log.info("Closed live pools of {} ({})", e.companyCode(), e.status());
    }

    @PreDestroy
    void shutdown() {
        pools.invalidateAll();
    }
}
