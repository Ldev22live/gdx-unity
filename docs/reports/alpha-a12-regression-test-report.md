# Alpha A12: Regression tests

**Date:** 2026-11-25. **Machine:** Windows 11, 16 GB RAM, with 0.7 to 2.6 GB free during the runs (browsers used most of it). **Method:**
- every run used `--max-workers=1 --offline -x generateAssetList`;
- modules ran in bounded batches, mostly in the foreground;
- counts were read from the JUnit XML reports, never inferred from a quiet exit;
- no test was disabled, and no JVM setting was changed.

## Results (last run of each module, 2026-11-25)

| Module | Classes | Tests | Skipped | Failures |
|---|---|---|---|---|
| engine-core | 11 | 61 | 0 | 0 |
| engine-scene | 38 | 254 | 0 | 0 |
| engine-render | 34 | 213 | 0 | 0 |
| engine-visualscript | 13 | 93 | 0 | 0 |
| engine-animation | 4 | 32 | 0 | 0 |
| engine-sculpt | 12 | 90 | 0 | 0 |
| engine-audio | 22 | 233 | 0 | 0 |
| engine-physics | 4 | 60 | 0 | 0 |
| engine-character | 3 | 19 | 0 | 0 |
| engine-sprite | 7 | 49 | 0 | 0 |
| engine-vector | 4 | 20 | 0 | 0 |
| engine-material | 3 | 15 | 0 | 0 |
| engine-terrain | 7 | 46 | 0 | 0 |
| engine-desktop | 37 | 173 | 0 | 0 |
| editor-core | 91 | 631 | 1 (the POSIX-only test) | 0 |
| editor-terrain | 11 | 63 | 0 | 0 |
| editor-visualscript | 19 | 94 | 0 | 0 |
| editor-animation | 2 | 17 | 0 | 0 |
| editor-sculpt | 12 | 68 | 0 | 0 |
| editor-audio | 6 | 57 | 0 | 0 |
| editor-character | 1 | 10 | 0 | 0 |
| editor-sprite | 9 | 66 | 0 | 0 |
| editor-desktop | 53 (all, in five groups) | 220 | 0 | 0 |
| **Java total** | **403** | **2,584** | **1** | **0** |
| engine-babylon (web, `npx vitest run`; `npx tsc --noEmit` clean) | 42 files | **532** | 0 | 0 |
| engine-web (TeaVM) | not run | | | Excluded from Alpha acceptance (TD-106 classification) |

## Failures seen on the way, and their causes

| Failure | Cause | Resolution |
|---|---|---|
| `GameComponentSerializersTest`, `BackendSupportMatrixTest` (2) | A12 registered `OwnerComponent`; the registration checks require a matrix row and the count | Row added to `BACKEND_SUPPORT_MATRIX.md`; count 43 → 44 |
| `WebUxContractTest` (stale vocabulary) | **Earlier milestones** (Platformer + 2D, Third-person Shooter + 3D became playable), not A12 | Regenerated (`GDXUNITY_UPDATE_AUDIT=1`); a diff against a snapshot taken first showed only those two cells |
| `SampleInventoryTest` (stale table) | A12: the valley sample is now also tested by the web `contract.test.ts` | Regenerated; one cell, checked against a snapshot |
| Web `shouldBuildEverySampleSceneOfTheDesktopEditor` (once) | Only while a Java build competed for the CPU; it passed alone and in the next full run | Not a code defect; the exact message was not captured |
| Web `shouldSupportEveryComponentTypeUsedByTheSamples` (from A00) | The valley's `AtmosphereComponent` | Resolved by the owner's classification (optional on the web), with a preservation test |

## Interrupted runs

- Claude Code stopped three background runs for low memory. None was counted. Each module was re-run to completion afterwards in smaller batches.
- **Not a single invocation:** the whole repository was never run in one `gradlew test`. It was covered module by module, in batches of this session (2026-11-25).
