# Roadmap M3c.2: Player Prefab and Spawn Point (2026-11-15)

Milestone M3c.2 of [milestone-chart.md](../milestone-chart.md) (roadmap R3c), built to [TD-088](../TECHNICAL_DECISIONS.md). TD-088 makes the player's authored position the spawn and leaves "a separate spawn point" to this milestone. **Status:**
- **Implemented and Integrated.**
- **The done-when is met in a test on real Jolt:** "placing it and pressing Play works, or the missing parts are named".
- **Manually Not Verified (Headless Environment):** the World strip and its palette were not clicked.

- Branch: `phase-12-3d-foundation`
- Starting `git rev-parse --short HEAD`: `0469ceb`. Ending: `0469ceb` (no commit was made).
- At the start, the working tree held this session's uncommitted M3.3 to M3b.7 slice 1 work (listed in their reports).
- **No new module or dependency.** The new component (`SpawnPointComponent`) is the one TD-088 anticipates; its design is in TD-088's implementation notes.

## 1. Acceptance criteria

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | An editable character prefab | **Met** | `assets/prefabs/characters/Player.prefab` is an ordinary prefab (TD-027): a root with a Character Controller 3D and Player, and a body of shapes (legs, body, head, and a nose that shows which way it faces). An instance's parts are edited in Details like any prefab instance. Editing the shared prefab from the editor is M1.6's |
| 2 | With spawn | **Met** | New `SpawnPointComponent` (Add Component > Game > Spawn Point, one per scene) and `Spawn Point.prefab`. In Play the player starts on it, standing, facing its way (`CharacterPlacementTest`: placed at (−400, 0, 100) turned 90 degrees, Hero starts at (−400, 71, 100) facing 90 degrees and stands on the level) |
| 3 | Collision, movement and camera | **Met** | The prefab's Character Controller 3D collides. The genre profile supplies movement and camera to its Player (M3b.3, M3c.1); after placing, Play adds the walker |
| 4 | Missing requirements shown | **Met** | With only a spawn point, Play says "No player" (with no fix: there's no likely entity to make the player). Two spawn points are named with their names (`PlayProfilesTest`). The strip refuses a second player or spawn point, naming the first. The rest of the checks came with M3b.2, M3b.3 and M3b.5 |
| 5 | Placing it and pressing Play works | **Met** | `CharacterPlacementTest` on a new level:<br>1. place the Player, then the Spawn Point, through `LevelSession` (what the World strip's Characters drives);<br>2. add a camera, set Action RPG + 3D, press Play: it starts at the spawn;<br>3. Stop: the authored scene is unchanged.<br>**Mutation check:** with the spawn placement off, 1 of 4 failed; restored |

## 2. What was built

- **`engine-scene` `play`:**
  - `SpawnPointComponent`, with no fields, and `SpawnPointComponentSerializer` (registered in `GameComponentSerializers`).
  - It draws nothing in the game.
- **`engine-render`:** `PlayProfiles`:
  - **Moves the player to the spawn point:** after validation and before the ground check, so the check and the character both start there. The player is lifted by half its capsule and turned to the spawn's yaw; a player with a parent is handled through the parent's matrix.
  - **Names a second spawn point.**
  - **Adds `spawnPoints(scene)`.**
  - **Adds a status note:** "Player starts at Spawn Point."
- **`editor-terrain`:** `LevelSession`:
  - New `Focus.CHARACTERS`, which offers only Place.
  - It lists `CHARACTER_FOLDER` (`prefabs/characters`).
  - A character prefab is placed without the objects' random turn and size. When its root has a Character Controller 3D, it is lifted to stand on the ground, and the placed entity is selected.
  - A second player or spawn point is refused with the first one's name.
  - A chosen object that isn't in the new focus's folder is cleared on a focus change.
- **`editor-core`:**
  - `WorldStrip`: Characters works in 2.5D and 3D. 2D still says it is planned, and why: there is no 2D character controller.
  - `ComponentCatalog`: Spawn Point under Game, one per scene.
- **`editor-desktop`:**
  - `WorldStripBar` opens the Characters focus.
  - `LevelPalette` shows "Place" and the characters without the ground row.
  - `SpawnPointComponentEditorProvider` gives a two-line note in Details.
  - `Collider3DRenderer` always marks spawn points in the editor's 3D view: a ring, a facing arrow and an upright post. A spawn point has no shape, so it would otherwise be invisible.
- **Prefabs:** `tools/make_level_prefabs.py` now writes the two character prefabs too. Its five level prefabs came out unchanged.

## 3. Commands and results

| Command | Exit | Result |
|---|---|---|
| `python tools/make_level_prefabs.py` | 0 | 5 level prefabs (unchanged) and 2 character prefabs |
| `./gradlew :editor-terrain:test --tests "*CharacterPlacementTest" --tests "*LevelEditingTest"` | 0 | 4 and 9 (passed the first time) |
| Spawn placement mutation check | 1 | 1 of 4 failed, as intended; restored |
| `./gradlew :<module>:cleanTest :<module>:test` for `engine-scene`, `engine-render`, `engine-desktop`, `editor-core`, `editor-terrain`, `editor-desktop` | 0, 0, 1, 0, 0, 1, then 0 | Two expected failures: `GameComponentSerializersTest` counts the registered types (36, now 37), and `web-ui-ux-spec.md`'s generated World strip table still said Characters was planned (regenerated with `GDXUNITY_UPDATE_AUDIT=1`). Then 182, 167, 110, 497 (1 skipped, as before), 24 and 160 |
| `GDXUNITY_SCREENSHOT=... GDXUNITY_EDITOR_ASSISTANT=0 ./gradlew :editor-desktop:run --args="--scene <scratch copy of action-rpg-valley-3d.json with a Spawn Point> --smoke-test"` | 0 (twice) | 180 frames, 0 errors. The first frame didn't show the spawn: it was outside the view. I moved the scratch spawn between the crate and the boulder |

| Module | Before (M3b.7 slice 1) | After |
|---|---|---|
| `engine-render` | 166 | 167 |
| `editor-core` | 496 | 497 |
| `editor-terrain` | 20 | 24 |
| Others changed | unchanged counts | all pass |

**Screenshot** (looked at):
- [m3c-2/editor-spawn-marker.png](m3c-2/editor-spawn-marker.png): the valley in the editor's default side view. The spawn marker stands between the crate and the boulder: its post upright, its ring seen edge-on as a line. In this view the marker's light blue is hard to tell from white.
- The Characters palette and a placed Player were not captured: there is no testing aid to open the strip, and nothing was clicked.

## 4. Failures found and fixed

- The serializer count and the generated World strip table (section 3).
- My first Bash edit script broke on apostrophes, a known shell quirk here. Nothing was changed by it; I reran it as a file.

## 5. Diff review

- **New:**
  - `SpawnPointComponent`, `SpawnPointComponentSerializer`, `SpawnPointComponentEditorProvider`;
  - `CharacterPlacementTest`;
  - `assets/prefabs/characters/Player.prefab`, `Spawn Point.prefab`;
  - this report and `docs/reports/m3c-2/`.
- **Changed:**
  - `PlayProfiles`, `LevelSession`, `WorldStrip`, `ComponentCatalog`;
  - `GameComponentSerializers`, `InspectorPanel`, `WorldStripBar`, `LevelPalette`, `Collider3DRenderer`;
  - `tools/make_level_prefabs.py`;
  - tests: `PlayProfilesTest`, `ComponentCatalogTest`, `GameComponentSerializersTest`.
- **Docs:**
  - the chart (M3c.2, R3c);
  - `INDEX.md`, `FEATURE_TEST_MATRIX.md`;
  - TD-088 implementation notes;
  - the regenerated `web-ui-ux-spec.md` table.

No files outside this repository were touched; the scene with a spawn point was a scratch copy outside the repository. CRLF files kept their line endings.

## 6. Known limitations and next steps

- **Manual check for a person** (Windows):
  1. New scene in 3D; World strip > Terrain > New Level.
  2. World strip > Characters, then click the ground with Spawn Point, then with Player. The player stands on the ground; the spawn shows as a blue ring with an arrow.
  3. Turn the spawn point. Choose Action RPG and press Play: the player starts on the spawn point facing the arrow's way.
  4. Place a second Player: it is refused.
- **2D characters are not offered:** there is no 2D character controller.
- **The strip places only on a terrain level.** A ground made of boxes (as in the platformer sample) needs Add Component or the Content Browser.
- **A spawn point can't be clicked in the 3D view**, because it has no shape. It is selected after placing, or from the Outliner.
- **The player prefab is simple shapes**, not a rigged character. A character from the character creator (TD-053) can be made the player with Player and a Character Controller 3D.
- **Editing the shared prefab from the editor** is M1.6's.
- **Next in R3c, in the chart's order:**
  - M3c.3, Action RPG + 2.5D, a new genre and mode profile validated on its own;
  - M3c.4, the Edit 3D Model hand-off;
  - M3c.5, the regression project and checklist, which needs M3c.1 and M3c.2, both now built.

## 7. Git

No stage, commit, push, reset, stash, branch switch or other destructive Git operation was performed.
