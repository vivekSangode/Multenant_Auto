# Every API with curl, and what it returns

This is a recorded run of [`scripts/curl-flow.sh`](scripts/curl-flow.sh) on a clean stack: control plane, Keycloak,
and the three car-dealer microservices. Every request below was really sent, and every body is the real response
(timestamps, ids, `CMP_<n>` codes and VINs differ on each run; tokens are shortened).

The flow onboards **Velocity Motors** (AUTO_DEALER bundle) and **Apex Autos** (vehicle + inventory, then an
upgrade with booking). It drives the **same** service APIs with each company's own token, then checks the guard
rails. To load richer demo data for your own testing, see [`scripts/seed-demo-data.sh`](scripts/seed-demo-data.sh)
(three companies, several users each).

## Run it yourself

The script needs only `bash` and `curl` (Git Bash works on Windows). If `jq` is installed, bodies are
pretty-printed. Start the stack as described in [`services/README.md`](services/README.md), then from `poc/`:

```bash
bash scripts/curl-flow.sh
```

Other hosts or ports can be set with `CP`, `KC`, `VEHICLE`, `INVENTORY`, `BOOKING`, for example:

```bash
CP=http://localhost:8080 KC=http://localhost:8180 bash scripts/curl-flow.sh
```

The commands below use these variables:

| Variable | Default | What |
|---|---|---|
| `$CP` | `http://localhost:8080` | Control plane: platform API (Super Admin token), tenant API, internal API (service tokens) |
| `$KC` | `http://localhost:8180` | Keycloak: realm `erp-platform`, one realm `co-<n>` per company |
| `$VEHICLE` / `$INVENTORY` / `$BOOKING` | `:8091` / `:8092` / `:8093` | The car-dealer microservices |
| `$PLATFORM_TOKEN` | — | Platform Super Admin (`superadmin` / `change-me`, client `platform-console`) |
| `$TOKEN_A` / `$TOKEN_B` | — | Company Super Admin of Velocity Motors / Apex Autos (client `erp-web` of their realm) |
| `$OPERATOR_TOKEN` | — | Keycloak `master` admin (`admin` / `admin`): only used to accept the invitation, because the POC has no SMTP |
| `$SVC_TOKEN` | — | M2M token of `svc-vehicle` (client credentials): what the services use for the internal API |

How to get a token by hand:

```bash
curl -s $KC/realms/erp-platform/protocol/openid-connect/token -d grant_type=password -d client_id=platform-console -d username=superadmin -d password=change-me
```

Errors always have the same shape (RFC 9457 problem details with a stable `code`), except 401. A 401 has no
body; the reason is in the `WWW-Authenticate` header.

## 0. Health

### 1. CP health

```bash
curl -X GET "$CP/actuator/health"
```

**HTTP 200**

```json
{
  "status": "UP"
}
```

### 2. VEHICLE health

```bash
curl -X GET "$VEHICLE/actuator/health"
```

**HTTP 200**

```json
{
  "status": "UP"
}
```

### 3. INVENTORY health

```bash
curl -X GET "$INVENTORY/actuator/health"
```

**HTTP 200**

```json
{
  "status": "UP"
}
```

### 4. BOOKING health

```bash
curl -X GET "$BOOKING/actuator/health"
```

**HTTP 200**

```json
{
  "status": "UP"
}
```

## 1. Platform login + catalog

### 5. Platform Super Admin token (realm erp-platform)

```bash
curl -X POST "$KC/realms/erp-platform/protocol/openid-connect/token" -d grant_type=password -d client_id=platform-console -d username=superadmin -d password=***
```

**HTTP 200**

```json
{
  "access_token": "eyJhbGciOiJS...",
  "expires_in": 300,
  "refresh_expires_in": 1800,
  "refresh_token": "eyJhbGciOiJI...",
  "token_type": "Bearer",
  "not-before-policy": 0,
  "session_state": "47332a68-1eb2-4ebc-8677-f2b3664f2431",
  "scope": "email profile"
}
```

### 6. Catalog with the monthly price list

```bash
curl -X GET "$CP/api/v1/platform/catalog?period=MONTHLY" -H "Authorization: Bearer $PLATFORM_TOKEN"
```

**HTTP 200**

<details><summary>Response body (547 lines)</summary>

```json
{
  "modules": {
    "crm": {
      "id": 1,
      "code": "crm",
      "name": "CRM",
      "type": "BUSINESS",
      "dbToken": "crm",
      "active": true
    },
    "logistics": {
      "id": 11,
      "code": "logistics",
      "name": "Logistics",
      "type": "BUSINESS",
      "dbToken": "logistics",
      "active": true
    },
    "portal": {
      "id": 15,
      "code": "portal",
      "name": "Customer Portal",
      "type": "ADDON",
      "dbToken": null,
      "active": true
    },
    "hrms": {
      "id": 2,
      "code": "hrms",
      "name": "HRMS",
      "type": "BUSINESS",
      "dbToken": "hrms",
      "active": true
    },
    "attendance": {
      "id": 3,
      "code": "attendance",
      "name": "Attendance",
      "type": "BUSINESS",
      "dbToken": "attendance",
      "active": true
    },
    "payroll": {
      "id": 4,
      "code": "payroll",
      "name": "Payroll",
      "type": "BUSINESS",
      "dbToken": "payroll",
      "active": true
    },
    "store": {
      "id": 5,
      "code": "store",
      "name": "Store",
      "type": "BUSINESS",
      "dbToken": "store",
      "active": true
    },
    "account-book": {
      "id": 6,
      "code": "account-book",
      "name": "Account Book",
      "type": "BUSINESS",
      "dbToken": "account_book",
      "active": true
    },
    "production": {
      "id": 7,
      "code": "production",
      "name": "Production",
      "type": "BUSINESS",
      "dbToken": "production",
      "active": true
    },
    "mining": {
      "id": 8,
      "code": "mining",
      "name": "Mining",
      "type": "BUSINESS",
      "dbToken": "mining",
      "active": true
    },
    "qc": {
      "id": 9,
      "code": "qc",
      "name": "Quality Control",
      "type": "BUSINESS",
      "dbToken": "qc",
      "active": true
    },
    "research-and-development": {
      "id": 10,
      "code": "research-and-development",
      "name": "Research and Development",
      "type": "BUSINESS",
      "dbToken": "rnd",
      "active": true
    },
    "compliance": {
      "id": 12,
      "code": "compliance",
      "name": "Compliance",
      "type": "BUSINESS",
      "dbToken": "compliance",
      "active": true
    },
    "asset": {
      "id": 13,
      "code": "asset",
      "name": "Asset",
      "type": "BUSINESS",
      "dbToken": "asset",
      "active": true
    },
    "chat": {
      "id": 14,
      "code": "chat",
      "name": "Chat",
      "type": "BUSINESS",
      "dbToken": "chat",
      "active": true
    },
    "vehicle": {
      "id": 18,
      "code": "vehicle",
      "name": "Vehicle Catalog",
      "type": "BUSINESS",
      "dbToken": "vehicle",
      "active": true
    },
    "inventory": {
      "id": 19,
      "code": "inventory",
      "name": "Vehicle Inventory",
      "type": "BUSINESS",
      "dbToken": "inventory",
      "active": true
    },
    "booking": {
      "id": 20,
      "code": "booking",
      "name": "Vehicle Booking",
      "type": "BUSINESS",
      "dbToken": "booking",
      "active": true
    },
    "file": {
      "id": 16,
      "code": "file",
      "name": "Files and Attachments",
      "type": "PLATFORM_CORE",
      "dbToken": "attachment",
      "active": true
    },
    "notification": {
      "id": 17,
      "code": "notification",
      "name": "Notifications",
      "type": "PLATFORM_CORE",
      "dbToken": "notification",
      "active": true
    }
  },
  "bundles": {
    "HR_SUITE": {
      "id": 1,
      "code": "HR_SUITE",
      "name": "HR Suite",
      "moduleCodes": [
        "attendance",
        "hrms",
        "payroll"
      ],
      "active": true
    },
    "SALES_DISPATCH": {
      "id": 2,
      "code": "SALES_DISPATCH",
      "name": "Sales & Dispatch",
      "moduleCodes": [
        "crm",
        "logistics",
        "production"
      ],
      "active": true
    },
    "FINANCE_INVENTORY": {
      "id": 3,
      "code": "FINANCE_INVENTORY",
      "name": "Finance & Inventory",
      "moduleCodes": [
        "account-book",
        "store"
      ],
      "active": true
    },
    "PLANT_OPERATIONS": {
      "id": 4,
      "code": "PLANT_OPERATIONS",
      "name": "Plant Operations",
      "moduleCodes": [
        "mining",
        "production",
        "qc",
        "research-and-development"
      ],
      "active": true
    },
    "AUTO_DEALER": {
      "id": 5,
      "code": "AUTO_DEALER",
      "name": "Auto Dealer Suite",
      "moduleCodes": [
        "booking",
        "inventory",
        "vehicle"
      ],
      "active": true
    }
  },
  "dependencies": [
    {
      "module": "store",
      "dependsOn": "account-book",
      "type": "MANDATORY",
      "chargeMode": "CHARGED"
    },
    {
      "module": "logistics",
      "dependsOn": "crm",
      "type": "MANDATORY",
      "chargeMode": "CHARGED"
    },
    {
      "module": "portal",
      "dependsOn": "crm",
      "type": "MANDATORY",
      "chargeMode": "CHARGED"
    },
    {
      "module": "portal",
      "dependsOn": "logistics",
      "type": "MANDATORY",
      "chargeMode": "CHARGED"
    },
    {
      "module": "payroll",
      "dependsOn": "hrms",
      "type": "MANDATORY",
      "chargeMode": "CHARGED"
    },
    {
      "module": "attendance",
      "dependsOn": "hrms",
      "type": "MANDATORY",
      "chargeMode": "CHARGED"
    },
    {
      "module": "payroll",
      "dependsOn": "attendance",
      "type": "RECOMMENDED",
      "chargeMode": "CHARGED"
    },
    {
      "module": "qc",
      "dependsOn": "research-and-development",
      "type": "MANDATORY",
      "chargeMode": "CHARGED"
    },
    {
      "module": "research-and-development",
      "dependsOn": "mining",
      "type": "RECOMMENDED",
      "chargeMode": "CHARGED"
    },
    {
      "module": "inventory",
      "dependsOn": "vehicle",
      "type": "MANDATORY",
      "chargeMode": "CHARGED"
    },
    {
      "module": "booking",
      "dependsOn": "inventory",
      "type": "MANDATORY",
      "chargeMode": "CHARGED"
    },
    {
      "module": "booking",
      "dependsOn": "vehicle",
      "type": "MANDATORY",
      "chargeMode": "CHARGED"
    }
  ],
  "prices": {
    "BUNDLE:HR_SUITE": {
      "planId": 16,
      "planVersionId": 1,
      "planPriceId": 32,
      "unitPrice": 380.0000,
      "unitQuantity": 1,
      "includedUsers": 0,
      "includedStorageGb": 0,
      "maxQuantity": null
    },
    "MODULE:crm": {
      "planId": 1,
      "planVersionId": 7,
      "planPriceId": 4,
      "unitPrice": 300.0000,
      "unitQuantity": 1,
      "includedUsers": 0,
      "includedStorageGb": 0,
      "maxQuantity": null
    },
    "MODULE:logistics": {
      "planId": 11,
      "planVersionId": 17,
      "planPriceId": 8,
      "unitPrice": 200.0000,
      "unitQuantity": 1,
      "includedUsers": 0,
      "includedStorageGb": 0,
      "maxQuantity": null
    },
    "MODULE:portal": {
      "planId": 15,
      "planVersionId": 21,
      "planPriceId": 30,
      "unitPrice": 120.0000,
      "unitQuantity": 1,
      "includedUsers": 0,
      "includedStorageGb": 0,
      "maxQuantity": null
    },
    "BUNDLE:SALES_DISPATCH": {
      "planId": 17,
      "planVersionId": 2,
      "planPriceId": 34,
      "unitPrice": 650.0000,
      "unitQuantity": 1,
      "includedUsers": 0,
      "includedStorageGb": 0,
      "maxQuantity": null
    },
    "MODULE:hrms": {
      "planId": 2,
      "planVersionId": 8,
      "planPriceId": 10,
      "unitPrice": 200.0000,
      "unitQuantity": 1,
      "includedUsers": 0,
      "includedStorageGb": 0,
      "maxQuantity": null
    },
    "MODULE:attendance": {
      "planId": 3,
      "planVersionId": 9,
      "planPriceId": 12,
      "unitPrice": 80.0000,
      "unitQuantity": 1,
      "includedUsers": 0,
      "includedStorageGb": 0,
      "maxQuantity": null
    },
    "MODULE:payroll": {
      "planId": 4,
      "planVersionId": 10,
      "planPriceId": 14,
      "unitPrice": 150.0000,
      "unitQuantity": 1,
      "includedUsers": 0,
      "includedStorageGb": 0,
      "maxQuantity": null
    },
    "BUNDLE:FINANCE_INVENTORY": {
      "planId": 18,
      "planVersionId": 3,
      "planPriceId": 36,
      "unitPrice": 350.0000,
      "unitQuantity": 1,
      "includedUsers": 0,
      "includedStorageGb": 0,
      "maxQuantity": null
    },
    "MODULE:store": {
      "planId": 5,
      "planVersionId": 11,
      "planPriceId": 18,
      "unitPrice": 150.0000,
      "unitQuantity": 1,
      "includedUsers": 0,
      "includedStorageGb": 0,
      "maxQuantity": null
    },
    "MODULE:account-book": {
      "planId": 6,
      "planVersionId": 12,
      "planPriceId": 16,
      "unitPrice": 250.0000,
... (147 more lines; run scripts/curl-flow.sh to see everything)
```

</details>

## 2. Onboard Velocity Motors (AUTO_DEALER bundle)

### 7. Create company

```bash
curl -X POST "$CP/api/v1/platform/companies" -H "Authorization: Bearer $PLATFORM_TOKEN" -H "Content-Type: application/json" -d '{"legalName":"Velocity Motors Pvt Ltd","superAdminEmail":"owner@velocity.test"}'
```

**HTTP 201**

```json
{
  "id": 13,
  "companyCode": "CMP_113",
  "legalName": "Velocity Motors Pvt Ltd",
  "displayName": "Velocity Motors Pvt Ltd",
  "slug": "velocity-motors-pvt-ltd-6",
  "loginCode": "HFJY9AEJ",
  "status": "DRAFT",
  "statusReason": null,
  "onboardingType": "NEW",
  "superAdminEmail": "owner@velocity.test",
  "realm": "co-113",
  "activatedAt": null
}
```

### 8. List companies

```bash
curl -X GET "$CP/api/v1/platform/companies" -H "Authorization: Bearer $PLATFORM_TOKEN"
```

**HTTP 200**

<details><summary>Response body (184 lines)</summary>

```json
[
  {
    "id": 1,
    "companyCode": "CMP_101",
    "legalName": "Velocity Motors Pvt Ltd",
    "displayName": "Velocity Motors Pvt Ltd",
    "slug": "velocity-motors-pvt-ltd",
    "loginCode": "VMFQXMRK",
    "status": "DRAFT",
    "statusReason": null,
    "onboardingType": "NEW",
    "superAdminEmail": "owner@velocity.test",
    "realm": "co-101",
    "activatedAt": null
  },
  {
    "id": 2,
    "companyCode": "CMP_102",
    "legalName": "Apex Autos LLP",
    "displayName": "Apex Autos LLP",
    "slug": "apex-autos-llp",
    "loginCode": "S35GG3TG",
    "status": "DRAFT",
    "statusReason": null,
    "onboardingType": "NEW",
    "superAdminEmail": "owner@apex.test",
    "realm": "co-102",
    "activatedAt": null
  },
  {
    "id": 3,
    "companyCode": "CMP_103",
    "legalName": "Velocity Motors Pvt Ltd",
    "displayName": "Velocity Motors Pvt Ltd",
    "slug": "velocity-motors-pvt-ltd-2",
    "loginCode": "65KGWFDT",
    "status": "DRAFT",
    "statusReason": null,
    "onboardingType": "NEW",
    "superAdminEmail": "owner@velocity.test",
    "realm": "co-103",
    "activatedAt": null
  },
  {
    "id": 4,
    "companyCode": "CMP_104",
    "legalName": "Apex Autos LLP",
    "displayName": "Apex Autos LLP",
    "slug": "apex-autos-llp-2",
    "loginCode": "T2CL5U3C",
    "status": "DRAFT",
    "statusReason": null,
    "onboardingType": "NEW",
    "superAdminEmail": "owner@apex.test",
    "realm": "co-104",
    "activatedAt": null
  },
  {
    "id": 5,
    "companyCode": "CMP_105",
    "legalName": "Velocity Motors Pvt Ltd",
    "displayName": "Velocity Motors Pvt Ltd",
    "slug": "velocity-motors-pvt-ltd-3",
    "loginCode": "M9QPTAQH",
    "status": "ACTIVE",
    "statusReason": "paid",
    "onboardingType": "NEW",
    "superAdminEmail": "owner@velocity.test",
    "realm": "co-105",
    "activatedAt": "2026-10-16T17:13:13.287144"
  },
  {
    "id": 6,
    "companyCode": "CMP_106",
    "legalName": "Apex Autos LLP",
    "displayName": "Apex Autos LLP",
    "slug": "apex-autos-llp-3",
    "loginCode": "JX595B4H",
    "status": "ACTIVE",
    "statusReason": "all mandatory steps verified",
    "onboardingType": "NEW",
    "superAdminEmail": "owner@apex.test",
    "realm": "co-106",
    "activatedAt": "2026-10-16T17:13:25.478398"
  },
  {
    "id": 7,
    "companyCode": "CMP_107",
    "legalName": "Velocity Motors Pvt Ltd",
    "displayName": "Velocity Motors Pvt Ltd",
    "slug": "velocity-motors-pvt-ltd-4",
    "loginCode": "AX9X7XFZ",
    "status": "ACTIVE",
    "statusReason": "all mandatory steps verified",
    "onboardingType": "NEW",
    "superAdminEmail": "owner@velocity.test",
    "realm": "co-107",
    "activatedAt": "2026-10-16T17:22:39.217893"
  },
  {
    "id": 8,
    "companyCode": "CMP_108",
    "legalName": "Apex Autos LLP",
    "displayName": "Apex Autos LLP",
    "slug": "apex-autos-llp-4",
    "loginCode": "R7TSHE5S",
    "status": "ACTIVE",
    "statusReason": "all mandatory steps verified",
    "onboardingType": "NEW",
    "superAdminEmail": "owner@apex.test",
    "realm": "co-108",
    "activatedAt": "2026-10-16T17:22:45.480159"
  },
  {
    "id": 9,
    "companyCode": "CMP_109",
    "legalName": "Zenith Cars Ltd",
    "displayName": "Zenith Cars Ltd",
    "slug": "zenith-cars-ltd",
    "loginCode": "VW4SFQCH",
    "status": "ACTIVE",
    "statusReason": "all mandatory steps verified",
    "onboardingType": "NEW",
    "superAdminEmail": "owner@zenith.test",
    "realm": "co-109",
    "activatedAt": "2026-10-16T17:22:53.447169"
  },
  {
    "id": 10,
    "companyCode": "CMP_110",
    "legalName": "Velocity Motors Pvt Ltd",
    "displayName": "Velocity Motors Pvt Ltd",
    "slug": "velocity-motors-pvt-ltd-5",
    "loginCode": "MY9GAHB2",
    "status": "ACTIVE",
    "statusReason": "all mandatory steps verified",
    "onboardingType": "NEW",
    "superAdminEmail": "owner@velocity.test",
    "realm": "co-110",
    "activatedAt": "2026-10-16T17:30:56.89866"
  },
  {
    "id": 11,
    "companyCode": "CMP_111",
    "legalName": "Apex Autos LLP",
    "displayName": "Apex Autos LLP",
    "slug": "apex-autos-llp-5",
    "loginCode": "SW3SYPKU",
    "status": "ACTIVE",
    "statusReason": "all mandatory steps verified",
    "onboardingType": "NEW",
    "superAdminEmail": "owner@apex.test",
    "realm": "co-111",
    "activatedAt": "2026-10-16T17:31:03.279006"
  },
  {
    "id": 12,
    "companyCode": "CMP_112",
    "legalName": "Zenith Cars Ltd",
    "displayName": "Zenith Cars Ltd",
    "slug": "zenith-cars-ltd-2",
    "loginCode": "RQVFJBPS",
    "status": "ACTIVE",
    "statusReason": "all mandatory steps verified",
    "onboardingType": "NEW",
    "superAdminEmail": "owner@zenith.test",
    "realm": "co-112",
    "activatedAt": "2026-10-16T17:31:09.341955"
  },
  {
    "id": 13,
    "companyCode": "CMP_113",
    "legalName": "Velocity Motors Pvt Ltd",
    "displayName": "Velocity Motors Pvt Ltd",
    "slug": "velocity-motors-pvt-ltd-6",
    "loginCode": "HFJY9AEJ",
    "status": "DRAFT",
    "statusReason": null,
    "onboardingType": "NEW",
    "superAdminEmail": "owner@velocity.test",
    "realm": "co-113",
    "activatedAt": null
  }
]
```

</details>

### 9. Get company

```bash
curl -X GET "$CP/api/v1/platform/companies/CMP_113" -H "Authorization: Bearer $PLATFORM_TOKEN"
```

**HTTP 200**

```json
{
  "id": 13,
  "companyCode": "CMP_113",
  "legalName": "Velocity Motors Pvt Ltd",
  "displayName": "Velocity Motors Pvt Ltd",
  "slug": "velocity-motors-pvt-ltd-6",
  "loginCode": "HFJY9AEJ",
  "status": "DRAFT",
  "statusReason": null,
  "onboardingType": "NEW",
  "superAdminEmail": "owner@velocity.test",
  "realm": "co-113",
  "activatedAt": null
}
```

### 10. Quote that will be cancelled

```bash
curl -X POST "$CP/api/v1/platform/companies/CMP_113/orders" -H "Authorization: Bearer $PLATFORM_TOKEN" -H "Content-Type: application/json" -d '{"billingPeriod":"MONTHLY","modules":["vehicle"],"users":5,"storageGb":10}'
```

**HTTP 201**

<details><summary>Response body (74 lines)</summary>

