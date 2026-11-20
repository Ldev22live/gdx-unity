package io.github.ldev22live.gdxunity.editor.desktop.ui.inspector;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.command.ModifyComponentPropertyCommand;
import io.github.ldev22live.gdxunity.editor.core.environment.EnvironmentPresets;
import io.github.ldev22live.gdxunity.editor.core.inspector.ComponentEditorProvider;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.environment.AtmosphereComponent;
import io.github.ldev22live.gdxunity.engine.scene.environment.AtmospherePreset;
import io.github.ldev22live.gdxunity.engine.scene.environment.PostProcessPreset;
import io.github.ldev22live.gdxunity.engine.scene.environment.PostProcessVolumeComponent;
import io.github.ldev22live.gdxunity.engine.scene.environment.WaterBodyComponent;
import io.github.ldev22live.gdxunity.engine.scene.environment.WaterPreset;

import java.util.Arrays;
import java.util.function.Consumer;

/**
 * Details editors for Sky and Weather and Water Body (roadmap M3.9, TD-090) and Post Process Volume (roadmap M6.2, TD-091). Each starts with a Preset menu showing the
 * preset the settings match, or Custom; choosing one sets every setting as one undo step. Every other field is its own undo
 * step.
 */
public final class EnvironmentComponentEditorProviders {
    /** The Preset menu's entry for settings that match no preset. */
    static final String CUSTOM = "Custom";

    private EnvironmentComponentEditorProviders() {}

    /** Preset, sky colours, fog, weather and sunlight of an {@link AtmosphereComponent}. */
    public static class Sky implements ComponentEditorProvider<AtmosphereComponent, Table> {
        @Override
        public Class<AtmosphereComponent> getComponentClass() {
            return AtmosphereComponent.class;
        }

        @Override
        public String getDisplayName() {
            return "Sky and Weather";
        }

        @Override
        public void buildEditor(Table container, Entity entity, AtmosphereComponent sky, EditorState editorState, Object skinObj) {
            Skin skin = (Skin) skinObj;
            container.left();
            String current = AtmospherePreset.matching(sky).map(AtmospherePreset::getDisplayName).orElse(CUSTOM);
            InspectorFieldRows.choiceRow(container, skin, "Preset", current, presetNames(AtmospherePreset.values()), name ->
                AtmospherePreset.find(name).ifPresent(preset ->
                    editorState.executeCommand(EnvironmentPresets.applySky(editorState, preset))));
            InspectorFieldRows.colorRow(container, skin, "Zenith", sky.getZenithColor(),
                color -> change(editorState, entity, sky, s -> s.setZenithColor(color), "Change Sky Colour"));
            InspectorFieldRows.colorRow(container, skin, "Horizon", sky.getHorizonColor(),
                color -> change(editorState, entity, sky, s -> s.setHorizonColor(color), "Change Horizon Colour"));
            InspectorFieldRows.toggleRow(container, skin, "Fog", sky.isFogEnabled(),
                on -> change(editorState, entity, sky, s -> s.setFogEnabled(on), on ? "Turn Fog On" : "Turn Fog Off"));
            InspectorFieldRows.colorRow(container, skin, "Fog colour", sky.getFogColor(),
                color -> change(editorState, entity, sky, s -> s.setFogColor(color), "Change Fog Colour"));
            InspectorFieldRows.floatRow(container, skin, "Fog Start", sky.getFogStart(),
                start -> change(editorState, entity, sky, s -> s.setFogRange(start, s.getFogEnd()), "Change Fog Start"));
            InspectorFieldRows.floatRow(container, skin, "Fog End", sky.getFogEnd(),
                end -> change(editorState, entity, sky, s -> s.setFogRange(s.getFogStart(), end), "Change Fog End"));
            InspectorFieldRows.enumRow(container, skin, "Weather", sky.getWeather(), AtmosphereComponent.Weather.values(),
                weather -> change(editorState, entity, sky, s -> s.setWeather(weather), "Change Weather"));
            InspectorFieldRows.floatRow(container, skin, "Intensity", sky.getWeatherIntensity(),
                intensity -> change(editorState, entity, sky, s -> s.setWeatherIntensity(intensity), "Change Weather Intensity"));
            InspectorFieldRows.floatRow(container, skin, "Sunlight", sky.getSunlight(),
                sunlight -> change(editorState, entity, sky, s -> s.setSunlight(sunlight), "Change Sunlight"));
        }
    }

