# Roadmap R0 — Trustworthy Baseline: Report (2026-11-13)

Phase R0 of [13-nov-forgeroom-roadmap.md](../13-nov-forgeroom-roadmap.md). **Status: partly done.** The automated parts are
implemented and tested; the end-to-end acceptance project needs a person and has **not** been run.

- Branch: `phase-12-3d-foundation`
- Starting `git rev-parse --short HEAD`: `d896331`. Ending: `d896331` (no commit was made).
- The working tree already held about 130 uncommitted files from the owner before this phase (graph samples, audio
  and editor changes, docs). They were left as they were; this phase's own changes are listed in §4.

## 1. Acceptance criteria

| # | Criterion (roadmap R0) | Result | Evidence |
|---|---|---|---|
| 1 | A full test run passes, or every failure is listed with a cause | **Met** | `./gradlew test --continue`: exit 0, 1,707 tests, 0 failures, 1 skipped; one more test was added afterwards and its module re-run: 1,708 (§2) |
| 2 | Fix the Kotlin generator test failure | **Nothing to fix now** | `GraphMethodNodeCodeTest.shouldMakeKotlinThatCompilesAndDoesWhatTheInterpreterDoes` passes in the first run of this phase. The 10-nov report saw it fail; the uncommitted graph and generator work in the tree appears to have fixed it. No code changed for it. |
| 3 | The two `SampleGamesTest` shooter failures from 10-nov | **Nothing to fix now** | `shouldFireAtEnemiesInTheShooter` and `shouldPlayEverySampleTheSameInTheGameRuntimeAsInEditorPlayMode` pass |
| 4 | Duplicate keeps all components | **Met** (found worse than reported) | §3.1 |
| 5 | Scene save and load round trip | **Met for the 36 samples** | §3.2 |
| 6 | Play isolation, undo and redo for the newest components | **Not met** | Undo and redo for Duplicate fixed and tested; Play isolation and undo for the other newer components were not re-examined in this pass |
| 7 | File > Open understands non-scene files, or says clearly it opens scenes only | **Met (says so)** | §3.3; opening them in their own editor is not done |
| 8 | Acceptance project: a person builds a game end to end and records what they saw | **Not met** | §5: checklist written; nothing observed, because no person has run it |
| 9 | INDEX and the feature matrix reconciled with dated results | **Partly met** | `FEATURE_TEST_MATRIX.md` has a new R0 section and two counts updated; `INDEX.md` links the roadmap. Older rows with old status words were not rewritten |

## 2. Commands and results

All run from the repository root on Windows (`gradlew.bat`).

| Command | Exit | Result |
|---|---|---|
| `./gradlew test --continue` (before any R0 change) | 0 | 1,703 tests, 0 failures, 1 skipped |
| `./gradlew test --continue` (after) | 0 | 1,707 tests, 0 failures, 1 skipped |
| `./gradlew :editor-desktop:test` (after adding the save-and-reopen test) | 0 | 98 tests, 0 failures; whole repo is therefore 1,708 (+5 over the start, §4). The other modules were not re-run after that last test. |
| `./gradlew :editor-desktop:run --args="--smoke-test"` | 0 | "Smoke test rendered 180 frames, 0 error(s) in the Output Log" |
| `./gradlew :engine-desktop:run --args="--scene samples/hopper-3d.json --smoke-test=240"` | 0 | "240 of 240 frames, 0 runtime error(s)" |

Skipped test: `GameExporterTest.shouldKeepTheLauncherExecutableOnALinuxImage`, a JUnit assumption that does not hold on
Windows. Pre-existing, not a regression.

Per-module counts (tests, failures); only the two marked columns changed:

| Module | Before | After | Module | Before | After |
|---|---|---|---|---|---|
| editor-animation | 17 | 17 | engine-animation | 32 | 32 |
| editor-audio | 57 | 57 | engine-audio | 222 | 222 |
| editor-character | 10 | 10 | engine-character | 19 | 19 |
| **editor-core** | 385 (1 skipped) | **388** (1 skipped) | engine-core | 36 | 36 |
| **editor-desktop** | 96 | **98** | engine-desktop | 92 | 92 |
| editor-sculpt | 56 | 56 | engine-physics | 53 | 53 |
| editor-sprite | 51 | 51 | engine-render | 119 | 119 |
| editor-visualscript | 89 | 89 | engine-scene | 149 | 149 |
| engine-sculpt | 81 | 81 | engine-sprite | 44 | 44 |
| engine-visualscript | 86 | 86 | engine-web | 9 | 9 |

