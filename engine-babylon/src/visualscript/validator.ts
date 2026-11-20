import { accepts, type Graph } from "./graph";
import { NODE, PROPERTY_FUNCTION, PROPERTY_NAME, PROPERTY_VARIABLE, type NodeRegistry, type PinDef } from "./nodes";

/** One problem; {@code nodeId} is null for problems not tied to a node. */
export interface GraphProblem {
  nodeId: string | null;
  message: string;
}

/**
 * Why {@code from} (an output) cannot be wired to {@code to} (an input), or null when it can. Also used by the editor
 * while dragging a wire.
 */
export function connectionProblem(from: PinDef, to: PinDef): string | null {
  if (from.input || !to.input) return "Wires go from an output to an input";
  if (from.exec !== to.exec) return "Execution pins connect only to execution pins";
  // A whole number may go where a float is wanted; a float never goes where an integer is wanted (say so with a node)
  if (!from.exec && from.type !== to.type && !(from.type === "INTEGER" && to.type === "FLOAT")) return `Cannot connect ${from.type} to ${to.type}`;
  if (from.type === "OBJECT" && to.type === "OBJECT" && from.objectClass !== to.objectClass) {
    return `Cannot connect ${from.objectClass} to ${to.objectClass}`;
  }
  return null;
}

/** Desktop node types that need the desktop's 3D physics API (TD-048), which graphs on the web player cannot reach yet. */
const PHYSICS_NODE_TYPES: readonly string[] = [
  "action.addForce",
  "action.addImpulse",
  "action.setVelocity",
  "action.raycast3d",
  "data.getVelocity",
  "action.moveCharacter",
  "action.jumpCharacter",
  "data.isGrounded",
];

/** Desktop node types that need navigation (Nav Mesh Bounds and Nav Agent, roadmap M6.4, TD-092), which the web player does not run. */
const NAVIGATION_NODE_TYPES: readonly string[] = ["action.navMoveTo", "action.navStop", "data.navHasArrived"];

/**
 * Node types the desktop editor has that the web player cannot run. A graph using one is reported with the reason instead of a
 * bare "unknown node type".
 */
export const DESKTOP_ONLY_NODE_TYPES: readonly string[] = [...PHYSICS_NODE_TYPES, ...NAVIGATION_NODE_TYPES];

/** Why a desktop-only node type does not run on the web player. */
function desktopOnlyReason(type: string): string {
  const needs = NAVIGATION_NODE_TYPES.includes(type)
    ? "navigation, which the web player does not run yet"
    : "3D physics, which the web player does not simulate yet";
  return `Node type ${type} needs ${needs}; it runs only in the desktop editor and game`;
}

/**
 * Checks a graph before it runs or is saved: known node types, wires between existing pins of matching kind and type,
 * one wire per data input and per execution output, settings filled in, and no loop among data wires (a value cannot
 * depend on itself). The same rules as the desktop's `GraphValidator`.
 */
export function validateGraph(graph: Graph, registry: NodeRegistry): GraphProblem[] {
  const problems: GraphProblem[] = [];
  for (const node of graph.nodes) {
    const type = registry.get(node.type);
    if (!type) {
      problems.push({
        nodeId: node.id,
        message: DESKTOP_ONLY_NODE_TYPES.includes(node.type)
          ? desktopOnlyReason(node.type)
          : node.type.startsWith("java:")
            ? `Java method node ${node.type} has no web version yet; it runs only in the desktop editor and game (the web player runs the MathUtils and Vector3 methods of its table)`
            : `Unknown node type ${node.type}`,
      });
      continue;
    }
    for (const property of type.properties) {
      const value = node.properties[property];
      if (value === undefined || value.trim() === "") {
        problems.push({ nodeId: node.id, message: `${type.title} needs a ${property}` });
      } else if (property === PROPERTY_VARIABLE && !graph.variable(value)) {
        problems.push({ nodeId: node.id, message: `Variable ${value} does not exist` });
      } else if (property === PROPERTY_FUNCTION && !graph.functionNamed(value)) {
        problems.push({ nodeId: node.id, message: `Function ${value} does not exist` });
      } else if (node.type === NODE.fireEvent && property === PROPERTY_NAME && !graph.eventNamed(value)) {
        problems.push({ nodeId: node.id, message: `Custom event ${value} is not declared; declare it with its parameters first` });
      }
    }
    problems.push(...placementProblems(graph, node.id, node.type, type.title, type.kind));
    for (const [pinId, literal] of Object.entries(node.literals)) {
      const pin = registry.pin(node, graph, pinId);
      if (pin && !pin.exec && pin.input && pin.type && !accepts(pin.type, literal)) {
        problems.push({ nodeId: node.id, message: `Value of ${pin.label} is not a ${pin.type}` });
      }
    }
  }

  const wiresPerEnd = new Map<string, number>();
  for (const link of graph.links) {
    const fromNode = graph.node(link.from);
    const toNode = graph.node(link.to);
    const from = fromNode ? registry.pin(fromNode, graph, link.fromPin) : undefined;
    const to = toNode ? registry.pin(toNode, graph, link.toPin) : undefined;
    if (!from || !to) {
      problems.push({ nodeId: from ? link.to : link.from, message: `Wire to a pin that does not exist: ${link.from}.${link.fromPin} to ${link.to}.${link.toPin}` });
      continue;
    }
    const problem = connectionProblem(from, to);
    if (problem) {
      problems.push({ nodeId: link.to, message: problem });
      continue;
    }
    // Single-wire ends: data inputs and execution outputs
    const end = from.exec ? `out:${link.from}.${link.fromPin}` : `in:${link.to}.${link.toPin}`;
    const count = (wiresPerEnd.get(end) ?? 0) + 1;
    wiresPerEnd.set(end, count);
    if (count === 2) {
      problems.push({
        nodeId: from.exec ? link.from : link.to,
        message: `${from.exec ? "Execution output " : "Input "}${from.exec ? from.label : to.label} has more than one wire`,
      });
    }
  }

  const loop = findDataLoop(graph, registry);
  if (loop) problems.push({ nodeId: loop, message: "Data wires form a loop: a value cannot depend on itself" });
  if (graph.owner) {
    const entries = graph.nodes.filter((node) => node.type === NODE.functionEntry).length;
    if (entries !== 1) problems.push({ nodeId: null, message: `Function ${graph.owner.name} needs exactly one Function Entry node, has ${entries}` });
  } else {
    for (const fn of graph.functions) {
      for (const inner of validateGraph(fn.body, registry)) problems.push({ nodeId: null, message: `Function ${fn.name}: ${inner.message}` });
    }
    const cycle = findCallCycle(graph);
    if (cycle) problems.push({ nodeId: null, message: `Functions call each other in a circle (${cycle}); a function cannot call itself, directly or through others` });
  }
  return problems;
}

