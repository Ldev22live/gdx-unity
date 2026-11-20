# Asset Integration Report: Discovery and Shortlist

**Date:** 2026-11-19. **Branch:** `phase-12-3d-foundation`. **HEAD:** `0469ceb` (nothing committed).

This is the brief's **initial deliverable**: a repository assessment and a shortlist of verified, redistributable test assets for the sample games. It builds on the MC.1 source audit ([authoring-sample-source-audit.md](reports/authoring-sample-source-audit.md), 2026-11-18) rather than repeating it.

**Status (end of 2026-11-19):**
- **Approval:** the owner approved the shortlist ("All usable rows") and chose to keep files in the working tree, uncommitted.
- **Downloaded and integrated:** the 5 Poly Haven items, 20 files, about 11 MB (section 8).
- **Not downloaded:** the Kenney packs and the OpenGameArt door sound. **Their sites' `robots.txt` disallow AI agents by name**, so a person has to download them in a browser (section 8.2).
- **Record:** every downloaded file is in a licence manifest that the code reads and a test guards ([TD-094](TECHNICAL_DECISIONS.md), proposed).

---

## 1. Phase 1: what the engine can take today

Verified in the source on 2026-11-19.

| Area | What exists | Formats | Evidence | Gap for downloaded assets |
|---|---|---|---|---|
| Asset types and the Content Browser | `AssetType` lists every type the browser knows. Models and sprites have thumbnails (`ModelThumbnails`, `SpriteThumbnails`). Prefabs, models and materials place by drag and drop (`AssetPlacement`) | see the rows below | `engine-core` `asset/AssetType.java`; `editor-desktop` `ui/AssetBrowserPanel.java` | Nothing records where a file came from or its licence: M2b.1's manifest is not built |
| 3D models | glTF 2.0 and GLB through gdx-gltf (TD-030). A `.gltf` with separate `.bin` and textures needs all of them beside it | `gltf`, `glb` | `AssetType.MODEL`; `engine-render` `ModelAssets` | **No FBX, OBJ or `.blend` import** (M9.2 not started) |
| Skeletal animation | `ModelComponent` plays a glTF model's own animations, such as a walk cycle, and lists their names and lengths. Keyframe clips (`.anim`), FK and two-bone IK, and the MakeHuman-based character creator also exist | glTF animations, `.anim` | `engine-render` `components/ModelComponent.java`; `engine-animation` | No retargeting of a downloaded clip onto another rig (M5.2) |
| PBR materials | A `.material` holds PBR parameters and five texture slots: base colour, normal, metallic-roughness, occlusion and emissive. It also has tiling and offset. Applied to primitives; **a glTF model keeps its own materials** (no material slot, M2.2 finding) | `.material` | `engine-material` `Material.java` | glTF models cannot take a `.material` |
| Textures | PNG and JPG | `png`, `jpg`, `jpeg` | `AssetType.TEXTURE` | No KTX2, WebP, EXR or HDR |
| Sky and image-based lighting | The sky light is baked from a two-colour sky (`BakedSkyLightMaps`, gdx-gltf `IBLBuilder`). Sky, fog and weather presets (M3.9) | none read from a file | `engine-render` `BakedSkyLightMaps.java` | **No HDRI or skybox file is read.** An `.hdr` or `.exr` environment needs new code |
| Terrain | `.terrain` heightmaps with brushes and holes; paint layers are **flat colours** (`Surface`: grass, sand, snow, gravel, rock, dirt). Heightmap import and export (M3.5) | `.terrain`, PNG heightmaps | `engine-terrain` `Surface.java` | **Terrain layers cannot take textures**, so PBR ground textures can be shown only on primitives |
| 2D | `.sprite` (layers, frames, tags; TD-066). A PNG sprite sheet is cut into a new `.sprite` (`SpriteSheet.importSheet`). Tile maps | `.sprite`, PNG sheets | `engine-sprite` | No SVG, Aseprite or OpenRaster import; a PNG comes in as one layer |
| Audio | Audio Source plays recorded clips; sfxr cues; SoundFont instruments; `.song` music | `wav`, `ogg` | `AssetType.SOUND`; `engine-audio` | No waveform editor (MC.5); no MP3 |
| UI | A game's interface is Text components only | n/a | MC.1, M6.3 | UI art packs cannot be used in a game until M6.3 |
| Scene save and reload | JSON scenes, with stable ids (M9.3) and round-trip tests | `.json` | `SceneSerializer` | none |
| Demo scenes | 47 samples (`assets/samples/`), already covering most of Phase 6's list (section 5) | | [sample-asset-migration-inventory.md](reports/sample-asset-migration-inventory.md) | They use project-made shapes, sprites and generated models, not downloaded art |

