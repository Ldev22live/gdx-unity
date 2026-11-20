# Roadmap M3b.7, Slice 3: First-person Shooter + 3D (2026-11-19)

Milestone M3b.7 of [milestone-chart.md](../milestone-chart.md) (roadmap R3b) gives each genre and mode pair its own slice. Earlier slices:
- slice 1: the agent tools ([report](m3b-7-1-genre-and-play-agent-tools.md));
- slice 2: Platformer + 3D ([report](m3b-7-2-platformer-3d.md)).

**This slice makes First-person Shooter + 3D playable.** **Status:**
- **Implemented and Integrated.** The footer's Game Genre offers it as playable in 3D, and editor Play and the game runtime run the same profile step.
- **The pair's done-when ("passes its test and has a template") is met in automated tests on real Jolt.** The template is `first-person-range-3d.json`.
- **Manually Not Verified (Headless Environment):** nobody has played it with the keyboard.
- **M3b.7 as a whole is not done.** Five of the 33 pairs play. Third-person Shooter, Racing and the rest are not started.

- Branch: `phase-12-3d-foundation`
- Starting `git rev-parse --short HEAD`: `0469ceb`. Ending: `0469ceb` (no commit was made).
- At the start, the working tree held this session's uncommitted work, slice 2 included (listed in their reports).
- **Approvals:** TD-088 (approved 2026-11-15) leaves each pair to its own slice without specifying it. I chose this design (section 2) and recorded it in TD-088's implementation notes. No new module, dependency or file format: the scene file is unchanged.

## 1. Acceptance criteria

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | The pair is in the registry, playable, with its reason | **Met** | `GameGenres.firstPersonShooter3D`; its reason names the view, the keys and the shot, and says health, enemies and ammunition come later (`GameGenresTest`, +2). Third-person Shooter + 3D stays planned |
| 2 | A first-person camera | **Met** | The camera sits at the Shooter's eyes (55 above its centre) and looks level along the way it faces. Turning the Shooter turns the view. The Shooter's own body is not seen (screenshot) |
| 3 | Shooter movement | **Met** | Q and E turn at 120 degrees per second. W and S walk the way it faces. A and D step sideways without turning; walking moves the view, turning it does not. After a 90-degree turn, W walks the new way. Space jumps |
| 4 | A shot | **Met** | F or the left Ctrl key shoots once per press: a ray from the camera along the view, up to 50 m, passing through the Shooter's own shape. The middle crate it aims at is knocked off the shelf and the others stay. A 12-degree turn aims at the right-hand crate, which the second key knocks off. Facing the east wall, the shot stops at the wall and knocks nothing |
| 5 | No script needed | **Met** | The sample has no scripts or graphs. A player with its own script gets neither the profile's movement nor its shot (`PlayProfilesTest`). The profile's parts are never saved |
| 6 | A template | **Met** | `first-person-range-3d.json` is in the gallery (desktop only, with a preview) and rated "Checks pending" |
| 7 | A pair is playable only with its acceptance test | **Met** | `PlayableGenrePairsTest` lists `FirstPersonRangeProfileOnJoltTest` for `first-person-shooter/3d` |

**Mutation checks** (each restored afterwards; `cmp` identical):

| Change | What failed |
|---|---|
| The shot stops at the Shooter's own shape | The shot test: "expected Crate Middle but was Shooter" |
| Walking ignores the way the Shooter faces | The movement test: "W now walks along +X" |

## 2. Design (decided here)

- **The camera reuses the rig's existing `FIRST_PERSON` mode** through a new `CameraSetup.Kind.FIRST_PERSON`.
  - The rig sits at the target plus its eye height and looks where the target faces, so the mover turns the view by turning the player.
  - The profile's camera distance is unused here, and documented so.
- **New movement kind `FIRST_PERSON_WALK` with `FirstPersonMover`.**
  - Action RPG's `GroundWalkMover` turns the player toward where it walks, so stepping sideways would spin a first-person view.
  - The new mover turns only with the turn keys and walks relative to its own facing. It reuses `GroundWalkMover.walkDirection` and `turnToward`.
- **The shot: `GameTestProfile.ShotSetup(range, knockSpeed)`.** It is optional; the record's `shot` is null without one, and `hasShot()` says which.
  - **What fires:** `ProfileShooter` on the player fires on `InputAction.FIRE`, new and mapped to F and the left Ctrl key, because the profile's input is keys only. It is the same key the physics sandbox uses for its push, but each scene has only one or the other.
  - **What it hits:**
    - A dynamic body gains 6 m/s along the shot (impulse = speed × mass, so light and heavy crates move alike).
    - Anything else stops the shot and is only reported (`getLastHit`).
    - A scene with no camera or no 3D physics is an error, not a silent miss.
  - **Scripted players:** the shooter is added only with the profile's movement, so a scripted player shoots by its own script.
- **A shot needs 3D physics,** and a profile that says otherwise is refused.
- **The numbers** are named constants in `GameGenres`:
  - `FIRST_PERSON_EYE_HEIGHT` 55;
  - `SHOOTER_WALK_SPEED` 450, `SHOOTER_JUMP_SPEED` 500, `SHOOTER_TURN_DEGREES_PER_SECOND` 120;
  - `SHOT_RANGE` 5000, `SHOT_KNOCK_SPEED` 600.
- **Not chosen now** (a pair is playable without them; later slices):
  - health, damage, enemies and ammunition;
  - mouse look and looking up or down;
  - a muzzle flash or hit marker.

## 3. What was built

