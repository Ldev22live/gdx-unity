package io.github.ldev22live.gdxunity.engine.render;

import com.badlogic.gdx.graphics.Texture;

/** Finds the texture a material's slot names (TD-081). Separate from {@link MaterialTextures} so the attribute mapping can be tested without files. */
@FunctionalInterface
interface TextureSource {
    /**
     * @param path a project-relative texture path
     * @return the texture, or null when it is missing or cannot be read (the slot is then drawn as if empty)
     */
    Texture get(String path);
}
