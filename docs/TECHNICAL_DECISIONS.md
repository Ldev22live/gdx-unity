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

**Status**: **Approved** (2026-11-09: "approve TD-025"). Earlier status: Proposed (implemented 2026-10-29; awaiting project-owner review)

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

**Status**: **Approved** (2026-11-09: "approve TD-027"). Earlier status: Proposed (implemented in Phase 14; awaiting project owner review)

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

**Status**: Approved for adoption (project owner, 2026-11-06: "I approve Jolt"); the Jolt backend is implemented (Phase 15-E, below)
and the compatibility spike passed.

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

**Implemented (Phase 15-E, 2026-11-06)**, after the owner's "I approve Jolt" and "proceed with the next milestone":
- New module `engine-physics` (depends on `engine-scene`) with `JoltPhysicsBackend3D` and `JoltNatives`; `editor-core` (Play) and
  `engine-desktop` (`GameSession`) depend on it. `joltJniVersion=6.2.0` is in `gradle.properties`. The module was created on
  the strength of that instruction; AGENTS.md §4 asks for approval of new modules, so tell me if that reading was wrong.
- **Differs from the evaluation:** no snaploader. jolt-jni's `NativeLibraryLoader.loadLibrary(path)` is `System.load`, so
  `JoltNatives` copies the library from the `ReleaseSp` jar to `<tmp>/forgeroom-jolt-<hash>/` and loads it from there. One
  dependency fewer. The three platform artifacts (Windows64, Linux64, MacOSX_ARM64) are bundled; the packaged editor keeps
  only the host platform's.
- **Layers:** Jolt filters by object-layer pair, the engine's masks belong to each body. Object layer is `2 * layer + moving`
  (so static pairs are skipped in the broad phase) and the exact rule runs in the contact-validate callback.
- **Contacts** are per body pair (Jolt reports per sub-shape pair). **Bodies never sleep**: Jolt reports a sleeping body's
  contacts as ended, which scripts would read as leaving the ground. The cost is simulating resting bodies.
- **Single-threaded** (0 helper threads): contact callbacks then arrive on the stepping thread. Raise only after profiling.
- 3D physics is created only for scenes with an active 3D collider (`PhysicsSystem3D.isUsedBy`), so 2D games never load the library.
- jolt-jni's `ObjectVsBroadPhaseLayerFilterTable(bpInterface, a, filter, b)` takes object-layer count then broad-phase count,
  the reverse of Jolt's C++ constructor: with the C++ order only the first two object layers filtered correctly (found by a test).

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

**Status**: **Approved** (2026-11-09: "approve TD-033"). Earlier status: Proposed and applied in 18-A (2026-10-30). The project owner said "continue with the next phase" after
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

**Status**: **Approved** (2026-11-09: "approve TD-034"). Earlier status: Proposed and applied (2026-10-30). The owner asked for "at least 3 2D and at least 3 3D runnable sample
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

**Status**: **Approved** (2026-11-09: "approve TD-035"). Earlier status: Proposed and applied (2026-10-30, Phase 18-B, "go" on the proposed scope). Not reviewed separately.
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

**Status**: **Approved** (2026-11-09: "approve TD-036"). Earlier status: Applied (2026-11-02). The user chose "web game player" as the scope for a web version that can be
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

**Note (2026-11-09, on approval)**: there are two web paths side by side. This record is the *Java* one (`engine-web`, the shared runtime through gdx-teavm). TD-041 is the *JavaScript* one (`engine-babylon`: player, editor, Jolt physics, music); both are approved.

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

**Status**: **Approved** (2026-11-09: "approve TD-038"). Earlier status: Proposed (2026-11-02). The user asked for "a sculpting feature similar to Mudbox or ZBrush" in the 3D
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

**Status**: **Approved** (2026-11-09: "approve TD-039"). Earlier status: Proposed (2026-11-03). The user asked for customizable viewports "similar to how old Blender worked":
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

**Status**: **Approved** (2026-11-09: "approve TD-040"). Earlier status: Proposed (2026-11-03). The user asked for "a music editor that looks and functions similar to LMMS or
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

**Status**: **Approved** (2026-11-09: "approve TD-041"). Earlier status: Proposed (2026-11-04). The user created the `engine-babylon` folder and asked for "a web clone of the
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

**Editor layout (same request, user chose "desktop follows web")**: the desktop editor's layout and dark colours now
follow the web editor's: one top bar with the brand, the tabs and the menus, then a tools row with Play, then OUTLINE
and DETAILS panels. The desktop kept its menus, content browser, output log, timeline and AI assistant, which the web
editor does not have. Five shared sample songs were added to `assets/music` for both editors.

**Web glTF loading (landscape sample)**: the web player now loads Model components with `@babylonjs/loaders` (the
official Babylon.js glTF loader, same version as the core package), added as a dependency of `engine-babylon` only. It
was not asked for by name; it is the smallest way to show the desktop's glTF models in the web editor.

**Consequences**: Old zip names, bookmarks and shortcuts that use `GdxUnityEditor` stop matching. The scene and song
file formats are unchanged, so existing files open as before.

### TD-043: Sound Effects in Scenes Through an Audio Source Component (Phase 20-C, First Part)

**Status**: **Approved** (2026-11-09: "approve TD-043"). Earlier status: Proposed (2026-11-04). The user asked "on the samples can you add sound effects"; asked how, they chose the
`AudioSourceComponent` (the Phase 20-C item TD-040 deferred), for all scripted gameplay samples, on the desktop and the
web. The decisions below are mine and unconfirmed.

**Context**: TD-040 built a synth and sfxr generator but said scenes had no way to play sound. Samples could not have
sound effects without a component the scene file can carry.

**Decision (proposed)**:
- `engine-scene` gets `AudioSourceComponent` (type id `AudioSource`): a **cue** (the name scripts trigger it by), a
  **sound** (an sfxr preset: Coin, Laser, Explosion, Powerup, Hurt, Jump, Blip), volume 0 to 1, pitch 0.25 to 4 and
  play on start. Several per entity are allowed. It holds only data and a `SoundSink` the host binds while the scene
  plays, so `engine-scene` still knows nothing about audio output. Scripts call
  `AudioSourceComponent.play(getScene(), "jump")`; with no source answering the cue, or outside Play, it plays nothing
  and returns 0.
- `engine-audio` gets `SfxPlayer`, a headless mixer of up to 16 overlapping effects (each generated once, replayed with
  linear interpolation at the requested pitch, a lock-free queue between the game thread and the output thread).
- `engine-desktop` now depends on `engine-audio` and gets `GameAudio` (checks the scene's sounds, binds the sources) and
  `SfxAudioOutput` (a libGDX `AudioDevice` thread). `GameSession` binds sound after scripts, physics, graphs and
  animators; the editor's Play does the same through `AudioPlayModeExtension`. A scene whose Audio Source names an
  unknown sound **does not start**, with the sound's name. A missing sound device is logged and play goes on. A scene
  with no Audio Source opens no device.
- The web player (`engine-babylon`) has a TypeScript port of the sfxr generator for the same seven presets, checked
  against numbers printed by the Java generator (frame counts exact, loudness within 0.1 %), `SfxPlayer` over the
  browser's `AudioContext` (one `AudioBuffer` per sound, a gain and a playback rate per play), and
  `GameScene.playCue`. There an unknown sound is reported on the status line and that source stays silent (it does not
  stop Play), which differs from the desktop.
- 13 samples got sound: 14 Java script files and 9 web script files call cues, and each scene has a "Sound Effects" entity whose
  sources set the sound, volume and pitch per cue. Tests check that the cues a script plays and the cues its scene
  answers are the same set, and that the web and desktop scripts play the same cues.

**Alternatives**: playing from scripts through a Java helper (no scene format change, but nothing to tune in the scene
and no web version); recorded `.wav` files (needs assets and an import path; sfxr needs none); an
`AudioListenerComponent` and positional sound (not needed for effects in these games).

**Consequences**: no songs in scenes yet, no `AudioListenerComponent`, no 3D positioning, no `.wav` or `.ogg` clips, no
looping; the landscape, skeleton and physics-sandbox samples have no sound because nothing in them happens by script.
`racing`, `arena-fps`, `puzzle` and `fighter` have sound on the desktop only (their scripts are not ported to the
web). The Java and web script sets were patched separately, so a new cue must be added to both and to the scene.
**Nothing was listened to**: the desktop device and the browser's `AudioContext` were not heard (see the feature
matrix).

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


### TD-044: Animations and Visual Scripting Tabs, Humanoid Clips and Generated Scripts (Phase 16/17 follow-up)

**Status**: Approved (2026-11-08: "approve TD-044-TD-061"). Proposed 2026-11-05. The user asked for two more tabs on the editor UI, "animations" with pre-built
humanoid skeleton animations (walking, running, jumping, falling) and "visual scripting" similar to Blueprints, with
Java/Kotlin for the desktop and TypeScript for the web. They chose both editors, and graphs that run **and** generate
code. The decisions below are mine and unconfirmed.

**Decision (proposed)**:
- **Clips**: `assets/animations/humanoid/` holds `Idle`, `Walk` (a copy of the skeleton sample's `WalkCycle`), `Run`,
  `Jump` and `Fall`, keyed to the bone names of the skeleton samples (`Hips`, `Spine`, `Chest`, `Neck`, `Head`, arms,
  forearms, thighs, shins). Idle, Walk, Run and Fall loop; Jump is one hop that starts and ends standing. Sample scene
  `humanoid-animations-3d.json` shows all five side by side.
- **Desktop tabs**: the center tabs are Viewport, Scene, **Animations**, **Visual Scripting** (was Graph), Music. The Timeline
  moved from the bottom panel into Animations, beside a clip library (`AnimationsPanel`, headless `ClipLibrary`): a name button
  previews the clip on the humanoid in the scene, Use makes the selected entity play it (one undo step).
- **Web tabs**: the web editor gets the same two tabs. Animations has its own small 3D view of the humanoid, play, scrub, loop and speed,
  the clip's tracks, and "Use on selected entity". Visual Scripting is a node canvas (palette, drag nodes, drag wires, typed-in
  values, variables, undo) on the **same `.graph` files and node ids** as the desktop; the web now has a graph interpreter,
  validator and serializer (`src/visualscript`), and the web player plays Animator and Graph Script components (it ignored both before).
- **Generated code**: `GraphCodeGenerator` (engine-visualscript) writes a `ScriptComponent` in Java or Kotlin to
  `scripts/generated/` (buttons on the desktop Visual Scripting tab); `generateTypeScript` writes a web `ScriptComponent`
  class (shown in the web tab, to copy or download). Delay nodes become timer fields; Collision and Trigger events become methods
  the game must call; a graph whose execution wires loop is refused.

**Consequences**: the web player no longer reports Animator as unsupported. Collision and Trigger events never fire on the web
(no collision reports to graphs there). Destroy Entity removes the entity from the web scene.

---

### TD-045: Scripts Move Between the Desktop and the Web Editor Through an Embedded Graph

**Status**: Approved (project owner, 2026-11-06). The user asked that the Visual Scripting tabs of both editors "allow conversion between
TypeScript and Kotlin/Java so that users can import/export source files between the two". Of four options (generate in every
language plus embedded graph; generate only; parse generated code; general transpiler) they chose the first, and approved
the record.

**Context**: TD-044 gave each editor a one-way generator (desktop: Java, Kotlin; web: TypeScript). The Java and TypeScript
script APIs differ (`ScriptComponent` on `Entity` against `ScriptComponent` on `GameEntity`), so translating hand-written
code needs a parser and an API map for each direction and is unreliable. Both editors already share the `.graph` file.

**Decision**:
- A generated script ends with the graph as base64 of its `.graph` JSON in `//` comment lines between
  `// forgeroom-graph:begin v1` and `// forgeroom-graph:end` (`GraphSourceEmbed` in engine-visualscript, `sourceEmbed.ts` on
  the web). All three languages accept it. **Importing** reads that block and opens the graph; the importing editor can then
  generate any language from it. That is the conversion: TypeScript to Java/Kotlin is import in the desktop editor then Generate.
- Each editor generates all three languages: the web gained `codegenJvm.ts` (a port of `GraphCodeGenerator`), the desktop
  gained `GraphTypeScriptGenerator` (a port of `codegen.ts`), so a script can be exported without the other editor.
- Parity is tested, not assumed: `test/golden/<Name>.{ts,java,kt}` (web-written) and `test/golden/desktop/` (desktop-written)
  must hold the same code for the sample graphs, and each side reads the graph out of the other's files.
  `FORGEROOM_UPDATE_GOLDEN=1` rewrites a side's files.
- The desktop stores a float as the widened double (0.8f as 0.800000011920929); the web import turns a number that is exactly
  a 32-bit float back into its shortest text.

**Consequences**: only scripts generated by Forgeroom can be imported, and only the embedded graph is read: **edits made to the
code after generating are not converted and are lost on import**; deleting the block makes the file unimportable. A change to
either generator must be made in both (the golden test fails otherwise). Desktop TypeScript goes to `scripts/web`, which the
script compiler ignores; the user copies it into the web project.

---

### TD-046: Game Export From the Packaged Editor, Start Scene in the Project File, Crash Log for Games (Phase 18-C)

**Status**: Approved (2026-11-08: "approve TD-044-TD-061"). Proposed 2026-11-06. The user said "you can proceed with Phase 18-C" (milestone text in
`docs/milestones/phase-18-packaging.md`: "package a project's scenes and assets with the runtime as a standalone game").
The choices below are mine and unconfirmed.

**Context**: TD-033 packages the editor; TD-035 made `engine-desktop` play scene files but left packaging it, choosing
the start scene from the project file and a crash log for it to 18-C. A second jpackage app image would double the build
and the download, and a game built from the editor must not need a developer toolchain.

**Decision**:
- **One app image, two launchers.** `:editor-desktop:packageEditor` adds a second launcher, `ForgeroomGame`
  (`jpackage --add-launcher`), that starts `DesktopLauncher` on the same jars and Java runtime. The launcher sets
  `-Dgdxunity.project=$APPDIR/game`.
- **Export copies that image.** `GameExporter` (editor-core, headless) writes `<Name>.exe`, `runtime/`, `app/*.jar` and
  `app/<Name>.cfg` (the launcher finds its config by its own name) and the project under `app/game/`. Left out: the editor
  launcher and config, `editor-*` jars, the sample project, the Kotlin compiler jars, script sources, other build output,
  hidden folders and the editor's own files (layout, theme, backup, asset list). Compiled scripts (`build/scripts`) go in.
  It refuses a missing start scene, uncompiled scripts, an output folder that is not empty or lies inside the project, and
  never overwrites.
- **Start scene in the project file**: `gdxunity.project` gains `startScene` (and uses `name`); `--scene` overrides it, and
  `--project` overrides the `gdxunity.project` system property. Exported games write both keys.
- **`GameExportCommand`** compiles the scripts first (so a game never ships stale classes) and then exports. It serves
  File > Export Game... (the open, saved scene is the start scene) and the command line
  `ForgeroomEditor.exe --project <dir> [--scene <start scene>] --export-game <new folder>`, which opens no window and
  exits 0 or 1.
- **Crash log and message box for games** (`GameFailureReporter`): a failure is written to `crash-<time>.log` in the player's
  own folder (`%LOCALAPPDATA%\<Game>\logs`, `~/Library/Logs/<Game>`, `$XDG_STATE_HOME/<Game>/logs`) and shown in a native
  message box; smoke tests skip the box. `engine-desktop` gains `lwjgl-tinyfd` (the version the editor already uses).

**Alternatives**: a separate `packageGame` Gradle task that jpackages a project (needs a JDK and Gradle on the developer's
machine, and cannot run from the editor); bundling the game runtime as a second copy inside the editor package (the
download grows by the Java runtime again); an installer or a single-file exe (later, with signing).

