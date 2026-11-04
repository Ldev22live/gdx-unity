package io.github.ldev22live.gdxunity.editor.sculpt;

import io.github.ldev22live.gdxunity.editor.core.command.CommandHistory;
import io.github.ldev22live.gdxunity.engine.sculpt.BrushSettings;
import io.github.ldev22live.gdxunity.engine.sculpt.BrushType;
import io.github.ldev22live.gdxunity.engine.sculpt.SculptMeshFactory;
import io.github.ldev22live.gdxunity.engine.sculpt.SculptStroke;
import io.github.ldev22live.gdxunity.engine.sculpt.SculptSurface;
import io.github.ldev22live.gdxunity.engine.sculpt.StrokeDelta;
import io.github.ldev22live.gdxunity.engine.sculpt.SymmetrySettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SculptStrokeCommandTest {
    private final SculptSurface surface = new SculptSurface(SculptMeshFactory.cubeSphere(1f, 10));

    private StrokeDelta drawAtTheTop() {
        BrushSettings brush = new BrushSettings();
        brush.setRadius(0.4f);
        brush.setStrength(1f);
        SculptStroke stroke = new SculptStroke(surface);
        stroke.begin(BrushType.DRAW, brush, new SymmetrySettings(), 0f, 0f, 1f, 0f, 0f, 1f);
        return stroke.end();
    }

    @Test
    void shouldKeepTheSculptedShapeWhenRecordedAfterTheStrokeAndUndoAndRedoIt() {
        float[] original = surface.getMesh().getPositions().clone();
        StrokeDelta delta = drawAtTheTop();
        float[] sculpted = surface.getMesh().getPositions().clone();
        CommandHistory history = new CommandHistory();

        assertTrue(history.execute(new SculptStrokeCommand(surface, delta, "Sculpt: Draw")));
        assertArrayEquals(sculpted, surface.getMesh().getPositions(), "recording the stroke changes nothing visible");

        assertTrue(history.undo());
        assertArrayEquals(original, surface.getMesh().getPositions());
        assertTrue(history.redo());
        assertArrayEquals(sculpted, surface.getMesh().getPositions());
    }

    @Test
    void shouldDescribeItselfAndReportItsMemory() {
        StrokeDelta delta = drawAtTheTop();

        SculptStrokeCommand command = new SculptStrokeCommand(surface, delta, "Sculpt: Draw");

        assertEquals("Sculpt: Draw", command.getDescription());
        assertEquals(delta.memoryBytes(), command.memoryBytes());
    }

    @Test
    void shouldRefuseAnEmptyStrokeAndNullArguments() {
        StrokeDelta empty = new StrokeDelta(new int[0], new float[0], new float[0]);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> new SculptStrokeCommand(surface, empty, "x"));
        assertTrue(error.getMessage().contains("nothing to undo"), error.getMessage());
        assertThrows(NullPointerException.class, () -> new SculptStrokeCommand(null, empty, "x"));
        assertThrows(NullPointerException.class, () -> new SculptStrokeCommand(surface, null, "x"));
        assertThrows(NullPointerException.class, () -> new SculptStrokeCommand(surface, drawAtTheTop(), null));
    }
}
