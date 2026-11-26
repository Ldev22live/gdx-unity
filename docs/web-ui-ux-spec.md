# Forgeroom web UI: shared UX specification

**Milestone:** M1b.6 in [milestone-chart.md](milestone-chart.md) (roadmap R1b). **Status:** proposed, 2026-11-15; **revised the same day for roadmap Appendix G**, whose split the owner **approved as decision 19 on 2026-11-15**: Desktop is the complete game creation studio, Web a streamlined creator inside UrConnex, the two share compatible project data, and feature parity is not required. The rest of this spec is still a proposal for the owner's review. Written from this repository's desktop editor, not from any web code outside it.
**Governs:** any web UI for Forgeroom, including ForgeRoom Web hosted in UrConnex, and this repository's own web editor (`engine-babylon/editor.html`).
**Source of the design:** roadmap Appendix G (G1 to G4, G6: the platform split, capability labels, safe saves, the simplified web UI and web template labels), Appendix E (World-first UX, Customize and Play), Appendix F (editable templates, a **Desktop** bar), TD-084 (Customize gateway), TD-085 (World layout) and TD-086 (New Game gallery). For the web, Appendix G wins over Appendix E and every older passage that asks for parity. Where this spec and the roadmap disagree, the roadmap wins and the difference is a defect in this spec.

## 1. What this spec is, and the boundary

ForgeRoom Desktop is the complete authoring studio. ForgeRoom Web is a **smaller, simpler** creator and game preview: easy entry, new projects, light World editing, browser Play and, where approved, sharing (Appendix G1). **The web does not copy the desktop.** It shares the desktop's World-first principles and words, so a person who knows one feels at home in the other, but not its screens or its workspaces. Visual scripting authoring, mesh geometry editing, full animation authoring and audio or music editing are **Desktop-only** unless a later phase is approved (chart decision 24).

This spec defines that shared experience and what the web must never do to a desktop project. It does **not** define how the web UI is built. A web UI inside UrConnex uses **UrConnex's own design system** (G4); the owner's reference screenshot guides interaction and simplicity, not pixels.

**The boundary (roadmap R1b, G1):**

- **The desktop depends on nothing on the web.** The desktop editor and game runtime (Java 21, LibGDX) use no web framework (no Spring Boot, Next.js, servlet container or web player module), need no server and no UrConnex login. `WebUxContractTest` checks this on every test run (section 10).
- **The web depends on files, not on desktop code.** It shares the project's file formats and this spec, nothing more. No desktop Java engine dependency goes into UrConnex, and the repositories stay separate.
- **A web save never loses desktop work (G3).** The web reads and writes **only the fields it supports**. It keeps every other field, component, reference and source file exactly as it found them: graphs, mesh sources, animation clips, songs and audio projects. It never deletes, flattens, rewrites or silently replaces them, and a browser preview file is never a stand-in for an editable original. If something cannot be kept safely, the web **blocks the save and says why**; it never strips it. Saves check the version they started from and resolve a conflict instead of overwriting newer desktop changes. The file contract itself is roadmap WEB2.
- **The shared words are generated, not copied.** Section 11 is written by a test from the desktop's own code. When the desktop changes a word, the test fails until this file is regenerated, so the web side sees the change. The web uses those words for the things it shares, and its own words for what it has and the desktop does not.
- **This spec changes no UrConnex code.** The UrConnex web application is built in its own repository and workflow; this file is the desktop side's statement of what the shared experience and files must be.
- **The full desktop command list** is the inventory in [world-first-ux-audit.md](reports/world-first-ux-audit.md) (generated from the desktop's action registry, with each command's id, label, shortcut and home). A web UI that offers the same command names it by its id there.

### Project compatibility levels

Every project, and every template, has one level for the web, worked out from what it contains and what the web has been checked to do. It is shown wherever the project is opened on the web, with the reason.

