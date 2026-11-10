package io.github.ldev22live.gdxunity.engine.visualscript;

import com.badlogic.gdx.math.Vector3;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Turns a graph into the source of an equivalent {@code ScriptComponent} (TD-044): the visual graph is the way in, and
 * the generated Java or Kotlin is a plain script a programmer can keep editing. Each event node becomes a method, each
 * action node a statement, each pure node an expression, and each Delay node a timer field with a continuation method.
 * <p>
 * The generated script behaves like the interpreter ({@link GraphInstance}) for what it supports. Differences:
 * Collision and Trigger events become public {@code onCollision} / {@code onTriggerEnter} methods that the game has
 * to call (the interpreter is fed by physics, a script is not), and a graph whose execution wires form a loop is
 * refused instead of being cut off after {@link GraphInstance#MAX_NODES_PER_EVENT} nodes.
 * <p>
 * The script ends with the graph embedded as comments ({@link GraphSourceEmbed}), so the web editor can import it (TD-045).
 */
public final class GraphCodeGenerator {

    /** Output language. */
    public enum Language {
        JAVA(".java"),
        KOTLIN(".kt");

        private final String extension;

        Language(String extension) {
            this.extension = extension;
        }

        /** File extension including the dot. */
        public String getExtension() {
            return extension;
        }
    }

    /** Package of generated scripts, below the project's {@code scripts} folder. */
    public static final String PACKAGE = "generated";

    private static final String INDENT = "    ";
    private static final Set<String> RESERVED = Set.of("abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
        "class", "const", "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for",
        "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private",
        "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this", "throw",
        "throws", "transient", "try", "void", "volatile", "while", "true", "false", "null", "fun", "val", "var", "when", "object",
        "is", "in", "as", "typealias", "internal", "open", "override", "entity", "deltaTime");

    private final NodeRegistry registry;

    public GraphCodeGenerator(NodeRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry cannot be null");
    }

    /** Class name generated for {@code graph}: its name in PascalCase with symbols removed ("door open" gives "DoorOpen"). */
    public static String className(Graph graph) {
        StringBuilder name = new StringBuilder();
        boolean upper = true;
        for (char c : graph.getName().toCharArray()) {
            if (Character.isLetterOrDigit(c) && c < 128) {
                name.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            } else {
                upper = true;
            }
        }
        if (name.length() == 0 || Character.isDigit(name.charAt(0))) {
            name.insert(0, "Graph");
        }
        return name.toString();
    }

    /**
     * Generates the script.
     *
     * @throws IllegalArgumentException when the graph has validation problems, or an execution loop that code cannot express
     */
    public String generate(Graph graph, Language language) {
        Objects.requireNonNull(graph, "graph cannot be null");
        Objects.requireNonNull(language, "language cannot be null");
        List<GraphValidator.Problem> problems = new GraphValidator(registry).validate(graph);
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("Graph " + graph.getName() + " has " + problems.size() + " problem(s), first: "
                + problems.get(0).message());
        }
        return GraphSourceEmbed.append(new Run(graph, language).generate(), graph);
    }

    /** One generation: holds the graph, the language and what the script needs besides the chains. */
    private final class Run {
        private final Graph graph;
        private final boolean kotlin;
        private final String className;
        private final List<String> delayNodes = new ArrayList<>();
        private final List<GraphNode> raycastNodes = new ArrayList<>();
        private final List<GraphNode> methodResultNodes = new ArrayList<>();
        private final Map<String, String> methods = new LinkedHashMap<>();
        private boolean needsWorldPosition;
        private boolean needsFormatNumber;
        private boolean needsKeys;

        Run(Graph graph, Language language) {
            this.graph = graph;
            this.kotlin = language == Language.KOTLIN;
            this.className = className(graph);
        }

        String generate() {
            StringBuilder body = new StringBuilder();
            // Event nodes first: their chains find the Delay nodes, whose methods are made right after
            List<GraphNode> begin = nodesOf(BuiltInNodes.EVENT_BEGIN_PLAY);
            List<GraphNode> tick = nodesOf(BuiltInNodes.EVENT_TICK);
            List<GraphNode> collision = nodesOf(BuiltInNodes.EVENT_COLLISION);
            List<GraphNode> trigger = nodesOf(BuiltInNodes.EVENT_TRIGGER);
            List<GraphNode> custom = nodesOf(BuiltInNodes.EVENT_CUSTOM);
            for (GraphNode node : concat(begin, tick, collision, trigger, custom)) {
                methods.put("run" + capital(identifier(node.getId())), chainBody(node.getId(), PinDef.EXEC_OUT, 2));
            }
            for (int i = 0; i < delayNodes.size(); i++) {
                // The list grows while a continuation holds another Delay
                String id = delayNodes.get(i);
                methods.put("run" + capital(identifier(id)) + "Completed", chainBody(id, "completed", 2));
            }

            StringBuilder fields = new StringBuilder();
            for (GraphVariable variable : graph.getVariables()) {
                fields.append(INDENT).append(field(typeName(variable.type()), identifier(variable.name()),
                    literal(variable.type(), variable.initialValue()), variable.type() == ValueType.ENTITY)).append('\n');
            }
            for (GraphNode node : tick) {
                fields.append(INDENT).append(field("float", outputField(node, BuiltInNodes.PIN_DELTA_TIME), "0f", false)).append('\n');
            }
            for (GraphNode node : concat(collision, trigger)) {
                fields.append(INDENT).append(field("Entity", outputField(node, BuiltInNodes.PIN_OTHER), "null", true)).append('\n');
            }
            for (GraphNode node : methodResultNodes) {
                fields.append(INDENT).append(resultField(node)).append('\n');
            }
            for (GraphNode node : raycastNodes) {
                fields.append(INDENT).append(field("Entity", outputField(node, BuiltInNodes.PIN_HIT_ENTITY), "null", true)).append('\n');
                fields.append(INDENT).append(field("Vector3", outputField(node, BuiltInNodes.PIN_HIT_POINT), newVector(), false)).append('\n');
                fields.append(INDENT).append(field("Vector3", outputField(node, BuiltInNodes.PIN_HIT_NORMAL), newVector(), false)).append('\n');
                fields.append(INDENT).append(field("float", outputField(node, BuiltInNodes.PIN_HIT_DISTANCE), "0f", false)).append('\n');
            }
            for (String id : delayNodes) {
                fields.append(INDENT).append(field("float", delayField(id), "-1f", false)).append('\n');
            }

            // Entry points
            body.append(INDENT).append(kotlin ? "override fun onStart() {" : "@Override\n" + INDENT + "public void onStart() {").append('\n');
            body.append(INDENT).append(INDENT).append(call("super.onStart()")).append('\n');
            for (GraphNode node : begin) {
                body.append(INDENT).append(INDENT).append(call(runMethod(node))).append('\n');
            }
            body.append(INDENT).append("}\n\n");

            body.append(INDENT).append(kotlin ? "override fun onUpdate(deltaTime: Float) {"
                : "@Override\n" + INDENT + "public void onUpdate(float deltaTime) {").append('\n');
            body.append(INDENT).append(INDENT).append(call("super.onUpdate(deltaTime)")).append('\n');
            for (String id : delayNodes) {
                String timer = delayField(id);
                body.append(INDENT).append(INDENT).append("if (").append(timer).append(" >= 0f) {\n");
                body.append(INDENT).append(INDENT).append(INDENT).append(timer).append(" -= deltaTime").append(end()).append('\n');
                body.append(INDENT).append(INDENT).append(INDENT).append("if (").append(timer).append(" <= 0f) {\n");
                body.append(INDENT).append(INDENT).append(INDENT).append(INDENT).append(timer).append(" = -1f").append(end()).append('\n');
                body.append(INDENT).append(INDENT).append(INDENT).append(INDENT)
                    .append(call("run" + capital(identifier(id)) + "Completed()")).append('\n');
                body.append(INDENT).append(INDENT).append(INDENT).append("}\n");
                body.append(INDENT).append(INDENT).append("}\n");
            }
            for (GraphNode node : tick) {
                body.append(INDENT).append(INDENT).append(outputField(node, BuiltInNodes.PIN_DELTA_TIME)).append(" = deltaTime")
                    .append(end()).append('\n');
                body.append(INDENT).append(INDENT).append(call(runMethod(node))).append('\n');
            }
            body.append(INDENT).append("}\n\n");

            appendOtherEntry(body, "onCollision", collision, "Called when the physics reports that this entity touched {@code other}.");
            appendOtherEntry(body, "onTriggerEnter", trigger, "Called when {@code other} enters this entity's trigger.");
            if (!custom.isEmpty()) {
                appendCustomEvents(body, custom);
            }

            for (Map.Entry<String, String> method : methods.entrySet()) {
                body.append(INDENT).append(kotlin ? "private fun " + method.getKey() + "() {" : "private void " + method.getKey() + "() {")
                    .append('\n').append(method.getValue()).append(INDENT).append("}\n\n");
            }
            appendHelpers(body);

            StringBuilder out = new StringBuilder();
            out.append("package ").append(PACKAGE).append(kotlin ? "\n\n" : ";\n\n");
            appendImports(out);
            out.append("/**\n * Generated from the graph \"").append(graph.getName().replace("*/", "* /"))
                .append("\" by Forgeroom (Visual Scripting > Generate ").append(kotlin ? "Kotlin" : "Java").append(").\n")
                .append(" * Edit it freely; generating again writes the file anew. Bind it to an entity with an Entity Script Binding\n")
                .append(" * component named ").append(PACKAGE).append('.').append(className).append(".\n */\n");
            out.append(kotlin ? "class " + className + " : ScriptComponent() {\n" : "public class " + className + " extends ScriptComponent {\n");
            out.append(fields);
            if (fields.length() > 0) {
                out.append('\n');
            }
            out.append(body.toString().stripTrailing()).append("\n}\n");
            return out.toString();
        }

        private void appendImports(StringBuilder out) {
            List<String> imports = new ArrayList<>();
            imports.add("com.badlogic.gdx.Gdx");
            imports.add("com.badlogic.gdx.math.Vector3");
            imports.add("io.github.ldev22live.gdxunity.engine.scene.Entity");
            imports.add("io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent");
            if (needsWorldPosition && !kotlin) {
                imports.add("io.github.ldev22live.gdxunity.engine.scene.Transform");
            }
            if (!raycastNodes.isEmpty() && !kotlin) {
                imports.add("io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsSystem3D");
            }
            if (needsKeys) {
                imports.add("com.badlogic.gdx.Input");
            }
            if (needsFormatNumber) {
                imports.add("java.util.Locale");
            }
            for (String name : imports) {
                out.append("import ").append(name).append(kotlin ? "\n" : ";\n");
            }
            out.append('\n');
        }

        private void appendOtherEntry(StringBuilder body, String method, List<GraphNode> nodes, String doc) {
            if (nodes.isEmpty()) {
                return;
            }
            body.append(INDENT).append("/** ").append(doc).append(" */\n");
            body.append(INDENT).append(kotlin ? "fun " + method + "(other: Entity?) {" : "public void " + method + "(Entity other) {").append('\n');
            for (GraphNode node : nodes) {
                body.append(INDENT).append(INDENT).append(outputField(node, BuiltInNodes.PIN_OTHER)).append(" = other").append(end()).append('\n');
                body.append(INDENT).append(INDENT).append(call(runMethod(node))).append('\n');
            }
            body.append(INDENT).append("}\n\n");
        }

        private void appendCustomEvents(StringBuilder body, List<GraphNode> custom) {
            body.append(INDENT).append("/** Runs the custom event nodes named {@code name}; false when the graph has none. */\n");
            body.append(INDENT).append(kotlin ? "fun fireCustomEvent(name: String): Boolean {" : "public boolean fireCustomEvent(String name) {").append('\n');
            body.append(INDENT).append(INDENT).append(kotlin ? "var handled = false" : "boolean handled = false;").append('\n');
            for (GraphNode node : custom) {
                body.append(INDENT).append(INDENT).append("if (name").append(kotlin ? " == " : ".equals(").append(quote(String.valueOf(node.getProperty(BuiltInNodes.PROPERTY_NAME))))
                    .append(kotlin ? "" : ")").append(") {\n");
                body.append(INDENT).append(INDENT).append(INDENT).append(call(runMethod(node))).append('\n');
                body.append(INDENT).append(INDENT).append(INDENT).append("handled = true").append(end()).append('\n');
                body.append(INDENT).append(INDENT).append("}\n");
            }
            body.append(INDENT).append(INDENT).append("return handled").append(end()).append('\n');
            body.append(INDENT).append("}\n\n");
        }

        private void appendHelpers(StringBuilder body) {
            if (needsWorldPosition) {
                if (kotlin) {
                    body.append(INDENT).append("private fun setWorldPosition(target: Entity, world: Vector3) {\n")
                        .append(INDENT).append(INDENT).append("val parent = target.getTransform().getParent()\n")
                        .append(INDENT).append(INDENT).append("if (parent == null) {\n")
                        .append(INDENT).append(INDENT).append(INDENT).append("target.getTransform().setLocalPosition(world)\n")
                        .append(INDENT).append(INDENT).append(INDENT).append("return\n")
                        .append(INDENT).append(INDENT).append("}\n")
                        .append(INDENT).append(INDENT).append("target.getTransform().setLocalPosition(Vector3(world).mul(parent.getWorldMatrix().inv()))\n")
                        .append(INDENT).append("}\n\n");
                } else {
                    body.append(INDENT).append("private static void setWorldPosition(Entity target, Vector3 world) {\n")
                        .append(INDENT).append(INDENT).append("Transform parent = target.getTransform().getParent();\n")
                        .append(INDENT).append(INDENT).append("if (parent == null) {\n")
                        .append(INDENT).append(INDENT).append(INDENT).append("target.getTransform().setLocalPosition(world);\n")
                        .append(INDENT).append(INDENT).append(INDENT).append("return;\n")
                        .append(INDENT).append(INDENT).append("}\n")
                        .append(INDENT).append(INDENT).append("target.getTransform().setLocalPosition(new Vector3(world).mul(parent.getWorldMatrix().inv()));\n")
                        .append(INDENT).append("}\n\n");
                }
            }
            if (needsKeys) {
                if (kotlin) {
                    body.append(INDENT).append("private fun keyDown(vararg keys: Int): Boolean {\n")
                        .append(INDENT).append(INDENT).append("return keys.any { Gdx.input.isKeyPressed(it) }\n")
                        .append(INDENT).append("}\n\n");
                } else {
                    body.append(INDENT).append("private static boolean keyDown(int... keys) {\n")
                        .append(INDENT).append(INDENT).append("for (int key : keys) {\n")
                        .append(INDENT).append(INDENT).append(INDENT).append("if (Gdx.input.isKeyPressed(key)) {\n")
                        .append(INDENT).append(INDENT).append(INDENT).append(INDENT).append("return true;\n")
                        .append(INDENT).append(INDENT).append(INDENT).append("}\n")
                        .append(INDENT).append(INDENT).append("}\n")
                        .append(INDENT).append(INDENT).append("return false;\n")
                        .append(INDENT).append("}\n\n");
                }
            }
            if (needsFormatNumber) {
                if (kotlin) {
                    body.append(INDENT).append("private fun formatNumber(value: Float): String {\n")
                        .append(INDENT).append(INDENT).append("if (value == Math.rint(value.toDouble()).toFloat() && !value.isInfinite()) {\n")
                        .append(INDENT).append(INDENT).append(INDENT).append("return value.toLong().toString()\n")
                        .append(INDENT).append(INDENT).append("}\n")
                        .append(INDENT).append(INDENT).append("return String.format(Locale.ROOT, \"%.3f\", value).replace(Regex(\"0+$\"), \"\").replace(Regex(\"\\\\.$\"), \"\")\n")
                        .append(INDENT).append("}\n\n");
                } else {
                    body.append(INDENT).append("private static String formatNumber(float value) {\n")
                        .append(INDENT).append(INDENT).append("if (value == Math.rint(value) && !Float.isInfinite(value)) {\n")
                        .append(INDENT).append(INDENT).append(INDENT).append("return Long.toString((long) value);\n")
                        .append(INDENT).append(INDENT).append("}\n")
                        .append(INDENT).append(INDENT).append("return String.format(Locale.ROOT, \"%.3f\", value).replaceAll(\"0+$\", \"\").replaceAll(\"\\\\.$\", \"\");\n")
                        .append(INDENT).append("}\n\n");
                }
            }
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
                    out.append(pad).append("if (").append(input(node, "condition")).append(") {\n");
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
                    String timer = delayField(nodeId);
                    out.append(pad).append("if (").append(timer).append(" < 0f) ").append(timer).append(" = ")
                        .append(input(node, "seconds")).append(end()).append('\n');
                }
                case BuiltInNodes.ACTION_PRINT -> {
                    out.append(pad).append("Gdx.app.log(").append(quote(className)).append(", ").append(input(node, "message")).append(')')
                        .append(end()).append('\n');
                    emitFrom(nodeId, PinDef.EXEC_OUT, depth, path, out);
                }
                case BuiltInNodes.ACTION_SET_POSITION -> {
                    needsWorldPosition = true;
                    out.append(pad).append("setWorldPosition(").append(target(node)).append(", ").append(input(node, "position")).append(')')
                        .append(end()).append('\n');
                    emitFrom(nodeId, PinDef.EXEC_OUT, depth, path, out);
                }
                case BuiltInNodes.ACTION_MOVE_BY -> {
                    needsWorldPosition = true;
                    String target = target(node);
                    out.append(pad).append("setWorldPosition(").append(target).append(", ").append(target)
                        .append(".getTransform().getWorldPosition().add(").append(input(node, "offset")).append("))").append(end()).append('\n');
                    emitFrom(nodeId, PinDef.EXEC_OUT, depth, path, out);
                }
                case BuiltInNodes.ACTION_ROTATE_BY -> {
                    String degrees = input(node, "degrees");
                    out.append(pad).append(target(node)).append(".getTransform().rotate(").append(vectorComponent(degrees, "x")).append(", ")
                        .append(vectorComponent(degrees, "y")).append(", ").append(vectorComponent(degrees, "z")).append(')').append(end()).append('\n');
                    emitFrom(nodeId, PinDef.EXEC_OUT, depth, path, out);
                }
                case BuiltInNodes.ACTION_DESTROY -> {
                    String target = target(node);
                    out.append(pad).append(target).append(".getScene()").append(kotlin ? "?" : "").append(".removeEntity(").append(target).append(')')
                        .append(end()).append('\n');
                    emitFrom(nodeId, PinDef.EXEC_OUT, depth, path, out);
                }
                case BuiltInNodes.ACTION_ADD_FORCE -> {
                    out.append(pad).append("requirePhysics3D().addForce(").append(target(node)).append(", ").append(input(node, "force"))
                        .append(')').append(end()).append('\n');
                    emitFrom(nodeId, PinDef.EXEC_OUT, depth, path, out);
                }
                case BuiltInNodes.ACTION_ADD_IMPULSE -> {
                    out.append(pad).append("requirePhysics3D().addImpulse(").append(target(node)).append(", ").append(input(node, "impulse"))
                        .append(')').append(end()).append('\n');
                    emitFrom(nodeId, PinDef.EXEC_OUT, depth, path, out);
                }
                case BuiltInNodes.ACTION_SET_VELOCITY -> {
                    out.append(pad).append("requirePhysics3D().setLinearVelocity(").append(target(node)).append(", ")
                        .append(input(node, "velocity")).append(')').append(end()).append('\n');
                    emitFrom(nodeId, PinDef.EXEC_OUT, depth, path, out);
                }
                case BuiltInNodes.ACTION_MOVE_CHARACTER -> {
                    out.append(pad).append("requirePhysics3D().moveCharacter(").append(target(node)).append(", ")
                        .append(input(node, "velocity")).append(')').append(end()).append('\n');
                    emitFrom(nodeId, PinDef.EXEC_OUT, depth, path, out);
                }
                case BuiltInNodes.ACTION_JUMP_CHARACTER -> {
                    out.append(pad).append("requirePhysics3D().jumpCharacter(").append(target(node)).append(", ")
                        .append(input(node, "speed")).append(')').append(end()).append('\n');
                    emitFrom(nodeId, PinDef.EXEC_OUT, depth, path, out);
                }
                case BuiltInNodes.ACTION_RAYCAST_3D -> {
                    if (!raycastNodes.contains(node)) {
                        raycastNodes.add(node);
                    }
                    String hit = identifier(nodeId) + "Hit";
                    out.append(pad).append(kotlin ? "val " : "PhysicsSystem3D.RaycastHit ").append(hit).append(" = raycast3D(")
                        .append(input(node, "origin")).append(", ").append(input(node, "direction")).append(", ")
                        .append(input(node, "distance")).append(')').append(end()).append('\n');
                    out.append(pad).append("if (").append(hit).append(" != null) {\n");
                    String inner = pad + INDENT;
                    out.append(inner).append(outputField(node, BuiltInNodes.PIN_HIT_ENTITY)).append(" = ").append(hit).append(".entity()")
                        .append(end()).append('\n');
                    out.append(inner).append(outputField(node, BuiltInNodes.PIN_HIT_POINT)).append(" = ").append(hit).append(".point()")
                        .append(end()).append('\n');
                    out.append(inner).append(outputField(node, BuiltInNodes.PIN_HIT_NORMAL)).append(" = ").append(hit).append(".normal()")
                        .append(end()).append('\n');
                    out.append(inner).append(outputField(node, BuiltInNodes.PIN_HIT_DISTANCE)).append(" = ").append(hit).append(".distance()")
                        .append(end()).append('\n');
                    emitFrom(nodeId, BuiltInNodes.PIN_HIT, depth + 1, path, out);
                    out.append(pad).append("} else {\n");
                    emitFrom(nodeId, BuiltInNodes.PIN_MISS, depth + 1, path, out);
                    out.append(pad).append("}\n");
                }
                case BuiltInNodes.VARIABLE_SET -> {
                    out.append(pad).append(identifier((String) node.getProperty(BuiltInNodes.PROPERTY_VARIABLE))).append(" = ")
                        .append(input(node, "value")).append(end()).append('\n');
                    emitFrom(nodeId, PinDef.EXEC_OUT, depth, path, out);
                }
                default -> {
                    if (!JavaMethodNodes.isMethodNode(node.getTypeId())) {
                        throw new IllegalArgumentException("Cannot generate code for node type " + node.getTypeId());
                    }
                    emitMethodNode(node, depth, path, out);
                }
            }
            path.remove(nodeId);
        }

        // --- Expressions ---

        /** Code of the data input {@code pinId}: the wire's source, else the typed-in value, else the type's default. */
        private String input(GraphNode node, String pinId) {
            return input(node, pinId, 0);
        }

        private String input(GraphNode node, String pinId, int depth) {
            PinDef pin = registry.get(node.getTypeId()).pin(node, graph, pinId);
            List<GraphLink> wires = graph.linksInto(node.getId(), pinId);
            if (!wires.isEmpty()) {
                GraphLink wire = wires.get(0);
                String value = output(wire.fromNode(), wire.fromPin(), depth + 1);
                GraphNode source = graph.getNode(wire.fromNode());
                PinDef from = registry.get(source.getTypeId()).pin(source, graph, wire.fromPin());
                return kotlin && pin.type() == ValueType.FLOAT && from.type() == ValueType.INTEGER
                    ? "(" + value + ").toFloat()" : value;
            }
            if (pin.type() == ValueType.ENTITY) {
                return "getEntity()";
            }
            Object literal = node.getLiteral(pinId);
            return literal(pin.type(), literal != null ? literal : pin.type().defaultValue());
        }

        /** Entity a node acts on: its Target wire, or this script's entity; Kotlin needs the not-null assertion. */
        private String target(GraphNode node) {
            String code = input(node, "target");
            return kotlin && !code.equals("getEntity()") ? "(" + code + ")!!" : code;
        }

        private String output(String nodeId, String pinId, int depth) {
            if (depth > GraphInstance.MAX_DATA_DEPTH) {
                throw new IllegalArgumentException("Data chain deeper than " + GraphInstance.MAX_DATA_DEPTH + " nodes at " + nodeId);
            }
            GraphNode node = graph.getNode(nodeId);
            if (node.getTypeId().startsWith(ComponentNodes.GET_PREFIX)) {
                Class<?> component = (Class<?>) registry.get(node.getTypeId()).getSource();
                return targetOf(node, depth) + ".getComponent(" + component.getCanonicalName()
                    + (kotlin ? "::class.java)" : ".class)");
            }
            switch (node.getTypeId()) {
                case BuiltInNodes.EVENT_TICK, BuiltInNodes.EVENT_COLLISION, BuiltInNodes.EVENT_TRIGGER:
                    return outputField(node, pinId);
                case BuiltInNodes.VARIABLE_GET:
                case BuiltInNodes.VARIABLE_SET:
                    return identifier((String) node.getProperty(BuiltInNodes.PROPERTY_VARIABLE));
                case BuiltInNodes.DATA_SELF:
                    return "getEntity()";
                case BuiltInNodes.DATA_GET_POSITION:
                    return targetOf(node, depth) + ".getTransform().getWorldPosition()";
                case BuiltInNodes.DATA_GET_NAME:
                    return targetOf(node, depth) + ".getName()";
                case BuiltInNodes.DATA_GET_VELOCITY:
                    return "requirePhysics3D().getLinearVelocity(" + targetOf(node, depth) + ")";
                case BuiltInNodes.DATA_IS_GROUNDED:
                    return "requirePhysics3D().isCharacterGrounded(" + targetOf(node, depth) + ")";
                case BuiltInNodes.INPUT_KEY_DOWN:
                    needsKeys = true;
                    return keyDownCall((String) node.getProperty(BuiltInNodes.PROPERTY_KEY));
                case BuiltInNodes.INPUT_AXIS:
                    needsKeys = true;
                    return "((" + keyDownCall((String) node.getProperty(BuiltInNodes.PROPERTY_POSITIVE)) + " ? 1f : 0f) - ("
                        + keyDownCall((String) node.getProperty(BuiltInNodes.PROPERTY_NEGATIVE)) + " ? 1f : 0f))";
                case BuiltInNodes.ACTION_RAYCAST_3D:
                    return outputField(node, pinId);
                case BuiltInNodes.MATH_ADD:
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
                    return binary(node, "==", depth);
                case BuiltInNodes.LOGIC_AND:
                    return binary(node, "&&", depth);
                case BuiltInNodes.LOGIC_OR:
                    return binary(node, "||", depth);
                case BuiltInNodes.MATH_SIN:
                case BuiltInNodes.MATH_COS: {
                    String function = node.getTypeId().equals(BuiltInNodes.MATH_SIN) ? "sin" : "cos";
                    String degrees = input(node, "degrees", depth);
                    return kotlin ? "Math." + function + "(Math.toRadians((" + degrees + ").toDouble())).toFloat()"
                        : "(float) Math." + function + "(Math.toRadians(" + degrees + "))";
                }
                case BuiltInNodes.LOGIC_NOT:
                    return "(!" + input(node, "value", depth) + ")";
                case BuiltInNodes.VECTOR_MAKE:
                    return (kotlin ? "Vector3(" : "new Vector3(") + input(node, "x", depth) + ", " + input(node, "y", depth) + ", "
                        + input(node, "z", depth) + ")";
                case BuiltInNodes.VECTOR_BREAK:
                    return vectorComponent(input(node, "vector", depth), pinId);
                case BuiltInNodes.VECTOR_ADD:
                    return (kotlin ? "Vector3(" : "new Vector3(") + input(node, "a", depth) + ").add(" + input(node, "b", depth) + ")";
                case BuiltInNodes.VECTOR_SCALE:
                    return (kotlin ? "Vector3(" : "new Vector3(") + input(node, "vector", depth) + ").scl(" + input(node, "factor", depth) + ")";
                case BuiltInNodes.TEXT_APPEND:
                    return "(" + input(node, "a", depth) + " + " + input(node, "b", depth) + ")";
                case BuiltInNodes.TEXT_FROM_FLOAT:
                    needsFormatNumber = true;
                    return "formatNumber(" + input(node, "value", depth) + ")";
                default:
                    if (JavaMethodNodes.isMethodNode(node.getTypeId())) {
                        return methodValue(node, depth);
                    }
                    throw new IllegalArgumentException("Cannot generate code for node type " + node.getTypeId());
            }
        }

        /** {@code keyDown(Input.Keys.A, Input.Keys.LEFT)}: the keys of a Key Down or Input Axis node as constants, checked when generating. */
        private String keyDownCall(String names) {
            return "keyDown(" + String.join(", ", KeyNames.constants(names)) + ")";
        }

        private String targetOf(GraphNode node, int depth) {
            String code = input(node, "target", depth);
            return kotlin && !code.equals("getEntity()") ? "(" + code + ")!!" : code;
        }

        private String binary(GraphNode node, String operator, int depth) {
            return "(" + input(node, "a", depth) + " " + operator + " " + input(node, "b", depth) + ")";
        }

        /** {@code vector.x}: a component of a vector expression that may be a constructor call. */
        private String vectorComponent(String vector, String component) {
            return (vector.startsWith("new ") || vector.startsWith("Vector3(") ? "(" + vector + ")" : vector) + "." + component;
        }


        // --- Method nodes (TD-060) ---

        private void emitMethodNode(GraphNode node, int depth, Set<String> path, StringBuilder out) {
            NodeType type = registry.get(node.getTypeId());
            Executable member = (Executable) type.getSource();
            String call = methodCall(node, member, depth);
            PinDef result = type.pin(node, graph, JavaMethodNodes.PIN_RESULT);
            String pad = INDENT.repeat(depth);
            if (result != null) {
                if (!methodResultNodes.contains(node)) {
                    methodResultNodes.add(node);
                }
                out.append(pad).append(outputField(node, JavaMethodNodes.PIN_RESULT)).append(" = ")
                    .append(convertResult(call, member, result.type())).append(end()).append('\n');
            } else {
                out.append(pad).append(call).append(end()).append('\n');
            }
            emitFrom(node.getId(), PinDef.EXEC_OUT, depth, path, out);
        }

        /** The value of a method node's result: its call when it is Pure, the field that kept it when it is an Action. */
        private String methodValue(GraphNode node, int depth) {
            NodeType type = registry.get(node.getTypeId());
            if (type.getKind() == NodeType.Kind.ACTION) {
                return outputField(node, JavaMethodNodes.PIN_RESULT);
            }
            Executable member = (Executable) type.getSource();
            PinDef result = type.pin(node, graph, JavaMethodNodes.PIN_RESULT);
            return convertResult(methodCall(node, member, depth), member, result.type());
        }

        private String methodCall(GraphNode node, Executable member, int depth) {
            Class<?> owner = member.getDeclaringClass();
            String className = owner.getCanonicalName();
            if (className == null) {
                throw new IllegalArgumentException("Cannot generate code for " + node.getTypeId() + ": the class has no source name");
            }
            Class<?>[] parameterTypes = member.getParameterTypes();
            List<String> arguments = new ArrayList<>();
            for (int i = 0; i < parameterTypes.length; i++) {
                arguments.add(convertArgument(input(node, JavaMethodNodes.PIN_ARGUMENT + i, depth), parameterTypes[i]));
            }
            String joined = String.join(", ", arguments);
            if (member instanceof Constructor<?>) {
                return (kotlin ? "" : "new ") + className + "(" + joined + ")";
            }
            if (java.lang.reflect.Modifier.isStatic(member.getModifiers())) {
                return className + "." + member.getName() + "(" + joined + ")";
            }
            String target = input(node, JavaMethodNodes.PIN_TARGET, depth);
            if (kotlin && !target.equals("getEntity()")) {
                target = "(" + target + ")!!";
            }
            return target + "." + member.getName() + "(" + joined + ")";
        }

        /** Kotlin does not widen numbers by itself, and an enum parameter is given by the constant's name. */
        private String convertArgument(String code, Class<?> parameter) {
            if (parameter.isEnum()) {
                return parameter.getCanonicalName() + ".valueOf(" + code + ")";
            }
            if (!kotlin) {
                return code;
            }
            if (parameter == float.class || parameter == Float.class) {
                return "(" + code + ").toFloat()";
            }
            if (parameter == double.class || parameter == Double.class) {
                return "(" + code + ").toDouble()";
            }
            if (parameter == int.class || parameter == Integer.class) {
                return "(" + code + ").toInt()";
            }
            if (parameter == long.class || parameter == Long.class) {
                return "(" + code + ").toLong()";
            }
            if (parameter == short.class || parameter == Short.class) {
                return "(" + code + ").toShort()";
            }
            if (parameter == byte.class || parameter == Byte.class) {
                return "(" + code + ").toByte()";
            }
            return code;
        }

        /** Brings a method's result to the pin's type: doubles become floats, longs ints and enums their names. */
        private String convertResult(String call, Executable member, ValueType pinType) {
            Class<?> result = member instanceof Constructor<?> ? member.getDeclaringClass() : ((Method) member).getReturnType();
            if (result.isEnum()) {
                return call + (kotlin ? ".name" : ".name()");
            }
            if (pinType == ValueType.FLOAT && (result == double.class || result == Double.class)) {
                return kotlin ? "(" + call + ").toFloat()" : "(float) " + call;
            }
            if (pinType == ValueType.INTEGER && result != int.class && result != Integer.class) {
                return kotlin ? "(" + call + ").toInt()" : "(int) " + call;
            }
            return call;
        }

        /** Declaration of the field that keeps an Action method node's result. */
        private String resultField(GraphNode node) {
            PinDef result = registry.get(node.getTypeId()).pin(node, graph, JavaMethodNodes.PIN_RESULT);
            String type = result.type() == ValueType.OBJECT ? result.objectClass().getCanonicalName() : typeName(result.type());
            return field(type, outputField(node, JavaMethodNodes.PIN_RESULT), literal(result.type(), result.type().defaultValue()),
                result.type().allowsNull());
        }

        // --- Language details ---

        private String typeName(ValueType type) {
            return switch (type) {
                case FLOAT -> kotlin ? "Float" : "float";
                case BOOLEAN -> kotlin ? "Boolean" : "boolean";
                case STRING -> "String";
                case VECTOR3 -> "Vector3";
                case ENTITY -> "Entity";
                case INTEGER -> kotlin ? "Int" : "int";
                case OBJECT -> throw new IllegalArgumentException("Objects cannot be graph variables");
            };
        }

        private String newVector() {
            return kotlin ? "Vector3()" : "new Vector3()";
        }

        private String field(String type, String name, String value, boolean nullable) {
            return kotlin ? "private var " + name + ": " + (type.equals("float") ? "Float" : type) + (nullable ? "?" : "") + " = " + value
                : "private " + type + " " + name + " = " + value + ";";
        }

        private String literal(ValueType type, Object value) {
            return switch (type) {
                case FLOAT -> floatLiteral((Float) value);
                case BOOLEAN -> String.valueOf(value);
                case STRING -> quote((String) value);
                case VECTOR3 -> {
                    Vector3 v = (Vector3) value;
                    yield (kotlin ? "Vector3(" : "new Vector3(") + floatLiteral(v.x) + ", " + floatLiteral(v.y) + ", " + floatLiteral(v.z) + ")";
                }
                case ENTITY, OBJECT -> "null";
                case INTEGER -> String.valueOf(value);
            };
        }

        /** A string literal; Kotlin also treats a dollar sign as the start of a template. */
        private String quote(String text) {
            StringBuilder out = new StringBuilder("\"");
            for (char c : text.toCharArray()) {
                switch (c) {
                    case '"' -> out.append("\\\"");
                    case '\\' -> out.append("\\\\");
                    case '\n' -> out.append("\\n");
                    case '\r' -> out.append("\\r");
                    case '\t' -> out.append("\\t");
                    case '$' -> out.append(kotlin ? "\\$" : "$");
                    default -> out.append(c);
                }
            }
            return out.append('"').toString();
        }

        private String end() {
            return kotlin ? "" : ";";
        }

        private String call(String code) {
            return code + end();
        }

        private String runMethod(GraphNode node) {
            return "run" + capital(identifier(node.getId())) + "()";
        }

        private String outputField(GraphNode node, String pin) {
            return identifier(node.getId()) + capital(pin);
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

    @SafeVarargs
    private static <T> List<T> concat(List<T>... lists) {
        List<T> all = new ArrayList<>();
        for (List<T> list : lists) {
            all.addAll(list);
        }
        return all;
    }

    private static String capital(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    /** A name that is legal in Java and Kotlin: symbols become underscores, and keywords and leading digits get a prefix. */
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

    private static String floatLiteral(float value) {
        if (value == Math.rint(value) && !Float.isInfinite(value) && Math.abs(value) < 1e7f) {
            return String.format(Locale.ROOT, "%.1ff", value);
        }
        return Float.toString(value) + "f";
    }

}
