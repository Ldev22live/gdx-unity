package io.github.ldev22live.gdxunity.engine.sprite;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpriteAnimationTest {
    private static SpriteDocument frames(int count) {
        SpriteDocument doc = new SpriteDocument(1, 1);
        for (int i = 1; i < count; i++) {
            doc.insertFrame(i, -1);
        }
        return doc;
    }

    @Test
    void shouldPlayForwardReverseAndPingPongSequences() {
        SpriteDocument doc = frames(4);

        assertEquals(List.of(1, 2, 3), SpriteAnimation.sequence(doc, new SpriteTag("a", 1, 3, SpriteTag.Direction.FORWARD)));
        assertEquals(List.of(3, 2, 1), SpriteAnimation.sequence(doc, new SpriteTag("a", 1, 3, SpriteTag.Direction.REVERSE)));
        assertEquals(List.of(0, 1, 2, 3, 2, 1), SpriteAnimation.sequence(doc, new SpriteTag("a", 0, 3, SpriteTag.Direction.PING_PONG)));
        assertEquals(List.of(0, 1, 2, 3), SpriteAnimation.sequence(doc, null));
    }

    @Test
    void shouldFindTheFrameByTimeUsingEachFramesDurationAndLooping() {
        SpriteDocument doc = frames(3);
        doc.setFrameDuration(0, 100);
        doc.setFrameDuration(1, 300);
        doc.setFrameDuration(2, 100);

        assertEquals(0, SpriteAnimation.frameAt(doc, null, 99, true));
        assertEquals(1, SpriteAnimation.frameAt(doc, null, 100, true));
        assertEquals(1, SpriteAnimation.frameAt(doc, null, 399, true));
        assertEquals(2, SpriteAnimation.frameAt(doc, null, 400, true));
        assertEquals(0, SpriteAnimation.frameAt(doc, null, 500, true), "wraps around");
        assertEquals(2, SpriteAnimation.frameAt(doc, null, 9999, false), "stays on the last frame");
    }
}
