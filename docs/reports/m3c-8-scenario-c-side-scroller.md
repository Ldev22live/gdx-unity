# Roadmap M3c.8: Scenario C, a 2.5D side-scroller (2026-11-16)

Milestone M3c.8 of [milestone-chart.md](../milestone-chart.md) (roadmap R3c; acceptance scenario C of Appendix E4). **Status:**
- **PASS on the sprite path, with three parts BLOCKED** (model geometry, CC0 sets, launching a packaged export). The milestone's done-when is "PASS, or BLOCKED naming the missing capability", so it is met in that form.
- **Manually Not Verified (Headless Environment):** nobody played it with the keyboard or clicked in 2D Studio.

- Branch: `phase-12-3d-foundation`
- Starting `git rev-parse --short HEAD`: `0469ceb`. Ending: `0469ceb` (no commit was made).
- At the start, the working tree held this session's uncommitted work up to M3c.5 (listed in its reports).
- No new module, dependency or technical decision.

**Order:** M3c.6 waits on R7a, and M3c.7 needs a Platformer + 2D profile and MC.3, so M3c.8 was the next one that could be done.

**Design choices (mine):**
- The scenario's "edit a model or sprite" is done with **the sprite**: 2D Studio exists, and mesh editing does not (R7a).
- The milestone depends on **MC.4** (a hybrid sample), which is not built, so the scenario builds its own level in a new project.
- A dropped sprite in a 2.5D or 3D world becomes **100 units** on its longer side, as a dropped texture does.

## 1. Acceptance criteria and step results

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | Built with compatible 2D and 3D assets | **PASS** (CC0 part BLOCKED) | New Level; a platform from Create > Box, sized in Details, with Add Component > Box Collider 3D (fitted to the box); a pine from Objects behind the play plane; `sprites/coin.sprite` imported and dragged in; Player and Spawn Point from Characters |
| 2 | Platformer + 2.5D with a side camera and a depth constraint | **PASS** | On real Jolt in editor Play: starts at the spawn; the side camera follows in front of the plane; D runs into the platform's side and stops; Space jumps onto it; depth stays 0 throughout |
| 3 | Edit a model or a sprite, return | **PASS** (sprite) / **BLOCKED** (model geometry, R7a) | Customize offers "Edit Sprite coin.sprite" in 2D Studio. A pixel was painted, undone, redone and saved; Back to World keeps the selection, and the World's coin reloads and shows it |
| 4 | Play again | **PASS** | The Play copy loads the edited coin |
| 5 | Export | **PASS** (files) / **BLOCKED** (launching a packaged program) | The export holds the scene and the edited sprite; its files play 2 s in the game runtime's session as "Platformer / Side-scroller + 2.5D" with 0 errors |
| 6 | Save and reopen | **PASS** | Genre, mode and every entity survive; Play works after reopening |
| 7 | Web status | **Not supported** | No Platformer + 2.5D profile in the web player, and no terrain (M3.11) |

The test writes its step table to `editor-desktop/build/scenario-c/scenario-steps.md` and asserts the BLOCKED list:
- model geometry (R7a);
- launching a packaged export;
- CC0 2D and 3D sets (M2b.5, MP.2).

## 2. What was built

- **`editor-desktop` test `ScenarioCSideScrollerTest`:** the scenario, run through the editor's own operations. It leaves the project in `build/scenario-c/project`.
- **Edited sprites reach the World (fix):**
  - `engine-render`: `SpriteAnimatorComponent.requestReload()` and `TilemapComponent.requestReload()`;
  - `editor-core`: `asset/SpriteReload.reloadUsers`;
  - `editor-desktop`: the editor's on-disk change handler now reloads the users of a changed `.sprite` in the shown scene, and in the authored scene during Play. The asset watcher sees a 2D Studio save within 2 s, so the same hook covers both.
- **Dropped sprites sized for the world (fix):** `AssetPlacement` gives a sprite dropped into a 2.5D or 3D scene a longer side of 100 units, keeping its shape. A 2D scene keeps the pixel size. A sprite that cannot be read keeps its pixel size, and the status bar says why.

## 3. Commands and results

| Command | Exit | Result |
|---|---|---|
| `./gradlew :editor-core:test --tests "*SpriteReloadTest" :editor-desktop:compileJava` | 0 | Passes |
| `./gradlew :editor-desktop:test --tests "*ScenarioCSideScrollerTest"` | 1, then 0 | A wrong import (`CameraRigComponent` is in `components`), then passing |
| Mutation: `SpriteAnimatorComponent.requestReload()` made empty | 1 | `SpriteReloadTest` fails, as intended; restored |
| `./gradlew :editor-core:test --tests "*AssetPlacementTest" --tests "*SpriteReloadTest" :editor-desktop:test --tests "*ScenarioCSideScrollerTest"` | 0 | Passes, with the sized coin asserted |
| `./gradlew :engine-desktop:run --args="--project <scratch copy> --scene scenes/Main.json --smoke-test=240"` with `GDXUNITY_SCREENSHOT` | 0 (twice) | "240 of 240 frames, 0 runtime error(s)" |
| `./gradlew :editor-desktop:run --args="--project <scratch copy> --scene scenes/Main.json --smoke-test"` with `GDXUNITY_EDITOR_SELECT=coin` | 0 (twice) | 180 frames, 0 errors |
| `./gradlew cleanTest test --continue` | 0 | 2,093 tests, 0 failures, 1 skipped (the POSIX-only test) |

