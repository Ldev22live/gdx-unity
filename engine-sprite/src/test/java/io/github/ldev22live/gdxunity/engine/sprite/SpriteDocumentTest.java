package io.github.ldev22live.gdxunity.engine.sprite;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpriteDocumentTest {
    private static final int RED = 0xFF0000FF;
    private static final int BLUE = 0x0000FFFF;

    private static SpriteDocument sample() {
        SpriteDocument doc = new SpriteDocument(4, 3);
        doc.insertFrame(1, 0);
        doc.insertFrame(2, -1);
        doc.addLayer("Top");
        doc.setPixel(0, 0, 1, 1, RED);
        doc.setPixel(1, 2, 3, 2, BLUE);
        doc.setFrameDuration(1, 250);
        doc.setTags(List.of(new SpriteTag("walk", 0, 1, SpriteTag.Direction.PING_PONG)));
        doc.setSlices(List.of(new SpriteSlice("panel", 0, 0, 4, 3, 1, 1, 1, 1, 2, 1)));
        doc.setPalette(new int[] {RED, BLUE, 0x00000000});
        doc.setLayer(1, doc.getLayer(1).withOpacity(128).withName("Top"));
        return doc;
    }

    @Test
    void shouldStartWithOneEmptyLayerAndFrame() {
        SpriteDocument doc = new SpriteDocument(8, 8);

        assertEquals(1, doc.getLayerCount());
        assertEquals(1, doc.getFrameCount());
        assertTrue(doc.isCelEmpty(0, 0));
        assertEquals(SpriteDocument.TRANSPARENT, doc.getPixel(0, 0, 3, 3));
    }

    @Test
    void shouldRejectPixelsOutsideTheCanvasAndBadSizes() {
        SpriteDocument doc = new SpriteDocument(4, 4);

        assertThrows(IndexOutOfBoundsException.class, () -> doc.setPixel(0, 0, 4, 0, RED));
        assertThrows(IllegalArgumentException.class, () -> new SpriteDocument(0, 5));
        assertThrows(IllegalArgumentException.class, () -> new SpriteDocument(SpriteDocument.MAX_SIZE + 1, 5));
    }

    @Test
    void shouldCopyTheSourceFrameWhenInsertingBeforeIt() {
        SpriteDocument doc = new SpriteDocument(2, 2);
        doc.setPixel(0, 0, 0, 0, RED);

        doc.insertFrame(0, 0);

        assertEquals(RED, doc.getPixel(0, 0, 0, 0));
        assertEquals(RED, doc.getPixel(0, 1, 0, 0));
    }

    @Test
    void shouldShiftTagsWhenAFrameIsInsertedAndShrinkThemWhenOneIsRemoved() {
        SpriteDocument doc = new SpriteDocument(2, 2);
        doc.insertFrame(1, -1);
        doc.insertFrame(2, -1);
        doc.setTags(List.of(new SpriteTag("a", 1, 2, SpriteTag.Direction.FORWARD), new SpriteTag("b", 0, 0, SpriteTag.Direction.FORWARD)));

        doc.insertFrame(0, -1);
        assertEquals(2, doc.getTags().get(0).from());
        assertEquals(3, doc.getTags().get(0).to());
        assertEquals(1, doc.getTags().get(1).to());

        doc.removeFrame(1);
        assertEquals(1, doc.getTags().size(), "the tag that had only the removed frame is gone");
        assertEquals(1, doc.getTags().get(0).from());
        assertEquals(2, doc.getTags().get(0).to());
    }

    @Test
    void shouldNotRemoveTheLastLayerOrFrame() {
        SpriteDocument doc = new SpriteDocument(2, 2);

        assertThrows(IllegalStateException.class, () -> doc.removeLayer(0));
        assertThrows(IllegalStateException.class, () -> doc.removeFrame(0));
    }

    @Test
    void shouldMoveLayersTogetherWithTheirPixels() {
        SpriteDocument doc = new SpriteDocument(2, 2);
        doc.addLayer("B");
        doc.setPixel(1, 0, 0, 0, BLUE);

        doc.moveLayer(1, 0);

        assertEquals("B", doc.getLayer(0).name());
        assertEquals(BLUE, doc.getPixel(0, 0, 0, 0));
        assertEquals(SpriteDocument.TRANSPARENT, doc.getPixel(1, 0, 0, 0));
    }

    @Test
    void shouldFlattenOpaqueLayersTopOverBottomAndSkipHiddenOnes() {
        SpriteDocument doc = new SpriteDocument(2, 1);
        doc.addLayer("Top");
        doc.setPixel(0, 0, 0, 0, RED);
        doc.setPixel(1, 0, 0, 0, BLUE);
        doc.setPixel(0, 0, 1, 0, RED);

        assertEquals(BLUE, doc.flatten(0)[0]);
        assertEquals(RED, doc.flatten(0)[1]);

        doc.setLayer(1, doc.getLayer(1).withVisible(false));
        assertEquals(RED, doc.flatten(0)[0]);
    }

    @Test
    void shouldBlendHalfTransparentPixelsAndLayerOpacity() {
        int half = over(0x00000000, 0xFFFFFF80, 255);
        assertEquals(0xFFFFFF80, half, "over transparent keeps the source");

        int onWhite = over(0xFFFFFFFF, 0x000000FF, 128);
        assertEquals(0xFF, onWhite & 0xFF);
        int red = onWhite >>> 24;
        assertTrue(red >= 126 && red <= 128, "black at half opacity over white is mid grey but was " + red);
    }

    private static int over(int dst, int src, int opacity) {
        return SpriteDocument.over(dst, src, opacity);
    }

    @Test
    void shouldResizeTheCanvasKeepingTheTopLeftAndDroppingSlicesThatNoLongerFit() {
        SpriteDocument doc = sample();

        doc.resizeCanvas(2, 2);

        assertEquals(RED, doc.getPixel(0, 0, 1, 1));
        assertTrue(doc.getSlices().isEmpty());
        assertTrue(doc.isCelEmpty(1, 2), "the blue pixel at 3,2 was cut away so the cel is empty");
    }

    @Test
    void shouldRoundTripThroughTheSpriteFile() throws IOException {
        SpriteDocument doc = sample();

        SpriteDocument back = SpriteFile.read(SpriteFile.write(doc));

        assertTrue(doc.contentEquals(back));
        assertEquals(250, back.getFrameDuration(1));
        assertEquals(2, back.getLayerCount());
        assertEquals(3, back.getFrameCount());
        assertArrayEquals(new int[] {RED, BLUE, 0}, back.getPalette());
    }

    @Test
    void shouldRefuseFilesThatAreNotSpritesOrHaveAnotherVersion() {
        assertThrows(IOException.class, () -> SpriteFile.read("{\"kind\":\"graph\"}"));
        assertThrows(IOException.class, () -> SpriteFile.read("{\"kind\":\"sprite\",\"schemaVersion\":2}"));
        assertThrows(IOException.class, () -> SpriteFile.read("not json {"));
        assertThrows(IOException.class, () -> SpriteFile.read("{\"kind\":\"sprite\",\"schemaVersion\":1,\"width\":2,\"height\":2,\"layers\":[],\"frames\":[]}"));
    }

    @Test
    void shouldCopyDeeplyAndCompareContent() {
        SpriteDocument doc = sample();
        SpriteDocument copy = doc.copy();

        assertTrue(doc.contentEquals(copy));
        copy.setPixel(0, 0, 0, 0, BLUE);

        assertFalse(doc.contentEquals(copy));
        assertEquals(SpriteDocument.TRANSPARENT, doc.getPixel(0, 0, 0, 0));
    }

    @Test
    void shouldExportASheetWithAnAtlasAndImportItBack() throws IOException {
        SpriteDocument doc = new SpriteDocument(2, 2);
        doc.insertFrame(1, -1);
        doc.setPixel(0, 0, 0, 0, RED);
        doc.setPixel(0, 1, 1, 1, BLUE);

        SpriteSheet.Export sheet = SpriteSheet.export(doc, 0, 0, "hero.png");
        PixelPng.Image image = PixelPng.decode(sheet.png());

        assertEquals(4, image.width());
        assertEquals(2, image.height());
        assertEquals(RED, image.pixels()[0]);
        assertEquals(BLUE, image.pixels()[1 * 4 + 3]);
        assertTrue(sheet.atlasJson().contains("\"image\":\"hero.png\""), sheet.atlasJson());

        SpriteDocument imported = SpriteSheet.importSheet(image, 2, 2, 0);
        assertEquals(2, imported.getFrameCount());
        assertEquals(RED, imported.getPixel(0, 0, 0, 0));
        assertEquals(BLUE, imported.getPixel(0, 1, 1, 1));
    }

    @Test
    void shouldLeaveGapsBetweenSheetCellsWhenPaddingIsGiven() throws IOException {
        SpriteDocument doc = new SpriteDocument(2, 2);
        doc.insertFrame(1, -1);
        doc.setPixel(0, 1, 0, 0, RED);

        SpriteSheet.Export sheet = SpriteSheet.export(doc, 0, 1, "x.png");

        assertEquals(5, sheet.width());
        assertEquals(RED, PixelPng.decode(sheet.png()).pixels()[3]);
    }

    @Test
    void shouldDropEmptyTrailingCellsOnImportButKeepOneFrame() {
        PixelPng.Image blank = new PixelPng.Image(4, 2, new int[8]);

        SpriteDocument doc = SpriteSheet.importSheet(blank, 2, 2, 0);

        assertEquals(1, doc.getFrameCount());
    }
}
