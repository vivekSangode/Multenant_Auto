# PHASE 08 — Implementation Plan (Company Provisioning)

| Item | Value |
|---|---|
| Phase document | [PHASE_08_COMPANY_PROVISIONING.md](../PHASE_08_COMPANY_PROVISIONING.md) (objective, acceptance §13). This plan **adds no decision**. |
| Design sources | PHASE_01 §0 (CD-01, CD-03, CD-05, CD-06, CD-07, CD-14, CD-16, CD-17, CD-20, CD-23, CD-24 Q-40, CD-26, CD-27), §6.1.3, §6.2 B, §6.5; developer guide §2 (P1–P13), §13, §18, §20 |
| Status | **DRAFT FOR OWNER REVIEW.** PHASE_08 is LOCKED until PHASE_02, PHASE_03, PHASE_06 and PHASE_07 are DONE. Readiness decisions recorded 2026-10-16 (CD-82); the plan itself is **not yet approved**. |
| Author / date | agent (Claude Opus 5.5), 2026-10-03 |
| Evidence | Local code inspection: CP `PlatformProvisioningService` (atomic claim, 202), `ProvisioningAsyncConfig` (in-process executor 2/4/50), `TenantProvisioningService` (all 16 YAML modules, `ensureOrganizationGroup`), `DomainDatabaseProvisioningService`, `AttachmentDatabaseProvisioningService` + `LiquibaseAttachmentDbSchemaManager`, `DomainTenantBootstrapService` + `HttpDomainTenantBootstrapClient` (Eureka, 5 s / 300 s), `ConfigAttachmentDbAdminConnectionProvider`, `PlatformProvisioningStatusService`; S0-5 baseline: `AttachmentProvisioningMySqlIT` (17) passes once the CP Testcontainers configuration (E-1) is set. |
| CP schema | CD-27: entity changes only, `ddl-auto: update`, no CP changelog. |
| Protected hosts | Provisioning in tests runs only against the local test MySQL 192.168.5.141 (safe runner, least-privilege test account, exact-name cleanup) and the local Keycloak 25.0.6 at 127.0.0.1:8081 (CD-30; CD-82 (6)); the MinIO IT environment is decided in the T6 readiness. No step ever targets `192.168.5.52/53` or the shared Keycloak 192.168.5.15; the legacy production DBs are never touched (CD-05). |

---

## 1. Summary

A durable, entitlement-driven, idempotent, **non-destructive** provisioning engine:

1. `ProvisioningPlanner` (catalog + entitlements + platform core) → ordered steps P1–P13 of the developer guide §2.
2. `ProvisioningWorker` (DB job table, lease, ShedLock, backoff 30 s × 2^k up to 1 h / 8 attempts, resume, stuck detection) replacing the in-process executor.
3. Step executors: names reservation, realm steps (Realm Provisioning Service with `erp-realm-provisioner`), databases + schema migration (existing services wrapped), buckets, registration, Company Super Admin invitation (outbox), cache publication, activation.
4. Job types `CREATE_COMPANY`, `ADD_MODULE`, `REPAIR`, `SCHEMA_UPGRADE`, `REALM_TEMPLATE_UPGRADE`.
5. Provisioning APIs, the read-only `ResourceReconciliationJob`, and the operator runbook for the environment provisioning account.

Size estimate: 10 tasks, about 70–90 files, about 30 test classes (local test MySQL + local Keycloak 25.0.6 + MinIO per the T6 readiness; CD-82 (6)).

---

## 2. Preconditions

