# Web 3D Physics Evaluation (TD-058)

**Date:** 2026-11-08 · **Decision:** TD-058 (approved: "approve TD-058", as typed; this is a study, no engine is adopted) · **Start and end HEAD:** `f9849c2` (nothing committed; the working tree already held many uncommitted changes of the owner and earlier phases, left as they were)
**Recommendation:** Jolt Physics through its JavaScript build (**jolt-physics**, MIT), behind a TypeScript port of the desktop's engine-neutral `PhysicsBackend3D`. **Not adopted:** it needs a new decision record and the owner's approval (§7).

## 1. The gap

The desktop editor has 3D physics (Jolt through jolt-jni, TD-028) with rigid bodies, box / sphere / capsule / convex hull / mesh colliders, joints, character controllers and camera rigs (TD-048 to TD-052). The web player (`engine-babylon`) has none of it: four samples load and draw, but nothing moves (`physics-sandbox-3d`, `physics-joints-3d`, `physics-mesh-3d`, `character-playground-3d`; TD-057 report). The web player's 2D physics is planck.js; its scripts are TypeScript ports written by hand (`src/scripts/registry.ts`), and the two samples that carry a script (`SandboxLauncher`, `WalkerPlayer`) have no web port yet.

## 2. What was measured

Scripts in `tools/web-physics-spike/` (not part of any build), run with Node 26.12.1 on this Windows machine, each in a folder outside the repository. Each engine drops a 0.5 m sphere from 5 m onto a static floor for 240 steps of 1/60 s, then drops 300 boxes (0.5 m) in a grid for 180 steps.

| | Jolt (jolt-physics 1.1.1, `wasm-compat` / `wasm`) | Rapier (@dimforge/rapier3d-compat 0.21.0) | Havok (@babylonjs/havok 1.3.14, low-level API) |
|---|---|---|---|
| Licence (npm field) | MIT | Apache-2.0 | MIT for the package; **the Havok engine's own terms were not checked** |
| Load time in Node | 89 ms / 124 ms | 161 ms | 37 ms |
| Sphere comes to rest at (expected 0.5) | 0.48 (2 cm slop, as the desktop Jolt) | 0.50 | 0.50 |
| 300 boxes, 180 steps | 5.6 ms per step (`wasm-compat`), 5.2 ms (`wasm`) | 6.0 ms per step | 2.1 ms per step |
| Download, gzipped | `wasm`: 137 KB script + 747 KB wasm; `wasm-compat` (wasm inside the script): 913 KB | 1.66 MB (wasm inside the script) | 13 KB script + 662 KB wasm |
| Joints | hinge, slider, distance, fixed, point (ball), motors: present | revolute, prismatic, fixed, spherical, rope, motors: present | constraint API present (37 `HP_Constraint_*` functions); joint kinds not enumerated one by one |
| Mesh and hull shapes | triangle mesh, convex hull: present | trimesh, convex hull: present | mesh, convex hull, height field: present |
| Character controller | `CharacterVirtual`: present (the one the desktop uses) | kinematic character controller: present | none in the low-level API; Babylon's `PhysicsCharacterController` sits above it |
| Ray and overlap queries, contact events | ray, shape queries, contact listener: present | ray, shape intersections, event queue: present | ray and shape casts with a collector: present |

How to read the numbers: one machine, Node (V8) not a browser, a single run each, and the three piles did not end in the same state (highest box 2.7, 2.8 and 3.2 m after 3 s), so they show that all three handle a few hundred bodies well inside a 16 ms frame, not which is faster in a game. Only presence of an API was checked for joints, characters and queries; **none of those was exercised**.

## 3. Fit with the desktop

- **Same engine family.** `JoltPhysicsBackend3D` (937 lines of Java) uses the Jolt classes the JavaScript build exposes under the same names (`BodyCreationSettings`, `ConvexHullShapeSettings`, `MeshShapeSettings`, `HingeConstraintSettings`, `CharacterVirtualSettings`, `ContactListenerJS`, object layer tables). Porting it is translation, not redesign, and behaviour should match the desktop closely: the sphere rested 2 cm into the floor in both.
- **What is not known:** the JavaScript library's Jolt version against jolt-jni 6.2.0 (its README mentions v5.5.220 in an unrelated context; the real version was not checked), and whether the published builds are compiled for cross-platform determinism (a build option the README documents; the published files were not examined).
- **Rapier and Havok** would each need a new backend written to their own model (collision groups instead of object layers, a different character controller and joint set), so the same scene would not behave the same on desktop and web.
- **Where the logic lives:** `PhysicsSystem3D` (1,048 lines) is engine-neutral Java: body creation, kinematic and character sync, scene units, events, raycasts. The web needs a TypeScript version of it as well as of the backend; roughly 2,000 lines of Java to translate, plus the camera rig (about 400), before scripts.

## 4. Risks

- **Jolt JS:** manual memory management (objects created with `new Jolt.X()` must be released with `Jolt.destroy`); download about 0.9 MB gzipped; the single-thread builds are used (the multithread builds need special HTTP headers); one main maintainer; version drift against jolt-jni; no browser measurement yet.
- **Rapier:** largest download of the three; a different feel than the desktop.
- **Havok:** engine licence terms unverified; Babylon-level characters and joints behave differently from Jolt's.
- **All:** only a static scene was simulated; no joints, characters or mesh shapes were run.

## 5. Options

| Option | Effort | Result |
|---|---|---|
| **A. Jolt JS, ported backend and system** (recommended) | largest, but mostly translation | same behaviour as the desktop, one set of tests can be mirrored |
| B. Havok through Babylon's physics plugin | medium | fastest, smallest script; differs from the desktop; licence to check |
| C. Rapier | medium | most different feel, largest download |
| D. Leave 3D physics desktop-only and say so in the web player | none | the four samples stay still on the web |

## 6. Recommendation

Option A. Build it in steps, each one a milestone with its own approval: (1) the backend and system port with bodies, colliders, layers, ray queries and events, the `SandboxLauncher` port and `physics-sandbox-3d` running; (2) joints, convex hull and mesh colliders (`physics-joints-3d`, `physics-mesh-3d`); (3) the character controller, the camera rig and the `WalkerPlayer` port (`character-playground-3d`). Tests run headless in Node, as the spike did, and mirror the desktop's Jolt tests (for example a body resting on a floor, a hinge holding, a character grounded). Fallback: Havok (B) if the browser measurement shows frame-time or size problems.

## 7. Open questions for the owner

1. Is web parity for 3D physics wanted at all, or is option D acceptable?
2. If A: approve a TD-059 record for step 1 above?
3. Are about 0.9 MB more to download for the web player acceptable?

## 8. Milestone record

| Item | Result |
|---|---|
| Candidates measured | three, with the scripts in `tools/web-physics-spike/` |
| Repository code changed | none (new files only: this report, the spike scripts and their README, the TD-058 record, matrix and INDEX rows) |
| Tests | no repository code changed, so the test suites were not re-run for this milestone; the last full runs are in the TD-056 and TD-057 reports |
| Downloads | the three engines from the npm registry, into a scratch folder outside the repository |
| Manually Not Verified | any browser run; joints, characters, mesh shapes and events on any candidate; Havok's licence; Jolt JS version and determinism |
| Git | no commit, push or other Git write |
