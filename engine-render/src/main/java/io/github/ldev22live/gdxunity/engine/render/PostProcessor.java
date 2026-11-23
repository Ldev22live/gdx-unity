package io.github.ldev22live.gdxunity.engine.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.GLFrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.Disposable;
import io.github.ldev22live.gdxunity.engine.core.resource.ResourceManager;
import io.github.ldev22live.gdxunity.engine.scene.environment.PostProcessSettings;

import java.nio.IntBuffer;
import java.util.Objects;

/**
 * Bloom, colour grading, ambient occlusion and depth of field on a finished 3D frame (roadmap M6.2, TD-091), with LibGDX frame
 * buffers and shaders only. Used by {@link SceneRenderer3D} in three steps:
 * <ol>
 *   <li>{@link #begin}: remembers the host's frame buffer and viewport and binds an off-screen frame of the viewport's size, which
 *       the scene is drawn into instead.</li>
 *   <li>{@link #beginDepth}: binds a second frame that the scene's depth is drawn into, packed into its colours by gdx-gltf's depth
 *       shader (the shadow map's format, with the depth taken per pixel: {@link #exactDepthFragment}), since a GL 2 context cannot
 *       sample a depth buffer.</li>
 *   <li>{@link #finish}: runs the effects that are on and draws the result into the host's viewport. It also writes the scene's depth
 *       there, so what the host draws next (2D sprites, gizmos) is still hidden behind 3D surfaces.</li>
 * </ol>
 * Bloom, ambient occlusion and the depth-of-field blur work at half resolution. The effects work on the displayed colours (0 to 1),
 * so bloom picks out what is bright on screen, not what is bright in physical units. Needs a GL context; owns every frame buffer,
 * shader and mesh it makes.
 */
public final class PostProcessor implements Disposable {
    /** Soft knee either side of the bloom threshold, on the 0 to 1 brightness scale. */
    static final float BLOOM_KNEE = 0.15f;
    /** Blur rounds (horizontal then vertical) that spread the bloom; each widens it. */
    static final int BLOOM_ROUNDS = 3;
    /** Half-resolution texels between the depth-of-field blur's taps at full blur. */
    static final float MAX_DOF_STEP = 3f;
    /** Blur rounds of the depth-of-field copy. */
    static final int DOF_ROUNDS = 2;
    /** Largest ambient occlusion sampling radius, as a fraction of the frame, so close-ups do not sample the whole screen. */
    static final float MAX_AO_SCREEN_RADIUS = 0.1f;
    /** Window depth at or above which a pixel is sky (cleared, nothing drawn). */
    static final float SKY_DEPTH = 0.99999f;
    private static final int GL_INT_QUERY_SIZE = 16;
    private static final int UNIT_SCENE = 0;
    private static final int UNIT_DEPTH = 1;
    private static final int UNIT_BLOOM = 2;
    private static final int UNIT_AO = 3;
    private static final int UNIT_DOF = 4;

    private static final String PREFIX = """
        #ifdef GL_ES
        precision highp float;
        #endif
        """;

    private static final String VERTEX = PREFIX + """
        attribute vec2 a_position;
        varying vec2 v_uv;
        void main() {
            v_uv = a_position * 0.5 + 0.5;
            gl_Position = vec4(a_position, 0.0, 1.0);
        }
        """;

    /** Window depth packed into RGBA by gdx-gltf's depth shader, and its distance (PostProcessMath.linearDepth). */
    private static final String DEPTH_FUNCTIONS = """
        uniform float u_near;
        uniform float u_far;
        uniform float u_ortho;
        float unpackDepth(vec4 c) {
            return dot(c, vec4(1.0, 1.0 / 255.0, 1.0 / 65025.0, 1.0 / 16581375.0));
        }
        float linearDepth(float d) {
            return u_ortho > 0.5 ? u_near + d * (u_far - u_near) : u_near * u_far / (u_far - d * (u_far - u_near));
        }
        """;