| # | Precondition | Tracker |
|---|---|---|
| 1 | PHASE_02 (registry, migrator, naming), PHASE_03 (realm template, realm-aware identity sync, invalidation), PHASE_06 (entitlements, publisher), PHASE_07 (FULFILLED order — paid and activated, CD-82 (1)) DONE | phases |
| 2 | S0-2 item 1 decided (registry uniqueness: attachment / notification DB and control-panel bucket rows against PLATFORM_CORE catalog rows) — determines the `RESERVE_NAMES` rows | S0-2 |
| 3 | S0-2 item 6 decided (key management for the `erp-identity-admin` signed-JWT key pair) — needed by `CONFIGURE_CLIENTS` outside tests. **DECIDED 2026-10-16 (CD-82 (8)).** Gating (CD-82 (7), reading A′): P08-T5 may start after this item is decided; P08-T5 is not set DONE, and no `CONFIGURE_CLIENTS` runs outside tests, before the D3-10 closure (A1–A6 + explicit owner approval, PHASE_12 plan §8.14); D3-10 is not an unlock gate | S0-2 |
| 4 | Operator creates the **environment provisioning account** (`CREATE` on `cmp\_%` only) and the `erp-realm-provisioner` client in `master` on the development environment; values in `.env` (names only) | owner action (T10 runbook); timing (CD-82 (6)): before the P08-T4 / P08-T5 readiness, not before the phase unlock |
| 5 | D8-2 answered (where the Company Super Admin email comes from) | new (S0-17) |
| 6 | PO-1 (CD-82 (5)): the internal Product Owner Company is provisioned under its documented exception (its audited Super Admin manual grants replace the order / subscription conditions, for that company only); the T2 / T4 readiness verifies the no-order path | CD-36, CD-38, CD-82 |

---

## 3. Owner decisions / clarifications

> **Decisions recorded 2026-10-03 (owner, CD-28):** D8-2 (S0-17 DONE): the Company Super Admin invitation is generated only by the controlled provisioning workflow (step INVITE_COMPANY_SUPER_ADMIN, invitee captured in the audited provisioning request), recorded in the audit log; no SMTP credential or secret is exposed. S0-2 item 1 approved: RESERVE_NAMES registers PLATFORM_CORE resources against their PLATFORM_CORE module rows.

> **Decisions recorded 2026-10-16 (owner, CD-82):** D6 = FULFILLED (CD-82 (1)); D8-1 approved (2); D8-3 approved with "existing data must never be overwritten" (3); BD-7 = in-process executor + manual recovery for the first Company, durable worker before a second Company (4); PO-1 scope (5); BD-12 / BD-13 development / IT scope (6); C6 = reading A′ (7); S0-2 item 6 DECIDED (8); D-9 deferral record (9, §9.1). The plan stays DRAFT FOR OWNER REVIEW.

| ID | Question | Options | Recommendation |
|---|---|---|---|
| **D8-1** — **decided 2026-10-16 (CD-82 (2)): approved** | PHASE_08 acceptance 1 omits the platform-core bucket `cmp-<n>-control-panel-bucket`, while PHASE_08 §1.4, PHASE_01 §6.1.3 and the developer guide §21 create it for every company. | Follow §1.4 / PHASE_01: acceptance 1 also expects `cmp-<n>-control-panel-bucket`. | Text clarification of acceptance 1 (follows PHASE_01 §6.1.3); the plan tests it. |
| **D8-2** | The `CREATE_COMPANY` precondition says "the Company Super Admin email is set", but no PHASE_01 §6.2 column names it (`tbl_company_profile.primary_contact_email` is a contact, `tbl_billing_account` has a billing email). | (a) Use `tbl_company_profile.primary_contact_email` as the Company Super Admin invitation address (shown and confirmed in the "Start provisioning" dialog). (b) An explicit field in the provisioning request (stored on the job, audited). | **(b)**: the invitee is an explicit, audited choice of the platform user at "Start provisioning"; no schema change beyond the job row's request payload. |
| **D8-3** — **decided 2026-10-16 (CD-82 (3)): approved; existing data must never be overwritten** | The step catalog has no explicit REPAIR step list. | REPAIR = verify every planned resource; recreate **missing empty** resources only (master §7.4, guide §18) using the same executors in "repair" mode; never overwrite data. | As described. |

---

## 4. Task order

```text
T1 job/step entities ─> T2 planner ─> T4 wrap DB / attachment / bootstrap executors + registry ─> T6 BucketProvisioner ─> T5 realm executors ─> T3 worker (replaces executor) ─> T8 APIs ─> T7 other job types ─> T9 reconciliation
T10 runbook (operator) in parallel; needed before T4 runs on a shared dev environment
```

