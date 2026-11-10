import { Vector3 } from "@babylonjs/core";
import type { GraphEntity } from "../visualscript/nodes";
import type { GameEntity, GameScene } from "./GameEntity";
import { parseTilemap, tilemapSolidAt } from "../sprites/sprite";
import { num, str } from "../scene/types";
import { COMPONENT_API, type GraphComponent } from "../visualscript/components";

/** The entity as visual script nodes see it: position, rotation and removal go through the game entity and scene. */
export function graphEntity(entity: GameEntity, game: GameScene): GraphEntity {
  const components = new Map<string, GraphComponent>();
  return {
    getComponent: (type) => {
      const known = components.get(type);
      if (known) return known;
      const value = component(entity, game, type);
      if (value) components.set(type, value);
      return value;
    },
    get name() {
      return entity.name;
    },
    getWorldPosition: () => {
      const at = entity.getWorldPosition();
      return { x: at.x, y: at.y, z: at.z };
    },
    setWorldPosition: (position) => entity.node.setAbsolutePosition(new Vector3(position.x, position.y, position.z)),
    rotate: (yaw, pitch, roll) => entity.rotate(yaw, pitch, roll),
    destroy: () => game.removeEntity(entity),
  };
}

function component(entity: GameEntity, game: GameScene, type: string): GraphComponent | null {
  const allowed = COMPONENT_API.filter(([owner]) => owner.endsWith(`.${type}`));
  if (allowed.length === 0) throw new Error(`Component ${type} is not exposed to graphs`);
  // Match the desktop's lookup: disabled components still exist and their API is available.
  const source = entity.components.find((value) => value.type === type);
  if (!source) return null;
  const map = type === "TilemapComponent" ? parseTilemap(source.data) : null;
  return { type, call: (method, args) => {
    if (!allowed.some(([, signature]) => signature === method)) throw new Error(`Component method ${type}.${method} is not exposed`);
    const data = source.data;
    switch (method) {
      case "getWidth()": return num(data, "width", 1);
      case "getHeight()": return num(data, "height", 1);
      case "getText()": return str(data, "text");
      case "setText(java.lang.String)": data["text"] = args[0] as string; return;
      case "getFieldOfView()": return num(data, "fieldOfView", 60);
      case "setFieldOfView(float)": {
        const value = args[0] as number;
        if (!(value > 0 && value < 180)) throw new Error(`Field of view must be between 0 and 180 degrees: ${value}`);
        data["fieldOfView"] = value;
        return;
      }
      case "getVolume()": return num(data, "volume", 1);
      case "setVolume(float)": {
        const value = args[0] as number;
        if (!(value >= 0 && value <= 1)) throw new Error(`Volume must be within 0 to 1: ${value}`);
        data["volume"] = value;
        return;
      }
      case "play()": if (source.enabled) game.playSource(entity, source); return;
      case "play(java.lang.String)": {
        const tag = (args[0] as string).trim() || null;
        entity.sprite?.play(tag);
        data["tag"] = tag;
        if (!entity.sprite) data["playOnStart"] = true;
        return;
      }
      case "isSolidAt(float,float)":
        return tilemapSolidAt(map!, entity.getWorldPosition(), entity.getWorldScale(), args[0] as number, args[1] as number);
      default: throw new Error(`Component method ${type}.${method} has no implementation`);
    }
  } };
}
