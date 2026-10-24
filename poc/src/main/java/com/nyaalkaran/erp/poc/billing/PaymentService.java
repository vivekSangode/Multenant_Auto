package com.nyaalkaran.erp.poc.billing;

import com.nyaalkaran.erp.poc.billing.BillingEnums.InvoiceStatus;
import com.nyaalkaran.erp.poc.billing.BillingEnums.OrderPaymentStatus;
import com.nyaalkaran.erp.poc.billing.BillingEnums.OrderStatus;
import com.nyaalkaran.erp.poc.billing.BillingEnums.PaymentMode;
import com.nyaalkaran.erp.poc.billing.BillingEnums.PaymentStatus;
import com.nyaalkaran.erp.poc.billing.BillingRepositories.BillingOrderRepository;
import com.nyaalkaran.erp.poc.billing.BillingRepositories.InvoiceRepository;
import com.nyaalkaran.erp.poc.billing.BillingRepositories.PaymentRepository;
import com.nyaalkaran.erp.poc.billing.BillingRepositories.PaymentStatusHistoryRepository;
import com.nyaalkaran.erp.poc.common.Actors;
import com.nyaalkaran.erp.poc.common.ApiException;
import com.nyaalkaran.erp.poc.common.AuditService;
import com.nyaalkaran.erp.poc.common.DocumentSeriesService;
import com.nyaalkaran.erp.poc.common.DocumentSeriesService.Series;
import com.nyaalkaran.erp.poc.subscription.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Full manual payment before activation (CD-07). Several verified instruments may total 100 % (CD-20);
 * a payment may not exceed the outstanding amount; nothing activates before 100 % is verified.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final BillingOrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentStatusHistoryRepository historyRepository;
    private final InvoiceRepository invoiceRepository;
    private final SubscriptionService subscriptionService;
    private final DocumentSeriesService documentSeries;
    private final AuditService auditService;

    public record RecordPaymentCommand(PaymentMode mode, BigDecimal amount, String instrumentRef,
                                       LocalDate instrumentDate, String bankName, String receivedBy,
                                       String idempotencyKey) {
    }

    @Transactional
    public Payment record(Long orderId, RecordPaymentCommand cmd) {
        var previous = paymentRepository.findByIdempotencyKey(cmd.idempotencyKey());
        if (previous.isPresent()) {
            return previous.get();
        }
        // The order row lock serializes concurrent payments of one order.
        BillingOrder order = orderRepository.lockById(orderId).orElseThrow(() -> ApiException.notFound("Order", orderId));
        if (order.getStatus() != OrderStatus.AWAITING_PAYMENT) {
            throw ApiException.conflict("INVALID_ORDER_STATE", "Order is " + order.getStatus());
        }
        BigDecimal amount = cmd.amount().setScale(2, java.math.RoundingMode.HALF_UP);
        if (amount.signum() <= 0) {
            throw ApiException.badRequest("INVALID_AMOUNT", "Amount must be positive");
        }
        BigDecimal outstanding = order.getGrandTotal().subtract(sum(order.getId(),
                PaymentStatus.CONFIRMED, PaymentStatus.PENDING_VERIFICATION));
        if (amount.compareTo(outstanding) > 0) {
            throw ApiException.conflict("PAYMENT_EXCEEDS_OUTSTANDING", "Outstanding amount is " + outstanding);
        }

        Payment p = new Payment();
        p.setPaymentNumber(documentSeries.next(Series.PAYMENT));
        p.setReceiptNumber(documentSeries.next(Series.RECEIPT));
        p.setCompanyId(order.getCompanyId());
        p.setBillingOrderId(order.getId());
        p.setPaymentChannel("MANUAL");
        p.setPaymentMode(cmd.mode());
        p.setAmount(amount);
        p.setCurrency(order.getCurrency());
        p.setInstrumentRef(cmd.instrumentRef());
        p.setInstrumentDate(cmd.instrumentDate());
        p.setBankName(cmd.bankName());
        p.setVerificationRequired(cmd.mode().verificationRequired);
        p.setReceivedAt(LocalDateTime.now());
        p.setReceivedBy(cmd.receivedBy() == null ? Actors.current() : cmd.receivedBy());
        p.setIdempotencyKey(cmd.idempotencyKey());
        p.setStatus(PaymentStatus.RECORDED);
        paymentRepository.save(p);
        history(p, null, PaymentStatus.RECORDED, null);
        if (p.isVerificationRequired()) {
            move(p, PaymentStatus.PENDING_VERIFICATION, "awaiting clearance");
        } else {
            move(p, PaymentStatus.CONFIRMED, "immediate method");
            p.setConfirmedAt(LocalDateTime.now());
        }
        auditService.record("payment.record", null, "PAYMENT", p.getPaymentNumber(),
                cmd.mode() + " " + amount + " for " + order.getOrderNumber());
        refreshOrder(order);
        return p;
    }

    /** Cheque cleared / credit seen. */
    @Transactional
    public Payment verify(Long paymentId) {
        Payment p = paymentRepository.findById(paymentId).orElseThrow(() -> ApiException.notFound("Payment", paymentId));
        BillingOrder order = orderRepository.lockById(p.getBillingOrderId()).orElseThrow();
        requireStatus(p, PaymentStatus.PENDING_VERIFICATION);
        move(p, PaymentStatus.CONFIRMED, "verified");
        p.setConfirmedAt(LocalDateTime.now());
        auditService.record("payment.verify", null, "PAYMENT", p.getPaymentNumber(), null);
        refreshOrder(order);
        return p;
    }

    /** Bounced / not received: the order goes back to waiting for payment, with history. */
    @Transactional
    public Payment reject(Long paymentId, String reason) {
        Payment p = paymentRepository.findById(paymentId).orElseThrow(() -> ApiException.notFound("Payment", paymentId));
        BillingOrder order = orderRepository.lockById(p.getBillingOrderId()).orElseThrow();
        requireStatus(p, PaymentStatus.PENDING_VERIFICATION);
        move(p, PaymentStatus.REJECTED, reason);
        p.setRejectionReason(reason);
        auditService.record("payment.reject", null, "PAYMENT", p.getPaymentNumber(), reason);
        refreshOrder(order);
        return p;
    }

    @Transactional(readOnly = true)
    public List<Payment> forOrder(Long orderId) {
        return paymentRepository.findByBillingOrderIdOrderByIdAsc(orderId);
    }

    private void refreshOrder(BillingOrder order) {
        BigDecimal confirmed = sum(order.getId(), PaymentStatus.CONFIRMED);
        BigDecimal pending = sum(order.getId(), PaymentStatus.PENDING_VERIFICATION);
        if (confirmed.compareTo(order.getGrandTotal()) == 0) {
            fulfill(order, confirmed);
        } else {
            order.setPaymentStatus(pending.signum() > 0 ? OrderPaymentStatus.PENDING_VERIFICATION
                    : OrderPaymentStatus.UNPAID);
        }
    }

    /** PAID: invoice issued (paid), subscription ACTIVE, entitlements computed; provisioning may start. */
    private void fulfill(BillingOrder order, BigDecimal paid) {
        order.setPaymentStatus(OrderPaymentStatus.PAID);
        order.setStatus(OrderStatus.PAID);

        Invoice inv = new Invoice();
        inv.setInvoiceNumber(documentSeries.next(Series.INVOICE));
        inv.setCompanyId(order.getCompanyId());
        inv.setBillingOrderId(order.getId());
        inv.setInvoiceType("TAX_INVOICE");
        inv.setIssueDate(LocalDate.now());
        inv.setCurrency(order.getCurrency());
        inv.setSubtotal(order.getSubtotal());
        inv.setDiscountTotal(order.getDiscountTotal());
        inv.setTaxableTotal(order.getTaxableTotal());
        inv.setTaxTotal(order.getTaxTotal());
        inv.setOtherChargesTotal(order.getOtherChargesTotal());
        inv.setRoundOff(order.getRoundOff());
        inv.setGrandTotal(order.getGrandTotal());
        inv.setAmountPaid(paid);
        inv.setAmountCredited(BigDecimal.ZERO);
        inv.setBalanceDue(order.getGrandTotal().subtract(paid));
        inv.setStatus(InvoiceStatus.ISSUED_PAID);
        inv.setIssuedAt(LocalDateTime.now());
        invoiceRepository.save(inv);
        paymentRepository.findByBillingOrderIdOrderByIdAsc(order.getId()).stream()
                .filter(p -> p.getStatus() == PaymentStatus.CONFIRMED)
                .forEach(p -> p.setInvoiceId(inv.getId()));

        subscriptionService.activate(order);
        order.setStatus(OrderStatus.FULFILLED);
        auditService.record("order.fulfilled", null, "BILLING_ORDER", order.getOrderNumber(),
                "invoice " + inv.getInvoiceNumber());
    }

    private BigDecimal sum(Long orderId, PaymentStatus... statuses) {
        List<PaymentStatus> wanted = List.of(statuses);
        return paymentRepository.findByBillingOrderIdOrderByIdAsc(orderId).stream()
                .filter(p -> wanted.contains(p.getStatus()))
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private void requireStatus(Payment p, PaymentStatus expected) {
        if (p.getStatus() != expected) {
            throw ApiException.conflict("INVALID_PAYMENT_STATE", "Payment is " + p.getStatus());
        }
    }

    private void move(Payment p, PaymentStatus to, String reason) {
        PaymentStatus from = p.getStatus();
        p.setStatus(to);
        history(p, from, to, reason);
    }

    private void history(Payment p, PaymentStatus from, PaymentStatus to, String reason) {
        PaymentStatusHistory h = new PaymentStatusHistory();
        h.setPaymentId(p.getId());
        h.setFromStatus(from);
        h.setToStatus(to);
        h.setActorName(Actors.current());
        h.setReason(reason);
        h.setOccurredAt(LocalDateTime.now());
        historyRepository.save(h);
    }
}
