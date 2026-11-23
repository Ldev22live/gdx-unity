package io.github.ldev22live.gdxunity.engine.core.asset;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Where a file of a project came from, from its two records (roadmap M2b.5, TD-099): {@link ThirdPartyManifest} for downloaded files
 * and {@link OriginalAssets} for art made in this repository. A file in neither is {@link Kind#UNRECORDED}, said plainly, never
 * guessed.
 */
public final class AssetProvenance {
    /** Which record a file is in. */
    public enum Kind {
        /** Downloaded, in {@code licenses/manifest.json}. */
        THIRD_PARTY,
        /** Made in this repository, in {@code licenses/originals.json}. */
        ORIGINAL,
        /** In neither record. */
        UNRECORDED
    }

    /**
     * A file's provenance.
     *
     * @param kind    which record it is in
     * @param licence its licence's SPDX id, or empty when unrecorded
     * @param summary one line for a person: the licence, who made it and how, e.g. "CC0-1.0: Nature Kit by Kenney (kenney.nl/...)"
     * @param sha256  the SHA-256 the record gives, or empty when unrecorded
     */
    public record Provenance(Kind kind, String licence, String summary, String sha256) {
        public Provenance {
            Objects.requireNonNull(kind, "kind cannot be null");
            Objects.requireNonNull(licence, "licence cannot be null");
            Objects.requireNonNull(summary, "summary cannot be null");
            Objects.requireNonNull(sha256, "sha256 cannot be null");
        }
    }

    /** What an unrecorded file is called. */
    public static final String UNRECORDED_SUMMARY = "No licence record (made in this project, or copied in by hand)";

    private final ThirdPartyManifest thirdParty;
    private final OriginalAssets originals;

    public AssetProvenance(ThirdPartyManifest thirdParty, OriginalAssets originals) {
        this.thirdParty = Objects.requireNonNull(thirdParty, "thirdParty cannot be null");
        this.originals = Objects.requireNonNull(originals, "originals cannot be null");
    }

    /**
     * Reads both records under {@code assetsRoot}; a missing record counts as empty.
     *
     * @throws IOException              when a record cannot be read
     * @throws IllegalArgumentException when a record is not valid
     */
    public static AssetProvenance read(File assetsRoot) throws IOException {
        Objects.requireNonNull(assetsRoot, "assetsRoot cannot be null");
        return new AssetProvenance(ThirdPartyManifest.read(assetsRoot), OriginalAssets.read(assetsRoot));
    }

    /** The provenance of the file at {@code path}, relative to the asset root with forward slashes. */
    public Provenance of(String path) {
        Objects.requireNonNull(path, "path cannot be null");
        Optional<ThirdPartyManifest.Entry> downloaded = thirdParty.find(path);
        if (downloaded.isPresent()) {
            ThirdPartyManifest.Entry entry = downloaded.get();
            String sha = entry.files().stream().filter(file -> file.path().equals(path)).findFirst()
                .map(ThirdPartyManifest.FileRecord::sha256).orElseThrow();
            return new Provenance(Kind.THIRD_PARTY, entry.licence(),
                entry.licence() + ": " + entry.name() + " by " + String.join(", ", entry.authors()) + " (" + entry.source() + ")", sha);
        }
        Optional<OriginalAssets.FileRecord> made = originals.find(path);
        if (made.isPresent()) {
            OriginalAssets.FileRecord file = made.get();
            StringBuilder summary = new StringBuilder(file.licence()).append(": made in this repository with ").append(file.madeWith());
            List<String> shown = new ArrayList<>();
            for (String id : file.derivedFrom()) {
                shown.add(thirdParty.entry(id).map(entry -> entry.name() + " by " + String.join(", ", entry.authors()) + ", "
                    + entry.licence()).orElse(id + " (no such record)"));
            }
            if (!shown.isEmpty()) {
                summary.append("; shows ").append(String.join("; ", shown));
            }
            return new Provenance(Kind.ORIGINAL, file.licence(), summary.toString(), file.sha256());
        }
        return new Provenance(Kind.UNRECORDED, "", UNRECORDED_SUMMARY, "");
    }
}
