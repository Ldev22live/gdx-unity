import { AbstractEngine, Engine } from "@babylonjs/core";
import { Hud } from "./Hud";
import { Input } from "./Input";
import { Physics2D } from "./Physics2D";
import { buildScene, type BuiltScene } from "./SceneBuilder";
import { ScriptRegistry, type ScriptComponent } from "./ScriptComponent";
import { str, type SceneFile } from "../scene/types";

/** What the player knows about the scene it holds, for the UI. */
export interface SceneReport {
  name: string;
  entityCount: number;
  /** Component types in the file this player cannot run, with counts. */
  unsupportedComponents: Map<string, number>;
  /** Script classes the scene binds that have no web version, so those entities do nothing in Play. */
  missingScripts: string[];
}

/** A script that was bound by the scene file but could not run, and why. */
export interface ScriptFailure {
  className: string;
  entityName: string;
  error: unknown;
}

/**
 * Plays scene files in a Babylon engine: builds the scene, runs the render loop, and on Play starts physics and
 * scripts. Stop rebuilds the scene from the file, so Play never changes what was loaded (the same promise the
 * desktop editor makes).
 */
export class ScenePlayer {
  private built: BuiltScene | null = null;
  private file: SceneFile | null = null;
  private hud: Hud | null = null;
  private physics: Physics2D | null = null;
  private scripts: { script: ScriptComponent; entityName: string }[] = [];
  private playing = false;
  private readonly input = new Input();
  private detachInput: (() => void) | null = null;
  /** When false the orbit (viewer) camera is always used, even if the scene has a camera: the editor's setting. */
  useGameCamera = true;
  /** Called every time a scene was built from a file, so the editor can attach its tools to the new nodes. */
  onSceneBuilt: (built: BuiltScene) => void = () => {};
  private viewerActive = false;
  /** Called when a script throws; the script is switched off and the game goes on. */
  onScriptFailure: (failure: ScriptFailure) => void = () => {};

  constructor(
    private readonly engine: AbstractEngine,
    private readonly canvas: HTMLCanvasElement,
    private readonly hudContainer: HTMLElement,
    private readonly registry: ScriptRegistry,
  ) {
    this.detachInput = this.input.attach(window);
    engine.runRenderLoop(() => this.frame());
    window.addEventListener("resize", () => engine.resize());
  }

  get isPlaying(): boolean {
    return this.playing;
  }

  /** The scene that is built right now, or null before the first load. */
  getBuilt(): BuiltScene | null {
    return this.built;
  }

  /** The canvas the scene is drawn on. */
  getCanvas(): HTMLCanvasElement {
    return this.canvas;
  }

  /** Replaces the current scene with the one in {@code file}; returns what the player could and could not load. */
  load(file: SceneFile): SceneReport {
    this.stop();
    this.disposeScene();
    this.file = file;
    this.built = buildScene(this.engine, file);
    this.hud = new Hud(this.hudContainer, this.built.texts);
    this.selectCamera();
    this.onSceneBuilt(this.built);
    return this.report();
  }

  play(): void {
    if (!this.built || !this.file || this.playing) return;
    // Start from the file's state every time, even if a previous Play moved things
    this.load(this.file);
    const built = this.built!;
    this.physics = new Physics2D(built.game);
    this.scripts = [];
    for (const entity of built.game.entities) {
      const binding = entity.getComponent("EntityScriptBindingComponent");
      if (!binding) continue;
      const className = str(binding.data, "scriptClassName");
      if (!this.registry.has(className)) continue;
      const script = this.registry.create(className);
      script.entity = entity;
      script.scene = built.game;
      script.input = this.input;
      this.scripts.push({ script, entityName: entity.name });
    }
    this.playing = true;
    this.startScripts();
    this.canvas.focus();
  }

  stop(): void {
    if (!this.playing) return;
    this.playing = false;
    this.physics = null;
    this.scripts = [];
    if (this.file) this.load(this.file);
  }

  dispose(): void {
    this.detachInput?.();
    this.disposeScene();
    this.engine.stopRenderLoop();
    this.engine.dispose();
  }

  private report(): SceneReport {
    const built = this.built!;
    const missing = new Set<string>();
    for (const entity of built.game.entities) {
      const binding = entity.getComponent("EntityScriptBindingComponent");
      if (!binding) continue;
      const className = str(binding.data, "scriptClassName");
      if (!this.registry.has(className)) missing.add(className);
    }
    return {
      name: this.file!.name,
      entityCount: built.game.entities.length,
      unsupportedComponents: built.unsupported,
      missingScripts: [...missing].sort(),
    };
  }

  private startScripts(): void {
    for (const entry of [...this.scripts]) {
      this.guard(entry, () => entry.script.onStart());
    }
  }

  private guard(entry: { script: ScriptComponent; entityName: string }, run: () => void): void {
    try {
      run();
    } catch (error) {
      // One broken script must not freeze the whole game: switch it off, report it, keep going
      this.scripts = this.scripts.filter((other) => other !== entry);
      this.onScriptFailure({ className: entry.script.constructor.name, entityName: entry.entityName, error });
    }
  }

  private frame(): void {
    const built = this.built;
    if (!built) return;
    const delta = this.engine.getDeltaTime() / 1000;
    if (this.playing) {
      this.physics?.update(delta);
      for (const entry of [...this.scripts]) {
        this.guard(entry, () => entry.script.onUpdate(delta));
      }
      this.input.endFrame();
    }
    if (built.gameCamera && !this.viewerActive) {
      const aspect = this.engine.getRenderWidth() / Math.max(1, this.engine.getRenderHeight());
      built.gameCamera.sync(aspect);
    }
    this.hud?.update();
    built.scene.render();
  }

  /** Uses the scene's own camera when it has one and {@link useGameCamera} is on, otherwise the orbit camera. */
  private selectCamera(): void {
    const built = this.built!;
    this.viewerActive = !this.useGameCamera || !built.gameCamera;
    if (this.viewerActive) {
      built.scene.activeCamera = built.viewerCamera;
      built.viewerCamera.attachControl(this.canvas, true);
    }
  }

  private disposeScene(): void {
    this.hud?.dispose();
    this.hud = null;
    this.built?.scene.dispose();
    this.built = null;
  }
}

/** Creates the Babylon engine for a canvas. */
export function createEngine(canvas: HTMLCanvasElement): Engine {
  return new Engine(canvas, true, { preserveDrawingBuffer: false, stencil: true });
}
