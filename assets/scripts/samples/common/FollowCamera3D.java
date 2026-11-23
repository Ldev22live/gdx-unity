package samples.common;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent;

/**
 * Third-person camera for the 3D samples: stays behind and above the entity named "Player" and looks at it.
 * Put it on the entity with a perspective Camera component. The camera looks along its local -Z axis, so aiming it
 * means setting yaw (turn around Y) and pitch (tilt around X).
 */
public class FollowCamera3D extends ScriptComponent {
    /** Camera position relative to the player: above and towards +Z (the player runs towards -Z). */
    static final Vector3 OFFSET = new Vector3(0f, 340f, 520f);
    /** Point above the player's centre the camera looks at. */
    static final float LOOK_HEIGHT = 40f;
    /** How quickly the camera catches up; higher is snappier. */
    static final float FOLLOW_RATE = 5f;

    private final Vector3 position = new Vector3();
    private final Vector3 desired = new Vector3();
    private final Vector3 direction = new Vector3();
    private Entity target;

    @Override
    public void onStart() {
        super.onStart();
        target = getScene().findEntityByName("Player");
        if (target == null) {
            throw new IllegalStateException("FollowCamera3D needs an entity named 'Player'");
        }
        position.set(target.getTransform().getWorldPosition()).add(OFFSET);
        aim();
    }

    @Override
    public void onUpdate(float deltaTime) {
        desired.set(target.getTransform().getWorldPosition()).add(OFFSET);
        position.lerp(desired, Math.min(1f, FOLLOW_RATE * deltaTime));
        aim();
    }

    private void aim() {
        Vector3 look = target.getTransform().getWorldPosition();
        direction.set(look.x, look.y + LOOK_HEIGHT, look.z).sub(position).nor();
        // Forward (0, 0, -1) turned by pitch, then yaw, points along (-cos p sin y, sin p, -cos p cos y)
        float yaw = MathUtils.atan2(-direction.x, -direction.z) * MathUtils.radiansToDegrees;
        float pitch = (float) Math.asin(MathUtils.clamp(direction.y, -1f, 1f)) * MathUtils.radiansToDegrees;
        getTransform().setLocalPosition(position);
        getTransform().setLocalEulerAngles(yaw, pitch, 0f);
    }
}
