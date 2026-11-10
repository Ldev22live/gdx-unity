# Phase 15-E report: Jolt 3D physics backend

**Date:** 2026-11-06 · **Decision:** TD-028 (Jolt approved by the owner the same day) · **Start HEAD:** `f9849c2` (nothing committed; the working tree already held uncommitted changes of the owner and earlier phases)

## What was built

- New module `engine-physics`: `JoltPhysicsBackend3D` (implements `PhysicsBackend3D`) and `JoltNatives` (extracts and loads the native library from the jolt-jni jar). jolt-jni 6.2.0, Windows64 / Linux64 / MacOSX_ARM64 artifacts; `joltJniVersion` in `gradle.properties`.
- `PhysicsSystem3D.isUsedBy(scene)`: only scenes with an active 3D collider create a backend, so 2D games never load the library.
- `PlayModeController` (editor Play) and `GameSession` (game runtime) create, step (after 2D physics) and dispose `PhysicsSystem3D`.
- Packaging keeps only the host platform's Jolt jars (`editor-desktop/build.gradle`); game export copies them with the rest.
- Sample `assets/samples/physics-sandbox-3d.json`: eight balls (one bouncy), a crate pyramid, floor and walls, no scripts.

## Acceptance criteria

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | Compatibility spike: natives load, a sphere dropped on a box rests on it | Met | `JoltPhysicsBackend3DTest.shouldLetASphereDroppedOnABoxComeToRestOnIt`; the packaged editor's bundled runtime loads them too |
| 2 | Backend honours the `PhysicsBackend3D` contract: layers and masks, sensors, contacts, rays, kinematic bodies, shapes, mass, disposal | Met | 18 tests; masks tested in both directions |
| 3 | `PhysicsSystem3D` on Jolt in scene units | Met | `PhysicsSystem3DOnJoltTest` 7 |
| 4 | Editor Play simulates 3D and restores the authored scene on Stop; 2D scenes never create 3D physics | Met | `PlayModeControllerTest` +2 |
| 5 | The game runtime plays a scene the same as editor Play | Met | `SampleGamesTest` 3D parity test (floor, three balls, a crate; all entities within 0.001 after 240 frames), sandbox added to the 14-sample parity list |
| 6 | Packaged build works | Met | Packaged editor, `--play --smoke-test=300` on the sandbox: exit 0, no Output Log errors; `--export-game`: exit 0; exported game `--smoke-test=300`: exit 0; library extracted to `%TEMP%\forgeroom-jolt-<hash>` |
| 7 | Tests green, docs updated | Met | see below |

## Problems found on the way

- jolt-jni takes the two counts of `ObjectVsBroadPhaseLayerFilterTable` in the opposite order from Jolt's C++ constructor; with the C++ order every body above the first two object layers fell through the floor. Found by a layer test, fixed, commented.
- Jolt reports a sleeping body's contacts as ended, so a ball resting on the floor produced a false "collision exit". Bodies now never sleep (cost: resting bodies are simulated). Both recorded in TD-028.
- Jolt does not check body ids; a stale id can crash the process. The backend keeps the set of live handles and throws `IllegalArgumentException` instead.

## Tests (per module, 2026-11-06, after the change)

engine-core 32, engine-scene 71, **engine-physics 25 (new)**, engine-render 90, engine-visualscript 9, engine-animation 32, engine-audio 185, engine-sculpt 77, engine-desktop 39, editor-core 341, editor-visualscript 20, editor-animation 17, editor-audio 55 (**2 failures, pre-existing**: `BundledDemoSongTest` x2, "Lead: File not found" and "expected 5 but was 4", listed as known in the matrix), editor-sculpt 45, editor-desktop 62. Everything else: 0 failures. Run per module group because a single full run was stopped for low memory earlier.

## Manually Not Verified (Headless Environment)

How the sandbox looks and moves in the 3D view and in the exported game window, and the collider wireframes during Play. The smoke tests prove that frames render and no errors are logged, not that the motion looks right.

## Known limitations and next steps

- Step F (not started): contact callbacks and raycasts for scripts, forces, velocities. `GameSession.getPhysicsSystem3D()` and `PlayModeController.getPhysicsSystem3D()` exist for it.
- Single thread, bodies never sleep: unprofiled for large scenes. No joints, character controller, mesh or convex colliders.
- Windows was the only platform run. Linux x64 and macOS arm64 natives are bundled but untried.
- The web players do not simulate 3D physics.
- The `engine-physics` module was created on the strength of "proceed with the next milestone" after Jolt was approved; AGENTS.md asks for approval of new modules, so say so if that reading was wrong.

## Git

No commit, push or other Git write was done.
