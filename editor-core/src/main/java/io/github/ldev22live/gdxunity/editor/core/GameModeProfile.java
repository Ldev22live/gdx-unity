package io.github.ldev22live.gdxunity.editor.core;

import io.github.ldev22live.gdxunity.engine.render.components.DynamicMeshComponent;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.CharacterController3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.Collider3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.RigidBody3DComponent;

import java.util.Objects;
import java.util.Set;

/**
 * What the editor does differently for each {@link GameMode} (TD-067): the scene view, whether the view may orbit, and which component
 * categories it offers. Pure data and rules; the panels read it.
 */
public final class GameModeProfile {
    private static final GameModeProfile PROFILE_2D = new GameModeProfile(GameMode.MODE_2D, ViewportMode.VIEW_2D, false,
        Set.of("Physics 3D", "Rendering 3D", "Lighting"),
        "Flat view of the XY plane. Sprites, tile maps and Box2D physics; 3D components are hidden.");
    private static final GameModeProfile PROFILE_2_5D = new GameModeProfile(GameMode.MODE_2_5D, ViewportMode.VIEW_3D, true,
        Set.of("Physics 2D"),
        "A 3D world seen from the side: the view pans and zooms but does not orbit. 3D physics, models and sprites.");
    private static final GameModeProfile PROFILE_3D = new GameModeProfile(GameMode.MODE_3D, ViewportMode.VIEW_3D, false,
        Set.of("Physics 2D"),
        "Free 3D navigation with 3D physics, models and lighting.");

    private final GameMode mode;
    private final ViewportMode viewport;
    private final boolean sideViewLocked;
    private final Set<String> hiddenCategories;
    private final String description;

    private GameModeProfile(GameMode mode, ViewportMode viewport, boolean sideViewLocked, Set<String> hiddenCategories, String description) {
        this.mode = mode;
        this.viewport = viewport;
        this.sideViewLocked = sideViewLocked;
        this.hiddenCategories = hiddenCategories;
        this.description = description;
    }

    /** The profile of a mode. */
    public static GameModeProfile of(GameMode mode) {
        return switch (Objects.requireNonNull(mode, "mode cannot be null")) {
            case MODE_2D -> PROFILE_2D;
            case MODE_2_5D -> PROFILE_2_5D;
            case MODE_3D -> PROFILE_3D;
        };
    }

    /**
     * The mode of a scene: the one it was saved with, else 3D when it holds any 3D component (model, mesh, 3D body, collider or character), else 2D.
     * Scenes from before game modes existed have no saved mode, which is why this is inferred.
     */
    public static GameMode resolve(Scene scene) {
        Objects.requireNonNull(scene, "scene cannot be null");
        if (scene.getGameMode() != null) {
            return scene.getGameMode();
        }
        for (Entity entity : scene.getAllEntities()) {
            if (entity.getComponent(ModelComponent.class) != null || entity.getComponent(MeshPrimitiveComponent.class) != null
                || entity.getComponent(DynamicMeshComponent.class) != null || entity.getComponent(RigidBody3DComponent.class) != null
                || entity.getComponent(CharacterController3DComponent.class) != null || !entity.getComponents(Collider3DComponent.class).isEmpty()) {
                return GameMode.MODE_3D;
            }
        }
        return GameMode.MODE_2D;
    }

    public GameMode getMode() {
        return mode;
    }

    /** The scene view the mode uses. */
    public ViewportMode getViewport() {
        return viewport;
    }

    /** Whether the 3D view is fixed to a side-on look that only pans and zooms. */
    public boolean isSideViewLocked() {
        return sideViewLocked;
    }

    /** One sentence for the mode screen. */
    public String getDescription() {
        return description;
    }

    /** Whether components of this catalog category are offered in this mode. */
    public boolean offersCategory(String category) {
        return !hiddenCategories.contains(Objects.requireNonNull(category, "category cannot be null"));
    }
}
