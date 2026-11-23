import type { ValueType } from "./graph";
import type { NodeRegistry, PinDef } from "./nodes";

/** A runtime component reference. Only the methods registered below can be called by component nodes. */
export interface GraphComponent {
  readonly type: string;
  call(method: string, args: unknown[]): unknown;
}

const RENDER = "io.github.ldev22live.gdxunity.engine.render.components.";
const AUDIO = "io.github.ldev22live.gdxunity.engine.scene.audio.";
/** Mirrors ComponentNodes.API. Getter results are pure; setters and playback are execution nodes. */
export const COMPONENT_API: readonly [string, string, ValueType | null, ...ValueType[]][] = [
  [RENDER + "ShapeRendererComponent", "getWidth()", "FLOAT"],
  [RENDER + "ShapeRendererComponent", "getHeight()", "FLOAT"],
  [RENDER + "TextComponent", "getText()", "STRING"],
  [RENDER + "TextComponent", "setText(java.lang.String)", null, "STRING"],
  [RENDER + "CameraComponent", "getFieldOfView()", "FLOAT"],
  [RENDER + "CameraComponent", "setFieldOfView(float)", null, "FLOAT"],
  [RENDER + "SpriteAnimatorComponent", "play(java.lang.String)", null, "STRING"],
  [RENDER + "TilemapComponent", "isSolidAt(float,float)", "BOOLEAN", "FLOAT", "FLOAT"],
  [AUDIO + "AudioSourceComponent", "getVolume()", "FLOAT"],
  [AUDIO + "AudioSourceComponent", "setVolume(float)", null, "FLOAT"],
  [AUDIO + "AudioSourceComponent", "play()", null],
];

/** Registers typed lookup nodes and the curated method nodes with desktop-compatible ids and pins. */
export function registerComponentNodes(registry: NodeRegistry): void {
  const registered = new Set<string>();
  for (const [owner, method, result, ...args] of COMPONENT_API) {
    const name = owner.slice(owner.lastIndexOf(".") + 1);
    const objectPin = (id: string, input: boolean): PinDef => ({ id, label: id, type: "OBJECT", objectClass: name, input, exec: false });
    if (!registered.has(name)) {
      registered.add(name);
      registry.register({ id: `component.get:${name}`, title: `Get ${name}`, category: "Components", kind: "pure", properties: [],
        pins: () => [{ id: "target", label: "Entity", type: "ENTITY", input: true, exec: false }, objectPin("component", false)],
        run: (c) => {
          const entity = c.inputEntity("target");
          if (!entity) throw c.error("Target entity is missing");
          if (!entity.getComponent) throw c.error("This host does not provide components");
          const component = entity.getComponent(name);
          if (!component) throw c.error(`Entity ${entity.name} has no ${name}`);
          c.output("component", component);
        },
      });
    }
    const pins: PinDef[] = [objectPin("target", true), ...args.map((type, i): PinDef => ({ id: `p${i}`, label: `p${i}`, type, input: true, exec: false }))];
    if (result) pins.push({ id: "result", label: "Result", type: result, input: false, exec: false });
    else pins.unshift({ id: "exec", label: "", exec: true, input: true }, { id: "then", label: "", exec: true, input: false });
    registry.register({ id: `java:${owner}#${method}`, title: `${name}.${method}`, category: `Components: ${name}`,
      kind: result ? "pure" : "action", properties: [], pins: () => pins,
      run: (c) => {
        const target = c.input("target") as GraphComponent | null;
        if (!target || target.type !== name) throw c.error(`Target must be a ${name}`);
        const value = target.call(method, args.map((_, i) => c.input(`p${i}`)));
        if (result) c.output("result", value);
        else c.continueWith("then");
      },
    });
  }
}
