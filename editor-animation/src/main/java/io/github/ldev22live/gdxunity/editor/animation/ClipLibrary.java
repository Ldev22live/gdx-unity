package io.github.ldev22live.gdxunity.editor.animation;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.command.AddComponentCommand;
import io.github.ldev22live.gdxunity.editor.core.command.ModifyComponentPropertyCommand;
import io.github.ldev22live.gdxunity.engine.animation.AnimationTrack;
import io.github.ldev22live.gdxunity.engine.animation.AnimatorComponent;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * The clips that ship with the editor, for the Animations tab (TD-044): the humanoid set (Idle, Walk, Run, Jump, Fall)
 * in {@code animations/humanoid} and every other clip under {@code animations}. The clips are keyed to the bone names
 * of the humanoid skeleton ({@code Hips}, {@code Spine}, {@code Chest}, {@code Neck}, {@code Head}, arms, forearms,
 * thighs, shins), so they play on any entity whose children carry those names.
 */
public final class ClipLibrary {
    /** Folder under the asset root holding the humanoid clips. */
    public static final String HUMANOID_FOLDER = AnimationAssets.ANIMATION_FOLDER + "/humanoid";
    /** Name of the root bone every humanoid clip's tracks start with. */
    public static final String HUMANOID_ROOT_BONE = "Hips";

    private ClipLibrary() {}

    /** A clip and the folder group it is listed under. */
    public record Entry(AssetReference asset, String name, boolean humanoid) {}

    /** The humanoid clips first (sorted by name), then the other clips of {@code animations} (by path). */
    public static List<Entry> list(File assetRoot) {
        Objects.requireNonNull(assetRoot, "assetRoot cannot be null");
        List<Entry> humanoid = new ArrayList<>();
        List<Entry> others = new ArrayList<>();
        collect(assetRoot, new File(assetRoot, AnimationAssets.ANIMATION_FOLDER), AnimationAssets.ANIMATION_FOLDER, humanoid, others);
        Comparator<Entry> byPath = Comparator.comparing(entry -> entry.asset().getPath());
        humanoid.sort(byPath);
        others.sort(byPath);
        humanoid.addAll(others);
        return humanoid;
    }

    private static void collect(File assetRoot, File folder, String relative, List<Entry> humanoid, List<Entry> others) {
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }
        Arrays.sort(files);
        for (File file : files) {
            String path = relative + "/" + file.getName();
            if (file.isDirectory()) {
                collect(assetRoot, file, path, humanoid, others);
            } else if (file.getName().endsWith(AnimationAssets.EXTENSION)) {
                String name = file.getName().substring(0, file.getName().length() - AnimationAssets.EXTENSION.length());
                boolean isHumanoid = relative.equals(HUMANOID_FOLDER);
                (isHumanoid ? humanoid : others).add(new Entry(new AssetReference(path), name, isHumanoid));
            }
        }
    }

    /** Whether {@code entity} has the humanoid's root bone as a direct child, so humanoid clips can play on it. */
    public static boolean isHumanoid(Entity entity) {
        return entity != null && AnimationTrack.resolve(entity, HUMANOID_ROOT_BONE) != null;
    }

    /**
     * The entity a library clip should animate: {@code preferred} (the selection) if it is a humanoid, otherwise the first
     * root entity of {@code scene} that is. Null when the scene has none.
     */
    public static Entity findHumanoid(Scene scene, Entity preferred) {
        if (isHumanoid(preferred)) {
            return preferred;
        }
        if (scene != null) {
            for (Entity root : scene.getRootEntities()) {
                if (isHumanoid(root)) {
                    return root;
                }
            }
        }
        return null;
    }

    /**
     * Makes {@code entity} play {@code clip}: sets the clip of its Animator, or adds an Animator that plays it. One undo
     * step either way.
     *
     * @return whether the command ran
     */
    public static boolean assign(EditorState editorState, Entity entity, AssetReference clip) {
        Objects.requireNonNull(editorState, "editorState cannot be null");
        Objects.requireNonNull(entity, "entity cannot be null");
        Objects.requireNonNull(clip, "clip cannot be null");
        AnimatorComponent animator = entity.getComponent(AnimatorComponent.class);
        if (animator == null) {
            return editorState.executeCommand(new AddComponentCommand(editorState, entity.getId(), new AnimatorComponent(clip.getPath())));
        }
        return editorState.executeCommand(new ModifyComponentPropertyCommand<>(editorState, entity.getId(), animator,
            animator.getClipPath(), clip.getPath(), animator::setClipPath, "Change Animation Clip"));
    }
}
