# Alpha proposals: A02 World shell, A11 shared contract, A13 web World shell

**Date:** 2026-11-25. **Status:** proposals for owner approval; **nothing here is approved or implemented.** They follow from [alpha-a01-ux-audit.md](alpha-a01-ux-audit.md) and [milestone-roadmap.md](../milestone-roadmap.md). Each needs the TDs listed in the audit's section 10.

**Order:**
1. Approve reference A and the gap list (A01 done).
2. Approve TD "shared project contract" (A11 design) **before** any new saved data. This includes the A04 scenario data.
3. A02 (desktop shell) and A13 (web shell) can then proceed in parallel. Neither changes file formats.

## A02 — Desktop World shell to reference A

**Approach:** reuse the existing scene2d.ui shell, drawers, `WorkspaceNavigator`, commands and the level sessions. Change the composition, not the engine.

**Layout (default):**
- **Header, one row:**
  - left: product name and project name;
  - right: New (opens the gallery), Open, Save, Undo, Redo, Settings;
  - a compact "Saved" mark.
  - Compile Scripts, Pause and the "Stopped" label leave the header: scripts compile on Play, and Pause is a key during Play.
- **Map (dominant).** Overlays on it:
  - "World / *scene name*" at top left;
  - the Top, Iso, Perspective chip at top right, mapped to the existing view modes, with the axis views under Settings;
  - a minimap at bottom left (new: a top orthographic render of the terrain bounds into a small frame buffer, with a click-to-centre camera);
  - a selection card at bottom right: Move, Rotate, Duplicate, and Customize's first offer ("Edit 3D Model" or "Edit 2D Asset"), from `CustomizeGateway`.
- **Category bar below the map:** Map, Terrain, Objects, Buildings, Characters, Players, Rules.
  - Map, Terrain, Objects, Buildings and Characters are wired to existing sessions and palettes.
  - **Players and Rules show their honest state:** "Not built yet (A04 and A05)" until those land. No fake controls.
- **Palette row under the categories:** the active category's existing palette, shown as one compact row. Terrain shows brush, size, strength, surface and "More…" (holes, water, roads, rivers, erosion in a pop-over).
- **Footer:** Genre and Mode (moved from World Settings) on the left; **Customize** and **Play** (Play becomes Stop) on the right.
- **Hidden by default, one toggle each (Settings → Panels):** Outliner, Details, Console, Asset Library, Layers, Scripts, AI Assistant, Profiler. The left tool rail becomes keyboard shortcuts and the selection card.

**First task:** trace the actual UI composition and input routing (`EditorApplication`, the drawer host, viewport input) and list each widget's owner. Then change the layout behind a single "World layout" class, so the old layout stays available while it is being replaced. No duplicate command paths.