**Repository facts that limit downloads:**
- **Size:** `assets/` is 31 MB.
- **Git LFS:** none (`.gitattributes` sets only line endings).
- **AGENTS.md:** nothing may be committed without the owner's say.

So downloaded binaries stay uncommitted until the owner decides how to keep them: plain Git, LFS, or a fetch script.

## 2. Phase 2: shortlist (18 sources)

**Licence verification** was done on the provider's own pages on 2026-11-19, quoted in section 3. MC.1 had verified rows 1, 4 to 6 and 9 to 13 the day before; they were rechecked today where marked.

**Formats:** Kenney's pages do not list file formats, so a Kenney row's format is confirmed only when its archive is opened.

| # | Asset | Category | Source URL | Licence | Author | Stated formats and size | Fits ForgeRoom today | Exercises | Sample it serves |
|---|---|---|---|---|---|---|---|---|---|
| 1 | Kenney **Building Kit** (80 files) | Buildings | https://kenney.nl/assets/building-kit | CC0 (MC.1) | Kenney | Not stated | Yes if the archive has GLB or glTF; otherwise needs M9.2 or an offline conversion | Model rendering, thumbnails, placement | `action-rpg-valley-3d`; MC.2 house slice |
| 2 | Kenney **Nature Kit** (330 files) | Trees, rocks, plants | https://kenney.nl/assets/nature-kit | CC0 (page checked today) | Kenney | Not stated | As row 1 | Low-poly vegetation, scatter (M3.6) | The landscape samples; `terrain-3d` |
| 3 | Kenney **Blocky Characters** (20 files, "Animation" feature) | Animated characters | https://kenney.nl/assets/blocky-characters | CC0 (page checked today) | Kenney | Not stated; animated per the page, rig not stated | Yes **only if** the archive has glTF or GLB with animations | Skeletal animation playback in `ModelComponent` | `animation-samples-3d`; a profile sample's player |
| 4 | Poly Haven **Rollershutter Door** | Architecture prop | https://polyhaven.com/a/rollershutter_door | CC0 | MP | blend, gltf, fbx, usd; 1k `.blend` 153.5 KB | Yes, with the glTF's `.bin` and textures | Model plus PBR textures; `.blend` kept as source | MC.2 conversion fixture |
| 5 | Poly Haven **Stone Fire Pit** (3,887 tris) | Environment prop | https://polyhaven.com/a/stone_fire_pit | CC0 | Sebastian Platen | as row 4; 1k `.blend` 255.6 KB | Yes | Realistic PBR model | `action-rpg-valley-3d` |
| 6 | Poly Haven **Boulder 01** (123,976 tris) | Rock | https://polyhaven.com/a/boulder_01 | CC0 | Rico Cilliers | as row 4; 1k `.blend` 5.37 MB | Yes, but heavy | **Stress test only**: frame cost (M2b.4) | none until an LOD exists |
| 7 | Poly Haven **Red Brick 03** | PBR texture | https://polyhaven.com/a/red_brick_03 | CC0 (page checked today) | Rob Tuytel | 1k JPG: diffuse 695 KB, normal (GL) 955 KB, `arm` 163 KB | **Yes**: base colour, normal, and `arm` in both metallic-roughness and occlusion | PBR materials on primitives | `materials-3d` |
| 8 | Poly Haven **Wood Floor Deck** | PBR texture | https://polyhaven.com/a/wood_floor_deck | CC0 | Dimitrios Savva | 1k JPG: diffuse 926 KB, normal 665 KB, `arm` 719 KB | Yes, as row 7 | PBR, tiling | `materials-3d`; a floor in a profile sample |
| 9 | Poly Haven **Rocky Terrain 02** | PBR texture | https://polyhaven.com/a/rocky_terrain_02 | CC0 | Amal Kumar | 1k JPG: diffuse 843 KB, normal 994 KB, `arm` 525 KB | On primitives only (terrain layers are colours) | PBR rock | `materials-3d`; a future textured terrain layer |
| 10 | Kenney **Pixel Platformer** (200 files) | 2D tiles and characters | https://kenney.nl/assets/pixel-platformer | CC0 (MC.1) | Kenney | Not stated; PNG expected | Yes: `SpriteSheet.importSheet` to `.sprite` | 2D sprites, tile maps, sprite animation | `platformer-tiles-2d`, `pixel-adventure-2d` (MP.2) |
| 11 | Kenney **UI Pack** (430 files) | UI | https://kenney.nl/assets/ui-pack | CC0 (MC.1) | Kenney | Not stated | As sprites; in-game UI waits for M6.3 | Sprite import only | none until M6.3 |
| 12 | Kenney **Game Icons** (105 files) | UI icons | https://kenney.nl/assets/game-icons | CC0 (page checked today) | Kenney | Not stated | As sprites | Sprite thumbnails and search | Content Browser categories |
| 13 | Kenney **RPG Audio** (50 files) | SFX: footsteps, foley | https://kenney.nl/assets/rpg-audio | CC0 (MC.1) | Kenney | Not stated; OGG expected | Yes if `.ogg` or `.wav` | Audio Source playback | Footsteps in `action-rpg-valley-3d` |
| 14 | Kenney **Impact Sounds** (130 files) | SFX: hits | https://kenney.nl/assets/impact-sounds | CC0 (MC.1) | Kenney | Not stated | As row 13 | Audio on collisions | `first-person-range-3d` (shot hits), `hopper-3d` |
| 15 | Kenney **Interface Sounds** (100 files) | SFX: UI | https://kenney.nl/assets/interface-sounds | CC0 (page checked today) | Kenney | Not stated | As row 13 | Audio preview in the browser | Menus once M6.3 exists |
| 16 | OpenGameArt **Door Open SFX**, by Oiboo | SFX | https://opengameart.org/content/door-open-sfx | CC0 (MC.1) | Oiboo | `door_open.wav`, 769.8 KB | Yes: plays as a clip | Audio | MC.6 door slice |
| 17 | Poly Haven **Kloofendal 48d Partly Cloudy (Pure Sky)** | HDRI | https://polyhaven.com/a/kloofendal_48d_partly_cloudy_puresky | CC0 | Greg Zaal, Jarod Guest | `hdr` or `exr` only at 1k; tonemapped JPG 22 MB | **No**: nothing reads HDR or EXR | Listed to record the capability gap | none; **do not download** |
| 18 | ForgeRoom's own `.song` files (7) | Music | `assets/music/` | project-made | ForgeRoom | `.song` | Yes, fully editable | Music | already in samples |

