package com.nyaalkaran.erp.poc.company;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyBrandingRepository extends JpaRepository<CompanyBranding, Long> {

    Optional<CompanyBranding> findByCompanyId(Long companyId);
}
