package io.github.ldev22live.gdxunity.engine.sculpt;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeshChunksTest {
    private static final int SMALL_CHUNK = 300;

    private static int unsigned(short index) {
        return index & 0xFFFF;
    }

    @Test
    void shouldCoverEveryTriangleExactlyOnceAndReferToTheRightVertices() {
        SculptMesh mesh = SculptTestSupport.sphere(12);
        MeshChunks chunks = new MeshChunks(mesh, SMALL_CHUNK);

        int next = 0;
        for (int c = 0; c < chunks.getChunkCount(); c++) {
            assertEquals(next, chunks.getFirstTriangle(c), "chunks are consecutive triangle runs");
            assertEquals(3 * chunks.getTriangleCount(c), chunks.getIndexCount(c));
            for (int i = 0; i < chunks.getIndexCount(c); i++) {
                int t = chunks.getFirstTriangle(c) + i / 3;
                int local = unsigned(chunks.getIndices(c)[i]);
                assertTrue(local < chunks.getVertexCount(c), "index stays inside the chunk");
                assertEquals(mesh.getTriangles()[3 * t + i % 3], chunks.getGlobalVertex(c, local),
                    "chunk " + c + " index " + i + " names the triangle's own vertex");
            }
            next += chunks.getTriangleCount(c);
        }
        assertEquals(mesh.getTriangleCount(), next);
    }

    @Test
    void shouldKeepEveryChunkWithinTheVertexLimitAndUseSeveralChunksOnABigMesh() {
        SculptMesh mesh = SculptTestSupport.sphere(12);
        MeshChunks chunks = new MeshChunks(mesh, SMALL_CHUNK);

        assertTrue(chunks.getChunkCount() > 3, "a 1,730 vertex mesh does not fit in 300-vertex chunks");
        for (int c = 0; c < chunks.getChunkCount(); c++) {
            assertTrue(chunks.getVertexCount(c) <= SMALL_CHUNK, "chunk " + c + " has " + chunks.getVertexCount(c));
            assertTrue(chunks.getVertexCount(c) > 0);
        }
    }

    @Test
    void shouldUseASingleChunkWhenTheMeshFits() {
        MeshChunks chunks = new MeshChunks(SculptTestSupport.sphere(4));

        assertEquals(1, chunks.getChunkCount());
        assertEquals(98, chunks.getVertexCount(0));
    }

    @Test
    void shouldKeepTheDefaultChunkSizeWithin16BitIndices() {
        // 480,000 triangles: the dense meshes this tool is for
        MeshChunks chunks = new MeshChunks(SculptTestSupport.sphere(200));

        // Neighbouring triangles share vertices, so a 60,000 vertex chunk holds about 120,000 triangles
        assertTrue(chunks.getChunkCount() >= 4 && chunks.getChunkCount() <= 8, "chunks: " + chunks.getChunkCount());
        for (int c = 0; c < chunks.getChunkCount(); c++) {
            assertTrue(chunks.getVertexCount(c) <= 65_536, "chunk " + c + " fits 16-bit indices");
        }
    }

    @Test
    void shouldWritePositionsThenNormalsPerVertex() {
        SculptMesh mesh = SculptTestSupport.sphere(6);
        MeshChunks chunks = new MeshChunks(mesh, 100);
        float[] out = new float[100 * MeshChunks.FLOATS_PER_VERTEX];

        for (int c = 0; c < chunks.getChunkCount(); c++) {
            chunks.writeVertices(c, out);
            for (int local = 0; local < chunks.getVertexCount(c); local++) {
                int v = chunks.getGlobalVertex(c, local);
                for (int axis = 0; axis < 3; axis++) {
                    assertEquals(mesh.getPositions()[3 * v + axis], out[6 * local + axis]);
                    assertEquals(mesh.getNormals()[3 * v + axis], out[6 * local + 3 + axis]);
                }
            }
        }
    }

    @Test
    void shouldRejectBadArguments() {
        SculptMesh mesh = SculptTestSupport.sphere(2);
        assertThrows(IllegalArgumentException.class, () -> new MeshChunks(mesh, 2));
        assertThrows(IllegalArgumentException.class, () -> new MeshChunks(mesh, 65_537));
        assertThrows(NullPointerException.class, () -> new MeshChunks(null, 100));
        MeshChunks chunks = new MeshChunks(mesh);
        IllegalArgumentException small = assertThrows(IllegalArgumentException.class,
            () -> chunks.writeVertices(0, new float[5]));
        assertTrue(small.getMessage().contains("needs"), small.getMessage());
    }

    /** Whether a chunk's vertex data differs between two snapshots. */
    private static boolean differs(float[] a, float[] b) {
        return !Arrays.equals(a, b);
    }

    @Test
    void shouldChangeTheRevisionOfEveryChunkWhoseDataChangedAndOfFewOthers() {
        SculptSurface surface = new SculptSurface(SculptTestSupport.sphere(24), 400);
        MeshChunks chunks = surface.getChunks();
        SculptStroke stroke = new SculptStroke(surface);
        BrushSettings brush = new BrushSettings();
        brush.setRadius(0.25f);
        brush.setStrength(1f);
        int count = chunks.getChunkCount();
        assertTrue(count > 4, "enough chunks to tell touched from untouched: " + count);

        for (BrushType type : BrushType.values()) {
            float[][] data = new float[count][];
            long[] revisions = new long[count];
            for (int c = 0; c < count; c++) {
                data[c] = new float[MeshChunks.FLOATS_PER_VERTEX * chunks.getVertexCount(c)];
                chunks.writeVertices(c, data[c]);
                revisions[c] = chunks.getRevision(c);
            }

            stroke.begin(type, brush, new SymmetrySettings(), 0f, 0f, 1f, 0f, 0f, 1f);
            stroke.move(0.3f, 0.2f, 0.95f, 0.3f, 0.2f, 0.95f);
            stroke.end();

            int dataChanged = 0;
            int revisionChanged = 0;
            for (int c = 0; c < count; c++) {
                float[] now = new float[data[c].length];
                chunks.writeVertices(c, now);
                boolean changed = differs(data[c], now);
                boolean marked = chunks.getRevision(c) != revisions[c];
                assertTrue(!changed || marked, type + ": chunk " + c + " changed but was not marked, so it would not be re-uploaded");
                dataChanged += changed ? 1 : 0;
                revisionChanged += marked ? 1 : 0;
            }
            assertTrue(dataChanged > 0, type + " changed some chunk");
            assertTrue(revisionChanged < count, type + " marked every chunk (" + revisionChanged + " of " + count
                + "), so nothing is saved by chunking");
            assertTrue(revisionChanged <= dataChanged + 2, type + ": marked " + revisionChanged
                + " chunks for " + dataChanged + " that changed");
        }
    }

    @Test
    void shouldGiveANewRevisionToChunksChangedByUndo() {
        SculptSurface surface = new SculptSurface(SculptTestSupport.sphere(12), 400);
        MeshChunks chunks = surface.getChunks();
        SculptStroke stroke = new SculptStroke(surface);
        BrushSettings brush = new BrushSettings();
        brush.setStrength(1f);
        stroke.begin(BrushType.DRAW, brush, new SymmetrySettings(), 0f, 0f, 1f, 0f, 0f, 1f);
        StrokeDelta delta = stroke.end();
        long[] afterStroke = new long[chunks.getChunkCount()];
        for (int c = 0; c < afterStroke.length; c++) {
            afterStroke[c] = chunks.getRevision(c);
        }

        surface.apply(delta, false);

        boolean anyNewer = false;
        for (int c = 0; c < afterStroke.length; c++) {
            anyNewer |= chunks.getRevision(c) > afterStroke[c];
        }
        assertTrue(anyNewer, "undo marks the chunks it changed");
    }

    @Test
    void shouldStartWithRevisionZero() {
        MeshChunks chunks = new SculptSurface(SculptTestSupport.sphere(8), 200).getChunks();

        long[] revisions = new long[chunks.getChunkCount()];
        for (int c = 0; c < revisions.length; c++) {
            revisions[c] = chunks.getRevision(c);
        }
        assertArrayEquals(new long[revisions.length], revisions);
    }
}
