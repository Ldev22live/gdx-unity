package io.github.ldev22live.gdxunity.editor.core.material;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.command.ModifyComponentPropertyCommand;
import io.github.ldev22live.gdxunity.engine.material.MaterialLibrary;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;

import java.util.Objects;

/**
 * Puts a {@code .material} on an entity's material slot as one undoable step (roadmap M2.2, TD-081): the one way Details, a material
 * dropped from the Content Browser and the agent's {@code material_assign} tool change which material a surface uses. Today the slot is
 * on a shape ({@link MeshPrimitiveComponent}); a glTF model, a sculpt or a sprite has none, and is refused with that reason.
 */
public final class MaterialAssignment {
    /** What {@link #assign} did. Anything but {@link #APPLIED} and {@link #UNCHANGED} leaves the scene as it was. */
    public enum Result {
        /** The slot now names the material, in one undo step. */
        APPLIED,
        /** The slot already named it; nothing was added to the undo history. */
        UNCHANGED,
        /** There was no entity to put it on. */
        NO_TARGET,
        /** The entity has no material slot. */
        NO_SLOT,
        /** The path does not name a {@code .material} file. */
        NOT_A_MATERIAL,
        /** The material file is missing or cannot be read. */
        UNREADABLE,
        /** The editor refused the change, for example during Play. */
        REJECTED
    }

    private MaterialAssignment() { }

    /** Whether {@code entity} has a slot a material can go in. */
    public static boolean hasSlot(Entity entity) {
        return entity != null && entity.getComponent(MeshPrimitiveComponent.class) != null;
    }

    /**
     * Puts the material at {@code path} on {@code target}, or empties the slot when {@code path} is blank (the shape's own color is used
     * again). Every outcome other than {@link Result#APPLIED} or {@link Result#UNCHANGED} is explained in the status line.
     *
     * @param library the project's materials, used to check that the file can be read
     * @param target  the entity to change, or null when nothing was under the drop
     * @param path    a project-relative {@code .material} path, or blank for none
     */
    public static Result assign(EditorState editorState, MaterialLibrary library, Entity target, String path) {
        Objects.requireNonNull(editorState, "editorState cannot be null");
        Objects.requireNonNull(library, "library cannot be null");
        String trimmed = path == null ? "" : path.trim();
        if (!trimmed.isEmpty() && !MaterialAssets.isMaterialPath(trimmed)) {
            editorState.setStatusMessage("A material is a " + MaterialAssets.EXTENSION + " file: " + trimmed, true);
            return Result.NOT_A_MATERIAL;
        }
        if (target == null) {
            editorState.setStatusMessage("Drop the material on a shape in the scene, or on Details with a shape selected", true);
            return Result.NO_TARGET;
        }
        MeshPrimitiveComponent shape = target.getComponent(MeshPrimitiveComponent.class);
        if (shape == null) {
            editorState.setStatusMessage(target.getName() + " has no material slot: only shapes (Mesh Primitive) take a material so far",
                true);
            return Result.NO_SLOT;
        }
        if (!trimmed.isEmpty() && library.get(trimmed) == null) {
            editorState.setStatusMessage(library.getError(trimmed), true);
            return Result.UNREADABLE;
        }
        if (trimmed.equals(shape.getMaterialPath())) {
            editorState.setStatusMessage(target.getName() + " already uses " + (trimmed.isEmpty() ? "no material" : trimmed), false);
            return Result.UNCHANGED;
        }
        String description = trimmed.isEmpty() ? "Remove Material from " + target.getName()
            : "Apply Material " + fileName(trimmed) + " to " + target.getName();
        boolean done = editorState.executeCommand(new ModifyComponentPropertyCommand<MeshPrimitiveComponent, String>(
            editorState, target.getId(), shape, shape.getMaterialPath(), trimmed, shape::setMaterialPath, description));
        return done ? Result.APPLIED : Result.REJECTED;
    }

    private static String fileName(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }
}
