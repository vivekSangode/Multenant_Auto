# PHASE 02 — Implementation Plan (CP Schema and Tenancy)

| Item | Value |
|---|---|
| Phase document | [PHASE_02_CP_SCHEMA_AND_TENANCY_IMPLEMENTATION.md](../PHASE_02_CP_SCHEMA_AND_TENANCY_IMPLEMENTATION.md) (objective, acceptance criteria §13). This plan **adds no decision**. It turns PHASE_02 into an ordered, file-level work list. |
| Design sources | [PHASE_01 §0](../PHASE_01_SAAS_FOUNDATION.md) (CD-01..CD-27, authoritative), PHASE_01 §6.1 (naming), §6.2 A/B/F/H (columns), §6.4 (migration strategy), §6.5.2 (realm registry), §6.6 (routing); master §7.1 (company lifecycle); [developer guide](../SAAS_TARGET_ARCHITECTURE_DEVELOPER_GUIDE.md) §3, §5, §9, §23 |
| Status | **Current (2026-10-07): executed — PHASE_02 DONE** (tasks T0, T2–T9, T11 DONE; T1 / T10 DEFERRED to PHASE_11A, CD-27; P02-T11 = owner directive 2026-10-04 + CD-31, not a section of this plan; X-2 docs-only cleanup, owner decision C-7, 2026-10-07). Original header (2026-10-03): **DRAFT FOR OWNER REVIEW** (revision 2 for CD-27). Implementation is **not allowed** until S0-1 (architecture approval), S0-4 (SVN safeguard of the unversioned control-plane code) and S0-5 (baseline test report) are DONE and PHASE_02 is UNLOCKED in the tracker. |
| Revision history | r1 2026-10-03: first plan (CP Liquibase baseline in T1). **r2 2026-10-03: rewritten for owner decision CD-27** — no CP Liquibase in PHASE_02; T1 and T10 deferred to [PHASE_11A](../PHASE_11A_FINAL_CP_LIQUIBASE_BASELINE.md); the Liquibase-driven questions D-1, D-2, D-5 withdrawn; the r1 changelog inventory removed. |
| Author / date | agent (Claude Opus 5.5), 2026-10-03 |
| Evidence | Local code inspection on 2026-10-03 (`CODE-INSPECTED`): CONTROL-PANEL-SERVICE model, repositories, services, `application.yml`, changelogs; COMMON-LIBRARY `tenant/{naming,schema,metadata}`; ACCOUNT-BOOK `system/tenant`; the 16 service changelog masters. No build or test was run for this plan. |
| Protected hosts | Nothing in this plan touches `192.168.5.52` or `192.168.5.53`. All databases are local or development (Testcontainers MySQL, the local development MySQL). SVN: local commands only. |

---

## 1. Summary

PHASE_02 delivers the data foundation that every later phase uses:

1. The **Company model** as JPA entities (`tbl_company` + profile, setting, status history), the race-safe **`CMP_<n>` allocator** (`tbl_id_sequence`) and the **Company Login Code** column.
2. The **resource registry** `tbl_company_resource`, which replaces `tbl_organization_database` as the routing source.
3. The **realm registry** entity `tbl_identity_realm` and the realm FK on `tbl_identity` (logic in PHASE_03).
4. **`TenantResourceNaming`** extensions (realm name, exact validators; `cmp_` / `cmp-` names follow automatically from the `CMP_<n>` input).
5. The **Liquibase tenant migrator** in COMMON-LIBRARY (company databases, AD-09) with bootstrap modes `SEEDED` / `EMPTY_FOR_IMPORT` / `UPGRADE`, plus **schema-parity tests** for all 17 tenant modules.
6. A **compatible internal metadata API** (same path and payload), now reading the registry.

**Control Plane schema (CD-27):** the CP keeps Hibernate `ddl-auto: update` and `spring.liquibase.enabled: false`. PHASE_02 writes **no** CP changelog: no baseline, no seed changeset, no temporary or `platform/legacy/` changelog, no table or FK created only for Liquibase. The schema is defined by the entity mappings and generated on a **fresh** development database. The clean CP baseline comes once, in PHASE_11A, after the CP model is stable.

Size estimate: 9 active work packages (T0, T2–T9; T1 and T10 deferred); roughly 50–70 new or changed source files, about 22 new test classes. Suggested split: one agent for T0, T9, T5, T2, T3, T4 (control plane, sequential); a second agent may take T6–T8 (COMMON migrator and parity) **after** T9 is DONE, because they touch different modules.

---

## 2. Preconditions (gate checklist)

| # | Precondition | Tracker | Why |
|---|---|---|---|
| 1 | Owner approval of the architecture | S0-1 | No code before approval |
| 2 | The unversioned control-plane SaaS code is safeguarded in SVN by the owner | S0-4 | `svn status` shows `CONTROL-PANEL-SERVICE/src/main/java/nyaalkaran/erp/system/platform/**` as added/unversioned (`A +`, `?`). T3 rewrites these files; without a safeguard there is no way back. |
| 3 | Baseline local test report (PHASE_01 task 7) | S0-5 | T0 and the "full regression" check of PHASE_02 §14 compare against it |
| 4 | Owner decision D-6 (module catalog ordering) | S0-10 | T2 (registry `module_id`) and T4 (`serviceName` → module) need the module catalog |
| 5 | S0-2 items 1 and 4 (gap fixes: registry uniqueness, audit columns) | S0-2 | They change the `CompanyResource` unique key and the audit-log entity (D-4) |

---

## 3. Owner decisions

> **Decisions recorded 2026-10-03 (owner, CD-28):** D-6 = option (a): `tbl_module` is a functional PHASE_04 table; P04-T6 runs as soon as P02-T2 is ready (S0-10 DONE). D-4: S0-2 item 1 approved (registry unique key on company + module + resource type; PLATFORM_CORE resources registered against their PLATFORM_CORE `tbl_module` rows; `module_id` NULL only for IDP_REALM) and item 4 approved (explicit audit columns). §3.2 below is kept for traceability.

