package io.github.ldev22live.gdxunity.engine.scene.environment;

import com.badlogic.gdx.graphics.Color;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Ready-made skies for {@link AtmosphereComponent} (roadmap M3.9, TD-090). Applying one sets every setting; a scene
 * keeps the settings, not the preset's name, so a preset that is applied and then tweaked reads as custom
 * ({@link #matching}).
 *
 * <p>Fog distances are world units at the editor's 100 units per metre: a clear day's haze starts 60 m away, thick fog
 * hides everything past 40 m.
 */
public enum AtmospherePreset {
    /** Blue sky, light haze far away, full sun. */
    CLEAR_DAY("Clear Day", rgb(0.24f, 0.47f, 0.85f), rgb(0.72f, 0.83f, 0.94f), true, rgb(0.72f, 0.83f, 0.94f),
        6000f, 40000f, AtmosphereComponent.Weather.CLEAR, 0f, 1f),
    /** Grey sky, hazy, the sun at half strength. */
    OVERCAST("Overcast", rgb(0.50f, 0.53f, 0.57f), rgb(0.70f, 0.71f, 0.73f), true, rgb(0.70f, 0.71f, 0.73f),
        2000f, 16000f, AtmosphereComponent.Weather.CLEAR, 0f, 0.5f),
    /** Thick pale fog that hides everything past 40 m. */
    FOGGY("Foggy", rgb(0.70f, 0.72f, 0.74f), rgb(0.80f, 0.81f, 0.82f), true, rgb(0.80f, 0.81f, 0.82f),
        200f, 4000f, AtmosphereComponent.Weather.CLEAR, 0f, 0.6f),
    /** Warm orange horizon under a violet sky, low warm light. */
    SUNSET("Sunset", rgb(0.30f, 0.27f, 0.52f), rgb(0.98f, 0.62f, 0.36f), true, rgb(0.93f, 0.66f, 0.50f),
        3000f, 26000f, AtmosphereComponent.Weather.CLEAR, 0f, 0.8f),
    /** Dark blue sky, the sun as faint moonlight. */
    NIGHT("Night", rgb(0.01f, 0.02f, 0.06f), rgb(0.06f, 0.09f, 0.17f), true, rgb(0.06f, 0.09f, 0.17f),
        1500f, 14000f, AtmosphereComponent.Weather.CLEAR, 0f, 0.15f),
    /** Dark grey sky, steady rain, close haze. */
    RAIN("Rain", rgb(0.33f, 0.36f, 0.40f), rgb(0.52f, 0.55f, 0.58f), true, rgb(0.52f, 0.55f, 0.58f),
        1000f, 10000f, AtmosphereComponent.Weather.RAIN, 0.7f, 0.4f),
    /** White sky, falling snow, close haze. */
    SNOW("Snow", rgb(0.70f, 0.74f, 0.80f), rgb(0.88f, 0.90f, 0.93f), true, rgb(0.88f, 0.90f, 0.93f),
        800f, 9000f, AtmosphereComponent.Weather.SNOW, 0.6f, 0.6f);

    /** Per-channel tolerance when telling whether a component still holds a preset (saved files round floats). */
    public static final float MATCH_TOLERANCE = 0.002f;

    private final String displayName;
    private final Color zenith;
    private final Color horizon;
    private final boolean fog;
    private final Color fogColor;
    private final float fogStart;
    private final float fogEnd;
    private final AtmosphereComponent.Weather weather;
    private final float weatherIntensity;
    private final float sunlight;

    AtmospherePreset(String displayName, Color zenith, Color horizon, boolean fog, Color fogColor, float fogStart, float fogEnd,
                     AtmosphereComponent.Weather weather, float weatherIntensity, float sunlight) {
        this.displayName = displayName;
        this.zenith = zenith;
        this.horizon = horizon;
        this.fog = fog;
        this.fogColor = fogColor;
        this.fogStart = fogStart;
        this.fogEnd = fogEnd;
        this.weather = weather;
        this.weatherIntensity = weatherIntensity;
        this.sunlight = sunlight;
    }

    /** The name shown in the editor, such as "Clear Day". */
    public String getDisplayName() {
        return displayName;
    }

    /** Sets every setting of {@code target} to this preset's; its enabled state is left alone. */
    public void applyTo(AtmosphereComponent target) {
        Objects.requireNonNull(target, "target cannot be null");
        target.setZenithColor(zenith);
        target.setHorizonColor(horizon);
        target.setFogEnabled(fog);
        target.setFogColor(fogColor);
        target.setFogRange(fogStart, fogEnd);
        target.setWeather(weather);
        target.setWeatherIntensity(weatherIntensity);
        target.setSunlight(sunlight);
    }

    /** Whether {@code component} holds exactly this preset's settings (to {@link #MATCH_TOLERANCE}). */
    public boolean matches(AtmosphereComponent component) {
        Objects.requireNonNull(component, "component cannot be null");
        AtmosphereComponent reference = new AtmosphereComponent();
        applyTo(reference);
        return reference.sameSettings(component, MATCH_TOLERANCE);
    }

    /** The preset {@code component} holds, or empty when its settings are custom. */
    public static Optional<AtmospherePreset> matching(AtmosphereComponent component) {
        for (AtmospherePreset preset : values()) {
            if (preset.matches(component)) {
                return Optional.of(preset);
            }
        }
        return Optional.empty();
    }

    /**
     * The preset named {@code name}, matched to its display name or constant name ignoring case, spaces and
     * underscores ("clear day", "CLEAR_DAY" and "ClearDay" all find {@link #CLEAR_DAY}).
     */
    public static Optional<AtmospherePreset> find(String name) {
        Objects.requireNonNull(name, "name cannot be null");
        String key = normalise(name);
        for (AtmospherePreset preset : values()) {
            if (normalise(preset.displayName).equals(key) || normalise(preset.name()).equals(key)) {
                return Optional.of(preset);
            }
        }
        return Optional.empty();
    }

    static String normalise(String name) {
        return name.replace(" ", "").replace("_", "").toLowerCase(Locale.ROOT);
    }

    private static Color rgb(float r, float g, float b) {
        return new Color(r, g, b, 1f);
    }
}
