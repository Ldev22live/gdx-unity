import {
  AbstractEngine,
  ArcRotateCamera,
  Camera,
  Color3,
  Color4,
  DirectionalLight,
  HemisphericLight,
  ImageProcessingConfiguration,
  Mesh,
  MeshBuilder,
  Quaternion,
  Scene,
  ShadowGenerator,
  StandardMaterial,
  TransformNode,
  UniversalCamera,
  Vector3,
  VertexBuffer,
  VertexData,
} from "@babylonjs/core";
import { GameEntity, GameScene, type ColorLike, type EntityFactory } from "./GameEntity";
import { bool, num, str, type ColorData, type ComponentData, type SceneFile } from "../scene/types";
import { decodeMesh, type SculptMesh } from "../sculpt/SculptMesh";

/** Component types this player draws or simulates. Anything else in a scene file is reported, not silently dropped. */
export const SUPPORTED_COMPONENTS: ReadonlySet<string> = new Set([
  "MeshPrimitiveComponent",
  "SculptMeshComponent",
  "ShapeRendererComponent",
  "TextComponent",
  "CameraComponent",
  "DirectionalLightComponent",
  "SkyLightComponent",
  "ToneMappingComponent",
  "RigidBody2DComponent",
  "BoxCollider2DComponent",
  "EntityScriptBindingComponent",
]);

/** Distance an orthographic game camera is pulled back along its view axis, so the z = 0 plane is in front of it. */
export const ORTHO_BACK_OFF = 1000;
/** Spacing in depth between flat shapes drawn at the same z, so later entities draw over earlier ones. */
const SHAPE_LAYER_STEP = 0.01;
const SHADOW_MAP_SIZE = 2048;
/** Shadow depth bias and normal bias; scenes are hundreds of units wide, so the defaults for small scenes show acne. */
const SHADOW_BIAS = 0.001;
const SHADOW_NORMAL_BIAS = 0.02;
/** Clear colour behind scenes that have no sky light. */
const BACKGROUND = new Color4(0.08, 0.09, 0.12, 1);

/** A text component placed on screen. {@code data.text} is read every frame, so scripts change it freely. */
export interface TextItem {
  entity: GameEntity;
  data: Record<string, unknown>;
}

/** The scene's own camera, driven by the transform of the entity that carries the Camera component. */
export class GameCamera {
  constructor(
    readonly entity: GameEntity,
    readonly camera: UniversalCamera,
    readonly orthographic: boolean,
    private readonly orthoHeight: number,
  ) {}

  /** Moves the Babylon camera to where the camera entity is now, and fits an orthographic view to the aspect ratio. */
  sync(aspect: number): void {
    const node = this.entity.node;
    node.computeWorldMatrix(true);
    const scale = new Vector3();
    const rotation = new Quaternion();
    const position = new Vector3();
    node.getWorldMatrix().decompose(scale, rotation, position);
    if (this.orthographic) {
      // The camera looks along its -Z; step back along +Z so the plane the 2D scene lives on is in front of it
      position.addInPlace(new Vector3(0, 0, ORTHO_BACK_OFF).applyRotationQuaternion(rotation));
      const halfHeight = this.orthoHeight / 2;
      this.camera.orthoTop = halfHeight;
      this.camera.orthoBottom = -halfHeight;
      this.camera.orthoLeft = -halfHeight * aspect;
      this.camera.orthoRight = halfHeight * aspect;
    }
    this.camera.position.copyFrom(position);
    this.camera.rotationQuaternion = rotation;
  }
}

/** A sculpt in the scene: the mesh data brushes edit, and the Babylon mesh drawn from it. */
export interface SculptBinding {
  entity: GameEntity;
  data: SculptMesh;
  mesh: Mesh;
}

export interface BuiltScene {
  scene: Scene;
  game: GameScene;
  /** The camera entity's camera, or null when the scene has none (the viewer camera is used then). */
  gameCamera: GameCamera | null;
  /** Orbit camera for scenes without a camera component. */
  viewerCamera: ArcRotateCamera;
  texts: TextItem[];
  /** The sculpts in the scene by entity id: the editable mesh data and the Babylon mesh that shows it. */
  sculpts: Map<string, SculptBinding>;
  /** Component types in the file that the player does not support, with how many components have each. */
  unsupported: Map<string, number>;
}

