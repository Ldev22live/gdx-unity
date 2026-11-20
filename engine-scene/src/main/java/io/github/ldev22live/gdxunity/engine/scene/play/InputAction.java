package io.github.ldev22live.gdxunity.engine.scene.play;

/** The player actions a {@link GameTestProfile}'s movement reads through an {@link InputMapping} (TD-088). */
public enum InputAction {
    MOVE_LEFT,
    MOVE_RIGHT,
    MOVE_FORWARD,
    MOVE_BACK,
    JUMP,
    TURN_LEFT,
    TURN_RIGHT,
    /** A profile's shot (roadmap M3b.7: First-person Shooter + 3D); once per press. */
    FIRE
}
