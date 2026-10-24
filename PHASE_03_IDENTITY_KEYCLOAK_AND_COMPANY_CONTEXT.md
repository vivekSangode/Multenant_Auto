# PHASE 03 — Identity, Keycloak and Company Context

| Item | Value |
|---|---|
| Master architecture | [SAAS_ARCHITECTURE_MASTER.md](SAAS_ARCHITECTURE_MASTER.md) §14, §15, §18 |
| Design source | [PHASE_01 §0 (decision register CD-02, CD-04, CD-06), §6.5 (realm per company), §6.6 (company routing and tenant context)](PHASE_01_SAAS_FOUNDATION.md) |
| Depends on | [PHASE_01](PHASE_01_SAAS_FOUNDATION.md) approved; [PHASE_02](PHASE_02_CP_SCHEMA_AND_TENANCY_IMPLEMENTATION.md) (company model, identity tables, realm registry table) |
| Enables | PHASE_04, PHASE_08 (`CREATE_REALM`), PHASE_10, PHASE_11, PHASE_12, PHASE_14 (public-route hardening) |
| Status | **DONE** (2026-10-07, owner closure governance after PHASE_02 DONE: all 13 tasks DONE; D3-10 stays OPEN as an accepted R1 limitation / PHASE_08 gate (gating clarified 2026-10-16, CD-82 (7): P08-T5 DONE, non-test `CONFIGURE_CLIENTS`, P10-T3 / P10-T10 — not the PHASE_08 unlock); automated realm export = R1 limitation; acceptance 2 OTP = G6 operator check, acceptance 3 superseded by the CD-30 local Keycloak 25.0.6 ITs, acceptance 7 suspension-login → PHASE_06 / P06-T6). Earlier: PARTIALLY_COMPLETED (X-2 docs-only cleanup, owner decision C-7, 2026-10-07). |

> **Revision 2026-10-03 (v5, decision register):**
> - The earlier single-realm design is **withdrawn**: one `ERPSystem` realm, user-profile attributes `tenant_id`/`account_scope`, the `SET_TENANT_ATTRIBUTE` task, the `/ORG_` group fallback and the `Active-Org` transition.
> - **CONFIRMED DECISION CD-06:** a dedicated Keycloak realm per company plus a platform realm.
> - **CONFIRMED DECISION CD-02:** one login = one company. The company comes only from the token issuer (realm) and the realm's hard-coded `tenant_id` claim. There is no switching and no account linking.
> - **CONFIRMED DECISION CD-11 (closes Q-50):** company roles and their **assignment** live in the company realm. The realm template therefore creates the **predefined company roles** (company-admin roles + the module roles of the purchased modules). Tokens carry the user's company roles, and the services enforce them with the CP policy (PHASE_10).
> - **CONFIRMED DECISION CD-04:** the Platform Super Admin is created manually by the operator, in the platform realm only.
> - **Final decision review (2026-10-03):** Q-46, Q-47, Q-48, Q-49, Q-53 and Q-56 are **CLOSED** (PHASE_01 CD-14..CD-19):
>   - Company Login Code discovery (CD-15);
>   - a standard new realm for Shivam, with a separate identity migration (CD-16);
>   - platform realm `erp-platform` and dedicated service accounts (CD-17);
>   - 50 companies planning target (CD-18);
>   - central platform SMTP for Keycloak emails (CD-19);
>   - company business roles = **realm roles** (CD-14).
> - No open question blocks this phase.

---

## 1. Objective

Implement the identity and company-context foundation approved in PHASE_01:

1. **Platform realm `erp-platform`** (owner-executed, by runbook; CD-17):
   - platform roles `PLATFORM_SUPER_ADMIN` and `PLATFORM_ADMIN`;
   - OTP required for every platform account;
   - the platform admin console client;
   - the per-service M2M clients (`svc-<service>`);
   - the service accounts of PHASE_01 §6.5.10: `erp-realm-provisioner` (master realm, `create-realm`), `erp-platform-identity-admin` (`erp-platform` user management).
