package io.github.ldev22live.gdxunity.engine.vector;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter.OutputType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The {@code .vector} source file (TD-104): JSON with {@code schemaVersion} 1, the canvas size and the shapes back to front. Each
 * shape's path is SVG path data in absolute commands, so files diff and merge line by line.
 *
 * <pre>{@code
 * {"schemaVersion": 1, "width": 64, "height": 64, "shapes": [
 *   {"name": "Roof", "path": "M 8 24 L 32 6 L 56 24 Z", "transform": [1, 0, 0, 1, 0, 0],
 *    "fill": "#b03a2e", "fillRule": "nonzero", "stroke": "#000000", "strokeWidth": 1, "opacity": 1}]}
 * }</pre>
 * {@code transform}, {@code fill}, {@code fillRule}, {@code stroke}, {@code strokeWidth} and {@code opacity} may be left out (identity,
 * none, nonzero, none, 0, 1).
 */
public final class VectorFile {
    /** The only version this reader and writer use. */
    public static final int SCHEMA_VERSION = 1;
    /** File extension, with the dot. */
    public static final String EXTENSION = ".vector";

    private VectorFile() { }

    public static String write(VectorDocument document) {
        Objects.requireNonNull(document, "document cannot be null");
        JsonValue root = new JsonValue(JsonValue.ValueType.object);
        root.addChild("schemaVersion", new JsonValue(SCHEMA_VERSION));
        root.addChild("width", new JsonValue(document.width()));
        root.addChild("height", new JsonValue(document.height()));
        JsonValue shapes = new JsonValue(JsonValue.ValueType.array);
        for (VectorShape shape : document.shapes()) {
            JsonValue json = new JsonValue(JsonValue.ValueType.object);
            json.addChild("name", new JsonValue(shape.name()));
            json.addChild("path", new JsonValue(PathData.format(shape.path())));
            if (!shape.transform().isIdentity()) {
                JsonValue matrix = new JsonValue(JsonValue.ValueType.array);
                Affine t = shape.transform();
                for (float value : new float[] {t.a(), t.b(), t.c(), t.d(), t.e(), t.f()}) {
                    matrix.addChild(new JsonValue(value));
                }
                json.addChild("transform", matrix);
            }
            if (shape.fill() != null) {
                json.addChild("fill", new JsonValue(shape.fill().toHex()));
            }
            if (shape.fillRule() != VectorShape.FillRule.NONZERO) {
                json.addChild("fillRule", new JsonValue("evenodd"));
            }
            if (shape.stroke() != null) {
                json.addChild("stroke", new JsonValue(shape.stroke().toHex()));
                json.addChild("strokeWidth", new JsonValue(shape.strokeWidth()));
            }
            if (shape.opacity() < 1f) {
                json.addChild("opacity", new JsonValue(shape.opacity()));
            }
            shapes.addChild(json);
        }
        root.addChild("shapes", shapes);
        return root.prettyPrint(OutputType.json, 0) + "\n";
    }

    /**
     * Reads a {@code .vector} file's text.
     *
     * @throws IllegalArgumentException naming the problem when the text is not a valid version-1 vector file
     */
    public static VectorDocument read(String text) {
        JsonValue root;
        try {
            root = new JsonReader().parse(Objects.requireNonNull(text, "text cannot be null"));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Not a vector file: " + e.getMessage(), e);
        }
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("Not a vector file: the top level is not an object");
        }
        int version = root.getInt("schemaVersion", -1);
        if (version != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported vector file version " + version + "; this reader knows " + SCHEMA_VERSION);
        }
        List<VectorShape> shapes = new ArrayList<>();
        JsonValue list = root.get("shapes");
        for (JsonValue json = list == null ? null : list.child; json != null; json = json.next) {
            String name = json.getString("name", null);
            Affine transform = Affine.IDENTITY;
            JsonValue matrix = json.get("transform");
            if (matrix != null) {
                float[] v = matrix.asFloatArray();
                if (v.length != 6) {
                    throw new IllegalArgumentException("Shape " + name + ": a transform has 6 numbers, not " + v.length);
                }
                transform = new Affine(v[0], v[1], v[2], v[3], v[4], v[5]);
            }
            String rule = json.getString("fillRule", "nonzero");
            if (!rule.equals("nonzero") && !rule.equals("evenodd")) {
                throw new IllegalArgumentException("Shape " + name + ": unknown fill rule " + rule);
            }
            String stroke = json.getString("stroke", null);
            shapes.add(new VectorShape(name, PathData.parse(json.getString("path", "")), transform,
                colour(json.getString("fill", null)), rule.equals("evenodd") ? VectorShape.FillRule.EVENODD : VectorShape.FillRule.NONZERO,
                colour(stroke), stroke == null ? 0f : json.getFloat("strokeWidth", 1f), json.getFloat("opacity", 1f)));
        }
        return new VectorDocument(root.getInt("width", 0), root.getInt("height", 0), shapes);
    }

    private static Rgba colour(String hex) {
        return hex == null ? null : Rgba.hex(hex);
    }
}
