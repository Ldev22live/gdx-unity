package io.github.ldev22live.gdxunity.engine.scene.environment;

import com.badlogic.gdx.graphics.Color;
import io.github.ldev22live.gdxunity.engine.scene.BaseComponent;

import java.util.Objects;

/**
 * The sky, fog and weather of a 3D scene (roadmap M3.9, TD-090), shown in the editor as <b>Sky and Weather</b>.
 *
 * <ul>
 *   <li><b>Sky:</b> a gradient from the horizon colour up to the zenith colour, drawn behind everything.</li>
 *   <li><b>Fog:</b> surfaces fade towards the fog colour from {@link #getFogStart()} to {@link #getFogEnd()} world units
 *       from the camera.</li>
 *   <li><b>Weather:</b> rain or snow falling around the camera, as many drops as {@link #getWeatherIntensity()} asks.</li>
 *   <li><b>Sunlight:</b> a multiplier on every directional light, so an overcast or night preset dims the sun without
 *       editing the light itself.</li>
 * </ul>
 *
 * <p>Colours are sRGB, like any colour picker. The entity's transform is ignored, and only the first enabled one in a
 * scene is used. {@link AtmospherePreset} fills every setting at once.
 */
public class AtmosphereComponent extends BaseComponent {
    /** What falls from the sky. */
    public enum Weather {
        /** Nothing falls. */
        CLEAR,
        /** Fast, thin streaks. */
        RAIN,
        /** Slow, drifting flakes. */
        SNOW
    }

    /** Largest sunlight multiplier; past this the scene only clips to white. */
    public static final float MAX_SUNLIGHT = 4f;
    /** Fog distances closer than this, in world units, count as the same in {@link #sameSettings}. */
    static final float DISTANCE_TOLERANCE = 0.5f;

    private final Color zenithColor = new Color();
    private final Color horizonColor = new Color();
    private boolean fogEnabled;
    private final Color fogColor = new Color();
    private float fogStart;
    private float fogEnd;
    private Weather weather = Weather.CLEAR;
    private float weatherIntensity;
    private float sunlight = 1f;

    /** A clear day ({@link AtmospherePreset#CLEAR_DAY}). */
    public AtmosphereComponent() {
        AtmospherePreset.CLEAR_DAY.applyTo(this);
    }

    /** Colour of the sky straight up (a copy). */
    public Color getZenithColor() {
        return new Color(zenithColor);
    }

    /** Writes the zenith colour into {@code out} and returns it; for per-frame use without allocating. */
    public Color getZenithColor(Color out) {
        return out.set(zenithColor);
    }

    /** Sets the colour of the sky straight up. */
    public void setZenithColor(Color color) {
        zenithColor.set(Objects.requireNonNull(color, "color cannot be null")).a = 1f;
    }

    /** Colour of the sky at the horizon (a copy); the view below the horizon has it too. */
    public Color getHorizonColor() {
        return new Color(horizonColor);
    }

    /** Writes the horizon colour into {@code out} and returns it; for per-frame use without allocating. */
    public Color getHorizonColor(Color out) {
        return out.set(horizonColor);
    }

    /** Sets the colour of the sky at the horizon. */
    public void setHorizonColor(Color color) {
        horizonColor.set(Objects.requireNonNull(color, "color cannot be null")).a = 1f;
    }

    /** Whether surfaces fade into the fog colour with distance. */
    public boolean isFogEnabled() {
        return fogEnabled;
    }

    public void setFogEnabled(boolean fogEnabled) {
        this.fogEnabled = fogEnabled;
    }

    /** Colour far surfaces fade to (a copy). */
    public Color getFogColor() {
        return new Color(fogColor);
    }

    /** Writes the fog colour into {@code out} and returns it; for per-frame use without allocating. */
    public Color getFogColor(Color out) {
        return out.set(fogColor);
    }

    /** Sets the colour far surfaces fade to. */
    public void setFogColor(Color color) {
        fogColor.set(Objects.requireNonNull(color, "color cannot be null")).a = 1f;
    }

    /** Distance from the camera, in world units, where fog begins. */
    public float getFogStart() {
        return fogStart;
    }

