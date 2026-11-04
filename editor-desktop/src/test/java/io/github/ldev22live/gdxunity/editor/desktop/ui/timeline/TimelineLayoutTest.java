package io.github.ldev22live.gdxunity.editor.desktop.ui.timeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineLayoutTest {

    @Test
    void shouldMapTimeToPixelsBothWays() {
        TimelineLayout layout = new TimelineLayout(200f, 400f, 2f);

        assertEquals(200f, layout.xOf(0f));
        assertEquals(400f, layout.xOf(1f));
        assertEquals(1.5f, layout.timeAt(500f), 1e-5f);
    }

    @Test
    void shouldClampTimeToClip() {
        TimelineLayout layout = new TimelineLayout(200f, 400f, 2f);

        assertEquals(0f, layout.timeAt(10f));
        assertEquals(2f, layout.timeAt(5000f));
    }

    @Test
    void shouldSpaceLabelsSoTheyDoNotOverlap() {
        // 400 px for 2 s = 200 px/s: 0.5 s labels are 100 px apart, 0.25 s would be 50 px (too close)
        assertEquals(0.5f, new TimelineLayout(0f, 400f, 2f).labelStep());
        // 60 px for 10 s = 6 px/s: needs 10 s labels
        assertEquals(10f, new TimelineLayout(0f, 60f, 10f).labelStep());
    }

    @Test
    void shouldShowFrameTicksOnlyWhenFarEnoughApart() {
        TimelineLayout layout = new TimelineLayout(0f, 600f, 2f);

        assertTrue(layout.showFrameTicks(30));
        assertFalse(layout.showFrameTicks(120));
    }

    @Test
    void shouldRejectEmptyLayout() {
        assertThrows(IllegalArgumentException.class, () -> new TimelineLayout(0f, 0f, 1f));
        assertThrows(IllegalArgumentException.class, () -> new TimelineLayout(0f, 100f, 0f));
    }
}
