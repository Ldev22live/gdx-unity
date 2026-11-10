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

// The desktop's model files (../assets/models/**/*.gltf and .glb) go to public/models, keeping their folders, so a scene's
// Model components find them at the same relative paths as on the desktop
const modelSource = join(root, "..", "assets", "models");
const modelTarget = join(root, "public", "models");
rmSync(modelTarget, { recursive: true, force: true });
let modelCount = 0;
function copyModels(from, to) {
  mkdirSync(to, { recursive: true });
  for (const entry of readdirSync(from, { withFileTypes: true })) {
    if (entry.isDirectory()) copyModels(join(from, entry.name), join(to, entry.name));
    else if (/\.(gltf|glb|bin|png|jpg)$/i.test(entry.name)) {
      copyFileSync(join(from, entry.name), join(to, entry.name));
      modelCount++;
    }
  }
}
copyModels(modelSource, modelTarget);
console.log(`Copied ${modelCount} model files to ${modelTarget}`);

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

// The desktop's animation clips (../assets/animations/**/*.anim) go to public/animations, keeping their folders, with an
// index.json the Animations tab lists (the humanoid Idle, Walk, Run, Jump and Fall are in the "humanoid" folder)
const clipSource = join(root, "..", "assets", "animations");
const clipTarget = join(root, "public", "animations");
rmSync(clipTarget, { recursive: true, force: true });
const clips = [];
function copyClips(from, to, relative) {
  mkdirSync(to, { recursive: true });
  for (const entry of readdirSync(from, { withFileTypes: true })) {
    if (entry.isDirectory()) {
      copyClips(join(from, entry.name), join(to, entry.name), `${relative}${entry.name}/`);
    } else if (entry.name.endsWith(".anim")) {
      copyFileSync(join(from, entry.name), join(to, entry.name));
      clips.push({ path: `animations/${relative}${entry.name}`, name: entry.name.replace(/\.anim$/, ""), group: relative.replace(/\/$/, "") });
    }
  }
}
copyClips(clipSource, clipTarget, "");
writeFileSync(join(clipTarget, "index.json"), JSON.stringify(clips, null, 2));
console.log(`Copied ${clips.length} animation clips to ${clipTarget}`);

// The desktop's visual scripts (../assets/graphs/*.graph) go to public/graphs, for the Visual Scripting tab and Graph Script components
const graphSource = join(root, "..", "assets", "graphs");
const graphTarget = join(root, "public", "graphs");
rmSync(graphTarget, { recursive: true, force: true });
mkdirSync(graphTarget, { recursive: true });
const graphs = [];
for (const file of readdirSync(graphSource).filter((name) => name.endsWith(".graph")).sort()) {
  copyFileSync(join(graphSource, file), join(graphTarget, file));
  graphs.push({ path: `graphs/${file}`, name: JSON.parse(readFileSync(join(graphSource, file), "utf8")).name ?? file });
}
writeFileSync(join(graphTarget, "index.json"), JSON.stringify(graphs, null, 2));
console.log(`Copied ${graphs.length} graphs to ${graphTarget}`);

// The character creator's baked characters (../assets/characters/*.gltf, TD-053) go to public/characters: scenes name them without the
// "models/" folder. The MakeHuman source data next to them is not served.
const characterSource = join(root, "..", "assets", "characters");
const characterTarget = join(root, "public", "characters");
rmSync(characterTarget, { recursive: true, force: true });
mkdirSync(characterTarget, { recursive: true });
let characterCount = 0;
for (const entry of readdirSync(characterSource, { withFileTypes: true })) {
  if (entry.isFile() && /\.(gltf|glb|bin|png|jpg)$/i.test(entry.name)) {
    copyFileSync(join(characterSource, entry.name), join(characterTarget, entry.name));
    characterCount++;
  }
}
console.log(`Copied ${characterCount} character files to ${characterTarget}`);

// The desktop's sprites and tile sets (../assets/sprites/*.sprite) go to public/sprites: Sprite Animator and Tilemap components name them
// by project path (`sprites/hero.sprite`), the same as the desktop (TD-066)
const spriteSource = join(root, "..", "assets", "sprites");
const spriteTarget = join(root, "public", "sprites");
rmSync(spriteTarget, { recursive: true, force: true });
mkdirSync(spriteTarget, { recursive: true });
let spriteCount = 0;
for (const file of readdirSync(spriteSource).filter((name) => name.endsWith(".sprite")).sort()) {
  copyFileSync(join(spriteSource, file), join(spriteTarget, file));
  spriteCount++;
}
console.log(`Copied ${spriteCount} sprites to ${spriteTarget}`);

// The example graphs that sample scenes run (../assets/graphs/samples/*.graph, TD-068) go to public/graphs/samples, where a Graph Script component
// finds them by the same project path as on the desktop. They are not listed in index.json (the Examples menu has its own list).
const exampleSource = join(graphSource, "samples");
const exampleTarget = join(graphTarget, "samples");
mkdirSync(exampleTarget, { recursive: true });
let exampleCount = 0;
for (const file of readdirSync(exampleSource).filter((name) => name.endsWith(".graph")).sort()) {
  copyFileSync(join(exampleSource, file), join(exampleTarget, file));
  exampleCount++;
}
console.log(`Copied ${exampleCount} example graphs to ${exampleTarget}`);
