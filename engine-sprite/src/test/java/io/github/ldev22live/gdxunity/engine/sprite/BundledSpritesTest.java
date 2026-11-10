package io.github.ldev22live.gdxunity.engine.sprite;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sprites the project ships in {@code assets/sprites} (used by the sample scene {@code sprite-animation-2d}). They are drawn here in code, so the
 * file is reproducible and cannot drift from this definition; run with {@code GDXUNITY_WRITE_SAMPLES=1} to write them again.
 */
class BundledSpritesTest {
    private static final Path HERO = Path.of("..", "assets", "sprites", "hero.sprite");
    private static final int SIZE = 16;
    private static final int OUTLINE = 0x1D2B53FF;
    private static final int SKIN = 0xFFCCAAFF;
    private static final int SHIRT = 0x29ADFFFF;
    private static final int PANTS = 0x7E2553FF;
    private static final int EYE = 0x000000FF;
    private static final int[] PALETTE = {OUTLINE, SKIN, SHIRT, PANTS, EYE, 0xFFF1E8FF};

    private static void rect(SpriteDocument doc, int frame, int x0, int y0, int x1, int y1, int color) {
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                doc.setPixel(0, frame, x, y, color);
            }
        }
    }

    /** A little person: head, body, and legs whose position depends on {@code legPhase} (0 to 3), bobbing by {@code bob} pixels. */
    private static void person(SpriteDocument doc, int frame, int bob, int legPhase) {
        int top = 1 + bob;
        rect(doc, frame, 5, top, 10, top + 5, OUTLINE);
        rect(doc, frame, 6, top + 1, 9, top + 4, SKIN);
        doc.setPixel(0, frame, 7, top + 2, EYE);
        doc.setPixel(0, frame, 9, top + 2, EYE);
        rect(doc, frame, 5, top + 6, 10, top + 10, OUTLINE);
        rect(doc, frame, 6, top + 6, 9, top + 9, SHIRT);
        int[][] left = {{6, 0}, {7, -1}, {6, 0}, {5, 0}};
        int[][] right = {{9, 0}, {8, 0}, {9, 0}, {10, -1}};
        int footY = 14;
        for (int[] leg : new int[][] {left[legPhase], right[legPhase]}) {
            int x = leg[0];
            int lift = leg[1] == 0 ? 0 : 2;
            rect(doc, frame, x - 1, top + 11, x, footY - lift, PANTS);
            rect(doc, frame, x - 1, footY - lift, x, footY - lift, OUTLINE);
        }
    }

    private static final Path TILES = Path.of("..", "assets", "sprites", "tiles.sprite");
    private static final int TILE = 16;
    private static final int EDGE = 2;
    private static final int GROUND = 0x7E5A3CFF;
    private static final int GROUND_EDGE = 0x3E2A1CFF;
    private static final int GRASS = 0x3CB44BFF;

    /**
     * The tile set of the sample level: tiles 1 to 16 are an auto-tile set in neighbour-mask order (north 1, east 2, south 4, west 8): ground with a dark
     * edge on each side that has no neighbour and grass along an open top. Tile 17 is a plain background brick.
     */
    static SpriteDocument tiles() {
        SpriteDocument doc = new SpriteDocument(TILE, TILE);
        for (int i = 1; i < 17; i++) {
            doc.insertFrame(i, -1);
        }
        for (int mask = 0; mask < 16; mask++) {
            rect(doc, mask, 0, 0, TILE - 1, TILE - 1, GROUND);
            if ((mask & 1) == 0) {
                rect(doc, mask, 0, 0, TILE - 1, EDGE - 1, GRASS);
                rect(doc, mask, 0, EDGE, TILE - 1, EDGE, GROUND_EDGE);
            }
            if ((mask & 2) == 0) {
                rect(doc, mask, TILE - EDGE, 0, TILE - 1, TILE - 1, GROUND_EDGE);
            }
            if ((mask & 4) == 0) {
                rect(doc, mask, 0, TILE - EDGE, TILE - 1, TILE - 1, GROUND_EDGE);
            }
            if ((mask & 8) == 0) {
                rect(doc, mask, 0, 0, EDGE - 1, TILE - 1, GROUND_EDGE);
            }
        }
        rect(doc, 16, 0, 0, TILE - 1, TILE - 1, 0x2A3550FF);
        rect(doc, 16, 0, 7, TILE - 1, 8, 0x1D2B53FF);
        rect(doc, 16, 7, 0, 8, 7, 0x1D2B53FF);
        doc.setPalette(new int[] {GROUND, GROUND_EDGE, GRASS, 0x2A3550FF, 0x1D2B53FF});
        return doc;
    }

    @Test
    void shouldMatchTheBundledTileSetFile() throws IOException {
        String built = SpriteFile.write(tiles());
        if ("1".equals(System.getenv("GDXUNITY_WRITE_SAMPLES"))) {
            Files.createDirectories(TILES.getParent());
            Files.writeString(TILES, built, StandardCharsets.UTF_8);
        }

        assertTrue(Files.isRegularFile(TILES), "missing " + TILES + "; run with GDXUNITY_WRITE_SAMPLES=1");
        assertTrue(tiles().contentEquals(SpriteFile.read(Files.readString(TILES, StandardCharsets.UTF_8))), "tiles.sprite is out of date");
    }

    @Test
    void shouldDrawAnOpenEdgeOnlyOnSidesWithoutANeighbour() {
        SpriteDocument tiles = tiles();

        // Mask 15 has all four neighbours: plain ground everywhere. Mask 0 has none: grass on top, dark edges on the sides
        assertEquals(GROUND, tiles.getPixel(0, 15, 0, 0));
        assertEquals(GROUND, tiles.getPixel(0, 15, TILE - 1, TILE - 1));
        assertEquals(GRASS, tiles.getPixel(0, 0, 8, 0));
        assertEquals(GROUND_EDGE, tiles.getPixel(0, 0, TILE - 1, 8));
        assertEquals(17, tiles.getFrameCount());
    }

    private static final Path COIN = Path.of("..", "assets", "sprites", "coin.sprite");

    /** A spinning coin: four frames whose width goes full, narrow, edge-on, narrow (tag "spin"). */
    static SpriteDocument coin() {
        SpriteDocument doc = new SpriteDocument(TILE, TILE);
        for (int i = 1; i < 4; i++) {
            doc.insertFrame(i, -1);
        }
        int[] halfWidths = {6, 4, 1, 4};
        for (int f = 0; f < 4; f++) {
            int w = halfWidths[f];
            rect(doc, f, 8 - w - 1, 2, 8 + w, 13, 0x8B6B00FF);
            rect(doc, f, 8 - w, 3, 8 + w - 1, 12, 0xFFEC27FF);
            if (w > 2) {
                rect(doc, f, 8 - w + 1, 4, 8 - w + 2, 8, 0xFFF1E8FF);
            }
        }
        doc.setFrameDuration(0, 140);
        doc.setFrameDuration(1, 110);
        doc.setFrameDuration(2, 90);
        doc.setFrameDuration(3, 110);
        doc.setTags(List.of(new SpriteTag("spin", 0, 3, SpriteTag.Direction.FORWARD)));
        doc.setPalette(new int[] {0xFFEC27FF, 0x8B6B00FF, 0xFFF1E8FF});
        return doc;
    }

    @Test
    void shouldMatchTheBundledCoinFile() throws IOException {
        String built = SpriteFile.write(coin());
        if ("1".equals(System.getenv("GDXUNITY_WRITE_SAMPLES"))) {
            Files.createDirectories(COIN.getParent());
            Files.writeString(COIN, built, StandardCharsets.UTF_8);
        }

        assertTrue(Files.isRegularFile(COIN), "missing " + COIN + "; run with GDXUNITY_WRITE_SAMPLES=1");
        assertTrue(coin().contentEquals(SpriteFile.read(Files.readString(COIN, StandardCharsets.UTF_8))), "coin.sprite is out of date");
        assertEquals(4, coin().getFrameCount());
        assertTrue(!java.util.Arrays.equals(coin().flatten(0), coin().flatten(2)), "the coin turns");
    }

    static SpriteDocument hero() {
        SpriteDocument doc = new SpriteDocument(SIZE, SIZE);
        for (int i = 1; i < 6; i++) {
            doc.insertFrame(i, -1);
        }
        // Frames 0-1: idle (breathing), frames 2-5: walk cycle
        person(doc, 0, 0, 0);
        person(doc, 1, 1, 0);
        for (int i = 0; i < 4; i++) {
            person(doc, 2 + i, i % 2, i);
        }
        doc.setFrameDuration(0, 400);
        doc.setFrameDuration(1, 400);
        for (int i = 2; i < 6; i++) {
            doc.setFrameDuration(i, 120);
        }
        doc.setTags(List.of(new SpriteTag("idle", 0, 1, SpriteTag.Direction.FORWARD), new SpriteTag("walk", 2, 5, SpriteTag.Direction.FORWARD)));
        doc.setPalette(PALETTE);
        return doc;
    }

    @Test
    void shouldMatchTheBundledHeroFile() throws IOException {
        String built = SpriteFile.write(hero());
        if ("1".equals(System.getenv("GDXUNITY_WRITE_SAMPLES"))) {
            Files.createDirectories(HERO.getParent());
            Files.writeString(HERO, built, StandardCharsets.UTF_8);
        }

        assertTrue(Files.isRegularFile(HERO), "missing " + HERO + "; run with GDXUNITY_WRITE_SAMPLES=1");
        SpriteDocument onDisk = SpriteFile.read(Files.readString(HERO, StandardCharsets.UTF_8));
        assertTrue(hero().contentEquals(onDisk), "hero.sprite is out of date");
    }

    @Test
    void shouldGiveTheHeroAnIdleAndAWalkTagThatAnimate() {
        SpriteDocument hero = hero();

        assertEquals(List.of("idle", "walk"), hero.getTags().stream().map(SpriteTag::name).toList());
        assertEquals(2, SpriteAnimation.frameAt(hero, hero.getTags().get(1), 0, true));
        assertEquals(3, SpriteAnimation.frameAt(hero, hero.getTags().get(1), 130, true));
        assertTrue(!java.util.Arrays.equals(hero.flatten(2), hero.flatten(3)), "walk frames differ");
    }
}
