# Roadmap M2.2: Material Slot and Drag and Drop (2026-11-15)

Milestone M2.2 of [milestone-chart.md](../milestone-chart.md) (roadmap R2,
[ForgeRoom_Master_Roadmap_2026-11-15_Desktop_Web_Scoped.md](../ForgeRoom_Master_Roadmap_2026-11-15_Desktop_Web_Scoped.md)).
**Status: Implemented and Integrated. Manually Not Verified (Headless Environment): nobody dragged a material in the running editor.**

- Branch: `phase-12-3d-foundation`
- Starting `git rev-parse --short HEAD`: `d0ca552`. Ending: `d0ca552` (no commit was made).
- The working tree was clean at the start. The M2.2 code was written earlier in this session, before the R1b reconciliation, and
  the owner committed it in `d0ca552`. This part adds the report, the documentation rows and one small test cleanup.
- Approvals: the chart lists none for M2.2. No new module, dependency or decision record.

## 1. Acceptance criteria

| # | Criterion (chart M2.2) | Result | Evidence |
|---|---|---|---|
| 1 | Details material slot | **Met** | The Mesh Primitive Details has a Material section (File, Use Selected Material, New Material, No Material). Its file row now goes through `MaterialAssignment`. The section was seen on screen (section 3), but not used |
| 2 | Drag from the Content Browser | **Met in code and tests; Manually Not Verified** | A material dropped on the scene view goes on the shape under the mouse (`ViewportInteraction.entityAt`). In the multi-pane view, or on Details, it goes on the selection. No drag was done in the running editor |
| 3 | Done when: a dropped material applies and undoes | **Met in automated tests** | `MaterialAssignmentTest.shouldApplyAMaterialInOneUndoStepThatUndoesAndRedoes`, `MaterialAssignToolTest.shouldApplyTheMaterialAsAPlanStepThatUndoesInOneStep`, and the `entityAt` tests that find the shape under a drop |

## 2. What was done

- **`MaterialAssignment`** (editor-core, `material`) is the one way to change which material a surface uses. Details, the three
  drop targets and the agent tool all use it.
  - A change is one `ModifyComponentPropertyCommand`, named "Apply Material Rock.material to Box" or "Remove Material from Box".
  - Putting on the same material again adds no undo step.
  - A blank path removes the material, and the shape's own colour is used again.
  - These are refused with the reason in the status line, and nothing changes: a file that is not `.material`, a missing or
    unreadable file, no entity under the drop, an entity with no slot, and any change during Play.
- **`ViewportInteraction.entityAt(x, y, width, height)`** returns the entity under a point and leaves the selection alone.
  - The 2D, 3D, orthographic and mode-switching views answer.
  - The sculpt, level, terrain and tile-painting wrappers pass it on.
  - The default is "none", never a guess.
- **Drop targets in `EditorApplication`:**
  - the scene view applies a material to the shape under the mouse; other assets are placed as before;
  - the multi-pane view applies it to the selection, because no single pane is under the mouse;
  - Details applies it to the selected entity.
- **Agent tool `material_assign`** (`MaterialAssignTool`; parameters `entityId` and `materialPath`). It provides `createCommand`,
  so in a plan it undoes as part of the plan's step. It is registered in `EditorApplication`.
- **This part:** two viewport tests used `java.util.Optional` inline, which AGENTS.md section 6 forbids. They now import it.

## 3. Commands and results

| Command | Exit | Result |
|---|---|---|
| `./gradlew cleanTest test --continue` | 0 | **1,975 tests, 0 failures, 1 skipped** (the known `GameExporterTest` Linux assumption), 23 modules |
| `./gradlew :editor-core:test --tests "*SceneViewportInteraction2DTest" --tests "*SceneViewportInteraction3DTest" --tests "*OrthographicViewTest" --tests "*MaterialAssign*" --tests "*MaterialEditingTest"` (after the import cleanup) | 0 | 8, 11, 10, 7, 4 and 5 tests, 0 failures |
| `GDXUNITY_SCREENSHOT=... GDXUNITY_EDITOR_SELECT="Rock Block" GDXUNITY_EDITOR_ASSISTANT=0 ./gradlew :editor-desktop:run --args="--scene samples/materials-3d.json --smoke-test"` | 0 | "Smoke test rendered 180 frames, 0 error(s) in the Output Log" |

- **First attempt at the editor run (exit 1):** an absolute Git Bash path (`/c/...`) was taken as relative to the project, giving
  "File not found". This was a mistake in the command, not in the editor; the project-relative path above worked.
- **Per module:** `editor-core` 473 (1 skipped), `editor-desktop` 158, `engine-render` 144, `engine-material` 15. The other modules
  are the same as in the R1b reconciliation report. Both the M2.2 code and the R1b work were in place when the 1,975 count was
  taken in that report, so this part adds no new tests; it only changes two existing ones.
- **Screenshot:** [m2-2/rock-block-details.png](m2-2/rock-block-details.png). It shows Rock Block selected, with the gizmo on it.
  The Details Material section shows File `materials/Rock.material`, the three buttons, the note "Shared by every surface that uses
  materials/Rock.material", and the material's values. The status line says the sample opened as a copy. This shows the slot is on
  screen; it does not show a drop.

## 4. Failures

There were no test failures. The only failed command was the editor run with the wrong path described in section 3.

## 5. Diff review

| File | Change |
|---|---|
| `docs/reports/m2-2-material-slot-drag-and-drop.md`, `docs/reports/m2-2/rock-block-details.png` | New: this report and its screenshot |
| `docs/milestone-chart.md` | M2.2 status updated. M2.1 status records the glTF gap |
| `docs/FEATURE_TEST_MATRIX.md` | New M2.2 row. The R2 materials row no longer says "no agent tools for materials" |
| `docs/INDEX.md` | New M2.2 entry |
| `SceneViewportInteraction2DTest.java`, `SceneViewportInteraction3DTest.java` | Import `java.util.Optional` instead of writing it inline. No behaviour change |

No other files were changed. Line endings were kept: CRLF in the docs, as before.

## 6. Known limitations and next steps

- **glTF models have no material slot.** Only Mesh Primitives do. Dropping a material on a model, a sculpt or a sprite is refused
  with that reason. The M2.1 row promised "primitives and glTF models", so that part of M2.1 is not met. It is recorded in the
  chart's M2.1 row. It needs a per-model material override in `engine-render` and its serializer, which is a change of its own.
- In the multi-pane view, a dropped material goes on the selection, not on the pane under the mouse.
- While dragging, nothing highlights the shape a material will go on.
- **Manual check for a person:**
  1. Open `samples/materials-3d.json`.
  2. Drag `materials/Gold.material` from the Content Browser onto Plain Ball. The ball turns gold and the change is one undo step.
  3. Press Ctrl+Z. The ball returns to its own colour.
  4. Drag a material onto Details with Pond selected; it applies to Pond.
  5. Drag one onto the Help Text; the status line says it has no material slot.
- **Next, per the chart:** M2.5 (web player reads materials, no gate) or the gated M2.3 (Material Editor, TD) and M3.3 (terrain
  collision, TD). None is started.

## 7. Git

No stage, commit, push, reset, stash, branch switch or other destructive Git operation was performed.
