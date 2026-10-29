# Feature-to-Test Coverage Matrix

Required by `docs/design-doc.md.txt` §10.3. Status words are defined in `AGENTS.md` §9:
**Implemented** = code + automated tests · **Integrated** = reachable by a user in the editor/runtime ·
**Validated** = integrated and checked in the running app (needs a dated report).

Last verified: 2026-10-29 against the working tree on `phase-12-3d-foundation` @ `3e55d5e` plus uncommitted Phase 12B-B/12C changes.
Test counts come from `./gradlew test --continue` XML reports: **374 tests, 0 failures, 0 errors, 0 skipped**
(editor-core 251, editor-desktop 14, engine-core 27, engine-render 27, engine-scene 55; Phase 15).

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
| Scene serialization | `SceneSerializer`, `ComponentSerializerRegistry` | `SceneSerializerTest` (5), `ComponentCloningAndSerializationCoverageTest` (7), `LightSerializationTest` (1) | Integrated | — |
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
| 3D perspective camera | `PerspectiveCamera3D` | `PerspectiveCamera3DTest` (5) | Implemented; used by the editor 3D view (pending manual check) | Runtime does not use it |
| 3D scene renderer & primitives (Phase 12C) | `SceneRenderer3D`, `MeshPrimitiveComponent` (+ serializer) | `SceneRenderer3DTest` (2), `MeshPrimitiveRenderingTest` (4) | Implemented; integrated in both editor views (pending manual check) | Box/sphere/plane only, diffuse color only; GL drawing needs a graphics test; runtime launcher does not render 3D |
| Directional / point lights | `DirectionalLightComponent`, `PointLightComponent` (+ serializers, Details editors) | `LightSerializationTest` (1), `MeshPrimitiveRenderingTest` (default light) | Implemented; integrated (pending manual check) | Point light component keeps an unused `position` field (the entity position is used) |
| Materials, PBR, shadows, IBL | — | — | Not implemented | — |
| glTF / GLB import | — | — | Not implemented | — |

## Editor

