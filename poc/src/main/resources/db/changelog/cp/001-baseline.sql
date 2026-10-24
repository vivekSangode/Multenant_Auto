--liquibase formatted sql

-- Control Plane baseline for the POC: a subset of PHASE_01 §6.2 (groups A, B, C, D, E, I).
-- Conventions (PHASE_01 §6.2): BIGINT ids, enums as VARCHAR(30), money DECIMAL(19,4), CHAR(3) currency,
-- named constraints, FKs ON DELETE RESTRICT, no soft delete. Every table carries [audit] + [ver].

--changeset poc:001-sequences
CREATE TABLE tbl_id_sequence (
    sequence_name VARCHAR(30) NOT NULL PRIMARY KEY,
    next_value    BIGINT      NOT NULL,
    version       INT         NOT NULL DEFAULT 0
);

CREATE TABLE tbl_document_series (
    id          BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    series_code VARCHAR(30) NOT NULL,
    period_key  VARCHAR(20) NOT NULL,
    prefix      VARCHAR(20) NOT NULL,
    next_value  BIGINT      NOT NULL,
    padding     INT         NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    created_by  VARCHAR(100) NULL,
    updated_at  DATETIME(6) NULL,
    updated_by  VARCHAR(100) NULL,
    version     INT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_document_series_code_period UNIQUE (series_code, period_key)
);

--changeset poc:002-catalog
CREATE TABLE tbl_module (
    id                 BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code               VARCHAR(60)   NOT NULL,
    gateway_token      VARCHAR(60)   NOT NULL,
    db_token           VARCHAR(40)   NULL,
    service_name       VARCHAR(100)  NULL,
    module_type        VARCHAR(30)   NOT NULL,
    requires_database  BIT(1)        NOT NULL,
    requires_bootstrap BIT(1)        NOT NULL,
    standalone_allowed BIT(1)        NOT NULL,
    name               VARCHAR(150)  NOT NULL,
    description        VARCHAR(1000) NULL,
    category           VARCHAR(60)   NULL,
    sort_order         INT           NOT NULL DEFAULT 0,
    status             VARCHAR(30)   NOT NULL,
    created_at DATETIME(6) NOT NULL, created_by VARCHAR(100) NULL,
    updated_at DATETIME(6) NULL, updated_by VARCHAR(100) NULL,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_module_code UNIQUE (code),
    CONSTRAINT uk_module_gateway_token UNIQUE (gateway_token),
    CONSTRAINT uk_module_db_token UNIQUE (db_token),
    CONSTRAINT ck_module_type CHECK (module_type IN ('BUSINESS', 'PLATFORM_CORE', 'ADDON'))
);

CREATE TABLE tbl_module_dependency (
    id                   BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    module_id            BIGINT       NOT NULL,
    depends_on_module_id BIGINT       NOT NULL,
    dependency_type      VARCHAR(30)  NOT NULL,
    charge_mode          VARCHAR(30)  NOT NULL,
    reason               VARCHAR(500) NOT NULL,
    status               VARCHAR(30)  NOT NULL,
    created_at DATETIME(6) NOT NULL, created_by VARCHAR(100) NULL,
    updated_at DATETIME(6) NULL, updated_by VARCHAR(100) NULL,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_module_dependency UNIQUE (module_id, depends_on_module_id),
    CONSTRAINT ck_module_dependency_self CHECK (module_id <> depends_on_module_id),
    CONSTRAINT fk_module_dependency_module_id FOREIGN KEY (module_id) REFERENCES tbl_module (id),
    CONSTRAINT fk_module_dependency_depends_on FOREIGN KEY (depends_on_module_id) REFERENCES tbl_module (id)
);

CREATE TABLE tbl_bundle (
    id          BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code        VARCHAR(60)   NOT NULL,
    name        VARCHAR(150)  NOT NULL,
    description VARCHAR(1000) NULL,
    status      VARCHAR(30)   NOT NULL,
    sort_order  INT           NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL, created_by VARCHAR(100) NULL,
    updated_at DATETIME(6) NULL, updated_by VARCHAR(100) NULL,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_bundle_code UNIQUE (code)
);

