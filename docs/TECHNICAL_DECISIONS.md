# Technical Decisions

## Decision Record Format

This document records significant technical decisions. Each decision should reference an Architecture Decision Record (ADR) in `docs/adr/` for detailed context.

## Milestone 0 Decisions

### TD-001: Java 21 Compatibility

**Status**: Approved

**Context**: Project specification requires Java 21. Initial project was configured for Java 8.

**Decision**: Upgrade all Gradle configurations to target Java 21 compatibility.

**Rationale**:
- Java 21 is a Long-Term Support (LTS) release
- LibGDX 1.14.2 has verified Java 21 compatibility
- Modern Java features improve code quality
- Future-proof for long-term project

**Consequences**:
- All modules now target Java 21
- Build configuration updated in root and subproject build.gradle files
- LWJGL3 constraints ensure compatibility with Java 21+

**Related ADR**: TBD

---

### TD-002: Dependency Removal

**Status**: Approved

**Context**: Initial project included Fleks ECS and many KTX libraries that are not immediately needed.

**Decision**:
- Remove Fleks ECS (version 2.14) - evaluate ECS only when requirements justify it
- Remove all KTX libraries - will add back individually when needed
- Remove gdx-controllers - not needed for initial milestones
- Remove gdx-box2d - defer to physics milestone
- Remove gdx-freetype - defer to font/text milestone
- Remove gdx-tools - not needed for initial milestones

**Rationale**:
- Minimize dependency surface area
- Reduce maintenance burden
- Avoid premature abstractions
- Add dependencies only when needed

**Consequences**:
- Only LibGDX core and LWJGL3 backend remain
- Project has minimal dependency footprint
- Future dependencies will be added with justification

**Retained Dependencies**:
- `com.badlogicgames.gdx:gdx:1.14.2` - Core LibGDX library
- `com.badlogicgames.gdx:gdx-backend-lwjgl3:1.14.2` - Desktop backend
- `com.badlogicgames.gdx:gdx-platform:1.14.2:natives-desktop` - Native libraries
- `org.junit.jupiter:junit-jupiter-api:5.10.0` - Testing framework
- `org.junit.jupiter:junit-jupiter-engine:5.10.0` - Testing framework

**Related ADR**: TBD

---

### TD-003: Multi-Module Structure

**Status**: Approved

**Context**: Initial project had two modules (core, lwjgl3). Need modular architecture for separation of concerns.

**Decision**: Implement four initial modules:
- `engine-core` - Core interfaces and services
- `engine-scene` - Entity/component and scene management
- `engine-render` - Rendering abstractions and implementation
- `engine-desktop` - Desktop launcher and platform code

**Rationale**:
- Clear separation of concerns
- Enforce dependency boundaries
- Enable independent testing
- Support future module additions

**Dependency Rules**:
- `engine-core` has no dependencies on other engine modules
- `engine-scene` depends on `engine-core`
- `engine-render` depends on `engine-core` and `engine-scene`
- `engine-desktop` depends on `engine-core`, `engine-scene`, `engine-render`
- No circular dependencies

**Consequences**:
- Gradle settings.gradle updated with new modules
- Each module has its own build.gradle
- Package structure enforced: `io.github.ldev22live.gdxunity.engine.{module}`
- Old `core` and `lwjgl3` modules will be deprecated

**Related ADR**: TBD

---

### TD-004: Package Naming

**Status**: Approved

**Context**: Need consistent package naming for new engine code.

**Decision**: Use `io.github.ldev22live.gdxunity.engine` as root package with subpackages:
- `io.github.ldev22live.gdxunity.engine.core` - Core module
- `io.github.ldev22live.gdxunity.engine.scene` - Scene module
- `io.github.ldev22live.gdxunity.engine.render` - Render module
- `io.github.ldev22live.gdxunity.engine.desktop` - Desktop module

**Rationale**:
- Consistent with repository name (gdx-unity)
- Clear module boundaries in package structure
- Follows Java package naming conventions
- Reverse domain name for uniqueness

**Consequences**:
- All new code uses this package structure
- Old code in `io.github.ldev22live` will be migrated or deprecated

**Related ADR**: TBD

---

### TD-005: Test Framework

**Status**: Approved

**Context**: No test framework was configured. Need automated testing capability.

**Decision**: Use JUnit 5 (version 5.10.0) for all automated tests.

**Rationale**:
- JUnit 5 is modern and well-maintained
- Good integration with Gradle
- Supports parameterized tests
- Industry standard for Java testing

**Consequences**:
- JUnit 5 added to all modules
- Test task configured with `useJUnitPlatform()`
- Test source directories created for all modules

**Related ADR**: TBD

---

### TD-006: LibGDX Version

**Status**: Approved

**Context**: Project specification requires LibGDX 1.14.2.

**Decision**: Keep LibGDX at version 1.14.2 as specified.

**Rationale**:
- Matches project specification
- Verified Java 21 compatibility
- Stable release with good documentation
- No known issues with current requirements

**Consequences**:
- All LibGDX dependencies use version 1.14.2
- LWJGL3 constrained to version 3.4.1 for Java 21+ compatibility

---

## Milestone 1 Decisions

### TD-007: Explicit Engine Lifecycle State Machine & Timing Abstraction

**Status**: Approved

**Context**: Milestone 1 requires a robust, testable engine runtime lifecycle separating platform startup from core update loops, with deterministic frame timing.

**Decision**:
- Implement `EngineState` enum (`NEW`, `RUNNING`, `PAUSED`, `DISPOSING`, `DISPOSED`, `FAILED`).
- Implement `EngineLifecycle` and `EngineRenderer` interfaces to decouple engine orchestration from rendering backends.
- Implement `FrameClock` with `TimeProvider` interface for injecting deterministic clock deltas in unit tests, with negative/NaN/infinity sanitization and configurable delta clamping (defaulting to 0.1s / 100ms).

**Rationale**:
- Eliminates reliance on global mutable static timing state in unit tests.
- Prevents frame spikes from destabilizing downstream transforms or updates.
- Decouples core lifecycle from concrete graphics hardware.

**Consequences**:
- Lifecycle events can be verified deterministically in CI environments without OpenGL/LWJGL windows.

---

### TD-008: LIFO Explicit Resource Ownership & Safe Disposal

**Status**: Approved

**Context**: Native resources (textures, framebuffers, shape renderers) must be disposed deterministically without duplicate disposal or early termination when a single resource throws an error.

**Decision**:
- Implement `ResourceManager` in `engine-core` adhering to LIFO (reverse registration) disposal order.
- Implement duplicate registration deduplication and idempotent `dispose()`.
- Collect exceptions thrown during disposal into a `ResourceDisposalException` with suppressed causes so every owned resource is guaranteed a disposal attempt.

**Rationale**:
- Child resources commonly depend on parents; reverse-order cleanup ensures child release before parent destruction.
- Ensures native memory leaks are prevented even during error situations.

---

## Milestone 2 Decisions

### TD-009: Transform Hierarchy with Dirty-Flag Propagation and Cycle Prevention

**Status**: Approved

**Context**: Milestone 2 requires hierarchical transforms connecting parent and child entities, world-coordinate recalculation, reparenting with coordinate preservation, and cycle prevention.

**Decision**:
- Implement `Transform` tightly bound to `Entity`.
- Represent local transforms using LibGDX `Vector3` (position, scale) and `Quaternion` (rotation).
- Calculate `Matrix4` world transforms lazily on request, marking descendants dirty recursively whenever a parent transform changes.
- Reject self-parenting and ancestor cycles using `isDescendantOf` checks.
- Support `setParent(newParent, preserveWorldTransform)` to automatically recompute local space values to maintain stable world position, rotation, and scale during hierarchy rearrangement.

**Rationale**:
- Avoids eager matrix multiplication for static entities.
- Ensures hierarchy mutations never crash with infinite recursion.

---

### TD-010: Versioned Scene Serialization via ComponentSerializerRegistry

**Status**: Approved

**Context**: Scenes and attached entities must be persistently serialized to JSON, supporting versioning, hierarchy reconstruction, and extensible component serialization without arbitrary reflection.

**Decision**:
- Implement `SceneSerializer` using LibGDX's native `com.badlogic.gdx.utils.Json` with explicit `schemaVersion: 1`.
- Establish `ComponentSerializer<T>` and `ComponentSerializerRegistry` to isolate serialization of component state from runtime dependencies.
- Reconstruct parent-child relationships in a two-pass deserialization step (first instantiate all entities with their UUIDs, then wire parent transforms).
- Strictly validate UUID validity, duplicate IDs, missing parents, and schema versions.

**Rationale**:
- Avoids adding external JSON dependencies (e.g. Jackson/Gson), keeping the build footprint minimal.
- Two-pass deserialization allows scenes to be stored in arbitrary order without requiring root-first sorting.

---

## Milestone 3 Decisions

### TD-011: Camera Abstraction & Virtual Aspect-Ratio Preservation

**Status**: Approved

**Context**: 2D scene presentation requires camera positioning, panning, zooming, and consistent aspect ratios regardless of window resizing.

