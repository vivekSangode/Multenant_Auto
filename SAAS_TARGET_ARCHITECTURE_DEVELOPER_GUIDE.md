# SaaS Target Architecture — Developer Guide (Company Lifecycle)

| Item | Value |
|---|---|
| Purpose | One readable explanation of the **final target architecture** and the complete Company onboarding and provisioning lifecycle, for developers, **before coding starts** |
| Status | **APPROVED** by the owner (S0-1, gate G0, 2026-10-03). Implementation still waits for the Stage 0 items in the tracker: S0-2, S0-10, S0-12 (PROPOSAL only) open; S0-4, S0-5 pending; PHASE_02 LOCKED until S0-4 and S0-5 are DONE. |
| Source of truth | [PHASE_01_SAAS_FOUNDATION.md](PHASE_01_SAAS_FOUNDATION.md) (decision register — CD-01..CD-27 explained in this guide; the current register is CD-01..CD-65 in PHASE_01 §0.1, which wins; column-level schema §6.2) and [SAAS_ARCHITECTURE_MASTER.md](SAAS_ARCHITECTURE_MASTER.md). This guide **explains** them. It does not change them. Where it proposes something new, it is marked **PROPOSED (gap)** and listed in §23. |
| Date | 2026-10-03 |
| Protected hosts | `192.168.5.52` and `192.168.5.53` are never accessed by anything described here. |

Terminology:
- The business concept is **Company**. Customer and Organization are not used as business concepts.
- `CMP_<n>` (`company_code`) is the **single** company identifier, used for business and runtime alike (CD-26): tenant context value, `tenant_id` claim, Redis keys, DB and bucket names.
- `ORG_<n>`, `ORG_MODULES` and the `org_` / `org-` prefixes are retired and not used in the target. **Owner directive 2026-10-04 (P02-T11) supersedes the earlier "legacy names may stay" rule:** the runtime uses company names — `TenantContext.companyCode` / `TenantContextHolder.getCompanyCode()`, header `X-Company-Code` (services accept only a `CMP_<n>` value), metadata JSON field `companyCode`, internal CP API `/api/v1/internal/platform/companies/…`, Redis key `COMPANY_MODULES:<CMP_n>` (done in P02-T11 stage B, 2026-10-05). The control-plane organization / customer tier and the gateway organization selection were removed in P02-T11 stages C and D (2026-10-05); stage E architecture tests keep the retired names out of every module (business "organization" — e.g. the ACCOUNT-BOOK organization profile — is unaffected).

---

## 1. Final SaaS architecture

```text
Platform (operated by Platform Super Admin / Platform Admins, realm erp-platform)
  |
  +-- Control Plane (CONTROL-PANEL-SERVICE + control_panel_db)
  |     company registry, catalog, plans, orders, payments, invoices,
  |     subscriptions, entitlements, permission policy, resource registry,
  |     realm registry, provisioning jobs, audit, platform email
  |
  +-- Company A  (company_code CMP_101, login code ABCMFG01)
  |     +-- Keycloak realm co-101 (users, realm roles, clients)
  |     +-- Purchased modules (entitlements): CRM, HRMS, ACCOUNT-BOOK
  |     +-- Databases:  cmp_101_crm_db, cmp_101_hrms_db, cmp_101_account_book_db,
  |     |               cmp_101_attachment_db, cmp_101_notification_db (platform core)
  |     +-- Buckets:    cmp-101-crm-bucket, cmp-101-hrms-bucket, cmp-101-account-book-bucket,
  |                     cmp-101-control-panel-bucket (company-level assets)
  |
  +-- Company B  (CMP_102 / realm co-102) ... its own realm, DBs, buckets
  +-- Company C  ...
```

Nothing is shared between companies except the platform services themselves and the **environment-level** DB / MinIO infrastructure credentials (CD-03, accepted risk R-08).

The confirmed decisions this guide relies on (PHASE_01 §0):

| ID | Decision |
|---|---|
| CD-01 | A database and a bucket per company per purchased module; plus `cmp-<n>-control-panel-bucket` |
| CD-02 | One login = one company; the company comes from the token issuer (realm) + the `tenant_id` claim |
| CD-03 | Environment-level DB credentials; no per-company MySQL users; the CP registry maps company → module → DB / bucket |
| CD-04 | The Platform Super Admin is created manually by the operator, in the platform realm only |
| CD-05 | New CP DB, new company DBs, buckets and realms; legacy production data is untouched |
| CD-06 | One platform realm + one realm per company |
| CD-07, CD-20 | Activation only after 100 % verified payment (several verified instruments allowed) |
| CD-11..CD-14 | Role assignment in the company realm (realm roles); CP Permission Policy; ERP-only administration |
| CD-15 | Company Login Code |
| CD-16 | Shivam uses the standard flow; legacy identity migration and data import are separate |
| CD-17 | Platform realm `erp-platform`; dedicated minimum-privilege realm-provisioning service account |
| CD-18 | Capacity planning target: 50 companies |
| CD-19, CD-23 | Platform email from one static platform mailbox (also Keycloak emails); business email only from the user's own OAuth mailbox |
| CD-21, CD-22 | No mandatory two-person payment rule; 0-day grace |
| CD-24, CD-25 | Delegated product decisions; configurable tax and invoicing (values pending the CA) |

---

## 2. Complete Company creation flow

### 2.1 Overview

Three state machines run side by side. They are linked, but not merged (see §13):
- the **order** (commercial);
- the **company** (lifecycle);
- the **provisioning job** (technical).

```text
Platform Super Admin (platform-console, realm erp-platform)
  |
  | S1  Create Company ...................................... company = DRAFT
  | S2  Allocate CMP_<n> (id sequence)
  | S3  Assign Company Login Code
  | S4  Billing account (GST identity, place of supply)
  | S5  Select modules / bundle / users / storage -> order DRAFT -> QUOTED (frozen; proforma)
  | S6  Confirm order ...................................... order = AWAITING_PAYMENT, subscription = PENDING
  | S7  Record payment(s); verify (cheque / DD / transfer clearance)
  | S8  100 % verified ...................................... order = PAID -> FULFILLED
  |                                                          subscription = ACTIVE, entitlements computed
  |                                                          tax invoice per billing policy (CD-25)
  | S9  Click "Start Provisioning" (Q-40) .................. job QUEUED, company = PROVISIONING
  v
Provisioning Worker (CONTROL-PANEL), job CREATE_COMPANY, one step at a time
  | P1  RESERVE_NAMES            (realm, DB and bucket names reserved in the registry)
  | P2  CREATE_REALM             -> co-<n>
  | P3  APPLY_REALM_TEMPLATE
  | P4  CREATE_STANDARD_ROLES    (company-admin roles + roles of purchased modules)
  | P5  CONFIGURE_CLIENTS        (erp-web, erp-identity-admin, [erp-portal])
  | P6  REGISTER_REALM           (tbl_identity_realm ACTIVE)
  | P7  CREATE_DATABASE:<m>      (each purchased module + attachment + notification)
  | P8  MIGRATE_SCHEMA:<m>       (Liquibase via the module service's bootstrap)
  | P9  CREATE_BUCKET:<m>        (each purchased module + control-panel)
  | P10 REGISTER_RESOURCES       (all registry rows VERIFIED)
  | P11 INVITE_COMPANY_SUPER_ADMIN (user in co-<n> + COMPANY_SUPER_ADMIN role + invitation email)
  | P12 PUBLISH_CACHES           (COMPANY_MODULES, TENANT_STATUS, TENANT_ROLE_PERMS)
  | P13 ACTIVATE                 company = ACTIVE (or AWAITING_DATA_IMPORT for import onboarding)
  v
Company Ready. The Company Super Admin logs in with the Login Code and creates the other users (§12).
```

Clarifications against the request's sketch (also listed in §23 G):
- The **subscription** becomes ACTIVE at S8, when 100 % is verified. The **company** becomes ACTIVE at P13. Users can log in only when the company and its realm are ACTIVE.
- Provisioning creates **only the Company Super Admin**. Other users are created afterwards by the company itself (§12).

### 2.2 Step-by-step detail (S = Control Plane step, P = provisioning step)

