package io.github.ldev22live.gdxunity.engine.scene.environment;

import com.badlogic.gdx.graphics.Color;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Ready-made looks for {@link PostProcessVolumeComponent} (roadmap M6.2, TD-091). Applying one sets the four effects and their
 * switches; the volume's place, size, blend distance, weight and priority stay. A scene keeps the settings, not the preset's name,
 * so a preset that is applied and then tweaked reads as custom ({@link #matching}).
 */
public enum PostProcessPreset {
    /** Every effect off: the volume changes nothing. */
    NONE("None", volume -> {}),
    /** A soft glow on highlights, slightly more contrast, a touch warm, contact shadows. */
    CINEMATIC("Cinematic", volume -> {
        bloom(volume, 0.6f, 0.8f, 1f);
        grading(volume, 0f, 1.15f, 0.9f, 0.1f, 0f, Color.WHITE);
        occlusion(volume, 1f, 50f);
    }),
    /** A strong wide glow, soft contrast, warm and slightly faded, the far distance blurred. */
    DREAMY("Dreamy", volume -> {
        bloom(volume, 1.5f, 0.6f, 2f);
        grading(volume, 0.2f, 0.85f, 0.8f, 0.15f, 0.05f, Color.WHITE);
        focus(volume, 800f, 600f, 0.6f);
    }),
    /** Black and white with hard contrast and deep contact shadows. */
    NOIR("Noir", volume -> {
        grading(volume, 0f, 1.4f, 0f, 0f, 0f, Color.WHITE);
        occlusion(volume, 1.5f, 60f);
    }),
    /** Golden-hour warmth with a light glow. */
    WARM("Warm", volume -> {
        bloom(volume, 0.4f, 0.8f, 1f);
        grading(volume, 0f, 1.05f, 1.1f, 0.45f, 0f, new Color(1f, 0.95f, 0.88f, 1f));
    }),
    /** A blue, washed-out cold. */
    COLD("Cold", volume -> grading(volume, -0.2f, 1.05f, 0.8f, -0.45f, -0.05f, new Color(0.9f, 0.96f, 1f, 1f))),
    /** Saturated colours, strong contrast and contact shadows. */
    VIVID("Vivid", volume -> {
        grading(volume, 0f, 1.15f, 1.35f, 0f, 0f, Color.WHITE);
        occlusion(volume, 1f, 40f);
    });

    /** Tolerance when telling whether a component still holds a preset (saved files round floats). */
    public static final float MATCH_TOLERANCE = 0.002f;

    private final String displayName;
    private final Consumer<PostProcessVolumeComponent> effects;

    PostProcessPreset(String displayName, Consumer<PostProcessVolumeComponent> effects) {
        this.displayName = displayName;
        this.effects = effects;
    }

    /** The name shown in the editor, such as "Cinematic". */
    public String getDisplayName() {
        return displayName;
    }

    /** Sets the four effects of {@code target} to this preset's; its place, reach and enabled state are left alone. */
    public void applyTo(PostProcessVolumeComponent target) {
        Objects.requireNonNull(target, "target cannot be null");
        PostProcessVolumeComponent look = new PostProcessVolumeComponent();
        effects.accept(look);
        target.setEffects(look);
    }

    /** Whether {@code component}'s effects are exactly this preset's (to {@link #MATCH_TOLERANCE}). */
    public boolean matches(PostProcessVolumeComponent component) {
        Objects.requireNonNull(component, "component cannot be null");
        PostProcessVolumeComponent reference = new PostProcessVolumeComponent();
        applyTo(reference);
        return reference.sameEffects(component, MATCH_TOLERANCE);
    }

    /** The preset {@code component} holds, or empty when its effects are custom. */
    public static Optional<PostProcessPreset> matching(PostProcessVolumeComponent component) {
        for (PostProcessPreset preset : values()) {
            if (preset.matches(component)) {
                return Optional.of(preset);
            }
        }
        return Optional.empty();
    }

    /** The preset named {@code name}, matched to its display name or constant name ignoring case, spaces and underscores. */
    public static Optional<PostProcessPreset> find(String name) {
        Objects.requireNonNull(name, "name cannot be null");
        String key = AtmospherePreset.normalise(name);
        for (PostProcessPreset preset : values()) {
            if (AtmospherePreset.normalise(preset.displayName).equals(key) || AtmospherePreset.normalise(preset.name()).equals(key)) {
                return Optional.of(preset);
            }
        }
        return Optional.empty();
    }

    private static void bloom(PostProcessVolumeComponent volume, float intensity, float threshold, float radius) {
        volume.setBloom(true);
        volume.setBloomIntensity(intensity);
        volume.setBloomThreshold(threshold);
        volume.setBloomRadius(radius);
    }

    private static void grading(PostProcessVolumeComponent volume, float exposure, float contrast, float saturation, float temperature,
                                float tint, Color filter) {
        volume.setGrading(true);
        volume.setExposure(exposure);
        volume.setContrast(contrast);
        volume.setSaturation(saturation);
        volume.setTemperature(temperature);
        volume.setTint(tint);
        volume.setColorFilter(filter);
    }

    private static void occlusion(PostProcessVolumeComponent volume, float intensity, float radius) {
        volume.setAmbientOcclusion(true);
        volume.setAoIntensity(intensity);
        volume.setAoRadius(radius);
    }

    private static void focus(PostProcessVolumeComponent volume, float distance, float range, float blur) {
        volume.setDepthOfField(true);
        volume.setFocusDistance(distance);
        volume.setFocusRange(range);
        volume.setBlur(blur);
    }
}
