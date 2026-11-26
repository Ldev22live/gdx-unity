package io.github.ldev22live.gdxunity.engine.vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A vector drawing (TD-104): a canvas size and named shapes, drawn first to last. Immutable; edits return a new document, which is
 * what the editor's undo keeps.
 *
 * @param width  canvas width in document units (the pixel size it rasterizes to by default)
 * @param height canvas height, y down as in SVG
 * @param shapes back to front
 */
public record VectorDocument(int width, int height, List<VectorShape> shapes) {
    public VectorDocument {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("A canvas needs a positive size: " + width + "x" + height);
        }
        shapes = List.copyOf(Objects.requireNonNull(shapes, "shapes cannot be null"));
        Set<String> names = new HashSet<>();
        for (VectorShape shape : shapes) {
            if (!names.add(shape.name())) {
                throw new IllegalArgumentException("Shape names must be unique: " + shape.name());
            }
        }
    }

    public Optional<VectorShape> find(String name) {
        return shapes.stream().filter(s -> s.name().equals(name)).findFirst();
    }

    /** The shape called {@code name}; fails when there is none. */
    public VectorShape shape(String name) {
        return find(name).orElseThrow(() -> new IllegalArgumentException("No shape " + name));
    }

    /** This document with the shape of the same name replaced by {@code shape}. */
    public VectorDocument with(VectorShape shape) {
        Objects.requireNonNull(shape, "shape cannot be null");
        List<VectorShape> result = new ArrayList<>(shapes);
        int index = indexOf(shape.name());
        result.set(index, shape);
        return new VectorDocument(width, height, result);
    }

    /** This document with {@code shape} drawn last. */
    public VectorDocument adding(VectorShape shape) {
        List<VectorShape> result = new ArrayList<>(shapes);
        result.add(Objects.requireNonNull(shape, "shape cannot be null"));
        return new VectorDocument(width, height, result);
    }

    /** This document without the shape called {@code name}. */
    public VectorDocument removing(String name) {
        List<VectorShape> result = new ArrayList<>(shapes);
        result.remove(indexOf(name));
        return new VectorDocument(width, height, result);
    }

    /** This document with the shape called {@code name} moved to draw order {@code index} (0 is the back). */
    public VectorDocument moving(String name, int index) {
        List<VectorShape> result = new ArrayList<>(shapes);
        VectorShape shape = result.remove(indexOf(name));
        if (index < 0 || index > result.size()) {
            throw new IllegalArgumentException("Draw order " + index + " is outside 0 to " + result.size());
        }
        result.add(index, shape);
        return new VectorDocument(width, height, result);
    }

    public int indexOf(String name) {
        for (int i = 0; i < shapes.size(); i++) {
            if (shapes.get(i).name().equals(name)) {
                return i;
            }
        }
        throw new IllegalArgumentException("No shape " + name);
    }
}
