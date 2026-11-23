package io.github.ldev22live.gdxunity.engine.render;

import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bounding spheres and view test the renderer culls shapes and models with (roadmap M3.6, TD-097). Reading a model's rest-pose
 * bounds needs its meshes, so that part is checked in the running game (see the M3.6 report); the maths is checked here.
 */
class ObjectCullingTest {
    @BeforeAll
    static void init() {
        GdxTestHelper.initNatives();
    }

    @Test
    void shouldMoveTheSphereWithTheTransformAndGrowItByTheLargestScale() {
        float[] local = {0f, 50f, 0f, 100f};
        Matrix4 transform = new Matrix4().set(new Vector3(1000f, 0f, -200f), new Quaternion(Vector3.Y, 90f), new Vector3(1f, 3f, 2f));
        float[] out = new float[ObjectCulling.STRIDE * 2];

        ObjectCulling.worldSphere(local, transform, new Vector3(), new Vector3(), out, ObjectCulling.STRIDE);

        assertEquals(1000f, out[4], 1e-3f);
        assertEquals(150f, out[5], 1e-3f, "the centre 50 up, scaled by 3 in y");
        assertEquals(-200f, out[6], 1e-3f);
        assertEquals(300f, out[7], 1e-3f, "the radius grows by the largest scale, so the sphere still holds the object");
        assertEquals(0f, out[0], "the first slot is untouched");
    }

    @Test
    void shouldTellSpheresInTheViewFromThoseBehindOrBesideIt() {
        PerspectiveCamera camera = new PerspectiveCamera(60f, 800f, 600f);
        camera.position.set(0f, 100f, 0f);
        camera.direction.set(0f, 0f, -1f);
        camera.near = 1f;
        camera.far = 10000f;
        camera.update();
        float[] spheres = {
            0f, 100f, -2000f, 50f, // ahead
            0f, 100f, 2000f, 50f, // behind
            5000f, 100f, -1000f, 50f, // far off to the side
            0f, 100f, 60f, 100f // behind, but large enough to reach into the view
        };

        assertTrue(ObjectCulling.touches(camera.frustum, spheres, 0));
        assertFalse(ObjectCulling.touches(camera.frustum, spheres, 4));
        assertFalse(ObjectCulling.touches(camera.frustum, spheres, 8));
        assertTrue(ObjectCulling.touches(camera.frustum, spheres, 12));
    }
}
