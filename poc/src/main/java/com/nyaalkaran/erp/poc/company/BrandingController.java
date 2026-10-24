package com.nyaalkaran.erp.poc.company;

import com.nyaalkaran.erp.poc.company.BrandingService.BrandingView;
import com.nyaalkaran.erp.poc.company.BrandingService.UpdateBranding;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Platform Super Admin sets a company's portal look (white-label). */
@RestController
@RequestMapping("/api/v1/platform/companies/{companyCode}/branding")
@RequiredArgsConstructor
public class BrandingController {

    private final CompanyService companyService;
    private final BrandingService brandingService;

    @GetMapping
    public BrandingView get(@PathVariable String companyCode) {
        return brandingService.forCompany(companyService.get(companyCode));
    }

    @PutMapping
    public BrandingView update(@PathVariable String companyCode, @RequestBody UpdateBranding req) {
        return brandingService.update(companyService.get(companyCode), req);
    }
}
