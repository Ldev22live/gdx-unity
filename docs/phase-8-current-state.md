# Phase 8 Current State Assessment: Editor Reliability, Undo/Redo, and Full Engine/Editor Maturity

**Project**: GdxUnity  
**Target Milestone**: Milestone 8 — Editor Reliability, Undo/Redo, and Scene Workflow  
**Baseline Date**: October 2024 / Milestone 8 Completed  
**Status**: Milestone 8 Complete (All Milestones 0–8 Fully Implemented & Verified)  
**Technology Stack**: Java 21 LTS | LibGDX 1.14.2 | LWJGL3 3.4.1 | Gradle 9.6.1 | JUnit Jupiter 5.10.0  

---

## 1. Executive Summary

The **GdxUnity** project has transitioned from an initial minimal template into a fully functional, multi-module 2D game engine and visual editor development environment inspired by the productivity and architectural workflows of Unity.

At the conclusion of **Phase 8 (Milestone 8)**, the project boasts:
1. **Full Runtime/Editor Separation**: Six active Gradle subprojects cleanly partitioned between runtime engine layers (`engine-core`, `engine-scene`, `engine-render`, `engine-desktop`) and editor layers (`editor-core`, `editor-desktop`).
2. **Authoring & Simulation Isolation (Play Mode)**: A battle-tested `PlayModeController` enabling Play, Pause, and Stop with strict scene cloning via versioned JSON serialization (`SceneSerializer`, schema version 1), ensuring gameplay runtime simulation never mutates authored editor state.
3. **Interactive Viewport & Visual Gizmos**: Complete Move, Rotate, and Scale gizmo interaction model with screen-scale camera adaptation, input priority over entities, and mathematical isolation in `editor-core` for headless testability.
4. **Persistent Asset Pipeline & Browser**: Immutable project-relative `AssetReference` system with filesystem discovery (`AssetCatalog`), single-point GPU texture caching and disposal (`AssetService`), serializable `SpriteRendererComponent`, and integrated `AssetBrowserPanel`.
5. **Robust Command & Undo/Redo System (Milestone 8)**: Reversible command architecture (`EditorCommand`, `CommandHistory`) supporting fine-grained undo/redo for entity creation, deletion (with full hierarchy/component preservation), renaming, active toggling, continuous transform gizmo manipulations (aggregated on drag end), sprite asset assignments, and component attachments.
6. **Headless Quality Verification**: 98 comprehensive automated unit and integration tests across 21 test suites passing under JUnit 5 without requiring an active OpenGL context.

---

## 2. Documentation Lookup & Audit (`docs/`)

The `docs/` directory maintains an exhaustive record of architecture, decisions, test strategies, and milestone implementation reports:

