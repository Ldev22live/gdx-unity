package io.github.ldev22live.gdxunity.editor.core.asset;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetMetadata;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** Project-owned tags used by Content Browser searches. */
record AssetClassification(Map<AssetReference, Map<String, Set<String>>> tags, String error) {
    private static final String FILE = "asset-classification.json";
    private static final Set<String> TAGS = Set.of("genre", "role", "style", "realism", "species",
        "rigged", "animation", "resolution", "triangles", "license", "provider");

    static AssetClassification load(Path project) {
        Path file = project.resolve(FILE);
        if (Files.notExists(file)) {
            return new AssetClassification(Map.of(), null);
        }
        try {
            JsonValue entries = new JsonReader().parse(Files.readString(file));
            if (!entries.isObject()) {
                throw new IllegalArgumentException("expected an object keyed by asset path");
            }
            Map<AssetReference, Map<String, Set<String>>> byPath = new LinkedHashMap<>();
            for (JsonValue entry : entries) {
                if (!entry.isObject()) {
                    throw new IllegalArgumentException("expected tags for " + entry.name());
                }
                Map<String, Set<String>> fields = new LinkedHashMap<>();
                for (JsonValue field : entry) {
                    if (!TAGS.contains(field.name()) || !field.isArray()) {
                        throw new IllegalArgumentException("invalid tag " + field.name());
                    }
                    List<String> values = new ArrayList<>();
                    for (JsonValue value : field) {
                        if (!value.isString() || !value.asString().matches("[a-z0-9][a-z0-9._-]*")) {
                            throw new IllegalArgumentException("invalid value for " + field.name());
                        }
                        values.add(value.asString());
                    }
                    fields.put(field.name(), Set.copyOf(values));
                }
                AssetReference reference = new AssetReference(entry.name());
                if (byPath.putIfAbsent(reference, Map.copyOf(fields)) != null) {
                    throw new IllegalArgumentException("duplicate path " + reference.getPath());
                }
            }
            return new AssetClassification(Map.copyOf(byPath), null);
        } catch (IOException | RuntimeException failure) {
            return new AssetClassification(Map.of(), FILE + ": " + failure.getMessage());
        }
    }

    Predicate<AssetMetadata> query(String search) {
        List<String> words = new ArrayList<>();
        List<Predicate<AssetMetadata>> facets = new ArrayList<>();
        for (String token : search.split("\\s+")) {
            int separator = token.indexOf(':');
            String field = separator < 0 ? "" : token.substring(0, separator);
            String value = token.substring(separator + 1);
            switch (field) {
                case "type" -> facets.add(asset -> asset.type().name().equalsIgnoreCase(value));
                case "imported" -> facets.add(asset -> "yes".equals(value));
                default -> {
                    if (TAGS.contains(field)) {
                        facets.add(asset -> tags.getOrDefault(asset.reference(), Map.of())
                            .getOrDefault(field, Set.of()).contains(value));
                    } else {
                        words.add(token);
                    }
                }
            }
        }
        String name = facets.isEmpty() ? search : String.join(" ", words);
        return asset -> asset.getPath().toLowerCase(Locale.ROOT).contains(name)
            && facets.stream().allMatch(facet -> facet.test(asset));
    }
}
