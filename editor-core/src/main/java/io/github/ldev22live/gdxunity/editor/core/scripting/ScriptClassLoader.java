package io.github.ldev22live.gdxunity.editor.core.scripting;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Objects;

/**
 * Isolated class loader for dynamically compiled script classes.
 * Can be closed and recreated upon recompilation to release old class references.
 */
public class ScriptClassLoader extends URLClassLoader implements Closeable {

    public ScriptClassLoader(File classesDir, ClassLoader parent) throws IOException {
        super(new URL[]{ Objects.requireNonNull(classesDir, "classesDir cannot be null").toURI().toURL() }, parent);
    }

    public Class<?> loadScriptClass(String className) throws ClassNotFoundException {
        return loadClass(className);
    }
}
