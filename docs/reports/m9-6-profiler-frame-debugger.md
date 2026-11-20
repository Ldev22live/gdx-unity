# M9.6: Profiler and Frame Debugger

**Date:** 2026-11-18. **Branch:** `phase-12-3d-foundation`. **HEAD at start and end:** `0469ceb` (nothing committed). **Gate:** none in the chart.

**Why this milestone:** M9.5 (its only dependency) was done, and M9.6 is the last open row of R9 that can start. The other startable
rows (MC.1, MP.1, MT.8) are audits. MC.1 needs web research into licences, so it was left for the owner to start.

## 1. Acceptance criteria

| # | Criterion (chart row M9.6) | Status | Evidence |
|---|---|---|---|
| 1 | Deliverable: "A panel" | **Met** | A **Profiler** tab in the editor's bottom dock, next to Content Browser and Output Log ([screenshots](#5-evidence-in-the-running-editor)) |
| 2 | Done-when: "It shows a frame breakdown" | **Met** | For the frame picked in a history graph, the panel shows where its time went: Play (2D physics, 3D physics, the scene update and, below that, each component type, each script and each graph), the scene view (3D scene, 2D scene, overlays as "Other"), editor update, editor UI and "Untimed". Each line has its time, the average over the last 60 frames, its share of the frame, how many times it ran, and its draw calls, vertices, texture binds, shader switches and GL errors |
| 3 | "Frame debugger" in the milestone's name | **Partly met** | The panel shows each pass's GL work (the counts in row 2) and can freeze a frame to study it. It **cannot** step through one frame's draw calls or show a pass's render target. See section 6 |

## 2. What was built

- **`FrameProfiler` (`engine-core`, new package `engine.core.profile`):**
  - named sections nested under a parent, timed with `begin`/`end`; a section timed several times in one frame adds up;
  - frame values such as the entity count;
  - a `CounterSource` whose counts are recorded per section, as the growth while the section is open;
  - the last 300 frames, kept in arrays allocated up front, so recording a frame allocates nothing;
  - each frame's work and its *period* (start to the next frame's start; filled in when the next frame begins);
  - pause, which freezes the history.

  Nothing is recorded unless the profiler is recording and not paused, so the hooks stay in the frame loop and cost a boolean check otherwise.
- **Scene hook (`engine-scene`):**
  - `Scene.setProfiler(profiler, parent)` makes `Entity.update` time each component's start and update in a section per component type.
  - A script binding or graph is timed under its own label, from the new `EntityLogicComponent.getProfilerLabel()`: "Script HopperPlayer", "Graph Hopper.graph". Otherwise every script would be lumped together as "EntityScriptBindingComponent", which is what the first run showed.
  - Labels are cached, so profiling builds no strings per frame.
- **Play hook (`editor-core`):** `PlayModeController.setProfiler` registers "Play", with "2D physics", "3D physics" and "Scene update" under it, and attaches the runtime scene on every Play.
- **`ProfilerReport` (`editor-core`, headless):**
  - turns one frame into rows: a tree with the slowest sibling first;
  - adds an "Other" row for a parent's time outside its timed children, and an "Untimed" row for the frame's time outside every section, so each level adds up to its parent;
  - adds a summary (work, period, frames a second, waiting, average, slowest, frame values);
  - `toText` gives the frame as plain text, for Copy frame and for the smoke test.
- **Editor (`editor-desktop`):**
  - **`GlCounters`:** reads LibGDX's `GLProfiler` for draw calls, vertices, texture binds and shader switches. It counts GL errors rather than logging each one, and resets every frame.
  - **`FrameHistoryGraph`:** one bar per frame, newest on the right. The solid part is the work and the faint part above it is the waiting, with 60 and 30 fps lines.
    - The scale is fixed at 50 ms, so a 300 ms loading frame no longer flattens the rest, as it did in the first screenshot.
    - Slow bars are drawn in the warning colour.
    - Clicking a bar pauses recording and picks that frame.
  - **`ProfilerPanel`:**
    - buttons for Pause/Resume, Newest, "Draw calls: counted/off" and Copy frame;
    - a summary line, the graph, and the breakdown table;
    - while recording, the table is rebuilt four times a second so the numbers can be read.
  - **`EditorApplication`:**
    - the frame loop is wrapped in "Editor update", "Scene view" and "Editor UI";
    - the editor records only while the Profiler tab is on screen (tab selected and bottom dock open);
    - draw calls are counted only while recording, not paused and switched on.
  - **`SceneEditorWorkspace`:** times "3D scene" and "2D scene" in every view and pane.
  - `ShapeWidget` was made public so the graph can reuse it.
