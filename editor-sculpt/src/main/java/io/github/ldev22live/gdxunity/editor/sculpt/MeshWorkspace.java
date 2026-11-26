package io.github.ldev22live.gdxunity.editor.sculpt;

import com.badlogic.gdx.math.Matrix4;
import io.github.ldev22live.gdxunity.editor.core.workspace.ModelingHandOff;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.prefab.PrefabInstanceComponent;
import io.github.ldev22live.gdxunity.engine.sculpt.EditableMesh;
import io.github.ldev22live.gdxunity.engine.sculpt.StaticMeshImport;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Opens individual mesh sources against a selected model or prefab instance in the World. */
public final class MeshWorkspace {
    /** A source document paired with the scene entity whose placement is used as reference. */
    public record OpenPart(Entity entity, EditableMesh mesh) {
        /** Returns the source-to-world placement, including the instance's model import scale. */
        public Matrix4 worldPlacement() {
            float scale = entity.getComponent(ModelComponent.class).getImportScale();
            return entity.getTransform().getWorldMatrix().scale(scale, scale, scale);
        }
    }

    private final Path assetRoot;
    private final Entity selection;
    private final List<Entity> parts;
    private OpenPart activePart;

    public MeshWorkspace(Path assetRoot, Entity selection) {
        this.assetRoot = Objects.requireNonNull(assetRoot, "assetRoot cannot be null");
        this.selection = Objects.requireNonNull(selection, "selection cannot be null");
        parts = ModelingHandOff.modelParts(selection);
    }

    /** Model-bearing parts available under the selection. */
    public List<Entity> parts() {
        return parts;
    }

    /** The linked prefab path, or null for a standalone model. */
    public String prefabPath() {
        PrefabInstanceComponent link = selection.getComponent(PrefabInstanceComponent.class);
        return link == null ? null : link.getPrefabPath();
    }

    /** The successfully opened part, or null after a failed open or before opening a source. */
    public OpenPart activePart() {
        return activePart;
    }

    /** Opens a detached source document in memory. The scene entity supplies its placement only. */
    public OpenPart open(Entity part) throws IOException {
        activePart = null;
        if (!parts.contains(part)) {
            throw new IllegalArgumentException("Mesh part does not belong to the selected object: " + part);
        }
        String source = part.getComponent(ModelComponent.class).getModelPath();
        if (source == null || source.isBlank()) {
            throw new IOException("No model source assigned to " + part.getName());
        }
        try {
            activePart = new OpenPart(part, StaticMeshImport.read(assetRoot, Path.of(source)));
            return activePart;
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid model source " + source + ": " + e.getMessage(), e);
        }
    }
}
