/**
 * The shared project contract on the web (TD-106, urGameForge Alpha A12.9): the same `project-manifest.json` the desktop editor
 * reads and writes (`docs/contracts/project-manifest.schema.json`), capability negotiation and revisions. Scene files are kept as
 * raw JSON by the editor model, so anything this client does not understand is written back unchanged.
 */

/** The contract version this client reads and writes. */
export const CONTRACT_VERSION = 1;
export const MANIFEST_FILE = "project-manifest.json";
export const MAX_ASSETS = 20000;
export const MAX_ASSET_BYTES = 512 * 1024 * 1024;
export const MAX_PATH_LENGTH = 512;

export interface SceneEntry { id: string; path: string }
export interface AssetEntry { path: string; kind: string; size: number; sha256: string; licence?: string }
export interface Revision { id: string; base: string | null; client: "desktop" | "web"; clientVersion: string; savedAt: string }
export interface ProjectManifest {
  projectId: string;
  name: string;
  startScene: string;
  scenes: SceneEntry[];
  assets: AssetEntry[];
  required: string[];
  optional: string[];
  revision: Revision;
  /** Namespaced data kept unchanged, as parsed JSON objects. */
  extensions: Record<string, Record<string, unknown>>;
}

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const SHA256 = /^[0-9a-f]{64}$/;
const CAPABILITY = /^[a-z][a-z0-9-]*(:[a-z0-9-]+)?\/[a-z0-9.]+$/;
const TIMESTAMP = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?Z$/;
const KINDS = new Set(["scene", "terrain", "material", "texture", "model", "mesh", "sprite", "vector", "animation", "graph", "song",
  "sound", "prefab", "script", "other"]);
const MEMBERS = new Set(["contractVersion", "projectId", "name", "startScene", "scenes", "assets", "capabilities", "revision", "extensions"]);

/** A manifest or path refused, with the reason. */
export class ContractError extends Error {}

/** Checks a project-relative path's text: relative, forward slashes, no `..`, no drive letter, no encoded separator or dot. */
export function checkPath(path: string): void {
  const refuse = (reason: string) => { throw new ContractError(`Unsafe project path '${path}': ${reason}`); };
  if (typeof path !== "string" || path.length === 0 || path.length > MAX_PATH_LENGTH) refuse(`it must be 1 to ${MAX_PATH_LENGTH} characters long`);
  if (path.includes("\0")) refuse("it contains a NUL character");
  if (path.includes("\\")) refuse("backslashes are not allowed; use '/'");
  if (path.startsWith("/") || /^[A-Za-z]:/.test(path)) refuse("it is absolute; project paths are relative to the project folder");
  for (const segment of path.split("/")) {
    if (segment === "..") refuse("it leaves the project folder ('..')");
    if (segment === "" || segment === ".") refuse("it has an empty or '.' segment");
    if (/%[0-9A-Fa-f]{2}/.test(segment)) {
      let decoded: string;
      try {
        decoded = decodeURIComponent(segment);
      } catch {
        return refuse("it has a malformed percent-encoding");
      }
      if (decoded.includes("/") || decoded.includes("\\") || decoded === ".." || decoded === ".") refuse("it percent-encodes a separator or a dot segment");
    }
  }
}

function fail(message: string): never {
  throw new ContractError(`project-manifest.json: ${message}`);
}

function uuid(what: string, value: unknown): string {
  if (typeof value !== "string" || !UUID.test(value)) fail(`${what} must be a lowercase UUID: ${String(value)}`);
  return value;
}

function strings(value: unknown, what: string): string[] {
  if (!Array.isArray(value) || value.some((v) => typeof v !== "string")) fail(`${what} must be a list of strings`);
  return value as string[];
}

