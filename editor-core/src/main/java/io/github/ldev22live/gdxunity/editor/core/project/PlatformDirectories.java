package io.github.ldev22live.gdxunity.editor.core.project;

import java.io.File;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Per-user folders for editor settings, logs and projects, following each desktop OS's convention (TD-033).
 * Nothing is created here; callers create a folder before writing into it.
 *
 * <ul>
 *   <li>Windows: settings in {@code %APPDATA%\GdxUnity}, logs in {@code %LOCALAPPDATA%\GdxUnity\logs}</li>
 *   <li>macOS: {@code ~/Library/Application Support/GdxUnity} and {@code ~/Library/Logs/GdxUnity}</li>
 *   <li>Linux and other Unix: {@code $XDG_CONFIG_HOME/gdxunity} and {@code $XDG_STATE_HOME/gdxunity/logs}, with the
 *       XDG defaults {@code ~/.config} and {@code ~/.local/state}</li>
 *   <li>Projects on every OS: {@code ~/Documents/GdxUnity}</li>
 * </ul>
 *
 * @param settingsDir editor preferences such as theme and layout
 * @param logDir      crash logs
 * @param projectsDir default location for the user's projects, including the sample project
 */
public record PlatformDirectories(File settingsDir, File logDir, File projectsDir) {
    /** Folder name on Windows and macOS. */
    static final String APP_FOLDER = "GdxUnity";
    /** Folder name on Linux, where XDG folders are lower case by convention. */
    static final String UNIX_APP_FOLDER = "gdxunity";

    public PlatformDirectories {
        Objects.requireNonNull(settingsDir, "settingsDir cannot be null");
        Objects.requireNonNull(logDir, "logDir cannot be null");
        Objects.requireNonNull(projectsDir, "projectsDir cannot be null");
    }

    /** Folders for the OS, environment and user home of the running JVM. */
    public static PlatformDirectories forCurrentPlatform() {
        return of(System.getProperty("os.name"), System.getenv(), new File(System.getProperty("user.home")));
    }

    /**
     * Folders for the given OS name ({@code os.name} format), environment variables and user home.
     * Blank or missing variables fall back to the OS default; relative XDG values are ignored, as the XDG
     * specification requires.
     */
    public static PlatformDirectories of(String osName, Map<String, String> environment, File userHome) {
        Objects.requireNonNull(osName, "osName cannot be null");
        Objects.requireNonNull(environment, "environment cannot be null");
        Objects.requireNonNull(userHome, "userHome cannot be null");
        String os = osName.toLowerCase(Locale.ROOT);
        File projects = new File(new File(userHome, "Documents"), APP_FOLDER);
        if (os.contains("win")) {
            File roaming = folderFromEnvironment(environment, "APPDATA", false,
                new File(userHome, "AppData" + File.separator + "Roaming"));
            File local = folderFromEnvironment(environment, "LOCALAPPDATA", false,
                new File(userHome, "AppData" + File.separator + "Local"));
            return new PlatformDirectories(new File(roaming, APP_FOLDER),
                new File(new File(local, APP_FOLDER), "logs"), projects);
        }
        if (os.contains("mac")) {
            File library = new File(userHome, "Library");
            return new PlatformDirectories(new File(new File(library, "Application Support"), APP_FOLDER),
                new File(new File(library, "Logs"), APP_FOLDER), projects);
        }
        File config = folderFromEnvironment(environment, "XDG_CONFIG_HOME", true, new File(userHome, ".config"));
        File state = folderFromEnvironment(environment, "XDG_STATE_HOME", true,
            new File(userHome, ".local" + File.separator + "state"));
        return new PlatformDirectories(new File(config, UNIX_APP_FOLDER),
            new File(new File(state, UNIX_APP_FOLDER), "logs"), projects);
    }

    private static File folderFromEnvironment(Map<String, String> environment, String name, boolean absoluteOnly,
                                              File fallback) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        File folder = new File(value);
        return absoluteOnly && !folder.isAbsolute() ? fallback : folder;
    }
}