| Document / Location | Summary & Milestone Alignment |
| :--- | :--- |
| **`PROJECT_VISION.md`** | High-level charter: Unity-like visual editor, entity/component system, versioned scene serialization, multi-game extensibility, runtime independence, and desktop-first focus. |
| **`ARCHITECTURE.md`** | Comprehensive architectural specification: 6-module layout, strict dependency direction (`editor-desktop` → `editor-core` / `engine-render` → `engine-scene` → `engine-core`), package hierarchy, resource management, asset pipeline, and command pattern architecture. |
| **`ROADMAP.md`** | Complete tracking of Milestones 0 through 8 (all marked Complete ✅), with defined scope for future milestones (Milestone 9: 2D Physics/Box2D, Animation, Audio, Advanced Rendering, Packaging). |
| **`TECHNICAL_DECISIONS.md`** | Canonical log of 18 Technical Decisions:<br>• **TD-001 – TD-006**: Java 21, dependency trimming, modularity, package structure, JUnit 5, LibGDX 1.14.2.<br>• **TD-007 – TD-010**: Frame timing, LIFO ResourceManager, stable UUID entity model, Transform dirty flag propagation.<br>• **TD-011 – TD-014**: FitViewport 2D camera, component rendering pipeline, programmatic scene2d skin (`EditorSkinFactory`), UUID selection & 2D bounds picking.<br>• **TD-015 – TD-018**: Play Mode scene cloning, visual gizmos math/renderer, project-relative asset pipeline, command-based undo/redo architecture. |
| **`TEST_STRATEGY.md`** | Testing pyramid guidelines: Unit, Integration, and Smoke tests; convention standards (`should{Behavior}When{Condition}`); 0-flakiness policy; headless test execution patterns. |
| **`phase-0-current-state.md`** | Initial baseline report recording Milestone 0 setup. |
| **`milestone-1-implementation-report.md`** | Implementation report for engine lifecycle, `FrameClock`, `ResourceManager`, and `PrimitiveRenderer`. |
| **`milestone-2-implementation-report.md`** | Implementation report for `Entity`, `Component`, `Transform`, `SceneManager`, and versioned `SceneSerializer`. |
| **`milestone-3-implementation-report.md`** | Implementation report for `OrthographicCamera2D`, `SceneRenderer2D`, `ShapeRendererComponent`, and aspect ratio preservation. |
| **`milestone-4-implementation-report.md`** | Implementation report for initial visual editor shell, Hierarchy, Inspector, Toolbar, StatusBar, and Viewport picking. |
| **`milestones/milestone-5-play-mode.md`** | Implementation report for Play/Pause/Stop state machine, serialized scene cloning, and simulation isolation. |
| **`milestones/milestone-6-transform-gizmos.md`** | Implementation report for Move/Rotate/Scale gizmos, `TransformGizmoMath`, handle hit-testing, and dynamic camera scaling. |
| **`milestones/milestone-7-asset-pipeline.md`** | Implementation report for `AssetReference`, `AssetCatalog`, `AssetService`, `SpriteRendererComponent`, and `AssetBrowserPanel`. |
| **`milestones/milestone-8-editor-undo-redo.md`** | Implementation report for `EditorCommand`, `CommandHistory`, drag aggregation, toolbar undo/redo controls, and keyboard shortcuts. |
| **`adr/0000-adr-template.md`** | Standardized Architecture Decision Record template. |

---

## 3. Active Module Structure & Dependency Topology

The project is structured into six decoupled Gradle subprojects in `settings.gradle`:

```
                           ┌────────────────────────┐
                           │     editor-desktop     │
                           └────────┬──────┬────────┘
                                    │      │
                         ┌──────────┘      └──────────┐
                         ▼                            ▼
               ┌───────────────────┐        ┌───────────────────┐
               │    editor-core    │        │   engine-desktop  │
               └─────────┬─────────┘        └─────────┬─────────┘
                         │                            │
                         │    ┌───────────────────────┘
                         ▼    ▼
               ┌───────────────────┐
               │   engine-render   │
               └─────────┬─────────┘
                         │
                         ▼
               ┌───────────────────┐
               │   engine-scene    │
               └─────────┬─────────┘
                         │
                         ▼
               ┌───────────────────┐
               │    engine-core    │
               └───────────────────┘
```

### Module Responsibilities

1. **`engine-core`** (`io.github.ldev22live.gdxunity.engine.core`):
   - Engine lifecycle contracts (`EngineLifecycle`, `EngineLifecycleListener`, `EngineState`).
   - Timing and frame rate management (`FrameClock`, `TimeProvider`, `LibGdxTimeProvider`).
   - Resource disposal lifecycle (`ResourceManager`, `ResourceDisposalException`).
   - Immutable persistent asset identification and cataloging (`AssetReference`, `AssetType`, `AssetMetadata`, `AssetCatalog`, `AssetService`).
   - Base application runner (`EngineApplication`).

2. **`engine-scene`** (`io.github.ldev22live.gdxunity.engine.scene`):
   - Entity system with stable UUID identification (`Entity`).
   - Extensible component architecture (`Component`, `BaseComponent`).
   - Hierarchical scene graph with dirty flag propagation and cycle prevention (`Transform`).
   - Scene lifecycle and entity management (`Scene`, `SceneManager`).
   - Versioned JSON serialization and component registry (`SceneSerializer`, `ComponentSerializer`, `ComponentSerializerRegistry`).

3. **`engine-render`** (`io.github.ldev22live.gdxunity.engine.render`):
   - 2D camera abstraction with aspect ratio preservation (`Camera`, `OrthographicCamera2D`).
   - Scene rendering pipeline (`SceneRenderer2D`, `PrimitiveRenderer`, `Renderable`).
   - Built-in visual components (`ShapeRendererComponent`, `SpriteRendererComponent`).
   - Dedicated component serializers (`ShapeRendererComponentSerializer`, `SpriteRendererComponentSerializer`).

