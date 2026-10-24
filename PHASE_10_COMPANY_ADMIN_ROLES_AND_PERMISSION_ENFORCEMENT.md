# PHASE 10 — Company Administration, Roles and Permission Enforcement

| Item | Value |
|---|---|
| Master architecture | [SAAS_ARCHITECTURE_MASTER.md](SAAS_ARCHITECTURE_MASTER.md) §9, §10, §13.6, §14.1 |
| Design source | [PHASE_01 §0 (CD-02, CD-04, CD-06, CD-11), §6.2 F and G, §6.7](PHASE_01_SAAS_FOUNDATION.md) |
| Depends on | [PHASE_06](PHASE_06_SUBSCRIPTIONS_AND_ENTITLEMENTS.md) (entitlements, user allowance), [PHASE_03](PHASE_03_IDENTITY_KEYCLOAK_AND_COMPANY_CONTEXT.md) (service token verification, realm registry, company realm template with the predefined roles, realm-aware identity sync), [PHASE_08](PHASE_08_COMPANY_PROVISIONING.md) (Company Super Admin step), [PHASE_04](PHASE_04_PLATFORM_ADMIN_AND_MODULE_CATALOG.md) (permission catalog and policy core) |
| Enables | PHASE_11, PHASE_13, PHASE_12 |
| Status | **PARTIALLY_COMPLETED** |

> **Revision 2026-10-03 (final review, CD-11):**
> - **CONFIRMED DECISION CD-11 (closes Q-50):** company user roles **and their assignment** live in the **company's Keycloak realm**.
>   - The Control Plane is **not** the runtime source of truth for who holds which role.
>   - The CP keeps the membership mirror (seats, status, audit), the permission catalog and the **CP Permission Policy** (CD-12).
>   - The services enforce authorization: token roles + policy + entitlement.
> - **Withdrawn:** the CP role-assignment tables (`tbl_company_user_role`), per-user module access (`tbl_company_user_module_access`), and the per-user caches `TENANT_USER_ACCESS` / `TENANT_USER_PERMS`.
> - Earlier v5 changes stay:
>   - **CD-06:** users live in the company realm; no `tenant_id` user attribute, no `ORG_` groups.
>   - **CD-02:** one login = one company.
>   - **CD-04:** no company API can create, invite or elevate a Platform Super Admin.
> - **Final decision review (2026-10-03):**
>   - **CD-12 (Q-54 closed):** CP Permission Policy for predefined **and custom** roles (role → module → permission → operation, policy version);
>   - **CD-13 (Q-55 closed):** company admins manage users and roles only through the ERP Company Admin UI and backend; no Keycloak console;
>   - **CD-14 (Q-56 closed):** company business roles are **realm roles**, read from `realm_access.roles`; no duplicate client roles;
>   - **CD-16 (Q-47 closed):** Shivam gets a new standard realm; its users are migrated separately.
> - Terminology used below: **Keycloak Role Assignment** · **CP Permission Policy** · **Runtime Authorization**.
> - **Q-11 decided (CD-24):** a Company Super Admin cannot appoint another Company Super Admin; only the platform (or provisioning) assigns `COMPANY_SUPER_ADMIN`.
> - **Q-27 decided (CD-24):** MFA (OTP) is mandatory for Company Super Admins and Company Admins (realm policy for these roles); optional for other users.

---

## 1. Objective

1. **Company membership** (`tbl_company_user`, a mirror of the company-realm account): invite, create, update, activate, deactivate, with **seat (billable user) enforcement**.
2. **Company Super Admin** (`COMPANY_SUPER_ADMIN` role in the company realm) assigned by the platform or by provisioning; **delegated Company Admins** through company-admin roles.
3. **Company roles in the company realm** (CD-11):
   - predefined roles from the realm template: company-admin roles, plus module roles for the purchased modules; assignable, but not editable or deletable by companies;
   - company custom roles `CUSTOM_<NAME>` (CD-12, CD-14);
   - **role assignment** written to the company realm through its admin API.
