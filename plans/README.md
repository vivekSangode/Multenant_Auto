# Implementation Plans — Index

The plans add no architecture decision (PHASE_01 §0 wins). **Status 2026-10-13 (current):** PHASE_02–PHASE_06 DONE; the PHASE_07 plan **APPROVED** (CD-45) and PHASE_07 UNLOCKED — P07-T1..T4 DONE, P07-T11 **DONE** (owner approval 2026-10-13 after the live SMTP smoke test; implemented under CD-65), P07-T5 **DONE** (*2026-10-14:* owner approval after CD-66 / CD-68 / CD-69 and the final verification), P07-T6 **DONE** (*2026-10-14:* owner approval after CD-70 / CD-71 and the final independent review), P07-T7..T10 and P07-T12 PENDING; PHASE_08–PHASE_12 plans unchanged (LOCKED phases). Authoritative status: [PHASE_STATUS_TRACKER.md](../PHASE_STATUS_TRACKER.md). The rows below keep their earlier wording; current notes are marked *2026-10-13*. **Status 2026-10-09:** PHASE_06 in progress (P06-T1, P06-T2 DONE; P06-T4 REVIEW). **Status 2026-10-08:** PHASE_02–PHASE_05 executed (phases DONE; the PHASE_05 plan APPROVED by F5-1); the PHASE_06 plan **APPROVED** (CD-33; PHASE_06 UNLOCKED); the PHASE_07–PHASE_12 plans are still **DRAFT FOR OWNER REVIEW**. Status of each is in [PHASE_STATUS_TRACKER.md](../PHASE_STATUS_TRACKER.md) (phase notes).

| Phase | Plan | Tasks | First task when unlocked |
|---|---|---|---|
| Stage 0 | [S0-5 baseline test report](S0-5_BASELINE_TEST_REPORT.md) + [failing tests](S0-5_BASELINE_FAILING_TESTS.txt) | — | REVIEW (owner sets DONE) |
| PHASE_02 | [PHASE_02_IMPLEMENTATION_PLAN.md](PHASE_02_IMPLEMENTATION_PLAN.md) (r2, CD-27) | 9 active (T1, T10 deferred to PHASE_11A) | P02-T0, P02-T9 |
| PHASE_03 | [PHASE_03_IMPLEMENTATION_PLAN.md](PHASE_03_IMPLEMENTATION_PLAN.md) | 13 | P03-T1 |
| PHASE_04 | [PHASE_04_IMPLEMENTATION_PLAN.md](PHASE_04_IMPLEMENTATION_PLAN.md) | 9 + early slices E-A, E-B | E-A / E-B (if S0-10 / S0-13 (a)), then P04-T1 |
| PHASE_05 | [PHASE_05_IMPLEMENTATION_PLAN.md](PHASE_05_IMPLEMENTATION_PLAN.md) (APPROVED; phase DONE 2026-10-08) | 7 (T7 DEFERRED → P06-T8) | — (done) |
| PHASE_06 | [PHASE_06_IMPLEMENTATION_PLAN.md](PHASE_06_IMPLEMENTATION_PLAN.md) (APPROVED, CD-33; phase UNLOCKED) | 8 | *2026-10-13:* phase **DONE** (all tasks incl. P06-T6-F1 / P06-T3-F1 DONE; P06-T8 took over P05-T7). Earlier (2026-10-09): P06-T1, P06-T2 DONE; **P06-T4 REVIEW** (owner review pending); next after acceptance: **P06-T5** (readiness first; order T5 → T6 → T3 → T7 → T8) |
| PHASE_07 | [PHASE_07_IMPLEMENTATION_PLAN.md](PHASE_07_IMPLEMENTATION_PLAN.md) (*2026-10-13:* APPROVED, CD-45; phase UNLOCKED) | 11 (*2026-10-13:* 12 — P07-T12 added by CD-43 item 11) | P07-T1, P07-T11 (*2026-10-13:* T1..T4 DONE, **T11 DONE** (owner approval 2026-10-13; SVN commit by the owner); plan §4: T11 before T5 — P07-T5 readiness NOT READY; *2026-10-14:* **T5 DONE** (owner approval; G-B redefined by CD-66 Q2-R; nothing committed, CD-67); *2026-10-14:* **T6 DONE** (owner approval; uncommitted, CD-67); next: P07-T7 readiness) |
| PHASE_08 | [PHASE_08_IMPLEMENTATION_PLAN.md](PHASE_08_IMPLEMENTATION_PLAN.md) | 10 | P08-T1 |
| PHASE_09 | [PHASE_09_IMPLEMENTATION_PLAN.md](PHASE_09_IMPLEMENTATION_PLAN.md) | 9 (T3 after P10-T13) | P09-T9 |
| PHASE_10 | [PHASE_10_IMPLEMENTATION_PLAN.md](PHASE_10_IMPLEMENTATION_PLAN.md) | 16 (two tracks) | P10-T11 / P10-T1 |
| PHASE_11 | [PHASE_11_IMPLEMENTATION_PLAN.md](PHASE_11_IMPLEMENTATION_PLAN.md) | 10 | P11-T4 |
| PHASE_11A | Task list in [PHASE_11A_FINAL_CP_LIQUIBASE_BASELINE.md](../PHASE_11A_FINAL_CP_LIQUIBASE_BASELINE.md) §12 (CD-27) | 8 | after S0-11 schema freeze |
| PHASE_12 | [PHASE_12_IMPLEMENTATION_PLAN.md](PHASE_12_IMPLEMENTATION_PLAN.md) | 11 | P12-T1, P12-T7 (parallel with PHASE_02) |
| PHASE_13 / 14 | No plan yet (FUTURE, after R1) | — | — |

