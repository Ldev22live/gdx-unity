package io.github.ldev22live.gdxunity.engine.render.components;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.render.Renderable;
import io.github.ldev22live.gdxunity.engine.scene.BaseComponent;
import io.github.ldev22live.gdxunity.engine.scene.Transform;
import io.github.ldev22live.gdxunity.engine.sprite.NineSlice;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteAnimationState;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteDocument;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteFile;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteSlice;

import java.io.IOException;
import java.util.Objects;

/**
 * Draws a {@code .sprite} (from the Sprites tab) and plays its animation (TD-066, milestone D). It shows the sprite's tag in the scene's 2D pass, so it works in
 * 2D and 2.5D scenes alike, with nearest filtering so pixels stay sharp. Scripts and graphs control it with {@link #play}, {@link #stop},
 * {@link #pause}, {@link #setSpeed} and {@link #isFinished}.
 * <p>
 * The component is a pure description until its sprite is loaded ({@link #load(FileHandle)} or {@link #setDocument}); the first draw builds the texture on the
 * GL thread.
 */
public class SpriteAnimatorComponent extends BaseComponent implements Renderable {
    private AssetReference assetReference;
    private String tag;
    private float speed = 1f;
    private boolean loop = true;
    private boolean playOnStart = true;
    private boolean flipX;
    private boolean flipY;
    /** Drawn size in scene units; 0 means the sprite's own pixel size. */
    private float width;
    private float height;
    private Color tint = new Color(Color.WHITE);
    private boolean centered = true;
    /** The name of a slice with 9-slice borders to draw stretched to the size, or null to draw the whole frame. */
    private String slice;

    private SpriteAnimationState state;
    private String loadError;
    private Texture texture;
    private int columns;
    private int builtFrames;

    public SpriteAnimatorComponent() {
    }

    public SpriteAnimatorComponent(AssetReference assetReference) {
        this.assetReference = assetReference;
    }

    // ---- configuration ----

    public AssetReference getAssetReference() {
        return assetReference;
    }

    /** Changes the sprite; it is loaded again on the next draw. */
    public void setAssetReference(AssetReference assetReference) {
        this.assetReference = assetReference;
        release();
        state = null;
        loadError = null;
    }

