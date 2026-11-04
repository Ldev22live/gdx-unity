package io.github.ldev22live.gdxunity.editor.desktop.workspaces;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.ViewportMode;
import io.github.ldev22live.gdxunity.editor.core.workspace.StudioWorkspace;
import io.github.ldev22live.gdxunity.editor.core.workspace.WorkspaceContext;
import io.github.ldev22live.gdxunity.editor.desktop.ui.Collider3DRenderer;
import io.github.ldev22live.gdxunity.editor.desktop.ui.SelectionOutlineRenderer;
import io.github.ldev22live.gdxunity.editor.desktop.ui.TransformGizmoRenderer;
import io.github.ldev22live.gdxunity.editor.desktop.ui.ViewportGridRenderer;
import io.github.ldev22live.gdxunity.editor.desktop.ui.viewport.ViewportGridRenderer3D;
import io.github.ldev22live.gdxunity.engine.render.HudRenderer;
import io.github.ldev22live.gdxunity.engine.render.SceneRenderer2D;
import io.github.ldev22live.gdxunity.engine.render.SceneRenderer3D;
import io.github.ldev22live.gdxunity.engine.render.camera.Camera;
import io.github.ldev22live.gdxunity.engine.render.camera.GameCameraRig;
import io.github.ldev22live.gdxunity.engine.render.camera.OrthographicCamera2D;
import io.github.ldev22live.gdxunity.engine.render.camera.PerspectiveCamera3D;
import io.github.ldev22live.gdxunity.engine.render.components.CameraComponent;
import io.github.ldev22live.gdxunity.engine.scene.Scene;

import java.util.Objects;

/**
 * Concrete StudioWorkspace hosting the primary scene editor, camera viewport,
 * background grid, and visual transform gizmos. With a 3D view configured
 * ({@link #configure3DView}), {@link ViewportMode#VIEW_3D} renders the same scene through a
 * perspective camera over a ground grid.
 */
public class SceneEditorWorkspace implements StudioWorkspace {
    public static final String ID = "scene-editor";

    private final SceneRenderer2D sceneRenderer;
    private final OrthographicCamera2D sceneCamera;
    private final TransformGizmoRenderer gizmoRenderer;
    private final ViewportGridRenderer gridRenderer;
    private io.github.ldev22live.gdxunity.editor.desktop.ui.PhysicsDebugRenderer physicsDebugRenderer;
    private EditorState editorState;

    private PerspectiveCamera3D camera3D;
    private TransformGizmoRenderer gizmoRenderer3D;
    private ViewportGridRenderer3D gridRenderer3D;

    private SceneRenderer3D sceneRenderer3D;
    private SelectionOutlineRenderer selectionOutline2D;
    private SelectionOutlineRenderer selectionOutline3D;
    private Collider3DRenderer colliderRenderer3D;
    private io.github.ldev22live.gdxunity.editor.desktop.ui.SkeletonOverlayRenderer skeletonOverlay3D;

    /** Game view while playing a scene with a camera (TD-034). */
    private final GameCameraRig gameCameras = new GameCameraRig();
    /** Screen-space text, drawn over the scene in every view. */
    private final HudRenderer hudRenderer = new HudRenderer();
    private int viewWidth;
    private int viewHeight;

    public SceneEditorWorkspace(SceneRenderer2D sceneRenderer,
                                OrthographicCamera2D sceneCamera,
                                TransformGizmoRenderer gizmoRenderer) {
        this(sceneRenderer, sceneCamera, gizmoRenderer, new ViewportGridRenderer(sceneCamera), null);
    }

    public SceneEditorWorkspace(SceneRenderer2D sceneRenderer,
                                OrthographicCamera2D sceneCamera,
                                TransformGizmoRenderer gizmoRenderer,
                                ViewportGridRenderer gridRenderer) {
        this(sceneRenderer, sceneCamera, gizmoRenderer, gridRenderer, null);
    }

