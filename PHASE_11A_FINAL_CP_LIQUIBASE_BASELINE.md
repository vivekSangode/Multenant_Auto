# PHASE 11A — Final Control Plane Liquibase Baseline

| Item | Value |
|---|---|
| Master architecture | [SAAS_ARCHITECTURE_MASTER.md](SAAS_ARCHITECTURE_MASTER.md) §16.4 |
| Decision | **CD-27** ([PHASE_01 §0.1](PHASE_01_SAAS_FOUNDATION.md)), owner decision of 2026-10-03: the Control Plane uses Hibernate `ddl-auto: update` while its Company-based model is being built; Liquibase is introduced **once**, as a clean baseline of the **final, stabilized** CP schema |
| Design source | The final CP JPA entity model, PHASE_01 §6.2 (target columns and constraints), `LIQUIBASE_CONVENTIONS.md` |
| Depends on | All R1 CP entity work of PHASE_02–PHASE_11 DONE, the legacy CP entities removed, and a written **CP schema freeze** by the owner (§6.1) |
| Enables | The CP running on Liquibase with `ddl-auto: validate` (CD-27). **Gate G6** (staging readiness, PHASE_12): PHASE_11A is a mandatory readiness gate before staging/production readiness (CD-28 item 10). |
| Scope | **Control Plane DB (`control_panel_db`) only.** Company databases keep the tenant Liquibase design of AD-09 (PHASE_02 tenant migrator); the attachment DB keeps its existing changelog. |
| Status | **PENDING** (not startable until §6.1 is met) |

---

## 1. Objective

1. Audit the **complete final** CP entity model and the schema Hibernate actually generates from it.
2. Write **one clean, reviewable Liquibase baseline** that represents exactly that final schema, with no intermediate development states, no temporary changelogs and no legacy structure.
3. Make Liquibase the **only** CP schema-change mechanism from then on; Hibernate switches to `ddl-auto: validate`.
4. Retire the obsolete CP Liquibase artifacts of the old Customer/Organization model.
5. Define how each environment receives the baseline (new DB, existing development DB, production, future legacy migration).

## 2. Existing implementation relevant to this phase (`CODE-INSPECTED` 2026-10-03)

| Component | Location | Notes |
|---|---|---|
| CP schema configuration | `CONTROL-PANEL-SERVICE/src/main/resources/application.yml` | `ddl-auto: update`, `MySQL8Dialect`, `spring.liquibase.enabled: false` |
| Old generated changelogs | `CONTROL-PANEL-SERVICE/src/main/resources/db/changelog/db.changelog-master.xml` + 26 files in `db/changelog/platform/` | Reverse-generated from the **old** Customer/Organization model on 2026-10-02; never run; native `ENUM` columns |
| Liquibase dependency | `CONTROL-PANEL-SERVICE/pom.xml` | `liquibase-core` present (used programmatically for the attachment DB only) |
| Schema-parity method | 2026-10-02 work on the service changelogs | Build DB A from changelogs and DB B from the entities, diff `information_schema` |

## 3. Existing completed work

| Item | Status |
|---|---|
| `LIQUIBASE_CONVENTIONS.md` (ids, authors, labels, preconditions, rollback, comments, immutability) | COMPLETED (reused) |
| Schema-parity diff method (information-schema comparison) | COMPLETED as a method (reused for the audit and the parity test) |
| `TenantMySqlDialect` (string enums as `VARCHAR`) | COMPLETED (reused for the CP) |

## 4. Reusable components

- **Unchanged:** `LIQUIBASE_CONVENTIONS.md` rules for changesets; the COMMON `changelog-audit-log.xml` include (if the CP keeps `tbl_audit_log`); `TenantMySqlDialect`.
- **With modifications:** the parity diff helper (pointed at `control_panel_db` instead of a tenant DB).
- **Not reused:** the 26 generated changelogs of the old model and their master (they describe a schema that no longer exists).

## 5. Work requiring modification or redesign

| Item | Change |
|---|---|
| `application.yml` (CP) | `spring.liquibase.enabled: true`, the new master, `ddl-auto: validate`, `TenantMySqlDialect` |
| `db/changelog/**` (CP) | Old generated files retired (§6.6); the clean baseline added |
| `LIQUIBASE_CONVENTIONS.md` | Updated only where the approved baseline layout (§6.3) differs from it |

## 6. New development required

### 6.1 Entry criteria (all must be met before this phase starts)

