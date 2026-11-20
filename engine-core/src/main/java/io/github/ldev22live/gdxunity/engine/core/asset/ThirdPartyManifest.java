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
 * The record of every file in a project that came from outside it ({@code licenses/manifest.json} under the asset root): where it
 * was downloaded, under which licence, by whom it was made, and each file's SHA-256 (docs/asset-integration-report.md, the shape of
 * roadmap M2b.1's manifest). Reading refuses an entry with a missing field, an unsafe path, a path listed twice, or a licence not
 * in {@link #ACCEPTED_LICENCES}, so a project never ships a file whose licence it cannot state.
 */
public final class ThirdPartyManifest {
    /** Where the manifest lives, relative to the asset root. */
    public static final String PATH = "licenses/manifest.json";
    /** The licences a shipped third-party file may have: CC0 only, until a decision allows others (attribution would need a credits screen). */
    public static final Set<String> ACCEPTED_LICENCES = Set.of("CC0-1.0");
    private static final int SCHEMA_VERSION = 1;
    private static final String KIND = "third-party-assets";

    /** One file of an asset: where it is now (relative to the asset root), its name at the source, its size and SHA-256. */
    public record FileRecord(String path, String original, long bytes, String sha256) {
    }

    /** One downloaded asset and its files. */
    public record Entry(String id, String name, String category, String source, String licence, String licenceEvidence,
                        List<String> authors, String retrieved, String retrievedWith, String format, String conversion,
                        String importStatus, List<FileRecord> files) {
        public Entry {
            authors = List.copyOf(authors);
            files = List.copyOf(files);
        }
    }

    private final List<Entry> entries;
    private final Map<String, Entry> byPath;

    private ThirdPartyManifest(List<Entry> entries) {
        this.entries = List.copyOf(entries);
        Map<String, Entry> index = new LinkedHashMap<>();
        for (Entry entry : entries) {
            for (FileRecord file : entry.files()) {
                if (index.put(file.path(), entry) != null) {
                    throw new IllegalArgumentException("Third-party manifest lists '" + file.path() + "' twice");
                }
            }
        }
        this.byPath = Collections.unmodifiableMap(index);
    }

    /** A manifest with no entries: a project with nothing downloaded. */
    public static ThirdPartyManifest empty() {
        return new ThirdPartyManifest(List.of());
    }

    /**
     * Reads {@link #PATH} under {@code assetsRoot}; a project without the file has nothing downloaded ({@link #empty()}).
     *
     * @throws IOException              when the file cannot be read
     * @throws IllegalArgumentException when it is not a valid manifest
     */
    public static ThirdPartyManifest read(File assetsRoot) throws IOException {
        File file = new File(Objects.requireNonNull(assetsRoot, "assetsRoot cannot be null"), PATH);
        if (!file.isFile()) {
            return empty();
        }
        return parse(Files.readString(file.toPath(), StandardCharsets.UTF_8));
    }

    /**
     * Parses manifest JSON.
     *
     * @throws IllegalArgumentException when it is not a valid manifest, naming what is wrong
     */
    public static ThirdPartyManifest parse(String json) {
        Objects.requireNonNull(json, "json cannot be null");
        JsonValue root;
        try {
            root = new JsonReader().parse(json);
        } catch (SerializationException e) {
            throw new IllegalArgumentException("Third-party manifest is not valid JSON: " + e.getMessage(), e);
        }
        if (root == null || root.getInt("schemaVersion", -1) != SCHEMA_VERSION || !KIND.equals(root.getString("kind", ""))) {
            throw new IllegalArgumentException("Third-party manifest needs schemaVersion " + SCHEMA_VERSION + " and kind '" + KIND + "'");
        }
        JsonValue assets = root.get("assets");
        if (assets == null || !assets.isArray()) {
            throw new IllegalArgumentException("Third-party manifest has no 'assets' list");
        }
        List<Entry> entries = new ArrayList<>();
        for (JsonValue asset = assets.child; asset != null; asset = asset.next) {
            entries.add(entry(asset));
        }
        return new ThirdPartyManifest(entries);
    }

    private static Entry entry(JsonValue asset) {
        String id = required(asset, "id", "?");
        String licence = required(asset, "licence", id);
        if (!ACCEPTED_LICENCES.contains(licence)) {
            throw new IllegalArgumentException("Third-party asset '" + id + "' has licence '" + licence + "'; accepted: " + ACCEPTED_LICENCES);
        }
        List<String> authors = new ArrayList<>();
        JsonValue authorList = asset.get("authors");
        if (authorList == null || !authorList.isArray() || authorList.size == 0) {
            throw new IllegalArgumentException("Third-party asset '" + id + "' names no author");
        }
        for (JsonValue author = authorList.child; author != null; author = author.next) {
            authors.add(author.asString());
        }
        JsonValue fileList = asset.get("files");
        if (fileList == null || !fileList.isArray() || fileList.size == 0) {
            throw new IllegalArgumentException("Third-party asset '" + id + "' lists no files");
        }
        List<FileRecord> files = new ArrayList<>();
        for (JsonValue file = fileList.child; file != null; file = file.next) {
            String path = required(file, "path", id);
            if (!isSafeRelative(path)) {
                throw new IllegalArgumentException("Third-party asset '" + id + "' has an unsafe path: " + path);
            }
            String sha256 = required(file, "sha256", id);
            if (!sha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Third-party asset '" + id + "' has a malformed SHA-256 for " + path);
            }
            files.add(new FileRecord(path, required(file, "original", id), file.getLong("bytes", -1L), sha256));
        }
        return new Entry(id, required(asset, "name", id), required(asset, "category", id), required(asset, "source", id), licence,
            required(asset, "licenceEvidence", id), authors, required(asset, "retrieved", id), required(asset, "retrievedWith", id),
            required(asset, "format", id), required(asset, "conversion", id), required(asset, "importStatus", id), files);
    }

    private static String required(JsonValue value, String field, String id) {
        String text = value.getString(field, "");
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Third-party asset '" + id + "' has no '" + field + "'");
        }
        return text;
    }

    /** True for a forward-slash path relative to the asset root, with no '..', '.', empty part or drive. */
    static boolean isSafeRelative(String path) {
        if (path.isEmpty() || path.startsWith("/") || path.contains("\\") || path.contains(":")) {
            return false;
        }
        for (String part : path.split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) {
                return false;
            }
        }
        return true;
    }

    /** Every entry, in the file's order. */
    public List<Entry> entries() {
        return entries;
    }

    /** The asset a file (a path relative to the asset root) belongs to, or empty when it is not a downloaded file. */
    public Optional<Entry> find(String path) {
        return Optional.ofNullable(byPath.get(Objects.requireNonNull(path, "path cannot be null")));
    }

    /** Every listed file path, relative to the asset root. */
    public Set<String> paths() {
        return byPath.keySet();
    }

    /** The entry with {@code id}, or empty. */
    public Optional<Entry> entry(String id) {
        Objects.requireNonNull(id, "id cannot be null");
        return entries.stream().filter(e -> e.id().equals(id)).findFirst();
    }

    /**
     * A copy with {@code entry} added, or in place of the entry with its id; entries are kept in id order, as the download tool
     * writes them (UI M4).
     *
     * @throws IllegalArgumentException when one of its files is listed by another entry
     */
    public ThirdPartyManifest with(Entry entry) {
        Objects.requireNonNull(entry, "entry cannot be null");
        List<Entry> next = new ArrayList<>();
        for (Entry existing : entries) {
            if (!existing.id().equals(entry.id())) {
                next.add(existing);
            }
        }
        next.add(entry);
        next.sort(java.util.Comparator.comparing(Entry::id));
        return new ThirdPartyManifest(next);
    }

    /**
     * The manifest as JSON, laid out exactly as {@code tools/thirdparty_assets.py} writes it (Python's {@code json.dumps} with an
     * indent of 2, non-ASCII escaped), so the editor and the tool can both write the file without changing each other's lines.
     */
    public String toJson() {
        StringBuilder out = new StringBuilder();
        out.append("{\n  \"schemaVersion\": ").append(SCHEMA_VERSION).append(",\n  \"kind\": ").append(quote(KIND))
            .append(",\n  \"assets\": [");
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            out.append(i == 0 ? "\n" : ",\n").append("    {\n");
            field(out, "id", e.id());
            field(out, "name", e.name());
            field(out, "category", e.category());
            field(out, "source", e.source());
            field(out, "licence", e.licence());
            field(out, "licenceEvidence", e.licenceEvidence());
            out.append("      \"authors\": [");
            for (int a = 0; a < e.authors().size(); a++) {
                out.append(a == 0 ? "\n" : ",\n").append("        ").append(quote(e.authors().get(a)));
            }
            out.append(e.authors().isEmpty() ? "],\n" : "\n      ],\n");
            field(out, "retrieved", e.retrieved());
            field(out, "retrievedWith", e.retrievedWith());
            field(out, "format", e.format());
            out.append("      \"dependencies\": [],\n");
            field(out, "conversion", e.conversion());
            field(out, "importStatus", e.importStatus());
            out.append("      \"files\": [");
            for (int f = 0; f < e.files().size(); f++) {
                FileRecord file = e.files().get(f);
                out.append(f == 0 ? "\n" : ",\n").append("        {\n")
                    .append("          \"path\": ").append(quote(file.path())).append(",\n")
                    .append("          \"original\": ").append(quote(file.original())).append(",\n")
                    .append("          \"bytes\": ").append(file.bytes()).append(",\n")
                    .append("          \"sha256\": ").append(quote(file.sha256())).append("\n")
                    .append("        }");
            }
            out.append("\n      ]\n    }");
        }
        out.append(entries.isEmpty() ? "]\n}\n" : "\n  ]\n}\n");
        return out.toString();
    }

    /** Writes {@link #toJson()} to {@link #PATH} under {@code assetsRoot}, creating the folder. */
    public void write(File assetsRoot) throws IOException {
        File file = new File(Objects.requireNonNull(assetsRoot, "assetsRoot cannot be null"), PATH);
        Files.createDirectories(file.getParentFile().toPath());
        Files.writeString(file.toPath(), toJson(), StandardCharsets.UTF_8);
    }

    private static void field(StringBuilder out, String name, String value) {
        out.append("      \"").append(name).append("\": ").append(quote(value)).append(",\n");
    }

    /** A JSON string as Python's {@code json.dumps} writes it: quotes, backslashes and control characters escaped, non-ASCII as a u-escape. */
    static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20 || c > 0x7f) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
