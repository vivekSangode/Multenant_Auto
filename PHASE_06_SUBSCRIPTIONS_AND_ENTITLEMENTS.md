# PHASE 06 — Subscriptions and Entitlements

| Item | Value |
|---|---|
| Master architecture | [SAAS_ARCHITECTURE_MASTER.md](SAAS_ARCHITECTURE_MASTER.md) §13, §10.6 |
| Design source | [PHASE_01 §6.2 D (subscriptions, items, periods, events) and E, §6.9.2–6.9.3](PHASE_01_SAAS_FOUNDATION.md) |
| Depends on | [PHASE_05](PHASE_05_PLANS_BUNDLES_PRICING_AND_DISCOUNTS.md) (plans, engine), [PHASE_04](PHASE_04_PLATFORM_ADMIN_AND_MODULE_CATALOG.md) (catalog) |
| Enables | PHASE_07, PHASE_08, PHASE_09, PHASE_10 |
| Status | **Current (2026-10-12, latest): DONE (owner confirmation B / CD-43 item 7 — conditions met: CD-43 items 1, 2, 2b, 3 and 5 recorded and Docs Batch 1 completed, 2026-10-12; 9 / 9 tasks DONE).** Earlier: **Current (2026-10-12): UNLOCKED; 9 / 9 tasks DONE (P06-T8 DONE (owner approval 2026-10-12: "I have reviewed the P06-T8 implementation and its test evidence. I approve P06-T8 as DONE."; status-only)); closure decisions CD-43; the phase is declared DONE only under the owner condition B; status in `PHASE_STATUS_TRACKER.md`.** Earlier: **Current (2026-10-12): UNLOCKED; 8 / 9 DONE (P06-T1, T2, T3, T4, T5, T6, T6-F1, T7); P06-T8 REVIEW (implemented 2026-10-12 after the owner authorization "P06-T8 — Owner Authorization for Implementation", A-1..A-5; owner review pending, not DONE); status in `PHASE_STATUS_TRACKER.md`.** Earlier: **Current (2026-10-12): UNLOCKED; 8 / 9 DONE (P06-T1, T2, T3, T4, T5, T6, T6-F1, T7); P06-T8 PENDING — readiness reviewed, owner decisions D-T8-1..D-T8-5 approved for planning (CD-42), implementation not authorized (status in `PHASE_STATUS_TRACKER.md`).** *Historical:* **UNLOCKED** (2026-10-08, CD-33): implementation plan APPROVED; 4 / 8 DONE (2026-10-10): P06-T1 DONE, P06-T2 DONE, **P06-T4 DONE** (owner acceptance 2026-10-10), **P06-T5 DONE** (owner acceptance 2026-10-10, CD-36; follow-up findings M-1 / T-1 retained); P06-T3 / T6..T8 PENDING (P06-T6 owner decisions T6-1..T6-10 recorded 2026-10-10, CD-36) (task status in `PHASE_STATUS_TRACKER.md`, decisions in `plans/PHASE_06_IMPLEMENTATION_PLAN.md` §3b..§3e). Code assessment: PARTIALLY_COMPLETED (legacy single-subscription model) |

> **Revision 2026-10-03 (v5, decision register):**
> - **CONFIRMED DECISION CD-07:** a subscription becomes ACTIVE only when its order is **fully paid** and confirmed. The v4 activation rules (FULL_PAYMENT / MINIMUM_PAYMENT / ADMIN_APPROVAL) are withdrawn.
> - **Renewal is manual** in R1, done by the Platform Super Admin. The scheduler only flags subscriptions that are about to expire and applies the expiry policy; it never creates or charges renewals by itself.
> - **CONFIRMED DECISION CD-22 (Q-07 closed for R1):** grace period = **0 days**. ACTIVE → EXPIRED at `end_date`. The scheduler warns 7 days and 1 day before expiry. A configurable grace period is future.
> - **FUTURE CAPABILITY:** automatic renewal (`auto_renew`) with online payments (PHASE_14).

---

## 1. Objective

