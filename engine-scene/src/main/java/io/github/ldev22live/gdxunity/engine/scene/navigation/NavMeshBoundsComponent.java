package io.github.ldev22live.gdxunity.engine.scene.navigation;

import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.scene.BaseComponent;

import java.util.Objects;

/**
 * The area where walking agents can find their way (roadmap M6.4, TD-092), shown in the editor as <b>Nav Mesh Bounds</b>. A box of
 * {@link #getSize()} centred on its entity, axis-aligned in the world (the entity's position and scale count; its rotation does not).
 * When a scene plays, the ground inside the box is baked into a walkable grid ({@link NavGrid3D}): terrain and static 3D colliders
 * make the floor, the parts of them that stand up make obstacles, and each cell keeps {@link #getAgentRadius()} clear of edges and
 * walls. Every {@link NavAgent3DComponent} in the box then walks around the obstacles.
 *
 * <p>Distances are world units at the editor's 100 units per metre.
 */
public class NavMeshBoundsComponent extends BaseComponent {
    /** Default box: 20 m square and 5 m high. */
    public static final float DEFAULT_WIDTH = 2000f;
    public static final float DEFAULT_HEIGHT = 500f;
    /** Default cell edge: 25 cm, small enough to pass a door with a 35 cm agent. */
    public static final float DEFAULT_CELL_SIZE = 25f;
    /** Default agent radius and height: a person. */
    public static final float DEFAULT_AGENT_RADIUS = 35f;
    public static final float DEFAULT_AGENT_HEIGHT = 180f;
    /** Default steepest walkable slope, in degrees. */
    public static final float DEFAULT_MAX_SLOPE = 45f;
    /** Default highest step an agent walks up without a slope: a stair. */
    public static final float DEFAULT_STEP_HEIGHT = 35f;
    /** Most cells a grid may have; past this the bake is refused, so a huge box with tiny cells does not stall play. */
    public static final int MAX_CELLS = 1_000_000;

    private final Vector3 size = new Vector3(DEFAULT_WIDTH, DEFAULT_HEIGHT, DEFAULT_WIDTH);
    private float cellSize = DEFAULT_CELL_SIZE;
    private float agentRadius = DEFAULT_AGENT_RADIUS;
    private float agentHeight = DEFAULT_AGENT_HEIGHT;
    private float maxSlope = DEFAULT_MAX_SLOPE;
    private float stepHeight = DEFAULT_STEP_HEIGHT;

    /** Size of the box before the entity's scale (a copy). */
    public Vector3 getSize() {
        return new Vector3(size);
    }

    /**
     * Sets the box's size.
     *
     * @throws IllegalArgumentException if a side is not above 0 or not finite
     */
    public void setSize(Vector3 size) {
        Objects.requireNonNull(size, "size cannot be null");
        if (!(size.x > 0f && size.y > 0f && size.z > 0f) || !Float.isFinite(size.x) || !Float.isFinite(size.y) || !Float.isFinite(size.z)) {
            throw new IllegalArgumentException("Every side of the bounds must be above 0: " + size);
        }
        this.size.set(size);
    }

    /** Edge of one grid cell in world units: smaller finds narrower gaps, larger bakes faster. */
    public float getCellSize() {
        return cellSize;
    }

    public void setCellSize(float cellSize) {
        this.cellSize = positive("Cell size", cellSize);
    }

    /** How far, in world units, agents keep from walls and edges. */
    public float getAgentRadius() {
        return agentRadius;
    }

    public void setAgentRadius(float agentRadius) {
        if (!(agentRadius >= 0f) || !Float.isFinite(agentRadius)) {
            throw new IllegalArgumentException("Agent radius must be 0 or more: " + agentRadius);
        }
        this.agentRadius = agentRadius;
    }

    /** Free height an agent needs above the floor; lower overhangs block it. */
    public float getAgentHeight() {
        return agentHeight;
    }

    public void setAgentHeight(float agentHeight) {
        this.agentHeight = positive("Agent height", agentHeight);
    }

    /** Steepest slope agents walk, in degrees, 0 to 89. */
    public float getMaxSlope() {
        return maxSlope;
    }

    public void setMaxSlope(float degrees) {
        if (!(degrees >= 0f && degrees <= 89f)) {
            throw new IllegalArgumentException("Max slope must be within 0 and 89 degrees: " + degrees);
        }
        this.maxSlope = degrees;
    }

    /** Highest step, in world units, agents walk up between neighbouring cells. */
    public float getStepHeight() {
        return stepHeight;
    }

    public void setStepHeight(float stepHeight) {
        if (!(stepHeight >= 0f) || !Float.isFinite(stepHeight)) {
            throw new IllegalArgumentException("Step height must be 0 or more: " + stepHeight);
        }
        this.stepHeight = stepHeight;
    }

    /** A new component with the same settings; enabled like this one. */
    public NavMeshBoundsComponent copy() {
        NavMeshBoundsComponent copy = new NavMeshBoundsComponent();
        copy.set(this);
        return copy;
    }

    /** Takes every setting of {@code other}; enabled state included. */
    public void set(NavMeshBoundsComponent other) {
        Objects.requireNonNull(other, "other cannot be null");
        size.set(other.size);
        cellSize = other.cellSize;
        agentRadius = other.agentRadius;
        agentHeight = other.agentHeight;
        maxSlope = other.maxSlope;
        stepHeight = other.stepHeight;
        setEnabled(other.isEnabled());
    }

    private static float positive(String what, float value) {
        if (!(value > 0f) || !Float.isFinite(value)) {
            throw new IllegalArgumentException(what + " must be above 0: " + value);
        }
        return value;
    }
}
