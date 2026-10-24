# PHASE 12 — Implementation Plan (Testing, Rollout and First Company Onboarding)

| Item | Value |
|---|---|
| Phase document | [PHASE_12_TESTING_ROLLOUT_AND_FIRST_COMPANY_ONBOARDING.md](../PHASE_12_TESTING_ROLLOUT_AND_FIRST_COMPANY_ONBOARDING.md) (objective, gates §6.5, onboarding §6.6, acceptance §13). This plan **adds no decision**. |
| Design sources | PHASE_01 §0 (CD-05, CD-16, CD-18, CD-20, CD-21, CD-23, CD-24 Q-23 / Q-28 / Q-41, CD-27), §6.8; developer guide §18; [S0-5 baseline report](S0-5_BASELINE_TEST_REPORT.md) |
| Status | **DRAFT FOR OWNER REVIEW.** 2026-10-10: §8 adds the owner-requested Version 1 fast-track register and plan (first company by 2026-10-20) — a priority view only, no task unlocked or authorized. PHASE_12 is LOCKED. Per the tracker, T1 (test profile) and T7 (runbooks) may start in parallel with PHASE_02 once PHASE_02 is unlocked; the gates need PHASE_02–PHASE_11; T11 (onboarding) is BLOCKED by gates and owner decisions. |
| Author / date | agent (Claude Opus 5.5), 2026-10-03 |
| Protected hosts | No test, script, CI job, environment or onboarding step uses `192.168.5.52` / `.53`. O-7 / O-8 (legacy backups and exports) are **owner-only**. Staging and production are new, owner-provided servers. |
| Owner approval | Every staging/production deployment, the import, and every destructive operation (root `CLAUDE.md`). |

---

## 1. Summary

1. **Shared test infrastructure:** compose test profile, test platform realm + company realms from the template, seeding through the APIs (T1), fixing the S0-5 environment gaps E-1..E-4.
2. **Suites:** isolation (gate, 100 %) (T2), cross-service functional (T3), performance at 50 companies with headroom (T4).
3. **Operations:** per-company backup/restore incl. per-realm export (T5), monitoring and alerts (T6), runbooks (T7), optional CI (T9).
4. **ACCOUNT-BOOK pre-existing failures** (94, S0-5) fixed or quarantined with approval (T8).
5. **Gate reviews** G0–G8 (T10) and the **first company onboarding** O-1..O-14 (T11).

Size estimate: 11 tasks; test code in every module plus `ops/` scripts and `saas-plan-doc/runbooks/`.

---

## 2. Preconditions

| # | Precondition | Tracker |
|---|---|---|
| 1 | PHASE_02 unlocked (S0-4 DONE, S0-5 accepted) for T1 / T7 | S0-4, S0-5 |
| 2 | Phases 02–11 DONE for the gate suites (G1–G5) | phases |
| 3 | Staging server (owner) before G6; PHASE_11A before G6 is a **PROPOSAL** (S0-12, open) | S0-12 |
| 4 | Owner decisions for onboarding: Q-02 (S0-9), Q-25 (S0-6), Q-42/Q-29/Q-04 values (S0-7, G2a), Shivam's Company Login Code, the role-mapping approval (P11-T7), credential rotation Q-22 (S0-8) | S0-6..S0-9 |
| 5 | Owner confirmation of the performance targets (D12-1) | new (report) |

---

## 3. Owner decisions / clarifications

> **Decisions recorded 2026-10-03 (owner, CD-28):** S0-12 decided (CD-28 item 10): PHASE_11A is a **mandatory** readiness gate before staging/production readiness (G6); it runs only after the schema-freeze approval S0-11. Precondition 3 and T10 are read with this decision.

| ID | Question | Options | Recommendation |
|---|---|---|---|
| **D12-1** (report) | PHASE_12 acceptance 3 proposes "100 active companies without pool exhaustion", while §6.1 and CD-18 use the planning target 50 (tested with headroom, e.g. 60). | (a) Test 60 companies (CD-18 + headroom) as the R1 gate; report the 100-company result as information. (b) Gate at 100. | **(a)**; the 100 figure is marked "for owner confirmation" in the document. |
| **D12-2** (report) | CI host: PHASE_12 recommends CI "on an owner-approved host". The S0-5 run showed a concurrent IDE build corrupting the local Maven cache. | A dedicated build host or container with its own Maven repository | Owner names the host (T9 optional). |

Fixed in the phase document on 2026-10-03 (following existing decisions or measured facts): §2 known failing tests (S0-5 numbers), O-1 no longer lists Q-28 as open (decided, CD-24), O-4 `CMP_101` wording (CD-26), §15 Q-28 risk row (manual re-entry, CD-24).

---

## 4. Task order

```text
T1 test profile (+E-1..E-4) ─> T7 runbooks (skeletons early, completed per phase) ─> T8 ACCOUNT-BOOK failures ─> T2 isolation suite ─> T3 cross-service ─> T6 monitoring ─> T5 backup/restore ─> T4 performance ─> T9 CI (optional) ─> T10 gates ─> T11 onboarding (O-1..O-14)
```

---

## 5. Task details

### T1 — Compose test profile, test realms, API seeding (P12-T1)

| Item | Detail |
|---|---|
| Files | `docker-compose.test.yml` (profile `test`: MySQL, Redis, MinIO, Keycloak 25.0.6 (CD-30), Eureka, gateway, CP, representative services ASSET, STORE, ACCOUNT-BOOK, CRM, PRODUCTION, LOGISTICS; all ports bound to `127.0.0.1`; no `.env` secrets — test values generated per run); `ops/test/seed-test-platform.sh` (platform realm import of test data, bootstrap Super Admin via the PHASE_03 runner, companies created and provisioned **through the platform APIs**); test company realms always from the template. |
| S0-5 gaps | E-1: CP `testcontainers.properties` + `api.version` like COMMON; E-2: entitlement ITs default Redis 16379; E-3: one documented local IT MySQL profile (or per-module properties); E-4: note on `~/.testcontainers.properties`. |
| Tests | `ops/test/smoke.sh`: login via login code → one business call per representative module. |

### T2 — Isolation suite (P12-T2, gate)

| Item | Detail |
|---|---|
| Module | New test module `ISOLATION-TESTS` (REST-assured against the test profile), one test class per PHASE_12 §6.2 item 1–10 incl. 6a; direct-to-container calls use the compose network; Redis outage via `docker pause`. |
| Pass rule | 100 % (acceptance 1); a failure blocks G4. |

### T3 — Cross-service functional suite (P12-T3)

| Item | Detail |
|---|---|
| Scenarios | ASSET standalone; STORE + ACCOUNT-BOOK (mandatory dependency, inward/outward posting); CRM + PRODUCTION + LOGISTICS (booking → dispatch); per-company integration switch on/off (P11-T1); provisioning ADD_MODULE end-to-end. |

### T4 — Performance tests (P12-T4)

| Item | Detail |
|---|---|
| Tooling | k6 (or Gatling) scripts in `ops/perf/`; local or staging only. |
| Targets | Per D12-1: 60 companies × 17 services pools; Keycloak 61 realms (login latency, memory, start-up, admin operations); gateway p95 overhead of the Redis checks (proposed ≤ 30 ms); `CREATE_COMPANY` ≤ 10 min for the full module set; provisioning throughput. Report `plans/PHASE_12_PERFORMANCE_REPORT.md`. |

### T5 — Backup and restore tooling (P12-T5)

| Item | Detail |
|---|---|
| Files | `ops/backup/{backup-company.sh, restore-company.sh, backup-platform.sh}`: all `cmp_<n>_%` schemas (`mysqldump --single-transaction`), mirror of every company module bucket (`mc mirror`), per-realm export (PHASE_03 export job), related platform rows; checksums; encryption at rest by the owner's tooling; Q-23: daily, 30-day retention, RPO ≤ 24 h, RTO ≤ 8 h. Restore into a **fresh** company code for verification (never over a live company). |
| Tests | Drill on the test profile: restore reproduces data (checksums) (acceptance 2), incl. one realm. |

### T6 — Monitoring and alerts (P12-T6)

| Item | Detail |
|---|---|
| Work | Prometheus rules + Grafana dashboards (`observability/`) for the PHASE_12 §6.4 list (provisioning failed/stuck, outbox dead letters, publication lag, Redis, pools per service, storage thresholds, subscription transitions, isolation denials, certificate/token expiry, per-realm login failures/lockouts/drift, realm count). |
| Tests | Alert tests by provoking each condition on the test profile. |

### T7 — Runbooks (P12-T7)

