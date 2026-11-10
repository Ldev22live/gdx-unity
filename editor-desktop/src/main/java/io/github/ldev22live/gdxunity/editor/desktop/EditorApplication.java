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
import io.github.ldev22live.gdxunity.editor.core.asset.AssetChangeWatcher;
import io.github.ldev22live.gdxunity.editor.core.asset.AssetPlacement;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetType;
import io.github.ldev22live.gdxunity.editor.core.component.ComponentCatalog;
import io.github.ldev22live.gdxunity.editor.core.inspector.ComponentEditorRegistry;
import io.github.ldev22live.gdxunity.editor.desktop.ui.graph.GraphEditorPanel;
import io.github.ldev22live.gdxunity.editor.desktop.ui.SkeletonOverlayRenderer;
import io.github.ldev22live.gdxunity.editor.desktop.ui.inspector.AnimatorComponentEditorProvider;
import io.github.ldev22live.gdxunity.editor.desktop.ui.inspector.ModelComponentEditorProvider;
import io.github.ldev22live.gdxunity.editor.desktop.ui.timeline.AnimationsPanel;
import io.github.ldev22live.gdxunity.editor.desktop.ui.timeline.CharacterCreatorPanel;
import io.github.ldev22live.gdxunity.editor.desktop.ui.timeline.TimelinePanel;
import io.github.ldev22live.gdxunity.editor.desktop.ui.music.MusicStudioPanel;
import io.github.ldev22live.gdxunity.editor.audio.MusicSession;
import io.github.ldev22live.gdxunity.editor.animation.AnimationAssets;
import io.github.ldev22live.gdxunity.editor.animation.ClipLibrary;
import io.github.ldev22live.gdxunity.editor.core.viewport.ViewportLayout;
import io.github.ldev22live.gdxunity.editor.desktop.ui.viewport.MultiViewportArea;
import io.github.ldev22live.gdxunity.editor.desktop.ui.viewport.ScenePaneFactory;
import io.github.ldev22live.gdxunity.editor.sculpt.SculptActions;
import io.github.ldev22live.gdxunity.editor.sculpt.SculptAssets;
import io.github.ldev22live.gdxunity.editor.sculpt.SculptSession;
import io.github.ldev22live.gdxunity.editor.sculpt.SculptViewportInteraction;
import io.github.ldev22live.gdxunity.editor.animation.AnimationPlayModeExtension;
import io.github.ldev22live.gdxunity.editor.animation.TimelineSession;
import io.github.ldev22live.gdxunity.engine.animation.AnimatedProperties;
import io.github.ldev22live.gdxunity.engine.animation.AnimatorComponent;
import io.github.ldev22live.gdxunity.engine.desktop.GameComponentSerializers;
import io.github.ldev22live.gdxunity.engine.render.ModelAssets;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.editor.desktop.ui.inspector.GraphScriptComponentEditorProvider;
import io.github.ldev22live.gdxunity.editor.visualscript.GraphAssets;
import io.github.ldev22live.gdxunity.editor.visualscript.GraphExecutionTrace;
import io.github.ldev22live.gdxunity.editor.visualscript.GraphPlayModeExtension;
import io.github.ldev22live.gdxunity.editor.visualscript.ProjectNodeCatalog;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphScriptComponent;
import io.github.ldev22live.gdxunity.engine.visualscript.NodeRegistry;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.ViewportMode;
import io.github.ldev22live.gdxunity.editor.core.action.DefaultEditorActions;
import io.github.ldev22live.gdxunity.editor.core.prefab.PrefabService;
import io.github.ldev22live.gdxunity.editor.core.play.GameViewPlayModeExtension;
import io.github.ldev22live.gdxunity.editor.core.project.EditorLaunchOptions;
import io.github.ldev22live.gdxunity.editor.core.project.EditorProject;
import io.github.ldev22live.gdxunity.editor.core.project.GameExportCommand;
import io.github.ldev22live.gdxunity.editor.core.project.GameExporter;
import io.github.ldev22live.gdxunity.editor.core.project.PlatformDirectories;
import io.github.ldev22live.gdxunity.engine.scene.lighting.SkyLightComponent;
import io.github.ldev22live.gdxunity.engine.scene.lighting.ToneMappingComponent;
import io.github.ldev22live.gdxunity.editor.core.action.EditorAction;
import io.github.ldev22live.gdxunity.editor.core.action.EditorActionRegistry;
import io.github.ldev22live.gdxunity.editor.core.command.RenameEntityCommand;
import io.github.ldev22live.gdxunity.editor.core.GameModeProfile;
import io.github.ldev22live.gdxunity.editor.core.file.EditorDialogService;
import io.github.ldev22live.gdxunity.editor.core.file.NonInteractiveDialogService;
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
import io.github.ldev22live.gdxunity.editor.core.play.PlayModeState;
import io.github.ldev22live.gdxunity.editor.desktop.ui.EditorSkinFactory;
import io.github.ldev22live.gdxunity.editor.desktop.ui.EditorStatusBar;
import io.github.ldev22live.gdxunity.editor.core.ToolMode;
import io.github.ldev22live.gdxunity.editor.desktop.ui.ActionTextButton;
import io.github.ldev22live.gdxunity.editor.desktop.ui.EditorToolbar;
import io.github.ldev22live.gdxunity.editor.desktop.ui.HierarchyPanel;
import io.github.ldev22live.gdxunity.editor.desktop.file.TinyFileDialogService;
import io.github.ldev22live.gdxunity.editor.desktop.ui.InspectorPanel;
import io.github.ldev22live.gdxunity.editor.desktop.ui.OutputLogPanel;
import io.github.ldev22live.gdxunity.editor.desktop.ui.TabbedPanel;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.editor.desktop.ui.menu.ContextMenu;
import io.github.ldev22live.gdxunity.editor.desktop.ui.menu.EditorMenuBar;
import io.github.ldev22live.gdxunity.editor.desktop.ui.viewport.SceneViewportWidget;
import io.github.ldev22live.gdxunity.editor.desktop.ui.viewport.ViewportGridRenderer3D;
import io.github.ldev22live.gdxunity.editor.desktop.ui.viewport.SculptToolbar;
import io.github.ldev22live.gdxunity.editor.desktop.ui.viewport.ViewportOverlayToolbar;
import com.badlogic.gdx.graphics.Color;
import io.github.ldev22live.gdxunity.engine.render.components.ShapeRendererComponent;
import io.github.ldev22live.gdxunity.engine.render.SceneRenderer2D;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import io.github.ldev22live.gdxunity.editor.desktop.ui.GameModeScreen;
import io.github.ldev22live.gdxunity.editor.desktop.ui.GameModeSelector;
import io.github.ldev22live.gdxunity.editor.desktop.ui.sprite.SpriteStudioPanel;
import io.github.ldev22live.gdxunity.editor.desktop.ui.sprite.TilemapStudioPanel;
import io.github.ldev22live.gdxunity.engine.render.SceneRenderer3D;
import io.github.ldev22live.gdxunity.engine.render.camera.OrthographicCamera2D;
import io.github.ldev22live.gdxunity.engine.render.camera.PerspectiveCamera3D;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;

