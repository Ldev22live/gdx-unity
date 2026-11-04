// Copies the sample scenes of the desktop editor (../assets/samples/*.json) into public/samples, with an index.json
// listing them, so the web player plays exactly the files the desktop editor and game runtime use.
import { copyFileSync, mkdirSync, readdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const source = join(root, "..", "assets", "samples");
const target = join(root, "public", "samples");

rmSync(target, { recursive: true, force: true });
mkdirSync(target, { recursive: true });

const index = [];
for (const file of readdirSync(source).filter((name) => name.endsWith(".json")).sort()) {
  copyFileSync(join(source, file), join(target, file));
  const scene = JSON.parse(readFileSync(join(source, file), "utf8"));
  index.push({ file, name: scene.name ?? file });
}
writeFileSync(join(target, "index.json"), JSON.stringify(index, null, 2));
console.log(`Copied ${index.length} sample scenes to ${target}`);

// The desktop's songs (../assets/music/*.song) go to public/music the same way, for the Music tab's Songs menu
const songSource = join(root, "..", "assets", "music");
const songTarget = join(root, "public", "music");
rmSync(songTarget, { recursive: true, force: true });
mkdirSync(songTarget, { recursive: true });
const songs = [];
for (const file of readdirSync(songSource).filter((name) => name.endsWith(".song")).sort()) {
  copyFileSync(join(songSource, file), join(songTarget, file));
  songs.push({ file, name: JSON.parse(readFileSync(join(songSource, file), "utf8")).name ?? file });
}
writeFileSync(join(songTarget, "index.json"), JSON.stringify(songs, null, 2));
console.log(`Copied ${songs.length} songs to ${songTarget}`);
