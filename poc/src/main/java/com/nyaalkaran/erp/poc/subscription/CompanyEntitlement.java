package com.nyaalkaran.erp.poc.subscription;

import com.nyaalkaran.erp.poc.common.BaseEntity;
import com.nyaalkaran.erp.poc.subscription.SubscriptionEnums.EntitlementSource;
import com.nyaalkaran.erp.poc.subscription.SubscriptionEnums.EntitlementStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * tbl_company_entitlement: one row per (company, module, source), materialized by the EntitlementEngine
 * (master §13.3). charged_guard = 1 while charged and not ENDED, so UQ(company, module, charged_guard)
 * prevents charging a module twice across subscriptions.
 */
@Getter
@Setter
@Entity
@Table(name = "tbl_company_entitlement")
public class CompanyEntitlement extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "module_id", nullable = false)
    private Long moduleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, columnDefinition = "varchar(30)")
    private EntitlementSource sourceType;

    @Column(name = "source_item_id")
    private Long sourceItemId;

    @Column(name = "source_key", nullable = false, length = 100)
    private String sourceKey;

    @Column(name = "is_charged", nullable = false)
    private boolean charged;

    @Column(name = "charged_guard", columnDefinition = "tinyint")
    private Integer chargedGuard;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "varchar(30)")
    private EntitlementStatus status;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    public void end(LocalDate today) {
        this.status = EntitlementStatus.ENDED;
        this.chargedGuard = null;
        this.validTo = today;
    }
}
