# Roadmap

## Milestone 0 — Foundation and Architecture ✅

**Status**: In Progress

**Objective**: Establish build system, module structure, and architecture documentation.

**Acceptance Criteria**:
- [x] Gradle multi-module structure created with engine-core, engine-scene, engine-render, engine-desktop
- [x] Java 21 compatibility configured and verified
- [x] Unnecessary dependencies removed (Fleks, unused KTX libraries)
- [x] JUnit 5 test framework configured
- [x] Build succeeds: `./gradlew build`
- [ ] Desktop launcher runs without errors: `./gradlew engine-desktop:run`
- [ ] Documentation created:
  - [ ] docs/PROJECT_VISION.md
  - [ ] docs/ARCHITECTURE.md
  - [ ] docs/ROADMAP.md
  - [ ] docs/TECHNICAL_DECISIONS.md
  - [ ] docs/TEST_STRATEGY.md
- [ ] Module dependency diagram documented
- [ ] Architecture decision record (ADR) template defined

**Estimated effort**: 2-4 hours

---

## Milestone 1 — Minimal Engine Runtime ✅

**Status**: Complete

**Objective**: Implement basic engine lifecycle and rendering.

**Acceptance Criteria**:
- [x] Engine lifecycle interface defined (create, resize, render, pause, resume, dispose)
- [x] Update loop with delta time implemented
- [x] Resource disposal framework established
- [x] Simple geometric object (e.g., colored rectangle) can be rendered
- [x] Window can be resized without crashing
- [x] Automated tests cover foundational logic
- [x] Desktop runtime launches and displays rendered content (verified with PrimitiveRenderer)

**Key Tasks**:
- Implement EngineApplication with full lifecycle (completed with explicit EngineState state machine)
- Implement update loop with delta time calculation (completed with FrameClock and TimeProvider)
- Create resource disposal framework with ownership tracking (completed with LIFO ResourceManager)
- Implement basic renderer with primitive geometry (completed with PrimitiveRenderer)
- Add unit tests for lifecycle and resource management (21 automated JUnit 5 tests passing)
- Verify desktop launcher runs successfully (DesktopLauncher wired to PrimitiveRenderer)

**Estimated effort**: 4-6 hours

---

## Milestone 2 — Scene System ✅

**Status**: Complete

**Objective**: Implement entities, components, transforms, and scene management.

**Acceptance Criteria**:
- [x] Scene creation and disposal working
- [x] Entity creation with stable UUID-based IDs
- [x] Component attachment/detachment working
- [x] Transform hierarchy with parent-child relationships
- [x] World transform calculation with dirty flag optimization
- [x] Basic scene switching (load/unload scenes via SceneManager)
- [x] Versioned JSON save/load for scenes (SceneSerializer schemaVersion 1)
- [x] Stable IDs persist through serialization
- [x] Serialization tests for schema validation

**Key Tasks**:
- Design and implement Entity class with UUID (completed in Entity.java)
- Design and implement Component interface and base classes (completed in Component.java, BaseComponent.java)
- Implement Transform class with hierarchy support (completed in Transform.java with cycle prevention and dirty propagation)
- Implement Scene class with entity lifecycle (completed in Scene.java with deferred mutation support)
- Design JSON schema with version field (schemaVersion 1 with LibGDX Json)
- Implement scene serialization and deserialization (SceneSerializer.java and ComponentSerializerRegistry.java)
- Add comprehensive unit tests for entity/component system (23 tests in engine-scene passing)
- Add serialization tests with schema validation (round-trip, schema mismatch, duplicate IDs, missing parents)

**Estimated effort**: 8-12 hours

---

## Milestone 3 — Basic Rendering and Camera ✅

**Status**: Complete

**Objective**: Implement camera system and integrate with transform hierarchy.

**Acceptance Criteria**:
- [x] Camera controller implemented (OrthographicCamera2D position, translation, zoom, and viewport control)
- [x] Camera can be positioned and rotated
- [x] Primitive geometry rendering (ShapeRendererComponent with configurable dimensions, color, shape type, and centering)
- [x] Object transforms affect rendering position (entity world matrix applied directly to ShapeRenderer / SpriteBatch)
- [x] Viewport resizing handled correctly (FitViewport aspect ratio preservation)
- [x] Clear separation between 2D and 3D rendering boundaries defined (SceneRenderer2D and OrthographicCamera2D)
- [x] Rendering tests verify transform-to-render integration

