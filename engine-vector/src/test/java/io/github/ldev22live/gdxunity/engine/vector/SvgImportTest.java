package io.github.ldev22live.gdxunity.engine.vector;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** TD-104's SVG subset: what is read, what is reported, and that export and import agree. */
class SvgImportTest {
    private static final float FIDELITY = 1e-4f;

    @Test
    void shouldReadTheSupportedElementsWithInheritedStyleAndTransforms() {
        SvgImport.Result result = SvgImport.read("""
            <svg xmlns="http://www.w3.org/2000/svg" width="64" height="32" viewBox="0 0 32 16">
              <title>House</title>
              <g transform="translate(2 1)" fill="#336699" stroke="red" stroke-width="0.5">
                <rect id="Wall" x="0" y="6" width="20" height="8"/>
                <path id="Roof" d="M -1 6 L 10 0 L 21 6 Z" style="fill: rgb(176, 58, 46); stroke: none"/>
                <circle id="Window" cx="10" cy="10" r="2" fill="white" fill-opacity="0.5"/>
                <ellipse cx="4" cy="10" rx="1" ry="2" fill-rule="evenodd"/>
                <line x1="0" y1="14" x2="20" y2="14"/>
                <polyline points="0,0 1,1 2,0" fill="none"/>
                <polygon points="22 6 26 6 24 2" opacity="0.25"/>
              </g>
            </svg>""");
        VectorDocument document = result.document();
        assertEquals(List.of(), result.unsupported(), "everything in this file is in the subset");
        assertEquals(64, document.width());
        assertEquals(32, document.height());
        assertEquals(List.of("Wall", "Roof", "Window", "ellipse", "line", "polyline", "polygon"),
            document.shapes().stream().map(VectorShape::name).toList());
        VectorShape wall = document.shape("Wall");
        assertEquals(Rgba.hex("#336699"), wall.fill(), "fill inherited from the group");
        assertEquals(Rgba.hex("#ff0000"), wall.stroke());
        assertEquals(0.5f, wall.strokeWidth());
        // The viewBox scales by 2, then the group translates by (2, 1): the wall's top left (0, 6) lands at (4, 14)
        assertEquals(4f, wall.transform().x(0, 6), FIDELITY);
        assertEquals(14f, wall.transform().y(0, 6), FIDELITY);
        VectorShape roof = document.shape("Roof");
        assertEquals(new Rgba(176 / 255f, 58 / 255f, 46 / 255f, 1f), roof.fill(), "style overrides the attribute");
        assertNull(roof.stroke(), "stroke: none in style");
        assertEquals(0.5f, document.shape("Window").fill().a(), FIDELITY);
        assertEquals(VectorShape.FillRule.EVENODD, document.shape("ellipse").fillRule());
        assertNull(document.shape("line").fill(), "a line is never filled");
        assertNull(document.shape("polyline").fill());
        assertEquals(0.25f, document.shape("polygon").opacity(), FIDELITY);
        float[] circle = document.shape("Window").path().flatten().getFirst().xy();
        for (int i = 0; i < circle.length; i += 2) {
            assertEquals(2.0, Math.hypot(circle[i] - 10, circle[i + 1] - 10), 0.01, "the circle's outline is round");
        }
    }

