package com.nyaalkaran.erp.poc.tenant;

import com.nyaalkaran.erp.poc.common.ApiException;
import com.nyaalkaran.erp.poc.common.TenantResourceNaming;
import com.nyaalkaran.erp.poc.company.CompanyResource;
import com.nyaalkaran.erp.poc.company.CompanyResource.ResourceType;
import com.nyaalkaran.erp.poc.company.CompanyResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registry lookups for the domain services (PHASE_01 §6.6.1/§6.6.2): which company an issuer belongs to, and
 * which VERIFIED database serves (company, module). Only M2M callers on the allow-list (SecurityConfig).
 * The services cache the answers; no credentials are ever returned (CD-03: they come from the environment).
 */
@RestController
@RequestMapping("/api/v1/internal")
@RequiredArgsConstructor
public class InternalRegistryController {

    private final CompanyRealmIssuers companyRealms;
    private final TenantRoutingService routing;
    private final CompanyResourceRepository resourceRepository;

    public record IssuerView(String issuer, String companyCode) {
    }

    public record DatabaseView(String companyCode, String moduleCode, String databaseName, String host, Integer port) {
    }

    @GetMapping("/realms/company")
    public IssuerView companyOfIssuer(@RequestParam String issuer) {
        String companyCode = companyRealms.companyFor(issuer);
        if (companyCode == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "UNKNOWN_ISSUER", "Not a registered company realm");
        }
        return new IssuerView(issuer, companyCode);
    }

    @GetMapping("/companies/{companyCode}/modules/{moduleCode}/database")
    public DatabaseView database(@PathVariable String companyCode, @PathVariable String moduleCode) {
        TenantResourceNaming.requireCompanyCode(companyCode);
        String name = routing.resolveDatabase(companyCode, moduleCode);
        CompanyResource db = resourceRepository.findByResourceTypeAndResourceName(ResourceType.DATABASE, name)
                .orElseThrow();
        return new DatabaseView(companyCode, moduleCode, name, db.getDbHost(), db.getDbPort());
    }
}
