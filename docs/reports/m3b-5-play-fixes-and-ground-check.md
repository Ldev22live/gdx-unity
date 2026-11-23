# Roadmap M3b.5: Play Fixes and the Ground Check (2026-11-15)

Milestone M3b.5 of [milestone-chart.md](../milestone-chart.md) (roadmap R3b), built to [TD-088](../TECHNICAL_DECISIONS.md). Its Play step (resolve, check and apply the profile on the copy; refusals; Stop restoring the scene and undo history) was built with [M3b.3](m3b-3-platformer-25d-profile.md). This report covers the two parts left:
- the safe fixes as buttons;
- the ground check at Play.

**Status:**
- **Implemented and Integrated.** Seen in the running editor.
- **Manually Not Verified (Headless Environment):** the fix button was not clicked. Clicking it is tested headless.

- Branch: `phase-12-3d-foundation`
- Starting `git rev-parse --short HEAD`: `0469ceb`. Ending: `0469ceb` (no commit was made).
- At the start, the working tree held this session's uncommitted M3.3 to M3b.4 and M3c.1 work (listed in their reports).
- No new module, dependency or decision; TD-088 already describes both parts.

## 1. Acceptance criteria

The chart's done-when is "tests for Play and Stop lifecycle, runtime isolation, editor restoration, save and load". The lifecycle, isolation and restoration tests came with M3b.3. This milestone adds the rest of what the row asks for:

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | Prerequisites checked with concise, actionable messages: player, controls, camera, **collision** | **Met** | Player, character and camera checks came with M3b.2 and M3b.3. **New:** a ray down from the player must reach something solid within 100 m, or Play stops with "Nothing under Hero to stand on: add a Terrain Collider 3D or a Box Collider 3D below it, or move it over the ground." Editor Play: `ProfilePlayStepTest`. Game runtime: `ActionRpgValleyProfileOnJoltTest`, Hero moved far off the valley. **Mutation check:** with the check off, 1 editor and 2 runtime tests failed; restored |
| 2 | Safe fixes where possible | **Met** | A refused Play's fixes show as buttons beside Customize, up to three. A click is one undoable command on the authored scene; the button goes away and the status bar says "Done: Make Runner the player (undoable). Press Play again." A fix never starts Play, and undo takes it back (`ProfilePlayStepTest`). Seen in the editor (section 3) |
| 3 | Play and Stop lifecycle, isolation and restoration | **Met (M3b.3)** | The copy alone gets the profile; Stop restores the scene, selection and undo history (`ProfilePlayStepTest`, `PlayModeControllerTest`, 18 tests, all pass) |
| 4 | Save and load; older files load unchanged | **Met (M3b.2, M3b.4)** | `SceneGenreSerializationTest` and `GenreChoicesTest` |
| 5 | The same controls and rules for 2D, 2.5D and 3D | **Partly** | One step, one set of messages and one fix UI for every mode. Only 2.5D and 3D have a playable pair; 2D pairs are refused with their reason |
| 6 | A customised asset replays with no manual reconnecting | **Not built** | Needs project-owned editable copies (roadmap R7a) |
| 7 | Apply Changes | **Not built, by decision** | Not approved (decision 17); Play changes are discarded |

## 2. What was built

- **`engine-scene`:** `PhysicsSystem3D.prepareBodies()` builds the bodies now, as the first update would, without stepping, so a ray before the first frame sees the world.
- **`engine-render`:** `PlayProfiles.start` runs the ground check after validation and before applying the profile. It applies to profiles that walk a character.
  - **The ray starts just below the player's capsule.** The first build started it at the centre, and the check refused every scene. A ray that starts inside a shape reports that shape at once.
  - **When the bodies can't be built yet, the check is skipped** and the start message says so. For example, a mesh collider in the game runtime needs the window, which opens after start.
- **Where the shapes come from at Play start:**
  - **Editor Play:** the copy now takes the authored scene's mesh and terrain sources. Before, it got them only once the renderer showed it, which is after the Play step.
  - **Game runtime:** `GameSession` reads terrains from the project itself before physics starts. The renderer replaces that source with its own once it is created.
- **`editor-core`:** `ProfilePlayStep.getLastFixes`, `applyFix` and `clearProblems`.
- **`editor-desktop`:**
  - `ProfileFixButtons` in the footer, left of Customize. Opening another scene forgets the offer.
  - **A layout bug that predates this milestone:** the Customize gateway's hidden "Back to World" kept its space in World, which put the fix button about 100 pixels left of Customize. Its space now collapses while it is hidden, and it shows again in other workspaces (seen in Animations).

## 3. Commands and results

