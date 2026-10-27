package io.github.ldev22live.gdxunity.engine.scene.scripting;

import io.github.ldev22live.gdxunity.engine.scene.BaseComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.Transform;

/**
 * Shared base class for user scripts authored in Java or Kotlin.
 *
 * Provides lifecycle callbacks:
 * - {@link #onCreate()}: Invoked once upon instantiation/attachment.
 * - {@link #onStart()}: Invoked once when simulation starts or entity becomes active.
 * - {@link #onUpdate(float)}: Invoked each frame during active Play Mode.
 * - {@link #onFixedUpdate(float)}: Invoked for fixed-step physics/simulation loops if supported.
 * - {@link #onEnable()}: Invoked whenever script component or entity is enabled.
 * - {@link #onDisable()}: Invoked whenever script component or entity is disabled.
 * - {@link #onDestroy()}: Invoked when component is removed or scene/entity is destroyed.
 */
public abstract class ScriptComponent extends BaseComponent {

    /**
     * Called once when the script is attached and initialized.
     */
    public void onCreate() {
        // Optional override
    }

    /**
     * Called once before the script's first update frame during Play Mode.
     */
    @Override
    public void onStart() {
        super.onStart();
    }

    /**
     * Called each frame during Play Mode if both entity and component are active and enabled.
     *
     * @param deltaTime frame delta time in seconds
     */
    @Override
    public void onUpdate(float deltaTime) {
        // Optional override
    }

    /**
     * Optional fixed update hook for physics and deterministic simulation steps.
     *
     * @param fixedDeltaTime fixed timestep in seconds
     */
    public void onFixedUpdate(float fixedDeltaTime) {
        // Optional override
    }

    /**
     * Called when the script or entity becomes enabled.
     */
    @Override
    public void onEnable() {
        super.onEnable();
    }

    /**
     * Called when the script or entity becomes disabled.
     */
    @Override
    public void onDisable() {
        super.onDisable();
    }

    /**
     * Called when the script instance is being removed or destroyed.
     */
    public void onDestroy() {
        // Optional override
    }

    @Override
    public void onDetach() {
        try {
            onDestroy();
        } finally {
            super.onDetach();
        }
    }

    // --- Convenience Accessors for User Scripts ---

    public Scene getScene() {
        Entity entity = getEntity();
        return entity != null ? entity.getScene() : null;
    }

    public Transform getTransform() {
        Entity entity = getEntity();
        return entity != null ? entity.getTransform() : null;
    }

    public <T extends io.github.ldev22live.gdxunity.engine.scene.Component> T getComponent(Class<T> componentClass) {
        Entity entity = getEntity();
        return entity != null ? entity.getComponent(componentClass) : null;
    }
}
