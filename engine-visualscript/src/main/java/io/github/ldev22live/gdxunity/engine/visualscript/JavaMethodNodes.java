package io.github.ldev22live.gdxunity.engine.visualscript;

import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.scene.Entity;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Makes nodes from the public methods and constructors of the classes in a {@link ClassCatalog} (TD-060), so a graph can call the
 * engine API and the project's own classes.
 *
 * <ul>
 *   <li><b>Id</b> {@code java:<class>#<method>(<parameter types>)}, for example {@code java:com.badlogic.gdx.math.Vector3#len()}; a
 *       constructor is {@code #<init>}. It does not change when a parameter is renamed.</li>
 *   <li><b>Pins</b> an instance method has a {@code target} pin, every parameter a pin {@code p0}, {@code p1}, ... and a method that
 *       returns something a {@code result} pin. Numbers, booleans, text, {@link Vector3} and {@link Entity} use the matching value
 *       types, an enum is a text pin that takes the constant's name, everything else is an {@link ValueType#OBJECT} pin that names its
 *       class. A method with a parameter or result this cannot express (an array, a {@code char}) gets no node.</li>
 *   <li><b>Kind</b> a method that returns nothing is an Action. One that returns something is Pure when it is named like a getter
 *       ({@code get}, {@code is}, {@code has}, {@code can}, {@code find}, {@code contains}, {@code to}) or returns a number, boolean or
 *       text and takes only such values and vectors; any other is an Action that keeps its result for the pins wired to it.</li>
 *   <li><b>Running</b> calls the method through a cached method handle. A target that is not set, an argument that does not fit and
 *       an exception from the method stop the chain with an error that names the node.</li>
 * </ul>
 */
public final class JavaMethodNodes {
    public static final String ID_PREFIX = "java:";
    public static final String PIN_TARGET = "target";
    public static final String PIN_RESULT = "result";
    /** Prefix of the parameter pin ids: {@code p0} is the first parameter. */
    public static final String PIN_ARGUMENT = "p";
    /**
     * Ends the id of the Action twin of a node that is Pure by default (TD-060): the same method, run in the chain of execution
     * with its result kept for the pins wired to it. The twin is not offered in the node menu; a node is switched to it and back
     * (see {@link #counterpartOf}). Order of evaluation is the reason to want it: a Pure node runs each time a pin reads it, an Action
     * node once, when the chain reaches it.
     */
    public static final String ACTION_SUFFIX = "@action";
    /** Category prefix in the node menu: one category per class. */
    public static final String CATEGORY_PREFIX = "Java: ";
    private static final String CONSTRUCTOR_NAME = "<init>";
    private static final List<String> GETTER_PREFIXES = List.of("get", "is", "has", "can", "find", "contains", "to");

    private JavaMethodNodes() {
    }

    /** Whether {@code typeId} names a node made from a method. */
    public static boolean isMethodNode(String typeId) {
        return typeId != null && typeId.startsWith(ID_PREFIX);
    }

    /** The id of the node for {@code member}. */
    public static String nodeId(Executable member) {
        Objects.requireNonNull(member, "member cannot be null");
        String name = member instanceof Constructor<?> ? CONSTRUCTOR_NAME : member.getName();
        String parameters = Arrays.stream(member.getParameterTypes()).map(Class::getTypeName).collect(Collectors.joining(","));
        return ID_PREFIX + member.getDeclaringClass().getName() + "#" + name + "(" + parameters + ")";
    }

    /**
     * Registers a node for each usable method and constructor of the catalog's classes. Ids already in the registry are kept as they
     * are.
     *
     * @return how many nodes were added
     */
    public static int registerAll(NodeRegistry registry, ClassCatalog catalog) {
        Objects.requireNonNull(registry, "registry cannot be null");
        Objects.requireNonNull(catalog, "catalog cannot be null");
        int added = 0;
        for (Class<?> type : catalog.getClasses()) {
            for (NodeType node : typesFor(type)) {
                if (registry.get(node.getId()) == null) {
                    registry.register(node);
                    added++;
                }
            }
        }
        return added;
    }

    /** Whether {@code typeId} is the Action twin of a Pure method node. */
    public static boolean isActionTwin(String typeId) {
        return isMethodNode(typeId) && typeId.endsWith(ACTION_SUFFIX);
    }

    /**
     * The node type a method node can be switched to: the Action twin of a Pure node, or the Pure node of a twin.
     *
     * @return the other type's id, or null when the node has no twin (it is an Action by nature)
     */
    public static String counterpartOf(String typeId, NodeRegistry registry) {
        Objects.requireNonNull(registry, "registry cannot be null");
        if (!isMethodNode(typeId)) {
            return null;
        }
        String other = isActionTwin(typeId) ? typeId.substring(0, typeId.length() - ACTION_SUFFIX.length()) : typeId + ACTION_SUFFIX;
        return registry.get(other) != null ? other : null;
    }

    /** The nodes of one class, in a stable order (by method name, then parameters). */
    public static List<NodeType> typesFor(Class<?> type) {
        Objects.requireNonNull(type, "type cannot be null");
        if (!Modifier.isPublic(type.getModifiers())) {
            return List.of();
        }
        List<Executable> members = new ArrayList<>();
        for (Method method : type.getMethods()) {
            if (usable(method)) {
                members.add(method);
            }
        }
        for (Constructor<?> constructor : type.getConstructors()) {
            if (usable(constructor)) {
                members.add(constructor);
            }
        }
        members.sort((a, b) -> nodeId(a).compareTo(nodeId(b)));
        Map<String, Integer> sameName = new HashMap<>();
        for (Executable member : members) {
            sameName.merge(titleName(member), 1, Integer::sum);
        }
        List<NodeType> nodes = new ArrayList<>();
        for (Executable member : members) {
            NodeType node = nodeFor(type, member, sameName.get(titleName(member)) > 1, false);
            nodes.add(node);
            if (node.getKind() == NodeType.Kind.PURE) {
                nodes.add(nodeFor(type, member, sameName.get(titleName(member)) > 1, true));
            }
        }
        return nodes;
    }

    // --- Which members get a node ---

    private static boolean usable(Executable member) {
        if (!Modifier.isPublic(member.getModifiers()) || member.isSynthetic() || member.isVarArgs()
            || member.isAnnotationPresent(Deprecated.class) || !Modifier.isPublic(member.getDeclaringClass().getModifiers())) {
            return false;
        }
        if (member instanceof Method method) {
            if (method.isBridge() || method.getDeclaringClass() == Object.class) {
                return false;
            }
            if (method.getReturnType() != void.class && !expressible(method.getReturnType())) {
                return false;
            }
        }
        for (Class<?> parameter : member.getParameterTypes()) {
            if (!expressible(parameter)) {
                return false;
            }
        }
        return true;
    }

    private static boolean expressible(Class<?> type) {
        return !type.isArray() && type != char.class && type != Character.class && type != void.class;
    }

    // --- Types of pins ---

    static ValueType valueTypeOf(Class<?> type) {
        if (type == float.class || type == double.class || type == Float.class || type == Double.class) {
            return ValueType.FLOAT;
        }
        if (type == int.class || type == long.class || type == short.class || type == byte.class
            || type == Integer.class || type == Long.class || type == Short.class || type == Byte.class) {
            return ValueType.INTEGER;
        }
        if (type == boolean.class || type == Boolean.class) {
            return ValueType.BOOLEAN;
        }
        if (type == String.class || type.isEnum()) {
            return ValueType.STRING;
        }
        if (type == Vector3.class) {
            return ValueType.VECTOR3;
        }
        if (type == Entity.class) {
            return ValueType.ENTITY;
        }
        return ValueType.OBJECT;
    }

    private static PinDef pin(String id, String label, Class<?> type, boolean input) {
        ValueType valueType = valueTypeOf(type);
        if (valueType == ValueType.OBJECT) {
            return input ? PinDef.inObject(id, label, type) : PinDef.outObject(id, label, type);
        }
        return input ? PinDef.in(id, label, valueType) : PinDef.out(id, label, valueType);
    }

    private static boolean valueLike(Class<?> type) {
        ValueType valueType = valueTypeOf(type);
        return valueType == ValueType.FLOAT || valueType == ValueType.INTEGER || valueType == ValueType.BOOLEAN
            || valueType == ValueType.STRING || valueType == ValueType.VECTOR3 || type == com.badlogic.gdx.math.Quaternion.class;
    }

    // --- Building a node ---

    private static String titleName(Executable member) {
        String name = member instanceof Constructor<?> ? "new " + member.getDeclaringClass().getSimpleName() : member.getName();
        return member.getDeclaringClass().getSimpleName() + "." + name;
    }

    private static NodeType.Kind kindOf(Executable member) {
        if (member instanceof Constructor<?>) {
            return NodeType.Kind.ACTION;
        }
        Method method = (Method) member;
        Class<?> result = method.getReturnType();
        if (result == void.class) {
            return NodeType.Kind.ACTION;
        }
        String name = method.getName();
        for (String prefix : GETTER_PREFIXES) {
            if (name.length() > prefix.length() && name.startsWith(prefix) && Character.isUpperCase(name.charAt(prefix.length()))) {
                return NodeType.Kind.PURE;
            }
        }
        boolean valueResult = valueLike(result) && result != Vector3.class && result != com.badlogic.gdx.math.Quaternion.class;
        boolean valueParameters = Arrays.stream(method.getParameterTypes()).allMatch(JavaMethodNodes::valueLike);
        return valueResult && valueParameters ? NodeType.Kind.PURE : NodeType.Kind.ACTION;
    }

    private static NodeType nodeFor(Class<?> owner, Executable member, boolean overloaded, boolean actionTwin) {
        boolean constructor = member instanceof Constructor<?>;
        boolean isStatic = !constructor && Modifier.isStatic(member.getModifiers());
        Class<?> resultType = constructor ? member.getDeclaringClass() : ((Method) member).getReturnType();
        NodeType.Kind kind = actionTwin ? NodeType.Kind.ACTION : kindOf(member);
        Class<?>[] parameterTypes = member.getParameterTypes();
        Parameter[] parameters = member.getParameters();

        String title = titleName(member);
        if (overloaded && parameterTypes.length > 0) {
            title += " (" + Arrays.stream(parameterTypes).map(Class::getSimpleName).collect(Collectors.joining(", ")) + ")";
        }
        // Under the class that declares it, so a method every subclass inherits is found in one place
        NodeType.Builder builder = NodeType.builder(nodeId(member) + (actionTwin ? ACTION_SUFFIX : ""),
            actionTwin ? title + " (as Action)" : title, CATEGORY_PREFIX + member.getDeclaringClass().getSimpleName(), kind);
        builder.source(member);
        boolean instance = !constructor && !isStatic;
        if (instance) {
            builder.pin(pin(PIN_TARGET, "Target", member.getDeclaringClass(), true));
        }
        for (int i = 0; i < parameterTypes.length; i++) {
            String label = parameters[i].isNamePresent() ? parameters[i].getName() : parameterTypes[i].getSimpleName();
            builder.pin(pin(PIN_ARGUMENT + i, label, parameterTypes[i], true));
        }
        boolean hasResult = resultType != void.class;
        if (hasResult) {
            builder.pin(pin(PIN_RESULT, "Result", resultType, false));
        }
        builder.behaviour(behaviour(owner, member, instance, hasResult, kind == NodeType.Kind.ACTION));
        return builder.build();
    }

    private static Consumer<NodeContext> behaviour(Class<?> owner, Executable member, boolean instance, boolean hasResult, boolean action) {
        MethodHandle handle;
        try {
            handle = member instanceof Constructor<?> constructor
                ? MethodHandles.publicLookup().unreflectConstructor(constructor)
                : MethodHandles.publicLookup().unreflect((Method) member);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot call " + nodeId(member) + ": " + e.getMessage(), e);
        }
        Class<?>[] parameterTypes = member.getParameterTypes();
        Class<?> resultType = member instanceof Constructor<?> ? member.getDeclaringClass() : ((Method) member).getReturnType();
        ValueType resultValueType = hasResult ? valueTypeOf(resultType) : null;
        String label = titleName(member);
        return context -> {
            List<Object> arguments = new ArrayList<>(parameterTypes.length + 1);
            if (instance) {
                arguments.add(target(context, member.getDeclaringClass(), label));
            }
            for (int i = 0; i < parameterTypes.length; i++) {
                arguments.add(argument(context, PIN_ARGUMENT + i, parameterTypes[i], label));
            }
            Object result;
            try {
                result = handle.invokeWithArguments(arguments);
            } catch (GraphRuntimeException e) {
                throw e;
            } catch (Throwable e) {
                Throwable cause = e.getCause() != null && e instanceof java.lang.reflect.InvocationTargetException ? e.getCause() : e;
                throw context.error(label + " failed: " + cause.getClass().getSimpleName()
                    + (cause.getMessage() != null ? ": " + cause.getMessage() : ""));
            }
            if (hasResult) {
                context.output(PIN_RESULT, fromResult(context, result, resultValueType, label));
            }
            if (action) {
                context.continueWith(PinDef.EXEC_OUT);
            }
        };
    }

    private static Object target(NodeContext context, Class<?> type, String label) {
        ValueType valueType = valueTypeOf(type);
        Object target = valueType == ValueType.ENTITY ? context.inputEntity(PIN_TARGET) : context.input(PIN_TARGET);
        if (target == null) {
            throw context.error(label + ": the Target is not set (wire an object into it)");
        }
        return valueType == ValueType.VECTOR3 ? new Vector3((Vector3) target) : target;
    }

    private static Object argument(NodeContext context, String pinId, Class<?> type, String label) {
        ValueType valueType = valueTypeOf(type);
        Object value = valueType == ValueType.ENTITY ? context.inputEntity(pinId) : context.input(pinId);
        if (type.isPrimitive()) {
            if (value == null) {
                throw context.error(label + ": " + pinId + " needs a value");
            }
            return primitive(context, value, type, label);
        }
        if (type.isEnum()) {
            return enumConstant(context, String.valueOf(value), type, label);
        }
        if (valueType == ValueType.FLOAT || valueType == ValueType.INTEGER) {
            return value == null ? null : boxedNumber(value, type);
        }
        if (value instanceof Vector3 vector) {
            return new Vector3(vector);
        }
        if (value != null && !type.isInstance(value)) {
            throw context.error(label + ": " + pinId + " needs a " + type.getSimpleName() + ", got " + value.getClass().getSimpleName());
        }
        return value;
    }

    private static Object primitive(NodeContext context, Object value, Class<?> type, String label) {
        if (type == boolean.class) {
            return value;
        }
        Number number = (Number) value;
        if (type == float.class) {
            return number.floatValue();
        }
        if (type == double.class) {
            return number.doubleValue();
        }
        if (type == int.class) {
            return number.intValue();
        }
        if (type == long.class) {
            return number.longValue();
        }
        if (type == short.class) {
            return number.shortValue();
        }
        if (type == byte.class) {
            return number.byteValue();
        }
        throw context.error(label + ": unsupported parameter type " + type);
    }

    private static Object boxedNumber(Object value, Class<?> type) {
        Number number = (Number) value;
        if (type == Float.class) {
            return number.floatValue();
        }
        if (type == Double.class) {
            return number.doubleValue();
        }
        if (type == Long.class) {
            return number.longValue();
        }
        if (type == Short.class) {
            return number.shortValue();
        }
        if (type == Byte.class) {
            return number.byteValue();
        }
        return number.intValue();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumConstant(NodeContext context, String name, Class<?> type, String label) {
        try {
            return Enum.valueOf((Class<? extends Enum>) type, name);
        } catch (IllegalArgumentException e) {
            String allowed = Arrays.stream(type.getEnumConstants()).map(Object::toString).collect(Collectors.joining(", "));
            throw context.error(label + ": \"" + name + "\" is not a " + type.getSimpleName() + " (one of " + allowed + ")");
        }
    }

    private static Object fromResult(NodeContext context, Object result, ValueType type, String label) {
        if (result == null) {
            return null;
        }
        return switch (type) {
            case FLOAT -> ((Number) result).floatValue();
            case INTEGER -> {
                long whole = ((Number) result).longValue();
                if (whole > Integer.MAX_VALUE || whole < Integer.MIN_VALUE) {
                    throw context.error(label + " returned " + whole + ", which does not fit a whole-number pin");
                }
                yield (int) whole;
            }
            case STRING -> result instanceof Enum<?> constant ? constant.name() : result;
            case VECTOR3 -> new Vector3((Vector3) result);
            default -> result;
        };
    }
}
