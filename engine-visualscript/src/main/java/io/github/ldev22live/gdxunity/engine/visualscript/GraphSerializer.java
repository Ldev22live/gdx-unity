package io.github.ldev22live.gdxunity.engine.visualscript;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializationException;

import java.util.Map;

/**
 * Reads and writes {@code .graph} files:
 * <pre>
 * { "schemaVersion": 1, "kind": "graph", "name": "Door",
 *   "variables": [ { "name": "speed", "type": "FLOAT", "value": 2 } ],
 *   "nodes": [ { "id": "n1", "type": "event.tick", "x": 0, "y": 0,
 *                "literals": [ { "pin": "a", "type": "FLOAT", "value": 1 } ], "properties": [ ... ] } ],
 *   "links": [ { "from": "n1", "fromPin": "then", "to": "n2", "toPin": "exec" } ] }
 * </pre>
 * Values carry their type so a file can be read without the node registry. Reading does not validate the graph;
 * use {@link GraphValidator}.
 */
public final class GraphSerializer {
    public static final int SCHEMA_VERSION = 1;
    public static final String KIND = "graph";
    /** Width at which the pretty printer puts small objects on one line. */
    private static final int PRETTY_PRINT_LINE_WIDTH = 100;

    private GraphSerializer() {}

    public static String toJson(Graph graph) {
        JsonValue root = new JsonValue(JsonValue.ValueType.object);
        root.addChild("schemaVersion", new JsonValue(SCHEMA_VERSION));
        root.addChild("kind", new JsonValue(KIND));
        root.addChild("name", new JsonValue(graph.getName()));

        JsonValue variables = new JsonValue(JsonValue.ValueType.array);
        for (GraphVariable variable : graph.getVariables()) {
            JsonValue v = new JsonValue(JsonValue.ValueType.object);
            v.addChild("name", new JsonValue(variable.name()));
            v.addChild("type", new JsonValue(variable.type().name()));
            v.addChild("value", writeValue(variable.type(), variable.initialValue()));
            variables.addChild(v);
        }
        root.addChild("variables", variables);

        JsonValue nodes = new JsonValue(JsonValue.ValueType.array);
        for (GraphNode node : graph.getNodes()) {
            JsonValue n = new JsonValue(JsonValue.ValueType.object);
            n.addChild("id", new JsonValue(node.getId()));
            n.addChild("type", new JsonValue(node.getTypeId()));
            n.addChild("x", new JsonValue(node.getX()));
            n.addChild("y", new JsonValue(node.getY()));
            n.addChild("literals", writeTypedValues(node.getLiterals(), "pin"));
            n.addChild("properties", writeTypedValues(node.getProperties(), "name"));
            nodes.addChild(n);
        }
        root.addChild("nodes", nodes);

        JsonValue links = new JsonValue(JsonValue.ValueType.array);
        for (GraphLink link : graph.getLinks()) {
            JsonValue l = new JsonValue(JsonValue.ValueType.object);
            l.addChild("from", new JsonValue(link.fromNode()));
            l.addChild("fromPin", new JsonValue(link.fromPin()));
            l.addChild("to", new JsonValue(link.toNode()));
            l.addChild("toPin", new JsonValue(link.toPin()));
            links.addChild(l);
        }
        root.addChild("links", links);
        return root.prettyPrint(JsonWriter.OutputType.json, PRETTY_PRINT_LINE_WIDTH);
    }

