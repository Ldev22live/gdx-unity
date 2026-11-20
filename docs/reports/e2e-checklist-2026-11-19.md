# E2E Checklist for the Packaged Editor, 2026-11-19 (7 pm)

The items today's reports left **Manually Not Verified**. Run them on the packaged editor, not from Gradle.

**The build**
- **Package:** `editor-desktop/build/package/ForgeroomEditor-1.0.0-windows-x64.zip`, rebuilt after UI M4, so it includes the pack import.
- **Check run:** the packaged `ForgeroomEditor.exe` was smoke-tested with `--project <scratch copy> --scene samples/landscape-3d.json --smoke-test`. It exited 0, with 180 frames and 0 errors.

**Before starting:**
- Use a copy of the project (`--project <copy>`). Without one, a first run sets up the sample project in your real Documents folder.
- Saved layouts are kept from older runs. To see the new default (one isometric view, every drawer closed), rename `%APPDATA%\GdxUnity\viewport_layout.json` and `workspace_layout.json` first.

## 1. Layout (UI M3b, M3)

1. **Header:** the logo and "ForgeRoom / <world>". Hover Save, Undo and Redo for tool tips; Undo is greyed with nothing to undo. Play, Pause, Stop, Project, Settings and Create all work.
2. **Tab row:**
   - Terrain, Objects, Buildings and Characters each open their settings in Properties, and the tab turns blue. Pressing the tab again closes it.
   - Assets, Layers, Scripts and AI Assistant each open and close their drawer independently.
3. **Tool rail:** each icon works and shows its tool tip; the active tool is lit.
4. **Properties:**
   - Drag Brush Size and Strength while sculpting, and check the brush follows.
   - With Paint chosen, pick each ground swatch and paint.
   - Fold and unfold World Settings, and change Game Genre and Game Mode there.
   - Customize opens.
5. **Status bar:** the links open their drawers. The right side shows the ground size and turns from "Saved" to "Unsaved changes" after an edit.
6. **Resizing:** drag the edges of Layers, Properties, Assets and Scripts. Restart the editor: the drawers come back as you left them.
7. **Smallest window (1024 × 640):** nothing important is off screen.

## 2. Full-map view (UI M2)

1. **Isometric view:** open `action-rpg-valley-3d` and `landscape-3d`. The whole map is framed.
2. **View controls:**
   - the view drop-down switches Isometric, Perspective and the axis views;
   - `<` and `>` turn the map a quarter round.
3. **Working in it:** pan (middle or right drag), zoom (wheel) and select. Paint ground, and place a tree and a Cottage (Buildings) in the isometric view. Each is one undo.

## 3. Genre profiles (M3b.7)

1. **Platformer Profile 3D:**
   - Play, and cross the stones with WASD and Space.
   - Fall off: you return to the start.
   - Q and E turn the camera.
2. **First-person Range:**
   - Play, walk, step sideways and turn.
   - F or Ctrl knocks crates off the shelf, one shot per press.

## 4. Assets (asset integration)

1. **Poly Haven Showcase:** check the wood, brick and rock materials, the door and the fire pit.
   - Known issue: the bricks show in vertical columns.
2. **Asset placement:** drag the fire pit model and a Poly Haven material from Assets into the world.

## 5. Pack import (UI M4)

1. **Get a pack:** Assets > Import. Press Open page for **Nature Kit**, so its Kenney page opens in your browser, and download the zip.
2. **Import it:** press Import..., choose the zip, and wait for the status bar to report "Imported Nature Kit: N file(s)".
   - Scripts > Console lists what was left out (FBX, OBJ).
   - The Nature Kit row now reads "Imported <date>, N file(s)".
3. **Use it:** Assets > Project > `thirdparty/kenney/nature-kit/`. Drag a GLB tree into the world.
4. **Refusal check:** import a zip that has no licence file. It is refused with the reasons, and nothing is written.

## 6. Poly Haven (UI M5, built after this checklist was first written)

1. **Search:** Assets > **Poly Haven**. Search "rock" among Models. The results list fills; "Powered by Poly Haven" shows under them.
2. **Details:** choose a result. Authors, categories and "Licence: CC0 1.0" show; the resolution box picks **2K** with its size.
3. **Download:** press Download. Progress shows, then the status bar says where it went. Drag the `.gltf` from Assets > Project into the world.
4. **Texture:** switch to Textures, search "brick", download one. A new material appears in `materials/`; drag it onto a shape.
5. **Large file:** choose an 8K texture. Download first reads "Download (<size>)...", then asks "Confirm <size>"; nothing downloads until confirmed. Cancel while downloading leaves nothing behind.
6. **Offline:** turn the network off, restart the editor, open the Poly Haven tab. It shows "Offline, from <date>" with the cached list, and the rest of the editor works.

## 7. ambientCG (UI M6)

1. **Search:** Assets > **ambientCG**. Search "grass". Results fill; "Materials from ambientCG (CC0)" shows under them.
2. **Download:** choose a result; 2K-JPG is selected with its size. Download (it can take a minute). The status bar lists the files left out (Blender, MaterialX and others); the Console names each.
3. **Use it:** a new material appears in `materials/`; drag it onto a shape or the ground.

## Record

For each item: pass or fail, with a screenshot or a note.
