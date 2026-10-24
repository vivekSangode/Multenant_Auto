# PHASE 07 — Billing: Orders, Invoices and Manual Payments (Admin Panel, CURRENT)

| Item | Value |
|---|---|
| Master architecture | [SAAS_ARCHITECTURE_MASTER.md](SAAS_ARCHITECTURE_MASTER.md) §12–§13 |
| Design source | [PHASE_01 §0 (CD-07), §1.1 (scope), §6.2 D (orders, manual adjustments), §6.2 I.1, §6.9.1–6.9.4, §6.9.7](PHASE_01_SAAS_FOUNDATION.md) |
| Release | **R1 (current)**: the admin-panel commercial flow used by every company created by the platform, including Shivam |
| Depends on | [PHASE_05](PHASE_05_PLANS_BUNDLES_PRICING_AND_DISCOUNTS.md) (plans, prices, tax rules, pricing engine), [PHASE_06](PHASE_06_SUBSCRIPTIONS_AND_ENTITLEMENTS.md) (subscriptions, periods, entitlement engine) |
| Enables | PHASE_08 (provisioning after activation), PHASE_12 (Shivam onboarding), PHASE_14 (future online payments reuse this core unchanged) |
| Status | **DONE / CLOSED 2026-10-14** (explicit owner declaration; all 12 tasks DONE; follow-ups and CA prerequisites stay open — tracker). *Earlier:* **UNLOCKED 2026-10-13 (CD-45)** — plan APPROVED. *Current 2026-10-14:* tasks 1–3 DONE; task 4 DONE (owner approval 2026-10-13 after CD-58..CD-62); task 11 DONE (owner approval 2026-10-13 after the live SMTP smoke test PASS and the confirmed TEST-mail delivery; implemented under CD-65 after CD-63 / CD-64); task 5 DONE (owner approval 2026-10-14 after CD-66 / CD-68 / CD-69 and the final verification); task 6 DONE (owner approval 2026-10-14 after CD-70 / CD-71 and the final independent review); tasks 7–10 and 12 PENDING. *At the unlock:* no task claimed. Earlier: **PENDING** (`CODE-INSPECTED`: `tbl_billing_account`, `tbl_invoice` and `tbl_payment_transaction` are unused stubs: no service, no FKs, non-unique `invoice_number`, no tax) |

> **Revision 2026-10-03 (v5, decision register):**
> - **CONFIRMED DECISION CD-07: full payment only.** A subscription is activated only when **100 %** of the order amount is confirmed.
> - The v4 design is **withdrawn**: partial payments, activation rules (FULL_PAYMENT / MINIMUM_PAYMENT / ADMIN_APPROVAL), credit terms, the PARTIALLY_PAID / OVERDUE invoice states, the allocation engine and activation on credit.
> - Renewal is **manual** and done by the Platform Super Admin.
> - **Final decision review:**
>   - **CD-20 (Q-45 closed):** several verified instruments may make up 100 %;
>   - **CD-21 (Q-31 closed):** no mandatory two-person rule; audit kept;
>   - **CD-22 (Q-07 closed):** 0-day grace.
> - **CD-23 (owner decision):** all platform emails of this phase (proforma, payment receipt, tax invoice, activation, renewal reminders, expiry) are sent in **R1** from the **single static platform mailbox** (§6.10).
> - **Q-40 decided (CD-24):** provisioning starts by an explicit platform action after the full verified payment.
> - **CD-25 (owner instruction):** **Q-42** (tax invoice timing), **Q-29** (seller entity data) and the Q-04 tax part stay **PENDING (values only)**.
>   - They are implemented as dynamic, versioned configuration (`tbl_billing_policy`, `tbl_seller_entity`, `tbl_tax_rule`, `tbl_document_series`; PHASE_01 §6.9.8).
>   - **Both** invoice-timing paths are built.
>   - The CA's answer is entered and approved by the Platform Super Admin **without a code change**.
>   - Development continues; only production quoting waits for the APPROVED configuration.
> - **FUTURE CAPABILITY:** `tbl_payment_allocation`, online payments (PHASE_14), refunds (Q-33).

