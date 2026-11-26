# M3c.7 Scenario B (2D Platformer + 2D): report

**Date:** 2026-11-24. **Milestone:** M3c.7. **Decision:** TD-105, approved by the owner on 2026-11-24 ("Drive its RigidBody2D"). **Status:** **done-when met in the form "PASS, or BLOCKED naming the missing capability" (2026-11-25).** 9 steps PASS and 2 BLOCKED, both expected. Platformer + 2D is Implemented and Integrated (editor Play and the game runtime). **Manually Not Verified (Headless Environment):** nobody played it with the keyboard or clicked in 2D Studio.

**Done-when:** "PASS, or BLOCKED naming the missing capability" for each step of: World, tiles and platforms, a CC0 sprite character, Platformer + 2D, Play with camera, movement and collision, Stop, Customize to 2D Studio, change a sprite or frame, return, Play, export.

## What was built

- **Platformer + 2D is playable (TD-105)**, where Play used to refuse it ("needs a 2D character controller").
  - `GameGenres.platformer2D`: a `FOLLOW_2D` camera, `RUN_2D` movement, 2D physics, and requirements Player, `CHARACTER_2D` and Camera.
  - `Run2DMover` (engine-render, runtime only): A/D set the horizontal velocity of the player's dynamic Rigid Body 2D. Space sets the upward velocity once per press, when there is ground under the player. It fixes the runtime body's rotation.
  - `PhysicsSystem2D`: attaches to its scene (`Scene.getPhysics2D()`). New `setLinearVelocity` and `getLinearVelocity` in scene units, and `isGrounded`: three short downward rays from the bottom of the body's shapes. The ground check allocates nothing per call.
  - `ProfileCameraRig` `FOLLOW_2D`: follows across the screen and is reset to look straight along −Z after every update.
  - Scene checks: a missing Rigid Body 2D and a missing 2D collider are each offered as a fix, one undo step each:
    - `ADD_RIGID_BODY_2D`: dynamic, fixed rotation.
    - `ADD_BOX_COLLIDER_2D`: the size of the sprite as drawn, or the loaded sprite's canvas for a sprite drawn at pixel size.

    A non-dynamic body is reported with no fix.
- **Found and fixed while testing:** the 2D camera tilted by about 89° during a jump. A follow rig aims at the player, and with a depth offset of 1 unit any vertical lag became a steep pitch. The rig now resets its rotation.
- **Scenario test** `ScenarioB2DPlatformerTest` (editor-desktop): the steps of the done-when from an Empty 2D project.
  - The level: a tile map with the ground row and a tile wall painted with the rectangle tool, the tile marked solid, and a static box platform.
  - The hero, imported and dragged in, then given Player. Play's fixes are applied one at a time.
  - Play: stands on tiles, jumps onto the platform, runs off, and stops at the wall; the camera follows.
  - Stop. Edit 2D Asset on a frame, Back to World, Play again.
  - Save, reopen, Play, export, and the export's files played in the game runtime.
  - It writes a PASS/BLOCKED table to `editor-desktop/build/scenario-b/scenario-steps.md`. Two BLOCKED steps are expected:
    - no CC0 2D character in the repository (M2b.5, MP.2);
    - the exported game not launched as a program.

## Evidence

| Check | Result |
|---|---|
| `.\gradlew.bat :engine-scene:test :engine-render:test --continue --offline -x generateAssetList` | **Exit 0. engine-scene 236 tests, engine-render 208 tests, 0 failures.** New: `PhysicsSystem2DMovementTest` (4) and `Platformer2DProfileTest` (2): the fixes one at a time; run, one jump per press, landing, upright, the camera follows and stays level. `PlayableGenrePairsTest` lists `Platformer2DProfileTest` as the pair's acceptance test, and `GameGenresTest` lists platformer/2d as playable |
| Earlier failures in those runs, all wrong expectations in my new tests except one | Box2D keeps shapes 2 px apart (two 0.01 m skins), so a resting body is 2 px higher. A single push slows by friction. A following camera trails by speed ÷ rate (50 px). **The one real bug:** the 2D camera tilt, fixed in `ProfileCameraRig` |
| `:editor-core:test` for `ProfileFixesTest` (+1 test for the 2D fixes), `ProfilePlayStepTest`, `GenreChoicesTest` and `PlayToolsTest` | **Exit 1, cause not established.** The run wrote no new result files, so the test code probably did not compile. **Not investigated:** the following editor-desktop run was stopped by Claude Code for low memory, and nothing was restarted |
| `:editor-desktop:test` for `ScenarioB2DPlatformerTest` and `GameModeSelectorTest` | **Not run** (stopped for low memory before it started) |

### Completion run (2026-11-25)

