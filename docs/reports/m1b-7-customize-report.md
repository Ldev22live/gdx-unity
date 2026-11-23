# M1b.7 Customize gateway (World-first stage 2): report

**Milestone:** M1b.7 in [milestone-chart.md](../milestone-chart.md) (roadmap phase R1b, Appendix E2), built as **stage 2** of the staged plan in [world-first-ux-audit.md](world-first-ux-audit.md).
**Owner instruction:** "continue with the next stage" (2026-11-14), after decision 16 was approved to be built in stages.
**Decision record:** [TD-084](../TECHNICAL_DECISIONS.md) (Customize gateway and the Back to World contract). It was proposed with this stage and **approved by the owner later on 2026-11-14**, as built. **Later change:** in M1b.3 the gateway moved from toolbar row 2 into the footer ([m1b-3-footer-report.md](m1b-3-footer-report.md)); the screenshots below show where it was at this stage.
**Date:** 2026-11-14. **Platform:** Windows 11 Pro, Java 21, real display for the smoke runs.
**Starting and ending `git rev-parse --short HEAD`:** `d896331` (unchanged). The working tree already held many uncommitted changes from earlier work; the files this stage touched are listed below.

## Done-when and result

The chart's done-when: *select a house, Customize, edit, Back to World, with nothing lost; the same for a sprite and a sound.*

| Part | Result |
|---|---|
| One entry point to World, 3D Modeling, 2D Studio, Animation, Audio & Music, Logic and Assets | **Met:** the Customize dropdown lists all seven; the one on screen is marked "(here)" |
| Edit Selected offered first | **Met:** the selection's entries come first, from the files it uses |
| Opens with no selection | **Met:** the first row then says "Select something in the World to edit it", and the workspaces follow |
| Prominent Back to World that keeps project, selection, unsaved changes and camera | **Met in code, not observed in a session:** Back to World shows whenever the workspace is not the World; switching only changes the panel shown (TD-084) |
| A house: Edit House in 3D | **Partly:** "Edit House in 3D (sculpt a copy)" starts a sculpt from its mesh as a new entity; the house's own model cannot be edited (mesh editing is R7a) |
| A sprite: Edit Sprite | **Met** for an entity whose component names a `.sprite`: it opens in the Sprites tab |
| A sound: Edit Sound | **Not met:** there is no sound editor (MC.5). The entry is listed as "Edit Sound door.wav (no editor yet)" and choosing it says so |

**M1b.7 is partly built.** Its dependencies M1b.2 and M1b.4 are not finished either.

## What was built

- **`Workspace`** (`editor-core`, new package `workspace/`): the seven workspaces, their labels, and which centre tab shows each (3D Modeling is the Viewport with sculpt mode on; the Scene tab during Play counts as the World).
- **`CustomizeGateway`**: the "Edit Selected" entries. It finds every file an entity uses by **serializing its components and taking every string that names an asset**, so it needs no list of component types and covers ones added later. A sculpt gets "Edit X in 3D" (enter sculpt mode). A model or shape gets "Edit X in 3D (sculpt a copy)" (the existing Sculpt From Selected). A sprite, clip, song or graph opens in its editor. A sound, model file, material, terrain, texture or prefab is **listed and marked unavailable**, with the reason taken from the M1.5 guidance texts.
- **`CustomizeMenu`** (`editor-desktop`): the dropdown's items. Edit Selected comes first, then a separator, then the workspaces with the current one marked. Choosing an unavailable entry puts its reason in the status bar instead of doing nothing.
- **In the editor:** a muted **workspace indicator**, the **Customize** button and **Back to World**, placed after Play, Pause and Stop (`EditorToolbar.addBesidePlay`), where Appendix E1's footer will hold Customize. `TabbedPanel` now reports the tab actually shown, so the indicator is right however a tab was opened (some code paths selected a tab without updating the editor's own record of it).

**A layout problem found on screen:** the first build put the gateway at the right end of the top bar. That bar is already wider than a 1920-pixel window and scrolls sideways, so the screenshot showed **Customize cut off**. It moved to the second row beside Play, which has room.

## Tests (6 new; `editor-core` 431 to 435, `editor-desktop` 140 to 142)

