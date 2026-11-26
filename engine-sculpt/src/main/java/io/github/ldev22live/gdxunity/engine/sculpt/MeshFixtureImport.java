package io.github.ldev22live.gdxunity.engine.sculpt;

import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter.OutputType;
import io.github.ldev22live.gdxunity.engine.core.asset.ThirdPartyManifest;
import io.github.ldev22live.gdxunity.engine.core.asset.ThirdPartyManifest.Entry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Imports a licence-verified CC0 fixture as a separate editable source with an audit of every input read. */
public final class MeshFixtureImport {
    private MeshFixtureImport() {}

    /** Command-line entry point: asset root, relative glTF or GLB and a new output directory. */
    public static void main(String[] args) throws IOException {
        if (args.length != 3) throw new IllegalArgumentException("Expected: asset-root relative-model new-output-directory");
        write(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]));
    }

    /** Creates model.mesh, an editor-loadable model.gltf and manifest.json together, or fails before publishing. */
    public static void write(Path assetRoot, Path source, Path destination) throws IOException {
        Objects.requireNonNull(assetRoot, "assetRoot cannot be null");
        Objects.requireNonNull(source, "source cannot be null");
        Objects.requireNonNull(destination, "destination cannot be null");
        Path output = destination.toAbsolutePath().normalize();
        if (Files.exists(output)) throw new IOException("Choose a new fixture directory: " + output);
        ThirdPartyManifest licences = ThirdPartyManifest.read(assetRoot.toFile());
        JsonValue inputs = new JsonValue(JsonValue.ValueType.array);
        Set<String> seen = new LinkedHashSet<>();
        EditableMesh mesh = StaticMeshImport.read(assetRoot, source, (path, bytes) -> {
            Entry entry = licences.find(path).orElseThrow(() -> new IllegalArgumentException("No licence record for " + path));
            if (!"CC0-1.0".equals(entry.licence())) throw new IllegalArgumentException("Fixture requires CC0-1.0: " + path);
            var file = entry.files().stream().filter(item -> item.path().equals(path)).findFirst().orElseThrow();
            String hash = StaticMeshImport.checksum(bytes);
            if (!hash.equals(file.sha256()) || bytes.length != file.bytes()) {
                throw new IllegalArgumentException("Source differs from licence manifest: " + path);
            }
            if (seen.add(path)) {
                JsonValue record = object();
                put(record, "path", path);
                put(record, "original", file.original());
                put(record, "sha256", hash);
                put(record, "providerId", entry.id());
                put(record, "source", entry.source());
                put(record, "licence", entry.licence());
                put(record, "licenceEvidence", entry.licenceEvidence());
                put(record, "retrieved", entry.retrieved());
                put(record, "conversion", entry.conversion());
                JsonValue authors = new JsonValue(JsonValue.ValueType.array);
                entry.authors().forEach(author -> authors.addChild(new JsonValue(author)));
                record.addChild("authors", authors);
                inputs.addChild(record);
            }
        });
        Files.createDirectories(output.getParent());
        Path staging = Files.createTempDirectory(output.getParent(), ".derived-mesh-");
        try {
            Path editable = staging.resolve("model.mesh");
            Path exchange = staging.resolve("model.gltf");
            EditableMeshIO.write(editable, mesh);
            Files.writeString(exchange, mesh.modelJson());
            JsonValue record = object();
            record.addChild("schemaVersion", new JsonValue(1));
            put(record, "importer", "Forgeroom static mesh importer / .mesh v1");
            put(record, "sourcePath", mesh.sourcePath());
            record.addChild("inputs", inputs);
            JsonValue outputs = object();
            for (Path file : new Path[]{editable, exchange}) {
                put(outputs, file.getFileName().toString(), StaticMeshImport.checksum(Files.readAllBytes(file)));
            }
            record.addChild("outputs", outputs);
            Files.writeString(staging.resolve("manifest.json"), record.prettyPrint(OutputType.json, 0) + "\n");
            Files.move(staging, output);
        } finally {
            if (Files.exists(staging)) {
                Files.deleteIfExists(staging.resolve("manifest.json"));
                Files.deleteIfExists(staging.resolve("model.gltf"));
                Files.deleteIfExists(staging.resolve("model.mesh"));
                Files.delete(staging);
            }
        }
    }

    private static JsonValue object() { return new JsonValue(JsonValue.ValueType.object); }
    private static void put(JsonValue record, String name, String value) { record.addChild(name, new JsonValue(value)); }
}
