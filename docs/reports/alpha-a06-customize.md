# A06 Customize and return to World

**The audit is complete, and the one gap that needed code is closed:** unsaved mesh edits were silently discarded on leaving 3D Modeling. A06 is otherwise covered by earlier milestones (M1b.5, M1b.7, M3c.4, M7a.1–6, MC.2, MC.3), whose tests are listed below. **Not validated:** the human edit loop (house wall or roof, then sprite frame, return and replay) is pending. No new TD was needed: the fix enforces the approved TD-084 ("navigating never touches unsaved changes").

The starting HEAD was `85ccc0a`, with A04 and A05 uncommitted in the working tree (see their reports).

## Audit against the roadmap

| A06 requirement | Where it stands | Evidence |
|---|---|---|
| Customize and Play adjacent and always visible | Met. The World footer has Customize beside Play (TD-107). | A02 report and captures |
| Selection-aware chooser: house → 3D Modeling, sprite → 2D Studio, character → animation/logic | Met through `CustomizeGateway.editSelected`. Edit 3D Model for models and shapes; Edit 2D Asset for sprites; Animate for `.anim`; Edit Logic for `.graph`; materials in Details; terrain tools. A file type with no editor is listed as unavailable, with the reason. | `CustomizeGatewayTest`, `CustomizeGatewaySamplesTest` |
| Entry without a selection | Met. `CustomizeMenu` lists the workspaces when nothing is selected. | `WorldFirstNavigationTest` |
| Back to World restores project, camera, selection and pending edits; no silent discard or commit | **Gap found and fixed (below).** Project, selection, undo history and the World camera were already restored (`WorkspaceNavigator`, M7a.5 camera capture). Unsaved mesh drafts were not. | `WorldFirstNavigationTest`, new `MeshDraftKeeperTest` |
| Vertex, edge and face editing; supported operations | Met (M7a.3): six operations with undo and redo. | `MeshPartEditorTest`; Windows graphics smoke (M7a.3 report) |
| Three save modes; project-owned derived model by default; explicit shared update; unique instance | Met (M7a.4, MC.2): New, Unique, Edit Shared Prefab (warning with the instance count) and Update (confirmed). Provider originals are protected. | `MeshSaveSessionTest`, `HouseSliceTest` |
| Rebuild render and collision; verify an actual geometry change | Met (M7a.5): the changed house is drawn in World and Play, and the extruded face is hit after reopening. | `ModelReturnGraphicsSmoke` (Windows GL and Jolt) |
| Tests: navigation, mesh and sprite edits, undo/redo, shared vs unique, source immutability, save/reopen, next Play uses the change | Met across `WorldFirstNavigationTest`, `HouseSliceTest` (unique, shared, instance precedence, Play, Stop, undo, reopen, export), `TwoDHouseSliceTest` (2D house roof and window, a character frame, return, play, save, reopen) and `OwnerJourneyRegressionTest`. | |
| Basic material properties of shipped templates editable | Met: material editing in Details is shared by every surface using the file (Phase 13, `CustomizeGateway` "Edit Material ... in Details"). | `MaterialEditingTest`, `MaterialAssignmentTest`, `CustomizeGatewayTest` (Edit Material offer) |
| Human: change a house wall or roof, return and replay; edit a sprite frame and replay | **Pending.** Manually Not Verified (Headless Environment). | |

## The gap: unsaved mesh edits were dropped on leaving 3D Modeling

`ModelingNoticePanel` kept each part's drafts (`MeshSaveSession`) in a local map that it cleared whenever 3D Modeling left the screen (Back to World, the Sculpt toggle) or the selection changed. Edits not yet saved were therefore silently lost.

**Fix:**
- `editor-sculpt/.../MeshDraftKeeper` (new, headless) holds each part's drafts, keyed by entity and model path, for the whole editing session.
  - Coming back to a part continues with the same drafts and their undo history.
  - A part now using another model (after Create Unique Mesh, for example) opens from that file.
  - Only Save (`markSaved`) or an explicit Discard ends the "unsaved" state.
- `ModelingNoticePanel` takes its sessions from the editor's one keeper. It marks a session saved after a successful Save. It has a **Discard** button, which asks for confirmation and then reopens the part from its file. A part with kept edits shows "Unsaved edits kept: <model>".
- Back to World (`EditorApplication.backToWorld`) leaves the edits as they are. When any are unsaved, it says so in the status bar: "Unsaved mesh edits of Wall are kept, not saved: open Customize > Edit 3D Model to Save or Discard them. The World shows the saved model until then."

**Tests:** `MeshDraftKeeperTest` (4), using the real Kenney wall:
- The same drafts and undo history come back after leaving and selecting another part.
- Back to World reports unsaved edits, and nothing is saved behind the person's back.
- Edits are unsaved again after a post-save change.
- A fresh start happens only after Discard.
- A part that switched to a unique model is reopened from that model.

## Known limitations

- Kept drafts belong to the entities of the open scene. Opening another scene leaves them unreachable, and saving one then refuses with "no longer editable in this scene". Quitting the editor does not warn about unsaved mesh edits. Both are candidates for the A09 persistence work.
- Leaving 3D Modeling by the Sculpt toggle keeps the edits but shows no notice; only Back to World does.
- Skinned models (characters) are rejected by the static mesh source (M7a.1), so a character is offered animation and logic, not mesh editing.

## Test evidence

| Command | Exit | Result |
|---|---|---|
| `:editor-sculpt:test --tests "*MeshDraftKeeperTest"` | 0 | 4 tests, 0 failures |
| `:editor-desktop:compileJava :editor-desktop:compileTestJava` | 0 | Compiles, including the two graphics smoke tests that build the panel |
| Full suite | see the [progress report](../progress/progress-reports-26-11-2026.md) | |

No commit, push or destructive Git operation was performed.
