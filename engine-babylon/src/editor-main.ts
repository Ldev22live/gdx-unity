import { EditorApp } from "./editor/EditorApp";
import { emptyScene } from "./editor/EditorModel";
import { byId } from "./editor/dom";
import { AnimationsTab } from "./editor/animations/animationsTab";
import { GraphTab } from "./editor/graph/graphTab";
import { MusicStudio } from "./editor/music/studio";
import { createEngine, ScenePlayer } from "./runtime/ScenePlayer";
import { createWebSceneMusic } from "./audio/webMusic";
import { createDefaultRegistry } from "./scripts/registry";
import { parseSceneFile, type SceneFile } from "./scene/types";

const canvas = byId<HTMLCanvasElement>("view");
const player = new ScenePlayer(createEngine(canvas), canvas, byId("hud"), createDefaultRegistry(), undefined, createWebSceneMusic());
player.onScriptFailure = (failure) => {
  byId("status").textContent = `Script ${failure.className} on '${failure.entityName}' stopped: ${String(failure.error)}`;
  byId("status").classList.add("error");
};
player.onAudioProblem = (message) => {
  byId("status").textContent = message;
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
    const editor = new EditorApp(player, scene);
    (window as unknown as { editor: EditorApp }).editor = editor;
    startTabs(editor);
  });

/** The tabs share the grid row of the scene workspace; only one of them shows. */
function startTabs(editor: EditorApp): void {
  const setStatus = (message: string, isError = false): void => {
    byId("status").textContent = message;
    byId("status").classList.toggle("error", isError);
  };
  const studio = new MusicStudio(document.body);
  const animations = new AnimationsTab(document.body, editor.model, setStatus);
  const scripting = new GraphTab(document.body, editor.model, player.graphs, setStatus);
  for (const panel of [studio.root, animations.root, scripting.root]) document.body.insertBefore(panel, byId("status"));
  const tools = window as unknown as { studio: MusicStudio; animations: AnimationsTab; scripting: GraphTab };
  tools.studio = studio;
  tools.animations = animations;
  tools.scripting = scripting;
  player.onGraphMessage = (message, isError) => setStatus(message, isError);
  player.onAssetProblem = (message) => setStatus(message, true);

  const tabs = ["viewport", "animations", "scripting", "music"] as const;
  const show = (name: (typeof tabs)[number]): void => {
    // The Music tab's own switch keeps working: body.music hides the scene toolbar there, body.fullpanel does the same for the others
    document.body.classList.toggle("music", name === "music");
    document.body.classList.toggle("fullpanel", name !== "viewport");
    byId("workspace").hidden = name !== "viewport";
    studio.setVisible(name === "music");
    animations.setVisible(name === "animations");
    scripting.setVisible(name === "scripting");
    for (const tab of tabs) byId(`tab-${tab}`).classList.toggle("active", tab === name);
  };
  for (const tab of tabs) byId(`tab-${tab}`).addEventListener("click", () => show(tab));
}
