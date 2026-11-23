# The Design Reference Compared with the Editor After UI M3

**Date:** 2026-11-19. **HEAD:** `0469ceb` (nothing committed). **Reference:** [`docs/ForgeRoom Isometric World Editor.png`](ForgeRoom%20Isometric%20World%20Editor.png), supplied by the owner on 2026-11-19. **Editor today:** [ui-m3/drawers-closed-1920.png](ui-m3/drawers-closed-1920.png) and [ui-m3/drawers-open-1920.png](ui-m3/drawers-open-1920.png).

The reference is a design picture, not a screenshot. Its terrain, water, waterfall, trees and blue-roofed houses show the look the world should reach. That look comes from content and rendering (textured terrain layers, water, CC0 models), not from the layout. Both parts are compared below.

## 1. Layout: what matches and what differs

| Area | Reference | Editor after M3 | Gap |
|---|---|---|---|
| Header | Logo, "ForgeRoom / Untitled World"; icon buttons on the right for Save, Play (green) and Settings | Text "Forgeroom", "World / Untitled", then Save, Undo, Redo, Project, Settings, Create, in one scrolling row with the tools | Logo and icon buttons; Play in the header (today it is in the footer with Pause and Stop); Undo, Redo, Project and Create need a home (a menu under Settings, or shortcuts) |
| Drawer tabs | A row of large tabs with icons, below the header: **Terrain, Objects, Buildings, Characters, Assets, Layers, Scripts, AI Assistant**. Terrain is selected (blue) | Terrain, Objects, Buildings and Characters are in the World strip at the bottom of the map. Outliner, Details, AI Assistant, Asset Library and Console are buttons at the right of the top row | **One tab row** for all of them, under the header. **Renames:** Asset Library → Assets, Outliner → Layers, Console → Scripts (Output Log, Profiler, and Compile Scripts). Details becomes the always-there Properties panel, not a tab |
| View preset | "Isometric ▾", a drop-down at the right of the tab row | The pane header's name cycles views, with `<` and `>` turn buttons | A drop-down of views (Isometric, Perspective, Top, Side) at the right of the tab row |
| Tool rail | A vertical rail of icons at the left: Select, Paint, Terrain, Move, Rotate, Scale, Erase | Text buttons in the top row: Select, Move, Rotate, Scale, Frame, Sculpt, Level | **A left icon rail** with the transform tools and the active drawer's tools (paint, sculpt, erase) |
| Map | Large, with rounded corners, between the rail and the Properties panel | The map fills the window between the top row and the footer (M2) | Rounded frame and margins; otherwise met |
| Properties | A right panel always shown. For the Terrain drawer: a tool picker ("Terrain Tool"), **Brush Size** and **Strength** sliders with values, a **Material** grid of eight swatches, and a collapsible **World Settings** | Details shows the selected entity's components. The terrain tools' settings are in the level palette over the map (top left), as +/- buttons | **A contextual Properties panel:** the active drawer's settings (brush sliders, ground swatches, object picker) when a tool is in use, and the selection's components otherwise. World Settings is a collapsible section |
| Bottom | Links "Assets | Layers | Console" on the left; "64 × 64 · Saved ✓" on the right | Footer: Game Genre, Game Mode, Customize, Play, Pause, Stop; the status line below | A thin status bar with the quick links, the map size and the save state. Game Genre, Game Mode and Customize need a new home (World Settings or the header) |
| Theme | Dark navy, rounded cards, blue accent, icons | Dark, flat, text buttons | An icon set (original; no copied icons), rounded panel backgrounds, larger tab buttons |

## 2. World look: what the editor can show today

| In the reference | Today | What it needs |
|---|---|---|
| Painted ground (grass, dirt paths, rock) | Terrain paint layers are flat colours (TD-089) | **Textured terrain layers:** a texture per layer, for example the CC0 Poly Haven or ambientCG ground textures from M5 and M6 (M1 section 6) |
| Lake with shore and a waterfall | Water level and water bodies (M3.4, M3.9), flat and untextured | Water material and a waterfall (a later rendering task) |
| Dense pines and broadleaf trees | Primitive prefab trees; Kenney's Nature Kit is approved but waits for a person to download it | Kenney Nature Kit (glTF if the archive has it); a scatter tool (M3.6) |
| Houses with blue roofs, fences, a well, a banner | The new Cottage and Watchtower, made of shapes | Kenney Building Kit, or Poly Haven models from M5 |
| Mountains and cliffs | Sculpted terrain and cliffs along paths (M3.7) | Rock textures |

## 3. Plan

The reference changes how M4 to M8 look, not what they do. I propose one extra step, M3b, before M4, because the Asset Library (M4) and the provider tabs (M5, M6) are built into the layout it sets:

- **M3b, reference layout** (desktop editor; no engine change):
  1. a header with the logo, the project and world name, and Save, Play and Settings icon buttons;
  2. one drawer tab row with icons: Terrain, Objects, Buildings, Characters, Assets, Layers, Scripts and AI Assistant, plus the view drop-down at its right;
  3. a left tool rail with icons;
  4. a contextual Properties panel on the right, showing brush size and strength as sliders and ground materials as swatches for Terrain, the object list for Objects and Buildings, and Details otherwise, with a World Settings section holding Game Genre, Game Mode and Customize;
  5. a status bar with Assets, Layers and Console links, the map size and the save state;
  6. an original icon set, drawn for ForgeRoom, and rounded panels.

  Existing actions keep their ids, so shortcuts, menus and the agent tools are unchanged. Tested by the drawer catalogue, the action inventory, and screenshots compared with the reference at 1920 and 1024.
- **M4 to M8 as planned,** under TD-094 (approved) and TD-095 (approved: online on use, offline first, high-definition imports up to 4 GB with confirmation above 500 MB).
- **World look** (section 2) is content and rendering. The textured terrain layers are the largest single step toward the reference picture. I suggest them right after M6, once ambientCG and Poly Haven textures can be imported.