```json
{
  "order": {
    "id": 15,
    "createdAt": "2026-10-16T17:31:30.70678",
    "createdBy": "superadmin",
    "updatedAt": "2026-10-16T17:31:30.70678",
    "updatedBy": "superadmin",
    "version": 0,
    "orderNumber": "ORD/2026-27/00015",
    "companyId": 13,
    "subscriptionId": null,
    "orderKind": "NEW",
    "channel": "ADMIN",
    "billingPeriod": "MONTHLY",
    "currency": "INR",
    "requestedUsers": 5,
    "requestedStorageGb": 10,
    "subtotal": 150.0000,
    "discountTotal": 0.0000,
    "taxableTotal": 150.0000,
    "taxTotal": 0.0000,
    "otherChargesTotal": 0.0000,
    "roundOff": 0.0000,
    "grandTotal": 150.0000,
    "paymentStatus": "UNPAID",
    "status": "QUOTED",
    "calculationVersion": "poc-1",
    "inputJson": "{\"command\": {\"users\": 5, \"bundles\": null, \"modules\": [\"vehicle\"], \"currency\": \"INR\", \"storageGb\": 10, \"billingPeriod\": \"MONTHLY\", \"idempotencyKey\": \"65b4ed7b-6209-40bb-8708-4dc7efd8f86f\", \"manualDiscount\": null}, \"existingModules\": []}",
    "validUntil": "2026-10-31T17:31:30.70678",
    "idempotencyKey": "65b4ed7b-6209-40bb-8708-4dc7efd8f86f",
    "createdByActor": "superadmin"
  },
  "lines": [
    {
      "id": 25,
      "createdAt": "2026-10-16T17:31:30.708774",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:30.708774",
      "updatedBy": "superadmin",
      "version": 0,
      "billingOrderId": 15,
      "lineNo": 1,
      "lineType": "PLAN",
      "planVersionId": 35,
      "planPriceId": 65,
      "moduleId": 18,
      "bundleId": null,
      "description": "Module Vehicle Catalog",
      "quantity": 1.0000,
      "unitPrice": 150.0000,
      "grossAmount": 150.0000,
      "lineDiscountAmount": 0.0000,
      "taxableAmount": 150.0000,
      "taxAmount": 0.0000,
      "lineTotal": 150.0000
    }
  ],
  "discounts": [],
  "warnings": [
    {
      "id": 15,
      "createdAt": "2026-10-16T17:31:30.709774",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:30.709774",
      "updatedBy": "superadmin",
      "version": 0,
      "billingOrderId": 15,
      "code": "TAX_RULE_NOT_CONFIGURED",
      "detail": "No APPROVED tax rule (Q-04); tax = 0"
    }
  ],
  "payments": [],
  "invoice": null
}
```

</details>

### 11. Cancel that quote

```bash
curl -X POST "$CP/api/v1/platform/orders/15/cancel" -H "Authorization: Bearer $PLATFORM_TOKEN" -H "Content-Type: application/json" -d '{"reason":"customer chose the bundle"}'
```

**HTTP 200**

<details><summary>Response body (74 lines)</summary>

```json
{
  "order": {
    "id": 15,
    "createdAt": "2026-10-16T17:31:30.70678",
    "createdBy": "superadmin",
    "updatedAt": "2026-10-16T17:31:30.842832",
    "updatedBy": "superadmin",
    "version": 1,
    "orderNumber": "ORD/2026-27/00015",
    "companyId": 13,
    "subscriptionId": null,
    "orderKind": "NEW",
    "channel": "ADMIN",
    "billingPeriod": "MONTHLY",
    "currency": "INR",
    "requestedUsers": 5,
    "requestedStorageGb": 10,
    "subtotal": 150.0000,
    "discountTotal": 0.0000,
    "taxableTotal": 150.0000,
    "taxTotal": 0.0000,
    "otherChargesTotal": 0.0000,
    "roundOff": 0.0000,
    "grandTotal": 150.0000,
    "paymentStatus": "UNPAID",
    "status": "CANCELLED",
    "calculationVersion": "poc-1",
    "inputJson": "{\"command\": {\"users\": 5, \"bundles\": null, \"modules\": [\"vehicle\"], \"currency\": \"INR\", \"storageGb\": 10, \"billingPeriod\": \"MONTHLY\", \"idempotencyKey\": \"65b4ed7b-6209-40bb-8708-4dc7efd8f86f\", \"manualDiscount\": null}, \"existingModules\": []}",
    "validUntil": "2026-10-31T17:31:30.70678",
    "idempotencyKey": "65b4ed7b-6209-40bb-8708-4dc7efd8f86f",
    "createdByActor": "superadmin"
  },
  "lines": [
    {
      "id": 25,
      "createdAt": "2026-10-16T17:31:30.708774",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:30.708774",
      "updatedBy": "superadmin",
      "version": 0,
      "billingOrderId": 15,
      "lineNo": 1,
      "lineType": "PLAN",
      "planVersionId": 35,
      "planPriceId": 65,
      "moduleId": 18,
      "bundleId": null,
      "description": "Module Vehicle Catalog",
      "quantity": 1.0000,
      "unitPrice": 150.0000,
      "grossAmount": 150.0000,
      "lineDiscountAmount": 0.0000,
      "taxableAmount": 150.0000,
      "taxAmount": 0.0000,
      "lineTotal": 150.0000
    }
  ],
  "discounts": [],
  "warnings": [
    {
      "id": 15,
      "createdAt": "2026-10-16T17:31:30.709774",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:30.709774",
      "updatedBy": "superadmin",
      "version": 0,
      "billingOrderId": 15,
      "code": "TAX_RULE_NOT_CONFIGURED",
      "detail": "No APPROVED tax rule (Q-04); tax = 0"
    }
  ],
  "payments": [],
  "invoice": null
}
```

</details>

### 12. Quote AUTO_DEALER, 8 users, 10% launch discount

```bash
curl -X POST "$CP/api/v1/platform/companies/CMP_113/orders" -H "Authorization: Bearer $PLATFORM_TOKEN" -H "Content-Type: application/json" -d '{"billingPeriod":"MONTHLY","bundles":["AUTO_DEALER"],"users":8,"storageGb":10,"manualDiscount":{"type":"PERCENT","value":10,"reason":"launch offer"}}'
```

**HTTP 201**

<details><summary>Response body (114 lines)</summary>

```json
{
  "order": {
    "id": 16,
    "createdAt": "2026-10-16T17:31:30.924387",
    "createdBy": "superadmin",
    "updatedAt": "2026-10-16T17:31:30.924387",
    "updatedBy": "superadmin",
    "version": 0,
    "orderNumber": "ORD/2026-27/00016",
    "companyId": 13,
    "subscriptionId": null,
    "orderKind": "NEW",
    "channel": "ADMIN",
    "billingPeriod": "MONTHLY",
    "currency": "INR",
    "requestedUsers": 8,
    "requestedStorageGb": 10,
    "subtotal": 560.0000,
    "discountTotal": 56.0000,
    "taxableTotal": 504.0000,
    "taxTotal": 0.0000,
    "otherChargesTotal": 0.0000,
    "roundOff": 0.0000,
    "grandTotal": 504.0000,
    "paymentStatus": "UNPAID",
    "status": "QUOTED",
    "calculationVersion": "poc-1",
    "inputJson": "{\"command\": {\"users\": 8, \"bundles\": [\"AUTO_DEALER\"], \"modules\": null, \"currency\": \"INR\", \"storageGb\": 10, \"billingPeriod\": \"MONTHLY\", \"idempotencyKey\": \"f16b90bf-d315-47b4-b2ad-62e65851e993\", \"manualDiscount\": {\"type\": \"PERCENT\", \"value\": 10, \"reason\": \"launch offer\"}}, \"existingModules\": []}",
    "validUntil": "2026-10-31T17:31:30.924387",
    "idempotencyKey": "f16b90bf-d315-47b4-b2ad-62e65851e993",
    "createdByActor": "superadmin"
  },
  "lines": [
    {
      "id": 26,
      "createdAt": "2026-10-16T17:31:30.92603",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:30.92603",
      "updatedBy": "superadmin",
      "version": 0,
      "billingOrderId": 16,
      "lineNo": 1,
      "lineType": "PLAN",
      "planVersionId": 32,
      "planPriceId": 71,
      "moduleId": null,
      "bundleId": 5,
      "description": "Bundle Auto Dealer Suite",
      "quantity": 1.0000,
      "unitPrice": 500.0000,
      "grossAmount": 500.0000,
      "lineDiscountAmount": 0.0000,
      "taxableAmount": 500.0000,
      "taxAmount": 0.0000,
      "lineTotal": 500.0000
    },
    {
      "id": 27,
      "createdAt": "2026-10-16T17:31:30.927036",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:30.927036",
      "updatedBy": "superadmin",
      "version": 0,
      "billingOrderId": 16,
      "lineNo": 2,
      "lineType": "ADDITIONAL_USERS",
      "planVersionId": 5,
      "planPriceId": 40,
      "moduleId": null,
      "bundleId": null,
      "description": "3 additional users",
      "quantity": 3.0000,
      "unitPrice": 20.0000,
      "grossAmount": 60.0000,
      "lineDiscountAmount": 0.0000,
      "taxableAmount": 60.0000,
      "taxAmount": 0.0000,
      "lineTotal": 60.0000
    }
  ],
  "discounts": [
    {
      "id": 3,
      "createdAt": "2026-10-16T17:31:30.93205",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:30.93205",
      "updatedBy": "superadmin",
      "version": 0,
      "billingOrderId": 16,
      "discountSource": "MANUAL",
      "discountType": "PERCENT",
      "discountValue": 10.0000,
      "baseAmount": 560.0000,
      "discountAmount": 56.0000,
      "reason": "launch offer",
      "sequenceNo": 1
    }
  ],
  "warnings": [
    {
      "id": 16,
      "createdAt": "2026-10-16T17:31:30.935803",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:30.935803",
      "updatedBy": "superadmin",
      "version": 0,
      "billingOrderId": 16,
      "code": "TAX_RULE_NOT_CONFIGURED",
      "detail": "No APPROVED tax rule (Q-04); tax = 0"
    }
  ],
  "payments": [],
  "invoice": null
}
```

</details>

### 13. List the company's orders

```bash
curl -X GET "$CP/api/v1/platform/companies/CMP_113/orders" -H "Authorization: Bearer $PLATFORM_TOKEN"
```

**HTTP 200**

<details><summary>Response body (64 lines)</summary>

```json
[
  {
    "id": 15,
    "createdAt": "2026-10-16T17:31:30.70678",
    "createdBy": "superadmin",
    "updatedAt": "2026-10-16T17:31:30.842832",
    "updatedBy": "superadmin",
    "version": 1,
    "orderNumber": "ORD/2026-27/00015",
    "companyId": 13,
    "subscriptionId": null,
    "orderKind": "NEW",
    "channel": "ADMIN",
    "billingPeriod": "MONTHLY",
    "currency": "INR",
    "requestedUsers": 5,
    "requestedStorageGb": 10,
    "subtotal": 150.0000,
    "discountTotal": 0.0000,
    "taxableTotal": 150.0000,
    "taxTotal": 0.0000,
    "otherChargesTotal": 0.0000,
    "roundOff": 0.0000,
    "grandTotal": 150.0000,
    "paymentStatus": "UNPAID",
    "status": "CANCELLED",
    "calculationVersion": "poc-1",
    "inputJson": "{\"command\": {\"users\": 5, \"bundles\": null, \"modules\": [\"vehicle\"], \"currency\": \"INR\", \"storageGb\": 10, \"billingPeriod\": \"MONTHLY\", \"idempotencyKey\": \"65b4ed7b-6209-40bb-8708-4dc7efd8f86f\", \"manualDiscount\": null}, \"existingModules\": []}",
    "validUntil": "2026-10-31T17:31:30.70678",
    "idempotencyKey": "65b4ed7b-6209-40bb-8708-4dc7efd8f86f",
    "createdByActor": "superadmin"
  },
  {
    "id": 16,
    "createdAt": "2026-10-16T17:31:30.924387",
    "createdBy": "superadmin",
    "updatedAt": "2026-10-16T17:31:30.924387",
    "updatedBy": "superadmin",
    "version": 0,
    "orderNumber": "ORD/2026-27/00016",
    "companyId": 13,
    "subscriptionId": null,
    "orderKind": "NEW",
    "channel": "ADMIN",
    "billingPeriod": "MONTHLY",
    "currency": "INR",
    "requestedUsers": 8,
    "requestedStorageGb": 10,
    "subtotal": 560.0000,
    "discountTotal": 56.0000,
    "taxableTotal": 504.0000,
    "taxTotal": 0.0000,
    "otherChargesTotal": 0.0000,
    "roundOff": 0.0000,
    "grandTotal": 504.0000,
    "paymentStatus": "UNPAID",
    "status": "QUOTED",
    "calculationVersion": "poc-1",
    "inputJson": "{\"command\": {\"users\": 8, \"bundles\": [\"AUTO_DEALER\"], \"modules\": null, \"currency\": \"INR\", \"storageGb\": 10, \"billingPeriod\": \"MONTHLY\", \"idempotencyKey\": \"f16b90bf-d315-47b4-b2ad-62e65851e993\", \"manualDiscount\": {\"type\": \"PERCENT\", \"value\": 10, \"reason\": \"launch offer\"}}, \"existingModules\": []}",
    "validUntil": "2026-10-31T17:31:30.924387",
    "idempotencyKey": "f16b90bf-d315-47b4-b2ad-62e65851e993",
    "createdByActor": "superadmin"
  }
]
```

</details>

### 14. Confirm the order (amounts frozen, subscription PENDING)

```bash
curl -X POST "$CP/api/v1/platform/orders/16/confirm" -H "Authorization: Bearer $PLATFORM_TOKEN"
```

**HTTP 200**

<details><summary>Response body (114 lines)</summary>

```json
{
  "order": {
    "id": 16,
    "createdAt": "2026-10-16T17:31:30.924387",
    "createdBy": "superadmin",
    "updatedAt": "2026-10-16T17:31:31.178705",
    "updatedBy": "superadmin",
    "version": 2,
    "orderNumber": "ORD/2026-27/00016",
    "companyId": 13,
    "subscriptionId": 13,
    "orderKind": "NEW",
    "channel": "ADMIN",
    "billingPeriod": "MONTHLY",
    "currency": "INR",
    "requestedUsers": 8,
    "requestedStorageGb": 10,
    "subtotal": 560.0000,
    "discountTotal": 56.0000,
    "taxableTotal": 504.0000,
    "taxTotal": 0.0000,
    "otherChargesTotal": 0.0000,
    "roundOff": 0.0000,
    "grandTotal": 504.0000,
    "paymentStatus": "UNPAID",
    "status": "AWAITING_PAYMENT",
    "calculationVersion": "poc-1",
    "inputJson": "{\"command\": {\"users\": 8, \"bundles\": [\"AUTO_DEALER\"], \"modules\": null, \"currency\": \"INR\", \"storageGb\": 10, \"billingPeriod\": \"MONTHLY\", \"idempotencyKey\": \"f16b90bf-d315-47b4-b2ad-62e65851e993\", \"manualDiscount\": {\"type\": \"PERCENT\", \"value\": 10, \"reason\": \"launch offer\"}}, \"existingModules\": []}",
    "validUntil": "2026-10-31T17:31:30.924387",
    "idempotencyKey": "f16b90bf-d315-47b4-b2ad-62e65851e993",
    "createdByActor": "superadmin"
  },
  "lines": [
    {
      "id": 26,
      "createdAt": "2026-10-16T17:31:30.92603",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:30.92603",
      "updatedBy": "superadmin",
      "version": 0,
      "billingOrderId": 16,
      "lineNo": 1,
      "lineType": "PLAN",
      "planVersionId": 32,
      "planPriceId": 71,
      "moduleId": null,
      "bundleId": 5,
      "description": "Bundle Auto Dealer Suite",
      "quantity": 1.0000,
      "unitPrice": 500.0000,
      "grossAmount": 500.0000,
      "lineDiscountAmount": 0.0000,
      "taxableAmount": 500.0000,
      "taxAmount": 0.0000,
      "lineTotal": 500.0000
    },
    {
      "id": 27,
      "createdAt": "2026-10-16T17:31:30.927036",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:30.927036",
      "updatedBy": "superadmin",
      "version": 0,
      "billingOrderId": 16,
      "lineNo": 2,
      "lineType": "ADDITIONAL_USERS",
      "planVersionId": 5,
      "planPriceId": 40,
      "moduleId": null,
      "bundleId": null,
      "description": "3 additional users",
      "quantity": 3.0000,
      "unitPrice": 20.0000,
      "grossAmount": 60.0000,
      "lineDiscountAmount": 0.0000,
      "taxableAmount": 60.0000,
      "taxAmount": 0.0000,
      "lineTotal": 60.0000
    }
  ],
  "discounts": [
    {
      "id": 3,
      "createdAt": "2026-10-16T17:31:30.93205",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:30.93205",
      "updatedBy": "superadmin",
      "version": 0,
      "billingOrderId": 16,
      "discountSource": "MANUAL",
      "discountType": "PERCENT",
      "discountValue": 10.0000,
      "baseAmount": 560.0000,
      "discountAmount": 56.0000,
      "reason": "launch offer",
      "sequenceNo": 1
    }
  ],
  "warnings": [
    {
      "id": 16,
      "createdAt": "2026-10-16T17:31:30.935803",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:30.935803",
      "updatedBy": "superadmin",
      "version": 0,
      "billingOrderId": 16,
      "code": "TAX_RULE_NOT_CONFIGURED",
      "detail": "No APPROVED tax rule (Q-04); tax = 0"
    }
  ],
  "payments": [],
  "invoice": null
}
```

</details>

### 15. Provision before full payment -> 409 SUBSCRIPTION_NOT_ACTIVE

```bash
curl -X POST "$CP/api/v1/platform/companies/CMP_113/provision" -H "Authorization: Bearer $PLATFORM_TOKEN"
```

**HTTP 409**

```json
{
  "type": "about:blank",
  "title": "SUBSCRIPTION_NOT_ACTIVE",
  "status": 409,
  "detail": "Provisioning needs an ACTIVE subscription: record and verify the full payment first (CD-07)",
  "instance": "/api/v1/platform/companies/CMP_113/provision",
  "code": "SUBSCRIPTION_NOT_ACTIVE"
}
```

### 16. Record a cash payment (half)

```bash
curl -X POST "$CP/api/v1/platform/orders/16/payments" -H "Authorization: Bearer $PLATFORM_TOKEN" -H "Content-Type: application/json" -d '{"mode":"CASH","amount":252.00}'
```

**HTTP 201**

<details><summary>Response body (142 lines)</summary>

```json
{
  "order": {
    "id": 16,
    "createdAt": "2026-10-16T17:31:30.924387",
    "createdBy": "superadmin",
    "updatedAt": "2026-10-16T17:31:31.178705",
    "updatedBy": "superadmin",
    "version": 2,
    "orderNumber": "ORD/2026-27/00016",
    "companyId": 13,
    "subscriptionId": 13,
    "orderKind": "NEW",
    "channel": "ADMIN",
    "billingPeriod": "MONTHLY",
    "currency": "INR",
    "requestedUsers": 8,
    "requestedStorageGb": 10,
    "subtotal": 560.0000,
    "discountTotal": 56.0000,
    "taxableTotal": 504.0000,
    "taxTotal": 0.0000,
    "otherChargesTotal": 0.0000,
    "roundOff": 0.0000,
    "grandTotal": 504.0000,
    "paymentStatus": "UNPAID",
    "status": "AWAITING_PAYMENT",
    "calculationVersion": "poc-1",
    "inputJson": "{\"command\": {\"users\": 8, \"bundles\": [\"AUTO_DEALER\"], \"modules\": null, \"currency\": \"INR\", \"storageGb\": 10, \"billingPeriod\": \"MONTHLY\", \"idempotencyKey\": \"f16b90bf-d315-47b4-b2ad-62e65851e993\", \"manualDiscount\": {\"type\": \"PERCENT\", \"value\": 10, \"reason\": \"launch offer\"}}, \"existingModules\": []}",
    "validUntil": "2026-10-31T17:31:30.924387",
    "idempotencyKey": "f16b90bf-d315-47b4-b2ad-62e65851e993",
    "createdByActor": "superadmin"
  },
  "lines": [
    {
      "id": 26,
      "createdAt": "2026-10-16T17:31:30.92603",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:30.92603",
      "updatedBy": "superadmin",
      "version": 0,
      "billingOrderId": 16,
      "lineNo": 1,
      "lineType": "PLAN",
      "planVersionId": 32,
      "planPriceId": 71,
      "moduleId": null,
      "bundleId": 5,
      "description": "Bundle Auto Dealer Suite",
      "quantity": 1.0000,
      "unitPrice": 500.0000,
      "grossAmount": 500.0000,
      "lineDiscountAmount": 0.0000,
      "taxableAmount": 500.0000,
      "taxAmount": 0.0000,
      "lineTotal": 500.0000
    },
    {
      "id": 27,
      "createdAt": "2026-10-16T17:31:30.927036",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:30.927036",
      "updatedBy": "superadmin",
      "version": 0,
      "billingOrderId": 16,
      "lineNo": 2,
      "lineType": "ADDITIONAL_USERS",
      "planVersionId": 5,
      "planPriceId": 40,
      "moduleId": null,
      "bundleId": null,
      "description": "3 additional users",
      "quantity": 3.0000,
      "unitPrice": 20.0000,
      "grossAmount": 60.0000,
      "lineDiscountAmount": 0.0000,
      "taxableAmount": 60.0000,
      "taxAmount": 0.0000,
      "lineTotal": 60.0000
    }
  ],
  "discounts": [
    {
      "id": 3,
      "createdAt": "2026-10-16T17:31:30.93205",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:30.93205",
      "updatedBy": "superadmin",
      "version": 0,
      "billingOrderId": 16,
      "discountSource": "MANUAL",
      "discountType": "PERCENT",
      "discountValue": 10.0000,
      "baseAmount": 560.0000,
      "discountAmount": 56.0000,
      "reason": "launch offer",
      "sequenceNo": 1
    }
  ],
  "warnings": [
    {
      "id": 16,
      "createdAt": "2026-10-16T17:31:30.935803",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:30.935803",
      "updatedBy": "superadmin",
      "version": 0,
      "billingOrderId": 16,
      "code": "TAX_RULE_NOT_CONFIGURED",
      "detail": "No APPROVED tax rule (Q-04); tax = 0"
    }
  ],
  "payments": [
    {
      "id": 16,
      "createdAt": "2026-10-16T17:31:31.368191",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:31.372805",
      "updatedBy": "superadmin",
      "version": 1,
      "paymentNumber": "PAY/2026-27/00016",
      "receiptNumber": "RCT/2026-27/00016",
      "companyId": 13,
      "billingOrderId": 16,
      "invoiceId": null,
      "paymentChannel": "MANUAL",
      "paymentMode": "CASH",
      "amount": 252.0000,
      "currency": "INR",
      "status": "CONFIRMED",
      "instrumentRef": null,
      "instrumentDate": null,
      "bankName": null,
      "verificationRequired": false,
      "receivedAt": "2026-10-16T17:31:31.368191",
      "confirmedAt": "2026-10-16T17:31:31.370806",
      "receivedBy": "superadmin",
      "rejectionReason": null,
      "idempotencyKey": "532ad196-d506-4f87-b864-6822c1219b8d"
    }
  ],
  "invoice": null
}
```

