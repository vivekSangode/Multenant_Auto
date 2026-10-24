package com.nyaalkaran.erp.poc.company;

import com.nyaalkaran.erp.poc.company.CompanyResource.ResourceStatus;
import com.nyaalkaran.erp.poc.company.CompanyResource.ResourceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyResourceRepository extends JpaRepository<CompanyResource, Long> {

    List<CompanyResource> findByCompanyIdOrderByIdAsc(Long companyId);

    Optional<CompanyResource> findByResourceTypeAndResourceName(ResourceType type, String resourceName);

    Optional<CompanyResource> findByCompanyIdAndResourceTypeAndModuleIdAndStatus(
            Long companyId, ResourceType type, Long moduleId, ResourceStatus status);
}
