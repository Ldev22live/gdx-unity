package io.github.ldev22live.gdxunity.engine.scene.environment;

import com.badlogic.gdx.graphics.Color;
import io.github.ldev22live.gdxunity.engine.scene.BaseComponent;

import java.util.Objects;

/**
 * Effects applied to the finished 3D frame while the camera is in this volume (roadmap M6.2, TD-091), shown in the editor as
 * <b>Post Process Volume</b>.
 *
 * <ul>
 *   <li><b>Where:</b> a <i>global</i> volume applies everywhere. Otherwise the volume is a box of {@link #getSizeX()} by
 *       {@link #getSizeY()} by {@link #getSizeZ()} world units centred on the entity, turned and scaled with it; its effects fade in over
 *       {@link #getBlendDistance()} world units outside the box.</li>
 *   <li><b>How much:</b> {@link #getWeight()} scales everything this volume does. Volumes are applied in {@link #getPriority()} order,
 *       lowest first, so a higher priority wins where two overlap.</li>
 *   <li><b>What:</b> four effects, each with its own switch. A switched-off effect is not overridden by this volume: a lower volume's
 *       setting, or none, stays. <b>Bloom</b> makes bright parts glow; <b>colour grading</b> sets exposure, contrast, saturation, white
 *       balance and a colour filter; <b>ambient occlusion</b> darkens creases and contact areas; <b>depth of field</b> blurs what is
 *       nearer or farther than the focus distance.</li>
 * </ul>
 *
 * <p>Distances are world units at the editor's 100 units per metre. {@link PostProcessPreset} sets every effect at once.
 */
public class PostProcessVolumeComponent extends BaseComponent {
    /** Default edge of a box volume: 10 m. */
    public static final float DEFAULT_SIZE = 1000f;
    /** Largest bloom intensity. */
    public static final float MAX_BLOOM_INTENSITY = 5f;
    /** Bloom radius range, a multiple of the default blur spread. */
    public static final float MIN_BLOOM_RADIUS = 0.25f;
    public static final float MAX_BLOOM_RADIUS = 4f;
    /** Exposure range in photographic stops. */
    public static final float MAX_EXPOSURE_STOPS = 4f;
    /** Largest contrast and saturation (1 leaves the frame as it is). */
    public static final float MAX_CONTRAST = 2f;
    public static final float MAX_SATURATION = 2f;
    /** Largest ambient occlusion intensity. */
    public static final float MAX_AO_INTENSITY = 4f;
    /** Largest ambient occlusion radius and focus distance or range, in world units (1 km). */
    public static final float MAX_DISTANCE = 100000f;
    /** Values closer than this count as the same in {@link #sameSettings}. */
    static final float DISTANCE_TOLERANCE = 0.5f;

    private boolean global = true;
    private float sizeX = DEFAULT_SIZE;
    private float sizeY = DEFAULT_SIZE;
    private float sizeZ = DEFAULT_SIZE;
    private float blendDistance;
    private float weight = 1f;
    private int priority;

    private boolean bloom;
    private float bloomIntensity = 1f;
    private float bloomThreshold = 0.8f;
    private float bloomRadius = 1f;

    private boolean grading;
    private float exposure;
    private float contrast = 1f;
    private float saturation = 1f;
    private float temperature;
    private float tint;
    private final Color colorFilter = new Color(Color.WHITE);

    private boolean ambientOcclusion;
    private float aoIntensity = 1f;
    private float aoRadius = 50f;

    private boolean depthOfField;
    private float focusDistance = 1000f;
    private float focusRange = 500f;
    private float blur = 0.5f;

    /** A global volume with every effect switched off: it changes nothing until an effect is switched on. */
    public PostProcessVolumeComponent() {}

    /** Whether the volume applies everywhere, whatever its box. */
    public boolean isGlobal() {
        return global;
    }

    public void setGlobal(boolean global) {
        this.global = global;
    }

    /** Width of the box along the entity's x axis, in world units before the entity's scale. */
    public float getSizeX() {
        return sizeX;
    }

    /** Height of the box along the entity's y axis. */
    public float getSizeY() {
        return sizeY;
    }

    /** Depth of the box along the entity's z axis. */
    public float getSizeZ() {
        return sizeZ;
    }

    /**
     * Sets the box's size.
     *
     * @throws IllegalArgumentException if a side is not above 0 or not finite
     */
    public void setSize(float x, float y, float z) {
        if (!(x > 0f && y > 0f && z > 0f) || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
            throw new IllegalArgumentException("Every side of a volume must be above 0: " + x + ", " + y + ", " + z);
        }
        sizeX = x;
        sizeY = y;
        sizeZ = z;
    }

    /** World units outside the box over which the effects fade in; 0 switches them on only inside. */
    public float getBlendDistance() {
        return blendDistance;
    }

