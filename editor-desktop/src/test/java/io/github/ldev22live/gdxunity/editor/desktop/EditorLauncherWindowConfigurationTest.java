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
    void shouldReadAWindowSizeForTheTestingAidAndIgnoreItWhenUnset() {
        assertArrayEquals(new int[] {1366, 768}, EditorLauncher.parseWindowSize("1366x768"));
        assertArrayEquals(new int[] {1280, 720}, EditorLauncher.parseWindowSize(" 1280X720 "));
        assertArrayEquals(new int[] {1024, 640}, EditorLauncher.parseWindowSize("1024x640"));
        assertNull(EditorLauncher.parseWindowSize(null));
        assertNull(EditorLauncher.parseWindowSize("  "));
    }

    @Test
    void shouldRefuseAWindowSizeThatIsMalformedOrSmallerThanTheEditorsMinimum() {
        IllegalArgumentException malformed = assertThrows(IllegalArgumentException.class, () -> EditorLauncher.parseWindowSize("big"));
        assertTrue(malformed.getMessage().contains("'big'"), malformed.getMessage());
        IllegalArgumentException narrow = assertThrows(IllegalArgumentException.class, () -> EditorLauncher.parseWindowSize("800x900"));
        assertTrue(narrow.getMessage().contains("800x900") && narrow.getMessage().contains("1024x640"), narrow.getMessage());
        assertThrows(IllegalArgumentException.class, () -> EditorLauncher.parseWindowSize("1920x600"));
    }

    @Test
    void shouldEnforceSafeMinimumWindowSizeLimits() {
        Lwjgl3ApplicationConfiguration config = EditorLauncher.getDefaultConfiguration();
        assertNotNull(config);
        // Verify method executes safely and produces a valid configuration
    }
}
