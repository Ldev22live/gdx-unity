package io.github.ldev22live.gdxunity.engine.desktop;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScriptRegistryTest {
    private static final String STEP = StepCountingScript.class.getName();

    @Test
    void shouldCreateANewInstanceOfARegisteredScriptEachTime() throws ClassNotFoundException {
        ScriptRegistry registry = new ScriptRegistry().register(STEP, StepCountingScript::new);

        assertInstanceOf(StepCountingScript.class, registry.create(STEP));
        assertNotSame(registry.create(STEP), registry.create(STEP));
        assertNull(registry.unavailableReason());
    }

    @Test
    void shouldNameAnUnknownScriptClass() {
        ClassNotFoundException error = assertThrows(ClassNotFoundException.class,
            () -> new ScriptRegistry().create("game.Missing"));

        assertTrue(error.getMessage().contains("game.Missing"), error.getMessage());
    }

    @Test
    void shouldKeepRegistrationOrderAndRejectDuplicatesAndBlankNames() {
        ScriptRegistry registry = new ScriptRegistry().register("b.B", StepCountingScript::new)
            .register("a.A", StepCountingScript::new);

        assertEquals(List.of("b.B", "a.A"), List.copyOf(registry.getClassNames()));
        IllegalArgumentException duplicate = assertThrows(IllegalArgumentException.class,
            () -> registry.register("a.A", StepCountingScript::new));
        assertTrue(duplicate.getMessage().contains("a.A"), duplicate.getMessage());
        assertThrows(IllegalArgumentException.class, () -> registry.register(" ", StepCountingScript::new));
    }

    @Test
    void shouldFailLoudlyWhenAFactoryReturnsNull() {
        ScriptRegistry registry = new ScriptRegistry().register("x.Null", () -> null);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> registry.create("x.Null"));
        assertTrue(error.getMessage().contains("x.Null"), error.getMessage());
    }
}
