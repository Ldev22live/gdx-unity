package io.github.ldev22live.gdxunity.engine.render;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;

import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;

/**
 * glTF files built in code for headless tests, and do-nothing GL / application stubs so meshes can be created
 * without a window (LibGDX 1.14 ships no mock GL). Nothing drawn through the stub is checked; only loading and posing are.
 */
public final class GltfTestFiles {

    private GltfTestFiles() {}

    private static GL20 previousGl;
    private static Application previousApp;

    /**
     * Installs a GL whose calls do nothing and return 1 / false / 0 (enough for {@code Mesh} creation) and an
     * application that only swallows log lines. Other tests take a non-null {@code Gdx.gl} as a real context, so
     * call {@link #removeStubs()} in {@code @AfterAll}.
     */
    public static synchronized void installStubs() {
        previousGl = Gdx.gl20;
        previousApp = Gdx.app;
        GL20 gl = stub(GL20.class);
        Gdx.gl = gl;
        Gdx.gl20 = gl;
        Gdx.app = stub(Application.class);
    }

    /** Restores what {@link #installStubs()} replaced. */
    public static synchronized void removeStubs() {
        Gdx.gl = previousGl;
        Gdx.gl20 = previousGl;
        Gdx.app = previousApp;
    }

    private static <T> T stub(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(GltfTestFiles.class.getClassLoader(), new Class<?>[]{type},
            (proxy, method, args) -> {
                Class<?> returnType = method.getReturnType();
                if (returnType == int.class) return 1;
                if (returnType == long.class) return 0L;
                if (returnType == boolean.class) return false;
                if (returnType == float.class) return 0f;
                if (returnType == String.class) return "";
                return null;
            }));
    }

    /**
     * A 1 x 2 strip of six vertices skinned to two joints, "Root" at the origin and "Tip" one unit up, with one
     * clip "Bend" (1 s) rotating Tip about Z: 0 degrees, 45 degrees at 0.5 s, back to 0.
     */
    public static String skinnedStrip() {
        float[] positions = {0, 0, 0, 1, 0, 0, 0, 1, 0, 1, 1, 0, 0, 2, 0, 1, 2, 0};
        short[] joints = {0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0};
        float[] weights = {1, 0, 0, 0, 1, 0, 0, 0, .5f, .5f, 0, 0, .5f, .5f, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0};
        short[] indices = {0, 1, 3, 0, 3, 2, 2, 3, 5, 2, 5, 4};
        float[] inverseBind = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1,
            1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, -1, 0, 1};
        float[] times = {0f, 0.5f, 1f};
        float s = (float) Math.sin(Math.PI / 8);
        float c = (float) Math.cos(Math.PI / 8);
        float[] rotations = {0, 0, 0, 1, 0, 0, s, c, 0, 0, 0, 1};

        ByteBuffer buffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN);
        int positionsAt = buffer.position();
        for (float f : positions) buffer.putFloat(f);
        int jointsAt = buffer.position();
        for (short v : joints) buffer.putShort(v);
        int weightsAt = buffer.position();
        for (float f : weights) buffer.putFloat(f);
        int indicesAt = buffer.position();
        for (short v : indices) buffer.putShort(v);
        int inverseBindAt = buffer.position();
        for (float f : inverseBind) buffer.putFloat(f);
        int timesAt = buffer.position();
        for (float f : times) buffer.putFloat(f);
        int rotationsAt = buffer.position();
        for (float f : rotations) buffer.putFloat(f);
        int length = buffer.position();
        byte[] bytes = new byte[length];
        buffer.flip();
        buffer.get(bytes);
        String uri = "data:application/octet-stream;base64," + Base64.getEncoder().encodeToString(bytes);

        return "{\"asset\":{\"version\":\"2.0\"},\"scene\":0,\"scenes\":[{\"nodes\":[0,1]}],"
            + "\"nodes\":[{\"mesh\":0,\"skin\":0,\"name\":\"Strip\"},{\"name\":\"Root\",\"children\":[2]},"
            + "{\"name\":\"Tip\",\"translation\":[0,1,0]}],"
            + "\"skins\":[{\"inverseBindMatrices\":4,\"joints\":[1,2]}],"
            + "\"meshes\":[{\"primitives\":[{\"attributes\":{\"POSITION\":0,\"JOINTS_0\":1,\"WEIGHTS_0\":2},\"indices\":3}]}],"
            + "\"animations\":[{\"name\":\"Bend\",\"channels\":[{\"sampler\":0,\"target\":{\"node\":2,\"path\":\"rotation\"}}],"
            + "\"samplers\":[{\"input\":5,\"output\":6,\"interpolation\":\"LINEAR\"}]}],"
            + "\"buffers\":[{\"uri\":\"" + uri + "\",\"byteLength\":" + length + "}],"
            + "\"bufferViews\":[" + view(positionsAt, 72) + "," + view(jointsAt, 48) + "," + view(weightsAt, 96) + ","
            + view(indicesAt, 24) + "," + view(inverseBindAt, 128) + "," + view(timesAt, 12) + "," + view(rotationsAt, 48) + "],"
            + "\"accessors\":["
            + "{\"bufferView\":0,\"componentType\":5126,\"count\":6,\"type\":\"VEC3\",\"min\":[0,0,0],\"max\":[1,2,0]},"
            + "{\"bufferView\":1,\"componentType\":5123,\"count\":6,\"type\":\"VEC4\"},"
            + "{\"bufferView\":2,\"componentType\":5126,\"count\":6,\"type\":\"VEC4\"},"
            + "{\"bufferView\":3,\"componentType\":5123,\"count\":12,\"type\":\"SCALAR\"},"
            + "{\"bufferView\":4,\"componentType\":5126,\"count\":2,\"type\":\"MAT4\"},"
            + "{\"bufferView\":5,\"componentType\":5126,\"count\":3,\"type\":\"SCALAR\",\"min\":[0],\"max\":[1]},"
            + "{\"bufferView\":6,\"componentType\":5126,\"count\":3,\"type\":\"VEC4\"}]}";
    }

    private static String view(int offset, int length) {
        return "{\"buffer\":0,\"byteOffset\":" + offset + ",\"byteLength\":" + length + "}";
    }
}
