# Feature-to-Test Coverage Matrix

Required by `docs/design-doc.md.txt` §10.3. Status words are defined in `AGENTS.md` §9:
**Implemented** = code + automated tests · **Integrated** = reachable by a user in the editor/runtime ·
**Validated** = integrated and checked in the running app (needs a dated report).

Last verified: 2026-10-29 against working tree on `main` @ `7772df4` (with uncommitted Phase 12 changes).
Test counts come from `./gradlew test --continue` XML reports: **262 tests, 0 failures, 0 errors, 0 skipped**.

Update this file in the same change as any feature or test change.

## Engine

| Feature | Main types | Tests (count) | Status | Gaps |
|---|---|---|---|---|
| Engine lifecycle | `EngineLifecycle`, `EngineApplication` | `EngineLifecycleTest` (7) | Integrated | — |
| Frame timing | `FrameClock`, `TimeProvider` | `FrameClockTest` (6) | Integrated | — |
| Resource ownership | `ResourceManager` | `ResourceManagerTest` (6) | Integrated (runtime) | Editor resources are not routed through `ResourceManager` |
| Asset references & catalog | `AssetReference`, `AssetCatalog`, `AssetService` | `AssetReferenceTest` (5), `AssetCatalogTest` (3) | Integrated | Textures only; no glTF/GLB, audio or import settings |
| Entities & components | `Entity`, `BaseComponent` | `EntityAndComponentTest` (5) | Integrated | — |
| Scene | `Scene`, `SceneManager` | `SceneTest` (5) | Integrated | `SceneManager` (scene switching) has no non-test call site |
| Transform hierarchy (2D/3D) | `Transform` | `TransformTest` (9) | Integrated | `rotate(yaw, pitch, roll)` not directly tested |
| Scene serialization | `SceneSerializer`, `ComponentSerializerRegistry` | `SceneSerializerTest` (5), `ComponentCloningAndSerializationCoverageTest` (7) | Integrated | Light components have **no serializer**, so they are not saved with scenes |
| 2D physics | `PhysicsSystem2D`, `RigidBody2D/BoxCollider2D/CircleCollider2D` | `PhysicsSystem2DTest` (9), `PhysicsUnits2DTest` (4) | Integrated (play mode) | — |
| 3D physics | — | — | Not implemented | Library evaluation required (design doc §3.3) |
| Script components | `ScriptComponent`, `EntityScriptBindingComponent` | `ScriptComponentTest` (2), `EntityScriptBindingSerializationTest` (2) | Integrated | — |
| Audio | — | — | Not implemented | — |
| Input mapping API | — | — | Not implemented | Only editor keyboard dispatch exists |

## Rendering

| Feature | Main types | Tests (count) | Status | Gaps |
|---|---|---|---|---|
| 2D camera | `OrthographicCamera2D` | `OrthographicCamera2DTest` (5) | Integrated | — |
| 2D scene rendering | `SceneRenderer2D`, `PrimitiveRenderer`, sprite/shape components | `SceneRenderer2DTest` (1), `PrimitiveRendererTest` (2), `RenderableComponentTest` (3), `SpriteRendererComponentSerializerTest` (2) | Integrated | GL output is not verified automatically |
| 3D perspective camera | `PerspectiveCamera3D` | `PerspectiveCamera3DTest` (5) | Implemented | Not used by the editor or runtime yet |
| 3D scene renderer | `SceneRenderer3D` | `SceneRenderer3DTest` (2) | Implemented (foundation) | **Submits no geometry**: there is no model/mesh component. Not used by the editor or runtime. Light objects are re-allocated every frame |
| Directional / point lights | `DirectionalLightComponent`, `PointLightComponent` | via `SceneRenderer3DTest` | Implemented | No serializer, no inspector, no dedicated unit tests |
| Materials, PBR, shadows, IBL | — | — | Not implemented | — |
| glTF / GLB import | — | — | Not implemented | — |

## Editor

