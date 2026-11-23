package io.github.ldev22live.gdxunity.engine.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonWriter;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.material.Material;
import io.github.ldev22live.gdxunity.engine.material.MaterialLibrary;
import io.github.ldev22live.gdxunity.engine.render.camera.PerspectiveCamera3D;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.MeshPrimitiveComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import net.mgsx.gltf.scene3d.attributes.PBRColorAttribute;
import net.mgsx.gltf.scene3d.attributes.PBRFloatAttribute;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Materials (TD-081) on Mesh Primitives: the attribute mapping, the renderer's choice between material and plain colour, and the file. */
class MaterialRenderingTest {
    @TempDir
    Path project;

    private SceneRenderer3D renderer;
    private MaterialLibrary library;

    @BeforeAll
    static void init() {
        GdxTestHelper.initNatives();
    }

    @BeforeEach
    void setUp() throws IOException {
        Files.createDirectories(project.resolve("materials"));
        library = new MaterialLibrary(AssetSource.directory(project.toFile()));
        renderer = new SceneRenderer3D(new PerspectiveCamera3D(60f, 800f, 600f, 1f, 100f), new Scene("S")) {
            @Override
            protected ModelBatch createModelBatch() {
                return null;
            }
        };
        renderer.setMaterialLibrary(library);
    }

    private static com.badlogic.gdx.graphics.g3d.Material surface() {
        com.badlogic.gdx.graphics.g3d.Material surface = new com.badlogic.gdx.graphics.g3d.Material();
        MaterialAttributes.reset(surface, SceneRenderer3D.PRIMITIVE_ROUGHNESS, false);
        return surface;
    }

    private void write(String name, String json) throws IOException {
        Files.writeString(project.resolve("materials/" + name), json);
    }

    private static MeshPrimitiveComponent primitive(String materialPath) {
        MeshPrimitiveComponent component = new MeshPrimitiveComponent();
        component.setMaterialPath(materialPath);
        return component;
    }

    @Test
    void shouldMapPbrParametersToShadingAttributesWithLinearColours() {
        Material material = new Material();
        material.setBaseColor(new Color(0.5f, 0.25f, 1f, 1f));
        material.setMetallic(0.8f);
        material.setRoughness(0.1f);
        com.badlogic.gdx.graphics.g3d.Material surface = surface();

        MaterialAttributes.apply(material, surface, SceneRenderer3D.DISPLAY_GAMMA, false);

        Color base = ((PBRColorAttribute) surface.get(PBRColorAttribute.BaseColorFactor)).color;
        assertEquals(Math.pow(0.5, 2.2), base.r, 1e-4, "sRGB is converted to linear light");
        assertEquals(1f, base.b, 1e-4);
        assertEquals(0.8f, ((PBRFloatAttribute) surface.get(PBRFloatAttribute.Metallic)).value);
        assertEquals(0.1f, ((PBRFloatAttribute) surface.get(PBRFloatAttribute.Roughness)).value);
        assertFalse(surface.has(ColorAttribute.Emissive), "black emissive is nothing");
        assertFalse(surface.has(BlendingAttribute.Type));
        assertEquals(GL20.GL_BACK, ((IntAttribute) surface.get(IntAttribute.CullFace)).value);
    }

    @Test
    void shouldGlowByEmissiveColourTimesStrengthAndStopWhenItIsRemoved() {
        Material material = new Material();
        material.setEmissive(new Color(1f, 0f, 0f, 1f));
        material.setEmissiveStrength(3f);
        com.badlogic.gdx.graphics.g3d.Material surface = surface();

        MaterialAttributes.apply(material, surface, SceneRenderer3D.DISPLAY_GAMMA, false);
        assertEquals(3f, ((ColorAttribute) surface.get(ColorAttribute.Emissive)).color.r, 1e-4);

        material.setEmissiveStrength(0f);
        MaterialAttributes.apply(material, surface, SceneRenderer3D.DISPLAY_GAMMA, false);
        assertFalse(surface.has(ColorAttribute.Emissive));
    }

    @Test
    void shouldBlendWithTheBaseColourAlphaMaskByCutoffAndIgnoreAlphaWhenOpaque() {
        Material material = new Material();
        material.setBaseColor(new Color(1f, 1f, 1f, 0.4f));
        com.badlogic.gdx.graphics.g3d.Material surface = surface();

        material.setAlphaMode(Material.AlphaMode.BLEND);
        MaterialAttributes.apply(material, surface, SceneRenderer3D.DISPLAY_GAMMA, false);
        assertEquals(0.4f, ((BlendingAttribute) surface.get(BlendingAttribute.Type)).opacity, 1e-4);
        assertEquals(0.4f, ((PBRColorAttribute) surface.get(PBRColorAttribute.BaseColorFactor)).color.a, 1e-4);

        material.setAlphaMode(Material.AlphaMode.MASK);
        material.setAlphaCutoff(0.3f);
        MaterialAttributes.apply(material, surface, SceneRenderer3D.DISPLAY_GAMMA, false);
        assertFalse(surface.has(BlendingAttribute.Type), "a mask is not blended");
        assertEquals(0.3f, ((FloatAttribute) surface.get(FloatAttribute.AlphaTest)).value);

        material.setAlphaMode(Material.AlphaMode.OPAQUE);
        MaterialAttributes.apply(material, surface, SceneRenderer3D.DISPLAY_GAMMA, false);
        assertFalse(surface.has(BlendingAttribute.Type));
        assertFalse(surface.has(FloatAttribute.AlphaTest));
        assertEquals(1f, ((PBRColorAttribute) surface.get(PBRColorAttribute.BaseColorFactor)).color.a, "opaque ignores alpha");
    }

