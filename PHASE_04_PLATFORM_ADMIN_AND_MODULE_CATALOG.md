# PHASE 04 — Platform Administration and Module Catalog

| Item | Value |
|---|---|
| Master architecture | [SAAS_ARCHITECTURE_MASTER.md](SAAS_ARCHITECTURE_MASTER.md) §8, §10.1, §11.1–11.2 |
| Design source | [PHASE_01 §6.2 C and G, §6.5.5, §6.7](PHASE_01_SAAS_FOUNDATION.md) |
| Depends on | [PHASE_02](PHASE_02_CP_SCHEMA_AND_TENANCY_IMPLEMENTATION.md) (Company model), [PHASE_03](PHASE_03_IDENTITY_KEYCLOAK_AND_COMPANY_CONTEXT.md) (Platform Super Admin bootstrap, issuer and audience validation, `/platform/me`) |
| Enables | PHASE_05, PHASE_06, PHASE_07, PHASE_10 |
| Status | **DONE** (2026-10-07; all 9 tasks DONE, owner-approved; owner decisions R-1..R-4: PHASE_02 / PHASE_03 DONE satisfied the dependency, LOCKED → DONE directly, all prior PHASE_04 approvals valid; PHASE_05 stays LOCKED). Runtime `PLATFORM_ACCESS_MODEL` stays v1 (D-D (a)). Earlier the same day: all 9 tasks DONE, phase LOCKED by governance (D-C (b)) until PHASE_02 / PHASE_03 were DONE. Earlier: PARTIALLY_COMPLETED (2026-10-03 assessment). |

> **Revision 2026-10-03 (final review, CD-04, CD-06, CD-11):**
> - Platform roles are defined and **assigned in the platform realm**. The CP keeps `tbl_platform_admin` (status, type, company scope) and the platform permission **policy** (`tbl_role` / `tbl_role_permission`, PLATFORM domain).
> - `tbl_platform_admin_role` is **removed** (CD-11).
> - The permission catalog and policy core created here is the base of the **CP Permission Policy** (CD-12, Q-54 closed).
> - Platform realm name: **`erp-platform`** (CD-17). Platform roles: `PLATFORM_SUPER_ADMIN`, `PLATFORM_ADMIN` (+ further platform roles), realm roles of `erp-platform`. The legacy code spelling `PLATFORM_SUPERADMIN` is migrated.
> - The platform user management goes through `erp-platform-identity-admin`. Platform Admins never receive Keycloak admin credentials (CD-17).
> - The Platform Super Admin is created only by the operator (CD-04).

---

## 1. Objective

1. **Platform administration:**
   - Platform Super Admin.
   - Platform Admins who receive **only explicitly granted platform permissions**, limited to a **company scope**.
   - Backend enforcement and an audit trail for every action.
2. **Company lifecycle APIs** for platform staff: create, read, update, suspend, reactivate, disable, archive.
3. **A database-driven module catalog** (`tbl_module`) with **mandatory and recommended dependencies** (`tbl_module_dependency`). It replaces the hard-coded `ModuleCatalog`, the YAML provisioning list and `tbl_application`.

## 2. Existing implementation relevant to this phase

> **P02-T11 note (stage G, 2026-10-05):** the tables of this section describe the code as inspected on 2026-10-03. P02-T11 stages A–F (2026-10-04/05, REVIEW) have since replaced the organization tenancy: the CP organization / customer tier (`Customer`, `Organization`, `OrganizationDatabase`, `OrganizationUser`, `OrganizationUserBusinessRole`, `OrganizationQuotaService`, `CompanyOrganizationService`, `OrganizationGroupProvisioner`, `@OrganizationScoped`, `PlatformOrgAdminController`, …) is deleted (stage C); `InternalOrganizationController` → `InternalCompanyController` (`/api/v1/internal/platform/companies/...`), `OrganizationEntitlementSyncListener` → `CompanyEntitlementSyncListener`, `CustomerUser` → `CompanyUser` (`tbl_company_user`), `CustomerUsage` → `CompanyUsage`; runtime `TenantContext.companyCode`, header `X-Company-Code`, Redis `COMPANY_MODULES:<CMP_n>`, resource names `cmp_<n>_…` / `cmp-<n>-…` (stages A / B); gateway `CompanyContextFilter` / `CompanySelection`, company from the verified `tenant_id` claim, no `Active-Org` (stage D). Read old names in this section as these replacements.

