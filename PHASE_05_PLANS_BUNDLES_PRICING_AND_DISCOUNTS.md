# PHASE 05 — Plans, Bundles, Pricing and Discounts

| Item | Value |
|---|---|
| Master architecture | [SAAS_ARCHITECTURE_MASTER.md](SAAS_ARCHITECTURE_MASTER.md) §11.2–11.3, §12 |
| Design source | [PHASE_01 §6.2 C and D (plans, plan versions, plan prices, discounts, coupons, tax rules), §6.9.3–6.9.4](PHASE_01_SAAS_FOUNDATION.md) |
| Depends on | [PHASE_04](PHASE_04_PLATFORM_ADMIN_AND_MODULE_CATALOG.md) (catalog, dependencies, platform permissions) |
| Enables | PHASE_06, PHASE_07 |
| Status | **DONE** (2026-10-08, owner decision "OWNER DECISIONS — PHASE_05 CLOSURE / PHASE_06 UNLOCK", CD-33: tasks 1–6 DONE, owner-approved; task 7 DEFERRED to PHASE_06 / P06-T8 (F5-3), counted closed). Earlier: **PARTIALLY_COMPLETED**: the existing `tbl_plan` / `tbl_plan_version` structure (versioning, `is_custom`) is reusable (`CODE-INSPECTED`). Prices, bundles, discounts, tax and the pricing engine are PENDING. |

---

## 1. Objective

Give the Platform Super Admin code-free control of the commercial offer:

1. **Bundles** of modules. A bundle must be closed under mandatory dependencies.
2. **Plans:** sellable offers for a module, a bundle, user seats and storage.
3. **Immutable plan versions** with **monthly and yearly prices** per currency. **Contract versions** (`is_custom`) serve company-specific pricing such as Shivam's.
4. **Discounts:**
   - **CURRENT:** the engine applies the MANUAL order adjustment (PERCENT or FIXED, reason, approval) defined in PHASE_07.
   - **FUTURE (PHASE_14):** catalog discounts and coupons with validity, limits, eligibility, stacking and priority. They are designed now in PHASE_01 §6.2 D and the engine interfaces are prepared, but the tables are not created in R1.
5. **Tax rules:** GST, IGST vs CGST+SGST by place of supply, built as **configurable, versioned, approved data** (CD-25, PHASE_01 §6.9.8). The **values** stay PENDING until the CA answers Q-04/Q-29; the implementation is not blocked.
6. A deterministic **pricing engine** implementing the standard calculation order: base → discount → tax → other charges → round-off → final payable (PHASE_01 §6.9.4). It covers duplicate prevention, bundle overlap, dependencies, proration, upgrades and downgrades.

The engine's output becomes a `tbl_billing_order` (PHASE_07). This phase provides the calculation and the catalog. Orders and payments belong to PHASE_07.

## 2. Existing implementation relevant to this phase

| Component | Location | Notes (`CODE-INSPECTED`) |
|---|---|---|
| `Plan`, `PlanVersion`, `PlanEntitlement` | `CONTROL-PANEL-SERVICE/.../platform/model/`, changelogs `changelog-tbl_plan*.xml` (old generated model, not run; retired in PHASE_11A) | `tbl_plan_version`: DRAFT/ACTIVE/RETIRED, `is_custom`, `effective_from/to`, UQ (`plan_id`, `version_number`). **No price columns.** No FKs. `tbl_plan_entitlement` key/value (`module.<id>`, `max_organizations`). No service or controller; orphaned DTOs `CreatePlanRequest`, `PlanVersionRequest`, `PlanResponse`. |
| Discount, tax, pricing | — | None in the control plane |
| Patterns in ACCOUNT-BOOK (reference) | `accounting/service/InvoiceService.java`, `PriceList`, `TaxMaster`, `GstTreatment`, `IndianState` | GST split, HALF_UP rounding, price-list rounding types, GST state codes |

## 3. Existing completed work

| Item | Status |
|---|---|
| Plan and plan-version structure with immutability semantics and contract flag | PARTIALLY_COMPLETED (reused; prices and FKs missing) |
| Bundles, prices, discounts, tax, engine | PENDING |

