package io.github.ldev22live.gdxunity.engine.sculpt;

import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.IntArray;

import java.util.Arrays;
import java.util.Objects;

/**
 * One brush stroke: from {@link #begin} when the button goes down, through {@link #move} as the cursor travels over
 * the surface, to {@link #end}, which returns the {@link StrokeDelta} to put on the undo stack.
 *
 * <p>Dab brushes ({@link BrushType#usesDabs()}) apply their effect at evenly spaced points along the path, so the
 * result does not depend on how fast the cursor moved or how often the mouse reported. {@link BrushType#GRAB} picks
 * up the vertices under the brush at the start and moves them with the cursor. With
 * {@link SymmetrySettings} every dab is repeated on the mirrored sides.
 *
 * <p>Positions passed in are in the mesh's local space, as found by {@link SculptSurface#raycast}. One instance can
 * be reused for many strokes. No allocation happens while a stroke is running except growth of the undo record.
 * Not thread-safe.
 */
public final class SculptStroke {
    /** A dab at strength 1 moves a vertex at the brush centre by this fraction of the radius. */
    static final float DISPLACEMENT_PER_STRENGTH = 0.1f;
    /** Fraction of the way to the neighbour average that a smooth dab at strength 1 moves a vertex. */
    static final float SMOOTH_RATE = 0.5f;
    /** Fraction of the distance to the average plane that a flatten dab at strength 1 removes. */
    static final float FLATTEN_RATE = 0.5f;
    /** Fraction of the distance to the brush centre that a pinch dab at strength 1 covers. */
    static final float PINCH_RATE = 0.3f;
    /** A pinch dab also lifts the surface along the average normal by this fraction of a draw dab, so a crease shows. */
    static final float PINCH_LIFT = 0.5f;
    /** Most dabs one {@link #move} places, so a huge jump of the cursor cannot freeze the editor. */
    static final int MAX_DABS_PER_MOVE = 64;
    /** A mirrored dab is skipped when the centre is within this fraction of the radius of the mirror plane. */
    private static final float MIRROR_PLANE_TOLERANCE = 1e-3f;
    /** Weights and lengths below this count as zero. */
    private static final float EPSILON = 1e-9f;

    private final SculptSurface surface;
    private final SculptMesh mesh;

    private boolean active;
    private BrushType type;
    private float radius;
    private float strength;
    private float spacing;
    private Falloff falloff;
    private boolean subtract;
    private int mirrorMask;

    private float lastX, lastY, lastZ;
    private float lastNx, lastNy, lastNz;
    private float startX, startY, startZ;
    /** Distance travelled since the last dab. */
    private float carry;
    private int dabCount;

    private final int[] touchedStamp;
    private int strokeId;
    private final IntArray touched = new IntArray();
    private final FloatArray originals = new FloatArray();

    private final IntArray affected = new IntArray();
    private final FloatArray weights = new FloatArray();
    private final FloatArray results = new FloatArray();
    private final IntArray moved = new IntArray();

    private final int[] grabStamp;
    private final IntArray grabVertices = new IntArray();
    private final FloatArray grabWeights = new FloatArray();
    private final IntArray grabMasks = new IntArray();
    private final FloatArray grabOrigins = new FloatArray();

    /** Creates a stroke engine for a surface. */
    public SculptStroke(SculptSurface surface) {
        this.surface = Objects.requireNonNull(surface, "surface cannot be null");
        this.mesh = surface.getMesh();
        this.touchedStamp = new int[mesh.getVertexCount()];
        this.grabStamp = new int[mesh.getVertexCount()];
    }

    /** True between {@link #begin} and {@link #end} or {@link #cancel}. */
    public boolean isActive() {
        return active;
    }

    /**
     * Number of dab positions placed along the current or last stroke, counting the first, whether or not any vertex
     * was under the brush (a mirrored copy of a dab is not counted again), or the number of grab moves.
     */
    public int getDabCount() {
        return dabCount;
    }