    /** Four bilinear taps around each half-resolution pixel; with a threshold, only what is bright passes (bloomContribution). */
    private static final String DOWNSAMPLE = PREFIX + """
        varying vec2 v_uv;
        uniform sampler2D u_texture;
        uniform vec2 u_texel;
        uniform float u_threshold;
        uniform float u_knee;
        void main() {
            vec3 c = (texture2D(u_texture, v_uv + u_texel * vec2(-0.5, -0.5)).rgb
                + texture2D(u_texture, v_uv + u_texel * vec2(0.5, -0.5)).rgb
                + texture2D(u_texture, v_uv + u_texel * vec2(-0.5, 0.5)).rgb
                + texture2D(u_texture, v_uv + u_texel * vec2(0.5, 0.5)).rgb) * 0.25;
            if (u_threshold >= 0.0) {
                float b = max(c.r, max(c.g, c.b));
                float soft = clamp(b - u_threshold + u_knee, 0.0, 2.0 * u_knee);
                soft = soft * soft / (4.0 * u_knee + 0.0001);
                c *= max(soft, b - u_threshold) / max(b, 0.0001);
            }
            gl_FragColor = vec4(c, 1.0);
        }
        """;

    /** A nine-tap Gaussian along u_step. */
    private static final String BLUR = PREFIX + """
        varying vec2 v_uv;
        uniform sampler2D u_texture;
        uniform vec2 u_step;
        void main() {
            vec4 sum = texture2D(u_texture, v_uv) * 0.2270270270;
            sum += (texture2D(u_texture, v_uv + u_step) + texture2D(u_texture, v_uv - u_step)) * 0.1945945946;
            sum += (texture2D(u_texture, v_uv + u_step * 2.0) + texture2D(u_texture, v_uv - u_step * 2.0)) * 0.1216216216;
            sum += (texture2D(u_texture, v_uv + u_step * 3.0) + texture2D(u_texture, v_uv - u_step * 3.0)) * 0.0540540541;
            sum += (texture2D(u_texture, v_uv + u_step * 4.0) + texture2D(u_texture, v_uv - u_step * 4.0)) * 0.0162162162;
            gl_FragColor = sum;
        }
        """;

    /**
     * Ambient occlusion from depth alone: the view-space position and normal are rebuilt from the depth frame, and twelve points on
     * a spiral within the radius tell how much nearby surfaces rise above this one's tangent plane.
     */
    private static final String OCCLUSION = PREFIX + DEPTH_FUNCTIONS + """
        varying vec2 v_uv;
        uniform sampler2D u_depth;
        uniform vec2 u_texel;
        uniform vec2 u_proj;
        uniform float u_radius;
        uniform float u_intensity;
        uniform float u_maxScreen;
        vec3 viewPosition(vec2 uv) {
            // The depth frame is read without filtering, so take the position of the texel's centre, where that depth was drawn;
            // the exact uv would turn a flat plane seen at an angle into steps, and the steps into false creases
            vec2 centre = (floor(uv / u_texel) + 0.5) * u_texel;
            float z = linearDepth(unpackDepth(texture2D(u_depth, centre)));
            vec2 ndc = centre * 2.0 - 1.0;
            return vec3(u_ortho > 0.5 ? ndc / u_proj : ndc * z / u_proj, z);
        }
        void main() {
            if (unpackDepth(texture2D(u_depth, v_uv)) >= SKY_DEPTH) {
                gl_FragColor = vec4(1.0);
                return;
            }
            vec3 p = viewPosition(v_uv);
            vec3 right = viewPosition(v_uv + vec2(u_texel.x, 0.0));
            vec3 left = viewPosition(v_uv - vec2(u_texel.x, 0.0));
            vec3 up = viewPosition(v_uv + vec2(0.0, u_texel.y));
            vec3 down = viewPosition(v_uv - vec2(0.0, u_texel.y));
            // The neighbour on the same surface is the one nearer in depth, so edges do not bend the normal
            vec3 dx = abs(right.z - p.z) < abs(p.z - left.z) ? right - p : p - left;
            vec3 dy = abs(up.z - p.z) < abs(p.z - down.z) ? up - p : p - down;
            vec3 n = normalize(cross(dx, dy));
            if (dot(n, p) > 0.0) n = -n;
            vec2 reach = 0.5 * u_radius * (u_ortho > 0.5 ? u_proj : u_proj / p.z);
            reach = min(reach, vec2(u_maxScreen));
            float turn = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453) * 6.2831853;
            float hidden = 0.0;
            for (int i = 0; i < 12; i++) {
                float f = float(i);
                float angle = turn + f * 2.3999632;
                vec2 uv = v_uv + vec2(cos(angle), sin(angle)) * ((f + 0.5) / 12.0) * reach;
                vec3 v = viewPosition(uv) - p;
                float len = length(v);
                hidden += max(0.0, dot(v, n) / max(len, 0.0001) - 0.1) * clamp(1.0 - len / u_radius, 0.0, 1.0);
            }
            float ao = clamp(1.0 - u_intensity * hidden / 12.0 * 2.0, 0.0, 1.0);
            gl_FragColor = vec4(ao, ao, ao, 1.0);
        }
        """.replace("SKY_DEPTH", Float.toString(SKY_DEPTH));