- **Testing aid:** `GDXUNITY_EDITOR_PROFILER=1` opens the Profiler tab (and the bottom dock, which is closed again before the layout is saved). At the end of a `--smoke-test` run it prints the last full frame's breakdown. Recorded in `AGENTS.md`.

## 3. Commands and results

| Command | Exit | Result |
|---|---|---|
| `./gradlew compileTestJava --continue` | 0 | Every module's main and test code compiles (the new interface method has two implementations, both updated) |
| `./gradlew :engine-core:test :engine-scene:test :engine-visualscript:test :editor-core:test :engine-desktop:test :editor-desktop:test --continue` | 0 | Counts below, 0 failures, 0 errors |
| Mutation: `Entity.update` with the profiler hook removed, `SceneProfilerTest` and `PlayModeControllerTest` | 1 | 3 tests fail (2 + 1); the source was restored and both classes pass again |
| `GDXUNITY_EDITOR_PROFILER=1 GDXUNITY_SCREENSHOT=... ./gradlew :editor-desktop:run --args="--scene samples/racing-3d.json --play --smoke-test=300"` | 0 | 300 frames, 0 Output Log errors, breakdown printed ([text](m9-6/racing-3d-play.txt)) |
| The same with `samples/platformer-2d.json --play` | 0 | 300 frames, 0 errors ([text](m9-6/platformer-2d-play.txt)) |
| The same with `samples/terrain-3d.json` (editing, no Play) | 0 | 300 frames, 0 errors ([text](m9-6/terrain-3d-edit.txt)) |
| The same with `samples/hopper-3d.json --play` (twice, before the label and period changes) | 0 | 300 frames, 0 errors; the first run showed scripts as "EntityScriptBindingComponent", which led to the labels |

| Module | Tests before | Tests after | New |
|---|---|---|---|
| engine-core | 36 | 45 | `FrameProfilerTest` (9) |
| engine-scene | 222 | 226 | `SceneProfilerTest` (4) |
| engine-visualscript | 90 | 91 | `GraphScriptComponentTest` (1) |
| editor-core | 533 (1 skipped) | 539 (1 skipped, not a new test) | `ProfilerReportTest` (5), `PlayModeControllerTest` +1 |
| engine-desktop | 144 | 144 | none |
| editor-desktop | 163 | 163 | none: the panel is GL glue, checked by the runs above |

The "before" counts are the after counts minus the new tests; nothing else changed in those modules. **Not run:** the other modules,
the full `./gradlew test --continue`, and the Babylon suite. No component, graph node or scene format changed, so the Babylon player
is unaffected.

## 4. What the panel shows on the samples

This is the second-to-last frame of each smoke test, with draw calls counted. The times include the cost of counting.

- **racing-3d (Play, 222 entities):** 3.7 ms of work in a 16.8 ms frame (vsync).
  - The 3D scene pass makes **418 draw calls and draws 340,068 vertices**, for 209 mesh primitives: two draw calls per mesh. `SceneRenderer3D` has a shadow pass that draws the meshes again into its shadow map, which would explain this. The panel does not yet split the shadow pass out, so this is not confirmed.
  - Play takes 0.27 ms: the RaceGame script 0.06 ms, the chase camera 0.02 ms, and 2D physics 0.04 ms.
- **platformer-2d (Play, 25 entities):** the 2D scene makes **19 draw calls and 19 shader switches for 19 shapes**: every shape is drawn on its own, with no batching. The two scripts take about 0.04 ms each, and 2D physics 0.12 ms.
- **terrain-3d (editing, 6 entities):** the 3D scene draws 193,608 vertices in 30 draw calls; the editor UI makes 87 draw calls of its own.
- **Every scene:** the editor UI costs 1 to 2 ms a frame and 80 to 90 draw calls. That is as much as, or more than, a small scene.
- **"Other" under Scene update** (0.13 to 0.15 ms) is mostly the cost of timing each component (209 meshes in racing-3d) plus the entity loop. It is not game logic.
- **3D physics:** none of these three samples steps Jolt during Play, so the "3D physics" row is not shown. It is tested headlessly.