    @Test
    void shouldDrawBothSidesForADoubleSidedMaterialOrAPlane() {
        Material material = new Material();
        com.badlogic.gdx.graphics.g3d.Material surface = surface();

        material.setDoubleSided(true);
        MaterialAttributes.apply(material, surface, SceneRenderer3D.DISPLAY_GAMMA, false);
        assertEquals(GL20.GL_NONE, ((IntAttribute) surface.get(IntAttribute.CullFace)).value);

        material.setDoubleSided(false);
        MaterialAttributes.apply(material, surface, SceneRenderer3D.DISPLAY_GAMMA, true);
        assertEquals(GL20.GL_NONE, ((IntAttribute) surface.get(IntAttribute.CullFace)).value, "a plane stays two-sided");
    }

    @Test
    void shouldLetTheMaterialDecideTheLookOfAPrimitiveThatNamesOne() throws IOException {
        write("gold.material", "{\"version\": 1, \"baseColor\": [1, 0.8, 0, 1], \"metallic\": 1, \"roughness\": 0.2}");
        com.badlogic.gdx.graphics.g3d.Material surface = surface();

        boolean decided = renderer.applyMaterial(primitive("materials/gold.material"), surface);

        assertTrue(decided);
        assertEquals(1f, ((PBRFloatAttribute) surface.get(PBRFloatAttribute.Metallic)).value);
        assertEquals(0.2f, ((PBRFloatAttribute) surface.get(PBRFloatAttribute.Roughness)).value);
    }

    @Test
    void shouldFollowEditsToASharedMaterialOnTheNextFrameForEveryUser() throws IOException {
        write("stone.material", "{\"version\": 1, \"roughness\": 0.9}");
        MeshPrimitiveComponent one = primitive("materials/stone.material");
        MeshPrimitiveComponent two = primitive("materials/stone.material");
        com.badlogic.gdx.graphics.g3d.Material first = surface();
        com.badlogic.gdx.graphics.g3d.Material second = surface();
        renderer.applyMaterial(one, first);
        renderer.applyMaterial(two, second);

        library.get("materials/stone.material").setRoughness(0.3f);
        renderer.applyMaterial(one, first);
        renderer.applyMaterial(two, second);

        assertEquals(0.3f, ((PBRFloatAttribute) first.get(PBRFloatAttribute.Roughness)).value);
        assertEquals(0.3f, ((PBRFloatAttribute) second.get(PBRFloatAttribute.Roughness)).value);
    }

    @Test
    void shouldKeepThePlainColourWhenThereIsNoMaterialOrItsFileIsMissingAndRestoreItWhenTheMaterialIsRemoved() throws IOException {
        write("shiny.material", "{\"version\": 1, \"metallic\": 1, \"emissive\": [1, 1, 1], \"alphaMode\": \"BLEND\"}");
        MeshPrimitiveComponent component = primitive("materials/shiny.material");
        com.badlogic.gdx.graphics.g3d.Material surface = surface();
        assertTrue(renderer.applyMaterial(component, surface));
        assertTrue(surface.has(ColorAttribute.Emissive));

        component.setMaterialPath("");
        assertFalse(renderer.applyMaterial(component, surface), "no material: the component's colour is used");
        assertEquals(0f, ((PBRFloatAttribute) surface.get(PBRFloatAttribute.Metallic)).value, "metal is gone");
        assertFalse(surface.has(ColorAttribute.Emissive), "glow is gone");
        assertFalse(surface.has(BlendingAttribute.Type), "blending is gone");

        component.setMaterialPath("materials/missing.material");
        assertFalse(renderer.applyMaterial(component, surface), "a missing file does not stop the primitive being drawn");
        assertNotNull(library.getError("materials/missing.material"));
    }

    @Test
    void shouldIgnoreMaterialPathsWhenThereIsNoLibrary() throws IOException {
        write("gold.material", "{\"version\": 1, \"metallic\": 1}");
        renderer.setMaterialLibrary(null);

        assertFalse(renderer.applyMaterial(primitive("materials/gold.material"), surface()));
    }

    @Test
    void shouldSaveTheMaterialPathOnlyWhenSetAndReadItBack() {
        MeshPrimitiveComponentSerializer serializer = new MeshPrimitiveComponentSerializer();
        MeshPrimitiveComponent with = primitive("materials/rock.material");
        MeshPrimitiveComponent without = new MeshPrimitiveComponent();

        String withJson = write(serializer, with);
        String withoutJson = write(serializer, without);

        assertTrue(withJson.contains("materials/rock.material"));
        assertFalse(withoutJson.contains("materialPath"), "scenes without materials do not change when saved again");
        Json json = new Json();
        assertEquals("materials/rock.material", serializer.deserialize(new JsonReader().parse(withJson), json).getMaterialPath());
        assertEquals("", serializer.deserialize(new JsonReader().parse(withoutJson), json).getMaterialPath());
    }

    private static String write(MeshPrimitiveComponentSerializer serializer, MeshPrimitiveComponent component) {
        Json json = new Json();
        StringWriter out = new StringWriter();
        json.setWriter(new JsonWriter(out));
        json.writeObjectStart();
        serializer.serialize(component, json);
        json.writeObjectEnd();
        return out.toString();
    }
}