    /**
     * Sets how far outside the box the effects fade in.
     *
     * @throws IllegalArgumentException if {@code distance} is negative or past {@link #MAX_DISTANCE}
     */
    public void setBlendDistance(float distance) {
        blendDistance = inRange("Blend distance", distance, 0f, MAX_DISTANCE);
    }

    /** How strongly the volume applies, 0 (not at all) to 1 (fully). */
    public float getWeight() {
        return weight;
    }

    public void setWeight(float weight) {
        this.weight = inRange("Weight", weight, 0f, 1f);
    }

    /** Order among overlapping volumes: higher priorities are applied later and win. */
    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    /** Whether this volume sets bloom. */
    public boolean isBloom() {
        return bloom;
    }

    public void setBloom(boolean bloom) {
        this.bloom = bloom;
    }

    /** How strongly bright parts glow, 0 to {@link #MAX_BLOOM_INTENSITY}. */
    public float getBloomIntensity() {
        return bloomIntensity;
    }

    public void setBloomIntensity(float intensity) {
        bloomIntensity = inRange("Bloom intensity", intensity, 0f, MAX_BLOOM_INTENSITY);
    }

    /** Brightness, 0 to 1 on the displayed frame, above which a pixel glows. */
    public float getBloomThreshold() {
        return bloomThreshold;
    }

    public void setBloomThreshold(float threshold) {
        bloomThreshold = inRange("Bloom threshold", threshold, 0f, 1f);
    }

    /** How far the glow spreads, a multiple of the default spread. */
    public float getBloomRadius() {
        return bloomRadius;
    }

    public void setBloomRadius(float radius) {
        bloomRadius = inRange("Bloom radius", radius, MIN_BLOOM_RADIUS, MAX_BLOOM_RADIUS);
    }

    /** Whether this volume sets colour grading. */
    public boolean isGrading() {
        return grading;
    }

    public void setGrading(boolean grading) {
        this.grading = grading;
    }

    /** Brightness change in photographic stops: +1 doubles the light, -1 halves it. */
    public float getExposure() {
        return exposure;
    }

    public void setExposure(float stops) {
        exposure = inRange("Exposure", stops, -MAX_EXPOSURE_STOPS, MAX_EXPOSURE_STOPS);
    }

    /** Contrast around mid grey: 1 unchanged, 0 flat grey, 2 strong. */
    public float getContrast() {
        return contrast;
    }

    public void setContrast(float contrast) {
        this.contrast = inRange("Contrast", contrast, 0f, MAX_CONTRAST);
    }

    /** Colourfulness: 1 unchanged, 0 black and white, 2 vivid. */
    public float getSaturation() {
        return saturation;
    }

    public void setSaturation(float saturation) {
        this.saturation = inRange("Saturation", saturation, 0f, MAX_SATURATION);
    }

    /** White balance, -1 (cold, bluer) to 1 (warm, more orange). */
    public float getTemperature() {
        return temperature;
    }

    public void setTemperature(float temperature) {
        this.temperature = inRange("Temperature", temperature, -1f, 1f);
    }

    /** Tint, -1 (greener) to 1 (more magenta). */
    public float getTint() {
        return tint;
    }

    public void setTint(float tint) {
        this.tint = inRange("Tint", tint, -1f, 1f);
    }

    /** A colour the frame is multiplied by (a copy); white leaves it as it is. */
    public Color getColorFilter() {
        return new Color(colorFilter);
    }

    /** Writes the colour filter into {@code out} and returns it; for per-frame use without allocating. */
    public Color getColorFilter(Color out) {
        return out.set(colorFilter);
    }

    /** Sets the colour the frame is multiplied by. Its alpha is ignored. */
    public void setColorFilter(Color color) {
        colorFilter.set(Objects.requireNonNull(color, "color cannot be null")).a = 1f;
    }

    /** Whether this volume sets ambient occlusion. */
    public boolean isAmbientOcclusion() {
        return ambientOcclusion;
    }

    public void setAmbientOcclusion(boolean ambientOcclusion) {
        this.ambientOcclusion = ambientOcclusion;
    }

    /** How dark creases get, 0 to {@link #MAX_AO_INTENSITY}. */
    public float getAoIntensity() {
        return aoIntensity;
    }

    public void setAoIntensity(float intensity) {
        aoIntensity = inRange("Ambient occlusion intensity", intensity, 0f, MAX_AO_INTENSITY);
    }

    /** How far, in world units, a surface looks for nearby surfaces that hide it. */
    public float getAoRadius() {
        return aoRadius;
    }

    /**
     * Sets how far a surface looks for nearby surfaces.
     *
     * @throws IllegalArgumentException if {@code radius} is not above 0 or past {@link #MAX_DISTANCE}
     */
    public void setAoRadius(float radius) {
        if (!(radius > 0f)) {
            throw new IllegalArgumentException("Ambient occlusion radius must be above 0: " + radius);
        }
        aoRadius = inRange("Ambient occlusion radius", radius, 0f, MAX_DISTANCE);
    }

