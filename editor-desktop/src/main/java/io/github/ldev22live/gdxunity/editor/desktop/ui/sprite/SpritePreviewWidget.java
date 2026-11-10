package io.github.ldev22live.gdxunity.editor.desktop.ui.sprite;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import com.badlogic.gdx.utils.Disposable;
import io.github.ldev22live.gdxunity.editor.sprite.SpriteSession;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteAnimation;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteDocument;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteTag;

import java.util.Objects;

/** Plays the sprite's animation (all frames, or one tag) at its own frame durations, as it will look in a game. */
final class SpritePreviewWidget extends Widget implements Disposable {
    private static final float SIZE = 96f;

    private final SpriteSession session;
    private Pixmap pixmap;
    private Texture texture;
    private boolean playing;
    private String tagName;
    private long elapsedMillis;
    private float carry;
    private int shownFrame = -1;
    private long shownRevision = -1;

    SpritePreviewWidget(SpriteSession session) {
        this.session = Objects.requireNonNull(session, "session cannot be null");
    }

    boolean isPlaying() {
        return playing;
    }

    void setPlaying(boolean playing) {
        this.playing = playing;
        elapsedMillis = 0;
        carry = 0;
        shownFrame = -1;
    }

    /** Plays only the tag with this name; null plays every frame. */
    void setTag(String tagName) {
        this.tagName = tagName;
        elapsedMillis = 0;
        shownFrame = -1;
    }

    @Override
    public float getPrefWidth() {
        return SIZE;
    }

    @Override
    public float getPrefHeight() {
        return SIZE;
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        if (playing) {
            carry += delta * 1000f;
            long whole = (long) carry;
            carry -= whole;
            elapsedMillis += whole;
        }
    }

    private SpriteTag tag(SpriteDocument doc) {
        if (tagName == null) {
            return null;
        }
        for (SpriteTag t : doc.getTags()) {
            if (t.name().equals(tagName)) {
                return t;
            }
        }
        return null;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        SpriteDocument doc = session.getDocument();
        int frame = playing ? SpriteAnimation.frameAt(doc, tag(doc), elapsedMillis, true) : session.getFrame();
        if (texture == null || pixmap.getWidth() != doc.getWidth() || pixmap.getHeight() != doc.getHeight()) {
            dispose();
            pixmap = new Pixmap(doc.getWidth(), doc.getHeight(), Pixmap.Format.RGBA8888);
            texture = new Texture(pixmap);
            texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            shownFrame = -1;
        }
        if (frame != shownFrame || shownRevision != session.getRevision()) {
            int[] flat = doc.flatten(frame);
            pixmap.setBlending(Pixmap.Blending.None);
            for (int y = 0; y < doc.getHeight(); y++) {
                for (int x = 0; x < doc.getWidth(); x++) {
                    pixmap.drawPixel(x, y, flat[y * doc.getWidth() + x]);
                }
            }
            texture.draw(pixmap, 0, 0);
            shownFrame = frame;
            shownRevision = session.getRevision();
        }
        float scale = Math.max(1, (int) Math.min(getWidth() / doc.getWidth(), getHeight() / doc.getHeight()));
        float w = doc.getWidth() * scale;
        float h = doc.getHeight() * scale;
        batch.setColor(1f, 1f, 1f, parentAlpha);
        batch.draw(texture, getX() + (getWidth() - w) / 2f, getY() + (getHeight() - h) / 2f, w, h);
    }

    @Override
    public void dispose() {
        if (texture != null) {
            texture.dispose();
            pixmap.dispose();
            texture = null;
            pixmap = null;
        }
    }
}
