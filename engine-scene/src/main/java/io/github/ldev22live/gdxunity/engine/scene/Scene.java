package io.github.ldev22live.gdxunity.engine.scene;

import com.badlogic.gdx.utils.Disposable;
import io.github.ldev22live.gdxunity.engine.core.profile.FrameProfiler;
import io.github.ldev22live.gdxunity.engine.scene.play.EntityLogicComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.ColliderGeometrySource;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.HeightFieldSource;
import io.github.ldev22live.gdxunity.engine.scene.navigation.NavigationSystem3D;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsSystem3D;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Manages an entity graph, lifecycle updates, and entity lookups.
 * Safe against concurrent mutations during scene updates (deferred additions and removals).
 */
public class Scene implements Disposable {
    private String name;
    private final Map<UUID, Entity> entitiesById = new LinkedHashMap<>();
    private final List<Entity> rootEntities = new ArrayList<>();
    private final List<Entity> unmodifiableRootEntities = Collections.unmodifiableList(rootEntities);

    // Deferred mutations during update loops
    private final List<Entity> pendingAdditions = new ArrayList<>();
    private final List<Entity> pendingRemovals = new ArrayList<>();
    private boolean updating = false;
    private boolean active = true;
    private GameMode gameMode;
    private String gameGenre;
    private boolean disposed = false;
    private PhysicsSystem3D physics3D;
    private ColliderGeometrySource colliderGeometry;
    private HeightFieldSource heightFields;
    /** Walkable areas, baked the first time an agent or a graph asks (roadmap M6.4); null until then. */
    private NavigationSystem3D navigation;
    /** Times each component type's updates while it records a frame (roadmap M9.6); null when not profiled. */
    private FrameProfiler profiler;
    private int profilerParent = FrameProfiler.ROOT;
    private final Map<Class<?>, Integer> componentSections = new IdentityHashMap<>();
    private final Map<String, Integer> logicSections = new HashMap<>();

    public Scene() {
        this("Untitled Scene");
    }

    public Scene(String name) {
        this.name = (name == null || name.isBlank()) ? "Untitled Scene" : name;
    }

    public String getName() {
        return name;
    }

    /**
     * Times this scene's component updates in {@code profiler} (roadmap M9.6): one section per component type, named after its
     * class, under {@code parentSection}; a script or graph gets one per script or graph
     * ({@link EntityLogicComponent#getProfilerLabel()}). The time a type's components take in one frame adds up in its
     * section. Only frames the profiler records are timed; {@code null} stops profiling.
     *
     * @param parentSection a section of {@code profiler}, or {@link FrameProfiler#ROOT}
     */
    public void setProfiler(FrameProfiler profiler, int parentSection) {
        if (profiler != null && parentSection != FrameProfiler.ROOT
            && (parentSection < 0 || parentSection >= profiler.getSectionCount())) {
            throw new IllegalArgumentException("No section " + parentSection + " in the profiler");
        }
        this.profiler = profiler;
        this.profilerParent = parentSection;
        componentSections.clear();
        logicSections.clear();
    }

    /** The profiler timing this scene's updates, or null. */
    public FrameProfiler getProfiler() {
        return profiler;
    }

    /** The profiler when it records the current frame, else null: a cheap check per entity. */
    FrameProfiler recordingProfiler() {
        return profiler != null && profiler.isInFrame() ? profiler : null;
    }

    /** The profiler section for a component: its script or graph, else its type; registered on its first update. */
    int profilerSection(Component component) {
        if (component instanceof EntityLogicComponent logic) {
            String label = logic.getProfilerLabel();
            Integer section = logicSections.get(label);
            if (section == null) {
                section = profiler.section(label, profilerParent);
                logicSections.put(label, section);
            }
            return section;
        }
        Class<?> type = component.getClass();
        Integer section = componentSections.get(type);
        if (section == null) {
            section = profiler.section(type.getSimpleName(), profilerParent);
            componentSections.put(type, section);
        }
        return section;
    }

    /**
     * The scene's running 3D physics, or null when none runs: before play, after it, and in scenes without 3D colliders
     * (those never load the native library). Scripts reach it through {@code ScriptComponent.getPhysics3D()} (TD-048).
     */
    public PhysicsSystem3D getPhysics3D() {
        return physics3D;
    }

