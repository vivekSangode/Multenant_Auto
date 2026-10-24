# PHASE 03 — Implementation Plan (Identity, Keycloak and Company Context) — r2

| Item | Value |
|---|---|
| Phase document | [PHASE_03_IDENTITY_KEYCLOAK_AND_COMPANY_CONTEXT.md](../PHASE_03_IDENTITY_KEYCLOAK_AND_COMPANY_CONTEXT.md) (objective, acceptance §13). This plan **adds no architecture decision**; it turns PHASE_03 into an ordered, file-level work list. Where a choice is still open it is listed in §3 for the owner. |
| Design sources | PHASE_01 §0 (CD-02, CD-04, CD-06, CD-11, CD-13..CD-17, CD-19, CD-24 Q-14, CD-26, CD-27, CD-28, CD-29), §6.5 (realms, clients, roles, bootstrap, login code), §6.6 (routing and context); developer guide §3, §5.2, §6–§9, §19; P02-T11 inventory ([ORG_TO_COMPANY_MIGRATION_INVENTORY.md](ORG_TO_COMPANY_MIGRATION_INVENTORY.md)) |
| Status | **Current (2026-10-07): r2 approved 2026-10-05 and executed — PHASE_03 DONE** (all 13 tasks DONE; D3-10 OPEN as an accepted R1 limitation / PHASE_08 gate; Keycloak tests on the local Keycloak 25.0.6 under CD-30 instead of Testcontainers; X-2 docs-only cleanup, owner decision C-7, 2026-10-07). Original header: **r2 — DRAFT FOR OWNER REVIEW (2026-10-05).** PHASE_03 is **LOCKED** in the tracker until PHASE_02 is DONE; no code is written from this plan before the owner approves r2 **and** unlocks PHASE_03 (fully or for the stage F subset, decision D3-4). |
| Revision | r1 2026-10-03 (agent, Claude Opus 5.5). **r2 2026-10-05** (agent, Claude Opus 5.5, owner request): rewritten for the company-only runtime delivered by P02-T11 stages A–E (no organization tenancy remains); defines the **P02-T11 stage F** subset (P03-T1, T3, T10, T5, T6) in detail; new owner decisions D3-4..D3-10. Previous text backed up in the agent scratchpad (`backup-phase03-plan-r1/`). **r2 update 2026-10-05 (owner decision CD-30):** Keycloak 25.0.6 is the fixed project standard for local and live/production; D3-5 CLOSED; all Keycloak ITs use the local Keycloak 25.0.6; no 26.x upgrade planned or validated. |
| Evidence | Local code inspection 2026-10-05 (`CODE-INSPECTED`, read-only): API-GATEWAY `configs/{RealmIssuers,SecurityConfig,RateLimiterConfig}`, `filter/{CompanyContextFilter,CompanySelection}`, `websocket/*`; COMMON `tenant/TenantContextFilter`, `tenant/metadata/{ControlPlaneClient,TenantInvalidationListener,TenantInvalidationMessage,TenantMetadataAutoConfiguration}`, `tenant/naming/TenantResourceNaming`, `client/FeignSecurityInterceptor`; CONTROL-PANEL `platform/model/IdentityRealm`, `platform/enums/{RealmType,IdentityRealmStatus}`, `platform/repository/IdentityRealmRepository`, `platform/identity/{IdentityRealmResolver,KeycloakIdentityProviderAdmin,KeycloakAdminTokenProvider}`, `platform/security/PlatformSecurityConfig`; ACCOUNT-BOOK `security/SecurityConfig`; CP `pom.xml` (`testcontainers-keycloak` 3.0.0). |
| Protected hosts | No step targets `192.168.5.52` / `.53`. All Keycloak work runs against the **local Keycloak 25.0.6** (CD-30: fixed project standard for local and live/production; `http://127.0.0.1:8081`). No agent changes or calls a shared or production Keycloak; the `ERPSystem` realm is never touched (only the source of the separate Shivam identity migration, CD-16). |
| Company rule | `CMP_<n>` (`^CMP_[0-9]{1,8}$`) is the only company identifier; the runtime contract stays `TenantContext.companyCode`, header `X-Company-Code`, Redis `COMPANY_MODULES:<CMP_n>`. `ORG_*`, `org_` / `org-`, `X-Organization-Id`, `Active-Org`, `ORG_MODULES` and organization tenancy are retired and must not return. Business-domain "organization" (ACCOUNT-BOOK organization profile, `bank_accounts.organization_id`, HRMS previous organization) is untouched. |
| CP schema | CD-27: entity changes only (`ddl-auto: update`), no CP changelog; CP Liquibase only in PHASE_11A. |

---

## 1. Summary

PHASE_03 makes the **company come from the token issuer** everywhere (CD-02, CD-06):

1. Keycloak as code: the `erp-platform` runbook (owner-executed) and the **company realm template v1** (versioned JSON + applier) with a read-only configuration check.
2. The **realm registry** service and the issuer allow-list API; invalidation on realm, company and resource changes.
3. **Issuer-based company resolution** in the gateway — the company is the registry mapping of the token issuer, and the realm's hard-coded `tenant_id` claim must equal it — and **service-side verification** (`TenantVerificationFilter`) of the forwarded token in every service.
4. The Platform Super Admin bootstrap and `GET /api/v1/platform/me`; issuer + audience validation in the control plane.
5. Internal authentication hardening (per-service M2M clients in the platform realm, no defaults, dual internal secrets, `azp` allow-lists), no published service ports, tenant-safe jobs.
6. Realm-aware identity sync with a realm-parameterized admin client (used by PHASE_08), Company Login Code discovery, basic realm operations.

### 1.1 What P02-T11 already delivered (no longer PHASE_03 work)

| Item | Done in | Effect on this plan |
|---|---|---|
| Company-only tenant contract: `TenantContext.companyCode`, `X-Company-Code` (only `CMP_<n>` accepted, else 403), metadata `companyCode`, `COMPANY_MODULES:<code>` | P02-T11 stage B | Every PHASE_03 component uses these names; no `X-Organization-Id` is set or read anywhere |
| CP organization / customer tier, `AccountScope.ORGANIZATION`, `PlatformRole.ORG_*`, organization identity-sync task types, `OrganizationGroupProvisioner` (ORG_ groups) removed | stage C | r1 T11 items "drop `ENSURE_ORGANIZATION_GROUP` / `INVITE_ORGANIZATION_MEMBER` / remove `OrganizationGroupProvisioner`" are **already done** |
| Gateway `CompanyContextFilter` / `CompanySelection`: company only from the verified JWT `tenant_id` claim; `groups`, `Active-Org` not read; client `X-Company-Code`, `X-Organization-Id`, `Active-Org`, `X-Tenant-Id` stripped; `COMPANY_MODULES` check; WebSocket ticket bound to the token company; rate-limit key from the token company | stage D | r1 T5 items "delete `OrganizationSelection`, remove groups / `Active-Org`" are **already done**; T5 now **adds** the issuer registry and the issuer → company equality on top of `CompanySelection` (no file deletion or rename needed) |
| Cross-module `CompanyTenancyArchitectureTest` in every module (COMMON test-jar contract); COMMON test-jar built at `process-test-classes` (no reactor leak) | stage E | Every new class must keep these tests green; realm names only through `TenantResourceNaming.realmName` |
| `IdentityRealm` (`tbl_identity_realm`), `RealmType`, `IdentityRealmStatus`, `IdentityRealmRepository`, `Identity.identityRealm` FK, interim `IdentityRealmResolver` | P02-T5 (REVIEW) | T3 builds the registry service on this table; no schema change |
| `TenantResourceNaming.realmName(CMP_<n>)` → `co-<n>`, `REALM_NAME` pattern, `assertMatchesTenant(co-<n>, CMP_<n>)` | P02 / stage A | T1 / T3 use them; no other code builds a realm name |

