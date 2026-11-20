# E2E Test Plan: 19 November 2026, 19:00

**What is tested:** the packaged ForgeRoom editor, built on 19 November from the working tree. HEAD is `0469ceb` plus uncommitted work, which runs up to UI M4.
**Platform:** Windows 11.
**Who:** the owner, at the keyboard. These are the checks the reports left **Manually Not Verified**.
**Related:**
- [milestone-chart.md](milestone-chart.md), the UI track and the M3b.7 rows;
- the short checklist [reports/e2e-checklist-2026-11-19.md](reports/e2e-checklist-2026-11-19.md);
- the reports linked in each section.

---

## 1. Scope

| Area | Milestones | Report |
|---|---|---|
| A. Layout of the design reference | UI M3b, UI M3 | [ui-m3b-reference-layout.md](reports/ui-m3b-reference-layout.md), [ui-m3-tool-drawers.md](reports/ui-m3-tool-drawers.md) |
| B. Isometric full-map view | UI M2 | [ui-m2-isometric-full-map-view.md](reports/ui-m2-isometric-full-map-view.md) |
| C. Map tools in the Properties panel | UI M3b, UI M3 (Buildings) | as A |
| D. Genre profiles in Play | M3b.7 slices 2 and 3 | [m3b-7-2-platformer-3d.md](reports/m3b-7-2-platformer-3d.md), [m3b-7-3-first-person-shooter-3d.md](reports/m3b-7-3-first-person-shooter-3d.md) |
| E. Downloaded CC0 assets | Asset integration | [asset-integration-report.md](asset-integration-report.md) |
| F. Pack import | UI M4 | [ui-m4-local-asset-library.md](reports/ui-m4-local-asset-library.md) |
| G. Save, reopen, stability | all of the above | |

**Out of scope:**
- Poly Haven and ambientCG search (UI M5 and M6, not built);
- mesh editing (R7a);
- music and sound editing;
- the web player;
- Linux and macOS.

---

## 2. Setup (about 10 minutes)

