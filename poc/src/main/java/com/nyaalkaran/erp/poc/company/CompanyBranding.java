package com.nyaalkaran.erp.poc.company;

import com.nyaalkaran.erp.poc.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** tbl_company_branding: the white-label look of one company's portal. */
@Getter
@Setter
@Entity
@Table(name = "tbl_company_branding")
public class CompanyBranding extends BaseEntity {

    public enum ThemeMode { LIGHT, DARK, SYSTEM }

    /** Font presets that need no web-font download: system stacks. */
    public enum FontStyle { MODERN, CLASSIC, ROUNDED, TECHNICAL }

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "primary_color", nullable = false, length = 7)
    private String primaryColor;

    @Column(name = "accent_color", nullable = false, length = 7)
    private String accentColor;

    @Enumerated(EnumType.STRING)
    @Column(name = "theme_mode", nullable = false, columnDefinition = "varchar(10)")
    private ThemeMode themeMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "font_style", nullable = false, columnDefinition = "varchar(20)")
    private FontStyle fontStyle;

    @Column(name = "logo_text", nullable = false, length = 3)
    private String logoText;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "tagline", length = 120)
    private String tagline;
}