All failures: 0. The "before" run already contained the `SONG` asset type test added on 2026-11-12. Gradle reuses a module's
test result when its inputs are unchanged, so some "before" counts are from an earlier run of identical code.

## 3. What was found and changed

### 3.1 Duplicate lost components, and undo then redo lost them all

`DuplicateEntityCommand` copied components with a hard-coded list of eight types. Everything else (models, lights, 3D rigid
bodies and colliders, graph scripts, audio, cameras, text) was silently dropped. Also, `Scene.removeEntity` detaches all
components, so after **undo then redo** a Duplicate brought back an entity with **no components at all**, for every type.

Fix (`editor-core/.../command/DuplicateEntityCommand.java`): components are copied through the scene serializer
(`SceneSerializer.entityToJsonValue` and `readComponent`, the path the prefab system already uses), so any component with a
serializer is copied with all its saved fields; undo remembers the components and redo re-attaches them (the pattern
`CreateEntityCommand` already uses). A component with no serializer cannot be copied; the status bar says how many were left out.

Tests: `DuplicateEntityCommandTest` (now 4): copies model, light, rigid body and collider with their fields; undo and redo keep
them; the "not copied" message. The existing test needed a registry that knows the shape component, since copying now uses
serializers; `new EditorState()` has an empty registry, as the real editor does not.
`DuplicateSampleEntitiesTest.shouldCopyEveryComponentOfEveryEntityInEverySampleScene` duplicates all **1,471 entities** of the
36 sample scenes with the editor's full serializer list and compares saved state. It found one thing: a directional light's
direction is normalized again every time it is read, so its last digit moves (for example -0.39581951 to -0.39581957, about
1e-7). That is not data loss; the comparison allows 1e-5 on numbers. The test was written before the fix and I did not run it
against the old code, so I cannot state that this exact test failed beforehand. The first `DuplicateEntityCommandTest` model
test did fail against the old code (run before the fix).

### 3.2 Save and reopen

`DuplicateSampleEntitiesTest.shouldKeepEveryEntityAndComponentWhenEverySampleSceneIsSavedAndOpenedAgain` loads each sample,
saves it to JSON, loads that, and compares entity count and every entity's saved components. It passes for all 36 samples.
It covers the scene file format, not file dialogs or the editor's File menu.

### 3.3 File > Open and asset files

Opening `demo.song` through File > Open reached the scene parser and showed "Missing or invalid schemaVersion in scene JSON:
-1". `SceneFileController.openFile` now checks the asset type first and says "<name> is a song file, not a scene. Double-click
it in the Content Browser to open it in its editor." Test: `SceneFileControllerTest.shouldSayAnAssetFileIsNotASceneInsteadOfShowingASchemaError`.
This test was written together with the fix and I did not run it against the old code; the old message contained "schemaVersion",
which the test asserts is absent, so it would have failed.

Earlier on 2026-11-12 (same session, before R0): `.song` was not a known asset type, so the Content Browser never listed songs.
`AssetType.SONG` was added, the Content Browser opens a song on double-click (`EditorApplication.openSong`), and
`AssetTypeTest` covers the type. **That double-click was not seen working by a person.**

## 4. Files changed in R0 (this session only)

- `editor-core/src/main/java/.../command/DuplicateEntityCommand.java` (rewritten copy and undo/redo)
- `editor-core/src/main/java/.../file/SceneFileController.java` (asset-type guard in `openFile`)
- `editor-core/src/test/java/.../command/DuplicateEntityCommandTest.java` (+2 tests, new setup)
- `editor-core/src/test/java/.../file/SceneFileControllerTest.java` (+1 test)
- `editor-desktop/src/test/java/.../DuplicateSampleEntitiesTest.java` (new, 2 tests)
- Earlier the same day: `engine-core/.../asset/AssetType.java` (+`SONG`), `AssetTypeTest` (+1), `editor-desktop/.../EditorApplication.java` (`openSong`)
- Docs: `docs/13-nov-forgeroom-roadmap.md`, `docs/INDEX.md` (link), `docs/FEATURE_TEST_MATRIX.md` (R0 section, two counts), this report.
- Unrelated and not part of R0: `docs/wendy-house-instalment-sale-agreement.pdf` (a personal request).

