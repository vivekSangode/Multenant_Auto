package com.nyaalkaran.erp.poc.provisioning;

import com.nyaalkaran.erp.poc.common.ApiException;
import com.nyaalkaran.erp.poc.common.AuditService;
import com.nyaalkaran.erp.poc.common.TenantResourceNaming;
import com.nyaalkaran.erp.poc.company.Company;
import com.nyaalkaran.erp.poc.company.CompanyResource.ResourceStatus;
import com.nyaalkaran.erp.poc.company.CompanyResource.ResourceType;
import com.nyaalkaran.erp.poc.company.CompanyResourceRepository;
import com.nyaalkaran.erp.poc.company.CompanyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /api/v1/platform/companies/{code}/super-admin/temporary-password: the platform's "resend invitation" for the
 * POC, where no invitation e-mail can be sent. Only the Platform Super Admin can call it (SecurityConfig).
 */
@RestController
@RequiredArgsConstructor
public class CompanyAdminAccessController {

    public record TemporaryPassword(String realm, String username, String temporaryPassword, String loginCode) {
    }

    private final CompanyService companyService;
    private final CompanyResourceRepository resourceRepository;
    private final RealmProvisioner realmProvisioner;
    private final AuditService auditService;

    @PostMapping("/api/v1/platform/companies/{companyCode}/super-admin/temporary-password")
    public TemporaryPassword issue(@PathVariable String companyCode) {
        Company company = companyService.get(companyCode);
        String realm = TenantResourceNaming.realmName(company.getCompanyCode());
        resourceRepository.findByResourceTypeAndResourceName(ResourceType.IDP_REALM, realm)
                .filter(r -> r.getStatus() == ResourceStatus.VERIFIED)
                .orElseThrow(() -> ApiException.conflict("REALM_NOT_READY", "Provision the company first"));
        String password = realmProvisioner.issueTemporaryPassword(realm, company.getSuperAdminEmail());
        auditService.record("company.super_admin.temporary_password", company.getCompanyCode(), "COMPANY",
                company.getId(), company.getSuperAdminEmail());
        return new TemporaryPassword(realm, company.getSuperAdminEmail(), password, company.getLoginCode());
    }
}