| Item | Detail |
|---|---|
| Files | `saas-plan-doc/runbooks/` (created at implementation): Keycloak platform realm + template; realm automation (create, upgrade, disable/enable, export/restore); environment provisioning account + least-privilege runtime account (CD-03); first Super Admin bootstrap; provisioning failures; credential rotation (DB, MinIO, M2M, internal secrets); schema upgrade; company lifecycle; manual purge (dual approval); Redis outage; **gateway runtime-state rollout and recovery** (P06-T6 N5: CONTROL-PANEL before gateway, initial-resync 503 window, 300 s TTL fail-closed, key / backlog / smoke verification before proceeding — PHASE_12 §6.4); single-company restore; **company data import** (§6.6, PHASE_11 tools); **Q-28 manual re-entry rollback**. Skeletons first, each completed by its phase; dry-run on staging (acceptance 4). |

### T8 — ACCOUNT-BOOK pre-existing failures (P12-T8)

| Item | Detail |
|---|---|
| Input | [S0-5_BASELINE_FAILING_TESTS.txt](S0-5_BASELINE_FAILING_TESTS.txt): 94 failures/errors in 33 classes (symptoms: HTTP 400 instead of 200, missing JSON fields). |
| Work | Triage per class: test bug vs product bug; fix, or quarantine with `@Disabled("P12-T8: <reason>")` **only with owner approval** per class; tracked list in `plans/PHASE_12_ACCOUNT_BOOK_TEST_TRIAGE.md`; done before G5. |

### T9 — Optional CI (P12-T9)

| Item | Detail |
|---|---|
| Work | Pipeline on an owner-approved host (D12-2): build, unit, component IT with Testcontainers, isolation suite nightly; own Maven repository; never a protected host. |

### T10 — Gate reviews G0–G8 (P12-T10)

| Item | Detail |
|---|---|
| Work | One gate report per gate in `plans/GATE_<n>_REPORT.md` with the evidence of PHASE_12 §6.5; owner signs. G0 = S0-1 (DONE). G2a (billing go-live) needs the CA values APPROVED. The PHASE_11A link to G6 stays a PROPOSAL until S0-12 is decided. |

### T11 — First company onboarding O-1..O-14 (P12-T11, BLOCKED)

| Item | Detail |
|---|---|
| Work | Exactly PHASE_12 §6.6 with the three separate activities (CD-16): A standard provisioning (`EMPTY_FOR_IMPORT`), B identity migration (`identity-migrate`), C data import (toolkit). Rehearsal on staging first (O-2/O-3, signed report). O-7/O-8 owner-only (legacy backups, `kc.sh export`, freeze). Go/no-go by the owner (O-11/O-12); soak 2 weeks (Q-28); no automatic deletion of anything legacy (O-14 owner decision). |
| Status | BLOCKED until G0–G7 and the owner decisions of precondition 4. |

---

## 6. Risks

| Risk | Mitigation |
|---|---|
| No staging server | Owner provides before G6 |
| Import overruns the freeze window | Rehearsal timing; per-module order |
| Concurrent local builds corrupt results | Dedicated build host (D12-2); S0-5 rule |
| Redis single point of failure | Persistence + runbook; HA for production considered |
| Test data mistaken for real data | Synthetic only; generated credentials; no `.env` values in test profiles |

---

## 7. Definition of done (PHASE_12)

PHASE_12 §13 acceptance criteria met with signed gate reports; onboarding completed and soaked; owner sets DONE.

---

## 8. Version 1 fast-track — first company by 2026-10-20 (owner instruction 2026-10-10)

**Source:** owner instructions "URGENT: First Company Go-Live by 20 October 2026 — 10-Day Fast-Track Execution" and "URGENT: First Company Onboarding and Complete Tenant Isolation — Version 1 Fast-Track" (2026-10-10). **Status of this section: REGISTER AND PLAN — no task is unlocked, claimed or authorized by it.** Every task keeps the normal process (readiness → owner decisions → explicit implementation authorization → claim → REVIEW → owner DONE). The approved architecture and decisions (CD-01..CD-33) are unchanged; the HOLD / BLOCKED labels below are a priority view, not tracker statuses (the tracker vocabulary is unchanged). This section is the single V1 plan — no other plan file is created.

### 8.1 Version 1 scope and acceptance criteria

V1 is ready for the first company only when evidence shows: (A1) the Platform Admin creates the company through the Admin Panel flow; (A2) the company has its own Keycloak realm (`co-<n>`), provisioned by the backend service-account workflow, registered ACTIVE, issuer allow-listed; (A3) isolated databases exist for its **selected purchased** modules (plus the PLATFORM_CORE resources), registered VERIFIED; (A4) its storage buckets are provisioned and registered, object access is company- and module-bound; (A5) activation happens only through the approved order → full verified manual payment → activation path (no manual grant as a payment substitute, CD-07), never partially; (A6) the Company Super Admin is invited securely and logs in through the company realm (no Keycloak Admin Console access); (A7) backend authorization (company context, module entitlement, module role) is enforced at the gateway and in the services — not only in a UI; (A8) representative business workflows of the selected modules succeed; (A9) cross-company data, identity and storage isolation tests pass; (A10) audit records and failure / retry / recovery behaviour are safe; (A11) no unresolved critical security or isolation defect; (A12) the onboarding is repeatable and documented (runbook + rehearsal evidence).

### 8.2 Verified current state (Day 1 audit, 2026-10-10, read-only; four fresh-context audits, key claims re-checked in code)

| # | Finding | Evidence |
|---|---|---|
| S1 | No production code path makes a subscription ACTIVE: `createPendingFromConfirmedOrder` / `activateOnFullPayment` are called only by tests; PHASE_07 0 / 11, plan DRAFT; `Invoice` / `PaymentTransaction` are unused stubs | CP `subscription/SubscriptionService.java:139,188`; `model/Invoice.java` |
| S2 | Provisioning creates the attachment DB and a DB for **every** catalog module that needs one (not only purchased ones), bootstraps / verifies them and sets the company ACTIVE — **without a realm**; in-process, non-durable executor with manual recovery | `CompanyProvisioningClaims.java:131-140`; `TenantProvisioningService.java:85-111`; `ProvisioningAsyncConfig.java:16-38` |
| S3 | Realm creation / registration / Super Admin invite building blocks exist but nothing in the onboarding flow calls them | `RealmTemplateApplier` used only inside `KeycloakIdentityProviderAdmin`; `RealmRegistryService.registerCompanyRealm`; `IdentitySyncOutbox.enqueueRealmAdminUser` (no caller) |
| S4 | D3-10 open: company-realm user operations fail closed in production; template defects (`erp-identity-admin` `fullScopeAllowed:false`, no user profile) | `KeycloakAdminTokenProvider.java:32-38,88-90`; NEXT_SESSION_PROMPT §7 item 1a |
| S5 | No company-user / business-role API, no `/api/v1/company/**` controller; invitations need a platform token and assign no realm role | `PlatformMembershipService.java:167-197`; `IdentitySyncProcessor.java:127-128` |
| S6 | Gateway enforces module entitlement only; no module-role or permission check; service entitlement filters off (`MODULE_ENTITLEMENT_ENFORCE:false`) | `API-GATEWAY …/CompanyContextFilter.java:155-166`; service `application.yml` |
| S7 | Security: unverified JWT payload decoding used for role decisions (COMMON `JwtAccessTokenUtil`, CRM `CrmDataAccessAuthority`, HRMS / PAYROLL `TenantContext`, COMPLIANCE, CP `JwtRoleExtractor`); Tickets IDOR (`?userId=`, unscoped GET / PUT / DELETE / filter) | COMMON `JwtAccessTokenUtil.java:136-153`; `TicketRaiseController.java:78-89,128-216` |
| S8 | Buckets are created lazily on first upload and not registered; FILE-SERVICE has no permission check; ACCOUNT-BOOK stores files locally | `MinioStorageService.java:41,118-131`; `AttachmentService.java:444` |
| S9 | Entitlement keys expire after 300 s while the resync scheduler is off by default — a deployment must set `MODULE_ENTITLEMENT_SYNC=true` | CP `application.yml:262-264` |
| S10 | CP schema is Hibernate `ddl-auto: update`; CD-27 / CD-28 item 10 forbid that on staging / production — PHASE_11A (0 / 8) is mandatory before G6 / production; master: no real company before G0–G6 | CP `application.yml:31`; PHASE_01 CD-27 / CD-28; master §22 ("No real company is onboarded … before gates G0–G6") |
| S11 | No ERP web UI for company login / administration in this repository (only ACCOUNT-BOOK `erp-ui`, hard-wired to `ERPSystem`); UI owner open (Q-25) | `ACCOUNT-BOOK-SERVICE/erp-ui/src/environments/environment.ts:9-10`; PHASE_01 Q-25 |
| S12 | First company per the register: Shivam IndusCorp (CD-09 / CD-16), standard flow, **with** identity migration and data import (P11-T6 / P11-T7a PENDING); modules not decided (Q-02); no backup / restore tooling, runbooks or alerts; staging / production hosts not defined in the repository | PHASE_01 CD-09 / CD-16, §6.8; PHASE_12 §6.6 |
| S13 | Works today: P05 pricing / tax engine (prod quoting gated by `TaxGoLiveGate` until APPROVED tax configuration), P06 subscription / entitlement / allowance engine, per-company module DB provisioning with Liquibase and verification, tenant routing in the services, gateway entitlement check | P05 / P06 code; P02 tasks DONE |

