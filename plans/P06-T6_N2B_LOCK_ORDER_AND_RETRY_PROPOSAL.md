# P06-T6 review finding N2 — N2b lock-order design and retry-policy recommendation (FOR REVIEW, not implemented)

Status: **implemented as P06-T6-F1 (CD-39), REVIEW 2026-10-12** — see WORK_LOG "P06-T6-F1 implemented → REVIEW" for the deviations and results. Original status: proposal only (2026-10-12); nothing here was authorized for implementation at that time. N2b touches code of the DONE tasks
P06-T2 / P06-T4 and needs a separate owner implementation approval. The retry policy stays unchanged (3 attempts,
`PessimisticLockingFailureException`). P06-T6 stays REVIEW.

## 1. Evidence (N2a, `PublicationLockConflictMySqlIT`, local MySQL 8.0.45, REPEATABLE READ)

| Case | Exception received by the publisher's retry | Cause | JDBC | Code / state | Attempts |
|---|---|---|---|---|---|
| 1205 lock wait timeout | `PessimisticLockingFailureException` | `org.hibernate.PessimisticLockException` | `MySQLTransactionRollbackException` | 1205 / 40001 | 3 |
| 1213 deadlock | `CannotAcquireLockException` | `org.hibernate.exception.LockAcquisitionException` | `MySQLTransactionRollbackException` | 1213 / 40001 | 3 |

The 1213 cycle (reproduced deterministically): transaction T holds the company **row** (clustered index, locked by id)
and then asks for the company-**code index entry**; the publication holds the code index entry (it locks by code: the
secondary index record first, then the row) and waits for the row. InnoDB rolls back the lighter transaction.

## 2. Current company lock sites (CONTROL-PANEL-SERVICE, main code)

| # | Site | Locks by | Transaction context |
|---|---|---|---|
| L1 | `entitlement/CompanyRuntimePublication.publishLocked` (`findByCompanyCodeForUpdate`) | code → index, then row | own REQUIRES_NEW, first statement (IC-2) |
| L2 | `entitlement/EntitlementEngine.lockCompany` (:195, + `refresh(…, PESSIMISTIC_WRITE)`) | code → index, then row | caller's transaction (subscription operations, order fulfilment, manual grants, `recomputeInNewTransaction`, `retryInNewTransaction`) |
| L3 | `subscription/SubscriptionService.lockCompany` (:807) | code | order-driven creation (P06-T2) |
| L4 | `subscription/SubscriptionService.lockCompanyOf` (:831) | **id** (row only) | after the subscription row lock (N1 of P06-T4) |
| L5 | `service/company/CompanyService.lockCompany` (:299) | code | lifecycle / status changes |
| L6 | `service/company/CompanyProvisioningClaims.lock` (:158) | code | provisioning claim / failure |

Cycles today: any L1/L2/L3/L5/L6 holder (index → row) against a transaction that holds the row through L4 and then
reaches L2 (row → index). L1 × (L4 + L2) is the observed 1213; L5 × (L4 + L2) (a status change racing a subscription
suspension) has the same shape and is not covered by a test today.

## 3. Proposed design (N2b): one lock resource — the company row by primary key

1. **Every company lock is `CompanyRepository.findByIdForUpdate(id)`** (clustered row only). `findByCompanyCodeForUpdate`
   is no longer used by main code (kept or removed in the implementation step; tests adapted).
2. **Code → id resolution before the locking transaction starts:** a new `CompanyIdResolver` (CP,
   `@Transactional(propagation = NOT_SUPPORTED)`, read-only, cached `ConcurrentHashMap<code, id>`; the company code is
   immutable once allocated — to be confirmed in the implementation readiness). `NOT_SUPPORTED` suspends any caller
   transaction, so the lookup runs on its own connection and **never creates the caller's REPEATABLE READ read view**
   (InnoDB creates it at the first plain read — a plain `SELECT id … WHERE company_code = ?` inside the locking
   transaction would freeze the snapshot before the lock and break IC-2). Unknown codes are not cached
   (`ResourceNotFoundException`, as today).
3. **Revalidation after the lock:** the locked row's `companyCode` must equal the requested code; otherwise fail closed
   (`IllegalStateException` / 409, logged without values) — guards a stale cache entry or a wrong mapping.