CREATE TABLE tbl_bundle_module (
    id        BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    bundle_id BIGINT NOT NULL,
    module_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL, created_by VARCHAR(100) NULL,
    updated_at DATETIME(6) NULL, updated_by VARCHAR(100) NULL,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_bundle_module UNIQUE (bundle_id, module_id),
    CONSTRAINT fk_bundle_module_bundle_id FOREIGN KEY (bundle_id) REFERENCES tbl_bundle (id),
    CONSTRAINT fk_bundle_module_module_id FOREIGN KEY (module_id) REFERENCES tbl_module (id)
);

CREATE TABLE tbl_plan (
    id          BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code        VARCHAR(60)   NOT NULL,
    name        VARCHAR(150)  NOT NULL,
    description VARCHAR(1000) NULL,
    plan_type   VARCHAR(30)   NOT NULL,
    module_id   BIGINT        NULL,
    bundle_id   BIGINT        NULL,
    is_public   BIT(1)        NOT NULL DEFAULT 0,
    status      VARCHAR(30)   NOT NULL,
    sort_order  INT           NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL, created_by VARCHAR(100) NULL,
    updated_at DATETIME(6) NULL, updated_by VARCHAR(100) NULL,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_plan_code UNIQUE (code),
    CONSTRAINT fk_plan_module_id FOREIGN KEY (module_id) REFERENCES tbl_module (id),
    CONSTRAINT fk_plan_bundle_id FOREIGN KEY (bundle_id) REFERENCES tbl_bundle (id),
    CONSTRAINT ck_plan_target CHECK (
        (plan_type = 'MODULE' AND module_id IS NOT NULL AND bundle_id IS NULL) OR
        (plan_type = 'BUNDLE' AND bundle_id IS NOT NULL AND module_id IS NULL) OR
        (plan_type IN ('USER_SEAT', 'STORAGE') AND module_id IS NULL AND bundle_id IS NULL))
);

CREATE TABLE tbl_plan_version (
    id                BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    plan_id           BIGINT       NOT NULL,
    version_number    INT          NOT NULL,
    status            VARCHAR(30)  NOT NULL,
    is_custom         BIT(1)       NOT NULL DEFAULT 0,
    custom_company_id BIGINT       NULL,
    effective_from    DATETIME(6)  NULL,
    effective_to      DATETIME(6)  NULL,
    published_at      DATETIME(6)  NULL,
    published_by      VARCHAR(100) NULL,
    created_at DATETIME(6) NOT NULL, created_by VARCHAR(100) NULL,
    updated_at DATETIME(6) NULL, updated_by VARCHAR(100) NULL,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_plan_version UNIQUE (plan_id, version_number),
    CONSTRAINT fk_plan_version_plan_id FOREIGN KEY (plan_id) REFERENCES tbl_plan (id)
);

CREATE TABLE tbl_plan_price (
    id                  BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    plan_version_id     BIGINT        NOT NULL,
    billing_period      VARCHAR(20)   NOT NULL,
    currency            CHAR(3)       NOT NULL,
    unit_price          DECIMAL(19,4) NOT NULL,
    unit_quantity       INT           NOT NULL DEFAULT 1,
    included_users      INT           NOT NULL DEFAULT 0,
    included_storage_gb INT           NOT NULL DEFAULT 0,
    min_quantity        INT           NOT NULL DEFAULT 1,
    max_quantity        INT           NULL,
    created_at DATETIME(6) NOT NULL, created_by VARCHAR(100) NULL,
    updated_at DATETIME(6) NULL, updated_by VARCHAR(100) NULL,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_plan_price UNIQUE (plan_version_id, billing_period, currency),
    CONSTRAINT ck_plan_price_non_negative CHECK (unit_price >= 0),
    CONSTRAINT fk_plan_price_plan_version_id FOREIGN KEY (plan_version_id) REFERENCES tbl_plan_version (id)
);

