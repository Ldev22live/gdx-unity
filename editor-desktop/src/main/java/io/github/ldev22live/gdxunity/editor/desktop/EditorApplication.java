package io.github.ldev22live.gdxunity.editor.desktop;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.picking.ViewportPicker;
import io.github.ldev22live.gdxunity.editor.core.play.PlayModeController;
import io.github.ldev22live.gdxunity.editor.desktop.ui.EditorSkinFactory;
import io.github.ldev22live.gdxunity.editor.desktop.ui.EditorStatusBar;
import io.github.ldev22live.gdxunity.editor.desktop.ui.EditorToolbar;
import io.github.ldev22live.gdxunity.editor.desktop.ui.HierarchyPanel;
import io.github.ldev22live.gdxunity.editor.desktop.ui.InspectorPanel;
import com.badlogic.gdx.graphics.Color;
import io.github.ldev22live.gdxunity.engine.render.components.ShapeRendererComponent;
import io.github.ldev22live.gdxunity.engine.render.SceneRenderer2D;
import io.github.ldev22live.gdxunity.engine.render.camera.OrthographicCamera2D;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;

/**
 * Main LibGDX Application for the desktop editor shell.
 * Coordinates SceneRenderer2D viewport rendering and scene2d UI panels on a single OpenGL window.
 */
public class EditorApplication extends ApplicationAdapter {
    private final EditorState editorState;
    private final OrthographicCamera2D sceneCamera;
    private final SceneRenderer2D sceneRenderer;

    private final io.github.ldev22live.gdxunity.engine.core.asset.AssetCatalog assetCatalog;
    private final io.github.ldev22live.gdxunity.engine.core.asset.AssetService assetService;

    private final PlayModeController playModeController;
    private final io.github.ldev22live.gdxunity.editor.core.gizmo.GizmoInteractionController gizmoController;
    private final io.github.ldev22live.gdxunity.editor.core.gizmo.ColliderGizmoController colliderController;
    private final io.github.ldev22live.gdxunity.editor.desktop.ui.TransformGizmoRenderer gizmoRenderer;
    private final io.github.ldev22live.gdxunity.editor.desktop.ui.PhysicsDebugRenderer physicsDebugRenderer;

    private final io.github.ldev22live.gdxunity.editor.core.workspace.WorkspaceManager workspaceManager;
    private final io.github.ldev22live.gdxunity.editor.desktop.workspaces.SceneEditorWorkspace sceneEditorWorkspace;
    private final io.github.ldev22live.gdxunity.editor.core.workspace.WorkspaceLayoutSerializer layoutSerializer;
    private io.github.ldev22live.gdxunity.editor.core.workspace.WorkspaceLayoutConfig layoutConfig;
    private final io.github.ldev22live.gdxunity.editor.core.input.EditorKeyboardDispatcher keyboardDispatcher;
    private final java.io.File layoutConfigFile = new java.io.File("workspace_layout.json");

    private final io.github.ldev22live.gdxunity.editor.core.agent.service.AssistantService assistantService;
    private io.github.ldev22live.gdxunity.editor.desktop.ui.AssistantPanel assistantPanel;

    private final io.github.ldev22live.gdxunity.editor.core.theme.EditorThemeManager themeManager;
    private EditorSkinFactory skinFactory;
    private Skin skin;
    private Stage uiStage;

    // Viewport navigation mouse drag state
    private boolean isPanning = false;
    private int lastMouseX;
    private int lastMouseY;

    // Viewport spacer for dynamic hit-testing
    private Table viewportSpacer;
    private boolean isAssistantVisible = false;
    private com.badlogic.gdx.scenes.scene2d.ui.SplitPane rightSplit;

