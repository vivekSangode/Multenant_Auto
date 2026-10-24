# PHASE 11 — Microservice Integration, Compatibility and Data-Import Tooling

| Item | Value |
|---|---|
| Master architecture | [SAAS_ARCHITECTURE_MASTER.md](SAAS_ARCHITECTURE_MASTER.md) §15.3–15.4, §20 |
| Design source | [PHASE_01 §6.8.4 (import stages), §6.8.7 (compatibility changes)](PHASE_01_SAAS_FOUNDATION.md) |
| Depends on | [PHASE_09](PHASE_09_FILE_STORAGE_ISOLATION_AND_USAGE.md) (module buckets, import tool), [PHASE_10](PHASE_10_COMPANY_ADMIN_ROLES_AND_PERMISSION_ENFORCEMENT.md) (directory, branding API, permissions), [PHASE_03](PHASE_03_IDENTITY_KEYCLOAK_AND_COMPANY_CONTEXT.md) |
| Enables | PHASE_13, PHASE_12 |
| Status | **PENDING** (the integrations exist with global flags; no per-company configuration; no import tooling) |

> **Revision 2026-10-03 (v5, decision register):**
> - **CD-05:** the new production platform starts with a new CP database and new company module databases. The existing production database is not the target, and its data is **not deleted or modified**. Importing existing data is a **future, controlled** activity: the toolkit below works only on owner-run exports and is run only with the owner's approval.
> - **CD-03:** the toolkit loads with the environment DB credentials; there is no per-company DDL account.
> - **CD-01:** objects go into the module buckets.
> - **CD-06:** identities are linked per realm.
> - **CD-16 (Q-47 closed):** Shivam's legacy identity migration is a **separate tool** (`identity-migrate`). It is not part of provisioning and not part of the business-data toolkit commands; it shares only the operator-run, owner-export-only safety rules.
> - **CD-14:** legacy client roles (`resource_access`) and HRMS/PAYROLL realm roles are mapped to the new company **realm roles**. The legacy role readers are replaced (PHASE_10).

---

## 1. Objective

Make every microservice behave correctly **per company**, keep the existing (Shivam) behaviour intact, and provide the generic tooling to import an existing company's data:

1. **Per-company integration switches.** They replace the global `*_ACCOUNT_SYNC_ENABLED` flags. An integration is active only when both modules are entitled **and** the company setting is on.
2. **Company-scoped user directory** in every service. It replaces the realm-wide legacy user lookups. The legacy control-plane endpoints are then removed.
3. **Company branding and settings** in every service. They replace the hard-coded Shivam names, logos, mail senders, mail addresses, letter templates and module URLs.
4. **Portal user provisioning** according to Q-14, company-scoped and not through the legacy API.
5. **Soft handling** of non-mandatory cross-module calls when the target module is not entitled.
6. **Generic company data-import toolkit**, used for any company created with `onboarding_type=IMPORT_EXISTING_DATA`, Shivam first: inventory, schema diff gate, data-only importer, integrity and reconciliation checks, attachment and object import, identity linking.
7. **Compatibility register.** Every change Shivam needs is listed, implemented and tested here, so its data, logins, workflows and integrations keep working. **No existing functionality is removed** without the owner's confirmation.

## 2. Existing implementation relevant to this phase

> **P02-T11 note (stage G, 2026-10-05):** the tables of this section describe the code as inspected on 2026-10-03. P02-T11 stages A–F (2026-10-04/05, REVIEW) have since replaced the organization tenancy: the CP organization / customer tier (`Customer`, `Organization`, `OrganizationDatabase`, `OrganizationUser`, `OrganizationUserBusinessRole`, `OrganizationQuotaService`, `CompanyOrganizationService`, `OrganizationGroupProvisioner`, `@OrganizationScoped`, `PlatformOrgAdminController`, …) is deleted (stage C); `InternalOrganizationController` → `InternalCompanyController` (`/api/v1/internal/platform/companies/...`), `OrganizationEntitlementSyncListener` → `CompanyEntitlementSyncListener`, `CustomerUser` → `CompanyUser` (`tbl_company_user`), `CustomerUsage` → `CompanyUsage`; runtime `TenantContext.companyCode`, header `X-Company-Code`, Redis `COMPANY_MODULES:<CMP_n>`, resource names `cmp_<n>_…` / `cmp-<n>-…` (stages A / B); gateway `CompanyContextFilter` / `CompanySelection`, company from the verified `tenant_id` claim, no `Active-Org` (stage D). Read old names in this section as these replacements.