---

## 1. Objective

Implement the current admin-panel commercial flow (PHASE_01 §1.1, §6.9.2):

1. Platform Super Admin → create company (DRAFT) → billing account.
2. Select plans and modules (monthly or yearly) → DRAFT order → QUOTED (amounts frozen; the proforma PDF is generated in T9 — CD-58 D17, CD-66 Q3) → confirmed → **AWAITING_PAYMENT**. *(Reconciled 2026-10-13 by CD-66 Q3; was: "QUOTED (amounts frozen, proforma PDF)".)* The subscription is created in PENDING with its items and period 1.
3. The Super Admin records the **full** payment: CASH, CHEQUE, DEMAND_DRAFT, UPI, BANK_TRANSFER or OTHER (approved), with the instrument reference and evidence.
4. **Verification** for non-immediate methods:
   - cheques and DDs stay PENDING_VERIFICATION until cleared;
   - UPI and bank transfers are verified against the bank credit before confirmation;
   - a bounce or failed verification makes the payment REJECTED, and the order returns to AWAITING_PAYMENT.
5. On full confirmation (**PAID**):
   1. the tax invoice is ISSUED_PAID (`balance_due` = 0): it is **created** now (policy ON_FULL_PAYMENT) or, if it was created unpaid at confirmation (policy ON_ORDER_CONFIRMATION), it **turns** ISSUED_PAID now (Q-42, CD-25);
   2. the subscription becomes ACTIVE (PHASE_06);
   3. the entitlements are computed;
   4. the order becomes FULFILLED;
   5. the company is ready for provisioning (PHASE_08).
6. **Manual renewal:** the Super Admin creates a RENEWAL order for an expiring subscription. The same full-payment flow applies.
6a. **Upgrade / downgrade (added 2026-10-12, CD-43 item 11, P07-T12):** the Super Admin creates an UPGRADE order (effective immediately after the full confirmed payment, prorated for the remaining period) or a DOWNGRADE order (effective at the period end). The same full-payment flow applies; details are decided at the P07-T12 readiness.
7. Credit notes for corrections and a complete payment history.

**Out of scope (FUTURE):**
- gateway payments, payment attempts, refunds, webhooks and reconciliation;
- payment allocations;
- catalog discounts and coupons;
- the financial hash-chain ledger;
- website orders and automatic renewal (PHASE_14).

## 2. Existing implementation relevant to this phase

| Component | Location | Notes (`CODE-INSPECTED`) |
|---|---|---|
| `BillingAccount`, `Invoice`, `PaymentTransaction`, `SubscriptionAddon`, `InvoiceStatus`, `PaymentStatus` | `CONTROL-PANEL-SERVICE/.../platform/model/`, `platform/enums/`, changelogs `changelog-tbl_{billing_account,invoice,payment_transaction}.xml` (old generated model, not run; retired in PHASE_11A) | Unused stubs; `PaymentTransaction` has gateway-shaped columns (`external_payment_id`, `webhook_event_id`) that move to FUTURE tables |
| `PlatformSubscriptionService` | `platform/service/` | Locked, audited transitions; the hook for activation |
| ACCOUNT-BOOK patterns (reference only) | `accounting/service/InvoiceService.java` (GST split, void and edit rules), `PaymentMode`, `CreditNote`, `LedgerCodeSequenceService` (atomic counter) | Re-implemented in the CP; no shared DB |
| Outbox and backoff, platform audit | CP `IdentitySyncWorker`, `PlatformAuditService` | Reused |

## 3. Existing completed work

None for SaaS billing. There is no payment gateway code anywhere in the repository. The patterns above are reused.

## 4. Reusable components

The atomic sequence pattern, the GST split pattern, the credit-note reasons, the `PaymentMode` enum idea, the subscription transition service, the platform audit.

## 5. Work requiring modification or redesign

