/**
 * The graph model and `.graph` file format of the desktop editor's visual scripting (TD-029), so one file runs on the
 * desktop and in the web player. A file looks like:
 *
 *   { "schemaVersion": 1, "kind": "graph", "name": "Door",
 *     "variables": [ { "name": "speed", "type": "FLOAT", "value": 2 } ],
 *     "nodes": [ { "id": "n1", "type": "event.tick", "x": 0, "y": 0,
 *                  "literals": [ { "pin": "a", "type": "FLOAT", "value": 1 } ],
 *                  "properties": [ { "name": "variable", "type": "STRING", "value": "speed" } ] } ],
 *     "links": [ { "from": "n1", "fromPin": "then", "to": "n2", "toPin": "exec" } ] }
 *
 * Version 2 (TD-070) adds "events" (declared custom events, with typed params) and "functions" (typed inputs and outputs and a body
 * of nodes and links). A graph without them is still written as version 1; version 1 files are read as before.
 */

/** INTEGER (whole numbers) came with the Java method nodes (TD-060, TD-065); it reads as a FLOAT where a float is wanted, not the other way. */
export type ValueType = "FLOAT" | "BOOLEAN" | "STRING" | "VECTOR3" | "ENTITY" | "INTEGER" | "OBJECT";
export const VALUE_TYPES: readonly ValueType[] = ["FLOAT", "BOOLEAN", "STRING", "VECTOR3", "ENTITY", "INTEGER"];

export interface Vec3 {
  x: number;
  y: number;
  z: number;
}

/** A value typed into a pin or stored in a variable; entities cannot be stored in a file. */
export type Literal = number | boolean | string | Vec3;

export interface GraphVariable {
  name: string;
  type: ValueType;
  /** Initial value; null only for an entity variable. */
  value: Literal | null;
}

export interface GraphNode {
  id: string;
  type: string;
  x: number;
  y: number;
  /** Values typed into unconnected data inputs, by pin id. */
  literals: Record<string, Literal>;
  /** Pins whose literal is a whole number (INTEGER); every other number literal is a FLOAT. Kept so a file is written as it was read. */
  integerLiterals?: Set<string>;
  /** Settings of the node that are not pins (a variable or custom event name), by name. */
  properties: Record<string, string>;
}

export interface GraphLink {
  from: string;
  fromPin: string;
  to: string;
  toPin: string;
}

/** The newest version this code reads. */
export const GRAPH_SCHEMA_VERSION = 2;
/** The version of a graph without functions and declared events. */
export const GRAPH_SCHEMA_BASIC = 1;

/** One typed input or output of a function, or parameter of a custom event; its name is also the id of its pin. */
export interface GraphParam {
  name: string;
  type: ValueType;
}

/** A function of a graph: typed inputs and outputs and a body graph that starts at one Function Entry node (TD-070). */
export interface GraphFunction {
  name: string;
  inputs: GraphParam[];
  outputs: GraphParam[];
  body: Graph;
}

/** A custom event a graph declares, with its parameters (TD-070). */
export interface GraphEventDef {
  name: string;
  params: GraphParam[];
}

const NAME_PATTERN = /^[A-Za-z_][A-Za-z0-9_]*$/;

/** Whether `name` can be a function, event or parameter name (also not one of the execution pin ids). */
export function isValidName(name: string): boolean {
  return NAME_PATTERN.test(name) && name !== "exec" && name !== "then";
}

/** A parameter, checked like the desktop's `GraphParam`. @throws Error saying what is wrong. */
export function makeParam(name: string, type: ValueType): GraphParam {
  if (!isValidName(name)) throw new Error(`Not a usable parameter name: '${name}'`);
  if (type === "OBJECT") throw new Error(`Parameter ${name} cannot be an object`);
  return { name, type };
}

/** Value of an unconnected pin with no literal set. */
export function defaultValue(type: ValueType): Literal | null {
  switch (type) {
    case "FLOAT":
      return 0;
    case "BOOLEAN":
      return false;
    case "STRING":
      return "";
    case "VECTOR3":
      return { x: 0, y: 0, z: 0 };
    case "ENTITY":
    case "OBJECT":
      return null;
    case "INTEGER":
      return 0;
  }
}

