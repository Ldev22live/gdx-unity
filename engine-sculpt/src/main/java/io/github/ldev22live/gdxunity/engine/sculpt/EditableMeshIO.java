package io.github.ldev22live.gdxunity.engine.sculpt;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter.OutputType;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetProvenance.Kind;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetProvenance.Provenance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/** Reads and atomically saves project-owned editable mesh documents. */
public final class EditableMeshIO {
    /** Filename extension for editable mesh source files. */
    public static final String EXTENSION = ".mesh";

    private static final int VERSION = 1;

    private EditableMeshIO() {
    }

    /** Opens a versioned mesh document, rejecting malformed or unsupported data. */
    public static EditableMesh read(Path file) throws IOException {
        Objects.requireNonNull(file, "file cannot be null");
        try {
            JsonValue root = new JsonReader().parse(Files.readString(file));
            if (root.getInt("version") != VERSION) {
                throw new IllegalArgumentException("Unsupported mesh version: " + root.getInt("version"));
            }
            Provenance provenance = new Provenance(Kind.valueOf(root.getString("provenanceKind")),
                root.getString("licence"), root.getString("provenanceSummary"), root.getString("manifestSha256"));
            return new EditableMesh(root.getString("sourcePath"), root.getString("sourceSha256"), provenance,
                root.require("model").toJson(OutputType.json));
        } catch (RuntimeException e) {
            throw new IOException("Cannot read mesh " + file + ": " + e.getMessage(), e);
        }
    }

    /** Atomically saves a document at a project-owned .mesh path in an existing directory. */
    public static void write(Path file, EditableMesh mesh) throws IOException {
        Objects.requireNonNull(file, "file cannot be null");
        Objects.requireNonNull(mesh, "mesh cannot be null");
        if (!file.getFileName().toString().endsWith(EXTENSION)) {
            throw new IllegalArgumentException("Expected a " + EXTENSION + " destination: " + file);
        }
        JsonValue root = new JsonValue(JsonValue.ValueType.object);
        root.addChild("version", new JsonValue(VERSION));
        root.addChild("sourcePath", new JsonValue(mesh.sourcePath()));
        root.addChild("sourceSha256", new JsonValue(mesh.sourceSha256()));
        root.addChild("provenanceKind", new JsonValue(mesh.provenance().kind().name()));
        root.addChild("licence", new JsonValue(mesh.provenance().licence()));
        root.addChild("provenanceSummary", new JsonValue(mesh.provenance().summary()));
        root.addChild("manifestSha256", new JsonValue(mesh.provenance().sha256()));
        root.addChild("model", mesh.model());

        Path destination = file.toAbsolutePath();
        Path temporary = Files.createTempFile(destination.getParent(), ".mesh-", ".tmp");
        try {
            Files.writeString(temporary, root.toJson(OutputType.json));
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}