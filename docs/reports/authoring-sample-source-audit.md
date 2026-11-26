# Authoring Sample Source Audit

**2026-11-24 follow-up.** The M7a.6 check verified Cube Diorama against both the official item listing and its bundled Blender README. The three fixtures, source records and conversion evidence are in the [M7a.6 report](m7a-6-source-preservation.md). The original audit below records the information available at the time.


Roadmap MC.1 (Appendix C7). **Date:** 2026-11-18. **Branch:** `phase-12-3d-foundation`. **HEAD at start and end:** `0469ceb` (nothing committed).

This is the audit Appendix C7 asks for before anything is downloaded: candidate open-content sources for the 3D Modeling, 2D and Music/SFX workspaces. For each candidate it records:
- its URL;
- its exact licence, and how that was verified;
- its editable source format;
- the conversion ForgeRoom would need;
- the sample it would serve.

**Nothing was downloaded.** Only the pages that state each licence were read, plus Poly Haven's public API for file lists. Nothing in the repository changed except this report and the status rows that point to it.

## 1. What the workspaces can take in today

Checked in the source on 2026-11-18. This decides each candidate's conversion.

| Workspace | Reads | Edits | Cannot |
|---|---|---|---|
| 3D models | glTF 2.0 and GLB (`ModelAssets`, gdx-gltf, TD-030) | Placement, import scale and materials in Details; a project-owned **sculpted copy** (Customize > Edit in 3D) | No mesh editor yet (R7a); no `.blend`, FBX or OBJ import (M9.2 not started); a glTF that names external `.bin` and textures needs all of them |
| 2D | The native `.sprite` (layers, frames, tags; TD-066). A PNG **sprite sheet** can be cut into the frames of a new `.sprite` (`SpriteSheet.importSheet`, Sprite Studio) | Pixel tools, layers, frames, tags, palette; tile maps with auto-tile rules and collision | No SVG, OpenRaster (`.ora`) or Aseprite import: nothing in the code reads them. A PNG comes in as one layer: its original layers are not recovered |
| Sound effects | Factory (sfxr) cues; recorded `.wav` and `.ogg` clips played by an Audio Source (`AudioSourceComponent.CLIP_EXTENSIONS`, `OggClips` decodes Ogg Vorbis) | The cue, volume and pitch in Details | No waveform editor (trim, fade, loop: MC.5); nothing writes Ogg; no `.7z` extraction |
| Music | The native `.song` (notes, patterns, mixer, instruments) and SoundFont instruments (`SoundFont`, TD-040) | All of it in Audio & Music | No MIDI file import; sampled-instrument libraries are Appendix D's audit (M8.5), not this one |

## 2. Candidates

**Verified** below means the licence was read on the provider's own page on 2026-11-18, quoted in section 3. "Formats" are what the page or API states; where a page does not list file types, that is said rather than guessed.