Size estimate (whole phase): 13 tasks, about 80–110 changed or new source files, about 35 new test classes. **Stage F subset** (§5.1): about 30–40 main files and 15–20 test classes.

---

## 2. Preconditions

| # | Precondition | Tracker | Needed by |
|---|---|---|---|
| 1 | PHASE_03 unlocked: PHASE_02 DONE, **or** an explicit owner exception for the stage F subset (D3-4) | phase table, PHASE_02 rows | all tasks |
| 2 | Owner approval of this plan r2 (AGENT_HANDOFF §6 rule 6) | PHASE_03 phase notes | all tasks |
| 3 | Keycloak test environment — **CLOSED 2026-10-05 (CD-30):** Keycloak **25.0.6** is the fixed project standard (local and live/production); real-Keycloak ITs run against the local Keycloak 25.0.6 (no image download) | — | T1, T3 ITs, T5 / T6 ITs with a real Keycloak |
| 4 | Owner decision D3-1 (`PlatformAdmin` entity first, PHASE_04 E-B) — **decided (CD-28)**, entity still to be built by PHASE_04 | S0-13 DONE | T2 only (not stage F) |
| 5 | Owner executes the `erp-platform` runbook on the **development** Keycloak and puts the platform realm issuer / JWKS URI and M2M client ids / secrets into `.env` (names only in documents) | owner action | shared-dev end-to-end only; unit tests and ITs use Testcontainers and need nothing |
| 6 | Q-25 (production ERP UI owner) | S0-6 | only the UI side of T12 |
| 7 | No concurrent IDE / Maven build during regression runs (S0-5 §4.2) | — | every task's test run |

---

## 3. Owner decisions / clarifications

> **Decisions recorded 2026-10-03 (owner, CD-28):** D3-1 = option (a): `PlatformAdmin` exists before any bootstrap or platform-role operation; PHASE_04 owns the entity and its bootstrap integration (early slice E-B) (S0-13 DONE). Bootstrap redesigned (CD-28 item 9): no seeded permanent credentials; initial username / email / one-time password only from environment or secret configuration; the runner **creates** the user in `erp-platform` with a temporary password (forced change at first login), VERIFY_EMAIL and CONFIGURE_TOTP; audit `PLATFORM_SUPER_ADMIN_BOOTSTRAPPED`; no company code.

| ID | Question | Why | Options | Recommendation |
|---|---|---|---|---|
| D3-1 | `PlatformAdmin` entity order | T2 persists the bootstrap row | decided (a), CD-28 | — |
| D3-2 (report) | "Target realm column" on `tbl_identity_sync_task` (PHASE_03 §8, guide §5.2) vs PHASE_01 §6.2 H (`company_code` only) | Avoid a column PHASE_01 does not define | (a) derive the realm (COMPANY tasks → registry realm of `company_code`; PLATFORM tasks → `erp-platform`); (b) add `target_realm` | **(a)**, no schema change |
| D3-3 (report) | The gateway still accepts the separate **customer realm** issuer (Phase 5L portal, `erp.security.customer-realm.*`); Q-14 / CD-24 puts portal users into the company realm with `erp-portal` | Sequencing only | Keep it behind its existing property until P11-T5; the registry-driven list does not include it | As described |
| **D3-4** | PHASE_03 is LOCKED until PHASE_02 is DONE; PHASE_02 has 9 tasks in REVIEW, T1 / T10 DEFERRED, T11 IN_PROGRESS (stages A–E REVIEW) | Stage F of P02-T11 **is** PHASE_03 work | (a) owner reviews and sets the PHASE_02 tasks DONE, then PHASE_03 UNLOCKED; (b) explicit owner exception: PHASE_03 UNLOCKED **for P03-T1, T3, T10, T5, T6 only** (stage F), the rest stays locked until PHASE_02 is DONE | **(b)** if the PHASE_02 review is not finished; otherwise (a). Not decided by the agent. |
| **D3-5** | Keycloak test image — **CLOSED 2026-10-05 (owner, CD-30)** | ITs need a real Keycloak | **Decided:** Keycloak **25.0.6** for both the local and the live/production environment (fixed project standard); no 26.x upgrade is planned or validated; no Testcontainers image download | Real-Keycloak ITs run against the developer's local Keycloak 25.0.6 (`LOCAL_KC_URL`, default `http://127.0.0.1:8081`; gated `*LocalKeycloak*IT`, validated 2026-10-05: T1 / T3 / T10 / T5 / T6 PASS, acceptance 6 proven on a real Keycloak) |
| **D3-6** | Gateway → CP registry API credentials. The gateway is reactive and has no Feign / M2M client today; `GET /api/v1/internal/platform/identity-realms` is protected (`SCOPE_CONTROL_PANEL_METADATA_READ` today, platform-realm M2M + `azp` after T7) | The gateway must load the registry | (a) interim: a reactive client-credentials client configured by environment keys (same token endpoint / client mechanism the services use today through `FeignSecurityInterceptor`), replaced by `svc-api-gateway` of `erp-platform` in T7; (b) wait for T7 before T5 | **(a)**, configuration keys only, no defaults, fail closed when unset outside `dev` |
| **D3-7** | The current development staff realm (`KEYCLOAK_REALM`) is not a `co-<n>` company realm and cannot be registered as one (T3 enforces `realm_name == TenantResourceNaming.realmName(company_code)`) | After T5 a token of that realm is "unknown issuer" → 401 on every route | (a) T5 behind `erp.security.registry-issuers.enabled` (default **true**, explicitly **false** only in the `dev` profile until a development `co-<n>` realm exists); (b) the operator creates a development company realm from the template (T1 applier, owner-executed) and registers it | **(a) + (b)**: (a) keeps local development working, (b) is the target. The stage D interim (`tenant_id` mapper in the dev staff realm) is then no longer needed. |
| **D3-8** | Existing service ITs (37 IT files) call services directly with `X-Company-Code` and no token; after T6 a non-internal request without a verified `X-User-Token` is refused | Regression scope of T6 | (a) a COMMON test-jar token fixture (local test signer + JWKS + registry stub) and every service IT sends a valid token; (b) the existing routing ITs set `erp.security.verify-user-token.enabled=false`, new verification ITs (CRM + ACCOUNT-BOOK) prove the filter | **(a)** — tests then run the production rule; done module by module inside T6. (b) is smaller but leaves the rule untested in most modules. |
| **D3-9** | ACCOUNT-BOOK `security/SecurityConfig` is `anyRequest().permitAll()`; T6 must apply the common verification there | ACCOUNT-BOOK owner rule: only tenant plumbing may change, each change approved | (a) T6 changes only `SecurityConfig` (tenant / security plumbing) to use the common verification, no business class; (b) leave ACCOUNT-BOOK unverified until a later task | **(a)**, with the exact diff shown to the owner before it is applied |
| **D3-10** | `erp-identity-admin` in the company realm template uses signed-JWT client authentication (CP key pair via JWKS URL) — key management is the open S0-2 item 6 | T1 template content | (a) template defines `client-jwt` with a `${CP_JWKS_URL}` placeholder; ITs use a test-only client-secret fallback; production use waits for S0-2 item 6; (b) client secret in R1 | **(a)** |
| **D3-11** | Issuer of the CP internal APIs during T4 — **decided 2026-10-05 (owner)** | Every service calls `/api/v1/internal/**` (and the legacy `/api/keycloak/**`, `/api/user-roles/**`) through Feign with the M2M token of the currently configured static issuer; a registry-only CP would refuse them | **Decided:** `/api/v1/internal/**` stays on the currently configured staff / platform M2M issuer for T4 (scope rule unchanged); registry-based issuers are not required there; T7 migrates to platform-realm M2M. The legacy admin APIs (in-cluster callers only, same M2M token) stay on that issuer too, `authenticated()` until PHASE_10 | Company self-service under `/api/v1/platform/companies/{code}/**`: **no interim exception** (owner) — platform token only until the PHASE_10 `/api/v1/company/**` APIs; legacy `POST /api/attachment/upload` → `denyAll()` (owner) **Ended 2026-10-05 by P03-T7 (REVIEW):** the internal and legacy APIs now accept only platform-realm M2M clients with an allowed `azp` (as decided here). |

