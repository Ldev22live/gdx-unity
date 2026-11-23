# Phase 15-G report: 3D character controller

**Date:** 2026-11-07 · **Decision:** TD-049 (approved: "approve TD-049") · **Start and end HEAD:** `f9849c2` (nothing committed; the working tree already held many uncommitted changes of the owner and earlier phases, left as they were)
**Asked:** "what's the next milestone" → I recommended a validation pass → "15-G" → TD-049 drafted → "approve TD-049". The three open questions in TD-049 were not answered, so the proposals were built: rotation is left to scripts, no moving platforms or character-vs-character, existing samples are not migrated.

## What was built

- **Component.** `CharacterController3DComponent` (radius, height, slope limit, step height, mass, gravity scale, layer, mask) with serializer. The entity's position is the capsule's center.
- **Backend.** `PhysicsBackend3D` gained `addCharacter`, `removeCharacter`, `moveCharacter`, `getCharacterPosition`, `isCharacterGrounded`; `JoltPhysicsBackend3D` implements them with Jolt's `CharacterVirtual` and `extendedUpdate` (stairs, stick to floor, slopes) plus an inner body, so balls land on the character and rays and sphere overlaps find it. Characters have negative handles that never equal a body id.
- **System.** `PhysicsSystem3D` runs characters in the fixed step: gravity as the average speed over a step (an exact jump parabola at any frame rate), horizontal velocity set by scripts, ceiling detection, position written back to the entity. API: `moveCharacter`, `jumpCharacter`, `isCharacterGrounded`, `getCharacterVelocity`. `isUsedBy` counts a scene with only a character as using 3D physics.
- **Scripts.** `ScriptComponent.moveCharacter3D`, `jumpCharacter3D`, `isGrounded3D`, `getCharacterVelocity3D`; errors name the entity.
- **Graphs.** Move Character, Jump Character, Is Grounded (Physics 3D); interpreter, Java and Kotlin generation (parity tested); the TypeScript generator refuses them; the web validator reports them as desktop-only.
- **Editor.** Add Component > Physics 3D > Character Controller 3D (refused beside a Rigid Body 3D or a collider, and the reverse), Details editor, orange capsule wireframe in the 3D view.
- **Sample.** `assets/samples/character-playground-3d.json` with `scripts/samples/walker/WalkerPlayer.java` and `WalkerCamera.java`.

## Acceptance criteria

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | Grounded and stable for 10 simulated seconds, no sinking or drifting | Met | `CharacterController3DOnJoltTest.shouldStandOnTheFloorForTenSecondsWithoutSinkingOrDrifting` (center within 2 units of rest height, x and z within 0.5) |
| 2 | Jump apex within 5% of v squared over 2g | Met | `shouldJumpToTheHeightPhysicsPredictsAndOnlyWhileGrounded` (4 m/s, 5% tolerance); a second jump while in the air is refused |
| 3 | Same walk distance at 30 and 60 fps | Met | `shouldWalkTheSameDistanceAtThirtyAndSixtyFramesPerSecond` (both within 5 units of 150); fake-backend version in `CharacterController3DTest` |
| 4 | A step under the limit is climbed, one above blocks | Met | `shouldClimbAStepLowerThanItsStepHeightAndStopAtAHigherOne` (20 climbed, 50 blocked at its face); sample steps in `SampleGamesTest` |
| 5 | A slope above the limit is not climbed | Met | `shouldWalkUpAGentleSlopeAndBeStoppedByASteepOne` (30 degrees climbed, 60 not); sample ramps |
| 6 | A crate is pushed | Met | `shouldPushALightCrateItWalksInto` (1 kg), `SampleGamesTest.shouldPushACrateAcrossThePlaygroundFloor` (4 kg) |
| 7 | Refused beside a Rigid Body 3D | Met | `CharacterController3DTest.shouldRefuseACharacterWithARigidBodyOrAColliderNamingTheEntity` (runtime), `Physics3DCatalogTest.shouldKeepACharacterControllerApartFromRigidBodiesAndColliders` (editor) |
| 8 | Play and the game runtime behave the same | Met | one `PhysicsSystem3D` code path; `GameSessionTest.shouldWalkACharacterFromAScriptOnTheRealPhysicsInTheRuntime` and `PlayModeControllerTest.shouldLetACharacterFallAndLandDuringPlayAndKeepTheAuthoredPosition` |
| 9 | Contacts and triggers | Met | one `COLLISION_ENTER` on landing and none while walking; one `TRIGGER_ENTER` and one `TRIGGER_EXIT` through a zone; a ball lands on the character (`CharacterController3DOnJoltTest`) |
| 10 | Graph nodes, Java/Kotlin generation, TypeScript refusal | Met | `GraphPhysics3DNodesTest` (+3), `GraphCharacterCodeTest` (3) |
| 11 | Sample works | Met in tests; **not seen by a person** | `SampleGamesTest` (+3); `engine-desktop:run --scene samples/character-playground-3d.json --smoke-test=240` printed "240 of 240 frames, 0 runtime error(s)" (it renders; I cannot judge how it looks) |
| 12 | Web side shows the reason | Written; **test not run** | `engine-babylon/node_modules` is empty and the C: drive has about 1.6 GB free, so `npm ci` was not run (see below) |
| 13 | All other tests unchanged | Met | table below; the 2 failures are the known `BundledDemoSongTest` ones |