| # | Workspace | Candidate | URL | Licence | Verified | Formats stated | Editable source | Conversion for ForgeRoom | Migration target |
|---|---|---|---|---|---|---|---|---|---|
| 1 | 3D | Kenney **Building Kit** (80 files) | https://kenney.nl/assets/building-kit | CC0 | **Yes**: asset page "Creative Commons CC0"; Kenney support page: all asset-page game assets are CC0 | Not listed on the page; inspect the archive | Interchange meshes; mesh editing only after R7a | Load directly if the archive has GLB/glTF; otherwise an FBX or OBJ importer (M9.2) or an offline conversion with its tool recorded | MC.2 house slice; a modular house to replace the generated `cabin.gltf` in the landscapes (M3.10, R7a) |
| 2 | 3D | Poly Haven **Rollershutter Door** (1,104 triangles) | https://polyhaven.com/a/rollershutter_door | CC0 | **Yes**: Poly Haven licence page; files from `api.polyhaven.com/files/rollershutter_door` | blend, gltf, fbx, usd (1k `.blend` 153.5 KB; 1k `.gltf` 5.4 KB plus its `.bin` and textures) | **`.blend` source available** (kept as the reference original) | Use the glTF with its buffers and textures; keep the `.blend` unmodified beside it as the source | MC.2: the CC0 conversion fixture Appendix C3 asks for. It replaces Blender's demo files, which could not be verified (row 13) |
| 3 | 3D | Poly Haven **Stone Fire Pit** (3,887 triangles) | https://polyhaven.com/a/stone_fire_pit | CC0 | **Yes**: as row 2; files from the API | blend, gltf, fbx, usd (1k `.blend` 255.6 KB) | `.blend` available | As row 2 | A light realistic prop for `action-rpg-valley-3d` (MP.2's 3D Action RPG set) |
| 4 | 3D | Poly Haven **Boulder 01** (123,976 triangles) | https://polyhaven.com/a/boulder_01 | CC0 | **Yes**: as row 2; files from the API | blend, gltf, fbx, usd (1k `.blend` 5.37 MB) | `.blend` available | As row 2; needs an LOD or decimated copy before scattering | **Stress test only** (Appendix C3): M2b.4 frame cost and M3.6 scatter. Too heavy for a first editing fixture |
| 5 | 2D | Kenney **Pixel Platformer** (200 files, 18 × 18 tiles) | https://kenney.nl/assets/pixel-platformer | CC0 | **Yes**: asset page "Creative Commons CC0" | Not listed; tiles and characters as PNG expected, to be inspected | Pixel level after import; no layers | `SpriteSheet.importSheet` cuts a sheet into a `.sprite` (the tile set and character frames); a tile set needs its 16 auto-tile pieces arranged as the `ground` rule expects | MP.2's 2D platformer set; replaces the project-made `tiles.sprite` art in `platformer-tiles-2d` and `tilemap-platformer-2d` (MT.1). The originals stay as fixtures |
| 6 | 2D | Kenney **UI Pack** (430 files) | https://kenney.nl/assets/ui-pack | CC0 | **Yes**: asset page "Creative Commons CC0" | Not listed | Pixel level after import | Buttons and panels as `.sprite` | Blocked on in-game UI (M6.3): today a game's interface is Text components only |
| 7 | SFX | Kenney **RPG Audio** (50 files; tags footstep, foley, weapon) | https://kenney.nl/assets/rpg-audio | CC0 | **Yes**: asset page "Creative Commons CC0" | Not listed; `.ogg` expected, to be inspected | The clip; a recording does not reveal how it was made | None if `.ogg` or `.wav`: an Audio Source plays it. Editing waits for MC.5's waveform editor | Footstep and interaction sounds for MC.6; the 3D Action RPG set |
| 8 | SFX | Kenney **Impact Sounds** (130 files) | https://kenney.nl/assets/impact-sounds | CC0 | **Yes**: asset page "Creative Commons CC0" | Not listed | As row 7 | As row 7 | Hits and landings in the platformers and `hopper-3d` |
| 9 | SFX | OpenGameArt **Door Open SFX**, by Oiboo | https://opengameart.org/content/door-open-sfx | CC0 | **Yes**: listing "License(s): CC0" | `door_open.wav`, 769.8 KB | As row 7 | None: a WAV plays as a clip | **The door sound of MC.6** (Appendix C5/C6 vertical slice 4); the simplest candidate |
| 10 | SFX | OpenGameArt **Door open, door close**, by Iwan Gabovitch (submitted by qubodup) | https://opengameart.org/content/door-open-door-close | CC0 | **Yes**: listing "License(s): CC0" | `door.7z`, 1.9 MB | As row 7 | Extract the 7z archive with an external tool, recording the tool. Nothing in the project reads 7z | A second door set (open and close) if row 9 is not enough |
| 11 | Music | ForgeRoom's own `.song` files (7 in `assets/music`) | in the repository | project-made, no licence record | No: the owner's decision (MP.1, MT.8) | `.song` | **Fully editable**: the composition is the source | None | MC.6's editable song. No outside source is needed for an editable song |
| 12 | 2D format | **OpenRaster** | https://www.openraster.org/ | A format, not assets; the page states no licence for the specification | n/a | ZIP of an XML layer stack with PNG (raster) or SVG (vector) layers; Krita, GIMP, MyPaint and Scribus write it | Layers would be preserved | An `.ora` reader and writer: a TD and a format audit first (Appendix C4) | MC.3's layered 2D source; to be fed with ForgeRoom-made fixtures, not outside art |

### Not verified, or not a source of shippable assets

| # | Candidate | URL | Why not | What it would take |
|---|---|---|---|---|
| 13 | Blender demo files: **Human Base Meshes**, **Cube Diorama**, the Geometry Nodes examples | https://www.blender.org/download/demo-files/ | **Not verified.** The page returned 403 to the web fetcher, and to `curl` a bot challenge ("Just a moment"). A search engine's listing of the page says Human Base Meshes v1.2 is CC0. Blender's 4.0 release note on asset bundles states no bundle licence, only the wiki's CC-BY-SA 4.0 for its own text. The page mixes licences (Appendix C2) | A person opens the page in a browser, confirms the CC0 line per item, and reads each bundle's included licence file. Until then these are **not candidates**; Poly Haven's `.blend` models (rows 2 to 4) fill the CC0 `.blend` fixture role |
| 14 | OpenGameArt **CC0 Sound Effects** collection, by OwlishMedia | https://opengameart.org/content/cc0-sound-effects | A collection of links, not a licence. Each linked item has its own listing | Discovery only: verify each item as rows 9 and 10 were |
| 15 | **Freesound** | https://freesound.org/ | Every sound has its own licence: CC0, CC BY, CC BY-NC or Sampling+. Downloading needs a logged-in account; Freesound points large-scale use to its Data Packs portal, not the API | Discovery only, one sound at a time, CC0 only. No sound is selected here, and nothing may be scraped or bulk-downloaded |
| 16 | **Surge XT** | https://github.com/surge-synthesizer/surge | GPL-3.0 software, not an asset library (Appendix C1) | Reference only: no code, presets or factory content are copied |

## 3. Licence evidence (2026-11-18)