---

## 4. Current code baseline (`CODE-INSPECTED`, 2026-10-05)

| Area | Today (after P02-T11 A–E) | PHASE_03 change |
|---|---|---|
| Gateway issuers | `configs/RealmIssuers`: two **static** issuers — staff (`spring.security.oauth2.resourceserver.jwt.issuer-uri` = `…/realms/${KEYCLOAK_REALM}`) + optional customer realm (`erp.security.customer-realm.*`); `JwtIssuerReactiveAuthenticationManagerResolver`; `issuedBy(issuer)` route rule (customer tokens only on `/portal/**`) | Registry-driven resolver, in place (T5) |
| Gateway company | `filter/CompanyContextFilter` + `CompanySelection.fromToken`: company = verified `tenant_id` (`CMP_<n>`); trust headers stripped; `COMPANY_MODULES:<code>` (403 / 503); `X-Company-Code` forwarded from the token; WebSocket ticket company check; platform-admin routes `/control-panel/api/v1/platform/**` carry no company | Add issuer → company registry equality and issuer scope rules (T5) |
| Gateway logging | `configs/SecurityConfig.logFilter()` prints requests | Removed (T5) |
| CP security | `PlatformSecurityConfig`: JWK only, no issuer / audience check; `/api/v1/internal/**` needs `SCOPE_CONTROL_PANEL_METADATA_READ`; `anyRequest().permitAll()` | T4 (not stage F) |
| Realm registry | `IdentityRealm` table + repository; interim `IdentityRealmResolver` (configured `keycloak.realm` must be registered and ACTIVE); no service, no API | T3 |
| Service filter | COMMON `TenantContextFilter` reads `X-Company-Code` (`CMP_<n>` only); exclusions: `path.contains("/swagger-ui" | "/v3/api-docs" | "/actuator")` + configured prefixes (no `..`) | `TenantVerificationFilter` beside it; prefix matching (T6) |
| Invalidation | COMMON `TenantInvalidationListener` on Redis channel `tenant-invalidation` reads the **body as a plain company code** and evicts the metadata cache only; `TenantInvalidationMessage(companyCode, action)` exists but is unused; `DynamicRoutingDataSource.evictPool` never called; **no publisher** anywhere; gateway has no listener | T10 |
| Feign / M2M | `FeignSecurityInterceptor`: defaults `keycloak.server-url=http://192.168.5.15:8081`, `realm=ERPSystem`, `client-id=admin-cli`, placeholder secret; `synchronized getM2mToken()`; propagates `X-Company-Code`, `X-User-Token`, `X-Performed-By(-Id)`; `X-Internal-Token` on every call | T7 (not stage F) |
| Keycloak admin client | `KeycloakIdentityProviderAdmin`, `KeycloakAdminTokenProvider` bound to one configured realm | T11 (not stage F); T1's applier uses its own minimal admin calls in tests |
| Jobs | `@RunPerTenant`, `PerTenantJobAspect.runForEachCompany`; ACCOUNT-BOOK `EwayBillExpiryScheduler` tenant-less | T9 |
| ACCOUNT-BOOK | `SecurityConfig` `anyRequest().permitAll()`; `CorsConfig` allows `http://192.168.5.*:*` | T6 (D3-9), T7 |
| Compose | Host ports published for several services | T8 |
| Keycloak in tests | `testcontainers-keycloak` 3.0.0 (CP test dependency); no test uses it yet; no Keycloak image local | D3-5 (CLOSED, CD-30: local Keycloak 25.0.6 used instead) |

---

## 5. Task order

```text
T1 Keycloak as code ──> T3 realm registry + issuer API ──> T10 invalidation publisher / listeners
                                   │                                │
                                   └──────────────> T5 gateway <────┘ ──> T6 service verification
T4 CP issuer / audience (after T3)   T7 Feign / M2M / secrets / azp (after T1, T6)   T8 compose ports (after T6)
T2 bootstrap + /platform/me (after PHASE_04 E-B)   T9 jobs   T11 realm-aware identity sync (after T1, T3)
T12 login-code discovery (after T3, T11)   T13 reconciler / export / metrics (after T11)
```

| Order | Task | Depends on | Main modules | Stage F |
|---|---|---|---|---|
| 1 | T1 | D3-4, D3-5 (CLOSED, CD-30) | repository `keycloak/`, CP | **yes** |
| 2 | T3 | T1 | CP, COMMON | **yes** |
| 3 | T10 | T3 | CP, COMMON, API-GATEWAY | **yes** |
| 4 | T5 | T3, T10, D3-6, D3-7 | API-GATEWAY | **yes** |
| 5 | T6 | T3, T5, D3-8, D3-9 | COMMON, ACCOUNT-BOOK, tests of every service | **yes** |
| 6 | T4 | T3 | CP | later |
| 7 | T7 | T1 (clients), T6 | COMMON, CP, ACCOUNT-BOOK, CRM, NOTIFICATION, QC | later |
| 8 | T9 | P02-T4 | COMMON, ACCOUNT-BOOK | later |
| 9 | T8 | T6 | `docker-compose.yml` | later |
| 10 | T2 | PHASE_04 E-B | CP | later |
| 11 | T11 | T1, T3 | CP | later |
| 12 | T12 | T3, T11 | CP, API-GATEWAY | later |
| 13 | T13 | T11 | CP | later |

### 5.1 Stage F of P02-T11 = P03-T1 + T3 + T10 + T5 + T6

The P02-T11 inventory stage F ("company Keycloak realm with the `tenant_id` mapper, realm registry API, gateway issuer → company equality, service token verification") is exactly these five tasks, executed in the order above, each ending REVIEW with tests before the next one starts. Out of stage F: T2, T4, T7, T8, T9, T11, T12, T13 (they stay PENDING; T4 is the natural next one). Realm **creation for real companies** (PHASE_08 `CREATE_REALM` step, T11 admin client) is not part of stage F: stage F creates realms only as test realms in the local Keycloak 25.0.6 from the template (CD-30; kept: `co-101`, `co-102`, `co-103`, `it-platform`).

Deletion / rename: none is needed. Every change is in place (`RealmIssuers`, `SecurityConfig`, `CompanyContextFilter`, `CompanySelection`, `TenantContextFilter`, `TenantInvalidationListener`, `TenantInvalidationMessage`, `IdentityRealmResolver`) or a new file. If an implementation step turns out to need a deletion or rename, the agent stops and asks the owner.

---

## 6. Task details

Common rules: follow the existing code style; every security check is **fail-closed**; no secret in code, logs or documents (`.env` key names only); all Keycloak tests use the local Keycloak 25.0.6 (CD-30; gated local-Keycloak ITs) or a local test JWKS; each security feature has a configuration flag for rollback (PHASE_03 §15), default **on** outside `dev`; every module's `CompanyTenancyArchitectureTest` stays green; ACCOUNT-BOOK business code and its 94 baseline test failures are not touched.

### 6.0 Company realm template, naming and claims (shared by T1, T3, T5, T6)