## Commands and results (2026-11-07, Windows, exit 0 unless noted)

Per module while developing: `gradlew :engine-scene:test`, `:engine-physics:test`, `:engine-visualscript:test :editor-visualscript:test`, `:editor-core:test :engine-desktop:test`, `:editor-desktop:test`, `compileJava compileTestJava`. Final: `gradlew test --continue` → exit 1, caused only by `:editor-audio:test` (the 2 known `BundledDemoSongTest` failures); every other module passed.

| Module | Before | After | Notes |
|---|---|---|---|
| engine-scene | 94 | 109 | +15 `CharacterController3DTest` |
| engine-physics | 37 | 51 | +14 `CharacterController3DOnJoltTest` |
| engine-visualscript | 19 | 22 | +3 in `GraphPhysics3DNodesTest` |
| editor-visualscript | 25 | 28 | +3 `GraphCharacterCodeTest` |
| engine-desktop | 43 | 44 | +1 `GameSessionTest` |
| editor-desktop | 65 | 68 | +3 `SampleGamesTest` |
| editor-core | 352 (1 skipped) | 354 (1 skipped) | +1 `Physics3DCatalogTest`, +1 `PlayModeControllerTest`; `ComponentCatalogTest` expectation updated |
| engine-core, engine-render, engine-animation, engine-audio, engine-sculpt, engine-web, editor-animation, editor-sculpt | unchanged | unchanged | 32, 90, 32, 185, 77, 9, 17, 45; 0 failures |
| editor-audio | 55 | 55 | the 2 known `BundledDemoSongTest` failures (pre-existing, unrelated) |

`GameComponentSerializersTest` now expects 24 registered component types (was 23).

## Problems found on the way

- **A latent failure from Phase 15-F in the web tests.** `visualscript.test.ts` "shouldOfferTheSameNodeTypesAsTheDesktopEditor" compared the web registry with *every* node id in `BuiltInNodes.java`; 15-F added five desktop-only ids, so that test would have failed since then (it could not be run, see below). Fixed: the test now leaves out `DESKTOP_ONLY_NODE_TYPES` and checks each of those is a real desktop id. Still **not run**.
- Test setup mistakes in my own tests (a step the climber walked past, a ramp-foot bound, the fake backend needing one more step to report "grounded"): fixed in the tests, no product change.
- The character's real resting height is 70 units (80/2 + 30), not the 75 it is spawned at; the sample test expectations use 70.

## Manually Not Verified (Headless Environment)

Playing the sample by hand: how walking, the steps, the ramps, the jump and the chase camera feel and look, the Help Text line; the orange capsule wireframe; the Details editor; the Add Component refusal message on screen; the new nodes in the graph palette; Linux and macOS behaviour of `CharacterVirtual`.

## Known limitations and next steps

- One capsule per entity; no moving platforms (a character standing on a moving body is not carried); no character-vs-character collision.
- The simulation owns the position: setting the entity's transform from a script does nothing after the next physics update, and there is no teleport (a respawn needs one: next small step). Entity rotation and scale are never used; turning the entity toward the move direction is left to scripts.
- The character's own collision mask does not limit what it walks into; layer and mask only decide what other bodies and rays see. Push strength is Jolt's default (100 N).
- No crouch, no slope-slide animation, no agent tools; the web players run none of this.
- Jump Character has no "jumped" output; use Is Grounded.
- To finish: free disk space, restore `engine-babylon/node_modules` (`npm ci`), run `npm test` and `npm run typecheck`; play both physics samples by hand.
- Next candidates: a teleport/respawn call, joints, mesh colliders, camera rigs (first and third person), moving platforms (each needs its own decision), or the validation pass of Phases 12-17.

## Follow-up: teleport/respawn (same day, asked by the owner)

`teleportCharacter(entity, position)` on `PhysicsSystem3D`, `teleportCharacter3D(position)` on `ScriptComponent`, `setCharacterPosition` on `PhysicsBackend3D` (Jolt: `CharacterVirtual.setPosition`). Not swept; clears walking and fall speed; entity moves at once. Tests: +2 `CharacterController3DTest` (17), +2 `CharacterController3DOnJoltTest` (16: teleport mid-walk, lands and walks on from the new place; refusal of non-characters and NaN). No graph node. engine-scene 109 to 111, engine-physics 51 to 53; editor-core, engine-desktop, editor-desktop, both visualscript modules pass. The limitation "there is no teleport" above no longer holds.

## Git

No commit, push or other Git write was done.
