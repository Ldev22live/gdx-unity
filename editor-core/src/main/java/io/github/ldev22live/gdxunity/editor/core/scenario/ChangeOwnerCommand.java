package io.github.ldev22live.gdxunity.editor.core.scenario;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.command.EditorCommand;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.scenario.OwnerComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Gives entities a new owner, or makes them unowned, in one undo step (TD-108). Each entity's previous {@link OwnerComponent}
 * object is removed and kept, and a new one added, so undo puts back the very components that were there and other commands that
 * hold them keep working. It never checks the player id: callers ({@link ScenarioEditor}) do.
 */
public class ChangeOwnerCommand implements EditorCommand {
    private final EditorState editorState;
    private final List<UUID> entityIds;
    private final String playerId;
    private final String description;
    private final List<Change> changes = new ArrayList<>();

    private record Change(UUID entityId, List<OwnerComponent> before, OwnerComponent after) {
    }

    /**
     * @param playerId the new owner's id, or null to make the entities unowned
     */
    public ChangeOwnerCommand(EditorState editorState, List<UUID> entityIds, String playerId, String description) {
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.entityIds = List.copyOf(Objects.requireNonNull(entityIds, "entityIds cannot be null"));
        if (this.entityIds.isEmpty()) {
            throw new IllegalArgumentException("entityIds cannot be empty");
        }
        this.playerId = playerId;
        this.description = Objects.requireNonNull(description, "description cannot be null");
    }

    @Override
    public boolean execute() {
        Scene scene = editorState.getActiveScene();
        if (scene == null) {
            return false;
        }
        if (changes.isEmpty()) {
            for (UUID id : entityIds) {
                Entity entity = scene.findEntityById(id);
                if (entity == null) {
                    return false;
                }
                List<OwnerComponent> before = List.copyOf(entity.getComponents(OwnerComponent.class));
                boolean same = playerId == null ? before.isEmpty()
                    : before.size() == 1 && before.get(0).getPlayerId().equals(playerId);
                if (!same) {
                    changes.add(new Change(id, before, playerId == null ? null : new OwnerComponent(playerId)));
                }
            }
            if (changes.isEmpty()) {
                return false;
            }
        }
        for (Change change : changes) {
            Entity entity = scene.findEntityById(change.entityId());
            if (entity == null) {
                return false;
            }
            change.before().forEach(entity::removeComponent);
            if (change.after() != null) {
                entity.addComponent(change.after());
            }
        }
        editorState.notifyHierarchyModified();
        return true;
    }

    @Override
    public boolean undo() {
        Scene scene = editorState.getActiveScene();
        if (scene == null || changes.isEmpty()) {
            return false;
        }
        for (int i = changes.size() - 1; i >= 0; i--) {
            Change change = changes.get(i);
            Entity entity = scene.findEntityById(change.entityId());
            if (entity == null) {
                return false;
            }
            if (change.after() != null) {
                entity.removeComponent(change.after());
            }
            change.before().forEach(entity::addComponent);
        }
        editorState.notifyHierarchyModified();
        return true;
    }

    @Override
    public String getDescription() {
        return description;
    }
}
