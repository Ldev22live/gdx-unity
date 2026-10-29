# Phase 15 — 3D Physics

**Decision:** TD-028 (Proposed) · **Evaluation:** [`docs/reports/3d-physics-evaluation.md`](../reports/3d-physics-evaluation.md)
**Unreal equivalent:** Chaos physics: collision primitives, simulate physics, collision channels, overlap events
**Status (2026-10-29):** engine-neutral parts implemented and integrated in the editor; simulation **blocked** on
approval of the physics library (recommended: Jolt via jolt-jni 6.2.0) after its compatibility spike.

## Steps

| Step | Content | Status |
|---|---|---|
| A — evaluation | Compare jolt-jni, gdx-bullet, Libbulletjme, ode4j against design doc §3.3; spike | **Done** (report); spike waits for the owner |
| B — components | `RigidBody3DComponent`, box / sphere / capsule colliders, layers and masks, serializers | **Done** |
| C — engine-neutral system | `PhysicsBackend3D` + `PhysicsSystem3D`: fixed step, body lifecycle, transform sync, triggers, contacts, raycast | **Done** (tested with a recording backend) |
| D — editor | Add Component ("Physics 3D"), Details editors, collider wireframes in the 3D view | **Done**; visual check pending |
| E — Jolt backend | `engine-physics` module with `JoltPhysicsBackend3D`; play mode runs `PhysicsSystem3D` | Blocked (dependency + new module need approval) |
| F — scripting | Contact callbacks and raycasts for scripts; forces and velocities | After E |

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

- No simulation until a backend is approved and written; play mode ignores 3D physics.
- Collider and body properties are read when a body is created; editing them during play has no effect.
- The sensor body of a dynamic entity follows it one frame late.
- `PhysicsSystem3D` scans the scene every frame (allocates a list); fine for editor-sized scenes, revisit with
  profiling.
- No mesh/convex-hull colliders, joints, characters or forces yet.
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
