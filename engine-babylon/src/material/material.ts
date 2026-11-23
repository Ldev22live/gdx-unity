/**
 * The desktop's `.material` file (TD-081) read by the web player (roadmap M2.5): the same fields, defaults and checks as the desktop's
 * `MaterialIO` and `Material`. Pure data, no Babylon types, so it is tested without an engine.
 */

/** The file version this player reads, the same as the desktop's `MaterialIO.VERSION`. */
export const MATERIAL_VERSION = 1;
/** Largest emissive strength (`Material.MAX_EMISSIVE_STRENGTH`). */
export const MAX_EMISSIVE_STRENGTH = 100;
/** Largest normal scale (`Material.setNormalScale`). */
export const MAX_NORMAL_SCALE = 10;
/** Largest tiling per axis (`Material.MAX_TILING`). */
export const MAX_TILING = 1000;

export type AlphaMode = "OPAQUE" | "BLEND" | "MASK";
export type TextureSlot = "BASE_COLOR" | "NORMAL" | "METALLIC_ROUGHNESS" | "OCCLUSION" | "EMISSIVE";
export const TEXTURE_SLOTS: readonly TextureSlot[] = ["BASE_COLOR", "NORMAL", "METALLIC_ROUGHNESS", "OCCLUSION", "EMISSIVE"];

/** One material as the file holds it. Colours are sRGB, 0 to 1; the base colour's alpha is the opacity. */
export interface MaterialData {
  name: string;
  baseColor: [number, number, number, number];
  metallic: number;
  roughness: number;
  emissive: [number, number, number];
  emissiveStrength: number;
  normalScale: number;
  occlusionStrength: number;
  alphaMode: AlphaMode;
  alphaCutoff: number;
  doubleSided: boolean;
  tiling: [number, number];
  offset: [number, number];
  /** Project-relative texture path per filled slot. */
  textures: Partial<Record<TextureSlot, string>>;
}

/** A material with every field at the desktop's default: matte white, opaque, no textures. */
export function defaultMaterial(): MaterialData {
  return {
    name: "Material",
    baseColor: [1, 1, 1, 1],
    metallic: 0,
    roughness: 0.7,
    emissive: [0, 0, 0],
    emissiveStrength: 1,
    normalScale: 1,
    occlusionStrength: 1,
    alphaMode: "OPAQUE",
    alphaCutoff: 0.5,
    doubleSided: false,
    tiling: [1, 1],
    offset: [0, 0],
    textures: {},
  };
}

function numberIn(root: Record<string, unknown>, field: string, min: number, max: number): number {
  const value = root[field];
  if (typeof value !== "number" || !Number.isFinite(value)) throw new Error(`${field} must be a number`);
  if (value < min || value > max) throw new Error(`${field} must be within ${min} and ${max}: ${value}`);
  return value;
}

function numbers(root: Record<string, unknown>, field: string, count: number): number[] {
  const value = root[field];
  if (!Array.isArray(value) || value.length !== count || value.some((item) => typeof item !== "number" || !Number.isFinite(item))) {
    throw new Error(`${field} must be a list of ${count} numbers`);
  }
  return value as number[];
}

function unitColor(root: Record<string, unknown>, field: string, count: number): number[] {
  const color = numbers(root, field, count);
  if (color.some((channel) => channel < 0 || channel > 1)) throw new Error(`${field} channels must be within 0 and 1: ${color.join(", ")}`);
  return color;
}

/**
 * Reads a `.material` file: strict about what it understands (a newer version, a wrong type, an out-of-range value, an unknown alpha mode
 * or texture slot is an error that names the field) and forgiving about what is missing (a missing field keeps the default), like the
 * desktop.
 */
