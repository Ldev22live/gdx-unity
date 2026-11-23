# Phase 15-F report: script access to 3D physics

**Date:** 2026-11-07 · **Decision:** TD-048 (approved: "approve TD-048") · **Start and end HEAD:** `f9849c2` (nothing committed; the working tree already held many uncommitted changes of the owner and earlier phases, left as they were)
**Asked:** "what would the next milestone be" → "yes please" (draft the scope and TD record) → "approve TD-048". The three open questions in TD-048 were not answered, so the proposals in it were built: web refuses the new nodes, no agent tools, no 2D script callbacks.

## What was built

- **Access.** `Scene.getPhysics3D()`; `PhysicsSystem3D` attaches itself to its scene in the constructor and detaches on `dispose()`. `ScriptComponent` gained `getPhysics3D()`, `requirePhysics3D()` (fails with advice) and `raycast3D(...)`.
- **Contacts.** `onCollisionEnter3D`, `onCollisionExit3D`, `onTriggerEnter3D`, `onTriggerExit3D` on `ScriptComponent`, delivered by `ScriptContactForwarder` (registered by `PhysicsSystem3D` itself, so Play and the game runtime cannot differ). It calls scripts attached directly **and** scripts held by an `EntityScriptBindingComponent` (how scene files attach them).
- **Forces and velocities.** `PhysicsSystem3D.addForce`, `addTorque` (accumulated and applied in every fixed step until a step happens), `addImpulse`, `setLinearVelocity`/`getLinearVelocity`, `setAngularVelocity`/`getAngularVelocity`, `overlapSphere`; `PhysicsBackend3D` and `JoltPhysicsBackend3D` gained the matching calls. Scene units throughout (100 per meter). Errors name the entity: no body yet, trigger-only, static, kinematic (for forces and impulses), non-finite vectors, a stopped system.
- **Graphs.** Nodes Add Force, Add Impulse, Set Velocity, Get Velocity, Raycast 3D (Hit/Miss exec outputs, entity/point/normal/distance outputs); Event Collision / Trigger Enter fire for 3D contacts (`GraphContactForwarder`, registered by `GameSession` and `GraphPlayModeExtension`); Java and Kotlin generation; the TypeScript generator refuses them with a message; the web validator says the node "needs 3D physics ... runs only in the desktop editor and game".
- **Sample.** `assets/scripts/samples/sandbox/SandboxLauncher.java` on the camera of `physics-sandbox-3d`: aim with the view, Space kick, hold F push, E pop the neighbours, T stop.

## Acceptance criteria

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | Scripts reach the scene's 3D physics, null when none runs | Met | `ScriptPhysics3DTest` (hook, second system refused, new system after dispose) |
| 2 | A script gets the four contact callbacks in Play and in the game runtime | Met for the runtime and the shared code; **Play for compiled scripts has no test of its own** | Runtime: `GameSessionTest` (bound script, real Jolt: one `collisionEnter:Floor`). Play uses the same `PhysicsSystem3D` constructor registration; graphs in Play: `GraphPlay3DTest`. Trigger enter and exit on real Jolt: `PhysicsSystem3DOnJoltTest`. Collision **exit** on real Jolt is not tested (fake backend only) |
| 3 | A force over one simulated second gives the same speed at 30 and 60 fps | Met | Fake backend: 60 force steps either way (`ScriptPhysics3DTest`); Jolt: 100 units/s ± 3 at both (`PhysicsSystem3DOnJoltTest`) |
| 4 | Impulses and velocities change the Jolt body | Met | `JoltPhysicsBackend3DTest` (+6), `PhysicsSystem3DOnJoltTest` (+6) |
| 5 | Static, kinematic, no-body and bad vectors fail with the entity's name | Met | `ScriptPhysics3DTest`, `GraphPhysics3DNodesTest` (as node errors) |
| 6 | Graphs using the nodes generate Java and Kotlin that do what the interpreter does; TypeScript is refused | Met | `GraphPhysics3DCodeTest` (compiles both, same messages and same backend calls, hit and miss), `GraphPhysics3DNodesTest` (refusals) |
| 7 | The sample uses the API | Met in tests; **not seen by a person** | `SampleGamesTest` (+3) |
| 8 | Web side shows the reason | Written; **test not run** | `visualscript.test.ts` (+1). `engine-babylon/node_modules` is empty (the earlier disk cleanup removed it), so neither `tsc` nor `vitest` could run. `npm ci` would restore it |

## Commands and results (2026-11-07, Windows, exit 0 unless noted)

`gradlew :engine-core:test :engine-scene:test :engine-render:test :engine-desktop:test`, `:engine-physics:test`, `:engine-visualscript:test`, `:editor-visualscript:test`, `:editor-core:test :editor-desktop:test`, the other modules, and `gradlew compileTestJava` (all modules).

| Module | Before | After | Notes |
|---|---|---|---|
| engine-scene | 71 | 94 | +23 `ScriptPhysics3DTest` |
| engine-physics | 25 | 37 | +12 |
| engine-visualscript | 9 | 19 | +10 `GraphPhysics3DNodesTest` |
| editor-visualscript | 20 | 25 | +4 `GraphPhysics3DCodeTest`, +1 `GraphPlay3DTest` |
| engine-desktop | 42 | 43 | +1 |
| editor-desktop | 62 | 65 | +3 (`SampleGamesTest`) |
| editor-core, engine-core, engine-render, engine-animation, engine-audio, engine-sculpt, engine-web, editor-animation, editor-sculpt | unchanged | unchanged | 352 (1 skipped), 32, 90, 32, 185, 77, 9, 17, 45; 0 failures |
| editor-audio | 55 | 55 | the 2 known `BundledDemoSongTest` failures (pre-existing, unrelated) |

## Problems found on the way

- A runtime test showed the first forwarder missed scripts bound through `EntityScriptBindingComponent` (the way every scene file attaches scripts): my unit tests attached scripts directly and passed. Fixed, with a unit test for bound scripts.
- The draft's acceptance wording ("same velocity change whether the frame ran 1 or 5 sub-steps") described the opposite of the intent; corrected in the milestone document and tested as frame-rate independence.
- A sample test aimed from outside the arena walls; the test now keeps the camera inside.

## Manually Not Verified (Headless Environment)

How the sandbox plays by hand (aiming, keys, the Help Text line, how a pop looks); the new nodes in the graph editor's palette; Linux and macOS behaviour of the new backend calls.

## Known limitations and next steps

- Raycast 3D has no layer-mask pin; a ray starting inside a solid does not hit it.
- A script exception in a contact callback stops that frame's remaining contact deliveries (same as an exception in `onUpdate`).
- Web players run none of this; no `AgentTool`s for physics; 2D scripts still have no collision callbacks.
- To finish: restore `engine-babylon/node_modules` (`npm ci`) and run `npm test` and `npm run typecheck`; play the sandbox by hand.
- Next candidates: a character controller, joints, mesh colliders (not started; each needs its own decision).

## Git

No commit, push or other Git write was done.