**Consequences**: export works from the **packaged Windows editor only**. A development run (`:editor-desktop:run`) has no
game launcher and says so; macOS and Linux app images have another layout and were not built or tried. The game is about
180 MB (the Java runtime and the engine's jars), and carries the Java runtime's compiler module because it is the editor's.
`gdxunity.project` is read by two classes that agree on the keys by convention (`GameProjectFile` in the runtime,
`GameExporter` in the editor), because `editor-core` must not depend on `engine-desktop`; a test exports and reads back.
The `ForgeroomGame` launcher is a Windows window application: it shows no console, so errors reach the player only through
the crash log and message box.

---

### TD-047: Application Icon, Installer, Code Signing and Linux/macOS Game Export (Phase 18 follow-ups)

**Status**: Approved (2026-11-08: "approve TD-044-TD-061"). Proposed 2026-11-06. The user said "start phase 18" and, asked which follow-ups, chose all four: icon,
Windows installer, macOS/Linux export and code signing. The choices below are mine and unconfirmed.

**Decision**:
- **Icon.** One Forgeroom icon, drawn by `tools/make_icons.py` (Pillow) into `editor-desktop/packaging/icons/`
  (`.ico`, `.png`, `.icns`) and as `forgeroom-icon-{32,64,128,256}.png` window icons in the resources of `editor-desktop` and
  `engine-desktop`. jpackage puts it on the editor launcher and (through the launcher properties file) on `ForgeroomGame`.
  The editor window and a game's window use the bundled PNGs. A project can name its own window icon: `windowIcon=<png in
  the project>` in `gdxunity.project`; export validates it (PNG, inside the project, exists) and the game uses it.
  **A game's `.exe` file icon is always the generic game icon**: changing it needs the PE resources of the exe rewritten,
  which jpackage only does at package time.
- **Installer.** `:editor-desktop:installerEditor` runs jpackage on the app image: `.msi` on Windows (per-user, Start menu entry,
  shortcut, folder chooser, a fixed upgrade UUID so a newer installer upgrades), `.dmg` on macOS, `.deb` on Linux. On Windows
  JDK 21's jpackage needs **WiX Toolset 3.x**; the task checks for it and stops with instructions (`-PwixHome` points at it).
  Nothing is downloaded or installed by the build.
- **Signing.** `-PsignCertificate=<file.pfx>` plus the password in the environment variable `FORGEROOM_SIGN_PASSWORD` (not a Gradle
  property, so it stays out of build logs) makes the build sign `ForgeroomEditor.exe` and `ForgeroomGame.exe` with signtool
  before zipping, and the `.msi` after it; `-PsignTimestampUrl` adds a timestamp. Without `-PsignCertificate` nothing is signed. A game
  export copies and renames the signed game launcher; the signature stays valid because it does not cover the file name.
- **Linux and macOS game export.** `GameExporter` recognises the app image layout from its files (`Layout`: Windows, Linux
  `bin/` + `lib/`, macOS `.app` bundle) and writes the same kind of layout, renaming the launcher, its config and its icon,
  leaving out everything named after the editor, and on macOS rewriting `Info.plist` (executable, names, bundle identifier,
  icon file). `locateTemplate()` finds the image root from the launcher path on each OS.

**Alternatives**: signing the exported game's launcher at export time (needs the player's tools or the author's certificate on
the author's machine; a later step); patching the exe's icon resource at export (a PE writer of our own, or rcedit, a new native
tool); an installer for the exported game (the zip or folder is enough for now); a custom launcher instead of jpackage's.

**Consequences / limits**:
- **Verified on Windows only**: the icon in both exes (extracted from the built files), the signing task (with a throwaway
  self-signed certificate: the signature is present, survives copying and renaming, and the chain stops at an untrusted root,
  as it must), the failure messages of the installer and signing tasks, and an export + run of a game with its own window icon.
- **Not verified**: the `.msi` itself (WiX is not installed on this machine), the signing of an `.msi`, a certificate from a real
  authority (SmartScreen, timestamping), the `.dmg` and `.deb` installers, and the Linux and macOS export (tested against fake
  app images written from jpackage's documented layout; the Linux launcher's execute bit is tested only on systems with POSIX
  permissions, which Windows is not). A real image on those systems may differ; the first export there is the test.
- macOS: the exported bundle is unsigned and not notarized; Gatekeeper blocks it until it is.
- The window icon does nothing on macOS (the window system has no per-window icons; the bundle's `.icns` is used).

---

### TD-048: Script Access to 3D Physics: Contacts, Raycasts, Forces and Velocities (Phase 15-F)

**Status**: Approved (2026-11-07: "approve TD-048"). The user did not answer the three open questions, so the proposals
below stand as the decision. It amends TD-028 (backend interface) and TD-029 (graph nodes).

**Context** (checked in the source): Java/Kotlin scripts (`ScriptComponent`) have no physics access at all, 2D or 3D. They get
lifecycle callbacks only, and `Scene` has no hook for services. Only graphs get collision events
(`GraphCollisionForwarder`, 2D only). `PhysicsSystem3D` already has `raycast` and contact listeners, but nothing hands them to
scripts. `PhysicsBackend3D` has no force, impulse or velocity calls. The `fps` sample raycasts with its own code (`Arena.raycast`).

**Proposed decision**:
- **Access.** `Scene` gets a nullable `getPhysics3D()` / `setPhysics3D(...)` (a small engine-neutral interface in
  `engine-scene`, implemented by `PhysicsSystem3D`); `PlayModeController` and `GameSession` set it after creating the system and
  clear it on dispose. `ScriptComponent` gets `getPhysics3D()`, which returns null for scenes without 3D colliders (they never
  load the native library, as in 15-E).
- **Contact callbacks.** `ScriptComponent` gets no-op `onCollisionEnter3D(Entity other)`, `onCollisionExit3D`,
  `onTriggerEnter3D`, `onTriggerExit3D`. A `ScriptContactForwarder` registered as a `PhysicsSystem3D.ContactListener` calls them on
  both entities' scripts. It lives in `engine-scene`, so Play and the game runtime share it (same pattern as 15-E's parity test).
- **Backend additions** (`PhysicsBackend3D`, scene units converted to meters in `PhysicsSystem3D`): `addForce`, `addTorque`,
  `addImpulse`, `setLinearVelocity`, `getLinearVelocity`, `setAngularVelocity`, `getAngularVelocity`, `overlapSphere(center, radius,
  layerMask)`. Forces and torques are **accumulated by `PhysicsSystem3D` and applied in every fixed sub-step of the frame**,
  because Jolt clears them after each step and a force applied once would act for one sub-step of up to five. Impulses and
  velocity changes apply once.
- **Rules (fail loudly, AGENTS §6).** Forces, impulses and velocity writes on an entity without a body, or whose body is static or
  kinematic, throw `IllegalStateException` naming the entity and its motion type. NaN/infinite vectors throw
  `IllegalArgumentException`. Raycast keeps its existing argument checks.
- **Graph nodes.** New nodes: Raycast 3D (hit, entity, point, normal, distance), Add Force, Add Impulse, Set Velocity, Get Velocity.
  The existing Event Collision / Trigger Enter nodes also fire for 3D contacts (a `GraphContactForwarder` next to the 2D one). Both
  code generators (Java, TypeScript) and the `.graph` validator learn the nodes; golden-file parity tests cover them.
- **Docs and sample.** `physics-sandbox-3d` gets a script that launches a ball on a key press and reports hits; Javadoc on the
  new API; `FEATURE_TEST_MATRIX.md` rows.

**Alternatives**: a `PhysicsBody3D` handle object instead of methods on the system (nicer API, more surface to test; can come
later on top of this); applying forces once instead of per sub-step (simple, but makes the strength depend on the frame time);
adding 2D script callbacks in the same step (a separate gap, listed below); a Lua/other runtime (excluded by TD-019).

**Open questions for the owner**:
1. **Web editor.** The web players do not simulate 3D physics, so the new graph nodes could not run there. Proposal: the
   TypeScript generator refuses a graph using them with a clear message instead of emitting code that would do nothing. The other
   choice is to hold the nodes back until the web side has 3D physics.
2. **AI agent tools.** Give agents the same abilities (`AgentTool`s for raycast and impulses in a running scene)? Proposed: not
   in 15-F.
3. **2D script callbacks.** Scripts get no 2D collision callbacks either. Proposed: separate small step, not part of 15-F.

**Consequences / limits (expected)**: contacts arrive after the step, so a callback that applies an impulse acts in the next
step; no character controller, joints or mesh colliders (still later steps); the sensor body of a dynamic entity still trails by one
frame (15-E limit); behaviour on Linux and macOS stays unverified like the rest of the Jolt backend.

**Implementation notes (2026-11-07, where the build differs from the text above):**
- `Scene` holds the `PhysicsSystem3D` itself (`getPhysics3D()`), not a separate interface: both live in `engine-scene`, so an
  interface would only have added a layer. `PhysicsSystem3D`'s constructor attaches itself to the scene (a second system on the
  same scene throws) and registers the script contact forwarder; `dispose()` detaches. So `PlayModeController` and `GameSession` needed
  no wiring for scripts, and cannot disagree. The graph forwarder (`GraphContactForwarder`) is registered by `GameSession` and
  `GraphPlayModeExtension`, like the 2D one.
- Scenes attach scripts through `EntityScriptBindingComponent`, whose instance is not itself a component of the entity. The forwarder
  therefore calls both kinds (found by a runtime test on `GameSession`; the first version missed bound scripts).
- The Raycast 3D node looks at every layer (no layer-mask pin yet); a ray that starts inside a solid does not hit it.
- Torques are in kilogram square units per second squared (force times distance, both in scene units); angular velocity is in radians
  per second and is not scaled.
- Web: the TypeScript generator refuses these nodes with a message naming the node; the web editor's validator reports them as
  "needs 3D physics ... runs only in the desktop editor and game" instead of "unknown node type". The AI agent tools and 2D
  script callbacks are unchanged (open questions 2 and 3: proposals stand).

### TD-049: 3D Character Controller (Phase 15-G)

**Status**: Approved (2026-11-07: "approve TD-049"). The owner did not answer the three open questions, so the proposals stand: rotation is left to scripts, no moving platforms or character-vs-character, existing samples are not migrated. Amends TD-028 (backend interface) and TD-048 (script API).

**Context** (checked in the source): the 3D samples (`hopper-3d`, `arena-fps-3d`, `collector-3d`, `runner-3d`) move their player with
their own script code and no physics, and `physics-sandbox-3d` has no walkable player. A dynamic capsule rigid body is a poor
player: it slides on slopes, catches on edges, tips over unless rotation is locked (there is no rotation lock on
`RigidBody3DComponent`), and bounces off steps. jolt-jni 6.2.0 (already bundled) ships `CharacterVirtual`, Jolt's kinematic
controller built for this: it handles slopes, steps, stairs, moving platforms and pushing dynamic bodies.

**Proposed decision**:
- **Component.** `CharacterController3DComponent` (engine-scene, with serializer and clone coverage): capsule `radius` and `height`
  (scene units), `slopeLimitDegrees` (default 45), `stepHeight` (default 30 = 0.3 m), `mass` (for pushing bodies, default 70 kg),
  `gravityScale` (default 1), `layer`/`mask` like the colliders. An entity with this component gets a character instead of a rigid
  body; combining it with a `RigidBody3DComponent` is refused with a message naming the entity.
- **Backend.** `PhysicsBackend3D` gains `createCharacter`, `setCharacterVelocity`, `getCharacterPosition`, `isCharacterGrounded`,
  `getGroundVelocity`, `removeCharacter`. `JoltPhysicsBackend3D` implements them with `CharacterVirtual.extendedUpdate` (stick to floor,
  walk stairs) inside the existing fixed step. `engine-physics` stays the only module that sees Jolt types.
- **System.** `PhysicsSystem3D` owns the characters: each fixed sub-step it adds gravity to the vertical speed (not when grounded),
  moves the character by the *desired horizontal velocity* a script set, and writes the resulting position back to the entity's
  transform (the character is the authority, like dynamic bodies). Scene units in and out (100 per meter).
- **Script API** on `ScriptComponent` (scene units, fail loudly with the entity's name as in TD-048): `moveCharacter3D(Vector3 velocity)`
  (horizontal desired velocity, kept until changed), `jumpCharacter3D(float speed)` (only while grounded; returns whether it jumped),
  `isGrounded3D()`, `getCharacterVelocity3D()`. Contacts with other bodies arrive through the existing `onCollisionEnter3D` family.
- **Graph nodes** (desktop only, like the TD-048 nodes): Move Character, Jump Character, Is Grounded; Java/Kotlin generation and
  parity tests. The TypeScript generator refuses them with a message.
- **Editor.** Add Component > Physics 3D > Character Controller 3D; Details editors; a capsule wireframe in the 3D view (same
  shape rule as the capsule collider).
- **Sample.** `physics-sandbox-3d` gets a walkable character (a script that reads the keys: WASD, Space to jump) with a ramp, steps
  and a moving platform is **not** included (see limits).
- **Tests.** Component serializer round trip; fake-backend tests for the system (gravity, jump only when grounded, transform
  sync, refusal with a rigid body, removal); real Jolt tests (stands on a floor, stops at a wall, climbs a step lower than
  `stepHeight` and not one higher, refuses a slope over the limit, pushes a dynamic crate, jump height matches the physics);
  `GameSession` runtime test; graph code parity; `SampleGamesTest` for the sample.

**Acceptance** (to be marked met / not met in the report): a character on a floor reports grounded and does not sink or drift
over 10 simulated seconds; the jump apex matches v²/2g within 5%; the same walk distance at 30 and 60 fps; a step under the limit
is climbed and one above it blocks; a slope above the limit is not climbed; a crate is pushed; the component is refused beside a
Rigid Body 3D; Play and the game runtime behave the same (shared code path); all other tests unchanged.

**Alternatives**: a dynamic capsule with locked rotation (simple, but needs rotation locks and gives the slope/step problems above);
Jolt's `Character` (rigid-body based, simpler but weaker on stairs and slopes); a hand-written kinematic sweep in `PhysicsSystem3D`
(more code, would re-implement what Jolt does); first-person camera and third-person camera rigs (separate step, after this one).

**Open questions for the owner**:
1. **Rotation.** Should the controller rotate the entity (turn toward the move direction) or leave rotation entirely to scripts?
   Proposed: leave it to scripts (position only).
2. **Moving platforms and character-vs-character.** Proposed: not in 15-G; the character stands on a moving body but only receives its
   velocity if `getGroundVelocity` is cheap to expose, otherwise later.
3. **Existing samples.** Proposed: do not migrate `hopper-3d`, `arena-fps-3d` and the others to the controller in this step (they work
   and are covered by tests); only the sandbox gets a walking player.

**Consequences / limits (expected)**: the web players do not simulate it (as for all 3D physics); one character per entity, capsule
shape only; the character does not trigger sensors until Jolt reports them for characters (to be checked in the spike and reported); no
slope sliding animation or crouch; the same Windows-only native verification as 15-E.

**Implementation notes (2026-11-07, where the build differs from the text above):**
- The sample is a new scene, `character-playground-3d`, not an addition to `physics-sandbox-3d`: the sandbox camera is fixed, a
  walker needs a chase camera, and Space is already the sandbox's kick key. It has the steps (4 x 20 units), a 25 degree ramp, a 60
  degree ramp, a 60 high block to jump onto, a 150 high block, three crates, and the scripts `WalkerPlayer` and `WalkerCamera`.
- `getGroundVelocity` was not built (open question 2 stands: no moving platforms). Standing on a moving body does not carry the
  character.
- Jump Character has no "jumped" output: it does nothing in the air, and Is Grounded answers whether a jump can start. The script
  API `jumpCharacter3D` does return whether it jumped.
- A Character Controller 3D together with a Rigid Body 3D or any enabled 3D collider is refused twice: the Add Component menu
  refuses it with a message naming the entity (`ComponentCatalog.canAdd`), and `PhysicsSystem3D` throws `IllegalStateException`
  naming the entity when a scene file has the combination. Trigger colliders are included in "any collider".
- Layer and collision mask decide what other bodies and rays see (the character has an inner body, so a ball lands on it and a ray
  hits it); the character itself walks into every solid body whatever its mask says. Jolt's `CharacterVirtual` push strength is left at
  its default (100 N); the sample crates weigh 4 kg and the Jolt test crate 1 kg.
- Trigger zones: a character entering and leaving a trigger collider gives one `TRIGGER_ENTER` and one `TRIGGER_EXIT`
  (`CharacterController3DOnJoltTest`). Landing on a body gives one `COLLISION_ENTER`, and walking along it gives none. Contacts are
  taken from the character's own contact list (within 5 cm) and from its inner body, merged per pair, reported under the
  character's handle (a negative number, never a body id).
- The system owns the character's position: setting the entity's transform from a script has no effect after the next physics
  update. A character starts where its entity is when it is created. **Added later the same day (owner asked for a teleport/respawn
  call):** `PhysicsSystem3D.teleportCharacter(entity, position)` / `ScriptComponent.teleportCharacter3D(position)` puts the capsule
  somewhere at once (not swept), clears its walking and fall speed and moves the entity immediately; `PhysicsBackend3D.setCharacterPosition`
  is the backend call. It extends the API without changing a decision; there is no graph node for it yet. Rotation and scale of the entity are
  never read or written.
- Gravity is applied as the average speed over a step, so a jump follows the exact parabola whatever the frame rate (apex of a
  4 m/s jump within 5% of v squared over 2g at 60 fps).

---

### TD-050: Camera Rigs (follow, third-person orbit, first-person)

**Status**: Approved (2026-11-07: "approve TD-050"). The open questions were not answered, so the proposals stand. Raised by the owner's list "teleport/respawn call, camera rigs, joints, mesh colliders". The `character-playground-3d` sample has to ship its own chase-camera script (`WalkerCamera`) and the other 3D samples each have one too; this makes the common ones components.

**Proposal**
- One component, `CameraRigComponent`, on the same entity as a `CameraComponent`. A `mode` picks the behaviour:
  - `FOLLOW`: stays at a fixed offset (scene units, in world space) from a target entity and looks at it; `followRate` smooths it (0 = rigid).
  - `ORBIT`: third person; yaw and pitch (degrees) with limits, distance with min/max, looks at the target plus a pivot height. A script or the player sets yaw/pitch/distance through the rig; the rig does not read input itself.
  - `FIRST_PERSON`: sits at the target plus an eye offset and copies the target's yaw; pitch comes from the script.
- Optional collision: `ORBIT` can pull in along the ray from pivot to camera using the 3D physics raycast (TD-048), keeping a margin; off by default.
- The target is an entity reference by id (resolved when play starts; a missing target is an error naming the rig's entity, not a silent no-op).
- Runs in the scene update after physics, so it follows the pose a character or body just reached. No new module: component in `engine-render` next to `CameraComponent`, system in `engine-scene`'s update order or a small `CameraRigSystem` in `engine-render`, to be settled in step 1.
- Script API: `ScriptComponent.getCameraRig()` with `setYaw`, `setPitch`, `setDistance`, `setTarget`. Graph nodes: none in the first step (they would be desktop-only again, as the web player has its own runtime).
- Editor: Add Component entry, Details editor, a wireframe line from camera to target in the 3D view.
- Web (Babylon) player: not part of this step; a scene with a rig loads there with a reported "not supported" warning.

**Open questions**
1. Entity reference by id: is there an existing component field type for "pick an entity" in Details, or must one be built? (Checked in step 1.)
2. Should the existing samples drop their camera scripts for rigs? Proposed: no; only `character-playground-3d` migrates, to prove the rig.
3. Camera shake and cut transitions are out of scope.

**Acceptance (planned)**: follow reaches the offset within 1% after N seconds at 30 and 60 fps equally; orbit keeps distance and respects pitch limits; occlusion pulls the camera in front of a wall and releases; first person matches the target yaw; serializer round trip; Play and game runtime agree; sample migrated; missing target reports its entity.

**Implementation notes (2026-11-07, where the build differs from the text above):**
- The target is found by **entity name**, not by id (open question 1): Details has no entity picker, names are editable text, scripts already use `findEntityByName`, and a prefab instance gets new ids. Two entities with the same name: the first found wins.
- The component and its serializer are in `engine-render` (`CameraRigComponent`, `CameraRigComponentSerializer`); it runs as the component's own `onUpdate`, which the scene calls after physics in both editor Play and the game runtime. No separate system.
- Script access is `getComponent(CameraRigComponent.class)` and then `rotate`, `setYaw`, `setPitch`, `setDistance`: `ScriptComponent` is in `engine-scene` and cannot see `engine-render`, so a `getCameraRig()` there was not possible.
- `followRate` smooths `FOLLOW` only (frame-rate independent: `1 - exp(-rate * dt)`); `ORBIT` and `FIRST_PERSON` are rigid. The first frame snaps to the wanted pose.
- Pitch is held to a configurable range inside -89..89 (default -80..60), distance inside min/max (default 100..1200); yaw wraps. Yaw 0 looks along -Z, positive pitch looks up.
- `FIRST_PERSON` yaw is the target's facing plus the rig's yaw (default 0), so a script that turns the target turns the view.
- Occlusion starts its ray at the pivot, usually inside the target's own capsule, which Jolt reports at distance 0; the ray steps on in 10 unit steps up to 16 times. A target wider than about 160 units across the ray therefore cannot be left and the camera is not pulled in (no fallback pose is invented). Occlusion uses the rig's own `occlusionMask`.
- The 3D view draws a yellow line from a selected rig to its look-at point. `character-playground-3d` now uses a rig (FOLLOW, offset 0/420/560, rate 6, look-at height 0) and `WalkerCamera.java` was deleted. The web (Babylon) player has no rig: the component would be ignored there, and that has not been tested or given a warning yet.

---

### TD-051: Joints for 3D Physics (fixed, hinge, slider, distance, ball)

**Status**: Approved (2026-11-07: "aprove TD-051", as typed). Implemented the same day with the deviations listed in the implementation notes below. Amends TD-028 (backend interface).

**Proposal**
- One component, `Joint3DComponent`, on the entity that is attached, with `type` (`FIXED`, `HINGE`, `SLIDER`, `DISTANCE`, `BALL`), `connectedEntity` (id; empty = attached to the world), local anchor point(s) and axes in scene units/degrees of this entity, `collideConnected` (default false), optional limits (angle for hinge, distance for slider/distance), optional motor (target velocity or angle with max force), and `breakForce` (0 = unbreakable).
- Both entities need a `RigidBody3DComponent`; a joint to a character, a static body on both sides, or a missing body is refused with a message naming the entity.
- Backend: `PhysicsBackend3D.addJoint(JointDescriptor3D)` / `removeJoint` / `setJointMotor`, implemented in `engine-physics` with Jolt's `FixedConstraint`, `HingeConstraint`, `SliderConstraint`, `DistanceConstraint`, `PointConstraint`. Descriptor uses meters and radians like the other descriptors.
- Lifetime: created after both bodies exist, removed when either entity is deactivated or disposed; a broken joint is removed and reported to the scene as an event (`JointBreak`), delivered to scripts as `onJointBreak3D`.
- Script API: `setJointMotor3D`, `isJointBroken3D`. Graph nodes: none first.
- Editor: Add Component entry, Details editor, debug lines for anchors/axes/limits in the 3D view.
- Sample: `physics-joints-3d` (a door, a pendulum, a chain, a slider platform).

**Open questions**
1. Per-entity single joint (above, simplest) or `allowMultiple` to build chains on one entity? Proposed: single per entity; chains are one joint per link entity.
2. Ragdolls (skeleton + joints) are out of scope.
3. Is breaking-force worth the event plumbing in this step? Proposed yes, small.

**Acceptance (planned)**: a hinge door swings and stops at its limits; a pendulum period within 5% of the formula; a fixed joint holds two bodies together under load; a slider motor moves a body to its target; a joint breaks above its force; refusal cases; removal on deactivate; serializer; Play and runtime agree; real Jolt on Windows.

**Implementation notes (2026-11-07)**
- Built as proposed: `Joint3DComponent` (types `FIXED`, `HINGE`, `SLIDER`, `DISTANCE`, `BALL`; anchor, axis, `collideConnected`, limits, motor) and `Joint3DComponentSerializer` in `engine-scene`; `JointDescriptor3D` and the backend calls `addJoint`, `removeJoint`, `setJointMotor` and `getJointPosition` on `PhysicsBackend3D`; Jolt's `FixedConstraint`, `HingeConstraint`, `SliderConstraint`, `DistanceConstraint` and `PointConstraint` in `JoltPhysicsBackend3D`; lifetime in `PhysicsSystem3D`; Add Component > Physics 3D > Joint 3D, a Details editor and lines in the 3D view (anchor cross, axis, hinge limit arc, slider travel, link to the connected entity).
- **Not built: breaking force, `JointBreak` and `onJointBreak3D`, `isJointBroken3D`** (open question 3, which proposed them). jolt-jni 6.2.0 does not expose the force a constraint carries (no `getTotalLambda*`), so a break force cannot be measured; a stand-in such as "stretch" would not mean what a force means and was not invented. A script breaks a joint by disabling its `Joint3DComponent`; the joint is then removed at the next physics update. Breaking force needs a newer jolt-jni or a native addition, which needs its own decision.
- **Connected entity by name, not id** (the proposal said id): Details has no entity picker and prefab instances get new ids; the same choice as TD-050. The first entity with that name is used. Empty means the world.
- **A solid collider is enough on the connected side** (the proposal said both need a `RigidBody3DComponent`): a door frame is a plain static collider. The rule that matters is kept: at least one of the two must be a dynamic body. A character, a trigger-only entity, a missing connected entity or a motor on a type that has none is refused with a message naming the entity, when the joint is created (the next physics update) and, for characters, already in Add Component.
- **`DISTANCE` has a second anchor**, `connectedAnchor` (local to the connected entity, or a world point when joined to the world), because a rod or rope needs two ends; the other types use one point. Without limits a distance joint keeps the distance the anchors have at the start; with limits it is a rope or a rod with play.
- **Rest pose.** Joints are made in world space when physics starts: the pose both bodies have then is angle 0 / position 0 and the reference for limits (which must include 0). A joint is made again, at the then current pose, when an entity that was inactive is active again.
- **Sign convention.** The connected body (or the world) is Jolt's body 1, so a positive hinge angle or slider position means this entity moved that way. Found while testing: with the entity as body 1 a slider driven towards +200 reported -200.
- Script API: `ScriptComponent.setJointMotor3D(mode, target)` and `getJointPosition3D()` (degrees or scene units); the same two on `PhysicsSystem3D`. No graph nodes yet. The motor's force limit is the component's `motorMaxForce` (newton meters for a hinge, newtons for a slider).
- `collideConnected` off is done in the contact validation callback (a set of joined body pairs), not with Jolt group filters, because bodies already exist when the joint does.
- `PhysicsBackend3D` got the four joint calls as `default` methods that throw `UnsupportedOperationException`, so the test fakes and any other backend without joints still compile and fail loudly if asked for one.
- Measured, not promised: a fixed joint holding a 20 kg load 1.3 m to the side sags about 10 units (cm) under Jolt's solver at 60 Hz; the pendulum period is within 5% of the formula (test tolerance). The web (Babylon) player has no joints: it ignores the component, with no warning yet.
- Sample `physics-joints-3d`: a door driven open by its hinge motor, a pendulum, a falling chain of ball joints, a slider platform with a crate. It runs by itself, no script.

---

### TD-052: Mesh Colliders for 3D Physics (convex hull and triangle mesh)

**Status**: Approved (2026-11-07: "TD-052 approved", as typed). Implemented the same day with the deviations listed in the implementation notes below. Amends TD-028 (backend interface, shape descriptor).

**Proposal**
- Two components next to the box, sphere and capsule colliders: `ConvexHullCollider3DComponent` and `MeshCollider3DComponent`. Both take their points from the entity's `ModelComponent` (glTF) or a `MeshPrimitiveComponent`; the source is read when physics starts, in the entity's world scale.
- Convex hull: any motion type, hull reduced to at most 64 points (Jolt's limit applies), a small convex radius. Triangle mesh: static bodies only (Jolt does not support dynamic triangle meshes); a mesh collider on a dynamic or kinematic body is refused naming the entity.
- `ShapeDescriptor3D` gains vertex/index data in meters; the engine-neutral side extracts it (`engine-render` knows models, `engine-scene` does not), so the extraction sits behind a small interface, `ColliderGeometrySource`, implemented in `engine-render` and registered with the scene like other services. Reasonable alternative: put the components in `engine-render`; settled in step 1.
- Characters, rays and overlaps work against mesh colliders without extra code (they use the same shapes).
- Scale: non-uniform scale on a mesh collider entity is baked in at start; changing scale during play does nothing (stated in the Javadoc).
- Editor: Add Component entries, Details editors, wireframe of the hull / triangles in the 3D view (triangle count limited for drawing).
- Sample: `physics-mesh-3d` (a ramp-and-bowl static mesh from a glTF, boxes and balls falling into it, a character walking on it).

**Open questions**
1. Models with several meshes: one collider per mesh node (separate shapes in one compound) or require one collider per entity? Proposed: compound of all nodes of the model, static mesh only.
2. Skinned/animated meshes: bind pose only; animation does not move the collider.
3. Large meshes: warn above 100,000 triangles in the Output Log; no hard limit.

**Acceptance (planned)**: a ball rolls into a bowl-shaped mesh and stays; a dynamic box rests on a convex hull of a cube within tolerance; refusal of mesh on dynamic body; characters walk on a mesh floor and ramp; ray hits a mesh at the right point and normal; serializer; Play and runtime agree; real Jolt on Windows.


**Implementation notes (2026-11-07)**
- Built as proposed: `ConvexHullCollider3DComponent` and `MeshCollider3DComponent` (both with offset and trigger, like the other colliders) and their serializers in `engine-scene`; `ShapeDescriptor3D` got the kinds `HULL` and `MESH` with `points` and `indices` (meters, entity scale applied); Jolt's `ConvexHullShapeSettings` (convex radius 0.5 cm) and `MeshShapeSettings` in `JoltPhysicsBackend3D`; Add Component entries, Details editors and wireframes in the 3D view (hull: a cross at each hull point; mesh: the triangle edges, every n-th triangle above 3,000); sample `physics-mesh-3d`.
- **Where the code sits (the open "reasonable alternative" of the proposal):** the components stay in `engine-scene`, next to the other colliders. `ColliderGeometrySource` (an interface in `engine-scene`, with `MeshGeometry3D` as its result) is implemented by `ColliderGeometry` in `engine-render`, and `SceneRenderer3D` registers it with the scene (`Scene.setColliderGeometry`) in `setActiveScene` and `setModelAssets`. So a scene shown by a renderer (editor Play, the game runtime) can build mesh colliders; a scene without a renderer (a plain headless test) refuses them with a message unless the test registers a source.
- **Open question 1 (models with several meshes):** every mesh node of the model, in its rest pose, is merged into one mesh (one triangle mesh, or one hull around all points). Static bodies only for the triangle mesh. **Question 2:** rest pose only; animation does not move the collider. **Question 3:** a mesh above 100,000 triangles adds a warning to `PhysicsSystem3D.getWarnings()`; **the warning is not shown in the Output Log yet** (nothing reads that list).
- **Convex hull reduction:** duplicate points are merged; above `PhysicsSystem3D.MAX_HULL_POINTS` (64) points the 64 that spread out the most are kept (farthest-point sampling). A reduced hull can be slightly smaller than the mesh. Primitives are meshes too: a sphere primitive is a 16 by 12 mesh, a plane has one face (up).
- **Refusals**, with the entity named: a Mesh Collider 3D on a dynamic or kinematic body (in `PhysicsSystem3D` when physics starts, and already in Add Component when the body exists), no `Model` or `Mesh Primitive` on the entity, a model that failed to load, a mesh without triangles, and a scene that cannot read meshes. A body changed to dynamic after the mesh collider was added is refused when Play starts, not at the moment of the change.
- **Scale and offset:** read when physics starts, per axis (the absolute value, so a mirrored model is not mirrored); changing scale during Play does nothing. The collider's offset works as for the other colliders.
- **Not in the sample:** the character walking on the mesh (the proposal listed it). A character needs a script and keyboard input, which the other sample scripts provide only for their own games; a real-Jolt test walks a character over a mesh floor and up a ramp instead.
- Not tested: a mesh collider with the trigger flag set (Jolt supports static mesh sensors; nothing here exercises it), skinned or animated models, and textured models (the sample terrain has none).
- The web (Babylon) player has no mesh colliders: it ignores the components, with no warning yet.

---

### TD-053: Character Creator from MakeHuman's CC0 Data (morph targets, base mesh, game rig)

**Status**: Approved with changes (2026-11-07). Owner's words: "put the code as selectable options in the anmation edit so that animation templates can be applied to the models I approve creation of the new modules yes the editor should work without makehuman installed" (as typed), after "yes please proceed" to copying the CC0 data. Implemented the same day with the changes below.

**Context (measured on the owner's machine, read-only, 2026-11-07).** MakeHuman 1.x community build in `AppData\Local\makehuman-community\makehuman`. Its `license.txt` says: source code AGPL-3; bundled assets (base mesh, targets, rigs, weights, clothes, poses) **CC0 1.0**; exported output is the user's own. So the data may be used freely, and **no MakeHuman code is copied or ported**: everything below is written fresh in Java from the data formats.
- `data/3dobjs/base.npz`: the base mesh, 19,158 vertices, 18,486 quads (a mix of body, eyes, teeth and helper geometry in 139 face groups), 21,334 UVs.
- `data/targets.npz` (numpy archive, 48 MB unpacked): 1,280 morph targets, each a sparse pair of arrays (vertex indices, offset vectors), license note CC0. Groups: 348 `macrodetails` (gender x age x muscle x weight x ethnicity anchors), 228 breast, 140 armslegs, 102 expression, 68 eyes, 62 asym, 44 ears, 44 mouth, 42 nose, 40 measure, 27 head, 22 bodyshapes, 18 torso, 16 cheek/neck, 15 chin, 14 hip, 8 forehead/stomach and a few small ones.
- `data/rigs/game_engine.mhskel` (JSON, 53 bones, CC0) plus `default_weights.mhw` (skin weights for the default rig; the game rig's bones list the default bones whose weights they merge in `weights_reference`).
- MakeHuman's own exporters are BVH, Collada, FBX, OBJ, Ogre and STL: no glTF, so its export cannot be imported as is.

**Proposal**
- **Data conversion, once, by script.** `tools/convert_makehuman_assets.py <MakeHuman folder>` (Python 3 with numpy, like the other tools) writes a compact, versioned set into `assets/characters/makehuman/`: the base mesh without helper geometry (positions, triangles, UVs, group names), a **chosen subset** of the targets as sparse binary (int32 index + float32 vector), the game rig (bone names, parents, head/tail positions as the vertices they follow, so bones move with the body), the skin weights merged onto the game rig's 53 bones, and `LICENSE-ASSETS.txt` / `CREDITS.md` naming MakeHuman, CC0 and the source files. The tool, not the installed files, is what the repo keeps; the converted files are committed only on approval.
- **Target subset (open question 1):** the macro anchors, face (head, forehead, eyes, nose, mouth, ears, cheek, chin, neck), body (torso, hip, arms and legs, stomach, buttocks, pelvis, breast) and bodyshapes; **left out:** genitals, expression, asym and measure (about 300 targets, estimated 10 MB or less; measured during step 1). Nothing is converted that is not listed in `CREDITS.md`.
- **Morph math, original code.** Macro sliders gender, age, muscle and weight blend the macro anchors multilinearly with piecewise-linear age nodes (baby, child, young, old; the node ages are measured from the anchor names, not copied); other sliders add one target each, signed (decrease/increase pairs become one slider from -1 to 1). Pure Java in `engine-character` (new module, no GL), unit-tested.
- **Output is a normal model asset, not a new runtime component.** An editor tool bakes the creator's state into `assets/characters/<name>.gltf` (skinned mesh, 53-bone skeleton, a base color) plus `<name>.character.json` (slider values, so it can be re-opened and changed). The game runtime and the web player then need nothing new: it is a glTF with a skeleton, like the humanoid samples (TD-030, TD-031, TD-044). This needs a small glTF writer in Java (`GltfWriter`, JSON with embedded buffers, as the sample generators already do in Python).
- **Editor:** a Character Creator panel (new module `editor-character`, same pattern as `editor-animation`): sliders grouped as Body, Head, Face; a live preview in the 3D view using the existing `DynamicMesh` path; undoable slider edits (`EditorCommand`); Save writes the glTF and the json and refreshes the Content Browser; "Place in Scene" adds an entity with a `ModelComponent`.
- **Animation:** the existing humanoid clips (TD-044) are retargeted to the game rig by a bone-name map; the sample `character-creator-3d` shows two differently built characters playing Idle and Walk.
- **Out of scope for this decision (own decisions later):** clothes, hair and proxies; facial expressions and visemes; runtime (in-game) morphing; skin textures beyond a flat color; ethnicity sliders (anchors converted, sliders can be added); importing from MakeHuman's own file formats.

**Open questions**
1. Target subset as listed; exclude genitals and breast variants too? Proposed: keep breast sliders (body shape), exclude genitals.
2. Base mesh: drop the helper geometry and the teeth/tongue/eyebrow groups? Proposed: body, eyes and teeth kept as separate mesh parts of one skin; eyebrows/eyelashes/tongue left out.
3. Two new modules (`engine-character`, `editor-character`) as above, or put the code in `engine-animation` / `editor-animation`? Proposed: new modules (AGENTS.md says creating a module needs approval).
4. Commit the converted data (about 10 MB) or only the tool and let each user run it against their own MakeHuman? Proposed: commit it, with credits, so the editor works without MakeHuman installed.

**Acceptance (planned)**: converter is deterministic and lists every file it read; credits and license files present; morph math: zero sliders give exactly the base mesh, one slider at 1 moves only the vertices its target names by the target's offsets, macro weights sum to 1; skin weights per vertex sum to 1 with at most 4 bones; baked glTF loads through `ModelAssets` with 53 bones and plays the Walk clip without exploding; two characters of different height are different heights by the measured amount; slider edits undo/redo; serializer round trip of the json; the sample runs in Play and in the game runtime; a person looks at the preview and the baked characters (otherwise Manually Not Verified).

**Risks**: retargeting the existing clips depends on their bone names (checked in step 1 before anything is built); skin weights merged from the default rig may need smoothing; 19k-vertex meshes re-morphed live need the update to stay under a frame (measured, with a throttled drag preview if not); the numpy `.npz` files must be read in the converter only, never at runtime.

**Implementation notes (2026-11-07)**
- **Owner's answers and changes.** The creator is part of the **Animations tab** (a Character button beside Clip Library), not a separate panel, and the **animation templates are options in it**: each humanoid clip (Idle, Walk, Run, Jump, Fall) has a [x] toggle, and the selected ones are baked into the character's model as glTF animations, so a character plays them through `ModelComponent` like any model. Two new modules approved and created: `engine-character` and `editor-character`. The converted data is committed so the editor works without MakeHuman (questions 3 and 4). Questions 1 and 2 were not answered: the proposals were built, except that **eyes, teeth, tongue and eyebrows are not included at all** because MakeHuman's base mesh has only the body (`body` group); those are separate proxy assets that this decision does not convert.
- **Data measured and converted** by `tools/convert_makehuman_assets.py` (deterministic: two runs give identical files): 19,158 base vertices, the body as 14,517 drawn vertices and 26,756 triangles, 421 targets (179 sliders from MakeHuman's modeling modifiers, 32 macro anchors; genitals, expression, asym, measure, eyes and eyebrows left out), 53 bones, skin weights for every vertex. Size in `assets/characters/makehuman`: about 8.9 MB. **Two facts found while building that the draft got wrong:** (1) the archive stores offsets as int16 thousandths of a unit, not floats (the first conversion made a 113 m tall body; a test now guards the height); (2) the gender/age shape does not come from the `universal-...-averagemuscle-averageweight` targets (those are empty) but from the `african|asian|caucasian-<gender>-<age>` anchors, which are blended equally. The 16 muscle and weight variants are only converted for the young adult and applied at every age.
- **Skin weights.** The game rig has no face bones, so the default rig's jaw, lip, eyelid and tongue groups move with `head`, the breasts with `spine_03`; without that 193 drawn vertices had no weight.
- **Morph math, original code** (`CharacterBuilder`): macro blend over gender (0 female to 1 male) and age (1, 11, 25, 90 years, linear between), muscle and weight from 0.5 in either direction, then one signed or one-sided slider per target pair, a whole-body height scale and standing on y = 0. Bone heads are the mean of the vertices the rig names, so the skeleton follows the body.
- **Output** (`GltfCharacterWriter`): `characters/<name>.gltf` (one skinned mesh, 53 unrotated bone nodes, inverse bind matrices, flat skin material, embedded buffer; about 1.5 MB with five animations) and `<name>.character.json` (the settings). gdx-gltf loads it (53 bones, the five clips, height 1.86 m for the tall sample) and `ModelPoser` poses it.
- **Animation templates** (`HumanoidTemplates`): the `.anim` clips animate named parts of a humanoid made of entities, with local yaw/pitch/roll. The rig is built with every bone unrotated at rest, so the same angles mean the same axes: Hips to pelvis, Spine to spine_01, Chest to spine_03, Neck, Head, Arm/Forearm to upperarm/lowerarm, Thigh/Shin to thigh/calf, left and right. The hips' height track moves the pelvis from 100 units (the template hips height). Tracks the rig cannot take (other properties, parts without a bone) are reported and skipped. Fingers, toes, clavicles and spine_02 do not move. Verified: the Walk template swings a baked character's right thigh from -28 to +28 degrees.
- **Editor.** `editor-character` (`CharacterSession`: settings with undo/redo, 100 steps, template selection, bake, open, place as one undoable scene command; `CharacterBakeTool` for the command line, `gradlew :editor-character:bakeCharacter`), and `CharacterCreatorPanel` in the Animations tab: body settings, skin color, template toggles, 179 sliders in collapsible body-part groups, Name, Undo/Redo/Reset/Open and Create / Update character.
- **Not built / limits.** No live preview in the 3D view while a slider moves: Create / Update writes the model and the scene shows it (the cached model is reloaded; a character already in the scene with the same name and model is updated, otherwise a new entity is created). Number fields instead of slider widgets (the editor skin has none). No ethnicity sliders (the three variants are mixed equally). Muscle direction is not covered by a test (MakeHuman's "less muscle" widens the waist). The Details of the created entity are a normal Model component. The web player needs nothing new (it plays the glTF) but was not run. A person has not looked at any of it.
- **Sample** `character-creator-3d` (`tools/make_character_sample.py`): Tall Man (Walk), Short Woman (Idle) and Old Man (Walk), baked with `CharacterBakeTool` from the specs in `tools/make_character_sample.py`'s header.

---

### TD-054: Visual Pass on the Recent 3D Features, with In-Process Frame Captures

**Status**: Approved (2026-11-07: "approve TD-054", as typed, after the option "TD-054: a look-and-feel pass on what's built" was offered as the next milestone). The record itself was written after the approval, from that offer.

**Context**: Camera rigs (TD-050), joints (TD-051), mesh colliders (TD-052) and the character creator (TD-053) are Implemented and tested but none is Validated: every report lists what a person must look at as Manually Not Verified. An agent has no screen, and an earlier attempt to screenshot the whole display (phase 19-B) captured another application's text.

**Decision**
- **Frame captures come from the application itself.** A testing aid saves the application's own last rendered frame buffer as a PNG at the end of a smoke test: the game runtime with `GDXUNITY_SCREENSHOT=<file.png>` and `--smoke-test=N`; the editor likewise (its window content only). Nothing outside the application's frame buffer can be in the file, no input is sent, no desktop is captured. The aid is listed in the launcher usage and does nothing unless the variable is set.
- **What is looked at** (by reading the PNGs): the game runtime on `physics-joints-3d`, `physics-mesh-3d`, `character-creator-3d`, `character-playground-3d` (camera rigs) and `physics-sandbox-3d`; the editor with the Animations tab > Character panel, the Details of a Joint 3D and a Mesh Collider 3D, and the 3D view lines (joint lines, hull and mesh wireframes).
- **What a capture can and cannot show.** One still frame at a chosen time: it shows layout, clipping, scale, color, whether things are where they should be and whether a skinned character is intact. It does not show motion quality, feel or interactions; those stay Manually Not Verified, and a status of Validated is claimed only for what a frame shows, saying so.
- **Findings are fixed** when small and clearly wrong (a camera inside a floor, a text clipped, a character upside down); anything larger is written down in the report as a bug list, not silently left.

**Alternatives**: whole-screen capture (rejected, privacy); an off-screen render test in CI (needs a GL context in tests, which the headless suite deliberately avoids).

**Consequences**: a small launcher aid; PNGs go in `docs/reports/img/` (committed only if the owner asks) and are cited by name in the report.

**Acceptance (planned)**: every listed view captured and looked at; each is marked as showing what it should, or listed with the defect; defects that are small are fixed with a test where one fits; the report separates "seen in a still frame" from "still not seen: motion and feel".

**Implementation notes (2026-11-07)**
- **Aids built** (all inert unless their variable is set; listed in `AGENTS.md` section 5): game runtime `GDXUNITY_SCREENSHOT=<file.png>` with `--smoke-test=N` (`GameRenderer.setScreenshotFile`, reads the game's own frame buffer); editor `GDXUNITY_SCREENSHOT` the same, plus `GDXUNITY_EDITOR_TAB=<Viewport|Scene|Animations|Graph|Music>`, `GDXUNITY_EDITOR_SELECT=<entity name>`, `GDXUNITY_EDITOR_CREATOR=1` (character creator open) and `GDXUNITY_EDITOR_ASSISTANT=0` (AI Assistant left out of the layout, so Details gets the whole right column). Environment variables instead of new launch options, so `GameLaunchOptions` and `EditorLaunchOptions` did not change.
- **Captured and looked at** (PNGs in `docs/reports/img/`, named `td54-*`): game runtime on `physics-joints-3d`, `physics-mesh-3d`, `character-creator-3d`, `character-playground-3d` (camera rig) and `physics-sandbox-3d`; editor with the character creator open, the Details of a Joint 3D and a Mesh Collider 3D (on a probe scene), the 3D view with the joint sample and the mesh terrain selected (mesh wireframe, hull points).
- **Defects found and fixed:** the `character-creator-3d` camera faced away from the characters and the frame was empty (my mistake in the generator: the camera was turned 180 degrees); skin colors came out too pale because glTF material colors are linear and the creator's are sRGB (now converted, with a test); Joint 3D row labels were cut off ("Joined to (n", "Motor targe") and are shorter; the Mesh and Hull Collider note ran off the panel and is shorter; the creator's Undo, Redo, Reset and Open buttons ran together and have a width now.
- **Found, not fixed (not caused by TD-050 to TD-053):** with the same code as `HEAD`, Details is only about 150 pixels high at startup (the AI Assistant takes the rest of the right column although `isAssistantVisible` is false; re-applying the split amount after layout did not change it), so the new component editors are below the fold until scrolled; the Timeline toolbar labels run together ("SaveUndoRedo", "ReverseLoop"); Content Browser rows overlap their type tag with the path; "Nothing selected" text is cut at the panel edge; the editor's first 3D view is a side-on orthographic view.

---

### TD-055: Editor Layout Fixes (Details Height, Timeline Toolbar, Content Browser, Hint Text)

**Status**: Approved (2026-11-08: "approve TD-055", as typed, after it was offered as the recommended next milestone). Implemented the same day.

**Context**: The TD-054 visual pass listed editor layout defects that were there before TD-050 and left them unfixed: Details only about 150 px high at startup, Timeline toolbar labels running together, Content Browser rows with the type tag over the path, the "Nothing selected" hint cut at the panel edge, and a side-on first 3D view.

**Decision**
- **Details height.** The right column is a vertical `SplitPane` of Details and the AI Assistant. `SplitPane` clamps its split so the second widget keeps its own minimum height, which is what left Details small while the assistant was minimized. The assistant now sits in a `Container` with a minimum height of 0, and the split amount (Details' share) is 1.0 minimized and 0.55 open; the drag minimum for Details is 0.2.
- **Timeline toolbar:** a 4 px gap after each button. **Content Browser:** the type tag column is 100 px, wide enough for `[ANIMATION]`. **Nothing selected:** the hint wraps at the panel edge.
- **Not changed:** the editor's first 3D view (a side-on orthographic view): it is how the scene view starts and changing the default is a product choice, not a defect fix.

**Alternatives**: removing the assistant from the layout while minimized (rejected: the toggle and its saved state would need more code); a custom split widget (rejected: more code than the container).

**Consequences**: no new modules or APIs; the layout code is GL glue in `editor-desktop`, so the checks are frame captures (`GDXUNITY_SCREENSHOT`), not unit tests.

**Acceptance**: before and after captures of the Viewport and Animations tabs show each fixed item; the full test suite is unchanged apart from the 2 known failures.

---

### TD-056: Live 3D Preview and Sliders in the Character Creator

**Status**: Approved (2026-11-08: "approve TD-056", as typed, after it was offered as the recommended next milestone). The record was written after the approval and the work done the same day.

**Context**: The character creator (TD-053) shows nothing until Create / Update is pressed and the model is looked at in the scene, and its settings are number fields. TD-053 listed "no live preview" and "number fields instead of sliders" as its main limits.

**Decision**
- **A preview at the top of the creator** (`CharacterPreviewWidget` in `editor-desktop`, drawn by `ModelPreview` in `engine-render`, an existing module): the character for the current settings, drawn off-screen into a frame buffer, with the PBR shaders and skinning of the scene renderer. A drop-down under it picks the animation to play in a loop (any selected template) or the rest pose; dragging turns the model, the wheel zooms, a button resets the view.
- **The preview shows the real model.** `CharacterSession.bakeModel()` builds the same glTF document `bake` writes, without writing a file (`bake` now uses it); `ModelPreview` loads it from memory, so animations are the baked templates.
- **Reload only when needed:** when the settings or the selected templates differ from what the preview shows, and only while the creator is on screen (checked just before each frame is drawn). A failure goes to the status bar once.
- **Sliders** beside every setting (body, skin, shape sliders), with the number field kept. A slider commits (one undo step) when it is let go; the field follows it while it moves. A `Slider` style was added to the editor skin.
- **Side fixes** needed to show it: the Timeline toolbar scrolls sideways instead of forcing the whole editor wider than a narrow window (with the creator open it had pushed the window content about 17 px off screen); the creator aid `GDXUNITY_EDITOR_CREATOR=<character>[:<animation>]` opens a saved character with a preview animation for captures.
- **Not changed:** no new modules; no ethnicity sliders, eyes or hair; the character is still baked as one body model.

**Alternatives**: a preview drawn straight from the builder's arrays without glTF (rejected: it would not show the baked animations or the real skinning); writing a temporary file into the project (rejected: the preview loads from memory, using a deleted temp file only for the loader).

**Consequences**: one frame buffer and one set of shaders while the creator is open; a reload takes a rebuild of the body (about 50 ms in tests), the glTF write and the parse, on the UI thread, once per committed change (not per drag step).

**Acceptance**: captures show the preview following the opened character (different builds) and a template animation playing at different moments; `CharacterSession.bakeModel` has tests; the full suite is unchanged apart from the 2 known failures.

---

### TD-057: Web Player Checks (`npm ci`, `typecheck`, `test`) and the Failures They Showed

**Status**: Approved (2026-11-08: "approve TD-057", as typed, after it was offered as the recommended next milestone). The record was written after the approval and the work done the same day.

**Context**: The web player (`engine-babylon`, package `forgeroom-web`) had never been checked since TD-048: its dependencies were never installed (the disk was nearly full) and its tests and type check were never run, while desktop samples and components kept being added.

**Decision**
- Install the dependencies from the lockfile (`npm ci`, from the npm registry; `node_modules` is ignored by Git), run `npm run typecheck` and `npm test`, and record the real results.
- Fix small failures caused by earlier work: my own samples and one missing component. Do not build large missing features: the web player's lack of 3D physics, joints, colliders, camera rigs and character controllers is written down as a known gap and listed in the test (`KNOWN_UNSUPPORTED`) so that any other gap still fails it.
- Not run: `npm run build` (it runs `sync-samples`, which rewrites files in the working tree) and the player in a browser.

**Alternatives**: building web 3D physics now (rejected: needs its own decision, a physics engine for the browser); leaving the test red (rejected: it would hide new gaps behind the known ones).

**Consequences**: the web test suite is green and states its known gaps; `PointLightComponent` is supported by the web player (a Babylon point light that follows its entity); web parity for 3D physics, joints, mesh colliders, camera rigs and character controllers is a separate, larger decision.

**Acceptance**: install, type check and tests run with results recorded; failures investigated and each either fixed or listed; no Git write.

---

### TD-058: Web 3D Physics Study (no engine adopted)

**Status**: Approved as a study (2026-11-08: "approve TD-058", as typed, after it was offered as the recommended next milestone). It adopts nothing: the study recommends an engine, and building anything needs a new record (a TD-059) and the owner's approval.

**Context**: The web player runs none of the desktop's 3D physics (TD-048 to TD-052); four samples load but do not move (TD-057 report).

**Decision**
- Compare three browser physics engines against what the desktop backend uses, with small scripts run in Node outside the repository: Jolt's JavaScript build, Rapier, Havok (`tools/web-physics-spike/`).
- Write the findings and a recommendation in `docs/reports/web-3d-physics-evaluation.md`. Change no engine or editor code.
- **Findings in one line:** all three simulate a falling body and a 300-box pile inside a frame; the Jolt JavaScript build is the same engine family as the desktop and would behave the same, at about 0.9 MB gzipped; Havok is fastest and smallest to download but differs from the desktop and its licence terms were not checked; Rapier is the largest download and the most different. Joints, characters and mesh shapes were found present but not run.
- **Recommendation:** Jolt through jolt-physics, behind a TypeScript port of `PhysicsBackend3D` and `PhysicsSystem3D`, built in three steps (bodies and colliders; joints, hull and mesh colliders; character controller, camera rig and the two script ports).

**Alternatives**: Havok, Rapier, or leaving 3D physics desktop-only (all in the report).

**Consequences**: no change to the product; the owner decides whether and how web parity goes ahead.

**Acceptance**: the three engines measured, the results and their limits written down, a recommendation and the open questions for the owner stated, no Git write.

---

### TD-059: Web 3D Physics on Jolt, Step 1 (Bodies, Box / Sphere / Capsule Colliders, Queries, Events)

**Status**: Approved (2026-11-08: "approve TD-059", as typed, after the TD-058 study recommended it and its two questions were put to the owner). This record covers **step 1 only**; steps 2 and 3 need their own approval. The record was written after the approval and the work done the same day.

**Context**: The web player ran none of the desktop's 3D physics (TD-057). TD-058 recommended Jolt's JavaScript build behind a TypeScript port of the desktop's `PhysicsBackend3D` and `PhysicsSystem3D`.

**Decision**
- **Dependency:** `jolt-physics` ^1.1.1 (MIT) in `engine-babylon`. The `wasm-compat` build (the WebAssembly inside the script, no extra file to serve). It is loaded with a dynamic import, so it is a separate download (about 953 KB gzipped) that only scenes with 3D colliders fetch; 2D scenes do not.
- **New code** (`engine-babylon/src/runtime/physics3d/`): `types.ts` (the engine-neutral backend interface, descriptors, layers, units), `JoltBackend.ts` (`JoltPhysicsBackend3D`: the desktop's object-layer scheme, shapes with offsets and compounds, sensors, masks, mass override, forces and impulses, ray and sphere queries, contact pairs reported once, manual release of every Jolt object), `Physics3D.ts` (the system: bodies from Rigid Body 3D, Box, Sphere and Capsule Collider 3D components, fixed 1/60 s steps, kinematic and dynamic pose sync, sensors for triggers, scene-unit API like the desktop).
- **Wiring:** `ScenePlayer` makes the system on Play when the scene has a 3D collider and waits for Jolt the first time (`onPlayingChanged` tells the page when play really starts; Stop cancels a waiting Play); `ScriptComponent` gets `physics3d`, `raycast3D` and the four `on...3D` contact callbacks; `SceneBuilder` lists the four component types as supported; the `SandboxLauncher` script is ported (`src/scripts/sandbox.ts`).
- **Not built (later steps):** convex hull and mesh colliders, joints (step 2); character controllers, camera rigs, the `WalkerPlayer` port (step 3). Scenes that use them still load; the player lists them as unsupported, and their other bodies do simulate (a joint sample's pieces fall apart; a mesh terrain is not solid).
- **Tests:** `test/physics3d.test.ts` (16, headless on the real Jolt build in Node); the known-gap list in `sceneBuilder.test.ts` now names only joints, hull and mesh colliders, character controllers and camera rigs.

**Alternatives**: as in TD-058 (Havok, Rapier, desktop-only).

**Consequences**: about 953 KB more for 3D scenes only; the web and desktop physics should agree because both are Jolt, but the two builds' versions were not compared (the desktop uses jolt-jni 6.2.0).

**Acceptance (step 1)**: the sandbox sample runs on the web with its script; tests pass; build passes; checked in a real browser (headless Edge) for loading, Play, Stop and the balls coming to rest.

---

### TD-060: Java and Kotlin Classes and Methods as Visual Scripting Nodes, and Example Graphs for the Samples

**Status**: **Approved** (2026-11-08: "approve TD-044-TD-061 and implement"). Proposed 2026-11-08. The owner chose the scope below in answer to two questions (class scope: "Engine API plus project scripts"; samples: "Hand-written example graphs"); the design still needs approval ("approve TD-060").

**Context**: The visual scripting editor (TD-029, TD-044) has about 40 built-in node types and five value types (`FLOAT`, `BOOLEAN`, `STRING`, `VECTOR3`, `ENTITY`). Anything else a script can do (call a method of the engine API, of a project script, use an integer or an object) needs a new hand-written node type. There are three example graphs (`Greeter`, `Hop`, `Spinner`); none of the 14 sample games' Java scripts has a visual version, and graphs can already be turned into Java, Kotlin and TypeScript but not the other way round.

**Decision (proposed)**
- **Method nodes from classes.** A node is generated for each public method (and constructor and static method) of the classes in a **catalog**: the engine API classes scripts already use (for example `Entity`, `Transform`, `PhysicsSystem3D`, `Vector3`, `Quaternion`, input and math helpers) plus every class compiled from the project's `scripts` folder (TD-019). Not the whole classpath: the palette stays readable and a graph cannot call arbitrary JDK code. The catalog is a file in the project (`assets/graphs/classes.json`), editable.
- **Node shape.** Id `java:<class>#<method>(<parameter types>)`, stable against renames of parameters. Pins: a `target` pin for an instance method, one pin per parameter, one pin for the result. A method returning `void` is an Action node; a method that returns a value and is named like a getter (`get`, `is`, `has`, `can`, `find`, `contains`, `to`) is a Pure node; any other value-returning method is an Action node whose result pin keeps the value. A node property lets the user switch a Pure node to Action.
- **Value types.** Add `INTEGER` and `OBJECT` (carrying a class name) to `ValueType`; `int`, `long` and `double` map to `INTEGER` and `FLOAT` with checked conversion; a pin of an object type connects to pins that accept it (assignable class). Existing graphs keep loading (`schemaVersion` stays 1; the new types are additions). This amends TD-029.
- **Runtime.** The interpreter calls methods through cached method handles; an argument that does not convert, a null target or an exception from the method stops only that chain and names the node, as today.
- **Editor.** The palette gets one category per catalog class ("Java: Entity"), a search box, and drag-onto-canvas; pins show their types in colors; a Refresh button rereads the project's compiled classes. Parameter names need the `-parameters` compiler flag on the engine modules (a small build change) and on project script compilation; without it pins show types only.
- **Code generation.** The Java and Kotlin generators emit direct calls for method nodes. The TypeScript generator and the web player's interpreter cannot call Java: a graph with method nodes is listed there as unsupported (the way unsupported components are), not silently dropped. Web equivalents are out of scope.
- **Example graphs for the samples.** A graph for each sample script (platformer, collector, hopper, runner, shooter, breakout, racing, fps, puzzle, fighter, grapple, ruins, trio, sandbox, walker), in `assets/graphs/samples`, each stating which Java script it mirrors and **which parts it covers**: a game with state fields, loops or helper methods may be only partly expressible, and the example says so. The Graph tab gets an **Examples** list that opens them; tests check that every example loads, validates and, where practical, behaves like the script on a headless scene. Automatic Java-to-graph import is **not** part of this decision.
- **Trust.** As with scripts (TD-019), method nodes run as trusted in-process code. The catalog limits what the palette offers; it is not a sandbox.

**Milestones (each its own report and approval)**: A) engine: value types, catalog, method nodes, interpreter, serialization, tests. B) editor: palette, search, drag, pin colors, refresh, Java and Kotlin generation. C) the example graphs and the Examples list, with whatever helper nodes they turn out to need (input keys, spawning entities, playing cues are likely).