    public SceneEditorWorkspace(SceneRenderer2D sceneRenderer,
                                OrthographicCamera2D sceneCamera,
                                TransformGizmoRenderer gizmoRenderer,
                                ViewportGridRenderer gridRenderer,
                                io.github.ldev22live.gdxunity.editor.desktop.ui.PhysicsDebugRenderer physicsDebugRenderer) {
        this.sceneRenderer = Objects.requireNonNull(sceneRenderer, "sceneRenderer cannot be null");
        this.sceneCamera = Objects.requireNonNull(sceneCamera, "sceneCamera cannot be null");
        this.gizmoRenderer = Objects.requireNonNull(gizmoRenderer, "gizmoRenderer cannot be null");
        this.gridRenderer = Objects.requireNonNull(gridRenderer, "gridRenderer cannot be null");
        this.physicsDebugRenderer = physicsDebugRenderer;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return "Scene Editor";
    }

    public ViewportGridRenderer getGridRenderer() {
        return gridRenderer;
    }

    public io.github.ldev22live.gdxunity.editor.desktop.ui.PhysicsDebugRenderer getPhysicsDebugRenderer() {
        return physicsDebugRenderer;
    }

    public void setPhysicsDebugRenderer(io.github.ldev22live.gdxunity.editor.desktop.ui.PhysicsDebugRenderer renderer) {
        this.physicsDebugRenderer = renderer;
    }

    /** Enables the 3D view. The 3D grid follows the 2D grid's enabled flag. */
    public void configure3DView(PerspectiveCamera3D camera3D, TransformGizmoRenderer gizmoRenderer3D,
                                ViewportGridRenderer3D gridRenderer3D) {
        this.camera3D = Objects.requireNonNull(camera3D, "camera3D cannot be null");
        this.gizmoRenderer3D = Objects.requireNonNull(gizmoRenderer3D, "gizmoRenderer3D cannot be null");
        this.gridRenderer3D = Objects.requireNonNull(gridRenderer3D, "gridRenderer3D cannot be null");
    }

    /** Renders 3D primitives in both views, before the 2D content; may be null to disable. */
    public void setSceneRenderer3D(SceneRenderer3D sceneRenderer3D) {
        this.sceneRenderer3D = sceneRenderer3D;
    }

    /** Sets the selection outline renderers for the 2D and 3D views (either may be null to disable). */
    public void setSelectionOutlines(SelectionOutlineRenderer outline2D, SelectionOutlineRenderer outline3D) {
        this.selectionOutline2D = outline2D;
        this.selectionOutline3D = outline3D;
    }

    /** Sets the 3D collider wireframe renderer (null to disable). */
    /** Bones of selected imported models, drawn in the 3D view. */
    public void setSkeletonOverlay3D(io.github.ldev22live.gdxunity.editor.desktop.ui.SkeletonOverlayRenderer overlay) {
        this.skeletonOverlay3D = overlay;
    }

    public void setColliderRenderer3D(Collider3DRenderer renderer) {
        this.colliderRenderer3D = renderer;
    }

    /** What a pane draws around the scene besides the scene itself (TD-039): its grid and its overlays. */
    public interface PaneOverlays {
        /** Draws the pane's grid, before the scene. */
        void renderGrid(com.badlogic.gdx.graphics.glutils.ShapeRenderer shapeRenderer);

        /** Draws colliders, the selection outline, skeletons and the gizmo, after the scene. */
        void renderOverlays(com.badlogic.gdx.graphics.glutils.ShapeRenderer shapeRenderer);
    }

    /**
     * Draws the 3D scene through any camera for one pane of the multi-view layout: grid, scene, 2D entities, overlays.
     * The caller has set up the pane's frame buffer.
     */
    public void renderPane3D(Camera camera, PaneOverlays overlays, float deltaTime) {
        Objects.requireNonNull(camera, "camera cannot be null");
        Objects.requireNonNull(overlays, "overlays cannot be null");
        camera.update();
        sceneRenderer.setCamera(camera);
        if (sceneRenderer.getShapeRenderer() != null && gridRenderer.isEnabled()) {
            overlays.renderGrid(sceneRenderer.getShapeRenderer());
        }
        if (sceneRenderer3D != null) {
            sceneRenderer3D.setCamera(camera);
            sceneRenderer3D.render(deltaTime);
        }
        sceneRenderer.render(deltaTime);
        if (sceneRenderer.getShapeRenderer() != null) {
            overlays.renderOverlays(sceneRenderer.getShapeRenderer());
        }
    }

    private boolean is3DActive() {
        return camera3D != null && editorState != null && editorState.getViewportMode() == ViewportMode.VIEW_3D;
    }

    @Override
    public void initialize(WorkspaceContext context) {
        this.editorState = context.getEditorState();
    }

