# ACCOUNT-BOOK-SERVICE — classification of the local SVN changes (owner request 2026-10-04)

| Item | Value |
|---|---|
| Scope | Every versioned file of `ACCOUNT-BOOK-SERVICE/src` that differs from BASE (r1383): **247** entries = 233 modified (M) + 14 deleted (D). Unversioned new files (`system/tenant/**`, per-tenant seeders, new changelogs, tenant tests) are SaaS additions and are not part of the 247. |
| Method | `svn status` / `svn diff` against BASE only (local working-copy commands; no server access). Each diff read; changelog diffs summarised by changeset author / label / id and checked against the deleted BASE files. |
| Result | **A = 188** (kept), **B = none found**, **C = 59 files**. Owner decisions 2026-10-04 (applied, see the end of this document): C1-a entity `@Size` removed; C1-b DTO `@Size`, C2, C3 kept. A was never reverted. |
| Rule (owner) | A is kept. B / C are not modified without the owner's explicit approval of the exact diff. ACCOUNT-BOOK business concepts (`organization_profile`, `bank_accounts.organization_id`, `OrganizationRequestDTO`, `OrganizationType`, accounting services, business seed data, business changelogs) are never renamed by the company migration. |

## A — required multi-tenant (kept, not reverted)

| Group | Files | Why it is multi-tenant |
|---|---|---|
| Liquibase changelogs | 117 modified changelogs + `db.changelog-master.xml`; 13 deleted `migrations/changelog-m0xx-*.xml` | Company (tenant) databases are created from the Liquibase master (P02-T6 / T7 / T8). The 173 changesets of the deleted migration files and 52 rewritten ones were **moved unchanged** (same ids, author `VISHAL_SURYAWANSHI`, label `acctbook-migration`; 220 re-added) into the table changelogs, e.g. `m014-01` → `sales/changelog-invoices.xml`, `m014-02` → `tax/changelog-eway_bills.xml`; 112 parity changesets (`NEEL_JOSHI`, label `account-book-schema`, ids `20261002-0001…0113`) align the schema with the entities. No business column / table was dropped. |
| Entity schema-parity annotations | 23 model files with parity changes only (`@OnDelete`, FK definitions, indexes, `uk_bill_vendor_number`) + the parity lines of 13 mixed files | Hibernate `validate` of a Liquibase-created company DB must match the entities (same FK rules / indexes as the changelogs). |
| Per-company seeding | `ChartOfAccountSeeder`, `AccountLedgerSeeder`, `TcsMasterSeeder`, `TdsMasterSeeder`, `PaymentTermsMasterService`, `UnitMasterService` | Startup seeding against one shared DB replaced by seeding inside each company's bootstrap (same default data, moved to the unversioned per-tenant seeders). |
| Legacy single-DB runners gated | `AccountingBackfillRunner`, `LegacyReversalCleanupRunner`, `LegacySoftDeletedContactCleanupRunner`, `OpeningBalancePostingBackfillRunner`, `WarehouseStockInitializer`, `CompositeItemService` (startup trigger), `WarehouseStockService` (DDL patch) | Run only with `accounting.legacy-single-db-runners.enabled=true`; under per-company routing there is no tenant at startup. Logic unchanged. |
| Tenant-aware schedulers | `OverdueScheduler`, `RecurringBillSchedulerService`, `RecurringInvoiceScheduler`, `NotificationCleanupJob` | `@Scheduled` moved to the per-company job runner (unversioned `TenantScheduledJobs`); job logic unchanged. |
| Tenant isolation | `WebSocketConfig` (COMMON `TenantStompSupport`), `DocumentShareService`, `InvoiceShareService` (company-bound signed share tokens), `accounting/exception/GlobalExceptionHandler` (tenant-routing 403/503), `ContactOpeningBalancePostingService` (message text only) | Prevent cross-company leakage / report tenant refusals correctly. |
| COMMON-LIBRARY integration | `Main.java` (component scan), `GlobalAuditListener` (`LocalAuditLogRepository`), `security/SecurityConfig.java` (javadoc only; no code change) | Coexistence with the COMMON tenant runtime. |
| Configuration | `application.yml` | Routing datasource, Redis, tenant provisioning, share-token, entitlement enforcement, legacy runners off. |
| Tests | `DocumentShareServiceTest`, `InvoiceShareServiceTest`, `TransactionLockIntegrationTest`, `TransactionLockPerformanceTest` (tenant context); `InventoryConsumption`, `PurchaseReceive`, `Report`, `SalesOrder`, `TransferOrder` controller tests and `AccountingTransactionControllerTest` (mock beans needed by the r1374 trunk merge — build support) | Keep the suite green under the tenant runtime. |

