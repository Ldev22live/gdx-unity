package io.github.ldev22live.gdxunity.editor.core.picking;

import com.badlogic.gdx.graphics.Color;
import io.github.ldev22live.gdxunity.editor.core.EditorTestHelper;
import io.github.ldev22live.gdxunity.engine.render.camera.PerspectiveCamera3D;
import io.github.ldev22live.gdxunity.engine.render.components.ShapeRendererComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ViewportPickerTest {

    @BeforeAll
    static void init() {
        EditorTestHelper.initNatives();
    }

    @Test
    void shouldHitTestCenteredShapeCorrectly() {
        Scene scene = new Scene("PickScene");

        // Entity at (100, 100), width=50, height=50, centered -> bounds: [75..125, 75..125]
        Entity box = scene.createEntity("Box");
        box.getTransform().setLocalPosition(100f, 100f, 0f);
        box.addComponent(new ShapeRendererComponent(50f, 50f, Color.RED, ShapeRendererComponent.ShapeType.FILLED, true));

        // Click directly on center
        Entity hit = ViewportPicker.pickEntityAt(scene, 100f, 100f);
        assertEquals(box, hit);

        // Click on boundary
        Entity hitEdge = ViewportPicker.pickEntityAt(scene, 76f, 76f);
        assertEquals(box, hitEdge);

        // Click outside
        Entity miss = ViewportPicker.pickEntityAt(scene, 200f, 200f);
        assertNull(miss);
    }

    @Test
    void shouldPickTopMostChildInHierarchyWhenOverlapping() {
        Scene scene = new Scene("OverlapScene");

        Entity parent = scene.createEntity("Parent");
        parent.getTransform().setLocalPosition(100f, 100f, 0f);
        parent.addComponent(new ShapeRendererComponent(100f, 100f, Color.BLUE, ShapeRendererComponent.ShapeType.FILLED, true));

        Entity child = scene.createEntity("Child");
        child.getTransform().setLocalPosition(0f, 0f, 0f);
        child.getTransform().setParent(parent.getTransform());
        child.addComponent(new ShapeRendererComponent(40f, 40f, Color.YELLOW, ShapeRendererComponent.ShapeType.FILLED, true));

        // Center click should hit child because it was added later / top-most
        Entity hit = ViewportPicker.pickEntityAt(scene, 100f, 100f);
        assertEquals(child, hit);
    }

    @Test
    void shouldIgnoreInactiveEntitiesDuringPicking() {
        Scene scene = new Scene("InactiveScene");

        Entity box = scene.createEntity("HiddenBox");
        box.getTransform().setLocalPosition(50f, 50f, 0f);
        box.addComponent(new ShapeRendererComponent(40f, 40f, Color.RED));
        box.setActive(false);

        Entity hit = ViewportPicker.pickEntityAt(scene, 50f, 50f);
        assertNull(hit);
    }

    @Test
    void shouldRejectPickRayWithoutCamera() {
        assertThrows(NullPointerException.class, () -> ViewportPicker.getPickRay(null, 10f, 10f));
    }

    @Test
    void shouldRejectPickRayWhenCameraHasNoViewport() {
        PerspectiveCamera3D cameraWithoutViewport = new PerspectiveCamera3D(67f, 800f, 600f, 0.1f, 100f) {
            @Override
            public com.badlogic.gdx.utils.viewport.Viewport getViewport() {
                return null;
            }
        };
        assertThrows(IllegalArgumentException.class, () -> ViewportPicker.getPickRay(cameraWithoutViewport, 10f, 10f));
    }

    @Test
    void shouldComputeUnionRenderBoundsUsedForOutlineAndPicking() {
        Scene scene = new Scene("BoundsScene");
        Entity entity = scene.createEntity("Composite");
        entity.getTransform().setLocalPosition(100f, 100f, 0f);
        entity.getTransform().setLocalScale(2f, 1f, 1f);
        // 20x10 centered, scaled x2 in X -> [80..120] x [95..105]
        entity.addComponent(new ShapeRendererComponent(20f, 10f, Color.RED, ShapeRendererComponent.ShapeType.FILLED, true));

        com.badlogic.gdx.math.Rectangle bounds = ViewportPicker.computeRenderBounds(entity);
        assertNotNull(bounds);
        assertEquals(80f, bounds.x, 0.001f);
        assertEquals(95f, bounds.y, 0.001f);
        assertEquals(40f, bounds.width, 0.001f);
        assertEquals(10f, bounds.height, 0.001f);
        assertEquals(entity, ViewportPicker.pickEntityAt(scene, 119f, 104f));

        assertNull(ViewportPicker.computeRenderBounds(scene.createEntity("Empty")), "No renderer means no bounds");
    }
}