### 3.1 Withdrawn or resolved by CD-27 (2026-10-03)

| ID (r1) | r1 question | Status now |
|---|---|---|
| D-1 | Pull `tbl_module` forward so the CP baseline can declare its FKs | **WITHDRAWN** as a Liquibase question: no CP baseline exists in PHASE_02, and no table is created only for Liquibase. The **functional** need (registry `module_id`, metadata `serviceName` → module) remains and is asked as D-6. |
| D-2 | Transitional `platform/legacy/` changelogs so legacy entities survive `validate` | **WITHDRAWN.** With `ddl-auto: update`, legacy entities keep their tables until their own replacing phase removes them. No legacy changelog layer is created. |
| D-3 | Which phase creates `tbl_company_user` / `tbl_platform_admin*` | **RESOLVED** without a new decision: PHASE_02 creates neither. Following PHASE_02 §5 ("org-level memberships are removed"), `OrganizationUser` is removed and the customer-level membership entity `CustomerUser` stays, re-pointed from `tbl_customer` to `tbl_company`, until PHASE_10 (P10-T1) introduces `tbl_company_user`. `tbl_platform_admin*` stay in PHASE_04. No duplicate target table is created early. |
| D-5 | Which `tbl_id_sequence` rows are seeded by Liquibase | **WITHDRAWN.** The allocator creates the `COMPANY` row (start 101) insert-if-missing under the same lock. The stale name `QUOTE` (v2 quotes, replaced by orders and `tbl_document_series`) is reported for the PHASE_01 text and needs no PHASE_02 action. |

### 3.2 Still needed

| ID | Question | Why it matters | Options | Recommendation |
|---|---|---|---|---|
| **D-4** (= S0-2 items 1 and 4) | Approve the gap fixes "registry uniqueness" and "explicit audit columns"? | Item 1: the attachment and notification DBs are both DATABASE rows with `module_id` NULL, so the target unique key (`company_id`, `resource_type`, `module_key`) rejects the second one. Item 4 changes the audit-log entity. | Approve / reject each | Approve item 1 (register the platform-core DBs and the control-panel bucket against PLATFORM_CORE catalog rows; `module_id` NULL only for IDP_REALM). Item 4 is independent. |
| **D-6** | The registry's `module_id` references the module catalog (`tbl_module`), which PHASE_04 owns (P04-T6). The metadata API (T4) also needs `tbl_module.service_name`. In which order? | Without a catalog, T2 cannot map `module_id` and T4 cannot resolve `serviceName`. | (a) **Order change only:** run P04-T6 (catalog entities `Module` / `ModuleDependency` + the insert-if-missing catalog seeder in code) right after P02-T2's Company entities, by the PHASE_04 owner; P02-T2's registry relationship and P02-T4 then use it. (b) PHASE_02 maps `module_id` as a plain column without a relationship and resolves `serviceName` → module from the existing `tenant.provisioning.modules` configuration; PHASE_04 adds the relationship later. | **(a).** One owner per table (PHASE_04), no interim mapping to undo, and the catalog is needed by PHASE_02 functionally, not for Liquibase. If D-4 item 1 is approved, the PLATFORM_CORE rows (`attachment`, `notification`, `control-panel`) are part of that seed. |

---

## 4. Current code baseline (`CODE-INSPECTED`, 2026-10-03)

### 4.1 Schema management today

- `CONTROL-PANEL-SERVICE/src/main/resources/application.yml`: `ddl-auto: update`, `database-platform: org.hibernate.dialect.MySQL8Dialect`, `spring.liquibase.enabled: false`. **Kept in PHASE_02 (CD-27).**
- `db/changelog/db.changelog-master.xml` + 26 generated files under `db/changelog/platform/` (old model, never run). **Left untouched in PHASE_02;** retired in PHASE_11A.
- Enum representation: PHASE_01 §6.2 targets `VARCHAR` status columns. With `MySQL8Dialect`, Hibernate generates native `ENUM` columns. T2 therefore sets the CP dialect to COMMON `TenantMySqlDialect` (string enums as `varchar`, the dialect all routed services use), so the generated development schema already has the target representation and PHASE_11A audits a schema without native enums. This is a configuration change on a fresh development DB, not a migration.

### 4.2 Legacy entities and their users

| Entity (table) | Used by (main code) | PHASE_02 action |
|---|---|---|
| `Customer` (`tbl_customer`) | `CompanyOrganizationService`, `PlatformCustomerService`, `PlatformOnboardingService`, `PlatformOrganizationService`, `PlatformSubscriptionService`, `PlatformAccessResolver`, `IdentitySyncProcessor` | Replaced by `Company` (T3); entity removed |
| `Organization` (`tbl_organization`) | 15 classes (onboarding, provisioning, bootstrap, internal API, entitlement, quota, access, identity sync) | Replaced by `Company` (T3); entity removed |
| `OrganizationDatabase` (`tbl_organization_database`) | `DomainDatabaseProvisioningService`, `AttachmentDatabaseProvisioningService`, `PlatformInternalService` | Replaced by `CompanyResource` (T3, T4); entity removed |
| `OrganizationUser` | `PlatformMembershipService`, `IdentitySyncProcessor`, `IdentityReconciliationScheduler`, `PlatformAccessResolver` | Removed (PHASE_02 §5: org-level memberships removed) |
| `CustomerUser` | same | **Kept**, FK re-pointed to `tbl_company` (`company_id`); replaced by `tbl_company_user` in PHASE_10 |
| `Subscription`, `PlanVersion`, `PlanEntitlement` | `PlatformSubscriptionService`, `ModuleEntitlementService`, `OrganizationQuotaService`, `SubscriptionResolver` | **Kept**; `Subscription.customer_id` re-pointed to `company_id`; replaced in PHASE_05/06 |
| `Application`, `BusinessRole` | `BusinessRoleCatalogSeeder` | **Kept**; replaced in PHASE_04 (P04-T7) |
| `BillingAccount`, `Invoice`, `PaymentTransaction`, `CustomerUsage`, `OrganizationApplication`, `SubscriptionAddon`, `Plan`, `Permission`, `BusinessRolePermission`, `OrganizationUserBusinessRole` | **No main-code users** | Untouched in PHASE_02 unless they reference `Customer` / `Organization` (then re-pointed to `Company`, no other change). Each is replaced by its owning phase (PHASE_04/05/07/10). |

