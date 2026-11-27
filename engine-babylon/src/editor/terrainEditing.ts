import { Vector3, type ArcRotateCamera } from "@babylonjs/core";
import type { BuiltScene } from "../runtime/SceneBuilder";
import type { ScenePlayer } from "../runtime/ScenePlayer";
import { parseTerrainFile, type TerrainData } from "../terrain/terrain";
import { applyDelta, DEFAULT_RADIUS, DEFAULT_STRENGTH, terrainToJson, TerrainStroke, type Brush, type BrushType } from "../terrain/terrainStroke";
import type { EditorModel } from "./EditorModel";
import { newId } from "./presets";

/** The desktop's new-level sizes (`TerrainAssets.MapSize`): cells per side. */
export const MAP_SIZES = { SMALL: 64, STANDARD: 128, LARGE: 256 } as const;
export type MapSize = keyof typeof MAP_SIZES;
/** Distance between grid points of a new ground (the desktop's `TerrainAssets.NEW_CELL_SIZE`). */
export const NEW_CELL_SIZE = 40;
/** Where new grounds are kept and their name (the desktop's `TerrainAssets.FOLDER` and New Level's base name). */
export const TERRAIN_FOLDER = "terrains";
export const LEVEL_NAME = "Level";
/** The entity a new level's ground is on (the desktop's `createLevel`). */
export const GROUND_ENTITY_NAME = "Level Ground";

/**
 * Sculpting and painting `.terrain` ground in the web editor (roadmap A14): the World shell's Terrain palette turns it on. While on,
 * the left button strokes the brush over the ground (Ctrl inverts) and the camera orbits with the other buttons. Each stroke is one
 * step of the scene's undo history ({@link EditorModel.recordExternal}). The edited terrain is drawn and played from memory until it
 * is saved with {@link saveTerrain}, as the desktop's terrain files are saved beside the scene.
 */
export class TerrainEditing {
  /** The brush the palette edits; a stroke copies it when it starts. */
  readonly brush: Brush = { type: "RAISE", radius: DEFAULT_RADIUS, strength: DEFAULT_STRENGTH, inverted: false, surface: 1 };
  private enabled = false;
  private built: BuiltScene | null = null;
  private stroke: TerrainStroke | null = null;
  private strokePath: string | null = null;
  private strokeEntity: string | null = null;
  /** Terrains whose edits are not saved yet, by path. */
  private readonly unsaved = new Set<string>();
  onStatus: (message: string, isError?: boolean) => void = () => {};

  constructor(private readonly model: EditorModel, private readonly player: ScenePlayer) {}

  get isEnabled(): boolean {
    return this.enabled;
  }

  /** The terrain paths with unsaved edits. */
  get unsavedPaths(): string[] {
    return [...this.unsaved];
  }

  setEnabled(enabled: boolean): void {
    this.enabled = enabled;
    if (!enabled && this.stroke?.isActive) this.endStroke();
    this.setOrbitButtons(enabled);
  }

  setType(type: BrushType): void {
    this.brush.type = type;
  }

  attach(built: BuiltScene): void {
    this.built = built;
    built.scene.onPointerObservable.add((info) => this.onPointer(info.type, info.event as PointerEvent));
    this.setOrbitButtons(this.enabled);
  }

  detach(): void {
    if (this.stroke?.isActive) this.endStroke();
    this.built = null;
  }

  /** The editable data of the terrain at {@code path}: the edited copy when there is one, else read from its file once. */
  async terrainData(path: string): Promise<TerrainData> {
    const edited = this.player.terrainOverrides.get(path);
    if (edited) return edited;
    const response = await fetch(path);
    if (!response.ok) throw new Error(`Cannot read ${path}: HTTP ${response.status}`);
    const data = parseTerrainFile(await response.text());
    this.player.terrainOverrides.set(path, data);
    return data;
  }