/** Whether {@code value} is a legal value of {@code type}. */
export function accepts(type: ValueType, value: unknown): boolean {
  switch (type) {
    case "FLOAT":
      return typeof value === "number" && !Number.isNaN(value);
    case "BOOLEAN":
      return typeof value === "boolean";
    case "STRING":
      return typeof value === "string";
    case "VECTOR3":
      return isVec3(value);
    case "ENTITY":
    case "OBJECT":
      return false;
    case "INTEGER":
      return typeof value === "number" && Number.isInteger(value);
  }
}

export function isVec3(value: unknown): value is Vec3 {
  if (typeof value !== "object" || value === null) return false;
  const v = value as Record<string, unknown>;
  return typeof v.x === "number" && typeof v.y === "number" && typeof v.z === "number";
}

/** A copy that no other owner can change (vectors are mutable). */
export function copyValue<T>(value: T): T {
  return isVec3(value) ? ({ x: value.x, y: value.y, z: value.z } as T) : value;
}

/** Nodes, wires and variables of one graph. Ids are unique within a graph. */
export class Graph {
  readonly variables: GraphVariable[] = [];
  readonly nodes: GraphNode[] = [];
  readonly links: GraphLink[] = [];
  readonly functions: GraphFunction[] = [];
  readonly events: GraphEventDef[] = [];
  /** The function this graph is the body of, or null for a graph of its own (TD-070). */
  owner: GraphFunction | null = null;
  /** For a function body: the graph the function belongs to, whose variables, functions and events it uses. */
  parent: Graph | null = null;
  private nextId = 1;

  constructor(public name: string) {}

  /** A node id not used yet ("n1", "n2", ...). */
  newNodeId(): string {
    while (this.nodes.some((node) => node.id === `n${this.nextId}`)) this.nextId++;
    return `n${this.nextId++}`;
  }

  addNode(node: GraphNode): void {
    if (this.nodes.some((other) => other.id === node.id)) throw new Error(`Duplicate node id ${node.id}`);
    this.nodes.push(node);
  }

  /** Removes the node and every wire that touches it. */
  removeNode(id: string): void {
    const index = this.nodes.findIndex((node) => node.id === id);
    if (index < 0) return;
    this.nodes.splice(index, 1);
    for (let i = this.links.length - 1; i >= 0; i--) {
      if (this.links[i]!.from === id || this.links[i]!.to === id) this.links.splice(i, 1);
    }
  }

  node(id: string): GraphNode | undefined {
    return this.nodes.find((node) => node.id === id);
  }

  requireNode(id: string): GraphNode {
    const node = this.node(id);
    if (!node) throw new Error(`No node ${id} in graph ${this.name}`);
    return node;
  }

  addLink(link: GraphLink): void {
    if (this.links.some((other) => sameLink(other, link))) return;
    this.links.push(link);
  }

  removeLink(link: GraphLink): void {
    const index = this.links.findIndex((other) => sameLink(other, link));
    if (index >= 0) this.links.splice(index, 1);
  }

  linksFrom(nodeId: string, pinId: string): GraphLink[] {
    return this.links.filter((link) => link.from === nodeId && link.fromPin === pinId);
  }

  linksInto(nodeId: string, pinId: string): GraphLink[] {
    return this.links.filter((link) => link.to === nodeId && link.toPin === pinId);
  }

  /** A variable of this graph; in a function body, of the graph the function belongs to. */
  variable(name: string): GraphVariable | undefined {
    return this.variables.find((variable) => variable.name === name) ?? this.parent?.variable(name);
  }

  /** A function of this graph; in a function body, of the graph the function belongs to. */
  functionNamed(name: string): GraphFunction | undefined {
    return this.functions.find((fn) => fn.name === name) ?? this.parent?.functionNamed(name);
  }

  /** An event declared by this graph; in a function body, by the graph the function belongs to. */
  eventNamed(name: string): GraphEventDef | undefined {
    return this.events.find((event) => event.name === name) ?? this.parent?.eventNamed(name);
  }