**Decision**:
- Define `Camera` interface in `engine-render.camera`.
- Implement `OrthographicCamera2D` encapsulating LibGDX `OrthographicCamera` and `FitViewport`.
- Configure default virtual resolution (640x480) with centered world coordinates.
- Ensure window resizing calls `viewport.update(width, height, false)` to avoid distorting entity geometry or changing entity world coordinates.

**Rationale**:
- Eliminates repetitive camera boilerplate across scenes.
- Decouples camera mathematics from engine lifecycle and rendering implementations.

---

### TD-012: Component-Driven Rendering Pipeline & Entity Transform Binding

**Status**: Approved

**Context**: Scene entities must be drawn based on their own world transforms without tight coupling to a single monolithic rendering loop or ECS.

**Decision**:
- Define `Renderable` component interface supporting primitive (`isPrimitive()`) and sprite (`isSprite()`) drawing.
- Implement `ShapeRendererComponent` (colored 2D rectangle) and `SpriteRendererComponent` (textured quad).
- `SceneRenderer2D` iterates through the active scene, skips inactive entities/components, and applies each entity's `getWorldMatrix()` directly to the rendering pipeline prior to drawing.
- Graphics resource ownership is explicit: `SceneRenderer2D` owns its `ShapeRenderer` and `SpriteBatch`, while textures are owned by external callers/managers unless `ownsTexture` is explicitly configured.

**Rationale**:
- Maintains strict separation between scene state and graphics hardware.
- Prevents rendering passes from mutating scene transforms or triggering unintended component updates.

---

## Milestone 4 Decisions

### TD-013: Programmatic scene2d.ui Skin for Editor Prototype Shell

**Status**: Approved

**Context**: Milestone 4 requires a usable desktop editor shell with Hierarchy, Inspector, Toolbar, Viewport, and Status panels. External skin files or heavy docking libraries (e.g. VisUI, ImGui via LWJGL bindings) introduce external binary dependencies or assets that can complicate CI builds and portability.

**Decision**:
- Implement `EditorSkinFactory` in `editor-desktop` to programmatically assemble a clean dark-themed LibGDX `Skin` using standard 1x1 colored `Pixmap` textures and LibGDX's built-in default `BitmapFont`.
- Construct the shell using LibGDX `scene2d.ui` (`Table`, `ScrollPane`, `Label`, `TextButton`, `TextField`, `CheckBox`).
- Structure the screen with a top toolbar (New, Save, Load, Tool Mode, Reset Camera), left hierarchy panel (tree list with indentation and creation/deletion buttons), center scene viewport (rendering `SceneRenderer2D`), right inspector panel (name, active checkbox, local transform inputs with validation, world position/rotation readouts), and bottom status bar.

**Rationale**:
- Zero additional third-party dependencies required beyond core LibGDX.
- Zero external asset files needed at runtime (no missing texture or font file crashes).
- Lightweight, fully integrated into the existing LibGDX render loop, and responsive to window resizing.

---

### TD-014: EditorState UUID Selection Model & 2D Bounds Viewport Picking

**Status**: Approved

**Context**: The editor must track the active scene, selected entity, active tool mode, and status notifications without mutating runtime engine classes or relying on transient UI list indices. Furthermore, clicking entities in the viewport requires a 2D hit-testing mechanism.

**Decision**:
- Implement `EditorState` in `editor-core` storing the selected entity identity as `UUID selectedEntityId`.
- Provide selection reconciliation: if an entity is removed or a scene replaced, selection safely resets or reconciles.
- Implement `EntityInspectorModel` in `editor-core` to isolate transform mathematics, local Euler angle conversion (quaternion `getYaw()` extraction), validation, and world coordinate computation.
- Implement `ViewportPicker` in `editor-core` that unprojects mouse screen coordinates through `Camera2D` and tests against the local bounding boxes of `ShapeRendererComponent` and `SpriteRendererComponent` transformed by `Transform.getWorldMatrix()`.

**Rationale**:
- `editor-core` remains completely headless and 100% unit-testable without requiring an OpenGL context.
- Runtime modules remain completely unaware of editor selection and tooling state.
- Bounding-box picking provides an immediate, reliable picking workflow without requiring a complex 3D raycaster or physics engine.

---

## Milestone 5 Decisions

### TD-015: Play Mode Scene Cloning via Versioned Serializer and State Isolation

**Status**: Approved

**Context**: In a Unity-inspired workflow, entering Play Mode runs game simulation on a live scene, allowing entities to move, spawn, or be destroyed. Edit Mode must be completely shielded from runtime mutations so that stopping playback cleanly restores the authored scene and all initial transforms.

**Decision**:
- Implement `PlayModeController` in `editor-core` with states `STOPPED`, `PLAYING`, and `PAUSED`.
- Utilize the existing `SceneSerializer` (schemaVersion: 1) to snapshot the authored scene to JSON and deserialize an isolated runtime duplicate.
- Before cloning, run a strict validation pass (`validateComponentSerializability`) over all scene entities. If any attached component lacks a registered `ComponentSerializer`, Play Mode refuses to start and publishes an actionable error message.
- Re-map entity selection by `UUID`: runtime entities retain the authored entity's UUID, ensuring the Inspector and Hierarchy point to the active runtime instance during play.
- Gate all destructive editor actions: entity creation, deletion, scene replacement, and scene saving/loading are rejected while `isPlayModeActive()` is true.
- On `stop()`, dispose the runtime scene via its `dispose()` method, swap the active scene pointer back to the authored scene, and reconcile selection back to the corresponding authored entity.

**Rationale**:
- Avoids Java object serialization or fragile reflection-based deep copying.
- Reuses established, tested serialization infrastructure.
- Zero possibility of runtime mutations bleeding into the authored scene or being accidentally overwritten on disk.

---

## Milestone 6 Decisions

### TD-016: Visual Transform Gizmos with Headless Math and Input Priority

**Status**: Approved

**Context**: In-viewport entity manipulation requires Move, Rotate, and Scale handles drawn at the selected entity's world position. The handles must take input priority over underlying entities, adapt their rendered screen size to camera zoom, cancel safely on interruptions, and compute parent-relative local transforms without cumulative drift.

**Decision**:
- Partition gizmo logic cleanly between `editor-core` and `editor-desktop`:
  - `editor-core`: Houses `GizmoHandle`, immutable `TransformSnapshot` (captured at drag start), `TransformGizmoMath` (pure mathematical hit-testing, world-to-local projections, 2D Z-rotation, scale clamping to `MIN_SCALE = 0.001f`), and `GizmoInteractionController`.
  - `editor-desktop`: Houses `TransformGizmoRenderer` (renders colored lines, arrowheads, rotation ring, and scale boxes via `ShapeRenderer`) and wires input into `ViewportInputProcessor`.
- Input Priority: In `touchDown()`, handle hit-testing executes before entity picking. If a handle is clicked, a drag begins and entity selection is preserved.
- Interruption Safety: If the user changes tool mode, deselects/reselects an entity, or replaces the scene, `GizmoInteractionController` automatically cancels the active drag and restores the pre-drag transform snapshot.
- Scale Adaptation: Handle dimensions are scaled inversely with camera zoom (`effectiveScale = zoom`) to keep visual and click targets consistent on screen across zooming.
- Inspector Synchronisation: Whenever gizmo dragging updates an entity transform, `EditorState.notifyEntityTransformModified()` fires, updating `InspectorPanel` text fields in real time.

**Rationale**:
- Pure mathematical separation in `editor-core` allows 100% deterministic unit testing of gizmo hit-testing, parented transformations, and boundary conditions without requiring an OpenGL context.
- Dragging relative to an initial snapshot eliminates floating-point drift and numerical accumulation errors.

---

## Milestone 7 Decisions

### TD-017: Project-Relative Asset References, Lazy Centralized Loading, and Editor Browser

**Status**: Approved

**Context**: Sprites previously lacked a serialized asset-identity mechanism, or relied on raw in-memory LibGDX `Texture` handles. Native textures cannot be saved into JSON, nor can scene instances independently own shared GPU textures without risking double-disposal when closing scenes or exiting Play Mode.

**Decision**:
- Immutable Persistent Asset Identity: Introduced `AssetReference` in `engine-core`. Paths are normalized to forward slashes, stripped of leading slashes, and strictly validated to prevent directory traversal (`..`) or machine-specific absolute roots (e.g. `C:/`).
- Catalog Discovery Separation: `AssetCatalog` scans the configured project asset root (defaulting to `assets/`) recursively, registering metadata (`AssetMetadata`) for allowlisted extensions (`.png`, `.jpg`, `.jpeg`). Discovery does not allocate or load GPU resources.
- Centralized GPU Lifecycle (`AssetService`): An `AssetService` wraps texture instantiation. Sprites only store `AssetReference`. `SceneRenderer2D` resolves textures through `AssetService` lazily on first render. Repeated queries return the cached texture without redundant GPU decodes.
- Ownership & Single Disposal: Entities and components never dispose shared textures. Scenes only dispose scene-owned components. The application shell explicitly disposes `AssetService` on application shutdown, unloading all cached textures exactly once.
- Serialization Round-Trip: `SpriteRendererComponentSerializer` writes `assetPath`, width, height, centering, and tint. On deserialization, the persistent path is reconstructed into an `AssetReference` without requiring active OpenGL context.
- Editor Browser & Assignment: `AssetBrowserPanel` displays discovered catalog entries and updates `EditorState.selectedAsset`. `InspectorPanel` allows applying the selected asset to a sprite or creating a new `SpriteRendererComponent`.