    public EditorApplication() {
        this.editorState = new EditorState();
        this.sceneCamera = new OrthographicCamera2D(640, 480);

        // Find asset root directory ('assets' or parent/current)
        java.io.File assetRoot = findAssetRoot();
        this.assetCatalog = new io.github.ldev22live.gdxunity.engine.core.asset.AssetCatalog(assetRoot);
        this.assetService = new io.github.ldev22live.gdxunity.engine.core.asset.AssetService(assetCatalog);

        this.editorState.setAssetService(assetService);
        this.sceneRenderer = new SceneRenderer2D(sceneCamera, assetService);

        // Register core serializers into editor state serializer
        this.editorState.getSerializer().getRegistry().register(
            new io.github.ldev22live.gdxunity.engine.render.components.serialization.ShapeRendererComponentSerializer()
        );
        this.editorState.getSerializer().getRegistry().register(
            new io.github.ldev22live.gdxunity.engine.render.components.serialization.SpriteRendererComponentSerializer()
        );
        this.editorState.getSerializer().getRegistry().register(
            new io.github.ldev22live.gdxunity.engine.scene.scripting.EntityScriptBindingComponentSerializer()
        );
        this.editorState.getSerializer().getRegistry().register(
            new io.github.ldev22live.gdxunity.engine.scene.physics.serialization.RigidBody2DComponentSerializer()
        );
        this.editorState.getSerializer().getRegistry().register(
            new io.github.ldev22live.gdxunity.engine.scene.physics.serialization.BoxCollider2DComponentSerializer()
        );
        this.editorState.getSerializer().getRegistry().register(
            new io.github.ldev22live.gdxunity.engine.scene.physics.serialization.CircleCollider2DComponentSerializer()
        );

        // Initialize script management: source directory "scripts", output directory "build/scripts"
        java.io.File scriptsDir = new java.io.File("scripts");
        java.io.File scriptsBuildDir = new java.io.File("build/scripts");
        io.github.ldev22live.gdxunity.editor.core.scripting.ScriptManager scriptManager =
            new io.github.ldev22live.gdxunity.editor.core.scripting.ScriptManager(scriptsDir, scriptsBuildDir);
        this.editorState.setScriptManager(scriptManager);

        this.playModeController = new PlayModeController(editorState);
        this.editorState.setPlayModeController(playModeController);

        this.gizmoController = new io.github.ldev22live.gdxunity.editor.core.gizmo.GizmoInteractionController(editorState);
        this.colliderController = new io.github.ldev22live.gdxunity.editor.core.gizmo.ColliderGizmoController(editorState);
        this.gizmoRenderer = new io.github.ldev22live.gdxunity.editor.desktop.ui.TransformGizmoRenderer(
            editorState, gizmoController, sceneCamera
        );
        this.physicsDebugRenderer = new io.github.ldev22live.gdxunity.editor.desktop.ui.PhysicsDebugRenderer(
            editorState, playModeController, colliderController, sceneCamera
        );
        // Workspace and Layout initialization
        this.workspaceManager = new io.github.ldev22live.gdxunity.editor.core.workspace.DefaultWorkspaceManager(() -> editorState);
        this.layoutSerializer = new io.github.ldev22live.gdxunity.editor.core.workspace.WorkspaceLayoutSerializer();
        this.layoutConfig = layoutSerializer.loadFromFile(layoutConfigFile);
        this.themeManager = new io.github.ldev22live.gdxunity.editor.core.theme.EditorThemeManager();

        // Register default SceneEditorWorkspace with physics debug renderer
        this.sceneEditorWorkspace = new io.github.ldev22live.gdxunity.editor.desktop.workspaces.SceneEditorWorkspace(
            sceneRenderer, sceneCamera, gizmoRenderer, new io.github.ldev22live.gdxunity.editor.desktop.ui.ViewportGridRenderer(sceneCamera), physicsDebugRenderer
        );
        this.workspaceManager.registerWorkspace(sceneEditorWorkspace);

        // Centralized keyboard shortcut dispatcher
        this.keyboardDispatcher = new io.github.ldev22live.gdxunity.editor.core.input.EditorKeyboardDispatcher(() -> {
            if (uiStage != null && uiStage.getKeyboardFocus() != null) {
                return uiStage.getKeyboardFocus() instanceof com.badlogic.gdx.scenes.scene2d.ui.TextField;
            }
            return false;
        });

        // Register standard global shortcuts
        // Keycode 54 = Z, 53 = Y, 47 = S, 112 = Forward Delete / Backspace
        this.keyboardDispatcher.registerGlobalShortcut("undo", Input.Keys.Z, io.github.ldev22live.gdxunity.editor.core.input.EditorKeyboardDispatcher.MOD_CTRL, editorState::undo);
        this.keyboardDispatcher.registerGlobalShortcut("redo-ctrl-y", Input.Keys.Y, io.github.ldev22live.gdxunity.editor.core.input.EditorKeyboardDispatcher.MOD_CTRL, editorState::redo);
        this.keyboardDispatcher.registerGlobalShortcut("redo-ctrl-shift-z", Input.Keys.Z, io.github.ldev22live.gdxunity.editor.core.input.EditorKeyboardDispatcher.MOD_CTRL | io.github.ldev22live.gdxunity.editor.core.input.EditorKeyboardDispatcher.MOD_SHIFT, editorState::redo);
        this.keyboardDispatcher.registerGlobalShortcut("delete", Input.Keys.FORWARD_DEL, 0, editorState::deleteSelectedEntity);
        this.keyboardDispatcher.registerGlobalShortcut("duplicate", Input.Keys.D, io.github.ldev22live.gdxunity.editor.core.input.EditorKeyboardDispatcher.MOD_CTRL, editorState::duplicateSelectedEntity);

        // Populate with initial demo scene
        Scene initialScene = createInitialScene();
        this.editorState.setActiveScene(initialScene);
        this.sceneRenderer.setActiveScene(initialScene);

        // Keep renderer synchronized with editor state's active scene
        this.editorState.addListener(new EditorState.Listener() {
            @Override
            public void onActiveSceneChanged(Scene oldScene, Scene newScene) {
                sceneRenderer.setActiveScene(newScene);
            }
        });

        // Initialize AI tool registry, provider registry, and assistant service
        io.github.ldev22live.gdxunity.editor.core.agent.AgentToolRegistry toolRegistry =
            io.github.ldev22live.gdxunity.editor.core.agent.DefaultAgentTools.createDefaultRegistry();
        io.github.ldev22live.gdxunity.editor.core.agent.provider.ModelProviderRegistry providerRegistry =
            new io.github.ldev22live.gdxunity.editor.core.agent.provider.ModelProviderRegistry();
        providerRegistry.registerProvider(new io.github.ldev22live.gdxunity.editor.core.agent.provider.MockModelProvider());
        providerRegistry.registerProvider(new io.github.ldev22live.gdxunity.editor.core.agent.provider.OllamaModelProvider());

        this.assistantService = new io.github.ldev22live.gdxunity.editor.core.agent.service.AssistantService(
            editorState, toolRegistry, providerRegistry
        );
    }

