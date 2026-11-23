package io.github.ldev22live.gdxunity.engine.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebLauncherTest {
    @Test
    void shouldPlayNamedSceneWhenQueryHasSceneParameter() {
        assertEquals("samples/hopper-3d.json", WebLauncher.sceneFromQuery("?scene=samples/hopper-3d.json"));
    }

    @Test
    void shouldFindSceneParameterAmongOtherParameters() {
        assertEquals("samples/breakout-2d.json", WebLauncher.sceneFromQuery("?debug=1&scene=samples/breakout-2d.json&x=2"));
    }

    @Test
    void shouldPlayDefaultSceneWhenQueryIsEmptyOrMissing() {
        assertEquals(WebLauncher.DEFAULT_SCENE, WebLauncher.sceneFromQuery(""));
        assertEquals(WebLauncher.DEFAULT_SCENE, WebLauncher.sceneFromQuery(null));
        assertEquals(WebLauncher.DEFAULT_SCENE, WebLauncher.sceneFromQuery("?other=1"));
    }

    @Test
    void shouldPlayDefaultSceneWhenSceneParameterIsEmpty() {
        assertEquals(WebLauncher.DEFAULT_SCENE, WebLauncher.sceneFromQuery("?scene="));
    }

    @Test
    void shouldDecodeAnEncodedScenePathAsHostPagesWriteIt() {
        // encodeURIComponent("samples/breakout-2d.json")
        assertEquals("samples/breakout-2d.json", WebLauncher.sceneFromQuery("?scene=samples%2Fbreakout-2d.json"));
        assertEquals("my levels/café.json", WebLauncher.sceneFromQuery("?scene=my+levels%2Fcaf%C3%A9.json"));
    }

    @Test
    void shouldRejectMalformedPercentEscapesWithTheValue() {
        for (String bad : new String[] {"?scene=a%2", "?scene=a%zz.json", "?scene=%"}) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> WebLauncher.sceneFromQuery(bad));
            assertTrue(error.getMessage().contains(bad.substring("?scene=".length())), error.getMessage());
        }
    }
}
