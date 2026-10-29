# 3D Physics Engine Evaluation (Phase 15)

**Date:** 2026-10-29 · **Decision:** TD-028 (Proposed) · **Requirement:** design doc §3.3 and §6, `AGENTS.md` §7
**Recommendation:** Jolt Physics through **jolt-jni 6.2.0** (MIT), behind the engine's own `PhysicsBackend3D`
interface. **Not adopted yet:** adding it needs the project owner's approval, after the compatibility spike (§6) passes.

## 1. Candidates

| | jolt-jni (Jolt Physics) | gdx-bullet (libGDX extension) | Libbulletjme (Bullet) | ode4j |
|---|---|---|---|---|
| What it is | JNI bindings to Jolt Physics, a current C++ engine (used in shipped AAA games; Godot 4.4 ships it as an option) | libGDX's SWIG wrapper of Bullet | JNI bindings to Bullet, by the jolt-jni author | Pure-Java port of the Open Dynamics Engine |
| Licence | MIT (Jolt: MIT) | Apache-2.0 (Bullet: zlib) | BSD-style (Bullet: zlib); confirm before use | LGPL per Maven listing (ODE is LGPL/BSD dual); confirm before use |
| Latest release | 7.0.0 (2026-10-26); 6.2.0 (2026-10-14) | 1.14.2 (2026-06-05), same as our libGDX | 23.0.0 (2026-03) | 0.5.4 (2025-05) |
| Engine version | Current Jolt, actively developed (exact Jolt version per release not checked) | Bullet **2.87 (2017)**; the wrapper's last Bullet update was libGDX 1.9.8 | Recent Bullet (version not checked) | ODE port (version not checked) |
| Desktop platforms | Windows x64, Linux x64, macOS ARM64 (more listed: Linux/Windows ARM64, macOS x64) | All libGDX desktop targets (`natives-desktop`) | Windows, Linux, macOS (x64, ARM64) | Any JVM |
| Native loading | Separate natives jar + JSnapLoader (`io.github.electrostat-lab:snaploader`) | `gdx-bullet-platform` natives, loaded by libGDX | Bundled natives per platform | None (pure Java) |
| Memory | Cleaner thread frees native objects when unreachable, or explicit `close()`; shapes reference-counted | Manual `dispose()` of every `bt*` object (SWIG); leaks are easy | Automatic (cleaner) or explicit | Java GC |
| Multithreading | Job system, multi-core simulation | Single-threaded | Single-threaded | Single-threaded |
| Determinism | Deterministic across runs on the same build/platform when inputs match (documented by Jolt) | Not guaranteed | Not guaranteed | Not guaranteed |

## 2. Requirements (design doc §3.3, §6)

| Requirement | jolt-jni | gdx-bullet | Libbulletjme | ode4j |
|---|---|---|---|---|
| 3D collision detection, rigid bodies | Yes | Yes | Yes | Yes |
| Collision layers and filtering | Object layers + pair filter tables (matches `PhysicsLayers3D`) | Group/mask bits | Group/mask bits | Category/collide bits |
| Raycasts and shape queries | Narrow-phase queries: ray, shape cast, collide shape | Ray and convex sweeps | Ray and sweeps | Ray, collide |
| Triggers and contact events | Sensor bodies; contact listener with added/persisted/removed | Ghost objects; manifold polling, no removal callbacks | Ghost objects, contact callbacks | Contact joints each step, no enter/exit |
| Fixed-timestep simulation | Yes | Yes | Yes | Yes |
| Deterministic behaviour where achievable | Best of the four | Weak | Weak | Weak |
| Performance and resources | Multi-core; cleaner-managed memory | Single core; manual disposal | Single core | Slowest (pure Java), no natives |
| Desktop platform support | Good (ARM64 macOS included) | Good | Good | Universal |
| Fits ECS + scripting | Through `PhysicsBackend3D` | Same | Same | Same |
| Characters, vehicles, ragdolls, soft bodies (later phases) | All built in | Vehicles, soft bodies (old) | Vehicles, soft bodies | Limited |

## 3. Risks

- **jolt-jni:** a second native-loading mechanism (JSnapLoader) next to libGDX's; JNI crashes take the editor down
  (as with Box2D); the API mirrors C++ closely, so a thin wrapper is needed (planned: `PhysicsBackend3D`); a fast
  release cadence (7.0.0 three days ago), so pin a version and upgrade deliberately. One maintainer.
- **gdx-bullet:** wraps a 2017 Bullet that no longer evolves; manual disposal conflicts with TD-008's single-owner
  rule in practice; no enter/exit contact events. Lowest integration effort (same build as libGDX).
- **Libbulletjme:** same author and loading model as jolt-jni, older engine underneath.
- **ode4j:** LGPL/BSD dual licence needs a licence check before shipping; slowest; contact events would be ours to build.

## 4. Recommendation

Adopt **Jolt Physics via jolt-jni**, version **6.2.0** (7.0.0 is under two weeks old), in a new `engine-physics`
module (planned in `ARCHITECTURE_ASSESSMENT_AND_ROADMAP.md`) holding one class, `JoltPhysicsBackend3D implements
PhysicsBackend3D`. Use the `ReleaseSp` natives for Windows64, Linux64 and MacOSX_ARM64. Fallback if the spike
fails: gdx-bullet (no new native loader, but older engine and weaker events).

## 5. What is built now, independent of the choice

Engine (`engine-scene/.../physics3d`): `RigidBody3DComponent` (static/kinematic/dynamic, mass, damping, gravity
factor, friction, restitution, layer, mask), `BoxCollider3DComponent`, `SphereCollider3DComponent`,
`CapsuleCollider3DComponent` (offset, trigger), serializers, `PhysicsLayers3D`, `PhysicsUnits3D` (100 units = 1 m),
`PhysicsBackend3D`, and `PhysicsSystem3D` (fixed 1/60 s steps, body creation and removal, kinematic and dynamic
transform sync including parented entities, trigger bodies, contact and trigger events after the step, raycasts).
Editor: Add Component entries (colliders fit the selected mesh), Details editors, collider wireframes in the 3D
view. Play mode cannot simulate 3D yet: no backend exists until the dependency is approved.

## 6. Compatibility spike (needs the project owner)

Throwaway Gradle project outside the repository (in the agent's scratch folder `jolt-spike`): jolt-jni 6.2.0
(Windows64, `ReleaseSp` natives) + snaploader 1.1.1-stable on Java 21. It loads the natives, drops a sphere on a
static box for 3 s and prints `SPIKE OK` when it rests on the floor. The agent may not download and run third-party
code without permission, so the owner runs it:

```
gradlew -p <scratchpad>/jolt-spike run -q --console=plain
```

**Pass:** `SPIKE OK` with `ballY` near 0.5. **Fail:** a load error or exception, reported back for diagnosis.

## Sources

- jolt-jni: https://github.com/stephengold/jolt-jni, docs https://stephengold.github.io/jolt-jni-docs/
- Maven Central listings: `com/github/stephengold/jolt-jni-Windows64`, `com/badlogicgames/gdx/gdx-bullet`,
  `com/github/stephengold/Libbulletjme-*`, `org/ode4j/core`, `io/github/electrostat-lab/snaploader`
- libGDX CHANGES (Bullet 2.87 in 1.9.8): https://github.com/libgdx/libgdx/blob/master/CHANGES
