package io.github.ldev22live.gdxunity.editor.core.project;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlatformDirectoriesTest {

    private static final File HOME = new File("/home/tester").getAbsoluteFile();

    @Test
    void shouldUseRoamingAndLocalAppDataOnWindows() {
        PlatformDirectories dirs = PlatformDirectories.of("Windows 11",
            Map.of("APPDATA", "C:\\Users\\t\\AppData\\Roaming", "LOCALAPPDATA", "C:\\Users\\t\\AppData\\Local"), HOME);

        assertEquals(new File("C:\\Users\\t\\AppData\\Roaming", "GdxUnity"), dirs.settingsDir());
        assertEquals(new File(new File("C:\\Users\\t\\AppData\\Local", "GdxUnity"), "logs"), dirs.logDir());
        assertEquals(new File(new File(HOME, "Documents"), "GdxUnity"), dirs.projectsDir());
    }

    @Test
    void shouldFallBackToUserHomeWhenWindowsVariablesAreMissingOrBlank() {
        PlatformDirectories dirs = PlatformDirectories.of("Windows 10", Map.of("APPDATA", " "), HOME);

        assertEquals(new File(new File(HOME, "AppData" + File.separator + "Roaming"), "GdxUnity"), dirs.settingsDir());
        assertEquals(new File(new File(new File(HOME, "AppData" + File.separator + "Local"), "GdxUnity"), "logs"),
            dirs.logDir());
    }

    @Test
    void shouldUseLibraryFoldersOnMacOs() {
        PlatformDirectories dirs = PlatformDirectories.of("Mac OS X", Map.of("XDG_CONFIG_HOME", "/ignored"), HOME);

        File library = new File(HOME, "Library");
        assertEquals(new File(new File(library, "Application Support"), "GdxUnity"), dirs.settingsDir());
        assertEquals(new File(new File(library, "Logs"), "GdxUnity"), dirs.logDir());
    }

    @Test
    void shouldUseXdgFoldersOnLinux() {
        File config = new File("/xdg/config").getAbsoluteFile();
        File state = new File("/xdg/state").getAbsoluteFile();
        PlatformDirectories dirs = PlatformDirectories.of("Linux",
            Map.of("XDG_CONFIG_HOME", config.getPath(), "XDG_STATE_HOME", state.getPath()), HOME);

        assertEquals(new File(config, "gdxunity"), dirs.settingsDir());
        assertEquals(new File(new File(state, "gdxunity"), "logs"), dirs.logDir());
    }

    @Test
    void shouldIgnoreRelativeXdgFoldersOnLinux() {
        PlatformDirectories dirs = PlatformDirectories.of("Linux",
            Map.of("XDG_CONFIG_HOME", "relative/config", "XDG_STATE_HOME", ""), HOME);

        assertEquals(new File(new File(HOME, ".config"), "gdxunity"), dirs.settingsDir());
        assertEquals(new File(new File(new File(HOME, ".local" + File.separator + "state"), "gdxunity"), "logs"),
            dirs.logDir());
    }
}
