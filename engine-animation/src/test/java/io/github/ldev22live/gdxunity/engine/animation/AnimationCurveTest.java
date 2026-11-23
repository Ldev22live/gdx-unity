package io.github.ldev22live.gdxunity.engine.animation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimationCurveTest {

    private static AnimationCurve curve(Interpolation interpolation, float... timeValuePairs) {
        AnimationCurve curve = new AnimationCurve();
        for (int i = 0; i < timeValuePairs.length; i += 2) {
            curve.setKey(new Keyframe(timeValuePairs[i], timeValuePairs[i + 1], interpolation));
        }
        return curve;
    }

    @Test
    void shouldKeepKeysSortedAndReplaceKeyAtSameTime() {
        AnimationCurve curve = curve(Interpolation.LINEAR, 2f, 20f, 0f, 0f, 1f, 10f);

        Keyframe replaced = curve.setKey(new Keyframe(1f + AnimationCurve.TIME_EPSILON / 2f, 15f, Interpolation.LINEAR));

        assertEquals(10f, replaced.value());
        assertEquals(List.of(0f, 1f + AnimationCurve.TIME_EPSILON / 2f, 2f),
            curve.getKeys().stream().map(Keyframe::time).toList());
        assertEquals(15f, curve.getKey(1).value());
    }

    @Test
    void shouldHoldEndValuesOutsideKeys() {
        AnimationCurve curve = curve(Interpolation.LINEAR, 1f, 5f, 2f, 7f);

        assertEquals(5f, curve.evaluate(0f));
        assertEquals(7f, curve.evaluate(10f));
    }

    @Test
    void shouldInterpolateLinearly() {
        AnimationCurve curve = curve(Interpolation.LINEAR, 0f, 0f, 2f, 10f);

        assertEquals(2.5f, curve.evaluate(0.5f), 1e-5f);
    }

    @Test
    void shouldHoldValueWhenStepped() {
        AnimationCurve curve = curve(Interpolation.CONSTANT, 0f, 0f, 1f, 10f);

        assertEquals(0f, curve.evaluate(0.99f));
        assertEquals(10f, curve.evaluate(1f));
    }

    @Test
    void shouldEaseInAndOutWithBezierBetweenTwoKeys() {
        AnimationCurve curve = curve(Interpolation.BEZIER, 0f, 0f, 1f, 1f);

        // Flat tangents at both ends: slow start, halfway at the middle, slow end
        assertEquals(0.5f, curve.evaluate(0.5f), 1e-5f);
        assertTrue(curve.evaluate(0.1f) < 0.1f, "starts slower than linear");
        assertTrue(curve.evaluate(0.9f) > 0.9f, "ends slower than linear");
    }

    @Test
    void shouldPassSmoothlyThroughMiddleKeyWithBezier() {
        AnimationCurve curve = curve(Interpolation.BEZIER, 0f, 0f, 1f, 1f, 2f, 2f);

        // Middle tangent follows the neighbours (slope 1), so the curve is a straight line through it
        float h = 1e-3f;
        float slopeBefore = (curve.evaluate(1f) - curve.evaluate(1f - h)) / h;
        float slopeAfter = (curve.evaluate(1f + h) - curve.evaluate(1f)) / h;
        assertEquals(1f, curve.slopeAt(1), 1e-6f);
        assertEquals(slopeBefore, slopeAfter, 1e-2f);
    }

    @Test
    void shouldUseInterpolationOfEarlierKey() {
        AnimationCurve curve = new AnimationCurve();
        curve.setKey(new Keyframe(0f, 0f, Interpolation.CONSTANT));
        curve.setKey(new Keyframe(1f, 10f, Interpolation.LINEAR));
        curve.setKey(new Keyframe(2f, 20f, Interpolation.LINEAR));

        assertEquals(0f, curve.evaluate(0.5f));
        assertEquals(15f, curve.evaluate(1.5f), 1e-5f);
    }

    @Test
    void shouldRemoveKeyAtTime() {
        AnimationCurve curve = curve(Interpolation.LINEAR, 0f, 0f, 1f, 10f);

        assertEquals(10f, curve.removeKeyAt(1f).value());
        assertNull(curve.removeKeyAt(1f));
        assertEquals(1, curve.size());
    }

    @Test
    void shouldRefuseToEvaluateEmptyCurveAndInvalidKeys() {
        assertThrows(IllegalStateException.class, () -> new AnimationCurve().evaluate(0f));
        assertThrows(IllegalArgumentException.class, () -> new Keyframe(-1f, 0f, Interpolation.LINEAR));
        assertThrows(IllegalArgumentException.class, () -> new Keyframe(0f, Float.NaN, Interpolation.LINEAR));
    }
}
