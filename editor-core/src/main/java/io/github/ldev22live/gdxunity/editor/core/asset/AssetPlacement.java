package io.github.ldev22live.gdxunity.editor.core.asset;

import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.command.CreateEntityCommand;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetType;
import io.github.ldev22live.gdxunity.engine.render.components.SpriteRendererComponent;

import java.util.List;
import java.util.Objects;

/**
 * Places an asset dragged from the Content Browser into the scene: textures become sprite entities at the
 * drop point, created as one undoable command. Other asset types are reported as not placeable yet.
 */
public final class AssetPlacement {

    /** Edge length of a sprite created from a dropped texture, in world units. */
    public static final float DROPPED_SPRITE_SIZE = 100f;

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
            case MODEL -> {
                editorState.setStatusMessage("Cannot place " + asset.getFileName()
                    + ": model loading is part of Phase 13 and waits on the glTF loader decision", true);
                return Result.UNSUPPORTED_TYPE;
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
