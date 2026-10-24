# PHASE 08 — Company Provisioning

| Item | Value |
|---|---|
| Master architecture | [SAAS_ARCHITECTURE_MASTER.md](SAAS_ARCHITECTURE_MASTER.md) §6.3–6.6, §7, §19.1–19.2 |
| Design source | [PHASE_01 §6.1.3, §6.2 A and B, §6.8.1](PHASE_01_SAAS_FOUNDATION.md) |
| Depends on | [PHASE_02](PHASE_02_CP_SCHEMA_AND_TENANCY_IMPLEMENTATION.md) (registry, naming, Liquibase migrator), [PHASE_03](PHASE_03_IDENTITY_KEYCLOAK_AND_COMPANY_CONTEXT.md) (realm template, realm-aware identity sync), [PHASE_06](PHASE_06_SUBSCRIPTIONS_AND_ENTITLEMENTS.md) (entitlements), [PHASE_07](PHASE_07_BILLING_ORDERS_INVOICES_AND_MANUAL_PAYMENTS.md) (a paid order triggers provisioning) |
| Enables | PHASE_09, PHASE_10, PHASE_12 |
| Status | **PARTIALLY_COMPLETED** |

> **Revision 2026-10-03 (v5, decision register):**
> - **CD-03:** no per-company MySQL accounts. The steps `CREATE_DB_ACCOUNTS`, `GRANT_PRIVILEGES` and `STORE_CREDENTIALS`, the job `ROTATE_CREDENTIALS` and `DbAccountProvisioner` are **withdrawn**. Databases are reached with the environment credentials.
> - **CD-01:** one database **and one bucket per purchased module**, replacing the single `org-<n>-files` bucket.
> - **CD-06:** a new `CREATE_REALM` step creates the company's Keycloak realm from the template. The `ORG_` group is dropped.
> - **CD-07:** provisioning starts only after the order is fully paid.
> - **Final decision review:**
>   - realm operations go through the **Realm Provisioning Service** with the dedicated service account `erp-realm-provisioner` (CD-17);
>   - realm steps `CREATE_REALM`, `APPLY_REALM_TEMPLATE`, `CREATE_STANDARD_ROLES`, `CONFIGURE_CLIENTS`, `REGISTER_REALM`, `ENABLE_REALM` / `DISABLE_REALM`, `RECONCILE_REALM`;
>   - **Shivam uses exactly this flow** (CD-16): no Shivam-specific provisioning code;
>   - provisioning completes only after the full verified payment (CD-20).
> - **Q-40 decided (CD-24):** in R1 provisioning starts by an explicit platform action ("Start provisioning") after the full verified payment; automatic start is future. On completion a platform email is sent to the Company Super Admin (CD-23).

---

## 1. Objective

Turn the existing provisioning engine into a **durable, entitlement-driven, idempotent and non-destructive** workflow. For each company it:

1. Creates the databases of the **entitled modules only**, plus the attachment and notification databases.
2. Creates the **company Keycloak realm** `co-<n>` from the versioned realm template (CD-06, PHASE_03 §6.1) and registers it in `tbl_identity_realm`. **No per-company MySQL account is created** (CD-03): the services use the environment DB credentials.
3. Initializes every schema through the Liquibase tenant migrator (mode `SEEDED`, or `EMPTY_FOR_IMPORT` when the company was created with `onboarding_type=IMPORT_EXISTING_DATA`).
4. Creates **one bucket per purchased module** (`cmp-<n>-<module>-bucket`, CD-01), plus the platform-core bucket `cmp-<n>-control-panel-bucket` (branding and company-level files). The bucket semantics are completed in PHASE_09.
5. Registers every resource (DATABASE and BUCKET per module, IDP_REALM), creates and invites the Company Super Admin **inside the company realm**, publishes the caches, and activates the company.
6. Supports `ADD_MODULE` (database + bucket of the new module), `REPAIR`, `SCHEMA_UPGRADE` and `REALM_TEMPLATE_UPGRADE` jobs.
7. Retries automatically, resumes after a restart, and **never deletes** anything on failure.

## 2. Existing implementation relevant to this phase

