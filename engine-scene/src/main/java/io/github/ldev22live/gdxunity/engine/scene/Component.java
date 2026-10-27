package io.github.ldev22live.gdxunity.engine.scene;

/**
 * Base interface for all components that can be attached to an {@link Entity}.
 * Provides standard lifecycle hooks: attachment, start, update, enabled changes, and detachment.
 */
public interface Component {
    /**
     * Returns the entity owning this component, or null if detached.
     */
    Entity getEntity();

    /**
     * Checks if this component is currently enabled.
     */
    boolean isEnabled();

    /**
     * Enables or disables this component.
     */
    void setEnabled(boolean enabled);

    /**
     * Called when the component is attached to an entity.
     * Internal framework hook.
     */
    void onAttach(Entity entity);

    /**
     * Called before the component's first update, or when the entity becomes active.
     */
    void onStart();

    /**
     * Called each frame during scene update if both entity and component are active/enabled.
     *
     * @param deltaTime clamped frame delta time in seconds
     */
    void onUpdate(float deltaTime);

    /**
     * Called when the component or entity is activated.
     */
    void onEnable();

    /**
     * Called when the component or entity is deactivated.
     */
    void onDisable();

    /**
     * Called when the component is removed from an entity, or the entity is destroyed.
     */
    void onDetach();
}