    /**
     * Starts a stroke at a surface point. Dab brushes apply their first dab right away; grab picks up the vertices.
     *
     * @param settings the brush settings, copied at this moment
     * @param symmetry the mirror planes, copied at this moment
     * @param x        the surface point
     * @param nx       the surface normal at the point; it need not be exactly unit length
     * @throws IllegalStateException    if a stroke is already in progress
     * @throws IllegalArgumentException if the normal has no length or a value is not finite
     */
    public void begin(BrushType type, BrushSettings settings, SymmetrySettings symmetry,
                      float x, float y, float z, float nx, float ny, float nz) {
        if (active) {
            throw new IllegalStateException("A stroke is already in progress");
        }
        this.type = Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(settings, "settings cannot be null");
        Objects.requireNonNull(symmetry, "symmetry cannot be null");
        requireFinite(x, y, z, "point");
        float length = normalLength(nx, ny, nz);
        radius = settings.getRadius();
        strength = settings.getStrength();
        spacing = settings.getSpacing();
        falloff = settings.getFalloff();
        subtract = settings.isSubtract();
        mirrorMask = symmetry.axisMask();
        if (++strokeId == Integer.MAX_VALUE) {
            Arrays.fill(touchedStamp, 0);
            Arrays.fill(grabStamp, 0);
            strokeId = 1;
        }
        touched.clear();
        originals.clear();
        grabVertices.clear();
        grabWeights.clear();
        grabMasks.clear();
        grabOrigins.clear();
        carry = 0f;
        dabCount = 0;
        lastX = startX = x;
        lastY = startY = y;
        lastZ = startZ = z;
        lastNx = nx / length;
        lastNy = ny / length;
        lastNz = nz / length;
        active = true;
        if (type == BrushType.GRAB) {
            captureGrab(x, y, z);
        } else {
            applyDabSymmetric(x, y, z, lastNx, lastNy, lastNz);
        }
    }

