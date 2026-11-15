# Test Plan: 12 November 2026 (End-to-End Run of TD-073 to TD-079)

Purpose: a person checks, in the running editor, the things the agent could only see in still frames or not at all. Every case below is **Manually Not Verified (Headless Environment)** until you fill in the Result column. Record results in this file, or tell the agent which cases failed and it will turn them into the next milestone.

Related: [TECHNICAL_DECISIONS.md](TECHNICAL_DECISIONS.md) (TD-073 to TD-079), [FEATURE_TEST_MATRIX.md](FEATURE_TEST_MATRIX.md), [TEST_STRATEGY.md](TEST_STRATEGY.md), reports `reports/td-073-mockito-report.md` to `reports/td-079-scroll-input-mixer-report.md`.

Result key: **P** pass, **F** fail (write what you saw), **B** blocked, **N/A**. Mark audio cases by what you **hear**, not by what the screen shows.

## 1. Setup

| Item | Value |
|---|---|
| Build under test | working tree on top of `d896331` (uncommitted TD-069 to TD-079), branch `phase-12-3d-foundation` |
| Platform | Windows (note OS version and display scaling) |
| Display | at least 1920x1080 and one laptop-size window (1366x768) |
| Audio | speakers or headphones working; volume at a safe level |
| Tester / date / start time | |

Start the editor: `./gradlew :editor-desktop:run` (starts maximized, minimum window 1024x640).

Start at a chosen window size (test aid, not maximized): set `GDXUNITY_EDITOR_WINDOW=1024x640` (or `1366x768`, `1280x720`) before the run command. Sizes under 1024x640 are refused.

Open the demo song directly: `GDXUNITY_EDITOR_TAB=Music GDXUNITY_EDITOR_SONG=music/instruments-demo.song GDXUNITY_EDITOR_MUSIC_TAB="Channel rack"` (other values for `GDXUNITY_EDITOR_MUSIC_TAB`: Piano roll, Playlist, Mixer, Instrument; `GDXUNITY_EDITOR_CHANNEL=<name or number>` selects a channel). The full list of aids is in [AGENTS.md](../AGENTS.md) section 5.

## 2. Automated gate (run first)

| # | Command | Expected | Result |
|---|---|---|---|
| A1 | `./gradlew test --continue --max-workers=1` | exit 0, 20 modules, 0 failures (editor-core has 1 pre-existing skip) | |
| A2 | `cd engine-babylon` then `npm test` | 0 failures (last agent run: 476 tests, before TD-077) | |
| A3 | `cd engine-babylon` then `npx tsc --noEmit` and `npm run build` | exit 0 | |

If A1 fails, stop and report the failing module before doing the manual cases.

## 3. Window sizes and layout (TD-077, TD-078, TD-079)

Run each case at **1024x640** and **1366x768** unless stated.

| # | Steps | Expected | Result |
|---|---|---|---|
| L1 | Open the editor at each size. Look at the Viewport tab | Outline (left), Details (right), Content Browser and status bar fully visible; nothing cut at the window edges | |
| L2 | Hover over the top bar (tabs, New/Open/Save, tools) and scroll the mouse wheel | The bar scrolls sideways; a thin scroll bar shows while hovering; buttons still click normally | |
| L3 | Click a button in the top bar after scrolling it (for example Undo, Play) | It does what it did before (no dead or shifted click areas) | |
| L4 | Switch through every tab: Viewport, Scene, Animations, Visual Scripting, Music, Sprites, Tiles | Each tab shows its panel; top bars and status bar stay visible; layout never grows taller than the window | |
| L5 | Sprites tab at 1024x640: scroll the panel with the bars; then **draw in the sprite canvas** with the pencil | Panel scrolls with its visible bars; dragging on the canvas **draws** and does not scroll the panel | |
| L6 | Sprites tab: use the mouse wheel over the canvas and over the side panels | Wheel behaves sensibly (zoom or scroll as before); note anything odd | |
| L7 | Tiles tab with nothing selected | A message "Select an entity with a Tilemap component..." is shown (not a blank panel) | |
| L8 | Tiles tab: select an entity with a Tilemap component (or add one), then paint | Palette, canvas, tools show; painting works | |
| L9 | Visual Scripting tab at 1024x640: open a graph (for example `graphs/samples/ShooterShip.graph`) | Two toolbar rows scroll sideways; the palette takes a share of the column; the canvas is narrow (about 200 px, known) but usable; note if it is too small to work in | |
| L10 | Graph palette: look at long names (for example "Components: ShapeRendererCom...") | Long names end in "..." and do not widen the palette; no sideways scroll in the palette | |
| L11 | Animations tab: look at the Timeline toolbar at both sizes | Toolbar scrolls sideways; buttons have clear gaps; nothing overlaps | |
| L12 | Resize the window by dragging its edge from maximized down to the minimum | Layout follows without tearing or overlapping; no panel disappears | |
| L13 | Try to make the window smaller than 1024x640 | The window will not go smaller | |
| L14 | Start with `GDXUNITY_EDITOR_WINDOW=800x600` | Refused with a message (does not open at that size) | |

## 4. Music Studio layout (TD-078, TD-079)

Open `music/instruments-demo.song`. Run at 1024x640 and 1366x768.

