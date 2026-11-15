# M0.5 Reconcile documents: report

**Milestone:** M0.5 in [milestone-chart.md](../milestone-chart.md) (roadmap phase R0, "Trustworthy baseline").
**Date:** 2026-11-14. **Platform:** Windows 11 Pro.
**Starting and ending `git rev-parse --short HEAD`:** `d896331` (unchanged). The working tree already held many uncommitted changes from earlier work; the files this milestone touched are listed below.

## Done-when and result

The chart's done-when for M0.5 is: *no row claims more than the evidence.* The roadmap's wording is: *reconcile INDEX and the feature matrix with dated results.*

**Met for the documents this milestone changed, with one limit stated plainly: M0.5 did not re-verify every row of the matrix or the INDEX.** There are about 250 matrix lines and about 35 INDEX phase rows, each resting on evidence from a different date. What was done is a targeted reconciliation of everything that was provably stale or contradicted by the 2026-11-14 results, an automated check of links and quoted test counts, and a visible statement in both documents of what their date does and does not support.

## What the audit found, and what changed

| Finding | Change |
|---|---|
| **The matrix headline was 15 days and 1,300 tests stale.** "Last verified 2026-10-30 ... 563 tests". | Replaced with "Last reconciled 2026-11-14": 1,869 tests, 0 failures, 1 skip, 23 modules, plus 478 web tests, the skip's cause, and a paragraph on what the date supports (a passing run supports "Implemented", not "Validated"). The old figure is kept as a labelled historical baseline. |
| **The matrix's R0 section contradicted the new results.** It said File > Open "does not yet open the file in its own editor" and that a light's direction "can move (about 1e-7)" on copy. | Rewritten as "Roadmap R0 (2026-11-13 and 2026-11-14)" with accurate rows for Duplicate, the every-component consistency test, the light fix, Play stopping with a reason, File > Open by type, the camera default, the acceptance project (marked **partly validated**: no person built it by hand), the trace aid and the smoke runs. |
| **The Play-mode row cited 13 tests** and said nothing of the crash. | Now 18 tests, and says that a scene failing on its first step used to end the editor and now stops with a message. |
| **The INDEX pointed at the oldest roadmap** and described R0 as it was on 2026-11-13. | The roadmap line now names the current roadmap and the chart, with earlier versions marked as history. The R0 line states the current state, including that the acceptance chain has **not** been run by a person. Lines for M0.4 and M0.5 were added; M0.1 to M0.3 were already there. |
| **Four INDEX phase rows said "Complete"** (Phases 0 to 8, 9, 10, 11) on the strength of reports written before 2026-10-29, which AGENTS.md says sometimes call work complete without in-app evidence. | Each now says "Reported complete in the cited report (written before 2026-10-29, not re-verified by a person); automated tests pass in the 2026-11-14 full run". A sentence above the table says rows were not re-verified except where dated. |
| **A cited report does not exist.** `reports/td-068-component-nodes-report.md` is linked as evidence from the INDEX, the matrix header, `TECHNICAL_DECISIONS.md` (TD-068 implementation notes) and the milestone page, but it is not on disk and was never in Git history. | I did **not** write a report for work I did not observe. The four links were replaced by an explicit statement that the report is missing from the repository (checked 2026-11-14) and that its results cannot be checked. **The TD-068 results those pages rely on are therefore unverified until someone supplies or re-creates that report.** |
| **Quoted counts were stale in undated places.** INDEX Phase 20 said `engine-audio` 178 and `editor-audio` 53; the matrix said `editor-sculpt` 43. | Annotated: "178 tests when written, 233 on 2026-11-14", and likewise 53 to 57 and 43 to 56. (A scan of both documents for `module N tests` found four hits; two were real, one was in a dated row and annotated, one was a Vitest count my pattern misread.) |
| **The first R0 report listed items later closed.** | An addendum (§8) records what M0.2, M0.3, M0.4 and M0.5 changed. The report's body is kept as written. |
| **My M0.1 to M0.3 reports and the chart did not mention the first R0 pass** (2026-11-13), which had already fixed Duplicate, added the "not a scene" message and found the Kotlin test passing. | A "Relationship to the earlier R0 pass" section was added to the three reports, and the chart rows cite it. Credit where it belongs: the Duplicate gap I called "already fixed" in M0.2 was fixed by that pass. |
| **A test tolerated a bug that is now fixed.** `DuplicateSampleEntitiesTest` allowed a 1e-5 difference because of the light-direction drift. | The tolerance is now 0. The test passes for all 1,471 entities of the 36 sample scenes compared exactly. |

## Automated checks run

| Check | Result |
|---|---|
| Relative links in every Markdown file under `docs/` | Before this milestone's final edits: 5 broken (the TD-068 report, cited from four files, plus the not-yet-written M0.5 report). After: none broken. |
| Milestone chart structure | 139 distinct milestone ids, no duplicates, every row has the right columns. |
| `./gradlew :editor-desktop:test` | Exit 0 (it includes `EditabilityAuditTest`, which fails when the audit table in the matrix is stale, and the exact-comparison `DuplicateSampleEntitiesTest`). |

No application code changed in this milestone other than the one test tolerance above.

## What M0.5 did not do

- **It did not re-verify each matrix row.** A row that says "Implemented" still rests on its own date; the matrix header now says so. Rows that say "Not validated" are still waiting for a person. Verifying them is what the milestone that touches each feature, and the visual checklist, are for.
- **It did not rewrite older reports** (Phases 9 to 11 and others from before 2026-10-29), only the INDEX rows that cite them.
- **It did not run the hands-on editor session** (the person's part of M0.4).
- **It did not resolve the missing TD-068 report**, only stopped citing it as if it existed.

## Files changed

- `docs/FEATURE_TEST_MATRIX.md` (header, Play-mode row, R0 section, one count annotation)
- `docs/INDEX.md` (roadmap line, R0 line, M0.4 and M0.5 lines, four "Complete" rows, Phase 20 counts, the TD-068 link, a note above the phase table)
- `docs/reports/r0-baseline-report.md` (addendum), `m0-1`, `m0-2`, `m0-3` reports (relationship sections), `docs/milestone-chart.md` (M0.1, M0.2 and M0.5 rows)
- `docs/TECHNICAL_DECISIONS.md` and `docs/milestones/td-068-component-nodes.md` (the missing-report statement)
- `editor-desktop/.../DuplicateSampleEntitiesTest.java` (tolerance 0)
- New: this report.

## Open items for the owner

1. **The TD-068 report is missing.** Supply it, or accept TD-068's results as unverified. Two further documents depend on that report's per-module counts.
2. **The person's hands-on acceptance run** (checklist in the M0.4 report) is the only thing between M0.4's "partly met" and "met", and with it R0 can be called done.
3. **The older "Complete" claims** (Phases 0 to 11) remain unverified. Deciding how much of them to re-check is a scope question for R1's editability audit.

## Acceptance criteria (AGENTS.md §10)

1. Criteria listed and marked: done above, with the limit stated.
2. Exact commands and checks: done above. No per-module counts changed (no behaviour changed); the totals are those of the M0.4 run.
3. New failures investigated: none.
4. Diff reviewed, no unrelated changes: only the files above.
5. Matrix and INDEX updated: this milestone's purpose.
6. Limits and next steps: above. The next phase is R1 (editable examples and project templates).
7. **No commit, push or other destructive Git operation was performed.**