| Step | Trigger | Performed by | DB | Keycloak API | Resource created | Identifier | Stored in CP | Can fail because | Retry / rollback | Audit |
|---|---|---|---|---|---|---|---|---|---|---|
| **S1** Create company | Platform Super Admin (`platform.company.create`) | CP `CompanyService` | control_panel_db | — | `tbl_company` row, status DRAFT | `id` (BIGINT) | `tbl_company`, `tbl_company_profile`, `tbl_company_status_history` | Validation (duplicate legal name or slug) | Transactional; nothing outside the DB | `COMPANY_CREATED` |
| **S2** Allocate codes | Same transaction | `CompanyCodeAllocator` | control_panel_db (`tbl_id_sequence` row `COMPANY`, `SELECT … FOR UPDATE`) | — | — | `company_code` CMP_<n> (n from 101) | `tbl_company` | Lock timeout | Transaction retry; gaps allowed, never reused | Part of `COMPANY_CREATED` |
| **S3** Login code | Same transaction, platform-chosen or generated | `CompanyService` | control_panel_db | — | — | `login_code` e.g. `ABCMFG01` | `tbl_company.login_code` (UQ) | Collision | Regenerate (generated) or 409 (chosen) | Part of `COMPANY_CREATED` |
| **S4** Billing account | Super Admin (`platform.billing.manage`) | CP billing | control_panel_db | — | `tbl_billing_account` | — | GSTIN, PAN, `state_code` (place of supply), `gst_treatment` | GSTIN/PAN format | Edit until the first invoice; later changes do not touch issued invoices (snapshots) | `BILLING_ACCOUNT_UPDATED` |
| **S5** Order | Super Admin (`platform.billing.manage`) | `PricingEngine` + `OrderService` | control_panel_db | — | `tbl_billing_order` + lines, discounts, taxes, warnings | `order_number` (series ORDER) | Order frozen at QUOTED | Dependency / duplicate / tax-config gate (CD-25) | Re-quote from DRAFT; QUOTED is immutable | `ORDER_CREATED`, `ORDER_QUOTED` |
| **S6** Confirm | Super Admin | `OrderService` + `SubscriptionService` | control_panel_db | — | Subscription PENDING + items + period 1; tax invoice ISSUED_UNPAID only if the policy is ON_ORDER_CONFIRMATION | `subscription_number`; `invoice_number` (policy-dependent) | `tbl_subscription*`, maybe `tbl_invoice*` | Quote expired | Re-quote | `ORDER_CONFIRMED` |
| **S7** Payments | Platform user (`platform.payment.record`, `platform.payment.confirm`) | `ManualPaymentService` | control_panel_db | — | `tbl_payment` + `tbl_manual_payment` (one per instrument) | `payment_number`, `receipt_number` | Status history, evidence key | Amount > outstanding; cheque bounce | Bounce → REJECTED; never deleted | `PAYMENT_RECORDED`, `PAYMENT_VERIFIED`, `PAYMENT_REJECTED` + receipt platform email |
| **S8** Fully paid | Automatic when Σ CONFIRMED = `grand_total` | `ActivationService` (one locked transaction) | control_panel_db | — | Invoice ISSUED_PAID, subscription ACTIVE, `tbl_company_entitlement` rows, order FULFILLED | `invoice_number` | All billing tables | Concurrency | Row lock; exactly-once (unique guards) | `ORDER_PAID`, `INVOICE_ISSUED`, `SUBSCRIPTION_ACTIVATED` + invoice platform email |
| **S9** Start provisioning | Super Admin (`platform.company.provision`) | `ProvisioningJobService.enqueue` | control_panel_db | — | `tbl_provisioning_job` QUEUED + planned steps | job `id` | Job, steps; company → PROVISIONING | Preconditions (order PAID, no active job) | One active job per company (`active_guard`) | `PROVISION_REQUESTED` |
| **P1** RESERVE_NAMES | Worker | `ProvisioningWorker` | control_panel_db | — | Registry rows RESERVED for the realm, every DB and every bucket | names | `tbl_company_resource` (UQ type + name) | Name already reserved by another company | Fails loudly (never reuses); reconciliation report | `PROVISION_STEP` |
| **P2** CREATE_REALM | Worker | Realm Provisioning Service with `erp-realm-provisioner` (master, `create-realm`) | control_panel_db | `POST /admin/realms` (minimal realm `co-<n>`, enabled=false until P6) | Realm `co-<n>` | `realm_name`, `issuer_uri` | `tbl_identity_realm` PROVISIONING, resource IDP_REALM | Keycloak down; name exists | Idempotent: existing realm + registry row → verify, do not recreate; an existing realm **without** a registry row → FAILED + manual review | `REALM_CREATED` |
| **P3** APPLY_REALM_TEMPLATE | Worker | Same | — | `PUT /admin/realms/{realm}`, client scopes, protocol mappers (audience, hard-coded `tenant_id`), SMTP (vault reference), brute-force, password policy, OTP flow for admin roles | Configured realm | `template_version` | `tbl_identity_realm.template_version` | Partial apply | Idempotent PUT-style; re-run | `REALM_TEMPLATE_APPLIED` |
| **P4** CREATE_STANDARD_ROLES | Worker | Same | — | `POST /admin/realms/{realm}/roles` | Realm roles `COMPANY_SUPER_ADMIN`, `COMPANY_ADMIN`, `<MODULE>_<LEVEL>` | role names | — (definitions are already in `tbl_role`) | Role exists | Skip existing roles | `REALM_ROLES_CREATED` |
| **P5** CONFIGURE_CLIENTS | Worker | Same | — | `POST /admin/realms/{realm}/clients`; service-account role mappings for `erp-identity-admin` (`realm-management`: `manage-users`, `view-users`, `query-users`, `view-realm`) | Clients `erp-web`, `erp-identity-admin`, optional `erp-portal` | client ids | — (fixed by convention) | Client exists | Update to the template | `REALM_CLIENTS_CONFIGURED` |
| **P6** REGISTER_REALM | Worker | `RealmRegistryService` | control_panel_db | `PUT /admin/realms/{realm}` enabled=true | Realm usable | issuer, JWKS URI | `tbl_identity_realm` ACTIVE; resource IDP_REALM VERIFIED; `tenant-invalidation` published | DB error | Retry; the realm stays disabled until success | `REALM_REGISTERED` |
| **P7** CREATE_DATABASE:<m> | Worker | `DomainDatabaseProvisioningService` (existing) with the **environment provisioning account** | MySQL | — | `cmp_<n>_<db_token>_db` | DB name | Resource row → CREATED | Privileges, disk | `CREATE DATABASE IF NOT EXISTS`; retry with backoff | `DATABASE_CREATED` |
| **P8** MIGRATE_SCHEMA:<m> | Worker | Bootstrap call to the module service (`POST /api/v1/internal/tenant-bootstrap/{CMP_n}`, mode SEEDED or EMPTY_FOR_IMPORT) | Company module DB | — | Tables (Liquibase) | `schema_version` | Resource `schema_version` | Service down; changeset error | Liquibase is idempotent; retry; never drop | `SCHEMA_MIGRATED` |
| **P9** CREATE_BUCKET:<m> | Worker | `BucketProvisioner` (environment MinIO keys) | MinIO | — | `cmp-<n>-<module>-bucket` (private) + quota if supported | bucket name | Resource row → CREATED | MinIO down | `bucketExists` → skip; retry | `BUCKET_CREATED` |
| **P10** REGISTER_RESOURCES | Worker | Registry | control_panel_db | — | — | — | All resource rows VERIFIED (host, port, verified_at) | Verification mismatch | Retry; mismatch → FAILED | `RESOURCES_REGISTERED` |
| **P11** INVITE_COMPANY_SUPER_ADMIN | Worker → identity-sync outbox | `IdentitySyncProcessor` with `erp-identity-admin` of `co-<n>` | control_panel_db | `POST /admin/realms/{realm}/users`; role mapping `COMPANY_SUPER_ADMIN`; `PUT …/execute-actions-email` (VERIFY_EMAIL, UPDATE_PASSWORD, CONFIGURE_TOTP) | User in the company realm | Keycloak `sub` | `tbl_identity` (realm + sub), `tbl_company_user` (`is_company_super_admin`) | Email delivery failure | Asynchronous outbox retry; **does not block** activation, but is visible in the status | `COMPANY_SUPER_ADMIN_INVITED` |
| **P12** PUBLISH_CACHES | Worker | Entitlement and policy publishers | Redis | — | `COMPANY_MODULES:CMP_n`, `TENANT_STATUS:CMP_n`, `TENANT_ROLE_PERMS:CMP_n:<role>` | — | — | Redis down | Retry; the resync scheduler repairs | `CACHES_PUBLISHED` |
| **P13** ACTIVATE | Worker | `CompanyLifecycleService` | control_panel_db | — | Company ACTIVE (or AWAITING_DATA_IMPORT) | — | Company status + history; job COMPLETED | — | — | `COMPANY_ACTIVATED` + "provisioning completed" platform email |

---

## 3. Company identity and naming design