## B — unrelated business logic

None found.

## C — uncertain (shown separately; not touched; owner decision)

| # | Files | Change | Note |
|---|---|---|---|
| C1 | 56 files: 17 DTOs (`accounting/dto/*` listed below, **`admin/dto/OrganizationRequestDTO`**, **`BankAccountRequestDTO`**) and 39 entities (`Invoice`, `Bill`, `Contact`, …) — 394 added `@Size(max = …)` (+ `@NotBlank` on `StatementColumnMapping.templateName`, `@Valid` on `Project`) | Bean-validation lengths equal to the column lengths | Not required by multi-tenancy and not documented in WORK_LOG / tracker. Behaviour change: input longer than the column now returns 400 instead of a database error. Keep or revert = owner decision. |
| C2 | `accounting/controller/CustomerPortalController.java` | Controller annotations removed (endpoints `/api/portal/**` disabled, Phase 5L decision P-5) | Security fix: the endpoints returned every invoice / customer of the company without customer scoping. Not tenancy. |
| C3 | `security/debug/AuthDebugFilter.java`, `DebugAuthenticationEntryPoint.java` (deleted) | Debug filters removed | They logged full JWT payloads (security hardening). Not tenancy. |

C1 DTO files: `AssemblyRequestDTO`, `BankAccountRequestDTO`, `CompositeItemRequestDTO`, `CostCentreDTO`, `CostCentreGroupDTO`, `EwayBillRequestDTO`, `ExpenseRequest`, `GspConfigurationRequestDTO`, `InventoryConsumptionRequestDTO`, `ItemGroupRequestDTO`, `ItemRequestDTO`, `ManualJournalDTO`, `TcsMasterDTO`, `TdsMasterDTO`, `TransactionLockRequest`, `TransferOrderRequestDTO`, `admin/dto/OrganizationRequestDTO`.

C1 entity files: `Account`, `ApprovalPolicy`, `BankFeedTransaction`, `Bill`*, `ChartOfAccount`, `Contact`*, `ContactAddress`, `ContactPerson`, `CreditNote`*, `CreditNoteRefund`*, `Currency`, `DebitNote`, `DebitNoteRefund`, `DeliveryChallan`*, `InventoryAdjustment`, `Invoice`*, `ManualAssembly`, `ManualTransaction`, `MileageLog`, `PaymentReceived`*, `PaymentRefund`*, `PriceList`, `Project`, `PurchaseOrder`*, `PurchaseReceive`, `Quote`*, `RecurringBillProfile`, `RecurringInvoiceProfile`*, `SalesOrder`*, `SalesReturn`*, `StatementColumnMapping`, `Task`, `TimesheetLog`, `VendorCredit`, `VendorCreditRefund`, `VendorPayment`, `VendorPaymentRefund`, `Warehouse`, `WorkflowRule` (* = the file also carries A-group parity annotations; a C1 revert would remove only the `@Size` / `@NotBlank` / `@Valid` lines).

## C — detailed review (2026-10-04, second pass; still nothing changed)

Categories requested by the owner: (1) required SaaS-tenant migration · (2) legitimate business logic · (3) unrelated historical change · (4) uncertain.

