package io.github.ldev22live.gdxunity.editor.desktop;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Starts the editor again on another project (roadmap M1.4, TD-086). The editor opens one project per start, so the template
 * gallery hands a new project over by starting a second editor with {@code --project} and {@code --scene} and then closing this
 * one, after the usual unsaved-changes questions. The command is the one this editor was started with: the packaged launcher when
 * there is one ({@code jpackage.app-path}), otherwise the same Java, JVM options and class path.
 */
public final class EditorRelaunch {
    /** System property the {@code jpackage} launcher sets to its own executable. */
    static final String APP_PATH_PROPERTY = "jpackage.app-path";
    /** A debugger agent cannot listen on the same port twice, so the second editor is started without it. */
    private static final String DEBUG_AGENT_PREFIX = "-agentlib:jdwp";

    /**
     * How this editor was started.
     *
     * @param appPath   the packaged launcher, or null in a development run
     * @param javaHome  {@code java.home}
     * @param jvmArgs   the JVM's own options
     * @param classPath {@code java.class.path}
     * @param windows   whether the Java executable is {@code java.exe}
     */
    record Origin(String appPath, String javaHome, List<String> jvmArgs, String classPath, boolean windows) {
        Origin {
            Objects.requireNonNull(javaHome, "javaHome cannot be null");
            jvmArgs = List.copyOf(jvmArgs);
            Objects.requireNonNull(classPath, "classPath cannot be null");
        }

        /** This process. */
        static Origin current() {
            return new Origin(System.getProperty(APP_PATH_PROPERTY), System.getProperty("java.home"),
                ManagementFactory.getRuntimeMXBean().getInputArguments(), System.getProperty("java.class.path"),
                System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows"));
        }
    }

    private EditorRelaunch() { }

    /** The command that starts an editor on {@code project} with {@code scene} (relative to the project) open. */
    static List<String> command(Origin origin, File project, String scene) {
        Objects.requireNonNull(origin, "origin cannot be null");
        Objects.requireNonNull(project, "project cannot be null");
        Objects.requireNonNull(scene, "scene cannot be null");
        List<String> command = new ArrayList<>();
        if (origin.appPath() != null && !origin.appPath().isBlank()) {
            command.add(origin.appPath());
        } else {
            command.add(origin.javaHome() + File.separator + "bin" + File.separator + (origin.windows() ? "java.exe" : "java"));
            for (String option : origin.jvmArgs()) {
                if (!option.startsWith(DEBUG_AGENT_PREFIX)) {
                    command.add(option);
                }
            }
            command.add("-cp");
            command.add(origin.classPath());
            command.add(EditorLauncher.class.getName());
        }
        command.add("--project");
        command.add(project.getAbsolutePath());
        command.add("--scene");
        command.add(scene);
        return command;
    }

    /**
     * Starts the other editor; it keeps running when this one exits.
     *
     * @throws IOException if it cannot be started; this editor should then stay open
     */
    static void start(File project, String scene) throws IOException {
        new ProcessBuilder(command(Origin.current(), project, scene)).inheritIO().start();
    }
}
