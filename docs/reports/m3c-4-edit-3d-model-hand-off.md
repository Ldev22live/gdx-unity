# Roadmap M3c.4: Edit 3D Model hand-off (2026-11-16)

Milestone M3c.4 of [milestone-chart.md](../milestone-chart.md) (roadmap R3c). **Status:**
- **Implemented and Integrated.** Customize > **Edit 3D Model** opens the 3D Modeling workspace on a selected model or shape, in context, with a capability notice.
- **Seen in the running editor** at 1920x991 and 1024x640 through a testing aid (screenshots below).
- **Manually Not Verified (Headless Environment):** no person clicked Customize or read the notice.

- Branch: `phase-12-3d-foundation`
- Starting `git rev-parse --short HEAD`: `0469ceb`. Ending: `0469ceb` (no commit was made).
- At the start, the working tree held this session's uncommitted M3.3 to M3c.3 work (listed in their reports).
- No new module, dependency or technical decision. The milestone's gate is "none".

**Design choices (mine, per the standing instruction to decide open details):**
- **Edit 3D Model never makes a copy.** It opens 3D Modeling on the object itself and explains what can be done. The existing "Edit X in 3D (sculpt a copy)" stays a separate entry, because making a project-owned copy is a choice the person should make on purpose.
- **The label says its limit:** "Edit 3D Model of House (opens 3D Modeling; mesh editing is planned)". The roadmap says to offer Edit 3D Model "only when supported, otherwise a clear explanation", so the entry is honest before it is clicked, and the notice explains the rest after.
- **"In context"** means: the World's view turns to 3D, 3D Modeling opens, the camera frames the object with the world around it, and the selection, scene and undo history are untouched. Back to World turns a 2D view back.

## 1. Acceptance criteria

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | Selecting a house opens the 3D Modeling workspace in context | **Met** | `WorldFirstNavigationTest.shouldOpen3DModelingOnTheSelectedHouseInContextAndComeBackToTheSameWorld`: 3D Modeling on screen, 3D view, framed once, house still selected, no undo step, no copy made. Seen in the editor on the landscape sample's Cabin (screenshots) |
| 2 | A clear capability notice until R7a is ready | **Met** | `ModelingHandOff`: "Mesh editing is not available yet", then why (R7a, and that the glTF file is unchanged), what a sculpt copy does, that Move, Rotate or Scale never edits the mesh, and where the material is edited (or that a glTF model has no slot). Shown in the view while 3D Modeling is on screen (`ModelingNoticePanel`); its first line also goes to the status bar. `ModelingHandOffTest` (4) |
| 3 | Transform-only editing is never presented as mesh editing | **Met** | No notice ever has `meshEditing` true, and every Move line says "never its mesh" (`ModelingHandOffTest`). Over every entity of every shipped sample, each model or shape gets Edit 3D Model first and a notice that never claims mesh editing (`CustomizeGatewaySamplesTest`). "Edit Mesh Geometry" stays unavailable (`CustomizeGatewayTest`) |
| 4 | Done when: opens the workspace or explains the limit | **Met** | Opens it for a model or shape; refuses with the reason during Play, for an object with no mesh, or when 3D Modeling cannot open (`WorldFirstNavigationTest`, 2 refusal tests) |
| 5 | Agent parity (chart §10 rule 8) | **Met** | Read-only tool `model_edit_check` reports the same facts as machine-readable outputs; it runs as a plan step from the default tools without an undo step (`ModelEditCheckToolTest`, 4) |
| 6 | UX gate (Appendix E5) | **Partly** | Seen at both window sizes; at 1024x640 a defect was found and fixed (section 4). Not done: a person clicking through it |

## 2. What was built

- **`editor-core`:**
  - `workspace/ModelingHandOff`: the capability per selection (sculpt, mesh planned, no mesh, no selection), the notice's title and sentences, and the Customize label.
  - `CustomizeGateway`: a new `Kind.OPEN_MODELING` entry, first for a glTF model or a shape.
  - `WorkspaceNavigator.editModel`: World tab, 3D view, sculpt mode (3D Modeling), frame, status line. Leaving 3D Modeling through Back to World or another workspace turns a 2D view back. If the person left 3D Modeling another way (the Sculpt toggle), their view is kept. `View` has a new `frameSelection()`.
  - `agent/tools/ModelEditCheckTool` (`model_edit_check`), registered in `DefaultAgentTools`.
- **`editor-desktop`:**
  - `ui/viewport/ModelingNoticePanel`: under the brush strip while 3D Modeling is on screen. It is hidden for a sculpt, during Play and in World, takes no room when hidden, and is rebuilt only when the selection changes.
  - `EditorApplication`: runs the new entry; `frameSelection` uses the Frame button's code; new testing aid `GDXUNITY_EDITOR_EDIT_MODEL=1`, which waits 10 frames.
  - `ui/EditorStatusBar`: long messages end in an ellipsis (section 4).
- **Docs:** chart (M3c.4 row, R3c line), `INDEX.md`, `FEATURE_TEST_MATRIX.md`, `AI_AGENT_ARCHITECTURE_AUDIT.md` (tool table), `AGENTS.md` (the new testing aid, beside the other TD-054 aids).

