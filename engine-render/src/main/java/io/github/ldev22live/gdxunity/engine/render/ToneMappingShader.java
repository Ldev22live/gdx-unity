package io.github.ldev22live.gdxunity.engine.render;

import io.github.ldev22live.gdxunity.engine.scene.lighting.ToneMappingComponent;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adds tone mapping to gdx-gltf's PBR fragment shader (TD-032). gdx-gltf 2.3.0 has no tone mapping of its own:
 * its lit path sums the light in linear space, adds emissive, then applies gamma. This inserts one line that maps
 * the linear color with the chosen operator between those two steps. Exposure is not part of the shader;
 * {@link SceneRenderer3D} applies it to the light colors.
 *
 * <p>Unlit materials are left alone. Pure string work, testable without a graphics context.
 */
public final class ToneMappingShader {

    /** End of the emissive block in the lit {@code main()} of {@code pbr.fs.glsl}; the mapping goes after it. */
    static final Pattern INSERTION_POINT = Pattern.compile(
        "#if defined\\(emissiveTextureFlag\\) \\|\\| defined\\(emissiveColorFlag\\)[ \\t]*\\r?\\n"
            + "[ \\t]*color \\+= emissive;[ \\t]*\\r?\\n"
            + "#endif[ \\t]*\\r?\\n");

    /** Reinhard: {@code c / (1 + c)}. */
    static final String REINHARD = "    color = color / (1.0 + color); // tone mapping: Reinhard\n";
    /** Narkowicz' fit of the ACES filmic reference curve. Constants are the published fit coefficients. */
    static final String ACES_FILMIC = "    color = clamp((color * (2.51 * color + 0.03)) / "
        + "(color * (2.43 * color + 0.59) + 0.14), 0.0, 1.0); // tone mapping: ACES filmic (Narkowicz)\n";

    private ToneMappingShader() {}

    /**
     * Returns {@code fragmentSource} with tone mapping for {@code operator} inserted; unchanged for
     * {@link ToneMappingComponent.Operator#NONE}.
     *
     * @throws IllegalStateException if the source does not contain the expected insertion point exactly once
     *                               (a different gdx-gltf version); nothing is guessed in that case
     */
    public static String apply(String fragmentSource, ToneMappingComponent.Operator operator) {
        Objects.requireNonNull(fragmentSource, "fragmentSource cannot be null");
        Objects.requireNonNull(operator, "operator cannot be null");
        if (operator == ToneMappingComponent.Operator.NONE) {
            return fragmentSource;
        }
        Matcher matcher = INSERTION_POINT.matcher(fragmentSource);
        if (!matcher.find()) {
            throw new IllegalStateException("PBR fragment shader has no emissive block to insert tone mapping after; "
                + "gdx-gltf's pbr.fs.glsl changed (expected the 2.3.0 layout)");
        }
        int end = matcher.end();
        if (matcher.find()) {
            throw new IllegalStateException("PBR fragment shader has more than one emissive block; "
                + "cannot tell where tone mapping belongs");
        }
        String line = operator == ToneMappingComponent.Operator.REINHARD ? REINHARD : ACES_FILMIC;
        return fragmentSource.substring(0, end) + line + fragmentSource.substring(end);
    }
}
