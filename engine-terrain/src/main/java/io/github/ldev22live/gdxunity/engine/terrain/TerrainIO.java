package io.github.ldev22live.gdxunity.engine.terrain;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.SerializationException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;

/**
 * Reads and writes the {@code .terrain} file: one {@link Terrain} as JSON, version {@value #VERSION} (TD-082). A file may also carry "surfaces" (TD-083): one byte per grid point, the code of its
 * {@link Surface}; a file without it is all {@link Surface#AUTO}. Since TD-089 (roadmap M3.4) it may also carry, each only when used:
 * "layerCount" and "layers" (that many paint layer weights per grid point, 0 to 255, point after point, in surface order from Grass;
 * an older file with fewer layers reads, the missing ones empty), "holes" (one byte per point, 1 for a hole) and "waterLevel" (a
 * number). A file without them reads as before, so every existing terrain is unchanged. The heights are every
 * grid point's height as a 32-bit float, little endian, row by row along x, in one Base64 string, so a map is a few hundred kilobytes
 * and round-trips exactly. Reading is strict: a newer version, a wrong type, a grid out of range or heights of the wrong length is an
 * error that names the problem.
 */
public final class TerrainIO {
    /** Current file format version. */
    public static final int VERSION = 1;

    private TerrainIO() { }

