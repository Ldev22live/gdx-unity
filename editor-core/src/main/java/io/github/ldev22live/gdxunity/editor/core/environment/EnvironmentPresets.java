package io.github.ldev22live.gdxunity.editor.core.environment;

import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.command.CreateEntityCommand;
import io.github.ldev22live.gdxunity.editor.core.command.EditorCommand;
import io.github.ldev22live.gdxunity.editor.core.command.ModifyComponentPropertyCommand;
import io.github.ldev22live.gdxunity.engine.scene.Component;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.environment.AtmosphereComponent;
import io.github.ldev22live.gdxunity.engine.scene.environment.AtmospherePreset;
import io.github.ldev22live.gdxunity.engine.scene.environment.PostProcessPreset;
import io.github.ldev22live.gdxunity.engine.scene.environment.PostProcessVolumeComponent;
import io.github.ldev22live.gdxunity.engine.scene.environment.WaterBodyComponent;
import io.github.ldev22live.gdxunity.engine.scene.environment.WaterPreset;

import java.util.List;
import java.util.Objects;

/**
 * Applying sky, water and post-processing presets as undoable steps (roadmap M3.9, TD-090; M6.2, TD-091). The Details panel, the
 * level palette and the {@code environment_preset} agent tool all go through here, so each is one undo step that restores every
 * setting.
 */
public final class EnvironmentPresets {
    /** Name of the entity made for a scene's first Sky and Weather. */
    public static final String SKY_ENTITY_NAME = "Sky and Weather";
    /** Name of an entity made for a new water body. */
    public static final String WATER_ENTITY_NAME = "Water";
    /** Name of the entity made for a scene's first global Post Process Volume (roadmap M6.2). */
    public static final String POST_PROCESS_ENTITY_NAME = "Post Process Volume";

    private EnvironmentPresets() {}

    /** The entity holding {@code scene}'s Sky and Weather (the first enabled one, else the first), or null. */
    public static Entity findSkyEntity(Scene scene) {
        if (scene == null) {
            return null;
        }
        Entity disabled = null;
        for (Entity entity : scene.getAllEntities()) {
            AtmosphereComponent sky = entity.getComponent(AtmosphereComponent.class);
            if (sky != null) {
                if (sky.isEnabled() && entity.isActiveInHierarchy()) {
                    return entity;
                }
                if (disabled == null) {
                    disabled = entity;
                }
            }
        }
        return disabled;
    }

    /** Which preset the scene's sky holds, its display name, "Custom", or "None" when the scene has no Sky and Weather. */
    public static String describeSky(Scene scene) {
        Entity entity = findSkyEntity(scene);
        if (entity == null) {
            return "None";
        }
        return AtmospherePreset.matching(entity.getComponent(AtmosphereComponent.class))
            .map(AtmospherePreset::getDisplayName).orElse("Custom");
    }

    /**
     * One undo step that gives the active scene the {@code preset} sky: the existing Sky and Weather takes every setting
     * (and is enabled), or, when the scene has none, a "Sky and Weather" entity is made with it.
     *
     * @throws IllegalStateException if there is no active scene
     */
    public static EditorCommand applySky(EditorState editorState, AtmospherePreset preset) {
        Objects.requireNonNull(editorState, "editorState cannot be null");
        Objects.requireNonNull(preset, "preset cannot be null");
        Scene scene = editorState.getActiveScene();
        if (scene == null) {
            throw new IllegalStateException("No scene is open");
        }
        Entity entity = findSkyEntity(scene);
        if (entity == null) {
            AtmosphereComponent sky = new AtmosphereComponent();
            preset.applyTo(sky);
            return new CreateEntityCommand(editorState, SKY_ENTITY_NAME, new Vector3(), List.<Component>of(sky));
        }
        AtmosphereComponent sky = entity.getComponent(AtmosphereComponent.class);
        AtmosphereComponent after = sky.copy();
        preset.applyTo(after);
        after.setEnabled(true);
        return new ModifyComponentPropertyCommand<AtmosphereComponent, AtmosphereComponent>(editorState, entity.getId(), sky,
            sky.copy(), after, sky::set, "Sky: " + preset.getDisplayName());
    }

