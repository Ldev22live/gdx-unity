package io.github.ldev22live.gdxunity.engine.desktop;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What an exported game (Phase 18-C, TD-046) needs from the runtime: the start scene and name from the project file, the
 * project folder from a system property, and crash logs outside the install folder.
 */
class GameExportRuntimeTest {

    private static void writeProjectFile(Path folder, String content) throws IOException {
        Files.createDirectories(folder);
        Files.writeString(folder.resolve(GameProjectFile.FILE_NAME), content);
    }

    @Test
    void shouldReadNameAndStartSceneFromTheProjectFile(@TempDir Path project) throws IOException {
        writeProjectFile(project, "format=1\nname=Space Hopper\nstartScene=levels/one.json\n");

        GameProjectFile file = GameProjectFile.read(project.toFile());

        assertEquals("Space Hopper", file.name());
        assertEquals("levels/one.json", file.startScene());
        assertTrue(file.hasStartScene());
    }

    @Test
    void shouldReportNoStartSceneForAnEditorProjectFile(@TempDir Path project) throws IOException {
        writeProjectFile(project, "format=1\nname=GdxUnity Sample\n");

        assertFalse(GameProjectFile.read(project.toFile()).hasStartScene());
    }

    @Test
    void shouldRefuseAFolderWithoutAProjectFile(@TempDir Path folder) {
        IOException error = assertThrows(IOException.class, () -> GameProjectFile.read(folder.toFile()));
        assertTrue(error.getMessage().contains(GameProjectFile.FILE_NAME), error.getMessage());
    }

    @Test
    void shouldPlayTheStartSceneWhenNoSceneIsGiven(@TempDir Path project) throws IOException {
        writeProjectFile(project, "name=G\nstartScene=levels/one.json\n");

        GameLaunchOptions options = GameLaunchOptions.parse(new String[0], new File("elsewhere").getAbsoluteFile(), null,
            project.toString());

        assertEquals(project.toAbsolutePath().normalize().toFile(), options.project());
        assertEquals(project.resolve("levels/one.json").toAbsolutePath().normalize().toFile(), options.scene());
        assertEquals(project.resolve("build/scripts").toAbsolutePath().normalize().toFile(), options.scripts());
    }

    @Test
    void shouldPreferTheSceneOnTheCommandLineToTheStartScene(@TempDir Path project) throws IOException {
        writeProjectFile(project, "startScene=levels/one.json\n");

        GameLaunchOptions options = GameLaunchOptions.parse(new String[] {"--scene", "other.json"}, new File(".").getAbsoluteFile(),
            null, project.toString());

        assertEquals(project.resolve("other.json").toAbsolutePath().normalize().toFile(), options.scene());
    }

    @Test
    void shouldPreferTheProjectOnTheCommandLineToTheProperty(@TempDir Path property, @TempDir Path argument) throws IOException {
        writeProjectFile(property, "startScene=a.json\n");
        writeProjectFile(argument, "startScene=b.json\n");

        GameLaunchOptions options = GameLaunchOptions.parse(new String[] {"--project", argument.toString()},
            new File(".").getAbsoluteFile(), null, property.toString());

        assertEquals(argument.resolve("b.json").toAbsolutePath().normalize().toFile(), options.scene());
    }

    @Test
    void shouldExplainWhenNeitherTheCommandLineNorTheProjectNamesAScene(@TempDir Path project) throws IOException {
        writeProjectFile(project, "name=G\n");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> GameLaunchOptions.parse(new String[0], new File(".").getAbsoluteFile(), null, project.toString()));