| Element | Rule |
|---|---|
| Realm name | `co-<n>` (`^co-[0-9]{1,8}$`, `TenantResourceNaming.REALM_NAME`); `<n>` = the number of the company code (`CMP_101` → `co-101`), never `tbl_company.id`; derived **only** by `TenantResourceNaming.realmName(companyCode)`; immutable; one company = one realm (CD-06); never `ERPSystem` |
| Issuer | `iss` = `<keycloak public base URL>/realms/co-<n>` exactly as Keycloak emits it (depends on the Keycloak hostname setting); stored in `tbl_identity_realm.issuer_uri` and compared by exact string |
| JWKS | `<base>/realms/co-<n>/protocol/openid-connect/certs`, stored in `jwks_uri` |
| Client scope `erp-company` (default scope of `erp-web`, `erp-portal`) | (1) **hard-coded claim mapper** `tenant_id` (`oidc-hardcoded-claim-mapper`, `claim.name=tenant_id`, `claim.value=${COMPANY_CODE}` = `CMP_<n>`, `jsonType.label=String`, in access token, ID token and userinfo) — set when the realm is created, not editable by users or company admins, no user attribute involved; (2) audience mapper `nyaalkaran-erp` (`oidc-audience-mapper`, `included.custom.audience`, access token) |
| Roles | realm roles only (CD-14): `COMPANY_SUPER_ADMIN`, `COMPANY_ADMIN`; module roles `<MODULE>_<LEVEL>` (`USER`, `TEAM_LEAD`, `MANAGER`, `ADMIN`, `SUPER_ADMIN`) for purchased modules (created by PHASE_08 / PHASE_10 with the same template data); built-in `roles` scope → `realm_access.roles`; **never** a `PLATFORM_*` role; no client roles |
| Clients | `erp-web` (public, Authorization Code + PKCE, Full Scope Allowed on, redirect URIs from `${ERP_REDIRECT_URIS}`); optional `erp-portal` (public, PKCE, Full Scope Allowed off); `erp-identity-admin` (confidential, service account only, `realm-management` `manage-users`, `view-users`, `query-users`, `view-realm`, `client-jwt` with `${CP_JWKS_URL}` — D3-10) |
| Realm settings | display name `${DISPLAY_NAME}`, access token ≤ 5 min, brute-force protection, password policy, required actions (verify email, update password), admin events on, SMTP from a vault reference (CD-19, no password in the file) |
| Template version | `template_version` = `v1`, stored in `tbl_identity_realm` |
| Placeholders | `${REALM_NAME}`, `${COMPANY_CODE}`, `${DISPLAY_NAME}`, `${ERP_REDIRECT_URIS}`, `${CP_JWKS_URL}`; added during T1 implementation (2026-10-05): `${PORTAL_REDIRECT_URIS}` (portal client) and `${SMTP_HOST}`, `${SMTP_PORT}`, `${SMTP_FROM}`, `${SMTP_FROM_DISPLAY_NAME}`, `${SMTP_USER}` (platform SMTP, CD-19; no SMTP block without them); `${vault.<key>}` is a Keycloak vault expression and passes through. An unknown placeholder or a `REALM_NAME` that is not `realmName(COMPANY_CODE)` is refused. The template files live in `keycloak/realm-template/v1/` and are copied onto the CP classpath by the CP build. |

### T1 — `erp-platform` runbook, company realm template as code, applier, configuration check (P03-T1) — stage F

| Item | Detail |
|---|---|
| New files (repository) | `keycloak/platform-realm/RUNBOOK.md` (owner steps on a development / later production Keycloak: realm `erp-platform`; roles `PLATFORM_SUPER_ADMIN`, `PLATFORM_ADMIN`; conditional OTP flow mandatory; access token ≤ 5 min; client `platform-console` (public, PKCE); `svc-<service>` confidential clients (list from the module catalog); `erp-platform-identity-admin` (`realm-management` user rights); `erp-realm-provisioner` in `master` with `create-realm` only; vault SMTP reference; `.env` key names). `keycloak/realm-template/v1/realm.json`, `clients/erp-web.json`, `clients/erp-portal.json`, `clients/erp-identity-admin.json`, `client-scopes/erp-company.json` (§6.0 mappers), `roles/company-admin.json`, `roles/module-levels.json`, `smtp.json` (vault reference), `README.md` (placeholders, version rules). |
| New CP code | `platform/keycloak/template/RealmTemplate` (loads v1 from the classpath, renders the §6.0 placeholders, refuses unknown ones and any realm name other than `TenantResourceNaming.realmName(companyCode)`); `RealmTemplateApplier` (idempotent PUT-style steps for PHASE_08: realm settings, client scope + mappers, clients, roles; existing items updated, never deleted; takes an admin client argument — the realm-parameterized production client is T11); `KeycloakConfigurationCheck` (read-only: platform realm roles + OTP flow; for sampled ACTIVE company realms: `tenant_id` mapper value = registry `company_code`, audience mapper present, predefined roles present, **no `PLATFORM_*` role**, `template_version`; warnings in `dev`, health DOWN elsewhere; disabled when no Keycloak admin access is configured). |
| Not in T1 | Creating realms for real companies (PHASE_08 via T11); any change on a shared Keycloak. |
| Tests | `RealmTemplateTest` (rendering; unknown placeholder refused; realm name must equal `realmName(CMP_<n>)` — `co-102` for `CMP_101` refused; `tenant_id` hard-coded to the company code; no `PLATFORM_*` role; no password / secret in any template file). `RealmTemplateKeycloakIT` (real Keycloak 25.0.6; delivered as the gated `LocalKeycloakRealmTemplateIT` on the local Keycloak, CD-30): apply the template for `CMP_101` → `co-101` and `CMP_102` → `co-102`; re-apply is idempotent; the configuration check passes (acceptance 6); a password-grant test token of a test user in `co-101` (test-only direct-grant client) carries `tenant_id=CMP_101`, `aud` ∋ `nyaalkaran-erp`, `iss` ends with `/realms/co-101`; a user cannot change `tenant_id` (no user attribute mapping). |

### T3 — Realm registry service + internal issuer API (P03-T3) — stage F

| Item | Detail |
|---|---|
| CP code | `platform/identity/realm/RealmRegistryService`: `registerCompanyRealm(company, issuerUri, jwksUri, templateVersion)` → PROVISIONING (realm name = `TenantResourceNaming.realmName(company.companyCode)`, asserted with `assertMatchesTenant`; one realm per company, unique name / issuer / company — existing table constraints), `activate`, `disable`, `enable`, `archive` (PROVISIONING → ACTIVE ↔ DISABLED → ARCHIVED; no other transition; a realm row is never deleted), `findActiveByIssuer`, `findByCompany`, `findByRealmName`; platform realm row (`realm_type = PLATFORM`, no company) registered idempotently at startup from `erp.platform.realm.realm-name` (`erp-platform`), `issuer-uri`, `jwks-uri` (environment; not registered when unset); every change audited (`IDENTITY_REALM_REGISTERED / _ACTIVATED / _DISABLED / _ENABLED / _ARCHIVED`) and published after commit (T10). `IdentityRealmRepository`: finder methods as needed. `IdentityRealmResolver` (interim, P02-T5) stays until T11. |
| CP API | `platform/controller/internal/InternalIdentityRealmController` `GET /api/v1/internal/platform/identity-realms` → `[{realmType, realmName, issuer, jwksUri, companyCode}]` for **ACTIVE** realms only (`companyCode` null for PLATFORM); protected like the other internal APIs today (`SCOPE_CONTROL_PANEL_METADATA_READ`), platform-realm M2M + `azp` after T7. No write API in stage F (writes come from the provisioning worker, PHASE_08). |
| COMMON code | `nyaalkaran.erp.common.security.realm.RealmRegistryClient` (Feign, `CONTROL-PANEL-SERVICE`, unique `contextId`), `RealmRegistry` (in-memory snapshot + Redis key `REALM_REGISTRY`, TTL, refresh on `tenant-invalidation` type `REALM`; **fail closed** when there is no snapshot and the CP is unreachable; a stale snapshot is used for at most the TTL), lookups `byIssuer(iss)` → `{realmType, companyCode, jwksUri}`. Auto-configuration guarded like the other COMMON tenant beans (no new component scan of COMMON packages). |
| Tests | Unit: transitions (illegal ones refused), realm name must match the company code, platform row registration idempotent, API returns ACTIVE only, payload shape; COMMON registry: cache hit / miss, fail closed without snapshot, refresh on invalidation. IT (MySQL 192.168.5.141): register / activate / disable round trip through the API; exact-name DB cleanup. |