| Component | Location | Notes (`CODE-INSPECTED`) |
|---|---|---|
| ACCOUNT-BOOK integrations | STORE `client/accountBookClient/*`, PRODUCTION `AccountBookClient`, `AccountBookTransferOrderClient`, MINING `AccountBookClient`, CRM `AccountBookSalesOrderClient`; `integration.account-book.enabled` from `STORE_/PRODUCTION_/MINING_ACCOUNT_SYNC_ENABLED` | Global flags. CRM's flag is bound to nothing. STORE inward/outward calls are unconditional. |
| User lookups | `lookup/UserLookupService.java` (QC, R&D, STORE, MINING, PRODUCTION), `ControlPanelClient` (CRM, STORE, MINING, PRODUCTION, QC, R&D, COMPLIANCE, ATTENDANCE), LOGISTICS `RoleClient` | Call the CP legacy `/api/keycloak/**`, `/api/users/**`, `/api/user-roles/**`, which return realm-wide users |
| Branding | CRM `email/config/EmailProperties.java`; QC `utils/PdfReportService.java`, `utils/SalesOrderMailService.java`; R&D `mail/SalesOrderMailService.java`; HRMS `templates/letters/*.html`; ATTENDANCE `reports/json_Monthly_Attendance_Report.jrxml`; COMPLIANCE `templates/compliance_email_template.html`; static logos in CRM/QC/R&D | Hard-coded Shivam values |
| Module URLs and settings | QC `application.yml` module URLs; ATTENDANCE `FACE_RECOGNITION_URL`; mail settings in `.env` | Global |
| Portal users | CRM `service/impl/CustomerPortalUserServiceImpl.java` | Creates users through the CP legacy `/api/keycloak/users` with `customer=true` |
| Legacy inert switches | compose `ACCOUNT_PRODUCTION_ENABLE` / `ACCOUNT_BOOK_DB_NAME` (read by no code); ACCOUNT-BOOK `accounting.legacy-single-db-runners`; COMPLIANCE `erp.multitenancy` (no consumer) | Inert |
| Manual legacy scripts | R&D `db/manual/2026-07_*`, HRMS `db/manual/2026-08_*`, QC delete script, `2026-10_ids_to_auto_increment.sql` (10 services), ACCOUNT-BOOK `db/migration/production_safe_*.sql` | Legacy single-DB maintenance; reference for the schema diff gate |
| Import tooling | — | None exists |

## 3. Existing completed work

| Item | Status |
|---|---|
| Cross-module workflows (Feign) used by Shivam today | COMPLETED as business functionality (preserved) |
| Per-company configuration | PENDING |
| Import tooling | PENDING |

## 4. Reusable components

- The existing Feign clients and the integration services (only the switch source changes).
- `IntegrationProperties` (replaced by a resolver).
- The PHASE_10 `CompanyUserDirectory` and `CompanyBrandingResolver`.
- The PHASE_09 `BucketImportTool`.
- The PHASE_02 schema-parity diff approach (reused by the schema diff gate).
- The PHASE_08 provisioning mode `EMPTY_FOR_IMPORT`.

## 5. Work requiring modification or redesign

| Item | Change |
|---|---|
| Global integration flags | `CompanyIntegrationResolver` (COMMON) reads `TENANT_SETTINGS:<companyCode>` (published from `tbl_company_setting`) **and** the entitlements. STORE→ACCOUNT-BOOK stays always-on (a mandatory dependency). |
| Legacy user lookups | Replaced by `CompanyUserDirectory`. Afterwards the CP `restcontroller/KeycloakController`, `KeycloakUserController`, `RoleController`, `service/Keycloak*` and `utils/JwtRoleExtractor` are removed (after every caller has migrated and passed the regression). |
| Hard-coded branding and addresses | `CompanyBrandingResolver` + company settings |
| Portal user creation | New CP internal API `POST /api/v1/internal/platform/companies/{companyCode}/portal-users` (Q-14 default: a portal client in the **company's own realm**; portal users are created in that realm; never in another company's realm) |

## 6. New development required

### 6.1 Per-company integrations
- Setting keys:
  - `integration.store-to-account-book.enabled` (fixed `true`; informational only);
  - `integration.production-to-account-book.enabled`;
  - `integration.mining-to-account-book.enabled`;
  - `integration.crm-to-account-book.enabled`.
- The defaults for a new company come from a platform setting. For Shivam they are set to today's `.env` behaviour (PHASE_01 §6.8.7).
- `CONDITIONAL` dependencies in `tbl_module_dependency` reference these keys (PHASE_01 §6.2 C).

