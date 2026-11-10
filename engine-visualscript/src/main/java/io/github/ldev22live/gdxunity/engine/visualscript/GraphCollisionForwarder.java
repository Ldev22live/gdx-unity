package io.github.ldev22live.gdxunity.engine.visualscript;

import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.physics.Collision2D;
import io.github.ldev22live.gdxunity.engine.scene.physics.PhysicsSystem2D;

/**
 * Sends 2D collisions and sensor overlaps to the bound graphs of the entity involved, as Event Collision and Event
 * Trigger Enter (TD-029). Shared by editor play mode and the game runtime so both react to physics the same way.
 */
public final class GraphCollisionForwarder implements PhysicsSystem2D.CollisionListener {

    /** The 2D system reports one event per contact, with the first entity in {@code entity()}: both entities are told, as in 3D. */
    @Override
    public void onCollision(Collision2D event) {
        if (event.entity() == null || event.otherEntity() == null) {
            return;
        }
        deliver(event.entity(), event.otherEntity(), event.type());
        deliver(event.otherEntity(), event.entity(), event.type());
    }

    private static void deliver(Entity entity, Entity other, Collision2D.Type type) {
        if (!entity.isActiveInHierarchy()) {
            return;
        }
        for (GraphScriptComponent component : entity.getComponents(GraphScriptComponent.class)) {
            GraphInstance instance = component.getInstance();
            if (instance == null) {
                continue;
            }
            switch (type) {
                case COLLISION_ENTER -> instance.collision(other);
                case SENSOR_ENTER -> instance.triggerEnter(other);
                default -> {
                    // Exit events have no graph node yet
                }
            }
        }
    }
}
