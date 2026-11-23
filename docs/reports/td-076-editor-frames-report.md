# TD-076 Report: Look at the Editors, and Fix What the Frames Show

Record: [TD-076](../TECHNICAL_DECISIONS.md). Starting and ending `git rev-parse --short HEAD`: `d896331`. The working tree already held the uncommitted work of TD-069 to TD-075; nothing was committed, staged or reverted.

All observations below come from **still frames** of the editor at **1920 by 990**, saved by the editor itself (`GDXUNITY_SCREENSHOT`) at the end of a 180-frame smoke run. Nothing was clicked, dragged, scrolled, typed into or heard: those are **Manually Not Verified (Headless Environment)**.

## Acceptance criteria

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | Test aids to open the Music Studio on a song, channel and sub-tab | **Met** | `GDXUNITY_EDITOR_SONG`, `GDXUNITY_EDITOR_CHANNEL`, `GDXUNITY_EDITOR_MUSIC_TAB` (listed in `AGENTS.md`); each smoke run below exits 0 with 0 errors in the Output Log |
| 2 | A sample song with every instrument kind | **Met** | `assets/music/instruments-demo.song`; `InstrumentsDemoSongTest` (matches the code exactly, has Drums, Synth, FM and plucked-string channels, renders audibly) |
| 3 | Frames of each instrument panel and the rack | **Met** | `images/td76-music-fm-before.png`, `-string-before`, `-synth-before`, `-drums`, and after the fixes `-fm-after`, `-synth-after`, `-rack-after` |
| 4 | Frames of the Graph tab with the key fields | **Met** | `images/td76-graph-shooter-before.png`, `-runner-before`, `-runner-after`; the Key Pressed node shows its key field (`SPACE,W,UP`) and typed values |
| 5 | Fix what the frames show | **Met for six findings; two seen and left** | below |
| 6 | Docs, matrix, report | **Met** | this file, TD-076 record, `FEATURE_TEST_MATRIX.md`, `INDEX.md`, `AGENTS.md` |

## What the frames showed

