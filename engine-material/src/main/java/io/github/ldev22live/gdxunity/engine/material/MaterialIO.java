package io.github.ldev22live.gdxunity.engine.material;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.SerializationException;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

/**
 * Reads and writes the {@code .material} file: one {@link Material} as JSON, version {@value #VERSION} (TD-081). Reading is strict
 * about what it understands (a newer version, a wrong type or an out-of-range value is an error that names the field) and forgiving
 * about what is missing (a missing field keeps the default), so older files keep loading when fields are added.
 */
public final class MaterialIO {
    /** Current file format version. */
    public static final int VERSION = 1;

    private MaterialIO() { }

    /** The material as JSON text; the same material always gives the same text. */
    public static String toJson(Material material) {
        Objects.requireNonNull(material, "material cannot be null");
        StringBuilder out = new StringBuilder();
        out.append("{\n");
        out.append("  \"version\": ").append(VERSION).append(",\n");
        out.append("  \"name\": ").append(quote(material.getName())).append(",\n");
        out.append("  \"baseColor\": ").append(array(material.getBaseColor(), true)).append(",\n");
        out.append("  \"metallic\": ").append(number(material.getMetallic())).append(",\n");
        out.append("  \"roughness\": ").append(number(material.getRoughness())).append(",\n");
        out.append("  \"emissive\": ").append(array(material.getEmissive(), false)).append(",\n");
        out.append("  \"emissiveStrength\": ").append(number(material.getEmissiveStrength())).append(",\n");
        out.append("  \"normalScale\": ").append(number(material.getNormalScale())).append(",\n");
        out.append("  \"occlusionStrength\": ").append(number(material.getOcclusionStrength())).append(",\n");
        out.append("  \"alphaMode\": ").append(quote(material.getAlphaMode().name())).append(",\n");
        out.append("  \"alphaCutoff\": ").append(number(material.getAlphaCutoff())).append(",\n");
        out.append("  \"doubleSided\": ").append(material.isDoubleSided()).append(",\n");
        out.append("  \"tiling\": [").append(number(material.getTilingU())).append(", ").append(number(material.getTilingV())).append("],\n");
        out.append("  \"offset\": [").append(number(material.getOffsetU())).append(", ").append(number(material.getOffsetV())).append("],\n");
        out.append("  \"textures\": {");
        boolean first = true;
        for (Material.Slot slot : Material.Slot.values()) {
            String path = material.getTexture(slot);
            if (!path.isEmpty()) {
                out.append(first ? "\n    " : ",\n    ").append(quote(slot.name())).append(": ").append(quote(path));
                first = false;
            }
        }
        out.append(first ? "}\n" : "\n  }\n");
        out.append("}\n");
        return out.toString();
    }

    /**
     * Reads a material.
     *
     * @throws MaterialFormatException if the text is not a version {@value #VERSION} material file
     */
    public static Material fromJson(String json) {
        Objects.requireNonNull(json, "json cannot be null");
        JsonValue root;
        try {
            root = new JsonReader().parse(json);
        } catch (SerializationException e) {
            throw new MaterialFormatException("Not a material file: " + e.getMessage(), e);
        }
        if (root == null || !root.isObject()) {
            throw new MaterialFormatException("Not a material file: expected a JSON object");
        }
        int version = root.getInt("version", -1);
        if (version != VERSION) {
            throw new MaterialFormatException(version < 0 ? "Not a material file: it has no version"
                : "Material file version " + version + " is not supported (this editor reads version " + VERSION + ")");
        }
        Material material = new Material();
        try {
            if (root.has("name")) material.setName(text(root, "name"));
            if (root.has("baseColor")) material.setBaseColor(color(root, "baseColor", 4));
            if (root.has("metallic")) material.setMetallic(number(root, "metallic"));
            if (root.has("roughness")) material.setRoughness(number(root, "roughness"));
            if (root.has("emissive")) material.setEmissive(color(root, "emissive", 3));
            if (root.has("emissiveStrength")) material.setEmissiveStrength(number(root, "emissiveStrength"));
            if (root.has("normalScale")) material.setNormalScale(number(root, "normalScale"));
            if (root.has("occlusionStrength")) material.setOcclusionStrength(number(root, "occlusionStrength"));
            if (root.has("alphaMode")) material.setAlphaMode(alphaMode(text(root, "alphaMode")));
            if (root.has("alphaCutoff")) material.setAlphaCutoff(number(root, "alphaCutoff"));
            if (root.has("doubleSided")) {
                JsonValue flag = root.get("doubleSided");
                if (!flag.isBoolean()) {
                    throw new IllegalArgumentException("doubleSided must be true or false");
                }
                material.setDoubleSided(flag.asBoolean());
            }
            if (root.has("tiling")) {
                float[] tiling = floats(root, "tiling", 2);
                material.setTiling(tiling[0], tiling[1]);
            }
            if (root.has("offset")) {
                float[] offset = floats(root, "offset", 2);
                material.setOffset(offset[0], offset[1]);
            }
            JsonValue textures = root.get("textures");
            if (textures != null) {
                for (JsonValue entry = textures.child; entry != null; entry = entry.next) {
                    material.setTexture(slot(entry.name), entry.asString());
                }
            }
        } catch (IllegalArgumentException | UnsupportedOperationException | IllegalStateException e) {
            throw new MaterialFormatException("Bad material: " + e.getMessage(), e);
        }
        return material;
    }

    private static String text(JsonValue root, String field) {
        JsonValue value = root.get(field);
        if (!value.isString()) {
            throw new IllegalArgumentException(field + " must be text");
        }
        return value.asString();
    }

    private static float number(JsonValue root, String field) {
        JsonValue value = root.get(field);
        if (!value.isNumber()) {
            throw new IllegalArgumentException(field + " must be a number");
        }
        return value.asFloat();
    }

    private static float[] floats(JsonValue root, String field, int count) {
        JsonValue array = root.get(field);
        if (!array.isArray() || array.size != count) {
            throw new IllegalArgumentException(field + " must be a list of " + count + " numbers");
        }
        float[] values = new float[count];
        int i = 0;
        for (JsonValue item = array.child; item != null; item = item.next, i++) {
            if (!item.isNumber()) {
                throw new IllegalArgumentException(field + " must be a list of " + count + " numbers");
            }
            values[i] = item.asFloat();
        }
        return values;
    }

    private static Color color(JsonValue root, String field, int channels) {
        float[] v = floats(root, field, channels);
        return new Color(v[0], v[1], v[2], channels == 4 ? v[3] : 1f);
    }

    private static Material.AlphaMode alphaMode(String name) {
        try {
            return Material.AlphaMode.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("alphaMode must be one of OPAQUE, BLEND or MASK: " + name);
        }
    }

    private static Material.Slot slot(String name) {
        try {
            return Material.Slot.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown texture slot '" + name + "'; the slots are " + Arrays.toString(Material.Slot.values()));
        }
    }

    private static String array(Color color, boolean withAlpha) {
        return "[" + number(color.r) + ", " + number(color.g) + ", " + number(color.b) + (withAlpha ? ", " + number(color.a) : "") + "]";
    }

    private static String number(float value) {
        return Float.toString(value);
    }

    private static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
