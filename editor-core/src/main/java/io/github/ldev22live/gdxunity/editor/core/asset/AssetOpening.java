package io.github.ldev22live.gdxunity.editor.core.asset;

import io.github.ldev22live.gdxunity.engine.core.asset.AssetType;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * What opening an asset does, for every asset type (roadmap M1.5): four types open in an editor of their own; for every other type the
 * person is told plainly how that kind of file is used, instead of a double-click that does nothing. The texts name real places in the
 * editor, and {@link #PLACEABLE} must stay the set {@link AssetPlacement} places when a file is dragged into the scene view.
 */
public final class AssetOpening {
    /** Types with an editor that opens one file: the Visual Scripting tab, the Timeline, the Sprites tab and the Music tab. */
    public static final Set<AssetType> WITH_EDITOR = Collections.unmodifiableSet(
        EnumSet.of(AssetType.GRAPH, AssetType.ANIMATION, AssetType.SPRITE, AssetType.SONG));
    /** Types the scene view places when one is dragged into it ({@link AssetPlacement#place}). */
    public static final Set<AssetType> PLACEABLE = Collections.unmodifiableSet(
        EnumSet.of(AssetType.TEXTURE, AssetType.PREFAB, AssetType.MODEL, AssetType.SCULPT, AssetType.SOUND, AssetType.SPRITE));

    private AssetOpening() { }

    /** Whether the type opens in an editor of its own. */
    public static boolean hasEditor(AssetType type) {
        return WITH_EDITOR.contains(Objects.requireNonNull(type, "type cannot be null"));
    }

    /**
     * How a file of this type is used, for a type with no editor of its own; for a type with one, where it opens. Never null.
     */
    public static String guidance(AssetType type) {
        return switch (Objects.requireNonNull(type, "type cannot be null")) {
            case GRAPH -> "A graph opens in Customize > Logic.";
            case ANIMATION -> "An animation clip opens in the Timeline (Customize > Animation).";
            case SPRITE -> "A sprite opens in Customize > 2D Studio: Sprites; drag it into the scene view to place one that plays it.";
            case SONG -> "A song opens in Customize > Audio & Music; a Music Source component plays it in a scene.";
            case TEXTURE -> "An image has no editor of its own yet. Drag it into the scene view to place it as a sprite, or name it in a "
                + "material's texture slot.";
            case MODEL -> "A 3D model has no editor of its own yet (mesh editing is roadmap R7a). Drag it into the scene view to place it; "
                + "its placement and import scale are set in Details.";
            case PREFAB -> "A prefab has no editor of its own. Drag it into the scene view to place a linked copy, change the copy, and "
                + "apply the changes back to the prefab from the Outliner's context menu.";
            case SCULPT -> "Drag a sculpt into the scene view to place it, then select it and turn on Sculpt to reshape it.";
            case SOUND -> "A recorded sound has no editor of its own yet. Drag it into the scene view to make an Audio Source that plays it.";
            case MATERIAL -> "A material is edited in the Details of the surface that uses it: select a Mesh Primitive and choose the "
                + "material in its Material section.";
            case TERRAIN -> "A terrain is shaped in the World: choose Terrain in the World strip under the view. To use this file, add a "
                + "Terrain component to an entity, select the file here and press Use Selected in its Details.";
            case UNKNOWN -> "This kind of file is not a Forgeroom asset.";
        };
    }
}
