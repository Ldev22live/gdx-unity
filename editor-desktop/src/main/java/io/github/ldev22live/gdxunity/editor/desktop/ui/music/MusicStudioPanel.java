package io.github.ldev22live.gdxunity.editor.desktop.ui.music;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Disposable;
import io.github.ldev22live.gdxunity.editor.audio.MusicDocument;
import io.github.ldev22live.gdxunity.editor.audio.MusicSession;
import io.github.ldev22live.gdxunity.editor.desktop.ui.TabbedPanel;
import io.github.ldev22live.gdxunity.engine.audio.SynthPatch;
import io.github.ldev22live.gdxunity.engine.audio.SfxrParams;
import io.github.ldev22live.gdxunity.engine.audio.song.Channel;
import io.github.ldev22live.gdxunity.engine.audio.song.InstrumentSpec;
import io.github.ldev22live.gdxunity.engine.audio.song.Pattern;
import io.github.ldev22live.gdxunity.engine.audio.song.Song;
import io.github.ldev22live.gdxunity.engine.audio.song.SongPlayer;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The music editor (TD-040): transport and song controls on top, and the channel rack with its step sequencer, the
 * piano roll, the playlist, the mixer and the instrument settings as tabs below. Everything edits the
 * {@link MusicDocument} of the {@link MusicSession}, so each change is undoable with Ctrl+Z while a music view has
 * focus. Sound is sent to the speakers by a background thread that starts the first time Play is pressed.
 */
public final class MusicStudioPanel extends Table implements Disposable {
    private static final String NEW_SONG_DIRECTORY = "music/";
    private static final int[] PATTERN_BARS = {1, 2, 4, 8, 16};
    private static final float CONTROL_HEIGHT = 24f;
    private static final float TRANSPORT_PAD = 4f;
    private static final String NO_SONG = "(no song)";
    private static final int TAB_COUNT = 5;

    private final Skin skin;
    private final MusicSession session;
    private final MusicAudioOutput output;
    private final Table transport;
    private final StepSequencerView rack;
    private final PianoRollView pianoRoll;
    private final PlaylistView playlist;
    private final MixerView mixer;
    private final InstrumentPanel instrument;
    private final TabbedPanel tabs;
    private final Label status;
    private final Label position;
    private MusicDocument boundDocument;
    private final MusicDocument.Listener documentListener = this::onDocumentChanged;
    private boolean structureChanged = true;
    private boolean updating;
    private boolean autoPlayDone;

    public MusicStudioPanel(Skin skin, MusicSession session) {
        super(skin);
        this.skin = Objects.requireNonNull(skin, "skin cannot be null");
        this.session = Objects.requireNonNull(session, "session cannot be null");
        this.output = new MusicAudioOutput(session::getPlayer);
        setBackground("panel-bg");

        rack = new StepSequencerView(skin, session);
        pianoRoll = new PianoRollView(skin, session);
        playlist = new PlaylistView(skin, session);
        mixer = new MixerView(skin, session);
        instrument = new InstrumentPanel(skin, session);
        status = new Label("", skin, "muted");
        position = new Label("", skin);
        transport = new Table(skin);
        transport.left();
        tabs = new TabbedPanel(skin, List.of("Channel rack", "Piano roll", "Playlist", "Mixer", "Instrument"),
            List.of(rackTab(), pianoRoll, playlist, mixer, instrument), initialTab(), index -> { });

        add(transport).growX().pad(TRANSPORT_PAD).row();
        add(tabs).grow().row();
        add(status).left().pad(TRANSPORT_PAD);
        addListener(new SpaceToPlay());
        selectChannelFromTestingAid();
    }

    /** Testing aid: {@code -Dgdxunity.musicTab=2} starts on the third tab (0 rack, 1 piano roll, 2 playlist, 3 mixer, 4 instrument). */
    private static int initialTab() {
        return Math.max(0, Math.min(Integer.getInteger("gdxunity.musicTab", 0), TAB_COUNT - 1));
    }

    /** Testing aid: {@code -Dgdxunity.musicChannel=3} starts with the fourth channel of the open song selected. */
    private void selectChannelFromTestingAid() {
        Integer index = Integer.getInteger("gdxunity.musicChannel");
        if (index != null && session.hasSong() && index >= 0 && index < session.getDocument().getSong().getChannels().size()) {
            session.selectChannel(session.getDocument().getSong().getChannels().get(index).getId());
        }
    }

