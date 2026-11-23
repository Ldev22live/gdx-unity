import { Vector3 } from "@babylonjs/core";
import type { GameEntity } from "../runtime/GameEntity";
import { ScriptComponent } from "../runtime/ScriptComponent";
import { ALL_LAYERS } from "../runtime/physics3d/types";
import { num, str } from "../scene/types";
import { TextHandle } from "./util";

/** How far the ray looks, in scene units. */
const RANGE = 6000;
/** Speed a kick gives a body, in scene units per second; the impulse is this times the body's mass. */
const KICK_SPEED = 900;
/** Push of a held F, in scene units per second squared, times the body's mass. */
const PUSH_ACCELERATION = 1500;
/** Bodies within this distance of the aim point are popped by E, in scene units. */
const POP_RADIUS = 260;
/** Upward speed a pop gives, in scene units per second. */
const POP_SPEED = 700;
const HINT = "Aim at a ball or crate: Space kick, hold F push, E pop the neighbours, T stop it.  Aiming at: ";

/**
 * Plays with the 3D physics sandbox through the script API: the web version of `samples.sandbox.SandboxLauncher`. It sits on
 * the camera and aims along its view. A ray finds what you look at; Space kicks it away with an impulse, F (held) pushes it with a
 * force, E pops everything within a sphere around the aim point, and T stops it with a velocity. The aimed entity's name is shown
 * on the "Help Text" entity.
 */
export class SandboxLauncher extends ScriptComponent {
  private label: TextHandle | null = null;

  override onStart(): void {
    const text = this.scene.findByName("Help Text")?.getComponent("TextComponent");
    this.label = text ? new TextHandle(text.data) : null;
  }

  override onUpdate(_deltaTime: number): void {
    const physics = this.physics3d;
    if (!physics) return;
    const forward = new Vector3(0, 0, -1).rotateByQuaternionToRef(this.entity.getWorldRotation(), new Vector3()).normalize();
    const hit = physics.raycast(this.entity.getWorldPosition(), forward, RANGE, ALL_LAYERS);
    const aimed = hit && isDynamic(hit.entity) ? hit.entity : null;
    this.label?.setText(HINT + (hit ? hit.entity.name : "nothing"));

    if (aimed && this.input.isJustPressed("Space")) physics.addImpulse(aimed, forward.scale(KICK_SPEED * massOf(aimed)));
    if (aimed && this.input.isDown("KeyF")) physics.addForce(aimed, forward.scale(PUSH_ACCELERATION * massOf(aimed)));
    if (aimed && this.input.isJustPressed("KeyT")) physics.setLinearVelocity(aimed, Vector3.Zero());
    if (hit && this.input.isJustPressed("KeyE")) {
      for (const neighbour of physics.overlapSphere(hit.point, POP_RADIUS, ALL_LAYERS)) {
        if (isDynamic(neighbour)) physics.addImpulse(neighbour, new Vector3(0, POP_SPEED * massOf(neighbour), 0));
      }
    }
  }
}

function isDynamic(entity: GameEntity): boolean {
  const body = entity.getComponent("RigidBody3DComponent");
  return body !== undefined && str(body.data, "motionType") === "DYNAMIC";
}

function massOf(entity: GameEntity): number {
  return num(entity.getComponent("RigidBody3DComponent")!.data, "mass", 1);
}
