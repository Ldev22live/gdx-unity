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

export const GRAPH_SCHEMA_VERSION = 1;

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

  variable(name: string): GraphVariable | undefined {
    return this.variables.find((variable) => variable.name === name);
  }
}

function sameLink(a: GraphLink, b: GraphLink): boolean {
  return a.from === b.from && a.fromPin === b.fromPin && a.to === b.to && a.toPin === b.toPin;
}

interface TypedValue {
  type: string;
  value: unknown;
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
    nodes?: { id: string; type: string; x?: number; y?: number; literals?: ({ pin: string } & TypedValue)[]; properties?: ({ name: string } & TypedValue)[] }[];
    links?: GraphLink[];
  };
  if (typeof data.schemaVersion !== "number" || data.schemaVersion < 1 || data.schemaVersion > GRAPH_SCHEMA_VERSION) {
    throw new Error(`Unsupported graph schemaVersion: ${String(data.schemaVersion)}`);
  }
  if (data.kind !== "graph") throw new Error(`Not a graph document (kind is ${String(data.kind)})`);
  const graph = new Graph(data.name ?? "Graph");
  for (const variable of data.variables ?? []) {
    graph.variables.push({ name: variable.name, type: variable.type as ValueType, value: readValue(variable.type, variable.value, `variable ${variable.name}`) });
  }
  for (const node of data.nodes ?? []) {
    const parsed: GraphNode = { id: node.id, type: node.type, x: node.x ?? 0, y: node.y ?? 0, literals: {}, properties: {} };
    for (const literal of node.literals ?? []) {
      parsed.literals[literal.pin] = readValue(literal.type, literal.value, `node ${node.id} pin ${literal.pin}`);
      if (literal.type === "INTEGER") (parsed.integerLiterals ??= new Set()).add(literal.pin);
    }
    for (const property of node.properties ?? []) parsed.properties[property.name] = String(property.value);
    graph.addNode(parsed);
  }
  for (const link of data.links ?? []) graph.addLink({ from: link.from, fromPin: link.fromPin, to: link.to, toPin: link.toPin });
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
  return JSON.stringify(
    {
      schemaVersion: GRAPH_SCHEMA_VERSION,
      kind: "graph",
      name: graph.name,
      variables: graph.variables.map((variable) => ({ name: variable.name, type: variable.type, value: variable.value })),
      nodes: graph.nodes.map((node) => ({
        id: node.id,
        type: node.type,
        x: node.x,
        y: node.y,
        literals: Object.entries(node.literals).map(([pin, value]) => ({ pin, type: node.integerLiterals?.has(pin) && typeof value === "number" ? "INTEGER" : typeOfLiteral(value), value })),
        properties: Object.entries(node.properties).map(([name, value]) => ({ name, type: "STRING", value })),
      })),
      links: graph.links.map((link) => ({ from: link.from, fromPin: link.fromPin, to: link.to, toPin: link.toPin })),
    },
    null,
    2,
  );
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
}
