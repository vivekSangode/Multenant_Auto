package com.nyaalkaran.erp.poc.subscription;

import com.nyaalkaran.erp.poc.common.BaseEntity;
import com.nyaalkaran.erp.poc.subscription.SubscriptionEnums.AllowanceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** tbl_company_allowance: one row per type, summed over all active subscriptions (master §13.5). */
@Getter
@Setter
@Entity
@Table(name = "tbl_company_allowance")
public class CompanyAllowance extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "allowance_type", nullable = false, columnDefinition = "varchar(30)")
    private AllowanceType allowanceType;

    @Column(name = "included_quantity", nullable = false)
    private long includedQuantity;

    @Column(name = "purchased_quantity", nullable = false)
    private long purchasedQuantity;

    @Column(name = "effective_limit", nullable = false)
    private long effectiveLimit;
}