### T10 — Invalidation publisher, cache and pool eviction (P03-T10) — stage F

| Item | Detail |
|---|---|
| Message | Redis channel `tenant-invalidation` (unchanged name); JSON `TenantInvalidationMessage {companyCode, type, at}` with `type` ∈ `STATUS`, `RESOURCE`, `REALM` (the unused `action` field becomes `type`; field change, no file rename); for `REALM`, `companyCode` is the company of the realm (null for the platform realm). No other publisher exists today, so no compatibility mode is needed; the listener refuses a message without a valid `CMP_<n>` (except `REALM` of the platform realm) and logs it. |
| CP code | `platform/tenant/TenantInvalidationPublisher` (publishes **after commit**); called by `CompanyService.changeStatus`, `CompanyResourceRegistry` state changes and `RealmRegistryService`. |
| COMMON code | `TenantInvalidationListener`: parse the JSON; `STATUS` / `RESOURCE` → evict `TENANT_META:<code>:*` (L1 + L2) and `DynamicRoutingDataSource.evictPool(code)` (+ attachment routing pool); `REALM` → refresh the realm registry snapshot (T3). Metric `tenant_invalidation_latency`. |
| Gateway code | Reactive Redis subscription to `tenant-invalidation`: `REALM` → refresh the gateway registry (T5); `STATUS` → nothing yet (gateway status check is P06-T6). |
| Tests | Unit: publisher after commit only (rolled-back transaction publishes nothing); listener per type; malformed message ignored safely. IT (loopback Redis 127.0.0.1:16379): a status change evicts the metadata cache and the pool within 5 s (acceptance 7, metric); a realm disable refreshes the registry in a COMMON consumer. |

### T5 — Gateway: registry-driven issuers, issuer → company equality, scope enforcement (P03-T5) — stage F

| Item | Detail |
|---|---|
| `configs/RealmIssuers` (changed in place) | Becomes registry-driven when `erp.security.registry-issuers.enabled=true` (D3-7): the issuer of the incoming token must be in the gateway registry snapshot (loaded from `GET /api/v1/internal/platform/identity-realms` with the D3-6 client, cached, refreshed by T10 and a periodic refresh); per issuer a cached `NimbusReactiveJwtDecoder` built from the registry `jwksUri` with validators: issuer (exact), signature, `exp` / `nbf` (small clock skew), audience `nyaalkaran-erp`; unknown or non-ACTIVE issuer → **401**; registry unavailable and no snapshot → **503**. The customer-realm issuer stays as today behind its property (D3-3). With the flag false (dev only) the current static staff issuer behaviour remains. |
| `filter/CompanySelection` (changed in place) | New `fromToken(jwt, registry)`: issuer → registry entry; **PLATFORM issuer** → no company, allowed only on `/control-panel/api/v1/platform/**`; **COMPANY issuer** → `company = registry.companyCode`; the `tenant_id` claim must be one string equal to it (existing `CMP_<n>` validation kept), else **403 `TENANT_MISMATCH`** + a security log event (issuer, realm, claimed value, request id; no token); company tokens are refused on `/control-panel/api/v1/platform/**` (**403 `SCOPE_NOT_ALLOWED`**) and allowed on business routes and `/control-panel/api/v1/company/**`. Without the flag the stage D rule (verified `tenant_id` only) stays. |
| `filter/CompanyContextFilter` (changed in place) | Uses the registry decision; everything else of stage D stays: trust-header stripping, `X-Company-Code` from the decided company only, `COMPANY_MODULES:<code>` check (403 / 503), WebSocket ticket company / user check, `X-User-Token` re-derived. |
| `configs/SecurityConfig` | Authentication manager resolver from `RealmIssuers`; `logFilter()` removed (it prints requests); route rules unchanged otherwise. |
| `configs/RateLimiterConfig` | Unchanged (key from the decided company). |
| Config | `application.yml`: `erp.security.registry-issuers.enabled`, `erp.security.registry.refresh-interval`, `.cache-ttl`, the D3-6 client keys (`erp.security.registry.client.*`, environment only, no defaults); the static `issuer-uri` stays only for the flag-off mode. |
| Tests | WebTestClient with a local test JWKS per realm (extend the existing `support/LocalJwks`) and a stub registry: registered ACTIVE company issuer + matching `tenant_id` → routed with `X-Company-Code` = registry company; unknown issuer 401; DISABLED realm 401; issuer of `co-101` with `tenant_id=CMP_102` → 403 `TENANT_MISMATCH`; missing `tenant_id` 403; platform token on `/crm/**` 403; company token on `/control-panel/api/v1/platform/**` 403; platform token on the platform route → passes without a company; forged `X-Company-Code` / `X-Organization-Id` / `Active-Org` still stripped; registry down without snapshot 503, with snapshot served; `REALM` invalidation removes a disabled issuer within the refresh window; WebSocket ticket of another company still 403; flag off → stage D behaviour. Existing 66 gateway tests (+ 7 architecture) stay green or are updated where they assumed the static issuer. One IT with the local Keycloak 25.0.6 (CD-30; `LocalKeycloakGatewayIT`): real `co-101` token through the gateway. |

### T6 — `TenantVerificationFilter` + exclusion fix + ACCOUNT-BOOK alignment (P03-T6) — stage F

| Item | Detail |
|---|---|
| COMMON code | `tenant/TenantVerificationFilter` (order right after `TenantContextFilter`): for every **non-internal, non-public** request (public = the existing excluded prefixes such as docs, actuator, signed public share / quotation links): `X-User-Token` required → JWT signature via the realm registry JWKS of its `iss` (T3), issuer ACTIVE and of type COMPANY, audience `nyaalkaran-erp`, `exp`; then **registry(iss).companyCode == `tenant_id` == `X-Company-Code`**; `sub` == `X-Performed-By-Id` when that header is present; failure → 401 (token) / 403 (mismatch) + security log event, `TenantContext` never set. **Internal requests** (`/api/v1/internal/**`) keep today's internal-token checks in stage F; the platform-realm M2M + `azp` rule is added by T7. The WebSocket / STOMP handshake is an HTTP request and is covered; STOMP frames keep the stage B tenant attribute. `TenantVerificationAutoConfiguration`: flag `erp.security.verify-user-token.enabled` (default **true**; `false` only in the `dev` profile). |
| `TenantContextFilter` | Exclusions → normalized path-prefix matching (`/actuator`, `/v3/api-docs`, `/swagger-ui` as prefixes after the context path; no `contains`; `..` still refused). No other change; it still accepts only `CMP_<n>`. |
| ACCOUNT-BOOK (D3-9) | `security/SecurityConfig`: `permitAll` replaced by the common verification (filter registration / ordering only); the exact diff is shown to the owner first. No business class, no business test changed; the 94 baseline failures stay identical. |
| Test support (D3-8) | A new COMMON test-jar package (e.g. `nyaalkaran/erp/common/security/testsupport/**`, added to the test-jar includes like the stage E contract; no Spring components): test token signer + JWKS + registry stub to mint `co-<n>` / `CMP_<n>` tokens. |
| Tests | Unit per rule: missing token 401; bad signature 401; unknown / disabled issuer 401; platform-issuer token on a business route 403; `tenant_id` ≠ registry company 403; `X-Company-Code` ≠ `tenant_id` 403; `sub` ≠ `X-Performed-By-Id` 403; expired 401; internal path not affected; excluded prefixes; flag off. IT in CRM and ACCOUNT-BOOK (forged headers sent **directly** to the service → 401 / 403; valid token → 200) (acceptance 3, 4). Regression: every service IT runs with the fixture (D3-8 a) or the flag (D3-8 b); full unit + IT regression; ACCOUNT-BOOK failing list identical. |
| Risk | Latency (JWKS + registry cached; measured in PHASE_12); long Feign chains with an expiring user token (≤ 5 min) — small clock skew, failures are 401 (fail closed). |

