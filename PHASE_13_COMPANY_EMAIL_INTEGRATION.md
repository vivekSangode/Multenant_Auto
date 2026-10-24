# PHASE 13 — User Mailbox Email Integration (FUTURE)

| Item | Value |
|---|---|
| Master architecture | [SAAS_ARCHITECTURE_MASTER.md](SAAS_ARCHITECTURE_MASTER.md) §15 |
| Design source | [PHASE_01 §0 (CD-08, CD-23), §6.2 J, §6.10, §6.11.3](PHASE_01_SAAS_FOUNDATION.md) |
| Release | **FUTURE (after R1).** Until it is done, further companies are onboarded with **business email disabled** (Q-41, decided). Shivam keeps the interim static sender. **Platform email is not part of this phase:** it exists from R1 (PHASE_07). |
| Depends on | [PHASE_12](PHASE_12_TESTING_ROLLOUT_AND_FIRST_COMPANY_ONBOARDING.md) (R1 live), [PHASE_10](PHASE_10_COMPANY_ADMIN_ROLES_AND_PERMISSION_ENFORCEMENT.md) (company users, permissions), [PHASE_11](PHASE_11_MICROSERVICE_INTEGRATION_AND_COMPATIBILITY.md) (branding adoption), [PHASE_02](PHASE_02_CP_SCHEMA_AND_TENANCY_IMPLEMENTATION.md) (the secret-reference mechanism is built **in this phase** when first needed; PHASE_02 §6.3) |
| Enables | Business email for every company; PHASE_14 |
| Decision status | **CONFIRMED (CD-23).** Business email only from the user's **own** OAuth-connected, verified mailbox. **No** company shared mailbox, **no** platform fallback, **no** generic SMTP. Without a connected, verified mailbox the business email is **not sent**. |
| Status | **PENDING** (`CODE-INSPECTED`: 6 services send through one global env-configured Gmail SMTP sender; no user connections, OAuth, credential storage or central dispatch) |

> **Owner decision (2026-10-03, CD-23; closes Q-51, Q-52, Q-44):**
> - **Two channels, never mixed:**
>   - **Platform email:** subscription, billing, invoices, renewal and expiry notices, onboarding, invitations and Keycloak identity emails, sent from **one single static platform mailbox** (SMTP + app password in the secret store). Delivered in **R1** (PHASE_07), not here.
>   - **Business email:** everything company users and company modules send, sent **only from the user's own mailbox**, connected by OAuth (Google, Microsoft 365, Zoho). The ERP **never** asks for or stores the mailbox password.
> - The connected mailbox must be **the user's own ERP account email**.
> - **If the user's mailbox is not connected and verified, the email is not sent.** The user sees "Connect your mailbox"; scheduled mail is recorded as BLOCKED_NO_MAILBOX with an in-app notice.

---

## 1. Objective

ERP services send **business** email through a controlled email layer. The only possible sender is the **acting or responsible user's own verified mailbox** (PHASE_01 §6.10.3).

1. **User-level connections only:** a company user connects their own mailbox through OAuth. The connection belongs to that user in that company only.
2. **Providers:** Google (Gmail, Google Workspace), Microsoft 365, Zoho; more later through adapters. **No SMTP or password connections for business email.**
3. **OAuth:** authorization with state + PKCE; provider-verified account ownership (`provider_account_id`, `verified_email`). `verified_email` must equal the user's own verified ERP account email.
4. **Tokens:**
   - encrypted secret references, never plaintext;
   - expiry tracking and silent refresh;
   - reconnect only when the provider invalidates the grant, with a clear prompt to the user;
   - revocation and disconnection by the user, and automatically on user deactivation;
   - audit of connect, refresh failure, reconnect, revoke and every send.
5. **Sender identity:** the verified mailbox address (and provider-verified aliases of the same mailbox). The sending policy is fixed: ACTING_USER, fallback NONE.
6. **No mailbox → no send:**
   - an interactive send is rejected with `EMAIL_MAILBOX_NOT_CONNECTED` and a "Connect your mailbox" prompt;
   - a scheduled or system send (sender = the responsible user) is stored as BLOCKED_NO_MAILBOX, and that user gets an in-app notification;
   - a revoked grant gives BLOCKED_REAUTH until the user reconnects.