2. **Company realm template as code** (versioned JSON plus scripts in the repository). It is used by the PHASE_08 `CREATE_REALM` step. It contains:
   - the security policies, token lifetimes, brute-force protection and password policy;
   - the public ERP client (PKCE) and the optional portal client (Q-14);
   - the audience mapper (`nyaalkaran-erp`) and the **hard-coded `tenant_id` claim mapper**;
   - the **predefined company roles** (CD-11): company-admin roles (`COMPANY_SUPER_ADMIN`, `COMPANY_ADMIN`, …) and the module roles of the purchased modules (e.g. `CRM_MANAGER`, `CRM_USER`), all as **realm roles** (CD-14, naming in PHASE_01 §6.5.9);
   - the built-in `roles` client scope (realm roles → `realm_access.roles`); the clients `erp-web` (and optional `erp-portal`); no duplicate client roles;
   - the client `erp-identity-admin` (service account, `manage-users` / `view-users` / `query-users` / `view-realm` of that realm only, signed-JWT authentication);
   - the central platform transactional SMTP with the vault-referenced password (CD-19).
3. **Realm registry logic** on `tbl_identity_realm` (table from PHASE_02):
   - the registry service;
   - the internal issuer allow-list API;
   - cache and invalidation on realm changes.
4. **Secure bootstrap of the first Platform Super Admin** (CD-04, PHASE_01 §6.5.5) and `GET /api/v1/platform/me`.
5. **Issuer-based company resolution at the gateway:**
   - the token issuer must be a registered ACTIVE realm;
   - issuer → company comes from the registry, and must equal the `tenant_id` claim;
   - platform-realm tokens are allowed only on platform routes;
   - no `Active-Org`, no group selection.
6. **Service-side verification** of the forwarded identity in every domain service (`TenantVerificationFilter`), using the same registry-driven issuer allow-list. Issuer and audience validation in the control plane.
7. **Hardened internal authentication:**
   - per-service M2M clients in the platform realm;
   - no hard-coded defaults;
   - the internal token sent only to internal paths, with dual secrets for rotation;
   - `azp` allow-lists.
8. **Network surface:** no host-published service ports.
9. **Tenant-safe background work:**
   - fix the tenant-less job;
   - add a module-aware `@RunPerTenant`;
   - add an architecture test against tenant-less jobs.
10. **Tenant invalidation:** company status, resource and realm changes evict the metadata, realm-registry and pool caches in every service.
11. **Realm-aware identity sync:** every identity-sync task carries its target realm.

## 2. Existing implementation relevant to this phase

> **P02-T11 note (stage G, 2026-10-05):** the tables of this section describe the code as inspected on 2026-10-03. P02-T11 stages A–F (2026-10-04/05, REVIEW) have since replaced the organization tenancy: the CP organization / customer tier (`Customer`, `Organization`, `OrganizationDatabase`, `OrganizationUser`, `OrganizationUserBusinessRole`, `OrganizationQuotaService`, `CompanyOrganizationService`, `OrganizationGroupProvisioner`, `@OrganizationScoped`, `PlatformOrgAdminController`, …) is deleted (stage C); `InternalOrganizationController` → `InternalCompanyController` (`/api/v1/internal/platform/companies/...`), `OrganizationEntitlementSyncListener` → `CompanyEntitlementSyncListener`, `CustomerUser` → `CompanyUser` (`tbl_company_user`), `CustomerUsage` → `CompanyUsage`; runtime `TenantContext.companyCode`, header `X-Company-Code`, Redis `COMPANY_MODULES:<CMP_n>`, resource names `cmp_<n>_…` / `cmp-<n>-…` (stages A / B); gateway `CompanyContextFilter` / `CompanySelection`, company from the verified `tenant_id` claim, no `Active-Org` (stage D). Read old names in this section as these replacements.

