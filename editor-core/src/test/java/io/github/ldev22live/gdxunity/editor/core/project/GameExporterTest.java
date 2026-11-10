package io.github.ldev22live.gdxunity.editor.core.project;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Game export (Phase 18-C, TD-046) against a fake app image and a small project, headless. */
class GameExporterTest {

    @TempDir
    Path temp;

    private Path projectDir;
    private Path template;
    private EditorProject project;

    @BeforeEach
    void setUp() throws IOException {
        template = temp.resolve("image");
        write(template.resolve("ForgeroomEditor.exe"), "editor exe");
        write(template.resolve("ForgeroomGame.exe"), "game exe");
        write(template.resolve("runtime/bin/java.dll"), "jvm");
        write(template.resolve("app/engine-desktop-1.0.0.jar"), "engine");
        write(template.resolve("app/gdx-1.14.2.jar"), "gdx");
        write(template.resolve("app/editor-core-1.0.0.jar"), "editor");
        write(template.resolve("app/kotlin-compiler-embeddable-2.0.21.jar"), "compiler");
        write(template.resolve("app/kotlin-stdlib-2.0.21.jar"), "stdlib");
        write(template.resolve("app/ForgeroomEditor.cfg"), "editor cfg");
        write(template.resolve("app/ForgeroomGame.cfg"), "game cfg");
        write(template.resolve("app/sample-project/gdxunity.project"), "sample");

        projectDir = temp.resolve("project");
        write(projectDir.resolve("gdxunity.project"), "format=1\nname=Editor Name\n");
        write(projectDir.resolve("scenes/level one.json"), "{}");
        write(projectDir.resolve("textures/hero.png"), "png");
        write(projectDir.resolve("graphs/Door.graph"), "{}");
        write(projectDir.resolve("workspace_layout.json"), "layout");
        write(projectDir.resolve("editor_theme.json"), "theme");
        write(projectDir.resolve(".hidden/secret"), "secret");
        write(projectDir.resolve("build/other/junk.bin"), "junk");
        project = new EditorProject(projectDir.toFile());
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private GameExporter.Result export(String scene, String name, Path out) throws IOException {
        return GameExporter.export(project, scene, name, template.toFile(), out.toFile());
    }

    @Test
    void shouldBuildAGameFolderWithTheRenamedLauncherAndRuntime() throws IOException {
        Path out = temp.resolve("Space Hopper");

        GameExporter.Result result = export("scenes/level one.json", "Space Hopper!", out);

        assertEquals(out.resolve("Space Hopper.exe").toFile(), result.executable());
        assertEquals("game exe", Files.readString(out.resolve("Space Hopper.exe")));
        assertEquals("game cfg", Files.readString(out.resolve("app/Space Hopper.cfg")), "the launcher finds its config by its own name");
        assertTrue(Files.isRegularFile(out.resolve("runtime/bin/java.dll")));
        assertTrue(Files.isRegularFile(out.resolve("app/engine-desktop-1.0.0.jar")));
        assertTrue(Files.isRegularFile(out.resolve("app/gdx-1.14.2.jar")));
    }

    @Test
    void shouldLeaveTheEditorOutOfTheGame() throws IOException {
        Path out = temp.resolve("game");

        export("scenes/level one.json", "G", out);

        assertFalse(Files.exists(out.resolve("ForgeroomEditor.exe")));
        assertFalse(Files.exists(out.resolve("app/editor-core-1.0.0.jar")));
        assertFalse(Files.exists(out.resolve("app/kotlin-compiler-embeddable-2.0.21.jar")), "the compiler is for the editor");
        assertTrue(Files.isRegularFile(out.resolve("app/kotlin-stdlib-2.0.21.jar")), "Kotlin scripts still need the standard library");
        assertFalse(Files.exists(out.resolve("app/ForgeroomEditor.cfg")));
        assertFalse(Files.exists(out.resolve("app/ForgeroomGame.cfg")), "only the renamed config remains");
        assertFalse(Files.exists(out.resolve("app/sample-project")));
    }

    @Test
    void shouldCopyScenesAndAssetsButNotEditorFilesHiddenFolderOrOtherBuildOutput() throws IOException {
        Path out = temp.resolve("game");

        GameExporter.Result result = export("scenes/level one.json", "G", out);

        Path game = out.resolve("app/game");
        assertTrue(Files.isRegularFile(game.resolve("scenes/level one.json")));
        assertTrue(Files.isRegularFile(game.resolve("textures/hero.png")));
        assertTrue(Files.isRegularFile(game.resolve("graphs/Door.graph")));
        assertFalse(Files.exists(game.resolve("workspace_layout.json")));
        assertFalse(Files.exists(game.resolve("editor_theme.json")));
        assertFalse(Files.exists(game.resolve(".hidden")));
        assertFalse(Files.exists(game.resolve("build/other")));
        assertEquals(3, result.projectFiles());
    }

    @Test
    void shouldWriteTheNameAndStartSceneIntoTheGamesProjectFile() throws IOException {
        Path out = temp.resolve("game");

        export("scenes\\level one.json", "Space Hopper", out);

        Properties properties = new Properties();
        try (var reader = new InputStreamReader(Files.newInputStream(out.resolve("app/game/gdxunity.project")), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        assertEquals("Space Hopper", properties.getProperty("name"));
        assertEquals("scenes/level one.json", properties.getProperty("startScene"), "back slashes become forward slashes");
        assertTrue(Files.readString(projectDir.resolve("gdxunity.project")).contains("Editor Name"), "the source project is untouched");
    }

    @Test
    void shouldShipCompiledScriptsButNotScriptSources() throws IOException {
        write(projectDir.resolve("scripts/Hero.java"), "class Hero {}");
        write(projectDir.resolve("build/scripts/Hero.class"), "bytes");
        Path out = temp.resolve("game");

        export("scenes/level one.json", "G", out);

        assertTrue(Files.isRegularFile(out.resolve("app/game/build/scripts/Hero.class")));
        assertFalse(Files.exists(out.resolve("app/game/scripts")));
    }

    @Test
    void shouldRefuseScriptsThatWereNotCompiled() throws IOException {
        write(projectDir.resolve("scripts/Hero.java"), "class Hero {}");

        IOException error = assertThrows(IOException.class, () -> export("scenes/level one.json", "G", temp.resolve("game")));

        assertTrue(error.getMessage().contains("Compile the scripts"), error.getMessage());
        assertFalse(Files.exists(temp.resolve("game")), "nothing is written when the export is refused");
    }

    @Test
    void shouldRefuseAMissingOrForeignStartScene() {
        assertTrue(assertThrows(IOException.class, () -> export("scenes/none.json", "G", temp.resolve("a"))).getMessage()
            .contains("does not exist"));
        assertTrue(assertThrows(IOException.class, () -> export("../outside.json", "G", temp.resolve("b"))).getMessage()
            .contains("inside the project"));
        assertTrue(assertThrows(IOException.class, () -> export(" ", "G", temp.resolve("c"))).getMessage()
            .contains("No start scene"));
    }

    @Test
    void shouldRefuseAnOutputFolderThatHasFilesOrIsInsideTheProject() throws IOException {
        write(temp.resolve("taken/readme.txt"), "x");

        assertTrue(assertThrows(IOException.class, () -> export("scenes/level one.json", "G", temp.resolve("taken"))).getMessage()
            .contains("not empty"));
        assertEquals("x", Files.readString(temp.resolve("taken/readme.txt")), "existing files are never touched");
        assertTrue(assertThrows(IOException.class, () -> export("scenes/level one.json", "G", projectDir.resolve("out"))).getMessage()
            .contains("outside the project"));
    }

    @Test
    void shouldAcceptAnEmptyExistingOutputFolder() throws IOException {
        Files.createDirectories(temp.resolve("empty"));

        export("scenes/level one.json", "G", temp.resolve("empty"));

        assertTrue(Files.isRegularFile(temp.resolve("empty/G.exe")));
    }

    @Test
    void shouldExplainWhenThereIsNoPackagedRuntime() {
        IOException none = assertThrows(IOException.class,
            () -> GameExporter.export(project, "scenes/level one.json", "G", null, temp.resolve("x").toFile()));
        assertTrue(none.getMessage().contains("packaged editor"), none.getMessage());

        IOException notAnImage = assertThrows(IOException.class,
            () -> GameExporter.export(project, "scenes/level one.json", "G", projectDir.toFile(), temp.resolve("y").toFile()));
        assertTrue(notAnImage.getMessage().contains("game launcher"), notAnImage.getMessage());
    }

    @Test
    void shouldNameTheGameFileSafely() {
        assertEquals("SpaceHopper 2", GameExporter.safeName("Space/Hopper: 2"));
        assertEquals("Game", GameExporter.safeName("???"));
        assertEquals("Game", GameExporter.safeName(null));
    }

    @Test
    void shouldLocateTheTemplateFromTheLauncherOrTheOverrideProperty() {
        String savedOverride = System.getProperty(GameExporter.TEMPLATE_PROPERTY);
        String savedLauncher = System.getProperty(GameExporter.LAUNCHER_PATH_PROPERTY);
        try {
            System.clearProperty(GameExporter.TEMPLATE_PROPERTY);
            System.clearProperty(GameExporter.LAUNCHER_PATH_PROPERTY);
            assertNull(GameExporter.locateTemplate(), "a development run has no packaged runtime");

            File launcher = template.resolve("ForgeroomEditor.exe").toFile();
            System.setProperty(GameExporter.LAUNCHER_PATH_PROPERTY, launcher.getPath());
            assertEquals(template.toAbsolutePath().toFile(), GameExporter.locateTemplate());

            System.setProperty(GameExporter.TEMPLATE_PROPERTY, "other");
            assertEquals(new File("other"), GameExporter.locateTemplate(), "the override wins");
        } finally {
            restore(GameExporter.TEMPLATE_PROPERTY, savedOverride);
            restore(GameExporter.LAUNCHER_PATH_PROPERTY, savedLauncher);
        }
    }

    private static void restore(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    @Test
    void shouldTakeNameAndStartSceneFromTheProjectFileWhenTheCommandGetsNone() throws IOException {
        write(projectDir.resolve("gdxunity.project"), "format=1\nname=Door Game\nstartScene=scenes/level one.json\n");
        Path out = temp.resolve("game");

        GameExporter.Result result = GameExportCommand.run(project, null, template.toFile(), out.toFile());

        assertEquals("Door Game.exe", result.executable().getName());
    }

    @Test
    void shouldNameTheGameAfterTheProjectFolderWhenTheFileHasNoName() throws IOException {
        write(projectDir.resolve("gdxunity.project"), "format=1\n");

        GameExporter.Result result = GameExportCommand.run(project, "scenes/level one.json", template.toFile(), temp.resolve("game").toFile());

        assertEquals("project.exe", result.executable().getName());
    }

    @Test
    void shouldAskForAStartSceneWhenNeitherTheCallerNorTheProjectFileGivesOne() {
        IOException error = assertThrows(IOException.class,
            () -> GameExportCommand.run(project, null, template.toFile(), temp.resolve("game").toFile()));

        assertTrue(error.getMessage().contains("No start scene"), error.getMessage());
    }

    @Test
    void shouldCompileScriptsBeforeExportingAndShipTheClasses() throws IOException {
        write(projectDir.resolve("scripts/Hero.java"), "public class Hero {}\n");
        Path out = temp.resolve("game");

        GameExportCommand.run(project, "scenes/level one.json", template.toFile(), out.toFile());

        assertTrue(Files.isRegularFile(out.resolve("app/game/build/scripts/Hero.class")), "the script was compiled by the export");
    }

    @Test
    void shouldListCompilerErrorsAndWriteNothingWhenAScriptDoesNotCompile() throws IOException {
        write(projectDir.resolve("scripts/Broken.java"), "public class Broken { this is not java }\n");

        IOException error = assertThrows(IOException.class,
            () -> GameExportCommand.run(project, "scenes/level one.json", template.toFile(), temp.resolve("game").toFile()));

        assertTrue(error.getMessage().contains("do not compile"), error.getMessage());
        assertTrue(error.getMessage().contains("Broken.java"), error.getMessage());
        assertFalse(Files.exists(temp.resolve("game")));
    }

    // --- Linux and macOS app image layouts (TD-047): fake images, because only the Windows layout has been built here ---

    private Path linuxTemplate() throws IOException {
        Path root = temp.resolve("linux-image");
        write(root.resolve("bin/ForgeroomEditor"), "editor launcher");
        write(root.resolve("bin/ForgeroomGame"), "game launcher");
        write(root.resolve("lib/runtime/bin/java"), "jvm");
        write(root.resolve("lib/app/engine-desktop-1.0.0.jar"), "engine");
        write(root.resolve("lib/app/editor-core-1.0.0.jar"), "editor");
        write(root.resolve("lib/app/ForgeroomEditor.cfg"), "editor cfg");
        write(root.resolve("lib/app/ForgeroomGame.cfg"), "game cfg");
        write(root.resolve("lib/libapplauncher.so"), "launcher library");
        write(root.resolve("lib/ForgeroomEditor.png"), "editor icon");
        write(root.resolve("lib/ForgeroomGame.png"), "game icon");
        return root;
    }

    private Path macTemplate() throws IOException {
        Path root = temp.resolve("ForgeroomEditor.app");
        write(root.resolve("Contents/MacOS/ForgeroomEditor"), "editor launcher");
        write(root.resolve("Contents/MacOS/ForgeroomGame"), "game launcher");
        write(root.resolve("Contents/runtime/Contents/Home/bin/java"), "jvm");
        write(root.resolve("Contents/app/engine-desktop-1.0.0.jar"), "engine");
        write(root.resolve("Contents/app/editor-core-1.0.0.jar"), "editor");
        write(root.resolve("Contents/app/ForgeroomEditor.cfg"), "editor cfg");
        write(root.resolve("Contents/app/ForgeroomGame.cfg"), "game cfg");
        write(root.resolve("Contents/PkgInfo"), "APPL????");
        write(root.resolve("Contents/Resources/ForgeroomEditor.icns"), "editor icon");
        write(root.resolve("Contents/Resources/ForgeroomGame.icns"), "game icon");
        write(root.resolve("Contents/Info.plist"), """
            <plist version="1.0"><dict>
              <key>CFBundleExecutable</key>
              <string>ForgeroomEditor</string>
              <key>CFBundleName</key>
              <string>ForgeroomEditor</string>
              <key>CFBundleIdentifier</key>
              <string>forgeroom.ForgeroomEditor</string>
              <key>CFBundleIconFile</key>
              <string>ForgeroomEditor.icns</string>
              <key>CFBundleVersion</key>
              <string>1.0.0</string>
            </dict></plist>
            """);
        return root;
    }

    @Test
    void shouldExportFromALinuxAppImageWithTheLaunchersAndLibrariesRenamed() throws IOException {
        Path out = temp.resolve("linux-game");

        GameExporter.Result result = GameExporter.export(project, "scenes/level one.json", "Space Hopper", linuxTemplate().toFile(), out.toFile());

        assertEquals(out.resolve("bin/Space Hopper").toFile(), result.executable());
        assertEquals("game launcher", Files.readString(out.resolve("bin/Space Hopper")));
        assertEquals("game cfg", Files.readString(out.resolve("lib/app/Space Hopper.cfg")));
        assertEquals("game icon", Files.readString(out.resolve("lib/Space Hopper.png")), "the icon follows the launcher's name");
        assertEquals("launcher library", Files.readString(out.resolve("lib/libapplauncher.so")));
        assertTrue(Files.isRegularFile(out.resolve("lib/runtime/bin/java")));
        assertTrue(Files.isRegularFile(out.resolve("lib/app/engine-desktop-1.0.0.jar")));
        assertTrue(Files.isRegularFile(out.resolve("lib/app/game/scenes/level one.json")));
        assertFalse(Files.exists(out.resolve("bin/ForgeroomEditor")));
        assertFalse(Files.exists(out.resolve("lib/ForgeroomEditor.png")));
        assertFalse(Files.exists(out.resolve("lib/app/editor-core-1.0.0.jar")));
        assertFalse(Files.exists(out.resolve("lib/app/ForgeroomEditor.cfg")));
        assertFalse(Files.exists(out.resolve("lib/app/ForgeroomGame.cfg")));
    }

    @Test
    void shouldKeepTheLauncherExecutableOnALinuxImage() throws IOException {
        org.junit.jupiter.api.Assumptions.assumeTrue(java.nio.file.FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        Path template = linuxTemplate();
        Files.setPosixFilePermissions(template.resolve("bin/ForgeroomGame"), java.nio.file.attribute.PosixFilePermissions.fromString("rwxr-xr-x"));
        Path out = temp.resolve("linux-game");

        GameExporter.export(project, "scenes/level one.json", "G", template.toFile(), out.toFile());

        assertTrue(Files.isExecutable(out.resolve("bin/G")), "a launcher that lost its execute bit would not start");
    }

    @Test
    void shouldExportFromAMacAppBundleIntoANamedBundle() throws IOException {
        Path out = temp.resolve("mac-game");

        GameExporter.Result result = GameExporter.export(project, "scenes/level one.json", "Space Hopper", macTemplate().toFile(), out.toFile());

        Path bundle = out.resolve("Space Hopper.app");
        assertEquals(bundle.resolve("Contents/MacOS/Space Hopper").toFile(), result.executable());
        assertEquals("game launcher", Files.readString(bundle.resolve("Contents/MacOS/Space Hopper")));
        assertEquals("game cfg", Files.readString(bundle.resolve("Contents/app/Space Hopper.cfg")));
        assertEquals("game icon", Files.readString(bundle.resolve("Contents/Resources/Space Hopper.icns")));
        assertEquals("APPL????", Files.readString(bundle.resolve("Contents/PkgInfo")));
        assertTrue(Files.isRegularFile(bundle.resolve("Contents/runtime/Contents/Home/bin/java")));
        assertTrue(Files.isRegularFile(bundle.resolve("Contents/app/game/gdxunity.project")));
        assertFalse(Files.exists(bundle.resolve("Contents/MacOS/ForgeroomEditor")));
        assertFalse(Files.exists(bundle.resolve("Contents/Resources/ForgeroomEditor.icns")));
        assertFalse(Files.exists(bundle.resolve("Contents/app/editor-core-1.0.0.jar")));
    }

    @Test
    void shouldTurnTheEditorsInfoPlistIntoTheGamesWithItsOwnIdentifier() throws IOException {
        Path out = temp.resolve("mac-game");

        GameExporter.export(project, "scenes/level one.json", "Space Hopper!", macTemplate().toFile(), out.toFile());

        String plist = Files.readString(out.resolve("Space Hopper.app/Contents/Info.plist"));
        assertTrue(plist.matches("(?s).*<key>CFBundleExecutable</key>\\s*<string>Space Hopper</string>.*"), plist);
        assertTrue(plist.matches("(?s).*<key>CFBundleName</key>\\s*<string>Space Hopper</string>.*"), plist);
        assertTrue(plist.matches("(?s).*<key>CFBundleIdentifier</key>\\s*<string>forgeroom\\.game\\.spacehopper</string>.*"), plist);
        assertTrue(plist.matches("(?s).*<key>CFBundleIconFile</key>\\s*<string>Space Hopper\\.icns</string>.*"), plist);
        assertTrue(plist.contains("<string>1.0.0</string>"), "keys that are not about the name stay: " + plist);
        assertFalse(plist.contains("ForgeroomEditor"), plist);
    }

    @Test
    void shouldLeaveAPlistWithoutTheNameKeysAlone() {
        String plist = "<plist><dict><key>CFBundleVersion</key><string>2</string></dict></plist>";

        assertEquals(plist, GameExporter.rewriteInfoPlist(plist, "G"));
    }

    @Test
    void shouldFindTheImageRootFromTheLauncherPathOfEachOperatingSystem() {
        assertEquals(new File("C:/Apps/Forgeroom"), GameExporter.imageRootOf(new File("C:/Apps/Forgeroom/ForgeroomEditor.exe")));
        assertEquals(new File("/opt/forgeroom"), GameExporter.imageRootOf(new File("/opt/forgeroom/bin/ForgeroomEditor")));
        assertEquals(new File("/Applications/ForgeroomEditor.app"),
            GameExporter.imageRootOf(new File("/Applications/ForgeroomEditor.app/Contents/MacOS/ForgeroomEditor")));
    }

    @Test
    void shouldNameAllThreeLayoutsWhenTheFolderIsNoAppImage() {
        IOException error = assertThrows(IOException.class,
            () -> GameExporter.export(project, "scenes/level one.json", "G", projectDir.toFile(), temp.resolve("x").toFile()));

        assertTrue(error.getMessage().contains("Windows") && error.getMessage().contains("Linux") && error.getMessage().contains("macOS"),
            error.getMessage());
    }

    // --- Window icon ---

    @Test
    void shouldCarryTheProjectsWindowIconIntoTheGamesProjectFile() throws IOException {
        write(projectDir.resolve("art/icon.png"), "png");
        Path out = temp.resolve("game");

        GameExporter.export(project, "scenes/level one.json", "G", "art\\icon.png", template.toFile(), out.toFile());

        Properties properties = new Properties();
        try (var reader = new InputStreamReader(Files.newInputStream(out.resolve("app/game/gdxunity.project")), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        assertEquals("art/icon.png", properties.getProperty("windowIcon"));
        assertTrue(Files.isRegularFile(out.resolve("app/game/art/icon.png")), "the icon is part of the project, so it is copied");
    }

    @Test
    void shouldWriteNoWindowIconKeyWhenTheProjectHasNone() throws IOException {
        Path out = temp.resolve("game");

        export("scenes/level one.json", "G", out);

        assertFalse(Files.readString(out.resolve("app/game/gdxunity.project")).contains("windowIcon"));
    }

    @Test
    void shouldRefuseAWindowIconThatIsMissingOutsideTheProjectOrNotAPng() throws IOException {
        write(projectDir.resolve("art/icon.ico"), "ico");

        assertTrue(assertThrows(IOException.class, () -> GameExporter.export(project, "scenes/level one.json", "G", "art/none.png",
            template.toFile(), temp.resolve("a").toFile())).getMessage().contains("does not exist"));
        assertTrue(assertThrows(IOException.class, () -> GameExporter.export(project, "scenes/level one.json", "G", "../icon.png",
            template.toFile(), temp.resolve("b").toFile())).getMessage().contains("inside the project"));
        assertTrue(assertThrows(IOException.class, () -> GameExporter.export(project, "scenes/level one.json", "G", "art/icon.ico",
            template.toFile(), temp.resolve("c").toFile())).getMessage().contains(".png"));
        assertFalse(Files.exists(temp.resolve("a")), "nothing is written when the export is refused");
    }

    @Test
    void shouldExportTheWindowIconNamedInTheProjectFile() throws IOException {
        write(projectDir.resolve("art/icon.png"), "png");
        write(projectDir.resolve("gdxunity.project"), "format=1\nname=Door Game\nstartScene=scenes/level one.json\nwindowIcon=art/icon.png\n");
        Path out = temp.resolve("game");

        GameExportCommand.run(project, null, template.toFile(), out.toFile());

        assertTrue(Files.readString(out.resolve("app/game/gdxunity.project")).contains("windowIcon=art/icon.png"));
    }
}