`OrganizationQuotaService` ("remove the organization quota", P02-T3) is removed in T3; its locking pattern is noted for the PHASE_10 seat check.

Under `ddl-auto: update`, tables of removed entities stay in an **existing** development DB (harmless, never dropped by this phase); a fresh development DB no longer contains them.

### 4.3 COMMON tenant runtime

- `TenantResourceNaming` lowercases and normalizes the **whole** tenant id: `ORG_101` → `org_101_crm_db`, `org-101-crm-bucket`. With the input `CMP_101` the same code yields `cmp_101_crm_db` and `cmp-101-crm-bucket`. The "prefix change" of CD-26 is therefore an **input** change (the allocator returns `CMP_<n>`) plus exact validators, not a rewrite.
- Bootstrap: `TenantSchemaInitController` (`POST /api/v1/internal/tenant-bootstrap/{externalId}`, also `/tenant-schema/`), `TenantBootstrapService` (MySQL `GET_LOCK`, then `TenantSchemaProvisioner` → `HibernateTenantSchemaInitializer` (update), contributors, Hibernate `validate`, seeders). Enabled by `spring.nyaalkaran.tenant.schema-init.enabled` in 15 services.
- ACCOUNT-BOOK has its own `TenantBootstrapController` / `AccountBookTenantProvisioningService`.
- The attachment DB is created by the control plane with Liquibase from COMMON-ATTACHMENT `db/changelog/attachment/changelog-attachment_metadata.xml` (`LiquibaseAttachmentDbSchemaManager`) — unchanged, not affected by CD-27.
- Changelog masters exist in 16 services; with the attachment changelog that is the **17 tenant modules** of P02-T7. COMMON-LIBRARY has **no** `liquibase-core` dependency today.
- Hard-coded `ORG_` patterns: `IdentitySyncProcessor.ORGANIZATION_ID` (`^ORG_…`), gateway `OrganizationSelection.ORGANIZATION_GROUP` (PHASE_03 scope), `CompanyOrganizationService` (`"ORG_" + (max + 1)`). *(All removed: P02-T3 / P02-T11 stages A, C, D.)*

---

## 5. Task order and dependencies

```text
T0 stale tests ─┐
T9 naming ──────┼─> T5 realm entity/FK ─> T2 Company model ─> [P04-T6 catalog, D-6 (a)] ─> T3 caller migration ─> T4 metadata API
                │
                └─> T6 tenant Liquibase migrator ─> T7 parity tests (17 modules) ─> T8 ACCOUNT-BOOK alignment
T1, T10: DEFERRED to PHASE_11A (CD-27)
```

| Order | Task | Depends on | Module(s) |
|---|---|---|---|
| 1 | T0 | S0-5 | CONTROL-PANEL tests |
| 2 | T9 | — | COMMON-LIBRARY |
| 3 | T5 | T9 | CONTROL-PANEL |
| 4 | T2 | T5, T9; D-4 | CONTROL-PANEL |
| 5 | (P04-T6, by PHASE_04) | T2 entities; D-6 (a) | CONTROL-PANEL |
| 6 | T3 | T2, P04-T6 | CONTROL-PANEL |
| 7 | T4 | T3 | CONTROL-PANEL |
| 8 | T6 | T9 | COMMON-LIBRARY + 15 services' config |
| 9 | T7 | T6 | COMMON-LIBRARY tests, 16 services' tests |
| 10 | T8 | T6, T7 (ACCOUNT-BOOK part) | ACCOUNT-BOOK |
| — | T1, T10 | PHASE_11A | — |

---

## 6. Task details

Common rules for every task:
- Code style: follow the existing control-plane and COMMON code (Lombok as used today, constructor injection, `ApiException` subclasses, Javadoc with `@author` as in the neighbouring files).
- **No CP changelog** is added or edited (CD-27). Tenant changelogs (T7 fixes) follow `LIQUIBASE_CONVENTIONS.md`.
- Entity mappings carry the PHASE_01 §6.2 definition: column names, types and lengths (`@Column(length, nullable)`), `@Enumerated(STRING)`, unique keys (`@Table(uniqueConstraints = …)`), indexes (`@Table(indexes = …)`), relationships (`@ManyToOne` + `@JoinColumn`, which makes Hibernate create the FK), `@Version` where PHASE_01 shows `[ver]`, and the audit columns. Database CHECK constraints are `@Check` for now (final DB CHECKs in PHASE_11A).
- Tests run **locally only** (Maven, Testcontainers MySQL). No test connects to a protected host.
- After each task: tracker row → `REVIEW` with the tests run; WORK_LOG entry.

### T0 — Stale control-plane tests (P02-T0)

| Item | Detail |
|---|---|
| Goal | A green or explicitly annotated baseline before the model switch, so later failures are attributable. |
| Files | `CONTROL-PANEL-SERVICE/src/test/java/.../security/PlatformJwtGrantedAuthoritiesConverterTest.java`, `.../identity/IdentitySyncMySqlIT.java` |
| Work | 1. Compare the S0-5 baseline report with the two known stale tests. 2. `mapsScopesAndRealmAndResourceRoles`: align the assertion with today's converter behaviour (pure unit test; the converter is redesigned in PHASE_03/04). 3. `IdentitySyncMySqlIT` realm-role assertions: annotate with `@Disabled("Stale realm-role assertion; fixed in P10-T11 (realm-aware identity sync)")` on the affected methods only. |
| Tests | `mvn -pl CONTROL-PANEL-SERVICE test` and the IT class locally. |
| Done when | Only documented, annotated failures remain; the list is in the tracker note. |
| Risk | Hiding a real regression: annotate per method, never per class; link P10-T11. |

