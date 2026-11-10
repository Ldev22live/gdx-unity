package io.github.ldev22live.gdxunity.engine.render.components;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteDocument;

import java.util.Objects;

/**
 * One GL texture holding every flattened frame of a sprite in a grid, with nearest filtering, so a sprite or a tile set is a single texture bind
 * (TD-066). Built on the GL thread; the owner disposes it.
 */
public final class FrameAtlas implements Disposable {
    private final Texture texture;
    private final int columns;
    private final int frameWidth;
    private final int frameHeight;
    private final int frames;

    public FrameAtlas(SpriteDocument doc) {
        Objects.requireNonNull(doc, "doc cannot be null");
        frames = doc.getFrameCount();
        frameWidth = doc.getWidth();
        frameHeight = doc.getHeight();
        columns = (int) Math.ceil(Math.sqrt(frames));
        int rows = (frames + columns - 1) / columns;
        Pixmap pixmap = new Pixmap(columns * frameWidth, rows * frameHeight, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        for (int f = 0; f < frames; f++) {
            int[] flat = doc.flatten(f);
            int ox = (f % columns) * frameWidth;
            int oy = (f / columns) * frameHeight;
            for (int y = 0; y < frameHeight; y++) {
                for (int x = 0; x < frameWidth; x++) {
                    pixmap.drawPixel(ox + x, oy + y, flat[y * frameWidth + x]);
                }
            }
        }
        texture = new Texture(pixmap);
        pixmap.dispose();
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
    }

    public Texture texture() {
        return texture;
    }

    public int frames() {
        return frames;
    }

    public int frameWidth() {
        return frameWidth;
    }

    public int frameHeight() {
        return frameHeight;
    }

    /** Left edge of a frame in the texture, in pixels. */
    public int srcX(int frame) {
        return (frame % columns) * frameWidth;
    }

    /** Top edge of a frame in the texture, in pixels (y down). */
    public int srcY(int frame) {
        return (frame / columns) * frameHeight;
    }

    @Override
    public void dispose() {
        texture.dispose();
    }
}
