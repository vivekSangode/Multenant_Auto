# PHASE 12 — Testing, Rollout and First Company Onboarding

| Item | Value |
|---|---|
| Master architecture | [SAAS_ARCHITECTURE_MASTER.md](SAAS_ARCHITECTURE_MASTER.md) §18–§20, §22 |
| Design source | [PHASE_01 §6.8 (Shivam onboarding and import plan)](PHASE_01_SAAS_FOUNDATION.md) |
| Depends on | The test infrastructure may start after PHASE_01 approval, in parallel with PHASE_02. The rollout gates G0–G8 (current release R1) require PHASE_02–PHASE_11. **PHASE_11A is a mandatory readiness gate before staging/production readiness (CD-28 item 10); it runs only after the schema-freeze approval S0-11 and its prerequisites.** [PHASE_14](PHASE_14_ONLINE_PAYMENTS_AND_PUBLIC_WEBSITE_ONBOARDING.md) (website and online payments) has its own later gate G9. |
| Status | **PENDING** (per-module unit/IT tests exist; no cross-service or isolation suite, no backup tooling, no CI, no onboarding executed) |

> **Revision 2026-10-03 (v5, decision register):**
> - The gates and the onboarding now follow **full payment only** (CD-07), a **realm per company** (CD-06; Shivam gets a new standard realm, CD-16), **shared environment DB credentials** (CD-03; isolation tested at the routing layer), a **bucket per company per module** (CD-01) and the **new production database** approach (CD-05).
> - The legacy production data is never modified. The import is a controlled, owner-approved activity.

---

## 1. Objective

1. **Test strategy and suites** across all phases:
   - unit, component IT, contract and cross-service tests;
   - the **tenant isolation** suite (a mandatory gate);
   - performance tests;
   - import rehearsal tests.
2. **Environments:** a local cross-service test profile, a staging environment on a new, non-protected server, and production on a new server.
3. **Operations:**
   - per-company backup and restore;
   - monitoring and alerts for the new components;
   - runbooks (Keycloak platform realm and realm automation, provisioning, environment credential rotation, schema upgrade, lifecycle, purge, Redis outage, restore, per-realm restore).
4. **Rollout gates** G1–G8. Every production action is approved by the owner.
5. **First company onboarding:** Shivam is created through the **standard company flow** and its existing data is imported, executing the plan in PHASE_01 §6.8. This is part of the rollout, not a separate architecture or phase.

## 2. Existing implementation relevant to this phase

| Item | Evidence |
|---|---|
| Unit and IT tests per module (JUnit 5, Testcontainers 1.20.4); `*IT` excluded from the default run; the MySQL ITs refuse the protected hosts | `CODE-INSPECTED` |
| Known failing tests | **Measured in the S0-5 baseline (2026-10-03, [report](plans/S0-5_BASELINE_TEST_REPORT.md)):** ACCOUNT-BOOK 94 unit failures/errors in 33 classes; CONTROL-PANEL `PlatformJwtGrantedAuthoritiesConverterTest.mapsScopesAndRealmAndResourceRoles` (stale) and 4 stale `IdentitySyncMySqlIT` realm-role methods; everything else passes (unit 3,810 / 3,905; IT 311 / 315 with the documented local settings; only the 4 known stale CP `IdentitySyncMySqlIT` methods fail) |
| Observability stack | Prometheus, Grafana, Loki/Promtail, Tempo, OTel Collector (compose) |
| Backup / DR scripts | Claimed by the old documents, **not present** (`DOC-REPORTED` contradiction) |
| CI/CD | None; deployment is manual (README) |
| Shivam onboarding | Not started. The old manual procedure in `_cleanup_review/docs/MANUAL_EXISTING_CLIENT_MIGRATION_PROCEDURE.md` is reference only (partly stale). |

## 3. Existing completed work

| Item | Status |
|---|---|
| Per-module tests | PARTIALLY_COMPLETED |
| Observability stack | COMPLETED (reused; new dashboards are needed) |

## 4. Reusable components

- The Testcontainers patterns.
- The protected-host refusal in the ITs.
- The in-memory identity provider for the identity-sync tests.
- The observability stack.
- The compose file (gets a test profile).
- The PHASE_11 import toolkit.
- The PHASE_09 bucket import tool.

