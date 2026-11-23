package io.github.ldev22live.gdxunity.engine.render;

import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.utils.Disposable;

/**
 * GPU maps for a sky light's image-based lighting, owned by {@link SceneRenderer3D}. {@link #applyTo} adds them to
 * the environment the PBR shader reads; it is called every frame and must not allocate.
 */
public interface SkyLightMaps extends Disposable {

    /** Adds the maps to {@code environment} (after the renderer has cleared it for the frame). */
    void applyTo(Environment environment);
}
