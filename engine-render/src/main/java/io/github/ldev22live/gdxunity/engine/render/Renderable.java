package io.github.ldev22live.gdxunity.engine.render;

import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.ldev22live.gdxunity.engine.scene.Component;

/**
 * Marker and execution interface for scene components that can be drawn by the rendering pipeline.
 */
public interface Renderable extends Component {
    /**
     * Determines whether this component renders via ShapeRenderer primitives.
     */
    default boolean isPrimitive() {
        return false;
    }

    /**
     * Determines whether this component renders via SpriteBatch textured sprites.
     */
    default boolean isSprite() {
        return false;
    }

    /**
     * Draws vector/shape geometry using the active ShapeRenderer.
     * The renderer's projection matrix has already been configured to the camera's view.
     *
     * @param shapeRenderer active ShapeRenderer
     */
    default void render(ShapeRenderer shapeRenderer) {
    }

    /**
     * Draws textured quad geometry using the active SpriteBatch.
     *
     * @param spriteBatch active SpriteBatch
     */
    default void render(SpriteBatch spriteBatch) {
    }
}