    /** One undo step that gives {@code water} on {@code entity} the look of {@code preset}; its size is kept. */
    public static EditorCommand applyWater(EditorState editorState, Entity entity, WaterPreset preset) {
        Objects.requireNonNull(editorState, "editorState cannot be null");
        Objects.requireNonNull(entity, "entity cannot be null");
        Objects.requireNonNull(preset, "preset cannot be null");
        WaterBodyComponent water = entity.getComponent(WaterBodyComponent.class);
        if (water == null) {
            throw new IllegalArgumentException(entity.getName() + " has no Water Body");
        }
        WaterBodyComponent after = water.copy();
        preset.applyTo(after);
        return new ModifyComponentPropertyCommand<WaterBodyComponent, WaterBodyComponent>(editorState, entity.getId(), water,
            water.copy(), after, water::set, "Water: " + preset.getDisplayName());
    }

    /** One undo step that makes a "Water" entity at {@code position} with a water body of {@code preset}'s look. */
    public static EditorCommand addWater(EditorState editorState, Vector3 position, WaterPreset preset) {
        Objects.requireNonNull(editorState, "editorState cannot be null");
        Objects.requireNonNull(position, "position cannot be null");
        Objects.requireNonNull(preset, "preset cannot be null");
        WaterBodyComponent water = new WaterBodyComponent();
        preset.applyTo(water);
        return new CreateEntityCommand(editorState, WATER_ENTITY_NAME, position, List.<Component>of(water));
    }

    /** The entity holding {@code scene}'s global Post Process Volume (the first enabled one, else the first), or null. */
    public static Entity findGlobalPostProcessEntity(Scene scene) {
        if (scene == null) {
            return null;
        }
        Entity disabled = null;
        for (Entity entity : scene.getAllEntities()) {
            PostProcessVolumeComponent volume = entity.getComponent(PostProcessVolumeComponent.class);
            if (volume != null && volume.isGlobal()) {
                if (volume.isEnabled() && entity.isActiveInHierarchy()) {
                    return entity;
                }
                if (disabled == null) {
                    disabled = entity;
                }
            }
        }
        return disabled;
    }

    /** Which preset the scene's global Post Process Volume holds, its display name, "Custom", or "None" when there is none. */
    public static String describePostProcess(Scene scene) {
        Entity entity = findGlobalPostProcessEntity(scene);
        if (entity == null) {
            return "None";
        }
        return PostProcessPreset.matching(entity.getComponent(PostProcessVolumeComponent.class))
            .map(PostProcessPreset::getDisplayName).orElse("Custom");
    }

    /**
     * One undo step that gives the whole active scene the {@code preset} look: the global Post Process Volume takes the preset's
     * effects (and is enabled), or, when the scene has none, a "Post Process Volume" entity is made with it.
     *
     * @throws IllegalStateException if there is no active scene
     */
    public static EditorCommand applyScenePostProcess(EditorState editorState, PostProcessPreset preset) {
        Objects.requireNonNull(editorState, "editorState cannot be null");
        Objects.requireNonNull(preset, "preset cannot be null");
        Scene scene = editorState.getActiveScene();
        if (scene == null) {
            throw new IllegalStateException("No scene is open");
        }
        Entity entity = findGlobalPostProcessEntity(scene);
        if (entity == null) {
            PostProcessVolumeComponent volume = new PostProcessVolumeComponent();
            preset.applyTo(volume);
            return new CreateEntityCommand(editorState, POST_PROCESS_ENTITY_NAME, new Vector3(), List.<Component>of(volume));
        }
        return applyPostProcess(editorState, entity, preset, true);
    }

    /** One undo step that gives the Post Process Volume on {@code entity} the effects of {@code preset}; its place and reach stay. */
    public static EditorCommand applyPostProcess(EditorState editorState, Entity entity, PostProcessPreset preset) {
        return applyPostProcess(editorState, entity, preset, false);
    }

    private static EditorCommand applyPostProcess(EditorState editorState, Entity entity, PostProcessPreset preset, boolean enable) {
        Objects.requireNonNull(editorState, "editorState cannot be null");
        Objects.requireNonNull(entity, "entity cannot be null");
        Objects.requireNonNull(preset, "preset cannot be null");
        PostProcessVolumeComponent volume = entity.getComponent(PostProcessVolumeComponent.class);
        if (volume == null) {
            throw new IllegalArgumentException(entity.getName() + " has no Post Process Volume");
        }
        PostProcessVolumeComponent after = volume.copy();
        preset.applyTo(after);
        if (enable) {
            after.setEnabled(true);
        }
        return new ModifyComponentPropertyCommand<PostProcessVolumeComponent, PostProcessVolumeComponent>(editorState, entity.getId(),
            volume, volume.copy(), after, volume::set, "Post Process: " + preset.getDisplayName());
    }
}
