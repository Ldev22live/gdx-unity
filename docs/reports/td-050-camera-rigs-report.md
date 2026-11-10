# Camera rigs report (TD-050)

**Date:** 2026-11-07 · **Decision:** TD-050 (approved: "approve TD-050") · **Start and end HEAD:** `f9849c2` (nothing committed; the working tree already held many uncommitted changes of the owner and earlier phases, left as they were)
**Asked:** "do the validation passes 12-17 then follow this with teleport/respawn call, camera rigs, joints, mesh colliders" → "what is the next milestone" → "approve TD-050". The three open questions were not answered, so the proposals were built (one deviation on the first: the target is found by name, see TD-050 implementation notes).

## What was built

- **Component.** `CameraRigComponent` (`engine-render`) with `CameraRigComponentSerializer`. Modes `FOLLOW`, `ORBIT`, `FIRST_PERSON`; target by entity name; follow offset and rate; look-at height; eye height; yaw, pitch and distance with limits; optional wall occlusion for `ORBIT` using the 3D physics ray, with margin and layer mask. It runs as its own `onUpdate`, after physics, in editor Play and in the game runtime.
- **Scripts.** `getComponent(CameraRigComponent.class)` then `rotate`, `setYaw`, `setPitch`, `setDistance`.
- **Editor.** Add Component > Game > Camera Rig, a Details editor (`CameraRigComponentEditorProvider`), a yellow line from a selected rig to its look-at point in the 3D view.
- **Game runtime.** Serializer registered in `GameComponentSerializers` (25 types).
- **Sample.** `character-playground-3d` uses a rig (FOLLOW) instead of the `WalkerCamera` script, which was deleted.
- **Same day, earlier:** the teleport call, see [phase-15g-report.md](phase-15g-report.md).

## Acceptance criteria

| # | Criterion (TD-050) | Result | Evidence |
|---|---|---|---|
| 1 | Follow reaches the offset and closes the gap at the rate, equally at 30 and 60 fps | Met | `CameraRigComponentTest.shouldCatchUpTheSameAtThirtyAndSixtyFramesPerSecond`, `shouldFollowAtTheOffsetAndLookAtTheTarget`, `shouldFollowRigidlyAtRateZero` |
| 2 | Orbit keeps its distance and respects pitch limits | Met | `shouldOrbitAtTheDistanceInTheDirectionOppositeToTheView`, `shouldHoldSettingsInsideTheirLimitsAndRefuseBadValues` |
| 3 | Occlusion pulls the camera in front of a wall and releases it | Met | `CameraRigOnJoltTest.shouldPullAnOrbitCameraInFrontOfAWallAndLetItOutAgain` (stops 20 units short of the wall, back to 400 when it goes, ignored when off) on real Jolt |
| 4 | The orbited character does not pull the camera in | Met | `CameraRigOnJoltTest.shouldNotBePulledInByTheCharacterItOrbits` (the ray starts inside the capsule; found and fixed while testing) |
| 5 | First person matches the target's yaw | Met | `shouldSitAtTheEyesOfTheTargetAndFaceWhereItFaces` |
| 6 | Serializer round trip | Met | `shouldRoundTripEverySettingThroughTheSceneFile` (also a bad mode is a `SceneSerializationException`) |
| 7 | Follows a walking character in the real physics | Met | `CameraRigOnJoltTest.shouldFollowACharacterThatWalksAwayAndKeepItsOffset` |
| 8 | A missing target names the rig's entity | Met | `shouldNameTheRigsEntityWhenTheTargetDoesNotExist`; an empty name stays idle (`shouldStayIdleWithoutATargetName`) |
| 9 | Play and the game runtime agree; sample migrated | Met | one component code path; `SampleGamesTest.shouldChaseTheHeroWithTheCameraRigOfThePlayground` (game runtime, real scene file); `--smoke-test=240` of the sample: "240 of 240 frames, 0 runtime error(s)" |
| 10 | Editor: Add Component, Details, line in the 3D view | Add Component met in tests (`Physics3DCatalogTest.shouldOfferACameraRigOncePerEntityAndUndoIt`); Details editor and line **not seen by a person** | editor `--smoke-test` passes (180 frames, 0 errors) |
| 11 | All other tests unchanged | Met | table below |

## Commands and results (2026-11-07, Windows)

Per module while developing: `gradlew :engine-render:test`, `:engine-desktop:test`, `:editor-core:test :editor-desktop:test`, `compileJava compileTestJava`. `engine-desktop:run --scene samples/character-playground-3d.json --smoke-test=240` and `editor-desktop:run --smoke-test`: exit 0. Final: `gradlew test --continue --rerun-tasks` → exit 1, caused only by `:editor-audio:test` (the 2 known `BundledDemoSongTest` failures).

| Module | Before | After | Notes |
|---|---|---|---|
| engine-scene | 109 | 111 | +2 teleport (earlier the same day) |
| engine-physics | 51 | 53 | +2 teleport |
| engine-render | 90 | 99 | +9 `CameraRigComponentTest` |
| engine-desktop | 44 | 47 | +3 `CameraRigOnJoltTest`; `GameComponentSerializersTest` now expects 25 |
| editor-core | 354 (1 skipped) | 355 (1 skipped) | +1 `Physics3DCatalogTest` |
| editor-desktop | 68 | 69 | +1 `SampleGamesTest` |
| engine-core, engine-visualscript, engine-animation, engine-audio, engine-sculpt, engine-web, editor-visualscript, editor-animation, editor-sculpt | unchanged | unchanged | 32, 22, 32, 185, 77, 9, 28, 17, 45; 0 failures |
| editor-audio | 55 | 55 | the 2 known failures (pre-existing, unrelated) |

## Problems found on the way

- The occlusion ray starts at the pivot, normally inside the orbited character's capsule; Jolt reports that hit at distance 0, so the first version never moved past it and the camera was never pulled in. Fixed by stepping on in 10 unit steps (up to 16). Limitation below.
- My first draft added a `ScriptComponent.getCameraRig()` stub, which cannot exist because `engine-scene` cannot see `engine-render`; removed before it was used.

## Manually Not Verified (Headless Environment)

How the chase camera, an orbit, a first-person view and occlusion look and feel while playing; the Details editor of the rig; the yellow line in the 3D view; the sample's camera framing now that it looks at the hero (the old script kept a fixed tilt).

## Known limitations and next steps

- Target by name (first match). No entity picker in Details.
- Occlusion cannot leave a target wider than about 160 units across the ray: then the camera is not pulled in.
- Keep the rig entity at the scene root (position is set as a world position).
- No camera shake, cut transitions, mouse capture or input handling: a script feeds `rotate`.
- The web (Babylon) player ignores the component, with no warning yet (TD-050 asked for one); no graph nodes; the other 3D samples keep their own camera scripts.
- Next: TD-051 (joints) and TD-052 (mesh colliders) are drafted, awaiting approval.

## Git

No commit, push or other Git write was done.