4. **Permission catalog** registration from the services, and the **CP Permission Policy** with data scope and policy version (CD-12).
5. **Runtime policy cache** `TENANT_ROLE_PERMS:<companyCode>:<role>`.
6. Keycloak integration through the realm-aware outbox:
   - users created in the company realm;
   - invitations sent from the company realm;
   - role assignments;
   - disabling deactivated users.
7. Company-scoped **user lookup APIs** that replace the realm-wide legacy endpoints.
8. **Company profile and branding** (replacing the hard-coded Shivam branding).
9. **Backend enforcement** in the gateway and every service (role of the module + permission + entitlement), plus removal of the legacy unverified role checks.

## 2. Existing implementation relevant to this phase

> **P02-T11 note (stage G, 2026-10-05):** the tables of this section describe the code as inspected on 2026-10-03. P02-T11 stages A–F (2026-10-04/05, REVIEW) have since replaced the organization tenancy: the CP organization / customer tier (`Customer`, `Organization`, `OrganizationDatabase`, `OrganizationUser`, `OrganizationUserBusinessRole`, `OrganizationQuotaService`, `CompanyOrganizationService`, `OrganizationGroupProvisioner`, `@OrganizationScoped`, `PlatformOrgAdminController`, …) is deleted (stage C); `InternalOrganizationController` → `InternalCompanyController` (`/api/v1/internal/platform/companies/...`), `OrganizationEntitlementSyncListener` → `CompanyEntitlementSyncListener`, `CustomerUser` → `CompanyUser` (`tbl_company_user`), `CustomerUsage` → `CompanyUsage`; runtime `TenantContext.companyCode`, header `X-Company-Code`, Redis `COMPANY_MODULES:<CMP_n>`, resource names `cmp_<n>_…` / `cmp-<n>-…` (stages A / B); gateway `CompanyContextFilter` / `CompanySelection`, company from the verified `tenant_id` claim, no `Active-Org` (stage D). Read old names in this section as these replacements.

| Component | Location | Notes (`CODE-INSPECTED`) |
|---|---|---|
| Identity and memberships | `platform/model/Identity.java`, `CustomerUser.java`, `OrganizationUser.java` | `account_scope` immutable. UNIQUE `identity_id` on each membership table, so one account belongs to one workspace. Role column = `PlatformRole` (tiers incl. `COMPANY_SUPERADMIN`). |
| `PlatformMembershipService` | `platform/service/` | assign / revoke / list / invite (org); invite company (service only, **no endpoint**); rank checks; no self-revoke; every change enqueues `SYNC_IDENTITY` |
| Self-service controllers | `PlatformCompanyAdminController` (`/api/v1/platform/companies/{id}`), `PlatformOrgAdminController` (`/api/v1/platform/organizations/{id}`; deleted in P02-T11 stage C, 2026-10-05) | No first-company-admin bootstrap yet (company invitation `POST /api/v1/platform/companies/{code}/invitations` added in P02-T11 stage C) |
| Business RBAC (CP) | `BusinessRole`, `Permission`, `BusinessRolePermission`, `OrganizationUserBusinessRole` + `BusinessRoleCatalogSeeder` | 5 SYSTEM roles per module seeded; CUSTOM per organization; **no API**; nothing enforced. **CURRENT/LEGACY:** the CP assignment table is not carried into the target (CD-11). |
| Roles in Keycloak today | CRM `CrmDataAccessAuthority`, ACCOUNT-BOOK `KeycloakAccountBookRolesConverter`, CP `JwtRoleExtractor`, COMMON `JwtAccessTokenUtil` | **CURRENT/LEGACY:** business roles are Keycloak **client roles** (`resource_access`) in the single `ERPSystem` realm, partly decoded without verification. This already matches "roles in Keycloak", but in one shared realm. |
| Identity sync | `platform/identity/*` | ORG_ groups (dropped), invitations (passwordless user + execute-actions email), reconciler; single realm only (to be made realm-aware); off by default; `NOT-VERIFIED` against a real Keycloak |
| Legacy user APIs | `restcontroller/KeycloakController`, `KeycloakUserController`, `RoleController`; `service/KeycloakService`, `KeycloakUserCacheService` | Realm-wide user lists used by ~10 services (`UserLookupService`, `ControlPanelClient`) |
| Branding | CRM `EmailProperties`, QC `PdfReportService` / `SalesOrderMailService`, R&D `SalesOrderMailService`, HRMS letter templates, ATTENDANCE report, COMPLIANCE email template, static logos | Shivam-specific, hard-coded |
| Tests | `PlatformMembershipServiceTest`, `PlatformAccessResolverTest`, `PlatformAccessControlMySqlIT`, `IdentitySyncMySqlIT` (stale assertions), `BusinessRoleModelTest` | `TEST-EXISTS` |