</details>

### 17. Record a cheque for the rest (PENDING_VERIFICATION)

```bash
curl -X POST "$CP/api/v1/platform/orders/16/payments" -H "Authorization: Bearer $PLATFORM_TOKEN" -H "Content-Type: application/json" -d '{"mode":"CHEQUE","amount":252.00,"instrumentRef":"CHQ-1792152089","bankName":"SBI"}'
```

**HTTP 201**

<details><summary>Response body (169 lines)</summary>

```json
{
  "order": {
    "id": 16,
    "createdAt": "2026-10-16T17:31:30.924387",
    "createdBy": "superadmin",
    "updatedAt": "2026-10-16T17:31:31.487932",
    "updatedBy": "superadmin",
    "version": 3,
    "orderNumber": "ORD/2026-27/00016",
    "companyId": 13,
    "subscriptionId": 13,
    "orderKind": "NEW",
    "channel": "ADMIN",
    "billingPeriod": "MONTHLY",
    "currency": "INR",
    "requestedUsers": 8,
    "requestedStorageGb": 10,
    "subtotal": 560.0000,
    "discountTotal": 56.0000,
    "taxableTotal": 504.0000,
    "taxTotal": 0.0000,
    "otherChargesTotal": 0.0000,
    "roundOff": 0.0000,
    "grandTotal": 504.0000,
    "paymentStatus": "PENDING_VERIFICATION",
    "status": "AWAITING_PAYMENT",
    "calculationVersion": "poc-1",
    "inputJson": "{\"command\": {\"users\": 8, \"bundles\": [\"AUTO_DEALER\"], \"modules\": null, \"currency\": \"INR\", \"storageGb\": 10, \"billingPeriod\": \"MONTHLY\", \"idempotencyKey\": \"f16b90bf-d315-47b4-b2ad-62e65851e993\", \"manualDiscount\": {\"type\": \"PERCENT\", \"value\": 10, \"reason\": \"launch offer\"}}, \"existingModules\": []}",
    "validUntil": "2026-10-31T17:31:30.924387",
    "idempotencyKey": "f16b90bf-d315-47b4-b2ad-62e65851e993",
    "createdByActor": "superadmin"
  },
  "lines": [
    {
      "id": 26,
      "createdAt": "2026-10-16T17:31:30.92603",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:30.92603",
      "updatedBy": "superadmin",
      "version": 0,
      "billingOrderId": 16,
      "lineNo": 1,
      "lineType": "PLAN",
      "planVersionId": 32,
      "planPriceId": 71,
      "moduleId": null,
      "bundleId": 5,
      "description": "Bundle Auto Dealer Suite",
      "quantity": 1.0000,
      "unitPrice": 500.0000,
      "grossAmount": 500.0000,
      "lineDiscountAmount": 0.0000,
      "taxableAmount": 500.0000,
      "taxAmount": 0.0000,
      "lineTotal": 500.0000
    },
    {
      "id": 27,
      "createdAt": "2026-10-16T17:31:30.927036",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:30.927036",
      "updatedBy": "superadmin",
      "version": 0,
      "billingOrderId": 16,
      "lineNo": 2,
      "lineType": "ADDITIONAL_USERS",
      "planVersionId": 5,
      "planPriceId": 40,
      "moduleId": null,
      "bundleId": null,
      "description": "3 additional users",
      "quantity": 3.0000,
      "unitPrice": 20.0000,
      "grossAmount": 60.0000,
      "lineDiscountAmount": 0.0000,
      "taxableAmount": 60.0000,
      "taxAmount": 0.0000,
      "lineTotal": 60.0000
    }
  ],
  "discounts": [
    {
      "id": 3,
      "createdAt": "2026-10-16T17:31:30.93205",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:30.93205",
      "updatedBy": "superadmin",
      "version": 0,
      "billingOrderId": 16,
      "discountSource": "MANUAL",
      "discountType": "PERCENT",
      "discountValue": 10.0000,
      "baseAmount": 560.0000,
      "discountAmount": 56.0000,
      "reason": "launch offer",
      "sequenceNo": 1
    }
  ],
  "warnings": [
    {
      "id": 16,
      "createdAt": "2026-10-16T17:31:30.935803",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:30.935803",
      "updatedBy": "superadmin",
      "version": 0,
      "billingOrderId": 16,
      "code": "TAX_RULE_NOT_CONFIGURED",
      "detail": "No APPROVED tax rule (Q-04); tax = 0"
    }
  ],
  "payments": [
    {
      "id": 16,
      "createdAt": "2026-10-16T17:31:31.368191",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:31.372805",
      "updatedBy": "superadmin",
      "version": 1,
      "paymentNumber": "PAY/2026-27/00016",
      "receiptNumber": "RCT/2026-27/00016",
      "companyId": 13,
      "billingOrderId": 16,
      "invoiceId": null,
      "paymentChannel": "MANUAL",
      "paymentMode": "CASH",
      "amount": 252.0000,
      "currency": "INR",
      "status": "CONFIRMED",
      "instrumentRef": null,
      "instrumentDate": null,
      "bankName": null,
      "verificationRequired": false,
      "receivedAt": "2026-10-16T17:31:31.368191",
      "confirmedAt": "2026-10-16T17:31:31.370806",
      "receivedBy": "superadmin",
      "rejectionReason": null,
      "idempotencyKey": "532ad196-d506-4f87-b864-6822c1219b8d"
    },
    {
      "id": 17,
      "createdAt": "2026-10-16T17:31:31.445403",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:31.481418",
      "updatedBy": "superadmin",
      "version": 1,
      "paymentNumber": "PAY/2026-27/00017",
      "receiptNumber": "RCT/2026-27/00017",
      "companyId": 13,
      "billingOrderId": 16,
      "invoiceId": null,
      "paymentChannel": "MANUAL",
      "paymentMode": "CHEQUE",
      "amount": 252.0000,
      "currency": "INR",
      "status": "PENDING_VERIFICATION",
      "instrumentRef": "CHQ-1792152089",
      "instrumentDate": null,
      "bankName": "SBI",
      "verificationRequired": true,
      "receivedAt": "2026-10-16T17:31:31.445403",
      "confirmedAt": null,
      "receivedBy": "superadmin",
      "rejectionReason": null,
      "idempotencyKey": "dc3b69d8-7b84-4046-bcfb-135f6ce8ea32"
    }
  ],
  "invoice": null
}
```

</details>

### 18. Over-payment is refused -> 409

```bash
curl -X POST "$CP/api/v1/platform/orders/16/payments" -H "Authorization: Bearer $PLATFORM_TOKEN" -H "Content-Type: application/json" -d '{"mode":"UPI","amount":1}'
```

**HTTP 409**

```json
{
  "type": "about:blank",
  "title": "PAYMENT_EXCEEDS_OUTSTANDING",
  "status": 409,
  "detail": "Outstanding amount is 0.0000",
  "instance": "/api/v1/platform/orders/16/payments",
  "code": "PAYMENT_EXCEEDS_OUTSTANDING"
}
```

### 19. The cheque cleared

```bash
curl -X POST "$CP/api/v1/platform/payments/17/verify" -H "Authorization: Bearer $PLATFORM_TOKEN"
```

**HTTP 200**

```json
{
  "id": 17,
  "createdAt": "2026-10-16T17:31:31.445403",
  "createdBy": "superadmin",
  "updatedAt": "2026-10-16T17:31:31.6891091",
  "updatedBy": "superadmin",
  "version": 3,
  "paymentNumber": "PAY/2026-27/00017",
  "receiptNumber": "RCT/2026-27/00017",
  "companyId": 13,
  "billingOrderId": 16,
  "invoiceId": 10,
  "paymentChannel": "MANUAL",
  "paymentMode": "CHEQUE",
  "amount": 252.0000,
  "currency": "INR",
  "status": "CONFIRMED",
  "instrumentRef": "CHQ-1792152089",
  "instrumentDate": null,
  "bankName": "SBI",
  "verificationRequired": true,
  "receivedAt": "2026-10-16T17:31:31.445403",
  "confirmedAt": "2026-10-16T17:31:31.6770179",
  "receivedBy": "superadmin",
  "rejectionReason": null,
  "idempotencyKey": "dc3b69d8-7b84-4046-bcfb-135f6ce8ea32"
}
```

### 20. Order: FULFILLED, invoice ISSUED_PAID

```bash
curl -X GET "$CP/api/v1/platform/orders/16" -H "Authorization: Bearer $PLATFORM_TOKEN"
```

**HTTP 200**

<details><summary>Response body (194 lines)</summary>

```json
{
  "order": {
    "id": 16,
    "createdAt": "2026-10-16T17:31:30.924387",
    "createdBy": "superadmin",
    "updatedAt": "2026-10-16T17:31:31.719675",
    "updatedBy": "superadmin",
    "version": 5,
    "orderNumber": "ORD/2026-27/00016",
    "companyId": 13,
    "subscriptionId": 13,
    "orderKind": "NEW",
    "channel": "ADMIN",
    "billingPeriod": "MONTHLY",
    "currency": "INR",
    "requestedUsers": 8,
    "requestedStorageGb": 10,
    "subtotal": 560.0000,
    "discountTotal": 56.0000,
    "taxableTotal": 504.0000,
    "taxTotal": 0.0000,
    "otherChargesTotal": 0.0000,
    "roundOff": 0.0000,
    "grandTotal": 504.0000,
    "paymentStatus": "PAID",
    "status": "FULFILLED",
    "calculationVersion": "poc-1",
    "inputJson": "{\"command\": {\"users\": 8, \"bundles\": [\"AUTO_DEALER\"], \"modules\": null, \"currency\": \"INR\", \"storageGb\": 10, \"billingPeriod\": \"MONTHLY\", \"idempotencyKey\": \"f16b90bf-d315-47b4-b2ad-62e65851e993\", \"manualDiscount\": {\"type\": \"PERCENT\", \"value\": 10, \"reason\": \"launch offer\"}}, \"existingModules\": []}",
    "validUntil": "2026-10-31T17:31:30.924387",
    "idempotencyKey": "f16b90bf-d315-47b4-b2ad-62e65851e993",
    "createdByActor": "superadmin"
  },
  "lines": [
    {
      "id": 26,
      "createdAt": "2026-10-16T17:31:30.92603",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:30.92603",
      "updatedBy": "superadmin",
      "version": 0,
      "billingOrderId": 16,
      "lineNo": 1,
      "lineType": "PLAN",
      "planVersionId": 32,
      "planPriceId": 71,
      "moduleId": null,
      "bundleId": 5,
      "description": "Bundle Auto Dealer Suite",
      "quantity": 1.0000,
      "unitPrice": 500.0000,
      "grossAmount": 500.0000,
      "lineDiscountAmount": 0.0000,
      "taxableAmount": 500.0000,
      "taxAmount": 0.0000,
      "lineTotal": 500.0000
    },
    {
      "id": 27,
      "createdAt": "2026-10-16T17:31:30.927036",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:30.927036",
      "updatedBy": "superadmin",
      "version": 0,
      "billingOrderId": 16,
      "lineNo": 2,
      "lineType": "ADDITIONAL_USERS",
      "planVersionId": 5,
      "planPriceId": 40,
      "moduleId": null,
      "bundleId": null,
      "description": "3 additional users",
      "quantity": 3.0000,
      "unitPrice": 20.0000,
      "grossAmount": 60.0000,
      "lineDiscountAmount": 0.0000,
      "taxableAmount": 60.0000,
      "taxAmount": 0.0000,
      "lineTotal": 60.0000
    }
  ],
  "discounts": [
    {
      "id": 3,
      "createdAt": "2026-10-16T17:31:30.93205",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:30.93205",
      "updatedBy": "superadmin",
      "version": 0,
      "billingOrderId": 16,
      "discountSource": "MANUAL",
      "discountType": "PERCENT",
      "discountValue": 10.0000,
      "baseAmount": 560.0000,
      "discountAmount": 56.0000,
      "reason": "launch offer",
      "sequenceNo": 1
    }
  ],
  "warnings": [
    {
      "id": 16,
      "createdAt": "2026-10-16T17:31:30.935803",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:30.935803",
      "updatedBy": "superadmin",
      "version": 0,
      "billingOrderId": 16,
      "code": "TAX_RULE_NOT_CONFIGURED",
      "detail": "No APPROVED tax rule (Q-04); tax = 0"
    }
  ],
  "payments": [
    {
      "id": 16,
      "createdAt": "2026-10-16T17:31:31.368191",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:31.689109",
      "updatedBy": "superadmin",
      "version": 2,
      "paymentNumber": "PAY/2026-27/00016",
      "receiptNumber": "RCT/2026-27/00016",
      "companyId": 13,
      "billingOrderId": 16,
      "invoiceId": 10,
      "paymentChannel": "MANUAL",
      "paymentMode": "CASH",
      "amount": 252.0000,
      "currency": "INR",
      "status": "CONFIRMED",
      "instrumentRef": null,
      "instrumentDate": null,
      "bankName": null,
      "verificationRequired": false,
      "receivedAt": "2026-10-16T17:31:31.368191",
      "confirmedAt": "2026-10-16T17:31:31.370806",
      "receivedBy": "superadmin",
      "rejectionReason": null,
      "idempotencyKey": "532ad196-d506-4f87-b864-6822c1219b8d"
    },
    {
      "id": 17,
      "createdAt": "2026-10-16T17:31:31.445403",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:31.689109",
      "updatedBy": "superadmin",
      "version": 3,
      "paymentNumber": "PAY/2026-27/00017",
      "receiptNumber": "RCT/2026-27/00017",
      "companyId": 13,
      "billingOrderId": 16,
      "invoiceId": 10,
      "paymentChannel": "MANUAL",
      "paymentMode": "CHEQUE",
      "amount": 252.0000,
      "currency": "INR",
      "status": "CONFIRMED",
      "instrumentRef": "CHQ-1792152089",
      "instrumentDate": null,
      "bankName": "SBI",
      "verificationRequired": true,
      "receivedAt": "2026-10-16T17:31:31.445403",
      "confirmedAt": "2026-10-16T17:31:31.677018",
      "receivedBy": "superadmin",
      "rejectionReason": null,
      "idempotencyKey": "dc3b69d8-7b84-4046-bcfb-135f6ce8ea32"
    }
  ],
  "invoice": {
    "id": 10,
    "createdAt": "2026-10-16T17:31:31.683019",
    "createdBy": "superadmin",
    "updatedAt": "2026-10-16T17:31:31.683019",
    "updatedBy": "superadmin",
    "version": 0,
    "invoiceNumber": "INV/2026-27/00010",
    "companyId": 13,
    "billingOrderId": 16,
    "invoiceType": "TAX_INVOICE",
    "issueDate": "2026-10-16",
    "currency": "INR",
    "subtotal": 560.0000,
    "discountTotal": 56.0000,
    "taxableTotal": 504.0000,
    "taxTotal": 0.0000,
    "otherChargesTotal": 0.0000,
    "roundOff": 0.0000,
    "grandTotal": 504.0000,
    "amountPaid": 504.0000,
    "amountCredited": 0.0000,
    "balanceDue": 0.0000,
    "status": "ISSUED_PAID",
    "issuedAt": "2026-10-16T17:31:31.683019"
  }
}
```

</details>

### 21. Provision: realm co-n + Company Super Admin, DB per module, Liquibase, buckets

```bash
curl -X POST "$CP/api/v1/platform/companies/CMP_113/provision" -H "Authorization: Bearer $PLATFORM_TOKEN"
```

**HTTP 202**

<details><summary>Response body (404 lines)</summary>

```json
{
  "job": {
    "id": 10,
    "createdAt": "2026-10-16T17:31:31.8538564",
    "createdBy": "superadmin",
    "updatedAt": "2026-10-16T17:31:31.8538564",
    "updatedBy": "superadmin",
    "version": 0,
    "companyId": 13,
    "jobType": "CREATE_COMPANY",
    "mode": "SEEDED",
    "status": "QUEUED",
    "attempt": 0,
    "nextAttemptAt": null,
    "lockedBy": null,
    "lockedUntil": null,
    "activeGuard": 1,
    "requestedBy": "superadmin",
    "lastError": null,
    "completedAt": null
  },
  "steps": [
    {
      "id": 151,
      "createdAt": "2026-10-16T17:31:31.85585",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:31.85585",
      "updatedBy": "superadmin",
      "version": 0,
      "jobId": 10,
      "sequenceNo": 1,
      "stepCode": "RESERVE_NAMES",
      "resourceType": null,
      "moduleId": null,
      "resourceName": null,
      "status": "PENDING",
      "attempts": 0,
      "lastError": null,
      "startedAt": null,
      "completedAt": null,
      "done": false
    },
    {
      "id": 152,
      "createdAt": "2026-10-16T17:31:31.859341",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:31.859341",
      "updatedBy": "superadmin",
      "version": 0,
      "jobId": 10,
      "sequenceNo": 2,
      "stepCode": "CREATE_REALM",
      "resourceType": "IDP_REALM",
      "moduleId": null,
      "resourceName": "co-113",
      "status": "PENDING",
      "attempts": 0,
      "lastError": null,
      "startedAt": null,
      "completedAt": null,
      "done": false
    },
    {
      "id": 153,
      "createdAt": "2026-10-16T17:31:31.860348",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:31.860348",
      "updatedBy": "superadmin",
      "version": 0,
      "jobId": 10,
      "sequenceNo": 3,
      "stepCode": "CREATE_DATABASE",
      "resourceType": "DATABASE",
      "moduleId": 18,
      "resourceName": "cmp_113_vehicle_db",
      "status": "PENDING",
      "attempts": 0,
      "lastError": null,
      "startedAt": null,
      "completedAt": null,
      "done": false
    },
    {
      "id": 154,
      "createdAt": "2026-10-16T17:31:31.861355",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:31.861355",
      "updatedBy": "superadmin",
      "version": 0,
      "jobId": 10,
      "sequenceNo": 4,
      "stepCode": "CREATE_DATABASE",
      "resourceType": "DATABASE",
      "moduleId": 19,
      "resourceName": "cmp_113_inventory_db",
      "status": "PENDING",
      "attempts": 0,
      "lastError": null,
      "startedAt": null,
      "completedAt": null,
      "done": false
    },
    {
      "id": 155,
      "createdAt": "2026-10-16T17:31:31.862347",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:31.862347",
      "updatedBy": "superadmin",
      "version": 0,
      "jobId": 10,
      "sequenceNo": 5,
      "stepCode": "CREATE_DATABASE",
      "resourceType": "DATABASE",
      "moduleId": 20,
      "resourceName": "cmp_113_booking_db",
      "status": "PENDING",
      "attempts": 0,
      "lastError": null,
      "startedAt": null,
      "completedAt": null,
      "done": false
    },
    {
      "id": 156,
      "createdAt": "2026-10-16T17:31:31.863347",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:31.863347",
      "updatedBy": "superadmin",
      "version": 0,
      "jobId": 10,
      "sequenceNo": 6,
      "stepCode": "CREATE_DATABASE",
      "resourceType": "DATABASE",
      "moduleId": 16,
      "resourceName": "cmp_113_attachment_db",
      "status": "PENDING",
      "attempts": 0,
      "lastError": null,
      "startedAt": null,
      "completedAt": null,
      "done": false
    },
    {
      "id": 157,
      "createdAt": "2026-10-16T17:31:31.864347",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:31.864347",
      "updatedBy": "superadmin",
      "version": 0,
      "jobId": 10,
      "sequenceNo": 7,
      "stepCode": "CREATE_DATABASE",
      "resourceType": "DATABASE",
      "moduleId": 17,
      "resourceName": "cmp_113_notification_db",
      "status": "PENDING",
      "attempts": 0,
      "lastError": null,
      "startedAt": null,
      "completedAt": null,
      "done": false
    },
    {
      "id": 158,
      "createdAt": "2026-10-16T17:31:31.865347",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:31.865347",
      "updatedBy": "superadmin",
      "version": 0,
      "jobId": 10,
      "sequenceNo": 8,
      "stepCode": "MIGRATE_SCHEMA",
      "resourceType": "DATABASE",
      "moduleId": 18,
      "resourceName": "cmp_113_vehicle_db",
      "status": "PENDING",
      "attempts": 0,
      "lastError": null,
      "startedAt": null,
      "completedAt": null,
      "done": false
    },
    {
      "id": 159,
      "createdAt": "2026-10-16T17:31:31.866347",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:31.866347",
      "updatedBy": "superadmin",
      "version": 0,
      "jobId": 10,
      "sequenceNo": 9,
      "stepCode": "MIGRATE_SCHEMA",
      "resourceType": "DATABASE",
      "moduleId": 19,
      "resourceName": "cmp_113_inventory_db",
      "status": "PENDING",
      "attempts": 0,
      "lastError": null,
      "startedAt": null,
      "completedAt": null,
      "done": false
    },
    {
      "id": 160,
      "createdAt": "2026-10-16T17:31:31.86735",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:31.86735",
      "updatedBy": "superadmin",
      "version": 0,
      "jobId": 10,
      "sequenceNo": 10,
      "stepCode": "MIGRATE_SCHEMA",
      "resourceType": "DATABASE",
      "moduleId": 20,
      "resourceName": "cmp_113_booking_db",
      "status": "PENDING",
      "attempts": 0,
      "lastError": null,
      "startedAt": null,
      "completedAt": null,
      "done": false
    },
    {
      "id": 161,
      "createdAt": "2026-10-16T17:31:31.868348",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:31.868348",
      "updatedBy": "superadmin",
      "version": 0,
      "jobId": 10,
      "sequenceNo": 11,
      "stepCode": "MIGRATE_SCHEMA",
      "resourceType": "DATABASE",
      "moduleId": 16,
      "resourceName": "cmp_113_attachment_db",
      "status": "PENDING",
      "attempts": 0,
      "lastError": null,
      "startedAt": null,
      "completedAt": null,
      "done": false
    },
    {
      "id": 162,
      "createdAt": "2026-10-16T17:31:31.869351",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:31.869351",
      "updatedBy": "superadmin",
      "version": 0,
      "jobId": 10,
      "sequenceNo": 12,
      "stepCode": "MIGRATE_SCHEMA",
      "resourceType": "DATABASE",
      "moduleId": 17,
      "resourceName": "cmp_113_notification_db",
      "status": "PENDING",
      "attempts": 0,
      "lastError": null,
      "startedAt": null,
      "completedAt": null,
      "done": false
    },
    {
      "id": 163,
      "createdAt": "2026-10-16T17:31:31.871348",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:31.871348",
      "updatedBy": "superadmin",
      "version": 0,
      "jobId": 10,
      "sequenceNo": 13,
      "stepCode": "CREATE_BUCKET",
      "resourceType": "BUCKET",
      "moduleId": null,
      "resourceName": "cmp-113-control-panel-bucket",
      "status": "PENDING",
      "attempts": 0,
      "lastError": null,
      "startedAt": null,
      "completedAt": null,
      "done": false
    },
    {
      "id": 164,
      "createdAt": "2026-10-16T17:31:31.872353",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:31.872353",
      "updatedBy": "superadmin",
      "version": 0,
      "jobId": 10,
      "sequenceNo": 14,
      "stepCode": "CREATE_BUCKET",
      "resourceType": "BUCKET",
      "moduleId": 18,
      "resourceName": "cmp-113-vehicle-bucket",
      "status": "PENDING",
      "attempts": 0,
      "lastError": null,
      "startedAt": null,
      "completedAt": null,
      "done": false
    },
    {
      "id": 165,
      "createdAt": "2026-10-16T17:31:31.873353",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:31.873353",
      "updatedBy": "superadmin",
      "version": 0,
      "jobId": 10,
      "sequenceNo": 15,
      "stepCode": "CREATE_BUCKET",
      "resourceType": "BUCKET",
      "moduleId": 19,
      "resourceName": "cmp-113-inventory-bucket",
      "status": "PENDING",
      "attempts": 0,
      "lastError": null,
      "startedAt": null,
      "completedAt": null,
      "done": false
    },
    {
      "id": 166,
      "createdAt": "2026-10-16T17:31:31.874352",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:31.874352",
      "updatedBy": "superadmin",
      "version": 0,
      "jobId": 10,
      "sequenceNo": 16,
      "stepCode": "CREATE_BUCKET",
      "resourceType": "BUCKET",
      "moduleId": 20,
      "resourceName": "cmp-113-booking-bucket",
      "status": "PENDING",
      "attempts": 0,
      "lastError": null,
      "startedAt": null,
      "completedAt": null,
      "done": false
    },
    {
      "id": 167,
      "createdAt": "2026-10-16T17:31:31.875353",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:31.875353",
      "updatedBy": "superadmin",
      "version": 0,
      "jobId": 10,
      "sequenceNo": 17,
      "stepCode": "INVITE_COMPANY_SUPER_ADMIN",
      "resourceType": null,
      "moduleId": null,
      "resourceName": null,
      "status": "PENDING",
      "attempts": 0,
      "lastError": null,
      "startedAt": null,
      "completedAt": null,
      "done": false
    },
    {
      "id": 168,
      "createdAt": "2026-10-16T17:31:31.876352",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:31.876352",
      "updatedBy": "superadmin",
      "version": 0,
      "jobId": 10,
      "sequenceNo": 18,
      "stepCode": "ACTIVATE",
      "resourceType": null,
      "moduleId": null,
      "resourceName": null,
      "status": "PENDING",
      "attempts": 0,
      "lastError": null,
      "startedAt": null,
      "completedAt": null,
      "done": false
    },
    {
      "id": 169,
      "createdAt": "2026-10-16T17:31:31.877361",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:31.877361",
      "updatedBy": "superadmin",
      "version": 0,
      "jobId": 10,
      "sequenceNo": 19,
      "stepCode": "PUBLISH_CACHES",
      "resourceType": null,
      "moduleId": null,
      "resourceName": null,
      "status": "PENDING",
      "attempts": 0,
      "lastError": null,
      "startedAt": null,
      "completedAt": null,
... (4 more lines; run scripts/curl-flow.sh to see everything)
```

