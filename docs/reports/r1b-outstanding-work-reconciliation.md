# R1b World-first UX: outstanding work reconciliation

**Phase:** R1b in [milestone-chart.md](../milestone-chart.md) (M1b.1 to M1b.7). **Date:** 2026-11-15. **Platform:** Windows 11 Pro, Java 21 (Gradle toolchain).
**Source of truth:** [ForgeRoom_Master_Roadmap_2026-11-15_Desktop_Web_Scoped.md](../ForgeRoom_Master_Roadmap_2026-11-15_Desktop_Web_Scoped.md) and the milestone chart. **Decision 19 approved by the owner on 2026-11-15:** Desktop is the complete studio, Web a streamlined creator in UrConnex, compatible project data, no feature parity.
**Starting and ending `git rev-parse --short HEAD`:** `d896331` (unchanged). The working tree already held many uncommitted changes from earlier milestones; the files this work touched are listed in section 2.
**Paused, not part of this report:** M2.2 (material drag and drop) was in progress when this task arrived. Its code and tests are in the tree and pass (listed in section 2 for completeness); its own report is still to be written.

## 1. Current implementation evidence

Status words as in AGENTS.md §9. "Seen" means seen in a screenshot of the running editor, taken by the editor itself (`GDXUNITY_SCREENSHOT`); nothing was clicked by a person.

| Requirement | Category | Evidence |
|---|---|---|
| **M1b.1** Every command has a home | Implemented and tested | [world-first-ux-audit.md](world-first-ux-audit.md); `ActionInventoryTest` (fails when an action lacks a home or the unreachable set changes) |
| **M1b.2** World is the default workspace, viewport dominant | Integrated, seen | Screenshots in section 3: the editor opens on **World / Untitled** with the scene view filling the window; no workspace tabs in the header |
| M1b.2 Terrain, Objects, Buildings, Characters with contextual tools | Integrated, partly | The strip shows the four sections for 2.5D and 3D (Tiles, Sprites, Objects, Characters for 2D). Terrain and Objects open only their tools (`LevelEditingTest`, focus tests); Buildings and Characters say why they are planned (`PanelLayoutTest`, `WorldStrip`). Buildings depends on R7a; Characters has no palette yet |
| M1b.2 Details, Outliner, Content Browser reachable; Details follows the selection; choices persist | Implemented and tested | Always-visible buttons in the tool row (screenshots); `PanelLayoutTest`; `WorldFirstNavigationTest.shouldKeepPanelChoicesThroughSaveAndReloadOfTheLayout` (new) |
| M1b.2 Minimum window | Implemented, seen | `EditorLauncherWindowConfigurationTest` (1024 by 640 minimum); the World at 1024 by 640 in section 3 |
| M1b.2 No command unreachable | Implemented and tested, with a finding | `ActionInventoryTest`. Finding: at 1024 by 640 the right end of the tool row (Level, Compile Scripts, AI Assistant) is cut off. The row scrolls sideways (`ScrollingBar`), and Level is also reached through the strip, but nothing on screen shows that it scrolls |
| M1b.2 Compared with the owner's reference screenshot | **Awaiting owner** | The reference is not in this repository and was not compared |
| **M1b.3** Genre and Mode separate; Customize beside Play | Integrated, seen; **fixed here** | Game Genre reads "none yet (planned)" (no fake selector); Game Mode shows the scene's own 2D, 2.5D or 3D. **Customize was not directly beside Play:** the hidden Back to World button kept its space between them (about 150 pixels). Fixed: Back to World now comes first, so Customize is always next to Play (section 3) |
| M1b.3 Play, Pause, Stop reflect the runtime state | Implemented and tested | `PlayModeControllerTest`; the footer's state badge reads "Stopped" (screenshots) |
| M1b.3 Footer visible at the minimum size | Integrated, seen | Section 3, 1024 by 640, World and Audio & Music |
| M1b.3 2D, 2.5D, 3D settings persist | Implemented and tested | `GameModeTest` (saved mode preferred over inference, mode per scene, refused during Play) |
| M1b.3 Genre selector | **Depends on M3b.4** | No genre registry exists; the label says planned |
| **M1b.4** Selection-aware entries for terrain, prefab, building, character, sprite, audio, nothing | Implemented and tested; **completed here** | `CustomizeGatewayTest.shouldOfferTheRightEditsForEachKindOfSelection` (new) |
| M1b.4 Transform, Edit prefab, Edit material and Edit mesh geometry kept apart | Implemented and tested; **new here** | See section 2. Mesh geometry is always marked unavailable with the R7a reason; it is never offered as available |
| M1b.4 Real mesh editing | **Depends on R7a** | |
| **M1b.5** Automated navigation, selection, undo, panels, Back to World, save and reopen | Implemented and tested; **new here** | `WorldFirstNavigationTest` (6 tests) on `WorkspaceNavigator`, which the editor now uses |
| M1b.5 Keyboard | Implemented and tested (existing) | Shortcuts are registered actions checked by `ActionInventoryTest` and `DefaultEditorActionsTest`; nothing was typed by a person |
| M1b.5 Unfamiliar tester | **Needs a person** | Checklist in section 5. Not validated |
| **M1b.6** Web UX spec reflects decision 19 | Done-when met; **updated here** | [web-ui-ux-spec.md](../web-ui-ux-spec.md): project compatibility levels, the web's single World workspace and short Customize, templates the web offers, browser performance limits, decision 19 recorded as approved. `WebUxContractTest` still finds no web dependency in the desktop |
| M1b.6 Owner review of the rest of the spec | **Awaiting owner** | |
| **M1b.7** Customize gateway | Integrated, seen in part | Customize beside Play; Edit Selected first; every workspace and Back to World (`CustomizeGatewaySamplesTest`, `WorldFirstNavigationTest`); Audio & Music with Back to World seen in section 3 |
| M1b.7 Sound editing, house mesh editing | **Depend on MC.5 and R7a** | Shown as unavailable with the reason |

