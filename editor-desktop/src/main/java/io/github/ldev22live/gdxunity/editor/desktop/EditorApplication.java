package io.github.ldev22live.gdxunity.editor.desktop;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop;
import io.github.ldev22live.gdxunity.editor.core.asset.AssetPlacement;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.ViewportMode;
import io.github.ldev22live.gdxunity.editor.core.action.DefaultEditorActions;
import io.github.ldev22live.gdxunity.editor.core.action.EditorAction;
import io.github.ldev22live.gdxunity.editor.core.action.EditorActionRegistry;
import io.github.ldev22live.gdxunity.editor.core.command.RenameEntityCommand;
import io.github.ldev22live.gdxunity.editor.core.file.SceneFileController;
import io.github.ldev22live.gdxunity.editor.core.input.EditorKeyboardDispatcher;
import io.github.ldev22live.gdxunity.editor.core.log.EditorLog;
import io.github.ldev22live.gdxunity.editor.core.input.ViewportCameraController3D;
import io.github.ldev22live.gdxunity.editor.core.viewport.ModeSwitchingViewportInteraction;
import io.github.ldev22live.gdxunity.editor.core.viewport.SceneViewportInteraction2D;
import io.github.ldev22live.gdxunity.editor.core.viewport.SceneViewportInteraction3D;
import io.github.ldev22live.gdxunity.editor.core.viewport.ViewportFocus;
import io.github.ldev22live.gdxunity.editor.core.viewport.ViewportInteraction;
import io.github.ldev22live.gdxunity.editor.core.play.PlayModeController;
import io.github.ldev22live.gdxunity.editor.desktop.ui.EditorSkinFactory;
import io.github.ldev22live.gdxunity.editor.desktop.ui.EditorStatusBar;
import io.github.ldev22live.gdxunity.editor.desktop.ui.EditorToolbar;
import io.github.ldev22live.gdxunity.editor.desktop.ui.HierarchyPanel;
import io.github.ldev22live.gdxunity.editor.desktop.file.TinyFileDialogService;
import io.github.ldev22live.gdxunity.editor.desktop.ui.InspectorPanel;
import io.github.ldev22live.gdxunity.editor.desktop.ui.OutputLogPanel;
import io.github.ldev22live.gdxunity.editor.desktop.ui.TabbedPanel;
import io.github.ldev22live.gdxunity.editor.desktop.ui.menu.ContextMenu;
import io.github.ldev22live.gdxunity.editor.desktop.ui.menu.EditorMenuBar;
import io.github.ldev22live.gdxunity.editor.desktop.ui.viewport.SceneViewportWidget;
import io.github.ldev22live.gdxunity.editor.desktop.ui.viewport.ViewportGridRenderer3D;
import io.github.ldev22live.gdxunity.editor.desktop.ui.viewport.ViewportOverlayToolbar;
import com.badlogic.gdx.graphics.Color;
import io.github.ldev22live.gdxunity.engine.render.components.ShapeRendererComponent;
import io.github.ldev22live.gdxunity.engine.render.SceneRenderer2D;
import io.github.ldev22live.gdxunity.engine.render.SceneRenderer3D;
import io.github.ldev22live.gdxunity.engine.render.camera.OrthographicCamera2D;
import io.github.ldev22live.gdxunity.engine.render.camera.PerspectiveCamera3D;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;

import java.util.List;

/**
 * Main LibGDX Application for the desktop editor shell.
 * Coordinates scene view rendering (off-screen, TD-024) and scene2d UI panels on a single OpenGL window.
 */
public class EditorApplication extends ApplicationAdapter {
    /** World position the editor view centers on at startup and on Reset View (center of the sample scene). */
    static final float DEFAULT_VIEW_CENTER_X = 320f;
    static final float DEFAULT_VIEW_CENTER_Y = 240f;

    static final List<String> MENU_ORDER = List.of(
        DefaultEditorActions.MENU_FILE, DefaultEditorActions.MENU_EDIT, DefaultEditorActions.MENU_CREATE,
        DefaultEditorActions.MENU_TOOLS, DefaultEditorActions.MENU_WINDOW, DefaultEditorActions.MENU_HELP);