</details>

### 22. Provisioning job and its steps

```bash
curl -X GET "$CP/api/v1/platform/companies/CMP_113/provisioning-jobs" -H "Authorization: Bearer $PLATFORM_TOKEN"
```

**HTTP 200**

<details><summary>Response body (406 lines)</summary>

```json
[
  {
    "job": {
      "id": 10,
      "createdAt": "2026-10-16T17:31:31.853856",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:35.839137",
      "updatedBy": "system",
      "version": 2,
      "companyId": 13,
      "jobType": "CREATE_COMPANY",
      "mode": "SEEDED",
      "status": "COMPLETED",
      "attempt": 0,
      "nextAttemptAt": null,
      "lockedBy": null,
      "lockedUntil": null,
      "activeGuard": null,
      "requestedBy": "superadmin",
      "lastError": null,
      "completedAt": "2026-10-16T17:31:35.839137"
    },
    "steps": [
      {
        "id": 151,
        "createdAt": "2026-10-16T17:31:31.85585",
        "createdBy": "superadmin",
        "updatedAt": "2026-10-16T17:31:33.610601",
        "updatedBy": "system",
        "version": 2,
        "jobId": 10,
        "sequenceNo": 1,
        "stepCode": "RESERVE_NAMES",
        "resourceType": null,
        "moduleId": null,
        "resourceName": null,
        "status": "COMPLETED",
        "attempts": 1,
        "lastError": null,
        "startedAt": "2026-10-16T17:31:33.583301",
        "completedAt": "2026-10-16T17:31:33.610601",
        "done": true
      },
      {
        "id": 152,
        "createdAt": "2026-10-16T17:31:31.859341",
        "createdBy": "superadmin",
        "updatedAt": "2026-10-16T17:31:34.32979",
        "updatedBy": "system",
        "version": 2,
        "jobId": 10,
        "sequenceNo": 2,
        "stepCode": "CREATE_REALM",
        "resourceType": "IDP_REALM",
        "moduleId": null,
        "resourceName": "co-113",
        "status": "COMPLETED",
        "attempts": 1,
        "lastError": null,
        "startedAt": "2026-10-16T17:31:33.624923",
        "completedAt": "2026-10-16T17:31:34.32979",
        "done": true
      },
      {
        "id": 153,
        "createdAt": "2026-10-16T17:31:31.860348",
        "createdBy": "superadmin",
        "updatedAt": "2026-10-16T17:31:34.381644",
        "updatedBy": "system",
        "version": 2,
        "jobId": 10,
        "sequenceNo": 3,
        "stepCode": "CREATE_DATABASE",
        "resourceType": "DATABASE",
        "moduleId": 18,
        "resourceName": "cmp_113_vehicle_db",
        "status": "COMPLETED",
        "attempts": 1,
        "lastError": null,
        "startedAt": "2026-10-16T17:31:34.350644",
        "completedAt": "2026-10-16T17:31:34.380644",
        "done": true
      },
      {
        "id": 154,
        "createdAt": "2026-10-16T17:31:31.861355",
        "createdBy": "superadmin",
        "updatedAt": "2026-10-16T17:31:34.426682",
        "updatedBy": "system",
        "version": 2,
        "jobId": 10,
        "sequenceNo": 4,
        "stepCode": "CREATE_DATABASE",
        "resourceType": "DATABASE",
        "moduleId": 19,
        "resourceName": "cmp_113_inventory_db",
        "status": "COMPLETED",
        "attempts": 1,
        "lastError": null,
        "startedAt": "2026-10-16T17:31:34.398654",
        "completedAt": "2026-10-16T17:31:34.426682",
        "done": true
      },
      {
        "id": 155,
        "createdAt": "2026-10-16T17:31:31.862347",
        "createdBy": "superadmin",
        "updatedAt": "2026-10-16T17:31:34.464249",
        "updatedBy": "system",
        "version": 2,
        "jobId": 10,
        "sequenceNo": 5,
        "stepCode": "CREATE_DATABASE",
        "resourceType": "DATABASE",
        "moduleId": 20,
        "resourceName": "cmp_113_booking_db",
        "status": "COMPLETED",
        "attempts": 1,
        "lastError": null,
        "startedAt": "2026-10-16T17:31:34.441691",
        "completedAt": "2026-10-16T17:31:34.464249",
        "done": true
      },
      {
        "id": 156,
        "createdAt": "2026-10-16T17:31:31.863347",
        "createdBy": "superadmin",
        "updatedAt": "2026-10-16T17:31:34.498657",
        "updatedBy": "system",
        "version": 2,
        "jobId": 10,
        "sequenceNo": 6,
        "stepCode": "CREATE_DATABASE",
        "resourceType": "DATABASE",
        "moduleId": 16,
        "resourceName": "cmp_113_attachment_db",
        "status": "COMPLETED",
        "attempts": 1,
        "lastError": null,
        "startedAt": "2026-10-16T17:31:34.477372",
        "completedAt": "2026-10-16T17:31:34.498657",
        "done": true
      },
      {
        "id": 157,
        "createdAt": "2026-10-16T17:31:31.864347",
        "createdBy": "superadmin",
        "updatedAt": "2026-10-16T17:31:34.532472",
        "updatedBy": "system",
        "version": 2,
        "jobId": 10,
        "sequenceNo": 7,
        "stepCode": "CREATE_DATABASE",
        "resourceType": "DATABASE",
        "moduleId": 17,
        "resourceName": "cmp_113_notification_db",
        "status": "COMPLETED",
        "attempts": 1,
        "lastError": null,
        "startedAt": "2026-10-16T17:31:34.513492",
        "completedAt": "2026-10-16T17:31:34.532472",
        "done": true
      },
      {
        "id": 158,
        "createdAt": "2026-10-16T17:31:31.865347",
        "createdBy": "superadmin",
        "updatedAt": "2026-10-16T17:31:34.724665",
        "updatedBy": "system",
        "version": 2,
        "jobId": 10,
        "sequenceNo": 8,
        "stepCode": "MIGRATE_SCHEMA",
        "resourceType": "DATABASE",
        "moduleId": 18,
        "resourceName": "cmp_113_vehicle_db",
        "status": "COMPLETED",
        "attempts": 1,
        "lastError": null,
        "startedAt": "2026-10-16T17:31:34.545472",
        "completedAt": "2026-10-16T17:31:34.724665",
        "done": true
      },
      {
        "id": 159,
        "createdAt": "2026-10-16T17:31:31.866347",
        "createdBy": "superadmin",
        "updatedAt": "2026-10-16T17:31:34.943854",
        "updatedBy": "system",
        "version": 2,
        "jobId": 10,
        "sequenceNo": 9,
        "stepCode": "MIGRATE_SCHEMA",
        "resourceType": "DATABASE",
        "moduleId": 19,
        "resourceName": "cmp_113_inventory_db",
        "status": "COMPLETED",
        "attempts": 1,
        "lastError": null,
        "startedAt": "2026-10-16T17:31:34.738335",
        "completedAt": "2026-10-16T17:31:34.943854",
        "done": true
      },
      {
        "id": 160,
        "createdAt": "2026-10-16T17:31:31.86735",
        "createdBy": "superadmin",
        "updatedAt": "2026-10-16T17:31:35.146277",
        "updatedBy": "system",
        "version": 2,
        "jobId": 10,
        "sequenceNo": 10,
        "stepCode": "MIGRATE_SCHEMA",
        "resourceType": "DATABASE",
        "moduleId": 20,
        "resourceName": "cmp_113_booking_db",
        "status": "COMPLETED",
        "attempts": 1,
        "lastError": null,
        "startedAt": "2026-10-16T17:31:34.959318",
        "completedAt": "2026-10-16T17:31:35.146277",
        "done": true
      },
      {
        "id": 161,
        "createdAt": "2026-10-16T17:31:31.868348",
        "createdBy": "superadmin",
        "updatedAt": "2026-10-16T17:31:35.345746",
        "updatedBy": "system",
        "version": 2,
        "jobId": 10,
        "sequenceNo": 11,
        "stepCode": "MIGRATE_SCHEMA",
        "resourceType": "DATABASE",
        "moduleId": 16,
        "resourceName": "cmp_113_attachment_db",
        "status": "COMPLETED",
        "attempts": 1,
        "lastError": null,
        "startedAt": "2026-10-16T17:31:35.160451",
        "completedAt": "2026-10-16T17:31:35.345746",
        "done": true
      },
      {
        "id": 162,
        "createdAt": "2026-10-16T17:31:31.869351",
        "createdBy": "superadmin",
        "updatedAt": "2026-10-16T17:31:35.532721",
        "updatedBy": "system",
        "version": 2,
        "jobId": 10,
        "sequenceNo": 12,
        "stepCode": "MIGRATE_SCHEMA",
        "resourceType": "DATABASE",
        "moduleId": 17,
        "resourceName": "cmp_113_notification_db",
        "status": "COMPLETED",
        "attempts": 1,
        "lastError": null,
        "startedAt": "2026-10-16T17:31:35.359005",
        "completedAt": "2026-10-16T17:31:35.532721",
        "done": true
      },
      {
        "id": 163,
        "createdAt": "2026-10-16T17:31:31.871348",
        "createdBy": "superadmin",
        "updatedAt": "2026-10-16T17:31:35.569916",
        "updatedBy": "system",
        "version": 2,
        "jobId": 10,
        "sequenceNo": 13,
        "stepCode": "CREATE_BUCKET",
        "resourceType": "BUCKET",
        "moduleId": null,
        "resourceName": "cmp-113-control-panel-bucket",
        "status": "COMPLETED",
        "attempts": 1,
        "lastError": null,
        "startedAt": "2026-10-16T17:31:35.546015",
        "completedAt": "2026-10-16T17:31:35.568916",
        "done": true
      },
      {
        "id": 164,
        "createdAt": "2026-10-16T17:31:31.872353",
        "createdBy": "superadmin",
        "updatedAt": "2026-10-16T17:31:35.608309",
        "updatedBy": "system",
        "version": 2,
        "jobId": 10,
        "sequenceNo": 14,
        "stepCode": "CREATE_BUCKET",
        "resourceType": "BUCKET",
        "moduleId": 18,
        "resourceName": "cmp-113-vehicle-bucket",
        "status": "COMPLETED",
        "attempts": 1,
        "lastError": null,
        "startedAt": "2026-10-16T17:31:35.58293",
        "completedAt": "2026-10-16T17:31:35.608309",
        "done": true
      },
      {
        "id": 165,
        "createdAt": "2026-10-16T17:31:31.873353",
        "createdBy": "superadmin",
        "updatedAt": "2026-10-16T17:31:35.6437",
        "updatedBy": "system",
        "version": 2,
        "jobId": 10,
        "sequenceNo": 15,
        "stepCode": "CREATE_BUCKET",
        "resourceType": "BUCKET",
        "moduleId": 19,
        "resourceName": "cmp-113-inventory-bucket",
        "status": "COMPLETED",
        "attempts": 1,
        "lastError": null,
        "startedAt": "2026-10-16T17:31:35.621055",
        "completedAt": "2026-10-16T17:31:35.642701",
        "done": true
      },
      {
        "id": 166,
        "createdAt": "2026-10-16T17:31:31.874352",
        "createdBy": "superadmin",
        "updatedAt": "2026-10-16T17:31:35.681294",
        "updatedBy": "system",
        "version": 2,
        "jobId": 10,
        "sequenceNo": 16,
        "stepCode": "CREATE_BUCKET",
        "resourceType": "BUCKET",
        "moduleId": 20,
        "resourceName": "cmp-113-booking-bucket",
        "status": "COMPLETED",
        "attempts": 1,
        "lastError": null,
        "startedAt": "2026-10-16T17:31:35.656703",
        "completedAt": "2026-10-16T17:31:35.681294",
        "done": true
      },
      {
        "id": 167,
        "createdAt": "2026-10-16T17:31:31.875353",
        "createdBy": "superadmin",
        "updatedAt": "2026-10-16T17:31:35.734771",
        "updatedBy": "system",
        "version": 2,
        "jobId": 10,
        "sequenceNo": 17,
        "stepCode": "INVITE_COMPANY_SUPER_ADMIN",
        "resourceType": null,
        "moduleId": null,
        "resourceName": null,
        "status": "COMPLETED",
        "attempts": 1,
        "lastError": null,
        "startedAt": "2026-10-16T17:31:35.694295",
        "completedAt": "2026-10-16T17:31:35.734771",
        "done": true
      },
      {
        "id": 168,
        "createdAt": "2026-10-16T17:31:31.876352",
        "createdBy": "superadmin",
        "updatedAt": "2026-10-16T17:31:35.795798",
        "updatedBy": "system",
        "version": 2,
        "jobId": 10,
        "sequenceNo": 18,
        "stepCode": "ACTIVATE",
        "resourceType": null,
        "moduleId": null,
        "resourceName": null,
        "status": "COMPLETED",
        "attempts": 1,
        "lastError": null,
        "startedAt": "2026-10-16T17:31:35.748626",
        "completedAt": "2026-10-16T17:31:35.795798",
        "done": true
      },
      {
        "id": 169,
        "createdAt": "2026-10-16T17:31:31.877361",
        "createdBy": "superadmin",
        "updatedAt": "2026-10-16T17:31:35.825786",
        "updatedBy": "system",
        "version": 2,
        "jobId": 10,
        "sequenceNo": 19,
        "stepCode": "PUBLISH_CACHES",
        "resourceType": null,
        "moduleId": null,
        "resourceName": null,
        "status": "COMPLETED",
        "attempts": 1,
        "lastError": null,
        "startedAt": "2026-10-16T17:31:35.808892",
... (6 more lines; run scripts/curl-flow.sh to see everything)
```

</details>

### 23. Retry is only for FAILED jobs -> 409

```bash
curl -X POST "$CP/api/v1/platform/provisioning-jobs/10/retry" -H "Authorization: Bearer $PLATFORM_TOKEN"
```

**HTTP 409**

```json
{
  "type": "about:blank",
  "title": "INVALID_JOB_STATE",
  "status": 409,
  "detail": "Job is COMPLETED",
  "instance": "/api/v1/platform/provisioning-jobs/10/retry",
  "code": "INVALID_JOB_STATE"
}
```

### 24. Overview: subscription, entitlements, allowances, resources, Redis projection

```bash
curl -X GET "$CP/api/v1/platform/companies/CMP_113/overview" -H "Authorization: Bearer $PLATFORM_TOKEN"
```

**HTTP 200**

<details><summary>Response body (228 lines)</summary>

```json
{
  "companyCode": "CMP_113",
  "status": "ACTIVE",
  "subscriptions": [
    {
      "subscription": {
        "id": 13,
        "createdAt": "2026-10-16T17:31:31.175188",
        "createdBy": "superadmin",
        "updatedAt": "2026-10-16T17:31:31.689109",
        "updatedBy": "superadmin",
        "version": 1,
        "subscriptionNumber": "SUB/2026-27/00013",
        "companyId": 13,
        "billingPeriod": "MONTHLY",
        "currency": "INR",
        "status": "ACTIVE",
        "subscriptionType": "PAID",
        "startDate": "2026-10-16",
        "endDate": "2026-11-15",
        "autoRenew": false,
        "cancelAtPeriodEnd": false,
        "sourceOrderId": 16,
        "endReason": null
      },
      "items": [
        {
          "id": 23,
          "createdAt": "2026-10-16T17:31:31.176189",
          "createdBy": "superadmin",
          "updatedAt": "2026-10-16T17:31:31.689109",
          "updatedBy": "superadmin",
          "version": 1,
          "subscriptionId": 13,
          "itemType": "PLAN",
          "planVersionId": 32,
          "planPriceId": 71,
          "moduleId": null,
          "bundleId": 5,
          "quantity": 1.0000,
          "unitPriceSnapshot": 500.0000,
          "sourceOrderLineId": 26,
          "status": "ACTIVE",
          "effectiveFrom": "2026-10-16",
          "effectiveTo": null
        },
        {
          "id": 24,
          "createdAt": "2026-10-16T17:31:31.176189",
          "createdBy": "superadmin",
          "updatedAt": "2026-10-16T17:31:31.689109",
          "updatedBy": "superadmin",
          "version": 1,
          "subscriptionId": 13,
          "itemType": "ADDITIONAL_USERS",
          "planVersionId": 5,
          "planPriceId": 40,
          "moduleId": null,
          "bundleId": null,
          "quantity": 3.0000,
          "unitPriceSnapshot": 20.0000,
          "sourceOrderLineId": 27,
          "status": "ACTIVE",
          "effectiveFrom": "2026-10-16",
          "effectiveTo": null
        }
      ]
    }
  ],
  "entitlements": [
    {
      "module": "vehicle",
      "source": "PLAN_BUNDLE",
      "sourceKey": "ITEM:23",
      "charged": true,
      "status": "ACTIVE",
      "validFrom": "2026-10-16",
      "validTo": null
    },
    {
      "module": "inventory",
      "source": "PLAN_BUNDLE",
      "sourceKey": "ITEM:23",
      "charged": true,
      "status": "ACTIVE",
      "validFrom": "2026-10-16",
      "validTo": null
    },
    {
      "module": "booking",
      "source": "PLAN_BUNDLE",
      "sourceKey": "ITEM:23",
      "charged": true,
      "status": "ACTIVE",
      "validFrom": "2026-10-16",
      "validTo": null
    },
    {
      "module": "file",
      "source": "PLATFORM_CORE",
      "sourceKey": "CORE",
      "charged": false,
      "status": "ACTIVE",
      "validFrom": "2026-10-16",
      "validTo": null
    },
    {
      "module": "notification",
      "source": "PLATFORM_CORE",
      "sourceKey": "CORE",
      "charged": false,
      "status": "ACTIVE",
      "validFrom": "2026-10-16",
      "validTo": null
    }
  ],
  "allowances": [
    {
      "id": 18,
      "createdAt": "2026-10-16T17:31:31.717675",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:31.717675",
      "updatedBy": "superadmin",
      "version": 0,
      "companyId": 13,
      "allowanceType": "STORAGE_BYTES",
      "includedQuantity": 10737418240,
      "purchasedQuantity": 0,
      "effectiveLimit": 10737418240
    },
    {
      "id": 17,
      "createdAt": "2026-10-16T17:31:31.714153",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:31.714153",
      "updatedBy": "superadmin",
      "version": 0,
      "companyId": 13,
      "allowanceType": "USERS",
      "includedQuantity": 5,
      "purchasedQuantity": 3,
      "effectiveLimit": 8
    }
  ],
  "resources": [
    {
      "type": "IDP_REALM",
      "module": null,
      "name": "co-113",
      "status": "VERIFIED",
      "schemaVersion": null
    },
    {
      "type": "DATABASE",
      "module": "vehicle",
      "name": "cmp_113_vehicle_db",
      "status": "VERIFIED",
      "schemaVersion": "001-model"
    },
    {
      "type": "DATABASE",
      "module": "inventory",
      "name": "cmp_113_inventory_db",
      "status": "VERIFIED",
      "schemaVersion": "001-stock-unit"
    },
    {
      "type": "DATABASE",
      "module": "booking",
      "name": "cmp_113_booking_db",
      "status": "VERIFIED",
      "schemaVersion": "001-booking"
    },
    {
      "type": "DATABASE",
      "module": "file",
      "name": "cmp_113_attachment_db",
      "status": "VERIFIED",
      "schemaVersion": "001-module-info"
    },
    {
      "type": "DATABASE",
      "module": "notification",
      "name": "cmp_113_notification_db",
      "status": "VERIFIED",
      "schemaVersion": "001-module-info"
    },
    {
      "type": "BUCKET",
      "module": null,
      "name": "cmp-113-control-panel-bucket",
      "status": "VERIFIED",
      "schemaVersion": null
    },
    {
      "type": "BUCKET",
      "module": "vehicle",
      "name": "cmp-113-vehicle-bucket",
      "status": "VERIFIED",
      "schemaVersion": null
    },
    {
      "type": "BUCKET",
      "module": "inventory",
      "name": "cmp-113-inventory-bucket",
      "status": "VERIFIED",
      "schemaVersion": null
    },
    {
      "type": "BUCKET",
      "module": "booking",
      "name": "cmp-113-booking-bucket",
      "status": "VERIFIED",
      "schemaVersion": null
    }
  ],
  "runtimeCache": {
    "companyCode": "CMP_113",
    "status": "ACTIVE",
    "modules": [
      "BOOKING",
      "FILE",
      "INVENTORY",
      "NOTIFICATION",
      "VEHICLE"
    ]
  }
}
```

