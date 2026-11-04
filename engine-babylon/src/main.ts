import { createEngine, ScenePlayer, type SceneReport } from "./runtime/ScenePlayer";
import { createDefaultRegistry } from "./scripts/registry";
import { parseSceneFile } from "./scene/types";

interface SampleEntry {
  file: string;
  name: string;
}

function element<T extends HTMLElement>(id: string): T {
  const found = document.getElementById(id);
  if (!found) throw new Error(`index.html has no #${id}`);
  return found as T;
}

const canvas = element<HTMLCanvasElement>("view");
const hud = element<HTMLElement>("hud");
const select = element<HTMLSelectElement>("scenes");
const playButton = element<HTMLButtonElement>("play");
const stopButton = element<HTMLButtonElement>("stop");
const status = element<HTMLElement>("status");

const player = new ScenePlayer(createEngine(canvas), canvas, hud, createDefaultRegistry());

function describe(report: SceneReport): string {
  const parts = [`${report.name}: ${report.entityCount} entities`];
  if (report.missingScripts.length > 0) {
    parts.push(`${report.missingScripts.length} script(s) have no web version, so the game logic will not run`);
  }
  if (report.unsupportedComponents.size > 0) {
    parts.push(`unsupported: ${[...report.unsupportedComponents].map(([type, n]) => `${type} x${n}`).join(", ")}`);
  }
  return parts.join(" | ");
}

function setPlaying(playing: boolean): void {
  playButton.disabled = playing;
  stopButton.disabled = !playing;
}

async function loadSample(file: string): Promise<void> {
  const response = await fetch(`samples/${file}`);
  if (!response.ok) throw new Error(`Could not load samples/${file}: HTTP ${response.status}`);
  const report = player.load(parseSceneFile(await response.text()));
  setPlaying(false);
  status.textContent = describe(report);
}

function showError(error: unknown): void {
  status.textContent = `Error: ${error instanceof Error ? error.message : String(error)}`;
  console.error(error);
}

player.onScriptFailure = (failure) => {
  showError(new Error(`Script ${failure.className} on '${failure.entityName}' stopped: ${String(failure.error)}`));
};

playButton.addEventListener("click", () => {
  player.play();
  setPlaying(player.isPlaying);
});
stopButton.addEventListener("click", () => {
  player.stop();
  setPlaying(false);
});
select.addEventListener("change", () => loadSample(select.value).catch(showError));

async function start(): Promise<void> {
  const response = await fetch("samples/index.json");
  if (!response.ok) throw new Error("No samples/index.json; run `npm run sync-samples`");
  const samples = (await response.json()) as SampleEntry[];
  for (const sample of samples) {
    select.add(new Option(sample.name, sample.file));
  }
  const first = samples.find((sample) => sample.file === "collector-3d.json") ?? samples[0];
  if (!first) throw new Error("The samples folder has no scenes");
  select.value = first.file;
  await loadSample(first.file);
}

start().catch(showError);