    /** Preset, size, colour, opacity, roughness and waves of a {@link WaterBodyComponent}. */
    public static class Water implements ComponentEditorProvider<WaterBodyComponent, Table> {
        @Override
        public Class<WaterBodyComponent> getComponentClass() {
            return WaterBodyComponent.class;
        }

        @Override
        public String getDisplayName() {
            return "Water Body";
        }

        @Override
        public void buildEditor(Table container, Entity entity, WaterBodyComponent water, EditorState editorState, Object skinObj) {
            Skin skin = (Skin) skinObj;
            container.left();
            String current = WaterPreset.matching(water).map(WaterPreset::getDisplayName).orElse(CUSTOM);
            InspectorFieldRows.choiceRow(container, skin, "Preset", current, presetNames(WaterPreset.values()), name ->
                WaterPreset.find(name).ifPresent(preset ->
                    editorState.executeCommand(EnvironmentPresets.applyWater(editorState, entity, preset))));
            InspectorFieldRows.floatRow(container, skin, "Width", water.getWidth(),
                width -> changeWater(editorState, entity, water, w -> w.setSize(width, w.getLength()), "Change Water Width"));
            InspectorFieldRows.floatRow(container, skin, "Length", water.getLength(),
                length -> changeWater(editorState, entity, water, w -> w.setSize(w.getWidth(), length), "Change Water Length"));
            InspectorFieldRows.colorRow(container, skin, "Colour", water.getColor(),
                color -> changeWater(editorState, entity, water, w -> w.setColor(color), "Change Water Colour"));
            InspectorFieldRows.floatRow(container, skin, "Opacity", water.getColor().a, opacity -> {
                if (!(opacity >= 0f && opacity <= 1f)) {
                    throw new IllegalArgumentException("Opacity must be within 0 and 1: " + opacity);
                }
                changeWater(editorState, entity, water, w -> {
                    Color color = w.getColor();
                    color.a = opacity;
                    w.setColor(color);
                }, "Change Water Opacity");
            });
            InspectorFieldRows.floatRow(container, skin, "Roughness", water.getRoughness(),
                roughness -> changeWater(editorState, entity, water, w -> w.setRoughness(roughness), "Change Water Roughness"));
            InspectorFieldRows.floatRow(container, skin, "Waves", water.getWaveHeight(),
                height -> changeWater(editorState, entity, water, w -> w.setWaves(height, w.getWaveLength(), w.getWaveSpeed()), "Change Waves"));
            InspectorFieldRows.floatRow(container, skin, "Spacing", water.getWaveLength(),
                length -> changeWater(editorState, entity, water, w -> w.setWaves(w.getWaveHeight(), length, w.getWaveSpeed()), "Change Waves"));
            InspectorFieldRows.floatRow(container, skin, "Speed", water.getWaveSpeed(),
                speed -> changeWater(editorState, entity, water, w -> w.setWaves(w.getWaveHeight(), w.getWaveLength(), speed), "Change Waves"));
        }
    }

    /**
     * Preset, place and reach, and the four effects of a {@link PostProcessVolumeComponent} (roadmap M6.2). An effect's settings
     * show only while its switch is on, and the box only while the volume is not global, so a new volume shows a short list.
     */
    public static class PostProcess implements ComponentEditorProvider<PostProcessVolumeComponent, Table> {
        @Override
        public Class<PostProcessVolumeComponent> getComponentClass() {
            return PostProcessVolumeComponent.class;
        }

        @Override
        public String getDisplayName() {
            return "Post Process Volume";
        }

