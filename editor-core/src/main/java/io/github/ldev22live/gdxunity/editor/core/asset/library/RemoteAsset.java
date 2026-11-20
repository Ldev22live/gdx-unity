package io.github.ldev22live.gdxunity.editor.core.asset.library;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * One asset in a provider's catalogue, as the Asset Library lists it (TD-095).
 *
 * @param provider      the provider's id, for example {@code polyhaven}
 * @param id            the provider's id of the asset, for example {@code stone_fire_pit}
 * @param name          its display name
 * @param type          model or texture
 * @param authors       its authors, sorted
 * @param categories    the provider's categories
 * @param tags          the provider's tags
 * @param page          its page at the provider, where its licence is shown
 * @param thumbnailUrl  the provider's thumbnail, or an empty string
 * @param downloadCount how often it was downloaded (orders the results), 0 if unknown
 */
public record RemoteAsset(String provider, String id, String name, AssetType type, List<String> authors, List<String> categories,
                          List<String> tags, String page, String thumbnailUrl, long downloadCount) {
    public RemoteAsset {
        Objects.requireNonNull(provider, "provider cannot be null");
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        authors = List.copyOf(authors);
        categories = List.copyOf(categories);
        tags = List.copyOf(tags);
        Objects.requireNonNull(page, "page cannot be null");
        Objects.requireNonNull(thumbnailUrl, "thumbnailUrl cannot be null");
        if (!PackImporter.isSafe(id) || id.contains("/")) {
            throw new IllegalArgumentException("Unsafe asset id from " + provider + ": '" + id + "'");
        }
    }

    /** The manifest id: provider, a dash, and the asset id with underscores as dashes ({@code polyhaven-stone-fire-pit}). */
    public String manifestId() {
        return provider + "-" + id.replace('_', '-').toLowerCase(Locale.ROOT);
    }

    /** The project folder its files go in ({@code models/thirdparty/polyhaven/stone_fire_pit}). */
    public String folder() {
        return type.folder() + "/thirdparty/" + provider + "/" + id;
    }

    /** True when every word of {@code words} appears in its id, name, a tag or a category (ignoring case). */
    public boolean matches(List<String> words) {
        StringBuilder text = new StringBuilder(id).append(' ').append(name);
        tags.forEach(tag -> text.append(' ').append(tag));
        categories.forEach(category -> text.append(' ').append(category));
        String haystack = text.toString().toLowerCase(Locale.ROOT).replace('_', ' ');
        for (String word : words) {
            if (!haystack.contains(word.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }
}
