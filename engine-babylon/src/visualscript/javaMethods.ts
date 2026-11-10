import table from "./javaMethodNodes.json";
import type { Vec3, ValueType } from "./graph";
import type { NodeContext, NodeKind, NodeRegistry, NodeType, PinDef } from "./nodes";

// The execution pin ids of nodes.ts, repeated so this module has no value import from it (nodes.ts registers these nodes)
const EXEC_IN = "exec";
const EXEC_OUT = "then";

/** Prefix of every node made from a Java method, and the suffix of the Action twin of a Pure one (TD-060). */
export const JAVA_ID_PREFIX = "java:";
export const ACTION_SUFFIX = "@action";

interface TablePin {
  id: string;
  label: string;
  exec: boolean;
  input: boolean;
  type?: ValueType;
}

interface TableNode {
  id: string;
  title: string;
  category: string;
  kind: NodeKind;
  twin: boolean;
  pins: TablePin[];
  cases: { in: Record<string, unknown>; out: unknown }[];
}

/** The Java method nodes the web player runs (TD-065): made from the desktop's own nodes by `WebMethodNodesTest`, with the results of real calls. */
export const JAVA_METHOD_TABLE: readonly TableNode[] = (table as unknown as { nodes: TableNode[] }).nodes;

type Inputs = Record<string, unknown>;
type Implementation = (inputs: Inputs) => unknown;

const MATH = `${JAVA_ID_PREFIX}com.badlogic.gdx.math.MathUtils#`;
const VECTOR = `${JAVA_ID_PREFIX}com.badlogic.gdx.math.Vector3#`;
const DEGREES = 180 / Math.PI;
/** libGDX's `MathUtils.nanoToSec`-style tolerance: `isZero(float)` is true up to this. */
const NANO = 1e-9;

const f = Math.fround;
const num = (inputs: Inputs, pin: string): number => inputs[pin] as number;
const vec = (inputs: Inputs, pin: string): Vec3 => inputs[pin] as Vec3;
const make = (x: number, y: number, z: number): Vec3 => ({ x: f(x), y: f(y), z: f(z) });
const len2 = (v: Vec3): number => v.x * v.x + v.y * v.y + v.z * v.z;
const scaled = (v: Vec3, factor: number): Vec3 => make(v.x * factor, v.y * factor, v.z * factor);

