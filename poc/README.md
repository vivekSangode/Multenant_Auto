# Nyaalkaran ERP — SaaS Control Plane POC

A runnable proof of concept of the **company-based SaaS architecture** described in
[`SAAS_ARCHITECTURE_MASTER.md`](../SAAS_ARCHITECTURE_MASTER.md) v5.3 and
[`PHASE_01_SAAS_FOUNDATION.md`](../PHASE_01_SAAS_FOUNDATION.md). It proves the R1 admin-panel flow end to end
on real infrastructure:

**company → catalog quote → order → full manual payment → subscription + entitlements → provisioning
(DB + bucket per purchased module) → fail-closed tenant routing → upgrade → suspension**

**Portals:** [`frontend/`](frontend/README.md) has a **platform console** (onboard companies, quotes, payments,
live provisioning, per-company white-label branding with live preview) and a **company portal** (login code →
the company's own realm and look; vehicles, inventory, bookings). No build step: `bash frontend/serve.sh`, then
open http://localhost:4200.

**Car-dealer microservices:** [`services/`](services/README.md) adds three separate Spring Boot services
(vehicle catalog, inventory, booking), built with SOLID and a shared `tenant-starter`. One deployment serves every
onboarded car company, each in its own databases. [`postman/car-dealer-saas.postman_collection.json`](postman/car-dealer-saas.postman_collection.json)
onboards two companies and drives the same APIs with each company's token. [`CURL_FLOW.md`](CURL_FLOW.md) lists
every API as a curl command with its real response, recorded by [`scripts/curl-flow.sh`](scripts/curl-flow.sh).
[`scripts/seed-demo-data.sh`](scripts/seed-demo-data.sh) loads three demo car companies for testing multi-tenancy.

## Tech stack

| Concern | Choice | Why (doc reference) |
|---|---|---|
| Language / framework | **Java 21, Spring Boot 3.4** (Web, Data JPA, Validation, Security, Actuator) | Existing services are Spring Boot 3.4 (master §1) |
| Database | **MySQL 8.4** — `control_panel_db` + one schema per company per module (`cmp_<n>_<module>_db`) | AD-03, CD-01, CD-03 |
| Schema management | **Liquibase** — CP baseline + a changelog run *inside every company DB* | CD-27, master §16.5 |
| Runtime cache | **Redis 7** — `COMPANY_MODULES:<CMP_n>`, `TENANT_STATUS:<CMP_n>` | master §10.6 |
| Object storage | **MinIO** (S3 API) — `cmp-<n>-<module>-bucket` | CD-01, master §6.5 |
| Pools / local cache | **HikariCP** per company DB, **Caffeine** L1 cache | master §6.6 |
| API docs | **springdoc-openapi** (Swagger UI) | — |
| Identity | **Keycloak 26.3.5** — platform realm `erp-platform` + one realm `co-<n>` per company; **Spring Security OAuth2 resource server** (JWT) + **Keycloak admin client** | master §14, CD-06, CD-11, CD-14, CD-17 |
| Tests | **JUnit 5, AssertJ, Testcontainers** (MySQL, Redis, MinIO, Keycloak), Awaitility | PHASE_12 |
| Local infra | **Docker Compose** | — |

## Run it

Prerequisites: JDK 21, Maven 3.9+, Docker.

The `minio/minio` images can no longer be pulled from Docker Hub. Load the pinned image
(`minio/minio:RELEASE.2025-09-07T16-13-09Z-cpuv1`, used by docker compose and the tests) from the provided tar once:

```bash
docker load -i minio-image.tar
```

```bash
docker compose up -d
```

```bash
mvn spring-boot:run
```

- Swagger UI: http://localhost:8080/swagger-ui.html (every API needs a Bearer token, see below)
- Keycloak console: http://localhost:8180 (`admin` / `admin`, operator only). Realm `erp-platform` is imported from
  [`keycloak/erp-platform-realm.json`](keycloak/erp-platform-realm.json) with the Platform Super Admin
  `superadmin` / `change-me`; company realms `co-<n>` are created by provisioning.
- MinIO console: http://localhost:9091 (`minioadmin` / `minioadmin`)
- Host ports: MySQL `3307`, Redis `6380`, MinIO API `9000`, Keycloak `8180`, app `8080` (chosen to avoid clashing
  with local installs)

Platform token (the app must reach Keycloak at the same URL as the clients, because it is the token issuer;
override with `KEYCLOAK_URL`):

```bash
curl -s -d grant_type=password -d client_id=platform-console -d username=superadmin -d password=change-me http://localhost:8180/realms/erp-platform/protocol/openid-connect/token
```

Walk through the whole flow with [`http/demo-flow.http`](http/demo-flow.http) (IntelliJ HTTP Client: run the requests
top to bottom; ids and tokens are carried between steps, including the simulated acceptance of the Company Super
Admin invitation and the company-realm token).

## Tests

```bash
mvn test
```

- `PricingEngineTest` — the illustrative scenarios S1–S5 of master §12.3 (e.g. S4 = 1575 IU), duplicates,
  redundant bundles, manual discount, tax, non-sellable platform-core modules. No database needed.
- `TenantResourceNamingTest` — the frozen naming rules of master §6.3.
- `EndToEndFlowTest` — the full lifecycle of two companies against real MySQL, Redis, MinIO and Keycloak
  containers (needs Docker). Every call uses a real token: platform calls a `erp-platform` token, tenant calls a
  `co-<n>` token. Covers: CMP_n allocation, mandatory dependency pricing, cash + cheque payments,
  over-payment refusal, "no provisioning before 100 % verified", provisioning of the realm (roles, `tenant_id`
  mapper, invited Company Super Admin, IDP_REALM `VERIFIED`) + 5 DBs + buckets + Liquibase, tenant routing to
  `cmp_<n>_crm_db`, `X-Company-Code` ignored, `MODULE_NOT_ENTITLED`, 401 for no / garbage / platform token and for
  a realm whose `tenant_id` is not its own company, company token refused on the platform API,
  `MODULE_ACCESS_DENIED` until the user holds `CRM_USER`, upgrade via `ADD_MODULE` job (new module roles added to
  the realm) with physical data isolation, suspension → `COMPANY_SUSPENDED`, reactivation, platform auth.

## What the POC implements (mapped to the docs)

| Doc decision / section | Where in code |
|---|---|
| Company = customer + tenant; `CMP_<n>` single identifier (CD-26); race-safe `SELECT … FOR UPDATE` sequence (§6.2) | `company/CompanyService`, `common/IdSequenceService` |
| Frozen resource naming `cmp_101_crm_db`, `cmp-101-crm-bucket`, realm `co-101` (§6.3) | `common/TenantResourceNaming` |
| Company lifecycle state machine + history + audit (§7.1, §8.3) | `company/CompanyStatus`, `CompanyService.transition` |
| Database-driven catalog: modules, dependencies, bundles, plans, immutable plan versions, prices (§11, §12.1) | `catalog/*`, `db/changelog/cp/002-seed-catalog.sql` |
| Pricing algorithm: bundle expansion, duplicate drop, redundant bundle, mandatory-dependency closure, users, storage blocks, manual discount, tax (§12.2) | `billing/PricingEngine` (pure, unit-tested) |
| Orders frozen at QUOTED, idempotency keys, document numbering `ORD/2026-27/00001` per FY (§6.9, CD-33) | `billing/OrderService`, `common/DocumentSeriesService` |
| **Full payment only**, several instruments, cheque verification, no partial activation (CD-07, CD-20) | `billing/PaymentService` |
| Invoice on full payment, subscription PENDING → ACTIVE, items with price snapshots (§13.1) | `PaymentService.fulfill`, `subscription/SubscriptionService` |
| Entitlement engine: diff-apply, charged-entitlement guard (UQ), platform-core modules, allowances (§13.3, §13.5) | `subscription/EntitlementEngine` |
| Redis published **after commit** + periodic resync (§10.6, §19.3) | `subscription/RuntimeCachePublisher`, `RuntimeCacheService` |
| Durable provisioning jobs/steps, atomic lease claim, retries with exponential backoff, resume, one active job per company, entitlement-driven plan, no destructive compensation (§7.3, §7.4) | `provisioning/*` |
| One DB per entitled module + attachment + notification, Liquibase per tenant, one bucket per purchased module, registry `RESERVED → CREATED → VERIFIED` (§6.4, §6.5, §16.5) | `provisioning/StepExecutor`, `TenantSchemaMigrator` |
| Shared environment DB credentials, no per-company MySQL user, least-privilege grant on `cmp\_%` (CD-03) | `docker/mysql-init/01-users.sql`, `tenant/TenantDataSourceFactory` |
| Fail-closed tenant gate: status + entitlement from Redis, missing key = deny, Redis error = 503 (§10.5, §10.6) | `tenant/TenantAccessInterceptor` |
| Dynamic routing to the company DB from the registry, pool per DB, pools closed on suspension (§6.6) | `tenant/TenantRoutingService` |
| Sample domain service with no tenant code and no company column | `crm/CrmCustomerController`, `db/tenant/crm.sql` |
| Platform realm `erp-platform`; platform API accepts only its tokens (issuer, signature, `aud` = `nyaalkaran-erp`), authority `PLATFORM_SUPER_ADMIN` from `realm_access.roles` (§14, CD-04, CD-14) | `keycloak/erp-platform-realm.json`, `common/SecurityConfig` |
| `CREATE_REALM`: realm `co-<n>` from the template as code, idempotent (create if missing, repair drifted mappers): client `erp-web`, hard-coded `tenant_id` = `CMP_<n>`, audience mapper, realm roles `COMPANY_SUPER_ADMIN`/`COMPANY_ADMIN` + `<MODULE>_<LEVEL>` of the purchased modules; verified, then IDP_REALM `VERIFIED`; re-applied by `ADD_MODULE` for new module roles (PHASE_01 §6.5.3, §6.5.9, CD-06, CD-14, CD-17) | `provisioning/RealmProvisioner`, `StepExecutor` |
| `INVITE_COMPANY_SUPER_ADMIN`: first user of the realm from `tbl_company.super_admin_email`, role `COMPANY_SUPER_ADMIN`, required actions `UPDATE_PASSWORD` + `VERIFY_EMAIL`, no password set by the CP (§6.5.7) | `provisioning/RealmProvisioner` |
| Tenant API: issuer allow-list from the registry (issuer → VERIFIED IDP_REALM row → company), JWKS per issuer, `aud` check, issuer company must equal `tenant_id`; no client header trusted; plus "COMPANY_SUPER_ADMIN or a role of the module" (§14.1, §14.3, §6.5.3) | `tenant/CompanyRealmIssuers`, `tenant/TenantAccessInterceptor`, `common/CompanyRoles` |
| Registry metadata for domain services, M2M tokens of `svc-*` clients with an `azp` allow-list (PHASE_01 §6.6.1, master §15.2) | `tenant/InternalRegistryController`, `common/SecurityConfig.internalApi`, `svc-*` clients in `keycloak/erp-platform-realm.json` |
| Automotive modules `vehicle` / `inventory` / `booking` + bundle `AUTO_DEALER`, tenant changelogs, served by real microservices (§15.3 checklist via `tenant-starter`) | `db/changelog/cp/003-seed-automotive.sql`, `db/tenant/{vehicle,inventory,booking}.sql`, [`services/`](services/README.md) |

## Deliberate simplifications (out of POC scope)

| Target (doc) | POC stand-in |
|---|---|
| Platform Super Admin bootstrapped once by the CP from secret configuration, temporary password, `VERIFY_EMAIL` + `CONFIGURE_TOTP`, plus an ACTIVE `tbl_platform_admin` row (CD-04, CD-28 item 9, §8.1) | `superadmin` / `change-me` is in the realm import file, permanent password, no OTP; authority is the realm role alone |
| Browser login with PKCE through `platform-console` / `erp-web`; Company Login Code discovery (CD-15) | Clients are PKCE-ready, but tests and `demo-flow.http` use the **password grant** (`directAccessGrantsEnabled` is on — must be off in production) |
| `erp-realm-provisioner` in `master` with only `create-realm` (CD-17) | The Keycloak 26 bootstrap admin service account with that client id (full admin of `master`) |
| `tbl_identity_realm` registry with `issuer_uri`, `jwks_uri`, `template_version`, reconciler (PHASE_01 §6.5.2) | The issuer is derived from `poc.keycloak.url` + realm name and looked up in the `IDP_REALM` row of `tbl_company_resource`; no reconciler, no template version |
| Invitation e-mail through the platform SMTP (CD-19); `erp-identity-admin` per realm (CD-17); identity sync, `tbl_identity` / `tbl_company_user` | No SMTP: `poc.keycloak.send-invite-email=false`, the test and `demo-flow.http` accept the invitation through the admin API. Only `erp-web` is created; no identity mirror in the CP |
| Realm disabled on suspension / archive (PHASE_01 §6.5.3 "Realm lifecycle") | The realm stays enabled; suspension is enforced by the `TENANT_STATUS` gate |
| API gateway + per-service JWT verification (§14, §15) | No gateway: the CP's tenant API and each car microservice run the gateway checks themselves (`CompanyRealmIssuers` + `TenantAccessInterceptor`, `tenant-starter`); see [`services/README.md`](services/README.md) for the service-side simplifications |
| CP Permission Policy, `@RequiresPermission`, `TENANT_ROLE_PERMS` (CD-12, §10) | Not implemented — module entitlement + "holds a role of the module" only |
| Tax rules, seller entity, billing policy (CD-25, Q-04/Q-29/Q-42 pending) | `poc.billing.tax-rate-percent` (default 0 → `TAX_RULE_NOT_CONFIGURED` warning); invoice issued on full payment |
| `tbl_payment` + `tbl_manual_payment` | Merged into one `tbl_payment` |
| Proration, renewals, expiry scheduler, downgrades (§12.4, §13.2) | Not implemented |
| Storage usage metering, user-seat enforcement (PHASE_09/10) | Allowances are computed and stored; not enforced |
| Real prices | All prices are the **illustrative** IU values of master §12.3 |

## API overview

Platform (`Authorization: Bearer <erp-platform token>`, realm role `PLATFORM_SUPER_ADMIN`):

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/platform/catalog?period=MONTHLY` | Catalog + current price list |
| POST / GET | `/api/v1/platform/companies` | Create (DRAFT) / list companies |
| GET | `/api/v1/platform/companies/{code}` | Company |
| POST | `/api/v1/platform/companies/{code}/status` | Suspend / reactivate / disable / archive |
| GET | `/api/v1/platform/companies/{code}/overview` | Subscriptions, entitlements, allowances, resources, Redis projection |
| POST / GET | `/api/v1/platform/companies/{code}/orders` | Quote (frozen order) / list |
| POST | `/api/v1/platform/orders/{id}/confirm` · `/cancel` | Confirm (subscription PENDING) / cancel |
| POST | `/api/v1/platform/orders/{id}/payments` | Record a manual payment |
| POST | `/api/v1/platform/payments/{id}/verify` · `/reject` | Cheque cleared / bounced |
| POST | `/api/v1/platform/companies/{code}/provision` | Start `CREATE_COMPANY` provisioning |
| GET | `/api/v1/platform/companies/{code}/provisioning-jobs` | Jobs and steps |
| POST | `/api/v1/platform/provisioning-jobs/{id}/retry` | Resume a FAILED job |

Tenant (`Authorization: Bearer <co-<n> token>`: company = `tenant_id` of a registered realm, and the user holds
`COMPANY_SUPER_ADMIN` or a role of the module, e.g. `CRM_USER`):

| Method | Path | Purpose |
|---|---|---|
| POST / GET | `/api/v1/tenant/crm/customers` | Business data in the company's own `cmp_<n>_crm_db` |
| GET | `/api/v1/tenant/crm/_routing` | Shows which physical DB served the request |

Internal (`Authorization: Bearer <M2M token of an allow-listed svc-* client of erp-platform>`, used by
[`services/`](services/README.md); `poc.internal.allowed-clients`):

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/internal/realms/company?issuer=…` | Company of a registered, VERIFIED company realm (404 `UNKNOWN_ISSUER`) |
| GET | `/api/v1/internal/companies/{code}/modules/{module}/database` | VERIFIED database name, host and port of (company, module); never credentials |

## Project layout

```
src/main/java/com/nyaalkaran/erp/poc/
  common/        base entity, naming, sequences, document series, audit, security, errors
  catalog/       modules, dependencies, bundles, plans, versions, prices, snapshot
  company/       company, lifecycle, status history, resource registry
  billing/       pricing engine, orders, payments, invoices
  subscription/  subscriptions, entitlement engine, allowances, Redis projection, overview
  provisioning/  jobs, steps, worker, step executor, tenant Liquibase, MinIO, Keycloak realms
  tenant/        tenant context, company-realm issuers, access gate, routing, datasource factory
  crm/           sample domain module
src/main/resources/db/changelog/   control-plane Liquibase baseline + catalog seed
src/main/resources/db/tenant/      per-module tenant changelogs (crm.sql, _default.sql)
keycloak/                          platform realm import (erp-platform, incl. svc-* M2M clients)
services/                          car-dealer microservices + tenant-starter (own Maven reactor, see services/README.md)
postman/                           Postman collection: onboard two car companies, drive the same APIs per tenant
scripts/                           curl-flow.sh (every API, see CURL_FLOW.md), seed-demo-data.sh (three demo companies)
frontend/                          platform console + company portal (static ES modules, see frontend/README.md)
```

Reset local state: `docker compose down -v`.
