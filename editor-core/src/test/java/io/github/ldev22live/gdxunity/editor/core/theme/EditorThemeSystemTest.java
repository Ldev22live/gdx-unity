package io.github.ldev22live.gdxunity.editor.core.theme;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests verifying EditorThemeMode, EditorThemePalette tokens,
 * theme switching, listeners, and persistence in EditorThemeManager.
 */
class EditorThemeSystemTest {

    private File tempPrefFile;

    @BeforeEach
    void setUp() throws IOException {
        tempPrefFile = File.createTempFile("editor_theme_test_", ".json");
    }

    @AfterEach
    void tearDown() {
        if (tempPrefFile != null && tempPrefFile.exists()) {
            tempPrefFile.delete();
        }
    }

    @Test
    void testDefaultThemeIsDark() {
        if (tempPrefFile.exists()) {
            tempPrefFile.delete(); // ensure no file
        }
        EditorThemeManager manager = new EditorThemeManager(tempPrefFile);
        assertEquals(EditorThemeMode.DARK, manager.getCurrentMode());
        assertNotNull(manager.getCurrentPalette());
    }

    @Test
    void testThemeModeParsingAndFallback() {
        assertEquals(EditorThemeMode.DARK, EditorThemeMode.fromId("dark"));
        assertEquals(EditorThemeMode.LIGHT, EditorThemeMode.fromId("light"));
        assertEquals(EditorThemeMode.DARK, EditorThemeMode.fromId("invalid_mode"));
        assertEquals(EditorThemeMode.DARK, EditorThemeMode.fromId(null));
        assertEquals(EditorThemeMode.DARK, EditorThemeMode.fromId(""));
    }

    @Test
    void testPaletteTokensCompleteForBothThemes() {
        EditorThemePalette dark = EditorThemePalette.createDarkPalette();
        assertNotNull(dark.getBackground());
        assertNotNull(dark.getSurface());
        assertNotNull(dark.getSurfaceVariant());
        assertNotNull(dark.getSurfaceElevated());
        assertNotNull(dark.getHeader());
        assertNotNull(dark.getSubheader());
        assertNotNull(dark.getTextPrimary());
        assertNotNull(dark.getTextSecondary());
        assertNotNull(dark.getTextMuted());
        assertNotNull(dark.getTextOnAccent());
        assertNotNull(dark.getBorder());
        assertNotNull(dark.getBorderMuted());
        assertNotNull(dark.getSplitHandle());
        assertNotNull(dark.getPrimary());
        assertNotNull(dark.getPrimaryActive());
        assertNotNull(dark.getSelection());
        assertNotNull(dark.getCursor());
        assertNotNull(dark.getFocus());
        assertNotNull(dark.getSuccess());
        assertNotNull(dark.getWarning());
        assertNotNull(dark.getError());
        assertNotNull(dark.getDisabled());
        assertNotNull(dark.getPlayTint());

        EditorThemePalette light = EditorThemePalette.createLightPalette();
        assertNotNull(light.getBackground());
        assertNotNull(light.getSurface());
        assertNotNull(light.getSurfaceVariant());
        assertNotNull(light.getSurfaceElevated());
        assertNotNull(light.getHeader());
        assertNotNull(light.getSubheader());
        assertNotNull(light.getTextPrimary());
        assertNotNull(light.getTextSecondary());
        assertNotNull(light.getTextMuted());
        assertNotNull(light.getTextOnAccent());
        assertNotNull(light.getBorder());
        assertNotNull(light.getBorderMuted());
        assertNotNull(light.getSplitHandle());
        assertNotNull(light.getPrimary());
        assertNotNull(light.getPrimaryActive());
        assertNotNull(light.getSelection());
        assertNotNull(light.getCursor());
        assertNotNull(light.getFocus());
        assertNotNull(light.getSuccess());
        assertNotNull(light.getWarning());
        assertNotNull(light.getError());
        assertNotNull(light.getDisabled());
        assertNotNull(light.getPlayTint());
    }

