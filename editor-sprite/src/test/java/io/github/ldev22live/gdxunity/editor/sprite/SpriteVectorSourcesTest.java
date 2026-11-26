package io.github.ldev22live.gdxunity.editor.sprite;

import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteDocument;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteLayerSource;
import io.github.ldev22live.gdxunity.engine.vector.Rgba;
import io.github.ldev22live.gdxunity.engine.vector.VectorDocument;
import io.github.ldev22live.gdxunity.engine.vector.VectorFile;
import io.github.ldev22live.gdxunity.engine.vector.VectorPath;
import io.github.ldev22live.gdxunity.engine.vector.VectorShape;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Edit 2D Asset's vector side (TD-104, MC.3): linking layers to a drawing, redrawing them, and saving both. */
class SpriteVectorSourcesTest {
    private static final String VECTOR = "vectors/house.vector";
    private static final int RED = 0xFF0000FF;
    private static final int BLUE = 0x0000FFFF;
    private static final int GREEN = 0x00FF00FF;

    @TempDir
    Path project;

    private SpriteSession sprite;
    private SpriteVectorSources sources;

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    /** An 8 × 8 drawing: a red square on the left half and a blue one on the right half, pixel aligned. */
    @BeforeEach
    void setUp() throws IOException {
        VectorDocument drawing = new VectorDocument(8, 8, List.of(
            square("Left", 0, Rgba.hex("#ff0000")),
            square("Right", 4, Rgba.hex("#0000ff"))));
        Files.createDirectories(project.resolve("vectors"));
        Files.writeString(project.resolve(VECTOR), VectorFile.write(drawing), StandardCharsets.UTF_8);
        SpriteDocument doc = new SpriteDocument(8, 8);
        doc.insertFrame(1, -1);
        doc.addLayer("Right");
        sprite = new SpriteSession(doc);
        sources = new SpriteVectorSources(sprite, project);
    }

    private static VectorShape square(String name, float x, Rgba fill) {
        VectorPath path = new VectorPath(List.of(new VectorPath.MoveTo(x, 0), new VectorPath.LineTo(x + 4, 0),
            new VectorPath.LineTo(x + 4, 8), new VectorPath.LineTo(x, 8), new VectorPath.Close()));
        return VectorShape.filled(name, path, fill);
    }

    @Test
    void shouldDrawAndLinkALayerInEveryFrameAsOneUndoStep() throws IOException {
        int steps = sprite.getHistory().getUndoCount();
        assertTrue(sources.link(0, VECTOR, List.of("Left")));

        assertEquals(steps + 1, sprite.getHistory().getUndoCount(), "one step");
        SpriteDocument doc = sprite.getDocument();
        for (int frame = 0; frame < 2; frame++) {
            assertEquals(RED, doc.getPixel(0, frame, 1, 1), "frame " + frame);
            assertEquals(0, doc.getPixel(0, frame, 6, 1), "only the named shape");
        }
        assertEquals(new SpriteLayerSource(VECTOR, List.of("Left")), doc.getLayerSource(0));
        assertEquals(List.of(new SpriteVectorSources.Link(0, doc.getLayer(0).name(), doc.getLayerSource(0))), sources.links());
        assertNull(sources.linkOf(1));

        assertTrue(sprite.getHistory().undo());
        assertNull(sprite.getDocument().getLayerSource(0), "undo unlinks");
        assertTrue(sprite.getDocument().isCelEmpty(0, 0), "and clears the drawing");
    }