    public static Scene createInitialScene() {
        Scene scene = new Scene("Editor Sample Scene");

        // 1. Root Entity: Sun / Parent Core (Center of 640x480 screen = 320, 240)
        Entity parentCore = scene.createEntity("ParentCore");
        parentCore.getTransform().setLocalPosition(320f, 240f, 0f);
        parentCore.addComponent(new ShapeRendererComponent(120f, 120f, new Color(0.2f, 0.7f, 0.9f, 1.0f)));

        // 2. Child Entity 1: Orbiting Planet (Inherits parent transform + offset (100, 50))
        Entity childPlanet = scene.createEntity("ChildPlanet");
        childPlanet.getTransform().setLocalPosition(100f, 50f, 0f);
        childPlanet.getTransform().setParent(parentCore.getTransform());
        childPlanet.addComponent(new ShapeRendererComponent(50f, 50f, new Color(0.9f, 0.4f, 0.2f, 1.0f)));

        // 3. Child Entity 2: Moon (Child of planet, offset (40, 20), inherits multi-level world transform)
        Entity moon = scene.createEntity("Moon");
        moon.getTransform().setLocalPosition(40f, 20f, 0f);
        moon.getTransform().setParent(childPlanet.getTransform());
        moon.addComponent(new ShapeRendererComponent(20f, 20f, new Color(0.9f, 0.9f, 0.3f, 1.0f)));

        // 4. Independent Entity: Ground Platform (Bottom center = 320, 40)
        Entity ground = scene.createEntity("GroundPlatform");
        ground.getTransform().setLocalPosition(320f, 40f, 0f);
        ground.addComponent(new ShapeRendererComponent(400f, 30f, new Color(0.3f, 0.8f, 0.4f, 1.0f)));

        return scene;
    }