4. **`engine-desktop`** (`io.github.ldev22live.gdxunity.engine.desktop`):
   - Standalone game runtime launcher (`DesktopLauncher`).
   - OS-specific LWJGL3 startup workarounds (`StartupHelper`).

5. **`editor-core`** (`io.github.ldev22live.gdxunity.editor.core`):
   - Editor state model and selection tracking (`EditorState`, `ToolMode`).
   - Viewport entity picking via 2D bounding boxes (`ViewportPicker`).
   - Inspector view model with Euler angle conversion and validation (`EntityInspectorModel`).
   - Play Mode simulation state machine and scene isolation (`PlayModeController`, `PlayModeState`).
   - Transform gizmo mathematics and hit-testing (`GizmoHandle`, `TransformSnapshot`, `TransformGizmoMath`, `GizmoInteractionController`).
   - Reversible command pattern and bounded history (`EditorCommand`, `CommandHistory`, and concrete commands).

6. **`editor-desktop`** (`io.github.ldev22live.gdxunity.editor.desktop`):
   - Desktop editor application shell and launcher (`EditorApplication`, `EditorLauncher`, `EditorStartupHelper`).
   - Programmatic dark-theme skin generator (`EditorSkinFactory`).
   - Docked UI panels (`EditorToolbar`, `HierarchyPanel`, `InspectorPanel`, `AssetBrowserPanel`, `EditorStatusBar`).
   - In-viewport transform gizmo rendering (`TransformGizmoRenderer`).
   - Unified input routing for picking, gizmo dragging, camera navigation, and keyboard shortcuts.

---

## 4. Subsystem Audits & Current Capabilities

### 4.1 Engine Runtime & Lifecycle
- **State Machine**: Transitions through `UNINITIALIZED` → `INITIALIZING` → `RUNNING` ⇄ `PAUSED` → `DISPOSING` → `DISPOSED`.
- **Clock**: `FrameClock` accumulates total elapsed time and clamps delta time to prevent physics/render explosion during long frame delays (e.g. window dragging or breakpoints).
- **Resource Safety**: `ResourceManager` disposes native LibGDX resources in strict LIFO order, catching and aggregating cleanup exceptions.

### 4.2 Scene & Component Architecture
- **Entity Model**: Entities feature immutable `UUID` identities, string names, active flags, and transform nodes.
- **Hierarchy**: `Transform` supports parent-child relationships, calculates 4x4 world matrices with cached dirty propagation, and actively prevents cyclic parenting loops.
- **Serialization**: `SceneSerializer` saves/loads entire scenes to human-readable versioned JSON (`schemaVersion: 1`), faithfully restoring entity UUIDs, active states, hierarchical relations, and component properties via `ComponentSerializerRegistry`.

### 4.3 Rendering & Viewport Management
- **Camera2D**: Encapsulates `FitViewport` and `OrthographicCamera` centered at world origin `(0, 0)`. Window resizing adapts viewports without altering world coordinate scales.
- **Pipeline**: `SceneRenderer2D` traverses active entities and executes `Renderable` components (`ShapeRendererComponent` and `SpriteRendererComponent`), binding each entity's world matrix to the rendering context.

### 4.4 Asset Pipeline & Resource Caching
- **AssetReference**: Enforces project-relative paths, forward slash normalization, and security checks against path traversal (`..`) or absolute drive roots.
- **Discovery**: `AssetCatalog` scans the asset tree recursively and collects file metadata without allocating GPU textures.
- **Lazy GPU Cache**: `AssetService` decodes textures on demand and unloads them cleanly upon application termination, preventing double disposal during scene swaps or Play Mode cycles.

### 4.5 Visual Editor Shell & Panels
- **Zero External UI Assets**: Built entirely using LibGDX `scene2d.ui` styled by programmatic 1x1 pixmaps and default bitmap fonts generated in `EditorSkinFactory`.
- **Layout**:
  - **Top**: `EditorToolbar` (New Scene, Save, Load, Tool Modes: Select/Move/Rotate/Scale, Play/Pause/Stop controls, Undo/Redo buttons, Reset Camera).
  - **Left**: `HierarchyPanel` (Indented scene graph tree, `+ Create Entity`, `- Delete Entity`).
  - **Center**: Viewport area with pan (`Right-click drag` or `Middle-click drag`), zoom (`Scroll wheel`), in-viewport entity picking (`Left click`), and visual transform gizmo manipulation.
  - **Right**: `InspectorPanel` (Entity UUID, editable Name, Active checkbox, Local Transform inputs with real-time Euler conversion, World Transform readouts, Add Component, and Sprite Renderer configuration with asset assignment).
  - **Bottom Left**: `AssetBrowserPanel` (Filesystem asset tree listing discovered textures with metadata and selection).
  - **Bottom Bar**: `EditorStatusBar` (Simulation status, tool notifications, and error reporting).

