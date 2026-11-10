import { copyValue, defaultValue, accepts, isVec3, type Graph, type GraphNode, type ValueType, type Vec3 } from "./graph";
import { EXEC_OUT, MAX_LOOP_ITERATIONS, NODE, PIN_DELTA_TIME, PIN_OTHER, PROPERTY_NAME, type GraphEntity, type NodeContext, type NodeRegistry, type NodeType } from "./nodes";
import { validateGraph } from "./validator";

/** A node failed; {@code nodeId} says which. */
export class GraphRuntimeError extends Error {
  constructor(
    readonly nodeId: string,
    message: string,
  ) {
    super(message);
    this.name = "GraphRuntimeError";
  }
}

/** Where a running graph reports to; the web player forwards these to the editor's log and status. */
export interface GraphHost {
  log(instance: GraphInstance, message: string): void;
  /** A node failed; that chain of execution stopped, the graph keeps responding to later events. */
  error(instance: GraphInstance, error: GraphRuntimeError): void;
  /** Called for every node that runs, so an editor can highlight it. */
  nodeExecuted?(instance: GraphInstance, nodeId: string): void;
  /** Whether any of the keys (`KeyboardEvent.code` values) is held down; without it Key Down and Input Axis read no key. */
  isKeyDown?(codes: string[]): boolean;
}

/** Nodes one event may run before it is stopped as an endless loop. */
export const MAX_NODES_PER_EVENT = 10_000;
/** Deepest chain of pure nodes one input may read through. */
export const MAX_DATA_DEPTH = 256;

interface PendingDelay {
  nodeId: string;
  pinId: string;
  remaining: number;
}

interface Continuation { nodeId: string; pinId: string; index?: number }

/**
 * One graph running for one entity: an interpreter (TD-029, TD-044), the web version of the desktop's `GraphInstance`.
 * Events start execution at their event nodes; action nodes run in order along execution wires; pure nodes are computed
 * when an input reads them, so their values are always current. Outputs of action and event nodes keep the value from
 * their last run. A failing node stops only the chain it is on; a chain of more than {@link MAX_NODES_PER_EVENT} nodes
 * is stopped as a probable endless loop.
 */
export class GraphInstance {
  private readonly variables = new Map<string, unknown>();
  private readonly lastOutputs = new Map<string, Map<string, unknown>>();
  private readonly delays: PendingDelay[] = [];
  private stopped = false;

  /**
   * @param isAlive whether the entity is still in the scene; the graph stops running when it is not
   * @throws Error when the graph has validation problems (the first one is in the message)
   */
  constructor(
    readonly graph: Graph,
    private readonly registry: NodeRegistry,
    readonly self: GraphEntity,
    private readonly host: GraphHost,
    private readonly isAlive: () => boolean = () => true,
  ) {
    const problems = validateGraph(graph, registry);
    if (problems.length > 0) {
      throw new Error(`Graph ${graph.name} has ${problems.length} problem(s), first: ${problems[0]!.message}`);
    }
    for (const variable of graph.variables) this.variables.set(variable.name, copyValue(variable.value));
  }

  // --- Events ---

  beginPlay(): void {
    this.fire(NODE.beginPlay, {}, null);
  }

  /** Finishes delays that ran out, then fires Event Tick. */
  tick(deltaSeconds: number): void {
    if (!this.isRunning) return;
    const due: PendingDelay[] = [];
    for (let i = this.delays.length - 1; i >= 0; i--) {
      const delay = this.delays[i]!;
      delay.remaining -= deltaSeconds;
      if (delay.remaining <= 0) {
        due.unshift(delay);
        this.delays.splice(i, 1);
      }
    }
    for (const delay of due) this.runChain(delay.nodeId, delay.pinId);
    this.fire(NODE.tick, { [PIN_DELTA_TIME]: deltaSeconds }, null);
  }

  collision(other: GraphEntity): void {
    this.fire(NODE.collision, { [PIN_OTHER]: other }, null);
  }

  triggerEnter(other: GraphEntity): void {
    this.fire(NODE.trigger, { [PIN_OTHER]: other }, null);
  }

  /** Runs the Custom Event nodes named {@code name}; false when the graph has none. */
  fireCustomEvent(name: string): boolean {
    return this.fire(NODE.custom, {}, name);
  }

  /** No more events run; pending delays are dropped. */
  stop(): void {
    this.stopped = true;
    this.delays.length = 0;
  }

