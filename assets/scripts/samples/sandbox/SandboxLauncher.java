package samples.sandbox;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.render.components.TextComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsLayers3D;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsSystem3D;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.RigidBody3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent;

import java.util.List;

/**
 * Plays with the 3D physics sandbox through the script API (TD-048): this script sits on the camera and aims along its view.
 * A ray finds what you look at; <b>Space</b> kicks it away with an impulse, <b>F</b> (held) pushes it with a force, <b>E</b>
 * pops everything within a sphere around the aim point, and <b>T</b> stops it with a velocity. The aimed entity's name is
 * shown on the "Help Text" entity.
 */
public class SandboxLauncher extends ScriptComponent {
    /** How far the ray looks, in scene units. */
    static final float RANGE = 6000f;
    /** Speed a kick gives a body, in scene units per second; the impulse is this times the body's mass. */
    static final float KICK_SPEED = 900f;
    /** Push of a held F, in scene units per second squared, times the body's mass. */
    static final float PUSH_ACCELERATION = 1500f;
    /** Bodies within this distance of the aim point are popped by E, in scene units. */
    static final float POP_RADIUS = 260f;
    /** Upward speed a pop gives, in scene units per second. */
    static final float POP_SPEED = 700f;
    static final String HINT = "Aim at a ball or crate: Space kick, hold F push, E pop the neighbours, T stop it.  Aiming at: ";

    private TextComponent label;

    @Override
    public void onStart() {
        super.onStart();
        Entity help = getScene().findEntityByName("Help Text");
        label = help != null ? help.getComponent(TextComponent.class) : null;
    }

    @Override
    public void onUpdate(float deltaTime) {
        PhysicsSystem3D physics = getPhysics3D();
        if (physics == null) {
            return;
        }
        Vector3 forward = new Vector3(0f, 0f, -1f).mul(getTransform().getWorldRotation()).nor();
        PhysicsSystem3D.RaycastHit hit = raycast3D(getTransform().getWorldPosition(), forward, RANGE);
        Entity aimed = hit != null && isDynamic(hit.entity()) ? hit.entity() : null;
        if (label != null) {
            label.setText(HINT + (hit != null ? hit.entity().getName() : "nothing"));
        }

        if (aimed != null && Gdx.input.isKeyJustPressed(Keys.SPACE)) {
            physics.addImpulse(aimed, new Vector3(forward).scl(KICK_SPEED * massOf(aimed)));
        }
        if (aimed != null && Gdx.input.isKeyPressed(Keys.F)) {
            physics.addForce(aimed, new Vector3(forward).scl(PUSH_ACCELERATION * massOf(aimed)));
        }
        if (aimed != null && Gdx.input.isKeyJustPressed(Keys.T)) {
            physics.setLinearVelocity(aimed, new Vector3());
        }
        if (hit != null && Gdx.input.isKeyJustPressed(Keys.E)) {
            List<Entity> neighbours = physics.overlapSphere(hit.point(), POP_RADIUS, PhysicsLayers3D.ALL_LAYERS);
            for (Entity neighbour : neighbours) {
                if (isDynamic(neighbour)) {
                    physics.addImpulse(neighbour, new Vector3(0f, POP_SPEED * massOf(neighbour), 0f));
                }
            }
        }
    }

    private static boolean isDynamic(Entity entity) {
        RigidBody3DComponent body = entity.getComponent(RigidBody3DComponent.class);
        return body != null && body.isEnabled() && body.getMotionType() == RigidBody3DComponent.MotionType.DYNAMIC;
    }

    private static float massOf(Entity entity) {
        return entity.getComponent(RigidBody3DComponent.class).getMass();
    }
}
