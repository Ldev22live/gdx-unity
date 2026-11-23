package io.github.ldev22live.gdxunity.engine.scene.scripting;

import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsSystem3D;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Calls the 3D contact callbacks of {@link ScriptComponent} on both entities of a contact (TD-048). {@link PhysicsSystem3D}
 * registers one for itself, so the editor's Play button and the game runtime deliver contacts the same way.
 * <p>
 * Scripts bound with an Entity Script Binding component (how scene files attach them) and scripts attached directly are
 * both called. Only enabled scripts on entities that are active are called. A script that throws stops the delivery of this frame's
 * remaining contacts and the exception reaches whoever called {@link PhysicsSystem3D#update}, as one thrown from
 * {@code onUpdate} would.
 */
public final class ScriptContactForwarder implements PhysicsSystem3D.ContactListener {

    @Override
    public void onContact(PhysicsSystem3D.Contact contact) {
        Objects.requireNonNull(contact, "contact cannot be null");
        deliver(contact.entityA(), contact.entityB(), contact.kind());
        deliver(contact.entityB(), contact.entityA(), contact.kind());
    }

    private static void deliver(Entity entity, Entity other, PhysicsSystem3D.ContactKind kind) {
        // A callback on the first entity may have destroyed or deactivated either one
        if (!entity.isActiveInHierarchy()) {
            return;
        }
        // Snapshot: a script may add or remove components. A script is either a component of the entity itself or, as in
        // every scene file, the instance an Entity Script Binding component holds (it runs while the binding is enabled)
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
            switch (kind) {
                case COLLISION_ENTER -> script.onCollisionEnter3D(other);
                case COLLISION_EXIT -> script.onCollisionExit3D(other);
                case TRIGGER_ENTER -> script.onTriggerEnter3D(other);
                case TRIGGER_EXIT -> script.onTriggerExit3D(other);
            }
        }
    }
}