    @Test
    void testThemeSwitchingAndListenerNotification() {
        EditorThemeManager manager = new EditorThemeManager(tempPrefFile);
        AtomicReference<EditorThemeMode> receivedMode = new AtomicReference<>();
        AtomicReference<EditorThemePalette> receivedPalette = new AtomicReference<>();

        manager.addListener((newMode, newPalette) -> {
            receivedMode.set(newMode);
            receivedPalette.set(newPalette);
        });

        manager.setThemeMode(EditorThemeMode.LIGHT);
        assertEquals(EditorThemeMode.LIGHT, manager.getCurrentMode());
        assertEquals(EditorThemeMode.LIGHT, receivedMode.get());
        assertNotNull(receivedPalette.get());

        // Setting same mode again does not fire listener unnecessarily
        receivedMode.set(null);
        manager.setThemeMode(EditorThemeMode.LIGHT);
        assertNull(receivedMode.get());

        // Switch back to Dark
        manager.setThemeMode(EditorThemeMode.DARK);
        assertEquals(EditorThemeMode.DARK, manager.getCurrentMode());
        assertEquals(EditorThemeMode.DARK, receivedMode.get());
    }

    @Test
    void testThemePersistenceAcrossReloads() {
        EditorThemeManager manager1 = new EditorThemeManager(tempPrefFile);
        manager1.setThemeMode(EditorThemeMode.LIGHT);
        assertTrue(tempPrefFile.exists());

        // New manager instance reading the same file
        EditorThemeManager manager2 = new EditorThemeManager(tempPrefFile);
        assertEquals(EditorThemeMode.LIGHT, manager2.getCurrentMode());

        manager2.setThemeMode(EditorThemeMode.DARK);

        EditorThemeManager manager3 = new EditorThemeManager(tempPrefFile);
        assertEquals(EditorThemeMode.DARK, manager3.getCurrentMode());
    }

    @Test
    void testCorruptedPreferenceFileFallbackToDark() throws IOException {
        Files.writeString(tempPrefFile.toPath(), "{ not valid json @@@ }");
        EditorThemeManager manager = new EditorThemeManager(tempPrefFile);
        assertEquals(EditorThemeMode.DARK, manager.getCurrentMode());
    }

    @Test
    void testEmptyOrWhitespacePreferenceFileFallbackToDark() throws IOException {
        Files.writeString(tempPrefFile.toPath(), "   \n\t  ");
        EditorThemeManager manager = new EditorThemeManager(tempPrefFile);
        assertEquals(EditorThemeMode.DARK, manager.getCurrentMode());
    }

    @Test
    void testEditorStatePreservedAcrossThemeSwitching() {
        io.github.ldev22live.gdxunity.editor.core.EditorState state = new io.github.ldev22live.gdxunity.editor.core.EditorState();
        io.github.ldev22live.gdxunity.engine.scene.Entity entity = state.createEntity("ThemeSubject");
        entity.getTransform().setLocalPosition(123f, 456f, 0f);
        state.setSelectedEntity(entity);

        EditorThemeManager manager = new EditorThemeManager(tempPrefFile);
        manager.setThemeMode(EditorThemeMode.LIGHT);

        // Verify scene, entity, selection, and transform are untouched by theme manager switching
        assertNotNull(state.getActiveScene());
        assertEquals("ThemeSubject", state.getSelectedEntity().getName());
        assertEquals(123f, state.getSelectedEntity().getTransform().getLocalPosition().x);
        assertEquals(456f, state.getSelectedEntity().getTransform().getLocalPosition().y);

        manager.setThemeMode(EditorThemeMode.DARK);
        assertEquals("ThemeSubject", state.getSelectedEntity().getName());
        assertEquals(123f, state.getSelectedEntity().getTransform().getLocalPosition().x);
    }
}