| # | Finding | Evidence | Action |
|---|---|---|---|
| 1 | The first frames showed the rack, not the instrument panel: the panel is under the "Instrument" sub-tab | first capture | added `GDXUNITY_EDITOR_MUSIC_TAB` |
| 2 | The **Preset menu said "Custom"** for a channel that is exactly a factory preset (FM Bell, Electric Piano) | `td76-music-fm-before.png`, `-synth-before.png` | `InstrumentPanel.matchingPresetIndex`; after: "Bell", "Electric Piano" (`-fm-after`, `-synth-after`); `InstrumentPanelPresetTest` (3) |
| 3 | The synth panel was **cut off mid-row with no scroll bar**: the default scroll style had none, so this holds for every scroll pane in the editor | `td76-music-synth-before.png` | `EditorSkinFactory` draws a track and knob; after: a bar at the right edge (`-synth-after`) |
| 4 | **Tab titles ran together** ("Channel rackPiano rollPlaylistMixerInstrument", "Content BrowserOutput Log") | all before frames | gap after each tab (`TabbedPanel`); after: separated |
| 5 | The **buttons under the channel rack** (now seven, two added in TD-075) read as one line of text | first rack frame | wider gaps (`MusicStudioPanel`); after: `td76-music-rack-after.png` |
| 6 | The **Graph tab made the whole editor wider than the window**: the left of the top bar ("ene Animations"), the Outline label and the Details panel were cut off; the toolbar buttons also ran together. The **TD-072 frame (`td72-runner-graph.png`) already showed this and I described it as readable** | `td76-graph-shooter-before.png`, `-runner-before` | the toolbar is two rows and the title is cut with an ellipsis (`GraphEditorPanel`); after: the whole editor fits (`td76-graph-runner-after.png`) |
| 7 | The Timeline toolbar in the Animations tab has the same run-together, partly cut-off buttons | `td76-tab-animations.png` | **seen, not fixed** (outside this milestone's panels) |
| 8 | The node palette's long group names ("Components: ShapeRendererComponent") are cut at the panel edge | `-runner-after` | **seen, not fixed**; with the new scroll bars the palette can now be scrolled sideways |

No button, field or menu changed what it does.

## Commands and results

| Command | Exit | Result |
|---|---|---|
| `GDXUNITY_RECORD_FIXTURES=1 ./gradlew :editor-audio:test --tests "*InstrumentsDemoSongTest"` | 0 | wrote `assets/music/instruments-demo.song` (6,925 bytes) |
| `./gradlew :editor-audio:test :editor-desktop:compileJava` | 0 | editor-audio 57 tests, 0 failures |
| `./gradlew :editor-desktop:run --args="--smoke-test"` with `GDXUNITY_EDITOR_SONG=music/instruments-demo.song`, `GDXUNITY_EDITOR_CHANNEL` of `Bell`, `Harp`, `Lead`, `Kick`, then again with `GDXUNITY_EDITOR_MUSIC_TAB=Instrument`, then `Channel rack` | 0 each | 180 frames, 0 errors each; frames above |
| same with `GDXUNITY_EDITOR_TAB="Visual Scripting"` and `GDXUNITY_EDITOR_GRAPH` of `ShooterShip.graph` and `RunnerPlayer.graph`, before and after the toolbar fix | 0 each (one extra run failed because my script passed a wrong graph path, `g`) | frames above |
| same with `GDXUNITY_EDITOR_TAB` of `Viewport`, `Animations`, `Sprites` after the style changes | 0 each | the Viewport and Animations frames show no new problem (`td76-tab-*.png`); the Sprites frame was captured but not read |
| `./gradlew test --continue --max-workers=1` (whole repo) | 0 | all 20 modules, 0 failures, 0 errors |
| `npx tsc --noEmit`, `npx vitest run` in `engine-babylon` | 0 | 37 files, 476 tests (unchanged), 0 failures |

Per-module counts (failures 0 everywhere): editor-animation 17, editor-audio **57** (was 56: +1 `InstrumentsDemoSongTest`), editor-character 10, editor-core 385 (1 skipped, pre-existing, not examined), editor-desktop **87** (was 84: +3 `InstrumentPanelPresetTest`), editor-sculpt 56, editor-sprite 51, editor-visualscript 89, engine-animation 32, engine-audio 222, engine-character 19, engine-core 35, engine-desktop 92, engine-physics 53, engine-render 119, engine-scene 149, engine-sculpt 81, engine-sprite 44, engine-visualscript 86, engine-web 9. Web: 476.

Pre-existing versus new: no failure appeared.

## Changes

- `editor-desktop`: `EditorApplication` (two aids and a channel selector), `MusicStudioPanel` (sub-tab aid, rack button gap), `InstrumentPanel` (preset matching), `EditorSkinFactory` (scroll bars), `TabbedPanel` (tab gap), `GraphEditorPanel` (two-row toolbar, ellipsized title, wider gaps); test `InstrumentPanelPresetTest` (new).
- `editor-audio`: test `InstrumentsDemoSongTest` (new). Asset: `assets/music/instruments-demo.song`.
- Docs: `TECHNICAL_DECISIONS.md`, `FEATURE_TEST_MATRIX.md`, `INDEX.md`, `AGENTS.md` (aid list), this report, 13 frames in `docs/reports/images/`.

## Known limitations and next steps

- **One window size** (1920 by 990). A smaller window may overflow somewhere else; the Graph toolbar's two rows are about 900 and 800 pixels wide before the title.
- The changes to shared pieces (scroll style, tab gap) affect every panel in the editor; I looked at Music, Visual Scripting, Viewport and Animations, not every panel.
- **Nobody has clicked or dragged anything**: the preset menus, knobs, "+ FM" and "+ String", the Graph palette's drag-to-add, the Functions and Events pop-ups, the Edit buttons and the key fields have only been seen drawn, not used. Sound has not been heard.
- The web editor was not looked at.
- Findings 7 and 8 are open.

No commit, push or other Git write was performed.
