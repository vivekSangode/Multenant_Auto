package com.nyaalkaran.erp.poc.catalog;

import com.nyaalkaran.erp.poc.catalog.CatalogEnums.CatalogStatus;
import com.nyaalkaran.erp.poc.catalog.CatalogEnums.ChargeMode;
import com.nyaalkaran.erp.poc.catalog.CatalogEnums.DependencyType;
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

/** tbl_module_dependency (master §11.2). */
@Getter
@Setter
@Entity
@Table(name = "tbl_module_dependency")
public class ModuleDependency extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "module_id", nullable = false)
    private ErpModule module;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "depends_on_module_id", nullable = false)
    private ErpModule dependsOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "dependency_type", nullable = false, columnDefinition = "varchar(30)")
    private DependencyType dependencyType;

    @Enumerated(EnumType.STRING)
    @Column(name = "charge_mode", nullable = false, columnDefinition = "varchar(30)")
    private ChargeMode chargeMode;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "varchar(30)")
    private CatalogStatus status;
}
