import { registerJavaMethodNodes } from "./javaMethods";
import { registerComponentNodes, type GraphComponent } from "./components";
import { parseKeyNames } from "./keys";
import { defaultValue, type Graph, type GraphNode, type ValueType, type Vec3 } from "./graph";

/** An entity as the graph nodes see it; the web player adapts its `GameEntity` to this. */
export interface GraphEntity {
  /** Curated component API bridge; absent on hosts that do not provide components. */
  getComponent?(type: string): GraphComponent | null;
  readonly name: string;
  getWorldPosition(): Vec3;
  setWorldPosition(position: Vec3): void;
  /** Turns by yaw, pitch and roll in degrees. */
  rotate(yaw: number, pitch: number, roll: number): void;
  /** Removes the entity from the scene. */
  destroy(): void;
}

export interface PinDef {
  /** Component class of an OBJECT pin; unlike a generic object, incompatible components cannot connect. */
  objectClass?: string;
  id: string;
  label: string;
  /** Execution pins carry control flow; data pins carry a value of {@link type}. */
  exec: boolean;
  input: boolean;
  type?: ValueType;
}

export type NodeKind = "event" | "action" | "pure";

/** What a node's behaviour can do while it runs; {@link GraphInstance} implements it. */
export interface NodeContext {
  input(pinId: string): unknown;
  inputFloat(pinId: string): number;
  inputBoolean(pinId: string): boolean;
  inputString(pinId: string): string;
  inputVector(pinId: string): Vec3;
  /** The wired entity, or the graph's own entity when the pin is not wired. */
  inputEntity(pinId: string): GraphEntity | null;
  output(pinId: string, value: unknown): void;
  property(name: string): string | undefined;
  continueWith(pinId: string): void;
  continueAfter(seconds: number, pinId: string): void;
  /** Synchronous Repeat body iterations, then Completed. Delays inside the body are not awaited. */
  repeat(count: number): void;
  self(): GraphEntity;
  getVariable(name: string): unknown;
  setVariable(name: string, value: unknown): void;
  log(message: string): void;
  /** Whether any of the keys (`KeyboardEvent.code` values) is held down. */
  isKeyDown(codes: string[]): boolean;
  error(message: string): Error;
}

/** A kind of node offered in the editor, and what it does when it runs. */
export interface NodeType {
  id: string;
  title: string;
  category: string;
  kind: NodeKind;
  /** Settings stored on each node (not pins): the variable or custom event name. */
  properties: string[];
  /** Pins of a node of this type; variable nodes take theirs from the variable's type. */
  pins(node: GraphNode, graph: Graph): PinDef[];
  /** Runs an action or computes a pure node; events have no behaviour of their own. */
  run?(context: NodeContext): void;
}

export const EXEC_IN = "exec";
export const EXEC_OUT = "then";
export const SEQUENCE_OUTPUTS = 3;
/** Maximum iterations of a single Repeat, matching desktop LoopNodes.MAX_ITERATIONS. */
export const MAX_LOOP_ITERATIONS = 1_000;

export const NODE = {
  beginPlay: "event.beginPlay",
  tick: "event.tick",
  collision: "event.collision",
  trigger: "event.trigger",
  custom: "event.custom",
  branch: "flow.branch",
  sequence: "flow.sequence",
  delay: "flow.delay",
  print: "action.print",
  setPosition: "action.setPosition",
  moveBy: "action.moveBy",
  rotateBy: "action.rotateBy",
  destroy: "action.destroy",
  getVariable: "variable.get",
  setVariable: "variable.set",
  self: "data.self",
  getPosition: "data.getPosition",
  getName: "data.getName",
  add: "math.add",
  subtract: "math.subtract",
  multiply: "math.multiply",
  keyDown: "input.keyDown",
  axis: "input.axis",
  sine: "math.sin",
  cosine: "math.cos",
  divide: "math.divide",
  greater: "math.greater",
  less: "math.less",
  equal: "math.equal",
  and: "logic.and",
  or: "logic.or",
  not: "logic.not",
  makeVector: "vector.make",
  breakVector: "vector.break",
  addVectors: "vector.add",
  scaleVector: "vector.scale",
  append: "text.append",
  numberToText: "text.fromFloat",
} as const;

