package io.github.ldev22live.gdxunity.editor.core.scripting;

import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScriptCompilerTest {

    @Test
    void testCompileValidJavaSource(@TempDir Path tempDir) throws Exception {
        Path scriptsDir = tempDir.resolve("scripts");
        Path outputDir = tempDir.resolve("bin");
        Files.createDirectories(scriptsDir);

        String javaCode = """
            package test;
            import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent;
            
            public class ValidPlayerScript extends ScriptComponent {
                public int counter = 0;
                
                @Override
                public void onUpdate(float dt) {
                    counter++;
                }
            }
            """;
        Path src = scriptsDir.resolve("ValidPlayerScript.java");
        Files.writeString(src, javaCode);

        JavaScriptCompiler compiler = new JavaScriptCompiler();
        CompilationResult result = compiler.compile(List.of(src.toFile()), outputDir.toFile(), null);

        assertTrue(result.success());
        assertFalse(result.hasErrors());

        // Test loading compiled class
        try (ScriptClassLoader loader = new ScriptClassLoader(outputDir.toFile(), getClass().getClassLoader())) {
            Class<?> clazz = loader.loadScriptClass("test.ValidPlayerScript");
            assertNotNull(clazz);
            assertTrue(ScriptComponent.class.isAssignableFrom(clazz));

            ScriptComponent instance = (ScriptComponent) clazz.getDeclaredConstructor().newInstance();
            assertNotNull(instance);
            instance.onUpdate(0.016f);
        }
    }

    @Test
    void testJavaSyntaxErrorDiagnostics(@TempDir Path tempDir) throws Exception {
        Path scriptsDir = tempDir.resolve("scripts");
        Path outputDir = tempDir.resolve("bin");
        Files.createDirectories(scriptsDir);

        String invalidCode = """
            public class BrokenScript {
                this is a syntax error !!
            }
            """;
        Path src = scriptsDir.resolve("BrokenScript.java");
        Files.writeString(src, invalidCode);

        JavaScriptCompiler compiler = new JavaScriptCompiler();
        CompilationResult result = compiler.compile(List.of(src.toFile()), outputDir.toFile(), null);

        assertFalse(result.success());
        assertTrue(result.hasErrors());
        assertFalse(result.diagnostics().isEmpty());
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.kind() == ScriptDiagnostic.Kind.ERROR));
    }

    @Test
    void testCompileValidKotlinSource(@TempDir Path tempDir) throws Exception {
        Path scriptsDir = tempDir.resolve("scripts");
        Path outputDir = tempDir.resolve("bin");
        Files.createDirectories(scriptsDir);

        String kotlinCode = """
            package test
            import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent

            class KotlinRotatorScript : ScriptComponent() {
                var speed: Float = 90.0f
                override fun onUpdate(deltaTime: Float) {
                    speed += deltaTime
                }
            }
            """;
        Path src = scriptsDir.resolve("KotlinRotatorScript.kt");
        Files.writeString(src, kotlinCode);

        KotlinScriptCompiler compiler = new KotlinScriptCompiler();
        CompilationResult result = compiler.compile(List.of(src.toFile()), outputDir.toFile(), null);

        assertTrue(result.success(), "Kotlin compilation should succeed: " + result.diagnostics());
        assertFalse(result.hasErrors());

        try (ScriptClassLoader loader = new ScriptClassLoader(outputDir.toFile(), getClass().getClassLoader())) {
            Class<?> clazz = loader.loadScriptClass("test.KotlinRotatorScript");
            assertNotNull(clazz);
            assertTrue(ScriptComponent.class.isAssignableFrom(clazz));

            ScriptComponent instance = (ScriptComponent) clazz.getDeclaredConstructor().newInstance();
            assertNotNull(instance);
            instance.onUpdate(0.016f);
        }
    }

    @Test
    void testKotlinSyntaxErrorDiagnostics(@TempDir Path tempDir) throws Exception {
        Path scriptsDir = tempDir.resolve("scripts");
        Path outputDir = tempDir.resolve("bin");
        Files.createDirectories(scriptsDir);

        String invalidCode = """
            package test
            class BrokenKt {
                invalid syntax here %%%
            }
            """;
        Path src = scriptsDir.resolve("BrokenKt.kt");
        Files.writeString(src, invalidCode);

        KotlinScriptCompiler compiler = new KotlinScriptCompiler();
        CompilationResult result = compiler.compile(List.of(src.toFile()), outputDir.toFile(), null);

        assertFalse(result.success());
        assertTrue(result.hasErrors());
        assertFalse(result.diagnostics().isEmpty());
    }

    @Test
    void testScriptManagerDiscoveryAndInstantiation(@TempDir Path tempDir) throws Exception {
        Path scriptsDir = tempDir.resolve("scripts");
        Path outputDir = tempDir.resolve("bin");
        Files.createDirectories(scriptsDir);

        Files.writeString(scriptsDir.resolve("MyJavaScript.java"), """
            import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent;
            public class MyJavaScript extends ScriptComponent {}
            """);

        Files.writeString(scriptsDir.resolve("MyKtScript.kt"), """
            import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent
            class MyKtScript : ScriptComponent()
            """);

        ScriptManager manager = new ScriptManager(scriptsDir.toFile(), outputDir.toFile());
        boolean compiled = manager.compileAll();

        assertTrue(compiled);
        assertEquals(2, manager.getDiscoveredScriptClasses().size());
        assertTrue(manager.getDiscoveredScriptClasses().containsKey("MyJavaScript"));
        assertTrue(manager.getDiscoveredScriptClasses().containsKey("MyKtScript"));

        ScriptComponent s1 = manager.createScriptInstance("MyJavaScript");
        assertNotNull(s1);
        ScriptComponent s2 = manager.createScriptInstance("MyKtScript");
        assertNotNull(s2);

        manager.dispose();
    }
}
