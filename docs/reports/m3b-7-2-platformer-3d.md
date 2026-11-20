# Roadmap M3b.7, Slice 2: Platformer + 3D (2026-11-19)

Milestone M3b.7 of [milestone-chart.md](../milestone-chart.md) (roadmap R3b) is "one slice each, in the registry: 3D platformer, third-person, first-person, racing and the rest". Slice 1 was the agent tools. **This slice makes Platformer + 3D playable**, the first pair the chart names. **Status:**
- **Implemented and Integrated.** The footer's Game Genre now offers Platformer as playable in 3D, and editor Play and the game runtime run the same profile step.
- **The pair's done-when ("passes its test and has a template") is met in automated tests on real Jolt.** The template is the new sample `platformer-profile-3d.json`, in the gallery.
- **Manually Not Verified (Headless Environment):** nobody has played it with the keyboard.
- **M3b.7 as a whole is not done.** Four of the 33 pairs play. Third-person and first-person shooter, racing and the rest are not started.

- Branch: `phase-12-3d-foundation`
- Starting `git rev-parse --short HEAD`: `0469ceb`. Ending: `0469ceb` (no commit was made).
- At the start, the working tree held this session's uncommitted earlier work (M3.3 to M1.2 stage 2, listed in their reports).
- **Approvals:** TD-088 (approved 2026-11-15) covers further pairs as their own slices. It does not specify them, so I chose this pair's design (section 2) and recorded it in TD-088's implementation notes. No new module, dependency or file format: the scene file is unchanged and the profile is code.

## 1. Acceptance criteria

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | The pair is in the registry, playable, with its reason | **Met** | `GameGenres.standard()`: Platformer + 3D is PLAYABLE with `platformer3D`. Its reason names the chase camera, running, jumping and the return after a fall (`GameGenresTest`, +2) |
| 2 | A camera suited to the genre | **Met** | A chase camera behind and above the player: 650 units back, 26 degrees down, so a landing can be judged (Action RPG: 520, 18 degrees). Q and E turn it. In the game runtime's frame it sits behind and well above the Jumper (`PlatformerChaseProfileOnJoltTest`; the screenshot below) |
| 3 | Running and jumping between platforms | **Met** | The keys move the player relative to the camera at 500 units per second. A jump starts at 700 units per second (about 2.5 m high); Action RPG's starts at 600. **An autopilot crosses the sample's four gaps to the goal with keys alone:** it runs to the edge nearest the next stone, presses Space once, then steers in the air. It needs one jump per gap, and nothing falls |
| 4 | Falling is not the end | **Met** | A fall of 12 m below where Play started the player puts it back there, facing as it started. Walking off the start platform's side falls 1,200 units, within 40, and then the Jumper stands on the start again with the camera following |
| 5 | No script needed | **Met** | The sample has no scripts and no graphs; the test checks the Jumper has neither. The profile's parts are never saved |
| 6 | A template | **Met** | `platformer-profile-3d.json` is in the gallery (desktop only, with a preview). It is rated "Checks pending", like the other script-free samples |
| 7 | A pair is playable only with its acceptance test | **Met** | `PlayableGenrePairsTest` lists `PlatformerChaseProfileOnJoltTest` for `platformer/3d` |

**Mutation checks** (each restored afterwards; `cmp` identical):

| Change | What failed |
|---|---|
| Jump speed 450 instead of 700 | The crossing test, on "jumps rise more than 2 m" (it rose 1.2 m). The course alone did not catch it: the character's 30-unit step height let it climb onto the 120-unit stone |
| Fall return that never fires | The fall test: "put back once, after 600 frames: expected 1 but was 0" |
| No fall return added at all | All three tests (the component is missing). This is a weak check, so I also ran the one above |

## 2. Design (decided here, as TD-088 leaves each pair to its slice)

- **Reuse what exists.**
  - The camera is TD-088's `THIRD_PERSON` rig, with its own distance, pivot height and tilt.
  - The movement is `GROUND_WALK`, with its own speed and jump. Running relative to the camera and turning to face the way it runs are the same as Action RPG's.
  - What makes it a platformer here is the level and the jump, not new movement code.
- **One addition: `GameTestProfile.fallRespawnDepth`** (0 means never).
  - **Why:** a 3D platform level has gaps, and without a rule the player falls forever. TD-088's ground check only looks before Play.
  - **How:** `PlayProfiles.apply` adds a runtime-only `FallRespawner`. It records the start when the profile is applied, after the spawn point has placed the player. Once the player is that far below the start, it teleports the player back (`PhysicsSystem3D.teleportCharacter`, which clears the fall speed) and restores its facing.
  - **Scripted players too:** it is a genre rule, not movement, so it is added even when the author's script moves the player.
  - **Other profiles:** the old seven-part constructor gives 0, so the other three are unchanged. A depth without `CHARACTER_3D` is refused.
