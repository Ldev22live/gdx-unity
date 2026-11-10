package io.github.ldev22live.gdxunity.engine.character;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;

import java.io.StringWriter;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * What makes one character (TD-053): the four body settings, a height, a skin color and the values of the other sliders. It is
 * saved next to the baked model as {@code <name>.character.json}, so the character can be opened and changed again.
 */
public final class CharacterSpec {
    public static final int FORMAT_VERSION = 1;
    public static final float DEFAULT_GENDER = 0.5f;
    public static final float DEFAULT_AGE_YEARS = 25f;
    public static final float MIN_AGE_YEARS = 1f;
    public static final float MAX_AGE_YEARS = 90f;
    public static final float DEFAULT_BUILD = 0.5f;
    public static final float MIN_HEIGHT_SCALE = 0.7f;
    public static final float MAX_HEIGHT_SCALE = 1.3f;

    private float gender = DEFAULT_GENDER;
    private float ageYears = DEFAULT_AGE_YEARS;
    private float muscle = DEFAULT_BUILD;
    private float weight = DEFAULT_BUILD;
    private float heightScale = 1f;
    private final float[] skinColor = {0.85f, 0.65f, 0.52f};
    private final Map<String, Float> sliders = new TreeMap<>();

    /** 0 is female, 1 is male, 0.5 the neutral base body. */
    public float getGender() {
        return gender;
    }

    public void setGender(float gender) {
        this.gender = requireRange(gender, 0f, 1f, "gender");
    }

    /** Age in years, from 1 to 90; the body is blended between baby (1), child (11), young adult (25) and old (90). */
    public float getAgeYears() {
        return ageYears;
    }

    public void setAgeYears(float ageYears) {
        this.ageYears = requireRange(ageYears, MIN_AGE_YEARS, MAX_AGE_YEARS, "ageYears");
    }

    /** 0 weak, 0.5 average, 1 muscular. */
    public float getMuscle() {
        return muscle;
    }

    public void setMuscle(float muscle) {
        this.muscle = requireRange(muscle, 0f, 1f, "muscle");
    }

    /** 0 lean, 0.5 average, 1 heavy. */
    public float getWeight() {
        return weight;
    }

    public void setWeight(float weight) {
        this.weight = requireRange(weight, 0f, 1f, "weight");
    }

    /** Whole-body scale on top of the age and build: 1 keeps the natural height. */
    public float getHeightScale() {
        return heightScale;
    }

    public void setHeightScale(float heightScale) {
        this.heightScale = requireRange(heightScale, MIN_HEIGHT_SCALE, MAX_HEIGHT_SCALE, "heightScale");
    }

    /** Red, green, blue of the skin material, 0 to 1. */
    public float[] getSkinColor() {
        return skinColor.clone();
    }

    public void setSkinColor(float red, float green, float blue) {
        skinColor[0] = requireRange(red, 0f, 1f, "red");
        skinColor[1] = requireRange(green, 0f, 1f, "green");
        skinColor[2] = requireRange(blue, 0f, 1f, "blue");
    }

    /** Value of slider {@code id}, 0 when it was never set. */
    public float getSlider(String id) {
        return sliders.getOrDefault(Objects.requireNonNull(id, "id cannot be null"), 0f);
    }

    /** Sets a slider; 0 removes it. A one-sided slider (0 to 1) rejects negative values when the character is built. */
    public void setSlider(String id, float value) {
        Objects.requireNonNull(id, "id cannot be null");
        requireRange(value, -1f, 1f, id);
        if (value == 0f) {
            sliders.remove(id);
        } else {
            sliders.put(id, value);
        }
    }

    /** The sliders that are not 0, by id. */
    public Map<String, Float> getSliders() {
        return new TreeMap<>(sliders);
    }

    public CharacterSpec copy() {
        CharacterSpec copy = new CharacterSpec();
        copy.gender = gender;
        copy.ageYears = ageYears;
        copy.muscle = muscle;
        copy.weight = weight;
        copy.heightScale = heightScale;
        System.arraycopy(skinColor, 0, copy.skinColor, 0, 3);
        copy.sliders.putAll(sliders);
        return copy;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof CharacterSpec spec)) {
            return false;
        }
        return gender == spec.gender && ageYears == spec.ageYears && muscle == spec.muscle && weight == spec.weight
            && heightScale == spec.heightScale && Arrays.equals(skinColor, spec.skinColor) && sliders.equals(spec.sliders);
    }

    @Override
    public int hashCode() {
        return Objects.hash(gender, ageYears, muscle, weight, heightScale, Arrays.hashCode(skinColor), sliders);
    }

    /** The character file text. */
    public String toJson() {
        StringWriter text = new StringWriter();
        Json json = new Json();
        json.setWriter(new JsonWriter(text));
        json.writeObjectStart();
        json.writeValue("format", FORMAT_VERSION);
        json.writeValue("gender", gender);
        json.writeValue("ageYears", ageYears);
        json.writeValue("muscle", muscle);
        json.writeValue("weight", weight);
        json.writeValue("heightScale", heightScale);
        json.writeArrayStart("skinColor");
        for (float channel : skinColor) {
            json.writeValue(channel);
        }
        json.writeArrayEnd();
        json.writeObjectStart("sliders");
        for (Map.Entry<String, Float> entry : sliders.entrySet()) {
            json.writeValue(entry.getKey(), entry.getValue());
        }
        json.writeObjectEnd();
        json.writeObjectEnd();
        return json.prettyPrint(text.toString());
    }

    /**
     * @throws IllegalArgumentException when the text is not a character file of this version or has a value out of range
     */
    public static CharacterSpec fromJson(String text) {
        JsonValue root;
        try {
            root = new JsonReader().parse(Objects.requireNonNull(text, "text cannot be null"));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Not a character file: " + e.getMessage(), e);
        }
        if (root == null || root.getInt("format", -1) != FORMAT_VERSION) {
            throw new IllegalArgumentException("Not a character file of format " + FORMAT_VERSION);
        }
        CharacterSpec spec = new CharacterSpec();
        spec.setGender(root.getFloat("gender", DEFAULT_GENDER));
        spec.setAgeYears(root.getFloat("ageYears", DEFAULT_AGE_YEARS));
        spec.setMuscle(root.getFloat("muscle", DEFAULT_BUILD));
        spec.setWeight(root.getFloat("weight", DEFAULT_BUILD));
        spec.setHeightScale(root.getFloat("heightScale", 1f));
        JsonValue color = root.get("skinColor");
        if (color != null && color.size == 3) {
            spec.setSkinColor(color.getFloat(0), color.getFloat(1), color.getFloat(2));
        }
        JsonValue sliders = root.get("sliders");
        if (sliders != null) {
            for (JsonValue slider = sliders.child; slider != null; slider = slider.next) {
                spec.setSlider(slider.name, slider.asFloat());
            }
        }
        return spec;
    }

    private static float requireRange(float value, float min, float max, String name) {
        if (Float.isNaN(value) || value < min || value > max) {
            throw new IllegalArgumentException(name + " must be between " + min + " and " + max + ": " + value);
        }
        return value;
    }
}
