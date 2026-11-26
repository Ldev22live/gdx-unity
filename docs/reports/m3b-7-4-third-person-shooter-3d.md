# Roadmap M3b.7, Slice 4: Third-person Shooter + 3D (2026-11-25)

Milestone M3b.7 of [milestone-chart.md](../milestone-chart.md) (roadmap R3b) gives each genre and mode pair its own slice. Earlier slices:
- [slice 1](m3b-7-1-genre-and-play-agent-tools.md): the agent tools;
- [slice 2](m3b-7-2-platformer-3d.md): Platformer + 3D;
- [slice 3](m3b-7-3-first-person-shooter-3d.md): First-person Shooter + 3D.

**This slice makes Third-person Shooter + 3D playable.** **Status:**
- **Implemented and Integrated:** the footer's Game Genre offers it as playable in 3D, and editor Play and the game runtime run the same profile step.
- **The pair's done-when ("passes its test and has a template") is met** in automated tests on real Jolt. The template is `third-person-yard-3d.json`.
- **Manually Not Verified (Headless Environment):** nobody has played it with the keyboard.
- **M3b.7 as a whole is not done:** seven of the 33 pairs play. Racing and the rest are not started.

**Why this milestone next:**
- M3c.9, the last R3c scenario, needs MC.5 and MC.6. Those sit behind the audio chain M8.7, M8.6, M8.5 and M8.1, which has TD, dependency and legal gates, and a person's listening.
- M3b.7 is the open work on the chart's critical path (R3b) with no gate.

**Context:**
- Branch: `phase-12-3d-foundation`. Starting and ending `git rev-parse --short HEAD`: `b8bace5` (no commit was made).
- The working tree held earlier uncommitted work, listed in its reports (M7a, MC.2, MC.3, MC.4, M3c.6, M3c.7 and asset work).
- **Approvals:** TD-088 (approved 2026-11-15) leaves each pair to its own slice without specifying it. I chose this design and recorded it in TD-088's implementation notes. No new module, dependency or file format.

## 1. Acceptance criteria

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | The pair is in the registry, playable, with its reason | **Met** | `GameGenres.thirdPersonShooter3D`; the reason names the camera, the keys and the level shot, and says health, enemies and ammunition come later (`GameGenresTest`, +2 tests, 12 in all) |
| 2 | A third-person camera | **Met** | The camera is straight behind the Gunner, 440 along the ground (450 at 12 degrees), above the pivot, and looks ahead and slightly down. After a 90-degree turn to the right it is behind on −X and looks along +X; after turning back, behind on +Z again |
| 3 | Shooter movement | **Met** | Q and E turn the Gunner at 120 degrees per second. A steps left without turning it, and the camera stays straight behind. After the turn, W walks along +X |
| 4 | A shot | **Met** | F shoots once per press: level from the Gunner's chest (30 above its centre) along its facing, through its own shape. The middle crate 15 m ahead is knocked back; the others stay. A 12-degree turn and left Ctrl knock the right-hand crate. Facing the east wall, the shot stops at the wall and knocks nothing |
| 5 | No script needed | **Met** | The sample has no scripts or graphs. The profile's parts are never saved (the test checks that serialising the Play copy refuses) |
| 6 | A template | **Met** | `third-person-yard-3d.json` is in the New Game gallery (desktop only) |
| 7 | A pair is playable only with its acceptance test | **Met** | `PlayableGenrePairsTest` lists `ThirdPersonYardProfileOnJoltTest` for `third-person-shooter/3d` |

**Mutation checks** (each restored from a copy afterwards):

| Change | What failed |
|---|---|
| The shot always along the camera's view | The shot test: "expected Crate Middle but was **Floor**". This is why the shot has its own origin |
| The camera no longer follows the Gunner's facing | The turning test: "now facing +X, the camera is behind it on −X: expected −440.2 but was 0.0" |

## 2. Design (decided here)

- **The camera:** `CameraSetup.Kind.BEHIND_FACING`.
  - Configured like Action RPG's `THIRD_PERSON` `ORBIT` rig, with occlusion on.
  - Every frame, before the rig moves, its yaw is set from the player's facing. The turn keys turn the player (its mover), not the camera, so the player aims with its body.
  - Action RPG's camera is turned by the keys on its own; its walker turns toward where it walks. Neither suits aiming.
- **Movement:** slice 3's `FIRST_PERSON_WALK` (`FirstPersonMover`), unchanged.
- **The shot:** `ShotSetup` gained `Origin` and `aimHeight`. The two-argument constructor keeps slice 3's camera-view shot, so the first-person profile is unchanged (its acceptance test passes, 3 of 3).
  - `PLAYER_FACING` fires from `aimHeight` above the player's centre, level along its facing. A player facing straight up or down has no level direction, so nothing is fired.
  - `aimHeight` must be 0 along the camera's view, and finite.
  - The shooter's errors no longer name First-person Shooter, since two genres shoot.
- **The numbers** are named constants in `GameGenres`:
  - `BEHIND_DISTANCE` 450, `BEHIND_PIVOT_HEIGHT` 80, `BEHIND_PITCH` 12, `THIRD_PERSON_AIM_HEIGHT` 30;
  - walking, turning, jumping, range and knock reuse the `SHOOTER_*` and `SHOT_*` constants.
