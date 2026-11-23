# M0.2 Data-consistency gaps: report

**Milestone:** M0.2 in [milestone-chart.md](../milestone-chart.md) (roadmap phase R0, "Trustworthy baseline").
**Date:** 2026-11-14. **Platform:** Windows 11 Pro, Java 21.
**Starting and ending `git rev-parse --short HEAD`:** `d896331` (unchanged). The working tree already held many uncommitted changes from earlier work; the files this milestone touched are listed below.

## Relationship to the earlier R0 pass

The earlier R0 pass ([r0-baseline-report.md](r0-baseline-report.md), 2026-11-13) is what fixed `DuplicateEntityCommand` (so the Duplicate gap was already closed here) and added a test duplicating all 1,471 sample entities, which tolerated a 1e-5 difference because of the light-direction drift. That report listed Play isolation and undo and redo for the newer components as not examined; this milestone examines them. The drift it tolerated is now fixed and that test compares exactly.

## Done-when and result

The chart's done-when for M0.2 is: *each has a test that fails without the fix.* The four items were Duplicate component, save and load round trip, Play isolation, and undo and redo for the newest components.

**Met headless, with the limits listed below.** Two real defects were found and fixed, each with a test that failed before the fix. The other two items already worked, and now have a test that covers every component the editor can add.

## What was found, item by item

| Item | Finding | Test, and did it fail first? |
|---|---|---|
| **Duplicate component** | **Already fixed before this milestone.** The 10 Nov status said `DuplicateEntityCommand` copied only a fixed `instanceof` list and dropped models, graph scripts and 3D bodies. The working tree's version copies every component through the scene serializer, with tests (an uncommitted change from earlier work). No further bug was found. | `ComponentConsistencyTest.shouldCopyEveryAddableComponentUnchangedWhenTheEntityIsDuplicated` passes for every catalog component. I did not see this one fail, because there was no gap left to fail on. |
| **Save and load round trip** | **Defect, fixed.** A Directional Light's direction changed by one unit in the last digit on every save and reopen (`-0.8944272` read back as `-0.8944273`), because the setter normalised an already-unit vector again and float arithmetic is not exact. A scene file therefore differed after a save and reopen with no edit. Fix: `DirectionalLightComponent` keeps a direction that is already unit length exactly and normalises only one that is not. | `ComponentConsistencyTest.shouldKeepEveryAddableComponentUnchangedThroughSaveAndLoad` **failed before the fix** (Directional Light). Also `LightSerializationTest.shouldWriteTheSameLightDirectionAfterEverySaveAndLoad` and `shouldKeepAUnitDirectionExactlyAndNormaliseOneThatIsNot`. |
| **Play isolation** | **Defect, fixed.** A scene that fails on its first simulated step (found with a Joint 3D that has no collider on its entity) threw out of `PlayModeController.update`. In the editor that call is in the frame loop with no handler, so the exception reaches `EditorLauncher`'s top-level crash handler and the editor stops, losing unsaved work. Play now stops itself, puts the authored scene back, and shows "Play stopped:" with the reason, for example "Loose Joint has the joint but has no solid 3D collider. Add a Box, Sphere or Capsule Collider 3D (not a trigger)." The authored scene was otherwise unchanged by Play for every component tested. | `PlayModeControllerTest.shouldStopPlayWithTheReasonInsteadOfThrowingWhenTheSceneFailsOnItsFirstStep` and `ComponentConsistencyTest.shouldLeaveTheAuthoredSceneIdenticalAfterPlayAndStopWithEveryAddableComponentInIt` (a scene with every addable component, 30 frames of Play, then Stop; the saved scene is identical, and no runtime component object is shared with the authored scene). The Joint failure **was observed before the fix**. |
| **Undo and redo, newest components** | **No defect found.** Adding any catalog component undoes and redoes with the same saved state. | `ComponentConsistencyTest.shouldUndoAndRedoAddingEveryAddableComponent` passes. No failing run to show. |

