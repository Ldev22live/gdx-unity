package io.github.ldev22live.gdxunity.engine.visualscript;

import com.badlogic.gdx.math.Vector3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Turns a graph into the source of a web {@code ScriptComponent} written in TypeScript (TD-045), the counterpart of
 * {@link GraphCodeGenerator} (Java, Kotlin) and of the web editor's {@code codegen.ts}, so the desktop editor can hand a
 * script to the web game without the web editor. The output follows {@code codegen.ts} and ends with the embedded graph
 * ({@link GraphSourceEmbed}). Collision and Trigger events become methods the game has to call, and a graph whose
 * execution wires loop is refused, as in the other generators.
 */
public final class GraphTypeScriptGenerator {

    /** File extension of the generated script, including the dot. */
    public static final String EXTENSION = ".ts";
    /** Folder the header comment tells the user to save the script in, inside the web project. */
    public static final String WEB_SCRIPT_FOLDER = "src/scripts";

    private static final String INDENT = "  ";
    private static final Set<String> RESERVED = Set.of("break", "case", "catch", "class", "const", "continue", "debugger",
        "default", "delete", "do", "else", "enum", "export", "extends", "false", "finally", "for", "function", "if", "import",
        "in", "instanceof", "new", "null", "return", "super", "switch", "this", "throw", "true", "try", "typeof", "var",
        "void", "while", "with", "yield", "let", "static", "entity", "scene", "input", "deltaTime", "constructor");

    private final NodeRegistry registry;

    public GraphTypeScriptGenerator(NodeRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry cannot be null");
    }

