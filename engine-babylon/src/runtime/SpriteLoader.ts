import { Mesh, RawTexture, StandardMaterial, Texture, VertexData, Color3 } from "@babylonjs/core";
import { buildAtlas, buildSprite, decodePngInBrowser, parseSpriteFile, type Atlas, type PngDecoder, type Sprite, tileLayerGeometry } from "../sprites/sprite";
import { ALPHA_INDEX_STEP, type BuiltScene, type PendingSprite, type PendingTilemap } from "./SceneBuilder";

/** Where a sprite file named by a scene (for example `sprites/hero.sprite`) is found by the browser. */
export type SpriteSource = (path: string) => string;
export const defaultSpriteSource: SpriteSource = (path) => path;
export type TextFetcher = (url: string) => Promise<string>;

const fetchText: TextFetcher = async (url) => {
  const response = await fetch(url);
  if (!response.ok) throw new Error(`${response.status} ${response.statusText} for ${url}`);
  return response.text();
};

/**
 * Makes a material draw the atlas unlit, with its own alpha and the component's tint: the texture is the base color and the (white by default)
 * emissive color is what the lighting would otherwise add, so the result is texture x tint. The planes are single sided and seen from both sides
 * (a flipped sprite has the opposite winding).
 */
function applyPixelMaterial(material: StandardMaterial, texture: RawTexture, tint: { r: number; g: number; b: number; a: number }): void {
  material.disableLighting = true;
  material.diffuseTexture = texture;
  material.useAlphaFromDiffuseTexture = true;
  material.emissiveColor = new Color3(tint.r, tint.g, tint.b);
  material.alpha = tint.a;
  material.backFaceCulling = false;
}

/** A sprite file after loading: the flattened frames and the atlas texture they share. */
interface LoadedSprite {
  sprite: Sprite;
  atlas: Atlas;
  texture: RawTexture;
}

/** What went wrong loading one sprite or tile set; the scene still shows everything else. */
export interface SpriteFailure {
  entityName: string;
  path: string;
  message: string;
}

/**
 * Loads the sprite files that Sprite Animator and Tilemap components name and puts them on their entities: animators get their texture and size,
 * tile maps get one mesh for each visible layer. A file is read once however many entities use it. A failure is reported, not thrown, and nothing is
 * added when the scene was disposed meanwhile (an editor rebuild).
 */
export async function loadPendingSprites(
  built: BuiltScene,
  decode: PngDecoder = decodePngInBrowser,
  source: SpriteSource = defaultSpriteSource,
  fetcher: TextFetcher = fetchText,
): Promise<SpriteFailure[]> {
  const cache = new Map<string, Promise<LoadedSprite>>();
  const load = (path: string): Promise<LoadedSprite> => {
    let loading = cache.get(path);
    if (!loading) {
      loading = (async () => {
        const sprite = await buildSprite(parseSpriteFile(await fetcher(source(path))), decode);
        const atlas = buildAtlas(sprite);
        const texture = RawTexture.CreateRGBATexture(atlas.data, atlas.width, atlas.height, built.scene, false, true, Texture.NEAREST_SAMPLINGMODE);
        texture.hasAlpha = true;
        texture.wrapU = Texture.CLAMP_ADDRESSMODE;
        texture.wrapV = Texture.CLAMP_ADDRESSMODE;
        return { sprite, atlas, texture };
      })();
      cache.set(path, loading);
    }
    return loading;
  };
  const failures: SpriteFailure[] = [];
  const fail = (entityName: string, path: string, error: unknown): SpriteFailure => ({
    entityName,
    path,
    message: error instanceof Error ? error.message : String(error),
  });
  await Promise.all([
    ...built.pendingSprites.map(async (pending: PendingSprite) => {
      try {
        const loaded = await load(pending.path);
        if (built.scene.isDisposed) return;
        const material = new StandardMaterial(`${pending.entity.name} sprite`, built.scene);
        applyPixelMaterial(material, loaded.texture, pending.tint);
        pending.binding.mesh.material = material;
        pending.binding.attach(loaded.sprite, loaded.atlas);
      } catch (error) {
        pending.binding.loadError = `Cannot load ${pending.path}: ${error instanceof Error ? error.message : String(error)}`;
        failures.push(fail(pending.entity.name, pending.path, error));
      }
    }),
    ...built.pendingTilemaps.map(async (pending: PendingTilemap) => {
      try {
        const loaded = await load(pending.path);
        if (built.scene.isDisposed) return;
        const material = new StandardMaterial(`${pending.entity.name} tiles`, built.scene);
        applyPixelMaterial(material, loaded.texture, pending.tint);
        pending.map.layers.forEach((layer, index) => {
          if (!layer.visible) return;
          const geometry = tileLayerGeometry(pending.map, layer, loaded.atlas, loaded.sprite.frames.length);
          if (geometry.indices.length === 0) return;
          const mesh = new Mesh(`${pending.entity.name} ${layer.name}`, built.scene);
          const data = new VertexData();
          data.positions = geometry.positions;
          data.uvs = geometry.uvs;
          data.indices = geometry.indices;
          data.applyToMesh(mesh);
          mesh.material = material;
          mesh.parent = pending.entity.node;
          mesh.metadata = { entityId: pending.entity.id };
          // Layers draw in order, one depth step apart; the tile map as a whole sits at its entity's depth
          mesh.position.z = pending.depth + index * pending.layerStep;
          mesh.alphaIndex = pending.order * ALPHA_INDEX_STEP + index;
          mesh.alwaysSelectAsActiveMesh = true;
          pending.entity.meshes.push(mesh);
        });
      } catch (error) {
        failures.push(fail(pending.entity.name, pending.path, error));
      }
    }),
  ]);
  built.spriteFailures.push(...failures);
  return failures;
}
