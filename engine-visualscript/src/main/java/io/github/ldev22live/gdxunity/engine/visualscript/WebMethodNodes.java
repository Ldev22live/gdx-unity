package io.github.ldev22live.gdxunity.engine.visualscript;

import java.util.List;

/**
 * The Java method nodes the web player can run too (TD-065, part A): the ones that have an exact TypeScript counterpart. The list is
 * the contract between the two sides: {@code engine-babylon/src/visualscript/javaMethodNodes.json} is made from these nodes (pins,
 * kinds and the results of real calls), the web player reads it, and a test on each side fails when the two disagree. Any other method node
 * is desktop-only on the web, as before.
 *
 * <p>Only methods of {@code MathUtils} and {@code Vector3} are listed: their pins are numbers, booleans and vectors, which the web
 * player has. {@code MathUtils.sin} and the like are libGDX's table-based approximations on the desktop and exact on the web; they agree
 * to about 1e-4.
 */
public final class WebMethodNodes {
    private static final String MATH = JavaMethodNodes.ID_PREFIX + "com.badlogic.gdx.math.MathUtils#";
    private static final String VECTOR = JavaMethodNodes.ID_PREFIX + "com.badlogic.gdx.math.Vector3#";

    /** Ids of the method nodes with a web version, in the order the table lists them. */
    public static final List<String> IDS = List.of(
        MATH + "clamp(float,float,float)", MATH + "clamp(int,int,int)", MATH + "lerp(float,float,float)",
        MATH + "map(float,float,float,float,float)", MATH + "norm(float,float,float)",
        MATH + "sin(float)", MATH + "cos(float)", MATH + "tan(float)",
        MATH + "sinDeg(float)", MATH + "cosDeg(float)", MATH + "tanDeg(float)",
        MATH + "asin(float)", MATH + "acos(float)", MATH + "atan(float)", MATH + "atan2(float,float)", MATH + "atan2Deg(float,float)",
        MATH + "floor(float)", MATH + "ceil(float)", MATH + "round(float)", MATH + "log2(float)",
        MATH + "isZero(float)", MATH + "isEqual(float,float,float)",
        MATH + "random()", MATH + "random(float,float)", MATH + "random(int,int)", MATH + "randomBoolean()",
        VECTOR + "<init>(float,float,float)", VECTOR + "cpy()",
        VECTOR + "dot(com.badlogic.gdx.math.Vector3)", VECTOR + "dst(com.badlogic.gdx.math.Vector3)",
        VECTOR + "dst2(com.badlogic.gdx.math.Vector3)", VECTOR + "len()", VECTOR + "len2()", VECTOR + "isZero()",
        VECTOR + "add(com.badlogic.gdx.math.Vector3)", VECTOR + "sub(com.badlogic.gdx.math.Vector3)",
        VECTOR + "scl(float)", VECTOR + "scl(com.badlogic.gdx.math.Vector3)", VECTOR + "nor()",
        VECTOR + "crs(com.badlogic.gdx.math.Vector3)", VECTOR + "lerp(com.badlogic.gdx.math.Vector3,float)",
        VECTOR + "limit(float)", VECTOR + "setLength(float)", VECTOR + "mulAdd(com.badlogic.gdx.math.Vector3,float)");

    private WebMethodNodes() {
    }

    /** Whether the web player has a version of the method node {@code typeId} (either the node or its Action twin). */
    public static boolean hasWebVersion(String typeId) {
        if (typeId == null) {
            return false;
        }
        String base = JavaMethodNodes.isActionTwin(typeId) ? typeId.substring(0, typeId.length() - JavaMethodNodes.ACTION_SUFFIX.length()) : typeId;
        return IDS.contains(base);
    }
}
