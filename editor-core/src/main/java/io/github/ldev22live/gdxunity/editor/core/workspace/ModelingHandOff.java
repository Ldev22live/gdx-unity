package io.github.ldev22live.gdxunity.editor.core.workspace;

import io.github.ldev22live.gdxunity.editor.core.material.MaterialAssignment;
import io.github.ldev22live.gdxunity.engine.render.components.DynamicMeshComponent;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * What the 3D Modeling workspace can do for the selected object today (roadmap M3c.4, Appendix E2 and R3c): the capability notice the
 * workspace shows, the same facts the agent's {@code model_edit_check} tool reports, and whether Customize's <b>Edit 3D Model</b> hands
 * the object over. Until mesh editing exists (roadmap R7a) a model or shape is opened in context with a clear notice that its vertices,
 * edges and faces cannot be edited yet; transforming it is never presented as editing its mesh.
 */
public final class ModelingHandOff {
    /** Label of the Customize entry, before the object's name (the roadmap's own words). */
    public static final String EDIT_3D_MODEL = "Edit 3D Model";
    /** Title of the notice for a model or shape whose mesh cannot be edited yet. */
    public static final String MESH_PLANNED_TITLE = "Mesh editing is not available yet";

    /** What 3D Modeling can do for an object. */
    public enum Capability {
        /** A sculpt: the brushes reshape it here. */
        SCULPT,
        /** A glTF model or a shape: shown here in context; mesh editing is planned (R7a), a sculpt copy is offered. */
        MESH_PLANNED,
        /** An object with no 3D mesh (a light, a camera, a sprite, an empty). */
        NO_MESH,
        /** Nothing is selected. */
        NO_SELECTION
    }

    /**
     * What 3D Modeling can do for one object, as a reader sees it.
     *
     * @param capability   the kind of case
     * @param title        one line, the notice's heading
     * @param lines        the notice's sentences, most important first
     * @param meshEditing  whether vertices, edges and faces can be edited (false until R7a)
     * @param sculptCopy   whether a project-owned sculpt copy can be made from its mesh
     * @param materialSlot whether its material can be changed in Details
     * @param modelPath    the glTF file it shows, or null when it shows none
     */
    public record Notice(Capability capability, String title, List<String> lines, boolean meshEditing, boolean sculptCopy,
                         boolean materialSlot, String modelPath) {
        public Notice {
            Objects.requireNonNull(capability, "capability cannot be null");
            Objects.requireNonNull(title, "title cannot be null");
            lines = List.copyOf(lines);
        }

        /** Whether the workspace shows this notice; a sculpt needs none, its brush strip says what it can do. */
        public boolean isShown() {
            return capability != Capability.SCULPT;
        }

        /** The title and the first line, for the status bar. */
        public String summary() {
            return lines.isEmpty() ? title : title + ". " + lines.get(0);
        }
    }

    private ModelingHandOff() { }

    /** Whether Customize offers {@link #EDIT_3D_MODEL} for {@code entity}: a glTF model or a shape that is not a sculpt. */
    public static boolean offersEdit3DModel(Entity entity) {
        return entity != null && capabilityOf(entity) == Capability.MESH_PLANNED;
    }

    /** The Customize label for {@code entity}, which says mesh editing is planned, so a transform is never taken for it. */
    public static String edit3DModelLabel(Entity entity) {
        Objects.requireNonNull(entity, "entity cannot be null");
        return EDIT_3D_MODEL + " of " + entity.getName() + " (opens 3D Modeling; mesh editing is planned)";
    }

    /** The notice for {@code entity}, or for no selection when it is null. */
    public static Notice noticeFor(Entity entity) {
        if (entity == null) {
            return new Notice(Capability.NO_SELECTION, "Nothing selected",
                List.of("Select a model, shape or sculpt in the World to work on it here."), false, false, false, null);
        }
        String name = entity.getName();
        ModelComponent model = entity.getComponent(ModelComponent.class);
        String modelPath = model == null || model.getModelPath() == null || model.getModelPath().isBlank() ? null : model.getModelPath();
        boolean slot = MaterialAssignment.hasSlot(entity);
        switch (capabilityOf(entity)) {
            case SCULPT -> {
                return new Notice(Capability.SCULPT, "Sculpting " + name,
                    List.of("The brushes reshape " + name + "; Save Sculpts writes the change."), false, false, false, modelPath);
            }
            case NO_MESH -> {
                return new Notice(Capability.NO_MESH, name + " has no 3D mesh",
                    List.of(name + " has no model, shape or sculpt to work on here. Go Back to World, or select a model, shape or sculpt."),
                    false, false, slot, modelPath);
            }
            default -> {
                List<String> lines = new ArrayList<>();
                lines.add("Editing the vertices, edges and faces of " + name + " is planned (roadmap R7a)"
                    + (modelPath == null ? "." : "; nothing here changes " + fileName(modelPath) + "."));
                lines.add("Sculpt a copy: Customize > Edit " + name + " in 3D (sculpt a copy) makes a new project-owned sculpt from its mesh; "
                    + name + " stays as it is.");
                lines.add("Move, Rotate or Scale changes only where " + name + " stands, never its mesh.");
                lines.add(slot ? "Its material: Customize > Edit Material, in Details."
                    : "A glTF model has no material slot yet, so its look cannot be changed here.");
                return new Notice(Capability.MESH_PLANNED, MESH_PLANNED_TITLE, lines, false, true, slot, modelPath);
            }
        }
    }

    private static Capability capabilityOf(Entity entity) {
        if (entity.getComponent(DynamicMeshComponent.class) != null) {
            return Capability.SCULPT;
        }
        if (entity.getComponent(ModelComponent.class) != null || entity.getComponent(MeshPrimitiveComponent.class) != null) {
            return Capability.MESH_PLANNED;
        }
        return Capability.NO_MESH;
    }

    private static String fileName(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }
}
