package io.github.ldev22live.gdxunity.editor.desktop.ui;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetMetadata;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteDocument;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** The picture shown for a {@code .sprite} in the Content Browser: its first frame, flattened. Rebuilt when the file changed; owns its textures. */
public final class SpriteThumbnails implements Disposable {
    /** Sprites larger than this are not read just for a thumbnail. */
    private static final long MAX_FILE_BYTES = 8L * 1024 * 1024;

    private record Stamp(long size, long modifiedMillis) { }

    private static final class Entry {
        Stamp stamp;
        Texture texture;
        TextureRegion region;
    }

    private final AssetSource source;
    private final Map<String, Entry> entries = new HashMap<>();

    public SpriteThumbnails(AssetSource source) {
        this.source = Objects.requireNonNull(source, "source cannot be null");
    }

    /** The picture of the sprite, or null when it is too large or cannot be read. */
    public TextureRegion get(AssetMetadata sprite) {
        Objects.requireNonNull(sprite, "sprite cannot be null");
        Stamp stamp = new Stamp(sprite.fileSizeBytes(), sprite.lastModifiedMillis());
        Entry entry = entries.get(sprite.getPath());
        if (entry != null && stamp.equals(entry.stamp)) {
            return entry.region;
        }
        if (entry == null) {
            entry = new Entry();
            entries.put(sprite.getPath(), entry);
        }
        entry.stamp = stamp;
        if (entry.texture != null) {
            entry.texture.dispose();
            entry.texture = null;
            entry.region = null;
        }
        if (sprite.fileSizeBytes() > MAX_FILE_BYTES) {
            return null;
        }
        try {
            FileHandle file = source.resolve(sprite.reference());
            SpriteDocument doc = SpriteFile.read(file.readString("UTF-8"));
            int[] flat = doc.flatten(0);
            Pixmap pixmap = new Pixmap(doc.getWidth(), doc.getHeight(), Pixmap.Format.RGBA8888);
            pixmap.setBlending(Pixmap.Blending.None);
            for (int y = 0; y < doc.getHeight(); y++) {
                for (int x = 0; x < doc.getWidth(); x++) {
                    pixmap.drawPixel(x, y, flat[y * doc.getWidth() + x]);
                }
            }
            entry.texture = new Texture(pixmap);
            pixmap.dispose();
            entry.texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            entry.region = new TextureRegion(entry.texture);
        } catch (IOException | RuntimeException e) {
            // A sprite that does not read gets no picture; opening it in the Sprites tab shows the reason
            entry.region = null;
        }
        return entry.region;
    }

    @Override
    public void dispose() {
        for (Entry entry : entries.values()) {
            if (entry.texture != null) {
                entry.texture.dispose();
            }
        }
        entries.clear();
    }
}
