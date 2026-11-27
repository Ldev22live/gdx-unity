import { byId, h } from "./dom";
import type { EditorApp } from "./EditorApp";
import { entriesIn, parsePrefab, placePrefab, type PaletteEntry } from "./prefabPalette";
import { newId } from "./presets";
import type { BrushType as BrushTypeName } from "../terrain/terrainStroke";
import { applyProfileFix } from "./profileFixes";
import type { ProfileProblem } from "../runtime/PlayProfiles";
import { MAP_SIZES, type MapSize } from "./terrainEditing";
import * as scenarioEdit from "./scenarioEditing";
import { ownerChoices, renderPlayers, renderRules } from "./scenarioPanels";
import { CATEGORIES, genreAndMode, type Category, type ViewPreset } from "./worldShell";
import { resolveProfile } from "../runtime/PlayProfiles";

/** What the Customize menu says about editing a model's geometry or a sprite, which the web does not do (no fake editors). */
const DESKTOP_HANDOFF = "3D model geometry, 2D sprites and terrain are edited in the desktop editor (Customize → Edit 3D Model or "
  + "Edit 2D Asset). Changes made there show here after reopening.";

/**
 * Re-composes the editor page into the World shell (roadmap A13, the same composition as the desktop's TD-107 World layout): a thin
 * header, the map with its overlays (context, camera chip, selection card), the category bar and one palette row, and a footer with
 * Genre, Mode, Customize and Play. The Outline and Details panels are hidden and reachable from Panels; the Animations, Visual
 * Scripting and Music views open from Customize. Every button runs the editor's existing command, so undo works as before.
 */
export class WorldLayout {
  private readonly categoryButtons = new Map<Category["id"], HTMLButtonElement>();
  private readonly palette = h("div", { id: "world-palette", class: "world-palette" });
  private readonly footerPlay = h("button", { id: "world-play", class: "primary", title: "Play the scene" }, "▶ Play") as HTMLButtonElement;
  private readonly genreLabel = h("span", { class: "world-genre" });
  /** The safe fixes the last refused Play offered (TD-110), as buttons beside Play. */
  private readonly fixes = h("span", { class: "world-fixes" });
  private readonly selectionCard = h("div", { id: "world-selection", class: "world-card", hidden: "" });
  private readonly selectionName = h("div", { class: "name" });
  private readonly context = h("div", { id: "world-context", class: "world-chip" });
  private readonly customizeMenu = h("div", { id: "world-customize-menu", class: "world-menu", hidden: "" });
  private open: Category["id"] | null = null;
  /** The scenario player that owns what is placed next (TD-108); null for Unowned, the default. */
  private placementOwner: string | null = null;
  /** What the Players or Rules row was last drawn from, so it is redrawn only when the scenario or the entities change. */
  private drawnScenario = "";
  private readonly ownerSelect = h("select", { title: "Who owns the selected objects" }) as HTMLSelectElement;
  private readonly ownerRow = h("div", { class: "row owner" });
  /** The header's Add, Frame and Samples controls, which the Map palette shows (captured once: the palette detaches them). */
  private readonly mapControls: HTMLElement[];
  private index: PaletteEntry[] | null = null;

  constructor(private readonly app: EditorApp) {
    document.body.classList.add("layout-world");
    this.mapControls = [byId("add"), byId("frame"), byId("samples")];
    this.buildHeader();
    this.buildOverlays();
    this.buildBottom();
    app.onUpdate(() => this.refresh());
    // Play's genre-profile messages and refusals go to the status line; a refusal's safe fixes become buttons (TD-110)
    app.player.onProfileMessage = (message, isProblem) => app.showStatus(message, isProblem);
    app.player.onProfileProblems = (problems) => this.showFixes(problems);
    app.model.addListener(() => this.refresh());
    this.refresh();
  }

  /** The category whose palette is open, or null. */
  get openCategory(): Category["id"] | null {
    return this.open;
  }

  // ---- Header: Panels (Outline, Details), hidden by default ----------------------------------------------------------------

