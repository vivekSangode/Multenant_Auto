# PHASE 09 — Implementation Plan (File Storage Isolation and Usage)

| Item | Value |
|---|---|
| Phase document | [PHASE_09_FILE_STORAGE_ISOLATION_AND_USAGE.md](../PHASE_09_FILE_STORAGE_ISOLATION_AND_USAGE.md) (objective, acceptance §13). This plan **adds no decision**. |
| Design sources | PHASE_01 §0 (CD-01, CD-03, CD-05, CD-11, CD-23, CD-26, CD-27), §6.1.3, §6.1.4; developer guide §10; master §17 |
| Status | **DRAFT FOR OWNER REVIEW.** PHASE_09 is LOCKED until PHASE_06 and PHASE_08 are DONE; task 3 also needs the PHASE_10 evaluator (§3, D9-1). |
| Author / date | agent (Claude Opus 5.5), 2026-10-03 |
| Evidence | Local code inspection: COMMON-ATTACHMENT `services/AttachmentService` (`verifyTenantAccess` prefix check `bucket.startsWith(orgDns + "-")`; `uploadedBy` / `deletedBy` never set), `bucket/PropertyBasedBucketResolver`, `storage/MinioStorageService` (lazy `makeBucket`), `enums/AttachmentModule` = CRM, HRMS, QC, COMPLIANCE, LOGISTICS, MINING, PRODUCTION, STORE, **VRM**, ASSET, **ACCOUNT**, TICKETS, CHAT, ATTENDANCE, RND, PAYROLL, CONTROL_PANEL; FILE-SERVICE `FileController` (no module/permission check), `AttachmentPurgeScheduler`; ACCOUNT-BOOK local files through `BillDocumentService`, `CustomerDocumentService`, `ExpenseReceiptService`, `InvoiceDocumentService`, `PurchaseOrderDocumentService`, `QuoteDocumentService`, `SalesOrderDocumentService` (compose volume `./account-book-files:/app/uploads`). The `attachment_metadata` structure stays exactly as decided on 2026-10-02 (19 columns, no organization column). |
| Schema | Company attachment DB: one new table `attachment_usage_outbox` (COMMON-ATTACHMENT Liquibase changelog — tenant Liquibase, **not** affected by CD-27). CP usage tables come from PHASE_06 (entities, CD-27). |
| Protected hosts | Tests on Testcontainers MinIO / MySQL only. The future Shivam bucket import runs only on owner-supplied copies (CD-05); never against `192.168.5.52/53`. |

---

## 1. Summary

1. **Registry-driven buckets:** `RegistryBucketResolver` (company, module) → VERIFIED BUCKET row; no runtime creation; `STORAGE_NOT_PROVISIONED` (503).
2. **Exact checks:** bucket equality with the registry row, key prefix = row module, row module = request module; `uploadedBy` / `deletedBy` set; enum cleaned.
3. **Authorization** of FILE-SERVICE downloads: entitlement + module realm role + `<module>.attachment.read/write` (PHASE_10 evaluator).
4. **Usage metering:** reserve before the PUT, idempotent deltas after commit with a local outbox, daily reconciliation, 80 % / 95 % notifications.
5. **Bucket policy and quota** at provisioning; **`BucketImportTool`** (future controlled import); **ACCOUNT-BOOK files** moved into `cmp-<n>-account-book-bucket`; removal of the tenant-less library purge job.

Size estimate: 9 tasks, about 45–60 files over COMMON-ATTACHMENT, FILE-SERVICE, ACCOUNT-BOOK, CP; about 20 test classes.

---

## 2. Preconditions

| # | Precondition | Tracker |
|---|---|---|
| 1 | PHASE_06 (allowances, `AllowanceService.assertCanStore`, `tbl_company_usage*`) and PHASE_08 (`CREATE_BUCKET` per purchased module, registry BUCKET rows) DONE | phases |
| 2 | P10-T13 (`PermissionEvaluator`) DONE before P09-T3 (D9-1) | PHASE_10 |
| 3 | P07-T11 platform mail sender available for the threshold emails (CD-23) | PHASE_07 |
| 4 | S0-2 item 1 decided (control-panel bucket registered against a PLATFORM_CORE catalog row) — affects the resolver key for `CONTROL_PANEL` files | S0-2 |

---

## 3. Owner decisions / clarifications