4. **Per site:** L1 resolves before its REQUIRES_NEW transaction (the lock stays the first statement); L2 gets an
   `id`-based variant (callers pass the id they already hold — subscription operations know `sub.getCompanyId()`;
   code-only callers resolve first), keeping the `refresh(…, PESSIMISTIC_WRITE)`; L3, L5, L6 resolve through the
   resolver, then lock by id; L4 unchanged.
5. **Resulting order:** subscription row → company row (unchanged), company row only everywhere else; the code index
   entry is never locked, so the L1/L5 × L4+L2 cycles disappear. Publication still never locks a subscription.
6. **Unchanged:** retry policy (3 attempts), backlog / N1 behaviour, Redis publication semantics, IC-2 "lock first".

### Transaction / snapshot implications

- The resolver's connection is separate (pool usage +1 short-lived connection per uncached lookup); with the cache a
  company is resolved once per instance.
- Callers that already did plain reads before the lock (e.g. order matching) are unchanged: `EntitlementEngine` keeps
  its refresh under the lock; no new plain read is added before any lock.
- `NOT_SUPPORTED` must not be called while the caller needs the resolution inside the same snapshot — it never does
  (it only maps code → id).
- A cached id of a company deleted later: the lock finds nothing → `ResourceNotFoundException` (as today) and the entry
  is evicted.

### Regression coverage (to be added with the implementation)

1. `PublicationLockConflictMySqlIT` 1213 scenario re-targeted: the same interleaving now completes **without a
   deadlock** in one attempt (holder locks the row by id, then asks for the code index entry — no conflict).
2. `EntitlementTransactionWiringMySqlRedisIT.twoSubscriptionsSuspendedConcurrently…` and
   `concurrentPublicationsAndAStatusChange…` with a log assertion: **0** "lost a lock conflict" retries.
3. New MySQL IT: `CompanyService` status change (L5) racing a subscription suspension (L4 + L2) — no 1213, final state
   consistent.
4. New MySQL IT (IC-2): code resolved, another transaction commits a change, then lock + read sees the committed change
   (latch-ordered, deterministic).
5. Unit: resolver cache (hit, miss, unknown not cached), revalidation mismatch fails closed, `NOT_SUPPORTED` wiring.
6. Full CP unit + CP IT + subscription / entitlement ITs; ACCOUNT-BOOK baseline unchanged.

Affected files (expected): `CompanyRuntimePublication`, `EntitlementEngine`, `SubscriptionService`, `CompanyService`,
`CompanyProvisioningClaims`, `CompanyRepository` (usage), new `CompanyIdResolver`, their unit tests, the ITs above,
PHASE_06 plan / §9 note.

## 4. Retry-policy recommendation (separate decision; not implemented)

**Today:** `ModuleEntitlementCachePublisher.withLockRetry` retries any `PessimisticLockingFailureException` (Spring
hierarchy incl. `CannotAcquireLockException`, `DeadlockLoserDataAccessException`, `CannotSerializeTransactionException`)
up to 3 attempts (backoff 25 / 50 ms).

**Recommendation:** retry only when **both** hold:
1. the exception is a `PessimisticLockingFailureException`, and
2. its cause chain (bounded walk, e.g. ≤ 10 levels, cycle-safe) contains a `java.sql.SQLException` whose vendor code is
   **1205** (lock wait timeout) or **1213** (deadlock).

Handling of the other cases (no retry, fail closed exactly as any other failure — the caller lists the company, recovery
and resync restore it):
- **missing SQL cause** (a translated exception without an `SQLException`): no retry, WARN "lock failure without SQL
  cause";
- **unexpected vendor code** (e.g. 3572 NOWAIT, 1206 lock table full, a non-MySQL 40001): no retry, WARN with the code
  (codes only, never values);
- SQL state 40001 alone is **not** sufficient (both observed codes carry it, but so do other conditions).

The N2a chains satisfy rule 2 directly (`MySQLTransactionRollbackException` with 1205 / 1213), so the change would not
reduce retries for the observed cases. Keep 3 attempts and the backoff. Tests: unit tests with synthetic chains (1205,
1213 → retried; 3572, missing cause, nested depth, a non-lock `DataAccessException` → not retried, exactly 1 attempt) and
the existing N2a ITs (real chains → retried 3 times). After N2b the 1213 case between publication and subscription
transactions disappears; 1205 under long holds remains, so the restriction stays useful.
