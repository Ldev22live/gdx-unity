# Roadmap M3c.3: Action RPG + 2.5D, Isometric (2026-11-15)

Milestone M3c.3 of [milestone-chart.md](../milestone-chart.md) (roadmap R3c), built to [TD-088](../TECHNICAL_DECISIONS.md). **Status:**
- **Implemented and Integrated.** Action RPG + 2.5D is the third playable pair.
- **Validated on its own:** it has its own acceptance test on real Jolt and its own sample, and is not inferred from 3D.
- **Manually Not Verified (Headless Environment):** nobody played it with the keyboard.

**Design choice.** The chart's row allows "isometric or side-view". I asked the owner, who declined to choose and asked me to continue, so the choice is mine: **isometric**. It is the usual view of an action RPG, and it is clearly different from Platformer + 2.5D. Side-view would have played almost like the platformer until combat exists.

- Branch: `phase-12-3d-foundation`
- Starting `git rev-parse --short HEAD`: `0469ceb`. Ending: `0469ceb` (no commit was made).
- At the start, the working tree held this session's uncommitted M3.3 to M3c.2 work (listed in their reports).
- No new module or dependency.
- **One addition to TD-088's profile model**, `CameraSetup.Kind.ISOMETRIC`, recorded in its implementation notes.

## 1. Acceptance criteria

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | Camera | **Met** | An isometric camera:<br>• high and at a fixed angle (45 degrees round, 35 down), 1300 units from a point just above the hero;<br>• follows the hero and never turns: Q held for a second leaves it at 45.<br>Seen in the game runtime's frame. `ActionRpgIsometricProfileOnJoltTest` |
| 2 | Movement | **Met** | W walks up the screen (along (−1, 0, −1)) and D walks right (along (1, 0, −1)); the hero faces where it walks; Space jumps once per press. **Mutation check:** with the camera's yaw at 0 instead of 45, 2 of 3 tests failed; restored |
| 3 | Collision | **Met** | The courtyard's east wall stops the hero within Jolt's contact margin. The hero stands on the box ground |
| 4 | Passes its own tests; not inferred from 3D | **Met** | `ActionRpgIsometricProfileOnJoltTest` (3) is listed for `action-rpg/2.5d` in the truth test `PlayableGenrePairsTest`; 3D keeps its own test |
| 5 | Applied to the runtime copy only | **Met** | The same Play step as the other pairs (M3b.3, M3b.5); the played scene cannot be saved (checked) |

The sample's hero starts at its spawn point, not where it stands, so the acceptance test also checks M3c.2 on a second pair.

## 2. What was built

- **`engine-scene`:**
  - `CameraSetup.Kind.ISOMETRIC`.
  - `GameGenres.actionRpg25D`: the isometric camera, the same ground walking as 3D, jumping, and named constants (`ISOMETRIC_DISTANCE` 1300, `ISOMETRIC_PIVOT_HEIGHT` 40, `ISOMETRIC_PITCH` 35).
  - The pair is marked playable, with its reason.
- **`engine-render`:** `ProfileCameraRig` has the `ISOMETRIC` kind:
  - an `ORBIT` rig at `ISOMETRIC_YAW_DEGREES` (45), with no turn keys;
  - no occlusion, because pulling the camera in front of walls would jump a fixed high view about.
  - The ground walking already walks relative to the camera, so with a fixed camera it walks relative to the screen. No mover change was needed.
- **Sample `action-rpg-iso-25d.json`** (from `tools/make_profile_samples.py`; the other two profile samples came out byte-identical):
  - a walled courtyard of boxes with a crate, a pillar and a low step;
  - a spawn point, and a hero standing elsewhere;
  - a camera placed at the isometric view of the spawn;
  - Game Mode 2.5D, Game Genre Action RPG;
  - **no scripts and no graphs**.
  - It is in the template gallery (desktop only, with a preview) and in the web test's unsupported list.

## 3. Commands and results

