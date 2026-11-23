package io.github.ldev22live.gdxunity.editor.desktop;

import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.editor.animation.AnimationAssets;
import io.github.ldev22live.gdxunity.engine.animation.AnimatedProperties;
import io.github.ldev22live.gdxunity.engine.animation.AnimationClip;
import io.github.ldev22live.gdxunity.engine.animation.AnimatorComponent;
import io.github.ldev22live.gdxunity.engine.animation.ClipSampler;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.render.RenderQuality3D;
import io.github.ldev22live.gdxunity.engine.render.SceneRenderer3D;
import io.github.ldev22live.gdxunity.engine.render.SkyLightMaps;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.lighting.DirectionalLightComponent;
import io.github.ldev22live.gdxunity.engine.scene.lighting.SkyLightComponent;
import io.github.ldev22live.gdxunity.engine.scene.lighting.ToneMappingComponent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The startup scene gives the 3D view something to show, and its sample assets exist and play. */
class InitialScene3DContentTest {

    /** Tests run in the module folder; the project's assets are one level up. */
    private static final File ASSET_ROOT = new File("../assets");

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    @Test
    void shouldContain3DObjectsStandingOnTheGrid() {
        Scene scene = EditorApplication.createInitialScene();

        assertEquals(3, SceneRenderer3D.collectPrimitives(scene).size(), "floor, crate and ball");
        assertEquals(1, SceneRenderer3D.collectModels(scene).size());
        assertNotNull(scene.findEntityByName("Sun").getComponent(DirectionalLightComponent.class));
        for (SceneRenderer3D.PrimitiveDraw draw : SceneRenderer3D.collectPrimitives(scene)) {
            float bottom = draw.component().getEntity().getTransform().getWorldPosition().y
                - draw.component().getSize().y / 2f;
            assertTrue(bottom >= -1f, draw.component().getEntity().getName() + " sinks below the grid");
        }
        // The 2D sample is still there
        assertNotNull(scene.findEntityByName("ParentCore"));
    }

    @Test
    void shouldLightTheStartupSceneWithShadowsSkyAndToneMapping() {
        Scene scene = EditorApplication.createInitialScene();
        SceneRenderer3D renderer = new SceneRenderer3D(null, scene) {
            @Override
            protected ModelBatch createModelBatch() {
                return null;
            }

            @Override
            protected SkyLightMaps bakeSkyLight(SkyLightComponent sky, Vector3 sunDirection, RenderQuality3D quality) {
                // No GL context: stand-in maps that add nothing
                return new SkyLightMaps() {
                    @Override
                    public void applyTo(Environment environment) {
                    }

                    @Override
                    public void dispose() {
                    }
                };
            }
        };
        renderer.initialize();

        assertSame(scene.findEntityByName("Sun").getComponent(DirectionalLightComponent.class), renderer.getShadowCaster());
        assertNotNull(renderer.getSkyLightMaps(), "sky light is active");
        assertEquals(ToneMappingComponent.Operator.ACES_FILMIC, renderer.getToneMappingOperator());
        assertEquals(EditorApplication.INITIAL_SKY_INTENSITY,
            scene.findEntityByName("Sky Light").getComponent(SkyLightComponent.class).getIntensity());
        renderer.dispose();
    }

    @Test
    void shouldShipSampleClipThatPlaysOnTheBall() throws Exception {
        Scene scene = EditorApplication.createInitialScene();
        Entity ball = scene.findEntityByName("Ball");
        AnimatorComponent animator = ball.getComponent(AnimatorComponent.class);

        AnimationClip clip = new AnimationAssets(ASSET_ROOT).load(new AssetReference(animator.getClipPath()));

        assertTrue(ClipSampler.problems(clip, ball, AnimatedProperties.createDefault()).isEmpty());
        ClipSampler.apply(clip, clip.getDuration() / 2f, ball, AnimatedProperties.createDefault());
        assertEquals(250f, ball.getTransform().getLocalPosition().y, 1e-3f);
    }

    @Test
    void shouldShipSampleModel() {
        ModelComponent model = EditorApplication.createInitialScene().findEntityByName("BendingStrip")
            .getComponent(ModelComponent.class);

        assertTrue(new File(ASSET_ROOT, model.getModelPath()).isFile(), model.getModelPath());
        assertEquals("Bend", model.getAnimation());
    }
}