### 4.6 Visual Transform Gizmos
- **Move Gizmo**: Red X axis, green Y axis, and yellow dual-axis center square.
- **Rotate Gizmo**: Cyan circular ring with continuous degree tracking around Z.
- **Scale Gizmo**: Red X box, green Y box, and yellow uniform scale center box with positive minimum scale protection (`MIN_SCALE = 0.001f`).
- **Camera Adaptation**: Gizmo handle geometries scale dynamically with viewport zoom, maintaining consistent screen-space click targets.

### 4.7 Play Mode & Simulation Isolation
- **Snapshot Cloning**: Entering Play Mode serializes the authored scene to JSON in memory and deserializes a sandboxed runtime scene instance.
- **Input Gating**: Destructive authoring actions (entity creation, deletion, scene saving, loading, new scene, and undo/redo) are strictly locked while playing.
- **Reconciliation**: Exiting Play Mode destroys the runtime scene and seamlessly restores the authored scene, selection, and history stack.

### 4.8 Command & Undo/Redo Architecture (Milestone 8)
- **Bounded History**: `CommandHistory` maintains an `ArrayDeque` stack capped at 100 operations.
- **Reversible Operations**:
  - `CreateEntityCommand`: Re-adds or removes entity, preserving selection.
  - `DeleteEntityCommand`: Captures complete entity snapshot (UUID, name, active state, transform, parent UUID, and all components) for full structural restoration.
  - `RenameEntityCommand` & `ChangeEntityActiveCommand`: Reversible field mutations.
  - `ChangeTransformCommand`: Captures immutable `TransformSnapshot`s before and after modification; ignores sub-epsilon no-op movements.
  - `AssignSpriteAssetCommand`: Swaps `AssetReference` safely without altering underlying GPU texture cache.
  - `AddComponentCommand`: Reversible component attachment.
- **Gizmo Aggregation**: Continuous mouse drag updates viewport transforms in real time, but commits exactly one `ChangeTransformCommand` when mouse is released (`endDrag()`).
- **Keyboard Shortcuts**: Global shortcut handling in `ViewportInputProcessor` for `Ctrl+Z` (Undo), `Ctrl+Y` (Redo), and `Ctrl+Shift+Z` (Redo).

---

## 5. Complete Source Code Inventory

### Active Modules Class Breakdown (64 Java Source Files)

#### `editor-core` (19 source files, 7 test suites)
- `command/EditorCommand.java` — Command contract interface.
- `command/CommandHistory.java` — Bounded undo/redo history manager.
- `command/CreateEntityCommand.java` — Entity creation command.
- `command/DeleteEntityCommand.java` — Deep entity deletion and restoration command.
- `command/RenameEntityCommand.java` — Entity renaming command.
- `command/ChangeEntityActiveCommand.java` — Entity active toggle command.
- `command/ChangeTransformCommand.java` — Transform modification command with snapshotting.
- `command/AssignSpriteAssetCommand.java` — Reversible sprite asset assignment command.
- `command/AddComponentCommand.java` — Component attachment command.
- `gizmo/GizmoHandle.java` — Gizmo handle enumeration.
- `gizmo/TransformSnapshot.java` — Immutable transform state record.
- `gizmo/TransformGizmoMath.java` — Pure math for handle hit-testing and dragging.
- `gizmo/GizmoInteractionController.java` — Gizmo drag state machine and aggregation.
- `inspector/EntityInspectorModel.java` — Inspector data extraction, validation, and Euler math.
- `picking/ViewportPicker.java` — 2D bounding-box entity picker.
- `play/PlayModeController.java` — Play/Pause/Stop lifecycle controller and scene cloner.
- `play/PlayModeState.java` — Simulation state enum.
- `EditorState.java` — Authoring state container (active scene, selection, tool mode, history).
- `ToolMode.java` — Editor tool enum (SELECT, MOVE, ROTATE, SCALE).