        assertTrue(error.getMessage().contains(GameProjectFile.KEY_START_SCENE), error.getMessage());
    }

    @Test
    void shouldPutLogsInTheUsersOwnFolderPerPlatform() {
        File home = new File("home");

        assertEquals(new File(new File("C:\\Users\\a\\AppData\\Local", "Space Hopper"), "logs"),
            GameFailureReporter.logDir("Windows 11", Map.of("LOCALAPPDATA", "C:\\Users\\a\\AppData\\Local"), home, "Space Hopper"));
        assertEquals(new File(new File(new File(new File(home, "AppData"), "Local"), "Hopper"), "logs"),
            GameFailureReporter.logDir("Windows 11", Map.of(), home, "Hopper"), "without LOCALAPPDATA the default Local folder is used");
        assertEquals(new File(new File(new File(home, "Library"), "Logs"), "Hopper"),
            GameFailureReporter.logDir("Mac OS X", Map.of(), home, "Hopper"));
        assertEquals(new File(new File(new File(home, ".local"), "state"), "Hopper" + File.separator + "logs"),
            GameFailureReporter.logDir("Linux", Map.of("XDG_STATE_HOME", "relative/ignored"), home, "Hopper"));
    }

    @Test
    void shouldStripSymbolsFromTheLogFolderName() {
        assertEquals("Space Hopper 2", GameFailureReporter.folderName("Space Hopper: 2?"));
        assertEquals(GameFailureReporter.DEFAULT_GAME_NAME, GameFailureReporter.folderName("..."));
        assertEquals(GameFailureReporter.DEFAULT_GAME_NAME, GameFailureReporter.folderName(null));
    }

    @Test
    void shouldWriteACrashLogWithTheMessageAndStackTraceAndNeverOverwrite(@TempDir Path folder) throws IOException {
        LocalDateTime time = LocalDateTime.of(2026, 11, 6, 12, 30, 5);
        Exception failure = new IllegalStateException("boom");

        File first = GameFailureReporter.writeLog(folder.resolve("logs").toFile(), "Cannot play x", failure, time,
            new String[] {"--scene", "x"}, Map.of("os.name", "TestOS"));
        File second = GameFailureReporter.writeLog(folder.resolve("logs").toFile(), "again", null, time, new String[0], Map.of());

        String text = Files.readString(first.toPath());
        assertTrue(text.contains("message: Cannot play x"), text);
        assertTrue(text.contains("os.name: TestOS"), text);
        assertTrue(text.contains("arguments: --scene x"), text);
        assertTrue(text.contains("IllegalStateException: boom"), text);
        assertEquals("crash-20261106-123005.log", first.getName());
        assertEquals("crash-20261106-123005-1.log", second.getName());
    }

    @Test
    void shouldReadTheWindowIconFromTheProjectFile(@TempDir Path project) throws IOException {
        writeProjectFile(project, "name=G\nwindowIcon=art/icon.png\n");

        assertEquals("art/icon.png", GameProjectFile.read(project.toFile()).windowIcon());
        writeProjectFile(project, "name=G\n");
        assertEquals("", GameProjectFile.read(project.toFile()).windowIcon());
    }

    @Test
    void shouldUseTheProjectsWindowIconOnlyWhenTheFileExists(@TempDir Path project) throws IOException {
        writeProjectFile(project, "name=G\nwindowIcon=art/icon.png\n");
        assertEquals(null, DesktopLauncher.windowIcon(project.toFile()), "named but missing: fall back to the Forgeroom icon");

        Files.createDirectories(project.resolve("art"));
        Files.writeString(project.resolve("art/icon.png"), "png");
        assertEquals(project.resolve("art/icon.png").toFile(), DesktopLauncher.windowIcon(project.toFile()));

        writeProjectFile(project, "name=G\n");
        assertEquals(null, DesktopLauncher.windowIcon(project.toFile()), "no icon named");
        assertEquals(null, DesktopLauncher.windowIcon(project.resolve("no-such-folder").toFile()), "no project file: no icon, no failure");
    }

    @Test
    void shouldBundleTheForgeroomIconInTheSizesTheWindowSystemPicksFrom() {
        for (int size : new int[] {32, 64, 128, 256}) {
            assertTrue(getClass().getClassLoader().getResource("forgeroom-icon-" + size + ".png") != null, "forgeroom-icon-" + size + ".png");
        }
    }
}
