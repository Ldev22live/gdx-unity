package io.github.ldev22live.gdxunity.engine.scene.play;

import com.badlogic.gdx.Input.Keys;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Keys for the profile's actions, read from a fake source as headless tests will (roadmap M3b.2, TD-088). */
class InputMappingTest {
    @Test
    void shouldMapWasdArrowsSpaceAndQE() {
        InputMapping keys = InputMapping.defaults();

        assertArrayEquals(new int[] {Keys.A, Keys.LEFT}, keys.keysOf(InputAction.MOVE_LEFT));
        assertArrayEquals(new int[] {Keys.W, Keys.UP}, keys.keysOf(InputAction.MOVE_FORWARD));
        assertArrayEquals(new int[] {Keys.SPACE}, keys.keysOf(InputAction.JUMP));
        assertArrayEquals(new int[] {Keys.E}, keys.keysOf(InputAction.TURN_RIGHT));
        assertArrayEquals(new int[] {Keys.F, Keys.CONTROL_LEFT}, keys.keysOf(InputAction.FIRE), "roadmap M3b.7: the shot");
    }

    @Test
    void shouldReadAnyKeyOfAnActionFromTheSource() {
        Set<Integer> held = Set.of(Keys.RIGHT);
        InputSource fake = held::contains;

        assertTrue(InputMapping.defaults().isActive(InputAction.MOVE_RIGHT, fake), "the arrow counts as well as D");
        assertFalse(InputMapping.defaults().isActive(InputAction.JUMP, fake));
    }

    @Test
    void shouldChangeOneActionInACopyAndRefuseAnActionWithoutKeys() {
        InputMapping changed = InputMapping.defaults().with(InputAction.JUMP, Keys.W);

        assertArrayEquals(new int[] {Keys.W}, changed.keysOf(InputAction.JUMP));
        assertArrayEquals(new int[] {Keys.SPACE}, InputMapping.defaults().keysOf(InputAction.JUMP), "the defaults are unchanged");
        assertNotEquals(InputMapping.defaults(), changed);
        assertEquals(InputMapping.defaults(), InputMapping.defaults().with(InputAction.JUMP, Keys.SPACE));
        assertThrows(IllegalArgumentException.class, () -> InputMapping.defaults().with(InputAction.JUMP));
    }

    @Test
    void shouldSayThereIsNoKeyboardOutsideARunningApplication() {
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> InputSource.keyboard().isPressed(Keys.SPACE));

        assertTrue(error.getMessage().contains("No keyboard"), error.getMessage());
    }
}
