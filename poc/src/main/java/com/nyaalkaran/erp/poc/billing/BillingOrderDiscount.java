package com.nyaalkaran.erp.poc.billing;

import com.nyaalkaran.erp.poc.billing.PricingEngine.DiscountType;
import com.nyaalkaran.erp.poc.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** tbl_billing_order_discount: R1 = an audited MANUAL order-level adjustment with a reason. */
@Getter
@Setter
@Entity
@Table(name = "tbl_billing_order_discount")
public class BillingOrderDiscount extends BaseEntity {

    @Column(name = "billing_order_id", nullable = false)
    private Long billingOrderId;

    @Column(name = "discount_source", nullable = false, length = 20)
    private String discountSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, columnDefinition = "varchar(20)")
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal discountValue;

    @Column(name = "base_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal baseAmount;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal discountAmount;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;
}
