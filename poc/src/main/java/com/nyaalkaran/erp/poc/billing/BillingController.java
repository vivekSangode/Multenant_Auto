package com.nyaalkaran.erp.poc.billing;

import com.nyaalkaran.erp.poc.billing.BillingEnums.PaymentMode;
import com.nyaalkaran.erp.poc.billing.BillingRepositories.BillingOrderDiscountRepository;
import com.nyaalkaran.erp.poc.billing.BillingRepositories.BillingOrderLineRepository;
import com.nyaalkaran.erp.poc.billing.BillingRepositories.BillingOrderWarningRepository;
import com.nyaalkaran.erp.poc.billing.BillingRepositories.InvoiceRepository;
import com.nyaalkaran.erp.poc.billing.OrderService.QuoteCommand;
import com.nyaalkaran.erp.poc.billing.PaymentService.RecordPaymentCommand;
import com.nyaalkaran.erp.poc.billing.PricingEngine.ManualDiscount;
import com.nyaalkaran.erp.poc.catalog.CatalogEnums.BillingPeriod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Admin-panel billing flow (PHASE_01 §6.9.2): quote -> confirm -> record / verify payments -> fulfilled. */
@RestController
@RequestMapping("/api/v1/platform")
@RequiredArgsConstructor
public class BillingController {

    private final OrderService orderService;
    private final PaymentService paymentService;
    private final BillingOrderLineRepository lineRepository;
    private final BillingOrderWarningRepository warningRepository;
    private final BillingOrderDiscountRepository discountRepository;
    private final InvoiceRepository invoiceRepository;

    public record QuoteRequest(
            @NotNull BillingPeriod billingPeriod,
            @Pattern(regexp = "[A-Z]{3}") String currency,
            List<@NotBlank String> bundles,
            List<@NotBlank String> modules,
            @Min(1) @Max(100_000) int users,
            @Min(0) @Max(1_000_000) int storageGb,
            @Valid ManualDiscountRequest manualDiscount,
            @Size(max = 80) String idempotencyKey) {
    }

    public record ManualDiscountRequest(@NotNull PricingEngine.DiscountType type,
                                        @NotNull @DecimalMin("0") BigDecimal value,
                                        @NotBlank @Size(max = 500) String reason) {
    }

    public record PaymentRequest(@NotNull PaymentMode mode, @NotNull @DecimalMin("0.01") BigDecimal amount,
                                 @Size(max = 100) String instrumentRef, LocalDate instrumentDate,
                                 @Size(max = 150) String bankName, @Size(max = 100) String receivedBy,
                                 @Size(max = 80) String idempotencyKey) {
    }

    public record RejectRequest(@NotBlank @Size(max = 500) String reason) {
    }

    public record OrderView(BillingOrder order, List<BillingOrderLine> lines, List<BillingOrderDiscount> discounts,
                            List<BillingOrderWarning> warnings, List<Payment> payments, Invoice invoice) {
    }

    @PostMapping("/companies/{companyCode}/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderView quote(@PathVariable String companyCode, @Valid @RequestBody QuoteRequest req) {
        ManualDiscount discount = req.manualDiscount() == null ? null : new ManualDiscount(
                req.manualDiscount().type(), req.manualDiscount().value(), req.manualDiscount().reason());
        BillingOrder o = orderService.quote(companyCode, new QuoteCommand(req.billingPeriod(),
                req.currency() == null ? "INR" : req.currency(), req.bundles(), req.modules(), req.users(),
                req.storageGb(), discount, keyOr(req.idempotencyKey())));
        return view(o.getId());
    }

    @GetMapping("/companies/{companyCode}/orders")
    public List<BillingOrder> orders(@PathVariable String companyCode) {
        return orderService.forCompany(companyCode);
    }

    @GetMapping("/orders/{orderId}")
    @Transactional(readOnly = true)
    public OrderView get(@PathVariable Long orderId) {
        return view(orderId);
    }

    @PostMapping("/orders/{orderId}/confirm")
    public OrderView confirm(@PathVariable Long orderId) {
        orderService.confirm(orderId);
        return view(orderId);
    }

    @PostMapping("/orders/{orderId}/cancel")
    public OrderView cancel(@PathVariable Long orderId, @Valid @RequestBody RejectRequest req) {
        orderService.cancel(orderId, req.reason());
        return view(orderId);
    }

    @PostMapping("/orders/{orderId}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderView recordPayment(@PathVariable Long orderId, @Valid @RequestBody PaymentRequest req) {
        paymentService.record(orderId, new RecordPaymentCommand(req.mode(), req.amount(), req.instrumentRef(),
                req.instrumentDate(), req.bankName(), req.receivedBy(), keyOr(req.idempotencyKey())));
        return view(orderId);
    }

    @PostMapping("/payments/{paymentId}/verify")
    public Payment verify(@PathVariable Long paymentId) {
        return paymentService.verify(paymentId);
    }

    @PostMapping("/payments/{paymentId}/reject")
    public Payment reject(@PathVariable Long paymentId, @Valid @RequestBody RejectRequest req) {
        return paymentService.reject(paymentId, req.reason());
    }

    private OrderView view(Long orderId) {
        BillingOrder o = orderService.get(orderId);
        return new OrderView(o, lineRepository.findByBillingOrderIdOrderByLineNoAsc(orderId),
                discountRepository.findByBillingOrderId(orderId), warningRepository.findByBillingOrderId(orderId),
                paymentService.forOrder(orderId), invoiceRepository.findByBillingOrderId(orderId).orElse(null));
    }

    private static String keyOr(String key) {
        return key == null || key.isBlank() ? UUID.randomUUID().toString() : key;
    }
}
