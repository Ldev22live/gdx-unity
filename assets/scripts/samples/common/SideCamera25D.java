package samples.common;

import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent;

/**
 * Side-view camera for the 2.5D samples: looks at the level from the front (+Z), slightly from above, and follows the
 * entity named "Player" left, right, up and down. Objects at other depths shift at different speeds as it moves,
 * which gives the 2.5D sense of depth. Put it on the entity with a perspective Camera component.
 */
public class SideCamera25D extends ScriptComponent {
    /** Distance in front of the gameplay plane. */
    static final float DISTANCE = 1000f;
    /** Height above the player. */
    static final float HEIGHT = 150f;
    /** Downward tilt in degrees. */
    static final float PITCH = -7f;
    /** Lowest camera height, so the view never sinks far below the ground. */
    static final float MIN_Y = 140f;
    /** How quickly the camera catches up; higher is snappier. */
    static final float FOLLOW_RATE = 4f;

    private Entity target;
    private float x;
    private float y;

    @Override
    public void onStart() {
        super.onStart();
        target = getScene().findEntityByName("Player");
        if (target == null) {
            throw new IllegalStateException("SideCamera25D needs an entity named 'Player'");
        }
        Vector3 position = target.getTransform().getWorldPosition();
        x = position.x;
        y = Math.max(MIN_Y, position.y + HEIGHT);
        getTransform().setLocalEulerAngles(0f, PITCH, 0f);
        getTransform().setLocalPosition(x, y, DISTANCE);
    }

    @Override
    public void onUpdate(float deltaTime) {
        Vector3 position = target.getTransform().getWorldPosition();
        float blend = Math.min(1f, FOLLOW_RATE * deltaTime);
        x += (position.x - x) * blend;
        y += (Math.max(MIN_Y, position.y + HEIGHT) - y) * blend;
        getTransform().setLocalPosition(x, y, DISTANCE);
    }
}
