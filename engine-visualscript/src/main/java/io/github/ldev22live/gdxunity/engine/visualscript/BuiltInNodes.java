package io.github.ldev22live.gdxunity.engine.visualscript;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.Transform;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsLayers3D;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.PhysicsSystem3D;

import java.util.List;
import java.util.Locale;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;

import static io.github.ldev22live.gdxunity.engine.visualscript.NodeType.Kind.ACTION;
import static io.github.ldev22live.gdxunity.engine.visualscript.NodeType.Kind.EVENT;
import static io.github.ldev22live.gdxunity.engine.visualscript.NodeType.Kind.PURE;
import static io.github.ldev22live.gdxunity.engine.visualscript.PinDef.in;
import static io.github.ldev22live.gdxunity.engine.visualscript.PinDef.out;
import static io.github.ldev22live.gdxunity.engine.visualscript.ValueType.BOOLEAN;
import static io.github.ldev22live.gdxunity.engine.visualscript.ValueType.ENTITY;
import static io.github.ldev22live.gdxunity.engine.visualscript.ValueType.FLOAT;
import static io.github.ldev22live.gdxunity.engine.visualscript.ValueType.STRING;
import static io.github.ldev22live.gdxunity.engine.visualscript.ValueType.VECTOR3;

/**
 * The node set shipped with the engine. Positions are world positions; rotations are yaw, pitch, roll in degrees.
 * An unconnected Target pin means the graph's own entity.
 */
public final class BuiltInNodes {
    public static final String EVENT_BEGIN_PLAY = "event.beginPlay";
    public static final String EVENT_TICK = "event.tick";
    public static final String EVENT_COLLISION = "event.collision";
    public static final String EVENT_TRIGGER = "event.trigger";
    public static final String EVENT_CUSTOM = "event.custom";
    public static final String FLOW_BRANCH = "flow.branch";
    public static final String FLOW_SEQUENCE = "flow.sequence";
    public static final String FLOW_DELAY = "flow.delay";
    public static final String ACTION_PRINT = "action.print";
    public static final String ACTION_SET_POSITION = "action.setPosition";
    public static final String ACTION_MOVE_BY = "action.moveBy";
    public static final String ACTION_ROTATE_BY = "action.rotateBy";
    public static final String ACTION_DESTROY = "action.destroy";
    /** 3D physics nodes (TD-048); they need a scene with 3D colliders, and the web player does not run them. */
    public static final String ACTION_ADD_FORCE = "action.addForce";
    public static final String ACTION_ADD_IMPULSE = "action.addImpulse";
    public static final String ACTION_SET_VELOCITY = "action.setVelocity";
    public static final String ACTION_RAYCAST_3D = "action.raycast3d";
    public static final String DATA_GET_VELOCITY = "data.getVelocity";
    /** Walking character nodes (TD-049); they need a Character Controller 3D, and the web player does not run them. */
    public static final String ACTION_MOVE_CHARACTER = "action.moveCharacter";
    public static final String ACTION_JUMP_CHARACTER = "action.jumpCharacter";
    public static final String DATA_IS_GROUNDED = "data.isGrounded";
    public static final String VARIABLE_GET = "variable.get";
    public static final String VARIABLE_SET = "variable.set";
    public static final String DATA_SELF = "data.self";
    public static final String DATA_GET_POSITION = "data.getPosition";
    public static final String DATA_GET_NAME = "data.getName";
    /** Desktop-only helper nodes that read the keyboard (TD-060, milestone C). */
    public static final String INPUT_KEY_DOWN = "input.keyDown";
    public static final String INPUT_AXIS = "input.axis";
    public static final String PROPERTY_KEY = "key";
    public static final String PROPERTY_NEGATIVE = "negative";
    public static final String PROPERTY_POSITIVE = "positive";
    public static final String MATH_SIN = "math.sin";
    public static final String MATH_COS = "math.cos";
    public static final String MATH_ADD = "math.add";
    public static final String MATH_SUBTRACT = "math.subtract";
    public static final String MATH_MULTIPLY = "math.multiply";
    public static final String MATH_DIVIDE = "math.divide";
    public static final String MATH_GREATER = "math.greater";
    public static final String MATH_LESS = "math.less";
    public static final String MATH_EQUAL = "math.equal";
    public static final String LOGIC_AND = "logic.and";
    public static final String LOGIC_OR = "logic.or";
    public static final String LOGIC_NOT = "logic.not";
    public static final String VECTOR_MAKE = "vector.make";
    public static final String VECTOR_BREAK = "vector.break";
    public static final String VECTOR_ADD = "vector.add";
    public static final String VECTOR_SCALE = "vector.scale";
    public static final String TEXT_APPEND = "text.append";
    public static final String TEXT_FROM_FLOAT = "text.fromFloat";

