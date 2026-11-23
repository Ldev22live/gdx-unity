import { Quaternion, Vector3 } from "@babylonjs/core";
import { Body, Box, Vec2, World, type BodyType, type Contact } from "planck";
import type { GameEntity, GameScene } from "./GameEntity";
import { bool, num, str } from "../scene/types";
import { parseTilemap, solidBoxes } from "../sprites/sprite";

/** Scene units (pixels) per physics metre; the desktop engine uses the same value. */
export const PIXELS_PER_METER = 100;
export const FIXED_TIMESTEP = 1 / 60;
const VELOCITY_ITERATIONS = 6;
const POSITION_ITERATIONS = 2;
/** A long frame (a background tab, a breakpoint) is cut to this, so physics never spirals. */
const MAX_FRAME_DELTA = 0.1;
const MAX_SUB_STEPS = 5;
const GRAVITY = -9.81;

/** What happened between two entities' 2D colliders: solid ones started or stopped touching, or a sensor overlap began or ended. */
export type Contact2DKind = "COLLISION_ENTER" | "COLLISION_EXIT" | "SENSOR_ENTER" | "SENSOR_EXIT";

export interface Contact2D {
  entityA: GameEntity;
  entityB: GameEntity;
  kind: Contact2DKind;
}

export type Contact2DListener = (contact: Contact2D) => void;

/**
 * 2D rigid bodies for entities with RigidBody2D and BoxCollider2D components, simulated with planck.js (a JavaScript
 * port of Box2D, the engine the desktop runtime uses). Bodies are created from the scene when play starts and move
 * their entities every step.
 */
export class Physics2D {
  private readonly world = new World({ gravity: new Vec2(0, GRAVITY) });
  private readonly bodies: { entity: GameEntity; body: Body }[] = [];
  private accumulator = 0;
  private readonly entitiesByBody = new Map<Body, GameEntity>();
  /** Contacts found inside a step, delivered after it (as on the desktop), so a callback may change the scene safely. */
  private readonly queued: Contact2D[] = [];
  private readonly listeners: Contact2DListener[] = [];

  constructor(scene: GameScene) {
    for (const entity of scene.entities) {
      if (!entity.node.isEnabled()) continue;
      const rigidBody = entity.getComponent("RigidBody2DComponent");
      const boxes = entity.getComponents("BoxCollider2DComponent");
      // A tile map with collision is a set of fixed boxes (TD-066); the entity gets one static body for them, like the desktop
      const tilemapData = entity.getComponent("TilemapComponent");
      const tilemap = tilemapData ? parseTilemap(tilemapData.data) : null;
      const tileBoxes = tilemap && tilemap.collides ? solidBoxes(tilemap) : [];
      if (!rigidBody && boxes.length === 0 && tileBoxes.length === 0) continue;

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
      if (tilemap) {
        const mapHeight = tilemap.rows * tilemap.tileHeight;
        for (const cells of tileBoxes) {
          // Row 0 is the top row; the entity's position is the map's bottom left corner
          const width = cells.cols * tilemap.tileWidth * scaleX;
          const height = cells.rows * tilemap.tileHeight * scaleY;
          const center = new Vec2(
            ((cells.col + cells.cols / 2) * tilemap.tileWidth * scale.x) / PIXELS_PER_METER,
            ((mapHeight - (cells.row + cells.rows / 2) * tilemap.tileHeight) * scale.y) / PIXELS_PER_METER,
          );
          body.createFixture({ shape: new Box(width / 2 / PIXELS_PER_METER, height / 2 / PIXELS_PER_METER, center, 0), friction: 0.2 });
        }
      }
      this.bodies.push({ entity, body });
      this.entitiesByBody.set(body, entity);
    }
    this.world.on("begin-contact", (contact) => this.queue(contact, true));
    this.world.on("end-contact", (contact) => this.queue(contact, false));
  }

  /** Calls {@code listener} for every contact, once per contact, after the step that found it. */
  addContactListener(listener: Contact2DListener): void {
    this.listeners.push(listener);
  }

  private queue(contact: Contact, began: boolean): void {
    const fixtureA = contact.getFixtureA();
    const fixtureB = contact.getFixtureB();
    const entityA = this.entitiesByBody.get(fixtureA.getBody());
    const entityB = this.entitiesByBody.get(fixtureB.getBody());
    if (!entityA || !entityB || entityA === entityB) return;
    const sensor = fixtureA.isSensor() || fixtureB.isSensor();
    const kind: Contact2DKind = sensor ? (began ? "SENSOR_ENTER" : "SENSOR_EXIT") : began ? "COLLISION_ENTER" : "COLLISION_EXIT";
    this.queued.push({ entityA, entityB, kind });
  }

  private flushContacts(): void {
    for (const contact of this.queued.splice(0)) {
      for (const listener of [...this.listeners]) listener(contact);
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
    this.flushContacts();
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
