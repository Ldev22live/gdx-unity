# Architecture

## Module Structure

### Current Modules (Milestones 1–4)

```
engine-core/
  - Engine lifecycle interfaces
  - Application services
  - Logging utilities
  - Base interfaces for other modules

engine-scene/
  - Entity and component abstractions
  - Transform hierarchy
  - Scene management
  - Versioned JSON serialization (SceneSerializer)

engine-render/
  - Rendering abstractions
  - LibGDX rendering implementation
  - Camera system (Camera2D, OrthographicCamera2D, Viewport2D)
  - SceneRenderer2D and renderable components

engine-desktop/
  - Desktop runtime launcher
  - Platform-specific configuration

editor-core/
  - Editor state and selection model
  - Tool modes (Select, Move, Rotate, Scale)
  - EntityInspectorModel with transform/Euler conversion and validation
  - ViewportPicker 2D bounds picking

editor-desktop/
  - Desktop editor application shell and launcher
  - Programmatic scene2d.ui skin and layout
  - Hierarchy, Inspector, Toolbar, StatusBar, and AssetBrowser panels
  - Integrated SceneRenderer2D viewport with camera pan/zoom controls, visual gizmos, undo/redo, and script compilation controls

### Scripting Foundation Architecture (Milestone 9)

```
engine-scene (scripting)
  ├── ScriptComponent                 (Base class for Java/Kotlin scripts: onCreate, onStart, onUpdate, onFixedUpdate, onEnable, onDisable, onDestroy)
  ├── ScriptLanguage                  (Enum: JAVA, KOTLIN)
  ├── EntityScriptBindingComponent    (Entity component holding scriptPath, scriptClassName, language, and runtime instance)
  └── EntityScriptBindingComponentSerializer (Versioned JSON serialization of script attachments)

editor-core (scripting)
  ├── JavaScriptCompiler              (Compiles .java scripts via javax.tools.JavaCompiler with full diagnostics)
  ├── KotlinScriptCompiler            (Compiles .kt scripts via embedded K2JVMCompiler targeting JVM 21)
  ├── ScriptClassLoader               (Isolated URLClassLoader for dynamically compiled script bytecodes)
  ├── ScriptDiagnostic                (Diagnostic record: Kind, sourceFile, lineNumber, columnNumber, message)
  ├── CompilationResult               (Result record: success, diagnostics, outputDirectory)
  ├── ScriptManager                   (Orchestrates scanning, compiling, discovering classes, instantiating, and Play Mode binding)
  └── PlayModeController              (Triggers compileAll, initializes runtime instances on play, cleans up on stop)

editor-desktop (ui)
  ├── EditorToolbar                   ("Compile Scripts" button triggering manual script recompilation)
  └── InspectorPanel                  (Displays script class, language, enabled toggle, and "+ Attach Script" action)
```

### Command & Undo/Redo Architecture (Milestone 8)

```
editor-core (command)
  ├── EditorCommand              (Contract: execute(), undo(), getDescription())
  ├── CommandHistory             (Bounded undo/redo deque, redo invalidation, listener notifications)
  ├── CreateEntityCommand        (Creates/re-adds entity, manages selection)
  ├── DeleteEntityCommand        (Removes entity, preserves hierarchy/components/transform for full undo)
  ├── RenameEntityCommand        (Reversible entity renaming)
  ├── ChangeEntityActiveCommand  (Reversible active state toggling)
  ├── ChangeTransformCommand     (Captures before/after TransformSnapshots, skips no-ops)
  ├── AssignSpriteAssetCommand   (Reversible AssetReference assignment; texture lifecycle preserved)
  └── AddComponentCommand        (Reversible component addition)

editor-desktop (ui)
  ├── EditorToolbar              (Undo/Redo buttons with action descriptions and dynamic enabled states)
  ├── InspectorPanel             (Routes name, active, transform, and sprite edits through commands)
  └── EditorApplication          (Routes Ctrl+Z / Ctrl+Y / Ctrl+Shift+Z through ViewportInputProcessor)
```

### Asset Pipeline Architecture (Milestone 7)

```
engine-core (asset)
  ├── AssetReference         (Immutable, normalized relative path; strictly rejects traversal/absolute paths)
  ├── AssetType              (TEXTURE: png, jpg, jpeg; UNKNOWN)
  ├── AssetMetadata          (Record: AssetReference, AssetType, sizeBytes, lastModifiedMillis)
  ├── AssetCatalog           (Recursive filesystem scanner; deterministic sorting; pure metadata discovery)
  └── AssetService           (Disposable GPU resource cache; single-point decoding & unloading)

engine-render (components & serialization)
  ├── SpriteRendererComponent (Stores AssetReference; decoupling entity from raw Texture lifecycle)
  └── SpriteRendererComponentSerializer (Serializes stable assetPath, dimensions, centered, and tint)

editor-desktop (ui)
  ├── AssetBrowserPanel      (Discovers assets via AssetCatalog without eager GPU loads; selection state)
  └── InspectorPanel         (Inspects SpriteRendererComponent & assigns selected AssetReference)
```
```

### Dependency Direction