    /**
     * Where mesh colliders and convex hull colliders read their triangles (TD-052), or null when nothing can read a mesh
     * (a headless scene). The renderer sets it when it shows the scene.
     */
    public ColliderGeometrySource getColliderGeometry() {
        return colliderGeometry;
    }

    public void setColliderGeometry(ColliderGeometrySource source) {
        this.colliderGeometry = source;
    }

    /**
     * Where terrain colliders read their heights (TD-087), or null when nothing can read a terrain (a headless scene). The renderer
     * sets it when it shows the scene.
     */
    public HeightFieldSource getHeightFields() {
        return heightFields;
    }

    public void setHeightFields(HeightFieldSource source) {
        this.heightFields = source;
    }

    /**
     * The scene's baked navigation, or null before anything asked for it. Use {@link NavigationSystem3D#of(Scene)}, which bakes it on
     * first use (roadmap M6.4).
     */
    public NavigationSystem3D getNavigation() {
        return navigation;
    }

    /** Keeps {@code navigation} as the scene's baked navigation; null forgets it, so the next use bakes again. */
    public void setNavigation(NavigationSystem3D navigation) {
        this.navigation = navigation;
    }

    /** Called by {@link PhysicsSystem3D}'s constructor; a scene runs one 3D physics system at a time. */
    public void attachPhysics3D(PhysicsSystem3D system) {
        Objects.requireNonNull(system, "system cannot be null");
        if (physics3D != null && physics3D != system) {
            throw new IllegalStateException("Scene " + name + " already has a 3D physics system; dispose it first");
        }
        physics3D = system;
    }

    /** Called by {@link PhysicsSystem3D#dispose()}; ignored when {@code system} is not the attached one. */
    public void detachPhysics3D(PhysicsSystem3D system) {
        if (physics3D == system) {
            physics3D = null;
        }
    }

    public void setName(String name) {
        this.name = (name == null || name.isBlank()) ? "Untitled Scene" : name;
    }

    /** The scene's game mode, or null for a scene that never had one chosen (the editor infers it from the content). */
    public GameMode getGameMode() {
        return gameMode;
    }

    /** Sets the game mode; null means "not chosen". */
    public void setGameMode(GameMode gameMode) {
        this.gameMode = gameMode;
    }

    /**
     * The id of the genre the scene is played as (TD-088), or null when none was chosen and Play runs the scene's own scripts only.
     * An id this version does not know is kept as it is, so a file from a newer version or the web is not changed by saving it.
     */
    public String getGameGenre() {
        return gameGenre;
    }

    /**
     * Sets the genre id; null or blank means "none".
     *
     * @throws IllegalArgumentException when the id has spaces or other characters a genre id cannot have
     */
    public void setGameGenre(String gameGenre) {
        if (gameGenre == null || gameGenre.isBlank()) {
            this.gameGenre = null;
            return;
        }
        if (!gameGenre.matches("[a-z0-9]+(-[a-z0-9]+)*")) {
            throw new IllegalArgumentException("A genre id is lower case words joined by hyphens, not '" + gameGenre + "'");
        }
        this.gameGenre = gameGenre;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        if (this.active != active) {
            this.active = active;
            for (Entity entity : entitiesById.values()) {
                entity.notifyActiveStateChanged();
            }
        }
    }

    /**
     * Creates and registers a new entity with a random UUID and the given name.
     */
    public Entity createEntity(String name) {
        Entity entity = new Entity(name);
        addEntity(entity);
        return entity;
    }

    /**
     * Registers an existing entity into this scene.
     * Rejects duplicate UUIDs or entities already belonging to another scene.
     */
    public void addEntity(Entity entity) {
        Objects.requireNonNull(entity, "entity cannot be null");
        checkNotDisposed();

        if (entity.getScene() != null && entity.getScene() != this) {
            throw new IllegalStateException("Entity " + entity.getName() + " already belongs to scene: " +
                entity.getScene().getName());
        }

        if (entitiesById.containsKey(entity.getId())) {
            throw new IllegalArgumentException("Entity with ID " + entity.getId() + " already exists in scene: " + name);
        }

        if (updating) {
            pendingAdditions.add(entity);
            return;
        }

        entitiesById.put(entity.getId(), entity);
        entity.setScene(this);

        if (entity.getTransform().getParent() == null) {
            rootEntities.add(entity);
        }
    }

