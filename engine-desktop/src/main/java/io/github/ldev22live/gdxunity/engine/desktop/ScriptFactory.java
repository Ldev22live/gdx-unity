package io.github.ldev22live.gdxunity.engine.desktop;

import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent;

import java.io.Closeable;

/**
 * Creates the game's script instances by class name (TD-035, TD-036). The desktop runtime loads compiled classes
 * from a folder or jar ({@link GameScripts}); the web runtime, which cannot load classes at run time, uses a
 * registry generated at build time ({@link ScriptRegistry}). {@link ScriptBinder} binds a scene's scripts through
 * either.
 */
public interface ScriptFactory extends Closeable {
    /**
     * Why no script can be created at all, such as a missing folder of compiled scripts, or {@code null} when
     * scripts are available. The message is shown to the user, so it should say how to fix the problem.
     *
     * @return the reason, or {@code null}
     */
    String unavailableReason();

    /**
     * Creates a new instance of a script class.
     *
     * @param className fully qualified name of a concrete {@link ScriptComponent} subclass
     * @return a new, unbound instance
     * @throws ReflectiveOperationException if the class is unknown or cannot be instantiated
     * @throws IllegalArgumentException     if the class is not a concrete {@link ScriptComponent}
     */
    ScriptComponent create(String className) throws ReflectiveOperationException;
}