    /** Event payload / output pin ids. */
    public static final String PIN_DELTA_TIME = "deltaTime";
    public static final String PIN_OTHER = "other";
    /** Output pin ids of the Raycast 3D node; its execution outputs are {@link #PIN_HIT} and {@link #PIN_MISS}. */
    public static final String PIN_HIT = "hit";
    public static final String PIN_MISS = "miss";
    public static final String PIN_HIT_ENTITY = "entity";
    public static final String PIN_HIT_POINT = "point";
    public static final String PIN_HIT_NORMAL = "normal";
    public static final String PIN_HIT_DISTANCE = "hitDistance";
    /** Property naming a custom event or a variable. */
    public static final String PROPERTY_NAME = "name";
    public static final String PROPERTY_VARIABLE = "variable";
    /** Number of outputs on a Sequence node. */
    public static final int SEQUENCE_OUTPUTS = 3;

    private BuiltInNodes() {}

    public static void registerAll(NodeRegistry registry) {
        // Events
        registry.register(NodeType.builder(EVENT_BEGIN_PLAY, "Event Begin Play", "Events", EVENT).build());
        registry.register(NodeType.builder(EVENT_TICK, "Event Tick", "Events", EVENT)
            .pin(out(PIN_DELTA_TIME, "Delta Seconds", FLOAT)).build());
        registry.register(NodeType.builder(EVENT_COLLISION, "Event Collision", "Events", EVENT)
            .pin(out(PIN_OTHER, "Other", ENTITY)).build());
        registry.register(NodeType.builder(EVENT_TRIGGER, "Event Trigger Enter", "Events", EVENT)
            .pin(out(PIN_OTHER, "Other", ENTITY)).build());
        registry.register(NodeType.builder(EVENT_CUSTOM, "Custom Event", "Events", EVENT)
            .property(PROPERTY_NAME, STRING).build());

        // Flow control
        registry.register(NodeType.builder(FLOW_BRANCH, "Branch", "Flow", ACTION)
            .execOutputs(PinDef.execOut("true", "True"), PinDef.execOut("false", "False"))
            .pin(in("condition", "Condition", BOOLEAN))
            .behaviour(c -> c.continueWith(c.inputBoolean("condition") ? "true" : "false")).build());
        PinDef[] sequenceOutputs = new PinDef[SEQUENCE_OUTPUTS];
        for (int i = 0; i < SEQUENCE_OUTPUTS; i++) {
            sequenceOutputs[i] = PinDef.execOut("then" + i, "Then " + i);
        }
        registry.register(NodeType.builder(FLOW_SEQUENCE, "Sequence", "Flow", ACTION)
            .execOutputs(sequenceOutputs)
            .behaviour(c -> {
                for (int i = 0; i < SEQUENCE_OUTPUTS; i++) {
                    c.continueWith("then" + i);
                }
            }).build());
        registry.register(NodeType.builder(FLOW_DELAY, "Delay", "Flow", ACTION)
            .execOutputs(PinDef.execOut("completed", "Completed"))
            .pin(in("seconds", "Seconds", FLOAT))
            .behaviour(c -> {
                float seconds = c.inputFloat("seconds");
                if (!(seconds >= 0f) || Float.isInfinite(seconds)) {
                    throw c.error("Delay needs zero or more seconds, got " + seconds);
                }
                c.continueAfter(seconds, "completed");
            }).build());

        // Actions
        registry.register(NodeType.builder(ACTION_PRINT, "Print", "Actions", ACTION)
            .pin(in("message", "Message", STRING))
            .behaviour(c -> {
                c.log(c.inputString("message"));
                c.continueWith(PinDef.EXEC_OUT);
            }).build());
        registry.register(NodeType.builder(ACTION_SET_POSITION, "Set Position", "Actions", ACTION)
            .pin(in("target", "Target", ENTITY)).pin(in("position", "Position", VECTOR3))
            .behaviour(c -> {
                setWorldPosition(target(c).getTransform(), c.inputVector("position"));
                c.continueWith(PinDef.EXEC_OUT);
            }).build());
        registry.register(NodeType.builder(ACTION_MOVE_BY, "Move By", "Actions", ACTION)
            .pin(in("target", "Target", ENTITY)).pin(in("offset", "Offset", VECTOR3))
            .behaviour(c -> {
                Transform transform = target(c).getTransform();
                setWorldPosition(transform, transform.getWorldPosition().add(c.inputVector("offset")));
                c.continueWith(PinDef.EXEC_OUT);
            }).build());
        registry.register(NodeType.builder(ACTION_ROTATE_BY, "Rotate By", "Actions", ACTION)
            .pin(in("target", "Target", ENTITY)).pin(in("degrees", "Yaw Pitch Roll", VECTOR3))
            .behaviour(c -> {
                Vector3 degrees = c.inputVector("degrees");
                target(c).getTransform().rotate(degrees.x, degrees.y, degrees.z);
                c.continueWith(PinDef.EXEC_OUT);
            }).build());
        registry.register(NodeType.builder(ACTION_DESTROY, "Destroy Entity", "Actions", ACTION)
            .pin(in("target", "Target", ENTITY))
            .behaviour(c -> {
                Entity target = target(c);
                Scene scene = target.getScene();
                if (scene != null) {
                    // Scene defers removal while it is updating, so this is safe mid-frame
                    scene.removeEntity(target);
                }
                c.continueWith(PinDef.EXEC_OUT);
            }).build());

        // 3D physics (TD-048): positions, forces and velocities are in scene units, like the other nodes
        registry.register(NodeType.builder(ACTION_ADD_FORCE, "Add Force", "Physics 3D", ACTION)
            .pin(in("target", "Target", ENTITY)).pin(in("force", "Force", VECTOR3))
            .behaviour(c -> {
                Vector3 force = c.inputVector("force");
                Entity target = target(c);
                physics(c, p -> p.addForce(target, force));
                c.continueWith(PinDef.EXEC_OUT);
            }).build());
        registry.register(NodeType.builder(ACTION_ADD_IMPULSE, "Add Impulse", "Physics 3D", ACTION)
            .pin(in("target", "Target", ENTITY)).pin(in("impulse", "Impulse", VECTOR3))
            .behaviour(c -> {
                Vector3 impulse = c.inputVector("impulse");
                Entity target = target(c);
                physics(c, p -> p.addImpulse(target, impulse));
                c.continueWith(PinDef.EXEC_OUT);
            }).build());
        registry.register(NodeType.builder(ACTION_SET_VELOCITY, "Set Velocity", "Physics 3D", ACTION)
            .pin(in("target", "Target", ENTITY)).pin(in("velocity", "Velocity", VECTOR3))
            .behaviour(c -> {
                Vector3 velocity = c.inputVector("velocity");
                Entity target = target(c);
                physics(c, p -> p.setLinearVelocity(target, velocity));
                c.continueWith(PinDef.EXEC_OUT);
            }).build());
        registry.register(NodeType.builder(DATA_GET_VELOCITY, "Get Velocity", "Physics 3D", PURE)
            .pin(in("target", "Target", ENTITY)).pin(out("velocity", "Velocity", VECTOR3))
            .behaviour(c -> {
                Entity target = target(c);
                Vector3[] velocity = new Vector3[1];
                physics(c, p -> velocity[0] = p.getLinearVelocity(target));
                c.output("velocity", velocity[0]);
            }).build());
        registry.register(NodeType.builder(ACTION_RAYCAST_3D, "Raycast 3D", "Physics 3D", ACTION)
            .execOutputs(PinDef.execOut(PIN_HIT, "Hit"), PinDef.execOut(PIN_MISS, "Miss"))
            .pin(in("origin", "Origin", VECTOR3)).pin(in("direction", "Direction", VECTOR3)).pin(in("distance", "Distance", FLOAT))
            .pin(out(PIN_HIT_ENTITY, "Entity", ENTITY)).pin(out(PIN_HIT_POINT, "Point", VECTOR3))
            .pin(out(PIN_HIT_NORMAL, "Normal", VECTOR3)).pin(out(PIN_HIT_DISTANCE, "Hit Distance", FLOAT))
            .behaviour(c -> {
                Vector3 origin = c.inputVector("origin");
                Vector3 direction = c.inputVector("direction");
                float distance = c.inputFloat("distance");
                PhysicsSystem3D.RaycastHit[] hit = new PhysicsSystem3D.RaycastHit[1];
                physics(c, p -> hit[0] = p.raycast(origin, direction, distance, PhysicsLayers3D.ALL_LAYERS).orElse(null));
                if (hit[0] == null) {
                    c.continueWith(PIN_MISS);
                    return;
                }
                c.output(PIN_HIT_ENTITY, hit[0].entity());
                c.output(PIN_HIT_POINT, hit[0].point());
                c.output(PIN_HIT_NORMAL, hit[0].normal());
                c.output(PIN_HIT_DISTANCE, hit[0].distance());
                c.continueWith(PIN_HIT);
            }).build());

        // Walking characters (TD-049): speeds are scene units per second
        registry.register(NodeType.builder(ACTION_MOVE_CHARACTER, "Move Character", "Physics 3D", ACTION)
            .pin(in("target", "Target", ENTITY)).pin(in("velocity", "Velocity", VECTOR3))
            .behaviour(c -> {
                Vector3 velocity = c.inputVector("velocity");
                Entity target = target(c);
                physics(c, p -> p.moveCharacter(target, velocity));
                c.continueWith(PinDef.EXEC_OUT);
            }).build());
        registry.register(NodeType.builder(ACTION_JUMP_CHARACTER, "Jump Character", "Physics 3D", ACTION)
            .pin(in("target", "Target", ENTITY)).pin(in("speed", "Speed", FLOAT))
            .behaviour(c -> {
                float speed = c.inputFloat("speed");
                Entity target = target(c);
                // Nothing happens in the air: check Is Grounded first to react to a jump that did not happen
                physics(c, p -> p.jumpCharacter(target, speed));
                c.continueWith(PinDef.EXEC_OUT);
            }).build());
        registry.register(NodeType.builder(DATA_IS_GROUNDED, "Is Grounded", "Physics 3D", PURE)
            .pin(in("target", "Target", ENTITY)).pin(out("grounded", "Grounded", BOOLEAN))
            .behaviour(c -> {
                Entity target = target(c);
                boolean[] grounded = new boolean[1];
                physics(c, p -> grounded[0] = p.isCharacterGrounded(target));
                c.output("grounded", grounded[0]);
            }).build());

        // Keyboard (TD-060 C): key names are libGDX's (A, LEFT, SPACE, SHIFT_LEFT, ...); several keys are separated by commas
        registry.register(NodeType.builder(INPUT_KEY_DOWN, "Key Down", "Input", PURE)
            .property(PROPERTY_KEY, STRING).pin(out("pressed", "Pressed", BOOLEAN))
            .behaviour(c -> c.output("pressed", anyKeyDown(c, (String) c.property(PROPERTY_KEY)))).build());
        registry.register(NodeType.builder(INPUT_AXIS, "Input Axis", "Input", PURE)
            .property(PROPERTY_NEGATIVE, STRING).property(PROPERTY_POSITIVE, STRING).pin(out("value", "Value", FLOAT))
            .behaviour(c -> c.output("value", (anyKeyDown(c, (String) c.property(PROPERTY_POSITIVE)) ? 1f : 0f)
                - (anyKeyDown(c, (String) c.property(PROPERTY_NEGATIVE)) ? 1f : 0f))).build());

        // Variables
        registry.register(NodeType.builder(VARIABLE_GET, "Get Variable", "Variables", PURE)
            .property(PROPERTY_VARIABLE, STRING)
            .dynamicPins((node, graph) -> {
                GraphVariable variable = graph.getVariable((String) node.getProperty(PROPERTY_VARIABLE));
                return variable == null ? List.of() : List.of(out("value", variable.name(), variable.type()));
            })
            .behaviour(c -> c.output("value", c.instance().getVariable(variableName(c)))).build());
        registry.register(NodeType.builder(VARIABLE_SET, "Set Variable", "Variables", ACTION)
            .property(PROPERTY_VARIABLE, STRING)
            .dynamicPins((node, graph) -> {
                GraphVariable variable = graph.getVariable((String) node.getProperty(PROPERTY_VARIABLE));
                return variable == null ? List.of(PinDef.execIn(), PinDef.execOut(PinDef.EXEC_OUT, ""))
                    : List.of(PinDef.execIn(), PinDef.execOut(PinDef.EXEC_OUT, ""),
                        in("value", variable.name(), variable.type()), out("result", "", variable.type()));
            })
            .behaviour(c -> {
                String name = variableName(c);
                c.instance().setVariable(name, c.input("value"));
                c.output("result", c.instance().getVariable(name));
                c.continueWith(PinDef.EXEC_OUT);
            }).build());

        // Entities
        registry.register(NodeType.builder(DATA_SELF, "Self", "Entities", PURE)
            .pin(out("entity", "Self", ENTITY))
            .behaviour(c -> c.output("entity", c.self())).build());
        registry.register(NodeType.builder(DATA_GET_POSITION, "Get Position", "Entities", PURE)
            .pin(in("target", "Target", ENTITY)).pin(out("position", "Position", VECTOR3))
            .behaviour(c -> c.output("position", target(c).getTransform().getWorldPosition())).build());
        registry.register(NodeType.builder(DATA_GET_NAME, "Get Name", "Entities", PURE)
            .pin(in("target", "Target", ENTITY)).pin(out("name", "Name", STRING))
            .behaviour(c -> c.output("name", target(c).getName())).build());

        // Math and logic
        registerFloatMath(registry, MATH_ADD, "Add", Float::sum);
        registerFloatMath(registry, MATH_SUBTRACT, "Subtract", (a, b) -> a - b);
        registerFloatMath(registry, MATH_MULTIPLY, "Multiply", (a, b) -> a * b);
        registry.register(NodeType.builder(MATH_DIVIDE, "Divide", "Math", PURE)
            .pin(in("a", "A", FLOAT)).pin(in("b", "B", FLOAT)).pin(out("result", "Result", FLOAT))
            .behaviour(c -> {
                float b = c.inputFloat("b");
                if (b == 0f) {
                    throw c.error("Division by zero");
                }
                c.output("result", c.inputFloat("a") / b);
            }).build());
        // Angles are in degrees, like Rotate By and the transforms; the heading of a car or a player turns into a direction with these
        registry.register(NodeType.builder(MATH_SIN, "Sine", "Math", PURE)
            .pin(in("degrees", "Degrees", FLOAT)).pin(out("result", "Result", FLOAT))
            .behaviour(c -> c.output("result", (float) Math.sin(Math.toRadians(c.inputFloat("degrees"))))).build());
        registry.register(NodeType.builder(MATH_COS, "Cosine", "Math", PURE)
            .pin(in("degrees", "Degrees", FLOAT)).pin(out("result", "Result", FLOAT))
            .behaviour(c -> c.output("result", (float) Math.cos(Math.toRadians(c.inputFloat("degrees"))))).build());
        registerComparison(registry, MATH_GREATER, "Greater Than", (a, b) -> a > b);
        registerComparison(registry, MATH_LESS, "Less Than", (a, b) -> a < b);
        registerComparison(registry, MATH_EQUAL, "Equal", (a, b) -> Float.compare(a, b) == 0);
        registry.register(NodeType.builder(LOGIC_AND, "And", "Logic", PURE)
            .pin(in("a", "A", BOOLEAN)).pin(in("b", "B", BOOLEAN)).pin(out("result", "Result", BOOLEAN))
            .behaviour(c -> c.output("result", c.inputBoolean("a") && c.inputBoolean("b"))).build());
        registry.register(NodeType.builder(LOGIC_OR, "Or", "Logic", PURE)
            .pin(in("a", "A", BOOLEAN)).pin(in("b", "B", BOOLEAN)).pin(out("result", "Result", BOOLEAN))
            .behaviour(c -> c.output("result", c.inputBoolean("a") || c.inputBoolean("b"))).build());
        registry.register(NodeType.builder(LOGIC_NOT, "Not", "Logic", PURE)
            .pin(in("value", "Value", BOOLEAN)).pin(out("result", "Result", BOOLEAN))
            .behaviour(c -> c.output("result", !c.inputBoolean("value"))).build());

        // Vectors
        registry.register(NodeType.builder(VECTOR_MAKE, "Make Vector", "Vectors", PURE)
            .pin(in("x", "X", FLOAT)).pin(in("y", "Y", FLOAT)).pin(in("z", "Z", FLOAT)).pin(out("vector", "Vector", VECTOR3))
            .behaviour(c -> c.output("vector", new Vector3(c.inputFloat("x"), c.inputFloat("y"), c.inputFloat("z")))).build());
        registry.register(NodeType.builder(VECTOR_BREAK, "Break Vector", "Vectors", PURE)
            .pin(in("vector", "Vector", VECTOR3)).pin(out("x", "X", FLOAT)).pin(out("y", "Y", FLOAT)).pin(out("z", "Z", FLOAT))
            .behaviour(c -> {
                Vector3 v = c.inputVector("vector");
                c.output("x", v.x);
                c.output("y", v.y);
                c.output("z", v.z);
            }).build());
        registry.register(NodeType.builder(VECTOR_ADD, "Add Vectors", "Vectors", PURE)
            .pin(in("a", "A", VECTOR3)).pin(in("b", "B", VECTOR3)).pin(out("result", "Result", VECTOR3))
            .behaviour(c -> c.output("result", c.inputVector("a").add(c.inputVector("b")))).build());
        registry.register(NodeType.builder(VECTOR_SCALE, "Scale Vector", "Vectors", PURE)
            .pin(in("vector", "Vector", VECTOR3)).pin(in("factor", "Factor", FLOAT)).pin(out("result", "Result", VECTOR3))
            .behaviour(c -> c.output("result", c.inputVector("vector").scl(c.inputFloat("factor")))).build());

        // Text
        registry.register(NodeType.builder(TEXT_APPEND, "Append Text", "Text", PURE)
            .pin(in("a", "A", STRING)).pin(in("b", "B", STRING)).pin(out("result", "Result", STRING))
            .behaviour(c -> c.output("result", c.inputString("a") + c.inputString("b"))).build());
        registry.register(NodeType.builder(TEXT_FROM_FLOAT, "Number to Text", "Text", PURE)
            .pin(in("value", "Value", FLOAT)).pin(out("text", "Text", STRING))
            .behaviour(c -> c.output("text", formatNumber(c.inputFloat("value")))).build());
    }

