package io.github.ldev22live.gdxunity.editor.core.picking;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.math.collision.Ray;
import io.github.ldev22live.gdxunity.editor.core.EditorTestHelper;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Picking and bounds of rotated and scaled 3D primitives. */
class OrientedPickingTest {

    @BeforeAll
    static void init() {
        EditorTestHelper.initNatives();
    }

    private static Ray downZ(float x, float y) {
        return new Ray(new Vector3(x, y, 500f), new Vector3(0f, 0f, -1f));
    }

    @Test
    void shouldPickRotatedBoxWhereItIsDrawn() {
        Scene scene = new Scene("Rotated");
        Entity bar = scene.createEntity("Bar");
        // 100 long in X, rotated 90 degrees around Z: now spans y in [-50, 50], x in [-10, 10]
        bar.addComponent(new MeshPrimitiveComponent(MeshPrimitiveComponent.Shape.BOX, 100f, 20f, 20f, Color.WHITE));
        bar.getTransform().setLocalEulerAngles(0f, 0f, 90f);

        assertEquals(bar, ViewportPicker.pickEntity3D(scene, downZ(0f, 40f)), "Hit along the rotated length");
        assertNull(ViewportPicker.pickEntity3D(scene, downZ(40f, 0f)), "The unrotated extent is empty space now");

        BoundingBox world = ViewportPicker.computeMeshBounds(bar);
        assertEquals(20f, world.getWidth(), 0.01f);
        assertEquals(100f, world.getHeight(), 0.01f);
    }

    @Test
    void shouldIncludeEntityScaleAndPickThePlaneFromAbove() {
        Scene scene = new Scene("Scaled");
        Entity floor = scene.createEntity("Floor");
        floor.addComponent(new MeshPrimitiveComponent(MeshPrimitiveComponent.Shape.PLANE, 100f, 1f, 100f, Color.WHITE));
        floor.getTransform().setLocalScale(3f, 1f, 3f);

        Ray fromAbove = new Ray(new Vector3(140f, 300f, 140f), new Vector3(0f, -1f, 0f));
        assertEquals(floor, ViewportPicker.pickEntity3D(scene, fromAbove), "300 x 300 plane reaches x = 140");
        Ray outside = new Ray(new Vector3(160f, 300f, 0f), new Vector3(0f, -1f, 0f));
        assertNull(ViewportPicker.pickEntity3D(scene, outside));
    }

    @Test
    void shouldPreferTheNearestOfOverlappingPrimitives() {
        Scene scene = new Scene("Overlap");
        Entity back = scene.createEntity("Back");
        back.addComponent(new MeshPrimitiveComponent());
        Entity front = scene.createEntity("Front");
        front.getTransform().setLocalPosition(0f, 0f, 200f);
        front.addComponent(new MeshPrimitiveComponent());

        assertEquals(front, ViewportPicker.pickEntity3D(scene, downZ(0f, 0f)));
    }

    @Test
    void shouldIgnorePrimitivesWithZeroScale() {
        Scene scene = new Scene("Degenerate");
        Entity flat = scene.createEntity("Flat");
        flat.addComponent(new MeshPrimitiveComponent());
        flat.getTransform().setLocalScale(1f, 1f, 0f);
        assertDoesNotThrow(() -> ViewportPicker.pickEntity3D(scene, downZ(0f, 0f)));
    }
}