| Item | Classification | Change |
|---|---|---|
| `tbl_billing_account` | Modify existing stub | GST identity, `company_id` FK, own status |
| `tbl_invoice` | Modify existing stub | Unique series number, snapshots, full amount breakdown, `amount_paid`, `amount_credited`, `balance_due`; statuses ISSUED_UNPAID (only with policy ON_ORDER_CONFIRMATION), ISSUED_PAID, CREDITED, VOID (**no partial or overdue state; activation depends only on 100 % verified payment**); references to the seller-entity and billing-policy versions; lines, tax lines, hash |
| `tbl_payment_transaction` | Replace | Neutral `tbl_payment` + `tbl_manual_payment` + `tbl_payment_status_history` (the gateway columns go to FUTURE tables) |
| `tbl_subscription_addon` | Remove | Subscription items (PHASE_06) |

## 6. New development required

1. **Document series** (atomic, per FY April–March, configurable `format_pattern`; CD-48 / CD-49): INVOICE `<code>-I/{FYS}/{SEQ:5}` and CREDIT_NOTE `<code>-C/{FYS}/{SEQ:5}` per seller entity (`invoice_series_code` exactly two uppercase alphanumeric characters and not used by another seller entity, otherwise issuance fails closed with 409 `INVOICE_SERIES_CODE_INVALID` / `INVOICE_SERIES_CODE_DUPLICATE` — CD-50 / CD-51; ≤ 16 characters, e.g. `AB-I/26-27/00001`), ORDER `ORD/{FY}/{SEQ:5}`, PAYMENT `PAY/{FY}/{SEQ:5}`, RECEIPT `RCT/{FY}/{SEQ:5}` (one global platform-wide series per type, CD-50); SUBSCRIPTION stays with PHASE_06. The versioned seller entity and billing policy are PHASE_05 configuration that PHASE_07 reads (CD-28); `EInvoiceProviderSpi` (NONE now; issuance refused with 409 `EINVOICE_PROVIDER_UNAVAILABLE` when e-invoicing applies without a provider) (Q-29, CD-25).
2. **Billing account** CRUD (GSTIN/PAN validation, state code).
3. **Order service** (on the PHASE_05 engine).
   - Status flow: DRAFT → QUOTED (frozen; proforma PDF generation deferred to T9 — CD-58 D17, CD-66 Q3) → AWAITING_PAYMENT → PAID → FULFILLED; or CANCELLED / EXPIRED. *(Reconciled 2026-10-13 by CD-66 Q3; was: "QUOTED (frozen, proforma PDF)".)*
   - `payment_status`: UNPAID → PENDING_VERIFICATION → PAID.
   - **Manual adjustment** (PERCENT or FIXED, with a reason; audited; no mandatory second approver, CD-21).
   - Idempotency key. There is no activation rule, no minimum amount and no credit term.
4. **Confirmation transaction** (QUOTED → AWAITING_PAYMENT):
   1. the subscription is created in PENDING with its items and period 1 (AWAITING_PAYMENT);
   2. audit.
   - Per the APPROVED billing policy (Q-42, CD-25):
     - **ON_FULL_PAYMENT:** no tax invoice yet; the order PDF is the proforma.
     - **ON_ORDER_CONFIRMATION:** the tax invoice is issued now as ISSUED_UNPAID (`balance_due` = `grand_total`); if the order is later cancelled or expires, a credit note cancels it.