        @Override
        public void buildEditor(Table container, Entity entity, PostProcessVolumeComponent volume, EditorState editorState,
                                Object skinObj) {
            Skin skin = (Skin) skinObj;
            container.left();
            String current = PostProcessPreset.matching(volume).map(PostProcessPreset::getDisplayName).orElse(CUSTOM);
            InspectorFieldRows.choiceRow(container, skin, "Preset", current, presetNames(PostProcessPreset.values()), name ->
                PostProcessPreset.find(name).ifPresent(preset ->
                    editorState.executeCommand(EnvironmentPresets.applyPostProcess(editorState, entity, preset))));
            InspectorFieldRows.toggleRow(container, skin, "Global", volume.isGlobal(),
                on -> changePost(editorState, entity, volume, v -> v.setGlobal(on), on ? "Make Volume Global" : "Make Volume a Box"));
            if (!volume.isGlobal()) {
                InspectorFieldRows.vector3Row(container, skin, "Size", new Vector3(volume.getSizeX(), volume.getSizeY(), volume.getSizeZ()),
                    size -> changePost(editorState, entity, volume, v -> v.setSize(size.x, size.y, size.z), "Change Volume Size"));
                InspectorFieldRows.floatRow(container, skin, "Blend", volume.getBlendDistance(),
                    distance -> changePost(editorState, entity, volume, v -> v.setBlendDistance(distance), "Change Blend Distance"));
            }
            InspectorFieldRows.floatRow(container, skin, "Weight", volume.getWeight(),
                weight -> changePost(editorState, entity, volume, v -> v.setWeight(weight), "Change Volume Weight"));
            InspectorFieldRows.intRow(container, skin, "Priority", volume.getPriority(),
                priority -> changePost(editorState, entity, volume, v -> v.setPriority(priority), "Change Volume Priority"));

            InspectorFieldRows.toggleRow(container, skin, "Bloom", volume.isBloom(),
                on -> changePost(editorState, entity, volume, v -> v.setBloom(on), on ? "Turn Bloom On" : "Turn Bloom Off"));
            if (volume.isBloom()) {
                InspectorFieldRows.floatRow(container, skin, "Intensity", volume.getBloomIntensity(),
                    value -> changePost(editorState, entity, volume, v -> v.setBloomIntensity(value), "Change Bloom"));
                InspectorFieldRows.floatRow(container, skin, "Threshold", volume.getBloomThreshold(),
                    value -> changePost(editorState, entity, volume, v -> v.setBloomThreshold(value), "Change Bloom"));
                InspectorFieldRows.floatRow(container, skin, "Radius", volume.getBloomRadius(),
                    value -> changePost(editorState, entity, volume, v -> v.setBloomRadius(value), "Change Bloom"));
            }
            InspectorFieldRows.toggleRow(container, skin, "Grading", volume.isGrading(),
                on -> changePost(editorState, entity, volume, v -> v.setGrading(on), on ? "Turn Grading On" : "Turn Grading Off"));
            if (volume.isGrading()) {
                InspectorFieldRows.floatRow(container, skin, "Exposure", volume.getExposure(),
                    value -> changePost(editorState, entity, volume, v -> v.setExposure(value), "Change Exposure"));
                InspectorFieldRows.floatRow(container, skin, "Contrast", volume.getContrast(),
                    value -> changePost(editorState, entity, volume, v -> v.setContrast(value), "Change Contrast"));
                InspectorFieldRows.floatRow(container, skin, "Saturation", volume.getSaturation(),
                    value -> changePost(editorState, entity, volume, v -> v.setSaturation(value), "Change Saturation"));
                InspectorFieldRows.floatRow(container, skin, "Warmth", volume.getTemperature(),
                    value -> changePost(editorState, entity, volume, v -> v.setTemperature(value), "Change Warmth"));
                InspectorFieldRows.floatRow(container, skin, "Tint", volume.getTint(),
                    value -> changePost(editorState, entity, volume, v -> v.setTint(value), "Change Tint"));
                InspectorFieldRows.colorRow(container, skin, "Filter", volume.getColorFilter(),
                    color -> changePost(editorState, entity, volume, v -> v.setColorFilter(color), "Change Colour Filter"));
            }
            InspectorFieldRows.toggleRow(container, skin, "Occlusion", volume.isAmbientOcclusion(),
                on -> changePost(editorState, entity, volume, v -> v.setAmbientOcclusion(on),
                    on ? "Turn Ambient Occlusion On" : "Turn Ambient Occlusion Off"));
            if (volume.isAmbientOcclusion()) {
                InspectorFieldRows.floatRow(container, skin, "Intensity", volume.getAoIntensity(),
                    value -> changePost(editorState, entity, volume, v -> v.setAoIntensity(value), "Change Ambient Occlusion"));
                InspectorFieldRows.floatRow(container, skin, "Radius", volume.getAoRadius(),
                    value -> changePost(editorState, entity, volume, v -> v.setAoRadius(value), "Change Ambient Occlusion"));
            }
            InspectorFieldRows.toggleRow(container, skin, "Depth blur", volume.isDepthOfField(),
                on -> changePost(editorState, entity, volume, v -> v.setDepthOfField(on),
                    on ? "Turn Depth of Field On" : "Turn Depth of Field Off"));
            if (volume.isDepthOfField()) {
                InspectorFieldRows.floatRow(container, skin, "Focus at", volume.getFocusDistance(),
                    value -> changePost(editorState, entity, volume, v -> v.setFocusDistance(value), "Change Focus Distance"));
                InspectorFieldRows.floatRow(container, skin, "Sharp", volume.getFocusRange(),
                    value -> changePost(editorState, entity, volume, v -> v.setFocusRange(value), "Change Focus Range"));
                InspectorFieldRows.floatRow(container, skin, "Blur", volume.getBlur(),
                    value -> changePost(editorState, entity, volume, v -> v.setBlur(value), "Change Blur"));
            }
        }
    }