## Owner decisions raised by the plans (tracker Stage 0; decided 2026-10-03 as CD-28 unless noted)

| Tracker | Plan | Question (short) | Decision (CD-28) |
|---|---|---|---|
| S0-10 | PHASE_02 D-6 | Order of the `tbl_module` catalog entity (owner PHASE_04) before P02-T3/T4 | DONE: functional PHASE_04 table; P04-T6 after P02-T2 |
| S0-12 | PHASE_11A | Is PHASE_11A a precondition of gate G6? (PROPOSAL only) | DONE: mandatory readiness gate before staging/production (after S0-11) |
| S0-13 | PHASE_03 D3-1 | `PlatformAdmin` entity (owner PHASE_04) before P03-T2 bootstrap | DONE: PHASE_04 owns the entity and the bootstrap integration; bootstrap with secret-supplied initial credentials, forced password change, audit (CD-28 item 9) |
| S0-14 | PHASE_04 D4-1 | Final list of platform roles and default policies | DONE: seed as inactive/default templates; explicit, audited activation and assignment |
| S0-15 | PHASE_05 D5-1 | Seller entity / billing policy ownership PHASE_05 vs PHASE_07 | DONE: PHASE_05 |
| S0-16 | PHASE_07 D7-1 | Storage of invoice PDFs and payment evidence ("platform bucket" undefined) | DONE: dedicated operator-controlled billing bucket, isolated from company buckets |
| S0-17 | PHASE_08 D8-2 | Source of the Company Super Admin invitation email | DONE: generated only by the controlled provisioning workflow, audited, no secrets exposed |
| S0-2 | several | The 6 gap fixes; items 1 (registry uniqueness), 3 (subscription series), 4 (audit columns), 5 (tax evidence key), 6 (signed-JWT key management) block specific tasks | Items 1 and 4 APPROVED (CD-28), item 5 (CD-32), item 3 (CD-33, 2026-10-08); items 2 and 6 still open |

Report-only items (no decision needed unless the owner disagrees) are listed in each plan's "Owner decisions / clarifications" and "Documentation findings" sections.

## Rules for every implementing agent

- Stage 0 gates first (S0-4 safeguard, S0-5 accepted); claim the task in the tracker; REVIEW, never DONE.
- CP schema: JPA entities with `ddl-auto: update`, **no CP Liquibase** until PHASE_11A (CD-27).
- Tests locally only (since CD-29 / CD-30: MySQL ITs on the local test MySQL 192.168.5.141, Redis `erp-it-redis` 127.0.0.1:16379, Keycloak 25.0.6 on 127.0.0.1:8081 — see NEXT_SESSION_PROMPT §3); never `192.168.5.52/53`; build only when no other (IDE) build of the working copy runs (S0-5 §4.2).
- `CMP_<n>` is the only target company identifier; `ORG_*` / `org_*` / `org-*` are LEGACY only.
- In Bash-tool perl regexes, write a literal backslash as `\x5c` (the tool collapses `\\`).
