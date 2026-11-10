import { parseKeyNames } from "./keys";
import { className } from "./codegen";
import { appendGraph } from "./sourceEmbed";
import { defaultValue, type Graph, type GraphNode, type Literal, type ValueType, type Vec3 } from "./graph";
import { MAX_DATA_DEPTH } from "./interpreter";
import { EXEC_OUT, NODE, PIN_DELTA_TIME, PIN_OTHER, PROPERTY_NAME, PROPERTY_VARIABLE, SEQUENCE_OUTPUTS, type NodeRegistry } from "./nodes";
import { validateGraph } from "./validator";

/**
 * Turns a graph into the source of a desktop `ScriptComponent` in Java or Kotlin (TD-045), so the web editor can hand a
 * script to the desktop game without the desktop editor. It is a port of the desktop's `GraphCodeGenerator`: for the same
 * graph both write the same text (a test compares the sample graphs with files in `test/golden`). Collision and Trigger
 * events become methods the game has to call, and a graph whose execution wires loop is refused. The script ends with the
 * embedded graph ({@link appendGraph}).
 */

export type JvmLanguage = "java" | "kotlin";

/** File extension of the generated script, including the dot. */
export const JVM_EXTENSIONS: Record<JvmLanguage, string> = { java: ".java", kotlin: ".kt" };

/** Package of generated scripts, below the project's `scripts` folder. */
export const JVM_PACKAGE = "generated";

const INDENT = "    ";
const RESERVED = new Set([
  "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue", "default", "do",
  "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if", "implements", "import", "instanceof",
  "int", "interface", "long", "native", "new", "package", "private", "protected", "public", "return", "short", "static",
  "strictfp", "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void", "volatile", "while",
  "true", "false", "null", "fun", "val", "var", "when", "object", "is", "in", "as", "typealias", "internal", "open", "override",
  "entity", "deltaTime",
]);

/** A name that is legal in Java and Kotlin: symbols become underscores, and keywords and leading digits get a prefix. */
function identifier(text: string): string {
  let name = text.replace(/[^A-Za-z0-9_]/g, "_");
  if (name === "" || /^[0-9]/.test(name)) name = `v${name}`;
  return RESERVED.has(name) ? `${name}_` : name;
}

const capital = (text: string): string => (text === "" ? text : text[0]!.toUpperCase() + text.slice(1));

/** A float literal: whole numbers as "1.0f", anything else as its shortest text with an f. */
function floatLiteral(value: number): string {
  if (Object.is(value, -0)) return "-0.0f";
  if (Number.isInteger(value) && Math.abs(value) < 1e7) return `${value.toFixed(1)}f`;
  return `${String(value)}f`;
}

/**
 * Generates the script.
 *
 * @throws Error when the graph has validation problems, or an execution loop that code cannot express
 */
export function generateJvm(graph: Graph, registry: NodeRegistry, language: JvmLanguage): string {
  const problems = validateGraph(graph, registry);
  if (problems.length > 0) throw new Error(`Graph ${graph.name} has ${problems.length} problem(s), first: ${problems[0]!.message}`);
  return appendGraph(new Generation(graph, registry, language === "kotlin").generate(), graph);
}

class Generation {
  private readonly name: string;
  private readonly delayNodes: string[] = [];
  private readonly methods = new Map<string, string>();
  private needsWorldPosition = false;
  private needsFormatNumber = false;
  private needsKeys = false;

  /** `keyDown(Input.Keys.A, Input.Keys.LEFT)`: the keys of a Key Down or Input Axis node as constants. */
  private keyDown(names: string): string {
    this.needsKeys = true;
    return `keyDown(${parseKeyNames(names).map((key) => `Input.Keys.${key.name}`).join(", ")})`;
  }

  constructor(
    private readonly graph: Graph,
    private readonly registry: NodeRegistry,
    private readonly kotlin: boolean,
  ) {
    this.name = className(graph);
  }

