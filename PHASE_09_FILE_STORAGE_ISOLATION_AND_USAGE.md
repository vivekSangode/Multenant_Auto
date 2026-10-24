# PHASE 09 — File Storage Isolation and Usage

| Item | Value |
|---|---|
| Master architecture | [SAAS_ARCHITECTURE_MASTER.md](SAAS_ARCHITECTURE_MASTER.md) §6.5, §13.5, §17 |
| Design source | [PHASE_01 §6.1.3–6.1.4](PHASE_01_SAAS_FOUNDATION.md) |
| Depends on | [PHASE_08](PHASE_08_COMPANY_PROVISIONING.md) (bucket creation step), [PHASE_06](PHASE_06_SUBSCRIPTIONS_AND_ENTITLEMENTS.md) (storage allowance) |
| Enables | PHASE_10, PHASE_11, PHASE_12 |
| Status | **PARTIALLY_COMPLETED** |

> **Revision 2026-10-03 (v5, decision register):**
> - **CONFIRMED DECISION CD-01:** a dedicated bucket **per company per purchased module** (`cmp-<n>-<module>-bucket`), never shared. The v2–v4 single company bucket `org-<n>-files` and the consolidation of module buckets into it are **withdrawn**.
> - The existing `TenantResourceNaming.bucketName(org, module)` pattern is kept with the prefix changed from the LEGACY `org-` to `cmp-` (CD-26). What else changes: buckets are created at provisioning (not lazily), registered and checked exactly.
> - Storage access keys are environment-level infrastructure configuration, like the DB credentials (CD-03).
> - **FUTURE CAPABILITY:** a per-company MinIO service account limited to that company's buckets.

---

## 1. Objective

1. **One dedicated bucket per company per purchased module** (`cmp-<n>-<module>-bucket`, e.g. `cmp-101-crm-bucket`). It is created at provisioning or `ADD_MODULE`, registered (`tbl_company_resource` BUCKET with `module_id`), private, and never created at runtime.
2. **Exact tenant checks** on every file operation: the bucket equals the registry bucket for (company, module), and the key prefix matches the module.
3. **Module-level authorization** for file access: company entitlement for the module, a module role of the user in the company realm, and the read permission of the role policy (CD-11; role ≠ entitlement).
4. **Storage usage metering** per company, plus **limit enforcement** at upload.
5. Move **all company files** into the company's module buckets, including ACCOUNT-BOOK's local files (into `cmp-<n>-account-book-bucket`).
6. Development module buckets carry the LEGACY `org-` prefix and are **not** reused: development companies are re-provisioned with `cmp-` buckets (test data re-created or copied with the import tool). Define the copy procedure for Shivam's legacy buckets into the new module buckets (future controlled import, CD-05).

## 2. Existing implementation relevant to this phase

> **P02-T11 note (stage G, 2026-10-05):** the tables of this section describe the code as inspected on 2026-10-03. P02-T11 stages A–F (2026-10-04/05, REVIEW) have since replaced the organization tenancy: the CP organization / customer tier (`Customer`, `Organization`, `OrganizationDatabase`, `OrganizationUser`, `OrganizationUserBusinessRole`, `OrganizationQuotaService`, `CompanyOrganizationService`, `OrganizationGroupProvisioner`, `@OrganizationScoped`, `PlatformOrgAdminController`, …) is deleted (stage C); `InternalOrganizationController` → `InternalCompanyController` (`/api/v1/internal/platform/companies/...`), `OrganizationEntitlementSyncListener` → `CompanyEntitlementSyncListener`, `CustomerUser` → `CompanyUser` (`tbl_company_user`), `CustomerUsage` → `CompanyUsage`; runtime `TenantContext.companyCode`, header `X-Company-Code`, Redis `COMPANY_MODULES:<CMP_n>`, resource names `cmp_<n>_…` / `cmp-<n>-…` (stages A / B); gateway `CompanyContextFilter` / `CompanySelection`, company from the verified `tenant_id` claim, no `Active-Org` (stage D). Read old names in this section as these replacements.