| Component | Location | Notes (`CODE-INSPECTED`) |
|---|---|---|
| Gateway JWT validation | `API-GATEWAY/.../configs/SecurityConfig.java`, `RealmIssuers.java`, `application.yml` | Issuer `…/realms/${KEYCLOAK_REALM}`, audience `nyaalkaran-erp`. `RealmIssuers` already accepts **two static issuers** (the main realm and a customer realm). This is the base for the registry-driven multi-issuer list. |
| Gateway company selection | `filter/OrganizationSelection.java`, `filter/OrganizationContextFilter.java` | Company from the `groups` claim (`ORG_` pattern) and `Active-Org`; header stripping; `ORG_MODULES` check; the CONTROL-PANEL platform path is exempt. **To be replaced** by issuer-based resolution (CD-02, CD-06). *(Since P02-T11 stage D, 2026-10-05: `CompanySelection` / `CompanyContextFilter`, company from the verified `tenant_id` claim, `X-Company-Code`, `COMPANY_MODULES`; issuer resolution in P03-T5.)* |
| Service tenant filter | COMMON `tenant/TenantContextFilter.java` | Trusts `X-Organization-Id` (since P02-T11 stage B: `X-Company-Code`; verified by `TenantVerificationFilter` since P03-T6); the exclusion uses `contains("/actuator")` |
| Control-plane JWT | `platform/security/PlatformSecurityConfig.java`, `application.yml` | JWK set only; no issuer or audience check; `anyRequest().permitAll()` |
| Platform roles | Keycloak realm roles; `PlatformJwtGrantedAuthoritiesConverter` | Client roles named `PLATFORM_*` are ignored (correct). Must also be limited to the platform issuer. |
| Feign / M2M | COMMON `client/FeignSecurityInterceptor.java` | Defaults `http://192.168.5.15:8081`, `admin-cli`, a placeholder secret; synchronized token fetch; `X-Internal-Token` on every call |
| Internal token | `InternalServiceTokenAuthenticator` (CP), `TenantSchemaInitController` (COMMON), `InternalServiceTokenFilter` (ACCOUNT-BOOK, CRM, NOTIFICATION) | One shared secret `INTERNAL_TENANT_SECRET`, constant-time comparison, fail-closed |
| Jobs | `PerTenantJobAspect`; ACCOUNT-BOOK `accounting/service/EwayBillExpiryScheduler.java` | The ACCOUNT-BOOK job is tenant-less and fails closed, so it never runs |
| Invalidation | COMMON `tenant/metadata/TenantInvalidationListener.java`; `DynamicRoutingDataSource.evictPool` | No publisher; `evictPool` is never called |
| Compose | `docker-compose.yml` | Host ports published for tickets (8097), attendance (8100), account-book, portal, notification, chat |
| ACCOUNT-BOOK security | `security/SecurityConfig.java` | `permitAll`; its role converter is not wired |
| Identity sync | `platform/identity/*` | Outbox, worker, reconciler; off by default; single realm only; never run against a real Keycloak (`NOT-VERIFIED`) |
| Keycloak admin client | `KeycloakIdentityProviderAdmin`, `KeycloakAdminTokenProvider` | Bound to one configured realm. Must become realm-parameterized, plus realm creation. |
| Keycloak clients in use | `account-book-ui` (public, `erp-ui` environments); `api-gateway` (legacy client roles); a confidential client via `KEYCLOAK_CLIENT_ID` | The production ERP UI client is outside this repository (Q-25). Today all users are in the `ERPSystem` realm (**CURRENT/LEGACY**). Shivam moves to a new standard realm through the separate identity migration (CD-16). |

## 3. Existing completed work

| Item | Status |
|---|---|
| Gateway JWT validation (signature, issuer, audience, expiry), header stripping, internal-path deny | COMPLETED |
| Multi-issuer support in the gateway (2 static issuers) | COMPLETED (to be extended to the registry-driven list) |
| Realm-role-only platform authorities | COMPLETED |
| Constant-time, fail-closed internal token checks | COMPLETED |
| `@RunPerTenant` with per-company locks; STOMP and async propagation | COMPLETED |
| Identity-sync outbox mechanics (backoff, dead letter, reconciler) | PARTIALLY_COMPLETED (needs realm awareness, new tasks, and a real-Keycloak verification) |

## 4. Reusable components

`SecurityConfig`, `RealmIssuers` (extended to the registry), `CompanyContextFilter` (modified; was `OrganizationContextFilter`), `CompanySelection` (issuer resolution; was `OrganizationSelection`), `TenantContextFilter` (unchanged; the new filter sits beside it), `PlatformJwtGrantedAuthoritiesConverter` (limited to the platform issuer), `InternalServiceTokenAuthenticator`, `PerTenantJobAspect` (extended), `TenantInvalidationListener` (extended), the identity-sync outbox, worker and processor (realm-aware, new tasks), `KeycloakIdentityProviderAdmin` and `KeycloakAdminTokenProvider` (realm-parameterized).

## 5. Work requiring modification or redesign

