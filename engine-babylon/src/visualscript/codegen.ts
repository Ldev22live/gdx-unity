import { parseKeyNames } from "./keys";
import { MAX_DATA_DEPTH } from "./interpreter";
import type { Graph, GraphNode, Literal, ValueType, Vec3 } from "./graph";
import { EXEC_OUT, NODE, PIN_DELTA_TIME, PIN_OTHER, PROPERTY_NAME, PROPERTY_VARIABLE, SEQUENCE_OUTPUTS, type NodeRegistry } from "./nodes";
import { appendGraph } from "./sourceEmbed";
import { validateGraph } from "./validator";

/**
 * Turns a graph into the source of an equivalent web script (TD-044): a TypeScript class extending `ScriptComponent`.
 * The desktop editor generates Java or Kotlin from the same graph (`GraphCodeGenerator`); this is the TypeScript
 * counterpart for the web player. Each event node becomes a method, each action node a statement, each pure node an
 * expression, and each Delay node a timer field with a continuation method.
 *
 * Differences from the interpreter: Collision and Trigger events become public `onCollision` / `onTriggerEnter` methods
 * that the game has to call (the web player does not report collisions to scripts), and a graph whose execution wires
 * loop back is refused instead of being cut off after 10,000 nodes.
 *
 * The script ends with the graph embedded as comments (`sourceEmbed.ts`), so the desktop editor can import it (TD-045).
 */

const INDENT = "  ";
const RESERVED = new Set([
  "break", "case", "catch", "class", "const", "continue", "debugger", "default", "delete", "do", "else", "enum", "export",
  "extends", "false", "finally", "for", "function", "if", "import", "in", "instanceof", "new", "null", "return", "super",
  "switch", "this", "throw", "true", "try", "typeof", "var", "void", "while", "with", "yield", "let", "static", "entity",
  "scene", "input", "deltaTime", "constructor",
]);

/** Where generated scripts are registered and the folder they are saved in, for the header comment. */
export const GENERATED_SCRIPT_FOLDER = "src/scripts";

/** Class name generated for {@code graph}: its name in PascalCase with symbols removed ("door open" gives "DoorOpen"). */
export function className(graph: Graph): string {
  let name = "";
  let upper = true;
  for (const c of graph.name) {
    if (/[A-Za-z0-9]/.test(c)) {
      name += upper ? c.toUpperCase() : c;
      upper = false;
    } else {
      upper = true;
    }
  }
  if (name === "" || /^[0-9]/.test(name)) name = `Graph${name}`;
  return name;
}

/** A name that is legal in TypeScript: symbols become underscores, and keywords and leading digits get a prefix. */
export function identifier(text: string): string {
  let name = text.replace(/[^A-Za-z0-9_]/g, "_");
  if (name === "" || /^[0-9]/.test(name)) name = `v${name}`;
  return RESERVED.has(name) ? `${name}_` : name;
}

const capital = (text: string): string => (text === "" ? text : text[0]!.toUpperCase() + text.slice(1));

function quote(text: string): string {
  return JSON.stringify(text);
}

function numberLiteral(value: number): string {
  return Object.is(value, -0) ? "0" : String(value);
}

/**
 * Generates the script.
 *
 * @throws Error when the graph has validation problems, or an execution loop that code cannot express
 */
export function generateTypeScript(graph: Graph, registry: NodeRegistry): string {
  const problems = validateGraph(graph, registry);
  if (problems.length > 0) throw new Error(`Graph ${graph.name} has ${problems.length} problem(s), first: ${problems[0]!.message}`);
  return appendGraph(new Generation(graph, registry).generate(), graph);
}

class Generation {
  private readonly name: string;
  private readonly delayNodes: string[] = [];
  private readonly methods = new Map<string, string>();
  /** `this.input.isDown("KeyA", "ArrowLeft")` for the keys of a Key Down or Input Axis node (the script's input is the player's). */
  private keyDown(names: string): string {
    return `this.input.isDown(${parseKeyNames(names).map((key) => JSON.stringify(key.code)).join(", ")})`;
  }

  private needsVector = false;
  private needsFormatNumber = false;

  constructor(
    private readonly graph: Graph,
    private readonly registry: NodeRegistry,
  ) {
    this.name = className(graph);
  }

