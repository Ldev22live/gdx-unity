package io.github.ldev22live.gdxunity.editor.core.scripting;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * Result of a script compilation pass.
 */
public record CompilationResult(
    boolean success,
    List<ScriptDiagnostic> diagnostics,
    File outputDirectory
) {
    public static CompilationResult successful(File outputDirectory) {
        return new CompilationResult(true, Collections.emptyList(), outputDirectory);
    }

    public static CompilationResult failed(List<ScriptDiagnostic> diagnostics) {
        return new CompilationResult(false, diagnostics != null ? diagnostics : Collections.emptyList(), null);
    }

    public boolean hasErrors() {
        return !success || diagnostics.stream().anyMatch(d -> d.kind() == ScriptDiagnostic.Kind.ERROR);
    }
}
