package io.github.ldev22live.gdxunity.editor.core.asset;

import io.github.ldev22live.gdxunity.engine.core.asset.AssetCatalog;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Assets changed on disk while the editor runs are noticed (Phase 13-C, reimport on change). */
class AssetChangeWatcherTest {
    private static final AssetReference HERO = new AssetReference("models/hero.glb");
    private static final AssetReference ENEMY = new AssetReference("enemy.png");
    private static final long A_MINUTE = 60_000L;

    @TempDir
    Path root;

    private AssetChangeWatcher watcher;
    private final List<AssetChangeWatcher.Changes> heard = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        Files.createDirectories(root.resolve("models"));
        Files.writeString(root.resolve("models/hero.glb"), "model");
        Files.writeString(root.resolve("enemy.png"), "image");
        watcher = new AssetChangeWatcher(new AssetCatalog(root.toFile()), 2f);
        watcher.addListener(heard::add);
        watcher.check();
    }

    @Test
    void shouldReportNothingForTheFirstCheckEvenThoughEveryAssetIsNew() {
        assertTrue(heard.isEmpty());
    }

    @Test
    void shouldReportAnEditedFileAsModifiedByItsSize() throws IOException {
        Files.writeString(root.resolve("models/hero.glb"), "a longer model");

        AssetChangeWatcher.Changes changes = watcher.check();

        assertEquals(List.of(HERO), changes.modified());
        assertTrue(changes.added().isEmpty() && changes.removed().isEmpty());
        assertEquals(1, heard.size());
    }

    @Test
    void shouldReportAnEditThatKeepsTheSizeByItsModificationTime() throws IOException {
        Files.writeString(root.resolve("enemy.png"), "IMAGE");
        Files.setLastModifiedTime(root.resolve("enemy.png"), FileTime.fromMillis(System.currentTimeMillis() + A_MINUTE));

        assertEquals(List.of(ENEMY), watcher.check().modified());
    }

    @Test
    void shouldReportAddedAndRemovedFilesInSubfolders() throws IOException {
        Files.delete(root.resolve("models/hero.glb"));
        Files.writeString(root.resolve("models/villain.glb"), "model");

        AssetChangeWatcher.Changes changes = watcher.check();

        assertEquals(List.of(new AssetReference("models/villain.glb")), changes.added());
        assertEquals(List.of(HERO), changes.removed());
    }

    @Test
    void shouldStaySilentWhenNothingChanged() {
        watcher.check();
        watcher.check();

        assertTrue(heard.isEmpty());
    }

    @Test
    void shouldOnlyCheckOnceTheIntervalHasPassed() throws IOException {
        Files.writeString(root.resolve("models/hero.glb"), "changed again");

        watcher.update(1.5f);
        assertTrue(heard.isEmpty(), "1.5 of 2 seconds");
        watcher.update(0.6f);

        assertEquals(1, heard.size());
        assertEquals(List.of(HERO), heard.get(0).modified());
    }

    @Test
    void shouldRefuseAnIntervalThatIsNotPositive() {
        assertThrows(IllegalArgumentException.class, () -> new AssetChangeWatcher(new AssetCatalog(root.toFile()), 0f));
    }
}