  /** Adds a function with an empty body. @throws Error for a bad or taken name, or inside a function body. */
  addFunction(name: string): GraphFunction {
    if (this.owner) throw new Error("A function body cannot hold functions");
    if (!isValidName(name)) throw new Error(`Not a usable function name: '${name}'`);
    if (this.functions.some((fn) => fn.name === name)) throw new Error(`Function ${name} already exists`);
    const fn: GraphFunction = { name, inputs: [], outputs: [], body: new Graph(name) };
    fn.body.owner = fn;
    fn.body.parent = this;
    this.functions.push(fn);
    return fn;
  }

  /** Declares a custom event. @throws Error for a bad or taken name, or inside a function body. */
  addEvent(name: string): GraphEventDef {
    if (this.owner) throw new Error("A function body cannot declare events");
    if (!isValidName(name)) throw new Error(`Not a usable event name: '${name}'`);
    if (this.events.some((event) => event.name === name)) throw new Error(`Event ${name} already exists`);
    const event: GraphEventDef = { name, params: [] };
    this.events.push(event);
    return event;
  }

  /** Whether the graph needs schema version 2 to be saved. */
  usesFunctionsOrEvents(): boolean {
    return this.functions.length > 0 || this.events.length > 0;
  }
}

function sameLink(a: GraphLink, b: GraphLink): boolean {
  return a.from === b.from && a.fromPin === b.fromPin && a.to === b.to && a.toPin === b.toPin;
}

interface TypedValue {
  type: string;
  value: unknown;
}

interface RawNode {
  id: string;
  type: string;
  x?: number;
  y?: number;
  literals?: ({ pin: string } & TypedValue)[];
  properties?: ({ name: string } & TypedValue)[];
}

function readBody(nodes: RawNode[] | undefined, links: GraphLink[] | undefined, target: Graph): void {
  for (const node of nodes ?? []) {
    const parsed: GraphNode = { id: node.id, type: node.type, x: node.x ?? 0, y: node.y ?? 0, literals: {}, properties: {} };
    for (const literal of node.literals ?? []) {
      parsed.literals[literal.pin] = readValue(literal.type, literal.value, `node ${node.id} pin ${literal.pin}`);
      if (literal.type === "INTEGER") (parsed.integerLiterals ??= new Set()).add(literal.pin);
    }
    for (const property of node.properties ?? []) parsed.properties[property.name] = String(property.value);
    target.addNode(parsed);
  }
  for (const link of links ?? []) target.addLink({ from: link.from, fromPin: link.fromPin, to: link.to, toPin: link.toPin });
}

function readParams(raw: { name: string; type: string }[] | undefined, what: string): GraphParam[] {
  return (raw ?? []).map((p) => {
    if (!VALUE_TYPES.includes(p.type as ValueType)) throw new Error(`${what}: unknown type ${p.type}`);
    return makeParam(p.name, p.type as ValueType);
  });
}

function readValue(type: string, value: unknown, what: string): Literal {
  if (!VALUE_TYPES.includes(type as ValueType)) throw new Error(`${what}: unknown type ${type}`);
  const valueType = type as ValueType;
  if (valueType === "ENTITY") throw new Error(`${what}: entities cannot be stored in a graph file`);
  if (valueType === "VECTOR3") {
    const v = (value ?? {}) as Partial<Vec3>;
    return { x: Number(v.x ?? 0), y: Number(v.y ?? 0), z: Number(v.z ?? 0) };
  }
  if (!accepts(valueType, value)) throw new Error(`${what}: ${JSON.stringify(value)} is not a ${type}`);
  return value as Literal;
}

