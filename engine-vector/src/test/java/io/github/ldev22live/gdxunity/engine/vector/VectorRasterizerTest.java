package io.github.ldev22live.gdxunity.engine.vector;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Coverage and blending of the TD-104 rasterizer, against pixels worked out by hand. */
class VectorRasterizerTest {
    private static final Rgba RED = Rgba.hex("#ff0000");
    private static final Rgba BLUE = Rgba.hex("#0000ff");

    private static int alpha(int pixel) {
        return pixel & 0xff;
    }

    @Test
    void shouldFillPixelAlignedEdgesExactlyAndHalfCoveredPixelsByHalf() {
        VectorDocument square = new VectorDocument(16, 16, List.of(VectorShape.filled("Square", VectorPath.polygon(0, 0, 8.5f, 0,
            8.5f, 8, 0, 8), RED)));
        int[] pixels = VectorRasterizer.render(square);
        assertEquals(0xff0000ff, pixels[0], "inside: opaque red");
        assertEquals(0xff0000ff, pixels[7 * 16 + 7]);
        assertEquals(128, alpha(pixels[3 * 16 + 8]), "column 8 is half covered");
        assertEquals(0xff000000, pixels[3 * 16 + 8] & 0xffffff00, "and still red");
        assertEquals(0, pixels[3 * 16 + 9], "outside: transparent");
        assertEquals(0, pixels[8 * 16 + 3], "the bottom edge is pixel aligned");
    }

    @Test
    void shouldFollowTheFillRule() {
        // Two nested squares wound the same way: non-zero fills the inner one, even-odd leaves it empty
        VectorPath nested = PathData.parse("M 0 0 L 12 0 L 12 12 L 0 12 Z M 4 4 L 8 4 L 8 8 L 4 8 Z");
        int[] nonzero = VectorRasterizer.render(new VectorDocument(12, 12, List.of(VectorShape.filled("A", nested, RED))));
        int[] evenodd = VectorRasterizer.render(new VectorDocument(12, 12, List.of(new VectorShape("A", nested, Affine.IDENTITY, RED,
            VectorShape.FillRule.EVENODD, null, 0f, 1f))));
        assertEquals(255, alpha(nonzero[6 * 12 + 6]));
        assertEquals(0, alpha(evenodd[6 * 12 + 6]));
        assertEquals(255, alpha(evenodd[1 * 12 + 1]));
    }

    @Test
    void shouldStrokeWithinHalfTheWidthOfTheOutline() {
        VectorPath line = PathData.parse("M 2 8 L 14 8");
        int[] pixels = VectorRasterizer.render(new VectorDocument(16, 16, List.of(new VectorShape("Line", line, Affine.IDENTITY, null,
            VectorShape.FillRule.NONZERO, BLUE, 2f, 1f))));
        assertEquals(0x0000ffff, pixels[7 * 16 + 8], "the two rows the 2-unit stroke straddles are covered");
        assertEquals(0x0000ffff, pixels[8 * 16 + 8]);
        assertEquals(0, pixels[6 * 16 + 8]);
        assertEquals(0, pixels[9 * 16 + 8]);
        assertEquals(0, pixels[8 * 16], "the round cap reaches one unit past the end, not further");
    }

    @Test
    void shouldBlendLaterShapesOverEarlierOnesByOpacity() {
        VectorPath all = VectorPath.polygon(0, 0, 4, 0, 4, 4, 0, 4);
        int[] pixels = VectorRasterizer.render(new VectorDocument(4, 4, List.of(VectorShape.filled("Blue", all, BLUE),
            new VectorShape("Red", all, Affine.IDENTITY, RED, VectorShape.FillRule.NONZERO, null, 0f, 0.5f))));
        assertEquals(0x800080ff, pixels[5] & 0xfffffff0 | 0xf, "half red over blue");
        int[] translucent = VectorRasterizer.render(new VectorDocument(4, 4, List.of(VectorShape.filled("Red", all,
            RED.withOpacity(0.5f)))));
        assertEquals(0xff000080, translucent[5], "straight alpha: the colour stays red, alpha halves");
    }

    @Test
    void shouldScaleTheCanvasAndApplyShapeTransforms() {
        VectorShape moved = VectorShape.filled("Dot", VectorPath.polygon(0, 0, 1, 0, 1, 1, 0, 1), RED)
            .withTransform(Affine.translate(2, 3));
        int[] doubled = VectorRasterizer.render(new VectorDocument(4, 4, List.of(moved)), 8, 8);
        assertEquals(255, alpha(doubled[6 * 8 + 4]), "(2, 3) of a 4-unit canvas is pixel (4, 6) at 8 pixels");
        assertEquals(255, alpha(doubled[7 * 8 + 5]));
        assertEquals(0, alpha(doubled[6 * 8 + 3]));
    }

    @Test
    void shouldCoverACircleAsItsArea() {
        VectorDocument circle = SvgImport.read("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"64\" height=\"64\">"
            + "<circle cx=\"32\" cy=\"32\" r=\"20\"/></svg>").document();
        double covered = 0;
        for (int pixel : VectorRasterizer.render(circle)) {
            covered += alpha(pixel) / 255.0;
        }
        assertEquals(Math.PI * 400, covered, Math.PI * 400 * 0.01, "within 1 % of pi r squared");
        assertThrows(IllegalArgumentException.class, () -> VectorRasterizer.render(circle, 0, 4));
    }
}
