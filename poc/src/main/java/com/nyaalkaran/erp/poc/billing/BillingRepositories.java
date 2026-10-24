package com.nyaalkaran.erp.poc.billing;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/** Billing repositories (kept together; Spring Data is configured to scan nested repositories). */
public final class BillingRepositories {

    private BillingRepositories() {
    }

    public interface BillingOrderRepository extends JpaRepository<BillingOrder, Long> {
        Optional<BillingOrder> findByOrderNumber(String orderNumber);

        Optional<BillingOrder> findByIdempotencyKey(String key);

        List<BillingOrder> findByCompanyIdOrderByIdAsc(Long companyId);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select o from BillingOrder o where o.id = :id")
        Optional<BillingOrder> lockById(Long id);
    }

    public interface BillingOrderLineRepository extends JpaRepository<BillingOrderLine, Long> {
        List<BillingOrderLine> findByBillingOrderIdOrderByLineNoAsc(Long orderId);
    }

    public interface BillingOrderDiscountRepository extends JpaRepository<BillingOrderDiscount, Long> {
        List<BillingOrderDiscount> findByBillingOrderId(Long orderId);
    }

    public interface BillingOrderWarningRepository extends JpaRepository<BillingOrderWarning, Long> {
        List<BillingOrderWarning> findByBillingOrderId(Long orderId);
    }

    public interface PaymentRepository extends JpaRepository<Payment, Long> {
        List<Payment> findByBillingOrderIdOrderByIdAsc(Long orderId);

        Optional<Payment> findByIdempotencyKey(String key);

        Optional<Payment> findByPaymentNumber(String paymentNumber);
    }

    public interface PaymentStatusHistoryRepository extends JpaRepository<PaymentStatusHistory, Long> {
    }

    public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
        Optional<Invoice> findByBillingOrderId(Long orderId);
    }
}