| Command | Exit | Result |
|---|---|---|
| `.\gradlew.bat :editor-core:compileTestJava --offline -x generateAssetList` | 0 | Compiles. The earlier exit 1 was therefore failing tests, not a compile error |
| `.\gradlew.bat :editor-core:test --tests "*ProfileFixesTest" --tests "*ProfilePlayStepTest" --tests "*GenreChoicesTest" --tests "*PlayToolsTest" --offline -x generateAssetList` | 1 | 22 tests, 3 failed: `GenreChoicesTest` (2) and `PlayToolsTest` (1) used Platformer + 2D as their example of a pair Play refuses. TD-105 made it playable on purpose, so these were stale expectations, not regressions |
| (after the fix below) the same command | 0 | `ProfileFixesTest` 7, `ProfilePlayStepTest` 6, `GenreChoicesTest` 5, `PlayToolsTest` 4; 0 failures |
| `.\gradlew.bat :editor-desktop:test --tests "*ScenarioB2DPlatformerTest" --tests "*GameModeSelectorTest" --offline -x generateAssetList` | 0 | `ScenarioB2DPlatformerTest` 1, `GameModeSelectorTest` 2; 0 failures, on the first run |
| `.\gradlew.bat :editor-core:test :engine-desktop:test --continue --offline -x generateAssetList` | 0 | editor-core **616 tests (1 skipped)**, engine-desktop **162**; 0 failures |

**Test fix:** the planned example in both tests is now Action RPG + 2D (still planned; it plays in 2.5D and 3D). Both tests now also check that Platformer + 2D is playable and plays in 2D, 2.5D and 3D.

### Step results (`editor-desktop/build/scenario-b/scenario-steps.md`)

| Step | What | Result | Evidence or reason |
|---|---|---|---|
| 1 | New project (Empty 2D) opens in World | PASS | `Platformer`, Game Mode 2D |
| 2 | Tiles and platforms | PASS | `sprites/tiles.sprite` imported; a Tile Map with the ground row and a 3-tile wall painted with the rectangle tool, tile 1 marked solid (2 static boxes); a 128 × 32 platform with a static Rigid Body 2D and Box Collider 2D |
| 3 | A sprite character | PASS | `sprites/hero.sprite` imported and dragged in; Player added (Add Component > Game) |
| 3 | A CC0 sprite character | BLOCKED | The hero is a ForgeRoom original (Apache-2.0); the repository has no CC0 2D character yet (CC0 sets are M2b.5 and MP.2) |
| 4 | Choose Platformer + 2D | PASS | Play offered, one at a time: Add a Rigid Body 2D to hero; Add a Box Collider 2D to hero. Each is one undo step |
| 5 | Play: camera, movement and collision | PASS | starts on the tile ground; D runs, Space jumps (once per press) and the hero lands on the platform; it runs off, lands on the ground and stops at the tile wall (x within 4 of 608.0 − 8); the camera follows and stays level (real Box2D, editor Play) |
| 6 | Stop | PASS | scene JSON identical, 6 undo steps kept |
| 7 | Customize > Edit 2D Asset; change a frame; Back to World | PASS | "Edit 2D Asset hero.sprite" leads to 2D Studio; a pixel of frame 3 painted and saved; the World reloads and shows it |
| 8 | Play again | PASS | the Play copy has the edited frame; Stop returns to the World |
| 9 | Save, reopen, Play, export | PASS | genre, mode, entities and tiles survive; Play works after reopening; the export holds the scene, the edited hero and the tile set, and its files play in the game runtime with 0 errors |
| 9 | Launch the exported game as a program | BLOCKED | The tests export with a stand-in runtime image; the export's files ran in the game runtime's session instead. A packaged run is on the human checklist |

### Diff review (completion run)

- Changed: `editor-core` tests `GenreChoicesTest` and `PlayToolsTest` (expectations only); this report; `FEATURE_TEST_MATRIX.md`, `INDEX.md`, `milestone-chart.md` (M3c.7 rows).
- The working tree held a lot of earlier uncommitted work (M7a, MC.2, MC.3, M3c.6, M3c.7 and asset work, listed in their reports); it was not touched.
- The other editor-desktop and engine modules were not run in full in this session (memory). The last full run is the one recorded in the MC.3 and M3c.6 reports.

Starting and ending HEAD: `b8bace5`. No commit, push or destructive Git operation was performed.

## Known limitations

- No slopes, one-way platforms, coyote time, jump buffering, variable jump height or moving platforms (TD-105). Another dynamic body can push the player.
- No ground check before Play in 2D: a player over nothing falls. The 3D profiles refuse Play in that case.
- The web player does not run genre profiles (unchanged).
- Nothing was seen in the running editor.

- The two BLOCKED steps: no CC0 2D character (M2b.5, MP.2) and no packaged-program launch.

## Human checklist

1. Project > New Game > **Empty 2D** > Create Project.
2. Import `sprites/tiles.sprite`; add a Tile Map; paint a ground row and a wall; mark the tile solid.
3. Create a platform with a static Rigid Body 2D and a Box Collider 2D.
4. Import and drag in `sprites/hero.sprite`; Add Component > Game > Player.
5. World Settings: Game Genre Platformer / Side-scroller. Press Play and apply each offered fix.
6. Play: A/D run, Space jumps; land on the platform, stop at the wall; the camera follows and stays level.
7. Stop; Customize > Edit 2D Asset; paint a frame; save; Back to World; Play again and see the change.
8. Save, close, reopen, Play; Project > Export, and launch the exported game.

## Next

- A person runs the checklist above on the packaged editor (E2E plan).
- Optional follow-ups from TD-105's limits: a 2D ground check before Play, one-way platforms, coyote time.
