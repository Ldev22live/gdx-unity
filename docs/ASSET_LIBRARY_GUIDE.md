# World Editor and Asset Library Guide

How to use ForgeRoom's full-map World editor, its tool drawers and the Asset Library (the brief "Full-Map UI, Tool Drawers and Open Asset APIs", UI M1 to M8; TD-094, TD-095). It describes the desktop editor as built on 2026-11-20.

## 1. The World screen

The map fills the window. Around it:

| Area | What it holds |
|---|---|
| **Header** (top) | The logo and "ForgeRoom / <world>", Create, Save, Undo, Redo, Compile Scripts, Play, Pause, Stop, Project and Settings |
| **Tab row** | The drawers: Terrain, Objects, Buildings and Characters (map tools), and Assets, Layers, Scripts and AI Assistant (panels). The view menu (Isometric, Perspective, axis views) is at the right |
| **Tool rail** (left) | Select, Move, Rotate, Scale, Frame, Sculpt, Level and the other view tools; hover for the name and shortcut |
| **Properties** (right) | The open map drawer's settings, or the selection's details. **World Settings** at the bottom holds Game Genre, Game Mode and Customize |
| **Status bar** (bottom) | Links that open drawers, the last message, the ground size and "Saved" or "Unsaved changes" |

In a 2D project the map tabs read Tiles, Sprites, Objects and Characters, and the view menu is hidden.

### The isometric full-map view

A new install opens 2.5D and 3D scenes in the **Isometric** view, framed to show the whole world. It is an editor camera only, so nothing in the scene moves.

| To | Do |
|---|---|
| Pan | Middle or right drag |
| Zoom | Mouse wheel, about the pointer |
| Turn a quarter round | `<` and `>` at the view's bottom left |
| Select | Click |
| Paint, sculpt and place | As in any other view |
| Choose another view | The view menu; choosing Isometric again frames the world |

### Drawers

- **Opening:** each tab opens and closes its own drawer, and an open tab is blue. Map drawers show their tools in Properties; Assets and Scripts share the bottom; Layers is on the left.
- **Resizing:** drag a drawer's edge.
- **Remembered:** open drawers and their sizes come back the next time the editor starts.

## 2. The Asset Library

Open **Assets**. The drawer has four tabs:

| Tab | What it is |
|---|---|
| **Project** | The project's own files. Drag one into the view to place it under the mouse; drop a material on a shape to apply it |
| **Import** | The curated Kenney packs. Press **Open page**, download the zip in your browser, then **Import...** and choose it |
| **Poly Haven** | Search Poly Haven's CC0 **Models** and **Textures**; download them into the project |
| **ambientCG** | Search ambientCG's CC0 **materials**; download them into the project |

### Searching and downloading (Poly Haven, ambientCG)

1. Open the tab. The provider's catalogue loads the first time in a session.
2. Type words and press Enter or **Search**. Every word must match the name, a tag or a category; the most downloaded come first.
3. Choose a result. Its **preview**, authors, categories, **licence** (CC0 1.0) and where it will go are shown. **Open page** opens the asset's page in your browser.
4. Choose a **resolution**: each shows its total size, and 2K is chosen first.
5. Press **Download**. Progress is shown, and **Cancel** stops it.
   - **Over 500 MB:** the button first shows the size and asks you to **Confirm** it.
   - **Over 4 GB:** refused.

Where the files go:

| Asset | Files | Material |
|---|---|---|
| Model | `models/thirdparty/<provider>/<id>/` | None (it keeps its own) |
| Texture or material | `textures/thirdparty/<provider>/<id>/` | `materials/<Name>.material`, which uses the maps |

### Putting downloads into the world

Once an asset is in the project, from this session or an earlier one, the row beside Download offers:

- **Place in world** (a model): it stands on the ground at the centre of the view, at its real size, selected. Ctrl+Z removes it in one step. With no ground, or the view centre beyond the ground's edge, it goes at the view centre and the status bar says so.
- **Apply to selection** (a material): select a shape first. Ctrl+Z puts the old material back.

Dragging the file from **Project** still works too, and places under the mouse.

### Importing a Kenney pack

1. On **Import**, press **Open page** for the pack and download its zip in your browser.
2. Press **Import...** and choose the zip.
3. ForgeRoom checks the whole archive first. It refuses it, and writes nothing, if it holds:
   - an unsafe path;
   - an executable or script;
   - no CC0 licence file.
4. Files of a format ForgeRoom reads (glTF, GLB, PNG, JPEG, WAV, OGG) are copied to `thirdparty/kenney/<pack>/`.
5. Everything else (FBX, OBJ and so on) is left out and listed in the Console.

## 3. Licences and the manifest

Every downloaded or imported file is recorded in `licenses/manifest.json` with:
- its source page and licence (CC0-1.0 only), and where that licence is stated;
- the authors and the date;
- how it was retrieved: the provider's MD5 checked for Poly Haven, or none given for ambientCG;
- each file's size and SHA-256.

Downloading the same asset again replaces only its own earlier files, and keeps your changes to its material. The project's tests check that every recorded file matches its record.

## 4. Online and offline

**When it goes online:**
- The editor goes online only when you open a provider tab, search or download. Nothing happens at start-up.
- Requests carry the User-Agent `ForgeRoom/<version>` and nothing personal.
- Only HTTPS to the provider's own hosts is allowed.
- Network work never runs on the drawing thread.

**Offline:**
- Catalogues and previews are cached under the settings folder: `%APPDATA%\GdxUnity\cache\asset-library` on Windows.
- A tab shows its cached catalogue as "Offline, from <date>", or says nothing is cached yet.
- Everything already in the project keeps working.

## 5. What is not supported yet

**Providers:**
- ambientCG's 3D models (OBJ and FBX) and HDRIs, and Poly Haven's HDRIs: ForgeRoom does not read those formats.
- ambientCG metalness and opacity maps are not used. Metal materials come in non-metal, and leaves come in opaque.

**Library screens:**
- Previews show for the chosen asset only, not in the result list.
- Place in world uses the view centre, not the mouse.
- There is no resuming of an interrupted download.

## 6. Testing aids (for automated checks)

These environment variables are listed in [AGENTS.md](../AGENTS.md) §5.

| Variable | What it does |
|---|---|
| `GDXUNITY_EDITOR_DRAWER=Assets:Poly Haven` (or `Assets:ambientCG`, `Assets:Import`, `Terrain:Paint`) | Opens that drawer and tab |
| `GDXUNITY_EDITOR_LIBRARY_SEARCH=[textures:\|ambientcg:]<words>[!][+]` | Searches the provider and selects the first result. `!` downloads it; `+` places or applies it once it is in the project |
| `APPDATA=<empty folder>` | Starts like a fresh install |
