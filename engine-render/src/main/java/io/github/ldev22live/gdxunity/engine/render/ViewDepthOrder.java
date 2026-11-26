package io.github.ldev22live.gdxunity.engine.render;

import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.scene.Component;
import io.github.ldev22live.gdxunity.engine.scene.Entity;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Orders flat 2D renderables (sprites, tile maps, filled shapes) from the farthest to the nearest along a perspective camera's view
 * direction, for the depth-composite pass of {@link SceneRenderer2D} (roadmap MC.4).
 *
 * <p>That pass tests each flat against the depth the 3D pass left but does not write depth, so transparent sprite edges leave no
 * halo. Among themselves the flats are therefore drawn in painter's order, and without this ordering a sprite behind another one in
 * the world could be drawn over it merely because it comes later in the scene's entity list. The depth of a renderable is that of
 * its entity's world position, the point the renderers draw it at; a renderable that is not an entity's component counts as the
 * world origin. The sort is stable: flats at the same depth keep their scene order, as in 2D.
 *
 * <p>An orthographic 2D camera keeps the scene order (layering by list position) and does not use this class.
 */
public final class ViewDepthOrder {
    private final Vector3 eye = new Vector3();
    private final Vector3 forward = new Vector3();
    private final Vector3 scratch = new Vector3();
    private final Comparator<Renderable> farthestFirst = (a, b) -> Float.compare(depthOf(b), depthOf(a));

    /**
     * Sorts the renderables in place from the farthest to the nearest seen from {@code eye} looking along {@code direction}.
     *
     * @param renderables the flats to draw; sorted in place
     * @param eye the camera position (world units)
     * @param direction the camera's view direction; need not be normalised, but must not be zero
     * @throws IllegalArgumentException when the direction is zero
     */
    public void sortBackToFront(List<Renderable> renderables, Vector3 eye, Vector3 direction) {
        Objects.requireNonNull(renderables, "renderables cannot be null");
        Objects.requireNonNull(eye, "eye cannot be null");
        Objects.requireNonNull(direction, "direction cannot be null");
        if (direction.isZero()) {
            throw new IllegalArgumentException("direction cannot be zero: " + direction);
        }
        if (renderables.size() < 2) {
            return;
        }
        this.eye.set(eye);
        this.forward.set(direction).nor();
        renderables.sort(farthestFirst);
    }

    /** The distance of the renderable's entity along the view direction from the eye, as last set by {@link #sortBackToFront}. */
    float depthOf(Renderable renderable) {
        if (renderable instanceof Component component) {
            Entity entity = component.getEntity();
            if (entity != null) {
                return scratch.set(entity.getTransform().getWorldPosition()).sub(eye).dot(forward);
            }
        }
        return scratch.set(eye).scl(-1f).dot(forward);
    }
}