export const PIN_DELTA_TIME = "deltaTime";
export const PIN_OTHER = "other";
export const PROPERTY_NAME = "name";
export const PROPERTY_VARIABLE = "variable";

const execIn = (): PinDef => ({ id: EXEC_IN, label: "", exec: true, input: true });
const execOut = (id: string = EXEC_OUT, label = ""): PinDef => ({ id, label, exec: true, input: false });
const dataIn = (id: string, label: string, type: ValueType): PinDef => ({ id, label, exec: false, input: true, type });
const dataOut = (id: string, label: string, type: ValueType): PinDef => ({ id, label, exec: false, input: false, type });

interface Spec {
  id: string;
  title: string;
  category: string;
  kind: NodeKind;
  /** Pins besides the execution pins every event and action has. */
  pins?: PinDef[];
  /** Replaces the single "then" output (Branch, Sequence, Delay). */
  execOutputs?: PinDef[];
  properties?: string[];
  dynamic?: (node: GraphNode, graph: Graph) => PinDef[];
  run?: (context: NodeContext) => void;
}

function define(spec: Spec): NodeType {
  const fixed: PinDef[] = [];
  if (spec.kind === "action") fixed.push(execIn());
  if (spec.kind !== "pure") fixed.push(...(spec.execOutputs ?? [execOut()]));
  fixed.push(...(spec.pins ?? []));
  return {
    id: spec.id,
    title: spec.title,
    category: spec.category,
    kind: spec.kind,
    properties: spec.properties ?? [],
    pins: spec.dynamic ?? (() => fixed),
    run: spec.run,
  };
}

/** The set of node types a graph can use, by id. */
export class NodeRegistry {
  private readonly types = new Map<string, NodeType>();

  register(type: NodeType): void {
    if (this.types.has(type.id)) throw new Error(`Node type ${type.id} is already registered`);
    this.types.set(type.id, type);
  }

  get(id: string): NodeType | undefined {
    return this.types.get(id);
  }

  require(id: string): NodeType {
    const type = this.types.get(id);
    if (!type) throw new Error(`Unknown node type ${id}`);
    return type;
  }

  all(): NodeType[] {
    return [...this.types.values()];
  }

  /** The pin {@code pinId} of {@code node}, or undefined. */
  pin(node: GraphNode, graph: Graph, pinId: string): PinDef | undefined {
    return this.get(node.type)?.pins(node, graph).find((pin) => pin.id === pinId);
  }
}

/** "3" for whole numbers, otherwise up to three decimals ("2.5", "0.333"), as the desktop's Number to Text node. */
export function formatNumber(value: number): string {
  if (Number.isInteger(value)) return String(value);
  return value.toFixed(3).replace(/0+$/, "").replace(/\.$/, "");
}

function target(c: NodeContext): GraphEntity {
  const entity = c.inputEntity("target");
  if (!entity) throw c.error("Target entity is missing");
  return entity;
}

function variableName(c: NodeContext): string {
  const name = c.property(PROPERTY_VARIABLE);
  if (!name || !name.trim()) throw c.error("No variable chosen");
  return name;
}

function floatMath(id: string, title: string, op: (a: number, b: number) => number): Spec {
  return {
    id, title, category: "Math", kind: "pure",
    pins: [dataIn("a", "A", "FLOAT"), dataIn("b", "B", "FLOAT"), dataOut("result", "Result", "FLOAT")],
    run: (c) => c.output("result", op(c.inputFloat("a"), c.inputFloat("b"))),
  };
}

function degreesFunction(id: string, title: string, fn: (radians: number) => number): Spec {
  return {
    id, title, category: "Math", kind: "pure",
    pins: [dataIn("degrees", "Degrees", "FLOAT"), dataOut("result", "Result", "FLOAT")],
    run: (c) => c.output("result", Math.fround(fn((c.inputFloat("degrees") * Math.PI) / 180))),
  };
}

/** Whether any of the comma separated libGDX key names is held down; an unknown name is an error that names the node. */
function anyKeyDown(c: NodeContext, names: string): boolean {
  let keys;
  try {
    keys = parseKeyNames(names);
  } catch (error) {
    throw c.error(error instanceof Error ? error.message : String(error));
  }
  return c.isKeyDown(keys.map((key) => key.code));
}

