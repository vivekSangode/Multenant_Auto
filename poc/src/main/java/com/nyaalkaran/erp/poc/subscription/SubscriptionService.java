package com.nyaalkaran.erp.poc.subscription;

import com.nyaalkaran.erp.poc.billing.BillingOrder;
import com.nyaalkaran.erp.poc.billing.BillingOrderLine;
import com.nyaalkaran.erp.poc.catalog.CatalogEnums.BillingPeriod;
import com.nyaalkaran.erp.poc.common.Actors;
import com.nyaalkaran.erp.poc.common.ApiException;
import com.nyaalkaran.erp.poc.common.DocumentSeriesService;
import com.nyaalkaran.erp.poc.common.DocumentSeriesService.Series;
import com.nyaalkaran.erp.poc.subscription.SubscriptionEnums.ItemStatus;
import com.nyaalkaran.erp.poc.subscription.SubscriptionEnums.ItemType;
import com.nyaalkaran.erp.poc.subscription.SubscriptionEnums.SubscriptionStatus;
import com.nyaalkaran.erp.poc.subscription.SubscriptionRepositories.SubscriptionEventRepository;
import com.nyaalkaran.erp.poc.subscription.SubscriptionRepositories.SubscriptionItemRepository;
import com.nyaalkaran.erp.poc.subscription.SubscriptionRepositories.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionItemRepository itemRepository;
    private final SubscriptionEventRepository eventRepository;
    private final DocumentSeriesService documentSeries;
    private final EntitlementEngine entitlementEngine;

    /** Order confirmation (PHASE_01 §6.9.2 step 3): the subscription is created PENDING with its items. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Subscription createPending(BillingOrder order, List<BillingOrderLine> lines) {
        Subscription s = new Subscription();
        s.setSubscriptionNumber(documentSeries.next(Series.SUBSCRIPTION));
        s.setCompanyId(order.getCompanyId());
        s.setBillingPeriod(order.getBillingPeriod());
        s.setCurrency(order.getCurrency());
        s.setStatus(SubscriptionStatus.PENDING);
        s.setSubscriptionType("PAID");
        s.setSourceOrderId(order.getId());
        subscriptionRepository.save(s);

        for (BillingOrderLine line : lines) {
            SubscriptionItem item = new SubscriptionItem();
            item.setSubscriptionId(s.getId());
            item.setItemType(ItemType.valueOf(line.getLineType().name()));
            item.setPlanVersionId(line.getPlanVersionId());
            item.setPlanPriceId(line.getPlanPriceId());
            item.setModuleId(line.getModuleId());
            item.setBundleId(line.getBundleId());
            item.setQuantity(line.getQuantity());
            item.setUnitPriceSnapshot(line.getUnitPrice());
            item.setSourceOrderLineId(line.getId());
            item.setStatus(ItemStatus.SCHEDULED);
            item.setEffectiveFrom(LocalDate.now());
            itemRepository.save(item);
        }
        event(s, "CREATED", null, SubscriptionStatus.PENDING, order.getId(), "order confirmed");
        return s;
    }

    /** 100 % of the order is verified paid (CD-07): ACTIVE, items ACTIVE, entitlements recomputed. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Subscription activate(BillingOrder order) {
        Subscription s = subscriptionRepository.findBySourceOrderId(order.getId())
                .orElseThrow(() -> ApiException.conflict("NO_SUBSCRIPTION", "Order has no subscription"));
        if (s.getStatus() != SubscriptionStatus.PENDING) {
            throw ApiException.conflict("INVALID_SUBSCRIPTION_STATE", "Subscription is " + s.getStatus());
        }
        LocalDate start = LocalDate.now();
        s.setStatus(SubscriptionStatus.ACTIVE);
        s.setStartDate(start);
        s.setEndDate(s.getBillingPeriod() == BillingPeriod.YEARLY
                ? start.plusYears(1).minusDays(1) : start.plusMonths(1).minusDays(1));
        for (SubscriptionItem item : itemRepository.findBySubscriptionIdOrderByIdAsc(s.getId())) {
            item.setStatus(ItemStatus.ACTIVE);
            item.setEffectiveFrom(start);
        }
        event(s, "ACTIVATED", SubscriptionStatus.PENDING, SubscriptionStatus.ACTIVE, order.getId(),
                "full payment verified");
        entitlementEngine.recompute(s.getCompanyId());
        return s;
    }

    @Transactional(readOnly = true)
    public List<Subscription> forCompany(Long companyId) {
        return subscriptionRepository.findByCompanyIdOrderByIdAsc(companyId);
    }

    @Transactional(readOnly = true)
    public List<SubscriptionItem> items(Long subscriptionId) {
        return itemRepository.findBySubscriptionIdOrderByIdAsc(subscriptionId);
    }

    private void event(Subscription s, String type, SubscriptionStatus from, SubscriptionStatus to, Long orderId,
                       String reason) {
        SubscriptionEvent e = new SubscriptionEvent();
        e.setSubscriptionId(s.getId());
        e.setEventType(type);
        e.setFromStatus(from);
        e.setToStatus(to);
        e.setBillingOrderId(orderId);
        e.setActor(Actors.current());
        e.setReason(reason);
        e.setOccurredAt(LocalDateTime.now());
        eventRepository.save(e);
    }
}
