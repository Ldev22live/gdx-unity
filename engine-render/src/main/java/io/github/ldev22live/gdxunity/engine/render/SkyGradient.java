package io.github.ldev22live.gdxunity.engine.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;

import java.util.Objects;

/**
 * Where the horizon falls on screen and which colours the sky has above it (roadmap M3.9, TD-090). Pure maths, reused
 * every frame, testable without a graphics context.
 *
 * <p>The sky's colour at an elevation {@code e} above the horizon is the horizon colour blended towards the zenith colour
 * by {@code sin(e)}; below the horizon it is the horizon colour (fog and ground cover that part). Cameras never roll in
 * this engine, so the horizon is a level line across the view.
 */
final class SkyGradient {
    /** Where the horizon is, as a fraction of the view's height from its bottom; may lie outside 0 to 1. */
    float horizonFraction;
    /** Sky colour at the bottom edge of the view, or at the horizon when the horizon is in view. */
    final Color lowColor = new Color();
    /** Sky colour at the top edge of the view. */
    final Color topColor = new Color();

    /**
     * Works out the gradient for a camera pitched {@code pitchDegrees} above level (negative: looking down).
     *
     * @param verticalFovDegrees the perspective camera's vertical field of view; ignored when {@code perspective} is false
     * @param perspective        false for an orthographic camera, whose view shows the whole sky gradient from the
     *                           horizon at its bottom edge to the zenith colour at its top
     */
    void compute(float pitchDegrees, float verticalFovDegrees, boolean perspective, Color horizon, Color zenith) {
        Objects.requireNonNull(horizon, "horizon cannot be null");
        Objects.requireNonNull(zenith, "zenith cannot be null");
        if (!perspective) {
            horizonFraction = 0f;
            lowColor.set(horizon);
            topColor.set(zenith);
            return;
        }
        float half = verticalFovDegrees * 0.5f;
        float pitch = MathUtils.clamp(pitchDegrees, -89.9f, 89.9f);
        // A level ray sits -pitch below the view's centre; tan maps that angle to the screen
        horizonFraction = 0.5f - 0.5f * MathUtils.tanDeg(pitch) / MathUtils.tanDeg(half);
        float bottomElevation = pitch - half;
        float topElevation = pitch + half;
        colorAt(Math.max(0f, bottomElevation), horizon, zenith, lowColor);
        colorAt(topElevation, horizon, zenith, topColor);
    }

    /** Sky colour {@code elevationDegrees} above the horizon; the horizon colour at or below it. */
    static Color colorAt(float elevationDegrees, Color horizon, Color zenith, Color out) {
        float t = MathUtils.sinDeg(MathUtils.clamp(elevationDegrees, 0f, 90f));
        out.r = horizon.r + (zenith.r - horizon.r) * t;
        out.g = horizon.g + (zenith.g - horizon.g) * t;
        out.b = horizon.b + (zenith.b - horizon.b) * t;
        out.a = 1f;
        return out;
    }
}