    /**
     * Continues the stroke to a new surface point. Dab brushes place dabs at {@code spacing * radius} intervals along
     * the straight line from the previous point; grab moves its captured vertices by the total travel so far.
     */
    public void move(float x, float y, float z, float nx, float ny, float nz) {
        requireActive();
        requireFinite(x, y, z, "point");
        float length = normalLength(nx, ny, nz);
        float unitNx = nx / length;
        float unitNy = ny / length;
        float unitNz = nz / length;
        if (type == BrushType.GRAB) {
            moveGrab(x - startX, y - startY, z - startZ);
            return;
        }
        float dx = x - lastX;
        float dy = y - lastY;
        float dz = z - lastZ;
        float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance <= EPSILON) {
            return;
        }
        float step = spacing * radius;
        float next = step - carry;
        int placed = 0;
        while (next <= distance && placed < MAX_DABS_PER_MOVE) {
            float t = next / distance;
            float px = lastX + dx * t;
            float py = lastY + dy * t;
            float pz = lastZ + dz * t;
            float qx = lastNx + (unitNx - lastNx) * t;
            float qy = lastNy + (unitNy - lastNy) * t;
            float qz = lastNz + (unitNz - lastNz) * t;
            float qLength = (float) Math.sqrt(qx * qx + qy * qy + qz * qz);
            if (qLength > EPSILON) {
                qx /= qLength;
                qy /= qLength;
                qz /= qLength;
            } else {
                qx = unitNx;
                qy = unitNy;
                qz = unitNz;
            }
            applyDabSymmetric(px, py, pz, qx, qy, qz);
            next += step;
            placed++;
        }
        // When the cap was hit the remaining travel is dropped, so the stroke resumes from the new point
        carry = placed == MAX_DABS_PER_MOVE ? 0f : distance - (next - step);
        lastX = x;
        lastY = y;
        lastZ = z;
        lastNx = unitNx;
        lastNy = unitNy;
        lastNz = unitNz;
    }

    /**
     * Ends the stroke.
     *
     * @return what the stroke changed, to record for undo; empty if it touched nothing
     */
    public StrokeDelta end() {
        requireActive();
        active = false;
        float[] positions = mesh.getPositions();
        int count = touched.size;
        float[] after = new float[3 * count];
        for (int i = 0; i < count; i++) {
            int v = touched.items[i];
            after[3 * i] = positions[3 * v];
            after[3 * i + 1] = positions[3 * v + 1];
            after[3 * i + 2] = positions[3 * v + 2];
        }
        return new StrokeDelta(touched.toArray(), originals.toArray(), after);
    }

    /** Abandons the stroke and puts every touched vertex back where it was. Nothing is recorded for undo. */
    public void cancel() {
        requireActive();
        active = false;
        float[] positions = mesh.getPositions();
        moved.clear();
        for (int i = 0; i < touched.size; i++) {
            int v = touched.items[i];
            positions[3 * v] = originals.items[3 * i];
            positions[3 * v + 1] = originals.items[3 * i + 1];
            positions[3 * v + 2] = originals.items[3 * i + 2];
            moved.add(v);
        }
        surface.commitMoved(moved);
    }

    private void requireActive() {
        if (!active) {
            throw new IllegalStateException("No stroke is in progress; call begin first");
        }
    }

    private static void requireFinite(float x, float y, float z, String what) {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
            throw new IllegalArgumentException("The " + what + " must be finite: " + x + ", " + y + ", " + z);
        }
    }

    private static float normalLength(float nx, float ny, float nz) {
        requireFinite(nx, ny, nz, "normal");
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (!(length > EPSILON)) {
            throw new IllegalArgumentException("The normal has no length");
        }
        return length;
    }

    /** True if the mirrored copy {@code mask} coincides with another copy because the centre lies on a plane. */
    private boolean isDuplicateCopy(int mask, float cx, float cy, float cz) {
        float tolerance = MIRROR_PLANE_TOLERANCE * radius;
        return ((mask & 1) != 0 && Math.abs(cx) < tolerance)
            || ((mask & 2) != 0 && Math.abs(cy) < tolerance)
            || ((mask & 4) != 0 && Math.abs(cz) < tolerance);
    }

    private void applyDabSymmetric(float cx, float cy, float cz, float nx, float ny, float nz) {
        dabCount++;
        for (int mask = 0; mask < 8; mask++) {
            if ((mask & ~mirrorMask) != 0 || isDuplicateCopy(mask, cx, cy, cz)) {
                continue;
            }
            applyDab((mask & 1) != 0 ? -cx : cx, (mask & 2) != 0 ? -cy : cy, (mask & 4) != 0 ? -cz : cz,
                (mask & 1) != 0 ? -nx : nx, (mask & 2) != 0 ? -ny : ny, (mask & 4) != 0 ? -nz : nz);
        }
    }

    private void applyDab(float cx, float cy, float cz, float nx, float ny, float nz) {
        surface.getBvh().collectVertices(cx, cy, cz, radius, affected);
        int count = affected.size;
        if (count == 0) {
            return;
        }
        float[] positions = mesh.getPositions();
        float[] normals = mesh.getNormals();
        weights.clear();
        float weightSum = 0f;
        float centerX = 0f;
        float centerY = 0f;
        float centerZ = 0f;
        float normalX = 0f;
        float normalY = 0f;
        float normalZ = 0f;
        for (int i = 0; i < count; i++) {
            int v = affected.items[i];
            float dx = positions[3 * v] - cx;
            float dy = positions[3 * v + 1] - cy;
            float dz = positions[3 * v + 2] - cz;
            float w = falloff.weight((float) Math.sqrt(dx * dx + dy * dy + dz * dz) / radius);
            weights.add(w);
            weightSum += w;
            centerX += w * positions[3 * v];
            centerY += w * positions[3 * v + 1];
            centerZ += w * positions[3 * v + 2];
            normalX += w * normals[3 * v];
            normalY += w * normals[3 * v + 1];
            normalZ += w * normals[3 * v + 2];
        }
        if (weightSum <= EPSILON) {
            return;
        }
        centerX /= weightSum;
        centerY /= weightSum;
        centerZ /= weightSum;
        float normalLength = (float) Math.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
        if (normalLength > EPSILON) {
            normalX /= normalLength;
            normalY /= normalLength;
            normalZ /= normalLength;
        } else {
            normalX = nx;
            normalY = ny;
            normalZ = nz;
        }

        float sign = subtract ? -1f : 1f;
        float push = radius * DISPLACEMENT_PER_STRENGTH * strength * sign;
        int[] neighbors = mesh.getNeighbors();
        results.clear();
        for (int i = 0; i < count; i++) {
            int v = affected.items[i];
            float w = weights.items[i];
            float px = positions[3 * v];
            float py = positions[3 * v + 1];
            float pz = positions[3 * v + 2];
            float rx = px;
            float ry = py;
            float rz = pz;
            if (w > 0f) {
                switch (type) {
                    case DRAW -> {
                        rx += normalX * w * push;
                        ry += normalY * w * push;
                        rz += normalZ * w * push;
                    }
                    case INFLATE -> {
                        rx += normals[3 * v] * w * push;
                        ry += normals[3 * v + 1] * w * push;
                        rz += normals[3 * v + 2] * w * push;
                    }
                    case SMOOTH -> {
                        int start = mesh.getNeighborStart(v);
                        int end = mesh.getNeighborEnd(v);
                        if (end > start) {
                            float ax = 0f;
                            float ay = 0f;
                            float az = 0f;
                            for (int k = start; k < end; k++) {
                                int u = neighbors[k];
                                ax += positions[3 * u];
                                ay += positions[3 * u + 1];
                                az += positions[3 * u + 2];
                            }
                            float inverse = 1f / (end - start);
                            float amount = w * strength * SMOOTH_RATE;
                            rx += (ax * inverse - px) * amount;
                            ry += (ay * inverse - py) * amount;
                            rz += (az * inverse - pz) * amount;
                        }
                    }
                    case FLATTEN -> {
                        float offset = (px - centerX) * normalX + (py - centerY) * normalY + (pz - centerZ) * normalZ;
                        float amount = offset * w * strength * FLATTEN_RATE;
                        rx -= normalX * amount;
                        ry -= normalY * amount;
                        rz -= normalZ * amount;
                    }
                    case PINCH -> {
                        float toX = cx - px;
                        float toY = cy - py;
                        float toZ = cz - pz;
                        float along = toX * normalX + toY * normalY + toZ * normalZ;
                        float amount = w * strength * PINCH_RATE * sign;
                        float lift = w * push * PINCH_LIFT;
                        rx += (toX - normalX * along) * amount + normalX * lift;
                        ry += (toY - normalY * along) * amount + normalY * lift;
                        rz += (toZ - normalZ * along) * amount + normalZ * lift;
                    }
                    default -> throw new IllegalStateException("Not a dab brush: " + type);
                }
            }
            results.add(rx);
            results.add(ry);
            results.add(rz);
        }

        moved.clear();
        for (int i = 0; i < count; i++) {
            if (weights.items[i] <= 0f) {
                continue;
            }
            int v = affected.items[i];
            recordOriginal(v, positions);
            positions[3 * v] = results.items[3 * i];
            positions[3 * v + 1] = results.items[3 * i + 1];
            positions[3 * v + 2] = results.items[3 * i + 2];
            moved.add(v);
        }
        surface.commitMoved(moved);
    }

    /** Remembers where a vertex was the first time this stroke touches it. */
    private void recordOriginal(int v, float[] positions) {
        if (touchedStamp[v] == strokeId) {
            return;
        }
        touchedStamp[v] = strokeId;
        touched.add(v);
        originals.add(positions[3 * v]);
        originals.add(positions[3 * v + 1]);
        originals.add(positions[3 * v + 2]);
    }

    private void captureGrab(float cx, float cy, float cz) {
        float[] positions = mesh.getPositions();
        for (int mask = 0; mask < 8; mask++) {
            if ((mask & ~mirrorMask) != 0 || isDuplicateCopy(mask, cx, cy, cz)) {
                continue;
            }
            float mx = (mask & 1) != 0 ? -cx : cx;
            float my = (mask & 2) != 0 ? -cy : cy;
            float mz = (mask & 4) != 0 ? -cz : cz;
            surface.getBvh().collectVertices(mx, my, mz, radius, affected);
            for (int i = 0; i < affected.size; i++) {
                int v = affected.items[i];
                if (grabStamp[v] == strokeId) {
                    continue;
                }
                float dx = positions[3 * v] - mx;
                float dy = positions[3 * v + 1] - my;
                float dz = positions[3 * v + 2] - mz;
                float w = falloff.weight((float) Math.sqrt(dx * dx + dy * dy + dz * dz) / radius);
                if (w <= 0f) {
                    continue;
                }
                grabStamp[v] = strokeId;
                grabVertices.add(v);
                grabWeights.add(w);
                grabMasks.add(mask);
                grabOrigins.add(positions[3 * v]);
                grabOrigins.add(positions[3 * v + 1]);
                grabOrigins.add(positions[3 * v + 2]);
                recordOriginal(v, positions);
            }
        }
    }

    private void moveGrab(float dx, float dy, float dz) {
        if (grabVertices.size == 0) {
            return;
        }
        float[] positions = mesh.getPositions();
        for (int i = 0; i < grabVertices.size; i++) {
            int v = grabVertices.items[i];
            int mask = grabMasks.items[i];
            float w = grabWeights.items[i];
            positions[3 * v] = grabOrigins.items[3 * i] + ((mask & 1) != 0 ? -dx : dx) * w;
            positions[3 * v + 1] = grabOrigins.items[3 * i + 1] + ((mask & 2) != 0 ? -dy : dy) * w;
            positions[3 * v + 2] = grabOrigins.items[3 * i + 2] + ((mask & 4) != 0 ? -dz : dz) * w;
        }
        surface.commitMoved(grabVertices);
        dabCount++;
    }
}
