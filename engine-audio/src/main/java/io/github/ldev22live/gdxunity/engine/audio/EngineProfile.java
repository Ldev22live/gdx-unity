package io.github.ldev22live.gdxunity.engine.audio;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * What kind of engine an {@link EngineSynth} sounds like: how many cylinders fire, the rev range, how evenly the cylinders
 * fire, how bright the exhaust is and how much it crackles when the driver lifts off the throttle. Scenes name a profile
 * (an Engine Sound component's {@code profile}); the factory profiles are listed by {@link #names()}.
 *
 * @param name         the name scenes use
 * @param cylinders    cylinders; a four-stroke engine fires {@code cylinders / 2} times per crankshaft turn
 * @param idleRpm      crankshaft speed when idling, revolutions per minute
 * @param redlineRpm   highest crankshaft speed; the rev limiter cuts in here
 * @param irregularity 0 to 1: how much louder or softer individual firings are than their neighbours (a V8's burble is high,
 *                     a straight six is nearly 0)
 * @param brightness   multiplier of the exhaust's filter cutoff; above 1 is sharper and buzzier, below 1 is rounder
 * @param crackle      0 to 1: how often the exhaust pops when the throttle is lifted at high revs
 */
public record EngineProfile(String name, int cylinders, float idleRpm, float redlineRpm, float irregularity,
                            float brightness, float crackle) {
    /** Fewest and most cylinders accepted. */
    public static final int MIN_CYLINDERS = 1;
    public static final int MAX_CYLINDERS = 16;

    /** A small four-cylinder: busy and buzzy, smooth. */
    public static final EngineProfile HATCHBACK = new EngineProfile("Hatchback", 4, 850f, 6500f, 0.06f, 1.0f, 0.05f);
    /** A straight six: smooth and high revving. */
    public static final EngineProfile SPORTS_COUPE = new EngineProfile("Sports Coupe", 6, 900f, 7600f, 0.03f, 1.25f, 0.18f);
    /** A big V8: low, lumpy at idle and crackling on the overrun. */
    public static final EngineProfile MUSCLE_V8 = new EngineProfile("Muscle V8", 8, 700f, 6200f, 0.3f, 0.75f, 0.6f);

    private static final Map<String, EngineProfile> FACTORY = new LinkedHashMap<>();

    static {
        for (EngineProfile profile : List.of(HATCHBACK, SPORTS_COUPE, MUSCLE_V8)) {
            FACTORY.put(profile.name.toLowerCase(Locale.ROOT), profile);
        }
    }

    /** @throws IllegalArgumentException if a value is outside its range, or the redline is not above the idle */
    public EngineProfile {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("An engine profile needs a name");
        }
        if (cylinders < MIN_CYLINDERS || cylinders > MAX_CYLINDERS) {
            throw new IllegalArgumentException("Cylinders must be within " + MIN_CYLINDERS + " to " + MAX_CYLINDERS + ": " + cylinders);
        }
        if (!(idleRpm > 0f && redlineRpm > idleRpm)) {
            throw new IllegalArgumentException("Revs must satisfy 0 < idle < redline: " + idleRpm + ", " + redlineRpm);
        }
        if (!(irregularity >= 0f && irregularity <= 1f)) {
            throw new IllegalArgumentException("Irregularity must be within 0 to 1: " + irregularity);
        }
        if (!(brightness > 0f)) {
            throw new IllegalArgumentException("Brightness must be positive: " + brightness);
        }
        if (!(crackle >= 0f && crackle <= 1f)) {
            throw new IllegalArgumentException("Crackle must be within 0 to 1: " + crackle);
        }
    }

    /** The factory profile names, in listing order. */
    public static List<String> names() {
        return FACTORY.values().stream().map(EngineProfile::name).toList();
    }

    /** Whether {@code name} is a factory profile (ignoring case). */
    public static boolean isKnown(String name) {
        return name != null && FACTORY.containsKey(name.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * @return the factory profile called {@code name}, ignoring case
     * @throws IllegalArgumentException if there is none
     */
    public static EngineProfile byName(String name) {
        Objects.requireNonNull(name, "name cannot be null");
        EngineProfile profile = FACTORY.get(name.trim().toLowerCase(Locale.ROOT));
        if (profile == null) {
            throw new IllegalArgumentException("Unknown engine '" + name + "'; use one of " + names());
        }
        return profile;
    }

    /** Cylinder firings per second at {@code rpm}. */
    public float firingHz(float rpm) {
        return rpm / 60f * cylinders / 2f;
    }
}
