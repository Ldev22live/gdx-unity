package io.github.ldev22live.gdxunity.editor.sprite;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PixelOpsTest {
    private static Set<String> keys(List<int[]> points) {
        Set<String> out = new HashSet<>();
        for (int[] p : points) {
            out.add(p[0] + "," + p[1]);
        }
        return out;
    }

    @Test
    void shouldDrawALineIncludingBothEndsInOrder() {
        List<int[]> line = PixelOps.line(0, 0, 4, 2);

        assertArrayEquals(new int[] {0, 0}, line.get(0));
        assertArrayEquals(new int[] {4, 2}, line.get(line.size() - 1));
        assertEquals(5, line.size());
        for (int i = 1; i < line.size(); i++) {
            assertTrue(Math.abs(line.get(i)[0] - line.get(i - 1)[0]) <= 1 && Math.abs(line.get(i)[1] - line.get(i - 1)[1]) <= 1, "no gaps");
        }
    }

    @Test
    void shouldDrawASinglePixelLineAndLinesInAllDirections() {
        assertEquals(1, PixelOps.line(3, 3, 3, 3).size());
        assertEquals(keys(PixelOps.line(0, 0, 3, 0)), keys(PixelOps.line(3, 0, 0, 0)));
        assertEquals(4, PixelOps.line(2, 5, 2, 2).size());
    }

    @Test
    void shouldDrawRectanglesFilledAndAsOutlineWithEitherCornerFirst() {
        assertEquals(12, PixelOps.rect(3, 2, 0, 0, true).size());
        Set<String> outline = keys(PixelOps.rect(0, 0, 3, 2, false));

        assertEquals(10, outline.size());
        assertTrue(!outline.contains("1,1") && !outline.contains("2,1"));
    }

    @Test
    void shouldDrawAFilledEllipseThatTouchesTheBoxEdgesAndAnOutlineWithAnEmptyMiddle() {
        Set<String> filled = keys(PixelOps.ellipse(0, 0, 8, 6, true));
        Set<String> outline = keys(PixelOps.ellipse(0, 0, 8, 6, false));

        assertTrue(filled.contains("4,0") && filled.contains("4,6") && filled.contains("0,3") && filled.contains("8,3"));
        assertTrue(!filled.contains("0,0"), "corners of the box are outside");
        assertTrue(filled.containsAll(outline));
        assertTrue(!outline.contains("4,3"));
        assertTrue(outline.size() < filled.size());
    }

    @Test
    void shouldFloodFillOnlyTheConnectedRegionOfTheSameColor() {
        // 3x3: a wall of 1s in the middle column splits the zeros
        int[] pixels = {0, 1, 0,
                        0, 1, 0,
                        0, 1, 0};

        Set<String> left = keys(PixelOps.floodFill(pixels, 3, 3, 0, 0));

        assertEquals(Set.of("0,0", "0,1", "0,2"), left);
        assertTrue(PixelOps.floodFill(pixels, 3, 3, 5, 5).isEmpty());
    }

    @Test
    void shouldNotFillDiagonally() {
        int[] pixels = {0, 1,
                        1, 0};

        assertEquals(1, PixelOps.floodFill(pixels, 2, 2, 0, 0).size());
    }

    @Test
    void shouldRemoveCornerPixelsFromAStrokeSoTheLineIsOnePixelThick() {
        List<int[]> stroke = List.of(new int[] {0, 0}, new int[] {1, 0}, new int[] {1, 1}, new int[] {2, 1});

        Set<String> result = keys(PixelOps.pixelPerfect(stroke));

        assertEquals(Set.of("0,0", "1,1", "2,1"), result, "the (1,0) corner is dropped");
    }

    @Test
    void shouldKeepStraightStrokesUnchangedByThePixelPerfectRule() {
        List<int[]> stroke = PixelOps.line(0, 0, 5, 0);

        assertEquals(6, PixelOps.pixelPerfect(stroke).size());
    }
}
