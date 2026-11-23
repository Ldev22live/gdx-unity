package io.github.ldev22live.gdxunity.engine.sprite;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NineSliceTest {
    /** A 12 x 10 panel at (2, 3) of the frame with 3 pixel left and right borders and 2 pixel top and bottom borders. */
    private static final SpriteSlice PANEL = new SpriteSlice("panel", 2, 3, 12, 10, 3, 3, 2, 2, 0, 0);

    @Test
    void shouldMakeNinePiecesThatTileTheTargetExactly() {
        List<NineSlice.Patch> patches = NineSlice.patches(PANEL, 60f, 40f);

        assertEquals(9, patches.size());
        float area = 0f;
        for (NineSlice.Patch p : patches) {
            area += p.dstWidth() * p.dstHeight();
        }
        assertEquals(60f * 40f, area, 0.01f);
    }

    @Test
    void shouldKeepTheCornersAtTheirOwnSizeAndStretchTheMiddle() {
        List<NineSlice.Patch> patches = NineSlice.patches(PANEL, 60f, 40f);

        NineSlice.Patch topLeft = patches.get(0);
        assertEquals(new NineSlice.Patch(2, 3, 3, 2, 0f, 38f, 3f, 2f), topLeft);
        NineSlice.Patch middle = patches.get(4);
        assertEquals(new NineSlice.Patch(5, 5, 6, 6, 3f, 2f, 54f, 36f), middle);
        NineSlice.Patch bottomRight = patches.get(8);
        assertEquals(new NineSlice.Patch(11, 11, 3, 2, 57f, 0f, 3f, 2f), bottomRight);
    }

    @Test
    void shouldLeaveOutPiecesWithoutAreaWhenOnlySomeBordersAreSet() {
        SpriteSlice bar = new SpriteSlice("bar", 0, 0, 10, 4, 3, 3, 0, 0, 0, 0);

        List<NineSlice.Patch> patches = NineSlice.patches(bar, 30f, 8f);

        assertEquals(3, patches.size(), "left cap, stretched middle, right cap");
        assertEquals(8f, patches.get(1).dstHeight(), 1e-4f);
    }

    @Test
    void shouldShrinkTheBordersWhenTheTargetIsSmallerThanTheyAre() {
        List<NineSlice.Patch> patches = NineSlice.patches(PANEL, 4f, 40f);

        float widest = 0f;
        for (NineSlice.Patch p : patches) {
            assertTrue(p.dstX() >= 0f && p.dstX() + p.dstWidth() <= 4.001f, "inside the target: " + p);
            widest = Math.max(widest, p.dstX() + p.dstWidth());
        }
        assertEquals(4f, widest, 1e-3f);
    }

    @Test
    void shouldRefuseASliceWithoutBordersAndANonPositiveSize() {
        assertThrows(IllegalArgumentException.class, () -> NineSlice.patches(SpriteSlice.of("plain", 0, 0, 8, 8), 10f, 10f));
        assertThrows(IllegalArgumentException.class, () -> NineSlice.patches(PANEL, 0f, 10f));
    }
}
