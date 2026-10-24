# PHASE 14 — Online Payments and Public Website Onboarding

| Item | Value |
|---|---|
| Master architecture | [SAAS_ARCHITECTURE_MASTER.md](SAAS_ARCHITECTURE_MASTER.md) §12–§13 |
| Design source | [PHASE_01 §6.2 I (gateway tables), §6.2 K, §6.9](PHASE_01_SAAS_FOUNDATION.md) |
| Depends on | [PHASE_07](PHASE_07_BILLING_ORDERS_INVOICES_AND_MANUAL_PAYMENTS.md) (billing core), the R1 platform mailbox of [PHASE_07](PHASE_07_BILLING_ORDERS_INVOICES_AND_MANUAL_PAYMENTS.md) (signup verification and receipts, CD-23), [PHASE_12](PHASE_12_TESTING_ROLLOUT_AND_FIRST_COMPANY_ONBOARDING.md) (first release live; gate G9 is separate) |
| Enables | Self-service company purchase |
| Status | **PENDING** (`CODE-INSPECTED`: no gateway SDK, configuration or adapter; no public signup, captcha or registration code; the gateway allows anonymous access only to two specific public paths) |

> **Revision 2026-10-03 (v5, decision register):**
> - **CD-07 applies to online payments too:** a subscription is activated only when the **full** order amount is captured and verified by the webhook. Partial captures or partial payments never activate anything.
> - **CD-06:** website signup creates the company through the standard flow, including its **own Keycloak realm** (PHASE_08 `CREATE_REALM`). The Company Super Admin is invited into that realm.
> - **CD-04:** no public or company flow can create a Platform Super Admin.
> - **FUTURE CAPABILITY:** automatic or online renewal. R1 renewal is manual (PHASE_07).

---

## 1. Objective

1. A provider-independent **online payment** capability: a gateway adapter SPI, with the initial adapter **Razorpay** (recommendation, Q-30).
2. Gateway orders and checkout, verified webhooks, refunds, payment links for the admin channel, and daily reconciliation.
3. **Public website onboarding**:
   1. signup with email verification and abuse protection;
   2. server-side pricing and checkout;
   3. payment;
   4. automatic creation of the company with the standard `CompanyService`;
   5. subscription, provisioning (company realm, databases and buckets per purchased module), and a Company Super Admin invitation into the company realm.

   It produces the same records as the admin channel.

## 2. Existing implementation relevant to this phase

| Item | Evidence (`CODE-INSPECTED`) |
|---|---|
| Payment gateway | None (no SDK in any `pom.xml` or `package.json`, no keys in `.env`). The only vendor mention is a RazorpayX comment in ACCOUNT-BOOK `VendorVerificationService`. |
| Webhook handling | None. The stub columns `webhook_event_id` / `external_payment_id` are unique but unused. |
| HMAC and constant-time comparison | ACCOUNT-BOOK `ShareTokenSigner`, CRM `QuotationPublicLinkSigner`, the internal-token filters (`MessageDigest.isEqual`) |
| AES-GCM template | API-GATEWAY `WebSocketTicketCodec` |
| Public endpoints | Gateway permit-all only for `/crm/api/v1/public/**` and `GET /account-book/api/public/invoices/**`. Redis rate limiter configured. |
| Signup and captcha | None |
| Billing core | PHASE_07 (orders, payments, invoices, fulfilment) |

## 3. Existing completed work

None for this phase. The reusable patterns are listed above.

## 4. Reusable components

- The PHASE_07 order, payment, fulfilment and invoice core.
- The HMAC verification utilities pattern.
- The secret-reference mechanism (`CredentialCipher`, built when first needed in PHASE_13 or this phase; PHASE_02 §6.3).
- The outbox and backoff worker pattern.
- The gateway rate limiter.
- `CompanyService` (PHASE_02/04).
- The provisioning worker (PHASE_08).
- Platform email (the single platform mailbox, PHASE_07 §6.10).

## 5. Work requiring modification or redesign

