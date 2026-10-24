# PHASE 10 — Implementation Plan (Company Administration, Roles and Permission Enforcement)

| Item | Value |
|---|---|
| Phase document | [PHASE_10_COMPANY_ADMIN_ROLES_AND_PERMISSION_ENFORCEMENT.md](../PHASE_10_COMPANY_ADMIN_ROLES_AND_PERMISSION_ENFORCEMENT.md) (objective, acceptance §13). This plan **adds no decision**. |
| Design sources | PHASE_01 §0 (CD-02, CD-04, CD-06, CD-11..CD-14, CD-16, CD-24 Q-06 / Q-11 / Q-27, CD-26, CD-27), §6.2 F and G, §6.7; master §9, §10, §13.6; developer guide §11, §12 |
| Status | **DRAFT FOR OWNER REVIEW.** PHASE_10 is LOCKED until PHASE_03, PHASE_04, PHASE_06 and PHASE_08 are DONE. |
| Author / date | agent (Claude Opus 5.5), 2026-10-03 |
| Evidence | Local code inspection: CP `PlatformMembershipService`, `CustomerUser` / `OrganizationUser`, `PlatformRole` (tiers incl. `COMPANY_SUPERADMIN`, `ORG_*`), `BusinessRole*` + `BusinessRoleCatalogSeeder`, `platform/identity/*`, legacy `KeycloakController` / `KeycloakUserController` / `RoleController` / `JwtRoleExtractor`; COMMON `util/JwtAccessTokenUtil`, `entitlement/ModuleEntitlementEnforcementFilter` (off by default), `audit/controller/AuditLogController`; CRM `CrmDataAccessAuthority`; gateway `OrganizationContextFilter`. S0-5 baseline: `PlatformAccessControlMySqlIT` (8), `IdentitySyncMySqlIT` (14, 4 stale). |
| CP schema | CD-27: JPA entities, `ddl-auto: update`, no CP changelog. |
| Protected hosts | Keycloak ITs on Testcontainers Keycloak (as in PHASE_03). No Keycloak or DB on `192.168.5.52/53`. |

---

## 1. Summary

1. **Company membership mirror** `tbl_company_user` (replaces the kept legacy `CustomerUser`) with **seats** (INVITED + ACTIVE, Q-06) under the company lock.
2. **Company Super Admin** assignment by the platform (realm role in `co-<n>`), **Company Admins**, escalation and last-super-admin guards.
3. **Permission catalog registration** from every service, **predefined role policy**, **custom roles** (`CUSTOM_<NAME>`) in the realm + CP policy, **role assignment** in the realm through the outbox; **`TENANT_ROLE_PERMS`** publication.
4. **Company console APIs** (backend of the ERP Company Admin UI, CD-13), **company-scoped user directory**, **company profile and branding**.
5. **Enforcement:** gateway module-role check, `PermissionEvaluator` + `@RequiresPermission` in every service (reflection coverage), legacy role checks and unverified decoders removed, Tickets IDOR fixed, entitlement filter on everywhere, audit endpoint and CP company APIs annotated.

Size estimate: 16 tasks — the largest phase. About 150–220 changed files (most are annotations across ~17 services), about 45 test classes. Two agents can split: agent A control plane (T1–T11, T16), agent B enforcement (T12–T15) after T4/T6.

---

## 2. Preconditions

| # | Precondition | Tracker |
|---|---|---|
| 1 | PHASE_03 (verified tokens, realm-aware identity sync, `TenantVerificationFilter`), PHASE_04 (`Role` / `Permission` / `RolePermission` entities, platform resolver), PHASE_06 (entitlements, `AllowanceService`, usage), PHASE_08 (company realms with predefined roles) DONE | phases |
| 2 | S0-2 item 6 (signed-JWT key management for `erp-identity-admin`) for non-test realms | S0-2 |
| 3 | The ERP Company Admin UI owner (Q-25) — only for the UI; the backend does not wait | S0-6 |
| 4 | D10-1 answered (which Keycloak runs the controlled identity-sync run) | new (report) |

---

## 3. Owner decisions / clarifications

