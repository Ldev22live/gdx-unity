package io.github.ldev22live.gdxunity.engine.render.components;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.render.Renderable;
import io.github.ldev22live.gdxunity.engine.scene.BaseComponent;
import io.github.ldev22live.gdxunity.engine.scene.Transform;

import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;

import java.util.Objects;

/**
 * Renderable component that draws a textured sprite or texture region transformed by its entity's {@link Transform}.
 * Texture ownership remains with the caller/asset manager; the component does not dispose textures unless explicitly configured.
 */
public class SpriteRendererComponent extends BaseComponent implements Renderable {
    private AssetReference assetReference;
    private Texture texture;
    private float width;
    private float height;
    private Color tint = new Color(Color.WHITE);
    private boolean centered = true;
    private boolean ownsTexture = false;

    public SpriteRendererComponent() {
    }

    public SpriteRendererComponent(float width, float height) {
        this.width = width;
        this.height = height;
    }

    public SpriteRendererComponent(AssetReference assetReference) {
        this.assetReference = assetReference;
    }

    public SpriteRendererComponent(AssetReference assetReference, float width, float height) {
        this.assetReference = assetReference;
        this.width = width;
        this.height = height;
    }

    public SpriteRendererComponent(Texture texture) {
        setTexture(texture);
    }

    public SpriteRendererComponent(Texture texture, float width, float height) {
        this.texture = texture;
        this.width = width;
        this.height = height;
    }

    public SpriteRendererComponent(AssetReference assetReference, Texture texture, float width, float height) {
        this.assetReference = assetReference;
        this.texture = texture;
        this.width = width;
        this.height = height;
    }

    public AssetReference getAssetReference() {
        return assetReference;
    }

    public void setAssetReference(AssetReference assetReference) {
        this.assetReference = assetReference;
    }

    @Override
    public boolean isSprite() {
        return texture != null;
    }

    @Override
    public void render(SpriteBatch spriteBatch) {
        if (texture == null || getEntity() == null || spriteBatch == null) {
            return;
        }

        Transform transform = getEntity().getTransform();
        Vector3 worldPos = transform.getWorldPosition();
        Quaternion worldRot = transform.getWorldRotation();
        Vector3 worldScale = transform.getWorldScale();

        // Calculate 2D yaw/roll angle around Z-axis in degrees
        float angleDeg = worldRot.getAngleAround(0f, 0f, 1f);

        float originX = width / 2f;
        float originY = height / 2f;
        float drawX = centered ? (worldPos.x - originX) : worldPos.x;
        float drawY = centered ? (worldPos.y - originY) : worldPos.y;

        Color prevColor = new Color(spriteBatch.getColor());
        spriteBatch.setColor(tint);

        spriteBatch.draw(
            texture,
            drawX, drawY,
            originX, originY,
            width, height,
            worldScale.x, worldScale.y,
            angleDeg,
            0, 0,
            texture.getWidth(), texture.getHeight(),
            false, false
        );

        spriteBatch.setColor(prevColor);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        if (ownsTexture && texture != null) {
            texture.dispose();
            texture = null;
        }
    }

    public Texture getTexture() {
        return texture;
    }

    public void setTexture(Texture texture) {
        this.texture = texture;
        if (texture != null) {
            this.width = texture.getWidth();
            this.height = texture.getHeight();
        }
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

    public boolean isOwnsTexture() {
        return ownsTexture;
    }

    public void setOwnsTexture(boolean ownsTexture) {
        this.ownsTexture = ownsTexture;
    }
}