> **P02-T11 note (stage G, 2026-10-05):** the tables of this section describe the code as inspected on 2026-10-03. P02-T11 stages A–F (2026-10-04/05, REVIEW) have since replaced the organization tenancy: the CP organization / customer tier (`Customer`, `Organization`, `OrganizationDatabase`, `OrganizationUser`, `OrganizationUserBusinessRole`, `OrganizationQuotaService`, `CompanyOrganizationService`, `OrganizationGroupProvisioner`, `@OrganizationScoped`, `PlatformOrgAdminController`, …) is deleted (stage C); `InternalOrganizationController` → `InternalCompanyController` (`/api/v1/internal/platform/companies/...`), `OrganizationEntitlementSyncListener` → `CompanyEntitlementSyncListener`, `CustomerUser` → `CompanyUser` (`tbl_company_user`), `CustomerUsage` → `CompanyUsage`; runtime `TenantContext.companyCode`, header `X-Company-Code`, Redis `COMPANY_MODULES:<CMP_n>`, resource names `cmp_<n>_…` / `cmp-<n>-…` (stages A / B); gateway `CompanyContextFilter` / `CompanySelection`, company from the verified `tenant_id` claim, no `Active-Org` (stage D). Read old names in this section as these replacements.

| Component | Location | Notes (`CODE-INSPECTED`) |
|---|---|---|
| `PlatformProvisioningService` | `CONTROL-PANEL-SERVICE/.../provisioning/service/` | Atomic PENDING→PROVISIONING claim; `requestProvisioning` returns 202; manual `recoverProvisioning` / `resetStuckRecovery`; on failure the org stays PROVISIONING with a reason |
| `ProvisioningAsyncConfig` | `provisioning/config/` | In-process executor: core 2, max 4, queue 50, AbortPolicy. **Not durable.** |
| `TenantProvisioningService` | `provisioning/service/` | Attachment DB first, then **every** module in `tenant.provisioning.modules` (16, regardless of purchase), then `ensureOrganizationGroup`, then `markActive`. Buckets are created lazily at first upload and are not registered. |
| `DomainDatabaseProvisioningService` | same | CREATE_DATABASE (regex `^[a-z0-9_]{1,64}$`, `CREATE DATABASE IF NOT EXISTS`), VERIFY, REGISTER_ROUTING; idempotent skip; upserts are race-safe |
| `AttachmentDatabaseProvisioningService` + `LiquibaseAttachmentDbSchemaManager` | same | CREATE_DATABASE, APPLY_CHANGELOG, VERIFY_SCHEMA (table plus 2 indexes), REGISTER_ROUTING |
| `DomainTenantBootstrapService` + `HttpDomainTenantBootstrapClient` | same | Eureka `LoadBalancerClient`; `POST /api/v1/internal/tenant-bootstrap/{id}` with `X-Internal-Token`, `X-Company-Code` (was `X-Organization-Id`, P02-T11 stage B); connect timeout 5 s, read 300 s |
| `ConfigAttachmentDbAdminConnectionProvider` | same | `DriverManager` with `attachment.provisioning.*`, which defaults to the shared `DB_*` login |
| `PlatformProvisioningStatusService` | `provisioning/service/` | Jobs and steps, derived overall status |
| `OrganizationGroupProvisioner` | `platform/identity/` | Keycloak `ORG_<n>` group (sync or outbox). **Dropped** with a realm per company (CD-06). *(Deleted in P02-T11 stage C, 2026-10-05.)* |
| Tables | `tbl_provisioning_job` (organization_id, status, error), `tbl_provisioning_step` (job_id, sequence, name, status, error, completed_at) | No FK, no job type, no resource/module column |
| Tests | `PlatformProvisioningServiceTest` (25), `TenantProvisioningServiceTest`, `AttachmentDatabaseProvisioningServiceTest`, `DomainTenantBootstrapServiceTest`, `HttpDomainTenantBootstrapClientTest`, `AttachmentProvisioningMySqlIT` (17) | `TEST-EXISTS` |

## 3. Existing completed work

| Item | Status |
|---|---|
| Step-based provisioning with persisted job/step rows | PARTIALLY_COMPLETED (rows exist; durability missing) |
| Idempotent database creation and routing registration | COMPLETED (reused) |
| Attachment schema via Liquibase with verification | COMPLETED (reused) |
| Bootstrap call to the domain services (token-protected, fail-closed) | COMPLETED (reused; the body gains `mode`) |
| Atomic claim of provisioning | COMPLETED (concept reused as the job lease) |
| No destructive compensation | COMPLETED (kept) |