**Key Tasks**:
- Implement Camera class with position, rotation, zoom (completed in Camera.java and OrthographicCamera2D.java)
- Implement camera controller for user input (camera translation/zoom methods and viewport resize handling)
- Create primitive geometry renderer (completed in ShapeRendererComponent.java and SceneRenderer2D.java)
- Integrate transform system with rendering (completed via entity.getTransform().getWorldMatrix() in renderables)
- Handle viewport resize events (FitViewport aspect ratio preservation tested and verified)
- Define 2D/3D rendering API boundaries (established in SceneRenderer2D and Renderable contracts)
- Add rendering integration tests (11 tests in engine-render passing, 53 tests workspace-wide)

**Estimated effort**: 6-8 hours

---

## Milestone 4 — First Editor Prototype ✅

**Status**: Complete

**Objective**: Implement basic editor UI with Scene View, Hierarchy, Inspector, and Console.

**Acceptance Criteria**:
- [x] Separate editor application launches (`:editor-desktop:run` on 1024x720 window)
- [x] Scene View displays rendered scene (reuses `SceneRenderer2D` and `OrthographicCamera2D`)
- [x] Hierarchy panel shows entity tree (indented tree view with active/inactive indicators)
- [x] Inspector panel shows selected entity properties (UUID, name, active state, local/world transforms)
- [x] Basic object creation (create entity with `+ Create Entity` button and `- Delete` button)
- [x] Property editing in Inspector (name, active toggle, local position, rotation Euler angles, scale)
- [x] Console displays logs, warnings, errors (EditorStatusBar status bar at bottom with error styling)
- [x] Editor can save and load scenes (New, Save, Load in EditorToolbar using `SceneSerializer`)

**Key Tasks**:
- Create editor-core module with editor state (completed in EditorState, ToolMode, EntityInspectorModel, ViewportPicker)
- Create editor-desktop module with UI framework (completed in EditorApplication, EditorLauncher, scene2d UI layout)
- Implement Scene View with LibGDX integration (completed with camera pan, zoom, reset, and viewport entity picking)
- Implement Hierarchy panel with tree view (completed in HierarchyPanel.java)
- Implement Inspector panel with property editors (completed in InspectorPanel.java)
- Implement Console with log capture (completed in EditorStatusBar.java)
- Implement basic object creation tools (completed in EditorState and HierarchyPanel)
- Integrate with scene serialization (completed in EditorToolbar and EditorState)

**Estimated effort**: 16-24 hours

---

## Milestone 5 — Editor/Runtime Integration & Play Mode ✅

**Status**: Complete

**Objective**: Implement Play, Pause, Stop with runtime state isolation.

**Acceptance Criteria**:
- [x] Play mode launches runtime instance (creates isolated cloned runtime scene using versioned serializer)
- [x] Pause mode freezes runtime (simulation updates halt while viewport and inspection remain active)
- [x] Stop mode terminates runtime and restores editor state (disposes runtime scene, restores authored scene and selection)
- [x] Runtime state is isolated from editor state (runtime mutations do not mutate authored transforms, components, or hierarchy)
- [x] Scene reload and reset working (two-way state reconciliation on play and stop)
- [x] Error reporting and diagnostics in editor (status bar displays simulation state, unsupported component warnings, and errors)
- [x] Changes in Play mode do not affect editor scene (verified via automated headless tests and state invariants)

**Key Tasks**:
- Implement PlayModeState enum and PlayModeController in editor-core
- Implement scene cloning via versioned SceneSerializer with component registry validation
- Add Play, Pause, and Stop toolbar controls and state indicators in EditorToolbar
- Gate destructive edit actions (create, delete, new, save, load) during Play/Pause mode
- Safely update runtime simulation during editor render loop with delta sanitization and clamping
- Ensure safe runtime disposal and authored scene restoration on Stop
- Add comprehensive headless unit tests in editor-core (PlayModeControllerTest: 9 tests)

**Estimated effort**: 8-12 hours

---

## Milestone 6 — Visual Transform Gizmos and Viewport Interaction ✅

**Status**: Complete