| Command | Exit | Result |
|---|---|---|
| `./gradlew :engine-desktop:test --tests "*ActionRpgValleyProfileOnJoltTest" --tests "*PlatformerSideProfileOnJoltTest" --tests "*TerrainValleyOnJoltTest" :editor-core:test --tests "*ProfilePlayStepTest" --tests "*PlayModeController*" --continue` | 1, then 0 | The first run refused every profile scene with "Nothing under …": the ray started inside the player's capsule. After the fix: 6, 4, 2, 6 and 18 pass |
| Ground check mutation check | 1 | 1 editor and 2 runtime tests failed, as intended; restored |
| `./gradlew :<module>:cleanTest :<module>:test` for `engine-scene`, `engine-render`, `engine-desktop`, `editor-core`, `editor-desktop`, `engine-physics`, one at a time | 0 each | 182, 166, 110, 491 (1 skipped, as before), 160, 60 |
| `GDXUNITY_SCREENSHOT=... GDXUNITY_EDITOR_ASSISTANT=0 ./gradlew :editor-desktop:run --args="--scene <copy of action-rpg-valley-3d.json without Player> --play --smoke-test"` | 1 (expected) | The 2 logged errors are the expected refusal ("Cannot play: No player …") and `--play` reporting it. Run four times while fixing the layout |
| `GDXUNITY_SCREENSHOT=... GDXUNITY_EDITOR_TAB=Animations ... --scene samples/action-rpg-valley-3d.json --smoke-test` | 0 | 180 frames, 0 errors |
| `./gradlew :engine-desktop:run --args="--scene samples/<s>.json --smoke-test=120"` for `action-rpg-valley-3d`, `platformer-profile-25d`, `hopper-3d` | 0 each | "120 of 120 frames, 0 runtime error(s)". The runtime now builds the ground at start |

| Module | Before (M3b.4) | After |
|---|---|---|
| `engine-desktop` | 108 | 110 |
| `editor-core` | 489 | 491 |
| Other changed modules | unchanged counts | all pass |

The modules not listed are untouched since M3c.1, when all 23 passed.

**Screenshots** (looked at):
- [m3b-5/fix-button.png](m3b-5/fix-button.png): after the refused Play, the footer reads "Make Hero the player" (amber), then Customize, Play, Pause, Stop. The first frames had the button about 100 pixels left of Customize, because of the hidden Back to World; then it touched Customize. Both are fixed.
- [m3b-5/back-to-world-animations.png](m3b-5/back-to-world-animations.png): in the Animations workspace, "Back to World" shows before Customize, as before.

## 4. Failures found and fixed

- **The ground check refused everything at first.** Its ray started at the player's centre, inside its own capsule. It now starts below the capsule, and still steps past the player's own shape if it meets it.
- **Layout:** the hidden Back to World kept its space; the fix button needed a gap before Customize.

## 5. Diff review

- **New:**
  - `ProfileFixButtons`;
  - tests in `ProfilePlayStepTest` (+2) and `ActionRpgValleyProfileOnJoltTest` (+2);
  - this report and `docs/reports/m3b-5/`.
- **Changed:**
  - `PhysicsSystem3D` (`prepareBodies`), `PlayProfiles` (ground check);
  - `PlayModeController` (the copy's mesh and terrain sources), `ProfilePlayStep`, `GameSession` (terrains from the project);
  - `EditorFooter`, `EditorApplication` (footer wiring, Back to World collapse);
  - `ActionInventoryTest`.
- **Docs:**
  - the chart (M3b.5, R3b);
  - `FEATURE_TEST_MATRIX.md`, `INDEX.md`;
  - TD-088 implementation notes.

No files outside this repository were touched; the scene without a Player was a scratch copy outside the repository. CRLF files kept their line endings.

## 6. Known limitations and next steps

- **Manual check for a person** (Windows):
  1. In `samples/action-rpg-valley-3d.json`, remove Player from Hero and press Play. "Make Hero the player" appears beside Customize.
  2. Click it. The button goes away; Undo removes the Player again.
  3. Press Play with the Player back: the game starts.
  4. Move Hero far off the valley and press Play: "Nothing under Hero to stand on".
- **The ground check is skipped in the game runtime when the scene has a mesh collider**, because its shape needs the window. The start message says so. Editor Play checks it.
- **The ground check has no safe fix**; adding ground is a design choice.
- **Next in R3b:**
  - M3b.6, the World View default. It includes renaming Viewport to World, which is still an open owner question (decision 11).
  - M3b.7, agent tools for profiles.
- **Then R3c:** M3c.2 (player prefab and spawn point).

## 7. Git

No stage, commit, push, reset, stash, branch switch or other destructive Git operation was performed.