  generate(): string {
    let body = "";
    // Event nodes first: their chains find the Delay nodes, whose methods are made right after
    const begin = this.nodesOf(NODE.beginPlay);
    const tick = this.nodesOf(NODE.tick);
    const collision = this.nodesOf(NODE.collision);
    const trigger = this.nodesOf(NODE.trigger);
    const custom = this.nodesOf(NODE.custom);
    for (const node of [...begin, ...tick, ...collision, ...trigger, ...custom]) {
      this.methods.set(`run${capital(identifier(node.id))}`, this.chainBody(node.id, EXEC_OUT, 2));
    }
    for (let i = 0; i < this.delayNodes.length; i++) {
      // The list grows while a continuation holds another Delay
      const id = this.delayNodes[i]!;
      this.methods.set(`run${capital(identifier(id))}Completed`, this.chainBody(id, "completed", 2));
    }

    let fields = "";
    for (const variable of this.graph.variables) {
      const type = variable.type;
      fields += `${INDENT}${this.field(this.typeName(type), identifier(variable.name), this.literal(type, variable.value ?? defaultValue(type)), type === "ENTITY")}\n`;
    }
    for (const node of tick) fields += `${INDENT}${this.field("float", this.outputField(node, PIN_DELTA_TIME), "0f", false)}\n`;
    for (const node of [...collision, ...trigger]) fields += `${INDENT}${this.field("Entity", this.outputField(node, PIN_OTHER), "null", true)}\n`;
    for (const id of this.delayNodes) fields += `${INDENT}${this.field("float", this.delayField(id), "-1f", false)}\n`;

    // Entry points
    body += `${INDENT}${this.kotlin ? "override fun onStart() {" : `@Override\n${INDENT}public void onStart() {`}\n`;
    body += `${INDENT}${INDENT}${this.call("super.onStart()")}\n`;
    for (const node of begin) body += `${INDENT}${INDENT}${this.call(this.runMethod(node))}\n`;
    body += `${INDENT}}\n\n`;

    body += `${INDENT}${this.kotlin ? "override fun onUpdate(deltaTime: Float) {" : `@Override\n${INDENT}public void onUpdate(float deltaTime) {`}\n`;
    body += `${INDENT}${INDENT}${this.call("super.onUpdate(deltaTime)")}\n`;
    for (const id of this.delayNodes) {
      const timer = this.delayField(id);
      body += `${INDENT}${INDENT}if (${timer} >= 0f) {\n`;
      body += `${INDENT}${INDENT}${INDENT}${timer} -= deltaTime${this.end()}\n`;
      body += `${INDENT}${INDENT}${INDENT}if (${timer} <= 0f) {\n`;
      body += `${INDENT}${INDENT}${INDENT}${INDENT}${timer} = -1f${this.end()}\n`;
      body += `${INDENT}${INDENT}${INDENT}${INDENT}${this.call(`run${capital(identifier(id))}Completed()`)}\n`;
      body += `${INDENT}${INDENT}${INDENT}}\n`;
      body += `${INDENT}${INDENT}}\n`;
    }
    for (const node of tick) {
      body += `${INDENT}${INDENT}${this.outputField(node, PIN_DELTA_TIME)} = deltaTime${this.end()}\n`;
      body += `${INDENT}${INDENT}${this.call(this.runMethod(node))}\n`;
    }
    body += `${INDENT}}\n\n`;

    body += this.otherEntry("onCollision", collision, "Called when the physics reports that this entity touched {@code other}.");
    body += this.otherEntry("onTriggerEnter", trigger, "Called when {@code other} enters this entity's trigger.");
    if (custom.length > 0) body += this.customEvents(custom);

    for (const [method, code] of this.methods) {
      body += `${INDENT}${this.kotlin ? `private fun ${method}() {` : `private void ${method}() {`}\n${code}${INDENT}}\n\n`;
    }
    body += this.helpers();

    let out = `package ${JVM_PACKAGE}${this.kotlin ? "\n\n" : ";\n\n"}`;
    out += this.imports();
    out += `/**\n * Generated from the graph "${this.graph.name.replace("*/", "* /")}" by Forgeroom (Visual Scripting > Generate ${this.kotlin ? "Kotlin" : "Java"}).\n`;
    out += " * Edit it freely; generating again writes the file anew. Bind it to an entity with an Entity Script Binding\n";
    out += ` * component named ${JVM_PACKAGE}.${this.name}.\n */\n`;
    out += this.kotlin ? `class ${this.name} : ScriptComponent() {\n` : `public class ${this.name} extends ScriptComponent {\n`;
    out += fields;
    if (fields.length > 0) out += "\n";
    out += `${body.trimEnd()}\n}\n`;
    return out;
  }

  private imports(): string {
    const imports = [
      "com.badlogic.gdx.Gdx",
      "com.badlogic.gdx.math.Vector3",
      "io.github.ldev22live.gdxunity.engine.scene.Entity",
      "io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent",
    ];
    if (this.needsWorldPosition && !this.kotlin) imports.push("io.github.ldev22live.gdxunity.engine.scene.Transform");
    if (this.needsKeys) imports.push("com.badlogic.gdx.Input");
    if (this.needsFormatNumber) imports.push("java.util.Locale");
    return `${imports.map((name) => `import ${name}${this.kotlin ? "" : ";"}\n`).join("")}\n`;
  }

  private otherEntry(method: string, nodes: GraphNode[], doc: string): string {
    if (nodes.length === 0) return "";
    let out = `${INDENT}/** ${doc} */\n`;
    out += `${INDENT}${this.kotlin ? `fun ${method}(other: Entity?) {` : `public void ${method}(Entity other) {`}\n`;
    for (const node of nodes) {
      out += `${INDENT}${INDENT}${this.outputField(node, PIN_OTHER)} = other${this.end()}\n`;
      out += `${INDENT}${INDENT}${this.call(this.runMethod(node))}\n`;
    }
    return `${out}${INDENT}}\n\n`;
  }

  private customEvents(custom: GraphNode[]): string {
    let out = `${INDENT}/** Runs the custom event nodes named {@code name}; false when the graph has none. */\n`;
    out += `${INDENT}${this.kotlin ? "fun fireCustomEvent(name: String): Boolean {" : "public boolean fireCustomEvent(String name) {"}\n`;
    out += `${INDENT}${INDENT}${this.kotlin ? "var handled = false" : "boolean handled = false;"}\n`;
    for (const node of custom) {
      const eventName = this.quote(String(node.properties[PROPERTY_NAME] ?? ""));
      out += `${INDENT}${INDENT}if (${this.kotlin ? `name == ${eventName}` : `name.equals(${eventName})`}) {\n`;
      out += `${INDENT}${INDENT}${INDENT}${this.call(this.runMethod(node))}\n`;
      out += `${INDENT}${INDENT}${INDENT}handled = true${this.end()}\n`;
      out += `${INDENT}${INDENT}}\n`;
    }
    out += `${INDENT}${INDENT}return handled${this.end()}\n`;
    return `${out}${INDENT}}\n\n`;
  }

  private helpers(): string {
    const lines: string[] = [];
    if (this.needsWorldPosition) {
      if (this.kotlin) {
        lines.push(
          "private fun setWorldPosition(target: Entity, world: Vector3) {",
          "    val parent = target.getTransform().getParent()",
          "    if (parent == null) {",
          "        target.getTransform().setLocalPosition(world)",
          "        return",
          "    }",
          "    target.getTransform().setLocalPosition(Vector3(world).mul(parent.getWorldMatrix().inv()))",
          "}",
        );
      } else {
        lines.push(
          "private static void setWorldPosition(Entity target, Vector3 world) {",
          "    Transform parent = target.getTransform().getParent();",
          "    if (parent == null) {",
          "        target.getTransform().setLocalPosition(world);",
          "        return;",
          "    }",
          "    target.getTransform().setLocalPosition(new Vector3(world).mul(parent.getWorldMatrix().inv()));",
          "}",
        );
      }
      lines.push("");
    }
    if (this.needsKeys) {
      if (this.kotlin) {
        lines.push(
          "private fun keyDown(vararg keys: Int): Boolean {",
          "    return keys.any { Gdx.input.isKeyPressed(it) }",
          "}",
        );
      } else {
        lines.push(
          "private static boolean keyDown(int... keys) {",
          "    for (int key : keys) {",
          "        if (Gdx.input.isKeyPressed(key)) {",
          "            return true;",
          "        }",
          "    }",
          "    return false;",
          "}",
        );
      }
      lines.push("");
    }
    if (this.needsFormatNumber) {
      if (this.kotlin) {
        lines.push(
          "private fun formatNumber(value: Float): String {",
          "    if (value == Math.rint(value.toDouble()).toFloat() && !value.isInfinite()) {",
          "        return value.toLong().toString()",
          "    }",
          String.raw`    return String.format(Locale.ROOT, "%.3f", value).replace(Regex("0+$"), "").replace(Regex("\\.$"), "")`,
          "}",
        );
      } else {
        lines.push(
          "private static String formatNumber(float value) {",
          "    if (value == Math.rint(value) && !Float.isInfinite(value)) {",
          "        return Long.toString((long) value);",
          "    }",
          String.raw`    return String.format(Locale.ROOT, "%.3f", value).replaceAll("0+$", "").replaceAll("\\.$", "");`,
          "}",
        );
      }
      lines.push("");
    }
    return lines.map((line) => (line === "" ? "\n" : `${INDENT}${line}\n`)).join("").replace(/\n\n\n/g, "\n\n");
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
      const end = this.end();
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
          const timer = this.delayField(id);
          out += `${pad}if (${timer} < 0f) ${timer} = ${this.input(node, "seconds")}${end}\n`;
          break;
        }
        case NODE.print:
          out += `${pad}Gdx.app.log(${this.quote(this.name)}, ${this.input(node, "message")})${end}\n`;
          emitFrom(id, EXEC_OUT, level, path);
          break;
        case NODE.setPosition:
          this.needsWorldPosition = true;
          out += `${pad}setWorldPosition(${this.target(node)}, ${this.input(node, "position")})${end}\n`;
          emitFrom(id, EXEC_OUT, level, path);
          break;
        case NODE.moveBy: {
          this.needsWorldPosition = true;
          const target = this.target(node);
          out += `${pad}setWorldPosition(${target}, ${target}.getTransform().getWorldPosition().add(${this.input(node, "offset")}))${end}\n`;
          emitFrom(id, EXEC_OUT, level, path);
          break;
        }
        case NODE.rotateBy: {
          const degrees = this.input(node, "degrees");
          out += `${pad}${this.target(node)}.getTransform().rotate(${this.component(degrees, "x")}, ${this.component(degrees, "y")}, ${this.component(degrees, "z")})${end}\n`;
          emitFrom(id, EXEC_OUT, level, path);
          break;
        }
        case NODE.destroy: {
          const target = this.target(node);
          out += `${pad}${target}.getScene()${this.kotlin ? "?" : ""}.removeEntity(${target})${end}\n`;
          emitFrom(id, EXEC_OUT, level, path);
          break;
        }
        case NODE.setVariable:
          out += `${pad}${identifier(node.properties[PROPERTY_VARIABLE]!)} = ${this.input(node, "value")}${end}\n`;
          emitFrom(id, EXEC_OUT, level, path);
          break;
        default:
          throw new Error(`Cannot generate code for node type ${node.type}`);
      }
      path.delete(id);
    };
    emitFrom(nodeId, pinId, depth, new Set());
    return out;
  }

  // --- Expressions ---

  /** Code of the data input `pinId`: the wire's source, else the typed-in value, else the type's default. */
  private input(node: GraphNode, pinId: string, depth = 0): string {
    const pin = this.registry.pin(node, this.graph, pinId)!;
    const wires = this.graph.linksInto(node.id, pinId);
    if (wires.length > 0) return this.output(wires[0]!.from, wires[0]!.fromPin, depth + 1);
    if (pin.type === "ENTITY") return "getEntity()";
    const literal = node.literals[pinId];
    return this.literal(pin.type!, literal !== undefined ? literal : defaultValue(pin.type!));
  }

  /** Entity a node acts on: its Target wire, or this script's entity; Kotlin needs the not-null assertion. */
  private target(node: GraphNode, depth = 0): string {
    const code = this.input(node, "target", depth);
    return this.kotlin && code !== "getEntity()" ? `(${code})!!` : code;
  }

  private output(nodeId: string, pinId: string, depth: number): string {
    if (depth > MAX_DATA_DEPTH) throw new Error(`Data chain deeper than ${MAX_DATA_DEPTH} nodes at ${nodeId}`);
    const node = this.graph.requireNode(nodeId);
    const binary = (operator: string): string => `(${this.input(node, "a", depth)} ${operator} ${this.input(node, "b", depth)})`;
    const vector = this.kotlin ? "Vector3(" : "new Vector3(";
    switch (node.type) {
      case NODE.tick:
      case NODE.collision:
      case NODE.trigger:
        return this.outputField(node, pinId);
      case NODE.getVariable:
      case NODE.setVariable:
        return identifier(node.properties[PROPERTY_VARIABLE]!);
      case NODE.self:
        return "getEntity()";
      case NODE.getPosition:
        return `${this.target(node, depth)}.getTransform().getWorldPosition()`;
      case NODE.getName:
        return `${this.target(node, depth)}.getName()`;
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
        return binary("==");
      case NODE.and:
        return binary("&&");
      case NODE.or:
        return binary("||");
      case NODE.keyDown:
        return this.keyDown(node.properties["key"] ?? "");
      case NODE.axis:
        return `((${this.keyDown(node.properties["positive"] ?? "")} ? 1f : 0f) - (${this.keyDown(node.properties["negative"] ?? "")} ? 1f : 0f))`;
      case NODE.sine:
      case NODE.cosine: {
        const fn = node.type === NODE.sine ? "sin" : "cos";
        const degrees = this.input(node, "degrees", depth);
        return this.kotlin ? `Math.${fn}(Math.toRadians((${degrees}).toDouble())).toFloat()` : `(float) Math.${fn}(Math.toRadians(${degrees}))`;
      }
      case NODE.not:
        return `(!${this.input(node, "value", depth)})`;
      case NODE.makeVector:
        return `${vector}${this.input(node, "x", depth)}, ${this.input(node, "y", depth)}, ${this.input(node, "z", depth)})`;
      case NODE.breakVector:
        return this.component(this.input(node, "vector", depth), pinId);
      case NODE.addVectors:
        return `${vector}${this.input(node, "a", depth)}).add(${this.input(node, "b", depth)})`;
      case NODE.scaleVector:
        return `${vector}${this.input(node, "vector", depth)}).scl(${this.input(node, "factor", depth)})`;
      case NODE.append:
        return `(${this.input(node, "a", depth)} + ${this.input(node, "b", depth)})`;
      case NODE.numberToText:
        this.needsFormatNumber = true;
        return `formatNumber(${this.input(node, "value", depth)})`;
      default:
        throw new Error(`Cannot generate code for node type ${node.type}`);
    }
  }

  /** `vector.x`: a component of a vector expression that may be a constructor call. */
  private component(vector: string, component: string): string {
    return `${vector.startsWith("new ") || vector.startsWith("Vector3(") ? `(${vector})` : vector}.${component}`;
  }

  // --- Language details ---

  private typeName(type: ValueType): string {
    switch (type) {
      case "FLOAT":
        return this.kotlin ? "Float" : "float";
      case "INTEGER":
        return this.kotlin ? "Int" : "int";
      case "BOOLEAN":
        return this.kotlin ? "Boolean" : "boolean";
      case "STRING":
        return "String";
      case "VECTOR3":
        return "Vector3";
      case "ENTITY":
        return "Entity";
      case "OBJECT":
        throw new Error("Component object generation from the web editor is not supported; use the desktop generator");
    }
  }

  private field(type: string, name: string, value: string, nullable: boolean): string {
    return this.kotlin
      ? `private var ${name}: ${type === "float" ? "Float" : type}${nullable ? "?" : ""} = ${value}`
      : `private ${type} ${name} = ${value};`;
  }

  private literal(type: ValueType, value: Literal | null): string {
    switch (type) {
      case "FLOAT":
        return floatLiteral(value as number);
      case "INTEGER":
        return String(value);
      case "BOOLEAN":
        return String(value);
      case "STRING":
        return this.quote(value as string);
      case "VECTOR3": {
        const v = value as Vec3;
        return `${this.kotlin ? "Vector3(" : "new Vector3("}${floatLiteral(v.x)}, ${floatLiteral(v.y)}, ${floatLiteral(v.z)})`;
      }
      case "ENTITY":
        return "null";
      case "OBJECT":
        throw new Error("Component references cannot be written as literals");
    }
  }

  /** A string literal; Kotlin also treats a dollar sign as the start of a template. */
  private quote(text: string): string {
    let out = '"';
    for (const c of text) {
      switch (c) {
        case '"':
          out += '\\"';
          break;
        case "\\":
          out += "\\\\";
          break;
        case "\n":
          out += "\\n";
          break;
        case "\r":
          out += "\\r";
          break;
        case "\t":
          out += "\\t";
          break;
        case "$":
          out += this.kotlin ? "\\$" : "$";
          break;
        default:
          out += c;
      }
    }
    return `${out}"`;
  }

  private end(): string {
    return this.kotlin ? "" : ";";
  }

  private call(code: string): string {
    return code + this.end();
  }

  private runMethod(node: GraphNode): string {
    return `run${capital(identifier(node.id))}()`;
  }

  private outputField(node: GraphNode, pin: string): string {
    return identifier(node.id) + capital(pin);
  }

  private delayField(nodeId: string): string {
    return `${identifier(nodeId)}Timer`;
  }

  private nodesOf(typeId: string): GraphNode[] {
    return this.graph.nodes.filter((node) => node.type === typeId);
  }
}
