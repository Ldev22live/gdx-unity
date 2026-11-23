package io.github.ldev22live.gdxunity.engine.render.play;

import java.util.List;
import java.util.Objects;

/**
 * Which parts of a profile Play adds to the copy it runs, after "what the author made wins" (TD-088).
 *
 * @param addMovement  whether the profile's mover goes on the player: false when the player has its own script or graph and its
 *                     Use Profile Movement is off
 * @param addCameraRig whether the profile's camera rig goes on the game camera: false when the camera has its own rig
 * @param notes        one sentence per part left to the author, for Play's status line
 */
public record ProfilePlan(boolean addMovement, boolean addCameraRig, List<String> notes) {
    public ProfilePlan {
        notes = List.copyOf(Objects.requireNonNull(notes, "notes cannot be null"));
    }
}
