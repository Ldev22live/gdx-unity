package io.github.ldev22live.gdxunity.editor.core.project;

import io.github.ldev22live.gdxunity.engine.core.asset.ThirdPartyManifest;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Keeps licence records with third-party files when they are copied into another project (TD-094, roadmap M2b.2): a template, or a new
 * project's starter palette, that copies a downloaded file also gets that file's record from the source's {@code licenses/manifest.json},
 * so the new project can say where every file came from. Only the copied files are recorded; records the target already has are kept.
 */
public final class ProjectLicences {
    private ProjectLicences() { }

    /**
     * Records in {@code targetRoot}'s manifest every file of {@code copied} that {@code sourceRoot}'s manifest lists.
     *
     * @param copied project-relative paths that were copied from the source to the target
     * @return whether the target's manifest was written (false when no copied file is a recorded third-party file)
     * @throws IOException if a manifest cannot be read or written
     */
    public static boolean carry(File sourceRoot, File targetRoot, Collection<String> copied) throws IOException {
        Objects.requireNonNull(targetRoot, "targetRoot cannot be null");
        ThirdPartyManifest existing = new File(targetRoot, ThirdPartyManifest.PATH).isFile()
            ? ThirdPartyManifest.read(targetRoot) : ThirdPartyManifest.empty();
        Optional<ThirdPartyManifest> merged = merged(sourceRoot, existing, copied);
        if (merged.isEmpty()) {
            return false;
        }
        merged.get().write(targetRoot);
        return true;
    }

    /**
     * The manifest a project with no records of its own would get for {@code copied}, without writing anything: for the template
     * gallery's file count and size.
     *
     * @return empty when no copied file is a recorded third-party file
     */
    public static Optional<ThirdPartyManifest> recordFor(File sourceRoot, Collection<String> copied) throws IOException {
        return merged(sourceRoot, ThirdPartyManifest.empty(), copied);
    }

    /** {@code target} with the source's records of {@code copied} added; empty when nothing was added. */
    private static Optional<ThirdPartyManifest> merged(File sourceRoot, ThirdPartyManifest target, Collection<String> copied)
        throws IOException {
        Objects.requireNonNull(sourceRoot, "sourceRoot cannot be null");
        Objects.requireNonNull(copied, "copied cannot be null");
        if (!new File(sourceRoot, ThirdPartyManifest.PATH).isFile()) {
            return Optional.empty();
        }
        ThirdPartyManifest source = ThirdPartyManifest.read(sourceRoot);
        Set<String> wanted = new HashSet<>(copied);
        boolean changed = false;
        for (ThirdPartyManifest.Entry entry : source.entries()) {
            List<ThirdPartyManifest.FileRecord> kept = entry.files().stream().filter(file -> wanted.contains(file.path())).toList();
            if (kept.isEmpty()) {
                continue;
            }
            List<ThirdPartyManifest.FileRecord> files = new ArrayList<>(
                target.entry(entry.id()).map(ThirdPartyManifest.Entry::files).orElse(List.of()));
            Set<String> present = new HashSet<>();
            files.forEach(file -> present.add(file.path()));
            boolean added = false;
            for (ThirdPartyManifest.FileRecord file : kept) {
                if (present.add(file.path())) {
                    files.add(file);
                    added = true;
                }
            }
            if (!added) {
                continue;
            }
            target = target.with(new ThirdPartyManifest.Entry(entry.id(), entry.name(), entry.category(), entry.source(), entry.licence(),
                entry.licenceEvidence(), entry.authors(), entry.retrieved(), entry.retrievedWith(), entry.format(), entry.conversion(),
                entry.importStatus(), files));
            changed = true;
        }
        return changed ? Optional.of(target) : Optional.empty();
    }
}
