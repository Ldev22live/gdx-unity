# Roadmap M3b.6: World View Default (2026-11-15)

Milestone M3b.6 of [milestone-chart.md](../milestone-chart.md) (roadmap R3b). **Owner decision 11 is closed:** asked in this session, the owner chose to **rename Viewport to World**. Its play-mode part was answered on 2026-11-14 (the footer's Game Mode is the scene's own mode). **Status:**
- **Implemented and Integrated.**
- **Seen in screenshots.**
- **Manually Not Verified (Headless Environment):** nothing was clicked.

- Branch: `phase-12-3d-foundation`
- Starting `git rev-parse --short HEAD`: `0469ceb`. Ending: `0469ceb` (no commit was made).
- At the start, the working tree held this session's uncommitted M3.3 to M3b.5 and M3c.1 work (listed in their reports).
- No new module, dependency or technical decision.

Most of this milestone already existed:
- **M1b.2:** World as the default layout, with the World strip as the contextual palette grown from the TD-083 level palette.
- **M1b.7:** Customize reaching every other view.
- **M3b.4:** the genre statuses in the footer.

This milestone adds two things:
- the name;
- keeping the World's tools out of the other workspaces, a finding of the [R1b reconciliation](r1b-outstanding-work-reconciliation.md) that belongs to "the World screen".

## 1. Acceptance criteria

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | The Viewport view becomes the World screen | **Met** | The tab is now "World" (`Workspace.TAB_WORLD`, `CustomizeGatewayTest`). The header already read "World / …" (M1b.2) |
| 2 | The level tools appear as its contextual strip | **Met (M1b.2)** | The World strip (Terrain, Objects, Buildings, Characters; 2D: Tiles, Sprites, Objects, Characters) opens only the relevant palette. Unbuilt sections say what they need |
| 3 | The World view is the default | **Met (M1b.2)** | The editor opens in World with no permanent panels (TD-085). Seen in `m3b-6/world-default.png` |
| 4 | All other views are reachable through Customize | **Met (M1b.7)** | Every centre tab except the game view is a Customize destination (`CustomizeGatewayTest`). The game view shows during Play |
| 5 | The default screen does not advertise unsupported profiles as ready | **Met** | Game Genre marks every pair that isn't playable, and a line after Game Mode says what Play does (M3b.4). The World strip labels its unbuilt sections. **New:** the World's tools no longer appear in other workspaces |

## 2. What was built

- **`editor-core`:**
  - `Workspace.TAB_VIEWPORT` ("Viewport") is now `Workspace.TAB_WORLD` ("World"). Its uses in `WorkspaceNavigator` and the tests are renamed.
  - New `Workspace.showsWorldTools()`: true for World and 3D Modeling, which is the World's view with sculpt mode on.
- **`editor-desktop`:**
  - The tools row (the view overlay, Select, Move, Rotate, Scale, Frame, Sculpt, Level) shows only where `showsWorldTools()` holds. In Animation, Logic, Audio & Music, 2D Studio and Assets only Compile Scripts and the panel buttons remain.
  - The testing aid `GDXUNITY_EDITOR_TAB` takes `World`, and still `Viewport`, so older scripts keep working.
  - `CENTER_TAB_VIEWPORT` is now `CENTER_TAB_WORLD`.
- **Kept on purpose:** "viewport layout" (the arrangement of view panes) and "viewport" for one camera's pane. That is a different meaning from the screen's name, and the usual one.
- **Docs:**
  - the generated tables in `world-first-ux-audit.md` (the inventory's homes now say "World tool strip" and "World view overlay");
  - `web-ui-ux-spec.md`'s layout row is now "World view".

## 3. Commands and results

| Command | Exit | Result |
|---|---|---|
| `./gradlew :editor-core:test --tests "io.github.ldev22live.gdxunity.editor.core.workspace.*"` | 0 | 7, 7, 4, 5 and 6 pass |
| `GDXUNITY_UPDATE_AUDIT=1 ./gradlew :editor-desktop:test --tests "*ActionInventoryTest" --tests "*WebUxContractTest"`, then without the variable | 0, 0 | Tables refreshed, then checked |
| `./gradlew :editor-core:cleanTest :editor-core:test` and the same for `editor-desktop` | 0, 0 | 492 (1 skipped, as before) and 160 |
| `GDXUNITY_SCREENSHOT=... GDXUNITY_EDITOR_ASSISTANT=0 ./gradlew :editor-desktop:run --args="--scene samples/action-rpg-valley-3d.json --smoke-test"`, three times: no tab, `GDXUNITY_EDITOR_TAB=Animations`, `GDXUNITY_EDITOR_TAB=World` | 0 each | 180 frames, 0 errors each |

| Module | Before (M3b.5) | After |
|---|---|---|
| `editor-core` | 491 | 492 |
| `editor-desktop` | 160 | 160 |

No engine module changed.

**Screenshots** (looked at):
- [m3b-6/world-default.png](m3b-6/world-default.png): "World / Untitled" with the view overlay, Select, Move, Rotate, Scale, Frame, Sculpt, Level and Compile Scripts.
- [m3b-6/animations-no-world-tools.png](m3b-6/animations-no-world-tools.png): "Animation / Untitled" with only Compile Scripts in that row.
- [m3b-6/world-by-tab-name.png](m3b-6/world-by-tab-name.png): opened with `GDXUNITY_EDITOR_TAB=World`.

## 4. Failures found and fixed

None. The rename compiled and the tests passed the first time.

## 5. Diff review

- **Changed:**
  - `Workspace`, `WorkspaceNavigator`, `EditorApplication`;
  - tests: `CustomizeGatewayTest` (+1), `WorldFirstNavigationTest`, `CustomizeGatewaySamplesTest`, `ActionInventoryTest`.
- **Docs:**
  - the chart (decision 11, M3b.6, R3b);
  - `INDEX.md`, `FEATURE_TEST_MATRIX.md`;
  - `web-ui-ux-spec.md`;
  - the regenerated tables in `world-first-ux-audit.md`;
  - this report and `docs/reports/m3b-6/`.

Older reports and roadmap versions that say "Viewport" are left as written, because they record what was true then. No files outside this repository were touched. CRLF files kept their line endings.

## 6. Known limitations and next steps

- **Shortcuts still work everywhere.** The keys for the World's tools (Q, W, E, R, F) still act in other workspaces. Only the buttons are hidden.
- **At 1024x640 the right end of the World tools row is cut off.** This is an R1b finding, unchanged here.
- **The owner's reference screenshot for the World screen** (M1b.2) is still not available.
- **Manual check for a person:**
  1. Open the editor: World with its tools.
  2. Open Customize > Animation: no World tools.
  3. Click Back to World: the tools return.
- **Next in R3b: M3b.7**, further genre slices and agent tools for profiles. Its first slice could be the agent tools: set the genre and mode, check the scene, Play and Stop. **Then R3c: M3c.2** (player prefab and spawn point).

## 7. Git

No stage, commit, push, reset, stash, branch switch or other destructive Git operation was performed.
