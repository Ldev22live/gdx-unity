package io.github.ldev22live.gdxunity.engine.core.time;

/**
 * Standard TimeProvider that delegates to LibGDX's Gdx.graphics.getDeltaTime().
 */
public final class LibGdxTimeProvider implements TimeProvider {
    @Override
    public float getRawDeltaTime() {
        return com.badlogic.gdx.Gdx.graphics != null ? com.badlogic.gdx.Gdx.graphics.getDeltaTime() : 0.0f;
    }
}