**Alternatives**: every public class on the classpath (rejected by the owner's choice: unreadable palette, arbitrary code); only the built-in engine classes (rejected: your own classes would not be nodes); more hand-written built-in node types (what exists; does not scale); Java-to-graph import (deferred).

**Consequences**: a bigger `ValueType` and pin compatibility rules; reflection in the interpreter; `-parameters` in the build; some example graphs will honestly show "partly covered"; the web player does not run method nodes.

**Acceptance (planned)**: a method of a catalog class and of a project script can be dragged in, wired and run in the editor's Play; undo and save and load work; Java and Kotlin generation compile; every sample has an example graph with its coverage stated; the full suite is unchanged apart from known failures; visual checks recorded as seen or not.

**Progress (2026-11-08)**:
- **A (engine)**: present in the working tree (`ClassCatalog`, `JavaMethodNodes`, `INTEGER`/`OBJECT` value types, interpreter, serialization, Java/Kotlin generation); `JavaMethodNodesTest` passes. Kotlin generation could not be checked here: `GraphMethodNodeCodeTest` fails to compile Kotlin because the scripting plugin jars are missing from this environment.
- **B (editor)**: done except what A already had. The node menu searches (title or category, so "Java: Entity" works) and pins are coloured by type. New: `ProjectNodeCatalog` (editor-visualscript) reads `graphs/classes.json` (engine classes when absent or broken, with the reason reported) and adds the public classes compiled from the project's scripts; **Refresh Nodes** in the Graph tab rereads both; `NodeRegistry.removeMethodNodes`. `ProjectNodeCatalogTest` (5). Not done: the "switch a Pure node to Action" property, the `-parameters` build flag (pins show types only), drag from a palette (the right-click menu is used). **Manually Not Verified**: the button and the menu with project classes were not clicked.
- **C (examples)**: done as the owner chose ("start milestone C", after the question whether to add helper nodes). Two desktop-only built-in nodes: **Key Down** (`input.keyDown`, property `key`) and **Input Axis** (`input.axis`, properties `negative` and `positive`, value -1 to 1). Keys are named like libGDX's `Input.Keys` constants (`A`, `LEFT`, `SPACE`, `SHIFT_LEFT`), several separated by commas, any case (`KeyNames`); an unknown name is a graph error naming the node. The interpreter asks the host (`GraphHost.isKeyDown`, default `Gdx.input`), Java and Kotlin generation writes `keyDown(Input.Keys.A, Input.Keys.LEFT)` plus a helper, and TypeScript generation and the web editor refuse the nodes with a message that says why (they are in `DESKTOP_ONLY_NODE_TYPES`). The clamp used by the examples is the existing method node `MathUtils#clamp`. 15 graphs in `assets/graphs/samples/` (one per sample game: platformer, collector, hopper, runner, shooter, breakout, racing, fps, puzzle, fighter, grapple, ruins, trio, sandbox, walker), listed in `graphs/samples/examples.json` with, for each, the script it mirrors, what it covers and what it leaves out; the Graph tab's **Examples** button shows the list and opens one. **Every example is a slice of its game, mostly the player's control**, not the game: none has the collision, scoring, enemies or levels, and the list says so per example. Two substitutions are approximations stated in the list (drag as a straight-line factor, a car that drives along -Z because turning a heading into a direction needs sine and cosine, which are not nodes). Not done: Java-to-graph import (deferred in this decision), web equivalents.


### TD-061: Songs in Scenes Through a Music Source Component (Phase 20-C, Second Part)

**Status**: Approved (2026-11-08: "approve TD-044-TD-061"). Proposed 2026-11-08. The user asked to complete the open work; songs in scenes is the next item of 20-C after
the sound effects of TD-043. The decisions below are mine and unconfirmed.

**Decision (proposed)**:
- `engine-scene` gets `MusicSourceComponent` (type id `MusicSource`): the project-relative path of a `.song` made in the music
  studio, volume 0 to 1, loop and play on start (default on). Like the Audio Source it holds data and a `MusicSink` the host
  binds while the scene plays; `play()` and `stop()` are for scripts.
- Desktop: `GameAudio` reads each song through the project's asset source, builds a `SongPlayer`, and mixes every sounding song
  (at its volume) into the same output as the sound effects. A scene with a Music Source whose song is missing, blank or has
  an instrument that cannot be built **does not start**, naming the entity and the song. Several songs may sound at once.
- Web: `SceneMusic` plays the song through the music engine (`AudioEngine`, the audio worklet). The browser has one song
  engine, so **one song sounds at a time**: with several Music Sources that play on start, the first plays and the rest
  are reported.
- Five samples got background music: platformer-2d, collector-3d, racing-3d, ruins-runner-25d and runner-3d.
- Not done: `AudioListenerComponent` and positional sound, `.wav`/`.ogg` clips, sound-effect export to `.ogg`.

**Consequences**: the bundled `demo.song` had a Lead that was a SoundFont without a file and had lost its three drum channels, so a
Music Source pointing at it was refused and `BundledDemoSongTest` failed. Repaired on 2026-11-09: Kick, Snare and Hat drum channels
and their patterns are back, the Lead is a synth again, and both tests pass.


### TD-062: Exporting Sculpts as glTF Models, and a Triangle Limit Where OpenGL Runs on the CPU (Phase 19-C)

**Status**: Approved (2026-11-08, by the user). The decisions below finish what TD-038 planned for 19-C.

**Decision (proposed)**:
- `engine-sculpt` gets `SculptGltfWriter`, a small hand-written `.glb` writer (no graphics context, no new dependency) instead
  of gdx-gltf's exporter, which needs a libGDX `Model`. The mesh is written as one node and one mesh with a primitive per
  60,000-vertex chunk (16-bit indices, the limit of a libGDX mesh), positions and normals interleaved, and a plain PBR
  material in the sculpt's colour. Positions are multiplied by 0.01 so a default Model component (import scale 100) shows the
  model at the size it was sculpted. No UVs, so no textures.
- File > Export Sculpt as Model and the Details button write `models/<name>.glb`, replacing the file of an earlier export;
  the Content Browser then lists it and it is placed like any model (drag, Model component, prefab, game runtime, web player).
- Where the view is drawn on the CPU (the streamed web editor sets `LIBGL_ALWAYS_SOFTWARE=1`, TD-037), new sculpts have 49,152
  triangles instead of 196,608, a sculpt made from a model is subdivided towards at most 100,000, and the Details panel
  warns about a sculpt above 100,000 triangles. Nothing is refused: the limit is advice.

**Consequences**: an exported model is a snapshot; it does not follow later strokes until exported again. The `.sculpt`
stays the editable source.


### TD-063: Positional Sound, Recorded Clips and a Depth Buffer Shared by 2D and 3D (Phase 20-C rest, Phase 13-D)

**Status**: Approved (2026-11-09: "approve TD-063"). Proposed the same day. The user said "start 1-4 as suggested" (13-D compositing, 20-C rest, TD-060 follow-ups, model
thumbnails). The choices below were mine; the three questions the 20-C rest needed an answer to were answered by the
defaults named here.

**Decision (proposed)**:
- **Listener and positional sound.** `AudioListenerComponent` (one per scene, no settings, usually on the camera or player) marks the
  ears. An Audio Source gains `spatial` (off by default, so every existing scene sounds as before), `minDistance` (default 100) and
  `maxDistance` (default 2000). A spatial source is heard at full volume up to `minDistance`, falls in a straight line to silence at
  `maxDistance`, and is panned by how far to the listener's right or left it is (`SpatialAudio`). Height and depth do not pan, and
  there is no Doppler effect or speed of sound. A spatial source in a scene with no listener plays in the middle at its own volume.
  The sink gets `play(sound, volume, pitch, pan)`; the mixer (`SfxPlayer`) quietens only the far speaker, so the middle is as loud as ever.
- **Recorded clips.** An Audio Source whose sound ends in `.wav` plays that file of the project (read with the existing `WavIO`; mono or
  stereo, 8 to 32 bit, any sample rate). A missing or unreadable clip stops the scene from starting and names the entity and file,
  like a missing song. `AssetType.SOUND` lists `.wav` files in the Content Browser, and dropping one into the scene makes an Audio
  Source named after it.
- **Not done: `.ogg`.** Reading and writing Ogg Vorbis needs a decoder and encoder that the project does not have; adding one is a new
  dependency, which this repository requires a written justification and approval for. Candidates when it is wanted: a pure-Java
  Vorbis decoder for reading (small, no natives), and for writing either a Java encoder or leaving export as WAV. The web player has
  not been given positional sound or clips either.
- **Depth buffer shared by 2D and 3D (13-D).** With a perspective camera, `SceneRenderer2D` tests shapes and sprites against the depth
  the 3D pass left, at each entity's own world Z, and does not write depth (so they keep painter's order among themselves). With an
  orthographic camera nothing changes. `setDepthComposite(false)` turns it off. No bundled sample uses 2D shapes under a perspective
  camera, so no existing scene changes; the new sample `depth-compositing-3d.json` shows it.
