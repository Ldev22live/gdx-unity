package io.github.ldev22live.gdxunity.engine.vector;

import io.github.ldev22live.gdxunity.engine.vector.VectorPath.Close;
import io.github.ldev22live.gdxunity.engine.vector.VectorPath.CubicTo;
import io.github.ldev22live.gdxunity.engine.vector.VectorPath.LineTo;
import io.github.ldev22live.gdxunity.engine.vector.VectorPath.MoveTo;
import io.github.ldev22live.gdxunity.engine.vector.VectorPath.QuadTo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathDataTest {
    @Test
    void shouldReadAbsoluteAndRelativeCommandsWithImplicitRepeats() {
        VectorPath path = PathData.parse("M10,10 20 10 l0 10 H5 v-5 h1 V0 L 1e1 0 z");
        assertEquals(List.of(new MoveTo(10, 10), new LineTo(20, 10), new LineTo(20, 20), new LineTo(5, 20), new LineTo(5, 15),
            new LineTo(6, 15), new LineTo(6, 0), new LineTo(10, 0), new Close()), path.segments());
    }

    @Test
    void shouldReflectControlPointsForSmoothCurves() {
        VectorPath path = PathData.parse("M0 0 C 0 10 10 10 10 0 S 20 -10 20 0 Q 25 5 30 0 T 40 0 t 10 0");
        assertEquals(new CubicTo(10, -10, 20, -10, 20, 0), path.segments().get(2), "S mirrors the previous second control point");
        assertEquals(new QuadTo(35, -5, 40, 0), path.segments().get(4), "T mirrors the previous quadratic control point");
        assertEquals(new QuadTo(45, 5, 50, 0), path.segments().get(5));
    }

    @Test
    void shouldTurnArcsIntoCubicsOnTheCircle() {
        VectorPath path = PathData.parse("M 0 0 A 10 10 0 0 1 20 0");
        assertTrue(path.segments().size() >= 3, "a half circle becomes at least two quarter cubics");
        assertInstanceOf(CubicTo.class, path.segments().get(1));
        CubicTo last = (CubicTo) path.segments().getLast();
        assertEquals(20f, last.x(), 1e-4f);
        assertEquals(0f, last.y(), 1e-4f);
        for (VectorPath.Polyline polyline : path.flatten()) {
            float[] xy = polyline.xy();
            for (int i = 0; i < xy.length; i += 2) {
                assertEquals(10.0, Math.hypot(xy[i] - 10, xy[i + 1]), 0.06, "every flattened point is on the circle");
                assertTrue(xy[i + 1] <= 1e-3, "sweep 1 is clockwise on screen, so left to right passes above (negative y): " + xy[i + 1]);
            }
        }
    }

    @Test
    void shouldScaleUpRadiiThatAreTooSmall() {
        VectorPath path = PathData.parse("M 0 0 A 1 1 0 0 0 20 0");
        float[] xy = path.flatten().getFirst().xy();
        assertEquals(20f, xy[xy.length - 2], 1e-3f);
        assertTrue(xy[3] > 0f, "sweep 0 goes the other way, below (positive y)");
    }

    @Test
    void shouldRoundTripThroughFormat() {
        VectorPath path = PathData.parse("M 0.5 -2 L 3 4 Q 1 2 3 4 C 1.25 2 3 4 5 6 Z M 10 10 L 11 11");
        assertEquals(path, PathData.parse(PathData.format(path)));
        assertEquals("M 0.5 -2 L 3 4 Q 1 2 3 4 C 1.25 2 3 4 5 6 Z M 10 10 L 11 11", PathData.format(path));
    }

    @Test
    void shouldRefuseMalformedDataNamingWhere() {
        IllegalArgumentException noMove = assertThrows(IllegalArgumentException.class, () -> PathData.parse("L 1 2"));
        assertTrue(noMove.getMessage().contains("starts with M"), noMove.getMessage());
        assertThrows(IllegalArgumentException.class, () -> PathData.parse("M 1"));
        assertThrows(IllegalArgumentException.class, () -> PathData.parse("M 0 0 Q 1"));
        assertThrows(IllegalArgumentException.class, () -> PathData.parse("M 0 0 A 1 1 0 2 0 3 3"), "an arc flag is 0 or 1");
        assertThrows(IllegalArgumentException.class, () -> PathData.parse("M 0 0 X 1 1"));
        assertThrows(IllegalArgumentException.class, () -> PathData.parse("12 3"));
    }

    @Test
    void shouldMoveOneEditablePoint() {
        VectorPath path = PathData.parse("M 0 0 C 1 2 3 4 5 6");
        assertEquals(4, path.points().size(), "a move's point and a cubic's three");
        VectorPath.PointRef secondControl = path.points().get(2);
        VectorPath moved = path.withPoint(secondControl, 9, 9);
        assertEquals(new CubicTo(1, 2, 9, 9, 5, 6), moved.segments().get(1));
        assertEquals(path.segments().getFirst(), moved.segments().getFirst());
        assertThrows(IllegalArgumentException.class, () -> path.withPoint(new VectorPath.PointRef(1, 3), 0, 0));
    }
}