### T9 — `TenantResourceNaming` extensions (P02-T9)

| Item | Detail |
|---|---|
| Goal | Exact, validated company resource names (PHASE_01 §6.1.3, PHASE_02 §6.5). |
| Files | `COMMON-LIBRARY/src/main/java/nyaalkaran/erp/common/tenant/naming/TenantResourceNaming.java`; new `CompanyCode.java` (same package, small value helper); test `COMMON-LIBRARY/src/test/java/.../naming/TenantResourceNamingTest.java` (extend or create) |
| New API | `COMPANY_CODE` pattern `^CMP_[0-9]{1,8}$`; `requireCompanyCode(String)`; `companyNumber(String)` (`CMP_101` → `101`); `realmName(companyCode)` → `co-101` (`^co-[0-9]{1,8}$`); `databaseName(companyCode, dbToken)` (alias of `domainDatabaseName`, `^[a-z0-9_]{1,64}$`); `notificationDatabaseName(companyCode)` → `cmp_101_notification_db`; `isValidDatabaseName`, `isValidBucketName` (3–63, `^[a-z0-9][a-z0-9-]*[a-z0-9]$`); `assertMatchesTenant(name, companyCode)` (exact match against the derived names). |
| Compatibility | Existing methods keep their signatures and still accept a LEGACY `ORG_<n>` value (development data) so other services compile and run; the new `require*` / `assert*` methods accept `CMP_<n>` only. Javadoc updated to `CMP_<n>` (CD-26). |
| Tests | `CMP_101` → `cmp_101_crm_db`, `cmp-101-crm-bucket`, `cmp-101-control-panel-bucket`, `co-101`, `cmp_101_attachment_db`, `cmp_101_notification_db`, `cmp_101_account_book_db`; rejects `cmp_101`, `CMP_`, `CMP_1a`, `CMP_123456789`, blank, null; bucket length limits; `assertMatchesTenant` rejects a name of `CMP_102` for `CMP_101` and a prefix-only match (`cmp_1011_crm_db`). Acceptance 8: names depend only on the code, never on a name or slug. |
| Risk | Low. Pure functions. |

### T1 — CP Liquibase baseline (P02-T1): **DEFERRED to PHASE_11A (CD-27)**

Nothing is implemented for T1 in PHASE_02. Its former content is covered as follows:

| Former T1 content | Where it is now |
|---|---|
| Company-model tables of PHASE_01 §6.2 A, B, F, H | JPA entities of T2 and T5, schema by `ddl-auto: update` |
| `COMPANY` sequence row, start 101 | Insert-if-missing in `CompanyCodeAllocator` (T2) |
| Enable Liquibase, `ddl-auto: validate` | PHASE_11A tasks 4–6 |
| Named constraints (`uk_`, `fk_`, `idx_`, `ck_`), DB CHECK constraints, seed changesets | PHASE_11A task 4 (`@Check` meanwhile) |
| Acceptance 1 (start on an empty DB) | `ControlPanelSchemaMySqlIT` in T2 (empty MySQL, `ddl-auto: update`, generated tables match PHASE_01 §6.2 A, B, F, H) |

### T5 — Realm registry entity and the identity realm FK (P02-T5)

| Item | Detail |
|---|---|
| Goal | `tbl_identity_realm` mapped; `tbl_identity.identity_realm_id` NOT NULL with a unique key (`identity_realm_id`, `keycloak_user_id`) (PHASE_01 §6.5.2). The registry **logic** (issuer API, invalidation) is PHASE_03. |
| Files | New `platform/model/IdentityRealm.java`, `platform/enums/RealmType.java`, `platform/enums/IdentityRealmStatus.java`, `platform/repository/IdentityRealmRepository.java`; modify `platform/model/Identity.java` (add `@ManyToOne identityRealm`; replace the soft-delete fields by status, PHASE_01 §5.2; `account_scope` reduced to PLATFORM, COMPANY), the account-scope enum (remove `ORGANIZATION`), `IdentityRepository` (`findByIdentityRealmIdAndKeycloakUserId`). |
| Interim behaviour | The existing identity-sync code works with one configured realm and is off by default. Until PHASE_03 (P03-T11) it looks up its realm row by the configured realm name and **fails closed** if the row is missing. No realm row with a host-specific issuer is hard-coded; development registers its row through a documented local step or the PHASE_03 runner. |
| Owner action | Confirm the least-privilege environment DB account (`cmp\_%` schemas + CP DB, no admin rights), part of P02-T5. Record the confirmation in the tracker. |
| Tests | `IdentityRealmRepositoryMySqlIT`: unique realm name, issuer, company; `tbl_identity` unique per realm (same `sub` in two realms allowed, twice in one realm rejected). |
| Risk | `IdentitySyncMySqlIT` needs a realm row in its fixture (update the fixture, not the assertion). |

### T2 — Company model, allocator, slug and login code, registry (P02-T2)

