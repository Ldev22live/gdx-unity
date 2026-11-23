package io.github.ldev22live.gdxunity.editor.core.material;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.command.EditorCommand;
import io.github.ldev22live.gdxunity.engine.material.Material;
import io.github.ldev22live.gdxunity.engine.material.MaterialLibrary;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Changes one property of a shared {@link Material} and writes the file, with undo and redo (TD-081). The material object is the
 * library's, so every surface that uses it changes at once; the file is written on every execute and undo, so the file on disk
 * always matches what the viewport shows.
 *
 * @param <V> type of the property value
 */
public class ModifyMaterialPropertyCommand<V> implements EditorCommand {
    private final EditorState editorState;
    private final MaterialLibrary library;
    private final String path;
    private final Material material;
    private final V oldValue;
    private final V newValue;
    private final Consumer<V> setter;
    private final String description;

    /**
     * @param path     the material's project-relative path
     * @param material the library's material for that path
     * @param setter   applies a value to the material
     */
    public ModifyMaterialPropertyCommand(EditorState editorState, MaterialLibrary library, String path, Material material,
                                         V oldValue, V newValue, Consumer<V> setter, String description) {
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.library = Objects.requireNonNull(library, "library cannot be null");
        this.path = Objects.requireNonNull(path, "path cannot be null");
        this.material = Objects.requireNonNull(material, "material cannot be null");
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.setter = Objects.requireNonNull(setter, "setter cannot be null");
        this.description = description != null ? description : "Modify Material";
    }

    @Override
    public boolean execute() {
        setter.accept(newValue);
        library.save(path, material);
        editorState.setStatusMessage(description, false);
        return true;
    }

    @Override
    public boolean undo() {
        setter.accept(oldValue);
        library.save(path, material);
        editorState.setStatusMessage("Undid: " + description, false);
        return true;
    }

    @Override
    public String getDescription() {
        return description;
    }
}