| Item | Change |
|---|---|
| `RealmIssuers` | From 2 static issuers to the **registry-driven allow-list**: loaded from the CP internal API `GET /api/v1/internal/platform/identity-realms`, cached, refreshed on invalidation; JWKS cached per issuer with key rotation. Unknown or non-ACTIVE issuer → 401. |
| `CompanySelection` (was `OrganizationSelection`, P02-T11 stage D) | **Replaced.** Company = registry(issuer). The `tenant_id` claim must equal it (rule of PHASE_01 §6.6.2). Groups and `Active-Org` are ignored and stripped. `Active-Org` is removed (CD-02). |
| `CompanyContextFilter` (was `OrganizationContextFilter`, P02-T11 stage D) | Platform-realm tokens only on `/control-panel/api/v1/platform/**`; company-realm tokens only on business routes and `/control-panel/api/v1/company/**`. Remove the gateway `logFilter` stdout printing. |
| `PlatformSecurityConfig` | Issuer **and** audience validation; platform routes accept only the platform issuer; company routes accept only registered company issuers. |
| `PlatformJwtGrantedAuthoritiesConverter` | Platform roles are evaluated **only** for tokens from the platform issuer. |
| `KeycloakIdentityProviderAdmin` | Every operation takes the realm. Realm creation, enable and disable from the template (used by PHASE_08). Role creation and user-role mapping in a company realm (used by PHASE_10). |
| Identity-sync processor | Realm-aware tasks: `CREATE_REALM_ADMIN_USER`, `INVITE_COMPANY_USER`, `SYNC_IDENTITY`, `ASSIGN_PLATFORM_ROLE` (platform realm only), `ASSIGN_COMPANY_ROLE` / `REVOKE_COMPANY_ROLE` / `CREATE_COMPANY_ROLE` (the company realm only, PHASE_10), `DISABLE_REALM`, `ENABLE_REALM`. `SET_TENANT_ATTRIBUTE` and `ENSURE_COMPANY_GROUP` are **dropped**. |
| `FeignSecurityInterceptor` | **Redesign**: required configuration (no defaults); a per-service client in the platform realm; token cache with a failure backoff (no synchronized storm); `X-Internal-Token` only for `/api/v1/internal/**` |
| `TenantContextFilter` exclusions | Normalized path-prefix matching instead of `contains` |
| ACCOUNT-BOOK security | Replace `permitAll` with the common verification; align its internal token filter |
| Compose ports | Remove the domain services' host ports; fix `depends_on` (logistics→control-panel, attendance→hrms, payroll→hrms) |
| `EwayBillExpiryScheduler` | Use the per-tenant runner |

## 6. New development required

### 6.1 Keycloak configuration: platform realm and company realm template

- **Platform realm `erp-platform`** (owner-executed, runbook produced in this phase; CD-17):
  - realm roles `PLATFORM_SUPER_ADMIN` and `PLATFORM_ADMIN`;
  - the conditional OTP flow (mandatory for all platform accounts);
  - the platform console client;
  - `svc-<service>` M2M clients;
  - the service accounts `erp-realm-provisioner` (in `master`) and `erp-platform-identity-admin` (PHASE_01 §6.5.10);
  - access token ≤ 5 minutes.
- **Company realm template** (`[CURRENT]`, versioned in the repository, applied only by automation — **no company realm is configured by hand**):

| Template element | Content |
|---|---|
| Realm settings | `co-<n>` name, display name, token lifetimes (access ≤ 5 min), brute-force protection, password policy, required actions (verify email, update password) |
| Clients | `erp-web` (public, PKCE, redirect to the ERP login page; **Full Scope Allowed = on**); optional `erp-portal` (Q-14; Full Scope Allowed = off + role scope mappings); `erp-identity-admin` (service account, user management of this realm only, signed JWT) |
| Client scope `erp-company` | Audience mapper `nyaalkaran-erp`; **hard-coded claim mapper `tenant_id` = the company `company_code` (`CMP_<n>`)** (set at creation, never user-editable) |
| Roles | **Realm roles** (CD-14), defined once per realm: `COMPANY_SUPER_ADMIN`, `COMPANY_ADMIN`; module roles `<MODULE>_<LEVEL>` (`USER`, `TEAM_LEAD`, `MANAGER`, `ADMIN`, `SUPER_ADMIN`) for the purchased modules (`CREATE_STANDARD_ROLES`, also on `ADD_MODULE`); **no duplicate client roles**; **never** any `PLATFORM_*` role. Custom roles `CUSTOM_<NAME>` are created later through the ERP UI (PHASE_10, CD-13). Naming and lifecycle in PHASE_01 §6.5.9. |
| Role mapper | The built-in default `roles` client scope (realm roles mapper → `realm_access.roles`); no custom role mappers needed |
| Email | Central platform transactional SMTP (platform-owned sender and domain; password through the Keycloak vault) for verification, password reset and invitations (CD-19) |
| Version | `template_version` recorded in `tbl_identity_realm` |