## 3. Commands and results

| Command | Exit | Result |
|---|---|---|
| `./gradlew :editor-core:test --tests "*ModelingHandOffTest" --tests "*ModelEditCheckToolTest" --tests "*WorldFirstNavigationTest" --tests "*CustomizeGatewayTest" --tests "*PlayToolsTest" --tests "*AgentPlan*" :editor-desktop:compileJava` | 1, then 0 | 40 of 41 passed first. My Play-refusal check could not start Play with a house whose model file does not exist; I moved it to its own test with a house outside the scene |
| Mutation: remove the view restore from Back to World | 1 | 1 test failed, as intended; restored |
| `./gradlew :editor-desktop:test` | 0 | 160 |
| `GDXUNITY_SCREENSHOT=... GDXUNITY_EDITOR_ASSISTANT=0 GDXUNITY_EDITOR_SELECT=Cabin GDXUNITY_EDITOR_EDIT_MODEL=1 ./gradlew :editor-desktop:run --args="--scene samples/landscape-3d.json --smoke-test"` (plus `GDXUNITY_EDITOR_WINDOW=1024x640`) | 0 each | 180 frames, 0 errors each |
| `./gradlew :editor-core:cleanTest :editor-desktop:cleanTest test --continue` | 1 | Every module passed except `editor-core`: 1 failure, a new test whose fix landed while the run was compiling (below) |
| `./gradlew :editor-core:cleanTest :editor-core:test :editor-desktop:cleanTest :editor-desktop:test --continue` | 0 | 509 (1 skipped, as before), 160 |

| Module | Before (M3c.3) | After |
|---|---|---|
| `editor-core` | 497 (1 skipped) | 509 (1 skipped) |
| `editor-desktop` | 160 | 160 (one test extended) |
| All others (full run) | unchanged | all pass: engine-scene 182, engine-render 167, engine-desktop 113, editor-terrain 24 and the rest |

**Screenshots** (looked at):
- [m3c-4/editor-edit-3d-model-cabin.png](m3c-4/editor-edit-3d-model-cabin.png), 1920x991: the header reads "3D Modeling / Untitled". The Cabin is framed among the trees with its gizmo, and the brush strip has the notice under it. The footer shows Back to World beside Customize. The status line gives the notice's first sentence.
- [m3c-4/editor-edit-3d-model-cabin-1024.png](m3c-4/editor-edit-3d-model-cabin-1024.png), 1024x640: the same, after the fix in section 4. The notice covers the top left of the view, but the cabin is still in sight.

## 4. Failures found and fixed

- **Testing-aid timing.** On the first frame the framing had no visible effect: the screenshot matched the plain World view. Run after 10 frames, it frames correctly. A person clicks Customize long after the first frame, so only the aid was changed.
- **A long status message widened the whole editor past a small window.** At 1024x640 the whole layout was cropped on both sides ("oom" for "Forgeroom"). Hiding my notice did not change it, and neither did shortening the tool row's info text (that change was reverted). The cause was the status label: its minimum width was the full message. It now ends in an ellipsis; the Output Log keeps the full text. This affected any long status message, not only this milestone's.
- **An edge case in my own navigator code:** leaving 3D Modeling with the Sculpt toggle would have turned the view back to 2D on a later, unrelated Back to World. It now restores only when 3D Modeling itself is left, with a test. The full run had picked up this test before the fix, which is the one failure in the table above.

## 5. Diff review

- **New:**
  - `ModelingHandOff`, `ModelEditCheckTool`, `ModelingNoticePanel`;
  - tests `ModelingHandOffTest`, `ModelEditCheckToolTest`;
  - this report and `docs/reports/m3c-4/`.
- **Changed:**
  - `CustomizeGateway`, `WorkspaceNavigator`, `DefaultAgentTools`, `EditorApplication`, `EditorStatusBar`;
  - tests `CustomizeGatewayTest`, `WorldFirstNavigationTest`, `CustomizeGatewaySamplesTest`;
  - the docs listed in section 2.
- No files outside this repository were touched. Line endings kept: CRLF files stayed CRLF, and LF files stayed LF (one script briefly wrote CRLF into `WorkspaceNavigator.java`; reverted to LF and checked).

## 6. Known limitations and next steps

- **No mesh editing:** that is R7a. The full house-edit replay is M3c.6.
- **The camera stays framed on the object** after Back to World. The view mode is restored, but the camera position is not.
- **At 1024x640** these were already known before this milestone:
  - the notice covers part of the view;
  - the tool row is still cut off at "Rot";
  - the footer's left side scrolls under Back to World.
- **No house in the Buildings palette** (M2b.3). The check used the landscape sample's Cabin.
- **Manual check for a person:**
  1. Open the Landscape sample and select the Cabin.
  2. Customize > Edit 3D Model; read the notice.
  3. Back to World.
  4. Repeat from a 2.5D scene; the view should return to the side view.
- **Next in R3c:** M3c.5, the regression project and human checklist for the whole journey.

## 7. Git

No stage, commit, push, reset, stash, branch switch or other destructive Git operation was performed.