1. **R1 CP model complete:** every CP entity task of PHASE_02 (T2–T5), PHASE_03 (realm registry, login code), PHASE_04 (T1, T6), PHASE_05 (T1–T7), PHASE_06 (T1, T8), PHASE_07 (T1), PHASE_08 (T1), PHASE_09 (CP usage parts), PHASE_10 (T1) and PHASE_11 (company settings, import runs) is DONE.
2. **Legacy CP entities removed:** `Customer`, `Organization`, `OrganizationDatabase`, `CustomerUser`, `OrganizationUser`, `PlanEntitlement`, `SubscriptionAddon`, `Application` (→ `Module`), `BusinessRole*` (→ `Role*`), `OrganizationUserBusinessRole`, the old billing stubs. No target table, column, key or value uses `ORG_` / `org_` / `org-` terminology (CD-26).
3. **Open schema decisions closed:** the S0-2 gap fixes (registry uniqueness, status-history tables, subscription series, audit columns, tax evidence key) are decided and implemented or rejected.
4. **CP test suite green** on `ddl-auto: update` against an empty MySQL.
5. **Written CP schema freeze** by the owner: no further CP entity change is merged during this phase except fixes found by its audit.

### 6.2 Final schema audit (per table)

For every final CP entity and table the audit verifies and records:
- table name; primary key; foreign keys and their delete/update behaviour (`RESTRICT` by default);
- unique constraints; indexes (including those needed by the later queries);
- nullability; default values; enum/status representation (`VARCHAR`, no native `ENUM`);
- audit columns (`created_at`, `created_by`, `updated_at`, `updated_by`); `version` where concurrent updates are possible;
- relationships and cardinality; naming consistency (`tbl_`, `uk_`, `fk_`, `idx_`, `ck_`);
- company isolation requirements (every company-owned row has `company_id`; no cross-company reference);
- historical and immutable financial data requirements (invoices, credit notes, payments, status histories: no update or delete paths, snapshots present);
- agreement with PHASE_01 §6.2 (any intended deviation is documented and approved).

Output: `saas-plan-doc/plans/PHASE_11A_CP_SCHEMA_AUDIT.md` (one row per table, findings, fixes). Mapping defects are fixed in the entities first, then the generated schema is re-captured.

### 6.3 Baseline structure

Owner's preferred organization:

```text
db/changelog/                       (CONTROL-PANEL-SERVICE resources)
  db.changelog-master.yaml
  schema/        (schema-level settings, if any)
  tables/        (one file per table: columns, PK)
  indexes/
  constraints/   (unique, foreign key, check)
  seeds/         (context "seed": sequences, catalog, permissions, system roles)
```

