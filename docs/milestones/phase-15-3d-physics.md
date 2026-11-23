# Phase 15 — 3D Physics

**Decision:** TD-028 (Proposed); script access TD-048 (Approved); character controller TD-049 (Approved) · **Evaluation:** [`docs/reports/3d-physics-evaluation.md`](../reports/3d-physics-evaluation.md)
**Unreal equivalent:** Chaos physics: collision primitives, simulate physics, collision channels, overlap events
**Status (2026-11-07):** engine-neutral parts, the Jolt backend (step E), script access (step F) and the character controller (step G) implemented; editor Play and
the game runtime simulate 3D physics and scripts and graphs can act on it. Visual check pending. Reports:
[phase-15e-report.md](../reports/phase-15e-report.md), [phase-15f-report.md](../reports/phase-15f-report.md), [phase-15g-report.md](../reports/phase-15g-report.md).

## Steps

| Step | Content | Status |
|---|---|---|
| A — evaluation | Compare jolt-jni, gdx-bullet, Libbulletjme, ode4j against design doc §3.3; spike | **Done** (report); spike passed 2026-11-06 (`JoltPhysicsBackend3DTest`) |
| B — components | `RigidBody3DComponent`, box / sphere / capsule colliders, layers and masks, serializers | **Done** |
| C — engine-neutral system | `PhysicsBackend3D` + `PhysicsSystem3D`: fixed step, body lifecycle, transform sync, triggers, contacts, raycast | **Done** (tested with a recording backend) |
| D — editor | Add Component ("Physics 3D"), Details editors, collider wireframes in the 3D view | **Done**; visual check pending |
| E — Jolt backend | `engine-physics` module with `JoltPhysicsBackend3D`; play mode runs `PhysicsSystem3D` | **Done** (Play and `GameSession`, sample `physics-sandbox-3d`, parity test); visual check pending |
| F — scripting | Contact callbacks and raycasts for scripts; forces and velocities | **Done** (TD-048): scripts, graphs, forces, impulses, velocities, raycast, overlap; Play and game runtime; visual check pending |
| G — character controller | Capsule character on Jolt `CharacterVirtual`: slopes, steps, jump, grounded; script API, graph nodes, editor, sandbox player | **Done** (TD-049): component, Jolt `CharacterVirtual`, script API, graph nodes, editor, sample `character-playground-3d`; visual check pending |

### Step F scope (TD-048; built as listed, see the report)

1. Scene hook so scripts reach `PhysicsSystem3D` (null when the scene has no 3D colliders).
2. Script callbacks `onCollisionEnter3D/Exit3D`, `onTriggerEnter3D/Exit3D` through one forwarder shared by Play and the game runtime.
3. Backend and system API: forces and torques (applied in every sub-step), impulses, linear and angular velocity, sphere overlap; clear errors for static or kinematic bodies.
4. Graph nodes Raycast 3D, Add Force, Add Impulse, Set Velocity, Get Velocity, plus 3D contacts for the existing event nodes; Java and TypeScript generators and parity tests.
5. `physics-sandbox-3d` gets a script using it; tests per item; report `phase-15f-report.md`; matrix and index updated.

Acceptance (marked met/not met in the report): a script receives each of the four contact callbacks in both Play and the game runtime;
a force applied every frame for one simulated second gives the same speed at 30 and at 60 frames per second (this replaces the
draft's wording about "1 or 5 sub-steps", which described the opposite of the intent); impulses and velocities change the Jolt body; static/kinematic/no-body
calls throw with the entity's name; graphs using the new nodes generate Java and TypeScript matching goldens (or are refused on web, per open question 1).

## Behaviour (as implemented)

- 100 scene units = 1 m (`PhysicsUnits3D`), the same convention as 2D physics.
- An entity with enabled 3D colliders gets a body; without a Rigid Body 3D it is static.
- Trigger colliders form a separate sensor body that follows the entity; contacts with it are reported as
  TRIGGER_ENTER / TRIGGER_EXIT, solid contacts as COLLISION_ENTER / COLLISION_EXIT.
- Two bodies collide only when each one's mask contains the other's layer (16 layers).
- Scale: boxes scale per axis; spheres use the largest axis; capsules use the largest of X/Z for the radius and Y
  for the height. The wireframes use the same rule.
- Listeners run after the step; entities they destroy lose their bodies before the next step. Deactivated
  entities leave the simulation.

## Known limitations

- Bodies never sleep and the simulation runs on one thread (TD-028): fine for sample-sized scenes, not profiled for large ones.
- Native library: Windows x64, Linux x64 and macOS arm64 only; only Windows was run. Game export is Windows-only anyway (TD-046).
- A 3D body whose entity is destroyed gets no exit event (`PhysicsSystem3D` drops events for entities without a body).
- The web players (`engine-web`, `engine-babylon`) do not simulate 3D physics.
- Collider and body properties are read when a body is created; editing them during play has no effect.
- The sensor body of a dynamic entity follows it one frame late.
- `PhysicsSystem3D` scans the scene every frame (allocates a list); fine for editor-sized scenes, revisit with
  profiling.
- No mesh/convex-hull colliders or joints. A walking character is the Character Controller 3D (15-G): one capsule per entity, no moving platforms, no teleport, rotation left to scripts. Forces, impulses, velocities, raycasts and sphere overlaps are available to scripts and graphs (15-F); a Raycast 3D node has no layer mask yet.
- The 3D graph nodes and script callbacks run on the desktop only (editor Play and the game runtime); the web editor refuses them with a message.
- Collider wireframes allocate per frame, like the selection outline.

## Tests

`PhysicsSystem3DTest` (9), `Physics3DSerializationTest` (2), `Physics3DCatalogTest` (1), `ComponentCatalogTest`
(updated). `./gradlew test --continue` → exit 0, **374 tests, 0 failures** (editor-core 251, editor-desktop 14,
engine-core 27, engine-render 27, engine-scene 55). Before Phase 15: 362.

## Manual checklist (when visual testing resumes)

1. Create → Box; Add Component → Box Collider 3D: a green wireframe matches the box. Change its size in Details;
   Ctrl+Z restores it.
2. Add Sphere Collider 3D, tick Trigger: a cyan sphere around the box.
3. Scale the entity 2× on X: the box wireframe stretches, the sphere grows to the largest axis.
4. Deselect: wireframes disappear; turn on Physics Debug in the 3D view: every collider shows.
5. Add Rigid Body 3D; set Mass 0: refused with a message, history unchanged.
6. Save and reopen the scene: all physics values are kept.