- A **read-only configuration check** (startup and scheduled) verifies the platform realm and a sample of company realms against the template. It covers mapper presence, the predefined company roles, the absence of platform roles in company realms, and the template version. It logs warnings and fails health in non-dev profiles. Full drift reconciliation is the realm reconciler (§6.7).

### 6.2 Platform Super Admin bootstrap and `/platform/me` (CONFIRMED DECISION CD-04, refined by CD-28 item 9)

- **No seeded permanent credentials** (never `admin/admin`). The operator supplies the initial username, email and one-time initial password **only through environment / secret configuration** (CD-28 item 9).
- `PlatformBootstrapRunner` implements PHASE_01 §6.5.5:
  - it runs once, from an environment property;
  - it refuses when an ACTIVE `SUPER_ADMIN` exists, unless the one-time allow-additional flag is set (operator only);
  - it creates the user **in the platform realm** `erp-platform` with the realm role `PLATFORM_SUPER_ADMIN`, the initial password as **temporary** (forced change at first login, `UPDATE_PASSWORD`), plus `VERIFY_EMAIL` and `CONFIGURE_TOTP`; an already existing user with that username is verified instead of recreated; no company code is involved (platform-level);
  - it inserts `tbl_identity` (platform realm) + `tbl_platform_admin(SUPER_ADMIN, BOOTSTRAP)` — the `PlatformAdmin` entity is owned by PHASE_04 and must exist first (CD-28 item 4);
  - it writes the audit entry `PLATFORM_SUPER_ADMIN_BOOTSTRAPPED`; the initial password is never logged, stored or returned.
- **Security rule:** no API exists through which a company administrator can create, invite or elevate a Platform Super Admin.
  - Company APIs operate only on company realms.
  - `ASSIGN_PLATFORM_ROLE` targets only the platform realm and requires the platform permission.
- `GET /api/v1/platform/me` returns the identity, admin type and status. Permissions and scope are added in PHASE_04.

### 6.3 Realm registry and issuer-based company resolution

- **Registry service** on `tbl_identity_realm`:
  - lookup by issuer, by company and by realm name;
  - `GET /api/v1/internal/platform/identity-realms` returns ACTIVE issuers with `realm_type`, `company_code` and `jwks_uri` (M2M + `azp` allow-list);
  - realm changes publish `tenant-invalidation`.
- **Gateway resolution** (PHASE_01 §6.6.2):
  1. Validate the signature (JWKS of that issuer), `exp` and `aud`.
  2. Look the issuer up in the registry. Unknown or not ACTIVE → 401.
  3. Platform issuer → platform routes only. Company issuer → company = registry mapping; the `tenant_id` claim must be equal, otherwise 403 `TENANT_MISMATCH` + a security audit event.
  4. Set `X-Company-Code` from the registry (never from the request).
  5. The company status check is added in PHASE_06; the entitlement check exists; the user-access check is added in PHASE_10.
- **Company Login Code discovery** (CD-15, PHASE_01 §6.5.11):
  - `POST /public/auth/discover {loginCode}` (gateway public route, rate-limited) → CP registry lookup (`tbl_company.login_code`) → company ACTIVE and realm ACTIVE → returns only the authorization endpoint of `co-<n>`, the client id `erp-web` and the registered issuer (owner decision 2026-10-06, P03-T12: no token endpoint; authorization endpoint = issuer + `/protocol/openid-connect/auth`; dedicated limiter 5 / minute / client IP, Redis unavailable → 503; input trimmed and upper-cased);
  - an invalid code, a non-ACTIVE company and a disabled realm all return the same generic "Invalid Company Code";
  - subdomains and a broker realm are future;
  - the UI change depends on Q-25 (UI client).

### 6.4 Service-side verification (`TenantVerificationFilter`, COMMON-LIBRARY)

1. It runs right after `TenantContextFilter`.
2. On non-internal requests it requires `X-User-Token`, then:
   - verifies the signature against the cached JWKS of the token's issuer (registry allow-list);
   - checks the issuer, audience and expiry.
3. It requires:
   - registry(issuer).`company_code` == `tenant_id` == `X-Company-Code`;
   - `sub` == `X-Performed-By-Id`;
   - a company-realm issuer on business routes (portal routes per Q-14).
