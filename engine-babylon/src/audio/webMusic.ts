import { AudioEngine } from "./AudioEngine";
import { SceneMusic } from "./SceneMusic";

/** The songs of a played scene, through the browser's audio worklet; separate from the Music tab's engine. */
export function createWebSceneMusic(): SceneMusic {
  return new SceneMusic(
    () => new AudioEngine(),
    async (path) => {
      const response = await fetch(path);
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      return response.text();
    },
  );
}
