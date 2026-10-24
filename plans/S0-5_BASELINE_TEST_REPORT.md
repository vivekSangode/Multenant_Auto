# S0-5 — Baseline Local Test Report (PHASE_01 task 7)

| Item | Value |
|---|---|
| Purpose | Reference result of the **unchanged** code base, so that failures during PHASE_02+ can be attributed (PHASE_02 §14 "full regression suite against the PHASE_01 baseline report") |
| Date / agent | 2026-10-03, agent (Claude Opus 5.5) |
| Code state | Working copy `C:\Neel-Workspace\Multi Tenant` as found (local SVN state; no code, test or configuration file changed by this task) |
| Tooling | Java 21.0.10, Apache Maven 3.9.14 (offline `-o`, local cache only), Docker Engine 29.6.1 (Docker Desktop) |
| Infrastructure | Local only. Throwaway containers bound to `127.0.0.1`: `mysql:8.0` on 13306 (empty root password), `redis:7-alpine` on 16379, `mysql:8.0` on 13307 (ACCOUNT-BOOK IT's own test password); Testcontainers `mysql:8.0` / `ryuk`. All containers created for this task were removed afterwards (images kept). |
| Protected hosts | Not contacted. No Maven repository or test configuration points at `192.168.5.52` / `.53`; the 18 ITs that name those addresses only **refuse** to run against them and against any non-loopback MySQL. |
| Status | **REVIEW** (tracker S0-5). The inconclusive item of §4.3 was **resolved by a clean re-run on 2026-10-03** (32 / 32 ACCOUNT-BOOK ITs pass). Owner accepts (sets DONE). |

---

## 1. Commands

| Run | Command (from the repository root) | Log (agent scratchpad, not kept) |
|---|---|---|
| U1 unit | `mvn -o -fae -B test` | scratchpad `s05-unit.log` |
| I1 IT as-is | `mvn -o -fae -B test -Dtest='*IT' -Dsurefire.failIfNoSpecifiedTests=false` | `s05-it.log` |
| I2 IT documented settings | I1 + `-Dit.redis.host=127.0.0.1 -Dit.redis.port=16379 -Ddocker.api.version=1.41`, env `TESTCONTAINERS_TRANSPORT_TYPE=httpclient5` | `s05-it2.log` |
| I3 IT CP + ACCOUNT-BOOK | I2 + `-Dapi.version=1.41 -Dit.mysql.password=`, `-pl CONTROL-PANEL-SERVICE,ACCOUNT-BOOK-SERVICE` | `s05-it3.log`, `s05-it4.log` |

Surefire's default includes run `*Test` classes in U1; the 46 `*IT` classes run only with `-Dtest='*IT'`.

## 2. Unit tests (U1) — 781 test classes

| Module | Tests | Failures | Errors | Result |
|---|---|---|---|---|
| common-library | 167 | 0 | 0 | PASS |
| API-GATEWAY | 66 | 0 | 0 | PASS |
| COMMON-ATTACHMENT-SERVICE | 35 | 0 | 0 | PASS |
| ASSET | 6 | 0 | 0 | PASS |
| COMPLIANCE | 177 | 0 | 0 | PASS |
| **CONTROL-PANEL** | 182 | **1** | 0 | FAIL (known) |
| CRM | 22 | 0 | 0 | PASS |
| DISCOVERY-SERVER | — | — | — | no tests |
| FILE | 12 | 0 | 0 | PASS |
| HRMS | 9 | 0 | 0 | PASS |
| ATTENDANCE | 6 | 0 | 0 | PASS |
| LOGISTICS | 6 | 0 | 0 | PASS |
| MINING | 6 | 0 | 0 | PASS |
| PRODUCTION | 10 | 0 | 0 | PASS |
| QC | 9 | 0 | 0 | PASS |
| RESEARCH-AND-DEVELOPMENT | 9 | 0 | 0 | PASS |
| STORE | 10 | 0 | 0 | PASS |
| TICKETS | 7 | 0 | 0 | PASS |
| PAYROLL | 16 | 0 | 0 | PASS |
| **ACCOUNT-BOOK** | 3,120 | **30** | **64** | FAIL (known, pre-existing) |
| CUSTOMER-PORTAL | 5 | 0 | 0 | PASS |
| NOTIFICATION | 10 | 0 | 0 | PASS |
| CHAT | 15 | 0 | 0 | PASS |
| **Total** | **3,905** | **31** | **64** | **3,810 pass (97.6 %)** |

Failing unit tests:
- CONTROL-PANEL: `PlatformJwtGrantedAuthoritiesConverterTest.mapsScopesAndRealmAndResourceRoles` — the known stale test of **P02-T0**.
- ACCOUNT-BOOK: 94 failures/errors in 33 classes (`PaymentServiceTest` 8, `VendorPaymentServiceTest` 6, `ReportControllerTest` 16 methods, `AccountingTransactionServiceTest`, `VendorCreditServiceTest`, `WarehouseServiceTest`, `CompositeItemControllerTest`, `InventoryConsumptionControllerTest`, `SalesOrderControllerTest`, `CurrencyControllerTest`, `TdsMasterControllerTest`, `TcsMasterControllerTest`, `TransferOrder*`, `SalesReturnControllerTest`, `ManualTransactionServiceTest`, `BalanceSheetServiceTest`, `ProfitAndLossServiceTest`, `LedgerReportServiceTest`, `CostCentre*`, `CreditNoteServiceTest`, `DebitNoteServiceTest`, `DashboardServiceTest`, `EwayBill*`, `ExpenseStatusServiceTest`, `ItemImportServiceTest`, `ManualAssemblyImportServiceTest`, `RecurringBillServiceTest`, `AccountingBackfillServiceTest`, `WarehouseStockServiceTest`). Typical symptoms: HTTP 400 instead of 200, missing JSON fields. These are the pre-existing failures tracked by **P12-T8** ("fix or quarantine with approval"). Full list: [S0-5_BASELINE_FAILING_TESTS.txt](S0-5_BASELINE_FAILING_TESTS.txt).

## 3. Integration tests — 46 IT classes

| Module | I1 as-is | I2 / I3 with documented local settings |
|---|---|---|
| common-library | 10 / 10 pass | 10 / 10 pass |
| ASSET, COMPLIANCE, CRM, FILE, HRMS, ATTENDANCE, LOGISTICS, MINING, PRODUCTION, QC, R&D, STORE, TICKETS, PAYROLL | 1 error each (entitlement IT: Redis on 6379 not reachable) | **all pass** (13, 10, 19, 11, 13, 13, 13, 13, 14, 13, 13, 13, 13, 15) |
| NOTIFICATION | 1 error (`ControlPlaneClient` class not found) | 11 / 11 pass |
| CHAT | JUnit discovery failed | 16 / 16 pass |
| **CONTROL-PANEL** | 60 errors (Testcontainers: no Docker) | I2: 60 errors (Docker API 400); **I3: 56 / 60 pass**, 4 fail |
| **ACCOUNT-BOOK** | 1 error (MySQL password) | **32 / 32 pass** (clean re-run I5, §4.3) |
| **Total (best run)** | — | **311 / 315 pass**: the 4 remaining failures are the known stale CP `IdentitySyncMySqlIT` methods |

Remaining CP IT failures (I3), all in `IdentitySyncMySqlIT`: `assignment_writesItsSyncTaskAtomically_andTheWorkerAppliesGroupAndRole`, `invitationOfANewPerson_createsAPasswordlessUser_sendsTheMail_andGrantsTheMembership`, `revocation_removesOnlyWhatTheRevokedMembershipGranted` (NPE), `undefinedRealmRole_isAPermanentFailure` (IndexOutOfBounds) — the known stale realm-role assertions of **P02-T0** / **P10-T11**.

## 4. Findings (test environment, not product code)

### 4.1 Test configuration gaps (affect reproducibility, not behaviour)

| # | Finding | Effect | Suggested fix (later, owner-approved task) |
|---|---|---|---|
| E-1 | CONTROL-PANEL has no `src/test/resources/testcontainers.properties` (`transport.type=httpclient5`) and its surefire config lacks `api.version` / `docker.api.version=1.41`, which COMMON-LIBRARY sets. Docker Engine 29 rejects the old default API version (HTTP 400). | All 60 CP ITs fail on a stock Docker Desktop | Mirror COMMON-LIBRARY's two settings in CONTROL-PANEL (fits P02-T0 or P12-T1) |
| E-2 | The 18 `*ModuleEntitlementEnforcementIT` classes default Redis to `localhost:6379`; every other IT defaults to `127.0.0.1:16379` | One error per domain service unless `-Dit.redis.port=16379` is passed | Align the default to 16379 (P12-T1 test profile) |
| E-3 | The ACCOUNT-BOOK IT expects its own MySQL root test password; the other domain ITs expect an empty password on the same port 13306 | One shared local MySQL cannot serve both | Document one local IT MySQL profile (P12-T1) |
| E-4 | A user-level `~/.testcontainers.properties` pins `NpipeSocketClientProviderStrategy` | Works only together with E-1 | Note in the test runbook |

### 4.2 Concurrent build interference (observed during this task)

While the ITs ran, an **IntelliJ Maven process** (started 18:00) and a separate surefire JVM (started 18:06) built the same working copy. Effects observed: `~/.m2/.../common-library-1.0.0.jar` rewritten at 18:06:11 and read while corrupt (`ZipException: invalid distance too far back`), and `ACCOUNT-BOOK-SERVICE/target/classes` deleted mid-run (`NoClassDefFoundError` on its own classes). The I1 NOTIFICATION/CHAT errors are most likely the same cause; both modules passed fully in I2. The agent did not stop or touch the IDE processes.

**Rule for the next agents:** run baseline or regression builds only when no other Maven build of this working copy is running.

### 4.3 Inconclusive item — RESOLVED (clean re-run I5, 2026-10-03)

**Re-run I5 (owner condition for accepting S0-5):** no other Java/Maven process running (checked before and during the run); COMMON-LIBRARY jar re-installed locally (`mvn -o install -pl COMMON-LIBRARY -DskipTests`, jar integrity verified); Docker Desktop started locally; loopback containers `redis:7-alpine` (127.0.0.1:16379) and `mysql:8.0` (127.0.0.1:13307, root password = the IT's own default test password from its test file); command `mvn -o -B test -pl ACCOUNT-BOOK-SERVICE -Dtest='*IT' -Dsurefire.failIfNoSpecifiedTests=false -Dit.redis.host=127.0.0.1 -Dit.redis.port=16379 -Dit.mysql.port=13307`. **Result: Tests run 32, Failures 0, Errors 0 — BUILD SUCCESS**, including `bootstrap_neverExposesDatabaseCredentials` and `bootstrap_ofAPopulatedPrePhase5KDatabase_isRefused_andChangesNothing`. Containers removed afterwards.

Original finding (kept for history):

`AccountBookTenantIsolationMySqlIT.bootstrap_neverExposesDatabaseCredentials` and `bootstrap_ofAPopulatedPrePhase5KDatabase_isRefused_andChangesNothing` failed in I3/I4 only because `-Dit.mysql.password=` (empty) makes their "response does not contain the password" assertions always fail. The rerun against a MySQL with the IT's own test password (port 13307) could not compile, because the shared COMMON jar was being rewritten by the concurrent IDE build (§4.2). **These two tests are not counted as product failures.** Re-run when the IDE build is idle:

```text
docker run -d --name s05-it-mysql-ab -p 127.0.0.1:13307:3306 -e MYSQL_ROOT_PASSWORD=<the IT's default test password> mysql:8.0
mvn -o -B test -pl ACCOUNT-BOOK-SERVICE -Dtest='*IT' -Dsurefire.failIfNoSpecifiedTests=false -Dit.redis.host=127.0.0.1 -Dit.redis.port=16379 -Dit.mysql.port=13307
```

## 5. Baseline to compare against

| Suite | Expected baseline failures (anything else is a regression) |
|---|---|
| Unit | CP: `PlatformJwtGrantedAuthoritiesConverterTest.mapsScopesAndRealmAndResourceRoles` (P02-T0); ACCOUNT-BOOK: the 94 listed in [S0-5_BASELINE_FAILING_TESTS.txt](S0-5_BASELINE_FAILING_TESTS.txt) (P12-T8) |
| IT (with §1 I2/I3 settings) | CP: the 4 `IdentitySyncMySqlIT` methods of §3 (P02-T0 / P10-T11) |

All other unit and integration tests pass.