| Source | Where | What it says |
|---|---|---|
| Kenney | https://kenney.nl/support | "Yes, all game assets on the asset pages are public domain licensed (CC0)." "You're free to use them, even in commercial projects." "Attribution is not required." The 'Kenney' logo is not to be used separately. Redistribution inside another product is not addressed beyond CC0 itself |
| Kenney asset pages | rows 1, 5 to 8 | Each shows "Creative Commons CC0" |
| Poly Haven | https://polyhaven.com/license | "Our assets are all licensed as CC0": any purpose, commercial included; no credit needed; may be redistributed, including in a product that is sold. The site's other content (logos, text) is not CC0 |
| Poly Haven API | `https://api.polyhaven.com/info/<id>`, `/files/<id>` | Names, authors and triangle counts (Boulder 01 by Rico Cilliers, 123,976; Rollershutter Door by MP, 1,104; Stone Fire Pit by Sebastian Platen, 3,887) and the per-format file URLs and sizes. The info record carries no licence field: the licence is the site-wide one above |
| OpenGameArt | listing pages of rows 9 and 10 (read with `curl`; the web fetcher was refused) | "License(s): CC0" on each; authors and files as in the table |
| Freesound | https://freesound.org/help/faq/ | Licences per sound (CC0 "you can do pretty much what you want"; CC BY needs credit; CC BY-NC no commercial use; Sampling+ being retired); downloads need a logged-in account |
| Surge XT | GitHub repository | "GPL-3.0 license" |

Every candidate still needs, at download time, the item checks Appendix C1 lists:
- the licence file inside the archive;
- the SHA-256;
- the original file name;
- the retrieval date;
- the converted format and the tool used.

That belongs to M2b.1's manifest, which this audit does not create.

## 4. Order of use (Appendix C6), and what blocks each step

| Slice | Candidate | Blocked on |
|---|---|---|
| 1. 3D house | Row 1, or a project-owned primitive house; row 2 as the conversion fixture | R7a mesh editing (M7a.1 to M7a.5); M2b.1 manifest; the Legal gate for downloads |
| 2. 2D house or character | Row 5, plus a ForgeRoom-made layered `.sprite` | MC.3 (vector and layer work); the manifest |
| 3. 2.5D hybrid | Rows 3 and 5 | MC.2 and MC.3 |
| 4. Audio | Row 9 (door), row 11 (song) | MC.5's waveform editor for the "edit the SFX" step; playing it needs nothing new |
| 5. Regression | The current samples kept as fixtures (the MP.3 copies show the pattern) | n/a |

## 5. Findings

- **The CC0 claims hold where they could be read.** Kenney's and Poly Haven's licences are site-wide CC0, stated on their own pages. The two OpenGameArt door sounds are CC0 on their listings.
- **Blender's demo files could not be verified** from here: the page refuses automated readers. A person has to confirm them. Poly Haven's `.blend` sources are a verified substitute for the CC0 `.blend` fixture.
- **Kenney's pages do not list file formats,** so whether a kit ships GLB, FBX or OBJ, or PNG and SVG, is unknown until the archive is inspected. That decides whether M9.2's importers are needed for row 1.
- **The door sound (row 9) needs no new code to play.** Editing it needs MC.5.
- **The 2D Studio already turns a PNG sheet into an editable `.sprite`** (pixel level, one layer). Kenney's 2D packs fit today. Layered and vector sources (OpenRaster, SVG) need MC.3 and a TD.

## 6. Milestone record (MC.1)

| # | Criterion (chart row MC.1) | Status | Evidence |
|---|---|---|---|
| 1 | The report exists | **Met** | This file |
| 2 | Every candidate URL, exact licence, editable source format, required conversion and migration target | **Met** | Section 2: 12 candidates plus 4 recorded as not verified or not shippable |
| 3 | Drawn from the real 3D, 2D and Music/SFX workspaces and current samples | **Met** | Section 1 (checked in the source); targets name current samples and milestones |
| 4 | Nothing downloaded | **Met** | Only licence pages and Poly Haven's file lists were read |
| 5 | Done-when: "each candidate has a verified licence" | **Met for every candidate kept; not for Blender's demo files** | Section 3. Blender's page refuses automated readers, so its items are excluded until a person confirms them (row 13) |

- **Commands:**
  - `WebFetch` and `WebSearch` on the URLs above;
  - `curl -s -L -A "Mozilla/5.0 ..."` on the two OpenGameArt listings (200) and Blender's page (403, bot challenge);
  - `grep` over the source for section 1.
- **Tests:** no code changed and no tests were run for this milestone. The last runs earlier today were editor-desktop 175, engine-desktop 147 and editor-core 539, all passing.
- **Changed besides this file:** the MC.1 row in `docs/milestone-chart.md`, `docs/FEATURE_TEST_MATRIX.md` and `docs/INDEX.md`.
- **Git:** no commit, push, stash, reset or other Git operation was performed. HEAD is `0469ceb` at start and end.
- **Next:** with this audit, MC.2 to MC.7 have their source list. Each still waits on its own gates (R7a, MC.3, MC.5, the Legal gate and M2b.1's manifest).