    /**
     * Generates the script.
     *
     * @throws IllegalArgumentException when the graph has validation problems, or an execution loop that code cannot express
     */
    public String generate(Graph graph) {
        Objects.requireNonNull(graph, "graph cannot be null");
        List<GraphValidator.Problem> problems = new GraphValidator(registry).validate(graph);
        for (GraphNode node : graph.getNodes()) {
            if (JavaMethodNodes.isMethodNode(node.getTypeId())) {
                throw new IllegalArgumentException("Graph " + graph.getName() + " uses a Java method node (" + node.getTypeId()
                    + "): TypeScript source generation for method nodes is not supported; use the graph interpreter "
                    + "for the curated web APIs or generate Java/Kotlin on the desktop");
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("Graph " + graph.getName() + " has " + problems.size() + " problem(s), first: "
                + problems.get(0).message());
        }
        return GraphSourceEmbed.append(new Run(graph).generate(), graph);
    }

    /** One generation: holds the graph and what the script needs besides the chains. */
    private final class Run {
        private final Graph graph;
        private final String name;
        private final List<String> delayNodes = new ArrayList<>();
        private final Map<String, String> methods = new LinkedHashMap<>();
        private boolean needsVector;
        private boolean needsFormatNumber;

        Run(Graph graph) {
            this.graph = graph;
            this.name = GraphCodeGenerator.className(graph);
        }

        String generate() {
            List<GraphNode> begin = nodesOf(BuiltInNodes.EVENT_BEGIN_PLAY);
            List<GraphNode> tick = nodesOf(BuiltInNodes.EVENT_TICK);
            List<GraphNode> collision = nodesOf(BuiltInNodes.EVENT_COLLISION);
            List<GraphNode> trigger = nodesOf(BuiltInNodes.EVENT_TRIGGER);
            List<GraphNode> custom = nodesOf(BuiltInNodes.EVENT_CUSTOM);
            // Event nodes first: their chains find the Delay nodes, whose methods are made right after
            for (List<GraphNode> group : List.of(begin, tick, collision, trigger, custom)) {
                for (GraphNode node : group) {
                    methods.put(runMethod(node.getId()), chainBody(node.getId(), PinDef.EXEC_OUT, 2));
                }
            }
            for (int i = 0; i < delayNodes.size(); i++) {
                // The list grows while a continuation holds another Delay
                String id = delayNodes.get(i);
                methods.put(runMethod(id) + "Completed", chainBody(id, "completed", 2));
            }

            List<String> fields = new ArrayList<>();
            for (GraphVariable variable : graph.getVariables()) {
                fields.add("private " + identifier(variable.name()) + ": " + typeName(variable.type()) + " = "
                    + literal(variable.type(), variable.initialValue()) + ";");
            }
            for (GraphNode node : tick) {
                fields.add("private " + outputField(node.getId(), BuiltInNodes.PIN_DELTA_TIME) + " = 0;");
            }
            for (List<GraphNode> group : List.of(collision, trigger)) {
                for (GraphNode node : group) {
                    fields.add("private " + outputField(node.getId(), BuiltInNodes.PIN_OTHER) + ": GameEntity | null = null;");
                }
            }
            for (String id : delayNodes) {
                fields.add("private " + delayField(id) + " = -1;");
            }

            List<String> body = new ArrayList<>();
            body.add(INDENT + "onStart(): void {");
            body.add(INDENT + INDENT + "super.onStart();");
            for (GraphNode node : begin) {
                body.add(INDENT + INDENT + "this." + runMethod(node.getId()) + "();");
            }
            body.add(INDENT + "}");
            body.add("");

            body.add(INDENT + "onUpdate(deltaTime: number): void {");
            body.add(INDENT + INDENT + "super.onUpdate(deltaTime);");
            for (String id : delayNodes) {
                String timer = "this." + delayField(id);
                body.add(INDENT + INDENT + "if (" + timer + " >= 0) {");
                body.add(INDENT + INDENT + INDENT + timer + " -= deltaTime;");
                body.add(INDENT + INDENT + INDENT + "if (" + timer + " <= 0) {");
                body.add(INDENT + INDENT + INDENT + INDENT + timer + " = -1;");
                body.add(INDENT + INDENT + INDENT + INDENT + "this." + runMethod(id) + "Completed();");
                body.add(INDENT + INDENT + INDENT + "}");
                body.add(INDENT + INDENT + "}");
            }
            for (GraphNode node : tick) {
                body.add(INDENT + INDENT + "this." + outputField(node.getId(), BuiltInNodes.PIN_DELTA_TIME) + " = deltaTime;");
                body.add(INDENT + INDENT + "this." + runMethod(node.getId()) + "();");
            }
            body.add(INDENT + "}");
            body.add("");

            appendOther(body, "onCollision", collision, "Called when the game reports that this entity touched `other`.");
            appendOther(body, "onTriggerEnter", trigger, "Called when `other` enters this entity's trigger.");
            if (!custom.isEmpty()) {
                body.add(INDENT + "/** Runs the custom event nodes named `name`; false when the graph has none. */");
                body.add(INDENT + "fireCustomEvent(name: string): boolean {");
                body.add(INDENT + INDENT + "let handled = false;");
                for (GraphNode node : custom) {
                    Object eventName = node.getProperty(BuiltInNodes.PROPERTY_NAME);
                    body.add(INDENT + INDENT + "if (name === " + quote(eventName == null ? "" : String.valueOf(eventName)) + ") {");
                    body.add(INDENT + INDENT + INDENT + "this." + runMethod(node.getId()) + "();");
                    body.add(INDENT + INDENT + INDENT + "handled = true;");
                    body.add(INDENT + INDENT + "}");
                }
                body.add(INDENT + INDENT + "return handled;");
                body.add(INDENT + "}");
                body.add("");
            }
            for (Map.Entry<String, String> method : methods.entrySet()) {
                body.add(INDENT + "private " + method.getKey() + "(): void {");
                body.add(method.getValue().replaceAll("\\n$", ""));
                body.add(INDENT + "}");
                body.add("");
            }
            if (needsFormatNumber) {
                body.add(INDENT + "private static formatNumber(value: number): string {");
                body.add(INDENT + INDENT + "return Number.isInteger(value) ? String(value) : value.toFixed(3).replace(/0+$/, \"\").replace(/\\.$/, \"\");");
                body.add(INDENT + "}");
                body.add("");
            }

            List<String> lines = new ArrayList<>();
            if (needsVector) {
                lines.add("import { Vector3 } from \"@babylonjs/core\";");
            }
            lines.add("import type { GameEntity } from \"../runtime/GameEntity\";");
            lines.add("import { ScriptComponent } from \"../runtime/ScriptComponent\";");
            lines.add("");
            lines.add("/**");
            lines.add(" * Generated from the graph \"" + graph.getName().replace("*/", "* /") + "\" by Forgeroom (Visual Scripting > Generate TypeScript).");
            lines.add(" * Save it as " + WEB_SCRIPT_FOLDER + "/" + name + ".ts and register it in the script registry, for example");
            lines.add(" * registry.register(\"generated." + name + "\", () => new " + name + "()), then bind it to an entity with an Entity Script");
            lines.add(" * Binding component named generated." + name + ". Edit it freely; generating again writes the text anew.");
            lines.add(" */");
            lines.add("export class " + name + " extends ScriptComponent {");
            for (String field : fields) {
                lines.add(INDENT + field);
            }
            if (!fields.isEmpty()) {
                lines.add("");
            }
            lines.addAll(body);
            while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
                lines.remove(lines.size() - 1);
            }
            lines.add("}");
            return String.join("\n", lines) + "\n";
        }

        private void appendOther(List<String> body, String method, List<GraphNode> nodes, String doc) {
            if (nodes.isEmpty()) {
                return;
            }
            body.add(INDENT + "/** " + doc + " */");
            body.add(INDENT + method + "(other: GameEntity): void {");
            for (GraphNode node : nodes) {
                body.add(INDENT + INDENT + "this." + outputField(node.getId(), BuiltInNodes.PIN_OTHER) + " = other;");
                body.add(INDENT + INDENT + "this." + runMethod(node.getId()) + "();");
            }
            body.add(INDENT + "}");
            body.add("");
        }

        // --- Chains of execution ---

        private String chainBody(String nodeId, String pinId, int depth) {
            StringBuilder out = new StringBuilder();
            emitFrom(nodeId, pinId, depth, new HashSet<>(), out);
            return out.toString();
        }

        private void emitFrom(String nodeId, String pinId, int depth, Set<String> path, StringBuilder out) {
            for (GraphLink link : graph.linksFrom(nodeId, pinId)) {
                emitNode(link.toNode(), depth, path, out);
            }
        }

        private void emitNode(String nodeId, int depth, Set<String> path, StringBuilder out) {
            if (!path.add(nodeId)) {
                throw new IllegalArgumentException("Execution wires loop back to node " + nodeId
                    + ": code cannot express that. Use Event Tick or a Delay between the repeats.");
            }
            GraphNode node = graph.getNode(nodeId);
            String pad = INDENT.repeat(depth);
            switch (node.getTypeId()) {
                case BuiltInNodes.FLOW_BRANCH -> {
                    out.append(pad).append("if (").append(input(node, "condition", 0)).append(") {\n");
                    emitFrom(nodeId, "true", depth + 1, path, out);
                    out.append(pad).append("} else {\n");
                    emitFrom(nodeId, "false", depth + 1, path, out);
                    out.append(pad).append("}\n");
                }
                case BuiltInNodes.FLOW_SEQUENCE -> {
                    for (int i = 0; i < BuiltInNodes.SEQUENCE_OUTPUTS; i++) {
                        emitFrom(nodeId, "then" + i, depth, path, out);
                    }
                }
                case BuiltInNodes.FLOW_DELAY -> {
                    if (!delayNodes.contains(nodeId)) {
                        delayNodes.add(nodeId);
                    }
                    String timer = "this." + delayField(nodeId);
                    out.append(pad).append("if (").append(timer).append(" < 0) ").append(timer).append(" = ")
                        .append(input(node, "seconds", 0)).append(";\n");
                }
                case BuiltInNodes.ACTION_PRINT -> {
                    out.append(pad).append("console.log(").append(input(node, "message", 0)).append(");\n");
                    emitFrom(nodeId, PinDef.EXEC_OUT, depth, path, out);
                }
                case BuiltInNodes.ACTION_SET_POSITION -> {
                    out.append(pad).append(target(node, 0)).append(".node.setAbsolutePosition(").append(input(node, "position", 0)).append(");\n");
                    emitFrom(nodeId, PinDef.EXEC_OUT, depth, path, out);
                }
                case BuiltInNodes.ACTION_MOVE_BY -> {
                    String target = target(node, 0);
                    out.append(pad).append(target).append(".node.setAbsolutePosition(").append(target).append(".getWorldPosition().add(")
                        .append(input(node, "offset", 0)).append("));\n");
                    emitFrom(nodeId, PinDef.EXEC_OUT, depth, path, out);
                }
                case BuiltInNodes.ACTION_ROTATE_BY -> {
                    String degrees = input(node, "degrees", 0);
                    out.append(pad).append(target(node, 0)).append(".rotate(").append(component(degrees, "x")).append(", ")
                        .append(component(degrees, "y")).append(", ").append(component(degrees, "z")).append(");\n");
                    emitFrom(nodeId, PinDef.EXEC_OUT, depth, path, out);
                }
                case BuiltInNodes.ACTION_DESTROY -> {
                    out.append(pad).append("this.scene.removeEntity(").append(target(node, 0)).append(");\n");
                    emitFrom(nodeId, PinDef.EXEC_OUT, depth, path, out);
                }
                case BuiltInNodes.VARIABLE_SET -> {
                    out.append(pad).append("this.").append(identifier((String) node.getProperty(BuiltInNodes.PROPERTY_VARIABLE)))
                        .append(" = ").append(input(node, "value", 0)).append(";\n");
                    emitFrom(nodeId, PinDef.EXEC_OUT, depth, path, out);
                }
                case BuiltInNodes.ACTION_ADD_FORCE, BuiltInNodes.ACTION_ADD_IMPULSE, BuiltInNodes.ACTION_SET_VELOCITY,
                     BuiltInNodes.ACTION_RAYCAST_3D, BuiltInNodes.ACTION_MOVE_CHARACTER, BuiltInNodes.ACTION_JUMP_CHARACTER ->
                    throw desktopOnly(node);
                default -> throw new IllegalArgumentException("Cannot generate code for node type " + node.getTypeId());
            }
            path.remove(nodeId);
        }

        // --- Expressions ---

        /** Code of the data input {@code pinId}: the wire's source, else the typed-in value, else the type's default. */
        private String input(GraphNode node, String pinId, int depth) {
            PinDef pin = registry.get(node.getTypeId()).pin(node, graph, pinId);
            List<GraphLink> wires = graph.linksInto(node.getId(), pinId);
            if (!wires.isEmpty()) {
                return output(wires.get(0).fromNode(), wires.get(0).fromPin(), depth + 1);
            }
            if (pin.type() == ValueType.ENTITY) {
                return "this.entity";
            }
            Object literal = node.getLiteral(pinId);
            return literal(pin.type(), literal != null ? literal : pin.type().defaultValue());
        }

        /** The entity a node acts on: its Target wire, or this script's entity. */
        private String target(GraphNode node, int depth) {
            String code = input(node, "target", depth);
            return code.equals("this.entity") ? code : code + "!";
        }

        private String output(String nodeId, String pinId, int depth) {
            if (depth > GraphInstance.MAX_DATA_DEPTH) {
                throw new IllegalArgumentException("Data chain deeper than " + GraphInstance.MAX_DATA_DEPTH + " nodes at " + nodeId);
            }
            GraphNode node = graph.getNode(nodeId);
            switch (node.getTypeId()) {
                case BuiltInNodes.EVENT_TICK, BuiltInNodes.EVENT_COLLISION, BuiltInNodes.EVENT_TRIGGER:
                    return "this." + outputField(node.getId(), pinId);
                case BuiltInNodes.VARIABLE_GET:
                case BuiltInNodes.VARIABLE_SET:
                    return "this." + identifier((String) node.getProperty(BuiltInNodes.PROPERTY_VARIABLE));
                case BuiltInNodes.DATA_SELF:
                    return "this.entity";
                case BuiltInNodes.DATA_GET_POSITION:
                    return target(node, depth) + ".getWorldPosition()";
                case BuiltInNodes.DATA_GET_NAME:
                    return target(node, depth) + ".name";
                case BuiltInNodes.MATH_ADD:
                case BuiltInNodes.TEXT_APPEND:
                    return binary(node, "+", depth);
                case BuiltInNodes.MATH_SUBTRACT:
                    return binary(node, "-", depth);
                case BuiltInNodes.MATH_MULTIPLY:
                    return binary(node, "*", depth);
                case BuiltInNodes.MATH_DIVIDE:
                    return binary(node, "/", depth);
                case BuiltInNodes.MATH_GREATER:
                    return binary(node, ">", depth);
                case BuiltInNodes.MATH_LESS:
                    return binary(node, "<", depth);
                case BuiltInNodes.MATH_EQUAL:
                    return binary(node, "===", depth);
                case BuiltInNodes.LOGIC_AND:
                    return binary(node, "&&", depth);
                case BuiltInNodes.LOGIC_OR:
                    return binary(node, "||", depth);
                case BuiltInNodes.MATH_SIN:
                    return "Math.sin((" + input(node, "degrees", depth) + ") * Math.PI / 180)";
                case BuiltInNodes.MATH_COS:
                    return "Math.cos((" + input(node, "degrees", depth) + ") * Math.PI / 180)";
                case BuiltInNodes.LOGIC_NOT:
                    return "(!" + input(node, "value", depth) + ")";
                case BuiltInNodes.VECTOR_MAKE:
                    needsVector = true;
                    return "new Vector3(" + input(node, "x", depth) + ", " + input(node, "y", depth) + ", " + input(node, "z", depth) + ")";
                case BuiltInNodes.VECTOR_BREAK:
                    return component(input(node, "vector", depth), pinId);
                case BuiltInNodes.VECTOR_ADD:
                    return group(input(node, "a", depth)) + ".add(" + input(node, "b", depth) + ")";
                case BuiltInNodes.VECTOR_SCALE:
                    return group(input(node, "vector", depth)) + ".scale(" + input(node, "factor", depth) + ")";
                case BuiltInNodes.TEXT_FROM_FLOAT:
                    needsFormatNumber = true;
                    return name + ".formatNumber(" + input(node, "value", depth) + ")";
                case BuiltInNodes.DATA_GET_VELOCITY:
                case BuiltInNodes.DATA_IS_GROUNDED:
                case BuiltInNodes.ACTION_RAYCAST_3D:
                    throw desktopOnly(node);
                case BuiltInNodes.INPUT_KEY_DOWN:
                    return keyDown((String) node.getProperty(BuiltInNodes.PROPERTY_KEY));
                case BuiltInNodes.INPUT_AXIS:
                    return "((" + keyDown((String) node.getProperty(BuiltInNodes.PROPERTY_POSITIVE)) + " ? 1 : 0) - ("
                        + keyDown((String) node.getProperty(BuiltInNodes.PROPERTY_NEGATIVE)) + " ? 1 : 0))";
                default:
                    throw new IllegalArgumentException("Cannot generate code for node type " + node.getTypeId());
            }
        }

        /** {@code this.input.isDown("KeyA", "ArrowLeft")}: the keys of a Key Down or Input Axis node, as the browser names them. */
        private String keyDown(String names) {
            StringBuilder codes = new StringBuilder();
            for (String code : KeyNames.browserCodes(names)) {
                codes.append(codes.length() == 0 ? "" : ", ").append(quote(code));
            }
            return "this.input.isDown(" + codes + ")";
        }

        /** The web player does not simulate 3D physics (TD-048), so code for these nodes would silently do nothing. */
        private IllegalArgumentException desktopOnly(GraphNode node) {
            String reason = "needs 3D physics, which the web player does not simulate yet";
            return new IllegalArgumentException("Node " + node.getId() + " (" + node.getTypeId() + ") " + reason
                + ", so TypeScript cannot be generated for this graph. Use Generate Java or Generate Kotlin.");
        }

        private String binary(GraphNode node, String operator, int depth) {
            return "(" + input(node, "a", depth) + " " + operator + " " + input(node, "b", depth) + ")";
        }

        /** {@code vector.x}: a component of a vector expression that may be a constructor call. */
        private String component(String vector, String component) {
            return group(vector) + "." + component;
        }

        private String group(String code) {
            return code.startsWith("new ") ? "(" + code + ")" : code;
        }

        // --- Names and literals ---

        private String typeName(ValueType type) {
            return switch (type) {
                case FLOAT -> "number";
                case BOOLEAN -> "boolean";
                case STRING -> "string";
                case VECTOR3 -> {
                    needsVector = true;
                    yield "Vector3";
                }
                case ENTITY -> "GameEntity | null";
                case INTEGER -> "number";
                case OBJECT -> throw new IllegalArgumentException("Objects cannot be generated as TypeScript");
            };
        }

        private String literal(ValueType type, Object value) {
            Object actual = value != null ? value : type.defaultValue();
            return switch (type) {
                case FLOAT -> numberLiteral((Float) actual);
                case BOOLEAN -> String.valueOf(actual);
                case STRING -> quote((String) actual);
                case VECTOR3 -> {
                    needsVector = true;
                    Vector3 v = (Vector3) actual;
                    yield "new Vector3(" + numberLiteral(v.x) + ", " + numberLiteral(v.y) + ", " + numberLiteral(v.z) + ")";
                }
                case ENTITY, OBJECT -> "null";
                case INTEGER -> String.valueOf(actual);
            };
        }

        private String runMethod(String nodeId) {
            return "run" + capital(identifier(nodeId));
        }

        private String outputField(String nodeId, String pin) {
            return identifier(nodeId) + capital(pin);
        }

        private String delayField(String nodeId) {
            return identifier(nodeId) + "Timer";
        }

        private List<GraphNode> nodesOf(String typeId) {
            List<GraphNode> result = new ArrayList<>();
            for (GraphNode node : graph.getNodes()) {
                if (node.getTypeId().equals(typeId)) {
                    result.add(node);
                }
            }
            return result;
        }
    }

    private static String capital(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    /** A name that is legal in TypeScript: symbols become underscores, and keywords and leading digits get a prefix. */
    static String identifier(String text) {
        StringBuilder name = new StringBuilder();
        for (char c : text.toCharArray()) {
            name.append(Character.isLetterOrDigit(c) && c < 128 || c == '_' ? c : '_');
        }
        if (name.length() == 0 || Character.isDigit(name.charAt(0))) {
            name.insert(0, 'v');
        }
        String result = name.toString();
        return RESERVED.contains(result) ? result + "_" : result;
    }

    /** A double-quoted string literal. */
    private static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < ' ') {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    /** A number literal as the shortest text of the float: whole numbers without a fraction, negative zero as 0. */
    private static String numberLiteral(float value) {
        if (value == 0f) {
            return "0";
        }
        if (value == Math.rint(value) && !Float.isInfinite(value) && Math.abs(value) < 1e7f) {
            return Long.toString((long) value);
        }
        return Float.toString(value);
    }
}
