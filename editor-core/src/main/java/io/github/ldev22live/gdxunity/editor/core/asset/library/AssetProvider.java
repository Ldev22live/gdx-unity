package io.github.ldev22live.gdxunity.editor.core.asset.library;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A source of CC0 assets the Asset Library searches and downloads from (TD-095; the provider-neutral interface of roadmap M2b.1).
 * A provider goes online only when asked (a search or a download a person started), and serves its last catalogue from the disk
 * cache when it cannot be reached.
 */
public interface AssetProvider {
    /**
     * A catalogue of one asset type.
     *
     * @param assets  every asset of the type
     * @param fetched when it was fetched from the provider
     * @param offline true when the provider could not be reached and this is the cached copy
     * @param problem why the provider could not be reached, or an empty string
     */
    record Catalog(List<RemoteAsset> assets, Instant fetched, boolean offline, String problem) {
        public Catalog {
            assets = List.copyOf(assets);
            Objects.requireNonNull(fetched, "fetched cannot be null");
            Objects.requireNonNull(problem, "problem cannot be null");
        }

        /** The assets whose id, name, tags or categories hold every word of {@code query}, most downloaded first. */
        public List<RemoteAsset> search(String query) {
            List<String> words = query == null || query.isBlank() ? List.of() : List.of(query.trim().split("\\s+"));
            return assets.stream()
                .filter(asset -> asset.matches(words))
                .sorted((a, b) -> Long.compare(b.downloadCount(), a.downloadCount()))
                .toList();
        }
    }

    /** The provider's id, used in folders and manifest ids ({@code polyhaven}). */
    String id();

    /** Its name as a person reads it. */
    String name();

    /** The credit shown wherever its live results are shown ("Powered by Poly Haven"). */
    String credit();

    /** Its home page, opened from the credit. */
    String homePage();

    /** Where its licence is stated (the manifest's licence evidence, TD-094). */
    String licenceEvidence();

    /** The asset types it offers. */
    List<AssetType> types();

    /**
     * The catalogue of one type: fetched from the provider the first time in a session, then kept; served from the disk cache, marked
     * offline, when the provider cannot be reached.
     *
     * @throws IOException when the provider cannot be reached and nothing is cached
     */
    Catalog catalog(AssetType type) throws IOException;

    /**
     * The resolutions an asset can be downloaded in, smallest first, each with every file it needs.
     *
     * @throws IOException when the provider cannot be reached
     */
    List<DownloadOption> options(RemoteAsset asset) throws IOException;

    /** What the manifest's {@code retrievedWith} says for a download at {@code option}. */
    String retrievedWith(DownloadOption option);

    /**
     * How a material uses a file unpacked from one of this provider's archives, by its path inside the archive; {@link
     * DownloadOption.Role#OTHER} when it is not a map a material uses.
     */
    default DownloadOption.Role roleOf(String path) {
        return DownloadOption.Role.OTHER;
    }

    /** The option picked first: 2K when offered (TD-095), else the largest below it, else the smallest. */
    static DownloadOption preferred(List<DownloadOption> options) {
        if (options.isEmpty()) {
            throw new IllegalArgumentException("No download options");
        }
        DownloadOption best = null;
        for (DownloadOption option : options) {
            if (option.size() <= 2 && (best == null || option.size() > best.size())) {
                best = option;
            }
        }
        return best != null ? best : options.stream().min((a, b) -> Integer.compare(a.size(), b.size())).orElseThrow();
    }
}
