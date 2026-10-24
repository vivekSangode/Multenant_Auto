package com.nyaalkaran.erp.poc.company;

import com.nyaalkaran.erp.poc.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** tbl_company: the customer AND the tenant (master §2.1). One company = one isolated workspace. */
@Getter
@Setter
@Entity
@Table(name = "tbl_company")
public class Company extends BaseEntity {

    /** CMP_n: the single, immutable company identifier (CD-26). */
    @Column(name = "company_code", nullable = false, length = 20, updatable = false)
    private String companyCode;

    @Column(name = "legal_name", nullable = false, length = 150)
    private String legalName;

    @Column(name = "display_name", nullable = false, length = 150)
    private String displayName;

    @Column(name = "slug", nullable = false, length = 40)
    private String slug;

    /** Company Login Code for login discovery (CD-15). */
    @Column(name = "login_code", nullable = false, length = 12)
    private String loginCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "varchar(30)")
    private CompanyStatus status;

    @Column(name = "status_reason", length = 500)
    private String statusReason;

    @Column(name = "onboarding_type", nullable = false, length = 30)
    private String onboardingType;

    @Column(name = "timezone", nullable = false, length = 50)
    private String timezone;

    @Column(name = "retention_days", nullable = false)
    private int retentionDays;

    @Column(name = "super_admin_email", nullable = false, length = 320)
    private String superAdminEmail;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    @Column(name = "suspended_at")
    private LocalDateTime suspendedAt;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;
}