| Component | Location | Notes (`CODE-INSPECTED`) |
|---|---|---|
| `AttachmentService` | `COMMON-ATTACHMENT-SERVICE/src/main/java/nyaalkaran/erp/attachment/services/AttachmentService.java` | upload / replace / download / delete (30-day cooling) / restore / hardDelete / purge per org; `verifyTenantAccess` uses `bucket.startsWith(orgDns + "-")`, a **prefix** match; `uploadedBy` / `deletedBy` are never set |
| Bucket resolver | `bucket/PropertyBasedBucketResolver.java` | `org-<id>-<module bucket>` from `attachment.buckets.<module>` |
| Storage | `storage/MinioStorageService.java` | `bucketExists` + `makeBucket` on first upload (lazy); ignores `minio.bucket-auto-create` |
| Key generator | `key/DefaultObjectKeyGenerator.java` | `<module>/<entity>/<formSegment>/<entityId>/[subforms/<sub>/]documents/<attachmentId>/v<n>/<file>` |
| Metadata | `model/AttachmentMetadata.java`; changelog `src/main/resources/db/changelog/attachment/changelog-attachment_metadata.xml` | `size` BIGINT per row; statuses ACTIVE, REPLACED, PENDING_DELETE, DELETED, PURGED, SYSTEM; no organization column (isolation by DB and bucket) |
| Module enum | `enums/AttachmentModule.java` | Still contains the removed `VRM` and `ACCOUNT` |
| MinIO client | `config/MinioConfig.java` | One key pair for all tenants |
| FILE-SERVICE | `FILE-SERVICE/.../controller/FileController.java`; `scheduler/AttachmentPurgeScheduler.java` | No module or permission check; the purge is per active org with a lock |
| ACCOUNT-BOOK files | Local volume `./account-book-files` (compose) | Outside MinIO, not measured |
| Library purge job | `PurgeExpiredAttachmentsJob` | Tenant-less, flag-off |

## 3. Existing completed work

| Item | Status |
|---|---|
| Attachment metadata in the per-company attachment DB | COMPLETED |
| Tenant-required upload path (fails closed without a tenant) | COMPLETED |
| Versioning, soft delete, restore, scheduled purge per organization | COMPLETED |
| Object key layout with the module prefix | COMPLETED (reused unchanged) |
| Per-row `size` | COMPLETED (basis for metering) |

## 4. Reusable components

`AttachmentService` (with modifications), `DefaultObjectKeyGenerator` (unchanged), `AttachmentMetadata` and the changelog (unchanged structure), `AttachmentPurgeScheduler` (unchanged pattern), the FILE-SERVICE download proxy (with authorization added).

## 5. Work requiring modification or redesign

| Item | Change |
|---|---|
| `PropertyBasedBucketResolver` | **Redesign**: `RegistryBucketResolver` returns the bucket for (company, module) from the cached tenant metadata (registry row `BUCKET` with `module_id`). The bucket name is never computed from properties at runtime. An unregistered module bucket → 503 `STORAGE_NOT_PROVISIONED`. |
| `MinioStorageService` lazy creation | **Removed.** A missing bucket returns 503 `STORAGE_NOT_PROVISIONED`. |
| `verifyTenantAccess` | Today a prefix check. Becomes: exact equality with the registry bucket of (company, row module), a key prefix equal to the row's module, and row module = request module |
| `uploadedBy`, `deletedBy` | Set from the TenantContext user |
| FileController | Requires the company's entitlement for the attachment module, a module role in the verified token, and `<module>.attachment.read` / `.write` (PHASE_10 evaluator) |
| `AttachmentModule` enum | Remove `VRM` and `ACCOUNT`; align with the `tbl_module` codes (keeping `RND` as the token alias of `research-and-development`) |
| ACCOUNT-BOOK local files | Stored through COMMON-ATTACHMENT (or the `StorageService` API) under the key prefix `account-book/` |
| Library tenant-less purge job | Removed |

## 6. New development required

### 6.1 Bucket provisioning completion (PHASE_08 step `CREATE_BUCKET`)
- Private bucket policy (no anonymous access). Versioning off (the application versions files).
- An optional MinIO bucket quota equal to the storage allowance, when the MinIO deployment supports it. It is defence in depth only; the application check is authoritative.
- **Credentials:** the MinIO access keys are environment-level infrastructure configuration (the same principle as CD-03). Isolation relies on registry-only bucket selection and the exact checks.
- **FUTURE CAPABILITY:** a per-company MinIO service account with a policy limited to `arn:aws:s3:::cmp-<n>-*-bucket/*`, as defence in depth. It is not part of R1.

