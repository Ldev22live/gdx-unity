import { Material, NullEngine, PBRMaterial, Scene, StandardMaterial, Texture } from "@babylonjs/core";
import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { defaultMaterial, emissiveLinear, parseMaterialFile, toLinear } from "../src/material/material";
import { createPbrMaterial, loadPendingMaterials, skyReflectionColors, type ReflectionFactory, type TextureFactory } from "../src/runtime/MaterialLoader";
import { buildScene } from "../src/runtime/SceneBuilder";
import { createHeadlessEngine, loadSample, SAMPLES_DIR } from "./support";

/** Roadmap M2.5: the web player reads the desktop's `.material` files and draws a primitive with the same material. */

const ASSETS = join(SAMPLES_DIR, "..");
const MATERIALS_DIR = join(ASSETS, "materials");
const read = (name: string): string => readFileSync(join(MATERIALS_DIR, name), "utf8");
const fromAssets = async (path: string): Promise<string> => readFileSync(join(ASSETS, path), "utf8");
/** A headless engine cannot decode images: textures are empty stand-ins that remember their URL. */
const fakeTextures: TextureFactory = (url, scene, sRGB) => {
  const texture = new Texture(null, scene);
  texture.name = url;
  texture.gammaSpace = sRGB;
  return texture;
};

/** Stands in for the sky cube: an empty texture, made only when the scene has a Sky Light, as the real one is. */
const fakeReflection: ReflectionFactory = (built) => (skyReflectionColors(built) ? new Texture(null, built.scene) : null);

describe("parseMaterialFile", () => {
  it("shouldReadEveryShippedMaterial", () => {
    const files = readdirSync(MATERIALS_DIR).filter((name) => name.endsWith(".material"));
    expect(files.length).toBeGreaterThanOrEqual(9);
    for (const file of files) expect(() => parseMaterialFile(read(file)), file).not.toThrow();
  });

  it("shouldReadTheBricksTexturesTilingAndNormalScale", () => {
    const brick = parseMaterialFile(read("Brick.material"));
    expect(brick.name).toBe("Brick");
    expect(brick.roughness).toBe(0.85);
    expect(brick.normalScale).toBe(1.5);
    expect(brick.tiling).toEqual([3, 3]);
    expect(brick.textures).toEqual({ BASE_COLOR: "textures/brick.png", NORMAL: "textures/brick_normal.png" });
  });

  it("shouldKeepDefaultsForMissingFieldsAndRefuseWhatTheDesktopRefuses", () => {
    expect(parseMaterialFile(JSON.stringify({ version: 1 }))).toEqual(defaultMaterial());
    expect(() => parseMaterialFile("{")).toThrow(/Not a material file/);
    expect(() => parseMaterialFile(JSON.stringify({ version: 2 }))).toThrow(/version 2 is not supported/);
    expect(() => parseMaterialFile(JSON.stringify({ version: 1, metallic: 2 }))).toThrow(/metallic must be within 0 and 1/);
    expect(() => parseMaterialFile(JSON.stringify({ version: 1, baseColor: [1, 1, 1] }))).toThrow(/list of 4 numbers/);
    expect(() => parseMaterialFile(JSON.stringify({ version: 1, alphaMode: "ADD" }))).toThrow(/OPAQUE, BLEND or MASK/);
    expect(() => parseMaterialFile(JSON.stringify({ version: 1, tiling: [0, 1] }))).toThrow(/Tiling must be above 0/);
    expect(() => parseMaterialFile(JSON.stringify({ version: 1, textures: { SHINE: "a.png" } }))).toThrow(/unknown texture slot 'SHINE'/);
    expect(() => parseMaterialFile(JSON.stringify({ version: 1, name: " " }))).toThrow(/needs a name/);
  });

  it("shouldGlowWhiteWhenOnlyAnEmissiveTextureIsGiven", () => {
    const glow = parseMaterialFile(JSON.stringify({ version: 1, emissiveStrength: 2, textures: { EMISSIVE: "textures/neon.png" } }));
    expect(emissiveLinear(glow)).toEqual([2, 2, 2]);
    expect(emissiveLinear(defaultMaterial())).toEqual([0, 0, 0]);
  });
});

