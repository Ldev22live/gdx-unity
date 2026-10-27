package io.github.ldev22live.gdxunity.editor.desktop.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.box2d.Box2DDebugRenderer;
import com.badlogic.gdx.utils.Disposable;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.gizmo.ColliderGizmoController;
import io.github.ldev22live.gdxunity.editor.core.gizmo.ColliderGizmoHandle;
import io.github.ldev22live.gdxunity.editor.core.play.PlayModeController;
import io.github.ldev22live.gdxunity.engine.render.camera.Camera;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics.BoxCollider2DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics.CircleCollider2DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics.Collider2DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics.PhysicsSystem2D;
import io.github.ldev22live.gdxunity.engine.scene.physics.PhysicsUnits2D;

import java.util.Objects;

/**
 * Renders physics debug outlines and interactive collider gizmos in the scene viewport.
 *
 * During Play Mode: Uses official {@link Box2DDebugRenderer} directly on the simulated Box2D World
 * with appropriate coordinate transformation (meters to scene pixels via matrix scaling).
 *
 * During Edit Mode: Renders authored collider wireframes directly from serialized component geometries,
 * ensuring zero duplicated Box2D world instances exist outside simulation.
 */
public class PhysicsDebugRenderer implements Disposable {

    private final EditorState editorState;
    private final PlayModeController playModeController;
    private final ColliderGizmoController colliderController;
    private final Camera camera;

    private boolean isDebugVisible = true;
    private Box2DDebugRenderer box2dDebugRenderer;

    // Palette for authored collider outlines and handles
    private static final Color COLOR_SOLID_COLLIDER = new Color(0.2f, 0.9f, 0.3f, 0.9f); // Bright green
    private static final Color COLOR_SENSOR_COLLIDER = new Color(0.9f, 0.8f, 0.2f, 0.9f); // Golden yellow
    private static final Color COLOR_SELECTED_COLLIDER = new Color(0.2f, 0.7f, 1.0f, 1.0f); // Bright cyan
    private static final Color COLOR_HANDLE = new Color(1.0f, 1.0f, 1.0f, 0.95f);
    private static final Color COLOR_HANDLE_ACTIVE = new Color(1.0f, 0.9f, 0.2f, 1.0f);

    public PhysicsDebugRenderer(EditorState editorState,
                                PlayModeController playModeController,
                                ColliderGizmoController colliderController,
                                Camera camera) {
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.playModeController = Objects.requireNonNull(playModeController, "playModeController cannot be null");
        this.colliderController = Objects.requireNonNull(colliderController, "colliderController cannot be null");
        this.camera = Objects.requireNonNull(camera, "camera cannot be null");
    }

    public boolean isDebugVisible() {
        return isDebugVisible;
    }

    public void setDebugVisible(boolean debugVisible) {
        this.isDebugVisible = debugVisible;
    }

    public void toggleDebugVisible() {
        this.isDebugVisible = !this.isDebugVisible;
    }

    /**
     * Main render entrypoint called from SceneEditorWorkspace inside viewport rendering.
     *
     * @param shapeRenderer active shape renderer for drawing authored outlines and handles
     * @param scaleFactor   camera zoom factor
     */
    public void render(ShapeRenderer shapeRenderer, float scaleFactor) {
        if (!isDebugVisible || shapeRenderer == null) {
            return;
        }

        // 1. Play Mode: If Box2D world is active, draw Box2DDebugRenderer
        if (playModeController.isPlaying() || playModeController.isPaused()) {
            PhysicsSystem2D physics = playModeController.getPhysicsSystem();
            if (physics != null && physics.getWorld() != null) {
                renderBox2DWorld(physics);
            }
        } else {
            // 2. Edit Mode: Render authored collider outlines for scene entities
            renderAuthoredColliders(shapeRenderer, scaleFactor);
        }

        // 3. Render interactive resize handles for selected entity's colliders
        renderColliderGizmoHandles(shapeRenderer, scaleFactor);
    }

    private void renderBox2DWorld(PhysicsSystem2D physics) {
        if (box2dDebugRenderer == null) {
            box2dDebugRenderer = new Box2DDebugRenderer(
                true,  // drawBodies
                true,  // drawJoints
                true,  // drawAABBs
                false, // drawInactiveBodies
                true,  // drawVelocities
                true   // drawContacts
            );
        }

        // Box2D simulates in physics meters (100 px = 1 m).
        // Transform the camera projection matrix so Box2D meter coordinates align with scene pixels:
        // P_box2d = P_camera * S(PPM, PPM, 1)
        Matrix4 physicsMatrix = new Matrix4(camera.getCombinedMatrix());
        physicsMatrix.scale(PhysicsUnits2D.PIXELS_PER_METER, PhysicsUnits2D.PIXELS_PER_METER, 1.0f);

        box2dDebugRenderer.render(physics.getWorld(), physicsMatrix);
    }

