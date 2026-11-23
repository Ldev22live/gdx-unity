package io.github.ldev22live.gdxunity.engine.animation;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import com.badlogic.gdx.utils.SerializationException;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializationException;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes {@code .anim} files (TD-031):
 * <pre>
 * { "format": 1, "name": "Bounce", "duration": 2, "frameRate": 30,
 *   "tracks": [ { "target": "", "property": "transform.position.y",
 *                 "keys": [ { "t": 0, "v": 0, "i": "BEZIER" }, ... ] } ] }
 * </pre>
 */
public final class AnimationClipSerializer {

    public static final int FORMAT_VERSION = 1;

    private AnimationClipSerializer() {}

    public static String toJson(AnimationClip clip) {
        StringWriter out = new StringWriter();
        Json json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);
        json.setWriter(out);
        json.writeObjectStart();
        json.writeValue("format", FORMAT_VERSION);
        json.writeValue("name", clip.getName());
        json.writeValue("duration", clip.getDuration());
        json.writeValue("frameRate", clip.getFrameRate());
        json.writeArrayStart("tracks");
        for (AnimationTrack track : clip.getTracks()) {
            json.writeObjectStart();
            json.writeValue("target", track.getTargetPath());
            json.writeValue("property", track.getPropertyId());
            json.writeArrayStart("keys");
            for (Keyframe key : track.getCurve().getKeys()) {
                json.writeObjectStart();
                json.writeValue("t", key.time());
                json.writeValue("v", key.value());
                json.writeValue("i", key.interpolation().name());
                json.writeObjectEnd();
            }
            json.writeArrayEnd();
            json.writeObjectEnd();
        }
        json.writeArrayEnd();
        json.writeObjectEnd();
        return json.prettyPrint(out.toString());
    }

    /**
     * @throws SceneSerializationException if the text is not a valid clip; the message names what is wrong
     */
    public static AnimationClip fromJson(String text) {
        JsonValue root;
        try {
            root = new JsonReader().parse(text);
        } catch (SerializationException e) {
            throw new SceneSerializationException("Not a valid animation file: " + e.getMessage(), e);
        }
        if (root == null || !root.isObject()) {
            throw new SceneSerializationException("Not a valid animation file: expected a JSON object");
        }
        int format = root.getInt("format", FORMAT_VERSION);
        if (format > FORMAT_VERSION) {
            throw new SceneSerializationException("Animation file format " + format + " is newer than this editor ("
                + FORMAT_VERSION + ")");
        }
        try {
            AnimationClip clip = new AnimationClip(root.getString("name", "Clip"),
                root.getFloat("duration", AnimationClip.DEFAULT_DURATION),
                root.getInt("frameRate", AnimationClip.DEFAULT_FRAME_RATE));
            JsonValue tracks = root.get("tracks");
            if (tracks != null) {
                for (JsonValue trackJson = tracks.child; trackJson != null; trackJson = trackJson.next) {
                    clip.addTrack(readTrack(trackJson));
                }
            }
            return clip;
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new SceneSerializationException("Invalid animation file: " + e.getMessage(), e);
        }
    }

    private static AnimationTrack readTrack(JsonValue trackJson) {
        String property = trackJson.getString("property", null);
        if (property == null) {
            throw new SceneSerializationException("Animation track without 'property'");
        }
        List<Keyframe> keys = new ArrayList<>();
        JsonValue keysJson = trackJson.get("keys");
        if (keysJson != null) {
            for (JsonValue keyJson = keysJson.child; keyJson != null; keyJson = keyJson.next) {
                String interpolation = keyJson.getString("i", Interpolation.LINEAR.name());
                try {
                    keys.add(new Keyframe(keyJson.getFloat("t"), keyJson.getFloat("v"), Interpolation.valueOf(interpolation)));
                } catch (IllegalArgumentException e) {
                    throw new SceneSerializationException("Invalid key on " + property + ": " + e.getMessage(), e);
                }
            }
        }
        return new AnimationTrack(trackJson.getString("target", ""), property, new AnimationCurve(keys));
    }
}