**Rationale**:
- Eliminates memory leaks, duplicate GPU textures, and crashes when scenes are discarded or swapped during Play/Stop cycles.
- Compatible with headless unit testing because cataloging and reference validation require zero graphics context.

---

## Milestone 8 Decisions

### TD-018: Command-Based Reversible Editing and Bounded History Architecture

**Status**: Approved

**Context**: As the editor expanded with gizmo interactions, component edits, asset assignments, and hierarchy management, mutations occurred directly against scene objects without reversal capability. A generic full-scene snapshot undo mechanism would incur severe serialization overhead and garbage churn on continuous events like gizmo dragging.

**Decision**:
- Command Pattern in `editor-core`: Defined `EditorCommand` (`execute()`, `undo()`, `getDescription()`) and `CommandHistory` with a bounded deque (default 100 entries).
- Fine-Grained Explicit Commands: Implemented discrete commands (`CreateEntityCommand`, `DeleteEntityCommand`, `RenameEntityCommand`, `ChangeEntityActiveCommand`, `ChangeTransformCommand`, `AssignSpriteAssetCommand`, `AddComponentCommand`). Each command stores only the specific delta/snapshot required for restoration.
- Gizmo Drag Aggregation: `GizmoInteractionController` performs interactive updates continuously while dragging, but commits exactly one `ChangeTransformCommand` when `endDrag()` completes. If dragging is cancelled or produces no effective transform delta, no command is pushed to history.
- Scene Reset & Play Mode Isolation:
  - Switching or creating an authored scene invokes `CommandHistory.clear()` to invalidate commands pointing to previous scene entities.
  - Play Mode prevents undo/redo and command execution while simulation is active, ensuring runtime entity mutations never pollute the authored history stack.
- Shared GPU Asset Safety: Undoing or redoing sprite asset assignments modifies only the `AssetReference` stored on the component and clears cached handles for lazy resolution; shared GPU textures owned by `AssetService` are never prematurely disposed or duplicated.

**Rationale**:
- Provides lightweight, instant undo/redo with zero scene-cloning overhead.
- Entire history stack is testable headlessly without UI or OpenGL context.

---

## Milestone 9 Decisions

### TD-019: Polyglot Java/Kotlin Scripting with Shared Runtime Component API and Embedded Compilers

**Status**: Approved

**Context**: Users need to write gameplay and behavior code in either Java or Kotlin, attach scripts to entities, have them compiled and diagnosed within the engine tooling, and execute their lifecycle during Play Mode without destabilizing editor authoring or scene serialization.

**Decision**:
- Shared Runtime Script API in `engine-scene`: Created `ScriptComponent` extending `BaseComponent`, exposing lifecycle hooks (`onCreate()`, `onStart()`, `onUpdate()`, `onFixedUpdate()`, `onEnable()`, `onDisable()`, `onDestroy()`) and entity/scene helper accessors. Both Java and Kotlin scripts inherit directly from `ScriptComponent`.
- Persistent Script Binding (`EntityScriptBindingComponent`): Instead of serializing live script instances or class objects, entities store a binding containing `scriptPath`, `scriptClassName`, `language`, and enabled state. Serialized via `EntityScriptBindingComponentSerializer`.
- Decoupled Compilers in `editor-core`:
  - Java: Utilizes standard `javax.tools.JavaCompiler` with `DiagnosticCollector`.
  - Kotlin: Utilizes `org.jetbrains.kotlin.cli.jvm.K2JVMCompiler` targeting JVM 21 with `-no-stdlib` (stdlib resolved from project classpath).
  - Both compilers emit structured `ScriptDiagnostic` records (kind, file, line, column, message).
- Class Loader Isolation (`ScriptClassLoader`): Scripts are compiled to an isolated build directory (`build/scripts`) and loaded via a custom `URLClassLoader`. Recompilation closes the previous class loader to release class metadata.
- Play Mode Execution Lifecycle: `PlayModeController` invokes `scriptManager.compileAll()` and `scriptManager.initializeSceneScripts()` on the cloned runtime scene. Stopping Play Mode invokes `scriptManager.cleanupSceneScripts()` before runtime scene disposal, preventing memory leaks and orphaned instance hooks.
- Security Model: Scripts execute as in-process trusted JVM code with full access to engine and JVM capabilities; class loading is used for modularity and reloading rather than sandboxing.

**Rationale**:
- Enables zero-overhead polyglot scripting using first-class JVM toolchains.
- Clean separation ensures headless unit testability and preserves scene JSON backwards compatibility.

---

## Product Direction Decisions

### TD-020: Unreal Engine as the Reference Product

**Status**: Approved (project owner, 2026-10-29)

**Context**: The project vision and `docs/design-doc.md.txt` named Unity (2026) as the product the editor's
capabilities and workflows are modelled on. The project owner has chosen Unreal Engine as the reference
instead, to raise the bar for editor quality and toolset breadth.

**Decision**:
- Unreal Engine 5 replaces Unity 2026 as the reference for **editor workflows, UX and toolset breadth**:
  viewport with overlay tools, outliner, details panel, content browser, output log, play-in-editor,
  reusable actor templates (prefabs), visual scripting, material graph editor, sequencer/timeline,
  particle editor, landscape tools, and packaging.
- The reference is **workflow parity for indie-scale projects, not rendering-technology parity**. Features
  that depend on Unreal's renderer (e.g. Nanite virtualized geometry, Lumen global illumination) are out of
  scope. Pursuing them would require a new rendering backend, which design doc §3.2 allows only after a
  separate feasibility study and explicit approval.
- All implementations stay original. No Unreal Engine source code (including source available under the
  Epic Games EULA), content, icons, branding or trademarked feature names (e.g. "Blueprints", "Niagara",
  "Nanite") are copied or used in the product UI; use generic names such as "visual scripting".

**Consequences**:
- TD-019 is unchanged: Java/Kotlin remain the text scripting languages. Visual scripting is an additional,
  not-yet-designed system and will get its own decision record.
- The editor UI overhaul uses Unreal's layout and interaction patterns as its acceptance reference. Renaming
  existing UI terms (Hierarchy, Inspector, GameObject menu) is decided in that milestone, not here.
- Project, module and package names (`GdxUnity`, `io.github.ldev22live.gdxunity`) are unchanged by this
  decision.
- Historical milestone reports that mention Unity remain as written.

---

## Phase 12B Decisions (Editor Shell)

All four approved by the project owner on 2026-10-29. Plan: `docs/milestones/phase-12b-editor-shell.md`.

### TD-021: Scalable Editor Typography via gdx-freetype

**Status**: Approved

**Context**: The editor skin uses LibGDX's built-in `BitmapFont()` (a single small Arial bitmap), which looks
blurry when scaled, has no HiDPI support and limits the editor to ASCII. TD-002 removed `gdx-freetype`
"until a font milestone".

**Decision**: Add the official `com.badlogicgames.gdx:gdx-freetype` extension (at `gdxVersion`) to
`editor-desktop` only, and bundle an open-licence UI font (Inter, SIL Open Font License 1.1) with its licence
file. Fonts are generated at startup per theme/scale and owned by `EditorSkinFactory`. This supersedes
the font part of TD-002. The runtime (`engine-*`) does not gain the dependency.

**Implemented (2026-10-29)**: Inter 4.0 Regular and SemiBold from the official release
(github.com/rsms/inter, v4.0) in `editor-desktop/src/main/resources/fonts/` with `Inter-OFL-LICENSE.txt`.

### TD-022: Native File Dialogs via LWJGL tinyfd

**Status**: Approved

**Context**: File operations save silently to a fixed `scene_backup.json`; there is no Open/Save As.

**Decision**: Use LWJGL's `lwjgl-tinyfd` module (native open/save dialogs on Windows, macOS and Linux) in
`editor-desktop`, pinned to `backendLwjglVersion` in `gradle.properties`: the LWJGL version actually resolved
for `gdx-backend-lwjgl3` (3.3.3). Dialog calls sit behind a small `EditorDialogService` interface so editor
logic stays headless-testable.

**Correction (2026-10-29)**: this record originally said "pinned to `lwjgl3Version`". That property (3.4.1)
is only used by the unbuilt `lwjgl3/` template module; pinning tinyfd to it would have upgraded the whole
LWJGL stack under libGDX through Gradle conflict resolution.

### TD-023: Editor Panel Naming

**Status**: Approved

**Decision**: User-facing panel names follow the Unreal-style layout with generic, non-trademarked terms:
**Outliner** (scene hierarchy), **Details** (inspector), **Content Browser** (assets), **Output Log**
(console). Java class names (`HierarchyPanel`, `InspectorPanel`, `AssetBrowserPanel`) are unchanged to
avoid churn.

### TD-024: Editor Camera Fills the Scene Viewport Panel