- **`engine-scene` `play`:**
  - `InputAction.FIRE` and its default keys;
  - `GameTestProfile`: `ShotSetup`, the `shot` field with `hasShot()`, `CameraSetup.Kind.FIRST_PERSON`, `MovementSetup.Kind.FIRST_PERSON_WALK`, and an eight-part constructor so the existing profiles are unchanged;
  - `GameGenres.firstPersonShooter3D` and its playable entry.
- **`engine-render` `play`:**
  - `FirstPersonMover` and `ProfileShooter` (both new, runtime only);
  - `ProfileCameraRig`'s first-person case;
  - `PlayProfiles.apply` adds the mover and the shooter.
- **Sample `first-person-range-3d.json`** (`tools/make_profile_samples.py`, which regenerated its four other samples byte-identical):
  - a floor and four walls;
  - a shelf with three crates of 5 kg (dynamic Rigid Body 3D) 1,500 units ahead;
  - a Shooter with a Character Controller 3D and a Player;
  - a centred "+" crosshair text;
  - a camera with no rig.
- **Lists:**
  - the gallery entry ("Shooter") and its preview;
  - `assets/samples/README.md`;
  - the MC.7 plan (`keep-primitives`);
  - the web test's known unsupported components (`PlayerComponent`).

## 4. Commands and results

| Command | Exit | Result |
|---|---|---|
| `./gradlew :engine-scene:test --tests "*GameGenresTest" --tests "*InputMappingTest" :engine-render:test --tests "*.play.*" :engine-desktop:test --tests "*FirstPersonRangeProfileOnJoltTest" --continue` | 1, then 0 | Stepping sideways moved 7.5 units along z, one frame of the W before it. Physics steps before the mover reads the keys, the same one-frame latency every profile mover has. The test now allows one frame of walking; then 3 of 3 pass |
| The two mutation checks | 1 each | As listed; restored |
| `installDist`, `gameScriptsClasses`, `make_template_previews.py first-person-range-3d` | 0 | Preview written |
| `GDXUNITY_SCREENSHOT=... ./gradlew :engine-desktop:run --args="--scene samples/first-person-range-3d.json --smoke-test=120"` | 0 | "120 of 120 frames, 0 runtime error(s)" |
| `GDXUNITY_UPDATE_AUDIT=1 ./gradlew :editor-desktop:test --tests "*.audit.*" --tests "*WebUxContractTest"`, then without the variable | 0, 0 | Tables regenerated, then current |
| `./gradlew :<module>:cleanTest :<module>:test`, **one module at a time** (the earlier combined run had been stopped for low memory) | 0 each | See the table |
| `node scripts/sync-samples.mjs && npx vitest run` (engine-babylon, on its own) | 0 | 510 of 510 |

| Module | Before (slice 2) | After |
|---|---|---|
| `engine-scene` | 230 | **232** |
| `engine-render` | 201 | **203** |
| `engine-desktop` | 150 | **153** |
| `editor-core` | 539 (1 skipped) | 539 (1 skipped) |
| `editor-desktop` | 181 | 181 (pins: 47 samples, 19 "Checks pending", 31 3D samples) |
| engine-babylon (vitest) | 509 | **510** (one more sample built) |

**Screenshots** (looked at):
- [m3b-7-3/runtime-first-person-range-3d.png](m3b-7-3/runtime-first-person-range-3d.png): the game runtime at frame 120. The view is from the Shooter's eyes, level, with the crosshair on the middle crate. Three crates are on the shelf; the walls and floor are in view. The Shooter's own body is not drawn in the way. The help text is on one line. A thin light line shows along the near edge of the east wall.
- `assets/templates/previews/first-person-range-3d.png`: the gallery preview.

## 5. Failures found and fixed

- The test's sideways check allowed no drift; it now allows one frame of walking, with the reason in a comment and a named constant.

## 6. Diff review

- **New:**
  - `FirstPersonMover`, `ProfileShooter`;
  - `FirstPersonRangeProfileOnJoltTest`;
  - `assets/samples/first-person-range-3d.json` and its preview;
  - this report and `docs/reports/m3b-7-3/`.
- **Changed (code):** `InputAction`, `InputMapping`, `GameTestProfile`, `GameGenres`, `ProfileCameraRig`, `PlayProfiles`, `tools/make_profile_samples.py`.
- **Changed (tests):**
  - `GameGenresTest` (+2), `InputMappingTest`, `PlayProfilesTest` (+2), `PlayableGenrePairsTest`;
  - `TemplateRatingsTest`, `SampleInventoryTest`;
  - web `sceneBuilder.test.ts`.
- **Changed (lists):** `gallery.json`, `assets/samples/README.md`, `sample-migrations.json`.
- **Docs:**
  - TD-088's notes, the chart, `FEATURE_TEST_MATRIX.md` (a row and regenerated tables) and `INDEX.md`;
  - the regenerated inventory and template audit tables.

No files outside this repository were touched. CRLF files kept their line endings.

## 7. Known limitations and next steps

- **Manual check for a person:**
  1. Project > New Game, "First-person Range"; press Play.
  2. Walk, strafe and turn.
  3. Shoot each crate off the shelf with F or Ctrl.
  4. Stop.
- **Not built:**
  - health, damage, enemies, ammunition and reloading;
  - mouse look and looking up or down (the view is level);
  - sound, a muzzle flash and a hit marker.
- **The thin light line** along the east wall's near edge in the screenshot was not investigated. It looks like a shadow or depth edge where the wall meets the floor.
- **Web:** the web player runs no profiles.
- **Next M3b.7 slices:**
  - Third-person Shooter + 3D: the third-person camera and this shot, with an aim offset;
  - Racing + 3D: vehicle movement;
  - Platformer + 2D: a 2D character controller.

## 8. Git

No stage, commit, push, reset, stash, branch switch or other destructive Git operation was performed.