## 2. Changes made

**M1b.4: selection-aware Customize entries** (`editor-core/.../workspace/CustomizeGateway.java`):

- **Transform:** every selected entity now gets *Move, Rotate or Scale <name> (transform only, the mesh is unchanged)*, which runs the editor's real Move tool (`tools.move`, checked in `ActionInventoryTest`).
- **Material:** a material the entity uses was listed as unavailable, although it is edited in Details. It is now *Edit Material <file> in Details (shared by every surface that uses it)*. Choosing it returns to the World and shows Details, re-opening it if it was hidden (`PanelLayout.revealDetails`).
- **Terrain:** a terrain was listed as unavailable, although the World strip's Terrain tools edit it. It is now *Sculpt and Paint Terrain <file> (World strip: Terrain)*. Choosing it opens the Terrain tools.
- **Shared prefab:** *Edit Shared Prefab <file>*, unavailable, with the reason. Editing the shared prefab from here is M1.6; Details changes this instance only, and Apply is in the Outliner's context menu.
- **Mesh geometry:** a model's entry was the vague *Edit Model <file>*. It is now *Edit Mesh Geometry of <file>*, unavailable, saying that vertex, edge and face editing is R7a. It also says that sculpting a copy reshapes a project-owned copy, and that Move, Rotate or Scale only transforms the object.

**M1b.5: navigation in the core, tested** (`editor-core/.../workspace/WorkspaceNavigator.java`, new): Customize > a workspace and Back to World, moved out of the desktop UI code, which now delegates to it. One behaviour was added: if 3D Modeling cannot open (sculpt mode refused, for example during Play), it now says so instead of doing nothing.

**M1b.3: Customize directly beside Play** (`EditorApplication.createCustomizeGateway`): Back to World now comes before Customize.

**Out-of-date words** (M1b.2 removed the header tabs, but some messages still named them):
- The World strip's Characters message said "the Animations tab". It now says "Customize > Animation".
- `AssetOpening`'s guidance named the Visual Scripting, Animations, Sprites and Music tabs. It now names Customize > Logic, Animation, 2D Studio: Sprites and Audio & Music.
- The terrain guidance said "turn Paint on". It now points to the World strip.
- The Tiles panel's hint said "the Sprites tab". It now says "2D Studio: Sprites".

**M1b.6:** the spec records decision 19 as approved and adds:
- project compatibility levels (Web Editable, Web Preview Only, Desktop Required, Not yet checked on the web);
- the web's one World workspace and its Customize tools;
- which templates the web offers;
- a statement that no UrConnex code is changed here;
- browser performance limits.