describe("createPbrMaterial", () => {
  const scene = (): Scene => new Scene(new NullEngine());
  const textures = (s: Scene) => (path: string, sRGB: boolean) => fakeTextures(path, s, sRGB);

  it("shouldMatchTheDesktopsColoursInLinearLightAndItsMetalAndRoughness", () => {
    const s = scene();
    const gold = parseMaterialFile(read("Gold.material"));
    const material = createPbrMaterial(s, "Gold", gold, false, null, textures(s));
    expect(material.albedoColor.r).toBeCloseTo(toLinear(gold.baseColor[0]), 6);
    expect(material.albedoColor.g).toBeCloseTo(toLinear(gold.baseColor[1]), 6);
    expect(material.metallic).toBe(gold.metallic);
    expect(material.roughness).toBe(gold.roughness);
    expect(material.transparencyMode).toBe(Material.MATERIAL_OPAQUE);
    expect(material.backFaceCulling).toBe(true);
  });

  it("shouldBlendGlassAtItsOpacityAndDrawAPlaneFromBothSides", () => {
    const s = scene();
    const glass = createPbrMaterial(s, "Glass", parseMaterialFile(read("Glass.material")), false, null, textures(s));
    expect(glass.transparencyMode).toBe(Material.MATERIAL_ALPHABLEND);
    expect(glass.alpha).toBeCloseTo(0.25, 6);
    const ground = createPbrMaterial(s, "Rock", parseMaterialFile(read("Rock.material")), true, null, textures(s));
    expect(ground.backFaceCulling).toBe(false);
  });

  it("shouldPlaceTheBricksTexturesWithItsTilingAndNormalScale", () => {
    const s = scene();
    const brick = createPbrMaterial(s, "Brick", parseMaterialFile(read("Brick.material")), false, null, textures(s));
    const albedo = brick.albedoTexture as Texture;
    const normal = brick.bumpTexture as Texture;
    expect(albedo.name).toBe("textures/brick.png");
    expect(albedo.gammaSpace, "colour textures are sRGB").toBe(true);
    expect(albedo.uScale).toBe(3);
    expect(albedo.vScale).toBe(3);
    expect(normal.name).toBe("textures/brick_normal.png");
    expect(normal.gammaSpace, "normal maps are data").toBe(false);
    expect(normal.level).toBe(1.5);
  });

  it("shouldMaskWithTheCutoff", () => {
    const s = scene();
    const mask = parseMaterialFile(JSON.stringify({ version: 1, alphaMode: "MASK", alphaCutoff: 0.3, textures: { BASE_COLOR: "textures/checker.png" } }));
    const material = createPbrMaterial(s, "Mask", mask, false, null, textures(s));
    expect(material.transparencyMode).toBe(Material.MATERIAL_ALPHATEST);
    expect(material.alphaCutOff).toBe(0.3);
    expect(material.albedoTexture!.hasAlpha).toBe(true);
  });
});

describe("the materials-3d sample in the web player", () => {
  it("shouldPutEveryPrimitivesMaterialOnItAndLeaveThePlainBallItsColour", async () => {
    const built = buildScene(createHeadlessEngine(), loadSample("materials-3d.json"));
    expect(built.pendingMaterials).toHaveLength(10);
    const plane = built.pendingMaterials.find((pending) => pending.entity.name === "Ground")!;
    expect(plane.twoSided).toBe(true);

    const failures = await loadPendingMaterials(built, (path) => path, fromAssets, fakeTextures, fakeReflection);

    expect(failures).toEqual([]);
    for (const pending of built.pendingMaterials) {
      expect(pending.mesh.material, pending.entity.name).toBeInstanceOf(PBRMaterial);
    }
    const gold = built.game.findByName("Gold Ball")!.meshes[0]!.material as PBRMaterial;
    expect(gold.name).toBe("Gold");
    expect(gold.reflectionTexture, "shiny materials reflect the sample's Sky Light").not.toBeNull();
    // Two primitives with the same material share one
    expect(built.game.findByName("Rock Block")!.meshes[0]!.material).toBe(
      built.pendingMaterials.find((pending) => pending.entity.name === "Rock Block")!.mesh.material);
    expect(built.game.findByName("Plain Ball")!.meshes[0]!.material).toBeInstanceOf(StandardMaterial);
  });

  it("shouldKeepThePrimitivesColourAndReportAMaterialThatCannotBeRead", async () => {
    const built = buildScene(createHeadlessEngine(), loadSample("materials-3d.json"));
    const failures = await loadPendingMaterials(built, (path) => path, async () => "{\"version\": 7}", fakeTextures, fakeReflection);
    expect(failures).toHaveLength(10);
    expect(failures[0]!.message).toMatch(/version 7 is not supported/);
    expect(built.game.findByName("Gold Ball")!.meshes[0]!.material).toBeInstanceOf(StandardMaterial);
    expect(built.materialFailures).toEqual(failures);
  });

  it("shouldReflectTheSkyLightsColoursAndNothingWithoutOne", () => {
    const built = buildScene(createHeadlessEngine(), loadSample("materials-3d.json"));
    const sky = built.game.entities.find((entity) => entity.getComponent("SkyLightComponent"))!.getComponent("SkyLightComponent")!.data;
    expect(skyReflectionColors(built)).toEqual({
      sky: [Math.round(Number(sky.skyR) * 255), Math.round(Number(sky.skyG) * 255), Math.round(Number(sky.skyB) * 255)],
      ground: [Math.round(Number(sky.groundR) * 255), Math.round(Number(sky.groundG) * 255), Math.round(Number(sky.groundB) * 255)],
    });
    const plain = buildScene(createHeadlessEngine(), loadSample("breakout-2d.json"));
    expect(skyReflectionColors(plain)).toBeNull();
  });
});
