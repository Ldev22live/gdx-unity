package io.github.ldev22live.gdxunity.editor.core.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorProjectLocatorTest {

    @TempDir
    Path temp;

    private File project(String name) throws IOException {
        Path root = Files.createDirectories(temp.resolve(name));
        Files.writeString(root.resolve(EditorProject.MARKER_FILE), "format=1\n");
        return root.toFile();
    }

    private File folder(String name) throws IOException {
        return Files.createDirectories(temp.resolve(name)).toFile();
    }

    @Test
    void shouldPreferTheCommandLineProjectOverTheWorkingDirectory() throws IOException {
        File requested = project("requested");
        File working = project("working");

        EditorProjectLocator.Result result = EditorProjectLocator.locate(requested, working, folder("projects"), null);

        assertEquals(requested.getAbsoluteFile(), result.project().getRoot());
        assertEquals(EditorProjectLocator.Source.COMMAND_LINE, result.source());
    }

    @Test
    void shouldRejectACommandLineFolderWithoutMarker() throws IOException {
        File plain = folder("plain");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> EditorProjectLocator.locate(plain, project("working"), folder("projects"), null));
        assertTrue(e.getMessage().contains(EditorProject.MARKER_FILE), e.getMessage());
    }

    @Test
    void shouldOpenTheAssetsFolderWhenStartedFromARepositoryCheckout() throws IOException {
        File checkout = folder("checkout");
        File assets = project("checkout/assets");

        EditorProjectLocator.Result result = EditorProjectLocator.locate(null, checkout, folder("projects"), null);

        assertEquals(assets.getAbsoluteFile(), result.project().getRoot());
        assertEquals(EditorProjectLocator.Source.WORKING_DIRECTORY, result.source());
    }

    @Test
    void shouldInstallTheBundledSampleOnceAndReuseItAfterwards() throws IOException {
        File bundled = project("app/sample-project");
        Files.createDirectories(bundled.toPath().resolve("models"));
        Files.writeString(bundled.toPath().resolve("models/Box.gltf"), "{}");
        File projects = temp.resolve("Documents/GdxUnity").toFile();
        File working = folder("install-folder");

        EditorProjectLocator.Result first = EditorProjectLocator.locate(null, working, projects, bundled);
        Path installedModel = first.project().getRoot().toPath().resolve("models/Box.gltf");
        Files.writeString(installedModel, "{\"edited\":true}");
        EditorProjectLocator.Result second = EditorProjectLocator.locate(null, working, projects, bundled);

        assertEquals(EditorProjectLocator.Source.SAMPLE_PROJECT_INSTALLED, first.source());
        assertEquals(new File(projects, EditorProjectLocator.SAMPLE_PROJECT_NAME).getAbsoluteFile(),
            first.project().getRoot());
        assertEquals(EditorProjectLocator.Source.SAMPLE_PROJECT, second.source());
        assertEquals("{\"edited\":true}", Files.readString(installedModel), "user edits survive a restart");
        assertFalse(new File(working, EditorProject.MARKER_FILE).exists(), "nothing written to the working folder");
        try (var siblings = Files.list(projects.toPath())) {
            assertEquals(1, siblings.count(), "no staging folder left behind");
        }
    }

    @Test
    void shouldNotOverwriteAFolderNamedLikeTheSampleThatIsNotAProject() throws IOException {
        File bundled = project("app/sample-project");
        File projects = folder("projects");
        Path existing = Files.createDirectories(projects.toPath().resolve(EditorProjectLocator.SAMPLE_PROJECT_NAME));
        Files.writeString(existing.resolve("notes.txt"), "mine");

        assertThrows(IllegalStateException.class,
            () -> EditorProjectLocator.locate(null, folder("working"), projects, bundled));
        assertEquals("mine", Files.readString(existing.resolve("notes.txt")));
        assertFalse(Files.exists(existing.resolve(EditorProject.MARKER_FILE)));
    }

    @Test
    void shouldFailWithInstructionsWhenNoProjectAndNoBundledSampleExist() throws IOException {
        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> EditorProjectLocator.locate(null, folder("working"), folder("projects"), null));
        assertTrue(e.getMessage().contains("--project"), e.getMessage());
    }

    @Test
    void shouldResolveScriptFoldersInsideTheProject() throws IOException {
        EditorProject project = new EditorProject(project("p"));

        assertEquals(new File(project.getRoot(), "scripts"), project.getScriptsDir());
        assertEquals(new File(new File(project.getRoot(), "build"), "scripts"), project.getScriptsBuildDir());
    }
}