1. **Unzip the package.** It is `editor-desktop\build\package\ForgeroomEditor-1.0.0-windows-x64.zip`. Unzip it to a folder of its own, for example `C:\ForgeRoomTest\`.
2. **Use a copy of the project.** Copy the repository's `assets` folder to `C:\ForgeRoomTest\project\`. Without `--project`, a first run sets up the sample project in your real Documents folder.
3. **Start from a fresh layout.**
   - In `%APPDATA%\GdxUnity\`, rename `viewport_layout.json` and `workspace_layout.json` (for example to `.bak`).
   - Your current saved layout is a single X-axis view, which hides the new isometric default.
   - Put the two files back after the test if you want your old layout.
4. **Start the editor:**
   ```
   C:\ForgeRoomTest\ForgeroomEditor\ForgeroomEditor.exe --project C:\ForgeRoomTest\project
   ```
5. **For section F:** have a Kenney **Nature Kit** zip downloaded beforehand (step F1 says how), plus any small zip with no licence file in it.
6. **Record:**
   - for each case, **Pass**, **Fail** or **Blocked** in §5's table;
   - for every Fail, a screenshot and one line on what you saw.

---

## 3. Known issues (do not fail these)

| Id | Issue | Where |
|---|---|---|
| K1 | On textured boxes the texture is turned 90 degrees, so bricks stand in vertical columns | Poly Haven Showcase, materials-3d |
| K2 | The view drop-down shows no ▾ arrow | Tab row |
| K3 | The Paint tool's ground swatches sit to the right of their label | Properties |
| K4 | The terrain Tool row is long and scrolls sideways in a narrow Properties panel | Properties |
| K5 | The Inspector keeps its own "DETAILS" title under "Properties" | Properties |
| K6 | The logo is the window icon, not the reference's cube | Header |
| K7 | The world's look in the reference (painted ground, water, waterfall, dense forest) is not built; it is content and rendering | Map |
| K8 | Turning the isometric view has no keys, only the `<` and `>` header buttons | Map |
| K9 | Moving and strafing start one frame after a key change | Play |

---

## 4. Test cases

**Expected** is what passes. Unless a case says otherwise, start each one from the editor open on a sample through **Project > New Game** (the template gallery).

### A. Layout (UI M3b, UI M3)

| Id | Steps | Expected |
|---|---|---|
| A1 | Open **Action RPG Valley** (gallery) | The header shows the logo, "ForgeRoom / …", and on the right Create, the Save, Undo and Redo icons, Compile Scripts, Play, Pause, Stop, Project and Settings. Below it, the tab row: Terrain, Objects, Buildings, Characters, Assets, Layers, Scripts, AI Assistant, with "Isometric" at the right. A tool rail is on the left, Properties on the right with World Settings at its bottom, and the status bar at the bottom |
| A2 | Hover each header icon and each tool-rail icon | Each shows a tool tip with its name, and its shortcut where it has one |
| A3 | With nothing to undo, look at Undo. Move an entity, then look again | Undo is greyed at first, then becomes active; Ctrl+Z undoes the move |
| A4 | Click **Layers**, **Assets**, **Scripts** and **AI Assistant** in turn, then each again | Each opens and closes its own drawer only; its tab turns blue while open. Assets and Scripts share the bottom, side by side, when both are open |
| A5 | With drawers open, drag the edges of Layers, Properties and the bottom drawers | They resize; the map takes the rest |
| A6 | Use the status bar links Assets, Layers, Scripts and Properties | They open and close the same drawers. Properties cycles "auto", "on" and "off"; at "off" the right column closes |
| A7 | Look at the status bar's right end. Move an entity | It reads "129 × 129 · Saved" in green, then "Unsaved changes" in amber |
| A8 | Leave some drawers open, close the editor, start it again | The same drawers are open, at the same sizes |
| A9 | In World Settings, change Game Genre and Game Mode, fold and unfold World Settings, press Customize | The changes take; the status line under Game Genre says whether Play uses the pair; Customize opens its menu |
| A10 | Resize the window to about 1024 × 640 | Header, tabs, rail, Properties and status bar still fit; nothing essential is off screen (long rows may scroll) |
| A11 | Open a 2D sample (**2D Platformer**) | The first tabs read Tiles, Sprites, Objects and Characters; the view drop-down is hidden; the 2D view is unchanged |

### B. Isometric full-map view (UI M2)

| Id | Steps | Expected |
|---|---|---|
| B1 | Open **Action RPG Valley**, then **Landscape Valley** | Each opens in the isometric view with the whole map in view, mountains included for the landscape |
| B2 | Press `<` and `>` in the view's header (bottom left) | The map turns a quarter round each time, around the same point |
| B3 | Middle-drag or right-drag; scroll the wheel | The view pans; the wheel zooms about the pointer |
| B4 | Click an entity, then drag the move gizmo | It is selected and moves; Ctrl+Z puts it back |
| B5 | Use the view drop-down: Perspective, Y axis, then Isometric | The view changes each time; back on Isometric the map is framed again |
| B6 | Press Play, then Stop | After Stop, the isometric view is where you left it, not reframed |

### C. Map tools in Properties (UI M3b, UI M3)

| Id | Steps | Expected |
|---|---|---|
| C1 | Open **Action RPG Valley**; click the **Terrain** tab | The tab turns blue and the rail's Level icon is lit. Properties shows the terrain tools: Ground, Water, Heightmap, Sky, Tool, Brush Size and Strength sliders with values |
| C2 | Drag Brush Size and Strength; sculpt (Raise) on the valley | The values change (size on a log scale, 40 to 4000; strength 10 to 100%); the brush follows; each stroke is one undo |
| C3 | Choose the Paint tool; click each ground swatch; paint | The chosen swatch is outlined and shows its name as a tool tip; the ground paints in that colour with soft edges |
| C4 | Click **Terrain** again | It closes; Properties shows the selection's details again |
| C5 | Click **Objects**; place a Pine Tree; Erase it | It is placed where clicked, slightly turned and sized; Erase removes it; each is one undo |
| C6 | Click **Buildings**; place a Cottage and a Watchtower; Erase one | Each stands square and at its own size, and is selected after placing; Erase removes it; one undo each |
| C7 | Click **Characters**; place a Player and a Spawn Point; try to place a second Player | Both are placed; the second Player is refused with a message naming the first |
| C8 | Do C5 and C6 in the isometric view | Placement lands where clicked in that view too |

### D. Genre profiles in Play (M3b.7)

| Id | Steps | Expected |
|---|---|---|
| D1 | Open **Platformer Profile 3D**; press Play | The camera is behind and above the Jumper; the status says "Playing as Platformer / Side-scroller + 3D." |
| D2 | Run with WASD; jump with Space; turn the camera with Q and E | Running follows the camera's facing; one jump per press; the camera turns |
| D3 | Cross the four stones to the goal (the flag) | It can be done with the keys alone |
| D4 | Walk off the edge on purpose | The Jumper falls and reappears on the start platform, facing the way it started |
| D5 | Stop | The scene is as before Play; the undo history is kept |
| D6 | Open **First-person Range**; press Play | The view is from the Shooter's eyes, level, with a "+" in the middle |
| D7 | W and S, A and D, Q and E, Space | Walking, stepping sideways without turning, turning, jumping |
| D8 | Aim at the middle crate; press F once; hold F | One shot per press knocks the crate off the shelf; holding F fires once |
| D9 | Turn slightly right; press the left Ctrl key | The right-hand crate is knocked off; the left one stays |
| D10 | Stop | The crates are back on the shelf |

### E. Downloaded CC0 assets (asset integration)

| Id | Steps | Expected |
|---|---|---|
| E1 | Open **Poly Haven Showcase** | Wood deck floor, brick wall, rock block, the two roller doors (plain and graffiti) and the stone fire pit, under sun shadows (bricks per K1) |
| E2 | Assets > Project: open `models/thirdparty/polyhaven/stone_fire_pit/`; drag the `.gltf` into the world | A second fire pit is placed where dropped, at the right size; one undo |
| E3 | Drag `materials/RockyTerrain02.material` onto a shape | The shape takes the rock material; one undo |
| E4 | Save, close and reopen the scene | Everything placed is still there, with its materials |

### F. Pack import (UI M4)

| Id | Steps | Expected |
|---|---|---|
| F1 | Assets > **Import**; press **Open page** for Nature Kit | The Kenney Nature Kit page opens in your browser. Download the zip from there |
| F2 | Press **Import...** for Nature Kit; choose the zip | The editor stays responsive while it imports. The status bar then reads "Imported Nature Kit: N file(s) into thirdparty/kenney/nature-kit; left out M file(s)…" |
| F3 | Open Scripts > Console | One warning line per left-out file (FBX, OBJ and so on); nothing was imported without being reported |
| F4 | Look at the Nature Kit row | It reads "Imported 2026-11-19, N file(s)" |
| F5 | Assets > Project > `thirdparty/kenney/nature-kit/`; find a GLB tree; drag it into the world | A tree is placed; one undo |
| F6 | Import the same zip again | It succeeds; the row and file count are the same; no duplicate files |
| F7 | Press Import... for another pack and choose a zip with no licence file | An error dialog lists the reasons ("no licence file"); the status bar says it was not imported; nothing new appears in Assets |
| F8 | (Optional) Open `licenses/manifest.json` in the test project | A `kenney-nature-kit` entry with source, CC0-1.0, the date, and every file with its SHA-256 |

### H. Poly Haven (UI M5; optional, needs the network)

| Id | Steps | Expected |
|---|---|---|
| H1 | Assets > **Poly Haven**; Models; search "rock" | Results fill; "Powered by Poly Haven" under them; nothing was requested before the tab opened |
| H2 | Choose a result | Name, authors, categories, "Licence: CC0 1.0 (public domain)"; the resolution box shows 2K with its size |
| H3 | Download; then drag the `.gltf` from Assets > Project into the world | Progress, then "Downloaded ... into models/thirdparty/polyhaven/..."; the model is placed at its real size (glTF metres become scene units) |
| H4 | Textures; search "brick"; Download | A `materials/<Name>.material` is made; dropped on a shape it shows the bricks |
| H5 | Choose an 8K texture; press Download once | The button asks "Confirm <size>"; nothing downloads until pressed again; Cancel during download leaves nothing in Assets |
| H6 | Network off, restart, open the Poly Haven tab | "Offline, from <date>" with the cached results; the rest of the editor works |

### I. ambientCG (UI M6; optional, needs the network)

| Id | Steps | Expected |
|---|---|---|
| I1 | Assets > **ambientCG**; search "grass" | Results fill; "Materials from ambientCG (CC0)" under them |
| I2 | Choose a result; Download (2K-JPG) | Progress, then "Downloaded ... into textures/thirdparty/ambientcg/..." and the files left out (.blend, .mtlx, .tres, .usdc) |
| I3 | Drag the new `materials/<Name>.material` onto a shape | The shape shows the material, matte (not metallic) |

### J. Previews and placement (UI M7; optional, needs the network)

| Id | Steps | Expected |
|---|---|---|
| J1 | Poly Haven or ambientCG tab; choose a result | Its preview image shows left of its details; choosing it again offline still shows it |
| J2 | Open **Action RPG Valley**; Poly Haven; a model already downloaded (or download one); press **Place in world** | "In the project: Place in world" is beside Download; the model stands on the valley at the centre of the view, selected, at its real size; Ctrl+Z removes it in one step |
| J3 | Select a box shape; ambientCG or Poly Haven texture already downloaded; press **Apply to selection** | The shape takes the material; Ctrl+Z puts the old one back |
| J4 | Save the scene, close, reopen | The placed model and the applied material are still there |

### G. Save, reopen and stability

| Id | Steps | Expected |
|---|---|---|
| G1 | After A to F: Save the scene, close the editor, start it again, reopen it | Everything is as saved |
| G2 | Throughout the session | No crash, no frozen window, and no error lines in the Console you cannot explain |
| G3 | Check that the shipped originals in the repository's `assets/` are untouched (the test used a copy) | `git status` in the repository shows no new changes made by the session |

---

## 5. Results

| Id | Result (Pass / Fail / Blocked) | Notes / screenshot |
|---|---|---|
| A1 | | |
| A2 | | |
| A3 | | |
| A4 | | |
| A5 | | |
| A6 | | |
| A7 | | |
| A8 | | |
| A9 | | |
| A10 | | |
| A11 | | |
| B1 | | |
| B2 | | |
| B3 | | |
| B4 | | |
| B5 | | |
| B6 | | |
| C1 | | |
| C2 | | |
| C3 | | |
| C4 | | |
| C5 | | |
| C6 | | |
| C7 | | |
| C8 | | |
| D1 | | |
| D2 | | |
| D3 | | |
| D4 | | |
| D5 | | |
| D6 | | |
| D7 | | |
| D8 | | |
| D9 | | |
| D10 | | |
| E1 | | |
| E2 | | |
| E3 | | |
| E4 | | |
| F1 | | |
| F2 | | |
| F3 | | |
| F4 | | |
| F5 | | |
| F6 | | |
| F7 | | |
| F8 | | |
| G1 | | |
| G2 | | |
| G3 | | |
| H1 | | |
| H2 | | |
| H3 | | |
| H4 | | |
| H5 | | |
| H6 | | |
| I1 | | |
| I2 | | |
| I3 | | |
| J1 | | |
| J2 | | |
| J3 | | |
| J4 | | |

**Exit criteria:** every case Pass, or a Fail with a screenshot and note that becomes a fix item. A **Pass** turns the matching report's "Manually Not Verified" into an observed result; a **Fail** reopens that milestone.

**After the run:** send the filled table (and screenshots) back; I will record the results in the milestone chart and the reports and fix the failures.
