package io.github.ldev22live.gdxunity.engine.scene.prefab;

import io.github.ldev22live.gdxunity.engine.scene.BaseComponent;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Links an entity to the entity it was instantiated from inside a prefab asset (TD-027).
 * Every entity of a prefab instance carries one; the instance root also records which prefab entities
 * existed at the last sync, so entities added to or removed from the prefab later can be told apart from
 * entities the user deleted in this instance.
 * <p>
 * {@link #getOverrides()} holds the property paths that differed from the prefab when the scene was last
 * saved. The editor recomputes them by comparison; the stored copy lets a scene opened later keep its
 * overrides when the prefab changed in the meantime.
 */
public class PrefabInstanceComponent extends BaseComponent {
    private String prefabPath;
    private UUID sourceId;
    private boolean root;
    private final Set<String> overrides = new LinkedHashSet<>();
    private final Set<UUID> knownSourceIds = new LinkedHashSet<>();

    public PrefabInstanceComponent(String prefabPath, UUID sourceId, boolean root) {
        this.prefabPath = Objects.requireNonNull(prefabPath, "prefabPath cannot be null");
        this.sourceId = Objects.requireNonNull(sourceId, "sourceId cannot be null");
        this.root = root;
    }

    /** Asset path of the prefab, relative to the asset root (for example {@code prefabs/Crate.prefab}). */
    public String getPrefabPath() {
        return prefabPath;
    }

    public void setPrefabPath(String prefabPath) {
        this.prefabPath = Objects.requireNonNull(prefabPath, "prefabPath cannot be null");
    }

    /** Id of the matching entity inside the prefab asset. */
    public UUID getSourceId() {
        return sourceId;
    }

    public void setSourceId(UUID sourceId) {
        this.sourceId = Objects.requireNonNull(sourceId, "sourceId cannot be null");
    }

    public boolean isRoot() {
        return root;
    }

    public void setRoot(boolean root) {
        this.root = root;
    }

    public Set<String> getOverrides() {
        return Collections.unmodifiableSet(overrides);
    }

    public void setOverrides(Set<String> paths) {
        overrides.clear();
        if (paths != null) {
            overrides.addAll(paths);
        }
    }

    /** Prefab entity ids present at the last sync; only meaningful on the instance root. */
    public Set<UUID> getKnownSourceIds() {
        return Collections.unmodifiableSet(knownSourceIds);
    }

    public void setKnownSourceIds(Set<UUID> ids) {
        knownSourceIds.clear();
        if (ids != null) {
            knownSourceIds.addAll(ids);
        }
    }
}
