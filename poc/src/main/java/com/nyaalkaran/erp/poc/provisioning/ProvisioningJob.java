package com.nyaalkaran.erp.poc.provisioning;

import com.nyaalkaran.erp.poc.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * tbl_provisioning_job: durable job claimed by a polling worker with a lease (master §7.3).
 * active_guard = 1 while not terminal, so UQ(company_id, active_guard) allows one active job per company.
 */
@Getter
@Setter
@Entity
@Table(name = "tbl_provisioning_job")
public class ProvisioningJob extends BaseEntity {

    public enum JobType { CREATE_COMPANY, ADD_MODULE, REPAIR, SCHEMA_UPGRADE, REALM_TEMPLATE_UPGRADE }

    public enum JobMode { SEEDED, EMPTY_FOR_IMPORT, UPGRADE }

    public enum JobStatus { QUEUED, RUNNING, RETRYING, FAILED, COMPLETED, CANCELLED }

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, columnDefinition = "varchar(30)")
    private JobType jobType;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, columnDefinition = "varchar(30)")
    private JobMode mode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "varchar(30)")
    private JobStatus status;

    @Column(name = "attempt", nullable = false)
    private int attempt;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "locked_by", length = 100)
    private String lockedBy;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "active_guard", columnDefinition = "tinyint")
    private Integer activeGuard;

    @Column(name = "requested_by", nullable = false, length = 100)
    private String requestedBy;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