/** Builds a Babylon scene and the entity tree from a scene file. Needs an engine, but not a canvas (tests use NullEngine). */
export function buildScene(engine: AbstractEngine, file: SceneFile): BuiltScene {
  const scene = new Scene(engine);
  // The desktop engine is right-handed with Y up and cameras looking along -Z; match it so files need no conversion
  scene.useRightHandedSystem = true;
  scene.clearColor = BACKGROUND;

  const entities = file.entities.map((data) => {
    const node = new TransformNode(data.name, scene);
    node.position.set(data.transform.position.x, data.transform.position.y, data.transform.position.z);
    const r = data.transform.rotation;
    node.rotationQuaternion = new Quaternion(r.x, r.y, r.z, r.w);
    node.scaling.set(data.transform.scale.x, data.transform.scale.y, data.transform.scale.z);
    const entity = new GameEntity(data, node);
    node.setEnabled(data.active);
    return entity;
  });
  const game = new GameScene(entities);
  for (const [index, data] of file.entities.entries()) {
    if (data.parentId === null) continue;
    const parent = game.getById(data.parentId);
    const entity = entities[index]!;
    if (!parent) throw new Error(`Entity '${data.name}' has parentId ${data.parentId}, which is not in the scene`);
    entity.parent = parent;
    parent.children.push(entity);
    entity.node.parent = parent.node;
  }

  const builtMeshes: Mesh[] = [];
  const texts: TextItem[] = [];
  const sculpts = new Map<string, SculptBinding>();
  const unsupported = new Map<string, number>();
  const shadowLights: DirectionalLight[] = [];
  let gameCamera: GameCamera | null = null;
  let hasLight = false;
  let skyColor: Color3 | null = null;

  for (const [index, entity] of entities.entries()) {
    for (const component of entity.components) {
      if (!component.enabled) continue;
      if (!SUPPORTED_COMPONENTS.has(component.type)) {
        unsupported.set(component.type, (unsupported.get(component.type) ?? 0) + 1);
        continue;
      }
      switch (component.type) {
        case "MeshPrimitiveComponent":
          builtMeshes.push(createPrimitive(scene, entity, component));
          break;
        case "SculptMeshComponent": {
          const binding = createSculpt(scene, entity, component);
          sculpts.set(entity.id, binding);
          builtMeshes.push(binding.mesh);
          break;
        }
        case "ShapeRendererComponent":
          createShape(scene, entity, component, index);
          break;
        case "TextComponent":
          texts.push({ entity, data: component.data });
          break;
        case "CameraComponent":
          gameCamera ??= createGameCamera(scene, entity, component);
          break;
        case "DirectionalLightComponent": {
          const light = createDirectionalLight(scene, entity, component);
          hasLight = true;
          if (bool(component.data, "castShadows", false)) shadowLights.push(light);
          break;
        }
        case "SkyLightComponent":
          skyColor = createSkyLight(scene, entity, component);
          hasLight = true;
          break;
        case "ToneMappingComponent":
          applyToneMapping(scene, component);
          break;
        default:
          break; // physics and scripts are started by the player, not drawn
      }
    }
  }

  if (!hasLight && builtMeshes.length > 0) {
    // A 3D scene with no light of its own would render black; light it the way an editor viewport does
    new HemisphericLight("viewer light", new Vector3(0.3, 1, 0.2), scene).intensity = 1;
  }
  if (skyColor) {
    const sky: Color3 = skyColor;
    scene.clearColor = new Color4(sky.r, sky.g, sky.b, 1);
  }
  const generators: ShadowGenerator[] = [];
  for (const light of shadowLights) {
    const generator = new ShadowGenerator(SHADOW_MAP_SIZE, light);
    generators.push(generator);
    generator.usePercentageCloserFiltering = true;
    generator.bias = SHADOW_BIAS;
    generator.normalBias = SHADOW_NORMAL_BIAS;
    for (const mesh of builtMeshes) {
      generator.addShadowCaster(mesh);
      mesh.receiveShadows = true;
    }
  }

  game.attachFactory(createEntityFactory(scene, generators));
  const viewerCamera = createViewerCamera(scene, builtMeshes);
  const own: GameCamera | null = gameCamera;
  scene.activeCamera = own ? own.camera : viewerCamera;
  return { scene, game, gameCamera: own, viewerCamera, texts, sculpts, unsupported };
}

let runtimeEntityCounter = 0;