5. **Full manual payment.**
   - `record` (`platform.payment.record`):
     - mode CASH, CHEQUE, DEMAND_DRAFT, UPI, BANK_TRANSFER or OTHER (approved);
     - one instrument for the full amount, **or several instruments** (e.g. cash ₹40,000 + UPI ₹60,000 for ₹100,000) (CD-20);
     - a payment may not exceed the order's outstanding amount (`grand_total` − recorded non-rejected payments);
     - instrument reference and date, bank, received-at, received-by, evidence, receipt number;
     - status RECORDED, or PENDING_VERIFICATION when `verification_required`.
   - `confirm` / `verify` / `reject` (`platform.payment.confirm`):
     - immediate methods (cash, a verified UPI or transfer credit) → CONFIRMED;
     - a cheque or DD → CONFIRMED only after clearance;
     - a bounce or failed verification → REJECTED, and the order goes back to AWAITING_PAYMENT.
     - No mandatory two-person rule (CD-21): an authorized platform user (Super Admin, or a Platform Admin with `platform.payment.confirm`) may both record and verify, with full history and audit.
   - **Activation condition:** the sum of **CONFIRMED** payments = `grand_total` (`confirmed_amount >= grand_total`). RECORDED or PENDING_VERIFICATION amounts never count. Until then the order stays AWAITING_PAYMENT (`payment_status` UNPAID or PENDING_VERIFICATION) with the stored `confirmed_amount` and `outstanding_amount`. There is no partial state and no partial activation.
   - A CONFIRMED payment found wrong later → REVERSED (with a reason), plus a credit note and the PHASE_06 suspension policy. Nothing is deleted.
   - *Note 2026-10-14 (CD-70; text above kept as approved):* `confirmed_amount` / `outstanding_amount` are computed from the payment rows under the order row lock, not stored (Q2); the record cap counts RECORDED, PENDING_VERIFICATION and CONFIRMED — "non-rejected" = neither REJECTED nor REVERSED (Q7); P07-T6 reverses only while the order is AWAITING_PAYMENT, the credit note / suspension part after activation is decided at the P07-T7 / P07-T9 readiness (Q3); order PAID / FULFILLED and activation stay P07-T7 (Q1).
   - Every transition writes `tbl_payment_status_history` and the platform audit.
6. **Activation** (inside one locked transaction, when the confirmed amount = `grand_total`):
   1. order PAID;
   2. invoice ISSUED (copies of the lines and taxes, seller and buyer snapshots, `content_sha256`, `amount_paid` = `grand_total`, `balance_due` = 0);
   3. subscription ACTIVE (PHASE_06) and entitlements recomputed;
   4. order FULFILLED;
   5. the company is ready for provisioning, started by the platform action "Start provisioning" (Q-40 decided, CD-24);
   6. platform email: payment receipt, tax invoice and activation notice to the billing contact (CD-23).
7. **Manual renewal** (`platform.subscription.renew`):
   - the PHASE_06 scheduler only **flags** subscriptions that are about to expire, on the dashboard and by platform email (7 days and 1 day before, CD-23);
   - the Super Admin creates the RENEWAL order, records and confirms the full payment;
   - on PAID, the next `tbl_subscription_period` starts.
7a. **Upgrade / downgrade orders** (P07-T12, CD-43 item 11): UPGRADE → PHASE_06 `applyUpgrade` (immediate, prorated); DOWNGRADE → PHASE_06 `scheduleDowngrade` (period end); same order, payment and activation rules as NEW orders.
8. **Credit notes** (`platform.credit-note.manage`): correction or cancellation of issued invoices (`amount_credited`). VOID is allowed only before any confirmed payment. Refunds of money are FUTURE (Q-33).
9. **APIs.**

    | Endpoint | Permission |
    |---|---|
    | `/api/v1/platform/admin/billing-accounts` | `platform.billing.*` |
    | `/api/v1/platform/admin/orders` (calculate, create, adjust, quote, confirm, cancel) | `platform.billing.*`, `platform.order.discount` |
    | `/api/v1/platform/admin/orders/{id}/renewal` | `platform.subscription.renew` |
    | `/api/v1/platform/admin/payments/manual` (record, confirm, verify, reject, reverse) | `platform.payment.record`, `platform.payment.confirm` |
    | `/api/v1/platform/admin/invoices` (read, PDF, void) | `platform.billing.*` |
    | `/api/v1/platform/admin/credit-notes` | `platform.credit-note.manage` |
    | `/control-panel/api/v1/company/billing/{orders,invoices,payments}` (read-only) | `company.billing.read` |