    @Test
    void shouldRedrawAfterAVectorEditAndSayWhenTheLayerNoLongerMatchesItsDrawing() throws IOException {
        sources.link(0, VECTOR, List.of("Left"));
        sources.link(1, VECTOR, List.of("Right"));
        VectorSession drawing = sources.vector(VECTOR);
        assertTrue(drawing == sources.vector(VECTOR), "opened once and kept");

        assertTrue(drawing.setFill("Left", Rgba.hex("#00ff00")));
        assertTrue(sources.isPaintedOver(0), "the drawing changed, the layer not yet");
        assertFalse(sources.isPaintedOver(1));
        assertTrue(sources.redraw(0));
        assertEquals(GREEN, sprite.getDocument().getPixel(0, 1, 2, 2), "every frame redrawn");
        assertFalse(sources.isPaintedOver(0));
        assertFalse(sources.redraw(0), "drawing again changes nothing");

        sprite.setLayer(0);
        sprite.setColor(BLUE);
        sprite.paint(List.of(new int[] {1, 1}), BLUE, "Pencil");
        assertTrue(sources.isPaintedOver(0), "painted over by hand: a redraw would replace it");

        assertTrue(sprite.getHistory().undo());
        assertTrue(sprite.getHistory().undo(), "the redraw is one sprite step");
        assertEquals(RED, sprite.getDocument().getPixel(0, 0, 2, 2));
    }

    @Test
    void shouldRedrawEveryLayerOfADrawingInOneStep() throws IOException {
        sources.link(0, VECTOR, List.of("Left"));
        sources.link(1, VECTOR, List.of("Right"));
        VectorSession drawing = sources.vector(VECTOR);
        drawing.setFill("Left", Rgba.hex("#00ff00"));
        drawing.setFill("Right", Rgba.hex("#00ff00"));
        int steps = sprite.getHistory().getUndoCount();

        assertTrue(sources.redrawFrom(VECTOR));

        assertEquals(steps + 1, sprite.getHistory().getUndoCount());
        assertEquals(GREEN, sprite.getDocument().getPixel(0, 0, 1, 1));
        assertEquals(GREEN, sprite.getDocument().getPixel(1, 0, 6, 1));
        assertFalse(sources.redrawFrom("vectors/other.vector"), "no layer uses it");
    }

    @Test
    void shouldSaveChangedDrawingsBeforeTheSpriteAndReopenBoth() throws IOException {
        sources.link(0, VECTOR, List.of("Left"));
        assertThrows(IllegalStateException.class, sources::save, "the sprite has no file yet");
        Files.createDirectories(project.resolve("sprites"));
        File spriteFile = project.resolve("sprites/house.sprite").toFile();
        sprite.saveAs(spriteFile);
        sources.vector(VECTOR).movePoint("Left", new VectorPath.PointRef(1, 0), 3f, 0f);
        sources.redraw(0);
        assertTrue(sources.hasUnsavedVectors());

        List<File> written = sources.save();

        assertEquals(List.of(project.resolve(VECTOR).toFile(), spriteFile), written, "the drawing first, then the sprite");
        assertFalse(sources.hasUnsavedVectors());
        SpriteSession reopened = SpriteSession.open(spriteFile);
        SpriteVectorSources back = new SpriteVectorSources(reopened, project);
        assertEquals(3f, back.vector(VECTOR).getDocument().shape("Left").path().point(new VectorPath.PointRef(1, 0))[0], 0f);
        assertFalse(back.isPaintedOver(0), "the saved layer is what the saved drawing gives");
        assertArrayEquals(sprite.getDocument().copyCel(0, 0), reopened.getDocument().copyCel(0, 0));
    }

    @Test
    void shouldRefuseAnUnlinkedLayerAMissingDrawingAndAMissingShape() throws IOException {
        assertThrows(IllegalArgumentException.class, () -> sources.redraw(0));
        sprite.setLayerSource(0, new SpriteLayerSource("vectors/gone.vector", List.of()));
        IOException missing = assertThrows(IOException.class, () -> sources.redraw(0));
        assertTrue(missing.getMessage().contains("vectors/gone.vector"), missing.getMessage());
        assertFalse(sources.exists("vectors/gone.vector"));

        sprite.setLayerSource(0, new SpriteLayerSource(VECTOR, List.of("Chimney")));
        assertThrows(IllegalArgumentException.class, () -> sources.redraw(0));
        assertTrue(sprite.getDocument().isCelEmpty(0, 0), "nothing drawn");
    }
}