    @Test
    void shouldReportEverythingOutsideTheSubsetAndNotGuess() {
        SvgImport.Result result = SvgImport.read("""
            <svg xmlns="http://www.w3.org/2000/svg" width="10mm" height="10" viewBox="0 0 10 10">
              <defs><linearGradient id="g"/></defs>
              <text x="0" y="5">Hi</text>
              <rect width="5" height="5" fill="url(#g)" stroke-dasharray="1 1"/>
              <rect width="5" height="5" fill="hsl(0, 0%, 0%)" style="stroke-linecap: round"/>
              <rect width="5" height="5" transform="perspective(3)"/>
              <use href="#g"/>
              <image href="a.png" width="1" height="1"/>
            </svg>""");
        assertEquals(List.of("attribute stroke-dasharray", "colour hsl(0, 0%, 0%)", "colour url(#g)",
                "element defs", "element image", "element text", "element use", "style stroke-linecap", "transform perspective(3)",
                "unit in width=\"10mm\" (the viewBox size is used)"),
            result.unsupported());
        assertEquals(3, result.document().shapes().size(), "the three rects are read");
        assertNull(result.document().shapes().get(0).fill(), "an unreadable paint is left out, not replaced by a guess");
        assertEquals(Affine.IDENTITY, result.document().shapes().get(2).transform(), "an unreadable transform is reported");
    }

    @Test
    void shouldRefuseDoctypesAndEntities() {
        assertThrows(IllegalArgumentException.class, () -> SvgImport.read("""
            <?xml version="1.0"?>
            <!DOCTYPE svg [<!ENTITY x SYSTEM "file:///etc/passwd">]>
            <svg xmlns="http://www.w3.org/2000/svg" width="1" height="1"><text>&x;</text></svg>"""));
        assertThrows(IllegalArgumentException.class, () -> SvgImport.read("<html/>"), "not an svg root");
        assertThrows(IllegalArgumentException.class, () -> SvgImport.read("<svg xmlns=\"http://www.w3.org/2000/svg\"/>"),
            "no size");
        assertThrows(IllegalArgumentException.class, () -> SvgImport.read("<svg"), "not well-formed");
    }

    @Test
    void shouldReadItsOwnExportBackToTheSameShapes() {
        VectorDocument original = new VectorDocument(32, 32, List.of(
            new VectorShape("Roof", PathData.parse("M 2 12 L 16 2 L 30 12 Z"), Affine.IDENTITY, Rgba.hex("#b03a2e"),
                VectorShape.FillRule.NONZERO, Rgba.hex("#202020"), 1.5f, 1f),
            new VectorShape("Window", PathData.parse("M 12 16 C 12 14 20 14 20 16 L 20 22 L 12 22 Z"),
                Affine.translate(1, 2).times(Affine.rotate(15)), Rgba.hex("#a0d8ff80"), VectorShape.FillRule.EVENODD, null, 0f, 0.75f)));
        SvgImport.Result back = SvgImport.read(SvgExport.write(original));
        assertEquals(List.of(), back.unsupported());
        assertEquals(original.width(), back.document().width());
        assertEquals(original.shapes().size(), back.document().shapes().size());
        for (int s = 0; s < original.shapes().size(); s++) {
            VectorShape want = original.shapes().get(s);
            VectorShape got = back.document().shapes().get(s);
            assertEquals(want.name(), got.name());
            assertEquals(want.fillRule(), got.fillRule());
            assertEquals(want.stroke(), got.stroke());
            assertEquals(want.opacity(), got.opacity(), FIDELITY);
            assertEquals(want.fill().a(), got.fill().a(), 1f / 255f);
            assertEquals(want.path().segments().size(), got.path().segments().size());
            float[] t = {want.transform().a(), want.transform().b(), want.transform().c(), want.transform().d(), want.transform().e(),
                want.transform().f()};
            float[] u = {got.transform().a(), got.transform().b(), got.transform().c(), got.transform().d(), got.transform().e(),
                got.transform().f()};
            for (int i = 0; i < 6; i++) {
                assertEquals(t[i], u[i], FIDELITY, want.name() + " transform " + i);
            }
            for (VectorPath.PointRef ref : want.path().points()) {
                assertEquals(want.path().point(ref)[0], got.path().point(ref)[0], FIDELITY);
                assertEquals(want.path().point(ref)[1], got.path().point(ref)[1], FIDELITY);
            }
        }
        assertTrue(SvgExport.write(original).contains("fill-opacity=\""), "translucent fills keep their alpha");
    }
}