## 4. Reusable components

- `DomainDatabaseProvisioningService` and `AttachmentDatabaseProvisioningService` become step executors.
- `LiquibaseAttachmentDbSchemaManager`.
- `HttpDomainTenantBootstrapClient`, with the `mode` and a per-step timeout.
- `PlatformProvisioningStatusService`, with new fields.
- The audit actions `PROVISION_*`.
- `OrganizationGroupProvisioner` is **not reused** (CD-06); the realm-aware identity sync of PHASE_03 replaces it.

## 5. Work requiring modification or redesign

| Item | Change |
|---|---|
| In-process executor | **Replaced** by `ProvisioningWorker`: polls `tbl_provisioning_job` (QUEUED/RETRYING with `next_attempt_at ≤ now`), claims with a lease (`locked_by`, `locked_until`, optimistic `version`) under a Redis ShedLock, renews the lease while running |
| Plan of work | From the config list to `ProvisioningPlanner.plan(company, jobType)` → ordered steps from `tbl_module` (PHASE_04) plus entitlements (PHASE_06) plus the platform core |
| Failure handling | From "stays PROVISIONING" to step retries with backoff (30 s × 2^k, max 1 h, 8 attempts), then the job FAILED and the company `PROVISIONING_FAILED`; resume re-runs non-completed steps |
| Admin connection | From the shared `DB_*` login to a dedicated **environment-level provisioning account** (`TENANT_PROVISIONING_DB_USER/PASSWORD`; `CREATE DATABASE` on `cmp\_%` only), used only by the worker. It is infrastructure configuration, the same for all companies (CD-03). The runtime account keeps no admin rights. |
| Routing registry | `tbl_organization_database` → `tbl_company_resource` (PHASE_02) |
| Organization status transitions | The company lifecycle (PHASE_02/03) |

## 6. New development required

### 6.1 Step catalog

| Step code | Action (idempotent) | Verification |
|---|---|---|
| `RESERVE_NAMES` | Insert RESERVED rows for every planned resource (DB names and bucket names per purchased module, realm name) | Unique constraints |
| `CREATE_REALM` | Create realm `co-<n>` (idempotent: an existing realm with the same name and registry row is verified, not recreated); registry row `tbl_identity_realm` PROVISIONING | Admin API read-back |
| `APPLY_REALM_TEMPLATE` | Apply the versioned template: security policies, token lifetimes, audience and hard-coded `tenant_id` mappers, the built-in `roles` scope, platform SMTP (CD-19), theme | Configuration check |
| `CREATE_STANDARD_ROLES` | Create the predefined **realm roles**: `COMPANY_SUPER_ADMIN`, `COMPANY_ADMIN` and the module roles of every purchased module (CD-14) | Role list read-back |
| `CONFIGURE_CLIENTS` | `erp-web` (PKCE, Full Scope Allowed on), optional `erp-portal`, `erp-identity-admin` (service account, user management of this realm only, signed JWT) | Client read-back |
| `REGISTER_REALM` | `tbl_identity_realm` ACTIVE with issuer and JWKS; `tbl_company_resource` IDP_REALM VERIFIED; publish the realm-registry invalidation | Registry row status |
| `CREATE_DATABASE:<module>` | `CREATE DATABASE IF NOT EXISTS` with a validated name | `information_schema.SCHEMATA` |
| `MIGRATE_SCHEMA:<module>` | Bootstrap call (mode SEEDED or EMPTY_FOR_IMPORT) → Liquibase + validate (+ seed) | Bootstrap response, changeset count |
| `MIGRATE_ATTACHMENT_SCHEMA` | Existing attachment Liquibase | Existing verification |
| `CREATE_BUCKET:<module>` | For each purchased module: `makeBucket` if missing; set the bucket policy (private); set a quota if supported (PHASE_09) | `bucketExists` |
| `REGISTER_RESOURCES` | Mark the resource rows VERIFIED with host and port | Row status |
| `INVITE_COMPANY_SUPER_ADMIN` | Outbox task `CREATE_REALM_ADMIN_USER`: create the user **in the company realm**, assign the realm role `COMPANY_SUPER_ADMIN` there (CD-11), link `tbl_identity` (realm + `sub`), send the invitation from the company realm (PHASE_10 membership). Never in the platform realm. | Outbox task DONE (asynchronous; does not block ACTIVE, Q: see §11) |
| `PUBLISH_CACHES` | Entitlement publisher (PHASE_06) | Redis keys present |
| `ACTIVATE` | Company → ACTIVE (onboarding_type NEW) or → `AWAITING_DATA_IMPORT` (onboarding_type IMPORT_EXISTING_DATA: the company becomes ACTIVE only after the verified data import and the owner go-live decision, PHASE_01 §6.8); entitlements PENDING → ACTIVE; job COMPLETED; audit | Status |

