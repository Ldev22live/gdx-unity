package io.github.ldev22live.gdxunity.engine.scene;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a game object in a scene with a persistent UUID, a display name, an intrinsic {@link Transform},
 * and attached {@link Component}s.
 */
public final class Entity {
    private final UUID id;
    private String name;
    private boolean activeSelf = true;
    private final Transform transform;
    private Scene scene;

    // Component storage: Class -> List of Components (allowing multiple components or 1 per type)
    private final List<Component> componentList = new ArrayList<>();
    private final List<Component> unmodifiableComponentList = Collections.unmodifiableList(componentList);
    private final Map<Class<? extends Component>, List<Component>> componentsByType = new LinkedHashMap<>();

    public Entity() {
        this("Entity");
    }

    public Entity(String name) {
        this(UUID.randomUUID(), name);
    }

    public Entity(UUID id, String name) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.name = (name == null || name.isBlank()) ? "Entity" : name;
        this.transform = new Transform(this);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = (name == null || name.isBlank()) ? "Entity" : name;
    }

    public Transform getTransform() {
        return transform;
    }

    public Scene getScene() {
        return scene;
    }

    void setScene(Scene scene) {
        this.scene = scene;
    }

    // --- Active state ---

    public boolean isActiveSelf() {
        return activeSelf;
    }

    public void setActive(boolean active) {
        if (this.activeSelf != active) {
            this.activeSelf = active;
            notifyActiveStateChanged();
        }
    }

    /**
     * An entity is active in hierarchy only if it is active itself AND all parent entities are active.
     */
    public boolean isActiveInHierarchy() {
        if (!activeSelf) {
            return false;
        }
        Transform parentTransform = transform.getParent();
        while (parentTransform != null) {
            if (!parentTransform.getEntity().isActiveSelf()) {
                return false;
            }
            parentTransform = parentTransform.getParent();
        }
        return true;
    }

    void notifyActiveStateChanged() {
        boolean active = isActiveInHierarchy();
        for (int i = 0; i < componentList.size(); i++) {
            Component c = componentList.get(i);
            if (c.isEnabled()) {
                if (active) {
                    c.onEnable();
                } else {
                    c.onDisable();
                }
            }
        }
        // Propagate to all child entities
        List<Transform> children = transform.getChildren();
        for (int i = 0; i < children.size(); i++) {
            children.get(i).getEntity().notifyActiveStateChanged();
        }
    }

    // --- Component management ---

    /**
     * Adds a component to this entity.
     *
     * @param component component to add
     * @param <T>       component type
     * @return the added component
     */
    public <T extends Component> T addComponent(T component) {
        Objects.requireNonNull(component, "component cannot be null");
        if (component.getEntity() != null && component.getEntity() != this) {
            throw new IllegalStateException("Component is already attached to another entity: " +
                component.getEntity().getName());
        }

        componentList.add(component);
        registerComponentType(component.getClass(), component);
        component.onAttach(this);

        if (isActiveInHierarchy() && component.isEnabled()) {
            component.onEnable();
        }

        return component;
    }

    @SuppressWarnings("unchecked")
    private void registerComponentType(Class<?> clazz, Component component) {
        if (clazz == null || !Component.class.isAssignableFrom(clazz) || clazz == Component.class) {
            return;
        }
        Class<? extends Component> compClass = (Class<? extends Component>) clazz;
        componentsByType.computeIfAbsent(compClass, k -> new ArrayList<>()).add(component);

        // Also register under implemented interfaces and superclasses up to Component
        for (Class<?> iface : clazz.getInterfaces()) {
            if (Component.class.isAssignableFrom(iface)) {
                registerComponentType(iface, component);
            }
        }
        if (clazz.getSuperclass() != null && Component.class.isAssignableFrom(clazz.getSuperclass())) {
            registerComponentType(clazz.getSuperclass(), component);
        }
    }

    @SuppressWarnings("unchecked")
    private void unregisterComponentType(Class<?> clazz, Component component) {
        if (clazz == null || !Component.class.isAssignableFrom(clazz) || clazz == Component.class) {
            return;
        }
        Class<? extends Component> compClass = (Class<? extends Component>) clazz;
        List<Component> list = componentsByType.get(compClass);
        if (list != null) {
            list.remove(component);
            if (list.isEmpty()) {
                componentsByType.remove(compClass);
            }
        }
        for (Class<?> iface : clazz.getInterfaces()) {
            if (Component.class.isAssignableFrom(iface)) {
                unregisterComponentType(iface, component);
            }
        }
        if (clazz.getSuperclass() != null && Component.class.isAssignableFrom(clazz.getSuperclass())) {
            unregisterComponentType(clazz.getSuperclass(), component);
        }
    }

    /**
     * Returns the first component matching the specified type, or null if none found.
     */
    @SuppressWarnings("unchecked")
    public <T extends Component> T getComponent(Class<T> type) {
        Objects.requireNonNull(type, "type cannot be null");
        List<Component> list = componentsByType.get(type);
        if (list != null && !list.isEmpty()) {
            return (T) list.get(0);
        }
        return null;
    }

    /**
     * Returns all components matching the specified type.
     */
    @SuppressWarnings("unchecked")
    public <T extends Component> List<T> getComponents(Class<T> type) {
        Objects.requireNonNull(type, "type cannot be null");
        List<Component> list = componentsByType.get(type);
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        List<T> result = new ArrayList<>(list.size());
        for (Component c : list) {
            result.add((T) c);
        }
        return Collections.unmodifiableList(result);
    }

    public List<Component> getAllComponents() {
        return unmodifiableComponentList;
    }

    /**
     * Removes a component from this entity.
     */
    public boolean removeComponent(Component component) {
        if (component == null || !componentList.contains(component)) {
            return false;
        }

        if (isActiveInHierarchy() && component.isEnabled()) {
            try {
                component.onDisable();
            } catch (Throwable t) {
                // Keep removing despite callback error
            }
        }

        componentList.remove(component);
        unregisterComponentType(component.getClass(), component);

        try {
            component.onDetach();
        } catch (Throwable t) {
            // Keep state clean
        }

        return true;
    }

    // --- Frame update hook called by Scene ---

    void update(float deltaTime) {
        if (!isActiveInHierarchy()) {
            return;
        }

        // Use indexed loop over snapshot copy in case components are added/removed during update
        for (int i = 0; i < componentList.size(); i++) {
            Component c = componentList.get(i);
            if (c.isEnabled()) {
                if (c instanceof BaseComponent baseComp) {
                    if (!baseComp.isStarted()) {
                        baseComp.onStart();
                    }
                }
                c.onUpdate(deltaTime);
            }
        }
    }

    @Override
    public String toString() {
        return "Entity{" +
            "id=" + id +
            ", name='" + name + '\'' +
            ", active=" + activeSelf +
            '}';
    }
}
