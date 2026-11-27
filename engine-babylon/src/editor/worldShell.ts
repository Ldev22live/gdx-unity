import type { SceneFile } from "../scene/types";

/**
 * The web editor's World shell (roadmap A13): the same composition as the desktop's World layout (TD-107) — a thin header, the
 * map filling the page with overlays, one category bar and one palette row under it, and a footer with Genre, Mode, Customize
 * and Play. This module is the layout's data and arithmetic, without the DOM, so it is tested headless.
 */

/** Which composition the editor page uses: the World shell (default) or the earlier IDE-style page, kept selectable. */
export type LayoutStyle = "world" | "classic";

/** The style named by the page's `?layout=` parameter; anything else (or nothing) is the World shell. */
export function layoutStyle(search: string): LayoutStyle {
  const value = new URLSearchParams(search).get("layout");
  return value !== null && value.trim().toLowerCase() === "classic" ? "classic" : "world";
}

/** What a category does on the web today. */
export type CategoryState =
  /** Its palette works here. */
  | { kind: "works" }
  /** Shown, but edited in the desktop editor for now; the text says what and when. */
  | { kind: "desktop"; note: string };

/** A button of the category bar. */
export interface Category {
  id: "map" | "terrain" | "objects" | "buildings" | "characters" | "players" | "rules";
  label: string;
  state: CategoryState;
  /** For the placement categories: the desktop palette folder whose prefabs it offers. */
  prefabFolder?: string;
}

/** The categories, in the desktop's order (Map, Terrain, Objects, Buildings, Characters, Players, Rules). */
export const CATEGORIES: readonly Category[] = [
  { id: "map", label: "Map", state: { kind: "works" } },
  // Sculpted and painted on the web since A14, with the desktop's brushes (fixtures/contract-v1/terrain-trace)
  { id: "terrain", label: "Terrain", state: { kind: "works" } },
  { id: "objects", label: "Objects", state: { kind: "works" }, prefabFolder: "prefabs/level" },
  { id: "buildings", label: "Buildings", state: { kind: "works" }, prefabFolder: "prefabs/buildings" },
  { id: "characters", label: "Characters", state: { kind: "works" }, prefabFolder: "prefabs/characters" },
  // Edited on the web since A14 (TD-108, TD-109), with the same rules and limits as the desktop
  { id: "players", label: "Players", state: { kind: "works" } },
  { id: "rules", label: "Rules", state: { kind: "works" } },
];

/** Bar heights in CSS pixels, the desktop's values (`WorldShell`): header, category bar, palette row, footer. */
export const HEADER_HEIGHT = 36;
export const CATEGORY_BAR_HEIGHT = 34;
export const PALETTE_ROW_HEIGHT = 96;
export const FOOTER_HEIGHT = 40;
/** The share of the window the map must keep (TD-107; the roadmap's A13 test). */
export const MIN_VIEWPORT_SHARE = 0.7;

/** The share of a `width` × `height` window left to the map with the side panels closed, with or without the palette row. */
export function viewportShare(width: number, height: number, paletteOpen: boolean): number {
  if (!(width > 0) || !(height > 0)) throw new Error(`window size must be positive: ${width} x ${height}`);
  const bars = HEADER_HEIGHT + CATEGORY_BAR_HEIGHT + FOOTER_HEIGHT + (paletteOpen ? PALETTE_ROW_HEIGHT : 0);
  return Math.max(0, height - bars) / height;
}

/** The camera presets of the map's camera chip. */
export type ViewPreset = "top" | "iso" | "perspective";

/** Orbit angles (Babylon `ArcRotateCamera` alpha and beta, radians) for each preset; seen from +Z like the game cameras. */
export const VIEW_ANGLES: Record<ViewPreset, { alpha: number; beta: number }> = {
  // Beta must stay off 0, where the orbit camera's up direction is undefined
  top: { alpha: Math.PI / 2, beta: 0.0001 },
  iso: { alpha: Math.PI / 2 + Math.PI / 4, beta: Math.atan(Math.SQRT2) },
  perspective: { alpha: Math.PI / 2, beta: 1.1 },
};

/** A read-only summary of the scene's scenario (players, objectives, rules) for the Players and Rules categories. */
export function scenarioSummary(file: SceneFile): string {
  const scenario = (file as unknown as Record<string, unknown>)["scenario"];
  if (typeof scenario !== "object" || scenario === null) return "This scene has no players or rules yet.";
  const block = scenario as { version?: unknown; players?: unknown; objectives?: unknown; rules?: unknown };
  const count = (value: unknown): number => (Array.isArray(value) ? value.length : 0);
  const names = Array.isArray(block.players)
    ? block.players.map((player) => (typeof player === "object" && player !== null ? String((player as { name?: unknown }).name ?? "?") : "?"))
    : [];
  const players = count(block.players);
  const parts = [
    players === 0 ? "no players" : `${players} player${players === 1 ? "" : "s"} (${names.join(", ")})`,
    `${count(block.objectives)} objective${count(block.objectives) === 1 ? "" : "s"}`,
    `${count(block.rules)} rule${count(block.rules) === 1 ? "" : "s"}`,
  ];
  const version = typeof block.version === "number" ? block.version : null;
  return `Scenario${version !== null ? ` version ${version}` : ""}: ${parts.join(", ")}.`;
}

/** The desktop's genre names by saved id (`GameGenres.standard()`); an id this list does not know is shown as it is. */
export const GENRE_NAMES: Record<string, string> = {
  "platformer": "Platformer / Side-scroller",
  "action-rpg": "Action RPG",
  "adventure": "Adventure",
  "first-person-shooter": "First-person Shooter",
  "third-person-shooter": "Third-person Shooter",
  "real-time-strategy": "Real-time Strategy",
  "turn-based-strategy": "Turn-based Strategy",
  "racing": "Racing",
  "puzzle": "Puzzle",
  "simulation": "Simulation",
  "sandbox": "Sandbox",
};

/** The Genre and Mode the footer shows, from the scene file ("None" when unset). Genre profiles run in desktop Play only (A14). */
export function genreAndMode(file: SceneFile): { genre: string; mode: string } {
  const record = file as unknown as Record<string, unknown>;
  const id = typeof record["gameGenre"] === "string" ? String(record["gameGenre"]) : "";
  const genre = id === "" ? "None" : GENRE_NAMES[id] ?? id;
  const saved = typeof record["gameMode"] === "string" ? String(record["gameMode"]) : "";
  const mode = saved === "" ? "None" : saved.toUpperCase();
  return { genre, mode };
}
