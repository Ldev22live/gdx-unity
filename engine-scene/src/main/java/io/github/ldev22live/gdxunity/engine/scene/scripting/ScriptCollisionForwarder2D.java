package io.github.ldev22live.gdxunity.engine.scene.scripting;

import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.physics.Collision2D;
import io.github.ldev22live.gdxunity.engine.scene.physics.PhysicsSystem2D;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Calls the 2D collision callbacks of {@link ScriptComponent} on both entities of a contact (TD-048 follow-up). {@link PhysicsSystem2D}
 * registers one for itself, so the editor's Play button and the game runtime deliver contacts the same way, as the 3D system does with
 * {@link ScriptContactForwarder}. The 2D system reports one event per contact (with the first entity as {@code entity()}); this
 * forwarder tells both entities.
 *
 * <p>Scripts bound with an Entity Script Binding component and scripts attached directly are both called, only while enabled and on an
 * entity that is active. A script that throws is reported on the standard error stream by {@link PhysicsSystem2D}, which keeps
 * delivering the other events.
 */
public final class ScriptCollisionForwarder2D implements PhysicsSystem2D.CollisionListener {

    @Override
    public void onCollision(Collision2D event) {
        Objects.requireNonNull(event, "event cannot be null");
        if (event.entity() == null || event.otherEntity() == null) {
            return;
        }
        deliver(event.entity(), event.otherEntity(), event.type());
        deliver(event.otherEntity(), event.entity(), event.type());
    }

    private static void deliver(Entity entity, Entity other, Collision2D.Type type) {
        // A callback on the first entity may have destroyed or deactivated either one
        if (!entity.isActiveInHierarchy()) {
            return;
        }
        List<ScriptComponent> scripts = new ArrayList<>();
        for (ScriptComponent script : entity.getComponents(ScriptComponent.class)) {
            if (script.isEnabled()) {
                scripts.add(script);
            }
        }
        for (EntityScriptBindingComponent binding : entity.getComponents(EntityScriptBindingComponent.class)) {
            if (binding.isEnabled() && binding.getScriptInstance() != null) {
                scripts.add(binding.getScriptInstance());
            }
        }
        for (ScriptComponent script : scripts) {
            switch (type) {
                case COLLISION_ENTER -> script.onCollisionEnter2D(other);
                case COLLISION_EXIT -> script.onCollisionExit2D(other);
                case SENSOR_ENTER -> script.onTriggerEnter2D(other);
                case SENSOR_EXIT -> script.onTriggerExit2D(other);
            }
        }
    }
}
