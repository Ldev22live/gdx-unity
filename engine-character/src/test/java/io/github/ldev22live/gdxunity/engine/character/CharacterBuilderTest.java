package io.github.ldev22live.gdxunity.engine.character;

import com.badlogic.gdx.utils.GdxNativesLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The converted data, the morph math and the skin (TD-053), against the files that ship in {@code assets/characters}. */
class CharacterBuilderTest {
    private static final File ASSETS = new File("../assets");
    private static CharacterData data;

    @BeforeAll
    static void load() throws IOException {
        GdxNativesLoader.load();
        data = CharacterData.load(ASSETS);
    }

    private static BuiltCharacter build(CharacterSpec spec) {
        return CharacterBuilder.build(data, spec);
    }

    @Test
    void shouldLoadTheConvertedBodyRigAndSliders() {
        assertEquals(19158, data.baseVertexCount());
        assertEquals(14517, data.renderVertexCount());
        assertEquals(26756, data.triangles().length / 3);
        assertEquals(53, data.bones().size());
        assertEquals(-1, data.bones().get(0).parent());
        for (int i = 1; i < data.bones().size(); i++) {
            assertTrue(data.bones().get(i).parent() < i, "parents come before children: " + data.bones().get(i).name());
        }
        assertTrue(data.sliders().size() > 150, "sliders: " + data.sliders().size());
        assertEquals(421, data.targetCount());
    }

    @Test
    void shouldGiveEveryDrawnVertexWeightsThatAddUpToOne() {
        BuiltCharacter character = build(new CharacterSpec());
        for (int v = 0; v < character.vertexCount(); v++) {
            float sum = 0f;
            for (int k = 0; k < 4; k++) {
                sum += character.weights()[v * 4 + k];
            }
            assertEquals(1f, sum, 1e-3f, "weights of vertex " + v);
        }
    }

    @Test
    void shouldStandTheBodyOnTheFloorAtAHumanHeight() {
        BuiltCharacter character = build(new CharacterSpec());
        float lowest = Float.MAX_VALUE;
        for (int i = 1; i < character.positions().length; i += 3) {
            lowest = Math.min(lowest, character.positions()[i]);
        }
        assertEquals(0f, lowest, 1e-5f);
        assertTrue(character.height() > 1.55f && character.height() < 1.85f, "height " + character.height());
        // the head joint is above the hips joint, which is above the feet
        float head = character.jointHead(data.boneIndex("head"))[1];
        float pelvis = character.jointHead(data.boneIndex("pelvis"))[1];
        assertTrue(head > pelvis && pelvis > 0.7f && head < character.height(), "head " + head + ", pelvis " + pelvis);
    }

    @Test
    void shouldFaceTriangleNormalsOutwards() {
        BuiltCharacter character = build(new CharacterSpec());
        int outward = 0;
        int[] t = character.triangles();
        float[] p = character.positions();
        float[] n = character.normals();
        for (int i = 0; i < t.length; i += 3) {
            // the stored normal and the winding (right hand rule) agree for nearly every triangle
            float abx = p[t[i + 1] * 3] - p[t[i] * 3];
            float aby = p[t[i + 1] * 3 + 1] - p[t[i] * 3 + 1];
            float abz = p[t[i + 1] * 3 + 2] - p[t[i] * 3 + 2];
            float acx = p[t[i + 2] * 3] - p[t[i] * 3];
            float acy = p[t[i + 2] * 3 + 1] - p[t[i] * 3 + 1];
            float acz = p[t[i + 2] * 3 + 2] - p[t[i] * 3 + 2];
            float fx = aby * acz - abz * acy;
            float fy = abz * acx - abx * acz;
            float fz = abx * acy - aby * acx;
            float dot = fx * n[t[i] * 3] + fy * n[t[i] * 3 + 1] + fz * n[t[i] * 3 + 2];
            if (dot >= 0f) {
                outward++;
            }
        }
        assertTrue(outward > t.length / 3 * 0.98, outward + " of " + t.length / 3 + " triangles face the way their normals do");
    }

