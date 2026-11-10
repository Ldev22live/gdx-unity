import { AbstractEngine, Engine } from "@babylonjs/core";
import { SceneMusic } from "../audio/SceneMusic";
import { isClipName, loadClipFile, type Clip as SoundClip } from "../audio/clips";
import { SfxPlayer, WebAudioSfxOutput } from "../audio/SfxPlayer";
import { createDefaultRegistry as createNodeRegistry, type NodeRegistry } from "../visualscript/nodes";
import { GraphInstance, type GraphHost } from "../visualscript/interpreter";
import { GraphStore } from "../visualscript/store";
import { Animator, loadClip, type AnimatorSettings, type Clip, type WrapMode } from "./Animation";
import { Hud } from "./Hud";
import { Input } from "./Input";
import { Physics2D, type Contact2D } from "./Physics2D";
import { joltIfLoaded, JoltPhysicsBackend3D, loadJolt } from "./physics3d/JoltBackend";
import { CameraRig } from "./CameraRig";
import { MeshColliderGeometry } from "./physics3d/ColliderGeometry";
import { Physics3D, type Contact } from "./physics3d/Physics3D";
import { loadPendingModels, type ModelFailure } from "./ModelLoader";
import { loadPendingSprites } from "./SpriteLoader";
import { buildScene, type BuiltScene } from "./SceneBuilder";
import { ScriptRegistry, type ScriptComponent } from "./ScriptComponent";
import { bool, num, str, type SceneFile } from "../scene/types";
import type { SoundSink } from "./GameEntity";
import { graphEntity } from "./GraphEntity";

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
  private physics3d: Physics3D | null = null;
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
  /** Called when an Audio Source names a sound this player cannot make; that source stays silent and the game goes on. */
  onAudioProblem: (message: string) => void = () => {};
  /** Called with what a visual script prints, and with the errors of its nodes (isError true); the game goes on. */
  onGraphMessage: (message: string, isError: boolean) => void = () => {};
  /** Called when a clip or a graph the scene names cannot be loaded; the entity just does not animate or script. */
  onAssetProblem: (message: string) => void = () => {};
  /** Graphs by path: the ones the Visual Scripting tab is editing win over the files. */
  readonly graphs = new GraphStore();
  /** Resolves when the clips and graphs of the scene built last have loaded (or failed). */
  assetsReady: Promise<void> = Promise.resolve();
  private readonly clips = new Map<string, Clip>();
  private readonly nodeRegistry: NodeRegistry = createNodeRegistry();
  private animators: Animator[] = [];
  private graphInstances: GraphInstance[] = [];
  private readonly sfx: SfxPlayer;
  /** How many of the 3D physics warnings were passed to {@link onAssetProblem}. */
  private reportedPhysicsWarnings = 0;

  constructor(
    private readonly engine: AbstractEngine,
    private readonly canvas: HTMLCanvasElement,
    private readonly hudContainer: HTMLElement,
    private readonly registry: ScriptRegistry,
    sfx: SfxPlayer = new SfxPlayer(new WebAudioSfxOutput()),
    /** Plays the songs of Music Sources; without it a scene's songs stay silent. */
    private readonly music: SceneMusic | null = null,
    /** Reads the {@code .wav} and {@code .ogg} clips that Audio Sources name; tests pass a fake. */
    private readonly loadSound: (path: string) => Promise<SoundClip> = (path) => loadClipFile(path),
  ) {
    this.sfx = sfx;
    this.detachInput = this.input.attach(window);
    engine.runRenderLoop(() => this.frame());
    window.addEventListener("resize", () => engine.resize());
  }

  get isPlaying(): boolean {
    return this.playing;
  }

  /**
   * Called when play starts or stops. Play can start a moment after {@link play} returns (the first Play of a scene with 3D
   * colliders waits for the physics engine to load), so a page follows this instead of reading {@link isPlaying} right after.
   */
  onPlayingChanged: (playing: boolean) => void = () => {};
  /** The scene a Play is waiting to start, while the 3D physics engine loads; Stop and loading another scene cancel it. */
  private pendingPlay: SceneFile | null = null;

  /** Resolves when the models of the scene built last have loaded (or failed). */
  modelsReady: Promise<void> = Promise.resolve();
  /** Resolves when the sprites and tile sets of the scene built last have loaded (or failed). */
  spritesReady: Promise<void> = Promise.resolve();
  /** Called when the current scene's models have loaded, with the ones that failed. */
  onModelsLoaded: (built: BuiltScene, failures: ModelFailure[]) => void = () => undefined;

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
    this.pendingPlay = null;
    this.stop();
    this.disposeScene();
    this.file = file;
    this.built = buildScene(this.engine, file);
    const built = this.built;
    // Models arrive after the scene is built; tell the page when they are in so it can frame and outline them
    this.modelsReady = loadPendingModels(built).then((failures) => {
      if (this.built === built) this.onModelsLoaded(built, failures);
    });
    this.assetsReady = this.loadAssets(built);
    // Sprites and tile sets (TD-066) arrive after the scene is built too; a file that fails is reported and the rest still shows
    this.spritesReady = loadPendingSprites(built).then((failures) => {
      if (this.built !== built) return;
      for (const failure of failures) this.onAssetProblem(`${failure.entityName}: cannot load ${failure.path}: ${failure.message}`);
    });
    this.hud = new Hud(this.hudContainer, this.built.texts);
    this.selectCamera();
    this.onSceneBuilt(this.built);
    return this.report();
  }

  play(): void {
    if (!this.built || !this.file || this.playing) return;
    // Jolt (the 3D physics engine) is WebAssembly that loads asynchronously: the first Play of a scene with 3D colliders waits for it
    if (Physics3D.isUsedBy(this.built.game) && !joltIfLoaded()) {
      const file = this.file;
      this.pendingPlay = file;
      loadJolt().then(
        () => {
          if (this.pendingPlay === file && this.file === file && !this.playing) this.play();
        },
        (error: unknown) => {
          this.pendingPlay = null;
          this.onScriptFailure({ className: "Physics3D", entityName: "(3D physics engine)", error });
        },
      );
      return;
    }
    // Start from the file's state every time, even if a previous Play moved things
    this.load(this.file);
    const built = this.built!;
    this.physics = new Physics2D(built.game);
    this.physics.addContactListener((contact) => this.deliverContact2D(contact));
    const jolt = joltIfLoaded();
    this.reportedPhysicsWarnings = 0;
    // Hull and mesh colliders read the triangles of the entity's meshes; models arrive after the scene is built, so the system waits for them
    const geometry = new MeshColliderGeometry((entity) => built.modelFailures.some((failure) => failure.entityName === entity.name));
    this.physics3d = jolt && Physics3D.isUsedBy(built.game) ? new Physics3D(built.game, new JoltPhysicsBackend3D(jolt), geometry) : null;
    this.physics3d?.addContactListener((contact) => this.deliverContact(contact));
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
      script.physics3d = this.physics3d;
      this.scripts.push({ script, entityName: entity.name });
    }
    this.startCameraRigs(built);
    this.playing = true;
    this.pendingPlay = null;
    this.onPlayingChanged(true);
    this.bindSound(built.game);
    this.startScripts();
    this.playStartSounds(built.game);
    this.startMusic(built.game);
    // Clips and graphs are loaded with the scene, so this runs before the next frame; it waits only the first time
    void this.assetsReady.then(() => {
      if (this.playing && this.built === built) this.startAssetDrivers(built);
    });
    this.canvas.focus();
  }

  stop(): void {
    this.pendingPlay = null;
    if (!this.playing) return;
    this.playing = false;
    this.onPlayingChanged(false);
    this.physics = null;
    this.physics3d?.dispose();
    this.physics3d = null;
    this.scripts = [];
    this.stopAssetDrivers();
    this.music?.stop();
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

  /** Reads the clips of the scene's Animators and the graphs of its Graph Scripts; a failure is reported, not thrown. */
  private async loadAssets(built: BuiltScene): Promise<void> {
    const jobs: Promise<void>[] = [];
    const seen = new Set<string>();
    for (const entity of built.game.entities) {
      for (const animator of entity.getComponents("AnimatorComponent")) {
        const path = str(animator.data, "clip", "");
        if (path === "" || this.clips.has(path) || seen.has(path)) continue;
        seen.add(path);
        jobs.push(
          loadClip(path).then(
            (clip) => void this.clips.set(path, clip),
            (error: unknown) => this.onAssetProblem(error instanceof Error ? error.message : String(error)),
          ),
        );
      }
      for (const source of entity.getComponents("AudioSource")) {
        const path = str(source.data, "sound", "Coin");
        if (!isClipName(path) || this.sfx.isAvailable(path) || seen.has(path)) continue;
        seen.add(path);
        jobs.push(
          this.loadSound(path).then(
            (clip) => this.sfx.addClip(path, clip),
            (error: unknown) => this.onAudioProblem(error instanceof Error ? error.message : String(error)),
          ),
        );
      }
      for (const script of entity.getComponents("GraphScriptComponent")) {
        const path = str(script.data, "graph", "");
        if (path === "" || seen.has(path)) continue;
        seen.add(path);
        jobs.push(
          this.graphs.load(path).then(
            () => undefined,
            (error: unknown) => this.onAssetProblem(error instanceof Error ? error.message : String(error)),
          ),
        );
      }
    }
    await Promise.all(jobs);
  }

  /** Starts the scene's Animators and Graph Scripts (the ones whose files loaded). */
  private startAssetDrivers(built: BuiltScene): void {
    this.stopAssetDrivers();
    for (const entity of built.game.entities) {
      for (const component of entity.getComponents("AnimatorComponent")) {
        const clip = this.clips.get(str(component.data, "clip", ""));
        if (!clip) continue;
        const settings: AnimatorSettings = {
          clip: str(component.data, "clip", ""),
          playOnStart: bool(component.data, "playOnStart", true),
          wrap: str(component.data, "wrap", "LOOP") as WrapMode,
          speed: num(component.data, "speed", 1),
        };
        this.animators.push(new Animator(entity, clip, settings));
      }
    }
    const host: GraphHost = {
      isKeyDown: (codes) => this.input.isDown(...codes),
      log: (_instance, message) => this.onGraphMessage(message, false),
      error: (_instance, error) => this.onGraphMessage(`Visual script: ${error.message} (node ${error.nodeId})`, true),
    };
    for (const entity of built.game.entities) {
      for (const component of entity.getComponents("GraphScriptComponent")) {
        const graph = this.graphs.peek(str(component.data, "graph", ""));
        if (!graph) continue;
        try {
          this.graphInstances.push(new GraphInstance(graph, this.nodeRegistry, graphEntity(entity, built.game), host, () => built.game.contains(entity)));
        } catch (error) {
          this.onGraphMessage(`${entity.name}: ${error instanceof Error ? error.message : String(error)}`, true);
        }
      }
    }
    for (const instance of [...this.graphInstances]) instance.beginPlay();
  }

  private stopAssetDrivers(): void {
    for (const instance of this.graphInstances) instance.stop();
    this.animators = [];
    this.graphInstances = [];
  }

  /** Connects the scene's Audio Sources to the sound output, and reports the ones that name a sound we cannot make. */
  private bindSound(game: BuiltScene["game"]): void {
    const unknown = new Set<string>();
    for (const entity of game.entities) {
      for (const source of entity.getComponents("AudioSource")) {
        const sound = str(source.data, "sound", "Coin");
        // A clip that failed to load was reported when it was read; it stays silent like an unknown name
        if (!this.sfx.isAvailable(sound) && !isClipName(sound)) unknown.add(sound);
      }
    }
    for (const sound of unknown) {
      this.onAudioProblem(`Unknown sound '${sound}' on an Audio Source; use one of ${SfxPlayer.names.join(", ")}, or a .wav or .ogg file of the project`);
    }
    const sink: SoundSink = {
      play: (sound, volume, pitch, pan) => {
        // Unknown names were reported above; they stay silent instead of stopping the script that played them
        if (this.sfx.isAvailable(sound)) this.sfx.play(sound, volume, pitch, pan ?? 0);
      },
    };
    game.bindSound(sink);
  }

  /** Plays the Audio Sources that are set to play once when Play starts. */
  private playStartSounds(game: BuiltScene["game"]): void {
    for (const entity of game.entities) {
      if (!entity.isActiveInHierarchy) continue;
      for (const source of entity.getComponents("AudioSource")) {
        if (!bool(source.data, "playOnStart", false)) continue;
        game.playSource(entity, source);
      }
    }
  }

  /** Starts the song of the first enabled Music Source that plays on start; the browser plays one song at a time. */
  private startMusic(game: BuiltScene["game"]): void {
    if (!this.music) return;
    const sources = game.entities.flatMap((entity) => (entity.isActiveInHierarchy ? entity.getComponents("MusicSource") : []));
    const starting = sources.filter((source) => source.enabled && bool(source.data, "playOnStart", true));
    if (starting.length > 1) {
      this.onAudioProblem(`The web player plays one song at a time; ${starting.length} Music Sources play on start, so only '${str(starting[0]!.data, "song", "")}' plays`);
    }
    const first = starting[0];
    if (!first) return;
    const settings = { song: str(first.data, "song", ""), volume: num(first.data, "volume", 1), loop: bool(first.data, "loop", true) };
    this.music.start(settings).catch((error: unknown) => this.onAudioProblem(error instanceof Error ? error.message : String(error)));
  }

  private startScripts(): void {
    for (const entry of [...this.scripts]) {
      this.guard(entry, () => entry.script.onStart());
    }
  }

  /** Calls the 2D contact callbacks of the scripts on both entities of a contact, like the desktop's `ScriptCollisionForwarder2D`. */
  private deliverContact2D(contact: Contact2D): void {
    const deliver = (entity: Contact2D["entityA"], other: Contact2D["entityB"]): void => {
      if (!entity.isActiveInHierarchy) return;
      for (const entry of [...this.scripts]) {
        if (entry.script.entity !== entity) continue;
        this.guard(entry, () => {
          switch (contact.kind) {
            case "COLLISION_ENTER":
              entry.script.onCollisionEnter2D(other);
              break;
            case "COLLISION_EXIT":
              entry.script.onCollisionExit2D(other);
              break;
            case "SENSOR_ENTER":
              entry.script.onTriggerEnter2D(other);
              break;
            case "SENSOR_EXIT":
              entry.script.onTriggerExit2D(other);
              break;
          }
        });
      }
    };
    deliver(contact.entityA, contact.entityB);
    deliver(contact.entityB, contact.entityA);
  }

  /** Calls the 3D contact callbacks of the scripts on both entities of a contact, like the desktop's `ScriptContactForwarder`. */
  private deliverContact(contact: Contact): void {
    const deliver = (entity: Contact["entityA"], other: Contact["entityB"]): void => {
      // A callback on the first entity may have deactivated either one
      if (!entity.isActiveInHierarchy) return;
      for (const entry of [...this.scripts]) {
        if (entry.script.entity !== entity) continue;
        this.guard(entry, () => {
          switch (contact.kind) {
            case "COLLISION_ENTER":
              entry.script.onCollisionEnter3D(other);
              break;
            case "COLLISION_EXIT":
              entry.script.onCollisionExit3D(other);
              break;
            case "TRIGGER_ENTER":
              entry.script.onTriggerEnter3D(other);
              break;
            case "TRIGGER_EXIT":
              entry.script.onTriggerExit3D(other);
              break;
          }
        });
      }
    };
    deliver(contact.entityA, contact.entityB);
    deliver(contact.entityB, contact.entityA);
  }

  /** A failure in the 3D physics stops it and is reported, so one bad frame does not freeze the whole game. */
  /**
   * Makes a rig of every enabled Camera Rig component (TD-050, TD-064). A rig whose target does not exist is reported like a script that
   * failed to start, naming the rig's entity, and stays still; the game goes on.
   */
  private startCameraRigs(built: BuiltScene): void {
    built.game.rigs.clear();
    for (const entity of built.game.entities) {
      for (const component of entity.components) {
        if (!CameraRig.isRig(component)) continue;
        const rig = new CameraRig(entity, component.data);
        try {
          rig.start((name) => built.game.findByName(name));
          built.game.rigs.set(entity.id, rig);
        } catch (error) {
          this.onScriptFailure({ className: "CameraRig", entityName: entity.name, error });
        }
      }
    }
  }

  /** Tells the page about colliders the physics refused (a mesh collider on a moving body, a model that did not load), once each. */
  private reportPhysicsWarnings(): void {
    const warnings = this.physics3d?.getWarnings() ?? [];
    for (; this.reportedPhysicsWarnings < warnings.length; this.reportedPhysicsWarnings++) {
      this.onAssetProblem(warnings[this.reportedPhysicsWarnings]!);
    }
  }

  private guardPhysics3D(run: () => void): void {
    try {
      run();
    } catch (error) {
      this.physics3d?.dispose();
      this.physics3d = null;
      for (const entry of this.scripts) entry.script.physics3d = null;
      this.onScriptFailure({ className: "Physics3D", entityName: "(3D physics)", error });
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
      this.guardPhysics3D(() => this.physics3d?.update(delta));
      this.reportPhysicsWarnings();
      for (const animator of this.animators) animator.update(delta);
      for (const sprite of built.sprites) sprite.update(delta);
      for (const instance of [...this.graphInstances]) instance.tick(delta);
      for (const entry of [...this.scripts]) {
        this.guard(entry, () => entry.script.onUpdate(delta));
      }
      // After physics and scripts: a rig sees the pose a character just reached and the turn a script just asked for
      for (const rig of built.game.rigs.values()) rig.update(delta, this.physics3d);
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
