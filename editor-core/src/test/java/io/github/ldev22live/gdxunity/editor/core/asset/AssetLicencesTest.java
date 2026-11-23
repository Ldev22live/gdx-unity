package io.github.ldev22live.gdxunity.editor.core.asset;

import io.github.ldev22live.gdxunity.engine.core.asset.OriginalAssets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetLicencesTest {
    @Test
    void shouldDescribeARecordedOriginalAndAnUnrecordedFile(@TempDir File project) throws IOException {
        Files.createDirectories(project.toPath().resolve("licenses"));
        Files.writeString(project.toPath().resolve(OriginalAssets.PATH), "{\"schemaVersion\":1,\"kind\":\"project-originals\",\"files\":"
            + "[{\"path\":\"sprites/hero.sprite\",\"bytes\":1,\"sha256\":\"" + "d".repeat(64) + "\",\"licence\":\"Apache-2.0\","
            + "\"madeWith\":\"by hand\"}]}", StandardCharsets.UTF_8);

        assertEquals("licence: Apache-2.0: made in this repository with by hand", AssetLicences.describe(project, "sprites/hero.sprite"));
        assertEquals("licence: No licence record (made in this project, or copied in by hand)",
            AssetLicences.describe(project, "sprites/mine.sprite"));
    }

    @Test
    void shouldSayWhyWhenARecordCannotBeRead(@TempDir File project) throws IOException {
        Files.createDirectories(project.toPath().resolve("licenses"));
        Files.writeString(project.toPath().resolve(OriginalAssets.PATH), "{\"schemaVersion\":7}", StandardCharsets.UTF_8);

        String line = AssetLicences.describe(project, "sprites/hero.sprite");

        assertTrue(line.startsWith("licence records could not be read: "), line);
        assertTrue(line.contains("schemaVersion"), line);
    }
}
