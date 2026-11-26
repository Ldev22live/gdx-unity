package io.github.ldev22live.gdxunity.editor.core.project.contract;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Capability negotiation of the shared project contract (TD-106 point 5, roadmap A12.8; ids in
 * {@code docs/contracts/capability-matrix.md}). A project lists {@code required} and {@code optional} capability ids; a client
 * compares them with what it supports:
 * <ul>
 *   <li>a missing <b>required</b> capability: the project opens {@link Access#READ_ONLY}, the reason is shown, and saving is refused,
 *       so nothing the client cannot keep can be overwritten;</li>
 *   <li>a missing <b>optional</b> capability: {@link Access#LIMITED}, editable, with the missing features named; their data is
 *       preserved (TD-106 point 3);</li>
 *   <li>otherwise {@link Access#FULL}.</li>
 * </ul>
 * Nothing is converted to a reduced feature set. On saving, a client adds the ids its own edits need and never removes another
 * client's ids ({@link #requiredAfterSave}).
 */
public final class CapabilityPolicy {
    /** How a project may be used. */
    public enum Access { FULL, LIMITED, READ_ONLY }

    /** The outcome of opening a project, with plain explanations. */
    public record Evaluation(Access access, List<String> missingRequired, List<String> missingOptional, List<String> explanations) {
        public Evaluation {
            missingRequired = List.copyOf(missingRequired);
            missingOptional = List.copyOf(missingOptional);
            explanations = List.copyOf(explanations);
        }

        /** True when saving would be refused. */
        public boolean readOnly() {
            return access == Access.READ_ONLY;
        }
    }

    /**
     * What this desktop editor supports (2026-11-25, A12). {@code scenario/1} and {@code ownership/1} mean the data is read, validated,
     * edited safely and preserved; running scenario rules is {@code scenario-run/1}, not yet supported by any client (roadmap A05).
     */
    public static final Set<String> DESKTOP = Set.of(
        "scene/1", "preserve-unknown/1", "project/1", "terrain/1", "terrain-edit/1", "material/1", "model/gltf", "mesh-edit/1",
        "sprite/2", "vector/1", "animation/1", "graph-run/2", "graph-edit/2", "java-script/1", "song/1", "prefab/1", "atmosphere/1",
        "postprocess/1", "physics2d/1", "physics3d/1", "scenario/1", "ownership/1",
        "profile:platformer/2d", "profile:platformer/2.5d", "profile:platformer/3d", "profile:action-rpg/2.5d",
        "profile:action-rpg/3d", "profile:first-person-shooter/3d", "profile:third-person-shooter/3d");

    private final Set<String> supported;
    private final String clientName;

    /** A policy for a client called {@code clientName} that supports {@code supported}. */
    public CapabilityPolicy(String clientName, Set<String> supported) {
        this.clientName = Objects.requireNonNull(clientName, "clientName cannot be null");
        this.supported = Set.copyOf(Objects.requireNonNull(supported, "supported cannot be null"));
    }

    /** The desktop editor's policy. */
    public static CapabilityPolicy desktop() {
        return new CapabilityPolicy("This desktop editor", DESKTOP);
    }

    /** How {@code manifest} may be used here, and why. */
    public Evaluation evaluate(ProjectManifest manifest) {
        Objects.requireNonNull(manifest, "manifest cannot be null");
        List<String> missingRequired = manifest.required().stream().filter(c -> !supported.contains(c)).toList();
        List<String> missingOptional = manifest.optional().stream().filter(c -> !supported.contains(c)).toList();
        List<String> explanations = new ArrayList<>();
        Access access;
        if (!missingRequired.isEmpty()) {
            access = Access.READ_ONLY;
            explanations.add(clientName + " opens \"" + manifest.name() + "\" read-only: it needs " + String.join(", ", missingRequired)
                + ", which this version does not support. Saving is turned off so that nothing it cannot keep is overwritten. Open it in "
                + "a client that supports " + (missingRequired.size() == 1 ? "it" : "them") + ".");
        } else if (!missingOptional.isEmpty()) {
            access = Access.LIMITED;
            explanations.add(clientName + " cannot show or run " + String.join(", ", missingOptional) + " in \"" + manifest.name()
                + "\". You can edit everything else; that data is kept unchanged when you save.");
        } else {
            access = Access.FULL;
        }
        return new Evaluation(access, missingRequired, missingOptional, explanations);
    }

    /** The {@code required} list after a save: the project's list, in order, plus {@code added} (never one removed). */
    public static List<String> requiredAfterSave(List<String> current, Set<String> added) {
        Set<String> out = new LinkedHashSet<>(current);
        out.addAll(added);
        return new ArrayList<>(out);
    }
}
