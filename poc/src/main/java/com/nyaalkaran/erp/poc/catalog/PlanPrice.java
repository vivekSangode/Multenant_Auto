package com.nyaalkaran.erp.poc.catalog;

import com.nyaalkaran.erp.poc.catalog.CatalogEnums.BillingPeriod;
import com.nyaalkaran.erp.poc.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** tbl_plan_price: monthly / yearly price of a plan version (PHASE_01 §6.2 D). */
@Getter
@Setter
@Entity
@Table(name = "tbl_plan_price")
public class PlanPrice extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_version_id", nullable = false)
    private PlanVersion planVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_period", nullable = false, columnDefinition = "varchar(20)")
    private BillingPeriod billingPeriod;

    @Column(name = "currency", nullable = false, columnDefinition = "char(3)")
    private String currency;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;

    /** USER_SEAT: 1 user. STORAGE: the block size in GiB. */
    @Column(name = "unit_quantity", nullable = false)
    private int unitQuantity;

    @Column(name = "included_users", nullable = false)
    private int includedUsers;

    @Column(name = "included_storage_gb", nullable = false)
    private int includedStorageGb;

    @Column(name = "min_quantity", nullable = false)
    private int minQuantity;

    @Column(name = "max_quantity")
    private Integer maxQuantity;
}