/** Parses the text of a `.graph` file. @throws Error naming what is wrong. */
export function parseGraph(text: string): Graph {
  if (!text.trim()) throw new Error("Graph file is empty");
  let root: unknown;
  try {
    root = JSON.parse(text);
  } catch (error) {
    throw new Error(`Malformed graph JSON: ${error instanceof Error ? error.message : String(error)}`);
  }
  if (typeof root !== "object" || root === null) throw new Error("Graph root element must be an object");
  const data = root as {
    schemaVersion?: number;
    kind?: string;
    name?: string;
    variables?: { name: string; type: string; value: unknown }[];
    nodes?: RawNode[];
    links?: GraphLink[];
    events?: { name: string; params?: { name: string; type: string }[] }[];
    functions?: { name: string; inputs?: { name: string; type: string }[]; outputs?: { name: string; type: string }[]; nodes?: RawNode[]; links?: GraphLink[] }[];
  };
  if (typeof data.schemaVersion !== "number" || data.schemaVersion < 1 || data.schemaVersion > GRAPH_SCHEMA_VERSION) {
    throw new Error(`Unsupported graph schemaVersion: ${String(data.schemaVersion)}`);
  }
  if (data.kind !== "graph") throw new Error(`Not a graph document (kind is ${String(data.kind)})`);
  const graph = new Graph(data.name ?? "Graph");
  for (const variable of data.variables ?? []) {
    graph.variables.push({ name: variable.name, type: variable.type as ValueType, value: readValue(variable.type, variable.value, `variable ${variable.name}`) });
  }
  readBody(data.nodes, data.links, graph);
  if (data.schemaVersion >= GRAPH_SCHEMA_VERSION) {
    for (const rawEvent of data.events ?? []) {
      const event = graph.addEvent(rawEvent.name);
      event.params.push(...readParams(rawEvent.params, `event ${rawEvent.name}`));
    }
    for (const rawFunction of data.functions ?? []) {
      const fn = graph.addFunction(rawFunction.name);
      fn.inputs.push(...readParams(rawFunction.inputs, `function ${rawFunction.name} inputs`));
      fn.outputs.push(...readParams(rawFunction.outputs, `function ${rawFunction.name} outputs`));
      readBody(rawFunction.nodes, rawFunction.links, fn.body);
    }
  }
  return graph;
}

function typeOfLiteral(value: Literal): ValueType {
  if (typeof value === "number") return "FLOAT";
  if (typeof value === "boolean") return "BOOLEAN";
  if (typeof value === "string") return "STRING";
  return "VECTOR3";
}

/** The text of a `.graph` file for {@code graph}, in the format the desktop editor reads and writes. */
export function serializeGraph(graph: Graph): string {
  const nodesOf = (g: Graph) =>
    g.nodes.map((node) => ({
      id: node.id,
      type: node.type,
      x: node.x,
      y: node.y,
      literals: Object.entries(node.literals).map(([pin, value]) => ({ pin, type: node.integerLiterals?.has(pin) && typeof value === "number" ? "INTEGER" : typeOfLiteral(value), value })),
      properties: Object.entries(node.properties).map(([name, value]) => ({ name, type: "STRING", value })),
    }));
  const linksOf = (g: Graph) => g.links.map((link) => ({ from: link.from, fromPin: link.fromPin, to: link.to, toPin: link.toPin }));
  const document: Record<string, unknown> = {
    schemaVersion: graph.usesFunctionsOrEvents() ? GRAPH_SCHEMA_VERSION : GRAPH_SCHEMA_BASIC,
    kind: "graph",
    name: graph.name,
    variables: graph.variables.map((variable) => ({ name: variable.name, type: variable.type, value: variable.value })),
    nodes: nodesOf(graph),
    links: linksOf(graph),
  };
  if (graph.usesFunctionsOrEvents()) {
    document.events = graph.events.map((event) => ({ name: event.name, params: event.params.map((p) => ({ name: p.name, type: p.type })) }));
    document.functions = graph.functions.map((fn) => ({
      name: fn.name,
      inputs: fn.inputs.map((p) => ({ name: p.name, type: p.type })),
      outputs: fn.outputs.map((p) => ({ name: p.name, type: p.type })),
      nodes: nodesOf(fn.body),
      links: linksOf(fn.body),
    }));
  }
  return JSON.stringify(document, null, 2);
}

/** A copy of {@code graph} that shares nothing with it. */
export function cloneGraph(graph: Graph): Graph {
  return parseGraph(serializeGraph(graph));
}

/** Makes {@code target} hold what {@code source} holds, keeping the target object (others, like the store, point at it). */
export function replaceGraphContents(target: Graph, source: Graph): void {
  target.name = source.name;
  target.variables.splice(0, target.variables.length, ...source.variables);
  target.nodes.splice(0, target.nodes.length, ...source.nodes);
  target.links.splice(0, target.links.length, ...source.links);
  target.functions.splice(0, target.functions.length, ...source.functions);
  target.events.splice(0, target.events.length, ...source.events);
  for (const fn of target.functions) fn.body.parent = target;
}
