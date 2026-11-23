package io.github.ldev22live.gdxunity.editor.core.material;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.EditorTestHelper;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.material.Material;
import io.github.ldev22live.gdxunity.engine.material.MaterialIO;
import io.github.ldev22live.gdxunity.engine.material.MaterialLibrary;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaterialEditingTest {
    @TempDir
    Path project;

    private EditorState editorState;
    private MaterialLibrary library;

    @BeforeAll
    static void init() {
        EditorTestHelper.initNatives();
    }

    @BeforeEach
    void setUp() {
        editorState = new EditorState();
        library = new MaterialLibrary(AssetSource.directory(project.toFile()));
    }

    @Test
    void shouldCreateMaterialsUnderUniqueNamesInTheMaterialsFolder() {
        String first = MaterialAssets.createNew(project.toFile(), library, "Rock");
        String second = MaterialAssets.createNew(project.toFile(), library, "Rock");
        String blank = MaterialAssets.createNew(project.toFile(), library, "  ");
        String odd = MaterialAssets.createNew(project.toFile(), library, "a/b:c");

        assertEquals("materials/Rock.material", first);
        assertEquals("materials/Rock 2.material", second);
        assertEquals("materials/Material.material", blank);
        assertEquals("materials/a_b_c.material", odd);
        assertTrue(Files.isRegularFile(project.resolve(first)));
        assertEquals("Rock 2", library.get(second).getName());
    }

    @Test
    void shouldRecogniseMaterialPathsByExtension() {
        assertTrue(MaterialAssets.isMaterialPath("materials/Rock.material"));
        assertTrue(MaterialAssets.isMaterialPath(" a/B.MATERIAL "));
        assertFalse(MaterialAssets.isMaterialPath("models/rock.gltf"));
        assertFalse(MaterialAssets.isMaterialPath(null));
    }

    @Test
    void shouldChangeTheSharedMaterialAndItsFileAndUndoAndRedoBoth() throws IOException {
        String path = MaterialAssets.createNew(project.toFile(), library, "Metal");
        Material metal = library.get(path);
        ModifyMaterialPropertyCommand<Float> command = new ModifyMaterialPropertyCommand<>(editorState, library, path, metal,
            metal.getMetallic(), 0.9f, metal::setMetallic, "Change Metallic");

        assertTrue(command.execute());
        assertEquals(0.9f, metal.getMetallic());
        assertEquals(0.9f, MaterialIO.fromJson(Files.readString(project.resolve(path))).getMetallic(), "the file follows");

        assertTrue(command.undo());
        assertEquals(0f, metal.getMetallic());
        assertEquals(0f, MaterialIO.fromJson(Files.readString(project.resolve(path))).getMetallic());

        assertTrue(command.execute());
        assertEquals(0.9f, metal.getMetallic());
        assertSame(metal, library.get(path), "still the one shared material");
        assertNotNull(command.getDescription());
    }

    @Test
    void shouldChangeATextureSlotAndTheTilingWithUndoAndKeepThemInTheFile() throws IOException {
        String path = MaterialAssets.createNew(project.toFile(), library, "Brick");
        Material brick = library.get(path);

        editorState.executeCommand(new ModifyMaterialPropertyCommand<String>(editorState, library, path, brick,
            brick.getTexture(Material.Slot.BASE_COLOR), "textures/brick.png", text -> brick.setTexture(Material.Slot.BASE_COLOR, text),
            "Change Material Color Tex"));
        editorState.executeCommand(new ModifyMaterialPropertyCommand<Float>(editorState, library, path, brick,
            brick.getTilingU(), 6f, u -> brick.setTiling(u, brick.getTilingV()), "Change Material Tiling"));

        Material saved = MaterialIO.fromJson(Files.readString(project.resolve(path)));
        assertEquals("textures/brick.png", saved.getTexture(Material.Slot.BASE_COLOR));
        assertEquals(6f, saved.getTilingU());
        assertEquals(1f, saved.getTilingV(), "only the changed axis moves");

        editorState.getCommandHistory().undo();
        editorState.getCommandHistory().undo();
        assertEquals("", brick.getTexture(Material.Slot.BASE_COLOR));
        assertEquals(1f, brick.getTilingU());
        assertEquals("", MaterialIO.fromJson(Files.readString(project.resolve(path))).getTexture(Material.Slot.BASE_COLOR));
    }

    @Test
    void shouldRunThroughTheEditorsUndoHistory() {
        String path = MaterialAssets.createNew(project.toFile(), library, "Glow");
        Material glow = library.get(path);

        editorState.executeCommand(new ModifyMaterialPropertyCommand<Float>(editorState, library, path, glow,
            glow.getEmissiveStrength(), 5f, glow::setEmissiveStrength, "Change Emissive Strength"));
        assertEquals(5f, glow.getEmissiveStrength());

        editorState.getCommandHistory().undo();
        assertEquals(1f, glow.getEmissiveStrength());
        editorState.getCommandHistory().redo();
        assertEquals(5f, glow.getEmissiveStrength());
    }
}
