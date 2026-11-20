# M6.4: Navigation and Behaviour

**Date:** 2026-11-17. **Branch:** `phase-12-3d-foundation`. **HEAD at start and end:** `0469ceb` (nothing committed). **Gate:** none in the chart. [TD-092](../TECHNICAL_DECISIONS.md) records the design for the owner's review, because the milestone adds two component types to the scene file, a navigation package and three graph nodes.

**Why this milestone:** after M6.2 it was the next startable row in phase order. Its dependency, M3.3 (terrain collision), is built, and it has no gate. M6.1 and M6.3 need a decision record first, and M2.3 needs approval.

## 1. Acceptance criteria

| # | Criterion (chart row M6.4) | Status | Evidence |
|---|---|---|---|
| 1 | Navmesh baking | **Met, as a walkable grid** | `NavMeshBaker` bakes a Nav Mesh Bounds box from terrain and static colliders, with clearance, steps, slopes and headroom (`NavigationTest`, 12). It is a 2.5D grid, not a polygon navmesh; TD-092 gives the reasons and limits |
| 2 | Agent component | **Met** | Nav Agent: Move To, Stop, Has Arrived, status. It walks through its Character Controller 3D on physics, or moves its transform, and faces its path |
| 3 | Behaviour graphs | **Met** | Nodes Move To (Moving and Unreachable outputs), Stop Moving and Has Arrived. They work in the interpreter (`GraphNavigationNodesTest`, 4) and in generated Java and Kotlin, which were compiled and run (`GraphNavigationCodeTest`) |
| 4 | Done-when: "An agent paths around an obstacle" | **Met** | `NavigationGuardOnJoltTest`, on real Jolt in the game runtime's session: a guard with a character controller, told by a graph file to cross a 12 m wall, walks round its end, never comes within its capsule radius of it, arrives and stops. In the game runtime: [frame 90](m6-4/courtyard-90.png) heading for the wall's end, [200](m6-4/courtyard-200.png) rounding it, [480](m6-4/courtyard-480.png) on the green goal (looked at; 0 errors each) |
| 5 | (Editor) | **Met** | Add Component > Navigation (hidden in 2D), Details for both. A selected bounds draws its walkable outline ([editor-bounds.png](m6-4/editor-bounds.png), top view, looked at): green lines either side of the wall, kept clear of it, and inside the box's edge |

## 2. What was built

- **`engine-scene`, package `navigation`:**
  - `NavMeshBoundsComponent` and `NavAgent3DComponent`, with `serialization/NavigationSerializers`.
  - `NavMeshBaker` turns solids into spans, picks the floor, computes the climb and the clearance.
  - `NavGrid3D` holds the grid:
    - A* with a binary heap and octile distance, never cutting a corner;
    - the path is straightened along lines checked every quarter cell;
    - the nearest walkable cell is used for an end off the walkable area;
    - `outline` gives the walkable area's edges for drawing.
  - `NavigationSystem3D` bakes each scene once, on demand, and keeps it on `Scene` (`get/setNavigation`); `bake` gives a check without keeping it.
  - `Transform.getWorldPosition(Vector3)` was added, so agents read their position without allocating.
- **`engine-visualscript`:**
  - `BuiltInNodes` gains Move To, Stop Moving and Has Arrived;
  - `GraphCodeGenerator` emits calls to `NavAgent3DComponent`;
  - `GraphTypeScriptGenerator` refuses them with "needs navigation, which the web player does not run yet".
- **`engine-desktop`:** the serializers are registered in `GameComponentSerializers` (43 types).
- **`editor-core`:**
  - catalog entries "Nav Mesh Bounds" and "Nav Agent" in the Navigation category, which `GameModeProfile` hides in 2D;
  - the agent tool `navigation_check` (read-only).
- **`editor-desktop`:**
  - `NavigationComponentEditorProviders` (registered in `InspectorPanel`);
  - `Collider3DRenderer` draws a selected bounds' box and walkable outline;
  - `TemplateMatrix` counts navigation as gameplay.
- **Tests:**
  - new: `NavigationTest` (12), `GraphNavigationNodesTest` (4), `GraphNavigationCodeTest` (1, both languages), `NavigationEditingTest` (4), `NavigationGuardOnJoltTest` (1, writes `engine-desktop/build/m6-4-nav`);
  - updated: `GameComponentSerializersTest` (43).

## 3. Commands and results

