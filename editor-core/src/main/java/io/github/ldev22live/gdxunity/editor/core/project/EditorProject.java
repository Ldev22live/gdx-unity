package io.github.ldev22live.gdxunity.editor.core.project;

import java.io.File;
import java.util.Objects;

/**
 * A project folder opened by the editor (TD-033): it holds the assets, scenes and scripts, and is marked by a
 * {@value #MARKER_FILE} file. Asset paths in scenes are relative to {@link #getRoot()}.
 */
public final class EditorProject {
    /** File whose presence makes a folder a Forgeroom project. */
    public static final String MARKER_FILE = "gdxunity.project";
    /** Script sources, relative to the project root. */
    static final String SCRIPTS_FOLDER = "scripts";
    /** Compiled scripts, relative to the project root. */
    static final String SCRIPTS_BUILD_FOLDER = "build" + File.separator + "scripts";

    private final File root;

    /**
     * @throws IllegalArgumentException if {@code root} has no {@value #MARKER_FILE} file
     */
    public EditorProject(File root) {
        Objects.requireNonNull(root, "root cannot be null");
        if (!isProject(root)) {
            throw new IllegalArgumentException("Not a Forgeroom project (no " + MARKER_FILE + " file): "
                + root.getAbsolutePath());
        }
        this.root = root.getAbsoluteFile();
    }

    /** True when {@code folder} is a directory containing a {@value #MARKER_FILE} file. */
    public static boolean isProject(File folder) {
        return folder != null && folder.isDirectory() && new File(folder, MARKER_FILE).isFile();
    }

    /** Absolute project folder; also the asset root. */
    public File getRoot() {
        return root;
    }

    /** Folder of Java and Kotlin script sources. */
    public File getScriptsDir() {
        return new File(root, SCRIPTS_FOLDER);
    }

    /** Folder the script compiler writes classes to. */
    public File getScriptsBuildDir() {
        return new File(root, SCRIPTS_BUILD_FOLDER);
    }

    @Override
    public String toString() {
        return root.getPath();
    }
}
