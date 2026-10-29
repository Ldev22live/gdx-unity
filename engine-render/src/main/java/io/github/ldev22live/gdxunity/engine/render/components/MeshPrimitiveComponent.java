package io.github.ldev22live.gdxunity.engine.render.components;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.scene.BaseComponent;

import java.util.Objects;

/**
 * A built-in 3D shape (box, sphere or plane) rendered by {@code SceneRenderer3D} with a solid color.
 * Size is in world units and is applied on top of the entity's transform scale. The plane lies in the
 * XZ plane (facing +Y), so its Y size is ignored.
 */
public class MeshPrimitiveComponent extends BaseComponent {

    public enum Shape {
        BOX,
        SPHERE,
        PLANE
    }

    /** Default edge length / diameter in world units (the sample scene uses pixel-sized units). */
    public static final float DEFAULT_SIZE = 100f;

    private Shape shape;
    private final Vector3 size = new Vector3();
    private final Color color = new Color();

    public MeshPrimitiveComponent() {
        this(Shape.BOX);
    }

    public MeshPrimitiveComponent(Shape shape) {
        this(shape, DEFAULT_SIZE, DEFAULT_SIZE, DEFAULT_SIZE, new Color(0.78f, 0.78f, 0.80f, 1f));
    }

    public MeshPrimitiveComponent(Shape shape, float sizeX, float sizeY, float sizeZ, Color color) {
        setShape(shape);
        setSize(sizeX, sizeY, sizeZ);
        setColor(color);
    }

    public Shape getShape() {
        return shape;
    }

    public void setShape(Shape shape) {
        this.shape = Objects.requireNonNull(shape, "shape cannot be null");
    }

    public Vector3 getSize() {
        return new Vector3(size);
    }

    /**
     * @throws IllegalArgumentException if any size is not positive
     */
    public void setSize(float sizeX, float sizeY, float sizeZ) {
        if (sizeX <= 0f || sizeY <= 0f || sizeZ <= 0f) {
            throw new IllegalArgumentException("Size must be positive, got: " + sizeX + "x" + sizeY + "x" + sizeZ);
        }
        size.set(sizeX, sizeY, sizeZ);
    }

    public void setSize(Vector3 size) {
        Objects.requireNonNull(size, "size cannot be null");
        setSize(size.x, size.y, size.z);
    }

    public Color getColor() {
        return new Color(color);
    }

    public void setColor(Color color) {
        this.color.set(Objects.requireNonNull(color, "color cannot be null"));
    }

    /** Extent in world units before entity scale; a plane has no thickness along Y. */
    public Vector3 getExtent() {
        return shape == Shape.PLANE ? new Vector3(size.x, 0f, size.z) : new Vector3(size);
    }
}