## 5. Acceptance project: for a person to run and fill in

Not run. A headless agent cannot see the screen, so this stays **Manually Not Verified (Headless Environment)** until someone
records what happened. Start with `./gradlew :editor-desktop:run`. For each step write what you saw; write "failed" and what
happened if it did not work. Keep screenshots in `docs/reports/img/r0-*`.

| # | Step | What to do | Expected | What I saw |
|---|---|---|---|---|
| 1 | New project scene | File > New, choose 3D | Empty scene, viewport shows a grid | |
| 2 | Add content | Create menu: Cube, Sphere, Sun light, Camera | Four entities in the Outliner, visible in the viewport | |
| 3 | Import a model | Drag a `.gltf` from the Content Browser into the viewport | An entity with a Model appears at the drop point | |
| 4 | Duplicate (R0 fix) | Select the model entity, Duplicate; then Undo, Redo | The copy has the model (and any physics) and keeps it after Redo | |
| 5 | Physics | Add Rigid Body 3D and a Box Collider 3D to the sphere; press Play | The sphere falls and lands | |
| 6 | Logic | Add a Graph Script to the cube; open it; connect nodes (for example rotate every frame) | Graph tab opens; Play rotates the cube | |
| 7 | Animation | Open the Animations tab; add a clip with two keys on the cube's position | Key diamonds appear; scrubbing moves the cube | |
| 8 | Audio | Open the Music tab, open `music/demo.song` from the Content Browser (R0 fix) and press Play | The song opens in the Music tab; you hear it | |
| 9 | File > Open guard (R0 fix) | File > Open, choose `demo.song` | A clear message that it is a song, not a scene | |
| 10 | Save and reopen | Save As, close the editor, reopen, File > Open | The same scene returns with all entities and components | |
| 11 | Play isolation | Press Play, move things, Stop | The scene returns exactly as before Play | |
| 12 | Export | File > Export Game | A game folder is produced without errors | |
| 13 | Launch export | Run the exported game | It starts and plays the scene | |

Anything that fails becomes an item for the next pass before R1 starts.

## 6. Known limitations and next steps

- The acceptance project (§5) is the one open R0 deliverable. Without it R0 is **not done**; with it recorded, R1 can start.
- Smoke runs prove the editor and game render frames without logged errors, not that the frames look right. No screenshot was taken.
- Duplicate copies only components that have a serializer; any future component type needs one (the scene file format already requires it).
- File > Open still refuses non-scene files rather than opening them in their editor.
- A `.song` still cannot be dragged into the viewport.
- Play isolation and undo/redo for the newer components, listed in the roadmap, were not re-examined.
- The 10-nov status items on documentation reconciliation (replacing old "complete" claims in older reports) were not done.

## 7. Git

No commit, push, stash, reset, branch change or other destructive Git operation was performed. HEAD is `d896331` before and after.

## 8. Addendum, 2026-11-14: what later milestones closed

This report is kept as written. These items from it were closed or changed afterwards (milestones M0.1 to M0.5 in [milestone-chart.md](../milestone-chart.md)):

| This report said | Now |
|---|---|
| Criterion 6: Play isolation, undo and redo for the other newer components "not met" | Covered by `ComponentConsistencyTest` over every addable component ([M0.2](m0-2-data-consistency-report.md)); it also found and fixed a Play crash and the light-direction drift |
| Criterion 7 and §6: "File > Open still refuses non-scene files rather than opening them in their editor" | A song, graph, clip and sprite open in their own editor; the rest are refused accurately ([M0.3](m0-3-file-open-by-type-report.md)) |
| Criterion 8 and §5: the acceptance project "not run" | Run by program on 2026-11-14 and observed (240 frames, 0 errors in the runtime, the editor's Play mode and the exported game); still **not run by a person at the keyboard** ([M0.4](m0-4-acceptance-project-report.md)) |
| §3.1: a light's direction moves in its last digit on copy, so the sample-duplication test allows 1e-5 | Fixed at the source in M0.2; the test now compares exactly |
| Criterion 9: documents "partly met" | Reconciled in [M0.5](m0-5-reconciliation-report.md) |
| Test totals of 1,708 | 1,869 on 2026-11-14 |