1. **Subscriptions per company.** A company may hold **several** subscriptions, each with one billing period (MONTHLY or YEARLY), one currency and its own renewal cycle.
   - Items reference the **plan version** they were priced with.
   - Lifecycle: PENDING, TRIAL (future), ACTIVE, PAST_DUE (reserved; **not used in R1**, CD-22), SUSPENDED, EXPIRED, CANCELLED.
   - **Renewal history:** `tbl_subscription_period`.
   - **Transition history:** `tbl_subscription_event`.
2. **Order-driven changes.** Subscriptions are created or changed only by **fulfilled billing orders** (PHASE_07): NEW, UPGRADE, DOWNGRADE, RENEWAL. Renewal orders are created **manually** by the Platform Super Admin (CD-07). A date-based lifecycle scheduler only flags upcoming expiries and applies the grace and expiry rules.
3. **Entitlement engine.** Materialized company entitlements from plan modules, bundles, dependencies, platform core and manual grants. Dependency validation. The **charged-entitlement guard** prevents duplicate charging across subscriptions.
4. **Allowances.** User and storage limits aggregated across all active subscriptions.
5. **Runtime publication.** `COMPANY_MODULES` (renamed from the LEGACY `ORG_MODULES` key, CD-26) and `TENANT_STATUS`, refreshed by events and by a resync scheduler that is on by default.
6. **Retention-safe.** Nothing is deleted when access ends.

## 2. Existing implementation relevant to this phase

> **P02-T11 note (stage G, 2026-10-05):** the tables of this section describe the code as inspected on 2026-10-03. P02-T11 stages A–F (2026-10-04/05, REVIEW) have since replaced the organization tenancy: the CP organization / customer tier (`Customer`, `Organization`, `OrganizationDatabase`, `OrganizationUser`, `OrganizationUserBusinessRole`, `OrganizationQuotaService`, `CompanyOrganizationService`, `OrganizationGroupProvisioner`, `@OrganizationScoped`, `PlatformOrgAdminController`, …) is deleted (stage C); `InternalOrganizationController` → `InternalCompanyController` (`/api/v1/internal/platform/companies/...`), `OrganizationEntitlementSyncListener` → `CompanyEntitlementSyncListener`, `CustomerUser` → `CompanyUser` (`tbl_company_user`), `CustomerUsage` → `CompanyUsage`; runtime `TenantContext.companyCode`, header `X-Company-Code`, Redis `COMPANY_MODULES:<CMP_n>`, resource names `cmp_<n>_…` / `cmp-<n>-…` (stages A / B); gateway `CompanyContextFilter` / `CompanySelection`, company from the verified `tenant_id` claim, no `Active-Org` (stage D). Read old names in this section as these replacements.

| Component | Location | Notes (`CODE-INSPECTED`) |
|---|---|---|
| `PlatformSubscriptionService`, `PlatformSubscriptionController` | `CONTROL-PANEL-SERVICE/.../platform/service/`, `controller/internal/` | Transitions createPending, activate (from PENDING or PAST_DUE), cancel, expire, markPastDue, reassignPlanVersion, correctDates. Pessimistic lock, audit, after-commit `OrganizationEntitlementChangedEvent`. CANCELLED and EXPIRED are terminal. |
| `SubscriptionResolver` | same | Validity at read time (ACTIVE and start ≤ today ≤ end) |
| `ModuleEntitlementService` | same | Organization ACTIVE → customer's valid subscriptions → plan version → `module.*` (union) |
| `ModuleEntitlementCachePublisher`, `CompanyEntitlementSyncListener` (was `OrganizationEntitlementSyncListener`), `ModuleEntitlementSyncScheduler` | `platform/entitlement/` | `COMPANY_MODULES:<companyCode>` (was `ORG_MODULES:<org>`; renamed in P02-T11 stages B / C) with a TTL of 300 s; the scheduler is off by default |
| Quota pattern | `OrganizationQuotaService` | Lock, then count and insert (concurrency IT) |
| Tests | `PlatformSubscriptionServiceTest`, `SubscriptionLifecycleMySqlRedisIT`, `ModuleEntitlement*Test/IT`, `OrganizationEntitlementSyncListenerTest` | `TEST-EXISTS` |

## 3. Existing completed work