  private buildHeader(): void {
    const panels = h("button", { id: "world-panels", title: "Show or hide the Outline and Details panels" }, "Panels ▾");
    const menu = h("div", { id: "world-panels-menu", class: "world-menu", hidden: "" },
      this.menuItem("Outline", () => document.body.classList.toggle("show-outline")),
      this.menuItem("Details", () => document.body.classList.toggle("show-details")));
    panels.addEventListener("click", () => (menu.hidden = !menu.hidden));
    const bar = byId("bar");
    bar.insertBefore(panels, byId("play"));
    bar.append(menu);
  }

  private menuItem(label: string, run: () => void): HTMLElement {
    const item = h("button", { class: "world-menu-item" }, label);
    item.addEventListener("click", () => {
      run();
      (item.parentElement as HTMLElement).hidden = true;
    });
    return item;
  }

  // ---- Map overlays: context, camera chip, selection card -----------------------------------------------------------------

  private buildOverlays(): void {
    const stage = byId("stage");
    const camera = h("div", { id: "world-camera", class: "world-chip" });
    for (const [preset, label] of [["top", "Top"], ["iso", "Iso"], ["perspective", "Perspective"]] as [ViewPreset, string][]) {
      const button = h("button", { title: `${label} view` }, label);
      button.addEventListener("click", () => this.app.setViewPreset(preset));
      camera.append(button);
    }
    const actions = h("div", { class: "row" });
    for (const [label, run] of [
      ["Move", () => this.app.setTool("move")],
      ["Rotate", () => this.app.setTool("rotate")],
      ["Duplicate", () => this.app.duplicateSelected()],
    ] as [string, () => void][]) {
      const button = h("button", {}, label);
      button.addEventListener("click", run);
      actions.append(button);
    }
    const customize = h("button", { class: "wide", title: DESKTOP_HANDOFF }, "Customize…");
    customize.addEventListener("click", () => this.app.showStatus(DESKTOP_HANDOFF));
    this.ownerRow.append(h("span", { class: "muted" }, "Owner"), this.ownerSelect);
    this.ownerSelect.addEventListener("change", () => {
      const id = this.app.model.selection;
      if (id === null) return;
      const result = scenarioEdit.assignOwner(this.app.model, [id], this.ownerSelect.value === "" ? null : this.ownerSelect.value);
      if (!result.ok) this.app.showStatus(result.reason, true);
      this.refresh();
    });
    this.selectionCard.append(this.selectionName, this.ownerRow, actions, customize);
    stage.append(this.context, camera, this.selectionCard);
  }

  // ---- Category bar, palette row, footer ----------------------------------------------------------------------------------

  private buildBottom(): void {
    const bar = h("nav", { id: "world-categories", class: "world-categories" });
    for (const category of CATEGORIES) {
      const button = h("button", { class: "flat", "data-category": category.id }, category.label) as HTMLButtonElement;
      button.addEventListener("click", () => this.toggle(category));
      this.categoryButtons.set(category.id, button);
      bar.append(button);
    }
    const customize = h("button", { id: "world-customize", title: "Open another workspace" }, "Customize ▴");
    customize.addEventListener("click", () => (this.customizeMenu.hidden = !this.customizeMenu.hidden));
    this.customizeMenu.append(
      this.menuItem("World (the map)", () => byId("tab-viewport").click()),
      this.menuItem("Animations", () => byId("tab-animations").click()),
      this.menuItem("Visual Scripting", () => byId("tab-scripting").click()),
      this.menuItem("Music", () => byId("tab-music").click()),
      h("div", { class: "world-menu-note" }, DESKTOP_HANDOFF));
    this.footerPlay.addEventListener("click", () => this.app.togglePlay());
    const footer = h("footer", { id: "world-footer", class: "world-footer" },
      this.genreLabel, h("span", { class: "spacer" }), this.fixes, customize, this.customizeMenu, this.footerPlay);
    const bottom = h("div", { id: "world-bottom" }, bar, this.palette, footer);
    this.palette.hidden = true;
    document.body.insertBefore(bottom, byId("status"));
  }

  /** Opens a category's palette row, or closes it when it is already open. */
  toggle(category: Category): void {
    this.app.disarmPlacement();
    this.open = this.open === category.id ? null : category.id;
    // The Terrain palette in hand: the left button sculpts and paints the ground (roadmap A14)
    this.app.terrain.setEnabled(this.open === "terrain");
    this.renderPalette();
    this.refresh();
  }

