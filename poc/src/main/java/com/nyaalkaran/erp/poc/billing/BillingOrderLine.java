package com.nyaalkaran.erp.poc.billing;

import com.nyaalkaran.erp.poc.billing.PricingEngine.LineType;
import com.nyaalkaran.erp.poc.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** tbl_billing_order_line: references the exact plan version and price it was sold with. */
@Getter
@Setter
@Entity
@Table(name = "tbl_billing_order_line")
public class BillingOrderLine extends BaseEntity {

    @Column(name = "billing_order_id", nullable = false)
    private Long billingOrderId;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "line_type", nullable = false, columnDefinition = "varchar(30)")
    private LineType lineType;

    @Column(name = "plan_version_id", nullable = false)
    private Long planVersionId;

    @Column(name = "plan_price_id", nullable = false)
    private Long planPriceId;

    @Column(name = "module_id")
    private Long moduleId;

    @Column(name = "bundle_id")
    private Long bundleId;

    @Column(name = "description", nullable = false, length = 300)
    private String description;

    @Column(name = "quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "gross_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal grossAmount;

    @Column(name = "line_discount_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal lineDiscountAmount;

    @Column(name = "taxable_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxableAmount;

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxAmount;

    @Column(name = "line_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal lineTotal;
}
