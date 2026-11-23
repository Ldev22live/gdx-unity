package io.github.ldev22live.gdxunity.editor.core.asset;

import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.render.components.SpriteAnimatorComponent;
import io.github.ldev22live.gdxunity.engine.render.components.TilemapComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;

import java.util.Objects;

/**
 * A changed {@code .sprite} file reaches the World (roadmap M3c.8, Appendix F4 "source-to-runtime links"): every sprite animator and
 * tile map in a scene that uses the file forgets what it loaded, so the next draw reads the file again. Without this, a sprite edited
 * in 2D Studio kept its old look in the World until the scene was reopened, while Play (a fresh copy) already showed the new one.
 */
public final class SpriteReload {
    private SpriteReload() { }

    /**
     * Asks every user of {@code spritePath} in {@code scene} to load it again.
     *
     * @param scene      the scene to look in; null does nothing
     * @param spritePath the project-relative path of the changed file
     * @return how many components will reload
     */
    public static int reloadUsers(Scene scene, String spritePath) {
        Objects.requireNonNull(spritePath, "spritePath cannot be null");
        if (scene == null) {
            return 0;
        }
        String path = normalize(spritePath);
        int count = 0;
        for (Entity entity : scene.getAllEntities()) {
            SpriteAnimatorComponent animator = entity.getComponent(SpriteAnimatorComponent.class);
            if (animator != null && uses(animator.getAssetReference(), path)) {
                animator.requestReload();
                count++;
            }
            TilemapComponent tilemap = entity.getComponent(TilemapComponent.class);
            if (tilemap != null && uses(tilemap.getTileSetReference(), path)) {
                tilemap.requestReload();
                count++;
            }
        }
        return count;
    }

    private static boolean uses(AssetReference reference, String path) {
        return reference != null && normalize(reference.getPath()).equals(path);
    }

    private static String normalize(String path) {
        return path.replace('\\', '/');
    }
}
