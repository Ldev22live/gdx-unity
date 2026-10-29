package io.github.ldev22live.gdxunity.engine.scene.lighting;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.lighting.serialization.DirectionalLightComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.lighting.serialization.PointLightComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LightSerializationTest {

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    @Test
    void shouldRoundTripLightsThroughSceneSerialization() {
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        registry.register(new DirectionalLightComponentSerializer());
        registry.register(new PointLightComponentSerializer());
        SceneSerializer serializer = new SceneSerializer(registry);

        Scene scene = new Scene("Lit");
        DirectionalLightComponent sun = new DirectionalLightComponent();
        sun.setDirection(1f, -2f, 0f);
        sun.setColor(new Color(1f, 0.9f, 0.7f, 1f));
        sun.setIntensity(1.5f);
        scene.createEntity("Sun").addComponent(sun);

        PointLightComponent lamp = new PointLightComponent();
        lamp.setColor(new Color(0.2f, 0.4f, 1f, 1f));
        lamp.setIntensity(2f);
        lamp.setRange(600f);
        scene.createEntity("Lamp").addComponent(lamp);

        Scene restored = serializer.fromJson(serializer.toJson(scene));

        Entity restoredSun = restored.findEntityByName("Sun");
        DirectionalLightComponent sunCopy = restoredSun.getComponent(DirectionalLightComponent.class);
        assertNotNull(sunCopy);
        assertEquals(sun.getDirection().x, sunCopy.getDirection().x, 0.0001f);
        assertEquals(sun.getDirection().y, sunCopy.getDirection().y, 0.0001f);
        assertEquals(0.9f, sunCopy.getColor().g, 0.0001f);
        assertEquals(1.5f, sunCopy.getIntensity(), 0.0001f);

        PointLightComponent lampCopy = restored.findEntityByName("Lamp").getComponent(PointLightComponent.class);
        assertNotNull(lampCopy);
        assertEquals(600f, lampCopy.getRange(), 0.0001f);
        assertEquals(2f, lampCopy.getIntensity(), 0.0001f);
        assertEquals(1f, lampCopy.getColor().b, 0.0001f);
    }
}
