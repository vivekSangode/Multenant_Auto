package com.nyaalkaran.erp.poc.provisioning;

import com.nyaalkaran.erp.poc.common.PocProperties;
import com.nyaalkaran.erp.poc.company.Company;
import com.nyaalkaran.erp.poc.company.CompanyRepository;
import com.nyaalkaran.erp.poc.company.CompanyService;
import com.nyaalkaran.erp.poc.company.CompanyStatus;
import com.nyaalkaran.erp.poc.provisioning.ProvisioningJob.JobStatus;
import com.nyaalkaran.erp.poc.provisioning.ProvisioningJob.JobType;
import com.nyaalkaran.erp.poc.provisioning.ProvisioningRepositories.ProvisioningJobRepository;
import com.nyaalkaran.erp.poc.provisioning.ProvisioningRepositories.ProvisioningStepRepository;
import com.nyaalkaran.erp.poc.provisioning.ProvisioningStep.StepStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.management.ManagementFactory;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Durable polling worker (master §7.3). Jobs are claimed with an atomic conditional UPDATE and a lease, so
 * several instances can run safely and a crashed worker's job is picked up again once its lease expires.
 * Failed steps are retried automatically with exponential backoff; after max attempts the job is FAILED and
 * the company PROVISIONING_FAILED. Nothing is ever dropped (no destructive compensation).
 */
@Slf4j
@Component
public class ProvisioningWorker {

    private final String workerId = ManagementFactory.getRuntimeMXBean().getName() + "/" + UUID.randomUUID();
    private final ProvisioningJobRepository jobRepository;
    private final ProvisioningStepRepository stepRepository;
    private final CompanyRepository companyRepository;
    private final CompanyService companyService;
    private final ProvisioningService provisioningService;
    private final StepExecutor executor;
    private final TransactionTemplate tx;
    private final PocProperties.Provisioning cfg;

    public ProvisioningWorker(ProvisioningJobRepository jobRepository, ProvisioningStepRepository stepRepository,
                              CompanyRepository companyRepository, CompanyService companyService,
                              ProvisioningService provisioningService, StepExecutor executor,
                              TransactionTemplate tx, PocProperties props) {
        this.jobRepository = jobRepository;
        this.stepRepository = stepRepository;
        this.companyRepository = companyRepository;
        this.companyService = companyService;
        this.provisioningService = provisioningService;
        this.executor = executor;
        this.tx = tx;
        this.cfg = props.provisioning();
    }

    @Scheduled(fixedDelayString = "${poc.provisioning.poll-interval-ms:2000}", initialDelay = 2000)
    public void poll() {
        if (!cfg.workerEnabled()) {
            return;
        }
        for (Long jobId : jobRepository.findRunnableIds(LocalDateTime.now())) {
            Integer claimed = tx.execute(s -> jobRepository.claim(jobId, workerId, LocalDateTime.now(),
                    LocalDateTime.now().plusSeconds(cfg.leaseSeconds())));
            if (claimed != null && claimed == 1) {
                run(jobId);
            }
        }
    }

    void run(Long jobId) {
        ProvisioningJob job = jobRepository.findById(jobId).orElseThrow();
        for (ProvisioningStep step : stepRepository.findByJobIdOrderBySequenceNoAsc(jobId)) {
            if (step.isDone()) {
                continue;
            }
            mark(step.getId(), StepStatus.RUNNING, null);
            try {
                boolean ran = executor.execute(job, step);
                mark(step.getId(), ran ? StepStatus.COMPLETED : StepStatus.SKIPPED, null);
                log.info("[{}] job {} step {} {} {}", job.getJobType(), jobId, step.getSequenceNo(),
                        step.getStepCode(), ran ? "COMPLETED" : "SKIPPED");
                tx.executeWithoutResult(s -> jobRepository.renewLease(jobId, workerId,
                        LocalDateTime.now().plusSeconds(cfg.leaseSeconds())));
            } catch (Exception ex) {
                String error = scrub(ex);
                log.warn("[{}] job {} step {} {} failed: {}", job.getJobType(), jobId, step.getSequenceNo(),
                        step.getStepCode(), error);
                mark(step.getId(), StepStatus.FAILED, error);
                fail(jobId, error);
                return;
            }
        }
        complete(jobId);
    }

    private void mark(Long stepId, StepStatus status, String error) {
        tx.executeWithoutResult(s -> {
            ProvisioningStep step = stepRepository.findById(stepId).orElseThrow();
            step.setStatus(status);
            if (status == StepStatus.RUNNING) {
                step.setAttempts(step.getAttempts() + 1);
                step.setStartedAt(LocalDateTime.now());
            } else if (status == StepStatus.COMPLETED || status == StepStatus.SKIPPED) {
                step.setCompletedAt(LocalDateTime.now());
                step.setLastError(null);
            } else {
                step.setLastError(error);
            }
        });
    }

    private void fail(Long jobId, String error) {
        tx.executeWithoutResult(s -> {
            ProvisioningJob job = jobRepository.findById(jobId).orElseThrow();
            job.setAttempt(job.getAttempt() + 1);
            job.setLastError(error);
            job.setLockedBy(null);
            job.setLockedUntil(null);
            if (job.getAttempt() >= cfg.maxAttempts()) {
                job.setStatus(JobStatus.FAILED);
                job.setActiveGuard(null);
                Company company = companyRepository.lockById(job.getCompanyId()).orElseThrow();
                if (job.getJobType() == JobType.CREATE_COMPANY && company.getStatus() == CompanyStatus.PROVISIONING) {
                    companyService.transition(company, CompanyStatus.PROVISIONING_FAILED, error);
                }
            } else {
                long delay = Math.min((long) cfg.backoffBaseSeconds() << (job.getAttempt() - 1), cfg.backoffMaxSeconds());
                job.setStatus(JobStatus.RETRYING);
                job.setNextAttemptAt(LocalDateTime.now().plusSeconds(delay));
            }
        });
    }

    private void complete(Long jobId) {
        Long companyId = tx.execute(s -> {
            ProvisioningJob job = jobRepository.findById(jobId).orElseThrow();
            job.setStatus(JobStatus.COMPLETED);
            job.setActiveGuard(null);
            job.setCompletedAt(LocalDateTime.now());
            job.setLockedBy(null);
            job.setLockedUntil(null);
            job.setLastError(null);
            return job.getCompanyId();
        });
        log.info("Provisioning job {} COMPLETED", jobId);
        // Entitlements bought while this job ran get their own ADD_MODULE job now.
        provisioningService.scheduleAddModuleIfNeeded(companyId);
    }

    /** Stored errors are truncated and never carry credentials (master §7.3 Retries). */
    private static String scrub(Exception ex) {
        String msg = ex.getClass().getSimpleName() + ": " + ex.getMessage();
        msg = msg.replaceAll("(?i)(password|secret|key)=\\S+", "$1=***");
        return msg.length() > 1000 ? msg.substring(0, 1000) : msg;
    }
}
