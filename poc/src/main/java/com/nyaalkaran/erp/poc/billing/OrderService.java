package com.nyaalkaran.erp.poc.billing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyaalkaran.erp.poc.billing.BillingEnums.OrderChannel;
import com.nyaalkaran.erp.poc.billing.BillingEnums.OrderKind;
import com.nyaalkaran.erp.poc.billing.BillingEnums.OrderPaymentStatus;
import com.nyaalkaran.erp.poc.billing.BillingEnums.OrderStatus;
import com.nyaalkaran.erp.poc.billing.BillingRepositories.BillingOrderDiscountRepository;
import com.nyaalkaran.erp.poc.billing.BillingRepositories.BillingOrderLineRepository;
import com.nyaalkaran.erp.poc.billing.BillingRepositories.BillingOrderRepository;
import com.nyaalkaran.erp.poc.billing.BillingRepositories.BillingOrderWarningRepository;
import com.nyaalkaran.erp.poc.billing.PricingEngine.ManualDiscount;
import com.nyaalkaran.erp.poc.billing.PricingEngine.Quote;
import com.nyaalkaran.erp.poc.catalog.CatalogEnums.BillingPeriod;
import com.nyaalkaran.erp.poc.catalog.CatalogService;
import com.nyaalkaran.erp.poc.catalog.CatalogSnapshot;
import com.nyaalkaran.erp.poc.common.Actors;
import com.nyaalkaran.erp.poc.common.ApiException;
import com.nyaalkaran.erp.poc.common.AuditService;
import com.nyaalkaran.erp.poc.common.DocumentSeriesService;
import com.nyaalkaran.erp.poc.common.DocumentSeriesService.Series;
import com.nyaalkaran.erp.poc.common.PocProperties;
import com.nyaalkaran.erp.poc.company.Company;
import com.nyaalkaran.erp.poc.company.CompanyService;
import com.nyaalkaran.erp.poc.company.CompanyStatus;
import com.nyaalkaran.erp.poc.subscription.SubscriptionEnums.EntitlementStatus;
import com.nyaalkaran.erp.poc.subscription.SubscriptionEnums.SubscriptionStatus;
import com.nyaalkaran.erp.poc.subscription.SubscriptionRepositories.CompanyEntitlementRepository;
import com.nyaalkaran.erp.poc.subscription.SubscriptionRepositories.SubscriptionRepository;
import com.nyaalkaran.erp.poc.subscription.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final Set<CompanyStatus> ORDERABLE = EnumSet.of(CompanyStatus.DRAFT, CompanyStatus.ACTIVE,
            CompanyStatus.PROVISIONING_FAILED, CompanyStatus.SUSPENDED);

    private final CompanyService companyService;
    private final CatalogService catalogService;
    private final BillingOrderRepository orderRepository;
    private final BillingOrderLineRepository lineRepository;
    private final BillingOrderDiscountRepository discountRepository;
    private final BillingOrderWarningRepository warningRepository;
    private final CompanyEntitlementRepository entitlementRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionService subscriptionService;
    private final DocumentSeriesService documentSeries;
    private final AuditService auditService;
    private final PocProperties props;
    private final ObjectMapper objectMapper;

    public record QuoteCommand(BillingPeriod billingPeriod, String currency, List<String> bundles,
                               List<String> modules, int users, int storageGb, ManualDiscount manualDiscount,
                               String idempotencyKey) {
    }

    /** Builds and freezes a QUOTED order (master §12.2). Idempotent on the idempotency key. */
    @Transactional
    public BillingOrder quote(String companyCode, QuoteCommand cmd) {
        Company company = companyService.get(companyCode);
        var previous = orderRepository.findByIdempotencyKey(cmd.idempotencyKey());
        if (previous.isPresent()) {
            if (!previous.get().getCompanyId().equals(company.getId())) {
                throw ApiException.conflict("IDEMPOTENCY_KEY_REUSED", "Key belongs to another company");
            }
            return previous.get();
        }
        if (!ORDERABLE.contains(company.getStatus())) {
            throw ApiException.conflict("COMPANY_NOT_ORDERABLE", "Company is " + company.getStatus());
        }

        CatalogSnapshot catalog = catalogService.snapshot(cmd.billingPeriod(), cmd.currency());
        Set<String> existing = existingModules(company.getId(), catalog);
        PricingEngine engine = new PricingEngine(props.billing().taxRatePercent());
        Quote quote = engine.price(catalog, new PricingEngine.Request(cmd.bundles(), cmd.modules(), cmd.users(),
                cmd.storageGb(), existing, cmd.manualDiscount()));

        boolean hasActive = !subscriptionRepository.findByCompanyIdAndStatusIn(company.getId(),
                Set.of(SubscriptionStatus.ACTIVE)).isEmpty();
        BillingOrder o = new BillingOrder();
        o.setOrderNumber(documentSeries.next(Series.ORDER));
        o.setCompanyId(company.getId());
        o.setOrderKind(hasActive ? OrderKind.UPGRADE : OrderKind.NEW);
        o.setChannel(OrderChannel.ADMIN);
        o.setBillingPeriod(cmd.billingPeriod());
        o.setCurrency(cmd.currency());
        o.setRequestedUsers(cmd.users());
        o.setRequestedStorageGb(cmd.storageGb());
        o.setSubtotal(quote.subtotal());
        o.setDiscountTotal(quote.discountTotal());
        o.setTaxableTotal(quote.taxableTotal());
        o.setTaxTotal(quote.taxTotal());
        o.setOtherChargesTotal(BigDecimal.ZERO);
        o.setRoundOff(BigDecimal.ZERO);
        o.setGrandTotal(quote.grandTotal());
        o.setPaymentStatus(OrderPaymentStatus.UNPAID);
        o.setStatus(OrderStatus.QUOTED);
        o.setCalculationVersion(PricingEngine.CALCULATION_VERSION);
        o.setInputJson(toJson(cmd, existing));
        o.setValidUntil(LocalDateTime.now().plusDays(props.billing().quoteValidityDays()));
        o.setIdempotencyKey(cmd.idempotencyKey());
        o.setCreatedByActor(Actors.current());
        orderRepository.save(o);

        BigDecimal subtotal = quote.subtotal();
        for (PricingEngine.Line l : quote.lines()) {
            BillingOrderLine line = new BillingOrderLine();
            line.setBillingOrderId(o.getId());
            line.setLineNo(l.lineNo());
            line.setLineType(l.type());
            line.setPlanVersionId(l.price().planVersionId());
            line.setPlanPriceId(l.price().planPriceId());
            line.setModuleId(l.moduleCode() == null ? null : catalog.modules().get(l.moduleCode()).id());
            line.setBundleId(l.bundleCode() == null ? null : catalog.bundles().get(l.bundleCode()).id());
            line.setDescription(l.description());
            line.setQuantity(l.quantity());
            line.setUnitPrice(l.unitPrice());
            line.setGrossAmount(l.grossAmount());
            line.setLineDiscountAmount(BigDecimal.ZERO);
            // Order-level discount and tax are kept at order level in the POC (no per-line allocation).
            line.setTaxableAmount(l.grossAmount());
            line.setTaxAmount(BigDecimal.ZERO);
            line.setLineTotal(l.grossAmount());
            lineRepository.save(line);
        }
        if (cmd.manualDiscount() != null) {
            BillingOrderDiscount d = new BillingOrderDiscount();
            d.setBillingOrderId(o.getId());
            d.setDiscountSource("MANUAL");
            d.setDiscountType(cmd.manualDiscount().type());
            d.setDiscountValue(cmd.manualDiscount().value());
            d.setBaseAmount(subtotal);
            d.setDiscountAmount(quote.discountTotal());
            d.setReason(cmd.manualDiscount().reason());
            d.setSequenceNo(1);
            discountRepository.save(d);
        }
        for (PricingEngine.Warning w : quote.warnings()) {
            BillingOrderWarning bw = new BillingOrderWarning();
            bw.setBillingOrderId(o.getId());
            bw.setCode(w.code());
            bw.setDetail(w.detail());
            warningRepository.save(bw);
        }
        auditService.record("order.quote", company.getCompanyCode(), "BILLING_ORDER", o.getOrderNumber(),
                "grand_total=" + o.getGrandTotal());
        return o;
    }

    /** QUOTED -> AWAITING_PAYMENT; the subscription is created PENDING (PHASE_01 §6.9.2 step 3). */
    @Transactional
    public BillingOrder confirm(Long orderId) {
        BillingOrder o = orderRepository.lockById(orderId).orElseThrow(() -> ApiException.notFound("Order", orderId));
        if (o.getStatus() != OrderStatus.QUOTED) {
            throw ApiException.conflict("INVALID_ORDER_STATE", "Order is " + o.getStatus());
        }
        if (o.getValidUntil() != null && o.getValidUntil().isBefore(LocalDateTime.now())) {
            throw ApiException.conflict("QUOTE_EXPIRED", "The quote validity has passed");
        }
        o.setStatus(OrderStatus.AWAITING_PAYMENT);
        var sub = subscriptionService.createPending(o, lineRepository.findByBillingOrderIdOrderByLineNoAsc(o.getId()));
        o.setSubscriptionId(sub.getId());
        auditService.record("order.confirm", null, "BILLING_ORDER", o.getOrderNumber(), null);
        return o;
    }

    @Transactional
    public BillingOrder cancel(Long orderId, String reason) {
        BillingOrder o = orderRepository.lockById(orderId).orElseThrow(() -> ApiException.notFound("Order", orderId));
        if (o.getStatus() != OrderStatus.QUOTED && o.getStatus() != OrderStatus.AWAITING_PAYMENT) {
            throw ApiException.conflict("INVALID_ORDER_STATE", "Order is " + o.getStatus());
        }
        if (o.getPaymentStatus() != OrderPaymentStatus.UNPAID) {
            throw ApiException.conflict("ORDER_HAS_PAYMENTS", "Reject or reverse the payments first");
        }
        o.setStatus(OrderStatus.CANCELLED);
        auditService.record("order.cancel", null, "BILLING_ORDER", o.getOrderNumber(), reason);
        return o;
    }

    @Transactional(readOnly = true)
    public BillingOrder get(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow(() -> ApiException.notFound("Order", orderId));
    }

    @Transactional(readOnly = true)
    public List<BillingOrder> forCompany(String companyCode) {
        return orderRepository.findByCompanyIdOrderByIdAsc(companyService.get(companyCode).getId());
    }

    /**
     * Modules the company already has or has committed to: non-ENDED entitlements plus orders awaiting
     * payment. Passed to the pricing engine so a module is never charged twice (master §12.2 step 2).
     */
    private Set<String> existingModules(Long companyId, CatalogSnapshot catalog) {
        Map<Long, String> codeById = new HashMap<>();
        catalog.modules().values().forEach(m -> codeById.put(m.id(), m.code()));
        Map<Long, Set<String>> bundleModules = new HashMap<>();
        catalog.bundles().values().forEach(b -> bundleModules.put(b.id(), b.moduleCodes()));

        Set<String> codes = new LinkedHashSet<>();
        entitlementRepository.findByCompanyIdAndStatusNot(companyId, EntitlementStatus.ENDED).stream()
                .map(e -> codeById.get(e.getModuleId()))
                .filter(code -> catalog.modules().get(code).type()
                        != com.nyaalkaran.erp.poc.catalog.CatalogEnums.ModuleType.PLATFORM_CORE)
                .forEach(codes::add);
        for (BillingOrder pending : orderRepository.findByCompanyIdOrderByIdAsc(companyId)) {
            if (pending.getStatus() != OrderStatus.AWAITING_PAYMENT) {
                continue;
            }
            for (BillingOrderLine l : lineRepository.findByBillingOrderIdOrderByLineNoAsc(pending.getId())) {
                if (l.getModuleId() != null) {
                    codes.add(codeById.get(l.getModuleId()));
                }
                if (l.getBundleId() != null) {
                    codes.addAll(bundleModules.getOrDefault(l.getBundleId(), Set.of()));
                }
            }
        }
        return codes;
    }

    private String toJson(QuoteCommand cmd, Set<String> existing) {
        try {
            Map<String, Object> input = new HashMap<>();
            input.put("command", cmd);
            input.put("existingModules", existing);
            return objectMapper.writeValueAsString(input);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
