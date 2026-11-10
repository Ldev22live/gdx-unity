import { Vector3 } from "@babylonjs/core";
import { ScriptComponent } from "../runtime/ScriptComponent";
import { keyAxis, TextHandle } from "./util";

/** Walking speed in scene units per second (3 m/s). */
const WALK_SPEED = 300;
/** Jump take-off speed in scene units per second: about one meter of height. */
const JUMP_SPEED = 450;
const HINT = "WASD or arrows walk, Space jumps. Climb the steps and the gentle ramp, jump onto the low block, push the crates.  ";

/**
 * Walks a Character Controller 3D: the web version of `samples.walker.WalkerPlayer`. WASD or the arrow keys walk along the world axes
 * (W goes into the screen), Space jumps while standing on the ground. The "Help Text" entity shows whether the character stands on the
 * ground. The physics system moves the entity; the script only says where it wants to go.
 */
export class WalkerPlayer extends ScriptComponent {
  private label: TextHandle | null = null;

  override onStart(): void {
    const text = this.scene.findByName("Help Text")?.getComponent("TextComponent");
    this.label = text ? new TextHandle(text.data) : null;
  }

  override onUpdate(_deltaTime: number): void {
    const physics = this.physics3d;
    if (!physics) return;
    const x = keyAxis((...codes) => this.input.isDown(...codes), ["KeyA", "ArrowLeft"], ["KeyD", "ArrowRight"]);
    const z = keyAxis((...codes) => this.input.isDown(...codes), ["KeyW", "ArrowUp"], ["KeyS", "ArrowDown"]);
    const walk = new Vector3(x, 0, z);
    if (walk.lengthSquared() > 0) walk.normalize().scaleInPlace(WALK_SPEED);
    this.moveCharacter3D(walk);
    if (this.input.isJustPressed("Space")) this.jumpCharacter3D(JUMP_SPEED);
    this.label?.setText(HINT + (this.isGrounded3D() ? "On the ground" : "In the air"));
  }
}