### 6.10 Platform email (CURRENT, R1; CONFIRMED DECISION CD-23)

- **One single static platform mailbox** (e.g. `no-reply@<platform domain>`, owner-defined), SMTP with an **app password** from the environment or secret store. Configuration names (owner decision CD-45, 2026-10-13): `erp.platform.mail.from` → `ERP_PLATFORM_MAIL_FROM`, `erp.platform.mail.host` → `ERP_PLATFORM_MAIL_HOST`, `erp.platform.mail.port` → `ERP_PLATFORM_MAIL_PORT`, `erp.platform.mail.username` → `ERP_PLATFORM_MAIL_USERNAME`, `erp.platform.mail.password` → `ERP_PLATFORM_MAIL_PASSWORD` (secret — its value only in `.env` / the secret store, never in documentation, logs or source control). It is never stored in the DB in plain text, in code or in logs.
- A small `PlatformMailSender` in the Control Plane with platform templates. Every message is logged in `tbl_platform_email_message` / `_recipient` / `_attempt`, with retry and backoff.
- **Purposes (platform only):**
  - order and proforma;
  - payment receipt;
  - tax invoice;
  - subscription activation;
  - renewal reminders (7 days and 1 day);
  - expiry notice;
  - company welcome;
  - Company Super Admin invitation notice;
  - provisioning completed;
  - storage warnings (80 % and 95 %);
  - security notices.
- **Recipients:** the company's billing contact and the Company Super Admin. Platform emails contain no company business data.
- The same platform mailbox is the Keycloak identity-email sender (CD-19).
- It **never** sends company business email (quotations, invoices of ACCOUNT-BOOK, HR letters, …). Those use only the user's own mailbox (PHASE_13).
- Permission: `platform.email.manage` (configuration view, test send, log).

### 6.11 Extensibility for future digital payments (FUTURE CAPABILITY)

- `tbl_payment` is channel-neutral (`payment_channel` MANUAL now; GATEWAY later).
- Gateway, attempt, webhook and refund details live in FUTURE child tables (PHASE_14).
- The rule stays the same: activation only when the **full** amount is captured and verified.
- `tbl_payment_allocation` is added only if a future requirement needs one payment to settle several invoices.

## 7. Exact services, files and database areas involved

| Area | Files |
|---|---|
| Control plane | New `platform/billing/{account,order,invoice,payment,creditnote,series,activation,renewal}`; new `platform/email/PlatformMailSender` + templates + `tbl_platform_email_message*` (CD-23); `platform/service/PlatformSubscriptionService.java` (activation hook); removal of the old stubs after replacement |
| Database | `control_panel_db`: PHASE_01 §6.2 D (`tbl_billing_order*`, `tbl_document_series`, `tbl_seller_entity`) and I.1 (`tbl_billing_account`, `tbl_invoice*`, `tbl_credit_note*`, `tbl_payment`, `tbl_manual_payment`, `tbl_payment_status_history`) |
| Storage | Platform bucket (invoice and proforma PDFs, payment evidence) |

## 8. Proposed schema changes

Only the `[CURRENT]` tables listed in §7. **No `[FUTURE]` table is created in this phase** (including `tbl_payment_allocation`). The CURRENT tables already contain the neutral columns the future tables attach to (`tbl_payment.payment_channel`, the nullable `tbl_billing_order.signup_request_id`, `tbl_billing_order_discount.discount_id` / `coupon_id`).

## 9. API and integration requirements

See §6, item 9. Errors:

| Status | Code |
|---|---|
| 409 | `ORDER_NOT_CONFIRMABLE` |
| 409 | `ORDER_EXPIRED` |
| 422 | `PAYMENT_EXCEEDS_OUTSTANDING` (the amount > the outstanding amount) |
| 409 | `PAYMENT_NOT_VERIFIED` |
| 409 | `INVOICE_IMMUTABLE` |

## 10. Dependencies on other phases

