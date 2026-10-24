package com.nyaalkaran.erp.poc.subscription;

import com.nyaalkaran.erp.poc.common.BaseEntity;
import com.nyaalkaran.erp.poc.subscription.SubscriptionEnums.SubscriptionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** tbl_subscription_event (append-only transition history). */
@Getter
@Setter
@Entity
@Table(name = "tbl_subscription_event")
public class SubscriptionEvent extends BaseEntity {

    @Column(name = "subscription_id", nullable = false)
    private Long subscriptionId;

    @Column(name = "event_type", nullable = false, length = 30)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", columnDefinition = "varchar(30)")
    private SubscriptionStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, columnDefinition = "varchar(30)")
    private SubscriptionStatus toStatus;

    @Column(name = "billing_order_id")
    private Long billingOrderId;

    @Column(name = "actor", nullable = false, length = 100)
    private String actor;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;
}