/** Reads a manifest strictly, as the desktop does: a newer version, an unknown member or a malformed value is refused. */
export function parseManifest(json: string): ProjectManifest {
  let root: Record<string, unknown>;
  try {
    root = JSON.parse(json) as Record<string, unknown>;
  } catch (error) {
    fail(`not JSON: ${String(error)}`);
  }
  if (typeof root !== "object" || root === null || Array.isArray(root)) fail("the manifest must be a JSON object");
  const version = root["contractVersion"];
  if (typeof version === "number" && version > CONTRACT_VERSION) {
    fail(`contract version ${version} needs a newer editor (this one reads ${CONTRACT_VERSION})`);
  }
  if (version !== CONTRACT_VERSION) fail(`missing or invalid contractVersion: ${String(version)}`);
  for (const key of Object.keys(root)) {
    if (!MEMBERS.has(key)) fail(`unknown member '${key}' (contract version 1 allows none; use extensions)`);
  }
  const name = root["name"];
  if (typeof name !== "string" || name.trim() === "" || name.length > 200) fail(`name must be 1 to 200 characters: ${String(name)}`);
  const startScene = root["startScene"] as string;
  checkPath(startScene);
  if (!Array.isArray(root["scenes"]) || root["scenes"].length === 0) fail("a project needs at least one scene");
  const scenes = (root["scenes"] as Record<string, unknown>[]).map((s) => {
    checkPath(s["path"] as string);
    return { id: uuid("scene id", s["id"]), path: s["path"] as string };
  });
  if (!scenes.some((s) => s.path === startScene)) fail(`startScene ${startScene} is not one of the scenes`);
  if (!Array.isArray(root["assets"])) fail("missing 'assets'");
  if ((root["assets"] as unknown[]).length > MAX_ASSETS) fail(`too many assets: ${(root["assets"] as unknown[]).length}`);
  const assets = (root["assets"] as Record<string, unknown>[]).map((a) => {
    const path = a["path"] as string;
    checkPath(path);
    if (!KINDS.has(a["kind"] as string)) fail(`unknown asset kind '${String(a["kind"])}' for ${path}`);
    const size = a["size"];
    if (typeof size !== "number" || !Number.isInteger(size) || size < 0 || size > MAX_ASSET_BYTES) fail(`asset size out of range for ${path}`);
    if (typeof a["sha256"] !== "string" || !SHA256.test(a["sha256"])) fail(`sha256 of ${path} must be 64 lowercase hex digits`);
    const entry: AssetEntry = { path, kind: a["kind"] as string, size, sha256: a["sha256"] };
    if (typeof a["licence"] === "string") entry.licence = a["licence"];
    return entry;
  });
  const capabilities = root["capabilities"] as Record<string, unknown> | undefined;
  if (!capabilities) fail("missing 'capabilities'");
  const required = strings(capabilities["required"], "capabilities.required");
  const optional = strings(capabilities["optional"], "capabilities.optional");
  for (const c of [...required, ...optional]) if (!CAPABILITY.test(c)) fail(`malformed capability: ${c}`);
  const revision = root["revision"] as Record<string, unknown> | undefined;
  if (!revision) fail("missing 'revision'");
  const savedBy = revision["savedBy"] as Record<string, unknown> | undefined;
  if (!savedBy) fail("missing 'savedBy'");
  const client = savedBy["client"];
  if (client !== "desktop" && client !== "web") fail(`savedBy.client must be desktop or web: ${String(client)}`);
  if (typeof savedBy["version"] !== "string" || savedBy["version"] === "") fail("savedBy.version is required");
  const savedAt = revision["savedAt"];
  if (typeof savedAt !== "string" || !TIMESTAMP.test(savedAt)) fail(`savedAt must be an ISO-8601 UTC time: ${String(savedAt)}`);
  const base = revision["base"];
  const extensions: Record<string, Record<string, unknown>> = {};
  for (const [key, value] of Object.entries((root["extensions"] as Record<string, unknown> | undefined) ?? {})) {
    if (typeof value !== "object" || value === null || Array.isArray(value)) fail(`extension '${key}' must be an object`);
    extensions[key] = value as Record<string, unknown>;
  }
  return {
    projectId: uuid("projectId", root["projectId"]), name, startScene, scenes, assets, required, optional,
    revision: {
      id: uuid("revision id", revision["id"]), base: base === null || base === undefined ? null : uuid("base revision", base),
      client, clientVersion: savedBy["version"], savedAt,
    },
    extensions,
  };
}