    @Override
    public void render(float deltaTime) {
        Scene scene = editorState != null ? editorState.getActiveScene() : null;
        CameraComponent gameCamera = editorState != null && editorState.isPlayModeActive()
            ? GameCameraRig.findActiveCamera(scene) : null;
        if (gameCamera != null && viewWidth > 0 && viewHeight > 0) {
            renderGameView(gameCamera, deltaTime);
        } else if (is3DActive()) {
            render3D(deltaTime);
        } else {
            render2D(deltaTime);
        }
        hudRenderer.render(scene, viewWidth, viewHeight);
    }

    /**
     * Playing a scene with a camera: the scene through the game camera, without grid, gizmos, selection or debug
     * overlays, as the game will look.
     */
    private void renderGameView(CameraComponent gameCamera, float deltaTime) {
        Camera camera = gameCameras.apply(gameCamera);
        if (sceneRenderer3D != null) {
            sceneRenderer3D.setCamera(camera);
            sceneRenderer3D.render(deltaTime);
        }
        sceneRenderer.setCamera(camera);
        sceneRenderer.render(deltaTime);
    }

    private void render2D(float deltaTime) {
        sceneRenderer.setCamera(sceneCamera);
        if (sceneRenderer != null) {
            // 1. Render viewport background grid
            if (sceneRenderer.getShapeRenderer() != null && gridRenderer != null) {
                float zoom = sceneCamera.getZoom();
                gridRenderer.render(sceneRenderer.getShapeRenderer(), zoom);
            }

            // 2. Render 3D primitives (orthographic) and then 2D entities and components
            if (sceneRenderer3D != null) {
                sceneRenderer3D.setCamera(sceneCamera);
                sceneRenderer3D.render(deltaTime);
            }
            sceneRenderer.render(deltaTime);

            // 3. Render physics debug visualization (Box2D or authored colliders)
            if (sceneRenderer.getShapeRenderer() != null && physicsDebugRenderer != null) {
                float zoom = sceneCamera.getZoom();
                physicsDebugRenderer.render(sceneRenderer.getShapeRenderer(), zoom);
            }

            // 4. Selection outline, then transform gizmos on top
            if (sceneRenderer.getShapeRenderer() != null && selectionOutline2D != null) {
                selectionOutline2D.render(sceneRenderer.getShapeRenderer());
            }
            if (sceneRenderer.getShapeRenderer() != null && gizmoRenderer != null) {
                float zoom = sceneCamera.getZoom();
                gizmoRenderer.render(sceneRenderer.getShapeRenderer(), zoom);
            }
        }
    }

    /**
     * 3D view: ground grid, then the scene's 2D renderables seen through the perspective camera, then the
     * 3D collider wireframes, the selection outline and gizmos.
     * The Box2D debug overlay is 2D-only; in 3D the physics debug toggle shows every 3D collider instead.
     */
    private void render3D(float deltaTime) {
        camera3D.update();
        sceneRenderer.setCamera(camera3D);
        if (sceneRenderer.getShapeRenderer() != null && gridRenderer.isEnabled()) {
            gridRenderer3D.render(sceneRenderer.getShapeRenderer());
        }
        if (sceneRenderer3D != null) {
            sceneRenderer3D.setCamera(camera3D);
            sceneRenderer3D.render(deltaTime);
        }
        sceneRenderer.render(deltaTime);
        if (sceneRenderer.getShapeRenderer() != null) {
            if (colliderRenderer3D != null) {
                colliderRenderer3D.render(sceneRenderer.getShapeRenderer());
            }
            if (selectionOutline3D != null) {
                selectionOutline3D.render(sceneRenderer.getShapeRenderer());
            }
            if (skeletonOverlay3D != null) {
                skeletonOverlay3D.render(sceneRenderer.getShapeRenderer());
            }
            gizmoRenderer3D.render(sceneRenderer.getShapeRenderer(), 1f);
        }
    }

    @Override
    public void resize(int width, int height) {
        if (width > 0 && height > 0) {
            viewWidth = width;
            viewHeight = height;
            gameCameras.resize(width, height);
        }
        sceneCamera.resize(width, height);
        if (camera3D != null) {
            camera3D.resize(width, height);
        }
    }

    @Override
    public void dispose() {
        hudRenderer.dispose();
        if (physicsDebugRenderer != null) {
            physicsDebugRenderer.dispose();
        }
    }
}