### T2 — `PlatformBootstrapRunner` + `/platform/me` (P03-T2) — later

Unchanged from r1 except names: active only when `erp.platform.bootstrap.username`, `.email` and the one-time `.initial-password` come from environment / secret configuration (no seeded defaults, CD-28 item 9); refuses when an ACTIVE `SUPER_ADMIN` exists unless `erp.platform.bootstrap.allow-additional=true`; creates the user in `erp-platform` via `erp-platform-identity-admin` with `PLATFORM_SUPER_ADMIN`, temporary password, `VERIFY_EMAIL`, `CONFIGURE_TOTP`; registers `tbl_identity` (platform realm, `account_scope = PLATFORM`, no company code) + `tbl_platform_admin(SUPER_ADMIN, BOOTSTRAP)` through PHASE_04 E-B; audit `PLATFORM_SUPER_ADMIN_BOOTSTRAPPED`; the password is never logged, stored or returned. `GET /api/v1/platform/me` (platform issuer only). Tests as r1 (company-realm token with a role named `PLATFORM_SUPER_ADMIN` refused).

**Status 2026-10-06: REVIEW** (owner go 2026-10-06: PHASE_03 exception for T2, PHASE_04 E-B early slice, T3 / T4 / T11 accepted as REVIEW dependencies, D4-4 rename approved). As implemented: `platform/bootstrap/PlatformBootstrapRunner` (after startup; inactive unless `ERP_PLATFORM_BOOTSTRAP_USERNAME` / `_EMAIL` / `_INITIAL_PASSWORD` are all set; refuses before any Keycloak call when an ACTIVE Super Admin exists unless `ERP_PLATFORM_BOOTSTRAP_ALLOW_ADDITIONAL=true`; platform realm only, as `erp-platform-identity-admin`; an existing username is verified and never duplicated; otherwise the user is created with a temporary password and `UPDATE_PASSWORD`, `VERIFY_EMAIL`, `CONFIGURE_TOTP`; `PLATFORM_SUPER_ADMIN` mapped and verified; rows through the E-B `PlatformAdminBootstrapRegistrar` (platform realm row locked, audit `PLATFORM_SUPER_ADMIN_BOOTSTRAPPED`); failures audited `PLATFORM_SUPER_ADMIN_BOOTSTRAP_FAILED`; the password is never logged / stored / returned); `GET /api/v1/platform/me` (platform issuer only; identity by (platform realm, sub) + `tbl_platform_admin`; identity, admin type, status); D4-4: `PlatformRole.PLATFORM_SUPER_ADMIN` and the JWT role checks use the `erp-platform` spelling. The runbook rights of `erp-platform-identity-admin` (`manage-users`, `view-users`, `query-users`) proved sufficient on Keycloak 25.0.6. No Liquibase; D3-10 untouched. Tests: CP unit 340 / 0, CP IT 147 / 0 (incl. `PlatformAdminBootstrapMySqlIT` on 192.168.5.141 and `LocalKeycloakPlatformBootstrapIT` in the temporary realm `it-platform-t2`, deleted). Operator procedure: `keycloak/platform-realm/RUNBOOK.md` §1a. Details: WORK_LOG 2026-10-06 "P03-T2".

### T4 — Control-plane issuer and audience validation; platform-issuer-only authorities; route rules (P03-T4) — later

`PlatformSecurityConfig`: `JwtIssuerAuthenticationManagerResolver` from `RealmRegistryService` (platform issuer + ACTIVE company issuers); audience `nyaalkaran-erp`; `/api/v1/platform/**` → platform issuer only; `/api/v1/company/**` → company issuers only, company = registry(issuer) = `tenant_id`; `/api/v1/internal/**` → platform-realm M2M (T7); `anyRequest().permitAll()` replaced by explicit rules for the remaining legacy controllers (`/api/keycloak/**`, `/api/users/**`, `/api/user-roles/**` `authenticated()` until PHASE_10) and `denyAll()` for the rest. `PlatformJwtGrantedAuthoritiesConverter`: `PLATFORM_*` authorities only when `iss` = platform issuer. Tests: company token with role `PLATFORM_SUPER_ADMIN` gets no platform authority; wrong audience 401; unknown issuer 401.

**As decided and implemented (2026-10-05, owner approval of T4, D3-11):** two filter chains — (1) service chain for `/api/v1/internal/**` + the legacy `/api/keycloak|users|user-roles/**` on the currently configured issuer (unchanged until T7 / PHASE_10); (2) user chain for every other route with registry issuers (`ControlPlaneIssuerRegistry` + `ControlPlaneIssuerAuthentication`, cache TTL 60 s, dropped after a committed realm change), audience `nyaalkaran-erp`, `/api/v1/platform/**` platform issuer only, `/api/v1/company/**` company issuer with `tenant_id` = registry company, `/api/attachment/**` and everything else `denyAll()`. Local development switch `erp.security.registry-issuers.enabled=false` (same as the gateway, D3-7). No interim exception for the company self-service routes. **Status 2026-10-05: REVIEW** — the full regression found that the service chain's `securityMatcher(String...)` needs the MVC introspector bean missing in `@DataJpaTest` slices; replaced by explicit path matchers (`PlatformSecurityConfig.serviceChainMatcher()`, same routes, fail closed); full unit green (ACCOUNT-BOOK 94 KNOWN BASELINE identical), full IT 430 / 0.

### T7 — Feign interceptor redesign, M2M clients, dual internal secrets, `azp` allow-lists (P03-T7) — later

`FeignSecurityInterceptor`: required `erp.m2m.token-uri`, `erp.m2m.client-id`, `erp.m2m.client-secret` (startup failure outside `dev`; **no defaults** — removes `192.168.5.15`, `ERPSystem`, `admin-cli`, the placeholder secret); `M2mTokenCache` (non-blocking refresh, backoff); `X-Internal-Token` only on `/api/v1/internal/**`; propagation of `X-Company-Code`, `X-User-Token`, `X-Performed-By(-Id)` unchanged. Internal token checks accept `INTERNAL_TENANT_SECRET` or `INTERNAL_TENANT_SECRET_NEXT` (constant time). `azp` allow-lists per internal API (`erp.security.internal.allowed-azp.<api>`; tenant bootstrap, registry: control plane / gateway only). The gateway registry client (D3-6) moves to `svc-api-gateway`. QC URL literals → required properties; ACCOUNT-BOOK `CorsConfig` origins from configuration. `.env` key names only: `M2M_CLIENT_ID_<SERVICE>`, `M2M_CLIENT_SECRET_<SERVICE>`, `INTERNAL_TENANT_SECRET_NEXT`, `ERP_PLATFORM_REALM`. Tests: internal header only on internal paths; token cache under concurrency; startup refusal without configuration; dual secret; wrong `azp` 403; grep test (acceptance 10).

