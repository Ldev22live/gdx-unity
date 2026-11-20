import { Color3, Mesh, MeshBuilder, StandardMaterial, VertexData } from "@babylonjs/core";
import { WATER_COLOR, parseTerrainFile, terrainGeometry, terrainSize, type TerrainData } from "../terrain/terrain";
import type { BuiltScene, PendingTerrain } from "./SceneBuilder";
import type { TextFetcher } from "./SpriteLoader";

/** Where a `.terrain` named by a scene (for example `terrains/Valley.terrain`) is found by the browser. */
export type TerrainSource = (path: string) => string;
export const defaultTerrainSource: TerrainSource = (path) => path;

const fetchText: TextFetcher = async (url) => {
  const response = await fetch(url);
  if (!response.ok) throw new Error(`${response.status} ${response.statusText} for ${url}`);
  return response.text();
};

/** Ground is matte, like the desktop's terrain (roughness 0.95): a faint highlight only. */
const GROUND_SPECULAR = 0.03;
/** Water catches highlights, like the desktop's (roughness 0.15). */
const WATER_SPECULAR = 0.5;

/** What went wrong loading one terrain; the scene still shows everything else. */
export interface TerrainFailure {
  entityName: string;
  path: string;
  message: string;
}

/** The mesh of a terrain read from its file, under {@code pending}'s entity, and its water when it has some (roadmap M3.11). */
export function createTerrainMeshes(built: BuiltScene, pending: PendingTerrain, data: TerrainData): Mesh[] {
  const scene = built.scene;
  const geometry = terrainGeometry(data);
  const ground = new Mesh(`${pending.entity.name} terrain`, scene);
  const vertexData = new VertexData();
  vertexData.positions = geometry.positions;
  vertexData.normals = geometry.normals;
  vertexData.colors = geometry.colors;
  vertexData.indices = geometry.indices;
  vertexData.applyToMesh(ground);
  const material = new StandardMaterial(`${pending.entity.name} ground`, scene);
  material.diffuseColor = Color3.White(); // the vertex colours are the ground
  material.specularColor = new Color3(GROUND_SPECULAR, GROUND_SPECULAR, GROUND_SPECULAR);
  material.backFaceCulling = false; // a hole shows the underside
  ground.material = material;
  ground.useVertexColors = true;
  ground.hasVertexAlpha = false;
  ground.parent = pending.entity.node;
  ground.metadata = { entityId: pending.entity.id, terrain: true };
  ground.receiveShadows = built.shadowGenerators.length > 0;
  for (const generator of built.shadowGenerators) generator.addShadowCaster(ground);
  const meshes = [ground];
  if (data.waterLevel !== null) {
    const size = terrainSize(data);
    const water = MeshBuilder.CreateGround(`${pending.entity.name} water`, { width: size.x, height: size.z }, scene);
    water.position.y = data.waterLevel;
    const waterMaterial = new StandardMaterial(`${pending.entity.name} water`, scene);
    waterMaterial.diffuseColor = new Color3(WATER_COLOR.r, WATER_COLOR.g, WATER_COLOR.b);
    waterMaterial.specularColor = new Color3(WATER_SPECULAR, WATER_SPECULAR, WATER_SPECULAR);
    waterMaterial.alpha = WATER_COLOR.a;
    waterMaterial.backFaceCulling = false;
    water.material = waterMaterial;
    water.parent = pending.entity.node;
    water.metadata = { entityId: pending.entity.id, terrainWater: true };
    meshes.push(water);
  }
  pending.entity.meshes.push(...meshes);
  return meshes;
}

/**
 * Loads the `.terrain` files that Terrain components name and draws them on their entities (roadmap M3.11). A file is read once however
 * many entities use it. A failure is reported, not thrown, and nothing is added when the scene was disposed meanwhile (an editor rebuild).
 */
export async function loadPendingTerrains(
  built: BuiltScene,
  source: TerrainSource = defaultTerrainSource,
  fetcher: TextFetcher = fetchText,
): Promise<TerrainFailure[]> {
  const cache = new Map<string, Promise<TerrainData>>();
  const failures: TerrainFailure[] = [];
  await Promise.all(built.pendingTerrains.map(async (pending) => {
    try {
      let loading = cache.get(pending.path);
      if (!loading) {
        loading = fetcher(source(pending.path)).then(parseTerrainFile);
        cache.set(pending.path, loading);
      }
      const data = await loading;
      if (built.scene.isDisposed) return;
      createTerrainMeshes(built, pending, data);
    } catch (error) {
      failures.push({ entityName: pending.entity.name, path: pending.path, message: error instanceof Error ? error.message : String(error) });
    }
  }));
  built.terrainFailures.push(...failures);
  return failures;
}
