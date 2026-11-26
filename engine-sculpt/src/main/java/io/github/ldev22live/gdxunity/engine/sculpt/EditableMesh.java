package io.github.ldev22live.gdxunity.engine.sculpt;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetProvenance.Provenance;

import java.util.Arrays;
import java.util.Objects;

/**
 * A static mesh document backed by a self-contained glTF 2.0 model. The scene graph, indexed
 * primitives, accessors and material slots retain their glTF indices.
 *
 * @param sourcePath original asset path relative to the project asset root
 * @param sourceSha256 checksum of the imported source file
 * @param provenance licence record at import time
 * @param modelJson self-contained model with embedded resource data
 */
public record EditableMesh(String sourcePath, String sourceSha256, Provenance provenance, String modelJson) {
    public EditableMesh {
        Objects.requireNonNull(sourcePath, "sourcePath cannot be null");
        Objects.requireNonNull(sourceSha256, "sourceSha256 cannot be null");
        Objects.requireNonNull(provenance, "provenance cannot be null");
        Objects.requireNonNull(modelJson, "modelJson cannot be null");
        if (sourcePath.isBlank() || sourcePath.startsWith("/") || sourcePath.contains("\\") || sourcePath.contains(":")
            || Arrays.asList(sourcePath.split("/")).contains("..") || !sourceSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid mesh source path or checksum: " + sourcePath);
        }
        StaticMeshImport.validate(new JsonReader().parse(modelJson));
    }

    /** Returns a fresh model tree for inspection or editing. */
    public JsonValue model() {
        return new JsonReader().parse(modelJson);
    }
}
