package io.github.ldev22live.gdxunity.engine.render.camera;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrthographicCamera2DTest {

    @BeforeAll
    static void init() {
        io.github.ldev22live.gdxunity.engine.render.GdxTestHelper.initNatives();
    }

    @Test
    void shouldInitializeWithDefaultVirtualDimensionsAndCenterPosition() {
        OrthographicCamera2D camera = new OrthographicCamera2D(800, 600);

        assertEquals(800f, camera.getVirtualWidth());
        assertEquals(600f, camera.getVirtualHeight());
        assertEquals(1.0f, camera.getZoom());

        // Center should be (400, 300, 0)
        Vector3 pos = camera.getPosition();
        assertEquals(400f, pos.x, 0.001f);
        assertEquals(300f, pos.y, 0.001f);
        assertEquals(0f, pos.z, 0.001f);

        assertNotNull(camera.getCombinedMatrix());
        assertNotNull(camera.getViewport());
    }

    @Test
    void shouldUpdateMatricesWhenPositionAndZoomChange() {
        OrthographicCamera2D camera = new OrthographicCamera2D(640, 480);
        Matrix4 initialMatrix = camera.getCombinedMatrix();

        camera.setPosition(100f, 200f, 0f);
        camera.setZoom(2.0f);
        camera.update();

        assertEquals(100f, camera.getPosition().x);
        assertEquals(200f, camera.getPosition().y);
        assertEquals(2.0f, camera.getZoom());

        Matrix4 updatedMatrix = camera.getCombinedMatrix();
        assertNotEquals(initialMatrix, updatedMatrix);
    }

    @Test
    void shouldTranslateCameraRelative() {
        OrthographicCamera2D camera = new OrthographicCamera2D(640, 480);
        camera.setPosition(10f, 10f, 0f);

        camera.translate(5f, -2f, 0f);
        assertEquals(15f, camera.getPosition().x, 0.001f);
        assertEquals(8f, camera.getPosition().y, 0.001f);
    }

    @Test
    void shouldPreserveVirtualAspectRatioOnResize() {
        OrthographicCamera2D camera = new OrthographicCamera2D(640, 480);

        // Aspect ratio is 640 / 480 = 4:3
        assertEquals(640f / 480f, camera.getViewport().getWorldWidth() / camera.getViewport().getWorldHeight(), 0.001f);

        // Resize window to widescreen 1920x1080 (16:9)
        camera.resize(1920, 1080);

        // Virtual world dimensions remain preserved
        assertEquals(640f, camera.getViewport().getWorldWidth(), 0.001f);
        assertEquals(480f, camera.getViewport().getWorldHeight(), 0.001f);
    }

    @Test
    void shouldRejectInvalidDimensionsOrZoom() {
        assertThrows(IllegalArgumentException.class, () -> new OrthographicCamera2D(0, 480));
        assertThrows(IllegalArgumentException.class, () -> new OrthographicCamera2D(640, -10));

        OrthographicCamera2D camera = new OrthographicCamera2D(640, 480);
        assertThrows(IllegalArgumentException.class, () -> camera.setZoom(0));
        assertThrows(IllegalArgumentException.class, () -> camera.setZoom(-1.5f));
    }
}
