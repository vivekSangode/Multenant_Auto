--liquibase formatted sql

-- Tenant changelog for the CRM module. Runs inside each company's own cmp_<n>_crm_db (master §16.5).
-- No tenant/company column: physical separation per company (AD-03).

--changeset crm:001-customer
CREATE TABLE crm_customer (
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    customer_code VARCHAR(40) NOT NULL,
    name         VARCHAR(150) NOT NULL,
    email        VARCHAR(320) NULL,
    phone        VARCHAR(30)  NULL,
    created_at   DATETIME(6)  NOT NULL,
    created_by   VARCHAR(100) NULL,
    CONSTRAINT uk_crm_customer_code UNIQUE (customer_code)
);
