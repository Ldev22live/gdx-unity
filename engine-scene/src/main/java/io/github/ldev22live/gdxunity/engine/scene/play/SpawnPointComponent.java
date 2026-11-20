package io.github.ldev22live.gdxunity.engine.scene.play;

import io.github.ldev22live.gdxunity.engine.scene.BaseComponent;

/**
 * Where the player starts when the game plays (roadmap M3c.2, TD-088): the entity's position is the point on the ground under the
 * player's feet, and its yaw is the way the player faces. A scene has at most one. Without one, the player starts where it stands
 * in the scene, as before. It has no settings and draws nothing in the game; the editor marks it in the 3D view.
 */
public class SpawnPointComponent extends BaseComponent {
}
