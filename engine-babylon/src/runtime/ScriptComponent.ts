import type { GameEntity, GameScene } from "./GameEntity";
import type { Input } from "./Input";

/**
 * Base class of game scripts, the web counterpart of the desktop engine's `ScriptComponent`. A script is bound to an
 * entity in the scene file by `scriptClassName` and found in the {@link ScriptRegistry} under that name.
 */
export abstract class ScriptComponent {
  entity!: GameEntity;
  scene!: GameScene;
  input!: Input;

  /** Called once, before the first update. */
  onStart(): void {}

  /** Called every frame while playing; {@code deltaTime} is in seconds. */
  onUpdate(_deltaTime: number): void {}
}

export type ScriptFactory = () => ScriptComponent;

/** Scripts the web player can run, by the fully qualified class name the scene file uses. */
export class ScriptRegistry {
  private readonly factories = new Map<string, ScriptFactory>();

  register(className: string, factory: ScriptFactory): void {
    if (this.factories.has(className)) {
      throw new Error(`A script is already registered as ${className}`);
    }
    this.factories.set(className, factory);
  }

  has(className: string): boolean {
    return this.factories.has(className);
  }

  create(className: string): ScriptComponent {
    const factory = this.factories.get(className);
    if (!factory) throw new Error(`No web script registered as ${className}`);
    return factory();
  }
}