- **Follow-ups of TD-060.** `-parameters` is passed to the Java compiler for every module and for project scripts (not yet to the Kotlin
  compiler), so method nodes label their pins with parameter names. Sine and Cosine nodes (degrees) exist in the desktop and web
  interpreters and in all code generators; the race car and first-person examples use them to follow their heading.
- **Model thumbnails.** `ModelThumbnails` draws each model once with `ModelPreview` into a 64 pixel frame buffer, one per frame.

**Deliberately not done**: web equivalents of the keyboard nodes (the examples also use method nodes, which the web cannot run, so
nothing would use them yet) and the Pure/Action switch on method nodes (it makes a node's kind depend on the node, which touches the
validator, the interpreter and all generators and wants its own design).

**Consequences**: scenes that use clips or spatial sources need the desktop runtime; the web player ignores spatial settings and cannot
play `.wav` sources. Mixed sound is checked in tests by rendering the mixer; nobody has listened to it.

**Amendment (2026-11-09, after "can you implement the remain issues")**: the parts listed above as not done were then done, within the
approved decision and without a new dependency:
- **`.ogg` clips are read**, not written. libGDX's desktop backend already carries a decoder (`OggInputStream`, on `jorbis`), so
  `OggClips` (engine-desktop) decodes Ogg Vorbis (mono or stereo) into the same clip type as `.wav`; `AssetType.SOUND` lists `.ogg`, an
  Audio Source names it like a `.wav`. **Writing `.ogg` is not possible**: nothing in the project can encode Vorbis, and no encoder was
  added. A sound effect or song is exported as WAV, as before. The test fixture `sounds/tone.ogg` was made once with libsndfile outside
  the project.
