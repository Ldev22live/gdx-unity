package io.github.ldev22live.gdxunity.engine.visualscript;

import io.github.ldev22live.gdxunity.engine.scene.physics.Collision2D;
import io.github.ldev22live.gdxunity.engine.scene.physics.PhysicsSystem2D;

/**
 * Sends 2D collisions and sensor overlaps to the bound graphs of the entity involved, as Event Collision and Event
 * Trigger Enter (TD-029). Shared by editor play mode and the game runtime so both react to physics the same way.
 */
public final class GraphCollisionForwarder implements PhysicsSystem2D.CollisionListener {

    @Override
    public void onCollision(Collision2D event) {
        if (event.entity() == null) {
            return;
        }
        for (GraphScriptComponent component : event.entity().getComponents(GraphScriptComponent.class)) {
            GraphInstance instance = component.getInstance();
            if (instance == null) {
                continue;
            }
            switch (event.type()) {
                case COLLISION_ENTER -> instance.collision(event.otherEntity());
                case SENSOR_ENTER -> instance.triggerEnter(event.otherEntity());
                default -> {
                    // Exit events have no graph node yet
                }
            }
        }
    }
}