4. A failure returns 401/403 and a security audit event.
5. **Internal requests** require a verified M2M JWT from the **platform realm** with an allowed `azp`, plus `X-Internal-Token`.
6. Flag `erp.security.verify-user-token.enabled`, default **true** outside `dev`.

### 6.5 Internal authentication hardening

- One Keycloak confidential client per service (`svc-<service>`), **only in the platform realm**, so services need no credentials per company. The secrets are owner-managed in `.env`, with names only in the documents.
- Dual internal secrets: `INTERNAL_TENANT_SECRET` and `INTERNAL_TENANT_SECRET_NEXT`. Both are accepted, and the sender uses the current one.
- `azp` allow-lists on the control-plane internal APIs. The tenant bootstrap and the realm-registry write APIs are allowed for the control plane only.

### 6.6 Background work and invalidation

- `@RunPerTenant(module = "...")` runs only for companies whose entitlements include the module (the `/active?module=` API from PHASE_02).
- An ArchUnit rule forbids `@Scheduled` business methods without `@RunPerTenant` or the explicit runner, unless they are allow-listed platform jobs.
- Fix the ACCOUNT-BOOK `EwayBillExpiryScheduler`.
- The control plane publishes `tenant-invalidation` (Redis pub/sub) on company status, resource and realm changes. The COMMON listener and the gateway clear the metadata and realm-registry caches and call `evictPool` for the tenant.

### 6.7 Realm operations automation (IMPLEMENTATION IMPACT of CD-06)

| Item | R1 scope |
|---|---|
| Realm template + `CREATE_REALM` / `DISABLE_REALM` / `ENABLE_REALM` | **R1** (used by PHASE_08; PHASE_06 does not enable / disable realms — T3-7, D-1 / CD-40, 2026-10-12) |
| Realm reconciler (drift report; template upgrades in batches through `REALM_TEMPLATE_UPGRADE` jobs) | **R1**: drift report. Batched upgrades can follow once more than one template version exists. |
| Per-realm export job (configuration + users) and restore procedure | **R1**: runbook + scheduled export; restore drill in PHASE_12 |
| Per-realm monitoring (login failures, lockouts, token errors, admin events; realm count) | **R1**: basic metrics and alerts |
| Key and client-secret rotation per realm | Runbook in R1; automation later |
| Capacity test at 50 companies / 51 realms (CD-18) | Before production (PHASE_12) |

## 7. Exact services, files and database areas involved

| Area | Files |
|---|---|
| API-GATEWAY | `configs/RealmIssuers.java` (registry-driven), `filter/CompanySelection.java` (was `OrganizationSelection`), `filter/CompanyContextFilter.java` (was `OrganizationContextFilter`), `configs/SecurityConfig.java`, `application.yml` (CORS headers) |
| COMMON-LIBRARY | new `tenant/TenantVerificationFilter.java`, new realm-registry client and cache, `tenant/TenantContextFilter.java` (exclusions), `client/FeignSecurityInterceptor.java`, `tenant/job/*`, `tenant/metadata/TenantInvalidationListener.java`, `tenant/db/DynamicRoutingDataSource.java` (eviction use) |
| CONTROL-PANEL | `platform/security/PlatformSecurityConfig.java`, new `platform/bootstrap/PlatformBootstrapRunner.java`, new `/platform/me` controller, new realm-registry service and internal API, `platform/identity/*` (realm-aware tasks, realm-parameterized admin client), the invalidation publisher, the Keycloak configuration check, the realm reconciler |
| Repository (new) | `keycloak/realm-template/` (versioned company realm template), `keycloak/platform-realm/` runbook |
| ACCOUNT-BOOK | `security/SecurityConfig.java`, `accounting/service/EwayBillExpiryScheduler.java`, the internal token filter |
| Compose / env | `docker-compose.yml` (ports, `depends_on`); `.env` key names: `INTERNAL_TENANT_SECRET_NEXT`, per-service M2M client ids and secrets, the platform realm name |
| Keycloak | `erp-platform` (owner, by runbook); company realms `co-<n>` (automation only, Shivam included); `ERPSystem` is only the **source** of the separate Shivam identity migration (CD-16), never modified |

## 8. Proposed schema changes

None beyond PHASE_02: `tbl_identity` (with `identity_realm_id`), `tbl_identity_realm`, `tbl_platform_admin` and `tbl_identity_sync_task` (with a target realm column) from PHASE_01 §6.2 F, H and §6.5.2.