T3 is wired after the executors exist so the worker can be tested end-to-end at once; until then the existing in-process flow keeps working on the Company model (PHASE_02).

> **V1 scope (CD-37; CD-82 (4), (9)):** T1 / T3 HOLD (in-process executor + manual recovery for the first Company; the durable worker is required before a second Company); T7 / T8 / T9 HOLD; T2 / T4 / T5 / T6 / T10 are the CD-37 exception tasks, each with its own readiness and explicit authorization. Deferred acceptance: §9.1.

---

## 5. Task details

### T1 — Job and step entities (P08-T1)

| Item | Detail |
|---|---|
| Entities | `ProvisioningJob` (+ `company_id` FK, `job_type`, `mode`, `attempt`, `next_attempt_at`, `locked_by`, `locked_until`, `active_guard` (1 while not terminal, NULL when terminal), `requested_by`, `request_payload_json` (invitee per D8-2, no secrets), `last_error`, `completed_at`, `version`), `ProvisioningStep` (+ `step_code`, `resource_type`, `module_id`, `resource_name`, `attempts`, `started_at`, `last_error`); `CompanyEntitlement.status` + `DEGRADED`. PHASE_01 §6.2 B. |
| Tests | Schema IT: UQ (`company_id`, `active_guard`) allows one active job per company and any number of terminal ones. |

### T2 — `ProvisioningPlanner` (P08-T2)

| Item | Detail |
|---|---|
| Files | `provisioning/service/ProvisioningPlanner`, `provisioning/model/{PlannedStep, StepCode}` (CP package standard, `AGENTS.md`) |
| Rules | `CREATE_COMPANY`: `RESERVE_NAMES` → realm steps (`CREATE_REALM`, `APPLY_REALM_TEMPLATE`, `CREATE_STANDARD_ROLES` for company-admin roles + every entitled business module, `CONFIGURE_CLIENTS`, `REGISTER_REALM`) → per entitled module with `requires_database`: `CREATE_DATABASE:<m>`, `MIGRATE_SCHEMA:<m>` (mode SEEDED, or EMPTY_FOR_IMPORT when `onboarding_type=IMPORT_EXISTING_DATA`; never a client choice) → platform core: attachment DB + `MIGRATE_ATTACHMENT_SCHEMA`, notification DB + migrate → `CREATE_BUCKET:<m>` per entitled module + `control-panel` → `REGISTER_RESOURCES` → `INVITE_COMPANY_SUPER_ADMIN` → `PUBLISH_CACHES` → `ACTIVATE`. `ADD_MODULE`: the subset of PHASE_08 §6.1 for the new module and its mandatory dependencies. Names only from `TenantResourceNaming` + catalog tokens. |
| Tests | Entitlements {asset} → exactly the resources of acceptance 1 (+ D8-1 bucket); {asset} + later {crm} → ADD_MODULE plan with CRM's dependency (production, if MANDATORY per Q-09). No name derives from a company name (property test). |

### T4 — Wrap existing executors; registry integration (P08-T4)

| Item | Detail |
|---|---|
| Files | `provisioning/step/service/StepExecutor`, `provisioning/step/model/{StepContext, StepResult}`; `CreateDatabaseStep` (wraps `DomainDatabaseProvisioningService`: `CREATE DATABASE IF NOT EXISTS`, regex, `information_schema` verify), `MigrateSchemaStep` (wraps `HttpDomainTenantBootstrapClient` with the `mode` body and a per-step timeout; stores `schema_version`), `MigrateAttachmentSchemaStep` (wraps `LiquibaseAttachmentDbSchemaManager`), `ReserveNamesStep`, `RegisterResourcesStep`, `PublishCachesStep`, `ActivateStep` (ACTIVE or AWAITING_DATA_IMPORT; platform email "provisioning completed" via P07-T11). Admin connection: `ProvisioningDbAdminConnectionProvider` with the environment provisioning account (`TENANT_PROVISIONING_DB_USER` / `_PASSWORD`), validated at start-up (privilege check). `ProvisioningErrorScrubber` (`password=`, `IDENTIFIED BY`, bearer tokens; 1000 chars). |
| Adoption rule | A database/bucket/realm that already exists **with** a RESERVED registry row of this company is verified and adopted; **without** a reservation → step FAILED + reconciliation item (guide §18). |
| Tests | Each executor idempotent (second run = SKIPPED); adoption rule both ways; static test: no `DROP` / `TRUNCATE` / `DELETE FROM` in `nyaalkaran.erp.system.provisioning..` (PHASE_08 §11.6). |

