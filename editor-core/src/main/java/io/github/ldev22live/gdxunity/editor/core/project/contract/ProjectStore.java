package io.github.ldev22live.gdxunity.editor.core.project.contract;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Where a project's revisions are kept (TD-106 points 6 and 8, roadmap A12.11). Provider-neutral: {@link LocalProjectStore} is the
 * desktop's folder store and needs no network. A cloud store (UrConnex, roadmap A16) implements the same contract once its API is
 * known; it must compare the base revision <b>atomically on the server</b>; a client-side check alone is not enough.
 *
 * <p>No save ever overwrites a newer revision: a save from a stale base is answered with {@link SaveResult.Conflict}, and the person
 * chooses {@link #saveAsCopy} ("Save mine as a copy") or {@link #open()} again ("Reload theirs"). There is no last-write-wins and no
 * automatic sync.
 */
public interface ProjectStore {
    /** The project as stored now, with how this client may use it. */
    OpenResult open() throws IOException;

    /**
     * Saves scene texts as a new revision whose base is {@code baseRevision}.
     *
     * @param sceneTexts      project path to full scene file text, for every scene that changed
     * @param addedRequired   capability ids this client's edits need (added to {@code required}, never removing any)
     * @param createManifest  for a legacy project (no manifest): true only when the person agreed to add one
     */
    SaveResult save(String baseRevision, Map<String, String> sceneTexts, Set<String> addedRequired, boolean createManifest)
        throws IOException;

    /** "Save mine as a copy": the scene texts saved as a new project with a new id, its first revision based on none. */
    OpenResult saveAsCopy(String copyName, Map<String, String> sceneTexts) throws IOException;

    /** An opened project: its manifest (null for a legacy project), what this client may do, and notes for the person. */
    record OpenResult(ProjectManifest manifest, CapabilityPolicy.Evaluation evaluation, List<String> notes) {
        public OpenResult {
            notes = List.copyOf(notes);
        }

        /** The current revision id, or null for a legacy project. */
        public String revision() {
            return manifest == null ? null : manifest.revision().id();
        }
    }

    /** The outcome of a save. */
    sealed interface SaveResult {
        /** Saved as a new revision. */
        record Saved(ProjectManifest manifest) implements SaveResult { }

        /** Not saved: the project has moved on since {@code yourBase}; it is now at {@code current}. */
        record Conflict(String yourBase, String current) implements SaveResult { }

        /** Not saved: the project is open read-only here (a required capability is missing). */
        record ReadOnly(List<String> reasons) implements SaveResult { }

        /** Not saved: a legacy project has no manifest, and the person has not agreed to add one. */
        record NeedsManifest(String message) implements SaveResult { }
    }
}