    private static void changePost(EditorState editorState, Entity entity, PostProcessVolumeComponent volume,
                                   Consumer<PostProcessVolumeComponent> edit, String description) {
        PostProcessVolumeComponent after = volume.copy();
        edit.accept(after);
        editorState.executeCommand(new ModifyComponentPropertyCommand<PostProcessVolumeComponent, PostProcessVolumeComponent>(
            editorState, entity.getId(), volume, volume.copy(), after, volume::set, description));
    }

    /**
     * One undo step: {@code edit} runs on a copy first, so a value the component refuses throws (the field restores its text)
     * before anything changes.
     */
    private static void change(EditorState editorState, Entity entity, AtmosphereComponent sky, Consumer<AtmosphereComponent> edit,
                               String description) {
        AtmosphereComponent after = sky.copy();
        edit.accept(after);
        editorState.executeCommand(new ModifyComponentPropertyCommand<AtmosphereComponent, AtmosphereComponent>(editorState,
            entity.getId(), sky, sky.copy(), after, sky::set, description));
    }

    private static void changeWater(EditorState editorState, Entity entity, WaterBodyComponent water, Consumer<WaterBodyComponent> edit,
                                    String description) {
        WaterBodyComponent after = water.copy();
        edit.accept(after);
        editorState.executeCommand(new ModifyComponentPropertyCommand<WaterBodyComponent, WaterBodyComponent>(editorState,
            entity.getId(), water, water.copy(), after, water::set, description));
    }

    /** "Custom" followed by the presets' display names. */
    static String[] presetNames(Enum<?>[] presets) {
        String[] names = new String[presets.length + 1];
        names[0] = CUSTOM;
        Arrays.setAll(names, i -> i == 0 ? CUSTOM : displayName(presets[i - 1]));
        return names;
    }

    private static String displayName(Enum<?> preset) {
        if (preset instanceof PostProcessPreset post) {
            return post.getDisplayName();
        }
        return preset instanceof AtmospherePreset sky ? sky.getDisplayName() : ((WaterPreset) preset).getDisplayName();
    }
}
