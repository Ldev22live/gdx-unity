package io.github.ldev22live.gdxunity.engine.audio.song;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.SerializationException;
import io.github.ldev22live.gdxunity.engine.audio.Effect;
import io.github.ldev22live.gdxunity.engine.audio.Effects;
import io.github.ldev22live.gdxunity.engine.audio.Mixer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Reads and writes the {@code .song} file: a Song as JSON, version {@value #VERSION}. */
public final class SongIO {
    /** Current file format version. */
    public static final int VERSION = 1;

    private SongIO() { }

    /** Quotes and escapes text as a JSON string. */
    static String quote(String text) {
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

    /** The song as JSON text. */
    public static String toJson(Song song) {
        Objects.requireNonNull(song, "song cannot be null");
        StringBuilder out = new StringBuilder();
        out.append(String.format(Locale.ROOT, "{\"version\":%d,\"name\":%s,\"bpm\":%s,\"swing\":%s,"
                + "\"nextChannelId\":%d,\"nextPatternId\":%d,\n\"channels\":[",
            VERSION, quote(song.getName()), song.getBpm(), song.getSwing(), song.getNextChannelId(),
            song.getNextPatternId()));
        boolean first = true;
        for (Channel channel : song.getChannels()) {
            out.append(first ? "\n" : ",\n");
            first = false;
            out.append(String.format(Locale.ROOT,
                "{\"id\":%d,\"name\":%s,\"mixerIndex\":%d,\"muted\":%s,\"stepPitch\":%d,\"instrument\":%s}",
                channel.getId(), quote(channel.getName()), channel.getMixerIndex(), channel.isMuted(),
                channel.getStepPitch(), channel.getInstrument().toJson()));
        }
        out.append("],\n\"patterns\":[");
        first = true;
        for (Pattern pattern : song.getPatterns()) {
            out.append(first ? "\n" : ",\n");
            first = false;
            out.append(String.format(Locale.ROOT, "{\"id\":%d,\"name\":%s,\"lengthTicks\":%d,\"notes\":{",
                pattern.getId(), quote(pattern.getName()), pattern.getLengthTicks()));
            boolean firstChannel = true;
            for (int channelId : pattern.getChannelsWithNotes()) {
                out.append(firstChannel ? "" : ",");
                firstChannel = false;
                out.append('"').append(channelId).append("\":[");
                boolean firstNote = true;
                for (Note note : pattern.getNotes(channelId)) {
                    out.append(firstNote ? "" : ",");
                    firstNote = false;
                    out.append(String.format(Locale.ROOT, "[%d,%d,%d,%s]",
                        note.tick(), note.length(), note.pitch(), note.velocity()));
                }
                out.append(']');
            }
            out.append("}}");
        }
        out.append("],\n\"clips\":[");
        first = true;
        for (PlaylistClip clip : song.getClips()) {
            out.append(first ? "" : ",");
            first = false;
            out.append(String.format(Locale.ROOT, "[%d,%d,%d,%d]",
                clip.patternId(), clip.track(), clip.startTick(), clip.lengthTicks()));
        }
        out.append("],\n\"mixer\":{\"strips\":[");
        Mixer mixer = song.getMixer();
        for (int i = 0; i < mixer.getStripCount(); i++) {
            out.append(i == 0 ? "\n" : ",\n").append(stripJson(mixer.getStrip(i)));
        }
        out.append("],\"master\":").append(stripJson(mixer.getMaster())).append("}}\n");
        return out.toString();
    }

    private static String stripJson(Mixer.Strip strip) {
        StringBuilder out = new StringBuilder();
        out.append(String.format(Locale.ROOT, "{\"name\":%s,\"volume\":%s,\"pan\":%s,\"muted\":%s,\"solo\":%s,\"effects\":[",
            quote(strip.getName()), strip.getVolume(), strip.getPan(), strip.isMuted(), strip.isSolo()));
        boolean first = true;
        for (Effect effect : strip.getEffects()) {
            out.append(first ? "" : ",");
            first = false;
            out.append("{\"type\":").append(quote(effect.getType())).append(",\"params\":{");
            boolean firstParam = true;
            for (Map.Entry<String, Float> entry : effect.getParameters().entrySet()) {
                out.append(firstParam ? "" : ",");
                firstParam = false;
                out.append(quote(entry.getKey())).append(':').append(String.format(Locale.ROOT, "%s", entry.getValue()));
            }
            out.append("}}");
        }
        return out.append("]}").toString();
    }

    /**
     * Reads a song.
     *
     * @throws IllegalArgumentException when the text is not a valid song file; the message says what is wrong
     */
    public static Song fromJson(String text) {
        Objects.requireNonNull(text, "text cannot be null");
        try {
            JsonValue root = new JsonReader().parse(text);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("A song file is a JSON object");
            }
            int version = root.getInt("version", -1);
            if (version != VERSION) {
                throw new IllegalArgumentException("Unsupported song file version " + version + " (expected " + VERSION + ")");
            }
            Song song = new Song(root.getString("name", "Untitled"));
            song.setBpm(root.getFloat("bpm", 120f));
            song.setSwing(root.getFloat("swing", 0f));
            readMixer(root.get("mixer"), song);
            for (JsonValue c = childOrNull(root, "channels"); c != null; c = c.next) {
                Channel channel = song.restoreChannel(c.getInt("id"), c.getString("name", "Channel"),
                    InstrumentSpec.fromJson(c.get("instrument")), c.getInt("mixerIndex", 0));
                channel.setMuted(c.getBoolean("muted", false));
                channel.setStepPitch(c.getInt("stepPitch", 60));
            }
            for (JsonValue p = childOrNull(root, "patterns"); p != null; p = p.next) {
                Pattern pattern = song.restorePattern(p.getInt("id"), p.getString("name", "Pattern"),
                    p.getInt("lengthTicks"));
                JsonValue notes = p.get("notes");
                for (JsonValue channelNotes = notes == null ? null : notes.child; channelNotes != null;
                     channelNotes = channelNotes.next) {
                    List<Note> list = new ArrayList<>();
                    for (JsonValue n = channelNotes.child; n != null; n = n.next) {
                        list.add(new Note(n.getInt(0), n.getInt(1), n.getInt(2), n.getFloat(3)));
                    }
                    pattern.setNotes(Integer.parseInt(channelNotes.name), list);
                }
            }
            List<PlaylistClip> clips = new ArrayList<>();
            for (JsonValue c = childOrNull(root, "clips"); c != null; c = c.next) {
                clips.add(new PlaylistClip(c.getInt(0), c.getInt(1), c.getInt(2), c.getInt(3)));
            }
            song.setClips(clips);
            song.setNextIds(Math.max(root.getInt("nextChannelId", 1), maxChannelId(song) + 1),
                Math.max(root.getInt("nextPatternId", 1), maxPatternId(song) + 1));
            return song;
        } catch (SerializationException | NumberFormatException | NullPointerException e) {
            throw new IllegalArgumentException("Invalid song file: " + e.getMessage(), e);
        }
    }

    private static JsonValue childOrNull(JsonValue parent, String name) {
        JsonValue array = parent.get(name);
        return array == null ? null : array.child;
    }

    private static int maxChannelId(Song song) {
        int max = 0;
        for (Channel channel : song.getChannels()) {
            max = Math.max(max, channel.getId());
        }
        return max;
    }

    private static int maxPatternId(Song song) {
        int max = 0;
        for (Pattern pattern : song.getPatterns()) {
            max = Math.max(max, pattern.getId());
        }
        return max;
    }

    private static void readMixer(JsonValue json, Song song) {
        if (json == null) {
            return;
        }
        int index = 0;
        for (JsonValue s = childOrNull(json, "strips"); s != null && index < song.getMixer().getStripCount(); s = s.next) {
            readStrip(s, song.getMixer().getStrip(index++));
        }
        JsonValue master = json.get("master");
        if (master != null) {
            readStrip(master, song.getMixer().getMaster());
        }
    }

    private static void readStrip(JsonValue json, Mixer.Strip strip) {
        strip.setName(json.getString("name", strip.getName()));
        strip.setVolume(json.getFloat("volume", strip.getVolume()));
        strip.setPan(json.getFloat("pan", 0f));
        strip.setMuted(json.getBoolean("muted", false));
        strip.setSolo(json.getBoolean("solo", false));
        for (JsonValue e = childOrNull(json, "effects"); e != null; e = e.next) {
            Effect effect = Effects.create(e.getString("type"), Song.SAMPLE_RATE);
            JsonValue params = e.get("params");
            for (JsonValue p = params == null ? null : params.child; p != null; p = p.next) {
                effect.setParameter(p.name, p.asFloat());
            }
            strip.addEffect(effect);
        }
    }
}
