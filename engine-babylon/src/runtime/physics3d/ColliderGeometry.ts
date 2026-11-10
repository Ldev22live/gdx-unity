import { type AbstractMesh, Matrix, Quaternion, Vector3, VertexBuffer } from "@babylonjs/core";
import type { GameEntity } from "../GameEntity";
import { METERS_PER_UNIT } from "./types";

/** A mesh in meters, in the entity's own frame (its rotation and position removed, its scale applied). */
export interface MeshGeometry {
  /** x, y, z per vertex. */
  points: Float32Array;
  /** Three indices per triangle. */
  indices: Uint32Array;
}

/**
 * What a hull or mesh collider is built from: ready, still loading (try again on a later update) or impossible (with the reason,
 * which names what to change).
 */
export type GeometryResult = { state: "ready"; geometry: MeshGeometry } | { state: "pending" } | { state: "failed"; reason: string };

/** Where {@link Physics3D} gets the triangles of an entity for its hull and mesh colliders; the web counterpart of `ColliderGeometrySource`. */
export interface ColliderGeometrySource {
  geometryOf(entity: GameEntity): GeometryResult;
}

/**
 * Reads the triangles of an entity from the meshes under its node: the primitive's mesh, or the meshes of its glTF model once
 * loaded (the holder's import scale is part of the world matrix, so it is applied). The pose the meshes have when the body is
 * made is used (the rest pose; animation does not move a collider). Models load after the scene is built, so an entity that has a
 * Model component and no mesh yet is reported as pending.
 */
export class MeshColliderGeometry implements ColliderGeometrySource {
  /**
   * @param isModelFailed whether the entity's model failed to load, so waiting for it is pointless
   */
  constructor(private readonly isModelFailed: (entity: GameEntity) => boolean = () => false) {}

  geometryOf(entity: GameEntity): GeometryResult {
    const hasModel = entity.getComponents("ModelComponent").length > 0;
    const meshes = entity.meshes.filter((mesh) => mesh.getTotalVertices() > 0);
    if (meshes.length === 0) {
      if (hasModel && !this.isModelFailed(entity)) return { state: "pending" };
      return {
        state: "failed",
        reason: hasModel ? "its model could not be loaded" : "it has no Model or Mesh Primitive to take the shape from",
      };
    }
    // glTF triangles wind counter-clockwise as seen from outside; Babylon's own primitives wind the other way in this right-handed scene,
    // and Jolt's mesh shape only has a front face, so a primitive's triangles are turned around
    return { state: "ready", geometry: readMeshes(entity, meshes, !hasModel) };
  }
}

function readMeshes(entity: GameEntity, meshes: AbstractMesh[], flipWinding: boolean): MeshGeometry {
  entity.node.computeWorldMatrix(true);
  const origin = entity.node.getAbsolutePosition();
  const rotation = new Quaternion();
  entity.node.getWorldMatrix().decompose(new Vector3(), rotation, new Vector3());
  const inverse = Quaternion.Inverse(rotation);
  const inverseMatrix = new Matrix();
  inverse.toRotationMatrix(inverseMatrix);
  const points: number[] = [];
  const indices: number[] = [];
  const world = new Vector3();
  const local = new Vector3();
  for (const mesh of meshes) {
    mesh.computeWorldMatrix(true);
    const positions = mesh.getVerticesData(VertexBuffer.PositionKind);
    const triangles = mesh.getIndices();
    if (!positions || !triangles) continue;
    const base = points.length / 3;
    const matrix = mesh.getWorldMatrix();
    // A mirrored mesh (a negative scale) turns its triangles inside out as well
    const flip = flipWinding !== matrix.determinant() < 0;
    for (let i = 0; i + 2 < positions.length; i += 3) {
      Vector3.TransformCoordinatesFromFloatsToRef(positions[i]!, positions[i + 1]!, positions[i + 2]!, matrix, world);
      world.subtractInPlace(origin);
      Vector3.TransformCoordinatesToRef(world, inverseMatrix, local);
      points.push(local.x * METERS_PER_UNIT, local.y * METERS_PER_UNIT, local.z * METERS_PER_UNIT);
    }
    for (let i = 0; i + 2 < triangles.length; i += 3) {
      const a = triangles[i]! + base;
      const b = triangles[i + 1]! + base;
      const c = triangles[i + 2]! + base;
      if (flip) indices.push(a, c, b);
      else indices.push(a, b, c);
    }
  }
  return { points: Float32Array.from(points), indices: Uint32Array.from(indices) };
}
