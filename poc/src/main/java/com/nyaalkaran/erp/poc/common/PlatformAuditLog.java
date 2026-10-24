package com.nyaalkaran.erp.poc.common;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** tbl_platform_audit_log: every platform mutation is audited (master §8.3). */
@Getter
@Setter
@Entity
@Table(name = "tbl_platform_audit_log")
public class PlatformAuditLog extends BaseEntity {

    @Column(name = "actor", nullable = false, length = 100)
    private String actor;

    @Column(name = "action", nullable = false, length = 100)
    private String action;

    @Column(name = "company_code", length = 20)
    private String companyCode;

    @Column(name = "target_type", length = 60)
    private String targetType;

    @Column(name = "target_id", length = 100)
    private String targetId;

    @Column(name = "outcome", nullable = false, length = 20)
    private String outcome;

    @Column(name = "detail", length = 2000)
    private String detail;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;
}