| Item | Status |
|---|---|
| Locked, audited transitions with after-commit events | COMPLETED (pattern reused) |
| Redis `COMPANY_MODULES` publication (was the LEGACY key `ORG_MODULES`), fail-closed semantics | COMPLETED (reused; the key was renamed to `COMPANY_MODULES` in P02-T11 stage B, CD-26) |
| Race-safe count-and-insert pattern | COMPLETED (reused for seats) |
| Read-time validity | PARTIALLY_COMPLETED (replaced by the scheduler and materialized state) |

## 4. Reusable components

`PlatformSubscriptionService` (refactored), `ModuleEntitlementCachePublisher` (extended), the sync listener and scheduler (default on), the quota locking pattern, the internal refresh APIs.

## 5. Work requiring modification or redesign

| Item | Change |
|---|---|
| `tbl_subscription` | `customer_id` → `company_id` (FK); `billing_period`, `currency`, `auto_renew` (always false in R1; FUTURE), `cancel_at_period_end`, `grace_until`, `subscription_number`; states + TRIAL, SUSPENDED; **several per company** (the v2 one-live guard is removed) |
| `plan_version_id` on the subscription | Moves to `tbl_subscription_item` (one price snapshot per item) |
| Multiple valid subscriptions with a union of key/value entitlements | Materialized `tbl_company_entitlement` with the charged guard |
| `max_organizations` quota | No longer required |
| `reassignPlanVersion` / `correctDates` admin shortcuts | Replaced by orders (UPGRADE/DOWNGRADE) and an audited `correctDates` for data fixes only (Super Admin) |
| Resync scheduler off by default | On by default |

## 6. New development required

### 6.1 Subscription service (order-driven)
`applyFulfilledOrder(order)` is called by the PHASE_07 fulfilment:
- **NEW:** at order **confirmation** (PHASE_07) the subscription is created in PENDING with its items (`plan_version_id` / `plan_price_id` / `unit_price_snapshot`) and period 1. It becomes ACTIVE **only when the order is fully paid and confirmed** (CD-07; no partial or credit activation). TRIAL is a FUTURE capability; the status and dates are reserved but unused in R1.
- **UPGRADE:** adds or increases items, effective immediately (prorated in the order).
- **DOWNGRADE:** schedules item end and new items at the period end.
- **RENEWAL** (manual, created by the Super Admin; `platform.subscription.renew`): on full payment it extends `end_date` and creates the next `tbl_subscription_period` (PAID). An EXPIRED subscription is reinstated the same way; its data was never touched.
- Every transition appends to `tbl_subscription_event` and is audited.

### 6.2 Lifecycle scheduler (daily, platform ShedLock)
- **Flags** subscriptions N days before `end_date` on the platform dashboard and by **platform email** to the company's billing contact and Company Super Admin (CD-23, R1), so the Super Admin can create the renewal order. It does **not** create or charge renewal orders.
- Warnings **7 days and 1 day** before `end_date` (platform dashboard + platform email, CD-23).
- At `end_date` without a verified paid renewal: directly **EXPIRED** (grace = 0 days, CD-22). `grace_until` stays NULL in R1.
- EXPIRED: access blocked, data kept. Suspension for other reasons is a platform action (SUSPENDED).
- When a company has no remaining ACTIVE subscription, its module entitlements end: business-module requests answer 403 `MODULE_NOT_ENTITLED` (the gateway blocks within the 120 s resync). The company is **not** suspended automatically and its realm is **not** disabled; login, Control Panel and Notification access stay available so that the renewal can be arranged; a renewal restores the entitlements (owner decision D-1, CD-40, 2026-10-12; clarifies T3-7 — the earlier text named `DISABLE_REALM` here).
- Trials end.
- Scheduled downgrades and cancellations take effect.
- Every change triggers an entitlement recompute.

### 6.3 Entitlement engine
`recompute(companyId)`:
1. Collect the sources over **all** active subscriptions: PLAN_MODULE, PLAN_BUNDLE (expanded), DEPENDENCY, PLATFORM_CORE (file, notification) and MANUAL_GRANT.
2. Validate the mandatory closure.
3. Diff-apply with `is_charged` / `charged_guard`. The guard violation is a programming error caught in tests; the engine validates before the insert.
4. Set the status from the subscription and company state.
5. Enqueue `ADD_MODULE` provisioning for modules without a database (PHASE_08).
6. Publish after commit.

