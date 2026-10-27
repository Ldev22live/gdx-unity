package io.github.ldev22live.gdxunity.editor.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3NativesLoader;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Startup helper for LWJGL3 cross-platform compatibility on Windows, macOS, and Linux.
 */
public class EditorStartupHelper {
    private static final String JVM_RESTARTED_ARG = "jvmIsRestarted";

    private EditorStartupHelper() {}

    public static boolean startNewJvmIfRequired() {
        String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);

        // Windows LWJGL native extraction fix
        if (osName.contains("windows")) {
            fixWindowsDirectLibraries();
        }

        // Mac thread requirement
        if (!osName.contains("mac")) {
            return false;
        }

        if ("true".equals(System.getProperty(JVM_RESTARTED_ARG))) {
            return false;
        }

        long pid = ManagementFactory.getRuntimeMXBean().getPid();
        List<String> jvmArgs = ManagementFactory.getRuntimeMXBean().getInputArguments();
        boolean hasXStartOnFirstThread = false;
        for (String jvmArg : jvmArgs) {
            if (jvmArg.contains("-XstartOnFirstThread")) {
                hasXStartOnFirstThread = true;
                break;
            }
        }

        if (hasXStartOnFirstThread) {
            return false;
        }

        List<String> cmd = new ArrayList<>();
        String javaHome = System.getProperty("java.home");
        String javaBin = javaHome + File.separator + "bin" + File.separator + "java";
        cmd.add(javaBin);
        cmd.add("-XstartOnFirstThread");
        cmd.add("-D" + JVM_RESTARTED_ARG + "=true");
        cmd.addAll(jvmArgs);
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add(System.getProperty("sun.java.command"));

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(cmd);
            processBuilder.inheritIO();
            Process process = processBuilder.start();
            process.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }

    private static void fixWindowsDirectLibraries() {
        try {
            Lwjgl3NativesLoader.load();
        } catch (Throwable ignored) {
        }
    }
}