| Component | Location | Notes (`CODE-INSPECTED`) |
|---|---|---|
| Platform roles | Keycloak realm roles `PLATFORM_SUPERADMIN`, `PLATFORM_ADMIN`; `PlatformRole` enum | Assigned manually by an operator; no database row; no permissions; no scope |
| Converter | `platform/security/PlatformJwtGrantedAuthoritiesConverter.java` | Maps realm roles and scopes; ignores `PLATFORM_*` client roles (correct) |
| Interceptor | `platform/security/PlatformSecurityInterceptor.java` | `@PlatformAdminOnly`, `@PlatformSuperAdminOnly`, `@CompanyScoped`, `@OrganizationScoped`, `@InternalServiceOnly` |
| Access resolver | `platform/security/PlatformAccessResolver.java` | Platform admins bypass every membership check (global) |
| Actor resolver | `platform/security/PlatformActorResolver.java` | Actor = `preferred_username`; IP = `getRemoteAddr()` |
| Admin controllers | `platform/controller/internal/Platform*Controller.java`, dual-mapped to `/api/v1/internal/platform/**` and `/api/v1/platform/admin/**` | PLATFORM_ADMIN: list/get/create customers. SUPERADMIN: organizations, lifecycle, provisioning, subscriptions. |
| Security chain | `platform/security/PlatformSecurityConfig.java` | `/api/v1/platform/**` authenticated; `anyRequest().permitAll()`; JWK-only validation (no issuer/audience) |
| Platform audit | `PlatformAuditService`, `tbl_platform_audit_log` | Action names encode the outcome; no detail column |
| Module catalog | `platform/entitlement/ModuleCatalog.java` (17 hard-coded ids), `BusinessRoleCatalogSeeder` → `tbl_application` | Disagrees with `tenant.provisioning.modules` in `application.yml` (16 entries; `rnd`, `notification`) and with `AttachmentModule` (still has `VRM`, `ACCOUNT`) |
| Gateway exemption | `CompanyContextFilter` (was `OrganizationContextFilter`, P02-T11 stage D): CONTROL-PANEL + `/api/v1/platform/` forwards without a company | Lets platform staff reach the admin APIs |

## 3. Existing completed work

| Item | Status |
|---|---|
| Interceptor annotation framework, fail-closed on a missing path variable | COMPLETED (reused) |
| Realm-role-only platform authorities (client roles cannot impersonate) | COMPLETED |
| Gateway route for platform admin APIs without an organization | COMPLETED |
| Platform audit table and service | PARTIALLY_COMPLETED (columns added in this phase) |
| Module ids and gateway tokens (code facts) | COMPLETED as input data for the seed |

## 4. Reusable components

`PlatformSecurityInterceptor`, `PlatformJwtGrantedAuthoritiesConverter`, `PlatformActorResolver` (with an IP fix), `PlatformAuditService`, `InternalServiceTokenAuthenticator`, the dual-mapping pattern (to be retired for admin APIs: keep only `/api/v1/platform/admin/**` for humans), `BusinessRoleCatalogSeeder` (reads `tbl_module` instead of `ModuleCatalog`).

## 5. Work requiring modification or redesign

| Item | Change |
|---|---|
| `PlatformAccessResolver` | No global bypass. It evaluates (a) the platform-realm roles, accepted **only** from a token of the platform realm (CD-06), (b) an ACTIVE `tbl_platform_admin` row, (c) the permission (the CP policy of those roles), (d) the company scope. A Super Admin passes (c) and (d). |
| `@PlatformAdminOnly` / `@PlatformSuperAdminOnly` | Kept for coarse gating; new `@RequiresPlatformPermission(code)` for fine-grained checks |
| Platform security chain | Issuer and audience validation is delivered in PHASE_03. In this phase: replace `anyRequest().permitAll()` with `denyAll()` after the legacy endpoints are listed explicitly. |
| `PlatformActorResolver` | Take the client IP from the gateway-set `X-Real-IP` / `X-Forwarded-For` only when the request comes from the gateway (trusted proxy list) |
| `ModuleCatalog`, `tenant.provisioning.modules`, `tbl_application` | Replaced by `tbl_module` |
| Admin controllers in `controller/internal` | Customer/organization endpoints become company endpoints under `/api/v1/platform/admin/companies/**` |

## 6. New development required

