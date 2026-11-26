package io.github.ldev22live.gdxunity.engine.sprite;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** TD-104: per-frame hitboxes and per-layer vector sources, the {@code .sprite} version 2 that carries them, and the atlas. */
class SpriteHitboxFileTest {
    private static final SpriteHitbox HURT = new SpriteHitbox("hurt", 2, 1, 4, 6);
    private static final SpriteHitbox SWING = new SpriteHitbox("attack", 6, 2, 5, 3);

    private static SpriteDocument character() {
        SpriteDocument doc = new SpriteDocument(8, 8);
        doc.insertFrame(1, 0);
        doc.insertFrame(2, -1);
        doc.setPixel(0, 1, 3, 3, 0xff0000ff);
        doc.setHitboxes(0, List.of(HURT));
        doc.setHitboxes(1, List.of(HURT, SWING));
        return doc;
    }

    @Test
    void shouldKeepPlainSpritesAtVersionOne() throws IOException {
        SpriteDocument plain = new SpriteDocument(4, 4);
        assertEquals(1, new JsonReader().parse(SpriteFile.write(plain)).getInt("schemaVersion"),
            "a sprite without hitboxes or sources stays readable by version-1 readers");
    }

    @Test
    void shouldRoundTripHitboxesAndLayerSourcesAsVersionTwo() throws IOException {
        SpriteDocument doc = character();
        int roof = doc.addLayer("Roof");
        doc.setLayerSource(roof, new SpriteLayerSource("vectors/house.vector", List.of("Roof")));
        String text = SpriteFile.write(doc);
        JsonValue json = new JsonReader().parse(text);
        assertEquals(2, json.getInt("schemaVersion"));
        assertEquals("vectors/house.vector", json.get("layers").get(roof).get("source").getString("vector"));
        SpriteDocument back = SpriteFile.read(text);
        assertTrue(back.contentEquals(doc), "hitboxes, sources and pixels survive");
        assertEquals(List.of(HURT, SWING), back.getHitboxes(1));
        assertEquals(List.of(), back.getHitboxes(2));
        assertEquals(List.of("Roof"), back.getLayerSource(roof).shapes());
        assertNull(back.getLayerSource(0));
    }

    @Test
    void shouldKeepHitboxesWithTheirFramesAndSourcesWithTheirLayers() {
        SpriteDocument doc = character();
        doc.insertFrame(0, 1);
        assertEquals(List.of(HURT, SWING), doc.getHitboxes(0), "a copied frame copies its hitboxes");
        assertEquals(List.of(HURT, SWING), doc.getHitboxes(2), "the original moved one along");
        doc.moveFrame(0, 3);
        assertEquals(List.of(HURT), doc.getHitboxes(0));
        assertEquals(List.of(HURT, SWING), doc.getHitboxes(3));
        doc.removeFrame(1);
        assertEquals(3, doc.getFrameCount());
        assertEquals(List.of(), doc.getHitboxes(1));

        int top = doc.addLayer("Top");
        doc.setLayerSource(top, new SpriteLayerSource("a.vector", List.of()));
        doc.insertLayer(0, "Bottom");
        assertEquals("a.vector", doc.getLayerSource(top + 1).vector(), "a source follows its layer");
        doc.moveLayer(top + 1, 0);
        assertEquals("a.vector", doc.getLayerSource(0).vector());
        SpriteDocument copy = doc.copy();
        assertTrue(copy.contentEquals(doc));
        doc.setHitboxes(0, List.of());
        assertFalse(copy.contentEquals(doc), "hitboxes count for equality");
        doc.removeLayer(0);
        assertFalse(doc.hasLayerSources());
    }

    @Test
    void shouldRefuseBrokenHitboxesSourcesAndUnknownVersions() {
        SpriteDocument doc = character();
        assertThrows(IllegalArgumentException.class, () -> doc.setHitboxes(0, List.of(HURT, HURT)), "names unique per frame");
        assertThrows(IllegalArgumentException.class, () -> new SpriteHitbox("x", 0, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new SpriteHitbox(" ", 0, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new SpriteLayerSource("../outside.vector", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new SpriteLayerSource("C:\\abs.vector", List.of()));
        assertThrows(IndexOutOfBoundsException.class, () -> doc.getHitboxes(9));
        assertThrows(IOException.class, () -> SpriteFile.read("{\"kind\": \"sprite\", \"schemaVersion\": 3, \"width\": 1, "
            + "\"height\": 1}"));
    }

    @Test
    void shouldExportHitboxesAndTheSourceInTheAtlas() throws IOException {
        String sha = "a".repeat(64);
        SpriteSheet.Export sheet = SpriteSheet.export(character(), 0, 0, "hero.png", new SpriteSheet.SourceRecord("sprites/hero.sprite",
            sha));
        JsonValue atlas = new JsonReader().parse(sheet.atlasJson());
        assertEquals("sprites/hero.sprite", atlas.get("source").getString("path"));
        assertEquals(sha, atlas.get("source").getString("sha256"));
        assertEquals("attack", atlas.get("frames").get(1).get("hitboxes").get(1).getString("name"));
        assertEquals(5, atlas.get("frames").get(1).get("hitboxes").get(1).getInt("w"));
        assertNull(atlas.get("frames").get(2).get("hitboxes"), "a frame without hitboxes lists none");
        assertNull(new JsonReader().parse(SpriteSheet.export(character(), 0, 0, "hero.png").atlasJson()).get("source"));
        assertThrows(IllegalArgumentException.class, () -> new SpriteSheet.SourceRecord("a.sprite", "not-a-hash"));
    }
}