### 8.3 Task register (P0 / P1 / HOLD / BLOCKED) — every open PHASE_06–PHASE_14 task

Effort in agent-days incl. readiness, tests and review rounds (estimates). "Eligible" = may start after the stated owner authorization; nothing is authorized today except the owner's P06-T5 review.

| Task | Tracker | V1 class | Reason / proof | Depends on | Est. |
|---|---|---|---|---|---|
| P06-T5 Allowances | REVIEW | **P0** | seat / storage limits (A7); implemented, CP IT 258 / 0 | owner acceptance | 0 |
| P06-T6 Publisher, `TENANT_STATUS`, resync on, gateway status check | PENDING | **P0** | S9 (keys expire without resync); company-status enforcement at the gateway; R4 / F1 / R2 carried | P06-T5 | 2.5 |
| P06-T3 Lifecycle scheduler | PENDING | HOLD (conditional) | access already stops after the PAID period at recompute once resync is on (S9); warnings / EXPIRED status / scheduled changes are needed only **before the first `end_date` or the first scheduled change** — annual billing (decision BD-10) gives ≥ 1 year | P06-T6 | 2 |
| P06-T7 Company read APIs | PENDING | HOLD | the company does not need to read its subscriptions to onboard / operate | PHASE_10 permissions | 1.5 |
| P06-T8 Removals | PENDING | HOLD | cleanup only | owner deletion approval | 1 |
| P07-T1 Billing entities | PENDING | **P0** | A5 | PHASE_07 unlock (BD-4), plan approval (BD-5), S0-2 item 2 | 1.5 |
| P07-T2 Series, seller, billing policy, `EInvoiceProvider` NONE | PENDING | **P0** | invoice / receipt numbering, seller snapshot | T1, series formats | 1 |
| P07-T3 Billing account | PENDING | **P0** | buyer GSTIN / state for the invoice | T1 | 1 |
| P07-T4 Order service | PENDING | **P0** | A5 | T1–T3, P05 engine | 2 |
| P07-T5 Confirmation (AWAITING_PAYMENT + PENDING subscription) | PENDING | **P0** (proforma PDF: HOLD) | A5 | T4 | 1 |
| P07-T6 Manual payments + verification | PENDING | **P0** | full verified payment before activation | T1 | 2 |
| P07-T7 Activation (invoice issue, ACTIVE, entitlements, FULFILLED) | PENDING | **P0** | A5, immutable financial record | T5, T6 | 1.5 |
| P07-T9 Credit notes; invoice / proforma PDFs | PENDING | **BLOCKED** (BD-5) | the tax-invoice PDF may be a legal day-one need (CA); credit notes can be HOLD only if the owner accepts no corrections until V2 | T7, billing bucket (D7-1) | 1.5–2 |
| P07-T8 Manual renewal | PENDING | HOLD (conditional) | needed before the first period ends (BD-10) | T7, P06-T3 | 1.5 |
| P07-T10 Company read APIs | PENDING | HOLD | not needed to onboard | PHASE_10 | 1 |
| P07-T11 Platform mail sender | PENDING | HOLD (V2) — *exception 2026-10-13 (PHASE_01 CD-63 G1): released from the HOLD (V2) for P07-T11 only; this historical row is kept as written* | D6-1 dashboard fallback approved; identity invitations use the realm SMTP, not this sender | — | 2 |
| P08-T5 Realm provisioning | PENDING | **P0** | A2, S3 | S0-2 item 6 (DECIDED, CD-82), PHASE_08 unlock or CD-37 authorization; D3-10 (BD-6) closure before DONE (CD-82 (7), A′) | 2–3 |
| P08-T2 Planner from entitlements | PENDING | **P0** | A3: databases for the **selected purchased** modules (S2 provisions all) | PHASE_06 | 1.5 |
| P08-T4 Executors + registry integration | PENDING | **P1** | carrier for T2 / T5 / T6 steps in the run | T2 | 1–1.5 |
| P08-T6 Bucket provisioner | PENDING | **P0** | A4, S8 | T4 | 1–1.5 |
| P08-T10 Provisioning account runbook (operator) | PENDING | **P0** | no `root` DB account in production (S2) | operator | 0.5 |
| P08-T1 / T3 Job schema, durable worker | PENDING | HOLD (BD-7 decided, CD-82 (4)) | the in-process executor + manual recovery is accepted for the first Company only; the durable worker is required before a second Company | — | 3 |
| P08-T7 `ADD_MODULE` / `REPAIR` / upgrade jobs | PENDING | HOLD | no module change after onboarding in V1 | T3 | 2 |
| P08-T8 Provisioning APIs | PENDING | HOLD | existing provision / recover endpoints suffice | — | 1 |
| P08-T9 Reconciliation job | PENDING | HOLD | manual verification at onboarding | — | 1 |
| P09-T1 Registry bucket resolver, no lazy buckets | PENDING | **P0** | A4 | P08-T6 | 1 |
| P09-T2 Exact tenant check, `uploadedBy` / `deletedBy` | PENDING | **P0** | A4, A9 | — | 1 |
| P09-T3 FILE-SERVICE authorization | PENDING | **P0** | A7, S8 | P10-T12 | 1 |
| P09-T4 Usage reserve / delta + outbox (`assertCanStore` caller) | PENDING | **P0** | storage allowance enforcement (A7) | P06-T5 | 1.5 |
| P09-T6 Bucket policy and quota | PENDING | **P1** | object-store policy per bucket | P08-T6 | 1 |
| P09-T9 Remove the tenant-less purge job | PENDING | **P1** | cross-tenant side effect risk | — | 0.5 |
| P09-T5 Daily reconciliation + notifications | PENDING | HOLD | counters are maintained transactionally; reconciliation later | T4 | 1 |
| P09-T7 Bucket import tool | PENDING | BLOCKED (BD-1) | only with data import | — | 1.5 |
| P09-T8 ACCOUNT-BOOK files to MinIO | PENDING | BLOCKED (BD-2) | P0 if ACCOUNT-BOOK is selected (local files break A4) | — | 2 |
| P10-T1 Entities | PENDING | **P0** | users / roles (A6, A7) | PHASE_10 unlock | 1 |
| P10-T2 `CompanyUserService` + seats | PENDING | **P0** | A6, seat limits | T1, P06-T5 | 1.5 |
| P10-T3 Company Super Admin assignment | PENDING | **P0** | A6 | P08-T5, D3-10 | 1 |
| P10-T5 `RoleService` / assignments (predefined roles) | PENDING | **P0** | A7 | T4 | 1.5 |
| P10-T4 Permission registration + predefined role policy | PENDING | **P1** | needed by T5 | — | 1 |
| P10-T7 Company console APIs (CD-13) | PENDING | **P0** | company admin manages users / roles via the ERP backend | T2, T3, T5 | 2–3 |
| P10-T10 Identity-sync test run (company realm) | PENDING | **P0** | proves A6 against Keycloak 25.0.6 | D3-10 | 1 |
| P10-T12 Gateway module-role check | PENDING | **P0** | A7 | realm roles | 1 |
| P10-T14 Legacy role checks, unverified decoders, Tickets IDOR | PENDING | **P0** | S7 (critical security) | — | 2 |
| P10-T15 Service entitlement filter everywhere | PENDING | **P0** | A7 backend enforcement | P11-T4 | 1 |
| P10-T8 Company user directory + COMMON client | PENDING | BLOCKED (BD-2) | P0 for every selected module that looks users up (10 services) | — | 2 |
| P10-T9 Branding API | PENDING | BLOCKED (BD-1) | branding is hard-coded to Shivam — P0 only if the first company is not Shivam | — | 1.5 |
| P10-T13 `PermissionEvaluator` + `@RequiresPermission` (~150–220 files) | PENDING | **BLOCKED** (BD-8) | module-role level (T12) + predefined roles may suffice for V1 only by owner decision; otherwise P0 (8–12 days) | T4, T6 | 8–12 |
| P10-T6 Role-policy publisher | PENDING | BLOCKED (BD-8) | needed with T13 | T4 | 1 |
| P10-T16 Audit-endpoint permission, CP annotations | PENDING | **P1** | audit access control | T4 | 1 |
| P10-T11 Stale `IdentitySyncMySqlIT` assertions | PENDING | HOLD | obsolete (tracker row) — owner may close | — | 0 |
| P11-T2 Directory adoption | PENDING | BLOCKED (BD-2) | with P10-T8 for the selected modules | P10-T8 | 2 |
| P11-T3 Branding adoption | PENDING | BLOCKED (BD-1) | with P10-T9 | P10-T9 | 1.5 |
| P11-T4 Feign `ErrorDecoder` + soft handling | PENDING | **P1** | needed by P10-T15 | — | 1 |
| P11-T8 Compatibility tests C-1..C-9 | PENDING | **P1** (subset) | selected modules on a rehearsal company | selected modules | 1.5 |
| P11-T6 Data-import toolkit | PENDING | BLOCKED (BD-1) | P0 only if the first company imports data (Shivam per CD-16) | PHASE_09 / 10 | 6–10 |
| P11-T7 / T7a Role mapping, identity migration | PENDING | BLOCKED (BD-1) | P0 only with identity migration (Shivam) | PHASE_10 | 5–8 |
| P11-T1 Integration resolver | PENDING | HOLD | global integration toggles suffice for one company | — | 2 |
| P11-T5 Portal users | PENDING | HOLD | unless the customer portal is selected (BD-2) | — | 2 |
| P11-T9 Legacy switch removal | PENDING | HOLD | cleanup | — | 1 |
| P11A-T1..T7 CP Liquibase baseline | PENDING | **BLOCKED** (BD-3) | P0 if V1 runs with real company data (CD-27 / CD-28 item 10); HOLD only for a non-production prototype | S0-11 freeze | 3–5 |
| P11A-T8 Retire old changelogs | PENDING | HOLD | cleanup (owner `svn move`) | T6 | 0.5 |
| P12-T1 Compose test profile, test realms, API seeding | PENDING | **P0** | end-to-end test environment (A9, A12); plan: may start in parallel with PHASE_02 | — | 2 |
| P12-T2 Isolation suite | PENDING | **P0** | A9 | T1 | 2 |
| P12-T3 Cross-service functional suite | PENDING | **P0** (subset) | A8 for the selected modules | T1, BD-2 | 2 |
| P12-T5 Backup and restore tooling | PENDING | **P0** | Q-23 restore drill before go-live with real data | hosts (BD-12) | 2 |
| P12-T7 Runbooks | PENDING | **P0** | A12; plan: may start in parallel with PHASE_02 | — | 1.5 |
| P12-T6 Monitoring and alerts | PENDING | **P1** (minimal) | failure visibility (A10) | — | 1 |
| P12-T8 ACCOUNT-BOOK 94 failures | PENDING | BLOCKED (BD-2) | fix or quarantine with approval if ACCOUNT-BOOK is selected | — | 0.5 |
| P12-T10 Gate reviews G0–G8 | PENDING | **BLOCKED** (BD-3, BD-4) | gates are mandatory before a real company (master §22); compression only by owner decision | — | 1 |
| P12-T11 First company onboarding | BLOCKED | **BLOCKED** | gates + decisions | all P0 | 2 + soak |
| P12-T4 Performance (50 companies) | PENDING | HOLD | one company in V1 (G6 wording needs the owner, BD-3) | — | 2 |
| P12-T9 Optional CI | PENDING | HOLD | optional | — | 2 |
| UI: company login (Company Login Code) + Super Admin / Company Admin screens | not a task in this repository | **BLOCKED** (BD-9) | S11 — without a UI no user can log in to a company realm | Q-25 | ? |
| P13-T1..T8 Company mailbox integration | PENDING (phase FUTURE) | **HOLD — Version 2** | owner deferral 2026-10-10; P13-T8 (Shivam transition from the interim global sender) also V2 | PHASE_12 live | — |
| P14-T1..T10 Online payments, public website, self-signup | PENDING (phase FUTURE) | **HOLD — Version 2** | owner deferral 2026-10-10; the R1 manual payment verification (P07-T6) stays in V1 | PHASE_12 live | — |

