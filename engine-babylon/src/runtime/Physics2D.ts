import { Quaternion, Vector3 } from "@babylonjs/core";
import { Body, Box, Vec2, World, type BodyType } from "planck";
import type { GameEntity, GameScene } from "./GameEntity";
import { bool, num, str } from "../scene/types";

/** Scene units (pixels) per physics metre; the desktop engine uses the same value. */
export const PIXELS_PER_METER = 100;
export const FIXED_TIMESTEP = 1 / 60;
const VELOCITY_ITERATIONS = 6;
const POSITION_ITERATIONS = 2;
/** A long frame (a background tab, a breakpoint) is cut to this, so physics never spirals. */
const MAX_FRAME_DELTA = 0.1;
const MAX_SUB_STEPS = 5;
const GRAVITY = -9.81;

/**
 * 2D rigid bodies for entities with RigidBody2D and BoxCollider2D components, simulated with planck.js (a JavaScript
 * port of Box2D, the engine the desktop runtime uses). Bodies are created from the scene when play starts and move
 * their entities every step.
 */
export class Physics2D {
  private readonly world = new World({ gravity: new Vec2(0, GRAVITY) });
  private readonly bodies: { entity: GameEntity; body: Body }[] = [];
  private accumulator = 0;

  constructor(scene: GameScene) {
    for (const entity of scene.entities) {
      if (!entity.node.isEnabled()) continue;
      const rigidBody = entity.getComponent("RigidBody2DComponent");
      const boxes = entity.getComponents("BoxCollider2DComponent");
      if (!rigidBody && boxes.length === 0) continue;

      entity.node.computeWorldMatrix(true);
      const scale = new Vector3();
      const rotation = new Quaternion();
      const position = new Vector3();
      entity.node.getWorldMatrix().decompose(scale, rotation, position);
      const angle = rotation.toEulerAngles().z;

      // Colliders without a rigid body are static, as on the desktop
      const d = rigidBody?.data;
      const type = (d ? str(d, "bodyType") : "STATIC").toLowerCase() as BodyType;
      const body = this.world.createBody({
        type,
        position: new Vec2(position.x / PIXELS_PER_METER, position.y / PIXELS_PER_METER),
        angle,
        gravityScale: d ? num(d, "gravityScale", 1) : 1,
        linearDamping: d ? num(d, "linearDamping", 0) : 0,
        angularDamping: d ? num(d, "angularDamping", 0) : 0,
        fixedRotation: d ? bool(d, "fixedRotation", false) : false,
        bullet: d ? bool(d, "bullet", false) : false,
        linearVelocity: d
          ? new Vec2(num(d, "initialLinearVelocityX", 0), num(d, "initialLinearVelocityY", 0))
          : new Vec2(0, 0),
        angularVelocity: d ? num(d, "initialAngularVelocity", 0) : 0,
      });

      const scaleX = Math.abs(scale.x) < 1e-4 ? 1 : Math.abs(scale.x);
      const scaleY = Math.abs(scale.y) < 1e-4 ? 1 : Math.abs(scale.y);
      for (const box of boxes) {
        const b = box.data;
        const halfWidth = (num(b, "width") * scaleX) / 2 / PIXELS_PER_METER;
        const halfHeight = (num(b, "height") * scaleY) / 2 / PIXELS_PER_METER;
        const center = new Vec2(
          (num(b, "offsetX", 0) * scale.x) / PIXELS_PER_METER,
          (num(b, "offsetY", 0) * scale.y) / PIXELS_PER_METER,
        );
        body.createFixture({
          shape: new Box(halfWidth, halfHeight, center, 0),
          density: num(b, "density", 0),
          friction: num(b, "friction", 0.2),
          restitution: num(b, "restitution", 0),
          isSensor: bool(b, "isSensor", false),
          filterCategoryBits: num(b, "categoryBits", 1),
          filterMaskBits: num(b, "maskBits", 0xffff),
        });
      }
      this.bodies.push({ entity, body });
    }
  }

  get bodyCount(): number {
    return this.bodies.length;
  }

  /** Advances the simulation by {@code deltaTime} seconds in fixed steps and moves the dynamic entities. */
  update(deltaTime: number): void {
    this.accumulator += Math.min(deltaTime, MAX_FRAME_DELTA);
    let steps = 0;
    while (this.accumulator >= FIXED_TIMESTEP && steps < MAX_SUB_STEPS) {
      this.world.step(FIXED_TIMESTEP, VELOCITY_ITERATIONS, POSITION_ITERATIONS);
      this.accumulator -= FIXED_TIMESTEP;
      steps++;
    }
    if (this.accumulator > FIXED_TIMESTEP) this.accumulator = 0;
    this.syncEntities();
  }

  private syncEntities(): void {
    for (const { entity, body } of this.bodies) {
      if (body.getType() === "static") continue;
      const position = body.getPosition();
      const z = entity.node.getAbsolutePosition().z;
      entity.node.setAbsolutePosition(new Vector3(position.x * PIXELS_PER_METER, position.y * PIXELS_PER_METER, z));
      entity.node.rotationQuaternion = Quaternion.RotationAxis(new Vector3(0, 0, 1), body.getAngle());
    }
  }
}
