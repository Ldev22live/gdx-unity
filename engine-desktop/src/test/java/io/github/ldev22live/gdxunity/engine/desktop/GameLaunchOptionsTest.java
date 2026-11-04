package io.github.ldev22live.gdxunity.engine.desktop;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameLaunchOptionsTest {
    private static final File WORK = new File("work").getAbsoluteFile();

    private static File under(File base, String path) {
        return new File(base, path).toPath().normalize().toFile();
    }

    @Test
    void shouldResolveSceneAndDefaultScriptsAgainstWorkingDirectoryWhenNoProjectGiven() {
        GameLaunchOptions options = GameLaunchOptions.parse(new String[] {"--scene", "samples/a.json"}, WORK, null);

        assertEquals(under(WORK, "."), options.project());
        assertEquals(under(WORK, "samples/a.json"), options.scene());
        assertEquals(under(WORK, GameLaunchOptions.DEFAULT_SCRIPTS_PATH), options.scripts());
        assertFalse(options.isSmokeTest());
    }

    @Test
    void shouldResolveSceneAndScriptsAgainstProject() {
        GameLaunchOptions options = GameLaunchOptions.parse(
            new String[] {"--project", "game", "--scene", "level.json", "--scripts", "classes.jar"}, WORK, "ignored");

        File project = under(WORK, "game");
        assertEquals(project, options.project());
        assertEquals(under(project, "level.json"), options.scene());
        assertEquals(under(project, "classes.jar"), options.scripts(), "--scripts wins over the system property");
    }

    @Test
    void shouldUseScriptsPropertyWhenNoScriptsOptionGiven() {
        File compiled = new File("compiled").getAbsoluteFile();

        GameLaunchOptions options = GameLaunchOptions.parse(new String[] {"--scene", "a.json"}, WORK, compiled.getPath());

        assertEquals(compiled.toPath().normalize().toFile(), options.scripts());
    }

    @Test
    void shouldParseSmokeTestWithAndWithoutFrameCount() {
        assertEquals(GameLaunchOptions.DEFAULT_SMOKE_TEST_FRAMES,
            GameLaunchOptions.parse(new String[] {"--scene", "a.json", "--smoke-test"}, WORK, null).smokeTestFrames());
        GameLaunchOptions counted = GameLaunchOptions.parse(new String[] {"--smoke-test=42", "--scene", "a.json"}, WORK, null);
        assertEquals(42, counted.smokeTestFrames());
        assertTrue(counted.isSmokeTest());
    }

    @Test
    void shouldRejectMissingSceneUnknownOptionsMissingValuesAndBadFrameCounts() {
        assertThrows(IllegalArgumentException.class, () -> GameLaunchOptions.parse(new String[0], WORK, null));
        assertThrows(IllegalArgumentException.class,
            () -> GameLaunchOptions.parse(new String[] {"--scene", "a.json", "--fullscreen"}, WORK, null));
        assertThrows(IllegalArgumentException.class, () -> GameLaunchOptions.parse(new String[] {"--scene"}, WORK, null));
        assertThrows(IllegalArgumentException.class,
            () -> GameLaunchOptions.parse(new String[] {"--scene", "--project", "x"}, WORK, null));
        assertThrows(IllegalArgumentException.class,
            () -> GameLaunchOptions.parse(new String[] {"--scene", "a.json", "--smoke-test=0"}, WORK, null));
        assertThrows(IllegalArgumentException.class,
            () -> GameLaunchOptions.parse(new String[] {"--scene", "a.json", "--smoke-test=many"}, WORK, null));
    }

    @Test
    void shouldResolveAgainstTheCurrentFolderWhenTheWorkingDirectoryIsRelative() {
        GameLaunchOptions options = GameLaunchOptions.parse(new String[] {"--scene", "samples/a.json"}, new File("."), null);

        File current = new File("").getAbsoluteFile();
        assertEquals(current, options.project());
        assertEquals(under(current, "samples/a.json"), options.scene());
        assertTrue(options.scene().isAbsolute() && options.scripts().isAbsolute());
    }

    @Test
    void shouldIgnoreMacOsProcessSerialNumber() {
        GameLaunchOptions options = GameLaunchOptions.parse(new String[] {"-psn_0_12345", "--scene", "a.json"}, WORK, null);

        assertEquals(under(WORK, "a.json"), options.scene());
    }
}