| Feature | Main types | Tests (count) | Status | Gaps |
|---|---|---|---|---|
| Selection & multi-selection | `EditorState` | `EditorStateTest` (7), `EditorStateMultiSelectionTest` (6) | Integrated | — |
| Scene integrity hardening | `EditorState` | `SceneModelIntegrityAndHardeningTest` (5) | Integrated | — |
| Undo / redo commands | `CommandHistory`, `*Command` | `CommandHistoryTest` (6), `EditorCommandsTest` (7), `CompoundCommandTest` (6), `ComponentCommandTest` (2), `DuplicateEntityCommandTest` (2), `ReparentEntityCommandTest` (6) | Integrated | — |
| Inspector model & component editors | `EntityInspectorModel`, `ComponentEditorRegistry` | `EntityInspectorModelTest` (3), `ComponentEditorRegistryTest` (3) | Integrated | No light component editors |
| 2D picking | `ViewportPicker.pickEntityAt` | `ViewportPickerTest` (3 of 5) | Integrated | — |
| 3D pick ray | `ViewportPicker.getPickRay` | `ViewportPickerTest` (2 of 5, error contract only) | Implemented | Success path needs a graphics context (`Camera.unproject` reads `Gdx.graphics`); not called by the editor |
| 2D transform gizmos | `TransformGizmoMath.hitTest`, `GizmoInteractionController`, `TransformGizmoRenderer` | `TransformGizmoTest` (2D cases) | Integrated | — |
| 3D transform gizmos | `TransformGizmoMath.hitTest3D`, `calculate*3D*`, `TransformGizmoRenderer` (perspective branch) | `TransformGizmoTest` (6 3D cases) | Implemented | `GizmoInteractionController` only calls the 2D path; 3D drag (ray→plane projection) is not implemented; nothing draws with a perspective camera yet |
| Collider gizmos | `ColliderGizmoController` | `ColliderGizmoControllerTest` (5) | Integrated | — |
| 3D viewport navigation | `ViewportCameraController3D` | `ViewportCameraController3DTest` (6) | Implemented | Not connected to editor input |
| Keyboard shortcuts | `EditorKeyboardDispatcher` | `EditorKeyboardDispatcherTest` (4) | Integrated | — |
| Play mode | `PlayModeController` | `PlayModeControllerTest` (13) | Integrated | — |
| Script compilation (Java/Kotlin) | `JavaScriptCompiler`, `KotlinScriptCompiler`, `ScriptManager` | `ScriptCompilerTest` (5) | Integrated | — |
| Asset browser | `AssetBrowserFilterModel`, `AssetBrowserPanel` | `AssetBrowserFilterModelTest` (4) | Integrated | — |
| Theme & workspace layout | `EditorThemeManager`, `DefaultWorkspaceManager`, `WorkspaceLayoutSerializer` | `EditorThemeSystemTest` (8), `DefaultWorkspaceManagerTest` (7), `WorkspaceLayoutSerializerTest` (5) | Integrated | — |
| Launcher window config | `EditorLauncher` | `EditorLauncherWindowConfigurationTest` (3) | Integrated | — |

## AI automation

| Feature | Main types | Tests (count) | Status | Gaps |
|---|---|---|---|---|
| Agent tools (scene inspect, entity create/rename/delete, transform set, component add) | `AgentToolRegistry`, `DefaultAgentTools`, `agent/tools/*` | `AgentPlanEngineTest` (12) | Integrated (assistant panel) | No CLI/headless automation entry point (design doc §7.2) |
| Plan parse / validate / execute | `AgentPlanParser`, `AgentPlanValidator`, `AgentPlanExecutor` | `AgentPlanEngineTest` | Integrated | — |
| Assistant service & providers | `AssistantService`, `OllamaModelProvider`, `MockModelProvider` | `AssistantServiceTest` (19), `AssistantIntegrationTest` (3) | Integrated | — |

## Not yet covered by design-doc requirements

Prefabs, visual scripting, skeletal animation, export/packaging pipeline, profiling/diagnostics UI,
cross-platform runtime validation (no recorded macOS or Linux runs), and a comprehensive engine test scene
(design doc §10.3).