**Download volume if all usable rows are approved:**

| Rows | Estimate |
|---|---|
| Poly Haven 4, 5, 7 to 9 at 1k | about 15 MB |
| Kenney 1 to 3 and 10 to 15 (archive sizes not stated) | likely 5 to 50 MB in all |
| Row 6, the stress test | 5.4 MB more for the `.blend`, more for its glTF |

**What actually ships** is only the files the samples use, not whole packs: a handful of models, three texture sets, a sprite sheet and a few sounds.

## 3. Licence evidence (2026-11-19)

| Source | Where | What it says |
|---|---|---|
| Kenney | https://kenney.nl/support | "all game assets on the asset pages are public domain licensed (CC0)"; "Attribution is not required"; "Refrain from using the 'Kenney' logo seen on top of the page separately"; "For full details, see the included license file". The licence file inside each archive is to be kept (section 4) |
| Kenney asset pages | rows 2, 3, 12, 15 today; 1, 10, 11, 13, 14 in MC.1 | Each shows "Creative Commons CC0" linked to the CC0 1.0 deed. No file formats are listed |
| Poly Haven | https://polyhaven.com/license | "Our assets are all licensed as CC0". Any purpose, commercial included; "You can redistribute them", including "in a product you sell"; no credit needed. **Exceptions:** logos, renders, avatars and text are not CC0. The Terms of Service prohibit "Web scraping or data mining without express permission" |
| Poly Haven API | https://polyhaven.com/llms.txt, https://github.com/Poly-Haven/Public-API/blob/master/ToS.md (read after the first draft) | **Settled:** the API is "free to access and use by anyone ... including commercial use, at no charge" (ToS 2.1), and `llms.txt` says the same: "Free for any purpose, including commercial use". The terms require a User-Agent that names the software (ToS 2.4). A "Powered by Poly Haven" credit is needed only when showing live API content, and "The assets themselves never need attribution once they are downloaded". A search listing of the older API page still said commercial use needed a licence; the ToS and `llms.txt` supersede it |
| Kenney robots.txt | https://kenney.nl/robots.txt; the asset pages' `<meta name="robots" content="noai, noimageai">` | **Disallows** GPTBot, ChatGPT-User, ClaudeBot, Claude-User, CCBot and others: `Disallow: /`. Downloads run through a script on the page (`/download-link/<id>`) |
| OpenGameArt robots.txt | https://opengameart.org/robots.txt | **Disallows** Claude-Code, Claude-User, ClaudeBot and many other agents: `Disallow: /` |
| OpenGameArt | listing of row 16 (MC.1) | "License(s): CC0" |
| Quaternius | https://quaternius.com/license.html | Commercial use in a product is allowed and no attribution is needed, **but** "Resell or redistribute the Assets themselves" is prohibited, and so is repackaging them "as a standalone asset, asset pack, stock file, template, or similar product" |

