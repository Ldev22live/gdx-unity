package io.github.ldev22live.gdxunity.editor.core.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The project-relative path of a file picked in File > Open (roadmap M0.3). */
class ProjectPathsTest {
    @TempDir
    Path temp;

    private File assets() {
        return temp.resolve("assets").toFile();
    }

    @Test
    void shouldGiveThePathBelowTheAssetsFolderWithForwardSlashes() {
        assertEquals(Optional.of("theme.song"), ProjectPaths.relativeAssetPath(assets(), new File(assets(), "theme.song")));
        assertEquals(Optional.of("music/boss/theme.song"),
            ProjectPaths.relativeAssetPath(assets(), assets().toPath().resolve("music").resolve("boss").resolve("theme.song").toFile()));
    }

    @Test
    void shouldSeeThroughDotDotInsideTheProjectButNotOutOfIt() {
        File inside = new File(assets(), "music/../graphs/logic.graph");
        assertEquals(Optional.of("graphs/logic.graph"), ProjectPaths.relativeAssetPath(assets(), inside));

        File climbing = new File(assets(), "../elsewhere/secret.song");
        assertEquals(Optional.empty(), ProjectPaths.relativeAssetPath(assets(), climbing), "climbing out of the project is outside it");
    }

    @Test
    void shouldCallAFileOutsideTheProjectOutsideEvenWhenItsFolderNameStartsTheSame() {
        assertEquals(Optional.empty(), ProjectPaths.relativeAssetPath(assets(), temp.resolve("other/theme.song").toFile()));
        assertEquals(Optional.empty(), ProjectPaths.relativeAssetPath(assets(), temp.resolve("assets-backup/theme.song").toFile()),
            "assets-backup is not inside assets");
    }

    @Test
    void shouldGiveNothingForTheFolderItself() {
        assertEquals(Optional.empty(), ProjectPaths.relativeAssetPath(assets(), assets()));
    }

    @Test
    void shouldRefuseNullArguments() {
        assertThrows(NullPointerException.class, () -> ProjectPaths.relativeAssetPath(null, assets()));
        assertThrows(NullPointerException.class, () -> ProjectPaths.relativeAssetPath(assets(), null));
    }
}
