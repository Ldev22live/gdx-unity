package io.github.ldev22live.gdxunity.engine.animation;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Transform;
import io.github.ldev22live.gdxunity.engine.scene.lighting.DirectionalLightComponent;
import io.github.ldev22live.gdxunity.engine.scene.lighting.PointLightComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The properties tracks can animate, by id. {@link #createDefault()} has the transform channels, mesh color,
 * light intensities and the clip time of an imported model; more can be {@link #register registered}.
 *
 * <p>Rotation is keyed as yaw, pitch and roll in degrees, the same three numbers the Details panel shows. The three
 * channels of one entity are written together ({@link #ROTATION_IDS}) so setting one does not disturb the others.
 */
public class AnimatedProperties {

    public static final String POSITION_X = "transform.position.x";
    public static final String POSITION_Y = "transform.position.y";
    public static final String POSITION_Z = "transform.position.z";
    public static final String ROTATION_YAW = "transform.rotation.yaw";
    public static final String ROTATION_PITCH = "transform.rotation.pitch";
    public static final String ROTATION_ROLL = "transform.rotation.roll";
    public static final String SCALE_X = "transform.scale.x";
    public static final String SCALE_Y = "transform.scale.y";
    public static final String SCALE_Z = "transform.scale.z";
    public static final String MODEL_CLIP_TIME = "model.time";

    /** The nine transform channels, in display order. */
    public static final List<String> TRANSFORM_IDS = List.of(POSITION_X, POSITION_Y, POSITION_Z,
        ROTATION_YAW, ROTATION_PITCH, ROTATION_ROLL, SCALE_X, SCALE_Y, SCALE_Z);
    public static final Set<String> ROTATION_IDS = Set.of(ROTATION_YAW, ROTATION_PITCH, ROTATION_ROLL);

    private static final String GROUP_TRANSFORM = "Transform";

    private final Map<String, AnimatedProperty> properties = new LinkedHashMap<>();

    /** Registers {@code property}; an id can be registered once. */
    public void register(AnimatedProperty property) {
        Objects.requireNonNull(property, "property cannot be null");
        if (properties.containsKey(property.id())) {
            throw new IllegalArgumentException("Animated property already registered: " + property.id());
        }
        properties.put(property.id(), property);
    }

    /** The property with {@code id}, or null if none is registered. */
    public AnimatedProperty get(String id) {
        return properties.get(id);
    }

    public List<AnimatedProperty> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(properties.values()));
    }

    /** Properties {@code entity} has, in registration order. */
    public List<AnimatedProperty> availableOn(Entity entity) {
        List<AnimatedProperty> result = new ArrayList<>();
        for (AnimatedProperty property : properties.values()) {
            if (property.isAvailableOn(entity)) {
                result.add(property);
            }
        }
        return result;
    }

    public static AnimatedProperties createDefault() {
        AnimatedProperties registry = new AnimatedProperties();
        registry.register(transform(POSITION_X, "Position X", e -> e.getTransform().getLocalPosition().x,
            (t, v) -> { Vector3 p = t.getLocalPosition(); t.setLocalPosition(v, p.y, p.z); }));
        registry.register(transform(POSITION_Y, "Position Y", e -> e.getTransform().getLocalPosition().y,
            (t, v) -> { Vector3 p = t.getLocalPosition(); t.setLocalPosition(p.x, v, p.z); }));
        registry.register(transform(POSITION_Z, "Position Z", e -> e.getTransform().getLocalPosition().z,
            (t, v) -> { Vector3 p = t.getLocalPosition(); t.setLocalPosition(p.x, p.y, v); }));
        // getLocalEulerAngles() is (yaw, pitch, roll)
        registry.register(transform(ROTATION_YAW, "Rotation Yaw", e -> e.getTransform().getLocalEulerAngles().x,
            (t, v) -> { Vector3 r = t.getLocalEulerAngles(); t.setLocalEulerAngles(v, r.y, r.z); }));
        registry.register(transform(ROTATION_PITCH, "Rotation Pitch", e -> e.getTransform().getLocalEulerAngles().y,
            (t, v) -> { Vector3 r = t.getLocalEulerAngles(); t.setLocalEulerAngles(r.x, v, r.z); }));
        registry.register(transform(ROTATION_ROLL, "Rotation Roll", e -> e.getTransform().getLocalEulerAngles().z,
            (t, v) -> { Vector3 r = t.getLocalEulerAngles(); t.setLocalEulerAngles(r.x, r.y, v); }));
        registry.register(transform(SCALE_X, "Scale X", e -> e.getTransform().getLocalScale().x,
            (t, v) -> { Vector3 s = t.getLocalScale(); t.setLocalScale(v, s.y, s.z); }));
        registry.register(transform(SCALE_Y, "Scale Y", e -> e.getTransform().getLocalScale().y,
            (t, v) -> { Vector3 s = t.getLocalScale(); t.setLocalScale(s.x, v, s.z); }));
        registry.register(transform(SCALE_Z, "Scale Z", e -> e.getTransform().getLocalScale().z,
            (t, v) -> { Vector3 s = t.getLocalScale(); t.setLocalScale(s.x, s.y, v); }));

        registry.register(meshColor("mesh.color.r", "Color R", 0));
        registry.register(meshColor("mesh.color.g", "Color G", 1));
        registry.register(meshColor("mesh.color.b", "Color B", 2));
        registry.register(meshColor("mesh.color.a", "Color A", 3));

        registry.register(new AnimatedProperty("light.point.intensity", "Point Light Intensity", "Light",
            e -> e.getComponent(PointLightComponent.class) != null,
            e -> e.getComponent(PointLightComponent.class).getIntensity(),
            (e, v) -> e.getComponent(PointLightComponent.class).setIntensity(v)));
        registry.register(new AnimatedProperty("light.directional.intensity", "Directional Light Intensity", "Light",
            e -> e.getComponent(DirectionalLightComponent.class) != null,
            e -> e.getComponent(DirectionalLightComponent.class).getIntensity(),
            (e, v) -> e.getComponent(DirectionalLightComponent.class).setIntensity(v)));

        // Drives an imported model's own clip (e.g. a skeletal walk) from the Timeline, like a sequencer animation track
        registry.register(new AnimatedProperty(MODEL_CLIP_TIME, "Model Clip Time", "Model",
            e -> e.getComponent(ModelComponent.class) != null,
            e -> e.getComponent(ModelComponent.class).getTime(),
            (e, v) -> e.getComponent(ModelComponent.class).setTime(v)));
        return registry;
    }

    private interface TransformSetter {
        void set(Transform transform, float value);
    }

    private static AnimatedProperty transform(String id, String name, AnimatedProperty.Getter getter, TransformSetter setter) {
        return new AnimatedProperty(id, name, GROUP_TRANSFORM, e -> true, getter, (e, v) -> setter.set(e.getTransform(), v));
    }

    private static AnimatedProperty meshColor(String id, String name, int channel) {
        return new AnimatedProperty(id, name, "Mesh",
            e -> e.getComponent(MeshPrimitiveComponent.class) != null,
            e -> channel(e.getComponent(MeshPrimitiveComponent.class).getColor(), channel),
            (e, v) -> {
                MeshPrimitiveComponent mesh = e.getComponent(MeshPrimitiveComponent.class);
                Color color = mesh.getColor();
                float clamped = Math.max(0f, Math.min(1f, v));
                switch (channel) {
                    case 0 -> color.r = clamped;
                    case 1 -> color.g = clamped;
                    case 2 -> color.b = clamped;
                    default -> color.a = clamped;
                }
                mesh.setColor(color);
            });
    }

    private static float channel(Color color, int channel) {
        return switch (channel) {
            case 0 -> color.r;
            case 1 -> color.g;
            case 2 -> color.b;
            default -> color.a;
        };
    }
}
