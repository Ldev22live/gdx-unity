package io.github.ldev22live.gdxunity.engine.sculpt;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter.OutputType;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetProvenance;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.Locale;
import java.util.Objects;

/** Imports static glTF 2.0 models into self-contained editable mesh documents without OpenGL. */
public final class StaticMeshImport {
    private static final int GLB_MAGIC = 0x46546c67;
    private static final int JSON_CHUNK = 0x4e4f534a;
    private static final int BIN_CHUNK = 0x004e4942;
    private static final int TRIANGLES = 4;
    private static final String BUFFER_MIME = "application/octet-stream";

    private StaticMeshImport() {
    }

    /** Imports a model path relative to the given asset root and captures its provenance. */
    public static EditableMesh read(Path assetRoot, Path sourcePath) throws IOException {
        return read(assetRoot, sourcePath, (path, bytes) -> {});
    }

    static EditableMesh read(Path assetRoot, Path sourcePath, BiConsumer<String, byte[]> record) throws IOException {
        Objects.requireNonNull(assetRoot, "assetRoot cannot be null");
        Objects.requireNonNull(sourcePath, "sourcePath cannot be null");
        if (sourcePath.isAbsolute()) {
            throw new IllegalArgumentException("Expected a relative model path: " + sourcePath);
        }
        Path root = assetRoot.toRealPath();
        Path source = confined(root, root.resolve(sourcePath));
        String relativePath = root.relativize(source).toString().replace('\\', '/');
        if (relativePath.toLowerCase(Locale.ROOT).endsWith(".blend")) {
            throw new IOException("Blender source requires explicit conversion. Use tools/blend_to_static_glb.py to export a static object: " + relativePath);
        }
        byte[] sourceBytes = Files.readAllBytes(source);
        try {
            record.accept(relativePath, sourceBytes);
            String name = source.getFileName().toString().toLowerCase(Locale.ROOT);
            Container container = switch (name.substring(name.lastIndexOf('.') + 1)) {
                case "glb" -> readGlb(sourceBytes);
                case "gltf" -> new Container(sourceBytes, null);
                default -> throw new IllegalArgumentException("Expected a .glb or .gltf model");
            };
            JsonValue model = new JsonReader().parse(new String(container.json(), StandardCharsets.UTF_8));
            for (JsonValue buffer : items(model, "buffers")) {
                if (buffer.has("uri")) {
                    embed(buffer, root, source.getParent(), BUFFER_MIME, record);
                } else {
                    require(buffer == model.get("buffers").child && container.binary() != null,
                        "Missing buffer URI or GLB binary chunk");
                    setUri(buffer, BUFFER_MIME, container.binary());
                }
            }
            for (JsonValue image : items(model, "images")) {
                if (image.has("uri")) {
                    embed(image, root, source.getParent(), image.getString("mimeType", ""), record);
                }
            }
            return new EditableMesh(relativePath, checksum(sourceBytes), AssetProvenance.read(root.toFile()).of(relativePath),
                model.toJson(OutputType.json));
        } catch (IOException | RuntimeException e) {
            throw new IOException("Cannot import " + relativePath + ": " + e.getMessage(), e);
        }
    }

    private record Container(byte[] json, byte[] binary) {
    }

    private static Container readGlb(byte[] bytes) {
        ByteBuffer input = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        require(input.remaining() >= 20 && input.getInt() == GLB_MAGIC && input.getInt() == 2
            && input.getInt() == bytes.length, "Invalid GLB 2.0 header");
        byte[] json = null;
        byte[] binary = null;
        while (input.hasRemaining()) {
            require(input.remaining() >= 8, "Truncated GLB chunk header");
            int length = input.getInt();
            int type = input.getInt();
            require(length >= 0 && length % 4 == 0 && length <= input.remaining(), "Invalid GLB chunk length");
            require(json != null || type == JSON_CHUNK, "GLB must start with JSON");
            if (type == JSON_CHUNK) {
                require(json == null, "Duplicate GLB JSON chunk");
                json = new byte[length];
                input.get(json);
            } else if (type == BIN_CHUNK) {
                require(binary == null, "Duplicate GLB binary chunk");
                binary = new byte[length];
                input.get(binary);
            } else {
                input.position(input.position() + length);
            }
        }
        return new Container(json, binary);
    }