## 5. Work requiring modification or redesign

- Fix the stale tests (PHASE_02 task 0).
- Turn the ACCOUNT-BOOK pre-existing failures into a tracked list. Fix them, or quarantine them with owner approval, before G5.
- The Kubernetes proof of concept is out of scope for the first release (owner decision).

## 6. New development required

### 6.1 Test pyramid

| Level | Scope | Tooling |
|---|---|---|
| Unit | Pricing engine, entitlement engine, guards, naming, planner, verification filter, issuer resolution | JUnit 5; property-based tests for pricing |
| Component IT | Control-plane services with MySQL / Redis / MinIO containers | Testcontainers |
| Contract | Bootstrap (all modes), metadata and realm-registry APIs, usage APIs, permission registration, portal-user API | Contract or schema tests |
| Cross-service | Gateway + control plane + representative modules (ASSET standalone; STORE + ACCOUNT-BOOK; CRM + PRODUCTION + LOGISTICS) on the compose test profile with a local test Keycloak (platform realm + two company realms from the template) | Compose test profile, REST-assured |
| Isolation (gate) | See §6.2 | Dedicated suite, 100 % pass required |
| Performance | **Planning target 50 companies** (CD-18; tested with headroom, e.g. 60): pools for 50 companies × 17 services; Keycloak with 51 realms: login latency, memory, startup, admin operations; gateway overhead of the Redis checks; provisioning throughput | Gatling or k6 locally |
| Import | Toolkit on synthetic dumps; rehearsal on staging | Toolkit IT + rehearsal |

