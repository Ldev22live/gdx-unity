package io.github.ldev22live.gdxunity.engine.sprite;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpriteAnimationStateTest {
    private static SpriteDocument sprite() {
        SpriteDocument doc = new SpriteDocument(1, 1);
        for (int i = 1; i < 5; i++) {
            doc.insertFrame(i, -1);
        }
        for (int i = 0; i < 5; i++) {
            doc.setFrameDuration(i, 100);
        }
        doc.setTags(List.of(new SpriteTag("idle", 0, 1, SpriteTag.Direction.FORWARD), new SpriteTag("walk", 2, 4, SpriteTag.Direction.FORWARD)));
        return doc;
    }

    @Test
    void shouldAdvanceThroughFramesByTimeAtTheAuthoredSpeed() {
        SpriteAnimationState state = new SpriteAnimationState(sprite());

        assertEquals(0, state.getFrame());
        state.advance(0.25f);
        assertEquals(2, state.getFrame());
    }

    @Test
    void shouldPlayATagFromItsFirstFrameAndKeepPlayingItWhenAskedAgain() {
        SpriteAnimationState state = new SpriteAnimationState(sprite());
        state.play("walk", false);
        state.advance(0.15f);

        assertEquals(3, state.getFrame());
        state.play("walk", false);
        assertEquals(3, state.getFrame(), "calling play every frame does not restart it");
        state.play("walk", true);
        assertEquals(2, state.getFrame());
        state.play("idle", false);
        assertEquals(0, state.getFrame(), "another tag starts from its beginning");
    }

    @Test
    void shouldRefuseAnUnknownTagAndNameTheKnownOnes() {
        SpriteAnimationState state = new SpriteAnimationState(sprite());

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> state.play("run", false));

        assertTrue(e.getMessage().contains("idle, walk"), e.getMessage());
    }

    @Test
    void shouldScaleTimeBySpeedAndHoldAtZeroSpeed() {
        SpriteAnimationState state = new SpriteAnimationState(sprite());
        state.setSpeed(2f);
        state.advance(0.1f);
        assertEquals(2, state.getFrame());

        state.setSpeed(0f);
        state.advance(10f);
        assertEquals(2, state.getFrame());
        assertThrows(IllegalArgumentException.class, () -> state.setSpeed(-1f));
    }

    @Test
    void shouldLoopOrStopOnTheLastFrameAndReportFinished() {
        SpriteAnimationState looping = new SpriteAnimationState(sprite());
        looping.play("idle", true);
        looping.advance(0.25f);
        assertEquals(0, looping.getFrame(), "wrapped around");
        assertFalse(looping.isFinished());

        SpriteAnimationState once = new SpriteAnimationState(sprite());
        once.setLoop(false);
        once.play("idle", true);
        once.advance(0.1f);
        assertFalse(once.isFinished());
        once.advance(5f);
        assertEquals(1, once.getFrame());
        assertTrue(once.isFinished());
    }

    @Test
    void shouldNotAdvanceWhilePausedAndGoBackToTheStartOnStop() {
        SpriteAnimationState state = new SpriteAnimationState(sprite());
        state.advance(0.15f);
        state.pause();
        state.advance(1f);
        assertEquals(1, state.getFrame());
        assertFalse(state.isPlaying());

        state.resume();
        state.advance(0.1f);
        assertEquals(2, state.getFrame());
        state.stop();
        assertEquals(0, state.getFrame());
    }
}
