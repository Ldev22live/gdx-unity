package io.github.ldev22live.gdxunity.editor.audio;

import io.github.ldev22live.gdxunity.engine.audio.SampleData;
import io.github.ldev22live.gdxunity.engine.audio.WavIO;
import io.github.ldev22live.gdxunity.engine.audio.song.Pattern;
import io.github.ldev22live.gdxunity.engine.audio.song.Schedule;
import io.github.ldev22live.gdxunity.engine.audio.song.Song;
import io.github.ldev22live.gdxunity.engine.audio.song.SongExporter;
import io.github.ldev22live.gdxunity.engine.audio.song.SongIO;
import io.github.ldev22live.gdxunity.engine.audio.song.SongPlayer;
import io.github.ldev22live.gdxunity.engine.audio.song.Note;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The music editor's state outside the screen: the open song and its player, which pattern and channel are
 * selected, the piano roll's selection and snap, and opening, saving and exporting. The UI reads and calls this and
 * draws; nothing here needs OpenGL or a sound device (the player is fed by whoever owns the device).
 */
public final class MusicSession {
    /** File extension of a song. */
    public static final String SONG_EXTENSION = ".song";

    private final Path assetRoot;
    private final FolderMusicAssets assets;
    private MusicDocument document;
    private SongPlayer player;
    private int selectedPatternId;
    private int selectedChannelId = -1;
    private final Set<Note> selectedNotes = new LinkedHashSet<>();
    private GridSnap snap = GridSnap.SIXTEENTH;
    private String lastError;

    public MusicSession(Path assetRoot) {
        this.assetRoot = Objects.requireNonNull(assetRoot, "assetRoot cannot be null").toAbsolutePath().normalize();
        this.assets = new FolderMusicAssets(this.assetRoot);
    }

    public Path getAssetRoot() {
        return assetRoot;
    }

    /** The open song, or null. */
    public MusicDocument getDocument() {
        return document;
    }

    /** The player of the open song, or null. */
    public SongPlayer getPlayer() {
        return player;
    }

    public boolean hasSong() {
        return document != null;
    }

    /** The last problem opening, saving or exporting, or null; cleared by the next successful operation. */
    public String getLastError() {
        return lastError;
    }

    // ---- selection ----

    public Pattern getSelectedPattern() {
        return document == null ? null : document.getSong().getPattern(selectedPatternId);
    }

    /** Chooses the pattern the step sequencer and piano roll edit, and the one that plays in pattern mode. */
    public void selectPattern(int patternId) {
        requireDocument();
        if (document.getSong().getPattern(patternId) == null) {
            throw new IllegalArgumentException("No pattern with id " + patternId);
        }
        selectedPatternId = patternId;
        selectedNotes.clear();
        if (player.getMode() == Schedule.Mode.PATTERN) {
            player.setPlayback(Schedule.Mode.PATTERN, patternId);
        }
    }

    public int getSelectedChannelId() {
        return selectedChannelId;
    }

    public void selectChannel(int channelId) {
        requireDocument();
        if (document.getSong().getChannel(channelId) == null) {
            throw new IllegalArgumentException("No channel with id " + channelId);
        }
        selectedChannelId = channelId;
        selectedNotes.clear();
    }

    /** Notes selected in the piano roll (of the selected channel and pattern). */
    public Set<Note> getSelectedNotes() {
        return selectedNotes;
    }

    public void setSelectedNotes(Set<Note> notes) {
        selectedNotes.clear();
        selectedNotes.addAll(notes);
    }

    public GridSnap getSnap() {
        return snap;
    }

    public void setSnap(GridSnap snap) {
        this.snap = Objects.requireNonNull(snap, "snap cannot be null");
    }

    // ---- playback mode ----

    /** Plays the playlist when true, the selected pattern on repeat when false. */
    public void setSongMode(boolean songMode) {
        requireDocument();
        player.setPlayback(songMode ? Schedule.Mode.SONG : Schedule.Mode.PATTERN, selectedPatternId);
    }

    public boolean isSongMode() {
        return player != null && player.getMode() == Schedule.Mode.SONG;
    }

    /** Brings the player up to date with edits; call once per frame (cheap when nothing changed). */
    public void update() {
        if (player != null) {
            player.refresh();
            Song song = document.getSong();
            if (song.getPattern(selectedPatternId) == null && !song.getPatterns().isEmpty()) {
                selectedPatternId = song.getPatterns().get(0).getId();
                selectedNotes.clear();
            }
            if (song.getChannel(selectedChannelId) == null) {
                selectedChannelId = song.getChannels().isEmpty() ? -1 : song.getChannels().get(0).getId();
                selectedNotes.clear();
            }
        }
    }

    // ---- files ----

