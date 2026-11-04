import { describe, expect, it } from "vitest";
import { buildScene, updateSculptMesh } from "../src/runtime/SceneBuilder";
import { EditorModel, emptyScene } from "../src/editor/EditorModel";
import { COMPONENT_TEMPLATES, ENTITY_PRESETS } from "../src/editor/presets";
import { cubeSphere, decodeMesh, encodeMesh, SculptMesh } from "../src/sculpt/SculptMesh";
import { defaultBrush, falloffWeight, SculptStroke, type BrushType, type Symmetry } from "../src/sculpt/SculptStroke";
import { createHeadlessEngine } from "./support";

const NO_MIRROR: Symmetry = { x: false, y: false, z: false };

/** The top of a sphere of radius 1: where the point is and which way the surface faces. */
const TOP = [0, 1, 0];
const UP = [0, 1, 0];

function radiusOf(mesh: SculptMesh, v: number): number {
  return Math.hypot(mesh.positions[3 * v]!, mesh.positions[3 * v + 1]!, mesh.positions[3 * v + 2]!);
}

function topVertex(mesh: SculptMesh): number {
  let best = 0;
  for (let v = 1; v < mesh.vertexCount; v++) if (mesh.positions[3 * v + 1]! > mesh.positions[3 * best + 1]!) best = v;
  return best;
}

function stroke(mesh: SculptMesh, type: BrushType, subtract = false, symmetry = NO_MIRROR) {
  const s = new SculptStroke(mesh);
  const brush = { ...defaultBrush(0.5), strength: 1, subtract };
  s.begin(type, brush, symmetry, TOP, UP);
  return { s, delta: s.end() };
}

describe("cubeSphere", () => {
  it("has the vertex and triangle counts of a cube sphere, all on the sphere", () => {
    const mesh = cubeSphere(2, 8);
    expect(mesh.vertexCount).toBe(6 * 64 + 2);
    expect(mesh.triangleCount).toBe(12 * 64);
    for (let v = 0; v < mesh.vertexCount; v++) expect(radiusOf(mesh, v)).toBeCloseTo(2, 1);
  });

  it("faces outwards", () => {
    const mesh = cubeSphere(1, 6);
    for (let v = 0; v < mesh.vertexCount; v++) {
      const dot = mesh.normals[3 * v]! * mesh.positions[3 * v]! + mesh.normals[3 * v + 1]! * mesh.positions[3 * v + 1]! + mesh.normals[3 * v + 2]! * mesh.positions[3 * v + 2]!;
      expect(dot).toBeGreaterThan(0.9);
    }
  });

  it("rejects a resolution it cannot build", () => {
    expect(() => cubeSphere(1, 0)).toThrow(/resolution/);
    expect(() => cubeSphere(1, 1000)).toThrow(/resolution/);
    expect(() => cubeSphere(0, 4)).toThrow(/radius/);
  });
});

describe("SculptMesh", () => {
  it("rejects triangles that use a vertex that does not exist", () => {
    expect(() => new SculptMesh(new Float32Array(9), new Uint32Array([0, 1, 5]))).toThrow(/uses vertex 5/);
  });

  it("survives a round trip through the scene file encoding", () => {
    const mesh = cubeSphere(1, 5);
    const back = decodeMesh(encodeMesh(mesh));
    expect(Array.from(back.positions)).toEqual(Array.from(mesh.positions));
    expect(Array.from(back.triangles)).toEqual(Array.from(mesh.triangles));
  });

  it("updates normals around moved vertices the same as recomputing them all", () => {
    const mesh = cubeSphere(1, 6);
    const v = topVertex(mesh);
    mesh.positions[3 * v + 1]! += 0.3;
    mesh.recomputeNormalsAround([v]);
    const partial = Array.from(mesh.normals);
    mesh.recomputeNormals();
    partial.forEach((value, i) => expect(value).toBeCloseTo(mesh.normals[i]!, 5));
  });
});

