package io.github.ldev22live.gdxunity.editor.core.scripting;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Compiles user Java scripts using the standard javax.tools.JavaCompiler.
 */
public class JavaScriptCompiler {

    /**
     * Compiles Java source files into the specified output directory.
     *
     * @param sourceFiles      list of Java source files
     * @param outputDir        directory where .class files will be written
     * @param classpathEntries additional classpath entries (e.g. engine-scene.jar, LibGDX)
     * @return CompilationResult containing success status and diagnostics
     */
    public CompilationResult compile(List<File> sourceFiles, File outputDir, List<String> classpathEntries) {
        if (sourceFiles == null || sourceFiles.isEmpty()) {
            return CompilationResult.successful(outputDir);
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return CompilationResult.failed(List.of(
                new ScriptDiagnostic(
                    ScriptDiagnostic.Kind.ERROR,
                    null,
                    0,
                    0,
                    "No system Java compiler available. Make sure GdxUnity is running on a JDK (Java Development Kit), not a JRE."
                )
            ));
        }

        if (outputDir != null && !outputDir.exists()) {
            outputDir.mkdirs();
        }

        DiagnosticCollector<JavaFileObject> diagnosticCollector = new DiagnosticCollector<>();
        StringWriter compilerOutput = new StringWriter();

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnosticCollector, Locale.getDefault(), StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(sourceFiles);

            List<String> options = new ArrayList<>();
            if (outputDir != null) {
                options.add("-d");
                options.add(outputDir.getAbsolutePath());
            }

            // Build classpath
            String currentClasspath = System.getProperty("java.class.path");
            StringBuilder cpBuilder = new StringBuilder(currentClasspath != null ? currentClasspath : "");
            if (classpathEntries != null) {
                for (String cp : classpathEntries) {
                    if (cp != null && !cp.isBlank()) {
                        if (cpBuilder.length() > 0) cpBuilder.append(File.pathSeparator);
                        cpBuilder.append(cp);
                    }
                }
            }

            options.add("-classpath");
            options.add(cpBuilder.toString());
            options.add("-proc:none"); // disable annotation processing for fast script compiles

            JavaCompiler.CompilationTask task = compiler.getTask(
                compilerOutput,
                fileManager,
                diagnosticCollector,
                options,
                null,
                compilationUnits
            );

            boolean success = Boolean.TRUE.equals(task.call());

            List<ScriptDiagnostic> diagnostics = new ArrayList<>();
            for (Diagnostic<? extends JavaFileObject> d : diagnosticCollector.getDiagnostics()) {
                ScriptDiagnostic.Kind kind = switch (d.getKind()) {
                    case ERROR -> ScriptDiagnostic.Kind.ERROR;
                    case WARNING, MANDATORY_WARNING -> ScriptDiagnostic.Kind.WARNING;
                    case NOTE -> ScriptDiagnostic.Kind.NOTE;
                    default -> ScriptDiagnostic.Kind.OTHER;
                };

                String sourceName = (d.getSource() != null) ? d.getSource().getName() : null;
                diagnostics.add(new ScriptDiagnostic(
                    kind,
                    sourceName,
                    d.getLineNumber(),
                    d.getColumnNumber(),
                    d.getMessage(Locale.getDefault())
                ));
            }

            if (!success && diagnostics.isEmpty() && compilerOutput.getBuffer().length() > 0) {
                diagnostics.add(new ScriptDiagnostic(
                    ScriptDiagnostic.Kind.ERROR,
                    null,
                    0,
                    0,
                    compilerOutput.toString()
                ));
            }

            return new CompilationResult(success, diagnostics, outputDir);
        } catch (Exception e) {
            return CompilationResult.failed(List.of(
                new ScriptDiagnostic(
                    ScriptDiagnostic.Kind.ERROR,
                    null,
                    0,
                    0,
                    "Compilation exception: " + e.getMessage()
                )
            ));
        }
    }
}