    /** @throws SceneSerializationException when the text is not a graph document */
    public static Graph fromJson(String text) {
        if (text == null || text.isBlank()) {
            throw new SceneSerializationException("Graph file is empty");
        }
        JsonValue root;
        try {
            root = new JsonReader().parse(text);
        } catch (Exception e) {
            throw new SceneSerializationException("Malformed graph JSON: " + e.getMessage(), e);
        }
        if (root == null || !root.isObject()) {
            throw new SceneSerializationException("Graph root element must be an object");
        }
        int version = root.getInt("schemaVersion", -1);
        if (version < 1 || version > SCHEMA_VERSION) {
            throw new SceneSerializationException("Unsupported graph schemaVersion: " + version);
        }
        if (!KIND.equals(root.getString("kind", null))) {
            throw new SceneSerializationException("Not a graph document (kind is " + root.getString("kind", null) + ")");
        }
        try {
            Graph graph = new Graph(root.getString("name", "Graph"));
            for (JsonValue v = child(root, "variables"); v != null; v = v.next) {
                ValueType type = ValueType.valueOf(v.getString("type"));
                graph.putVariable(new GraphVariable(v.getString("name"), type, readValue(type, v.get("value"))));
            }
            for (JsonValue n = child(root, "nodes"); n != null; n = n.next) {
                GraphNode node = new GraphNode(n.getString("id"), n.getString("type"), n.getFloat("x", 0f), n.getFloat("y", 0f));
                for (JsonValue l = child(n, "literals"); l != null; l = l.next) {
                    ValueType type = ValueType.valueOf(l.getString("type"));
                    node.setLiteral(l.getString("pin"), readValue(type, l.get("value")));
                }
                for (JsonValue p = child(n, "properties"); p != null; p = p.next) {
                    ValueType type = ValueType.valueOf(p.getString("type"));
                    node.setProperty(p.getString("name"), readValue(type, p.get("value")));
                }
                graph.addNode(node);
            }
            for (JsonValue l = child(root, "links"); l != null; l = l.next) {
                graph.addLink(new GraphLink(l.getString("from"), l.getString("fromPin"), l.getString("to"), l.getString("toPin")));
            }
            return graph;
        } catch (IllegalArgumentException e) {
            // Missing fields, unknown types, duplicate ids, bad values
            throw new SceneSerializationException("Invalid graph: " + e.getMessage(), e);
        }
    }

    private static JsonValue child(JsonValue parent, String name) {
        JsonValue array = parent.get(name);
        return array != null && array.isArray() ? array.child : null;
    }

    private static JsonValue writeTypedValues(Map<String, Object> values, String keyName) {
        JsonValue array = new JsonValue(JsonValue.ValueType.array);
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            ValueType type = typeOf(entry.getValue());
            JsonValue item = new JsonValue(JsonValue.ValueType.object);
            item.addChild(keyName, new JsonValue(entry.getKey()));
            item.addChild("type", new JsonValue(type.name()));
            item.addChild("value", writeValue(type, entry.getValue()));
            array.addChild(item);
        }
        return array;
    }

    private static ValueType typeOf(Object value) {
        for (ValueType type : ValueType.values()) {
            if (type.hasLiteral() && type.accepts(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Cannot store value " + value + " in a graph file");
    }

    private static JsonValue writeValue(ValueType type, Object value) {
        return switch (type) {
            case FLOAT -> new JsonValue((Float) value);
            case BOOLEAN -> new JsonValue((Boolean) value);
            case STRING -> new JsonValue((String) value);
            case VECTOR3 -> {
                Vector3 v = (Vector3) value;
                JsonValue object = new JsonValue(JsonValue.ValueType.object);
                object.addChild("x", new JsonValue(v.x));
                object.addChild("y", new JsonValue(v.y));
                object.addChild("z", new JsonValue(v.z));
                yield object;
            }
            case ENTITY -> throw new IllegalArgumentException("Entities cannot be stored in a graph file");
        };
    }

    private static Object readValue(ValueType type, JsonValue value) {
        if (value == null) {
            throw new IllegalArgumentException("Missing value of type " + type);
        }
        return switch (type) {
            case FLOAT -> value.asFloat();
            case BOOLEAN -> value.asBoolean();
            case STRING -> value.asString();
            case VECTOR3 -> new Vector3(value.getFloat("x", 0f), value.getFloat("y", 0f), value.getFloat("z", 0f));
            case ENTITY -> throw new IllegalArgumentException("Entities cannot be stored in a graph file");
        };
    }
}
