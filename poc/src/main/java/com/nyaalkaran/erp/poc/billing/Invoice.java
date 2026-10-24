package com.nyaalkaran.erp.poc.billing;

import com.nyaalkaran.erp.poc.billing.BillingEnums.InvoiceStatus;
import com.nyaalkaran.erp.poc.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** tbl_invoice: issued on full payment (invoice_issue_trigger = ON_FULL_PAYMENT, Q-42 default). */
@Getter
@Setter
@Entity
@Table(name = "tbl_invoice")
public class Invoice extends BaseEntity {

    @Column(name = "invoice_number", nullable = false, length = 40, updatable = false)
    private String invoiceNumber;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "billing_order_id", nullable = false)
    private Long billingOrderId;

    @Column(name = "invoice_type", nullable = false, length = 20)
    private String invoiceType;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "currency", nullable = false, columnDefinition = "char(3)")
    private String currency;

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

    @Column(name = "grand_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal grandTotal;

    @Column(name = "amount_paid", nullable = false, precision = 19, scale = 4)
    private BigDecimal amountPaid;

    @Column(name = "amount_credited", nullable = false, precision = 19, scale = 4)
    private BigDecimal amountCredited;

    @Column(name = "balance_due", nullable = false, precision = 19, scale = 4)
    private BigDecimal balanceDue;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "varchar(20)")
    private InvoiceStatus status;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;
}