--changeset poc:003-company
CREATE TABLE tbl_company (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_code    VARCHAR(20)  NOT NULL,
    legal_name      VARCHAR(150) NOT NULL,
    display_name    VARCHAR(150) NOT NULL,
    slug            VARCHAR(40)  NOT NULL,
    login_code      VARCHAR(12)  NOT NULL,
    status          VARCHAR(30)  NOT NULL,
    status_reason   VARCHAR(500) NULL,
    onboarding_type VARCHAR(30)  NOT NULL DEFAULT 'NEW',
    timezone        VARCHAR(50)  NOT NULL DEFAULT 'Asia/Kolkata',
    retention_days  INT          NOT NULL DEFAULT 365,
    super_admin_email VARCHAR(320) NOT NULL,
    activated_at    DATETIME(6)  NULL,
    suspended_at    DATETIME(6)  NULL,
    archived_at     DATETIME(6)  NULL,
    created_at DATETIME(6) NOT NULL, created_by VARCHAR(100) NULL,
    updated_at DATETIME(6) NULL, updated_by VARCHAR(100) NULL,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_company_code UNIQUE (company_code),
    CONSTRAINT uk_company_slug UNIQUE (slug),
    CONSTRAINT uk_company_login_code UNIQUE (login_code),
    CONSTRAINT ck_company_code_format CHECK (company_code REGEXP '^CMP_[0-9]{1,8}$')
);
CREATE INDEX idx_company_status ON tbl_company (status);

CREATE TABLE tbl_company_status_history (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_id  BIGINT       NOT NULL,
    from_status VARCHAR(30)  NULL,
    to_status   VARCHAR(30)  NOT NULL,
    reason      VARCHAR(500) NULL,
    actor       VARCHAR(100) NOT NULL,
    changed_at  DATETIME(6)  NOT NULL,
    created_at DATETIME(6) NOT NULL, created_by VARCHAR(100) NULL,
    updated_at DATETIME(6) NULL, updated_by VARCHAR(100) NULL,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_company_status_history_company_id FOREIGN KEY (company_id) REFERENCES tbl_company (id)
);
CREATE INDEX idx_company_status_history ON tbl_company_status_history (company_id, changed_at);

CREATE TABLE tbl_company_resource (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_id     BIGINT       NOT NULL,
    resource_type  VARCHAR(30)  NOT NULL,
    module_id      BIGINT       NULL,
    module_key     BIGINT AS (COALESCE(module_id, 0)) STORED,
    resource_name  VARCHAR(100) NOT NULL,
    db_host        VARCHAR(255) NULL,
    db_port        INT          NULL,
    schema_version VARCHAR(100) NULL,
    status         VARCHAR(30)  NOT NULL,
    verified_at    DATETIME(6)  NULL,
    created_at DATETIME(6) NOT NULL, created_by VARCHAR(100) NULL,
    updated_at DATETIME(6) NULL, updated_by VARCHAR(100) NULL,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_company_resource_name UNIQUE (resource_type, resource_name),
    CONSTRAINT uk_company_resource_module UNIQUE (company_id, resource_type, module_key, resource_name),
    CONSTRAINT ck_company_resource_db CHECK (resource_type <> 'DATABASE' OR (db_host IS NOT NULL AND db_port IS NOT NULL)),
    CONSTRAINT fk_company_resource_company_id FOREIGN KEY (company_id) REFERENCES tbl_company (id),
    CONSTRAINT fk_company_resource_module_id FOREIGN KEY (module_id) REFERENCES tbl_module (id)
);
CREATE INDEX idx_company_resource_type ON tbl_company_resource (company_id, resource_type);

--changeset poc:004-orders-billing
CREATE TABLE tbl_billing_order (
    id                  BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_number        VARCHAR(40)   NOT NULL,
    company_id          BIGINT        NOT NULL,
    subscription_id     BIGINT        NULL,
    order_kind          VARCHAR(20)   NOT NULL,
    channel             VARCHAR(20)   NOT NULL,
    billing_period      VARCHAR(20)   NOT NULL,
    currency            CHAR(3)       NOT NULL,
    requested_users     INT           NOT NULL,
    requested_storage_gb INT          NOT NULL,
    subtotal            DECIMAL(19,4) NOT NULL,
    discount_total      DECIMAL(19,4) NOT NULL,
    taxable_total       DECIMAL(19,4) NOT NULL,
    tax_total           DECIMAL(19,4) NOT NULL,
    other_charges_total DECIMAL(19,4) NOT NULL DEFAULT 0,
    round_off           DECIMAL(19,4) NOT NULL DEFAULT 0,
    grand_total         DECIMAL(19,4) NOT NULL,
    payment_status      VARCHAR(20)   NOT NULL,
    status              VARCHAR(20)   NOT NULL,
    calculation_version VARCHAR(20)   NOT NULL,
    input_json          JSON          NOT NULL,
    valid_until         DATETIME(6)   NULL,
    idempotency_key     VARCHAR(80)   NOT NULL,
    created_by_actor    VARCHAR(100)  NOT NULL,
    created_at DATETIME(6) NOT NULL, created_by VARCHAR(100) NULL,
    updated_at DATETIME(6) NULL, updated_by VARCHAR(100) NULL,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_billing_order_number UNIQUE (order_number),
    CONSTRAINT uk_billing_order_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_billing_order_company_id FOREIGN KEY (company_id) REFERENCES tbl_company (id)
);
CREATE INDEX idx_billing_order_company_status ON tbl_billing_order (company_id, status);

