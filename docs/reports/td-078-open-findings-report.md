# TD-078 Report: Closing the TD-077 Open Findings

Record: [TD-078](../TECHNICAL_DECISIONS.md). Starting and ending `git rev-parse --short HEAD`: `d896331` (the working tree holds the uncommitted work of TD-069 to TD-077; nothing was staged, committed or reverted). The owner's end-to-end results had not been given, so the scope is the three findings TD-077 left open.

## Acceptance criteria

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | Music rack names visible at 1024 by 640 | **Met (still frame)** | `images/td78-1024x640-music-rack.png`: cause was the button row under the rack; now in `ScrollingBar.around` |
| 2 | Tiles tab shows something when nothing is selected | **Met (still frame)** | `images/td78-1024x640-tiles.png`; cause: the message was only set when the selection changed (pre-existing) |
| 3 | Palette ellipsis | **Met (re-read of an existing frame)** | `images/td77-1366x768-graph-after.png` shows "ShapeRendererCom..." |
| 4 | An automated test for the changes | **Not met** | the Tiles message needs a font (GL); the constant `NO_TILEMAP_MESSAGE` is package-visible for a later test |

## Commands and results

| Command | Exit | Result |
|---|---|---|
| `./gradlew :editor-desktop:run --args="--smoke-test"` with `GDXUNITY_EDITOR_WINDOW=1024x640`, Music rack (song aids) and Tiles | 0 each | frames above |
| `./gradlew test --continue --max-workers=1` | 0 | 20 modules, 0 failures |

Per-module counts, before and after (unchanged, no tests added): editor-animation 17, editor-audio 57, editor-character 10, editor-core 385 (1 skipped, pre-existing, not examined), editor-desktop 94, editor-sculpt 56, editor-sprite 51, editor-visualscript 89, engine-animation 32, engine-audio 222, engine-character 19, engine-core 35, engine-desktop 92, engine-physics 53, engine-render 119, engine-scene 149, engine-sculpt 81, engine-sprite 44, engine-visualscript 86, engine-web 9. No new or pre-existing failures. The web suite was not re-run (no web change; last 476).

## Changes

`MusicStudioPanel` (rack button row wrapped; rack cell min width 0), `TilemapStudioPanel` (message set at construction). Docs: TD-078 record, matrix row, INDEX line, this report, two frames.

## Known limitations and next steps

- **Nothing was clicked, scrolled or dragged**: whether the rack button row scrolls with the wheel is untried (**Manually Not Verified (Headless Environment)**).
- Not looked at: Piano roll, Playlist and Mixer sub-tabs, Graph pop-ups, 1280 by 720, larger sizes, the web editor. The Graph canvas is still about 200 px wide at 1024 by 640.
- Owner's end-to-end results are still the best input for the next milestone.

No commit, push or other Git write was performed.