#### `editor-desktop` (10 source files)
- `EditorApplication.java` — Main editor application adapter, render loop, and input dispatcher.
- `EditorLauncher.java` — Desktop entry point for the visual editor.
- `EditorStartupHelper.java` — Multi-platform JVM bootstrap utility.
- `ui/EditorSkinFactory.java` — Programmatic dark-theme scene2d skin factory.
- `ui/EditorToolbar.java` — Top toolbar with file, tool, play, and undo/redo controls.
- `ui/HierarchyPanel.java` — Left panel displaying the indented scene graph.
- `ui/InspectorPanel.java` — Right panel displaying editable properties of selected entity.
- `ui/AssetBrowserPanel.java` — Bottom-left panel listing project assets.
- `ui/EditorStatusBar.java` — Bottom status and error notification bar.
- `ui/TransformGizmoRenderer.java` — Viewport renderer for Move/Rotate/Scale gizmos.

#### `engine-core` (15 source files, 5 test suites)
- `asset/AssetCatalog.java` — Filesystem asset scanner.
- `asset/AssetMetadata.java` — Asset file metadata record.
- `asset/AssetReference.java` — Immutable project-relative asset path handle.
- `asset/AssetService.java` — Centralized texture cache and GPU lifecycle manager.
- `asset/AssetType.java` — Asset type enum (TEXTURE, UNKNOWN).
- `resource/ResourceManager.java` — LIFO native resource manager.
- `resource/ResourceDisposalException.java` — Cleanup failure exception.
- `time/FrameClock.java` — Frame timing, elapsed time, and delta clamping.
- `time/TimeProvider.java` — Clock source abstraction.
- `time/LibGdxTimeProvider.java` — LibGDX `Gdx.graphics` time provider.
- `EngineApplication.java` — Runtime application adapter.
- `EngineLifecycle.java` — Engine lifecycle contract.
- `EngineLifecycleListener.java` — Lifecycle observer interface.
- `EngineRenderer.java` — Renderer lifecycle contract.
- `EngineState.java` — Engine state enum.

#### `engine-desktop` (2 source files)
- `DesktopLauncher.java` — Standalone game runtime launcher.
- `StartupHelper.java` — Runtime JVM bootstrap utility.

#### `engine-render` (9 source files, 5 test suites)
- `camera/Camera.java` — Camera contract interface.
- `camera/OrthographicCamera2D.java` — Orthographic 2D camera with FitViewport.
- `components/ShapeRendererComponent.java` — Colored rectangle renderable component.
- `components/SpriteRendererComponent.java` — Textured sprite renderable component.
- `components/serialization/ShapeRendererComponentSerializer.java` — JSON serializer for shape component.
- `components/serialization/SpriteRendererComponentSerializer.java` — JSON serializer for sprite component.
- `PrimitiveRenderer.java` — Basic geometric primitive renderer.
- `Renderable.java` — Renderable component interface.
- `SceneRenderer2D.java` — 2D scene rendering pipeline.

#### `engine-scene` (10 source files, 4 test suites)
- `serialization/ComponentSerializer.java` — Component JSON serializer interface.
- `serialization/ComponentSerializerRegistry.java` — Registry of component serializers.
- `serialization/SceneSerializationException.java` — Scene serialization error.
- `serialization/SceneSerializer.java` — Versioned scene JSON serializer.
- `BaseComponent.java` — Base component implementation.
- `Component.java` — Component marker interface.
- `Entity.java` — Scene entity container with UUID and transform.
- `Scene.java` — Scene container and entity lifecycle coordinator.
- `SceneManager.java` — Active scene switcher.
- `Transform.java` — Hierarchical transform node with matrix caching.

#### Legacy Modules (Unlinked in `settings.gradle`)
- `core/` (contains legacy `io.github.ldev22live.GdxUnity`)
- `lwjgl3/` (contains legacy `io.github.ldev22live.lwjgl3.Lwjgl3Launcher`)

---

## 6. Automated Test Results Audit

All automated tests are executed in a headless JVM environment without native display server or OpenGL hardware context dependencies.

### Summary Across Subprojects
- **Total Tests**: **98**
- **Passing**: **98 (100%)**
- **Failures**: **0**
- **Errors**: **0**
- **Skipped**: **0**

### Breakdown by Subproject & Test Suite