| Command | Exit | Result |
|---|---|---|
| `./gradlew :engine-scene:test --tests "*NavigationTest"` | 1, then 0 | 4 of 12 failed first, all on the tests' own expectations, not the code (section 4). Then 12 of 12 |
| `./gradlew :engine-visualscript:test --tests "*GraphNavigationNodesTest"` | 0 | 4 of 4 |
| `./gradlew :editor-visualscript:test --tests "*GraphNavigationCodeTest"` | 0 | 1 of 1: Java and Kotlin compile, run, and reach the goal |
| `./gradlew :engine-desktop:test --tests "*NavigationGuardOnJoltTest" --tests "*GameComponentSerializersTest"` | 0 | 1 + 1 |
| `GDXUNITY_SCREENSHOT=... ./gradlew :engine-desktop:run --args="--project <engine-desktop/build/m6-4-nav> --scene scenes/Courtyard.json --smoke-test=<90, 200, 480>"` | 0 each | "N of N frames, 0 runtime error(s)"; the log shows "Patrol on Guard: walking" |
| `APPDATA=<scratch folder with a one-pane Y axis layout> GDXUNITY_EDITOR_ASSISTANT=0 GDXUNITY_EDITOR_SELECT="Nav Mesh Bounds" GDXUNITY_SCREENSHOT=... ./gradlew :editor-desktop:run --args="--project <same> --scene scenes/Courtyard.json --smoke-test"` | 0 | 180 frames, 0 errors. The scratch `APPDATA` gave the run a top view without touching the owner's saved layout, which was checked afterwards and is unchanged |
| `./gradlew :engine-scene:test` | 0 | **212**, 0 failures |
| `./gradlew :engine-visualscript:test` | 0 | **90** |
| `./gradlew :engine-render:test` | 0 | **199** |
| `./gradlew :engine-desktop:test` | 0 | **117** |
| `./gradlew :editor-core:test` | 0 | **531**, 1 skipped |
| `./gradlew :editor-visualscript:test` | 0 | **93** (before the new code test; that test was then run on its own, 1 of 1) |
| `./gradlew :editor-desktop:test` | 0 | **163** |

The full `./gradlew test --continue` was not run: the host stopped it twice before for low memory. Instead, every module this milestone touches was run on its own. The earlier counts from M6.2 were engine-scene 200, engine-render 199, engine-desktop 116, editor-core 527 and editor-desktop 163.

## 4. Failures found and fixed

- **Four first-run failures of `NavigationTest` were wrong expectations:**
  - a terrain height checked at x -10 where the cell's centre is -12.5 (98.75 is right);
  - a wall's top expected to have no floor, while its top within the box is correctly a separate, unreachable floor (and wide enough for an agent);
  - a facing expected along +x, while the last stretch runs diagonally from the wall's corner.
  
  Each test now asserts the right fact.
- **An editor camera testing aid was tried and removed.** I added `GDXUNITY_EDITOR_CAMERA` to place the view's camera for the screenshot. It set the main camera, but the pane loaded from a layout file draws through its own camera, so it had no visible effect. I removed it (code and test) rather than leave an aid that does nothing, and used the existing orthographic top view through a scratch layout instead.

## 5. Diff review

- **New:**
  - `engine-scene/.../navigation/` (`NavMeshBoundsComponent`, `NavAgent3DComponent`, `NavMeshBaker`, `NavGrid3D`, `NavigationSystem3D`, `serialization/NavigationSerializers`);
  - `editor-core/.../agent/tools/NavigationCheckTool`;
  - `editor-desktop/.../inspector/NavigationComponentEditorProviders`;
  - the five test classes;
  - this report and `docs/reports/m6-4/`.
- **Changed:**
  - `Scene` (navigation field), `Transform` (`getWorldPosition(Vector3)`);
  - `BuiltInNodes`, `GraphCodeGenerator`, `GraphTypeScriptGenerator`;
  - `GameComponentSerializers` and its test;
  - `ComponentCatalog`, `GameModeProfile`, `DefaultAgentTools`;
  - `InspectorPanel`, `Collider3DRenderer`, `TemplateMatrix`;
  - `EditorApplication`: the camera aid was added and then fully removed, leaving no net change from this milestone;
  - docs: TD-092, chart, FEATURE_TEST_MATRIX, INDEX, AI_AGENT_ARCHITECTURE_AUDIT.
- **Pre-existing uncommitted changes.** Several changed files already carried earlier milestones' uncommitted changes (for example `ComponentCatalog`, `EditorApplication`, `TemplateMatrix`). They were kept; only the lines listed here were added.
- **Outside the repository:** nothing was written except the scratch folder used as `APPDATA` for one editor run, which is in the session's temporary directory. The owner's `%APPDATA%\GdxUnity\viewport_layout.json` was read to understand the view and not changed.

## 6. Known limitations and next steps

- **A grid, not a polygon navmesh:**
  - one floor per cell, so no bridges or stacked floors;
  - a low beam's top becomes that cell's floor (an island) rather than a roof over walkable ground;
  - mesh, hull and capsule colliders count as their boxes.
- **No off-mesh links:** no jumps, ladders or doors.
- **Static bake:**
  - it is made at the first query and kept; obstacles moved later need `NavigationSystem3D.rebake()`, which no node or menu calls yet;
  - agents do not avoid each other or moving bodies.
- **Cost:** the bake runs on the game thread at the first Move To, up to 1,000,000 cells per box (refused above that, with the reason). Bake time was not measured; a large terrain map could pause play the first time (a candidate for M9.5's baselines).
- **Not built:**
  - a Bake button or a saved bake;
  - a path preview in the editor (only the walkable outline);
  - behaviour trees (the behaviour is the graph);
  - steering and crowds;
  - the web player (no component support, and TypeScript generation refuses the nodes);
  - 2D navigation.
- **Manually Not Verified (graphics):**
  - dragging an obstacle in the editor and watching the outline follow;
  - pressing Play in the editor with an agent (the game runtime and the headless session were checked);
  - mouse editing in Details.
- **Next by phase order:**
  - M6.1 (particles) and M6.3 (in-game UI) need decision records; M2.3 needs approval.
  - Unblocked: M9.3 to M9.5 pipeline and the audits MC.1, MP.1 and MT.8.

## 7. Git

No commit, push, stash, reset or other Git operation was performed.