/** The manifest as JSON, in the same member order and layout the desktop writes. */
export function manifestToJson(m: ProjectManifest): string {
  const q = (s: string) => JSON.stringify(s);
  const lines: string[] = ["{", `  "contractVersion": ${CONTRACT_VERSION},`, `  "projectId": ${q(m.projectId)},`, `  "name": ${q(m.name)},`,
    `  "startScene": ${q(m.startScene)},`];
  lines.push(m.scenes.length === 0 ? `  "scenes": [],` : `  "scenes": [\n${m.scenes.map((s) => `    {"id": ${q(s.id)}, "path": ${q(s.path)}}`).join(",\n")}\n  ],`);
  const asset = (a: AssetEntry) => `    {"path": ${q(a.path)}, "kind": ${q(a.kind)}, "size": ${a.size}, "sha256": ${q(a.sha256)}${a.licence !== undefined ? `, "licence": ${q(a.licence)}` : ""}}`;
  lines.push(m.assets.length === 0 ? `  "assets": [],` : `  "assets": [\n${m.assets.map(asset).join(",\n")}\n  ],`);
  lines.push(`  "capabilities": {"required": [${m.required.map(q).join(", ")}], "optional": [${m.optional.map(q).join(", ")}]},`);
  let revision = `  "revision": {"id": ${q(m.revision.id)}, "base": ${m.revision.base === null ? "null" : q(m.revision.base)}, "savedBy": {"client": ${q(m.revision.client)}, "version": ${q(m.revision.clientVersion)}}, "savedAt": ${q(m.revision.savedAt)}}`;
  const ext = Object.entries(m.extensions);
  if (ext.length > 0) revision += `,\n  "extensions": {${ext.map(([k, v]) => `${q(k)}: ${JSON.stringify(v)}`).join(", ")}}`;
  lines.push(revision);
  return `${lines.join("\n")}\n}\n`;
}

/**
 * What this web client supports (2026-11-25, A12). Scene files are kept raw, so unknown data is preserved; `scenario/1` and
 * `ownership/1` mean that data is kept and its references checked. Not here: terrain editing, mesh and vector editing, Java or Kotlin
 * scripts, atmosphere and post-processing rendering, genre profiles.
 */
export const WEB_SUPPORTED: ReadonlySet<string> = new Set(["scene/1", "preserve-unknown/1", "project/1", "terrain/1", "material/1",
  "model/gltf", "sprite/2", "animation/1", "graph-run/2", "graph-edit/2", "song/1", "prefab/1", "physics2d/1", "physics3d/1",
  "scenario/1", "ownership/1"]);

export type Access = "full" | "limited" | "read-only";
export interface Evaluation { access: Access; missingRequired: string[]; missingOptional: string[]; explanations: string[] }

/** Capability negotiation (TD-106 point 5): a missing required capability opens read-only; a missing optional one limits editing. */
export function evaluate(manifest: ProjectManifest, supported: ReadonlySet<string> = WEB_SUPPORTED, client = "The web editor"): Evaluation {
  const missingRequired = manifest.required.filter((c) => !supported.has(c));
  const missingOptional = manifest.optional.filter((c) => !supported.has(c));
  if (missingRequired.length > 0) {
    return {
      access: "read-only", missingRequired, missingOptional,
      explanations: [`${client} opens "${manifest.name}" read-only: it needs ${missingRequired.join(", ")}, which it does not support. Saving is turned off so that nothing it cannot keep is overwritten. Open it in urGameForge Desktop.`],
    };
  }
  if (missingOptional.length > 0) {
    return {
      access: "limited", missingRequired, missingOptional,
      explanations: [`${client} cannot show or run ${missingOptional.join(", ")} in "${manifest.name}". You can edit everything else; that data is kept unchanged when you save.`],
    };
  }
  return { access: "full", missingRequired, missingOptional, explanations: [] };
}

/** SHA-256 of bytes, lowercase hex (Web Crypto; also in Node). */
export async function sha256Hex(bytes: Uint8Array): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new Uint8Array(bytes));
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

export type AssetIssue = "missing" | "modified" | "unsafe-path";

