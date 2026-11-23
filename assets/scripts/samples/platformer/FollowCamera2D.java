package samples.platformer;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent;

/**
 * Keeps the camera on the entity named "Player", horizontally only, and never shows past the level's ends.
 * Put it on the entity with the Camera component.
 */
public class FollowCamera2D extends ScriptComponent {
    /** How quickly the camera catches up; higher is snappier. */
    static final float FOLLOW_RATE = 6f;
    /** Leftmost camera centre: half of the 640-unit design width, so the view starts at x = 0. */
    static final float MIN_X = 320f;
    /** Rightmost camera centre, so the view ends at the level's right edge. */
    static final float MAX_X = 1900f;

    private Entity target;

    @Override
    public void onStart() {
        super.onStart();
        target = getScene().findEntityByName("Player");
        if (target == null) {
            throw new IllegalStateException("FollowCamera2D needs an entity named 'Player'");
        }
    }

    @Override
    public void onUpdate(float deltaTime) {
        Vector3 position = getTransform().getLocalPosition();
        float targetX = MathUtils.clamp(target.getTransform().getWorldPosition().x, MIN_X, MAX_X);
        float blend = Math.min(1f, FOLLOW_RATE * deltaTime);
        getTransform().setLocalPosition(position.x + (targetX - position.x) * blend, position.y, position.z);
    }
}
