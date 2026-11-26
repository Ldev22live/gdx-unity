package io.github.ldev22live.gdxunity.engine.sprite;

import java.util.List;

/**
 * Where a layer's pixels come from (TD-104): shapes of a {@code .vector} drawing, rasterized into the layer. The vector is the
 * source; the layer is redrawn from it, and Edit 2D Asset opens it. A layer without one was painted in the sprite itself.
 *
 * @param vector project-relative path of the {@code .vector} file, forward slashes
 * @param shapes the shapes drawn into the layer, by name; empty for the whole drawing
 */
public record SpriteLayerSource(String vector, List<String> shapes) {
    public SpriteLayerSource {
        if (vector == null || vector.isBlank() || vector.contains("\\") || vector.startsWith("/") || vector.contains("..")) {
            throw new IllegalArgumentException("A layer source is a project-relative path with forward slashes: " + vector);
        }
        shapes = List.copyOf(shapes);
    }
}
