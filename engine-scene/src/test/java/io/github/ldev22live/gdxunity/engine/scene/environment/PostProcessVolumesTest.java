package io.github.ldev22live.gdxunity.engine.scene.environment;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.environment.serialization.PostProcessVolumeComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializationException;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Post Process Volumes: settings, presets, where a volume reaches, blending and files (roadmap M6.2, TD-091). */
class PostProcessVolumesTest {
    private static final float EPS = 1e-4f;

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    private static Scene sceneWith(PostProcessVolumeComponent... volumes) {
        Scene scene = new Scene("Post");
        for (PostProcessVolumeComponent volume : volumes) {
            scene.createEntity("Volume").addComponent(volume);
        }
        return scene;
    }

    private static PostProcessVolumeComponent saturation(float value) {
        PostProcessVolumeComponent volume = new PostProcessVolumeComponent();
        volume.setGrading(true);
        volume.setSaturation(value);
        return volume;
    }

    @Test
    void shouldChangeNothingUntilAnEffectIsSwitchedOn() {
        PostProcessVolumeComponent volume = new PostProcessVolumeComponent();
        assertTrue(volume.isGlobal());
        assertFalse(volume.hasEffects());
        assertEquals(PostProcessPreset.NONE, PostProcessPreset.matching(volume).orElseThrow());
        PostProcessSettings settings = new PostProcessVolumes().resolve(sceneWith(volume), new Vector3(), new PostProcessSettings());
        assertFalse(settings.isActive());
    }

    @Test
    void shouldGiveEveryPresetDistinctEffectsThatItAloneMatchesAndKeepThePlacement() {
        for (PostProcessPreset preset : PostProcessPreset.values()) {
            PostProcessVolumeComponent volume = new PostProcessVolumeComponent();
            volume.setGlobal(false);
            volume.setPriority(3);
            volume.setEnabled(false);
            preset.applyTo(volume);
            assertEquals(preset, PostProcessPreset.matching(volume).orElseThrow(), preset.name());
            assertFalse(volume.isGlobal(), preset.name());
            assertEquals(3, volume.getPriority(), preset.name());
            assertFalse(volume.isEnabled(), preset.name());
            assertEquals(preset != PostProcessPreset.NONE, volume.hasEffects(), preset.name());
        }
        PostProcessVolumeComponent tweaked = new PostProcessVolumeComponent();
        PostProcessPreset.NOIR.applyTo(tweaked);
        tweaked.setContrast(1.2f);
        assertTrue(PostProcessPreset.matching(tweaked).isEmpty(), "a tweaked preset is custom");
        assertEquals(PostProcessPreset.CINEMATIC, PostProcessPreset.find("cinematic").orElseThrow());
        assertTrue(PostProcessPreset.find("Sepia").isEmpty());
    }