## 4. Reusable components

- `tbl_plan` / `tbl_plan_version` and their entities and repositories (modified).
- The PLAN_STATUS enum.
- The platform permissions (PHASE_04).
- The audit service.
- The ACCOUNT-BOOK GST and rounding patterns (re-implemented, with rates as `BigDecimal`, not `Double`).

## 5. Work requiring modification or redesign

| Item | Classification | Change |
|---|---|---|
| `tbl_plan` | Modify existing | Add `plan_type`, `module_id` / `bundle_id`, `is_public`, `status`; drop the soft-delete triplet |
| `tbl_plan_version` | Modify existing | FK to plan, `custom_company_id`, `published_at/by`; enforce immutability |
| `tbl_plan_entitlement` | Remove | Entitlements derive from the plan's module or bundle |
| Plan DTOs | Replace | New plan, version and price APIs |
| v2 design: price books, user and storage pricing rules, quotes | Replaced in v3 | By plan versions with `tbl_plan_price`; USER_SEAT and STORAGE plans; billing orders |

## 6. New development required

### 6.1 Bundles
- `Bundle`, `BundleModule`, and `BundleService`: DRAFT → ACTIVE (content immutable) → RETIRED.
- Validation:
  - the mandatory-dependency closure is inside the bundle, or each missing dependency is `INCLUDED` or `CHARGED`;
  - identical bundles raise a warning.

### 6.2 Plans, versions, prices
- `PlanService`:
  - create a plan per module or bundle, plus one USER_SEAT plan and one STORAGE plan;
  - create a DRAFT version with prices per billing period and currency;
  - **publish**: validation, then immutable;
  - retire.
- **Contract versions:** `is_custom` + `custom_company_id`, used only for that company's orders.
- **Publish validation:**
  - each ACTIVE plan has prices for each offered period;
  - USER_SEAT defines the base included users, the unit price and the maximum;
  - STORAGE defines the base included GiB, the block size, the price and the maximum (storage quantities are GiB, 1 GiB = 2^30 bytes — P06-T5 T5-4, 2026-10-10; the `*_gb` field names are kept).

### 6.3 Discounts and coupons (FUTURE: implemented in PHASE_14)

R1 implements only the engine hook and the MANUAL adjustment (PHASE_07). The following is the future design:

- `DiscountService` (`platform.discount.manage`): targets, eligibility, validity, limits, priority, stackability.
- `CouponService`: codes stored upper-case; private codes per company; redemption counts.
- Reservation at order confirmation and application at payment (PHASE_07). Usage limits are enforced under a row lock on `tbl_discount` / `tbl_coupon`.