    /** Whether this volume sets depth of field. */
    public boolean isDepthOfField() {
        return depthOfField;
    }

    public void setDepthOfField(boolean depthOfField) {
        this.depthOfField = depthOfField;
    }

    /** Distance from the camera, in world units, that is sharpest. */
    public float getFocusDistance() {
        return focusDistance;
    }

    public void setFocusDistance(float distance) {
        focusDistance = inRange("Focus distance", distance, 0f, MAX_DISTANCE);
    }

    /** Depth, in world units around the focus distance, that stays fully sharp. */
    public float getFocusRange() {
        return focusRange;
    }

    public void setFocusRange(float range) {
        focusRange = inRange("Focus range", range, 0f, MAX_DISTANCE);
    }

    /** How blurred the out-of-focus parts get, 0 to 1. */
    public float getBlur() {
        return blur;
    }

    public void setBlur(float blur) {
        this.blur = inRange("Blur", blur, 0f, 1f);
    }

    /** Whether any effect is switched on. */
    public boolean hasEffects() {
        return bloom || grading || ambientOcclusion || depthOfField;
    }

    /** A new component with the same settings; enabled like this one. */
    public PostProcessVolumeComponent copy() {
        PostProcessVolumeComponent copy = new PostProcessVolumeComponent();
        copy.set(this);
        return copy;
    }

    /** Takes every setting of {@code other}; enabled state included. */
    public void set(PostProcessVolumeComponent other) {
        Objects.requireNonNull(other, "other cannot be null");
        setPlacement(other);
        setEffects(other);
        setEnabled(other.isEnabled());
    }

    /** Takes {@code other}'s place and reach: global, size, blend distance, weight and priority. */
    public void setPlacement(PostProcessVolumeComponent other) {
        Objects.requireNonNull(other, "other cannot be null");
        global = other.global;
        sizeX = other.sizeX;
        sizeY = other.sizeY;
        sizeZ = other.sizeZ;
        blendDistance = other.blendDistance;
        weight = other.weight;
        priority = other.priority;
    }

    /** Takes {@code other}'s four effects, their switches and settings; place and reach stay. */
    public void setEffects(PostProcessVolumeComponent other) {
        Objects.requireNonNull(other, "other cannot be null");
        bloom = other.bloom;
        bloomIntensity = other.bloomIntensity;
        bloomThreshold = other.bloomThreshold;
        bloomRadius = other.bloomRadius;
        grading = other.grading;
        exposure = other.exposure;
        contrast = other.contrast;
        saturation = other.saturation;
        temperature = other.temperature;
        tint = other.tint;
        colorFilter.set(other.colorFilter);
        ambientOcclusion = other.ambientOcclusion;
        aoIntensity = other.aoIntensity;
        aoRadius = other.aoRadius;
        depthOfField = other.depthOfField;
        focusDistance = other.focusDistance;
        focusRange = other.focusRange;
        blur = other.blur;
    }

    /**
     * Whether the four effects equal {@code other}'s: the switches exactly, the settings to within {@code tolerance} (distances to
     * {@link #DISTANCE_TOLERANCE}). A switched-off effect's settings are not compared, since they do nothing.
     */
    public boolean sameEffects(PostProcessVolumeComponent other, float tolerance) {
        Objects.requireNonNull(other, "other cannot be null");
        if (bloom != other.bloom || grading != other.grading || ambientOcclusion != other.ambientOcclusion
            || depthOfField != other.depthOfField) {
            return false;
        }
        if (bloom && !(close(bloomIntensity, other.bloomIntensity, tolerance) && close(bloomThreshold, other.bloomThreshold, tolerance)
            && close(bloomRadius, other.bloomRadius, tolerance))) {
            return false;
        }
        if (grading && !(close(exposure, other.exposure, tolerance) && close(contrast, other.contrast, tolerance)
            && close(saturation, other.saturation, tolerance) && close(temperature, other.temperature, tolerance)
            && close(tint, other.tint, tolerance) && AtmosphereComponent.close(colorFilter, other.colorFilter, tolerance))) {
            return false;
        }
        if (ambientOcclusion && !(close(aoIntensity, other.aoIntensity, tolerance)
            && close(aoRadius, other.aoRadius, DISTANCE_TOLERANCE))) {
            return false;
        }
        return !depthOfField || (close(focusDistance, other.focusDistance, DISTANCE_TOLERANCE)
            && close(focusRange, other.focusRange, DISTANCE_TOLERANCE) && close(blur, other.blur, tolerance));
    }

    private static boolean close(float a, float b, float tolerance) {
        return Math.abs(a - b) <= tolerance;
    }

    private static float inRange(String what, float value, float min, float max) {
        if (!(value >= min && value <= max)) {
            throw new IllegalArgumentException(what + " must be within " + min + " and " + max + ": " + value);
        }
        return value;
    }
}