| Item | Change |
|---|---|
| Gateway `SecurityConfig` | Add permit-all, rate-limited routes for `/control-panel/api/v1/public/{signup,checkout,payments/webhooks,mail/oauth/callback}/**`. Keep the internal deny. |
| Payment model | The CURRENT neutral `tbl_payment` (PHASE_07) stays unchanged. Online details go into the FUTURE 1:1 `tbl_gateway_payment` (`provider_account_id` + `provider_payment_ref` unique), plus attempts, refunds, webhooks and reconciliation (PHASE_01 §6.2 I.2). |
| Rate limiter fail-open | Public signup and checkout paths must **fail closed** (PHASE_03) |

## 6. New development required

**Catalog discounts and coupons (moved here in v4):** `tbl_discount`, `tbl_discount_target`, `tbl_coupon`, `tbl_discount_redemption` (PHASE_01 §6.2 D, FUTURE) attach to the CURRENT `tbl_billing_order_discount` (`discount_source` = CATALOG). Redemptions are RESERVED at checkout, APPLIED on capture, RELEASED on expiry. The PHASE_05 engine gains catalog-discount evaluation (eligibility, limits, stacking; Q-34).

**Financial hash-chain ledger:** `tbl_financial_audit_ledger` is introduced in this phase, before any public payment goes live.

1. **Gateway SPI and Razorpay adapter.**
   - `PaymentGatewayAdapter` (PHASE_01 §6.9.5).
   - The Razorpay adapter: orders, checkout signature verification, webhook HMAC verification, payment fetch, refunds, payment links, settlements report.
   - TEST and LIVE accounts in `tbl_payment_provider_account`, with encrypted secrets.
2. **Webhook pipeline.**
   1. Endpoint per provider and account.
   2. Verify the signature.
   3. Persist `tbl_payment_webhook_event` (dedup), then return 200.
   4. A worker applies idempotent state transitions (CAPTURED for the **full** `grand_total` → order PAID → PHASE_07 fulfilment; FAILED; REFUNDED). A captured amount below `grand_total` never marks the order PAID.
   5. Amount and currency mismatch: alert and block.
3. **Online checkout** (website and admin payment links):
   - the gateway order is created from `tbl_billing_order`;
   - the client receives only the public key id and the order ref;
   - the client callback signature is verified, but **the webhook is the authority for capture**.
4. **Refunds.** Requested and approved (maker-checker), executed through the adapter, confirmed by webhook, with credit note linkage.
5. **Reconciliation.** A daily settlement import; `tbl_payment_reconciliation_*`; a mismatch dashboard and alerts.
6. **Public signup.**
   - `tbl_signup_request` with a random public ref.
   - Email verification code through the platform mail account.
   - Captcha (provider decision Q-35).
   - Per-IP and per-email rate limits, disposable-domain checks, and an expiry for abandoned signups.
   - **No company is created before the payment is captured** (Q-35 may add free trials).
7. **Website fulfilment.** Order PAID, then:
   1. create the company (`onboarding_type` NEW) from the signup data;
   2. create the billing account;
   3. issue the invoice and activate the subscription (PHASE_07);
   4. provision (PHASE_08);
   5. invite the Company Super Admin (the signup email) through the identity outbox.
8. **Public website** (frontend, a separate deliverable; Q-15): plan catalog from public `tbl_plan` rows (`is_public`), price display from the active plan versions, checkout UI.

## 7. Exact services, files and database areas involved

| Area | Files |
|---|---|
| CONTROL-PANEL | New `platform/billing/gateway/*` (SPI, Razorpay adapter), `platform/billing/webhook/*`, `platform/billing/reconciliation/*`, `platform/signup/*`, public controllers |
| API-GATEWAY | `configs/SecurityConfig.java` (public routes), rate-limit configuration |
| Database | `control_panel_db`: `tbl_payment_provider_account`, `tbl_gateway_customer`, `tbl_payment_webhook_event`, `tbl_payment_reconciliation_*`, `tbl_signup_request`, `tbl_signup_verification` |
| Frontend | The public website (new; outside the existing `erp-ui`) |

## 8. Proposed schema changes

All `[FUTURE]` tables of PHASE_01 §6.2 D (discounts and coupons), I.2 (gateway, attempts, refunds, webhooks, reconciliation, ledger) and K (signup), created by additive changesets. The only touch to a CURRENT table is the new FK `tbl_billing_order.signup_request_id` → `tbl_signup_request` (the column already exists).