- **The web player has positional sound and clips.** `SceneBuilder` accepts the Audio Listener; `GameScene.playSource` applies the same
  model (`hear` in `src/audio/spatial.ts`); the output pans with one gain per speaker; clips are read by `decodeWav` (pure TypeScript,
  same formats as `WavIO`, channels averaged to mono) and, for `.ogg`, the browser's `decodeAudioData`.
- **Keyboard nodes run on the web.** `src/visualscript/keys.ts` maps libGDX key names to `KeyboardEvent.code` (letters, `NUM_0` to
  `NUM_9`, `F1` to `F12`, arrows, Space, Enter, Escape, Tab, Backspace, left and right Shift, Control and Alt); the Java side keeps the same
  table (`KeyNames.browserCodes`) and a Vitest test compares the two. TypeScript generation writes `this.input.isDown(...)`; a key the web
  does not map is refused with its name. The examples that also use the Java method node `clamp` still cannot run on the web.
- **Method nodes can be switched between Pure and Action.** Every Pure method node has a hidden Action twin (`<id>@action`, same method,
  same pins plus execution pins); the Graph tab's **Pure/Action** button switches the selected nodes (`SwitchMethodKind`, undoable; going
  back to Pure drops the execution wires and undo restores them).
- **`-java-parameters` is passed to the Kotlin compiler** as well.


