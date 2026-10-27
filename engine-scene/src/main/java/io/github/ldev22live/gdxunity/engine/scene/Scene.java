package io.github.ldev22live.gdxunity.engine.scene;

import com.badlogic.gdx.utils.Disposable;

import java.util.ArrayList;
import java.util.Collections;
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
    private boolean disposed = false;

    public Scene() {
        this("Untitled Scene");
    }

    public Scene(String name) {
        this.name = (name == null || name.isBlank()) ? "Untitled Scene" : name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = (name == null || name.isBlank()) ? "Untitled Scene" : name;
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