## 9. API and integration requirements

| API | Notes |
|---|---|
| `GET /api/v1/platform/me` | Platform issuer only |
| `GET /api/v1/internal/platform/identity-realms` | Issuer allow-list for the gateway and services; M2M + `azp` allow-list; cached |
| `POST /public/auth/discover` | Company Login Code → authorization endpoint (CD-15); rate-limited; generic error; no company enumeration |
| Internal APIs | Require the platform-realm M2M JWT + `X-Internal-Token` + the `azp` allow-list |
| Gateway errors | 401 invalid token or unknown issuer; 403 `SCOPE_NOT_ALLOWED`, `TENANT_MISMATCH`; 503 when the registry and Redis are unavailable and no cache is present |

## 10. Dependencies on other phases

| Phase | Relationship |
|---|---|
| PHASE_02 | Prerequisite: company model, identity tables, `tbl_identity_realm`, `/active?module=` |
| PHASE_04 | Uses the bootstrap, `/platform/me` and the platform token validation |
| PHASE_06 | Adds the gateway company-status check (P06-T6); does **not** use `DISABLE_REALM` / `ENABLE_REALM` — subscription expiry removes module entitlements only (T3-7, D-1 / CD-40, 2026-10-12) |
| PHASE_08 | Uses the realm template, `CREATE_REALM`, `CREATE_REALM_ADMIN_USER` and invalidation |
| PHASE_10 | Adds the user-access and permission checks on top of the verified context; invites users into the company realm |
| PHASE_12 | Runs the isolation suite against this phase's controls; realm restore drill; capacity test at 50 companies (CD-18); Shivam identity migration rehearsal (CD-16) |

## 11. Security and tenant-isolation requirements

1. A forged `X-Company-Code` sent directly to any service (inside the Docker network) is rejected.
2. A token from company A's realm with the header of company B is rejected at the gateway and in the service.
3. A token whose `tenant_id` differs from the registry mapping of its issuer is rejected and audited.
4. A token from an unregistered or disabled realm is rejected.
5. Platform-realm tokens are rejected on business and company routes. Company-realm tokens are rejected on platform routes, **even if they carry a role named `PLATFORM_*`**.
6. Company administrators cannot reach any API that creates or elevates platform identities (CD-04).
7. No hard-coded credentials, URLs or IPs remain in code defaults. The `192.168.5.15` defaults are removed. **`192.168.5.52/53` must never appear in any configuration.**
8. The Keycloak configuration is applied by the owner (platform realm) or by automation (company realms). The identity-sync and realm tasks are verified against the **local test Keycloak** before they are enabled, never against production.

## 12. Ordered implementation tasks

| # | Task | Status |
|---|---|---|
| 1 | `erp-platform` runbook + service accounts + company realm template as code + read-only configuration check | DONE (2026-10-07) |
| 2 | `PlatformBootstrapRunner` + `/platform/me` | DONE (2026-10-07) |
| 3 | Realm registry service + internal issuer API + invalidation | DONE (2026-10-07) |
| 4 | Control-plane issuer and audience validation; platform-issuer-only authorities; route rules | DONE (2026-10-07) |
| 5 | Gateway: registry-driven `RealmIssuers`; issuer → company resolution + `tenant_id` equality; scope enforcement; remove `Active-Org` and `logFilter` | DONE (2026-10-07) |
| 6 | `TenantVerificationFilter` + exclusion fix + ACCOUNT-BOOK alignment | DONE (2026-10-07) |
| 7 | Feign interceptor redesign; per-service M2M clients in the platform realm; dual internal secrets; `azp` allow-lists | DONE (2026-10-07) |
| 8 | Remove the published ports; fix `depends_on` | DONE (2026-10-07) |
| 9 | Module-aware `@RunPerTenant`; ArchUnit rule; fix the ACCOUNT-BOOK job | DONE (2026-10-07; P03-T9 DONE 2026-10-06) |
| 10 | Invalidation publisher + cache and pool eviction | DONE (2026-10-07) |
| 11 | Realm-aware identity sync + realm-parameterized admin client + `CREATE_REALM` / `DISABLE_REALM` / `ENABLE_REALM`; a controlled run on the local test Keycloak | DONE (2026-10-07) |
| 12 | Company Login Code discovery endpoint (+ `tbl_company.login_code`) | DONE (2026-10-07) |
| 13 | Realm reconciler (drift report), per-realm export job, per-realm metrics | DONE (2026-10-07) |

