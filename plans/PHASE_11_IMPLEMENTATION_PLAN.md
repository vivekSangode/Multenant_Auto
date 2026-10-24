# PHASE 11 — Implementation Plan (Microservice Integration, Compatibility and Data-Import Tooling)

| Item | Value |
|---|---|
| Phase document | [PHASE_11_MICROSERVICE_INTEGRATION_AND_COMPATIBILITY.md](../PHASE_11_MICROSERVICE_INTEGRATION_AND_COMPATIBILITY.md) (objective, acceptance §13). This plan **adds no decision**. |
| Design sources | PHASE_01 §0 (CD-01, CD-03, CD-05, CD-06, CD-14, CD-16, CD-23, CD-24 Q-14 / Q-41, CD-26, CD-27), §6.8 (Shivam: three separate activities, import and cutover), §6.2 A (`tbl_company_setting`) and H (`tbl_data_import_*`); master §15.4 (conditional integrations) |
| Status | **DRAFT FOR OWNER REVIEW.** PHASE_11 is LOCKED until PHASE_03, PHASE_09 and PHASE_10 are DONE. |
| Author / date | agent (Claude Opus 5.5), 2026-10-03 |
| Evidence | Local code inspection (PHASE_11 §2 list re-checked): ACCOUNT-BOOK clients in STORE / PRODUCTION / MINING / CRM with global `integration.account-book.enabled`; `UserLookupService` / `ControlPanelClient` / LOGISTICS `RoleClient` calling CP `/api/keycloak/**`, `/api/users/**`, `/api/user-roles/**`; hard-coded branding sites; CRM `CustomerPortalUserServiceImpl`; QC `application.yml` module URLs (`http://192.168.5.15:…` literals, removed in P03-T7). |
| Data safety | **No import is executed in this phase.** The toolkit and the identity-migration tool are built and tested on **synthetic** data only. Real imports (Shivam) are PHASE_12 activities with owner-supplied exports; nothing ever connects to `192.168.5.52/53` or any legacy host. |
| CP schema | CD-27 (entities); company DBs: tenant Liquibase (reviewed changesets only if the diff gate requires). |

---

## 1. Summary

1. **Per-company integrations:** `CompanyIntegrationResolver` (entitlement **and** `TENANT_SETTINGS` switch) replacing the global `*_ACCOUNT_SYNC_ENABLED` flags; STORE→ACCOUNT-BOOK always on (mandatory).
2. **Directory, branding and settings adoption** in every service; legacy CP user endpoints removed after all callers move.
3. **Soft handling** of non-mandatory Feign targets (`ModuleNotEntitledException`).
4. **Portal users** in the company's own realm (`erp-portal`, Q-14) through a CP internal API.
5. **Data-import toolkit** (inventory, checksum script, diff gate, load, integrity, reconcile, attachments, objects, link-identities, reset-target, report) and the separate **`identity-migrate`** tool; role-mapping template + approval process.
6. **Compatibility register C-1..C-9** proven on a rehearsal company; removal of inert legacy switches/runners after owner confirmation.

Size estimate: 10 tasks; about 120–160 changed files (adoption touches ~12 services) + two new tool modules; about 40 test classes.

---

## 2. Preconditions

| # | Precondition | Tracker |
|---|---|---|
| 1 | PHASE_10 (directory, branding resolver, permissions, realm role targets), PHASE_09 (`BucketImportTool`), PHASE_08 (`EMPTY_FOR_IMPORT`), PHASE_03 (portal client in the template) DONE | phases |
| 2 | Q-25 (UI owner) for C-2 UI changes | S0-6 |
| 3 | Owner-approved legacy → realm role mapping table (T7 produces the template) | owner action |
| 4 | Synthetic legacy dumps for tests are produced by the agent from the **repository's** schema (changelogs / entities), never from real data | — |

---

## 3. Owner decisions / clarifications

| ID | Question | Options | Recommendation |
|---|---|---|---|
| **D11-1** (delegated: "decided at implementation", PHASE_11 §6.4) | Where do the toolkit and `identity-migrate` live? | (a) New Maven modules `DATA-IMPORT-TOOLKIT` and `IDENTITY-MIGRATION-TOOL` (Spring Boot CLI, no web, not deployed as services). (b) CLI profiles inside CONTROL-PANEL. | **(a)**: no bulk-load code or Keycloak-import credentials in the running control plane; separate artifacts that operators run on demand; they call CP internal APIs for registry checks and write audit rows through them. |
| **D11-2** (report) | Branding vs CD-23 / Q-41: the company profile `mail_*` fields are **display defaults**; business email for further companies stays disabled until PHASE_13; Shivam keeps the interim global sender. | T3 moves names/logos/addresses into the profile but does **not** add any new sending path. | As described; no decision change. |