  /**
   * New Level on the web (the desktop's `LevelSession.createLevel`): a flat ground of {@code size} cells on {@code surface}, as a new
   * terrain at `terrains/Level.terrain` (numbered when taken) and an entity named "Level Ground" with a Terrain and a Terrain Collider
   * 3D, in one undo step. The terrain is unsaved until Save Terrain.
   *
   * @param surface the base surface code (0 Auto, 1 Grass ... 6 Dirt)
   * @returns the new terrain's path, or why it was refused
   */
  createGround(size: MapSize, surface: number): { path: string } | { reason: string } {
    const file = this.model.file;
    if (file.entities.some((e) => e.components.some((c) => c.type === "Terrain" && c.enabled))) {
      return { reason: "This scene already has a level ground; change its Ground instead" };
    }
    const used = new Set([...this.player.terrainOverrides.keys(),
      ...file.entities.flatMap((e) => e.components.filter((c) => c.type === "Terrain").map((c) => String(c.data["terrainPath"])))]);
    let path = `${TERRAIN_FOLDER}/${LEVEL_NAME}.terrain`;
    for (let n = 2; used.has(path); n++) path = `${TERRAIN_FOLDER}/${LEVEL_NAME} ${n}.terrain`;
    const resolution = MAP_SIZES[size] + 1;
    const points = resolution * resolution;
    const surfaces = surface === 0 ? null : new Uint8Array(points).fill(surface);
    const title = path.slice(TERRAIN_FOLDER.length + 1, -".terrain".length);
    this.player.terrainOverrides.set(path, { name: title, resolutionX: resolution, resolutionZ: resolution, cellSize: NEW_CELL_SIZE,
      heights: new Float32Array(points), surfaces, layers: null, holes: null, waterLevel: null });
    this.unsaved.add(path);
    const entityId = newId();
    this.model.edit("New Level", "scene", (scene) => scene.entities.push({
      id: entityId, name: GROUND_ENTITY_NAME, active: true, parentId: null,
      transform: { position: { x: 0, y: 0, z: 0 }, rotation: { x: 0, y: 0, z: 0, w: 1 }, scale: { x: 1, y: 1, z: 1 } },
      components: [{ type: "Terrain", enabled: true, data: { terrainPath: path } },
        { type: "TerrainCollider3DComponent", enabled: true, data: { offsetX: 0, offsetY: 0, offsetZ: 0, trigger: false } }],
    }));
    return { path };
  }

  /** The terrain file text of an edited terrain, as the desktop writes it; marks it saved. */
  saveTerrain(path: string): string {
    const data = this.player.terrainOverrides.get(path);
    if (!data) throw new Error(`${path} has no edits to save`);
    this.unsaved.delete(path);
    return terrainToJson(data);
  }

  /** While painting the left button strokes, so it must not also orbit the camera. */
  private setOrbitButtons(painting: boolean): void {
    const camera = this.built?.viewerCamera as ArcRotateCamera | undefined;
    const pointers = camera?.inputs.attached["pointers"] as { buttons: number[] } | undefined;
    if (pointers) pointers.buttons = painting ? [1, 2] : [0, 1, 2];
  }

  private onPointer(type: number, event: PointerEvent): void {
    if (!this.enabled || !this.built) return;
    if (type === 1 /* POINTERDOWN */ && event.button === 0) void this.startStroke(event.ctrlKey);
    else if (type === 4 /* POINTERMOVE */ && this.stroke?.isActive) this.moveStroke();
    else if (type === 2 /* POINTERUP */ && event.button === 0 && this.stroke?.isActive) this.endStroke();
  }

  /** The ground under the pointer: its entity, terrain path and the point in the terrain's own space; null off the ground. */
  private pickGround(): { entityId: string; path: string; x: number; z: number } | null {
    const built = this.built;
    if (!built) return null;
    const pick = built.scene.pick(built.scene.pointerX, built.scene.pointerY, (mesh) => mesh.metadata?.terrain === true);
    const point = pick?.hit ? pick.pickedPoint : null;
    const entityId = pick?.pickedMesh?.metadata?.entityId as string | undefined;
    if (!point || !entityId) return null;
    const pending = built.pendingTerrains.find((p) => p.entity.id === entityId);
    if (!pending) return null;
    const local = Vector3.TransformCoordinates(point, pending.entity.node.getWorldMatrix().clone().invert());
    return { entityId, path: pending.path, x: local.x, z: local.z };
  }

  private async startStroke(ctrl: boolean): Promise<void> {
    const hit = this.pickGround();
    if (!hit) {
      this.onStatus("Click the ground to sculpt or paint it", true);
      return;
    }
    let data: TerrainData;
    try {
      data = await this.terrainData(hit.path);
    } catch (error) {
      this.onStatus(String(error instanceof Error ? error.message : error), true);
      return;
    }
    this.stroke = new TerrainStroke(data);
    this.strokePath = hit.path;
    this.strokeEntity = hit.entityId;
    this.stroke.begin({ ...this.brush, inverted: this.brush.inverted !== ctrl }, hit.x, hit.z);
    this.player.redrawTerrain(hit.path);
  }

  private moveStroke(): void {
    const hit = this.pickGround();
    if (!hit || hit.entityId !== this.strokeEntity) return;
    this.stroke!.move(hit.x, hit.z);
    this.player.redrawTerrain(this.strokePath!);
  }

  private endStroke(): void {
    const stroke = this.stroke!;
    const path = this.strokePath!;
    const delta = stroke.end();
    this.stroke = null;
    if (delta.heights.length + delta.weights.length + delta.holes.length === 0) return;
    const data = this.player.terrainOverrides.get(path)!;
    this.unsaved.add(path);
    const label = `Terrain: ${this.brush.type.charAt(0)}${this.brush.type.slice(1).toLowerCase()}`;
    this.model.recordExternal(label, () => {
      applyDelta(data, delta, false);
      this.unsaved.add(path);
      this.player.redrawTerrain(path);
    }, () => {
      applyDelta(data, delta, true);
      this.unsaved.add(path);
      this.player.redrawTerrain(path);
    });
    this.onStatus(`${label} on ${path}: not saved yet (Save Terrain)`);
  }
}
