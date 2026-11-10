package io.github.ldev22live.gdxunity.engine.visualscript;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The table of method nodes the web player runs too (TD-065, part A). The test builds {@code javaMethodNodes.json} from the real nodes
 * (pins, kinds) and from real calls of the methods with fixed arguments (the results the web side must reproduce), and compares it with the
 * file in the web project, so the web table cannot drift from the desktop's nodes. Run with the environment variable
 * {@code GDXUNITY_WRITE_SAMPLES=1} to write the file again after changing the list.
 */
class WebMethodNodesTest {
    private static final NodeRegistry REGISTRY = NodeRegistry.createDefault();
    private static final Path FILE = Path.of("..", "engine-babylon", "src", "visualscript", "javaMethodNodes.json");
    private static final float[] FLOATS = {0.5f, -2f, 7.25f};
    private static final int[] INTS = {3, -4, 10};
    private static final float[][] VECTORS = {{1f, 2f, 3f}, {-4f, 0.5f, 2f}, {0.25f, -1f, 8f}};
    /** How many sets of arguments are tried per node. */
    private static final int CASES = 3;
    private static final String QUOTE = "\"";

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    private static String number(float value) {
        String text = Float.toString(value);
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }

    private static String vector(Vector3 v) {
        return "{\"x\":" + number(v.x) + ",\"y\":" + number(v.y) + ",\"z\":" + number(v.z) + "}";
    }

    /** The JSON of one value of a pin of type {@code type}. */
    private static String json(ValueType type, Object value) {
        return switch (type) {
            case FLOAT -> number(((Number) value).floatValue());
            case INTEGER -> String.valueOf(((Number) value).intValue());
            case BOOLEAN -> String.valueOf(value);
            case VECTOR3 -> vector((Vector3) value);
            default -> throw new IllegalArgumentException("No JSON for " + type);
        };
    }

    private static Object argumentFor(ValueType type, int caseNumber, int pinNumber) {
        int pick = (caseNumber + pinNumber) % CASES;
        return switch (type) {
            case FLOAT -> FLOATS[pick];
            case INTEGER -> INTS[pick];
            case VECTOR3 -> new Vector3(VECTORS[pick][0], VECTORS[pick][1], VECTORS[pick][2]);
            default -> throw new IllegalArgumentException("No argument pool for " + type);
        };
    }

    private static Object convert(Class<?> parameter, Object value) {
        if (parameter == float.class) {
            return ((Number) value).floatValue();
        }
        if (parameter == int.class) {
            return ((Number) value).intValue();
        }
        return value instanceof Vector3 v ? new Vector3(v) : value;
    }

    private static boolean random(String id) {
        return id.contains("#random");
    }

    private static String pinJson(PinDef pin) {
        return "{\"id\":\"" + pin.id() + "\",\"label\":\"" + pin.label() + "\",\"exec\":" + pin.isExec() + ",\"input\":" + pin.isInput()
            + (pin.isExec() ? "" : ",\"type\":\"" + pin.type() + QUOTE) + "}";
    }

    /** The cases of one node: the same method called with fixed arguments, on a copy of the target. */
    private static String casesOf(Executable member, List<PinDef> pins) throws ReflectiveOperationException {
        List<String> cases = new ArrayList<>();
        List<PinDef> inputs = pins.stream().filter(p -> !p.isExec() && p.isInput()).toList();
        PinDef output = pins.stream().filter(p -> !p.isExec() && !p.isInput()).findFirst().orElse(null);
        for (int c = 0; c < CASES; c++) {
            List<String> in = new ArrayList<>();
            List<Object> arguments = new ArrayList<>();
            Object target = null;
            for (int p = 0; p < inputs.size(); p++) {
                PinDef pin = inputs.get(p);
                Object value = argumentFor(pin.type(), c, p);
                in.add("\"" + pin.id() + "\":" + json(pin.type(), value));
                if (pin.id().equals(JavaMethodNodes.PIN_TARGET)) {
                    target = value;
                } else {
                    arguments.add(value);
                }
            }
            Class<?>[] parameters = member.getParameterTypes();
            Object[] converted = new Object[parameters.length];
            for (int i = 0; i < parameters.length; i++) {
                converted[i] = convert(parameters[i], arguments.get(i));
            }
            Object result;
            if (member instanceof Constructor<?> constructor) {
                result = constructor.newInstance(converted);
            } else {
                Method method = (Method) member;
                result = method.invoke(Modifier.isStatic(method.getModifiers()) ? null : new Vector3((Vector3) target), converted);
            }
            if (result instanceof Float value && !Float.isFinite(value)) {
                continue; // NaN and infinity are not JSON; the case is left out
            }
            cases.add("{\"in\":{" + String.join(",", in) + "},\"out\":" + (output == null ? "null" : json(output.type(), result)) + "}");
        }
        return "[" + String.join(",", cases) + "]";
    }

    private static String build() throws ReflectiveOperationException {
        List<String> nodes = new ArrayList<>();
        for (String id : WebMethodNodes.IDS) {
            NodeType type = REGISTRY.get(id);
            assertNotNull(type, "no node " + id);
            List<PinDef> pins = type.pinsFor(new GraphNode("n", id, 0f, 0f), new Graph("g"));
            List<String> pinJson = pins.stream().map(WebMethodNodesTest::pinJson).toList();
            String cases = random(id) ? "[]" : casesOf((Executable) type.getSource(), pins);
            nodes.add("    {\"id\":\"" + id + "\",\"title\":\"" + type.getTitle() + "\",\"category\":\"" + type.getCategory()
                + "\",\"kind\":\"" + type.getKind().name().toLowerCase(Locale.ROOT) + "\",\"twin\":" + (REGISTRY.get(id + JavaMethodNodes.ACTION_SUFFIX) != null)
                + ",\"pins\":[" + String.join(",", pinJson) + "],\"cases\":" + cases + "}");
        }
        return "{\n  \"schemaVersion\": 1,\n  \"nodes\": [\n" + String.join(",\n", nodes) + "\n  ]\n}\n";
    }

    @Test
    void shouldMatchTheTableTheWebPlayerReads() throws Exception {
        String built = build();
        if ("1".equals(System.getenv("GDXUNITY_WRITE_SAMPLES"))) {
            Files.writeString(FILE, built, StandardCharsets.UTF_8);
        }

        assertTrue(Files.isRegularFile(FILE), "missing " + FILE + "; run with GDXUNITY_WRITE_SAMPLES=1");
        assertEquals(built.replace("\r\n", "\n"), Files.readString(FILE, StandardCharsets.UTF_8).replace("\r\n", "\n"),
            "javaMethodNodes.json is out of date");
    }

    @Test
    void shouldKnowWhichMethodNodesHaveAWebVersionIncludingTheirActionTwins() {
        String clamp = WebMethodNodes.IDS.get(0);

        assertTrue(WebMethodNodes.hasWebVersion(clamp));
        assertTrue(WebMethodNodes.hasWebVersion(clamp + JavaMethodNodes.ACTION_SUFFIX));
        assertFalse(WebMethodNodes.hasWebVersion("java:io.github.ldev22live.gdxunity.engine.scene.Entity#getName()"));
        assertFalse(WebMethodNodes.hasWebVersion(null));
    }
}