**What this audit did on the Poly Haven API:** 7 `info` and 4 `files` lookups, with a User-Agent naming no person. They were read-only.

**After the terms were settled,** the approved downloads used the API's file URLs:
- with the User-Agent `ForgeRoom-asset-import/1.0`;
- one asset at a time, 20 files in all;
- each file's MD5 checked against the API's.

**Kenney and OpenGameArt were not fetched by the agent at all,** past their `robots.txt` and the pages already read for licences.

## 4. Rejected or excluded

| Candidate | Why |
|---|---|
| **Quaternius** (Ultimate Animated Character Pack and the other animated packs) | **Licence conflict.** It forbids redistributing the assets themselves or as a "template", and ForgeRoom ships its samples as templates (M1.3, M1.4). Would need written permission |
| Poly Haven **trees** (Island Tree 01: 3,729,692 tris; Tree Small 02: 4,652,585) | CC0, but far too heavy for a test scene and with no LOD (M2b.4). Kenney's Nature Kit (row 2) covers trees |
| Poly Haven **HDRIs** (row 17) | CC0, but ForgeRoom reads no HDR or EXR file. Downloading one would be an untested file |
| **Blender demo files** | Not verified: the page refuses automated readers (MC.1 row 13). A person must confirm |
| **Freesound** | A licence per sound (some NC), and downloads need a logged-in account. Discovery only (MC.1 row 15) |
| OpenGameArt **collections** | A list of links, not a licence. Each item must be verified on its own |
| Khronos glTF sample models | Not checked here: licences differ per model (some CC-BY, some trademarked). Could be a later, separate check for animation test fixtures |

## 5. Phases 4 to 6: proposal (to do after approval)

**Layout, following the repository's folders** rather than a new tree:

