package io.github.ldev22live.gdxunity.engine.render.camera;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.render.GdxTestHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PerspectiveCamera3DTest {

    @BeforeAll
    static void init() {
        GdxTestHelper.initNatives();
    }

    @Test
    void shouldInitializeWithConfigurableFovAndPlanes() {
        PerspectiveCamera3D camera = new PerspectiveCamera3D(60f, 800f, 600f, 0.5f, 500f);

        assertEquals(60f, camera.getFieldOfView(), 0.001f);
        assertEquals(0.5f, camera.getNearPlane(), 0.001f);
        assertEquals(500f, camera.getFarPlane(), 0.001f);

        // Position defaults to (0, 0, 10) looking down -Z
        Vector3 pos = camera.getPosition();
        assertEquals(0f, pos.x, 0.001f);
        assertEquals(0f, pos.y, 0.001f);
        assertEquals(10f, pos.z, 0.001f);

        Vector3 dir = camera.getDirection();
        assertEquals(0f, dir.x, 0.001f);
        assertEquals(0f, dir.y, 0.001f);
        assertEquals(-1f, dir.z, 0.001f);

        assertNotNull(camera.getCombinedMatrix());
        assertNotNull(camera.getViewport());
    }

    @Test
    void shouldUpdateMatricesWhenPositionAndOrientationChange() {
        PerspectiveCamera3D camera = new PerspectiveCamera3D(67f, 800f, 600f, 0.1f, 100f);
        Matrix4 initialMatrix = camera.getCombinedMatrix();

        camera.setPosition(10f, 20f, 30f);
        camera.lookAt(0f, 0f, 0f);
        camera.update();

        Vector3 pos = camera.getPosition();
        assertEquals(10f, pos.x, 0.001f);
        assertEquals(20f, pos.y, 0.001f);
        assertEquals(30f, pos.z, 0.001f);

        Matrix4 updatedMatrix = camera.getCombinedMatrix();
        assertNotEquals(initialMatrix, updatedMatrix);
    }

    @Test
    void shouldRotateCameraAroundTarget() {
        PerspectiveCamera3D camera = new PerspectiveCamera3D(67f, 800f, 600f, 0.1f, 100f);
        camera.setPosition(0f, 0f, 10f);
        camera.lookAt(0f, 0f, 0f);
        camera.update();

        Vector3 target = new Vector3(0f, 0f, 0f);
        camera.rotateAround(target, Vector3.Y, 90f);
        camera.update();

        // After 90 deg rotation around Y, camera position should be near (10, 0, 0)
        Vector3 pos = camera.getPosition();
        assertEquals(10f, pos.x, 0.01f);
        assertEquals(0f, pos.y, 0.01f);
        assertEquals(0f, pos.z, 0.01f);
    }

    @Test
    void shouldHandleResizeGracefully() {
        PerspectiveCamera3D camera = new PerspectiveCamera3D(67f, 800f, 600f, 0.1f, 100f);
        camera.resize(1920, 1080);
        camera.update();

        assertEquals(1920, camera.getViewport().getScreenWidth());
        assertEquals(1080, camera.getViewport().getScreenHeight());
    }

    @Test
    void shouldRejectInvalidFovAndPlanes() {
        assertThrows(IllegalArgumentException.class, () -> new PerspectiveCamera3D(0f, 0.1f, 100f));
        assertThrows(IllegalArgumentException.class, () -> new PerspectiveCamera3D(180f, 0.1f, 100f));
        assertThrows(IllegalArgumentException.class, () -> new PerspectiveCamera3D(67f, -1f, 100f));
        assertThrows(IllegalArgumentException.class, () -> new PerspectiveCamera3D(67f, 100f, 50f));
    }
}
