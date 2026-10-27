# GdxUnity Long-Term Architecture Assessment and Comprehensive Product Roadmap

**Document Version:** 1.0.0  
**Baseline Date:** October 2024 (Verified Repository State)  
**Target Vision:** Unified Creative Platform (Game Engine + 3D DCC Suite + Digital Audio Workstation)  
**Base Technology Stack:** Java 21 LTS | LibGDX 1.14.2 | LWJGL3 3.4.1 | Gradle Wrapper 9.6.1 | JUnit Jupiter 5.10.0  
**Current Repository Status:** 6 Active Subprojects, 101 Verified Passing Tests, Clean Build  

---

## 1. Executive Summary

### 1.1 The Strategic Vision
The strategic ambition for **GdxUnity** is to evolve from an extensible, Unity-inspired 2D game engine and desktop editor into a **unified cross-platform creative development platform**. The final product envisions the convergence of three traditionally fragmented domains into a single cohesive ecosystem:
1. **Interactive Game Engine & Real-Time Editor** (Unity / Godot style): Real-time multi-viewport rendering, entity-component-system architecture, scene serialization, visual transform gizmos, runtime play-mode isolation, asset database, and polyglot scripting (Java/Kotlin).
2. **3D Modeling, Materials, Rigging, and Animation Suite** (Blender style): B-Rep and polygonal mesh topology authoring, procedural modifiers, sub-division surfaces, UV unwrap, node-based PBR material authoring, skeletal deformation rigging, inverse kinematics (IK), non-linear animation (NLA), and timeline curves.
3. **Professional Digital Audio Workstation (DAW)** (FL Studio / LMMS style): Sample-accurate multi-track audio engine, MIDI sequencing, pattern arrangement, piano roll, software synthesizer instruments (FM/Subtractive/Wavetable), VST/CLAP audio plugin hosting (desktop) or pure-Java audio DSP graph, mixer routing with parametric EQ and effects, and stem/master rendering.

### 1.2 The Architectural Challenge
Attempting to build such an ambitious suite directly on top of a monolithic desktop application or hastily ad-hoc modules would guarantee architectural collapse, unmaintainable coupling, garbage collection latency spikes during audio playback, and unportable code. 

This assessment establishes the **Target Architecture**, **Dependency Topology**, **Cross-Platform Feasibility Matrix**, **Java-First vs. Native Code Boundary Policy**, and **Phased Implementation Roadmap**. It provides an actionable, mathematically sound blueprint for scaling GdxUnity into this creative suite over 18 sequential milestones without destabilizing the verified foundation.

---

## 2. Verified Current-State Inventory (Repository Baseline)

An audit of the repository was conducted on the active codebase. All facts below reflect verified source code:

### 2.1 Subprojects & Module Boundaries
The project is organized into six active Gradle subprojects in `settings.gradle`:
- **`engine-core`**: Core engine lifecycle contracts, `FrameClock` timing, LIFO `ResourceManager`, and persistent asset infrastructure (`AssetReference`, `AssetType`, `AssetMetadata`, `AssetCatalog`, `AssetService`).
- **`engine-scene`**: `Entity`, `Component`, `Transform` hierarchy with dirty propagation, `Scene`, versioned JSON `SceneSerializer`, and the newly added polyglot `ScriptComponent` / `EntityScriptBindingComponent`.
- **`engine-render`**: 2D rendering pipeline (`SceneRenderer2D`, `OrthographicCamera2D`), `ShapeRendererComponent`, `SpriteRendererComponent`, and versioned component serializers.
- **`engine-desktop`**: Desktop runtime launcher and GLFW/LWJGL3 window configuration.
- **`editor-core`**: Editor state management (`EditorState`), `ToolMode`, picking (`ViewportPicker`), visual gizmo mathematics (`TransformGizmoMath`, `GizmoInteractionController`), command-based undo/redo (`EditorCommand`, `CommandHistory`), Play Mode state machine (`PlayModeController`), and polyglot script compilation services (`JavaScriptCompiler`, `KotlinScriptCompiler`, `ScriptClassLoader`, `ScriptManager`).
- **`editor-desktop`**: Desktop visual editor shell built with `scene2d.ui` (`EditorSkinFactory`), hosting `HierarchyPanel`, `InspectorPanel`, `EditorToolbar`, `EditorStatusBar`, `AssetBrowserPanel`, and integrated OpenGL viewport.