7. **Authorization:** no user can use another user's connection, and no company another company's.
8. **Central dispatch** in NOTIFICATION-SERVICE with per-company send logs, retries and backoff. **No provider credentials inside individual microservices.**

## 2. Existing implementation relevant to this phase

See PHASE_01 §6.10.1 for the full reuse and change table. Summary (`CODE-INSPECTED`):

| Service | Today (CURRENT/LEGACY) |
|---|---|
| CRM | `GmailEmailSender` (global `JavaMailSender`); outbox `email_messages` / `email_logs` / `email_recipients` / `email_attachments`; `event_email_dispatches` with `claimed_by`; quotation emails show the **logged-in user's** name and title in the signature but are sent from the global account; kill switch `CRM_EMAIL_ENABLED` |
| QC, R&D | Async fire-and-forget, hard-coded noreply address and logo, a **committed SMTP password in QC `application.yml`** |
| COMPLIANCE | Direct sends; report to one hard-coded address |
| TICKETS | `email_queue`, but the sender bean is empty (broken) |
| ACCOUNT-BOOK | Synchronous document emails; "NyaalKaran ERP" hard-coded |
| NOTIFICATION | In-app only; `channel` EMAIL stored but unused; per-company DB; internal-token API |
| Keycloak | Realm SMTP for its own account emails (target: the platform mailbox, CD-19) |

## 3. Existing completed work

| Item | Status |
|---|---|
| CRM outbox model and claim pattern; failure classifier; HTML sanitizer | COMPLETED as patterns (reused) |
| `@RunPerTenant` and `MdcTaskDecorator` | COMPLETED (reused) |
| Per-company recipient groups | COMPLETED (kept) |
| Acting-user context in CRM sends | COMPLETED as context (reused for ACTING_USER) |
| In-app notifications (NOTIFICATION) | COMPLETED (used for the "mailbox not connected" notices) |
| User email connections, OAuth, central dispatch | PENDING |

## 4. Reusable components

Listed in PHASE_01 §6.10.1:
- the CRM outbox, claim and classifier patterns;
- the `HtmlSanitizer`;
- the service templates;
- the recipient groups;
- the acting-user context;
- the per-tenant jobs;
- the NOTIFICATION service (email channel + in-app notices);
- the secret-reference mechanism (built here, PHASE_02 §6.3).

## 5. Work requiring modification or redesign

| Item | Change |
|---|---|
| Direct `JavaMailSender` in CRM, QC, R&D, COMPLIANCE, TICKETS, ACCOUNT-BOOK | Replaced by the COMMON `EmailClient`. All `spring.mail.*`, `MAIL_*`, `EMAIL_*`, `CRM_EMAIL_*`, `SMTP_*` credentials are removed from the services. |
| CRM email tables | Generalized into the NOTIFICATION `email_message*` tables. The CRM history is kept read-only, then retired after owner approval. |
| TICKETS `MailConfig` + queue | Removed; uses the client |
| Hard-coded addresses and recipients | Company settings (recipients only; never the sender) |
| NOTIFICATION | Becomes the business email layer (`channel` EMAIL implemented) |
| Scheduled business mail (e.g. COMPLIANCE report, CRM events) | Each schedule records its **responsible user**; it is sent from that user's mailbox or blocked (CD-23) |

## 6. New development required

1. **Control plane: user email configuration** (PHASE_01 §6.2 J, owner type USER only).
   - Providers and OAuth apps (owner registrations, Q-37).
   - USER connections, secret references, OAuth state, sender identity.
   - **Write-only secret APIs.**
2. **OAuth connect flow:** "Connect my mailbox" in the ERP creates a USER connection for the caller's own `company_user_id` only.
   - Callback validation: state hash, PKCE, fixed redirect, provider identity → `provider_account_id`, `verified_email`.
   - Reject when `verified_email` ≠ the user's own ERP account email (`EMAIL_ADDRESS_MISMATCH`).
3. **Token service.**
   - Proactive refresh under a per-connection Redis lock, rotating credential versions.
   - REAUTH_REQUIRED on `invalid_grant`, with an in-app prompt.
   - Revocation on disconnect (the provider endpoint where supported).
   - Automatic DISABLED when the user is deactivated or the company is suspended.