| Item | Detail |
|---|---|
| Entities (`platform/model/`) | `Company`, `CompanyProfile` (1:1), `CompanySetting`, `CompanyStatusHistory` (append-only, no setters after persist), `CompanyResource`, `IdSequence` (`@Id sequenceName`, `@Version`). `Company.companyCode` with `@Column(updatable = false)`. `Company.loginCode` changeable only through `CompanyService.changeLoginCode` (audited; the API comes in PHASE_04). |
| Enums (`platform/enums/`) | `CompanyStatus` (DRAFT, PROVISIONING, PROVISIONING_FAILED, AWAITING_DATA_IMPORT, ACTIVE, SUSPENDED, DISABLED, ARCHIVED, PURGE_APPROVED), `OnboardingType` (NEW, IMPORT_EXISTING_DATA), `CompanyResourceType` (DATABASE, BUCKET, IDP_REALM), `CompanyResourceStatus` (RESERVED, CREATED, VERIFIED, DEGRADED, ORPHANED, RETIRED). |
| Configuration | CP `application.yml`: `database-platform: nyaalkaran.erp.common.tenant.schema.TenantMySqlDialect` (§4.1); `ddl-auto: update` and `spring.liquibase.enabled: false` unchanged. |
| Repositories | `CompanyRepository` (`findByCompanyCode`, `findByLoginCode`, `existsBySlug`, `findCompanyCodesByStatus`), `CompanyProfileRepository`, `CompanySettingRepository`, `CompanyStatusHistoryRepository`, `CompanyResourceRepository` (`findByCompanyIdAndResourceTypeAndModuleId`, `findByResourceTypeAndResourceName`), `IdSequenceRepository` (`@Lock(PESSIMISTIC_WRITE) findForUpdate(name)`). |
| `CompanyResource` module reference | Per D-6 (a): `@ManyToOne Module module` (PHASE_04 entity) once P04-T6 exists; the entity is written in T2 without the relationship and the relationship is added in the same PR that consumes P04-T6 (T3). The unique key follows D-4. |
| `CompanyCodeAllocator` (`platform/service/company/`) | `@Transactional(propagation = REQUIRES_NEW)`; locks row `COMPANY` (`SELECT … FOR UPDATE`); if the row is missing, inserts it with `next_value = 101` insert-if-missing (`INSERT IGNORE`) and locks it; returns `"CMP_" + next_value`; increments; never reuses (gaps allowed). |
| `SlugPolicy` | Regex `^[a-z0-9](?:[a-z0-9-]{1,38}[a-z0-9])$`; reserved words `admin`, `api`, `platform`, `www`, `internal`, `control-panel` + every module code; `suggest(displayName)` (ASCII fold, lowercase, collapse, trim, suffix `-2`, `-3` on collision). |
| `LoginCodePolicy` | `^[A-Z0-9]{6,12}$`; validate a platform-chosen code (409 on collision) or generate one (random `A–Z0-9`, 8 characters, retry on collision; never derived from the company code, realm or DB name, CD-15). |
| `CompanyService` | `create(CreateCompanyCommand)` in one transaction: validate → allocate code → slug → login code → `tbl_company` DRAFT + profile + history row (`null → DRAFT`) → audit `COMPANY_CREATED`. `get`, `updateProfile`, `changeStatus(code, target, reason, actor)` with **exactly the transition table of master §7.1** (anything else → 409 `INVALID_COMPANY_TRANSITION`), writing a history row and setting `activated_at` / `suspended_at` / `archived_at`. `retention_days` is filled from the platform setting (Q-19: 365). `PURGE_APPROVED` is not reachable through this service in PHASE_02 (manual dual approval, master §19.6). Lifecycle **endpoints** are PHASE_04. |
| `CompanyResourceRegistry` | `reserve(company, type, module, name)` (validates `name` with `TenantResourceNaming.assertMatchesTenant`; the unique key is the backstop → `ResourceNameConflictException`), `markCreated`, `markVerified(host, port, schemaVersion)`, `markOrphaned`, `findVerifiedDatabase(companyCode, serviceName)`. Names are derived only from `company_code` + module tokens (PHASE_02 §11.1). |
| Tests | Unit: `SlugPolicyTest`, `LoginCodePolicyTest`, `CompanyLifecycleTransitionTest` (every allowed edge, a sample of forbidden edges), `CompanyResourceRegistryTest`. MySQL IT: `ControlPanelSchemaMySqlIT` — context boots on an **empty** MySQL with `ddl-auto: update`; the generated Company-model tables have the PHASE_01 §6.2 A, B, F, H columns, nullability, unique keys, indexes and FKs; no `enum` column in the new tables (acceptance 1, CD-27 wording). `CompanyCodeAllocatorConcurrencyMySqlIT` — **50 parallel creations → 50 unique `CMP_<n>`, no duplicates**, first code `CMP_101` on an empty DB (acceptance 2). `CompanyServiceMySqlIT` (unique slug and login code, history rows). `CompanyResourceRegistryMySqlIT` (unique type + name across companies; D-4 variant). |
| Risk | Hibernate `update` does not drop or rename columns and swallows some DDL errors: always run the ITs on an **empty** container DB, not on a long-lived development DB. Lock contention: short `REQUIRES_NEW` transaction; a lock timeout surfaces as 503 and the caller retries (guide §2.2 S2). |

### T3 — Migrate control-plane callers to Company (P02-T3)

