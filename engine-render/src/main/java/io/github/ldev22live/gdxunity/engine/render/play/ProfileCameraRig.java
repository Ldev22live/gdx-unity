package io.github.ldev22live.gdxunity.engine.render.play;

import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.render.components.CameraRigComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.play.GameTestProfile;
import io.github.ldev22live.gdxunity.engine.scene.play.InputAction;
import io.github.ldev22live.gdxunity.engine.scene.play.InputMapping;
import io.github.ldev22live.gdxunity.engine.scene.play.InputSource;
import io.github.ldev22live.gdxunity.engine.scene.play.RuntimeOnlyComponent;

import java.util.Objects;

/**
 * The camera rig a genre profile puts on the game camera of the Play copy (TD-088). It is an ordinary {@link CameraRigComponent}
 * configured from the profile's {@link GameTestProfile.CameraSetup}, and is never saved.
 * <ul>
 *   <li>{@code SIDE_FOLLOW} (Platformer + 2.5D): a {@code FOLLOW} rig in front of the play plane.</li>
 *   <li>{@code FOLLOW_2D} (Platformer + 2D, TD-105): a {@code FOLLOW} rig that follows across the screen; its rotation is reset to
 *       looking straight along -Z after every update, so the 2D view never tilts.</li>
 *   <li>{@code THIRD_PERSON} (Action RPG + 3D): an {@code ORBIT} rig behind the player, which starts looking the way the player
 *       faces and turns with the turn keys (Q and E by default). Walls and terrain in the way pull it in.</li>
 *   <li>{@code ISOMETRIC} (Action RPG + 2.5D): an {@code ORBIT} rig high and at a fixed angle ({@link #ISOMETRIC_YAW_DEGREES} round,
 *       the profile's tilt down) that follows the player and never turns, so the screen's directions stay the same.</li>
 *   <li>{@code FIRST_PERSON} (First-person Shooter + 3D): a {@code FIRST_PERSON} rig at the player's eyes, looking the way the
 *       player faces; the rig itself never turns.</li>
 *   <li>{@code BEHIND_FACING} (Third-person Shooter + 3D): an {@code ORBIT} rig behind and above the player, set every frame to look
 *       the way the player faces, so the player's own turning aims the view. Walls and terrain in the way pull it in.</li>
 * </ul>
 */
public class ProfileCameraRig extends CameraRigComponent implements RuntimeOnlyComponent {
    /** How fast the turn keys turn the third-person camera, degrees per second. */
    public static final float TURN_DEGREES_PER_SECOND = 120f;
    /** The isometric camera's fixed yaw: looking diagonally across the ground, as isometric games do. */
    public static final float ISOMETRIC_YAW_DEGREES = 45f;

    private final GameTestProfile.CameraSetup setup;
    private final InputMapping keys;
    private final InputSource input;
    private final Vector3 tmpFacing = new Vector3();

    /**
     * A rig that follows {@code targetName} as {@code setup} says; the third-person rig reads its turn keys from {@code input}.
     */
    public ProfileCameraRig(GameTestProfile.CameraSetup setup, String targetName, InputMapping keys, InputSource input) {
        this.setup = Objects.requireNonNull(setup, "setup cannot be null");
        this.keys = Objects.requireNonNull(keys, "keys cannot be null");
        this.input = Objects.requireNonNull(input, "input cannot be null");
        setTargetName(Objects.requireNonNull(targetName, "targetName cannot be null"));
        switch (setup.kind()) {
            case SIDE_FOLLOW -> {
                // Beside the play plane on +Z, looking back along -Z at the player: the side view of a 2.5D game
                setMode(Mode.FOLLOW);
                setFollowOffset(new Vector3(0f, setup.height(), setup.distance()));
                setFollowRate(setup.followRate());
                setPivotHeight(0f);
            }
            case FOLLOW_2D -> {
                // Offset and pivot at the same height: the rig looks straight along -Z (yaw and pitch 0)
                setMode(Mode.FOLLOW);
                setFollowOffset(new Vector3(0f, setup.height(), setup.distance()));
                setFollowRate(setup.followRate());
                setPivotHeight(setup.height());
            }
            case THIRD_PERSON, BEHIND_FACING -> {
                setMode(Mode.ORBIT);
                setDistanceLimits(Math.min(DEFAULT_MIN_DISTANCE, setup.distance()), Math.max(DEFAULT_MAX_DISTANCE, setup.distance()));
                setDistance(setup.distance());
                setPivotHeight(setup.height());
                setPitch(-setup.pitchDegrees());
                setOcclusionEnabled(true);
            }
            case FIRST_PERSON -> {
                // At the player's eyes, looking the way it faces: its movement turns the view (roadmap M3b.7)
                setMode(Mode.FIRST_PERSON);
                setEyeHeight(setup.height());
                setYaw(0f);
                setPitch(-setup.pitchDegrees());
            }
            case ISOMETRIC -> {
                setMode(Mode.ORBIT);
                setDistanceLimits(Math.min(DEFAULT_MIN_DISTANCE, setup.distance()), Math.max(DEFAULT_MAX_DISTANCE, setup.distance()));
                setDistance(setup.distance());
                setPivotHeight(setup.height());
                setPitch(-setup.pitchDegrees());
                setYaw(ISOMETRIC_YAW_DEGREES);
                // A fixed view from high up: pulling the camera in front of walls would jump the view about
                setOcclusionEnabled(false);
            }
        }
    }

    /** Starts a camera behind the player (third person, behind facing) looking the way it faces. */
    @Override
    public void onStart() {
        super.onStart();
        GameTestProfile.CameraSetup.Kind kind = setup.kind();
        if (kind == GameTestProfile.CameraSetup.Kind.THIRD_PERSON || kind == GameTestProfile.CameraSetup.Kind.BEHIND_FACING) {
            faceTargetsWay();
        }
    }

    /** Sets the yaw to look the way the target faces; a target facing straight up or down leaves it as it is. */
    private void faceTargetsWay() {
        Entity target = getTarget();
        if (target == null) {
            return;
        }
        target.getTransform().getWorldRotation().transform(forward(0f, 0f, tmpFacing));
        if (tmpFacing.x * tmpFacing.x + tmpFacing.z * tmpFacing.z > 1e-6f) {
            setYaw(yawOf(tmpFacing));
        }
    }

    @Override
    public void onUpdate(float deltaTime) {
        if (setup.kind() == GameTestProfile.CameraSetup.Kind.THIRD_PERSON) {
            float turn = (keys.isActive(InputAction.TURN_LEFT, input) ? 1f : 0f) - (keys.isActive(InputAction.TURN_RIGHT, input) ? 1f : 0f);
            if (turn != 0f) {
                // Positive yaw turns left (see CameraRigComponent)
                rotate(turn * TURN_DEGREES_PER_SECOND * deltaTime, 0f);
            }
        }
        if (setup.kind() == GameTestProfile.CameraSetup.Kind.BEHIND_FACING) {
            // The player's turning (its mover) aims the view: the camera stays behind it
            faceTargetsWay();
        }
        super.onUpdate(deltaTime);
        if (setup.kind() == GameTestProfile.CameraSetup.Kind.FOLLOW_2D && getEntity() != null) {
            // A FOLLOW rig aims at the player; in 2D a lagging camera would tilt steeply (the depth offset is tiny). A 2D view looks
            // straight along -Z, whatever the lag
            getEntity().getTransform().setLocalEulerAngles(0f, 0f, 0f);
        }
    }
}