## Why the new test is registry-driven

`ComponentConsistencyTest` (in `editor-desktop`, where every module is visible) takes its list from `ComponentCatalog.createDefault()` and the serializers the editor and game runtime share (`GameComponentSerializers.registerAll`). It has no hand-written component list. A component added to the catalog later is covered at once, and one with no serializer fails `shouldHaveASerializerForEveryComponentTheEditorCanAdd` instead of being silently dropped by save, Duplicate or Play, which is how Duplicate originally broke.

## Commands and exit codes

| Command | Exit | Result |
|---|---|---|
| `./gradlew :editor-desktop:test --tests "*ComponentConsistencyTest"` (first run, before fixes) | 1 | 4 of 5 failed: a Model setup gap in the test (it needs a selected asset), the Directional Light drift (a real defect), and then Play stopping on a hull collider with no geometry source and on the loose joint |
| Same, after the light fix and test setup | 1 | 1 of 5 failed: the loose-joint Play crash (a real defect) |
| `./gradlew :editor-core:test --tests "*PlayModeControllerTest"` and the consistency test, after the Play fix | 0 after correcting one assertion of mine | 18 of 18 and 5 of 5 |
| `./gradlew cleanTest test --continue --console=plain` (everything, from scratch) | 0 | **1,857 tests, 0 failures, 0 errors, 1 skipped**, 23 modules |

Counts before and after, by module (only the three that changed): editor-core 396 to 397 (+1), editor-desktop 116 to 121 (+5), engine-scene 159 to 161 (+2). Every other module is as in the [M0.1 report](m0-1-test-baseline-report.md). The one skip is unchanged: the POSIX-only exporter test, which runs on Linux or macOS.

A hull collider failed in the first Play run because the test had no renderer. That is not an editor defect: in the editor, `SceneRenderer3D.bindColliderGeometry()` gives the playing scene its geometry source when the scene changes (`EditorApplication` registers that listener). The test now does the same with a small Play extension.

## Files changed

- `engine-scene/.../lighting/DirectionalLightComponent.java` (keep an exact unit direction)
- `editor-core/.../play/PlayModeController.java` (`update` stops Play with the reason when the simulation throws)
- Tests: `ComponentConsistencyTest` (new, `editor-desktop/.../audit/`), `PlayModeControllerTest` (+1), `LightSerializationTest` (+2)
- Docs: this report, `docs/milestone-chart.md`, `docs/INDEX.md`

## Limits of this evidence

- **Default values only.** The registry-driven test adds each component with the catalog's default values. A serializer that drops a field only when it is not at its default would pass. The existing per-component tests cover specific components with non-default values; there is no generic non-default test yet.
- **Catalog components only.** Components that cannot be added from the catalog (for example prefab links) are not covered by this test.
- **Property edits and asset reload.** Undo and redo of *editing a value* on the newest components is not checked generically (Terrain strokes and Material fields have their own tests), and the 10 Nov status's "asset reload" item was not examined.
- **Headless.** The "Play stopped" message was produced in a test, not seen in the running editor. It still stops on the first failing step only; a script that throws later does the same (the same catch), but that was not separately tested.
- **Not examined:** M0.3 (File > Open by type) and the acceptance project (M0.4).

## Acceptance criteria (AGENTS.md §10)

1. Criteria listed and marked: done above.
2. Exact commands, exit codes and per-module counts: done above.
3. New failures investigated: each of the four first-run failures was traced to a cause (two real defects, two test-environment gaps).
4. Diff reviewed, no unrelated changes: only the files above.
5. Matrix and INDEX updated: INDEX and the chart updated. `docs/FEATURE_TEST_MATRIX.md` was not changed in this milestone, which it should be when the cross-component test is listed; that is open.
6. Limits and next steps: above. Next is M0.3.
7. **No commit, push or other destructive Git operation was performed.**