    private static void embed(JsonValue item, Path root, Path directory, String mime, BiConsumer<String, byte[]> record) throws IOException {
        String value = item.getString("uri");
        if (value.startsWith("data:")) {
            return;
        }
        URI uri = URI.create(value);
        require(!uri.isAbsolute() && uri.getAuthority() == null && uri.getQuery() == null
            && uri.getFragment() == null && uri.getPath() != null && !uri.getPath().startsWith("/"),
            "Expected a relative resource URI: " + value);
        Path resource = confined(root, directory.resolve(uri.getPath()));
        if (mime.isEmpty()) {
            String name = resource.getFileName().toString().toLowerCase(Locale.ROOT);
            mime = name.endsWith(".png") ? "image/png" : name.endsWith(".jpeg") || name.endsWith(".jpg") ? "image/jpeg" : "";
            require(!mime.isEmpty(), "Unsupported image type: " + value);
        }
        byte[] bytes = Files.readAllBytes(resource);
        record.accept(root.relativize(resource).toString().replace('\\', '/'), bytes);
        setUri(item, mime, bytes);
    }

    private static Path confined(Path root, Path file) throws IOException {
        Path resolved = file.normalize().toRealPath();
        if (!resolved.startsWith(root) || !Files.isRegularFile(resolved)) {
            throw new IOException("Resource is outside the asset root or is not a file: " + file);
        }
        return resolved;
    }

    private static void setUri(JsonValue item, String mime, byte[] data) {
        item.remove("uri");
        item.addChild("uri", new JsonValue("data:" + mime + ";base64," + Base64.getEncoder().encodeToString(data)));
    }

    private static byte[] embedded(String uri) {
        int comma = uri.indexOf(',');
        require(uri.startsWith("data:") && comma > 0 && uri.substring(0, comma).endsWith(";base64"),
            "Expected an embedded base64 resource");
        return Base64.getDecoder().decode(uri.substring(comma + 1));
    }

    static String checksum(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    static void validate(JsonValue model) {
        require(model.isObject() && "2.0".equals(model.require("asset").getString("version")), "Expected a glTF 2.0 model");
        for (String unsupported : new String[]{"extensionsRequired", "skins", "animations"}) {
            require(!items(model, unsupported).hasNext(), "Unsupported glTF " + unsupported);
        }
        List<ByteBuffer> buffers = new ArrayList<>();
        for (JsonValue buffer : items(model, "buffers")) {
            int length = buffer.getInt("byteLength");
            byte[] bytes = embedded(buffer.getString("uri"));
            require(length > 0 && bytes.length >= length, "Invalid buffer length");
            buffers.add(ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN));
        }
        for (JsonValue view : items(model, "bufferViews")) {
            long offset = view.getInt("byteOffset", 0);
            int length = view.getInt("byteLength");
            int bufferLength = reference(model, "buffers", view.getInt("buffer")).getInt("byteLength");
            require(offset >= 0 && length > 0 && offset + length <= bufferLength, "Buffer view outside its buffer");
        }
        for (JsonValue accessor : items(model, "accessors")) {
            require(!accessor.has("sparse"), "Sparse accessors are unsupported");
            JsonValue view = reference(model, "bufferViews", accessor.getInt("bufferView"));
            int bytes = switch (accessor.getInt("componentType")) {
                case 5120, 5121 -> 1;
                case 5122, 5123 -> 2;
                case 5125, 5126 -> 4;
                default -> throw new IllegalArgumentException("Unsupported accessor component type");
            };
            int components = switch (accessor.getString("type")) {
                case "SCALAR" -> 1;
                case "VEC2" -> 2;
                case "VEC3" -> 3;
                case "VEC4" -> 4;
                default -> throw new IllegalArgumentException("Unsupported accessor shape");
            };
            int width = bytes * components;
            int stride = view.getInt("byteStride", width);
            int offset = accessor.getInt("byteOffset", 0);
            int count = accessor.getInt("count");
            require(count > 0 && offset >= 0 && stride >= width
                && offset + (count - 1L) * stride + width <= view.getInt("byteLength"), "Accessor outside its buffer view");
        }
        for (JsonValue image : items(model, "images")) {
            if (image.has("uri")) {
                require(embedded(image.getString("uri")).length > 0, "Empty embedded image");
            } else {
                reference(model, "bufferViews", image.getInt("bufferView"));
            }
        }
        for (JsonValue texture : items(model, "textures")) {
            reference(model, "images", texture.getInt("source"));
            if (texture.has("sampler")) {
                reference(model, "samplers", texture.getInt("sampler"));
            }
        }
        for (JsonValue material : items(model, "materials")) {
            validateTextureSlots(model, material, "normalTexture", "occlusionTexture", "emissiveTexture");
            JsonValue pbr = material.get("pbrMetallicRoughness");
            if (pbr != null) {
                validateTextureSlots(model, pbr, "baseColorTexture", "metallicRoughnessTexture");
            }
        }
        require(items(model, "meshes").hasNext(), "Model has no meshes");
        for (JsonValue mesh : model.get("meshes")) {
            require(mesh.require("primitives").size > 0 && !mesh.has("weights"), "Empty mesh or unsupported morph weights");
            for (JsonValue primitive : mesh.get("primitives")) {
                validatePrimitive(model, primitive, buffers);
            }
        }
        for (JsonValue node : items(model, "nodes")) {
            require(!node.has("skin") && !node.has("weights"), "Unsupported skinned or morphing node");
            if (node.has("mesh")) {
                reference(model, "meshes", node.getInt("mesh"));
            }
            for (JsonValue child : items(node, "children")) {
                reference(model, "nodes", child.asInt());
            }
        }
        for (JsonValue scene : items(model, "scenes")) {
            for (JsonValue node : items(scene, "nodes")) {
                reference(model, "nodes", node.asInt());
            }
        }
        if (model.has("scene")) {
            reference(model, "scenes", model.getInt("scene"));
        }
    }

