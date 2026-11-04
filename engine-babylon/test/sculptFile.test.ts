import { describe, expect, it } from "vitest";
import { EditorModel, emptyScene } from "../src/editor/EditorModel";
import { cubeSphere, decodeMesh, encodeMesh, SculptMesh } from "../src/sculpt/SculptMesh";
import { readSculptFile, subdivide, writeSculptFile } from "../src/sculpt/sculptFile";

describe("subdivide", () => {
  it("quadruples the triangles, keeps the original vertices, and adds one vertex per edge", () => {
    const mesh = cubeSphere(1, 4);
    const finer = subdivide(mesh, 1);
    expect(finer.triangleCount).toBe(4 * mesh.triangleCount);
    // A closed triangle mesh has 3T/2 edges
    expect(finer.vertexCount).toBe(mesh.vertexCount + (3 * mesh.triangleCount) / 2);
    expect(Array.from(finer.positions.subarray(0, mesh.positions.length))).toEqual(Array.from(mesh.positions));
  });

  it("does not change the shape: new vertices lie on the old edges", () => {
    const mesh = new SculptMesh(Float32Array.of(0, 0, 0, 2, 0, 0, 0, 2, 0), Uint32Array.of(0, 1, 2));
    const finer = subdivide(mesh, 1);
    expect(finer.vertexCount).toBe(6);
    expect(Array.from(finer.positions.subarray(9))).toEqual([1, 0, 0, 1, 1, 0, 0, 1, 0]);
  });

  it("keeps the surface facing the same way", () => {
    const finer = subdivide(cubeSphere(1, 3), 1);
    for (let v = 0; v < finer.vertexCount; v++) {
      const dot = finer.normals[3 * v]! * finer.positions[3 * v]! + finer.normals[3 * v + 1]! * finer.positions[3 * v + 1]! + finer.normals[3 * v + 2]! * finer.positions[3 * v + 2]!;
      expect(dot).toBeGreaterThan(0);
    }
  });

  it("refuses a result bigger than the editor handles, and bad levels", () => {
    expect(() => subdivide(cubeSphere(1, 48), 1)).not.toThrow();
    expect(() => subdivide(cubeSphere(1, 100), 1)).toThrow(/at most 100000/);
    expect(() => subdivide(cubeSphere(1, 3), 0)).toThrow(/levels/);
    expect(() => subdivide(cubeSphere(1, 3), 9)).toThrow(/levels/);
  });
});

describe(".sculpt files", () => {
  it("writes the desktop's layout: GSCM, version 1, counts, floats, ints (little-endian)", () => {
    const mesh = new SculptMesh(Float32Array.of(0, 0, 0, 1, 0, 0, 0, 1, 0), Uint32Array.of(0, 1, 2));
    const bytes = writeSculptFile(mesh);
    const view = new DataView(bytes.buffer);
    expect(String.fromCharCode(...bytes.subarray(0, 4))).toBe("GSCM");
    expect(view.getInt32(4, true)).toBe(1);
    expect(view.getInt32(8, true)).toBe(3);
    expect(view.getInt32(12, true)).toBe(1);
    expect(view.getFloat32(16 + 4 * 3, true)).toBe(1);
    expect(view.getInt32(16 + 36 + 8, true)).toBe(2);
    expect(bytes.length).toBe(16 + 36 + 12);
  });

  it("round-trips a mesh exactly", () => {
    const mesh = cubeSphere(2, 6);
    const back = readSculptFile(writeSculptFile(mesh));
    expect(Array.from(back.positions)).toEqual(Array.from(mesh.positions));
    expect(Array.from(back.triangles)).toEqual(Array.from(mesh.triangles));
  });

  it("rejects what is not a sculpt, with the reason", () => {
    expect(() => readSculptFile(new Uint8Array(4))).toThrow(/truncated/);
    const wrong = writeSculptFile(cubeSphere(1, 2));
    wrong[0] = 0;
    expect(() => readSculptFile(wrong)).toThrow(/signature/);
    const version = writeSculptFile(cubeSphere(1, 2));
    new DataView(version.buffer).setInt32(4, 2, true);
    expect(() => readSculptFile(version)).toThrow(/version 2/);
    expect(() => readSculptFile(writeSculptFile(cubeSphere(1, 2)).subarray(0, 60))).toThrow(/truncated/);
    const huge = writeSculptFile(cubeSphere(1, 2));
    new DataView(huge.buffer).setInt32(8, 500000, true);
    expect(() => readSculptFile(huge)).toThrow(/at most 100000/);
    const badIndex = writeSculptFile(new SculptMesh(Float32Array.of(0, 0, 0, 1, 0, 0, 0, 1, 0), Uint32Array.of(0, 1, 2)));
    new DataView(badIndex.buffer).setInt32(16 + 36, 9, true);
    expect(() => readSculptFile(badIndex)).toThrow(/uses vertex 9/);
  });
});

describe("replaceSculptGeometry", () => {
  it("swaps the geometry as one undoable step and asks the view to rebuild", () => {
    const model = new EditorModel(emptyScene());
    const id = model.addEntity("sculpt");
    const original = model.require(id).components[0]!.data["positions"] as string;
    const kinds: string[] = [];
    model.addListener((k) => kinds.push(k));
    model.replaceSculptGeometry(id, encodeMesh(subdivide(decodeMesh({ positions: original, triangles: model.require(id).components[0]!.data["triangles"] as string }), 1)), "Subdivide sculpt");
    expect(kinds).toContain("scene");
    expect(model.undoLabel).toBe("Subdivide sculpt");
    model.undo();
    expect(model.require(id).components[0]!.data["positions"]).toBe(original);
  });
});
