package io.github.ldev22live.gdxunity.editor.core.asset;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.editor.core.file.ProjectPaths;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetType;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * File > Import Asset (roadmap M1.5): brings a file from outside the project into it, so the Content Browser lists it and it can be
 * placed or opened. The file is copied into the folder for its type ({@code models/}, {@code textures/}, {@code sounds/} and so on)
 * under a name no other file there has; nothing in the project is ever overwritten, and the original is not touched.
 *
 * <p>A {@code .gltf} that keeps its buffers or images in separate files goes into a folder of its own ({@code models/<name>/}) with those
 * files beside it, so their relative names keep working; a missing one stops the import with its name. Other files that name further
 * files (a material that names a texture, a prefab that names a model) are copied alone, and the names that do not exist in this
 * project are reported, because importing them by guessing could pick up the wrong files.
 */
public final class AssetImport {
    /** The folder each type is imported into, relative to the project. */
    public static final Map<AssetType, String> FOLDERS = new EnumMap<>(AssetType.class);

    static {
        FOLDERS.put(AssetType.TEXTURE, "textures");
        FOLDERS.put(AssetType.MODEL, "models");
        FOLDERS.put(AssetType.PREFAB, "prefabs");
        FOLDERS.put(AssetType.GRAPH, "graphs");
        FOLDERS.put(AssetType.ANIMATION, "animations");
        FOLDERS.put(AssetType.SCULPT, "sculpts");
        FOLDERS.put(AssetType.SOUND, "sounds");
        FOLDERS.put(AssetType.SPRITE, "sprites");
        FOLDERS.put(AssetType.SONG, "music");
        FOLDERS.put(AssetType.MATERIAL, "materials");
        FOLDERS.put(AssetType.TERRAIN, "terrains");
        FOLDERS.put(AssetType.VECTOR, "vectors");
    }

    /**
     * What an import did.
     *
     * @param asset          the project-relative path of the imported asset (or the existing one, when it was already in the project)
     * @param copied         every project-relative path written, in order; empty when nothing was copied
     * @param alreadyInside  true when the file was already in the project, so nothing was copied
     * @param unresolvedNames file names the imported file mentions that this project does not have
     */
    public record Result(String asset, List<String> copied, boolean alreadyInside, List<String> unresolvedNames) {
        public Result {
            Objects.requireNonNull(asset, "asset cannot be null");
            copied = List.copyOf(copied);
            unresolvedNames = List.copyOf(unresolvedNames);
        }
    }

    private AssetImport() { }

    /** The extensions the import dialog offers: every asset type's, lower case, without the dot. */
    public static List<String> importableExtensions() {
        List<String> extensions = new ArrayList<>();
        for (AssetType type : FOLDERS.keySet()) {
            extensions.addAll(type.getExtensions());
        }
        return List.copyOf(extensions);
    }

    /**
     * Imports one file.
     *
     * @param projectRoot the project's asset folder
     * @param source      the file to bring in
     * @throws IOException if it is not an asset type, does not exist, a glTF's separate file is missing, or the copy fails; a
     *                     half-done import is removed
     */
    public static Result importFile(File projectRoot, File source) throws IOException {
        Objects.requireNonNull(projectRoot, "projectRoot cannot be null");
        Objects.requireNonNull(source, "source cannot be null");
        AssetType type = AssetType.fromPath(source.getName());
        if (!FOLDERS.containsKey(type)) {
            throw new IOException(source.getName() + " is not a kind of file Forgeroom uses (models, images, sounds, songs, sprites, "
                + "graphs, clips, prefabs, sculpts, materials and terrains are)");
        }
        if (!source.isFile()) {
            throw new IOException(source.getName() + " does not exist");
        }
        Optional<String> inside = ProjectPaths.relativeAssetPath(projectRoot, source);
        if (inside.isPresent()) {
            return new Result(inside.get(), List.of(), true, List.of());
        }
        Path root = projectRoot.toPath().toAbsolutePath().normalize();
        String folder = FOLDERS.get(type);
        String extension = source.getName().substring(source.getName().lastIndexOf('.'));
        String base = source.getName().substring(0, source.getName().length() - extension.length());

        List<String> separate = type == AssetType.MODEL && extension.equalsIgnoreCase(".gltf") ? gltfFiles(source) : List.of();
        List<Path> written = new ArrayList<>();
        try {
            String asset;
            if (separate.isEmpty()) {
                asset = folder + "/" + uniqueName(root.resolve(folder), base, extension);
                written.add(copy(source.toPath(), root.resolve(asset)));
            } else {
                // A glTF with separate buffers or images goes into its own folder so their relative names keep working
                String own = folder + "/" + uniqueName(root.resolve(folder), base, "");
                asset = own + "/" + source.getName();
                written.add(copy(source.toPath(), root.resolve(asset)));
                for (String name : separate) {
                    written.add(copy(source.toPath().resolveSibling(name), root.resolve(own).resolve(name).normalize()));
                }
            }
            List<String> copied = new ArrayList<>();
            for (Path path : written) {
                copied.add(root.relativize(path).toString().replace('\\', '/'));
            }
            return new Result(asset, copied, false, separate.isEmpty() ? unresolved(root, source.toPath(), type) : List.of());
        } catch (IOException | RuntimeException e) {
            for (int i = written.size() - 1; i >= 0; i--) {
                try {
                    Files.deleteIfExists(written.get(i));
                } catch (IOException cleanup) {
                    e.addSuppressed(cleanup);
                }
            }
            throw e;
        }
    }

