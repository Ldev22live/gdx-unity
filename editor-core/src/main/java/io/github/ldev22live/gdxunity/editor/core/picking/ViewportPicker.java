package io.github.ldev22live.gdxunity.editor.core.picking;

import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.math.collision.Ray;
import io.github.ldev22live.gdxunity.engine.render.camera.Camera;
import io.github.ldev22live.gdxunity.engine.render.components.DynamicMeshComponent;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;
import io.github.ldev22live.gdxunity.engine.render.components.ShapeRendererComponent;
import io.github.ldev22live.gdxunity.engine.render.components.SpriteRendererComponent;
import io.github.ldev22live.gdxunity.engine.scene.Component;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 2D point hit-testing utility for picking scene entities in the viewport.
 */
public final class ViewportPicker {

    /** Local-space thickness given to planes so rays can hit them (they have no Y extent). */
    public static final float PLANE_PICK_THICKNESS = 1f;
    /** Transforms with a smaller determinant (e.g. a zero scale axis) are not pickable. */
    private static final float SINGULAR_EPSILON = 1e-9f;

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
            if (entity.isActiveInHierarchy() && containsPoint(entity, worldX, worldY)) {
                return entity;
            }
        }
        return null;
    }

    /**
     * Finds the nearest active entity hit by a world-space ray (3D viewport). 3D primitives are tested as
     * oriented boxes (rotation and scale included). Flat 2D content is tested in the XY plane at the entity's
     * world Z; rotation of 2D content out of that plane is not taken into account.
     *
     * @return the hit entity closest to the ray origin, or null if none
     */
    public static Entity pickEntity3D(Scene scene, Ray ray) {
        if (scene == null || !scene.isActive() || ray == null) {
            return null;
        }

        Entity nearest = null;
        float nearestDistance = Float.POSITIVE_INFINITY;
        List<Entity> entities = scene.getAllEntities();
        for (int i = entities.size() - 1; i >= 0; i--) {
            Entity entity = entities.get(i);
            if (!entity.isActiveInHierarchy()) {
                continue;
            }
            BoundingBox localBounds = computeMeshLocalBounds(entity);
            if (localBounds != null) {
                float meshDistance = intersectOriented(ray, localBounds, entity.getTransform().getWorldMatrix());
                if (meshDistance >= 0f && meshDistance < nearestDistance) {
                    nearest = entity;
                    nearestDistance = meshDistance;
                }
                continue;
            }
            if (ray.direction.z == 0f) {
                continue;
            }
            float planeZ = entity.getTransform().getWorldPosition().z;
            float distance = (planeZ - ray.origin.z) / ray.direction.z;
            if (distance < 0f || distance >= nearestDistance) {
                continue;
            }
            float hitX = ray.origin.x + ray.direction.x * distance;
            float hitY = ray.origin.y + ray.direction.y * distance;
            if (containsPoint(entity, hitX, hitY)) {
                nearest = entity;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    /**
     * Bounds of the entity's enabled {@link MeshPrimitiveComponent}s, loaded {@link ModelComponent}s (rest pose,
     * times the import scale) and {@link DynamicMeshComponent}s in the entity's local space (before its world
     * transform), or {@code null} if it has none. Planes get {@link #PLANE_PICK_THICKNESS} so they can be hit. A
     * model or dynamic mesh counts once the renderer has loaded it.
     */
    public static BoundingBox computeMeshLocalBounds(Entity entity) {
        BoundingBox union = null;
        for (Component c : entity.getAllComponents()) {
            if (c.isEnabled() && c instanceof MeshPrimitiveComponent primitive) {
                Vector3 half = primitive.getExtent().scl(0.5f);
                half.y = Math.max(half.y, PLANE_PICK_THICKNESS / 2f);
                BoundingBox box = new BoundingBox(new Vector3(half).scl(-1f), half);
                union = (union == null) ? box : union.ext(box);
            } else if (c.isEnabled() && c instanceof ModelComponent model && model.getModelInfo().bounds() != null) {
                BoundingBox source = model.getModelInfo().bounds();
                float scale = model.getImportScale();
                BoundingBox box = new BoundingBox(new Vector3(source.min).scl(scale), new Vector3(source.max).scl(scale));
                union = (union == null) ? box : union.ext(box);
            } else if (c.isEnabled() && c instanceof DynamicMeshComponent dynamic && dynamic.getBounds() != null) {
                BoundingBox box = dynamic.getBounds();
                union = (union == null) ? box : union.ext(box);
            }
        }
        return union;
    }

    /**
     * World-space axis-aligned box enclosing the entity's primitives after rotation and scale, or {@code null}
     * if it has none. Used for framing and 2D picking; 3D picking tests the oriented box exactly.
     */
    public static BoundingBox computeMeshBounds(Entity entity) {
        BoundingBox local = computeMeshLocalBounds(entity);
        return local == null ? null : new BoundingBox(local).mul(entity.getTransform().getWorldMatrix());
    }

    /**
     * Distance along {@code ray} to the oriented box {@code localBounds} placed by {@code worldMatrix}, or -1
     * if it misses (or the matrix cannot be inverted, e.g. zero scale).
     */
    static float intersectOriented(Ray ray, BoundingBox localBounds, Matrix4 worldMatrix) {
        if (Math.abs(worldMatrix.det()) < SINGULAR_EPSILON) {
            return -1f;
        }
        Ray localRay = ray.cpy().mul(new Matrix4(worldMatrix).inv());
        Vector3 hit = new Vector3();
        if (!Intersector.intersectRayBounds(localRay, localBounds, hit)) {
            return -1f;
        }
        return hit.mul(worldMatrix).dst(ray.origin);
    }

    /**
     * World-space XY bounds of the entity's enabled shape and sprite renderers (the area that picking
     * responds to), or {@code null} if the entity has nothing pickable. Rotation is not taken into account.
     */
    public static Rectangle computeRenderBounds(Entity entity) {
        Rectangle union = null;
        for (Rectangle bounds : componentBounds(entity)) {
            union = (union == null) ? new Rectangle(bounds) : union.merge(bounds);
        }
        return union;
    }

    /** Whether the world XY point lies inside any enabled shape or sprite bounds of the entity. */
    private static boolean containsPoint(Entity entity, float worldX, float worldY) {
        for (Rectangle bounds : componentBounds(entity)) {
            if (bounds.contains(worldX, worldY)) {
                return true;
            }
        }
        return false;
    }

    private static List<Rectangle> componentBounds(Entity entity) {
        Vector3 worldPos = entity.getTransform().getWorldPosition();
        Vector3 worldScale = entity.getTransform().getWorldScale();
        List<Rectangle> result = new ArrayList<>();
        for (Component c : entity.getAllComponents()) {
            if (!c.isEnabled()) continue;

            if (c instanceof ShapeRendererComponent shape) {
                result.add(bounds(worldPos, shape.getWidth() * worldScale.x, shape.getHeight() * worldScale.y, shape.isCentered()));
            } else if (c instanceof SpriteRendererComponent sprite) {
                result.add(bounds(worldPos, sprite.getWidth() * worldScale.x, sprite.getHeight() * worldScale.y, sprite.isCentered()));
            } else if (c instanceof MeshPrimitiveComponent) {
                BoundingBox world = computeMeshBounds(entity);
                result.add(new Rectangle(world.min.x, world.min.y, world.getWidth(), world.getHeight()));
            }
        }
        return result;
    }

    private static Rectangle bounds(Vector3 worldPos, float w, float h, boolean centered) {
        float minX = centered ? (worldPos.x - w / 2f) : worldPos.x;
        float minY = centered ? (worldPos.y - h / 2f) : worldPos.y;
        return new Rectangle(minX, minY, w, h);
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

    /**
     * Generates a world-space picking ray from screen coordinates through the camera viewport.
     *
     * <p>Unlike {@link #screenToWorld2D}, there is no meaningful fallback ray without a camera
     * viewport, so a missing camera or viewport is reported instead of returning a fabricated ray.
     *
     * @throws NullPointerException     if {@code camera} is null
     * @throws IllegalArgumentException if the camera has no viewport
     */
    public static Ray getPickRay(Camera camera, float screenX, float screenY) {
        Objects.requireNonNull(camera, "camera cannot be null");
        if (camera.getViewport() == null) {
            throw new IllegalArgumentException("camera has no viewport; cannot build a picking ray");
        }
        return camera.getViewport().getPickRay(screenX, screenY);
    }
}
