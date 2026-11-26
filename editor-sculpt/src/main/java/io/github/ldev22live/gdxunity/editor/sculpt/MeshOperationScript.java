package io.github.ldev22live.gdxunity.editor.sculpt;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.sculpt.EditableMesh;
import io.github.ldev22live.gdxunity.engine.sculpt.StaticMeshImport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A deterministic, reviewable record of mesh edits (roadmap MC.2): the source model, the derived model's project path, and the
 * 3D Modeling operations that turn one into the other, each naming its part, tool, element kind, element indices and amounts.
 * Replaying a script on the unchanged source gives the same bytes a 3D Modeling save writes, so a shipped derived asset can be
 * checked against its source and rebuilt by anyone.
 *
 * <p>File format (JSON): {@code {"schemaVersion": 1, "source": "<project path>", "output": "<project path>", "operations":
 * [{"part": 0, "tool": "Translate", "kind": "Vertex", "indices": [5, 7], "x": 0, "y": 0.4, "z": 0}]}}. Tools and kinds are
 * {@link MeshPartEditor.Tool} and {@link MeshPartEditor.Kind} names; each index is applied as one operation, in order, with the
 * amounts {@link MeshPartEditor#apply} takes.
 */
public final class MeshOperationScript {
    /** The only file format version this reader accepts. */
    public static final int SCHEMA_VERSION = 1;

    /** One recorded operation, applied once per index. */
    public record Operation(int part, MeshPartEditor.Tool tool, MeshPartEditor.Kind kind, List<Integer> indices, float x, float y,
                            float z) {
        public Operation {
            Objects.requireNonNull(tool, "tool cannot be null");
            Objects.requireNonNull(kind, "kind cannot be null");
            indices = List.copyOf(Objects.requireNonNull(indices, "indices cannot be null"));
            if (part < 0 || indices.isEmpty()) {
                throw new IllegalArgumentException("An operation needs a part from 0 and at least one index: part " + part + ", "
                    + indices);
            }
        }
    }

    private final String source;
    private final String output;
    private final List<Operation> operations;

    public MeshOperationScript(String source, String output, List<Operation> operations) {
        this.source = requirePath(source, "source");
        this.output = requirePath(output, "output");
        this.operations = List.copyOf(Objects.requireNonNull(operations, "operations cannot be null"));
        if (this.operations.isEmpty()) {
            throw new IllegalArgumentException("A script needs at least one operation");
        }
    }

    /** Reads a script file. */
    public static MeshOperationScript read(Path file) throws IOException {
        Objects.requireNonNull(file, "file cannot be null");
        JsonValue json;
        try {
            json = new JsonReader().parse(Files.readString(file));
        } catch (RuntimeException e) {
            throw new IOException("Not a mesh operation script: " + file + ": " + e.getMessage(), e);
        }
        if (json == null || json.getInt("schemaVersion", -1) != SCHEMA_VERSION) {
            throw new IOException("Unsupported mesh operation script version in " + file);
        }
        try {
            List<Operation> operations = new ArrayList<>();
            for (JsonValue op : json.require("operations")) {
                List<Integer> indices = new ArrayList<>();
                for (JsonValue index : op.require("indices")) {
                    indices.add(index.asInt());
                }
                operations.add(new Operation(op.getInt("part"), MeshPartEditor.Tool.valueOf(op.getString("tool")),
                    MeshPartEditor.Kind.valueOf(op.getString("kind")), indices, op.getFloat("x", 0f), op.getFloat("y", 0f),
                    op.getFloat("z", 0f)));
            }
            return new MeshOperationScript(json.getString("source"), json.getString("output"), operations);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid mesh operation script " + file + ": " + e.getMessage(), e);
        }
    }

    /** The source model, relative to the project. */
    public String source() {
        return source;
    }

    /** Where the derived model belongs, relative to the project. */
    public String output() {
        return output;
    }

    public List<Operation> operations() {
        return operations;
    }

    /**
     * Applies every operation to fresh drafts of {@code mesh}, as the 3D Modeling panel would.
     *
     * @throws IllegalArgumentException when an operation names a missing part or element, or does nothing
     */
    public List<MeshPartEditor> apply(EditableMesh mesh) {
        List<MeshPartEditor> drafts = MeshPartEditor.open(Objects.requireNonNull(mesh, "mesh cannot be null"));
        applyTo(drafts);
        return drafts;
    }

    /**
     * Applies every operation to drafts already open, for example a 3D Modeling session's, each as one undoable step of its part.
     *
     * @throws IllegalArgumentException when an operation names a missing part or element, or does nothing
     */
    public void applyTo(List<MeshPartEditor> drafts) {
        Objects.requireNonNull(drafts, "drafts cannot be null");
        for (Operation op : operations) {
            if (op.part() >= drafts.size()) {
                throw new IllegalArgumentException("No part " + op.part() + " in " + source + " (" + drafts.size() + " parts)");
            }
            for (int index : op.indices()) {
                if (!drafts.get(op.part()).apply(op.tool(), op.kind(), index, op.x(), op.y(), op.z())) {
                    throw new IllegalArgumentException(op.tool() + " " + op.kind() + " " + index + " changed nothing in part "
                        + op.part());
                }
            }
        }
    }

    /** The derived model's bytes: the source read from {@code assetRoot}, the operations applied, encoded as a save writes it. */
    public byte[] derive(Path assetRoot) throws IOException {
        EditableMesh original = StaticMeshImport.read(Objects.requireNonNull(assetRoot, "assetRoot cannot be null"), Path.of(source));
        return MeshSaveSession.encode(MeshDraftModel.build(original, apply(original)), output);
    }

    /** Command line: asset root and script file; writes the derived model at the script's output under the root, never over a file. */
    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            throw new IllegalArgumentException("Expected: asset-root script.json");
        }
        Path root = Path.of(args[0]);
        MeshOperationScript script = read(Path.of(args[1]));
        Path target = root.resolve(script.output()).normalize();
        if (!target.startsWith(root.normalize())) {
            throw new IOException("Output leaves the asset root: " + script.output());
        }
        if (Files.exists(target)) {
            throw new IOException("Refusing to overwrite " + target);
        }
        Files.createDirectories(target.getParent());
        Files.write(target, script.derive(root));
    }

    private static String requirePath(String path, String what) {
        if (path == null || path.isBlank() || path.startsWith("/") || path.contains("..") || path.contains("\\")) {
            throw new IllegalArgumentException(what + " must be a project-relative path with forward slashes: " + path);
        }
        return path;
    }
}