### 6.1 Platform admin model
- Entities `PlatformAdmin` (`tbl_platform_admin`) and `PlatformAdminCompanyScope`. There is **no** `PlatformAdminRole` entity (CD-11: the assignment is in the platform realm).
- The platform-domain rows of `tbl_role` (role definitions) / `tbl_permission` / `tbl_role_permission` (policy) are created here for the PLATFORM domain only. PHASE_10 extends them to the other domains (CP Permission Policy, CD-12).
- A seed of platform permissions (master §8.2) with the `grantable` flag. The predefined platform roles **exist in the platform realm** (operator runbook, PHASE_03), and their policy is seeded in the CP:
  - `PLATFORM_SUPER_ADMIN` (all, implicit; operator-assigned only, CD-04);
  - `PLATFORM_ADMIN` (the base platform-staff role);
  - `PLATFORM_SUPPORT` (read companies, provisioning status);
  - `PLATFORM_SALES` (catalog read, orders and quotes; manual payment **recording** only, never approval);
  - `PLATFORM_OPERATIONS` (provisioning, lifecycle except archive).

  These policy defaults are adjustable by the Super Admin. The final list of platform roles is owner-confirmed; additional roles are created in the platform realm.
- `PlatformAdminService`:
  - create a Platform Admin (invite by email through the identity-sync outbox, task `ASSIGN_PLATFORM_ROLE`, **in the platform realm only**);
  - assign or revoke platform roles **in the platform realm** (outbox `ASSIGN_PLATFORM_ROLE` / `REVOKE_PLATFORM_ROLE`; only roles whose permissions are `grantable`; never `PLATFORM_SUPER_ADMIN`, never `platform.admin.manage`);
  - set the scope (`ALL` or a company list);
  - suspend or disable the admin (the outbox removes the realm role and disables the account when the scope is PLATFORM).
- `GET /api/v1/platform/me` (created in PHASE_03) is extended with the effective platform permissions and the scope.
- **Bootstrap** of the first Platform Super Admin is delivered in PHASE_03 (PHASE_01 §6.5.5, CONFIRMED DECISION CD-04). This phase adds the creation of further Platform **Admins** by the Super Admin.

### 6.2 Company lifecycle APIs (platform)

| Endpoint | Permission |
|---|---|
| `POST /api/v1/platform/admin/companies` | `platform.company.create` |
| `GET /api/v1/platform/admin/companies`, `GET …/{companyCode}` | `platform.company.read` (filtered by scope) |
| `PATCH …/{companyCode}` (profile, display name, slug before ACTIVE) | `platform.company.update` |
| `POST …/{companyCode}/suspend|reactivate|disable|archive` | `platform.company.lifecycle` (archive: Super Admin) |
| `POST …/{companyCode}/company-super-admin` (assign/replace) | `platform.company-admin.assign` (implemented with PHASE_10 membership) |

Status transitions follow master §7.1 and are recorded in `tbl_company_status_history` and the audit log. A lifecycle change publishes `TENANT_STATUS` (PHASE_06 publisher) and the tenant invalidation event (PHASE_03).

### 6.3 Module catalog
- Entities `Module` (`tbl_module`) and `ModuleDependency` (`tbl_module_dependency`). Columns are in master §11.1–11.2.
- **Seed** (during development an insert-if-missing seeder in code; in the final CP Liquibase baseline a data changeset, `context=seed`, PHASE_11A, CD-27) of the initial catalog in master §11.1 and the dependency set in master §11.2. Each row's `reason` cites the code evidence.
- `ModuleCatalogService`:
  - CRUD for DRAFT modules;
  - activate / retire;
  - edit display fields;
  - `db_token`, `gateway_token` and `service_name` are **immutable after ACTIVE**.
- `ModuleDependencyService`:
  - add or remove dependencies, with cycle detection (DFS) and self-reference rejection;
  - an impact preview: which bundles and subscriptions are affected;
  - removing a MANDATORY dependency while companies rely on it is allowed only with an explicit confirmation flag, and is audited.
- Internal API `GET /api/v1/internal/platform/modules` (tokens and service names) for the gateway and the services, cached.
- Consumers switch to the database catalog:
  - provisioning plans (PHASE_08);
  - the entitlement engine (PHASE_06);
  - the role-policy seeder;
  - the gateway token list (static routes stay in YAML; the token comes from the first path segment, as today).

### 6.4 Catalog consistency checks
A startup self-check in the control plane compares `tbl_module.service_name` with the Eureka registrations (warning only) and with the gateway route prefixes (internal API). It reports drift as found today (`rnd` vs `research-and-development`).

> **As implemented (P04-T8, DONE 2026-10-07; owner decisions T8-2 (C), T8-3):** the runtime self-check compares the catalog with the Eureka registrations only (WARN log, fail-safe, startup + scheduled). The Gateway comparison (`gateway_token` → route path prefix `/<token>/**`, `service_name` → `lb://<service_name>`) is a build-time test against the Gateway `application.yml`; no Gateway internal API was added and the Gateway was not changed.

## 7. Exact services, files and database areas involved

