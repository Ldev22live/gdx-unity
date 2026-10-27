package io.github.ldev22live.gdxunity.editor.core.scripting;

import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.scripting.EntityScriptBindingComponent;
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent;
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptLanguage;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Central service for discovering, compiling, and instantiating Java and Kotlin scripts.
 */
public class ScriptManager {
    private final File scriptsRootDir;
    private final File buildOutputDir;

    private final JavaScriptCompiler javaCompiler = new JavaScriptCompiler();
    private final KotlinScriptCompiler kotlinCompiler = new KotlinScriptCompiler();

    private ScriptClassLoader classLoader;
    private final List<ScriptDiagnostic> latestDiagnostics = new ArrayList<>();
    private final Map<String, Class<? extends ScriptComponent>> discoveredScriptClasses = new LinkedHashMap<>();

    public ScriptManager(File scriptsRootDir, File buildOutputDir) {
        this.scriptsRootDir = Objects.requireNonNull(scriptsRootDir, "scriptsRootDir cannot be null");
        this.buildOutputDir = Objects.requireNonNull(buildOutputDir, "buildOutputDir cannot be null");
    }

    public File getScriptsRootDir() {
        return scriptsRootDir;
    }

    public File getBuildOutputDir() {
        return buildOutputDir;
    }

    public List<ScriptDiagnostic> getLatestDiagnostics() {
        return Collections.unmodifiableList(latestDiagnostics);
    }

    public Map<String, Class<? extends ScriptComponent>> getDiscoveredScriptClasses() {
        return Collections.unmodifiableMap(discoveredScriptClasses);
    }

    /**
     * Recursively scans for .java and .kt source files, compiles them, and reloads class loader.
     *
     * @return true if all compilation passed, false if any compiler errors occurred.
     */
    public synchronized boolean compileAll() {
        latestDiagnostics.clear();
        discoveredScriptClasses.clear();

        if (!scriptsRootDir.exists()) {
            scriptsRootDir.mkdirs();
        }
        if (!buildOutputDir.exists()) {
            buildOutputDir.mkdirs();
        }

        List<File> javaSources = new ArrayList<>();
        List<File> kotlinSources = new ArrayList<>();

        try (Stream<Path> stream = Files.walk(scriptsRootDir.toPath())) {
            stream.filter(Files::isRegularFile).forEach(p -> {
                String name = p.getFileName().toString().toLowerCase();
                if (name.endsWith(".java")) {
                    javaSources.add(p.toFile());
                } else if (name.endsWith(".kt")) {
                    kotlinSources.add(p.toFile());
                }
            });
        } catch (IOException e) {
            latestDiagnostics.add(new ScriptDiagnostic(
                ScriptDiagnostic.Kind.ERROR,
                null,
                0,
                0,
                "Failed to scan script directory: " + e.getMessage()
            ));
            return false;
        }

        boolean allSuccess = true;

        // 1. Compile Java scripts
        if (!javaSources.isEmpty()) {
            CompilationResult javaResult = javaCompiler.compile(javaSources, buildOutputDir, null);
            latestDiagnostics.addAll(javaResult.diagnostics());
            if (javaResult.hasErrors()) {
                allSuccess = false;
            }
        }

        // 2. Compile Kotlin scripts
        if (!kotlinSources.isEmpty()) {
            CompilationResult ktResult = kotlinCompiler.compile(kotlinSources, buildOutputDir, null);
            latestDiagnostics.addAll(ktResult.diagnostics());
            if (ktResult.hasErrors()) {
                allSuccess = false;
            }
        }

        if (!allSuccess) {
            return false;
        }

        // 3. Reload classloader with fresh compiled output
        reloadClassLoader();
        discoverCompiledScriptClasses();
        return true;
    }