| ID | Question | Options | Recommendation |
|---|---|---|---|
| **D10-1** (report) | PHASE_10 §11.9 names the "local test Keycloak" as `192.168.5.15:8081`; PHASE_03 removes `192.168.5.15` defaults from code and the PHASE_03 plan runs every Keycloak test on Testcontainers. | (a) All automated tests on Testcontainers Keycloak; the one "controlled run" (T10) on Testcontainers too, recorded with the realm export. (b) Additionally one run on the `.15` development Keycloak, owner-supervised. `.52` / `.53` are never used. | **(a)**; (b) only on explicit owner request. |
| **D10-2** (report) | The 5 module role levels (`USER`, `TEAM_LEAD`, `MANAGER`, `ADMIN`, `SUPER_ADMIN`) × 16 business modules = 80 predefined module roles per realm, plus company-admin roles. | Keep the 5 levels (PHASE_01 §6.5.9); the predefined policy file assigns permissions per level and module. | As designed; performance checked in the PHASE_12 capacity test (51 realms). |
| **D10-3** (report) | Legacy client-role names (`CRM_SUPERADMIN`, `IT_MANAGER`, `COMPLIANCE_*`, …) must map to the new realm roles for Shivam (PHASE_11 C-3, P11-T7). | The mapping table is a PHASE_11 deliverable; PHASE_10 removes the legacy checks only after each module's permission annotations pass. | As designed. |

---

## 4. Task order

```text
T11 stale IT fix (if not closed in P02-T0) ─> T1 entities ─> T2 CompanyUserService + seats + outbox tasks ─> T3 Company Super Admin + guards
T4 permission registration + predefined policy ─> T5 RoleService + RoleAssignmentService ─> T6 RolePolicyPublisher ─> T7 company console APIs ─> T10 controlled identity-sync run
T8 user directory ─> T9 profile + branding
T13 PermissionEvaluator + @RequiresPermission (module by module) ─> T12 gateway module-role check ─> T15 entitlement filter on everywhere ─> T14 legacy checks removed + Tickets IDOR ─> T16 audit endpoint + CP company annotations
```

T13 needs T4 (permission codes registered) and T6 (`TENANT_ROLE_PERMS`).

---

## 5. Task details

### T1 — Entities (P10-T1)

| Item | Detail |
|---|---|
| Entities | `CompanyUser` (`company_id`, `identity_id` UQ (CD-02), `status` INVITED/ACTIVE/INACTIVE/REMOVED, `is_company_super_admin` mirror, `invited_at`, `activated_at`, `deactivated_at`, `invited_by_identity_id`, [audit], [ver]); `Role` / `Permission` / `RolePermission` extended to the COMPANY_ADMIN and BUSINESS domains (`@Check` rules, `scope_key` = company id or 0). Not created: `tbl_company_user_role`, `tbl_company_user_module_access`, `tbl_platform_admin_role` (CD-11). |
| Migration of the kept legacy entity | `CustomerUser` (kept and re-pointed to `tbl_company` in P02-T3) is replaced by `CompanyUser`; development rows are re-created (fresh development DB); then `CustomerUser` is removed (owner approval). `PlatformRole` keeps only the PLATFORM tier (D4-4 rename applied in PHASE_04). |
| Tests | Schema IT on an empty DB: unique identity, checks, `(company_id, status)` index. |

### T2 — `CompanyUserService`, seats, outbox tasks (P10-T2)

