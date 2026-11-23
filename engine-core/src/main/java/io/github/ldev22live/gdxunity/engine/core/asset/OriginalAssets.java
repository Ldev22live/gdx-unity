package io.github.ldev22live.gdxunity.engine.core.asset;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.SerializationException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The record of the 2D art made in this repository ({@code licenses/originals.json} under the asset root, roadmap M2b.5, TD-099):
 * for each shipped sprite, texture or picture that did not come from outside, how it was made, under which licence, and its
 * SHA-256. Together with {@link ThirdPartyManifest} it gives every shipped 2D file a record ({@link AssetProvenance}).
 *
 * <p>The file is written by {@code tools/record_original_assets.py}. Reading refuses a missing field, an unsafe path, a path listed
 * twice, a malformed SHA-256, or a licence not in {@link #ACCEPTED_LICENCES}.
 */
public final class OriginalAssets {
    /** Where the record lives, relative to the asset root. */
    public static final String PATH = "licenses/originals.json";
    /** The licences a file made in this repository may have: the repository's own (the root {@code LICENSE}). */
    public static final Set<String> ACCEPTED_LICENCES = Set.of("Apache-2.0");
    private static final int SCHEMA_VERSION = 1;
    private static final String KIND = "project-originals";

    /**
     * One file made in this repository.
     *
     * @param path        where it is, relative to the asset root
     * @param bytes       its size
     * @param sha256      its SHA-256, lowercase hex
     * @param licence     its licence, one of {@link #ACCEPTED_LICENCES}
     * @param madeWith    how it was made, e.g. "tools/make_material_samples.py" or "by hand in the Sprite Studio"
     * @param derivedFrom the {@link ThirdPartyManifest} ids of the downloaded assets it shows (a render of a CC0 model), or none
     */
    public record FileRecord(String path, long bytes, String sha256, String licence, String madeWith, List<String> derivedFrom) {
        public FileRecord {
            Objects.requireNonNull(path, "path cannot be null");
            Objects.requireNonNull(sha256, "sha256 cannot be null");
            Objects.requireNonNull(licence, "licence cannot be null");
            Objects.requireNonNull(madeWith, "madeWith cannot be null");
            derivedFrom = List.copyOf(derivedFrom);
        }
    }

    private final Map<String, FileRecord> byPath;

    private OriginalAssets(List<FileRecord> files) {
        Map<String, FileRecord> index = new LinkedHashMap<>();
        for (FileRecord file : files) {
            if (index.put(file.path(), file) != null) {
                throw new IllegalArgumentException("Original assets record lists '" + file.path() + "' twice");
            }
        }
        this.byPath = Collections.unmodifiableMap(index);
    }

    /** A record with no files: a project with no shipped original art recorded. */
    public static OriginalAssets empty() {
        return new OriginalAssets(List.of());
    }

    /**
     * Reads {@link #PATH} under {@code assetsRoot}; a project without the file has nothing recorded ({@link #empty()}).
     *
     * @throws IOException              when the file cannot be read
     * @throws IllegalArgumentException when it is not a valid record
     */
    public static OriginalAssets read(File assetsRoot) throws IOException {
        File file = new File(Objects.requireNonNull(assetsRoot, "assetsRoot cannot be null"), PATH);
        if (!file.isFile()) {
            return empty();
        }
        return parse(Files.readString(file.toPath(), StandardCharsets.UTF_8));
    }

    /**
     * Parses the record's JSON.
     *
     * @throws IllegalArgumentException when it is not a valid record, naming what is wrong
     */
    public static OriginalAssets parse(String json) {
        Objects.requireNonNull(json, "json cannot be null");
        JsonValue root;
        try {
            root = new JsonReader().parse(json);
        } catch (SerializationException e) {
            throw new IllegalArgumentException("Original assets record is not valid JSON: " + e.getMessage(), e);
        }
        if (root == null || root.getInt("schemaVersion", -1) != SCHEMA_VERSION || !KIND.equals(root.getString("kind", ""))) {
            throw new IllegalArgumentException("Original assets record needs schemaVersion " + SCHEMA_VERSION + " and kind '" + KIND + "'");
        }
        JsonValue fileList = root.get("files");
        if (fileList == null || !fileList.isArray()) {
            throw new IllegalArgumentException("Original assets record has no 'files' list");
        }
        List<FileRecord> files = new ArrayList<>();
        for (JsonValue file = fileList.child; file != null; file = file.next) {
            files.add(file(file));
        }
        return new OriginalAssets(files);
    }

    private static FileRecord file(JsonValue file) {
        String path = required(file, "path", "?");
        if (!ThirdPartyManifest.isSafeRelative(path)) {
            throw new IllegalArgumentException("Original assets record has an unsafe path: " + path);
        }
        String sha256 = required(file, "sha256", path);
        if (!sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Original asset '" + path + "' has a malformed SHA-256");
        }
        long bytes = file.getLong("bytes", -1L);
        if (bytes < 0L) {
            throw new IllegalArgumentException("Original asset '" + path + "' has no 'bytes'");
        }
        String licence = required(file, "licence", path);
        if (!ACCEPTED_LICENCES.contains(licence)) {
            throw new IllegalArgumentException("Original asset '" + path + "' has licence '" + licence + "'; accepted: " + ACCEPTED_LICENCES);
        }
        List<String> derivedFrom = new ArrayList<>();
        JsonValue derived = file.get("derivedFrom");
        if (derived != null) {
            if (!derived.isArray()) {
                throw new IllegalArgumentException("Original asset '" + path + "' has a 'derivedFrom' that is not a list");
            }
            for (JsonValue id = derived.child; id != null; id = id.next) {
                derivedFrom.add(id.asString());
            }
        }
        return new FileRecord(path, bytes, sha256, licence, required(file, "madeWith", path), derivedFrom);
    }

    private static String required(JsonValue value, String field, String path) {
        String text = value.getString(field, "");
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Original asset '" + path + "' has no '" + field + "'");
        }
        return text;
    }

    /** Every recorded file, in the record's order. */
    public List<FileRecord> files() {
        return List.copyOf(byPath.values());
    }

    /** The record of the file at {@code path} (relative to the asset root), if there is one. */
    public Optional<FileRecord> find(String path) {
        return Optional.ofNullable(byPath.get(Objects.requireNonNull(path, "path cannot be null")));
    }
}
