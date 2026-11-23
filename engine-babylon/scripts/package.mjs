// Builds the Forgeroom web player and editor and packs them for a server:
//
//   npm run package              build, then pack
//   npm run package -- --skip-build   pack the existing dist/ (when you just built it)
//
// Output: package/forgeroom-web-<version>.tar.gz (and a .sha256 file next to it). The archive holds
//   site/       the built player (index.html) and editor (editor.html), with scenes, models and songs
//   deploy/     nginx configuration, Dockerfile and docker-compose.yml
//   INSTALL.md  step-by-step install on a VPS
//   BUILD-INFO.txt  version, date, commit and the size of the site
// Needs only Node, npm and `tar` (Windows 10/11, Linux and macOS ship it).
import { spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import { cpSync, existsSync, mkdirSync, readFileSync, readdirSync, rmSync, statSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const skipBuild = process.argv.includes("--skip-build");
const version = JSON.parse(readFileSync(join(root, "package.json"), "utf8")).version;
const name = `forgeroom-web-${version}`;
const outDir = join(root, "package");
const staging = join(outDir, name);
const archive = join(outDir, `${name}.tar.gz`);

function run(command, args, options = {}) {
  const result = spawnSync(command, args, { cwd: root, stdio: "inherit", shell: process.platform === "win32", ...options });
  if (result.status !== 0) throw new Error(`${command} ${args.join(" ")} failed with exit code ${result.status}`);
}

function folderSize(path) {
  let total = 0;
  for (const entry of readdirSync(path, { withFileTypes: true })) {
    const full = join(path, entry.name);
    total += entry.isDirectory() ? folderSize(full) : statSync(full).size;
  }
  return total;
}

function git(args) {
  const result = spawnSync("git", args, { cwd: root, encoding: "utf8" });
  return result.status === 0 ? result.stdout.trim() : "unknown";
}

if (!skipBuild) run("npm", ["run", "build"]);
const dist = join(root, "dist");
if (!existsSync(join(dist, "index.html")) || !existsSync(join(dist, "editor.html"))) {
  throw new Error("dist/ has no index.html and editor.html: run `npm run build` first");
}
for (const folder of ["samples", "models", "music"]) {
  if (!existsSync(join(dist, folder))) throw new Error(`dist/${folder} is missing: the build did not copy the sample files`);
}

rmSync(staging, { recursive: true, force: true });
rmSync(archive, { force: true });
mkdirSync(staging, { recursive: true });
cpSync(dist, join(staging, "site"), { recursive: true });
cpSync(join(root, "deploy"), join(staging, "deploy"), { recursive: true });
cpSync(join(root, "deploy", "INSTALL.md"), join(staging, "INSTALL.md"));
rmSync(join(staging, "deploy", "INSTALL.md"));

const siteBytes = folderSize(join(staging, "site"));
writeFileSync(
  join(staging, "BUILD-INFO.txt"),
  [
    `Forgeroom web ${version}`,
    `Built:   ${new Date().toISOString()}`,
    `Commit:  ${git(["rev-parse", "--short", "HEAD"])}${git(["status", "--porcelain"]) ? " (with uncommitted changes)" : ""}`,
    `Node:    ${process.version}`,
    `Site:    ${(siteBytes / 1024 / 1024).toFixed(1)} MB in site/`,
    "",
  ].join("\n"),
);

// Relative names from inside the folder: GNU tar reads "C:\..." as a remote host, so no absolute paths on Windows
run("tar", ["-czf", `${name}.tar.gz`, name], { cwd: outDir });
const digest = createHash("sha256").update(readFileSync(archive)).digest("hex");
writeFileSync(`${archive}.sha256`, `${digest}  ${name}.tar.gz\n`);
rmSync(staging, { recursive: true, force: true });

console.log(`\nPacked ${archive}`);
console.log(`  ${(statSync(archive).size / 1024 / 1024).toFixed(1)} MB compressed, ${(siteBytes / 1024 / 1024).toFixed(1)} MB of site files`);
console.log(`  sha256 ${digest}`);
console.log("Copy it to the server and follow INSTALL.md inside it (also in deploy/INSTALL.md in the repository).");