**Totals:** P0 + P1 (without the BLOCKED items) ≈ 50–60 agent-days; with Shivam's import and identity migration + 15–25; with fine-grained permissions (P10-T13) + 8–12; UI outside this repository.

### 8.4 Owner decisions required (BLOCKED items) — BD-1..BD-13

| ID | Decision | Options / recommendation |
|---|---|---|
| BD-1 | First company and import | (a) Shivam with identity migration + data import (CD-16) → P11-T6 / T7a / P09-T7 P0, date moves; (b) a first company **without** import for V1 (Shivam later) — recommended for a V1 prototype |
| BD-2 | Selected ERP modules (Q-02) | scopes the module DBs, buckets, directory adoption, P09-T8 / P12-T8 (ACCOUNT-BOOK), P12-T3; recommendation: the smallest useful set |
| BD-3 | Is V1 a production with real company data? | yes → PHASE_11A + G0–G6 + restore drill are P0 (CD-27 / CD-28 / master §22); no (staging prototype) → they move to the production cutover |
| BD-4 | Governance exception | unlock subsets of PHASE_07 / 08 / 09 / 10 (+ P12-T1 / T2 / T3 / T5 / T7) in parallel (precedent D3-4, PHASE_04 exceptions); P06-T3 / T7 / T8 after the V1 path — **2026-10-12 (CD-36): exception prepared (§8.13), not in force until the owner approves it; no phase unlocked** — **2026-10-12 (CD-37): approved as a scoped exception, not a blanket authorization; per-task readiness + authorization; locked list in §8.13** |
| BD-5 | PHASE_07 plan approval with the V1 scope | T1–T7; S0-2 item 2 (status-history tables); ORDER / INVOICE / RECEIPT series formats; billing bucket (D7-1); invoice PDF on day one (P07-T9) and credit notes; CA values S0-7 (Q-04, Q-29, Q-42) — without APPROVED tax configuration `TaxGoLiveGate` refuses production quoting |
| BD-6 | D3-10 | (a) CP key pair + JWKS + signed JWT (target design); (b) per-realm client secret for R1; plus the template fixes — **2026-10-12 (CD-36): comparison and recommendation (a) signed JWT with an internal-only JWKS URL (§8.14); D3-10 stays OPEN until the owner records the decision; no implementation** — **2026-10-12 (CD-37): (a) selected as the preferred R1 design; D3-10 OPEN until the acceptance criteria A1–A6 (§8.14) are evidenced** |
| BD-7 | Provisioning executor | accept the in-process executor + manual recovery for V1 (P08-T1 / T3 HOLD) or require the durable worker. **Decided 2026-10-16 (CD-82 (4)):** in-process executor + manual recovery for the first Company only; the durable worker is required before a second Company is onboarded |
| BD-8 | Authorization granularity | V1 = authentication + company context + entitlement + module realm role (P10-T12) + predefined roles; fine permissions (P10-T13 / T6) in V2 — or P10-T13 now (+ 8–12 days) |
| BD-9 | UI (Q-25) | who delivers company login (Company Login Code) and the minimum Super Admin / Company Admin screens, and when — **resolved (CD-35, §8.12): UI deferred to the final UI phase; reaffirmed 2026-10-12 (CD-36 item 6)** |
| BD-10 | Billing period of the first company | annual → P06-T3 / P07-T8 HOLD justified for V1 |
| BD-11 | P06-T5 | owner review (REVIEW; F5-7 resolved) — **resolved 2026-10-12 (CD-36 item 1): P06-T5 DONE; M-1 / T-1 retained as follow-up findings** |
| BD-12 | Environment | staging / production hosts; resolve the `CLAUDE.md` statement that .52 / .53 are reserved for future production while the documents place the legacy data there (agents never access .52 / .53 / .15). **PHASE_08 development / IT scope decided 2026-10-16 (CD-82 (6)):** local MySQL 192.168.5.141 and local Keycloak 25.0.6 at 127.0.0.1:8081; staging / production hosts stay open |
| BD-13 | Operator setup | `erp-platform` realm, service accounts, `svc-*` clients, internal secrets, pinned Keycloak hostname, SMTP vault, `IDENTITY_SYNC_ENABLED`, provisioning DB account, MinIO keys, `MODULE_ENTITLEMENT_SYNC=true`; S0-3 SVN commit and S0-8 credential rotation (owner). **Timing for PHASE_08 (CD-82 (6)):** the provisioning DB account and the `erp-realm-provisioner` client are required before the P08-T4 / P08-T5 readiness, not before the phase unlock |

