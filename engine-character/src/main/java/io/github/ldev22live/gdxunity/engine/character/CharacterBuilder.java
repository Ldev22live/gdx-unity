package io.github.ldev22live.gdxunity.engine.character;

import java.util.Map;
import java.util.Objects;

/**
 * Builds a {@link BuiltCharacter} from a {@link CharacterSpec} (TD-053): blends the macro targets for gender and age, adds the
 * muscle and weight differences and the slider targets to the base body, scales and stands it on the floor, moves the skeleton's
 * joints with the body and skins the drawn mesh.
 *
 * <p>Macro blend: the neutral base body plus the weighted sum of the bodies of both genders at the four age nodes (baby 1, child 11,
 * young 25, old 90 years, linear in between), each the equal mix of three ethnic variants. Muscle and weight add the young adult's
 * strong or weak (heavy or lean) variant, scaled by how far the slider is from 0.5.
 */
public final class CharacterBuilder {
    private static final String MACRO = "macrodetails/";
    private static final String[] GENDERS = {"female", "male"};
    private static final String[] AGES = {"baby", "child", "young", "old"};
    private static final String[] ETHNICITIES = {"african", "asian", "caucasian"};
    private static final float[] AGE_NODES = {1f, 11f, 25f, 90f};

    private CharacterBuilder() {}

    /**
     * @throws IllegalArgumentException for a slider the data does not have or a one-sided slider below 0
     */
    public static BuiltCharacter build(CharacterData data, CharacterSpec spec) {
        Objects.requireNonNull(data, "data cannot be null");
        Objects.requireNonNull(spec, "spec cannot be null");
        float[] units = data.basePositions();
        applyMacros(data, spec, units);
        for (Map.Entry<String, Float> entry : spec.getSliders().entrySet()) {
            SliderDef slider = data.slider(entry.getKey());
            float value = entry.getValue();
            if (!slider.isSigned() && value < 0f) {
                throw new IllegalArgumentException("Slider " + slider.id() + " goes from 0 to 1, not " + value);
            }
            if (value < 0f) {
                add(units, data.target(slider.lowTarget()), -value);
            } else {
                add(units, data.target(slider.highTarget()), value);
            }
        }

        int[] source = data.renderSource();
        float scale = CharacterData.METERS_PER_UNIT * spec.getHeightScale();
        float feet = Float.MAX_VALUE;
        float top = -Float.MAX_VALUE;
        for (int s : source) {
            feet = Math.min(feet, units[s * 3 + 1]);
            top = Math.max(top, units[s * 3 + 1]);
        }
        for (int v = 0; v < units.length; v += 3) {
            units[v] *= scale;
            units[v + 1] = (units[v + 1] - feet) * scale;
            units[v + 2] *= scale;
        }

        int renderCount = source.length;
        float[] positions = new float[renderCount * 3];
        for (int i = 0; i < renderCount; i++) {
            System.arraycopy(units, source[i] * 3, positions, i * 3, 3);
        }
        int[] triangles = data.triangles();
        float[] normals = smoothNormals(units, source, triangles);

        int boneCount = data.bones().size();
        float[] heads = new float[boneCount * 3];
        for (int b = 0; b < boneCount; b++) {
            int[] vertices = data.bones().get(b).head();
            for (int vertex : vertices) {
                heads[b * 3] += units[vertex * 3] / vertices.length;
                heads[b * 3 + 1] += units[vertex * 3 + 1] / vertices.length;
                heads[b * 3 + 2] += units[vertex * 3 + 2] / vertices.length;
            }
        }

        int[] joints = new int[renderCount * CharacterData.MAX_INFLUENCES];
        float[] weights = new float[renderCount * CharacterData.MAX_INFLUENCES];
        for (int i = 0; i < renderCount; i++) {
            int[] bones = data.weightBones(source[i]);
            float[] values = data.weightValues(source[i]);
            for (int k = 0; k < bones.length; k++) {
                joints[i * CharacterData.MAX_INFLUENCES + k] = bones[k];
                weights[i * CharacterData.MAX_INFLUENCES + k] = values[k];
            }
        }
        return new BuiltCharacter(positions, normals, data.renderUvs(), triangles, joints, weights, data.bones(), heads,
            (top - feet) * scale);
    }