- **`GroundWalkMover.faceCurrentRotation`:** after the fall return turns the player back, the mover takes that rotation as its facing, rather than turning back from where it faced before the fall.
- **The numbers** are named constants in `GameGenres`, each with a one-line reason:
  - `CHASE_DISTANCE` 650, `CHASE_PIVOT_HEIGHT` 60, `CHASE_PITCH` 26;
  - `PLATFORMER_3D_RUN_SPEED` 500, `PLATFORMER_3D_JUMP_SPEED` 700;
  - `PLATFORMER_3D_FALL_DEPTH` 1200.
- **Not chosen now:**
  - coyote time and jump buffering;
  - a jump whose height depends on how long Space is held;
  - checkpoints;
  - a goal or win.

  Each needs new movement or game-rule code, and none is needed for the pair to play. They are listed as next steps.

## 3. What was built

- **`engine-scene` `play`:**
  - `GameTestProfile.fallRespawnDepth`, with `respawnsAfterFalls()` and the seven-part constructor;
  - `GameGenres.platformer3D` and its playable entry, with the constants above.
  - I also removed a duplicated, orphaned Javadoc block that sat above `actionRpg25D` (it belonged to `platformer25D`).
- **`engine-render` `play`:**
  - `FallRespawner` (new);
  - `PlayProfiles.apply` adds it;
  - `GroundWalkMover.faceCurrentRotation`.
- **Sample `platformer-profile-3d.json`** (from `tools/make_profile_samples.py`, which regenerated its three earlier samples byte-identical to the copies the web build had synced):
  - a start platform, three stones that rise 120 and then step 350 to the side, and a goal platform with a flag that has no collider;
  - nothing underneath;
  - a Jumper with a Character Controller 3D and a Player;
  - a camera with no rig;
  - Game Mode 3D, Game Genre Platformer.
- **Lists:**
  - the gallery entry and its preview;
  - `assets/samples/README.md`;
  - the MC.7 migration plan (`keep-primitives`: made of shapes, nothing to replace);
  - the web test's list of known unsupported components (`PlayerComponent`: the web player runs no profiles).

## 4. Commands and results

| Command | Exit | Result |
|---|---|---|
| `./gradlew :engine-scene:test --tests "*GameGenresTest" :engine-render:test --tests "*.play.*" :engine-desktop:test --tests "*PlatformerChaseProfileOnJoltTest" --continue` | 0 | 8, 21 and 3 pass. The acceptance test passed the first time |
| The three mutation checks (section 1) | 1 each | As listed; restored |
| `./gradlew :engine-desktop:installDist :engine-desktop:gameScriptsClasses`, then `python tools/make_template_previews.py platformer-profile-3d` | 0, 0 | Preview written and looked at |
| `GDXUNITY_SCREENSHOT=... ./gradlew :engine-desktop:run --args="--scene samples/platformer-profile-3d.json --smoke-test=120"` | 0 | "120 of 120 frames, 0 runtime error(s)" |
| `GDXUNITY_UPDATE_AUDIT=1 ./gradlew :editor-desktop:test --tests "*.audit.*" --tests "*WebUxContractTest"` | 1, then 0 | `SampleInventoryTest` pins 29 3D samples; now 30. Then the tables regenerated, and a run without the variable passes |
| `./gradlew :engine-scene:cleanTest :engine-scene:test :engine-render:cleanTest :engine-render:test :engine-desktop:cleanTest :engine-desktop:test :editor-core:cleanTest :editor-core:test :editor-desktop:cleanTest :editor-desktop:test --continue` | stopped | The first three modules all pass (table below). `editor-core` had 5 failures, all mine (section 5); I fixed the tests but **have not rerun them**. **The run was stopped by Claude Code during `editor-desktop` because the machine was low on memory**, so `editor-desktop`'s full suite has not run. Its audit tests did pass (row above) |
| `node scripts/sync-samples.mjs && npx vitest run` (engine-babylon) | 1 | 508 of 509. The one failure is `shouldBuildEverySampleSceneOfTheDesktopEditor` timing out at 5 s while the Gradle suites ran on the same machine. **Not rerun alone yet**, so it is not shown to be only the load |
| `./gradlew :editor-core:cleanTest :editor-core:test` (on its own, after the test fix) | 0 | 539, 1 skipped as before |
| `./gradlew :editor-desktop:cleanTest :editor-desktop:test` (on its own) | 0 | 181 |
| `npx vitest run` (engine-babylon, on its own) | 0 | 509 of 509; the timeout was the load of the parallel Gradle run |