    /** The rack with the buttons that add and remove channels beneath it. */
    private Table rackTab() {
        Table table = new Table(skin);
        table.add(rack).grow().row();
        Table buttons = new Table(skin);
        buttons.left();
        buttons.add(actionButton("+ Synth", () -> addChannel("Synth", InstrumentSpec.synth(SynthPatch.preset("Saw Lead")))))
            .height(CONTROL_HEIGHT).padRight(6f);
        buttons.add(actionButton("+ Drums", () -> addChannel("Drum", InstrumentSpec.drums())))
            .height(CONTROL_HEIGHT).padRight(6f);
        buttons.add(actionButton("+ Sound effect", () -> addChannel("Effect", InstrumentSpec.sfxr(SfxrParams.preset("Coin")))))
            .height(CONTROL_HEIGHT).padRight(8f);
        buttons.add(actionButton("Clear row", this::clearSelectedChannel)).height(CONTROL_HEIGHT).padRight(6f);
        buttons.add(actionButton("Remove channel", this::removeSelectedChannel)).height(CONTROL_HEIGHT);
        table.add(buttons).left().pad(TRANSPORT_PAD);
        return table;
    }

    private TextButton actionButton(String text, Runnable action) {
        TextButton button = new TextButton(text, skin, "flat");
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (!updating) {
                    action.run();
                }
            }
        });
        return button;
    }

    // ---- commands from the buttons ----

    private void addChannel(String baseName, InstrumentSpec spec) {
        if (!session.hasSong()) {
            return;
        }
        MusicDocument doc = session.getDocument();
        Channel channel = doc.addChannel(baseName + " " + (doc.getSong().getChannels().size() + 1), spec);
        if (channel == null) {
            status.setText("A song can have at most " + Song.MAX_CHANNELS + " channels");
            return;
        }
        if (spec.type == InstrumentSpec.Type.DRUMS) {
            doc.setStepPitch(channel, io.github.ldev22live.gdxunity.engine.audio.DrumKit.Drum.KICK.getNote());
        }
        session.selectChannel(channel.getId());
    }

    private void clearSelectedChannel() {
        Channel channel = selectedChannel();
        Pattern pattern = session.getSelectedPattern();
        if (channel != null && pattern != null) {
            session.getDocument().clearChannel(pattern, channel);
        }
    }

    private void removeSelectedChannel() {
        Channel channel = selectedChannel();
        if (channel != null) {
            session.getDocument().removeChannel(channel);
        }
    }

    private Channel selectedChannel() {
        return session.hasSong() ? session.getDocument().getSong().getChannel(session.getSelectedChannelId()) : null;
    }

    // ---- transport ----

    private void rebuildTransport() {
        updating = true;
        transport.clearChildren();
        List<String> songs = session.listSongs();

        transport.add(actionButton("New song", this::newSong)).height(CONTROL_HEIGHT).padRight(4f);
        SelectBox<String> songBox = new SelectBox<>(skin);
        List<String> shown = new ArrayList<>(songs);
        if (shown.isEmpty() || !session.hasSong()) {
            shown.add(0, NO_SONG);
        }
        songBox.setItems(shown.toArray(new String[0]));
        songBox.setSelected(session.hasSong() && shown.contains(session.getDocument().getPath())
            ? session.getDocument().getPath() : NO_SONG);
        songBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (!updating && !NO_SONG.equals(songBox.getSelected())) {
                    openSong(songBox.getSelected());
                }
            }
        });
        transport.add(songBox).height(CONTROL_HEIGHT).minWidth(160f).padRight(4f);
        if (!session.hasSong()) {
            transport.add(new Label("Create a song to start making music", skin, "muted")).padLeft(8f);
            updating = false;
            return;
        }
        MusicDocument doc = session.getDocument();
        Song song = doc.getSong();
        SongPlayer player = session.getPlayer();

        transport.add(actionButton("Save", () -> {
            if (!session.save()) {
                status.setText(session.getLastError());
            } else {
                status.setText("Saved " + doc.getPath());
            }
        })).height(CONTROL_HEIGHT).padRight(6f);
        transport.add(actionButton("Undo", doc::undo)).height(CONTROL_HEIGHT).padRight(6f);
        transport.add(actionButton("Redo", doc::redo)).height(CONTROL_HEIGHT).padRight(14f);
        transport.add(actionButton("Export WAV", this::exportWav)).height(CONTROL_HEIGHT).padRight(14f);
        transport.add(position).minWidth(130f);
        transport.row().padTop(4f);

        transport.add(actionButton("Play / Pause", this::togglePlay)).height(CONTROL_HEIGHT).padRight(6f);
        transport.add(actionButton("Stop", player::stop)).height(CONTROL_HEIGHT).padRight(6f);
        TextButton loop = actionButton(player.isLoop() ? "Loop: on" : "Loop: off", () -> {
            player.setLoop(!player.isLoop());
            structureChanged = true;
        });
        transport.add(loop).height(CONTROL_HEIGHT).padRight(6f);
        TextButton mode = actionButton(session.isSongMode() ? "Mode: song" : "Mode: pattern", () -> {
            session.setSongMode(!session.isSongMode());
            player.stop();
            structureChanged = true;
        });
        transport.add(mode).height(CONTROL_HEIGHT).padRight(14f);

        transport.add(new Label("BPM", skin, "secondary")).padRight(4f);
        TextField bpm = new TextField(String.format(Locale.ROOT, "%.1f", song.getBpm()), skin);
        bpm.setTextFieldListener((field, c) -> {
            if (c == '\r' || c == '\n') {
                try {
                    doc.setBpm(Float.parseFloat(field.getText().trim()));
                } catch (NumberFormatException e) {
                    status.setText("BPM must be a number between " + (int) Song.MIN_BPM + " and " + (int) Song.MAX_BPM);
                }
                structureChanged = true;
            }
        });
        transport.add(bpm).width(72f).height(CONTROL_HEIGHT).padRight(6f);
        ValueBar swing = new ValueBar(skin, "Swing", 0f, 1f, 0f, false, false, "%.2f",
            song::getSwing, song::setSwing, (before, after) -> doc.recordChange("Set swing", before, after, song::setSwing));
        transport.add(swing).width(130f).height(CONTROL_HEIGHT).padRight(6f);
        transport.add(actionButton("Snap " + session.getSnap().getLabel(), () -> {
            session.setSnap(session.getSnap().next());
            structureChanged = true;
        })).height(CONTROL_HEIGHT).padRight(14f);

        transport.add(new Label("Pattern", skin, "secondary")).padRight(4f);
        SelectBox<String> patternBox = new SelectBox<>(skin);
        List<String> names = new ArrayList<>();
        int selectedIndex = 0;
        for (int i = 0; i < song.getPatterns().size(); i++) {
            Pattern p = song.getPatterns().get(i);
            names.add(p.getName());
            if (p.getId() == session.getSelectedPattern().getId()) {
                selectedIndex = i;
            }
        }
        patternBox.setItems(names.toArray(new String[0]));
        patternBox.setSelectedIndex(selectedIndex);
        patternBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (!updating) {
                    session.selectPattern(song.getPatterns().get(patternBox.getSelectedIndex()).getId());
                    structureChanged = true;
                }
            }
        });
        transport.add(patternBox).height(CONTROL_HEIGHT).minWidth(110f).padRight(2f);
        transport.add(actionButton("+", () -> {
            Pattern added = doc.addPattern("Pattern " + (song.getPatterns().size() + 1), 1);
            if (added != null) {
                session.selectPattern(added.getId());
            }
        })).size(CONTROL_HEIGHT, CONTROL_HEIGHT).padRight(6f);
        transport.add(actionButton("-", () -> {
            Pattern current = session.getSelectedPattern();
            if (!doc.removePattern(current)) {
                status.setText("A song needs at least one pattern");
            }
        })).size(CONTROL_HEIGHT, CONTROL_HEIGHT).padRight(4f);
        SelectBox<String> bars = new SelectBox<>(skin);
        List<String> barNames = new ArrayList<>();
        int barIndex = 0;
        for (int i = 0; i < PATTERN_BARS.length; i++) {
            barNames.add(PATTERN_BARS[i] + (PATTERN_BARS[i] == 1 ? " bar" : " bars"));
            if (PATTERN_BARS[i] == session.getSelectedPattern().getBars()) {
                barIndex = i;
            }
        }
        bars.setItems(barNames.toArray(new String[0]));
        bars.setSelectedIndex(barIndex);
        bars.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (!updating) {
                    doc.setPatternBars(session.getSelectedPattern(), PATTERN_BARS[bars.getSelectedIndex()]);
                }
            }
        });
        transport.add(bars).height(CONTROL_HEIGHT).padRight(14f);

        updating = false;
    }

    private void newSong() {
        int number = 1;
        while (Files.exists(session.getAssetRoot().resolve(NEW_SONG_DIRECTORY + "song_" + number + MusicSession.SONG_EXTENSION))) {
            number++;
        }
        if (session.createSong(NEW_SONG_DIRECTORY + "song_" + number)) {
            status.setText("Created " + session.getDocument().getPath());
        } else {
            status.setText(session.getLastError());
        }
        structureChanged = true;
    }

    private void openSong(String path) {
        if (session.hasSong() && path.equals(session.getDocument().getPath())) {
            return;
        }
        status.setText(session.open(path) ? "Opened " + path : session.getLastError());
        structureChanged = true;
    }

    private void exportWav() {
        MusicDocument doc = session.getDocument();
        String base = doc.getPath().substring(doc.getPath().lastIndexOf('/') + 1, doc.getPath().length() - MusicSession.SONG_EXTENSION.length());
        String target = "audio/" + base + ".wav";
        boolean whole = session.isSongMode();
        status.setText(session.exportWav(target, whole) ? "Exported " + (whole ? "the song" : "the pattern") + " to " + target
            : session.getLastError());
    }

    private void togglePlay() {
        if (!session.hasSong()) {
            return;
        }
        output.start();
        if (output.getError() != null) {
            status.setText(output.getError());
        }
        SongPlayer player = session.getPlayer();
        if (player.isPlaying()) {
            player.pause();
        } else {
            player.play();
        }
    }

    // ---- frame update ----

    @Override
    public void act(float delta) {
        session.update();
        MusicDocument doc = session.getDocument();
        if (doc != boundDocument) {
            if (boundDocument != null) {
                boundDocument.removeListener(documentListener);
            }
            boundDocument = doc;
            if (doc != null) {
                doc.addListener(documentListener);
            }
            structureChanged = true;
        }
        if (!autoPlayDone && session.hasSong() && Boolean.getBoolean("gdxunity.musicAutoPlay")) {
            // Testing aid: -Dgdxunity.musicAutoPlay=true presses Play once the song is open
            autoPlayDone = true;
            togglePlay();
        }
        if (structureChanged) {
            structureChanged = false;
            rebuildTransport();
            mixer.rebuild();
            instrument.rebuild();
        }
        if (session.hasSong()) {
            double ticks = session.getPlayer().getPositionTicks();
            int bar = (int) (ticks / Song.TICKS_PER_BAR) + 1;
            int beat = (int) (ticks % Song.TICKS_PER_BAR / Song.TICKS_PER_BEAT) + 1;
            position.setText(String.format(Locale.ROOT, "%s  %d.%d", session.getPlayer().isPlaying() ? "Playing" : "Stopped", bar, beat)
                + (output.isRunning() ? String.format(Locale.ROOT, "  (%.1f s of sound sent)", output.getFramesWritten() / Song.SAMPLE_RATE) : ""));
            List<String> problems = session.getPlayer().getErrors();
            if (!problems.isEmpty()) {
                status.setText(String.join("; ", problems));
            }
        }
        super.act(delta);
    }

    private void onDocumentChanged(MusicDocument changed) {
        structureChanged = true;
    }

    /** Saves unsaved changes; false when saving failed. Called when the editor closes. */
    public boolean saveIfDirty() {
        boolean saved = session.saveIfDirty();
        if (!saved) {
            status.setText(session.getLastError());
        }
        return saved;
    }

    /** True when a song with unsaved changes is open. */
    public boolean isDirty() {
        return session.hasSong() && session.getDocument().isDirty();
    }

    /** Space starts and stops playback from any music view. */
    private final class SpaceToPlay extends InputListener {
        @Override
        public boolean keyDown(InputEvent event, int keycode) {
            if (keycode != Input.Keys.SPACE || getStage() == null || getStage().getKeyboardFocus() instanceof TextField) {
                return false;
            }
            togglePlay();
            return true;
        }
    }

    @Override
    public void dispose() {
        output.dispose();
        rack.dispose();
        pianoRoll.dispose();
        playlist.dispose();
        mixer.dispose();
    }
}
