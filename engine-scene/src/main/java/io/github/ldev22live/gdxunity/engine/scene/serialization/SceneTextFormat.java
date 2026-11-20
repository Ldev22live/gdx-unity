package io.github.ldev22live.gdxunity.engine.scene.serialization;

import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The text layout of scene files, chosen so that version control shows a small edit as a small diff (roadmap M9.3, TD-093).
 *
 * <p>Input is compact JSON, as {@link #newWriter(Writer)} writes it; output is the same document laid out by fixed
 * rules, so the same scene always gives the same bytes:
 * <ul>
 *   <li>two spaces per level, every member of an object on its own line, {@code "key": value};</li>
 *   <li>numbers, strings and literals are copied exactly as written (a float keeps its {@code 1.0}; nothing is re-parsed or
 *       rounded);</li>
 *   <li>an array of plain values is written {@value #VALUES_PER_LINE} values to a line, so changing one value (a tile, a height)
 *       changes one line; an array of objects or arrays puts each element on its own lines;</li>
 *   <li>empty objects and arrays are {@code {}} and {@code []};</li>
 *   <li>lines end with {@code \n}, and the text ends with one.</li>
 * </ul>
 * Member order is the order the writer gave, which for scenes is fixed by the serializers.
 */
public final class SceneTextFormat {
    /** How many plain values (numbers, strings, booleans, nulls) an array puts on one line. */
    public static final int VALUES_PER_LINE = 16;
    /** The indentation of one level. */
    static final String INDENT = "  ";

    private SceneTextFormat() {
    }

    /**
     * Lays out a JSON document by the rules above.
     *
     * @param json one JSON value (usually compact, but any valid JSON is accepted)
     * @return the laid-out text, ending with a line break
     * @throws IllegalArgumentException naming the position when the text is not one complete JSON value
     */
    public static String format(String json) {
        Objects.requireNonNull(json, "json cannot be null");
        Parser parser = new Parser(json);
        Node root = parser.value();
        parser.skipSpace();
        if (parser.pos != json.length()) {
            throw parser.error("text after the end of the document");
        }
        StringBuilder out = new StringBuilder(json.length() * 2);
        write(root, 0, out);
        out.append('\n');
        return out.toString();
    }

    /**
     * Lays out a parsed JSON tree by the rules above. Numbers are written with the text they were read with
     * ({@link JsonValue#asString()}), so a tree read from a file and written back keeps {@code 1.0} as {@code 1.0}; numbers made
     * in code have Java's own text.
     *
     * @param root the tree; its own name, if any, is ignored
     * @return the laid-out text, ending with a line break
     */
    public static String format(JsonValue root) {
        Objects.requireNonNull(root, "root cannot be null");
        StringBuilder compact = new StringBuilder();
        writeCompact(root, compact);
        return format(compact.toString());
    }

    private static void writeCompact(JsonValue value, StringBuilder out) {
        switch (value.type()) {
            case object, array -> {
                boolean object = value.isObject();
                out.append(object ? '{' : '[');
                for (JsonValue child = value.child; child != null; child = child.next) {
                    if (child != value.child) {
                        out.append(',');
                    }
                    if (object) {
                        out.append(JsonWriter.OutputType.json.quoteName(child.name)).append(':');
                    }
                    writeCompact(child, out);
                }
                out.append(object ? '}' : ']');
            }
            case stringValue -> out.append(JsonWriter.OutputType.json.quoteValue(value.asString()));
            case doubleValue, longValue, booleanValue -> out.append(value.asString());
            case nullValue -> out.append("null");
        }
    }

    /**
     * A compact JSON writer for {@link #format}'s input that writes floats and doubles with Java's own shortest text
     * ({@code 1.0}, {@code 0.96}, {@code 1.0E-5}). LibGDX's writer drops the {@code .0} of a whole float, so a value saved as
     * {@code 1.0} would otherwise come back as {@code 1} and every whole number in a file written by hand or by another tool
     * would change on the first save.
     *
     * @param out where the compact JSON goes
     * @return a writer set to plain JSON output
     */
    public static JsonWriter newWriter(Writer out) {
        Objects.requireNonNull(out, "out cannot be null");
        JsonWriter writer = new ExactNumberWriter(out);
        writer.setOutputType(JsonWriter.OutputType.json);
        return writer;
    }

    /** See {@link #newWriter(Writer)}. */
    private static final class ExactNumberWriter extends JsonWriter {
        ExactNumberWriter(Writer out) {
            super(out);
        }

        @Override
        public JsonWriter value(Object value) throws IOException {
            if (value instanceof Float || value instanceof Double) {
                return json(value.toString());
            }
            return super.value(value);
        }

        @Override
        public JsonWriter value(float value) throws IOException {
            return json(Float.toString(value));
        }

        @Override
        public JsonWriter value(double value) throws IOException {
            return json(Double.toString(value));
        }
    }

    private static void write(Node node, int depth, StringBuilder out) {
        if (node.scalar != null) {
            out.append(node.scalar);
            return;
        }
        boolean object = node.keys != null;
        char open = object ? '{' : '[';
        char close = object ? '}' : ']';
        if (node.children.isEmpty()) {
            out.append(open).append(close);
            return;
        }
        if (!object && node.allScalars()) {
            writeValues(node.children, depth, out);
            return;
        }
        out.append(open).append('\n');
        for (int i = 0; i < node.children.size(); i++) {
            indent(depth + 1, out);
            if (object) {
                out.append(node.keys.get(i)).append(": ");
            }
            write(node.children.get(i), depth + 1, out);
            if (i < node.children.size() - 1) {
                out.append(',');
            }
            out.append('\n');
        }
        indent(depth, out);
        out.append(close);
    }

    /** An array of plain values: one line when it fits, else {@link #VALUES_PER_LINE} to a line between brackets on their own lines. */
    private static void writeValues(List<Node> values, int depth, StringBuilder out) {
        if (values.size() <= VALUES_PER_LINE) {
            out.append('[');
            for (int i = 0; i < values.size(); i++) {
                out.append(i == 0 ? "" : ", ").append(values.get(i).scalar);
            }
            out.append(']');
            return;
        }
        out.append("[\n");
        for (int i = 0; i < values.size(); i++) {
            boolean lineStart = i % VALUES_PER_LINE == 0;
            if (lineStart) {
                indent(depth + 1, out);
            } else {
                out.append(' ');
            }
            out.append(values.get(i).scalar);
            if (i < values.size() - 1) {
                out.append(',');
            }
            if ((i + 1) % VALUES_PER_LINE == 0 || i == values.size() - 1) {
                out.append('\n');
            }
        }
        indent(depth, out);
        out.append(']');
    }

    private static void indent(int depth, StringBuilder out) {
        for (int i = 0; i < depth; i++) {
            out.append(INDENT);
        }
    }

    /** A parsed value: a scalar's exact text, or an object's keys (exact quoted text) and values, or an array's values. */
    private static final class Node {
        final String scalar;
        final List<String> keys;
        final List<Node> children;

        Node(String scalar) {
            this.scalar = scalar;
            this.keys = null;
            this.children = null;
        }

        Node(boolean object) {
            this.scalar = null;
            this.keys = object ? new ArrayList<>() : null;
            this.children = new ArrayList<>();
        }

        boolean allScalars() {
            for (Node child : children) {
                if (child.scalar == null) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * A JSON number, {@code true}, {@code false} or {@code null}; also {@code NaN} and the infinities, which LibGDX's writer puts out
     * for such floats, so a scene holding one still saves (and the value is kept for the reader to judge).
     */
    private static final Pattern LITERAL =
        Pattern.compile("-?(0|[1-9][0-9]*)([.][0-9]+)?([eE][+-]?[0-9]+)?|true|false|null|NaN|-?Infinity");

    /** A JSON reader that keeps every value's text exactly as written. */
    private static final class Parser {
        final String text;
        int pos;

        Parser(String text) {
            this.text = text;
        }

        Node value() {
            skipSpace();
            if (pos >= text.length()) {
                throw error("a value was expected");
            }
            char c = text.charAt(pos);
            if (c == '{') {
                return object();
            }
            if (c == '[') {
                return array();
            }
            if (c == '"') {
                return new Node(string());
            }
            return new Node(literal());
        }

        Node object() {
            Node node = new Node(true);
            pos++;
            skipSpace();
            if (peek() == '}') {
                pos++;
                return node;
            }
            while (true) {
                skipSpace();
                if (peek() != '"') {
                    throw error("a quoted key was expected");
                }
                node.keys.add(string());
                skipSpace();
                expect(':');
                node.children.add(value());
                skipSpace();
                char c = peek();
                pos++;
                if (c == '}') {
                    return node;
                }
                if (c != ',') {
                    throw error("',' or '}' was expected");
                }
            }
        }

        Node array() {
            Node node = new Node(false);
            pos++;
            skipSpace();
            if (peek() == ']') {
                pos++;
                return node;
            }
            while (true) {
                node.children.add(value());
                skipSpace();
                char c = peek();
                pos++;
                if (c == ']') {
                    return node;
                }
                if (c != ',') {
                    throw error("',' or ']' was expected");
                }
            }
        }

        String string() {
            int start = pos;
            pos++;
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (c == '\\') {
                    pos += 2;
                } else if (c == '"') {
                    pos++;
                    return text.substring(start, pos);
                } else if (c < 0x20) {
                    throw error("a control character inside a string");
                } else {
                    pos++;
                }
            }
            throw error("an unterminated string");
        }

        String literal() {
            int start = pos;
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (c == ',' || c == '}' || c == ']' || c == ':' || Character.isWhitespace(c)) {
                    break;
                }
                pos++;
            }
            String literal = text.substring(start, pos);
            if (!LITERAL.matcher(literal).matches()) {
                pos = start;
                throw error("a number, true, false or null was expected, not '" + literal + "'");
            }
            return literal;
        }

        void expect(char c) {
            if (peek() != c) {
                throw error("'" + c + "' was expected");
            }
            pos++;
        }

        char peek() {
            if (pos >= text.length()) {
                throw error("the text ended early");
            }
            return text.charAt(pos);
        }

        void skipSpace() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
        }

        IllegalArgumentException error(String what) {
            return new IllegalArgumentException("Not JSON at character " + pos + ": " + what);
        }
    }
}
