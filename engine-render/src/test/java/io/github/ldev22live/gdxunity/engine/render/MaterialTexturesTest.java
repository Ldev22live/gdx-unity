package io.github.ldev22live.gdxunity.engine.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.material.Material;
import net.mgsx.gltf.scene3d.attributes.PBRFloatAttribute;
import net.mgsx.gltf.scene3d.attributes.PBRTextureAttribute;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Textures of materials (TD-081): loading from the project, and the texture attributes a material's slots become. Runs against a GL that
 * does nothing, so what is checked is the data handed to the shader (which texture, tiling, offset, strengths), not what is drawn.
 */
class MaterialTexturesTest {
    private static Graphics previousGraphics;

    @TempDir
    Path project;

    @BeforeAll
    static void init() {
        GdxTestHelper.initNatives();
        GltfTestFiles.installStubs();
        previousGraphics = Gdx.graphics;
        // Mip map generation asks the graphics for an extension; none is the plain path
        Gdx.graphics = (Graphics) Proxy.newProxyInstance(Graphics.class.getClassLoader(), new Class<?>[]{Graphics.class},
            (proxy, method, args) -> method.getReturnType() == boolean.class ? Boolean.FALSE : method.getReturnType() == int.class ? 1 : null);
    }

    @AfterAll
    static void tearDown() {
        Gdx.graphics = previousGraphics;
        GltfTestFiles.removeStubs();
    }

    private void writePng(String name) throws IOException {
        Files.createDirectories(project.resolve("textures"));
        Pixmap pixmap = new Pixmap(4, 4, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.RED);
        pixmap.fill();
        PixmapIO.writePNG(new FileHandle(project.resolve("textures/" + name).toFile()), pixmap);
        pixmap.dispose();
    }

    private static Texture texture() {
        Pixmap pixmap = new Pixmap(2, 2, Pixmap.Format.RGBA8888);
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return texture;
    }

