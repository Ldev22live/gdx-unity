# engine-babylon

Forgeroom for the web on [Babylon.js](https://www.babylonjs.com/), written in TypeScript (decision
[TD-041](../docs/TECHNICAL_DECISIONS.md), proposed). It is not a Gradle module: it uses npm and is not listed in
`settings.gradle`.

**Plan: player first, then editor.** Stage 1 (the player) is implemented, stage 2 (porting scripts) is partly done, and stage 3 (the editor) has its first version.

| Stage | What | State |
|---|---|---|
| 1 | Player: load the desktop editor's scene files and play them | Implemented, see below |
| 2 | Port the sample scripts, add 3D physics, models and sprites | In progress: 10 of 14 samples play with their game logic; `racing-3d`, `arena-fps-3d`, `puzzle-3d` and `fighter-25d` do not yet |
| 3 | Editor: outline, details, viewport with gizmos, undo, save in the same file format | First version, see "The editor" below |

## Run it

Needs Node 20 or newer.

```bash
cd engine-babylon
npm install
npm run dev          # copies ../assets/samples, then serves http://localhost:5173
npm test             # Vitest, headless (Babylon's NullEngine, no GPU), 60 tests
npm run build        # type-check and bundle into dist/ (plain static files)
npm run typecheck
```

Pick a scene in the drop-down, press **Play**, click the view and use the keys the sample's text shows. **Stop**
rebuilds the scene from the file, so Play never changes what was loaded.

`dist/` is static: serve it with any web server, from any folder (asset URLs are relative).

## The editor

`npm run dev`, then open http://localhost:5173/editor.html (`?scene=hopper-3d.json` opens a sample). It edits the same
scene files the desktop editor and the player use.

- **Outline** (left): the entities as a tree. Click to select; drag one onto another to make it a child, or onto the empty
  area to move it to the top level.
- **Details** (right): name, active, position / rotation (degrees) / scale, and every field of every component
  (numbers, text, true/false, lists, colours as separate fields). Add and remove components.
- **View**: orbit with the left mouse button, pan with the right, zoom with the wheel. Click an object to select it (gold
  outline); click empty space to clear. The Move, Rotate and Scale gizmos (W, E, R; Q for none) change the selected
  entity. One drag is one undo step.
- **Toolbar**: New, Open, Samples, Save (Ctrl+S: writes back to the opened file where the browser allows it, otherwise
  downloads `<name>.json`), Undo / Redo (Ctrl+Z, Ctrl+Y; the last 100 steps), Add (Empty, Box, Sphere, Plane, Rectangle,
  Text, Camera, Sun, Sky Light), Duplicate (Ctrl+D), Delete, Frame (F focuses the selection), Play / Stop.
- **Play** runs a copy of the scene in the same window, through the scene's camera, with the ported scripts; Stop brings
  the editing view back. Playing never changes the scene you are editing.

How it works: `src/editor/EditorModel.ts` holds the scene file, the selection and the undo history, with no Babylon or
DOM in it (tested headless). Every edit takes a snapshot first, so undo cannot get an inverse wrong. Moving something
only moves its node; any other edit rebuilds the scene, keeping the camera.

### Sculpting

Add → **Sculpt Sphere** (a 13,826-vertex cube sphere), select it, then press **Sculpt** (or B). The left mouse button now
paints; the middle or right button orbits; Esc or Done leaves. The panel offers the desktop editor's six brushes (Draw,
Inflate, Smooth, Flatten, Pinch, Grab), radius, strength, spacing, falloff, Subtract (or hold Ctrl), Mirror X / Y / Z
and a Wireframe overlay. A ring shows where the brush is. One stroke is one undo step. Keys, as on the desktop: `]` and
`[` for brush size, `=` and `-` for strength. **Subdivide** splits every triangle in four (13.8k to 55k vertices; a
second step is refused at the 100,000-vertex limit). **Export** downloads the desktop's `.sculpt` file (the same
binary layout, `GSCM` version 1) and **Import…** replaces the geometry from one.

The brushes are a port of `engine-sculpt`'s `SculptStroke` (same constants), in `src/sculpt/`. The geometry is stored in
the scene file as a `SculptMeshComponent` (base64 positions and triangles + a colour), so it saves, reloads and plays with
the scene. **The desktop editor does not know this component**: it keeps sculpts in separate `.sculpt` files and exports
glTF, so a scene with a web sculpt shows it only here (the desktop reports the component as unknown). Limits: at most
100,000 vertices (brushes scan every vertex), no subdivision, no glTF export, no masking, and a 14k-vertex sculpt adds
about 0.2 MB to the file per undo step held in memory.

### Music

The **Music** tab (next to Viewport) is the desktop music studio's web version. It reads and writes the desktop's
`.song` files (version 1), so a song made on either side opens on the other; the Songs menu lists the bundled ones.

- **Transport**: Play/Pause (Space), Stop, Loop, Pattern or Song mode, BPM, swing, a bar.beat counter, a master meter.
  Open, Save (Ctrl+S), New, Undo/Redo (the last 100 steps), **Export WAV** (16-bit stereo, rendered faster than real time
  by the same player that plays live, with the reverb tail kept).
- **Channel rack and step sequencer**: click steps to switch them (a click also sounds the note); mute, add Synth or
  Drums channels, remove; patterns can be added, duplicated, renamed, deleted and set to 1-16 bars.
- **Piano roll** (selected channel): click to add a note and drag sideways for its length, drag a note to move it,
  drag its right edge to resize, right-click to delete; snap 1/4 to 1/32; the wheel scrolls pitch; click the keys to hear
  them.