  /** Running until stopped or until its entity leaves the scene. */
  get isRunning(): boolean {
    return !this.stopped && this.isAlive();
  }

  private fire(eventTypeId: string, payload: Record<string, unknown>, customName: string | null): boolean {
    if (!this.isRunning) return false;
    let handled = false;
    for (const node of this.graph.nodes) {
      if (node.type !== eventTypeId) continue;
      if (customName !== null && node.properties[PROPERTY_NAME] !== customName) continue;
      handled = true;
      const outputs = this.outputsOf(node.id);
      for (const [pin, value] of Object.entries(payload)) outputs.set(pin, value);
      this.host.nodeExecuted?.(this, node.id);
      this.runChain(node.id, EXEC_OUT);
    }
    return handled;
  }

  // --- Variables ---

  /** Whether any of the keys is held down, according to the host. */
  isKeyDown(codes: string[]): boolean {
    return this.host.isKeyDown?.(codes) ?? false;
  }

  getVariable(name: string): unknown {
    const variable = this.graph.variable(name);
    if (!variable) throw new Error(`Unknown variable: ${name}`);
    return copyValue(this.variables.get(name));
  }

  setVariable(name: string, value: unknown): void {
    const variable = this.graph.variable(name);
    if (!variable) throw new Error(`Unknown variable: ${name}`);
    if (value === null || value === undefined || !valueFits(variable.type, value)) {
      throw new Error(`Variable ${name} holds ${variable.type}, not ${JSON.stringify(value)}`);
    }
    this.variables.set(name, copyValue(value));
  }

  // --- Execution ---

  private outputsOf(nodeId: string): Map<string, unknown> {
    let outputs = this.lastOutputs.get(nodeId);
    if (!outputs) {
      outputs = new Map();
      this.lastOutputs.set(nodeId, outputs);
    }
    return outputs;
  }

  /** Runs everything wired after output {@code pinId} of node {@code nodeId}. */
  private runChain(nodeId: string, pinId: string): void {
    const pending: Continuation[] = [{ nodeId, pinId }];
    let count = 0;
    let current = nodeId;
    try {
      while (pending.length > 0 && this.isRunning) {
        const next = pending.pop()!;
        const { nodeId: fromNode, pinId: fromPin } = next;
        if (next.index !== undefined) this.outputsOf(fromNode).set("index", next.index);
        for (const link of this.graph.linksFrom(fromNode, fromPin)) {
          const target = this.graph.requireNode(link.to);
          current = target.id;
          if (++count > MAX_NODES_PER_EVENT) {
            throw new GraphRuntimeError(current, `Stopped after ${MAX_NODES_PER_EVENT} nodes in one event; is there an endless loop?`);
          }
          const context = new Context(this, target, 0);
          this.host.nodeExecuted?.(this, target.id);
          this.registry.require(target.type).run?.(context);
          // Continue in the order the node asked for (a stack pops the last push first)
          for (let i = context.continuations.length - 1; i >= 0; i--) pending.push(context.continuations[i]!);
        }
      }
    } catch (error) {
      if (error instanceof GraphRuntimeError) this.host.error(this, error);
      else this.host.error(this, new GraphRuntimeError(current, error instanceof Error ? error.message : String(error)));
    }
  }

  /** Value of output {@code pinId} of node {@code nodeId}, computing pure nodes now. */
  valueOf(nodeId: string, pinId: string, depth: number): unknown {
    const source = this.graph.requireNode(nodeId);
    const type = this.registry.require(source.type);
    const pin = this.registry.pin(source, this.graph, pinId);
    if (!pin?.type) throw new GraphRuntimeError(nodeId, `No data output ${pinId}`);
    if (type.kind === "pure") {
      if (depth > MAX_DATA_DEPTH) throw new GraphRuntimeError(nodeId, `Data chain deeper than ${MAX_DATA_DEPTH} nodes`);
      const context = new Context(this, source, depth);
      type.run?.(context);
      const value = context.pureOutputs.get(pinId);
      return value !== undefined && value !== null ? value : defaultValue(pin.type);
    }
    const value = this.lastOutputs.get(nodeId)?.get(pinId);
    return value !== undefined && value !== null ? value : defaultValue(pin.type);
  }

