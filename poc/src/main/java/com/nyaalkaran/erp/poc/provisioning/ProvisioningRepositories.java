package com.nyaalkaran.erp.poc.provisioning;

import com.nyaalkaran.erp.poc.provisioning.ProvisioningJob.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public final class ProvisioningRepositories {

    private ProvisioningRepositories() {
    }

    public interface ProvisioningJobRepository extends JpaRepository<ProvisioningJob, Long> {

        List<ProvisioningJob> findByCompanyIdOrderByIdAsc(Long companyId);

        boolean existsByCompanyIdAndStatusIn(Long companyId, Collection<JobStatus> statuses);

        /** Runnable: queued, due for retry, or RUNNING with an expired lease (a crashed worker). */
        @Query(value = """
                SELECT id FROM tbl_provisioning_job
                WHERE (status IN ('QUEUED', 'RETRYING') AND (next_attempt_at IS NULL OR next_attempt_at <= :now))
                   OR (status = 'RUNNING' AND locked_until < :now)
                ORDER BY id LIMIT 5
                """, nativeQuery = true)
        List<Long> findRunnableIds(LocalDateTime now);

        /** Atomic claim with a lease: only one worker instance can win this UPDATE. */
        @Modifying
        @Query(value = """
                UPDATE tbl_provisioning_job
                SET status = 'RUNNING', locked_by = :worker, locked_until = :leaseUntil, version = version + 1
                WHERE id = :id
                  AND ((status IN ('QUEUED', 'RETRYING') AND (next_attempt_at IS NULL OR next_attempt_at <= :now))
                       OR (status = 'RUNNING' AND locked_until < :now))
                """, nativeQuery = true)
        int claim(Long id, String worker, LocalDateTime now, LocalDateTime leaseUntil);

        @Modifying
        @Query(value = "UPDATE tbl_provisioning_job SET locked_until = :leaseUntil WHERE id = :id AND locked_by = :worker",
                nativeQuery = true)
        int renewLease(Long id, String worker, LocalDateTime leaseUntil);
    }

    public interface ProvisioningStepRepository extends JpaRepository<ProvisioningStep, Long> {
        List<ProvisioningStep> findByJobIdOrderBySequenceNoAsc(Long jobId);
    }
}