    @Test
    void shouldMakeMenTallerThanWomenAndChildrenShorterThanAdults() {
        CharacterSpec woman = new CharacterSpec();
        woman.setGender(0f);
        CharacterSpec man = new CharacterSpec();
        man.setGender(1f);
        CharacterSpec child = new CharacterSpec();
        child.setAgeYears(11f);
        CharacterSpec baby = new CharacterSpec();
        baby.setAgeYears(1f);

        assertTrue(build(man).height() > build(woman).height() + 0.05f, build(man).height() + " vs " + build(woman).height());
        assertTrue(build(child).height() < build(new CharacterSpec()).height() - 0.25f, "child " + build(child).height());
        assertTrue(build(baby).height() < build(child).height() - 0.2f, "baby " + build(baby).height());
    }

    @Test
    void shouldScaleTheWholeBodyWithTheHeightSetting() {
        CharacterSpec taller = new CharacterSpec();
        taller.setHeightScale(1.1f);
        assertEquals(build(new CharacterSpec()).height() * 1.1f, build(taller).height(), 1e-3f);
    }

    @Test
    void shouldWidenTheTorsoWithWeight() {
        CharacterSpec lean = new CharacterSpec();
        lean.setWeight(0f);
        CharacterSpec heavy = new CharacterSpec();
        heavy.setWeight(1f);

        assertTrue(width(build(heavy)) > width(build(lean)) * 1.05f, width(build(heavy)) + " vs " + width(build(lean)));
        
    }

    /** Mean distance from the body's vertical axis of the torso vertices (waist to chest, arms at the sides left out), meters. */
    private static float width(BuiltCharacter character) {
        double sum = 0.0;
        int count = 0;
        for (int i = 0; i < character.positions().length; i += 3) {
            float y = character.positions()[i + 1] / character.height();
            if (y > 0.5f && y < 0.65f && Math.abs(character.positions()[i]) < 0.28f) {
                sum += Math.hypot(character.positions()[i], character.positions()[i + 2]);
                count++;
            }
        }
        return (float) (sum / count);
    }

    @Test
    void shouldMoveOnlyTheVerticesASliderTargetNamesByItsOffsets() {
        SliderDef nose = data.slider("nose/nose-width1");
        CharacterData.Target target = data.target(nose.lowTarget());
        BuiltCharacter base = build(new CharacterSpec());
        CharacterSpec changed = new CharacterSpec();
        changed.setSlider(nose.id(), -1f);
        BuiltCharacter morphed = build(changed);

        int[] source = data.renderSource();
        java.util.Map<Integer, Integer> targetIndex = new java.util.HashMap<>();
        for (int i = 0; i < target.vertices().length; i++) {
            targetIndex.put(target.vertices()[i], i);
        }
        int moved = 0;
        for (int v = 0; v < base.vertexCount(); v++) {
            Integer entry = targetIndex.get(source[v]);
            for (int axis = 0; axis < 3; axis++) {
                float difference = morphed.positions()[v * 3 + axis] - base.positions()[v * 3 + axis];
                float expected = entry == null ? 0f : target.offsets()[entry * 3 + axis] * CharacterData.METERS_PER_UNIT;
                assertEquals(expected, difference, 1e-5f, "vertex " + v + " axis " + axis);
            }
            if (entry != null) {
                moved++;
            }
        }
        assertTrue(moved > 10, "the target names " + moved + " drawn vertices");
    }

    @Test
    void shouldRefuseANegativeValueForAOneSidedSliderAndAnUnknownSlider() {
        SliderDef oneSided = data.sliders().stream().filter(s -> !s.isSigned()).findFirst().orElseThrow();
        CharacterSpec spec = new CharacterSpec();
        spec.setSlider(oneSided.id(), -0.5f);
        assertThrows(IllegalArgumentException.class, () -> build(spec));
        CharacterSpec unknown = new CharacterSpec();
        unknown.setSlider("nose/no-such-slider", 0.5f);
        assertThrows(IllegalArgumentException.class, () -> build(unknown));
    }

    @Test
    void shouldBlendTheAgeNodesToOne() {
        for (float years : new float[] {1f, 6f, 11f, 18f, 25f, 60f, 90f}) {
            float sum = 0f;
            for (float weight : CharacterBuilder.ageWeights(years)) {
                sum += weight;
            }
            assertEquals(1f, sum, 1e-6f, "age " + years);
        }
    }

    @Test
    void shouldBuildTheSameCharacterTwice() {
        CharacterSpec spec = new CharacterSpec();
        spec.setGender(0.8f);
        spec.setSlider("nose/nose-width1", 0.4f);
        org.junit.jupiter.api.Assertions.assertArrayEquals(build(spec).positions(), build(spec).positions());
    }
}