### 6.2 Directory, branding, settings adoption
- Every service listed in §2 migrates to the directory and the branding resolver.
- Each migrated module gets tests for "company A sees only company A users".

### 6.3 Soft handling of non-mandatory calls
- A COMMON Feign `ErrorDecoder` maps 403 `MODULE_NOT_ENTITLED` to `ModuleNotEntitledException`.
- The integration services catch it for RECOMMENDED and CONDITIONAL targets. MANDATORY targets are guaranteed to be entitled.

### 6.4 Generic company data-import toolkit
The toolkit is an operator-run module (CLI profile in the control plane or a separate `DATA-IMPORT-TOOLKIT` module; decided at implementation). It records everything in `tbl_data_import_run` / `tbl_data_import_check` (PHASE_01 §6.2 H).

| Command | Function |
|---|---|
| `inventory` | Reads the owner-supplied export manifests and the source checksum report: tables, row counts, checksums, object counts and bytes |
| `checksum-script` | Emits the read-only SQL script the **owner** runs on the source side (`COUNT(*)` and a per-table checksum over primary-key ranges) |
| `diff` | Schema diff gate: legacy dump schema vs the target Liquibase schema per module → MATCH / MAPPABLE / BLOCKER |
| `load --module` | Data-only load into `cmp_<n>_<module>_db` with the environment DB credentials (CD-03); strict SQL mode; FK checks off only during the load; excludes the legacy `*_seq` tables; refuses any target outside the company's registry |
| `integrity` | FK orphan queries; `AUTO_INCREMENT` reset above `MAX(id)` |
| `reconcile` | Target counts and checksums vs the source report → `tbl_data_import_check` |
| `attachments` | Imports `attachment_db` rows for the agreed modules into `cmp_<n>_attachment_db`; rewrites `bucket` to the module bucket `cmp-<n>-<module>-bucket`; keys unchanged |
| `objects` | Copies the owner-supplied mirror of each legacy module bucket into the matching `cmp-<n>-<module>-bucket` (PHASE_09 `BucketImportTool`), verifying size and ETag |
| `link-identities` | After the separate identity migration (below), links the migrated users of Shivam's new realm to `tbl_identity` (realm + `sub`) and `tbl_company_user`, and applies the old → new `sub` mapping to imported business data if the ids changed. No role assignment is stored in the CP. |
| `reset-target` | Owner-approved and audited. Allowed **only** while the company is `AWAITING_DATA_IMPORT`, after a backup, for that company's module databases only. Used before a retry. **Never automatic.** |
| `report` | Produces the verification report (counts only, no data values) |

**Safety:**
- The toolkit has **no connection settings for source hosts**. It reads only files from an owner-provided path.
- It refuses any host in a deny-list that contains `192.168.5.52` and `192.168.5.53`.

### 6.5 Compatibility register (Shivam)

| # | Compatibility item | Implementation |
|---|---|---|
| C-1 | Existing logins keep working as far as technically possible | Separate identity migration into Shivam's new standard realm (CD-16): password hashes from the owner-run `kc.sh export` if they verify in the rehearsal; otherwise a controlled activation and password-reset flow. Users enter Shivam's Company Login Code (CD-15). |
| C-2 | The existing UI(s) work through the gateway | Client scope and audience (PHASE_03); UI changes listed after Q-25 |
| C-3 | Business roles equivalent to today's legacy client roles | The owner-approved mapping table (legacy client and realm roles → new company realm roles, CD-14), applied by the identity migration; the roles live in Shivam's new realm |
| C-4 | Branding, mail senders and addresses unchanged | `tbl_company_profile` and settings of Shivam's company, taken from the current hard-coded values |
| C-5 | Integrations behave as today | Company settings (§6.1) |
| C-6 | All legacy data retained | The schema diff gate: legacy-only columns are preserved with reviewed changesets, never dropped |
| C-7 | Files and attachments | Bucket rewrite and object copy; ACCOUNT-BOOK local files into the bucket (PHASE_09) |
| C-8 | Attendance data | Owner export from the protected host; imported like the other modules |
| C-9 | No removal of business code | Legacy runners and inert switches are removed only after owner confirmation |

## 7. Exact services, files and database areas involved

| Area | Files |
|---|---|
| COMMON-LIBRARY | new `integration/CompanyIntegrationResolver`, Feign `ErrorDecoder` |
| Domain services | Every integration client and service listed in §2; the user-lookup sites; the branding sites; QC/ATTENDANCE settings; CRM portal user service |
| CONTROL-PANEL | The portal-user internal API; removal of the legacy controllers and services; the toolkit (or a separate module) |
| Databases | `control_panel_db` (`tbl_company_setting`, `tbl_data_import_*`); company databases (import targets only) |