4. **COMMON mail library.**
   - `MailTransportAdapter` SPI with Gmail API, Microsoft Graph and Zoho adapters (no SMTP adapter for business email).
   - Provider failure classifiers.
   - `EmailClient` for the services. It carries the purpose, module, rendered content, attachment ids, idempotency key and the acting or responsible user from the verified context.
5. **NOTIFICATION email layer.**
   - Submit API.
   - Sender resolution: the acting or responsible user's CONNECTED, verified connection. Otherwise the message is **not sent** (BLOCKED_NO_MAILBOX / BLOCKED_REAUTH) and an in-app notice is raised. Checked at selection **and** at send.
   - The `email_message*` tables in the company notification DB.
   - A worker with claim, backoff, per-connection limits and a suppression list.
   - Retention purge: bodies 180 days, metadata 2 years (Q-38, decided).
6. **ERP UI** (consumer):
   - "My email connection" for every user (connect, status, reconnect, disconnect);
   - a send-log view (`company.email.log.read`);
   - company admins can disable a user's connection (`company.user.manage`).
7. **Service migration:** CRM, QC, R&D, COMPLIANCE, TICKETS, ACCOUNT-BOOK. At the same time:
   - fix the unescaped HTML;
   - remove `mail.smtp.debug`;
   - fix the CRM sender-source inconsistency and the wrong logo CID;
   - make the COMPLIANCE report recipient a company setting, with its responsible user as the sender.
8. **Shivam transition:**
   - Shivam's users connect their own mailboxes;
   - every scheduled business mail gets a responsible user;
   - after a rehearsal, the interim global sender is switched off for business email. From then on, unconnected users cannot send (CD-23).

## 7. Exact services, files and database areas involved

| Area | Files |
|---|---|
| COMMON-LIBRARY | New `mail/*` (SPI, OAuth adapters, classifiers, `EmailClient`) |
| CONTROL-PANEL | New `platform/email/*` (providers, apps, USER connections, credentials, OAuth, tokens, identity, internal credential API) |
| NOTIFICATION-SERVICE | New `email/*` (API, sender resolution, entities, worker, purge, in-app blocked notices) |
| CRM | `email/**`, `event/service/EventEmail*`, `quotation/service/QuotationEmailDispatchService.java`, `utils/SalesOrderMailService.java` |
| QC, R&D | `utils/SalesOrderMailService.java`, `mail/*`, `application.yml` (credentials removed) |
| COMPLIANCE | `service/impl/EmailNotificationServiceImpl.java`, `scheduler/ComplianceSchedulerService.java` |
| TICKETS | `config/MailConfig.java` (removed), `service/ServiceImpl/MailQueueServiceImpl.java`, `utill/ScheduledMailSenderService.java` |
| ACCOUNT-BOOK | `accounting/service/DocumentEmailService.java`, `InvoiceEmailService.java` |
| Databases | `control_panel_db` (PHASE_01 §6.2 J, USER part); `cmp_<n>_notification_db` (PHASE_01 §6.10.5) |

## 8. Proposed schema changes

The `[FUTURE]` USER-related tables from PHASE_01 §6.2 J and §6.10.5, created by additive changesets in this phase. No CURRENT table is altered. The platform mailbox log (`tbl_platform_email_message*`) already exists from R1.

## 9. API and integration requirements

| API | Notes |
|---|---|
| `POST /api/v1/internal/notifications/email` (NOTIFICATION) | Services only (M2M + internal token); company and acting/responsible user from the verified context; answers `EMAIL_MAILBOX_NOT_CONNECTED` when no verified mailbox exists |
| `GET /api/v1/internal/platform/email/connections/{id}/credential` (CP) | NOTIFICATION and CP only (`azp`); short-lived; never logged |
| `/control-panel/api/v1/me/email/**` | The caller's own connection: connect, status, reconnect, disconnect |
| `/control-panel/api/v1/company/email/log` | Send log (`company.email.log.read`) |
| `/control-panel/api/v1/public/email/oauth/callback/{provider}` | Fixed redirect; state + PKCE |
| Provider APIs | Outbound only (OAuth endpoints, Gmail API, Microsoft Graph, Zoho Mail API) |

## 10. Dependencies on other phases

| Phase | Relationship |
|---|---|
| PHASE_12 | R1 live (Shivam on the interim sender) |
| PHASE_10 | Company users, permissions, user email in the company realm |
| PHASE_11 | Branding and settings |
| PHASE_02 | Secret-reference mechanism |
| PHASE_07 | The platform mailbox (R1) stays separate and unchanged |