    /**
     * Puts it together in the host's viewport: depth of field (circleOfConfusion), ambient occlusion (applied in linear light),
     * bloom, then grading (exposure, white balance and filter in linear light; saturation and contrast on the displayed values). Also
     * writes the scene's depth.
     */
    private static final String COMPOSITE = PREFIX + DEPTH_FUNCTIONS + """
        varying vec2 v_uv;
        uniform sampler2D u_scene;
        uniform sampler2D u_depth;
        uniform sampler2D u_bloom;
        uniform sampler2D u_ao;
        uniform sampler2D u_dof;
        uniform float u_bloomIntensity;
        uniform float u_ao_on;
        uniform float u_dof_on;
        uniform float u_focus;
        uniform float u_range;
        uniform float u_grading;
        uniform float u_exposure;
        uniform float u_contrast;
        uniform float u_saturation;
        uniform vec3 u_balance;
        uniform vec3 u_filter;
        void main() {
            float d = unpackDepth(texture2D(u_depth, v_uv));
            vec3 c = texture2D(u_scene, v_uv).rgb;
            if (u_dof_on > 0.5) {
                float t = clamp((abs(linearDepth(d) - u_focus) - u_range * 0.5) / max(u_range, 1.0), 0.0, 1.0);
                c = mix(c, texture2D(u_dof, v_uv).rgb, t * t * (3.0 - 2.0 * t));
            }
            if (u_ao_on > 0.5) {
                c *= pow(texture2D(u_ao, v_uv).r, 1.0 / 2.2);
            }
            c += texture2D(u_bloom, v_uv).rgb * u_bloomIntensity;
            if (u_grading > 0.5) {
                c = pow(pow(max(c, vec3(0.0)), vec3(2.2)) * u_exposure * u_balance * u_filter, vec3(1.0 / 2.2));
                c = mix(vec3(dot(c, vec3(0.2126, 0.7152, 0.0722))), c, u_saturation);
                c = (c - 0.5) * u_contrast + 0.5;
            }
            gl_FragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
            gl_FragDepth = d;
        }
        """;

    /** The line of gdx-gltf 2.3.0's {@code depth.fs.glsl} that takes the depth from the vertex shader. */
    static final String VERTEX_DEPTH_LINE = "HIGH float depth = v_depth;";
    /** Its replacement: the pixel's own window depth. */
    static final String PIXEL_DEPTH_LINE = "HIGH float depth = gl_FragCoord.z;";

    /**
     * gdx-gltf's packed-depth fragment shader with the depth taken per pixel. Its vertex shader computes {@code z / w} per vertex,
     * which is right for the orthographic shadow map, but across a large triangle seen in perspective (a ground plane of two
     * triangles) the interpolated value drifts from the true depth, and ambient occlusion then finds false creases in bands. The
     * pixel's own {@code gl_FragCoord.z} is exact. Pure string work, testable without a graphics context.
     *
     * @throws IllegalStateException if the line to replace is not there exactly once (a different gdx-gltf version)
     */
    public static String exactDepthFragment(String fragmentSource) {
        Objects.requireNonNull(fragmentSource, "fragmentSource cannot be null");
        int at = fragmentSource.indexOf(VERTEX_DEPTH_LINE);
        if (at < 0 || fragmentSource.indexOf(VERTEX_DEPTH_LINE, at + 1) >= 0) {
            throw new IllegalStateException("gdx-gltf's depth fragment shader changed: expected '" + VERTEX_DEPTH_LINE
                + "' exactly once (the 2.3.0 layout)");
        }
        return fragmentSource.substring(0, at) + PIXEL_DEPTH_LINE + fragmentSource.substring(at + VERTEX_DEPTH_LINE.length());
    }

    private FrameBuffer scene;
    private FrameBuffer depth;
    private FrameBuffer halfA;
    private FrameBuffer halfB;
    private FrameBuffer occlusionA;
    private FrameBuffer occlusionB;
    private FrameBuffer bloom;
    private ShaderProgram downsampleShader;
    private ShaderProgram blurShader;
    private ShaderProgram occlusionShader;
    private ShaderProgram compositeShader;
    private Mesh quad;
    private int width;
    private int height;
    private int hostFrameBuffer;
    /** x, y, width, height of the host's viewport at {@link #begin}. */
    private final int[] hostViewport = new int[4];
    private IntBuffer query;
    private boolean capturing;
    private boolean disposed;
    private final float[] balance = new float[3];
    private final Color filter = new Color();