**Status**: Approved. Supersedes TD-011 for the editor scene view only.

**Context**: The editor scene camera was an `OrthographicCamera2D` with a fixed 640×480 `FitViewport`,
rendered straight to the window behind the UI panels. The scene was letterboxed to the window instead of
the panel, the panels covered it, and the GL viewport it left behind distorted the UI and broke picking.

**Decision**:
- The editor scene view renders off-screen into a `FrameBuffer` sized to its panel and is drawn as a UI
  widget (`SceneViewportWidget`). Pointer input arrives in panel-local coordinates.
- The editor camera uses a screen-filling viewport (1 world unit = 1 pixel at zoom 1), so it always fills the
  panel and panel-to-world conversion is a pure function (`ViewportCoordinates2D`).
- Games keep TD-011's fixed virtual resolution (`FitViewport`) at runtime.

---

## Phase 12C Decisions (3D Content)

### TD-025: Built-in 3D Primitives and Editor Lighting

**Status**: Proposed (implemented 2026-10-29; awaiting project-owner review)

**Context**: The 3D view showed only flat 2D content; `SceneRenderer3D` submitted no geometry and light
components could not be saved, so scenes with lights could not enter Play mode.

**Decision**:
- `MeshPrimitiveComponent` (box, sphere, plane; size in world units; diffuse color) in `engine-render`, with a
  serializer. `SceneRenderer3D` builds one unit-sized `Model` per shape lazily, shares it, and caches one
  `ModelInstance` per component (scaled by size, tinted per instance). Imported meshes (glTF) remain out of
  scope.
- Directional and point lights get serializers and Details editors.
- In the editor only, `SceneRenderer3D` adds a fallback directional light when the scene has none and uses a
  brighter ambient light, so new primitives are visible before the user adds lights. Runtime defaults are
  unchanged (no fallback light).
- The 2D editor camera gets a ±20000-unit orthographic depth range so 3D primitives also show in the 2D view.
- Create menu entries (Box, Sphere, Plane, Directional Light, Point Light) spawn at the view center as one
  undo step (`CreateEntityCommand` with initial components; redo restores the components that
  `Scene.removeEntity` detaches).

**Limitations**: picking and outlines use axis-aligned bounds (rotation ignored); 2D content draws on top of
3D primitives regardless of depth; the plane is edge-on (unpickable) in the 2D view.

### TD-026: Unreal-Aligned Roadmap After Phase 12

**Status**: Approved (project owner, 2026-10-29)

**Context**: The roadmap continued with in-engine studios (mesh modeling, rigging, vector art, audio). With
Unreal Engine as the reference (TD-020), the larger gap is importing and presenting real 3D content: Unreal
imports meshes rather than modeling them.

**Decision**: Phase 13 becomes the 3D asset pipeline (glTF import, PBR materials, shadows and image-based
lighting, Content Browser thumbnails and drag-and-drop). Then 14 prefabs, 15 3D physics, 16 visual scripting,
17 animation and sequencer, 18 packaging. The vector, audio, mesh modeling and rigging studios are deferred,
not cancelled; their original roadmap sections are kept and labelled.

**Consequences**: Phase 13 starts with a library evaluation (`docs/reports/gdx-gltf-evaluation.md`); adopting a
library still requires approval of the dependency.

---

## Phase 14 Decisions (Prefabs)

### TD-027: Prefab Assets, Instance Links and Comparison-Based Overrides

**Status**: Proposed (implemented in Phase 14; awaiting project owner review)

**Context**: Phase 14 adds reusable entity templates, the equivalent of Unreal's Blueprint actor classes
without the graph. An instance must follow later changes to its prefab while keeping the values the user
changed on that instance, survive save/load, and undo like any other edit.

**Decision**:
- A prefab is a `.prefab` JSON file under the asset root (`prefabs/` by default): the entity tree in the scene
  file format, with prefab-local ids and a `rootId` (`PrefabDocument`). No new dependency.
- Each instance entity carries a `PrefabInstanceComponent` (engine-scene) naming the prefab and its matching
  prefab entity. The root also records which prefab entities existed at the last sync, so entities added to or
  removed from the prefab later are told apart from entities the user deleted in the instance.
- An override is any property whose value differs from the prefab, found by comparing flattened entity state
  (`PrefabState`). Overrides are recomputed while editing and stored on the links when the scene is saved, so a
  scene opened after the prefab changed keeps them. The instance root's name, position and rotation are never
  overrides.
- Apply writes an instance into the prefab and updates the other instances in the scene; Revert, per instance
  or per component, restores prefab values; Unpack removes the links. Each is one `SceneSnapshotCommand`, which
  restores the original entity and component objects so other commands in the history keep valid references.
  Undoing Apply also restores the previous prefab file.

**Alternatives**: recording each edit as an explicit modification (the property-modification lists of other
engines) needs every editing command to report property paths; comparison needs no changes to existing
commands. Replacing whole scenes on undo would invalidate references held by earlier commands.

**Consequences**: nested prefabs and prefab variants are not supported (links inside a new prefab are
replaced). Duplicating an instance produces plain entities. Prefab files changed outside the editor while it
runs are not reloaded until the scene is reopened. Components without a serializer are not part of prefab state.

---

## Phase 15 Decisions (3D Physics)

### TD-028: 3D Physics Behind an Engine Interface; Jolt Physics (jolt-jni) Recommended

**Status**: Proposed. The engine-neutral part is implemented; adopting the library needs project owner approval
after the compatibility spike.

**Context**: Design doc §3.3 requires an evaluated, Java-compatible 3D physics solution integrated behind clear
engine abstractions; Box2D is 2D only. Evaluation: `docs/reports/3d-physics-evaluation.md`.

**Decision**:
- Scenes describe physics with engine-owned components (`RigidBody3DComponent`, box / sphere / capsule colliders,
  16 collision layers with masks) and `PhysicsSystem3D` runs them on a `PhysicsBackend3D`. No library type appears
  outside the backend. 100 scene units = 1 m.
- Recommended backend: Jolt Physics through jolt-jni 6.2.0 (MIT), in a new `engine-physics` module, with
  gdx-bullet as the fallback.

**Alternatives**: gdx-bullet (no new native loader, but Bullet 2.87 from 2017, manual disposal, no enter/exit
events); Libbulletjme (current Bullet, same loader as jolt-jni); ode4j (pure Java, slowest, LGPL listing).

**Consequences**: play mode does not simulate 3D until a backend exists. Adopting jolt-jni adds JSnapLoader and
per-platform native jars, and creates the `engine-physics` module (an architectural change needing approval).

---

## Phase 16 Decisions (Visual Scripting)

### TD-029: Visual Scripting as Interpreted Node Graphs in Two New Modules

**Status**: Approved (project owner, 2026-10-29: "two modules, interpreter first")

**Context**: Phase 16 adds Blueprint-style visual scripting (`docs/project-suggestions.md` §3). TD-019 covers Java and
Kotlin scripts; `AGENTS.md` asks for a decision record before another way of scripting is added.

**Decision**:
- New modules `engine-visualscript` (runtime: graph model, nodes, validator, `.graph` files, interpreter,
  `GraphScriptComponent`) and `editor-visualscript` (headless editing: documents, undoable commands, assets, play
  mode extension). The canvas UI stays in `editor-desktop`.
- Graphs run in an interpreter on the JVM: no new language runtime, no code generation. Pure nodes are computed
  when read; action nodes run along execution wires; a node error stops only its chain and names the node.
- Graphs are assets (`assets/graphs/*.graph`, JSON with typed values) referenced by path from a component, so one
  graph can drive many entities, each with its own variables.
- `PlayModeController` gains an `Extension` hook so modules outside editor-core start and stop with play.

**Alternatives**: compiling graphs to Java bytecode (faster, but harder to debug and to report errors per node; can be
added later behind the same model); putting the code in `engine-scene` / `editor-core` (rejected: keeps those modules
focused and the dependency direction clear).

**Consequences**: two more modules in `settings.gradle`; graph edits have their own undo history, separate from the
scene's; the open graph is saved before play so play always runs what is on screen.

---

## Phase 17 Decisions (Animation)

### TD-030: Adopt gdx-gltf 2.3.0 for glTF Import, PBR Shading and Skinning

**Status**: Approved (project owner, 2026-10-29: spike and adoption approved together with Phase 17)

**Context**: Phase 13 steps A/B and Phase 17's skeletal animation need glTF import. The evaluation
(`docs/reports/gdx-gltf-evaluation.md`) recommended gdx-gltf if a compatibility spike passed.

**Spike result (2026-10-29, scratch project outside the repository)**: gdx-gltf 2.3.0 from JitPack against libGDX
1.14.2 headless: no linkage errors; the minimal triangle loads (1 node, 1 mesh, 3 vertices);
`PBRShaderProvider.createDefaultConfig()` links; a generated two-joint skinned glTF loads with 1 clip (1 s), 2 bones,
and LibGDX's `AnimationController` poses the joint at 45 degrees halfway. Headless runs need a stub GL (libGDX 1.14 has
no mock GL); animation writes to `Node.localTransform`, not `Node.rotation`.