    /**
     * Looks up an entity by its unique UUID.
     */
    public Entity findEntityById(UUID id) {
        if (id == null) return null;
        return entitiesById.get(id);
    }

    /**
     * Finds the first entity with the specified name.
     */
    public Entity findEntityByName(String name) {
        if (name == null) return null;
        for (Entity e : entitiesById.values()) {
            if (name.equals(e.getName())) {
                return e;
            }
        }
        return null;
    }

    /**
     * Removes an entity from this scene.
     * By default, recursively removes all child entities.
     *
     * @param entity the entity to remove
     * @return true if entity was found and removed, false otherwise
     */
    public boolean removeEntity(Entity entity) {
        return removeEntity(entity, true);
    }

    /**
     * Removes an entity from this scene, with option to remove or unparent children.
     *
     * @param entity          entity to remove
     * @param removeChildren  if true, removes all child entities recursively;
     *                        if false, unparents children to scene root.
     */
    public boolean removeEntity(Entity entity, boolean removeChildren) {
        if (entity == null || !entitiesById.containsKey(entity.getId())) {
            return false;
        }
        checkNotDisposed();

        if (updating) {
            pendingRemovals.add(entity);
            return true;
        }

        return doRemoveEntity(entity, removeChildren);
    }

    private boolean doRemoveEntity(Entity entity, boolean removeChildren) {
        // Handle child hierarchy
        List<Transform> children = new ArrayList<>(entity.getTransform().getChildren());
        for (Transform childTransform : children) {
            if (removeChildren) {
                doRemoveEntity(childTransform.getEntity(), true);
            } else {
                childTransform.setParent(null);
            }
        }

        // Detach from parent if it has one
        if (entity.getTransform().getParent() != null) {
            entity.getTransform().setParent(null);
        }

        rootEntities.remove(entity);
        entitiesById.remove(entity.getId());
        entity.setScene(null);

        // Detach all components
        List<Component> components = new ArrayList<>(entity.getAllComponents());
        for (Component c : components) {
            entity.removeComponent(c);
        }

        return true;
    }

    void notifyEntityBecameRoot(Entity entity) {
        if (entity != null && !rootEntities.contains(entity) && entitiesById.containsKey(entity.getId())) {
            rootEntities.add(entity);
        }
    }

    void notifyEntityNoLongerRoot(Entity entity) {
        if (entity != null) {
            rootEntities.remove(entity);
        }
    }

    public List<Entity> getRootEntities() {
        return unmodifiableRootEntities;
    }

    public List<Entity> getAllEntities() {
        return Collections.unmodifiableList(new ArrayList<>(entitiesById.values()));
    }

    public int getEntityCount() {
        return entitiesById.size();
    }

    /**
     * Updates all active entities and their components.
     * Safely flushes pending additions and removals outside of collection iteration.
     *
     * @param deltaTime frame delta time in seconds
     */
    public void update(float deltaTime) {
        if (!active || disposed) {
            return;
        }

        updating = true;
        try {
            // Iterate over a snapshot list of current entities to prevent ConcurrentModificationException
            List<Entity> snapshot = new ArrayList<>(entitiesById.values());
            for (int i = 0; i < snapshot.size(); i++) {
                Entity entity = snapshot.get(i);
                if (entitiesById.containsKey(entity.getId())) { // check not removed earlier in this frame
                    entity.update(deltaTime);
                }
            }
        } finally {
            updating = false;
        }

        // Flush deferred mutations
        if (!pendingRemovals.isEmpty()) {
            for (int i = 0; i < pendingRemovals.size(); i++) {
                doRemoveEntity(pendingRemovals.get(i), true);
            }
            pendingRemovals.clear();
        }

        if (!pendingAdditions.isEmpty()) {
            for (int i = 0; i < pendingAdditions.size(); i++) {
                addEntity(pendingAdditions.get(i));
            }
            pendingAdditions.clear();
        }
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;

        List<Entity> all = new ArrayList<>(entitiesById.values());
        for (Entity e : all) {
            doRemoveEntity(e, true);
        }
        entitiesById.clear();
        rootEntities.clear();
        pendingAdditions.clear();
        pendingRemovals.clear();
    }

    public boolean isDisposed() {
        return disposed;
    }

    private void checkNotDisposed() {
        if (disposed) {
            throw new IllegalStateException("Scene " + name + " is disposed.");
        }
    }
}
