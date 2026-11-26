package io.github.ldev22live.gdxunity.editor.core.command;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * The editor's entity clipboard (roadmap A03: copy/paste). Copy takes a snapshot of each entity (name, world position, rotation,
 * scale and every component as the scene file writes it), so editing or deleting the originals afterwards does not change what
 * is pasted. Paste is {@link PasteEntitiesCommand}. Children are not copied, as with Duplicate; each copied entity is pasted at
 * the top level of the scene.
 */
public final class EntityClipboard {
    /** One copied entity. */
    public record Snapshot(String name, Vector3 position, Quaternion rotation, Vector3 scale, JsonValue components) {
        public Snapshot {
            Objects.requireNonNull(name, "name cannot be null");
            position = new Vector3(Objects.requireNonNull(position, "position cannot be null"));
            rotation = new Quaternion(Objects.requireNonNull(rotation, "rotation cannot be null"));
            scale = new Vector3(Objects.requireNonNull(scale, "scale cannot be null"));
        }
    }

    private List<Snapshot> snapshots = List.of();
    private int pasteCount;

    /** Replaces the clipboard with snapshots of {@code entities} (in order); returns how many were copied. */
    public int copy(Collection<Entity> entities, SceneSerializer serializer) {
        Objects.requireNonNull(entities, "entities cannot be null");
        Objects.requireNonNull(serializer, "serializer cannot be null");
        List<Snapshot> copied = new ArrayList<>();
        for (Entity entity : entities) {
            JsonValue components = serializer.entityToJsonValue(entity).get("components");
            copied.add(new Snapshot(entity.getName(), entity.getTransform().getWorldPosition(), entity.getTransform().getLocalRotation(),
                entity.getTransform().getLocalScale(), components));
        }
        snapshots = List.copyOf(copied);
        pasteCount = 0;
        return snapshots.size();
    }

    /** True when nothing has been copied. */
    public boolean isEmpty() {
        return snapshots.isEmpty();
    }

    /** The copied entities, in the order they were copied. */
    public List<Snapshot> getSnapshots() {
        return snapshots;
    }

    /** Counts one paste and returns its number (1 for the first paste after a copy), so repeated pastes do not stack exactly. */
    int nextPaste() {
        return ++pasteCount;
    }
}