  generate(): string {
    const begin = this.nodesOf(NODE.beginPlay);
    const tick = this.nodesOf(NODE.tick);
    const collision = this.nodesOf(NODE.collision);
    const trigger = this.nodesOf(NODE.trigger);
    const custom = this.nodesOf(NODE.custom);
    // Event nodes first: their chains find the Delay nodes, whose methods are made right after
    for (const node of [...begin, ...tick, ...collision, ...trigger, ...custom]) {
      this.methods.set(this.runMethod(node.id), this.chainBody(node.id, EXEC_OUT, 2));
    }
    for (let i = 0; i < this.delayNodes.length; i++) {
      // The list grows while a continuation holds another Delay
      const id = this.delayNodes[i]!;
      this.methods.set(`${this.runMethod(id)}Completed`, this.chainBody(id, "completed", 2));
    }

    const fields: string[] = [];
    for (const variable of this.graph.variables) {
      fields.push(`private ${identifier(variable.name)}: ${this.typeName(variable.type)} = ${this.literal(variable.type, variable.value)};`);
    }
    for (const node of tick) fields.push(`private ${this.outputField(node.id, PIN_DELTA_TIME)} = 0;`);
    for (const node of [...collision, ...trigger]) fields.push(`private ${this.outputField(node.id, PIN_OTHER)}: GameEntity | null = null;`);
    for (const id of this.delayNodes) fields.push(`private ${this.delayField(id)} = -1;`);

    const body: string[] = [];
    body.push(`${INDENT}onStart(): void {`, `${INDENT}${INDENT}super.onStart();`);
    for (const node of begin) body.push(`${INDENT}${INDENT}this.${this.runMethod(node.id)}();`);
    body.push(`${INDENT}}`, "");

    body.push(`${INDENT}onUpdate(deltaTime: number): void {`, `${INDENT}${INDENT}super.onUpdate(deltaTime);`);
    for (const id of this.delayNodes) {
      const timer = `this.${this.delayField(id)}`;
      body.push(
        `${INDENT}${INDENT}if (${timer} >= 0) {`,
        `${INDENT}${INDENT}${INDENT}${timer} -= deltaTime;`,
        `${INDENT}${INDENT}${INDENT}if (${timer} <= 0) {`,
        `${INDENT}${INDENT}${INDENT}${INDENT}${timer} = -1;`,
        `${INDENT}${INDENT}${INDENT}${INDENT}this.${this.runMethod(id)}Completed();`,
        `${INDENT}${INDENT}${INDENT}}`,
        `${INDENT}${INDENT}}`,
      );
    }
    for (const node of tick) {
      body.push(`${INDENT}${INDENT}this.${this.outputField(node.id, PIN_DELTA_TIME)} = deltaTime;`, `${INDENT}${INDENT}this.${this.runMethod(node.id)}();`);
    }
    body.push(`${INDENT}}`, "");

    this.appendOther(body, "onCollision", collision, "Called when the game reports that this entity touched `other`.");
    this.appendOther(body, "onTriggerEnter", trigger, "Called when `other` enters this entity's trigger.");
    if (custom.length > 0) {
      body.push(`${INDENT}/** Runs the custom event nodes named \`name\`; false when the graph has none. */`, `${INDENT}fireCustomEvent(name: string): boolean {`, `${INDENT}${INDENT}let handled = false;`);
      for (const node of custom) {
        body.push(
          `${INDENT}${INDENT}if (name === ${quote(node.properties[PROPERTY_NAME] ?? "")}) {`,
          `${INDENT}${INDENT}${INDENT}this.${this.runMethod(node.id)}();`,
          `${INDENT}${INDENT}${INDENT}handled = true;`,
          `${INDENT}${INDENT}}`,
        );
      }
      body.push(`${INDENT}${INDENT}return handled;`, `${INDENT}}`, "");
    }
    for (const [method, code] of this.methods) {
      body.push(`${INDENT}private ${method}(): void {`, code.replace(/\n$/, ""), `${INDENT}}`, "");
    }
    if (this.needsFormatNumber) {
      body.push(
        `${INDENT}private static formatNumber(value: number): string {`,
        `${INDENT}${INDENT}return Number.isInteger(value) ? String(value) : value.toFixed(3).replace(/0+$/, "").replace(/\\.$/, "");`,
        `${INDENT}}`,
        "",
      );
    }

    const imports: string[] = [];
    if (this.needsVector) imports.push(`import { Vector3 } from "@babylonjs/core";`);
    imports.push(`import type { GameEntity } from "../runtime/GameEntity";`);
    imports.push(`import { ScriptComponent } from "../runtime/ScriptComponent";`);

    const lines = [
      ...imports,
      "",
      "/**",
      ` * Generated from the graph "${this.graph.name.replace("*/", "* /")}" by Forgeroom (Visual Scripting > Generate TypeScript).`,
      ` * Save it as ${GENERATED_SCRIPT_FOLDER}/${this.name}.ts and register it in the script registry, for example`,
      ` * registry.register("generated.${this.name}", () => new ${this.name}()), then bind it to an entity with an Entity Script`,
      " * Binding component named generated." + this.name + ". Edit it freely; generating again writes the text anew.",
      " */",
      `export class ${this.name} extends ScriptComponent {`,
      ...fields.map((field) => INDENT + field),
      ...(fields.length > 0 ? [""] : []),
      ...body,
    ];
    while (lines[lines.length - 1] === "") lines.pop();
    lines.push("}", "");
    return lines.join("\n");
  }