    private static void registerFloatMath(NodeRegistry registry, String id, String title, BinaryOperator<Float> op) {
        registry.register(NodeType.builder(id, title, "Math", PURE)
            .pin(in("a", "A", FLOAT)).pin(in("b", "B", FLOAT)).pin(out("result", "Result", FLOAT))
            .behaviour(c -> c.output("result", op.apply(c.inputFloat("a"), c.inputFloat("b")))).build());
    }

    private interface FloatPredicate {
        boolean test(float a, float b);
    }

    private static void registerComparison(NodeRegistry registry, String id, String title, FloatPredicate predicate) {
        registry.register(NodeType.builder(id, title, "Math", PURE)
            .pin(in("a", "A", FLOAT)).pin(in("b", "B", FLOAT)).pin(out("result", "Result", BOOLEAN))
            .behaviour(c -> c.output("result", predicate.test(c.inputFloat("a"), c.inputFloat("b")))).build());
    }

    /** "3" for whole numbers, otherwise up to three decimals ("2.5", "0.333"). */
    static String formatNumber(float value) {
        if (value == Math.rint(value) && !Float.isInfinite(value)) {
            return Long.toString((long) value);
        }
        String text = String.format(Locale.ROOT, "%.3f", value);
        return text.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    /** Whether any of the comma separated libGDX key names is held down; an unknown name is an error that names the node. */
    private static boolean anyKeyDown(NodeContext c, String keys) {
        List<Integer> codes;
        try {
            codes = KeyNames.parse(keys);
        } catch (IllegalArgumentException e) {
            throw c.error(e.getMessage());
        }
        boolean down = false;
        for (int code : codes) {
            down |= c.isKeyDown(code);
        }
        return down;
    }

    private static Entity target(NodeContext c) {
        Entity target = c.inputEntity("target");
        if (target == null) {
            throw c.error("Target entity is missing");
        }
        return target;
    }

    /** A call on the scene's 3D physics; a scene without it, or a refused call, becomes an error tied to the node. */
    private static void physics(NodeContext c, Consumer<PhysicsSystem3D> call) {
        Scene scene = c.self().getScene();
        PhysicsSystem3D physics = scene != null ? scene.getPhysics3D() : null;
        if (physics == null) {
            throw c.error("This scene runs no 3D physics. Add a 3D collider (Add Component > Physics 3D) to an entity that is active "
                + "when play starts.");
        }
        try {
            call.accept(physics);
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw c.error(e.getMessage());
        }
    }

    private static String variableName(NodeContext c) {
        Object name = c.property(PROPERTY_VARIABLE);
        if (!(name instanceof String s) || s.isBlank()) {
            throw c.error("No variable chosen");
        }
        return s;
    }

    /** Moves {@code transform} so its world position becomes {@code world}, whatever its parent is. */
    static void setWorldPosition(Transform transform, Vector3 world) {
        Transform parent = transform.getParent();
        if (parent == null) {
            transform.setLocalPosition(world);
            return;
        }
        Matrix4 inverse = parent.getWorldMatrix();
        if (Math.abs(inverse.det()) < 1e-12f) {
            return; // a parent scaled to zero has no local position for this world point
        }
        transform.setLocalPosition(new Vector3(world).mul(inverse.inv()));
    }
}
