package io.github.ldev22live.gdxunity.editor.core.input;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.editor.core.EditorTestHelper;
import io.github.ldev22live.gdxunity.engine.render.camera.PerspectiveCamera3D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ViewportCameraController3DTest {

    @BeforeAll
    static void init() {
        EditorTestHelper.initNatives();
    }

    private PerspectiveCamera3D camera;
    private ViewportCameraController3D controller;

    @BeforeEach
    void setUp() {
        camera = new PerspectiveCamera3D(67f, 800f, 600f, 0.1f, 100f);
        camera.setPosition(0f, 0f, 10f);
        camera.lookAt(0f, 0f, 0f);
        camera.update();

        controller = new ViewportCameraController3D(camera);
        controller.setTarget(0f, 0f, 0f);
    }

    @Test
    void shouldInitializeWithTargetAndNoMode() {
        assertEquals(new Vector3(0f, 0f, 0f), controller.getTarget());
        assertEquals(ViewportCameraController3D.NavigationMode.NONE, controller.getCurrentMode());
        assertEquals(camera, controller.getCamera());
    }

    @Test
    void shouldOrbitCameraAroundTarget() {
        controller.startNavigation(ViewportCameraController3D.NavigationMode.ORBIT, 100f, 100f);
        assertEquals(ViewportCameraController3D.NavigationMode.ORBIT, controller.getCurrentMode());

        Vector3 initialPos = camera.getPosition();

        // Drag 50 pixels horizontally
        boolean dragged = controller.processDrag(150f, 100f);
        assertTrue(dragged);

        Vector3 afterOrbitPos = camera.getPosition();
        assertNotEquals(initialPos.x, afterOrbitPos.x);

        // Distance to target (0,0,0) should be preserved
        float initialDist = initialPos.dst(controller.getTarget());
        float afterDist = afterOrbitPos.dst(controller.getTarget());
        assertEquals(initialDist, afterDist, 0.05f);

        controller.stopNavigation();
        assertEquals(ViewportCameraController3D.NavigationMode.NONE, controller.getCurrentMode());
    }

    @Test
    void shouldPanCameraParallelToViewPlane() {
        controller.startNavigation(ViewportCameraController3D.NavigationMode.PAN, 100f, 100f);
        Vector3 initialPos = camera.getPosition();
        Vector3 initialTarget = controller.getTarget();

        controller.processDrag(120f, 110f);

        Vector3 newPos = camera.getPosition();
        Vector3 newTarget = controller.getTarget();

        assertNotEquals(initialPos, newPos);
        assertNotEquals(initialTarget, newTarget);

        // Offset between camera and target must remain unchanged during pan
        assertEquals(initialPos.dst(initialTarget), newPos.dst(newTarget), 0.001f);
    }

    @Test
    void shouldDollyZoomCameraOnScroll() {
        float initialDist = camera.getPosition().dst(controller.getTarget());

        // Scroll forward (dolly in)
        boolean scrolled = controller.processScroll(-1f);
        assertTrue(scrolled);

        float closerDist = camera.getPosition().dst(controller.getTarget());
        assertTrue(closerDist < initialDist, "Camera should have moved closer to target");

        // Scroll backward (dolly out)
        controller.processScroll(2f);
        float fartherDist = camera.getPosition().dst(controller.getTarget());
        assertTrue(fartherDist > closerDist, "Camera should have moved farther from target");
    }

    @Test
    void shouldHandleFlyMovementWithWASD() {
        controller.startNavigation(ViewportCameraController3D.NavigationMode.FLY, 100f, 100f);
        Vector3 initialPos = camera.getPosition();

        controller.processKeyDown(Input.Keys.W);
        controller.update(0.1f); // 100ms at default 10 units/sec = 1 unit forward

        Vector3 afterForward = camera.getPosition();
        assertTrue(afterForward.z < initialPos.z, "Camera should have moved forward in -Z");

        controller.processKeyUp(Input.Keys.W);
        controller.processKeyDown(Input.Keys.D);
        controller.update(0.1f);

        Vector3 afterRight = camera.getPosition();
        assertTrue(afterRight.x > afterForward.x, "Camera should have moved right in +X");

        controller.stopNavigation();
    }

    @Test
    void shouldClampVerticalOrbitAtPolesWithoutFlipping() {
        controller.startNavigation(ViewportCameraController3D.NavigationMode.ORBIT, 100f, 100f);
        float initialDist = camera.getPosition().dst(controller.getTarget());

        // 10 drags of 50px at 0.4 deg/px request 200 degrees upward, which would carry the camera over the top pole
        for (int i = 1; i <= 10; i++) {
            controller.processDrag(100f, 100f + i * 50f);
        }

        Vector3 offset = camera.getPosition().sub(controller.getTarget());
        float polarAngle = (float) Math.toDegrees(Math.acos(offset.cpy().nor().y));
        assertEquals(ViewportCameraController3D.MIN_ORBIT_POLAR_ANGLE, polarAngle, 0.1f);
        assertTrue(camera.getUp().y > 0f, "Camera must stay upright after clamping at the pole");
        assertEquals(initialDist, offset.len(), 0.05f);

        // Dragging back down must move the camera away from the pole again
        // lastScreenY is 600: dragging to 350 requests +100 degrees, taking the polar angle from 1 to 101
        controller.processDrag(100f, 350f);
        float polarAfterReturn = (float) Math.toDegrees(Math.acos(camera.getPosition().sub(controller.getTarget()).nor().y));
        assertTrue(polarAfterReturn > 90f, "Camera should orbit back below the pole, polar=" + polarAfterReturn);
    }
}