| Command | Exit | Result |
|---|---|---|
| `./gradlew :engine-scene:test --tests "*GameGenresTest" :engine-render:test --tests "*.play.*" :engine-desktop:test --tests "*ActionRpgIsometricProfileOnJoltTest" ... :editor-core:test --tests "*GenreChoicesTest" ... --continue` | 1, then 0 | The new acceptance test passed 3 of 3 the first time. Four older assertions used Action RPG + 2.5D as their example of a planned pair; they now use Action RPG + 2D |
| Isometric yaw mutation check | 1 | 2 of 3 failed, as intended; restored |
| `GDXUNITY_UPDATE_AUDIT=1 ./gradlew :editor-desktop:test --tests "*EditabilityAuditTest" --tests "*TemplateMatrixTest" --tests "*WebUxContractTest"` | 0 | Tables refreshed |
| `npx vitest run`, `npx tsc --noEmit` in `engine-babylon` | 0, 0 | 481 pass; the type check passes |
| `./gradlew :engine-desktop:installDist :engine-desktop:gameScriptsClasses`, `python tools/make_template_previews.py action-rpg-iso-25d` | 0, 0 | Preview written |
| `GDXUNITY_SCREENSHOT=... ./gradlew :engine-desktop:run --args="--scene samples/action-rpg-iso-25d.json --smoke-test=120"` | 0 | "120 of 120 frames, 0 runtime error(s)" |
| `./gradlew :<module>:cleanTest :<module>:test` for `engine-scene`, `engine-render`, `engine-desktop`, `editor-core`, `editor-terrain`, `editor-desktop` | 0 each | 182, 167, 113, 497 (1 skipped, as before), 24, 160 |
| `GDXUNITY_SCREENSHOT=... ./gradlew :editor-desktop:run --args="--scene samples/action-rpg-iso-25d.json --smoke-test"` | 0 | 180 frames, 0 errors |

| Module | Before (M3c.2) | After |
|---|---|---|
| `engine-desktop` | 110 | 113 |
| Web | 480 | 481 |
| Others changed | unchanged counts | all pass |

**Screenshots** (looked at):
- [m3c-3/runtime-frame-120.png](m3c-3/runtime-frame-120.png): the isometric view from high and diagonally behind, with the hero (red) in the middle at the spawn point, the crate, the low step and the courtyard walls. The help text is readable.
- [m3c-3/editor-courtyard.png](m3c-3/editor-courtyard.png): the editor's 2.5D side view of the courtyard, with the spawn marker's post, the hero's capsule outline and the crate. The footer reads "Game Genre Action RPG", Game Mode 2.5D, "Playable", with 2D dimmed.

## 4. Failures found and fixed

- The four older assertions (section 3).
- My first edit to `GenreChoicesTest` put the message in the wrong argument of `assertEquals`; fixed and rerun.

## 5. Diff review

- **New:**
  - `ActionRpgIsometricProfileOnJoltTest`;
  - `assets/samples/action-rpg-iso-25d.json`, `assets/templates/previews/action-rpg-iso-25d.png`;
  - this report and `docs/reports/m3c-3/`.
- **Changed:**
  - `GameTestProfile`, `GameGenres`, `ProfileCameraRig`;
  - `tools/make_profile_samples.py`, `assets/templates/gallery.json`;
  - tests: `GameGenresTest`, `PlayableGenrePairsTest`, `PlayProfilesTest`, `GenreChoicesTest`, `TemplateRatingsTest`, web `sceneBuilder.test.ts`.
- **Docs:**
  - the chart (M3c.3, R3c);
  - `INDEX.md`, `FEATURE_TEST_MATRIX.md`;
  - TD-088 implementation notes;
  - the regenerated audit tables and `web-ui-ux-spec.md` (the generated footer table now shows Action RPG playable in 2.5D).

No files outside this repository were touched. CRLF files kept their line endings.

## 6. Known limitations and next steps

- **Manual check for a person:**
  1. Open "Action RPG Courtyard" from Project > New Game and press Play.
  2. Walk with WASD: up, down, left and right on the screen.
  3. Jump, and walk into a wall and the crate.
  4. Stop.
- **The camera is a perspective camera at an isometric angle**, not an orthographic projection.
- **No mouse movement and no combat.**
- **If the owner prefers side-view**, it can be added as its own camera kind; the registry has one profile per pair, so the two would be a choice to make.
- **Next in R3c, in the chart's order:**
  - M3c.4, the Edit 3D Model hand-off;
  - M3c.5, the regression project and checklist, now unblocked: M3c.1 and M3c.2 are built.

## 7. Git

No stage, commit, push, reset, stash, branch switch or other destructive Git operation was performed.
