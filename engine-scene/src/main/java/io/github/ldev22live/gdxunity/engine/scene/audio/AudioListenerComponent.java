package io.github.ldev22live.gdxunity.engine.scene.audio;

import io.github.ldev22live.gdxunity.engine.scene.BaseComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;

import java.util.Objects;

/**
 * Marks the entity whose position and facing are the player's ears (Phase 20-C). Audio Sources that are <em>spatial</em> are heard
 * from here: they get quieter with distance and move between the speakers as they pass to the left or right of the entity.
 * Usually it sits on the camera or the player. It holds no settings; the first enabled listener on an active entity is used.
 */
public class AudioListenerComponent extends BaseComponent {

    /**
     * The listener a scene is heard from.
     *
     * @return the first enabled listener on an entity that is active in the hierarchy, or null if the scene has none
     */
    public static AudioListenerComponent find(Scene scene) {
        Objects.requireNonNull(scene, "scene cannot be null");
        for (Entity entity : scene.getAllEntities()) {
            if (!entity.isActiveInHierarchy()) {
                continue;
            }
            for (AudioListenerComponent listener : entity.getComponents(AudioListenerComponent.class)) {
                if (listener.isEnabled()) {
                    return listener;
                }
            }
        }
        return null;
    }
}