### 6.2 Usage metering
- `StorageUsageRecorder` (COMMON-ATTACHMENT): on upload, replace, hard delete and purge it emits a delta (bytes) to the control plane, through the internal API `POST /api/v1/internal/platform/companies/{companyCode}/usage/storage` with `{deltaBytes, attachmentId, reason}`.
  - The call is idempotent, keyed by `attachmentId` + reason.
  - It is sent after the metadata transaction commits.
  - If the control plane is unavailable, the delta is retried from a small local outbox table `attachment_usage_outbox` in the company attachment DB.
- **Pre-check:** before the MinIO PUT, `POST …/usage/storage/reserve` with the file size performs the conditional update from PHASE_06 `AllowanceService.assertCanStore`.
  - If it is rejected, the upload returns 409 `STORAGE_LIMIT_REACHED`.
  - The reservation is confirmed by the delta, or released after a timeout.
- **Counting rule:** every row whose status ≠ `PURGED` counts (the objects still exist). A hard delete subtracts.
- **Daily reconciliation** (control plane): the per-company sum of `size` (through the attachment DB, read-only, internal job) and the usage of **all the company's module buckets** (admin API or listing). It writes `tbl_company_usage_snapshot` and corrects drift with an audit entry.
- **Notifications:** usage at 80 % and 95 % of the allowance goes to the Company Super Admin, in-app (NOTIFICATION service) and by **platform email** (CD-23).

### 6.3 Existing buckets and future file import
- **Development data:** the existing development buckets use the LEGACY `org-<id>-<module>-bucket` names (CD-26 changes the prefix). They are not registered. Development companies are re-provisioned with `cmp-` buckets; test files are re-created, or copied with `BucketImportTool` in dry-run-verified mode. The legacy buckets are left untouched (no deletion).
- **Future import (Shivam, CD-05):** `BucketImportTool` (operator-run, idempotent) copies each legacy module bucket into the matching new `cmp-101-<module>-bucket`:
  1. keys unchanged;
  2. size and ETag verified per object;
  3. `attachment_metadata.bucket` rewritten per verified object;
  4. the sources are left untouched until the owner approves their removal.
- The import is a separate, controlled activity (PHASE_12, PHASE_01 §6.8). It is never run against production without the owner's approval.

### 6.4 ACCOUNT-BOOK file migration
- Inventory the ACCOUNT-BOOK local file usage (expense receipts and others).
- Route new writes through the attachment service.
- Copy the existing files into `cmp-<n>-account-book-bucket` under `account-book/<entity>/…` and register their metadata.
- The old volume is kept read-only until verified.

## 7. Exact services, files and database areas involved

| Area | Files |
|---|---|
| COMMON-ATTACHMENT | `bucket/*` (new `RegistryBucketResolver`), `storage/MinioStorageService.java`, `services/AttachmentService.java`, new `usage/*`, `enums/AttachmentModule.java`, `config/MinioConfig.java` (environment credentials) |
| FILE-SERVICE | `controller/FileController.java` (authorization), `scheduler/AttachmentPurgeScheduler.java` (usage deltas on purge) |
| ACCOUNT-BOOK | The file storage code paths (to be inventoried; currently a local volume) |
| Control plane | Internal usage APIs, `AllowanceService` (PHASE_06), the reconciliation job, `BucketProvisioner` (PHASE_08) |
| Databases | `cmp_<n>_attachment_db`: a new table `attachment_usage_outbox` (changelog in COMMON-ATTACHMENT); `control_panel_db`: usage tables (PHASE_06) |
| MinIO | `cmp-<n>-<module>-bucket` per purchased module |

## 8. Proposed schema changes

- `attachment_usage_outbox`: `id`, `attachment_id`, `reason`, `delta_bytes`, `status`, `attempts`, `created_at`; unique (`attachment_id`, `reason`).
- No change to `attachment_metadata` columns. The `bucket` values change only through the future import tool.

## 9. API and integration requirements

| API | Caller | Notes |
|---|---|---|
| `POST /api/v1/internal/platform/companies/{companyCode}/usage/storage/reserve` | COMMON-ATTACHMENT | Returns a reservation id or 409 |
| `POST /api/v1/internal/platform/companies/{companyCode}/usage/storage` | COMMON-ATTACHMENT | Idempotent delta |
| `GET /api/v1/company/limits` | Company console (PHASE_06) | Shows the storage usage and limit |
| `GET /api/v1/platform/admin/companies/{code}/usage` | Platform | `platform.usage.read` |

## 10. Dependencies on other phases