Section 11 was regenerated after the Characters message change.

**Files:**
- New:
  - `editor-core/.../workspace/WorkspaceNavigator.java`
  - `editor-core/src/test/.../workspace/WorldFirstNavigationTest.java`
  - this report and its screenshots in `docs/reports/r1b-reconciliation/`
- Changed:
  - `CustomizeGateway.java`, `PanelLayout.java` (`revealDetails`), `WorldStrip.java`, `AssetOpening.java` (editor-core)
  - `EditorApplication.java` (navigator, new entry kinds, button order), `TilemapStudioPanel.java` (editor-desktop)
  - `CustomizeGatewayTest.java`, `ActionInventoryTest.java`
  - `docs/web-ui-ux-spec.md`, `docs/milestone-chart.md`, `docs/FEATURE_TEST_MATRIX.md`, `docs/INDEX.md`
- M2.2, paused, already in the tree:
  - `MaterialAssignment`, `MaterialAssignTool` and their tests
  - `entityAt` on the viewport interactions, with tests in the 2D, 3D and orthographic view tests
  - the material drop targets in `EditorApplication` and the Details change in `MeshPrimitiveComponentEditorProvider`

## 3. Test results

| Command | Exit | Result |
|---|---|---|
| `./gradlew :editor-core:test --tests "*WorldFirstNavigationTest" --tests "*CustomizeGatewayTest" --tests "*PanelLayoutTest" :editor-desktop:test --tests "*ActionInventoryTest"` | 0 | All passed |
| The same with `WorkspaceNavigator.backToWorld` changed to ignore Play | 1 | `shouldGoBackToTheGameViewWhilePlaying` failed as it should; restored |
| `GDXUNITY_UPDATE_AUDIT=1 ./gradlew :editor-desktop:test --tests "*WebUxContractTest"`, then without the variable, with `*CustomizeGatewaySamplesTest` and `*ActionInventoryTest` | 0, 0 | Section 11 rewritten; all passed |
| `./gradlew :editor-desktop:run --args="--smoke-test"` with `GDXUNITY_SCREENSHOT`: default window, 1024x640, and 1024x640 with `GDXUNITY_EDITOR_TAB=Music` (World ones taken before and after the footer fix) | 0 each | 180 frames, 0 errors each |
| `./gradlew cleanTest test --continue` (whole repository) | 0 | **1,975 tests, 0 failures, 0 errors, 1 skipped** (the POSIX-only exporter test), 23 modules |

**Counts (from the XML reports):** repository 1,954 (after M1b.6) to **1,975** (+21, all in editor-core, 473 now): `WorldFirstNavigationTest` +6 and `CustomizeGatewayTest` +1 (this work); `MaterialAssignmentTest` +7, `MaterialAssignToolTest` +4 and three `entityAt` tests in the 2D, 3D and orthographic view tests (M2.2, paused). editor-desktop stays 158 (its changed tests were edited, not added). Every other module unchanged.

**Screenshots (taken by the editor itself, after the fix):**

- [world-default.png](r1b-reconciliation/world-default.png), 1920 by 991:
  - the header reads **World / Untitled**, then Save, Undo, Redo, Project, Settings and Create;
  - the tool row is followed by the Outliner, Details and Content Browser buttons;
  - the scene fills the window, with the World strip (Terrain, Objects, Buildings, Characters) at the bottom;
  - the footer reads `Game Genre none yet (planned)  Game Mode 2D 2.5D 3D ... Customize | Play Pause Stop Stopped`.
- [world-1024x640.png](r1b-reconciliation/world-1024x640.png): the same at the minimum size. Customize and Play are side by side. The tool row is cut off after Sculpt (the finding above).
- [music-1024x640-back-to-world.png](r1b-reconciliation/music-1024x640-back-to-world.png): Audio & Music at the minimum size.
  - The header reads **Audio & Music / Untitled** and the footer `Back to World Customize | Play`.
  - Finding: the World's view tools (Select, Move, Rotate, Scale, Sculpt) still show in the tool row here, where they do nothing. That is clutter against Appendix E5's "low clutter"; recorded, not changed.

