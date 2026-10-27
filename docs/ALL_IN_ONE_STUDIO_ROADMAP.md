# All-in-One Game Dev Creative Studio Roadmap
## Emulating the ArtCraft Paradigm for Game Developers (Blender + FL Studio/LMMS + Inkscape/Illustrator + Game Engine)

**Target Project:** GdxUnity  
**Document:** `docs/ALL_IN_ONE_STUDIO_ROADMAP.md`  
**Architectural Baseline:** Phase 8 Complete (Java 21, LibGDX 1.14.2, LWJGL3 3.4.1, 98 Passing Tests)  
**Inspiration:** [ArtCraft](https://app.getartcraft.com/) (Suite of integrated, fast, open-source creative tools) applied directly to game development workflows.

---

## 1. Vision & Paradigm Shift

### 1.1 The Problem in Modern Game Development
Today, an indie game developer or small studio must jump across fragmented, heavyweight external software suites to build a single game:
- **Game Engine** (Unity, Godot, LibGDX): Scene composition, component logic, physics, play testing.
- **3D Modeling & Animation** (Blender): 3D modeling, UV unwrapping, skeletal rigging, keyframe animation, material authoring.
- **Digital Audio Workstation** (FL Studio / Fruityloops, LMMS): Step sequencing, piano rolls, software synthesizers, sound effect generation, mixing.
- **Vector Graphics & 2D Art** (Inkscape, Adobe Illustrator): Bézier paths, UI iconography, vector illustrations, 9-slice sprites, SVG authoring.

This fragmentation leads to:
1. **Context Switching & Friction**: Constant file exports (`.obj`, `.gltf`, `.wav`, `.png`, `.svg`), re-imports, re-indexing, and broken reference paths.
2. **Heavy Toolchains**: Running three separate massive desktop applications concurrently eats system memory, GPU contexts, and battery.
3. **Loss of Live Context**: You cannot tweak a synth sound while hearing it in the actual game room, or adjust a 3D vertex / vector UI path while seeing it interact with live game camera and lighting.

### 1.2 The ArtCraft Inspiration
ArtCraft (`getartcraft.com` / `app.getartcraft.com`) demonstrated that an integrated suite of purpose-built, high-performance creative tools (PhotoCraft, VectorCraft, FilmCraft, etc.) can replace bloated legacy software by focusing on speed, clean architecture, and unified user workflow.

**The GdxUnity Mission:**  
Transform `gdx-unity` from a standalone 2D Unity-like editor into the **All-in-One Game Dev Studio**—where 3D modeling (Blender-lite), vector art (Inkscape-lite), audio sequencing and synthesis (FL Studio/LMMS-lite), and game engine simulation live inside a single, blazing-fast Java 21 / LibGDX desktop workspace.

---

## 2. Current Project Baseline Analysis (Gap Analysis from Phase 8)

Before introducing new creative studios, we must evaluate what `gdx-unity` currently provides versus what is missing:

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                                GdxUnity Phase 8 Baseline                               │
├───────────────────────────────┬───────────────────────────────┬────────────────────────┤
│ What Exists & Is Solid        │ What Is Partial / Constrained │ What Is Missing        │
├───────────────────────────────┼───────────────────────────────┼────────────────────────┤
│ • 6-Module architecture       │ • 2D Only (No 3D viewport,    │ • 3D Mesh & Modeling   │
│ • Stable UUID Entity system   │   no PerspectiveCamera,       │ • Bézier Vector Engine │
│ • Hierarchical Transform      │   no 3D mesh rendering)       │ • Software Synthesizer │
│ • Versioned JSON serializer   │ • Static scene2d UI layout    │ • Step Sequencer/DAW   │
│ • 2D Camera (FitViewport)     │   (tables, no dockable tabs   │ • Keyframe Animation   │
│ • Viewport 2D bounds picker   │   or multi-window docking)    │ • Audio Bus / Mixer    │
│ • Move/Rotate/Scale 2D Gizmos │ • Single entity selection     │ • SVG Import/Export    │
│ • AssetReference & Catalog    │   (no multi-selection/batch)  │ • Procedural SFX tools │
│ • Centralized GPU AssetService│ • In-memory texture cache     │ • Material Shaders     │
│ • Command Undo/Redo (98 tests)│   (no asset baking/pipeline)  │ • Multi-studio layout  │
│ • Play Mode runtime isolation │                               │                        │
└───────────────────────────────┴───────────────────────────────┴────────────────────────┘
```

---

## 3. Unified Studio Architecture & Module Topology

To support four creative domains without creating a tangled monolith, the modular architecture must expand cleanly following strict dependency rules:

```
                            ┌─────────────────────────────────┐
                            │         editor-desktop          │
                            │  (Docking Workspace Shell, UI)  │
                            └───────┬─────────────────┬───────┘
                                    │                 │
                ┌───────────────────┴─────────┐       │
                ▼                             ▼       │
   ┌─────────────────────────┐   ┌────────────────────────┐
   │       editor-core       │   │     editor-studios     │
   │ (State, Undo, PlayMode) │   │ (3D, Vector, DAW UIs)  │
   └────────────┬────────────┘   └────────────┬───────────┘
                │                             │
                └──────────────┬──────────────┘
                               ▼
  ┌───────────────────────────────────────────────────────────────┐
  │                         engine-render                         │
  │     (2D Viewport, 3D ModelBatch, Shaders, Vector Rasterizer)  │
  └──────────────┬───────────────────────────────┬────────────────┘
                 │                               │
                 ▼                               ▼
  ┌───────────────────────────────┐ ┌─────────────────────────────┐
  │          engine-scene         │ │        engine-audio         │
  │ (Entity, Transform, Hierarchy)│ │ (Synth, Sequencer, Mixer)   │
  └──────────────┬────────────────┘ └─────────────┬───────────────┘
                 │                                │
                 ▼                                ▼
  ┌───────────────────────────────┐ ┌─────────────────────────────┐
  │         engine-vector         │ │        engine-core          │
  │ (Bézier Curves, Tessellation) │ │ (Lifecycle, Assets, Memory) │
  └───────────────────────────────┘ └─────────────────────────────┘
```

### Proposed New & Upgraded Modules

1. **`engine-core`** *(Existing - Upgrade)*:
   - Asset pipeline extensions: Support new asset types (`VECTOR_SVG`, `MODEL_3D`, `AUDIO_SYNTH_PRESET`, `AUDIO_SAMPLE`, `AUDIO_SEQUENCE`).
   - Project-wide dirty asset auto-compiler.

2. **`engine-vector`** *(New Module)*:
   - Pure mathematical 2D vector primitives: cubic and quadratic Bézier segments, subpaths, compound paths, fills, and strokes.
   - Polygon triangulation (ear-clipping / Delaunay) for direct GPU rendering.
   - SVG import/export parser.

3. **`engine-audio`** *(New Module)*:
   - Low-latency LibGDX `AudioDevice` wrapper with float audio buffer rendering.
   - Multi-oscillator software synthesizer (Sine, Square/Pulse, Sawtooth, Triangle, Noise) with ADSR envelope, filter, and LFO.
   - Chiptune / SFX procedural generator (sfxr/bfxr algorithm integration).
   - Multi-channel audio mixer with volume, pan, and effects (delay, reverb, filter).
   - Pattern and sequence data models (16-step grid and continuous MIDI-like ticks).

4. **`engine-render`** *(Existing - Upgrade)*:
   - Add 3D rendering pipeline: `PerspectiveCamera3D`, `ModelBatch`, `Environment` with ambient/directional/point lights, and PBR/Lit shaders.
   - Add 2D vector mesh renderers for antialiased curves.

5. **`editor-studios`** *(New Module)*:
   - **Studio 3D** (Blender-like mesh editor, UV mapper, 3D viewport, 3D gizmos).
   - **Studio Vector** (Inkscape-like canvas, pen tool, node manipulation, path booleans).
   - **Studio Audio** (FL Studio/LMMS-like step sequencer, piano roll, synth rack, mixer).

6. **`editor-desktop`** *(Existing - Upgrade)*:
   - Upgrade to a dockable multi-tab workspace manager allowing developers to seamlessly switch or split between:
     - `[Scene View]`
     - `[3D Model Studio]`
     - `[Vector Art Studio]`
     - `[Audio & Music Studio]`
     - `[Game Play Preview]`

---

## 4. Creative Domain Feature Breakdowns

### 4.1 The Blender Domain (3D Modeling, UVs, Rigging & Shaders)
*Goal: Enable low-poly 3D modeling, level blocking, UV unwrapping, and skeletal rigging directly inside the engine without needing external Blender exports.*

#### Core Features Required
1. **Half-Edge / PolyMesh Data Structure**:
   - Explicit representation of Vertices, Edges, and Faces (quads and n-gons).
   - Real-time conversion to LibGDX `Mesh` / `Model` for hardware rendering.
2. **Mesh Edit Mode Operations**:
   - **Vertex / Edge / Face Selection**: Interactive raycasting picking in 3D viewport.
   - **Extrude (`E`)**: Extrude faces or edges along normal or free axes.
   - **Loop Cut (`Ctrl+R`)**: Subdivide faces with edge loops.
   - **Bevel (`Ctrl+B`)**: Chamfer edges with configurable segments.
   - **Subdivide**: Surface refinement for organic or smoothed low-poly meshes.
   - **Insetting (`I`)**: Offset faces inward.
3. **3D Viewport Navigation & Gizmos**:
   - 3D Orbit, Pan, and First-Person Fly-through camera controls.
   - 3D Move (3-axis arrows + 3-plane squares), Rotate (3 gimbal rings), and Scale gizmos.
   - Grid floor, wireframe overlay, and normal vector display.
4. **UV Unwrapping & Material Assign**:
   - Basic automatic projection (Box, Planar, Cylindrical).
   - 2D UV editor panel showing mesh UV islands over a texture atlas.
   - Material slot manager (assign diffuse texture, specular, normal maps, roughness).
5. **Direct Integration with Game Engine**:
   - One-click "Apply to Scene": Edited mesh immediately instantiates as a `MeshRendererComponent` on an entity in the scene view.
   - Reversible mesh edits wired into the Milestone 8 `EditorCommand` architecture.

---

### 4.2 The Inkscape / Illustrator Domain (Vector Art, 2D UI & SVG)
*Goal: Author vector iconography, 9-slice UI frames, character sprites, and resolution-independent game assets directly inside the project.*

#### Core Features Required
1. **Vector Geometry Engine**:
   - Parametric Bézier paths: Anchor points, incoming/outgoing control handles (Smooth, Cusp/Sharp, Symmetric).
   - Primitive vector shapes: Rectangle, Ellipse/Circle, Regular Polygon, Star, Line.
   - Compound paths: Subpaths with even-odd and non-zero winding rules.
2. **Vector Tools Palette**:
   - **Select / Transform Tool**: Move, scale, rotate vector objects with visual bounding box handles.
   - **Pen Tool (`P`)**: Click to place anchors, drag to pull Bézier curvature.
   - **Node / Edit Path Tool (`N`)**: Select individual anchors or handles, convert between sharp and smooth.
   - **Boolean Path Operations**: Union, Difference, Intersection, Exclusion.
3. **Styling & Fill/Stroke**:
   - Solid color fills, linear and radial gradients.
   - Stroke width, line join (Miter, Round, Bevel), line cap (Butt, Round, Square), and dashed strokes.
4. **Game-Centric Vector Features**:
   - **9-Slice Guide Placement**: Visually drag 9-slice slice borders directly on the vector art canvas.
   - **Auto-Rasterization to Project Assets**: One-click bake to `.png` at multiple resolutions (`1x`, `2x`, `4x`) directly into the project `assets/` folder, auto-registered into `AssetCatalog`.
   - **Vector-to-Mesh Triangulation**: Render vector graphics dynamically as textured/colored triangles for infinite zoom without pixelation.
   - **SVG Import & Export**: Round-trip with external tools like Inkscape/Illustrator.

---

### 4.3 The Fruityloops / LMMS Domain (DAW, Synthesizers, SFX & Sequencing)
*Goal: Create original game music, retro chiptune tunes, dynamic environmental audio, and instant game sound effects (jump, laser, explosion) without leaving the engine.*

#### Core Features Required
1. **Built-in Software Synthesizer (SynthCraft)**:
   - **Oscillators**: Dual-oscillator engine with Sine, Saw, Pulse/Square (with pulse-width modulation), Triangle, and White Noise.
   - **Envelope (ADSR)**: Attack, Decay, Sustain, Release curve generation.
   - **Filters**: 12dB/24dB Low-Pass, High-Pass, Band-Pass with cutoff and resonance.
   - **LFO**: Low-Frequency Oscillator routable to pitch (vibrato), cutoff (wah), or volume (tremolo).
2. **Procedural Game SFX Generator (sfxr/bfxr Engine)**:
   - Instant 1-click sound effect presets:
     - 🚀 Laser / Shoot
     - 💥 Explosion
     - 🪙 Coin / Pickup
     - 🦘 Jump
     - ⚡ Powerup
     - 💔 Hurt / Death
   - Randomize & mutate controls for rapid sound design exploration.
3. **16-Step Pattern Sequencer (Drum Machine)**:
   - Grid of 16/32 steps across multiple instrument tracks (Kick, Snare, Hi-Hat, Bass, Lead).
   - Velocity sensitivity and swing/groove adjustments.
4. **Piano Roll Note Editor**:
   - Vertical piano keyboard (note pitches C1 through B7) vs. horizontal timeline grid.
   - Click and drag to draw note lengths, transpose pitches, and adjust per-note velocity.
   - Snap-to-grid (1/4, 1/8, 1/16, 1/32, triplets).
5. **Mixer & Audio Routing**:
   - 8-16 channel mixer with master bus.
   - Per-channel volume fader, stereo panning, mute/solo, and effect inserts (Delay, Reverb).
   - Real-time preview playback synced with the game editor.
6. **Direct Game Engine Integration**:
   - One-click export to compressed `.ogg` or `.wav` directly into project `assets/`.
   - Procedural runtime playback: Run the lightweight software synth directly at runtime for zero-disk-footprint procedural audio!
   - `AudioSourceComponent` and `AudioListenerComponent` in `engine-scene`.

---

## 5. Phase-by-Phase Implementation Roadmap

The project has achieved **Phase 0 through Phase 8**. The following phased roadmap specifies what needs to be implemented to achieve the All-in-One Studio vision.

```
       [Completed]                       [New Studio Roadmap]
Phase 0 ──► Phase 8  ──►  Phase 9  ──►  Phase 10 ──►  Phase 11 ──►  Phase 12 ──►  Phase 13 ──►  Phase 14 ──►  Phase 15
 (Foundations, Gizmos,   (Dockable     (Vector Art     (DAW & Synth    (3D Render    (3D Mesh      (3D Bones     (Unified Live
  Undo/Redo, Assets)    Workspace)       Studio)         Studio)       Pipeline)     Modeling)     & Rigging)     Pipeline)
```

---

### Phase 9 — Unified Docking Workspace & Multi-Studio Architecture

**Objective**: Upgrade `editor-desktop` from rigid layout tables into an extensible, tabbed, dockable studio shell capable of hosting specialized creative environments.

**Key Deliverables**:
- [ ] Create `editor-studios` module in `settings.gradle`.
- [ ] Implement `StudioWorkspace` contract with lifecycle hooks (`onEnter`, `onUpdate`, `onRender`, `onExit`).
- [ ] Implement `StudioNavigationToolbar` supporting seamless switching between:
  - 🎮 **Scene Editor** (current Milestone 8 workflow)
  - 🎨 **Vector Art Studio** (Phase 10)
  - 🎵 **Audio & Music Studio** (Phase 11)
  - 🧊 **3D Model Studio** (Phases 12–14)
- [ ] Implement dockable tab / split-pane system in `scene2d.ui` without introducing heavy native dependencies.
- [ ] Multi-entity selection model in `EditorState` (`Set<UUID> selectedEntityIds`) supporting box-selection in viewport and hierarchy.
- [ ] Unified hotkey dispatcher allowing global shortcuts (`Ctrl+Z`, `Ctrl+Y`, `Ctrl+S`, `Space` for play) regardless of active studio.

---

### Phase 10 — Vector Art & UI Studio (Inkscape/Illustrator Features)

**Objective**: Implement 2D vector path editing, Bézier curves, SVG input/output, and automatic sprite baking.

**Key Deliverables**:
- [ ] Create `engine-vector` module.
- [ ] Implement `BezierCurve` mathematics (quadratic, cubic, point evaluation, tangents, bounding boxes).
- [ ] Implement `VectorPath` data structure (nodes, handles, closed/open loops, fill, stroke).
- [ ] Implement 2D polygon triangulation (Ear Clipping / Poly2Tri) for GPU rendering via LibGDX `Mesh` / `ShapeRenderer`.
- [ ] Implement Vector Art Canvas in `editor-studios`:
  - Zoomable, pannable infinite vector canvas with pixel-grid snap.
  - Pen Tool (`P`): Click to place nodes, drag to adjust smooth Bézier control handles.
  - Node Select Tool (`N`): Interactive dragging of nodes and handles with multi-node selection.
  - Shape Tools: Rectangle, Rounded Rect, Circle, Star, Polygon.
  - Color Picker & Gradient Editor (Linear, Radial).
- [ ] 9-Slice Visual Guide Tool: Drag 4 slice lines over the vector shape.
- [ ] Vector Export Pipeline:
  - Export to `.png` at chosen resolutions (`1x`, `2x`, `4x`) directly into project `assets/`.
  - Export to `.svg` and import existing external `.svg` files into `VectorPath` objects.
- [ ] Reversible vector editing commands wired into `CommandHistory`.
- [ ] Comprehensive unit tests for Bézier math, triangulation, and SVG parsing.

---

### Phase 11 — Audio & Music Studio (FL Studio / LMMS Features)

**Objective**: Implement software sound synthesis, procedural SFX generation, 16-step sequencing, piano roll, and mixer channels.

**Key Deliverables**:
- [ ] Create `engine-audio` module.
- [ ] Implement real-time audio generation via LibGDX `AudioDevice`:
  - `AudioSynth`: Dual oscillator (Sine, Square, Saw, Triangle, White Noise).
  - ADSR Envelope generator with linear and exponential release.
  - Resonant Filter (Low-pass, High-pass) and LFO modulator.
- [ ] Implement Chiptune / Game SFX Generator (`SfxrGenerator`):
  - Presets for Jump, Laser, Explosion, Coin, Powerup, Hurt.
  - Parameter mutation, randomizer, and pitch bend.
- [ ] Implement Step Sequencer (`StepSequencerPanel`):
  - 16/32-step rhythmic grid across multiple channels.
  - Sample playback support (`.wav`, `.ogg`) and synth trigger support.
- [ ] Implement Piano Roll (`PianoRollPanel`):
  - Note matrix with timeline grid and chromatic pitch keys.
  - Note placement, resizing (duration), velocity control, and quantization.
- [ ] Implement Audio Mixer (`AudioMixerPanel`):
  - Master channel + 8 effect tracks with volume fader, stereo panning, and mute/solo.
  - Digital Delay and Reverb filter effects.
- [ ] Integration with Game Runtime:
  - Add `AudioSourceComponent` and `AudioListenerComponent` to `engine-scene`.
  - One-click export of sequences/SFX to `.wav` or `.ogg` in `assets/`.
  - Support procedural in-engine playback without disk export.
- [ ] Automated headless tests for DSP buffer math, synth voices, and pattern serialization.

---

### Phase 12 — 3D Rendering Foundation & Camera Systems

**Objective**: Extend the rendering pipeline from 2D orthographic to a unified 2D/3D hybrid engine.

**Key Deliverables**:
- [ ] Implement `PerspectiveCamera3D` in `engine-render` with configurable FOV, near/far planes, and viewport handling.
- [ ] Implement `ModelBatch` wrapper and rendering pass inside `engine-render`.
- [ ] Implement 3D Light System:
  - `DirectionalLightComponent`, `PointLightComponent`, and ambient lighting parameters.
- [ ] Implement 3D Transform enhancements:
  - Complete 3D rotation (quaternions + Euler angles), 3D scale, and 3D translation matrix math in `Transform`.
- [ ] Implement 3D Viewport Navigation:
  - Orbit Camera (Alt + Left Click Drag), Pan Camera (Alt + Middle Click Drag), Fly Camera (Right Click + WASD).
- [ ] Implement 3D Visual Transform Gizmos:
  - 3-axis Move arrows (X=Red, Y=Green, Z=Blue) + planar drag squares.
  - 3-axis Rotate gimbal rings.
  - 3-axis Scale cubes + uniform center box.
- [ ] Automated headless tests for 3D camera projections, raycasting, and 3D gizmo math.

---

### Phase 13 — 3D Modeling & Mesh Studio (Blender-like Modeling & UV)

**Objective**: Provide an interactive in-engine low-poly 3D modeling and UV mapping studio.

**Key Deliverables**:
- [ ] Implement Half-Edge / B-Rep mesh data model in `engine-render`:
  - `MeshModel`, `MeshVertex`, `MeshEdge`, `MeshFace`.
  - Conversion between internal editable mesh and hardware LibGDX `Mesh` / `Model`.
- [ ] Implement 3D Mesh Studio in `editor-studios`:
  - Mode toggle: Object Mode vs. Mesh Edit Mode (`Tab`).
  - Selection modes: Vertex (`1`), Edge (`2`), Face (`3`).
  - Interactive 3D raycasting selection for vertices, edges, and faces.
- [ ] Core Mesh Modeling Operations:
  - **Extrude (`E`)**: Pull selected face/edge along its normal.
  - **Inset Face (`I`)**: Create concentric offset face.
  - **Loop Cut (`Ctrl+R`)**: Split face rings with edge loops.
  - **Bevel (`Ctrl+B`)**: Chamfer edges or vertices.
  - **Subdivide**: Surface refinement.
  - **Delete**: Dissolve or delete vertices/edges/faces.
- [ ] UV Mapping & Atlas Panel:
  - Planar, Box, and Cylindrical UV projection algorithms.
  - 2D UV island preview over texture.
- [ ] Reversible Mesh Commands (`ExtrudeCommand`, `MoveVerticesCommand`, etc.) wired into `CommandHistory`.
- [ ] Material Assignment: Assign material slots and diffuse/normal textures to selected faces.

---

### Phase 14 — 3D Rigging, Bones & Keyframe Animation Studio

**Objective**: Implement bone hierarchies, vertex skinning, and timeline-based keyframe animation (Blender Animation / Dope Sheet).

**Key Deliverables**:
- [ ] Implement Skeletal Data Structures in `engine-scene`:
  - `Bone`, `Skeleton`, `BoneWeight`, `VertexSkinningData`.
  - Forward Kinematics (FK) and basic 2-bone Inverse Kinematics (IK).
- [ ] Implement Keyframe Animation Engine:
  - Animation curves (Linear, Bézier, Stepped interpolation).
  - Tracks for Transform (Position, Rotation quaternion, Scale) and custom properties.
- [ ] Implement Animation Studio UI (`AnimationTimelinePanel`):
  - Dope sheet and keyframe timeline scrubber.
  - Play, Loop, Reverse, and Record / Auto-keyframe modes.
  - Curve Editor for fine-tuning easing and acceleration.
- [ ] Integration with Scene View:
  - Add `AnimatorComponent` and `SkeletonRendererComponent` to `engine-scene`.
  - Play animations in Edit mode preview and live during Play mode.

---

### Phase 15 — Unified Cross-Studio Asset Pipeline & Live Game Preview

**Objective**: Complete the seamless feedback loop where editing a vector shape, 3D model, or audio synth instantly updates in the active game scene without manual file export.

**Key Deliverables**:
- [ ] **Instant Live Sync Engine**:
  - Editing a vector logo in Studio Vector updates all entities utilizing that sprite in Scene View in real time.
  - Tweaking a low-poly tree in Studio 3D instantly re-renders in the active 3D game level.
  - Adjusting a synth bassline or SFX pitch in Studio Audio immediately plays in the scene's audio emitters.
- [ ] **Unified Multi-Asset Serialization**:
  - Save all project assets into standardized, versioned project formats.
  - Embed vector paths, synth patches, and mesh models cleanly into the asset catalog.
- [ ] **Game Build & Packaging Pipeline**:
  - One-click build script to package the game runtime (`engine-desktop`) with all compiled textures, baked audio, and meshes into an optimized standalone distributable jar/executable.
- [ ] **Comprehensive End-to-End Smoke & Integration Testing**:
  - Headless test coverage validating cross-studio commands, memory leak prevention across studios, and full Play Mode stability.

---

## 6. Actionable Task Checklist: What Still Needs to Be Done

Based on the audit of the current codebase (Phase 8), here is the consolidated checklist of required work items:

### Architectural & Foundation Work
- [ ] Create `engine-vector` subproject in Gradle build.
- [ ] Create `engine-audio` subproject in Gradle build.
- [ ] Create `editor-studios` subproject in Gradle build.
- [ ] Implement `StudioWorkspace` interface and multi-studio navigation toolbar.
- [ ] Refactor `EditorState` to support multi-entity selection (`Set<UUID>`).
- [ ] Remove obsolete legacy directories (`core/` and `lwjgl3/`).

### Vector Studio (Inkscape / Illustrator Domain)
- [ ] Implement `BezierCurve` evaluation and bounding box math in `engine-vector`.
- [ ] Implement `VectorPath` node and segment data model with fills and strokes.
- [ ] Implement ear-clipping triangulation for GPU vector rendering.
- [ ] Build Vector Canvas UI with Pen Tool (`P`) and Node Tool (`N`).
- [ ] Implement rectangle, circle, star, and polygon vector primitives.
- [ ] Implement 9-slice guide dragging on vector art.
- [ ] Implement auto-rasterization to `.png` (`1x`, `2x`, `4x`) directly into `assets/`.
- [ ] Implement SVG import and export parser.

### Audio Studio (FL Studio / LMMS Domain)
- [ ] Implement LibGDX `AudioDevice` PCM float audio streaming in `engine-audio`.
- [ ] Implement multi-oscillator software synth (`AudioSynth`) with ADSR envelope.
- [ ] Implement resonant filter (low-pass/high-pass) and LFO modulation.
- [ ] Implement `SfxrGenerator` with 1-click Jump, Laser, Explosion, Coin, Powerup presets.
- [ ] Build 16-step pattern sequencer UI grid (`StepSequencerPanel`).
- [ ] Build chromatic Piano Roll UI (`PianoRollPanel`) with note drawing and velocity.
- [ ] Build 8-channel mixer UI (`AudioMixerPanel`) with volume, pan, delay, and reverb.
- [ ] Implement `AudioSourceComponent` in `engine-scene`.
- [ ] Implement one-click export to `.ogg` / `.wav` and runtime procedural playback.

### 3D Modeling & Animation Studio (Blender Domain)
- [ ] Implement `PerspectiveCamera3D` and orbit/pan/fly controllers in `engine-render`.
- [ ] Implement `ModelBatch` rendering pass and 3D directional/point lighting.
- [ ] Implement 3D visual gizmos (3-axis arrows, planar squares, rotation rings).
- [ ] Implement editable mesh data model (Half-Edge structure) with vertex/edge/face arrays.
- [ ] Build 3D Mesh Studio UI with Object Mode vs. Mesh Edit Mode (`Tab`).
- [ ] Implement raycast picking for 3D vertices, edges, and faces.
- [ ] Implement modeling operations: Extrude (`E`), Inset (`I`), Loop Cut (`Ctrl+R`), Bevel (`Ctrl+B`).
- [ ] Implement 2D UV mapping preview and unwrapping algorithms.
- [ ] Implement skeletal bone hierarchy (`Bone`, `Skeleton`, `VertexSkinningData`).
- [ ] Build Animation Timeline and Dope Sheet UI with keyframe curves.
- [ ] Implement `AnimatorComponent` and `MeshRendererComponent` in `engine-scene`.

### Cross-Studio Integration & Polish
- [ ] Wire all mesh, vector, and audio edits into the Milestone 8 `EditorCommand` / `CommandHistory` undo/redo system.
- [ ] Implement live asset synchronization (modifications in studios immediately reflect in active scene view).
- [ ] Ensure full Play Mode isolation across 3D simulation, vector assets, and audio sources.
- [ ] Maintain 0-leak resource disposal rules (`ResourceManager` and `AssetService`) across all new studios.
- [ ] Expand automated test suite to maintain >80% code coverage across all new subprojects.
