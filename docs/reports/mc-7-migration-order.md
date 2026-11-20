# MC.7: Regression Fixtures and Migration Order

**Date:** 2026-11-18. **Branch:** `phase-12-3d-foundation`. **HEAD at start and end:** `0469ceb` (nothing committed). **Gate:** none in the chart.

**Why this milestone:** MC.1 (its only dependency) was finished just before. MC.7 was the one unblocked ungated row left that a person is not needed for. M1.2 is in progress, M1b.5's remaining part is manual, M4.5 belongs to R4, whose design TD (M4.1) is not approved, and M8.1 needs a person listening.

## 1. Acceptance criteria

| # | Criterion (chart row MC.7) | Status | Evidence |
|---|---|---|---|
| 1 | Before downloading anything, an inventory of sample scenes, models, sprites, audio and `.song` files with path, licence, replacement, workspace, source and export format, runtime support, cost, tests and rollback | **Met** | Path, licence, workspace, source format, runtime support and tests per file: the [MP.1 inventory](sample-asset-migration-inventory.md). Replacement, export format, cost and rollback per sample group: [`docs/migration/sample-migrations.json`](../migration/sample-migrations.json), with the replacements taken from [MC.1's candidates](authoring-sample-source-audit.md). Nothing downloaded |
| 2 | Originals kept as separate fixtures | **Met** | Each slice records its original. The test asserts that the original is still offered beside its copy and that every id it had is kept (except the entities the slice replaced) |
| 3 | Opt-in migrations one slice at a time, in the order 3D, 2D, 2.5D, audio | **Met as one slice per sample; order deviation recorded** | One slice per original (asserted). The two slices done so far, MP.3's 2D and 2.5D, came before the 3D slice, which needs R7a and downloads. `orderNote` in the ledger says why. The order still applies to downloaded replacements, which start with 3D |
| 4 | Screenshots, manifests, load, undo, redo, collision, performance and export tests per slice | **Met for both slices; manifests n/a** | Each slice names its evidence, and the test checks that every named test method, screenshot and file exists. Undo and redo, and the performance comparison, were the gaps; both are added here. No slice uses a downloaded asset, so no manifest is needed. The test refuses a slice with a downloaded asset until M2b.1's manifest exists |
| 5 | Done-when: "No sample is replaced all at once and each migrated slice has its evidence" | **Met** | `SampleMigrationsTest` (5 of 5) |

## 2. What was built

- **[`docs/migration/sample-migrations.json`](../migration/sample-migrations.json)** places all 45 samples in six groups:

  | Group | Samples | Plan |
  |---|---|---|
  | Landscapes | 6 | `.terrain` ground (M3.10), a Kenney house, Poly Haven rocks |
  | House slice | `action-rpg-valley-3d` | MC.2 |
  | Pixel 2D | 5 | Kenney Pixel Platformer |
  | Hybrid 2.5D | 8 | — |
  | Keep (primitives) | 18 | No asset replacement; their gap is Java logic (M1.2) |
  | Keep (characters and animation) | 7 | — |

  - **Each group** records its replacement (MC.1 candidate numbers), workspace, source and export formats, runtime support, cost (where M9.5 measured it), rollback, blockers and status.
  - **The audio slice** (a door sound, footsteps and an edited song) is planned against three targets, blocked on MC.5 and MC.6.
  - **The two MP.3 slices** are recorded with their original, generator, replaced entities, new files, downloaded assets (none) and evidence.
- **[`docs/migration/mp-3-performance.tsv`](../migration/mp-3-performance.tsv):** the four scenes measured with the M9.5 runner (3 runs each, medians).
- **`SampleMigrationsTest`** (editor-desktop, 5 tests):
  - **Exactly one group per sample:** every shipped sample is in exactly one group, so a new sample fails until it is planned.
  - **One slice per original:**
    - each slice writes a new file, and no original is migrated twice;
    - original and copy are both in the gallery, and the generator is kept;
    - a downloaded asset is refused without a manifest;
    - every original id survives except the replaced entities.
  - **Evidence exists:** every evidence item a slice names exists (test class and method, screenshots, performance file).
  - **2D undo and redo (new evidence):**
    - painting one tile in the migrated tile map with the 2D Studio's `TilemapSession` makes a new 20 × 20 collision box at (610, 130);
    - undo restores the exact cells and boxes; redo puts the tile back.
  - **2.5D undo and redo (new evidence):**
    - raising the migrated terrain with the level editor's terrain tool (`TerrainEditTool`, the same command the World strip runs) makes a slope;
    - the `.terrain` file follows the edit, the undo and the redo.
    - This runs on a copy of the terrain file in a temporary project: the shipped file was not written (its time is unchanged).

## 3. Performance of the slices (medians of 3 runs, game runtime, glFinish)

| Scene | Parse ms | Start ms | First frame ms | Frame median ms | Frame 95th ms | Frame max ms | Terrain triangles | Heap kept MiB |
|---|---|---|---|---|---|---|---|---|
| `platformer-2d` | 107.7 | 161.3 | 142.0 | 1.22 | 1.85 | 2.81 | 0 | 4.88 |
| `platformer-tiles-2d` | 117.2 | 235.5 | 330.9 | 1.18 | 1.74 | 7.72 | 0 | 5.03 |
| `platformer-profile-25d` | 102.3 | 243.7 | 206.1 | 2.26 | 3.08 | 4.44 | 0 | 4.94 |
| `platformer-profile-terrain-25d` | 103.0 | 248.0 | 234.2 | 2.24 | 3.23 | 20.94 | 2,816 | 5.98 |

- **Frame cost is unchanged.** Medians and 95th percentiles are within 0.1 ms, below M9.5's noise floors.
- **Loading costs a little more:**
  - the tile copy's start is 74 ms longer and its first frame 189 ms longer (the tile set is read and its atlas built);
  - the terrain copy's first frame is 28 ms longer.
- **One slow frame each:** the copies' single worst frame was 7.7 ms and 20.9 ms. M9.5 does not compare the maximum frame, so these are recorded, not judged.
- **Interrupted first attempt:** the first measurement run stopped at frame 276 of 330 of `platformer-2d`'s third run (exit 1), and the runner failed the whole recording as designed. The repeat, with the window left alone, was clean: 12 of 12 runs, 0 errors. The cause was not established; the likely one is the window being disturbed.

## 4. Commands and results

| Command | Exit | Result |
|---|---|---|
| `./gradlew :editor-desktop:test --tests "*SampleMigrationsTest"` | 1, then 0 | First a compile error (a loop variable used in a lambda); fixed; then 5 of 5 |
| `./gradlew :engine-desktop:perfBaseline -PperfScenes=<the four>` | 1, then 0 | See section 3 |
| `./gradlew :editor-desktop:test` | 1, then 0 | First: 1 failure, `SampleInventoryTest`'s staleness guard. The new test names two samples, so the inventory's "tests naming it" column changed. After `GDXUNITY_UPDATE_AUDIT=1` regenerated it: **180** (175 before, +5), 0 failures |

engine-desktop (147) and editor-core (539) were not changed. **Not run:** the full `test --continue` and the Babylon suite (no scene, node or component changed).

## 5. Limitations and next steps

- **The groups' replacement plans are plans.** Each one names the blocker it waits on, mostly the Legal gate (M2b.2 and M2b.5), R7a, M3.10, MC.3 and MC.5.
- **Undo and redo were checked on the editing sessions,** not by a person in the running editor. **Manually Not Verified:** painting and sculpting these scenes by hand.
- **Performance was measured on this Windows machine only,** like the M9.5 baseline.
- **Next unblocked work:**
  - M1.2's remaining graph conversions (in progress, ungated). They are the largest editability gap in MT.8's audit.
  - Everything else waits on an approval, the Legal gate, or a person.

## 6. Diff review and Git

- **New:**
  - `docs/migration/sample-migrations.json` and `docs/migration/mp-3-performance.tsv`
  - `SampleMigrationsTest.java`
  - this report
- **Changed:** the generated tables of `sample-asset-migration-inventory.md` (the new test's name); the MC.7 row and the MC overview in `docs/milestone-chart.md`, `docs/FEATURE_TEST_MATRIX.md` and `docs/INDEX.md`. All three were already modified; only these lines were added.
- No commit, push, stash, reset or other Git operation was performed. HEAD is `0469ceb` at start and end.
