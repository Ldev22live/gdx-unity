package io.github.ldev22live.gdxunity.engine.desktop;

import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Script classes known in advance, each with a constructor reference (TD-036). Used where classes cannot be
 * loaded by name at run time, such as the web runtime, whose build generates the registrations from the
 * project's scripts. Needs no reflection.
 */
public final class ScriptRegistry implements ScriptFactory {
    private final Map<String, Supplier<? extends ScriptComponent>> factories = new LinkedHashMap<>();

    /**
     * Registers a script class.
     *
     * @param className fully qualified class name, as stored in the scene's script bindings
     * @param factory   creates a new instance, usually the class's constructor reference
     * @return this registry, for chaining
     * @throws IllegalArgumentException if {@code className} is blank or already registered
     */
    public ScriptRegistry register(String className, Supplier<? extends ScriptComponent> factory) {
        Objects.requireNonNull(className, "className cannot be null");
        Objects.requireNonNull(factory, "factory cannot be null");
        if (className.isBlank()) {
            throw new IllegalArgumentException("className cannot be blank");
        }
        if (factories.putIfAbsent(className, factory) != null) {
            throw new IllegalArgumentException("Script already registered: " + className);
        }
        return this;
    }

    /** The registered class names, in registration order. */
    public Set<String> getClassNames() {
        return Collections.unmodifiableSet(factories.keySet());
    }

    /** Always available: a scene naming an unregistered script gets a per-script message instead. */
    @Override
    public String unavailableReason() {
        return null;
    }

    /**
     * @throws ClassNotFoundException if no script with that name was registered; the message lists how to fix it
     */
    @Override
    public ScriptComponent create(String className) throws ClassNotFoundException {
        Supplier<? extends ScriptComponent> factory = factories.get(className);
        if (factory == null) {
            throw new ClassNotFoundException(className + " is not in this build's script registry (only scripts "
                + "under the project's scripts folder are built in)");
        }
        ScriptComponent script = factory.get();
        if (script == null) {
            throw new IllegalStateException("The registered factory for " + className + " returned null");
        }
        return script;
    }

    /** Nothing to release. */
    @Override
    public void close() {
    }
}