| # | Identifier | Rule | Example (ABC Manufacturing, the first company) | Unique | Can change? | Collision prevention |
|---|---|---|---|---|---|---|
| 1 | `tbl_company.id` | BIGINT identity, internal only | `1` | PK | Never | DB identity |
| 1a, 7 | `company_code` (business **and** runtime company id, CD-26) | `CMP_<n>`, n from `tbl_id_sequence` COMPANY, starting at 101 | `CMP_101` | UQ | Never (`updatable=false`) | Locked sequence; gaps never reused |
| 2 | `login_code` | `^[A-Z0-9]{6,12}$`; platform-chosen or generated; not derived from the company code, realm or DB | `ABCMFG01` | UQ | Only by an audited platform operation (`platform.company.update`) | UQ; generator retries |
| 3 | Realm | `co-<n>`, `^co-[0-9]{1,8}$` | `co-101` | UQ (`tbl_identity_realm.realm_name`) | Never | Reserved in P1 |
| 4 | Clients (company realm) | Fixed: `erp-web`, `erp-identity-admin`, `erp-portal` (optional) | `erp-web` in `co-101` | Unique per realm | Never | Template |
| 4a | Clients (platform realm) | `platform-console`, `svc-<service>`, `erp-platform-identity-admin`; master: `erp-realm-provisioner` | `svc-crm-service` | Unique per realm | Never | Runbook |
| 5 | Module DB | `cmp_<n>_<db_token>_db`, `^[a-z0-9_]{1,64}$` | `cmp_101_crm_db`, `cmp_101_hrms_db`, `cmp_101_account_book_db` | UQ (registry) | Never | `db_token` immutable after ACTIVE; reserved in P1 |
| 5a | Platform-core DBs | `cmp_<n>_attachment_db`, `cmp_<n>_notification_db` | `cmp_101_attachment_db` | UQ | Never | Same |
| 6 | Module bucket | `cmp-<n>-<module dns token>-bucket` (the existing `TenantResourceNaming.bucketName` pattern with the `cmp-` prefix, CD-26), 3–63 chars | `cmp-101-crm-bucket`, `cmp-101-hrms-bucket`, `cmp-101-account-book-bucket` | UQ | Never | Reserved in P1 |
| 6a | Control-panel bucket | `cmp-<n>-control-panel-bucket` | `cmp-101-control-panel-bucket` | UQ | Never | Same |
| 8 | Subscription | `subscription_number`; format **APPROVED (CD-33, 2026-10-08)**: series `SUBSCRIPTION`, `SUB/{FY}/{SEQ:5}`, FY reset, width 5 | `SUB/2026-27/00001` | UQ | Never | Atomic series |
| 9 | Order | `order_number`, series ORDER, `format_pattern` configurable (CD-25) | `ORD/2026-27/00001` | UQ | Never | Atomic series |
| 10 | Invoice | `invoice_number`, series INVOICE per FY, format per the CA (Q-29) | `INV/2026-27/00001` | UQ | Never | Atomic series; no gaps allowed for issued invoices |
| 10a | Payment / receipt | `payment_number` (PAYMENT), `receipt_number` (RECEIPT) | `PAY/2026-27/00001`, `RCT/2026-27/00001` | UQ | Never | Atomic series |
| 11 | Provisioning job | BIGINT `id`; display `PJ-<id>` | `PJ-1` | PK | Never | Identity; one active job per company |

- `<n>` in DB and bucket names is the number of the **company code** (`CMP_101` → `101`), **not** `tbl_company.id`.
- The raw company name is **never** a technical identifier. It is display data only (`legal_name`, `display_name`, `slug`).
- Every technical name is derived only from `company_code` + the catalog tokens, never from user input (`TenantResourceNaming`, exact validators).

---

## 4. Database architecture

```text
                       MySQL server(s) - one environment login (CD-03)
+------------------------------+   +-----------------------------------------------+
| control_panel_db  (platform) |   | Company A (CMP_101)                            |
|  companies, catalog, plans,  |   |   cmp_101_crm_db           <- CRM-SERVICE      |
|  orders, payments, invoices, |   |   cmp_101_hrms_db          <- HRMS-SERVICE     |
|  subscriptions, entitlements,|   |   cmp_101_account_book_db  <- ACCOUNT-BOOK     |
|  policy, registries, jobs,   |   |   cmp_101_attachment_db    <- platform core    |
|  audit                       |   |   cmp_101_notification_db  <- platform core    |
+------------------------------+   +-----------------------------------------------+
                                   | Company B (CMP_102)                            |
                                   |   cmp_102_crm_db, cmp_102_attachment_db, ...   |
                                   +-----------------------------------------------+
```

- **A. Control Plane DB (`control_panel_db`)** holds platform data only. **No business data.**
- **B. Company module DBs:** one per company per **purchased** module, plus the two platform-core DBs. There is no shared business DB between companies.
- The legacy production `control_panel_db` on the protected host is not touched (Q-20, CD-05).

```text
Company
  +--> Subscription item --> Entitlement (module)
                                +--> DATABASE resource (tbl_company_resource, module_id)
                                +--> BUCKET resource   (tbl_company_resource, module_id)
```

---

## 5. Control Plane database — complete table inventory

The column-level definitions are in PHASE_01 §6.2 (groups A–K) and §6.5.2.

Common notations:
- `[audit]` = `created_at`, `created_by`, `updated_at`, `updated_by`;
- `[ver]` = optimistic `version`.

Owner: CONTROL-PANEL-SERVICE for every table (the only writer).

"Runtime-critical" means the table is read (usually through a cache) on the request path. Sensitive data is personal data, financial data, or security configuration.

### 5.1 Company

| Table | Purpose | PK | Important columns | FK | Unique | Status | Version / effective | API | Runtime-critical | Sensitive |
|---|---|---|---|---|---|---|---|---|---|---|
| `tbl_company` | Company = customer + tenant | `id` | `company_code` (`CMP_<n>`), `login_code`, `legal_name`, `display_name`, `slug`, `onboarding_type`, `timezone`, `retention_days` | — | company code, login code, slug | DRAFT, PROVISIONING, PROVISIONING_FAILED, AWAITING_DATA_IMPORT, ACTIVE, SUSPENDED, DISABLED, ARCHIVED, PURGE_APPROVED | [audit], [ver] | `/api/v1/platform/admin/companies/**`, login discovery | Yes (status → `TENANT_STATUS`, login code) | Low |
| `tbl_company_profile` | Contact and branding (1:1) | `id` | logo key (control-panel bucket), contacts | company | company | — | [audit] | company profile API | Branding cache | Personal contact data |
| `tbl_company_setting` | Per-company settings (integrations) | `id` | key, value | company | (company, key) | — | [audit] | settings API | Yes (`TENANT_SETTINGS`) | Low |
| `tbl_company_status_history` | Append-only status log | `id` | from, to, reason, actor, changed_at | company | — | — | append-only | — | No | Low |
| `tbl_id_sequence` | Atomic counters (COMPANY, QUOTE, IMPORT_RUN) | `sequence_name` | `next_value` | — | PK | — | [ver] | internal | No | No |

### 5.2 Identity, Keycloak and resources

| Table | Purpose | PK | Important columns | FK | Unique | Status | API | Runtime-critical | Sensitive |
|---|---|---|---|---|---|---|---|---|---|
| `tbl_identity_realm` | Realm registry (platform + company realms) | `id` | `realm_type` (PLATFORM, COMPANY), `realm_name`, `issuer_uri`, `jwks_uri`, `template_version`, `last_reconciled_at` | company (COMPANY only) | company; realm name; issuer | PROVISIONING, ACTIVE, DISABLED, ARCHIVED | `GET /api/v1/internal/platform/identity-realms` (issuer allow-list) | **Yes** (gateway and services, cached) | Security config |
| `tbl_company_resource` | Registry of every company resource | `id` | `resource_type` (DATABASE, BUCKET, IDP_REALM), `module_id`, `resource_name`, `db_host`, `db_port`, `schema_version`, `verified_at` | company, module | (type, name); (company, type, module_key) | RESERVED, CREATED, VERIFIED, DEGRADED, ORPHANED, RETIRED | metadata API `TENANT_META` | **Yes** (routing, cached) | Infra metadata (no secrets) |
| `tbl_identity` | A Keycloak account known to the platform | `id` | `identity_realm_id`, `keycloak_user_id` (`sub`), `account_scope` (PLATFORM, COMPANY), `status` | realm | (realm, sub) | PENDING_VERIFICATION, ACTIVE, SUSPENDED, DISABLED, REMOVED | internal | Audit and actor resolution | Personal |
| `tbl_company_user` | Membership **mirror** (seats, status, audit). **Not** role storage. | `id` | `company_id`, `identity_id`, `status`, `is_company_super_admin` (mirror) | company, identity | identity (one company per account) | INVITED, ACTIVE, INACTIVE, REMOVED | `/control-panel/api/v1/company/users` | Seats | Personal |
| `tbl_platform_admin` + `tbl_platform_admin_company_scope` | Platform staff status and company scope | `id` | `admin_type`, `scope_mode`, `mfa_required` | identity | identity | ACTIVE, SUSPENDED, DISABLED | platform admin APIs | Platform authorization | Personal |
| `tbl_identity_sync_task` | Outbox of Keycloak operations (realm-aware) | `id` | `task_type` (CREATE_REALM_ADMIN_USER, INVITE_COMPANY_USER, ASSIGN_/REVOKE_COMPANY_ROLE, CREATE_COMPANY_ROLE, ASSIGN_/REVOKE_PLATFORM_ROLE, DISABLE_/ENABLE_ACCOUNT, DISABLE_/ENABLE_REALM, …), target realm, `dedup_key`, attempts | identity | — | PENDING, DONE, FAILED | internal worker | No | Personal (email) |

Realm and client metadata:
- Client ids are fixed by convention (§7), so no client table is needed.
- `template_version` records what was applied. Optional **PROPOSED (gap)**: Keycloak internal client UUIDs, if the implementation needs them cached.

### 5.3 Catalog