    /** Songs in the project, as project-relative paths with forward slashes. */
    public List<String> listSongs() {
        if (!Files.isDirectory(assetRoot)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(assetRoot)) {
            List<String> found = new ArrayList<>();
            files.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(SONG_EXTENSION))
                .forEach(p -> found.add(assetRoot.relativize(p).toString().replace('\\', '/')));
            Collections.sort(found);
            return found;
        } catch (IOException e) {
            lastError = "Could not list songs: " + e.getMessage();
            return List.of();
        }
    }

    /** Starts a new song with the default rack, saved at {@code relativePath} (a name ending in .song). */
    public boolean createSong(String relativePath) {
        String path = normalizeSongPath(relativePath);
        Path target = assetRoot.resolve(path).normalize();
        if (!target.startsWith(assetRoot)) {
            lastError = "The path '" + relativePath + "' is outside the project assets";
            return false;
        }
        if (Files.exists(target)) {
            lastError = "A file already exists at " + path;
            return false;
        }
        String name = path.substring(path.lastIndexOf('/') + 1, path.length() - SONG_EXTENSION.length());
        if (!close()) {
            return false;
        }
        install(new MusicDocument(path, Song.createDefault(name)));
        return save();
    }

    /** Opens a song file; the open song must be saved or discarded first (see {@link #close()}). */
    public boolean open(String relativePath) {
        String path = normalizeSongPath(relativePath);
        Path file = assetRoot.resolve(path).normalize();
        if (!file.startsWith(assetRoot) || !Files.isRegularFile(file)) {
            lastError = "Song not found: " + path;
            return false;
        }
        Song song;
        try {
            song = SongIO.fromJson(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | IllegalArgumentException e) {
            lastError = "Could not open " + path + ": " + e.getMessage();
            return false;
        }
        if (!close()) {
            return false;
        }
        install(new MusicDocument(path, song));
        lastError = null;
        return true;
    }

    private void install(MusicDocument opened) {
        document = opened;
        player = new SongPlayer(opened.getSong(), assets);
        selectedPatternId = opened.getSong().getPatterns().isEmpty() ? 0 : opened.getSong().getPatterns().get(0).getId();
        selectedChannelId = opened.getSong().getChannels().isEmpty() ? -1 : opened.getSong().getChannels().get(0).getId();
        selectedNotes.clear();
        player.setPlayback(Schedule.Mode.PATTERN, selectedPatternId);
    }

    /** Writes the song to its file. */
    public boolean save() {
        requireDocument();
        Path file = assetRoot.resolve(document.getPath()).normalize();
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, SongIO.toJson(document.getSong()), StandardCharsets.UTF_8);
            document.markSaved();
            lastError = null;
            return true;
        } catch (IOException e) {
            lastError = "Could not save " + document.getPath() + ": " + e.getMessage();
            return false;
        }
    }

    /** Saves when there are unsaved changes; true when nothing needed saving or the save worked. */
    public boolean saveIfDirty() {
        return document == null || !document.isDirty() || save();
    }

    /** Saves unsaved changes, then closes the song and stops its player. False when saving failed (the song stays open). */
    public boolean close() {
        if (document == null) {
            return true;
        }
        if (!saveIfDirty()) {
            return false;
        }
        player.stop();
        player.dispose();
        player = null;
        document = null;
        selectedNotes.clear();
        return true;
    }

    // ---- export ----

    /**
     * Renders the pattern or the playlist and writes a 16 bit WAV file inside the project assets.
     *
     * @param relativePath project-relative target, a name ending in .wav
     * @return false with {@link #getLastError()} set when rendering or writing failed
     */
    public boolean exportWav(String relativePath, boolean wholeSong) {
        requireDocument();
        Path target = assetRoot.resolve(relativePath).normalize();
        if (!target.startsWith(assetRoot) || !relativePath.toLowerCase(java.util.Locale.ROOT).endsWith(".wav")) {
            lastError = "Export to a .wav file inside the project assets, not '" + relativePath + "'";
            return false;
        }
        try {
            SampleData audio = SongExporter.render(document.getSong(), assets,
                wholeSong ? Schedule.Mode.SONG : Schedule.Mode.PATTERN, selectedPatternId);
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(target, WavIO.write(audio));
            lastError = null;
            return true;
        } catch (IOException e) {
            lastError = "Export failed: " + e.getMessage();
            return false;
        }
    }

    private static String normalizeSongPath(String relativePath) {
        String path = Objects.requireNonNull(relativePath, "relativePath cannot be null").replace('\\', '/');
        return path.endsWith(SONG_EXTENSION) ? path : path + SONG_EXTENSION;
    }

    private void requireDocument() {
        if (document == null) {
            throw new IllegalStateException("No song is open");
        }
    }
}
