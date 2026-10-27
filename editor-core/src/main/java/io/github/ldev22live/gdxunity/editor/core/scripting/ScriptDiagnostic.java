package io.github.ldev22live.gdxunity.editor.core.scripting;

/**
 * Diagnostic record representing a compilation error, warning, or informational message.
 */
public record ScriptDiagnostic(
    Kind kind,
    String sourceFile,
    long lineNumber,
    long columnNumber,
    String message
) {
    public enum Kind {
        ERROR,
        WARNING,
        NOTE,
        OTHER
    }

    @Override
    public String toString() {
        String loc = (sourceFile != null ? sourceFile : "") +
            (lineNumber > 0 ? ":" + lineNumber + (columnNumber > 0 ? ":" + columnNumber : "") : "");
        return "[" + kind + "] " + (loc.isEmpty() ? "" : loc + " - ") + message;
    }
}
