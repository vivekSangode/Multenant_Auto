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

/** tbl_company_status_history (append-only). */
@Getter
@Setter
@Entity
@Table(name = "tbl_company_status_history")
public class CompanyStatusHistory extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", columnDefinition = "varchar(30)")
    private CompanyStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, columnDefinition = "varchar(30)")
    private CompanyStatus toStatus;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "actor", nullable = false, length = 100)
    private String actor;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;
}