| ID | Question | Options | Recommendation |
|---|---|---|---|
| **D9-1** (sequencing, report) | P09-T3 needs the PHASE_10 permission evaluator, while PHASE_10 is a separate later phase (PHASE_10 does not depend on PHASE_09). | Do P09-T1/T2/T4–T9 in PHASE_09; run P09-T3 right after P10-T13. PHASE_09 can be REVIEW without T3; its acceptance 4 is proven after P10-T13. | As described; no ownership change. |
| **D9-2** (report) | `AttachmentModule` `RND` vs catalog code `research-and-development` (`db_token` `rnd`) and `CONTROL_PANEL` (no catalog row in master §11.1). | Map enum → catalog by an explicit table (`RND` → `research-and-development`; `CONTROL_PANEL` → the PLATFORM_CORE row per S0-2 item 1); remove `VRM`, `ACCOUNT`. Key prefixes in existing object keys stay unchanged. | As described. |

---

## 4. Task order

```text
T9 remove tenant-less purge job ─> T1 RegistryBucketResolver + no lazy creation ─> T2 exact checks + audit fields + enum ─> T6 bucket policy/quota (PHASE_08 executor) ─> T4 usage reserve/delta + outbox ─> T5 reconciliation + thresholds ─> T8 ACCOUNT-BOOK files ─> T7 BucketImportTool ─> T3 FILE authorization (after P10-T13)
```

---

## 5. Task details

### T1 — `RegistryBucketResolver`; no lazy creation (P09-T1)

| Item | Detail |
|---|---|
| Files | COMMON-ATTACHMENT `bucket/RegistryBucketResolver` (reads the BUCKET entry of the cached tenant metadata for (company, module); the CP metadata payload gets an additive `buckets` map `{moduleCode: bucketName}` from VERIFIED rows — `TenantMetadata` new optional field, old clients ignore it); `PropertyBasedBucketResolver` kept only behind `attachment.bucket-resolver=property` for `dev`; `MinioStorageService`: `makeBucket` removed, missing bucket → `StorageNotProvisionedException` → 503 `STORAGE_NOT_PROVISIONED`. |
| Tests | Unregistered module → 503 (acceptance 1, last sentence); resolver never derives a name from properties in non-dev profiles. |

### T2 — Exact tenant check, audit fields, enum (P09-T2)

| Item | Detail |
|---|---|
| Files | `AttachmentService.verifyTenantAccess`: `row.bucket.equals(resolver.bucketFor(company, row.module))`, `row.objectKey.startsWith(moduleKeyPrefix(row.module) + "/")`, `row.module == requestModule`; mismatch → 403 + security audit event; `uploadedBy` / `deletedBy` from `TenantContext` user (`sub`); `AttachmentModule` cleaned per D9-2. |
| Tests | Prefix-collision case `cmp-1-crm-bucket` vs `cmp-11-crm-bucket`; crafted row with another company's bucket or another module's bucket refused on read (acceptance 1); audit fields set. |

### T6 — Bucket policy and quota at provisioning (P09-T6)

| Item | Detail |
|---|---|
| Files | PHASE_08 `BucketProvisioner` extended: private policy (no anonymous access), versioning off, optional quota = storage allowance when the MinIO deployment supports it (feature probe; the application check stays authoritative). Environment MinIO keys (CD-03 principle). |
| Tests | MinIO Testcontainers: anonymous GET denied; quota applied when supported, skipped otherwise. |

### T4 — Usage reserve and delta APIs + outbox (P09-T4)

| Item | Detail |
|---|---|
| CP | `platform/usage/{StorageUsageController (internal), StorageReservationService}`: `POST /api/v1/internal/platform/companies/{companyCode}/usage/storage/reserve` (conditional update via `AllowanceService.assertCanStore`; reservation id; timeout release job) and `POST …/usage/storage` (idempotent by `attachmentId` + `reason`); M2M + `azp` (PHASE_03). |
| COMMON-ATTACHMENT | `usage/{StorageUsageRecorder, AttachmentUsageOutbox, UsageOutboxWorker}`; changelog `db/changelog/attachment/changelog-attachment_usage_outbox.xml` (tenant Liquibase; `LIQUIBASE_CONVENTIONS.md`); reserve before the MinIO PUT (409 `STORAGE_LIMIT_REACHED`, no object written); delta after the metadata commit; retry from the outbox when the CP is down. Counting rule: every row with status ≠ PURGED counts; hard delete subtracts. |
| Tests | Limit 10 MB, used 9.5 MB, 1 MB upload → 409 and no object (acceptance 2); downloads/deletes still work; outbox retry after a simulated CP outage; idempotent deltas. |

### T5 — Daily reconciliation and thresholds (P09-T5)

