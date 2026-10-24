package com.nyaalkaran.erp.poc.provisioning;

import com.nyaalkaran.erp.poc.catalog.ErpModule;
import com.nyaalkaran.erp.poc.catalog.ErpModuleRepository;
import com.nyaalkaran.erp.poc.common.Actors;
import com.nyaalkaran.erp.poc.common.ApiException;
import com.nyaalkaran.erp.poc.common.AuditService;
import com.nyaalkaran.erp.poc.common.TenantResourceNaming;
import com.nyaalkaran.erp.poc.company.Company;
import com.nyaalkaran.erp.poc.company.CompanyRepository;
import com.nyaalkaran.erp.poc.company.CompanyResource.ResourceStatus;
import com.nyaalkaran.erp.poc.company.CompanyResource.ResourceType;
import com.nyaalkaran.erp.poc.company.CompanyResourceRepository;
import com.nyaalkaran.erp.poc.company.CompanyService;
import com.nyaalkaran.erp.poc.company.CompanyStatus;
import com.nyaalkaran.erp.poc.provisioning.ProvisioningJob.JobMode;
import com.nyaalkaran.erp.poc.provisioning.ProvisioningJob.JobStatus;
import com.nyaalkaran.erp.poc.provisioning.ProvisioningJob.JobType;
import com.nyaalkaran.erp.poc.provisioning.ProvisioningRepositories.ProvisioningJobRepository;
import com.nyaalkaran.erp.poc.provisioning.ProvisioningRepositories.ProvisioningStepRepository;
import com.nyaalkaran.erp.poc.provisioning.ProvisioningStep.StepCode;
import com.nyaalkaran.erp.poc.provisioning.ProvisioningStep.StepStatus;
import com.nyaalkaran.erp.poc.subscription.EntitlementsChangedEvent;
import com.nyaalkaran.erp.poc.subscription.SubscriptionEnums.EntitlementStatus;
import com.nyaalkaran.erp.poc.subscription.SubscriptionEnums.SubscriptionStatus;
import com.nyaalkaran.erp.poc.subscription.SubscriptionRepositories.CompanyEntitlementRepository;
import com.nyaalkaran.erp.poc.subscription.SubscriptionRepositories.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Plans provisioning jobs from the active entitlements (master §7.3: entitlement-driven). */
@Service
@RequiredArgsConstructor
public class ProvisioningService {

    static final Set<JobStatus> ACTIVE_STATUSES = EnumSet.of(JobStatus.QUEUED, JobStatus.RUNNING, JobStatus.RETRYING);

    private final CompanyRepository companyRepository;
    private final CompanyService companyService;
    private final CompanyResourceRepository resourceRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final CompanyEntitlementRepository entitlementRepository;
    private final ErpModuleRepository moduleRepository;
    private final ProvisioningJobRepository jobRepository;
    private final ProvisioningStepRepository stepRepository;
    private final AuditService auditService;

    /** POST /companies/{code}/provision: only after full payment (CD-07). */
    @Transactional
    public ProvisioningJob requestCreateCompany(String companyCode) {
        Company company = companyRepository.lockById(companyService.get(companyCode).getId()).orElseThrow();
        if (company.getStatus() != CompanyStatus.DRAFT) {
            throw ApiException.conflict("INVALID_COMPANY_STATE",
                    "Company is " + company.getStatus() + (company.getStatus() == CompanyStatus.PROVISIONING_FAILED
                            ? "; retry the failed job instead" : ""));
        }
        if (subscriptionRepository.findByCompanyIdAndStatusIn(company.getId(), Set.of(SubscriptionStatus.ACTIVE)).isEmpty()) {
            throw ApiException.conflict("SUBSCRIPTION_NOT_ACTIVE",
                    "Provisioning needs an ACTIVE subscription: record and verify the full payment first (CD-07)");
        }
        companyService.transition(company, CompanyStatus.PROVISIONING, "provisioning requested");
        JobMode mode = "IMPORT_EXISTING_DATA".equals(company.getOnboardingType()) ? JobMode.EMPTY_FOR_IMPORT : JobMode.SEEDED;
        return createJob(company, JobType.CREATE_COMPANY, mode, modulesToProvision(company.getId(), false));
    }

    /** A new entitlement on an ACTIVE company: ADD_MODULE job; the company stays ACTIVE (master §7.1). */
    @EventListener
    public void onEntitlementsChanged(EntitlementsChangedEvent e) {
        if (e.pendingProvisioning()) {
            scheduleAddModuleIfNeeded(e.companyId());
        }
    }

    @Transactional
    public void scheduleAddModuleIfNeeded(Long companyId) {
        Company company = companyRepository.findById(companyId).orElseThrow();
        if (company.getStatus() != CompanyStatus.ACTIVE
                || jobRepository.existsByCompanyIdAndStatusIn(companyId, ACTIVE_STATUSES)) {
            return; // the running job re-checks when it completes
        }
        List<ErpModule> missing = modulesToProvision(companyId, true);
        if (!missing.isEmpty()) {
            createJob(company, JobType.ADD_MODULE, JobMode.SEEDED, missing);
        }
    }

