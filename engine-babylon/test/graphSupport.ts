import { readFileSync } from "node:fs";
import { join } from "node:path";
import { Graph, parseGraph, type GraphNode, type Literal } from "../src/visualscript/graph";
import { EXEC_IN, EXEC_OUT } from "../src/visualscript/nodes";

/** The sample graphs of the desktop editor, read straight from the repository so tests cover the real files. */
export const GRAPHS_DIR = join(import.meta.dirname, "..", "..", "assets", "graphs");

export function readSampleGraph(name: string): Graph {
  return parseGraph(readFileSync(join(GRAPHS_DIR, `${name}.graph`), "utf8"));
}

/** Adds nodes and wires to a graph with fresh ids, the way the tests of the desktop interpreter do. */
export class Builder {
  constructor(readonly graph: Graph) {}

  node(type: string, literals: Record<string, Literal> = {}, properties: Record<string, string> = {}): string {
    const node: GraphNode = { id: this.graph.newNodeId(), type, x: 0, y: 0, literals, properties };
    this.graph.addNode(node);
    return node.id;
  }

  wire(from: string, fromPin: string, to: string, toPin: string): void {
    this.graph.addLink({ from, fromPin, to, toPin });
  }

  exec(from: string, to: string, pin: string = EXEC_OUT): void {
    this.wire(from, pin, to, EXEC_IN);
  }
}