### 6.4 Allowances and usage
`tbl_company_allowance` holds:
- USERS = USER_SEAT base + contributions + ADDITIONAL_USERS quantities, over all active subscriptions;
- STORAGE_BYTES likewise.

> **Superseded 2026-10-10 (P06-T5 owner decisions):** the two lines above are the original design. The approved rule is `effectiveLimit = min(includedContributions, applicableIncludedCap) + purchasedAdditionalQuantity` — the base is counted **once per company** (also with mixed YEARLY / MONTHLY subscriptions), only ACTIVE subscriptions' PAID items contribute, the cap applies only to the included contributions, and STORAGE_BYTES are GiB × 2^30; see the note below and plan T5.

`AllowanceService.assertCanAddUser` and `assertCanStore` use the locking pattern. Usage counters are maintained by PHASE_09 (storage) and PHASE_10 (users).

**P06-T5 owner decisions T5-1..T5-10 (2026-10-10; details and the PHASE_07 pricing implication in plan T5):** the USER_SEAT / STORAGE base is counted once per company (not per subscription); only ACTIVE subscriptions' PAID items contribute (narrower than the T4-9 entitlement applicability, which is unchanged); the normal cap is the `max_quantity` of the deterministically selected applicable ACTIVE price, a valid Super Admin override may exceed it; storage in GiB (1 GiB = 2^30 = 1,073,741,824 bytes); the override is an absolute limit with Super Admin authorization, mandatory reason and expiry, audited, and the computed allowance applies again after the expiry; the allowance is recomputed in the entitlement engine's transaction; a missing allowance row means limit 0 (new consumption denied); P06-T5 adds the service and tests only (membership flows, reconciliation: PHASE_10; snapshots: PHASE_09); usage rows are created lazily under the company lock; usage above a reduced limit only rejects new additions (no deactivation, no file deletion). Second readiness 2026-10-10 found B5-1 / B5-2, decided the same day: **B5-1** the highest applicable base and the highest applicable `max_quantity` across the applicable ACTIVE prices of the qualifying subscriptions, deterministic across YEARLY / MONTHLY; no applicable price → base 0, no cap; **B5-2** a lazily created usage row starts with `billable_users = 0` (counting, integration and reconciliation: PHASE_10). Technical assumptions TA5-1..TA5-7 approved (TA5-3 with D-1 (b): previously purchased qualifying additional users / storage are not reduced solely because a newly published price has a lower cap; TA5-5 with D-2: locking reads of the allowance and usage rows after the company lock, refresh of loaded entities, a concurrent first-use test). Third readiness review NOT READY (BL-1 / BL-2: the D-1 (b) formula), decided the same day: **`effectiveLimit = min(includedContributions, applicableIncludedCap) + purchasedAdditionalQuantity`** — the cap applies only to the included part (base once per company + item contributions), purchased additional quantities are never capped a second time (order-level caps stay in pricing); examples: included 5, cap 12, purchased 10 + 5 → 20; 7 + 7 → 19. TA5-2 clarified (an ACTIVE subscription in a PAID period makes the base / cap eligible without a qualifying item; purchased quantities need qualifying PAID items); the override PUT creates a missing allowance row under the company lock; recompute and override both use the company lock and locking reads (`CompanyAllowance` has no `@Version`). PHASE_07 implications in plan T5. **Fourth readiness review 2026-10-10: READY (PASS)**, no blocker; proposed implementation assumptions recorded (an override-created row starts with computed fields 0, deleting a missing override is a no-op; `included_quantity` = uncapped included contributions, bytes for STORAGE_BYTES; the allowance calculation does not reuse the engine's ARCHIVED-company skip). No implementation authorization yet.

### 6.5 Runtime publication
- `COMPANY_MODULES:<companyCode>` and `TENANT_STATUS:<companyCode>` (TTL 300 s); resync every 120 s (on by default). Both keys of a company are written together by one Lua script; R1 assumes a standalone or Sentinel Redis — not Redis Cluster (the two keys are not hash-tagged; re-check with BD-12 if Cluster is chosen; CD-37 TA6-4).
- The gateway `TENANT_STATUS` check (fail-closed) is added to `CompanyContextFilter` (was `OrganizationContextFilter`).

### 6.6 Company read APIs
`GET /control-panel/api/v1/company/{subscriptions,entitlements,limits}` (`company.subscription.read`, `company.usage.read`). **P06-T7 (REVIEW 2026-10-12, CD-41):** the company comes only from the verified company-realm token (`tenant_id`); until PHASE_10 all three need the realm role `COMPANY_SUPER_ADMIN` (D-T7-5); subscriptions of all statuses with items, unit-price snapshots and periods, entitlement rows incl. ENDED (source, charged flag, manual-grant reason / expiry), limits (included, purchased, effective, override, limit in force) and usage (D-T7-6) — the users usage is a current counter without a measurement timestamp, `updatedAt` is the usage row's last update and the storage usage keeps `storageMeasuredAt` (owner decision F1 option A); read-only.

## 7. Exact services, files and database areas involved

| Area | Files |
|---|---|
| Control plane | `platform/service/PlatformSubscriptionService.java` → `platform/subscription/*`; `SubscriptionResolver` (retired); `ModuleEntitlementService` → `platform/entitlement/EntitlementEngine`; publisher, listener and scheduler; `platform/quota/*` (pattern reused; organization quota removed) |
| Gateway | `API-GATEWAY/.../filter/CompanyContextFilter.java` (`TENANT_STATUS`) |
| Database | `control_panel_db`: `tbl_subscription` (modified), `tbl_subscription_item`, `tbl_subscription_period`, `tbl_subscription_event`, `tbl_company_entitlement`, `tbl_company_allowance`, `tbl_company_usage`, `tbl_company_usage_snapshot` (PHASE_01 §6.2 D/E) |

## 8. Proposed schema changes

As defined in PHASE_01 §6.2 D (subscription part) and E. Key constraints:
- UQ `subscription_number`;
- UQ (`subscription_id`, `period_no`);
- the entitlement UQ, plus the **charged guard** UQ (`company_id`, `module_id`, `charged_guard`);
- allowance UQ per type.

Owner decisions E6-1..E6-9 (2026-10-08; PHASE_06 plan §3b) complete the column definitions: `source_key` and the `charged_guard` rule on `tbl_company_entitlement`, the allowance / usage / snapshot columns of the master §13.5 / §15, PHASE_07 references as nullable columns without a physical FK, the PHASE_01 `item_type` values, and `tbl_document_series` created by P06-T1 for the `SUBSCRIPTION` series (`SUB/{FY}/{SEQ:5}`, CD-33).

## 9. API and integration requirements

| Endpoint | Permission |
|---|---|
| `GET /api/v1/platform/admin/companies/{code}/subscriptions` | `platform.subscription.read` |
| `POST /api/v1/platform/admin/subscriptions/{id}/{suspend,reinstate,cancel,schedule-cancel}` (numeric id — the number contains slashes, D-T7-3; P06-T7) | `platform.subscription.manage` (v1: Super Admin only, D-T7-4) |
| `POST /api/v1/platform/admin/companies/{code}/entitlements/manual-grants` | Super Admin (reason, expiry) |
| `GET/POST /api/v1/internal/platform/companies/{companyCode}/modules[/refresh]` | Internal (existing) |
| `/control-panel/api/v1/company/{subscriptions,entitlements,limits}` (GET) | Company permissions (until PHASE_10: realm role `COMPANY_SUPER_ADMIN`, D-T7-5) |

Purchases and changes go through **orders** (PHASE_07). There is no direct "create subscription" API.

**Gateway contract:**

| Response | Code | When (owner decisions T6-1..T6-4, CD-36, 2026-10-10; aligned with master §7.1) |
|---|---|---|
| 503 | `COMPANY_NOT_READY` | `TENANT_STATUS` = DRAFT, PROVISIONING, PROVISIONING_FAILED or AWAITING_DATA_IMPORT |
| 403 | `COMPANY_SUSPENDED` | `TENANT_STATUS` = SUSPENDED |
| 403 | `COMPANY_DISABLED` | `TENANT_STATUS` = DISABLED |
| 403 | `COMPANY_ARCHIVED` | `TENANT_STATUS` = ARCHIVED or PURGE_APPROVED |
| 403 | `MODULE_NOT_ENTITLED` | company ACTIVE, `COMPANY_MODULES` present without the module — incl. a `#NONE`-only set (an ACTIVE company with no entitlement, e.g. expired subscriptions — no separate status or code, T6-4; CD-37 B-1) |
| 503 | `ENTITLEMENT_UNAVAILABLE` | `TENANT_STATUS` or `COMPANY_MODULES` missing, an unknown status value, or Redis unavailable (fail closed, T6-3) |

`TENANT_STATUS:<CMP_n>` holds the raw `CompanyStatus` name. The status check runs on **every company-scoped route** (business modules, CONTROL-PANEL company routes, NOTIFICATION, WebSocket ticket issuance) **before** the entitlement check; platform routes and `/public/**` are exempt; a SUSPENDED company has **no unrestricted GET**: per master §7.1 only GET / HEAD on an explicit allow-list of company subscription and billing view routes (P06-T7: the three company read routes `/control-panel/api/v1/company/{subscriptions,entitlements,limits}`, D-T7-8; P07-T10 billing views later) stays open, everything else → 403 `COMPANY_SUSPENDED` (CD-37, replacing the "all GET" reading of T6-2). `COMPANY_MODULES:<CMP_n>` of an ACTIVE, not-listed company is always present; an empty set is the single reserved member `#NONE`, which is never a module, token or grant (CD-37 B-1). Blocking is **API-level only**: no Keycloak realm change on a status change (T6-4, P03-6 / T3-7).

**Status-check-only routes (IC-1, P06-T6):** CONTROL-PANEL company routes and NOTIFICATION get the status check only — no `COMPANY_MODULES` lookup. An ACTIVE company whose module key is evicted because its entitlement recompute or publication failed (recovery backlog, T6-3) therefore gets 503 `ENTITLEMENT_UNAVAILABLE` on business-module routes but **keeps its company console and notifications** until the recovery job or the resync republishes it; a missing `TENANT_STATUS` or any non-ACTIVE status is still refused on those routes as in the table above.

**Recovery backlog and publication order (P06-T6 review N1, 2026-10-12):** a company is listed in the recovery backlog before its eviction takes the company lock, and every publication of the committed state decides between publishing and evicting only while it holds that lock (`CompanyRuntimePublication.publishUnlessListed`), so on one CONTROL-PANEL instance no stale module set can follow an eviction. **Known limitation:** the backlog is in memory per CONTROL-PANEL instance — a failure listed on one instance is invisible to a publication on another, where the race remains, bounded by the next successful retry on the listing instance, the resync (every 120 s) and the key TTL (300 s).

## 10. Dependencies on other phases

| Phase | Relationship |
|---|---|
| PHASE_05 | Prerequisite: plans and engine |
| PHASE_07 | Calls `applyFulfilledOrder`; receives renewal orders |
| PHASE_08 | `ADD_MODULE` jobs |
| PHASE_09 / PHASE_10 | Storage and user usage |

## 11. Security and tenant-isolation requirements

1. Role permissions never override entitlements: a company-realm role of a non-entitled module grants nothing (CD-11, role ≠ entitlement).
2. A missing Redis key means deny. Redis is rebuildable from the CP.
3. Suspension takes effect within 120 s at the gateway (immediately via events). Pool eviction comes in PHASE_03.
4. **No data deletion on any transition** (static check + IT).
5. Duplicate charging is prevented by the engine **and** by the DB guard.

## 12. Ordered implementation tasks

| # | Task | Status |
|---|---|---|
| 1 | JPA entities and schema for the subscription, item, period, event, entitlement, allowance and usage tables (Hibernate during development; **no CP Liquibase changelog**, CD-27; final baseline in PHASE_11A) | DONE (2026-10-09, owner-approved; plan §3b) |
| 2 | Refactor the subscription service (company, several subscriptions, items, new states, order-driven) | DONE (2026-10-09, owner acceptance; plan §3c) |
| 3 | Lifecycle scheduler (7-day and 1-day warnings, expiry at `end_date` with 0-day grace, scheduled changes); manual renewal hook | DONE (2026-10-12, owner approval after the correction round; non-blocking limitations retained — plan T3 Status row). Earlier: REVIEW (2026-10-12, implemented after the explicit owner authorization, CD-40; owner review pending; WORK_LOG "P06-T3 implemented → REVIEW"). Earlier: PENDING (readiness NOT READY 2026-10-09 → owner decisions T3-1..T3-7, plan §3d; readiness reruns after T4 / T5 / T6) |
| 4 | Entitlement engine with the charged guard; manual grants | DONE (2026-10-10, owner acceptance after the third fresh-context review of the final correction round (N1–N3): PASS WITH NON-BLOCKING FINDINGS F1–F6; residual risks R1–R6 retained; plan §3e / T4 row). Earlier: REVIEW (2026-10-09, implemented per plan §3e; correction rounds 2026-10-10 after the two independent reviews) |
| 5 | Allowances and usage counters | DONE (2026-10-10, owner acceptance of the independent review PASS WITH NON-BLOCKING FINDINGS, CD-36; follow-up findings M-1 (InnoDB gap-lock deadlock risk on concurrent first allowance / usage row inserts of different companies) and T-1 (timing-dependent override-expiry IT step) retained, plan T5 row). Earlier: REVIEW (2026-10-10: implemented after the owner authorization — allowance recompute in the engine, seat / storage checks, override endpoint, lazy usage rows; CP unit 812 / 0, full CP IT 258 / 0 / 0 / 0; owner review pending. Earlier: T5-1..T5-10, B5-1, B5-2, TA5-1..TA5-7 and the final allowance formula approved and recorded, plan T5; fourth readiness READY (PASS) 2026-10-10; not claimed, no implementation authorization) |
| 6 | Publisher (+ `TENANT_STATUS`), resync on, gateway status check | DONE (2026-10-12, owner approval "APPROVE DONE for both P06-T6-F1 and P06-T6"; review findings handled by the authorized follow-ups and P06-T6-F1; limitations retained). Before: REVIEW (2026-10-12, implemented after the explicit owner authorization, CD-38; owner review pending; WORK_LOG "P06-T6 implemented → REVIEW"). Earlier: PENDING — owner decisions T6-1..T6-10 recorded 2026-10-10 (CD-36, plan T6 row); owner decisions CD-37 (B-1 `#NONE`, TA6-2..TA6-10) 2026-10-12; **third readiness 2026-10-12: READY (PASS)** — waits for an explicit implementation authorization (earlier: second readiness NOT READY, B-1). Code: PARTIALLY_COMPLETED (the publisher exists; the key rename `ORG_MODULES` → `COMPANY_MODULES` (CD-26) was done in P02-T11 stage B) |
| 6a | P06-T6-F1 — N2b company primary-key lock order + retry eligibility 1205 / 1213 (follow-up of the P06-T6 review finding N2; CD-39) | DONE (2026-10-12, owner approval; limitations retained). Before: REVIEW (2026-10-12, implemented after the owner authorization CD-39; owner review pending; dependency exception: P06-T6 is REVIEW) |
| 7 | Company read APIs | DONE (2026-10-12, owner approval; D-T7-1..D-T7-9, CD-41) |
| 8 | Remove the plan key/value entitlements, `SubscriptionResolver`, the organization quota (+ the former P05-T7 `tbl_plan_entitlement` / orphaned plan DTOs, DEFERRED here 2026-10-08) | DONE (2026-10-12, owner approval; `PlanEntitlement*` kept — CD-43 items 2 / 2b; earlier: readiness reviewed, decisions D-T8-1..D-T8-5, CD-42) |

## 13. Acceptance criteria

1. A company with a YEARLY subscription (HR Suite) and a MONTHLY subscription (Asset) has both entitlement sets. The allowances sum correctly. Each is renewed manually on its own cycle, with its own `tbl_subscription_period` rows.
2. Trying to buy the HRMS module standalone while HR Suite is active is rejected (engine), and a forced insert violates the DB guard (test).
3. After a plan price change, manual renewal orders use the current published version unless the company is on a contract version. Old items keep their snapshot.
4. A subscription without a paid renewal becomes EXPIRED at `end_date` (0-day grace, CD-22). The gateway blocks within 120 s. No data is touched. A manual renewal order with a confirmed full payment reinstates the subscription. A subscription order that is not fully paid never activates.
5. Concurrent seat checks never exceed the allowance.

## 14. Testing and validation checklist

- [ ] Unit: state machine, engine diff and guard, allowance math.
- [ ] MySQL + Redis IT: order fulfilment → subscription → entitlements → Redis; scheduler with a fixed clock; multiple subscriptions.
- [ ] Gateway status tests.
- [ ] Regression of the existing subscription and entitlement ITs (adapted).

## 15. Risks and rollback considerations

| Risk | Mitigation |
|---|---|
| Wrong automatic expiry | Warnings 7 days and 1 day in advance, dry-run mode, audit, reinstate by manual renewal |
| Redis outage blocks everyone | Redis HA (PHASE_12), clear 503 |
| Complexity of several subscriptions | One period per subscription; the engine aggregates; a guard for duplicates |

**Rollback:** the scheduler is disableable. Entitlements are recomputable from items at any time.

## 16. Current status

| Item | Status |
|---|---|
| Transition service (customer and plan model) | PARTIALLY_COMPLETED |
| Several subscriptions, items, periods, events | DONE (P06-T1 schema, P06-T2 service; 2026-10-09) |
| Materialized entitlements, charged guard | DONE (P06-T4, owner acceptance 2026-10-10; engine, reader, listeners, manual grants; correction round 2026-10-10: fail-closed recovery of failed recomputes, flush order, fresh company lock; final correction round 2026-10-10 (N1–N3): company lock right after the subscription lock in `SubscriptionService`, no repeated rejection audits + retry backoff 30 s → 1 h, backlog acknowledged only after a successful publication — reviewed by a third fresh-context review, PASS WITH NON-BLOCKING FINDINGS F1–F6; residual risks R1–R6 in plan T4; F1 / R4 / R2 carried into the P06-T6 readiness) |
| Allowances | DONE (P06-T5, owner acceptance 2026-10-10, CD-36; M-1 / T-1 retained as follow-up findings; implemented 2026-10-10; earlier: owner decisions T5-1..T5-10, B5-1, B5-2, TA5-1..TA5-7 and the final formula approved 2026-10-10; fourth readiness READY (PASS); implementation not authorized) |
| Entitlement publication | COMPLETED; the key rename `ORG_MODULES` → `COMPANY_MODULES` (CD-26) was done in P02-T11 stage B; `TENANT_STATUS`, atomic Lua publication, `#NONE`, resync on and the gateway status check implemented in P06-T6 (DONE 2026-10-12, owner approval) |
| Lifecycle scheduler (expiry flags and expiry; manual renewal) | DONE (P06-T3, owner approval 2026-10-12: daily job 00:10 IST + startup catch-up, warnings / expiry / cancel at period end / item boundaries, notification requests; correction round 2026-10-12: stale warnings cancelled at EXPIRED / CANCELLED, no renewal after a terminal cancellation) |
| Company read APIs | DONE (P06-T7, owner approval 2026-10-12, CD-41; earlier the same day REVIEW) |
| **Phase** | **DONE** (2026-10-12, owner confirmation B / CD-43 item 7 — conditions met: CD-43 items 1, 2, 2b, 3 and 5 recorded and Docs Batch 1 completed, 2026-10-12). Earlier: **UNLOCKED** (2026-10-08, CD-33); **2026-10-12: 9 / 9 tasks DONE (P06-T8 DONE (owner approval 2026-10-12: "I have reviewed the P06-T8 implementation and its test evidence. I approve P06-T8 as DONE."; status-only)); closure decisions CD-43; phase DONE only under the owner condition B.** Earlier: 8 / 9 DONE (P06-T1, P06-T2, P06-T4, P06-T5 — owner acceptances 2026-10-10; P06-T6, P06-T6-F1, P06-T3, P06-T7 — owner approvals 2026-10-12); P06-T8 REVIEW (implemented 2026-10-12 after the owner authorization "P06-T8 — Owner Authorization for Implementation", A-1..A-5; owner review pending, not DONE); earlier: P06-T8 PENDING, not authorized, readiness reviewed 2026-10-12 (owner decisions D-T8-1..D-T8-5 approved, CD-42), implementation NOT authorized — awaiting the owner's SVN-state verification (P06-T5 history: decisions T5-1..T5-10, B5-1, B5-2, TA5-1..TA5-7 and the final formula approved 2026-10-10; fourth readiness READY (PASS); implementation not authorized) (status in `PHASE_STATUS_TRACKER.md`) |
