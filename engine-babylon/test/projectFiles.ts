import { createHash } from "node:crypto";
import { cpSync, existsSync, mkdirSync, readdirSync, readFileSync, rmSync, statSync, writeFileSync } from "node:fs";
import { dirname, join, relative, sep } from "node:path";
import { expect } from "vitest";

/** The shared-contract fixtures (TD-106), read straight from the repository. */
export const CONTRACT_DIR = join(import.meta.dirname, "..", "..", "fixtures", "contract-v1");
export const INTEROP_DIR = join(CONTRACT_DIR, "interop");
/** Set UPDATE_INTEROP=1 to rewrite the web steps' committed folders (see CrossPlatformInteropTest on the desktop). */
export const UPDATE_INTEROP = process.env["UPDATE_INTEROP"] === "1";
const LOCK = ".forgeroom-save.lock";

/** Every file of a project folder, by forward-slash relative path (the desktop's save lock excluded). */
export function readProject(root: string): Map<string, Buffer> {
  const out = new Map<string, Buffer>();
  const walk = (dir: string) => {
    for (const name of readdirSync(dir)) {
      const path = join(dir, name);
      if (statSync(path).isDirectory()) walk(path);
      else if (name !== LOCK) out.set(relative(root, path).split(sep).join("/"), readFileSync(path));
    }
  };
  walk(root);
  return new Map([...out].sort(([a], [b]) => (a < b ? -1 : 1)));
}

/** Reads one file of a project as the contract's `read` callback wants (null when missing). */
export function reader(root: string): (path: string) => Promise<Uint8Array | null> {
  return async (path) => (existsSync(join(root, path)) ? new Uint8Array(readFileSync(join(root, path))) : null);
}

export function copyProject(from: string, to: string): string {
  cpSync(from, to, { recursive: true, filter: (src) => !src.endsWith(LOCK) });
  return to;
}

export function writeFiles(root: string, files: Record<string, string>): void {
  for (const [path, text] of Object.entries(files)) {
    mkdirSync(dirname(join(root, path)), { recursive: true });
    writeFileSync(join(root, path), text, "utf8");
  }
}

/** A fixed sequence of name-based UUIDs (version 3, as the desktop's UUID.nameUUIDFromBytes), so a step writes the same ids. */
export function ids(step: string): () => string {
  let next = 0;
  return () => {
    const bytes = createHash("md5").update(`${step}-${next++}`, "utf8").digest();
    bytes[6] = (bytes[6]! & 0x0f) | 0x30;
    bytes[8] = (bytes[8]! & 0x3f) | 0x80;
    const hex = bytes.toString("hex");
    return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
  };
}

/** The committed step folder must hold exactly what this step produced; with UPDATE_INTEROP=1 it is replaced. */
export function matchOrWrite(produced: string, committed: string): void {
  const files = readProject(produced);
  if (UPDATE_INTEROP) {
    rmSync(committed, { recursive: true, force: true });
    for (const [path, bytes] of files) {
      mkdirSync(dirname(join(committed, path)), { recursive: true });
      writeFileSync(join(committed, path), bytes);
    }
    return;
  }
  expect(existsSync(committed), `${committed} is missing: run with UPDATE_INTEROP=1`).toBe(true);
  const expected = readProject(committed);
  expect([...files.keys()], "the step's files changed: run with UPDATE_INTEROP=1").toEqual([...expected.keys()]);
  const differing = [...files].filter(([path, bytes]) => {
    const recorded = expected.get(path)!;
    if (/\.(glb|png)$/i.test(path)) return !bytes.equals(recorded);
    return bytes.toString("utf8").replace(/\r\n/g, "\n") !== recorded.toString("utf8").replace(/\r\n/g, "\n");
  })
    .map(([path]) => path);
  expect(differing, "the step now writes different bytes: run with UPDATE_INTEROP=1 and review").toEqual([]);
}