/** Lets scripts add entities, meshes and shapes while the game runs; new meshes join the scene's shadows. */
function createEntityFactory(scene: Scene, generators: ShadowGenerator[]): EntityFactory {
  return {
    createEntity(name: string): GameEntity {
      const node = new TransformNode(name, scene);
      node.rotationQuaternion = Quaternion.Identity();
      return new GameEntity(
        {
          id: `runtime-${++runtimeEntityCounter}`,
          name,
          active: true,
          parentId: null,
          transform: { position: { x: 0, y: 0, z: 0 }, rotation: { x: 0, y: 0, z: 0, w: 1 }, scale: { x: 1, y: 1, z: 1 } },
          components: [],
        },
        node,
      );
    },
    addMeshPrimitive(entity: GameEntity, shape: string, size: Vector3, color: ColorLike): void {
      const component: ComponentData = {
        type: "MeshPrimitiveComponent",
        enabled: true,
        data: { shape, sizeX: size.x, sizeY: size.y, sizeZ: size.z, colorR: color.r, colorG: color.g, colorB: color.b, colorA: color.a ?? 1 },
      };
      entity.components.push(component);
      const mesh = createPrimitive(scene, entity, component);
      for (const generator of generators) generator.addShadowCaster(mesh);
      mesh.receiveShadows = generators.length > 0;
    },
    addShapeRenderer(entity: GameEntity, width: number, height: number, color: ColorLike): void {
      const component: ComponentData = {
        type: "ShapeRendererComponent",
        enabled: true,
        data: { width, height, shapeType: "FILLED", centered: true, color: { r: color.r, g: color.g, b: color.b, a: color.a ?? 1 } },
      };
      entity.components.push(component);
      createShape(scene, entity, component, scene.transformNodes.length);
    },
  };
}

function createPrimitive(scene: Scene, entity: GameEntity, component: ComponentData): Mesh {
  const d = component.data;
  const sx = num(d, "sizeX");
  const sy = num(d, "sizeY");
  const sz = num(d, "sizeZ");
  const shape = str(d, "shape");
  let mesh: Mesh;
  switch (shape) {
    case "BOX":
      mesh = MeshBuilder.CreateBox(entity.name, { width: sx, height: sy, depth: sz }, scene);
      break;
    case "SPHERE":
      mesh = MeshBuilder.CreateSphere(entity.name, { diameterX: sx, diameterY: sy, diameterZ: sz, segments: 24 }, scene);
      break;
    case "PLANE":
      // A flat sheet in the XZ plane facing up; its Y size is only thickness in the file
      mesh = MeshBuilder.CreateGround(entity.name, { width: sx, height: sz }, scene);
      break;
    default:
      throw new Error(`Entity '${entity.name}': unknown MeshPrimitive shape '${shape}'`);
  }
  const material = new StandardMaterial(`${entity.name} material`, scene);
  material.diffuseColor = new Color3(num(d, "colorR"), num(d, "colorG"), num(d, "colorB"));
  material.specularColor = new Color3(0.08, 0.08, 0.08);
  material.alpha = num(d, "colorA", 1);
  mesh.material = material;
  mesh.parent = entity.node;
  mesh.metadata = { entityId: entity.id };
  entity.meshes.push(mesh);
  return mesh;
}

/** Copies a sculpt's positions and normals into the Babylon mesh; called after every brush dab. */
export function updateSculptMesh(binding: SculptBinding): void {
  binding.mesh.updateVerticesData(VertexBuffer.PositionKind, binding.data.positions);
  binding.mesh.updateVerticesData(VertexBuffer.NormalKind, binding.data.normals);
  binding.mesh.refreshBoundingInfo();
}

function createSculpt(scene: Scene, entity: GameEntity, component: ComponentData): SculptBinding {
  const d = component.data;
  const data = decodeMesh({ positions: str(d, "positions"), triangles: str(d, "triangles") });
  const mesh = new Mesh(entity.name, scene);
  const vertexData = new VertexData();
  vertexData.positions = data.positions;
  vertexData.indices = data.triangles;
  vertexData.normals = data.normals;
  vertexData.applyToMesh(mesh, true);
  const material = new StandardMaterial(`${entity.name} material`, scene);
  material.diffuseColor = new Color3(num(d, "colorR", 0.75), num(d, "colorG", 0.75), num(d, "colorB", 0.75));
  material.specularColor = new Color3(0.15, 0.15, 0.15);
  mesh.material = material;
  mesh.parent = entity.node;
  mesh.metadata = { entityId: entity.id };
  entity.meshes.push(mesh);
  return { entity, data, mesh };
}