### 8.5 Isolation requirements (V1 verification)

- **Database:** one database per selected module and company plus the PLATFORM_CORE resources, created by provisioning, schema by the module's Liquibase, registered VERIFIED; services route by the verified `X-Company-Code`; an isolation test proves company A cannot read / write company B (P12-T2).
- **Keycloak:** platform realm + one realm per company, created by the provisioning service account, registered ACTIVE; the gateway accepts only registered issuers (signature, audience, expiry, company context); no user-chosen realm / issuer; no Admin Console for company admins; Super Admin by invitation (P08-T5, P10-T3, P10-T10).
- **Authorization:** gateway: issuer → company, entitlement, module role; services: verified token only (P10-T14), entitlement filter on (P10-T15), predefined roles (P10-T5); Tickets IDOR fixed.
- **Storage:** registered buckets per company / module (P08-T6, P09-T1), exact tenant check (P09-T2), FILE-SERVICE authorization (P09-T3), storage allowance reserve (P09-T4).
- **Activation integrity:** order → full verified manual payment → activation (P07-T4..T7); provisioning success before ACTIVE; no partial activation; audit on every step; documented recovery.

### 8.6 Onboarding checklist and end-to-end acceptance tests (V1)

1. Platform Admin creates the company (Admin Panel) → `CMP_<n>`, DRAFT. 2. Billing account, order with the selected modules, quote, confirm → AWAITING_PAYMENT + PENDING subscription. 3. Manual payment recorded and verified (full amount) → invoice issued, subscription ACTIVE, entitlements computed (resources PENDING). 4. Provisioning: realm `co-<n>` (template, clients, roles) registered ACTIVE; databases of the selected modules VERIFIED; buckets registered; company ACTIVE only when every required step succeeded. 5. Company Super Admin invited (realm SMTP) → sets a password → logs in through the company realm (UI). 6. Super Admin invites users with predefined module roles; seat limit enforced. 7. Users run representative workflows per selected module. 8. Negative tests: a second company cannot see the first company's data, users, files or modules; a user without the module role is refused; a forged role header is ignored; a suspended company is refused; a failed provisioning step leaves the company not ACTIVE and recoverable; an unpaid order never activates. 9. Restore drill (if BD-3 = production). 10. Runbook updated from the rehearsal.

### 8.7 Ten-day plan (2026-10-11..20) — only after BD-1..BD-9 are decided

| Day | Work (parallel streams) | Exit criterion |
|---|---|---|
| 11 Oct | Owner decisions BD-1..BD-9; P06-T5 review; readiness reviews of the authorized subsets (read-only) | decisions recorded |
| 12–14 Oct | A: PHASE_07 T1–T7; B: D3-10 + P08-T5 + P08-T2 / T4 / T6; C: P10-T14 (security) + P10-T1 / T2; D: P06-T6 | tasks in REVIEW, ITs green |
| 15–16 Oct | B: P09-T1 / T2 / T3 / T4; C: P10-T3 / T4 / T5 / T7 / T10 / T12 / T15 + P11-T4; D: P12-T1 | local end-to-end create → pay → provision → invite → login (with UI) |
| 17 Oct | P12-T2 isolation suite, P12-T3 subset, failure paths | isolation 100 % |
| 18 Oct | defects; P12-T5 / T7 (restore drill, runbook) | drill evidence |
| 19 Oct | onboarding rehearsal on a rehearsal company | rehearsal report |
| 20 Oct | GO / NO-GO review against §8.8 | signed decision |

**Feasibility (Day 1):** the P0 work alone (≈ 50–60 agent-days plus the UI, governance rounds and, for Shivam, the import) does **not** fit 10 days even with four parallel streams; 20 Oct is realistic only as a **readiness milestone** (decisions, authorized tasks in REVIEW, local end-to-end path partly proven). Earliest realistic V1 with a real company: about mid-December 2026 with the exceptions of BD-4 and the narrow scope of BD-1 / BD-2 / BD-8; Shivam with import later.

### 8.8 GO / NO-GO criteria for 2026-10-20

GO only if A1–A12 (§8.1) are evidenced; every P0 task is at least REVIEW with green tests and the owner accepted the P0 set; P12-T2 isolation 100 %; no open critical security finding (S7 fixed); BD-3 requirements met (PHASE_11A and the restore drill if real data); a rehearsal report exists. Any missing item → **NO-GO** with the gap list. Day-1 assessment: **NO-GO** for a production go-live on 20 Oct.

### 8.9 Daily reporting (template)

Completed work and task IDs; files changed; tests and results; onboarding capabilities verified (A1–A12); remaining P0 tasks and dependencies; security / isolation issues; owner decisions required (BD-x); features held for V2; revised confidence in the 20-Oct target. Reports are appended to WORK_LOG ("V1 fast-track — day N").

### 8.10 Revision 2026-10-10 (CD-34): Product Owner Company first, Shivam deferred, legacy Ticket Service retired

