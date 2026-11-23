# M3.6: Scatter Tools

**Date:** 2026-11-20
**Branch:** `phase-12-3d-foundation`
**HEAD at start and end:** `2391dad` (nothing committed in this milestone)

Roadmap milestone **M3.6** of R3, after M2b.4 (which it waited on). The chart lists no gate. Decision record: [TD-097](../TECHNICAL_DECISIONS.md), recorded for the owner's review.

**Status: Implemented and Integrated.** The level editor's Objects drawer has a **Scatter** tool:
- a drag scatters the chosen object inside the brush, and Ctrl-drag removes copies of it;
- settings for density, spacing, slope limit, scale range and height range;
- keep-out zones around buildings and the player;
- one undo step per stroke.

The 3D renderer now skips shapes and models out of view.
**Manually Not Verified:** no person has dragged the tool with the mouse. The drag was driven through the viewport interaction in tests, and the palette was seen in the running editor.

## 1. Acceptance criteria

The chart's deliverable is "foliage and rocks with density, random scale and rotation, slope and height limits, exclusion zones, instanced rendering, from prefabs". Its done-when is "scatter pines on a slope with measured frame cost; one undo step".

| Criterion | Result | Evidence |
|---|---|---|
| From prefabs | **Met** | Any Objects prefab: the shipped trees and rocks, the CC0 ones included. Scattered copies are ordinary prefab instances, with their colliders (TD-096). |
| Density | **Met** | Objects per 10 m × 10 m. A dab fills its circle to the density, counting copies already there, so going over the same ground again adds nothing. `ScatterPlannerTest` (fill and top-up); `LevelEditingTest.shouldRefuseSteepGroundAndTopUpRatherThanPileUp`. |
| Random scale and rotation | **Met** | A random turn, and a scale from the set range (0.8 to 1.2 by default). Tested on every scattered tree. |
| Slope and height limits | **Met** | **Slope:** a 50° slope gets nothing under a 35° limit, and a 25° one fills. In the level, pines fill a 25° slope and none land on its 55° part.<br>**Height:** only ground inside the range gets objects.<br>`ScatterPlannerTest`, `LevelEditingTest`. |
| Exclusion zones | **Met, as automatic zones** | **Where nothing goes:** holes, under water, off the terrain, within the spacing of any placed object, inside a building's footprint (from its box colliders), and within 1.5 m of the player or the spawn point. Tests: `ScatterPlannerTest` and `ScatterForestTest` (none inside the CC0 House or at the player's start).<br>**Not built:** zones a person draws. TD-097 records the choice. |
| Instanced rendering | **Partly met: culling, not instancing** | **Built:** `ObjectCulling` and `SceneRenderer3D`. The lit and depth passes draw only objects in view, and the shadow pass only those in the shadow map's area.<br>**Not built:** GPU instancing (GL ES 2.0 and `ModelBatch` have none), batching by mesh, and lower-detail models (planned in TD-096). |
| Done-when: pines on a slope, one undo step | **Met** | **`LevelEditingTest.shouldScatterPinesOnASlopeWithinTheSlopeLimitAsOneUndoStep`:**<br>- one stroke over a 25° slope that turns 55° further up;<br>- at least 10 trees on the slope, each standing on the ground within the slope limit and scaled in range;<br>- none on the steep part;<br>- one undo step ("Scatter Tree x N"): undo removes them all, redo brings them back.<br>**`ScatterForestTest` (shipped palette, editor Play on real Jolt):** 265 pines and 91 CC0 oaks over a hill in 20 strokes, one undo step each; none in the house, at the start or over 30°. |
| Done-when: measured frame cost | **Met** | **The forest:** 1,536 entities.<br>**Physics** (editor Play): 0.9 s to start; a step's median 1.6 ms, p95 3.3 ms.<br>**Drawing** (game runtime, GPU-finished frame times, Intel Iris Xe, 330 frames, two runs each): 19.3 and 20.4 ms a frame with culling, 21.2 and 24.0 ms without (`GDXUNITY_OBJECT_CULLING=0`). That is 9 to 15 % less in this view, with a couple of milliseconds of noise between runs.<br>Frame: [scatter-forest-runtime.png](m3-6/scatter-forest-runtime.png). |
| Integrated | **Met** | **Seen in the editor** (`GDXUNITY_EDITOR_DRAWER=Objects:Scatter`): the Scatter tool, the object tiles, Brush Size, Density, Spacing, Max Slope, Scale Min/Max and Lowest/Highest sliders, and the forest. 240 frames, 0 errors. Screenshot: [scatter-palette-editor.png](m3-6/scatter-palette-editor.png).<br>**Tested by drag:** a drag through `LevelViewportInteraction` scatters, and a Ctrl-drag removes. Each is one undo step. |

## 2. What was built

**`editor-terrain`, new:**
- `ScatterSettings`: checked settings;
- `ScatterPlanner`: headless, seeded; refusals counted by reason.

**`editor-terrain`, changed:**
- `LevelSession`:
  - `Tool.SCATTER`, offered by Objects and the full Level palette;
  - `beginScatter` / `scatterAt` / `endScatter`, with the obstacles and keep-out zones read from the scene.
- `LevelViewportInteraction`: drag and Ctrl-drag for Scatter.

**`editor-core`:**
- `SceneSnapshotCommand.begin` / `Recording.finish` / `cancel`, for a change made live and recorded as one step;
- `PrefabService.placeUnrecorded`. The existing placement command now shares its code.