  private renderPalette(): void {
    this.palette.replaceChildren();
    const category = CATEGORIES.find((c) => c.id === this.open);
    this.palette.hidden = category === undefined;
    if (!category) return;
    if (category.id === "map") {
      // The existing Add and Frame controls move here; their own listeners keep working
      this.palette.append(h("span", { class: "label" }, "Scene:"), ...this.mapControls);
      return;
    }
    if (category.state.kind === "desktop") {
      this.palette.append(h("div", { class: "note" }, category.state.note));
      return;
    }
    if (category.id === "terrain") {
      this.palette.append(...this.terrainPalette());
      return;
    }
    if (category.id === "players") {
      this.palette.append(...renderPlayers(this.app, () => this.placementOwner, (id) => (this.placementOwner = id)));
      this.drawnScenario = this.scenarioKey();
      return;
    }
    if (category.id === "rules") {
      this.palette.append(...renderRules(this.app, () => this.app.viewCentre()));
      this.drawnScenario = this.scenarioKey();
      return;
    }
    if (category.prefabFolder) void this.renderPrefabs(category.prefabFolder);
  }

  /** The Terrain row: the brush tools, radius, strength, the surface to paint, and Save Terrain. */
  private terrainPalette(): HTMLElement[] {
    const terrain = this.app.terrain;
    const hasGround = this.app.model.file.entities.some((e) => e.components.some((c) => c.type === "Terrain" && c.enabled));
    if (!hasGround) {
      // New Level, as on the desktop: a flat ground of the chosen size and surface (TerrainAssets.MapSize, TD-083)
      const size = h("select") as HTMLSelectElement;
      for (const [key, cells] of Object.entries(MAP_SIZES)) size.append(h("option", { value: key }, `${key.charAt(0)}${key.slice(1).toLowerCase()}: ${cells} x ${cells} cells`));
      size.value = "STANDARD";
      const base = h("select") as HTMLSelectElement;
      for (const [code, label] of [[1, "Grass"], [2, "Sand"], [3, "Snow"], [4, "Gravel"], [5, "Rock"], [6, "Dirt"], [0, "Auto"]] as [number, string][]) {
        base.append(h("option", { value: String(code) }, label));
      }
      const create = h("button", { class: "primary" }, "New Ground");
      create.addEventListener("click", () => {
        const made = terrain.createGround(size.value as MapSize, Number(base.value));
        if ("reason" in made) this.app.showStatus(made.reason, true);
        else {
          this.app.showStatus(`New level on ${made.path}: sculpt and paint it, then Save Terrain`);
          // The new ground is far wider than the empty scene's view: frame it once it is drawn
          void this.app.player.terrainsReady.then(() => this.app.frameAll());
        }
        this.renderPalette();
      });
      return [h("div", { class: "scenario-card" }, h("span", { class: "muted" }, "This scene has no ground yet"),
        h("div", { class: "scenario-row" }, size, base), h("div", { class: "scenario-row" }, create))];
    }
    const tools = h("div", { class: "scenario-card" }, h("span", { class: "muted" }, "Brush (Ctrl inverts)"));
    const row = h("div", { class: "scenario-row" });
    const types: [BrushTypeName, string][] = [["RAISE", "Raise"], ["LOWER", "Lower"], ["SMOOTH", "Smooth"], ["FLATTEN", "Flatten"],
      ["NOISE", "Noise"], ["PAINT", "Paint"], ["HOLE", "Hole"], ["ERODE", "Erode"], ["RAMP", "Ramp"]];
    for (const [type, label] of types) {
      const button = h("button", { class: terrain.brush.type === type ? "active" : "" }, label);
      button.addEventListener("click", () => {
        terrain.setType(type);
        this.renderPalette();
      });
      row.append(button);
    }
    tools.append(row);
    const slider = (label: string, min: number, max: number, step: number, value: number, set: (v: number) => void) => {
      const input = h("input", { type: "range", min: String(min), max: String(max), step: String(step), value: String(value) }) as HTMLInputElement;
      const shown = h("span", {}, String(value));
      input.addEventListener("input", () => {
        set(Number(input.value));
        shown.textContent = input.value;
      });
      return h("div", { class: "scenario-row" }, h("span", { class: "muted" }, label), input, shown);
    };
    const settings = h("div", { class: "scenario-card" },
      slider("Size", 20, 3000, 10, terrain.brush.radius, (v) => (terrain.brush.radius = v)),
      slider("Strength", 0.05, 1, 0.05, terrain.brush.strength, (v) => (terrain.brush.strength = v)));
    const surfaces: [number, string][] = [[1, "Grass"], [2, "Sand"], [3, "Snow"], [4, "Gravel"], [5, "Rock"], [6, "Dirt"], [0, "Auto (wipe paint)"]];
    const surface = h("select") as HTMLSelectElement;
    for (const [code, label] of surfaces) surface.append(h("option", { value: String(code) }, label));
    surface.value = String(terrain.brush.surface);
    surface.addEventListener("change", () => (terrain.brush.surface = Number(surface.value)));
    settings.append(h("div", { class: "scenario-row" }, h("span", { class: "muted" }, "Paint"), surface));
    const save = h("button", { title: "Save each edited .terrain file (the scene's own Save keeps the scene)" }, "Save Terrain…");
    save.addEventListener("click", () => void this.saveTerrains());
    const files = h("div", { class: "scenario-card" }, h("span", { class: "muted" }, terrain.unsavedPaths.length === 0 ? "Ground saved"
      : `Unsaved: ${terrain.unsavedPaths.join(", ")}`), save);
    return [tools, settings, files];
  }