**Decision**: `com.github.mgsx-dev.gdx-gltf:gltf:2.3.0` (version in `gradle.properties` as `gltfVersion`) is an
`implementation` dependency of `engine-render` only. Its types stay inside `ModelAssets` / `SceneRenderer3D`: the
scene stores a `ModelComponent` with an asset path; imported models draw through a second `ModelBatch` with
`PBRShaderProvider` (64 bones). Clips are posed at an exact time by `ModelPoser` (no clock of its own).

**Consequences**: builds depend on JitPack for this artifact (dependency verification not yet enabled). Shadows and
image-based lighting (Phase 13-B) are not set up. The runtime launcher does not register `ModelComponent` yet.

### TD-031: Keyframe Animation and Timeline in Two New Modules

**Status**: Approved (project owner, 2026-10-29: "two new modules")

**Context**: Phase 17 adds a sequencer-style timeline and skeletal animation (Unreal: Sequencer, Animation Blueprint
basics). Like TD-029, keeping it out of `engine-scene` / `editor-core` keeps those modules focused.

**Decision**:
- `engine-animation` (depends on `engine-render`): `AnimationCurve` (stepped / linear / Bezier with automatic tangents),
  `AnimationTrack` (child path + property id), `AnimationClip` (`.anim` JSON, format 1), `AnimatedProperties`
  (transform channels as yaw/pitch/roll, mesh color, light intensities, imported model clip time), `ClipSampler`
  (apply, problems, snapshot/restore), `AnimatorComponent`, and skeleton math (`Skeleton`, `Pose` FK, `TwoBoneIk`,
  `Skeletons.fromModel`).
- `editor-animation`: `TimelineDocument` (own undo history, like graphs), `TimelineCommands`, `AnimationAssets`,
  `TimelineSession` (preview, editor playback, auto-key), `AnimationPlayModeExtension`.
- Skeletal clips from imported files play through `ModelComponent`; a clip can drive them with a "Model Clip Time"
  track instead of re-implementing skeletal sampling.
- Editor preview poses the real scene and restores it; `PlayModeController.Extension.beforePlay` and
  `SceneFileController.addBeforeSaveHook` guarantee a preview pose is never saved or carried into play.

**Alternatives**: editing a separate preview copy of the scene (no restore needed, but every other panel would show
the unposed scene); sampling glTF skeletons in our own code (duplicates gdx-gltf / LibGDX).

**Consequences**: two more modules; clips are per entity tree (child paths by name, so renaming a child breaks its
tracks and is reported); IK is engine math only, not yet a component.

---

## Phase 13-B Decisions (Lighting Quality)

### TD-032: Shadows, Sky Light and Tone Mapping Through gdx-gltf, One PBR Shading Model

**Status**: Approved (project owner, 2026-10-30: "go" on the Phase 13-B plan that proposed it)

**Context**: Phase 13 step B needs directional shadows, image-based lighting (IBL) and tone mapping. Primitives used
LibGDX's default shader and imported models gdx-gltf's PBR shader, so the two looked different under the same light.
Spike against gdx-gltf 2.3.0 (bytecode and shader sources in the jar):
- `DirectionalShadowLight` (one shadow map; `CascadeShadowMap` also exists), `PBRDepthShaderProvider`, `IBLBuilder`
  (procedural sky to irradiance/radiance cubemaps), `brdfLUT.png`, `SceneSkybox` are available.
- There is **no tone mapping or exposure**: `pbr.fs.glsl` sums light in linear space, adds emissive, applies gamma.
- The PBR shader shadows only directional light 0. Point lights fall off as `1 / (1 + d²)`, like LibGDX's default shader.
- Rough reflections need `textureLod`; gdx-gltf enables it only on GL 3 or with `EXT_shader_texture_lod`, and only
  when the specular cubemap is mip-mapped. The editor does not request a GL 3 context.
- LibGDX 1.14.2 frame buffers do not nest (`end()` binds the default frame buffer), and the editor renders the scene
  into its own frame buffer.

**Decision**:
- Primitives and imported models share one PBR batch. Component colors are treated as sRGB and converted to linear
  (gamma 2.2); direct light is multiplied by π to keep LibGDX's former brightness for intensity 1.
- Shadows: one `DirectionalShadowLight` for the first enabled directional light with `castShadows` (new field,
  default true; old scenes load with it on), placed by `ShadowBounds` (texel-snapped box in front of the camera).
  The shadow pass saves and restores the host's frame buffer binding and viewport.
- Sky light: new `SkyLightComponent` (sky color, ground color, intensity); `BakedSkyLightMaps` bakes it with
  `IBLBuilder` only when colors or quality change. Without texture LOD support the radiance map is skipped and
  surfaces reflect the blurred irradiance map (documented degradation, no context change).
- Tone mapping: new `ToneMappingComponent` (exposure, operator NONE / REINHARD / ACES_FILMIC). Exposure scales the
  light colors (no shader uniform needed); the operator is one line inserted by `ToneMappingShader` into gdx-gltf's
  fragment shader after emissive and before gamma, failing loudly if gdx-gltf's source layout changes.
- `RenderQuality3D` presets (shadow map and cubemap sizes); Window menu: Shadows, Lighting Quality.