</details>

### 25. Status history

```bash
curl -X GET "$CP/api/v1/platform/companies/CMP_113/status-history" -H "Authorization: Bearer $PLATFORM_TOKEN"
```

**HTTP 200**

```json
[
  {
    "id": 31,
    "createdAt": "2026-10-16T17:31:30.415517",
    "createdBy": "superadmin",
    "updatedAt": "2026-10-16T17:31:30.415517",
    "updatedBy": "superadmin",
    "version": 0,
    "companyId": 13,
    "fromStatus": null,
    "toStatus": "DRAFT",
    "reason": "created",
    "actor": "superadmin",
    "changedAt": "2026-10-16T17:31:30.415517"
  },
  {
    "id": 32,
    "createdAt": "2026-10-16T17:31:31.84885",
    "createdBy": "superadmin",
    "updatedAt": "2026-10-16T17:31:31.84885",
    "updatedBy": "superadmin",
    "version": 0,
    "companyId": 13,
    "fromStatus": "DRAFT",
    "toStatus": "PROVISIONING",
    "reason": "provisioning requested",
    "actor": "superadmin",
    "changedAt": "2026-10-16T17:31:31.84885"
  },
  {
    "id": 33,
    "createdAt": "2026-10-16T17:31:35.757625",
    "createdBy": "system",
    "updatedAt": "2026-10-16T17:31:35.757625",
    "updatedBy": "system",
    "version": 0,
    "companyId": 13,
    "fromStatus": "PROVISIONING",
    "toStatus": "ACTIVE",
    "reason": "all mandatory steps verified",
    "actor": "system",
    "changedAt": "2026-10-16T17:31:35.757625"
  }
]
```

## 3. Velocity Motors' first login

### 26. Keycloak admin: find the invited Company Super Admin in co-113

```bash
curl -X GET "$KC/admin/realms/co-113/users?email=owner@velocity.test&exact=true" -H "Authorization: Bearer $OPERATOR_TOKEN"
```

**HTTP 200**

<details><summary>Response body (113 lines)</summary>

```json
[
  {
    "id": "3b7cebf8-6491-4cfe-980d-13374724f4b7",
    "username": "owner@velocity.test",
    "firstName": "Company",
    "lastName": "Super Admin",
    "email": "owner@velocity.test",
    "emailVerified": false,
    "userProfileMetadata": {
      "attributes": [
        {
          "name": "username",
          "displayName": "${username}",
          "required": true,
          "readOnly": true,
          "validators": {
            "username-prohibited-characters": {
              "ignore.empty.value": true
            },
            "multivalued": {
              "max": "1"
            },
            "length": {
              "max": 255,
              "ignore.empty.value": true,
              "min": 3
            },
            "up-username-not-idn-homograph": {
              "ignore.empty.value": true
            }
          },
          "multivalued": false
        },
        {
          "name": "email",
          "displayName": "${email}",
          "required": false,
          "readOnly": false,
          "validators": {
            "multivalued": {
              "max": "1"
            },
            "length": {
              "max": 255,
              "ignore.empty.value": true
            },
            "email": {
              "ignore.empty.value": true
            }
          },
          "multivalued": false
        },
        {
          "name": "firstName",
          "displayName": "${firstName}",
          "required": false,
          "readOnly": false,
          "validators": {
            "person-name-prohibited-characters": {
              "ignore.empty.value": true
            },
            "multivalued": {
              "max": "1"
            },
            "length": {
              "max": 255,
              "ignore.empty.value": true
            }
          },
          "multivalued": false
        },
        {
          "name": "lastName",
          "displayName": "${lastName}",
          "required": false,
          "readOnly": false,
          "validators": {
            "person-name-prohibited-characters": {
              "ignore.empty.value": true
            },
            "multivalued": {
              "max": "1"
            },
            "length": {
              "max": 255,
              "ignore.empty.value": true
            }
          },
          "multivalued": false
        }
      ],
      "groups": [
        {
          "name": "user-metadata",
          "displayHeader": "User metadata",
          "displayDescription": "Attributes, which refer to user metadata"
        }
      ]
    },
    "enabled": true,
    "createdTimestamp": 1792152095716,
    "totp": false,
    "disableableCredentialTypes": [],
    "requiredActions": [
      "VERIFY_EMAIL",
      "UPDATE_PASSWORD"
    ],
    "notBefore": 0,
    "access": {
      "manage": true
    }
  }
]
```

</details>

### 27. Keycloak admin: set the password (invitation accepted)

```bash
curl -X PUT "$KC/admin/realms/co-113/users/3b7cebf8-6491-4cfe-980d-13374724f4b7/reset-password" -H "Authorization: Bearer $OPERATOR_TOKEN" -H "Content-Type: application/json" -d '{"type":"password","value":"Velocity-Owner-1","temporary":false}'
```

**HTTP 204**

_(empty body)_

### 28. Keycloak admin: verify e-mail, clear required actions

```bash
curl -X PUT "$KC/admin/realms/co-113/users/3b7cebf8-6491-4cfe-980d-13374724f4b7" -H "Authorization: Bearer $OPERATOR_TOKEN" -H "Content-Type: application/json" -d '{"id":"3b7cebf8-6491-4cfe-980d-13374724f4b7","username":"owner@velocity.test","firstName":"Company","lastName":"Super Admin","email":"owner@velocity.test","emailVerified":true,"userProfileMetadata":{"attributes":[{"name":"username","displayName":"${username}","required":true,"readOnly":true,"validators":{"username-prohibited-characters":{"ignore.empty.value":true},"multivalued":{"max":"1"},"length":{"max":255,"ignore.empty.value":true,"min":3},"up-username-not-idn-homograph":{"ignore.empty.value":true}},"multivalued":false},{"name":"email","displayName":"${email}","required":false,"readOnly":false,"validators":{"multivalued":{"max":"1"},"length":{"max":255,"ignore.empty.value":true},"email":{"ignore.empty.value":true}},"multivalued":false},{"name":"firstName","displayName":"${firstName}","required":false,"readOnly":false,"validators":{"person-name-prohibited-characters":{"ignore.empty.value":true},"multivalued":{"max":"1"},"length":{"max":255,"ignore.empty.value":true}},"multivalued":false},{"name":"lastName","displayName":"${lastName}","required":false,"readOnly":false,"validators":{"person-name-prohibited-characters":{"ignore.empty.value":true},"multivalued":{"max":"1"},"length":{"max":255,"ignore.empty.value":true}},"multivalued":false}],"groups":[{"name":"user-metadata","displayHeader":"User metadata","displayDescription":"Attributes, which refer to user metadata"}]},"enabled":true,"createdTimestamp":1792152095716,"totp":false,"disableableCredentialTypes":[],"requiredActions":[],"notBefore":0,"access":{"manage":true}}'
```

**HTTP 204**

_(empty body)_

### 29. Company token (realm co-113)

```bash
curl -X POST "$KC/realms/co-113/protocol/openid-connect/token" -d grant_type=password -d client_id=erp-web -d username=owner@velocity.test -d password=***
```

**HTTP 200**

```json
{
  "access_token": "eyJhbGciOiJS...",
  "expires_in": 300,
  "refresh_expires_in": 1800,
  "refresh_token": "eyJhbGciOiJI...",
  "token_type": "Bearer",
  "not-before-policy": 0,
  "session_state": "a59766e5-9492-4802-999d-a751affefdfc",
  "scope": "profile email"
}
```

### 30. Decoded claims of the company token

```bash
(base64url-decoded JWT payload)
```

**HTTP -**

```json
{
  "exp": 1792152397,
  "iat": 1792152097,
  "jti": "onrtro:7c8190ca-84de-9d7b-b0ee-c82f22d7f0bd",
  "iss": "http://localhost:18180/realms/co-113",
  "aud": [
    "nyaalkaran-erp",
    "account"
  ],
  "sub": "3b7cebf8-6491-4cfe-980d-13374724f4b7",
  "typ": "Bearer",
  "azp": "erp-web",
  "sid": "a59766e5-9492-4802-999d-a751affefdfc",
  "acr": "1",
  "allowed-origins": [
    "http://localhost:4200"
  ],
  "realm_access": {
    "roles": [
      "COMPANY_SUPER_ADMIN",
      "offline_access",
      "uma_authorization",
      "default-roles-co-113"
    ]
  },
  "resource_access": {
    "account": {
      "roles": [
        "manage-account",
        "manage-account-links",
        "view-profile"
      ]
    }
  },
  "scope": "profile email",
  "tenant_id": "CMP_113",
  "email_verified": true,
  "name": "Company Super Admin",
  "preferred_username": "owner@velocity.test",
  "given_name": "Company",
  "family_name": "Super Admin",
  "email": "owner@velocity.test"
}
```

## 4. Internal registry API (M2M, what the services call)

### 31. Which company is this issuer?

```bash
curl -X GET "$CP/api/v1/internal/realms/company?issuer=http://localhost:18180/realms/co-113" -H "Authorization: Bearer $SVC_TOKEN"
```

**HTTP 200**

```json
{
  "issuer": "http://localhost:18180/realms/co-113",
  "companyCode": "CMP_113"
}
```

### 32. Which database serves (company, vehicle)?

```bash
curl -X GET "$CP/api/v1/internal/companies/CMP_113/modules/vehicle/database" -H "Authorization: Bearer $SVC_TOKEN"
```

**HTTP 200**

```json
{
  "companyCode": "CMP_113",
  "moduleCode": "vehicle",
  "databaseName": "cmp_113_vehicle_db",
  "host": "localhost",
  "port": 13307
}
```

### 33. A user token is refused on the internal API -> 401

```bash
curl -X GET "$CP/api/v1/internal/companies/CMP_113/modules/vehicle/database" -H "Authorization: Bearer $TOKEN_A"
```

**HTTP 401**

```text
(no body) WWW-Authenticate: Bearer error="invalid_token", error_description="An error occurred while attempting to decode the Jwt: Signed JWT rejected: Another algorithm expected, or no matching key(s) found", error_uri="https://tools.ietf.org/html/rfc6750#section-3.1"
```

## 5. Velocity Motors: vehicle-service

### 34. Add model VX-SUV

```bash
curl -X POST "$VEHICLE/api/v1/vehicle/models" -H "Authorization: Bearer $TOKEN_A" -H "Content-Type: application/json" -d '{"modelCode":"VX-SUV","name":"Velocity VX SUV","bodyType":"SUV","fuelType":"DIESEL","basePrice":1450000}'
```

**HTTP 201**

```json
{
  "id": 1,
  "modelCode": "VX-SUV",
  "name": "Velocity VX SUV",
  "bodyType": "SUV",
  "fuelType": "DIESEL",
  "basePrice": 1450000,
  "createdAt": "2026-10-16T17:31:37.8071248",
  "createdBy": "owner@velocity.test"
}
```

### 35. Add model VX-EV

```bash
curl -X POST "$VEHICLE/api/v1/vehicle/models" -H "Authorization: Bearer $TOKEN_A" -H "Content-Type: application/json" -d '{"modelCode":"VX-EV","name":"Velocity VX Electric","bodyType":"HATCHBACK","fuelType":"ELECTRIC","basePrice":1150000}'
```

**HTTP 201**

```json
{
  "id": 2,
  "modelCode": "VX-EV",
  "name": "Velocity VX Electric",
  "bodyType": "HATCHBACK",
  "fuelType": "ELECTRIC",
  "basePrice": 1150000,
  "createdAt": "2026-10-16T17:31:37.8858507",
  "createdBy": "owner@velocity.test"
}
```

### 36. Duplicate model -> 409 MODEL_EXISTS

```bash
curl -X POST "$VEHICLE/api/v1/vehicle/models" -H "Authorization: Bearer $TOKEN_A" -H "Content-Type: application/json" -d '{"modelCode":"vx-suv","name":"Again","bodyType":"SUV","fuelType":"PETROL","basePrice":1}'
```

**HTTP 409**

```json
{
  "type": "about:blank",
  "title": "MODEL_EXISTS",
  "status": 409,
  "detail": "Model VX-SUV already exists",
  "instance": "/api/v1/vehicle/models",
  "code": "MODEL_EXISTS"
}
```

### 37. Missing / invalid fields -> 400 VALIDATION_FAILED

```bash
curl -X POST "$VEHICLE/api/v1/vehicle/models" -H "Authorization: Bearer $TOKEN_A" -H "Content-Type: application/json" -d '{"modelCode":"X","bodyType":"SUV"}'
```

**HTTP 400**

```json
{
  "type": "about:blank",
  "title": "VALIDATION_FAILED",
  "status": 400,
  "detail": "modelCode: must match \"[A-Za-z0-9-]{2,40}\"; fuelType: must not be null; basePrice: must not be null; name: must not be blank",
  "instance": "/api/v1/vehicle/models",
  "code": "VALIDATION_FAILED"
}
```

### 38. Unknown enum value -> 400 MALFORMED_BODY

```bash
curl -X POST "$VEHICLE/api/v1/vehicle/models" -H "Authorization: Bearer $TOKEN_A" -H "Content-Type: application/json" -d '{"modelCode":"VX-TANK","name":"Tank","bodyType":"TANK","fuelType":"DIESEL","basePrice":1}'
```

**HTTP 400**

```json
{
  "type": "about:blank",
  "title": "MALFORMED_BODY",
  "status": 400,
  "detail": "The request body is not valid JSON for this API",
  "instance": "/api/v1/vehicle/models",
  "code": "MALFORMED_BODY"
}
```

### 39. List models

```bash
curl -X GET "$VEHICLE/api/v1/vehicle/models" -H "Authorization: Bearer $TOKEN_A"
```

**HTTP 200**

```json
[
  {
    "id": 2,
    "modelCode": "VX-EV",
    "name": "Velocity VX Electric",
    "bodyType": "HATCHBACK",
    "fuelType": "ELECTRIC",
    "basePrice": 1150000.00,
    "createdAt": "2026-10-16T23:01:37.885851",
    "createdBy": "owner@velocity.test"
  },
  {
    "id": 1,
    "modelCode": "VX-SUV",
    "name": "Velocity VX SUV",
    "bodyType": "SUV",
    "fuelType": "DIESEL",
    "basePrice": 1450000.00,
    "createdAt": "2026-10-16T23:01:37.807125",
    "createdBy": "owner@velocity.test"
  }
]
```

### 40. Get one model

```bash
curl -X GET "$VEHICLE/api/v1/vehicle/models/VX-SUV" -H "Authorization: Bearer $TOKEN_A"
```

**HTTP 200**

```json
{
  "id": 1,
  "modelCode": "VX-SUV",
  "name": "Velocity VX SUV",
  "bodyType": "SUV",
  "fuelType": "DIESEL",
  "basePrice": 1450000.00,
  "createdAt": "2026-10-16T23:01:37.807125",
  "createdBy": "owner@velocity.test"
}
```

### 41. Unknown model -> 404 MODEL_NOT_FOUND

```bash
curl -X GET "$VEHICLE/api/v1/vehicle/models/NOPE" -H "Authorization: Bearer $TOKEN_A"
```

**HTTP 404**

```json
{
  "type": "about:blank",
  "title": "MODEL_NOT_FOUND",
  "status": 404,
  "detail": "No model NOPE",
  "instance": "/api/v1/vehicle/models/NOPE",
  "code": "MODEL_NOT_FOUND"
}
```

### 42. Routing: which DB served Velocity

```bash
curl -X GET "$VEHICLE/api/v1/vehicle/_routing" -H "Authorization: Bearer $TOKEN_A"
```

**HTTP 200**

```json
{
  "companyCode": "CMP_113",
  "connectedTo": "cmp_113_vehicle_db",
  "user": "owner@velocity.test",
  "database": "cmp_113_vehicle_db",
  "module": "vehicle"
}
```

## 6. Velocity Motors: inventory-service

### 43. Receive car 1 (inventory asks vehicle-service if the model exists)

```bash
curl -X POST "$INVENTORY/api/v1/inventory/units" -H "Authorization: Bearer $TOKEN_A" -H "Content-Type: application/json" -d '{"vin":"MA1VX001792152081","modelCode":"VX-SUV","color":"Red"}'
```

**HTTP 201**

```json
{
  "id": 1,
  "vin": "MA1VX001792152081",
  "modelCode": "VX-SUV",
  "color": "RED",
  "status": "AVAILABLE",
  "reservedFor": null,
  "createdAt": "2026-10-16T17:31:38.4616763",
  "createdBy": "owner@velocity.test"
}
```

### 44. Receive car 2

```bash
curl -X POST "$INVENTORY/api/v1/inventory/units" -H "Authorization: Bearer $TOKEN_A" -H "Content-Type: application/json" -d '{"vin":"MA1VX001792152082","modelCode":"VX-SUV","color":"Red"}'
```

**HTTP 201**

```json
{
  "id": 2,
  "vin": "MA1VX001792152082",
  "modelCode": "VX-SUV",
  "color": "RED",
  "status": "AVAILABLE",
  "reservedFor": null,
  "createdAt": "2026-10-16T17:31:38.533892",
  "createdBy": "owner@velocity.test"
}
```

### 45. Same VIN again -> 409 VIN_EXISTS

```bash
curl -X POST "$INVENTORY/api/v1/inventory/units" -H "Authorization: Bearer $TOKEN_A" -H "Content-Type: application/json" -d '{"vin":"MA1VX001792152082","modelCode":"VX-SUV","color":"Red"}'
```

**HTTP 409**

```json
{
  "type": "about:blank",
  "title": "VIN_EXISTS",
  "status": 409,
  "detail": "VIN MA1VX001792152082 is already in stock",
  "instance": "/api/v1/inventory/units",
  "code": "VIN_EXISTS"
}
```

### 46. Model not in the catalog -> 422 UNKNOWN_MODEL

```bash
curl -X POST "$INVENTORY/api/v1/inventory/units" -H "Authorization: Bearer $TOKEN_A" -H "Content-Type: application/json" -d '{"vin":"MA1XX0000XXX00009","modelCode":"NO-SUCH-CAR","color":"Red"}'
```

**HTTP 422**

```json
{
  "type": "about:blank",
  "title": "UNKNOWN_MODEL",
  "status": 422,
  "detail": "Model NO-SUCH-CAR is not in the vehicle catalog",
  "instance": "/api/v1/inventory/units",
  "code": "UNKNOWN_MODEL"
}
```

### 47. Reserve directly (normally booking-service does this)

```bash
curl -X POST "$INVENTORY/api/v1/inventory/reservations" -H "Authorization: Bearer $TOKEN_A" -H "Content-Type: application/json" -d '{"reference":"MANUAL-1","modelCode":"VX-SUV","color":"red"}'
```

**HTTP 201**

```json
{
  "id": 1,
  "vin": "MA1VX001792152081",
  "modelCode": "VX-SUV",
  "color": "RED",
  "status": "RESERVED",
  "reservedFor": "MANUAL-1",
  "createdAt": "2026-10-16T23:01:38.461676",
  "createdBy": "owner@velocity.test"
}
```

### 48. Release the reservation

```bash
curl -X DELETE "$INVENTORY/api/v1/inventory/reservations/MANUAL-1" -H "Authorization: Bearer $TOKEN_A"
```

**HTTP 204**

_(empty body)_

### 49. List AVAILABLE VX-SUV

```bash
curl -X GET "$INVENTORY/api/v1/inventory/units?modelCode=VX-SUV&status=AVAILABLE" -H "Authorization: Bearer $TOKEN_A"
```

**HTTP 200**

```json
[
  {
    "id": 1,
    "vin": "MA1VX001792152081",
    "modelCode": "VX-SUV",
    "color": "RED",
    "status": "AVAILABLE",
    "reservedFor": null,
    "createdAt": "2026-10-16T23:01:38.461676",
    "createdBy": "owner@velocity.test"
  },
  {
    "id": 2,
    "vin": "MA1VX001792152082",
    "modelCode": "VX-SUV",
    "color": "RED",
    "status": "AVAILABLE",
    "reservedFor": null,
    "createdAt": "2026-10-16T23:01:38.533892",
    "createdBy": "owner@velocity.test"
  }
]
```

