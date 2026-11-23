package io.github.ldev22live.gdxunity.editor.core.asset.library;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.SerializationException;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * ambientCG (TD-095, UI M6) through its API v3 ({@code https://ambientcg.com/api/v3/assets}); every asset is CC0. Only materials are
 * offered: its 3D models are OBJ or FBX and its HDRIs EXR or HDR, which ForgeRoom does not read. The catalogue is fetched in pages,
 * most popular first, kept for the session and on disk, and searched here like Poly Haven's. A download is a zip of maps per
 * resolution (JPEG only offered); ambientCG states its size but gives no hash, so the SHA-256 of what arrived is what is recorded.
 * The zip is unpacked with the pack importer's rules; the colour, OpenGL normal, roughness and ambient occlusion maps feed the
 * material, and the Blender, MaterialX, USD and Godot files in it are left out and listed.
 */
public final class AmbientCgProvider implements AssetProvider {
    /** The API's assets address. */
    public static final String API = "https://ambientcg.com/api/v3/assets";
    /** The hosts the API and its downloads (a redirect to its download host) use; nothing else is contacted. */
    public static final List<String> HOSTS = List.of("ambientcg.com", "struffelproductions.com");
    /** ambientCG's licence page: every asset is CC0. */
    public static final String LICENCE = "https://docs.ambientcg.com/license";
    /** Assets per catalogue page; the API allows at most 500. */
    static final int PAGE_SIZE = 500;
    /** The most pages read for one catalogue, so a misbehaving answer cannot keep the worker busy for ever. */
    static final int MAX_PAGES = 20;
    /** A thumbnail as a 256-pixel PNG (UI M7), at the address the API's {@code thumbnails} list gives for every material. */
    static final String THUMBNAIL = "https://acg-media.struffelproductions.com/file/ambientCG-Web/media/thumbnail/256-PNG/%s.png";
    /** A download offered: a resolution in JPEG ({@code 2K-JPG}). */
    private static final Pattern JPEG_DOWNLOAD = Pattern.compile("\\d+K-JPG");

    private final HttpSource http;
    private final File cacheDir;
    private final int pageSize;
    private Catalog kept;

    /**
     * @param http     how the API is reached
     * @param cacheDir where the catalogue is kept for offline use (created when first written)
     */
    public AmbientCgProvider(HttpSource http, File cacheDir) {
        this(http, cacheDir, PAGE_SIZE);
    }

    /** With a smaller page, so tests can show paging without 500 recorded assets. */
    AmbientCgProvider(HttpSource http, File cacheDir, int pageSize) {
        this.http = Objects.requireNonNull(http, "http cannot be null");
        this.cacheDir = Objects.requireNonNull(cacheDir, "cacheDir cannot be null");
        if (pageSize < 1 || pageSize > PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize must be 1 to " + PAGE_SIZE + ": " + pageSize);
        }
        this.pageSize = pageSize;
    }

    @Override
    public String id() {
        return "ambientcg";
    }

    @Override
    public String name() {
        return "ambientCG";
    }

    @Override
    public String credit() {
        return "Materials from ambientCG (CC0)";
    }

    @Override
    public String homePage() {
        return "https://ambientcg.com";
    }

    @Override
    public String licenceEvidence() {
        return LICENCE;
    }

    @Override
    public List<AssetType> types() {
        return List.of(AssetType.TEXTURE);
    }

    @Override
    public synchronized Catalog catalog(AssetType type) throws IOException {
        if (type != AssetType.TEXTURE) {
            throw new IllegalArgumentException("ambientCG offers materials only, not " + type);
        }
        if (kept != null) {
            return kept;
        }
        File cached = new File(new File(cacheDir, id()), "materials.json");
        Catalog catalog;
        try {
            List<String> pages = new ArrayList<>();
            for (int page = 0; page < MAX_PAGES; page++) {
                String body = new String(http.get(API + "?type=material&sort=popular&limit=" + pageSize + "&offset=" + page * pageSize
                    + "&include=title,tags"), StandardCharsets.UTF_8);
                pages.add(body);
                JsonValue root = new JsonReader().parse(body);
                JsonValue assets = root.get("assets");
                if (assets == null || assets.size < pageSize || (page + 1) * pageSize >= root.getInt("totalResults", 0)) {
                    break;
                }
            }
            String all = "[" + String.join(",", pages) + "]";
            List<RemoteAsset> assets = parseCatalog(all);
            String cacheProblem = "";
            try {
                writeCache(cached, all);
            } catch (IOException notCached) {
                // The live catalogue still works; only offline use later is lost, and that is said
                cacheProblem = "not kept for offline use: " + notCached.getMessage();
            }
            catalog = new Catalog(assets, Instant.now(), false, cacheProblem);
            kept = catalog;
        } catch (IOException | SerializationException | IllegalArgumentException e) {
            String problem = e.getMessage() == null ? e.toString() : e.getMessage();
            if (!cached.isFile()) {
                throw new IOException(name() + " cannot be reached (" + problem + ") and nothing is cached yet", e);
            }
            try {
                catalog = new Catalog(parseCatalog(Files.readString(cached.toPath(), StandardCharsets.UTF_8)),
                    Instant.ofEpochMilli(cached.lastModified()), true, problem);
            } catch (SerializationException | IllegalArgumentException unreadable) {
                throw new IOException(name() + " cannot be reached (" + problem + ") and its cached catalogue is unreadable: "
                    + unreadable.getMessage(), unreadable);
            }
        }
        return catalog;
    }

    @Override
    public List<DownloadOption> options(RemoteAsset asset) throws IOException {
        Objects.requireNonNull(asset, "asset cannot be null");
        byte[] body = http.get(API + "?id=" + URLEncoder.encode(asset.id(), StandardCharsets.UTF_8) + "&include=downloads");
        try {
            return parseOptions(new String(body, StandardCharsets.UTF_8), asset.id());
        } catch (SerializationException e) {
            throw new IOException(name() + " sent an unreadable download list for " + asset.id() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public String retrievedWith(DownloadOption option) {
        return "ForgeRoom Asset Library: ambientCG API v3, " + option.resolution() + " zip (no source hash; SHA-256 recorded)";
    }

    @Override
    public DownloadOption.Role roleOf(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        String stem = dot < 0 ? name : name.substring(0, dot);
        if (stem.endsWith("_color")) {
            return DownloadOption.Role.BASE_COLOR;
        }
        if (stem.endsWith("_normalgl")) {
            return DownloadOption.Role.NORMAL;
        }
        if (stem.endsWith("_roughness")) {
            return DownloadOption.Role.ROUGHNESS;
        }
        if (stem.endsWith("_ambientocclusion")) {
            return DownloadOption.Role.AMBIENT_OCCLUSION;
        }
        return DownloadOption.Role.OTHER;
    }

    /** The materials in the catalogue's pages (a JSON array of API answers), most popular first. */
    static List<RemoteAsset> parseCatalog(String pagesJson) {
        JsonValue pages = new JsonReader().parse(pagesJson);
        if (pages == null || !pages.isArray()) {
            throw new IllegalArgumentException("not an ambientCG catalogue");
        }
        List<JsonValue> items = new ArrayList<>();
        for (JsonValue page = pages.child; page != null; page = page.next) {
            JsonValue assets = page.get("assets");
            for (JsonValue item = assets == null ? null : assets.child; item != null; item = item.next) {
                items.add(item);
            }
        }
        List<RemoteAsset> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < items.size(); i++) {
            JsonValue item = items.get(i);
            String id = item.getString("id", "");
            if (id.isEmpty() || !PackImporter.isSafe(id) || id.contains("/") || seen.contains(id)) {
                continue;
            }
            seen.add(id);
            List<String> tags = new ArrayList<>();
            JsonValue tagArray = item.get("tags");
            for (JsonValue tag = tagArray == null ? null : tagArray.child; tag != null; tag = tag.next) {
                if (tag.isString()) {
                    tags.add(tag.asString());
                }
            }
            // The API lists them most popular first; that order is kept as the download count's rank
            result.add(new RemoteAsset("ambientcg", id, item.getString("title", id), AssetType.TEXTURE, List.of("ambientCG"), List.of(),
                tags, "https://ambientcg.com/view?id=" + id, String.format(THUMBNAIL, id), items.size() - i));
        }
        result.sort(Comparator.comparing(RemoteAsset::id));
        return result;
    }

    /** The JPEG downloads of asset {@code id} in an API answer, smallest first. */
    static List<DownloadOption> parseOptions(String json, String id) {
        JsonValue root = new JsonReader().parse(json);
        JsonValue assets = root.get("assets");
        List<DownloadOption> options = new ArrayList<>();
        for (JsonValue item = assets == null ? null : assets.child; item != null; item = item.next) {
            if (!id.equals(item.getString("id", ""))) {
                continue;
            }
            JsonValue downloads = item.get("downloads");
            for (JsonValue download = downloads == null ? null : downloads.child; download != null; download = download.next) {
                String attributes = download.getString("attributes", "");
                if (JPEG_DOWNLOAD.matcher(attributes).matches() && "zip".equals(download.getString("extension", ""))
                    && download.has("url")) {
                    options.add(new DownloadOption(attributes, List.of(new DownloadOption.RemoteFile(id + "_" + attributes + ".zip",
                        download.getString("url"), download.getLong("size", 0L), "", DownloadOption.Role.ARCHIVE))));
                }
            }
        }
        options.sort(Comparator.comparingInt(DownloadOption::size));
        return options;
    }

    private static void writeCache(File target, String body) throws IOException {
        Files.createDirectories(target.getParentFile().toPath());
        File temporary = new File(target.getParentFile(), target.getName() + ".part");
        Files.writeString(temporary.toPath(), body, StandardCharsets.UTF_8);
        Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
}
