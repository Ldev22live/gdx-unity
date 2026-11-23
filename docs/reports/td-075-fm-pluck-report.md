# TD-075 Report: FM and Plucked-String Instruments

Record: [TD-075](../TECHNICAL_DECISIONS.md). Starting and ending `git rev-parse --short HEAD`: `d896331`. The working tree already held the uncommitted work of TD-069 to TD-074; nothing was committed, staged or reverted.

## Acceptance criteria

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | An FM instrument (desktop and web) with presets | **Met** (5 presets) | `FmPatch`, `FmSynthesizer`, `fm.ts`; `FmSynthesizerTest` (12), `fmPluck.test.ts` |
| 2 | A plucked-string instrument (desktop and web) with presets | **Met** (3 presets) | `PluckPatch`, `PluckedString`, `pluck.ts`; `PluckedStringTest` (12), `fmPluck.test.ts` |
| 3 | The noise oscillator | **Dropped: it already exists** (`Waveform.NOISE`); my earlier proposal was wrong about this | `Waveform.java` |
| 4 | Song format carries the new kinds; old songs still load | **Met** | `SongIOTest`: a song with both kinds round-trips and renders and sounds identical after reload; a song with the new blocks removed loads with defaults and renders identically; web `fmPluck.test.ts` does both too |
| 5 | Pitch, decay, brightness, no clipping or NaN, determinism, voice limits | **Met** | the two instrument test classes and the web mirror, across notes 0 to 127 |
| 6 | Java and web sound alike | **Met within 0.5% on peak and loudness** | `InstrumentFingerprintTest` records, `instrumentFingerprints.test.ts` replays (now 8 more tests); nudging one web FM value and one digit of the string's noise generator failed 8 tests; restored |
| 7 | Editors: pick the kind, load a preset, edit knobs, undo | **Implemented; not seen.** Desktop `InstrumentPanel` and `MusicStudioPanel`, web `instrumentView.ts`, `studio.ts`, `MusicModel.ts`. Undo of a swap, a knob edit and a preset is tested headlessly (`MusicDocumentTest` +1, `fmPluck.test.ts`). **Manually Not Verified (Headless Environment)**: the Music Studio was not opened on desktop or web | n/a |
| 8 | The sounds sound like their names | **Not verified.** Nobody listened | n/a |
| 9 | Docs, matrix, report | **Met** | this file, TD-075 record, `FEATURE_TEST_MATRIX.md`, `INDEX.md` |

## Commands and results

| Command | Exit | Result |
|---|---|---|
| `GDXUNITY_RECORD_FIXTURES=1 ./gradlew :engine-audio:test --continue --max-workers=1` (first run) | 1 | 221 tests, **2 failed**: `FmSynthesizerTest.shouldKeepTheNotesPitchWhenTheRatioIsAWholeNumber` (my zero-crossing pitch estimate read 523 Hz: a modulated wave crosses zero twice a cycle, the pitch was right; the test now reads the pitch with autocorrelation, shared as `AudioTestSupport.period`) and `PluckedStringTest.shouldSoundBrighterWithMoreBrightnessAndLoseTheHighEndFasterWithMoreDamping` (**a real flaw**: linear interpolation of the delay line dulled the string on every trip; replaced by a first-order all-pass fractional delay) |
| same command, after both fixes | 0 | 221 tests, 0 failures; fingerprints recorded |
| `./gradlew :engine-audio:test :editor-audio:test :editor-desktop:test :engine-desktop:test --continue --max-workers=1` | 0 | engine-audio 222 (+1 `SongIOTest` render test added after), editor-audio 56, editor-desktop 84, engine-desktop 92; 0 failures |
| `npx tsc --noEmit`, `npx vitest run`, `npx vite build` in `engine-babylon` | 0 | 37 files, **476** tests (was 455: +13 `fmPluck.test.ts`, +8 fingerprints), 0 failures; typecheck and build pass |
| mutation check: FM `VOICE_HEADROOM` 0.5 to 0.55 and the string's `LCG_INCREMENT` off by one, then `npx vitest run test/instrumentFingerprints.test.ts` | 1 (expected) | 8 failed (5 FM, 3 string); both restored, 51 of 51 pass |
| `./gradlew test --continue --max-workers=1` (whole repo, background) | 0 | all 20 modules, 0 failures, 0 errors |

Per-module counts of the whole-repo run (failures 0 everywhere): editor-animation 17, editor-audio **56** (was 55), editor-character 10, editor-core 385 (1 skipped, pre-existing, not examined), editor-desktop 84, editor-sculpt 56, editor-sprite 51, editor-visualscript 89, engine-animation 32, engine-audio **222** (was 196 at the TD-074 report: +12 `FmSynthesizerTest`, +12 `PluckedStringTest`, +2 `SongIOTest`), engine-character 19, engine-core 35, engine-desktop 92, engine-physics 53, engine-render 119, engine-scene 149, engine-sculpt 81, engine-sprite 44, engine-visualscript 86, engine-web 9. Web: **476**.

Pre-existing versus new: no pre-existing failure appeared; the two first-run failures were new tests (one a bad measurement, one a real instrument flaw, both fixed above).

## Changes

- `engine-audio`: new `FmPatch`, `FmSynthesizer`, `PluckPatch`, `PluckedString`; changed `InstrumentSpec` (types `FM`, `PLUCK`; optional `fm` and `pluck` blocks), `InstrumentFactory`. Tests: `FmSynthesizerTest`, `PluckedStringTest` (new), `SongIOTest`, `InstrumentFingerprintTest`, `AudioTestSupport`; fixture `src/test/resources/instrument-fingerprints.json` (re-recorded).
- `editor-audio`: test `MusicDocumentTest`.
- `editor-desktop`: `InstrumentPanel` (two kinds, preset menu, knobs), `MusicStudioPanel` ("+ FM", "+ String").
- `engine-babylon`: new `src/audio/fm.ts`, `src/audio/pluck.ts`; changed `song.ts`, `player.ts`, `editor/music/MusicModel.ts`, `instrumentView.ts`, `studio.ts`. Tests: `fmPluck.test.ts` (new), `instrumentFingerprints.test.ts`.
- Docs: `TECHNICAL_DECISIONS.md`, `FEATURE_TEST_MATRIX.md`, `INDEX.md`, this report.

## Known limitations and next steps

- **Nobody has listened** to the 8 new presets, and nobody has opened the Music Studio with them. Presets were set by reasoning about the instrument, not by ear; expect to retune.
- Two-operator FM only; no feedback or multi-operator routing.
- Java and web are compared by peak and loudness of half a second at middle C, not sample for sample.
- Notes below 20 Hz are held at the longest delay line and sound a little sharp.
- **Compatibility**: a program from before this change cannot open a song that uses an FM or string channel (unknown instrument type). Songs from before this change open fine.
- Every channel now writes two more settings blocks, so song files are a little larger.
- TD-076 (a bundled General MIDI SoundFont) is still blocked on a freely licensed `.sf2` that the owner supplies or names; `AGENTS.md` forbids copying external files without authorization.

No commit, push or other Git write was performed.
