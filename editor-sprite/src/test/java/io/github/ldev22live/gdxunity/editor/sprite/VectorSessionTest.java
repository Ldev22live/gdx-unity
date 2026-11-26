package io.github.ldev22live.gdxunity.editor.sprite;

import io.github.ldev22live.gdxunity.engine.sprite.SpriteDocument;
import io.github.ldev22live.gdxunity.engine.vector.PathData;
import io.github.ldev22live.gdxunity.engine.vector.Rgba;
import io.github.ldev22live.gdxunity.engine.vector.VectorDocument;
import io.github.ldev22live.gdxunity.engine.vector.VectorFile;
import io.github.ldev22live.gdxunity.engine.vector.VectorPath;
import io.github.ldev22live.gdxunity.engine.vector.VectorShape;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** TD-104 vector editing: every edit one undo step, SVG imports report what they skip, and drawings rasterize into sprite layers. */
class VectorSessionTest {
    private static final Rgba RED = Rgba.hex("#b03a2e");
    private static final Rgba BLUE = Rgba.hex("#2e5cb0");

    @TempDir
    Path temp;

    private static VectorDocument house() {
        return new VectorDocument(16, 16, List.of(
            VectorShape.filled("Roof", VectorPath.polygon(0, 0, 16, 0, 16, 4, 0, 4), RED),
            VectorShape.filled("Window", VectorPath.polygon(6, 8, 10, 8, 10, 12, 6, 12), Rgba.hex("#a0d8ff"))));
    }

    @Test
    void shouldRecolourMoveNodesAndUndoEachStep() {
        VectorSession session = new VectorSession(house());
        assertTrue(session.setFill("Roof", BLUE));
        assertEquals(BLUE, session.getDocument().shape("Roof").fill());
        VectorPath.PointRef corner = session.getDocument().shape("Window").path().points().get(2);
        assertTrue(session.movePoint("Window", corner, 12, 14));
        assertEquals(12f, session.getDocument().shape("Window").path().point(corner)[0]);
        assertTrue(session.movePoints("Window", List.of(corner, corner), 1, 0), "a point listed twice moves once");
        assertEquals(13f, session.getDocument().shape("Window").path().point(corner)[0]);
        assertTrue(session.isDirty());

        assertTrue(session.undo());
        assertEquals(12f, session.getDocument().shape("Window").path().point(corner)[0]);
        assertTrue(session.undo());
        assertTrue(session.undo());
        assertEquals(house(), session.getDocument(), "three undos give back the original drawing");
        assertTrue(session.redo());
        assertEquals(BLUE, session.getDocument().shape("Roof").fill());
        assertFalse(session.setFill("Roof", BLUE), "a change to the same value is no step");
    }

    @Test
    void shouldAddDeleteMoveAndReorderShapesAndNodes() {
        VectorSession session = new VectorSession(house());
        assertTrue(session.insertNode("Window", 1, 8, 6));
        assertEquals(new VectorPath.LineTo(8, 6), session.getDocument().shape("Window").path().segments().get(2));
        assertTrue(session.deleteNode("Window", 2));
        assertEquals(house().shape("Window"), session.getDocument().shape("Window"));
        assertThrows(IllegalArgumentException.class, () -> session.deleteNode("Window", 0), "a subpath's move is not deletable");
        assertThrows(IllegalArgumentException.class, () -> session.insertNode("Window", 4, 0, 0), "nothing follows a close");
        assertTrue(session.moveShape("Window", 2, -1));
        assertEquals(2f, session.getDocument().shape("Window").transform().e());
        assertTrue(session.reorder("Window", 0));
        assertEquals("Window", session.getDocument().shapes().getFirst().name());
        assertTrue(session.addShape(VectorShape.filled("Door", PathData.parse("M 1 1 L 2 1 L 2 2 Z"), RED)));
        assertTrue(session.removeShape("Door"));
        assertThrows(IllegalArgumentException.class, () -> session.setFill("Chimney", RED));
    }

    @Test
    void shouldRasterizeChosenShapesIntoOneSpriteLayerAsOneSpriteStep() {
        VectorSession vectors = new VectorSession(house());
        SpriteDocument sprite = new SpriteDocument(16, 16);
        int roofLayer = sprite.addLayer("Roof");
        SpriteSession sprites = new SpriteSession(sprite);
        int steps = sprites.getHistory().getUndoCount();

        assertTrue(vectors.rasterizeInto(sprites, roofLayer, 0, List.of("Roof")));
        assertEquals(steps + 1, sprites.getHistory().getUndoCount());
        assertEquals(0xb03a2eff, sprite.getPixel(roofLayer, 0, 8, 2), "the roof is drawn in its colour");
        assertEquals(0, sprite.getPixel(roofLayer, 0, 8, 10), "the window is not in this layer");

        vectors.setFill("Roof", BLUE);
        assertTrue(vectors.rasterizeInto(sprites, roofLayer, 0, List.of("Roof")), "redrawing after a vector edit");
        assertEquals(0x2e5cb0ff, sprite.getPixel(roofLayer, 0, 8, 2), "the layer follows the drawing");
        assertTrue(sprites.getHistory().undo());
        assertEquals(0xb03a2eff, sprite.getPixel(roofLayer, 0, 8, 2), "undo in the sprite takes the redraw back");
        assertThrows(IllegalArgumentException.class, () -> vectors.rasterizeInto(sprites, roofLayer, 0, List.of("Chimney")));
    }

    @Test
    void shouldImportSvgReportingWhatItSkipsAndSaveAndReopen() throws IOException {
        VectorSession imported = VectorSession.importSvg("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"16\" height=\"16\">"
            + "<rect id=\"Roof\" width=\"16\" height=\"4\" fill=\"#b03a2e\"/><text>House</text></svg>");
        assertEquals(List.of("element text"), imported.getLastImportReport());
        assertTrue(imported.isDirty(), "an import is not saved yet");
        File file = temp.resolve("house.vector").toFile();
        imported.saveAs(file);
        assertFalse(imported.isDirty());
        VectorSession reopened = VectorSession.open(file);
        assertEquals(imported.getDocument(), reopened.getDocument());
        assertEquals(List.of(), reopened.getLastImportReport());
        assertNull(reopened.getDocument().shape("Roof").stroke());
        Files.writeString(file.toPath(), "{\"schemaVersion\": 9}");
        assertThrows(IOException.class, () -> VectorSession.open(file));
        assertEquals(VectorFile.SCHEMA_VERSION, 1);
    }
}
