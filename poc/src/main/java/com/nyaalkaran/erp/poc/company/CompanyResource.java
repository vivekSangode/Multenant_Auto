package com.nyaalkaran.erp.poc.company;

import com.nyaalkaran.erp.poc.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * tbl_company_resource: the registry of every company resource (master §6.4/§6.6). Runtime routing reads the
 * DB name from here; names are RESERVED before they are created (master §6.3).
 */
@Getter
@Setter
@Entity
@Table(name = "tbl_company_resource")
public class CompanyResource extends BaseEntity {

    public enum ResourceType { DATABASE, BUCKET, IDP_REALM }

    public enum ResourceStatus { RESERVED, CREATED, VERIFIED, DEGRADED, ORPHANED, RETIRED }

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, columnDefinition = "varchar(30)")
    private ResourceType resourceType;

    /** Set for module databases and module buckets; NULL for company-level resources. */
    @Column(name = "module_id")
    private Long moduleId;

    @Column(name = "resource_name", nullable = false, length = 100)
    private String resourceName;

    @Column(name = "db_host", length = 255)
    private String dbHost;

    @Column(name = "db_port")
    private Integer dbPort;

    @Column(name = "schema_version", length = 100)
    private String schemaVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "varchar(30)")
    private ResourceStatus status;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;
}
