package io.github.ldev22live.gdxunity.engine.render;

import io.github.ldev22live.gdxunity.engine.scene.lighting.ToneMappingComponent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ToneMappingShaderTest {
    /** gdx-gltf 2.3.0's lit PBR fragment shader, read from its jar on the test class path. */
    private static String pbrFragmentShader;

    @BeforeAll
    static void loadShader() throws IOException {
        try (InputStream in = ToneMappingShaderTest.class.getClassLoader()
                .getResourceAsStream("net/mgsx/gltf/shaders/pbr/pbr.fs.glsl")) {
            assertNotNull(in, "gdx-gltf shader not on the class path");
            pbrFragmentShader = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void shouldLeaveShaderUnchangedWithoutToneMapping() {
        assertSame(pbrFragmentShader, ToneMappingShader.apply(pbrFragmentShader, ToneMappingComponent.Operator.NONE));
    }

    @Test
    void shouldInsertOperatorAfterEmissiveAndBeforeGammaInTheLitPath() {
        String shader = ToneMappingShader.apply(pbrFragmentShader, ToneMappingComponent.Operator.ACES_FILMIC);

        int mapping = shader.indexOf(ToneMappingShader.ACES_FILMIC);
        assertTrue(mapping > 0);
        assertEquals(mapping, shader.lastIndexOf(ToneMappingShader.ACES_FILMIC), "inserted once");
        assertTrue(shader.lastIndexOf("color += emissive;") < mapping, "after emissive");
        assertTrue(shader.lastIndexOf("pow(color,vec3(1.0/GAMMA_CORRECTION))") > mapping, "before gamma");
        assertEquals(pbrFragmentShader.length() + ToneMappingShader.ACES_FILMIC.length(), shader.length());
    }

    @Test
    void shouldInsertReinhardWhenRequested() {
        String shader = ToneMappingShader.apply(pbrFragmentShader, ToneMappingComponent.Operator.REINHARD);

        assertTrue(shader.contains(ToneMappingShader.REINHARD));
        assertFalse(shader.contains(ToneMappingShader.ACES_FILMIC));
    }

    @Test
    void shouldFailWhenInsertionPointIsMissingOrAmbiguous() {
        assertThrows(IllegalStateException.class,
            () -> ToneMappingShader.apply("void main() {}", ToneMappingComponent.Operator.REINHARD));

        String block = "#if defined(emissiveTextureFlag) || defined(emissiveColorFlag)\n    color += emissive;\n#endif\n";
        assertThrows(IllegalStateException.class,
            () -> ToneMappingShader.apply(block + block, ToneMappingComponent.Operator.REINHARD));
        assertDoesNotThrow(() -> ToneMappingShader.apply(block.replace("\n", "\r\n"), ToneMappingComponent.Operator.REINHARD));
    }
}
