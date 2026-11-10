package io.github.ldev22live.gdxunity.editor.core.asset;

import io.github.ldev22live.gdxunity.engine.core.asset.AssetCatalog;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetMetadata;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Notices assets that were added, removed or changed on disk while the editor runs (Phase 13-C), so a texture or model edited in
 * another program is loaded again without restarting. {@link #update} rescans the catalog every {@link #getIntervalSeconds()}
 * seconds; {@link #check} rescans now. A file counts as changed when its size or modification time differs from the last check.
 *
 * <p>The first check only takes the baseline and reports nothing, so opening a project does not "reimport" every asset.
 */
public final class AssetChangeWatcher {
    /** Default time between rescans: often enough to feel live, rare enough that walking a project folder costs nothing. */
    public static final float DEFAULT_INTERVAL_SECONDS = 2f;

    /**
     * What changed since the last check.
     *
     * @param added    assets that were not there before
     * @param removed  assets that are gone
     * @param modified assets whose size or modification time changed
     */
    public record Changes(List<AssetReference> added, List<AssetReference> removed, List<AssetReference> modified) {
        public boolean isEmpty() {
            return added.isEmpty() && removed.isEmpty() && modified.isEmpty();
        }
    }

    /** Called with the changes found by a check, on the thread that called {@link #update} or {@link #check}. */
    public interface Listener {
        void assetsChanged(Changes changes);
    }

    private record Stamp(long size, long modifiedMillis) {
    }

    private final AssetCatalog catalog;
    private final float intervalSeconds;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private Map<AssetReference, Stamp> known;
    private float sinceCheck;

    public AssetChangeWatcher(AssetCatalog catalog, float intervalSeconds) {
        this.catalog = Objects.requireNonNull(catalog, "catalog cannot be null");
        if (!(intervalSeconds > 0f)) {
            throw new IllegalArgumentException("intervalSeconds must be positive, got " + intervalSeconds);
        }
        this.intervalSeconds = intervalSeconds;
    }

    public float getIntervalSeconds() {
        return intervalSeconds;
    }

    public void addListener(Listener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener cannot be null"));
    }

    /** Counts time; checks the disk when the interval has passed. */
    public void update(float deltaSeconds) {
        sinceCheck += deltaSeconds;
        if (sinceCheck >= intervalSeconds) {
            check();
        }
    }

    /**
     * Rescans the catalog and tells the listeners what differs from the previous check.
     *
     * @return the changes; empty on the first check, which only records the baseline
     */
    public Changes check() {
        sinceCheck = 0f;
        catalog.scan();
        Map<AssetReference, Stamp> now = new HashMap<>();
        for (AssetMetadata meta : catalog.getAllAssets()) {
            now.put(meta.reference(), new Stamp(meta.fileSizeBytes(), meta.lastModifiedMillis()));
        }
        Changes changes = new Changes(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        if (known != null) {
            now.forEach((reference, stamp) -> {
                Stamp before = known.get(reference);
                if (before == null) {
                    changes.added().add(reference);
                } else if (!before.equals(stamp)) {
                    changes.modified().add(reference);
                }
            });
            known.keySet().stream().filter(reference -> !now.containsKey(reference)).forEach(changes.removed()::add);
        }
        known = now;
        if (!changes.isEmpty()) {
            for (Listener listener : listeners) {
                listener.assetsChanged(changes);
            }
        }
        return changes;
    }
}