describe("falloff", () => {
  it("is 1 at the centre, 0 at the edge and never grows with distance", () => {
    for (const falloff of ["SMOOTH", "LINEAR", "SHARP", "CONSTANT"] as const) {
      expect(falloffWeight(falloff, 0)).toBe(1);
      expect(falloffWeight(falloff, 1)).toBe(0);
      let previous = 1;
      for (let d = 0; d < 1; d += 0.05) {
        const w = falloffWeight(falloff, d);
        expect(w).toBeLessThanOrEqual(previous + 1e-9);
        previous = w;
      }
    }
  });
});

describe("SculptStroke", () => {
  it("draw raises the surface under the brush and subtract digs it", () => {
    const up = cubeSphere(1, 24);
    const v = topVertex(up);
    const before = radiusOf(up, v);
    stroke(up, "DRAW");
    expect(radiusOf(up, v)).toBeGreaterThan(before);

    const down = cubeSphere(1, 24);
    stroke(down, "DRAW", true);
    expect(radiusOf(down, topVertex(down))).toBeLessThan(before);
  });

  it("records what it touched so a stroke can be undone exactly", () => {
    const mesh = cubeSphere(1, 24);
    const original = Array.from(mesh.positions);
    const { delta } = stroke(mesh, "DRAW");
    expect(delta.indices.length).toBeGreaterThan(0);
    expect(delta.before.length).toBe(3 * delta.indices.length);
    mesh.restore(delta.indices, delta.before);
    expect(Array.from(mesh.positions)).toEqual(original);
  });

  it("does not touch vertices outside the radius", () => {
    const mesh = cubeSphere(1, 24);
    const original = Array.from(mesh.positions);
    stroke(mesh, "DRAW");
    for (let v = 0; v < mesh.vertexCount; v++) {
      const far = Math.hypot(original[3 * v]!, original[3 * v + 1]! - 1, original[3 * v + 2]!) > 0.5;
      if (far) expect(mesh.positions[3 * v + 1]).toBe(original[3 * v + 1]);
    }
  });

  it("smooth brings a spike back towards its neighbours", () => {
    const mesh = cubeSphere(1, 24);
    const v = topVertex(mesh);
    mesh.positions[3 * v + 1]! += 0.4;
    mesh.recomputeNormalsAround([v]);
    const spike = radiusOf(mesh, v);
    stroke(mesh, "SMOOTH");
    expect(radiusOf(mesh, v)).toBeLessThan(spike);
  });

  it("pinch lifts the surface and subtract lowers it", () => {
    const lifted = cubeSphere(1, 24);
    stroke(lifted, "PINCH");
    expect(radiusOf(lifted, topVertex(lifted))).toBeGreaterThan(1.0001);
    const lowered = cubeSphere(1, 24);
    stroke(lowered, "PINCH", true);
    expect(radiusOf(lowered, topVertex(lowered))).toBeLessThan(1.0001);
  });

  it("mirrors a dab across the chosen plane", () => {
    const mesh = cubeSphere(1, 24);
    const s = new SculptStroke(mesh);
    // A dab on the +X side, mirrored across X, must also raise the -X side
    s.begin("DRAW", { ...defaultBrush(0.5), strength: 1 }, { x: true, y: false, z: false }, [1, 0, 0], [1, 0, 0]);
    s.end();
    const plusX = Math.max(...Array.from({ length: mesh.vertexCount }, (_, v) => mesh.positions[3 * v]!));
    const minusX = Math.min(...Array.from({ length: mesh.vertexCount }, (_, v) => mesh.positions[3 * v]!));
    expect(plusX).toBeGreaterThan(1.001);
    expect(-minusX).toBeCloseTo(plusX, 4);
  });

  it("places dabs at even spacing however the cursor moved", () => {
    const run = (steps: number) => {
      const mesh = cubeSphere(1, 24);
      const s = new SculptStroke(mesh);
      s.begin("DRAW", { ...defaultBrush(0.2), spacing: 0.5 }, NO_MIRROR, [0.8, 0.6, 0], [0.8, 0.6, 0]);
      for (let i = 1; i <= steps; i++) {
        const a = 0.9273 - (0.9273 * i) / steps; // angle from +Y, sweeping 0.6 .. 0
        s.move([Math.sin(a + 0.0), Math.cos(a), 0], [Math.sin(a), Math.cos(a), 0]);
      }
      return s.dabCount;
    };
    expect(Math.abs(run(3) - run(60))).toBeLessThanOrEqual(1);
  });

  it("grab carries the surface along with the cursor", () => {
    const mesh = cubeSphere(1, 24);
    const v = topVertex(mesh);
    const s = new SculptStroke(mesh);
    s.begin("GRAB", defaultBrush(0.4), NO_MIRROR, TOP, UP);
    s.move([0, 1.5, 0], UP);
    s.end();
    expect(mesh.positions[3 * v + 1]).toBeGreaterThan(1.4);
  });

  it("cancel puts the surface back", () => {
    const mesh = cubeSphere(1, 24);
    const original = Array.from(mesh.positions);
    const s = new SculptStroke(mesh);
    s.begin("DRAW", { ...defaultBrush(0.5), strength: 1 }, NO_MIRROR, TOP, UP);
    s.cancel();
    expect(Array.from(mesh.positions)).toEqual(original);
    expect(s.isActive).toBe(false);
  });

  it("refuses a second begin and a move before begin", () => {
    const s = new SculptStroke(cubeSphere(1, 6));
    expect(() => s.move(TOP, UP)).toThrow(/No stroke/);
    s.begin("DRAW", defaultBrush(0.5), NO_MIRROR, TOP, UP);
    expect(() => s.begin("DRAW", defaultBrush(0.5), NO_MIRROR, TOP, UP)).toThrow(/already/);
  });

  it("rejects a point that is not finite", () => {
    const s = new SculptStroke(cubeSphere(1, 6));
    expect(() => s.begin("DRAW", defaultBrush(0.5), NO_MIRROR, [Number.NaN, 0, 0], UP)).toThrow(/finite/);
  });
});

