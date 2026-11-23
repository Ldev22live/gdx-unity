# Joints report (TD-051)

**Date:** 2026-11-07 · **Decision:** TD-051 (approved: "aprove TD-051", as typed) · **Start and end HEAD:** `f9849c2` (nothing committed; the working tree already held many uncommitted changes of the owner and earlier phases, left as they were)
**Asked:** "aprove TD-051". The open questions were not answered, so the proposals were built (single joint per entity, no ragdolls); the break force of question 3 could not be built, see below.

## What was built

- **Component.** `Joint3DComponent` + `Joint3DComponentSerializer` (`engine-scene`): types `FIXED`, `HINGE`, `SLIDER`, `DISTANCE`, `BALL`; connected entity by name (empty = world); anchor, second anchor (distance), axis; `collideConnected`; limits; motor (`OFF`, `VELOCITY`, `POSITION`) with a force limit.
- **Backend.** `JointDescriptor3D`; `PhysicsBackend3D.addJoint / removeJoint / setJointMotor / getJointPosition` (default methods that throw, so other backends fail loudly); Jolt constraints in `JoltPhysicsBackend3D`.
- **Lifetime.** `PhysicsSystem3D` makes a joint when both bodies exist, removes it before its bodies go (entity or connected entity inactive, component disabled or removed) and makes it again when they are back. Refusals name the entity.
- **Scripts.** `setJointMotor3D(mode, target)` and `getJointPosition3D()` on `ScriptComponent` (same two on `PhysicsSystem3D`).
- **Editor.** Add Component > Physics 3D > Joint 3D (refused on characters), Details editor, pink lines in the 3D view (anchor cross, axis, hinge limit arc, slider travel, link to the connected entity).
- **Game runtime.** Serializer registered in `GameComponentSerializers` (26 types).
- **Sample.** `physics-joints-3d`: a door driven open by its hinge motor, a pendulum, a falling chain of ball joints, a slider platform with a crate. No script.

## Acceptance criteria

| # | Criterion (TD-051) | Result | Evidence |
|---|---|---|---|
| 1 | A hinge door swings and stops at its limits | Met | `Joints3DOnJoltTest.shouldSwingAHingeDoorAndStopItAtItsLimit` (motor drives it to the 80 degree limit within 2 degrees, the anchor stays within 3 units) |
| 2 | Pendulum period within 5% of the formula | Met | `shouldSwingAPendulumWithThePeriodOfTheFormula` (physical pendulum length and the 10 degree correction) |
| 3 | A fixed joint holds two bodies under load | Met, with a sag | `shouldHoldABoxAndItsLoadWithFixedJointsInTheAir`: a 20 kg load 1.3 m to the side stays within 15 units (it sags about 10) |
| 4 | A slider motor moves a body to its target | Met | `shouldDriveASliderToItsTargetPositionAndStopItAtItsLimit` (position target 200, then velocity into the upper limit 300) |
| 5 | A joint breaks above its force | **Not met, not built** | jolt-jni 6.2.0 cannot report a constraint's force; see TD-051 implementation notes |
| 6 | Refusals | Met | `shouldRefuseJointsThatCannotWorkAndNameTheEntity`, `shouldRefuseABadHingeMotorOrCharacterAndTriggerOnlyBodies`, `shouldRefuseToJoinACharacter` (missing connected entity, nothing dynamic, limits without 0, motor on a ball joint, character) and the catalog refusal |
| 7 | Removal on deactivate | Met | `shouldRemoveAJointWhileAnEntityIsInactiveAndMakeItAgain` |
| 8 | Serializer | Met | `Joint3DComponentTest` (round trip of every setting; bad type, motor or limits are a `SceneSerializationException`); `GameComponentSerializersTest` (26) |
| 9 | Play and the game runtime agree | Met | one code path; the sample is in the `SampleGamesTest` Play-versus-runtime parity check; `shouldSwingTheDoorPendulumChainAndSliderOfTheJointsSample` in the game runtime |
| 10 | Real Jolt on Windows | Met | all joint tests above run on Jolt; also rope (`DISTANCE`), ball chain, `collideConnected`, script drive |
| 11 | Editor: Add Component, Details, lines in the 3D view | Add Component met in tests (`Physics3DCatalogTest.shouldOfferAJointOncePerEntityRefuseCharactersAndUndo`); Details editor and lines **not seen by a person** | editor `--smoke-test`: 180 frames, 0 errors |
| 12 | All other tests unchanged | Met | table below (one expected-list assertion in `ComponentCatalogTest` updated for the new entry) |

## Commands and results (2026-11-07, Windows)

Per module while developing: `gradlew :engine-desktop:test --tests "*Joints3DOnJoltTest"`, `:engine-scene:test`, `:editor-core:test`, `:editor-desktop:test --tests "*SampleGamesTest"`, `compileJava compileTestJava`. `engine-desktop:run --args="--scene samples/physics-joints-3d.json --smoke-test=240"`: "240 of 240 frames, 0 runtime error(s)". `editor-desktop:run --args="--smoke-test"`: "180 frames, 0 error(s)". Final: `gradlew test --continue --rerun-tasks` → exit 1: the 2 known `BundledDemoSongTest` failures in `:editor-audio` (pre-existing) and one `ComponentCatalogTest` assertion that lists the Physics entries, which I fixed (and re-ran that class: passes) after the run. The full run was not repeated after that fix.

| Module | Before | After | Notes |
|---|---|---|---|
| engine-scene | 111 | 117 | +6 `Joint3DComponentTest` |
| engine-physics | 53 | 53 | |
| engine-render | 99 | 99 | |
| engine-desktop | 47 | 59 | +12 `Joints3DOnJoltTest`; `GameComponentSerializersTest` now expects 26 |
| editor-core | 355 (1 skipped) | 356 (1 skipped) | +1 `Physics3DCatalogTest`; `ComponentCatalogTest` list updated |
| editor-desktop | 69 | 70 | +1 `SampleGamesTest`; the sample also joins the existing checks over all samples |
| others | unchanged | unchanged | 0 failures |
| editor-audio | 55 | 55 | the 2 known failures (pre-existing, unrelated) |

## Problems found on the way

- **Slider sign.** With the entity as Jolt's body 1, a slider driven towards +200 reported -200 (Jolt measures body 2 relative to body 1). Fixed by making the connected body (or the world) body 1; the distance joint's points follow.
- **Break force impossible** with this jolt-jni version (no constraint force query). Left out rather than faked.
- My first collide test set no anchor, so nothing hung; fixed in the test. The chain, rope and pendulum needed no changes.

## Manually Not Verified (Headless Environment)

How the sample looks and feels (door, pendulum, chain, platform with the crate), the Details editor of the joint, the pink lines in the 3D view, the camera framing of the sample.

## Known limitations and next steps

- No breaking force, `onJointBreak3D` or `isJointBroken3D` (disable the component to remove a joint).
- One joint per entity; connected entity by name (first match); no ragdolls, springs, cone limits or graph nodes.
- Everything but the motor is read when the joint is created; the rest pose is the pose at that moment.
- A fixed joint under load sags a little (about 10 units for 20 kg at 1.3 m).
- The web (Babylon) player ignores the component, with no warning yet.
- Next: TD-052 (mesh colliders) is drafted, awaiting approval.

## Git

No commit, push or other Git write was done.