| Module | Test Suite Class | Tests | Status | Coverage Focus |
| :--- | :--- | :---: | :---: | :--- |
| **`editor-core`** | `CommandHistoryTest` | 6 | Pass | Execute/undo/redo sequence, redo stack clearing, bounded limits, empty stack safety, failure protection. |
| | `EditorCommandsTest` | 7 | Pass | Rename, active toggle, transform reversal, entity create/delete round-trip, asset assignment, history clearing on scene switch. |
| | `TransformGizmoTest` | 9 | Pass | Move/rotate/scale drag calculations, handle hit-testing, zoom scaling, command aggregation on drag end, skipping no-ops. |
| | `PlayModeControllerTest` | 10 | Pass | Play/Pause/Stop transitions, scene cloning, validation checks, input gating, selection mapping, authored scene restoration. |
| | `EditorStateTest` | 7 | Pass | Selection reconciliation, tool switching, transform modification events, history interactions. |
| | `EntityInspectorModelTest` | 3 | Pass | Euler angle extraction, quaternion conversion, input parsing and validation. |
| | `ViewportPickerTest` | 3 | Pass | 2D bounding-box picking against transformed shapes and sprites. |
| **`engine-core`** | `EngineLifecycleTest` | 7 | Pass | Engine state machine transitions, event listener notifications. |
| | `ResourceManagerTest` | 6 | Pass | LIFO disposal ordering, double-disposal protection, aggregated exception handling. |
| | `FrameClockTest` | 6 | Pass | Delta calculation, frame timing, max delta clamping. |
| | `AssetReferenceTest` | 5 | Pass | Path normalization, rejection of traversal (`..`) and absolute drive roots (`C:/`). |
| | `AssetCatalogTest` | 3 | Pass | Recursive filesystem discovery, extension filtering, deterministic sorting. |
| **`engine-render`** | `OrthographicCamera2DTest` | 5 | Pass | Virtual resolution, translation, zoom bounds, viewport aspect ratio preservation. |
| | `PrimitiveRendererTest` | 2 | Pass | Basic primitive rendering contract and lifecycle. |
| | `RenderableComponentTest` | 3 | Pass | Shape and sprite component properties, dimensions, centering, tinting. |
| | `SceneRenderer2DTest` | 1 | Pass | Scene traversal, transform matrix application, lifecycle disposal. |
| | `SpriteRendererComponentSerializerTest` | 2 | Pass | JSON round-trip serialization of `SpriteRendererComponent` with `AssetReference`. |
| **`engine-scene`** | `EntityAndComponentTest` | 5 | Pass | UUID stability, component attachment, detachment, component querying. |
| | `TransformTest` | 8 | Pass | Local/world matrices, parent-child inheritance, dirty flag caching, cycle prevention. |
| | `SceneTest` | 5 | Pass | Scene entity lifecycle, deferred entity addition/removal, root transform hierarchy. |
| | `SceneSerializerTest` | 5 | Pass | Schema versioning, round-trip serialization, missing parent handling, duplicate ID detection. |

---

## 7. Technical Decisions Audit Summary (TD-001 through TD-018)

| Decision ID | Title | Summary of Strategic Choice |
| :--- | :--- | :--- |
| **TD-001** | Java 21 Target | Upgraded to modern LTS Java across build chain. |
| **TD-002** | Dependency Pruning | Removed Fleks ECS, KTX libraries, and unused plugins to establish a minimal baseline. |
| **TD-003** | Multi-Module Architecture | Decoupled runtime and editor subprojects into independent modules. |
| **TD-004** | Consistent Package Naming | Standardized package prefix `io.github.ldev22live.gdxunity.*`. |
| **TD-005** | JUnit Jupiter 5 | Standardized on JUnit 5 with `useJUnitPlatform()`. |
| **TD-006** | LibGDX 1.14.2 & LWJGL3 3.4.1 | Pinned core runtime libraries with verified Java 21 compatibility. |
| **TD-007** | Frame Timing & Delta Clamping | Implemented `FrameClock` with maximum delta clamping. |
| **TD-008** | LIFO Resource Management | Explicit ownership and reverse-order resource disposal. |
| **TD-009** | UUID Entity Model | Stable UUIDs persisting through JSON serialization. |
| **TD-010** | Authoritative Transform & Dirty Flags | Single transform component with dirty propagation to avoid redundant matrix math. |
| **TD-011** | Camera Abstraction & FitViewport | Virtual aspect ratio preservation via `OrthographicCamera2D`. |
| **TD-012** | Component Rendering Pipeline | Component-driven drawing via `Renderable` and `SceneRenderer2D`. |
| **TD-013** | Programmatic scene2d Skin | Self-contained dark-theme UI skin without external binary skin assets. |
| **TD-014** | EditorState & 2D Bounds Picking | Headless selection model and unprojected bounding-box picking in `editor-core`. |
| **TD-015** | Play Mode Scene Cloning | Isolated simulation via versioned JSON serialization snapshots. |
| **TD-016** | Visual Transform Gizmos | Headless math in `editor-core` with zoom-adaptive `ShapeRenderer` drawing in `editor-desktop`. |
| **TD-017** | Project-Relative Asset Pipeline | Normalized `AssetReference`, pure metadata `AssetCatalog`, and centralized GPU `AssetService`. |
| **TD-018** | Command Pattern & Undo/Redo | Reversible `EditorCommand` architecture with drag aggregation and Play Mode isolation. |