  /** Shows the refused Play's safe fixes as buttons; each applies one undo step and clears the list. */
  private showFixes(problems: ProfileProblem[]): void {
    this.fixes.replaceChildren();
    for (const problem of problems) {
      const fix = problem.fix;
      if (!fix) continue;
      const button = h("button", { title: problem.message }, fix.label);
      button.addEventListener("click", () => {
        const refused = applyProfileFix(this.app.model, fix);
        if (refused) this.app.showStatus(refused, true);
        else this.app.showStatus(`${fix.label}: done. Press Play again.`);
        this.fixes.replaceChildren();
      });
      this.fixes.append(button);
    }
  }

  /** Saves every edited terrain file: to the file the browser allows, otherwise a download named like the original. */
  private async saveTerrains(): Promise<void> {
    const paths = this.app.terrain.unsavedPaths;
    if (paths.length === 0) {
      this.app.showStatus("No terrain edits to save");
      return;
    }
    for (const path of paths) {
      const text = this.app.terrain.saveTerrain(path);
      const name = path.split("/").pop() ?? "ground.terrain";
      const picker = (window as unknown as { showSaveFilePicker?: (o: object) => Promise<FileSystemFileHandle> }).showSaveFilePicker;
      try {
        if (picker) {
          const handle = await picker({ suggestedName: name, types: [{ description: "Forgeroom terrain", accept: { "application/json": [".terrain"] } }] });
          const writable = await handle.createWritable();
          await writable.write(text);
          await writable.close();
        } else {
          const link = h("a", { download: name }) as HTMLAnchorElement;
          link.href = URL.createObjectURL(new Blob([text], { type: "application/json" }));
          link.click();
          URL.revokeObjectURL(link.href);
        }
        this.app.showStatus(`Saved ${path}: put it in the project's ${path.slice(0, path.lastIndexOf("/") + 1) || "terrains/"} folder`);
      } catch (error) {
        if (error instanceof DOMException && error.name === "AbortError") return;
        this.app.showStatus(`Cannot save ${path}: ${String(error)}`, true);
      }
    }
    this.renderPalette();
  }

  private async renderPrefabs(folder: string): Promise<void> {
    const showing = this.open;
    try {
      this.index ??= await (await fetch("prefabs/index.json")).json() as PaletteEntry[];
    } catch (error) {
      this.app.showStatus(`Cannot read the palette (prefabs/index.json): ${String(error)}`, true);
      return;
    }
    if (this.open !== showing) return;
    const entries = entriesIn(this.index, folder);
    if (entries.length === 0) this.palette.append(h("div", { class: "note" }, `No prefabs in ${folder}.`));
    for (const entry of entries) {
      const tile = h("button", { class: "tile", title: `Place ${entry.name}: click it, then click the map (Esc stops)` },
        entry.thumbnail ? h("img", { src: entry.thumbnail, alt: "" }) : h("span", { class: "noimage" }, "□"),
        h("span", {}, entry.name));
      tile.addEventListener("click", () => void this.arm(entry, tile));
      this.palette.append(tile);
    }
  }