    private void reloadClassLoader() {
        if (classLoader != null) {
            try {
                classLoader.close();
            } catch (IOException ignored) {
            }
            classLoader = null;
        }

        try {
            classLoader = new ScriptClassLoader(buildOutputDir, getClass().getClassLoader());
        } catch (IOException e) {
            latestDiagnostics.add(new ScriptDiagnostic(
                ScriptDiagnostic.Kind.ERROR,
                null,
                0,
                0,
                "Failed to initialize ScriptClassLoader: " + e.getMessage()
            ));
        }
    }

    private void discoverCompiledScriptClasses() {
        if (classLoader == null || !buildOutputDir.exists()) {
            return;
        }

        Path rootPath = buildOutputDir.toPath();
        try (Stream<Path> stream = Files.walk(rootPath)) {
            stream.filter(Files::isRegularFile)
                  .filter(p -> p.getFileName().toString().endsWith(".class"))
                  .filter(p -> !p.getFileName().toString().contains("$")) // skip synthetic inner classes
                  .forEach(p -> {
                      Path rel = rootPath.relativize(p);
                      String pathStr = rel.toString().replace('/', '.').replace('\\', '.');
                      String fqcn = pathStr.substring(0, pathStr.length() - ".class".length());

                      try {
                          Class<?> loaded = classLoader.loadScriptClass(fqcn);
                          if (ScriptComponent.class.isAssignableFrom(loaded) && !Modifier.isAbstract(loaded.getModifiers())) {
                              @SuppressWarnings("unchecked")
                              Class<? extends ScriptComponent> scriptClass = (Class<? extends ScriptComponent>) loaded;
                              discoveredScriptClasses.put(fqcn, scriptClass);
                          }
                      } catch (Throwable ignored) {
                          // Skip unresolvable or helper classes
                      }
                  });
        } catch (IOException ignored) {
        }
    }

    /**
     * Instantiates a new script instance for the specified class name.
     */
    public ScriptComponent createScriptInstance(String className) throws Exception {
        if (className == null || className.isBlank()) {
            return null;
        }
        if (classLoader == null) {
            reloadClassLoader();
        }

        Class<?> clazz = classLoader.loadScriptClass(className);
        if (!ScriptComponent.class.isAssignableFrom(clazz)) {
            throw new IllegalArgumentException("Class " + className + " does not extend " + ScriptComponent.class.getName());
        }
        if (Modifier.isAbstract(clazz.getModifiers())) {
            throw new IllegalArgumentException("Cannot instantiate abstract script class: " + className);
        }

        return (ScriptComponent) clazz.getDeclaredConstructor().newInstance();
    }

    /**
     * Binds and initializes runtime script instances across all EntityScriptBindingComponent instances in the given scene.
     * Used when starting Play Mode.
     *
     * @param scene scene to initialize scripts in
     * @return list of diagnostics/errors encountered during instantiation
     */
    public List<String> initializeSceneScripts(Scene scene) {
        List<String> errors = new ArrayList<>();
        if (scene == null) return errors;

        for (Entity entity : scene.getAllEntities()) {
            EntityScriptBindingComponent binding = entity.getComponent(EntityScriptBindingComponent.class);
            if (binding != null && binding.getScriptClassName() != null) {
                try {
                    ScriptComponent instance = createScriptInstance(binding.getScriptClassName());
                    binding.bindInstance(instance);
                } catch (Throwable t) {
                    String msg = "Failed to instantiate script '" + binding.getScriptClassName() + "' on entity '" + entity.getName() + "': " + t.getMessage();
                    errors.add(msg);
                }
            }
        }
        return errors;
    }

    /**
     * Disposes runtime script instances in the scene. Used when stopping Play Mode.
     */
    public void cleanupSceneScripts(Scene scene) {
        if (scene == null) return;
        for (Entity entity : scene.getAllEntities()) {
            EntityScriptBindingComponent binding = entity.getComponent(EntityScriptBindingComponent.class);
            if (binding != null) {
                binding.unbindInstance();
            }
        }
    }

    public void dispose() {
        if (classLoader != null) {
            try {
                classLoader.close();
            } catch (IOException ignored) {
            }
            classLoader = null;
        }
        discoveredScriptClasses.clear();
        latestDiagnostics.clear();
    }
}