### 50. Routing: which DB served Velocity

```bash
curl -X GET "$INVENTORY/api/v1/inventory/_routing" -H "Authorization: Bearer $TOKEN_A"
```

**HTTP 200**

```json
{
  "connectedTo": "cmp_113_inventory_db",
  "user": "owner@velocity.test",
  "database": "cmp_113_inventory_db",
  "module": "inventory",
  "companyCode": "CMP_113"
}
```

## 7. Velocity Motors: booking-service

### 51. Book a red VX-SUV (price from vehicle-service, car reserved in inventory-service)

```bash
curl -X POST "$BOOKING/api/v1/booking/bookings" -H "Authorization: Bearer $TOKEN_A" -H "Content-Type: application/json" -d '{"customerName":"Ravi Kumar","customerPhone":"9800000001","modelCode":"VX-SUV","color":"Red"}'
```

**HTTP 201**

```json
{
  "id": 1,
  "bookingNo": "BK-20261016-MH7D19",
  "customerName": "Ravi Kumar",
  "customerPhone": "9800000001",
  "modelCode": "VX-SUV",
  "color": "RED",
  "vin": "MA1VX001792152081",
  "price": 1450000.00,
  "status": "BOOKED",
  "createdAt": "2026-10-16T17:31:39.1515229",
  "createdBy": "owner@velocity.test"
}
```

### 52. Book the second red VX-SUV

```bash
curl -X POST "$BOOKING/api/v1/booking/bookings" -H "Authorization: Bearer $TOKEN_A" -H "Content-Type: application/json" -d '{"customerName":"Anita Rao","customerPhone":"9800000003","modelCode":"VX-SUV","color":"Red"}'
```

**HTTP 201**

```json
{
  "id": 2,
  "bookingNo": "BK-20261016-AY2ABN",
  "customerName": "Anita Rao",
  "customerPhone": "9800000003",
  "modelCode": "VX-SUV",
  "color": "RED",
  "vin": "MA1VX001792152082",
  "price": 1450000.00,
  "status": "BOOKED",
  "createdAt": "2026-10-16T17:31:39.3164868",
  "createdBy": "owner@velocity.test"
}
```

### 53. No red VX-SUV left -> 409 OUT_OF_STOCK

```bash
curl -X POST "$BOOKING/api/v1/booking/bookings" -H "Authorization: Bearer $TOKEN_A" -H "Content-Type: application/json" -d '{"customerName":"Late Customer","modelCode":"VX-SUV","color":"Red"}'
```

**HTTP 409**

```json
{
  "type": "about:blank",
  "title": "OUT_OF_STOCK",
  "status": 409,
  "detail": "No AVAILABLE VX-SUV in RED",
  "instance": "/api/v1/booking/bookings",
  "code": "OUT_OF_STOCK"
}
```

### 54. Get booking

```bash
curl -X GET "$BOOKING/api/v1/booking/bookings/BK-20261016-MH7D19" -H "Authorization: Bearer $TOKEN_A"
```

**HTTP 200**

```json
{
  "id": 1,
  "bookingNo": "BK-20261016-MH7D19",
  "customerName": "Ravi Kumar",
  "customerPhone": "9800000001",
  "modelCode": "VX-SUV",
  "color": "RED",
  "vin": "MA1VX001792152081",
  "price": 1450000.00,
  "status": "BOOKED",
  "createdAt": "2026-10-16T23:01:39.151523",
  "createdBy": "owner@velocity.test"
}
```

### 55. Deliver booking 1 (car SOLD)

```bash
curl -X POST "$BOOKING/api/v1/booking/bookings/BK-20261016-MH7D19/deliver" -H "Authorization: Bearer $TOKEN_A"
```

**HTTP 200**

```json
{
  "id": 1,
  "bookingNo": "BK-20261016-MH7D19",
  "customerName": "Ravi Kumar",
  "customerPhone": "9800000001",
  "modelCode": "VX-SUV",
  "color": "RED",
  "vin": "MA1VX001792152081",
  "price": 1450000.00,
  "status": "DELIVERED",
  "createdAt": "2026-10-16T23:01:39.151523",
  "createdBy": "owner@velocity.test"
}
```

### 56. Cancel booking 2 (car AVAILABLE again)

```bash
curl -X POST "$BOOKING/api/v1/booking/bookings/BK-20261016-AY2ABN/cancel" -H "Authorization: Bearer $TOKEN_A"
```

**HTTP 200**

```json
{
  "id": 2,
  "bookingNo": "BK-20261016-AY2ABN",
  "customerName": "Anita Rao",
  "customerPhone": "9800000003",
  "modelCode": "VX-SUV",
  "color": "RED",
  "vin": "MA1VX001792152082",
  "price": 1450000.00,
  "status": "CANCELLED",
  "createdAt": "2026-10-16T23:01:39.316487",
  "createdBy": "owner@velocity.test"
}
```

### 57. Cancel a delivered booking -> 409 INVALID_BOOKING_STATE

```bash
curl -X POST "$BOOKING/api/v1/booking/bookings/BK-20261016-MH7D19/cancel" -H "Authorization: Bearer $TOKEN_A"
```

**HTTP 409**

```json
{
  "type": "about:blank",
  "title": "INVALID_BOOKING_STATE",
  "status": 409,
  "detail": "Booking BK-20261016-MH7D19 is DELIVERED",
  "instance": "/api/v1/booking/bookings/BK-20261016-MH7D19/cancel",
  "code": "INVALID_BOOKING_STATE"
}
```

### 58. List bookings

```bash
curl -X GET "$BOOKING/api/v1/booking/bookings" -H "Authorization: Bearer $TOKEN_A"
```

**HTTP 200**

```json
[
  {
    "id": 1,
    "bookingNo": "BK-20261016-MH7D19",
    "customerName": "Ravi Kumar",
    "customerPhone": "9800000001",
    "modelCode": "VX-SUV",
    "color": "RED",
    "vin": "MA1VX001792152081",
    "price": 1450000.00,
    "status": "DELIVERED",
    "createdAt": "2026-10-16T23:01:39.151523",
    "createdBy": "owner@velocity.test"
  },
  {
    "id": 2,
    "bookingNo": "BK-20261016-AY2ABN",
    "customerName": "Anita Rao",
    "customerPhone": "9800000003",
    "modelCode": "VX-SUV",
    "color": "RED",
    "vin": "MA1VX001792152082",
    "price": 1450000.00,
    "status": "CANCELLED",
    "createdAt": "2026-10-16T23:01:39.316487",
    "createdBy": "owner@velocity.test"
  }
]
```

### 59. Stock after: one SOLD, one AVAILABLE

```bash
curl -X GET "$INVENTORY/api/v1/inventory/units?modelCode=VX-SUV" -H "Authorization: Bearer $TOKEN_A"
```

**HTTP 200**

```json
[
  {
    "id": 1,
    "vin": "MA1VX001792152081",
    "modelCode": "VX-SUV",
    "color": "RED",
    "status": "SOLD",
    "reservedFor": "BK-20261016-MH7D19",
    "createdAt": "2026-10-16T23:01:38.461676",
    "createdBy": "owner@velocity.test"
  },
  {
    "id": 2,
    "vin": "MA1VX001792152082",
    "modelCode": "VX-SUV",
    "color": "RED",
    "status": "AVAILABLE",
    "reservedFor": null,
    "createdAt": "2026-10-16T23:01:38.533892",
    "createdBy": "owner@velocity.test"
  }
]
```

### 60. Routing: which DB served Velocity

```bash
curl -X GET "$BOOKING/api/v1/booking/_routing" -H "Authorization: Bearer $TOKEN_A"
```

**HTTP 200**

```json
{
  "module": "booking",
  "database": "cmp_113_booking_db",
  "user": "owner@velocity.test",
  "connectedTo": "cmp_113_booking_db",
  "companyCode": "CMP_113"
}
```

### 61. CP tenant API of a module Velocity did not buy -> 403 MODULE_NOT_ENTITLED

```bash
curl -X GET "$CP/api/v1/tenant/crm/customers" -H "Authorization: Bearer $TOKEN_A"
```

**HTTP 403**

```json
{
  "type": "about:blank",
  "title": "MODULE_NOT_ENTITLED",
  "status": 403,
  "detail": "CRM is not in the company's subscription",
  "instance": "/api/v1/tenant/crm/customers",
  "code": "MODULE_NOT_ENTITLED"
}
```

## 8. Onboard Apex Autos (vehicle + inventory only; first cheque bounces)

### 62. Create company

```bash
curl -X POST "$CP/api/v1/platform/companies" -H "Authorization: Bearer $PLATFORM_TOKEN" -H "Content-Type: application/json" -d '{"legalName":"Apex Autos LLP","superAdminEmail":"owner@apex.test"}'
```

**HTTP 201**

```json
{
  "id": 14,
  "companyCode": "CMP_114",
  "legalName": "Apex Autos LLP",
  "displayName": "Apex Autos LLP",
  "slug": "apex-autos-llp-6",
  "loginCode": "RU4KQCUU",
  "status": "DRAFT",
  "statusReason": null,
  "onboardingType": "NEW",
  "superAdminEmail": "owner@apex.test",
  "realm": "co-114",
  "activatedAt": null
}
```

### 63. Quote vehicle + inventory

```bash
curl -X POST "$CP/api/v1/platform/companies/CMP_114/orders" -H "Authorization: Bearer $PLATFORM_TOKEN" -H "Content-Type: application/json" -d '{"billingPeriod":"MONTHLY","modules":["vehicle","inventory"],"users":5,"storageGb":10}'
```

**HTTP 201**

<details><summary>Response body (97 lines)</summary>

```json
{
  "order": {
    "id": 17,
    "createdAt": "2026-10-16T17:31:40.241191",
    "createdBy": "superadmin",
    "updatedAt": "2026-10-16T17:31:40.241191",
    "updatedBy": "superadmin",
    "version": 0,
    "orderNumber": "ORD/2026-27/00017",
    "companyId": 14,
    "subscriptionId": null,
    "orderKind": "NEW",
    "channel": "ADMIN",
    "billingPeriod": "MONTHLY",
    "currency": "INR",
    "requestedUsers": 5,
    "requestedStorageGb": 10,
    "subtotal": 350.0000,
    "discountTotal": 0.0000,
    "taxableTotal": 350.0000,
    "taxTotal": 0.0000,
    "otherChargesTotal": 0.0000,
    "roundOff": 0.0000,
    "grandTotal": 350.0000,
    "paymentStatus": "UNPAID",
    "status": "QUOTED",
    "calculationVersion": "poc-1",
    "inputJson": "{\"command\": {\"users\": 5, \"bundles\": null, \"modules\": [\"vehicle\", \"inventory\"], \"currency\": \"INR\", \"storageGb\": 10, \"billingPeriod\": \"MONTHLY\", \"idempotencyKey\": \"50425537-650c-4a57-8863-1a9bc966363f\", \"manualDiscount\": null}, \"existingModules\": []}",
    "validUntil": "2026-10-31T17:31:40.241191",
    "idempotencyKey": "50425537-650c-4a57-8863-1a9bc966363f",
    "createdByActor": "superadmin"
  },
  "lines": [
    {
      "id": 28,
      "createdAt": "2026-10-16T17:31:40.243201",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:40.243201",
      "updatedBy": "superadmin",
      "version": 0,
      "billingOrderId": 17,
      "lineNo": 1,
      "lineType": "PLAN",
      "planVersionId": 35,
      "planPriceId": 65,
      "moduleId": 18,
      "bundleId": null,
      "description": "Module Vehicle Catalog",
      "quantity": 1.0000,
      "unitPrice": 150.0000,
      "grossAmount": 150.0000,
      "lineDiscountAmount": 0.0000,
      "taxableAmount": 150.0000,
      "taxAmount": 0.0000,
      "lineTotal": 150.0000
    },
    {
      "id": 29,
      "createdAt": "2026-10-16T17:31:40.2442",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:40.2442",
      "updatedBy": "superadmin",
      "version": 0,
      "billingOrderId": 17,
      "lineNo": 2,
      "lineType": "PLAN",
      "planVersionId": 34,
      "planPriceId": 67,
      "moduleId": 19,
      "bundleId": null,
      "description": "Module Vehicle Inventory",
      "quantity": 1.0000,
      "unitPrice": 200.0000,
      "grossAmount": 200.0000,
      "lineDiscountAmount": 0.0000,
      "taxableAmount": 200.0000,
      "taxAmount": 0.0000,
      "lineTotal": 200.0000
    }
  ],
  "discounts": [],
  "warnings": [
    {
      "id": 17,
      "createdAt": "2026-10-16T17:31:40.245203",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:40.245203",
      "updatedBy": "superadmin",
      "version": 0,
      "billingOrderId": 17,
      "code": "TAX_RULE_NOT_CONFIGURED",
      "detail": "No APPROVED tax rule (Q-04); tax = 0"
    }
  ],
  "payments": [],
  "invoice": null
}
```

</details>

### 64. Confirm

```bash
curl -X POST "$CP/api/v1/platform/orders/17/confirm" -H "Authorization: Bearer $PLATFORM_TOKEN"
```

**HTTP 200**

<details><summary>Response body (97 lines)</summary>

```json
{
  "order": {
    "id": 17,
    "createdAt": "2026-10-16T17:31:40.241191",
    "createdBy": "superadmin",
    "updatedAt": "2026-10-16T17:31:40.429702",
    "updatedBy": "superadmin",
    "version": 2,
    "orderNumber": "ORD/2026-27/00017",
    "companyId": 14,
    "subscriptionId": 14,
    "orderKind": "NEW",
    "channel": "ADMIN",
    "billingPeriod": "MONTHLY",
    "currency": "INR",
    "requestedUsers": 5,
    "requestedStorageGb": 10,
    "subtotal": 350.0000,
    "discountTotal": 0.0000,
    "taxableTotal": 350.0000,
    "taxTotal": 0.0000,
    "otherChargesTotal": 0.0000,
    "roundOff": 0.0000,
    "grandTotal": 350.0000,
    "paymentStatus": "UNPAID",
    "status": "AWAITING_PAYMENT",
    "calculationVersion": "poc-1",
    "inputJson": "{\"command\": {\"users\": 5, \"bundles\": null, \"modules\": [\"vehicle\", \"inventory\"], \"currency\": \"INR\", \"storageGb\": 10, \"billingPeriod\": \"MONTHLY\", \"idempotencyKey\": \"50425537-650c-4a57-8863-1a9bc966363f\", \"manualDiscount\": null}, \"existingModules\": []}",
    "validUntil": "2026-10-31T17:31:40.241191",
    "idempotencyKey": "50425537-650c-4a57-8863-1a9bc966363f",
    "createdByActor": "superadmin"
  },
  "lines": [
    {
      "id": 28,
      "createdAt": "2026-10-16T17:31:40.243201",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:40.243201",
      "updatedBy": "superadmin",
      "version": 0,
      "billingOrderId": 17,
      "lineNo": 1,
      "lineType": "PLAN",
      "planVersionId": 35,
      "planPriceId": 65,
      "moduleId": 18,
      "bundleId": null,
      "description": "Module Vehicle Catalog",
      "quantity": 1.0000,
      "unitPrice": 150.0000,
      "grossAmount": 150.0000,
      "lineDiscountAmount": 0.0000,
      "taxableAmount": 150.0000,
      "taxAmount": 0.0000,
      "lineTotal": 150.0000
    },
    {
      "id": 29,
      "createdAt": "2026-10-16T17:31:40.2442",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:40.2442",
      "updatedBy": "superadmin",
      "version": 0,
      "billingOrderId": 17,
      "lineNo": 2,
      "lineType": "PLAN",
      "planVersionId": 34,
      "planPriceId": 67,
      "moduleId": 19,
      "bundleId": null,
      "description": "Module Vehicle Inventory",
      "quantity": 1.0000,
      "unitPrice": 200.0000,
      "grossAmount": 200.0000,
      "lineDiscountAmount": 0.0000,
      "taxableAmount": 200.0000,
      "taxAmount": 0.0000,
      "lineTotal": 200.0000
    }
  ],
  "discounts": [],
  "warnings": [
    {
      "id": 17,
      "createdAt": "2026-10-16T17:31:40.245203",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:40.245203",
      "updatedBy": "superadmin",
      "version": 0,
      "billingOrderId": 17,
      "code": "TAX_RULE_NOT_CONFIGURED",
      "detail": "No APPROVED tax rule (Q-04); tax = 0"
    }
  ],
  "payments": [],
  "invoice": null
}
```

</details>

### 65. Cheque for the full amount

```bash
curl -X POST "$CP/api/v1/platform/orders/17/payments" -H "Authorization: Bearer $PLATFORM_TOKEN" -H "Content-Type: application/json" -d '{"mode":"CHEQUE","amount":350.0000,"instrumentRef":"CHQ-B-1792152089","bankName":"HDFC"}'
```

**HTTP 201**

<details><summary>Response body (125 lines)</summary>

```json
{
  "order": {
    "id": 17,
    "createdAt": "2026-10-16T17:31:40.241191",
    "createdBy": "superadmin",
    "updatedAt": "2026-10-16T17:31:40.50934",
    "updatedBy": "superadmin",
    "version": 3,
    "orderNumber": "ORD/2026-27/00017",
    "companyId": 14,
    "subscriptionId": 14,
    "orderKind": "NEW",
    "channel": "ADMIN",
    "billingPeriod": "MONTHLY",
    "currency": "INR",
    "requestedUsers": 5,
    "requestedStorageGb": 10,
    "subtotal": 350.0000,
    "discountTotal": 0.0000,
    "taxableTotal": 350.0000,
    "taxTotal": 0.0000,
    "otherChargesTotal": 0.0000,
    "roundOff": 0.0000,
    "grandTotal": 350.0000,
    "paymentStatus": "PENDING_VERIFICATION",
    "status": "AWAITING_PAYMENT",
    "calculationVersion": "poc-1",
    "inputJson": "{\"command\": {\"users\": 5, \"bundles\": null, \"modules\": [\"vehicle\", \"inventory\"], \"currency\": \"INR\", \"storageGb\": 10, \"billingPeriod\": \"MONTHLY\", \"idempotencyKey\": \"50425537-650c-4a57-8863-1a9bc966363f\", \"manualDiscount\": null}, \"existingModules\": []}",
    "validUntil": "2026-10-31T17:31:40.241191",
    "idempotencyKey": "50425537-650c-4a57-8863-1a9bc966363f",
    "createdByActor": "superadmin"
  },
  "lines": [
    {
      "id": 28,
      "createdAt": "2026-10-16T17:31:40.243201",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:40.243201",
      "updatedBy": "superadmin",
      "version": 0,
      "billingOrderId": 17,
      "lineNo": 1,
      "lineType": "PLAN",
      "planVersionId": 35,
      "planPriceId": 65,
      "moduleId": 18,
      "bundleId": null,
      "description": "Module Vehicle Catalog",
      "quantity": 1.0000,
      "unitPrice": 150.0000,
      "grossAmount": 150.0000,
      "lineDiscountAmount": 0.0000,
      "taxableAmount": 150.0000,
      "taxAmount": 0.0000,
      "lineTotal": 150.0000
    },
    {
      "id": 29,
      "createdAt": "2026-10-16T17:31:40.2442",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:40.2442",
      "updatedBy": "superadmin",
      "version": 0,
      "billingOrderId": 17,
      "lineNo": 2,
      "lineType": "PLAN",
      "planVersionId": 34,
      "planPriceId": 67,
      "moduleId": 19,
      "bundleId": null,
      "description": "Module Vehicle Inventory",
      "quantity": 1.0000,
      "unitPrice": 200.0000,
      "grossAmount": 200.0000,
      "lineDiscountAmount": 0.0000,
      "taxableAmount": 200.0000,
      "taxAmount": 0.0000,
      "lineTotal": 200.0000
    }
  ],
  "discounts": [],
  "warnings": [
    {
      "id": 17,
      "createdAt": "2026-10-16T17:31:40.245203",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:40.245203",
      "updatedBy": "superadmin",
      "version": 0,
      "billingOrderId": 17,
      "code": "TAX_RULE_NOT_CONFIGURED",
      "detail": "No APPROVED tax rule (Q-04); tax = 0"
    }
  ],
  "payments": [
    {
      "id": 18,
      "createdAt": "2026-10-16T17:31:40.503339",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:40.507356",
      "updatedBy": "superadmin",
      "version": 1,
      "paymentNumber": "PAY/2026-27/00018",
      "receiptNumber": "RCT/2026-27/00018",
      "companyId": 14,
      "billingOrderId": 17,
      "invoiceId": null,
      "paymentChannel": "MANUAL",
      "paymentMode": "CHEQUE",
      "amount": 350.0000,
      "currency": "INR",
      "status": "PENDING_VERIFICATION",
      "instrumentRef": "CHQ-B-1792152089",
      "instrumentDate": null,
      "bankName": "HDFC",
      "verificationRequired": true,
      "receivedAt": "2026-10-16T17:31:40.502342",
      "confirmedAt": null,
      "receivedBy": "superadmin",
      "rejectionReason": null,
      "idempotencyKey": "ffabf162-32dd-4ea7-be38-415064a43ef4"
    }
  ],
  "invoice": null
}
```

</details>

### 66. The cheque bounced

```bash
curl -X POST "$CP/api/v1/platform/payments/18/reject" -H "Authorization: Bearer $PLATFORM_TOKEN" -H "Content-Type: application/json" -d '{"reason":"insufficient funds"}'
```

**HTTP 200**

```json
{
  "id": 18,
  "createdAt": "2026-10-16T17:31:40.503339",
  "createdBy": "superadmin",
  "updatedAt": "2026-10-16T17:31:40.6367897",
  "updatedBy": "superadmin",
  "version": 2,
  "paymentNumber": "PAY/2026-27/00018",
  "receiptNumber": "RCT/2026-27/00018",
  "companyId": 14,
  "billingOrderId": 17,
  "invoiceId": null,
  "paymentChannel": "MANUAL",
  "paymentMode": "CHEQUE",
  "amount": 350.0000,
  "currency": "INR",
  "status": "REJECTED",
  "instrumentRef": "CHQ-B-1792152089",
  "instrumentDate": null,
  "bankName": "HDFC",
  "verificationRequired": true,
  "receivedAt": "2026-10-16T17:31:40.502342",
  "confirmedAt": null,
  "receivedBy": "superadmin",
  "rejectionReason": "insufficient funds",
  "idempotencyKey": "ffabf162-32dd-4ea7-be38-415064a43ef4"
}
```