- **Playlist**: click a bar to place the selected pattern, drag a clip to move it (to another track too), drag its
  right edge to resize, right-click to delete. Clips shorter than their pattern cut it; longer ones repeat it.
- **Mixer**: eight inserts and a master with volume, pan, mute, solo, meters and up to four effects each (delay, reverb,
  filter). Knobs act live and never restart a reverb tail.
- **Instrument**: the synthesizer (two oscillators, ADSR, filter with envelope amount, LFO, five presets) or the drum kit.

How it works: `src/audio/` is the engine, a TypeScript port of `engine-audio` with no DOM in it: oscillators with
PolyBLEP, ADSR, biquad filter, 16-voice synth, generated drums, delay / reverb / filter, the mixer with a soft limiter,
the song model, the scheduler (swing, clips) and a sample-accurate `SongPlayer`. Live sound comes from an
`AudioWorklet` (`worklet.ts`) that runs that same player; the page owns the song (`MusicModel`, with its own undo)
and sends it to the worklet whole, except knob moves, which go as small messages. The browser starts sound only after a
click, so the audio thread starts on the first Play or note preview.

Limits: samples, sfxr and SoundFont instruments are **read, kept and saved but silent** (the Music tab says so; the
desktop's demo song has one SoundFont and one sfxr channel, so those two sound silent here). No sample or SoundFont file
loading, no sfxr, no WAV import. The audio runs at the browser's sample rate; exports render at 44.1 kHz like the
desktop. Songs do not play inside a scene yet (the desktop has no audio component either).

Not in the first version: selecting more than one entity, snapping, copy and paste between scenes, editing script
bindings except as raw text, showing cameras and lights in the view (select them in the outline), a bone or animation
editor, and moving a child keeps its local transform when you re-parent it (the desktop editor may differ).

## What the player does

- Reads scene files with `schemaVersion` 1 unchanged. `npm run sync-samples` copies `../assets/samples/*.json` into
  `public/samples` (ignored by git), so the desktop editor and this player play the same files.
- Draws Mesh Primitive (box, sphere, plane), Shape Renderer (filled rectangles), Directional Light with shadows, Sky Light,
  ACES tone mapping, Camera (perspective and orthographic, driven by the camera entity), and Text (HTML over the canvas,
  nine anchors).
- Scenes without a camera get an orbit camera that frames everything.
- 2D physics from Rigid Body 2D and Box Collider 2D, with [planck.js](https://piqnt.com/planck.js/) (a JavaScript port
  of Box2D), at 100 pixels per metre and a fixed 1/60 s step like the desktop engine.
- Runs scripts. See below.
- Lists what it cannot do: the status line names component types it does not support and script classes that have no web
  version.

## Scripts

The scenes bind **Java** classes (`samples.collector.CollectorGame`). A browser cannot run them here (the
[`engine-web`](../engine-web/README.md) module runs them by compiling the Java runtime to JavaScript instead). So each
script is ported to TypeScript by hand: extend `ScriptComponent` (`src/runtime/ScriptComponent.ts`) and register the
port in `src/scripts/registry.ts` under the Java class name.

Ported so far (10 of the 14 samples play with their game logic; the physics sandbox has no scripts):
`collector-3d`, `shooter-2d`, `breakout-2d`, `platformer-2d`, `runner-3d`, `hopper-3d`, `ruins-runner-25d`,
`grapple-gorge-25d` and `three-heroes-25d`. **Not ported yet:** `racing-3d`, `arena-fps-3d`, `puzzle-3d` and
`fighter-25d`; they load and render, but their game logic does not run. The Java sources are in
`../assets/scripts/samples`, and `assets/samples/README.md` lists which scripts each scene uses. Scripts that use a
seeded `java.util.Random` get the same random numbers as on the desktop (`JavaRandom` is an exact port).

The script API: `this.entity` (`getWorldPosition`, `setLocalPosition`, `setLocalEulerAngles`, `setActive`,
`getComponent`, `rotate`, `setLocalScale`, `getWorldRotation`, `setPrimitiveColor`), `this.scene` (`findByName`,
`findByPrefix`, `createEntity`, `addMeshPrimitive`, `addShapeRenderer`), `this.input` (`isDown`, `isJustPressed` with
`KeyboardEvent.code` values such as `KeyW`), and `onStart()` / `onUpdate(dt)`. A script that throws is switched off
and reported in the status line; the game keeps running.

## Layout

```
src/scene/types.ts         scene file types and checked field readers
src/runtime/SceneBuilder   scene file -> Babylon scene + entity tree
src/runtime/GameEntity     entity (transform node + data) and scene lookups
src/runtime/Physics2D      planck.js bodies and the step loop
src/runtime/ScenePlayer    render loop, Play and Stop, script loop
src/runtime/Hud            Text components
src/scripts/               ported sample scripts and the registry
src/editor/                the editor: model (undo), outline, details, viewport tools
editor.html                the editor page (index.html is the player)
test/                      Vitest; reads the real sample files
```

## Known limits

- Four samples' scripts are not ported (see above); no 3D physics, glTF models, sprites, animation clips, graphs, audio or
  sculpts; Circle Collider 2D is not read.
- Colours and tone mapping are close to the desktop renderer, not identical (it uses PBR; this uses Babylon's standard
  material). The shadow bias was tuned by eye on one scene.
- The bundle is 6.5 MB (1.4 MB gzipped) because everything is imported from `@babylonjs/core`; importing the individual
  files would make it much smaller.
- Checked in headless Edge with a software renderer only (see
  [`FEATURE_TEST_MATRIX.md`](../docs/FEATURE_TEST_MATRIX.md)). Not tried on a real GPU or in other browsers.
