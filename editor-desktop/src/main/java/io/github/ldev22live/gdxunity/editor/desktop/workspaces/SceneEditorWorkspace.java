package io.github.ldev22live.gdxunity.editor.desktop.workspaces;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.ViewportMode;
import io.github.ldev22live.gdxunity.editor.core.workspace.StudioWorkspace;
import io.github.ldev22live.gdxunity.editor.core.workspace.WorkspaceContext;
import io.github.ldev22live.gdxunity.editor.desktop.ui.SelectionOutlineRenderer;
import io.github.ldev22live.gdxunity.editor.desktop.ui.TransformGizmoRenderer;
import io.github.ldev22live.gdxunity.editor.desktop.ui.ViewportGridRenderer;
import io.github.ldev22live.gdxunity.editor.desktop.ui.viewport.ViewportGridRenderer3D;
import io.github.ldev22live.gdxunity.engine.render.SceneRenderer2D;
import io.github.ldev22live.gdxunity.engine.render.SceneRenderer3D;
import io.github.ldev22live.gdxunity.engine.render.camera.OrthographicCamera2D;
import io.github.ldev22live.gdxunity.engine.render.camera.PerspectiveCamera3D;

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

    private boolean is3DActive() {
        return camera3D != null && editorState != null && editorState.getViewportMode() == ViewportMode.VIEW_3D;
    }

    @Override
    public void initialize(WorkspaceContext context) {
        this.editorState = context.getEditorState();
    }

    @Override
    public void render(float deltaTime) {
        if (is3DActive()) {
            render3D(deltaTime);
            return;
        }
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
     * selection outline and gizmos.
     * The physics debug overlay is 2D-only and is not drawn here.
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
            if (selectionOutline3D != null) {
                selectionOutline3D.render(sceneRenderer.getShapeRenderer());
            }
            gizmoRenderer3D.render(sceneRenderer.getShapeRenderer(), 1f);
        }
    }

    @Override
    public void resize(int width, int height) {
        sceneCamera.resize(width, height);
        if (camera3D != null) {
            camera3D.resize(width, height);
        }
    }

    @Override
    public void dispose() {
        if (physicsDebugRenderer != null) {
            physicsDebugRenderer.dispose();
        }
    }
}
