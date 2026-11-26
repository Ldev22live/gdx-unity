package io.github.ldev22live.gdxunity.editor.sculpt;

import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter.OutputType;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.command.ModifyComponentPropertyCommand;
import io.github.ldev22live.gdxunity.editor.core.prefab.PrefabService;
import io.github.ldev22live.gdxunity.engine.scene.prefab.PrefabInstanceComponent;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetProvenance;
import io.github.ldev22live.gdxunity.engine.core.asset.ThirdPartyManifest;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.sculpt.EditableMesh;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Saves the detached part and tracks the last saved revision for shared updates. */
public final class MeshSaveSession {
    public enum Mode {
        NEW("Save as new model"), UPDATE("Update project-owned model"), UNIQUE("Create unique instance mesh"),
        /** MC.2: a new derived model bound to this part in the prefab itself, so every instance of the prefab shows it. */
        PREFAB("Edit shared prefab (every instance)");
        private final String label;
        Mode(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    /** Canonical project location for models created by the modeling workspace. */
    private static final String DIRECTORY = "models/edited";
    private static final String ORIGIN = "forgeroomMeshSource";
    private final Path root;
    private final Entity part;
    private final EditableMesh original;
    private final List<MeshPartEditor> drafts;
    private String target;
    private String expectedHash;

    public MeshSaveSession(Path root, MeshWorkspace.OpenPart opened) throws IOException {
        this.root = Objects.requireNonNull(root, "root cannot be null").toRealPath();
        this.part = opened.entity();
        this.original = opened.mesh();
        this.drafts = MeshPartEditor.open(original);
        this.target = original.sourcePath();
        this.expectedHash = original.sourceSha256();
    }

    /** The same primitive drafts that the panel edits. */
    public List<MeshPartEditor> drafts() { return drafts; }
    /** The project-relative destination for Update. */
    public String target() { return target; }

    /** Describes shared updates before any write, including possible uses outside the open scene. */
    public String updateWarning(EditorState state) throws IOException {
        checkUpdate();
        long others = state.getActiveScene().getAllEntities().stream().filter(entity -> entity != part)
            .map(entity -> entity.getComponent(ModelComponent.class)).filter(Objects::nonNull)
            .filter(component -> sameTarget(component.getModelPath())).count();
        return "Update " + target + "? " + others + " other instance(s) in this scene use it. Saved scenes and prefabs referencing it also change.";
    }

    /**
     * Describes an Edit Shared Prefab save before any write: the prefab, how many of its instances this scene holds, and that
     * other scenes placing it change too.
     *
     * @throws IOException when the part is not part of a prefab instance (there is no shared prefab to edit)
     */
    public String prefabWarning(EditorState state) throws IOException {
        Objects.requireNonNull(state, "state cannot be null");
        PrefabService prefabs = state.getPrefabService();
        PrefabInstanceComponent link = PrefabService.linkOf(part);
        if (prefabs == null || link == null || PrefabService.instanceRootOf(part) == null) {
            throw new IOException(part.getName() + " is not part of a prefab instance. Create a unique instance mesh instead.");
        }
        int instances = prefabs.countInstances(part);
        return "Edit shared prefab " + link.getPrefabPath() + "? All " + instances + " instance(s) in this scene change their "
            + part.getName() + " to the new model. Other scenes that place this prefab change when next opened.";
    }

    private boolean sameTarget(String path) {
        if (path == null) return false;
        return root.resolve(path).normalize().equals(root.resolve(target));
    }

    private Path checkUpdate() throws IOException {
        Path file = root.resolve(target).toRealPath();
        if (!file.startsWith(root) || !file.equals(root.resolve(target))) throw new IOException("Model path changed: " + target);
        String relative = target.toLowerCase(Locale.ROOT);
        var provenance = AssetProvenance.read(root.toFile()).of(target);
        if (("/" + relative + "/").contains("/thirdparty/") || provenance.kind() == AssetProvenance.Kind.THIRD_PARTY) {
            throw new IOException("Provider original is read-only. Save as new model or create a unique instance.");
        }
        // Match manifest paths case-insensitively as well on case-insensitive filesystems.
        for (var entry : ThirdPartyManifest.read(root.toFile()).entries()) {
            if (entry.files().stream().anyMatch(record -> record.path().equalsIgnoreCase(target))) {
                throw new IOException("Provider original is read-only: " + target);
            }
        }
        if (!relative.startsWith(DIRECTORY + "/") && provenance.kind() != AssetProvenance.Kind.ORIGINAL) {
            throw new IOException("Project ownership is unrecorded. Save as new model first.");
        }
        if (!digest(Files.readAllBytes(file)).equals(expectedHash)) throw new IOException("Model changed on disk. Reopen " + target);
        return file;
    }

    /**
     * NEW creates a reusable asset, UNIQUE also rebinds the selected entity (an instance override), PREFAB rebinds the part in its
     * prefab so every instance changes (needs {@code confirmed}, see {@link #prefabWarning}), UPDATE replaces the confirmed asset.
     */
    public String save(Mode mode, String name, EditorState state, boolean confirmed) throws IOException {
        Objects.requireNonNull(mode, "mode cannot be null");
        Objects.requireNonNull(state, "state cannot be null");
        synchronized (MeshSaveSession.class) {
            if (state.isPlayModeActive() || state.getActiveScene() == null || state.getActiveScene().findEntityById(part.getId()) != part) {
                throw new IOException("The model part is no longer editable in this scene.");
            }
            boolean update = mode == Mode.UPDATE;
            if (update && !confirmed) throw new IOException(updateWarning(state));
            if (mode == Mode.PREFAB) {
                String warning = prefabWarning(state);
                if (!confirmed) throw new IOException(warning);
            }
            Path file = update ? checkUpdate() : newFile(name);
            String relative = root.relativize(file).toString().replace('\\', '/');
            EditableMesh edited = MeshDraftModel.build(original, drafts);
            byte[] bytes = encode(edited, relative);
            Path temp = Files.createTempFile(file.getParent(), ".model-", ".tmp");
            try {
                Files.write(temp, bytes);
                if (update) {
                    checkUpdate();
                    Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Files.move(temp, file);
                }
            } finally {
                Files.deleteIfExists(temp);
            }
            if (mode == Mode.UNIQUE) {
                ModelComponent model = part.getComponent(ModelComponent.class);
                if (!state.executeCommand(new ModifyComponentPropertyCommand<>(state, part.getId(), model, model.getModelPath(),
                    relative, model::setModelPath, "Use unique model on " + part.getName()))) {
                    Files.delete(file);
                    throw new IOException("Could not assign the unique model to " + part.getName());
                }
            } else if (mode == Mode.PREFAB) {
                ModelComponent model = part.getComponent(ModelComponent.class);
                if (!state.getPrefabService().applyComponent(part, model, copy -> ((ModelComponent) copy).setModelPath(relative))) {
                    Files.delete(file);
                    throw new IOException("Could not apply the new model to the prefab of " + part.getName() + ": "
                        + state.getStatusMessage());
                }
            }
            target = relative;
            expectedHash = digest(bytes);
            return relative;
        }
    }

    private Path newFile(String name) throws IOException {
        if (name == null || !name.matches("[A-Za-z0-9_-][A-Za-z0-9 _-]{0,95}") || name.endsWith(" ")) {
            throw new IOException("Use a model name of 1 to 96 letters, digits, spaces, hyphens or underscores.");
        }
        Path directory = root;
        for (Path segment : Path.of(DIRECTORY)) {
            directory = directory.resolve(segment);
            if (!Files.exists(directory)) Files.createDirectory(directory);
            if (!directory.toRealPath().equals(directory)) throw new IOException("Model directory redirects outside its location.");
        }
        Path file = directory.resolve(name + ".gltf");
        if (Files.exists(file)) throw new IOException("Model already exists: " + file.getFileName());
        return file;
    }

    /** The bytes a save writes for {@code mesh} at {@code target}: glTF JSON, or GLB for a .glb target, with its source recorded. */
    static byte[] encode(EditableMesh mesh, String target) {
        JsonValue model = mesh.model();
        JsonValue asset = model.require("asset");
        JsonValue extras = asset.get("extras");
        if (extras == null) {
            extras = new JsonValue(JsonValue.ValueType.object);
            asset.addChild("extras", extras);
        }
        if (!extras.isObject()) {
            // glTF extras may be any JSON value. Keep author metadata intact and put attribution on the asset instead.
            String credit = mesh.provenance().summary() + " (source " + mesh.sourcePath() + ", SHA-256 " + mesh.sourceSha256() + ")";
            String copyright = asset.getString("copyright", "");
            if (!copyright.contains(credit)) {
                asset.remove("copyright");
                asset.addChild("copyright", new JsonValue(copyright.isBlank() ? credit : copyright + "\n" + credit));
            }
        } else if (!extras.has(ORIGIN)) {
            JsonValue origin = new JsonValue(JsonValue.ValueType.object);
            origin.addChild("path", new JsonValue(mesh.sourcePath()));
            origin.addChild("sha256", new JsonValue(mesh.sourceSha256()));
            origin.addChild("licence", new JsonValue(mesh.provenance().licence()));
            origin.addChild("attribution", new JsonValue(mesh.provenance().summary()));
            extras.addChild(ORIGIN, origin);
        }
        if (!target.toLowerCase(Locale.ROOT).endsWith(".glb")) return jsonBytes(model);
        return binaryModel(model);
    }

    private static byte[] jsonBytes(JsonValue model) {
        return model.toJson(OutputType.json).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] binaryModel(JsonValue model) {
        // The desktop GLB loader reads vertex buffers and embedded images from the BIN chunk.
        ByteArrayOutputStream binary = new ByteArrayOutputStream();
        JsonValue buffers = model.require("buffers");
        int[] offsets = new int[buffers.size];
        int i = 0;
        for (JsonValue buffer : buffers) {
            offsets[i++] = binary.size();
            byte[] bytes = decodeResource(buffer.getString("uri"));
            binary.write(bytes, 0, buffer.getInt("byteLength"));
            padBinary(binary);
        }
        JsonValue views = model.require("bufferViews");
        for (JsonValue view : views) {
            int offset = offsets[view.getInt("buffer")] + view.getInt("byteOffset", 0);
            view.remove("buffer");
            view.remove("byteOffset");
            view.addChild("buffer", new JsonValue(0));
            view.addChild("byteOffset", new JsonValue(offset));
        }
        if (model.has("images")) for (JsonValue image : model.get("images")) {
            if (!image.has("uri")) continue;
            String uri = image.getString("uri");
            byte[] bytes = decodeResource(uri);
            JsonValue view = new JsonValue(JsonValue.ValueType.object);
            view.addChild("buffer", new JsonValue(0));
            view.addChild("byteOffset", new JsonValue(binary.size()));
            view.addChild("byteLength", new JsonValue(bytes.length));
            image.remove("uri");
            image.addChild("bufferView", new JsonValue(views.size));
            if (!image.has("mimeType")) image.addChild("mimeType", new JsonValue(uri.substring(5, uri.indexOf(';'))));
            views.addChild(view);
            binary.writeBytes(bytes);
            padBinary(binary);
        }
        JsonValue buffer = new JsonValue(JsonValue.ValueType.object);
        buffer.addChild("byteLength", new JsonValue(binary.size()));
        JsonValue packed = new JsonValue(JsonValue.ValueType.array);
        packed.addChild(buffer);
        model.remove("buffers");
        model.addChild("buffers", packed);
        byte[] json = jsonBytes(model);
        int padded = (json.length + 3) & ~3;
        ByteBuffer glb = ByteBuffer.allocate(28 + padded + binary.size()).order(ByteOrder.LITTLE_ENDIAN);
        glb.putInt(0x46546c67).putInt(2).putInt(glb.capacity()).putInt(padded).putInt(0x4e4f534a).put(json);
        while (glb.position() < 20 + padded) glb.put((byte)' ');
        glb.putInt(binary.size()).putInt(0x004e4942).put(binary.toByteArray());
        return glb.array();
    }

    private static byte[] decodeResource(String uri) {
        return Base64.getDecoder().decode(uri.substring(uri.indexOf(',') + 1));
    }

    private static void padBinary(ByteArrayOutputStream binary) {
        while ((binary.size() & 3) != 0) binary.write(0);
    }

    private static String digest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
