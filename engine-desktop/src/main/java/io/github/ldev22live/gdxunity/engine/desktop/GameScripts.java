package io.github.ldev22live.gdxunity.engine.desktop;

import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Objects;

/**
 * Script classes compiled ahead of time, loaded from a folder or jar (TD-035). The game runtime has no compiler:
 * the editor compiles scripts into {@code <project>/build/scripts} when Play is pressed, and the Gradle run task
 * compiles the project's scripts for {@code :engine-desktop:run}. Desktop only: the web runtime uses a generated
 * {@link ScriptRegistry} instead (TD-036).
 */
public final class GameScripts implements ScriptFactory {
    private final File location;
    private URLClassLoader classLoader;

    /**
     * @param location folder or jar of compiled script classes; it only has to exist once a scene needs a script
     * @param parent   class loader of the engine classes the scripts use
     */
    public GameScripts(File location, ClassLoader parent) {
        this.location = Objects.requireNonNull(location, "location cannot be null");
        Objects.requireNonNull(parent, "parent cannot be null");
        try {
            this.classLoader = new URLClassLoader(new URL[] {location.toURI().toURL()}, parent);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Not a valid script location: " + location, e);
        }
    }

    public File getLocation() {
        return location;
    }

    /**
     * Creates and binds a script instance for every script binding in {@code scene}.
     *
     * @return one message per script that could not be created; empty when all are bound
     */
    public List<String> bind(Scene scene) {
        Objects.requireNonNull(scene, "scene cannot be null");
        requireOpen();
        return ScriptBinder.bind(scene, this);
    }

    /** Detaches every bound script instance in {@code scene}. */
    public static void unbind(Scene scene) {
        ScriptBinder.unbind(scene);
    }

    /** Reports a missing script folder or jar, which happens when the editor never compiled the scripts. */
    @Override
    public String unavailableReason() {
        if (location.exists()) {
            return null;
        }
        return "No compiled scripts at " + location + ". Press Play in the editor once, or pass "
            + "--scripts <folder or jar>.";
    }

    /**
     * Loads {@code className} from the script location and creates an instance with its no-argument constructor.
     *
     * @throws IllegalStateException if this object is closed
     */
    @Override
    public ScriptComponent create(String className) throws ReflectiveOperationException {
        requireOpen();
        Class<?> type = classLoader.loadClass(className);
        if (!ScriptComponent.class.isAssignableFrom(type)) {
            throw new IllegalArgumentException(className + " does not extend " + ScriptComponent.class.getSimpleName());
        }
        if (Modifier.isAbstract(type.getModifiers())) {
            throw new IllegalArgumentException(className + " is abstract");
        }
        return (ScriptComponent) type.getDeclaredConstructor().newInstance();
    }

    private void requireOpen() {
        if (classLoader == null) {
            throw new IllegalStateException("GameScripts is closed");
        }
    }

    /** Releases the loaded classes. Idempotent. */
    @Override
    public void close() throws IOException {
        URLClassLoader loader = classLoader;
        classLoader = null;
        if (loader != null) {
            loader.close();
        }
    }
}