## 3. Existing completed work

| Item | Status |
|---|---|
| Identity table and immutable account scope | COMPLETED (reused) |
| Membership rank and escalation guards (pattern) | PARTIALLY_COMPLETED (rules re-expressed with permissions) |
| Identity-sync outbox, worker, backoff, reconciler | PARTIALLY_COMPLETED (needs realm awareness, role tasks and a real Keycloak verification) |
| Invitation flow (passwordless + verify email) | PARTIALLY_COMPLETED (reused for company users) |
| Business-role definitions and system-role seeding | PARTIALLY_COMPLETED (kept as role definitions and policy templates; assignments move to the realm) |

## 4. Reusable components

`Identity`, `IdentitySyncOutbox`, `IdentitySyncWorker`, `IdentitySyncProcessor` (with realm-aware role tasks), `KeycloakIdentityProviderAdmin` (realm-parameterized, role-mapping operations), `KeycloakAdminTokenProvider`, `BusinessRoleCatalogSeeder` (as the policy-template seeder), the rank and escalation logic from `PlatformMembershipService`, `OrganizationQuotaService` (the locking pattern for seats).

## 5. Work requiring modification or redesign

| Item | Change |
|---|---|
| `tbl_customer_user` + `tbl_organization_user` | Merged into `tbl_company_user` (status INVITED/ACTIVE/INACTIVE/REMOVED, `is_company_super_admin` as a mirror). It holds membership and seats, **not roles**. |
| `PlatformRole` company/org tiers | Replaced by company roles **in the company realm**; `PlatformRole` keeps only the PLATFORM tier (platform realm) |
| `tbl_business_role` | Becomes `tbl_role`: role **definitions and policy templates** (`role_domain`; Hibernate `@Check` during development, DB CHECK in the final CP Liquibase baseline, PHASE_11A, CD-27); service guards for predefined-role immutability |
| `tbl_organization_user_business_role` | **Removed** (CD-11): role assignments live in the company realm |
| Keycloak `ORG_` groups | **Dropped** (CD-06): the company is the realm |
| Legacy client-role checks (`resource_access`, unverified decoding) | Replaced by the central verified `PermissionEvaluator` reading the roles of the verified company-realm token: `realm_access.roles` only (CD-14). **CURRENT/LEGACY → migration**: client-role names are mapped to realm roles |
| Legacy realm-wide user APIs | Replaced by company-scoped lookups; the legacy endpoints are removed after the callers migrate (PHASE_11) |
| Hard-coded branding | `tbl_company_profile` + a profile API + a branding resolver in COMMON-LIBRARY |

## 6. New development required