| Item | Category | Evidence | Impact if kept | Impact if reverted | Owner decision needed |
|---|---|---|---|---|---|
| C1-a: `@Size` on the **39 entities** | **(3) unrelated historical change** (not tenancy) | Automated check of every `@Size(max)` against the field's `@Column` (`length`, `columnDefinition`, default 255): **all equal** to the column (or TEXT). So no DDL / schema-parity effect — the Hibernate-generated tenant schema and the Liquibase parity (P02-T7) are identical with or without them. | Persist-time Bean Validation: a too-long value that did not pass a DTO check now fails at flush with `jakarta.validation.ConstraintViolationException`. ACCOUNT-BOOK `GlobalExceptionHandler` has no handler for it → caught by the `RuntimeException` handler → **HTTP 500** generic message. Before: MySQL "Data too long" → `DataIntegrityViolationException` → **HTTP 409** "Database constraint violation: …". | Back to 409 from MySQL; no tenancy effect. | **Yes.** Options: (a) remove the entity `@Size` lines (keep column lengths), (b) keep them and add a `ConstraintViolationException` → 400 handler (business code change), (c) keep as is (accept 500). Recommendation: (a) or (b); not decided by the agent. |
| C1-b: `@Size` on the **17 DTOs** (incl. `OrganizationRequestDTO`, `BankAccountRequestDTO`) | **(3) unrelated historical change** (business input validation, not tenancy) | DTO limits mirror the target entity columns (e.g. pincode 6, GSTIN 15, PAN 10, IFSC 11, currency 3). Controllers validate with `@Valid @RequestBody` (109 uses) → `MethodArgumentNotValidException` → handled → **HTTP 400** with field errors. Some DTO limits are looser than 255 (e.g. notes 2000) because the target column is TEXT. | User-friendly 400 instead of a 409 database error. | Back to 409 database errors for too-long input. | **Yes** (business validation of ACCOUNT-BOOK). Recommendation: keep; not decided by the agent. |
| C2: `CustomerPortalController` disabled (`/api/portal/invoices`, `/credit-notes`, `/debit-notes`, `/customers`) | **(3) unrelated historical change** — security fix inside one company (customer scoping), not SaaS tenancy | BASE code: `accountName` was an optional `@RequestParam`; without it every invoice / credit note / debit note of the company was returned to any authenticated user of that company. No caller in the repository (CUSTOMER-PORTAL-SERVICE does not call it; gateway test asserts 401 for anonymous). The cited decision document (`docs/PHASE_5L_DECISION_DOCUMENT.md`) no longer exists (withdrawn documents). | Endpoints return 404; a UI page that called them (UI not in this repository) breaks. | Data of all customers exposed again to any company user. | **Yes**: confirm "keep disabled until a customer-scoped replacement exists" (recommended). |
| C3: `security/debug/AuthDebugFilter`, `DebugAuthenticationEntryPoint` deleted | **(3) unrelated historical change** — dead diagnostic code (security hygiene) | In BASE neither class is a `@Component` nor referenced by `SecurityConfig` (BASE `SecurityConfig` = `anyRequest().permitAll()`, unchanged except javadoc). They would log full JWT payloads if wired. | None at runtime (they were not active). | Restores two unused classes; none at runtime. | Low: confirm deletion (recommended). |

No C item is a required SaaS-tenant migration (category 1) or ACCOUNT-BOOK business logic that the company migration may change (category 2). Per the owner rule, all C items stay exactly as they are until the owner decides.

## C — owner decisions applied (2026-10-04)

These are **historical / unrelated changes**, recorded separately from the SaaS migration (P02-T11).

| Item | Owner decision | Applied | Verification |
|---|---|---|---|
| C1-a: entity `@Size` (39 entities) | **Revert / remove** | Removed exactly the 277 `@Size` lines that were added relative to svn BASE (+ the `jakarta.validation.constraints.Size` import in 38 files where it had been added and is no longer used; `Contact` keeps its pre-existing import / BASE `@Size`). Pre-existing BASE `@Size` lines untouched; line endings (CRLF) and missing final newlines preserved. | Each of the 39 files differs from the pre-change backup **only** by the removed lines; `svn diff` of these files now shows only the A-group parity annotations. ACCOUNT-BOOK ITs 34/34 incl. `AccountBookSchemaParityMySqlIT` (no DDL effect, as predicted); unit failure set identical before / after (94 baseline failures, none new, none fixed). |
| C1-b: DTO `@Size` (17 DTOs) | **Keep** | Unchanged | — |
| C2: `CustomerPortalController` disabled | **Keep** | Unchanged | — |
| C3: debug filters removed | **Keep** | Unchanged | — |

Open (small, owner): the same historical C1 change also added `@NotBlank` on `StatementColumnMapping.templateName` and `@Valid` on `Project` (entity-level validation, same persist-time 500 risk as C1-a). The decision named only `@Size`, so both were **left unchanged** — keep or remove?

### Baseline ACCOUNT-BOOK unit failures (not related to P02-T11)

`mvn -o test -pl ACCOUNT-BOOK-SERVICE`: 3121 run, **30 failures, 64 errors**, identical before and after every P02-T11 / C1-a change. All in business tests (controllers / services: `WarehouseServiceTest`, `PaymentServiceTest`, `VendorPaymentServiceTest`, `CompositeItemControllerTest`, …). Causes seen: WebMvc slices missing mock beans added by trunk merges (`EwayBillMapper`, `ConsumptionReportExcelService`), NPEs from missing repository mocks (`CreditNoteInvoiceMappingRepository`, `VendorCreditBillMappingRepository`), Mockito `UnnecessaryStubbingException`, test payloads missing fields that BASE already validates as required. None of these test or production classes was changed by P02-T11. Business tests are not modified without owner approval.

## P02-T11 stage B (2026-10-05) — ACCOUNT-BOOK files changed (tenant plumbing only)

