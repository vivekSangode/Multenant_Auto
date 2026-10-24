package com.nyaalkaran.erp.poc.provisioning;

import com.nyaalkaran.erp.poc.catalog.ErpModule;
import com.nyaalkaran.erp.poc.catalog.ErpModuleRepository;
import com.nyaalkaran.erp.poc.common.CompanyRoles;
import com.nyaalkaran.erp.poc.common.PocProperties;
import com.nyaalkaran.erp.poc.common.TenantResourceNaming;
import com.nyaalkaran.erp.poc.company.Company;
import com.nyaalkaran.erp.poc.company.CompanyRepository;
import com.nyaalkaran.erp.poc.company.CompanyResource;
import com.nyaalkaran.erp.poc.company.CompanyResource.ResourceStatus;
import com.nyaalkaran.erp.poc.company.CompanyResource.ResourceType;
import com.nyaalkaran.erp.poc.company.CompanyResourceRepository;
import com.nyaalkaran.erp.poc.company.CompanyService;
import com.nyaalkaran.erp.poc.company.CompanyStatus;
import com.nyaalkaran.erp.poc.provisioning.ProvisioningJob.JobType;
import com.nyaalkaran.erp.poc.provisioning.ProvisioningRepositories.ProvisioningStepRepository;
import com.nyaalkaran.erp.poc.provisioning.ProvisioningStep.StepCode;
import com.nyaalkaran.erp.poc.subscription.EntitlementsChangedEvent;
import com.nyaalkaran.erp.poc.subscription.SubscriptionEnums.EntitlementStatus;
import com.nyaalkaran.erp.poc.subscription.SubscriptionRepositories.CompanyEntitlementRepository;
import com.nyaalkaran.erp.poc.subscription.RuntimeCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Executes one provisioning step. Every step checks the real state first and is idempotent:
 * "create if missing", never "create or fail" (master §7.3). A failure never drops anything.
 *
 * @return true if the step ran, false if it was intentionally SKIPPED
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StepExecutor {

    private static final Pattern SAFE_DB_NAME = Pattern.compile("^cmp_[0-9]{1,8}_[a-z0-9_]{1,40}_db$");

    private final JdbcTemplate controlPlaneJdbc;
    private final TransactionTemplate tx;
    private final CompanyRepository companyRepository;
    private final CompanyService companyService;
    private final CompanyResourceRepository resourceRepository;
    private final CompanyEntitlementRepository entitlementRepository;
    private final ErpModuleRepository moduleRepository;
    private final ProvisioningStepRepository stepRepository;
    private final TenantSchemaMigrator schemaMigrator;
    private final StorageService storageService;
    private final RealmProvisioner realmProvisioner;
    private final RuntimeCacheService cacheService;
    private final ApplicationEventPublisher events;
    private final PocProperties props;

    public boolean execute(ProvisioningJob job, ProvisioningStep step) throws Exception {
        return switch (step.getStepCode()) {
            case RESERVE_NAMES -> reserveNames(job);
            case CREATE_REALM -> createRealm(job, step);
            case INVITE_COMPANY_SUPER_ADMIN -> inviteCompanySuperAdmin(job);
            case CREATE_DATABASE -> createDatabase(step);
            case MIGRATE_SCHEMA -> migrateSchema(step);
            case CREATE_BUCKET -> createBucket(step);
            case ACTIVATE -> activate(job);
            case PUBLISH_CACHES -> {
                cacheService.publish(job.getCompanyId());
                yield true;
            }
        };
    }

    /** Reserve every name of the job under UQ(resource_type, resource_name) before anything is created. */
    private boolean reserveNames(ProvisioningJob job) {
        tx.executeWithoutResult(status -> {
            for (ProvisioningStep s : stepRepository.findByJobIdOrderBySequenceNoAsc(job.getId())) {
                if (s.getResourceName() == null || s.getStepCode() == StepCode.MIGRATE_SCHEMA) {
                    continue;
                }
                var existing = resourceRepository.findByResourceTypeAndResourceName(s.getResourceType(),
                        s.getResourceName());
                if (existing.isPresent()) {
                    if (!existing.get().getCompanyId().equals(job.getCompanyId())) {
                        throw new IllegalStateException("NAME_COLLISION: " + s.getResourceName());
                    }
                    continue;
                }
                CompanyResource r = new CompanyResource();
                r.setCompanyId(job.getCompanyId());
                r.setResourceType(s.getResourceType());
                r.setModuleId(s.getModuleId());
                r.setResourceName(s.getResourceName());
                r.setStatus(ResourceStatus.RESERVED);
                if (s.getResourceType() == ResourceType.DATABASE) {
                    r.setDbHost(props.tenantDb().host());
                    r.setDbPort(props.tenantDb().port());
                }
                resourceRepository.save(r);
            }
        });
        return true;
    }

    /**
     * Realm co-<n> from the template (CD-06): hard-coded tenant_id = CMP_n, COMPANY_* roles and the module roles of
     * every non-ENDED, non-platform-core entitlement (CD-14). Runs again in ADD_MODULE jobs to add new module roles.
     */
    private boolean createRealm(ProvisioningJob job, ProvisioningStep step) {
        Company company = companyRepository.findById(job.getCompanyId()).orElseThrow();
        List<String> roles = new ArrayList<>(CompanyRoles.COMPANY_ROLES);
        entitlementRepository.findByCompanyIdAndStatusNot(company.getId(), EntitlementStatus.ENDED).stream()
                .map(e -> moduleRepository.findById(e.getModuleId()).orElseThrow())
                .filter(m -> !m.isPlatformCore())
                .distinct()
                .forEach(m -> roles.addAll(CompanyRoles.moduleRoles(m.getGatewayToken())));
        realmProvisioner.ensureRealm(step.getResourceName(), company.getCompanyCode(), company.getDisplayName(), roles);
        realmProvisioner.verify(step.getResourceName(), company.getCompanyCode(), roles);
        updateResource(step, ResourceStatus.VERIFIED, null);
        return true;
    }

    /** The Company Super Admin from tbl_company.super_admin_email is the first user of the company realm. */
    private boolean inviteCompanySuperAdmin(ProvisioningJob job) {
        Company company = companyRepository.findById(job.getCompanyId()).orElseThrow();
        String realm = TenantResourceNaming.realmName(company.getCompanyCode());
        String userId = realmProvisioner.inviteCompanySuperAdmin(realm, company.getSuperAdminEmail());
        log.info("Company Super Admin of {} is user {} in realm {}", company.getCompanyCode(), userId, realm);
        return true;
    }

    private boolean createDatabase(ProvisioningStep step) {
        String name = step.getResourceName();
        if (!SAFE_DB_NAME.matcher(name).matches()) {
            throw new IllegalStateException("Refusing unsafe database name " + name);
        }
        // DDL cannot be bound as a parameter; the name is validated above and derived only from CMP_n + db_token.
        controlPlaneJdbc.execute("CREATE DATABASE IF NOT EXISTS `" + name
                + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        updateResource(step, ResourceStatus.CREATED, null);
        return true;
    }

    private boolean migrateSchema(ProvisioningStep step) throws Exception {
        ErpModule module = moduleRepository.findById(step.getModuleId()).orElseThrow();
        String version = schemaMigrator.migrate(step.getResourceName(), module.getDbToken(), module.getCode());
        updateResource(step, ResourceStatus.VERIFIED, version);
        return true;
    }

    private boolean createBucket(ProvisioningStep step) throws Exception {
        if (!storageService.enabled()) {
            return false;
        }
        storageService.ensureBucket(step.getResourceName());
        if (!storageService.bucketExists(step.getResourceName())) {
            throw new IllegalStateException("Bucket not visible after creation: " + step.getResourceName());
        }
        updateResource(step, ResourceStatus.VERIFIED, null);
        return true;
    }

    /** Entitlements PENDING -> ACTIVE for provisioned modules; CREATE_COMPANY also activates the company. */
    private boolean activate(ProvisioningJob job) {
        tx.executeWithoutResult(status -> {
            Company company = companyRepository.lockById(job.getCompanyId()).orElseThrow();
            if (job.getJobType() == JobType.CREATE_COMPANY && company.getStatus() == CompanyStatus.PROVISIONING) {
                CompanyStatus target = "IMPORT_EXISTING_DATA".equals(company.getOnboardingType())
                        ? CompanyStatus.AWAITING_DATA_IMPORT : CompanyStatus.ACTIVE;
                companyService.transition(company, target, "all mandatory steps verified");
            }
            if (company.getStatus() == CompanyStatus.ACTIVE) {
                for (var e : entitlementRepository.findByCompanyIdAndStatus(company.getId(), EntitlementStatus.PENDING)) {
                    ErpModule m = moduleRepository.findById(e.getModuleId()).orElseThrow();
                    boolean ready = !m.isRequiresDatabase() || resourceRepository
                            .findByCompanyIdAndResourceTypeAndModuleIdAndStatus(company.getId(),
                                    ResourceType.DATABASE, m.getId(), ResourceStatus.VERIFIED).isPresent();
                    if (ready) {
                        e.setStatus(EntitlementStatus.ACTIVE);
                    }
                }
                events.publishEvent(new EntitlementsChangedEvent(company.getId(), company.getCompanyCode(), false));
            }
        });
        return true;
    }

    private void updateResource(ProvisioningStep step, ResourceStatus status, String schemaVersion) {
        tx.executeWithoutResult(s -> {
            CompanyResource r = resourceRepository.findByResourceTypeAndResourceName(step.getResourceType(),
                    step.getResourceName()).orElseThrow(() ->
                    new IllegalStateException("Resource not reserved: " + step.getResourceName()));
            if (r.getStatus() != ResourceStatus.VERIFIED || status == ResourceStatus.VERIFIED) {
                r.setStatus(status);
            }
            if (schemaVersion != null) {
                r.setSchemaVersion(schemaVersion);
            }
            if (status == ResourceStatus.VERIFIED) {
                r.setVerifiedAt(LocalDateTime.now());
            }
        });
    }
}