**Objective**: Extend the desktop editor with interactive, viewport-based Move, Rotate, and Scale gizmos.

**Acceptance Criteria**:
- [x] Move gizmo implemented (horizontal X axis, vertical Y axis, and center-plane handle)
- [x] Rotate gizmo implemented (2D rotation ring around Z axis with degree tracking)
- [x] Scale gizmo implemented (X-axis, Y-axis, and uniform-scale handles with positive MIN_SCALE enforcement)
- [x] Gizmo interaction priority over entities behind handles
- [x] Input cancellations on tool switch, selection change, or mouse release
- [x] Parented entities transform correctly without world displacement distortions
- [x] Synchronized two-way updates with the Inspector Panel
- [x] Full compatibility with Play/Pause/Stop scene isolation
- [x] Headless automated unit tests passing (TransformGizmoTest: 7 tests)

**Key Tasks**:
- Implement GizmoHandle enum and TransformSnapshot in editor-core
- Implement TransformGizmoMath for pure mathematical hit-testing, move projection, rotation delta, and scale clamping
- Implement GizmoInteractionController with automatic cancellation on tool or selection changes
- Implement TransformGizmoRenderer in editor-desktop using ShapeRenderer with camera scale adaptation
- Wire gizmo hit-testing and drag updates into EditorApplication's ViewportInputProcessor
- Add Rotate and Scale buttons to EditorToolbar
- Synchronize InspectorPanel via onEntityTransformModified
- Add 7 headless unit tests in editor-core (TransformGizmoTest)

**Estimated effort**: 8-12 hours

---

## Milestone 7 — Asset Pipeline and Asset Browser Foundation ✅

**Status**: Complete

**Objective**: Implement persistent asset references, filesystem cataloging, centralized resource management, sprite asset serialization, and desktop asset browser.

**Acceptance Criteria**:
- [x] Asset reference system implemented (normalized relative paths, traversal and absolute path protection)
- [x] Pure filesystem discovery catalog (`AssetCatalog`) without eager GPU texture loading
- [x] Resource caching with centralized loading and single-point disposal (`AssetService`)
- [x] Texture format allowlist (`.png`, `.jpg`, `.jpeg`) with deterministic sorting
- [x] `SpriteRendererComponent` stores persistent `AssetReference`
- [x] Versioned JSON serialization via `SpriteRendererComponentSerializer`
- [x] Asset Browser UI (`AssetBrowserPanel`) in editor desktop shell
- [x] Asset assignment workflow in `InspectorPanel`
- [x] Safe Play Mode isolation without resource collision or double disposal
- [x] Automated tests covering reference validation, cataloging, serialization, and editor tracking

**Key Tasks**:
- Implement `AssetReference`, `AssetType`, `AssetMetadata`, and `AssetCatalog` in `engine-core`
- Implement `AssetService` GPU texture lifecycle management
- Integrate `AssetReference` into `SpriteRendererComponent` and `SceneRenderer2D`
- Implement `SpriteRendererComponentSerializer` and register in editor serializer
- Create `AssetBrowserPanel` in `editor-desktop`
- Add sprite inspection and asset assignment in `InspectorPanel`
- Add headless unit tests in `engine-core`, `engine-render`, and `editor-core`

**Estimated effort**: 12-16 hours

---

## Milestone 8 — Editor Reliability, Undo/Redo, and Scene Workflow ✅

**Status**: Complete

**Objective**: Implement an undo/redo command architecture, integrate it into editor operations (transforms, entities, inspector, asset assignment, gizmos), and improve scene workflow safety.

**Acceptance Criteria**:
- [x] Command interface (`EditorCommand`) and history manager (`CommandHistory`) implemented
- [x] Bounded history limit, redo stack clearing on new edit, and recursive execution protection
- [x] Reversible operations: entity creation, deletion (with full component/transform preservation), rename, active toggle, transform edits, and sprite asset assignments
- [x] Gizmo integration: single undoable command recorded upon drag completion (skipping no-ops and cancellations)
- [x] Inspector edits and Asset Browser assignments routed through command architecture
- [x] UI integration: Undo and Redo buttons in EditorToolbar with dynamic labels and disabled states; keyboard shortcuts (Ctrl+Z, Ctrl+Y, Ctrl+Shift+Z)
- [x] Scene safety: history is cleared on scene replacement/reset to prevent stale mutations
- [x] Play Mode isolation: runtime mutations do not pollute authored undo history; undo/redo disabled while playing
- [x] Shared assets preserved: undoing sprite assignment does not dispose or duplicate shared GPU textures
- [x] Comprehensive automated unit tests (95 passing tests across all modules)