  private appendOther(body: string[], method: string, nodes: GraphNode[], doc: string): void {
    if (nodes.length === 0) return;
    body.push(`${INDENT}/** ${doc} */`, `${INDENT}${method}(other: GameEntity): void {`);
    for (const node of nodes) {
      body.push(`${INDENT}${INDENT}this.${this.outputField(node.id, PIN_OTHER)} = other;`, `${INDENT}${INDENT}this.${this.runMethod(node.id)}();`);
    }
    body.push(`${INDENT}}`, "");
  }

  // --- Chains of execution ---

  private chainBody(nodeId: string, pinId: string, depth: number): string {
    let out = "";
    const emitFrom = (id: string, pin: string, level: number, path: Set<string>): void => {
      for (const link of this.graph.linksFrom(id, pin)) emitNode(link.to, level, path);
    };
    const emitNode = (id: string, level: number, path: Set<string>): void => {
      if (path.has(id)) {
        throw new Error(`Execution wires loop back to node ${id}: code cannot express that. Use Event Tick or a Delay between the repeats.`);
      }
      path.add(id);
      const node = this.graph.requireNode(id);
      const pad = INDENT.repeat(level);
      switch (node.type) {
        case NODE.branch:
          out += `${pad}if (${this.input(node, "condition")}) {\n`;
          emitFrom(id, "true", level + 1, path);
          out += `${pad}} else {\n`;
          emitFrom(id, "false", level + 1, path);
          out += `${pad}}\n`;
          break;
        case NODE.sequence:
          for (let i = 0; i < SEQUENCE_OUTPUTS; i++) emitFrom(id, `then${i}`, level, path);
          break;
        case NODE.delay: {
          if (!this.delayNodes.includes(id)) this.delayNodes.push(id);
          const timer = `this.${this.delayField(id)}`;
          out += `${pad}if (${timer} < 0) ${timer} = ${this.input(node, "seconds")};\n`;
          break;
        }
        case NODE.print:
          out += `${pad}console.log(${this.input(node, "message")});\n`;
          emitFrom(id, EXEC_OUT, level, path);
          break;
        case NODE.setPosition:
          out += `${pad}${this.target(node)}.node.setAbsolutePosition(${this.input(node, "position")});\n`;
          emitFrom(id, EXEC_OUT, level, path);
          break;
        case NODE.moveBy: {
          const target = this.target(node);
          out += `${pad}${target}.node.setAbsolutePosition(${target}.getWorldPosition().add(${this.input(node, "offset")}));\n`;
          emitFrom(id, EXEC_OUT, level, path);
          break;
        }
        case NODE.rotateBy: {
          const degrees = this.input(node, "degrees");
          out += `${pad}${this.target(node)}.rotate(${this.component(degrees, "x")}, ${this.component(degrees, "y")}, ${this.component(degrees, "z")});\n`;
          emitFrom(id, EXEC_OUT, level, path);
          break;
        }
        case NODE.destroy:
          out += `${pad}this.scene.removeEntity(${this.target(node)});\n`;
          emitFrom(id, EXEC_OUT, level, path);
          break;
        case NODE.setVariable:
          out += `${pad}this.${identifier(node.properties[PROPERTY_VARIABLE]!)} = ${this.input(node, "value")};\n`;
          emitFrom(id, EXEC_OUT, level, path);
          break;
        default:
          throw new Error(
          node.type.startsWith("java:")
            ? `Node ${node.id} (${node.type}) is a Java method node: it runs in the web player, but code for it cannot be generated yet; use the desktop editor's Generate Java or Generate Kotlin`
            : `Cannot generate code for node type ${node.type}`,
        );
      }
      path.delete(id);
    };
    emitFrom(nodeId, pinId, depth, new Set());
    return out;
  }

  // --- Expressions ---

  /** Code of the data input {@code pinId}: the wire's source, else the typed-in value, else the type's default. */
  private input(node: GraphNode, pinId: string, depth = 0): string {
    const pin = this.registry.pin(node, this.graph, pinId)!;
    const wires = this.graph.linksInto(node.id, pinId);
    if (wires.length > 0) return this.output(wires[0]!.from, wires[0]!.fromPin, depth + 1);
    if (pin.type === "ENTITY") return "this.entity";
    const literal = node.literals[pinId];
    return this.literal(pin.type!, literal !== undefined ? literal : defaultFor(pin.type!));
  }

  /** The entity a node acts on: its Target wire, or this script's entity. */
  private target(node: GraphNode, depth = 0): string {
    const code = this.input(node, "target", depth);
    return code === "this.entity" ? code : `${code}!`;
  }

