package io.github.ldev22live.gdxunity.editor.core.asset.library;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * An asset pack the Asset Library offers to import from a file a person downloaded (UI M4): who made it, where its page is, and the
 * licence its provider states. ForgeRoom never downloads such a pack itself (TD-095: Kenney has no API and disallows automated
 * agents), so the page is opened in the browser and the downloaded archive is imported by {@link PackImporter}.
 *
 * @param provider        the provider's id, a lowercase word ("kenney"); the pack is imported under {@code thirdparty/<provider>/}
 * @param id              the pack's id, lowercase words joined by hyphens ("nature-kit")
 * @param name            the pack's name as its page gives it
 * @param category        what it holds, in a few words ("trees, rocks, plants")
 * @param page            its page, where a person downloads it
 * @param author          who made it
 * @param licenceEvidence the page that states the provider's licence
 */
public record CuratedPack(String provider, String id, String name, String category, String page, String author,
                          String licenceEvidence) {
    private static final Pattern WORD = Pattern.compile("[a-z0-9]+");
    private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

    public CuratedPack {
        Objects.requireNonNull(provider, "provider cannot be null");
        Objects.requireNonNull(id, "id cannot be null");
        if (!WORD.matcher(provider).matches() || !SLUG.matcher(id).matches()) {
            throw new IllegalArgumentException("A pack needs a lowercase provider and id: " + provider + "/" + id);
        }
        for (String url : new String[] {Objects.requireNonNull(page, "page cannot be null"),
            Objects.requireNonNull(licenceEvidence, "licenceEvidence cannot be null")}) {
            if (!url.startsWith("https://")) {
                throw new IllegalArgumentException("A pack's links must be https: " + url);
            }
        }
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(category, "category cannot be null");
        Objects.requireNonNull(author, "author cannot be null");
    }

    /** The manifest id of the imported pack: {@code <provider>-<id>}. */
    public String manifestId() {
        return provider + "-" + id;
    }

    /** Where the pack's files go, relative to the asset root: {@code thirdparty/<provider>/<id>}. */
    public String folder() {
        return "thirdparty/" + provider + "/" + id;
    }
}
