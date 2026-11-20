package io.github.ldev22live.gdxunity.editor.core.asset;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.GameModeProfile;
import io.github.ldev22live.gdxunity.editor.core.command.CreateEntityCommand;
import io.github.ldev22live.gdxunity.editor.core.prefab.PrefabService;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetService;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetType;
import io.github.ldev22live.gdxunity.engine.render.components.DynamicMeshComponent;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;
import io.github.ldev22live.gdxunity.engine.render.components.SpriteAnimatorComponent;
import io.github.ldev22live.gdxunity.engine.render.components.SpriteRendererComponent;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.audio.AudioSourceComponent;

import java.util.List;
import java.util.Objects;

/**
 * Places an asset dragged from the Content Browser into the scene: textures become sprite entities at the
 * drop point, created as one undoable command; prefabs are placed as linked instances. Other asset types are reported as not placeable yet.
 */
public final class AssetPlacement {

    /** Edge length of a sprite created from a dropped texture, in world units. */
    public static final float DROPPED_SPRITE_SIZE = 100f;

    /**
     * Roadmap M3c.8: in a 2.5D or 3D scene a dropped sprite is sized so its longer side is {@link #DROPPED_SPRITE_SIZE} world units,
     * keeping its shape; at its pixel size (16 units for a 16-pixel coin) it was all but invisible beside an 80-unit character. A 2D
     * scene keeps the pixel size.
     *
     * @return a note for the status bar when the size could not be set, otherwise null
     */
    static String sizeForWorld(EditorState editorState, AssetReference asset, SpriteAnimatorComponent animator) {
        Scene scene = editorState.getActiveScene();
        if (scene == null || GameModeProfile.resolve(scene) == GameMode.MODE_2D) {
            return null;
        }
        AssetService assets = editorState.getAssetService();
        if (assets == null) {
            return "Placed " + asset.getFileName() + " at its pixel size: this editor cannot read the sprite to size it";
        }
        int width;
        int height;
        try {
            FileHandle file = assets.resolve(asset);
            JsonValue header = new JsonReader().parse(file.readString("UTF-8"));
            width = header.getInt("width", 0);
            height = header.getInt("height", 0);
        } catch (RuntimeException e) {
            return "Placed " + asset.getFileName() + " at its pixel size: cannot read it (" + e.getMessage() + ")";
        }
        if (width <= 0 || height <= 0) {
            return "Placed " + asset.getFileName() + " at its pixel size: it names no width and height";
        }
        float scale = DROPPED_SPRITE_SIZE / Math.max(width, height);
        animator.setWidth(width * scale);
        animator.setHeight(height * scale);
        return null;
    }

    public enum Result {
        PLACED,
        UNSUPPORTED_TYPE,
        REJECTED
    }

    private AssetPlacement() {}

    /**
     * @param position world position of the new entity
     * @return what happened; the status bar explains anything other than {@link Result#PLACED}
     */
    public static Result place(EditorState editorState, AssetReference asset, Vector3 position) {
        Objects.requireNonNull(editorState, "editorState cannot be null");
        Objects.requireNonNull(asset, "asset cannot be null");
        Objects.requireNonNull(position, "position cannot be null");

        AssetType type = AssetType.fromPath(asset.getPath());
        switch (type) {
            case TEXTURE -> {
                String name = baseName(asset.getFileName());
                SpriteRendererComponent sprite = new SpriteRendererComponent(asset, DROPPED_SPRITE_SIZE, DROPPED_SPRITE_SIZE);
                boolean created = editorState.executeCommand(new CreateEntityCommand(editorState, name, position, List.of(sprite)));
                return created ? Result.PLACED : Result.REJECTED;
            }
            case PREFAB -> {
                PrefabService prefabs = editorState.getPrefabService();
                if (prefabs == null) {
                    editorState.setStatusMessage("Cannot place " + asset.getFileName() + ": prefabs need an asset folder", true);
                    return Result.UNSUPPORTED_TYPE;
                }
                return prefabs.instantiate(asset, position) != null ? Result.PLACED : Result.REJECTED;
            }
            case MODEL -> {
                String name = baseName(asset.getFileName());
                ModelComponent model = new ModelComponent(asset.getPath());
                boolean created = editorState.executeCommand(new CreateEntityCommand(editorState, name, position, List.of(model)));
                return created ? Result.PLACED : Result.REJECTED;
            }
            case SCULPT -> {
                String name = baseName(asset.getFileName());
                DynamicMeshComponent sculpt = new DynamicMeshComponent(asset.getPath());
                boolean created = editorState.executeCommand(new CreateEntityCommand(editorState, name, position, List.of(sculpt)));
                return created ? Result.PLACED : Result.REJECTED;
            }
            case SOUND -> {
                // A dropped clip becomes an Audio Source that plays it when a script or graph triggers its cue (the file's name)
                String name = baseName(asset.getFileName());
                AudioSourceComponent sound = new AudioSourceComponent(name, asset.getPath());
                boolean created = editorState.executeCommand(new CreateEntityCommand(editorState, name, position, List.of(sound)));
                return created ? Result.PLACED : Result.REJECTED;
            }
            case SPRITE -> {
                // A dropped sprite becomes an entity that plays it (all frames until a tag is chosen in Details)
                String name = baseName(asset.getFileName());
                SpriteAnimatorComponent animator = new SpriteAnimatorComponent(asset);
                String sizeNote = sizeForWorld(editorState, asset, animator);
                boolean created = editorState.executeCommand(new CreateEntityCommand(editorState, name, position, List.of(animator)));
                if (created && sizeNote != null) {
                    editorState.setStatusMessage(sizeNote, false);
                }
                return created ? Result.PLACED : Result.REJECTED;
            }
            default -> {
                editorState.setStatusMessage("Cannot place " + asset.getFileName() + ": unsupported asset type", true);
                return Result.UNSUPPORTED_TYPE;
            }
        }
    }

    /** "crate.png" -> "crate". */
    static String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