### T6 — `BucketProvisioner` (P08-T6)

| Item | Detail |
|---|---|
| Files | `provisioning/step/service/CreateBucketStep` + `provisioning/service/BucketProvisioner` (environment MinIO keys; `bucketExists` → skip; `makeBucket`; private policy; quota hook left to PHASE_09) |
| Tests | MinIO (local MinIO or mock — decided in the T6 readiness, CD-82 (6)): idempotent creation; bucket names validated (3–63, `cmp-<n>-…-bucket`). |

### T5 — Realm Provisioning Service executors (P08-T5)

| Item | Detail |
|---|---|
| Files | `provisioning/realm/service/{RealmProvisioningService, CreateRealmStep, ApplyRealmTemplateStep, CreateStandardRolesStep, ConfigureClientsStep, RegisterRealmStep, EnableRealmStep, DisableRealmStep, ReconcileRealmStep}` on the PHASE_03 `RealmTemplateApplier` and realm-parameterized admin client; `erp-realm-provisioner` (master, `create-realm`) only for `CREATE_REALM`; realm created **disabled** until `REGISTER_RESOURCES`/`REGISTER_REALM`; every Keycloak admin call audited with the platform actor and the correlation id. |
| Tests | Local Keycloak 25.0.6 at 127.0.0.1:8081 (CD-30, CD-82 (6); never 192.168.5.15): `CREATE_REALM` idempotent (existing realm + registry row → verify, not recreate; existing realm without row → FAILED); roles of purchased modules only; no `PLATFORM_*` role; configuration check passes. |
| Gate | CD-82 (7), reading A′: starts only after its readiness prerequisites incl. S0-2 item 6 (DECIDED, CD-82 (8)); not set DONE before the D3-10 closure (A1–A6 evidence + explicit owner approval); the A1–A6 work (key loading from the secret mount, JWKS endpoint + gateway deny test, `client-jwt` on the local Keycloak, rollover test incl. the key-cache measurement, template fixes with a version bump, negative test) belongs to this task; `CONFIGURE_CLIENTS` outside tests only after the closure. |

### T3 — `ProvisioningWorker` (P08-T3)

| Item | Detail |
|---|---|
| Files | `provisioning/worker/service/{ProvisioningWorker, JobLeaseService, BackoffPolicy, StuckJobDetector}`; `ProvisioningJobService` (`enqueue`, `retry`, `cancel`, `status`); remove `ProvisioningAsyncConfig` and the in-process path of `PlatformProvisioningService` (owner approval). |
| Rules | `@Scheduled` every 5 s + ShedLock `provisioning-worker`; claim QUEUED/RETRYING with `next_attempt_at ≤ now` using `locked_by`/`locked_until` + optimistic `version`; lease renewal per step; step retry with backoff; after 8 attempts job FAILED + company PROVISIONING_FAILED; `retry` keeps COMPLETED steps; `cancel` only QUEUED/RETRYING; stuck detection after 30 min without progress → metric `provisioning_stuck_jobs` + audit. Pausable by `provisioning.worker.enabled=false`. A repeated "Start provisioning" returns the active job (`active_guard`). |
| Tests | Two workers claim a job exactly once; simulated crash mid-job then restart completes without duplicates (acceptance 2); unreachable domain service → backoff → FAILED → retry resumes at the failed step (acceptance 3). |

### T8 — Provisioning APIs (P08-T8)