`ADD_MODULE` uses the subset: `RESERVE_NAMES`, `CREATE_DATABASE`, `MIGRATE_SCHEMA`, `CREATE_BUCKET`, `CREATE_STANDARD_ROLES` (only the new module's predefined realm roles, CD-14), `REGISTER_RESOURCES`, `PUBLISH_CACHES`.

**Removing a module** (subscription change) only revokes the entitlement. The database and bucket are kept (retention); no step drops them.

### 6.2 Worker and API
- `ProvisioningWorker` (`@Scheduled` every 5 s, ShedLock `provisioning-worker`): claims one job at a time per instance. There is one active job per company (the `active_guard` unique column).
- `ProvisioningJobService`: `enqueue(company, type, mode)`, `retry(jobId)` (FAILED → RETRYING, keeping the completed steps), `cancel(jobId)` (only QUEUED or RETRYING; resources stay as they are), `status(company)`.
- **Stuck detection:** a lease expired for more than 30 minutes without step progress raises an alert metric `provisioning_stuck_jobs` and an audit event. The next worker takes the job over, and idempotent steps make that safe.
- **Preconditions to enqueue `CREATE_COMPANY`:** the company is DRAFT or PROVISIONING_FAILED; the order is **FULFILLED** (paid and activated — CD-07, CD-72, CD-82 (1)) and the subscription ACTIVE; at least one business entitlement exists; the Company Super Admin email is set. **Exception (PO-1, CD-82 (5)):** for the internal Product Owner Company only, the order / subscription conditions are replaced by its audited Super Admin manual grants (CD-36 PO-1 (a)); every other condition applies; no other company and no automated path is covered.

### 6.3 Resource reconciliation report
`ResourceReconciliationJob` (daily, read-only):
1. Compares `SHOW DATABASES LIKE 'cmp\_%'`, the MinIO buckets `cmp-%` and the Keycloak realms with `tbl_company_resource` and `tbl_identity_realm`.
2. Reports `UNREGISTERED`, `MISSING` and `ORPHANED` items to the platform audit and the metrics.
3. **Never deletes anything.**

### 6.4 Schema and realm-template upgrades
- Rotation of the **environment** DB credentials is an owner or operator procedure (runbook), not a per-company job (CD-03). After the rotation the services are restarted or their pools refreshed.
- `REALM_TEMPLATE_UPGRADE` job: applies a new realm-template version to company realms in batches; the result is recorded per realm; a failure marks the realm for review without affecting other companies.
- `SCHEMA_UPGRADE` job (per module, across companies):
  - in batches of N companies;
  - the bootstrap mode UPGRADE;
  - the per-company result goes to `tbl_provisioning_step`;
  - a failure leaves that company's module marked `DEGRADED` (entitlement status) with an alert; other companies continue.

## 7. Exact services, files and database areas involved

| Area | Files |
|---|---|
| Control plane | `provisioning/*` (new `ProvisioningWorker`, `ProvisioningPlanner`, `ProvisioningJobService`, step executors, `RealmProvisioner`, `BucketProvisioner`, `ResourceReconciliationJob`), `provisioning/service/PlatformProvisioningService.java` (refactored or removed), `provisioning/config/ProvisioningAsyncConfig.java` (removed), `provisioning/controller/PlatformProvisioningController.java` → `/api/v1/platform/admin/companies/{code}/provisioning/**` |
| COMMON | `tenant/schema/*` bootstrap `mode` (PHASE_02) |
| Configuration | `CONTROL-PANEL-SERVICE/src/main/resources/application.yml`: remove `tenant.provisioning.modules`, add the worker, backoff and provisioning account settings (values in `.env`, names only) |
| MySQL | New databases per company and purchased module (no per-company users); the operator creates the environment provisioning account once (runbook in PHASE_12) |
| Keycloak | Company realms from the template (PHASE_03) |
| MinIO | Company module buckets (PHASE_09 completes the policy and quota) |

## 8. Proposed schema changes

- `tbl_provisioning_job`: add `company_id` (FK), `job_type`, `mode` (SEEDED/EMPTY_FOR_IMPORT/UPGRADE; full definition in PHASE_01 §6.2 B), `attempt`, `next_attempt_at`, `locked_by`, `locked_until`, `active_guard`, `requested_by`, `version`; index (`status`, `next_attempt_at`).
- `tbl_provisioning_step`: add `step_code`, `resource_type`, `module_id`, `resource_name`, `attempts`, `started_at`, `last_error`; unique (`job_id`, `sequence_no`).
- `tbl_company_entitlement.status`: add `DEGRADED`.

Error text is truncated to 1000 characters and scrubbed of credentials (a regex for `password=`, `IDENTIFIED BY`, tokens).

## 9. API and integration requirements

| Endpoint | Permission |
|---|---|
| `POST /api/v1/platform/admin/companies/{code}/provisioning` (the mode is derived from `onboarding_type`; it is never a free client choice) | `platform.company.provision` |
| `GET …/provisioning` (jobs, steps, resources) | `platform.company.read` |
| `POST …/provisioning/jobs/{id}/retry`, `…/cancel` | `platform.company.provision` |
| `POST …/provisioning/repair` | Super Admin |
| `POST /api/v1/platform/admin/modules/{code}/schema-upgrade` | Super Admin |
| `GET /api/v1/platform/admin/resources/reconciliation` | `platform.company.read` |

Integrations:
- MySQL via the provisioning account;
- the domain services' bootstrap (internal);
- MinIO admin and client;
- Keycloak: the Realm Provisioning Service through the dedicated service account `erp-realm-provisioner` (CD-17), and realm-aware identity sync through `erp-identity-admin` of each company realm;
- Redis via the publisher.

## 10. Dependencies on other phases

| Phase | Relationship |
|---|---|
| PHASE_02 | Prerequisite: registry, realm registry, migrator, naming |
| PHASE_06 | Prerequisite: entitlements trigger `ADD_MODULE`; the publisher |
| PHASE_07 | Prerequisite: the FULFILLED order and the ACTIVE subscription (CD-82 (1)) |
| PHASE_09 | Completes the bucket policy, quota and usage |
| PHASE_10 | Completes the Company Super Admin membership and invitation |
| PHASE_03 | Realm template, realm-aware identity sync, tenant invalidation |

## 11. Security and tenant-isolation requirements

1. The provisioning account is used only by the worker and is never available to the domain services. The runtime account has no MySQL admin rights (CD-03).
2. Isolation between company databases is enforced by registry-only routing (PHASE_01 §6.1.4): a test proves that a request of company A can never be routed to `cmp_102_*`, whatever the input.
3. Every SQL identifier is validated by regex and built from `company_code` only. **No company name or user input reaches SQL.**
4. Passwords are never logged and never stored in step errors (the scrubber).
5. **Activation policy:** the company becomes ACTIVE when every *resource* step is verified. The Company Super Admin invitation is asynchronous: an email failure must not block activation, but it is visible in the status and retried by the outbox.
6. **No step drops or truncates anything.** A static test rejects `DROP`/`TRUNCATE`/`DELETE FROM` in provisioning code.

## 12. Ordered implementation tasks

| # | Task | Status |
|---|---|---|
| 1 | Job and step schema changes | PENDING |
| 2 | `ProvisioningPlanner` from the catalog + entitlements + platform core | PENDING |
| 3 | `ProvisioningWorker` (lease, ShedLock, backoff, resume, stuck detection); remove the in-process executor | PENDING |
| 4 | Wrap the existing DB, attachment and bootstrap steps as executors; registry integration | PARTIALLY_COMPLETED (step logic exists) |
| 5 | Realm Provisioning Service (`CREATE_REALM`, `APPLY_REALM_TEMPLATE`, `CREATE_STANDARD_ROLES`, `CONFIGURE_CLIENTS`, `REGISTER_REALM`, `ENABLE_REALM`, `DISABLE_REALM`, `RECONCILE_REALM`) with `erp-realm-provisioner` | PENDING |
| 6 | `BucketProvisioner` (one bucket per purchased module; create and register; policy and quota in PHASE_09) | PENDING |
| 7 | `ADD_MODULE`, `REPAIR`, `SCHEMA_UPGRADE`, `REALM_TEMPLATE_UPGRADE` job types | PENDING |
| 8 | Provisioning APIs under `/api/v1/platform/admin/companies/{code}/provisioning` | PARTIALLY_COMPLETED (organization APIs exist) |
| 9 | `ResourceReconciliationJob` | PENDING |
| 10 | The environment provisioning account runbook (operator) | PENDING |

## 13. Acceptance criteria

1. Provisioning a company with entitlements {asset} creates exactly `cmp_<n>_asset_db`, `cmp_<n>_attachment_db`, `cmp_<n>_notification_db`, the buckets `cmp-<n>-asset-bucket` and `cmp-<n>-control-panel-bucket` (§1.4, PHASE_01 §6.1.3; D8-1, CD-82 (2)) and the realm `co-<n>`. **No** database or bucket of a non-purchased module and **no** MySQL user is created. Every resource is VERIFIED and the company is ACTIVE.
2. Killing the control plane in the middle of a job and restarting it completes the job without duplicate resources or errors.
3. An unreachable domain service makes its step retry with backoff. After the retries are exhausted the company is `PROVISIONING_FAILED`; retry resumes from the failed step; **no resource is dropped**.
4. Buying CRM later creates `cmp_<n>_crm_db` and `cmp-<n>-crm-bucket` (and its dependencies) through `ADD_MODULE` while the company stays ACTIVE.
5. The Company Super Admin exists only in realm `co-<n>`; their token is rejected for every other company (IT).
6. The reconciliation report lists a manually created `cmp_999_x_db` as UNREGISTERED and does not delete it.

V1 (CD-82 (9)): acceptance 1 and 5 are V1 requirements; 2, 3 (backoff / automatic retry part), 4 and 6 are deferred explicitly — implementation plan §9.1 (destinations UNASSIGNED; no criterion waived).

## 14. Testing and validation checklist

- [ ] Unit: planner (entitlements → steps), backoff, lease logic, scrubber, identifier validation.
- [ ] MySQL IT (local test MySQL 192.168.5.141, safe runner, least-privilege test account; CD-82 (6)): full `CREATE_COMPANY`, crash or resume (simulated), routing isolation, `ADD_MODULE`.
- [ ] Keycloak IT (local test Keycloak): `CREATE_REALM` idempotency, template check, admin user creation.
- [ ] MinIO IT (environment decided in the P08-T6 readiness): bucket creation idempotency.
- [ ] Contract test with a domain service's bootstrap (SEEDED/EMPTY_FOR_IMPORT/UPGRADE).
- [ ] Concurrency: two workers and the same job claim exactly once.
- [ ] Regression: the existing provisioning tests, adapted.

## 15. Risks and rollback considerations

| Risk | Mitigation |
|---|---|
| Provisioning-account privilege errors in managed MySQL | Validate the provisioning account privileges at worker start-up; clear error code |
| Realm creation failure or a slow Keycloak at scale | Idempotent `CREATE_REALM`, retries, capacity test at 50 companies (CD-18) |
| Partial resources after failures | Idempotent steps, registry, reconciliation; no deletion |
| Connection exhaustion with many company pools | Pool sizing and idle eviction (master §19.5) |
| Bucket and DB naming collision with legacy development resources | `RESERVE_NAMES` first; the reconciliation report |

**Rollback:** the worker can be paused (`provisioning.worker.enabled=false`). Jobs stay queued. Resources created so far remain and are reported. Re-enabling resumes.

## 16. Current status

| Item | Status |
|---|---|
| DB creation, attachment schema, bootstrap call, status API | COMPLETED (reused) |
| Durable worker, retries, resume | PENDING |
| Entitlement-driven plan | PENDING |
| Per-company DB accounts and credentials | **Withdrawn** (CD-03) |
| Realm creation at provisioning | PENDING |
| Bucket per purchased module at provisioning | PENDING |
| Additional job types | PENDING |
| **Phase** | **PARTIALLY_COMPLETED** |
