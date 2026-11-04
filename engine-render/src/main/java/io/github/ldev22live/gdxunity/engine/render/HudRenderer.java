package io.github.ldev22live.gdxunity.engine.render;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;
import io.github.ldev22live.gdxunity.engine.core.resource.ResourceManager;
import io.github.ldev22live.gdxunity.engine.render.components.TextComponent;
import io.github.ldev22live.gdxunity.engine.scene.Component;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;

import java.util.List;

/**
 * Draws the scene's {@link TextComponent}s in screen space over whatever was rendered before (TD-034), with
 * LibGDX's built-in font. The batch and font are created on first use, so construction needs no graphics context.
 */
public final class HudRenderer implements Disposable {
    private final Matrix4 projection = new Matrix4();
    private final GlyphLayout layout = new GlyphLayout();
    private ResourceManager resources;
    private SpriteBatch batch;
    private BitmapFont font;

    /**
     * Draws every enabled text component of the scene's active entities into a view of {@code width} x
     * {@code height} pixels. Does nothing when the scene has no text.
     */
    public void render(Scene scene, int width, int height) {
        if (scene == null || width <= 0 || height <= 0 || !hasText(scene)) {
            return;
        }
        ensureCreated();
        projection.setToOrtho2D(0f, 0f, width, height);
        batch.setProjectionMatrix(projection);
        batch.begin();
        try {
            List<Entity> entities = scene.getAllEntities();
            for (int i = 0; i < entities.size(); i++) {
                Entity entity = entities.get(i);
                if (!entity.isActiveInHierarchy()) {
                    continue;
                }
                List<Component> components = entity.getAllComponents();
                for (int j = 0; j < components.size(); j++) {
                    if (components.get(j) instanceof TextComponent text && text.isEnabled() && !text.getText().isEmpty()) {
                        draw(text, width, height);
                    }
                }
            }
        } finally {
            batch.end();
        }
    }

    private void draw(TextComponent text, int width, int height) {
        font.getData().setScale(text.getScale());
        font.setColor(text.getColor());
        layout.setText(font, text.getText());
        TextComponent.Anchor anchor = text.getAnchor();
        float left = anchor.textLeft(width, layout.width) + text.getOffsetX();
        float top = anchor.textTop(height, layout.height) + text.getOffsetY();
        font.draw(batch, layout, left, top);
    }

    private static boolean hasText(Scene scene) {
        List<Entity> entities = scene.getAllEntities();
        for (int i = 0; i < entities.size(); i++) {
            if (entities.get(i).getComponent(TextComponent.class) != null) {
                return true;
            }
        }
        return false;
    }

    private void ensureCreated() {
        if (batch != null) {
            return;
        }
        resources = new ResourceManager();
        batch = resources.register(new SpriteBatch());
        font = resources.register(new BitmapFont());
        // Scaled text stays smooth instead of blocky
        font.getRegion().getTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        font.setUseIntegerPositions(false);
    }

    /** Disposes the batch and font; failures are collected per TD-008. Idempotent. */
    @Override
    public void dispose() {
        ResourceManager owned = resources;
        resources = null;
        batch = null;
        font = null;
        if (owned != null) {
            owned.dispose();
        }
    }
}
