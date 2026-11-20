package io.github.ldev22live.gdxunity.editor.core.viewport;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
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

import java.util.List;

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

    /** UI M2: true isometric, the three axes equally foreshortened, the ground's diagonal across the screen. */
    @Test
    void shouldLookDownTheDiagonalInTheIsometricViewAtTheIsometricAngle() {
        view.setKind(ViewKind.ISOMETRIC);
        Vector3 direction = view.getDirection();

        float pitch = (float) Math.toDegrees(Math.asin(-direction.y));
        assertEquals(35.264f, pitch, 0.01f, "looks down at the isometric angle");
        assertEquals(0f, direction.dot(view.getUp()), 1e-6f, "up is square to the view");
        Vector3 right = view.getRight();
        Vector3 up = view.getUp();
        float x = (float) Math.hypot(right.x, up.x);
        assertEquals(x, (float) Math.hypot(right.y, up.y), 1e-5f, "X and Y are foreshortened alike");
        assertEquals(x, (float) Math.hypot(right.z, up.z), 1e-5f, "and Z");
        assertTrue(up.y > 0f, "up on the screen is up in the world");
    }

    @Test
    void shouldTurnTheIsometricViewInQuarterTurnsAroundItsFocusAndOnlyIt() {
        view.setKind(ViewKind.ISOMETRIC);
        view.setFocus(new Vector3(100f, 0f, 50f));
        Vector3 start = view.getDirection();

        view.turn(1);
        Vector3 turned = view.getDirection();
        assertEquals(1, view.getQuarterTurns());
        assertEquals(start.y, turned.y, 1e-6f, "still looking down at the same angle");
        assertEquals(0f, start.x * turned.x + start.z * turned.z, 1e-5f, "a quarter round");
        assertEquals(0f, view.getFocus().dst(100f, 0f, 50f), 1e-4f, "about the focus");
        assertEquals(0f, view.getCamera().getGdxCamera().direction.dst(turned), 1e-6f, "the camera turned with it");
        view.turn(-5);
        assertEquals(0, view.getQuarterTurns(), "four quarter turns come back round");
        assertEquals(0f, view.getDirection().dst(start), 1e-6f);

        view.turn(2);
        view.setKind(ViewKind.ISOMETRIC);
        assertEquals(0, view.getQuarterTurns(), "a new view starts unturned");
        view.setKind(ViewKind.Y_AXIS);
        assertThrows(IllegalStateException.class, () -> view.turn(1), "an axis view looks along its axis");
    }

    /** UI M2: the whole map fits, centred, measured box by box (not one box round everything, whose corners are empty). */
    @Test
    void shouldFrameEveryCornerOfTheBoxesInsideThePaneAndCentreThem() {
        view.setKind(ViewKind.ISOMETRIC);
        List<BoundingBox> boxes = List.of(
            new BoundingBox(new Vector3(-3000f, -50f, -3000f), new Vector3(3000f, 400f, 3000f)),
            new BoundingBox(new Vector3(2500f, 0f, -4500f), new Vector3(4000f, 2500f, -3000f)));

        view.frame(boxes);

        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (BoundingBox box : boxes) {
            for (int i = 0; i < 8; i++) {
                Vector3 corner = new Vector3((i & 1) == 0 ? box.min.x : box.max.x, (i & 2) == 0 ? box.min.y : box.max.y,
                    (i & 4) == 0 ? box.min.z : box.max.z);
                view.getCamera().getGdxCamera().project(corner, 0f, 0f, W, H);
                minX = Math.min(minX, corner.x);
                maxX = Math.max(maxX, corner.x);
                minY = Math.min(minY, corner.y);
                maxY = Math.max(maxY, corner.y);
            }
        }
        assertTrue(minX >= 0f && maxX <= W && minY >= 0f && maxY <= H, "every corner on screen: " + minX + ".." + maxX + ", " + minY + ".." + maxY);
        float fill = Math.max((maxX - minX) / W, (maxY - minY) / H);
        assertEquals(1f / OrthographicViewCamera.BOX_MARGIN, fill, 0.01f, "fills the pane but for the margin");
        assertEquals(W / 2f, (minX + maxX) / 2f, 1f, "centred across");
        assertEquals(H / 2f, (minY + maxY) / 2f, 1f, "centred up and down");
        assertThrows(IllegalArgumentException.class, () -> view.frame(List.of()));
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
    void shouldReportTheEntityUnderThePointerForADrop() {
        assertSame(box, interaction.entityAt(W / 2, H / 2, W, H).orElseThrow());
        assertTrue(interaction.entityAt(10f, 10f, W, H).isEmpty());
        assertNull(editorState.getSelectedEntity());
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
