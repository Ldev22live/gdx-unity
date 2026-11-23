package io.github.ldev22live.gdxunity.editor.desktop.ui;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.PixmapPacker;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.utils.GdxNativesLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks the bundled Inter fonts (TD-021) load through FreeType and contain the glyphs the editor uses.
 * Glyphs are packed into an own PixmapPacker, so no GL context is needed.
 */
class EditorFontResourcesTest {

    private static final int TEST_FONT_SIZE = 14;

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    private static FileHandle resource(String path) throws URISyntaxException {
        URL url = EditorFontResourcesTest.class.getResource("/" + path);
        assertNotNull(url, "Missing font resource: " + path);
        return new FileHandle(new File(url.toURI()));
    }

    private static FreeTypeFontGenerator.FreeTypeBitmapFontData generate(String path) throws URISyntaxException {
        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(resource(path));
        PixmapPacker packer = new PixmapPacker(512, 512, Pixmap.Format.RGBA8888, 1, false);
        try {
            FreeTypeFontGenerator.FreeTypeFontParameter parameter = new FreeTypeFontGenerator.FreeTypeFontParameter();
            parameter.size = TEST_FONT_SIZE;
            parameter.packer = packer;
            parameter.characters = FreeTypeFontGenerator.DEFAULT_CHARS + "—…";
            return generator.generateData(parameter);
        } finally {
            packer.dispose();
            generator.dispose();
        }
    }

    @Test
    void shouldGenerateRegularAndSemiBoldWithEditorGlyphs() throws URISyntaxException {
        for (String path : new String[]{"fonts/Inter-Regular.ttf", "fonts/Inter-SemiBold.ttf"}) {
            FreeTypeFontGenerator.FreeTypeBitmapFontData data = generate(path);
            assertNotNull(data.getGlyph('A'), path + " should contain Latin letters");
            assertNotNull(data.getGlyph('—'), path + " should contain the em dash used in editor text");
            assertTrue(data.lineHeight > 0f);
        }
    }

    @Test
    void shouldShipTheFontLicenseAlongsideTheFonts() {
        assertNotNull(EditorFontResourcesTest.class.getResource("/fonts/Inter-OFL-LICENSE.txt"),
            "The SIL Open Font License must ship with the bundled fonts");
    }
}