| Table | Purpose | Important columns | Unique | Status |
|---|---|---|---|---|
| `tbl_module` | Modules (applications), incl. PLATFORM_CORE (attachment, notification, control-panel) | `code`, `gateway_token`, `db_token` (immutable), `service_name`, `module_type` (BUSINESS, PLATFORM_CORE, ADDON), `requires_database`, `requires_bootstrap` | code, gateway token, db token | DRAFT, ACTIVE, RETIRED |
| `tbl_module_dependency` | Mandatory and optional dependencies | module, depends_on, type | pair | — |
| `tbl_bundle` / `tbl_bundle_module` | Bundles of modules (closed under mandatory dependencies) | code; pair | code; pair | DRAFT, ACTIVE, RETIRED |

### 5.4 Pricing

| Table | Purpose | Notes |
|---|---|---|
| `tbl_plan` | A sellable offer (module, bundle, seats, storage) | `is_custom` for contract plans |
| `tbl_plan_version` | Immutable price version | DRAFT, ACTIVE, RETIRED; **effective / version** |
| `tbl_plan_price` | Monthly and yearly prices per currency | per version |
| `tbl_discount`, `tbl_discount_target`, `tbl_coupon`, `tbl_discount_redemption` | Catalog discounts and coupons | **FUTURE** (R1 uses MANUAL order adjustments) |

### 5.5 Subscription and entitlement

| Table | Purpose | Status / notes |
|---|---|---|
| `tbl_subscription` | Per company, several allowed; `subscription_number`, `billing_period`, `currency`, dates, `auto_renew` (0 in R1) | PENDING, ACTIVE, EXPIRED, SUSPENDED, CANCELLED (TRIAL and PAST_DUE reserved) |
| `tbl_subscription_item` | Modules, bundles, dependencies, extra users and storage, with a price snapshot | — |
| `tbl_subscription_period` | Renewal history | — |
| `tbl_subscription_event` | Append-only transition log | — |
| `tbl_company_entitlement` | **Materialized** module entitlement; the source of `COMPANY_MODULES` | ACTIVE, DEGRADED, …; charged guard (no double charging) |
| `tbl_company_allowance` | Limits: USERS (seats), STORAGE_BYTES | summed over active subscriptions |
| `tbl_company_usage`, `tbl_company_usage_snapshot` | Billable users, storage bytes | daily reconciliation |

### 5.6 Order and billing

| Table | Purpose | Immutability |
|---|---|---|
| `tbl_billing_account` | GST identity and place of supply of the company | Snapshot copied into each invoice |
| `tbl_billing_order` (+ `_line`, `_discount`, `_tax`, `_warning`) | Priced commitment; DRAFT → QUOTED → AWAITING_PAYMENT → PAID → FULFILLED / CANCELLED / EXPIRED; `confirmed_amount`, `outstanding_amount` | Frozen at QUOTED |
| `tbl_invoice` (+ `_line`, `_tax_line`) | Tax invoice: ISSUED_UNPAID (policy-dependent), ISSUED_PAID, CREDITED, VOID; snapshots; `content_sha256`; seller-entity and billing-policy versions | **Never updated or deleted** (only status and derived balances, with history) |
| `tbl_credit_note` (+ `_line`) | Corrections and cancellations | Immutable |
| `tbl_payment` + `tbl_manual_payment` | One row per instrument; verification fields; evidence key | Never deleted |
| `tbl_payment_status_history` | Append-only payment transitions | Append-only |
| `tbl_document_series` | Atomic numbering per series and FY, `format_pattern` | — |
| **PROPOSED (gap)** `tbl_invoice_status_history`, `tbl_billing_order_status_history` | Append-only status logs for invoices and orders | Append-only |
| `tbl_payment_allocation` | FUTURE | — |

### 5.7 Tax and invoice configuration (CD-25)

| Table | Holds | Lifecycle |
|---|---|---|
| `tbl_billing_policy` | Invoice trigger (ON_FULL_PAYMENT / ON_ORDER_CONFIRMATION), proforma, FY start, rounding | DRAFT → APPROVED → RETIRED; `effective_from` / `effective_to`; `approved_by`, `approved_at`, `approval_reference` |
| `tbl_seller_entity` | Legal name, GSTIN, PAN, state, address, SAC, e-invoice flag and provider | Same, versioned |
| `tbl_tax_rule` | GST components and rates per supply type and SAC / plan type | Same, versioned |
| `tbl_document_series` | Numbering patterns | Pattern per FY |

**PROPOSED (gap):** `approval_evidence_object_key` on the three versioned tables, to store the CA document in the platform bucket.

### 5.8 User and access policy (CD-11, CD-12)

| Table | Holds | Never holds |
|---|---|---|
| `tbl_permission` | Permission catalog (`<module>.<resource>.<action>`, domain, module, default data scope, grantable) | — |
| `tbl_role` | Role **definitions and templates** (domain, SYSTEM/CUSTOM, module, level, the realm-role name, `policy_version`) | Who holds the role |
| `tbl_role_permission` | **CP Permission Policy**: role → permission (+ data scope) | — |

Role-to-module mapping is `tbl_role.module_id`. **Actual user → role assignment lives only in the company Keycloak realm** (removed CP tables: `tbl_company_user_role`, `tbl_platform_admin_role`, `tbl_company_user_module_access`).

### 5.9 Provisioning, audit, operations

| Table | Purpose |
|---|---|
| `tbl_provisioning_job` | One job (CREATE_COMPANY, ADD_MODULE, REPAIR, SCHEMA_UPGRADE, REALM_TEMPLATE_UPGRADE); lease, attempts, `active_guard` |
| `tbl_provisioning_step` | One row per step and resource; status, attempts, last error (scrubbed) |
| `tbl_platform_audit_log` | Every privileged action (§17) |
| `tbl_platform_setting` | Platform-wide settings |
| `tbl_platform_email_message` (+ recipient, attempt) | Platform email log (R1, CD-23) |
| `tbl_data_import_run`, `tbl_data_import_check` | Generic company data import (Shivam later) |

---

## 6. Keycloak architecture

```text
Keycloak (one installation, 25.0.6 — CD-30)
 +-- master realm .............. operator only; service account erp-realm-provisioner (create-realm)
 +-- erp-platform .............. platform realm
 |     roles:   PLATFORM_SUPER_ADMIN (operator-assigned only), PLATFORM_ADMIN (+ further platform roles)
 |     users:   platform staff (OTP mandatory)
 |     clients: platform-console, svc-<service> (M2M), erp-platform-identity-admin
 +-- co-101 (Company A) ........ one realm per company
 |     roles:   COMPANY_SUPER_ADMIN, COMPANY_ADMIN, CRM_MANAGER, CRM_USER, HRMS_MANAGER, ..., CUSTOM_*
 |     users:   company A users only
 |     clients: erp-web, erp-identity-admin, [erp-portal]
 +-- co-102 (Company B) ........ same structure, completely separate
```

**Realm creation chain** (least privilege, CD-17):

```text
Platform Super Admin --(platform.company.provision)--> Control Plane
  --> Provisioning Worker / Realm Provisioning Service
  --> erp-realm-provisioner (secret in the secret store; CP worker only)
  --> Keycloak Admin API --> create co-<n> --> template --> roles --> clients --> CP registry
```

- Platform Admins never hold master or platform admin credentials. No admin credential is in any frontend.
- `create-realm` grants full access **only to the realms the account creates**.
- Day-to-day user operations use `erp-identity-admin` of each company realm. It has user-management rights in that single realm, and no realm configuration rights.
- **Audit:** every Keycloak admin call made by the worker is logged in `tbl_platform_audit_log`, with the platform actor who triggered the job and the correlation id. Keycloak's own admin events are enabled in every realm for cross-checking.

---

## 7. Keycloak client design