    /** The terrain as JSON text; the same terrain always gives the same text. */
    public static String toJson(Terrain terrain) {
        Objects.requireNonNull(terrain, "terrain cannot be null");
        float[] heights = terrain.copyHeights();
        ByteBuffer bytes = ByteBuffer.allocate(heights.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        bytes.asFloatBuffer().put(heights);
        String surfaces = terrain.hasSurfaces()
            ? ",\n  \"surfaces\": \"" + Base64.getEncoder().encodeToString(terrain.copySurfaces()) + "\"" : "";
        String layers = terrain.hasWeights()
            ? ",\n  \"layerCount\": " + Surface.LAYER_COUNT + ",\n  \"layers\": \""
                + Base64.getEncoder().encodeToString(terrain.copyWeights()) + "\"" : "";
        String holes = terrain.hasHoles()
            ? ",\n  \"holes\": \"" + Base64.getEncoder().encodeToString(terrain.copyHoles()) + "\"" : "";
        String water = terrain.hasWater()
            ? ",\n  \"waterLevel\": " + String.format(Locale.ROOT, "%s", terrain.getWaterLevel()) : "";
        return "{\n"
            + "  \"version\": " + VERSION + ",\n"
            + "  \"name\": \"" + escape(terrain.getName()) + "\",\n"
            + "  \"resolutionX\": " + terrain.getResolutionX() + ",\n"
            + "  \"resolutionZ\": " + terrain.getResolutionZ() + ",\n"
            + "  \"cellSize\": " + String.format(Locale.ROOT, "%s", terrain.getCellSize()) + ",\n"
            + "  \"heights\": \"" + Base64.getEncoder().encodeToString(bytes.array()) + "\"" + surfaces + layers + holes + water + "\n"
            + "}\n";
    }

    /**
     * Reads a terrain.
     *
     * @throws TerrainFormatException if the text is not a version {@value #VERSION} terrain file
     */
    public static Terrain fromJson(String json) {
        Objects.requireNonNull(json, "json cannot be null");
        JsonValue root;
        try {
            root = new JsonReader().parse(json);
        } catch (SerializationException e) {
            throw new TerrainFormatException("Not a terrain file: " + e.getMessage(), e);
        }
        if (root == null || !root.isObject()) {
            throw new TerrainFormatException("Not a terrain file: expected a JSON object");
        }
        int version = root.getInt("version", -1);
        if (version != VERSION) {
            throw new TerrainFormatException(version < 0 ? "Not a terrain file: it has no version"
                : "Terrain file version " + version + " is not supported (this editor reads version " + VERSION + ")");
        }
        try {
            int resolutionX = integer(root, "resolutionX");
            int resolutionZ = integer(root, "resolutionZ");
            Terrain terrain = new Terrain(resolutionX, resolutionZ, number(root, "cellSize"));
            if (root.has("name")) {
                JsonValue name = root.get("name");
                if (!name.isString()) {
                    throw new IllegalArgumentException("name must be text");
                }
                terrain.setName(name.asString());
            }
            JsonValue encoded = root.get("heights");
            if (encoded == null || !encoded.isString()) {
                throw new IllegalArgumentException("heights must be a Base64 string");
            }
            byte[] bytes = Base64.getDecoder().decode(encoded.asString());
            if (bytes.length != resolutionX * resolutionZ * Float.BYTES) {
                throw new IllegalArgumentException("heights hold " + bytes.length + " bytes but a " + resolutionX + " x " + resolutionZ
                    + " grid needs " + resolutionX * resolutionZ * Float.BYTES);
            }
            ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            for (int iz = 0; iz < resolutionZ; iz++) {
                for (int ix = 0; ix < resolutionX; ix++) {
                    terrain.setHeight(ix, iz, buffer.getFloat());
                }
            }
            JsonValue surfaceData = root.get("surfaces");
            if (surfaceData != null) {
                if (!surfaceData.isString()) {
                    throw new IllegalArgumentException("surfaces must be a Base64 string");
                }
                byte[] codes = Base64.getDecoder().decode(surfaceData.asString());
                if (codes.length != resolutionX * resolutionZ) {
                    throw new IllegalArgumentException("surfaces hold " + codes.length + " bytes but a " + resolutionX + " x " + resolutionZ
                        + " grid needs " + resolutionX * resolutionZ);
                }
                for (int iz = 0; iz < resolutionZ; iz++) {
                    for (int ix = 0; ix < resolutionX; ix++) {
                        terrain.setSurface(ix, iz, Surface.fromCode(codes[iz * resolutionX + ix]));
                    }
                }
            }
            readLayers(root, terrain);
            readHoles(root, terrain);
            if (root.has("waterLevel")) {
                terrain.setWaterLevel(number(root, "waterLevel"));
            }
            return terrain;
        } catch (IllegalArgumentException | UnsupportedOperationException | IllegalStateException e) {
            throw new TerrainFormatException("Bad terrain: " + e.getMessage(), e);
        }
    }

    /** The paint layer weights (TD-089): {@code layerCount} bytes per point, never summing past full at a point. */
    private static void readLayers(JsonValue root, Terrain terrain) {
        JsonValue data = root.get("layers");
        if (data == null) {
            if (root.has("layerCount")) {
                throw new IllegalArgumentException("layerCount is given but layers is missing");
            }
            return;
        }
        if (!data.isString()) {
            throw new IllegalArgumentException("layers must be a Base64 string");
        }
        int count = integer(root, "layerCount");
        if (count < 1 || count > Surface.LAYER_COUNT) {
            throw new IllegalArgumentException("layerCount must be 1 to " + Surface.LAYER_COUNT + " (this editor's layers): " + count);
        }
        byte[] weights = Base64.getDecoder().decode(data.asString());
        int points = terrain.getResolutionX() * terrain.getResolutionZ();
        if (weights.length != points * count) {
            throw new IllegalArgumentException("layers hold " + weights.length + " bytes but " + points + " points with " + count
                + " layers need " + points * count);
        }
        for (int point = 0; point < points; point++) {
            int sum = 0;
            for (int layer = 0; layer < count; layer++) {
                sum += weights[point * count + layer] & 0xFF;
            }
            if (sum > Terrain.FULL_WEIGHT) {
                throw new IllegalArgumentException("the layer weights of point " + point + " sum to " + sum + ", more than "
                    + Terrain.FULL_WEIGHT);
            }
            int ix = point % terrain.getResolutionX();
            int iz = point / terrain.getResolutionX();
            for (int layer = 0; layer < count; layer++) {
                terrain.setLayerWeight(ix, iz, Surface.ofLayer(layer), weights[point * count + layer] & 0xFF);
            }
        }
    }

    /** The hole flags (TD-089): one byte per point, 0 or 1. */
    private static void readHoles(JsonValue root, Terrain terrain) {
        JsonValue data = root.get("holes");
        if (data == null) {
            return;
        }
        if (!data.isString()) {
            throw new IllegalArgumentException("holes must be a Base64 string");
        }
        byte[] flags = Base64.getDecoder().decode(data.asString());
        int points = terrain.getResolutionX() * terrain.getResolutionZ();
        if (flags.length != points) {
            throw new IllegalArgumentException("holes hold " + flags.length + " bytes but the grid has " + points + " points");
        }
        for (int point = 0; point < points; point++) {
            if (flags[point] != 0 && flags[point] != 1) {
                throw new IllegalArgumentException("holes must be 0 or 1 per point; point " + point + " is " + flags[point]);
            }
            terrain.setHole(point % terrain.getResolutionX(), point / terrain.getResolutionX(), flags[point] == 1);
        }
    }

    private static int integer(JsonValue root, String field) {
        JsonValue value = root.get(field);
        if (value == null || !value.isNumber()) {
            throw new IllegalArgumentException(field + " must be a number");
        }
        return value.asInt();
    }

    private static float number(JsonValue root, String field) {
        JsonValue value = root.get(field);
        if (value == null || !value.isNumber()) {
            throw new IllegalArgumentException(field + " must be a number");
        }
        return value.asFloat();
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
