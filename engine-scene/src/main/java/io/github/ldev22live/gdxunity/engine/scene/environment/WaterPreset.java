package io.github.ldev22live.gdxunity.engine.scene.environment;

import com.badlogic.gdx.graphics.Color;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Ready-made looks for {@link WaterBodyComponent} (roadmap M3.9, TD-090): colour, opacity, roughness and waves. A
 * preset leaves the water's size alone. Wave sizes are world units at 100 units per metre.
 */
public enum WaterPreset {
    /** Clear blue-green, smooth, small ripples. */
    LAKE("Lake", new Color(0.12f, 0.38f, 0.46f, 0.70f), 0.12f, 4f, 600f, 60f),
    /** Deep blue, nearly opaque, rolling swell. */
    OCEAN("Ocean", new Color(0.04f, 0.20f, 0.38f, 0.88f), 0.18f, 30f, 2400f, 260f),
    /** Murky green-brown, opaque, still. */
    SWAMP("Swamp", new Color(0.22f, 0.28f, 0.14f, 0.92f), 0.45f, 0.5f, 400f, 10f),
    /** Bright turquoise, see-through, gentle waves. */
    TROPICAL("Tropical", new Color(0.10f, 0.70f, 0.72f, 0.55f), 0.08f, 8f, 900f, 90f),
    /** Pale grey-blue, frosted and still. */
    ICY("Icy", new Color(0.70f, 0.82f, 0.90f, 0.85f), 0.30f, 0f, 1000f, 0f);

    /** Per-value tolerance when telling whether a water body still holds a preset (saved files round floats). */
    public static final float MATCH_TOLERANCE = 0.002f;

    private final String displayName;
    private final Color color;
    private final float roughness;
    private final float waveHeight;
    private final float waveLength;
    private final float waveSpeed;

    WaterPreset(String displayName, Color color, float roughness, float waveHeight, float waveLength, float waveSpeed) {
        this.displayName = displayName;
        this.color = color;
        this.roughness = roughness;
        this.waveHeight = waveHeight;
        this.waveLength = waveLength;
        this.waveSpeed = waveSpeed;
    }

    /** The name shown in the editor, such as "Lake". */
    public String getDisplayName() {
        return displayName;
    }

    /** Sets the look of {@code target} to this preset's; size and enabled state are left alone. */
    public void applyTo(WaterBodyComponent target) {
        Objects.requireNonNull(target, "target cannot be null");
        target.setColor(color);
        target.setRoughness(roughness);
        target.setWaves(waveHeight, waveLength, waveSpeed);
    }

    /** Whether {@code water} has this preset's look (to {@link #MATCH_TOLERANCE}; wave sizes to half a unit). */
    public boolean matches(WaterBodyComponent water) {
        Objects.requireNonNull(water, "water cannot be null");
        return AtmosphereComponent.close(color, water.getColor(), MATCH_TOLERANCE)
            && Math.abs(roughness - water.getRoughness()) <= MATCH_TOLERANCE
            && Math.abs(waveHeight - water.getWaveHeight()) <= AtmosphereComponent.DISTANCE_TOLERANCE
            && Math.abs(waveLength - water.getWaveLength()) <= AtmosphereComponent.DISTANCE_TOLERANCE
            && Math.abs(waveSpeed - water.getWaveSpeed()) <= AtmosphereComponent.DISTANCE_TOLERANCE;
    }

    /** The preset {@code water} looks like, or empty when its look is custom. */
    public static Optional<WaterPreset> matching(WaterBodyComponent water) {
        for (WaterPreset preset : values()) {
            if (preset.matches(water)) {
                return Optional.of(preset);
            }
        }
        return Optional.empty();
    }

    /** The preset named {@code name}, ignoring case, spaces and underscores. */
    public static Optional<WaterPreset> find(String name) {
        Objects.requireNonNull(name, "name cannot be null");
        String key = name.replace(" ", "").replace("_", "").toLowerCase(Locale.ROOT);
        for (WaterPreset preset : values()) {
            if (preset.name().toLowerCase(Locale.ROOT).equals(key)) {
                return Optional.of(preset);
            }
        }
        return Optional.empty();
    }
}
