package io.github.ldev22live.gdxunity.engine.scene.lighting;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.lighting.serialization.DirectionalLightComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.lighting.serialization.PointLightComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.lighting.serialization.SkyLightComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.lighting.serialization.ToneMappingComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializationException;
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

    @Test
    void shouldRoundTripCastShadowsFlag() {
        SceneSerializer serializer = createSerializer();
        Scene scene = new Scene("Shadows");
        DirectionalLightComponent sun = new DirectionalLightComponent();
        sun.setCastShadows(false);
        scene.createEntity("Sun").addComponent(sun);

        Scene restored = serializer.fromJson(serializer.toJson(scene));

        assertFalse(restored.findEntityByName("Sun").getComponent(DirectionalLightComponent.class).isCastShadows());
    }

    @Test
    void shouldCastShadowsWhenSceneWasSavedBeforeTheFlagExisted() {
        SceneSerializer serializer = createSerializer();
        Scene scene = new Scene("Legacy");
        scene.createEntity("Sun").addComponent(new DirectionalLightComponent());
        String json = serializer.toJson(scene);
        String legacyJson = json.replaceAll(",?\s*\"?castShadows\"?\s*:\s*true", "");
        assertFalse(legacyJson.contains("castShadows"), "test setup: flag removed from " + legacyJson);

        Scene restored = serializer.fromJson(legacyJson);

        assertTrue(restored.findEntityByName("Sun").getComponent(DirectionalLightComponent.class).isCastShadows());
    }

    @Test
    void shouldRoundTripSkyLightAndToneMapping() {
        SceneSerializer serializer = createSerializer();
        Scene scene = new Scene("Sky");
        Entity settings = scene.createEntity("Lighting");
        SkyLightComponent sky = new SkyLightComponent();
        sky.setSkyColor(new Color(0.1f, 0.2f, 0.3f, 1f));
        sky.setGroundColor(new Color(0.4f, 0.5f, 0.6f, 1f));
        sky.setIntensity(2.5f);
        settings.addComponent(sky);
        ToneMappingComponent toneMapping = new ToneMappingComponent();
        toneMapping.setExposure(1.75f);
        toneMapping.setOperator(ToneMappingComponent.Operator.REINHARD);
        settings.addComponent(toneMapping);

        Entity restored = serializer.fromJson(serializer.toJson(scene)).findEntityByName("Lighting");

        SkyLightComponent skyCopy = restored.getComponent(SkyLightComponent.class);
        assertEquals(0.3f, skyCopy.getSkyColor().b, 0.0001f);
        assertEquals(0.4f, skyCopy.getGroundColor().r, 0.0001f);
        assertEquals(2.5f, skyCopy.getIntensity(), 0.0001f);
        ToneMappingComponent toneCopy = restored.getComponent(ToneMappingComponent.class);
        assertEquals(1.75f, toneCopy.getExposure(), 0.0001f);
        assertEquals(ToneMappingComponent.Operator.REINHARD, toneCopy.getOperator());
    }

    @Test
    void shouldRejectUnknownToneMappingOperatorWhenLoading() {
        SceneSerializer serializer = createSerializer();
        Scene scene = new Scene("Bad");
        scene.createEntity("Lighting").addComponent(new ToneMappingComponent());
        String json = serializer.toJson(scene).replace("ACES_FILMIC", "SPARKLY");

        SceneSerializationException error = assertThrows(SceneSerializationException.class, () -> serializer.fromJson(json));
        assertTrue(error.getMessage().contains("SPARKLY") || String.valueOf(error.getCause()).contains("SPARKLY"),
            error.getMessage());
    }

    private static SceneSerializer createSerializer() {
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        registry.register(new DirectionalLightComponentSerializer());
        registry.register(new PointLightComponentSerializer());
        registry.register(new SkyLightComponentSerializer());
        registry.register(new ToneMappingComponentSerializer());
        return new SceneSerializer(registry);
    }
}