**Alternatives**: own shadow/IBL/tone mapping shaders (several milestones); a post-processing tone mapping pass
(needs an HDR frame buffer and changes the editor's compositing); requesting a GL 3.2 core context for sharp
reflections (every existing scene2d/ShapeRenderer shader would need a GLSL 1.50 prelude; not verifiable headless);
cascaded shadow maps (left for later; one map is simpler to tune first).

**Consequences**: the look of primitives and imported models changes (linear lighting, gamma). Tone mapping depends on
gdx-gltf's shader text; a gdx-gltf upgrade must re-run `ToneMappingShaderTest`. Exposure does not scale emissive
surfaces; unlit materials are not tone mapped. No sky background is drawn yet. `engine-desktop` does not render 3D.

---

## Phase 18 Decisions (Packaging)

### TD-033: Package the Editor With jpackage; Projects Located Explicitly, Preferences Per User

**Status**: Proposed and applied in 18-A (2026-10-30). The project owner said "continue with the next phase" after
an 18-A outline (package the editor, Windows first); this text itself has not been reviewed. Constraints from the
owner: end-to-end testing runs on a Windows executable, and the engine stays cross-platform.

**Context**: The editor used its working directory as the project (assets, scripts, scene dialogs) and stored
preferences there. `gradlew run` hides this by starting in `assets/`. A packaged app starts in an unrelated, often
read-only folder. In-editor Java scripts need `jdk.compiler`. There was no packaging task.

**Decision**:
- Package the editor with the JDK's own `jpackage` as an app image (launcher + `app/` + bundled runtime), built by
  `:editor-desktop:packageEditor` for the host OS. No Gradle plugin and no new dependency. The bundled runtime is the
  JDK that runs the build (it compiles with `--release 21`); `jpackage`'s default module set includes `jdk.compiler`.
- A project is a folder with a `gdxunity.project` marker file. `EditorProjectLocator` picks `--project <dir>`, else the
  working directory (or its `assets/`), else `<Documents>/GdxUnity/Sample Project`, installed once from the sample
  bundled with the app. Nothing is guessed: a `--project` folder without the marker is an error.
- Editor preferences (theme, layout) and crash logs live in per-user folders from `PlatformDirectories`
  (Windows `%APPDATA%` / `%LOCALAPPDATA%`, macOS `~/Library`, Linux XDG).
- `--smoke-test` renders a number of frames and exits with a status code, so a packaged build can be checked
  automatically on each OS.

**Alternatives**: the `org.beryx.runtime` / `badass-jlink` Gradle plugins (extra plugin for what one `jpackage`
call does); Construo / Packr (libGDX-specific packagers; less control over the runtime modules); a single fat jar
(needs an installed Java and still has the working-directory problem); a trimmed `jlink` module list (smaller, but a
missing module only shows up at run time, e.g. in the Kotlin compiler).

**Consequences**: the app image is about 200 MB. Builds are per OS. Preferences stored in `assets/` by earlier dev runs
are no longer read. Installers, signing and icons are later steps. The repository's `assets/` folder gains the
`gdxunity.project` marker.

---

### TD-034: Game Camera, Screen Text and Sample Games

**Status**: Proposed and applied (2026-10-30). The owner asked for "at least 3 2D and at least 3 3D runnable sample
games ... as starting templates"; the engine additions below were needed for that and have not been reviewed
separately.

**Context**: Play mode drew through the editor cameras, with the grid, gizmos and selection still showing, and there
was no way for a game to show text. Scripts could not reach Box2D bodies or collision events, and there is no 3D
physics simulation (TD-028). Scene files could only be opened through a file dialog.

**Decision**:
- `CameraComponent` (engine-render): orthographic (visible height) or perspective (field of view, clip planes),
  placed by its entity. `GameCameraRig` turns the first enabled camera into a renderer camera. While playing a scene
  with a camera, the scene view draws through it without editor overlays, and `GameViewPlayModeExtension` switches
  to the matching 2D or 3D view and restores the previous view on Stop.
- `TextComponent` (engine-render): screen-space text anchored to the view, drawn by `HudRenderer` with LibGDX's
  built-in font in every view.
- Seven sample scenes in the project's `samples/` folder (four 2D including a Box2D sandbox, three 3D) with Java
  scripts in `scripts/samples/`. Collision is written in the scripts (box overlap) rather than adding physics APIs
  for scripts. File → Open Sample...; launcher options `--scene <file>` and `--play`. In smoke-test mode, dialogs
  are replaced by `NonInteractiveDialogService`, so an unattended run cannot block.
- `EditorApplication.registerComponentSerializers` is the single list of the editor's component serializers,
  shared with `SampleGamesTest`, which plays every sample headlessly.

**Alternatives**: driving the editor cameras from the scene (they would need save/restore, and editor navigation
would fight the game); world-space text (games mostly need a HUD); exposing Box2D bodies and collision events to
scripts first (a larger API decision, left for later); prefab-based spawning (prefab instantiation goes through
undoable commands that are rejected during Play).

**Consequences**: two new component types with serializers, catalog entries ("Game") and Details panels. The bundled
sample project grows by the samples. Samples rely on name conventions and hard-coded tuning constants until scripts
can take parameters. `engine-desktop` still cannot run these scenes (Phase 18-B).

### TD-035: Game Runtime Plays Scene Files Like Editor Play; Scripts Precompiled

**Status**: Proposed and applied (2026-10-30, Phase 18-B, "go" on the proposed scope). Not reviewed separately.
The proposal said the editor's Play setup would move into shared code. That was only partly done. See
*Deviation* below.

**Context**: `engine-desktop` showed a hard-coded 2D demo. It could not load scene files, draw 3D, or run scripts,
graphs or Animators. A game export (18-C) needs a runtime that behaves the same as editor Play.

**Decision**:
- `engine-desktop` is the game runtime. It plays one scene file from a project folder:
  `--scene <file> [--project <folder>] [--scripts <folder or jar>] [--smoke-test[=N]]` (`GameLaunchOptions`).
  Asset paths are read from the project folder. The runtime jar no longer bundles `assets/` as classpath
  resources.
- `GameSession` (headless, unit-tested) starts the same systems as `PlayModeController`, in the same order:
  script binding, `PhysicsSystem2D`, graphs and then Animators. Each frame it steps physics and then the scene,
  with the same 0.1 s cap on the frame step. A scene without an enabled Camera is refused with an explanation.
- Scripts are **precompiled**. The runtime ships no compiler. `GameScripts` loads classes from a folder or jar. The
  folder is `<project>/build/scripts` by default, which is where editor Play compiles to, so pressing Play in the
  editor once is enough. `:engine-desktop:run` compiles `assets/scripts` with a Gradle source set and passes it in
  through the `gdxunity.scripts` property.
- `GameRenderer` draws like the editor's game view (TD-034): the 3D pass with models, lights, shadows, sky light and
  tone mapping, then the 2D pass through the scene camera, then `HudRenderer`. The fallback sun is on when the scene
  has no directional light, as in the editor. A model that fails to load becomes a runtime error, because the
  runtime has no Details panel to show it.
- `GameComponentSerializers` (engine-desktop) is the single list of component serializers. The editor delegates to
  it, so any scene the editor saves can be loaded by the runtime. `PrefabInstanceComponent` is now part of the list.
- `GraphCollisionForwarder` (engine-visualscript) forwards 2D collisions to graphs. The editor extension and the
  runtime share it.
- **Module dependency change:** `editor-desktop` now depends on `engine-desktop` for the shared serializer list.
  `engine-desktop` gains `engine-visualscript` and `engine-animation`.

**Deviation and parity check**: `PlayModeController` (editor-core) was not rebuilt on top of `GameSession`. Sharing
the code properly would need a new module that both `editor-core` and the runtime can use. AGENTS.md §4 says
creating a module needs approval first. `editor-core` cannot depend on `engine-desktop` without pulling LWJGL into a
headless module. Instead, `SampleGamesTest.shouldPlayEverySampleTheSameInTheGameRuntimeAsInEditorPlayMode` plays all
ten samples through both paths with the same key presses for 240 frames. It requires every entity's position,
active state and text to match within 0.001 units. A runtime stepped 1% slower fails it.

**Alternatives**: a new `engine-runtime` module holding `GameSession`, used by `PlayModeController` (cleanest, but
needs a module approval); compiling scripts at game start (ships `jdk.compiler` and source with every game); keeping
the demo launcher and adding 3D to it (no scene files, so it could not test anything).

**Consequences**: two copies of the start-up sequence (`PlayModeController` with its extensions, and
`GameSession`), kept equal by the parity test. A new engine system has to be added to both. Kotlin scripts run in
the runtime only if they were compiled by the editor, because the Gradle run task compiles Java only. The runtime
writes errors to stderr only; a windowed exe without a console needs a crash log (18-C).

---

## Web Runtime Decisions

### TD-036: Web Game Runtime via gdx-teavm; the Editor Stays Desktop-Only

**Status**: Applied (2026-11-02). The user chose "web game player" as the scope for a web version that can be
deployed to a VPS, approved the radiance fix, then asked to "continue with the next phase" (the follow-ups below).
Not reviewed separately.
Report: [web-runtime-spike-report.md](reports/web-runtime-spike-report.md).

**Context**: The user asked for a web version of GdxUnity that can be hosted on a VPS. The editor compiles
Java/Kotlin scripts in-process (TD-019) and reads and writes project files through `java.io`/`java.nio`. Neither
works in a browser. The game runtime (TD-035) only plays scene files, so it is the part that can move to the web.

**Decision (proposed)**:
- New module `engine-web`. It depends on the engine modules and on `engine-desktop` for the shared runtime
  (`GameSession`, `GameRenderer`, `GameComponentSerializers`, `ScriptRegistry`), and is built with the **gdx-teavm** Gradle plugin (`com.github.xpenatan.gdx-teavm`, version
  `gdxTeavmVersion=1.6.2`, which targets libGDX 1.14.2 and TeaVM 0.15). The output is a static site.
- Box2D on the web uses libGDX's pure-Java **`gdx-box2d-gwt:1.14.2`** (same API, `transitive = false`). The JNI
  `gdx-box2d` and the LWJGL backend are excluded from the module's configurations. gdx-teavm has no current
  Box2D extension.
- Deployment is a multi-stage Docker image (JDK 21 build, nginx serve, `engine-web/deploy/`) or a plain copy of
  `build/dist/js/release/webapp`.
- `PhysicsSystem2D` queues collision events in an `ArrayDeque` instead of a `ConcurrentLinkedQueue`. That queue is
  filled and drained on the stepping thread, and TeaVM has no `ConcurrentLinkedQueue`.

**Follow-up** (items 1 to 3 done on 2026-11-02):
1. **Done:** runtime file access goes through `AssetSource` (engine-core), which resolves an `AssetReference` to a
   `FileHandle`: `AssetSource.directory(File)` on desktop, `AssetSource.internal()` (bundled files) on the web.
   `GameSession`, `GameRenderer`, `ModelAssets` and `AssetService` take one. The `File` constructors remain and
   delegate. `AssetCatalog` implements `AssetSource`. `AssetService(AssetSource)` has no catalog (`hasCatalog()`).
   No new module was created: the shared runtime stays in `engine-desktop`, and `engine-web` excludes LWJGL from
   it. `AssetReference` no longer uses `java.nio.file.Path`, which TeaVM cannot iterate, to detect `..` segments.
2. **Done:** `ScriptFactory` (engine-desktop) creates scripts by class name, and `ScriptBinder` binds them.
   `GameScripts` (class loader, desktop) and `ScriptRegistry` (class name → constructor reference, no reflection)
   implement it. `:engine-web:generateScriptRegistry` compiles `assets/scripts` (Java only) and writes
   `GeneratedScripts` from the compiled classes. A script that cannot be constructed that way is left out with a
   warning.
3. ~~Fix the sky-light bake~~ **Done (2026-11-02):** `BakedSkyLightMaps` passed the face size (64/128/256) to
   gdx-gltf's `IBLBuilder.buildRadianceMap(int mipMapLevels)`. It now passes
   `RenderQuality3D.getRadianceMipLevels()` (7/8/9).
4. Reflection policy: not needed so far. Scenes, graphs, clips and the sample glTF load without one. Add one if a
   glTF file with features the sample lacks fails to load on the web.

**Embedding (2026-11-02, the user's target is urconnex.com/games):**
- The runtime is served as static files at `/games/engine/`, and site pages embed `index.html?scene=…` in a
  same-origin iframe.
- The build ships its own `index.html` (no scrollbars), a catalog `assets/games.json`, URL-decoding of `?scene=`,
  and caught browser keys. The container's nginx sends `frame-ancestors 'self'`.
- No data bridge to the host site yet.
- Guide: [engine-web/EMBEDDING.md](../engine-web/EMBEDDING.md).

**Editor on the web:** see TD-037. The editor is streamed, not compiled to the browser, so "the editor stays
desktop-only" above means the editor code is not compiled with TeaVM.

**Alternatives**: the official GWT backend (needs Java 11-level source and no reflection, so it would mean
rewriting Java 21 code such as records and pattern matching); streaming the desktop editor from the VPS
(Xpra/noVNC; single-user, needs software GL on the server); WebAssembly via the same plugin (`wasm {}`, not tried).

**Consequences**: one more module and a third-party backend maintained by one person. Each new runtime API must
avoid `java.io.File`, `java.nio.file`, threads, class loading and classes that TeaVM lacks, or isolate them. Kotlin
scripts do not run on the web. A web build contains the project's scripts and assets, so one build is one game.

### TD-037: The Web Editor Is the Desktop Editor, Streamed From the VPS

**Status**: Proposed (2026-11-02). The user asked for the editor to work on the web. Of the options offered
(stream the real editor; port it to the browser with TeaVM; port scene editing only), the user chose "Stream the
real editor" for "Logged-in UrConnex users". The image and scripts are written; **nothing but the session script
has been run** (no Docker or Linux on the development machine). See [editor-streaming.md](editor-streaming.md).

**Context**: The editor compiles Java and Kotlin scripts in-process (`javax.tools`, the embedded Kotlin compiler,
`ScriptClassLoader`), opens native file dialogs (LWJGL tinyfd), and reads and writes the project through `java.io`
and `java.nio` (24 files, about 23,000 lines across the editor modules). Compiling it with TeaVM would need a new
storage layer, replacements for the dialogs and FreeType, and a server-side compile service, and it would supersede
TD-019. That is weeks of work and a new decision record.

**Decision (proposed)**:
- The unchanged desktop editor runs in a container (`editor-desktop/deploy/stream/`): Xvfb display, openbox,
  software OpenGL (Mesa llvmpipe), x11vnc, websockify and noVNC. The browser shows the noVNC page.
- One container per logged-in user with a volume for `/data` (project, preferences, logs). The sample project is
  installed on first start. The editor needs no code change: Linux natives, XDG folders and `--project` exist.
- The web application never touches Docker. `editor-session.sh start|stop|status|list <user-id>` is the only
  interface, called with `sudo` for exactly that script. It validates the user id, limits sessions
  (`GDXUNITY_MAX_SESSIONS`), and starts containers with no published ports, all capabilities dropped,
  `no-new-privileges`, a non-root user, a read-only root file system, memory, CPU and process limits, on an
  `internal` network with `enable_icc=false`. nginx `auth_request` asks Spring Boot which container belongs to the
  logged-in user and proxies the WebSocket there. Sessions end after 30 idle minutes.
- The pages and endpoints on the UrConnex side are specified in editor-streaming.md as snippets; they live in the
  UrConnex repository, which this repository's agents do not touch (AGENTS.md section 2).

**Alternatives**: a TeaVM port (above); a port limited to scene editing without script compilation (smaller, but
still needs the storage layer and dialogs, and scripts could not be edited); the Xpra HTML5 client or KasmVNC instead
of x11vnc and noVNC (better latency, but not in Ubuntu's repositories in a form that is as simple to install).

**Consequences**:
- Every open editor is an OS process with up to 3 GB of memory and 2 CPUs on the VPS, drawn without a GPU.
  Capacity and the defaults are estimates until measured.
- Users run arbitrary code (TD-019) on the VPS inside a container. Container isolation is the only barrier; stronger
  isolation (gVisor, a VM per user), disk quotas and file upload and download are not done.
- No audio. Clipboard and file transfer between the user's computer and the editor are limited to what noVNC offers.
- `engine-web` (the game player, TD-036) is unaffected and stays the way to publish games.

## Phase 19 Decisions (Sculpting)

### TD-038: Sculpting as Fixed-Topology Vertex Editing in Separate Modules

**Status**: Proposed (2026-11-02). The user asked for "a sculpting feature similar to Mudbox or ZBrush" in the 3D
model editor, then chose: fixed-topology sculpting for the first milestone, new modules `engine-sculpt` and
`editor-sculpt`, desktop first. Plan: [phase-19-sculpting.md](milestones/phase-19-sculpting.md).

**Context**: There is no 3D model editor yet. Meshes are primitives (`ModelBuilder` box and sphere) or imported
glTF models, and picking uses oriented bounding boxes. The deferred Mesh Studio (TD-026) is a low-poly half-edge
polygon modeler, a different tool. Sculpting needs dense meshes (100k+ triangles), brushes, fast spatial queries,
incremental normals and per-stroke undo. The desktop-only constraint matters because the streamed web editor
(TD-037) has no GPU.

**Decision (proposed)**:
- First milestone: **fixed topology**. Triangles are never added or removed while sculpting. The vertex set is
  moved by brushes (draw, inflate, smooth, flatten, pinch, grab) with radius, strength, falloff and symmetry.
  Multi-resolution levels and dynamic topology are out of scope.
- `engine-sculpt` (headless, depends on `engine-core` only): `SculptMesh` (indexed triangles, adjacency, normals),
  `SculptMeshFactory` (cube-sphere, welding), `Subdivision`, `TriangleBvh` (ray picking, sphere queries, partial
  refit), `SculptSurface`, brushes and `SculptStroke` (spaced dabs, symmetry, `StrokeDelta` for undo),
  `SculptMeshIO` (lossless `.sculpt` file). No OpenGL, no gdx-gltf.
- `editor-sculpt` (19-B, same pattern as `editor-animation`): open sculpts and the brush (`SculptSession`), sculpt
  mode in the 3D view, undoable stroke commands, menu and key actions. It depends on `editor-core` and
  `engine-sculpt`, not on a graphics context.
- Display (19-B): `engine-render` gets `DynamicMesh` and `DynamicMeshSource` interfaces and a
  `DynamicMeshComponent` that refers to a mesh by path. The renderer asks the source for the geometry each frame and
  re-uploads only chunks whose revision changed (`engine-sculpt` cuts a mesh into chunks of at most 60,000
  vertices, because LibGDX meshes use 16-bit indices). `engine-render` does not depend on `engine-sculpt`; the editor
  supplies the source. glTF export (19-C) will also go through `engine-render`, where gdx-gltf types stay (TD-030);
  gdx-gltf 2.3.0 contains `net.mgsx.gltf.exporters`.
- Original implementation with generic names (Sculpt Mode, Draw, Smooth, Grab). No Mudbox or ZBrush names, assets or
  code (TD-020 applies to trademarks in general).
- No pen pressure: libGDX/GLFW give no tablet pressure; strength is a setting.

**Alternatives**: multires subdivision levels (Mudbox-like; needs a subdivision scheme and level projection);
dynamic topology (ZBrush Dynamesh/DynTopo style; remeshing and a different data structure; not first-milestone
size); putting the code in `engine-render`/`editor-core` (those modules are already large and the code could not be
tested on its own); building the half-edge Mesh Studio first (delays sculpting).

**Consequences**: two more modules. A sculpt is edited as a `.sculpt` file. **Changed in 19-B:** while editing, a
scene refers to the `.sculpt` file through a `DynamicMeshComponent` (scene files store only its path and colour).
The game runtime and the web player have no source for it yet, so they draw nothing for such components until
19-C adds export to glTF or a loader (the first plan was that scenes would only ever reference the exported
model). Dense meshes
are slow in the streamed web editor (software OpenGL). Brush speed on 480,000 triangles was measured in 19-A (a dab costs 0.2 to 8 ms headless), so the GPU upload of changed vertices is the open performance question for 19-B.

---

## Viewport Decisions

### TD-039: A Multi-Pane 3D Scene View With Axis Views

**Status**: Proposed (2026-11-03). The user asked for customizable viewports "similar to how old Blender worked":
when the editor starts in 3D mode the scene view is divided into four panes (1 world view, 2 X axis, 3 Y axis,
4 Z axis), plus a feature that enables sculpt mode. Interpretation chosen by me, not confirmed: "customizable" means
split, close, retype, resize and maximize panes; the layout is saved per user.

**Context**: The scene view was one widget with one frame buffer and one camera per mode; the grid, gizmo, outline,
collider and skeleton renderers were each tied to one camera.

**Decision (proposed)**:
- `ViewportLayout` (editor-core, headless): a tree of panes and splits. It can split, close (never the last pane),
  change a pane's `ViewKind`, drag a divider (ratio 0.1 to 0.9), maximize and restore, compute pane and divider
  rectangles, and read and write JSON; at most 8 panes. The default is `quad()`.
- `ViewKind`: perspective, or an orthographic view along the X, Y or Z axis (X looks along -X with Y up, Y looks down
  with -Z up so X is across, Z looks along -Z with Y up).
- Axis views use `OrthographicViewCamera` (a wrapper around the existing screen-filling orthographic camera) and
  `OrthographicViewInteraction`: left button gizmo or pick, middle or right button or Alt+left pans, the wheel zooms.
  The gizmo maths already accepts any ray; only the handle size differs (`orthographicScaleFactor`).
- Desktop: `MultiViewportArea` shows one `SceneViewportWidget` (one frame buffer) per pane with a header (view name,
  split side by side, split stacked, maximize, close) and draggable dividers. `ScenePaneFactory` builds the panes; the
  first perspective pane is the editor's existing main camera, so its navigation carries over. Each pane draws its
  own grid, outline, collider, skeleton and gizmo overlays through `SceneEditorWorkspace.renderPane3D`.
- The area replaces the single view only while editing in 3D; 2D and Play keep the single view. The editor now
  **starts in the 3D view** (it started in 2D). The layout is saved to `viewport_layout.json` in the settings folder.
- Sculpting: `SculptViewportInteraction` accepts any camera, so every pane sculpts; the toolbar has a Sculpt button
  (highlighted while on) and F4 toggles sculpt mode.

**Alternatives**: one frame buffer split by scissor rectangles (cheaper, but each pane's widget and input would be
hand-routed); linked quad dividers as in old Blender (the tree lets each row move on its own); a new
`engine`-level orthographic 3D camera class (the 2D camera already does it).

**Consequences**: the scene is drawn once per pane every frame, including the shadow pass, so four panes cost about
four times one, noticeably with a dense sculpt. Dropping a Content Browser asset onto the multi-pane view places it
at the orbit target, not under the mouse. Wheel zoom in an axis view zooms about the last click position, because
the viewport widget forwards no hover movement.

### TD-040: A Music Studio From Two New Audio Modules, Our Own Synth and a Built-In SoundFont Reader

**Status**: Proposed (2026-11-03). The user asked for "a music editor that looks and functions similar to LMMS or
FL Studio", chose the full Phase 11 scope of the studio roadmap, new modules `engine-audio` and `editor-audio`,
and SoundFont support. Creating modules was approved in that answer; the decisions below are mine and unconfirmed.

**Context**: The roadmap (`ALL_IN_ONE_STUDIO_ROADMAP.md` §4.3, Phase 11) plans a software synth, step sequencer,
piano roll, mixer and effects. The engine had no audio code at all.

**Decision (proposed)**:
- `engine-audio` (headless, pure Java plus libGDX's JSON reader, no OpenGL and no sound device): oscillators with
  PolyBLEP, ADSR, biquad filter, `Synthesizer` (16 voices, two oscillators, filter envelope, LFO), `DrumKit` (generated
  kick, snare, clap, hats, tom, crash), `SampleInstrument` with `WavIO`, `SoundFont` + `SoundFontInstrument`,
  `SfxrGenerator` (retro effects; presets, mutate, randomize), `DelayEffect`, `ReverbEffect`, `FilterEffect`,
  `Mixer` (8 inserts and a master with volume, constant-power pan, mute, solo, four effects each, meters and a
  soft limiter), and the song model: `Song` (ticks, 96 per beat, 4/4), `Channel`, `Pattern`, `Note`,
  `PlaylistClip`, `StepGrid`, `Schedule` (the song flattened to timed events with swing), `SongPlayer`
  (sample-accurate events, loop, seek, live preview), `SongExporter` (offline render, tail until silence) and
  `SongIO` (the `.song` JSON file). Sound output is the caller's job.
- `editor-audio` (headless): `MusicDocument` with its own undo history (like a graph or a clip), `MusicCommands`,
  `PianoRollOps` (pure note edits), `GridSnap`, `MusicSession` (open song, selection, save, close, WAV export).
- `editor-desktop`: a **Music** tab beside Viewport and Graph with the channel rack and step sequencer, piano roll,
  playlist, mixer and instrument settings; sound goes to a libGDX `AudioDevice` from a background thread that
  starts on the first Play. Songs are `*.song` files in the project assets.
- **SoundFont**: I first proposed the JDK's built-in Gervill synthesizer (no dependency). Its public API cannot render
  to memory (only `com.sun.media.sound.SoftSynthesizer.openStream` can, and that package is not exported), and it
  could not run in the web player. So `SoundFont` is **our own reader**: 16 bit sample data, presets, instrument
  zones, key and velocity ranges, root key, tuning, pan, attenuation, volume envelope, loops. **No filters, no
  modulators, no 24 bit samples, no SF3.** No new dependency was added.
- Output is passed through a soft limiter (linear to 0.8, bent towards 1.0), because several instruments added
  past full scale and the sample demo song peaked at 1.66 before it was added. Meters show the true level.

**Alternatives**: a third-party SoundFont library (needs an evaluation and approval, and probably cannot run in the
web); MIDI through `javax.sound.midi` (no offline rendering); one `editor-studios` module as drawn in the roadmap
(chosen against by the user in favour of the `engine-x`/`editor-x` pair used for sculpting).

**Consequences**: no VST plug-ins, no recording, no automation lanes, no audio clips on the playlist, 4/4 only, and
ogg is neither read nor written (WAV only). Songs do not yet play in the game runtime or the web player: there is
no `AudioSourceComponent` (Phase 20-C). Editing while playing releases the notes that are sounding.

### TD-041: A JavaScript Web Player and Editor on Babylon.js (`engine-babylon`)

**Status**: Proposed (2026-11-04). The user created the `engine-babylon` folder and asked for "a web clone of the
gdx-unity using babylonjs and perhaps threejs". Asked for the scope, they chose **both a player and an editor, player
first**, and **Babylon.js only** (three.js is not used). The decisions below are mine and unconfirmed.

**Context**: TD-036 put a game player on the web by compiling the Java runtime with gdx-teavm and said the editor stays
desktop-only; TD-037 proposed streaming the desktop editor instead. Neither gives a web editor built from web
technology. This record adds one, as a separate implementation, without removing either of those.

**Decision (proposed)**:
- `engine-babylon` is a Vite + TypeScript project (npm, not Gradle; it is not in `settings.gradle`). Runtime
  dependencies: `@babylonjs/core` (rendering) and `planck` (a JavaScript port of Box2D, for the 2D physics). Dev
  tools: Vite, TypeScript, Vitest.
- **The scene file is the contract.** The player reads the desktop editor's `.json` scene files unchanged
  (`schemaVersion` 1); `npm run sync-samples` copies `assets/samples/*.json` so both programs play the same files.
  Babylon runs right-handed (`scene.useRightHandedSystem`), so positions and rotations need no conversion; a test
  checks that a camera with an identity rotation looks along -Z, as in the desktop engine.
- **Scripts are ported by hand.** The scenes name Java classes (`samples.collector.CollectorGame`), which cannot run
  in a browser outside the teavm build. A TypeScript `ScriptComponent` mirrors the Java one and a `ScriptRegistry`
  maps the Java class name to the port. Scripts without a port are listed by the player, never skipped quietly.
- **Stages**: (1) player: scenes, lights, shadows, tone mapping, game cameras, text, 2D physics, scripts, (done); (2) port the samples' scripts (10 of 14 done) and add
  3D physics; (3) the editor:
  outline, details, viewport with gizmos, undo, scene load and save in the same file format, play mode (first version
  built: one page, `editor.html`, sharing the player's scene builder; its undo keeps whole-scene snapshots).

**Consequences**: two web players now exist (`engine-web` runs all 14 samples with their scripts today;
`engine-babylon` runs 10 of them). Every new component or scene field has to be implemented twice, in Java
and in TypeScript, until one of the two is dropped. Importing from Babylon's main entry point gives a 6.5 MB bundle
(1.4 MB gzipped); deep imports would shrink it. TD-036's "the editor stays desktop-only" no longer holds once stage 3 is
built.

### TD-042: The Product Is Renamed From GdxUnity to Forgeroom

**Status**: Approved by the user (2026-11-04: "can you update both the desktop version and the web version to be
forgeroom"). The name was suggested by me and accepted. **Trademark and domain availability were not checked.**

**Context**: "GdxUnity" borrows the name of another engine and says only that libGDX is used. The product is now a
game editor with sculpting, a music studio, visual scripting and animation on desktop and web.

**Decision**: The product name is **Forgeroom**. Renamed: window titles and the About box, crash reports, error
messages, the AI agent's prompt, the scene file dialog, the editor's brand label, the package name and zip file
(`ForgeroomEditor-<version>-<os>-<arch>.zip`, application name `ForgeroomEditor`), the web player and editor pages,
`engine-babylon/package.json`, and the current docs (README, AGENTS.md, INDEX, design brief, deployment and streaming
guides, `engine-web`, workflows).

**Deliberately not renamed**:
- The Java package `io.github.ldev22live.gdxunity` and the module names: a package rename touches every source file
  and the user's many uncommitted changes. Say so if it is wanted.
- The repository folder `gdx-unity`.
- The per-user data folders `GdxUnity` (settings, logs, and `~/Documents/GdxUnity` projects, `PlatformDirectories`):
  renaming them without a migration would hide existing projects and settings. A migration is a separate decision.
- Historical reports and milestone documents, which record what was true when written.
- The unbuilt `core/` and `lwjgl3/` template leftovers.

**Consequences**: Old zip names, bookmarks and shortcuts that use `GdxUnityEditor` stop matching. The scene and song
file formats are unchanged, so existing files open as before.

---

## Future Decisions

The following decisions will be made in future milestones:

- Physics engine selection: evaluated in TD-028 (Proposed)

## Decision Review Process

1. Document context and problem
2. Identify alternatives
3. Evaluate consequences of each alternative
4. Make decision with rationale
5. Record in this document and create ADR
6. Review periodically for relevance
