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

## Future Decisions

The following decisions will be made in future milestones:

- Physics engine selection (Later milestone)
- Animation system design (Later milestone)

## Decision Review Process

1. Document context and problem
2. Identify alternatives
3. Evaluate consequences of each alternative
4. Make decision with rationale
5. Record in this document and create ADR
6. Review periodically for relevance
