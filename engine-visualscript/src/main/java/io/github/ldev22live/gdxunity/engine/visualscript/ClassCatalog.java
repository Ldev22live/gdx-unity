package io.github.ldev22live.gdxunity.engine.visualscript;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The Java and Kotlin classes whose public methods are offered as nodes in the visual scripting editor (TD-060): the engine API
 * classes scripts already use, plus the classes of the project's scripts. It is a list, not a scan of the whole classpath, so the
 * palette stays readable and a graph cannot call arbitrary library code. It is not a sandbox: a node runs as trusted code.
 *
 * <p>A catalog is read from a project file ({@link #FILE}, JSON with a {@code classes} array of class names) or made from
 * {@link #engineDefaults}. A class that cannot be found is skipped and reported in {@link #getMissing()}, so a project that does not
 * contain a module's classes still loads.
 */
public final class ClassCatalog {
    /** Where a project keeps its catalog, relative to the asset root. */
    public static final String FILE = "graphs/classes.json";
    public static final int FORMAT_VERSION = 1;

    /** The engine classes offered by default; the ones in modules a game does not contain are skipped. */
    public static final List<String> ENGINE_CLASSES = List.of(
        "io.github.ldev22live.gdxunity.engine.scene.Entity",
        "io.github.ldev22live.gdxunity.engine.scene.Transform",
        "io.github.ldev22live.gdxunity.engine.scene.Scene",
        "io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsSystem3D",
        "io.github.ldev22live.gdxunity.engine.scene.physics3d.RigidBody3DComponent",
        "io.github.ldev22live.gdxunity.engine.render.components.TextComponent",
        "io.github.ldev22live.gdxunity.engine.render.components.ModelComponent",
        "com.badlogic.gdx.math.Vector3",
        "com.badlogic.gdx.math.Quaternion",
        "com.badlogic.gdx.math.MathUtils");

    private final List<Class<?>> classes;
    private final List<String> missing;

    private ClassCatalog(List<Class<?>> classes, List<String> missing) {
        this.classes = List.copyOf(classes);
        this.missing = List.copyOf(missing);
    }

    /** {@link #ENGINE_CLASSES} that {@code loader} can find. */
    public static ClassCatalog engineDefaults(ClassLoader loader) {
        return ofNames(ENGINE_CLASSES, loader);
    }

    /** The classes called {@code names}, in order, without duplicates; names the loader cannot find go to {@link #getMissing()}. */
    public static ClassCatalog ofNames(List<String> names, ClassLoader loader) {
        Objects.requireNonNull(names, "names cannot be null");
        Objects.requireNonNull(loader, "loader cannot be null");
        Set<Class<?>> found = new LinkedHashSet<>();
        List<String> missing = new ArrayList<>();
        for (String name : names) {
            try {
                found.add(Class.forName(name, false, loader));
            } catch (ClassNotFoundException | LinkageError e) {
                missing.add(name);
            }
        }
        return new ClassCatalog(new ArrayList<>(found), missing);
    }

    /** A catalog of exactly these classes. */
    public static ClassCatalog of(List<Class<?>> classes) {
        Objects.requireNonNull(classes, "classes cannot be null");
        return new ClassCatalog(new ArrayList<>(new LinkedHashSet<>(classes)), List.of());
    }

    /**
     * Reads a catalog file.
     *
     * @throws IllegalArgumentException when the text is not a catalog file
     */
    public static ClassCatalog fromJson(String json, ClassLoader loader) {
        Objects.requireNonNull(json, "json cannot be null");
        JsonValue root;
        try {
            root = new JsonReader().parse(json);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Not a class catalog (" + FILE + "): " + e.getMessage(), e);
        }
        if (root == null || !"classCatalog".equals(root.getString("kind", null))) {
            throw new IllegalArgumentException("Not a class catalog (" + FILE + "): its kind must be classCatalog");
        }
        if (root.getInt("schemaVersion", -1) != FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported class catalog version " + root.getInt("schemaVersion", -1));
        }
        JsonValue array = root.get("classes");
        if (array == null || !array.isArray()) {
            throw new IllegalArgumentException("A class catalog needs a classes array");
        }
        List<String> names = new ArrayList<>();
        for (JsonValue item = array.child; item != null; item = item.next) {
            names.add(item.asString());
        }
        return ofNames(names, loader);
    }

    /** The catalog as the text of a catalog file (the names of its classes, then the missing ones, so nothing is lost on a save). */
    public String toJson() {
        StringBuilder out = new StringBuilder("{\n  \"schemaVersion\": ").append(FORMAT_VERSION)
            .append(",\n  \"kind\": \"classCatalog\",\n  \"classes\": [");
        List<String> names = new ArrayList<>();
        classes.forEach(c -> names.add(c.getName()));
        names.addAll(missing);
        for (int i = 0; i < names.size(); i++) {
            out.append(i == 0 ? "\n    \"" : ",\n    \"").append(names.get(i)).append('"');
        }
        return out.append(names.isEmpty() ? "]\n}\n" : "\n  ]\n}\n").toString();
    }

    public List<Class<?>> getClasses() {
        return classes;
    }

    /** Names in the catalog that the class loader could not find. */
    public List<String> getMissing() {
        return missing;
    }

    /** A catalog with {@code more} added after its classes (project script classes, for example). */
    public ClassCatalog plus(List<Class<?>> more) {
        Objects.requireNonNull(more, "more cannot be null");
        Set<Class<?>> all = new LinkedHashSet<>(classes);
        all.addAll(more);
        return new ClassCatalog(new ArrayList<>(all), missing);
    }
}
