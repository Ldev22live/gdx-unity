# Alpha A12.10: Cross-platform round-trip verification

**Date:** 2026-11-25. **Status:** **Passed in automated tests, in both directions, with each runtime reading files the other wrote.**

## 1. Method

Java and TypeScript cannot run in one process, so the chain passes through committed project folders under `fixtures/contract-v1/interop/`. Each is written by one runtime's test and read by the other's.
- **Fixed inputs:** ids (name-based UUIDs) and times are fixed, so each step writes the same bytes every run.
- **Staleness check:** each step's output must equal its committed folder, or the test fails.
- **Regenerating:** set `GDXUNITY_UPDATE_INTEROP=1` (desktop) and `UPDATE_INTEROP=1` (web), then review the diff.

| Step | Runtime and test | Reads | Does | Writes |
|---|---|---|---|---|
| 1 | Desktop, `CrossPlatformInteropTest.step1…` | `valley-project` (the contract fixture) | Opens through `LocalProjectStore`, loads and saves with the full serializer list (semantic diff empty), saves a revision | `1-desktop` |
| 2 | Web, `interop.test.ts` "step 2" | `1-desktop` | Checks every desktop-made SHA-256 and the marker; capabilities "limited" (the optional profile); moves House (x 720) and renames Rock to Boulder with `EditorModel`; diff shows exactly those 2 edits; `nextManifest` (web revision based on 1) | `2-web` |
| 3 | Desktop, `step3…` | `2-web` | Open notes empty: hashes, marker and capabilities agree after a web save; base is 1's revision; the web's move and rename arrived; the unknown component, scenario and ownership are intact (`ScenarioValidator` clean); then the advanced edits: Tower's owner changes, Sun intensity 1.5; diff shows exactly 2 changes | `3-desktop` |
| 4 | Web, "step 4" | `3-desktop` | Base is 2's revision; both clients' 4 edits present; against the original fixture, **only those 4 edits differ** (with the desktop normalizations); unknown members, scenario and extension key intact; a web load and save of it changes nothing | (reads only) |
| r1 | Web, "step r1" | none | The web creates a project: a scene with an owned crate and a scenario block, a manifest (base none, client web) and a marker | `r1-web` |
| r2 | Desktop, `stepR2…` | `r1-web` | Opens with no notes; desktop load and save of the web-made scene is semantically equal; ownership read; scales the crate to 2; only that differs | `r2-desktop` |
| r3 | Web, "step r3" | `r2-desktop` | Same project id; base is r1's revision; only the scale differs from r1; a web load and save changes nothing | (reads only) |

## 2. Verified items (directive §16)

| Item | Where verified |
|---|---|
| Project identity | Steps 3, 4 and r3 (same `projectId`; base chain) |
| Scene identity | Manifests carry the same scene ids through the chain (committed files) |
| Entity identity | Every diff matches entities by id; no MISSING or ADDED entity |
| Transform values | Step 2 move, r2 scale |
| Asset references | Material path unchanged through 1 to 4 |
| Asset hashes | Verified by the other runtime at every step (`verifyAssets`, `AssetInventory.verify`) |
| Material properties | `materials/Meadow.material` byte-identical in every folder |
| Terrain data | **Not covered:** the fixture has no `.terrain` file yet (listed as an A12 gap in the final report) |
| Ownership | Steps 3, 4, r2 and r3 |
| Scenario data | Steps 3 and 4 (validator; raw equality) |
| Gameplay configuration | `gameMode` and `gameGenre` unchanged through the chain |
| Unsupported components and unknown fields | `ExampleFutureComponent`, `futureTerrainBlend` and `exampleFutureSetting` intact at every step |
| Revision information | Base chain 1 → 2 → 3 and r1 → r2; client recorded per step |

## 3. Commands

- **Generation, in dependency order (update flags):** desktop step 1 → web steps 2 and r1 → desktop steps 3 and r2.
- **Verification, without flags:**
  - `npx vitest run test/interop.test.ts`: **4 of 4 pass**;
  - `.\gradlew.bat :editor-desktop:test --tests "*CrossPlatformInteropTest"`: **3 of 3 pass**.
