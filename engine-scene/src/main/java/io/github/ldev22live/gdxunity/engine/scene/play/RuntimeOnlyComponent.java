package io.github.ldev22live.gdxunity.engine.scene.play;

import io.github.ldev22live.gdxunity.engine.scene.Component;

/**
 * A component that exists only in the copy of a scene that Play runs (TD-088): what a genre profile adds, such as the player's
 * mover or the camera's rig. {@code SceneSerializer} refuses to write a scene that holds one, so a profile's parts can never end up
 * in a saved file. Stop disposes the copy and them with it.
 */
public interface RuntimeOnlyComponent extends Component {
}
