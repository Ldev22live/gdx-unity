import { Color3, Constants, Material, PBRMaterial, RawCubeTexture, Texture, type BaseTexture, type Scene } from "@babylonjs/core";
import { emissiveLinear, opacity, parseMaterialFile, toLinear, type MaterialData } from "../material/material";
import type { BuiltScene, PendingMaterial } from "./SceneBuilder";
import type { TextFetcher } from "./SpriteLoader";

/** Where a `.material` or texture named by a scene (for example `materials/Brick.material`) is found by the browser. */
export type MaterialSource = (path: string) => string;
export const defaultMaterialSource: MaterialSource = (path) => path;
/** Makes a texture from its URL; tests pass a stand-in, since a headless engine cannot decode images. */
export type TextureFactory = (url: string, scene: Scene, sRGB: boolean) => Texture;
export const defaultTextureFactory: TextureFactory = (url, scene, sRGB) => {
  const texture = new Texture(url, scene, false, false, Texture.TRILINEAR_SAMPLINGMODE);
  texture.gammaSpace = sRGB;
  return texture;
};

const fetchText: TextFetcher = async (url) => {
  const response = await fetch(url);
  if (!response.ok) throw new Error(`${response.status} ${response.statusText} for ${url}`);
  return response.text();
};

/** Faces of the sky cube the materials reflect: small, since it holds only two colours. */
const SKY_CUBE_SIZE = 8;

/** What went wrong loading one material; the primitive keeps its own colour and the rest of the scene still shows. */
export interface MaterialFailure {
  entityName: string;
  path: string;
  message: string;
}

/**
 * A cube of the scene's Sky Light colours, sky above the horizon and ground below, for shiny materials to reflect: the web's stand-in for
 * the desktop's image-based sky light (TD-032). Without a Sky Light there is none, as the desktop then has only flat ambient light.
 */
export function createSkyReflection(built: BuiltScene): BaseTexture | null {
  const colors = skyReflectionColors(built);
  if (!colors) return null;
  const { sky: skyColor, ground } = colors;
  const face = (upper: number[], lower: number[]): Uint8Array => {
    const pixels = new Uint8Array(SKY_CUBE_SIZE * SKY_CUBE_SIZE * 4);
    for (let y = 0; y < SKY_CUBE_SIZE; y++) {
      // Row 0 is the top of a side face
      const color = y < SKY_CUBE_SIZE / 2 ? upper : lower;
      for (let x = 0; x < SKY_CUBE_SIZE; x++) pixels.set([color[0]!, color[1]!, color[2]!, 255], (y * SKY_CUBE_SIZE + x) * 4);
    }
    return pixels;
  };
  const side = face(skyColor, ground);
  // Faces in Babylon's order: +x, -x, +y (up: sky), -y (down: ground), +z, -z
  const faces = [side, side, face(skyColor, skyColor), face(ground, ground), side, side];
  const cube = new RawCubeTexture(built.scene, faces, SKY_CUBE_SIZE, Constants.TEXTUREFORMAT_RGBA, Constants.TEXTURETYPE_UNSIGNED_BYTE, false);
  cube.gammaSpace = true;
  return cube;
}

/** The scene's first enabled Sky Light's sky and ground colours as 0 to 255 sRGB bytes, or null without one. */
export function skyReflectionColors(built: BuiltScene): { sky: number[]; ground: number[] } | null {
  const entity = built.game.entities.find((candidate) => candidate.isActiveInHierarchy && candidate.getComponent("SkyLightComponent")?.enabled);
  const data = entity?.getComponent("SkyLightComponent")?.data;
  if (!data) return null;
  const channel = (key: string): number => Math.round(Math.max(0, Math.min(1, Number(data[key] ?? 0))) * 255);
  return { sky: [channel("skyR"), channel("skyG"), channel("skyB")], ground: [channel("groundR"), channel("groundG"), channel("groundB")] };
}

/** Makes the sky the materials reflect; tests pass a stand-in, since a headless engine cannot make cube textures. */
export type ReflectionFactory = (built: BuiltScene) => BaseTexture | null;

/**
 * A Babylon PBR material with the look of {@code data}, as the desktop's `MaterialAttributes.apply` sets gdx-gltf's PBR shader: base
 * colour and opacity, metallic, roughness, emissive colour times strength, the alpha mode, double sides (always for a plane), and each
 * texture slot with the tiling and offset, normal scale and occlusion strength. Colours are sRGB in the file and linear here.
 */