import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

    /** Height of, and gap between, the labelled buttons in the top bar. */
    static final float BAR_BUTTON_HEIGHT = 26f;
    static final float BAR_BUTTON_GAP = 4f;

    static final String ACTION_RESET_VIEW = "view.reset";
    static final String ACTION_TOGGLE_GRID = "window.toggle-grid";
    static final String ACTION_TOGGLE_PHYSICS_DEBUG = "window.toggle-physics-debug";
    static final String ACTION_TOGGLE_ASSISTANT = "window.toggle-assistant";
    static final String ACTION_TOGGLE_THEME = "window.toggle-theme";
    static final String ACTION_RENAME = "edit.rename";
    /** Opens the mode screen: pick 2D, 2.5D or 3D for a new scene (TD-067). */
    static final String ACTION_NEW_SCENE_SCREEN = "file.new-scene-screen";
    static final String ACTION_FOCUS_SELECTION = "view.focus-selection";

    /** Actions in the Outliner right-click menu. */
    static final List<String> OUTLINER_CONTEXT_ACTIONS = List.of(
        DefaultEditorActions.CREATE_EMPTY_ENTITY, ACTION_RENAME,
        DefaultEditorActions.EDIT_DUPLICATE, DefaultEditorActions.EDIT_DELETE,
        DefaultEditorActions.PREFAB_CREATE, DefaultEditorActions.PREFAB_APPLY,
        DefaultEditorActions.PREFAB_REVERT, DefaultEditorActions.PREFAB_UNPACK);
    private static final int BOTTOM_TAB_CONTENT_BROWSER = 0;
    private static final int CENTER_TAB_VIEWPORT = 0;
    /** The game as it plays: Play switches to it and Stop switches back. */
    private static final int CENTER_TAB_SCENE = 1;
    /** Clip library and Timeline (TD-044). */
    private static final int CENTER_TAB_ANIMATIONS = 2;
    /** The node graph editor, with code generation (TD-044). */
    private static final int CENTER_TAB_GRAPH = 3;
    private static final int CENTER_TAB_MUSIC = 4;
    /** The Sprites tab (TD-066). */
    private static final int CENTER_TAB_SPRITES = 5;
    /** The Tiles tab (TD-066, milestone E). */
    private static final int CENTER_TAB_TILES = 6;
    /** System property naming a song to open at startup (a testing aid, not a user setting). */
    static final String OPEN_SONG_PROPERTY = "gdxunity.openSong";

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
    /** Sky light intensity of the startup scene: about half, so sunlit and shaded sides stay distinct. */
    static final float INITIAL_SKY_INTENSITY = 0.5f;
    /** Editor layout, stored per user in {@link PlatformDirectories#settingsDir()} (TD-033). */
    static final String LAYOUT_FILE_NAME = "workspace_layout.json";
    /** Where the arrangement of the 3D view panes is saved, next to the other editor settings. */
    static final String VIEWPORT_LAYOUT_FILE_NAME = "viewport_layout.json";
    /** Editor theme, stored per user in {@link PlatformDirectories#settingsDir()} (TD-033). */
    static final String THEME_FILE_NAME = "editor_theme.json";
    /** Output Log source name for startup and smoke-test messages. */
    private static final String LOG_SOURCE = "Editor";
    /** Folder of sample game scenes inside the project, offered by File → Open Sample (TD-034). */
    static final String SAMPLES_FOLDER = "samples";

    private final EditorState editorState;
    private final OrthographicCamera2D sceneCamera;
    private final SceneRenderer2D sceneRenderer;

    private final io.github.ldev22live.gdxunity.engine.core.asset.AssetCatalog assetCatalog;
    /** Notices assets edited, added or deleted on disk while the editor runs, and reloads the ones in use (Phase 13-C). */
    private final AssetChangeWatcher assetWatcher;
    /** Pictures of the project's models for the Content Browser, drawn on demand (Phase 13-C). */
    private final io.github.ldev22live.gdxunity.editor.desktop.ui.ModelThumbnails modelThumbnails;
    private io.github.ldev22live.gdxunity.editor.desktop.ui.AssetBrowserPanel assetBrowserPanel;
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
    private final File layoutConfigFile;
    /** The open project (TD-033): asset root, scene dialogs and scripts are inside it. */
    private final EditorProject project;
    private final EditorLaunchOptions launchOptions;
    /** Frames rendered so far; only counted in smoke-test mode. */
    private int smokeTestFramesRendered;
    /** Errors seen when a smoke test ended; read by the launcher after the application loop returns. */
    private List<EditorLog.Entry> smokeTestErrors = List.of();
    /** Set by {@code --play}: Play starts on the first frame, once the UI exists. */
    private boolean playOnFirstFrame;

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
    /** The Scene tab's view: the playing game through its camera, drawn by the same workspace as the Viewport. */
    private SceneViewportWidget gameViewport;
    /** The panes of the 3D scene view (TD-039); shown instead of the single view while editing in 3D. */
    private MultiViewportArea multiViewport;
    private ViewportLayout viewportLayout;
    private File viewportLayoutFile;
    private ScenePaneFactory scenePaneFactory;

    private final EditorLog editorLog = new EditorLog();
    private SceneFileController sceneFiles;
    /** Native file dialogs, or a non-interactive stand-in in smoke tests; also used by the Visual Scripting import (TD-045). */
    private EditorDialogService editorDialogs;
    private ContextMenu renamePopup;
    private GameModeScreen gameModeScreen;
    private int selectedBottomTab = BOTTOM_TAB_CONTENT_BROWSER;

    /** Share of the right column Details keeps while the AI Assistant is open. */
    private static final float DETAILS_SPLIT_WITH_ASSISTANT = 0.55f;
    /** Least of the right column Details keeps when the divider is dragged. */
    private static final float ASSISTANT_SPLIT_MIN = 0.2f;
    private boolean isAssistantVisible = false;
    private com.badlogic.gdx.scenes.scene2d.ui.SplitPane rightSplit;

    // Visual scripting (TD-029)
    private final NodeRegistry nodeRegistry = NodeRegistry.createDefault();
    /** Method nodes of the project's catalog file and compiled scripts (TD-060); read again by the Graph tab's Refresh Nodes. */
    private final ProjectNodeCatalog projectNodes;
    private final GraphAssets graphAssets;
    private final GraphExecutionTrace graphTrace = new GraphExecutionTrace();
    private GraphEditorPanel graphEditorPanel;
    /** Imported glTF models, shared by the 3D renderer and the Details panel (TD-030); disposed after the renderer. */
    private final ModelAssets modelAssets;
    /** The sculpts open in the editor and the sculpt brush (TD-038); also the source of their geometry. */
    private final SculptSession sculptSession;
    private final TimelineSession timelineSession;
    private TimelinePanel timelinePanel;
    private AnimationsPanel animationsPanel;
    /** The open song of the music editor (TD-040); it outlives UI rebuilds. */
    private final MusicSession musicSession;
    private MusicStudioPanel musicPanel;
    private SpriteStudioPanel spritePanel;
    private TilemapStudioPanel tilemapPanel;
    private final io.github.ldev22live.gdxunity.editor.desktop.ui.SpriteThumbnails spriteThumbnails;
    private TabbedPanel bottomTabs;
    private TabbedPanel centerTabs;
    /** The "Press Play" hint of the Scene tab; hidden while a game plays. */
    private Container<Label> sceneHintContainer;
    private int selectedCenterTab = CENTER_TAB_VIEWPORT;

    /**
     * @param project     the project to open; its folder is the asset root
     * @param directories per-user folders; layout and theme are stored in {@link PlatformDirectories#settingsDir()}
     */
    public EditorApplication(EditorProject project, PlatformDirectories directories, EditorLaunchOptions launchOptions) {
        this.project = Objects.requireNonNull(project, "project cannot be null");
        this.projectNodes = new ProjectNodeCatalog(nodeRegistry, project.getRoot(), project.getScriptsBuildDir());
        projectNodes.refresh();
        Objects.requireNonNull(directories, "directories cannot be null");
        this.launchOptions = Objects.requireNonNull(launchOptions, "launchOptions cannot be null");
        this.layoutConfigFile = new File(directories.settingsDir(), LAYOUT_FILE_NAME);
        this.editorState = new EditorState();
        this.sceneCamera = OrthographicCamera2D.createScreenFilling(DEFAULT_VIEW_CENTER_X, DEFAULT_VIEW_CENTER_Y);

        File assetRoot = project.getRoot();
        this.assetCatalog = new io.github.ldev22live.gdxunity.engine.core.asset.AssetCatalog(assetRoot);
        this.assetService = new io.github.ldev22live.gdxunity.engine.core.asset.AssetService(assetCatalog);
        this.assetWatcher = new AssetChangeWatcher(assetCatalog, AssetChangeWatcher.DEFAULT_INTERVAL_SECONDS);
        assetWatcher.addListener(this::onAssetsChangedOnDisk);
        this.modelThumbnails = new io.github.ldev22live.gdxunity.editor.desktop.ui.ModelThumbnails(assetCatalog);
        this.spriteThumbnails = new io.github.ldev22live.gdxunity.editor.desktop.ui.SpriteThumbnails(assetCatalog);

        this.editorState.setAssetService(assetService);
        this.editorState.setPrefabService(new PrefabService(editorState, assetRoot));
        this.graphAssets = new GraphAssets(assetRoot);
        registerComponentSerializers(editorState.getSerializer().getRegistry());
        this.modelAssets = new ModelAssets(assetRoot);
        this.sculptSession = new SculptSession(new SculptAssets(assetRoot));
        this.musicSession = new MusicSession(assetRoot.toPath());
        // Testing aid: -Dgdxunity.openSong=music/demo.song opens that song and shows the Music tab at startup
        String openSong = System.getProperty(OPEN_SONG_PROPERTY);
        if (openSong != null && musicSession.open(openSong)) {
            selectedCenterTab = CENTER_TAB_MUSIC;
        }
        // Testing aid (TD-054): GDXUNITY_EDITOR_TAB=Animations starts on that center tab (Viewport, Scene, Animations, Graph, Music)
        String startTab = System.getenv(START_TAB_VARIABLE);
        if (startTab != null && !startTab.isBlank()) {
            selectedCenterTab = centerTabIndex(startTab);
        }
        this.timelineSession = new TimelineSession(editorState, new AnimationAssets(assetRoot), AnimatedProperties.createDefault());
        this.sceneRenderer = new SceneRenderer2D(sceneCamera, assetService);
        // SceneViewportWidget clears the frame buffer; the workspace draws the grid before the scene
        this.sceneRenderer.setClearEnabled(false);


        // Script sources in <project>/scripts, compiled classes in <project>/build/scripts
        io.github.ldev22live.gdxunity.editor.core.scripting.ScriptManager scriptManager =
            new io.github.ldev22live.gdxunity.editor.core.scripting.ScriptManager(
                project.getScriptsDir(), project.getScriptsBuildDir());
        this.editorState.setScriptManager(scriptManager);

        this.playModeController = new PlayModeController(editorState);
        this.editorState.setPlayModeController(playModeController);
        // Graphs run from their files: save the one being edited before play loads them
        playModeController.addExtension(new PlayModeController.Extension() {
            @Override
            public List<String> onPlay(Scene runtimeScene, PlayModeController controller) {
                return graphEditorPanel == null || graphEditorPanel.saveIfDirty()
                    ? List.of() : List.of("the open graph could not be saved");
            }

            @Override
            public void onStop(Scene runtimeScene) {
            }
        });
        playModeController.addExtension(new GraphPlayModeExtension(editorState, graphAssets, nodeRegistry, editorLog, graphTrace));
        // Ends the Timeline preview before the scene is copied, saves the open clip, binds Animators (TD-031)
        playModeController.addExtension(new AnimationPlayModeExtension(timelineSession, editorLog));
        // A scene with a Camera component plays through it, in the matching 2D or 3D view (TD-034)
        playModeController.addExtension(new GameViewPlayModeExtension(editorState));
        // Audio Sources play their sound effects while the scene plays (TD-043)
        playModeController.addExtension(new AudioPlayModeExtension(editorLog, io.github.ldev22live.gdxunity.engine.core.asset.AssetSource.directory(project.getRoot())));
        // The game plays in the Scene tab: Play opens it from the Viewport, Stop goes back
        playModeController.addListener((oldState, newState) -> {
            if (centerTabs == null) {
                return;
            }
            if (newState == PlayModeState.PLAYING && selectedCenterTab == CENTER_TAB_VIEWPORT) {
                showCenterTab(CENTER_TAB_SCENE);
            } else if (newState == PlayModeState.STOPPED && selectedCenterTab == CENTER_TAB_SCENE) {
                showCenterTab(CENTER_TAB_VIEWPORT);
            }
        });

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
        sceneRenderer3D.setModelAssets(modelAssets);
        sceneRenderer3D.setDynamicMeshSource(sculptSession);
        this.viewportInteraction3D = new SceneViewportInteraction3D(editorState, sceneCamera3D, cameraController3D, gizmoController);
        this.viewportInteraction = new ModeSwitchingViewportInteraction(editorState,
            // The Tiles tab can paint in this view (TD-066); with it off this is the ordinary 2D interaction
            new io.github.ldev22live.gdxunity.editor.sprite.TilePaintInteraction(
                new SceneViewportInteraction2D(editorState, sceneCamera, gizmoController, colliderController),
                () -> tilemapPanel == null ? null : tilemapPanel.getPaintTarget()),
            new SculptViewportInteraction(editorState, sculptSession, sceneCamera3D, viewportInteraction3D));
        // Workspace and Layout initialization
        this.workspaceManager = new io.github.ldev22live.gdxunity.editor.core.workspace.DefaultWorkspaceManager(() -> editorState);
        this.layoutSerializer = new io.github.ldev22live.gdxunity.editor.core.workspace.WorkspaceLayoutSerializer();
        this.layoutConfig = layoutSerializer.loadFromFile(layoutConfigFile);
        this.themeManager = new io.github.ldev22live.gdxunity.editor.core.theme.EditorThemeManager(
            new File(directories.settingsDir(), THEME_FILE_NAME));

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
        this.sceneEditorWorkspace.setSkeletonOverlay3D(new SkeletonOverlayRenderer(editorState, sceneCamera3D, sceneRenderer3D));
        this.sceneEditorWorkspace.setColliderRenderer3D(new io.github.ldev22live.gdxunity.editor.desktop.ui.Collider3DRenderer(
            editorState, sceneCamera3D, physicsDebugRenderer::isDebugVisible));
        this.viewportLayoutFile = new File(directories.settingsDir(), VIEWPORT_LAYOUT_FILE_NAME);
        this.viewportLayout = loadViewportLayout();
        this.scenePaneFactory = new ScenePaneFactory(editorState, gizmoController, sculptSession, sceneRenderer3D,
            sceneEditorWorkspace, physicsDebugRenderer::isDebugVisible,
            new ScenePaneFactory.MainPerspective(sceneCamera3D, cameraController3D,
                new SculptViewportInteraction(editorState, sculptSession, sceneCamera3D, viewportInteraction3D),
                this::reset3DView),
            new Vector3(DEFAULT_VIEW_CENTER_X, DEFAULT_VIEW_CENTER_Y, 0f));
        // The editor starts in the 3D view with its four panes: perspective, X axis, Y axis, Z axis
        editorState.setViewportMode(ViewportMode.VIEW_3D);
        this.workspaceManager.registerWorkspace(sceneEditorWorkspace);

        // Centralized keyboard shortcut dispatcher
        this.keyboardDispatcher = new io.github.ldev22live.gdxunity.editor.core.input.EditorKeyboardDispatcher(() -> {
            if (uiStage != null && uiStage.getKeyboardFocus() != null) {
                // Text fields take typing; the graph editor handles its own Delete and Ctrl+Z / Ctrl+Y
                Actor focus = uiStage.getKeyboardFocus();
                return focus instanceof com.badlogic.gdx.scenes.scene2d.ui.TextField
                    || (graphEditorPanel != null && focus.isDescendantOf(graphEditorPanel))
                    || (timelinePanel != null && focus.isDescendantOf(timelinePanel));
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

            @Override
            public void onGameModeChanged(GameMode oldMode, GameMode newMode) {
                // 2.5D: the 3D view looks side-on and only pans and zooms
                cameraController3D.setSideViewLocked(GameModeProfile.of(newMode).isSideViewLocked());
            }
        });

        // Output Log records every status message from here on
        editorLog.captureStatusMessages(editorState);

        // Scene files (TD-022). Created after the initial scene is set so the document starts clean.
        // Smoke tests run unattended: a modal dialog would wait forever (errors still reach the Output Log)
        EditorDialogService dialogs = launchOptions.isSmokeTest() ? new NonInteractiveDialogService() : new TinyFileDialogService();
        this.editorDialogs = dialogs;
        this.sceneFiles = new SceneFileController(editorState, dialogs, project.getRoot());
        editorLog.info(LOG_SOURCE, "Project: " + project.getRoot().getPath());
        sceneFiles.addListener(files -> {
            if (Gdx.graphics != null) {
                Gdx.graphics.setTitle(files.getWindowTitle());
            }
        });
        // A Timeline preview pose is never written to the scene file
        sceneFiles.addBeforeSaveHook(timelineSession::endPreview);
        if (launchOptions.scene() != null) {
            File scene = launchOptions.scene().isAbsolute()
                ? launchOptions.scene() : new File(project.getRoot(), launchOptions.scene().getPath());
            // A failure is reported in the Output Log (and fails a smoke test)
            sceneFiles.openFile(scene);
        }
        this.playOnFirstFrame = launchOptions.play();
        // Testing aid (TD-054): GDXUNITY_EDITOR_SELECT=<entity name> selects that entity once the scene is open
        String selectName = System.getenv(SELECT_VARIABLE);
        if (selectName != null && !selectName.isBlank()) {
            Entity toSelect = editorState.getActiveScene().findEntityByName(selectName);
            if (toSelect == null) {
                editorLog.error(LOG_SOURCE, SELECT_VARIABLE + ": no entity named " + selectName);
            } else {
                editorState.setSelectedEntity(toSelect);
            }
        }

        // Editor actions drive menus, toolbars, shortcuts and automation
        DefaultEditorActions.registerFileActions(actionRegistry, editorState, sceneFiles, () -> {
            if (saveSculptsBeforeClose() && saveMusicBeforeClose()) {
                Gdx.app.exit();
            }
        });
        DefaultEditorActions.registerSampleActions(actionRegistry, editorState, sceneFiles,
            new File(project.getRoot(), SAMPLES_FOLDER));
        DefaultEditorActions.registerDefaults(actionRegistry, editorState);
        DefaultEditorActions.registerCreate3DActions(actionRegistry, editorState, this::spawnPosition);
        SculptActions.register(actionRegistry, editorState, sculptSession, this::spawnPosition);
        SculptActions.registerExport(actionRegistry, editorState, sculptSession, this::onSculptExported);
        SculptActions.registerFromSelected(actionRegistry, editorState, sculptSession,
            new io.github.ldev22live.gdxunity.engine.render.ColliderGeometry(modelAssets));
        DefaultEditorActions.registerLightingActions(actionRegistry, editorState, sceneRenderer3D);
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

        add3DSampleContent(scene);
        return scene;
    }

    /** Path of the sample clip the startup scene's ball plays (shipped in {@code assets/animations}). */
    static final String SAMPLE_BALL_CLIP = "animations/BallBounce.anim";
    /** Path of the sample skinned model shipped in {@code assets/models}. */
    static final String SAMPLE_MODEL = "models/BendingStrip.gltf";

    /**
     * 3D objects for the 3D view: they stand on the grid (y = 0) in front of the 2D sample, where the default 3D
     * camera looks. Press Play to see the ball bounce (Animator, Timeline clip) and the strip bend (its own clip).
     */
    static void add3DSampleContent(Scene scene) {
        Entity floor = scene.createEntity("Floor");
        floor.getTransform().setLocalPosition(320f, 0f, 150f);
        floor.addComponent(new MeshPrimitiveComponent(MeshPrimitiveComponent.Shape.PLANE, 900f, 1f, 500f,
            new Color(0.32f, 0.34f, 0.38f, 1f)));

        Entity crate = scene.createEntity("Crate");
        crate.getTransform().setLocalPosition(130f, 50f, 220f);
        crate.getTransform().setLocalEulerAngles(30f, 0f, 0f);
        crate.addComponent(new MeshPrimitiveComponent(MeshPrimitiveComponent.Shape.BOX, 100f, 100f, 100f,
            new Color(0.25f, 0.5f, 0.85f, 1f)));

        Entity ball = scene.createEntity("Ball");
        ball.getTransform().setLocalPosition(510f, 50f, 220f);
        ball.addComponent(new MeshPrimitiveComponent(MeshPrimitiveComponent.Shape.SPHERE, 100f, 100f, 100f,
            new Color(0.9f, 0.3f, 0.3f, 1f)));
        ball.addComponent(new AnimatorComponent(SAMPLE_BALL_CLIP));

        Entity strip = scene.createEntity("BendingStrip");
        strip.getTransform().setLocalPosition(270f, 0f, 260f);
        io.github.ldev22live.gdxunity.engine.render.components.ModelComponent model =
            new io.github.ldev22live.gdxunity.engine.render.components.ModelComponent(SAMPLE_MODEL);
        model.setAnimation("Bend");
        strip.addComponent(model);

        Entity sun = scene.createEntity("Sun");
        sun.getTransform().setLocalPosition(320f, 600f, 300f);
        sun.addComponent(new io.github.ldev22live.gdxunity.engine.scene.lighting.DirectionalLightComponent());

        // Sky light and filmic tone mapping (Phase 13-B); the sky is dimmed so the sun still models the shapes
        Entity lighting = scene.createEntity("Sky Light");
        SkyLightComponent sky = new SkyLightComponent();
        sky.setIntensity(INITIAL_SKY_INTENSITY);
        lighting.addComponent(sky);
        lighting.addComponent(new ToneMappingComponent());
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
        if (gameViewport != null) {
            gameViewport.dispose();
        }
        this.gameViewport = new SceneViewportWidget(new NoInteraction(), workspaceManager::resize);
        if (multiViewport != null) {
            multiViewport.dispose();
        }
        this.multiViewport = new MultiViewportArea(skin, viewportLayout, scenePaneFactory);
        multiViewport.setOnLayoutChanged(this::saveViewportLayout);

        // 4. Build UI layout
        buildUi();
        Gdx.graphics.setTitle(sceneFiles.getWindowTitle());

        // 5. Configure input handling: UI stage (including the scene view) first, then global shortcuts
        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(uiStage);
        multiplexer.addProcessor(new KeyboardShortcutProcessor());
        Gdx.input.setInputProcessor(multiplexer);
    }

    /**
     * Registers a serializer for every component type the editor can save, load and copy into Play mode.
     * Shared with tests that load scene files the way the editor does.
     */
    static void registerComponentSerializers(ComponentSerializerRegistry registry) {
        // The game runtime loads scenes with the same list (TD-035)
        GameComponentSerializers.registerAll(registry);
    }

    /**
     * Files changed on disk: textures and models that changed or went away are dropped from their caches so the next draw reads the
     * file again, and the Content Browser lists what is there now.
     */
    private void onAssetsChangedOnDisk(AssetChangeWatcher.Changes changes) {
        int reloaded = 0;
        List<AssetReference> stale = new ArrayList<>(changes.modified());
        stale.addAll(changes.removed());
        for (AssetReference asset : stale) {
            AssetType type = AssetType.fromPath(asset.getPath());
            if (type == AssetType.TEXTURE) {
                assetService.unload(asset);
                reloaded++;
            } else if (type == AssetType.MODEL) {
                modelAssets.reload(asset.getPath());
                reloaded++;
            }
        }
        if (assetBrowserPanel != null) {
            assetBrowserPanel.onAssetsChanged();
        }
        editorState.setStatusMessage("Assets changed on disk: " + changes.added().size() + " added, " + changes.removed().size()
            + " removed, " + changes.modified().size() + " changed" + (reloaded > 0 ? "; " + reloaded + " reloaded" : ""), false);
    }

    /** A sculpt was exported as a model: list it in the Content Browser, and show the new shape if it was already loaded. */
    private void onSculptExported(AssetReference model) {
        modelAssets.reload(model.getPath());
        assetCatalog.scan();
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

        // 1. Top bar like the web editor's: brand, the Viewport / Scene / Animations / Visual Scripting / Music tabs, the drop-down menus
        //    generated from the action registry, and the open scene's name with an unsaved marker. It is filled in
        //    below, once the tabs exist.
        Table menuRow = new Table(skin);
        menuRow.setBackground("header-bg");
        Label brand = new Label(" Forgeroom ", skin);
        brand.setColor(skin.getColor("accent"));
        menuRow.add(brand).padLeft(6).padRight(10);

        // 3. Outliner | Scene view | Details (+ optional AI Assistant), Content Browser below
        HierarchyPanel hierarchyPanel = new HierarchyPanel(editorState, skin, actionRegistry, OUTLINER_CONTEXT_ACTIONS);

        Map<String, String> toolIcons = Map.ofEntries(
            Map.entry(DefaultEditorActions.TOOL_SELECT, "tool-select"),
            Map.entry(DefaultEditorActions.TOOL_MOVE, "tool-move"),
            Map.entry(DefaultEditorActions.TOOL_ROTATE, "tool-rotate"),
            Map.entry(DefaultEditorActions.TOOL_SCALE, "tool-scale"),
            Map.entry(DefaultEditorActions.VIEW_TOGGLE_2D_3D, "tool-view_mode"),
            Map.entry(ACTION_FOCUS_SELECTION, "tool-focus"),
            Map.entry(ACTION_TOGGLE_GRID, "tool-grid"),
            Map.entry(ACTION_TOGGLE_PHYSICS_DEBUG, "tool-physics"),
            Map.entry(ACTION_RESET_VIEW, "tool-reset_view"),
            Map.entry(SculptActions.TOGGLE_MODE, "tool-sculpt"));
        // The transform tools, Sculpt and Frame are labelled buttons in the top bar; the view toggles stay as icons here
        ViewportOverlayToolbar overlay = new ViewportOverlayToolbar(editorState, actionRegistry, skin, false, toolIcons,
            DefaultEditorActions.VIEW_TOGGLE_2D_3D, ACTION_TOGGLE_GRID, ACTION_TOGGLE_PHYSICS_DEBUG, ACTION_RESET_VIEW);
        overlay.setInfoText(() -> editorState.getViewportMode() == ViewportMode.VIEW_3D
            ? (sculptSession.isSculptMode()
                ? "Sculpt  " + SculptActions.brushSummary(sculptSession)
                : "Fly speed " + Math.round(viewportInteraction3D.getFlySpeed()))
            : "");
        SculptToolbar sculptBar = new SculptToolbar(actionRegistry, sculptSession, skin);
        Table overlayColumn = new Table();
        // The tool strip lives in the toolbar now (as in the web editor); only the sculpt brush panel floats over the view
        overlayColumn.add(sculptBar).left();
        // Only the strips take clicks; the rest of the column lets the mouse through to the viewport
        overlayColumn.setTouchable(Touchable.childrenOnly);
        Container<Table> overlayContainer = new Container<>(overlayColumn);
        overlayContainer.top().left().pad(6f);
        // The container covers the whole scene view; only the toolbar itself should take clicks
        overlayContainer.setTouchable(Touchable.childrenOnly);
        Stack sceneView = new Stack(sceneViewport, multiViewport, overlayContainer);

        // Graph tab beside the viewport; a rebuilt UI reopens the graph that was open (saved first)
        AssetReference openGraph = null;
        if (graphEditorPanel != null) {
            graphEditorPanel.saveIfDirty();
            openGraph = graphEditorPanel.getDocument() != null ? graphEditorPanel.getDocument().getAsset() : null;
            graphEditorPanel.dispose();
        }
        this.graphEditorPanel = new GraphEditorPanel(skin, editorState, graphAssets, nodeRegistry, graphTrace, editorDialogs, project.getRoot(), assetCatalog::scan,
            () -> projectNodes.refresh().summary());
        if (openGraph != null) {
            graphEditorPanel.open(openGraph);
        }
        if (musicPanel != null) {
            musicPanel.dispose();
        }
        this.musicPanel = new MusicStudioPanel(skin, musicSession);
        if (tilemapPanel == null) {
            tilemapPanel = new TilemapStudioPanel(skin, editorState);
        }
        if (spritePanel == null) {
            // Created once: it holds the sprite being edited, which must survive a rebuild of the layout
            spritePanel = new SpriteStudioPanel(skin, project.getRoot(), assetCatalog::scan);
            if (System.getenv("GDXUNITY_EDITOR_SPRITE_DEMO") != null) {
                spritePanel.drawDemo();
            }
        }
        Label sceneHint = new Label("Press Play to run the scene here", skin, "muted");
        sceneHint.setTouchable(Touchable.disabled);
        Container<Label> sceneHintContainer = new Container<>(sceneHint);
        sceneHintContainer.setTouchable(Touchable.disabled);
        this.sceneHintContainer = sceneHintContainer;
        Stack gameView = new Stack(gameViewport, sceneHintContainer);
        if (animationsPanel != null) {
            animationsPanel.dispose();
        }
        this.timelinePanel = new TimelinePanel(skin, editorState, timelineSession);
        CharacterCreatorPanel characterCreator = new CharacterCreatorPanel(skin, editorState, project.getRoot(), model -> modelAssets.reload(model.getPath()));
        this.animationsPanel = new AnimationsPanel(skin, project.getRoot(), timelinePanel, this::previewLibraryClip, this::useLibraryClip,
            characterCreator);
        // Testing aid (TD-054): GDXUNITY_EDITOR_CREATOR=1 opens the character creator in the Animations tab
        String creatorAid = System.getenv(SHOW_CREATOR_VARIABLE);
        if (creatorAid != null && !creatorAid.isBlank()) {
            animationsPanel.showCreator(true);
            if (!"1".equals(creatorAid)) {
                // GDXUNITY_EDITOR_CREATOR=<character>[:<preview animation>] also opens that saved character
                String[] parts = creatorAid.split(":", 2);
                characterCreator.open(parts[0], parts.length > 1 ? parts[1] : null);
            }
        }
        this.centerTabs = new TabbedPanel(skin, List.of("Viewport", "Scene", "Animations", "Visual Scripting", "Music", "Sprites", "Tiles"),
            List.of(sceneView, gameView, animationsPanel, graphEditorPanel, musicPanel, spritePanel, tilemapPanel),
            selectedCenterTab, index -> selectedCenterTab = index, false);

        Label documentLabel = new Label("", skin, "muted") {
            @Override
            public void act(float delta) {
                super.act(delta);
                String text = sceneFiles.getDocumentName() + (sceneFiles.isDirty() ? " ●" : "");
                if (!text.contentEquals(getText())) {
                    setText(text);
                }
            }
        };
        menuRow.add(centerTabs.getTabRow()).padRight(10);
        // The web editor's button row, in its order: file, history, add / duplicate / delete, then the tools
        menuRow.add(new Label("|", skin, "muted")).padRight(8);
        addBarButtons(menuRow, new String[][] {
            {ACTION_NEW_SCENE_SCREEN, "New"}, {DefaultEditorActions.FILE_OPEN_SCENE, "Open..."},
            {DefaultEditorActions.FILE_SAVE_SCENE, "Save"}});
        menuRow.add(new Label("|", skin, "muted")).padLeft(4).padRight(8);
        addBarButtons(menuRow, new String[][] {
            {DefaultEditorActions.EDIT_UNDO, "Undo"}, {DefaultEditorActions.EDIT_REDO, "Redo"}});
        menuRow.add(new Label("|", skin, "muted")).padLeft(4).padRight(8);
        menuRow.add(new EditorMenuBar(actionRegistry, List.of(DefaultEditorActions.MENU_CREATE), skin)).padRight(4);
        addBarButtons(menuRow, new String[][] {
            {DefaultEditorActions.EDIT_DUPLICATE, "Duplicate"}, {DefaultEditorActions.EDIT_DELETE, "Delete"}});
        menuRow.add(new Label("|", skin, "muted")).padLeft(4).padRight(8);
        addToolButton(menuRow, DefaultEditorActions.TOOL_SELECT, "Select", ToolMode.SELECT);
        addToolButton(menuRow, DefaultEditorActions.TOOL_MOVE, "Move", ToolMode.MOVE);
        addToolButton(menuRow, DefaultEditorActions.TOOL_ROTATE, "Rotate", ToolMode.ROTATE);
        addToolButton(menuRow, DefaultEditorActions.TOOL_SCALE, "Scale", ToolMode.SCALE);
        menuRow.add(new ActionTextButton(skin, actionRegistry, SculptActions.TOGGLE_MODE, "Sculpt", sculptSession::isSculptMode))
            .height(BAR_BUTTON_HEIGHT).padRight(BAR_BUTTON_GAP);
        menuRow.add(new ActionTextButton(skin, actionRegistry, ACTION_FOCUS_SELECTION, "Frame", null))
            .height(BAR_BUTTON_HEIGHT).padRight(BAR_BUTTON_GAP);
        menuRow.add().expandX();
        menuRow.add(documentLabel).padRight(10);
        root.add(menuRow).growX().height(MENU_BAR_HEIGHT).row();

        // 2. Second row, like the web editor's wrapped toolbar: the view toggles, then compile and the play controls.
        //    (The File / Edit / Tools / Window / Help menu bar was removed at the user's request.)
        root.add(new EditorToolbar(editorState, playModeController, editorLog, skin, this::toggleAssistantPanel, overlay,
            new GameModeSelector(editorState, skin)))
            .growX().height(TOOLBAR_HEIGHT).row();

        com.badlogic.gdx.scenes.scene2d.ui.SplitPane leftSplit =
            new com.badlogic.gdx.scenes.scene2d.ui.SplitPane(hierarchyPanel, centerTabs, false, skin);
        leftSplit.setSplitAmount(layoutConfig.getSplitRatioLeft());
        leftSplit.setMinSplitAmount(0.12f);
        leftSplit.setMaxSplitAmount(0.40f);

        ComponentEditorRegistry<Table> detailEditors = InspectorPanel.createDefaultRegistry();
        detailEditors.register(new GraphScriptComponentEditorProvider(this::openGraph));
        detailEditors.register(new ModelComponentEditorProvider(modelAssets));
        detailEditors.register(new AnimatorComponentEditorProvider(this::openClip));
        detailEditors.register(new io.github.ldev22live.gdxunity.editor.desktop.ui.inspector.SpriteAnimatorComponentEditorProvider());
        detailEditors.register(new io.github.ldev22live.gdxunity.editor.desktop.ui.inspector.TilemapComponentEditorProvider());
        detailEditors.register(new io.github.ldev22live.gdxunity.editor.desktop.ui.inspector.DynamicMeshComponentEditorProvider(sculptSession, this::onSculptExported));
        ComponentCatalog componentCatalog = ComponentCatalog.createDefault();
        componentCatalog.register(new ComponentCatalog.Entry("Graph Script", "Scripting", GraphScriptComponent.class, true,
            this::createGraphScript));
        componentCatalog.register(new ComponentCatalog.Entry("Animator", "Animation", AnimatorComponent.class, false,
            this::createAnimator));
        InspectorPanel inspectorPanel = new InspectorPanel(editorState, skin, detailEditors, componentCatalog);
        this.assistantPanel = new io.github.ldev22live.gdxunity.editor.desktop.ui.AssistantPanel(assistantService, skin);
        this.rightSplit = new com.badlogic.gdx.scenes.scene2d.ui.SplitPane(inspectorPanel,
            new com.badlogic.gdx.scenes.scene2d.ui.Container<>(assistantPanel).minHeight(0f).fill(), true, skin);
        this.rightSplit.setSplitAmount(detailsSplitAmount());
        this.rightSplit.setMinSplitAmount(ASSISTANT_SPLIT_MIN);
        this.rightSplit.setMaxSplitAmount(1.0f);

        com.badlogic.gdx.scenes.scene2d.ui.SplitPane centerRightSplit =
            new com.badlogic.gdx.scenes.scene2d.ui.SplitPane(leftSplit,
                "0".equals(System.getenv(ASSISTANT_VARIABLE)) ? inspectorPanel : rightSplit, false, skin);
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
        // In the multi-pane view the drop lands at the orbit target, since no single pane is under the mouse
        assetDragAndDrop.addTarget(new DragAndDrop.Target(multiViewport) {
            @Override
            public boolean drag(DragAndDrop.Source source, DragAndDrop.Payload payload, float x, float y, int pointer) {
                return payload.getObject() instanceof AssetReference;
            }

            @Override
            public void drop(DragAndDrop.Source source, DragAndDrop.Payload payload, float x, float y, int pointer) {
                AssetPlacement.place(editorState, (AssetReference) payload.getObject(), spawnPosition());
            }
        });
        io.github.ldev22live.gdxunity.editor.desktop.ui.AssetBrowserPanel assetBrowser =
            new io.github.ldev22live.gdxunity.editor.desktop.ui.AssetBrowserPanel(editorState, assetCatalog, skin, assetDragAndDrop);
        this.assetBrowserPanel = assetBrowser;
        assetBrowser.setModelThumbnails(modelThumbnails);
        assetBrowser.setSpriteThumbnails(spriteThumbnails);
        // Testing aid: GDXUNITY_EDITOR_FOLDER=models starts the Content Browser in that folder
        String startFolder = System.getenv(START_FOLDER_VARIABLE);
        if (startFolder != null && !startFolder.isBlank()) {
            assetBrowser.showFolder(startFolder);
        }
        assetBrowser.setOnOpen(asset -> {
            if (AssetType.fromPath(asset.getPath()) == AssetType.GRAPH) {
                openGraph(asset);
            } else if (AssetType.fromPath(asset.getPath()) == AssetType.ANIMATION) {
                openClip(asset, animatorEntityFor(asset));
            } else if (AssetType.fromPath(asset.getPath()) == AssetType.SPRITE) {
                openSprite(asset);
            }
        });

        com.badlogic.gdx.scenes.scene2d.ui.SplitPane mainSplit =
            new com.badlogic.gdx.scenes.scene2d.ui.SplitPane(centerRightSplit, createBottomTabs(assetBrowser), true, skin);
        mainSplit.setSplitAmount(layoutConfig.getSplitRatioMain());
        mainSplit.setMinSplitAmount(0.55f);
        mainSplit.setMaxSplitAmount(0.92f);

        root.add(mainSplit).grow().row();

        // 4. Status bar
        root.add(new EditorStatusBar(editorState, skin)).growX().height(STATUS_BAR_HEIGHT);
    }

    /** Adds labelled top-bar buttons for the {@code {actionId, label}} pairs. */
    private void addBarButtons(Table bar, String[][] buttons) {
        for (String[] button : buttons) {
            bar.add(new ActionTextButton(skin, actionRegistry, button[0], button[1], null))
                .height(BAR_BUTTON_HEIGHT).padRight(BAR_BUTTON_GAP);
        }
    }

    /** A top-bar button for a transform tool, drawn selected while that tool is the active one. */
    private void addToolButton(Table bar, String actionId, String label, ToolMode mode) {
        bar.add(new ActionTextButton(skin, actionRegistry, actionId, label, () -> editorState.getToolMode() == mode))
            .height(BAR_BUTTON_HEIGHT).padRight(BAR_BUTTON_GAP);
    }

    /** Content Browser and Output Log; the Timeline moved to the Animations tab (TD-044). */
    private TabbedPanel createBottomTabs(Actor assetBrowser) {
        this.bottomTabs = new TabbedPanel(skin,
            List.of("Content Browser", "Output Log"),
            List.of(assetBrowser, new OutputLogPanel(editorLog, skin)),
            Math.min(selectedBottomTab, 1), index -> selectedBottomTab = index);
        return bottomTabs;
    }

    /** A library clip's name button: previews it on the humanoid in the scene (the selection if it is one). */
    private void previewLibraryClip(AssetReference asset) {
        Entity humanoid = ClipLibrary.findHumanoid(editorState.getActiveScene(), editorState.getSelectedEntity());
        Entity target = humanoid != null ? humanoid : animatorEntityFor(asset);
        if (target == null) {
            timelineSession.open(asset, null);
            editorState.setStatusMessage("No humanoid in this scene to preview " + asset.getFileName()
                + " on. Open samples/humanoid-animations-3d.json, or give an entity a Hips child", true);
            return;
        }
        openClip(asset, target);
    }

    /** A library clip's Use button: the selected entity plays the clip (an Animator is added when it has none). */
    private void useLibraryClip(AssetReference asset) {
        Entity selected = editorState.getSelectedEntity();
        if (selected == null) {
            editorState.setStatusMessage("Select an entity in the Outliner to play " + asset.getFileName(), true);
            return;
        }
        if (ClipLibrary.assign(editorState, selected, asset)) {
            editorState.setStatusMessage(selected.getName() + " now plays " + asset.getFileName(), false);
            openClip(asset, selected);
        }
    }

    /** Opens a clip in the Timeline to animate {@code entity} (the entity with the Animator) and shows the tab. */
    private void openClip(AssetReference asset, Entity entity) {
        if (timelineSession.open(asset, entity != null ? entity.getId() : null) && centerTabs != null) {
            showCenterTab(CENTER_TAB_ANIMATIONS);
            if (entity == null) {
                editorState.setStatusMessage("No entity's Animator uses " + asset.getFileName()
                    + ": keys can be edited, but there is nothing to preview", true);
            }
        }
    }

    /** The selected entity if its Animator plays {@code asset}, otherwise the first entity whose Animator does. */
    private Entity animatorEntityFor(AssetReference asset) {
        Entity selected = editorState.getSelectedEntity();
        if (selected != null && selected.getComponent(AnimatorComponent.class) != null
            && asset.getPath().equals(new AssetReference(selected.getComponent(AnimatorComponent.class).getClipPath()).getPath())) {
            return selected;
        }
        for (Entity entity : editorState.getActiveScene().getAllEntities()) {
            AnimatorComponent animator = entity.getComponent(AnimatorComponent.class);
            if (animator != null && asset.getPath().equals(new AssetReference(animator.getClipPath()).getPath())) {
                return entity;
            }
        }
        return null;
    }

    /**
     * Factory for Add Component → Animator: uses the clip selected in the Content Browser, otherwise creates a clip
     * named after the entity. The clip opens in the Timeline once the component is added.
     */
    private AnimatorComponent createAnimator(EditorState state) {
        Entity entity = state.getSelectedEntity();
        AssetReference selected = state.getSelectedAsset();
        AssetReference clip;
        if (selected != null && AssetType.fromPath(selected.getPath()) == AssetType.ANIMATION) {
            clip = selected;
        } else {
            try {
                clip = timelineSession.getAssets().create(entity != null ? entity.getName() : "Clip");
                assetCatalog.scan();
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot create animation clip: " + e.getMessage(), e);
            }
        }
        // Open after the add command has run, so the Timeline previews the entity that now has the Animator
        AssetReference toOpen = clip;
        Gdx.app.postRunnable(() -> openClip(toOpen, entity));
        return new AnimatorComponent(clip.getPath());
    }

    /** Opens a sprite asset in the Sprites tab and switches to it. */
    private void openSprite(AssetReference asset) {
        if (spritePanel != null && spritePanel.open(new File(project.getRoot(), asset.getPath())) && centerTabs != null) {
            centerTabs.select(CENTER_TAB_SPRITES);
        }
    }

    /** Opens a graph asset in the Visual Scripting tab and switches to it. */
    private void openGraph(AssetReference asset) {
        if (graphEditorPanel != null && graphEditorPanel.open(asset) && centerTabs != null) {
            centerTabs.select(CENTER_TAB_GRAPH);
        }
    }

    /**
     * Factory for Add Component → Graph Script: uses the graph selected in the Content Browser, otherwise creates
     * a new graph named after the entity and opens it.
     */
    private GraphScriptComponent createGraphScript(EditorState state) {
        AssetReference selected = state.getSelectedAsset();
        if (selected != null && AssetType.fromPath(selected.getPath()) == AssetType.GRAPH) {
            return new GraphScriptComponent(selected.getPath());
        }
        String name = state.getSelectedEntity() != null ? state.getSelectedEntity().getName() : "Graph";
        try {
            AssetReference created = graphAssets.create(name);
            assetCatalog.scan();
            openGraph(created);
            return new GraphScriptComponent(created.getPath());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create graph: " + e.getMessage(), e);
        }
    }

    /** Id of the File menu action that exports the project as a standalone game (Phase 18-C, TD-046). */
    private static final String ACTION_EXPORT_GAME = "file.export-game";
    /** Menu group of Export Game in the File menu: with Save, before Exit. */
    private static final int EXPORT_GAME_MENU_GROUP = 1;

    /**
     * File &gt; Export Game: asks for a new folder, compiles the scripts and writes the game, starting with the scene that
     * is open (it must be saved). Works from the packaged editor only; the status line says why when it cannot.
     */
    private void exportGame() {
        File scene = sceneFiles.getCurrentFile();
        if (scene == null || sceneFiles.isDirty()) {
            editorState.setStatusMessage("Save the scene first: the game starts with the saved scene", true);
            return;
        }
        File template = GameExporter.locateTemplate();
        if (template == null) {
            editorState.setStatusMessage("Export Game works from the packaged editor (ForgeroomEditor.exe); this is a development run", true);
            return;
        }
        String startScene = project.getRoot().toPath().relativize(scene.toPath().toAbsolutePath()).toString();
        File suggested = new File(project.getRoot().getParentFile(), GameExporter.safeName(project.getRoot().getName()) + " Game");
        Optional<File> chosen = editorDialogs.chooseSaveFile("Export Game: name the new game folder", suggested, "Game folder", "*");
        if (chosen.isEmpty()) {
            return;
        }
        try {
            GameExporter.Result result = GameExportCommand.run(project, startScene, template, chosen.get());
            editorState.setStatusMessage("Exported the game to " + result.folder() + ": start " + result.executable().getName(), false);
        } catch (IOException | RuntimeException e) {
            editorState.setStatusMessage("Cannot export the game: " + e.getMessage(), true);
        }
    }

    private void registerDesktopActions() {
        actionRegistry.register(EditorAction.builder(ACTION_EXPORT_GAME, "Export Game...", this::exportGame)
            .menu(DefaultEditorActions.MENU_FILE, EXPORT_GAME_MENU_GROUP)
            .enabledWhen(() -> !editorState.isPlayModeActive())
            .build());
        actionRegistry.register(EditorAction.builder(ACTION_FOCUS_SELECTION, "Focus Selection", this::focusSelection)
            .menu(DefaultEditorActions.MENU_TOOLS, 1)
            .shortcut(Input.Keys.F, 0)
            .enabledWhen(() -> editorState.getSelectedEntity() != null)
            .build());
        actionRegistry.register(EditorAction.builder(ACTION_NEW_SCENE_SCREEN, "New Scene...", this::showGameModeScreen)
            .menu(DefaultEditorActions.MENU_FILE, 0)
            .enabledWhen(() -> !editorState.isPlayModeActive())
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

    /** The mode screen: three cards, one per game mode, each starting a new scene in that mode (TD-067). */
    private void showGameModeScreen() {
        if (uiStage == null) {
            return;
        }
        if (gameModeScreen == null) {
            gameModeScreen = new GameModeScreen(skin);
        }
        gameModeScreen.show(uiStage, editorState.getGameMode(), sceneFiles::newScene);
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
        return saveSculptsBeforeClose() && saveMusicBeforeClose() && (sceneFiles == null || sceneFiles.confirmDiscardChanges());
    }

    /**
     * Writes sculpts with unsaved strokes before the editor closes, so hours of sculpting are not lost the way an
     * unsaved scene can be re-opened. A sculpt that cannot be written stops the close, with the reason in the status
     * bar.
     */
    private boolean saveSculptsBeforeClose() {
        try {
            int saved = sculptSession.saveAllModified();
            if (saved > 0) {
                editorState.setStatusMessage("Saved " + saved + " sculpt" + (saved == 1 ? "" : "s"), false);
            }
            return true;
        } catch (IOException e) {
            editorState.setStatusMessage("Cannot close: a sculpt could not be saved. " + e.getMessage(), true);
            return false;
        }
    }

    /** Saves the open song before the editor closes; a song that cannot be written stops the close. */
    private boolean saveMusicBeforeClose() {
        if (musicSession.saveIfDirty()) {
            return true;
        }
        editorState.setStatusMessage("Cannot close: " + musicSession.getLastError(), true);
        return false;
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
        if (multiViewport != null) {
            multiViewport.resetAll();
        }
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
            if (multiViewport != null) {
                multiViewport.frameAll(center, radius);
            }
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
    private ViewportLayout loadViewportLayout() {
        if (viewportLayoutFile.isFile()) {
            try {
                return ViewportLayout.fromJson(Files.readString(viewportLayoutFile.toPath()));
            } catch (IOException | IllegalArgumentException e) {
                editorState.setStatusMessage("The saved viewport layout could not be read, using the default: "
                    + e.getMessage(), true);
            }
        }
        return ViewportLayout.quad();
    }

    private void saveViewportLayout() {
        try {
            File parent = viewportLayoutFile.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                throw new IOException("Could not create " + parent);
            }
            Files.writeString(viewportLayoutFile.toPath(), viewportLayout.toJson());
        } catch (IOException e) {
            editorState.setStatusMessage("Could not save the viewport layout: " + e.getMessage(), true);
        }
    }

    private void reset3DView() {
        sceneCamera3D.setPosition(DEFAULT_VIEW_CENTER_X, VIEW3D_CAMERA_HEIGHT, VIEW3D_CAMERA_DISTANCE);
        sceneCamera3D.setUp(Vector3.Y);
        sceneCamera3D.lookAt(DEFAULT_VIEW_CENTER_X, DEFAULT_VIEW_CENTER_Y, 0f);
        sceneCamera3D.update();
        cameraController3D.setTarget(DEFAULT_VIEW_CENTER_X, DEFAULT_VIEW_CENTER_Y, 0f);
    }

    /**
     * The right column's split amount, the share Details keeps. The assistant sits in a container without a minimum height:
     * SplitPane keeps the second widget at its own minimum, which left Details about 150 px high while it was minimized (TD-055).
     */
    private float detailsSplitAmount() {
        return isAssistantVisible ? DETAILS_SPLIT_WITH_ASSISTANT : 1f;
    }

    public void toggleAssistantPanel() {
        isAssistantVisible = !isAssistantVisible;
        if (rightSplit != null) {
            rightSplit.setSplitAmount(detailsSplitAmount());
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
        if (playOnFirstFrame) {
            playOnFirstFrame = false;
            if (!playModeController.play()) {
                editorLog.error(LOG_SOURCE, "--play: the scene could not enter Play mode");
            }
        }
        Color background = themeManager.getCurrentPalette().getBackground();
        float delta = Gdx.graphics.getDeltaTime();

        // 1. Advance Play Mode simulation if active
        if (playModeController.isPlaying()) {
            playModeController.update(delta);
        }
        viewportInteraction.update(delta);
        timelineSession.update(delta);
        assetWatcher.update(delta);

        // 2. Render the active workspace off-screen into the scene view panel
        workspaceManager.update(delta);
        // Editing in 3D shows the panes; 2D and Play show the single view
        boolean showPanes = multiViewport != null && editorState.getViewportMode() == ViewportMode.VIEW_3D
            && !editorState.isPlayModeActive();
        if (multiViewport != null) {
            multiViewport.setVisible(showPanes);
        }
        if (sceneViewport != null) {
            sceneViewport.setVisible(!showPanes);
        }
        boolean showGame = gameViewport != null && editorState.isPlayModeActive() && selectedCenterTab == CENTER_TAB_SCENE;
        if (sceneHintContainer != null) {
            sceneHintContainer.setVisible(!editorState.isPlayModeActive());
        }
        if (showGame) {
            gameViewport.renderScene(background, () -> workspaceManager.render(delta));
        } else if (showPanes) {
            multiViewport.renderPanes(background, delta);
        } else if (sceneViewport != null) {
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
        if (launchOptions.isSmokeTest() && ++smokeTestFramesRendered == launchOptions.smokeTestFrames()) {
            finishSmokeTest();
        }
    }

    private void showCenterTab(int tab) {
        selectedCenterTab = tab;
        centerTabs.select(tab);
    }

    /** The Scene tab shows the game and takes no editing input: clicks and keys do nothing there. */
    private static final class NoInteraction implements ViewportInteraction {
        @Override
        public boolean touchDown(float localX, float localY, float panelWidth, float panelHeight, int button, int modifiers) {
            return false;
        }

        @Override
        public boolean touchDragged(float localX, float localY, float panelWidth, float panelHeight) {
            return false;
        }

        @Override
        public boolean touchUp(int button) {
            return false;
        }

        @Override
        public boolean scrolled(float amountY) {
            return false;
        }

        @Override
        public Vector3 worldPointAt(float localX, float localY, float panelWidth, float panelHeight) {
            throw new UnsupportedOperationException("The Scene tab has no editing input");
        }
    }

    /** Testing aid (TD-054): GDXUNITY_EDITOR_ASSISTANT=0 leaves the AI Assistant out of the layout, so Details gets the whole right column. */
    private static final String ASSISTANT_VARIABLE = "GDXUNITY_EDITOR_ASSISTANT";
    private static final String START_TAB_VARIABLE = "GDXUNITY_EDITOR_TAB";
    private static final String START_FOLDER_VARIABLE = "GDXUNITY_EDITOR_FOLDER";
    private static final String SELECT_VARIABLE = "GDXUNITY_EDITOR_SELECT";
    private static final String SHOW_CREATOR_VARIABLE = "GDXUNITY_EDITOR_CREATOR";
    private static final String SCREENSHOT_VARIABLE = "GDXUNITY_SCREENSHOT";

    private static int centerTabIndex(String name) {
        return switch (name.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "viewport" -> CENTER_TAB_VIEWPORT;
            case "scene" -> CENTER_TAB_SCENE;
            case "animations" -> CENTER_TAB_ANIMATIONS;
            case "graph", "visual scripting" -> CENTER_TAB_GRAPH;
            case "music" -> CENTER_TAB_MUSIC;
            case "sprites" -> CENTER_TAB_SPRITES;
            case "tiles" -> CENTER_TAB_TILES;
            default -> throw new IllegalArgumentException(START_TAB_VARIABLE + " must be Viewport, Scene, Animations, Graph or Music: " + name);
        };
    }

    /** Saves this window's last drawn frame to the file named by {@value #SCREENSHOT_VARIABLE}, if set (TD-054); reads only our own frame buffer. */
    private void saveScreenshotIfRequested() {
        String path = System.getenv(SCREENSHOT_VARIABLE);
        if (path == null || path.isBlank()) {
            return;
        }
        com.badlogic.gdx.graphics.Pixmap pixmap = com.badlogic.gdx.graphics.Pixmap.createFromFrameBuffer(0, 0,
            Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        try {
            File file = new File(path).getAbsoluteFile();
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            com.badlogic.gdx.graphics.PixmapIO.writePNG(new com.badlogic.gdx.files.FileHandle(file), pixmap,
                java.util.zip.Deflater.DEFAULT_COMPRESSION, true);
        } finally {
            pixmap.dispose();
        }
    }

    /** Records the Output Log errors and closes the editor; the launcher turns the result into an exit code. */
    private void finishSmokeTest() {
        saveScreenshotIfRequested();
        smokeTestErrors = editorLog.getEntries(EditorLog.Severity.ERROR);
        Gdx.app.log(LOG_SOURCE, "Smoke test rendered " + smokeTestFramesRendered + " frames, "
            + smokeTestErrors.size() + " error(s) in the Output Log");
        Gdx.app.exit();
    }

    /**
     * Output Log errors at the end of a smoke test; empty when there were none or no smoke test ran to its end.
     * Valid after the application loop has returned.
     */
    public List<EditorLog.Entry> getSmokeTestErrors() {
        return smokeTestErrors;
    }

    /** True once a smoke test has rendered all its frames. */
    public boolean isSmokeTestFinished() {
        return launchOptions.isSmokeTest() && smokeTestFramesRendered >= launchOptions.smokeTestFrames();
    }

    @Override
    public void dispose() {
        if (tilemapPanel != null) {
            tilemapPanel.dispose();
        }
        if (spritePanel != null) {
            // The sprite being edited is saved under its name, like the graph and the Timeline
            if (spritePanel.isDirty()) {
                spritePanel.save();
            }
            spritePanel.dispose();
        }
        if (musicPanel != null) {
            musicPanel.dispose();
        }
        musicSession.close();
        if (graphEditorPanel != null) {
            graphEditorPanel.saveIfDirty();
            graphEditorPanel.dispose();
        }
        timelineSession.saveIfDirty();
        timelineSession.close();
        if (timelinePanel != null) {
            timelinePanel.dispose();
        }
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
        try {
            modelAssets.dispose();
        } catch (RuntimeException e) {
            // Keep shutting down; the failures are listed in the exception (TD-008)
            Gdx.app.error("ForgeroomEditor", "Model disposal failed", e);
        }
        modelThumbnails.dispose();
        spriteThumbnails.dispose();
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
        if (gameViewport != null) {
            gameViewport.dispose();
        }
        if (multiViewport != null) {
            multiViewport.dispose();
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
