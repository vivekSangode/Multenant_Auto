package com.nyaalkaran.erp.poc.company;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CompanyStatusHistoryRepository extends JpaRepository<CompanyStatusHistory, Long> {

    List<CompanyStatusHistory> findByCompanyIdOrderByChangedAtAsc(Long companyId);
}
