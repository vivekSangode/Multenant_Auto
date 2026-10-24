package com.nyaalkaran.erp.poc.subscription;

import com.nyaalkaran.erp.poc.subscription.SubscriptionEnums.AllowanceType;
import com.nyaalkaran.erp.poc.subscription.SubscriptionEnums.EntitlementStatus;
import com.nyaalkaran.erp.poc.subscription.SubscriptionEnums.ItemStatus;
import com.nyaalkaran.erp.poc.subscription.SubscriptionEnums.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public final class SubscriptionRepositories {

    private SubscriptionRepositories() {
    }

    public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
        List<Subscription> findByCompanyIdOrderByIdAsc(Long companyId);

        List<Subscription> findByCompanyIdAndStatusIn(Long companyId, Collection<SubscriptionStatus> statuses);

        Optional<Subscription> findBySourceOrderId(Long orderId);
    }

    public interface SubscriptionItemRepository extends JpaRepository<SubscriptionItem, Long> {
        List<SubscriptionItem> findBySubscriptionIdOrderByIdAsc(Long subscriptionId);

        List<SubscriptionItem> findBySubscriptionIdInAndStatus(Collection<Long> subscriptionIds, ItemStatus status);
    }

    public interface SubscriptionEventRepository extends JpaRepository<SubscriptionEvent, Long> {
    }

    public interface CompanyEntitlementRepository extends JpaRepository<CompanyEntitlement, Long> {
        List<CompanyEntitlement> findByCompanyIdOrderByIdAsc(Long companyId);

        List<CompanyEntitlement> findByCompanyIdAndStatusNot(Long companyId, EntitlementStatus status);

        List<CompanyEntitlement> findByCompanyIdAndStatus(Long companyId, EntitlementStatus status);
    }

    public interface CompanyAllowanceRepository extends JpaRepository<CompanyAllowance, Long> {
        List<CompanyAllowance> findByCompanyId(Long companyId);

        Optional<CompanyAllowance> findByCompanyIdAndAllowanceType(Long companyId, AllowanceType type);
    }
}