describe("sculpt components", () => {
  it("builds a Babylon mesh from a sculpt and reflects a stroke after updateSculptMesh", () => {
    const model = new EditorModel(emptyScene());
    const id = model.addEntity("sculpt");
    const built = buildScene(createHeadlessEngine(), model.file);
    const binding = built.sculpts.get(id);
    expect(binding).toBeDefined();
    expect(built.unsupported.size).toBe(0);
    const top = topVertex(binding!.data);
    const before = binding!.mesh.getVerticesData("position")![3 * top + 1]!;
    const s = new SculptStroke(binding!.data);
    s.onMoved = () => updateSculptMesh(binding!);
    s.begin("DRAW", { ...defaultBrush(30), strength: 1 }, NO_MIRROR, [0, 60, 0], UP);
    s.end();
    expect(binding!.mesh.getVerticesData("position")![3 * top + 1]).toBeGreaterThan(before);
    expect(binding!.mesh.getBoundingInfo().boundingBox.maximum.y).toBeGreaterThan(60);
  });

  it("stores a stroke as one undo step that restores the geometry", () => {
    const model = new EditorModel(emptyScene());
    const id = model.addEntity("sculpt");
    const original = model.require(id).components[0]!.data["positions"];
    const mesh = decodeMesh({ positions: original as string, triangles: model.require(id).components[0]!.data["triangles"] as string });
    const s = new SculptStroke(mesh);
    s.begin("DRAW", { ...defaultBrush(30), strength: 1 }, NO_MIRROR, [0, 60, 0], UP);
    s.end();
    const kinds: string[] = [];
    model.addListener((kind) => kinds.push(kind));
    model.setSculptGeometry(id, encodeMesh(mesh), "Sculpt Draw");
    expect(kinds).toContain("sculpt");
    expect(kinds).not.toContain("scene");
    expect(model.undoLabel).toBe("Sculpt Draw");
    expect(model.require(id).components[0]!.data["positions"]).not.toBe(original);
    model.undo();
    expect(model.require(id).components[0]!.data["positions"]).toBe(original);
  });

  it("refuses to store geometry on an entity without a sculpt", () => {
    const model = new EditorModel(emptyScene());
    const id = model.addEntity("box");
    expect(() => model.setSculptGeometry(id, { positions: "", triangles: "" })).toThrow(/no sculpt/);
  });

  it("is offered as a preset and as a component", () => {
    expect(ENTITY_PRESETS["sculpt"]).toBeDefined();
    expect(COMPONENT_TEMPLATES["SculptMeshComponent"]!().type).toBe("SculptMeshComponent");
  });
});