| Test | What it pins |
|---|---|
| `CustomizeGatewayTest` (4, new) | Every centre tab maps to its workspace and back, an unknown tab is refused; a song is offered in Audio & Music and a recorded sound is listed but unavailable, with MC.5 in the reason; a sculpt gets one "Edit in 3D" entry, a model and a shape a "sculpt a copy" entry, and the model and material files are marked unavailable; no selection and an entity that uses no file give nothing |
| `CustomizeGatewaySamplesTest` (2, new) | **Every entity of every shipped sample**, with the editor's full serializer list: each graph script is offered in Logic and each animator's clip in Animation; every file an entry opens exists; the samples exercise Logic, Animation and Audio & Music. Also the menu's order, the "(no editor yet)" and "(here)" marks, that an unavailable entry still reaches its handler, and the no-selection row |
| `ActionInventoryTest` (changed) | The two sculpt actions Edit Selected runs are registered |

## Evidence from the real editor

`./gradlew :editor-desktop:run --args="--smoke-test"` was run with `GDXUNITY_SCREENSHOT`, once as the editor opens and once with `GDXUNITY_EDITOR_TAB=Animations`. Each run rendered 180 frames with 0 errors.

- [m1b-7-customize/world.png](m1b-7-customize/world.png): `Play Pause Stop Stopped | World Customize`; Back to World is hidden.
- [m1b-7-customize/animation-with-back-to-world.png](m1b-7-customize/animation-with-back-to-world.png): `Animation Customize Back to World`.

These were taken after the layout fix and the muted indicator; the earlier attempts were overwritten. **The Customize dropdown was not opened** and none of its entries were chosen on screen.

## Commands and exit codes

| Command | Exit | Result |
|---|---|---|
| `./gradlew :editor-core:test --tests "*CustomizeGatewayTest"` | 0 | 4 pass |
| `./gradlew :editor-desktop:compileJava` | 0 | |
| `./gradlew :editor-desktop:test --tests "*CustomizeGatewaySamplesTest" --tests "*ActionInventoryTest"` | 0 | 2 + 6 pass |
| `./gradlew :editor-desktop:run --args="--smoke-test"` with screenshots (three pairs over the layout fix) | 0 each | 180 frames, 0 errors each |
| `./gradlew :editor-core:test :editor-desktop:test` | 0 | **editor-core 435 tests, 0 failures, 1 skipped; editor-desktop 142, 0 failures** |

No other module changed, so the repository total is 1,910 + 6 = **1,916 (computed, not a fresh full run)**.

## Limits

- **Manually Not Verified (Headless Environment):** opening Customize, choosing each entry, and the claim that nothing is lost on the way back. That claim rests on the code: switching only selects a tab and turns sculpt mode on or off; no session is closed and no selection or camera call is made.
- **Sculpt a copy** creates and selects a new entity (the existing action's behaviour), so after Back to World the copy is selected, not the original.
- 2D Studio opens the Sprites tab, not Tiles. Assets only brings the Content Browser tab forward.
- An entity whose Sprite Renderer names an image (not a `.sprite`) gets "Edit Texture … (no editor yet)".
- The top bar is wider than a 1920-pixel window and scrolls (pre-existing); stage 4 moves its tabs and tools out.

## Next stages

Stage 3 (M1b.3 footer: Play, Stop, Customize, Game Mode, Genre shown as planned) needs **decision 11**, whether the footer's Game Mode overrides the scene's own mode at Play. Stage 4 (the rest of M1b.2: World strip, collapsible panels, the World as default) needs a layout TD. **TD-084 needs the owner's approval** (or changes).

## Files changed

- New: `editor-core/.../workspace/Workspace.java`, `CustomizeGateway.java`, `editor-core/.../workspace/CustomizeGatewayTest.java`, `editor-desktop/.../ui/menu/CustomizeMenu.java`, `editor-desktop/.../CustomizeGatewaySamplesTest.java`, this report, `m1b-7-customize/*.png`.
- `editor-desktop/.../EditorApplication.java` (tab titles from `Workspace`, the gateway, Back to World, Edit Selected handling), `.../ui/EditorToolbar.java` (`addBesidePlay`), `.../ui/TabbedPanel.java` (`getSelectedIndex`), `.../audit/ActionInventoryTest.java`.
- `docs/TECHNICAL_DECISIONS.md` (TD-084), `docs/milestone-chart.md` (M1b.7 row), `docs/reports/world-first-ux-audit.md` (stage 2, controls table), `docs/FEATURE_TEST_MATRIX.md`, `docs/INDEX.md`.

## Acceptance criteria (AGENTS.md §10)

1. Criteria listed and marked: above; partly met, with the parts that wait on R7a and MC.5.
2. Exact commands, exit codes and per-module counts: above.
3. New failures investigated: no test failed; one layout problem found on screen and fixed.
4. Diff reviewed, no unrelated changes: only the files above.
5. Matrix and INDEX updated: done.
6. Limits and next steps: above.
7. **No commit, push or other destructive Git operation was performed.**
