package io.github.ldev22live.gdxunity.engine.vector;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VectorFileTest {
    private static final VectorDocument HOUSE = new VectorDocument(64, 48, List.of(
        VectorShape.filled("Wall", VectorPath.polygon(8, 20, 56, 20, 56, 46, 8, 46), Rgba.hex("#d8c8a8")),
        new VectorShape("Roof", PathData.parse("M 4 22 L 32 4 L 60 22 Z"), Affine.IDENTITY, Rgba.hex("#b03a2e"),
            VectorShape.FillRule.NONZERO, Rgba.hex("#401010"), 1f, 1f),
        new VectorShape("Window", PathData.parse("M 0 0 Q 6 -4 12 0 L 12 10 L 0 10 Z"), Affine.translate(26, 28), Rgba.hex("#a0d8ff"),
            VectorShape.FillRule.EVENODD, null, 0f, 0.9f)));

    @Test
    void shouldRoundTripADocument() {
        String text = VectorFile.write(HOUSE);
        assertEquals(HOUSE, VectorFile.read(text));
        assertEquals(text, VectorFile.write(VectorFile.read(text)), "writing is stable");
        assertTrue(text.contains("\"path\": \"M 4 22 L 32 4 L 60 22 Z\""), text);
        assertFalse(text.contains("transform\": [1"), "an identity transform is left out");
    }

    @Test
    void shouldRefuseOtherVersionsAndBrokenShapes() {
        assertThrows(IllegalArgumentException.class, () -> VectorFile.read("{\"schemaVersion\": 2, \"width\": 1, \"height\": 1}"));
        assertThrows(IllegalArgumentException.class, () -> VectorFile.read("not json"));
        assertThrows(IllegalArgumentException.class, () -> VectorFile.read("{\"schemaVersion\": 1, \"width\": 4, \"height\": 4, "
            + "\"shapes\": [{\"name\": \"A\", \"path\": \"M 0 0 L 1 1\"}, {\"name\": \"A\", \"path\": \"M 0 0 L 1 1\"}]}"),
            "duplicate names");
        assertThrows(IllegalArgumentException.class, () -> VectorFile.read("{\"schemaVersion\": 1, \"width\": 4, \"height\": 4, "
            + "\"shapes\": [{\"name\": \"A\", \"path\": \"M 0 0\", \"transform\": [1, 0]}]}"), "a short transform");
        assertThrows(IllegalArgumentException.class, () -> VectorFile.read("{\"schemaVersion\": 1, \"width\": 0, \"height\": 4}"));
    }

    @Test
    void shouldEditImmutably() {
        VectorDocument recoloured = HOUSE.with(HOUSE.shape("Roof").withFill(Rgba.hex("#2e5cb0")));
        assertEquals(Rgba.hex("#b03a2e"), HOUSE.shape("Roof").fill(), "the original is unchanged");
        assertEquals(Rgba.hex("#2e5cb0"), recoloured.shape("Roof").fill());
        assertEquals(List.of("Roof", "Wall", "Window"), HOUSE.moving("Roof", 0).shapes().stream().map(VectorShape::name).toList());
        assertEquals(2, HOUSE.removing("Wall").shapes().size());
        assertThrows(IllegalArgumentException.class, () -> HOUSE.adding(VectorShape.filled("Wall", VectorPath.polygon(0, 0, 1, 0, 1, 1),
            Rgba.BLACK)));
    }
}