    private void renderAuthoredColliders(ShapeRenderer sr, float scaleFactor) {
        Scene scene = editorState.getActiveScene();
        if (scene == null) return;

        Entity selected = editorState.getSelectedEntity();

        Matrix4 origMatrix = new Matrix4(sr.getTransformMatrix());
        sr.setTransformMatrix(new Matrix4());
        sr.setProjectionMatrix(camera.getCombinedMatrix());

        sr.begin(ShapeRenderer.ShapeType.Line);

        for (Entity entity : scene.getAllEntities()) {
            if (!entity.isActiveInHierarchy()) continue;

            boolean isEntitySelected = (selected != null && entity.getId().equals(selected.getId()));
            Vector3 worldPos = entity.getTransform().getWorldPosition();
            Vector3 worldScale = entity.getTransform().getWorldScale();
            float scaleX = Math.abs(worldScale.x);
            float scaleY = Math.abs(worldScale.y);

            // Draw Box Colliders
            for (BoxCollider2DComponent box : entity.getComponents(BoxCollider2DComponent.class)) {
                if (!box.isEnabled()) continue;

                Color outlineColor = isEntitySelected ? COLOR_SELECTED_COLLIDER :
                    (box.isSensor() ? COLOR_SENSOR_COLLIDER : COLOR_SOLID_COLLIDER);
                sr.setColor(outlineColor);

                Vector2 off = box.getOffset();
                float centerX = worldPos.x + off.x * worldScale.x;
                float centerY = worldPos.y + off.y * worldScale.y;
                float w = box.getWidth() * scaleX;
                float h = box.getHeight() * scaleY;

                sr.rect(centerX - w * 0.5f, centerY - h * 0.5f, w, h);
            }

            // Draw Circle Colliders
            for (CircleCollider2DComponent circle : entity.getComponents(CircleCollider2DComponent.class)) {
                if (!circle.isEnabled()) continue;

                Color outlineColor = isEntitySelected ? COLOR_SELECTED_COLLIDER :
                    (circle.isSensor() ? COLOR_SENSOR_COLLIDER : COLOR_SOLID_COLLIDER);
                sr.setColor(outlineColor);

                Vector2 off = circle.getOffset();
                float centerX = worldPos.x + off.x * worldScale.x;
                float centerY = worldPos.y + off.y * worldScale.y;
                float r = circle.getRadius() * Math.max(scaleX, scaleY);

                sr.circle(centerX, centerY, r, 32);
            }
        }

        sr.end();
        sr.setTransformMatrix(origMatrix);
    }

    private void renderColliderGizmoHandles(ShapeRenderer sr, float scaleFactor) {
        Entity selected = editorState.getSelectedEntity();
        if (selected == null || !selected.isActiveInHierarchy()) return;

        float effectiveScale = (scaleFactor <= 0f) ? 1.0f : scaleFactor;
        float handleR = ColliderGizmoController.HANDLE_RADIUS * effectiveScale;
        Vector3 worldPos = selected.getTransform().getWorldPosition();
        Vector3 worldScale = selected.getTransform().getWorldScale();
        float scaleX = Math.abs(worldScale.x);
        float scaleY = Math.abs(worldScale.y);

        Matrix4 origMatrix = new Matrix4(sr.getTransformMatrix());
        sr.setTransformMatrix(new Matrix4());
        sr.setProjectionMatrix(camera.getCombinedMatrix());

        sr.begin(ShapeRenderer.ShapeType.Filled);

        // Box handles
        for (BoxCollider2DComponent box : selected.getComponents(BoxCollider2DComponent.class)) {
            if (!box.isEnabled()) continue;
            Vector2 off = box.getOffset();
            float centerX = worldPos.x + off.x * worldScale.x;
            float centerY = worldPos.y + off.y * worldScale.y;
            float halfW = (box.getWidth() * scaleX) * 0.5f;
            float halfH = (box.getHeight() * scaleY) * 0.5f;

            drawHandle(sr, centerX + halfW, centerY, handleR,
                colliderController.getActiveHandle() == ColliderGizmoHandle.BOX_RIGHT && colliderController.getActiveCollider() == box);
            drawHandle(sr, centerX - halfW, centerY, handleR,
                colliderController.getActiveHandle() == ColliderGizmoHandle.BOX_LEFT && colliderController.getActiveCollider() == box);
            drawHandle(sr, centerX, centerY + halfH, handleR,
                colliderController.getActiveHandle() == ColliderGizmoHandle.BOX_TOP && colliderController.getActiveCollider() == box);
            drawHandle(sr, centerX, centerY - halfH, handleR,
                colliderController.getActiveHandle() == ColliderGizmoHandle.BOX_BOTTOM && colliderController.getActiveCollider() == box);
        }

        // Circle handles
        for (CircleCollider2DComponent circle : selected.getComponents(CircleCollider2DComponent.class)) {
            if (!circle.isEnabled()) continue;
            Vector2 off = circle.getOffset();
            float centerX = worldPos.x + off.x * worldScale.x;
            float centerY = worldPos.y + off.y * worldScale.y;
            float r = circle.getRadius() * Math.max(scaleX, scaleY);

            drawHandle(sr, centerX + r, centerY, handleR,
                colliderController.getActiveHandle() == ColliderGizmoHandle.CIRCLE_RADIUS && colliderController.getActiveCollider() == circle);
        }

        sr.end();
        sr.setTransformMatrix(origMatrix);
    }

    private void drawHandle(ShapeRenderer sr, float x, float y, float radius, boolean isActive) {
        sr.setColor(isActive ? COLOR_HANDLE_ACTIVE : COLOR_HANDLE);
        sr.circle(x, y, radius, 16);
    }

    @Override
    public void dispose() {
        if (box2dDebugRenderer != null) {
            box2dDebugRenderer.dispose();
            box2dDebugRenderer = null;
        }
    }
}