CREATE TABLE tbl_billing_order_line (
    id                   BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    billing_order_id     BIGINT        NOT NULL,
    line_no              INT           NOT NULL,
    line_type            VARCHAR(30)   NOT NULL,
    plan_version_id      BIGINT        NOT NULL,
    plan_price_id        BIGINT        NOT NULL,
    module_id            BIGINT        NULL,
    bundle_id            BIGINT        NULL,
    description          VARCHAR(300)  NOT NULL,
    quantity             DECIMAL(19,4) NOT NULL,
    unit_price           DECIMAL(19,4) NOT NULL,
    gross_amount         DECIMAL(19,4) NOT NULL,
    line_discount_amount DECIMAL(19,4) NOT NULL DEFAULT 0,
    taxable_amount       DECIMAL(19,4) NOT NULL,
    tax_amount           DECIMAL(19,4) NOT NULL DEFAULT 0,
    line_total           DECIMAL(19,4) NOT NULL,
    created_at DATETIME(6) NOT NULL, created_by VARCHAR(100) NULL,
    updated_at DATETIME(6) NULL, updated_by VARCHAR(100) NULL,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_billing_order_line UNIQUE (billing_order_id, line_no),
    CONSTRAINT fk_billing_order_line_order_id FOREIGN KEY (billing_order_id) REFERENCES tbl_billing_order (id),
    CONSTRAINT fk_billing_order_line_plan_version_id FOREIGN KEY (plan_version_id) REFERENCES tbl_plan_version (id),
    CONSTRAINT fk_billing_order_line_plan_price_id FOREIGN KEY (plan_price_id) REFERENCES tbl_plan_price (id),
    CONSTRAINT fk_billing_order_line_module_id FOREIGN KEY (module_id) REFERENCES tbl_module (id),
    CONSTRAINT fk_billing_order_line_bundle_id FOREIGN KEY (bundle_id) REFERENCES tbl_bundle (id)
);

CREATE TABLE tbl_billing_order_discount (
    id               BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    billing_order_id BIGINT        NOT NULL,
    discount_source  VARCHAR(20)   NOT NULL,
    discount_type    VARCHAR(20)   NOT NULL,
    discount_value   DECIMAL(19,4) NOT NULL,
    base_amount      DECIMAL(19,4) NOT NULL,
    discount_amount  DECIMAL(19,4) NOT NULL,
    reason           VARCHAR(500)  NULL,
    sequence_no      INT           NOT NULL,
    created_at DATETIME(6) NOT NULL, created_by VARCHAR(100) NULL,
    updated_at DATETIME(6) NULL, updated_by VARCHAR(100) NULL,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT ck_billing_order_discount_manual CHECK (discount_source <> 'MANUAL' OR reason IS NOT NULL),
    CONSTRAINT fk_billing_order_discount_order_id FOREIGN KEY (billing_order_id) REFERENCES tbl_billing_order (id)
);

CREATE TABLE tbl_billing_order_warning (
    id               BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    billing_order_id BIGINT       NOT NULL,
    code             VARCHAR(60)  NOT NULL,
    detail           VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL, created_by VARCHAR(100) NULL,
    updated_at DATETIME(6) NULL, updated_by VARCHAR(100) NULL,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_billing_order_warning_order_id FOREIGN KEY (billing_order_id) REFERENCES tbl_billing_order (id)
);

