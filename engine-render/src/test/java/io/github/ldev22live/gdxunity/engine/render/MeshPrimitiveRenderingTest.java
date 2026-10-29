package io.github.ldev22live.gdxunity.engine.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.attributes.DirectionalLightsAttribute;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonWriter;
import io.github.ldev22live.gdxunity.engine.render.camera.PerspectiveCamera3D;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.MeshPrimitiveComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.lighting.DirectionalLightComponent;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializationException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MeshPrimitiveRenderingTest {

    @BeforeAll
    static void init() {
        GdxTestHelper.initNatives();
    }

    @Test
    void shouldValidateSizeAndFlattenPlaneExtent() {
        MeshPrimitiveComponent plane = new MeshPrimitiveComponent(MeshPrimitiveComponent.Shape.PLANE, 200f, 50f, 300f, Color.WHITE);
        assertEquals(new Vector3(200f, 0f, 300f), plane.getExtent());
        assertThrows(IllegalArgumentException.class, () -> plane.setSize(0f, 1f, 1f));
        assertThrows(NullPointerException.class, () -> plane.setShape(null));
    }

    @Test
    void shouldRoundTripThroughSerializerAndRejectUnknownShape() {
        MeshPrimitiveComponent original = new MeshPrimitiveComponent(MeshPrimitiveComponent.Shape.SPHERE, 10f, 20f, 30f,
            new Color(0.1f, 0.2f, 0.3f, 1f));
        MeshPrimitiveComponentSerializer serializer = new MeshPrimitiveComponentSerializer();

        Json json = new Json();
        StringWriter out = new StringWriter();
        json.setWriter(new JsonWriter(out));
        json.writeObjectStart();
        serializer.serialize(original, json);
        json.writeObjectEnd();

        MeshPrimitiveComponent restored = serializer.deserialize(new JsonReader().parse(out.toString()), json);
        assertEquals(MeshPrimitiveComponent.Shape.SPHERE, restored.getShape());
        assertEquals(new Vector3(10f, 20f, 30f), restored.getSize());
        assertEquals(0.2f, restored.getColor().g, 0.0001f);

        assertThrows(SceneSerializationException.class,
            () -> serializer.deserialize(new JsonReader().parse("{shape: CYLINDER}"), json));
    }

    @Test
    void shouldCollectEnabledPrimitivesWithSizeScaledWorldTransform() {
        Scene scene = new Scene("Primitives");
        Entity box = scene.createEntity("Box");
        box.getTransform().setLocalPosition(10f, 20f, 30f);
        box.addComponent(new MeshPrimitiveComponent(MeshPrimitiveComponent.Shape.BOX, 2f, 3f, 4f, Color.RED));

        Entity disabled = scene.createEntity("Disabled");
        MeshPrimitiveComponent off = new MeshPrimitiveComponent();
        off.setEnabled(false);
        disabled.addComponent(off);

        Entity inactive = scene.createEntity("Inactive");
        inactive.addComponent(new MeshPrimitiveComponent());
        inactive.setActive(false);

        List<SceneRenderer3D.PrimitiveDraw> draws = SceneRenderer3D.collectPrimitives(scene);
        assertEquals(1, draws.size());
        // A unit-cube corner (0.5, 0.5, 0.5) ends up at position + size / 2
        Vector3 corner = new Vector3(0.5f, 0.5f, 0.5f).mul(draws.get(0).transform());
        assertEquals(new Vector3(11f, 21.5f, 32f), corner);
        assertTrue(SceneRenderer3D.collectPrimitives(null).isEmpty());
    }

    @Test
    void shouldAddDefaultLightOnlyWhenSceneHasNoDirectionalLight() {
        Scene scene = new Scene("Lights");
        SceneRenderer3D renderer = new SceneRenderer3D(new PerspectiveCamera3D(60f, 800f, 600f, 1f, 100f), scene) {
            @Override
            protected ModelBatch createModelBatch() {
                return null;
            }
        };
        renderer.initialize();

        renderer.updateEnvironmentLights();
        assertFalse(renderer.getEnvironment().has(DirectionalLightsAttribute.Type), "No fallback light by default");

        renderer.setDefaultLightEnabled(true);
        renderer.updateEnvironmentLights();
        assertEquals(1, directionalLightCount(renderer));

        Entity sun = scene.createEntity("Sun");
        sun.addComponent(new DirectionalLightComponent());
        renderer.updateEnvironmentLights();
        assertEquals(1, directionalLightCount(renderer), "The scene's own light replaces the fallback");
        renderer.dispose();
    }

    private static int directionalLightCount(SceneRenderer3D renderer) {
        DirectionalLightsAttribute lights = renderer.getEnvironment().get(DirectionalLightsAttribute.class, DirectionalLightsAttribute.Type);
        return lights == null ? 0 : lights.lights.size;
    }
}