    @Override
    public void create() {
        // 1. Initialize renderer
        sceneRenderer.initialize();

        // 2. Build UI skin and Stage
        this.skinFactory = new EditorSkinFactory();
        this.skin = skinFactory.createSkin(themeManager.getCurrentPalette());
        this.uiStage = new Stage(new ScreenViewport());

        // 3. Build UI layout
        buildUi();

        // 4. Configure input handling: UI stage has first priority, then viewport controller
        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(uiStage);
        multiplexer.addProcessor(new ViewportInputProcessor());
        Gdx.input.setInputProcessor(multiplexer);
    }

    private static java.io.File findAssetRoot() {
        java.io.File dir = new java.io.File("assets");
        if (dir.exists() && dir.isDirectory()) return dir;
        dir = new java.io.File("../assets");
        if (dir.exists() && dir.isDirectory()) return dir;
        return new java.io.File(".");
    }

    private void switchTheme(io.github.ldev22live.gdxunity.editor.core.theme.EditorThemeMode newMode) {
        themeManager.setThemeMode(newMode);
        if (skinFactory != null) {
            skinFactory.dispose();
        }
        skinFactory = new EditorSkinFactory();
        skin = skinFactory.createSkin(themeManager.getCurrentPalette());
        if (uiStage != null) {
            uiStage.clear();
            buildUi();
            editorState.setStatusMessage("Theme switched to: " + newMode.getDisplayName(), false);
        }
    }

