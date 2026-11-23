package io.github.ldev22live.gdxunity.engine.core.asset;

import com.badlogic.gdx.files.FileHandle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetSourceTest {
    @TempDir
    Path root;

    @Test
    void shouldResolveReferencesAgainstTheFolder() throws IOException {
        Files.createDirectories(root.resolve("graphs"));
        Files.writeString(root.resolve("graphs/A.graph"), "{}");
        AssetSource source = AssetSource.directory(root.toFile());

        FileHandle found = source.resolve(new AssetReference("graphs\\A.graph"));
        FileHandle missing = source.resolve(new AssetReference("graphs/B.graph"));

        assertTrue(found.exists());
        assertEquals("{}", found.readString("UTF-8"));
        assertFalse(missing.exists(), "a missing file still gets a handle, which callers check");
    }

    @Test
    void shouldLetTheCatalogServeAsASourceForItsFolder() throws IOException {
        Files.writeString(root.resolve("hero.png"), "not really a png");
        AssetCatalog catalog = new AssetCatalog(root.toFile());

        FileHandle handle = catalog.resolve(new AssetReference("hero.png"));

        assertTrue(handle.exists(), "resolves without a scan");
        assertEquals(root.resolve("hero.png").toFile().getAbsolutePath(), handle.file().getAbsolutePath());
    }

    @Test
    void shouldGiveAServiceWithoutCatalogNoCatalog() {
        AssetSource source = AssetSource.directory(root.toFile());
        AssetCatalog catalog = new AssetCatalog(root.toFile());

        AssetService plain = new AssetService(source);
        AssetService catalogued = new AssetService(catalog);

        assertFalse(plain.hasCatalog());
        IllegalStateException error = assertThrows(IllegalStateException.class, plain::getCatalog);
        assertTrue(error.getMessage().contains("no catalog"), error.getMessage());
        assertTrue(catalogued.hasCatalog());
        assertSame(catalog, catalogued.getCatalog());
    }

    @Test
    void shouldRecordMissingTexturesFromTheSourceWithoutLoadingAnything() {
        AssetService service = new AssetService(AssetSource.directory(root.toFile()));
        AssetReference missing = new AssetReference("textures/none.png");

        assertEquals(null, service.loadTexture(missing));
        assertEquals("Asset file not found: textures/none.png", service.getLoadError(missing));
    }
}
