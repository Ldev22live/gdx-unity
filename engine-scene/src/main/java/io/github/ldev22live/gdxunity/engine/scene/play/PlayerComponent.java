package io.github.ldev22live.gdxunity.engine.scene.play;

import io.github.ldev22live.gdxunity.engine.scene.BaseComponent;

/**
 * Marks the entity a genre profile controls at Play (TD-088). A scene has at most one; its authored position is where the player
 * starts. It does nothing by itself: the scene's Game Genre and Game Mode decide what Play adds to the player.
 *
 * <p>When the player already has its own script or graph, the profile leaves its movement to that logic and supplies only the
 * camera, unless {@link #isUseProfileMovement()} is on (TD-088 amendment).
 */
public class PlayerComponent extends BaseComponent {
    private boolean useProfileMovement;

    /**
     * Whether the profile's movement runs even when the player has its own script or graph. Off by default, so the author's logic
     * is never overridden without asking.
     */
    public boolean isUseProfileMovement() {
        return useProfileMovement;
    }

    public void setUseProfileMovement(boolean useProfileMovement) {
        this.useProfileMovement = useProfileMovement;
    }
}