| Area | Files |
|---|---|
| Control-plane security | `CONTROL-PANEL-SERVICE/.../platform/security/*` (resolver, interceptor, new annotation, config) |
| Platform admin | new `platform/admin/*` (model, repository, service, controller) |
| Company APIs | `platform/controller/` (new `PlatformCompanyController`), `platform/service/CompanyService` (PHASE_02) |
| Catalog | new `platform/catalog/*`; remove `platform/entitlement/ModuleCatalog.java` after its callers migrate; *(callers migrated in P04-T7; the file deletion is an owner SVN action, D7-9)* `rbac/BusinessRoleCatalogSeeder.java` |
| Configuration | `CONTROL-PANEL-SERVICE/src/main/resources/application.yml` (remove `tenant.provisioning.modules` once PHASE_08 uses the catalog; issuer/audience settings) |
| Identity sync | `platform/identity/IdentitySyncProcessor.java` (new task `ASSIGN_PLATFORM_ROLE` / `REVOKE_PLATFORM_ROLE`) |
| Database | `control_panel_db`: new JPA entities for the tables in §8 (schema by Hibernate `ddl-auto: update` during development; no CP changelog until PHASE_11A, CD-27) |

## 8. Proposed schema changes

New tables:
- `tbl_platform_admin`, `tbl_platform_admin_company_scope` (no `tbl_platform_admin_role`, CD-11);
- `tbl_role` (role definitions and templates), `tbl_permission`, `tbl_role_permission` (policy), created now with `role_domain` / `permission_domain`; only PLATFORM rows in this phase;
- `tbl_module`, `tbl_module_dependency`.

`tbl_platform_audit_log` gains: `company_id`, `outcome`, `permission_code`, `detail_json`, `correlation_id`.

The full column definitions are in PHASE_01 §6.2, groups C and G.

## 9. API and integration requirements

- **Human admin APIs** live only under `/api/v1/platform/admin/**` and `/api/v1/platform/me`, reachable through the gateway. The internal aliases `/api/v1/internal/platform/...` for human operations are removed; machine endpoints stay internal.
- Errors are consistent:

  | Status | Code |
  |---|---|
  | 403 | `PLATFORM_PERMISSION_DENIED` |
  | 403 | `COMPANY_OUT_OF_SCOPE` |
  | 409 | `INVALID_STATUS_TRANSITION` |
  | 409 | `DEPENDENCY_CYCLE` |

- **Identity sync**: assigning or removing the `PLATFORM_ADMIN` realm role in the **platform realm**. The outbox requires a realm-management permission for role mapping (owner Keycloak configuration).

## 10. Dependencies on other phases

| Phase | Relationship |
|---|---|
| PHASE_02 | Prerequisite: Company model (CP Liquibase is PHASE_11A, CD-27) |
| PHASE_03 | Prerequisite: platform identity bootstrap and token validation |
| PHASE_05 | Uses the catalog |
| PHASE_06 | Uses the catalog and the dependencies |
| PHASE_08 | Uses the catalog for provisioning plans |
| PHASE_10 | Extends the permission and role-policy tables to the company-admin and business domains (CP Permission Policy, CD-12); company role assignments stay in the company realms |

## 11. Security and tenant-isolation requirements

1. Every admin endpoint is protected by `@RequiresPlatformPermission` **and** a scope check against the target company. Missing either means deny.
2. Platform identities can never get a tenant claim. A test proves that the gateway rejects platform identities on business routes.
3. A Platform Admin cannot grant permissions they do not hold. They can never grant non-grantable permissions, and they cannot modify their own roles or scope.
4. Only the platform operator can create Platform Super Admins (CD-04), manually in the platform realm plus the PHASE_03 bootstrap procedure. No platform API creates a Super Admin, and **company administrators can never create, invite or elevate a Platform Super Admin** (company APIs operate only on company realms, where platform roles do not exist).
5. Catalog writes are Super Admin only (`platform.catalog.manage` is grantable only if Q-16 allows; the default is not grantable).

## 12. Ordered implementation tasks