### 6.2 Isolation suite (mandatory)
1. A company A token sent to company B data on every module route → 403.
2. Forged `X-Company-Code` sent directly to every service container → 401/403.
3. No request input (headers, path, body, crafted metadata) can route company A to a company B database; a token from realm A with a `tenant_id` or header of B → 401/403; a token from an unregistered realm → 401.
4. A crafted bucket on a company A attachment (another company's bucket, or another module's bucket) → refused.
5. Platform Admin out of scope → 403. A platform identity on a business route → 403. A company identity on a platform route → 403.
6. Company Admin escalation attempts (a role they do not hold; demoting the last Super Admin; assigning a role in another company's realm; creating a platform role or platform user) → rejected.
6a. **Role ≠ entitlement:** a role of a non-entitled module → 403; an entitled module without a role → 403 (CD-11).
7. A non-entitled module at the gateway **and** directly at the service → 403.
8. A suspended company is blocked within the SLA, and its pools are evicted.
9. Jobs: no cross-company writes (two-company job IT).
10. Redis outage → 503 (fail-closed), with no data access.

### 6.3 Environments

| Environment | Purpose | Rule |
|---|---|---|
| Local developer | Unit + component IT | Testcontainers only |
| Local integration | Cross-service, isolation, performance | Local containers + local test Keycloak (platform realm + test company realms) |
| Staging (new server) | Gate checks, onboarding rehearsal | **Never `192.168.5.52/53`**; owner-provisioned |
| Production (new server) | Go-live | Owner deploys; owner approval for every deployment |

### 6.4 Operations tooling and runbooks
- **Per-company backup:** all `cmp_<n>_%` schemas + the mirror of every company module bucket + the **per-realm export** of the company realm + an export of the related platform rows, with checksums. **Per-company restore** into a fresh company code for verification. **Platform DB backup** daily. Schedule and retention: Q-23.
- **Monitoring and alerts:**
  - provisioning (failed or stuck);
  - outbox dead letters;
  - entitlement and Redis publication;
  - Redis availability;
  - pool counts per service;
  - storage thresholds;
  - subscription transitions;
  - isolation-check denials (security audit events);
  - certificate and token expiry;
  - per-realm login failures, lockouts and drift; realm count.
- **Runbooks:**
  - Keycloak platform realm and company realm template (PHASE_03);
  - realm automation: creation, template upgrade, disable and enable, per-realm export and restore;
  - environment provisioning MySQL account and least-privilege runtime account (CD-03);
  - first Platform Super Admin bootstrap;
  - provisioning failure handling;
  - environment credential rotation (DB, MinIO, M2M secrets);
  - schema upgrade;
  - company lifecycle;
  - manual purge;
  - Redis outage;
  - **gateway runtime-state rollout and recovery (P06-T6; review finding N5):**
    - deploy CONTROL-PANEL-SERVICE **before** API-GATEWAY, and roll back in the reverse order (gateway first);
    - after CONTROL-PANEL starts, the first entitlement resync runs after `MODULE_ENTITLEMENT_SYNC_INITIAL_DELAY_MS` (default 60 s), then every `MODULE_ENTITLEMENT_SYNC_INTERVAL_MS` (default 120 s); until a company's `TENANT_STATUS:<CMP_n>` and `COMPANY_MODULES:<CMP_n>` exist, the new gateway answers **503 `ENTITLEMENT_UNAVAILABLE`** for it — expected and temporary;
    - if publishing stays unavailable for longer than the key TTL `MODULE_ENTITLEMENT_TTL` (default **300 s**) — CONTROL-PANEL down, Redis down or the resync disabled — the keys expire and **every company-scoped route returns 503 (fail closed)** until the next successful resync;
    - before deploying the gateway, and before proceeding after any outage, verify: every company has `TENANT_STATUS:<CMP_n>` with a TTL > 0; every ACTIVE company has `COMPANY_MODULES:<CMP_n>` (or the single member `#NONE`) with a TTL > 0; no company is left in the recovery backlog (CONTROL-PANEL log: no unresolved entitlement recompute / publication failure); a smoke check returns 200 for an entitled module, 403 `MODULE_NOT_ENTITLED` for a non-entitled one and no unexpected 503;
  - single-company restore;
  - **company data import** (§6.6).
- **CI (recommended):** build, unit and component IT on an owner-approved host, never a protected host.

### 6.5 Rollout gates

| Gate | Criteria | Approver |
|---|---|---|
| G0 | PHASE_01 approved (precondition for everything) | Owner |
| G1 Foundation | PHASE_02–03 acceptance; regression ≥ the baseline | Owner |
| G2 Commercial core | PHASE_04–07 acceptance (catalog, plans, subscriptions, admin billing with **full manual payment only** and verification, manual renewal, credit notes); pricing and calculation-order scenario tests; financial immutability tests; **both invoice-timing paths and the tax scenarios (intra/inter-state, rate change, seller change) tested with the SAMPLE configuration** (CD-25) | Owner |
| G2a Billing go-live (production) | The CA-confirmed values (Q-42, Q-29, Q-04 tax) entered and **APPROVED** by the Platform Super Admin with the approval reference; a test quote and invoice on staging reviewed by the CA. Only production quoting waits for this gate (CD-25). | Owner + CA |
| G3 Provisioning and storage | PHASE_08–09 acceptance (realm, databases and buckets per purchased module); isolation tests 3–4 | Owner |
| G4 Administration and security | PHASE_10 acceptance; the isolation suite 100 % | Owner |
| G5 Integration and compatibility | PHASE_11 acceptance; Shivam emails verified on the **interim** global sender (unchanged behaviour); compatibility register C-1..C-9 green on a rehearsal company | Owner |
| G6 Staging readiness | Staging up; runbooks dry-run; backup restore drill (including one realm); performance and capacity targets at 50 companies met (CD-18). **PHASE_11A DONE** (CP schema from the final Liquibase baseline, `ddl-auto: validate`; mandatory, CD-28 item 10). | Owner |
| G7 First company onboarding | §6.6 rehearsal report signed; cutover window agreed | Owner |
| G8 Go-live | Shivam cutover verified; soak plan active | Owner |
| G10 Business email for further companies (future) | PHASE_13 acceptance (user mailbox OAuth only, CD-23). Until then, any further company is onboarded with **business email disabled** (Q-41, decided); platform email works for all companies from R1 | Owner |
| G9 Website and online payments (later release) | PHASE_14 acceptance; gateway LIVE account verified by the owner; webhook, reconciliation and refund drills on TEST; security review of the public endpoints | Owner |

### 6.6 First company onboarding: Shivam (executes PHASE_01 §6.8)

This uses only standard flows: company creation, admin-channel order and payment (PHASE_07), subscription, provisioning with `onboarding_type=IMPORT_EXISTING_DATA`, and the PHASE_11 import toolkit.

| Step | Action | Who | Gate |
|---|---|---|---|
| O-1 | Confirm the remaining decisions Q-02 (modules, users, storage, contract plan versions, attachments), Q-25 (UI client), **Q-42** (tax invoice timing, accountant), Shivam's Company Login Code, and the legacy role → realm role mapping table (CD-14) | Owner | before G7 |
| O-2 | **Rehearsal company** on staging through the standard flow (activity A); **identity-migration rehearsal** (activity B: do the migrated password hashes verify? otherwise the activation flow); data-import rehearsal (activity C); reconciliation; functional checks; compatibility C-1..C-9 | Platform team + Owner | G5/G7 |
| O-3 | Rehearsal report (counts, checksums, FK integrity, objects, identities, smoke flows, timing) signed | Owner | G7 |
| O-4 | **Create Shivam** on production through the standard API: `onboarding_type=IMPORT_EXISTING_DATA`. If it is the first company created on the new platform, it receives `CMP_101` (CD-26; nothing depends on the number, PHASE_01 §6.1.2). | Platform Super Admin | G7 |
| O-5 | Standard **admin-channel** commercial flow (PHASE_07): billing account (GSTIN, state) → order (plans, users, storage; contract plan versions `is_custom` if agreed) → **full** manual payment (cash, cheque, DD, UPI, bank transfer or other approved; cheque or DD only after clearance) in one or several verified instruments totalling 100 % (CD-20), recorded and verified by authorized platform users (no mandatory two-person rule, CD-21; the gateway payment link is FUTURE) → PAID → invoice ISSUED (timing per Q-42) → subscription ACTIVE | Platform Super Admin or authorized Platform Admin | — |
| O-6 | **Activity A**, standard provisioning (mode `EMPTY_FOR_IMPORT`), exactly as for every company: `CREATE_REALM` (new standard `co-<n>`), `APPLY_REALM_TEMPLATE`, `CREATE_STANDARD_ROLES`, `CONFIGURE_CLIENTS`, `REGISTER_REALM`, one database and one bucket per purchased module → status `AWAITING_DATA_IMPORT` | Provisioning worker | — |
| O-6a | Email: (1) platform email for Shivam (receipts, invoices, notices) from the platform mailbox (CD-23); (2) business email (interim): Shivam keeps the existing global sender unchanged until PHASE_13, then switches to the users' own mailboxes; the owner rotates the committed QC SMTP password | Owner + Platform team | before O-10 |
| O-7 | **Backup**: full legacy DB and bucket backups plus a checksum report on the legacy side, and a boot-time `kc.sh export --realm ERPSystem --users realm_file` (users with password hashes) for activity B | **Owner only** (protected hosts) | — |
| O-8 | Freeze window: the legacy system is read-only; final export delivered to the approved location | Owner | — |
| O-9 | **Activity B**, identity migration (`identity-migrate`, PHASE_11): transform → import into Shivam's realm with the mapped realm roles → verify logins (or start the activation flow). **Activity C**, data import (owner-supplied exports only; no change to the legacy data): `diff` → `load` → `integrity` → `reconcile` → `attachments` → `objects` → `link-identities` (applies the `sub` mapping if needed) | Platform team | — |
| O-10 | Verification: 100 % reconciliation (or owner-approved waivers); smoke flows; sample logins per department | Platform team + Owner | — |
| O-11 | Go / no-go. **No-go → rollback:** stay on legacy; the company stays `AWAITING_DATA_IMPORT`; the data is kept for analysis; a retry uses the audited `reset-target` | Owner | G8 |
| O-12 | Go: company → ACTIVE; the UI points to the new gateway (owner); hypercare | Owner + Platform team | G8 |
| O-13 | Soak (e.g. 2 weeks): the legacy system is untouched; daily reconciliation of the key counts; monitoring | Owner + Platform team | — |
| O-14 | Legacy decommission decision (owner; no automatic deletion) | Owner | after soak |

**Protected hosts:** steps O-7 and O-8 are performed by the owner. No project tool connects to `192.168.5.52` or `192.168.5.53` at any step.

## 7. Exact services, files and database areas involved

- Tests in every module (`src/test/**`).
- The compose test profile.
- A test platform realm and the company realm template (no secrets).
- Company seeding through the APIs.
- Runbooks in `saas-plan-doc/runbooks/` (created when this phase is implemented).
- Backup scripts in `ops/` (created when this phase is implemented).
- The import toolkit (PHASE_11).
- The staging and production servers (owner).

## 8. Proposed schema changes

None. The import records use `tbl_data_import_run` / `tbl_data_import_check` (PHASE_01 §6.2 H).

## 9. API and integration requirements

- No test-only endpoint in production builds. The COMPLIANCE `ComplianceSchedulerTestController` is removed or guarded by profile.
- The onboarding uses the standard platform APIs and the toolkit commands only.

## 10. Dependencies on other phases

Every phase provides its own tests. This phase adds the shared infrastructure, the isolation suite, operations and the gates, and executes the first company onboarding after G0–G6.

## 11. Security and tenant-isolation requirements

1. No test, script, CI job, environment or onboarding step uses `192.168.5.52` / `192.168.5.53`.
2. Test data and realm templates contain no real credentials.
3. Backups and exports are encrypted at rest and access-restricted (owner).
4. Production deployments, the import and every destructive operation need explicit owner approval (root `CLAUDE.md`).

## 12. Ordered implementation tasks

| # | Task | Status |
|---|---|---|
| 1 | Compose test profile + test platform realm + company realm template + seeding through the APIs | PENDING |
| 2 | Isolation suite | PENDING |
| 3 | Cross-service functional suite | PENDING |
| 4 | Performance tests | PENDING |
| 5 | Backup and restore tooling | PENDING |
| 6 | Monitoring and alerts | PENDING |
| 7 | Runbooks (including company data import) | PENDING |
| 8 | ACCOUNT-BOOK pre-existing failures: fix or quarantine with approval | PENDING |
| 9 | Optional CI | PENDING |
| 10 | Gate reviews G0–G8 | PENDING (G0 = PHASE_01 approval) |
| 11 | First company onboarding steps O-1..O-14 | BLOCKED (gates and owner decisions) |

## 13. Acceptance criteria

1. The isolation suite passes 100 % locally and on staging.
2. A per-company restore reproduces the data, verified by checksums.
3. The performance targets are met. Proposed targets, for owner confirmation:
   - gateway p95 overhead ≤ 30 ms for the Redis checks;
   - 100 active companies without pool exhaustion;
   - `CREATE_COMPANY` ≤ 10 minutes for the full module set.
4. Every runbook exists and was dry-run on staging.
5. The rehearsal report for the first company is signed. The final import reaches 100 % reconciliation or owner-approved waivers.
6. Shivam users log in with Shivam's Company Login Code into Shivam's new realm, with their migrated credentials (or after the controlled activation flow, CD-16), see the expected modules and data, and the main workflows pass.
7. The legacy system was never modified by the project, and no project tool contacted the protected hosts (audit evidence).

## 14. Testing and validation checklist

- [ ] The baseline regression is green or the known failures are listed.
- [ ] The isolation suite is green.
- [ ] The cross-service suite is green.
- [ ] Performance report.
- [ ] Backup and restore drill.
- [ ] Alert tests.
- [ ] Rehearsal import report signed.
- [ ] Cutover checklist signed.
- [ ] The protected-host refusal is verified in every test helper and in the toolkit.

## 15. Risks and rollback considerations

| Risk | Mitigation |
|---|---|
| No staging server | The owner provides one before G6 |
| Import overruns the freeze window | Rehearsal timing; per-module import order; window agreed from the measured timing |
| Data entered in the new system during the soak cannot be returned to the legacy system on rollback | Q-28 decided (CD-24): 2-week soak; rollback uses a documented **manual re-entry** procedure, no reverse data sync; the procedure is part of the runbooks (task 7) |
| Redis single point of failure | Persistence + a restart runbook; HA considered for production |

**Rollback:** releases keep the previous images. The database changes are expand–contract. For onboarding, the legacy system stays the fallback until the decommission decision.

## 16. Current status

| Item | Status |
|---|---|
| Per-module tests | PARTIALLY_COMPLETED |
| Cross-service, isolation and performance suites | PENDING |
| Backup and restore, monitoring, runbooks | PENDING |
| CI | PENDING (optional) |
| First company (Shivam) onboarding | BLOCKED (gates G0–G6, owner decisions) |
| **Phase** | **PENDING** |
