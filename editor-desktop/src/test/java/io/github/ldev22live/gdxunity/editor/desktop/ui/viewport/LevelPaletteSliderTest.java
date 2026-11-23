package io.github.ldev22live.gdxunity.editor.desktop.ui.viewport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Brush Size slider of the Properties panel (UI M3b): logarithmic, so small brushes are as easy to set as large ones. */
class LevelPaletteSliderTest {
    @Test
    void shouldSpanTheBrushSizesOnALogScaleAndRoundTrip() {
        assertEquals(40f, LevelPalette.sliderToSize(0f), 0.01f, "the smallest brush at the left end");
        assertEquals(4000f, LevelPalette.sliderToSize(1f), 0.5f, "the largest at the right end");
        assertEquals(400f, LevelPalette.sliderToSize(0.5f), 0.5f, "the middle is the geometric middle, 40 x 10, not 2020");
        for (float radius : new float[] {40f, 120f, 300f, 1000f, 4000f}) {
            assertEquals(radius, LevelPalette.sliderToSize(LevelPalette.sizeToSlider(radius)), radius * 1e-4f, "round trip " + radius);
        }
        assertEquals(0f, LevelPalette.sizeToSlider(1f), 1e-6f, "a smaller brush than the slider offers sits at its left end");
        assertEquals(1f, LevelPalette.sizeToSlider(99999f), 1e-6f);
        assertTrue(LevelPalette.sliderToSize(0.25f) < 200f, "a quarter of the way is still a small brush");
    }
}