## 11. Security and tenant-isolation requirements

1. A connection is used only by its owner, in the owner's company, for messages whose acting or responsible user is that owner. This is checked at selection and at send; violations are rejected and audited.
2. **No fallback sender of any kind** for business email (CD-23). The platform mailbox can never send business email.
3. **No plaintext secrets.** Secret references (encrypted DB column or external store); write-only APIs; no tokens in logs, Redis, metrics or exceptions; no shared token caches.
4. OAuth: state + PKCE; a fixed redirect; the provider identity is bound to the connection; scopes minimized to sending (Q-37).
5. The connected mailbox must equal the user's own ERP account email. Sanitized HTML. No SMTP debug in production.
6. Revocation on disconnect or user removal. Sending pauses on company suspension.

## 12. Ordered implementation tasks

| # | Task | Status |
|---|---|---|
| 1 | CP USER email schema + configuration services + write-only APIs | PENDING |
| 2 | COMMON mail library: OAuth adapters, classifiers, `EmailClient` | PENDING |
| 3 | NOTIFICATION email layer: sender resolution (no fallback), outbox, worker, logs, blocked notices | PENDING |
| 4 | OAuth: Microsoft 365 and Google connect, callback, refresh, revoke | PENDING (owner app registrations, Q-37; Google restricted-scope verification) |
| 5 | Zoho OAuth | PENDING (owner app registration, Q-37) |
| 6 | User "connect my mailbox" UI + send log | PENDING |
| 7 | Migrate the 6 services (responsible user for scheduled mail); fix the listed defects | PENDING |
| 8 | Shivam transition from the interim global sender | PENDING |

## 13. Acceptance criteria

1. User A of company X connects their Google mailbox (A's own ERP email). A CRM quotation sent by A goes out from A's mailbox and is logged in X's DB. User B (same company) cannot send as A. A user of company Y cannot use any of X's connections (tests at selection and at send).
2. User C has no connected mailbox: sending a quotation returns `EMAIL_MAILBOX_NOT_CONNECTED`, **nothing is sent from any other mailbox**, and C sees "Connect your mailbox".
3. Connecting a mailbox whose address differs from the user's ERP email is rejected.
4. A scheduled COMPLIANCE report whose responsible user has no connection is stored as BLOCKED_NO_MAILBOX, and that user receives an in-app notice.
5. A revoked grant leads to REAUTH_REQUIRED and BLOCKED_REAUTH messages. After reconnecting, the user can resend. Other users and companies are unaffected.
6. Tokens refresh without user action until the provider invalidates the grant.
7. No secret is present in API responses, logs or exceptions (automated scan). No service still holds mail credentials or injects `JavaMailSender` (grep).

## 14. Testing and validation checklist

- [ ] Unit: sender resolution (connected / not connected / mismatch / reauth), authorization matrix (owner × acting user × company), token state machine.
- [ ] Integration: stubbed OAuth and token endpoints (Google, Microsoft, Zoho); two companies and several users; no-fallback tests.
- [ ] Migration regression per service (template output).
- [ ] Load and fairness: per-connection caps.

## 15. Risks and rollback considerations

| Risk | Mitigation |
|---|---|
| Users who never connect a mailbox cannot send business email | Clear in-app prompts; onboarding checklist; send-log visibility for admins |
| Google restricted-scope verification delay | Microsoft first; Google after verification (Q-37) |
| Behaviour change for Shivam (no shared sender any more) | Rehearsal; every Shivam user connects before the switch-off |
| Provider throttling | Per-connection caps, backoff |

**Rollback:** a per-service flag returns that service to the interim sender (Shivam only) until the migration is signed off. Connections can be disabled without data loss.

## 16. Current status

| Item | Status |
|---|---|
| Global SMTP senders (interim for Shivam) | Exist (CURRENT/LEGACY) |
| Platform mailbox for platform email | R1, PHASE_07 |
| User mailbox connections, OAuth, secret references, central dispatch | PENDING (FUTURE) |
| Company shared mailboxes, platform fallback, generic SMTP for business email | **Not supported** (CD-23) |
| OAuth app registrations | PENDING (owner, Q-37) |
| **Phase** | **PENDING** |