**Tests (headless where possible):**
- Layout at 1280 × 720 and 1920 × 1080: viewport ≥ 70% with drawers closed (the roadmap's proposal; confirm against reference A, which is about 63%), no overlaps.
- Every action in the audit's feature-preservation matrix reachable.
- Drawers do not steal viewport input.
- Selection and undo kept across category switches.
- The first-run default ignores stale local layout files.

**Human gate:** an unfamiliar tester finds terrain, object placement, Play and Customize without help. Times and steps are recorded.

**Sizing:** M (2 to 6 weeks, one person; a guess).

## A11 — Shared project contract (design and TD; no code until approved)

**Principle:** evolve today's formats; do not replace them. The scene JSON, `.terrain`, `.material`, `.sprite`, `.graph`, `.anim` and glTF are already read by both engines.

**Proposal:**
1. **`project.forgeroom.json` manifest, versioned:**
   - `contractVersion`, `projectId`, `name`;
   - scenes (path, id);
   - assets (path, sha256, size, kind, licence record id);
   - `requiredCapabilities` and `optionalCapabilities`, for example `terrain/1`, `profile:action-rpg/3d`, `graph/2`, `mesh-edit/1`;
   - `revision` (id, base revision, author, time);
   - an `extensions` namespace map.
   - The desktop writes it beside existing projects on save. Projects without it still open (legacy).
2. **No silent loss:**
   - The desktop `SceneSerializer` keeps unknown components as opaque `{type, enabled, data}` records and writes them back. This replaces the current "ignore".
   - Unknown fields inside known components are kept the same way, per component.
   - The web editor already keeps raw JSON; it gets the same guarantee in tests.
   - A save that cannot preserve something refuses, with a list.
3. **Capability negotiation:** on open, each client compares `requiredCapabilities` with what it supports.
   - It opens read-only, or with named limitations, or refuses.
   - It never converts or discards.
4. **Stable ids:** entity ids are already UUIDs. Add stable ids for scenario players, rules and objectives (A04 and A05 data goes into the scene under a versioned `scenario` block, designed here).
5. **Revisions:**
   - Every cloud save carries its base revision.
   - The server rejects a stale base (409), and the client offers "keep mine as a copy", "reload" or "merge per entity" (later).
   - No last-write-wins.
   - Offline desktop edits keep the base revision and reconcile on reconnect.
6. **Safety:**
   - Relative paths only, no `..`;
   - archive limits (size, count, no symlinks);
   - hash checks on assets;
   - mandatory unknown capabilities refused.
7. **Units and axes:** 100 units per metre, +Y up, −Z forward, quaternions. Recorded as the contract; already true in practice.
8. **Boundaries:**
   - The API and AI contracts are written as interfaces (project store, job service, authoring plan) whose UrConnex and UDX implementations wait on the missing contracts (audit §9).
   - The desktop keeps a local-folder store that needs no network.

**Deliverables:**
- the TD;
- the JSON Schema for the manifest and the `scenario` block;
- canonical fixtures: a terrain valley, a house with a derived mesh, a sprite, a prefab, two players, a material, a trigger rule, a profile;
- a capability matrix;
- interface stubs, docs only until approved.

**Tests at A12:**
- round trips desktop → web → desktop and the reverse;
- semantic equality;
- malformed, future-version and unknown-capability files.

## A13 — Web World shell to reference A

**Approach:** reuse `engine-babylon`'s editor (`EditorApp`, `EditorModel` with snapshot undo, the inspector and the sculpt mode) and its player. Re-compose the page to the same hierarchy as A02.
- A thin header.
- The Babylon canvas filling the page, with overlays: context, camera chip (Top, Iso, Perspective; new camera modes on the existing orbit camera), selection card.
- The category bar and one palette row.
- A footer with Genre, Mode, Customize and Play.
- The Outline, Details, Animations, Visual Scripting and Music views become panels opened on demand (Customize, or "Advanced").

**Honest scope for the shell (A13), before A14 adds authoring:**
- **Map, Objects, Buildings, Characters:** placement from a project-managed starter set (the shipped Kenney CC0 GLBs). Undoable through `EditorModel.edit`.
- **Terrain:** shown as "Coming in A14", or the existing sculpt-mesh tool labelled honestly. `.terrain` editing on the web is A14 work.
- **Players and Rules:** "Not built yet".
- **Customize:** shows the desktop hand-off message for mesh, sprite and graph editing ("Edit geometry in urGameForge Desktop"), per the existing web UX spec ([web-ui-ux-spec.md](../web-ui-ux-spec.md)).
- **Play:** the existing player in a runtime copy. Genre profiles stay "Desktop only" until A14 implements a web profile subset.
- **Responsive:** a layout test at 1280 × 720 and 1920 × 1080 and one narrow width; keyboard focus and pointer input on the canvas checked with a browser test, not only screenshots.

**Where it runs:** this repository's `engine-babylon` until the UrConnex Next.js host is confirmed. Embedding into UrConnex (A16) needs the UrConnex contract.

**Sizing:** M (a guess).

## Approvals requested

1. ~~Which reference is authoritative~~ **Answered 2026-11-25:** `docs/Screenshot 2026-11-25 113907.png`.
2. **The A00 checkpoint plan** (baseline report §3) and the storage choice for `asset-sources/` (148 MB).
3. **The AtmosphereComponent web classification** (recommended: known-unsupported now, with a safe-degradation test) and **TeaVM excluded from Alpha**.
4. **Start A11:** write the shared-contract TD and schema. Docs only, no code.
5. **Start A02** after reference A is confirmed (a layout TD first).
6. **Start A13** in `engine-babylon`, or point to the UrConnex web editor's repository if that is the intended host.
7. **Provide the missing contracts:**
   - the UrConnex API (routes, auth);
   - the UDX/UCX AI service identity and endpoint;
   - VPS read-only access, or an inventory;
   - authorization to read the UrConnex repository, if wanted.
8. **The 14 November roadmap file:** the directive names `docs/14-nov-forgeroom-roadmap-revised.md`, which does not exist. Confirm which document was meant.