  /** @internal used by the node context */
  addDelay(nodeId: string, pinId: string, seconds: number): void {
    // A running Delay ignores new calls
    if (this.delays.some((delay) => delay.nodeId === nodeId)) return;
    this.delays.push({ nodeId, pinId, remaining: seconds });
  }

  /** @internal used by the node context */
  logMessage(message: string): void {
    this.host.log(this, message);
  }

  /** @internal used by the node context */
  graphOf(): Graph {
    return this.graph;
  }

  /** @internal used by the node context */
  registryOf(): NodeRegistry {
    return this.registry;
  }

  /** @internal used by the node context */
  outputs(nodeId: string): Map<string, unknown> {
    return this.outputsOf(nodeId);
  }
}

function valueFits(type: ValueType, value: unknown): boolean {
  return type === "ENTITY" ? false : accepts(type, value);
}

class Context implements NodeContext {
  readonly continuations: Continuation[] = [];
  readonly pureOutputs = new Map<string, unknown>();
  private readonly type: NodeType;

  constructor(
    private readonly instance: GraphInstance,
    private readonly node: GraphNode,
    private readonly depth: number,
  ) {
    this.type = instance.registryOf().require(node.type);
  }

  input(pinId: string): unknown {
    const graph = this.instance.graphOf();
    const pin = this.instance.registryOf().pin(this.node, graph, pinId);
    if (!pin || pin.exec || !pin.input || !pin.type) throw this.error(`No data input ${pinId}`);
    const wires = graph.linksInto(this.node.id, pinId);
    if (wires.length > 0) return this.instance.valueOf(wires[0]!.from, wires[0]!.fromPin, this.depth + 1);
    const literal = this.node.literals[pinId];
    return literal !== undefined ? copyValue(literal) : defaultValue(pin.type);
  }

  inputFloat(pinId: string): number {
    const value = this.input(pinId);
    if (typeof value !== "number") throw this.error(`Input ${pinId} should be a number, got ${JSON.stringify(value)}`);
    return value;
  }

  inputBoolean(pinId: string): boolean {
    return this.input(pinId) === true;
  }

  inputString(pinId: string): string {
    const value = this.input(pinId);
    return typeof value === "string" ? value : String(value ?? "");
  }

  inputVector(pinId: string): Vec3 {
    const value = this.input(pinId);
    if (!isVec3(value)) throw this.error(`Input ${pinId} should be a vector, got ${JSON.stringify(value)}`);
    return value;
  }

  inputEntity(pinId: string): GraphEntity | null {
    if (this.instance.graphOf().linksInto(this.node.id, pinId).length === 0) return this.instance.self;
    return (this.input(pinId) as GraphEntity | null) ?? null;
  }

  output(pinId: string, value: unknown): void {
    if (this.type.kind === "pure") this.pureOutputs.set(pinId, value);
    else this.instance.outputs(this.node.id).set(pinId, value);
  }

  property(name: string): string | undefined {
    return this.node.properties[name];
  }

  continueWith(pinId: string): void {
    this.requireExecOutput(pinId);
    this.continuations.push({ nodeId: this.node.id, pinId });
  }

  repeat(count: number): void {
    if (this.node.type !== "flow.repeat" || !Number.isInteger(count) || count < 0 || count > MAX_LOOP_ITERATIONS) throw this.error(`Invalid Repeat count: ${count}`);
    this.output("index", 0);
    for (let index = 0; index < count; index++) this.continuations.push({ nodeId: this.node.id, pinId: "body", index });
    this.continuations.push({ nodeId: this.node.id, pinId: "completed" });
  }

  continueAfter(seconds: number, pinId: string): void {
    this.requireExecOutput(pinId);
    this.instance.addDelay(this.node.id, pinId, seconds);
  }

  private requireExecOutput(pinId: string): void {
    const pin = this.instance.registryOf().pin(this.node, this.instance.graphOf(), pinId);
    if (!pin || !pin.exec || pin.input) throw this.error(`No execution output ${pinId}`);
  }

  self(): GraphEntity {
    return this.instance.self;
  }

  getVariable(name: string): unknown {
    return this.instance.getVariable(name);
  }

  setVariable(name: string, value: unknown): void {
    this.instance.setVariable(name, value);
  }

  log(message: string): void {
    this.instance.logMessage(message);
  }

  isKeyDown(codes: string[]): boolean {
    return this.instance.isKeyDown(codes);
  }

  error(message: string): Error {
    return new GraphRuntimeError(this.node.id, message);
  }
}