    static final String ACTION_RESET_VIEW = "view.reset";
    static final String ACTION_TOGGLE_GRID = "window.toggle-grid";
    static final String ACTION_TOGGLE_PHYSICS_DEBUG = "window.toggle-physics-debug";
    static final String ACTION_TOGGLE_ASSISTANT = "window.toggle-assistant";
    static final String ACTION_TOGGLE_THEME = "window.toggle-theme";
    static final String ACTION_RENAME = "edit.rename";
    static final String ACTION_FOCUS_SELECTION = "view.focus-selection";

    /** Actions in the Outliner right-click menu. */
    static final List<String> OUTLINER_CONTEXT_ACTIONS = List.of(
        DefaultEditorActions.CREATE_EMPTY_ENTITY, ACTION_RENAME,
        DefaultEditorActions.EDIT_DUPLICATE, DefaultEditorActions.EDIT_DELETE);
    private static final int BOTTOM_TAB_CONTENT_BROWSER = 0;

    private static final float MENU_BAR_HEIGHT = 28f;
    private static final float TOOLBAR_HEIGHT = 34f;
    private static final float STATUS_BAR_HEIGHT = 24f;
    private static final float RENAME_FIELD_WIDTH = 260f;

    // 3D view. Scene units are pixel-sized (the sample scene spans 640x480), so distances and speeds are large.
    private static final float VIEW3D_FIELD_OF_VIEW = 60f;
    private static final float VIEW3D_NEAR_PLANE = 1f;
    private static final float VIEW3D_FAR_PLANE = 50000f;
    /** Initial 3D camera offset from the view center: raised and pulled back to look slightly down at the scene. */
    private static final float VIEW3D_CAMERA_HEIGHT = 520f;
    private static final float VIEW3D_CAMERA_DISTANCE = 900f;
    /** Pan speed per pixel, scaled by camera distance / 10 inside ViewportCameraController3D. */
    private static final float VIEW3D_PAN_SENSITIVITY = 0.022f;
    /** Fly speed in world units per second. */
    private static final float VIEW3D_FLY_SPEED = 400f;
    /** Dolly distance per mouse-wheel notch. */
    private static final float VIEW3D_DOLLY_STEP = 60f;
    /** Ambient light in the editor views, brighter than the runtime default so unlit sides stay readable. */
    private static final Color EDITOR_AMBIENT_LIGHT = new Color(0.35f, 0.35f, 0.38f, 1f);

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

    private final EditorActionRegistry actionRegistry = new EditorActionRegistry();
    private final PerspectiveCamera3D sceneCamera3D;
    private final ViewportCameraController3D cameraController3D;
    private final SceneRenderer3D sceneRenderer3D;
    private final SceneViewportInteraction3D viewportInteraction3D;
    private final ViewportInteraction viewportInteraction;
    private SceneViewportWidget sceneViewport;

    private final EditorLog editorLog = new EditorLog();
    private SceneFileController sceneFiles;
    private ContextMenu renamePopup;
    private int selectedBottomTab = BOTTOM_TAB_CONTENT_BROWSER;

    private boolean isAssistantVisible = false;
    private com.badlogic.gdx.scenes.scene2d.ui.SplitPane rightSplit;

