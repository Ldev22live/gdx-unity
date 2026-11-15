package io.github.ldev22live.gdxunity.editor.core.asset;

import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.EditorTestHelper;
import io.github.ldev22live.gdxunity.editor.core.prefab.PrefabService;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What opening an asset does, for every type (roadmap M1.5). */
class AssetOpeningTest {
    @TempDir
    Path temp;

    @BeforeAll
    static void init() {
        EditorTestHelper.initNatives();
    }

    @Test
    void shouldOpenExactlyGraphsClipsSpritesAndSongsInAnEditorAndExplainEveryOtherType() {
        assertEquals(EnumSet.of(AssetType.GRAPH, AssetType.ANIMATION, AssetType.SPRITE, AssetType.SONG), AssetOpening.WITH_EDITOR);
        for (AssetType type : AssetType.values()) {
            String text = AssetOpening.guidance(type);
            assertFalse(text.isBlank(), type + " needs a text");
            assertFalse(text.toLowerCase(Locale.ROOT).contains("double-click"), type + ": never promise a double-click: " + text);
            assertEquals(AssetOpening.WITH_EDITOR.contains(type), AssetOpening.hasEditor(type), type.name());
        }
    }

    @Test
    void shouldSayDragIntoTheSceneViewExactlyForTheTypesTheSceneViewPlaces() {
        EditorState state = new EditorState();
        state.setPrefabService(new PrefabService(state, temp.toFile()));
        Set<AssetType> placed = EnumSet.noneOf(AssetType.class);
        for (AssetType type : AssetType.values()) {
            if (type == AssetType.UNKNOWN) {
                continue;
            }
            AssetReference asset = new AssetReference("x/thing." + type.getExtensions().get(0));
            if (AssetPlacement.place(state, asset, new Vector3()) != AssetPlacement.Result.UNSUPPORTED_TYPE) {
                placed.add(type);
            }
        }

        assertEquals(placed, AssetOpening.PLACEABLE, "the guidance must match what a drop into the scene view really does");
        for (AssetType type : AssetOpening.PLACEABLE) {
            assertTrue(AssetOpening.guidance(type).contains("into the scene view"), type + ": " + AssetOpening.guidance(type));
        }
        for (AssetType type : EnumSet.of(AssetType.MATERIAL, AssetType.TERRAIN, AssetType.GRAPH, AssetType.ANIMATION, AssetType.SONG)) {
            assertFalse(AssetOpening.guidance(type).contains("Drag"), type + " cannot be dropped into the scene view");
        }
        assertNotEquals("", AssetOpening.guidance(AssetType.MATERIAL));
    }
}
