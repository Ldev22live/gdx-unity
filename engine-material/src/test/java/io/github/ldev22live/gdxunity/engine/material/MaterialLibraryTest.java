package io.github.ldev22live.gdxunity.engine.material;

import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaterialLibraryTest {
    @TempDir
    Path project;

    private MaterialLibrary library;

    @BeforeEach
    void setUp() throws IOException {
        Files.createDirectories(project.resolve("materials"));
        library = new MaterialLibrary(AssetSource.directory(project.toFile()));
    }

    private void write(String path, String text) throws IOException {
        Files.writeString(project.resolve(path), text);
    }

    @Test
    void shouldGiveEveryUserOfAFileTheSameMaterial() throws IOException {
        write("materials/rock.material", "{\"version\": 1, \"name\": \"Rock\", \"roughness\": 0.9}");

        Material first = library.get("materials/rock.material");
        Material second = library.get("materials\\rock.material");

        assertNotNull(first);
        assertSame(first, second, "shared material, whatever the slashes");
        assertEquals(0.9f, first.getRoughness());
        assertEquals(1, library.size());
    }

    @Test
    void shouldGiveNoMaterialAndSayWhyForAMissingOrBrokenFile() throws IOException {
        write("materials/bad.material", "{\"version\": 1, \"metallic\": 5}");

        assertNull(library.get("materials/nothing.material"));
        assertNull(library.get("materials/bad.material"));

        assertTrue(library.getError("materials/nothing.material").contains("not found"));
        assertTrue(library.getError("materials/bad.material").contains("metallic"));
        assertNull(library.getError("materials/never-asked.material"));
        assertEquals(0, library.size());
    }

    @Test
    void shouldSaveAMaterialToItsFileAndReadItBack() {
        Material water = new Material();
        water.setName("Water");
        water.setRoughness(0.05f);
        water.setAlphaMode(Material.AlphaMode.BLEND);

        library.save("materials/water.material", water);
        Material again = new MaterialLibrary(AssetSource.directory(project.toFile())).get("materials/water.material");

        assertNotNull(again);
        assertEquals("Water", again.getName());
        assertEquals(0.05f, again.getRoughness());
        assertEquals(Material.AlphaMode.BLEND, again.getAlphaMode());
        assertSame(water, library.get("materials/water.material"), "the saved object becomes the shared one");
    }

    @Test
    void shouldReloadAChangedFileIntoTheSameObjectSoEveryUserSeesIt() throws IOException {
        write("materials/wood.material", "{\"version\": 1, \"roughness\": 0.5}");
        Material wood = library.get("materials/wood.material");
        int revision = wood.getRevision();

        write("materials/wood.material", "{\"version\": 1, \"roughness\": 0.2}");
        library.reload("materials/wood.material");

        assertSame(wood, library.get("materials/wood.material"));
        assertEquals(0.2f, wood.getRoughness());
        assertTrue(wood.getRevision() > revision, "renderers see a change");
    }

    @Test
    void shouldForgetAMaterialWhoseFileBecameUnreadable() throws IOException {
        write("materials/wood.material", "{\"version\": 1}");
        library.get("materials/wood.material");

        write("materials/wood.material", "garbage");
        library.reload("materials/wood.material");

        assertNull(library.get("materials/wood.material"));
        assertTrue(library.getError("materials/wood.material").contains("Not a material file"));
    }
}