| # | Steps | Expected | Result |
|---|---|---|---|
| M1 | Channel rack tab | Channel names (Kick, Snare, Hat, Lead, Bass, Bell, Harp) and mute boxes fully visible on the left; step grid to the right | |
| M2 | Rack buttons row under the rack ("+ Synth" to "Remove channel"); scroll it with the wheel while hovering | Row scrolls sideways; every button reachable and clickable | |
| M3 | Click a step in the rack, right-click it | Left click sets a step; right-click clears it; a Undo reverts | |
| M4 | Piano roll tab | Keys and grid visible; notes visible; velocity strip at the bottom | |
| M5 | Piano roll: add, move and delete a note | Works as before; note sounds when played | |
| M6 | Playlist tab | Tracks 1 to 8 visible with the bar ruler | |
| M7 | Mixer tab at 1024x640 | Strip(s) visible, "Pan 0.00" fully readable on the first strip, a **sideways scroll bar is always visible** under the strips | |
| M8 | Mixer: scroll the strips sideways (wheel and by dragging the bar) | All 7 channel strips and the master strip can be reached | |
| M9 | Mixer: move a fader, a pan control, press M and S, add an effect with "+ FX" | Values change; M and S toggle; effect appears; clicking an effect shows its settings on the right | |
| M10 | Transport rows (Play / Pause, Stop, Loop, Mode, BPM, Undo, Redo, Export): scroll if needed, press Play | Both rows visible at 1024x640; playback starts and the playhead moves | |

## 5. Instruments you can hear (TD-074, TD-075)

None of these sounds were ever listened to by the agent. Judge by ear: is it the named instrument, is it free of clicks, hiss, stuck notes and silence, and is the volume reasonable?

| # | Steps | Expected | Result |
|---|---|---|---|
| S1 | Play `music/instruments-demo.song` from the start | All channels (drums, lead, bass, bell, harp) are audible and in time; no clicks or stuck notes | |
| S2 | Instrument tab: step through every **synth preset** (14) with a held note | Each is distinct, pitched, and not silent; write down any that sound wrong | |
| S3 | Add a drum channel ("+ Drums"); play each drum note (15 drums: kick, snare, hats, toms, cymbals, cowbell and others) | Each is a recognisable drum; hats and cymbals end (no endless ring) | |
| S4 | Add an FM channel ("+ FM"); step through its presets (for example Bell) | Distinct bell/electric-piano/organ-like timbres; no harsh aliasing | |
| S5 | FM: change its settings (ratio, depth, envelope) while a note plays | Sound changes smoothly; no crash | |
| S6 | Add a string channel ("+ String"); step through its presets (for example Nylon Guitar) | Plucked-string sounds that fade naturally; pitch follows the key | |
| S7 | String: change damping and decay | Longer decay rings longer; damping makes it duller | |
| S8 | Play a chord (five or more notes at once) on an FM and on a string channel | No dropouts; no clipping crackle | |
| S9 | Save the song, close and reopen it | Instruments, presets and settings come back the same; plays the same | |
| S10 | Export a WAV (Export button) and play it in an external player | File exists, plays, sounds like the in-editor playback | |

## 6. Web player parity (TD-074, TD-075)

| # | Steps | Expected | Result |
|---|---|---|---|
| W1 | Run the web build (`npm run dev` in `engine-babylon`) and open the music page with the instruments demo song | Page loads without console errors | |
| W2 | Play the same song in the browser | Sounds like the desktop editor (same instruments; small differences in volume to be noted, not failures) | |
| W3 | Switch FM and string presets in the web studio | Sound changes accordingly | |
| W4 | Resize the browser window small | Note whether the web editor layout breaks (not yet reviewed by the agent; report only) | |

## 7. Tests and tooling (TD-073)

| # | Steps | Expected | Result |
|---|---|---|---|
| T1 | `./gradlew :engine-visualscript:test --tests "*GraphHostMockTest"` | exit 0, 3 tests pass | |
| T2 | In one test class, change an assertion so it must fail, run it, then undo the change | The test fails as expected, then passes again (shows the Mockito tests are not vacuous) | |

## 8. Regression sweep (existing features near the changes)

| # | Steps | Expected | Result |
|---|---|---|---|
| R1 | Viewport: select an entity, move it with the gizmo, Undo, Redo | Works as before | |
| R2 | Play and Stop a scene | Play runs, Stop restores | |
| R3 | Save and reopen a scene | Round-trips | |
| R4 | Graph editor: add a node by dragging it from the palette onto the canvas | Node appears where dropped | |
| R5 | Graph editor: connect two nodes, compile (Generate Java) | Works as before | |
| R6 | Content Browser: filter assets, open a folder | Works as before | |

## 9. Known gaps going in (do not report as new unless worse)

- The Graph canvas is about 200 px wide at 1024x640 with the palette open.
- The web editor, the Scene tab's panels, the Graph pop-ups, the Instrument sub-tab at 1280x720 and sizes above 1366 were never reviewed.
- The 8 FM/string presets and 17 synth/drum sounds were never listened to by the agent.
- editor-core has 1 skipped test that has not been examined.

## 10. Sign-off

| Item | Value |
|---|---|
| Cases run / passed / failed / blocked | |
| Failed cases (IDs and what you saw) | |
| Screenshots or logs saved at | |
| Tester | |
| Date and end time | |
