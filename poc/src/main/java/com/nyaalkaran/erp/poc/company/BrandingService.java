package com.nyaalkaran.erp.poc.company;

import com.nyaalkaran.erp.poc.common.ApiException;
import com.nyaalkaran.erp.poc.common.AuditService;
import com.nyaalkaran.erp.poc.company.CompanyBranding.FontStyle;
import com.nyaalkaran.erp.poc.company.CompanyBranding.ThemeMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Company branding. Companies that were never customised still look distinct: the default palette is picked
 * deterministically from the company code, and the logo text from the company name.
 */
@Service
@RequiredArgsConstructor
public class BrandingService {

    /** primary, accent: accessible on white with white text on the primary colour. */
    private static final List<String[]> PALETTES = List.of(
            new String[]{"#4F46E5", "#F59E0B"}, new String[]{"#0F766E", "#F97316"},
            new String[]{"#B91C1C", "#0EA5E9"}, new String[]{"#1D4ED8", "#10B981"},
            new String[]{"#7C3AED", "#EC4899"}, new String[]{"#C2410C", "#2563EB"},
            new String[]{"#15803D", "#EAB308"}, new String[]{"#334155", "#06B6D4"});
    private static final Pattern HEX = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final Pattern HTTPS_URL = Pattern.compile("^https?://[^\\s\"'<>]{1,490}$");

    public record BrandingView(String primaryColor, String accentColor, ThemeMode themeMode, FontStyle fontStyle,
                               String logoText, String logoUrl, String tagline, boolean customized) {
    }

    public record UpdateBranding(String primaryColor, String accentColor, ThemeMode themeMode, FontStyle fontStyle,
                                 String logoText, String logoUrl, String tagline) {
    }

    private final CompanyBrandingRepository repository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public BrandingView forCompany(Company company) {
        return repository.findByCompanyId(company.getId())
                .map(b -> new BrandingView(b.getPrimaryColor(), b.getAccentColor(), b.getThemeMode(), b.getFontStyle(),
                        b.getLogoText(), b.getLogoUrl(), b.getTagline(), true))
                .orElseGet(() -> defaults(company));
    }

    @Transactional
    public BrandingView update(Company company, UpdateBranding req) {
        requireHex("primaryColor", req.primaryColor());
        requireHex("accentColor", req.accentColor());
        String logoText = req.logoText() == null ? "" : req.logoText().trim().toUpperCase(Locale.ROOT);
        if (logoText.isEmpty() || logoText.length() > 3) {
            throw ApiException.badRequest("INVALID_BRANDING", "logoText must be 1-3 characters");
        }
        String logoUrl = blankToNull(req.logoUrl());
        if (logoUrl != null && !HTTPS_URL.matcher(logoUrl).matches()) {
            throw ApiException.badRequest("INVALID_BRANDING", "logoUrl must be an http(s) URL");
        }
        CompanyBranding b = repository.findByCompanyId(company.getId()).orElseGet(() -> {
            CompanyBranding n = new CompanyBranding();
            n.setCompanyId(company.getId());
            return n;
        });
        b.setPrimaryColor(req.primaryColor().toUpperCase(Locale.ROOT));
        b.setAccentColor(req.accentColor().toUpperCase(Locale.ROOT));
        b.setThemeMode(req.themeMode() == null ? ThemeMode.LIGHT : req.themeMode());
        b.setFontStyle(req.fontStyle() == null ? FontStyle.MODERN : req.fontStyle());
        b.setLogoText(logoText);
        b.setLogoUrl(logoUrl);
        String tagline = blankToNull(req.tagline());
        b.setTagline(tagline == null ? null : tagline.substring(0, Math.min(120, tagline.length())));
        repository.save(b);
        auditService.record("company.branding.update", company.getCompanyCode(), "COMPANY", company.getId(),
                b.getPrimaryColor() + "/" + b.getAccentColor() + "/" + b.getThemeMode());
        return forCompany(company);
    }

    static BrandingView defaults(Company company) {
        String[] palette = PALETTES.get(Math.floorMod(company.getCompanyCode().hashCode(), PALETTES.size()));
        return new BrandingView(palette[0], palette[1], ThemeMode.LIGHT, FontStyle.MODERN,
                initials(company.getDisplayName()), null, null, false);
    }

    static String initials(String name) {
        String letters = Arrays.stream(name.split("[^\\p{L}\\p{N}]+"))
                .filter(w -> !w.isBlank())
                .filter(w -> !List.of("PVT", "LTD", "LLP", "INC", "CO", "PRIVATE", "LIMITED").contains(w.toUpperCase(Locale.ROOT)))
                .limit(2)
                .map(w -> w.substring(0, 1))
                .collect(Collectors.joining());
        return letters.isEmpty() ? "CO" : letters.toUpperCase(Locale.ROOT);
    }

    private static void requireHex(String field, String value) {
        if (value == null || !HEX.matcher(value).matches()) {
            throw ApiException.badRequest("INVALID_BRANDING", field + " must be a #RRGGBB colour");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