## 4. Remaining dependencies

| Item | Owned by |
|---|---|
| The genre selector and its registry | M3b.4 (needs M3b.1, M3b.2 and a TD) |
| Real mesh editing of a building, "Edit House in 3D" beyond sculpting a copy, the Buildings strip section | R7a |
| Sound-effect editing | MC.5 |
| Editing a shared prefab from Customize; Edit this instance, Make unique | M1.6 (TD) |
| A Characters palette in the strip | Not assigned to a milestone in the chart; M3c.2 (player prefab and spawn) is the nearest |
| The web UI itself | UrConnex (WEB1 to WEB6), outside this repository |

## 5. Manual verification still required

Nothing in R1b has been clicked by a person. All of it is **Manually Not Verified (Headless Environment)** except what the screenshots show.

**Checklist for an unfamiliar tester (M1b.5).** Give no help. Record what they do, where they hesitate, and a screenshot at each numbered step. Use the editor at 1024 by 640 once and maximised once.

1. Start the editor. Say where you are and what you would do first.
2. Make a slope: find the terrain tools and raise part of the ground.
3. Place a rock on the ground.
4. Find how to play the scene; play it; stop it. Is the scene as it was?
5. Select the rock. Open Customize: what does it offer? Choose the 3D editing entry.
6. Return to the World. Is the rock still selected, and the camera where it was?
7. Undo the rock, then redo it.
8. Open Details, the Outliner and the Content Browser, then close them. Restart the editor: are they as you left them?
9. Save, close and reopen the scene. Is the slope and the rock there?
10. Choose Buildings and Characters in the strip: is the message clear?
11. With the rock selected, which Customize entry would change its shape, and which only moves it?
12. Use keyboard shortcuts for Save, Undo and the Move tool.

Pass: steps 2 to 9 done with no help. The observer records the time per step and every place the tester looked for something that was not there.

## 6. Acceptance criteria satisfied

- M1b.1: done-when met (unchanged).
- M1b.3: the footer's done-when ("visible from World and other workspaces where it applies") is met and now shows Customize directly beside Play. The genre part waits on M3b.4.
- M1b.4: "unrelated controls never show together" is met for the strip, the level tools, Details and Customize's entries, which now keep transform, material, prefab and mesh editing apart and are tested for each kind of selection. It is not met for the tool row in non-World workspaces (the finding above).
- M1b.5: the automated part is done.
- M1b.6: done-when met; the spec reflects decision 19.
- M1b.7: Customize, Edit Selected, every workspace and Back to World are built and tested, with nothing lost on the way.

## 7. Acceptance criteria still blocked

| Milestone | Blocked on |
|---|---|
| M1b.2 | The comparison with the owner's reference screenshot (owner) |
| M1b.3 | The genre selector (M3b.4) |
| M1b.4 | Mesh editing (R7a) |
| M1b.5 | The unfamiliar tester (a person) |
| M1b.6 | The owner's review of everything beyond decision 19 |
| M1b.7 | A sound editor (MC.5) and house mesh editing (R7a) for its done-when ("the same for a sprite and a sound") |

## 8. Recommendations for the next milestone

1. **Run the tester session in section 5.** It is the only thing that can close M1b.5. Also have the owner compare the World against the reference screenshot, which is the last open part of M1b.2.
2. **Make the tool row follow the workspace.** Hide the World's view tools outside World and 3D Modeling, and show that the row scrolls at the minimum size. That is a small desktop change and closes the M1b.4 finding.
3. **Finish M2.2** (material drag and drop, paused with its code and tests passing) and write its report.
4. **For M3b.4,** write the M3b.1 audit and the `GameTestProfile` TD, so the genre selector can replace the "planned" label.

## Acceptance (AGENTS.md §10)

1. Criteria listed and marked: sections 1, 6 and 7.
2. Commands, exit codes and counts: section 3.
3. New failures investigated: two failures from my own changes were fixed (a duplicate in a test's set, and the expected stale generated section); the mutation check failed as intended.
4. Diff reviewed: only the files in section 2.
5. Matrix and INDEX updated.
6. Limits and next steps: sections 4, 5 and 8.
7. **No commit, push, deployment or other destructive Git operation was performed.** The UrConnex repository was not read or changed.
