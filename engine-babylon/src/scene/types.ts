/** The scene file format written by the desktop editor (`schemaVersion` 1), as it appears in `assets/samples/*.json`. */

export interface Vec3Data { x: number; y: number; z: number }
export interface QuatData { x: number; y: number; z: number; w: number }
export interface ColorData { r: number; g: number; b: number; a: number }

export interface TransformData {
  position: Vec3Data;
  rotation: QuatData;
  scale: Vec3Data;
}

export interface ComponentData {
  type: string;
  enabled: boolean;
  /** Fields of the component; each component type documents its own in the matching `*Data` interface. */
  data: Record<string, unknown>;
}

export interface EntityData {
  id: string;
  name: string;
  active: boolean;
  parentId: string | null;
  transform: TransformData;
  components: ComponentData[];
}

export interface SceneFile {
  schemaVersion: number;
  name: string;
  active: boolean;
  entities: EntityData[];
}

/** The scene file version this player reads. */
export const SUPPORTED_SCHEMA_VERSION = 1;

/** Parses and checks a scene file; throws with the reason when it is not one this player can read. */
export function parseSceneFile(json: string): SceneFile {
  const parsed: unknown = JSON.parse(json);
  if (typeof parsed !== "object" || parsed === null) {
    throw new Error("A scene file must be a JSON object");
  }
  const file = parsed as Partial<SceneFile>;
  if (file.schemaVersion !== SUPPORTED_SCHEMA_VERSION) {
    throw new Error(`Unsupported scene schemaVersion ${String(file.schemaVersion)}; this player reads ${SUPPORTED_SCHEMA_VERSION}`);
  }
  if (!Array.isArray(file.entities)) {
    throw new Error("A scene file needs an 'entities' array");
  }
  return file as SceneFile;
}

/** Reads a number field of a component, or fails naming the field, instead of inventing a value. */
export function num(data: Record<string, unknown>, key: string, fallback?: number): number {
  const value = data[key];
  if (typeof value === "number") return value;
  if (fallback !== undefined && value === undefined) return fallback;
  throw new Error(`Component field '${key}' should be a number, got ${JSON.stringify(value)}`);
}

export function str(data: Record<string, unknown>, key: string, fallback?: string): string {
  const value = data[key];
  if (typeof value === "string") return value;
  if (fallback !== undefined && value === undefined) return fallback;
  throw new Error(`Component field '${key}' should be a string, got ${JSON.stringify(value)}`);
}

export function bool(data: Record<string, unknown>, key: string, fallback?: boolean): boolean {
  const value = data[key];
  if (typeof value === "boolean") return value;
  if (fallback !== undefined && value === undefined) return fallback;
  throw new Error(`Component field '${key}' should be true or false, got ${JSON.stringify(value)}`);
}
