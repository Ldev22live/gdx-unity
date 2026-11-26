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

    /**
     * Members of this component's {@code data} object that its serializer did not write back when it was read: fields from another
     * client or a newer version. The scene serializer writes them after the serializer's own fields, except any the serializer
     * writes itself, so an edited known field always wins (TD-106).
     */
    public PreservedFields getPreservedData() {
        return preservedData;
    }

    /** Sets the preserved {@code data} members (see {@link #getPreservedData()}); used by the scene serializer. */
    public void setPreservedData(PreservedFields preservedData) {
        this.preservedData = Objects.requireNonNull(preservedData, "preservedData cannot be null");
    }

    /** Members of this component's entry besides {@code type}, {@code enabled} and {@code data}, kept for saving (TD-106). */
    public PreservedFields getPreservedEntry() {
        return preservedEntry;
    }

    /** Sets the preserved entry members (see {@link #getPreservedEntry()}); used by the scene serializer. */
    public void setPreservedEntry(PreservedFields preservedEntry) {
        this.preservedEntry = Objects.requireNonNull(preservedEntry, "preservedEntry cannot be null");
    }

    private PreservedFields preservedData = PreservedFields.EMPTY;
    private PreservedFields preservedEntry = PreservedFields.EMPTY;
}