    @Test
    void shouldGiveEveryPrimitiveShapeTextureCoordinatesSoTexturesCanBeDrawn() {
        SceneRenderer3D renderer = new SceneRenderer3D();
        for (com.badlogic.gdx.graphics.g3d.Model model : new com.badlogic.gdx.graphics.g3d.Model[]{
            renderer.createUnitModel(io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent.Shape.BOX),
            renderer.createUnitModel(io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent.Shape.SPHERE),
            renderer.createUnitModel(io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent.Shape.PLANE)}) {
            assertNotNull(model.meshes.first().getVertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.TextureCoordinates));
            model.dispose();
        }
    }

    @Test
    void shouldLoadATextureOnceAndShareItAndRepeatIt() throws IOException {
        writePng("rock.png");
        MaterialTextures textures = new MaterialTextures(AssetSource.directory(project.toFile()));

        Texture first = textures.get("textures/rock.png");
        Texture again = textures.get("textures\\rock.png");

        assertNotNull(first);
        assertSame(first, again, "one texture per file, whatever the slash");
        assertEquals(Texture.TextureWrap.Repeat, first.getUWrap(), "tiling needs repeating textures");
        assertEquals(Texture.TextureWrap.Repeat, first.getVWrap());
        assertEquals(1, textures.size());
        assertNull(textures.getError("textures/rock.png"));
        textures.dispose();
        assertEquals(0, textures.size());
    }

    @Test
    void shouldGiveNoTextureAndAReasonForAMissingFileAndForOneThatIsNotAnImage() throws IOException {
        Files.createDirectories(project.resolve("textures"));
        Files.writeString(project.resolve("textures/notes.png"), "this is not an image");
        MaterialTextures textures = new MaterialTextures(AssetSource.directory(project.toFile()));

        assertNull(textures.get("textures/missing.png"));
        assertTrue(textures.getError("textures/missing.png").contains("not found"));
        assertNull(textures.get("textures/notes.png"));
        assertNotNull(textures.getError("textures/notes.png"));
        assertEquals(0, textures.size());
    }

    @Test
    void shouldTryAgainAfterAFailureBecauseTheFileMayHaveBeenAddedSince() throws IOException {
        MaterialTextures textures = new MaterialTextures(AssetSource.directory(project.toFile()));
        assertNull(textures.get("textures/late.png"));

        writePng("late.png");

        assertNotNull(textures.get("textures/late.png"));
        assertNull(textures.getError("textures/late.png"));
    }

    @Test
    void shouldTurnFilledSlotsIntoTextureAttributesWithTilingAndOffset() {
        Material material = new Material();
        material.setTexture(Material.Slot.BASE_COLOR, "textures/a.png");
        material.setTexture(Material.Slot.NORMAL, "textures/n.png");
        material.setNormalScale(2f);
        material.setTiling(3f, 4f);
        material.setOffset(0.25f, 0.5f);
        Texture a = texture();
        Texture n = texture();
        List<String> asked = new ArrayList<>();
        TextureSource source = path -> {
            asked.add(path);
            return path.equals("textures/a.png") ? a : n;
        };
        com.badlogic.gdx.graphics.g3d.Material surface = new com.badlogic.gdx.graphics.g3d.Material();

        MaterialAttributes.apply(material, surface, SceneRenderer3D.DISPLAY_GAMMA, false, source);

        PBRTextureAttribute base = (PBRTextureAttribute) surface.get(PBRTextureAttribute.BaseColorTexture);
        assertSame(a, base.textureDescription.texture);
        assertEquals(3f, base.scaleU);
        assertEquals(4f, base.scaleV);
        assertEquals(0.25f, base.offsetU);
        assertEquals(0.5f, base.offsetV);
        assertSame(n, ((PBRTextureAttribute) surface.get(PBRTextureAttribute.NormalTexture)).textureDescription.texture);
        assertEquals(2f, ((PBRFloatAttribute) surface.get(PBRFloatAttribute.NormalScale)).value);
        assertFalse(surface.has(PBRTextureAttribute.OcclusionTexture), "an empty slot is not asked for");
        assertFalse(surface.has(PBRFloatAttribute.OcclusionStrength));
        assertEquals(List.of("textures/a.png", "textures/n.png"), asked);
    }

    @Test
    void shouldDrawNoTextureForAnEmptySlotOneThatCannotBeReadOrAMaterialAppliedWithoutASource() {
        Material material = new Material();
        material.setTexture(Material.Slot.BASE_COLOR, "textures/gone.png");
        com.badlogic.gdx.graphics.g3d.Material surface = new com.badlogic.gdx.graphics.g3d.Material();
        surface.set(PBRTextureAttribute.createBaseColorTexture(texture()));

        MaterialAttributes.apply(material, surface, SceneRenderer3D.DISPLAY_GAMMA, false, path -> null);
        assertFalse(surface.has(PBRTextureAttribute.BaseColorTexture), "an unreadable texture leaves the slot empty");

        surface.set(PBRTextureAttribute.createBaseColorTexture(texture()));
        MaterialAttributes.apply(material, surface, SceneRenderer3D.DISPLAY_GAMMA, false);
        assertFalse(surface.has(PBRTextureAttribute.BaseColorTexture), "without a source textures are not drawn");
    }

    @Test
    void shouldRemoveTheTextureWhenTheSlotIsEmptiedAndOnReset() {
        Material material = new Material();
        material.setTexture(Material.Slot.OCCLUSION, "textures/ao.png");
        material.setOcclusionStrength(0.4f);
        com.badlogic.gdx.graphics.g3d.Material surface = new com.badlogic.gdx.graphics.g3d.Material();
        MaterialAttributes.apply(material, surface, SceneRenderer3D.DISPLAY_GAMMA, false, path -> texture());
        assertEquals(0.4f, ((PBRFloatAttribute) surface.get(PBRFloatAttribute.OcclusionStrength)).value);

        material.setTexture(Material.Slot.OCCLUSION, "");
        MaterialAttributes.apply(material, surface, SceneRenderer3D.DISPLAY_GAMMA, false, path -> texture());
        assertFalse(surface.has(PBRTextureAttribute.OcclusionTexture));
        assertFalse(surface.has(PBRFloatAttribute.OcclusionStrength));

        material.setTexture(Material.Slot.BASE_COLOR, "textures/a.png");
        MaterialAttributes.apply(material, surface, SceneRenderer3D.DISPLAY_GAMMA, false, path -> texture());
        MaterialAttributes.reset(surface, SceneRenderer3D.PRIMITIVE_ROUGHNESS, false);
        assertFalse(surface.has(PBRTextureAttribute.BaseColorTexture), "a primitive without a material shows no texture");
    }

    @Test
    void shouldGlowByTheEmissiveTextureAloneWhenTheEmissiveColourIsBlack() {
        Material material = new Material();
        material.setTexture(Material.Slot.EMISSIVE, "textures/glow.png");
        material.setEmissiveStrength(2f);
        com.badlogic.gdx.graphics.g3d.Material surface = new com.badlogic.gdx.graphics.g3d.Material();

        MaterialAttributes.apply(material, surface, SceneRenderer3D.DISPLAY_GAMMA, false, path -> texture());
        assertEquals(2f, ((ColorAttribute) surface.get(ColorAttribute.Emissive)).color.r, 1e-4);

        MaterialAttributes.apply(material, surface, SceneRenderer3D.DISPLAY_GAMMA, false, path -> null);
        assertFalse(surface.has(ColorAttribute.Emissive), "no texture, no glow from nothing");
    }
}