| Item | Detail |
|---|---|
| Files | `platform/company/user/{CompanyUserService, SeatGuard}`; `IdentitySyncProcessor` tasks `INVITE_COMPANY_USER` (user without password in the caller's realm, requested realm roles, execute-actions email), `ASSIGN_COMPANY_ROLE`, `REVOKE_COMPANY_ROLE`, `CREATE_COMPANY_ROLE`, `DISABLE_ACCOUNT` (+ session revocation), `ENABLE_ACCOUNT`; target realm = registry realm of the company (D3-2). |
| Rules | `invite`, `activate`, `reactivate` take the seat lock (`AllowanceService.assertCanAddUser`, PHASE_06) — seats = INVITED + ACTIVE (Q-06); `deactivate` / `remove` free the seat; `remove` disables, never deletes; `tbl_company_usage.billable_users` maintained in the same transaction; daily reconciliation against the realm users (read-only). Same email in two companies = two accounts in two realms (CD-02). |
| Tests | Allowance 5 → 6th invite 409 `USER_LIMIT_REACHED`; deactivate frees one (acceptance 1); 10 concurrent invites at the limit → never above; outbox task carries the right realm. |

### T3 — Company Super Admin assignment + guards (P10-T3)

| Item | Detail |
|---|---|
| Files | `POST /api/v1/platform/admin/companies/{code}/company-super-admin` (`platform.company-admin.assign`; create or link the user in `co-<n>`, assign `COMPANY_SUPER_ADMIN`; two-step handover); `platform/company/guard/{EscalationGuard, LastSuperAdminGuard}` (Company Admin never assigns `COMPANY_SUPER_ADMIN` / `COMPANY_ADMIN`, a platform role or a non-entitled module role; Company Super Admin never assigns `COMPANY_SUPER_ADMIN` (Q-11); assigner holds all permissions of the role; no self-elevation; last Super Admin protected; company admins cannot manage holders of `company.admin.manage`); MFA required for company-admin roles is a realm policy (template, Q-27). |
| Tests | Guard decision tables (every rule, both outcomes); company-admin attempt to create a platform user / role → 403 (acceptance 7). |

### T4 — Permission registration + predefined role policy (P10-T4)

| Item | Detail |
|---|---|
| Files | COMMON `security/permission/PermissionRegistrar` (reads `META-INF/nyaalkaran/permissions.yml`, posts on start-up, retries with backoff, never blocks start-up); CP `POST /api/v1/internal/platform/permissions/register` (idempotent; unknown → added; missing → DEPRECATED, never deleted); `rbac/system-roles.yml` (company-admin roles + 5 levels × module → permission sets with data scope; versioned) applied by the policy seeder (insert-if-missing, `policy_version`). Each service gets its `permissions.yml` (codes `<module>.<resource>.<action>`), derived from its controllers. |
| Tests | Registration idempotent; deprecation; seeder idempotent; every code in `system-roles.yml` exists in a registry file. |

### T5 — `RoleService` and `RoleAssignmentService` (P10-T5)

| Item | Detail |
|---|---|
| Files | `platform/rbac/{RoleService, RoleAssignmentService}`: custom role `CUSTOM_<NAME>` (immutable name) created in the realm (outbox `CREATE_COMPANY_ROLE`) + CUSTOM `tbl_role` / `tbl_role_permission` rows; permissions limited to the role's domain/module; delete only when unassigned (`ROLE_IN_USE` 409, else disable); predefined roles 409 `SYSTEM_ROLE_IMMUTABLE`; assignment only for entitled modules, assigner holds the role's permissions; **no assignment row in the CP** — audit entry + outbox task only. |
| Tests | Acceptance 3 (predefined immutable; custom role create/assign/delete); acceptance 5 (assignment visible in the realm and the next token, no CP table). |

### T6 — `RolePolicyPublisher` (P10-T6)

| Item | Detail |
|---|---|
| Files | `platform/rbac/RolePolicyPublisher`: `TENANT_ROLE_PERMS:<CMP_n>:<role>` = permission codes with data scope, filtered to entitled modules; after commit of policy, custom-role or entitlement changes; TTL 300 s; resync; internal rebuild API. No per-user cache (CD-11). |
| Tests | Entitlement removal removes that module's permissions from every role key. |

### T7 — Company console APIs (P10-T7)

| Item | Detail |
|---|---|
| Files | `platform/controller/company/*` under `/control-panel/api/v1/company/**` (users, invitations, roles, permissions, role assignment, profile, `me`, audit) with `@RequiresCompanyPermission` (T16); company from the verified token only. |
| Tests | Web-layer matrix: a Company Admin with only `company.user.read` gets 403 on create/update/assign (acceptance 2). |

### T8 — Company-scoped user directory (P10-T8)

| Item | Detail |
|---|---|
| Files | CP internal `GET /api/v1/internal/platform/companies/{companyCode}/users?ids=&department=&role=` (members of that company only; display data from that company's realm, cached per company); COMMON `directory/CompanyUserDirectory` client. Callers migrate in PHASE_11. |
| Tests | Only that company's users returned (acceptance 6, first part). |

### T9 — Company profile and branding (P10-T9)

| Item | Detail |
|---|---|
| Files | Profile API (`company.profile.read|update`); logo upload to `cmp-<n>-control-panel-bucket` under `platform/branding/` (registry bucket, PHASE_09); COMMON `branding/CompanyBrandingResolver` (cached internal API: name, logo key, mail sender name, reply-to). Services adopt it in PHASE_11. |
| Tests | Branding of company A never returned for B. |

### T10 — Controlled identity-sync run (P10-T10)

| Item | Detail |
|---|---|
| Work | Per D10-1: Testcontainers Keycloak with two company realms from the template; run invite, assign, revoke, create custom role, disable, enable; verify in Keycloak; export both realms as evidence (`plans/PHASE_10_IDENTITY_SYNC_RUN.md`). Identity sync is enabled by default only after this run passes. |

### T11 — Stale `IdentitySyncMySqlIT` assertions (P10-T11)

| Item | Detail |
|---|---|
| Work | If P02-T0 annotated them, rewrite them now for the realm-aware tasks (P03-T11 + T2 of this phase) and remove the `@Disabled`. |

### T12 — Gateway module-role check (P10-T12)

| Item | Detail |
|---|---|
| Files | `CompanyContextFilter` (was `OrganizationContextFilter`): after issuer, company, status and entitlement checks, the verified company token must hold a role `<MODULE>_*` of the target module or `COMPANY_SUPER_ADMIN` (entitled modules only) — from `realm_access.roles` only (CD-14); else 403 `MODULE_ACCESS_DENIED`. |
| Tests | Role ≠ entitlement matrix (acceptance 4). |

### T13 — `PermissionEvaluator` + `@RequiresPermission`, module by module (P10-T13)

| Item | Detail |
|---|---|
| Files | COMMON `security/permission/{PermissionEvaluator, RequiresPermission, PermissionAspect}`, `ownership/OwnershipContext` (OWN / TEAM / COMPANY data scope); roles only from the verified token (`TenantVerificationFilter`), resolved through `TENANT_ROLE_PERMS` (local cache ≤ 30 s); Company Super Admin passes inside entitled modules. Rollout order (small → large): ASSET, TICKETS, CHAT, ATTENDANCE, LOGISTICS, STORE, MINING, QC, R&D, PRODUCTION, HRMS, PAYROLL, CRM, COMPLIANCE, NOTIFICATION, FILE, CUSTOMER-PORTAL, ACCOUNT-BOOK; flag `erp.security.permissions.enforce` (default true outside `dev`). Per module: `permissions.yml` + annotations + a reflection test (every non-internal endpoint annotated or explicitly public/internal). |
| Tests | Worked example 3a (John/Mary); acceptance 8 through the gateway **and** directly against the service with a valid token. |

### T14 — Legacy checks, unverified decoders, Tickets IDOR (P10-T14)

| Item | Detail |
|---|---|
| Work | Replace: TICKETS `TicketRaiseController` `IT_MANAGER` + `userId` IDOR (data scope), CRM support chat, COMPLIANCE controllers, R&D `RoleAuthorizationService`, LOGISTICS `RoleClient`, HRMS/PAYROLL `TenantContext.requireAdmin`; remove `JwtAccessTokenUtil` role methods, `CrmDataAccessAuthority` decoding, CP `JwtRoleExtractor` and `/api/user-roles/extract` — each only after the module's T13 annotations pass its regression. |
| Tests | Grep: no `JWT.decode(` and no unverified Base64 payload decoding in `src/main` (acceptance 10); IDOR test (user A cannot read user B's tickets by id). |

### T15 — Entitlement filter on everywhere; soft Feign handling (P10-T15)

| Item | Detail |
|---|---|
| Work | `MODULE_ENTITLEMENT_ENFORCE=true` (owner-managed `.env`) with the correct module token per service; COMMON Feign `ErrorDecoder` hook: 403 `MODULE_NOT_ENTITLED` from a non-mandatory dependency → logged, skipped, never fails the user transaction (full decoder in P11-T4). |
| Tests | Company entitled to {asset}: ASSET works, every other module 403 at gateway and service (acceptance 9). |

### T16 — Audit endpoint permission; CP company-permission annotations (P10-T16)

| Item | Detail |
|---|---|
| Work | COMMON `AuditLogController` → `company.audit.read`; CP `@RequiresCompanyPermission("company.…")` with the company from the verified token on every company API. |
| Tests | Reflection test for the CP company controllers. |

---

## 6. Test plan summary

| Level | Coverage | Acceptance (PHASE_10 §13) |
|---|---|---|
| Unit | guard tables, effective-access algorithm (role without entitlement, entitlement without role), seat counting | 2, 3a, 4 |
| MySQL + Redis IT | invite → activate → deactivate with seats; concurrent invites; policy publication | 1, 6 |
| Keycloak IT (Testcontainers, 2 realms) | assignment lands only in the caller's realm; cross-realm attempts fail | 5, 7 |
| Web layer | company API permission matrix | 2, 7 |
| Reflection + grep | endpoint coverage per module; no unverified decoding | 8, 10 |
| Gateway + service | role ≠ entitlement; entitlement filter | 4, 9 |
| Regression | S0-5 baseline; adapted membership / access ITs | — |

---

## 7. Risks

| Risk | Mitigation |
|---|---|
| Very large annotation rollout breaks modules | Module-by-module with flags; reflection tests; legacy checks removed only after replacement passes |
| Role drift in realms | Template as code; reconciler (PHASE_03); ERP-only administration (CD-13) |
| Role change latency (token lifetime) | Access token ≤ 5 min; session revocation for urgent removals |
| Last admin lock-out | Guard + platform reassignment API |
| Permission catalog drift | Code-declared `permissions.yml`; deprecation instead of deletion |

---

## 8. Documentation findings

| # | Finding | Handling |
|---|---|---|
| F-1 | §11.9 names `192.168.5.15:8081` as the local test Keycloak; PHASE_03 removes that literal from code defaults | D10-1 (report; Testcontainers recommended) |
| F-2 | P10-T11 duplicates P02-T0 | T11 completes what P02-T0 annotated |

---

## 9. Definition of done (PHASE_10)

All PHASE_10 §13 acceptance criteria proven; every task row REVIEW with evidence; owner sets DONE.
