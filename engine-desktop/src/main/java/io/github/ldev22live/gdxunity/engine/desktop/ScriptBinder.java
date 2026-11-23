package io.github.ldev22live.gdxunity.engine.desktop;

import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.scripting.EntityScriptBindingComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Binds and unbinds a scene's script instances through a {@link ScriptFactory}. */
public final class ScriptBinder {
    private ScriptBinder() {
    }

    /**
     * Creates and binds a script instance for every script binding in {@code scene}.
     *
     * @return one message per script that could not be created; empty when all are bound. If the scene needs
     *         scripts and the factory has none ({@link ScriptFactory#unavailableReason()}), that reason is the
     *         only message.
     */
    public static List<String> bind(Scene scene, ScriptFactory factory) {
        Objects.requireNonNull(scene, "scene cannot be null");
        Objects.requireNonNull(factory, "factory cannot be null");
        List<String> problems = new ArrayList<>();
        boolean checkedAvailability = false;
        for (Entity entity : scene.getAllEntities()) {
            EntityScriptBindingComponent binding = entity.getComponent(EntityScriptBindingComponent.class);
            if (binding == null || binding.getScriptClassName() == null || binding.getScriptClassName().isBlank()) {
                continue;
            }
            if (!checkedAvailability) {
                String reason = factory.unavailableReason();
                if (reason != null) {
                    problems.add(reason);
                    return problems;
                }
                checkedAvailability = true;
            }
            try {
                binding.bindInstance(factory.create(binding.getScriptClassName()));
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                problems.add("Script '" + binding.getScriptClassName() + "' on entity '" + entity.getName()
                    + "' could not be created: " + e);
            }
        }
        return problems;
    }

    /** Detaches every bound script instance in {@code scene}. */
    public static void unbind(Scene scene) {
        Objects.requireNonNull(scene, "scene cannot be null");
        for (Entity entity : scene.getAllEntities()) {
            EntityScriptBindingComponent binding = entity.getComponent(EntityScriptBindingComponent.class);
            if (binding != null) {
                binding.unbindInstance();
            }
        }
    }
}