    private void buildUi() {
        Table root = new Table();
        root.setFillParent(true);
        uiStage.addActor(root);

        // 1. Top Studio Navigation Bar (Workspace Switcher & Menu)
        io.github.ldev22live.gdxunity.editor.desktop.ui.workspace.StudioNavigationBar studioNav =
            new io.github.ldev22live.gdxunity.editor.desktop.ui.workspace.StudioNavigationBar(workspaceManager, themeManager, skin, menuName -> {
                switch (menuName) {
                    case "File" -> {
                        try {
                            java.io.File saveFile = new java.io.File("scene_backup.json");
                            editorState.saveSceneToFile(saveFile);
                            editorState.setStatusMessage("Scene saved to " + saveFile.getName(), false);
                        } catch (Exception e) {
                            editorState.setStatusMessage("Save failed: " + e.getMessage(), true);
                        }
                    }
                    case "Edit" -> {
                        if (editorState.getCommandHistory().canUndo()) {
                            editorState.undo();
                        } else {
                            editorState.setStatusMessage("Nothing to undo", false);
                        }
                    }
                    case "Assets" -> {
                        assetCatalog.scan();
                        editorState.setStatusMessage("Asset catalog refreshed (" + assetCatalog.getAllAssets().size() + " assets)", false);
                    }
                    case "GameObject" -> {
                        Entity created = editorState.createEntity("GameObject");
                        editorState.setSelectedEntity(created);
                        editorState.setStatusMessage("Created: " + created.getName(), false);
                    }
                    case "Component" -> {
                        Entity selected = editorState.getSelectedEntity();
                        if (selected != null) {
                            io.github.ldev22live.gdxunity.engine.render.components.SpriteRendererComponent sprite =
                                new io.github.ldev22live.gdxunity.engine.render.components.SpriteRendererComponent(100f, 100f);
                            editorState.executeCommand(new io.github.ldev22live.gdxunity.editor.core.command.AddComponentCommand(editorState, selected.getId(), sprite));
                            editorState.setStatusMessage("Added SpriteRendererComponent to " + selected.getName(), false);
                        } else {
                            editorState.setStatusMessage("Select a GameObject first to add a component", true);
                        }
                    }
                    case "View", "ToggleTheme" -> {
                        io.github.ldev22live.gdxunity.editor.core.theme.EditorThemeMode next =
                            (themeManager.getCurrentMode() == io.github.ldev22live.gdxunity.editor.core.theme.EditorThemeMode.DARK) ?
                                io.github.ldev22live.gdxunity.editor.core.theme.EditorThemeMode.LIGHT :
                                io.github.ldev22live.gdxunity.editor.core.theme.EditorThemeMode.DARK;
                        switchTheme(next);
                    }
                    case "Window" -> toggleAssistantPanel();
                    case "Help" -> editorState.setStatusMessage("GdxUnity v1.0.0 — LibGDX 1.14.2 & Java 21 LTS Editor", false);
                }
            });
        root.add(studioNav).growX().height(30).row();

        // 2. Editor Command Toolbar
        EditorToolbar toolbar = new EditorToolbar(editorState, playModeController, sceneCamera, skin, this::toggleAssistantPanel, this::toggleGrid, this::togglePhysicsDebug);
        root.add(toolbar).growX().height(34).row();

        // 3. Middle Resizable Workspace Layout using SplitPane
        // Left: HierarchyPanel
        HierarchyPanel hierarchyPanel = new HierarchyPanel(editorState, skin);

        // Center: Viewport spacer (transparent area where SceneRenderer2D draws)
        this.viewportSpacer = new Table();

        // Left split: [Hierarchy] | [Center Viewport]
        com.badlogic.gdx.scenes.scene2d.ui.SplitPane leftSplit =
            new com.badlogic.gdx.scenes.scene2d.ui.SplitPane(hierarchyPanel, viewportSpacer, false, skin);
        leftSplit.setSplitAmount(layoutConfig.getSplitRatioLeft());
        leftSplit.setMinSplitAmount(0.12f);
        leftSplit.setMaxSplitAmount(0.40f);

        // Right: Inspector and optional AI Assistant in vertical split
        InspectorPanel inspectorPanel = new InspectorPanel(editorState, skin);
        this.assistantPanel = new io.github.ldev22live.gdxunity.editor.desktop.ui.AssistantPanel(assistantService, skin);
        this.rightSplit = new com.badlogic.gdx.scenes.scene2d.ui.SplitPane(inspectorPanel, assistantPanel, true, skin);
        // By default show Inspector primarily, Assistant hidden or docked
        this.rightSplit.setSplitAmount(isAssistantVisible ? 0.55f : 1.0f);
        this.rightSplit.setMinSplitAmount(0.2f);
        this.rightSplit.setMaxSplitAmount(1.0f);

        // Center+Right split: [leftSplit] | [rightSplit (Inspector + Assistant)]
        com.badlogic.gdx.scenes.scene2d.ui.SplitPane centerRightSplit =
            new com.badlogic.gdx.scenes.scene2d.ui.SplitPane(leftSplit, rightSplit, false, skin);
        centerRightSplit.setSplitAmount(1.0f - layoutConfig.getSplitRatioRight());
        centerRightSplit.setMinSplitAmount(0.50f);
        centerRightSplit.setMaxSplitAmount(0.88f);

        // Bottom Section: Asset Browser (Height configurable via vertical split)
        io.github.ldev22live.gdxunity.editor.desktop.ui.AssetBrowserPanel assetBrowser =
            new io.github.ldev22live.gdxunity.editor.desktop.ui.AssetBrowserPanel(editorState, assetCatalog, skin);

        com.badlogic.gdx.scenes.scene2d.ui.SplitPane mainSplit =
            new com.badlogic.gdx.scenes.scene2d.ui.SplitPane(centerRightSplit, assetBrowser, true, skin);
        mainSplit.setSplitAmount(layoutConfig.getSplitRatioMain());
        mainSplit.setMinSplitAmount(0.55f);
        mainSplit.setMaxSplitAmount(0.92f);

        root.add(mainSplit).grow().row();

        // 4. Bottom Status Bar
        EditorStatusBar statusBar = new EditorStatusBar(editorState, skin);
        root.add(statusBar).growX().height(24);
    }

