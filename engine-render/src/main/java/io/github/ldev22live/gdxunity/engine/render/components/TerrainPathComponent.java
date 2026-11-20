package io.github.ldev22live.gdxunity.engine.render.components;

import com.badlogic.gdx.math.Vector2;
import io.github.ldev22live.gdxunity.engine.scene.BaseComponent;
import io.github.ldev22live.gdxunity.engine.terrain.Surface;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainPath;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A road, river or cliff along a curve on the level's ground (roadmap M3.7): the control points of a {@link TerrainPath} and how to
 * apply it. The points are in the ground's own space (x and z), so the path stays on the ground if the ground moves. Applying it (the
 * level editor's Path tool) carves the ground, paints {@link #getPaint()} along it and places {@link #getPrefabPath()} every
 * {@link #getSpacing()} units beside it; the component keeps the curve so it can be extended and applied again. It draws nothing
 * itself and does nothing in Play.
 */
public class TerrainPathComponent extends BaseComponent {
    /** Width of a new path, in world units. */
    public static final float DEFAULT_WIDTH = 120f;
    /** Depth of a new river, or height of a new cliff, in world units. */
    public static final float DEFAULT_DEPTH = 40f;
    /** Distance between the meshes placed along a new path, in world units. */
    public static final float DEFAULT_SPACING = 400f;

    private TerrainPath.Kind kind = TerrainPath.Kind.ROAD;
    private float width = DEFAULT_WIDTH;
    private float depth = DEFAULT_DEPTH;
    private Surface paint = Surface.DIRT;
    private String prefabPath = "";
    private float spacing = DEFAULT_SPACING;
    private final List<Vector2> points = new ArrayList<>();

    public TerrainPath.Kind getKind() {
        return kind;
    }

    public void setKind(TerrainPath.Kind kind) {
        this.kind = Objects.requireNonNull(kind, "kind cannot be null");
    }

    /** Full width of the road, river or cliff, in world units. */
    public float getWidth() {
        return width;
    }

    /** @throws IllegalArgumentException unless above 0 */
    public void setWidth(float width) {
        if (!(width > 0f && Float.isFinite(width))) {
            throw new IllegalArgumentException("Width must be above 0: " + width);
        }
        this.width = width;
    }

    /** How far a river cuts below the ground, or a cliff rises above it, in world units. */
    public float getDepth() {
        return depth;
    }

    /** @throws IllegalArgumentException if below 0 */
    public void setDepth(float depth) {
        if (!(depth >= 0f && Float.isFinite(depth))) {
            throw new IllegalArgumentException("Depth must be 0 or more: " + depth);
        }
        this.depth = depth;
    }

    /** The paint layer laid along the path, or {@link Surface#AUTO} for none. */
    public Surface getPaint() {
        return paint;
    }

    public void setPaint(Surface paint) {
        this.paint = Objects.requireNonNull(paint, "paint cannot be null");
    }

    /** The prefab placed beside the path, or an empty string for none. */
    public String getPrefabPath() {
        return prefabPath;
    }

    public void setPrefabPath(String prefabPath) {
        this.prefabPath = prefabPath == null ? "" : prefabPath.trim();
    }

    /** Distance between placed meshes along the path, in world units. */
    public float getSpacing() {
        return spacing;
    }

    /** @throws IllegalArgumentException unless above 0 */
    public void setSpacing(float spacing) {
        if (!(spacing > 0f && Float.isFinite(spacing))) {
            throw new IllegalArgumentException("Spacing must be above 0: " + spacing);
        }
        this.spacing = spacing;
    }

    /** The control points (x, z in the ground's own space), as a copy. */
    public List<Vector2> getPoints() {
        List<Vector2> copy = new ArrayList<>(points.size());
        for (Vector2 point : points) {
            copy.add(new Vector2(point));
        }
        return copy;
    }

    /** Replaces the control points with copies of {@code points}. */
    public void setPoints(List<Vector2> points) {
        Objects.requireNonNull(points, "points cannot be null");
        this.points.clear();
        for (Vector2 point : points) {
            this.points.add(new Vector2(Objects.requireNonNull(point, "a point cannot be null")));
        }
    }

    /** Number of control points. */
    public int getPointCount() {
        return points.size();
    }
}
