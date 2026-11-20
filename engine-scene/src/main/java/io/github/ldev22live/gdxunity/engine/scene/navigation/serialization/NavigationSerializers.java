package io.github.ldev22live.gdxunity.engine.scene.navigation.serialization;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.scene.navigation.NavAgent3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.navigation.NavMeshBoundsComponent;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializationException;

import java.util.Objects;

/**
 * Serializers for the navigation components (roadmap M6.4, TD-092). Every setting is written; a missing field reads as the default,
 * and a value out of range is an error naming it. The baked grid and a walk in progress are never saved.
 */
public final class NavigationSerializers {
    private NavigationSerializers() {}

    /** Registers both serializers. */
    public static void registerAll(ComponentSerializerRegistry registry) {
        Objects.requireNonNull(registry, "registry cannot be null");
        registry.register(new Bounds());
        registry.register(new Agent());
    }

    /** {@link NavMeshBoundsComponent}, type id {@value #TYPE_ID}. */
    public static final class Bounds implements ComponentSerializer<NavMeshBoundsComponent> {
        public static final String TYPE_ID = "NavMeshBoundsComponent";

        @Override
        public String getTypeId() {
            return TYPE_ID;
        }

        @Override
        public Class<NavMeshBoundsComponent> getComponentClass() {
            return NavMeshBoundsComponent.class;
        }

        @Override
        public void serialize(NavMeshBoundsComponent component, Json json) {
            Vector3 size = component.getSize();
            json.writeValue("sizeX", size.x);
            json.writeValue("sizeY", size.y);
            json.writeValue("sizeZ", size.z);
            json.writeValue("cellSize", component.getCellSize());
            json.writeValue("agentRadius", component.getAgentRadius());
            json.writeValue("agentHeight", component.getAgentHeight());
            json.writeValue("maxSlope", component.getMaxSlope());
            json.writeValue("stepHeight", component.getStepHeight());
        }

        @Override
        public NavMeshBoundsComponent deserialize(JsonValue data, Json json) {
            NavMeshBoundsComponent c = new NavMeshBoundsComponent();
            Vector3 size = c.getSize();
            try {
                c.setSize(new Vector3(data.getFloat("sizeX", size.x), data.getFloat("sizeY", size.y), data.getFloat("sizeZ", size.z)));
                c.setCellSize(data.getFloat("cellSize", c.getCellSize()));
                c.setAgentRadius(data.getFloat("agentRadius", c.getAgentRadius()));
                c.setAgentHeight(data.getFloat("agentHeight", c.getAgentHeight()));
                c.setMaxSlope(data.getFloat("maxSlope", c.getMaxSlope()));
                c.setStepHeight(data.getFloat("stepHeight", c.getStepHeight()));
            } catch (IllegalArgumentException e) {
                throw new SceneSerializationException("Invalid NavMeshBoundsComponent: " + e.getMessage(), e);
            }
            return c;
        }
    }

    /** {@link NavAgent3DComponent}, type id {@value #TYPE_ID}. */
    public static final class Agent implements ComponentSerializer<NavAgent3DComponent> {
        public static final String TYPE_ID = "NavAgent3DComponent";

        @Override
        public String getTypeId() {
            return TYPE_ID;
        }

        @Override
        public Class<NavAgent3DComponent> getComponentClass() {
            return NavAgent3DComponent.class;
        }

        @Override
        public void serialize(NavAgent3DComponent component, Json json) {
            json.writeValue("speed", component.getSpeed());
            json.writeValue("stoppingDistance", component.getStoppingDistance());
            json.writeValue("faceMovement", component.isFaceMovement());
        }

        @Override
        public NavAgent3DComponent deserialize(JsonValue data, Json json) {
            NavAgent3DComponent c = new NavAgent3DComponent();
            try {
                c.setSpeed(data.getFloat("speed", c.getSpeed()));
                c.setStoppingDistance(data.getFloat("stoppingDistance", c.getStoppingDistance()));
                c.setFaceMovement(data.getBoolean("faceMovement", c.isFaceMovement()));
            } catch (IllegalArgumentException e) {
                throw new SceneSerializationException("Invalid NavAgent3DComponent: " + e.getMessage(), e);
            }
            return c;
        }
    }
}