/**
 * Source generation does not cover functions and parameterized events yet (TD-070): a graph that has them runs in the interpreter, on the
 * web and on the desktop, but cannot become Java, Kotlin or TypeScript.
 * @throws Error saying so
 */
export function refuseFunctionsForGeneration(graph: Graph, target: string): void {
  const fires = graph.nodes.some((node) => node.type === NODE.fireEvent || node.type === NODE.functionCall);
  if (graph.usesFunctionsOrEvents() || fires) {
    throw new Error(`Graph ${graph.name} uses functions or declared custom events: ${target} source generation does not support them yet; the graph runs as it is in the editor, the game runtime and the web player`);
  }
}

/** Nodes that only make sense in a function body, or only outside one, and what a body may not hold. */
function placementProblems(graph: Graph, nodeId: string, typeId: string, title: string, kind: string): GraphProblem[] {
  const problems: GraphProblem[] = [];
  const inBody = graph.owner !== null;
  if (!inBody && (typeId === NODE.functionEntry || typeId === NODE.functionReturn)) problems.push({ nodeId, message: `${title} can only be used inside a function` });
  if (inBody && kind === "event" && typeId !== NODE.functionEntry) problems.push({ nodeId, message: `A function cannot hold ${title}; events belong to the graph` });
  if (inBody && typeId === NODE.delay) problems.push({ nodeId, message: "A function cannot hold a Delay: it runs to its end in one go" });
  return problems;
}

/** A path like "a -> b -> a" when functions call each other in a circle, else null. */
function findCallCycle(graph: Graph): string | null {
  const calls = new Map<string, string[]>();
  for (const fn of graph.functions) {
    calls.set(fn.name, fn.body.nodes.filter((node) => node.type === NODE.functionCall).map((node) => node.properties[PROPERTY_FUNCTION] ?? ""));
  }
  const done = new Set<string>();
  const visit = (name: string, path: string[]): string | null => {
    if (done.has(name) || !calls.has(name)) return null;
    const at = path.indexOf(name);
    if (at >= 0) return [...path.slice(at), name].join(" -> ");
    path.push(name);
    for (const next of calls.get(name)!) {
      const found = visit(next, path);
      if (found) return found;
    }
    path.pop();
    done.add(name);
    return null;
  };
  for (const start of calls.keys()) {
    const found = visit(start, []);
    if (found) return found;
  }
  return null;
}

/** A node on a cycle of data wires, or null. */
function findDataLoop(graph: Graph, registry: NodeRegistry): string | null {
  const dependsOn = new Map<string, string[]>();
  for (const link of graph.links) {
    const fromNode = graph.node(link.from);
    const from = fromNode ? registry.pin(fromNode, graph, link.fromPin) : undefined;
    if (from && !from.exec) dependsOn.set(link.to, [...(dependsOn.get(link.to) ?? []), link.from]);
  }
  const done = new Set<string>();
  const visit = (node: string, onPath: Set<string>): string | null => {
    if (done.has(node)) return null;
    if (onPath.has(node)) return node;
    onPath.add(node);
    for (const next of dependsOn.get(node) ?? []) {
      const loop = visit(next, onPath);
      if (loop) return loop;
    }
    onPath.delete(node);
    done.add(node);
    return null;
  };
  for (const start of dependsOn.keys()) {
    const loop = visit(start, new Set());
    if (loop) return loop;
  }
  return null;
}
