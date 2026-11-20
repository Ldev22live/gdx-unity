package io.github.ldev22live.gdxunity.engine.desktop;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Disposable;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.audio.EngineProfile;
import io.github.ldev22live.gdxunity.engine.audio.EngineSynth;
import io.github.ldev22live.gdxunity.engine.audio.SampleData;
import io.github.ldev22live.gdxunity.engine.audio.SfxPlayer;
import io.github.ldev22live.gdxunity.engine.audio.WavIO;
import io.github.ldev22live.gdxunity.engine.audio.song.MusicAssets;
import io.github.ldev22live.gdxunity.engine.audio.song.Schedule;
import io.github.ldev22live.gdxunity.engine.audio.song.Song;
import io.github.ldev22live.gdxunity.engine.audio.song.SongIO;
import io.github.ldev22live.gdxunity.engine.audio.song.SongPlayer;
import io.github.ldev22live.gdxunity.engine.scene.audio.MusicSink;
import io.github.ldev22live.gdxunity.engine.scene.audio.MusicSourceComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.audio.AudioSourceComponent;
import io.github.ldev22live.gdxunity.engine.scene.audio.EngineSoundComponent;
import io.github.ldev22live.gdxunity.engine.scene.audio.EngineVoice;
import io.github.ldev22live.gdxunity.engine.scene.audio.SoundSink;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Sound for one played scene (TD-043): checks every {@link AudioSourceComponent}'s sound, binds the sources to an
 * {@link SfxPlayer} and, when asked, to the speakers. Shared by the game runtime ({@link GameSession}) and editor Play.
 * Music Sources (TD-061) play their songs through the same output, mixed with the effects, and Engine Sounds (TD-080) run
 * a synthesized engine each, sounding from {@link #bind()} until {@link #unbind()}. A scene without audio, music or engine
 * sources opens no sound device.
 */
public final class GameAudio implements SoundSink, MusicSink, Disposable {
    private final Scene scene;
    private final boolean output;
    private final SfxPlayer player = new SfxPlayer();
    private final List<AudioSourceComponent> bound = new ArrayList<>();
    private final MusicAssets assets;
    /** Songs by source; filled while binding, before the output thread starts. */
    private final Map<MusicSourceComponent, SongVoice> songs = new LinkedHashMap<>();
    /** The songs that are sounding; the output thread reads it, so it is replaced, never changed. */
    private volatile List<SongVoice> activeSongs = List.of();
    /** The bound engines; the output thread reads it, so it is replaced, never changed. */
    private volatile List<EngineChannel> engines = List.of();
    private float[] engineBlock = new float[0];
    private float[] songLeft = new float[0];
    private float[] songRight = new float[0];
    private SfxAudioOutput device;
    private boolean disposed;

    /**
     * @param scene  the scene whose sources are bound
     * @param output whether to open the sound device; false in headless tests
     */
    public GameAudio(Scene scene, boolean output) {
        this(scene, output, MusicAssets.NONE);
    }

    /**
     * @param assets reads the project's files by project-relative path: the {@code .song} files themselves and the
     *               samples and SoundFonts they use
     */
    public GameAudio(Scene scene, boolean output, MusicAssets assets) {
        this.scene = Objects.requireNonNull(scene, "scene cannot be null");
        this.output = output;
        this.assets = Objects.requireNonNull(assets, "assets cannot be null");
    }

    /** Lets songs read their files (the {@code .song} itself, samples, SoundFonts) from a project's asset source. */
    public static MusicAssets assetsOf(AssetSource source) {
        Objects.requireNonNull(source, "source cannot be null");
        return path -> {
            FileHandle file = source.resolve(new AssetReference(path));
            if (!file.exists()) {
                throw new IOException("file not found: " + path);
            }
            return file.readBytes();
        };
    }

    /** One song of the scene: its player and the source it belongs to. */
    private static final class SongVoice {
        final SongPlayer player;
        final MusicSourceComponent source;

        SongVoice(SongPlayer player, MusicSourceComponent source) {
            this.player = player;
            this.source = source;
        }
    }

    /**
     * One engine of the scene: the synth the output thread renders and the volume and pan the game thread last set. The game
     * thread writes the volatile fields and the synth's own volatile setters; the output thread only reads them.
     */
    private static final class EngineChannel implements EngineVoice {
        final EngineSoundComponent source;
        final EngineSynth synth;
        private volatile float gain;
        private volatile float pan;
        /** The gain the last block ended on, so a change ramps over the next block instead of clicking. */
        private float renderedGain;

        EngineChannel(EngineSoundComponent source, EngineSynth synth) {
            this.source = source;
            this.synth = synth;
        }

        @Override
        public void update(float revs, float throttle, float gain, float pan) {
            EngineProfile profile = synth.getProfile();
            synth.setRpm(profile.idleRpm() + revs * (profile.redlineRpm() - profile.idleRpm()));
            synth.setThrottle(throttle);
            this.gain = gain;
            this.pan = pan;
        }
    }

    /**
     * Binds every enabled source of the scene. Nothing stays bound if a source names a sound that does not exist.
     *
     * @return what prevents the scene from playing; empty when bound
     */
    public List<String> bind() {
        if (disposed) {
            throw new IllegalStateException("GameAudio is disposed");
        }
        List<String> problems = new ArrayList<>();
        List<AudioSourceComponent> sources = new ArrayList<>();
        Map<String, SampleData> clips = new LinkedHashMap<>();
        for (Entity entity : scene.getAllEntities()) {
            for (AudioSourceComponent source : entity.getComponents(AudioSourceComponent.class)) {
                if (!source.isEnabled()) {
                    continue;
                }
                if (source.isClip()) {
                    String problem = readClip(source.getSound(), clips);
                    if (problem == null) {
                        sources.add(source);
                    } else {
                        problems.add(entity.getName() + ": " + problem + " (cue '" + source.getCue() + "')");
                    }
                } else if (SfxPlayer.isKnown(source.getSound())) {
                    sources.add(source);
                } else {
                    problems.add(entity.getName() + ": unknown sound '" + source.getSound() + "' (cue '"
                        + source.getCue() + "')");
                }
            }
        }
        List<EngineChannel> channels = new ArrayList<>();
        for (Entity entity : scene.getAllEntities()) {
            for (EngineSoundComponent source : entity.getComponents(EngineSoundComponent.class)) {
                if (!source.isEnabled()) {
                    continue;
                }
                if (EngineProfile.isKnown(source.getProfile())) {
                    channels.add(new EngineChannel(source,
                        new EngineSynth(EngineProfile.byName(source.getProfile()), engineSeed(entity, channels.size()))));
                } else {
                    problems.add(entity.getName() + ": unknown engine '" + source.getProfile() + "'; use one of "
                        + EngineProfile.names());
                }
            }
        }
        Map<MusicSourceComponent, SongPlayer> players = new LinkedHashMap<>();
        for (Entity entity : scene.getAllEntities()) {
            for (MusicSourceComponent source : entity.getComponents(MusicSourceComponent.class)) {
                if (!source.isEnabled()) {
                    continue;
                }
                try {
                    SongPlayer player = loadSong(source);
                    if (player.getErrors().isEmpty()) {
                        players.put(source, player);
                    } else {
                        problems.add(entity.getName() + ": song '" + source.getSong() + "': " + player.getErrors().get(0));
                    }
                } catch (IOException | RuntimeException e) {
                    problems.add(entity.getName() + ": song '" + source.getSong() + "' cannot be loaded: " + e.getMessage());
                }
            }
        }
        if (!problems.isEmpty()) {
            players.values().forEach(SongPlayer::dispose);
            return problems;
        }
        for (Map.Entry<MusicSourceComponent, SongPlayer> entry : players.entrySet()) {
            songs.put(entry.getKey(), new SongVoice(entry.getValue(), entry.getKey()));
            entry.getKey().bind(this);
        }
        for (EngineChannel channel : channels) {
            channel.source.bind(channel);
        }
        engines = List.copyOf(channels);
        clips.forEach(player::addClip);
        for (AudioSourceComponent source : sources) {
            if (!source.isClip()) {
                player.prepare(source.getSound());
            }
            source.bind(this);
            bound.add(source);
        }
        if (output) {
            startOutput();
        }
        return problems;
    }

    /**
     * Opens the sound device, if the bound scene has something to play and no device is open yet. {@link #bind()} does this
     * itself when constructed with {@code output}; the game runtime binds before its window (and so its audio backend)
     * exists, and calls this once it does.
     *
     * @return why the device could not be opened, or null
     */
    public String startOutput() {
        if (disposed) {
            throw new IllegalStateException("GameAudio is disposed");
        }
        if (device == null && (!bound.isEmpty() || !songs.isEmpty() || !engines.isEmpty())) {
            device = new SfxAudioOutput(this::render);
            device.start();
        }
        return getOutputError();
    }

    /** True while a sound device is open and playing. */
    public boolean isOutputRunning() {
        return device != null && device.isRunning();
    }

    /** The open device's latency and render cost (roadmap M9.5), or null when no device is open. */
    public AudioOutputStats getOutputStats() {
        return device != null && device.isRunning() ? device.getStats() : null;
    }

    /** Reads a {@code .wav} clip of the project into {@code clips} (once per file); returns what is wrong with it, or null. */
    private String readClip(String path, Map<String, SampleData> clips) {
        if (clips.containsKey(path)) {
            return null;
        }
        try {
            byte[] bytes = assets.read(path);
            clips.put(path, path.toLowerCase(java.util.Locale.ROOT).endsWith(".ogg") ? OggClips.read(bytes) : WavIO.read(bytes));
            return null;
        } catch (IOException | RuntimeException e) {
            return "sound clip '" + path + "' cannot be loaded: " + e.getMessage();
        }
    }

    /** Why the sound device could not be opened, or null (also null when no device was asked for or needed). */
    public String getOutputError() {
        return device != null ? device.getError() : null;
    }

    /** Number of sound effect sources bound by {@link #bind()}. */
    public int getBoundCount() {
        return bound.size();
    }

    /** Number of Engine Sounds bound by {@link #bind()}. */
    public int getBoundEngineCount() {
        return engines.size();
    }

    /** Number of Music Sources bound by {@link #bind()}. */
    public int getBoundSongCount() {
        return songs.size();
    }

    /** Number of songs that are sounding right now. */
    public int getPlayingSongCount() {
        return activeSongs.size();
    }

    private SongPlayer loadSong(MusicSourceComponent source) throws IOException {
        if (source.getSong().isBlank()) {
            throw new IOException("no song chosen");
        }
        Song song = SongIO.fromJson(new String(assets.read(source.getSong()), StandardCharsets.UTF_8));
        SongPlayer player = new SongPlayer(song, assets);
        player.setPlayback(song.getClips().isEmpty() ? Schedule.Mode.PATTERN : Schedule.Mode.SONG,
            song.getPatterns().isEmpty() ? 0 : song.getPatterns().get(0).getId());
        return player;
    }

    @Override
    public void start(MusicSourceComponent source) {
        SongVoice voice = songs.get(source);
        if (voice == null) {
            return;
        }
        voice.player.stop();
        voice.player.setLoop(source.isLoop());
        voice.player.play();
        List<SongVoice> next = new ArrayList<>(activeSongs);
        if (!next.contains(voice)) {
            next.add(voice);
        }
        activeSongs = List.copyOf(next);
    }

    @Override
    public void stop(MusicSourceComponent source) {
        SongVoice voice = songs.get(source);
        if (voice == null) {
            return;
        }
        voice.player.stop();
        List<SongVoice> next = new ArrayList<>(activeSongs);
        next.remove(voice);
        activeSongs = List.copyOf(next);
    }

    /**
     * Fills a block with the effects and every sounding song, each song at its source's volume. The output thread calls
     * it; tests call it to hear what a scene plays.
     */
    public void render(float[] left, float[] right, int frames) {
        player.render(left, right, frames);
        renderSongs(left, right, frames);
        renderEngines(left, right, frames);
    }

    private void renderSongs(float[] left, float[] right, int frames) {
        List<SongVoice> voices = activeSongs;
        if (voices.isEmpty()) {
            return;
        }
        if (songLeft.length < frames) {
            songLeft = new float[frames];
            songRight = new float[frames];
        }
        for (SongVoice voice : voices) {
            voice.player.render(songLeft, songRight, frames);
            float volume = voice.source.getVolume();
            for (int i = 0; i < frames; i++) {
                left[i] += songLeft[i] * volume;
                right[i] += songRight[i] * volume;
            }
        }
    }

    /** Adds every engine, each at its last set gain (ramped over the block) and pan (equal power between the speakers). */
    private void renderEngines(float[] left, float[] right, int frames) {
        List<EngineChannel> running = engines;
        if (running.isEmpty() || frames == 0) {
            return;
        }
        if (engineBlock.length < frames) {
            engineBlock = new float[frames];
        }
        for (EngineChannel engine : running) {
            engine.synth.render(engineBlock, frames);
            float targetGain = engine.gain;
            float angle = (Math.min(Math.max(engine.pan, -1f), 1f) + 1f) * (float) (Math.PI / 4.0);
            float leftShare = (float) Math.cos(angle);
            float rightShare = (float) Math.sin(angle);
            float start = engine.renderedGain;
            for (int i = 0; i < frames; i++) {
                float gain = start + (targetGain - start) * (i + 1) / frames;
                left[i] += engineBlock[i] * gain * leftShare;
                right[i] += engineBlock[i] * gain * rightShare;
            }
            engine.renderedGain = targetGain;
        }
    }

    /** Different engines of one scene get different seeds, so two V8s do not fire in lockstep. */
    private static long engineSeed(Entity entity, int index) {
        return entity.getName().hashCode() * 31L + index;
    }

    /** The mixer the bound sources play into; tests render it to hear what was played. */
    public SfxPlayer getPlayer() {
        return player;
    }

    @Override
    public void play(String sound, float volume, float pitch) {
        player.play(sound, volume, pitch);
    }

    @Override
    public void play(String sound, float volume, float pitch, float pan) {
        player.play(sound, volume, pitch, pan);
    }

    /** Unbinds every source. Idempotent. */
    public void unbind() {
        for (AudioSourceComponent source : bound) {
            source.unbind();
        }
        bound.clear();
        activeSongs = List.of();
        for (EngineChannel engine : engines) {
            engine.source.unbind();
        }
        engines = List.of();
        for (Map.Entry<MusicSourceComponent, SongVoice> entry : songs.entrySet()) {
            entry.getKey().unbind();
            entry.getValue().player.dispose();
        }
        songs.clear();
    }

    /** Unbinds the sources and closes the sound device. Idempotent. */
    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        unbind();
        if (device != null) {
            device.dispose();
            device = null;
        }
    }
}