**`engine-render`:**
- `ObjectCulling`: bounding spheres, the view test, and models kept by weak reference;
- `SceneRenderer3D`: objects culled in the lit, depth and shadow passes; `setObjectCulling`, `getObjectsDrawn`, `getCullableObjects`.

**`engine-desktop`:** `GameRenderer.setObjectCulling`, and `GDXUNITY_OBJECT_CULLING=0` in `DesktopLauncher`.

**`editor-desktop`:** `LevelPalette`'s Scatter row (object tiles and the setting sliders) and its hint.

**Tests:**
- new: `ScatterPlannerTest` (7), `SceneSnapshotCommandTest` (2), `ObjectCullingTest` (2), `ScatterForestTest` (1);
- `LevelEditingTest` (+4: the done-when, steep ground and topping up, drag and Ctrl-drag, where Scatter is offered).

**Choices made without asking** (in TD-097):
- **Exclusion zones are automatic.** There is no painted zone and no new component, so the scene format is unchanged.
- **Scatter is in Objects only,** because buildings stand square one at a time.
- **Objects appear live during the drag,** and the stroke is recorded once at the end.
- **Culling comes before batching and LOD,** as TD-096 planned.

## 3. Commands and results

Each Gradle run used `JAVA_TOOL_OPTIONS=-Xmx700m`, `--offline --max-workers=1`, because memory stays short (about 1.4 to 2 GB free).

| Command | Exit | Result |
|---|---|---|
| `:editor-core:test --tests "*SceneSnapshotCommandTest" "*EditorCommandsTest"`, `:engine-render:test --tests "*ObjectCullingTest"`, `:editor-terrain:test --tests "*ScatterPlannerTest" "*LevelEditingTest" "*StarterPackPlacementTest"`, `:editor-desktop:compileJava`, `:engine-desktop:compileJava` | 1 | Compiled. 3 failures, all wrong expectations in the new tests, fixed (below) |
| The same classes again | 0 | All passing |
| `:editor-desktop:test --tests "*ScatterForestTest"` | 0 | 1 passing; 265 pines, 91 oaks, 20 strokes |
| `:editor-core:test --tests "*Prefab*" "*CompoundCommandTest"`, `:editor-terrain:test` (whole module), `:engine-render:test --tests "*SceneRenderer3DTest" "*ObjectCullingTest"`, `:editor-desktop:test --tests "*LevelPaletteSliderTest"` | 0 | editor-core 23, **editor-terrain 63 (whole module)**, engine-render 10, editor-desktop 1; 0 failures |
| `GDXUNITY_RENDER_STATS=1 [GDXUNITY_OBJECT_CULLING=0] :engine-desktop:run --args="--project <build>/m3-6-scatter-forest --scene scenes/level.json --smoke-test=330"`, four runs | 0 each | 330 frames, 0 runtime errors each; the times above |
| `GDXUNITY_EDITOR_WINDOW=1440x900 GDXUNITY_EDITOR_DRAWER=Objects:Scatter :editor-desktop:run --args="--project <build>/m3-6-scatter-forest --scene scenes/level.json --smoke-test=240"` | 0 | 240 frames, 0 errors. A first run without the window size saved a 0×0 picture, the window having been hidden. |

**The three test fixes:**
- **Tools offered by Objects.** An existing assertion listed only Place and Erase; Scatter was added.
- **The slope test's check.** The brush also reaches the flat ground below the slope, so the check became "not on the steep part", plus "at least 10 on the slope".
- **A "no scene" test removed.** `EditorState` always has a scene, so that check cannot be reached.

**After the screenshot:** the Scatter hint was shortened, because it ran past the panel's edge. That is a string change, compiled in the editor run.

**Babylon suite:** not run. There is no new component type, graph node or scene-format change.

### Full module runs

**Done:** the whole of `editor-terrain`.

**Still pending** (low memory), for M2b.2 to M3.6:
- full `editor-core`, `engine-render` and `editor-desktop` runs;
- the package rebuild.

## 4. Known limitations and next steps

**Limitations:**
- **No GPU instancing, batching or LOD.** Culling helps only with what is out of view.
- **No painted exclusion zones.** Paths are not excluded unless they are holes or water.
- **Trees can hide the player.** Scattered trees can stand between the Action RPG camera and the player, as in the runtime frame.
- **No brush circle** is drawn in the view for Scatter.
- **Already there before this milestone, seen in the screenshot:**
  - the tool buttons run together ("PlaceEraseScatter");
  - the fourth thumbnail column is clipped at this panel width.
- **One machine and few runs** for the timings.

**Next on the chart:** M3.10, which needs M3.6, and R2b's M2b.5 to M2b.7.

## 5. Diff review and Git

**New:**
- code: `ScatterSettings`, `ScatterPlanner`, `ObjectCulling`;
- tests: the four new test classes;
- this report and `docs/reports/m3-6/`.

**Changed:**
- code: `LevelSession`, `LevelViewportInteraction`, `SceneSnapshotCommand`, `PrefabService`, `SceneRenderer3D`, `GameRenderer`, `DesktopLauncher`, `LevelPalette`;
- tests: `LevelEditingTest`;
- docs: `TECHNICAL_DECISIONS.md` (TD-097), `FEATURE_TEST_MATRIX.md`, `INDEX.md`, both chart copies, `AGENTS.md` (the new testing variable).

**Uncommitted from M2b.4** (since `2391dad`):
- its test fixes;
- TD-096's numbers;
- its report and doc updates.

**Git:** no commit, push or other Git operation was performed in this milestone.
