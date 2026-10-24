--liquibase formatted sql

-- Tenant changelog of INVENTORY-SERVICE. Runs inside each company's own cmp_<n>_inventory_db (master §16.5).

--changeset inventory:001-stock-unit
CREATE TABLE stock_unit (
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    vin          VARCHAR(17)  NOT NULL,
    model_code   VARCHAR(40)  NOT NULL,
    color        VARCHAR(40)  NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    reserved_for VARCHAR(40)  NULL,
    created_at   DATETIME(6)  NOT NULL,
    created_by   VARCHAR(100) NULL,
    updated_at   DATETIME(6)  NULL,
    CONSTRAINT uk_stock_unit_vin UNIQUE (vin)
);
CREATE INDEX ix_stock_unit_model_status ON stock_unit (model_code, status);
