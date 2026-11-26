package io.github.ldev22live.gdxunity.editor.sculpt;

import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.core.asset.ThirdPartyManifest;
import io.github.ldev22live.gdxunity.engine.sculpt.StaticMeshImport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Roadmap MC.2: the 3D house slice's derived roof is rebuilt exactly from its CC0 source and its operation script. */
class MeshOperationScriptTest {
    private static final Path ASSETS = Path.of("..", "assets");
    private static final Path SLICE = Path.of("..", "fixtures", "mesh-sources", "derived", "kenney-house-roof");
    /** The roof is raised by this much, in model units. */
    private static final float RAISE = 0.4f;

    @TempDir Path temp;

    @BeforeAll static void initNatives() { GdxNativesLoader.load(); }

    @Test void shouldRebuildTheShippedDerivedRoofByteForByteFromItsSourceAndScript() throws IOException {
        MeshOperationScript script = MeshOperationScript.read(SLICE.resolve("operations.json"));
        byte[] source = Files.readAllBytes(ASSETS.resolve(script.source()));

        assertArrayEquals(Files.readAllBytes(SLICE.resolve("model.gltf")), script.derive(ASSETS));
        assertArrayEquals(source, Files.readAllBytes(ASSETS.resolve(script.source())), "the CC0 original is only read");
        assertEquals("models/edited/Roof NE raised.gltf", script.output());
    }

    @Test void shouldRaiseOnlyTheRoofTop() throws IOException {
        MeshOperationScript script = MeshOperationScript.read(SLICE.resolve("operations.json"));
        var original = MeshPartEditor.open(StaticMeshImport.read(ASSETS, Path.of(script.source()))).getFirst().geometry().vertices();
        var raised = script.apply(StaticMeshImport.read(ASSETS, Path.of(script.source()))).getFirst().geometry().vertices();
        float top = (float) original.stream().mapToDouble(MeshPartEditor.Vertex::y).max().orElseThrow();
        assertEquals(original.size(), raised.size());
        for (int i = 0; i < original.size(); i++) {
            float expected = original.get(i).y() >= top - 1e-3f ? original.get(i).y() + RAISE : original.get(i).y();
            assertEquals(expected, raised.get(i).y(), 1e-5f, "vertex " + i);
            assertEquals(original.get(i).x(), raised.get(i).x(), 0f);
        }
    }

    @Test void shouldRecordEveryInputOutputAndLicenceInTheManifest() throws IOException {
        JsonValue manifest = new JsonReader().parse(Files.readString(SLICE.resolve("manifest.json")));
        MeshOperationScript script = MeshOperationScript.read(SLICE.resolve("operations.json"));
        assertEquals(script.source(), manifest.getString("sourcePath"));
        assertEquals(script.output(), manifest.getString("derivedPath"));
        ThirdPartyManifest licences = ThirdPartyManifest.read(ASSETS.toFile());
        for (JsonValue input : manifest.require("inputs")) {
            String path = input.getString("path");
            assertEquals("CC0-1.0", input.getString("licence"), path);
            assertEquals(sha256(Files.readAllBytes(ASSETS.resolve(path))), input.getString("sha256"), path);
            assertTrue(licences.find(path).isPresent(), "licence record for " + path);
        }
        for (JsonValue output : manifest.require("outputs")) {
            assertEquals(sha256(Files.readAllBytes(SLICE.resolve(output.name))), output.asString(), output.name);
        }
    }

    @Test void shouldRefuseScriptsThatCannotBeReplayed() throws IOException {
        Path script = temp.resolve("bad.json");
        Files.writeString(script, "{\"schemaVersion\": 2, \"source\": \"a.glb\", \"output\": \"b.gltf\", \"operations\": []}");
        assertThrows(IOException.class, () -> MeshOperationScript.read(script), "unknown version");
        Files.writeString(script, "{\"schemaVersion\": 1, \"source\": \"a.glb\", \"output\": \"../b.gltf\", \"operations\": "
            + "[{\"part\": 0, \"tool\": \"Translate\", \"kind\": \"Vertex\", \"indices\": [0], \"y\": 1}]}");
        assertThrows(IOException.class, () -> MeshOperationScript.read(script), "an output outside the project");
        Files.writeString(script, "{\"schemaVersion\": 1, \"source\": \"a.glb\", \"output\": \"b.gltf\", \"operations\": "
            + "[{\"part\": 0, \"tool\": \"Spin\", \"kind\": \"Vertex\", \"indices\": [0]}]}");
        assertThrows(IOException.class, () -> MeshOperationScript.read(script), "an unknown tool");

        MeshOperationScript roof = MeshOperationScript.read(SLICE.resolve("operations.json"));
        var mesh = StaticMeshImport.read(ASSETS, Path.of(roof.source()));
        MeshOperationScript missing = new MeshOperationScript(roof.source(), roof.output(), List.of(
            new MeshOperationScript.Operation(0, MeshPartEditor.Tool.Translate, MeshPartEditor.Kind.Vertex, List.of(100000), 0, 1, 0)));
        assertThrows(IllegalArgumentException.class, () -> missing.apply(mesh), "an element the source does not have");
        MeshOperationScript nothing = new MeshOperationScript(roof.source(), roof.output(), List.of(
            new MeshOperationScript.Operation(0, MeshPartEditor.Tool.Translate, MeshPartEditor.Kind.Vertex, List.of(5), 0, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> nothing.apply(mesh), "an operation that changes nothing");
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
