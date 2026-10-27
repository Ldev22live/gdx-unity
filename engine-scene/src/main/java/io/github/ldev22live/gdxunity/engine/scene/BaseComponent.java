package io.github.ldev22live.gdxunity.engine.scene;

import java.util.Objects;

/**
 * Base implementation of {@link Component} providing safe state management and ownership checks.
 */
public abstract class BaseComponent implements Component {
    private Entity entity;
    private boolean enabled = true;
    private boolean started = false;

    @Override
    public Entity getEntity() {
        return entity;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            this.enabled = enabled;
            if (entity != null && entity.isActiveInHierarchy()) {
                if (this.enabled) {
                    onEnable();
                } else {
                    onDisable();
                }
            }
        }
    }

    @Override
    public void onAttach(Entity entity) {
        if (this.entity != null && this.entity != entity) {
            throw new IllegalStateException("Component is already attached to entity: " + this.entity.getName());
        }
        this.entity = Objects.requireNonNull(entity, "entity cannot be null");
    }

    @Override
    public void onStart() {
        this.started = true;
    }

    @Override
    public void onUpdate(float deltaTime) {
        // Optional override
    }

    @Override
    public void onEnable() {
        // Optional override
    }

    @Override
    public void onDisable() {
        // Optional override
    }

    @Override
    public void onDetach() {
        this.entity = null;
        this.started = false;
    }

    public boolean isStarted() {
        return started;
    }
}