| Module | Before | After |
|---|---|---|
| `engine-scene` | 228 | **230** (all pass) |
| `engine-render` | 199 | **201** (all pass) |
| `engine-desktop` | 147 | **150** (all pass) |
| `editor-core` | 539 (1 skipped) | 539 (1 skipped), all pass after the test fix (5 had failed before it, section 5) |
| `editor-desktop` | 181 | 181, all pass (rerun on its own) |
| engine-babylon (vitest) | 508 | **509**, all pass (the new sample is one more scene built), rerun on its own |

The first combined run was stopped by Claude Code for low memory during `editor-desktop`; each remaining suite was then run on its own (rows below the table's first runs).

**Screenshots** (looked at):
- [m3b-7-2/runtime-platformer-profile-3d.png](m3b-7-2/runtime-platformer-profile-3d.png): the game runtime at frame 120. The camera is behind and above the Jumper (blue) on the green start platform; the three stones lead to the yellow goal with its red flag; nothing is below; the help text is light and on one line.
- `assets/templates/previews/platformer-profile-3d.png`: the gallery preview, the same view.

## 5. Failures found and fixed

- **Tests that used Platformer + 3D as their example of a planned pair** (5 in `editor-core`):
  - `PlayToolsTest` (1);
  - `GenreChoicesTest` (3);
  - `ProfilePlayStepTest` (1).

  They now use Platformer + 2D, still planned, or check that Platformer plays in 2.5D and 3D. `GenreChoicesTest` also checks that Adventure is shown as planned in 3D.
- **`SampleInventoryTest`'s pin of 29 3D samples, and `TemplateRatingsTest`'s 45 samples and 17 "Checks pending":** now 30, 46 and 18.

## 6. Diff review

- **New:**
  - `FallRespawner`;
  - `PlatformerChaseProfileOnJoltTest`;
  - `assets/samples/platformer-profile-3d.json` and `assets/templates/previews/platformer-profile-3d.png`;
  - this report and `docs/reports/m3b-7-2/`.
- **Changed (code):** `GameTestProfile`, `GameGenres`, `PlayProfiles`, `GroundWalkMover`, `tools/make_profile_samples.py`.
- **Changed (tests):**
  - `GameGenresTest` (+2), `PlayProfilesTest` (+2), `PlayableGenrePairsTest`;
  - `PlayToolsTest`, `GenreChoicesTest`, `ProfilePlayStepTest`;
  - `SampleInventoryTest`, `TemplateRatingsTest`;
  - web `sceneBuilder.test.ts`.
- **Changed (lists):** `assets/templates/gallery.json`, `assets/samples/README.md`, `docs/migration/sample-migrations.json`.
- **Docs:**
  - TD-088's implementation notes;
  - the chart (M3b.7 and the R3b row);
  - `FEATURE_TEST_MATRIX.md` (a row, and its regenerated tables);
  - `INDEX.md`;
  - the regenerated tables of `sample-asset-migration-inventory.md` and `fully-editable-template-audit.md`.

No other sample changed. No files outside this repository were touched. CRLF files kept their line endings.

## 7. Known limitations and next steps

- **Manual check for a person** (Windows):
  1. Project > New Game, "Platformer Profile 3D". The footer reads Platformer, playable in 3D.
  2. Press Play. The status line reads "Playing as Platformer / Side-scroller + 3D." and the view is behind and above the Jumper.
  3. Cross the stones with WASD and Space, and turn the camera with Q and E.
  4. Fall off on purpose: the Jumper is back on the start platform.
  5. Stop. The scene and undo history are as before.
- **No goal:** reaching the flag does nothing. Goals, checkpoints and a win are game rules, as for Action RPG's combat (later named slices).
- **Jump feel:**
  - there is no coyote time or jump buffering;
  - the jump height does not depend on how long Space is held.
- **Camera:** it turns only with keys; there is no mouse look.
- **Web:** the web player runs no profiles (desktop only, like the other three pairs).
- **Next M3b.7 slices** (each its own design and acceptance test):
  - Third-person Shooter + 3D: needs aiming and a shot, so a first combat rule;
  - First-person Shooter + 3D: needs a first-person camera kind;
  - Racing + 3D: needs vehicle movement;
  - Platformer + 2D: needs a 2D character controller.

## 8. Git

No stage, commit, push, reset, stash, branch switch or other destructive Git operation was performed.