    /** The tag that plays when the scene starts; null plays every frame. */
    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag == null || tag.isBlank() ? null : tag;
        if (state != null) {
            state.play(this.tag, true);
        }
    }

    public float getSpeed() {
        return speed;
    }

    public void setSpeed(float speed) {
        if (speed < 0f || Float.isNaN(speed)) {
            throw new IllegalArgumentException("speed cannot be negative or NaN: " + speed);
        }
        this.speed = speed;
        if (state != null) {
            state.setSpeed(speed);
        }
    }

    public boolean isLoop() {
        return loop;
    }

    public void setLoop(boolean loop) {
        this.loop = loop;
        if (state != null) {
            state.setLoop(loop);
        }
    }

    public boolean isPlayOnStart() {
        return playOnStart;
    }

    public void setPlayOnStart(boolean playOnStart) {
        this.playOnStart = playOnStart;
    }

    public boolean isFlipX() {
        return flipX;
    }

    public void setFlipX(boolean flipX) {
        this.flipX = flipX;
    }

    public boolean isFlipY() {
        return flipY;
    }

    public void setFlipY(boolean flipY) {
        this.flipY = flipY;
    }

    public float getWidth() {
        return width;
    }

    public void setWidth(float width) {
        this.width = width;
    }

    public float getHeight() {
        return height;
    }

    public void setHeight(float height) {
        this.height = height;
    }

    public Color getTint() {
        return tint;
    }

    public void setTint(Color tint) {
        this.tint = Objects.requireNonNull(tint, "tint cannot be null");
    }

    public boolean isCentered() {
        return centered;
    }

    public void setCentered(boolean centered) {
        this.centered = centered;
    }

    public String getSlice() {
        return slice;
    }

    /**
     * Draws the slice of this name as a 9-slice, stretched to the drawn size (corners keep their pixels), instead of the whole frame; null draws the whole frame.
     * A name the sprite has no bordered slice for draws the whole frame. A 9-slice is drawn without rotation or flipping.
     */
    public void setSlice(String slice) {
        this.slice = slice == null || slice.isBlank() ? null : slice;
    }

    // ---- loading ----

    /** Whether the sprite file still has to be read ({@link #load} was not called, or failed once: it is not tried again until the asset changes). */
    public boolean needsLoad() {
        return state == null && assetReference != null && loadError == null;
    }

    /** The reason the sprite could not be loaded, or null. */
    public String getLoadError() {
        return loadError;
    }

    /** Reads the sprite from a file; the reason for a failure is kept in {@link #getLoadError()}. */
    public void load(FileHandle file) {
        Objects.requireNonNull(file, "file cannot be null");
        if (!file.exists()) {
            loadError = "Sprite file not found: " + file.path();
            return;
        }
        try {
            setDocument(SpriteFile.read(file.readString("UTF-8")));
        } catch (IOException | RuntimeException e) {
            loadError = "Cannot read " + file.name() + ": " + e.getMessage();
        }
    }

    /** Uses an already read sprite (tests, and sprites built in code). */
    public void setDocument(SpriteDocument doc) {
        Objects.requireNonNull(doc, "doc cannot be null");
        release();
        SpriteAnimationState next = new SpriteAnimationState(doc);
        next.setSpeed(speed);
        next.setLoop(loop);
        if (tag != null) {
            try {
                next.play(tag, true);
            } catch (IllegalArgumentException e) {
                loadError = e.getMessage();
                state = null;
                return;
            }
        }
        if (!playOnStart) {
            next.pause();
        }
        state = next;
        loadError = null;
    }

    // ---- playback (scripts and graphs) ----

    private SpriteAnimationState requireState() {
        if (state == null) {
            throw new IllegalStateException(loadError != null ? loadError : "The sprite is not loaded yet");
        }
        return state;
    }

    /**
     * Plays a tag from its start, or keeps playing it when it already plays (so it may be called every frame). A null tag plays all frames.
     *
     * @throws IllegalArgumentException when the sprite has no tag with that name
     */
    public void play(String tagName) {
        play(tagName, false);
    }

    /** Like {@link #play(String)}; {@code restart} starts the tag over even if it already plays. */
    public void play(String tagName, boolean restart) {
        String wanted = tagName == null || tagName.isBlank() ? null : tagName;
        if (state == null) {
            // Not loaded yet: it starts with this tag once it is
            tag = wanted;
            playOnStart = true;
            return;
        }
        state.play(wanted, restart);
        tag = wanted;
    }

    public void pause() {
        playOnStart = false;
        if (state != null) {
            state.pause();
        }
    }

    public void resume() {
        playOnStart = true;
        if (state != null) {
            state.resume();
        }
    }

    /** Stops and shows the first frame of the current tag. */
    public void stop() {
        playOnStart = false;
        if (state != null) {
            state.stop();
        }
    }

    public boolean isPlaying() {
        return state != null && state.isPlaying();
    }

    /** Whether a non-looping animation reached its end. */
    public boolean isFinished() {
        return state != null && state.isFinished();
    }

    /** The tag that plays now, or null for all frames. */
    public String getCurrentTag() {
        return state != null ? state.getTag() : tag;
    }

    /** The index of the frame shown now. */
    public int getFrame() {
        return requireState().getFrame();
    }

    /** The loaded sprite, or null. */
    public SpriteDocument getDocument() {
        return state != null ? state.getDocument() : null;
    }

    @Override
    public void onUpdate(float deltaTime) {
        super.onUpdate(deltaTime);
        if (state != null) {
            state.advance(deltaTime);
        }
    }

    // ---- drawing ----

    @Override
    public boolean isSprite() {
        return state != null;
    }

    private void buildTexture() {
        SpriteDocument doc = state.getDocument();
        int frames = doc.getFrameCount();
        columns = (int) Math.ceil(Math.sqrt(frames));
        int rows = (frames + columns - 1) / columns;
        Pixmap pixmap = new Pixmap(columns * doc.getWidth(), rows * doc.getHeight(), Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        for (int f = 0; f < frames; f++) {
            int[] flat = doc.flatten(f);
            int ox = (f % columns) * doc.getWidth();
            int oy = (f / columns) * doc.getHeight();
            for (int y = 0; y < doc.getHeight(); y++) {
                for (int x = 0; x < doc.getWidth(); x++) {
                    pixmap.drawPixel(ox + x, oy + y, flat[y * doc.getWidth() + x]);
                }
            }
        }
        texture = new Texture(pixmap);
        pixmap.dispose();
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        builtFrames = frames;
    }

    @Override
    public void render(SpriteBatch spriteBatch) {
        if (state == null || getEntity() == null || spriteBatch == null) {
            return;
        }
        SpriteDocument doc = state.getDocument();
        if (texture == null || builtFrames != doc.getFrameCount()) {
            if (texture != null) {
                texture.dispose();
            }
            buildTexture();
        }
        int frame = state.getFrame();
        SpriteSlice nine = slice == null ? null : doc.getSlices().stream().filter(s -> s.name().equals(slice) && s.hasNineSlice()).findFirst().orElse(null);
        if (nine != null) {
            renderNineSlice(spriteBatch, doc, nine, frame);
            return;
        }
        int srcX = (frame % columns) * doc.getWidth();
        int srcY = (frame / columns) * doc.getHeight();
        float drawWidth = width > 0f ? width : doc.getWidth();
        float drawHeight = height > 0f ? height : doc.getHeight();

        Transform transform = getEntity().getTransform();
        Vector3 worldPos = transform.getWorldPosition();
        Quaternion worldRot = transform.getWorldRotation();
        Vector3 worldScale = transform.getWorldScale();
        float angleDeg = worldRot.getAngleAround(0f, 0f, 1f);
        float originX = drawWidth / 2f;
        float originY = drawHeight / 2f;
        float drawX = centered ? worldPos.x - originX : worldPos.x;
        float drawY = centered ? worldPos.y - originY : worldPos.y;

        Color previous = new Color(spriteBatch.getColor());
        spriteBatch.setColor(tint);
        spriteBatch.draw(texture, drawX, drawY, originX, originY, drawWidth, drawHeight, worldScale.x, worldScale.y, angleDeg,
            srcX, srcY, doc.getWidth(), doc.getHeight(), flipX, flipY);
        spriteBatch.setColor(previous);
    }

    private void renderNineSlice(SpriteBatch spriteBatch, SpriteDocument doc, SpriteSlice nine, int frame) {
        Transform transform = getEntity().getTransform();
        Vector3 worldPos = transform.getWorldPosition();
        Vector3 worldScale = transform.getWorldScale();
        float drawWidth = (width > 0f ? width : nine.width()) * Math.abs(worldScale.x);
        float drawHeight = (height > 0f ? height : nine.height()) * Math.abs(worldScale.y);
        float left = centered ? worldPos.x - drawWidth / 2f : worldPos.x;
        float bottom = centered ? worldPos.y - drawHeight / 2f : worldPos.y;
        int frameX = (frame % columns) * doc.getWidth();
        int frameY = (frame / columns) * doc.getHeight();
        Color previous = new Color(spriteBatch.getColor());
        spriteBatch.setColor(tint);
        for (NineSlice.Patch p : NineSlice.patches(nine, drawWidth, drawHeight)) {
            spriteBatch.draw(texture, left + p.dstX(), bottom + p.dstY(), p.dstWidth(), p.dstHeight(),
                frameX + p.srcX(), frameY + p.srcY(), p.srcWidth(), p.srcHeight(), false, false);
        }
        spriteBatch.setColor(previous);
    }

    private void release() {
        if (texture != null) {
            texture.dispose();
            texture = null;
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        release();
    }
}