- **The sample** (`tools/make_profile_samples.py third-person-yard`): the first-person range's walled yard without the shelf.
  - Three 120-unit crates (dynamic, 5 kg) stand on the floor 15 m ahead; the side ones are about 11 degrees off.
  - They are tall enough for a level shot from the chest (100 above the floor) to strike them.
- **Not chosen now:**
  - an over-the-shoulder side offset;
  - a crosshair (the level shot and a camera looking down do not meet at the screen's centre, so a centred mark would mislead);
  - health, enemies, ammunition, mouse look.

## 3. Commands and results

| Command | Exit | Result |
|---|---|---|
| `.\gradlew.bat :engine-scene:test --tests "*GameGenresTest" :engine-render:test --tests "*PlayableGenrePairsTest" --tests "*PlayProfilesTest" :engine-desktop:test --tests "*ThirdPersonYardProfileOnJoltTest" --tests "*FirstPersonRangeProfileOnJoltTest" --max-workers=1 --offline -x generateAssetList` | 0 | `GameGenresTest` 12, `PlayableGenrePairsTest` 2, `PlayProfilesTest` 18, `ThirdPersonYardProfileOnJoltTest` 3, `FirstPersonRangeProfileOnJoltTest` 3; 0 failures, on the first run |
| The two mutations above, `ThirdPersonYardProfileOnJoltTest` only | 1, 1 | 1 of 3 fails each time, as intended; restored |
| Sample audits with `GDXUNITY_UPDATE_AUDIT=1` (two runs of three classes) | 1, then 0 | The only failure was the label count: the yard is "Checks pending", like the first-person range (15 → 16, updated). The regenerated tables differ from a snapshot taken first only by the yard's rows and totals, plus one line about MC.4's hybrid (its web test entry, added in that milestone) |
| The seven audit and plan classes again, without the flag (`TemplateRatingsTest`, `SampleInventoryTest`, `SampleMigrationsTest`; then `AssetSupportMatrixTest`, `EditabilityAuditTest`, `TemplateAuditTest`, `TemplateMatrixTest`) | 0, 0 | Pass |
| `:editor-core:test` for `GenreChoicesTest`, `PlayToolsTest`, `ProfilePlayStepTest`, `ProfileFixesTest` | 0 | Pass |
| `:editor-desktop:test` for `GameModeSelectorTest`, `TemplateGalleryProjectsTest` (every gallery template opens, plays and exports, the yard included), `CustomizeGatewaySamplesTest` | 0 | Pass |
| `engine-babylon`: `npx vitest run` | 1 | **520 of 521.** The one failure is the existing `action-rpg-valley-3d.json` `AtmosphereComponent` gap, not this work (see the MC.4 report). With that gap listed temporarily, `sceneBuilder.test.ts` passes 8 of 8, so the yard builds on the web with only its expected `PlayerComponent` gap; reverted |
| `.\gradlew.bat :engine-desktop:run --args="--scene samples/third-person-yard-3d.json --smoke-test=240"` with `GDXUNITY_SCREENSHOT` | 0 | "240 of 240 frames, 0 runtime error(s)". [Frame](m3b-7-4/runtime-yard-frame-240.png), looked at: the Gunner from behind and slightly above, the three crates ahead on the floor, the walls framing the yard |

## 4. Diff review

- **New:**
  - `engine-desktop` test `ThirdPersonYardProfileOnJoltTest`;
  - `assets/samples/third-person-yard-3d.json`;
  - this report and `docs/reports/m3b-7-4/` (the runtime frame).
- **Changed:**
  - `GameTestProfile` (`BEHIND_FACING`, `ShotSetup.Origin`, `aimHeight`);
  - `GameGenres` (the profile, constants, playable);
  - `ProfileCameraRig`, `ProfileShooter`;
  - tests `GameGenresTest`, `PlayableGenrePairsTest`, `SampleInventoryTest` and `TemplateRatingsTest` (counts), `engine-babylon/test/sceneBuilder.test.ts` (the yard's `PlayerComponent`);
  - `tools/make_profile_samples.py`, `assets/templates/gallery.json`, `assets/samples/README.md`, `docs/migration/sample-migrations.json`;
  - `TECHNICAL_DECISIONS.md` (TD-088 notes), the chart, `INDEX.md`, `FEATURE_TEST_MATRIX.md`;
  - regenerated tables: `FEATURE_TEST_MATRIX.md` (two), `sample-asset-migration-inventory.md`, `fully-editable-template-audit.md`, `asset-support.tsv`, `ASSET_SUPPORT_MATRIX.md`.
- The other profile samples were not rewritten (the generator writes the named sample only).

No commit, push or destructive Git operation was performed.

## 5. Human checklist

1. Project > New Game > **Third-person Yard** > Play Sample.
2. The camera is behind and above the Gunner. Q and E turn the Gunner, and the camera swings behind it.
3. A and D step sideways; W and S walk.
4. F or left Ctrl knocks the crate the Gunner faces.
5. Stop: the yard is as it was.