---

## 4. Task order

```text
T4 Feign ErrorDecoder ─> T1 integration resolver + settings publication ─> T2 directory adoption ─> T3 branding/settings adoption ─> T5 portal users
T7 role-mapping template ─> T7a identity-migrate (synthetic realm export) ─> T6 data-import toolkit (synthetic dumps) ─> T8 compatibility register on a rehearsal company ─> T9 legacy switch/runner removal (owner confirmation)
```

---

## 5. Task details

### T4 — Feign `ErrorDecoder` + soft handling (P11-T4)

| Item | Detail |
|---|---|
| Files | COMMON `client/ModuleAwareErrorDecoder` (403 `MODULE_NOT_ENTITLED` → `ModuleNotEntitledException`), registered for all Feign clients; integration services catch it for RECOMMENDED / CONDITIONAL targets (log + skip); MANDATORY targets propagate (they are always entitled by rule 1 of master §11.2). |
| Tests | Non-entitled optional target → user operation succeeds; mandatory target error → propagated. |

### T1 — `CompanyIntegrationResolver` + settings publication (P11-T1)

| Item | Detail |
|---|---|
| Files | CP `platform/company/setting/{CompanySettingService, CompanySettingsPublisher}` → `TENANT_SETTINGS:<CMP_n>` (Redis, rebuildable; after commit; TTL + resync); platform setting defaults for new companies; COMMON `integration/CompanyIntegrationResolver.isEnabled(key)` = entitlement of both modules **and** the company switch (fallback: platform default); adoption in STORE (fixed true, informational), PRODUCTION, MINING, CRM (the unbound CRM flag gets its key `integration.crm-to-account-book.enabled`). `tbl_module_dependency` CONDITIONAL rows reference the keys. |
| Tests | Switch off for company A → PRODUCTION does not call ACCOUNT-BOOK for A; company B unaffected (acceptance 1). |

### T2 — Directory adoption; remove legacy CP endpoints (P11-T2)

| Item | Detail |
|---|---|
| Work | Replace `UserLookupService` (QC, R&D, STORE, MINING, PRODUCTION), `ControlPanelClient` user calls (CRM, STORE, MINING, PRODUCTION, QC, R&D, COMPLIANCE, ATTENDANCE) and LOGISTICS `RoleClient` by `CompanyUserDirectory`; then remove CP `KeycloakController`, `KeycloakUserController`, `RoleController`, `service/Keycloak*`, `JwtRoleExtractor` (owner approval), after every caller passes its regression. |
| Tests | Per service: company A sees only A users; grep + runtime metric: no call to the legacy endpoints (acceptance 2). |

### T3 — Branding and settings adoption (P11-T3)

| Item | Detail |
|---|---|
| Work | Replace hard-coded values in CRM `EmailProperties`, QC `PdfReportService` / `SalesOrderMailService`, R&D `SalesOrderMailService`, HRMS letter templates, ATTENDANCE `json_Monthly_Attendance_Report.jrxml`, COMPLIANCE email template, static logos — by `CompanyBrandingResolver`; QC module URLs and ATTENDANCE `FACE_RECOGNITION_URL` → company settings or service discovery. D11-2: no new mail-sending path. Grep CI check for `shivam`, `induscorp` and the logo file names in `src/main` (allow-list only for the Shivam company seed values used by the rehearsal). |
| Tests | Mail / PDF / letter rendering per company; a new company never shows Shivam branding (acceptance 3). |

### T5 — Portal users in the company realm (P11-T5)

| Item | Detail |
|---|---|
| Work | CP `POST /api/v1/internal/platform/companies/{companyCode}/portal-users` (CRM caller, `azp` allow-list): creates the portal user in that company's realm with client `erp-portal` (Full Scope Allowed off, portal roles only; Q-14); CRM `CustomerPortalUserServiceImpl` switches to it; gateway: the legacy separate customer-realm issuer (D3-3) is removed after the portal routes run on `erp-portal` tokens. |
| Tests | Portal user exists only in the company realm; portal token limited to `/portal/**`; legacy `/api/keycloak/users` no longer called. |

### T7 — Role-mapping template + approval process (P11-T7)

| Item | Detail |
|---|---|
| Output | `plans/PHASE_11_ROLE_MAPPING_TEMPLATE.md`: every legacy client role (`resource_access` of `api-gateway`: `CRM_SUPERADMIN`, `IT_MANAGER`, `RND_MANAGER`, `COMPLIANCE_*`, …) and HRMS/PAYROLL realm role (from the code readers, not from the production realm) → target realm role(s) (CD-14), with an empty "owner approved" column. The owner fills/approves it; the identity migration refuses unmapped roles. |

