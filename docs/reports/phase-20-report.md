# Phase 20 Report: Music Studio (TD-040)

Date: 2026-11-03 · Platform: Windows 11, JDK 21 toolchain · HEAD before and after: `1dc0a11` (nothing committed)
Milestone: [phase-20-music-studio.md](../milestones/phase-20-music-studio.md)

## What was asked and what was built

The user asked for a music editor "similar to LMMS or Fruity Loops", and chose: full roadmap Phase 11, new modules
`engine-audio` and `editor-audio`, SoundFont support.

| Acceptance criterion | Result |
|---|---|
| Step sequencer (rack of channels, 16+ steps, velocity, swing) | Met in code and tests; shown in the running editor; painting with the mouse **not tried** |
| Piano roll (draw, move, resize, velocity, snap, quantize) | Met in code and tests (`PianoRollOps`); shown in the running editor; mouse editing **not tried** |
| Software synthesizer (two oscillators, ADSR, filter, LFO) | Met in code and tests; sound reached the device; **not listened to** |
| Procedural game sound effects (jump, laser, explosion, coin, power-up, hurt) | Met: `SfxrGenerator` with 7 presets, mutate, randomize; heard by nobody yet |
| Playlist / song arrangement | Met in code and tests; shown in the running editor; mouse editing **not tried** |
| Mixer: 8 channels and master, volume, pan, mute, solo, delay, reverb | Met in code and tests; shown in the running editor with live meters |
| SoundFont support | **Partly met**: our own reader (no new dependency); tested only against a file the tests write themselves; no real `.sf2` tried; no filters, modulators or SF3 |
| Export to `.wav` | Met in code and tests (`SongExporter`, `MusicSession.exportWav`); the Export button was **not pressed** |
| Export to `.ogg` | **Not met**: WAV only |
| `AudioSourceComponent` / `AudioListenerComponent`, playback in game runtime and web player | **Not met** (20-C not started) |
| Undo and redo for every edit | Met: every `MusicDocument` edit is a command; tests undo each kind and compare the whole song text before and after |

## Decisions that differ from what I told the user

- I proposed the JDK's built-in synthesizer (Gervill) for SoundFont playback and the user agreed. It turned out its
  public API cannot render to memory and it cannot run in the web player, so I wrote a SoundFont reader and sampler
  instead. The user approved "no new dependency", and none was added, but the way it is done is not what they approved.
  Recorded in TD-040.
- A master soft limiter was added after the sample song's mix peaked at 1.66 (the output would have clipped).
- The editor's Music tab can be started from system properties (`-Dgdxunity.openSong`, `musicTab`, `musicChannel`,
  `musicAutoPlay`) so a screenshot is possible without synthetic clicks. They are testing aids, listed here and in the code.

## Commands and results

| Command | Result |
|---|---|
| `./gradlew cleanTest :engine-core:test :engine-scene:test :engine-render:test :engine-visualscript:test :engine-animation:test :engine-desktop:test :editor-visualscript:test :editor-animation:test :editor-desktop:test :editor-sculpt:test :engine-sculpt:test :engine-audio:test :editor-audio:test :editor-core:test --continue` | exit 0; 980 tests, 0 failures, 0 errors |
| `./gradlew :editor-desktop:run --args="--smoke-test=1500"` with `-Dgdxunity.openSong=music/demo.song` and each `musicTab` 0 to 4 | exit 0, 1500 frames, 0 errors in the Output Log, every time |

Tests per module after this work: `engine-core` 32, `engine-scene` 63, `engine-render` 90, `engine-visualscript` 9,
`engine-animation` 32, `engine-desktop` 27, `engine-sculpt` 76, **`engine-audio` 178 (new)**, `editor-core` 319,
`editor-visualscript` 4, `editor-animation` 13, `editor-sculpt` 44, **`editor-audio` 53 (new)**, `editor-desktop` 40.
Counts before the work were not recorded for the whole repository (the last full run predates the sculpting and viewport
work); the other modules' sources were not changed, so no new failures are attributable to them. `engine-web` was not run.

## Mutation checks and the defects they found

I injected eight bugs into `engine-audio` and ran the matching test class: a wrong swing size, a wrong pan law, swapped
SoundFont pan gains, a release that never fades, a pan that is not saved, a step row that ignores its pitch, a key range
mask, and a loop wrap that keeps no remainder. Six were caught at once. The key range mask change is equivalent (key
numbers never exceed 127), so no test can catch it. The loop wrap change **survived**, and looking into it found two real
defects in `SongPlayer`:

1. Loops drifted by a fraction of a frame each time round (3 frames over 40 loops at 121 bpm).
2. After a loop wrap the event list restarted *after* a note sitting exactly at the loop start whenever the carried-over
   fraction was slightly above zero, so that note was **silently skipped on some loops** (7 of every 14 loops in the
   test song).

Both are fixed, with `shouldNotDriftOverManyLoopsWhenABarIsAFractionOfAFrameLong` and
`shouldPlayTheNoteAtTheLoopStartOnEveryLoop` (the second fails against the old code, checked). A ninth mutation, in the
event timing, made the test run loop forever and had to be stopped; the file was restored from its backup before anything
else ran and compared with the fixed copy. Other defects found by the tests while building: the SoundFont looping voice
stopped at its sample end instead of wrapping, and the sample song clipped.

## What I saw in the running editor

Window-only screenshots (the editor window was verified to be the foreground window before capture; no input was sent):
the channel rack with the demo beat and note marks on the melodic rows, the piano roll with the lead melody, the playlist
with Groove, Break, Groove, the mixer with reverb and delay on the Lead strip, and the instrument panel for the synth
lead. With Play started by `-Dgdxunity.musicAutoPlay=true`, the transport showed "Playing 2.2 (3.2 s of sound sent)" and
the mixer meters moved, so samples did flow to the audio device.

A rendering defect found this way, and fixed: my canvas widgets clipped before the previous widget's text had been
sent to the GPU, so the last piece of text before a canvas disappeared (the fifth tab and the BPM field). `ShapeWidget`
now flushes first. The Timeline and graph canvases use the same order of calls and may show the same fault when a label
is drawn just before them; I did not check or change them.

## Not checked

- **Nobody has listened to the output.** Levels, pitch, timing feel, drum sound quality and the effects were checked by
  numbers (tests) and meters, not by ear.
- **No mouse use**: painting steps, drawing and dragging notes, clip dragging, faders and knobs, the dropdowns, the
  effect menu, Space to play, Ctrl+Z in the music views, "New song", "Save", "Export WAV" and saving when the editor closes.
- A real SoundFont, sample files, and the sound-effect editor on screen.
- Long songs and many voices under load (no load measurement); the audio thread shares the process with the editor.
- `engine-web`.

## Known limitations and next steps

See the milestone document. The next piece, if wanted, is 20-C: `AudioSourceComponent` and a game-side player so scenes
can play songs and effects, then `.ogg` export.

## Git

No commit, push or destructive Git operation was performed. Starting and ending HEAD: `1dc0a11`. `AGENTS.md` already had
uncommitted changes from before; two rows were added to its module table. `settings.gradle` and
`editor-desktop/build.gradle` gained the two modules. `docs/TECHNICAL_DECISIONS.md`, `docs/INDEX.md` and
`docs/FEATURE_TEST_MATRIX.md` were updated.
