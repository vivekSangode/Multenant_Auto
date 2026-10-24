package com.nyaalkaran.erp.poc.subscription;

import com.nyaalkaran.erp.poc.common.BaseEntity;
import com.nyaalkaran.erp.poc.subscription.SubscriptionEnums.ItemStatus;
import com.nyaalkaran.erp.poc.subscription.SubscriptionEnums.ItemType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** tbl_subscription_item: keeps the plan version and price snapshot it was sold with. */
@Getter
@Setter
@Entity
@Table(name = "tbl_subscription_item")
public class SubscriptionItem extends BaseEntity {

    @Column(name = "subscription_id", nullable = false)
    private Long subscriptionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, columnDefinition = "varchar(30)")
    private ItemType itemType;

    @Column(name = "plan_version_id", nullable = false)
    private Long planVersionId;

    @Column(name = "plan_price_id", nullable = false)
    private Long planPriceId;

    @Column(name = "module_id")
    private Long moduleId;

    @Column(name = "bundle_id")
    private Long bundleId;

    @Column(name = "quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(name = "unit_price_snapshot", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPriceSnapshot;

    @Column(name = "source_order_line_id")
    private Long sourceOrderLineId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "varchar(30)")
    private ItemStatus status;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;
}