| Item | Detail |
|---|---|
| Files | `provisioning/controller/PlatformProvisioningController` (exists) under `/api/v1/platform/admin/companies/{code}/provisioning` (start with the invitee per D8-2: `platform.company.provision`; read: `platform.company.read`; retry/cancel; repair: Super Admin), `/api/v1/platform/admin/modules/{code}/schema-upgrade` (Super Admin), `/api/v1/platform/admin/resources/reconciliation` (`platform.company.read`). Enqueue preconditions: company DRAFT or PROVISIONING_FAILED, order PAID / subscription ACTIVE, ≥ 1 business entitlement, invitee present, no active job. Audit `PROVISION_REQUESTED` (actor, company, order, job, correlation id, IP). The old organization provisioning endpoints are removed. |
| Tests | Permission matrix; precondition failures 409; duplicate start returns the same job. |

### T7 — `ADD_MODULE`, `REPAIR`, `SCHEMA_UPGRADE`, `REALM_TEMPLATE_UPGRADE` (P08-T7)

| Item | Detail |
|---|---|
| Work | `ADD_MODULE` enqueued by the PHASE_06 entitlement engine (company stays ACTIVE); `REPAIR` per D8-3; `SCHEMA_UPGRADE` per module across companies in batches (mode UPGRADE; per-company step rows; failure → that company's entitlement DEGRADED + alert; others continue); `REALM_TEMPLATE_UPGRADE` per realm in batches. |
| Tests | Buying CRM later → `cmp_<n>_crm_db` + `cmp-<n>-crm-bucket` (+ dependencies) while ACTIVE (acceptance 4); a failing company in a schema-upgrade batch does not stop the others. |

### T9 — `ResourceReconciliationJob` (P08-T9)

| Item | Detail |
|---|---|
| Files | `provisioning/reconcile/service/ResourceReconciliationJob` (daily, read-only): `SHOW DATABASES LIKE 'cmp\_%'`, MinIO buckets `cmp-%`, Keycloak realms `co-%` vs `tbl_company_resource` / `tbl_identity_realm` → UNREGISTERED / MISSING / ORPHANED to audit + metrics; **never deletes**. LEGACY development `org_*` / `org-*` resources are reported in a separate LEGACY section and never adopted (CD-26). |
| Tests | A manually created `cmp_999_x_db` is reported UNREGISTERED and still exists afterwards (acceptance 6). |

### T10 — Environment provisioning account runbook (P08-T10)

| Item | Detail |
|---|---|
| File | `saas-plan-doc` runbook section (or `docs/runbooks/` per PHASE_12 T7): create the MySQL account with `CREATE` on `cmp\_%` only (no admin rights, no other schemas), store the secret in the secret store, rotate (CD-03); create `erp-realm-provisioner` in `master` with `create-realm` only (CD-17); MinIO keys scope. Operator-executed; agents never run it on shared environments. |

---

## 6. Test plan summary

| Level | Coverage | Acceptance (PHASE_08 §13) |
|---|---|---|
| Unit | planner, backoff, lease, scrubber, identifier validation, adoption rule | 1, 3 |
| MySQL IT | full CREATE_COMPANY, crash/resume, routing isolation, ADD_MODULE, reconciliation | 1, 2, 3, 4, 6 |
| Keycloak IT (local Keycloak 25.0.6, CD-30 / CD-82 (6)) | realm steps, admin user creation in `co-<n>` only | 1, 5 |
| MinIO IT (environment per the T6 readiness) | bucket idempotency | 1, 4 |
| Contract | bootstrap SEEDED / EMPTY_FOR_IMPORT / UPGRADE with one domain service | — |
| Concurrency | two workers, one claim | 2 |
| Static | no DROP/TRUNCATE/DELETE in provisioning code | — |

---

## 7. Risks

| Risk | Mitigation |
|---|---|
| Provisioning-account privileges differ per environment | Start-up privilege check, clear error |
| Keycloak slow at scale | Idempotent realm steps, retries, capacity test at 50 companies (PHASE_12) |
| Partial resources after failures | Reservation first, adoption rule, reconciliation, no deletion |
| Collision with LEGACY development `org_*` resources | Different prefix (`cmp_`); reconciliation LEGACY section |
| Pool exhaustion | Pool sizing and eviction (PHASE_03) |

---

## 8. Documentation findings

| # | Finding | Handling |
|---|---|---|
| F-1 | Acceptance 1 omits `cmp-<n>-control-panel-bucket` | D8-1 decided (CD-82 (2); follows PHASE_01 §6.1.3) |
| F-2 | Source of the Company Super Admin email undefined | D8-2 (S0-17) |
| F-3 | REPAIR step list implicit | D8-3 decided (CD-82 (3)) |

---

## 9. Definition of done (PHASE_08)

All PHASE_08 §13 acceptance criteria proven (acceptance 1 including the control-panel bucket per D8-1); every task row REVIEW with evidence; owner sets DONE.

### 9.1 V1 deferral record (D-9, option (ii), CD-82 (9))

Acceptance 1 and 5 stay V1 requirements. The items below cannot (or only partly) be demonstrated while the listed tasks are HOLD; they are deferred **explicitly** — no criterion is waived. Only the owner assigns a destination; until then it is **UNASSIGNED — OWNER DECISION REQUIRED** and PHASE_08 cannot be closed. The durable worker stays required before a second Company is onboarded (CD-82 (4)), whatever destination D9-1 / D9-2 / D9-5 receive.

| # | Acceptance / task | Demonstrable in V1 | Not demonstrable in V1 | Interim control | Evidence required to close | Destination |
|---|---|---|---|---|---|---|
| D9-1 | Acceptance 2 (crash mid-job → restart completes, no duplicates) | a re-run after a failure creates no duplicates (T4 idempotency, adoption rule) — partial | automatic resume by a worker (T3) | in-process executor + manual recovery, first Company only (CD-82 (4); PHASE_12 plan V1 register, P08-T1 / T3 row) | two-worker / crash-resume IT (T3 tests) | Gate: before a second Company is onboarded (CD-82 (4)); owning phase / task for the PHASE_08 closure: UNASSIGNED — OWNER DECISION REQUIRED |
| D9-2 | Acceptance 3 (backoff → PROVISIONING_FAILED → retry from the failed step, nothing dropped) | PROVISIONING_FAILED with the failed step recorded; no resource dropped (T4 tests + static no-DROP test); whether the existing recover endpoint resumes at the failed step is checked in the T4 readiness | backoff and automatic retry (T3) | manual recovery (as D9-1) | backoff → FAILED → retry-resume IT | Gate: before a second Company is onboarded (CD-82 (4)); owning phase / task for the PHASE_08 closure: UNASSIGNED — OWNER DECISION REQUIRED |
| D9-3 | Acceptance 4 (`ADD_MODULE` while ACTIVE) | — | all (T7 HOLD) | no module change after onboarding in V1 (PHASE_12 plan V1 register) | CRM-later IT (T7 tests) | UNASSIGNED — OWNER DECISION REQUIRED |
| D9-4 | Acceptance 6 (reconciliation reports `cmp_999_x_db` and keeps it) | — | all (T9 HOLD) | manual verification at onboarding (PHASE_12 plan V1 register) | UNREGISTERED report IT, resource kept | UNASSIGNED — OWNER DECISION REQUIRED |
| D9-5 | DoD "every task row REVIEW" — T1, T3 | — | task rows (BD-7) | as D9-1 | task REVIEW + owner DONE | Gate: before a second Company is onboarded (CD-82 (4)); owning phase / task for the PHASE_08 closure: UNASSIGNED — OWNER DECISION REQUIRED |
| D9-6 | DoD — T7 | — | task row (HOLD) | as D9-3 | task REVIEW + owner DONE | UNASSIGNED — OWNER DECISION REQUIRED |
| D9-7 | DoD — T8 | — | task row (HOLD) | existing provision / recover endpoints (PHASE_12 plan V1 register) | task REVIEW + owner DONE | UNASSIGNED — OWNER DECISION REQUIRED |
| D9-8 | DoD — T9 | — | task row (HOLD) | as D9-4 | task REVIEW + owner DONE | UNASSIGNED — OWNER DECISION REQUIRED |