-- tbl_payment + tbl_manual_payment (PHASE_01 §I.1) are merged into one table in the POC.
CREATE TABLE tbl_payment (
    id                    BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    payment_number        VARCHAR(40)   NOT NULL,
    receipt_number        VARCHAR(40)   NOT NULL,
    company_id            BIGINT        NOT NULL,
    billing_order_id      BIGINT        NOT NULL,
    invoice_id            BIGINT        NULL,
    payment_channel       VARCHAR(20)   NOT NULL,
    payment_mode          VARCHAR(30)   NOT NULL,
    amount                DECIMAL(19,4) NOT NULL,
    currency              CHAR(3)       NOT NULL,
    status                VARCHAR(30)   NOT NULL,
    instrument_ref        VARCHAR(100)  NULL,
    instrument_date       DATE          NULL,
    bank_name             VARCHAR(150)  NULL,
    verification_required BIT(1)        NOT NULL,
    received_at           DATETIME(6)   NULL,
    confirmed_at          DATETIME(6)   NULL,
    received_by           VARCHAR(100)  NOT NULL,
    rejection_reason      VARCHAR(500)  NULL,
    idempotency_key       VARCHAR(80)   NOT NULL,
    created_at DATETIME(6) NOT NULL, created_by VARCHAR(100) NULL,
    updated_at DATETIME(6) NULL, updated_by VARCHAR(100) NULL,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_payment_number UNIQUE (payment_number),
    CONSTRAINT uk_payment_receipt_number UNIQUE (receipt_number),
    CONSTRAINT uk_payment_idempotency UNIQUE (idempotency_key),
    CONSTRAINT ck_payment_amount_positive CHECK (amount > 0),
    CONSTRAINT fk_payment_company_id FOREIGN KEY (company_id) REFERENCES tbl_company (id),
    CONSTRAINT fk_payment_order_id FOREIGN KEY (billing_order_id) REFERENCES tbl_billing_order (id)
);
CREATE INDEX idx_payment_company_status ON tbl_payment (company_id, status);

CREATE TABLE tbl_payment_status_history (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    payment_id  BIGINT       NOT NULL,
    from_status VARCHAR(30)  NULL,
    to_status   VARCHAR(30)  NOT NULL,
    actor_name  VARCHAR(100) NOT NULL,
    reason      VARCHAR(500) NULL,
    occurred_at DATETIME(6)  NOT NULL,
    created_at DATETIME(6) NOT NULL, created_by VARCHAR(100) NULL,
    updated_at DATETIME(6) NULL, updated_by VARCHAR(100) NULL,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_payment_status_history_payment_id FOREIGN KEY (payment_id) REFERENCES tbl_payment (id)
);

CREATE TABLE tbl_invoice (
    id                  BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    invoice_number      VARCHAR(40)   NOT NULL,
    company_id          BIGINT        NOT NULL,
    billing_order_id    BIGINT        NOT NULL,
    invoice_type        VARCHAR(20)   NOT NULL,
    issue_date          DATE          NOT NULL,
    currency            CHAR(3)       NOT NULL,
    subtotal            DECIMAL(19,4) NOT NULL,
    discount_total      DECIMAL(19,4) NOT NULL,
    taxable_total       DECIMAL(19,4) NOT NULL,
    tax_total           DECIMAL(19,4) NOT NULL,
    other_charges_total DECIMAL(19,4) NOT NULL,
    round_off           DECIMAL(19,4) NOT NULL,
    grand_total         DECIMAL(19,4) NOT NULL,
    amount_paid         DECIMAL(19,4) NOT NULL,
    amount_credited     DECIMAL(19,4) NOT NULL DEFAULT 0,
    balance_due         DECIMAL(19,4) NOT NULL,
    status              VARCHAR(20)   NOT NULL,
    issued_at           DATETIME(6)   NOT NULL,
    created_at DATETIME(6) NOT NULL, created_by VARCHAR(100) NULL,
    updated_at DATETIME(6) NULL, updated_by VARCHAR(100) NULL,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_invoice_number UNIQUE (invoice_number),
    CONSTRAINT uk_invoice_order UNIQUE (billing_order_id),
    CONSTRAINT fk_invoice_company_id FOREIGN KEY (company_id) REFERENCES tbl_company (id),
    CONSTRAINT fk_invoice_order_id FOREIGN KEY (billing_order_id) REFERENCES tbl_billing_order (id)
);

