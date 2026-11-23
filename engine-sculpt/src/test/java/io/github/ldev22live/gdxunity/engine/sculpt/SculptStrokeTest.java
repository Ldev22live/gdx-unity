package io.github.ldev22live.gdxunity.engine.sculpt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SculptStrokeTest {
    private static final float RADIUS = 0.3f;

    private SculptMesh mesh;
    private SculptSurface surface;
    private SculptStroke stroke;
    private BrushSettings brush;
    private SymmetrySettings symmetry;

    @BeforeEach
    void setUp() {
        useMesh(SculptTestSupport.sphere(24));
        brush = new BrushSettings();
        brush.setRadius(RADIUS);
        brush.setStrength(1f);
        brush.setSpacing(0.25f);
        symmetry = new SymmetrySettings();
    }

    private void useMesh(SculptMesh newMesh) {
        mesh = newMesh;
        surface = new SculptSurface(mesh);
        stroke = new SculptStroke(surface);
    }

    private StrokeDelta singleDab(BrushType type, float x, float y, float z) {
        stroke.begin(type, brush, symmetry, x, y, z, x, y, z);
        return stroke.end();
    }

    @Test
    void shouldPushTheSurfaceOutUnderADrawBrushAndLeaveTheRestAlone() {
        float[] before = mesh.getPositions().clone();

        StrokeDelta delta = singleDab(BrushType.DRAW, 0f, 0f, 1f);

        int near = 0;
        int touchedExpected = 0;
        for (int v = 0; v < mesh.getVertexCount(); v++) {
            float d = SculptTestSupport.distance(before, v, 0f, 0f, 1f);
            if (d >= RADIUS) {
                for (int axis = 0; axis < 3; axis++) {
                    assertEquals(before[3 * v + axis], mesh.getPositions()[3 * v + axis],
                        "vertex " + v + " is outside the brush and must not move");
                }
            } else {
                touchedExpected++;
            }
            if (d < RADIUS / 2) {
                near++;
                float after = SculptTestSupport.radius(mesh, v);
                assertTrue(after > 1.002f, "vertex " + v + " near the centre was pushed out: " + after);
            }
        }
        assertTrue(near > 5, "the test brush covers several vertices");
        // The brush and this test measure the radius with different rounding, so allow a vertex on the edge
        assertTrue(delta.size() <= touchedExpected + 2 && delta.size() > touchedExpected / 2,
            "the delta lists the touched vertices: " + delta.size() + " of " + touchedExpected);
    }

    @Test
    void shouldLeaveEveryVertexOutsideTheBrushUntouchedForEveryBrush() {
        for (BrushType type : BrushType.values()) {
            for (boolean subtract : new boolean[] {false, true}) {
                useMesh(SculptTestSupport.sphere(24));
                brush.setSubtract(subtract);
                float[] before = mesh.getPositions().clone();

                singleDab(type, 0f, 0f, 1f);

                int moved = 0;
                for (int v = 0; v < mesh.getVertexCount(); v++) {
                    boolean changed = false;
                    for (int axis = 0; axis < 3; axis++) {
                        changed |= before[3 * v + axis] != mesh.getPositions()[3 * v + axis];
                    }
                    if (changed) {
                        moved++;
                        assertTrue(SculptTestSupport.distance(before, v, 0f, 0f, 1f) < RADIUS,
                            type + (subtract ? " (subtract)" : "") + " moved vertex " + v + " outside its radius");
                    }
                }
                assertTrue(moved > 0 || type == BrushType.GRAB, type + " moved something inside its radius");
            }
        }
    }

    @Test
    void shouldPushTheSurfaceInWhenSubtracting() {
        brush.setSubtract(true);

        singleDab(BrushType.DRAW, 0f, 0f, 1f);

        int top = nearestVertex(0f, 0f, 1f);
        assertTrue(SculptTestSupport.radius(mesh, top) < 0.998f);
    }

    @Test
    void shouldUndoAndRedoAStrokeExactly() {
        float[] original = mesh.getPositions().clone();

        StrokeDelta delta = singleDab(BrushType.DRAW, 0f, 0f, 1f);
        float[] sculpted = mesh.getPositions().clone();
        assertFalse(Arrays.equals(original, sculpted));
        long revision = surface.getRevision();

        surface.apply(delta, false);
        assertArrayEquals(original, mesh.getPositions(), "undo restores every position exactly");
        SculptTestSupport.assertNormalsMatchFullRecompute(mesh);
        surface.apply(delta, true);
        assertArrayEquals(sculpted, mesh.getPositions(), "redo restores the sculpted positions exactly");
        SculptTestSupport.assertNormalsMatchFullRecompute(mesh);
        SculptTestSupport.assertBvhMatchesFreshBuild(surface);
        assertTrue(surface.getRevision() > revision, "every change bumps the revision");
    }

    @Test
    void shouldUndoAStrokeOfManyDabsAndMirroredDabsExactly() {
        symmetry.setMirrorX(true);
        float[] original = mesh.getPositions().clone();

        // Dabs along the path overlap, so most vertices are touched many times; undo must still restore the first state
        stroke.begin(BrushType.DRAW, brush, symmetry, 0.6f, 0.2f, 0.75f, 0.6f, 0.2f, 0.75f);
        for (int i = 1; i <= 8; i++) {
            stroke.move(0.6f - 0.05f * i, 0.2f, 0.75f + 0.03f * i, 0.5f, 0.2f, 0.8f);
        }
        StrokeDelta delta = stroke.end();
        float[] sculpted = mesh.getPositions().clone();
        assertTrue(stroke.getDabCount() > 5, "a long stroke has many overlapping dabs: " + stroke.getDabCount());
        Set<Integer> listed = new HashSet<>();
        for (int vertex : delta.vertices()) {
            assertTrue(listed.add(vertex), "vertex " + vertex + " appears once in the delta however often it was hit");
        }

        surface.apply(delta, false);
        assertArrayEquals(original, mesh.getPositions(), "undo restores the state before the whole stroke");
        surface.apply(delta, true);
        assertArrayEquals(sculpted, mesh.getPositions(), "redo restores the state after the whole stroke");
        SculptTestSupport.assertNormalsMatchFullRecompute(mesh);
        SculptTestSupport.assertBvhMatchesFreshBuild(surface);
    }

    @Test
    void shouldKeepNormalsAndHierarchyCorrectAfterEveryKindOfBrush() {
        symmetry.setMirrorX(true);
        brush.setRadius(0.35f);
        for (BrushType type : BrushType.values()) {
            for (boolean subtract : new boolean[] {false, true}) {
                brush.setSubtract(subtract);
                stroke.begin(type, brush, symmetry, 0.5f, 0.5f, 0.7f, 0.5f, 0.5f, 0.7f);
                for (int i = 1; i <= 6; i++) {
                    float a = 0.15f * i;
                    stroke.move((float) Math.sin(a) * 0.9f + 0.3f, 0.4f, (float) Math.cos(a) * 0.9f, 0.5f, 0.3f, 0.8f);
                }
                stroke.end();

                SculptTestSupport.assertNormalsMatchFullRecompute(mesh);
                SculptTestSupport.assertBvhMatchesFreshBuild(surface);
            }
        }
    }

    @Test
    void shouldPlaceDabsByDistanceNotByMouseEvents() {
        brush.setRadius(0.25f);
        brush.setSpacing(0.25f); // a dab every 0.0625, which is exact in binary floating point
        stroke.begin(BrushType.DRAW, brush, symmetry, 0f, 0f, 1f, 0f, 0f, 1f);
        assertEquals(1, stroke.getDabCount(), "the first dab is placed when the stroke begins");

        stroke.move(1.01f, 0f, 1f, 0f, 0f, 1f);

        assertEquals(1 + 16, stroke.getDabCount(), "1.01 units at 0.0625 per dab is 16 dabs, plus the first");
        stroke.end();
    }

    @Test
    void shouldPlaceTheSameDabsWhetherTheCursorReportsOnceOrManyTimes() {
        SculptMesh a = SculptTestSupport.sphere(24);
        SculptMesh b = SculptTestSupport.sphere(24);
        brush.setRadius(0.25f);
        brush.setSpacing(0.25f);
        SculptStroke onceStroke = new SculptStroke(new SculptSurface(a));
        onceStroke.begin(BrushType.DRAW, brush, symmetry, -0.5f, 0f, 1f, 0f, 0f, 1f);
        onceStroke.move(0.51f, 0f, 1f, 0f, 0f, 1f);
        onceStroke.end();
        SculptStroke manyStroke = new SculptStroke(new SculptSurface(b));
        manyStroke.begin(BrushType.DRAW, brush, symmetry, -0.5f, 0f, 1f, 0f, 0f, 1f);
        for (int i = 1; i <= 10; i++) {
            manyStroke.move(-0.5f + 1.01f * i / 10f, 0f, 1f, 0f, 0f, 1f);
        }
        manyStroke.end();

        assertEquals(onceStroke.getDabCount(), manyStroke.getDabCount());
        assertArrayEquals(a.getPositions(), b.getPositions(), 1e-4f);
    }

    @Test
    void shouldLimitTheDabsOfOneHugeJump() {
        brush.setSpacing(BrushSettings.MIN_SPACING);
        stroke.begin(BrushType.DRAW, brush, symmetry, 0f, 0f, 1f, 0f, 0f, 1f);

        stroke.move(1000f, 0f, 1f, 0f, 0f, 1f);

        assertEquals(1 + SculptStroke.MAX_DABS_PER_MOVE, stroke.getDabCount());
        stroke.end();
    }

    @Test
    void shouldRepeatDabsOnTheMirroredSide() {
        symmetry.setMirrorX(true);
        float x = 0.8f;
        float y = 0.2f;
        float z = (float) Math.sqrt(1 - x * x - y * y);

        singleDab(BrushType.DRAW, x, y, z);

        Map<String, Integer> byPosition = new HashMap<>();
        float[] unmoved = SculptTestSupport.sphere(24).getPositions();
        for (int v = 0; v < mesh.getVertexCount(); v++) {
            byPosition.put(key(unmoved[3 * v], unmoved[3 * v + 1], unmoved[3 * v + 2]), v);
        }
        int checked = 0;
        for (int v = 0; v < mesh.getVertexCount(); v++) {
            Integer mirror = byPosition.get(key(-unmoved[3 * v], unmoved[3 * v + 1], unmoved[3 * v + 2]));
            assertTrue(mirror != null, "the sphere has a mirror vertex for " + v);
            assertEquals(SculptTestSupport.radius(mesh, v), SculptTestSupport.radius(mesh, mirror), 1e-5f);
            if (SculptTestSupport.radius(mesh, v) > 1.002f) {
                checked++;
            }
        }
        assertTrue(checked > 10, "both sides were pushed out: " + checked + " vertices raised");
        // The control: without symmetry only one side is raised
        useMesh(SculptTestSupport.sphere(24));
        symmetry.setMirrorX(false);
        singleDab(BrushType.DRAW, x, y, z);
        assertNotEquals(SculptTestSupport.radius(mesh, nearestVertex(x, y, z)),
            SculptTestSupport.radius(mesh, nearestVertex(-x, y, z)), 1e-3f);
    }

    private static String key(float x, float y, float z) {
        return Math.round(x * 1e4f) + "," + Math.round(y * 1e4f) + "," + Math.round(z * 1e4f);
    }

    @Test
    void shouldNotApplyTheMirroredCopyTwiceWhenTheDabIsOnTheMirrorPlane() {
        SculptMesh plain = SculptTestSupport.sphere(24);
        SculptStroke plainStroke = new SculptStroke(new SculptSurface(plain));
        plainStroke.begin(BrushType.DRAW, brush, new SymmetrySettings(), 0f, 0f, 1f, 0f, 0f, 1f);
        plainStroke.end();

        symmetry = new SymmetrySettings(true, false, false);
        singleDab(BrushType.DRAW, 0f, 0f, 1f);

        assertArrayEquals(plain.getPositions(), mesh.getPositions(), 0f,
            "a dab on the mirror plane is applied once, as without symmetry");
    }

    @Test
    void shouldSmoothOutNoise() {
        Random random = new Random(7);
        float[] noisy = SculptTestSupport.sphere(24).getPositions().clone();
        for (int v = 0; v < noisy.length / 3; v++) {
            float scale = 1f + (random.nextFloat() - 0.5f) * 0.06f;
            noisy[3 * v] *= scale;
            noisy[3 * v + 1] *= scale;
            noisy[3 * v + 2] *= scale;
        }
        useMesh(new SculptMesh(noisy, SculptTestSupport.sphere(24).getTriangles()));
        float before = radiusSpread(0f, 0f, 1f, 0.4f);
        brush.setRadius(0.5f);

        stroke.begin(BrushType.SMOOTH, brush, symmetry, 0f, 0f, 1f, 0f, 0f, 1f);
        for (int i = 0; i < 12; i++) {
            float a = i * 0.5f;
            stroke.move(0.1f * (float) Math.cos(a), 0.1f * (float) Math.sin(a), 1f, 0f, 0f, 1f);
        }
        stroke.end();

        float after = radiusSpread(0f, 0f, 1f, 0.4f);
        assertTrue(after < before * 0.5f, "spread of radii fell from " + before + " to " + after);
        SculptTestSupport.assertNormalsMatchFullRecompute(mesh);
    }

    /** Standard deviation of the radius of vertices near a point. */
    private float radiusSpread(float x, float y, float z, float within) {
        float sum = 0f;
        float sumSquares = 0f;
        int count = 0;
        for (int v = 0; v < mesh.getVertexCount(); v++) {
            if (SculptTestSupport.distance(mesh.getPositions(), v, x, y, z) < within) {
                float r = SculptTestSupport.radius(mesh, v);
                sum += r;
                sumSquares += r * r;
                count++;
            }
        }
        float mean = sum / count;
        return (float) Math.sqrt(Math.max(0f, sumSquares / count - mean * mean));
    }

    @Test
    void shouldFlattenAnAreaOntoItsAveragePlane() {
        brush.setRadius(0.6f);
        brush.setSpacing(0.02f); // a dab every 0.012, so the short path below places several dabs
        float before = zSpread(0.5f);
        stroke.begin(BrushType.FLATTEN, brush, symmetry, 0f, 0f, 1f, 0f, 0f, 1f);
        for (int i = 0; i < 4; i++) {
            stroke.move(0.02f * i, 0f, 1f, 0f, 0f, 1f);
        }
        stroke.end();

        float after = zSpread(0.5f);
        assertTrue(after < before * 0.7f, "height spread fell from " + before + " to " + after);

        // More dabs flatten further
        stroke.begin(BrushType.FLATTEN, brush, symmetry, 0f, 0f, 1f, 0f, 0f, 1f);
        for (int i = 0; i < 4; i++) {
            stroke.move(0.02f * i, 0f, 1f, 0f, 0f, 1f);
        }
        stroke.end();
        assertTrue(zSpread(0.5f) < after, "a second stroke flattens further: " + after + " -> " + zSpread(0.5f));
    }

    private float zSpread(float within) {
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (int v = 0; v < mesh.getVertexCount(); v++) {
            float x = mesh.getPositions()[3 * v];
            float y = mesh.getPositions()[3 * v + 1];
            if (Math.sqrt(x * x + y * y) < within && mesh.getPositions()[3 * v + 2] > 0f) {
                min = Math.min(min, mesh.getPositions()[3 * v + 2]);
                max = Math.max(max, mesh.getPositions()[3 * v + 2]);
            }
        }
        return max - min;
    }

    @Test
    void shouldInflateAlongEachVertexNormal() {
        float[] before = mesh.getPositions().clone();

        singleDab(BrushType.INFLATE, 0f, 0f, 1f);

        for (int v = 0; v < mesh.getVertexCount(); v++) {
            float d = SculptTestSupport.distance(before, v, 0f, 0f, 1f);
            float after = SculptTestSupport.radius(mesh, v);
            if (d < RADIUS * 0.9f) {
                assertTrue(after > 1.0001f, "vertex " + v + " swelled: " + after);
            } else if (d >= RADIUS) {
                assertEquals(1f, after, 1e-5f, "vertex " + v + " is outside the brush");
            }
        }
    }

    @Test
    void shouldPinchTheSurfaceTowardsTheBrushCentreAndSpreadItWhenSubtracting() {
        float before = meanDistanceFromAxis(0.28f);

        singleDab(BrushType.PINCH, 0f, 0f, 1f);
        float pinched = meanDistanceFromAxis(0.28f);
        useMesh(SculptTestSupport.sphere(24));
        brush.setSubtract(true);
        singleDab(BrushType.PINCH, 0f, 0f, 1f);
        float spread = meanDistanceFromAxis(0.28f);

        assertTrue(pinched < before, "pinch moved vertices closer to the axis: " + before + " -> " + pinched);
        assertTrue(spread > before, "subtract moved them away: " + before + " -> " + spread);
    }

    @Test
    void shouldLiftTheSurfaceOutwardWhenPinchingAndLowerItWhenSubtracting() {
        int top = nearestVertexTo(0f, 0f, 1f);
        float before = SculptTestSupport.radius(mesh, top);

        singleDab(BrushType.PINCH, 0f, 0f, 1f);
        float pinched = SculptTestSupport.radius(mesh, top);
        useMesh(SculptTestSupport.sphere(24));
        brush.setSubtract(true);
        singleDab(BrushType.PINCH, 0f, 0f, 1f);
        float spread = SculptTestSupport.radius(mesh, top);

        assertTrue(pinched > before + 1e-4f, "pinch raised the crease: " + before + " -> " + pinched);
        assertTrue(spread < before - 1e-4f, "subtract lowered it: " + before + " -> " + spread);
    }

    private int nearestVertexTo(float x, float y, float z) {
        int nearest = 0;
        for (int v = 1; v < mesh.getVertexCount(); v++) {
            if (SculptTestSupport.distance(mesh.getPositions(), v, x, y, z)
                < SculptTestSupport.distance(mesh.getPositions(), nearest, x, y, z)) {
                nearest = v;
            }
        }
        return nearest;
    }

    private float meanDistanceFromAxis(float within) {
        float[] reference = SculptTestSupport.sphere(24).getPositions();
        float sum = 0f;
        int count = 0;
        for (int v = 0; v < mesh.getVertexCount(); v++) {
            if (SculptTestSupport.distance(reference, v, 0f, 0f, 1f) < within) {
                sum += (float) Math.hypot(mesh.getPositions()[3 * v], mesh.getPositions()[3 * v + 1]);
                count++;
            }
        }
        return sum / count;
    }

    @Test
    void shouldDragTheVerticesUnderTheBrushWithAGrab() {
        float[] before = mesh.getPositions().clone();
        int top = nearestVertex(0f, 0f, 1f);

        stroke.begin(BrushType.GRAB, brush, symmetry, 0f, 0f, 1f, 0f, 0f, 1f);
        stroke.move(0f, 0.25f, 1f, 0f, 0f, 1f);
        stroke.move(0f, 0.5f, 1f, 0f, 0f, 1f);
        StrokeDelta delta = stroke.end();

        float moved = SculptTestSupport.distance(before, top, mesh.getPositions()[3 * top],
            mesh.getPositions()[3 * top + 1], mesh.getPositions()[3 * top + 2]);
        assertEquals(0.5f, moved, 0.05f, "a vertex under the centre follows the cursor almost fully");
        for (int v = 0; v < mesh.getVertexCount(); v++) {
            if (SculptTestSupport.distance(before, v, 0f, 0f, 1f) >= RADIUS) {
                assertEquals(before[3 * v + 1], mesh.getPositions()[3 * v + 1], "outside the brush: " + v);
            }
        }
        SculptTestSupport.assertNormalsMatchFullRecompute(mesh);
        SculptTestSupport.assertBvhMatchesFreshBuild(surface);
        surface.apply(delta, false);
        assertArrayEquals(before, mesh.getPositions(), "undoing a grab restores every position");
    }

    @Test
    void shouldGrabOnBothSidesWithSymmetry() {
        symmetry.setMirrorX(true);
        float[] before = mesh.getPositions().clone();
        float x = 0.8f;
        float z = (float) Math.sqrt(1 - x * x);

        stroke.begin(BrushType.GRAB, brush, symmetry, x, 0f, z, x, 0f, z);
        stroke.move(x + 0.2f, 0f, z, x, 0f, z);
        stroke.end();

        int right = nearestVertex(x, 0f, z);
        int left = nearestVertex(-x, 0f, z);
        assertEquals(0.2f, mesh.getPositions()[3 * right] - before[3 * right], 0.03f, "right side follows the cursor");
        assertEquals(-0.2f, mesh.getPositions()[3 * left] - before[3 * left], 0.03f, "left side moves the mirrored way");
    }

    @Test
    void shouldRestoreEverythingWhenAStrokeIsCancelled() {
        float[] before = mesh.getPositions().clone();
        long revision = surface.getRevision();

        stroke.begin(BrushType.DRAW, brush, symmetry, 0f, 0f, 1f, 0f, 0f, 1f);
        stroke.move(0.4f, 0f, 1f, 0f, 0f, 1f);
        stroke.cancel();

        assertFalse(stroke.isActive());
        assertArrayEquals(before, mesh.getPositions());
        SculptTestSupport.assertNormalsMatchFullRecompute(mesh);
        SculptTestSupport.assertBvhMatchesFreshBuild(surface);
        assertTrue(surface.getRevision() > revision);
    }

    @Test
    void shouldReturnAnEmptyDeltaWhenTheBrushIsNowhereNearTheSurface() {
        long revision = surface.getRevision();

        StrokeDelta delta = singleDab(BrushType.DRAW, 10f, 10f, 10f);

        assertTrue(delta.isEmpty());
        assertEquals(revision, surface.getRevision(), "nothing changed, so nothing to re-upload");
    }

    @Test
    void shouldUseTheSettingsFromTheStartOfTheStrokeEvenIfTheyChangeLater() {
        float[] before = mesh.getPositions().clone();
        stroke.begin(BrushType.DRAW, brush, symmetry, 0f, 0f, 1f, 0f, 0f, 1f);

        brush.setRadius(100f);
        stroke.move(0.5f, 0f, 1f, 0f, 0f, 1f);
        stroke.end();

        int bottom = nearestVertex(0f, 0f, -1f);
        assertEquals(before[3 * bottom + 2], mesh.getPositions()[3 * bottom + 2], "the far side was not touched");
    }

    @Test
    void shouldRefuseMisuseAndBadInput() {
        assertThrows(IllegalStateException.class, () -> stroke.move(0, 0, 1, 0, 0, 1), "move before begin");
        assertThrows(IllegalStateException.class, () -> stroke.end(), "end before begin");
        assertThrows(IllegalArgumentException.class,
            () -> stroke.begin(BrushType.DRAW, brush, symmetry, 0, 0, 1, 0, 0, 0), "a normal without length");
        assertThrows(IllegalArgumentException.class,
            () -> stroke.begin(BrushType.DRAW, brush, symmetry, Float.NaN, 0, 1, 0, 0, 1), "a point that is not finite");
        assertFalse(stroke.isActive(), "a rejected begin starts nothing");

        stroke.begin(BrushType.DRAW, brush, symmetry, 0, 0, 1, 0, 0, 1);
        assertThrows(IllegalStateException.class,
            () -> stroke.begin(BrushType.DRAW, brush, symmetry, 0, 0, 1, 0, 0, 1), "begin twice");
        assertThrows(IllegalArgumentException.class, () -> stroke.move(0, Float.POSITIVE_INFINITY, 1, 0, 0, 1));
        stroke.end();
        assertThrows(NullPointerException.class, () -> stroke.begin(null, brush, symmetry, 0, 0, 1, 0, 0, 1));
    }

    private int nearestVertex(float x, float y, float z) {
        int best = 0;
        float bestDistance = Float.MAX_VALUE;
        float[] reference = SculptTestSupport.sphere(24).getPositions();
        for (int v = 0; v < reference.length / 3; v++) {
            float d = SculptTestSupport.distance(reference, v, x, y, z);
            if (d < bestDistance) {
                bestDistance = d;
                best = v;
            }
        }
        return best;
    }
}
