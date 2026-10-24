package com.nyaalkaran.erp.poc.billing;

import com.nyaalkaran.erp.poc.billing.BillingEnums.PaymentMode;
import com.nyaalkaran.erp.poc.billing.BillingEnums.PaymentStatus;
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

/**
 * tbl_payment (+ the tbl_manual_payment fields, merged in the POC). One instrument; several instruments may
 * together make up the order total (CD-20). Never deleted; amounts never updated after confirmation.
 */
@Getter
@Setter
@Entity
@Table(name = "tbl_payment")
public class Payment extends BaseEntity {

    @Column(name = "payment_number", nullable = false, length = 40, updatable = false)
    private String paymentNumber;

    @Column(name = "receipt_number", nullable = false, length = 40, updatable = false)
    private String receiptNumber;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "billing_order_id", nullable = false)
    private Long billingOrderId;

    @Column(name = "invoice_id")
    private Long invoiceId;

    @Column(name = "payment_channel", nullable = false, length = 20)
    private String paymentChannel;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode", nullable = false, columnDefinition = "varchar(30)")
    private PaymentMode paymentMode;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, columnDefinition = "char(3)")
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "varchar(30)")
    private PaymentStatus status;

    @Column(name = "instrument_ref", length = 100)
    private String instrumentRef;

    @Column(name = "instrument_date")
    private LocalDate instrumentDate;

    @Column(name = "bank_name", length = 150)
    private String bankName;

    @Column(name = "verification_required", nullable = false)
    private boolean verificationRequired;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "received_by", nullable = false, length = 100)
    private String receivedBy;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "idempotency_key", nullable = false, length = 80, updatable = false)
    private String idempotencyKey;
}
