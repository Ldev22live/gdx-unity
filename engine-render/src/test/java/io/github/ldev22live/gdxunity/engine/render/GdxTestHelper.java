package io.github.ldev22live.gdxunity.engine.render;

import com.badlogic.gdx.utils.GdxNativesLoader;

/**
 * Helper to ensure LibGDX native libraries are loaded in unit test environments.
 */
public final class GdxTestHelper {
    private static boolean loaded = false;

    public static synchronized void initNatives() {
        if (!loaded) {
            GdxNativesLoader.load();
            loaded = true;
        }
    }
}
