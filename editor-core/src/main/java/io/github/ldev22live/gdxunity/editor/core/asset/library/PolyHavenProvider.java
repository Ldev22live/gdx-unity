package io.github.ldev22live.gdxunity.editor.core.asset.library;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.SerializationException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Poly Haven (TD-095) through its public API ({@code https://api.polyhaven.com}): every asset is CC0. The API has no text search,
 * so the catalogue of a type is fetched whole (about a megabyte), kept for the session and on disk, and searched here. Models come as
 * glTF with their buffer and textures; textures as the base colour, OpenGL normal and ARM (occlusion, roughness, metalness) maps in
 * JPEG, the three a ForgeRoom material uses.
 */
public final class PolyHavenProvider implements AssetProvider {
    /** The API's address. */
    public static final String API = "https://api.polyhaven.com";
    /** The hosts the API and its downloads use; nothing else is contacted. */
    public static final List<String> HOSTS = List.of("polyhaven.com", "polyhaven.org");
    /** Poly Haven's licence page: every asset is CC0. */
    public static final String LICENCE = "https://polyhaven.com/license";
    /**
     * A thumbnail as PNG (UI M7): the API's own address asks for WebP, which the editor cannot decode, so the CDN is asked for PNG at
     * 256 pixels.
     */
    static final String THUMBNAIL = "https://cdn.polyhaven.com/asset_img/thumbs/%s.png?width=256&height=256&format=png";
    /** The API's type number of each asset type. */
    private static final Map<AssetType, String> API_TYPES = new EnumMap<>(Map.of(AssetType.MODEL, "models", AssetType.TEXTURE, "textures"));
    /** The texture maps downloaded for a material, with how the material uses each. */
    private static final Map<String, DownloadOption.Role> TEXTURE_MAPS = new java.util.LinkedHashMap<>();

    static {
        TEXTURE_MAPS.put("Diffuse", DownloadOption.Role.BASE_COLOR);
        TEXTURE_MAPS.put("nor_gl", DownloadOption.Role.NORMAL);
        TEXTURE_MAPS.put("arm", DownloadOption.Role.OCCLUSION_ROUGHNESS_METALLIC);
    }

    private final HttpSource http;
    private final File cacheDir;
    private final Map<AssetType, Catalog> session = new EnumMap<>(AssetType.class);

    /**
     * @param http     how the API is reached
     * @param cacheDir where catalogues are kept for offline use (created when first written)
     */
    public PolyHavenProvider(HttpSource http, File cacheDir) {
        this.http = Objects.requireNonNull(http, "http cannot be null");
        this.cacheDir = Objects.requireNonNull(cacheDir, "cacheDir cannot be null");
    }

    @Override
    public String id() {
        return "polyhaven";
    }

    @Override
    public String name() {
        return "Poly Haven";
    }

    @Override
    public String credit() {
        return "Powered by Poly Haven";
    }

    @Override
    public String homePage() {
        return "https://polyhaven.com";
    }

    @Override
    public String licenceEvidence() {
        return LICENCE;
    }

    @Override
    public List<AssetType> types() {
        return List.of(AssetType.MODEL, AssetType.TEXTURE);
    }

    @Override
    public synchronized Catalog catalog(AssetType type) throws IOException {
        Objects.requireNonNull(type, "type cannot be null");
        Catalog kept = session.get(type);
        if (kept != null) {
            return kept;
        }
        File cached = cacheFile(type);
        Catalog catalog;
        try {
            byte[] body = http.get(API + "/assets?t=" + API_TYPES.get(type));
            List<RemoteAsset> assets = parseCatalog(new String(body, StandardCharsets.UTF_8), type);
            String cacheProblem = "";
            try {
                writeCache(cached, body);
            } catch (IOException notCached) {
                // The live catalogue still works; only offline use later is lost, and that is said
                cacheProblem = "not kept for offline use: " + notCached.getMessage();
            }
            catalog = new Catalog(assets, Instant.now(), false, cacheProblem);
        } catch (IOException | SerializationException | IllegalArgumentException e) {
            String problem = e.getMessage() == null ? e.toString() : e.getMessage();
            if (!cached.isFile()) {
                throw new IOException(name() + " cannot be reached (" + problem + ") and nothing is cached yet", e);
            }
            try {
                catalog = new Catalog(parseCatalog(Files.readString(cached.toPath(), StandardCharsets.UTF_8), type),
                    Instant.ofEpochMilli(cached.lastModified()), true, problem);
            } catch (SerializationException | IllegalArgumentException unreadable) {
                throw new IOException(name() + " cannot be reached (" + problem + ") and its cached catalogue is unreadable: "
                    + unreadable.getMessage(), unreadable);
            }
        }
        // An offline catalogue is not kept, so the next search tries the provider again
        if (!catalog.offline()) {
            session.put(type, catalog);
        }
        return catalog;
    }

    @Override
    public List<DownloadOption> options(RemoteAsset asset) throws IOException {
        Objects.requireNonNull(asset, "asset cannot be null");
        byte[] body = http.get(API + "/files/" + asset.id());
        try {
            return parseOptions(new String(body, StandardCharsets.UTF_8), asset.type());
        } catch (SerializationException e) {
            throw new IOException(name() + " sent an unreadable file list for " + asset.id() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public String retrievedWith(DownloadOption option) {
        return "ForgeRoom Asset Library: Poly Haven public API, " + option.label() + ", MD5 checked";
    }

    /** The catalogue's assets of {@code type} from the API's {@code /assets} answer; assets of another type are skipped. */
    static List<RemoteAsset> parseCatalog(String json, AssetType type) {
        JsonValue root = new JsonReader().parse(json);
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("not a Poly Haven catalogue");
        }
        int apiType = type == AssetType.MODEL ? 2 : 1;
        List<RemoteAsset> assets = new ArrayList<>();
        for (JsonValue item = root.child; item != null; item = item.next) {
            if (item.getInt("type", -1) != apiType || !PackImporter.isSafe(item.name) || item.name.contains("/")) {
                continue;
            }
            TreeSet<String> authors = new TreeSet<>();
            JsonValue authorObject = item.get("authors");
            for (JsonValue author = authorObject == null ? null : authorObject.child; author != null; author = author.next) {
                authors.add(author.name);
            }
            assets.add(new RemoteAsset("polyhaven", item.name, item.getString("name", item.name), type, List.copyOf(authors),
                strings(item.get("categories")), strings(item.get("tags")), "https://polyhaven.com/a/" + item.name,
                String.format(THUMBNAIL, item.name), item.getLong("download_count", 0L)));
        }
        assets.sort(Comparator.comparing(RemoteAsset::id));
        return assets;
    }

    /** The download options in the API's {@code /files/<id>} answer, smallest first; a resolution missing a needed file is left out. */
    static List<DownloadOption> parseOptions(String json, AssetType type) {
        JsonValue root = new JsonReader().parse(json);
        List<DownloadOption> options = new ArrayList<>();
        if (type == AssetType.MODEL) {
            JsonValue gltf = root.get("gltf");
            for (JsonValue resolution = gltf == null ? null : gltf.child; resolution != null; resolution = resolution.next) {
                JsonValue main = resolution.get("gltf");
                if (main == null || !main.has("url")) {
                    continue;
                }
                List<DownloadOption.RemoteFile> files = new ArrayList<>();
                files.add(file(lastSegment(main.getString("url")), main, DownloadOption.Role.OTHER));
                JsonValue include = main.get("include");
                List<DownloadOption.RemoteFile> included = new ArrayList<>();
                for (JsonValue part = include == null ? null : include.child; part != null; part = part.next) {
                    included.add(file(part.name, part, DownloadOption.Role.OTHER));
                }
                included.sort(Comparator.comparing(DownloadOption.RemoteFile::path));
                files.addAll(included);
                options.add(new DownloadOption(resolution.name, files));
            }
        } else {
            JsonValue diffuse = root.get("Diffuse");
            for (JsonValue resolution = diffuse == null ? null : diffuse.child; resolution != null; resolution = resolution.next) {
                List<DownloadOption.RemoteFile> files = new ArrayList<>();
                for (Map.Entry<String, DownloadOption.Role> map : TEXTURE_MAPS.entrySet()) {
                    JsonValue maps = root.get(map.getKey());
                    JsonValue atResolution = maps == null ? null : maps.get(resolution.name);
                    JsonValue jpg = atResolution == null ? null : atResolution.get("jpg");
                    if (jpg != null && jpg.has("url")) {
                        files.add(file(lastSegment(jpg.getString("url")), jpg, map.getValue()));
                    }
                }
                if (files.size() == TEXTURE_MAPS.size()) {
                    options.add(new DownloadOption(resolution.name, files));
                }
            }
        }
        options.sort(Comparator.comparingInt(DownloadOption::size));
        return options;
    }

    private static DownloadOption.RemoteFile file(String path, JsonValue value, DownloadOption.Role role) {
        return new DownloadOption.RemoteFile(path, value.getString("url"), value.getLong("size", 0L), value.getString("md5", ""), role);
    }

    private static String lastSegment(String url) {
        String path = url.contains("?") ? url.substring(0, url.indexOf('?')) : url;
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static List<String> strings(JsonValue array) {
        List<String> values = new ArrayList<>();
        for (JsonValue value = array == null ? null : array.child; value != null; value = value.next) {
            if (value.isString()) {
                values.add(value.asString());
            }
        }
        return values;
    }

    private File cacheFile(AssetType type) {
        return new File(new File(cacheDir, id()), API_TYPES.get(type) + ".json");
    }

    private static void writeCache(File target, byte[] body) throws IOException {
        Files.createDirectories(target.getParentFile().toPath());
        File temporary = new File(target.getParentFile(), target.getName() + ".part");
        Files.write(temporary.toPath(), body);
        Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
}
