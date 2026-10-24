package com.nyaalkaran.erp.poc.catalog;

import com.nyaalkaran.erp.poc.catalog.CatalogEnums.CatalogStatus;
import com.nyaalkaran.erp.poc.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** tbl_bundle (master §11.3). */
@Getter
@Setter
@Entity
@Table(name = "tbl_bundle")
public class Bundle extends BaseEntity {

    @Column(name = "code", nullable = false, length = 60)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "varchar(30)")
    private CatalogStatus status;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
