package com.nyaalkaran.erp.poc.subscription;

import com.nyaalkaran.erp.poc.catalog.CatalogEnums.BillingPeriod;
import com.nyaalkaran.erp.poc.common.BaseEntity;
import com.nyaalkaran.erp.poc.subscription.SubscriptionEnums.SubscriptionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** tbl_subscription: created PENDING on order confirmation, ACTIVE only at 100 % verified payment (CD-07). */
@Getter
@Setter
@Entity
@Table(name = "tbl_subscription")
public class Subscription extends BaseEntity {

    @Column(name = "subscription_number", nullable = false, length = 40, updatable = false)
    private String subscriptionNumber;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_period", nullable = false, columnDefinition = "varchar(20)")
    private BillingPeriod billingPeriod;

    @Column(name = "currency", nullable = false, columnDefinition = "char(3)")
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "varchar(30)")
    private SubscriptionStatus status;

    @Column(name = "subscription_type", nullable = false, length = 20)
    private String subscriptionType;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    /** Reserved for future automatic renewal; always false in R1 (manual renewal, CD-07). */
    @Column(name = "auto_renew", nullable = false)
    private boolean autoRenew;

    @Column(name = "cancel_at_period_end", nullable = false)
    private boolean cancelAtPeriodEnd;

    @Column(name = "source_order_id", nullable = false)
    private Long sourceOrderId;

    @Column(name = "end_reason", length = 500)
    private String endReason;
}