**Source:** CD-34 (owner instruction "Claude Master Task: Product Owner Testing Company, Legacy Ticket Service Removal, and SaaS Critical-Path Execution"). This revision **resolves BD-1** (first company = the owner's internal Product Owner Company, onboarding type NEW, no import) and **BD-2 for that company** (all approved — ACTIVE — catalog modules). Everything else in §8 stays; no task status changed; nothing below is authorized for execution unless stated.

**Register changes:** P11-T6, P11-T7 / T7a, P09-T7 (import, identity migration, bucket import) → **HOLD (Shivam, deferred)** instead of BLOCKED; P10-T9 / P11-T3 (branding) → **P0 for the Product Owner Company** (branding is hard-coded to Shivam today); P08-T2 (planner from entitlements) stays P0 for customer companies (the Product Owner Company receives every module anyway); the Tickets part of P10-T14 (IDOR) becomes moot once the service is retired — the unverified-JWT decoding fixes of P10-T14 stay P0; P12-T3 subset = representative workflows of every approved module.

**Product Owner Company — evidence-based approach.** Verified in code: there is no company type / purpose field (`Company`: code, names, slug, login code, status, `onboarding_type` NEW / IMPORT_EXISTING_DATA, …); `SubscriptionType` = TRIAL (reserved, no trials in R1 — Q-18) / PAID; a subscription becomes ACTIVE only through a fulfilled order (master §12.2: "paid or approved-waived"; §13: 100 % of the order amount confirmed paid) and PHASE_07 does not exist yet; Super Admin **manual grants** (P06-T4, T4-6) can entitle any usable catalog module incl. PLATFORM_CORE, non-charged, with reason and inclusive `valid_to`, audited — but PLATFORM_CORE rows of source `CORE` are created only with a qualifying subscription, and CD-07 forbids a manual grant as a payment substitute; **allowance overrides** (P06-T5) can set its user / storage limits with reason and expiry, audited; current provisioning already creates the databases of every catalog module (S2) — which matches this company's all-module need — but no realm (S3). Remaining blockers for its login are the same as for any company: D3-10 (BD-6), realm provisioning P08-T5, Super Admin invitation P10-T3 / P10-T10, the UI (BD-9), the security fixes (P10-T14) and resync on (S9), plus the phase unlocks (BD-4).

| ID | Owner decision needed | Options (recommendation first) |
|---|---|---|
| PO-1 | How the Product Owner Company's modules are entitled without a paid order | (a) **interim, recommended:** an explicit, documented exception to CD-07 for this internal company only — audited Super Admin manual grants of every approved module (incl. the PLATFORM_CORE modules) with reason "Product Owner internal testing company" and a `valid_to` (e.g. one year, renewed by a new grant), plus allowance overrides for users / storage with an expiry; no subscription, no invoice, no payment record; replaced by (b) once PHASE_07 exists; (b) **target:** a standard PHASE_07 order with a 100 % MANUAL adjustment (D4-4 permits 100 %) → an "approved-waived" order — needs a PHASE_07 rule for zero-total confirmation / activation (master §12.2 mentions it, no detailed rule yet); (c) a new INTERNAL subscription type — new business rule and enum value, not recommended — **DECIDED 2026-10-12 (CD-36 item 3): option (a) — a documented CD-07 internal entitlement exception for the owner's permanent Product Owner / testing Company only (all approved ACTIVE catalog modules, internal testing only, never for customer companies); consequence: the PHASE_08 provisioning precondition "valid subscription required" (master §7.1, not enforced in code today) must admit this company under the exception — decide in the P08 readiness**. **Decided 2026-10-16 (CD-82 (5)):** the `CREATE_COMPANY` order / subscription conditions are replaced by these grants for this company only |
| PO-2 | How the internal purpose is recorded | (a) **recommended:** no new field — the purpose in the creation / grant / override audit reasons and in the company's legal / display name; (b) a new company attribute (schema change, needs a decision and a P10 / P11A schema item) — **DECIDED 2026-10-12 (CD-38 item 2)** as recommended (register entry naming the company code once created, audit reasons, naming marker "(Internal testing)"); earlier — **recommendation recorded 2026-10-12 (CD-37 item 5):** (a) with the existing architecture only — (1) a decision-register entry naming the Product Owner Company's code (`CMP_<n>`) once it is created, which binds the CD-36 exception to that one company; (2) the creation, manual-grant and allowance-override audit reasons "Product Owner internal testing company (CD-36 PO-1)"; (3) a name marker in the legal / display name (e.g. "… (Internal testing)"); no schema change, no new enum value, no provisioning bypass; code that ever needs to recognise the company reads the registered code from configuration, decided in that task's readiness |
| PO-3 | "All approved modules" | all ACTIVE catalog modules (RETIRED excluded — after the Ticket retirement `tickets` is excluded) |

**Legacy `TICKETS-SERVICE` retirement — verified scope** (read-only inventory, 2026-10-10): versioned directory (working copy r1383); 42 main Java files, 9 resources, 6 **unversioned local test files** (back up before any move), `pom.xml`, `Dockerfile`, `AGENTS.md`, 5 Liquibase table changelogs; no other module depends on it and no service calls it. Active references: root `pom.xml` module line; `docker-compose.yml` `tickets-service` block; `.env` key `TICKETS_PORT` (owner file — agents never modify `.env`); gateway route `tickets-service` (`/tickets/**`) and `GatewayCorrelationFilter` line; CP `application.yml` (`tenant.provisioning.modules` comparison list, `svc-tickets-service` in two `allowed-azp` lists); Prometheus job `tickets-service`; README service row. **Coupled (risky) items:** the CP catalog seed row `tickets` (live `tbl_module` rows, plans, subscriptions, entitlements may reference it; RETIRED modules are still provisioned — `ModuleCatalogReader.provisioningModules()` uses ACTIVE + RETIRED — so after the service is gone a new company's provisioning would fail closed on the `tickets` bootstrap); the catalog / gateway parity and count tests (~12 CP test files); `ModuleCatalog.CANONICAL_MODULES`; `AttachmentModule.TICKETS` + FILE-SERVICE `tickets-bucket` (existing attachment rows / buckets — keep, as for VRM / ACCOUNT); existing `cmp_<n>_tickets_db` databases (archive, never drop without approval). Documentation mentions in ~17 governance files (updated when the retirement is executed). False positives: the gateway WebSocket-ticket feature (`WebSocketTicket*`, `WS_TICKET_SECRET` — **keep**), `weighbridge_ticket_no`.

| ID | Owner decision / authorization needed | Options (recommendation first) |
|---|---|---|
| TK-1 | Execution authorization for the archive and the active-reference cleanup | separate explicit authorization (this revision records the plan only) |
| TK-2 | Archive method (SVN) | (a) **recommended:** the owner runs `svn move TICKETS-SERVICE _cleanup_review/legacy-tickets-service-2026-10-10` (agents may run only `svn status` / `info` / `diff` / `cat`, root `CLAUDE.md`); (b) the owner explicitly authorizes the agent to run that local `svn move` (working copy only, no server operation); a plain file-system move is **not** acceptable — the versioned folder would come back on the next `svn update` (precedent: ACCOUNT / VRM, 2026-09-30) |
| TK-3 | Write access to `_cleanup_review/` | the hard rule "never touch `_cleanup_review/`" needs an explicit, limited exception: only the new folder `_cleanup_review/legacy-tickets-service-2026-10-10/`, nothing else in `_cleanup_review/` read or changed |
| TK-4 | Catalog module `tickets` | (a) **recommended:** retire it through the catalog lifecycle (ACTIVE → RETIRED) **and** exclude RETIRED modules from provisioning of new companies (small CP change + tests; decide before retiring, RETIRED rows are read-only) and let the gateway-drift rule skip RETIRED modules; (b) delete the seed row (unsafe while live rows may reference it) |
| TK-5 | Shared attachment artefacts | keep `AttachmentModule.TICKETS`, the FILE-SERVICE bucket mapping and existing tenant databases / buckets (recommended, as for VRM / ACCOUNT); data not migrated, not dropped |
| TK-6 | Owner-only files | `.env` (`TICKETS_PORT`) and any live Keycloak client `svc-tickets-service` are changed by the owner / operator, not by an agent |

**Revised critical-path order (business priority; each item still needs its readiness review and authorization):** (1) P06-T5 owner review; (2) P06-T6 readiness (resync on, gateway status check); (3) PO-1 decision → for the Product Owner Company the interim manual-grant path needs no PHASE_07; for customer companies PHASE_07 T1–T7 (order → manual payment verification → activation); (4) D3-10 + P08-T5 realm provisioning + P10-T3 / P10-T10 Super Admin invitation and login; (5) security: P10-T14 (verified JWT only), P10-T12 module-role check, P10-T15 entitlement filter; (6) P08-T2 module-specific provisioning for customer companies; (7) Product Owner Company end-to-end validation (create → resources → realm → Super Admin login → authorized module access, negative tests incl. invalid / expired JWT, missing roles, cross-company access); (8) storage: P08-T6, P09-T1..T4; (9) production readiness before any real customer go-live: PHASE_11A, gates, backup / restore drill, runbooks, environment; (10) Shivam onboarding / import (deferred); (11) the future Support module (§8.11). The Ticket retirement (TK-1..TK-6) is independent of this chain and can run in parallel once authorized. The UI (BD-9) remains a prerequisite for any interactive login.

### 8.11 Future SaaS-native Support / Ticket module (planning only, CD-34 item 4)

**Not built now; separate from the retired legacy service.** Scope: company users raise bugs, support requests and product issues; status, priority, assignment, comments and attachments; notifications. **Security boundaries:** strict company isolation of tickets and attachments (company-scoped storage, no cross-company identifiers in APIs); tenant-aware APIs with backend authorization (company context, module entitlement, role / permission — PHASE_10 `@RequiresPermission`); ownership checks on every read / write (no IDOR — the defects of the legacy service are the negative test list); platform-support access only explicitly authorized, time-bound and audited (no standing cross-company access). **Dependencies:** PHASE_09 (registered buckets, FILE-SERVICE authorization), PHASE_10 (company users, roles, permissions, directory), PHASE_11 (integration patterns), the platform realm for support staff; possibly platform mail (P07-T11) for notifications. **Proposed placement (SP-1, owner decision):** a new future phase after the R1 go-live (e.g. "PHASE_15 — SaaS Support / Ticket module", status FUTURE), or a work package inside a later release; it is not part of Version 1.

**Execution 2026-10-10 (TK-1..TK-6, owner authorization in chat):** the legacy TICKETS-SERVICE is archived at `_cleanup_review/legacy-tickets-service-2026-10-10/` (copy verified identical; the active copy scheduled for deletion with a local `svn delete` — `_cleanup_review/` is SVN-ignored, so `svn move` was not possible; the owner commits) and its active references are removed (root `pom.xml`, `docker-compose.yml`, gateway route + correlation mapping, CP provisioning list / allow-lists / seed row `tickets` (TK-4: seed row removed, owner choice) / `ModuleCatalog`, Prometheus job, README); kept: `AttachmentModule.TICKETS` + the FILE-SERVICE bucket mapping (TK-5), the WebSocket-ticket feature; owner / operator: `.env` `TICKETS_PORT`, a live `svc-tickets-service` client, existing CP databases holding a `tickets` module row (TK-6). Validation: reactor unit build green except the ACCOUNT-BOOK 94 baseline (identical), CP unit 812 / 0, full CP IT 258 / 0 / 0 / 0 (WORK_LOG "Legacy TICKETS-SERVICE retired").

### 8.12 Revision 2026-10-10 (CD-35): backend first, all UI deferred to a final UI phase

**Source:** CD-35. **BD-9 is resolved:** the UI is deferred and no longer a backend blocker. Acceptance A6 ("the Company Super Admin … logs in") is verified at backend level by the default Keycloak login page (where the realm / client configuration supports it) or by automated API-level tests; the company login experience moves to the final UI phase. No task status changed.

**Backend-first order (business priority; every item still needs its readiness review and the owner's authorization):**
- **Stage A:** P06-T5 owner review.
- **Stage B — business workflows:** P06-T6 → PHASE_07 T1–T7 (order → manual payment verification → activation) → P06-T3 + P07-T8 (lifecycle, renewal) → PHASE_08 T2 / T4 / T5 / T6 / T10 (planner, executors, company realm, buckets, provisioning account) + P10-T3 / T10 (Super Admin identity lifecycle) → PHASE_09 T1–T4 (storage) → P10-T1 / T2 / T4 / T5 / T7 backend APIs (company users, predefined roles, console backend).
- **Stage C — authentication and security:** D3-10 (BD-6), P10-T14 (verified JWT only, legacy role checks), P10-T12 (gateway module-role check), P10-T15 + P11-T4 (service entitlement filter, Feign soft handling), P06-T6 (company status at the gateway), P09-T3 (FILE-SERVICE authorization).
- **Stage D — module integration:** P10-T8 + P11-T2 (directory), P10-T9 + P11-T3 (branding), P11-T8 compatibility checks per module; Product Owner Company entitlements per PO-1.
- **Stage E — backend end-to-end validation:** P12-T1 / T2 / T3 subset — create → databases → realm → Super Admin invite → login (default Keycloak page or API) → JWT / company context → roles → entitlements → module APIs → negative cases (unauthorized, invalid / expired token, cross-company) → provisioning / authentication failure paths → storage isolation.
- **Production readiness:** PHASE_11A, gates, backup / restore drill, runbooks.
- **Final UI phase:** see below.

**Technical note for default-login testing:** realm template v1 client `erp-web` (and `erp-portal`) is public, standard flow with PKCE S256, no direct-access grants, redirect URIs `${ERP_REDIRECT_URIS}` (`keycloak/realm-template/v1/clients/erp-web.json`). An interactive test through the default Keycloak page therefore needs a configured local redirect URI for the provisioned realm. Automated backend tests keep using test-only clients inside the local test realms (COMMON `LocalKeycloak`); no direct grant is added to the production template.

**Deferred UI deliverables (final UI phase; not implemented now):**

| ID | Deliverable | Final-phase dependencies |
|---|---|---|
| UI-1 | Company login experience: Company Login Code discovery (P03-T12 API), secure realm lookup, redirect to the approved authorization endpoint, callback, secure session / token handling (PKCE, refresh, logout), errors for inactive companies, invalid codes and failed authentication; never a user-supplied realm or issuer | P03-T12 discovery API (DONE), realm provisioning (P08-T5), Q-25 UI owner, redirect URIs per environment |
| UI-2 | Keycloak login-page configuration | Keycloak 25.0.6 only (CD-30); decide default page vs a realm login theme; company branding (if approved); login / invitation policy; redirect URIs and web origins; client authentication and OIDC flow settings; session, logout and error pages; theme deployment, version compatibility and rollback; tests across the platform realm and company realms; no secrets in source |
| UI-3 | Platform Admin screens (company lifecycle, catalog, plans, orders, payments, provisioning, grants / overrides) | the corresponding backend APIs (PHASE_04 / 05 / 06 / 07 / 08) |
| UI-4 | Company Admin screens: users, invitations, role assignment, profile / branding (CD-13) | P10-T2 / T3 / T5 / T7 / T9 |
| UI-5 | ERP-wide integration: entitlement-aware module navigation, permission-aware actions, session expiry, API error handling | P06-T6 runtime status, P10-T12 / T13 (permissions), module APIs |
| UI-6 | End-to-end UI tests | UI-1..UI-5 and a staging environment |

**Final UI phase plan (to be written when the backend readiness gates have passed):**
- inspect the actual frontend repository and stack (Q-25: owner and repository of the production ERP UI; the ACCOUNT-BOOK `erp-ui` is legacy and bound to `ERPSystem`);
- confirm the Keycloak configuration of UI-2;
- define the UI architecture for UI-3..UI-5 and the UI-6 tests.

Frontend visibility never replaces backend authorization.

### 8.13 BD-4 — governance exception for PHASE_07 / 08 / 09 / 10 task subsets (prepared CD-36 item 4; approved 2026-10-12 as a scoped exception, CD-37 item 3 — not a phase unlock)

**Status:** prepared 2026-10-12; **approved 2026-10-12 as a scoped exception, not a blanket authorization (CD-37 item 3)** — a listed task may only proceed through the conditions below (plan approval, dependency check, readiness review, explicit per-task implementation authorization); **no phase is unlocked and no task is authorized by this text.** Precedents: D3-4 (PHASE_03 subset), the PHASE_04 exceptions (T6-1 of 2026-10-06), the P02-T11 stage F subset.

**Scope — only these tasks (the P0 items of the V1 / backend-first register, §8.3 / §8.10 / §8.12):**

| Phase | Tasks in the exception | Explicitly outside the exception (stay LOCKED / HOLD / BLOCKED) |
|---|---|---|
| PHASE_07 | P07-T1 billing entities, T2 series / seller / billing policy / `EInvoiceProvider` NONE, T3 billing account, T4 order service, T5 confirmation (AWAITING_PAYMENT + PENDING subscription; proforma PDF HOLD), T6 manual payments + verification, T7 activation (invoice issue, ACTIVE, entitlements, FULFILLED) | T8 manual renewal (HOLD, BD-10), T9 credit notes / PDFs (BLOCKED, BD-5), T10 company read APIs (HOLD), T11 platform mail sender (V2) [*exception 2026-10-13, CD-63 G1: P07-T11 released from the V2 hold; text kept as written*] |
| PHASE_08 | P08-T2 planner from entitlements, T4 executors + registry integration, T5 realm provisioning (only after the BD-6 / D3-10 decision — CD-82 (7), reading A′: start after S0-2 item 6 is decided; DONE only after the D3-10 closure), T6 bucket provisioner, T10 provisioning-account runbook | T1 / T3 job schema + durable worker (BLOCKED, BD-7), T7 `ADD_MODULE` / `REPAIR` / upgrade jobs, T8 provisioning APIs, T9 reconciliation (HOLD) |
| PHASE_09 | P09-T1 registry bucket resolver (no lazy buckets), T2 exact tenant check + `uploadedBy` / `deletedBy`, T3 FILE-SERVICE authorization, T4 usage reserve / delta + outbox (`assertCanStore` caller) | T5 reconciliation (HOLD), T6 bucket policy / quota and T9 tenant-less purge removal (P1 — add only by a separate owner decision), T7 bucket import (Shivam, deferred), T8 ACCOUNT-BOOK files (BD-2) |
| PHASE_10 | P10-T1 entities, T2 `CompanyUserService` + seats, T3 Company Super Admin assignment (after BD-6), T4 permission registration + predefined role policy (needed by T5), T5 `RoleService` / assignments, T7 company console backend APIs (CD-13; backend only, CD-35), T9 branding API (P0 for the Product Owner Company, §8.10), T10 identity-sync test run (after BD-6), T12 gateway module-role check, T14 legacy role checks / unverified decoders, T15 service entitlement filter | T6 role-policy publisher and T13 permission evaluator (BLOCKED, BD-8), T8 company user directory (BD-2), T11 stale IT assertions (HOLD), T16 audit-endpoint permission (P1) |

**Conditions (each task, every time):** (1) the phase implementation plan of the task is owner-approved (PHASE_07 also needs BD-5 with its V1 scope); (2) a **dependency check** against the tracker and the task's plan section (in-phase and cross-phase, e.g. P07-T7 needs P06-T6 runtime publication, P08-T5 / P10-T3 / P10-T10 need the BD-6 decision, P09-T3 needs P10-T12, P10-T15 needs P11-T4) — a task whose dependencies are not DONE (or REVIEW where the owner accepts that) is not started; (3) a **read-only readiness review** with the owner's decisions recorded; (4) an **explicit implementation authorization** per task; (5) the task ends as REVIEW and only the owner sets DONE; (6) the phase stays LOCKED for every other task, the phase status in the tracker is changed only by the owner, and phase closure / gate reviews (G0–G8, master §22) are unchanged; (7) CD-35 (no UI work), CD-34 (Shivam / Support deferred) and every environment restriction stay in force; (8) the exception is revoked by any owner instruction and lapses with the phase's regular unlock.

**To take effect:** the owner approves this text (it would then be recorded as a decision-register entry and in the tracker phase rows of PHASE_07..PHASE_10 as "LOCKED — exception for <tasks>"). **Recorded 2026-10-12 (CD-37).** **What still needs separate approval:** (i) every listed task — its own readiness review and explicit implementation authorization; (ii) the phase implementation plans PHASE_07..PHASE_10 (DRAFT FOR OWNER REVIEW) — owner approval before the first task of a phase (PHASE_07 also BD-5 with the V1 scope); (iii) P08-T5 — the BD-6 / D3-10 closure (§8.14) before P08-T5 is set DONE and before any `CONFIGURE_CLIENTS` outside tests (CD-82 (7), reading A′; the A1–A6 work belongs to P08-T5); P10-T3, P10-T10 — additionally the BD-6 / D3-10 closure (§8.14), unchanged; (iv) P07-T7 — additionally P06-T6 DONE (runtime publication); (v) P09-T3 after P10-T12; P10-T15 after P11-T4 (PHASE_11, not in the exception). **What stays LOCKED (not in the exception):** P07-T8 / T9 / T10 / T11; P08-T1 / T3 (BD-7), T7, T8, T9; P09-T5, T6, T7, T8, T9; P10-T6, T8, T11, T13, T16; every task of PHASE_11 / PHASE_11A / PHASE_12 (P12 tasks keep the plan's own rules); PHASE_13 / PHASE_14 FUTURE.

### 8.14 BD-6 / D3-10 — signed JWT (JWKS) vs per-realm client secret for `erp-identity-admin` (CD-36 item 5; CD-37 item 4: preferred R1 design selected — D3-10 OPEN)

**Status:** analysis 2026-10-12 (read-only, code-inspected); **D3-10 stays OPEN; nothing is implemented** until the owner records the decision.

**Facts (verified in the repository):** the realm template client `keycloak/realm-template/v1/clients/erp-identity-admin.json` is confidential, service-account only, `fullScopeAllowed: false`, `clientAuthenticatorType: client-jwt`, `use.jwks.url: true`, `jwks.url: ${CP_JWKS_URL}`, RS256, and gets `realm-management` `manage-users` / `view-users` / `query-users` / `view-realm`; `RealmTemplateInvariants` enforces `client-jwt`; `RealmTemplate` requires `CP_JWKS_URL` to be an http(s) URI, but no production code supplies it (tests use a loopback placeholder). The CONTROL-PANEL `KeycloakAdminTokenProvider` authenticates every client (realm provisioner, platform identity admin, realm auditor) with a client secret from the environment and **fails closed for company realms** ("signed-JWT key management is the open owner decision D3-10"); the only fallback is test-only. CP has **no key pair, no JWKS endpoint and no `client_assertion` code** (Nimbus is already on the classpath for token validation). COMMON M2M clients use client secrets. The local IT `LocalKeycloakIdentityAdminIT` switches its realm to a client secret at runtime and adds the realm-management scope mapping the template lacks. **Template defects independent of the option:** `fullScopeAllowed: false` without a `realm-management` scope mapping (Keycloak answers 403 to the admin calls) and no user profile. Planning target: 50 companies = 51 realms (CD-18). Vault was removed (2026-10-01); generic secret storage is the PHASE_01 §6.11.3 "secret reference" design (`DB_ENCRYPTED` with an environment / KMS master key, or `SECRET_STORE`), choice open (Q-13). Keycloak is external, CP has no published port, and the gateway routes `/control-panel/**` to CP — a CP JWKS endpoint would be publicly reachable through the gateway unless excluded.

| Criterion | (a) Signed JWT (`private_key_jwt`), one CP key pair, public keys via JWKS URL | (b) Per-realm client secret |
|---|---|---|
| Secret material | one private key, held only by CP; Keycloak stores no shared secret, only fetches the public key | 51 shared secrets (one per realm), each held by Keycloak **and** by CP (encrypted at rest with a master key) |
| Exposure on use | the credential never travels; each token request carries a short-lived signed assertion (`jti`, `exp`) — replay window seconds | the secret is sent with every token request (TLS required) |
| Compromise of Keycloak's database / an export | no CP credential obtainable | every realm's secret obtainable |
| Compromise of CP | the private key gives admin access to every company realm | the stored secrets (and their master key) give the same — equal blast radius |
| Rotation | new key pair with a new `kid` published in the JWKS **before** use (overlap ≥ Keycloak's key-cache period), switch signing, remove the old key after the overlap — one operation for all realms, no Keycloak change | per realm: generate, set in Keycloak (admin API), store in CP, switch — 51 coordinated operations; failure leaves a realm unusable |
| Provisioning (P08-T5 `CONFIGURE_CLIENTS`) | template unchanged; supply `CP_JWKS_URL` | template, `RealmTemplateInvariants` and the token provider change; secret generation + storage per realm |
| New components | key loading (PKCS#8 file / keystore from the environment-managed secret mount), a JWKS endpoint, a `client_assertion` builder (Nimbus) | encrypted secret store (or `SECRET_STORE` integration), per-realm secret CRUD, rotation job |
| Network | Keycloak must reach the JWKS URL; the endpoint must be **internal only** (not through the public `/control-panel/**` route — a separate internal listener / path denied at the gateway, or a static file served on the internal network) | no Keycloak → CP path |
| Alternative within (a) | instead of a JWKS URL, the public certificate can be imported into each realm client at provisioning (`use.jwks.url = false`) — no network path, but rotation then touches every realm like (b) | — |
| Consistency | differs from the existing secret-based clients (only this client) | same as the other clients |
| Effort (estimate) | ~2–3 days incl. tests on the local Keycloak 25.0.6 (Q-26: signed JWT still to verify there) | ~2–3 days + the encrypted secret store (Q-13) if not yet available |

**Recommendation: (a) signed JWT with a JWKS URL** — the safer and more maintainable option at 51 realms: no shared secret at rest in Keycloak or CP's database, no credential on the wire, one rotation for all realms, the template / invariants / PHASE_01 design ("no per-realm secret is stored") stay as they are. **Conditions:** (1) the JWKS endpoint is reachable only on the internal network (explicitly denied at the gateway and covered by a test); (2) the private key comes from an owner-managed secret mount / environment (never the DB, a log or the repository), with a documented `kid` scheme; (3) rotation runbook: planned rotation (e.g. every 6–12 months) with overlap, emergency rotation on suspected compromise (publish new key, switch, remove old immediately — accepting a short refusal window), alerting on assertion failures; (4) verify `client-jwt` with JWKS on the local Keycloak 25.0.6 first (Q-26); (5) fix the two template defects (realm-management scope mapping, user profile) in the same change, with a template version bump. **Fallback** if the internal network path cannot be provided in time: (a) with the imported certificate (no JWKS URL), accepting per-realm rotation. (b) is acceptable only as a time-boxed R1 measure with an encrypted secret store and a documented migration to (a).

**Status update 2026-10-12 (CD-37):** option (a) — signed JWT with an internal-only JWKS endpoint — is **selected as the preferred R1 design**; **D3-10 stays OPEN** until these **acceptance criteria** are evidenced and the owner records the closure: (A1) the CP private key is loaded only from an owner-managed secret mount / environment (never the database, a log, the repository or a test report); startup fails closed without it; a documented `kid` scheme; (A2) a rotation runbook (planned rotation with an overlap ≥ Keycloak's key-cache period, emergency rotation, verification steps) and a test of a key rollover on the local Keycloak; (A3) the JWKS endpoint is reachable only on the internal network — denied at the gateway (`/control-panel/**` must not expose it) and covered by a gateway test; the Keycloak → CP network path is documented for each environment (BD-12); (A4) `client-jwt` with a JWKS URL works on the **local** Keycloak 25.0.6 (127.0.0.1:8081 only — never the shared Keycloak 192.168.5.15) for token issuance and the company-realm admin calls; (A5) the template defects are fixed with a template version bump (`realm-management` scope mapping, user profile) and the template invariants stay green; (A6) a negative test: an assertion signed with an unknown key / expired assertion is refused. The implementation belongs to a separately authorized task (P08-T5 / P10-T3 / P10-T10 path, BD-4 list).

**Operational implications (both options):** the CP credential is a platform-wide admin credential for company identities — restrict who can read it, rotate after any restore (RUNBOOK §78), and alert on failed client authentications.

**S0-2 item 6 decided 2026-10-16 (CD-82 (8)):** secret-mount PKCS#8 key files (fail-closed outside `test`); `kid` `cp-ia-<yyyyMMdd>-<n>`; planned rotation every 12 months; overlap ≥ the measured Keycloak key-cache behaviour, minimum 1 hour (local Keycloak 25.0.6, 2026-10-16: unknown `kid` → JWKS refetch at most about every 10 s; a removed key stays accepted from the cache until a refetch, the idle expiry — configured max-idle 3 600 000 ms, not observed — or an admin clear-keys-cache); emergency rotation clears the keys cache in every realm; rotation after restore; manual in R1 with verification and rollback; JWKS under CP `/api/v1/internal/` (proposed `/api/v1/internal/identity-admin/jwks`), Keycloak-only network path, gateway-denied; production base URL = deployment prerequisite (BD-12). Nothing implemented; **D3-10 stays OPEN** (A1–A6 + explicit owner approval).
