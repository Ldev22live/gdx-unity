# Phase 20: Music Studio (TD-040)

Status: 20-A (audio core) and 20-B (music editor) implemented and integrated; 20-C (game integration) not started.
Report: [../reports/phase-20-report.md](../reports/phase-20-report.md). Decision: TD-040 in
[../TECHNICAL_DECISIONS.md](../TECHNICAL_DECISIONS.md). Roadmap source: `ALL_IN_ONE_STUDIO_ROADMAP.md` §4.3 and Phase 11.

## Goal

A music editor in the editor, in the spirit of LMMS and FL Studio: make a beat on a step sequencer, write melodies on
a piano roll, arrange patterns on a playlist, shape the sound with a synth, a SoundFont or a sound-effect generator,
mix with effects, and render the result to a WAV file in the project.

## Scope as the user chose it

Full roadmap Phase 11, new modules `engine-audio` and `editor-audio`, SoundFont support.

| Part | Content | Status |
|---|---|---|
| 20-A | `engine-audio`: synth, drums, sampler, SoundFont reader, sfxr, effects, mixer, song model, player, exporter, `.song` file | Implemented, 178 tests |
| 20-B | `editor-audio` and the Music tab: rack and step sequencer, piano roll, playlist, mixer, instrument settings, undo, save, WAV export, sound output | Implemented and integrated; 53 tests; see the report for what was and was not tried by hand |
| 20-C | `AudioSourceComponent` and `AudioListenerComponent`, playing songs and sounds in the game runtime and the web player, sound-effect export to `.ogg` | **Not started** |

## How it is built

- `engine-audio` has no editor, OpenGL or sound-device dependency, so everything in it runs in headless tests and can
  later run in the web player. The editor feeds `SongPlayer.render()` to a libGDX `AudioDevice` from its own thread.
- Time is in ticks: 96 per beat, 4/4. A step is a sixteenth (24 ticks). A pattern is a whole number of bars (1 to 16).
- `Schedule` flattens a song (one pattern on repeat, or the playlist) into note-on and note-off events, with swing and
  muted channels applied. `SongPlayer` plays that list: events fall on the exact frame (an event within half a
  frame is played now), whatever the block size, and loop points keep their fraction of a frame so a loop does not drift.
- The song is not read by the audio thread while it renders. `SongPlayer.refresh()` swaps in a new schedule and
  instruments when the song's revisions changed; mixer and synth knobs are plain fields and change live.
- Editing goes through `MusicDocument`, which has its own undo history (a song is not a scene). Knob and fader
  drags apply live and are recorded as one undo step when released.

## The Music tab

| View | What you can do |
|---|---|
| Channel rack | One row per channel with a mute box and 16 (or 32...) steps. Click or drag to paint steps, right-click to clear, Ctrl+click a lit step to change its velocity. Small squares mark piano roll notes on melodic rows. Add synth, drum and sound-effect channels; clear a row; remove a channel |
| Piano roll | Draw, move, resize and delete notes; box select; velocity lane; keyboard preview; quantize; duplicate; nudge; snap 1/4 to 1/32 and triplets |
| Playlist | Place the selected pattern on one of 8 tracks, drag to move, drag the right edge to stretch (the pattern repeats), right-click to delete, click the ruler to move the play head |
| Mixer | 8 inserts and a master: meter, volume fader, pan, mute, solo, up to four effects each (delay, reverb, filter) with their settings |
| Instrument | Name, kind, mixer insert; synth (oscillators, envelope, filter, LFO, presets), drum choice, sample file, sound effects (presets, mutate, randomize, all parameters), SoundFont file and preset |
| Transport | New song, open, save, undo, redo, Play/Pause (also Space), Stop, loop, pattern or song mode, BPM, swing, snap, pattern select, add and remove pattern, pattern length, Export WAV |

A sample song is in `assets/music/demo.song`: a two-bar groove and a one-bar break arranged on the playlist, with drums, bass and a lead that has reverb and delay.

## Manual smoke checklist

Run `./gradlew :editor-desktop:run`, open the Music tab, choose `music/demo.song`.

1. Press Play (or Space): the beat plays, the playhead moves along the rack, the meters in the Mixer tab move.
2. Click steps in the rack: they toggle and are heard on the next pass; Ctrl+Z undoes each paint stroke.
3. Piano roll: draw a note, drag it, stretch it, delete it with right-click; undo each.
4. Playlist: switch the mode to song, place a pattern, drag it, play from the ruler.
5. Mixer: move a fader while playing; add a reverb to the bass; solo a strip.
6. Instrument: pick another synth preset, then drag the cutoff while a note plays; choose "Sound effect" and press Play.
7. Export WAV: `assets/audio/<song name>.wav` appears and plays in another player.
8. Close the editor with unsaved changes: the song is saved first.

## Known limits

- No audio recording, no VST or plug-ins, no automation lanes, no audio clips on the playlist, 4/4 only.
- SoundFont playback has no filters, modulators or 24 bit samples; SF3 is not supported.
- Only WAV is read and written; `.ogg` import and export are not done.
- Everything renders on one audio thread and there is no load meter, so a very heavy song can stutter.
- Songs do not play in the game runtime or the web player yet (20-C).
- Mouse editing in the piano roll, playlist, rack and mixer was not tried by hand (see the report).