## 5. Evidence in the running editor

| Screenshot | What it shows |
|---|---|
| [`m9-6/profiler-racing-3d-play.png`](m9-6/profiler-racing-3d-play.png) | Playing racing-3d with the Profiler tab open: graph, summary, the Scene view and 3D scene rows with 419 and 418 draw calls |
| [`m9-6/profiler-platformer-2d-play.png`](m9-6/profiler-platformer-2d-play.png) | Playing the 2D platformer: 2D scene with 19 draw calls and 19 shader switches |
| [`m9-6/profiler-terrain-3d-edit.png`](m9-6/profiler-terrain-3d-edit.png) | Editing terrain-3d: the graph with the fixed scale and the 60/30 fps lines; slow frames in the warning colour |

The first racing screenshot showed problems, all fixed before the final screenshots:
- the bottom dock was closed (it is by default), so the Profiler recorded without being on screen;
- the graph was flattened by the 335 ms first frame;
- the header buttons and the counter headings ran together.

The saved layout (`workspace_layout.json`) still holds only defaults after the runs, so the dock stays closed for the owner.

**Manually Not Verified:** these need a person at the running editor:
- clicking a bar to pick a frame;
- Pause and Resume;
- Newest;
- Copy frame (the clipboard's contents);
- switching draw-call counting off and on;
- switching to another tab and back, which stops and resumes recording.

The code paths are in place, and `ProfilerReport.toText` (what Copy frame copies) is unit-tested.

## 6. Limitations and next steps

- **No step-through frame debugger:**
  - the panel counts each pass's GL work but cannot list the individual draw calls of one frame, replay them, or show a pass's render target;
  - the shadow pass, sky and opaque models are not split inside "3D scene". `SceneRenderer3D` would need its own sections, which is the natural next step and would settle the two-calls-per-mesh question.
- **Counting changes what it measures.** LibGDX's `GLProfiler` checks for a GL error after every GL call, which makes frames slower. The panel says "times include counting draw calls", and the counting can be switched off.
- **CPU times only.** The sections are CPU time on the render thread. GPU time is not measured per pass (no GL timer queries), so a pass that is cheap to submit but expensive to draw looks cheap. The waiting part of each bar includes the GPU, vsync and the operating system.
- **The editor only.** The game runtime (`engine-desktop`) does not use `FrameProfiler` yet; its frame cost stays the M9.5 numbers. The web players have no profiler.
- **Only the editor's thread.** Sound mixing (its own thread) and asset loading in the background are not shown. Sound block costs are in the M9.5 baseline.
- **Public API change:** `EntityLogicComponent` gained `getProfilerLabel()`, implemented by both its implementers (script binding, graph). A third-party implementer would have to add it. There is no decision record, since the interface is internal to the engine's play rules (TD-088).

## 7. Diff review and Git

- **New files:**
  - `engine-core/.../profile/FrameProfiler.java`
  - `editor-core/.../profile/ProfilerReport.java`
  - `editor-desktop/.../ui/profiler/{GlCounters,FrameHistoryGraph,ProfilerPanel}.java`
  - four test classes: `FrameProfilerTest`, `SceneProfilerTest`, `GraphScriptComponentTest` and `ProfilerReportTest`. There is also one new test in the existing `PlayModeControllerTest`
  - this report and `docs/reports/m9-6/`
- **Changed:**
  - `Scene`, `Entity`, `EntityLogicComponent` and `EntityScriptBindingComponent` (engine-scene)
  - `GraphScriptComponent` (engine-visualscript)
  - `PlayModeController` and its test (editor-core)
  - `EditorApplication`, `SceneEditorWorkspace` and `ShapeWidget` (visibility only) (editor-desktop)
  - `AGENTS.md` (the testing aid), the milestone chart, `FEATURE_TEST_MATRIX.md` and `INDEX.md`
- **Already changed before this milestone** (part of the uncommitted work from earlier milestones; their diffs are larger than this milestone's additions, and their other lines were left as they were):
  - modified: `AGENTS.md`, `Scene.java`, `PlayModeController.java` and its test, `EditorApplication.java`, `SceneEditorWorkspace.java`, the chart, the matrix and the index;
  - untracked: `EntityLogicComponent.java`, from TD-088.
- No commit, push, stash, reset or other Git operation was performed. HEAD is `0469ceb` at start and end.
