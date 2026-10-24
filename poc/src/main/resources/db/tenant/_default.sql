--liquibase formatted sql

-- Default tenant changelog for modules without their own POC changelog.
-- Records the module bootstrap so the schema is "migrated + validated" (master §7.2).

--changeset platform:001-module-info
CREATE TABLE module_info (
    id          INT          NOT NULL PRIMARY KEY,
    module_code VARCHAR(60)  NOT NULL,
    bootstrapped_at DATETIME(6) NOT NULL
);
