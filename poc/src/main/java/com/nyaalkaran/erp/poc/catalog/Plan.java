package com.nyaalkaran.erp.poc.catalog;

import com.nyaalkaran.erp.poc.catalog.CatalogEnums.CatalogStatus;
import com.nyaalkaran.erp.poc.catalog.CatalogEnums.PlanType;
import com.nyaalkaran.erp.poc.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** tbl_plan: a sellable offer - MODULE, BUNDLE, USER_SEAT or STORAGE (master §12.1). */
@Getter
@Setter
@Entity
@Table(name = "tbl_plan")
public class Plan extends BaseEntity {

    @Column(name = "code", nullable = false, length = 60)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type", nullable = false, columnDefinition = "varchar(30)")
    private PlanType planType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "module_id")
    private ErpModule module;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bundle_id")
    private Bundle bundle;

    @Column(name = "is_public", nullable = false)
    private boolean publicPlan;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "varchar(30)")
    private CatalogStatus status;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