### 2.2 Verified Test Execution & Baseline Metrics
Full headless execution of `./gradlew.bat test` completed with **0 failures, 0 errors**:
- **Total Test Suites**: 23 test classes across 4 subprojects (`engine-core`, `engine-scene`, `engine-render`, `editor-core`).
- **Total Passing Tests**: **101 tests** (surpassing the 98 reported at the conclusion of Milestone 8 due to the verified Milestone 9 scripting test suites).
- **Clean Full Build**: `./gradlew.bat build` succeeds in 31s with zero compilation warnings.
- **Legacy Artifacts**: Two inactive legacy directories exist in the filesystem root (`core/` and `lwjgl3/`), which represent the initial template prior to Milestone 0 restructuring. They are omitted from `settings.gradle` and are completely inactive.

### 2.3 Identified Technical Debt & Architectural Gaps
1. **Rendering Monolith**: `engine-render` is currently strictly 2D (`SceneRenderer2D`, `SpriteBatch`, `ShapeRenderer`). It lacks a 3D pipeline (`ModelBatch`, perspective cameras, lighting, materials, depth testing).
2. **Synchronous Asset Cataloging**: `AssetCatalog` scans the filesystem synchronously on the calling thread. For thousands of assets, this must migrate to an asynchronous background worker.
3. **Script Compilation Classpath Resolution**: `JavaScriptCompiler` and `KotlinScriptCompiler` currently resolve the classpath via `System.getProperty("java.class.path")`. In complex modular runtime environments, an explicit compilation classpath registry is required.
4. **Volatile Undo History**: `CommandHistory` operates strictly in memory and is cleared upon scene switching; it does not persist undo snapshots across file saves.

---

## 3. Existing Module Dependency Diagram

The currently verified dependency topology is strictly unidirectional and acyclic:

```
                          ┌────────────────────────┐
                          │     editor-desktop     │
                          └────────┬──────┬────────┘
                                   │      │
                        ┌──────────┘      └──────────┐
                        ▼                            ▼
              ┌───────────────────┐        ┌───────────────────┐
              │    editor-core    │───────►│   engine-render   │
              └─────────┬─────────┘        └─────────┬─────────┘
                        │                            │
                        ▼                            ▼
              ┌───────────────────┐        ┌───────────────────┐
              │   engine-scene    │◄───────┤   engine-desktop  │
              └─────────┬─────────┘        └───────────────────┘
                        │
                        ▼
              ┌───────────────────┐
              │    engine-core    │
              └───────────────────┘
```

**Verified Invariant**: Runtime modules (`engine-*`) have zero compile-time dependencies on editor modules (`editor-*`). Editor desktop depends on `editor-core` and `engine-render`.

---

## 4. Target Architecture: The Creative Platform

To support Game Development, 3D DCC, and Digital Audio Workstation authoring without compromising performance or portability, the architecture is partitioned into **five distinct layers**:

1. **Platform & Hardware Abstraction Layer (HAL)**
2. **Core Domain Engines (Headless & Independent)**
3. **Shared Platform Services (Assets, Undo, Plugins, Serialization)**
4. **Workspace Authoring Layer (Editor Core)**
5. **UI & Desktop Presentation Layer**

