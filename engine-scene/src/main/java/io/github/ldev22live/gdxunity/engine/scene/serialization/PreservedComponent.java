package io.github.ldev22live.gdxunity.engine.scene.serialization;

import io.github.ldev22live.gdxunity.engine.scene.BaseComponent;

import java.util.Objects;

/**
 * A component whose type this version of the engine does not know (another client's, or a newer version's), kept exactly as it
 * was read so that saving writes it back unchanged (TD-106, roadmap A12). It does nothing at run time, is never edited, and is
 * copied with its entity (duplication, prefabs, undo). The editor shows it as "Not editable here".
 */
public final class PreservedComponent extends BaseComponent {
    private final String typeId;
    private final String rawData;

    /**
     * @param typeId  the {@code type} the file gave
     * @param rawData the compact JSON text of its {@code data} member, or null when the entry had no {@code data}
     */
    public PreservedComponent(String typeId, String rawData) {
        this.typeId = Objects.requireNonNull(typeId, "typeId cannot be null");
        if (typeId.isBlank()) {
            throw new IllegalArgumentException("typeId cannot be blank");
        }
        this.rawData = rawData;
    }

    /** The component type id as written in the file. */
    public String getTypeId() {
        return typeId;
    }

    /** The compact JSON text of the {@code data} member, or null when the file had none. */
    public String getRawData() {
        return rawData;
    }

    @Override
    public String toString() {
        return "PreservedComponent[" + typeId + "]";
    }
}