export function parseMaterialFile(text: string): MaterialData {
  let root: unknown;
  try {
    root = JSON.parse(text);
  } catch (error) {
    throw new Error(`Not a material file: ${error instanceof Error ? error.message : String(error)}`);
  }
  if (typeof root !== "object" || root === null || Array.isArray(root)) throw new Error("Not a material file: expected a JSON object");
  const r = root as Record<string, unknown>;
  if (r.version !== MATERIAL_VERSION) {
    throw new Error(r.version === undefined ? "Not a material file: it has no version"
      : `Material file version ${String(r.version)} is not supported (this player reads version ${MATERIAL_VERSION})`);
  }
  const material = defaultMaterial();
  try {
    if ("name" in r) {
      if (typeof r.name !== "string") throw new Error("name must be text");
      if (r.name.trim() === "") throw new Error("A material needs a name");
      material.name = r.name.trim();
    }
    if ("baseColor" in r) material.baseColor = unitColor(r, "baseColor", 4) as MaterialData["baseColor"];
    if ("metallic" in r) material.metallic = numberIn(r, "metallic", 0, 1);
    if ("roughness" in r) material.roughness = numberIn(r, "roughness", 0, 1);
    if ("emissive" in r) material.emissive = unitColor(r, "emissive", 3) as MaterialData["emissive"];
    if ("emissiveStrength" in r) material.emissiveStrength = numberIn(r, "emissiveStrength", 0, MAX_EMISSIVE_STRENGTH);
    if ("normalScale" in r) material.normalScale = numberIn(r, "normalScale", 0, MAX_NORMAL_SCALE);
    if ("occlusionStrength" in r) material.occlusionStrength = numberIn(r, "occlusionStrength", 0, 1);
    if ("alphaMode" in r) {
      if (r.alphaMode !== "OPAQUE" && r.alphaMode !== "BLEND" && r.alphaMode !== "MASK") {
        throw new Error(`alphaMode must be one of OPAQUE, BLEND or MASK: ${String(r.alphaMode)}`);
      }
      material.alphaMode = r.alphaMode;
    }
    if ("alphaCutoff" in r) material.alphaCutoff = numberIn(r, "alphaCutoff", 0, 1);
    if ("doubleSided" in r) {
      if (typeof r.doubleSided !== "boolean") throw new Error("doubleSided must be true or false");
      material.doubleSided = r.doubleSided;
    }
    if ("tiling" in r) {
      const [u, v] = numbers(r, "tiling", 2) as [number, number];
      if (!(u > 0 && u <= MAX_TILING && v > 0 && v <= MAX_TILING)) throw new Error(`Tiling must be above 0 and at most ${MAX_TILING}: ${u}, ${v}`);
      material.tiling = [u, v];
    }
    if ("offset" in r) material.offset = numbers(r, "offset", 2) as [number, number];
    if ("textures" in r) {
      const textures = r.textures;
      if (typeof textures !== "object" || textures === null || Array.isArray(textures)) throw new Error("textures must be an object");
      for (const [slot, path] of Object.entries(textures as Record<string, unknown>)) {
        if (!TEXTURE_SLOTS.includes(slot as TextureSlot)) {
          throw new Error(`unknown texture slot '${slot}'; the slots are [${TEXTURE_SLOTS.join(", ")}]`);
        }
        if (typeof path !== "string") throw new Error(`texture ${slot} must be a path`);
        if (path.trim() !== "") material.textures[slot as TextureSlot] = path.trim();
      }
    }
  } catch (error) {
    throw new Error(`Bad material: ${error instanceof Error ? error.message : String(error)}`);
  }
  return material;
}

/** The desktop's display gamma; material colours are sRGB and lighting works in linear light. */
const GAMMA = 2.2;

/** An sRGB channel in linear light, as the desktop's renderer converts it (a power of 2.2, not the exact sRGB curve). */
export function toLinear(channel: number): number {
  return Math.pow(channel, GAMMA);
}

/** The colour surfaces emit, in linear light: the emissive colour (white when only an emissive texture is given) times its strength. */
export function emissiveLinear(material: MaterialData): [number, number, number] {
  const black = material.emissive.every((channel) => channel === 0);
  const color = black && material.textures.EMISSIVE ? [1, 1, 1] : material.emissive;
  return [toLinear(color[0]!) * material.emissiveStrength, toLinear(color[1]!) * material.emissiveStrength, toLinear(color[2]!) * material.emissiveStrength];
}

/** How opaque the surface is: 1 when opaque, else the base colour's alpha (the desktop's rule). */
export function opacity(material: MaterialData): number {
  return material.alphaMode === "OPAQUE" ? 1 : material.baseColor[3];
}