### TD-064: Web 3D Physics on Jolt, Steps 2 and 3 (Hull and Mesh Colliders, Joints, Character Controllers, Camera Rigs)

**Status**: **Approved** (2026-11-09: "apporve TD-064", as typed). Proposed the same day; TD-059 covered step 1 only and said steps 2 and 3 need their own approval. The choices were mine.

**Context**: three bundled samples still lose parts on the web (`sceneBuilder.test.ts` lists them): `physics-mesh-3d` (hull and mesh
colliders, TD-052), `physics-joints-3d` (joints, TD-051) and `character-playground-3d` (character controller, TD-049, and camera rigs,
TD-050). The desktop and web both run Jolt, and the web already has the backend interface, the system and the layer scheme (TD-059).

**Decision (proposed)**:
- **Step 2, shapes and joints.** In `JoltBackend.ts` and `Physics3D.ts`: convex hull colliders and triangle mesh colliders (mesh shapes on
  static bodies only, like the desktop), reading the same geometry the desktop's `ColliderGeometry` does (the glTF the entity's Model
  component names, scaled by its import scale, or the entity's primitive); and the five joint types (fixed, hinge, slider, distance, ball)
  with the desktop's limits, motor-free as there. Break force stays out, as on the desktop.
- **Step 3, characters and cameras.** A `CharacterController3DComponent` runs on Jolt's `CharacterVirtual` through the same
  `moveCharacter`, `jumpCharacter` and `isGrounded` calls as the desktop (steps, slopes, jump, no Rigid Body 3D or collider on the entity);
  the three camera rigs (follow, third-person orbit, first-person, TD-050) are ported to the web `ScenePlayer`; the `WalkerPlayer` script
  is ported (`src/scripts/walker.ts`).
- **No new dependency**: `jolt-physics` already ships these shapes, constraints and `CharacterVirtual` (to be confirmed in the first step
  of the work; if the `wasm-compat` build lacks one, the work stops and the record is revisited).
- **Order and reports**: step 2 first (shapes, then joints), each with its own report; then step 3. After each, the matching entry leaves the
  known-gap list in `sceneBuilder.test.ts`, so the list shrinks to nothing and the test then fails on any new gap.
- **Tests**: headless on the real Jolt build in Node, as `physics3d.test.ts`: a ball rests on a mesh terrain, a hull crate stops on a floor,
  each joint type holds its constraint (a hinge door swings and stops, a slider stays on its axis), a character walks onto steps and up a
  slope and stops at a wall, and the camera rigs reach their offsets.

**Alternatives**: leave the three samples as known gaps (cheapest; the web stays visibly behind), or build only step 2.

**Consequences**: the web download for 3D scenes may grow a little with more shapes and constraint code; web and desktop behaviour should
match because both are Jolt, but the two builds' versions were never compared (desktop jolt-jni 6.2.0). Visual and interaction checks are
not possible in this environment, so each step ends with "Manually Not Verified" for what a person must see.

**Acceptance (planned)**: the three samples run on the web without any of their parts reported unsupported; the tests above pass; the
known-gap list is empty; type check, Vitest and build pass.

**Implementation notes (2026-11-09, steps 2 and 3 built in one go after the approval)**:
- **Jolt build checked first**: `jolt-physics` 1.1.1's `wasm-compat` build ships `ConvexHullShapeSettings`, `MeshShapeSettings`, the five constraint
  settings, `CharacterVirtual` and `ExtendedUpdateSettings`; no new dependency, and the 3D download is unchanged (about 953 KB gzipped).
- **Step 2, shapes**: `ShapeDescriptor` got `HULL` and `MESH`. Triangles come from the Babylon meshes under the entity (`MeshColliderGeometry`):
  its primitive's mesh, or its glTF model's meshes once loaded, in the pose they have when the body is made, with the entity's scale applied and
  its own position and rotation taken out (so the body's pose places them). An entity whose model is still loading has no body yet (it is tried
  again on every update); a model that failed to load, a mesh collider on a moving body, an entity with no model or primitive, and a scene with
  no geometry source add a warning that names the entity (`Physics3D.getWarnings()`, shown by the player through `onAssetProblem`), once each.
  Hull points are reduced to 64 by the desktop's farthest-point sampling (`reduceHullPoints`). **Primitives' triangles are turned around**: Babylon's
  built-in meshes wind the other way from glTF in this right-handed scene, and a Jolt mesh shape only has a front face (found by a ball that fell through a
  box mesh).
- **Step 2, joints**: `Joint3DComponent` runs on Jolt's constraints with the desktop's rules: world-space settings (the pose at creation is the rest
  pose), the connected body or the world as body 1, `collideConnected` off through the contact-validation callback, motors on hinge and slider,
  the same refusals (nothing dynamic, a character, a missing or self-connected name, a motor on a type without one, limits that do not include 0).
  `setJointMotor`, `getJointPosition` and `hasJoint` on `Physics3D`, and `setJointMotor3D` / `getJointPosition3D` on scripts. **A long-lived Jolt vector kept by the
  backend corrupted memory and crashed joint removal on dispose**; the backend keeps numbers and makes vectors per call.
- **Step 3, characters**: `CharacterController3DComponent` on `CharacterVirtual` with the desktop's gravity, jump, ceiling and step rules
  (`moveCharacter`, `jumpCharacter`, `isCharacterGrounded`, `getCharacterVelocity`, `teleportCharacter`, and the `...3D` calls on scripts); contact events
  for the bodies it touches. **Not the same as the desktop**: this Jolt build does not expose the character's inner body settings, so other bodies do
  not collide with the character's shape (the character collides with them and pushes dynamic ones), and the character's collision layer and mask are not applied
  (it collides with every layer). A character beside a rigid body or collider is refused.
- **Step 3, camera rigs**: `CameraRig` (FOLLOW, ORBIT, FIRST_PERSON, occlusion through the physics ray) runs in the player's frame after physics and
  scripts; scripts reach it with `getCameraRig()` (`rotate`, `setDistance`, ...). A target that does not exist is reported like a script that failed
  to start. The `WalkerPlayer` script is ported (`src/scripts/walker.ts`).
- **Tests**: Vitest `physics3dShapes` (9: hull reduction, terrain model, hull rocks, ray on the terrain, refusals), `physics3dJoints` (12),
  `physics3dCharacter` (14: stands, walks at the speed and the same distance at 30 and 60 frames a second, jump apex within 5% of v squared over 2g,
  four steps climbed, a block taller than a step not climbed, the 25 degree ramp climbed and the 60 degree one not, a crate pushed, contacts, refusal,
  removal and recreation, teleport, the script), `cameraRig` (9). The known-gap list in `sceneBuilder.test.ts` is empty. Vitest 359 of 359, `tsc` clean,
  `npm run build` passes.
- **Not verified**: nothing was run in a browser (no browser tool in this environment); the page, the Play button and the keyboard were not used.

