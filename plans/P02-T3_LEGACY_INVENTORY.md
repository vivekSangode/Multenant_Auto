# P02-T3 — Legacy organization-tier inventory (control plane)

| Item | Value |
|---|---|
| Task | P02-T3 — migrate the control-plane callers from Customer / Organization to Company |
| Date | 2026-10-04 |
| Agent | Claude Opus 5.5 |
| Evidence | Local code inspection of `CONTROL-PANEL-SERVICE/src/main` and `src/test` (`grep -w` per class name, excluding the class's own file). No other service references these classes; the only cross-service contracts are `GET /api/v1/internal/platform/organizations/{id}/metadata` and `/active` (COMMON `ControlPlaneClient`), which stay unchanged (P02-T4). The gateway only matches the path prefix `/api/v1/platform/`. |
| Shivam / legacy compatibility | No Shivam-specific code exists in the control plane (searched). CD-16: Shivam is onboarded as a normal company; identity migration and data import are separate future activities. No development control-plane data exists on the local MySQL (192.168.5.141 holds no `control_panel_db`), so no data migration is needed. |
| Status 2026-10-05 | **Superseded by P02-T11 stage C** (owner directive 2026-10-04, approval 2026-10-05): every LEGACY class of this inventory is deleted or renamed to its company equivalent and the §7 allow-list of `LegacyModelArchitectureTest` is **empty** — see `ORG_TO_COMPANY_MIGRATION_INVENTORY.md` §4.1. This document is history. |

## 1. Rules applied (owner, 2026-10-04)

- No class is deleted in this task. Plan r2 lists the removals under "Removed (**owner approval**, copies kept per the owner's `_cleanup_review` process)": deletion is an owner step (`svn move` into `_cleanup_review/`), after T3 has made the classes unused.
- T3 acceptance (plan r2 T3 goal): **no main code uses `Customer`, `Organization`, `OrganizationDatabase` or `OrganizationUser`**; development onboarding and provisioning keep working on `Company`; an architecture test proves that no class in `platform..` references the removed model classes.
- Gateway company selection (`OrganizationSelection`) is PHASE_03 (P03-T5) and is not touched.
- `CMP_<n>` is the target identifier; `ORG_*` remains only where a LEGACY compatibility need is documented below.

## 2. Inventory

Columns: **Kind** = TARGET (target architecture), LEGACY (organization tier to be retired), RE-POINT (kept, its FK changes to the company). **T3 removal** = whether T3 itself requires removing it (always "no deletion now; make unused" for LEGACY). **Replacement** = target code after T3.

### 2.1 Entities and repositories

| Class / file | Purpose today | Main references | Test references | Kind | T3 removal | Replacement | Risk of removal |
|---|---|---|---|---|---|---|---|
| `model/Customer` + `repository/CustomerRepository` | Tenant owner ("customer") | CompanyOrganizationService, CustomerResponse, CustomerUsage, CustomerUser, IdentitySyncProcessor, Organization, OrganizationQuotaService, PlatformAccessResolver, PlatformCustomerController/Service, PlatformMembershipService, PlatformOnboardingController/Service, PlatformOrganizationService, PlatformProvisioningService, PlatformSubscriptionService | 16 classes (service tests and `*MySqlIT`) | LEGACY | Plan: removed with owner approval after the callers move → **unused in T3, deletion = owner** | `Company` (+ `CompanyProfile`), `CompanyService` | Low once unused; `tbl_customer` stays in old dev DBs (`ddl-auto: update` never drops) |
| `model/Organization` + `repository/OrganizationRepository` | Tenant (one per database set) under a customer | 28 main classes (onboarding, provisioning, entitlement, identity sync, access, internal metadata, lifecycle) | 20 test classes | LEGACY | as above | `Company` (company = tenant, CD-01/CD-26); lifecycle → `CompanyService.changeStatus` | Medium: largest caller set; done service by service |
| `model/OrganizationDatabase` + repository | Per-organization DB routing row | Attachment/DomainDatabaseProvisioningService, PlatformInternalService | AttachmentDatabaseProvisioningServiceTest, AttachmentProvisioningMySqlIT, ModuleEntitlementSchedulerMySqlRedisIT | LEGACY | as above | `CompanyResource` (DATABASE rows, RESERVED → CREATED → VERIFIED) via `CompanyResourceRegistry` | Medium: metadata API (T4) reads the new registry |
| `model/OrganizationUser` + repository | Org-level membership (ORG_SUPERADMIN / ORG_ADMIN / ORG_USER) | CustomerUser, IdentitySyncProcessor, OrganizationUserBusinessRole, PlatformAccessResolver, PlatformMembershipService, IdentityReconciliationScheduler | BusinessRoleModelTest, IdentitySyncMySqlIT, PlatformAccessControlMySqlIT, PlatformAccessResolverTest, PlatformMembershipServiceTest | LEGACY | as above (PHASE_02 §5: "org-level memberships" removed) | Company membership on `CustomerUser` (re-pointed to `company_id`); target `tbl_company_user` is PHASE_10 | Medium: org-admin endpoints lose their org tier (see 2.4) |
| `model/OrganizationUserBusinessRole` + repository | Business roles held by an org member | OrganizationUser | BusinessRoleModelTest | LEGACY (depends on `OrganizationUser`) | Not named in the plan; becomes unused with `OrganizationUser` | Company permission policy (PHASE_04 G, CD-11..CD-14) | Low (no runtime caller) — removal listed for owner approval |
| `model/CustomerUser` + repository | Company-tier membership of an identity | CustomerUserRepository, IdentitySyncProcessor, PlatformAccessResolver, PlatformMembershipService | 5 test classes | RE-POINT | No | Kept; `customer_id` → `company_id` → `tbl_company` (plan "Re-pointed") | — |
| `model/CustomerUsage` + repository | Usage counters per customer | CustomerUsageRepository only | ControlPanelArchitectureIntegrationTest | RE-POINT (unused stub) | No | `customer_id` → `company_id`; real usage counters are PHASE_06 | — |
| `model/OrganizationApplication` + repository | Application entitlement per organization | repository only (no service) | ControlPanelArchitectureIntegrationTest | LEGACY (unused stub) | Not named in the plan | Entitlements PHASE_06 | Low — listed for owner approval, not removed |
| `model/BusinessRole` (+ `BusinessRolePermission`, seeder, repository) | System / CUSTOM business-role catalog; CUSTOM roles have `organization_id` | BusinessRoleCatalogSeeder, BusinessRolePermission, BusinessRoleRepository, OrganizationUserBusinessRole | BusinessRoleModelTest | RE-POINT | No | `organization_id` → `company_id` (CUSTOM roles belong to a company); the permission model itself is PHASE_04 G | Low (only the seeder and the model test use it) |
| `model/ProvisioningJob` | Provisioning job per organization | PlatformProvisioningService, status service | provisioning tests | RE-POINT | No | `organization_id` → `company_id` (plan §7: changed only as T3 needs) | — |
| `model/Subscription`, `model/BillingAccount`, `model/Invoice` | Legacy subscription / billing stubs (`customer_id` Long) | PlatformSubscriptionService, SubscriptionResolver | subscription tests | RE-POINT | No | `customer_id` → `company_id`; full redesign PHASE_06 / PHASE_07 | — |
| `enums/OrganizationStatus` | Org lifecycle states | 15 main classes | 13 test classes | LEGACY | Becomes unused | `CompanyStatus` (mapping PENDING → DRAFT, RECOVERING → job state, DEACTIVATED → ARCHIVED, per plan) | Low once unused |
| `enums/CustomerStatus` | Customer states | BillingAccount, CompanyOrganizationService, Customer, CustomerResponse, IdentitySyncProcessor, PlatformOnboardingService | 11 test classes | LEGACY | Becomes unused except `BillingAccount` (PHASE_07) | `CompanyStatus` | Low; `BillingAccount` keeps it until PHASE_07 |

### 2.2 Services

| Class | Purpose today | Callers | Kind | T3 removal | Replacement | Risk |
|---|---|---|---|---|---|---|
| `service/CompanyOrganizationService` | Company admin creates organizations (`"ORG_" + (max + 1)`), quota-checked | PlatformCompanyAdminController, OrganizationQuotaService | LEGACY | Plan: merged into `CompanyService`, generator removed → **unused**, deletion = owner | `CompanyService.create` + `CompanyCodeAllocator` | Low |
| `service/OrganizationQuotaService` (+ `quota/OrganizationQuotaPolicy`) | Organization quota from the plan entitlement | CompanyOrganizationService, ModuleEntitlementService | LEGACY | Plan: removed (owner approval) with its test and the quota part of `CompanyOrganizationQuotaMySqlIT` → **unused** | None (organization tier gone; one company = one tenant) | Low |
| `service/PlatformCustomerService` | Customer read | PlatformCompanyAdminController, PlatformCustomerController | LEGACY → modified | No (re-implemented) | Company read via `CompanyService` | Low |
| `service/PlatformOrganizationService` | Org read + lifecycle (suspend/disable/reactivate) | 4 controllers | LEGACY → modified | No (re-implemented) | Company read + `CompanyService.changeStatus` (master §7.1) | Medium: status semantics change |
| `service/PlatformOnboardingService` | Create customer, then organizations | PlatformOnboardingController | TARGET (modified) | No | Creates a `Company` (+ profile, settings) | Medium |
| `service/PlatformProvisioningService`, `PlatformProvisioningStatusService`, `provisioning/{Domain,Attachment}DatabaseProvisioningService`, `DomainTenantBootstrapService` | Provision DBs per organization, record `tbl_organization_database`, bootstrap | onboarding / provisioning controllers | TARGET (modified) | No | Company code + `CompanyResourceRegistry` (`cmp_<n>_*` names); bootstrap `mode` from `onboarding_type`; `schema_version` stored | Medium-high (end-to-end provisioning) |
| `service/PlatformInternalService` | Internal metadata from `tbl_organization_database` | InternalOrganizationController | TARGET (modified) | No | Registry-backed — **this is P02-T4**, not T3 | — |
| `service/ModuleEntitlementService`, `PlatformSubscriptionService`, `SubscriptionResolver` | Entitlements per organization from the legacy subscription | entitlement/subscription controllers, scheduler | TARGET (modified) | No | Keyed by company (`company_id`); Redis key rename `ORG_MODULES` → company key is P06-T6 (not T3) | Medium |
| `service/PlatformMembershipService`, `security/PlatformAccessResolver` | Company + organization memberships and access checks | admin controllers, interceptor | TARGET (modified) | No | Company tier only (`CustomerUser` re-pointed) | Medium (access rules) |
| `identity/IdentitySyncProcessor`, `IdentityReconciliationScheduler` | Sync identities for org memberships (`^ORG_…` id pattern) | sync worker | TARGET (modified) | No | Company code (`CMP_<n>`); realm-aware sync is P03-T11 | Medium |
| `identity/OrganizationGroupProvisioner` | Creates the Keycloak `ORG_<id>` group used by the gateway's group selection | PlatformProvisioningService | LEGACY compatibility (gateway, PHASE_03) | **No — kept**: the gateway still selects by groups until P03-T5 | Removed by P03-T5 scope (issuer-based resolution) | High if removed now (would remove the only gateway path for LEGACY data) |
| `entitlement/OrganizationEntitlementChangedEvent`, `OrganizationEntitlementSyncListener` | Re-publish module entitlements to the gateway cache | provisioning, lifecycle, subscription services | TARGET (modified) | No | Carry the company code; the `ORG_MODULES` key stays until P06-T6 | Low |

### 2.3 Security

| Class | Purpose | Kind | T3 removal | Replacement |
|---|---|---|---|---|
| `security/CompanyScoped` | Company-scope guard | TARGET | No | Kept; resolves the company by code |
| `security/OrganizationScoped` | Org-scope guard (`adminRequired`) on `PlatformOrgAdminController` | LEGACY | Becomes unused with the org-only endpoints | `CompanyScoped` |
| `security/OrganizationBindingFilter` | Binds the request organization (registered in `PlatformSecurityConfig`) | LEGACY | Becomes unused | Company binding (same filter re-targeted, or removed by owner approval) |

### 2.4 Controllers and DTOs

| Class | Endpoints | Kind | T3 action | Replacement |
|---|---|---|---|---|
| `controller/PlatformCompanyAdminController` | `/api/v1/platform/companies/{id}` (+ `/organizations` list/create, `/members`) | TARGET (modified) | Keep company + members; **remove** the organization list/create endpoints (organization-only, plan) | Company read, members on `CustomerUser` |
| `controller/PlatformOrgAdminController` | `/api/v1/platform/organizations/{id}` (+ members, invitations) | LEGACY (org-only) | Endpoints become organization-only → removed from use; class deletion = owner | Company-level members (PlatformCompanyAdminController); invitations move to the company tier only if a development tool needs them (owner) |
| `controller/internal/PlatformCustomerController`, `PlatformOrganizationController` | Internal customer / organization admin | LEGACY → modified | Keep the paths development tools use, backed by Company services; drop "customer → organizations" listing | Company read / lifecycle |
| `controller/internal/InternalOrganizationController` | Metadata + `/active` | TARGET | Unchanged in T3 (P02-T4) | — |
| `controller/PlatformOnboardingController` | Onboarding | TARGET (modified) | Company onboarding | — |
| DTOs `CreateCustomerRequest`, `CreateOrganizationRequest`, `CreateCompanyOrganizationRequest`, `OrganizationLifecycleRequest`, `CustomerResponse`, `OrganizationResponse`, `OrganizationProvisioningResult` | Request / response shapes | LEGACY / TARGET | Replaced or adapted to Company where the endpoint stays; unused ones listed for owner approval | Company DTOs |

## 3. Owner decisions (2026-10-04: no deletion; invitations stay legacy; group provisioner kept)

> **Decided by the owner on 2026-10-04:** (1) legacy classes are NOT deleted after T3; they stay as legacy / compatibility code until a later dedicated cleanup task explicitly approves removal; (2) organization-level invitations stay as legacy / deprecated compatibility where required; no company-level invitations in PHASE_02 (designed in the identity / provisioning phase); (3) `OrganizationGroupProvisioner` is kept (P03-T5 depends on the legacy ORG_* group flow). The list below is kept for that later cleanup task.

1. **Delete or keep** (after T3 has made them unused, copies via the owner's `_cleanup_review` process): `Customer`, `Organization`, `OrganizationDatabase`, `OrganizationUser` (+ repositories), `OrganizationQuotaService` (+ `OrganizationQuotaPolicy`, `OrganizationQuotaServiceTest`, the quota part of `CompanyOrganizationQuotaMySqlIT`), `CompanyOrganizationService`, `OrganizationUserBusinessRole` (+ repository), `OrganizationApplication` (+ repository), `OrganizationScoped`, `OrganizationBindingFilter`, `PlatformOrgAdminController`, the organization-only DTOs, `OrganizationStatus`.
2. **Organization-level invitations** (`POST /api/v1/platform/organizations/{id}/invitations`): no company-tier counterpart exists in PHASE_02 (company user management is PHASE_10). Keep the org endpoint unused, or move invitations to the company tier now?

`OrganizationGroupProvisioner` stays (LEGACY gateway compatibility until P03-T5).

## 4. Implementation order (plan r2 sub-steps, build green after each)

T3a onboarding → T3b provisioning + registry (incl. `schema_version`, EMPTY_FOR_IMPORT from `onboarding_type`, `allow-legacy-organization-ids` off) → T3c access / membership / identity sync (+ `AccountScope.ORGANIZATION` removal, `Identity` status from P02-T5) → T3d subscription / entitlement → T3e quota removal (made unused) → architecture test.

## 5. T3b design notes (continuation point, 2026-10-04)

- **Coupling with P02-T4:** provisioning writes routing rows that `PlatformInternalService` (metadata API, P02-T4) reads. Switching the writes from `tbl_organization_database` to `tbl_company_resource` without switching the read side breaks domain-service routing. T3b therefore lands together with the T4 read side (the tracker row of T3 already names "metadata API"); the payload shape stays (`TenantMetadata`, PHASE_02 §6.2).
- **Status mapping (master §7.1, `CompanyLifecycle`):** request = DRAFT or PROVISIONING_FAILED → PROVISIONING (claimed under the company row lock, `findByCompanyCodeForUpdate`; a concurrent request sees PROVISIONING → "already in progress"); success = PROVISIONING → ACTIVE (`onboarding_type` NEW, bootstrap mode SEEDED) or → AWAITING_DATA_IMPORT (IMPORT_EXISTING_DATA, mode EMPTY_FOR_IMPORT); failure = PROVISIONING → PROVISIONING_FAILED (was: stay PROVISIONING + reason).
- **Recovery without `RECOVERING`** (plan: "RECOVERING → job state"): retry of a failed run = PROVISIONING_FAILED → PROVISIONING (normal path). A run stuck in PROVISIONING after a restart is claimed through the provisioning job, not the company status: one company-level job row (`job_type` RECOVERY / `status` IN_PROGRESS) inserted under the company row lock, unique per company while IN_PROGRESS; the resource steps stay idempotent. Full job model = PHASE_08.
- **Registry writes:** per resource `CompanyResourceRegistry.reserve` (module id from the catalog by `service_name`, attachment / notification via PLATFORM_CORE rows, CD-28 item 1) → `markCreated` after `CREATE DATABASE` → `markVerified` (host, port, `schema_version` = bootstrap `latestChangesetId`) after a successful bootstrap. Names from `TenantResourceNaming` with the company code (`cmp_<n>_*`).
- **Other T3b items:** bootstrap mode from `onboarding_type`; `allow-legacy-organization-ids` switched off in the services once no `ORG_` ids are provisioned; `OrganizationGroupProvisioner` unchanged (gateway, P03-T5); `ProvisioningJob.organization_id` → `company_id`.
- **Tests:** rewrite `PlatformProvisioningServiceTest`, `PlatformProvisioningStatusServiceTest`, `DomainTenantBootstrapServiceTest`, `AttachmentDatabaseProvisioningServiceTest`, `AttachmentProvisioningMySqlIT` (end state: registry rows VERIFIED with `cmp_<n>_*` names, metadata payload unchanged); run on 192.168.5.141 with `run-and-clean` (exact created test DBs dropped afterwards).

## 6. Status at handoff (2026-10-04)

§5 is implemented in main code (WORK_LOG 2026-10-04 "HANDOFF"). Remaining for T3b / T4: rewrite `AttachmentProvisioningMySqlIT` (the CP test build is red until then), run all CP tests from clean, add `InternalMetadataApiMySqlIT`, run the CP ITs on the local MySQL 192.168.5.141 with exact test-database cleanup. Then T3c, T3d, T3e and the architecture test.

## 7. Status 2026-10-04 (continuation session) — P02-T3 REVIEW

- T3b + T4: done and tested (company provisioning, registry-backed metadata; see WORK_LOG).
- T3c: `CustomerUser` re-pointed to `company_id`; `PlatformAccessResolver`, `PlatformMembershipService`, `IdentitySyncProcessor`, `IdentityReconciliationScheduler` company-only; the organization tier was moved unchanged into the LEGACY classes `LegacyOrganizationAccessResolver`, `LegacyOrganizationMembershipService`, `LegacyOrganizationIdentitySync` (owner decisions (2) and (3) of §3). `Identity` soft-delete → status. Company-admin organization endpoints removed.
- T3d: `Subscription` re-pointed; entitlements, subscription lifecycle and entitlement re-sync keyed by company code.
- T3e: organization quota unused (fails closed), policy kept.
- Controllers on the existing paths backed by company services: `PlatformCustomerController` (company read), `PlatformOrganizationController` (company read + lifecycle via `PlatformCompanyLifecycleService`).
- Stubs re-pointed: `BusinessRole`, `CustomerUsage`, `BillingAccount`, `Invoice`.
- Architecture test `LegacyModelArchitectureTest`: no `platform..` main class references `Customer` / `Organization` / `OrganizationDatabase` / `OrganizationUser`, except this LEGACY allow-list (input for the later cleanup task of §3 item 1): the four model classes + `OrganizationUserBusinessRole`; their four repositories; `PlatformCustomerService`, `PlatformOrganizationService`, `CompanyOrganizationService`, `OrganizationQuotaService`; `CustomerResponse`, `OrganizationResponse`; `LegacyOrganizationMembershipService`, `LegacyOrganizationAccessResolver`, `LegacyOrganizationIdentitySync`.
- Open owner question: `AccountScope.ORGANIZATION` (plan: remove) is still created by the organization invitations kept by decision (2) → kept as LEGACY until the owner decides.
- Follow-up outside the CP: `allow-legacy-organization-ids` off (P02-T6 row).
