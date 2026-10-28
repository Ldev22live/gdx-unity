package io.github.ldev22live.gdxunity.editor.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

/**
 * Launcher for the desktop GdxUnity Editor application.
 */
public class EditorLauncher {
    public static void main(String[] args) {
        if (EditorStartupHelper.startNewJvmIfRequired()) return;
        createApplication();
    }

    private static Lwjgl3Application createApplication() {
        return new Lwjgl3Application(new EditorApplication(), getDefaultConfiguration());
    }

    public static Lwjgl3ApplicationConfiguration getDefaultConfiguration() {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("GdxUnity Editor");
        configuration.useVsync(true);
        configuration.setForegroundFPS(60);

        // Responsive window resolution detection:
        // Set sensible windowed fallback size based on primary monitor or safe default,
        // and configure maximized mode to respect OS taskbar/work areas.
        int defaultWidth = 1440;
        int defaultHeight = 900;
        try {
            com.badlogic.gdx.Graphics.DisplayMode displayMode = Lwjgl3ApplicationConfiguration.getDisplayMode();
            if (displayMode != null && displayMode.width > 0 && displayMode.height > 0) {
                // Set fallback to ~85% of display dimensions to prevent overflow on non-maximized startup
                defaultWidth = Math.max(1024, (int) (displayMode.width * 0.85f));
                defaultHeight = Math.max(640, (int) (displayMode.height * 0.85f));
            }
        } catch (Throwable ignored) {
            // Safe fallback if display query fails before graphics initialization
        }

        configuration.setWindowedMode(defaultWidth, defaultHeight);
        configuration.setMaximized(true);
        configuration.setResizable(true);
        configuration.setWindowSizeLimits(1024, 640, -1, -1);
        return configuration;
    }
}
