package com.nyaalkaran.erp.poc.catalog;

import com.nyaalkaran.erp.poc.catalog.CatalogEnums.BillingPeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PlanPriceRepository extends JpaRepository<PlanPrice, Long> {

    /** The current public price list: ACTIVE, non-contract plan versions of ACTIVE plans. */
    @Query("""
            select pp from PlanPrice pp
              join fetch pp.planVersion pv
              join fetch pv.plan p
              left join fetch p.module
              left join fetch p.bundle
            where pv.status = com.nyaalkaran.erp.poc.catalog.CatalogEnums.CatalogStatus.ACTIVE
              and p.status = com.nyaalkaran.erp.poc.catalog.CatalogEnums.CatalogStatus.ACTIVE
              and pv.custom = false
              and pp.billingPeriod = :period
              and pp.currency = :currency
            order by p.sortOrder
            """)
    List<PlanPrice> findCurrentPrices(BillingPeriod period, String currency);
}
