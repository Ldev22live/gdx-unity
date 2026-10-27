package io.github.ldev22live.gdxunity.editor.core.picking;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.render.camera.Camera;
import io.github.ldev22live.gdxunity.engine.render.components.ShapeRendererComponent;
import io.github.ldev22live.gdxunity.engine.render.components.SpriteRendererComponent;
import io.github.ldev22live.gdxunity.engine.scene.Component;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;

import java.util.List;

/**
 * 2D point hit-testing utility for picking scene entities in the viewport.
 */
public final class ViewportPicker {

    private ViewportPicker() {}

    /**
     * Finds the top-most active entity under the specified world coordinates.
     *
     * @param scene  active scene
     * @param worldX world X coordinate
     * @param worldY world Y coordinate
     * @return clicked Entity or null if none
     */
    public static Entity pickEntityAt(Scene scene, float worldX, float worldY) {
        if (scene == null || !scene.isActive()) {
            return null;
        }

        List<Entity> entities = scene.getAllEntities();
        // Traverse in reverse so top/latest rendered entities are picked first
        for (int i = entities.size() - 1; i >= 0; i--) {
            Entity entity = entities.get(i);
            if (!entity.isActiveInHierarchy()) {
                continue;
            }

            Vector3 worldPos = entity.getTransform().getWorldPosition();
            Vector3 worldScale = entity.getTransform().getWorldScale();

            for (Component c : entity.getAllComponents()) {
                if (!c.isEnabled()) continue;

                if (c instanceof ShapeRendererComponent shape) {
                    float w = shape.getWidth() * worldScale.x;
                    float h = shape.getHeight() * worldScale.y;
                    float minX = shape.isCentered() ? (worldPos.x - w / 2f) : worldPos.x;
                    float minY = shape.isCentered() ? (worldPos.y - h / 2f) : worldPos.y;
                    float maxX = minX + w;
                    float maxY = minY + h;

                    if (worldX >= minX && worldX <= maxX && worldY >= minY && worldY <= maxY) {
                        return entity;
                    }
                } else if (c instanceof SpriteRendererComponent sprite) {
                    float w = sprite.getWidth() * worldScale.x;
                    float h = sprite.getHeight() * worldScale.y;
                    float minX = sprite.isCentered() ? (worldPos.x - w / 2f) : worldPos.x;
                    float minY = sprite.isCentered() ? (worldPos.y - h / 2f) : worldPos.y;
                    float maxX = minX + w;
                    float maxY = minY + h;

                    if (worldX >= minX && worldX <= maxX && worldY >= minY && worldY <= maxY) {
                        return entity;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Unprojects screen coordinates (window pixel space) into camera world coordinates.
     */
    public static Vector2 screenToWorld2D(Camera camera, float screenX, float screenY) {
        if (camera == null || camera.getViewport() == null) {
            return new Vector2(screenX, screenY);
        }
        Vector3 unprojected = new Vector3(screenX, screenY, 0f);
        camera.getViewport().unproject(unprojected);
        return new Vector2(unprojected.x, unprojected.y);
    }
}