    private static void validateTextureSlots(JsonValue model, JsonValue material, String... slots) {
        for (String slot : slots) {
            JsonValue texture = material.get(slot);
            if (texture != null) {
                reference(model, "textures", texture.getInt("index"));
            }
        }
    }

    private static void validatePrimitive(JsonValue model, JsonValue primitive, List<ByteBuffer> buffers) {
        require(primitive.getInt("mode", TRIANGLES) == TRIANGLES && !primitive.has("targets"),
            "Only triangle primitives without morph targets are supported");
        JsonValue attributes = primitive.require("attributes");
        JsonValue positions = reference(model, "accessors", attributes.getInt("POSITION"));
        require("VEC3".equals(positions.getString("type")) && positions.getInt("componentType") == 5126,
            "Expected floating-point VEC3 positions");
        int vertexCount = positions.getInt("count");
        for (JsonValue attribute : attributes) {
            require(reference(model, "accessors", attribute.asInt()).getInt("count") == vertexCount,
                "Attribute count differs from POSITION: " + attribute.name);
        }
        if (primitive.has("material")) {
            reference(model, "materials", primitive.getInt("material"));
        }
        if (!primitive.has("indices")) {
            require(vertexCount % 3 == 0, "Triangle vertex count must be divisible by three");
            return;
        }
        JsonValue indices = reference(model, "accessors", primitive.getInt("indices"));
        int count = indices.getInt("count");
        require("SCALAR".equals(indices.getString("type")) && count % 3 == 0, "Invalid triangle index accessor");
        int width = switch (indices.getInt("componentType")) {
            case 5121 -> 1;
            case 5123 -> 2;
            case 5125 -> 4;
            default -> throw new IllegalArgumentException("Expected unsigned triangle indices");
        };
        JsonValue view = reference(model, "bufferViews", indices.getInt("bufferView"));
        ByteBuffer bytes = buffers.get(view.getInt("buffer"));
        int offset = view.getInt("byteOffset", 0) + indices.getInt("byteOffset", 0);
        int stride = view.getInt("byteStride", width);
        for (int i = 0; i < count; i++) {
            int location = offset + i * stride;
            long index = switch (width) {
                case 1 -> Byte.toUnsignedInt(bytes.get(location));
                case 2 -> Short.toUnsignedInt(bytes.getShort(location));
                default -> Integer.toUnsignedLong(bytes.getInt(location));
            };
            require(index < vertexCount, "Triangle index outside POSITION accessor");
        }
    }

    private static JsonValue.JsonIterator items(JsonValue object, String name) {
        JsonValue array = object.get(name);
        if (array == null) {
            return new JsonValue(JsonValue.ValueType.array).iterator();
        }
        require(array.isArray(), "Expected array: " + name);
        return array.iterator();
    }

    private static JsonValue reference(JsonValue model, String name, int index) {
        JsonValue array = model.get(name);
        require(array != null && array.isArray() && index >= 0 && index < array.size, "Invalid " + name + " index: " + index);
        return array.get(index);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
