import { EditorApp } from "./editor/EditorApp";
import { emptyScene } from "./editor/EditorModel";
import { byId } from "./editor/dom";
import { MusicStudio } from "./editor/music/studio";
import { createEngine, ScenePlayer } from "./runtime/ScenePlayer";
import { createDefaultRegistry } from "./scripts/registry";
import { parseSceneFile, type SceneFile } from "./scene/types";

const canvas = byId<HTMLCanvasElement>("view");
const player = new ScenePlayer(createEngine(canvas), canvas, byId("hud"), createDefaultRegistry());
player.onScriptFailure = (failure) => {
  byId("status").textContent = `Script ${failure.className} on '${failure.entityName}' stopped: ${String(failure.error)}`;
  byId("status").classList.add("error");
};

/** Opens the scene named by the page's ?scene= parameter (a sample file name), otherwise an empty scene. */
async function initialScene(): Promise<SceneFile> {
  const sample = new URLSearchParams(window.location.search).get("scene");
  if (!sample) return emptyScene();
  const response = await fetch(`samples/${encodeURIComponent(sample)}`);
  if (!response.ok) throw new Error(`Could not load samples/${sample}: HTTP ${response.status}`);
  return parseSceneFile(await response.text());
}

initialScene()
  .catch((error) => {
    byId("status").textContent = `Error: ${error instanceof Error ? error.message : String(error)}`;
    return emptyScene();
  })
  .then((scene) => {
    (window as unknown as { editor: EditorApp }).editor = new EditorApp(player, scene);
    startMusicStudio();
  });

/** The Music tab sits in the same grid row as the scene workspace; only one of them shows. */
function startMusicStudio(): void {
  const studio = new MusicStudio(document.body);
  document.body.insertBefore(studio.root, byId("status"));
  (window as unknown as { studio: MusicStudio }).studio = studio;
  const show = (music: boolean) => {
    document.body.classList.toggle("music", music);
    byId("workspace").hidden = music;
    studio.setVisible(music);
    byId("tab-music").classList.toggle("active", music);
    byId("tab-viewport").classList.toggle("active", !music);
  };
  byId("tab-viewport").addEventListener("click", () => show(false));
  byId("tab-music").addEventListener("click", () => show(true));
}
