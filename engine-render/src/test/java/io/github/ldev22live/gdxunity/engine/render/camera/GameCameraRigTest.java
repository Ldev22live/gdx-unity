package io.github.ldev22live.gdxunity.engine.render.camera;

import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.render.GdxTestHelper;
import io.github.ldev22live.gdxunity.engine.render.components.CameraComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameCameraRigTest {

    @BeforeAll
    static void init() {
        GdxTestHelper.initNatives();
    }

    @Test
    void shouldPickTheFirstEnabledCameraOnAnActiveEntity() {
        Scene scene = new Scene("cameras");
        Entity inactive = scene.createEntity("Inactive");
        inactive.addComponent(new CameraComponent());
        inactive.setActive(false);
        Entity disabled = scene.createEntity("Disabled");
        disabled.addComponent(new CameraComponent()).setEnabled(false);
        Entity main = scene.createEntity("Main");
        CameraComponent expected = main.addComponent(new CameraComponent(CameraComponent.Projection.PERSPECTIVE));
        scene.createEntity("Second").addComponent(new CameraComponent());

        assertSame(expected, GameCameraRig.findActiveCamera(scene));
        assertNull(GameCameraRig.findActiveCamera(new Scene("empty")));
        assertNull(GameCameraRig.findActiveCamera(null));
    }

    @Test
    void shouldFitTheOrthographicHeightToTheViewAtTheEntityPosition() {
        Scene scene = new Scene("2d");
        Entity entity = scene.createEntity("Camera");
        entity.getTransform().setLocalPosition(500f, 300f, 7f);
        CameraComponent component = entity.addComponent(new CameraComponent());
        component.setOrthographicHeight(480f);
        GameCameraRig rig = new GameCameraRig();
        rig.resize(1280, 960);

        Camera camera = rig.apply(component);

        assertInstanceOf(OrthographicCamera2D.class, camera);
        assertEquals(0.5f, camera.getZoom(), 1e-6f, "960 pixels show 480 units");
        assertEquals(new Vector3(500f, 300f, 0f), camera.getPosition());
    }

    @Test
    void shouldLookAlongTheEntityForwardAxisWithItsFieldOfView() {
        Scene scene = new Scene("3d");
        Entity entity = scene.createEntity("Camera");
        entity.getTransform().setLocalPosition(10f, 200f, 400f);
        // Yaw 90 turns -Z to -X; pitch -30 tilts it down
        entity.getTransform().setLocalEulerAngles(90f, -30f, 0f);
        CameraComponent component = entity.addComponent(new CameraComponent(CameraComponent.Projection.PERSPECTIVE));
        component.setFieldOfView(70f);
        component.setClipPlanes(2f, 9000f);
        GameCameraRig rig = new GameCameraRig();
        rig.resize(800, 600);

        PerspectiveCamera3D camera = (PerspectiveCamera3D) rig.apply(component);

        Vector3 direction = camera.getDirection();
        assertEquals(-(float) Math.cos(Math.toRadians(30)), direction.x, 1e-4f);
        assertEquals(-0.5f, direction.y, 1e-4f);
        assertEquals(0f, direction.z, 1e-4f);
        assertTrue(camera.getUp().y > 0.8f, "up stays up");
        assertEquals(new Vector3(10f, 200f, 400f), camera.getPosition());
        assertEquals(70f, camera.getFieldOfView());
        assertEquals(2f, camera.getNearPlane());
        assertEquals(9000f, camera.getFarPlane());
    }

    @Test
    void shouldRejectCamerasWithoutEntityAndEmptyViews() {
        GameCameraRig rig = new GameCameraRig();
        assertThrows(IllegalArgumentException.class, () -> rig.apply(new CameraComponent()));
        assertThrows(IllegalArgumentException.class, () -> rig.resize(0, 10));
    }
}
