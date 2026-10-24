package com.nyaalkaran.erp.poc.catalog;

import com.nyaalkaran.erp.poc.catalog.CatalogEnums.CatalogStatus;
import com.nyaalkaran.erp.poc.catalog.CatalogEnums.ModuleType;
import com.nyaalkaran.erp.poc.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** tbl_module: the database-driven module catalog (master §11.1). */
@Getter
@Setter
@Entity
@Table(name = "tbl_module")
public class ErpModule extends BaseEntity {

    @Column(name = "code", nullable = false, length = 60)
    private String code;

    @Column(name = "gateway_token", nullable = false, length = 60)
    private String gatewayToken;

    /** Frozen after ACTIVE: company DB names depend on it (cmp_n_[db_token]_db). */
    @Column(name = "db_token", length = 40, updatable = false)
    private String dbToken;

    @Column(name = "service_name", length = 100)
    private String serviceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "module_type", nullable = false, columnDefinition = "varchar(30)")
    private ModuleType moduleType;

    @Column(name = "requires_database", nullable = false)
    private boolean requiresDatabase;

    @Column(name = "requires_bootstrap", nullable = false)
    private boolean requiresBootstrap;

    @Column(name = "standalone_allowed", nullable = false)
    private boolean standaloneAllowed;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "category", length = 60)
    private String category;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "varchar(30)")
    private CatalogStatus status;

    public boolean isPlatformCore() {
        return moduleType == ModuleType.PLATFORM_CORE;
    }
}