export function createPbrMaterial(
  scene: Scene,
  name: string,
  data: MaterialData,
  twoSided: boolean,
  reflection: BaseTexture | null,
  texture: (path: string, sRGB: boolean) => Texture,
): PBRMaterial {
  const material = new PBRMaterial(name, scene);
  material.albedoColor = new Color3(toLinear(data.baseColor[0]), toLinear(data.baseColor[1]), toLinear(data.baseColor[2]));
  material.metallic = data.metallic;
  material.roughness = data.roughness;
  const [er, eg, eb] = emissiveLinear(data);
  material.emissiveColor = new Color3(er, eg, eb);
  // Light ranges are authored for the web's other materials, which fade point lights over their range; keep the same falloff
  material.usePhysicalLightFalloff = false;
  material.backFaceCulling = !(twoSided || data.doubleSided);
  material.twoSidedLighting = !material.backFaceCulling;
  if (reflection) material.reflectionTexture = reflection;
  const place = (t: Texture): Texture => {
    t.uScale = data.tiling[0];
    t.vScale = data.tiling[1];
    t.uOffset = data.offset[0];
    t.vOffset = data.offset[1];
    t.wrapU = Texture.WRAP_ADDRESSMODE;
    t.wrapV = Texture.WRAP_ADDRESSMODE;
    return t;
  };
  const slots = data.textures;
  if (slots.BASE_COLOR) material.albedoTexture = place(texture(slots.BASE_COLOR, true));
  if (slots.NORMAL) {
    const normal = place(texture(slots.NORMAL, false));
    normal.level = data.normalScale;
    material.bumpTexture = normal;
  }
  if (slots.METALLIC_ROUGHNESS) {
    // glTF's layout, which the desktop's shader reads too: roughness in green, metallic in blue
    material.metallicTexture = place(texture(slots.METALLIC_ROUGHNESS, false));
    material.useRoughnessFromMetallicTextureGreen = true;
    material.useMetallnessFromMetallicTextureBlue = true;
    material.useRoughnessFromMetallicTextureAlpha = false;
  }
  if (slots.OCCLUSION) {
    material.ambientTexture = place(texture(slots.OCCLUSION, false));
    material.ambientTextureStrength = data.occlusionStrength;
    material.useAmbientInGrayScale = true;
  }
  if (slots.EMISSIVE) material.emissiveTexture = place(texture(slots.EMISSIVE, true));
  switch (data.alphaMode) {
    case "BLEND":
      material.alpha = opacity(data);
      material.transparencyMode = Material.MATERIAL_ALPHABLEND;
      break;
    case "MASK":
      material.transparencyMode = Material.MATERIAL_ALPHATEST;
      material.alphaCutOff = data.alphaCutoff;
      if (material.albedoTexture) material.albedoTexture.hasAlpha = true;
      break;
    default:
      material.transparencyMode = Material.MATERIAL_OPAQUE;
  }
  return material;
}

/**
 * Loads the `.material` files that Mesh Primitives name and puts them on their meshes (roadmap M2.5). A file is read once however many
 * primitives use it; a primitive whose material cannot be read keeps its own colour, and the failure is reported, not thrown, as on the
 * desktop. Nothing is added when the scene was disposed meanwhile (an editor rebuild).
 */
export async function loadPendingMaterials(
  built: BuiltScene,
  source: MaterialSource = defaultMaterialSource,
  fetcher: TextFetcher = fetchText,
  textures: TextureFactory = defaultTextureFactory,
  reflections: ReflectionFactory = createSkyReflection,
): Promise<MaterialFailure[]> {
  const files = new Map<string, Promise<MaterialData>>();
  const made = new Map<string, PBRMaterial>();
  const failures: MaterialFailure[] = [];
  const reflection = built.pendingMaterials.length > 0 ? reflections(built) : null;
  const textureCache = new Map<string, Texture>();
  const texture = (path: string, sRGB: boolean): Texture => {
    const key = `${path}|${sRGB}`;
    let found = textureCache.get(key);
    if (!found) {
      found = textures(source(path), built.scene, sRGB);
      textureCache.set(key, found);
    }
    // Each material places its own copy, so two materials can tile the same image differently
    return found.clone();
  };
  await Promise.all(built.pendingMaterials.map(async (pending: PendingMaterial) => {
    try {
      let loading = files.get(pending.path);
      if (!loading) {
        loading = fetcher(source(pending.path)).then(parseMaterialFile);
        files.set(pending.path, loading);
      }
      const data = await loading;
      if (built.scene.isDisposed) return;
      const key = `${pending.path}|${pending.twoSided}`;
      let material = made.get(key);
      if (!material) {
        material = createPbrMaterial(built.scene, data.name, data, pending.twoSided, reflection, texture);
        made.set(key, material);
      }
      const previous = pending.mesh.material;
      pending.mesh.material = material;
      if (previous && previous !== material) previous.dispose();
    } catch (error) {
      failures.push({ entityName: pending.entity.name, path: pending.path, message: error instanceof Error ? error.message : String(error) });
    }
  }));
  built.materialFailures.push(...failures);
  return failures;
}
