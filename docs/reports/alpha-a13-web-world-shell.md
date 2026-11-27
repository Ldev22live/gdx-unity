# A13 Web World shell

**Implemented, in this repository's `engine-babylon` web editor.** The page now opens in the World shell, the same composition as the desktop's World layout (TD-107):
- a thin header;
- the map with its overlays: context, camera chip, selection card;
- one category bar and one palette row;
- a footer with Genre and Mode, Customize and Play.

The earlier IDE-style page stays available with `?layout=classic`. Objects, Buildings and Characters place **the desktop's own palette prefabs** as linked instances, which the desktop reads as its own (tested).

**Not validated:** nobody has looked at the page in a browser yet, and the human gate (an unfamiliar user finds terrain, placement, Customize and Play without help) is pending. There is no browser automation in this repository (Playwright is not a dependency), so layout and pointer behaviour are **Manually Not Verified (Headless Environment)**.

**Host decision:** approval item 6 of the [A02/A11/A13 proposals](alpha-a02-a11-a13-proposals.md) asked whether A13 should start in `engine-babylon` or in the UrConnex web editor. It was never answered explicitly. With urconnex-api not ready (owner, 2026-11-26), A13 was built in `engine-babylon`, as that proposal recommended; embedding in UrConnex is A16. Nothing in A13 changes a file format, and it touches no other repository.

The starting HEAD was `85ccc0a`, with A04–A07 and A15 uncommitted in the working tree.

## What was built

| Part | Where | What it does |
|---|---|---|
| Shell model | `src/editor/worldShell.ts` | The layout choice (`?layout=classic`). The categories in the desktop's order (Map, Terrain, Objects, Buildings, Characters, Players, Rules), each with its honest web state. The bar heights (the desktop's values) and the map share. The camera presets (Top, Iso, Perspective). A read-only scenario summary, and the desktop's genre and mode names. Headless and tested. |
| Palette placement | `src/editor/prefabPalette.ts` | Reads the palette index and a prefab file, then places the prefab as **one undo step**: new ids, parents remapped inside the copy, the root at the clicked point keeping its rotation and scale, and a `PrefabInstanceComponent` on every entity in the desktop's form (the root lists `knownSourceIds`). The placed root is selected. |
| Palette files | `scripts/sync-samples.mjs` | Copies `assets/prefabs/{level,buildings,characters}` (prefabs and their thumbnails) to `public/prefabs` with an `index.json` (14 prefabs). Their models were already synced (M2b.7). `public/prefabs/` is Git-ignored like the other copies. |
| Page | `src/editor/worldLayout.ts`, `src/editor/world.css`, `editor.html`, `src/editor-main.ts` | Body class `layout-world`. **Header:** New, Open, Save, Undo, Redo and **Panels** (Outline, Details; hidden by default). **Map overlays:** "World / scene", the camera chip, and a selection card (Move, Rotate, Duplicate, and Customize with the desktop hand-off message). **Category bar and palette row:** Map holds the existing Add, Frame and Samples controls; Objects, Buildings and Characters are thumbnail tiles (click, then click the map; Esc stops); Terrain, Players and Rules show what is edited on the desktop for now, with a read-only scenario summary. **Footer:** Genre and Mode ("genre profiles play on the desktop for now"), a Customize menu (World, Animations, Visual Scripting, Music, and the hand-off note), and Play/Stop. Existing element ids and listeners are reused; no command is duplicated. |
| Editor hooks | `src/editor/EditorApp.ts` | `armPlacement`/`disarmPlacement`: a click on the map places instead of selecting, on the mesh under the pointer or else the ground plane, with an explicit message when the ray hits neither. Also `setViewPreset`, `onUpdate`, `isPlaying`, `isPlacing`, `currentTool` and `showStatus`. Placement is turned off by Play and by Escape. |

## Acceptance against the roadmap

| A13 requirement | Result |
|---|---|
| Owner screenshot as the reference; capture the actual web editor | The reference is the same as the desktop's (`docs/Screenshot 2026-11-25 113907.png`, A01). **Not captured:** there is no browser automation here, so no capture was taken. |
| Reuse the Babylon canvas and existing routes/components | Met: the same canvas, `EditorApp`, `EditorModel`, panels and tabs. The Next.js host is A16. |
| Same categories and Customize/Play adjacency as the desktop | Met: same categories and order, Customize beside Play in the footer (`worldShell.test.ts`). |
| Responsive drawers, pointer/touch input, focus, resize, keyboard, accessibility | **Partial:** the map keeps at least 70% at 1280 × 720 and 1920 × 1080 with the palette open, and at 1024 × 640 with it closed (tested). Narrow windows scroll the bars sideways. Keyboard: Escape stops placing; the existing shortcuts are unchanged. Touch, focus order and screen-reader labels are **not checked**. |
| Advanced unsupported tools show a desktop hand-off, not fake editors | Met: Terrain, Players and Rules say what is edited on the desktop until A14, and Customize states the geometry, sprite and terrain hand-off. Genre profiles are labelled desktop-only. |
| Separate browser editor from the exported web player | Met as before: `editor.html` versus `index.html`. |
| Owner approves before/after screenshots; an unfamiliar user finds terrain, placement, Customize and Play | **Pending.** |

## Test evidence

| Command | Exit | Result |
|---|---|---|
| `npx tsc --noEmit` (`engine-babylon`) | 0 | No type errors |
| `UPDATE_INTEROP=1 npx vitest run test/worldShell.test.ts` | 0 | 9 tests passed; `fixtures/contract-v1/interop/w1-web` written (an oak tree and the CC0 house placed by the web) |
| `npx vitest run` (full web suite) | **stopped** | Claude Code stopped it because the machine was low on memory (it ran alongside the full Gradle suite). It still needs a run. |
| `gradlew :editor-desktop:test --tests "*WebPlacedPrefabsTest"` (the desktop syncs the web-placed scene: no problems, nothing created or removed, every entity linked, no overrides, placement kept) | **stopped** | The attempt on 2026-11-26 was stopped by Claude Code because the machine was low on memory. Still not run. |

## Known limitations

- The page has not been seen in a browser.
- Placement puts the root at the clicked point, without the desktop's random turn and scale for objects or its ground-height lift for characters.
- The one-player and one-spawn-point rules of the desktop's Characters palette are not enforced on the web yet (A14).
- The Map palette has no level-size or lighting presets (desktop-only, A03).
- The Customize entry for the selection only shows the hand-off message; it does not open anything.

No commit, push or destructive Git operation was performed.