    private static Path copy(Path from, Path to) throws IOException {
        Files.createDirectories(to.getParent());
        // COPY without REPLACE_EXISTING: an existing file is never overwritten, the copy fails instead
        return Files.copy(from, to, StandardCopyOption.COPY_ATTRIBUTES);
    }

    /** {@code name.ext}, else {@code name 2.ext} and so on, so nothing in the folder is overwritten. */
    private static String uniqueName(Path folder, String base, String extension) {
        String clean = base.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (clean.isEmpty()) {
            clean = "asset";
        }
        String name = clean + extension;
        for (int n = 2; Files.exists(folder.resolve(name)); n++) {
            name = clean + " " + n + extension;
        }
        return name;
    }

    /**
     * The files a {@code .gltf} keeps beside it (buffer and image URIs that are not embedded), relative to it.
     *
     * @throws IOException if the file is not readable JSON, a URI leaves its folder, or a named file is missing
     */
    static List<String> gltfFiles(File gltf) throws IOException {
        JsonValue root;
        try {
            root = new JsonReader().parse(Files.readString(gltf.toPath(), StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            throw new IOException(gltf.getName() + " is not readable glTF: " + e.getMessage(), e);
        }
        List<String> files = new ArrayList<>();
        for (String section : List.of("buffers", "images")) {
            JsonValue list = root.get(section);
            for (JsonValue item = list == null ? null : list.child; item != null; item = item.next) {
                String uri = item.getString("uri", "");
                if (uri.isEmpty() || uri.startsWith("data:")) {
                    continue;
                }
                String name = java.net.URLDecoder.decode(uri, StandardCharsets.UTF_8);
                Path file = gltf.toPath().resolveSibling(name).normalize();
                if (!file.startsWith(gltf.toPath().getParent()) || name.contains("..")) {
                    throw new IOException(gltf.getName() + " names a file outside its folder: " + uri);
                }
                if (!Files.isRegularFile(file)) {
                    throw new IOException(gltf.getName() + " needs " + name + ", which is missing beside it");
                }
                if (!files.contains(name)) {
                    files.add(name);
                }
            }
        }
        return files;
    }

    /** File names in a copied data file that do not exist in this project (reported, not guessed at). */
    private static List<String> unresolved(Path root, Path source, AssetType type) throws IOException {
        if (type == AssetType.TEXTURE || type == AssetType.SOUND || type == AssetType.MODEL) {
            return List.of();
        }
        JsonValue value;
        try {
            value = new JsonReader().parse(Files.readString(source, StandardCharsets.UTF_8));
        } catch (RuntimeException | IOException e) {
            // A binary or non-JSON asset (a sculpt, for example) names no other files
            return List.of();
        }
        List<String> missing = new ArrayList<>();
        collectMissing(value, root, missing);
        return missing;
    }

    private static void collectMissing(JsonValue value, Path root, List<String> out) {
        if (value == null) {
            return;
        }
        if (value.isString()) {
            String text = value.asString();
            if (AssetType.fromPath(text) != AssetType.UNKNOWN && !text.contains(" ") && !text.startsWith("data:")) {
                try {
                    if (!Files.isRegularFile(root.resolve(text)) && !out.contains(text)) {
                        out.add(text);
                    }
                } catch (java.nio.file.InvalidPathException e) {
                    // Not a valid path on this system, so not a file it could need
                }
            }
            return;
        }
        for (JsonValue child = value.child; child != null; child = child.next) {
            collectMissing(child, root, out);
        }
    }

    /** The import folder of a type, or null for a type that cannot be imported. */
    public static String folderOf(AssetType type) {
        return FOLDERS.get(Objects.requireNonNull(type, "type cannot be null"));
    }

    /** Lower-case name of a type for messages, e.g. "model". */
    public static String kindOf(AssetType type) {
        return type.name().toLowerCase(Locale.ROOT);
    }
}
