package io.github.ldev22live.gdxunity.editor.core.viewport;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.editor.core.EditorTestHelper;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.engine.render.components.ShapeRendererComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ViewportFocusTest {

    @BeforeAll
    static void init() {
        EditorTestHelper.initNatives();
    }

    @Test
    void shouldFrameMeshBoundsCenterAndRadius() {
        Entity box = new Scene("Focus").createEntity("Box");
        box.getTransform().setLocalPosition(100f, 0f, 0f);
        box.addComponent(new MeshPrimitiveComponent(MeshPrimitiveComponent.Shape.BOX, 30f, 40f, 0.0001f, Color.WHITE));

        assertEquals(new Vector3(100f, 0f, 0f), ViewportFocus.focusCenter(box));
        // Half the 30 x 40 box diagonal
        assertEquals(25f, ViewportFocus.boundingRadius(box), 0.01f);
    }

    @Test
    void shouldUseFlatBoundsOrDefaultRadius() {
        Scene scene = new Scene("Focus");
        Entity flat = scene.createEntity("Flat");
        flat.getTransform().setLocalPosition(0f, 0f, 5f);
        flat.addComponent(new ShapeRendererComponent(60f, 80f, Color.RED, ShapeRendererComponent.ShapeType.FILLED, false));
        assertEquals(new Vector3(30f, 40f, 5f), ViewportFocus.focusCenter(flat));
        assertEquals(50f, ViewportFocus.boundingRadius(flat), 0.01f);

        Entity light = scene.createEntity("Light");
        assertEquals(ViewportFocus.DEFAULT_FOCUS_RADIUS, ViewportFocus.boundingRadius(light));
    }

    @Test
    void shouldBackOffFarEnoughToFitSphereInPerspectiveView() {
        // At 60 degrees FOV, sin(30) = 0.5, so distance = radius * margin * 2
        assertEquals(100f * ViewportFocus.FRAME_MARGIN * 2f, ViewportFocus.perspectiveDistance(100f, 60f), 0.01f);
        assertThrows(IllegalArgumentException.class, () -> ViewportFocus.perspectiveDistance(0f, 60f));
    }

    @Test
    void shouldZoomToFitTheLimitingAxisWithinZoomLimits() {
        // 800 wide in a 400 px panel needs 2 units per pixel before the margin
        assertEquals(2f * ViewportFocus.FRAME_MARGIN, ViewportFocus.orthographicZoom(800f, 100f, 400f, 300f), 0.001f);
        assertEquals(SceneViewportInteraction2D.MIN_ZOOM, ViewportFocus.orthographicZoom(1f, 1f, 400f, 300f), 0.0001f);
        assertEquals(SceneViewportInteraction2D.MAX_ZOOM, ViewportFocus.orthographicZoom(1e6f, 1f, 400f, 300f), 0.0001f);
    }
}
