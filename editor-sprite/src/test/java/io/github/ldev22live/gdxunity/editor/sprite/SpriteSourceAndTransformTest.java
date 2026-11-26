package io.github.ldev22live.gdxunity.editor.sprite;

import io.github.ldev22live.gdxunity.engine.sprite.SpriteDocument;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteHitbox;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteLayerSource;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteSheet;
import io.github.ldev22live.gdxunity.engine.vector.Rgba;
import io.github.ldev22live.gdxunity.engine.vector.VectorDocument;
import io.github.ldev22live.gdxunity.engine.vector.VectorPath;
import io.github.ldev22live.gdxunity.engine.vector.VectorShape;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** TD-104 in 2D Studio: free selection transforms, hitbox edits, layers linked to their vector, and exports that know their source. */
class SpriteSourceAndTransformTest {
    private static final int RED = 0xff0000ff;
    private static final int GREEN = 0x00ff00ff;

    @TempDir
    Path temp;

    private static SpriteSession block(int width, int height) {
        SpriteDocument doc = new SpriteDocument(16, 16);
        for (int y = 4; y < 4 + height; y++) {
            for (int x = 4; x < 4 + width; x++) {
                doc.setPixel(0, 0, x, y, x == 4 ? GREEN : RED);
            }
        }
        SpriteSession session = new SpriteSession(doc);
        session.select(4, 4, 3 + width, 3 + height);
        return session;
    }

    private static int count(SpriteSession session, int colour) {
        int n = 0;
        for (int pixel : session.getDocument().copyCel(0, 0)) {
            if (pixel == colour) {
                n++;
            }
        }
        return n;
    }

    @Test
    void shouldScaleASelectionByNearestNeighbour() {
        SpriteSession session = block(2, 2);
        assertTrue(session.transformSelection(2f, 2f, 0f) > 0);
        assertEquals(new SpriteSession.SelectionRect(3, 3, 4, 4), session.getSelection(), "a 2x2 block about its centre becomes 4x4");
        assertEquals(8, count(session, GREEN), "the green left column is two pixels wide and four high");
        assertEquals(8, count(session, RED));
        assertTrue(session.getHistory().undo(), "one undo step");
        assertEquals(2, count(session, GREEN));
    }

    @Test
    void shouldRotateANonSquareSelectionAndKeepOnlyItsColours() {
        SpriteSession session = block(2, 4);
        session.transformSelection(1f, 1f, 90f);
        assertEquals(4, session.getSelection().width(), "a 2x4 selection turned a quarter is 4x2");
        assertEquals(2, session.getSelection().height());
        assertEquals(4, count(session, GREEN), "no pixel is lost or doubled at a right angle");
        assertEquals(4, count(session, RED));

        SpriteSession tilted = block(4, 4);
        tilted.transformSelection(1f, 1f, 30f);
        Set<Integer> colours = new HashSet<>();
        for (int pixel : tilted.getDocument().copyCel(0, 0)) {
            colours.add(pixel);
        }
        assertEquals(Set.of(0, GREEN, RED), colours, "nearest neighbour adds no blended colours to pixel art");
        assertThrows(IllegalArgumentException.class, () -> tilted.transformSelection(0f, 1f, 0f));
        SpriteSession none = new SpriteSession(new SpriteDocument(4, 4));
        assertThrows(IllegalStateException.class, () -> none.transformSelection(1f, 1f, 10f), "nothing selected");
    }

    @Test
    void shouldEditTheCurrentFramesHitboxesAsUndoableSteps() {
        SpriteSession session = new SpriteSession(new SpriteDocument(8, 8));
        session.addFrame();
        session.setFrame(1);
        assertTrue(session.setHitbox(new SpriteHitbox("attack", 1, 1, 3, 3)));
        assertTrue(session.setHitbox(new SpriteHitbox("attack", 2, 2, 3, 3)), "the same name replaces the box");
        assertEquals(List.of(new SpriteHitbox("attack", 2, 2, 3, 3)), session.getDocument().getHitboxes(1));
        assertEquals(List.of(), session.getDocument().getHitboxes(0), "other frames are untouched");
        assertTrue(session.removeHitbox("attack"));
        assertFalse(session.removeHitbox("attack"), "nothing left to remove");
        assertTrue(session.getHistory().undo());
        assertEquals(1, session.getDocument().getHitboxes(1).size());
    }

    @Test
    void shouldLinkALayerToTheVectorItIsDrawnFrom() {
        VectorSession vectors = new VectorSession(new VectorDocument(8, 8, List.of(VectorShape.filled("Roof",
            VectorPath.polygon(0, 0, 8, 0, 8, 2, 0, 2), Rgba.hex("#b03a2e")))));
        SpriteSession sprite = new SpriteSession(new SpriteDocument(8, 8));
        assertTrue(vectors.rasterizeInto(sprite, 0, 0, List.of("Roof"), "vectors/house.vector"));
        assertEquals(new SpriteLayerSource("vectors/house.vector", List.of("Roof")), sprite.getDocument().getLayerSource(0));
        assertTrue(sprite.getHistory().undo(), "drawing and linking are one step");
        assertNull(sprite.getDocument().getLayerSource(0));
        assertEquals(0, sprite.getDocument().getPixel(0, 0, 4, 1));
        assertTrue(sprite.setLayerSource(0, new SpriteLayerSource("other.vector", List.of())));
        assertTrue(sprite.setLayerSource(0, null));
    }

    @Test
    void shouldTellWhereAnExportedPngCameFrom() throws IOException {
        Path sprites = Files.createDirectories(temp.resolve("sprites"));
        Path source = sprites.resolve("hero.sprite");
        Files.writeString(source, "{}");
        SpriteSheet.SourceRecord record = SpriteExports.recordOf(temp, source);
        assertEquals("sprites/hero.sprite", record.path());

        Path sheet = sprites.resolve("hero.png");
        Files.write(sheet, new byte[] {1});
        Files.writeString(sprites.resolve("hero" + SpriteExports.ATLAS_SUFFIX),
            SpriteSheet.export(new SpriteDocument(2, 2), 0, 0, "hero.png", record).atlasJson());
        SpriteExports.Origin current = SpriteExports.originOf(temp, sheet);
        assertEquals(new SpriteExports.Origin(SpriteExports.State.CURRENT, "sprites/hero.sprite"), current);
        assertTrue(current.advice().contains("Open the source"), current.advice());

        Files.writeString(source, "{\"changed\": true}");
        assertEquals(SpriteExports.State.CHANGED, SpriteExports.originOf(temp, sheet).state());
        Files.delete(source);
        assertEquals(SpriteExports.State.MISSING, SpriteExports.originOf(temp, sheet).state());

        Path lone = sprites.resolve("roof.png");
        Files.write(lone, new byte[] {1});
        SpriteExports.Origin none = SpriteExports.originOf(temp, lone);
        assertEquals(SpriteExports.State.NONE, none.state());
        assertTrue(none.advice().contains("cannot be recovered"), none.advice());
        Path vector = Files.writeString(temp.resolve("house.vector"), "{}");
        SpriteExports.writeSidecar(lone, SpriteExports.recordOf(temp, vector));
        assertEquals(new SpriteExports.Origin(SpriteExports.State.CURRENT, "house.vector"), SpriteExports.originOf(temp, lone));
        assertThrows(IOException.class, () -> SpriteExports.recordOf(temp.resolve("sprites"), vector), "a source outside the project");
    }
}