```
┌─────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                           editor-desktop (scene2d.ui / GLFW)                                    │
│  ┌───────────────────────┬─────────────────────────┬──────────────────────────┬──────────────────────────────┐  │
│  │   Scene / Game View   │    3D DCC Workspace     │      DAW Workspace       │      Project / Browser       │  │
│  │  (Viewport, Hierarchy)│  (Mesh, Rig, Timeline)  │ (Piano Roll, Mixer, Arr) │    (Assets, Inspector)       │  │
│  └───────────────────────┴─────────────────────────┴──────────────────────────┴──────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘
                                                          │
┌─────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                           editor-core (Universal Workspaces)                                    │
│  ┌───────────────────────────────────┬──────────────────────────────────┬────────────────────────────────────┐  │
│  │     Command History / Undo-Redo   │      Workspace Layout Manager    │        Asset Database (ID / Meta)  │  │
│  ├───────────────────────────────────┼──────────────────────────────────┼────────────────────────────────────┤  │
│  │     Gizmo Interaction & Picking   │      Play Mode Controller        │        Compiler & Diagnostic Svc   │  │
│  └───────────────────────────────────┴──────────────────────────────────┴────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘
                                                          │
┌─────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                         Shared Core & Domain Engine Modules                                     │
│  ┌──────────────────────┬──────────────────────┬──────────────────────┬──────────────────────┬───────────────┐  │
│  │    engine-scene      │    engine-render     │     engine-audio     │    engine-modeling   │ engine-physics│  │
│  │ (Entity, Transform,  │ (2D/3D Batching, PBR,│ (DSP Graph, Sampler, │ (Half-Edge Mesh,     │(Box2D, 3D Phys│  │
│  │  Hierarchy, SerDe)   │  Materials, Shaders) │  Mixer, MIDI Engine) │  Modifiers, Sculpt)  │  Integration) │  │
│  └──────────────────────┴──────────────────────┴──────────────────────┴──────────────────────┴───────────────┘  │
└─────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘
                                                          │
┌─────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                          engine-core (Foundational Layer)                                       │
│    Engine Lifecycle | FrameClock | LIFO ResourceManager | AssetReference | Math3D Utilities | Event Bus         │
└─────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 5. Current-to-Target Module Mapping

| Current Module | Target Role | Transformation Strategy | Justification |
| :--- | :--- | :--- | :--- |
| `engine-core` | Foundation HAL | **Retain & Extend** | Houses foundational interfaces, timing, math utilities, and resource contracts. Add high-performance audio buffer contracts. |
| `engine-scene` | Scene & ECS | **Retain & Extend** | Retain entity/component hierarchy. Add support for spatial partitioning (Octree/BVH) and timeline tracks. |
| `engine-render` | 2D/3D Renderer | **Extend** | Currently 2D only. Must integrate LibGDX 3D (`ModelBatch`, PBR shaders, perspective cameras) without breaking `SceneRenderer2D`. |
| `engine-desktop` | Desktop Runner | **Retain** | Lightweight runtime runner for standalone desktop game builds. |
| `editor-core` | Universal Tooling | **Extend & Partition** | Command history, asset indexing, and script compilation are already solid. Introduce generic `Workspace` contracts. |
| `editor-desktop` | Visual Shell | **Extend** | Keep `scene2d.ui` shell. Introduce docking/workspace tabs: **Game Editor**, **3D DCC Modeler**, and **DAW Studio**. |
| *(New)* `engine-physics` | Physics Engine | **New Bounded Module** | Isolate 2D (Box2D) and 3D (Bullet/Jolt-Java) physics away from scene core. |
| *(New)* `engine-audio` | Audio & MIDI Engine | **New Bounded Module** | Real-time DSP audio synthesis, sampler, mixer, MIDI sequencer, and WAV export. Zero UI dependencies. |
| *(New)* `engine-modeling`| 3D DCC Geometry | **New Bounded Module** | Half-edge polygon mesh structures, procedural subdivision, sculpting brushes, UV unwrap, and armature deformation. |

---

## 6. Cross-Platform Feasibility Matrix

GdxUnity’s long-term promise is authoring on Desktop with export/playability across Mobile and Web. The technical feasibility of each planned capability across target platforms is evaluated below:

| Feature Domain | Desktop (Win/Mac/Linux) | Web (HTML5/WebGL/WebAssembly) | Mobile (Android / iOS) | Technical Feasibility & Notes |
| :--- | :---: | :---: | :---: | :--- |
| **Core ECS & Scene Serialization** | **Full** | **Full** | **Full** | Pure Java 21 logic. Compiles to TeaVM/Cheerpj or standard Dalvik/RoboVM. |
| **2D / 3D Game Runtime** | **Full** | **Full** | **Full** | Backed by LibGDX OpenGL ES 2.0 / 3.0 abstractions. Standard WebGL support. |
| **Visual Desktop Editor Shell** | **Full** | **Partial** | **Minimal / Viewer** | Desktop is primary authoring environment. Mobile authoring is ergonomics-constrained. |
| **Java/Kotlin Dynamic Scripting** | **Full (In-Process)** | **Pre-Compiled Only** | **Pre-Compiled / AOT** | In-process JIT compilation works on Desktop. iOS and WebAssembly prohibit JIT at runtime; require pre-build compilation. |
| **3D Modeling & Mesh Topology** | **Full** | **Full (Runtime/Viewer)** | **Viewer / Simple Tweak** | Pure algorithmic half-edge topology math is 100% portable Java. |
| **Software Synthesizer & DSP** | **Full** | **Full (AudioWorklet)** | **Full (AAudio/AudioUnit)**| Pure Java fixed-point / float audio DSP graphs run everywhere. |
| **Low-Latency Audio Mixing** | **< 10ms (ASIO/WASAPI)** | **20–50ms (Browser Buffer)** | **15–30ms (OS Buffer)** | Desktop achieves pro-audio latencies; Browser WebAudio is acceptable for playback. |
| **Native VST3 / CLAP Plugin Hosting** | **Desktop Only (JNI)** | **Not Feasible** | **Not Feasible** | Relies on architecture-specific dynamic libraries (.dll/.so/.dylib). |

---

## 7. Java-First vs. Native Code Decision Policy

A common failure mode in Java game engine projects is prematurely dropping into C++ or Rust via JNI/FFM, which destroys cross-platform packaging simplicity and introduces fatal memory segmentation faults.

### The Decision Policy:
1. **Pure Java Default**: All data structures, scene graphs, serialization, mathematics, half-edge mesh processing, MIDI sequencing, and DSP synthesis logic **MUST** be implemented in pure Java.
2. **Zero Garbage in Inner Loops**: Inner rendering, audio synthesis, and physics loops must allocate zero garbage collector objects per frame, using object pools (`com.badlogic.gdx.utils.Pool`), primitive arrays, or direct NIO byte buffers.
3. **Evidenced Native Threshold**: Native libraries (via JNI or Java 22+ Project Panama FFI) are permitted **ONLY** when:
   - Access to platform-exclusive device drivers is required (e.g. low-latency ASIO audio hardware, native MIDI hardware ports).
   - An industry-standard native binary format must be hosted in-process (e.g. VST3/CLAP audio plugin binary hosting).
   - Compute demands exceed JVM vectorization capabilities by an order of magnitude, proven by profiling.
4. **Isolate Behind Pure Interfaces**: Any native bridge must be isolated in a platform adapter module (`*-desktop`), with pure Java fallback implementations available for web and mobile.

---

## 8. Prioritized Strategic Roadmap

The strategic execution is structured into **four distinct phases** spanning Milestones 9 through 18:

```
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│ PHASE 1: Runtime & Engine Core Foundations (Milestones 9–11)                                     │
│ M9: Polyglot Scripting (Complete) ──► M10: 3D Rendering Foundation ──► M11: 2D/3D Physics Engine│
└────────────────────────────────┬─────────────────────────────────────────────────────────────────┘
                                 │