    /**
     * Remembers the bound frame buffer and viewport and binds the off-screen frame instead, cleared to {@code clearColor}, or, with
     * {@code keepHostImage}, holding a copy of what the host already drew there (such as the editor's grid).
     *
     * @return false, with nothing bound or changed, when the viewport is empty
     */
    public boolean begin(boolean keepHostImage, Color clearColor) {
        Objects.requireNonNull(clearColor, "clearColor cannot be null");
        if (disposed) {
            throw new IllegalStateException("PostProcessor is disposed");
        }
        if (capturing) {
            throw new IllegalStateException("begin() was called twice without finish()");
        }
        saveHost();
        int w = hostViewport[2];
        int h = hostViewport[3];
        if (w <= 0 || h <= 0) {
            return false;
        }
        ensureResources(w, h);
        if (keepHostImage) {
            Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, hostFrameBuffer);
            scene.getColorBufferTexture().bind(UNIT_SCENE);
            Gdx.gl.glCopyTexSubImage2D(GL20.GL_TEXTURE_2D, 0, 0, 0, hostViewport[0], hostViewport[1], w, h);
        }
        target(scene, w, h);
        if (keepHostImage) {
            Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT);
        } else {
            Gdx.gl.glClearColor(clearColor.r, clearColor.g, clearColor.b, clearColor.a);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        }
        capturing = true;
        return true;
    }

    /** Binds the depth frame, cleared to "nothing drawn"; the caller then draws the scene with a packed-depth batch. */
    public void beginDepth() {
        requireCapturing();
        target(depth, width, height);
        Gdx.gl.glClearColor(1f, 0f, 0f, 0f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
    }

    /**
     * Runs the effects of {@code settings} that are on, seen through {@code camera}, and draws the frame into the host's frame
     * buffer and viewport, which stay bound. Leaves blending and depth testing off and texture unit 0 active.
     */
    public void finish(PostProcessSettings settings, Camera camera) {
        Objects.requireNonNull(settings, "settings cannot be null");
        Objects.requireNonNull(camera, "camera cannot be null");
        requireCapturing();
        capturing = false;
        Gdx.gl.glDisable(GL20.GL_BLEND);
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        int hw = PostProcessMath.half(width);
        int hh = PostProcessMath.half(height);
        boolean ortho = camera instanceof OrthographicCamera;
        float projX = camera.projection.val[Matrix4.M00];
        float projY = camera.projection.val[Matrix4.M11];

        Texture bloomTexture = null;
        if (settings.hasBloom()) {
            downsample(scene.getColorBufferTexture(), bloom, settings.getBloomThreshold());
            for (int i = 0; i < BLOOM_ROUNDS; i++) {
                blur(bloom, halfB, settings.getBloomRadius(), 0f);
                blur(halfB, bloom, 0f, settings.getBloomRadius());
            }
            bloomTexture = bloom.getColorBufferTexture();
        }
        Texture occlusionTexture = null;
        if (settings.hasAmbientOcclusion()) {
            target(occlusionA, hw, hh);
            occlusionShader.bind();
            depth.getColorBufferTexture().bind(UNIT_DEPTH);
            uniform(occlusionShader, "u_depth", UNIT_DEPTH);
            depthUniforms(occlusionShader, camera, ortho);
            occlusionShader.setUniformf("u_texel", 1f / width, 1f / height);
            occlusionShader.setUniformf("u_proj", projX, projY);
            occlusionShader.setUniformf("u_radius", settings.getAoRadius());
            occlusionShader.setUniformf("u_intensity", settings.getAoIntensity());
            occlusionShader.setUniformf("u_maxScreen", MAX_AO_SCREEN_RADIUS);
            quad.render(occlusionShader, GL20.GL_TRIANGLE_FAN);
            blur(occlusionA, occlusionB, 1f, 0f);
            blur(occlusionB, occlusionA, 0f, 1f);
            occlusionTexture = occlusionA.getColorBufferTexture();
        }
        Texture focusTexture = null;
        if (settings.hasDepthOfField()) {
            downsample(scene.getColorBufferTexture(), halfA, -1f);
            float step = settings.getBlur() * MAX_DOF_STEP;
            for (int i = 0; i < DOF_ROUNDS; i++) {
                blur(halfA, halfB, step, 0f);
                blur(halfB, halfA, 0f, step);
            }
            focusTexture = halfA.getColorBufferTexture();
        }

        Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, hostFrameBuffer);
        Gdx.gl.glViewport(hostViewport[0], hostViewport[1], hostViewport[2], hostViewport[3]);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_ALWAYS);
        Gdx.gl.glDepthMask(true);
        Texture sceneTexture = scene.getColorBufferTexture();
        compositeShader.bind();
        sceneTexture.bind(UNIT_SCENE);
        depth.getColorBufferTexture().bind(UNIT_DEPTH);
        (bloomTexture != null ? bloomTexture : sceneTexture).bind(UNIT_BLOOM);
        (occlusionTexture != null ? occlusionTexture : sceneTexture).bind(UNIT_AO);
        (focusTexture != null ? focusTexture : sceneTexture).bind(UNIT_DOF);
        uniform(compositeShader, "u_scene", UNIT_SCENE);
        uniform(compositeShader, "u_depth", UNIT_DEPTH);
        uniform(compositeShader, "u_bloom", UNIT_BLOOM);
        uniform(compositeShader, "u_ao", UNIT_AO);
        uniform(compositeShader, "u_dof", UNIT_DOF);
        depthUniforms(compositeShader, camera, ortho);
        uniformf(compositeShader, "u_bloomIntensity", bloomTexture != null ? settings.getBloomIntensity() : 0f);
        uniformf(compositeShader, "u_ao_on", occlusionTexture != null ? 1f : 0f);
        uniformf(compositeShader, "u_dof_on", focusTexture != null ? 1f : 0f);
        uniformf(compositeShader, "u_focus", settings.getFocusDistance());
        uniformf(compositeShader, "u_range", settings.getFocusRange());
        uniformf(compositeShader, "u_grading", settings.hasGrading() ? 1f : 0f);
        uniformf(compositeShader, "u_exposure", (float) Math.pow(2.0, settings.getExposure()));
        uniformf(compositeShader, "u_contrast", settings.getContrast());
        uniformf(compositeShader, "u_saturation", settings.getSaturation());
        PostProcessMath.whiteBalance(settings.getTemperature(), settings.getTint(), balance);
        settings.getColorFilter(filter);
        if (compositeShader.hasUniform("u_balance")) {
            compositeShader.setUniformf("u_balance", balance[0], balance[1], balance[2]);
        }
        if (compositeShader.hasUniform("u_filter")) {
            compositeShader.setUniformf("u_filter", filter.r, filter.g, filter.b);
        }
        quad.render(compositeShader, GL20.GL_TRIANGLE_FAN);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
    }

    /** Whether {@link #begin} bound the off-screen frame and {@link #finish} has not run yet. */
    public boolean isCapturing() {
        return capturing;
    }

    /** Size of the off-screen frame, the host's viewport at the last {@link #begin}. */
    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    private void requireCapturing() {
        if (!capturing) {
            throw new IllegalStateException("begin() has not been called");
        }
    }

    private void saveHost() {
        if (query == null) {
            query = BufferUtils.newIntBuffer(GL_INT_QUERY_SIZE);
        }
        query.clear();
        Gdx.gl.glGetIntegerv(GL20.GL_FRAMEBUFFER_BINDING, query);
        hostFrameBuffer = query.get(0);
        query.clear();
        Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT, query);
        query.get(hostViewport, 0, hostViewport.length);
    }

    /** Makes the shaders once and the frames for a {@code w} by {@code h} viewport; making a frame buffer binds the default one. */
    private void ensureResources(int w, int h) {
        if (quad == null) {
            quad = new Mesh(true, 4, 0, new VertexAttribute(VertexAttributes.Usage.Position, 2, "a_position"));
            quad.setVertices(new float[] {-1f, -1f, 1f, -1f, 1f, 1f, -1f, 1f});
            downsampleShader = compile("downsample", DOWNSAMPLE);
            blurShader = compile("blur", BLUR);
            occlusionShader = compile("ambient occlusion", OCCLUSION);
            compositeShader = compile("composite", COMPOSITE);
        }
        if (w == width && h == height && scene != null) {
            return;
        }
        disposeFrames();
        width = w;
        height = h;
        int hw = PostProcessMath.half(w);
        int hh = PostProcessMath.half(h);
        scene = withDepth(w, h);
        depth = withDepth(w, h);
        // Packed depth must not be filtered: blending two packed values gives a meaningless third
        depth.getColorBufferTexture().setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        halfA = colourOnly(hw, hh);
        halfB = colourOnly(hw, hh);
        bloom = colourOnly(hw, hh);
        occlusionA = colourOnly(hw, hh);
        occlusionB = colourOnly(hw, hh);
    }

    private static FrameBuffer withDepth(int w, int h) {
        GLFrameBuffer.FrameBufferBuilder builder = new GLFrameBuffer.FrameBufferBuilder(w, h);
        builder.addBasicColorTextureAttachment(Pixmap.Format.RGBA8888);
        builder.addDepthRenderBuffer(GL30.GL_DEPTH_COMPONENT24);
        FrameBuffer frame = builder.build();
        smooth(frame);
        return frame;
    }

    private static FrameBuffer colourOnly(int w, int h) {
        FrameBuffer frame = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
        smooth(frame);
        return frame;
    }

    private static void smooth(FrameBuffer frame) {
        Texture texture = frame.getColorBufferTexture();
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        texture.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
    }

    private static ShaderProgram compile(String name, String fragment) {
        ShaderProgram program = new ShaderProgram(VERTEX, fragment);
        if (!program.isCompiled()) {
            String log = program.getLog();
            program.dispose();
            throw new IllegalStateException("The post-processing " + name + " shader did not compile: " + log);
        }
        return program;
    }

    private static void target(FrameBuffer frame, int w, int h) {
        Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, frame.getFramebufferHandle());
        Gdx.gl.glViewport(0, 0, w, h);
    }

    /** Halves {@code source} into {@code to}; a {@code threshold} of 0 or more keeps only what is brighter (bloom). */
    private void downsample(Texture source, FrameBuffer to, float threshold) {
        target(to, to.getWidth(), to.getHeight());
        downsampleShader.bind();
        source.bind(UNIT_SCENE);
        uniform(downsampleShader, "u_texture", UNIT_SCENE);
        downsampleShader.setUniformf("u_texel", 1f / source.getWidth(), 1f / source.getHeight());
        uniformf(downsampleShader, "u_threshold", threshold);
        uniformf(downsampleShader, "u_knee", BLOOM_KNEE);
        quad.render(downsampleShader, GL20.GL_TRIANGLE_FAN);
    }

    /** Blurs {@code from} into {@code to} along ({@code texelsX}, {@code texelsY}) texels between taps. */
    private void blur(FrameBuffer from, FrameBuffer to, float texelsX, float texelsY) {
        target(to, to.getWidth(), to.getHeight());
        blurShader.bind();
        from.getColorBufferTexture().bind(UNIT_SCENE);
        uniform(blurShader, "u_texture", UNIT_SCENE);
        blurShader.setUniformf("u_step", texelsX / from.getWidth(), texelsY / from.getHeight());
        quad.render(blurShader, GL20.GL_TRIANGLE_FAN);
    }

    private static void depthUniforms(ShaderProgram shader, Camera camera, boolean ortho) {
        uniformf(shader, "u_near", camera.near);
        uniformf(shader, "u_far", camera.far);
        uniformf(shader, "u_ortho", ortho ? 1f : 0f);
    }

    /** Sets a uniform the compiler kept; one it optimised away (an effect's branch never reads it) is skipped. */
    private static void uniformf(ShaderProgram shader, String name, float value) {
        if (shader.hasUniform(name)) {
            shader.setUniformf(name, value);
        }
    }

    private static void uniform(ShaderProgram shader, String name, int value) {
        if (shader.hasUniform(name)) {
            shader.setUniformi(name, value);
        }
    }

    private void disposeFrames() {
        ResourceManager owned = new ResourceManager();
        for (FrameBuffer frame : new FrameBuffer[] {scene, depth, halfA, halfB, bloom, occlusionA, occlusionB}) {
            if (frame != null) {
                owned.register(frame);
            }
        }
        scene = null;
        depth = null;
        halfA = null;
        halfB = null;
        bloom = null;
        occlusionA = null;
        occlusionB = null;
        width = 0;
        height = 0;
        owned.dispose();
    }

    /** Releases every frame buffer, shader and the quad (TD-008: all are attempted, failures reported together). Idempotent. */
    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        capturing = false;
        ResourceManager owned = new ResourceManager();
        for (Disposable resource : new Disposable[] {quad, downsampleShader, blurShader, occlusionShader, compositeShader}) {
            if (resource != null) {
                owned.register(resource);
            }
        }
        quad = null;
        downsampleShader = null;
        blurShader = null;
        occlusionShader = null;
        compositeShader = null;
        try {
            disposeFrames();
        } finally {
            owned.dispose();
        }
    }
}