| Feature | Main types | Tests (count) | Status | Gaps |
|---|---|---|---|---|
| Scene files: New/Open/Save/Save As, unsaved changes (Phase 12B-B, TD-022) | `SceneFileController`, `EditorDialogService`, `TinyFileDialogService` | `SceneFileControllerTest` (8), `TinyFileDialogServiceTest` (1) | Implemented; native dialogs pending manual check | No recent-files list |
| Output Log (Phase 12B-B) | `EditorLog`, `OutputLogPanel` | `EditorLogTest` (4) | Implemented; integration pending manual check | Only status messages and script diagnostics are captured |
| Outliner context menu & rename (Phase 12B-B) | `HierarchyPanel`, `ContextMenu` | via `DefaultEditorActionsTest` | Implemented; integration pending manual check | Widget behaviour is not unit-tested |
| Editor typography & icons (Phase 12B-C, TD-021) | `EditorSkinFactory` (Inter via FreeType, drawn Play/Pause/Stop icons) | `EditorFontResourcesTest` (2) | Implemented; visual check pending | Tool and panel icons not done; font rendering itself needs a graphics test |
| Focus selection (F) | `ViewportFocus`, `EditorApplication.focusSelection` | `ViewportFocusTest` (4) | Implemented; integration pending manual check | — |
| Content Browser drag-and-drop, model asset type (Phase 13-C, part) | `AssetPlacement`, `AssetType.MODEL`, `AssetBrowserPanel`, `ViewportInteraction.worldPointAt` | `AssetPlacementTest` (3), `SceneViewportInteraction3DTest` | Implemented; visual check pending | Models cannot be placed until a glTF loader is approved; no thumbnails/folders yet |
| Prefabs: create, place, overrides, apply, revert, unpack (Phase 14, TD-027) | `PrefabService`, `PrefabState`, `PrefabDocument`, `PrefabInstanceComponent`, `SceneSnapshotCommand`, prefab actions, Details prefab bar, Outliner colour | `PrefabServiceTest` (12), `PrefabActionsTest` (2), `PrefabInstanceComponentSerializerTest` (2) | Implemented; integrated (Edit menu, Outliner menu, Details, Content Browser drop); visual check pending | No nested prefabs or variants; no prefab editing mode; override marks per component section, not per field |
| 3D physics components and engine-neutral system (Phase 15, TD-028) | `RigidBody3DComponent`, `Box/Sphere/CapsuleCollider3DComponent`, serializers, `PhysicsLayers3D`, `PhysicsSystem3D`, `PhysicsBackend3D`, Add Component entries, `Physics3DComponentEditorProviders`, `Collider3DRenderer` | `PhysicsSystem3DTest` (9), `Physics3DSerializationTest` (2), `Physics3DCatalogTest` (1) | Implemented; editing integrated; visual check pending | No physics library yet (TD-028 Proposed), so no 3D simulation in play mode |
| Oriented 3D picking, fly speed (Phase 13-D, part) | `ViewportPicker.intersectOriented`, `SelectionOutlineRenderer`, `SceneViewportInteraction3D` | `OrientedPickingTest` (4), `SceneViewportInteraction3DTest` | Implemented; visual check pending | 2D content still picked unrotated |
| Editor actions, menus & shortcuts (Phase 12B-A) | `EditorActionRegistry`, `DefaultEditorActions`, `EditorMenuBar` | `EditorActionRegistryTest` (6), `DefaultEditorActionsTest` (5) | Implemented; integration pending manual check | Menu widget itself has no automated test |
| Scene view in its own panel (Phase 12B-A, TD-024) | `SceneViewportWidget`, `SceneViewportInteraction2D`, `ViewportCoordinates2D`, `OrthographicCamera2D.createScreenFilling` | `SceneViewportInteraction2DTest` (7), `SceneViewportWidgetTest` (2), `OrthographicCamera2DTest`, `SceneRenderer2DTest` | Implemented; integration pending manual check | Frame-buffer rendering needs a graphics test |
| Selection & multi-selection | `EditorState` | `EditorStateTest` (7), `EditorStateMultiSelectionTest` (6) | Integrated | — |
| Scene integrity hardening | `EditorState` | `SceneModelIntegrityAndHardeningTest` (5) | Integrated | — |
| Undo / redo commands | `CommandHistory`, `*Command` | `CommandHistoryTest` (6), `EditorCommandsTest` (7), `CompoundCommandTest` (6), `ComponentCommandTest` (2), `DuplicateEntityCommandTest` (2), `ReparentEntityCommandTest` (6) | Integrated | — |
| Details panel: Add Component, sections (Phase 12B-B) | `EntityInspectorModel`, `ComponentEditorRegistry`, `ComponentCatalog`, `InspectorPanel` | `EntityInspectorModelTest` (3), `ComponentEditorRegistryTest` (3), `ComponentCatalogTest` (4), `InspectorPanelNamingTest` (1) | Implemented; integration pending manual check | Older 2D component editors still show their own Enable/Remove buttons as well |
| 2D picking | `ViewportPicker.pickEntityAt` | `ViewportPickerTest` (3 of 5) | Integrated | — |
| 3D pick ray | `ViewportPicker.getPickRay` | `ViewportPickerTest` (2 of 5, error contract only) | Implemented | Success path needs a graphics context (`Camera.unproject` reads `Gdx.graphics`); not called by the editor |
| 2D transform gizmos | `TransformGizmoMath.hitTest`, `GizmoInteractionController`, `TransformGizmoRenderer` | `TransformGizmoTest` (2D cases) | Integrated | — |
| 3D transform gizmos | `TransformGizmoMath` (`hitTest3D`, `dragPoint3D`, `perspectiveScaleFactor`), `GizmoInteractionController.startDrag3D/updateDrag3D`, `TransformGizmoRenderer` | `TransformGizmoTest` (6 3D cases), `GizmoDrag3DTest` (8) | Implemented; integrated in the 3D view (pending manual check) | Center/uniform handles drag in the XY plane, not a camera-facing plane |
| Collider gizmos | `ColliderGizmoController` | `ColliderGizmoControllerTest` (5) | Integrated | — |
| 3D viewport navigation & 2D/3D toggle | `ViewportCameraController3D`, `SceneViewportInteraction3D`, `ModeSwitchingViewportInteraction`, `ViewportGridRenderer3D` | `ViewportCameraController3DTest` (6), `SceneViewportInteraction3DTest` (8), `ViewportGridRenderer3DTest` (2) | Implemented; integrated (pending manual check) | Physics debug overlay is 2D-only; no focus-selection (F) yet |
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

Visual scripting, skeletal animation, export/packaging pipeline, profiling/diagnostics UI,
cross-platform runtime validation (no recorded macOS or Linux runs), and a comprehensive engine test scene
(design doc §10.3).