**Status 2026-10-05: REVIEW** (owner go 2026-10-05, T7 only). As implemented: COMMON `security/m2m` (`M2mTokenCache`, `ClientCredentialsTokenEndpoint`, `M2mClientAutoConfiguration`: no defaults, startup refused outside `dev`) and `security/internal` (`InternalServiceTokens` dual secret, `InternalServiceAuthenticationFilter`: service-side platform-realm M2M + `azp` on every `/api/v1/internal/**`, PHASE_03 §6.4 item 5); CP service chain with `azp` lists `identity-realms`, `companies`, `default`, `legacy-admin`; CP tenant-bootstrap client sends its M2M bearer. Interpretations (no new decision): the runbook's "audience per target group" is implemented as the single audience `nyaalkaran-erp` of the platform clients (runbook item 5) plus per-API `azp` allow-lists; client ids `svc-<lower-case spring.application.name>`; one extra key `ERP_M2M_TOKEN_URI` (the platform token endpoint); QC `QC_BASE_URL` became required. D3-10 untouched (svc-* clients use client secrets). Tests: full unit green (ACCOUNT-BOOK 94 KNOWN BASELINE), IT 435 / 0 incl. real local Keycloak 25.0.6 M2M tokens.

### T8 — Network surface (P03-T8) — later

`docker-compose.yml`: remove host ports of tickets, attendance, account-book, customer-portal, notification, chat, eureka; keep only `api-gateway`; bind infrastructure UIs to `127.0.0.1:`; fix `depends_on`. Owner applies it to shared environments. Test: `docker compose config` check (only the gateway on `0.0.0.0`).

### T9 — Module-aware `@RunPerTenant`, job architecture rule, ACCOUNT-BOOK job (P03-T9) — later

`@RunPerTenant(module = …)`; `PerTenantJobAspect.runForEachCompany` lists companies through `/active?module=` (P02-T4); job architecture test (`@Scheduled` methods in `nyaalkaran.erp..` need `@RunPerTenant` or the explicit runner, except an allow-list of platform jobs) shipped through the COMMON test-jar like the stage E contract (no new dependency needed; ArchUnit only if the owner prefers it); ACCOUNT-BOOK `EwayBillExpiryScheduler` per company (tenant plumbing only). Tests: aspect runs only for entitled companies; e-way bill job per company (acceptance 8).

### T11 — Realm-aware identity sync, realm-parameterized admin client, realm operations (P03-T11) — later

`IdentityProviderAdmin` / `KeycloakIdentityProviderAdmin`: every method takes the realm; `createRealm(template)`, `enableRealm`, `disableRealm`, `createRealmRole`, `assignRealmRole`, `executeActionsEmail`. Token providers: `erp-realm-provisioner` (master) for realm creation, `erp-platform-identity-admin` for the platform realm, `erp-identity-admin` per company realm (signed JWT, D3-10). `IdentitySyncTaskType` additions: `CREATE_REALM_ADMIN_USER`, `INVITE_COMPANY_USER`, `LINK_EXISTING_ACCOUNT`, `ASSIGN_PLATFORM_ROLE`, `REVOKE_PLATFORM_ROLE`, `DISABLE_REALM`, `ENABLE_REALM`, `DISABLE_ACCOUNT`, `ENABLE_ACCOUNT`, `RECONCILE_COMPANY` (company-role tasks PHASE_10). The organization task types and group logic were already removed by P02-T11 stage C. Target realm derived (D3-2); `IdentityRealmResolver` replaced by registry lookups. Tests: `IdentitySyncMySqlIT` for realms; controlled run on the local Keycloak 25.0.6 (CD-30) (create realm from the template, realm admin user, invite, disable, enable). Never against a shared or production Keycloak.

**Status 2026-10-06: REVIEW — D3-10-independent slice** (owner go 2026-10-06; D3-2 approved as written above, no schema change). As implemented: `IdentityRealmTarget` (only created from the registry) + `IdentityRealmTargets` (COMPANY task → `company_code` → registry realm `co-<n>`; PLATFORM task → `erp.platform.realm.realm-name`; identity task → account scope + its single company + stored-realm check; fail closed before any Keycloak call); realm-aware `IdentityProviderAdmin` (the unused organization-group methods dropped); `KeycloakAdminTokenProvider` with three service accounts (`erp-realm-provisioner` and `erp-platform-identity-admin` by client secret; company `erp-identity-admin` **fails closed — D3-10 open**: no signing key storage, rotation, CP JWKS endpoint or `client_assertion`; only a test-only client-secret fallback that refuses to start outside the `test` profile) and the unchanged legacy no-arg token for the out-of-scope `/api/keycloak/**` services; `KeycloakIdentityProviderAdmin.realmProvisioning()` = production `RealmAdminOperations` of the T1 seam; processor for all task types; `PlatformMembershipService` creates company identities in the company's registry realm. Findings for the owner from the local Keycloak 25.0.6 run (template = P03-T1, not changed): `erp-identity-admin` has `fullScopeAllowed: false` and no scope mapping (its tokens carry no realm-management role → 403 on user management); the realm declares no user profile, so Keycloak 24+ drops the `erp_invitation` attribute (Phase 5L retry recognition parks instead). Details: WORK_LOG 2026-10-06 "P03-T11".

### T12 — Company Login Code discovery (P03-T12) — later

CP `platform/controller/publicapi/LoginDiscoveryController` `POST /api/v1/public/auth/discover {loginCode}` → `tbl_company.login_code` → company ACTIVE and its realm ACTIVE → `{authorizationEndpoint of co-<n>, clientId: "erp-web"}`; every other case the same generic 404 "Invalid Company Code" (no enumeration). Gateway public route `POST /public/auth/discover` → CP with the existing Redis rate limiter per IP. Tests: valid code; unknown / suspended / disabled-realm identical; 429. UI side waits for Q-25.

**Status 2026-10-06: REVIEW** (owner go 2026-10-06; T3 and T11 accepted as REVIEW dependencies). As implemented with the owner decisions: answer `{authorizationEndpoint, clientId, issuer}` (no token endpoint), authorization endpoint = ACTIVE registry issuer + `/protocol/openid-connect/auth`, input trimmed + upper-cased, every failure (format, unknown, non-ACTIVE company incl. `AWAITING_DATA_IMPORT`, missing / non-ACTIVE realm, unreadable body) the same 404, client realm / issuer ignored; CP anonymous only for `POST /api/v1/public/auth/discover`; gateway route `login-discovery` with its own fail-closed limiter (5 / minute / client IP, trusted proxies only, Redis unavailable → 503), client `X-User-Token` / `Authorization` removed on this route, answer not re-wrapped. No schema change. Tests: CP unit 280 / 0, gateway unit 128 / 0, CP IT 129 / 0 (incl. MySQL and local Keycloak 25.0.6 discovery ITs), gateway IT 9 / 0.

### T13 — Realm reconciler, per-realm export, per-realm metrics (P03-T13) — later

`RealmReconciler` (scheduled, read-only drift report incl. `tenant_id` mapper value ≠ registry company, `PLATFORM_*` roles in company realms, realms without registry rows and vice versa; `last_reconciled_at`); `RealmExportJob` (per-realm configuration export to the platform bucket); metrics. Tests on the local Keycloak 25.0.6 (CD-30) with injected drift; export to MinIO Testcontainers.