/** Checks each listed asset against the bytes `read` returns (null for a missing file); never substitutes another file. */
export async function verifyAssets(manifest: ProjectManifest, read: (path: string) => Promise<Uint8Array | null>):
  Promise<{ path: string; issue: AssetIssue }[]> {
  const findings: { path: string; issue: AssetIssue }[] = [];
  for (const asset of manifest.assets) {
    try {
      checkPath(asset.path);
    } catch {
      findings.push({ path: asset.path, issue: "unsafe-path" });
      continue;
    }
    const bytes = await read(asset.path);
    if (bytes === null) findings.push({ path: asset.path, issue: "missing" });
    else if (bytes.length !== asset.size || await sha256Hex(bytes) !== asset.sha256) findings.push({ path: asset.path, issue: "modified" });
  }
  return findings;
}

/** A save refused because the project has moved on since `yourBase`; offer "Save mine as a copy" or "Reload theirs". */
export class RevisionConflict extends Error {
  constructor(readonly yourBase: string | null, readonly current: string) {
    super(`The project was saved elsewhere since you opened it (now revision ${current.slice(0, 8)}). Your changes were not written.`);
  }
}

/**
 * The manifest of a web save: a new revision whose base is the current one, scene hashes from the texts being saved, other assets
 * unchanged, required capabilities only added to. Refuses a stale base. A cloud store must repeat this check on the server,
 * atomically (TD-106 point 6); this is the client half.
 */
export async function nextManifest(current: ProjectManifest, base: string | null, sceneTexts: Record<string, string>,
  options: { newId: () => string; now: () => Date; clientVersion: string; addedRequired?: string[] }): Promise<ProjectManifest> {
  if (base !== current.revision.id) throw new RevisionConflict(base, current.revision.id);
  const encoder = new TextEncoder();
  const assets = await Promise.all(current.assets.map(async (asset) => {
    const text = sceneTexts[asset.path];
    if (text === undefined) return asset;
    const bytes = encoder.encode(text);
    return { ...asset, size: bytes.length, sha256: await sha256Hex(bytes) };
  }));
  for (const path of Object.keys(sceneTexts)) checkPath(path);
  const required = [...current.required];
  for (const c of options.addedRequired ?? []) if (!required.includes(c)) required.push(c);
  return {
    ...current, assets, required,
    revision: { id: options.newId(), base: current.revision.id, client: "web", clientVersion: options.clientVersion,
      savedAt: options.now().toISOString() },
  };
}

/** The editor's status note for components the web view does not draw or run (kept unchanged in the file). */
export function describeUnsupported(unsupported: ReadonlyMap<string, number>): string {
  if (unsupported.size === 0) return "";
  const list = [...unsupported].map(([type, n]) => (n > 1 ? `${type} x${n}` : type)).join(", ");
  return `Not shown on the web: ${list}. Their data is kept unchanged when you save; edit them in urGameForge Desktop.`;
}

/**
 * The `gdxunity.project` marker a save writes (TD-106 point 1): format 2, mirroring the manifest's name and start scene for older
 * readers, with the project id and the manifest's name. Same text as the desktop's `ProjectMarker.toText`.
 */
export function markerText(m: ProjectManifest): string {
  const escape = (value: string) => value.replace(/\\/g, "\\\\").replace(/\n/g, "\\n").replace(/\r/g, "\\r").replace(/^ /, "\\ ");
  return `format=2\nname=${escape(m.name)}\nstartScene=${escape(m.startScene)}\nprojectId=${m.projectId}\nmanifest=${MANIFEST_FILE}\n`;
}

/** Reads a properties-style marker's keys (format 1 or 2); used to check it agrees with the manifest. */
export function parseMarker(text: string): Record<string, string> {
  const out: Record<string, string> = {};
  for (const line of text.split(/\r?\n/)) {
    if (line.trim() === "" || line.startsWith("#") || line.startsWith("!")) continue;
    const at = line.search(/[=:]/);
    if (at > 0) out[line.slice(0, at).trim()] = line.slice(at + 1).replace(/\\(.)/g, (_m, c: string) => (c === "n" ? "\n" : c === "r" ? "\r" : c));
  }
  return out;
}