**Browser pass (2026-11-09, headless Edge driven over the DevTools protocol; not a person)**: the built player (`vite preview`) loaded all 32
samples and played them for a few seconds each. It found **two real defects the Node tests had missed**, both fixed: (1) stopping a scene with
joints crashed Jolt (`RemoveConstraint`, memory access out of bounds) and broke every later 3D scene, because the backend released a constraint that
`RemoveConstraint` had already freed; (2) the character creator's models (`characters/*.gltf`) were not copied to the web build, so
`character-creator-3d` reported three failed models (the sync script now copies them). After the fixes no console error was left; screenshots of
`character-playground-3d` (the character walked towards the steps with the follow camera tracking it), `physics-joints-3d` and `physics-mesh-3d` were
looked at. Not seen: sound, the text of the help line in a wide window, and any mouse use.


### TD-065: Web Method Nodes for a Safe Subset, and Java Source Back Into Graphs

**Status**: **Approved** (2026-11-09: "approve TD-065"). Earlier status: **Proposed** (2026-11-09). Nothing is built. It covers the two TD-060 leftovers that are too large to start without choices
from the owner. Each part can be approved, changed or refused on its own.

**Context**: since TD-060 a graph can call Java methods (`java:` nodes). The web player cannot (there is no JVM), so a graph that uses one,
such as `MathUtils.clamp` in 14 of the 15 example graphs, is listed as unsupported there. And a graph can become Java, Kotlin or
TypeScript (and back, through the block TD-045 embeds in generated files), but hand-written Java cannot become a graph.

**Part A, web method nodes for a safe subset (proposed)**
- A fixed list of methods that have an exact TypeScript counterpart, kept in one table next to the web registry: `MathUtils` (`clamp`,
  `lerp`, `sin`, `cos`, `atan2`, `floor`, `ceil`, `abs`, `random`), the arithmetic and length methods of `Vector3` (`add`, `sub`, `scl`,
  `len`, `nor`, `dot`, `crs`, `dst`, `lerp`), `String` (`length`, `contains`, `startsWith`, `toUpperCase`, `toLowerCase`), `Entity` (`getName`, `getTransform`)
  and the `Transform` position and rotation getters and setters. A node whose method is in the table runs on the web with the same id
  (`java:com.badlogic.gdx.math.MathUtils#clamp(float,float,float)`); any other `java:` node stays unsupported there, with the message it has now.
- A test reads the Java catalog and the TypeScript table and fails if a table entry has no Java method, so the two cannot drift; each entry has a
  behaviour test with the same inputs on both sides.
- Alternative: leave the web without method nodes (cheapest; the examples would stay desktop-only).

**Part B, Java source back into graphs (proposed, narrow)**
- An importer for **the shape the generators write**: a class with `onStart`, `onUpdate`, the event methods and `runN` chains of statements
  the node set can express (assignments, calls of catalog methods, `if`, field reads and writes). Everything else stays text: the importer
  reports the first line it cannot read, with the reason, and creates nothing partial. Today's embed block already restores generated files exactly, so this part is for
  hand-edited generated code and small hand-written scripts.
- Not proposed: a general Java decompiler to nodes, loops with arbitrary conditions, inner classes, generics, lambdas, or reading the sample game scripts
  (their collision code is hundreds of lines of Java that nodes do not express; see the "left out" text of each example graph).
- Alternative: no importer; keep the embed block as the only way back.

**Acceptance (planned)**: A: every example graph that only uses table methods plays on the web; the parity test passes; B: every graph the
generators can write round-trips through its generated Java and back to an equal graph, and a hand-edited file with one unsupported line is refused with that line named.

**Consequences**: A adds a table to maintain (a new catalog method needs a web entry or stays desktop-only); B adds a parser that must track
the generators (the round-trip test does that).

**Implementation notes (2026-11-09, after the approval)**:
- **Part A, built**: `WebMethodNodes` (engine-visualscript) lists 44 method nodes of `MathUtils` and `Vector3`. `WebMethodNodesTest` builds
  `engine-babylon/src/visualscript/javaMethodNodes.json` from the real nodes (ids, titles, categories, kinds, pins, whether an Action twin exists) and from
  real calls of each method with three sets of fixed arguments (more than 100 recorded results; non-finite results are left out) and fails when the file is out of date
  (`GDXUNITY_WRITE_SAMPLES=1` rewrites it). The web player (`src/visualscript/javaMethods.ts`) registers those nodes with the same ids and pins, plus the Action twins,
  each running a TypeScript version; `javaMethodNodes.test.ts` compares every implementation with every recorded Java result, checks the id list against the Java class,
  and runs a Pure node and its twin in a graph. The web has a new `INTEGER` value type (whole numbers; wires into a float pin, not the other way; integer literals are
  kept as integers when a file is read and written again). `Math.random` replaces libGDX's generator, so random nodes keep their ranges but not their sequences; `sin` and
  the like are exact on the web and table-based on the desktop (they agree to about 1e-4).
- **Deviations from the proposal**: (1) `String` and `Entity` / `Transform` methods are **not** in the table: `String` is not in the desktop's catalog (the nodes do not exist
  there), and `Entity` / `Transform` pins are objects, which the web graph model has no type for. (2) Graphs with these nodes **run** on the web, but the web's TypeScript and JVM
  **code generators still refuse them**, with a message saying so; code for them is only generated on the desktop.
- **Examples on the web**: all 13 example graphs that need no 3D physics nodes now validate in the web editor, and the ship example flies by held keys with `MathUtils.clamp`
  (test); `WalkerPlayer` and `SandboxKick` stay desktop-only (they need the physics nodes).
