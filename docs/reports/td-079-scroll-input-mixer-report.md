# TD-079 Report: Scrolling Panes Tested by Input Events; Mixer at Small Sizes

Record: [TD-079](../TECHNICAL_DECISIONS.md). Starting and ending `git rev-parse --short HEAD`: `d896331` (working tree holds the uncommitted work of TD-069 to TD-078; nothing was staged, committed or reverted). The owner's end-to-end results had not been given, so the scope is what TD-077 could not check from stills.

## Acceptance criteria

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | Wheel scrolls a toolbar sideways only | **Met (synthetic event)** | `ScrollingBarTest.shouldScrollTheToolbarSidewaysWithTheMouseWheel` |
| 2 | Dragging on a panel does not scroll it | **Met (synthetic events)** | `ScrollingBarTest.shouldNotScrollAPanelWhenTheMouseIsDraggedOnIt`; with `setFlickScroll(true)` in `ScrollingBar.panel` it failed (exit 1), restored it passes (exit 0) |
| 3 | Frames of Piano roll, Playlist, Mixer at 1024x640 and 1280x720 | **Met**: 6 frames | `images/td79-<size>-<tab>.png` |
| 4 | Fix what they show | **Met for the Mixer**; nothing wrong seen on Piano roll and Playlist | `td79-*-mixer-after.png` |
| 5 | Check by a person with a mouse | **Not met** | **Manually Not Verified (Headless Environment)** |

## Commands and results

| Command | Exit | Result |
|---|---|---|
| `./gradlew :editor-desktop:test --tests "*ScrollingBarTest"` (first) | 1 | my drag test asserted the wrong end: scene2d's `scrollY` is 0 at the top; test corrected |
| same, then with drag scrolling switched on (mutation), then restored | 0, 1, 0 | the test can fail |
| `frames79.ps1`: editor `--smoke-test` with `GDXUNITY_EDITOR_WINDOW` 1024x640 and 1280x720, `GDXUNITY_EDITOR_MUSIC_TAB` Piano roll, Playlist, Mixer | 0 each (6) | frames |
| editor `--smoke-test`, Mixer at both sizes after the fix | 0 each (2) | after frames |
| `./gradlew test --continue --max-workers=1` | 0 | 20 modules, 0 failures |

Per-module counts, before then after: editor-desktop 94 then **96** (+2 `ScrollingBarTest`); all others unchanged: editor-animation 17, editor-audio 57, editor-character 10, editor-core 385 (1 skipped, pre-existing, not examined), editor-sculpt 56, editor-sprite 51, editor-visualscript 89, engine-animation 32, engine-audio 222, engine-character 19, engine-core 35, engine-desktop 92, engine-physics 53, engine-render 119, engine-scene 149, engine-sculpt 81, engine-sprite 44, engine-visualscript 86, engine-web 9. No new or pre-existing failures apart from my own wrong assertion above. Web suite not re-run (no web change; last 476).

## Findings (Mixer, before)

Only 2 of 7 strips fit at 1024 (4 at 1280); the sideways scroll bar faded out, so nothing showed the other strips existed; the first strip's "Pan 0.00" was cut at the pane's left edge. After: bar always shown, 6 px left room, minimum width 0.

## Changes

`MixerView`; `ScrollingBarTest` (+2). Docs: TD-079 record, matrix row, INDEX line, this report, 8 frames.

## Known limitations and next steps

- The input tests drive scene2d with synthetic events; they do not show that the wheel scrolls when a real mouse is over the bar in the running editor, nor that sprite drawing works inside its scroller. The owner's E2E run is the check.
- Not looked at: the Instrument sub-tab at 1280x720, the Scene tab's panels, Graph pop-ups, sizes above 1366, the web editor. The Graph canvas is about 200 px wide at 1024x640.

No commit, push or other Git write was performed.