| Phase | Relationship |
|---|---|
| PHASE_08 | Prerequisite: bucket step per purchased module, registry |
| PHASE_06 | Prerequisite: storage allowance |
| PHASE_10 | Permission evaluation for FILE access |
| PHASE_12 | Uses the import tool for the future company data import (PHASE_01 §6.8) |

## 11. Security and tenant-isolation requirements

1. The bucket for every operation comes only from the tenant registry, for (company, module). Clients never pass a bucket or a key.
2. The exact-match check is applied on every read, write and delete, and a mismatch is audited.
3. File downloads stay proxied (no presigned URLs). If presigned URLs are introduced later, they must expire within 5 minutes and be generated only after the authorization checks.
4. Storage limit checks run on the backend, before any byte is written to MinIO.
5. **No automatic deletion of objects beyond the existing user-initiated delete, cooling and purge rules.** The registration and import tools never delete their sources.

## 12. Ordered implementation tasks

| # | Task | Status |
|---|---|---|
| 1 | `RegistryBucketResolver`; remove lazy bucket creation; `STORAGE_NOT_PROVISIONED` | PENDING |
| 2 | Exact tenant check; set `uploadedBy` / `deletedBy`; clean the `AttachmentModule` enum | PENDING |
| 3 | FILE-SERVICE authorization (module entitlement, module role, permission) | PENDING (needs the PHASE_10 evaluator) |
| 4 | Usage reserve and delta APIs + the attachment outbox | PENDING |
| 5 | Daily reconciliation and threshold notifications | PENDING |
| 6 | Bucket policy and quota at provisioning (per module bucket) | PENDING |
| 7 | `BucketImportTool` (future import; optional copy of LEGACY development `org-` buckets into `cmp-` buckets) | PENDING |
| 8 | ACCOUNT-BOOK file inventory and migration to MinIO | PENDING |
| 9 | Remove the tenant-less library purge job | PENDING |

## 13. Acceptance criteria

1. A CRM upload for company A always lands in `cmp-<a>-crm-bucket`. An attachment row of A referencing `cmp-<b>-crm-bucket` or `cmp-<a>-hrms-bucket` for a CRM request (crafted) is refused on read. An upload for a module without a registered bucket returns 503.
2. With a 10 MB limit and 9.5 MB used, a 1 MB upload returns 409 and no object is written. Downloads and deletes still work.
3. After uploads, replaces and purges, `tbl_company_usage.storage_bytes` equals the reconciled sum (± 0 bytes) in the IT.
4. A user without an HRMS role cannot download an HRMS attachment through `/file/**`, and a user with an HRMS role cannot when the company has no HRMS entitlement.
5. The import tool IT copies and verifies every object (e.g. from a LEGACY `org-` bucket into the `cmp-` bucket), and a second run is a no-op.
6. ACCOUNT-BOOK writes no files to the local volume.

## 14. Testing and validation checklist

- [ ] Unit: the resolver, the exact check (including the `cmp-1-` vs `cmp-11-` prefix collision case), the counting rule.
- [ ] MinIO + MySQL IT (Testcontainers): upload, replace, purge and usage; limit rejection; outbox retry.
- [ ] Authorization tests on FILE-SERVICE.
- [ ] Registration and import tool ITs (idempotency, verification failure handling).
- [ ] Regression: the existing attachment tests.

## 15. Risks and rollback considerations

| Risk | Mitigation |
|---|---|
| Usage drift (missed deltas) | Outbox + daily reconciliation |
| MinIO quota features differ by version | The application check is authoritative; the quota is optional |
| Copy errors during the future import | Verify per object; the sources are kept |
| Bucket count grows with companies × modules | Naming within the 63-character limit; monitoring of the bucket count; MinIO capacity planning |
| ACCOUNT-BOOK file paths unknown | Inventory task first; read-only fallback to the old volume during the transition |

**Rollback:** the resolver can be switched back by property to the existing property-based mode for development only (the names are the same). The import tool's metadata rewrite is reversible from its mapping log. The sources are never deleted.

## 16. Current status

| Item | Status |
|---|---|
| Metadata per company, versioning, purge | COMPLETED |
| Bucket per company per module: naming | COMPLETED (existing `bucketName(companyCode, module)`; `cmp-` prefix since P02-T11 stage A) |
| Bucket per company per module: provisioning, registry, exact checks | PENDING |
| Exact tenant check, audit fields | PENDING |
| File authorization by module | PENDING |
| Usage metering and limits | PENDING |
| ACCOUNT-BOOK files in MinIO | PENDING |
| **Phase** | **PARTIALLY_COMPLETED** |