function comparison(id: string, title: string, test: (a: number, b: number) => boolean): Spec {
  return {
    id, title, category: "Math", kind: "pure",
    pins: [dataIn("a", "A", "FLOAT"), dataIn("b", "B", "FLOAT"), dataOut("result", "Result", "BOOLEAN")],
    run: (c) => c.output("result", test(c.inputFloat("a"), c.inputFloat("b"))),
  };
}

/** The node set shipped with the engine, the same ids and pins as the desktop's `BuiltInNodes`. */
export function createDefaultRegistry(): NodeRegistry {
  const registry = new NodeRegistry();
  registerComponentNodes(registry);
  registry.register(define({ id: "flow.repeat", title: "Repeat", category: "Flow", kind: "action",
    execOutputs: [execOut("body", "Body"), execOut("completed", "Completed")],
    pins: [dataIn("count", "Count", "INTEGER"), dataOut("index", "Index", "INTEGER")],
    run: (c) => {
      const count = c.inputFloat("count");
      if (!Number.isInteger(count) || count < 0 || count > MAX_LOOP_ITERATIONS) throw c.error(`Repeat count must be within 0..${MAX_LOOP_ITERATIONS}: ${count}`);
      c.repeat(count);
    },
  }));
  const specs: Spec[] = [
    // Events
    { id: NODE.beginPlay, title: "Event Begin Play", category: "Events", kind: "event" },
    { id: NODE.tick, title: "Event Tick", category: "Events", kind: "event", pins: [dataOut(PIN_DELTA_TIME, "Delta Seconds", "FLOAT")] },
    { id: NODE.collision, title: "Event Collision", category: "Events", kind: "event", pins: [dataOut(PIN_OTHER, "Other", "ENTITY")] },
    { id: NODE.trigger, title: "Event Trigger Enter", category: "Events", kind: "event", pins: [dataOut(PIN_OTHER, "Other", "ENTITY")] },
    { id: NODE.custom, title: "Custom Event", category: "Events", kind: "event", properties: [PROPERTY_NAME] },

    // Flow control
    {
      id: NODE.branch, title: "Branch", category: "Flow", kind: "action",
      execOutputs: [execOut("true", "True"), execOut("false", "False")],
      pins: [dataIn("condition", "Condition", "BOOLEAN")],
      run: (c) => c.continueWith(c.inputBoolean("condition") ? "true" : "false"),
    },
    {
      id: NODE.sequence, title: "Sequence", category: "Flow", kind: "action",
      execOutputs: Array.from({ length: SEQUENCE_OUTPUTS }, (_, i) => execOut(`then${i}`, `Then ${i}`)),
      run: (c) => {
        for (let i = 0; i < SEQUENCE_OUTPUTS; i++) c.continueWith(`then${i}`);
      },
    },
    {
      id: NODE.delay, title: "Delay", category: "Flow", kind: "action",
      execOutputs: [execOut("completed", "Completed")],
      pins: [dataIn("seconds", "Seconds", "FLOAT")],
      run: (c) => {
        const seconds = c.inputFloat("seconds");
        if (!(seconds >= 0) || !Number.isFinite(seconds)) throw c.error(`Delay needs zero or more seconds, got ${seconds}`);
        c.continueAfter(seconds, "completed");
      },
    },

    // Actions
    {
      id: NODE.print, title: "Print", category: "Actions", kind: "action",
      pins: [dataIn("message", "Message", "STRING")],
      run: (c) => {
        c.log(c.inputString("message"));
        c.continueWith(EXEC_OUT);
      },
    },
    {
      id: NODE.setPosition, title: "Set Position", category: "Actions", kind: "action",
      pins: [dataIn("target", "Target", "ENTITY"), dataIn("position", "Position", "VECTOR3")],
      run: (c) => {
        target(c).setWorldPosition(c.inputVector("position"));
        c.continueWith(EXEC_OUT);
      },
    },
    {
      id: NODE.moveBy, title: "Move By", category: "Actions", kind: "action",
      pins: [dataIn("target", "Target", "ENTITY"), dataIn("offset", "Offset", "VECTOR3")],
      run: (c) => {
        const entity = target(c);
        const at = entity.getWorldPosition();
        const offset = c.inputVector("offset");
        entity.setWorldPosition({ x: at.x + offset.x, y: at.y + offset.y, z: at.z + offset.z });
        c.continueWith(EXEC_OUT);
      },
    },
    {
      id: NODE.rotateBy, title: "Rotate By", category: "Actions", kind: "action",
      pins: [dataIn("target", "Target", "ENTITY"), dataIn("degrees", "Yaw Pitch Roll", "VECTOR3")],
      run: (c) => {
        const degrees = c.inputVector("degrees");
        target(c).rotate(degrees.x, degrees.y, degrees.z);
        c.continueWith(EXEC_OUT);
      },
    },
    {
      id: NODE.destroy, title: "Destroy Entity", category: "Actions", kind: "action",
      pins: [dataIn("target", "Target", "ENTITY")],
      run: (c) => {
        target(c).destroy();
        c.continueWith(EXEC_OUT);
      },
    },

    // Variables
    {
      id: NODE.getVariable, title: "Get Variable", category: "Variables", kind: "pure",
      properties: [PROPERTY_VARIABLE],
      dynamic: (node, graph) => {
        const variable = graph.variable(node.properties[PROPERTY_VARIABLE] ?? "");
        return variable ? [dataOut("value", variable.name, variable.type)] : [];
      },
      run: (c) => c.output("value", c.getVariable(variableName(c))),
    },
    {
      id: NODE.setVariable, title: "Set Variable", category: "Variables", kind: "action",
      properties: [PROPERTY_VARIABLE],
      dynamic: (node, graph) => {
        const variable = graph.variable(node.properties[PROPERTY_VARIABLE] ?? "");
        return variable
          ? [execIn(), execOut(EXEC_OUT), dataIn("value", variable.name, variable.type), dataOut("result", "", variable.type)]
          : [execIn(), execOut(EXEC_OUT)];
      },
      run: (c) => {
        const name = variableName(c);
        c.setVariable(name, c.input("value"));
        c.output("result", c.getVariable(name));
        c.continueWith(EXEC_OUT);
      },
    },

    // Entities
    { id: NODE.self, title: "Self", category: "Entities", kind: "pure", pins: [dataOut("entity", "Self", "ENTITY")], run: (c) => c.output("entity", c.self()) },
    {
      id: NODE.getPosition, title: "Get Position", category: "Entities", kind: "pure",
      pins: [dataIn("target", "Target", "ENTITY"), dataOut("position", "Position", "VECTOR3")],
      run: (c) => c.output("position", target(c).getWorldPosition()),
    },
    {
      id: NODE.getName, title: "Get Name", category: "Entities", kind: "pure",
      pins: [dataIn("target", "Target", "ENTITY"), dataOut("name", "Name", "STRING")],
      run: (c) => c.output("name", target(c).name),
    },

    // Math and logic
    floatMath(NODE.add, "Add", (a, b) => a + b),
    floatMath(NODE.subtract, "Subtract", (a, b) => a - b),
    floatMath(NODE.multiply, "Multiply", (a, b) => a * b),
    {
      id: NODE.keyDown, title: "Key Down", category: "Input", kind: "pure", properties: ["key"],
      pins: [dataOut("pressed", "Pressed", "BOOLEAN")],
      run: (c) => c.output("pressed", anyKeyDown(c, c.property("key") ?? "")),
    },
    {
      id: NODE.axis, title: "Input Axis", category: "Input", kind: "pure", properties: ["negative", "positive"],
      pins: [dataOut("value", "Value", "FLOAT")],
      run: (c) => c.output("value", (anyKeyDown(c, c.property("positive") ?? "") ? 1 : 0) - (anyKeyDown(c, c.property("negative") ?? "") ? 1 : 0)),
    },
    // Angles are in degrees, like Rotate By and the transforms
    degreesFunction(NODE.sine, "Sine", Math.sin),
    degreesFunction(NODE.cosine, "Cosine", Math.cos),
    {
      id: NODE.divide, title: "Divide", category: "Math", kind: "pure",
      pins: [dataIn("a", "A", "FLOAT"), dataIn("b", "B", "FLOAT"), dataOut("result", "Result", "FLOAT")],
      run: (c) => {
        const b = c.inputFloat("b");
        if (b === 0) throw c.error("Division by zero");
        c.output("result", c.inputFloat("a") / b);
      },
    },
    comparison(NODE.greater, "Greater Than", (a, b) => a > b),
    comparison(NODE.less, "Less Than", (a, b) => a < b),
    comparison(NODE.equal, "Equal", (a, b) => a === b),
    {
      id: NODE.and, title: "And", category: "Logic", kind: "pure",
      pins: [dataIn("a", "A", "BOOLEAN"), dataIn("b", "B", "BOOLEAN"), dataOut("result", "Result", "BOOLEAN")],
      run: (c) => c.output("result", c.inputBoolean("a") && c.inputBoolean("b")),
    },
    {
      id: NODE.or, title: "Or", category: "Logic", kind: "pure",
      pins: [dataIn("a", "A", "BOOLEAN"), dataIn("b", "B", "BOOLEAN"), dataOut("result", "Result", "BOOLEAN")],
      run: (c) => c.output("result", c.inputBoolean("a") || c.inputBoolean("b")),
    },
    {
      id: NODE.not, title: "Not", category: "Logic", kind: "pure",
      pins: [dataIn("value", "Value", "BOOLEAN"), dataOut("result", "Result", "BOOLEAN")],
      run: (c) => c.output("result", !c.inputBoolean("value")),
    },

    // Vectors
    {
      id: NODE.makeVector, title: "Make Vector", category: "Vectors", kind: "pure",
      pins: [dataIn("x", "X", "FLOAT"), dataIn("y", "Y", "FLOAT"), dataIn("z", "Z", "FLOAT"), dataOut("vector", "Vector", "VECTOR3")],
      run: (c) => c.output("vector", { x: c.inputFloat("x"), y: c.inputFloat("y"), z: c.inputFloat("z") }),
    },
    {
      id: NODE.breakVector, title: "Break Vector", category: "Vectors", kind: "pure",
      pins: [dataIn("vector", "Vector", "VECTOR3"), dataOut("x", "X", "FLOAT"), dataOut("y", "Y", "FLOAT"), dataOut("z", "Z", "FLOAT")],
      run: (c) => {
        const v = c.inputVector("vector");
        c.output("x", v.x);
        c.output("y", v.y);
        c.output("z", v.z);
      },
    },
    {
      id: NODE.addVectors, title: "Add Vectors", category: "Vectors", kind: "pure",
      pins: [dataIn("a", "A", "VECTOR3"), dataIn("b", "B", "VECTOR3"), dataOut("result", "Result", "VECTOR3")],
      run: (c) => {
        const a = c.inputVector("a");
        const b = c.inputVector("b");
        c.output("result", { x: a.x + b.x, y: a.y + b.y, z: a.z + b.z });
      },
    },
    {
      id: NODE.scaleVector, title: "Scale Vector", category: "Vectors", kind: "pure",
      pins: [dataIn("vector", "Vector", "VECTOR3"), dataIn("factor", "Factor", "FLOAT"), dataOut("result", "Result", "VECTOR3")],
      run: (c) => {
        const v = c.inputVector("vector");
        const f = c.inputFloat("factor");
        c.output("result", { x: v.x * f, y: v.y * f, z: v.z * f });
      },
    },

    // Text
    {
      id: NODE.append, title: "Append Text", category: "Text", kind: "pure",
      pins: [dataIn("a", "A", "STRING"), dataIn("b", "B", "STRING"), dataOut("result", "Result", "STRING")],
      run: (c) => c.output("result", c.inputString("a") + c.inputString("b")),
    },
    {
      id: NODE.numberToText, title: "Number to Text", category: "Text", kind: "pure",
      pins: [dataIn("value", "Value", "FLOAT"), dataOut("text", "Text", "STRING")],
      run: (c) => c.output("text", formatNumber(c.inputFloat("value"))),
    },
  ];
  for (const spec of specs) registry.register(define(spec));
  // The Java method nodes that have a web version (TD-065)
  registerJavaMethodNodes(registry);
  return registry;
}

/** A new node of {@code type} for the editor, with default settings. */
export function newNode(registry: NodeRegistry, graph: Graph, typeId: string, x: number, y: number): GraphNode {
  const type = registry.require(typeId);
  const properties: Record<string, string> = {};
  for (const name of type.properties) {
    properties[name] = name === PROPERTY_VARIABLE ? (graph.variables[0]?.name ?? "") : "";
  }
  return { id: graph.newNodeId(), type: typeId, x, y, literals: {}, properties };
}

/** The value a data input shows when nothing is wired or typed in. */
export function pinDefault(pin: PinDef): unknown {
  return pin.type ? defaultValue(pin.type) : null;
}