    /** Resume a FAILED job: only the steps that are not COMPLETED run again (master §7.3 Resume). */
    @Transactional
    public ProvisioningJob retry(Long jobId) {
        ProvisioningJob job = jobRepository.findById(jobId).orElseThrow(() -> ApiException.notFound("Job", jobId));
        if (job.getStatus() != JobStatus.FAILED) {
            throw ApiException.conflict("INVALID_JOB_STATE", "Job is " + job.getStatus());
        }
        job.setStatus(JobStatus.QUEUED);
        job.setAttempt(0);
        job.setActiveGuard(1);
        job.setNextAttemptAt(null);
        job.setLastError(null);
        stepRepository.findByJobIdOrderBySequenceNoAsc(jobId).stream()
                .filter(s -> s.getStatus() == StepStatus.FAILED)
                .forEach(s -> s.setStatus(StepStatus.PENDING));
        Company company = companyRepository.findById(job.getCompanyId()).orElseThrow();
        if (company.getStatus() == CompanyStatus.PROVISIONING_FAILED) {
            companyService.transition(company, CompanyStatus.PROVISIONING, "retry job " + jobId);
        }
        auditService.record("provisioning.retry", company.getCompanyCode(), "PROVISIONING_JOB", jobId, null);
        return job;
    }

    @Transactional(readOnly = true)
    public List<ProvisioningJob> jobs(String companyCode) {
        return jobRepository.findByCompanyIdOrderByIdAsc(companyService.get(companyCode).getId());
    }

    @Transactional(readOnly = true)
    public List<ProvisioningStep> steps(Long jobId) {
        return stepRepository.findByJobIdOrderBySequenceNoAsc(jobId);
    }

    /**
     * Modules of the non-ENDED entitlements (purchased + dependencies + platform core).
     * onlyMissing = those without a VERIFIED database (or bucket) yet.
     */
    private List<ErpModule> modulesToProvision(Long companyId, boolean onlyMissing) {
        Map<Long, ErpModule> modules = moduleRepository.findAll().stream()
                .collect(Collectors.toMap(ErpModule::getId, Function.identity()));
        return entitlementRepository.findByCompanyIdAndStatusNot(companyId, EntitlementStatus.ENDED).stream()
                .map(e -> modules.get(e.getModuleId()))
                .distinct()
                .filter(m -> !onlyMissing || !isProvisioned(companyId, m))
                .sorted(Comparator.comparing(ErpModule::getSortOrder))
                .toList();
    }

    private boolean isProvisioned(Long companyId, ErpModule m) {
        boolean db = !m.isRequiresDatabase() || resourceRepository.findByCompanyIdAndResourceTypeAndModuleIdAndStatus(
                companyId, ResourceType.DATABASE, m.getId(), ResourceStatus.VERIFIED).isPresent();
        return db;
    }

    private ProvisioningJob createJob(Company company, JobType type, JobMode mode, List<ErpModule> modules) {
        ProvisioningJob job = new ProvisioningJob();
        job.setCompanyId(company.getId());
        job.setJobType(type);
        job.setMode(mode);
        job.setStatus(JobStatus.QUEUED);
        job.setActiveGuard(1);
        job.setRequestedBy(Actors.current());
        jobRepository.save(job);

        String code = company.getCompanyCode();
        List<ProvisioningStep> steps = new ArrayList<>();
        steps.add(step(job, StepCode.RESERVE_NAMES, null, null, null));
        // CREATE_COMPANY creates the realm; ADD_MODULE re-applies it idempotently to add the new module roles (CD-14)
        steps.add(step(job, StepCode.CREATE_REALM, ResourceType.IDP_REALM, null,
                TenantResourceNaming.realmName(code)));
        for (ErpModule m : modules) {
            if (m.isRequiresDatabase()) {
                steps.add(step(job, StepCode.CREATE_DATABASE, ResourceType.DATABASE, m.getId(),
                        TenantResourceNaming.databaseName(code, m.getDbToken())));
            }
        }
        for (ErpModule m : modules) {
            if (m.isRequiresDatabase() && m.isRequiresBootstrap()) {
                steps.add(step(job, StepCode.MIGRATE_SCHEMA, ResourceType.DATABASE, m.getId(),
                        TenantResourceNaming.databaseName(code, m.getDbToken())));
            }
        }
        if (type == JobType.CREATE_COMPANY) {
            // Platform-core per-company bucket (company logo, platform documents)
            steps.add(step(job, StepCode.CREATE_BUCKET, ResourceType.BUCKET, null,
                    TenantResourceNaming.bucketName(code, "control-panel")));
        }
        for (ErpModule m : modules) {
            if (!m.isPlatformCore()) {
                steps.add(step(job, StepCode.CREATE_BUCKET, ResourceType.BUCKET, m.getId(),
                        TenantResourceNaming.bucketName(code, m.getCode())));
            }
        }
        if (type == JobType.CREATE_COMPANY) {
            steps.add(step(job, StepCode.INVITE_COMPANY_SUPER_ADMIN, null, null, null));
        }
        steps.add(step(job, StepCode.ACTIVATE, null, null, null));
        steps.add(step(job, StepCode.PUBLISH_CACHES, null, null, null));
        for (int i = 0; i < steps.size(); i++) {
            steps.get(i).setSequenceNo(i + 1);
        }
        stepRepository.saveAll(steps);
        auditService.record("provisioning.request." + type.name().toLowerCase(), code, "PROVISIONING_JOB",
                job.getId(), modules.stream().map(ErpModule::getCode).collect(Collectors.joining(",")));
        return job;
    }

    private static ProvisioningStep step(ProvisioningJob job, StepCode code, ResourceType type, Long moduleId,
                                         String resourceName) {
        ProvisioningStep s = new ProvisioningStep();
        s.setJobId(job.getId());
        s.setStepCode(code);
        s.setResourceType(type);
        s.setModuleId(moduleId);
        s.setResourceName(resourceName);
        s.setStatus(StepStatus.PENDING);
        return s;
    }
}
