package com.nyaalkaran.erp.poc.billing;

import com.nyaalkaran.erp.poc.billing.BillingEnums.PaymentStatus;
import com.nyaalkaran.erp.poc.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** tbl_payment_status_history (append-only). */
@Getter
@Setter
@Entity
@Table(name = "tbl_payment_status_history")
public class PaymentStatusHistory extends BaseEntity {

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", columnDefinition = "varchar(30)")
    private PaymentStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, columnDefinition = "varchar(30)")
    private PaymentStatus toStatus;

    @Column(name = "actor_name", nullable = false, length = 100)
    private String actorName;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;
}
