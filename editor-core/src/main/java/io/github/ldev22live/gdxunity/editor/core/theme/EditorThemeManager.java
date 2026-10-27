package io.github.ldev22live.gdxunity.editor.core.theme;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Manages active editor theme mode, provides theme notifications, and handles persistence.
 */
public class EditorThemeManager {
    public interface ThemeListener {
        void onThemeChanged(EditorThemeMode newMode, EditorThemePalette newPalette);
    }

    private static final String DEFAULT_PREF_FILENAME = "editor_theme.json";
    private final File prefFile;
    private final List<ThemeListener> listeners = new ArrayList<>();

    private EditorThemeMode currentMode;
    private EditorThemePalette currentPalette;

    public EditorThemeManager() {
        this(new File(DEFAULT_PREF_FILENAME));
    }

    public EditorThemeManager(File prefFile) {
        this.prefFile = Objects.requireNonNull(prefFile, "prefFile cannot be null");
        this.currentMode = loadThemePreference();
        this.currentPalette = resolvePalette(this.currentMode);
    }

    public EditorThemeMode getCurrentMode() {
        return currentMode;
    }

    public EditorThemePalette getCurrentPalette() {
        return currentPalette;
    }

    public void setThemeMode(EditorThemeMode mode) {
        if (mode == null) {
            mode = EditorThemeMode.DARK;
        }
        if (this.currentMode == mode) {
            return;
        }
        this.currentMode = mode;
        this.currentPalette = resolvePalette(mode);
        saveThemePreference(mode);

        for (ThemeListener listener : new ArrayList<>(listeners)) {
            try {
                listener.onThemeChanged(currentMode, currentPalette);
            } catch (Throwable ignored) {}
        }
    }

    public void addListener(ThemeListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(ThemeListener listener) {
        listeners.remove(listener);
    }

    public static EditorThemePalette resolvePalette(EditorThemeMode mode) {
        return mode == EditorThemeMode.LIGHT ?
            EditorThemePalette.createLightPalette() :
            EditorThemePalette.createDarkPalette();
    }

    private EditorThemeMode loadThemePreference() {
        if (!prefFile.exists() || !prefFile.isFile()) {
            return EditorThemeMode.DARK;
        }
        try {
            String content = Files.readString(prefFile.toPath()).trim();
            // Basic JSON parse: {"theme":"dark"} or plain text "dark"
            if (content.contains("\"theme\"")) {
                int start = content.indexOf("\"theme\"");
                int colon = content.indexOf(':', start);
                if (colon != -1) {
                    int quote1 = content.indexOf('"', colon);
                    int quote2 = content.indexOf('"', quote1 + 1);
                    if (quote1 != -1 && quote2 != -1) {
                        return EditorThemeMode.fromId(content.substring(quote1 + 1, quote2));
                    }
                }
            }
            return EditorThemeMode.fromId(content);
        } catch (Exception e) {
            return EditorThemeMode.DARK;
        }
    }

    private void saveThemePreference(EditorThemeMode mode) {
        try {
            if (prefFile.getParentFile() != null && !prefFile.getParentFile().exists()) {
                prefFile.getParentFile().mkdirs();
            }
            String json = "{\n  \"theme\": \"" + mode.getId() + "\"\n}\n";
            Files.writeString(prefFile.toPath(), json);
        } catch (IOException ignored) {}
    }
}
