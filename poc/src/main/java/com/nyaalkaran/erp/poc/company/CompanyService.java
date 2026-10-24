package com.nyaalkaran.erp.poc.company;

import com.nyaalkaran.erp.poc.common.Actors;
import com.nyaalkaran.erp.poc.common.ApiException;
import com.nyaalkaran.erp.poc.common.AuditService;
import com.nyaalkaran.erp.poc.common.IdSequenceService;
import com.nyaalkaran.erp.poc.common.TenantResourceNaming;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private static final Set<String> RESERVED_SLUGS = Set.of("admin", "api", "platform", "www", "login", "erp");
    private static final String LOGIN_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CompanyRepository companyRepository;
    private final CompanyStatusHistoryRepository historyRepository;
    private final IdSequenceService idSequenceService;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;

    public record CreateCompanyCommand(String legalName, String displayName, String superAdminEmail,
                                       String onboardingType) {
    }

    /** Master §7.2 step 2: allocate CMP_n, insert the company in DRAFT, audit - one transaction. */
    @Transactional
    public Company create(CreateCompanyCommand cmd) {
        long n = idSequenceService.next("COMPANY");
        Company c = new Company();
        c.setCompanyCode(TenantResourceNaming.companyCode(n));
        c.setLegalName(cmd.legalName().trim());
        c.setDisplayName(cmd.displayName() == null || cmd.displayName().isBlank()
                ? cmd.legalName().trim() : cmd.displayName().trim());
        c.setSlug(uniqueSlug(c.getDisplayName()));
        c.setLoginCode(uniqueLoginCode());
        c.setStatus(CompanyStatus.DRAFT);
        c.setOnboardingType(cmd.onboardingType() == null ? "NEW" : cmd.onboardingType());
        c.setTimezone("Asia/Kolkata");
        c.setRetentionDays(365);
        c.setSuperAdminEmail(cmd.superAdminEmail().trim().toLowerCase(Locale.ROOT));
        companyRepository.save(c);
        writeHistory(c, null, CompanyStatus.DRAFT, "created");
        auditService.record("company.create", c.getCompanyCode(), "COMPANY", c.getId(), c.getLegalName());
        return c;
    }

    @Transactional(readOnly = true)
    public Company get(String companyCode) {
        return companyRepository.findByCompanyCode(TenantResourceNaming.requireCompanyCode(companyCode))
                .orElseThrow(() -> ApiException.notFound("Company", companyCode));
    }

    @Transactional(readOnly = true)
    public List<Company> list() {
        return companyRepository.findAllByOrderByIdAsc();
    }

    /** Validated lifecycle transition (master §7.1) with history, audit and an after-commit cache event. */
    @Transactional
    public Company transition(String companyCode, CompanyStatus target, String reason) {
        Company c = companyRepository.lockById(get(companyCode).getId()).orElseThrow();
        return transition(c, target, reason);
    }

    @Transactional
    public Company transition(Company c, CompanyStatus target, String reason) {
        CompanyStatus from = c.getStatus();
        if (!from.canMoveTo(target)) {
            throw ApiException.conflict("INVALID_COMPANY_TRANSITION", from + " -> " + target + " is not allowed");
        }
        c.setStatus(target);
        c.setStatusReason(reason);
        LocalDateTime now = LocalDateTime.now();
        switch (target) {
            case ACTIVE -> {
                if (c.getActivatedAt() == null) {
                    c.setActivatedAt(now);
                }
            }
            case SUSPENDED -> c.setSuspendedAt(now);
            case ARCHIVED -> c.setArchivedAt(now);
            default -> {
            }
        }
        writeHistory(c, from, target, reason);
        auditService.record("company.status." + target.name().toLowerCase(Locale.ROOT), c.getCompanyCode(),
                "COMPANY", c.getId(), from + " -> " + target + (reason == null ? "" : " : " + reason));
        events.publishEvent(new CompanyStatusChangedEvent(c.getId(), c.getCompanyCode(), target));
        return c;
    }

    @Transactional(readOnly = true)
    public List<CompanyStatusHistory> history(String companyCode) {
        return historyRepository.findByCompanyIdOrderByChangedAtAsc(get(companyCode).getId());
    }

    private void writeHistory(Company c, CompanyStatus from, CompanyStatus to, String reason) {
        CompanyStatusHistory h = new CompanyStatusHistory();
        h.setCompanyId(c.getId());
        h.setFromStatus(from);
        h.setToStatus(to);
        h.setReason(reason);
        h.setActor(Actors.current());
        h.setChangedAt(LocalDateTime.now());
        historyRepository.save(h);
    }

    /** Slug: lower-case ASCII from the display name; never used in a resource identifier (master §6.1). */
    private String uniqueSlug(String name) {
        String base = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (base.length() > 34) {
            base = base.substring(0, 34).replaceAll("-+$", "");
        }
        if (base.length() < 3 || RESERVED_SLUGS.contains(base)) {
            base = "company-" + base;
        }
        String slug = base;
        for (int i = 2; companyRepository.existsBySlug(slug); i++) {
            slug = base + "-" + i;
        }
        return slug;
    }

    private String uniqueLoginCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 8; i++) {
                sb.append(LOGIN_CODE_ALPHABET.charAt(RANDOM.nextInt(LOGIN_CODE_ALPHABET.length())));
            }
            if (!companyRepository.existsByLoginCode(sb.toString())) {
                return sb.toString();
            }
        }
        throw new IllegalStateException("Could not allocate a unique login code");
    }
}
