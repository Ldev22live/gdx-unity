package io.github.ldev22live.gdxunity.editor.sculpt;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.command.EditorCommand;
import io.github.ldev22live.gdxunity.editor.core.input.EditorKeyboardDispatcher;
import io.github.ldev22live.gdxunity.editor.core.viewport.ViewportInteraction;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.render.camera.PerspectiveCamera3D;
import io.github.ldev22live.gdxunity.engine.render.components.DynamicMeshComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import io.github.ldev22live.gdxunity.engine.sculpt.BrushType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SculptViewportInteractionTest {
    private static final float WIDTH = 800f;
    private static final float HEIGHT = 600f;
    private static final float SCULPT_RADIUS = 50f;

    /** Remembers what it was asked, so a test can tell what was passed on. */
    private static final class RecordingInteraction implements ViewportInteraction {
        final List<String> calls = new ArrayList<>();

        @Override
        public boolean touchDown(float x, float y, float w, float h, int button, int modifiers) {
            calls.add("touchDown " + button);
            return true;
        }

        @Override
        public boolean touchDragged(float x, float y, float w, float h) {
            calls.add("touchDragged");
            return true;
        }

        @Override
        public boolean touchUp(int button) {
            calls.add("touchUp " + button);
            return true;
        }

        @Override
        public boolean scrolled(float amountY) {
            calls.add("scrolled");
            return true;
        }

        @Override
        public Vector3 worldPointAt(float x, float y, float w, float h) {
            calls.add("worldPointAt");
            return new Vector3();
        }
    }

    @TempDir
    Path root;

    private EditorState editorState;
    private SculptSession session;
    private SculptDocument document;
    private Entity rock;
    private PerspectiveCamera3D camera;
    private RecordingInteraction normal;
    private SculptViewportInteraction interaction;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() throws IOException {
        SculptAssets assets = new SculptAssets(root.toFile());
        AssetReference asset = assets.create("Rock", SCULPT_RADIUS, 16);
        session = new SculptSession(assets);
        document = session.open(asset);
        editorState = new EditorState(new SceneSerializer());
        Scene scene = new Scene("Level");
        editorState.setActiveScene(scene);
        rock = scene.createEntity("Rock");
        rock.addComponent(new DynamicMeshComponent(asset.getPath()));
        editorState.setSelectedEntity(rock);
        camera = new PerspectiveCamera3D(60f, WIDTH, HEIGHT, 1f, 5000f);
        lookAtRockFrom(0f, 0f, 300f);
        normal = new RecordingInteraction();
        interaction = new SculptViewportInteraction(editorState, session, camera, normal);
        session.setSculptMode(true);
        session.getBrush().setRadius(20f);
        session.getBrush().setStrength(1f);
    }

    private void lookAtRockFrom(float x, float y, float z) {
        camera.setPosition(x, y, z);
        camera.setDirection(new Vector3(0, 0, -1));
        camera.update();
    }

    /** Presses, drags a little and releases the left button at the centre of the view. */
    private void stroke(int modifiers) {
        interaction.touchDown(WIDTH / 2, HEIGHT / 2, WIDTH, HEIGHT, Input.Buttons.LEFT, modifiers);
        interaction.touchDragged(WIDTH / 2 + 15f, HEIGHT / 2, WIDTH, HEIGHT);
        interaction.touchUp(Input.Buttons.LEFT);
    }

    /** The radius of the vertex nearest the top of the sculpt, in its own units. */
    private float topRadius() {
        float[] p = document.getSurface().getMesh().getPositions();
        int best = 0;
        for (int v = 0; v < p.length / 3; v++) {
            if (p[3 * v + 2] > p[3 * best + 2]) {
                best = v;
            }
        }
        return (float) Math.sqrt(p[3 * best] * p[3 * best] + p[3 * best + 1] * p[3 * best + 1] + p[3 * best + 2] * p[3 * best + 2]);
    }

    @Test
    void shouldSculptTheSelectedSculptAndRecordOneUndoStep() {
        float[] before = document.getSurface().getMesh().getPositions().clone();

        stroke(0);

        assertTrue(normal.calls.isEmpty(), "a stroke is not passed on to the normal interaction: " + normal.calls);
        assertTrue(topRadius() > SCULPT_RADIUS + 0.5f, "the draw brush pushed the top out: " + topRadius());
        assertTrue(editorState.getCommandHistory().canUndo());
        float[] sculpted = document.getSurface().getMesh().getPositions().clone();

        assertTrue(editorState.getCommandHistory().undo());
        assertArrayEquals(before, document.getSurface().getMesh().getPositions(), "one undo takes back the whole stroke");
        assertFalse(editorState.getCommandHistory().canUndo(), "and it was a single step");
        assertTrue(editorState.getCommandHistory().redo());
        assertArrayEquals(sculpted, document.getSurface().getMesh().getPositions());
        assertTrue(document.isModified());
    }

    @Test
    void shouldUseTheSmoothBrushWithShiftAndInvertWithControl() {
        stroke(EditorKeyboardDispatcher.MOD_SHIFT);
        float smoothed = topRadius();
        assertTrue(smoothed < SCULPT_RADIUS, "smoothing a sphere pulls its bumps in, here the top: " + smoothed);
        editorState.getCommandHistory().undo();

        stroke(EditorKeyboardDispatcher.MOD_CTRL);
        assertTrue(topRadius() < SCULPT_RADIUS - 0.5f, "ctrl inverts the draw brush into a dent: " + topRadius());
        editorState.getCommandHistory().undo();

        session.setBrushType(BrushType.INFLATE);
        stroke(0);
        assertTrue(topRadius() > SCULPT_RADIUS + 0.5f, "the selected brush is used");
    }

    @Test
    void shouldFindTheSurfaceOfAMovedAndScaledSculpt() {
        // Scaled by 2 the sculpt has a world radius of 100. A ray straight down at x = 60 meets it at world
        // z = 80, which is the point (30, 0, 40) of the sculpt's own space. In world space the same ray would miss
        // the unscaled sphere of radius 50 altogether, so only a ray moved into the sculpt's space can paint here.
        rock.getTransform().setLocalScale(2f, 2f, 2f);
        lookAtRockFrom(60f, 0f, 500f);
        float[] before = document.getSurface().getMesh().getPositions().clone();
        int target = nearestVertex(before, 30f, 0f, 40f);
        int top = nearestVertex(before, 0f, 0f, 50f);

        stroke(0);

        assertTrue(normal.calls.isEmpty(), "the click hit the sculpt, so it was a stroke: " + normal.calls);
        assertTrue(radiusOf(target) > radiusBefore(before, target) + 0.5f, "the point under the cursor was pushed out");
        assertEquals(radiusBefore(before, top), radiusOf(top), 1e-3f, "the top, 30 units away from it, was not touched");
    }

    @Test
    void shouldFindTheSurfaceOfASculptMovedAwayFromTheOrigin() {
        rock.getTransform().setLocalPosition(80f, 0f, 0f);
        lookAtRockFrom(80f, 0f, 300f);

        stroke(0);

        assertTrue(normal.calls.isEmpty(), "a ray through the sculpt's own centre starts a stroke: " + normal.calls);
        assertTrue(topRadius() > SCULPT_RADIUS + 0.5f);
    }

    private static int nearestVertex(float[] positions, float x, float y, float z) {
        int best = 0;
        float bestDistance = Float.MAX_VALUE;
        for (int v = 0; v < positions.length / 3; v++) {
            float dx = positions[3 * v] - x;
            float dy = positions[3 * v + 1] - y;
            float dz = positions[3 * v + 2] - z;
            float distance = dx * dx + dy * dy + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = v;
            }
        }
        return best;
    }

    private static float radiusBefore(float[] positions, int v) {
        return (float) Math.sqrt(positions[3 * v] * positions[3 * v] + positions[3 * v + 1] * positions[3 * v + 1]
            + positions[3 * v + 2] * positions[3 * v + 2]);
    }

    private float radiusOf(int v) {
        return radiusBefore(document.getSurface().getMesh().getPositions(), v);
    }

    @Test
    void shouldPassEverythingElseToTheNormalInteraction() {
        session.setSculptMode(false);
        interaction.touchDown(WIDTH / 2, HEIGHT / 2, WIDTH, HEIGHT, Input.Buttons.LEFT, 0);
        assertEquals(List.of("touchDown " + Input.Buttons.LEFT), normal.calls, "sculpt mode is off");
        interaction.touchUp(Input.Buttons.LEFT);
        normal.calls.clear();
        session.setSculptMode(true);

        interaction.touchDown(WIDTH / 2, HEIGHT / 2, WIDTH, HEIGHT, Input.Buttons.RIGHT, 0);
        interaction.touchUp(Input.Buttons.RIGHT);
        interaction.touchDown(WIDTH / 2, HEIGHT / 2, WIDTH, HEIGHT, Input.Buttons.MIDDLE, 0);
        interaction.touchUp(Input.Buttons.MIDDLE);
        interaction.touchDown(WIDTH / 2, HEIGHT / 2, WIDTH, HEIGHT, Input.Buttons.LEFT, EditorKeyboardDispatcher.MOD_ALT);
        interaction.touchUp(Input.Buttons.LEFT);
        interaction.scrolled(1f);
        interaction.worldPointAt(1f, 1f, WIDTH, HEIGHT);

        assertEquals(List.of("touchDown 1", "touchUp 1", "touchDown 2", "touchUp 2", "touchDown 0", "touchUp 0",
            "scrolled", "worldPointAt"), normal.calls, "camera buttons, alt-orbit, scrolling and queries are not strokes");
        assertFalse(editorState.getCommandHistory().canUndo());
    }

    @Test
    void shouldLetAClickThatMissesTheSculptSelectOrDoWhatTheViewDoes() {
        interaction.touchDown(5f, 5f, WIDTH, HEIGHT, Input.Buttons.LEFT, 0);

        assertEquals(List.of("touchDown 0"), normal.calls);
        assertFalse(interaction.isStroking());
        interaction.touchDragged(6f, 6f, WIDTH, HEIGHT);
        interaction.touchUp(Input.Buttons.LEFT);
        assertEquals(List.of("touchDown 0", "touchDragged", "touchUp 0"), normal.calls,
            "the rest of that gesture also goes to the normal interaction");
    }

    @Test
    void shouldNotSculptWithoutASelectedSculpt() {
        editorState.clearSelection();
        interaction.touchDown(WIDTH / 2, HEIGHT / 2, WIDTH, HEIGHT, Input.Buttons.LEFT, 0);
        assertEquals(List.of("touchDown 0"), normal.calls, "nothing selected");
        interaction.touchUp(Input.Buttons.LEFT);
        normal.calls.clear();

        Entity plain = editorState.getActiveScene().createEntity("Plain");
        editorState.setSelectedEntity(plain);
        interaction.touchDown(WIDTH / 2, HEIGHT / 2, WIDTH, HEIGHT, Input.Buttons.LEFT, 0);

        assertEquals(List.of("touchDown 0"), normal.calls, "the selected entity has no sculpt");
    }

    @Test
    void shouldDiscardAStrokeTheEditorRefusesSoTheSurfaceNeverDiffersFromTheUndoStack() {
        EditorState refusing = new EditorState(new SceneSerializer()) {
            @Override
            public boolean executeCommand(EditorCommand command) {
                return false;
            }
        };
        refusing.setActiveScene(editorState.getActiveScene());
        refusing.setSelectedEntity(rock);
        interaction = new SculptViewportInteraction(refusing, session, camera, normal);
        float[] before = document.getSurface().getMesh().getPositions().clone();

        stroke(0);

        assertArrayEquals(before, document.getSurface().getMesh().getPositions(), "the stroke was taken back");
        assertFalse(refusing.getCommandHistory().canUndo());
    }

    @Test
    void shouldReportTheBrushPositionOnlyWhileStroking() {
        Vector3 center = new Vector3();
        Vector3 normalWorld = new Vector3();
        assertFalse(interaction.getBrushIndicator(center, normalWorld));

        interaction.touchDown(WIDTH / 2, HEIGHT / 2, WIDTH, HEIGHT, Input.Buttons.LEFT, 0);
        boolean stroking = interaction.getBrushIndicator(center, normalWorld);
        interaction.touchUp(Input.Buttons.LEFT);

        assertTrue(stroking);
        assertEquals(SCULPT_RADIUS, center.z, 2f, "the brush is on the front of the sculpt");
        assertEquals(1f, normalWorld.len(), 1e-4f);
        assertTrue(normalWorld.z > 0.9f, "pointing at the camera");
        assertFalse(interaction.getBrushIndicator(center, normalWorld));
    }
}
