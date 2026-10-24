package com.nyaalkaran.erp.poc.company;

import com.nyaalkaran.erp.poc.common.ApiException;
import com.nyaalkaran.erp.poc.common.TenantResourceNaming;
import com.nyaalkaran.erp.poc.company.CompanyService.CreateCompanyCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/platform/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;

    public record CreateCompanyRequest(
            @NotBlank @Size(max = 150) String legalName,
            @Size(max = 150) String displayName,
            @NotBlank @Email @Size(max = 320) String superAdminEmail,
            @Pattern(regexp = "NEW|IMPORT_EXISTING_DATA") String onboardingType) {
    }

    public record StatusChangeRequest(@NotNull CompanyStatus status, @Size(max = 500) String reason) {
    }

    public record CompanyView(Long id, String companyCode, String legalName, String displayName, String slug,
                              String loginCode, CompanyStatus status, String statusReason, String onboardingType,
                              String superAdminEmail, String realm, LocalDateTime activatedAt) {
        static CompanyView of(Company c) {
            return new CompanyView(c.getId(), c.getCompanyCode(), c.getLegalName(), c.getDisplayName(), c.getSlug(),
                    c.getLoginCode(), c.getStatus(), c.getStatusReason(), c.getOnboardingType(),
                    c.getSuperAdminEmail(), TenantResourceNaming.realmName(c.getCompanyCode()), c.getActivatedAt());
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CompanyView create(@Valid @RequestBody CreateCompanyRequest req) {
        return CompanyView.of(companyService.create(new CreateCompanyCommand(
                req.legalName(), req.displayName(), req.superAdminEmail(), req.onboardingType())));
    }

    @GetMapping
    public List<CompanyView> list() {
        return companyService.list().stream().map(CompanyView::of).toList();
    }

    @GetMapping("/{companyCode}")
    public CompanyView get(@PathVariable String companyCode) {
        return CompanyView.of(companyService.get(companyCode));
    }

    /** Platform lifecycle actions: suspend, reactivate, disable, archive (master §7.1). */
    @PostMapping("/{companyCode}/status")
    public CompanyView changeStatus(@PathVariable String companyCode, @Valid @RequestBody StatusChangeRequest req) {
        if (req.status() == CompanyStatus.PROVISIONING || req.status() == CompanyStatus.PURGE_APPROVED) {
            throw ApiException.badRequest("USE_DEDICATED_FLOW",
                    "Provisioning and purge have their own flows");
        }
        return CompanyView.of(companyService.transition(companyCode, req.status(), req.reason()));
    }

    @GetMapping("/{companyCode}/status-history")
    public List<CompanyStatusHistory> history(@PathVariable String companyCode) {
        return companyService.history(companyCode);
    }
}
