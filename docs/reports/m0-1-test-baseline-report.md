# M0.1 Green test baseline: report

**Milestone:** M0.1 in [milestone-chart.md](../milestone-chart.md) (roadmap phase R0, "Trustworthy baseline").
**Date:** 2026-11-14. **Platform:** Windows 11 Pro, Java 21, Gradle wrapper, Node for the web tests.
**Starting and ending `git rev-parse --short HEAD`:** `d896331` (unchanged). The working tree already held many uncommitted changes from earlier work (terrain, materials, level editor, documents); this milestone added only this report and two documentation edits.

## Relationship to the earlier R0 pass

An earlier R0 pass ([r0-baseline-report.md](r0-baseline-report.md), 2026-11-13) had already found the Kotlin generator test passing (its criterion 2) and run the suite (1,707 tests). This milestone repeats that from scratch with `cleanTest` so nothing is taken from Gradle's cache, adds the web tests, and records every skip's cause.

## Done-when and result

The chart's done-when for M0.1 is: *a full run passes, or each failure is listed as pre-existing with a cause.*

**Met.** A from-scratch run of every Gradle test exited 0 with no failures and no errors, and the web tests all passed. There is nothing to list as a pre-existing failure. One test is skipped, with a known cause (below).

## What the roadmap asked for, item by item

| Item | Result |
|---|---|
| Fix the Kotlin generator test failure | **Nothing to fix: it no longer fails.** The test named in the 10 Nov status, `GraphMethodNodeCodeTest.shouldMakeKotlinThatCompilesAndDoesWhatTheInterpreterDoes` (an Int/Float mismatch in generated Kotlin), ran in this run and passed; the class passed 5 of 5. I did not find out which change fixed it, because no change was needed here. `docs/FEATURE_TEST_MATRIX.md` already records a numeric-widening fix in `GraphCodeGenerator.input`. |
| Run the full suite | Done, from scratch (below). |
| Record per-module counts | Done (table below). |
| Record every skip | One skip, explained below. |

## Commands and exit codes

| Command | Exit | Result |
|---|---|---|
| `./gradlew cleanTest test --continue --console=plain` | 0 | 1,849 tests, 0 failures, 0 errors, 1 skipped, 23 modules. `cleanTest` forces every test to run again so Gradle's up-to-date cache cannot hide a failure. |
| `npm test` in `engine-babylon` (vitest) | 0 | 37 test files, 478 tests passed. |

Output noise that is not a defect: a JVM class-data-sharing warning from Gradle workers, and Node's "localStorage is not available" experimental warning from vitest.

## Per-module counts (Gradle, 2026-11-14)

Counts read from `<module>/build/test-results/test/*.xml`. "Before" is the earlier full run of the same day (before this milestone, same code apart from the documents); the two runs agree for every module.

| Module | Tests | Skipped | Failures | Errors |
|---|---:|---:|---:|---:|
| editor-animation | 17 | 0 | 0 | 0 |
| editor-audio | 57 | 0 | 0 | 0 |
| editor-character | 10 | 0 | 0 | 0 |
| editor-core | 396 | **1** | 0 | 0 |
| editor-desktop | 116 | 0 | 0 | 0 |
| editor-sculpt | 56 | 0 | 0 | 0 |
| editor-sprite | 51 | 0 | 0 | 0 |
| editor-terrain | 15 | 0 | 0 | 0 |
| editor-visualscript | 89 | 0 | 0 | 0 |
| engine-animation | 32 | 0 | 0 | 0 |
| engine-audio | 233 | 0 | 0 | 0 |
| engine-character | 19 | 0 | 0 | 0 |
| engine-core | 36 | 0 | 0 | 0 |
| engine-desktop | 98 | 0 | 0 | 0 |
| engine-material | 15 | 0 | 0 | 0 |
| engine-physics | 53 | 0 | 0 | 0 |
| engine-render | 144 | 0 | 0 | 0 |
| engine-scene | 159 | 0 | 0 | 0 |
| engine-sculpt | 81 | 0 | 0 | 0 |
| engine-sprite | 44 | 0 | 0 | 0 |
| engine-terrain | 33 | 0 | 0 | 0 |
| engine-visualscript | 86 | 0 | 0 | 0 |
| engine-web | 9 | 0 | 0 | 0 |
| **Total** | **1,849** | **1** | **0** | **0** |

Web player (`engine-babylon`): 478 tests in 37 files, all passing.

## The one skip

`GameExporterTest.shouldKeepTheLauncherExecutableOnALinuxImage` (editor-core). It begins with `assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"))`. Windows file systems have no POSIX permission view, so the test skips here by design. It checks that an exported Linux launcher keeps its execute bit, so it can only run on Linux or macOS. This is an environment limit, not a defect. It belongs to the cross-platform release checks in milestone M9.4.

## Limits of this evidence

- **Headless only.** These are automated tests. They do not show that any feature works in the running editor; the chart's other milestones carry that debt (the AGENTS.md "Manually Not Verified (Headless Environment)" wording still applies to graphical behaviour).
- **Not run:** the editor and game-runtime smoke tests (`--smoke-test`), which need a display, and any packaged build. Only the Windows host was used; Linux and macOS were not.
- **Pre-existing failures:** none found. The other gaps the 10 Nov status listed (Duplicate component, save and load, Play isolation, undo and redo for the newest components, File > Open by type) are milestones M0.2 and M0.3 and were not examined here.

## Acceptance criteria (AGENTS.md §10)

1. Criteria listed and marked: done above.
2. Exact commands, exit codes and per-module counts: done above.
3. New failures investigated, pre-existing distinguished from regressions: there were none of either.
4. Diff reviewed, no unrelated changes: this milestone changed no code. It added this report and edited `docs/milestone-chart.md` and `docs/INDEX.md`.
5. Matrix and INDEX updated: INDEX and the chart updated; no `FEATURE_TEST_MATRIX.md` row changed, because no feature or test changed.
6. Known limitations and next steps: above. Next is M0.2, the data-consistency gaps.
7. **No commit, push or other destructive Git operation was performed.**