### 6.4 Tax rules (configurable; values PENDING Q-04/Q-29; CD-25)
- `TaxRuleService` and `BillingPolicyService`: versioned rules (`supply_type`, components and rates, SAC / plan type, cess), the billing policy (rounding mode and level, round-off unit, FY start), `effective_from` / `effective_to`, DRAFT → APPROVED → RETIRED with `approval_reference` (the CA's letter). Only the Platform Super Admin (`platform.tax.manage`).
- The engine selects the APPROVED rule valid on the order date. It derives intra-state (CGST + SGST) or inter-state (IGST) from the seller state (`tbl_seller_entity`, approved version) and the buyer place of supply (`tbl_billing_account.state_code`). B2B and B2C follow `gst_treatment`.
- Orders store snapshots of the rule version, rates and amounts. A new version never changes existing orders.
- **While the values are PENDING:**
  - dev and staging use a SAMPLE configuration (DRAFT, watermarked PDFs);
  - production quoting is blocked by the billing go-live gate until the configuration is APPROVED;
  - the former "tax not configured" override is withdrawn.

### 6.5 Pricing engine
`PricingEngine.calculate(request)` is pure and deterministic for the given plan versions, discounts and tax rules:
- **Inputs:** company or signup context, channel, billing period, currency, selected bundle/module plans, users, storage, coupon codes, and the current entitlements and subscription items.
- **Rules** (PHASE_01 §6.9.3–6.9.4):
  1. duplicates;
  2. redundant bundles;
  3. bundle overlap warning;
  4. transitive mandatory dependencies;
  5. users = base + contributions, then additional seats;
  6. storage = base + contributions, then blocks;
  7. line discounts;
  8. order discounts with proportional allocation;
  9. tax per line component;
  10. other charges (none);
  11. round-off;
  12. totals;
  13. proration for upgrades;
  14. scheduling for downgrades.
- **Output:** lines, discounts, taxes, warnings, totals and `calculation_version`. It is persisted as `tbl_billing_order` by PHASE_07.
- **Money:** `BigDecimal`, scale per currency, HALF_UP per line and component, largest-remainder allocation.

### 6.6 Illustrative scenarios as tests
- The master §12.3 scenarios S1–S7 are re-expressed with plan versions. The amounts are labelled ILLUSTRATIVE.
- New scenarios:
  - **S8:** a percentage coupon with a cap. **FUTURE (PHASE_14, CD-10):** in R1 S8 is tested only against the engine hook with a MANUAL adjustment; the coupon variant is added in PHASE_14.
  - **S9:** a fixed order discount allocated across lines with tax.
  - **S10:** the same plan bought monthly in one subscription and attempted yearly in another, rejected as `DUPLICATE_ENTITLEMENT`.
  - **S11:** a contract (`is_custom`) version for one company.
  - **S12:** a price change after the order; the old order is unchanged.

## 7. Exact services, files and database areas involved

| Area | Files |
|---|---|
| Control plane | `platform/model/Plan*.java` (modified), new `platform/catalog/bundle/*`, `platform/pricing/{plan,discount,coupon,tax,engine}/*`; controllers under `/api/v1/platform/admin/{bundles,plans,discounts,coupons,tax-rules,pricing/calculate}` |
| Removed | `PlanEntitlement*`, the orphaned plan DTOs (after replacement) |
| Database | `control_panel_db`: PHASE_01 §6.2 C (bundles) and D (`tbl_plan`, `tbl_plan_version`, `tbl_plan_price`, `tbl_tax_rule`, `tbl_seller_entity`, `tbl_billing_policy`). `tbl_discount`, `tbl_discount_target`, `tbl_coupon`, `tbl_discount_redemption` are **FUTURE (PHASE_14, CD-10)** and are not created in R1 (task 1). |

## 8. Proposed schema changes

As listed in §7 (full column definitions in PHASE_01 §6.2 C and D). FKs are added where the existing tables lack them (`tbl_plan_version.plan_id`).

## 9. API and integration requirements

| Endpoint | Permission |
|---|---|
| Bundles CRUD and lifecycle | `platform.catalog.*` |
| Plans and versions (create, price, publish, retire, contract version) | `platform.plan.manage` (Super Admin by default) |
| Discounts and coupons (**FUTURE, PHASE_14, CD-10**; R1 uses the MANUAL order adjustment of PHASE_07) | `platform.discount.manage` |
| Tax rules, billing policy | `platform.tax.manage` (Super Admin only; CD-25) |
| `POST /api/v1/platform/admin/pricing/calculate` | `platform.billing.read` (no persistence) |
| Public read of `is_public` plans and prices (PHASE_14 website) | Anonymous, cached |

## 10. Dependencies on other phases

| Phase | Relationship |
|---|---|
| PHASE_04 | Prerequisite: catalog |
| PHASE_06 | Consumes the plan items and dependency rules. Owns the removal of `tbl_plan_entitlement` and the orphaned plan DTOs (former task 7, moved to P06-T8 by owner decision F5-3, 2026-10-07); PHASE_05 does not depend on PHASE_06 |
| PHASE_07 | Persists the engine results as orders and invoices |

## 11. Security and tenant-isolation requirements

1. Only the Super Admin (or an explicit grant, Q-16) changes plans, prices, discounts and tax.
2. Published versions are immutable, and every change is audited.
3. The engine never trusts client prices. Contract versions apply only to their company.
4. Coupon brute-force is limited by rate limits on public endpoints (PHASE_14) and by generic error messages.

## 12. Ordered implementation tasks

| # | Task | Status |
|---|---|---|
| 1 | JPA entities and schema for the CURRENT tables (bundles, plan modifications, prices, tax rules, seller entity), generated by Hibernate during development; **no CP Liquibase changelog** (CD-27, final baseline in PHASE_11A). No discount or coupon tables in R1. | DONE |
| 2 | Bundles + validation | DONE |
| 3 | Plans, versions, prices, contract versions, publish validation | DONE |
| 4 | Engine hook for discounts (MANUAL now). Catalog discounts, coupons and redemptions move to PHASE_14 (FUTURE). | DONE (2026-10-08, owner-approved; discount SPI + MANUAL source + validation; the engine integration is P05-T5) |
| 5 | Pricing engine + scenario tests S1–S12 | DONE (2026-10-08, owner-approved; pure engine, input resolution, NEW-only calculate endpoint; orders / subscriptions are PHASE_06 / PHASE_07) |
| 6 | Configurable tax rules + billing policy (versioning, approval, effective dating) + GST component derivation + SAMPLE configuration + go-live gate | DONE (2026-10-08, owner-approved; the real tax values from the CA are entered later through the DRAFT → APPROVED workflow) |
| 7 | Remove `tbl_plan_entitlement` and the orphaned DTOs — **moved to PHASE_06 / P06-T8** (owner decision F5-3, 2026-10-07; not implemented in PHASE_05) | DEFERRED (2026-10-08, CD-33; target PHASE_06 / P06-T8) |

## 13. Acceptance criteria

1. The Super Admin defines plans, monthly and yearly prices, bundles, and user and storage pricing without code changes. (Catalog discounts are FUTURE, PHASE_14, CD-10; R1 discounts are the MANUAL order adjustment of PHASE_07.)
2. The engine reproduces every scenario. Duplicates are never charged. Dependencies are always present.
3. Published versions cannot be modified (409). A price change creates a new version, and existing orders and subscriptions are unchanged.
4. **FUTURE (PHASE_14, CD-10):** discount limits hold under concurrency (8 parallel redemptions for a limit of 5 → 5 applied). Not an R1 acceptance criterion; moved with the catalog discounts.
5. The calculation follows PHASE_01 §6.9.4 exactly (property tests: totals equal the sum of the lines; the allocations sum to the discount).

## 14. Testing and validation checklist

- [ ] Engine unit and property tests (rounding, allocation, proration boundaries, chains of dependencies).
- [ ] MySQL IT: constraints, publish immutability. (Concurrent redemption limits: FUTURE, PHASE_14, CD-10.)
- [ ] Permission tests.

## 15. Risks and rollback considerations

| Risk | Mitigation |
|---|---|
| Tax values not yet confirmed by the CA | Configurable design (CD-25); production quoting gate; values entered and approved later without a release |
| Discount abuse | Limits, eligibility, private coupons, audit |
| Rounding disputes | Documented order; golden tests; amounts frozen on the order |

**Rollback:** the catalog and pricing tables are additive. Nothing enforces entitlements until PHASE_06/07 use them.

## 16. Current status

| Item | Status |
|---|---|
| Plan and version structure | DONE (P05-T1 entities; P05-T3 plans, versions, prices, contract versions, publish validation) |
| Bundles | DONE (P05-T2) |
| Discounts | DONE for R1: discount SPI + MANUAL source (P05-T4); catalog discounts, coupons and redemptions FUTURE (PHASE_14) |
| Pricing engine | DONE (P05-T5: pure engine, scenarios S1–S12, NEW-only calculate endpoint) |
| Tax | DONE as configuration (P05-T6: versioned tax rules, seller entities, billing policies, approval, GST resolver, go-live gate, SAMPLE seed); the real values stay PENDING with the CA (Q-04, Q-29, Q-42; CD-25) |
| `tbl_plan_entitlement` removal | DEFERRED to PHASE_06 / P06-T8 (F5-3, CD-33) |
| **Phase** | **DONE** (2026-10-08, CD-33; 6 / 7 + 1 DEFERRED) |

*Earlier assessment (2026-10-03, before implementation): plan and version structure PARTIALLY_COMPLETED; prices, bundles, discounts, coupons and engine PENDING; tax PENDING (configurable design confirmed, values pending); phase PARTIALLY_COMPLETED.*