### 67. Pay by UPI instead (immediate)

```bash
curl -X POST "$CP/api/v1/platform/orders/17/payments" -H "Authorization: Bearer $PLATFORM_TOKEN" -H "Content-Type: application/json" -d '{"mode":"UPI","amount":350.0000}'
```

**HTTP 201**

<details><summary>Response body (177 lines)</summary>

```json
{
  "order": {
    "id": 17,
    "createdAt": "2026-10-16T17:31:40.241191",
    "createdBy": "superadmin",
    "updatedAt": "2026-10-16T17:31:40.749031",
    "updatedBy": "superadmin",
    "version": 6,
    "orderNumber": "ORD/2026-27/00017",
    "companyId": 14,
    "subscriptionId": 14,
    "orderKind": "NEW",
    "channel": "ADMIN",
    "billingPeriod": "MONTHLY",
    "currency": "INR",
    "requestedUsers": 5,
    "requestedStorageGb": 10,
    "subtotal": 350.0000,
    "discountTotal": 0.0000,
    "taxableTotal": 350.0000,
    "taxTotal": 0.0000,
    "otherChargesTotal": 0.0000,
    "roundOff": 0.0000,
    "grandTotal": 350.0000,
    "paymentStatus": "PAID",
    "status": "FULFILLED",
    "calculationVersion": "poc-1",
    "inputJson": "{\"command\": {\"users\": 5, \"bundles\": null, \"modules\": [\"vehicle\", \"inventory\"], \"currency\": \"INR\", \"storageGb\": 10, \"billingPeriod\": \"MONTHLY\", \"idempotencyKey\": \"50425537-650c-4a57-8863-1a9bc966363f\", \"manualDiscount\": null}, \"existingModules\": []}",
    "validUntil": "2026-10-31T17:31:40.241191",
    "idempotencyKey": "50425537-650c-4a57-8863-1a9bc966363f",
    "createdByActor": "superadmin"
  },
  "lines": [
    {
      "id": 28,
      "createdAt": "2026-10-16T17:31:40.243201",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:40.243201",
      "updatedBy": "superadmin",
      "version": 0,
      "billingOrderId": 17,
      "lineNo": 1,
      "lineType": "PLAN",
      "planVersionId": 35,
      "planPriceId": 65,
      "moduleId": 18,
      "bundleId": null,
      "description": "Module Vehicle Catalog",
      "quantity": 1.0000,
      "unitPrice": 150.0000,
      "grossAmount": 150.0000,
      "lineDiscountAmount": 0.0000,
      "taxableAmount": 150.0000,
      "taxAmount": 0.0000,
      "lineTotal": 150.0000
    },
    {
      "id": 29,
      "createdAt": "2026-10-16T17:31:40.2442",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:40.2442",
      "updatedBy": "superadmin",
      "version": 0,
      "billingOrderId": 17,
      "lineNo": 2,
      "lineType": "PLAN",
      "planVersionId": 34,
      "planPriceId": 67,
      "moduleId": 19,
      "bundleId": null,
      "description": "Module Vehicle Inventory",
      "quantity": 1.0000,
      "unitPrice": 200.0000,
      "grossAmount": 200.0000,
      "lineDiscountAmount": 0.0000,
      "taxableAmount": 200.0000,
      "taxAmount": 0.0000,
      "lineTotal": 200.0000
    }
  ],
  "discounts": [],
  "warnings": [
    {
      "id": 17,
      "createdAt": "2026-10-16T17:31:40.245203",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:40.245203",
      "updatedBy": "superadmin",
      "version": 0,
      "billingOrderId": 17,
      "code": "TAX_RULE_NOT_CONFIGURED",
      "detail": "No APPROVED tax rule (Q-04); tax = 0"
    }
  ],
  "payments": [
    {
      "id": 18,
      "createdAt": "2026-10-16T17:31:40.503339",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:40.63679",
      "updatedBy": "superadmin",
      "version": 2,
      "paymentNumber": "PAY/2026-27/00018",
      "receiptNumber": "RCT/2026-27/00018",
      "companyId": 14,
      "billingOrderId": 17,
      "invoiceId": null,
      "paymentChannel": "MANUAL",
      "paymentMode": "CHEQUE",
      "amount": 350.0000,
      "currency": "INR",
      "status": "REJECTED",
      "instrumentRef": "CHQ-B-1792152089",
      "instrumentDate": null,
      "bankName": "HDFC",
      "verificationRequired": true,
      "receivedAt": "2026-10-16T17:31:40.502342",
      "confirmedAt": null,
      "receivedBy": "superadmin",
      "rejectionReason": "insufficient funds",
      "idempotencyKey": "ffabf162-32dd-4ea7-be38-415064a43ef4"
    },
    {
      "id": 19,
      "createdAt": "2026-10-16T17:31:40.705806",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:40.720636",
      "updatedBy": "superadmin",
      "version": 2,
      "paymentNumber": "PAY/2026-27/00019",
      "receiptNumber": "RCT/2026-27/00019",
      "companyId": 14,
      "billingOrderId": 17,
      "invoiceId": 11,
      "paymentChannel": "MANUAL",
      "paymentMode": "UPI",
      "amount": 350.0000,
      "currency": "INR",
      "status": "CONFIRMED",
      "instrumentRef": null,
      "instrumentDate": null,
      "bankName": null,
      "verificationRequired": false,
      "receivedAt": "2026-10-16T17:31:40.705806",
      "confirmedAt": "2026-10-16T17:31:40.707807",
      "receivedBy": "superadmin",
      "rejectionReason": null,
      "idempotencyKey": "ddf53ed8-3560-4fd0-91ca-486e315b8bde"
    }
  ],
  "invoice": {
    "id": 11,
    "createdAt": "2026-10-16T17:31:40.71396",
    "createdBy": "superadmin",
    "updatedAt": "2026-10-16T17:31:40.71396",
    "updatedBy": "superadmin",
    "version": 0,
    "invoiceNumber": "INV/2026-27/00011",
    "companyId": 14,
    "billingOrderId": 17,
    "invoiceType": "TAX_INVOICE",
    "issueDate": "2026-10-16",
    "currency": "INR",
    "subtotal": 350.0000,
    "discountTotal": 0.0000,
    "taxableTotal": 350.0000,
    "taxTotal": 0.0000,
    "otherChargesTotal": 0.0000,
    "roundOff": 0.0000,
    "grandTotal": 350.0000,
    "amountPaid": 350.0000,
    "amountCredited": 0.0000,
    "balanceDue": 0.0000,
    "status": "ISSUED_PAID",
    "issuedAt": "2026-10-16T17:31:40.71396"
  }
}
```

</details>

### 68. Provision

```bash
curl -X POST "$CP/api/v1/platform/companies/CMP_114/provision" -H "Authorization: Bearer $PLATFORM_TOKEN"
```

**HTTP 202**

<details><summary>Response body (344 lines)</summary>

```json
{
  "job": {
    "id": 11,
    "createdAt": "2026-10-16T17:31:40.8280927",
    "createdBy": "superadmin",
    "updatedAt": "2026-10-16T17:31:40.8280927",
    "updatedBy": "superadmin",
    "version": 0,
    "companyId": 14,
    "jobType": "CREATE_COMPANY",
    "mode": "SEEDED",
    "status": "QUEUED",
    "attempt": 0,
    "nextAttemptAt": null,
    "lockedBy": null,
    "lockedUntil": null,
    "activeGuard": 1,
    "requestedBy": "superadmin",
    "lastError": null,
    "completedAt": null
  },
  "steps": [
    {
      "id": 170,
      "createdAt": "2026-10-16T17:31:40.829092",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:40.829092",
      "updatedBy": "superadmin",
      "version": 0,
      "jobId": 11,
      "sequenceNo": 1,
      "stepCode": "RESERVE_NAMES",
      "resourceType": null,
      "moduleId": null,
      "resourceName": null,
      "status": "PENDING",
      "attempts": 0,
      "lastError": null,
      "startedAt": null,
      "completedAt": null,
      "done": false
    },
    {
      "id": 171,
      "createdAt": "2026-10-16T17:31:40.830103",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:40.830103",
      "updatedBy": "superadmin",
      "version": 0,
      "jobId": 11,
      "sequenceNo": 2,
      "stepCode": "CREATE_REALM",
      "resourceType": "IDP_REALM",
      "moduleId": null,
      "resourceName": "co-114",
      "status": "PENDING",
      "attempts": 0,
      "lastError": null,
      "startedAt": null,
      "completedAt": null,
      "done": false
    },
    {
      "id": 172,
      "createdAt": "2026-10-16T17:31:40.830103",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:40.830103",
      "updatedBy": "superadmin",
      "version": 0,
      "jobId": 11,
      "sequenceNo": 3,
      "stepCode": "CREATE_DATABASE",
      "resourceType": "DATABASE",
      "moduleId": 18,
      "resourceName": "cmp_114_vehicle_db",
      "status": "PENDING",
      "attempts": 0,
      "lastError": null,
      "startedAt": null,
      "completedAt": null,
      "done": false
    },
    {
      "id": 173,
      "createdAt": "2026-10-16T17:31:40.831609",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:40.831609",
      "updatedBy": "superadmin",
      "version": 0,
      "jobId": 11,
      "sequenceNo": 4,
      "stepCode": "CREATE_DATABASE",
      "resourceType": "DATABASE",
      "moduleId": 19,
      "resourceName": "cmp_114_inventory_db",
      "status": "PENDING",
      "attempts": 0,
      "lastError": null,
      "startedAt": null,
      "completedAt": null,
      "done": false
    },
    {
      "id": 174,
      "createdAt": "2026-10-16T17:31:40.831609",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:40.831609",
      "updatedBy": "superadmin",
      "version": 0,
      "jobId": 11,
      "sequenceNo": 5,
      "stepCode": "CREATE_DATABASE",
      "resourceType": "DATABASE",
      "moduleId": 16,
      "resourceName": "cmp_114_attachment_db",
      "status": "PENDING",
      "attempts": 0,
      "lastError": null,
      "startedAt": null,
      "completedAt": null,
      "done": false
    },
    {
      "id": 175,
      "createdAt": "2026-10-16T17:31:40.833264",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:40.833264",
      "updatedBy": "superadmin",
      "version": 0,
      "jobId": 11,
      "sequenceNo": 6,
      "stepCode": "CREATE_DATABASE",
      "resourceType": "DATABASE",
      "moduleId": 17,
      "resourceName": "cmp_114_notification_db",
      "status": "PENDING",
      "attempts": 0,
      "lastError": null,
      "startedAt": null,
      "completedAt": null,
      "done": false
    },
    {
      "id": 176,
      "createdAt": "2026-10-16T17:31:40.833264",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:40.833264",
      "updatedBy": "superadmin",
      "version": 0,
      "jobId": 11,
      "sequenceNo": 7,
      "stepCode": "MIGRATE_SCHEMA",
      "resourceType": "DATABASE",
      "moduleId": 18,
      "resourceName": "cmp_114_vehicle_db",
      "status": "PENDING",
      "attempts": 0,
      "lastError": null,
      "startedAt": null,
      "completedAt": null,
      "done": false
    },
    {
      "id": 177,
      "createdAt": "2026-10-16T17:31:40.834269",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:40.834269",
      "updatedBy": "superadmin",
      "version": 0,
      "jobId": 11,
      "sequenceNo": 8,
      "stepCode": "MIGRATE_SCHEMA",
      "resourceType": "DATABASE",
      "moduleId": 19,
      "resourceName": "cmp_114_inventory_db",
      "status": "PENDING",
      "attempts": 0,
      "lastError": null,
      "startedAt": null,
      "completedAt": null,
      "done": false
    },
    {
      "id": 178,
      "createdAt": "2026-10-16T17:31:40.835268",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:40.835268",
      "updatedBy": "superadmin",
      "version": 0,
      "jobId": 11,
      "sequenceNo": 9,
      "stepCode": "MIGRATE_SCHEMA",
      "resourceType": "DATABASE",
      "moduleId": 16,
      "resourceName": "cmp_114_attachment_db",
      "status": "PENDING",
      "attempts": 0,
      "lastError": null,
      "startedAt": null,
      "completedAt": null,
      "done": false
    },
    {
      "id": 179,
      "createdAt": "2026-10-16T17:31:40.835268",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:40.835268",
      "updatedBy": "superadmin",
      "version": 0,
      "jobId": 11,
      "sequenceNo": 10,
      "stepCode": "MIGRATE_SCHEMA",
      "resourceType": "DATABASE",
      "moduleId": 17,
      "resourceName": "cmp_114_notification_db",
      "status": "PENDING",
      "attempts": 0,
      "lastError": null,
      "startedAt": null,
      "completedAt": null,
      "done": false
    },
    {
      "id": 180,
      "createdAt": "2026-10-16T17:31:40.836268",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:40.836268",
      "updatedBy": "superadmin",
      "version": 0,
      "jobId": 11,
      "sequenceNo": 11,
      "stepCode": "CREATE_BUCKET",
      "resourceType": "BUCKET",
      "moduleId": null,
      "resourceName": "cmp-114-control-panel-bucket",
      "status": "PENDING",
      "attempts": 0,
      "lastError": null,
      "startedAt": null,
      "completedAt": null,
      "done": false
    },
    {
      "id": 181,
      "createdAt": "2026-10-16T17:31:40.837269",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:40.837269",
      "updatedBy": "superadmin",
      "version": 0,
      "jobId": 11,
      "sequenceNo": 12,
      "stepCode": "CREATE_BUCKET",
      "resourceType": "BUCKET",
      "moduleId": 18,
      "resourceName": "cmp-114-vehicle-bucket",
      "status": "PENDING",
      "attempts": 0,
      "lastError": null,
      "startedAt": null,
      "completedAt": null,
      "done": false
    },
    {
      "id": 182,
      "createdAt": "2026-10-16T17:31:40.837269",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:40.837269",
      "updatedBy": "superadmin",
      "version": 0,
      "jobId": 11,
      "sequenceNo": 13,
      "stepCode": "CREATE_BUCKET",
      "resourceType": "BUCKET",
      "moduleId": 19,
      "resourceName": "cmp-114-inventory-bucket",
      "status": "PENDING",
      "attempts": 0,
      "lastError": null,
      "startedAt": null,
      "completedAt": null,
      "done": false
    },
    {
      "id": 183,
      "createdAt": "2026-10-16T17:31:40.838268",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:40.838268",
      "updatedBy": "superadmin",
      "version": 0,
      "jobId": 11,
      "sequenceNo": 14,
      "stepCode": "INVITE_COMPANY_SUPER_ADMIN",
      "resourceType": null,
      "moduleId": null,
      "resourceName": null,
      "status": "PENDING",
      "attempts": 0,
      "lastError": null,
      "startedAt": null,
      "completedAt": null,
      "done": false
    },
    {
      "id": 184,
      "createdAt": "2026-10-16T17:31:40.838268",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:40.838268",
      "updatedBy": "superadmin",
      "version": 0,
      "jobId": 11,
      "sequenceNo": 15,
      "stepCode": "ACTIVATE",
      "resourceType": null,
      "moduleId": null,
      "resourceName": null,
      "status": "PENDING",
      "attempts": 0,
      "lastError": null,
      "startedAt": null,
      "completedAt": null,
      "done": false
    },
    {
      "id": 185,
      "createdAt": "2026-10-16T17:31:40.839285",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:40.839285",
      "updatedBy": "superadmin",
      "version": 0,
      "jobId": 11,
      "sequenceNo": 16,
      "stepCode": "PUBLISH_CACHES",
      "resourceType": null,
      "moduleId": null,
      "resourceName": null,
      "status": "PENDING",
      "attempts": 0,
      "lastError": null,
      "startedAt": null,
      "completedAt": null,
      "done": false
    }
  ]
}
```

</details>

### 69. Keycloak admin: find the invited Company Super Admin in co-114

```bash
curl -X GET "$KC/admin/realms/co-114/users?email=owner@apex.test&exact=true" -H "Authorization: Bearer $OPERATOR_TOKEN"
```

**HTTP 200**

<details><summary>Response body (113 lines)</summary>

```json
[
  {
    "id": "5fc8b38e-07b3-47c0-9b78-f00d9ba48d4c",
    "username": "owner@apex.test",
    "firstName": "Company",
    "lastName": "Super Admin",
    "email": "owner@apex.test",
    "emailVerified": false,
    "userProfileMetadata": {
      "attributes": [
        {
          "name": "username",
          "displayName": "${username}",
          "required": true,
          "readOnly": true,
          "validators": {
            "username-prohibited-characters": {
              "ignore.empty.value": true
            },
            "multivalued": {
              "max": "1"
            },
            "length": {
              "max": 255,
              "ignore.empty.value": true,
              "min": 3
            },
            "up-username-not-idn-homograph": {
              "ignore.empty.value": true
            }
          },
          "multivalued": false
        },
        {
          "name": "email",
          "displayName": "${email}",
          "required": false,
          "readOnly": false,
          "validators": {
            "multivalued": {
              "max": "1"
            },
            "length": {
              "max": 255,
              "ignore.empty.value": true
            },
            "email": {
              "ignore.empty.value": true
            }
          },
          "multivalued": false
        },
        {
          "name": "firstName",
          "displayName": "${firstName}",
          "required": false,
          "readOnly": false,
          "validators": {
            "person-name-prohibited-characters": {
              "ignore.empty.value": true
            },
            "multivalued": {
              "max": "1"
            },
            "length": {
              "max": 255,
              "ignore.empty.value": true
            }
          },
          "multivalued": false
        },
        {
          "name": "lastName",
          "displayName": "${lastName}",
          "required": false,
          "readOnly": false,
          "validators": {
            "person-name-prohibited-characters": {
              "ignore.empty.value": true
            },
            "multivalued": {
              "max": "1"
            },
            "length": {
              "max": 255,
              "ignore.empty.value": true
            }
          },
          "multivalued": false
        }
      ],
      "groups": [
        {
          "name": "user-metadata",
          "displayHeader": "User metadata",
          "displayDescription": "Attributes, which refer to user metadata"
        }
      ]
    },
    "enabled": true,
    "createdTimestamp": 1792152103596,
    "totp": false,
    "disableableCredentialTypes": [],
    "requiredActions": [
      "VERIFY_EMAIL",
      "UPDATE_PASSWORD"
    ],
    "notBefore": 0,
    "access": {
      "manage": true
    }
  }
]
```

</details>

### 70. Keycloak admin: set the password (invitation accepted)

```bash
curl -X PUT "$KC/admin/realms/co-114/users/5fc8b38e-07b3-47c0-9b78-f00d9ba48d4c/reset-password" -H "Authorization: Bearer $OPERATOR_TOKEN" -H "Content-Type: application/json" -d '{"type":"password","value":"Apex-Owner-1","temporary":false}'
```

**HTTP 204**

_(empty body)_

### 71. Keycloak admin: verify e-mail, clear required actions

```bash
curl -X PUT "$KC/admin/realms/co-114/users/5fc8b38e-07b3-47c0-9b78-f00d9ba48d4c" -H "Authorization: Bearer $OPERATOR_TOKEN" -H "Content-Type: application/json" -d '{"id":"5fc8b38e-07b3-47c0-9b78-f00d9ba48d4c","username":"owner@apex.test","firstName":"Company","lastName":"Super Admin","email":"owner@apex.test","emailVerified":true,"userProfileMetadata":{"attributes":[{"name":"username","displayName":"${username}","required":true,"readOnly":true,"validators":{"username-prohibited-characters":{"ignore.empty.value":true},"multivalued":{"max":"1"},"length":{"max":255,"ignore.empty.value":true,"min":3},"up-username-not-idn-homograph":{"ignore.empty.value":true}},"multivalued":false},{"name":"email","displayName":"${email}","required":false,"readOnly":false,"validators":{"multivalued":{"max":"1"},"length":{"max":255,"ignore.empty.value":true},"email":{"ignore.empty.value":true}},"multivalued":false},{"name":"firstName","displayName":"${firstName}","required":false,"readOnly":false,"validators":{"person-name-prohibited-characters":{"ignore.empty.value":true},"multivalued":{"max":"1"},"length":{"max":255,"ignore.empty.value":true}},"multivalued":false},{"name":"lastName","displayName":"${lastName}","required":false,"readOnly":false,"validators":{"person-name-prohibited-characters":{"ignore.empty.value":true},"multivalued":{"max":"1"},"length":{"max":255,"ignore.empty.value":true}},"multivalued":false}],"groups":[{"name":"user-metadata","displayHeader":"User metadata","displayDescription":"Attributes, which refer to user metadata"}]},"enabled":true,"createdTimestamp":1792152103596,"totp":false,"disableableCredentialTypes":[],"requiredActions":[],"notBefore":0,"access":{"manage":true}}'
```

**HTTP 204**

_(empty body)_

### 72. Company token (realm co-114)

```bash
curl -X POST "$KC/realms/co-114/protocol/openid-connect/token" -d grant_type=password -d client_id=erp-web -d username=owner@apex.test -d password=***
```

**HTTP 200**

```json
{
  "access_token": "eyJhbGciOiJS...",
  "expires_in": 300,
  "refresh_expires_in": 1800,
  "refresh_token": "eyJhbGciOiJI...",
  "token_type": "Bearer",
  "not-before-policy": 0,
  "session_state": "2ee72ef6-e3f0-4ac4-92a6-5e64cadb6191",
  "scope": "email profile"
}
```

## 9. Apex Autos: the SAME APIs, its own data

### 73. Add model AP-SEDAN

```bash
curl -X POST "$VEHICLE/api/v1/vehicle/models" -H "Authorization: Bearer $TOKEN_B" -H "Content-Type: application/json" -d '{"modelCode":"AP-SEDAN","name":"Apex Sedan","bodyType":"SEDAN","fuelType":"PETROL","basePrice":980000}'
```

**HTTP 201**

```json
{
  "id": 1,
  "modelCode": "AP-SEDAN",
  "name": "Apex Sedan",
  "bodyType": "SEDAN",
  "fuelType": "PETROL",
  "basePrice": 980000,
  "createdAt": "2026-10-16T17:31:45.8826999",
  "createdBy": "owner@apex.test"
}
```

### 74. Same code as Velocity is fine (separate catalog)

