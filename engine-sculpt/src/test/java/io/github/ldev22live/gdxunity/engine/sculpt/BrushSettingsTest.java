package io.github.ldev22live.gdxunity.engine.sculpt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrushSettingsTest {
    @Test
    void shouldStartWithTheDocumentedDefaults() {
        BrushSettings settings = new BrushSettings();

        assertEquals(BrushSettings.DEFAULT_RADIUS, settings.getRadius());
        assertEquals(BrushSettings.DEFAULT_STRENGTH, settings.getStrength());
        assertEquals(BrushSettings.DEFAULT_SPACING, settings.getSpacing());
        assertEquals(Falloff.SMOOTH, settings.getFalloff());
        assertFalse(settings.isSubtract());
    }

    @Test
    void shouldRejectValuesOutOfRangeAndNamesTheValue() {
        BrushSettings settings = new BrushSettings();

        for (float radius : new float[] {0f, -1f, Float.NaN, Float.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> settings.setRadius(radius), "radius " + radius);
        }
        for (float strength : new float[] {-0.01f, 1.01f, Float.NaN}) {
            assertThrows(IllegalArgumentException.class, () -> settings.setStrength(strength), "strength " + strength);
        }
        for (float spacing : new float[] {0f, BrushSettings.MIN_SPACING / 2, BrushSettings.MAX_SPACING * 2, Float.NaN}) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> settings.setSpacing(spacing), "spacing " + spacing);
            assertTrue(error.getMessage().contains(String.valueOf(spacing)), error.getMessage());
        }
        assertThrows(NullPointerException.class, () -> settings.setFalloff(null));
        assertEquals(BrushSettings.DEFAULT_RADIUS, settings.getRadius(), "a rejected value changes nothing");
    }

    @Test
    void shouldAcceptTheBoundaryValues() {
        BrushSettings settings = new BrushSettings();

        settings.setStrength(0f);
        settings.setStrength(1f);
        settings.setSpacing(BrushSettings.MIN_SPACING);
        settings.setSpacing(BrushSettings.MAX_SPACING);

        assertEquals(1f, settings.getStrength());
        assertEquals(BrushSettings.MAX_SPACING, settings.getSpacing());
    }

    @Test
    void shouldCopyIndependently() {
        BrushSettings original = new BrushSettings();
        original.setRadius(2f);
        original.setSubtract(true);
        original.setFalloff(Falloff.SHARP);

        BrushSettings copy = original.copy();
        original.setRadius(5f);

        assertEquals(2f, copy.getRadius());
        assertTrue(copy.isSubtract());
        assertEquals(Falloff.SHARP, copy.getFalloff());
    }

    @Test
    void shouldReportMirrorAxesAsAMask() {
        SymmetrySettings symmetry = new SymmetrySettings();
        assertEquals(0, symmetry.axisMask());

        symmetry.setMirrorX(true);
        symmetry.setMirrorZ(true);

        assertEquals(5, symmetry.axisMask());
        assertEquals(7, new SymmetrySettings(true, true, true).axisMask());
        assertEquals(symmetry.axisMask(), symmetry.copy().axisMask());
    }

    @Test
    void shouldGiveEveryFalloffAWeightFromOneAtTheCentreToZeroAtTheEdge() {
        for (Falloff falloff : Falloff.values()) {
            assertEquals(1f, falloff.weight(0f), 1e-6f, falloff + " at the centre");
            assertEquals(0f, falloff.weight(1f), falloff + " at the edge");
            assertEquals(0f, falloff.weight(2f), falloff + " beyond the edge");
            assertEquals(falloff.weight(0f), falloff.weight(-0.5f), falloff + " treats a negative distance as the centre");
            float previous = 1f;
            for (int i = 0; i <= 100; i++) {
                float weight = falloff.weight(i / 100f);
                assertTrue(weight >= 0f && weight <= 1f, falloff + " stays within 0..1 at " + i / 100f);
                assertTrue(weight <= previous + 1e-6f, falloff + " never increases with distance at " + i / 100f);
                previous = weight;
            }
        }
    }

    @Test
    void shouldOrderTheFalloffsByHowFarTheyReach() {
        float middle = 0.5f;

        assertTrue(Falloff.SHARP.weight(middle) < Falloff.SMOOTH.weight(middle) + 1e-6f);
        assertTrue(Falloff.SMOOTH.weight(middle) <= Falloff.LINEAR.weight(middle) + 1e-6f);
        assertEquals(1f, Falloff.CONSTANT.weight(0.99f));
    }
}
