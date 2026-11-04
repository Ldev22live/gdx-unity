import type { ComponentData, EntityData } from "../scene/types";
import { cubeSphere, encodeMesh } from "../sculpt/SculptMesh";

/** A new sculpt starts as a sphere this big (scene units; the sample primitives are about 100 across). */
export const SCULPT_START_RADIUS = 60;
/** Quads per cube edge of the starting sphere: 6 * 48^2 + 2 = 13,826 vertices. */
export const SCULPT_START_RESOLUTION = 48;

let idCounter = 0;

/** A new entity id: a UUID where the browser has one, otherwise a counter (tests). */
export function newId(): string {
  const crypto = globalThis.crypto;
  return typeof crypto?.randomUUID === "function" ? crypto.randomUUID() : `entity-${++idCounter}`;
}

function component(type: string, data: Record<string, unknown>): ComponentData {
  return { type, enabled: true, data };
}

/** A new component of each type the editor can add, with the desktop editor's usual defaults. */
export const COMPONENT_TEMPLATES: Record<string, () => ComponentData> = {
  MeshPrimitiveComponent: () =>
    component("MeshPrimitiveComponent", { shape: "BOX", sizeX: 100, sizeY: 100, sizeZ: 100, colorR: 0.8, colorG: 0.8, colorB: 0.8, colorA: 1 }),
  SculptMeshComponent: () =>
    component("SculptMeshComponent", {
      ...encodeMesh(cubeSphere(SCULPT_START_RADIUS, SCULPT_START_RESOLUTION)),
      colorR: 0.78, colorG: 0.72, colorB: 0.66,
    }),
  ShapeRendererComponent: () =>
    component("ShapeRendererComponent", { width: 100, height: 100, shapeType: "FILLED", centered: true, color: { r: 1, g: 1, b: 1, a: 1 } }),
  TextComponent: () =>
    component("TextComponent", { text: "Text", colorR: 1, colorG: 1, colorB: 1, colorA: 1, scale: 1, anchor: "TOP_LEFT", offsetX: 0, offsetY: 0 }),
  CameraComponent: () =>
    component("CameraComponent", { projection: "PERSPECTIVE", orthographicHeight: 480, fieldOfView: 70, nearPlane: 1, farPlane: 50000 }),
  DirectionalLightComponent: () =>
    component("DirectionalLightComponent", {
      directionX: -0.4, directionY: -1, directionZ: -0.6, colorR: 1, colorG: 0.96, colorB: 0.88, colorA: 1, intensity: 1, castShadows: true,
    }),
  SkyLightComponent: () =>
    component("SkyLightComponent", { skyR: 0.62, skyG: 0.72, skyB: 0.88, groundR: 0.32, groundG: 0.29, groundB: 0.26, intensity: 0.6 }),
  ToneMappingComponent: () => component("ToneMappingComponent", { exposure: 1, operator: "ACES_FILMIC" }),
  RigidBody2DComponent: () =>
    component("RigidBody2DComponent", {
      bodyType: "DYNAMIC", gravityScale: 1, linearDamping: 0, angularDamping: 0.05, fixedRotation: false, bullet: false,
      initialLinearVelocityX: 0, initialLinearVelocityY: 0, initialAngularVelocity: 0,
    }),
  BoxCollider2DComponent: () =>
    component("BoxCollider2DComponent", {
      width: 100, height: 100, offsetX: 0, offsetY: 0, density: 1, friction: 0.4, restitution: 0.1, isSensor: false, categoryBits: 1, maskBits: 65535,
    }),
};

/** A function that builds an entity from a name, with the label the menu shows. */
export type EntityPreset = ((name: string) => EntityData) & { label: string };

function preset(label: string, components: string[]): EntityPreset {
  const make = ((name: string): EntityData => ({
    id: newId(),
    name,
    active: true,
    parentId: null,
    transform: { position: { x: 0, y: 0, z: 0 }, rotation: { x: 0, y: 0, z: 0, w: 1 }, scale: { x: 1, y: 1, z: 1 } },
    components: components.map((type) => COMPONENT_TEMPLATES[type]!()),
  })) as EntityPreset;
  make.label = label;
  return make;
}

/** The entities the Add menu offers. */
export const ENTITY_PRESETS: Record<string, EntityPreset> = {
  empty: preset("Empty", []),
  box: preset("Box", ["MeshPrimitiveComponent"]),
  sphere: (() => {
    const make = preset("Sphere", ["MeshPrimitiveComponent"]);
    const wrapped = ((name: string) => {
      const entity = make(name);
      entity.components[0]!.data["shape"] = "SPHERE";
      return entity;
    }) as EntityPreset;
    wrapped.label = "Sphere";
    return wrapped;
  })(),
  plane: (() => {
    const make = preset("Plane", ["MeshPrimitiveComponent"]);
    const wrapped = ((name: string) => {
      const entity = make(name);
      Object.assign(entity.components[0]!.data, { shape: "PLANE", sizeX: 1000, sizeY: 1, sizeZ: 1000 });
      return entity;
    }) as EntityPreset;
    wrapped.label = "Plane";
    return wrapped;
  })(),
  sculpt: preset("Sculpt Sphere", ["SculptMeshComponent"]),
  rectangle: preset("Rectangle", ["ShapeRendererComponent"]),
  text: preset("Text", ["TextComponent"]),
  camera: preset("Camera", ["CameraComponent"]),
  sun: preset("Sun", ["DirectionalLightComponent"]),
  skyLight: preset("Sky Light", ["SkyLightComponent", "ToneMappingComponent"]),
};