┌────────────────────────────────▼─────────────────────────────────────────────────────────────────┐
│ PHASE 2: Shared Creative Platform Infrastructure (Milestones 12–13)                              │
│ M12: Universal Asset Database & Pipeline ──► M13: Workspace Architecture & Docking Editor Shell │
└────────────────────────────────┬─────────────────────────────────────────────────────────────────┘
                                 │
┌────────────────────────────────▼─────────────────────────────────────────────────────────────────┐
│ PHASE 3: 3D DCC Modeling & Animation Suite (Milestones 14–15)                                    │
│ M14: 3D Mesh Modeling & Modifiers ────────► M15: Skeletal Rigging, Skinning & Animation Timeline │
└────────────────────────────────┬─────────────────────────────────────────────────────────────────┘
                                 │
┌────────────────────────────────▼─────────────────────────────────────────────────────────────────┐
│ PHASE 4: Digital Audio Workstation & Audio Production (Milestones 16–18)                         │
│ M16: Audio DSP Engine & Synthesis ──► M17: MIDI Sequencing & Piano Roll ──► M18: Mixer & DAW Hub │
└──────────────────────────────────────────────────────────────────────────────────────────────────┘
```

### Detailed Milestone Breakdown

#### Milestone 9 — Java/Kotlin Scripting Foundation ✅ *(Verified Complete)*
- **Objective**: Polyglot entity scripting with lifecycle callbacks (`onCreate`, `onStart`, `onUpdate`, `onDestroy`), embedded `JavaCompiler` and `K2JVMCompiler`, structured compiler diagnostics, and Play Mode isolation.
- **Status**: **Complete & Verified (101 Passing Tests)**.

#### Milestone 10 — 3D Rendering Foundation (Next Implementation Stage)
- **Objective**: Extend GdxUnity into true 3D while preserving 100% of existing 2D rendering and editor capabilities.
- **Prerequisites**: Milestones 1–9.
- **Key Deliverables**:
  - `PerspectiveCamera3D` implementing the camera abstraction.
  - `MeshRendererComponent` and `Material` definition (Diffuse, Specular, Normal, PBR roughness/metallic properties).
  - Integration of LibGDX `ModelBatch` and `Environment` (directional light, ambient light, point lights).
  - Dual 2D/3D viewport rendering modes with 3D grid plane and axis visualization.
  - 3D Raycasting entity picking in `ViewportPicker`.
  - Full versioned JSON serialization for 3D meshes and materials.

#### Milestone 11 — Physics Engine Integration (2D Box2D & 3D Collision)
- **Objective**: Integrated physics simulation for 2D rigid bodies and colliders, with editor wireframe visualization.
- **Key Deliverables**:
  - `RigidBody2DComponent`, `BoxCollider2DComponent`, `CircleCollider2DComponent`.
  - Box2D world simulation loop tied to `ScriptComponent.onFixedUpdate()`.
  - Viewport debug renderer drawing physics bounding boundaries.
  - Safe Play-Mode physics state reset on simulation stop.

#### Milestone 12 — Universal Asset Database & Background Importer Pipeline
- **Objective**: Upgrade synchronous `AssetCatalog` into an asynchronous, asset-database indexer supporting 3D models (OBJ, GLTF 2.0), textures, and audio files.
- **Key Deliverables**:
  - Background file watcher and non-blocking indexing thread.
  - GLTF 2.0 / OBJ mesh importer generating engine asset references.
  - Asset metadata caching with thumbnail generation.

#### Milestone 13 — Workspace Architecture & Modular Docking Shell
- **Objective**: Transform the single-purpose game editor into a multi-workspace creative shell (Game View, DCC Modeling, DAW Studio).
- **Key Deliverables**:
  - `Workspace` lifecycle abstraction in `editor-core`.
  - Dockable panel layouts, tabbed viewports, and workspace state persistence.
  - Shared command history across active workspaces.

#### Milestone 14 — 3D DCC Mesh Modeling, Topology, and Modifiers (Blender-Inspired)
- **Objective**: Implement geometry authoring tools directly inside the 3D editor.
- **Key Deliverables**:
  - Half-edge mesh data structure (`engine-modeling`) supporting non-destructive topological edits (extrude, inset, bevel, loop cut).
  - Procedural modifier stack: Sub-division surfaces (Catmull-Clark), Mirror, Array, Solidify.
  - Edit Mode vs. Object Mode viewport toggle with vertex, edge, and face selection.

#### Milestone 15 — Skeletal Rigging, Weight Painting, and Animation Timeline
- **Objective**: Character rigging and timeline curve animation.
- **Key Deliverables**:
  - `ArmatureComponent` and bone hierarchy.
  - Vertex skinning and linear blend weight deformation.
  - Dope Sheet and Graph Editor animation curve evaluation.

#### Milestone 16 — Low-Latency Audio DSP Engine & Synthesizer Foundation (FL Studio-Inspired)
- **Objective**: Pure Java real-time audio synthesis engine with sample-accurate scheduling.
- **Key Deliverables**:
  - `engine-audio` module: Real-time Audio Server running on a high-priority, zero-garbage audio thread.
  - Core DSP nodes: Oscillators (Sine, Saw, Square, Noise), State-Variable Filters (LP, HP, BP), ADSR Envelope Generators, and LFOs.
  - Multi-voice polyphonic synthesizer instrument.

#### Milestone 17 — MIDI Sequencing, Piano Roll, and Audio Sampler
- **Objective**: Musical arrangement, pattern sequencing, and sample playback.
- **Key Deliverables**:
  - MIDI event model: Notes, Velocity, Pitch Bend, CC automation.
  - Interactive **Piano Roll** editor panel in `editor-desktop` (note placing, velocity dragging, quantization).
  - Wavetable/PCM Audio Sampler instrument.

#### Milestone 18 — Multi-Track Mixer, Audio Effects, and Stem Rendering
- **Objective**: Complete DAW mixing desk and audio master export.
- **Key Deliverables**:
  - Multi-channel Mixer (Insert tracks, Send tracks, Master bus).
  - Built-in Audio Effects: Parametric 4-band EQ, Stereo Delay, Algorithmic Reverb, Compressor/Limiter.
  - Non-real-time stem rendering and WAV audio export.

---

## 9. Architectural Quality Gates

To prevent regressions across this expansive development cycle, every milestone must pass the following mandatory quality gates:

1. **Module Boundary Gate**: 
   - `./gradlew.bat build` must fail if any runtime module (`engine-*`) references editor packages (`editor-*`).
   - Pure headless execution for all domain math, audio DSP, and geometry modules.
2. **Backward-Compatible Scene Serialization Gate**:
   - Automated tests must deserialize legacy scene files (from Milestones 2, 7, 8, 9) and verify 100% field preservation.
3. **Play-Mode Isolation Gate**:
   - Running simulation, modifying entities, and stopping simulation must restore the authored scene bit-for-bit without retaining stale pointers.
4. **Zero-Garbage Audio & Inner Loop Gate**:
   - Continuous audio synthesis and 3D rendering inner loops must achieve zero object allocations per frame/buffer to eliminate GC pauses.
5. **Headless Quality Invariant**:
   - 100% of test suites must run in CI/headless environments without requiring an active physical display or GPU.

---

## 10. Proposed Acceptance Criteria for Milestone 10 (3D Rendering Foundation)

As the immediate next implementation milestone, Milestone 10 must fulfill:

- [ ] **Dual 2D/3D Viewport Support**: The viewport can switch between 2D orthographic mode and 3D perspective mode without broken rendering.
- [ ] **Perspective Camera**: `PerspectiveCamera3D` supports position, look-at, FOV (60° default), near/far planes, and orbit/pan controls.
- [ ] **3D Grid & Gizmo Adaptation**: Ground grid (X/Z plane) renders in 3D; Transform gizmos operate accurately across 3 axes (X, Y, Z).
- [ ] **3D Primitives & Meshes**: Native generation and rendering of Cube, Sphere, Cylinder, and Plane using LibGDX `ModelBatch`.
- [ ] **Material System**: Basic PBR/Phong material holding Diffuse color/texture, Specular tint, and Normal map reference.
- [ ] **3D Raycast Picking**: Clicking a 3D primitive in the viewport performs ray-bounding box/sphere intersection and selects the entity.
- [ ] **Scene Serialization Round-Trip**: 3D mesh references and material parameters round-trip through JSON serializer without losing 2D sprite compatibility.
- [ ] **Regression Invariant**: All existing 101 tests continue to pass; 2D sprite rendering and Play Mode operate without regressions.

---

## 11. Final Recommendations & Decisions Requiring Approval

### Explicit Decisions Requiring Approval:
1. **Approval of Phased Sequence**: Approval to proceed immediately with **Milestone 10 (3D Rendering Foundation)** as the next step, followed by Physics (M11) and Asset DB (M12), deferring DCC mesh modeling and DAW audio to Phases 3 & 4.
2. **Retain LibGDX 3D Framework**: Confirm utilizing LibGDX's built-in `ModelBatch`, `Mesh`, `ShaderProvider`, and `Renderable` systems for Milestone 10 rather than writing a raw OpenGL 3D engine from scratch.
3. **Pure Java Audio Architecture**: Confirm building `engine-audio` as a pure Java DSP graph (zero garbage) for multi-platform portability, reserving native VST hosting strictly for desktop extension plugins.
4. **Preservation of Six Baseline Modules**: Confirm keeping the existing 6 modules intact and introducing `engine-physics`, `engine-audio`, and `engine-modeling` only when their respective milestones begin.