**Key Tasks**:
- Implement `EditorCommand` and `CommandHistory` in `editor-core`
- Implement concrete command implementations (`CreateEntityCommand`, `DeleteEntityCommand`, `RenameEntityCommand`, `ChangeEntityActiveCommand`, `ChangeTransformCommand`, `AssignSpriteAssetCommand`, `AddComponentCommand`)
- Wire commands into `EditorState`, `HierarchyPanel`, `InspectorPanel`, and `GizmoInteractionController`
- Add Undo/Redo controls to `EditorToolbar` and keyboard shortcuts to `EditorApplication`
- Write automated test suite in `editor-core`

**Estimated effort**: 8-12 hours

---

## Milestone 9 — Java/Kotlin Scripting Foundation ✅

**Status**: Complete

**Objective**: Implement a scripting foundation enabling users to attach Java or Kotlin scripts to scene entities, compile them, diagnose errors, execute lifecycle callbacks in Play Mode, and persist script configuration in saved scenes.

**Acceptance Criteria**:
- [x] Shared script base API (`ScriptComponent`) for Java and Kotlin scripts
- [x] Full lifecycle hooks: `onCreate`, `onStart`, `onUpdate`, `onFixedUpdate`, `onEnable`, `onDisable`, `onDestroy`
- [x] Java script compilation via `javax.tools.JavaCompiler` with structured diagnostics (`ScriptDiagnostic`)
- [x] Kotlin script compilation via embedded `K2JVMCompiler` targeting JVM 21
- [x] Isolated dynamic class loader (`ScriptClassLoader`) for compiled script classes
- [x] Entity script attachment (`EntityScriptBindingComponent`) supporting Java and Kotlin
- [x] Scene persistence: versioned JSON serialization preserving script path, class name, language, and enabled state
- [x] Play Mode integration: script compilation validation, runtime instance creation on play, update invocation, and safe cleanup on stop
- [x] Editor integration: "Compile Scripts" toolbar action, script inspection in InspectorPanel, and "+ Attach Script" action
- [x] Comprehensive automated test coverage (101 passing tests across all modules)

**Key Tasks**:
- Implement `ScriptComponent`, `ScriptLanguage`, `EntityScriptBindingComponent`, and serializer in `engine-scene`
- Implement `JavaScriptCompiler`, `KotlinScriptCompiler`, `ScriptClassLoader`, and `ScriptManager` in `editor-core`
- Wire script compilation and lifecycle into `PlayModeController` and `EditorState`
- Update `InspectorPanel` and `EditorToolbar` in `editor-desktop`
- Write automated tests in `engine-scene` and `editor-core`

**Estimated effort**: 12-16 hours

---

## Later Milestones

### Physics
- Physics integration (Box2D or similar)
- Collision detection and response
- Physics components
- Physics debugging tools

### Animation
- Animation system
- Skeletal animation
- Animation blending
- Animation editor

### Audio
- Audio system
- Sound and music playback
- Audio components
- Audio mixing

### Advanced Rendering
- Shaders and materials
- Lighting system
- Post-processing effects
- Particle systems

### Custom Editor Extensions
- Plugin system for editor tools
- Custom inspectors
- Custom windows
- Scripting integration

### Game Packaging
- Build pipeline for distributable games
- Asset bundling
- Platform-specific packaging
- Installer generation

### Additional Platforms
- Android support
- iOS support
- Web support (GWT)

---

## Milestone Completion Criteria

Each milestone is complete only when:

1. All acceptance criteria are satisfied
2. All automated tests pass
3. Documentation is updated
4. No regressions in previous milestones
5. Code review approved
6. Milestone completion report created

## Risk Mitigation

- **Technical risks**: Prototype complex features before full implementation
- **Scope creep**: Strict adherence to milestone acceptance criteria
- **Quality issues**: Mandatory automated tests for all features
- **Documentation debt**: Update documentation with each milestone
