package io.github.ldev22live.gdxunity.editor.core.command;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.engine.scene.Component;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Pastes the {@link EntityClipboard}'s entities into the active scene as new entities with new ids, as one undo step, and selects
 * them. Each paste after a copy is moved a further {@link #PASTE_OFFSET} along the ground (x and z in 2.5D and 3D, x and y in 2D),
 * so pasted copies do not hide each other and 3D copies stay on the ground instead of being lifted.
 */
public class PasteEntitiesCommand implements EditorCommand {
    /** How far, in scene units, each paste is moved from the copied position (per paste since the copy). */
    public static final float PASTE_OFFSET = 2f;

    private final EditorState editorState;
    private final EntityClipboard clipboard;
    private final List<Entity> pasted = new ArrayList<>();
    private final List<List<Component>> componentsToRestore = new ArrayList<>();

    public PasteEntitiesCommand(EditorState editorState, EntityClipboard clipboard) {
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.clipboard = Objects.requireNonNull(clipboard, "clipboard cannot be null");
    }

    @Override
    public boolean execute() {
        if (editorState.isPlayModeActive()) {
            editorState.setStatusMessage("Cannot paste during Play/Pause mode", true);
            return false;
        }
        Scene scene = editorState.getActiveScene();
        if (scene == null) {
            return false;
        }
        if (pasted.isEmpty()) {
            if (clipboard.isEmpty()) {
                editorState.setStatusMessage("Nothing copied to paste", true);
                return false;
            }
            float offset = PASTE_OFFSET * clipboard.nextPaste();
            boolean ground = scene.getGameMode() != GameMode.MODE_2D;
            SceneSerializer serializer = editorState.getSerializer();
            for (EntityClipboard.Snapshot snapshot : clipboard.getSnapshots()) {
                Entity entity = scene.createEntity(snapshot.name());
                Vector3 p = snapshot.position();
                entity.getTransform().setLocalPosition(p.x + offset, ground ? p.y : p.y + offset, ground ? p.z + offset : p.z);
                entity.getTransform().setLocalRotation(snapshot.rotation());
                entity.getTransform().setLocalScale(snapshot.scale());
                if (snapshot.components() != null) {
                    for (JsonValue entry = snapshot.components().child; entry != null; entry = entry.next) {
                        Component copy = serializer.readComponent(entry);
                        if (copy != null) {
                            entity.addComponent(copy);
                        }
                    }
                }
                pasted.add(entity);
            }
        } else {
            // Redo: the same entities (same ids) with the components undo detached
            for (int i = 0; i < pasted.size(); i++) {
                scene.addEntity(pasted.get(i));
                for (Component component : componentsToRestore.get(i)) {
                    pasted.get(i).addComponent(component);
                }
            }
        }
        List<UUID> ids = pasted.stream().map(Entity::getId).toList();
        editorState.setSelectedEntityIds(ids, ids.get(ids.size() - 1));
        editorState.notifyHierarchyModified();
        editorState.setStatusMessage(pasted.size() == 1 ? "Pasted: " + pasted.get(0).getName() : "Pasted " + pasted.size() + " entities", false);
        return true;
    }

    @Override
    public boolean undo() {
        Scene scene = editorState.getActiveScene();
        if (scene == null || pasted.isEmpty()) {
            return false;
        }
        componentsToRestore.clear();
        boolean removedAll = true;
        for (Entity entity : pasted) {
            editorState.removeSelectedEntityId(entity.getId());
            // Scene.removeEntity detaches the components; keep them for redo
            componentsToRestore.add(new ArrayList<>(entity.getAllComponents()));
            removedAll &= scene.removeEntity(entity, false);
        }
        editorState.notifyHierarchyModified();
        editorState.setStatusMessage("Undid paste", false);
        return removedAll;
    }

    @Override
    public String getDescription() {
        return pasted.size() == 1 ? "Paste Entity" : "Paste Entities";
    }

    /** The entities this command created (empty before it first runs). */
    public List<Entity> getPasted() {
        return List.copyOf(pasted);
    }
}