    public void toggleAssistantPanel() {
        isAssistantVisible = !isAssistantVisible;
        if (rightSplit != null) {
            rightSplit.setSplitAmount(isAssistantVisible ? 0.55f : 1.0f);
        }
        editorState.setStatusMessage(isAssistantVisible ? "AI Assistant panel opened" : "AI Assistant panel minimized", false);
    }

    public void toggleGrid() {
        if (sceneEditorWorkspace != null && sceneEditorWorkspace.getGridRenderer() != null) {
            boolean newState = !sceneEditorWorkspace.getGridRenderer().isEnabled();
            sceneEditorWorkspace.getGridRenderer().setEnabled(newState);
            editorState.setStatusMessage("Scene View grid " + (newState ? "enabled" : "disabled"), false);
        }
    }

    public void togglePhysicsDebug() {
        if (physicsDebugRenderer != null) {
            physicsDebugRenderer.toggleDebugVisible();
            editorState.setStatusMessage("Physics debug overlay " + (physicsDebugRenderer.isDebugVisible() ? "enabled" : "disabled"), false);
        }
    }

    @Override
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;

        // Resize UI Stage
        if (uiStage != null) {
            uiStage.getViewport().update(width, height, true);
        }

        // Delegate to active workspace for internal viewports and camera
        if (workspaceManager != null) {
            workspaceManager.resize(width, height);
        } else if (sceneRenderer != null) {
            sceneRenderer.resize(width, height);
        }
    }

    @Override
    public void render() {
        // 1. Clear OpenGL buffer using theme surface/background
        Color clearColor = themeManager != null ? themeManager.getCurrentPalette().getBackground() : new Color(0.12f, 0.14f, 0.18f, 1.0f);
        Gdx.gl.glClearColor(clearColor.r, clearColor.g, clearColor.b, 1.0f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        float delta = Gdx.graphics.getDeltaTime();

        // 2. Advance Play Mode simulation if active
        if (playModeController != null && playModeController.isPlaying()) {
            playModeController.update(delta);
        }

        // 3. Delegate per-frame rendering to active creative workspace
        if (workspaceManager != null) {
            workspaceManager.update(delta);
            workspaceManager.render(delta);
        } else if (sceneRenderer != null) {
            sceneRenderer.render(delta);
            if (sceneRenderer.getShapeRenderer() != null) {
                float zoom = sceneCamera.getZoom();
                gizmoRenderer.render(sceneRenderer.getShapeRenderer(), zoom);
            }
        }

        // 4. Render Editor UI Stage on top
        if (uiStage != null) {
            uiStage.act(delta);
            uiStage.draw();
        }
    }

    @Override
    public void dispose() {
        // Save layout configuration before exiting
        if (layoutSerializer != null && layoutConfig != null) {
            layoutSerializer.saveToFile(layoutConfig, layoutConfigFile);
        }

        // Stop simulation first before disposing shared renderer and resources
        if (playModeController != null) {
            playModeController.stop();
        }
        if (workspaceManager != null) {
            workspaceManager.dispose();
        }
        if (sceneRenderer != null) {
            sceneRenderer.dispose();
        }
        if (assetService != null) {
            assetService.dispose();
        }
        if (editorState.getScriptManager() != null) {
            editorState.getScriptManager().dispose();
        }
        if (assistantPanel != null) {
            assistantPanel.dispose();
        }
        if (assistantService != null) {
            assistantService.dispose();
        }
        if (physicsDebugRenderer != null) {
            physicsDebugRenderer.dispose();
        }
        if (uiStage != null) {
            uiStage.dispose();
        }
        if (skinFactory != null) {
            skinFactory.dispose();
        }
    }

    public io.github.ldev22live.gdxunity.editor.core.theme.EditorThemeManager getThemeManager() {
        return themeManager;
    }

    public Skin getSkin() {
        return skin;
    }

    public EditorState getEditorState() {
        return editorState;
    }

    public PlayModeController getPlayModeController() {
        return playModeController;
    }

    public io.github.ldev22live.gdxunity.editor.core.gizmo.GizmoInteractionController getGizmoController() {
        return gizmoController;
    }

    public OrthographicCamera2D getSceneCamera() {
        return sceneCamera;
    }

    public SceneRenderer2D getSceneRenderer() {
        return sceneRenderer;
    }

    public io.github.ldev22live.gdxunity.editor.core.workspace.WorkspaceManager getWorkspaceManager() {
        return workspaceManager;
    }

    public io.github.ldev22live.gdxunity.editor.core.input.EditorKeyboardDispatcher getKeyboardDispatcher() {
        return keyboardDispatcher;
    }

    public io.github.ldev22live.gdxunity.editor.core.agent.service.AssistantService getAssistantService() {
        return assistantService;
    }

    public io.github.ldev22live.gdxunity.editor.desktop.ui.AssistantPanel getAssistantPanel() {
        return assistantPanel;
    }

    // --- Viewport Input Handling: Right-Click Pan, Mouse Wheel Zoom, Left-Click Picking ---

    private class ViewportInputProcessor extends InputAdapter {
        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            // Check if clicking in the center area (outside of side panels)
            boolean inViewport = true;
            if (viewportSpacer != null && viewportSpacer.getStage() != null) {
                com.badlogic.gdx.math.Vector2 stageCoords = viewportSpacer.getStage().screenToStageCoordinates(new Vector2(screenX, screenY));
                Actor hit = viewportSpacer.getStage().hit(stageCoords.x, stageCoords.y, true);
                // Allow interaction if clicked on the viewportSpacer or no UI element absorbed the hit
                inViewport = (hit == null || hit == viewportSpacer || hit.isDescendantOf(viewportSpacer));
            } else {
                int leftPanelWidth = (int) (Gdx.graphics.getWidth() * layoutConfig.getSplitRatioLeft());
                int rightPanelWidth = (int) (Gdx.graphics.getWidth() * layoutConfig.getSplitRatioRight());
                int topBarHeight = 60;
                int bottomBarHeight = 24;
                int windowW = Gdx.graphics.getWidth();
                int windowH = Gdx.graphics.getHeight();
                inViewport = (screenX >= leftPanelWidth) && (screenX <= windowW - rightPanelWidth)
                    && (screenY >= topBarHeight) && (screenY <= windowH - bottomBarHeight);
            }

            if (!inViewport) {
                return false;
            }

            if (button == Input.Buttons.RIGHT) {
                isPanning = true;
                lastMouseX = screenX;
                lastMouseY = screenY;
                return true;
            }

            if (button == Input.Buttons.LEFT) {
                Vector2 worldCoords = ViewportPicker.screenToWorld2D(sceneCamera, screenX, screenY);
                float zoom = sceneCamera.getZoom();

                // 1. Collider Gizmo interaction priority: check if a collider handle was clicked on selected entity
                io.github.ldev22live.gdxunity.editor.core.gizmo.ColliderGizmoController.HitResult colHit = colliderController.hitTest(worldCoords, zoom);
                if (colHit.handle != io.github.ldev22live.gdxunity.editor.core.gizmo.ColliderGizmoHandle.NONE) {
                    colliderController.startDrag(colHit, worldCoords);
                    editorState.setStatusMessage("Resizing Collider: " + colHit.handle.name(), false);
                    return true;
                }

                // 2. Transform Gizmo interaction priority: check if a transform gizmo handle was clicked
                io.github.ldev22live.gdxunity.editor.core.gizmo.GizmoHandle handle = gizmoController.hitTest(worldCoords, zoom);
                if (handle != io.github.ldev22live.gdxunity.editor.core.gizmo.GizmoHandle.NONE) {
                    gizmoController.startDrag(handle, worldCoords);
                    editorState.setStatusMessage("Manipulating: " + handle.name(), false);
                    return true;
                }

                // 3. Viewport Entity Picking: check if an entity was clicked
                Entity picked = ViewportPicker.pickEntityAt(editorState.getActiveScene(), worldCoords.x, worldCoords.y);
                if (picked != null) {
                    boolean shiftOrCtrl = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                        || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)
                        || Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
                        || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);

                    if (shiftOrCtrl) {
                        if (editorState.isSelected(picked.getId())) {
                            editorState.removeSelectedEntityId(picked.getId());
                        } else {
                            editorState.addSelectedEntityId(picked.getId());
                        }
                    } else {
                        editorState.setSelectedEntity(picked);
                    }
                    editorState.setStatusMessage("Picked entity: " + picked.getName(), false);
                } else {
                    editorState.clearSelection();
                }
                return true;
            }

            return false;
        }

        @Override
        public boolean touchDragged(int screenX, int screenY, int pointer) {
            if (isPanning) {
                float dx = screenX - lastMouseX;
                float dy = screenY - lastMouseY;

                // Move camera opposite to mouse drag in world coordinates scaled by camera zoom
                float zoom = sceneCamera.getZoom();
                sceneCamera.translate(-dx * zoom, dy * zoom, 0f);
                sceneCamera.update();

                lastMouseX = screenX;
                lastMouseY = screenY;
                return true;
            }

            if (colliderController.isDragging()) {
                Vector2 worldCoords = ViewportPicker.screenToWorld2D(sceneCamera, screenX, screenY);
                colliderController.updateDrag(worldCoords);
                return true;
            }

            if (gizmoController.isDragging()) {
                Vector2 worldCoords = ViewportPicker.screenToWorld2D(sceneCamera, screenX, screenY);
                float zoom = sceneCamera.getZoom();
                gizmoController.updateDrag(worldCoords, zoom);
                return true;
            }

            return false;
        }

        @Override
        public boolean touchUp(int screenX, int screenY, int pointer, int button) {
            if (button == Input.Buttons.RIGHT) {
                isPanning = false;
                return true;
            }

            if (button == Input.Buttons.LEFT && colliderController.isDragging()) {
                colliderController.endDrag();
                editorState.setStatusMessage("Collider resizing finished", false);
                return true;
            }

            if (button == Input.Buttons.LEFT && gizmoController.isDragging()) {
                gizmoController.endDrag();
                editorState.setStatusMessage("Manipulation finished", false);
                return true;
            }

            return false;
        }

        @Override
        public boolean scrolled(float amountX, float amountY) {
            float currentZoom = sceneCamera.getZoom();
            float newZoom = Math.max(0.1f, Math.min(10f, currentZoom + amountY * 0.1f));
            sceneCamera.setZoom(newZoom);
            sceneCamera.update();
            return true;
        }

        @Override
        public boolean keyDown(int keycode) {
            int modifiers = 0;
            if (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT)) {
                modifiers |= io.github.ldev22live.gdxunity.editor.core.input.EditorKeyboardDispatcher.MOD_CTRL;
            }
            if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)) {
                modifiers |= io.github.ldev22live.gdxunity.editor.core.input.EditorKeyboardDispatcher.MOD_SHIFT;
            }
            if (Gdx.input.isKeyPressed(Input.Keys.ALT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.ALT_RIGHT)) {
                modifiers |= io.github.ldev22live.gdxunity.editor.core.input.EditorKeyboardDispatcher.MOD_ALT;
            }

            if (keyboardDispatcher != null && keyboardDispatcher.dispatch(keycode, modifiers)) {
                return true;
            }

            return false;
        }
    }
}
