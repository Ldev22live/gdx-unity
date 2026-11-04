package io.github.ldev22live.gdxunity.engine.render;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Loading glTF through gdx-gltf (TD-030) and posing clips headlessly, with a stub GL. */
class ModelAssetsTest {

    @TempDir
    Path assetRoot;

    private ModelAssets assets;

    @BeforeAll
    static void setUpNatives() {
        GdxTestHelper.initNatives();
        GltfTestFiles.installStubs();
    }

    @AfterAll
    static void removeStubs() {
        GltfTestFiles.removeStubs();
    }

    @BeforeEach
    void setUp() throws IOException {
        Files.createDirectories(assetRoot.resolve("models"));
        Files.writeString(assetRoot.resolve("models/strip.gltf"), GltfTestFiles.skinnedStrip());
        assets = new ModelAssets(assetRoot.toFile());
    }

    @AfterEach
    void tearDown() {
        assets.dispose();
    }

    @Test
    void shouldReportClipsBonesAndBoundsWhenModelLoads() {
        ModelAssets.LoadedModel loaded = assets.get("models/strip.gltf");

        assertNotNull(loaded);
        ModelComponent.ModelInfo info = loaded.info();
        assertNull(info.error());
        assertEquals(List.of("Bend"), info.animationNames());
        assertEquals(1f, info.animationDurations().get("Bend"), 1e-4f);
        assertEquals(2, info.boneCount());
        assertNotNull(info.bounds());
        assertEquals(2f, info.bounds().getHeight(), 1e-3f);
    }

    @Test
    void shouldLoadBundledSampleModel() {
        // assets/models/BendingStrip.gltf ships for the Phase 17 manual check; tests run in the module folder
        ModelAssets bundled = new ModelAssets(new java.io.File("../assets"));
        try {
            ModelAssets.LoadedModel loaded = bundled.get("models/BendingStrip.gltf");

            assertNotNull(loaded, bundled.info("models/BendingStrip.gltf").error());
            assertEquals(2f, loaded.info().animationDurations().get("Bend"), 1e-4f);
            assertEquals(2, loaded.info().boneCount());
        } finally {
            bundled.dispose();
        }
    }

    @Test
    void shouldLoadEachFileOnce() {
        assertSame(assets.get("models/strip.gltf").model(), assets.get("models/strip.gltf").model());
    }

    @Test
    void shouldRememberWhyMissingFileFailed() {
        assertNull(assets.get("models/missing.glb"));

        String error = assets.info("models/missing.glb").error();
        assertNotNull(error);
        assertTrue(error.contains("not found"), error);
    }

    @Test
    void shouldRememberWhyBrokenFileFailed() throws IOException {
        Files.writeString(assetRoot.resolve("models/broken.gltf"), "{ this is not gltf");

        assertNull(assets.get("models/broken.gltf"));
        assertNotNull(assets.info("models/broken.gltf").error());
    }

    @Test
    void shouldPoseClipAtExactTimeAndReturnToRestPose() {
        ModelInstance instance = new ModelInstance(assets.get("models/strip.gltf").model());
        ModelPoser poser = new ModelPoser(instance);
        Node tip = instance.getNode("Tip", true);

        assertTrue(poser.pose("Bend", 0.5f));
        assertEquals(45f, tipAngle(tip), 0.5f);
        assertEquals("Bend", poser.getAppliedAnimation());

        // Between keys LibGDX blends rotations with its own interpolation: about 21.7 degrees, not an exact 22.5
        assertTrue(poser.pose("Bend", 0.25f));
        assertEquals(22.5f, tipAngle(tip), 1.5f);

        poser.pose(null, 0f);
        assertEquals(0f, tipAngle(tip), 0.01f);
        assertNull(poser.getAppliedAnimation());
    }

    @Test
    void shouldReportLoadedInfoToComponentWhenPosingForDraw() {
        SceneRenderer3D renderer = new SceneRenderer3D();
        renderer.setModelAssets(assets);
        ModelComponent component = new ModelComponent("models/strip.gltf");
        component.setAnimation("Bend");
        component.setTime(0.5f);

        ModelInstance instance = renderer.posedInstanceFor(component);

        assertNotNull(instance);
        assertEquals(1f, component.getAnimationDuration(), 1e-4f);
        assertEquals(45f, tipAngle(instance.getNode("Tip", true)), 0.5f);
        assertSame(instance, renderer.getModelInstance(component));
    }

    @Test
    void shouldScaleModelDrawsByImportScale() {
        Scene scene = new Scene("Test");
        Entity entity = scene.createEntity("Model");
        entity.getTransform().setLocalPosition(10f, 0f, 0f);
        ModelComponent component = entity.addComponent(new ModelComponent("models/strip.gltf"));
        component.setImportScale(50f);

        List<SceneRenderer3D.ModelDraw> draws = SceneRenderer3D.collectModels(scene);

        assertEquals(1, draws.size());
        Vector3 top = new Vector3(0f, 2f, 0f).mul(draws.get(0).transform());
        assertEquals(new Vector3(10f, 100f, 0f), top);
    }

    private static float tipAngle(Node tip) {
        Matrix4 local = tip.localTransform;
        return local.getRotation(new Quaternion(), true).getAxisAngle(new Vector3());
    }

    @Test
    void shouldLoadModelsThroughAnAssetSourceAsTheWebRuntimeDoes() throws IOException {
        // Stored under another folder than the scene names: only the source knows where it is
        Files.createDirectories(assetRoot.resolve("bundle/models"));
        Files.writeString(assetRoot.resolve("bundle/models/strip.gltf"), GltfTestFiles.skinnedStrip());
        List<String> requested = new ArrayList<>();
        AssetSource bundled = reference -> {
            requested.add(reference.getPath());
            return new FileHandle(assetRoot.resolve("bundle").resolve(reference.getPath()).toFile());
        };
        ModelAssets fromSource = new ModelAssets(bundled);
        try {
            ModelAssets.LoadedModel loaded = fromSource.get("models/strip.gltf");
            ModelAssets.LoadedModel missing = fromSource.get("models/none.gltf");

            assertNotNull(loaded);
            assertNull(loaded.info().error());
            assertNull(missing);
            assertEquals("Model file not found: models/none.gltf", fromSource.info("models/none.gltf").error());
            assertEquals(List.of("models/strip.gltf", "models/none.gltf"), requested);
        } finally {
            fromSource.dispose();
        }
    }
}