| # | Task | Status |
|---|---|---|
| 1 | JPA entities and schema for the platform admin, permission and role-policy core (domains) and catalog tables (Hibernate `ddl-auto: update`; **no CP Liquibase changelog**, CD-27; the changelogs are written once in PHASE_11A) | DONE |
| 2 | Seed the platform permissions and system platform roles | DONE |
| 3 | `PlatformAccessResolver` redesign + `@RequiresPlatformPermission`; `denyAll` default | DONE |
| 4 | `PlatformAdminService` (invite or suspend Platform Admins, roles, scope); extend `/platform/me`; identity-sync platform-role tasks | DONE |
| 5 | Company lifecycle APIs (on the PHASE_02 `CompanyService`) | DONE |
| 6 | Module catalog entities, seed, services, cycle detection, internal module API | DONE |
| 7 | Switch `BusinessRoleCatalogSeeder` to `tbl_module`; remove `ModuleCatalog` (callers migrated; the unused `ModuleCatalog.java` file is deleted later by the owner, D7-9) | DONE |
| 8 | Catalog consistency self-check | DONE |
| 9 | Fix `PlatformActorResolver` IP source; extend the audit columns | DONE |

## 13. Acceptance criteria

1. A Platform Admin with only `platform.company.read` and scope {CMP_101} can read CMP_101 and gets 403 for CMP_102 and for any write.
2. A Platform Admin can never call `platform.admin.manage` endpoints, even with a crafted role.
3. Every admin mutation creates an audit row with actor, permission, company, outcome and detail.
4. The catalog seed loads 19 modules (the 18 rows of master §11.1 + the CD-28 PLATFORM_CORE `control-panel` row; P04-T6, decisions T6-2..T6-4 — originally written as "18 modules") and the dependency set. Adding a dependency that forms a cycle returns 409 `DEPENDENCY_CYCLE`.
5. No code path still reads `ModuleCatalog` or `tenant.provisioning.modules`. Static check: grep in CI. *(Status P04-T7, DONE: no runtime code reads either; the static check is the CP unit test `LegacyModuleCatalogUsageTest`. `ModuleCatalog.java` stays as unused source until the owner deletes it (D7-9, owner SVN action); the `tenant.provisioning.modules` key stays in `application.yml`, unread, until PHASE_08.)*
6. Platform-admin endpoints are reachable only by tokens of the **platform realm** with an ACTIVE `tbl_platform_admin` row (`account_scope=PLATFORM`); company-realm tokens get 403.

## 14. Testing and validation checklist

- [ ] Unit: resolver decision table (role × row status × permission × scope), interceptor annotations, escalation guards, cycle detection.
- [ ] MySQL IT: admin CRUD, scope filtering in lists, audit rows, catalog seed and constraints.
- [ ] Web-layer tests for every admin endpoint: 401/403/200 matrix.
- [ ] Identity-sync IT with the in-memory provider for the platform role tasks; **one controlled run against the local test Keycloak** before enabling it (it was never run against a real Keycloak, `NOT-VERIFIED`).
- [ ] Regression of the existing control-plane tests.

## 15. Risks and rollback considerations

| Risk | Mitigation |
|---|---|
| Locking out every platform admin after the resolver change | The PHASE_03 bootstrap procedure + operator runbook; test on a copy first |
| Catalog seed differs from the deployed services | Consistency self-check; tokens taken from code evidence |
| Keycloak role-mapping permission missing | Owner configures realm-management `manage-users` + role mapping for the service account; documented in the PHASE_12 runbook |

**Rollback:** the new tables are additive. The resolver change is behind the property `platform.access.model=v2`, defaulting to v1 until the acceptance tests pass. The catalog is read alongside the old `ModuleCatalog` until task 7. *(2026-10-07: acceptance criteria 1, 2 and 6 are proven under `v2` in tests; the runtime default stays `v1` by owner decision D-D (a) — switching to `v2` is a separate owner decision. Task 7 is DONE.)*

## 16. Current status

| Item | Status |
|---|---|
| Annotation framework, realm-role authorities, gateway admin route | COMPLETED |
| Platform audit | DONE (P04-T9: explicit columns, outcome, DENIED audit, trusted client IP) |
| Platform admin permissions and scope | DONE (P04-T1 model, P04-T2 seed, P04-T3 enforcement under `v2`, P04-T4 admin management; runtime default `v1`, D-D (a)) |
| Company lifecycle APIs (company model) | DONE (P04-T5) |
| Database-driven catalog and dependencies | DONE (P04-T6 catalog, P04-T7 consumers, P04-T8 consistency self-check) |
| **Phase** | **DONE** (2026-10-07; 9 / 9 owner-approved; decisions R-1..R-4; PHASE_05 stays LOCKED). Earlier the same day: LOCKED by governance (D-C (b)) until PHASE_02 / PHASE_03 were DONE |

*Earlier assessment (2026-10-03, before implementation): platform audit PARTIALLY_COMPLETED, platform admin permissions and scope PENDING, company lifecycle APIs PARTIALLY_COMPLETED (organization lifecycle only), database-driven catalog PENDING (hard-coded catalog), phase PARTIALLY_COMPLETED.*