  private async arm(entry: PaletteEntry, tile: HTMLElement): Promise<void> {
    let text: string;
    try {
      const response = await fetch(entry.path);
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      text = await response.text();
    } catch (error) {
      this.app.showStatus(`Cannot read ${entry.path}: ${String(error)}`, true);
      return;
    }
    let prefab;
    try {
      prefab = parsePrefab(text, entry.path);
    } catch (error) {
      this.app.showStatus(String(error instanceof Error ? error.message : error), true);
      return;
    }
    for (const other of this.palette.querySelectorAll(".tile.armed")) other.classList.remove("armed");
    tile.classList.add("armed");
    this.app.armPlacement((point) => {
      const owner = this.placementOwner;
      if (owner !== null && !scenarioEdit.playersOf(this.app.model.file).some((p) => p.id === owner)) {
        // Never placed silently unowned: the chosen owner is gone (TD-108)
        this.app.showStatus("The owner chosen for placing is not a player of this scene any more: choose another in Players", true);
        return;
      }
      placePrefab(this.app.model, prefab, entry.path, point, newId, owner);
    });
    this.app.showStatus(`Click the map to place ${entry.name}; Esc stops`);
  }

  // ---- State ---------------------------------------------------------------------------------------------------------------

  /** The scenario block and the entities' names and components, the inputs of the Players and Rules rows. */
  private scenarioKey(): string {
    const file = this.app.model.file as unknown as Record<string, unknown>;
    return JSON.stringify(file["scenario"] ?? null) + this.app.model.file.entities.map((e) => e.id + e.name + e.components.length).join();
  }

  /** The selection card's Owner list: shown when the scene has editable players. */
  private refreshOwner(entity: { id: string; components: { type: string; data: Record<string, unknown> }[] } | null): void {
    const file = this.app.model.file;
    const editable = entity !== null && scenarioEdit.scenarioState(file).readOnlyReason === null && scenarioEdit.playersOf(file).length > 0;
    this.ownerRow.hidden = !editable;
    if (!editable) return;
    const current = String(entity!.components.find((c) => c.type === "OwnerComponent")?.data["player"] ?? "");
    const choices = ownerChoices(file);
    if (!choices.some(([id]) => id === current)) choices.push([current, `Unknown player (${current.slice(0, 8)})`]);
    const key = JSON.stringify(choices) + current;
    if (this.ownerSelect.dataset["key"] === key) return;
    this.ownerSelect.dataset["key"] = key;
    this.ownerSelect.replaceChildren(...choices.map(([id, label]) => h("option", { value: id }, label)));
    this.ownerSelect.value = current;
  }

  private refresh(): void {
    const file = this.app.model.file;
    this.context.textContent = `World / ${file.name}`;
    const { genre, mode } = genreAndMode(file);
    const play = resolveProfile(file);
    // What web Play will do with this pair (TD-110): its profile, no profile (desktop-only pairs), or a refusal
    const note = play.outcome === "PROFILE" ? "plays here with its profile"
      : play.outcome === "DESKTOP_ONLY" ? "its profile plays on the desktop only"
        : play.outcome === "REFUSED" ? play.message : "no genre profile";
    this.genreLabel.textContent = `Genre: ${genre} · Mode: ${mode} · ${note}`;
    this.genreLabel.title = play.outcome === "NO_GENRE" ? "" : "message" in play ? play.message : "";
    this.footerPlay.textContent = this.app.isPlaying ? "■ Stop" : "▶ Play";
    for (const [id, button] of this.categoryButtons) button.classList.toggle("active", id === this.open);
    if (!this.app.isPlacing) for (const tile of this.palette.querySelectorAll(".tile.armed")) tile.classList.remove("armed");
    const id = this.app.model.selection;
    const entity = id === null ? undefined : file.entities.find((e) => e.id === id);
    this.selectionCard.hidden = entity === undefined || this.app.isPlaying;
    if (entity) this.selectionName.textContent = `Selected: ${entity.name}`;
    if ((this.open === "players" || this.open === "rules") && this.scenarioKey() !== this.drawnScenario) this.renderPalette();
    this.refreshOwner(entity ?? null);
  }
}