| Module | Before (M3c.5) | After |
|---|---|---|
| `editor-core` | 511 (1 skipped) | 513 (1 skipped): +`SpriteReloadTest`, +1 `AssetPlacementTest` |
| `editor-desktop` | 161 | 162: +`ScenarioCSideScrollerTest` |
| All other modules | unchanged | unchanged, all pass |

**Screenshots** (looked at):
- [m3c-8/runtime-side-scroller-frame-240.png](m3c-8/runtime-side-scroller-frame-240.png): the game runtime's side view along the play plane. It shows the player at the spawn, the platform to the right, the pine behind the plane, and the coin as a clear 2D sprite above the platform, with the green pixel painted in 2D Studio at its corner. **The first run (before the size fix) showed the coin as a tiny speck.**
- [m3c-8/editor-side-scroller-world.png](m3c-8/editor-side-scroller-world.png): the editor with the coin selected (Details: Width 100, Height 100). The footer reads "Game Genre Platformer / Side-scroller", 2.5D, "Playable".
  - The view is this machine's saved "X axis" side pane. It looks along X, so a flat sprite in the play plane is edge-on there and does not show; the runtime frame shows it.

## 4. Failures found and fixed

- **An edited sprite kept its old look in the World.** A sprite animator reads its `.sprite` once and keeps it. The editor's on-disk change handler reloaded textures and models but not sprites. So after a 2D Studio save, the World showed the old sprite until the scene was reopened, while Play (a fresh copy) showed the new one. Fixed (section 2).
- **A dropped sprite was invisible in a 2.5D or 3D world.** It took its pixel size: 16 units for the 16-pixel coin, beside an 80-unit character. Seen in the first runtime screenshot. Fixed (section 2).
- **The desktop hook itself is GL-side glue:** listed as **needs graphics test**. Nobody watched a sprite reload on screen; the reload logic is tested headless.

## 5. Diff review

- **New:**
  - `SpriteReload`;
  - tests `SpriteReloadTest`, `ScenarioCSideScrollerTest`;
  - this report and `docs/reports/m3c-8/`.
- **Changed:**
  - `SpriteAnimatorComponent`, `TilemapComponent`, `AssetPlacement`, `EditorApplication` (the change handler);
  - `AssetPlacementTest`;
  - chart (M3c.8 row, R3c line), `INDEX.md`, `FEATURE_TEST_MATRIX.md` (three rows).
- The new `AssetPlacementTest` case uses imports, as AGENTS.md section 6 asks. The test's older cases still use inline names; I left them.
- No files outside this repository were touched; line endings kept.

## 6. Human checklist (keyboard and screen)

1. Project > New Game > **Empty 2.5D** > Create Project.
2. World strip > Terrain > New Level (grass).
3. Create > Box. In Details, set its size to 300 x 40 x 400 and move it to x 300, y 100. Then Add Component > Box Collider 3D; the collider outline matches the box.
4. Objects > Pine Tree: place one behind the play plane.
5. File > Import Asset > `sprites/coin.sprite` from the shipped assets. Drag it from the Content Browser above the platform; it is clearly visible.
6. Characters: place the Player and a Spawn Point.
7. Footer: Game Genre Platformer / Side-scroller, Game Mode 2.5D, "Playable".
8. Play: the camera is side-on; A and D run, the platform's side stops you, Space jumps onto it, and you cannot move in depth. Stop.
9. Select the coin > Customize > Edit Sprite. Paint something visible, then Ctrl+Z and Ctrl+Y. Save.
10. Back to World: **within about 2 seconds the coin in the World shows the change.**
11. Play: the changed coin shows. Stop.
12. Save, close, reopen, Play. Export Game and launch the exported game; the coin is the edited one.

## 7. Known limitations and next steps

- **Model geometry:** R7a (sculpt a copy is the only 3D reshape today).
- **CC0 2D and 3D sets:** M2b.5 and MP.2.
- **MC.4 (the hybrid sample):** not built.
- **Sprite reload timing:** a reload restarts the sprite's animation, and it waits for the asset watcher's next check (up to 2 s).
- **The scenario's coin is decoration:** it is not collected. Collectibles are game logic (graphs) beyond this profile.
- **Next in R3c:**
  - M3c.9 (Scenario D, audio) needs M8.6, MC.5 and MC.6, none built. It could be run now to record its BLOCKED steps.
  - Otherwise R3c is waiting on other phases. The chart's next unblocked work on the critical path is in R2b (M2b.1, the asset source interface and manifest). That needs a TD, Legal and Dep approval from the owner before any code.

## 8. Git

No stage, commit, push, reset, stash, branch switch or other destructive Git operation was performed.
