package io.github.ldev22live.gdxunity.engine.desktop;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenGameArtModelsTest {
    private static final Path MODELS = Path.of("../assets/models/thirdparty/opengameart");

    @Test
    void shouldKeepThePlatformerRigAndAuthoredClipsInOneFile() throws IOException {
        JsonValue model = readGlb("detective-bunny");
        assertEquals(30, model.get("skins").get(0).get("joints").size);
        assertEquals(Set.of("Idle", "Walk", "Jump", "Climb", "Talk", "Investigate"), clips(model));
        assertEmbeddedImages(model, 2);
    }

    @Test
    void shouldKeepBothFoxMeshHalvesAndTheWildlifeNormalMaps() throws IOException {
        JsonValue fox = readGlb("fox");
        int indexAccessor = fox.get("meshes").get(0).get("primitives").get(0).getInt("indices");
        assertEquals(342 * 3, fox.get("accessors").get(indexAccessor).getInt("count"));
        assertTrue(clips(fox).contains("IDLE"));
        assertTrue(fox.get("materials").get(0).has("normalTexture"));
        assertEmbeddedImages(fox, 2);

        JsonValue rabbit = readGlb("rabbit");
        assertTrue(clips(rabbit).contains("Sitting.000"));
        assertTrue(rabbit.get("materials").get(1).has("normalTexture"));
        assertEmbeddedImages(rabbit, 3);
    }

    private static JsonValue readGlb(String name) throws IOException {
        ByteBuffer glb = ByteBuffer.wrap(Files.readAllBytes(MODELS.resolve(name).resolve(name + ".glb")))
            .order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(0x46546c67, glb.getInt(), "glTF header");
        assertEquals(2, glb.getInt(), "GLB version");
        assertEquals(glb.limit(), glb.getInt(), "GLB byte length");
        byte[] json = new byte[glb.getInt()];
        assertEquals(0x4e4f534a, glb.getInt(), "GLB JSON chunk");
        glb.get(json);
        return new JsonReader().parse(new String(json, StandardCharsets.UTF_8));
    }

    private static Set<String> clips(JsonValue model) {
        Set<String> names = new LinkedHashSet<>();
        for (JsonValue clip : model.get("animations")) {
            assertTrue(clip.get("channels").size > 0);
            names.add(clip.getString("name"));
        }
        return names;
    }

    private static void assertEmbeddedImages(JsonValue model, int expectedCount) {
        JsonValue images = model.get("images");
        assertEquals(expectedCount, images.size);
        for (JsonValue image : images) {
            assertTrue(image.has("bufferView"));
            assertFalse(image.has("uri"));
        }
    }
}
