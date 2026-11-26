package io.github.ldev22live.gdxunity.editor.sculpt;

import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.editor.sculpt.MeshPartEditor.*;
import io.github.ldev22live.gdxunity.engine.sculpt.StaticMeshImport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MeshPartEditorTest {
    @BeforeAll static void loadNatives() { GdxNativesLoader.load(); }

    private MeshPartEditor wall() throws IOException {
        return MeshPartEditor.open(StaticMeshImport.read(Path.of("..", "assets"),
            Path.of("thirdparty/kenney/building-kit/Models/GLB format/wall.glb"))).getFirst();
    }

    @Test void shouldRestoreEveryFaceOperationOnTheImportedWall() throws IOException {
        MeshPartEditor editor = wall();
        Geometry original = editor.geometry();
        Face face = original.faces().getFirst();
        for (Tool tool : Tool.values()) {
            float value = tool == Tool.Material ? (face.material() == -1 ? 0 : -1) : .25f;
            assertTrue(editor.apply(tool, Kind.Face, 0, value, 0, 0), tool.name());
            Geometry edited = editor.geometry();
            switch (tool) {
                case Material -> assertEquals((int)value, edited.faces().getFirst().material());
                case Translate -> assertEquals(original.vertices().get(face.a()).x() + value, edited.vertices().get(face.a()).x());
                case Delete -> assertEquals(original.faces().subList(1, original.faces().size()), edited.faces());
                default -> {
                    assertEquals(original.faces().size() + 6, edited.faces().size());
                    assertEquals(original.vertices().size() + 3, edited.vertices().size());
                    var a = original.vertices().get(face.a()).position();
                    var b = original.vertices().get(face.b()).position();
                    var c = original.vertices().get(face.c()).position();
                    var normal = b.cpy().sub(a).crs(c.cpy().sub(a)).nor();
                    var delta = edited.vertices().get(edited.faces().getFirst().a()).position().sub(a);
                    if (tool == Tool.Extrude) assertEquals(value, delta.dot(normal), .00001f);
                    if (tool == Tool.Inset) assertEquals(0f, delta.dot(normal), .00001f);
                    if (tool == Tool.Bevel) assertTrue(delta.dot(normal) > 0);
                }
            }
            assertTrue(editor.undo());
            assertEquals(original, editor.geometry());
            assertTrue(editor.redo());
            assertEquals(edited, editor.geometry());
            assertTrue(editor.undo());
        }
    }

    @Test void shouldTranslateOnlyTheSelectedEdgeOrVertexAndRejectInvalidEditsAtomically() throws IOException {
        MeshPartEditor editor = wall();
        Geometry original = editor.geometry();
        for (Kind kind : new Kind[]{Kind.Vertex, Kind.Edge}) {
            assertTrue(editor.apply(Tool.Translate, kind, 0, 1, 2, 3));
            Edge edge = original.edges().getFirst();
            for (int i = 0; i < original.vertices().size(); i++) {
                boolean moved = kind == Kind.Vertex ? i == 0 : i == edge.a() || i == edge.b();
                var expected = original.vertices().get(i).position();
                if (moved) expected.add(1, 2, 3);
                assertEquals(expected, editor.geometry().vertices().get(i).position());
            }
            assertTrue(editor.undo());
        }
        assertThrows(IllegalArgumentException.class, () -> editor.apply(Tool.Inset, Kind.Face, 0, 1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> editor.apply(Tool.Delete, Kind.Edge, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> editor.apply(Tool.Translate, Kind.Vertex, -1, 0, 0, 0));
        assertSame(original, editor.geometry());
        assertFalse(editor.undo());
        assertTrue(editor.redo());
    }
}
