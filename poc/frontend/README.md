# Portals: platform console and company portal

Two web apps on the same backend. Neither needs a build step or Node.js: they are plain ES modules and CSS, served
as static files.

| App | URL | Who | Signs in to |
|---|---|---|---|
| **Platform console** | http://localhost:4200/platform/ | Platform Super Admin | Realm `erp-platform`, client `platform-console` |
| **Company portal** | http://localhost:4200/tenant/ | A company's own staff | That company's realm `co-<n>`, client `erp-web`, found through the **Company Login Code** |

Both sign in with OpenID Connect Authorization Code + PKCE against Keycloak (no passwords pass through the
apps). Tokens live in the browser tab's `sessionStorage` and refresh before they expire.

## Run

1. Start the backend first: `docker compose up -d`, the control plane on 8080 and the three services on
   8091–8093 (see [`../services/README.md`](../services/README.md)). After pulling these changes, restart the
   control plane (new branding tables and endpoints) and rebuild the services (CORS). Stop the services before
   `mvn -f services/pom.xml package`, because on Windows a running jar is locked.
2. Serve the portals. Port 4200 is required, because it is what Keycloak and the backends' CORS settings allow:

   ```bash
   bash frontend/serve.sh
   ```

   This runs `jwebserver` from your JDK. Any static file server on port 4200 works too.
3. Open http://localhost:4200/.

Demo data with three branded car companies (Velocity Motors: red, light; Apex Autos: navy, dark, technical font;
Zenith Cars: green, rounded):

```bash
bash scripts/seed-demo-data.sh
```

The script prints each company's **login code**. Every demo user's password is `Demo-Pass-1`: `owner@…`
(Company Super Admin), `sales@…` (vehicle, inventory and booking roles), `viewer@…` (vehicle role only, so
Inventory and Bookings appear locked).

## What each app does

**Platform console**
- Dashboard: company counts by status, recently onboarded companies (each avatar in its own branding).
- **Onboard a company** wizard:
  1. Company details.
  2. Plan: bundles and modules as cards, live price estimate, optional audited discount.
  3. Server quote, confirm, payments (instant UPI or cash; cheque or bank transfer with verify and reject).
  4. Provisioning with live step-by-step progress.
  5. Access: login code, portal link, one-time temporary password.
- Company page tabs:
  - Overview: modules, subscription, allowances, the live Redis access state.
  - Orders & payments: upgrade quotes; a drawer for each order with its payments.
  - Provisioning jobs (resume a failed job), resources (databases, buckets, realm), access, history.
  - **Branding**: colours, presets, light/dark/system, typography, logo, tagline, with a **live preview** of the
    company portal.
  - Status actions: suspend, reactivate, disable, archive, each with a reason.
- Catalog & pricing: bundles with savings, modules with dependencies, the database per module, monthly or yearly
  prices.

**Company portal**
- Step 1, login code: the page **re-themes itself** to the company's branding (colours, logo, tagline, font) before
  anyone signs in. Step 2: sign in to the company's own realm.
- Navigation shows only the modules the company bought. Modules the user has no role for are **locked**.
- Dashboard: KPIs, stock by model (available, reserved, sold), recent bookings, plan and roles.
- Vehicles (catalog cards), Inventory (VIN list, receive a car with VIN validation), Bookings:
  - "New booking" shows the models and colours actually in stock, then the booking service reserves a real VIN.
  - Each booking can then be delivered or cancelled.
- Clear full-page states for `MODULE_NOT_ENTITLED`, `MODULE_ACCESS_DENIED`, `COMPANY_SUSPENDED`, and so on.
- Each user can switch light/dark; the choice is remembered per company.

## How the theming works

`GET /api/v1/public/login-discovery?loginCode=…` (no token) and `GET /api/v1/company/me` return the company's
branding. [`shared/theme.js`](shared/theme.js) turns it into CSS variables (`--primary`, `--accent`, `--font`,
plus derived tints) and `data-theme="light|dark"`. Every component in [`shared/styles.css`](shared/styles.css)
uses only those variables, so one stylesheet renders every company. Text on brand colours picks black or white by
WCAG contrast. The favicon and page title follow the company too. Companies that were never customised get a
deterministic palette from their code, so no two look the same by accident.

## Code map

```
index.html                 landing: choose console or portal
config.js                  API base URLs (Keycloak's URL comes from the control plane)
serve.sh                   static server on :4200 (jwebserver)
shared/                    design system and plumbing used by both apps
  styles.css               tokens, light/dark, components (buttons, tables, modals, drawers, steps, …)
  theme.js                 branding -> CSS variables, contrast, favicon, light/dark
  auth.js                  OIDC Authorization Code + PKCE, refresh, back-channel logout
  api.js                   fetch wrapper: bearer token, problem-details errors with `code`
  ui.js shell.js icons.js router.js   DOM helpers, app frame, inline SVG icons, hash router
platform/                  console: app.js, lib.js, pages/, components/ (billing, provisioning, access, branding, onboarding)
tenant/                    company portal: app.js, lib.js, pages/ (dashboard, vehicles, inventory, bookings)
```

## Backend endpoints added for the portals

| Endpoint | Auth | Purpose |
|---|---|---|
| `GET /api/v1/public/config` | none | Keycloak URL, platform realm, client ids |
| `GET /api/v1/public/login-discovery?loginCode=` | none | Company name, realm, issuer, branding; one generic 404 for unknown codes |
| `GET /api/v1/company/me` | company-realm token | Company, entitled modules with "can this user open it", user, roles, branding |
| `GET` / `PUT /api/v1/platform/companies/{code}/branding` | Platform Super Admin | Read or set the white-label branding (`tbl_company_branding`, changeset `004`) |
| `POST /api/v1/platform/companies/{code}/super-admin/temporary-password` | Platform Super Admin | POC stand-in for the invitation e-mail: a one-time password Keycloak forces the user to change |

CORS: the control plane (`poc.cors.allowed-origins`) and every service (`erp.tenant.cors-allowed-origins`) allow
`http://localhost:4200`.

## Limits (POC)

- No build step means no TypeScript and no bundling. Modules load individually, which is fine on localhost.
- The login-code discovery endpoint is not rate-limited (CD-15 asks for that).
- Logo images are loaded from any http(s) URL the platform admin enters. There is no upload to company storage yet.
