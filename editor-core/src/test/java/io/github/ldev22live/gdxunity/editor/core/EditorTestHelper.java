package io.github.ldev22live.gdxunity.editor.core;

import com.badlogic.gdx.utils.GdxNativesLoader;

/**
 * Ensures LibGDX natives are loaded in test environments.
 */
public final class EditorTestHelper {
    private static boolean loaded = false;

    public static synchronized void initNatives() {
        if (!loaded) {
            GdxNativesLoader.load();
            loaded = true;
        }
    }
}
