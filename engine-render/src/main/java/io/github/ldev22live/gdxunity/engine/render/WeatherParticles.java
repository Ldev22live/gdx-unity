package io.github.ldev22live.gdxunity.engine.render;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.scene.environment.AtmosphereComponent;

import java.util.Objects;
import java.util.Random;

/**
 * Rain drops or snow flakes falling in a box around the camera (roadmap M3.9, TD-090). The box moves with the camera:
 * a particle that leaves it on one side comes back on the other, and one that falls through the bottom starts again at
 * the top, so the weather looks the same wherever the camera goes. Pure simulation with fixed arrays and no
 * allocation per frame; drawing is {@link AtmosphereRenderer}'s job.
 *
 * <p>Sizes are world units at 100 units per metre.
 */
final class WeatherParticles {
    /** Most particles at full intensity. */
    static final int MAX_PARTICLES = 3000;
    /** Half the width and depth of the box around the camera: 15 m. */
    static final float HALF_EXTENT = 1500f;
    /** How far above the camera particles start. */
    static final float TOP = 1200f;
    /** How far below the camera particles fall before starting again. */
    static final float BOTTOM = 400f;
    /** Rain's falling speed per second: about 9 m/s. */
    static final float RAIN_SPEED = 900f;
    /** Snow's falling speed per second: about 1.2 m/s. */
    static final float SNOW_SPEED = 120f;
    /** How far snow sways from side to side per second at most. */
    static final float SNOW_SWAY = 50f;
    /** Rain drifts sideways by this fraction of its fall, so it slants a little. */
    static final float RAIN_SLANT = 0.12f;

    private final float[] x = new float[MAX_PARTICLES];
    private final float[] y = new float[MAX_PARTICLES];
    private final float[] z = new float[MAX_PARTICLES];
    private final float[] phase = new float[MAX_PARTICLES];
    private final Random random;
    private int count;
    private AtmosphereComponent.Weather weather = AtmosphereComponent.Weather.CLEAR;
    private float time;
    private boolean seeded;

    WeatherParticles(long seed) {
        this.random = new Random(seed);
    }

    /**
     * Moves the particles {@code deltaSeconds} on, around {@code camera}. Clear weather or zero intensity leaves none.
     * The first call, or a change of weather, scatters them through the whole box.
     */
    void update(float deltaSeconds, Vector3 camera, AtmosphereComponent.Weather weather, float intensity) {
        Objects.requireNonNull(camera, "camera cannot be null");
        Objects.requireNonNull(weather, "weather cannot be null");
        count = weather == AtmosphereComponent.Weather.CLEAR ? 0 : Math.round(MAX_PARTICLES * MathUtils.clamp(intensity, 0f, 1f));
        if (count == 0) {
            return;
        }
        if (!seeded || weather != this.weather) {
            for (int i = 0; i < MAX_PARTICLES; i++) {
                scatter(i, camera, camera.y - BOTTOM + random.nextFloat() * (TOP + BOTTOM));
            }
            seeded = true;
        }
        this.weather = weather;
        time += deltaSeconds;
        boolean rain = weather == AtmosphereComponent.Weather.RAIN;
        float fall = (rain ? RAIN_SPEED : SNOW_SPEED) * deltaSeconds;
        for (int i = 0; i < count; i++) {
            y[i] -= fall;
            if (rain) {
                x[i] += fall * RAIN_SLANT;
            } else {
                x[i] += MathUtils.sin(time + phase[i]) * SNOW_SWAY * deltaSeconds;
                z[i] += MathUtils.cos(time * 0.7f + phase[i]) * SNOW_SWAY * deltaSeconds;
            }
            if (y[i] < camera.y - BOTTOM) {
                scatter(i, camera, camera.y + TOP);
            } else if (y[i] > camera.y + TOP) {
                y[i] -= TOP + BOTTOM; // the camera fell faster than the particle: keep it in the box
            }
            x[i] = wrap(x[i], camera.x);
            z[i] = wrap(z[i], camera.z);
        }
    }

    private void scatter(int i, Vector3 camera, float height) {
        x[i] = camera.x + (random.nextFloat() * 2f - 1f) * HALF_EXTENT;
        z[i] = camera.z + (random.nextFloat() * 2f - 1f) * HALF_EXTENT;
        y[i] = height;
        phase[i] = random.nextFloat() * MathUtils.PI2;
    }

    /** Keeps {@code value} within {@link #HALF_EXTENT} of {@code center} by moving it a whole box width. */
    static float wrap(float value, float center) {
        float size = HALF_EXTENT * 2f;
        float offset = value - center + HALF_EXTENT;
        offset -= (float) Math.floor(offset / size) * size;
        return center - HALF_EXTENT + offset;
    }

    /** Particles falling now. */
    int count() {
        return count;
    }

    /** The weather of the last update. */
    AtmosphereComponent.Weather weather() {
        return weather;
    }

    float x(int i) {
        return x[i];
    }

    float y(int i) {
        return y[i];
    }

    float z(int i) {
        return z[i];
    }
}