| Phase | Relationship |
|---|---|
| PHASE_05 / PHASE_06 | Prerequisites |
| PHASE_08 | Provisioning after activation |
| PHASE_12 | Shivam onboarding (O-5) |
| PHASE_13 | Billing emails (future) |
| PHASE_14 | Online payments reuse the same order, invoice and payment core |

## 11. Security and tenant-isolation requirements

1. Company billing APIs are read-only and filtered by the verified tenant.
2. Payment recording, confirmation, renewal and manual adjustments require platform permissions (Super Admin by default). There is no mandatory two-person rule (CD-21); a maker-checker policy may be added later if the owner chooses it.
3. Immutable financial rows: no DELETE grant. Only status fields change, inside locked transactions, with history.
4. No card, bank password or UPI-PIN data in any field. Evidence files are private.
5. Server-side amounts only. Client totals are ignored. Activation is impossible unless the confirmed amount equals `grand_total`.

## 12. Ordered implementation tasks

| # | Task | Status |
|---|---|---|
| 1 | JPA entities and schema for the CURRENT billing tables — core order / invoice / payment / history tables and the E6-5 FKs; credit notes → task 9, platform email → task 11 (CD-46) (Hibernate during development; **no CP Liquibase changelog**, CD-27; final baseline in PHASE_11A) | DONE (2026-10-13, owner approval after the final independent review; correction C1–C6, CD-48) |
| 2 | Document series (CD-48 / CD-49 formats) + `EInvoiceProviderSpi` (NONE); reads the PHASE_05 seller entity / billing policy | DONE (2026-10-13 — owner approval after the correction round CD-53; values from the CA later) |
| 3 | Billing account service and APIs | DONE (2026-10-13 — owner approval after the correction round CD-57; follow-up notes in the tracker row) |
| 4 | Order service (draft, adjust, quote, confirm, cancel, expire) | DONE (2026-10-13 — owner approval after CD-58..CD-62; the confirm endpoint is task 5) |
| 5 | Confirmation: AWAITING_PAYMENT + PENDING subscription (proforma PDF generation deferred to T9 — CD-58 D17, CD-66 Q3; title reconciled 2026-10-13, was: "…; proforma PDF") | DONE (2026-10-14 — owner approval after CD-66 / CD-68 / CD-69 and the final verification; was REVIEW) |
| 6 | Manual payments (one or several instruments): record, verification, confirmation, rejection, reversal, status history; activation at 100 % verified | DONE (2026-10-14 — owner approval after CD-70 / CD-71 and the final independent review; activation stays task 7, CD-70 Q1; was REVIEW) |
| 7 | Activation: invoice issue (both policy paths), subscription ACTIVE, entitlements, FULFILLED | DONE (2026-10-14 — owner approval; tracker) |
| 8 | Manual renewal order flow | DONE (2026-10-14 — owner approval, CD-77; tracker) |
| 9 | Credit notes (incl. the `tbl_credit_note*` entities, CD-46); invoice and proforma PDFs | DONE (2026-10-14 — owner approval, CD-80; CA validation pending; tracker) |
| 10 | Company read APIs; remove the old stubs | DONE (2026-10-14 — owner approval; tracker) |
| 11 | Platform mail sender (single static platform mailbox, templates, log, retry; incl. the `tbl_platform_email_*` entities, CD-46) | DONE (2026-10-13 — owner approval; implemented under CD-65 after CD-63 / CD-64; mandatory live SMTP smoke test PASS (SMTP 250), TEST-mail delivery confirmed by the owner) |
| 12 | Upgrade / downgrade orders (added 2026-10-12, CD-43 item 11) | DONE (2026-10-14 — owner approval, CD-81; tracker) |

## 13. Acceptance criteria

