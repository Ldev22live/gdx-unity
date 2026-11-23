package io.github.ldev22live.gdxunity.editor.terrain;

import io.github.ldev22live.gdxunity.engine.render.components.TerrainComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.terrain.Terrain;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainBrush;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainLibrary;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * What terrain painting needs to remember between strokes (TD-082): whether paint mode is on, the brush, and the project's terrains.
 * One per editor, shared by the Details panel and the viewport.
 */
public final class TerrainSession {
    private final TerrainLibrary library;
    private final TerrainBrush brush = new TerrainBrush();
    private boolean paintMode;
    private Supplier<Entity> levelTarget;

    public TerrainSession(TerrainLibrary library) {
        this.library = Objects.requireNonNull(library, "library cannot be null");
    }

    public TerrainLibrary getLibrary() {
        return library;
    }

    /** The brush strokes are made with; changing it affects the next stroke. */
    public TerrainBrush getBrush() {
        return brush;
    }

    /** True while a left-button drag on the selected terrain paints instead of doing what it normally does. */
    public boolean isPaintMode() {
        return paintMode;
    }

    public void setPaintMode(boolean paintMode) {
        this.paintMode = paintMode;
    }

    /**
     * While the level editor is on, strokes go to this entity's terrain whatever is selected, so placing an object (which selects it)
     * does not lose the ground. Null turns that off and strokes go to the selected entity again.
     */
    public void setLevelTarget(Supplier<Entity> levelTarget) {
        this.levelTarget = levelTarget;
    }

    /** The entity a stroke goes to: the level's ground when the level editor is on and has one, otherwise the selected entity. */
    public Entity strokeTarget(Entity selected) {
        Entity level = levelTarget == null ? null : levelTarget.get();
        return level != null ? level : selected;
    }

    /** The terrain the entity draws, or null when it has no Terrain component or the file cannot be read. */
    public Terrain getTerrainOf(Entity entity) {
        TerrainComponent component = componentOf(entity);
        if (component == null || component.getTerrainPath().isEmpty()) {
            return null;
        }
        return library.get(component.getTerrainPath());
    }

    /** The entity's Terrain component, or null. */
    public static TerrainComponent componentOf(Entity entity) {
        return entity == null ? null : entity.getComponent(TerrainComponent.class);
    }
}
