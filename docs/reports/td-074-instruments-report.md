# TD-074 Report: More Synth Presets and More Drums

Record: [TD-074](../TECHNICAL_DECISIONS.md). Starting and ending `git rev-parse --short HEAD`: `d896331`. The working tree already held the uncommitted work of TD-069 to TD-073; nothing was committed, staged or reverted.

## Acceptance criteria

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | More synth presets (desktop and web, same values) | **Met** (9 new: 14 in all) | `SynthPatch.java`, `synth.ts`; `SynthesizerTest` (14 distinct, inside the editors' slider ranges; all audible, finite, not clipping); `instrumentFingerprints.test.ts` |
| 2 | More drums (desktop and web) | **Met** (8 new: 15 in all, each on its own General MIDI note, the original seven unchanged in order) | `DrumKit.java`, `drums.ts`; `DrumKitTest` (every drum sounds on its note and falls silent; order and notes; toms tuned floor < mid < high; shaker swells; cowbell pitch) |
| 3 | Java and web sound alike | **Met within 0.5% on peak and loudness** | `InstrumentFingerprintTest` records, `instrumentFingerprints.test.ts` replays (30 tests). A deliberate change to one web value failed the test, then was restored |
| 4 | Old sounds unchanged | **Met by the fingerprints** | the original five presets and seven drums are in the recording and match on both sides; the tom code was generalized and still matches |
| 5 | Reachable from the editor | **Implemented and integrated by construction; not seen** | the desktop preset menu reads `SynthPatch.presetNames()` and the drum picker `DrumKit.drums()`; the web preset menu reads `PRESET_NAMES` and the drum hint `DRUMS`. **Manually Not Verified (Headless Environment)**: the Music Studio was not opened |
| 6 | The new sounds sound like their names | **Not verified.** Nobody listened. They are approximations (two oscillators and noise) | n/a |
| 7 | Docs, matrix, report | **Met** | this file, TD-074 record, `FEATURE_TEST_MATRIX.md`, `INDEX.md` |

## Commands and results

| Command | Exit | Result |
|---|---|---|
| `GDXUNITY_RECORD_FIXTURES=1 ./gradlew :engine-audio:test --tests "*InstrumentFingerprintTest"` (first attempt) | 1 | failed: the recorded file is written to `src` and is not on the test classpath until the next build. Fixed: in recording mode the test reads back the text it wrote |
| same command, after the fix | 0 | wrote `instrument-fingerprints.json` |
| `./gradlew :engine-audio:test :editor-audio:test :editor-desktop:test :engine-desktop:test --continue --max-workers=1` | 0 | engine-audio **196** (was 189: +1 fingerprint, +4 drum, +2 synth), editor-audio 55, editor-desktop 84, engine-desktop 92; 0 failures |
| `npx tsc --noEmit` in `engine-babylon` | 0 | no type errors |
| `npx vitest run` in `engine-babylon` | 0 | 36 files, **455** tests (was 425: +30 `instrumentFingerprints.test.ts`), 0 failures |
| `npx vite build` in `engine-babylon` | 0 | builds |
| mutation check: one web cowbell gain changed from 0.3 to 0.33, `npx vitest run test/instrumentFingerprints.test.ts` | 1 (expected) | "drum COWBELL peak: web 0.66, desktop 0.6"; restored, then 30 of 30 pass |

**Not run**: the whole-repo `./gradlew test --continue`, repeated after this milestone. The last complete run (TD-073 report) was before these changes; only the four modules above and the web suite were run now. Other modules do not use the audio instruments' lists, but I did not prove that by running them.

## Changes

- `engine-audio`: `SynthPatch.java`, `DrumKit.java`; tests `InstrumentFingerprintTest` (new), `DrumKitTest`, `SynthesizerTest`; fixture `src/test/resources/instrument-fingerprints.json` (new).
- `engine-babylon`: `src/audio/synth.ts`, `src/audio/drums.ts`, `src/editor/music/instrumentView.ts`; test `test/instrumentFingerprints.test.ts` (new).
- Docs: `TECHNICAL_DECISIONS.md`, `FEATURE_TEST_MATRIX.md`, `INDEX.md`, this report.

## Known limitations and next steps

- Nobody has listened to the 17 new sounds or opened the Music Studio with them. Presets and drums are approximations; realistic bells, plucked strings or organs need new synthesis (TD-075).
- TD-076 (a bundled General MIDI SoundFont) is blocked on a freely licensed `.sf2` that the owner supplies or names; `AGENTS.md` forbids copying external files without authorization. The web player's SoundFont support also needs checking against that file.
- Only half a second at middle C (preset) or the drum's own note was compared across platforms; peak and loudness do not prove the waveforms match sample for sample.
- The shaker, rim shot and ride levels were set by reasoning about the existing drums, not by ear.

No commit, push or other Git write was performed.
