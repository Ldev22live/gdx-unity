package io.github.ldev22live.gdxunity.editor.core.project;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorLaunchOptionsTest {

    @Test
    void shouldStartANormalSessionWithoutArguments() {
        EditorLaunchOptions options = EditorLaunchOptions.parse(new String[0]);

        assertNull(options.project());
        assertFalse(options.isSmokeTest());
    }

    @Test
    void shouldAcceptProjectAsSeparateOrJoinedArgument() {
        assertEquals(new File("My Game"), EditorLaunchOptions.parse(new String[] {"--project", "My Game"}).project());
        assertEquals(new File("C:/games/a"), EditorLaunchOptions.parse(new String[] {"--project=C:/games/a"}).project());
    }

    @Test
    void shouldUseDefaultOrGivenSmokeTestFrameCount() {
        assertEquals(EditorLaunchOptions.DEFAULT_SMOKE_TEST_FRAMES,
            EditorLaunchOptions.parse(new String[] {"--smoke-test"}).smokeTestFrames());
        EditorLaunchOptions options = EditorLaunchOptions.parse(new String[] {"--smoke-test=30", "--project", "p"});
        assertEquals(30, options.smokeTestFrames());
        assertTrue(options.isSmokeTest());
    }

    @Test
    void shouldRejectUnknownOptionsMissingFoldersAndBadFrameCounts() {
        assertThrows(IllegalArgumentException.class, () -> EditorLaunchOptions.parse(new String[] {"--fullscreen"}));
        assertThrows(IllegalArgumentException.class, () -> EditorLaunchOptions.parse(new String[] {"--project"}));
        assertThrows(IllegalArgumentException.class, () -> EditorLaunchOptions.parse(new String[] {"--project="}));
        assertThrows(IllegalArgumentException.class, () -> EditorLaunchOptions.parse(new String[] {"--smoke-test=0"}));
        assertThrows(IllegalArgumentException.class, () -> EditorLaunchOptions.parse(new String[] {"--smoke-test=x"}));
    }

    @Test
    void shouldOpenASceneAndStartPlayingWhenAsked() {
        EditorLaunchOptions options = EditorLaunchOptions.parse(new String[] {"--scene", "samples/breakout-2d.json", "--play"});

        assertEquals(new File("samples/breakout-2d.json"), options.scene());
        assertTrue(options.play());
        assertEquals(new File("a.json"), EditorLaunchOptions.parse(new String[] {"--scene=a.json"}).scene());
        assertFalse(EditorLaunchOptions.parse(new String[0]).play());
        assertThrows(IllegalArgumentException.class, () -> EditorLaunchOptions.parse(new String[] {"--scene"}));
    }

    @Test
    void shouldIgnoreTheMacOsFinderProcessSerialNumber() {
        assertNull(EditorLaunchOptions.parse(new String[] {"-psn_0_12345"}).project());
    }

    @Test
    void shouldAcceptAGameExportFolderAsSeparateOrJoinedArgument() {
        EditorLaunchOptions separate = EditorLaunchOptions.parse(new String[] {"--export-game", "out dir", "--scene", "a.json"});
        EditorLaunchOptions joined = EditorLaunchOptions.parse(new String[] {"--export-game=out"});

        assertEquals(new File("out dir"), separate.exportGame());
        assertEquals(new File("a.json"), separate.scene());
        assertTrue(separate.isGameExport());
        assertEquals(new File("out"), joined.exportGame());
        assertFalse(EditorLaunchOptions.parse(new String[0]).isGameExport());
    }

    @Test
    void shouldRejectAGameExportWithoutAFolder() {
        assertThrows(IllegalArgumentException.class, () -> EditorLaunchOptions.parse(new String[] {"--export-game"}));
    }
}