## 13. Acceptance criteria

1. The bootstrap creates exactly one Super Admin in the platform realm and refuses a second run. Every attempt is audited.
2. A platform login requires OTP. A platform token gets 403 on `/crm/**`. A company-realm token gets 403 on `/control-panel/api/v1/platform/**`.
3. A direct call to each service with forged headers returns 401/403 (an IT on the local compose profile).
4. A token from realm A with `X-Company-Code` of B returns 403 at the gateway and in the service.
5. A token from an unregistered realm, or a registered realm with a mismatching `tenant_id`, returns 401/403.
6. Two test company realms created from the template on the local Keycloak pass the configuration check.
7. Suspending a company evicts its pools in every service within 5 s of the event (metric) and blocks its realm logins.
8. The ArchUnit rule passes, and the ACCOUNT-BOOK e-way bill job runs per company.
9. Compose publishes only the gateway (plus localhost-bound infrastructure UIs).
10. Grep: no `192.168.5.` literal in code defaults; no `admin-cli` / placeholder secrets; no `Active-Org` handling.

## 14. Testing and validation checklist

- [ ] Unit: verification filter (signature, issuer allow-list, `tenant_id` equality, mismatch, expiry), issuer resolution, the bootstrap rules.
- [ ] Integration: a gateway + 2 services + a local Keycloak with the platform realm and two company realms from the template (compose test profile).
- [ ] Identity-sync IT (in-memory provider) + one controlled run against the local test Keycloak (realm creation, admin user, invite, disable and enable).
- [ ] Per-realm export and restore of one test realm.
- [ ] Regression against the PHASE_01 baseline report.

## 15. Risks and rollback considerations

| Risk | Mitigation |
|---|---|
| Existing UIs log in to `ERPSystem` only | The UI adopts the Company Login Code flow (Q-25 for the production UI client); Shivam users move to their new realm through the separate identity migration (CD-16) |
| Realm-per-company operational load (provisioning, upgrades, backups, monitoring) | Template as code, automation only, reconciler, per-realm export, monitoring (§6.7); R-07 in PHASE_01 |
| Keycloak performance at a high realm count | Capacity test at 50 companies before production (CD-18); realm cache sized to the realm count; new validation before growing beyond 50 |
| Keycloak version lacks a required feature | The version check (owner); template adjusted per version |
| Verification in every service adds latency | JWKS and registry cache; measured in PHASE_12 |
| M2M client sprawl | Clients only in the platform realm; generated client list in the runbook |

**Rollback:** verification, scope enforcement, the registry-driven issuer list and the Feign changes are behind configuration flags per service. Port removal is a compose change that can be reverted per environment. The bootstrap and the registry write only additive rows. A realm is never deleted automatically.

## 16. Current status

| Item | Status |
|---|---|
| Gateway JWT validation and header stripping | COMPLETED |
| Multi-issuer base (`RealmIssuers`, 2 static issuers) | COMPLETED; registry-driven issuers DONE (P03-T5) |
| Issuer-based company resolution; scope separation | DONE (P03-T4, T5) |
| Platform Super Admin bootstrap | DONE (P03-T2, incl. the attempt audit) |
| Realm registry, realm template, realm automation | DONE (P03-T1, T3, T11, T13; company-realm production user operations gated by D3-10 / PHASE_08) |
| Company Login Code discovery | DONE (P03-T12; UI integration waits for Q-25) |
| Service-side verification | DONE (P03-T6) |
| Internal authentication hardening | DONE (P03-T7) |
| Network surface | DONE (P03-T8; MinIO 9005 and account-book frontend 4123 approved exceptions) |
| Tenant-safe jobs and invalidation | DONE (P03-T9, T10) |
| Keycloak platform realm `erp-platform` configuration | Operator item (RUNBOOK; real platform-login OTP check = G6 operator work, decision P03-4) |
| **Phase** | **DONE** (2026-10-07; D3-10 OPEN — accepted R1 limitation / PHASE_08 gate; gating clarified by CD-82 (7)) |

*Earlier assessment (2026-10-03, before implementation): registry-driven issuers, company resolution, bootstrap, realm automation, discovery, service-side verification, internal authentication, network surface and the platform realm PENDING; tenant-safe jobs PARTIALLY_COMPLETED; phase PARTIALLY_COMPLETED (X-2 docs-only cleanup, owner decision C-7, 2026-10-07).*