## 8. Proposed schema changes

None beyond PHASE_01 §6.2 (`tbl_company_setting`, `tbl_data_import_run`, `tbl_data_import_check`). Company databases receive reviewed changesets only if the schema diff gate requires preserving legacy columns.

## 9. API and integration requirements

| API | Notes |
|---|---|
| `POST /api/v1/internal/platform/companies/{companyCode}/portal-users` | CRM caller; `azp` allow-list |
| Settings publication | `TENANT_SETTINGS:<companyCode>` (Redis, rebuildable) |
| Removed | `/api/keycloak/**`, `/api/users/**`, `/api/user-roles/**` (after the migration) |

## 10. Dependencies on other phases

| Phase | Relationship |
|---|---|
| PHASE_10 | Prerequisite: directory, branding API, permissions, role mapping targets |
| PHASE_09 | Prerequisite: bucket tools |
| PHASE_08 | Prerequisite: `EMPTY_FOR_IMPORT` |
| PHASE_12 | Executes the import |

## 11. Security and tenant-isolation requirements

1. Integrations never cross companies. The resolver uses the current tenant context only.
2. Directory lookups are company-scoped, and a test proves it for every migrated service.
3. The toolkit writes only into the registered resources of the target company (registry check before every write), using the environment DB credentials, and is audited.
4. **No source-host access; no protected-host access.**

## 12. Ordered implementation tasks

| # | Task | Status |
|---|---|---|
| 1 | `CompanyIntegrationResolver` + settings publication + adoption in STORE, PRODUCTION, MINING, CRM | PENDING |
| 2 | Directory adoption in every service; remove the legacy CP endpoints | PENDING |
| 3 | Branding and settings adoption in every service | PENDING |
| 4 | Feign `ErrorDecoder` + soft handling | PENDING |
| 5 | Portal user internal API + CRM adoption (portal users in the company realm, client `erp-portal`; Q-14 decided, CD-24) | PENDING |
| 6 | Data-import toolkit (all commands) + tests on synthetic dumps | PENDING |
| 7 | Role-mapping table template + owner approval process | PENDING |
| 7a | **Identity migration tool** (`identity-migrate`, separate from provisioning and the data toolkit): reads the owner-run `kc.sh export --users realm_file` of `ERPSystem` from an owner-provided path; filters Shivam's users; maps the roles (CD-14); keeps the user ids or emits the old → new `sub` map; imports into the existing `co-<n>` realm with the credential hashes; dry-run and report modes; no connection to legacy hosts | PENDING |
| 8 | Compatibility register tests (C-1..C-9) on a rehearsal company | PENDING |
| 9 | Removal of the legacy inert switches and runners after owner confirmation | PENDING |

## 13. Acceptance criteria

1. With the integration switch off for a company, PRODUCTION does not call ACCOUNT-BOOK for that company, and other companies are unaffected.
2. No service calls the legacy CP user endpoints (a grep check plus runtime metrics). The endpoints are removed.
3. A company's emails and PDFs show that company's branding. A new company never shows Shivam branding.
4. The toolkit imports synthetic dumps with 100 % reconciliation, rejects schema BLOCKERs, refuses targets outside the company and refuses protected hosts.
5. Every compatibility item C-1..C-9 has a passing test on the rehearsal company.

## 14. Testing and validation checklist

- [ ] Integration tests per switch and per company.
- [ ] Directory isolation tests per service.
- [ ] Branding rendering tests (mail, PDF, letter templates).
- [ ] Toolkit unit and IT tests (synthetic legacy schemas including drift cases).
- [ ] Full module regression with the entitlement and permission enforcement on.

## 15. Risks and rollback considerations

| Risk | Mitigation |
|---|---|
| Behaviour drift for Shivam after moving to per-company settings | The settings are initialized from today's values; regression on the rehearsal company |
| Missed hard-coded values | A grep inventory (`shivam`, `induscorp`, logo names) as a CI check |
| Toolkit misuse | Target checks, audit, owner approval for `reset-target` |

**Rollback:** the resolver falls back to the platform default setting when a company has no value. The legacy endpoints are removed last, after their callers are verified. Toolkit actions only affect `AWAITING_DATA_IMPORT` companies.

## 16. Current status

| Item | Status |
|---|---|
| Existing cross-module workflows | COMPLETED (preserved) |
| Per-company integrations, directory, branding | PENDING |
| Portal user provisioning | PENDING (Q-14 decided) |
| Data-import toolkit | PENDING |
| Compatibility register | PENDING |
| **Phase** | **PENDING** |