## 9. API and integration requirements

| Endpoint | Notes |
|---|---|
| `POST /control-panel/api/v1/public/signup`, `POST …/signup/{ref}/verify` | Captcha, rate limits |
| `POST /control-panel/api/v1/public/checkout` (idempotency key), `POST …/checkout/{order}/confirm` | Server pricing; signature verification |
| `POST /control-panel/api/v1/public/payments/webhooks/{provider}/{account}` | Signature-verified; idempotent |
| `POST /api/v1/platform/admin/orders/{number}/payment-link` | Admin channel online payment |
| `POST /api/v1/platform/admin/refunds/{id}/execute` | Maker-checker |
| Razorpay APIs | Outbound HTTPS only; credentials from the encrypted provider account |

## 10. Dependencies on other phases

PHASE_07 (core), PHASE_13 (emails), PHASE_08 (provisioning), PHASE_03 (public route hardening). Released after the first-company go-live (gate G9 in PHASE_12).

## 11. Security and tenant-isolation requirements

1. No card or UPI credentials are ever touched by the platform. The gateway-hosted checkout keeps the platform at minimal PCI scope.
2. Webhooks: signature verification with constant-time comparison, deduplication, replay-safe, no trust in the browser callback for capture.
3. Secrets are encrypted and write-only. TEST and LIVE are strictly separated. LIVE credentials are entered by the owner only.
4. Public endpoints are rate-limited, use captcha and fail closed. Error responses do not reveal internal ids.
5. A company created from a signup belongs only to the verified signup email's Company Super Admin.
6. Personal data in signups is minimized. Abandoned signups expire and are anonymized (Q-39).

## 12. Ordered implementation tasks

| # | Task | Status |
|---|---|---|
| 1 | Owner: gateway merchant account (TEST, then LIVE), KYC, webhook configuration (Razorpay decided, CD-24) | PENDING (owner action) |
| 2 | Gateway SPI + Razorpay adapter on TEST | PENDING |
| 3 | Webhook pipeline + idempotent transitions | PENDING |
| 4 | Admin payment links | PENDING |
| 5 | Refund execution | PENDING |
| 6 | Reconciliation | PENDING |
| 7 | Public signup + verification + captcha + abuse controls (company created only after payment, no trial; Q-35 decided) | PENDING |
| 8 | Website checkout + fulfilment (company creation after capture) | PENDING |
| 9 | Public website frontend (separate deliverable; Q-15 decided) | PENDING |
| 10 | Gate G9 drills (webhook loss and replay, double callbacks, partial outages, refunds) | PENDING |

## 13. Acceptance criteria

1. On TEST, a website purchase creates exactly one company, subscription, invoice and provisioning job, even with duplicated webhooks and double-clicked checkouts.
2. A tampered webhook signature is stored as invalid and never processed.
3. An abandoned checkout expires. No company is created and discount reservations are released.
4. The reconciliation report matches 100 % of TEST payments and flags injected mismatches.
5. A refund updates the payment status only after the provider confirms it, and links a credit note.

## 14. Testing and validation checklist

- [ ] Adapter contract tests against recorded provider fixtures.
- [ ] Webhook idempotency and ordering tests (captured before authorized, duplicates, late failures).
- [ ] Security tests: signature tampering, replay, rate limits, captcha bypass attempts.
- [ ] End-to-end on TEST with sandbox credentials.

## 15. Risks and rollback considerations

| Risk | Mitigation |
|---|---|
| Gateway outage | Orders stay AWAITING_PAYMENT; customers retry; the admin channel is unaffected |
| Missed webhooks | Polling fallback (`fetchPayment`) for orders stuck in PENDING; reconciliation |
| Fraudulent signups | Captcha, rate limits, payment before company creation |
| Vendor lock-in | SPI and neutral core schema; the second adapter (e.g. Cashfree) needs no schema change |

**Rollback:** disable the public routes and the gateway account (status DISABLED). Existing companies and subscriptions are unaffected. The admin channel continues.

## 16. Current status

| Item | Status |
|---|---|
| Gateway integration | PENDING (none exists) |
| Merchant account | PENDING (owner action) |
| Public signup and website | PENDING (FUTURE) |
| **Phase** | **PENDING** |
