package io.github.ldev22live.gdxunity.engine.scene.navigation;

import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A scene's walkable areas (roadmap M6.4, TD-092): one {@link NavGrid3D} per enabled {@link NavMeshBoundsComponent} on an active
 * entity, baked the first time anything asks ({@link #of}) and kept for the scene's life, since obstacles that are baked do not move.
 * Call {@link #rebake()} after moving them. A box that cannot be baked is left out and its reason kept in {@link #getProblems()}.
 */
public final class NavigationSystem3D {
    /** One baked box. */
    public record Region(Entity entity, NavMeshBoundsComponent bounds, NavGrid3D grid) {}

    private final Scene scene;
    private final List<Region> regions = new ArrayList<>();
    private final List<String> problems = new ArrayList<>();

    private NavigationSystem3D(Scene scene) {
        this.scene = scene;
        rebake();
    }

    /** The navigation of {@code scene}, baked now if it has none yet. */
    public static NavigationSystem3D of(Scene scene) {
        Objects.requireNonNull(scene, "scene cannot be null");
        NavigationSystem3D navigation = scene.getNavigation();
        if (navigation == null) {
            navigation = new NavigationSystem3D(scene);
            scene.setNavigation(navigation);
        }
        return navigation;
    }

    /**
     * A fresh bake of {@code scene} that is not kept on it: for a check while editing, when the scene will still change before it
     * plays.
     */
    public static NavigationSystem3D bake(Scene scene) {
        return new NavigationSystem3D(Objects.requireNonNull(scene, "scene cannot be null"));
    }

    /** Bakes every Nav Mesh Bounds of the scene again, from what the scene holds now. */
    public void rebake() {
        regions.clear();
        problems.clear();
        for (Entity entity : scene.getAllEntities()) {
            NavMeshBoundsComponent bounds = entity.getComponent(NavMeshBoundsComponent.class);
            if (bounds == null || !bounds.isEnabled() || !entity.isActiveInHierarchy()) continue;
            try {
                NavMeshBaker.Result result = NavMeshBaker.bake(scene, entity, bounds);
                regions.add(new Region(entity, bounds, result.grid()));
                problems.addAll(result.warnings());
            } catch (IllegalArgumentException e) {
                problems.add(e.getMessage());
            }
        }
    }

    /** The baked boxes, in scene order. */
    public List<Region> getRegions() {
        return Collections.unmodifiableList(regions);
    }

    /** What could not be baked, or was left out of a bake, as sentences. */
    public List<String> getProblems() {
        return Collections.unmodifiableList(problems);
    }

    /** The first grid over world x, z, or null. */
    public NavGrid3D gridAt(float x, float z) {
        for (Region region : regions) {
            if (region.grid().contains(x, z)) {
                return region.grid();
            }
        }
        return null;
    }

    /**
     * A path from {@code from} to {@code to} on the grid under {@code from} ({@link NavGrid3D#findPath}); empty when no grid lies
     * under {@code from} or there is no way through.
     */
    public List<Vector3> findPath(Vector3 from, Vector3 to) {
        Objects.requireNonNull(from, "from cannot be null");
        Objects.requireNonNull(to, "to cannot be null");
        NavGrid3D grid = gridAt(from.x, from.z);
        return grid == null ? List.of() : grid.findPath(from, to);
    }
}
