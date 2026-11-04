package samples.racing;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent;

/**
 * Chase camera for the racing sample: stays behind and above the entity named "Player", turns with it, and looks a
 * little ahead of it. Unlike {@code samples.common.FollowCamera3D}, which keeps a fixed offset, this one follows the
 * car's heading, so "behind" changes as the car turns. Put it on the entity with a perspective Camera component.
 */
public class ChaseCamera3D extends ScriptComponent {
    /** Distance behind the car and height above it. */
    static final float DISTANCE = 300f;
    static final float HEIGHT = 140f;
    /** The camera looks at a point this far ahead of the car, at this height. */
    static final float LOOK_AHEAD = 140f;
    static final float LOOK_HEIGHT = 20f;
    /** How quickly the camera catches up; higher is snappier. */
    static final float FOLLOW_RATE = 6f;

    private final Vector3 position = new Vector3();
    private final Vector3 desired = new Vector3();
    private final Vector3 look = new Vector3();
    private final Vector3 forward = new Vector3();
    private Entity target;

    @Override
    public void onStart() {
        super.onStart();
        target = getScene().findEntityByName("Player");
        if (target == null) {
            throw new IllegalStateException("ChaseCamera3D needs an entity named 'Player'");
        }
        place(desired);
        position.set(desired);
        aim();
    }

    @Override
    public void onUpdate(float deltaTime) {
        place(desired);
        position.lerp(desired, Math.min(1f, FOLLOW_RATE * deltaTime));
        aim();
    }

    /** Where the camera wants to be: behind the car along its heading, and above it. */
    private void place(Vector3 out) {
        forward.set(0f, 0f, -1f);
        target.getTransform().getWorldRotation().transform(forward);
        forward.y = 0f;
        forward.nor();
        out.set(target.getTransform().getWorldPosition()).mulAdd(forward, -DISTANCE).add(0f, HEIGHT, 0f);
    }

    private void aim() {
        look.set(target.getTransform().getWorldPosition()).mulAdd(forward, LOOK_AHEAD).add(0f, LOOK_HEIGHT, 0f);
        look.sub(position).nor();
        // Forward (0, 0, -1) turned by pitch, then yaw, points along (-cos p sin y, sin p, -cos p cos y)
        float yaw = MathUtils.atan2(-look.x, -look.z) * MathUtils.radiansToDegrees;
        float pitch = (float) Math.asin(MathUtils.clamp(look.y, -1f, 1f)) * MathUtils.radiansToDegrees;
        getTransform().setLocalPosition(position);
        getTransform().setLocalEulerAngles(yaw, pitch, 0f);
    }
}
