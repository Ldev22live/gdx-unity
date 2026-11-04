package io.github.ldev22live.gdxunity.editor.desktop.ui.music;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import io.github.ldev22live.gdxunity.editor.audio.MusicDocument;
import io.github.ldev22live.gdxunity.editor.audio.MusicSession;
import io.github.ldev22live.gdxunity.engine.audio.BiquadFilter;
import io.github.ldev22live.gdxunity.engine.audio.DrumKit;
import io.github.ldev22live.gdxunity.engine.audio.SfxrParams;
import io.github.ldev22live.gdxunity.engine.audio.SoundFont;
import io.github.ldev22live.gdxunity.engine.audio.SynthPatch;
import io.github.ldev22live.gdxunity.engine.audio.Waveform;
import io.github.ldev22live.gdxunity.engine.audio.song.Channel;
import io.github.ldev22live.gdxunity.engine.audio.song.InstrumentSpec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Settings of the selected channel's instrument: choose its kind (synth, drums, sample, sound effect, SoundFont),
 * route it to a mixer insert, and edit it. Synth knobs sound while dragged and are recorded as one undo step on
 * release; changes that replace the instrument (kind, preset, files, sound-effect settings) go through the song's
 * undo history too.
 */
final class InstrumentPanel extends Table {
    private static final List<String> KINDS = List.of("Synth", "Drums", "Sample", "Sound effect", "SoundFont");
    private static final float BAR_HEIGHT = 22f;
    private static final float LABEL_WIDTH = 110f;
    private static final float MUTATE_AMOUNT = 0.3f;
    private static final int MAX_SCAN_DEPTH = 6;

    private final Skin skin;
    private final MusicSession session;
    private SfxrParams workingSfxr;
    private String soundFontPath = "";
    private List<SoundFont.Preset> soundFontPresets = List.of();
    private String soundFontError;

    InstrumentPanel(Skin skin, MusicSession session) {
        super(skin);
        this.skin = Objects.requireNonNull(skin, "skin cannot be null");
        this.session = Objects.requireNonNull(session, "session cannot be null");
        setBackground("panel-bg");
        rebuild();
    }

    /** Rebuilds the panel for the selected channel. */
    void rebuild() {
        clearChildren();
        Channel channel = session.hasSong() ? session.getDocument().getSong().getChannel(session.getSelectedChannelId()) : null;
        if (channel == null) {
            add(new Label("Select a channel in the rack to edit its instrument", skin, "muted")).pad(10f);
            return;
        }
        Table content = new Table(skin);
        content.top().left().pad(8f);
        MusicDocument doc = session.getDocument();
        InstrumentSpec spec = channel.getInstrument();

        content.add(new Label("Channel", skin, "section")).left().colspan(2).padBottom(4f).row();
        TextField name = new TextField(channel.getName(), skin);
        name.setTextFieldListener((field, c) -> {
            if (c == '\r' || c == '\n') {
                doc.renameChannel(channel, field.getText().isBlank() ? channel.getName() : field.getText().trim());
            }
        });
        row(content, "Name", name);

        SelectBox<String> kind = new SelectBox<>(skin);
        kind.setItems(KINDS.toArray(new String[0]));
        kind.setSelectedIndex(kindIndex(spec.type));
        kind.addListener(onChange(() -> changeKind(channel, kind.getSelectedIndex())));
        row(content, "Instrument", kind);

        SelectBox<String> insert = new SelectBox<>(skin);
        List<String> inserts = new ArrayList<>();
        for (int i = 0; i < doc.getSong().getMixer().getStripCount(); i++) {
            inserts.add((i + 1) + " " + doc.getSong().getMixer().getStrip(i).getName());
        }
        insert.setItems(inserts.toArray(new String[0]));
        insert.setSelectedIndex(Math.min(channel.getMixerIndex(), inserts.size() - 1));
        insert.addListener(onChange(() -> doc.setChannelMixerIndex(channel, insert.getSelectedIndex())));
        row(content, "Mixer insert", insert);

        content.add(new Label("Sound", skin, "section")).left().colspan(2).padTop(10f).padBottom(4f).row();
        switch (spec.type) {
            case SYNTH -> buildSynth(content, channel, spec);
            case DRUMS -> buildDrums(content, channel);
            case SAMPLE -> buildSample(content, channel, spec);
            case SFXR -> buildSfxr(content, channel, spec);
            case SOUNDFONT -> buildSoundFont(content, channel, spec);
            default -> throw new IllegalStateException("Unknown instrument " + spec.type);
        }
        ScrollPane scroll = new ScrollPane(content, skin);
        scroll.setFadeScrollBars(false);
        add(scroll).grow();
    }

