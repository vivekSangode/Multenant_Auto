package com.nyaalkaran.erp.poc.billing;

import com.nyaalkaran.erp.poc.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** tbl_billing_order_warning: DUPLICATE_ENTITLEMENT, BUNDLE_OVERLAP, RECOMMENDED_DEPENDENCY_MISSING, ... */
@Getter
@Setter
@Entity
@Table(name = "tbl_billing_order_warning")
public class BillingOrderWarning extends BaseEntity {

    @Column(name = "billing_order_id", nullable = false)
    private Long billingOrderId;

    @Column(name = "code", nullable = false, length = 60)
    private String code;

    @Column(name = "detail", length = 500)
    private String detail;
}
