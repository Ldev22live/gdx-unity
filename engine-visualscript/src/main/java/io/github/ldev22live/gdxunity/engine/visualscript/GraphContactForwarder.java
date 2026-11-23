package io.github.ldev22live.gdxunity.engine.visualscript;

import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsSystem3D;

/**
 * Sends 3D contacts to the bound graphs of both entities involved, as Event Collision and Event Trigger Enter (TD-048),
 * like {@link GraphCollisionForwarder} does for 2D. Shared by editor play mode and the game runtime.
 */
public final class GraphContactForwarder implements PhysicsSystem3D.ContactListener {

    @Override
    public void onContact(PhysicsSystem3D.Contact contact) {
        deliver(contact.entityA(), contact.entityB(), contact.kind());
        deliver(contact.entityB(), contact.entityA(), contact.kind());
    }

    private static void deliver(Entity entity, Entity other, PhysicsSystem3D.ContactKind kind) {
        if (!entity.isActiveInHierarchy()) {
            return;
        }
        for (GraphScriptComponent component : entity.getComponents(GraphScriptComponent.class)) {
            GraphInstance instance = component.getInstance();
            if (instance == null) {
                continue;
            }
            switch (kind) {
                case COLLISION_ENTER -> instance.collision(other);
                case TRIGGER_ENTER -> instance.triggerEnter(other);
                default -> {
                    // Exit events have no graph node yet
                }
            }
        }
    }
}
