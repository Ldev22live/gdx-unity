package io.github.ldev22live.gdxunity.engine.visualscript;

import com.badlogic.gdx.Input;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Key names of the Key Down and Input Axis nodes (TD-060, milestone C). */
class KeyNamesTest {

    @Test
    void shouldReadSeveralKeysWhateverTheCaseAndSpacing() {
        assertEquals(List.of(Input.Keys.A, Input.Keys.LEFT, Input.Keys.SHIFT_LEFT, Input.Keys.SPACE), KeyNames.parse("a, Left ,SHIFT_LEFT,space"));
    }

    @Test
    void shouldNameTheKeyItDoesNotKnow() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> KeyNames.parse("A,FIRE"));

        assertTrue(error.getMessage().contains("'FIRE'"), error.getMessage());
    }

    @Test
    void shouldNotTreatNonKeyConstantsAsKeys() {
        assertThrows(IllegalArgumentException.class, () -> KeyNames.parse("ANY_KEY"));
    }

    @Test
    void shouldWriteConstantsForGeneratedCode() {
        assertEquals(List.of("Input.Keys.W", "Input.Keys.UP"), KeyNames.constants("w, up"));
    }

    @Test
    void shouldNameTheKeysAsTheBrowserDoes() {
        assertEquals(List.of("KeyW", "ArrowUp", "Space", "ShiftLeft", "Digit5", "F12"), KeyNames.browserCodes("w, up,SPACE,shift_left,num_5,f12"));
    }

    @Test
    void shouldSayWhichKeyHasNoBrowserEquivalent() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> KeyNames.browserCodes("A,DPAD_CENTER"));

        assertTrue(error.getMessage().contains("DPAD_CENTER") && error.getMessage().contains("web player"), error.getMessage());
        assertThrows(IllegalArgumentException.class, () -> KeyNames.browserCodes("FIRE"));
    }
}
