package io.github.ldev22live.gdxunity.editor.core.scripting;

import org.jetbrains.kotlin.cli.common.ExitCode;
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity;
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation;
import org.jetbrains.kotlin.cli.common.messages.MessageCollector;
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Compiles user Kotlin scripts using the embedded K2JVMCompiler.
 */
public class KotlinScriptCompiler {

    /**
     * Compiles Kotlin source files into the specified output directory.
     *
     * @param sourceFiles      list of Kotlin source files (.kt)
     * @param outputDir        directory where .class files will be written
     * @param classpathEntries additional classpath entries
     * @return CompilationResult containing success status and diagnostics
     */
    public CompilationResult compile(List<File> sourceFiles, File outputDir, List<String> classpathEntries) {
        if (sourceFiles == null || sourceFiles.isEmpty()) {
            return CompilationResult.successful(outputDir);
        }

        if (outputDir != null && !outputDir.exists()) {
            outputDir.mkdirs();
        }

        List<ScriptDiagnostic> diagnostics = new ArrayList<>();
        MessageCollector collector = new MessageCollector() {
            @Override
            public void clear() {
                diagnostics.clear();
            }

            @Override
            public boolean hasErrors() {
                return diagnostics.stream().anyMatch(d -> d.kind() == ScriptDiagnostic.Kind.ERROR);
            }

            @Override
            public void report(CompilerMessageSeverity severity, String message, CompilerMessageSourceLocation location) {
                ScriptDiagnostic.Kind kind = switch (severity) {
                    case ERROR, EXCEPTION -> ScriptDiagnostic.Kind.ERROR;
                    case WARNING, STRONG_WARNING -> ScriptDiagnostic.Kind.WARNING;
                    case INFO, LOGGING -> ScriptDiagnostic.Kind.NOTE;
                    default -> ScriptDiagnostic.Kind.OTHER;
                };

                String path = (location != null) ? location.getPath() : null;
                int line = (location != null) ? location.getLine() : 0;
                int col = (location != null) ? location.getColumn() : 0;

                diagnostics.add(new ScriptDiagnostic(kind, path, line, col, message));
            }
        };

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

        List<String> args = new ArrayList<>();
        for (File src : sourceFiles) {
            args.add(src.getAbsolutePath());
        }
        if (outputDir != null) {
            args.add("-d");
            args.add(outputDir.getAbsolutePath());
        }
        args.add("-classpath");
        args.add(cpBuilder.toString());
        args.add("-jvm-target");
        args.add("21");
        args.add("-no-stdlib"); // stdlib is already provided on the classpath

        org.jetbrains.kotlin.cli.jvm.K2JVMCompiler compiler = new org.jetbrains.kotlin.cli.jvm.K2JVMCompiler();
        org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments compilerArgs =
            new org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments();

        compiler.parseArguments(args.toArray(new String[0]), compilerArgs);

        ByteArrayOutputStream errStream = new ByteArrayOutputStream();
        PrintStream prevErr = System.err;

        ExitCode exitCode;
        try {
            System.setErr(new PrintStream(errStream));
            exitCode = compiler.exec(collector, org.jetbrains.kotlin.config.Services.EMPTY, compilerArgs);
        } catch (Throwable t) {
            diagnostics.add(new ScriptDiagnostic(
                ScriptDiagnostic.Kind.ERROR,
                null,
                0,
                0,
                "Kotlin compilation failed with exception: " + t.getMessage()
            ));
            return CompilationResult.failed(diagnostics);
        } finally {
            System.setErr(prevErr);
        }

        boolean success = (exitCode == ExitCode.OK) && !collector.hasErrors();
        if (!success && diagnostics.isEmpty() && errStream.size() > 0) {
            diagnostics.add(new ScriptDiagnostic(
                ScriptDiagnostic.Kind.ERROR,
                null,
                0,
                0,
                errStream.toString()
            ));
        }

        return new CompilationResult(success, diagnostics, outputDir);
    }
}