    private static void applyMacros(CharacterData data, CharacterSpec spec, float[] units) {
        float[] genderWeights = {1f - spec.getGender(), spec.getGender()};
        float[] ageWeights = ageWeights(spec.getAgeYears());
        float muscle = (spec.getMuscle() - CharacterSpec.DEFAULT_BUILD) * 2f;
        float weightSlider = (spec.getWeight() - CharacterSpec.DEFAULT_BUILD) * 2f;
        for (int g = 0; g < GENDERS.length; g++) {
            for (int a = 0; a < AGES.length; a++) {
                float weight = genderWeights[g] * ageWeights[a] / ETHNICITIES.length;
                if (weight > 0f) {
                    for (String ethnicity : ETHNICITIES) {
                        add(units, data.target(MACRO + ethnicity + "-" + GENDERS[g] + "-" + AGES[a]), weight);
                    }
                }
            }
            String young = MACRO + "universal-" + GENDERS[g] + "-young-";
            addIf(units, data, young + (muscle >= 0f ? "maxmuscle-averageweight" : "minmuscle-averageweight"), genderWeights[g] * Math.abs(muscle));
            addIf(units, data, young + (weightSlider >= 0f ? "averagemuscle-maxweight" : "averagemuscle-minweight"),
                genderWeights[g] * Math.abs(weightSlider));
        }
    }

    private static void addIf(float[] units, CharacterData data, String name, float weight) {
        if (weight > 0f) {
            add(units, data.target(name), weight);
        }
    }

    /** How much of each age node (baby, child, young, old) a body of {@code years} has. */
    static float[] ageWeights(float years) {
        float[] weights = new float[AGE_NODES.length];
        if (years <= AGE_NODES[0]) {
            weights[0] = 1f;
            return weights;
        }
        for (int i = 0; i < AGE_NODES.length - 1; i++) {
            if (years <= AGE_NODES[i + 1]) {
                float t = (years - AGE_NODES[i]) / (AGE_NODES[i + 1] - AGE_NODES[i]);
                weights[i] = 1f - t;
                weights[i + 1] = t;
                return weights;
            }
        }
        weights[AGE_NODES.length - 1] = 1f;
        return weights;
    }

    private static void add(float[] units, CharacterData.Target target, float weight) {
        int[] vertices = target.vertices();
        float[] offsets = target.offsets();
        for (int i = 0; i < vertices.length; i++) {
            units[vertices[i] * 3] += offsets[i * 3] * weight;
            units[vertices[i] * 3 + 1] += offsets[i * 3 + 1] * weight;
            units[vertices[i] * 3 + 2] += offsets[i * 3 + 2] * weight;
        }
    }

    /** Area-weighted vertex normals over the base vertices, so seams in the texture do not show as creases. */
    private static float[] smoothNormals(float[] units, int[] source, int[] triangles) {
        float[] sums = new float[units.length];
        for (int t = 0; t < triangles.length; t += 3) {
            int a = source[triangles[t]] * 3;
            int b = source[triangles[t + 1]] * 3;
            int c = source[triangles[t + 2]] * 3;
            float abx = units[b] - units[a];
            float aby = units[b + 1] - units[a + 1];
            float abz = units[b + 2] - units[a + 2];
            float acx = units[c] - units[a];
            float acy = units[c + 1] - units[a + 1];
            float acz = units[c + 2] - units[a + 2];
            float nx = aby * acz - abz * acy;
            float ny = abz * acx - abx * acz;
            float nz = abx * acy - aby * acx;
            for (int corner : new int[] {a, b, c}) {
                sums[corner] += nx;
                sums[corner + 1] += ny;
                sums[corner + 2] += nz;
            }
        }
        float[] normals = new float[source.length * 3];
        for (int i = 0; i < source.length; i++) {
            int s = source[i] * 3;
            float length = (float) Math.sqrt(sums[s] * sums[s] + sums[s + 1] * sums[s + 1] + sums[s + 2] * sums[s + 2]);
            if (length > 0f) {
                normals[i * 3] = sums[s] / length;
                normals[i * 3 + 1] = sums[s + 1] / length;
                normals[i * 3 + 2] = sums[s + 2] / length;
            } else {
                normals[i * 3 + 1] = 1f;
            }
        }
        return normals;
    }
}
