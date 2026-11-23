# One ordered pass for everything marked "visual check pending" (2026-11-09)

Many features are implemented and tested but have never been used by a person: the Windows desktop editor, its mouse and keyboard
behaviour, anything that needs listening, and the web player in a real browser beyond what one automated pass saw. This page orders
the checks so one sitting covers them, with the detailed steps in each milestone page. Mark each line **seen**, **not as described**
(with what you saw), or **skipped**, and send me the list; I will turn it into status changes and fixes.

Nothing below needs a build step beyond `./gradlew :editor-desktop:run` (the desktop editor) and, for the web, `npm run build` then
`npx vite preview` in `engine-babylon` and the page at `http://127.0.0.1:4173/`. A pass over the web samples was done by an automated
Edge session on 2026-11-09 (see "Already seen" at the end).

## 1. Desktop editor, about 40 minutes

| # | Where | What to do | What you should see | Detailed steps |
|---|---|---|---|---|
| 1 | Start | `./gradlew :editor-desktop:run` | Four-pane 3D view, Outline left, Details right, Content Browser at the bottom | [phase-12b-editor-shell.md](../milestones/phase-12b-editor-shell.md) |
| 2 | Content Browser | Click a folder, a breadcrumb part and ".. (up)"; type in the filter; edit a `.png` in another program while the editor runs | The list follows; a search finds files in every folder; the status bar says "Assets changed on disk" within about 2 s and a sprite shows the new picture | [phase-13-asset-pipeline.md](../milestones/phase-13-asset-pipeline.md) item 0 |
| 3 | Models | Drag a `.glb` into the view; Ctrl+Z | A model entity appears under the mouse; undo removes it | phase 13, items 4 to 6 |
| 4 | Lighting | Open `samples/landscape-3d.json`; change sky light and tone mapping | Shadows and tone change as the settings change | [phase-13b-lighting.md](../milestones/phase-13b-lighting.md) |
| 5 | Depth | Open `samples/depth-compositing-3d.json` in the 3D view and in Play | The blue wall hides the left half of the green panel; the orange panel is over the red cube | phase 13, item 00 |
| 6 | Prefabs | Make a prefab from an entity, place two instances, change one, apply, revert | Links, overrides and apply / revert behave as listed | [phase-14-prefabs.md](../milestones/phase-14-prefabs.md) |
| 7 | 3D physics | Play `physics-sandbox-3d`, `physics-joints-3d`, `physics-mesh-3d`, `character-playground-3d` (WASD, Space) | Balls and crates settle; the door swings open; the bowl catches the balls; the character climbs the steps and the 25 degree ramp, not the steep one, and pushes a crate | [phase-15-3d-physics.md](../milestones/phase-15-3d-physics.md) |
| 8 | Visual scripting | Graph tab: right-click for the node menu; open Examples and an example; wire a node; Pure/Action on a selected Java node; Refresh Nodes | The menu searches; an example opens with its description; the button switches the node and undo reverts | [phase-16-visual-scripting.md](../milestones/phase-16-visual-scripting.md) |
| 9 | Animation | Animations tab: preview Walk, Run, Jump, Fall on a humanoid; Timeline: add a key | The clips play; keys are undoable | [phase-17-animation.md](../milestones/phase-17-animation.md) |
| 10 | Character creator | Animations tab > Character: move a slider, pick an animation, bake | The live preview follows the slider; baking places the model | `docs/reports/td-056-creator-preview-report.md` |
| 11 | Sculpting | Create > Sculpt From Selected; sculpt a few strokes; Details > Export Model; place the `.glb` | The surface changes under the brush; the exported model shows in the Content Browser and in the scene | [phase-19-sculpting.md](../milestones/phase-19-sculpting.md) |
| 12 | Game export | File > Export Game (packaged editor only) | A folder with the game `.exe`; it starts and plays the start scene | `docs/milestones/phase-18-packaging.md` |

## 2. Listening, about 15 minutes (speakers on)

| # | Where | What to do | What you should hear |
|---|---|---|---|
| 13 | Music tab | Open `music/demo.song` (repaired on 2026-11-09), press Play | Kick, snare, hats, bass and a lead with reverb and delay; no crackle |
| 14 | Music tab | Open `music/neon-drive.song`, then `funk-machine.song` | Each plays to the end of its playlist |
| 15 | Sound effects | Play `platformer-2d` (jump, coin) and `shooter-2d` | Effects on the right actions; a song in the background of `platformer-2d` |
| 16 | Positional sound | Add an Audio Listener to the camera and a spatial Audio Source (Spatial on) with sound `Coin` or a `.wav`; Play; trigger it with a script cue | Quieter with distance and in the left or right speaker as the source passes the camera. No sample scene has this yet |
| 17 | `.ogg` clip | Set an Audio Source's sound to an `.ogg` of the project | The clip plays. (The test fixture `engine-desktop/src/test/resources/sounds/tone.ogg` is a half-second 440 Hz tone) |

## 3. Web player in a browser, about 15 minutes

| # | Where | What to do | What you should see |
|---|---|---|---|
| 18 | Player | Open the page; play three or four samples with the keyboard | Each loads without a red status line; keys work |
| 19 | Character playground | Play it; hold D, press Space | The red character walks to the steps; the camera follows; the help text shows "On the ground" or "In the air" |
| 20 | Sound | Play `platformer-2d` | Effects and the song (one song at a time on the web) |
| 21 | Editor | Open the Editor link; Graph tab: Examples are not there (desktop only); Animations and Music tabs | The tabs open; a graph runs; a song plays |

## Already seen (an automated Edge session, 2026-11-09; not a person)

- All 32 samples load in the built web player with no console errors; the 3D physics samples ran after the joint-disposal fix.
- Screenshots of `character-playground-3d` (the character moved toward the steps with the follow camera tracking it), `physics-joints-3d`
  (door open, chain hanging, pendulum, slider moved) and `physics-mesh-3d` (balls resting in the bowl) were looked at.
- Not seen: anything with sound, any desktop window, mouse interaction.
