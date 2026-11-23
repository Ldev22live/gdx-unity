package io.github.ldev22live.gdxunity.editor.core.project;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Decides which project the editor opens at startup (TD-033), in this order:
 * <ol>
 *   <li>the folder given with {@code --project}; it must be a project</li>
 *   <li>the working directory, or its {@code assets} folder, when it is a project (development runs)</li>
 *   <li>{@value #SAMPLE_PROJECT_NAME} in the user's projects folder, copied there from the sample bundled with the app
 *       the first time; an existing folder of that name is never overwritten</li>
 * </ol>
 * Nothing is guessed: when no rule applies, {@link #locate} throws with a message that says what to do.
 */
public final class EditorProjectLocator {
    /** Folder name of the per-user sample project. */
    public static final String SAMPLE_PROJECT_NAME = "Sample Project";
    /** Subfolder checked when the working directory is a repository checkout. */
    static final String ASSETS_FOLDER = "assets";

    /** How the project was found, for the Output Log. */
    public enum Source {
        COMMAND_LINE, WORKING_DIRECTORY, SAMPLE_PROJECT, SAMPLE_PROJECT_INSTALLED
    }

    /** The located project and the rule that found it. */
    public record Result(EditorProject project, Source source) {
        public Result {
            Objects.requireNonNull(project, "project cannot be null");
            Objects.requireNonNull(source, "source cannot be null");
        }
    }

    private EditorProjectLocator() {}

    /**
     * @param requestedProject  folder from {@code --project}, or null
     * @param workingDirectory  the JVM's working directory
     * @param projectsDir       the user's projects folder ({@link PlatformDirectories#projectsDir()})
     * @param bundledSample     the sample project shipped with the app, or null when there is none (development)
     * @throws IllegalArgumentException if {@code requestedProject} is not a project
     * @throws IllegalStateException    if no project can be found or the sample cannot be installed
     */
    public static Result locate(File requestedProject, File workingDirectory, File projectsDir, File bundledSample) {
        Objects.requireNonNull(workingDirectory, "workingDirectory cannot be null");
        Objects.requireNonNull(projectsDir, "projectsDir cannot be null");
        if (requestedProject != null) {
            return new Result(new EditorProject(requestedProject), Source.COMMAND_LINE);
        }
        for (File candidate : List.of(workingDirectory, new File(workingDirectory, ASSETS_FOLDER))) {
            if (EditorProject.isProject(candidate)) {
                return new Result(new EditorProject(candidate), Source.WORKING_DIRECTORY);
            }
        }
        File sample = new File(projectsDir, SAMPLE_PROJECT_NAME);
        if (EditorProject.isProject(sample)) {
            return new Result(new EditorProject(sample), Source.SAMPLE_PROJECT);
        }
        if (sample.exists()) {
            throw new IllegalStateException("'" + sample.getAbsolutePath() + "' exists but is not a Forgeroom project "
                + "(no " + EditorProject.MARKER_FILE + " file); it was left untouched. Rename it, or start the editor "
                + "with --project <folder>.");
        }
        if (bundledSample == null || !EditorProject.isProject(bundledSample)) {
            throw new IllegalStateException("No project to open: the working directory '"
                + workingDirectory.getAbsolutePath() + "' is not a Forgeroom project and no sample project is bundled. "
                + "Start the editor with --project <folder>.");
        }
        installSample(bundledSample.toPath(), sample.toPath());
        return new Result(new EditorProject(sample), Source.SAMPLE_PROJECT_INSTALLED);
    }

    /** Copies into a temporary sibling first, so a failed copy never leaves a half-filled sample project. */
    private static void installSample(Path source, Path target) {
        Path staging = target.resolveSibling(target.getFileName() + ".installing-" + System.nanoTime());
        try {
            Files.createDirectories(target.getParent());
            try (Stream<Path> files = Files.walk(source)) {
                for (Path file : (Iterable<Path>) files::iterator) {
                    Path copy = staging.resolve(source.relativize(file).toString());
                    if (Files.isDirectory(file)) {
                        Files.createDirectories(copy);
                    } else {
                        Files.copy(file, copy, StandardCopyOption.COPY_ATTRIBUTES);
                    }
                }
            }
            Files.move(staging, target);
        } catch (IOException e) {
            IllegalStateException failure = new IllegalStateException("Could not install the sample project into '"
                + target + "': " + e.getMessage(), e);
            deleteStagingFolder(staging, failure);
            throw failure;
        }
    }

    /** Removes the staging folder; problems are attached to {@code failure} instead of hiding it. */
    private static void deleteStagingFolder(Path folder, IllegalStateException failure) {
        if (!Files.exists(folder)) {
            return;
        }
        try (Stream<Path> files = Files.walk(folder)) {
            for (Path file : (Iterable<Path>) files.sorted(Comparator.reverseOrder())::iterator) {
                Files.deleteIfExists(file);
            }
        } catch (IOException | UncheckedIOException e) {
            failure.addSuppressed(e);
        }
    }
}