  private output(nodeId: string, pinId: string, depth: number): string {
    if (depth > MAX_DATA_DEPTH) throw new Error(`Data chain deeper than ${MAX_DATA_DEPTH} nodes at ${nodeId}`);
    const node = this.graph.requireNode(nodeId);
    const binary = (operator: string): string => `(${this.input(node, "a", depth)} ${operator} ${this.input(node, "b", depth)})`;
    switch (node.type) {
      case NODE.tick:
      case NODE.collision:
      case NODE.trigger:
        return `this.${this.outputField(node.id, pinId)}`;
      case NODE.getVariable:
      case NODE.setVariable:
        return `this.${identifier(node.properties[PROPERTY_VARIABLE]!)}`;
      case NODE.self:
        return "this.entity";
      case NODE.getPosition:
        return `${this.target(node, depth)}.getWorldPosition()`;
      case NODE.getName:
        return `${this.target(node, depth)}.name`;
      case NODE.add:
        return binary("+");
      case NODE.subtract:
        return binary("-");
      case NODE.multiply:
        return binary("*");
      case NODE.divide:
        return binary("/");
      case NODE.greater:
        return binary(">");
      case NODE.less:
        return binary("<");
      case NODE.equal:
        return binary("===");
      case NODE.and:
        return binary("&&");
      case NODE.or:
        return binary("||");
      case NODE.keyDown:
        return this.keyDown(node.properties["key"] ?? "");
      case NODE.axis:
        return `((${this.keyDown(node.properties["positive"] ?? "")} ? 1 : 0) - (${this.keyDown(node.properties["negative"] ?? "")} ? 1 : 0))`;
      case NODE.sine:
        return `Math.sin((${this.input(node, "degrees", depth)}) * Math.PI / 180)`;
      case NODE.cosine:
        return `Math.cos((${this.input(node, "degrees", depth)}) * Math.PI / 180)`;
      case NODE.not:
        return `(!${this.input(node, "value", depth)})`;
      case NODE.makeVector:
        this.needsVector = true;
        return `new Vector3(${this.input(node, "x", depth)}, ${this.input(node, "y", depth)}, ${this.input(node, "z", depth)})`;
      case NODE.breakVector:
        return this.component(this.input(node, "vector", depth), pinId);
      case NODE.addVectors:
        return `${this.group(this.input(node, "a", depth))}.add(${this.input(node, "b", depth)})`;
      case NODE.scaleVector:
        return `${this.group(this.input(node, "vector", depth))}.scale(${this.input(node, "factor", depth)})`;
      case NODE.append:
        return binary("+");
      case NODE.numberToText:
        this.needsFormatNumber = true;
        return `${this.name}.formatNumber(${this.input(node, "value", depth)})`;
      default:
        throw new Error(`Cannot generate code for node type ${node.type}`);
    }
  }

  /** {@code vector.x}: a component of a vector expression that may be a constructor call. */
  private component(vector: string, component: string): string {
    return `${this.group(vector)}.${component}`;
  }

  private group(code: string): string {
    return code.startsWith("new ") ? `(${code})` : code;
  }

  // --- Names and literals ---

  private typeName(type: ValueType): string {
    switch (type) {
      case "FLOAT":
      case "INTEGER":
        return "number";
      case "BOOLEAN":
        return "boolean";
      case "STRING":
        return "string";
      case "VECTOR3":
        this.needsVector = true;
        return "Vector3";
      case "ENTITY":
        return "GameEntity | null";
      case "OBJECT":
        throw new Error("Component object code generation is not supported; run this graph in the interpreter");
    }
  }

  private literal(type: ValueType, value: Literal | null): string {
    switch (type) {
      case "FLOAT":
      case "INTEGER":
        return numberLiteral(value as number);
      case "BOOLEAN":
        return String(value);
      case "STRING":
        return quote(value as string);
      case "VECTOR3": {
        this.needsVector = true;
        const v = value as Vec3;
        return `new Vector3(${numberLiteral(v.x)}, ${numberLiteral(v.y)}, ${numberLiteral(v.z)})`;
      }
      case "ENTITY":
        return "null";
      case "OBJECT":
        throw new Error("Component references cannot be written as literals");
    }
  }

  private runMethod(nodeId: string): string {
    return `run${capital(identifier(nodeId))}`;
  }

  private outputField(nodeId: string, pin: string): string {
    return `${identifier(nodeId)}${capital(pin)}`;
  }

  private delayField(nodeId: string): string {
    return `${identifier(nodeId)}Timer`;
  }

  private nodesOf(typeId: string): GraphNode[] {
    return this.graph.nodes.filter((node) => node.type === typeId);
  }
}

function defaultFor(type: ValueType): Literal | null {
  switch (type) {
    case "FLOAT":
    case "INTEGER":
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
  }
}