- Changesets are deterministic, ordered (referenced tables first), reviewable (no single huge generated file), idempotent where applicable (`MARK_RAN` preconditions), traceable (id, developer author, label, one-line comment) and documented.
- Where this layout differs from `LIQUIBASE_CONVENTIONS.md` (§1: FKs in the owning table's file; §6: XML master path), the owner confirms the final layout in task 2 and the conventions file is updated to match. This is a format choice, not an architecture change.
- Seed data contains no secrets and no environment-specific hosts (for example, no issuer URI of a real Keycloak).

### 6.4 Environments

| Case | Handling |
|---|---|
| **A. New clean database** (local, CI, staging, the **new** production CP DB of CD-05) | Run the baseline on an empty `control_panel_db`; then `validate` |
| **B. Existing development database** (created by `ddl-auto: update`) | **Never** run the baseline blindly on it. Default: back it up, create a fresh database from the baseline and re-seed development data. Only if the owner wants to keep it: diff it against a baseline-built DB; if the diff is empty, mark the baseline as applied (`changelog-sync`) under a written procedure; if not, keep the fresh-DB route. No automatic drop. |
| **C. Existing production database** | The legacy production `control_panel_db` on the protected host is **not touched** (CD-05, Q-20). Production uses a **new** CP DB created by case A. No production migration is part of this phase. |
| **D. Future migration from the legacy CP schema** | Not part of the baseline. If legacy CP data must ever be moved, a **separate controlled migration plan** is written and approved (owner-run exports only, no access to protected hosts). |

### 6.5 Hibernate after Liquibase

`ddl-auto: validate` in every environment. Hibernate never creates or alters the CP schema again. Every later CP change is an additive changeset (expand–contract), for example the `[FUTURE]` tables of PHASE_13 / PHASE_14.

### 6.6 Retirement of obsolete artifacts

The 26 generated changelogs and the old master are moved to `_cleanup_review/` by the owner with `svn move` (no deletion; a plain file move would be restored by `svn update`). Comments that say Liquibase never runs at startup are removed.

## 7. Exact services, files and database areas involved

| Area | Files |
|---|---|
| CP configuration | `CONTROL-PANEL-SERVICE/src/main/resources/application.yml`, `pom.xml` |
| CP changelogs | `CONTROL-PANEL-SERVICE/src/main/resources/db/changelog/**` (new baseline; old files retired) |
| CP tests | `CONTROL-PANEL-SERVICE/src/test/java/**` (baseline, parity, validate boot) |
| Documents | `saas-plan-doc/plans/PHASE_11A_CP_SCHEMA_AUDIT.md`, `LIQUIBASE_CONVENTIONS.md`, PHASE_01 §6.2 (final constraint names, if changed) |
| Databases | Local and development `control_panel_db` only; never a protected host |

## 8. Proposed schema changes

None beyond the final CP model. The baseline **records** the audited final schema; audit findings are fixed in the entities before the baseline is written.

## 9. API and integration requirements

None. No API changes.

## 10. Dependencies on other phases

- Requires §6.1 (PHASE_02–PHASE_11 CP entity work DONE, schema freeze).
- Required by PHASE_12 gate G6: staging and production run the CP on Liquibase + `validate`; PHASE_11A is a mandatory readiness gate before staging/production readiness (CD-28 item 10).
- PHASE_13 / PHASE_14 add their `[FUTURE]` CP tables as additive changesets on top of this baseline.

## 11. Security and tenant-isolation requirements

1. No password, token, secret or real host address in any changeset or seed.
2. No changeset drops or truncates data; the baseline is create-only.
3. Nothing in this phase connects to `192.168.5.52` or `192.168.5.53`; no production change; the legacy production CP DB is untouched.
4. Company isolation checks of §6.2 are part of the audit.

## 12. Ordered implementation tasks

| # | Task | Status |
|---|---|---|
| 1 | Confirm the entry criteria (§6.1) and record the owner's written CP schema freeze | PENDING |
| 2 | Baseline design review: layout (§6.3), ordering, contexts, author, labels; owner approval; align `LIQUIBASE_CONVENTIONS.md` | PENDING |
| 3 | Final schema audit (§6.2) on a fresh Hibernate-generated development DB; audit report; entity fixes | PENDING |
| 4 | Write the clean CP Liquibase baseline (tables, indexes, constraints incl. CHECKs, seeds) | PENDING |
| 5 | Parity and idempotency tests: baseline-built DB equals the Hibernate model; re-run applies 0 changesets; CHECKs and seeds verified | PENDING |
| 6 | Switch the CP to Liquibase at boot and `ddl-auto: validate`; boot IT on an empty DB | PENDING |
| 7 | Environment procedures A–D (§6.4), including the development-DB route and the statement that production uses a new DB | PENDING |
| 8 | Retire the old generated changelogs (owner `svn move` to `_cleanup_review/`) and update the documents | PENDING |

## 13. Acceptance criteria

1. The CP starts on an empty `control_panel_db` with Liquibase only, and `ddl-auto: validate` passes.
2. The schema built by the baseline equals the schema Hibernate generates from the final entities (empty diff of tables, columns, types, nullability, defaults, PK, unique keys, indexes, FKs and rules).
3. Running the baseline a second time applies 0 changesets.
4. Every table of the audit report is covered; every finding is closed or approved as a documented deviation.
5. No intermediate, temporary or legacy changelog exists in the CP master; the old generated files are retired.
6. Procedures A–D are documented; no step touches a protected host or production data.

## 14. Testing and validation checklist

- [ ] Baseline-on-empty-DB IT (Testcontainers MySQL), followed by `validate` boot.
- [ ] Parity IT (information-schema diff) baseline vs Hibernate.
- [ ] Re-run IT (0 changesets).
- [ ] CHECK constraint and seed tests.
- [ ] The full CP test suite on the Liquibase-built schema.

## 15. Risks and rollback considerations

| Risk | Mitigation |
|---|---|
| Hibernate `update` silently skipped or mis-applied a change during development (it never widens native enums, it swallows some DDL errors) | Audit on a **fresh** generated DB, not on a long-lived one; parity IT |
| The CP model keeps changing during the phase | Written schema freeze (§6.1.5) |
| A developer runs the baseline on an existing populated DB | Procedure B; preconditions; never run blindly |
| Layout disagreement with `LIQUIBASE_CONVENTIONS.md` | Task 2 owner decision; conventions updated |

**Rollback:** until task 6 is merged, the CP keeps running on `ddl-auto: update`. After it, rollback = redeploy the previous build against a backup of the development DB. Production is never touched by this phase.

## 16. Current status

| Item | Status |
|---|---|
| Owner decision CD-27 | DECIDED (2026-10-03) |
| Entry criteria | NOT MET (PHASE_02–PHASE_11 not started) |
| **Phase** | **PENDING** |