**Status 2026-10-06: REVIEW** (owner go 2026-10-06; T1, T3, T11 accepted as REVIEW dependencies). As implemented with the owner decisions: a separate read-only master service account `erp-realm-auditor` (view roles per `<realm>-realm`; every write refused in code before any HTTP call; `erp-realm-provisioner` not widened); CP `platform/keycloak/reconcile`: `RealmReconciler` (report-only, per realm independently: missing in Keycloak, registry status vs `enabled`, `tenant_id` mapper, `PLATFORM_*` roles, template version, other template invariants, platform realm, Keycloak realms without a registry row; only `last_reconciled_at` written; audit events on new / cleared drift; logs), `RealmExportJob` (configuration-only partial export, service-account users stripped, unsafe exports not stored, platform bucket `erp-platform-realm-exports` with a 30-day lifecycle rule), `RealmOperationsMetrics`; two ShedLock schedulers default off, approved in `ScheduledJobPolicy`. No schema change. Tests: CP unit 313 / 0, CP IT 137 / 0 (incl. `RealmReconcilerMySqlIT` on 192.168.5.141 and `LocalKeycloakRealmReconcilerIT` with the temporary realm `co-105`: controlled drift detected, restore drill passed, realm deleted). **Export permission (owner decision 2026-10-06: auditor read-only, option 2 dedicated export client without fallback to `manage-realm`):** Keycloak 25.0.6 has no export-only permission — the partial export requires `manage-realm` (plain role check) + `view-clients` + `query-groups` (source + measured matrix, RUNBOOK §2a) — so no export client was built and the export stores nothing. **Final owner decision 2026-10-06:** automated partial export is a documented **R1 limitation / blocker** — `manage-realm` intentionally granted to no ERP client (it is write access to the whole realm configuration), `RealmExportJob` kept but disabled by default, no view-API assembly in T13, a future automated export needs a separate owner / security decision; not validated against a real MinIO (mocked client only). Final focused validation: COMMON 8 / 0, CP 44 / 0. Stays REVIEW; D3-10 OPEN. Deviations: no MinIO Testcontainer (no local image; mocked client + in-memory store); module-role completeness and CD-13 assignments out of scope (PHASE_08 / PHASE_10). Details: WORK_LOG 2026-10-06 "P03-T13".

---

## 7. Test plan summary

| Level | Coverage | Acceptance (PHASE_03 §13) |
|---|---|---|
| Unit | template rendering + naming, registry transitions, invalidation publisher / listeners, issuer resolution + equality + scope, verification filter rules (stage F); bootstrap, converter issuer rule, Feign / secrets / `azp`, job aspect (later) | 1, 2, 4, 5, 10 |
| IT (local Keycloak 25.0.6 (CD-30) + MySQL 192.168.5.141 + Redis 127.0.0.1:16379) | template realms + configuration check (T1); registry API (T3); invalidation latency (T10); real `co-101` token through the gateway (T5); forged headers directly to CRM and ACCOUNT-BOOK (T6); identity sync controlled run, reconciler (later) | 3–7 |
| Architecture | every module's `CompanyTenancyArchitectureTest` (stage E) stays green; job rule (T9); grep for literals (T7) | 8, 10 |
| Compose | published-ports check (T8) | 9 |
| Regression | full unit + IT of all modules in one reactor (stage E baseline: unit all green except ACCOUNT-BOOK 94 KNOWN BASELINE with an identical list; IT 354 / 0); pre-flight + exact test-DB cleanup (6 protected DBs) | — |

---

## 8. Manual / operator actions

| # | Action | Who | When |
|---|---|---|---|
| 1 | ~~Approve the Keycloak test image tag and its one-time download (D3-5)~~ — **CLOSED 2026-10-05 (CD-30): Keycloak 25.0.6, local Keycloak used for the ITs** | owner | done |
| 2 | Execute `keycloak/platform-realm/RUNBOOK.md` on the **development** Keycloak (never production by an agent); put `erp.platform.realm.*` values and M2M client ids / secrets into `.env` | owner / operator | before shared-dev end-to-end; not needed for unit / IT |
| 3 | Make the Keycloak hostname setting stable so that `iss` equals the registered `issuer_uri` | operator | before shared-dev end-to-end |
| 4 | Create a development company realm `co-<n>` from the template (applier, owner-run) and register it, or keep `erp.security.registry-issuers.enabled=false` in `dev` (D3-7) | owner / operator | after T1 / T3 |
| 5 | Until T5 is active in an environment: the existing staff / customer development realms keep the interim `tenant_id` mapper (stage D note) | operator | now |
| 6 | Signed-JWT key management of `erp-identity-admin` (S0-2 item 6) | owner | before production use of T11 |

---

## 9. Risks

| Risk | Mitigation |
|---|---|
| `iss` differs between Keycloak configuration and registry (hostname, proxy) | Exact comparison by design; operator action 3; registry IT with Testcontainers issuer |
| Every service IT needs a valid token after T6 | COMMON test-jar fixture (D3-8); flag off only in `dev` |
| Local development loses access after T5 (staff realm not registered) | Flag per D3-7; development `co-<n>` realm |
| Registry / CP outage at the gateway | Snapshot cache + periodic refresh; 503 only without any snapshot (fail closed) |
| The production ERP UI still logs in to `ERPSystem` | T12 backend; UI by the Q-25 team; Shivam identity migration separate (CD-16, P11-T7a) |
| Keycloak feature differences by version | One fixed version, Keycloak 25.0.6 (CD-30), for local and live; configuration check |
| Signed-JWT key management undecided (S0-2 item 6) | Test-only client-secret fallback; production waits |
| A test accidentally targets a shared Keycloak | ITs use only the local Keycloak 25.0.6 through the loopback-only `LocalKeycloak` guard (refuses any non-loopback host), admin login only from `LOCAL_KC_ADMIN_*`; never the root `.env` `KEYCLOAK_*` (shared 192.168.5.15); no `KEYCLOAK_HOST` in test profiles |
| Reintroduction of organization tenancy names | Stage E architecture tests in every module |
| Concurrent builds corrupt the local Maven cache | Build only when no IDE build runs (S0-5 §4.2) |

---

## 10. Documentation findings (reported, not edited)

| # | Finding | Where | Proposed handling |
|---|---|---|---|
| F-1 | `PlatformAdmin` entity needed by P03-T2 but owned by PHASE_04 | PHASE_03 §6.2 vs PHASE_04 §8 | D3-1 (decided) |
| F-2 | "Target realm column" on `tbl_identity_sync_task` not in PHASE_01 §6.2 H | PHASE_03 §8, developer guide §5.2 | D3-2 |
| F-3 | PHASE_03 §8 says `tbl_platform_admin` comes "from PHASE_02" | PHASE_03 §8 | Same as F-1 |
| F-4 | ACCOUNT-BOOK `CorsConfig` contains a `192.168.5.*` origin wildcard | code | T7 |
| F-5 | PHASE_03 document §2, §4, §5, §6.3, §6.4, §7, §11, §13 and developer guide §8 / §9 still name `X-Organization-Id`, `OrganizationSelection`, `OrganizationContextFilter`, the `groups` claim and `ORG_MODULES` | PHASE_03 document, developer guide | P02-T11 stage G (owner go); this plan r2 already uses the company names. **Resolved 2026-10-05 by stage G (REVIEW):** the PHASE_03 document and the developer guide now use `X-Company-Code`, `CompanySelection` / `CompanyContextFilter`, the `tenant_id` claim and `COMPANY_MODULES`; old names remain only in marked historical "existing code" rows. |

---

## 11. Definition of done

- **Stage F (P02-T11):** P03-T1, T3, T10, T5, T6 each REVIEW with the tests of §6; full regression green (ACCOUNT-BOOK 94 KNOWN BASELINE identical); acceptance 2 (route scope part), 3, 4, 5, 6, 7 proven; tracker, WORK_LOG, AGENT_HANDOFF, NEXT_SESSION_PROMPT and the P02-T11 inventory updated; P02-T11 stays IN_PROGRESS until stage G.
- **PHASE_03:** all PHASE_03 §13 acceptance criteria proven; every task REVIEW with test evidence; the owner sets DONE and unlocks PHASE_04 (with PHASE_02 DONE).
