package io.github.ldev22live.gdxunity.editor.core.project;

import io.github.ldev22live.gdxunity.engine.core.asset.ThirdPartyManifest;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * What the World strip's palette shows for each prefab it offers (roadmap M2b.3): a name, a group (Trees, Rocks, Plants, Buildings),
 * a thumbnail and where its files come from, so a person can search, filter and see the licence before placing. Headless; the editor
 * draws it.
 *
 * <p><b>Groups</b> come from the prefab's folder (Buildings, Characters) or its name: tree, pine, oak, palm or fir is a tree; rock,
 * boulder, stone or cliff a rock; bush, plant, flower, fern, grass, shrub or mushroom a plant; anything else Other.
 * <b>Thumbnails</b> are the {@code .png} beside the prefab with the same name ({@code tools/make_palette_thumbnails.py} renders them).
 * <b>Licences</b> come from the project's {@code licenses/manifest.json} for the files the prefab uses (TD-094); a prefab that uses no
 * recorded file is made in ForgeRoom.
 */
public final class ObjectPalette {
    /** What a prefab made only of ForgeRoom's own shapes and files says about its licence. */
    public static final String MADE_IN_FORGEROOM = "Made in ForgeRoom (no third-party files)";
    private static final String PREFAB_EXTENSION = ".prefab";
    private static final String THUMBNAIL_EXTENSION = ".png";

    /** A palette group, in the order the palette shows them. */
    public enum Category {
        TREES("Trees", List.of("tree", "pine", "oak", "palm", "fir")),
        ROCKS("Rocks", List.of("rock", "boulder", "stone", "cliff")),
        PLANTS("Plants", List.of("bush", "plant", "flower", "fern", "grass", "shrub", "mushroom")),
        BUILDINGS("Buildings", List.of()),
        CHARACTERS("Characters", List.of()),
        OTHER("Other", List.of());

        private final String label;
        private final List<String> words;

        Category(String label, List<String> words) {
            this.label = label;
            this.words = words;
        }

        /** The group's name as the palette shows it. */
        public String label() {
            return label;
        }
    }

    /**
     * One prefab as the palette shows it.
     *
     * @param path      the prefab, project relative
     * @param label     its name (the file name without {@code .prefab})
     * @param category  its group
     * @param thumbnail the project-relative thumbnail, or an empty string when it has none
     * @param licence   where its files come from, in one line
     */
    public record Item(String path, String label, Category category, String thumbnail, String licence) {
        public Item {
            Objects.requireNonNull(path, "path cannot be null");
            Objects.requireNonNull(label, "label cannot be null");
            Objects.requireNonNull(category, "category cannot be null");
            Objects.requireNonNull(thumbnail, "thumbnail cannot be null");
            Objects.requireNonNull(licence, "licence cannot be null");
        }

        /** True when every word of {@code query} is in its name or group (ignoring case); a blank query matches everything. */
        public boolean matches(String query) {
            if (query == null || query.isBlank()) {
                return true;
            }
            String text = (label + " " + category.label()).toLowerCase(Locale.ROOT);
            for (String word : query.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
                if (!text.contains(word)) {
                    return false;
                }
            }
            return true;
        }
    }

    private ObjectPalette() { }

    /**
     * Describes each prefab of {@code prefabPaths}, in their order.
     *
     * @throws IOException if the licence manifest or a prefab cannot be read
     */
    public static List<Item> describe(File assetRoot, List<String> prefabPaths) throws IOException {
        Objects.requireNonNull(assetRoot, "assetRoot cannot be null");
        Objects.requireNonNull(prefabPaths, "prefabPaths cannot be null");
        Path root = assetRoot.toPath().toAbsolutePath().normalize();
        ThirdPartyManifest manifest = ThirdPartyManifest.read(assetRoot);
        List<Item> items = new ArrayList<>();
        for (String path : prefabPaths) {
            String name = path.substring(path.lastIndexOf('/') + 1);
            String label = name.toLowerCase(Locale.ROOT).endsWith(PREFAB_EXTENSION)
                ? name.substring(0, name.length() - PREFAB_EXTENSION.length()) : name;
            String thumbnail = path.substring(0, path.length() - (name.length() - label.length())) + THUMBNAIL_EXTENSION;
            items.add(new Item(path, label, categoryOf(path, label), new File(assetRoot, thumbnail).isFile() ? thumbnail : "",
                licenceOf(manifest, TemplateProject.findDependencies(root, path))));
        }
        return items;
    }

    /** The items in {@code category} (all of them for null) that match {@code query}, in their order. */
    public static List<Item> filter(List<Item> items, Category category, String query) {
        Objects.requireNonNull(items, "items cannot be null");
        return items.stream().filter(item -> category == null || item.category() == category).filter(item -> item.matches(query)).toList();
    }

    /** The groups {@code items} fall into, in the palette's order. */
    public static List<Category> categories(List<Item> items) {
        Objects.requireNonNull(items, "items cannot be null");
        return Arrays.stream(Category.values()).filter(c -> items.stream().anyMatch(item -> item.category() == c)).toList();
    }

    /** A prefab's group, from its folder or its name (see the class description). */
    static Category categoryOf(String path, String label) {
        if (path.startsWith(StarterPalette.BUILDING_FOLDER + "/")) {
            return Category.BUILDINGS;
        }
        if (path.startsWith(StarterPalette.CHARACTER_FOLDER + "/")) {
            return Category.CHARACTERS;
        }
        String name = label.toLowerCase(Locale.ROOT);
        for (Category category : Category.values()) {
            if (category.words.stream().anyMatch(name::contains)) {
                return category;
            }
        }
        return Category.OTHER;
    }

    /** "CC0 1.0 · Nature Kit by Kenney (kenney.nl)" for the recorded files among {@code files}, or {@link #MADE_IN_FORGEROOM}. */
    static String licenceOf(ThirdPartyManifest manifest, Iterable<String> files) {
        Map<String, ThirdPartyManifest.Entry> sources = new LinkedHashMap<>();
        for (String file : files) {
            manifest.find(file).ifPresent(entry -> sources.putIfAbsent(entry.id(), entry));
        }
        if (sources.isEmpty()) {
            return MADE_IN_FORGEROOM;
        }
        List<String> parts = new ArrayList<>();
        Set<String> licences = new TreeSet<>();
        for (ThirdPartyManifest.Entry entry : sources.values()) {
            licences.add(entry.licence().replace("CC0-1.0", "CC0 1.0"));
            String by = entry.authors().isEmpty() ? "" : " by " + String.join(", ", entry.authors());
            parts.add(entry.name() + by + " (" + host(entry.source()) + ")");
        }
        return String.join(", ", licences) + " · " + String.join("; ", parts);
    }

    private static String host(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? url : host.startsWith("www.") ? host.substring(4) : host;
        } catch (IllegalArgumentException e) {
            return url;
        }
    }
}
