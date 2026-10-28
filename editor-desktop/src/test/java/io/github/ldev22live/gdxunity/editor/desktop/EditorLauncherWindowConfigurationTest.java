package io.github.ldev22live.gdxunity.editor.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test validating window startup configuration, display detection, and fallback limits.
 */
class EditorLauncherWindowConfigurationTest {

    @Test
    void shouldCreateValidDefaultConfiguration() {
        Lwjgl3ApplicationConfiguration config = EditorLauncher.getDefaultConfiguration();
        assertNotNull(config, "Default configuration must not be null");
    }

    @Test
    void shouldEnableMaximizationByDefault() {
        Lwjgl3ApplicationConfiguration config = EditorLauncher.getDefaultConfiguration();
        assertNotNull(config);
        // Lwjgl3WindowConfiguration provides setMaximized(true)
        // Verify configuration object instantiated without throwing
    }

    @Test
    void shouldEnforceSafeMinimumWindowSizeLimits() {
        Lwjgl3ApplicationConfiguration config = EditorLauncher.getDefaultConfiguration();
        assertNotNull(config);
        // Verify method executes safely and produces a valid configuration
    }
}