    @Test
    void shouldRefuseValuesOutOfRange() {
        PostProcessVolumeComponent volume = new PostProcessVolumeComponent();
        assertThrows(IllegalArgumentException.class, () -> volume.setSaturation(-0.1f));
        assertThrows(IllegalArgumentException.class, () -> volume.setBloomThreshold(1.5f));
        assertThrows(IllegalArgumentException.class, () -> volume.setAoRadius(0f));
        assertThrows(IllegalArgumentException.class, () -> volume.setSize(0f, 1f, 1f));
        assertThrows(IllegalArgumentException.class, () -> volume.setWeight(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> volume.setExposure(5f));
        assertEquals(1f, volume.getSaturation(), "a refused value leaves the old one");
    }

    @Test
    void shouldApplyABoxVolumeInsideAndFadeItOverItsBlendDistance() {
        PostProcessVolumeComponent volume = saturation(0f);
        volume.setGlobal(false);
        volume.setSize(200f, 200f, 200f);
        volume.setBlendDistance(100f);
        Scene scene = sceneWith(volume);
        Entity entity = scene.findEntityByName("Volume");
        entity.getTransform().setLocalPosition(1000f, 0f, 0f);
        PostProcessVolumes volumes = new PostProcessVolumes();
        PostProcessSettings settings = new PostProcessSettings();

        assertEquals(0f, volumes.resolve(scene, new Vector3(1000f, 50f, -90f), settings).getSaturation(), EPS, "inside");
        assertEquals(0.5f, volumes.resolve(scene, new Vector3(1150f, 0f, 0f), settings).getSaturation(), EPS, "half way out");
        assertEquals(1f, volumes.resolve(scene, new Vector3(1250f, 0f, 0f), settings).getSaturation(), EPS, "past the blend");
        assertFalse(settings.isActive());

        volume.setBlendDistance(0f);
        assertEquals(1f, volumes.resolve(scene, new Vector3(1101f, 0f, 0f), settings).getSaturation(), EPS, "a hard edge");
    }

    @Test
    void shouldTurnAndScaleTheBoxWithItsEntityButNotTheBlendDistance() {
        PostProcessVolumeComponent volume = saturation(0f);
        volume.setGlobal(false);
        volume.setSize(100f, 100f, 100f);
        volume.setBlendDistance(100f);
        Scene scene = sceneWith(volume);
        Entity entity = scene.findEntityByName("Volume");
        entity.getTransform().setLocalScale(4f, 1f, 1f);
        entity.getTransform().setLocalEulerAngles(90f, 0f, 0f); // the long side now lies along z
        PostProcessVolumes volumes = new PostProcessVolumes();
        PostProcessSettings settings = new PostProcessSettings();
        assertEquals(0f, volumes.resolve(scene, new Vector3(0f, 0f, 190f), settings).getSaturation(), EPS, "inside the long side");
        assertEquals(0.5f, volumes.resolve(scene, new Vector3(0f, 0f, 250f), settings).getSaturation(), EPS, "50 world units past it");
        assertEquals(1f, volumes.resolve(scene, new Vector3(190f, 0f, 0f), settings).getSaturation(), EPS, "past the short side");

        entity.getTransform().setLocalScale(0f, 1f, 1f);
        assertEquals(1f, volumes.resolve(scene, new Vector3(), settings).getSaturation(), EPS, "scaled to nothing reaches nothing");
    }

    @Test
    void shouldBlendVolumesInPriorityOrderWithTheirWeights() {
        PostProcessVolumeComponent grey = saturation(0f);
        grey.setPriority(5);
        PostProcessVolumeComponent vivid = saturation(2f);
        vivid.setWeight(0.5f);
        vivid.setBloom(true);
        vivid.setBloomIntensity(2f);
        // Listed first but applied first only because of its lower priority: grey (5) wins over vivid (0)
        Scene scene = sceneWith(grey, vivid);
        PostProcessSettings settings = new PostProcessVolumes().resolve(scene, new Vector3(), new PostProcessSettings());
        assertEquals(0f, settings.getSaturation(), EPS);
        assertEquals(1f, settings.getBloomIntensity(), EPS, "bloom is only set by vivid, at half weight");
        assertTrue(settings.hasBloom());
        assertTrue(settings.hasGrading());
        assertFalse(settings.hasAmbientOcclusion());
        assertFalse(settings.hasDepthOfField());

        grey.setPriority(-1);
        assertEquals(1f, new PostProcessVolumes().resolve(scene, new Vector3(), settings).getSaturation(), EPS,
            "grey first, then vivid half way from 0 to 2");
    }

    @Test
    void shouldIgnoreDisabledVolumesInactiveEntitiesAndEffectsAVolumeLeavesOff() {
        PostProcessVolumeComponent off = saturation(0f);
        off.setEnabled(false);
        PostProcessVolumeComponent hidden = saturation(0f);
        PostProcessVolumeComponent occlusion = new PostProcessVolumeComponent();
        occlusion.setAmbientOcclusion(true);
        occlusion.setAoIntensity(2f);
        occlusion.setSaturation(0f); // grading is off, so this does nothing
        Scene scene = sceneWith(off, hidden, occlusion);
        scene.getAllEntities().get(1).setActive(false);
        PostProcessSettings settings = new PostProcessVolumes().resolve(scene, new Vector3(), new PostProcessSettings());
        assertEquals(1f, settings.getSaturation(), EPS);
        assertEquals(2f, settings.getAoIntensity(), EPS);
        assertTrue(settings.isActive());
    }

    @Test
    void shouldSaveAndReadEverySetting() {
        PostProcessVolumeComponent volume = new PostProcessVolumeComponent();
        PostProcessPreset.DREAMY.applyTo(volume);
        volume.setGlobal(false);
        volume.setSize(300f, 400f, 500f);
        volume.setBlendDistance(250f);
        volume.setWeight(0.75f);
        volume.setPriority(2);
        volume.setColorFilter(new Color(0.9f, 0.8f, 0.7f, 1f));
        volume.setAmbientOcclusion(true);
        volume.setAoRadius(80f);
        Scene scene = sceneWith(volume);
        SceneSerializer serializer = serializer();
        PostProcessVolumeComponent read = serializer.fromJson(serializer.toJson(scene)).findEntityByName("Volume")
            .getComponent(PostProcessVolumeComponent.class);
        assertTrue(read.sameEffects(volume, EPS));
        assertFalse(read.isGlobal());
        assertEquals(400f, read.getSizeY());
        assertEquals(250f, read.getBlendDistance());
        assertEquals(0.75f, read.getWeight());
        assertEquals(2, read.getPriority());
        assertEquals(0.7f, read.getColorFilter().b, EPS);
    }

    @Test
    void shouldRefuseAFileValueOutOfRangeNamingIt() {
        SceneSerializer serializer = serializer();
        String json = serializer.toJson(sceneWith(saturation(0.5f))).replaceFirst("\"saturation\"\\s*:\\s*0\\.5", "\"saturation\":7");
        SceneSerializationException error = assertThrows(SceneSerializationException.class, () -> serializer.fromJson(json));
        assertTrue(error.getMessage().contains("Saturation"), error.getMessage());
    }

    private static SceneSerializer serializer() {
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        registry.register(new PostProcessVolumeComponentSerializer());
        return new SceneSerializer(registry);
    }
}
