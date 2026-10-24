package com.nyaalkaran.erp.poc.billing;

import com.nyaalkaran.erp.poc.billing.BillingEnums.OrderChannel;
import com.nyaalkaran.erp.poc.billing.BillingEnums.OrderKind;
import com.nyaalkaran.erp.poc.billing.BillingEnums.OrderPaymentStatus;
import com.nyaalkaran.erp.poc.billing.BillingEnums.OrderStatus;
import com.nyaalkaran.erp.poc.catalog.CatalogEnums.BillingPeriod;
import com.nyaalkaran.erp.poc.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** tbl_billing_order: the persisted, frozen calculation result. A quote is an order in QUOTED status. */
@Getter
@Setter
@Entity
@Table(name = "tbl_billing_order")
public class BillingOrder extends BaseEntity {

    @Column(name = "order_number", nullable = false, length = 40, updatable = false)
    private String orderNumber;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "subscription_id")
    private Long subscriptionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_kind", nullable = false, columnDefinition = "varchar(20)")
    private OrderKind orderKind;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, columnDefinition = "varchar(20)")
    private OrderChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_period", nullable = false, columnDefinition = "varchar(20)")
    private BillingPeriod billingPeriod;

    @Column(name = "currency", nullable = false, columnDefinition = "char(3)")
    private String currency;

    @Column(name = "requested_users", nullable = false)
    private int requestedUsers;

    @Column(name = "requested_storage_gb", nullable = false)
    private int requestedStorageGb;

    @Column(name = "subtotal", nullable = false, precision = 19, scale = 4)
    private BigDecimal subtotal;

    @Column(name = "discount_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal discountTotal;

    @Column(name = "taxable_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxableTotal;

    @Column(name = "tax_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxTotal;

    @Column(name = "other_charges_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal otherChargesTotal;

    @Column(name = "round_off", nullable = false, precision = 19, scale = 4)
    private BigDecimal roundOff;

    /** The final payable amount; frozen from QUOTED on. */
    @Column(name = "grand_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal grandTotal;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, columnDefinition = "varchar(20)")
    private OrderPaymentStatus paymentStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "varchar(20)")
    private OrderStatus status;

    @Column(name = "calculation_version", nullable = false, length = 20)
    private String calculationVersion;

    /** Calculation inputs, kept for reproducibility. */
    @Column(name = "input_json", nullable = false, columnDefinition = "json")
    private String inputJson;

    @Column(name = "valid_until")
    private LocalDateTime validUntil;

    @Column(name = "idempotency_key", nullable = false, length = 80, updatable = false)
    private String idempotencyKey;

    @Column(name = "created_by_actor", nullable = false, length = 100)
    private String createdByActor;
}
