--liquibase formatted sql

-- Tenant changelog of VEHICLE-SERVICE. Runs inside each company's own cmp_<n>_vehicle_db (master §16.5).
-- No tenant/company column: physical separation per company (AD-03).

--changeset vehicle:001-model
CREATE TABLE vehicle_model (
    id          BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    model_code  VARCHAR(40)   NOT NULL,
    name        VARCHAR(150)  NOT NULL,
    body_type   VARCHAR(30)   NOT NULL,
    fuel_type   VARCHAR(30)   NOT NULL,
    base_price  DECIMAL(15,2) NOT NULL,
    created_at  DATETIME(6)   NOT NULL,
    created_by  VARCHAR(100)  NULL,
    CONSTRAINT uk_vehicle_model_code UNIQUE (model_code)
);
