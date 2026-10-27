package io.github.ldev22live.gdxunity.editor.desktop.workspaces;

import com.badlogic.gdx.graphics.Camera;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.workspace.StudioWorkspace;
import io.github.ldev22live.gdxunity.editor.core.workspace.WorkspaceContext;
import io.github.ldev22live.gdxunity.editor.desktop.ui.TransformGizmoRenderer;
import io.github.ldev22live.gdxunity.editor.desktop.ui.ViewportGridRenderer;
import io.github.ldev22live.gdxunity.engine.render.SceneRenderer2D;
import io.github.ldev22live.gdxunity.engine.render.camera.OrthographicCamera2D;

import java.util.Objects;

/**
 * Concrete StudioWorkspace hosting the primary 2D scene editor, camera viewport,
 * background grid, and visual transform gizmos.
 */
public class SceneEditorWorkspace implements StudioWorkspace {
    public static final String ID = "scene-editor";

    private final SceneRenderer2D sceneRenderer;
    private final OrthographicCamera2D sceneCamera;
    private final TransformGizmoRenderer gizmoRenderer;
    private final ViewportGridRenderer gridRenderer;
    private io.github.ldev22live.gdxunity.editor.desktop.ui.PhysicsDebugRenderer physicsDebugRenderer;
    private EditorState editorState;

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

    @Override
    public void initialize(WorkspaceContext context) {
        this.editorState = context.getEditorState();
    }

    @Override
    public void render(float deltaTime) {
        if (sceneRenderer != null) {
            // 1. Render viewport background grid
            if (sceneRenderer.getShapeRenderer() != null && gridRenderer != null) {
                float zoom = sceneCamera.getZoom();
                gridRenderer.render(sceneRenderer.getShapeRenderer(), zoom);
            }

            // 2. Render scene entities and components
            sceneRenderer.render(deltaTime);

            // 3. Render physics debug visualization (Box2D or authored colliders)
            if (sceneRenderer.getShapeRenderer() != null && physicsDebugRenderer != null) {
                float zoom = sceneCamera.getZoom();
                physicsDebugRenderer.render(sceneRenderer.getShapeRenderer(), zoom);
            }

            // 4. Render transform gizmos on top
            if (sceneRenderer.getShapeRenderer() != null && gizmoRenderer != null) {
                float zoom = sceneCamera.getZoom();
                gizmoRenderer.render(sceneRenderer.getShapeRenderer(), zoom);
            }
        }
    }

    @Override
    public void resize(int width, int height) {
        if (sceneRenderer != null) {
            sceneRenderer.resize(width, height);
        }
    }

    @Override
    public void dispose() {
        if (physicsDebugRenderer != null) {
            physicsDebugRenderer.dispose();
        }
    }
}
