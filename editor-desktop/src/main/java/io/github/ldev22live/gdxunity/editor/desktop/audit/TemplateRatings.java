package io.github.ldev22live.gdxunity.editor.desktop.audit;

import io.github.ldev22live.gdxunity.editor.core.project.TemplateGallery;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The label the New Game gallery shows for a template (roadmap M1.7, Appendix F5), read from the sample-to-template matrix every time
 * a template is shown, never written by hand: the gallery and the table in {@code docs/FEATURE_TEST_MATRIX.md} come from the same
 * {@link TemplateMatrix#evaluate} on the same scene file, so they cannot disagree.
 */
public final class TemplateRatings {
    /**
     * What the gallery shows for one template.
     *
     * @param label   the label
     * @param reasons why, one line each: the domains that fail with their evidence, the flattened content and its replacement, and
     *                for {@link TemplateMatrix.Label#CHECKS_PENDING} the checks not yet made
     */
    public record Rating(TemplateMatrix.Label label, List<String> reasons) {
        public Rating {
            Objects.requireNonNull(label, "label cannot be null");
            reasons = List.copyOf(reasons);
        }

        /** The sentence under the template's details, e.g. "Technical Demo / Partially Editable: some parts it uses cannot ...". */
        public String headline() {
            return label.text() + ": " + label.meaning() + ".";
        }
    }

    private final File projectRoot;
    private final EditabilityAudit audit;

    /**
     * @param projectRoot the project's asset folder, which the catalog's scene paths are relative to
     * @param audit       the editability audit, with the editor's own serializers and Details editors
     */
    public TemplateRatings(File projectRoot, EditabilityAudit audit) {
        this.projectRoot = Objects.requireNonNull(projectRoot, "projectRoot cannot be null");
        this.audit = Objects.requireNonNull(audit, "audit cannot be null");
    }

    /**
     * Rates a sample template from its scene file as it is now.
     *
     * @throws IllegalArgumentException for an empty start, which is not a template and has nothing to rate
     * @throws IOException              if the scene cannot be read
     */
    public Rating rate(TemplateGallery.Choice choice) throws IOException {
        Objects.requireNonNull(choice, "choice cannot be null");
        if (choice.kind() != TemplateGallery.Kind.SAMPLE) {
            throw new IllegalArgumentException("Only sample templates are rated, not " + choice.title());
        }
        File scene = new File(projectRoot, choice.scene());
        return of(TemplateMatrix.evaluate(projectRoot, scene, audit.audit(scene)));
    }

    /** The rating for one matrix entry. */
    public static Rating of(TemplateMatrix.Entry entry) {
        Objects.requireNonNull(entry, "entry cannot be null");
        List<String> reasons = new ArrayList<>();
        for (TemplateMatrix.Domain domain : entry.failures()) {
            reasons.add(domain.label() + ": " + entry.cells().get(domain).evidence());
        }
        for (TemplateMatrix.Flattened item : entry.flattened()) {
            reasons.add("Flattened " + item.describe());
        }
        if (entry.label() == TemplateMatrix.Label.CHECKS_PENDING) {
            reasons.add("Not yet checked: " + String.join(", ", TemplateMatrix.UNCHECKED_GATES));
        }
        return new Rating(entry.label(), reasons);
    }
}
