package io.github.ldev22live.gdxunity.engine.character;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Character settings: ranges, copies and the saved file (TD-053). */
class CharacterSpecTest {

    @Test
    void shouldRoundTripEverySetting() {
        CharacterSpec spec = new CharacterSpec();
        spec.setGender(0.8f);
        spec.setAgeYears(40f);
        spec.setMuscle(0.7f);
        spec.setWeight(0.2f);
        spec.setHeightScale(1.1f);
        spec.setSkinColor(0.3f, 0.4f, 0.5f);
        spec.setSlider("nose/nose-width1", -0.5f);
        spec.setSlider("head/head-round", 1f);

        CharacterSpec read = CharacterSpec.fromJson(spec.toJson());

        assertEquals(spec, read);
        assertEquals(Map.of("head/head-round", 1f, "nose/nose-width1", -0.5f), read.getSliders());
    }

    @Test
    void shouldRefuseValuesOutOfRangeAndFilesOfAnotherFormat() {
        CharacterSpec spec = new CharacterSpec();
        assertThrows(IllegalArgumentException.class, () -> spec.setGender(1.5f));
        assertThrows(IllegalArgumentException.class, () -> spec.setAgeYears(0f));
        assertThrows(IllegalArgumentException.class, () -> spec.setHeightScale(2f));
        assertThrows(IllegalArgumentException.class, () -> spec.setSlider("x", 2f));
        assertThrows(IllegalArgumentException.class, () -> spec.setMuscle(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> CharacterSpec.fromJson("{\"format\": 99}"));
        assertThrows(IllegalArgumentException.class, () -> CharacterSpec.fromJson("not json"));
        assertThrows(IllegalArgumentException.class, () -> CharacterSpec.fromJson("{\"format\": 1, \"gender\": 3}"));
    }

    @Test
    void shouldCopyIndependentlyAndDropSlidersSetToZero() {
        CharacterSpec spec = new CharacterSpec();
        spec.setSlider("a/b", 0.5f);
        CharacterSpec copy = spec.copy();
        copy.setSlider("a/b", 0f);
        copy.setGender(0.1f);

        assertEquals(0.5f, spec.getSlider("a/b"));
        assertEquals(0.5f, spec.getGender());
        assertEquals(Map.of(), copy.getSliders());
        assertNotEquals(spec, copy);
    }
}