| Client ID | Realm | Type | OAuth flow | Redirect URIs | Scopes / audience | Client roles | Realm roles used | Service account | Users interact? |
|---|---|---|---|---|---|---|---|---|---|
| `platform-console` | `erp-platform` | Public | Authorization Code + PKCE | The platform console URL | `openid`, built-in `roles`, audience `nyaalkaran-erp` | None | `PLATFORM_SUPER_ADMIN`, `PLATFORM_ADMIN` | No | Yes (platform staff) |
| `svc-<service>` (one per service) | `erp-platform` | Confidential | Client Credentials | — | audience per target group; `azp` checked on internal APIs | None | None | Yes (M2M only) | No |
| `erp-platform-identity-admin` | `erp-platform` | Confidential | Client Credentials | — | — | `realm-management`: manage/view/query-users | — | Yes | No |
| `erp-realm-provisioner` | `master` | Confidential | Client Credentials | — | — | — (master realm role `create-realm`) | — | Yes | No |
| `erp-web` | each `co-<n>` | Public | Authorization Code + PKCE | The ERP UI login callback (no subdomains in R1) | `openid`, built-in `roles` (`realm_access.roles`), audience mapper `nyaalkaran-erp`, hard-coded `tenant_id` mapper; **Full Scope Allowed = on** | None | All company roles of the user | No | Yes (company users) |
| `erp-identity-admin` | each `co-<n>` | Confidential, service account only | Client Credentials, **signed JWT** (CP key pair via JWKS URL) | — | — | `realm-management`: `manage-users`, `view-users`, `query-users`, `view-realm` | — | Yes (CP only) | No |
| `erp-portal` | each `co-<n>` (if the customer portal is used) | Public | Authorization Code + PKCE | Portal callback | Full Scope Allowed = **off** + role scope mappings to portal roles | None | Portal roles only | No | Yes (company's customers) |

- **No gateway client** and no per-service client in company realms. The gateway and the services are resource servers: they validate tokens through the issuer's JWKS.
- **Business roles are realm roles only**, defined once per realm. No client duplicates them (CD-14). Client roles appear only as Keycloak's built-in `realm-management` roles for service accounts.

---

## 8. Company login flow (R1, Company Login Code)

```text
User --(enters ABCMFG01)--> ERP login page
  --> POST /public/auth/discover {loginCode}        (gateway public route, rate-limited)
  --> CP: tbl_company.login_code -> company ACTIVE? -> tbl_identity_realm ACTIVE?
        no  -> generic "Invalid Company Code" (same answer for unknown / suspended / disabled)
        yes -> { authorizationEndpoint of co-101, clientId: erp-web, issuer of co-101 }   (P03-T12; 5 / min / IP)
  --> browser redirect to https://<keycloak>/realms/co-101/protocol/openid-connect/auth (PKCE)
  --> user authenticates (OTP for admin roles)
  --> tokens (iss = .../realms/co-101, sub, aud nyaalkaran-erp, tenant_id CMP_101, realm_access.roles)
  --> Gateway validates: issuer in registry and ACTIVE; signature via that issuer's JWKS;
      audience; expiry; registry(issuer) == tenant_id; company ACTIVE
  --> company runtime context (X-Company-Code from the registry, never from the request)
  --> microservice (re-verifies the token itself)
```

**Why users cannot choose a realm or issuer:**
- The discovery API returns only the realm registered for that login code.
- The gateway accepts only issuers in `tbl_identity_realm` with status ACTIVE.
- The company is taken **from the registry mapping of the issuer**, and the realm's hard-coded `tenant_id` claim must match it.
- A token minted by any other Keycloak realm or server is rejected (401). Client-sent `X-Company-Code` headers (and the retired `X-Organization-Id` / `Active-Org`) are stripped (P02-T11 stage D).

The realm name appears in the Keycloak URL. It is a non-sensitive technical identifier and exposes no infrastructure details.

---

## 9. Database routing

```text
verified JWT -> TenantVerificationFilter -> TenantContext(CMP_101, sub, roles)
  -> TenantMetadataResolver(CMP_101, CRM-SERVICE)   cache: TENANT_META:CMP_101:CRM-SERVICE
       miss -> CP internal metadata API -> tbl_company_resource (DATABASE, module CRM, VERIFIED)
  -> DynamicRoutingDataSource: pool "CMP_101" -> jdbc:mysql://<db_host>:<db_port>/cmp_101_crm_db
       (user/password = environment credentials, CD-03)
  -> query runs only on cmp_101_crm_db
  -> finally: TenantContext cleared
```

| Concern | Design |
|---|---|
| Cache | Metadata cached per (tenant, service) in Redis and locally; evicted on `tenant-invalidation` (status, resource, realm change) |
| Fallback | No fallback DB. Unknown tenant, unregistered or non-VERIFIED resource, or CP unavailable with no cache → **fail closed** (503 / 403); never route to a default DB |
| Validation | DB name format and exact registry match; company ACTIVE; module entitled (`COMPANY_MODULES`) before any query |
| Connection pools | One small Hikari pool per (service, company), created lazily for ACTIVE entitled companies; idle eviction; `evictPool` on suspension and invalidation (sized for 50 companies, CD-18) |
| Context cleanup | The filter clears `TenantContext` and MDC in `finally`; `MultiTenantSafetyEnforcer` rejects static datasources |
| Async / threads | `MdcTaskDecorator` copies and restores the context for `@Async`; `@RunPerTenant` sets and clears it per company for jobs; Quartz carries the tenant in the job data; STOMP uses namespaced destinations |
| Security | Tenant only from the verified token; headers re-verified in each service; services have no published ports; internal APIs need M2M + `azp` + internal token |
| Cross-company prevention | Registry-only DB names, exact checks, fail-closed routing, audited denials. Shared credentials remain the accepted risk R-08 (§19). |

---

## 10. Storage (MinIO) architecture

```text
Company ABC (CMP_101)
  cmp-101-control-panel-bucket   company-level assets: branding (platform/branding/...), company documents
  cmp-101-crm-bucket             CRM files
  cmp-101-hrms-bucket            HRMS files
  cmp-101-account-book-bucket    ACCOUNT-BOOK files (incl. the former local volume)
(no bucket for non-purchased modules; no shared business bucket)
```

| Concern | Design |
|---|---|
| Creation | At provisioning (P9) and `ADD_MODULE`; private policy; quota if supported; **never at runtime** |
| Ownership and registry | `tbl_company_resource` (BUCKET, company, module); VERIFIED before use |
| Object keys | Unchanged existing convention: `<module>/<entity>/<formSegment>/<entityId>/documents/<attachmentId>/v<n>/<file>`; metadata in `cmp_<n>_attachment_db.attachment_metadata` |
| Access | Only through COMMON-ATTACHMENT / FILE-SERVICE; downloads are proxied; the bucket is resolved from the registry for (company, module), and clients never pass a bucket or key |
| Checks | Exact bucket equality + module key prefix + row module = request module (fixes today's prefix bug) |
| Authorization | Company entitled to the module + a module role in the token + the read/write permission of the CP policy |
| Credentials | Environment MinIO keys (CD-03 principle); per-company MinIO accounts are future |
| Usage | Per-upload deltas + daily reconciliation; limit check before writing; warnings at 80 % and 95 % |

---

## 11. Roles, permissions, entitlements

```text
Authentication (verified token)  !=  Role (Keycloak)  !=  Permission (CP policy)
                                 !=  Module entitlement (CP)  !=  Subscription (CP)

ALLOW only if:
  token valid AND company = registry(issuer) = tenant_id AND company ACTIVE
  AND module entitled (subscription ACTIVE -> entitlement)
  AND user holds a role of that module (realm_access.roles)
  AND CP policy grants that role the permission for the operation
```

| Request | Role | Entitlement | Permission | Result |
|---|---|---|---|---|
| John updates a customer | `CRM_MANAGER` | CRM ACTIVE | `crm.customer.update` granted | **ALLOW** |
| John updates a customer | `CRM_MANAGER` | CRM subscription EXPIRED | — | **DENY** `MODULE_NOT_ENTITLED` |
| Mary deletes a customer | `CRM_USER` | CRM ACTIVE | `crm.customer.delete` not granted | **DENY** `PERMISSION_DENIED` |

Enforced by the gateway (coarse checks) and **every service** (`@RequiresPermission`). Hiding buttons in the frontend is not security.

---

## 12. Company admin flow

```text
Company Admin -> ERP UI (admin section of erp-web) -> ERP backend (CP company APIs)
   -> authorization (verified token of co-<n>, company-admin permission)
   -> policy checks (seats, escalation, entitlement, predefined-role protection)
   -> Keycloak Admin API via erp-identity-admin of co-<n> (outbox)
   -> company realm (the authoritative store of users and role assignments)
```

| Operation | Rule |
|---|---|
| Create or invite user | Seat check under the company lock (INVITED + ACTIVE ≤ USERS allowance) → user in the realm, no password → execute-actions email (platform SMTP) |
| Disable or enable | Frees or consumes a seat; disabling also revokes the sessions |
| Assign or remove roles | Only roles of entitled modules; the assigner must hold every permission of the role; a Company Admin never assigns `COMPANY_SUPER_ADMIN` / `COMPANY_ADMIN`; a Company Super Admin never assigns `COMPANY_SUPER_ADMIN` (platform only, Q-11) |
| Custom roles | `CUSTOM_<NAME>` realm role + CP policy rows; the name is immutable; delete only when unassigned |
| Predefined roles | Cannot be edited or deleted (409 `SYSTEM_ROLE_IMMUTABLE`) |
| Never | Keycloak console access, the platform realm, platform roles, another company's realm |
| Audit | Every change is in the platform / company audit; the realm reconciler reports console changes |

---

## 13. Provisioning state machine

The single chain sketched in the request is represented by **three linked state machines** (existing design). This keeps commercial, lifecycle and technical failures independent:

```text
ORDER:    DRAFT -> QUOTED -> AWAITING_PAYMENT -> PAID -> FULFILLED      (or CANCELLED / EXPIRED)
PAYMENT:  RECORDED -> PENDING_VERIFICATION -> CONFIRMED                  (or REJECTED / REVERSED)
COMPANY:  DRAFT -> PROVISIONING -> ACTIVE (or AWAITING_DATA_IMPORT -> ACTIVE)
                      |-> PROVISIONING_FAILED -> (retry) PROVISIONING
          ACTIVE <-> SUSPENDED;  ACTIVE -> DISABLED -> ARCHIVED -> PURGE_APPROVED (manual only)
JOB:      QUEUED -> RUNNING -> COMPLETED
                      |-> RETRYING -> RUNNING
                      |-> FAILED -> (retry) RETRYING  /  CANCELLED (only QUEUED or RETRYING)
STEP:     PENDING -> RUNNING -> COMPLETED | SKIPPED (already exists) | FAILED
```

**Mapping of the requested states:**

| Requested state | Represented by |
|---|---|
| DRAFT | company DRAFT |
| ORDER_CREATED | order DRAFT / QUOTED |
| PAYMENT_PENDING | order AWAITING_PAYMENT |
| PAYMENT_VERIFIED | order PAID |
| PROVISIONING_READY | order FULFILLED + no active job (the "Start Provisioning" button is enabled) |
| PROVISIONING_IN_PROGRESS | job RUNNING, company PROVISIONING |
| REALM_CREATED | step CREATE_REALM COMPLETED |
| DATABASES_CREATED | all CREATE_DATABASE steps COMPLETED |
| BUCKETS_CREATED | all CREATE_BUCKET steps COMPLETED |
| CLIENTS_CONFIGURED | step CONFIGURE_CLIENTS COMPLETED |
| ROLES_CONFIGURED | step CREATE_STANDARD_ROLES COMPLETED |
| RESOURCES_REGISTERED | step REGISTER_RESOURCES COMPLETED |
| COMPANY_ACTIVATED / READY | step ACTIVATE COMPLETED, company ACTIVE |
| FAILED | job FAILED, company PROVISIONING_FAILED |
| RETRY_REQUIRED | job FAILED awaiting the platform's `retry` (or RETRYING with backoff) |
| PARTIALLY_PROVISIONED | PROVISIONING_FAILED with some steps COMPLETED (visible per step; resources are kept) |
| SUSPENDED / DISABLED | company SUSPENDED / DISABLED |

**Idempotency guarantees:**
1. One active job per company (`active_guard` unique).
2. Every name is reserved before creation (unique registry).
3. Every step checks first and creates second (`IF NOT EXISTS`, `bucketExists`, realm or role lookup).
4. Completed steps are skipped on retry.
5. Job leases prevent two workers from running the same job.

A repeated "Start Provisioning" returns the existing job.

---

## 14. Payment and activation rule

- `ACTIVE subscription` only when Σ CONFIRMED payments = `grand_total` (several instruments allowed; unverified amounts never count; no partial activation).
- "Start Provisioning" is allowed only when:
  - the order is PAID / FULFILLED;
  - the company is DRAFT or PROVISIONING_FAILED;
  - no job is active.
- Permission: `platform.company.provision` (Super Admin; or a Platform Admin with this permission **and** the company in scope).
- Audit: `PROVISION_REQUESTED` with actor, company, order, job id, correlation id and IP.

---

## 15. Billing and invoice immutability

- An issued invoice is never updated in its financial content and never deleted. Only the status and derived balances change, in locked transactions, with history.
- Every invoice stores **snapshots**:
  - seller (the seller-entity version);
  - buyer (billing account);
  - lines;
  - tax rule versions, rates and components;
  - the billing-policy version;
  - the series and number;
  - the content hash.
- Corrections use a **credit note** (and a new invoice when needed), linked to the original. The original stays unchanged.
- Payments are never deleted. Rejections and reversals are new statuses with history.

---

## 16. Tax configuration (CD-25)

```text
DRAFT --(CA reference / document attached)--> APPROVED --(effective_from reached)--> EFFECTIVE --> RETIRED
```

- Configurable items:
  - tax rate; CGST + SGST / IGST; supply types;
  - seller legal entity, GSTIN, PAN, state, SAC;
  - e-invoice flag and provider;
  - numbering pattern and FY reset;
  - invoice generation timing;
  - rounding;
  - effective dates;
  - approval status.
- Only the Platform Super Admin (`platform.tax.manage`, non-grantable) manages them. No hard-coded CA-dependent value exists.
- **Current stage:** the values are PENDING (Q-42, Q-29, Q-04 tax).
  - Dev and staging use a SAMPLE configuration with watermarked PDFs.
  - Production quoting is gated until the configuration is APPROVED (gate G2a).
  - Historical invoices keep their snapshots.

---

## 17. Audit and security design

Each record has these fields:
- **who:** actor identity, name, scope;
- **what:** action, target type and id;
- **when:** `occurred_at`;
- **company:** `company_id`;
- **resource;**
- **old value / new value** (in `detail_json`, without secrets);
- **reason;**
- **correlation / request id;**
- **result** (SUCCESS, DENIED, FAILED);
- **IP;**
- **user agent / device** (**PROPOSED (gap)**: explicit `old_value_json`, `new_value_json`, `reason`, `user_agent` columns on `tbl_platform_audit_log` instead of free-form JSON).

| Event group | Actions (examples) |
|---|---|
| Company | COMPANY_CREATED, COMPANY_UPDATED, LOGIN_CODE_CHANGED, COMPANY_ACTIVATED, COMPANY_SUSPENDED, COMPANY_DISABLED, COMPANY_ARCHIVED |
| Commercial | ORDER_CREATED / QUOTED / CONFIRMED / CANCELLED, SUBSCRIPTION_ACTIVATED / RENEWED / EXPIRED / CHANGED |
| Payments and invoices | PAYMENT_RECORDED / VERIFIED / REJECTED / REVERSED, INVOICE_ISSUED, CREDIT_NOTE_ISSUED |
| Tax configuration | BILLING_POLICY / SELLER_ENTITY / TAX_RULE / SERIES _CREATED, _APPROVED, _RETIRED |
| Provisioning | PROVISION_REQUESTED, each step, REALM_CREATED, REALM_TEMPLATE_APPLIED, REALM_CLIENTS_CONFIGURED, REALM_REGISTERED, DATABASE_CREATED, BUCKET_CREATED, RESOURCES_REGISTERED, PROVISION_FAILED, PROVISION_RETRIED |
| Identity | USER_INVITED / CREATED / DISABLED / ENABLED, ROLE_ASSIGNED / REVOKED, CUSTOM_ROLE_CREATED / DELETED, PERMISSION_POLICY_CHANGED, PLATFORM_ROLE_ASSIGNED |
| Security | ACCESS_DENIED (cross-tenant, scope, permission), TENANT_MISMATCH, UNKNOWN_ISSUER |

**Never logged:** passwords, access or refresh tokens, client secrets, the signed-JWT private key, DB or MinIO passwords, SMTP app passwords, OAuth codes. Errors are scrubbed (`password=`, `IDENTIFIED BY`, token patterns).

---

## 18. Failure, rollback and retry design

**General rule:** **no automatic deletion.** A failed step leaves the created resources in place, recorded in the registry. Retry resumes from the failed step. Cleanup is a manual, audited decision.

| Failure | What happens |
|---|---|
| Realm OK, DB creation fails | Job → RETRYING with backoff (30 s × 2^k, max 1 h, 8 attempts) → FAILED, company PROVISIONING_FAILED. The realm stays registered as PROVISIONING / **disabled** (no logins). Retry resumes at CREATE_DATABASE. |
| DB OK, bucket fails | Same; the DB stays CREATED in the registry; retry resumes at CREATE_BUCKET |
| Bucket OK, client configuration fails | Same; resume at CONFIGURE_CLIENTS (idempotent update) |
| Role creation fails | Resume at CREATE_STANDARD_ROLES; existing roles are skipped |
| Company Super Admin creation fails | The outbox retries; the company can still become ACTIVE (visible as "invitation pending"); the platform can re-send |
| CP registration fails after a Keycloak or DB success | The next attempt finds the existing realm or DB with a RESERVED registry row and **adopts** it after verification (name reserved earlier = proven ownership). A realm or DB **without** a reservation is never adopted: FAILED + reconciliation report. |
| Interrupted (crash, restart) | The lease expires; another worker takes over; idempotent steps make the re-run safe; a stuck-job alert fires after 30 minutes |
| Same request twice | The second "Start Provisioning" returns the existing active job (unique `active_guard`); order and payment idempotency keys prevent duplicates |
| Keycloak or MySQL down for long | FAILED after the retries; manual `retry` later; nothing is dropped |

- **Reconciliation:** a daily `ResourceReconciliationJob` (read-only) compares MySQL schemas `cmp_%`, MinIO buckets `cmp-%` and Keycloak realms with the registry. It reports UNREGISTERED, MISSING and ORPHANED items.
- **Manual recovery:** `REPAIR` job (Super Admin) recreates **missing empty** resources only.
- **Compensation:** only by explicit, audited platform actions (e.g. disable a realm). Never automatic drops.

---

## 19. Security boundaries

| Boundary | What prevents crossing it |
|---|---|
| **Company A user → Company B DB** | The user's token is from `co-101`; the company comes from the registry mapping of the issuer; DB names come only from VERIFIED registry rows of CMP_101; headers are stripped and re-verified; fail-closed routing. **Residual risk R-08:** the shared DB login means a compromised service process could reach other schemas. Mitigation: least-privilege runtime account (`cmp\_%` + CP only, no admin), no published ports, network isolation, monitoring. |
| **Company A user → Company B bucket** | Bucket resolved only from the registry for (CMP_101, module); exact equality check; proxied downloads; clients never pass a bucket |
| **Company Admin → Platform Admin** | Platform roles exist only in `erp-platform`; platform authority is evaluated only for the platform issuer; company APIs cannot touch the platform realm |
| **Company Admin → Keycloak console / master** | No console access or credentials for company admins; only CP service accounts call the Admin API; `erp-identity-admin` is scoped to one realm |
| **Company role → unsubscribed module** | The entitlement check runs first and independently of roles (gateway and service filters); a role of a non-entitled module grants nothing |
| **Realm boundary** | Users and roles live in different realms; there are no cross-realm identities (CD-02) |
| **Module boundary** | Each service routes only to its own module DB and bucket; Feign calls to non-entitled modules fail softly |

---

## 20. Complete resource creation matrix

| Resource | Created when | Created by | Owner | Stored where | Naming | Can change? | Audit |
|---|---|---|---|---|---|---|---|
| Company | S1 | Platform Super Admin via CP | CP | `tbl_company` | CMP_<n> | Display data yes; codes never | COMPANY_CREATED |
| Company Login Code | S1/S3 | CP | CP | `tbl_company.login_code` | `^[A-Z0-9]{6,12}$` | Audited platform operation only | LOGIN_CODE_CHANGED |
| Order | S5 | Platform via CP | CP | `tbl_billing_order*` | ORD series | Frozen at QUOTED | ORDER_* |
| Subscription | S6 (PENDING) / S8 (ACTIVE) | CP | CP | `tbl_subscription*` | SUB series (`SUB/{FY}/{SEQ:5}`, CD-33) | Via orders only (renewal, change) | SUBSCRIPTION_* |
| Payment | S7 | Authorized platform user | CP | `tbl_payment`, `tbl_manual_payment` | PAY / RCT series | Status only; never deleted | PAYMENT_* |
| Invoice | S6 or S8 (policy) | CP | CP | `tbl_invoice*` | INV series | Never; credit note instead | INVOICE_ISSUED |
| Entitlement | S8 | Entitlement engine | CP | `tbl_company_entitlement` → `COMPANY_MODULES` | — | Recomputed from subscriptions | ENTITLEMENT_CHANGED |
| Provisioning job | S9 | Platform action | CP | `tbl_provisioning_job/step` | PJ-<id> | Status only | PROVISION_* |
| Keycloak realm | P2–P3 | Worker (`erp-realm-provisioner`) | Keycloak + CP registry | Keycloak; `tbl_identity_realm`; resource IDP_REALM | `co-<n>` | Never (template upgrades only) | REALM_* |
| Keycloak clients | P5 | Worker | Keycloak | Realm | fixed ids | Template upgrades only | REALM_CLIENTS_CONFIGURED |
| Realm roles (predefined) | P4, ADD_MODULE | Worker | Keycloak (+ CP definition and policy) | Realm; `tbl_role` | naming §6.5.9 of PHASE_01 | Never by companies | REALM_ROLES_CREATED |
| Users | P11 (Super Admin); later by the company admin | CP via `erp-identity-admin` | Keycloak (CP mirror) | Realm; `tbl_identity`, `tbl_company_user` | Keycloak `sub` | Profile yes; realm never | USER_* |
| Module DB | P7–P8 | Worker (environment provisioning account) | Company (data), CP (registry) | MySQL; `tbl_company_resource` | `cmp_<n>_<db_token>_db` | Never; never dropped automatically | DATABASE_CREATED |
| Control-panel bucket | P9 | Worker | Company / CP registry | MinIO; registry | `cmp-<n>-control-panel-bucket` | Never | BUCKET_CREATED |
| Module bucket | P9, ADD_MODULE | Worker | Company / CP registry | MinIO; registry | `cmp-<n>-<module>-bucket` | Never | BUCKET_CREATED |
| DB / bucket registry rows | P1 → P10 | Worker | CP | `tbl_company_resource` | = resource name | Status only | RESOURCES_REGISTERED |
| Permission policy | Seed (predefined), company admin (custom) | CP | CP | `tbl_permission`, `tbl_role`, `tbl_role_permission` | permission codes, role names | New `policy_version` per change | PERMISSION_POLICY_CHANGED |

---

## 21. Complete end-to-end example — ABC Manufacturing

**Input:** CRM + HRMS + ACCOUNT-BOOK, 50 users, yearly; first company created; login code `ABCMFG01`. Prices and tax amounts depend on the approved configuration, so they are not shown.

**Control Plane (`control_panel_db`):**

| Table | Rows |
|---|---|
| `tbl_company` | id 1, `CMP_101`, `ABCMFG01`, "ABC Manufacturing Pvt Ltd", status DRAFT → PROVISIONING → **ACTIVE** |
| `tbl_company_status_history` | DRAFT, PROVISIONING, ACTIVE |
| `tbl_billing_account` | GSTIN, state code, billing email |
| `tbl_billing_order` + lines | `ORD/2026-27/00001`: CRM, HRMS, ACCOUNT-BOOK, 50 user seats (+ any mandatory dependency, e.g. per §11.2) → PAID → FULFILLED |
| `tbl_payment` + `tbl_manual_payment` | e.g. `PAY/…/00001` cash (CONFIRMED) + `PAY/…/00002` cheque (PENDING_VERIFICATION → CONFIRMED) |
| `tbl_invoice` + lines + tax lines | `INV/2026-27/00001`, ISSUED_PAID, with snapshots |
| `tbl_subscription` + items + period 1 | `SUB/…/00001`, yearly, ACTIVE |
| `tbl_company_entitlement` | CRM, HRMS, ACCOUNT-BOOK (charged); attachment, notification, control-panel (PLATFORM_CORE) |
| `tbl_company_allowance` | USERS 50, STORAGE_BYTES per plan |
| `tbl_identity_realm` | COMPANY, `co-101`, issuer `https://<kc>/realms/co-101`, template v1, ACTIVE |
| `tbl_company_resource` | IDP_REALM `co-101`; DATABASE `cmp_101_crm_db`, `cmp_101_hrms_db`, `cmp_101_account_book_db`, `cmp_101_attachment_db`, `cmp_101_notification_db`; BUCKET `cmp-101-crm-bucket`, `cmp-101-hrms-bucket`, `cmp-101-account-book-bucket`, `cmp-101-control-panel-bucket`. All VERIFIED. |
| `tbl_provisioning_job` / steps | PJ-1 CREATE_COMPANY, COMPLETED; ~20 step rows |
| `tbl_identity`, `tbl_company_user` | The Company Super Admin (`sub` from `co-101`), `is_company_super_admin`=1 |
| `tbl_platform_audit_log`, `tbl_platform_email_message` | Every action above; receipts, invoice, welcome and provisioning emails |

**Keycloak:**
- realm `co-101`;
- clients `erp-web`, `erp-identity-admin`;
- realm roles `COMPANY_SUPER_ADMIN`, `COMPANY_ADMIN`, `CRM_USER` … `CRM_SUPER_ADMIN`, `HRMS_USER` … `HRMS_SUPER_ADMIN`, `ACCOUNT_BOOK_USER` … `ACCOUNT_BOOK_SUPER_ADMIN`;
- users: the Company Super Admin (later up to 50 users created by the company admins).

**MySQL:** `cmp_101_crm_db`, `cmp_101_hrms_db`, `cmp_101_account_book_db`, `cmp_101_attachment_db`, `cmp_101_notification_db`.

**MinIO:** `cmp-101-control-panel-bucket`, `cmp-101-crm-bucket`, `cmp-101-hrms-bucket`, `cmp-101-account-book-bucket`.

**Runtime request (John, `CRM_MANAGER`, uploads a document to a CRM customer):**

```text
John -> login code ABCMFG01 -> discovery -> co-101 -> login -> JWT(iss co-101, tenant_id CMP_101, roles [CRM_MANAGER])
 -> Gateway: issuer ACTIVE in registry, signature, aud, exp, registry(co-101)=CMP_101=tenant_id,
             TENANT_STATUS ACTIVE, COMPANY_MODULES contains CRM, token has a CRM role
 -> CRM-SERVICE: TenantVerificationFilter (same checks), @RequiresPermission("crm.customer.update")
             via TENANT_ROLE_PERMS:CMP_101:CRM_MANAGER -> allowed
 -> routing: TENANT_META:CMP_101:CRM-SERVICE -> cmp_101_crm_db (environment credentials)
 -> file: COMMON-ATTACHMENT resolves cmp-101-crm-bucket from the registry; key crm/customer/...; metadata in cmp_101_attachment_db
```

---

## 22. Final architecture diagram

```text
                                    +---------------------------------------------+
  Platform staff --(platform-console)-->           KEYCLOAK (25.0.6)            |
                                    |  master: erp-realm-provisioner             |
  Company users --(login code)----->|  erp-platform: platform roles, svc-* M2M   |
        |                           |  co-101 / co-102 / ...: users, realm roles,|
        |                           |     erp-web, erp-identity-admin            |
        |                           +-------^-------------------^-----------------+
        |                                   | Admin API         | JWKS per issuer
        v                                   |                   |
  +-----------+   discovery    +------------+-------------------+-----------------------------+
  |  ERP UI   |--------------->|  API-GATEWAY: issuer allow-list (registry), token checks,     |
  +-----------+   API calls    |  company = registry(issuer), status + entitlement, strip hdrs |
                               +----------+------------------------------+---------------------+
                                          |                              |
                    platform routes       |                              | business routes
                                          v                              v
  +-----------------------------------------------+   +-------------------------------------------+
  | CONTROL PLANE (CONTROL-PANEL-SERVICE)          |   | COMPANY MODULE SERVICES (CRM, HRMS, ...)   |
  |  Company registry / login code                 |   |  TenantVerificationFilter, entitlement     |
  |  Catalog, plans, pricing, orders               |   |  filter, @RequiresPermission (CP policy)   |
  |  Payments, invoices, tax config (CD-25)        |   |  DynamicRoutingDataSource (env creds)      |
  |  Subscriptions -> entitlements -> COMPANY_MODULES  |   |  COMMON-ATTACHMENT (registry buckets)      |
  |  Permission policy -> TENANT_ROLE_PERMS        |   +-----+----------------------+---------------+
  |  Realm registry, resource registry             |         |                      |
  |  Provisioning worker + Realm Provisioning Svc  |         v                      v
  |  Identity-sync outbox (erp-identity-admin)     |  +----------------+   +-------------------------+
  |  Platform audit, platform email (one mailbox)  |  | MySQL          |   | MinIO                   |
  +----+-------------------+-----------------------+  | cmp_<n>_<m>_db |   | cmp-<n>-<m>-bucket      |
       |                   |                          | cmp_<n>_attach |   | cmp-<n>-control-panel-  |
       v                   v                          | cmp_<n>_notif  |   |   bucket                |
  control_panel_db      Redis (COMPANY_MODULES, TENANT_STATUS, TENANT_ROLE_PERMS, TENANT_META, invalidation)
```

---

## 23. Implementation readiness

**A. Already decided:** CD-01..CD-27 (PHASE_01 §0); naming (§3); realm, client and role design (§6–7); login (§8); routing (§9); storage (§10); authorization (§11–12); provisioning and failure model (§13, §18); immutability (§15).

**B. Configurable:**
- tax, seller, billing policy and numbering (CD-25);
- plans, prices and catalog (DB-driven);
- the permission policy (predefined templates + custom roles);
- the realm template version;
- company settings and integrations.

**C. Business / CA input still needed:**
- Q-42, Q-29, Q-04 tax values (CA);
- owner action items: Q-25 (UI owner), Q-02 (Shivam data), Q-22 (credential rotation), Q-30 / Q-37 (future accounts).

**D. PHASE_02 work:**
- the Company-based CP entities, including `tbl_company` (+ `login_code`), `tbl_company_resource`, `tbl_id_sequence`, `tbl_identity_realm`, status history (schema generated by Hibernate `ddl-auto: update` during development; **no CP Liquibase yet**, CD-27);
- the Company model and the code allocator;
- the `TenantResourceNaming` extensions (realm name, exact validators);
- the tenant Liquibase migrator and bootstrap modes;
- the metadata API with environment credentials.

**E. PHASE_03 work:**
- the `erp-platform` runbook and the service accounts;
- the company realm template as code;
- the realm registry and issuer API;
- gateway issuer-based resolution;
- `TenantVerificationFilter`;
- login-code discovery;
- M2M hardening and dual internal secrets;
- network surface;
- tenant-safe jobs and invalidation;
- the realm reconciler, export and monitoring basics.

**E2. Final CP Liquibase (PHASE_11A, CD-27):** after the CP model of PHASE_02..PHASE_11 is complete and stable: audit the final entities and the generated schema, write one clean Liquibase baseline, switch the CP to `ddl-auto: validate`, retire the old generated changelogs.

**F. Must NOT be implemented yet:**
- anything before this guide and PHASE_01 are approved;
- any Control Plane Liquibase changelog before PHASE_11A (no early baseline, no per-phase CP changelogs, no temporary `legacy` changelog layer; CD-27);
- PHASE_13 (user OAuth email) and PHASE_14 (website, gateway payments, automatic renewal);
- the actual tax values;
- Shivam identity migration or data-import execution;
- any production change;
- automatic deletion or purge;
- the central role-policy editing UI;
- per-company MinIO accounts;
- subdomain or broker login.

**G. Architectural contradictions found** (none changes a decision; they are clarified or reported):
1. **Subscription vs company activation timing:** the request's sketch activates the subscription at the end. The documents activate the **subscription** at 100 % verified payment (CD-07) and the **company** at the end of provisioning. This guide follows the documents. Access is still impossible before the company is ACTIVE.
2. **Single provisioning state machine requested:** the documents use separate order, payment, company and job state machines. §13 maps every requested state onto them.
3. **"Create Company Admin / Users" during provisioning:** the documents create only the Company Super Admin; other users come later through the company admin (CD-13).
4. **Client names:** the request mentions `platform-web`, `platform-api` and `gateway`. The documents use `platform-console` and `svc-<service>`; no gateway client exists, because the gateway is a resource server.
5. **Stale step code:** the `tbl_provisioning_step.step_code` example in PHASE_01 §6.2 B still lists `ADD_MODULE_ROLES`. It must read the CD-17 step names (`CREATE_STANDARD_ROLES`, `APPLY_REALM_TEMPLATE`, `CONFIGURE_CLIENTS`, `REGISTER_REALM`). **Corrected in PHASE_01** (editorial alignment with CD-17).
6. **Bucket naming notation:** in `cmp-<n>-…` the number is the company-code number (`CMP_101` → `101`), not the internal `tbl_company.id`.
7. **Identifier consistency (CD-26, owner decision):** `CMP_<n>` is the single company identifier; the former separate tenant key `ORG_<n>` and the `org_` / `org-` prefixes are retired. Code impact: `TenantResourceNaming` prefixes, the `ORG_MODULES` key constant, the CP allocator, the gateway claim check, the identity-sync id pattern; development DBs and buckets named `org_*` / `org-*` are LEGACY and are not reused.

**H. Missing tables / resources (PROPOSED; items 1 and 4 approved by CD-28, item 5 by CD-32 (F5-2), item 3 by CD-33; items 2 and 6 still need approval):**
1. **`tbl_company_resource` uniqueness defect:** **APPROVED by the owner (CD-28 item 1, 2026-10-03).**
   - the attachment and notification DBs are both DATABASE rows with `module_id` NULL, so UQ (`company_id`, `resource_type`, `module_key`) rejects the second one;
   - proposal: register them (and the control-panel bucket) against their **PLATFORM_CORE** rows in `tbl_module` (`attachment`, `notification`, `control-panel`), leaving `module_id` NULL only for IDP_REALM.
2. `tbl_invoice_status_history` and `tbl_billing_order_status_history` (append-only).
3. A `SUBSCRIPTION` series in `tbl_document_series` (the `subscription_number` format is undefined). **APPROVED by the owner (CD-33, 2026-10-08):** series code `SUBSCRIPTION`, format `SUB/{FY}/{SEQ:5}`, example `SUB/2026-27/00001`, FY reset yes, width 5; the P06-T2 `SubscriptionNumberAllocator` uses it (no second format).
4. Explicit audit columns `old_value_json`, `new_value_json`, `reason`, `user_agent` on `tbl_platform_audit_log`. **APPROVED by the owner (CD-28 item 2, 2026-10-03):** explicit columns for actor, action, entity/resource type, entity id, timestamp and before/after values or metadata where applicable.
5. `approval_evidence_object_key` on the versioned tax-configuration tables. **APPROVED by the owner (CD-32 / F5-2, 2026-10-07).**
6. Key management for the signed-JWT key pair of `erp-identity-admin`: storage, rotation, and a JWKS URL reachable only from Keycloak.

**I. Security gaps:**
1. **R-08:** shared DB and MinIO credentials (accepted, with compensating controls).
2. `erp-realm-provisioner` has full rights on every company realm it creates: highest-value secret → secret store, rotation, CP-worker-only network path, alerting.
3. Legacy code to fix (PHASE_03 / PHASE_10):
   - unverified token decoding (`JwtAccessTokenUtil`, `CrmDataAccessAuthority`, `JwtRoleExtractor`);
   - the HRMS/PAYROLL `X-Tenant-Id` fallback;
   - ~~`Active-Org` / `ORG_` group selection~~ — removed by P02-T11 stage D (company only from the verified `tenant_id` claim) and P03-T5 (issuer registry);
   - realm-wide user APIs;
   - the bucket prefix check;
   - published service ports;
   - Feign defaults (`192.168.5.15`, `admin-cli`);
   - the committed QC SMTP password.
4. The public discovery endpoint needs rate limiting and uniform responses.

**J. Migration risks:**
1. Shivam's identity migration (password-hash verification; `sub` mapping for business data).
2. Legacy schemas vs the Liquibase baseline (diff gate).
3. Unversioned control-plane code in SVN (owner safeguard before PHASE_02).
4. Role-name migration (`PLATFORM_SUPERADMIN` → `PLATFORM_SUPER_ADMIN`, seeder levels).
5. The production UI outside this repository (Q-25).
6. The legacy `ERPSystem` realm must stay untouched until cutover.

---

**Next step:** the guide is approved (S0-1 DONE, 2026-10-03). The PROPOSED items of §23 H stay open (S0-2). The PHASE_02 implementation plan exists ([plans/PHASE_02_IMPLEMENTATION_PLAN.md](plans/PHASE_02_IMPLEMENTATION_PLAN.md)); no code is written before S0-4 and S0-5 are DONE.
