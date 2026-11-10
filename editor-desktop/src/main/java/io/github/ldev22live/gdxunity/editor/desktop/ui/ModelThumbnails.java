package io.github.ldev22live.gdxunity.editor.desktop.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.Disposable;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetMetadata;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.render.ModelPreview;

import java.io.IOException;
import java.nio.IntBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Small pictures of the project's models for the Content Browser (Phase 13-C): each model is drawn once, from the front-right and a
 * little above, into a {@value #SIZE} pixel frame buffer with {@link ModelPreview}. A model is drawn on the first request, a few per
 * frame ({@link #renderPending}), so opening a folder of large models never stalls the editor; a file whose size or modification time
 * changed is drawn again, and a model that cannot be read is remembered (see {@link #getFailure}) instead of being tried every frame.
 *
 * <p>Needs a GL context for {@link #renderPending} and {@link #dispose}; {@link #get} only looks things up.
 */
public final class ModelThumbnails implements Disposable {
    /** Side of a thumbnail in pixels; shown smaller in the list, which keeps it sharp on a high-density screen. */
    public static final int SIZE = 64;
    /** Models larger than this are not loaded just for a picture. */
    public static final long MAX_FILE_BYTES = 24L * 1024 * 1024;
    private static final Color BACKGROUND = new Color(0.16f, 0.17f, 0.2f, 1f);
    private static final int VIEWPORT_VALUES = 16;

    private record Stamp(long size, long modifiedMillis) {
    }

    private static final class Entry {
        Stamp stamp;
        FrameBuffer picture;
        TextureRegion region;
        String failure;
    }

    private final AssetSource source;
    private final Map<String, Entry> entries = new HashMap<>();
    private final Deque<AssetMetadata> queue = new ArrayDeque<>();
    private final IntBuffer viewportBefore = BufferUtils.newIntBuffer(VIEWPORT_VALUES);
    private ModelPreview preview;
    private boolean disposed;

    public ModelThumbnails(AssetSource source) {
        this.source = Objects.requireNonNull(source, "source cannot be null");
    }

    /**
     * The picture of {@code model}, or null while it is waiting to be drawn, was too large, or could not be read. Asks for it to be
     * drawn when there is none yet, or when the file changed since it was drawn (the old picture is shown until the new one is ready).
     */
    public TextureRegion get(AssetMetadata model) {
        Objects.requireNonNull(model, "model cannot be null");
        Entry entry = entries.get(model.getPath());
        Stamp stamp = new Stamp(model.fileSizeBytes(), model.lastModifiedMillis());
        if (entry == null || !stamp.equals(entry.stamp)) {
            if (model.fileSizeBytes() > MAX_FILE_BYTES) {
                Entry skipped = entry != null ? entry : new Entry();
                skipped.stamp = stamp;
                skipped.failure = "larger than " + (MAX_FILE_BYTES / (1024 * 1024)) + " MB";
                entries.put(model.getPath(), skipped);
                return null;
            }
            if (queue.stream().noneMatch(queued -> queued.getPath().equals(model.getPath()))) {
                queue.add(model);
            }
        }
        return entry != null ? entry.region : null;
    }

    /** Why {@code path} has no picture ("larger than ..." or the load error), or null when it has one or is still waiting. */
    public String getFailure(String path) {
        Entry entry = entries.get(path);
        return entry != null ? entry.failure : null;
    }

    /** How many models wait to be drawn. */
    public int getPendingCount() {
        return queue.size();
    }

    /**
     * Draws up to {@code limit} waiting models. Call outside any batch (the stage's {@code act} is a good place); the GL viewport and
     * scissor state are put back as they were.
     *
     * @return whether a picture was made or a model was given up on, so the list should be drawn again
     */
    public boolean renderPending(int limit) {
        if (disposed || queue.isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (int i = 0; i < limit && !queue.isEmpty(); i++) {
            draw(queue.poll());
            changed = true;
        }
        return changed;
    }

    private void draw(AssetMetadata model) {
        Entry entry = entries.computeIfAbsent(model.getPath(), path -> new Entry());
        entry.stamp = new Stamp(model.fileSizeBytes(), model.lastModifiedMillis());
        try {
            if (preview == null) {
                preview = new ModelPreview();
            }
            preview.loadFile(source.resolve(model.reference()));
            if (entry.picture == null) {
                entry.picture = new FrameBuffer(Pixmap.Format.RGBA8888, SIZE, SIZE, true);
                entry.region = new TextureRegion(entry.picture.getColorBufferTexture());
                // Frame buffer textures are stored bottom-up
                entry.region.flip(false, true);
            }
            boolean scissor = Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST);
            viewportBefore.clear();
            Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT, viewportBefore);
            Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
            entry.picture.begin();
            try {
                preview.render(BACKGROUND, SIZE, SIZE);
            } finally {
                entry.picture.end();
                Gdx.gl.glViewport(viewportBefore.get(0), viewportBefore.get(1), viewportBefore.get(2), viewportBefore.get(3));
                if (scissor) {
                    Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
                }
            }
            entry.failure = null;
        } catch (IOException | RuntimeException e) {
            entry.failure = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        }
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        queue.clear();
        for (Entry entry : entries.values()) {
            if (entry.picture != null) {
                entry.picture.dispose();
            }
        }
        entries.clear();
        if (preview != null) {
            preview.dispose();
            preview = null;
        }
    }
}