    private static int kindIndex(InstrumentSpec.Type type) {
        return switch (type) {
            case SYNTH -> 0;
            case DRUMS -> 1;
            case SAMPLE -> 2;
            case SFXR -> 3;
            case SOUNDFONT -> 4;
        };
    }

    private ChangeListener onChange(Runnable action) {
        return new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                action.run();
            }
        };
    }

    private void row(Table table, String label, Actor field) {
        table.add(new Label(label, skin, "secondary")).left().width(LABEL_WIDTH).padBottom(3f);
        table.add(field).growX().minWidth(220f).height(BAR_HEIGHT).padBottom(3f).row();
    }

    private void changeKind(Channel channel, int index) {
        InstrumentSpec current = channel.getInstrument();
        if (kindIndex(current.type) == index) {
            return;
        }
        InstrumentSpec replacement = switch (index) {
            case 0 -> InstrumentSpec.synth(SynthPatch.preset("Saw Lead"));
            case 1 -> InstrumentSpec.drums();
            case 2 -> {
                List<String> files = projectFiles(".wav");
                yield InstrumentSpec.sample(files.isEmpty() ? "" : files.get(0));
            }
            case 3 -> InstrumentSpec.sfxr(SfxrParams.preset("Coin"));
            default -> {
                List<String> files = projectFiles(".sf2");
                yield InstrumentSpec.soundFont(files.isEmpty() ? "" : files.get(0), 0, 0);
            }
        };
        session.getDocument().setInstrument(channel, replacement);
    }

    // ---- synth ----

    private void buildSynth(Table content, Channel channel, InstrumentSpec spec) {
        MusicDocument doc = session.getDocument();
        SynthPatch patch = spec.patch;
        List<String> presets = new ArrayList<>(SynthPatch.presetNames());
        presets.add(0, "Custom");
        SelectBox<String> preset = new SelectBox<>(skin);
        preset.setItems(presets.toArray(new String[0]));
        preset.addListener(onChange(() -> {
            if (preset.getSelectedIndex() > 0) {
                doc.setInstrument(channel, InstrumentSpec.synth(SynthPatch.preset(preset.getSelected())));
            }
        }));
        row(content, "Preset", preset);

        row(content, "Oscillator 1", waveformBox(patch.osc1, w -> patch.osc1 = w, "oscillator 1"));
        row(content, "Oscillator 2", waveformBox(patch.osc2, w -> patch.osc2 = w, "oscillator 2"));
        patchBar(content, "Osc 2 detune", -100f, 100f, 7f, false, "%.0f ct", () -> patch.osc2Detune, v -> patch.osc2Detune = v);
        patchBar(content, "Osc 2 octave", -2f, 2f, 0f, false, "%.0f", () -> (float) patch.osc2Octave, v -> patch.osc2Octave = Math.round(v));
        patchBar(content, "Osc 2 mix", 0f, 1f, 0.5f, false, "%.2f", () -> patch.osc2Mix, v -> patch.osc2Mix = v);
        patchBar(content, "Pulse width", 0.05f, 0.95f, 0.5f, false, "%.2f", () -> patch.pulseWidth, v -> patch.pulseWidth = v);
        content.add(new Label("Envelope", skin, "section")).left().colspan(2).padTop(8f).padBottom(4f).row();
        patchBar(content, "Attack", 0.001f, 5f, 0.01f, true, "%.3f s", () -> patch.attack, v -> patch.attack = v);
        patchBar(content, "Decay", 0.001f, 5f, 0.2f, true, "%.3f s", () -> patch.decay, v -> patch.decay = v);
        patchBar(content, "Sustain", 0f, 1f, 0.7f, false, "%.2f", () -> patch.sustain, v -> patch.sustain = v);
        patchBar(content, "Release", 0.001f, 5f, 0.25f, true, "%.3f s", () -> patch.release, v -> patch.release = v);
        content.add(new Label("Filter", skin, "section")).left().colspan(2).padTop(8f).padBottom(4f).row();
        SelectBox<BiquadFilter.Type> filter = new SelectBox<>(skin);
        filter.setItems(BiquadFilter.Type.values());
        filter.setSelected(patch.filterType);
        filter.addListener(onChange(() -> commitPatch(patch, "Change filter type", () -> patch.filterType = filter.getSelected())));
        row(content, "Type", filter);
        patchBar(content, "Cutoff", 20f, 20000f, 8000f, true, "%.0f Hz", () -> patch.cutoff, v -> patch.cutoff = v);
        patchBar(content, "Resonance", 0.5f, 20f, 0.7f, false, "%.2f", () -> patch.resonance, v -> patch.resonance = v);
        patchBar(content, "Envelope amount", 0f, 6f, 0f, false, "%.1f oct", () -> patch.filterEnvOctaves, v -> patch.filterEnvOctaves = v);
        content.add(new Label("LFO", skin, "section")).left().colspan(2).padTop(8f).padBottom(4f).row();
        SelectBox<SynthPatch.LfoTarget> target = new SelectBox<>(skin);
        target.setItems(SynthPatch.LfoTarget.values());
        target.setSelected(patch.lfoTarget);
        target.addListener(onChange(() -> commitPatch(patch, "Change LFO target", () -> patch.lfoTarget = target.getSelected())));
        row(content, "Target", target);
        patchBar(content, "Rate", 0.1f, 20f, 5f, true, "%.1f Hz", () -> patch.lfoRate, v -> patch.lfoRate = v);
        patchBar(content, "Depth", 0f, 2f, 0f, false, "%.2f", () -> patch.lfoDepth, v -> patch.lfoDepth = v);
        content.add(new Label("Output", skin, "section")).left().colspan(2).padTop(8f).padBottom(4f).row();
        patchBar(content, "Volume", 0f, 1f, 0.6f, false, "%.2f", () -> patch.volume, v -> patch.volume = v);
    }

    private SelectBox<Waveform> waveformBox(Waveform current, Consumer<Waveform> apply, String what) {
        SelectBox<Waveform> box = new SelectBox<>(skin);
        box.setItems(Waveform.values());
        box.setSelected(current);
        Channel channel = session.getDocument().getSong().getChannel(session.getSelectedChannelId());
        SynthPatch patch = channel.getInstrument().patch;
        box.addListener(onChange(() -> commitPatch(patch, "Change " + what, () -> apply.accept(box.getSelected()))));
        return box;
    }

    /** Applies a discrete change to a patch and records it as one undo step. */
    private void commitPatch(SynthPatch patch, String description, Runnable change) {
        SynthPatch before = patch.copy();
        change.run();
        session.getDocument().recordChange(description, before, patch.copy(), patch::copyFrom);
    }

    private void patchBar(Table table, String label, float min, float max, float def, boolean log, String format,
                          java.util.function.Supplier<Float> get, Consumer<Float> set) {
        MusicDocument doc = session.getDocument();
        ValueBar bar = new ValueBar(skin, label, min, max, def, false, log, format, get, set,
            (before, after) -> doc.recordChange("Change " + label.toLowerCase(Locale.ROOT), before, after, set));
        table.add(new Label("", skin)).width(LABEL_WIDTH);
        table.add(bar).growX().height(BAR_HEIGHT).padBottom(3f).row();
    }

    // ---- drums ----

    private void buildDrums(Table content, Channel channel) {
        MusicDocument doc = session.getDocument();
        content.add(new Label("Each drum answers to its own note. This channel's step row plays:", skin, "muted"))
            .left().colspan(2).padBottom(4f).row();
        SelectBox<String> drum = new SelectBox<>(skin);
        List<String> names = new ArrayList<>();
        for (DrumKit.Drum d : DrumKit.drums()) {
            names.add(d.getDisplayName());
        }
        drum.setItems(names.toArray(new String[0]));
        DrumKit.Drum current = DrumKit.drumForNote(channel.getStepPitch());
        drum.setSelectedIndex(current == null ? 0 : current.ordinal());
        drum.addListener(onChange(() -> doc.setStepPitch(channel, DrumKit.drums().get(drum.getSelectedIndex()).getNote())));
        row(content, "Step drum", drum);
    }

    // ---- sample ----

    private void buildSample(Table content, Channel channel, InstrumentSpec spec) {
        MusicDocument doc = session.getDocument();
        List<String> files = projectFiles(".wav");
        if (files.isEmpty()) {
            content.add(new Label("No .wav files in the project assets. Put a recording in the assets folder.", skin, "muted"))
                .left().colspan(2).row();
        } else {
            SelectBox<String> file = new SelectBox<>(skin);
            file.setItems(files.toArray(new String[0]));
            if (files.contains(spec.path)) {
                file.setSelected(spec.path);
            }
            file.addListener(onChange(() -> {
                InstrumentSpec changed = spec.copy();
                changed.path = file.getSelected();
                doc.setInstrument(channel, changed);
            }));
            row(content, "File", file);
        }
        if (!spec.path.isEmpty() && !files.contains(spec.path)) {
            content.add(new Label("Missing: " + spec.path, skin, "error")).left().colspan(2).row();
        }
        ValueBar root = new ValueBar(skin, "Root note", 0f, 127f, 60f, false, false, "%.0f",
            () -> (float) spec.rootNote, v -> { },
            (before, after) -> {
                InstrumentSpec changed = spec.copy();
                changed.rootNote = Math.round(after);
                doc.setInstrument(channel, changed);
            });
        content.add(new Label("", skin)).width(LABEL_WIDTH);
        content.add(root).growX().height(BAR_HEIGHT).padBottom(3f).row();
        TextButton oneShot = new TextButton(spec.oneShot ? "One-shot: plays to the end" : "Held: fades on note-off", skin, "flat");
        oneShot.addListener(onChange(() -> {
            InstrumentSpec changed = spec.copy();
            changed.oneShot = !spec.oneShot;
            doc.setInstrument(channel, changed);
        }));
        content.add(new Label("", skin)).width(LABEL_WIDTH);
        content.add(oneShot).left().height(BAR_HEIGHT).row();
        TextButton reload = new TextButton("Reload files", skin, "flat");
        reload.addListener(onChange(() -> {
            session.getPlayer().reloadFiles();
            rebuild();
        }));
        content.add(new Label("", skin)).width(LABEL_WIDTH);
        content.add(reload).left().height(BAR_HEIGHT).row();
    }

    // ---- sound effect (sfxr) ----

    private void buildSfxr(Table content, Channel channel, InstrumentSpec spec) {
        MusicDocument doc = session.getDocument();
        workingSfxr = spec.sfxr.copy();
        Table presets = new Table(skin);
        for (String name : SfxrParams.presetNames()) {
            TextButton button = new TextButton(name, skin, "flat");
            button.addListener(onChange(() -> replaceSfxr(channel, SfxrParams.preset(name))));
            presets.add(button).height(BAR_HEIGHT).padRight(2f).padBottom(2f);
        }
        content.add(presets).left().colspan(2).row();
        Table actions = new Table(skin);
        TextButton mutate = new TextButton("Mutate", skin, "flat");
        mutate.addListener(onChange(() -> replaceSfxr(channel, spec.sfxr.mutated(new Random().nextLong(), MUTATE_AMOUNT))));
        TextButton randomize = new TextButton("Randomize", skin, "flat");
        randomize.addListener(onChange(() -> replaceSfxr(channel, SfxrParams.randomized(new Random().nextLong()))));
        TextButton play = new TextButton("Play", skin, "primary");
        play.addListener(onChange(() -> {
            // Settings dragged but not released yet are not part of the instrument; play what is saved
            session.getPlayer().previewNoteOn(channel.getId(), spec.rootNote, 1f);
            session.getPlayer().previewNoteOff(channel.getId(), spec.rootNote);
        }));
        actions.add(mutate).height(BAR_HEIGHT).padRight(2f);
        actions.add(randomize).height(BAR_HEIGHT).padRight(2f);
        actions.add(play).height(BAR_HEIGHT);
        content.add(actions).left().colspan(2).padBottom(6f).row();

        SelectBox<SfxrParams.Shape> shape = new SelectBox<>(skin);
        shape.setItems(SfxrParams.Shape.values());
        shape.setSelected(spec.sfxr.shape);
        shape.addListener(onChange(() -> {
            SfxrParams changed = spec.sfxr.copy();
            changed.shape = shape.getSelected();
            replaceSfxr(channel, changed);
        }));
        row(content, "Wave", shape);
        sfxrBar(content, channel, "Start frequency", 0f, 1f, 0.3f, p -> p.baseFreq, (p, v) -> p.baseFreq = v);
        sfxrBar(content, channel, "Min frequency", 0f, 1f, 0f, p -> p.minFreq, (p, v) -> p.minFreq = v);
        sfxrBar(content, channel, "Slide", -1f, 1f, 0f, p -> p.slide, (p, v) -> p.slide = v);
        sfxrBar(content, channel, "Slide change", -1f, 1f, 0f, p -> p.deltaSlide, (p, v) -> p.deltaSlide = v);
        sfxrBar(content, channel, "Duty", 0f, 1f, 0f, p -> p.duty, (p, v) -> p.duty = v);
        sfxrBar(content, channel, "Duty sweep", -1f, 1f, 0f, p -> p.dutySweep, (p, v) -> p.dutySweep = v);
        sfxrBar(content, channel, "Vibrato depth", 0f, 1f, 0f, p -> p.vibratoDepth, (p, v) -> p.vibratoDepth = v);
        sfxrBar(content, channel, "Vibrato speed", 0f, 1f, 0f, p -> p.vibratoSpeed, (p, v) -> p.vibratoSpeed = v);
        sfxrBar(content, channel, "Attack", 0f, 1f, 0f, p -> p.attack, (p, v) -> p.attack = v);
        sfxrBar(content, channel, "Sustain", 0f, 1f, 0.3f, p -> p.sustain, (p, v) -> p.sustain = v);
        sfxrBar(content, channel, "Decay", 0f, 1f, 0.4f, p -> p.decay, (p, v) -> p.decay = v);
        sfxrBar(content, channel, "Punch", 0f, 1f, 0f, p -> p.punch, (p, v) -> p.punch = v);
        sfxrBar(content, channel, "Low-pass cutoff", 0f, 1f, 1f, p -> p.lowPassCutoff, (p, v) -> p.lowPassCutoff = v);
        sfxrBar(content, channel, "Low-pass sweep", -1f, 1f, 0f, p -> p.lowPassSweep, (p, v) -> p.lowPassSweep = v);
        sfxrBar(content, channel, "Resonance", 0f, 1f, 0f, p -> p.lowPassResonance, (p, v) -> p.lowPassResonance = v);
        sfxrBar(content, channel, "High-pass cutoff", 0f, 1f, 0f, p -> p.highPassCutoff, (p, v) -> p.highPassCutoff = v);
        sfxrBar(content, channel, "High-pass sweep", -1f, 1f, 0f, p -> p.highPassSweep, (p, v) -> p.highPassSweep = v);
        sfxrBar(content, channel, "Phaser offset", -1f, 1f, 0f, p -> p.phaserOffset, (p, v) -> p.phaserOffset = v);
        sfxrBar(content, channel, "Phaser sweep", -1f, 1f, 0f, p -> p.phaserSweep, (p, v) -> p.phaserSweep = v);
        sfxrBar(content, channel, "Repeat speed", 0f, 1f, 0f, p -> p.repeatSpeed, (p, v) -> p.repeatSpeed = v);
        sfxrBar(content, channel, "Pitch jump", -1f, 1f, 0f, p -> p.arpeggioChange, (p, v) -> p.arpeggioChange = v);
        sfxrBar(content, channel, "Pitch jump speed", 0f, 1f, 0f, p -> p.arpeggioSpeed, (p, v) -> p.arpeggioSpeed = v);
        sfxrBar(content, channel, "Volume", 0f, 1f, 0.5f, p -> p.volume, (p, v) -> p.volume = v);
    }

    /** Sound-effect settings are edited on a working copy and become the instrument (rebuilt, one undo step) on release. */
    private void sfxrBar(Table table, Channel channel, String label, float min, float max, float def,
                         Function<SfxrParams, Float> get, BiConsumer<SfxrParams, Float> set) {
        ValueBar bar = new ValueBar(skin, label, min, max, def, false, false, "%.2f",
            () -> get.apply(workingSfxr), v -> set.accept(workingSfxr, v),
            (before, after) -> replaceSfxr(channel, workingSfxr.copy()));
        table.add(new Label("", skin)).width(LABEL_WIDTH);
        table.add(bar).growX().height(BAR_HEIGHT).padBottom(2f).row();
    }

    private void replaceSfxr(Channel channel, SfxrParams params) {
        InstrumentSpec changed = channel.getInstrument().copy();
        changed.sfxr = params;
        session.getDocument().setInstrument(channel, changed);
    }

    // ---- SoundFont ----

    private void buildSoundFont(Table content, Channel channel, InstrumentSpec spec) {
        MusicDocument doc = session.getDocument();
        List<String> files = projectFiles(".sf2");
        if (files.isEmpty()) {
            content.add(new Label("No .sf2 SoundFonts in the project assets. Put one in the assets folder.", skin, "muted"))
                .left().colspan(2).row();
            return;
        }
        SelectBox<String> file = new SelectBox<>(skin);
        file.setItems(files.toArray(new String[0]));
        if (files.contains(spec.path)) {
            file.setSelected(spec.path);
        }
        file.addListener(onChange(() -> doc.setInstrument(channel, InstrumentSpec.soundFont(file.getSelected(), 0, 0))));
        row(content, "SoundFile", file);
        loadSoundFontPresets(spec.path);
        if (soundFontError != null) {
            content.add(new Label(soundFontError, skin, "error")).left().colspan(2).row();
            return;
        }
        SelectBox<String> preset = new SelectBox<>(skin);
        List<String> names = new ArrayList<>();
        int selected = 0;
        for (int i = 0; i < soundFontPresets.size(); i++) {
            SoundFont.Preset p = soundFontPresets.get(i);
            names.add(String.format(Locale.ROOT, "%03d:%03d %s", p.getBank(), p.getProgram(), p.getName()));
            if (p.getBank() == spec.bank && p.getProgram() == spec.program) {
                selected = i;
            }
        }
        preset.setItems(names.toArray(new String[0]));
        if (!names.isEmpty()) {
            preset.setSelectedIndex(selected);
        }
        preset.addListener(onChange(() -> {
            SoundFont.Preset chosen = soundFontPresets.get(preset.getSelectedIndex());
            doc.setInstrument(channel, InstrumentSpec.soundFont(spec.path, chosen.getBank(), chosen.getProgram()));
        }));
        row(content, "Preset", preset);
    }

    private void loadSoundFontPresets(String path) {
        if (path.equals(soundFontPath) && soundFontError == null) {
            return;
        }
        soundFontPath = path;
        soundFontError = null;
        soundFontPresets = List.of();
        try {
            soundFontPresets = SoundFont.read(Files.readAllBytes(session.getAssetRoot().resolve(path))).getPresets();
        } catch (IOException | RuntimeException e) {
            soundFontError = "Could not read " + path + ": " + e.getMessage();
        }
    }

    // ---- files ----

    /** Project-relative paths of files with the extension, sorted. */
    private List<String> projectFiles(String extension) {
        Path root = session.getAssetRoot();
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(root, MAX_SCAN_DEPTH)) {
            List<String> found = new ArrayList<>();
            walk.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(extension))
                .forEach(p -> found.add(root.relativize(p).toString().replace('\\', '/')));
            Collections.sort(found);
            return found;
        } catch (IOException e) {
            return List.of();
        }
    }
}