/** What each method does, written to give what the libGDX method gives. A test compares every one with the results of real Java calls. */
const IMPLEMENTATIONS: Record<string, Implementation> = {
  [`${MATH}clamp(float,float,float)`]: (i) => (num(i, "p0") < num(i, "p1") ? num(i, "p1") : num(i, "p0") > num(i, "p2") ? num(i, "p2") : num(i, "p0")),
  [`${MATH}clamp(int,int,int)`]: (i) => (num(i, "p0") < num(i, "p1") ? num(i, "p1") : num(i, "p0") > num(i, "p2") ? num(i, "p2") : num(i, "p0")),
  [`${MATH}lerp(float,float,float)`]: (i) => f(num(i, "p0") + (num(i, "p1") - num(i, "p0")) * num(i, "p2")),
  [`${MATH}map(float,float,float,float,float)`]: (i) =>
    f(num(i, "p2") + ((num(i, "p4") - num(i, "p0")) * (num(i, "p3") - num(i, "p2"))) / (num(i, "p1") - num(i, "p0"))),
  [`${MATH}norm(float,float,float)`]: (i) => f((num(i, "p2") - num(i, "p0")) / (num(i, "p1") - num(i, "p0"))),
  [`${MATH}sin(float)`]: (i) => f(Math.sin(num(i, "p0"))),
  [`${MATH}cos(float)`]: (i) => f(Math.cos(num(i, "p0"))),
  [`${MATH}tan(float)`]: (i) => f(Math.tan(num(i, "p0"))),
  [`${MATH}sinDeg(float)`]: (i) => f(Math.sin(num(i, "p0") / DEGREES)),
  [`${MATH}cosDeg(float)`]: (i) => f(Math.cos(num(i, "p0") / DEGREES)),
  [`${MATH}tanDeg(float)`]: (i) => f(Math.tan(num(i, "p0") / DEGREES)),
  [`${MATH}asin(float)`]: (i) => f(Math.asin(num(i, "p0"))),
  [`${MATH}acos(float)`]: (i) => f(Math.acos(num(i, "p0"))),
  [`${MATH}atan(float)`]: (i) => f(Math.atan(num(i, "p0"))),
  [`${MATH}atan2(float,float)`]: (i) => f(Math.atan2(num(i, "p0"), num(i, "p1"))),
  [`${MATH}atan2Deg(float,float)`]: (i) => f(Math.atan2(num(i, "p0"), num(i, "p1")) * DEGREES),
  [`${MATH}floor(float)`]: (i) => Math.floor(num(i, "p0")),
  [`${MATH}ceil(float)`]: (i) => Math.ceil(num(i, "p0")),
  [`${MATH}round(float)`]: (i) => Math.floor(num(i, "p0") + 0.5),
  [`${MATH}log2(float)`]: (i) => f(Math.log(num(i, "p0")) / Math.log(2)),
  [`${MATH}isZero(float)`]: (i) => Math.abs(num(i, "p0")) <= NANO,
  [`${MATH}isEqual(float,float,float)`]: (i) => Math.abs(num(i, "p0") - num(i, "p1")) <= num(i, "p2"),
  // The desktop uses its own generator; the web uses the browser's, so the sequences differ but the ranges are the same
  [`${MATH}random()`]: () => f(Math.random()),
  [`${MATH}random(float,float)`]: (i) => f(num(i, "p0") + Math.random() * (num(i, "p1") - num(i, "p0"))),
  [`${MATH}random(int,int)`]: (i) => num(i, "p0") + Math.floor(Math.random() * (num(i, "p1") - num(i, "p0") + 1)),
  [`${MATH}randomBoolean()`]: () => Math.random() < 0.5,
  [`${VECTOR}<init>(float,float,float)`]: (i) => make(num(i, "p0"), num(i, "p1"), num(i, "p2")),
  [`${VECTOR}cpy()`]: (i) => make(vec(i, "target").x, vec(i, "target").y, vec(i, "target").z),
  [`${VECTOR}dot(com.badlogic.gdx.math.Vector3)`]: (i) => {
    const a = vec(i, "target");
    const b = vec(i, "p0");
    return f(a.x * b.x + a.y * b.y + a.z * b.z);
  },
  [`${VECTOR}dst(com.badlogic.gdx.math.Vector3)`]: (i) => {
    const a = vec(i, "target");
    const b = vec(i, "p0");
    return f(Math.sqrt((b.x - a.x) ** 2 + (b.y - a.y) ** 2 + (b.z - a.z) ** 2));
  },
  [`${VECTOR}dst2(com.badlogic.gdx.math.Vector3)`]: (i) => {
    const a = vec(i, "target");
    const b = vec(i, "p0");
    return f((b.x - a.x) ** 2 + (b.y - a.y) ** 2 + (b.z - a.z) ** 2);
  },
  [`${VECTOR}len()`]: (i) => f(Math.sqrt(len2(vec(i, "target")))),
  [`${VECTOR}len2()`]: (i) => f(len2(vec(i, "target"))),
  [`${VECTOR}isZero()`]: (i) => vec(i, "target").x === 0 && vec(i, "target").y === 0 && vec(i, "target").z === 0,
  [`${VECTOR}add(com.badlogic.gdx.math.Vector3)`]: (i) => make(vec(i, "target").x + vec(i, "p0").x, vec(i, "target").y + vec(i, "p0").y, vec(i, "target").z + vec(i, "p0").z),
  [`${VECTOR}sub(com.badlogic.gdx.math.Vector3)`]: (i) => make(vec(i, "target").x - vec(i, "p0").x, vec(i, "target").y - vec(i, "p0").y, vec(i, "target").z - vec(i, "p0").z),
  [`${VECTOR}scl(float)`]: (i) => scaled(vec(i, "target"), num(i, "p0")),
  [`${VECTOR}scl(com.badlogic.gdx.math.Vector3)`]: (i) => make(vec(i, "target").x * vec(i, "p0").x, vec(i, "target").y * vec(i, "p0").y, vec(i, "target").z * vec(i, "p0").z),
  [`${VECTOR}nor()`]: (i) => {
    const v = vec(i, "target");
    const l2 = f(len2(v));
    return l2 === 0 || l2 === 1 ? make(v.x, v.y, v.z) : scaled(v, 1 / Math.sqrt(l2));
  },
  [`${VECTOR}crs(com.badlogic.gdx.math.Vector3)`]: (i) => {
    const a = vec(i, "target");
    const b = vec(i, "p0");
    return make(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x);
  },
  [`${VECTOR}lerp(com.badlogic.gdx.math.Vector3,float)`]: (i) => {
    const a = vec(i, "target");
    const b = vec(i, "p0");
    const alpha = num(i, "p1");
    return make(a.x + alpha * (b.x - a.x), a.y + alpha * (b.y - a.y), a.z + alpha * (b.z - a.z));
  },
  [`${VECTOR}limit(float)`]: (i) => limited(vec(i, "target"), num(i, "p0") * num(i, "p0")),
  [`${VECTOR}setLength(float)`]: (i) => {
    const v = vec(i, "target");
    const wanted = num(i, "p0") * num(i, "p0");
    const old = len2(v);
    return old === 0 || old === wanted ? make(v.x, v.y, v.z) : scaled(v, Math.sqrt(wanted / old));
  },
  [`${VECTOR}mulAdd(com.badlogic.gdx.math.Vector3,float)`]: (i) => {
    const a = vec(i, "target");
    const b = vec(i, "p0");
    const k = num(i, "p1");
    return make(a.x + b.x * k, a.y + b.y * k, a.z + b.z * k);
  },
};

