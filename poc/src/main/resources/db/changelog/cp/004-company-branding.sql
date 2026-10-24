--liquibase formatted sql

-- White-label branding per company, applied by the tenant portal right after login-code discovery and by the
-- platform portal's preview. Optional: a company without a row gets a deterministic default palette.

--changeset poc:030-company-branding
CREATE TABLE tbl_company_branding (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_id     BIGINT       NOT NULL,
    primary_color  VARCHAR(7)   NOT NULL,
    accent_color   VARCHAR(7)   NOT NULL,
    theme_mode     VARCHAR(10)  NOT NULL DEFAULT 'LIGHT',
    font_style     VARCHAR(20)  NOT NULL DEFAULT 'MODERN',
    logo_text      VARCHAR(3)   NOT NULL,
    logo_url       VARCHAR(500) NULL,
    tagline        VARCHAR(120) NULL,
    created_at DATETIME(6) NOT NULL, created_by VARCHAR(100) NULL,
    updated_at DATETIME(6) NULL, updated_by VARCHAR(100) NULL,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_company_branding_company UNIQUE (company_id),
    CONSTRAINT fk_company_branding_company_id FOREIGN KEY (company_id) REFERENCES tbl_company (id),
    CONSTRAINT ck_company_branding_primary CHECK (primary_color REGEXP '^#[0-9A-Fa-f]{6}$'),
    CONSTRAINT ck_company_branding_accent CHECK (accent_color REGEXP '^#[0-9A-Fa-f]{6}$')
);