1. **S-P1 (full payment):** an order with total 1000 (illustrative) is confirmed → AWAITING_PAYMENT.
   1. Cash 400 is recorded and confirmed: `confirmed_amount` 400, outstanding 600, subscription still PENDING, no tax invoice. Recording 700 more is rejected (`PAYMENT_EXCEEDS_OUTSTANDING`).
   2. A cheque of 600 is recorded: PENDING_VERIFICATION. It does not count; the subscription stays PENDING and no tax invoice exists.
   3. The cheque is cleared: verified total 1000 = `grand_total` → order PAID, invoice ISSUED with `balance_due` 0 (timing per Q-42), subscription ACTIVE, order FULFILLED (CD-20).
   4. A second order paid by a single cash payment of the full amount activates in the same way.
2. A bounced cheque makes the payment REJECTED and the order AWAITING_PAYMENT, with history. Nothing is deleted. The subscription never became ACTIVE.
3. A UPI payment recorded without bank verification does not activate the subscription.
4. A manual renewal order with confirmed full payment starts the next period. Without it, the subscription becomes EXPIRED at `end_date` (0-day grace, CD-22).
5. Changing a plan price leaves existing orders, invoices and items unchanged.
6. An issued invoice cannot be edited. Corrections go through credit notes.
7. **PHASE_06 carry-over (CD-43 item 1):** PHASE_06 §13 acceptance 3 (renewal-order pricing with the current published or contract version, old items unchanged) and 4 (reinstatement only after the full confirmed payment; a subscription order that is not fully paid never activates) are proven end-to-end in P07-T7 / P07-T8.
8. **Upgrade / downgrade (P07-T12):** an UPGRADE takes effect only after the full confirmed payment; a DOWNGRADE takes effect at the period end (details at the P07-T12 readiness).

## 14. Testing and validation checklist

- [ ] Unit: order states, full-amount validation, verification states, activation conditions, series formatting.
- [ ] MySQL IT: atomic series, concurrent confirmation of the same order (exactly one activation), bounce handling, immutability (no update or delete paths).
- [ ] Permission matrix (platform and company).
- [ ] Golden PDF hashes.

## 15. Risks and rollback considerations

| Risk | Mitigation |
|---|---|
| GST invoicing rules (timing, IRN, SAC) not yet confirmed | Configurable design (CD-25); production quoting gate until the CA-approved configuration exists; both timing paths tested |
| Activation on an unverified payment | Verification states; activation only on CONFIRMED payments that equal `grand_total` |
| Manual entry mistakes | Evidence, status history, platform audit, reversal, credit notes; a maker-checker policy can be added later (CD-21) |

**Rollback:** new tables (replacing unused stubs). Disabling activation leaves subscriptions PENDING. Nothing is deleted.

## 16. Current status

| Item | Status |
|---|---|
| PHASE_07 tasks (2026-10-14) | Tasks 1–3 DONE; task 4 DONE (owner approval 2026-10-13 after CD-58..CD-62); task 11 DONE (owner approval 2026-10-13 after the live SMTP smoke test PASS and the confirmed TEST-mail delivery; implemented under CD-65 after CD-63 / CD-64); task 5 DONE (owner approval 2026-10-14 after CD-66 / CD-68 / CD-69 and the final verification); task 6 DONE (owner approval 2026-10-14 after CD-70 / CD-71 and the final independent review); tasks 7–10 and 12 PENDING — authoritative: the tracker |
| Billing stubs (unused) | Exist; to be modified or replaced |
| Orders, invoices, full manual payments, verification, activation, manual renewal | PENDING |
| Tax and seller entity | PENDING: configurable design confirmed (CD-25); values PENDING (Q-42, Q-29, Q-04) |
| **Phase** | **UNLOCKED** (2026-10-13, CD-45; tasks PENDING — every task: read-only readiness review → separate explicit implementation authorization → REVIEW → owner-approved DONE). Earlier: **LOCKED** (tracker; 2026-10-12: PHASE_05 and PHASE_06 DONE; unlock only by a separate written owner decision after the CD-43 item 13 gate (PHASE_06 DONE ✓, S0-2 item 2 decided ✓, the `PlanVersionService.create` lock-inversion follow-up DONE ✓ (P06-T3-F1, 2026-10-13), the PHASE_07 plan approved, the mailbox configuration key names fixed)) |