function createShape(scene: Scene, entity: GameEntity, component: ComponentData, order: number): void {
  const d = component.data;
  const width = num(d, "width");
  const height = num(d, "height");
  const colorData = d["color"] as ColorData | undefined;
  if (!colorData) throw new Error(`Entity '${entity.name}': ShapeRenderer has no color`);
  const mesh = MeshBuilder.CreatePlane(entity.name, { width, height, sideOrientation: Mesh.DOUBLESIDE }, scene);
  const material = new StandardMaterial(`${entity.name} material`, scene);
  material.disableLighting = true;
  material.emissiveColor = new Color3(colorData.r, colorData.g, colorData.b);
  material.alpha = colorData.a;
  mesh.material = material;
  mesh.parent = entity.node;
  mesh.metadata = { entityId: entity.id };
  entity.meshes.push(mesh);
  // "centered" false means the shape grows right and up from the entity's position
  const centered = bool(d, "centered", true);
  mesh.position.set(centered ? 0 : width / 2, centered ? 0 : height / 2, order * SHAPE_LAYER_STEP);
}

function createGameCamera(scene: Scene, entity: GameEntity, component: ComponentData): GameCamera {
  const d = component.data;
  const camera = new UniversalCamera(entity.name, Vector3.Zero(), scene);
  camera.rotationQuaternion = new Quaternion();
  camera.minZ = num(d, "nearPlane", 1);
  camera.maxZ = num(d, "farPlane", 50000);
  const orthographic = str(d, "projection") === "ORTHOGRAPHIC";
  if (orthographic) {
    camera.mode = Camera.ORTHOGRAPHIC_CAMERA;
    // The camera sits ORTHO_BACK_OFF away from its entity, so the near plane must start closer than that
    camera.minZ = 0.1;
    camera.maxZ = Math.max(camera.maxZ, ORTHO_BACK_OFF * 10);
  } else {
    camera.fov = (num(d, "fieldOfView", 60) * Math.PI) / 180;
  }
  return new GameCamera(entity, camera, orthographic, num(d, "orthographicHeight", 480));
}

function createDirectionalLight(scene: Scene, entity: GameEntity, component: ComponentData): DirectionalLight {
  const d = component.data;
  const direction = new Vector3(num(d, "directionX"), num(d, "directionY"), num(d, "directionZ"));
  const light = new DirectionalLight(entity.name, direction.normalize(), scene);
  light.diffuse = new Color3(num(d, "colorR"), num(d, "colorG"), num(d, "colorB"));
  light.specular = light.diffuse.scale(0.4);
  light.intensity = num(d, "intensity", 1);
  light.position = entity.getWorldPosition();
  return light;
}

function createSkyLight(scene: Scene, entity: GameEntity, component: ComponentData): Color3 {
  const d = component.data;
  const light = new HemisphericLight(entity.name, new Vector3(0, 1, 0), scene);
  const sky = new Color3(num(d, "skyR"), num(d, "skyG"), num(d, "skyB"));
  light.diffuse = sky;
  light.groundColor = new Color3(num(d, "groundR"), num(d, "groundG"), num(d, "groundB"));
  light.specular = Color3.Black();
  light.intensity = num(d, "intensity", 1);
  return sky;
}

function applyToneMapping(scene: Scene, component: ComponentData): void {
  const config = scene.imageProcessingConfiguration;
  const operator = str(component.data, "operator");
  config.toneMappingEnabled = true;
  config.exposure = num(component.data, "exposure", 1);
  switch (operator) {
    case "ACES_FILMIC":
      config.toneMappingType = ImageProcessingConfiguration.TONEMAPPING_ACES;
      break;
    default:
      throw new Error(`Unknown tone mapping operator '${operator}'`);
  }
}

/** An orbit camera that frames everything drawn, for scenes that bring no camera of their own. */
function createViewerCamera(scene: Scene, meshes: Mesh[]): ArcRotateCamera {
  const min = new Vector3(Infinity, Infinity, Infinity);
  const max = new Vector3(-Infinity, -Infinity, -Infinity);
  for (const mesh of meshes) {
    mesh.computeWorldMatrix(true);
    const box = mesh.getBoundingInfo().boundingBox;
    min.minimizeInPlace(box.minimumWorld);
    max.maximizeInPlace(box.maximumWorld);
  }
  const hasBounds = Number.isFinite(min.x);
  const center = hasBounds ? min.add(max).scale(0.5) : Vector3.Zero();
  const radius = hasBounds ? Math.max(Vector3.Distance(min, max), 100) : 500;
  const camera = new ArcRotateCamera("viewer", -Math.PI / 2, Math.PI / 3, radius, center, scene);
  camera.minZ = 1;
  camera.maxZ = radius * 20;
  camera.wheelPrecision = 50 / Math.max(radius / 500, 0.1);
  camera.lowerRadiusLimit = 1;
  return camera;
}