| Kind | Where |
|---|---|
| Models | `assets/models/thirdparty/<source>-<pack>/...` |
| Characters | `assets/characters/thirdparty/...` |
| Textures | `assets/textures/thirdparty/<source>-<name>/...` |
| Materials (project-made, naming the textures) | `assets/materials/` |
| Sprites (the imported `.sprite`) | `assets/sprites/thirdparty/...` |
| Sounds | `assets/sounds/thirdparty/...` (new: there is no sounds folder yet) |
| Licences | `assets/licenses/<asset id>/` (the archive's own licence file, unchanged) |
| Manifest | `assets/licenses/manifest.json` |

**The manifest:** one entry per shipped file, with:
- id, name, category, source URL, licence, author;
- original file name, SHA-256, retrieval date;
- format, dependencies;
- conversion and tool;
- import status.

This is the shape of M2b.1's manifest. **M2b.1 needs a TD first** (chart: "TD, Legal, Dep"), so I would write that TD for approval.

**Validation on download** (a small tool, tested), checking each archive's entries:
- reject absolute paths, `..`, links, executables and scripts;
- keep only the formats listed in section 1;
- record the SHA-256.

**Integration:**
- **No new browser system.** The Content Browser, thumbnails and drag-and-drop already handle models, textures, sprites and sounds.
- **New:** licence and source shown in Details (M2b.3's "license detail") and searchable by category.

**Test scenes (Phase 6).** Most already exist with project-made content. The plan is to add **opt-in copies** that use the new assets, keeping the originals as fixtures (MC.7's rule):

| Phase 6 item | Existing sample | With downloaded assets |
|---|---|---|
| 2D sprites | `pixel-adventure-2d`, `platformer-tiles-2d` | A copy with Pixel Platformer tiles and hero (row 10) |
| 3D models | `landscape-3d` | Nature Kit trees and rocks (row 2), Building Kit house (row 1) |
| PBR materials | `materials-3d` | Brick, wood and rock `.material` files from rows 7 to 9 |
| Character animation | `humanoid-animations-3d` | A Blocky Character's own clips (row 3), if the archive has glTF |
| Lighting and shadows | every 3D sample | Poly Haven props (rows 4, 5) under the sun and sky |
| Terrain and environment | `terrain-3d`, `action-rpg-valley-3d` | Nature Kit scattered on the valley |
| Audio | `racing-3d` (engine sound), the `.song` samples | Footsteps (row 13), impacts (row 14), the door (row 16) on Audio Sources |
| Save and reload | all, by `SceneSerializer` tests | The new copies are added to the round-trip and sample-play tests |

## 6. Missing engine capabilities found

1. **No HDRI or skybox file:** the sky is procedural.
2. **No textured terrain layers:** the paint layers are colours.
3. **No `.material` on glTF models:** a downloaded model keeps its embedded material (M2.2).
4. **No FBX or OBJ import (M9.2).** Whether this matters depends on the Kenney archives' contents.
5. **No provenance or licence manifest (M2b.1):** nothing in the editor shows where a file came from.
6. **No in-game UI (M6.3):** UI packs can only be sprites.

## 7. Decisions after the first draft

- **Approval (owner, 2026-11-19):**
  - downloads: "All usable rows": rows 1 to 5 and 7 to 16 (not row 6, the stress test, nor row 17, the HDRI);
  - binaries: "Working tree only", uncommitted, with a manifest.
- **Kenney and OpenGameArt are left to a person,** because of their `robots.txt` (section 3). The brief says "Do not scrape sites that prohibit automated downloading", and an agent fetching their files is that.
- **The licence record became code** ([TD-094](TECHNICAL_DECISIONS.md), proposed):
  - `ThirdPartyManifest` reads `assets/licenses/manifest.json` and accepts CC0-1.0 only;
  - `ThirdPartyAssetsTest` guards it;
  - the sample inventory reports a downloaded file's licence and origin from it.

## 8. Phases 3 to 7: results

### 8.1 Downloaded, validated and imported (Poly Haven, CC0)

| Manifest id | Asset | Files | Bytes | Where | Used by |
|---|---|---|---|---|---|
| `polyhaven-rollershutter-door` | Rollershutter Door, by MP | 6: `.gltf`, `.bin`, 4 JPG textures (colour, graffiti colour, GL normal, arm) | 2,342,983 | `models/thirdparty/polyhaven/rollershutter_door/` | `polyhaven-showcase-3d` |
| `polyhaven-stone-fire-pit` | Stone Fire Pit, by Sebastian Platen | 5: `.gltf`, `.bin`, 3 JPG | 2,535,945 | `models/thirdparty/polyhaven/stone_fire_pit/` | `polyhaven-showcase-3d` |
| `polyhaven-red-brick-03` | Red Brick 03, by Rob Tuytel | 3 JPG (diff, nor_gl, arm) | 1,812,664 | `textures/thirdparty/polyhaven/red_brick_03/` | `materials/RedBrick03.material` |
| `polyhaven-wood-floor-deck` | Wood Floor Deck, by Dimitrios Savva | 3 JPG | 2,310,266 | `textures/thirdparty/polyhaven/wood_floor_deck/` | `materials/WoodFloorDeck.material` |
| `polyhaven-rocky-terrain-02` | Rocky Terrain 02, by Amal Kumar | 3 JPG | 2,361,748 | `textures/thirdparty/polyhaven/rocky_terrain_02/` | `materials/RockyTerrain02.material` |

**Validation:**
- Every file's MD5 matched the API's.
- Every path is relative and inside its folder.
- There are no executables, scripts or links.
- Every format is one ForgeRoom reads (glTF, BIN, JPG).
- **Each glTF is self-contained:** every buffer and image it names is present, with no remote or absolute URI. Each has PBR materials and no animation.
- **Units:** the models are in metres (the door frame is 1.08 m wide and 2.4 m tall), so the sample places them with `importScale` 100.

**Integration, reusing what exists:**
- **Materials:** three `.material` files, the project-made materials for the textures. Each takes colour, normal and `arm`; `arm` fills both the metallic-roughness and occlusion slots, with metallic and roughness 1, as glTF multiplies them.
- **New opt-in sample `polyhaven-showcase-3d.json`** (`tools/make_thirdparty_samples.py`): a wood deck floor, a brick wall with the door set into it, a rock block and the fire pit, under a shadow-casting sun. It is in the gallery (desktop) with a preview. No existing sample was changed (MC.7's rule).
- **Editor:** the Content Browser, thumbnails and drag and drop take these files as they do any glTF, texture or material. No second asset system was added.

### 8.2 Approved but not downloaded: a person has to fetch them

Rows 1 to 3 and 10 to 15 (Kenney) and row 16 (OpenGameArt):
1. Download each archive in a browser.
2. Run `python tools/thirdparty_assets.py check-archive <zip>` on it. This refuses unsafe paths, links, executables and scripts, and lists formats ForgeRoom cannot read, such as FBX.
3. The same install step then records the files.

**For Kenney, the archive's own `License.txt` is kept beside the files.** The install step for archives is not built yet: it is the next step.

### 8.3 Tests and commands

| Command | Exit | Result |
|---|---|---|
| `python -m unittest tools/test_thirdparty_assets.py` | 0 | 9 tests: safe paths, archive checks, Poly Haven plans, install and a changed file refused |
| `python -I tools/thirdparty_assets.py fetch-polyhaven <slug> <kind> <empty folder>` (5 times), then `install` | 0 each | 20 files, MD5 checked |
| `./gradlew :engine-core:test --tests "*ThirdPartyManifestTest"` | 0 | 4 |
| `./gradlew :editor-desktop:test --tests "*ThirdPartyAssetsTest" --tests "*SampleInventoryTest.shouldTake*"` | 0 | 3 |
| **Guard mutation checks:** a stray file in a `thirdparty` folder; one changed byte in a recorded texture (each restored, `cmp` identical) | 1, 1 | "files in a thirdparty folder with no licence record"; "... is not the file that was downloaded" |
| `GDXUNITY_UPDATE_AUDIT=1 ./gradlew :editor-desktop:test --tests "*.audit.*" --tests "*WebUxContractTest"` | 1, 1, then 0 | **First run:** two pins tripped as intended. `EditabilityAuditTest`: a material's name must be its file name ("Red Brick 03" became `RedBrick03`). `TemplateRatingsTest`: the showcase is "Partially Editable", because glTF models have no mesh editor yet (R7a). **Then** the tables regenerated, and a run without the variable passes |
| `./gradlew :<module>:cleanTest :<module>:test`, one module at a time | 0 each | `engine-core` 45 → **49**; `engine-desktop` 153; `editor-core` 539 (1 skipped); `editor-desktop` 181 → **184** |
| `node scripts/sync-samples.mjs && npx vitest run` (engine-babylon) | 0 | 510 → **511**: the web player builds the showcase, glTF models and materials included |
| `./gradlew :engine-desktop:run --args="--scene samples/polyhaven-showcase-3d.json --smoke-test=60"` with a screenshot | 0 | "60 of 60 frames, 0 runtime error(s)"; gdx-gltf computed tangents for two primitives |
| `./gradlew :engine-desktop:perfBaseline -PperfScenes=samples/polyhaven-showcase-3d.json,samples/materials-3d.json` | 0 | See 8.5 |

### 8.4 Rendering observations (from the game runtime's screenshot, 1280 by 720)

- **What shows correctly:**
  - the floor shows wood planks;
  - the wall shows brick with relief;
  - both doors of the Rollershutter Door asset show, plain and graffiti. The asset holds two doors side by side;
  - the fire pit's stones show;
  - shadows fall from the sun.
- **Issue found: the bricks run in vertical columns** on the wall's front face, not horizontal courses. The wall is a `BOX` shape, drawn by LibGDX's `ModelBuilder.createBox` on a unit cube, and its side faces map the texture turned 90 degrees. This is an existing engine issue that affects every textured box (the `materials-3d` sample too), not a fault of the asset. **Not fixed here.**
- **Not compared:** the wood looks strongly orange and the rock block greenish. I did not compare them with Poly Haven's own renders, so whether colour space or tone mapping changes them is unchecked.
- **Manually Not Verified:** the sample was not opened in the editor, and nothing was dragged from the Content Browser.

### 8.5 Performance (Windows 11, Intel Iris Xe, `perfBaseline`, medians of 3 runs of 300 frames)

| Scene | Frame p50 | Frame p95 | First frame | Heap peak |
|---|---|---|---|---|
| `polyhaven-showcase-3d` | 4.03 ms | 9.25 ms | **2,033 ms** | 115 MB |
| `materials-3d` (project-made textures) | 4.63 ms | 6.11 ms | 608 ms | 102 MB |

- **Drawing:** the 1k textures and two small models cost no more per frame.
- **Loading:** the first frame takes 1.4 s more, from decoding the JPG textures and computing tangents for the models. That is worth watching once more assets ship.

## 9. Findings for the brief's Phase 7 list

| Item | Result |
|---|---|
| Assets discovered | 18 shortlisted sources and 7 rejected or excluded (sections 2 and 4) |
| Assets downloaded | 5 (Poly Haven), 20 files |
| Successfully imported | 5: two glTF models in a sample, three texture sets as `.material` files |
| Unsupported formats | HDR and EXR (row 17). FBX, OBJ and `.blend`: not imported; the Poly Haven `.blend` sources were not downloaded. Kenney formats are unknown until a person downloads the archives |
| Licensing concerns | Quaternius forbids redistribution and templates (excluded). Kenney and OpenGameArt disallow AI agents in `robots.txt` (left to a person). Poly Haven's site forbids scraping, while its API allows any use (used, with a named User-Agent) |
| Rendering issues | Textured box faces map the texture turned 90 degrees (section 8.4) |
| Performance | Per-frame cost unchanged; first frame 1.4 s slower (section 8.5) |
| Missing engine capabilities | Section 6: HDRI or skybox files, textured terrain layers, `.material` on glTF models, FBX and OBJ import, licence detail in the editor, in-game UI |

## 10. Next steps

1. **The owner, or a person, downloads** the approved Kenney packs and the OpenGameArt door sound in a browser and puts the archives in a folder outside the repository. I then check them, and write and run the archive install step (not built yet). Integration then follows:
   - Nature Kit and Building Kit models in a landscape copy, if the archives carry glTF;
   - Pixel Platformer sheets as `.sprite`;
   - sounds on Audio Sources.
2. **Fix the box texture orientation** (section 8.4) with a test, as a small engine fix.
3. **Review TD-094**, then show the licence and source in Details and the Content Browser (M2b.3).
4. **Decide how the binaries are kept:** plain Git, LFS or a fetch script.

## 11. Files changed

- **New:**
  - `tools/thirdparty_assets.py` and `tools/test_thirdparty_assets.py`;
  - `tools/make_thirdparty_samples.py`;
  - `engine-core` `ThirdPartyManifest` and its test;
  - `editor-desktop` `ThirdPartyAssetsTest`;
  - `assets/licenses/manifest.json`;
  - the 20 downloaded files (section 8.1);
  - three materials;
  - `assets/samples/polyhaven-showcase-3d.json` and its preview.
- **Changed:**
  - `SampleInventory` (licence and origin from the manifest) and `SampleInventoryTest` (+1, and the 3D count);
  - `TemplateRatingsTest` (48 samples, 22 "Partially Editable");
  - `gallery.json`, `assets/samples/README.md`, `docs/migration/sample-migrations.json`;
  - the regenerated tables of the inventory, the template audit and the feature matrix;
  - `TECHNICAL_DECISIONS.md` (TD-094, proposed);
  - the chart, the feature matrix and the index.
- **Git:** no commit, push or other Git operation was performed. HEAD is `0469ceb`. The downloaded binaries are uncommitted, as the owner chose.
