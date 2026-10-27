package io.github.ldev22live.gdxunity.engine.scene.scripting;

/**
 * Supported programming languages for user scripts in GdxUnity.
 */
public enum ScriptLanguage {
    JAVA("java", "Java"),
    KOTLIN("kt", "Kotlin");

    private final String extension;
    private final String displayName;

    ScriptLanguage(String extension, String displayName) {
        this.extension = extension;
        this.displayName = displayName;
    }

    public String getExtension() {
        return extension;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static ScriptLanguage fromPath(String path) {
        if (path == null) return null;
        String lower = path.toLowerCase();
        if (lower.endsWith(".java")) return JAVA;
        if (lower.endsWith(".kt")) return KOTLIN;
        return null;
    }
}