```bash
curl -X POST "$VEHICLE/api/v1/vehicle/models" -H "Authorization: Bearer $TOKEN_B" -H "Content-Type: application/json" -d '{"modelCode":"VX-SUV","name":"Apex own VX-SUV","bodyType":"SUV","fuelType":"PETROL","basePrice":1999999}'
```

**HTTP 201**

```json
{
  "id": 2,
  "modelCode": "VX-SUV",
  "name": "Apex own VX-SUV",
  "bodyType": "SUV",
  "fuelType": "PETROL",
  "basePrice": 1999999,
  "createdAt": "2026-10-16T17:31:45.9397905",
  "createdBy": "owner@apex.test"
}
```

### 75. List models: only Apex's

```bash
curl -X GET "$VEHICLE/api/v1/vehicle/models" -H "Authorization: Bearer $TOKEN_B"
```

**HTTP 200**

```json
[
  {
    "id": 1,
    "modelCode": "AP-SEDAN",
    "name": "Apex Sedan",
    "bodyType": "SEDAN",
    "fuelType": "PETROL",
    "basePrice": 980000.00,
    "createdAt": "2026-10-16T23:01:45.8827",
    "createdBy": "owner@apex.test"
  },
  {
    "id": 2,
    "modelCode": "VX-SUV",
    "name": "Apex own VX-SUV",
    "bodyType": "SUV",
    "fuelType": "PETROL",
    "basePrice": 1999999.00,
    "createdAt": "2026-10-16T23:01:45.939791",
    "createdBy": "owner@apex.test"
  }
]
```

### 76. Receive a car

```bash
curl -X POST "$INVENTORY/api/v1/inventory/units" -H "Authorization: Bearer $TOKEN_B" -H "Content-Type: application/json" -d '{"vin":"MA3AP001792152081","modelCode":"AP-SEDAN","color":"White"}'
```

**HTTP 201**

```json
{
  "id": 1,
  "vin": "MA3AP001792152081",
  "modelCode": "AP-SEDAN",
  "color": "WHITE",
  "status": "AVAILABLE",
  "reservedFor": null,
  "createdAt": "2026-10-16T17:31:46.1023618",
  "createdBy": "owner@apex.test"
}
```

### 77. List stock: none of Velocity's cars

```bash
curl -X GET "$INVENTORY/api/v1/inventory/units" -H "Authorization: Bearer $TOKEN_B"
```

**HTTP 200**

```json
[
  {
    "id": 1,
    "vin": "MA3AP001792152081",
    "modelCode": "AP-SEDAN",
    "color": "WHITE",
    "status": "AVAILABLE",
    "reservedFor": null,
    "createdAt": "2026-10-16T23:01:46.102362",
    "createdBy": "owner@apex.test"
  }
]
```

### 78. Routing: Apex's own DB

```bash
curl -X GET "$VEHICLE/api/v1/vehicle/_routing" -H "Authorization: Bearer $TOKEN_B"
```

**HTTP 200**

```json
{
  "companyCode": "CMP_114",
  "connectedTo": "cmp_114_vehicle_db",
  "user": "owner@apex.test",
  "database": "cmp_114_vehicle_db",
  "module": "vehicle"
}
```

### 79. Booking not bought -> 403 MODULE_NOT_ENTITLED

```bash
curl -X GET "$BOOKING/api/v1/booking/bookings" -H "Authorization: Bearer $TOKEN_B"
```

**HTTP 403**

```json
{
  "type": "about:blank",
  "title": "MODULE_NOT_ENTITLED",
  "status": 403,
  "detail": "BOOKING is not in the company's subscription",
  "instance": "/api/v1/booking/bookings",
  "code": "MODULE_NOT_ENTITLED"
}
```

## 10. Apex upgrades with booking (ADD_MODULE job, Apex stays ACTIVE)

### 80. Quote booking (UPGRADE)

```bash
curl -X POST "$CP/api/v1/platform/companies/CMP_114/orders" -H "Authorization: Bearer $PLATFORM_TOKEN" -H "Content-Type: application/json" -d '{"billingPeriod":"MONTHLY","modules":["booking"],"users":5,"storageGb":10}'
```

**HTTP 201**

<details><summary>Response body (74 lines)</summary>

```json
{
  "order": {
    "id": 18,
    "createdAt": "2026-10-16T17:31:46.36234",
    "createdBy": "superadmin",
    "updatedAt": "2026-10-16T17:31:46.36234",
    "updatedBy": "superadmin",
    "version": 0,
    "orderNumber": "ORD/2026-27/00018",
    "companyId": 14,
    "subscriptionId": null,
    "orderKind": "UPGRADE",
    "channel": "ADMIN",
    "billingPeriod": "MONTHLY",
    "currency": "INR",
    "requestedUsers": 5,
    "requestedStorageGb": 10,
    "subtotal": 250.0000,
    "discountTotal": 0.0000,
    "taxableTotal": 250.0000,
    "taxTotal": 0.0000,
    "otherChargesTotal": 0.0000,
    "roundOff": 0.0000,
    "grandTotal": 250.0000,
    "paymentStatus": "UNPAID",
    "status": "QUOTED",
    "calculationVersion": "poc-1",
    "inputJson": "{\"command\": {\"users\": 5, \"bundles\": null, \"modules\": [\"booking\"], \"currency\": \"INR\", \"storageGb\": 10, \"billingPeriod\": \"MONTHLY\", \"idempotencyKey\": \"019e4353-396d-4de5-90ec-cb1788055e56\", \"manualDiscount\": null}, \"existingModules\": [\"vehicle\", \"inventory\"]}",
    "validUntil": "2026-10-31T17:31:46.36234",
    "idempotencyKey": "019e4353-396d-4de5-90ec-cb1788055e56",
    "createdByActor": "superadmin"
  },
  "lines": [
    {
      "id": 30,
      "createdAt": "2026-10-16T17:31:46.363339",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:46.363339",
      "updatedBy": "superadmin",
      "version": 0,
      "billingOrderId": 18,
      "lineNo": 1,
      "lineType": "PLAN",
      "planVersionId": 33,
      "planPriceId": 69,
      "moduleId": 20,
      "bundleId": null,
      "description": "Module Vehicle Booking",
      "quantity": 1.0000,
      "unitPrice": 250.0000,
      "grossAmount": 250.0000,
      "lineDiscountAmount": 0.0000,
      "taxableAmount": 250.0000,
      "taxAmount": 0.0000,
      "lineTotal": 250.0000
    }
  ],
  "discounts": [],
  "warnings": [
    {
      "id": 18,
      "createdAt": "2026-10-16T17:31:46.364339",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:46.364339",
      "updatedBy": "superadmin",
      "version": 0,
      "billingOrderId": 18,
      "code": "TAX_RULE_NOT_CONFIGURED",
      "detail": "No APPROVED tax rule (Q-04); tax = 0"
    }
  ],
  "payments": [],
  "invoice": null
}
```

</details>

### 81. Confirm

```bash
curl -X POST "$CP/api/v1/platform/orders/18/confirm" -H "Authorization: Bearer $PLATFORM_TOKEN"
```

**HTTP 200**

<details><summary>Response body (74 lines)</summary>

```json
{
  "order": {
    "id": 18,
    "createdAt": "2026-10-16T17:31:46.36234",
    "createdBy": "superadmin",
    "updatedAt": "2026-10-16T17:31:46.554004",
    "updatedBy": "superadmin",
    "version": 2,
    "orderNumber": "ORD/2026-27/00018",
    "companyId": 14,
    "subscriptionId": 15,
    "orderKind": "UPGRADE",
    "channel": "ADMIN",
    "billingPeriod": "MONTHLY",
    "currency": "INR",
    "requestedUsers": 5,
    "requestedStorageGb": 10,
    "subtotal": 250.0000,
    "discountTotal": 0.0000,
    "taxableTotal": 250.0000,
    "taxTotal": 0.0000,
    "otherChargesTotal": 0.0000,
    "roundOff": 0.0000,
    "grandTotal": 250.0000,
    "paymentStatus": "UNPAID",
    "status": "AWAITING_PAYMENT",
    "calculationVersion": "poc-1",
    "inputJson": "{\"command\": {\"users\": 5, \"bundles\": null, \"modules\": [\"booking\"], \"currency\": \"INR\", \"storageGb\": 10, \"billingPeriod\": \"MONTHLY\", \"idempotencyKey\": \"019e4353-396d-4de5-90ec-cb1788055e56\", \"manualDiscount\": null}, \"existingModules\": [\"vehicle\", \"inventory\"]}",
    "validUntil": "2026-10-31T17:31:46.36234",
    "idempotencyKey": "019e4353-396d-4de5-90ec-cb1788055e56",
    "createdByActor": "superadmin"
  },
  "lines": [
    {
      "id": 30,
      "createdAt": "2026-10-16T17:31:46.363339",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:46.363339",
      "updatedBy": "superadmin",
      "version": 0,
      "billingOrderId": 18,
      "lineNo": 1,
      "lineType": "PLAN",
      "planVersionId": 33,
      "planPriceId": 69,
      "moduleId": 20,
      "bundleId": null,
      "description": "Module Vehicle Booking",
      "quantity": 1.0000,
      "unitPrice": 250.0000,
      "grossAmount": 250.0000,
      "lineDiscountAmount": 0.0000,
      "taxableAmount": 250.0000,
      "taxAmount": 0.0000,
      "lineTotal": 250.0000
    }
  ],
  "discounts": [],
  "warnings": [
    {
      "id": 18,
      "createdAt": "2026-10-16T17:31:46.364339",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:46.364339",
      "updatedBy": "superadmin",
      "version": 0,
      "billingOrderId": 18,
      "code": "TAX_RULE_NOT_CONFIGURED",
      "detail": "No APPROVED tax rule (Q-04); tax = 0"
    }
  ],
  "payments": [],
  "invoice": null
}
```

</details>

### 82. Pay by bank transfer (needs verification)

```bash
curl -X POST "$CP/api/v1/platform/orders/18/payments" -H "Authorization: Bearer $PLATFORM_TOKEN" -H "Content-Type: application/json" -d '{"mode":"BANK_TRANSFER","amount":250.0000,"instrumentRef":"UTR1792152089"}'
```

**HTTP 201**

<details><summary>Response body (102 lines)</summary>

```json
{
  "order": {
    "id": 18,
    "createdAt": "2026-10-16T17:31:46.36234",
    "createdBy": "superadmin",
    "updatedAt": "2026-10-16T17:31:46.656224",
    "updatedBy": "superadmin",
    "version": 3,
    "orderNumber": "ORD/2026-27/00018",
    "companyId": 14,
    "subscriptionId": 15,
    "orderKind": "UPGRADE",
    "channel": "ADMIN",
    "billingPeriod": "MONTHLY",
    "currency": "INR",
    "requestedUsers": 5,
    "requestedStorageGb": 10,
    "subtotal": 250.0000,
    "discountTotal": 0.0000,
    "taxableTotal": 250.0000,
    "taxTotal": 0.0000,
    "otherChargesTotal": 0.0000,
    "roundOff": 0.0000,
    "grandTotal": 250.0000,
    "paymentStatus": "PENDING_VERIFICATION",
    "status": "AWAITING_PAYMENT",
    "calculationVersion": "poc-1",
    "inputJson": "{\"command\": {\"users\": 5, \"bundles\": null, \"modules\": [\"booking\"], \"currency\": \"INR\", \"storageGb\": 10, \"billingPeriod\": \"MONTHLY\", \"idempotencyKey\": \"019e4353-396d-4de5-90ec-cb1788055e56\", \"manualDiscount\": null}, \"existingModules\": [\"vehicle\", \"inventory\"]}",
    "validUntil": "2026-10-31T17:31:46.36234",
    "idempotencyKey": "019e4353-396d-4de5-90ec-cb1788055e56",
    "createdByActor": "superadmin"
  },
  "lines": [
    {
      "id": 30,
      "createdAt": "2026-10-16T17:31:46.363339",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:46.363339",
      "updatedBy": "superadmin",
      "version": 0,
      "billingOrderId": 18,
      "lineNo": 1,
      "lineType": "PLAN",
      "planVersionId": 33,
      "planPriceId": 69,
      "moduleId": 20,
      "bundleId": null,
      "description": "Module Vehicle Booking",
      "quantity": 1.0000,
      "unitPrice": 250.0000,
      "grossAmount": 250.0000,
      "lineDiscountAmount": 0.0000,
      "taxableAmount": 250.0000,
      "taxAmount": 0.0000,
      "lineTotal": 250.0000
    }
  ],
  "discounts": [],
  "warnings": [
    {
      "id": 18,
      "createdAt": "2026-10-16T17:31:46.364339",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:46.364339",
      "updatedBy": "superadmin",
      "version": 0,
      "billingOrderId": 18,
      "code": "TAX_RULE_NOT_CONFIGURED",
      "detail": "No APPROVED tax rule (Q-04); tax = 0"
    }
  ],
  "payments": [
    {
      "id": 20,
      "createdAt": "2026-10-16T17:31:46.643224",
      "createdBy": "superadmin",
      "updatedAt": "2026-10-16T17:31:46.651224",
      "updatedBy": "superadmin",
      "version": 1,
      "paymentNumber": "PAY/2026-27/00020",
      "receiptNumber": "RCT/2026-27/00020",
      "companyId": 14,
      "billingOrderId": 18,
      "invoiceId": null,
      "paymentChannel": "MANUAL",
      "paymentMode": "BANK_TRANSFER",
      "amount": 250.0000,
      "currency": "INR",
      "status": "PENDING_VERIFICATION",
      "instrumentRef": "UTR1792152089",
      "instrumentDate": null,
      "bankName": null,
      "verificationRequired": true,
      "receivedAt": "2026-10-16T17:31:46.643224",
      "confirmedAt": null,
      "receivedBy": "superadmin",
      "rejectionReason": null,
      "idempotencyKey": "1faa2ee4-6275-4d0d-bf33-24ede33bd2e1"
    }
  ],
  "invoice": null
}
```

</details>

### 83. Transfer received

```bash
curl -X POST "$CP/api/v1/platform/payments/20/verify" -H "Authorization: Bearer $PLATFORM_TOKEN"
```

**HTTP 200**

```json
{
  "id": 20,
  "createdAt": "2026-10-16T17:31:46.643224",
  "createdBy": "superadmin",
  "updatedAt": "2026-10-16T17:31:46.8216283",
  "updatedBy": "superadmin",
  "version": 3,
  "paymentNumber": "PAY/2026-27/00020",
  "receiptNumber": "RCT/2026-27/00020",
  "companyId": 14,
  "billingOrderId": 18,
  "invoiceId": 12,
  "paymentChannel": "MANUAL",
  "paymentMode": "BANK_TRANSFER",
  "amount": 250.0000,
  "currency": "INR",
  "status": "CONFIRMED",
  "instrumentRef": "UTR1792152089",
  "instrumentDate": null,
  "bankName": null,
  "verificationRequired": true,
  "receivedAt": "2026-10-16T17:31:46.643224",
  "confirmedAt": "2026-10-16T17:31:46.8070741",
  "receivedBy": "superadmin",
  "rejectionReason": null,
  "idempotencyKey": "1faa2ee4-6275-4d0d-bf33-24ede33bd2e1"
}
```

### 84. Booking now works for Apex

```bash
curl -X POST "$BOOKING/api/v1/booking/bookings" -H "Authorization: Bearer $TOKEN_B" -H "Content-Type: application/json" -d '{"customerName":"Meera Shah","customerPhone":"9800000002","modelCode":"AP-SEDAN","color":"White"}'
```

**HTTP 201**

```json
{
  "id": 1,
  "bookingNo": "BK-20261016-RHV68D",
  "customerName": "Meera Shah",
  "customerPhone": "9800000002",
  "modelCode": "AP-SEDAN",
  "color": "WHITE",
  "vin": "MA3AP001792152081",
  "price": 980000.00,
  "status": "BOOKED",
  "createdAt": "2026-10-16T17:31:49.1514198",
  "createdBy": "owner@apex.test"
}
```

### 85. Apex's bookings

```bash
curl -X GET "$BOOKING/api/v1/booking/bookings" -H "Authorization: Bearer $TOKEN_B"
```

**HTTP 200**

```json
[
  {
    "id": 1,
    "bookingNo": "BK-20261016-RHV68D",
    "customerName": "Meera Shah",
    "customerPhone": "9800000002",
    "modelCode": "AP-SEDAN",
    "color": "WHITE",
    "vin": "MA3AP001792152081",
    "price": 980000.00,
    "status": "BOOKED",
    "createdAt": "2026-10-16T23:01:49.15142",
    "createdBy": "owner@apex.test"
  }
]
```

## 11. Guard rails

### 86. No token -> 401

```bash
curl -X GET "$VEHICLE/api/v1/vehicle/models"
```

**HTTP 401**

```text
(no body) WWW-Authenticate: Bearer
```

### 87. Garbage token -> 401

```bash
curl -X GET "$VEHICLE/api/v1/vehicle/models" -H "Authorization: Bearer $GARBAGE"
```

**HTTP 401**

```text
(no body) WWW-Authenticate: Bearer error="invalid_token", error_description="Invalid JWT serialization: Missing dot delimiter(s)", error_uri="https://tools.ietf.org/html/rfc6750#section-3.1"
```

### 88. Platform token on a business API -> 401

```bash
curl -X GET "$VEHICLE/api/v1/vehicle/models" -H "Authorization: Bearer $PLATFORM_TOKEN"
```

**HTTP 401**

```text
(no body) WWW-Authenticate: Bearer error="invalid_token", error_description="Invalid issuer", error_uri="https://tools.ietf.org/html/rfc6750#section-3.1"
```

### 89. Company token on the platform API -> 401

```bash
curl -X GET "$CP/api/v1/platform/companies" -H "Authorization: Bearer $TOKEN_A"
```

**HTTP 401**

```text
(no body) WWW-Authenticate: Bearer error="invalid_token", error_description="An error occurred while attempting to decode the Jwt: Signed JWT rejected: Another algorithm expected, or no matching key(s) found", error_uri="https://tools.ietf.org/html/rfc6750#section-3.1"
```

### 90. Spoofed X-Company-Code header is ignored

```bash
curl -X GET "$INVENTORY/api/v1/inventory/_routing" -H "Authorization: Bearer $TOKEN_A" -H "Content-Type: application/json" -d 'X-Company-Code: CMP_114'
```

**HTTP 200**

```json
{
  "connectedTo": "cmp_113_inventory_db",
  "user": "owner@velocity.test",
  "database": "cmp_113_inventory_db",
  "module": "inventory",
  "companyCode": "CMP_113"
}
```

### 91. Suspend Velocity Motors

```bash
curl -X POST "$CP/api/v1/platform/companies/CMP_113/status" -H "Authorization: Bearer $PLATFORM_TOKEN" -H "Content-Type: application/json" -d '{"status":"SUSPENDED","reason":"payment overdue"}'
```

**HTTP 200**

```json
{
  "id": 13,
  "companyCode": "CMP_113",
  "legalName": "Velocity Motors Pvt Ltd",
  "displayName": "Velocity Motors Pvt Ltd",
  "slug": "velocity-motors-pvt-ltd-6",
  "loginCode": "HFJY9AEJ",
  "status": "SUSPENDED",
  "statusReason": "payment overdue",
  "onboardingType": "NEW",
  "superAdminEmail": "owner@velocity.test",
  "realm": "co-113",
  "activatedAt": "2026-10-16T17:31:35.757625"
}
```

### 92. Velocity blocked -> 403 COMPANY_SUSPENDED

```bash
curl -X GET "$BOOKING/api/v1/booking/bookings" -H "Authorization: Bearer $TOKEN_A"
```

**HTTP 403**

```json
{
  "type": "about:blank",
  "title": "COMPANY_SUSPENDED",
  "status": 403,
  "detail": "Company is SUSPENDED",
  "instance": "/api/v1/booking/bookings",
  "code": "COMPANY_SUSPENDED"
}
```

### 93. Apex unaffected

```bash
curl -X GET "$BOOKING/api/v1/booking/bookings" -H "Authorization: Bearer $TOKEN_B"
```

**HTTP 200**

```json
[
  {
    "id": 1,
    "bookingNo": "BK-20261016-RHV68D",
    "customerName": "Meera Shah",
    "customerPhone": "9800000002",
    "modelCode": "AP-SEDAN",
    "color": "WHITE",
    "vin": "MA3AP001792152081",
    "price": 980000.00,
    "status": "BOOKED",
    "createdAt": "2026-10-16T23:01:49.15142",
    "createdBy": "owner@apex.test"
  }
]
```

### 94. Reactivate Velocity Motors

```bash
curl -X POST "$CP/api/v1/platform/companies/CMP_113/status" -H "Authorization: Bearer $PLATFORM_TOKEN" -H "Content-Type: application/json" -d '{"status":"ACTIVE","reason":"paid"}'
```

**HTTP 200**

```json
{
  "id": 13,
  "companyCode": "CMP_113",
  "legalName": "Velocity Motors Pvt Ltd",
  "displayName": "Velocity Motors Pvt Ltd",
  "slug": "velocity-motors-pvt-ltd-6",
  "loginCode": "HFJY9AEJ",
  "status": "ACTIVE",
  "statusReason": "paid",
  "onboardingType": "NEW",
  "superAdminEmail": "owner@velocity.test",
  "realm": "co-113",
  "activatedAt": "2026-10-16T17:31:35.757625"
}
```

### 95. Velocity's data is intact

```bash
curl -X GET "$BOOKING/api/v1/booking/bookings" -H "Authorization: Bearer $TOKEN_A"
```

**HTTP 200**

```json
[
  {
    "id": 1,
    "bookingNo": "BK-20261016-MH7D19",
    "customerName": "Ravi Kumar",
    "customerPhone": "9800000001",
    "modelCode": "VX-SUV",
    "color": "RED",
    "vin": "MA1VX001792152081",
    "price": 1450000.00,
    "status": "DELIVERED",
    "createdAt": "2026-10-16T23:01:39.151523",
    "createdBy": "owner@velocity.test"
  },
  {
    "id": 2,
    "bookingNo": "BK-20261016-AY2ABN",
    "customerName": "Anita Rao",
    "customerPhone": "9800000003",
    "modelCode": "VX-SUV",
    "color": "RED",
    "vin": "MA1VX001792152082",
    "price": 1450000.00,
    "status": "CANCELLED",
    "createdAt": "2026-10-16T23:01:39.316487",
    "createdBy": "owner@velocity.test"
  }
]
```