    /** Distance from the camera, in world units, where fog hides everything. */
    public float getFogEnd() {
        return fogEnd;
    }

    /**
     * Sets where fog begins and where it hides everything, in world units from the camera.
     *
     * @throws IllegalArgumentException if {@code start} is negative, {@code end} is not past {@code start}, or either is
     *                                  not a finite number
     */
    public void setFogRange(float start, float end) {
        if (!(start >= 0f) || !Float.isFinite(start) || !(end > start) || !Float.isFinite(end)) {
            throw new IllegalArgumentException("Fog needs 0 <= start < end, both finite: start " + start + ", end " + end);
        }
        this.fogStart = start;
        this.fogEnd = end;
    }

    /** What falls from the sky. */
    public Weather getWeather() {
        return weather;
    }

    public void setWeather(Weather weather) {
        this.weather = Objects.requireNonNull(weather, "weather cannot be null");
    }

    /** How heavy the rain or snow is, 0 (none) to 1 (a downpour or blizzard). */
    public float getWeatherIntensity() {
        return weatherIntensity;
    }

    /**
     * Sets how heavy the rain or snow is.
     *
     * @throws IllegalArgumentException if {@code intensity} is outside 0 to 1
     */
    public void setWeatherIntensity(float intensity) {
        if (!(intensity >= 0f && intensity <= 1f)) {
            throw new IllegalArgumentException("Weather intensity must be within 0 and 1: " + intensity);
        }
        this.weatherIntensity = intensity;
    }

    /** Multiplier on every directional light's intensity; 1 leaves them as authored. */
    public float getSunlight() {
        return sunlight;
    }

    /**
     * Sets the multiplier on every directional light's intensity.
     *
     * @throws IllegalArgumentException if {@code sunlight} is outside 0 to {@link #MAX_SUNLIGHT}
     */
    public void setSunlight(float sunlight) {
        if (!(sunlight >= 0f && sunlight <= MAX_SUNLIGHT)) {
            throw new IllegalArgumentException("Sunlight must be within 0 and " + MAX_SUNLIGHT + ": " + sunlight);
        }
        this.sunlight = sunlight;
    }

    /** A new component with the same settings; enabled like this one. */
    public AtmosphereComponent copy() {
        AtmosphereComponent copy = new AtmosphereComponent();
        copy.set(this);
        return copy;
    }

    /** Takes every setting of {@code other}; enabled state included. */
    public void set(AtmosphereComponent other) {
        Objects.requireNonNull(other, "other cannot be null");
        zenithColor.set(other.zenithColor);
        horizonColor.set(other.horizonColor);
        fogEnabled = other.fogEnabled;
        fogColor.set(other.fogColor);
        fogStart = other.fogStart;
        fogEnd = other.fogEnd;
        weather = other.weather;
        weatherIntensity = other.weatherIntensity;
        sunlight = other.sunlight;
        setEnabled(other.isEnabled());
    }

    /** Whether every setting equals {@code other}'s, colours to within {@code colorTolerance} per channel. */
    public boolean sameSettings(AtmosphereComponent other, float colorTolerance) {
        Objects.requireNonNull(other, "other cannot be null");
        return close(zenithColor, other.zenithColor, colorTolerance)
            && close(horizonColor, other.horizonColor, colorTolerance)
            && fogEnabled == other.fogEnabled
            && close(fogColor, other.fogColor, colorTolerance)
            && Math.abs(fogStart - other.fogStart) <= DISTANCE_TOLERANCE
            && Math.abs(fogEnd - other.fogEnd) <= DISTANCE_TOLERANCE
            && weather == other.weather
            && Math.abs(weatherIntensity - other.weatherIntensity) <= colorTolerance
            && Math.abs(sunlight - other.sunlight) <= colorTolerance;
    }

    static boolean close(Color a, Color b, float tolerance) {
        return Math.abs(a.r - b.r) <= tolerance && Math.abs(a.g - b.g) <= tolerance && Math.abs(a.b - b.b) <= tolerance
            && Math.abs(a.a - b.a) <= tolerance;
    }
}
