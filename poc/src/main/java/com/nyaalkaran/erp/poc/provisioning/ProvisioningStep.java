package com.nyaalkaran.erp.poc.provisioning;

import com.nyaalkaran.erp.poc.common.BaseEntity;
import com.nyaalkaran.erp.poc.company.CompanyResource.ResourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** tbl_provisioning_step: explicit ordered steps, persisted when the job is created (master §7.3). */
@Getter
@Setter
@Entity
@Table(name = "tbl_provisioning_step")
public class ProvisioningStep extends BaseEntity {

    public enum StepCode {
        RESERVE_NAMES, CREATE_REALM, CREATE_DATABASE, MIGRATE_SCHEMA, CREATE_BUCKET,
        INVITE_COMPANY_SUPER_ADMIN, ACTIVATE, PUBLISH_CACHES
    }

    public enum StepStatus { PENDING, RUNNING, COMPLETED, FAILED, SKIPPED }

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "step_code", nullable = false, columnDefinition = "varchar(60)")
    private StepCode stepCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", columnDefinition = "varchar(30)")
    private ResourceType resourceType;

    @Column(name = "module_id")
    private Long moduleId;

    @Column(name = "resource_name", length = 100)
    private String resourceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "varchar(30)")
    private StepStatus status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public boolean isDone() {
        return status == StepStatus.COMPLETED || status == StepStatus.SKIPPED;
    }
}