    public EditorApplication() {
        this.editorState = new EditorState();
        this.sceneCamera = OrthographicCamera2D.createScreenFilling(DEFAULT_VIEW_CENTER_X, DEFAULT_VIEW_CENTER_Y);

        // Find asset root directory ('assets' or parent/current)
        java.io.File assetRoot = findAssetRoot();
        this.assetCatalog = new io.github.ldev22live.gdxunity.engine.core.asset.AssetCatalog(assetRoot);
        this.assetService = new io.github.ldev22live.gdxunity.engine.core.asset.AssetService(assetCatalog);

        this.editorState.setAssetService(assetService);
        this.sceneRenderer = new SceneRenderer2D(sceneCamera, assetService);
        // SceneViewportWidget clears the frame buffer; the workspace draws the grid before the scene
        this.sceneRenderer.setClearEnabled(false);

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
        this.editorState.getSerializer().getRegistry().register(
            new io.github.ldev22live.gdxunity.engine.render.components.serialization.MeshPrimitiveComponentSerializer()
        );
        this.editorState.getSerializer().getRegistry().register(
            new io.github.ldev22live.gdxunity.engine.scene.lighting.serialization.DirectionalLightComponentSerializer()
        );
        this.editorState.getSerializer().getRegistry().register(
            new io.github.ldev22live.gdxunity.engine.scene.lighting.serialization.PointLightComponentSerializer()
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
        // 3D view: perspective camera with editor navigation; input is routed by the current viewport mode
        this.sceneCamera3D = new PerspectiveCamera3D(VIEW3D_FIELD_OF_VIEW, 800f, 600f, VIEW3D_NEAR_PLANE, VIEW3D_FAR_PLANE);
        this.cameraController3D = new ViewportCameraController3D(sceneCamera3D);
        cameraController3D.setPanSensitivity(VIEW3D_PAN_SENSITIVITY);
        cameraController3D.setFlyMoveSpeed(VIEW3D_FLY_SPEED);
        cameraController3D.setZoomSensitivity(VIEW3D_DOLLY_STEP);
        reset3DView();
        // 3D primitives draw in both views; the frame buffer is cleared by SceneViewportWidget
        this.sceneRenderer3D = new SceneRenderer3D(sceneCamera3D);
        sceneRenderer3D.setClearEnabled(false);
        sceneRenderer3D.setDefaultLightEnabled(true);
        sceneRenderer3D.setAmbientLight(EDITOR_AMBIENT_LIGHT);
        this.viewportInteraction3D = new SceneViewportInteraction3D(editorState, sceneCamera3D, cameraController3D, gizmoController);
        this.viewportInteraction = new ModeSwitchingViewportInteraction(editorState,
            new SceneViewportInteraction2D(editorState, sceneCamera, gizmoController, colliderController),
            viewportInteraction3D);
        // Workspace and Layout initialization
        this.workspaceManager = new io.github.ldev22live.gdxunity.editor.core.workspace.DefaultWorkspaceManager(() -> editorState);
        this.layoutSerializer = new io.github.ldev22live.gdxunity.editor.core.workspace.WorkspaceLayoutSerializer();
        this.layoutConfig = layoutSerializer.loadFromFile(layoutConfigFile);
        this.themeManager = new io.github.ldev22live.gdxunity.editor.core.theme.EditorThemeManager();

        // Register default SceneEditorWorkspace with physics debug renderer
        this.sceneEditorWorkspace = new io.github.ldev22live.gdxunity.editor.desktop.workspaces.SceneEditorWorkspace(
            sceneRenderer, sceneCamera, gizmoRenderer, new io.github.ldev22live.gdxunity.editor.desktop.ui.ViewportGridRenderer(sceneCamera), physicsDebugRenderer
        );
        this.sceneEditorWorkspace.configure3DView(sceneCamera3D,
            new io.github.ldev22live.gdxunity.editor.desktop.ui.TransformGizmoRenderer(editorState, gizmoController, sceneCamera3D),
            new ViewportGridRenderer3D(sceneCamera3D));
        this.sceneEditorWorkspace.setSelectionOutlines(
            new io.github.ldev22live.gdxunity.editor.desktop.ui.SelectionOutlineRenderer(editorState, sceneCamera),
            new io.github.ldev22live.gdxunity.editor.desktop.ui.SelectionOutlineRenderer(editorState, sceneCamera3D));
        this.sceneEditorWorkspace.setSceneRenderer3D(sceneRenderer3D);
        this.workspaceManager.registerWorkspace(sceneEditorWorkspace);

        // Centralized keyboard shortcut dispatcher
        this.keyboardDispatcher = new io.github.ldev22live.gdxunity.editor.core.input.EditorKeyboardDispatcher(() -> {
            if (uiStage != null && uiStage.getKeyboardFocus() != null) {
                return uiStage.getKeyboardFocus() instanceof com.badlogic.gdx.scenes.scene2d.ui.TextField;
            }
            return false;
        });

        // Like most 3D editors, start with the Move tool so a selection immediately shows its gizmo
        this.editorState.setToolMode(io.github.ldev22live.gdxunity.editor.core.ToolMode.MOVE);

        // Populate with initial demo scene
        Scene initialScene = createInitialScene();
        this.editorState.setActiveScene(initialScene);
        this.sceneRenderer.setActiveScene(initialScene);
        this.sceneRenderer3D.setActiveScene(initialScene);

        // Keep renderer synchronized with editor state's active scene
        this.editorState.addListener(new EditorState.Listener() {
            @Override
            public void onActiveSceneChanged(Scene oldScene, Scene newScene) {
                sceneRenderer.setActiveScene(newScene);
                sceneRenderer3D.setActiveScene(newScene);
            }
        });

        // Output Log records every status message from here on
        editorLog.captureStatusMessages(editorState);

        // Scene files (TD-022). Created after the initial scene is set so the document starts clean.
        this.sceneFiles = new SceneFileController(editorState, new TinyFileDialogService(),
            new java.io.File(System.getProperty("user.dir")));
        sceneFiles.addListener(files -> {
            if (Gdx.graphics != null) {
                Gdx.graphics.setTitle(files.getWindowTitle());
            }
        });

        // Editor actions drive menus, toolbars, shortcuts and automation
        DefaultEditorActions.registerFileActions(actionRegistry, editorState, sceneFiles, () -> Gdx.app.exit());
        DefaultEditorActions.registerDefaults(actionRegistry, editorState);
        DefaultEditorActions.registerCreate3DActions(actionRegistry, editorState, this::spawnPosition);
        registerDesktopActions();
        actionRegistry.bindShortcuts(keyboardDispatcher);

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
        // 1. Initialize renderers (they need the GL context)
        sceneRenderer.initialize();
        sceneRenderer3D.initialize();

        // 2. Build UI skin and Stage
        this.skinFactory = new EditorSkinFactory();
        this.skin = skinFactory.createSkin(themeManager.getCurrentPalette());
        this.uiStage = new Stage(new ScreenViewport());

        // 3. Scene view widget lives across UI rebuilds (theme switches) because it owns a frame buffer
        this.sceneViewport = new SceneViewportWidget(viewportInteraction, workspaceManager::resize);

        // 4. Build UI layout
        buildUi();
        Gdx.graphics.setTitle(sceneFiles.getWindowTitle());

        // 5. Configure input handling: UI stage (including the scene view) first, then global shortcuts
        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(uiStage);
        multiplexer.addProcessor(new KeyboardShortcutProcessor());
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

        // 1. Menu bar: brand + drop-down menus generated from the action registry
        Table menuRow = new Table(skin);
        menuRow.setBackground("header-bg");
        Label brand = new Label(" GdxUnity ", skin);
        brand.setColor(skin.getColor("accent"));
        menuRow.add(brand).padLeft(6).padRight(6);
        menuRow.add(new EditorMenuBar(actionRegistry, MENU_ORDER, skin)).left().expandX();
        root.add(menuRow).growX().height(MENU_BAR_HEIGHT).row();

        // 2. Main toolbar: compile left, play controls centered, panel toggles right
        root.add(new EditorToolbar(editorState, playModeController, editorLog, skin, this::toggleAssistantPanel))
            .growX().height(TOOLBAR_HEIGHT).row();

        // 3. Outliner | Scene view | Details (+ optional AI Assistant), Content Browser below
        HierarchyPanel hierarchyPanel = new HierarchyPanel(editorState, skin, actionRegistry, OUTLINER_CONTEXT_ACTIONS);

        ViewportOverlayToolbar overlay = new ViewportOverlayToolbar(editorState, actionRegistry, skin,
            DefaultEditorActions.VIEW_TOGGLE_2D_3D, ACTION_FOCUS_SELECTION, ACTION_TOGGLE_GRID, ACTION_TOGGLE_PHYSICS_DEBUG, ACTION_RESET_VIEW);
        overlay.setInfoText(() -> editorState.getViewportMode() == ViewportMode.VIEW_3D
            ? "Fly speed " + Math.round(viewportInteraction3D.getFlySpeed())
            : "");
        Container<ViewportOverlayToolbar> overlayContainer = new Container<>(overlay);
        overlayContainer.top().left().pad(6f);
        // The container covers the whole scene view; only the toolbar itself should take clicks
        overlayContainer.setTouchable(Touchable.childrenOnly);
        Stack sceneView = new Stack(sceneViewport, overlayContainer);

        com.badlogic.gdx.scenes.scene2d.ui.SplitPane leftSplit =
            new com.badlogic.gdx.scenes.scene2d.ui.SplitPane(hierarchyPanel, sceneView, false, skin);
        leftSplit.setSplitAmount(layoutConfig.getSplitRatioLeft());
        leftSplit.setMinSplitAmount(0.12f);
        leftSplit.setMaxSplitAmount(0.40f);

        InspectorPanel inspectorPanel = new InspectorPanel(editorState, skin);
        this.assistantPanel = new io.github.ldev22live.gdxunity.editor.desktop.ui.AssistantPanel(assistantService, skin);
        this.rightSplit = new com.badlogic.gdx.scenes.scene2d.ui.SplitPane(inspectorPanel, assistantPanel, true, skin);
        this.rightSplit.setSplitAmount(isAssistantVisible ? 0.55f : 1.0f);
        this.rightSplit.setMinSplitAmount(0.2f);
        this.rightSplit.setMaxSplitAmount(1.0f);

        com.badlogic.gdx.scenes.scene2d.ui.SplitPane centerRightSplit =
            new com.badlogic.gdx.scenes.scene2d.ui.SplitPane(leftSplit, rightSplit, false, skin);
        centerRightSplit.setSplitAmount(1.0f - layoutConfig.getSplitRatioRight());
        centerRightSplit.setMinSplitAmount(0.50f);
        centerRightSplit.setMaxSplitAmount(0.88f);

        // Content Browser rows can be dragged into the scene view; the drop lands under the mouse
        DragAndDrop assetDragAndDrop = new DragAndDrop();
        assetDragAndDrop.addTarget(new DragAndDrop.Target(sceneViewport) {
            @Override
            public boolean drag(DragAndDrop.Source source, DragAndDrop.Payload payload, float x, float y, int pointer) {
                return payload.getObject() instanceof AssetReference;
            }

            @Override
            public void drop(DragAndDrop.Source source, DragAndDrop.Payload payload, float x, float y, int pointer) {
                Vector3 position = viewportInteraction.worldPointAt(x, y, sceneViewport.getWidth(), sceneViewport.getHeight());
                AssetPlacement.place(editorState, (AssetReference) payload.getObject(), position);
            }
        });
        io.github.ldev22live.gdxunity.editor.desktop.ui.AssetBrowserPanel assetBrowser =
            new io.github.ldev22live.gdxunity.editor.desktop.ui.AssetBrowserPanel(editorState, assetCatalog, skin, assetDragAndDrop);

        com.badlogic.gdx.scenes.scene2d.ui.SplitPane mainSplit =
            new com.badlogic.gdx.scenes.scene2d.ui.SplitPane(centerRightSplit, new TabbedPanel(skin,
                List.of("Content Browser", "Output Log"),
                List.of(assetBrowser, new OutputLogPanel(editorLog, skin)),
                selectedBottomTab, index -> selectedBottomTab = index), true, skin);
        mainSplit.setSplitAmount(layoutConfig.getSplitRatioMain());
        mainSplit.setMinSplitAmount(0.55f);
        mainSplit.setMaxSplitAmount(0.92f);

        root.add(mainSplit).grow().row();

        // 4. Status bar
        root.add(new EditorStatusBar(editorState, skin)).growX().height(STATUS_BAR_HEIGHT);
    }

    private void registerDesktopActions() {
        actionRegistry.register(EditorAction.builder(ACTION_FOCUS_SELECTION, "Focus Selection", this::focusSelection)
            .menu(DefaultEditorActions.MENU_TOOLS, 1)
            .shortcut(Input.Keys.F, 0)
            .enabledWhen(() -> editorState.getSelectedEntity() != null)
            .build());
        actionRegistry.register(EditorAction.builder(ACTION_RENAME, "Rename", this::showRenamePopup)
            .menu(DefaultEditorActions.MENU_EDIT, 1)
            .shortcut(Input.Keys.F2, 0)
            .enabledWhen(() -> !editorState.isPlayModeActive() && editorState.getSelectedEntity() != null)
            .build());
        actionRegistry.register(EditorAction.builder(ACTION_RESET_VIEW, "Reset View", this::resetView)
            .menu(DefaultEditorActions.MENU_TOOLS, 1)
            .build());
        actionRegistry.register(EditorAction.builder(ACTION_TOGGLE_GRID, "Grid", this::toggleGrid)
            .menu(DefaultEditorActions.MENU_WINDOW, 0)
            .build());
        actionRegistry.register(EditorAction.builder(ACTION_TOGGLE_PHYSICS_DEBUG, "Physics Debug", this::togglePhysicsDebug)
            .menu(DefaultEditorActions.MENU_WINDOW, 0)
            .build());
        actionRegistry.register(EditorAction.builder(ACTION_TOGGLE_ASSISTANT, "AI Assistant", this::toggleAssistantPanel)
            .menu(DefaultEditorActions.MENU_WINDOW, 1)
            .build());
        // Rebuilding the UI from inside a menu click would remove the actor handling the event; defer it
        actionRegistry.register(EditorAction.builder(ACTION_TOGGLE_THEME, "Toggle Dark/Light Theme",
                () -> Gdx.app.postRunnable(this::toggleTheme))
            .menu(DefaultEditorActions.MENU_WINDOW, 2)
            .build());
    }

    private void toggleTheme() {
        io.github.ldev22live.gdxunity.editor.core.theme.EditorThemeMode next =
            (themeManager.getCurrentMode() == io.github.ldev22live.gdxunity.editor.core.theme.EditorThemeMode.DARK)
                ? io.github.ldev22live.gdxunity.editor.core.theme.EditorThemeMode.LIGHT
                : io.github.ldev22live.gdxunity.editor.core.theme.EditorThemeMode.DARK;
        switchTheme(next);
    }

    /** Small popup with the selected entity's name; Enter or OK renames it as one undoable command. */
    private void showRenamePopup() {
        Entity selected = editorState.getSelectedEntity();
        if (selected == null || uiStage == null) {
            return;
        }
        if (renamePopup == null) {
            renamePopup = new ContextMenu(skin);
        }
        TextField nameField = new TextField(selected.getName(), skin);
        TextButton okButton = new TextButton("Rename", skin, "primary");
        Runnable apply = () -> {
            String newName = nameField.getText().trim();
            renamePopup.close();
            if (!newName.isEmpty() && !newName.equals(selected.getName())) {
                editorState.executeCommand(new RenameEntityCommand(editorState, selected.getId(), selected.getName(), newName));
            }
        };
        nameField.setTextFieldListener((field, c) -> {
            if (c == '\r' || c == '\n') {
                apply.run();
            }
        });
        okButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                apply.run();
            }
        });
        Table content = new Table(skin);
        content.add(new Label("Rename " + selected.getName(), skin, "section")).left().padBottom(4).row();
        content.add(nameField).width(RENAME_FIELD_WIDTH).height(24).padBottom(4).row();
        content.add(okButton).right().height(24);
        renamePopup.showContent(uiStage, uiStage.getWidth() / 2f - RENAME_FIELD_WIDTH / 2f, uiStage.getHeight() * 0.6f, content);
        uiStage.setKeyboardFocus(nameField);
        nameField.selectAll();
    }

    /**
     * Called when the window close button is pressed. Offers to save unsaved changes.
     *
     * @return true to let the window close
     */
    public boolean confirmClose() {
        return sceneFiles == null || sceneFiles.confirmDiscardChanges();
    }

    public SceneFileController getSceneFiles() {
        return sceneFiles;
    }

    public EditorLog getEditorLog() {
        return editorLog;
    }

    private void resetView() {
        sceneCamera.setPosition(DEFAULT_VIEW_CENTER_X, DEFAULT_VIEW_CENTER_Y, 0f);
        sceneCamera.setZoom(1.0f);
        sceneCamera.update();
        reset3DView();
        editorState.setStatusMessage("Camera view reset", false);
    }

    /** Frames the selected entity: the 3D camera backs off along its view direction, the 2D view zooms to fit. */
    private void focusSelection() {
        Entity selected = editorState.getSelectedEntity();
        if (selected == null) {
            return;
        }
        Vector3 center = ViewportFocus.focusCenter(selected);
        float radius = ViewportFocus.boundingRadius(selected);
        if (editorState.getViewportMode() == ViewportMode.VIEW_3D) {
            float distance = ViewportFocus.perspectiveDistance(radius, sceneCamera3D.getFieldOfView());
            Vector3 direction = sceneCamera3D.getDirection().nor();
            sceneCamera3D.setPosition(new Vector3(center).mulAdd(direction, -distance));
            sceneCamera3D.update();
            cameraController3D.setTarget(center);
        } else {
            sceneCamera.setPosition(center.x, center.y, sceneCamera.getPosition().z);
            if (sceneViewport != null && sceneViewport.getWidth() > 0f && sceneViewport.getHeight() > 0f) {
                sceneCamera.setZoom(ViewportFocus.orthographicZoom(2f * radius, 2f * radius,
                    sceneViewport.getWidth(), sceneViewport.getHeight()));
            }
            sceneCamera.update();
        }
        editorState.setStatusMessage("Focused on " + selected.getName(), false);
    }

    /** Where Create menu entities appear: the orbit target in 3D, the center of the 2D view otherwise. */
    private Vector3 spawnPosition() {
        if (editorState.getViewportMode() == ViewportMode.VIEW_3D) {
            return cameraController3D.getTarget();
        }
        Vector3 center = sceneCamera.getPosition();
        return new Vector3(center.x, center.y, 0f);
    }

    /** Places the 3D camera in front of and above the view center, looking at it. */
    private void reset3DView() {
        sceneCamera3D.setPosition(DEFAULT_VIEW_CENTER_X, VIEW3D_CAMERA_HEIGHT, VIEW3D_CAMERA_DISTANCE);
        sceneCamera3D.setUp(Vector3.Y);
        sceneCamera3D.lookAt(DEFAULT_VIEW_CENTER_X, DEFAULT_VIEW_CENTER_Y, 0f);
        sceneCamera3D.update();
        cameraController3D.setTarget(DEFAULT_VIEW_CENTER_X, DEFAULT_VIEW_CENTER_Y, 0f);
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

        // The scene view sizes its own camera from its panel (SceneViewportWidget), so only the UI follows the window
        if (uiStage != null) {
            uiStage.getViewport().update(width, height, true);
        }
    }

    @Override
    public void render() {
        Color background = themeManager.getCurrentPalette().getBackground();
        float delta = Gdx.graphics.getDeltaTime();

        // 1. Advance Play Mode simulation if active
        if (playModeController.isPlaying()) {
            playModeController.update(delta);
        }
        viewportInteraction.update(delta);

        // 2. Render the active workspace off-screen into the scene view panel
        workspaceManager.update(delta);
        if (sceneViewport != null) {
            sceneViewport.renderScene(background, () -> workspaceManager.render(delta));
        }

        // 3. Clear the window and draw the editor UI (which shows the scene view texture)
        Gdx.gl.glClearColor(background.r, background.g, background.b, 1.0f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        if (uiStage != null) {
            uiStage.act(delta);
            uiStage.getViewport().apply();
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
        if (sceneRenderer3D != null) {
            sceneRenderer3D.dispose();
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
        if (sceneViewport != null) {
            sceneViewport.dispose();
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

    public EditorActionRegistry getActionRegistry() {
        return actionRegistry;
    }

    public io.github.ldev22live.gdxunity.editor.core.agent.service.AssistantService getAssistantService() {
        return assistantService;
    }

    public io.github.ldev22live.gdxunity.editor.desktop.ui.AssistantPanel getAssistantPanel() {
        return assistantPanel;
    }

    // --- Global keyboard shortcuts (pointer input is handled by SceneViewportWidget) ---

    private class KeyboardShortcutProcessor extends InputAdapter {
        @Override
        public boolean keyDown(int keycode) {
            int modifiers = 0;
            if (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT)) {
                modifiers |= EditorKeyboardDispatcher.MOD_CTRL;
            }
            if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)) {
                modifiers |= EditorKeyboardDispatcher.MOD_SHIFT;
            }
            if (Gdx.input.isKeyPressed(Input.Keys.ALT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.ALT_RIGHT)) {
                modifiers |= EditorKeyboardDispatcher.MOD_ALT;
            }
            return keyboardDispatcher.dispatch(keycode, modifiers);
        }
    }
}
