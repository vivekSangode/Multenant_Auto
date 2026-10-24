package com.nyaalkaran.erp.poc.common;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlatformAuditLogRepository extends JpaRepository<PlatformAuditLog, Long> {

    List<PlatformAuditLog> findByCompanyCodeOrderByOccurredAtAsc(String companyCode);
}
