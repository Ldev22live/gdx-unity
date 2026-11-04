package io.github.ldev22live.gdxunity.engine.render;

import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.model.Animation;
import com.badlogic.gdx.graphics.g3d.utils.BaseAnimationController;

/**
 * Puts a model instance into the pose of one of its clips at an exact time. Unlike LibGDX's
 * {@code AnimationController} it keeps no clock of its own: the time comes from the scene (a
 * {@code ModelComponent} during play, the Timeline while previewing), so the same time always gives the same pose.
 */
public final class ModelPoser extends BaseAnimationController {
    private Animation applied;

    public ModelPoser(ModelInstance target) {
        super(target);
    }

    /**
     * Poses the instance at {@code time} seconds into the clip named {@code animation}; null or an unknown name
     * returns it to its rest pose.
     *
     * @return true if a clip was applied
     */
    public boolean pose(String animation, float time) {
        Animation next = animation == null ? null : target.getAnimation(animation);
        if (applied != null && applied != next) {
            // Restores the rest pose of the nodes the previous clip moved
            removeAnimation(applied);
            target.calculateTransforms();
        }
        applied = next;
        if (next == null) {
            return false;
        }
        applyAnimation(next, Math.max(0f, Math.min(next.duration, time)));
        return true;
    }

    /** Name of the clip currently applied, or null in the rest pose. */
    public String getAppliedAnimation() {
        return applied == null ? null : applied.id;
    }
}
