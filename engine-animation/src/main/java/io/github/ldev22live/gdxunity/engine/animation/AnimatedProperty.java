package io.github.ldev22live.gdxunity.engine.animation;

import io.github.ldev22live.gdxunity.engine.scene.Entity;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * A number on an entity that a track can animate, such as "transform.position.x" or a light's intensity.
 *
 * @param id          stable identifier stored in {@code .anim} files
 * @param displayName name shown in the Timeline
 * @param group       heading the Timeline groups it under ("Transform", "Light", ...)
 * @param appliesTo   whether an entity has this property (e.g. has the component)
 * @param getter      reads the current value; only called when {@code appliesTo} is true
 * @param setter      writes a value; only called when {@code appliesTo} is true
 */
public record AnimatedProperty(String id, String displayName, String group, Predicate<Entity> appliesTo,
                               Getter getter, Setter setter) {

    @FunctionalInterface
    public interface Getter {
        float get(Entity entity);
    }

    @FunctionalInterface
    public interface Setter {
        void set(Entity entity, float value);
    }

    public AnimatedProperty {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(displayName, "displayName cannot be null");
        Objects.requireNonNull(group, "group cannot be null");
        Objects.requireNonNull(appliesTo, "appliesTo cannot be null");
        Objects.requireNonNull(getter, "getter cannot be null");
        Objects.requireNonNull(setter, "setter cannot be null");
    }

    public boolean isAvailableOn(Entity entity) {
        return entity != null && appliesTo.test(entity);
    }

    public float get(Entity entity) {
        return getter.get(entity);
    }

    public void set(Entity entity, float value) {
        setter.set(entity, value);
    }
}