### 6.1 Company membership service
- `CompanyUserService` (all writes to Keycloak go to the **caller's own company realm**):
  - `invite(email, roles)`: seat check under the company lock, then INVITED, then outbox `INVITE_COMPANY_USER` **in the company realm**. The task creates the Keycloak user without a password, assigns the requested realm roles (`ASSIGN_COMPANY_ROLE`) and sends the execute-actions email from that realm. No tenant attribute is written: the realm stamps `tenant_id`.
  - `activate` (seat check), `deactivate` (frees the seat; outbox `DISABLE_ACCOUNT` + session revocation), `reactivate` (seat check; `ENABLE_ACCOUNT`).
  - `update`: profile attributes go to Keycloak through the outbox.
  - `remove`: REMOVED; the account is disabled, not deleted; the history is kept.
- **Seat accounting** (master §13.6): `tbl_company_usage.billable_users` is maintained in the same transaction and reconciled daily against the realm users.
- **No cross-company accounts** (CD-02): the same email address in two companies means two separate accounts in two realms, with separate credentials and roles.
- **Shivam (CD-16):** Shivam's users are created in Shivam's new standard realm by the **separate identity migration** (PHASE_01 §6.8.0 B; tool in PHASE_11, execution in PHASE_12), not by this service. Afterwards they are managed exactly like any other company's users. The legacy role mapping follows PHASE_11 C-3.

### 6.2 Company Super Admin and Company Admins
- Platform API `POST /api/v1/platform/admin/companies/{code}/company-super-admin` (`platform.company-admin.assign`) creates or links the user **in the company realm** and assigns `COMPANY_SUPER_ADMIN` there. Replacement is a two-step handover, so the last Super Admin is never removed.
- Predefined company-admin roles (realm template):
  - `COMPANY_SUPER_ADMIN` (implicit all company-admin permissions; assigned by the platform or provisioning only);
  - `COMPANY_ADMIN` (user and role administration within the CP policy and the escalation guard).

  Further company-admin roles (e.g. an auditor role) can be added to the template later. Custom admin roles are `CUSTOM_<NAME>` roles with company-admin permissions (CD-12).
- **Company Admin flow (CD-13):** Company Admin → ERP Company Admin UI → ERP backend → authorization → company policy checks → Keycloak Admin API (`erp-identity-admin` of this realm only) → company realm. Company admins never get Keycloak console access.
- **Seat limit:** seats in use = INVITED + ACTIVE users. With a plan of 20 users and 18 in use, the 19th and 20th users are allowed and the 21st returns 409 `USER_LIMIT_REACHED`. This is checked in the backend under the company lock.
- **Guards** (master §9.3, PHASE_01 §6.7.5), enforced by the ERP backend before anything is written to the realm (CD-13):
  - a Company Admin can never assign `COMPANY_SUPER_ADMIN`, `COMPANY_ADMIN`, a platform role or a role of a non-entitled module;
  - a Company Super Admin can assign every company role except `COMPANY_SUPER_ADMIN` (platform or provisioning only; Q-11 default no);
  - the assigner must hold the permissions of every role they assign;
  - no self-elevation;
  - the last-super-admin guard;
  - company admins cannot manage Super Admins or holders of `company.admin.manage`;
  - company admins can never see, create or assign platform roles, or reach another realm (CD-04).

### 6.3 Roles and permission policy
- **Permission registration:** each service ships `META-INF/nyaalkaran/permissions.yml` (code, name, description, default data scope). The COMMON-LIBRARY `PermissionRegistrar` posts it on start-up to `POST /api/v1/internal/platform/permissions/register` (idempotent; unknown codes are added; missing codes become DEPRECATED).
- **Predefined role policy:** `rbac/system-roles.yml` in the control plane maps each predefined role (company-admin roles and the module roles, 5 levels per module today) to permission sets with data scope. It is versioned and applied by the seeder. The **same role names** are created in every company realm by the realm template (PHASE_03).
- **Custom roles** (CD-12, CD-14):
  - `RoleService` creates the role **in the company realm** and stores its permission policy as the company's CUSTOM `tbl_role` + `tbl_role_permission` rows, with a `policy_version`. The name `CUSTOM_<NAME>` is immutable;
  - permissions must belong to the role's domain and module;
  - delete is allowed only when the role is unassigned (otherwise disable);
  - predefined roles reject update and delete (409 `SYSTEM_ROLE_IMMUTABLE`).
- `RoleAssignmentService` (`company.role.assign`):
  - assigns or unassigns realm roles of company users **in the company realm** (outbox `ASSIGN_COMPANY_ROLE` / `REVOKE_COMPANY_ROLE`);
  - the role's module must be entitled, and the assigner must hold the role's permissions;
  - the CP stores **no** assignment row. It writes an audit entry, and the outbox task records the requested change until it is applied.
- **Module access** is expressed by module roles. There is no separate module-access grant.

### 6.4 Policy cache publication
`RolePolicyPublisher` writes `TENANT_ROLE_PERMS:<companyCode>:<role>` (permission codes with data scope, filtered to the company's entitled modules) after commit of any policy, custom-role or entitlement change. The TTL is 300 s, plus a resync and an on-demand rebuild through an internal API.

Per-user data is **not** cached by the CP. The roles come from the verified token.

### 6.5 Company console APIs (gateway `/control-panel/api/v1/company/**`, company from the token)

| Endpoint | Permission |
|---|---|
| `GET/POST/PATCH /users`, `POST /users/{id}/activate|deactivate|reactivate`, `POST /users/invitations` | `company.user.*` / `company.user.invite` |
| `GET/POST/PATCH/DELETE /roles`, `GET /permissions?module=` | `company.role.read|manage` (custom roles, CD-12) |
| `PUT /users/{id}/roles` (written to the company realm) | `company.role.assign` |
| `GET/PATCH /profile` (branding, contacts) | `company.profile.read|update` |
| `GET /me` (roles from the token, effective permissions, entitled modules) | authenticated company user |
| `GET /subscriptions`, `/entitlements`, `/limits` (PHASE_06 / P06-T7; canonical plural path, D-T7-7) | `company.subscription.read`, `company.usage.read` (until PHASE_10: realm role `COMPANY_SUPER_ADMIN`, D-T7-5) |
| `GET /audit` | `company.audit.read` |

### 6.6 Company-scoped user lookup (replacing the legacy APIs)
- Internal `GET /api/v1/internal/platform/companies/{companyCode}/users?ids=|department=|role=` returns only members of that company. Display data and role filters are read from **that company's realm** (cached per company).
- COMMON-LIBRARY `CompanyUserDirectory` client replaces each service's `UserLookupService` / `ControlPanelClient` user calls (migration of the callers in PHASE_11).

### 6.7 Company profile and branding
- `tbl_company_profile` (PHASE_02) gets the API (§6.5). The logo is stored in the company's `cmp-<n>-control-panel-bucket` under `platform/branding/`.
- COMMON-LIBRARY `CompanyBrandingResolver` (cached, from an internal API) gives the services the name, logo, mail sender and reply-to for emails, PDFs and letters. The services replace their hard-coded values in PHASE_11.

### 6.8 Permission enforcement (gateway, services, control plane)
**Gateway module-role check** (`API-GATEWAY/.../filter/CompanyContextFilter.java`):
- Runs after the issuer, company, status and entitlement checks (PHASE_03, PHASE_06).
- The verified company-realm token must hold at least one role of the target module, or `COMPANY_SUPER_ADMIN` (AD-11, entitled modules only).
- Otherwise it returns 403 `MODULE_ACCESS_DENIED`.

**Service permission evaluator** (COMMON-LIBRARY `security/permission/PermissionEvaluator` + `@RequiresPermission("<module>.<resource>.<action>")`):
- Reads the roles **only** from the verified token (`TenantVerificationFilter`, PHASE_03), then resolves them through `TENANT_ROLE_PERMS` (local cache of at most 30 s).
- Exposes the data scope (OWN, TEAM or COMPANY) to `OwnershipContext`.
- A Company Super Admin passes inside entitled modules (AD-11).
- **Role ≠ entitlement:** the entitlement filter runs first and independently. A role of a non-entitled module grants nothing.

**Annotation rollout:**
- Every non-internal controller method of every module gets `@RequiresPermission`. The rollout goes module by module, behind `erp.security.permissions.enforce` (default **true** outside `dev`).
- A reflection test per module asserts that every endpoint is annotated, or explicitly public or internal.

**Legacy authorization removal:**
- Replace the legacy role checks with permission checks:
  - TICKETS `TicketRaiseController` `IT_MANAGER`, and the `userId` IDOR, which is now filtered by data scope;
  - CRM support chat;
  - COMPLIANCE controllers;
  - R&D `RoleAuthorizationService`;
  - LOGISTICS `RoleClient`;
  - HRMS/PAYROLL `TenantContext.requireAdmin`.
- Remove the unverified token decoders: the `JwtAccessTokenUtil` role methods, the `CrmDataAccessAuthority` decoding, `JwtRoleExtractor` and `/api/user-roles/extract`.

**Service entitlement filter:**
- `ModuleEntitlementEnforcementFilter` is **enabled** in every service (`MODULE_ENTITLEMENT_ENFORCE=true`, owner-managed `.env`), with the correct module token.
- Feign calls to modules that are not mandatory dependencies handle 403 `MODULE_NOT_ENTITLED` softly (log, skip, never fail the user transaction).

**Audit API:** `AuditLogController` requires `company.audit.read`.

**Control-plane company APIs:** `@RequiresCompanyPermission("company.…")`, with the company taken from the verified token.

## 7. Exact services, files and database areas involved

| Area | Files |
|---|---|
| Control plane | `platform/service/PlatformMembershipService.java` → new `platform/company/user/*`; `platform/rbac/*` (`RoleService`, `RoleAssignmentService` (realm writes), `PermissionRegistryService`, `RolePolicyPublisher`, seeder + `system-roles.yml`); `platform/identity/IdentitySyncProcessor.java` (tasks `INVITE_COMPANY_USER`, `ASSIGN_COMPANY_ROLE`, `REVOKE_COMPANY_ROLE`, `CREATE_COMPANY_ROLE`, `DISABLE_ACCOUNT`, `ENABLE_ACCOUNT`); new `platform/controller/company/*`; `platform/security/*` (company permission evaluation for control-plane company APIs) |
| COMMON-LIBRARY | new `security/permission/PermissionRegistrar`, `directory/CompanyUserDirectory`, `branding/CompanyBrandingResolver` |
| COMMON-LIBRARY (enforcement) | new `security/permission/PermissionEvaluator`, `RequiresPermission`; `ownership/*`; `audit/controller/AuditLogController.java`; `util/JwtAccessTokenUtil.java` (role methods removed) |
| API-GATEWAY | `filter/CompanyContextFilter.java` (module-role check) |
| Domain services | Every controller (annotations); the legacy role-check sites listed in §6.8; CRM `config/CrmDataAccessAuthority.java`; each `application.yml` (enforcement flags) |
| Keycloak | Company realms: predefined roles created by the template (PHASE_03); role assignments and custom roles written through the admin API by this phase |
| Database | `control_panel_db`: `tbl_company_user` (membership mirror), `tbl_role` (definitions and templates), `tbl_role_permission` (policy), `tbl_permission`. **No** user-role or module-access table. |

## 8. Proposed schema changes

As defined in PHASE_01 §6.2, groups F and G. Key points:
- CHECK constraints (Hibernate `@Check` during development; database CHECKs in the PHASE_11A baseline, CD-27) for role type, company and level consistency, and for permission domain and module consistency;
- unique (`scope_key`, `code`);
- `tbl_company_user.identity_id` unique (CD-02);
- `tbl_identity` unique (`identity_realm_id`, `keycloak_user_id`) (CD-06);
- indexes on (`company_id`, `status`);
- **not created** (CD-11): `tbl_company_user_role`, `tbl_company_user_module_access`, `tbl_platform_admin_role`.

## 9. API and integration requirements

See §6.5 and §6.6. Error codes:

| Status | Code |
|---|---|
| 409 | `USER_LIMIT_REACHED` |
| 409 | `SYSTEM_ROLE_IMMUTABLE` |
| 409 | `ROLE_IN_USE` |
| 403 | `PERMISSION_ESCALATION` |
| 409 | `LAST_SUPER_ADMIN` |
| 403 | `MODULE_NOT_ENTITLED` |
| 403 | `MODULE_ACCESS_DENIED` |
| 403 | `PERMISSION_DENIED` |

Keycloak operations go through the realm-aware outbox. No synchronous Keycloak call happens in a user-facing transaction. A role change becomes effective when the task is applied **and** the user's next token is issued (≤ 5 minutes). Urgent removals also revoke the sessions.

## 10. Dependencies on other phases

| Phase | Relationship |
|---|---|
| PHASE_03 | Prerequisite: realm template (predefined roles), token verification, realm-aware identity sync |
| PHASE_04 | Prerequisite: permission catalog and policy core, module catalog |
| PHASE_06 | Prerequisite: entitlements, allowance |
| PHASE_08 | Consumes the Company Super Admin step; `ADD_MODULE` adds the module roles to the realm |
| PHASE_11 | Migrates the user lookups and branding to the new APIs; legacy role mapping (C-3) |
| PHASE_12 | Runs Shivam's separate identity migration (CD-16) with the legacy role → realm role mapping (CD-14) |

## 11. Security and tenant-isolation requirements

1. Every company API takes the company from the verified token (registry(issuer) = `tenant_id`), never from a path or body parameter.
2. Roles are read **only** from the verified token of the company realm. Roles, permissions or company ids sent by the frontend are ignored.
3. A user of company A can never receive a role of company B. Role writes target only the caller's own realm, resolved from the registry, never from input.
4. Escalation guards and the last-super-admin guard are covered by tests for every endpoint.
5. Role assignment is limited to entitled modules. Entitlement changes re-publish the policy cache, and a role of a non-entitled module grants nothing (role ≠ entitlement).
6. A deactivated user loses access within at most the token lifetime (≤ 5 minutes). `DISABLE_ACCOUNT` also revokes the Keycloak sessions.
7. The `tenant_id` claim comes from the realm's hard-coded mapper; no user or company admin can change it. Company APIs can never assign platform roles or create platform identities (CD-04). Tests cover both.
8. Changes made in Keycloak outside the ERP APIs (operator console) are detected by the realm reconciler and reported (CD-13).
9. **Identity sync is enabled only after one controlled run against the local test Keycloak** (`192.168.5.15:8081` per the project documents). It is never run against production Keycloak or the protected hosts.

## 12. Ordered implementation tasks

| # | Task | Status |
|---|---|---|
| 1 | JPA entity and schema changes for the company user, role-definition, role-permission and permission tables (Hibernate during development; **no CP Liquibase changelog**, CD-27; final baseline in PHASE_11A) | PENDING |
| 2 | `CompanyUserService` + seat accounting + realm-aware outbox tasks | PARTIALLY_COMPLETED (invite and membership logic exists for the org/company tiers) |
| 3 | Company Super Admin assignment API (realm role) + company-admin guards | PENDING |
| 4 | Permission registration (COMMON registrar + control-plane endpoint) + predefined role policy | PENDING |
| 5 | `RoleService` (custom roles in the realm + policy) and `RoleAssignmentService` (realm role mapping) | PENDING |
| 6 | `RolePolicyPublisher` (`TENANT_ROLE_PERMS` + policy version) | PENDING |
| 7 | Company console APIs (ERP Company Admin UI backend, CD-13) | PENDING |
| 8 | Company-scoped user directory API + COMMON client | PENDING |
| 9 | Company profile and branding API + COMMON resolver | PENDING |
| 10 | Controlled identity-sync test run of the company-user and role tasks against the local test Keycloak (configuration from PHASE_03) | PENDING |
| 11 | Fix the stale `IdentitySyncMySqlIT` assertions | PENDING (PHASE_02 task 0) |
| 12 | Gateway module-role check (`realm_access.roles`, CD-14) | PENDING |
| 13 | `PermissionEvaluator` + `@RequiresPermission`, module by module, with reflection coverage tests | PENDING |
| 14 | Replace the legacy role checks, remove the unverified decoders, fix the Tickets IDOR | PENDING |
| 15 | Enable the service entitlement filter everywhere; soft handling of non-mandatory Feign targets | PARTIALLY_COMPLETED (the filter exists, off) |
| 16 | Audit endpoint permission; control-plane company-permission annotations | PENDING |

## 13. Acceptance criteria

1. With a user allowance of 5, a sixth invitation returns 409. Deactivating one user allows the invitation.
2. A Company Admin holding only `company.user.read` gets 403 on create, update and role assignment.
3. Predefined roles exist in every new company realm (template). A predefined-role update returns 409. A custom role `CUSTOM_ROLE_SALES` can be created, assigned and deleted when unassigned.
3a. **Worked example (PHASE_01 §6.7.6):** John (`CRM_MANAGER`, CRM ACTIVE) updates a customer → allowed; the same with CRM EXPIRED → 403; Mary (`CRM_USER`) deletes a customer → 403.
4. **Role ≠ entitlement:**
   - a user holding `CRM_MANAGER` in a company **without** an active CRM subscription gets 403 `MODULE_NOT_ENTITLED`;
   - a user **without** a CRM role in a company **with** CRM gets 403;
   - only role + permission + entitlement together allow the operation.
5. A role assigned through the ERP API appears in the company realm and in the user's next token. No CP table holds the assignment.
6. A company user lookup returns only that company's users. The legacy realm-wide endpoints are no longer called by the migrated services (PHASE_11).
7. Every company user exists only in their company realm. Their token is rejected for any other company. A company admin's attempt to assign a role in another realm, or to create a platform user or platform role, returns 403.
8. Every business endpoint of every module requires a permission (reflection tests pass). A user without `crm.customer.create` gets 403 on customer creation, through the gateway **and** when calling the service directly with a valid token.
9. With the entitlement filter on everywhere, a company entitled to {asset} can use ASSET, and every other module returns 403 at the gateway and in the service.
10. No `JWT.decode(` and no unverified Base64 token payload decoding remain in `src/main` (grep check).

## 14. Testing and validation checklist

- [ ] Unit tests: the guard decision tables, the effective-access algorithm (master §10.5) including role-without-entitlement and entitlement-without-role, the seat counting.
- [ ] MySQL + Redis IT: invite → activate → deactivate, with seats; concurrent invitations at the limit; publication of the policy cache.
- [ ] Keycloak IT (local test Keycloak, two company realms): role assignment lands only in the caller's realm; cross-realm attempts fail.
- [ ] Web-layer permission matrix for every company API.
- [ ] Regression: the membership and access-control ITs, adapted.

## 15. Risks and rollback considerations

| Risk | Mitigation |
|---|---|
| Role drift between realms (predefined roles changed by hand) | Realm template as code; reconciler drift report; ERP-only administration (CD-13) |
| Role changes take up to one token lifetime to apply | Access token ≤ 5 minutes; session revocation for urgent removals |
| Permission catalog explosion / inconsistency | Code-declared catalogs; deprecation instead of deletion; predefined policy versioned |
| Seat drift | Transactional counter + daily reconciliation against the realm users |
| Locking out a company (last admin) | Last-super-admin guard; platform reassignment API |

**Rollback:** the APIs and publishers are additive. Enforcement is behind per-service flags (`erp.security.permissions.enforce`, `MODULE_ENTITLEMENT_ENFORCE`), so a single service can be rolled back without blocking the others. The legacy role checks are removed only after their replacement passes the module regression.

## 16. Current status

| Item | Status |
|---|---|
| Identity, outbox, invitation flow | PARTIALLY_COMPLETED |
| Company membership and seats | PENDING (organization/company tier memberships exist; seats are missing) |
| Company Super Admin / Company Admin model (realm roles) | PARTIALLY_COMPLETED (tier roles exist in the CP; realm-based model pending) |
| Role definitions, permission catalog, role → permission policy | PARTIALLY_COMPLETED (schema and seed only); design CONFIRMED (CD-12) |
| Role assignment in the company realm | PENDING (design CONFIRMED: CD-13, CD-14) |
| Permission enforcement in the gateway and services | PENDING (no `@PreAuthorize` or permission check exists today) |
| Company-scoped user lookup | PENDING |
| Company profile and branding | PENDING |
| **Phase** | **PARTIALLY_COMPLETED** |
