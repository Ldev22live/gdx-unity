package io.github.ldev22live.gdxunity.engine.character;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.animation.AnimationClip;
import io.github.ldev22live.gdxunity.engine.animation.AnimationClipSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The baked model file and the animation templates applied to the rig (TD-053). Loading in a renderer is tested in engine-render. */
class GltfCharacterWriterTest {
    private static final File ASSETS = new File("../assets");
    private static CharacterData data;
    private static BuiltCharacter character;

    @BeforeAll
    static void load() throws IOException {
        GdxNativesLoader.load();
        data = CharacterData.load(ASSETS);
        character = CharacterBuilder.build(data, new CharacterSpec());
    }

    private static AnimationClip template(String name) throws IOException {
        return AnimationClipSerializer.fromJson(Files.readString(new File(ASSETS, "animations/humanoid/" + name + ".anim").toPath()));
    }

    @Test
    void shouldWriteASkinnedMeshWithOneNodePerBoneAndConsistentCounts() throws IOException {
        String text = GltfCharacterWriter.write(character, new float[] {0.8f, 0.6f, 0.5f}, List.of(HumanoidTemplates.bake(template("Walk"), character)));

        JsonValue root = new JsonReader().parse(text);
        assertEquals("2.0", root.get("asset").getString("version"));
        assertEquals(54, root.get("nodes").size, "53 bones and the body mesh");
        assertEquals(53, root.get("skins").get(0).get("joints").size);
        JsonValue accessors = root.get("accessors");
        assertEquals(character.vertexCount(), accessors.get(0).getInt("count"));
        assertEquals(character.triangles().length, accessors.get(5).getInt("count"));
        assertEquals(53, accessors.get(6).getInt("count"));
        assertEquals(1, root.get("animations").size);
        assertEquals("Walk", root.get("animations").get(0).getString("name"));
        // the embedded buffer is as long as it says
        String uri = root.get("buffers").get(0).getString("uri");
        assertEquals(root.get("buffers").get(0).getInt("byteLength"), Base64.getDecoder().decode(uri.substring(uri.indexOf(',') + 1)).length);
        // the color is given as shown on screen (sRGB) and written linear, as glTF wants: 0.8 is 0.604 linear
        assertEquals(0.604f, root.get("materials").get(0).get("pbrMetallicRoughness").get("baseColorFactor").getFloat(0), 1e-3f);
        // POSITION bounds are the body's: standing on y = 0
        assertEquals(0f, accessors.get(0).get("min").getFloat(1), 1e-5f);
        assertEquals(character.height(), accessors.get(0).get("max").getFloat(1), 1e-5f);
    }

    @Test
    void shouldPlaceEveryBoneAtItsJointThroughTheBindMatrices() {
        String text = GltfCharacterWriter.write(character, new float[] {1f, 1f, 1f}, List.of());
        JsonValue root = new JsonReader().parse(text);
        String uri = root.get("buffers").get(0).getString("uri");
        ByteBuffer buffer = ByteBuffer.wrap(Base64.getDecoder().decode(uri.substring(uri.indexOf(',') + 1))).order(ByteOrder.LITTLE_ENDIAN);
        JsonValue view = root.get("bufferViews").get(root.get("accessors").get(6).getInt("bufferView"));
        int offset = view.getInt("byteOffset");
        for (int b = 0; b < 53; b++) {
            float[] head = character.jointHead(b);
            // column-major 4x4: the translation is in the last column
            assertEquals(-head[0], buffer.getFloat(offset + (b * 16 + 12) * 4), 1e-6f);
            assertEquals(-head[1], buffer.getFloat(offset + (b * 16 + 13) * 4), 1e-6f);
            assertEquals(-head[2], buffer.getFloat(offset + (b * 16 + 14) * 4), 1e-6f);
        }
        assertNull(root.get("animations"), "no templates, no animations");
    }

    @Test
    void shouldBakeTheHumanoidTemplatesOntoTheRigsBones() throws IOException {
        String[] names = new File(ASSETS, "animations/humanoid").list((dir, file) -> file.endsWith(".anim"));
        assertEquals(10, names.length, "the five original and the five added templates");
        for (String file : names) {
            String name = file.substring(0, file.length() - ".anim".length());
            BakedAnimation baked = HumanoidTemplates.bake(template(name), character);
            assertEquals(name, baked.name());
            assertTrue(baked.problems().isEmpty(), name + ": " + baked.problems());
            assertTrue(baked.channels().size() >= 1, name);
            assertEquals(0f, baked.times()[0]);
            assertEquals(template(name).getDuration(), baked.times()[baked.times().length - 1], 1e-5f);
        }
    }

    @Test
    void shouldSwingTheThighsOfAWalkingCharacterOppositelyAboutTheSideways() throws IOException {
        BakedAnimation walk = HumanoidTemplates.bake(template("Walk"), character);
        int left = data.boneIndex("thigh_l");
        int right = data.boneIndex("thigh_r");
        float[] leftRotations = rotationsOf(walk, left);
        float[] rightRotations = rotationsOf(walk, right);
        assertNotNull(leftRotations);
        assertNotNull(rightRotations);

        // The template swings the thighs by +-28 degrees about the sideways axis, in opposite directions
        Quaternion a = new Quaternion(leftRotations[0], leftRotations[1], leftRotations[2], leftRotations[3]);
        Quaternion b = new Quaternion(rightRotations[0], rightRotations[1], rightRotations[2], rightRotations[3]);
        assertEquals(28f, Math.abs(a.getPitch()), 0.5f);
        assertEquals(-a.getPitch(), b.getPitch(), 0.5f);
        // The pelvis bobs between 2 cm below and 3 cm above where it stands
        int pelvis = data.boneIndex("pelvis");
        float rest = HumanoidTemplates.restTranslation(character, pelvis)[1];
        float lowest = Float.MAX_VALUE;
        float highest = -Float.MAX_VALUE;
        for (BakedAnimation.Channel channel : walk.channels()) {
            if (channel.bone() == pelvis && channel.translations() != null) {
                for (int i = 1; i < channel.translations().length; i += 3) {
                    lowest = Math.min(lowest, channel.translations()[i] - rest);
                    highest = Math.max(highest, channel.translations()[i] - rest);
                }
            }
        }
        assertEquals(-0.02f, lowest, 0.002f);
        assertEquals(0.03f, highest, 0.002f);
    }

    @Test
    void shouldReportTracksTheRigCannotTake() {
        AnimationClip clip = new AnimationClip("Odd", 1f, 30);
        clip.addTrack(new io.github.ldev22live.gdxunity.engine.animation.AnimationTrack("Tail", "transform.rotation.pitch",
            new io.github.ldev22live.gdxunity.engine.animation.AnimationCurve()));
        clip.addTrack(new io.github.ldev22live.gdxunity.engine.animation.AnimationTrack("Hips/Head", "mesh.color.r",
            new io.github.ldev22live.gdxunity.engine.animation.AnimationCurve()));

        BakedAnimation baked = HumanoidTemplates.bake(clip, character);

        assertEquals(2, baked.problems().size(), baked.problems().toString());
        assertTrue(baked.problems().get(0).contains("Tail"));
        assertTrue(baked.channels().isEmpty());
    }

    private static float[] rotationsOf(BakedAnimation animation, int bone) {
        for (BakedAnimation.Channel channel : animation.channels()) {
            if (channel.bone() == bone) {
                return channel.rotations();
            }
        }
        return null;
    }
}
