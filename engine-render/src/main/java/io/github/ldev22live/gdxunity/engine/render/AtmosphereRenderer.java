package io.github.ldev22live.gdxunity.engine.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;
import io.github.ldev22live.gdxunity.engine.scene.environment.AtmosphereComponent;

/**
 * Draws an {@link AtmosphereComponent}'s sky behind the scene and its rain or snow in front of it (roadmap M3.9,
 * TD-090), with one {@link ShapeRenderer} made the first time it is needed. Fog and sunlight are applied by
 * {@link SceneRenderer3D} through the PBR shader's environment. Needs a GL context. Owned by {@link SceneRenderer3D}.
 */
final class AtmosphereRenderer implements Disposable {
    /** Vertices the shape renderer holds before it flushes: room for every rain streak at once. */
    private static final int SHAPE_VERTICES = WeatherParticles.MAX_PARTICLES * 2 + 16;
    /** Length of a rain streak in world units (45 cm). */
    static final float RAIN_STREAK = 45f;
    /** Half the size of a snow flake's cross in world units (5 cm). */
    static final float SNOW_FLAKE = 5f;
    /** Rain colour (sRGB) and how opaque a streak is. */
    static final Color RAIN_COLOR = new Color(0.78f, 0.82f, 0.90f, 0.5f);
    /** Snow colour (sRGB) and how opaque a flake is. */
    static final Color SNOW_COLOR = new Color(0.97f, 0.98f, 1f, 0.9f);
    /** Seed of the weather's random positions, so a smoke test's picture repeats. */
    private static final long WEATHER_SEED = 0x5EA50FL;

    private ShapeRenderer shapes;
    private final SkyGradient gradient = new SkyGradient();
    private final WeatherParticles particles = new WeatherParticles(WEATHER_SEED);
    private final Matrix4 screen = new Matrix4().setToOrtho2D(0f, 0f, 1f, 1f);
    private final Color horizon = new Color();
    private final Color zenith = new Color();
    private boolean disposed;

    /** Fills the current viewport with the sky gradient for {@code camera}. Depth is neither tested nor written. */
    void drawSky(Camera camera, AtmosphereComponent atmosphere) {
        if (disposed || Gdx.gl == null) {
            return;
        }
        float pitch = (float) Math.toDegrees(Math.asin(MathUtils.clamp(camera.direction.y, -1f, 1f)));
        boolean perspective = camera instanceof PerspectiveCamera;
        float fov = perspective ? ((PerspectiveCamera) camera).fieldOfView : 0f;
        atmosphere.getHorizonColor(horizon);
        atmosphere.getZenithColor(zenith);
        gradient.compute(pitch, fov, perspective, horizon, zenith);
        float h = MathUtils.clamp(gradient.horizonFraction, 0f, 1f);

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        ShapeRenderer renderer = shapes();
        renderer.setProjectionMatrix(screen);
        renderer.begin(ShapeRenderer.ShapeType.Filled);
        if (h > 0f) {
            renderer.setColor(horizon);
            renderer.rect(0f, 0f, 1f, h);
        }
        if (h < 1f) {
            Color low = gradient.lowColor;
            Color top = gradient.topColor;
            renderer.rect(0f, h, 1f, 1f - h, low, low, top, top);
        }
        renderer.end();
    }

    /**
     * Moves the rain or snow on by {@code deltaSeconds} and draws it with depth testing, so terrain and models hide what
     * falls behind them. Nothing is drawn in clear weather.
     */
    void drawWeather(Camera camera, AtmosphereComponent atmosphere, float deltaSeconds) {
        particles.update(deltaSeconds, camera.position, atmosphere.getWeather(), atmosphere.getWeatherIntensity());
        if (disposed || Gdx.gl == null || particles.count() == 0) {
            return;
        }
        boolean rain = particles.weather() == AtmosphereComponent.Weather.RAIN;
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
        Gdx.gl.glDepthMask(false);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        ShapeRenderer renderer = shapes();
        renderer.setProjectionMatrix(camera.combined);
        renderer.begin(ShapeRenderer.ShapeType.Line);
        renderer.setColor(rain ? RAIN_COLOR : SNOW_COLOR);
        for (int i = 0; i < particles.count(); i++) {
            float x = particles.x(i);
            float y = particles.y(i);
            float z = particles.z(i);
            if (rain) {
                renderer.line(x, y, z, x - RAIN_STREAK * WeatherParticles.RAIN_SLANT, y + RAIN_STREAK, z);
            } else {
                renderer.line(x - SNOW_FLAKE, y, z, x + SNOW_FLAKE, y, z);
                renderer.line(x, y - SNOW_FLAKE, z, x, y + SNOW_FLAKE, z);
                renderer.line(x, y, z - SNOW_FLAKE, x, y, z + SNOW_FLAKE);
            }
        }
        renderer.end();
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glDisable(GL20.GL_BLEND);
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    }

    /** Particles falling after the last {@link #drawWeather}, for tests and statistics. */
    int weatherParticles() {
        return particles.count();
    }

    private ShapeRenderer shapes() {
        if (shapes == null) {
            shapes = new ShapeRenderer(SHAPE_VERTICES);
        }
        return shapes;
    }

    /** Disposes the shape renderer. Idempotent. */
    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        if (shapes != null) {
            ShapeRenderer renderer = shapes;
            shapes = null;
            renderer.dispose();
        }
    }
}
