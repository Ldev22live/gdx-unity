package io.github.ldev22live.gdxunity.editor.core.theme;

import java.util.Locale;

/**
 * Supported editor theme modes.
 */
public enum EditorThemeMode {
    DARK("dark", "Dark (Material Dark)"),
    LIGHT("light", "Light (Material Light)");

    private final String id;
    private final String displayName;

    EditorThemeMode(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static EditorThemeMode fromId(String id) {
        if (id == null) {
            return DARK;
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        for (EditorThemeMode mode : values()) {
            if (mode.id.equals(normalized)) {
                return mode;
            }
        }
        return DARK;
    }
}