| Item | Detail |
|---|---|
| Files | CP `platform/usage/StorageReconciliationJob` (daily, ShedLock, per company: sum of `size` over the company attachment DB (read-only internal call to COMMON-ATTACHMENT, not a direct cross-DB query) + bucket listing of all the company's module buckets; writes `tbl_company_usage_snapshot`, corrects drift with an audit entry); `StorageThresholdNotifier` (80 % and 95 %; in-app via NOTIFICATION; platform email via P07-T11 / `PlatformNotifier`). |
| Tests | After uploads, replaces and purges the stored usage equals the reconciled sum ± 0 bytes (acceptance 3); thresholds fire once per crossing. |

### T8 — ACCOUNT-BOOK file inventory and migration (P09-T8)

| Item | Detail |
|---|---|
| Inventory | The 7 document services (bill, customer, expense receipt, invoice, purchase order, quote, sales order) and their DB path columns; output `plans/PHASE_09_ACCOUNT_BOOK_FILE_INVENTORY.md`. |
| Code | Route all writes through COMMON-ATTACHMENT under the key prefix `account-book/<entity>/…`; reads fall back to the old volume (read-only) during the transition; a one-off, operator-run copy command copies existing files of a **development** company into `cmp-<n>-account-book-bucket` and registers metadata (verified by size + checksum); the volume stays untouched. Production/Shivam files: only through the PHASE_11/12 import with owner-supplied copies (CD-05). |
| Tests | No file is written to the local volume (acceptance 6); fallback read works; copy idempotent. |

### T7 — `BucketImportTool` (P09-T7)

| Item | Detail |
|---|---|
| Files | Operator CLI (CP profile or the PHASE_11 toolkit module, per PHASE_11's decision): `plan` (dry run: object counts and bytes per source → target), `copy` (keys unchanged; size + ETag per object; mapping log), `rewrite-metadata` (per verified object, `attachment_metadata.bucket`; reversible from the mapping log), `verify`. Sources never deleted. Allowed sources: LEGACY development `org-<id>-<module>-bucket` (CD-26) or owner-supplied copies. |
| Tests | MinIO Testcontainers: copy + verify; second run no-op (acceptance 5); verification failure leaves metadata unchanged. |

### T9 — Remove the tenant-less library purge job (P09-T9)

| Item | Detail |
|---|---|
| Work | Remove `PurgeExpiredAttachmentsJob` (flag-off, tenant-less); the per-company `AttachmentPurgeScheduler` (FILE-SERVICE) stays and now also emits usage deltas (T4). Owner approval for the deletion. |
| Tests | PHASE_03 ArchUnit rule passes. |

### T3 — FILE-SERVICE authorization (P09-T3, after P10-T13)

| Item | Detail |
|---|---|
| Files | FILE-SERVICE `FileController`: module from the attachment row; require company entitlement for the module (`COMPANY_MODULES`), a role of that module in the verified token (`realm_access.roles`), and `<module>.attachment.read` / `.write` through `@RequiresPermission` (PHASE_10). Downloads stay proxied (no presigned URLs). |
| Tests | User without an HRMS role cannot download an HRMS file; user with an HRMS role cannot when HRMS is not entitled (acceptance 4). |

---

## 6. Test plan summary

| Level | Coverage | Acceptance (PHASE_09 §13) |
|---|---|---|
| Unit | resolver, exact check incl. prefix collision, counting rule | 1 |
| MinIO + MySQL IT | upload/replace/purge/usage, limit rejection, outbox retry, reconciliation | 1, 2, 3 |
| Authorization | FILE-SERVICE matrix (after P10-T13) | 4 |
| Tool ITs | import idempotency, verification failures | 5 |
| ACCOUNT-BOOK | no local writes | 6 |
| Regression | S0-5 baseline (COMMON-ATTACHMENT 35 unit tests, FILE 12 + 11 IT) | — |

---

## 7. Risks

| Risk | Mitigation |
|---|---|
| Usage drift | Outbox + daily reconciliation |
| MinIO quota support varies | Feature probe; application check authoritative |
| ACCOUNT-BOOK file paths unknown | Inventory first; read fallback; no deletion |
| Bucket count = companies × modules | 63-char names; bucket-count monitoring; capacity planning (CD-18) |
| Import copy errors | Per-object verification; sources kept; reversible metadata rewrite |

---

## 8. Documentation findings

| # | Finding | Handling |
|---|---|---|
| F-1 | P09-T3 depends on PHASE_10 | D9-1 (report) |
| F-2 | `AttachmentModule` names vs catalog codes | D9-2 (report) |

---

## 9. Definition of done (PHASE_09)

PHASE_09 §13 acceptance 1, 2, 3, 5, 6 proven in PHASE_09; acceptance 4 after P10-T13; every task row REVIEW with evidence; owner sets DONE.