--changeset poc:005-subscriptions-entitlements
CREATE TABLE tbl_subscription (
    id                  BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    subscription_number VARCHAR(40)  NOT NULL,
    company_id          BIGINT       NOT NULL,
    billing_period      VARCHAR(20)  NOT NULL,
    currency            CHAR(3)      NOT NULL,
    status              VARCHAR(30)  NOT NULL,
    subscription_type   VARCHAR(20)  NOT NULL,
    start_date          DATE         NULL,
    end_date            DATE         NULL,
    auto_renew          BIT(1)       NOT NULL DEFAULT 0,
    cancel_at_period_end BIT(1)      NOT NULL DEFAULT 0,
    source_order_id     BIGINT       NOT NULL,
    end_reason          VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL, created_by VARCHAR(100) NULL,
    updated_at DATETIME(6) NULL, updated_by VARCHAR(100) NULL,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_subscription_number UNIQUE (subscription_number),
    CONSTRAINT fk_subscription_company_id FOREIGN KEY (company_id) REFERENCES tbl_company (id),
    CONSTRAINT fk_subscription_source_order_id FOREIGN KEY (source_order_id) REFERENCES tbl_billing_order (id)
);
CREATE INDEX idx_subscription_company_status ON tbl_subscription (company_id, status);

CREATE TABLE tbl_subscription_item (
    id                   BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    subscription_id      BIGINT        NOT NULL,
    item_type            VARCHAR(30)   NOT NULL,
    plan_version_id      BIGINT        NOT NULL,
    plan_price_id        BIGINT        NOT NULL,
    module_id            BIGINT        NULL,
    bundle_id            BIGINT        NULL,
    quantity             DECIMAL(19,4) NOT NULL,
    unit_price_snapshot  DECIMAL(19,4) NOT NULL,
    source_order_line_id BIGINT        NULL,
    status               VARCHAR(30)   NOT NULL,
    effective_from       DATE          NOT NULL,
    effective_to         DATE          NULL,
    created_at DATETIME(6) NOT NULL, created_by VARCHAR(100) NULL,
    updated_at DATETIME(6) NULL, updated_by VARCHAR(100) NULL,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_subscription_item_subscription_id FOREIGN KEY (subscription_id) REFERENCES tbl_subscription (id),
    CONSTRAINT fk_subscription_item_plan_version_id FOREIGN KEY (plan_version_id) REFERENCES tbl_plan_version (id),
    CONSTRAINT fk_subscription_item_module_id FOREIGN KEY (module_id) REFERENCES tbl_module (id),
    CONSTRAINT fk_subscription_item_bundle_id FOREIGN KEY (bundle_id) REFERENCES tbl_bundle (id)
);
CREATE INDEX idx_subscription_item_status ON tbl_subscription_item (subscription_id, status);

CREATE TABLE tbl_subscription_event (
    id               BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    subscription_id  BIGINT       NOT NULL,
    event_type       VARCHAR(30)  NOT NULL,
    from_status      VARCHAR(30)  NULL,
    to_status        VARCHAR(30)  NOT NULL,
    billing_order_id BIGINT       NULL,
    actor            VARCHAR(100) NOT NULL,
    reason           VARCHAR(500) NULL,
    occurred_at      DATETIME(6)  NOT NULL,
    created_at DATETIME(6) NOT NULL, created_by VARCHAR(100) NULL,
    updated_at DATETIME(6) NULL, updated_by VARCHAR(100) NULL,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_subscription_event_subscription_id FOREIGN KEY (subscription_id) REFERENCES tbl_subscription (id)
);

CREATE TABLE tbl_company_entitlement (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_id     BIGINT       NOT NULL,
    module_id      BIGINT       NOT NULL,
    source_type    VARCHAR(30)  NOT NULL,
    source_item_id BIGINT       NULL,
    source_key     VARCHAR(100) NOT NULL,
    is_charged     BIT(1)       NOT NULL,
    charged_guard  TINYINT      NULL,
    status         VARCHAR(30)  NOT NULL,
    valid_from     DATE         NOT NULL,
    valid_to       DATE         NULL,
    created_at DATETIME(6) NOT NULL, created_by VARCHAR(100) NULL,
    updated_at DATETIME(6) NULL, updated_by VARCHAR(100) NULL,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_company_entitlement_source UNIQUE (company_id, module_id, source_type, source_key),
    -- No module is charged twice across a company's subscriptions (master §13.1, rule 2)
    CONSTRAINT uk_company_entitlement_charged UNIQUE (company_id, module_id, charged_guard),
    CONSTRAINT fk_company_entitlement_company_id FOREIGN KEY (company_id) REFERENCES tbl_company (id),
    CONSTRAINT fk_company_entitlement_module_id FOREIGN KEY (module_id) REFERENCES tbl_module (id)
);

