package io.github.ldev22live.gdxunity.editor.sculpt;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.IntArray;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.action.DefaultEditorActions;
import io.github.ldev22live.gdxunity.editor.core.action.EditorAction;
import io.github.ldev22live.gdxunity.editor.core.action.EditorActionRegistry;
import io.github.ldev22live.gdxunity.editor.core.input.EditorKeyboardDispatcher;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.render.components.DynamicMeshComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.MeshGeometry3D;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import io.github.ldev22live.gdxunity.engine.sculpt.BrushType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SculptActionsTest {
    @TempDir
    Path root;

    private boolean playing;
    private EditorState editorState;
    private EditorActionRegistry registry;
    private SculptSession session;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        editorState = new EditorState(new SceneSerializer()) {
            @Override
            public boolean isPlayModeActive() {
                return playing;
            }
        };
        editorState.setActiveScene(new Scene("Level"));
        session = new SculptSession(new SculptAssets(root.toFile()));
        registry = new EditorActionRegistry();
        SculptActions.register(registry, editorState, session, () -> new Vector3(1f, 2f, 3f));
    }

    private EditorAction action(String id) {
        return registry.find(id).orElseThrow(() -> new AssertionError("no action " + id));
    }

    @Test
    void shouldToggleTheWireframeAndReportItToTheRenderer() {
        assertFalse(session.isWireframe());
        registry.execute(SculptActions.TOGGLE_WIREFRAME);
        assertTrue(session.isWireframe());
        assertEquals("Sculpt wireframe: on", editorState.getStatusMessage());
        registry.execute(SculptActions.TOGGLE_WIREFRAME);
        assertFalse(session.isWireframe());
        assertEquals(DefaultEditorActions.MENU_TOOLS, action(SculptActions.TOGGLE_WIREFRAME).getMenu());
    }

    @Test
    void shouldRegisterEveryActionInItsMenu() {
        assertEquals(DefaultEditorActions.MENU_CREATE, action(SculptActions.CREATE_SCULPT).getMenu());
        assertEquals(DefaultEditorActions.MENU_FILE, action(SculptActions.SAVE_SCULPTS).getMenu());
        for (BrushType type : BrushType.values()) {
            assertEquals(DefaultEditorActions.MENU_TOOLS, action(SculptActions.brushActionId(type)).getMenu());
        }
        for (String id : new String[] {SculptActions.ENTER_MODE, SculptActions.EXIT_MODE, SculptActions.MIRROR_X,
            SculptActions.MIRROR_Y, SculptActions.MIRROR_Z, SculptActions.BRUSH_LARGER, SculptActions.BRUSH_SMALLER,
            SculptActions.BRUSH_STRONGER, SculptActions.BRUSH_WEAKER}) {
            assertEquals(DefaultEditorActions.MENU_TOOLS, action(id).getMenu(), id);
        }
        assertEquals("tools.sculpt-brush.grab", SculptActions.brushActionId(BrushType.GRAB));
    }

    @Test
    void shouldBindTheBrushKeys() {
        assertEquals(Input.Keys.RIGHT_BRACKET, action(SculptActions.BRUSH_LARGER).getKeycode());
        assertEquals(Input.Keys.LEFT_BRACKET, action(SculptActions.BRUSH_SMALLER).getKeycode());
        assertEquals(Input.Keys.EQUALS, action(SculptActions.BRUSH_STRONGER).getKeycode());
        assertEquals(Input.Keys.MINUS, action(SculptActions.BRUSH_WEAKER).getKeycode());
        assertEquals(Input.Keys.S, action(SculptActions.SAVE_SCULPTS).getKeycode());
        assertEquals(EditorKeyboardDispatcher.MOD_CTRL | EditorKeyboardDispatcher.MOD_ALT,
            action(SculptActions.SAVE_SCULPTS).getModifiers());
    }

    @Test
    void shouldCreateASculptFileAnEntityShowingItAndStartSculpting() {
        assertTrue(registry.execute(SculptActions.CREATE_SCULPT));

        Entity entity = editorState.getSelectedEntity();
        assertNotNull(entity);
        assertEquals("Sculpt", entity.getName());
        DynamicMeshComponent mesh = entity.getComponent(DynamicMeshComponent.class);
        assertEquals("sculpts/Sculpt.sculpt", mesh.getMeshPath());
        assertTrue(Files.isRegularFile(root.resolve("sculpts/Sculpt.sculpt")));
        assertEquals(new Vector3(1f, 2f, 3f), entity.getTransform().getWorldPosition());
        assertTrue(session.isSculptMode());
        assertNotNull(session.getDocumentOf(entity), "the sculpt is open and ready to draw");
        assertTrue(editorState.getCommandHistory().canUndo());

        assertTrue(registry.execute(SculptActions.CREATE_SCULPT));
        assertEquals("sculpts/Sculpt 1.sculpt",
            editorState.getSelectedEntity().getComponent(DynamicMeshComponent.class).getMeshPath());
    }

    /** The 12 triangles of a cube with split vertices (24 corners), as an imported model has them. */
    private static MeshGeometry3D splitCube() {
        float[][] corners = {{-1, -1, -1}, {1, -1, -1}, {1, 1, -1}, {-1, 1, -1}, {-1, -1, 1}, {1, -1, 1}, {1, 1, 1}, {-1, 1, 1}};
        int[][] faces = {{0, 3, 2, 1}, {4, 5, 6, 7}, {0, 1, 5, 4}, {3, 7, 6, 2}, {0, 4, 7, 3}, {1, 2, 6, 5}};
        float[] positions = new float[24 * 3];
        int[] indices = new int[36];
        for (int face = 0; face < 6; face++) {
            for (int corner = 0; corner < 4; corner++) {
                float[] p = corners[faces[face][corner]];
                System.arraycopy(p, 0, positions, (face * 4 + corner) * 3, 3);
            }
            int base = face * 4;
            int[] quad = {base, base + 1, base + 2, base, base + 2, base + 3};
            System.arraycopy(quad, 0, indices, face * 6, 6);
        }
        return new MeshGeometry3D(positions, indices);
    }

    @Test
    void shouldStartASculptFromTheSelectedEntitysMeshAndKeepTheOriginal() {
        SculptActions.registerFromSelected(registry, editorState, session, entity -> splitCube());
        Entity source = editorState.getActiveScene().createEntity("Crate");
        source.getTransform().setLocalPosition(4f, 5f, 6f);
        editorState.setSelectedEntity(source);

        assertTrue(registry.execute(SculptActions.CREATE_SCULPT_FROM_SELECTED));

        Entity sculpt = editorState.getSelectedEntity();
        assertEquals("Crate Sculpt", sculpt.getName());
        assertEquals(new Vector3(4f, 5f, 6f), sculpt.getTransform().getWorldPosition());
        String path = sculpt.getComponent(DynamicMeshComponent.class).getMeshPath();
        assertEquals("sculpts/Crate.sculpt", path);
        SculptDocument document = session.getDocumentOf(sculpt);
        assertNotNull(document, "the new sculpt is open");
        assertEquals(12 * 256, document.getSurface().getMesh().getTriangleCount(), "welded, then subdivided four levels");
        assertTrue(session.isSculptMode());
        assertNotNull(editorState.getActiveScene().findEntityByName("Crate"), "the source stays");
    }

    @Test
    void shouldNeedASelectionAndReportAMeshThatCannotBeRead() {
        SculptActions.registerFromSelected(registry, editorState, session, entity -> {
            throw new IllegalStateException(entity.getName() + " shows no mesh");
        });
        assertFalse(action(SculptActions.CREATE_SCULPT_FROM_SELECTED).isEnabled(), "nothing is selected");

        Entity source = editorState.getActiveScene().createEntity("Empty");
        editorState.setSelectedEntity(source);
        registry.execute(SculptActions.CREATE_SCULPT_FROM_SELECTED);

        assertEquals(source, editorState.getSelectedEntity());
        assertTrue(editorState.getStatusMessage().contains("Cannot sculpt Empty: Empty shows no mesh"), editorState.getStatusMessage());
        assertFalse(Files.exists(root.resolve("sculpts")), "no file is made for a mesh that cannot be read");
    }

    @Test
    void shouldReportAProblemCreatingTheFileAndCreateNoEntity() throws IOException {
        Files.writeString(root.resolve("sculpts"), "a file where the folder should be");

        registry.execute(SculptActions.CREATE_SCULPT);

        assertNull(editorState.getSelectedEntity());
        assertTrue(editorState.getStatusMessage().startsWith("Cannot create the sculpt"), editorState.getStatusMessage());
        assertFalse(session.isSculptMode());
    }

    @Test
    void shouldEnterAndLeaveSculptModeAndOnlyOfferWhatMakesSense() {
        assertTrue(action(SculptActions.ENTER_MODE).isEnabled());
        assertFalse(action(SculptActions.EXIT_MODE).isEnabled());

        registry.execute(SculptActions.ENTER_MODE);
        assertTrue(session.isSculptMode());
        assertFalse(action(SculptActions.ENTER_MODE).isEnabled());
        assertTrue(action(SculptActions.EXIT_MODE).isEnabled());
        assertTrue(editorState.getStatusMessage().contains("Select a sculpt")
            || editorState.getStatusMessage().contains("3D view"), editorState.getStatusMessage());

        registry.execute(SculptActions.EXIT_MODE);
        assertFalse(session.isSculptMode());
        assertEquals("Sculpt mode off", editorState.getStatusMessage());
    }

    @Test
    void shouldNotOfferEditingActionsDuringPlay() {
        playing = true;
        for (String id : new String[] {SculptActions.CREATE_SCULPT, SculptActions.ENTER_MODE,
            SculptActions.brushActionId(BrushType.DRAW), SculptActions.MIRROR_X}) {
            assertFalse(action(id).isEnabled(), id + " while playing");
            assertFalse(registry.execute(id), id + " does nothing while playing");
        }
        assertFalse(session.isSculptMode());
        playing = false;
        assertTrue(action(SculptActions.CREATE_SCULPT).isEnabled(), "and they are back after Play");
    }

    @Test
    void shouldSelectABrushAndTurnSculptModeOn() {
        registry.execute(SculptActions.brushActionId(BrushType.SMOOTH));

        assertEquals(BrushType.SMOOTH, session.getBrushType());
        assertTrue(session.isSculptMode());
        assertTrue(editorState.getStatusMessage().startsWith("Brush: Smooth, radius 15.00, strength 0.50"),
            editorState.getStatusMessage());
    }

    @Test
    void shouldChangeBrushSizeAndStrengthInStepsAndKeepThemInRange() {
        session.setSculptMode(true);
        float radius = session.getBrush().getRadius();

        registry.execute(SculptActions.BRUSH_LARGER);
        assertEquals(radius * SculptActions.RADIUS_STEP, session.getBrush().getRadius(), 1e-4f);
        registry.execute(SculptActions.BRUSH_SMALLER);
        assertEquals(radius, session.getBrush().getRadius(), 1e-3f);
        for (int i = 0; i < 100; i++) {
            registry.execute(SculptActions.BRUSH_LARGER);
        }
        assertEquals(SculptActions.MAX_RADIUS, session.getBrush().getRadius(), 1e-3f);
        for (int i = 0; i < 200; i++) {
            registry.execute(SculptActions.BRUSH_SMALLER);
        }
        assertEquals(SculptActions.MIN_RADIUS, session.getBrush().getRadius(), 1e-6f);

        for (int i = 0; i < 20; i++) {
            registry.execute(SculptActions.BRUSH_STRONGER);
        }
        assertEquals(1f, session.getBrush().getStrength(), 1e-6f);
        for (int i = 0; i < 20; i++) {
            registry.execute(SculptActions.BRUSH_WEAKER);
        }
        assertEquals(SculptActions.MIN_STRENGTH, session.getBrush().getStrength(), 1e-6f);
    }

    @Test
    void shouldOnlyOfferTheSizeKeysWhileSculpting() {
        assertFalse(action(SculptActions.BRUSH_LARGER).isEnabled());
        assertFalse(registry.execute(SculptActions.BRUSH_LARGER));

        session.setSculptMode(true);

        assertTrue(action(SculptActions.BRUSH_LARGER).isEnabled());
    }

    @Test
    void shouldToggleMirrorAxesAndShowThemInTheBrushSummary() {
        registry.execute(SculptActions.MIRROR_X);
        assertTrue(session.getSymmetry().isMirrorX());
        assertEquals("Sculpt mirror X: on", editorState.getStatusMessage());
        registry.execute(SculptActions.MIRROR_Z);
        registry.execute(SculptActions.MIRROR_Y);
        registry.execute(SculptActions.MIRROR_Y);
        assertFalse(session.getSymmetry().isMirrorY());
        assertEquals("Sculpt mirror Y: off", editorState.getStatusMessage());
        assertTrue(SculptActions.brushSummary(session).endsWith(", mirror XZ"), SculptActions.brushSummary(session));

        registry.execute(SculptActions.MIRROR_X);
        assertFalse(session.getSymmetry().isMirrorX());
    }

    @Test
    void shouldSaveModifiedSculptsOnlyWhenThereAreSome() throws IOException {
        AssetReference asset = session.getAssets().create("Rock", 10f, 4);
        SculptDocument document = session.open(asset);
        assertFalse(action(SculptActions.SAVE_SCULPTS).isEnabled(), "nothing to save yet");

        document.getSurface().getMesh().getPositions()[0] += 2f;
        IntArray moved = new IntArray();
        moved.add(0);
        document.getSurface().commitMoved(moved);
        assertTrue(action(SculptActions.SAVE_SCULPTS).isEnabled());
        float expected = document.getSurface().getMesh().getPositions()[0];

        registry.execute(SculptActions.SAVE_SCULPTS);

        assertEquals("Saved 1 sculpt", editorState.getStatusMessage());
        assertEquals(expected, session.getAssets().load(asset).getPositions()[0], 0f);
        assertFalse(action(SculptActions.SAVE_SCULPTS).isEnabled());
    }

    @Test
    void shouldToggleSculptModeWithOneActionAndTheF4Key() {
        assertEquals(Input.Keys.F4, action(SculptActions.TOGGLE_MODE).getKeycode());

        registry.execute(SculptActions.TOGGLE_MODE);
        assertTrue(session.isSculptMode());
        registry.execute(SculptActions.TOGGLE_MODE);
        assertFalse(session.isSculptMode());

        playing = true;
        assertFalse(action(SculptActions.TOGGLE_MODE).isEnabled(), "not while playing");
    }
}