---

## 8. Launch & Verification Instructions

### 8.1 Execute Automated Tests
To run all 98 automated unit and integration tests across all modules:
```bash
./gradlew test
```

### 8.2 Launch the Standalone Game Runtime
To launch the minimal desktop runtime:
```bash
./gradlew :engine-desktop:run
```

### 8.3 Launch the Visual Desktop Editor
To launch the full GdxUnity desktop editor:
```bash
./gradlew :editor-desktop:run
```

#### Interactive Verification Checklist in Editor
- [x] **Hierarchy**: Click `+ Create Entity` to spawn an entity; click `- Delete` to remove it.
- [x] **Undo / Redo**:
  - Click `↺ Undo` or press `Ctrl+Z` to reverse entity creation/deletion, renaming, active toggle, or transform changes.
  - Click `↻ Redo` or press `Ctrl+Y` / `Ctrl+Shift+Z` to reapply reversed operations.
- [x] **Gizmo Dragging**:
  - Select an entity, switch to Move (`W`), Rotate (`E`), or Scale (`R`) mode.
  - Drag gizmo handles in the viewport. When released, exactly one undoable command is added to the toolbar.
- [x] **Asset Browser & Sprites**:
  - Select a texture in the bottom-left `AssetBrowserPanel`.
  - In `InspectorPanel`, click `Assign Selected Asset` to assign it to an existing or new `SpriteRendererComponent`.
  - Undo reverses the sprite assignment without unloading or crashing GPU textures.
- [x] **Play Mode**:
  - Click `▶ Play`. Notice that destructive actions and Undo/Redo are disabled.
  - Click `⏸ Pause` to freeze simulation.
  - Click `⏹ Stop` to terminate playback. The authored scene and undo history are restored intact.
- [x] **Scene Persistence**: Click `Save Scene` to write JSON to disk; click `New Scene` to clear; click `Load Scene` to verify complete round-trip restoration.

---

## 9. Identified Tech Debt & Strategic Recommendations for Milestone 9

With Milestone 8 complete, the editor and engine foundation are stable, reversible, and thoroughly tested. The following areas represent the logical roadmap progression:

### 9.1 Immediate Housekeeping
1. **Remove Obsolete Legacy Modules**: Permanently remove the unlinked `core/` and `lwjgl3/` directories from the repository to prevent confusion.
2. **Multi-Entity Selection**: Expand `EditorState` from single-entity selection (`UUID selectedEntityId`) to a selection set (`Set<UUID>`), enabling batch movement and deletion commands.

### 9.2 Proposed Milestone 9: 2D Physics Integration (Box2D) & Collider Visualization
1. **Module Integration**:
   - Add `com.badlogicgames.gdx:gdx-box2d` and desktop natives to `engine-scene` and `engine-desktop`.
2. **Physics Components**:
   - `RigidBody2DComponent`: Body type (Static, Kinematic, Dynamic), mass, gravity scale, linear/angular damping.
   - `BoxCollider2DComponent` & `CircleCollider2DComponent`: Dimensions, radius, friction, restitution, sensor flag.
3. **Simulation System**:
   - Physics world step integration inside `Scene.update()` during Play Mode.
4. **Editor Visualizations**:
   - Render physics collider wireframes in the editor viewport using `ShapeRenderer` (green outlines).
5. **Reversible Command Routing**:
   - Support undo/redo for collider resizing and physics body property edits.