CREATE TABLE tbl_company_allowance (
    id                 BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_id         BIGINT      NOT NULL,
    allowance_type     VARCHAR(30) NOT NULL,
    included_quantity  BIGINT      NOT NULL,
    purchased_quantity BIGINT      NOT NULL,
    effective_limit    BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL, created_by VARCHAR(100) NULL,
    updated_at DATETIME(6) NULL, updated_by VARCHAR(100) NULL,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_company_allowance UNIQUE (company_id, allowance_type),
    CONSTRAINT fk_company_allowance_company_id FOREIGN KEY (company_id) REFERENCES tbl_company (id)
);

--changeset poc:006-provisioning
CREATE TABLE tbl_provisioning_job (
    id              BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_id      BIGINT        NOT NULL,
    job_type        VARCHAR(30)   NOT NULL,
    mode            VARCHAR(30)   NOT NULL,
    status          VARCHAR(30)   NOT NULL,
    attempt         INT           NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6)   NULL,
    locked_by       VARCHAR(100)  NULL,
    locked_until    DATETIME(6)   NULL,
    active_guard    TINYINT       NULL,
    requested_by    VARCHAR(100)  NOT NULL,
    last_error      VARCHAR(1000) NULL,
    completed_at    DATETIME(6)   NULL,
    created_at DATETIME(6) NOT NULL, created_by VARCHAR(100) NULL,
    updated_at DATETIME(6) NULL, updated_by VARCHAR(100) NULL,
    version INT NOT NULL DEFAULT 0,
    -- One active job per company (master §7.3 Concurrency)
    CONSTRAINT uk_provisioning_job_active UNIQUE (company_id, active_guard),
    CONSTRAINT fk_provisioning_job_company_id FOREIGN KEY (company_id) REFERENCES tbl_company (id)
);
CREATE INDEX idx_provisioning_job_status ON tbl_provisioning_job (status, next_attempt_at);

CREATE TABLE tbl_provisioning_step (
    id            BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    job_id        BIGINT        NOT NULL,
    sequence_no   INT           NOT NULL,
    step_code     VARCHAR(60)   NOT NULL,
    resource_type VARCHAR(30)   NULL,
    module_id     BIGINT        NULL,
    resource_name VARCHAR(100)  NULL,
    status        VARCHAR(30)   NOT NULL,
    attempts      INT           NOT NULL DEFAULT 0,
    last_error    VARCHAR(1000) NULL,
    started_at    DATETIME(6)   NULL,
    completed_at  DATETIME(6)   NULL,
    created_at DATETIME(6) NOT NULL, created_by VARCHAR(100) NULL,
    updated_at DATETIME(6) NULL, updated_by VARCHAR(100) NULL,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_provisioning_step UNIQUE (job_id, sequence_no),
    CONSTRAINT fk_provisioning_step_job_id FOREIGN KEY (job_id) REFERENCES tbl_provisioning_job (id),
    CONSTRAINT fk_provisioning_step_module_id FOREIGN KEY (module_id) REFERENCES tbl_module (id)
);

--changeset poc:007-audit
CREATE TABLE tbl_platform_audit_log (
    id             BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    actor          VARCHAR(100)  NOT NULL,
    action         VARCHAR(100)  NOT NULL,
    company_code   VARCHAR(20)   NULL,
    target_type    VARCHAR(60)   NULL,
    target_id      VARCHAR(100)  NULL,
    outcome        VARCHAR(20)   NOT NULL,
    detail         VARCHAR(2000) NULL,
    occurred_at    DATETIME(6)   NOT NULL,
    created_at DATETIME(6) NOT NULL, created_by VARCHAR(100) NULL,
    updated_at DATETIME(6) NULL, updated_by VARCHAR(100) NULL,
    version INT NOT NULL DEFAULT 0
);
CREATE INDEX idx_platform_audit_company ON tbl_platform_audit_log (company_code, occurred_at);