/** libGDX's `limit2`: the vector scaled down when it is longer than the limit. */
function limited(v: Vec3, limit2: number): Vec3 {
  const l2 = len2(v);
  return l2 > limit2 ? scaled(v, Math.sqrt(limit2 / l2)) : make(v.x, v.y, v.z);
}

/** Ids of the method nodes the web player has, from the table. */
export const JAVA_METHOD_IDS: readonly string[] = JAVA_METHOD_TABLE.map((node) => node.id);

/** Whether {@code id} is a Java method node id, or the id of the Action twin of one. */
export function isJavaMethodId(id: string): boolean {
  return id.startsWith(JAVA_ID_PREFIX);
}

function pinOf(pin: TablePin): PinDef {
  return pin.exec
    ? { id: pin.id, label: pin.label, exec: true, input: pin.input }
    : { id: pin.id, label: pin.label, exec: false, input: pin.input, type: pin.type! };
}

function behaviour(node: TableNode, implementation: Implementation, action: boolean): (c: NodeContext) => void {
  const data = node.pins.filter((pin) => !pin.exec);
  const inputs = data.filter((pin) => pin.input);
  const output = data.find((pin) => !pin.input);
  return (c) => {
    const values: Inputs = {};
    for (const pin of inputs) values[pin.id] = pin.type === "VECTOR3" ? c.inputVector(pin.id) : pin.type === "BOOLEAN" ? c.inputBoolean(pin.id) : c.inputFloat(pin.id);
    const result = implementation(values);
    if (output) c.output(output.id, result);
    if (action) c.continueWith(EXEC_OUT);
  };
}

function typeOf(node: TableNode, implementation: Implementation, twin: boolean): NodeType {
  const action = twin || node.kind === "action";
  const data = node.pins.filter((pin) => !pin.exec).map(pinOf);
  const pins: PinDef[] = action
    ? [{ id: EXEC_IN, label: "", exec: true, input: true }, { id: EXEC_OUT, label: "", exec: true, input: false }, ...data]
    : data;
  return {
    id: twin ? node.id + ACTION_SUFFIX : node.id,
    title: twin ? `${node.title} (as Action)` : node.title,
    category: node.category,
    kind: action ? "action" : "pure",
    properties: [],
    pins: () => pins,
    run: behaviour(node, implementation, action),
  };
}

/**
 * Adds the Java method nodes of the table to {@code registry}: the same ids, pins and kinds as the desktop (Pure nodes also get their
 * Action twin, `<id>@action`), each running the TypeScript version of its method.
 *
 * @returns how many node types were added
 * @throws Error when the table has a node without an implementation, or an implementation has no node
 */
export function registerJavaMethodNodes(registry: NodeRegistry): number {
  let added = 0;
  for (const node of JAVA_METHOD_TABLE) {
    const implementation = IMPLEMENTATIONS[node.id];
    if (!implementation) throw new Error(`The method node table has ${node.id}, which has no TypeScript version`);
    registry.register(typeOf(node, implementation, false));
    added++;
    if (node.twin) {
      registry.register(typeOf(node, implementation, true));
      added++;
    }
  }
  return added;
}

/** The implementation of a method node by id; for tests. */
export function implementationOf(id: string): Implementation | undefined {
  return IMPLEMENTATIONS[id];
}
