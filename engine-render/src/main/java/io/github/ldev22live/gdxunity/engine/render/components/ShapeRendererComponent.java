package io.github.ldev22live.gdxunity.engine.render.components;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import io.github.ldev22live.gdxunity.engine.render.Renderable;
import io.github.ldev22live.gdxunity.engine.scene.BaseComponent;
import io.github.ldev22live.gdxunity.engine.scene.Transform;

import java.util.Objects;

/**
 * Renderable component that draws a colored 2D rectangle in world space according to its entity's {@link Transform}.
 * Supports origin offsetting for centered or corner alignment.
 */
public class ShapeRendererComponent extends BaseComponent implements Renderable {
    public enum ShapeType {
        FILLED,
        LINE
    }

    private float width;
    private float height;
    private Color color;
    private ShapeType shapeType;
    private boolean centered;

    public ShapeRendererComponent() {
        this(50f, 50f, Color.CYAN, ShapeType.FILLED, true);
    }

    public ShapeRendererComponent(float width, float height, Color color) {
        this(width, height, color, ShapeType.FILLED, true);
    }

    public ShapeRendererComponent(float width, float height, Color color, ShapeType shapeType, boolean centered) {
        this.width = width;
        this.height = height;
        this.color = new Color(Objects.requireNonNull(color, "color cannot be null"));
        this.shapeType = Objects.requireNonNull(shapeType, "shapeType cannot be null");
        this.centered = centered;
    }

    @Override
    public boolean isPrimitive() {
        return true;
    }

    @Override
    public void render(ShapeRenderer shapeRenderer) {
        if (getEntity() == null || shapeRenderer == null) {
            return;
        }

        Transform transform = getEntity().getTransform();
        Matrix4 worldMatrix = transform.getWorldMatrix();

        // Push world transform matrix
        Matrix4 previousMatrix = new Matrix4(shapeRenderer.getTransformMatrix());
        shapeRenderer.setTransformMatrix(worldMatrix);

        shapeRenderer.set(shapeType == ShapeType.FILLED ? ShapeRenderer.ShapeType.Filled : ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(color);

        float drawX = centered ? -width / 2f : 0f;
        float drawY = centered ? -height / 2f : 0f;

        shapeRenderer.rect(drawX, drawY, width, height);

        // Restore transform matrix
        shapeRenderer.setTransformMatrix(previousMatrix);
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

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        this.color.set(Objects.requireNonNull(color, "color cannot be null"));
    }

    public ShapeType getShapeType() {
        return shapeType;
    }

    public void setShapeType(ShapeType shapeType) {
        this.shapeType = Objects.requireNonNull(shapeType, "shapeType cannot be null");
    }

    public boolean isCentered() {
        return centered;
    }

    public void setCentered(boolean centered) {
        this.centered = centered;
    }
}
