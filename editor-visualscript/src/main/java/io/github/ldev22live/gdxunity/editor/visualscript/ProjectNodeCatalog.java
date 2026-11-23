package io.github.ldev22live.gdxunity.editor.visualscript;

import io.github.ldev22live.gdxunity.editor.core.scripting.ScriptClassLoader;
import io.github.ldev22live.gdxunity.engine.visualscript.ClassCatalog;
import io.github.ldev22live.gdxunity.engine.visualscript.ComponentNodes;
import io.github.ldev22live.gdxunity.engine.visualscript.JavaMethodNodes;
import io.github.ldev22live.gdxunity.engine.visualscript.NodeRegistry;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Keeps the method nodes of a {@link NodeRegistry} in step with the project (TD-060): the classes listed in the project's catalog
 * file ({@link ClassCatalog#FILE}, or the engine defaults when the project has none) and every public class compiled from the
 * project's scripts. {@link #refresh} reads both again, so the palette follows a compile or an edit of the catalog.
 *
 * <p>The compiled classes are loaded through a {@link ScriptClassLoader} that stays open while the registry uses their nodes; the
 * previous one is closed by the next refresh.
 */
public final class ProjectNodeCatalog implements Closeable {
    private static final String CLASS_SUFFIX = ".class";

    /** What a refresh found. */
    public record Result(int nodes, int projectClasses, List<String> missingClasses, List<String> problems) {
        /** One line for the status bar. */
        public String summary() {
            StringBuilder text = new StringBuilder().append(nodes).append(" method nodes, ").append(projectClasses)
                .append(" project classes");
            if (!missingClasses.isEmpty()) {
                text.append(", not found: ").append(String.join(", ", missingClasses));
            }
            if (!problems.isEmpty()) {
                text.append(", problems: ").append(String.join("; ", problems));
            }
            return text.toString();
        }
    }

    private final NodeRegistry registry;
    private final File assetRoot;
    private final File scriptClassesDir;
    private ScriptClassLoader loader;

    /**
     * @param assetRoot        the project folder; the catalog file is {@code graphs/classes.json} under it
     * @param scriptClassesDir where the project's scripts are compiled to (may not exist yet)
     */
    public ProjectNodeCatalog(NodeRegistry registry, File assetRoot, File scriptClassesDir) {
        this.registry = Objects.requireNonNull(registry, "registry cannot be null");
        this.assetRoot = Objects.requireNonNull(assetRoot, "assetRoot cannot be null");
        this.scriptClassesDir = Objects.requireNonNull(scriptClassesDir, "scriptClassesDir cannot be null");
    }

    /** Replaces the registry's method nodes with those of the catalog file (or the engine defaults) and the compiled project classes. */
    public Result refresh() {
        List<String> problems = new ArrayList<>();
        close();
        ClassLoader parent = NodeRegistry.class.getClassLoader();
        ClassCatalog catalog = readCatalog(parent, problems);
        List<Class<?>> projectClasses = loadProjectClasses(parent, problems);
        registry.removeMethodNodes();
        ComponentNodes.registerAll(registry);
        int nodes = JavaMethodNodes.registerAll(registry, catalog.plus(projectClasses));
        return new Result(nodes, projectClasses.size(), catalog.getMissing(), problems);
    }

    private ClassCatalog readCatalog(ClassLoader parent, List<String> problems) {
        File file = new File(assetRoot, ClassCatalog.FILE);
        if (!file.isFile()) {
            return ClassCatalog.engineDefaults(parent);
        }
        try {
            return ClassCatalog.fromJson(Files.readString(file.toPath()), parent);
        } catch (IOException | IllegalArgumentException e) {
            problems.add(ClassCatalog.FILE + " ignored (" + e.getMessage() + "); using the engine classes");
            return ClassCatalog.engineDefaults(parent);
        }
    }

    private List<Class<?>> loadProjectClasses(ClassLoader parent, List<String> problems) {
        List<Class<?>> classes = new ArrayList<>();
        if (!scriptClassesDir.isDirectory()) {
            return classes;
        }
        try {
            loader = new ScriptClassLoader(scriptClassesDir, parent);
            Path base = scriptClassesDir.toPath();
            List<String> names = new ArrayList<>();
            try (Stream<Path> files = Files.walk(base)) {
                files.filter(path -> path.getFileName().toString().endsWith(CLASS_SUFFIX)).forEach(path -> {
                    String relative = base.relativize(path).toString().replace(File.separatorChar, '.');
                    names.add(relative.substring(0, relative.length() - CLASS_SUFFIX.length()));
                });
            }
            names.sort(String::compareTo);
            for (String name : names) {
                try {
                    Class<?> type = loader.loadScriptClass(name);
                    if (java.lang.reflect.Modifier.isPublic(type.getModifiers()) && !type.isAnonymousClass()) {
                        classes.add(type);
                    }
                } catch (ClassNotFoundException | LinkageError e) {
                    problems.add("class " + name + " not loaded (" + e.getClass().getSimpleName() + ")");
                }
            }
        } catch (IOException e) {
            problems.add("compiled scripts not read (" + e.getMessage() + ")");
        }
        return classes;
    }

    /** Closes the class loader of the compiled project classes; their nodes stay in the registry until the next refresh. */
    @Override
    public void close() {
        if (loader != null) {
            try {
                loader.close();
            } catch (IOException e) {
                // The loader only holds file handles for classes; a failure to close leaks nothing the next refresh needs.
                loader = null;
                return;
            }
            loader = null;
        }
    }
}
