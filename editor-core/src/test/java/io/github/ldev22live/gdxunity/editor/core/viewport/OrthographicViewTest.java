package io.github.ldev22live.gdxunity.editor.core.viewport;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.EditorTestHelper;
import io.github.ldev22live.gdxunity.editor.core.gizmo.GizmoInteractionController;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrthographicViewTest {
    private static final float W = 800f;
    private static final float H = 600f;

    private EditorState editorState;
    private Entity box;
    private OrthographicViewCamera view;
    private OrthographicViewInteraction interaction;

    @BeforeAll
    static void natives() {
        EditorTestHelper.initNatives();
    }

    @BeforeEach
    void setUp() {
        editorState = new EditorState(new SceneSerializer());
        Scene scene = new Scene("Level");
        editorState.setActiveScene(scene);
        box = scene.createEntity("Box");
        box.addComponent(new MeshPrimitiveComponent(MeshPrimitiveComponent.Shape.BOX, 100f, 100f, 100f, Color.WHITE));
        view = new OrthographicViewCamera(ViewKind.Z_AXIS);
        view.resize((int) W, (int) H);
        interaction = new OrthographicViewInteraction(editorState, view, new GizmoInteractionController(editorState));
    }

    @Test
    void shouldLookAlongTheChosenAxisWithTheRightUp() {
        for (ViewKind kind : new ViewKind[] {ViewKind.X_AXIS, ViewKind.Y_AXIS, ViewKind.Z_AXIS}) {
            view.setKind(kind);
            assertEquals(0f, view.getCamera().getGdxCamera().direction.dst(kind.getDirection()), 1e-6f);
            assertEquals(0f, view.getCamera().getGdxCamera().up.dst(kind.getUp()), 1e-6f);
        }
        assertThrows(IllegalArgumentException.class, () -> view.setKind(ViewKind.PERSPECTIVE));
    }

    @Test
    void shouldPanTheSceneWithTheDragAndKeepTheZoom() {
        view.setZoom(2f);

        view.pan(10f, 0f);

        assertEquals(-20f, view.getFocus().x, 1e-3f, "dragging right moves the focus left, so the scene follows");
        assertEquals(2f, view.getZoom());
        view.pan(0f, 5f);
        assertEquals(-10f, view.getFocus().y, 1e-3f);
    }

    @Test
    void shouldKeepTheScenePointUnderThePointerFixedWhileZooming() {
        view.setZoom(1f);
        view.setFocus(new Vector3(30f, 40f, 0f));
        Vector3 before = view.worldPointAt(600f, 450f);

        view.zoomAt(2f, 600f, 450f);

        assertEquals(0.5f, view.getZoom(), 1e-6f);
        Vector3 after = view.worldPointAt(600f, 450f);
        assertEquals(0f, before.dst(after), 1e-3f);
    }

    @Test
    void shouldLimitTheZoomAndRejectABadFactor() {
        view.zoomAt(1e9f, 0f, 0f);
        assertEquals(OrthographicViewCamera.MIN_ZOOM, view.getZoom(), 1e-9f);
        view.setZoom(1e9f);
        assertEquals(OrthographicViewCamera.MAX_ZOOM, view.getZoom(), 1e-3f);
        assertThrows(IllegalArgumentException.class, () -> view.zoomAt(0f, 0f, 0f));
    }

    @Test
    void shouldFrameASphere() {
        view.frame(new Vector3(100f, 50f, 0f), 100f);

        assertEquals(100f, view.getFocus().x);
        assertTrue(view.getVisibleHeight() >= 200f, "the sphere fits: " + view.getVisibleHeight());
    }

    @Test
    void shouldSelectTheEntityClickedAndClearOnEmptySpace() {
        interaction.touchDown(W / 2, H / 2, W, H, Input.Buttons.LEFT, 0);
        interaction.touchUp(Input.Buttons.LEFT);
        assertSame(box, editorState.getSelectedEntity());

        interaction.touchDown(10f, 10f, W, H, Input.Buttons.LEFT, 0);
        interaction.touchUp(Input.Buttons.LEFT);
        assertNull(editorState.getSelectedEntity());
    }

    @Test
    void shouldPanWithTheRightAndMiddleButtonsAndAltLeftButUseLeftForSelecting() {
        for (int button : new int[] {Input.Buttons.RIGHT, Input.Buttons.MIDDLE}) {
            view.setFocus(new Vector3());
            interaction.touchDown(100f, 100f, W, H, button, 0);
            interaction.touchDragged(120f, 100f, W, H);
            interaction.touchUp(button);
            assertEquals(-20f, view.getFocus().x, 1e-3f, "button " + button);
        }
        view.setFocus(new Vector3());
        interaction.touchDown(100f, 100f, W, H, Input.Buttons.LEFT, io.github.ldev22live.gdxunity.editor.core.input.EditorKeyboardDispatcher.MOD_ALT);
        interaction.touchDragged(110f, 100f, W, H);
        interaction.touchUp(Input.Buttons.LEFT);
        assertEquals(-10f, view.getFocus().x, 1e-3f);
        assertNull(editorState.getSelectedEntity(), "panning does not select");
    }

    @Test
    void shouldZoomWithTheWheel() {
        interaction.touchDown(W / 2, H / 2, W, H, Input.Buttons.RIGHT, 0);
        interaction.touchUp(Input.Buttons.RIGHT);

        interaction.scrolled(-1f);

        assertTrue(view.getZoom() < 1f, "scrolling up zooms in: " + view.getZoom());
        interaction.scrolled(2f);
        assertTrue(view.getZoom() > 0.8f);
    }

    @Test
    void shouldSizeTheGizmoWithTheZoom() {
        float near = interaction.gizmoScale();
        view.setZoom(4f);

        assertEquals(4f * near, interaction.gizmoScale(), near * 0.01f);
    }
}
