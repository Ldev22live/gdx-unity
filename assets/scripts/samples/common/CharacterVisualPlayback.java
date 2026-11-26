package samples.common;

import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsSystem3D;
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent;

/** Drives only the visual child of a 3D character controller from its resolved motion. */
public final class CharacterVisualPlayback extends ScriptComponent {
    /** Contact corrections below this horizontal speed should leave the visual idle. */
    private static final float MOVING_SPEED = 8f;
    /** Faster movement reuses the walk cycle at a higher rate when no run clip exists. */
    private static final float RUN_SPEED = 220f;
    /** The upper playback multiplier for the walk fallback. */
    private static final float FAST_WALK = 1.5f;

    private enum Motion { IDLE, WALK, RUN, JUMP, FALL, LAND }

    private Entity character;
    private ModelComponent model;
    private Motion motion;

    @Override
    public void onStart() {
        character = getEntity().getTransform().getParent().getEntity();
        model = getEntity().getComponent(ModelComponent.class);
        motion = null;
    }

    @Override
    public void onUpdate(float deltaTime) {
        PhysicsSystem3D physics = getScene().getPhysics3D();
        Vector3 velocity = physics.getCharacterVelocity(character);
        Motion next = classify(physics.isCharacterGrounded(character), velocity);
        if (next == motion || model.getModelInfo().animationDurations().isEmpty()) {
            return;
        }
        motion = next;
        switch (next) {
            case IDLE -> play("Idle", true, 1f);
            case WALK, LAND -> play(horizontalSpeedSquared(velocity) > MOVING_SPEED * MOVING_SPEED ? "Walk" : "Idle", true, 1f);
            case RUN -> {
                boolean hasRun = hasClip("Run");
                play(hasRun ? "Run" : "Walk", true, hasRun ? 1f : FAST_WALK);
            }
            case JUMP -> play("Jump", false, 1f);
            case FALL -> {
                if (hasClip("Fall")) {
                    play("Fall", true, 1f);
                } else if (!"Jump".equals(model.getAnimation())) {
                    play("Idle", true, 1f);
                }
            }
        }
    }

    private Motion classify(boolean grounded, Vector3 velocity) {
        if (!grounded) {
            return velocity.y > 0f ? Motion.JUMP : Motion.FALL;
        }
        if (motion == Motion.JUMP || motion == Motion.FALL) {
            return Motion.LAND;
        }
        float speedSquared = horizontalSpeedSquared(velocity);
        if (speedSquared >= RUN_SPEED * RUN_SPEED) {
            return Motion.RUN;
        }
        return speedSquared >= MOVING_SPEED * MOVING_SPEED ? Motion.WALK : Motion.IDLE;
    }

    private static float horizontalSpeedSquared(Vector3 velocity) {
        return velocity.x * velocity.x + velocity.z * velocity.z;
    }

    private boolean hasClip(String clip) {
        return model.getModelInfo().animationDurations().containsKey(clip);
    }

    private void play(String clip, boolean loop, float speed) {
        if (hasClip(clip)) {
            model.setAnimation(clip);
            model.setLoop(loop);
            model.setSpeed(speed);
            model.setTime(0f);
            model.setPlaying(true);
        }
    }
}