Only SaaS-tenant infrastructure was changed (owner rule); every change is a rename of the tenant API (`TenantContextHolder.getCompanyCode()`, header `X-Company-Code`, `COMPANY_MODULES`, `getActiveCompanyCodes`, `runForCompany`) or of tenant identifiers / comments. Category of each file stays as classified above (A, or new multi-tenant code in the unversioned `system/tenant` package).

| File | Kind |
|---|---|
| `system/tenant/` `AccountBookTenantInfrastructureConfiguration`, `AccountBookTenantProvisioningProperties`, `AccountBookTenantProvisioningService`, `PublicShareTokenFilter`, `ShareTokenSigner`, `TenantBootstrapController`, `TenantJobRunner`, `TenantScheduledJobs` | Multi-tenant package (unversioned, new code) |
| `accounting/service/DocumentShareService`, `InvoiceShareService` | A (company-bound share tokens): tenant identifier `companyCode` |
| `security/SecurityConfig`, `accounting/config/WebSocketConfig`, `src/main/resources/application.yml` | A: comments only |
| Tests: `AccountBookWebSocketTenantIsolationTest`, `DocumentShareServiceTest`, `InvoiceShareServiceTest`, `AccountBookModuleEntitlementEnforcementIT`, `AccountBookTenantContextIsolationIT`, `AccountBookTenantIsolationMySqlIT`, `ShareTokenSignerTest`, `TenantJobRunnerTest`, `support/AccountBookH2Tenant`, `H2TenantExtension`, `H2TenantTestConfiguration` | Tenant tests |

Business concepts protected during the rename and verified unchanged: `organization_profile`, `organization_id`, `OrganizationType`, `OrganizationRequestDTO`, `admin.model.Organization` (e.g. the IT still asserts the `organization_profile` table). No entity, DTO, accounting service, seeder or business changelog was changed.

Tests: ACCOUNT-BOOK ITs 34/34 (incl. schema parity); unit 3121 run, **30 failures + 64 errors = KNOWN BASELINE** — the list of failing tests is identical to the stage A run; treated as skipped for P02-T11 and not touched (owner instruction 2026-10-05).

## P02-T11 stage C (2026-10-05)

Stage C changed CONTROL-PANEL-SERVICE only; **no ACCOUNT-BOOK file was changed** (checked by modification time). ACCOUNT-BOOK unit tests: 3121 run, 30 failures + 64 errors — the KNOWN BASELINE, identical list, not touched.

## P02-T11 stage E (2026-10-05)

Stage E added **one new (unversioned) test file** to ACCOUNT-BOOK: `src/test/java/nyaalkaran/erp/system/CompanyTenancyArchitectureTest.java` (cross-module company-tenancy architecture test). No main file, business class, changelog or existing test was changed. Its explicit exclusions record the business "organization" of ACCOUNT-BOOK as **not** SaaS tenancy (owner rule 2026-10-04): the organization profile classes `admin.{controller.OrganizationController, dto.OrganizationRequestDTO, model.Organization, model.enums.OrganizationType, repository.OrganizationRepository, service.OrganizationService}` and `bank_accounts.organization_id` (`accounting.dto.BankAccountRequestDTO`, `accounting.repository.BankAccountRepository`, `accounting.service.BankAccountService`, `accounting.model.BankAccount`, `db/changelog/organization/changelog-bank_accounts.xml`). The tenant plumbing (`system.tenant`) has no exclusion and passes. ACCOUNT-BOOK unit tests: 3128 run (3121 + 7 new, all new pass), 30 failures + 64 errors = KNOWN BASELINE, identical list; ITs 34/34.

## P02-T11 stage F (2026-10-05)

Tenant / security plumbing only (owner decision D3-9). **Main:** `security/SecurityConfig.java` — **javadoc only** (one list item describing the COMMON `TenantVerificationFilter` at order `HIGHEST_PRECEDENCE + 6`; the entitlement filter order in the existing item changed from `+6` to `+7`); no code, no business class, entity, DTO, changelog or seeder changed. The service-side verification itself comes from COMMON and runs ahead of this Spring Security chain. **Tests (tenant tests only):** `AccountBookWebSocketTenantIsolationTest` (signed handshake token from the COMMON `TestRealms` fixture), `AccountBookTenantIsolationMySqlIT` (tokens in its tenant request helper, new direct-call refusal test, double-encoded internal path now refused with 401 by the verification instead of 400 by the firewall). ACCOUNT-BOOK unit tests: 3128 run, 30 failures + 64 errors = KNOWN BASELINE (identical list); ITs 35 / 35.