### T7a — `identity-migrate` tool (P11-T7a)

| Item | Detail |
|---|---|
| Module | `IDENTITY-MIGRATION-TOOL` (D11-1). Input: the owner-run `kc.sh export --users realm_file` JSON from an owner-provided **local path**; no network access to any legacy host; deny-list `192.168.5.52`, `192.168.5.53`. |
| Commands | `inspect` (counts, roles found), `filter --company` (Shivam's users by the agreed rule), `map-roles` (T7 table; unmapped → stop), `plan` / `dry-run` (report only), `import` into the **existing** `co-<n>` realm via the Admin API with credential hashes (password-hash algorithm verified in the rehearsal), `sub-map` (keep ids if possible; else emit old → new `sub` CSV for `link-identities`), `report`. |
| Tests | Synthetic realm export (generated users, hashes of known test passwords) imported into Testcontainers Keycloak; logins with the test passwords work; unmapped role stops the run. |

### T6 — Data-import toolkit (P11-T6)

| Item | Detail |
|---|---|
| Module | `DATA-IMPORT-TOOLKIT` (D11-1). Commands of PHASE_11 §6.4. Reads only files from an owner-provided path; writes only into the **registered** resources of the target company (CP registry check before every write), with the environment DB credentials (CD-03); company must be `AWAITING_DATA_IMPORT`; every run/phase/check recorded through the CP internal API into `tbl_data_import_run` / `tbl_data_import_check`; `IMPORT_RUN` sequence (D-5 note: seeded with PHASE_11). |
| Gates | `diff` uses the parity method (PHASE_02 T7) on the dump schema vs the target Liquibase schema: MATCH / MAPPABLE / BLOCKER — a BLOCKER stops that module; legacy-only columns need reviewed changesets (C-6), never dropped. `load`: strict SQL mode, FK checks off only during the load, excludes `*_seq` tables. `reset-target`: owner-approved, audited, only in `AWAITING_DATA_IMPORT`, after a backup, that company's module DBs only. Refuses protected hosts (deny-list on the configured `DB_HOST`). |
| Tests | Synthetic dumps generated from the repository schema (with drift cases: extra column, missing column, type change); 100 % reconciliation; BLOCKER rejected; target outside the company refused; protected-host DB_HOST refused (acceptance 4). |

### T8 — Compatibility register tests on a rehearsal company (P11-T8)

| Item | Detail |
|---|---|
| Work | A rehearsal company created through the standard flow with `onboarding_type=IMPORT_EXISTING_DATA` on the local/staging stack, loaded with **synthetic** data shaped like Shivam's modules; one test per C-1..C-9 (C-1 with the synthetic identity import; C-8 attendance with a synthetic export). Real Shivam data only in PHASE_12 rehearsal with owner exports. |
| Tests | C-1..C-9 green (acceptance 5). |

### T9 — Removal of inert legacy switches and runners (P11-T9)

| Item | Detail |
|---|---|
| Work | After owner confirmation only: compose `ACCOUNT_PRODUCTION_ENABLE`, `ACCOUNT_BOOK_DB_NAME`; ACCOUNT-BOOK `accounting.legacy-single-db-runners`; COMPLIANCE `erp.multitenancy`; the manual legacy scripts stay as reference for the diff gate (moved, not deleted, only with approval). |

---

## 6. Test plan summary

| Level | Coverage | Acceptance (PHASE_11 §13) |
|---|---|---|
| Integration | switches per company | 1 |
| Isolation | directory per service | 2 |
| Rendering | branding mail/PDF/letters | 3 |
| Tool ITs | toolkit on synthetic dumps; identity migration on a synthetic export | 4 |
| Rehearsal | C-1..C-9 | 5 |
| Regression | full module regression with entitlement + permission enforcement on | — |

---

## 7. Risks

| Risk | Mitigation |
|---|---|
| Shivam behaviour drift | Settings initialized from today's values (PHASE_01 §6.8.7); rehearsal |
| Missed hard-coded values | Grep CI check |
| Toolkit misuse | Registry target checks, audit, `AWAITING_DATA_IMPORT` only, owner approval for `reset-target`, deny-list |
| Password hashes do not verify after migration | Rehearsal decides; fallback activation / reset flow (C-1) |

---

## 8. Documentation findings

| # | Finding | Handling |
|---|---|---|
| F-1 | Toolkit location left to implementation | D11-1 recommendation (separate modules) |
| F-2 | Branding `mail_*` fields vs CD-23 / Q-41 | D11-2 (display only) |

---

## 9. Definition of done (PHASE_11)

All PHASE_11 §13 acceptance criteria proven on synthetic data and a rehearsal company; every task row REVIEW with evidence; owner sets DONE. The real Shivam import is a PHASE_12 activity.
