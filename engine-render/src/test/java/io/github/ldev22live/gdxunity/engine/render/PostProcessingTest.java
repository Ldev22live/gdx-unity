package io.github.ldev22live.gdxunity.engine.render;

import com.badlogic.gdx.graphics.g3d.ModelBatch;
import io.github.ldev22live.gdxunity.engine.render.camera.PerspectiveCamera3D;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.environment.PostProcessPreset;
import io.github.ldev22live.gdxunity.engine.scene.environment.PostProcessSettings;
import io.github.ldev22live.gdxunity.engine.scene.environment.PostProcessVolumeComponent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Post-processing for roadmap M6.2 (TD-091): the passes' arithmetic and which volume the renderer's camera sees, without a GL context. */
class PostProcessingTest {
    private static final float EPS = 1e-4f;

    @BeforeAll
    static void init() {
        GdxTestHelper.initNatives();
    }

    @Test
    void shouldKeepAGreysBrightnessWhileWarmingOrCoolingIt() {
        float[] balance = PostProcessMath.whiteBalance(0f, 0f, new float[3]);
        assertEquals(1f, balance[0], EPS);
        assertEquals(1f, balance[1], EPS);
        assertEquals(1f, balance[2], EPS);
        for (float[] setting : new float[][] {{1f, 0f}, {-1f, 0f}, {0.4f, 0.7f}, {-0.3f, -1f}}) {
            PostProcessMath.whiteBalance(setting[0], setting[1], balance);
            float luma = PostProcessMath.LUMA_R * balance[0] + PostProcessMath.LUMA_G * balance[1] + PostProcessMath.LUMA_B * balance[2];
            assertEquals(1f, luma, EPS, "temperature " + setting[0] + ", tint " + setting[1]);
        }
        PostProcessMath.whiteBalance(1f, 0f, balance);
        assertTrue(balance[0] > 1f && balance[0] > balance[1] && balance[1] > balance[2], "warm: more red than blue");
        PostProcessMath.whiteBalance(0f, 1f, balance);
        assertTrue(balance[1] < balance[0] && balance[1] < balance[2], "magenta: less green");
    }

    @Test
    void shouldTurnWindowDepthBackIntoDistance() {
        float near = 1f;
        float far = 50000f;
        assertEquals(near, PostProcessMath.linearDepth(0f, near, far, false), EPS);
        assertEquals(far, PostProcessMath.linearDepth(1f, near, far, false), 1f);
        // A point 100 units away: its window depth (the perspective divide of the projection) maps back to 100
        float depth = (far / (far - near)) * (1f - near / 100f);
        assertEquals(100f, PostProcessMath.linearDepth(depth, near, far, false), 0.01f);
        assertEquals(25000.5f, PostProcessMath.linearDepth(0.5f, near, far, true), 0.01f);
    }

    @Test
    void shouldKeepTheFocusRangeSharpAndBlurFullyOneRangeBeyondIt() {
        assertEquals(0f, PostProcessMath.circleOfConfusion(1000f, 1000f, 400f), EPS);
        assertEquals(0f, PostProcessMath.circleOfConfusion(1200f, 1000f, 400f), EPS, "the edge of the sharp range");
        assertEquals(0.5f, PostProcessMath.circleOfConfusion(1400f, 1000f, 400f), EPS, "half way through the fade");
        assertEquals(0.5f, PostProcessMath.circleOfConfusion(600f, 1000f, 400f), EPS, "nearer blurs the same as farther");
        assertEquals(1f, PostProcessMath.circleOfConfusion(1600f, 1000f, 400f), EPS);
        assertEquals(1f, PostProcessMath.circleOfConfusion(50000f, 1000f, 0f), EPS, "a zero range still fades over a unit");
    }

    @Test
    void shouldPassOnlyWhatIsBrighterThanTheThresholdIntoBloom() {
        assertEquals(0f, PostProcessMath.bloomContribution(0.3f, 0.8f, PostProcessor.BLOOM_KNEE), EPS);
        assertEquals(1f, PostProcessMath.bloomContribution(1f, 0f, PostProcessor.BLOOM_KNEE), EPS);
        float knee = PostProcessMath.bloomContribution(0.8f, 0.8f, PostProcessor.BLOOM_KNEE);
        assertTrue(knee > 0f && knee < 0.1f, "a soft start at the threshold: " + knee);
        assertTrue(PostProcessMath.bloomContribution(0.95f, 0.8f, PostProcessor.BLOOM_KNEE) > knee);
    }

    @Test
    void shouldStretchContrastAroundMidGreyAndHalveTheFrameForTheBlurs() {
        assertEquals(0.5f, PostProcessMath.contrast(0.5f, 1.8f), EPS);
        assertEquals(0.9f, PostProcessMath.contrast(0.7f, 2f), EPS);
        assertEquals(0f, PostProcessMath.contrast(0.1f, 2f), EPS);
        assertEquals(0.5f, PostProcessMath.contrast(0.9f, 0f), EPS);
        assertEquals(640, PostProcessMath.half(1280));
        assertEquals(1, PostProcessMath.half(1));
    }

    @Test
    void shouldTakeEachPixelsOwnDepthInGdxGltfsDepthShader() throws IOException {
        String source;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("net/mgsx/gltf/shaders/depth.fs.glsl")) {
            assertNotNull(in, "gdx-gltf's depth shader is on the class path");
            source = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        String exact = PostProcessor.exactDepthFragment(source);
        assertFalse(exact.contains(PostProcessor.VERTEX_DEPTH_LINE));
        assertTrue(exact.contains(PostProcessor.PIXEL_DEPTH_LINE));
        assertEquals(source.length() - PostProcessor.VERTEX_DEPTH_LINE.length() + PostProcessor.PIXEL_DEPTH_LINE.length(), exact.length());
        assertThrows(IllegalStateException.class, () -> PostProcessor.exactDepthFragment("void main() {}"));
        String twice = source + source;
        assertThrows(IllegalStateException.class, () -> PostProcessor.exactDepthFragment(twice), "the line twice: unclear which");
    }

    @Test
    void shouldResolveTheVolumeAroundTheRenderersCameraEachFrame() {
        Scene scene = new Scene("Post");
        PostProcessVolumeComponent noir = new PostProcessVolumeComponent();
        PostProcessPreset.NOIR.applyTo(noir);
        noir.setGlobal(false);
        noir.setSize(200f, 200f, 200f);
        Entity room = scene.createEntity("Dark Room");
        room.getTransform().setLocalPosition(0f, 0f, -1000f);
        room.addComponent(noir);
        PerspectiveCamera3D camera = new PerspectiveCamera3D(60f, 800f, 600f, 1f, 50000f);
        SceneRenderer3D renderer = new SceneRenderer3D(camera, scene) {
            @Override
            protected ModelBatch createModelBatch() {
                return null;
            }
        };
        renderer.initialize();

        assertFalse(renderer.resolvePostProcess().isActive(), "outside the room");
        camera.getGdxCamera().position.set(0f, 0f, -1050f);
        PostProcessSettings inside = renderer.resolvePostProcess();
        assertTrue(inside.hasGrading());
        assertTrue(inside.hasAmbientOcclusion());
        assertEquals(0f, inside.getSaturation(), EPS);
        assertEquals(inside, renderer.getPostProcessSettings());

        noir.setEnabled(false);
        assertFalse(renderer.resolvePostProcess().isActive(), "a disabled volume does nothing");
        assertTrue(renderer.isPostProcessingEnabled());
        renderer.dispose();
    }
}