```
engine-desktop           editor-desktop
    │                         │
    │                    ┌────┴────────────┐
    │                    ▼                 ▼
    │               editor-core      engine-render
    │                    │                 │
    └───────────┬────────┘                 │
                ▼                          ▼
           engine-render ───────────► engine-scene
                                           │
                                           ▼
                                      engine-core
```

**Dependency Rules**:
- Runtime modules must not depend on editor modules
- `engine-core` has no dependencies on other engine modules
- `editor-core` depends on `engine-core`, `engine-scene`, and `engine-render` (for renderable component bounds/picker)
- `editor-desktop` depends on `editor-core` and engine runtime modules to assemble the editor shell
- No circular dependencies allowed

### Package Structure

Root package: `io.github.ldev22live.gdxunity`

- `io.github.ldev22live.gdxunity.engine.core` - Core engine interfaces and services
- `io.github.ldev22live.gdxunity.engine.scene` - Scene, entity, and component system
- `io.github.ldev22live.gdxunity.engine.render` - Rendering abstractions and implementation
- `io.github.ldev22live.gdxunity.engine.desktop` - Desktop launcher and platform code
- `io.github.ldev22live.gdxunity.editor.core` - Editor state, selection, inspector model, and viewport picker
- `io.github.ldev22live.gdxunity.editor.desktop` - Desktop editor application shell, UI panels, and launcher

## Entity/Component Design

### Entity

- **Stable ID**: UUID-based entity ID that persists across serialization
- **Ownership**: Entities are owned by a scene
- **Components**: Map<Class<? extends Component>, Component> for component storage
- **Transform**: Single authoritative transform per entity (no duplication)

### Component

- **Interface marker**: Components implement a Component interface
- **Data-only**: Components contain data, not logic
- **Ownership**: Components are owned by entities

### Transform

- **Hierarchy**: Parent-child transform relationships
- **Properties**: Position, rotation, scale (local and world)
- **Dirty flag**: World matrix recalculated only when dirty
- **Single source of truth**: No duplicate transform state

### Scene

- **Entity ownership**: Scene owns all entities
- **Lifecycle**: Create, destroy, activate, deactivate entities
- **Root transform**: Root node of transform hierarchy
- **Update loop**: Scene integrates with engine update loop

## Resource Management

### Ownership Rules

- Resources have explicit ownership (scene, entity, or system)
- Owners are responsible for disposal
- No uncontrolled global singletons

### Disposal

- Explicit disposal methods for all native-backed resources
- Disposal order: children before parents
- Double-disposal protection

### Asset Loading

- Asset references are lightweight handles
- Actual loading is managed by asset system
- Resource caching with reference counting

## Separation of Concerns

### Engine Runtime

- Engine lifecycle
- Scene management
- Rendering
- Asset loading
- Input handling
- Physics (future)
- Animation (future)
- Audio (future)

### Editor (Future)

- Editor state
- Editor services
- UI panels
- Editor-to-runtime communication
- Custom editor tools (future)

### Game-Specific Code

- Game logic
- Game-specific components
- Game assets
- Game configuration

## Runtime and Editor Separation

### Runtime Independence

- The engine runtime can launch a game without the editor
- Runtime modules have no dependency on editor modules
- Game builds include only runtime modules

### Editor Integration & Play Mode (Milestone 5)

- **Edit Mode**: The editor owns the authored `Scene`. All property edits, entity creation, hierarchy reparenting, and file saves directly affect the authored scene.
- **Play Mode**: Initiated via `PlayModeController` in `editor-core`.
  - Serializes the authored scene to JSON using `SceneSerializer` and reconstructs an independent runtime copy.
  - Runtime scene entities retain identical UUIDs for selection mapping, but point to distinct in-memory object references.
  - The editor switches its active scene reference and viewport rendering to the runtime scene.
  - Edits to transforms, entity creation/deletion, new scenes, and file saving/loading are strictly blocked during Play/Pause mode to prevent accidental corruption or data loss.
- **Pause Mode**: Freezes simulation updates while preserving the runtime scene in memory for inspection and viewport rendering.
- **Stop Mode**:
  - Safely disposes the runtime scene using standard LIFO resource disposal rules.
  - Restores the original, untouched authored scene into `EditorState` and `SceneRenderer2D`.
  - Reconciles selection back to the corresponding authored entity if still present.

## Future Modules

When needed, create these modules:

- `engine-assets` - Asset references, loading, and resource management
- `engine-input` - Input abstraction
- `engine-serialization` - Versioned scene serialization
- `engine-debug` - Logging and diagnostic utilities
- `editor-core` - Editor state and editor services
- `editor-desktop` - Desktop editor application
- `engine-samples` - Independent example projects

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────┐
│                    Game Application                       │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│                  engine-desktop                          │
│  - Desktop launcher                                      │
│  - Platform configuration                                │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│                  engine-render                           │
│  - Rendering abstractions                                │
│  - LibGDX implementation                                 │
│  - Camera system                                         │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│                  engine-scene                            │
│  - Entity/component system                               │
│  - Transform hierarchy                                   │
│  - Scene management                                      │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│                  engine-core                             │
│  - Engine lifecycle                                      │
│  - Application services                                 │
│  - Logging utilities                                     │
│  - Base interfaces                                       │
└─────────────────────────────────────────────────────────┘
```