| Item | Detail |
|---|---|
| Goal | No main code uses `Customer`, `Organization`, `OrganizationDatabase` or `OrganizationUser`; development onboarding and provisioning keep working on `Company`. |
| Services | `PlatformOnboardingService`, `CompanyOrganizationService` (merged into `CompanyService`; the `"ORG_" + (max + 1)` generator is removed), `PlatformCustomerService` + `PlatformOrganizationService` (→ company read/update; "company → organizations" listing removed), `PlatformProvisioningService`, `PlatformProvisioningStatusService`, `DomainDatabaseProvisioningService` and `AttachmentDatabaseProvisioningService` (write `tbl_company_resource` RESERVED → CREATED → VERIFIED instead of `tbl_organization_database`; names from `TenantResourceNaming` with `CMP_<n>`), `DomainTenantBootstrapService` (company code), `ModuleEntitlementService`, `PlatformSubscriptionService`, `SubscriptionResolver` (company id on the kept legacy subscription entities), `PlatformAccessResolver`, `PlatformMembershipService` (company membership on the kept `CustomerUser`, re-pointed to company), `IdentitySyncProcessor` (`ORGANIZATION_ID` pattern → `^CMP_[0-9]{1,8}$`; LEGACY group handling stays until PHASE_03), `IdentityReconciliationScheduler`. |
| Controllers | `PlatformCompanyAdminController`, `PlatformOrgAdminController`, `PlatformCustomerController`, `PlatformOrganizationController`, `PlatformOnboardingController`, `PlatformProvisioningController`, `PlatformSubscriptionController`: keep the paths that the development tools use, backed by the Company services; remove the organization-only endpoints. New platform-admin company APIs are PHASE_04. |
| Removed (owner approval, copies kept per the owner's `_cleanup_review` process) | `OrganizationQuotaService` (+ test) and the organization-quota parts of `CompanyOrganizationQuotaMySqlIT`; `Customer`, `Organization`, `OrganizationDatabase`, `OrganizationUser` entities and repositories after their callers are migrated. |
| Re-pointed (no other change) | `CustomerUser`, `Subscription` and any unused stub that referenced `Customer` / `Organization`: their FK column becomes `company_id` → `tbl_company`. |
| Status mapping | Old `OrganizationStatus` → `CompanyStatus` per master §7.1 (PENDING → DRAFT, RECOVERING → job state, DEACTIVATED → ARCHIVED). |
| Tests | Update the existing service tests (`PlatformOnboardingServiceTest`, `PlatformOrganizationServiceTest`, `PlatformCustomerServiceTest`, `PlatformMembershipServiceTest`, `PlatformProvisioningServiceTest`, `PlatformProvisioningStatusServiceTest`, `PlatformSubscriptionServiceTest`, `TenantProvisioningServiceTest`, `DomainTenantBootstrapServiceTest`, `AttachmentDatabaseProvisioningServiceTest`, the `*MySqlIT` classes) to the Company model. New: a provisioning IT proving registry rows end VERIFIED with `cmp_<n>_*` names. An architecture test: no class in `platform..` references the removed model classes. |
| Risk | Largest task. Do it service by service, keeping the build green after each step. Because the CP stays on `ddl-auto: update`, the not-yet-replaced legacy entities keep working without any changelog work. |

### T4 — Internal metadata API on the registry (P02-T4)

| Item | Detail |
|---|---|
| Goal | Same path and payload; data from `tbl_company_resource` (PHASE_02 §6.2, acceptance 3 and 4). |
| Files | `platform/controller/internal/InternalOrganizationController.java`, `platform/service/PlatformInternalService.java`; COMMON `TenantMetadata` **unchanged**. |
| Behaviour | `GET /api/v1/internal/platform/organizations/{companyCode}/metadata?serviceName=` (since P02-T11 stage B: `/api/v1/internal/platform/companies/{companyCode}/metadata`, payload field `companyCode` instead of `organizationId`): validate `companyCode` (`^CMP_[0-9]{1,8}$`, else 404); company by code; module by `service_name` of the catalog (D-6); VERIFIED DATABASE row for (company, module) → `dbName`, `dbHost`, `dbPort`; `organizationId` = company code; `tenantId` = `tbl_company.id` (backwards compatible); `status` per PHASE_02 §6.2 (ACTIVE → ACTIVE; PROVISIONING → PROVISIONING; DRAFT, PROVISIONING_FAILED, AWAITING_DATA_IMPORT, SUSPENDED → SUSPENDED; DISABLED, ARCHIVED, PURGE_APPROVED → DISABLED). No VERIFIED row → 404 (the resolver turns it into 403). `GET …/organizations/active` returns ACTIVE company codes; optional `?module=` filter (companies with a VERIFIED DATABASE row for that module; the entitlement-based filter replaces it in PHASE_06). |
| Tests | `PlatformInternalServiceTest` (each status mapping, missing row, non-VERIFIED row, bad code); `InternalMetadataApiMySqlIT` (payload JSON shape equals the pre-change shape); COMMON `TenantMetadataResolverTest` runs **unchanged** (acceptance 3). A test proves no request field other than the path code and `serviceName` influences the returned DB (acceptance 4). |
| Risk | The ACCOUNT-BOOK and gateway callers keep the same contract; no change outside the control plane. |

### T6 — `LiquibaseTenantSchemaMigrator` and bootstrap modes (P02-T6) — company databases, not the CP

| Item | Detail |
|---|---|
| Goal | Company (tenant) schemas come from each service's changelog master; Hibernate `update` is no longer used **for tenant DBs** (PHASE_02 §6.4, AD-09; acceptance 5 and 6). CD-27 does not apply here. |
| Dependencies | Add `org.liquibase:liquibase-core` to `COMMON-LIBRARY/pom.xml` (`optional`, version from the Spring Boot BOM, same as CONTROL-PANEL). |
| New classes (`common/tenant/schema/`) | `TenantBootstrapMode` (SEEDED, EMPTY_FOR_IMPORT, UPGRADE); `TenantBootstrapRequest` (`mode`, default SEEDED); `LiquibaseTenantSchemaMigrator`: opens the target DB with the DDL data source of `TenantSchemaDataSourceProvider`, runs `classpath:db/changelog/db.changelog-master.xml` with context `tenant` through Spring's `SpringResourceAccessor` (plain `ClassLoaderResourceAccessor` does not resolve `classpath:` includes), returns the applied count and the last changeset id; `TenantDatabaseGuard` (rows present and no `DATABASECHANGELOG` → `409 TENANT_DB_NOT_EMPTY` in SEEDED mode). |
| Modified | `TenantBootstrapService.initialize(externalId, mode)`: lock → guard → migrator → contributors (Camunda after Liquibase, unchanged) → Hibernate `validate` (existing code) → seeders unless EMPTY_FOR_IMPORT; UPGRADE = migrate + validate only. `TenantSchemaInitController`: optional JSON body `{ "mode": … }`; no body = SEEDED. `TenantBootstrapResult`: + `mode`, `appliedChangesets`, `latestChangesetId`, `validation`. `TenantSchemaProvisioner` delegates to the migrator; `HibernateTenantSchemaInitializer` stays only as the parity-test helper (T7). `TenantSchemaInitAutoConfiguration` wires the migrator. `TenantDataSeeder` contract documented as insert-if-missing. `SAFE_EXTERNAL_ID` → company code pattern (LEGACY `ORG_` accepted only behind a development flag until T3 is merged). |
| Safety (PHASE_02 §11.4–5) | The derived DB name must equal the VERIFIED registry name passed by the control plane (mismatch → 409). The migrator never calls `dropAll`. `TenantChangelogNoDropTest` (T7) rejects `<dropTable>` / `<dropColumn>` in tenant changelogs unless listed in an owner-approved allow-list (empty). |
| Control-plane caller | `HttpDomainTenantBootstrapClient` sends `{ "mode": "SEEDED" }` (or `EMPTY_FOR_IMPORT` when `onboarding_type = IMPORT_EXISTING_DATA`) and stores `schema_version` on the registry row. |
| Tests | `LiquibaseTenantSchemaMigratorMySqlIT` (COMMON, test changelog): SEEDED creates + seeds; a second run is a no-op (acceptance 5); EMPTY_FOR_IMPORT skips seeders (acceptance 6); UPGRADE applies only new changesets; guard 409; lock prevents parallel runs. `TenantSchemaInitControllerTest`: no body = SEEDED; invalid mode = 400. |
| Risk | Changelogs not runnable on an empty DB (PRODUCTION's BASE changelog failed on a fresh DB on 2026-10-02) → T7 detects; fix forward with new changesets only. |

### T7 — Schema-parity tests for the 17 tenant modules (P02-T7)

| Item | Detail |
|---|---|
| Goal | For every tenant module, the Liquibase-created schema equals the entity model (acceptance 7). |
| Approach | DB A from the service's master changelog (T6 migrator), DB B from the entities with `HibernateTenantSchemaInitializer` (same dialect, naming, and COMMON `tbl_audit_log`), diff `information_schema` (tables, columns, types, nullability, defaults, PK, unique, index, FK + rules). A shared helper `SchemaParityAssert` in a COMMON test-jar (PHASE_11A reuses it for the CP); one small `*SchemaParityMySqlIT` per service. |
| Modules | ACCOUNT-BOOK, ASSET, ATTENDANCE, CHAT, COMPLIANCE (Camunda tables excluded from the diff), CRM, HRMS, LOGISTICS, MINING, NOTIFICATION, PAYROLL, PRODUCTION, QC, RESEARCH-AND-DEVELOPMENT, STORE, TICKETS, and the attachment DB. |
| Fixes | Any difference is fixed **forward** with a new changeset in the table's file or in the entity if the entity is wrong; never by editing a deployed changeset. |
| Tests | 17 parity ITs + `TenantChangelogNoDropTest`. |
| Risk | Effort depends on drift found; ACCOUNT-BOOK needs `allowMultiQueries=true`; keep CRLF line endings when editing. |

### T8 — ACCOUNT-BOOK bootstrap alignment (P02-T8)

| Item | Detail |
|---|---|
| Goal | ACCOUNT-BOOK bootstraps through the common migrator with the same modes and result. |
| Files | `ACCOUNT-BOOK-SERVICE/src/main/java/nyaalkaran/erp/system/tenant/{TenantBootstrapController, AccountBookTenantProvisioningService, TenantBootstrapResult}.java`, its `application.yml` (DDL data source JDBC URL with `allowMultiQueries=true`). |
| Work | Delegate to `TenantBootstrapService` (COMMON) or, if its infrastructure configuration differs, keep the controller and call `LiquibaseTenantSchemaMigrator` + the same guard and modes. Same path, same `mode` body, same result fields. |
| Tests | Update the ACCOUNT-BOOK bootstrap ITs: SEEDED, EMPTY_FOR_IMPORT, re-run no-op, guard. |
| Risk | ACCOUNT-BOOK's own data-source wiring (secondary EMF/TM must stay non-default candidates); change only the migrator call. |

### T10 — Superseded generated changelogs (P02-T10): **DEFERRED to PHASE_11A (CD-27)**

The 26 generated CP changelogs stay untouched (not run, not extended). PHASE_11A task 8 retires them together with the final cleanup (owner `svn move` to `_cleanup_review/`, no deletion).

---

## 7. Company-model entity map (replaces the r1 changelog inventory)

Target columns, keys and indexes: PHASE_01 §6.2 / §6.5.2. Delivered as JPA entities in PHASE_02; the Liquibase form is written only in PHASE_11A.

| Table | Entity | Task | Notes |
|---|---|---|---|
| `tbl_id_sequence` | `IdSequence` | T2 | `COMPANY` row insert-if-missing (101) |
| `tbl_company` | `Company` | T2 | unique `company_code`, `slug`, `login_code`; `@Check` on code and login-code format |
| `tbl_company_profile` | `CompanyProfile` | T2 | 1:1 |
| `tbl_company_setting` | `CompanySetting` | T2 | unique (`company_id`, `setting_key`) |
| `tbl_company_status_history` | `CompanyStatusHistory` | T2 | append-only |
| `tbl_company_resource` | `CompanyResource` | T2 / T3 | module relationship per D-6; unique keys per D-4 |
| `tbl_identity_realm` | `IdentityRealm` | T5 | unique company, realm name, issuer |
| `tbl_identity` | `Identity` (changed) | T5 | realm FK; unique (realm, `sub`) |
| `tbl_identity_sync_task` | `IdentitySyncTask` (changed) | T3 | `company_code` instead of the organization/customer external ids |
| `tbl_provisioning_job`, `tbl_provisioning_step` | existing (changed only as T3 needs: `company_id`) | T3 | full target columns in PHASE_08 |
| `tbl_platform_audit_log` | existing (changed only as T3 needs: `company_id`) | T3 | full target columns in PHASE_04 (+ D-4 item 4) |
| Not in PHASE_02 | `tbl_module` (PHASE_04), `tbl_company_user` (PHASE_10), `tbl_platform_admin*` (PHASE_04), `tbl_data_import_*` (PHASE_11) | — | no early or duplicate creation |

---

## 8. Test plan summary

| Level | Tests | Acceptance (PHASE_02 §13) |
|---|---|---|
| Unit | naming (T9), slug, login code, transitions, registry, metadata mapping, controller body parsing | 8 |
| MySQL IT (Testcontainers, local Docker) | CP schema on empty DB with `update` (T2), realm keys (T5), allocator concurrency 50 (T2), company service, registry conflicts, provisioning registry rows (T3), metadata API shape (T4), tenant migrator modes (T6), ACCOUNT-BOOK bootstrap (T8) | 1, 2, 3, 4, 5, 6 |
| Parity IT | 17 tenant modules + no-drop rule (T7) | 7 |
| Regression | the full suite of the touched modules vs the S0-5 baseline report | PHASE_02 §14 |

Commands (local only): `mvn -pl COMMON-LIBRARY install`, `mvn -pl CONTROL-PANEL-SERVICE verify`, `mvn -pl <service> verify` per touched service. Docker Desktop must be running for Testcontainers.

---

## 9. Risk register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| The unversioned control-plane code is lost or overwritten during T3 | Medium | High | Precondition S0-4 (owner safeguard); no T3 start before it |
| Hibernate `update` leaves stale columns or tables in long-lived development DBs, hiding mapping errors | High | Medium | All ITs on an empty Testcontainers DB; a fresh development DB for PHASE_02; PHASE_11A audits a freshly generated schema |
| The entity mappings drift from PHASE_01 §6.2 during PHASE_02–PHASE_11 | Medium | Medium | `ControlPanelSchemaMySqlIT` asserts the §6.2 columns and keys; PHASE_11A audit |
| Tenant changelogs not runnable on an empty DB | Medium | High | T7 parity ITs per module; forward-only fixes |
| Metadata contract regression breaks all domain services | Low | High | Unchanged `TenantMetadata`; resolver tests unchanged; JSON shape IT |
| Concurrent allocation duplicates | Low | High | Locked sequence + unique `company_code`; 50-thread IT |
| An `svn update` by the owner lands trunk changes mid-phase | Medium | Medium | Re-run the ITs after every owner update; check for single-DB runners and tenant-less jobs |
| An agent accidentally targets a protected host | Low | Critical | Local Testcontainers only; no host addresses in test configuration; review of every `application-*.yml` change |

---

## 10. Rollback

- The control plane can be redeployed with the previous build against the **backed-up** development database (PHASE_02 §15). The new model runs on a **fresh** development `control_panel_db`; the old development database is kept until the owner approves dropping it.
- COMMON bootstrap: the `mode` body is optional; old callers keep SEEDED behaviour.
- Tenant databases are not changed structurally; a database migrated by Liquibase only gains `DATABASECHANGELOG*`.

---

## 11. Out of scope for PHASE_02

**Any Control Plane Liquibase work (CD-27, PHASE_11A)**; realm registry logic and issuer API (PHASE_03); platform-admin company endpoints and the full module catalog (PHASE_04); plans, prices, tax (PHASE_05); subscriptions and entitlements redesign, `COMPANY_MODULES` publisher rename (PHASE_06); billing (PHASE_07); the durable provisioning worker, realm and bucket provisioning (PHASE_08); company membership table and seat enforcement (PHASE_10); `CredentialCipher` (PHASE_13/14); any production change.

---

## 12. Documentation findings

r1 (editorial fixes following existing decisions): `org\_%` → `cmp\_%` in six places (CD-26); PHASE_02 §15 default mode SEEDED; PHASE_02 §14 cipher tests moved to PHASE_13/14; developer guide header CD-01..CD-26. (The r1 path fix `platform/v2/` in PHASE_02 §7 is superseded by CD-27.)

r2 (CD-27, owner decision 2026-10-03): every early CP Liquibase requirement was changed to "entities with `ddl-auto: update` now, clean baseline in PHASE_11A" in PHASE_01 (§0.1 CD-27, §0.4, §1.1 marks, §6.2, §6.4, §7, §16), PHASE_02 (objective, §5, §6.3, §7, §8, §10, §12 T1/T10, §13, §14, §16), PHASE_04 (T1, seed, dependencies), PHASE_05/06/07/10 (task 1), the master (§10.2 note, §16.4, phase table), the developer guide (§23 D, E2, F). Company-database Liquibase (AD-09) is unchanged. The PHASE_11A → PHASE_12 gate G6 link is a **PROPOSAL** only (S0-12, pending owner approval), labelled as such in PHASE_01 CD-27, PHASE_11A and PHASE_12.

Open for the owner (not changed): `LIQUIBASE_CONVENTIONS.md` §1 / §6 (one table per file with its FKs, XML master) differ from the preferred final layout of the owner's CD-27 instruction (`db.changelog-master.yaml`, `tables/`, `indexes/`, `constraints/`, `seeds/`). PHASE_11A task 2 settles the final layout for the CP.

---

## 13. Definition of done (PHASE_02)

All PHASE_02 §13 acceptance criteria are proven by the tests of §8; every active task row is REVIEW with its test evidence (T1 and T10 stay DEFERRED to PHASE_11A); the owner sets DONE and unlocks PHASE_03 / PHASE_04 in the tracker.
