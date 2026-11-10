package io.github.ldev22live.gdxunity.editor.sculpt;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.action.EditorActionRegistry;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.render.ColliderGeometry;
import io.github.ldev22live.gdxunity.engine.render.ModelAssets;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.MeshGeometry3D;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import io.github.ldev22live.gdxunity.engine.sculpt.SculptMesh;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exporting a sculpt as a glTF model and using it like any other model (TD-038, 19-C). */
class SculptModelExportTest {

    private static GL20 previousGl;
    private static Application previousApp;

    @TempDir
    Path root;

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
        previousGl = Gdx.gl20;
        previousApp = Gdx.app;
        GL20 gl = stub(GL20.class);
        Gdx.gl = gl;
        Gdx.gl20 = gl;
        Gdx.app = stub(Application.class);
    }

    @AfterAll
    static void removeStubs() {
        Gdx.gl = previousGl;
        Gdx.gl20 = previousGl;
        Gdx.app = previousApp;
    }

    /** Answers every call with a harmless value, enough for libGDX to build meshes without a graphics card. */
    private static <T> T stub(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(SculptModelExportTest.class.getClassLoader(), new Class<?>[] {type},
            (proxy, method, args) -> {
                Class<?> returnType = method.getReturnType();
                if (returnType == int.class) return 1;
                if (returnType == long.class) return 0L;
                if (returnType == boolean.class) return false;
                if (returnType == float.class) return 0f;
                if (returnType == String.class) return "";
                return null;
            }));
    }

    private EditorState newEditor(SculptSession[] sessionOut, List<AssetReference> exported, EditorActionRegistry registry) {
        EditorState editorState = new EditorState(new SceneSerializer());
        editorState.setActiveScene(new Scene("Level"));
        SculptSession session = new SculptSession(new SculptAssets(root.toFile()));
        session.setSoftwareRendering(false);
        sessionOut[0] = session;
        SculptActions.register(registry, editorState, session, () -> new Vector3());
        SculptActions.registerExport(registry, editorState, session, exported::add);
        return editorState;
    }

    @Test
    void shouldWriteAModelThatTheModelLoaderReadsBackWithTheSameTrianglesAndSize() throws IOException {
        SculptAssets assets = new SculptAssets(root.toFile());
        AssetReference sculpt = assets.create("Rock", 50f, 16);
        SculptMesh mesh = assets.load(sculpt);

        AssetReference model = assets.exportModel(sculpt, mesh, new float[] {0.8f, 0.7f, 0.6f, 1f});

        assertEquals("models/Rock.glb", model.getPath());
        assertTrue(Files.size(root.resolve("models/Rock.glb")) > mesh.getTriangleCount() * 3 * 2);
        assertFalse(Files.exists(root.resolve("models/Rock.glb.tmp")));
        ModelAssets models = new ModelAssets(root.toFile());
        Scene scene = new Scene("Check");
        Entity entity = scene.createEntity("Placed");
        entity.addComponent(new ModelComponent(model.getPath()));
        MeshGeometry3D geometry = new ColliderGeometry(models).geometryOf(entity);
        models.dispose();

        assertEquals(mesh.getTriangleCount(), geometry.indices().length / 3);
        float high = 0f;
        for (float p : geometry.positions()) {
            high = Math.max(high, Math.abs(p));
        }
        assertEquals(50f, high, 1e-2f, "a default Model component (import scale 100) shows it at the size it was sculpted");
    }

    @Test
    void shouldReplaceTheModelWhenExportedAgainAfterMoreStrokes() throws IOException {
        SculptAssets assets = new SculptAssets(root.toFile());
        AssetReference sculpt = assets.create("Rock", 50f, 8);
        SculptMesh mesh = assets.load(sculpt);
        float[] clay = {0.5f, 0.5f, 0.5f, 1f};
        assets.exportModel(sculpt, mesh, clay);
        byte[] first = Files.readAllBytes(root.resolve("models/Rock.glb"));

        mesh.getPositions()[0] += 5f;
        assets.exportModel(sculpt, mesh, clay);

        assertNotEquals(0, first.length);
        assertFalse(Arrays.equals(first, Files.readAllBytes(root.resolve("models/Rock.glb"))), "the new shape is in the file");
    }

    @Test
    void shouldExportTheSelectedSculptAndTellTheApp() {
        EditorActionRegistry registry = new EditorActionRegistry();
        SculptSession[] session = new SculptSession[1];
        List<AssetReference> exported = new ArrayList<>();
        EditorState editorState = newEditor(session, exported, registry);
        registry.execute(SculptActions.CREATE_SCULPT);

        assertTrue(registry.execute(SculptActions.EXPORT_MODEL));

        assertEquals(List.of("models/Sculpt.glb"), exported.stream().map(AssetReference::getPath).toList());
        assertTrue(Files.isRegularFile(root.resolve("models/Sculpt.glb")));
        assertTrue(editorState.getStatusMessage().startsWith("Exported models/Sculpt.glb"), editorState.getStatusMessage());
    }

    @Test
    void shouldSayWhenThereIsNothingToExport() {
        SculptSession[] session = new SculptSession[1];
        EditorState editorState = newEditor(session, new ArrayList<>(), new EditorActionRegistry());

        assertNull(SculptActions.exportModel(editorState, session[0], editorState.getActiveScene().createEntity("Empty")));

        assertEquals("Select a sculpt to export", editorState.getStatusMessage());
    }

    @Test
    void shouldMakeSmallerSculptsAndWarnWhereTheViewIsDrawnOnTheCpu() throws IOException {
        EditorActionRegistry registry = new EditorActionRegistry();
        SculptSession[] session = new SculptSession[1];
        EditorState editorState = newEditor(session, new ArrayList<>(), registry);
        session[0].setSoftwareRendering(true);
        assertEquals(SculptAssets.SOFTWARE_TRIANGLE_LIMIT, session[0].getTriangleLimit());

        registry.execute(SculptActions.CREATE_SCULPT);

        SculptDocument small = session[0].getDocumentOf(editorState.getSelectedEntity());
        assertEquals(6 * 64 * 64 * 2, small.getSurface().getMesh().getTriangleCount());
        assertNull(session[0].triangleWarning(small), "49,152 triangles are fine");
        session[0].setSoftwareRendering(false);
        assertEquals(Integer.MAX_VALUE, session[0].getTriangleLimit());

        SculptDocument big = session[0].open(new SculptAssets(root.toFile()).create("Big"));
        assertNull(session[0].triangleWarning(big), "no limit on a machine with a graphics card");
        session[0].setSoftwareRendering(true);
        String warning = session[0].triangleWarning(big);
        assertNotNull(warning);
        assertTrue(warning.contains("196,608") && warning.contains("100,000"), warning);
    }
}