- **Part B, narrowed**: a parser from arbitrary Java back into nodes was **not built**. What exists: importing a script now compares its code with what the embedded graph generates
  now and **names the first line edited by hand** (`GraphSourceEmbed.findEdit`, `GraphAssets.importScriptChecked`, the status line of both editors; `findEdit` in `sourceEmbed.ts`),
  so an edit is not dropped without notice. The embedded graph is still what is imported; it is never refused for an edit (refusing would lose the user's graph). Tests:
  `ScriptImportEditTest` (5) and `scriptEdits.test.ts` (3). A real importer for hand-written code would need a parser for the statement shapes the generators write and is still open.

### TD-066: 2D and 2.5D Authoring Tools: Sprite Editor, Sprite Animation, Tilemaps

**Status**: **Approved** (2026-11-09: "approve TD-066"; the open questions were not answered separately, so the proposal's defaults apply: names `engine-sprite` and `editor-sprite`, desktop first, `.ase` import later, order A to F). Earlier status: **Proposed**. Milestones A and B are being built.

**Context**: the engine runs 2D (`SceneRenderer2D`, `SpriteRendererComponent`, Box2D) and 2.5D (3D scenes with side-on camera scripts; four samples) games, but
all authoring tools so far serve 3D. A 2D game today needs pictures made in outside programs, and has no sprite sheets, no frame animation and no tile maps.
`SpriteRendererComponent` shows one whole texture; `AnimatorComponent` animates transforms and skeletons, not pictures.
Phase 10 of `ALL_IN_ONE_STUDIO_ROADMAP.md` (vector studio, 9-slice) is a different, later tool and stays as planned.

**Decision (proposed)**: build the 2D tools in this order, each milestone usable on its own. Headless logic first, as for every other studio.

1. **Milestone A, `engine-sprite` (new, headless, depends on `engine-core`)**: a layered, multi-frame pixel image model (`SpriteDocument`: canvas size, RGBA layers,
   frames with durations, named tags such as "walk", palette, slices for 9-slice and pivot), a `.sprite` JSON file (cels as PNG base64, so files diff and merge),
   flatten and sprite-sheet export (PNG + JSON atlas), and import of PNG sheets and animated GIF. Reading Aseprite/LibreSprite `.ase` files is **optional later**
   (the format is documented; no code copied).
2. **Milestone B, `editor-sprite` (new, headless, depends on `editor-core`, `engine-sprite`)**: undoable edits through `EditorCommand`: pencil, eraser, line, rectangle,
   ellipse, flood fill, picker, selection (move, flip, rotate 90), mirror drawing, shading ramps, palette editing, layers (visibility, opacity, order), frames (add, copy,
   reorder, duration), tags, onion skin state, pixel-perfect line rule, tile mode drawing. One stroke is one command (as TD-018).
3. **Milestone C, desktop panel in `editor-desktop`**: a *Sprites* tab like LibreSprite: zoomable canvas with pixel grid, tool bar, palette, layers and timeline
   (frames x layers), onion skin, preview, save to `assets/`. Appears in the Content Browser as new asset type `SPRITE` (`.sprite`) with a thumbnail; double-click opens it.
4. **Milestone D, game side (`engine-scene`/`engine-render`)**: `SpriteAnimatorComponent` (sprite asset + tag, speed, loop, ping-pong; script and graph API `play("walk")`),
   `SpriteRendererComponent` can show one frame or a slice, 9-slice drawing, pixel-art texture filtering (nearest) and pixel-snapped cameras, sorting layers/order for 2D.
   Same behavior in the game runtime and the web player (`engine-babylon`).
5. **Milestone E, tile maps**: `TilemapComponent` (tile set from a sprite sheet, layers, collision shapes to Box2D, auto-tile rules), a tile painting tool in the
   viewport and a tile set editor. Later: 2D lights, parallax layers, 2.5D helpers (sprite billboards in 3D scenes, side-scroller camera rig preset).
6. **Milestone F, samples**: a pixel-art platformer and a 2.5D scene with billboard sprites, built with these tools.

**Web**: the web editor gets a port of C only after A to D work on the desktop; the web *player* gets D and E, since it must play the same scenes. Both read `.sprite`
files, so there is one format.

**Alternatives considered**: (a) embedding LibreSprite or Aseprite as an external tool: licensing (GPL / EULA), no undo or Content Browser integration, not
cross-platform inside our package; (b) only import PNG sheets and no editor: smallest, but no authoring, which was the request; (c) putting everything in `editor-core`:
breaks the module rules and the 3D-only builds.

**Consequences**: two new modules; new asset type and component serializers (round-trip tests, as AGENTS.md §7); format `.sprite` is a new public format and needs a
`schemaVersion`. Not covered: vector art (Phase 10), 3D-to-sprite rendering, spine-style 2D skeletal animation (possible later TD).

**Open questions for the owner**: (1) approve the module names `engine-sprite` and `editor-sprite`? (2) desktop first and web editor later, as above? (3) is Aseprite `.ase` import wanted in A?
(4) is tile map work (E) wanted right after the sprite editor, or should animation (D) come before the editor UI (C)?

**Implementation notes (2026-11-09, milestones A and B)**:
- **A, built**: module `engine-sprite` (headless): `SpriteDocument` (layers, frames with durations, whole-canvas cels, tags, slices with 9-slice borders and pivot, palette, canvas resize, flatten with layer opacity),
  `SpriteFile` (`.sprite` JSON, `schemaVersion` 1, one base64 PNG per non-empty cel, via `PixelPng`/javax.imageio), `SpriteSheet` (export: one PNG plus a JSON atlas with frames, tags and slices,
  optional columns and padding; import: cut a PNG into equal cells), `SpriteAnimation` (frame sequence of a tag, forward, reverse, ping-pong, and the frame at a time; for milestone D).
- **B, built**: module `editor-sprite` (headless): `PixelOps` (line, rectangle, ellipse, flood fill, pixel-perfect rule), `SpriteCommands` (`SetPixels` stores only changed pixels; `Snapshot` for structural edits),
  `SpriteSession` (pencil and eraser strokes, line, rectangle, ellipse, fill, picker, brush size, mirror X/Y, tile (wrap) mode, rectangle selection with delete, move, flip and rotate of a square, layers, frames,
  tags, slices, palette with optional recoloring, canvas resize, onion skin pixels, open and save). Every edit is one `CommandHistory` step.
- **Not built yet (honest list)**: animated GIF import, `.ase` import, shading ramps / dither ink, non-square rotation, linked cels, cel offsets (cels always cover the whole canvas), a tile-aware preview.
  Milestones C (panel), D (game components), E (tile maps), F (samples) are untouched; **nothing here is reachable by a user yet** (no panel, no asset type), so the status is **Implemented**, not Integrated.
- `CommandHistory.execute` swallows an exception thrown by a command and returns false, so `SpriteSession.edit` reports a refused change (removing the last layer) as "no change".

### TD-067: Game Modes: 2D, 2.5D and 3D Scenes in the Editor

**Status**: **Approved** (2026-11-09). The owner asked for "the modes screens, 2D mode, 2.5D mode and 3D mode", and chose, out of three offered designs, "mode per scene + switchable workspace",
and "mode screens first, then the Sprites tab inside them". Implemented in part (see the notes); nothing here has been seen running.

**Context**: the editor only had a 2D/3D view toggle (`ViewportMode`) and always started in the four-pane 3D layout. Nothing told the editor what kind of game a scene is, so a 2D game showed 3D
physics and lights in its Add Component menu, and a 2.5D game (a 3D world from the side, as in four samples) could be orbited out of its intended view.

**Decision**:
- Each scene has a **game mode**: `GameMode` (`MODE_2D`, `MODE_2_5D`, `MODE_3D`) in `engine-scene`, saved as the optional `gameMode` field of the scene file (`"2d"`, `"2.5d"`, `"3d"`; schema version unchanged,
  files without it still load; an unknown value is refused). A scene without one gets its mode inferred: 3D when it holds a model, mesh, 3D body, collider or character, else 2D (`GameModeProfile.resolve`).
- `GameModeProfile` (`editor-core`) states what a mode changes: the scene view (2D: flat XY view; 2.5D and 3D: the perspective view), whether the 3D view is **locked to a side view** (2.5D: the camera looks along -Z,
  drags pan instead of orbit or fly, the wheel zooms), and which Add Component categories are hidden (2D hides Physics 3D, Rendering 3D and Lighting; 2.5D and 3D hide Physics 2D).
- `EditorState.getGameMode/setGameMode/newScene(name, mode)` and the listener `onGameModeChanged`; opening a scene switches the view to its mode (not during Play, which picks its view from the game camera).
- Editor: a **2D | 2.5D | 3D strip** in the toolbar, the **mode screen** (File > New Scene, the "New" button: three cards, each with what the mode gives and a button that starts a new scene in it),
  File menu entries "New 2D / 2.5D / 3D Scene", and the side-view lock follows the mode. The 32 sample scenes now carry their mode.
- Changing the mode of the open scene is **not an undo step** (like renaming a scene) and removes nothing; components that the new mode hides stay on their entities and keep working.

**Not done**: the 2.5D view still shows the four panes (the perspective pane is the locked one; the three axis panes stay), a 2D-specific layout with the Sprites and Tile Map panels (milestones C and E of TD-066 will
add them to this mode), mode-specific Create menu entries (the 3D primitive entries are still offered in 2D), a start screen at launch, and mode-aware project templates.

**Alternatives**: a start screen only (no saved mode, nothing filtered); three fixed workspaces independent of the scene.

**TD-066 implementation notes (2026-11-09, milestone C)**:
- **C, built**: the **Sprites** tab of the editor (`editor-desktop/.../ui/sprite`: `SpriteStudioPanel`, `SpriteCanvasWidget`, `SpritePreviewWidget`, `ColorSwatch`) and the asset type `SPRITE` (`.sprite`) in the Content Browser,
  with a thumbnail (`SpriteThumbnails`: first frame, flattened) and double-click to open in the tab. Pointer handling is headless and tested (`SpriteToolController` in `editor-sprite`: pencil, eraser, line, rectangle,
  ellipse with preview and one undo step per shape, fill, picker, select and move). The tab has: tools and options (filled, mirror X/Y, tile, pixel perfect, onion skin, brush size), selection actions (delete, flip,
  rotate), a canvas (zoom with the wheel, pan with middle or right drag, grid from 8x zoom, checkerboard under transparency), palette and a hex color field, layers (visibility, select, reorder), frames (new, copy,
  delete, move, duration in ms), tags (name a range from the current frame to the end, preview one tag), a preview that plays the animation, New (width x height), Open (list of `sprites/*.sprite`), Save, Export PNG
  (sheet PNG and `.sheet.json` atlas next to the sprite, listed as a texture) and Import Sheet (a PNG named like the sprite, cut into frames of the current canvas size). Sprites live in the project's `sprites/` folder.
- **Behaviors to know**: Save always writes `sprites/<name>.sprite` from the Name field; closing the editor saves an unsaved sprite the same way (no prompt, like the graph and Timeline). The tab is always there, in every
  game mode; it is not yet tied to the 2D / 2.5D modes of TD-067. The tab's `Undo` and `Redo` buttons act on the sprite only; the editor's scene undo keys are not wired to the sprite.
- **Not done in C**: keyboard shortcuts in the tab, a color picker dialog (hex field and palette only), tag editing beyond "from the current frame to the end" and delete, drag to reorder frames, a tile-aware preview, sprite
  assets dropped into a scene (milestone D adds the component), non-square rotate, GIF and `.ase` import. Mouse and keyboard use was **not tried by a person**; a screenshot of the real editor with a demo sprite (testing aid
  `GDXUNITY_EDITOR_SPRITE_DEMO=1` with `GDXUNITY_EDITOR_TAB=Sprites`) shows the canvas, layers, frames, tags, palette and preview drawing correctly.

**TD-066 implementation notes (2026-11-09, milestone D, desktop part)**:
- **D, built on the desktop** (editor and game runtime): `SpriteAnimatorComponent` (`engine-render`): a sprite asset (`.sprite`), the tag that plays first, speed, loop, play on start, flip X and Y, drawn size (0 = the sprite's own size),
  tint, centered. It draws in the scene's 2D pass (so in 2D and in 2.5D scenes, with the depth compositing of TD-063) from one texture of all flattened frames with **nearest filtering**, and loads its file itself on the first draw
  (`AssetService.resolve` is new). Playback logic is headless and shared: `SpriteAnimationState` in `engine-sprite`. Scripts reach it with `getComponent(SpriteAnimatorComponent.class)` and
  `play(tag)` (continues if that tag already plays, so it may be called every frame), `play(tag, restart)`, `pause`, `resume`, `stop`, `setSpeed`, `isFinished`, `getFrame`, `getCurrentTag`; an unknown tag throws and names the sprite's tags.
  Also: serializer (registered in `GameComponentSerializers`, so the game runtime plays it), Add Component entry "Sprite Animator" (uses the sprite selected in the Content Browser), a Details editor (assign selected sprite, tag, speed, loop, play on start, flips,
  size, tint, all undoable), dropping a `.sprite` from the Content Browser into the scene, duplicating an entity keeps it, sample scene `sprite-animation-2d` with the bundled `assets/sprites/hero.sprite` (idle and walk tags; the file is built in code
  and compared by `BundledSpritesTest`).
- **Not done in D (honest list)**: the **web player** (it has no Sprite Renderer at all yet, so a web sprite path has to be built first), **9-slice drawing** (the slice data exists in `.sprite` but nothing draws it), a pixel-art filter option for the plain Sprite Renderer,
  pixel-snapped cameras, 2D sorting layers, graph nodes for sprite playback (scripts only), billboard sprites that face a 3D camera (2.5D scenes draw them as flat quads in the XY plane), a frame-change callback.
- Seen running: the game runtime played `sprite-animation-2d` (smoke test 240 frames, 0 runtime errors); the screenshot shows three heroes on different frames, sharp pixels, one flipped and tinted. The editor viewport with the component was not looked at.

**TD-066 implementation notes (2026-11-09, milestone E, desktop part)**:
- **E, built on the desktop** (editor and game runtime): a **tile set is a `.sprite`** (each frame is a tile; tile id = frame index + 1; 0 is an empty cell), so the Sprites tab is also the tile set editor. `TileMapData` (`engine-sprite`, headless):
  layers of cells, solid tiles, **auto-tile rules** (`AutoTileRule`: 16 tiles by neighbour mask, north 1, east 2, south 4, west 8; painting refits the cell and its neighbours, erasing refits the neighbours), solid cells **merged into
  boxes** (`solidBoxes`, greedy rows then down), resize, deep copy. `TilemapComponent` (`engine-render`): draws the visible layers from one nearest-filtered texture (`FrameAtlas`), the entity's position is the map's bottom left corner, a cell is
  tile width x height scene units times the entity's scale, and it is a new `StaticColliderSource2D` (`engine-scene`): `PhysicsSystem2D` makes one static body for the entity with one box fixture per merged box when the simulation starts. Serializer registered in
  `GameComponentSerializers` (so the game runtime plays it). Add Component entry "Tilemap" (uses the selected `.sprite` as tile set), a Details editor (tile set, cell size, solid tiles collide, tint), duplicating keeps the map.
- **Editor tab "Tiles"** (`TilemapStudioPanel`, `TilemapCanvasWidget`, headless `TilemapSession`): follows the selected entity's Tilemap; palette of the tile set, tools paint, erase, fill, rectangle, pick (strokes are one undo step), layers (add, remove, show or hide,
  clear), Solid toggle for the selected tile and Collides for the map, auto-tile rule from the selected tile and the 15 after it ("Rule from 16 tiles"), map size, undo and redo (the tab has its own history), solid tiles tinted red on the canvas.
- Sample: `samples/tilemap-platformer-2d.json` (20 x 12 level, auto-tiled ground and platforms on a brick layer, two sprite heroes that fall onto the platforms) with the bundled `sprites/tiles.sprite` (an auto-tile set plus a brick); both are built in code and checked
  by `BundledSpritesTest` and `BundledTilemapSceneTest` (the second also runs the Box2D world: the heroes come to rest on the low and the high platform).
- **Not done in E (honest list)**: **painting in the scene viewport** (the planned "tile painting tool in the viewport"; painting is in the Tiles tab only), **the web player** (no tile map there), a way to edit an auto-tile rule's 16 tiles one by one (only "16 tiles from the selected one"),
  tile flipping, animated tiles, per-tile properties other than solid, one-way platforms, collision changes while the game runs (the boxes are made when the simulation starts), culling of off-screen cells (every cell of a visible layer is drawn), isometric or hex grids,
  and tiles in 2.5D scenes beyond drawing in the XY plane. Painting with the mouse and the tab's buttons was **not tried by a person**.
- Seen running: the game runtime played the sample (smoke test 300 frames, 0 runtime errors; screenshot: auto-tiled ground with grass and dark edges that join up, heroes standing on the platforms); the editor's Tiles tab with the Level selected rendered the map, palette, tools, layers and rule list (smoke test, 0 errors; screenshot).

**TD-066 implementation notes (2026-11-09, closing the D and E gaps, and milestone F)**:
- **Web player** (`engine-babylon`): `SpriteAnimatorComponent` and `TilemapComponent` now play in the browser. `src/sprites/sprite.ts` (pure: reads `.sprite`, flattens frames with the desktop's blend arithmetic, playback state with the same rules, atlas,
  tile map merged boxes, tile quads), `src/runtime/SpriteBinding.ts` and `SpriteLoader.ts` (sprite files load after the scene is built, like models; one RawTexture per file, nearest filtering; a failed file is reported and the rest shows),
  `Physics2D` makes one static body with the merged boxes for a tile map, `entity.sprite` gives scripts `play(tag, restart)`, `pause`, `resume`, `stop`, `setSpeed`, `setFlipX/Y`, `frame`, `finished`. `npm run sync-samples` copies `assets/sprites` to `public/sprites`.
  **A real browser run found two defects the unit tests could not** (the whole atlas shown on each sprite because a double sided plane has twice the vertices the UV update wrote; colors washed to white because an emissive texture is added to the emissive color), both fixed;
  transparent layers are now sorted by `alphaIndex` (entity order, then layer) because flat layers tie on camera distance. A faint hairline between tile columns remains visible in headless Edge (cosmetic).
- **9-slice** (desktop): `NineSlice` (`engine-sprite`: the nine pieces for any size, borders shrink when the size is smaller than they are), `SpriteAnimatorComponent.slice` (draws the named bordered slice stretched to the component's size; no rotation or flip), a "9-slice (slice name)" field in Details, and a
  **Slices row in the Sprites tab** ("Slice from selection" with left, right, top, bottom borders). Not on the web yet.
- **Painting in the 2D scene view** (desktop): `TilePaintInteraction` (`editor-sprite`) wraps the 2D view; with "Paint in view" on in the Tiles tab and a tile map selected, the left button paints with the tab's tool, tile, layer and rule (one undo step of the tile session per stroke), every other input goes to the ordinary view.
  Not in the 3D or 2.5D views; strokes undo with the Tiles tab's Undo, not the editor's.
- **Milestone F, samples**: `pixel-adventure-2d` (2D, orthographic: a 40 x 12 tile level with pits and platforms, six spinning coins, a hero that runs, jumps, turns and plays "idle" and "walk") and `sprite-25d` (2.5D, perspective camera: the same level and hero in a 3D world with hills behind and pillars in front of the 2D plane).
  The hero script `samples.pixel.PixelHero` exists in Java (`assets/scripts/samples/pixel/PixelHero.java`) and TypeScript (`engine-babylon/src/scripts/pixel.ts`); movement is hand written against `TilemapComponent.isSolidAt` (and `tilemapSolidAt` on the web), like the other platformer samples.
  `sprites/coin.sprite` is new. Scenes and sprites are built in code and checked by `BundledPixelScenesTest` and `BundledSpritesTest`.
- **Still not done**: graph nodes for sprite playback and tile painting (scripts only), web 9-slice, a tile-by-tile auto-tile rule editor, Aseprite and GIF import, pixel-snapped cameras, 2D sorting layers, tile flips and animated tiles, shading and dither ink in the Sprites tab,
  mouse and keyboard use of the Sprites and Tiles tabs by a person, hearing the sample sounds.

### TD-068: Blueprint-Style Visual Scripting: Editable Sample Graphs, Component Nodes, Functions, Variables Panel, Live Debugging

**Status**: **Approved** — explicitly confirmed by the project owner on **2026-11-10**: "approve TD-068". This confirms the decision's desktop and Babylon web scope and the revised dependency order in the implementation notes below. The earlier 2026-11-09 approval ("approve TD-08") was interpreted as referring to TD-068; this explicit confirmation removes that ambiguity. Earlier status: **Proposed**. Milestone 1 started. The owner said: "I can't edit the sample visual scripts, I want it to work like blueprints from Unreal Engine", and asked for all four extensions
offered: components as nodes, functions / macros / custom events, a variables panel with defaults, live debugging.

**Next milestone (confirmed direction, 2026-11-10): Visual Scripting Reliability and Component Nodes.** Restore the test baseline (Kotlin numeric conversion and graph-enabled shooter test setup), then add curated component nodes and bounded loop support with desktop/Babylon behavioral parity tests. Convert one further sample only when its graph preserves the Java reference behavior; verify graph editing, undo/redo, save/reopen and Play in the running editor, and record acceptance evidence. Functions/macros/custom events, the variables panel and live debugging follow as separate milestones. Approval records the direction and scope; it does not claim implementation or validation of these deliverables.

**Context** (checked in the repository): visual scripting exists (TD-029, TD-044, TD-045, TD-060, TD-065): event, flow, math, input, physics and Java-method nodes, typed pins, a graph editor tab with Examples,
code generation to Java, Kotlin and TypeScript, and a web interpreter. **But only 1 of the 34 sample scenes (`visual-scripting-3d`) runs a graph.** The other samples run Java scripts (`EntityScriptBindingComponent`), which cannot be opened as nodes;
the 15 example graphs in `assets/graphs/samples` are reachable only from the Examples menu of the Visual Scripting tab, not from the scenes. So opening a sample and looking for its script finds code, not a graph.
Nodes cannot yet touch components (a Sprite Animator's `play`, a Tilemap, the camera), there are no functions or macros, variables are edited only in the graph, and a running graph shows which node ran (`GraphExecutionTrace`) but not values.

**Proposal, in this order, each milestone usable on its own**:
1. **Samples run their graphs** (the answer to the complaint). Every sample that has an example graph (15 of them) is switched to a Graph Script bound to that graph, so selecting the entity and pressing *Open Graph* shows editable nodes; the Java script stays in `scripts/samples` as the reference and
   a *Use Java script instead* switch. Where a graph and its Java sample differ in behavior, the graph is fixed first (a test plays both and compares positions, as `GraphPlayModeTest` does for the one existing sample). The pixel hero (this session's sample) gets a graph too.
   Needs: missing nodes found while converting (likely sprite and tile map calls, collectables, text) become built-in nodes. Web: the web player already runs graphs; the converted samples must keep running there (the existing web sample test builds every sample).
2. **Component nodes**. Nodes generated from a component's public API: *Get Component* (entity + type -> object pin), property get and set, and function calls (`Sprite Animator: Play`, `Tilemap: Is Solid At`, `Audio Source: Play`, `Camera`, `Text: Set Text`), chosen from a Components category in the node menu.
   Builds on the Java-method nodes (TD-060 `ClassCatalog`): a curated allow-list of engine components, so graphs cannot reach editor or file APIs. Web: the same list with TypeScript implementations, checked against recorded Java results like TD-065.
3. **Functions, macros and custom events**. A function is a sub-graph with typed inputs and outputs, called by a node; a macro is expanded in place; a custom event has parameters and is fired by a node. Needs a `.graph` format extension (schema version bump with a reader for version 1),
   validator and interpreter support, and code generation (or a clear refusal) for the three languages.
4. **Variables panel and defaults**. A *My Blueprint* style panel in the tab: variables with type and default, drag onto the canvas for get / set nodes; each Graph Script component lists the graph's variables in Details so an entity overrides the default (stored in the component).
5. **Live debugging**. While the game plays: executed wires pulse, pin values show beside the pins, and breakpoints on nodes pause the game (the Pause state exists) with *Step* to run to the next node. A trace of the last values is kept per node.

**Out of scope**: replacing Java and Kotlin scripts (TD-019 stays), a Blueprint-style *class* inheritance model, networking and replication, a graph for materials or animation state machines.

**Risks and costs**: every new node type needs desktop and web implementations and a recorded-result test; the `.graph` format change in milestone 3 must not break existing files; converting 15 samples to graphs changes what users see by default, so the Java scripts must stay one click away.

**Open question for the owner**: what exactly failed? (a) there was no graph to open for a sample, (b) a graph opened but could not be edited, or (c) both. (a) is what the repository shows; for (b) a reproduction would help, because the graph editor was tested headless and its mouse use was never checked by a person.

**TD-068 implementation notes (2026-11-09, milestone 1, started)**:
- **Finding that changes the order**: of the 15 example graphs only **`ShooterShip`** reproduces its whole Java script (`examples.json`, "notCovered"). The other 14 leave out rules (gravity and collision against boxes, coins, score text, goals, restart, enemies), because the nodes for them do not exist:
  there are no loops, no functions and no component nodes. Switching those scenes to their graphs now would remove behavior from the samples, so they stay on Java until milestones 2 and 3 provide the nodes. **Milestone 1 is therefore finished sample by sample, after the nodes it needs**;
  the rest of milestone 1's list moves behind milestones 2 (component nodes, plus loop nodes, which the proposal did not name) and 3 (functions).
- **Done**: `shooter-2d` now runs the ship from `graphs/samples/ShooterShip.graph` (a Graph Script component; no Java script on the ship; the game rules stay Java), on the desktop and on the web (`sync-samples` now copies `assets/graphs/samples` to `public/graphs/samples`).
  `SampleGraphsTest` checks the wiring; `GraphExamplesTest` (desktop) and `javaMethodNodes.test.ts` (web) already fly the ship from the graph. The Java `ShooterShip.java` stays in `scripts/samples/shooter` as the reference.
- Seen: the game runtime played the shooter with the graph (240 frames, 0 errors). Editing the graph in the Visual Scripting tab with the mouse was **not tried by a person**.

**TD-068 implementation notes (2026-11-10, component-node milestone):** Curated typed component lookup/API nodes and bounded synchronous Repeat are implemented in the desktop and Babylon interpreters. Breakout's paddle now reads/caches its actual Shape Renderer width in its graph; the Java paddle remains the reference and the ball/game rules stay Java. Kotlin numeric widening and graph initialization in the sample test harness were corrected. [Scope, limits and manual checklist](milestones/td-068-component-nodes.md); [test and acceptance evidence](reports/td-068-component-nodes-report.md). No new module, dependency, graph schema version or scripting runtime is introduced. This note does not approve another milestone or claim that manual acceptance has passed.
