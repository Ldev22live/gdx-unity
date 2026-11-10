import { existsSync, readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { loadPendingModels } from "../src/runtime/ModelLoader";
import { buildScene } from "../src/runtime/SceneBuilder";
import { str } from "../src/scene/types";
import { createHeadlessEngine, loadSample } from "./support";

const ASSETS = join(import.meta.dirname, "..", "..", "assets");
const MODELS = join(ASSETS, "models", "landscape");
const FOREST_MODELS = ["terrain-hills", "mountain", "pine-tree", "oak-tree", "rock", "bush", "cabin", "lake"].map((n) => `${n}.gltf`);
/** Every model in the folder: the forest valley's and the four other biomes'. */
const EXPECTED_MODELS = readdirSync(MODELS).filter((name) => name.endsWith(".gltf"));

interface Gltf {
  asset: { version: string };
  accessors: { bufferView: number; componentType: number; count: number; type: string; min?: number[]; max?: number[] }[];
  bufferViews: { buffer: number; byteOffset: number; byteLength: number }[];
  buffers: { byteLength: number; uri: string }[];
  meshes: { primitives: { attributes: Record<string, number>; indices: number; material: number }[] }[];
  materials: { alphaMode?: string }[];
}

function readGltf(file: string): { gltf: Gltf; bytes: Buffer } {
  const gltf = JSON.parse(readFileSync(join(MODELS, file), "utf8")) as Gltf;
  const uri = gltf.buffers[0]!.uri;
  return { gltf, bytes: Buffer.from(uri.slice(uri.indexOf(",") + 1), "base64") };
}

const COMPONENTS: Record<number, number> = { 5126: 4, 5123: 2, 5125: 4 };

function readFloats(bytes: Buffer, offset: number, count: number): Float32Array {
  const out = new Float32Array(count);
  for (let i = 0; i < count; i++) out[i] = bytes.readFloatLE(offset + 4 * i);
  return out;
}

function readIndices(bytes: Buffer, offset: number, count: number, componentType: number): number[] {
  return Array.from({ length: count }, (_, i) => (componentType === 5123 ? bytes.readUInt16LE(offset + 2 * i) : bytes.readUInt32LE(offset + 4 * i)));
}
const TYPES: Record<string, number> = { SCALAR: 1, VEC3: 3, VEC4: 4 };

describe("the landscape models", () => {
  it("are all in the shared models folder", () => {
    expect(EXPECTED_MODELS).toEqual(expect.arrayContaining(FOREST_MODELS));
    // Eight for the forest valley and seventeen more for the desert, alpine, island and autumn landscapes, plus four terrains
    expect(EXPECTED_MODELS.length).toBeGreaterThanOrEqual(29);
  });

  for (const file of EXPECTED_MODELS) {
    it(`${file} is a valid glTF 2.0 file with one lit, coloured mesh`, () => {
      const { gltf, bytes } = readGltf(file);
      expect(gltf.asset.version).toBe("2.0");
      expect(bytes.length).toBe(gltf.buffers[0]!.byteLength);
      const primitive = gltf.meshes[0]!.primitives[0]!;
      expect(Object.keys(primitive.attributes).sort()).toEqual(["COLOR_0", "NORMAL", "POSITION"]);
      // Every accessor lies inside its buffer view, which lies inside the buffer
      for (const accessor of gltf.accessors) {
        const view = gltf.bufferViews[accessor.bufferView]!;
        expect(view.byteOffset + view.byteLength).toBeLessThanOrEqual(bytes.length);
        expect(accessor.count * COMPONENTS[accessor.componentType]! * TYPES[accessor.type]!).toBeLessThanOrEqual(view.byteLength);
      }
      const position = gltf.accessors[primitive.attributes["POSITION"]!]!;
      const positions = readFloats(bytes, gltf.bufferViews[position.bufferView]!.byteOffset, position.count * 3);
      expect(positions.every(Number.isFinite)).toBe(true);
      // The declared bounds are the real ones (viewers rely on them)
      for (let k = 0; k < 3; k++) {
        const values = Array.from({ length: position.count }, (_, i) => positions[3 * i + k]!);
        expect(position.min![k]).toBeCloseTo(Math.min(...values), 4);
        expect(position.max![k]).toBeCloseTo(Math.max(...values), 4);
      }
      // Triangles only use vertices that exist
      const indexAccessor = gltf.accessors[primitive.indices]!;
      const indexView = gltf.bufferViews[indexAccessor.bufferView]!;
      const indices = readIndices(bytes, indexView.byteOffset, indexAccessor.count, indexAccessor.componentType);
      expect(indices.length % 3).toBe(0);
      expect(indices.length).toBeGreaterThan(0);
      expect(indices.every((i) => i < position.count)).toBe(true);
    });
  }

  it("have unit normals that point out of the surface (the lake faces up, the terrain mostly up)", () => {
    for (const file of ["lake.gltf", "terrain-hills.gltf"]) {
      const { gltf, bytes } = readGltf(file);
      const primitive = gltf.meshes[0]!.primitives[0]!;
      const accessor = gltf.accessors[primitive.attributes["NORMAL"]!]!;
      const normals = readFloats(bytes, gltf.bufferViews[accessor.bufferView]!.byteOffset, accessor.count * 3);
      let upward = 0;
      for (let i = 0; i < accessor.count; i++) {
        expect(Math.hypot(normals[3 * i]!, normals[3 * i + 1]!, normals[3 * i + 2]!)).toBeCloseTo(1, 3);
        if (normals[3 * i + 1]! > 0) upward++;
      }
      expect(upward / accessor.count).toBeGreaterThan(0.95);
    }
  });

  it("make the water see-through and nothing else", () => {
    for (const water of ["lake.gltf", "oasis.gltf", "ice.gltf", "ocean.gltf"]) expect(readGltf(water).gltf.materials[0]!.alphaMode, water).toBe("BLEND");
    for (const solid of ["rock.gltf", "mesa.gltf", "palm-tree.gltf", "hut.gltf"]) expect(readGltf(solid).gltf.materials[0]!.alphaMode, solid).toBeUndefined();
  });
});

describe("the landscape sample scene", () => {
  const scene = loadSample("landscape-3d.json");

  it("places only models that exist, with names that are unique", () => {
    const names = new Set<string>();
    let models = 0;
    for (const entity of scene.entities) {
      expect(names.has(entity.name), entity.name).toBe(false);
      names.add(entity.name);
      for (const component of entity.components) {
        if (component.type !== "ModelComponent") continue;
        models++;
        expect(existsSync(join(ASSETS, str(component.data, "model"))), str(component.data, "model")).toBe(true);
      }
    }
    expect(models).toBe(73);
    expect(scene.entities.some((e) => e.components.some((c) => c.type === "CameraComponent"))).toBe(true);
  });

  it("builds in the web player and asks for every model", () => {
    const engine = createHeadlessEngine();
    const built = buildScene(engine, scene);
    expect(built.unsupported.size).toBe(0);
    expect(built.pendingModels).toHaveLength(73);
    // The holder scales metres to scene units (100 per metre by default)
    const terrain = built.pendingModels.find((p) => p.entity.name === "Terrain")!;
    expect(terrain.holder.scaling.x).toBe(100);
    built.scene.dispose();
    engine.dispose();
  });

  it("loads a model's meshes under its entity, so it can be picked and outlined", async () => {
    const engine = createHeadlessEngine();
    const built = buildScene(engine, { ...scene, entities: scene.entities.filter((e) => e.name === "Cabin" || e.name === "Pine 1") });
    const failures = await loadPendingModels(built, (path) => {
      const bytes = readFileSync(join(ASSETS, path));
      return `data:model/gltf+json;base64,${bytes.toString("base64")}`;
    });
    expect(failures).toEqual([]);
    for (const pending of built.pendingModels) {
      const drawn = pending.entity.meshes.filter((m) => m.getTotalVertices() > 0);
      expect(drawn.length, pending.entity.name).toBeGreaterThan(0);
      expect(drawn.every((m) => m.metadata?.entityId === pending.entity.id)).toBe(true);
    }
    // The cabin is about 4 m wide and the holder makes it 100 units per metre
    const cabin = built.pendingModels.find((p) => p.entity.name === "Cabin")!;
    cabin.entity.node.computeWorldMatrix(true);
    const mesh = cabin.entity.meshes.find((m) => m.getTotalVertices() > 0)!;
    mesh.computeWorldMatrix(true);
    const width = mesh.getBoundingInfo().boundingBox.maximumWorld.x - mesh.getBoundingInfo().boundingBox.minimumWorld.x;
    expect(width).toBeGreaterThan(350);
    expect(width).toBeLessThan(550);
    built.scene.dispose();
    engine.dispose();
  }, 30000);

  it("reports a model that cannot be loaded instead of failing the scene", async () => {
    const engine = createHeadlessEngine();
    const built = buildScene(engine, { ...scene, entities: scene.entities.filter((e) => e.name === "Rock 1") });
    const failures = await loadPendingModels(built, () => "data:model/gltf+json;base64,e30=");
    expect(failures).toHaveLength(1);
    expect(failures[0]!.entityName).toBe("Rock 1");
    expect(built.modelFailures).toHaveLength(1);
    built.scene.dispose();
    engine.dispose();
  }, 30000);
});

describe("the other landscapes", () => {
  const landscapes = [
    { file: "landscape-desert-3d.json", title: "Desert Canyon", mustHave: ["Mesa 1", "Cactus 1", "Palm 1", "Oasis"] },
    { file: "landscape-alpine-3d.json", title: "Alpine Valley", mustHave: ["Snow Pine 1", "Ice", "Cabin", "Mountain 1"] },
    { file: "landscape-island-3d.json", title: "Tropical Island", mustHave: ["Palm 1", "Ocean", "Hut 1"] },
    { file: "landscape-autumn-3d.json", title: "Autumn Forest", mustHave: ["Red Oak 1", "Orange Oak 1", "Yellow Oak 1", "Pond", "Cabin"] },
  ];

  for (const { file, title, mustHave } of landscapes) {
    it(`${file} is a ${title} scene that builds and only uses models that exist`, () => {
      const scene = loadSample(file);
      expect(scene.name).toBe(title);
      const names = new Set(scene.entities.map((e) => e.name));
      expect(names.size).toBe(scene.entities.length);
      for (const name of mustHave) expect(names.has(name), name).toBe(true);
      let models = 0;
      for (const entity of scene.entities) {
        for (const component of entity.components) {
          if (component.type !== "ModelComponent") continue;
          models++;
          expect(existsSync(join(ASSETS, str(component.data, "model"))), str(component.data, "model")).toBe(true);
        }
      }
      expect(models).toBeGreaterThan(40);
      // A camera and a sun, and no script (these are views, not games)
      expect(scene.entities.some((e) => e.components.some((c) => c.type === "CameraComponent"))).toBe(true);
      expect(scene.entities.some((e) => e.components.some((c) => c.type === "DirectionalLightComponent"))).toBe(true);
      expect(scene.entities.some((e) => e.components.some((c) => c.type === "EntityScriptBindingComponent"))).toBe(false);
      const engine = createHeadlessEngine();
      const built = buildScene(engine, scene);
      expect(built.unsupported.size).toBe(0);
      expect(built.pendingModels).toHaveLength(models);
      built.scene.dispose();
      engine.dispose();
    });
  }

  it("stand every plant on the ground: its height matches the terrain mesh under it", () => {
    for (const file of ["landscape-3d.json", ...landscapes.map((l) => l.file)]) {
      const scene = loadSample(file);
      const terrainEntity = scene.entities.find((e) => e.name === "Terrain")!;
      const terrainPath = str(terrainEntity.components[0]!.data, "model");
      const { gltf, bytes } = readGltf(terrainPath.split("/").pop()!);
      const accessor = gltf.accessors[gltf.meshes[0]!.primitives[0]!.attributes["POSITION"]!]!;
      const positions = readFloats(bytes, gltf.bufferViews[accessor.bufferView]!.byteOffset, accessor.count * 3);
      const side = Math.round(Math.sqrt(accessor.count));
      const size = positions[3 * (side * side - 1)]! - positions[0]!;
      let checked = 0;
      for (const entity of scene.entities) {
        if (!/^(Palm|Cactus|Snow Pine|Pine|Red Oak|Orange Oak|Yellow Oak|Cabin|Hut)( \d+)?$/.test(entity.name)) continue;
        const x = entity.transform.position.x / 100;
        const z = entity.transform.position.z / 100;
        const i = Math.round(((x + size / 2) / size) * (side - 1));
        const j = Math.round(((z + size / 2) / size) * (side - 1));
        const ground = positions[3 * (j * side + i) + 1]!;
        // The nearest grid point is up to 0.3 m off horizontally, which on a steep slope is a fraction of a metre of height
        expect(Math.abs(entity.transform.position.y / 100 - ground), `${file} ${entity.name}`).toBeLessThan(1.0);
        checked++;
      }
      expect(checked, file).toBeGreaterThan(10);
    }
  });
});