| Level | Meaning | What the web may do |
|---|---|---|
| **Web Editable** | Everything the web advertises for this project works on it: World editing, placement, transforms, supported materials and characters, Play | Edit those fields and save, keeping every other field and file |
| **Web Preview Only** | It plays in the browser, but the web cannot safely edit it (for example a component or schema version it does not know) | Play and Stop; no save |
| **Desktop Required** | It does not play in the browser (an unsupported genre and mode profile, Java or Kotlin scripts, a feature with no browser runtime) | Show what it is and offer "Open in ForgeRoom Desktop" |
| **Not yet checked on the web** | No browser check has been made | As Desktop Required until checked |

Desktop-only data (graphs, mesh sources, animation clips, songs, audio projects, terrain sources, prefabs, scripts) never lowers a project to Desktop Required on its own: the web keeps it untouched and edits the rest. A level only rises with evidence from a browser check.

## 2. Layout (Appendix E1, G4)

Both editions start with New Game, Open Game and Samples, then open the **World** with a large viewport. From top to bottom:

| Region | Desktop | Web |
|---|---|---|
| **Header** (thin) | Product name, the context indicator **World / <scene name>** with a dot for unsaved changes; **Save**, **Undo**, **Redo**, the **Project** and **Settings** dropdowns, **Create** (section 11) | The same idea: the context indicator, Save, Undo and Redo, and a minimal project menu. No workspace tabs |
| **World view** (named Viewport until owner decision 11, 2026-11-15) | The interactive scene from the scene renderer, with the tools (Select, Move, Rotate, Scale, Frame) in a slim row | A simplified browser viewport, never a static picture standing in for the scene. Select, place and transform |
| **World strip** | 2D: Tiles, Sprites, Objects, Characters. 2.5D and 3D: Terrain, Objects, Buildings, Characters. Unbuilt sections say why (section 11) | **Terrain, Objects, Buildings, Characters**, and for 2D, tile or sprite placement once WEB4 adds it. A section is offered only if it works on the web, or shown as **Planned** with its reason. The desktop's planned messages point at desktop tools, so the web writes its own |
| **Footer** | **Game Genre**, **Game Mode** (2D, 2.5D, 3D: the scene's own), then **Customize** and **Play** side by side, with Pause and Stop while playing. Game Genre is a drop-down of None and every registered genre, each marked "(planned)" or "(not offered)" in the scene's mode, then a short line after Game Mode saying whether Play uses the pair (M3b.4) | The same order, with Genre and Mode as separate selectors. Only **browser-verified** genre and mode profiles can be played; the others are shown with their label (section 6), never hidden or faked |
| **Panels** | Outliner, Details, Content Browser: collapsible, closed by default in World, remembered per person | An asset browser and a properties panel for the selection, kept small |

On a narrow window both keep Customize and Play visible; the genre and mode selectors may scroll or shrink, Customize and Play may not. The desktop's minimum is 1024 by 640 and every desktop region fits there. A web UI should support the same minimum; below it, it may stack its panels over the viewport but keeps the header actions, the context indicator, Customize and Play reachable without a menu.

## 3. Customize (Appendix E2, TD-084, G4)

Customize sits **immediately beside Play** in both editions, and in both it opens a short chooser that starts with **Edit Selected** entries for the selected object. What follows differs:

- **Desktop:** then every workspace (World, 3D Modeling, 2D Studio, Animation, Audio & Music, Logic, Assets; section 11), so a workspace can be entered with nothing selected. Edit Selected offers what the selection's files allow (*Edit Sprite hero.sprite*, *Animate Hero (Walk.anim)*, *Edit Music theme.song*, *Edit Logic Player.graph*, *Edit Rock in 3D*, *Edit Rock in 3D (sculpt a copy)*). A kind of file with no editor yet is **listed and marked unavailable, with the reason**. Every workspace has a prominent **Back to World**.
- **Web:** then a **short, task-related list** of what the web can really do, for example Terrain, Place Objects, Buildings, Characters and basic Materials, each only once it works (WEB3, WEB4). **No desktop workspace is listed and no empty tab is shown** for an editor the web does not have. Edit Selected offers the web's own actions; for a house that is its transform and material, plus **"Edit geometry in ForgeRoom Desktop"**: a labelled handoff that says where the edit happens, never a control that looks like a mesh editor. A graph, an animation clip or a song on the selection gets the same kind of Desktop-only handoff. The web's tools open beside the World, so returning is closing them.
- **Web workspaces.** The web has one workspace, the **World**, with the short Customize list above as tools inside it: Terrain (raise, lower and paint, once validated), Place Objects and Buildings (choose, place, move, rotate, scale), Characters (choose a character prefab, set the spawn and basic settings) and Materials (choose or adjust a supported material). Assets is a browser of the project's files. 3D Modeling, 2D Studio, Animation, Audio & Music and Logic are Desktop workspaces and are not on the web.
- **Both:** switching never closes, saves, discards or deselects anything. Project, selection, unsaved changes and the World camera are kept, across every supported action. An operation that needs confirming asks first.

## 4. Play and Stop (Appendix E3, G2)

- **Play** runs an isolated copy of the scene. Nothing the game does while playing changes the edited scene or its undo history. **Stop** returns to the same World, selection and undo history. Changes made while playing are discarded; there is no "apply changes" without a separate approval.
- If the scene cannot start, Play says why in one short, actionable message and stays stopped. This covers a script that fails to compile, a scene that fails on its first frame and, on the web, a genre and mode profile the browser does not support.
- **On the web, Play is offered only for verified browser profiles** (WEB5). The browser plays desktop-made graphs only through an interpreter tested to be safe and compatible; otherwise it shows a compatibility warning. It **never runs Java, Kotlin or untrusted scripts**, in the browser or on the UrConnex server. The web never promises native export; publishing a browser game needs its own approval.
- The same controls and rules apply in 2D, 2.5D and 3D wherever the profile runs.

## 5. New Game: the template gallery (TD-086, M1.4, M1.7, G6)

- **New Game** lists the empty 2D, 2.5D and 3D starts, then the templates from `assets/templates/gallery.json`, with an All / 2D / 2.5D / 3D filter. The web shows only what it can open.
- The selected entry shows its preview (`assets/templates/previews/`, real frames from the game runtime), its genre, its dimension (read from the scene's own `gameMode`), its size and its label with the reasons for it.
- The actions are **Play Sample** (opens a copy) and **Use as Template** (**Create Project** for an empty start), which makes a separate project. **The shipped template is never changed** by either.
- **Two kinds of label, both computed, never typed:**
  - **Desktop label (Appendix F, M1.7):** Fully Customizable, Not yet rated (checks pending), Technical Demo / Partially Editable or INCOMPLETE (section 11), from the editability matrix on the template's scene file each time it is shown.
  - **Web label (G6):** **Web Editable** (every web operation the template advertises works, not every desktop source), **Web Preview Only** (it plays in the browser but the web cannot edit it) or **Desktop Required**, from checks run in a browser. No template is called "Fully Editable on Web" unless that is separately proven. Until a template has been checked in a browser it has **no** web label and says "not yet checked on the web"; today that is every template, because no web check has been made (the desktop gallery's web line says only whether the scripts were ported to the web player, section 11).
- A web UI must show the same label as the desktop for the same file, for example by reading a matrix the build exports. It must not keep a hand-written list.
- **Which templates the web offers:** those whose project level (section 1) is Web Editable or Web Preview Only, each with its level. A Desktop Required template may be listed with "Open in ForgeRoom Desktop", never with a Play or Use as Template button that would fail. The first browser-playable genre and dimension is the owner's choice (chart decision 22).

## 6. Honest status (Appendix E1, E5.9, G2)

- Show only actions that work. Never present a button that does nothing.
- On the web, every capability carries one evidence-backed label: **Editable on Web**, **Preview/Play on Web**, **Desktop-only**, **Planned** or **Unsupported in this project**. The roadmap's capability matrix (G2) is the starting list; each entry moves to "Editable on Web" or "Preview/Play on Web" only when it is implemented and validated in a browser.
- When something is planned, say what it needs (the World strip's reasons, Customize's unavailable entries, "(planned)" on a Game Genre choice, with the reason in the status bar).
- Errors say what happened and what to do, in plain words, where the person is looking: in the status line and in the place the action was taken (the gallery shows its own errors). A blocked web save says which desktop data it would have lost.
- Never present a mock-up, screenshot or video as the working UI.

## 7. Keyboard and accessibility (Appendix E5.7)

- Where the web offers a desktop command, it uses the desktop's shortcut, as listed in the action inventory. For example: Save Ctrl+S, Undo Ctrl+Z, Redo Ctrl+Y and Ctrl+Shift+Z, Duplicate Ctrl+D, Delete Del, and the tools Q, W, E, R. A browser shortcut that cannot be overridden is replaced by one documented next to it.
- Every control has an accessible name. Focus order follows the layout's reading order (header, strip, viewport, footer). Focus is visible. Text meets WCAG AA contrast in both the light and dark themes. Panels can be resized, and the UI scales with the browser's zoom.
- Viewport focus: keys typed into a text field never move the camera or run tools.

## 8. Performance (Appendix E5.8)

Opening the World never waits on optional downloads, all the samples or instrument loading. Tools and their data load when first opened.

On the web, the browser is the limit, so:

- Assets stream as the World needs them; the first view shows before every model and texture has arrived, with a visible loading state.
- A project or template states its download size before it is opened, and large optional packs (high-resolution textures, instruments) are never fetched by opening the World.
- Scenes have a budget agreed in WEB0 (draw calls, triangles, texture memory, and a target frame rate on a stated reference device). A scene over budget opens with a warning, and is never silently simplified on save.
- WebGL is the baseline; a missing feature (for example WebGPU) falls back or says so, never fails silently.
- Long work (importing, saving a large project) shows progress and can be cancelled, and never freezes the page.

## 9. This repository's web editor today, against this spec

`engine-babylon/editor.html` predates Appendices E and G. It is a starting point for WEB0, not the UrConnex UI, which is built in UrConnex. This list is what a web UI built from it would need to change. It is not a plan approved for implementation.

| Spec | Web editor today | Gap |
|---|---|---|
| Thin header, no workspace tabs | Header with Viewport, Animations, Visual Scripting and Music tabs, plus New, Open, Samples, Save, Undo, Redo, Add, Duplicate, Delete, the tools, Frame and Play | File commands move into a project menu; tools move to the view's row. **The Animations, Visual Scripting and Music tabs are working editors the web no longer needs** (G2: Desktop-only). Whether to keep them, hide them or retire them is the owner's (chart decision 24); under this spec they would not be header tabs |
| Context indicator | Scene title and an unsaved mark at the right end | Move to the left as **World / <scene>** |
| World strip | None | Missing |
| Footer with Genre, Mode, Customize and Play | A status line; Play in the header | Missing |
| Short web Customize list, Edit Selected, Desktop handoffs | None | Missing |
| Panels small, for the selection | Outline and Details always open | Make them collapsible |
| New Game gallery with desktop and web labels | A Samples dropdown | Missing; no template has a web check yet |
| Safe saves (section 1) | Saves the whole scene it loaded | To audit in WEB0 and WEB2: whether fields and components it does not know survive its save |
| Play isolation and Stop | Play runs a copy of the scene in place (`structuredClone` of the model) and the button becomes Stop; no Pause | Isolation matches; Pause, the failure message and the per-profile check of section 4 to add |

## 10. How conformance is checked

- **Automated, desktop:** `WebUxContractTest` regenerates section 11 from the desktop code and fails when it is stale. It also checks that no module the desktop editor is built from depends on a web framework or the web player module, and that no desktop source imports one.
- **Automated, web:** a web UI should load section 11 (or the code it is generated from, exported as data) in its own tests, to show the same words, sections and labels for what it shares.
- **Preservation (G6, WEB2):** a desktop project with a rich graph, editable mesh source, animation source and a multitrack song is changed on the web in one supported way (one object's transform), saved and reopened on the desktop; the untouched data is compared by hash and structure. Any silent loss fails. Repeated with a concurrent edit and an unsupported schema version, each of which must be refused with a reason.
- **By a person:** the Appendix E5 checklist (discoverability, low clutter, context, consistency, short path, persistence, accessibility, performance, honest status) on a real browser at 1024 by 640 and at a phone width, recorded with screenshots. Until that happens the web UI is **Manually Not Verified**.

## 11. Shared vocabulary (generated)

Written by `WebUxContractTest` from the desktop's code; refresh with `GDXUNITY_UPDATE_AUDIT=1`. Do not edit by hand. These are the **desktop's** words. The web uses them for what it shares (the header, the strip's section names, the footer, the desktop labels); the Customize list, the planned messages and the web labels are its own (sections 3, 5 and 6).

<!-- ux-contract:begin -->
### Header controls

| Control | Label | Shortcut | Action id |
|---|---|---|---|
| Save | Save Scene | Ctrl+S | `file.save-scene` |
| Undo | Undo | Ctrl+Z | `edit.undo` |
| Redo | Redo | Ctrl+Y | `edit.redo` |

Dropdowns, left to right: Project, Settings, Create. Their items are in the action inventory.

### Customize destinations on Desktop (in order)

The web does not list these; it has its own short list (section 3).

- World
- 3D Modeling
- 2D Studio: Sprites
- 2D Studio: Tiles
- Animation
- Audio & Music
- Logic
- Assets

Workspace names: World, 3D Modeling, 2D Studio, Animation, Audio & Music, Logic, Assets.

### World strip

The section names are shared; the messages point at desktop tools, so the web writes its own (section 2).

| Game mode | Sections, left to right | Planned sections and the message shown |
|---|---|---|
| 2D | Tiles, Sprites, Objects, Characters | **Objects**: Objects for 2D scenes are planned. Drag a sprite or a prefab from the Content Browser into the World for now.<br>**Characters**: Characters for 2D scenes are planned: they need a 2D character controller. Drag a sprite from the Content Browser into the World for now. |
| 2.5D | Terrain, Objects, Buildings, Characters | none |
| 3D | Terrain, Objects, Buildings, Characters | none |

### Footer

- Game Mode choices: 2D, 2.5D, 3D
- Game Genre choices, as the desktop marks them in each game mode (the desktop registry; the web plays none of them yet):

| Game mode | Choices |
|---|---|
| 2D | None, Platformer / Side-scroller, Action RPG (planned), Adventure (planned), First-person Shooter (not offered), Third-person Shooter (not offered), Real-time Strategy (planned), Turn-based Strategy (planned), Racing (planned), Puzzle (planned), Simulation (planned), Sandbox (planned) |
| 2.5D | None, Platformer / Side-scroller, Action RPG, Adventure (planned), First-person Shooter (not offered), Third-person Shooter (not offered), Real-time Strategy (planned), Turn-based Strategy (planned), Racing (planned), Puzzle (planned), Simulation (planned), Sandbox (planned) |
| 3D | None, Platformer / Side-scroller, Action RPG, Adventure (planned), First-person Shooter, Third-person Shooter, Real-time Strategy (planned), Turn-based Strategy (planned), Racing (planned), Puzzle (planned), Simulation (planned), Sandbox (planned) |

### Desktop template labels (Appendix F5), from the most to the least editable

The web labels (Web Editable, Web Preview Only, Desktop Required) are separate (section 5).

| Label | Meaning |
|---|---|
| Fully Customizable | every part it uses can be edited, and every check has been made |
| Not yet rated (checks pending) | every part it uses has an editor, but editing, undo, Play, export, licenses and a person's checks are not yet verified |
| Technical Demo / Partially Editable | some parts it uses cannot be edited yet |
| INCOMPLETE | it holds flattened content that cannot be edited and has no editable replacement in the template |

Not yet checked for any template: a user's edit succeeds in the editor; undo and redo of that edit; Play and Stop of the edited sample; export of the sample and launch of the export; a license manifest for the sample's assets; desktop visual and listening validation by a person.

### Desktop gallery status lines

The web lines say whether a sample's scripts were ported to the web player; they are not web labels.

- "Desktop: opens, plays and exports (checked by test)"
- "Web player: scripts ported (headless tests, not run in a browser)"
- "Web player: not checked"
<!-- ux-contract:end -->
