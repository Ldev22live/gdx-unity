package io.github.ldev22live.gdxunity.engine.material;

import com.badlogic.gdx.graphics.Color;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaterialIOTest {

    private static Material everything() {
        Material material = new Material();
        material.setName("Wet \"Rock\"\\ 1");
        material.setBaseColor(new Color(0.25f, 0.5f, 0.75f, 0.5f));
        material.setMetallic(0.3f);
        material.setRoughness(0.15f);
        material.setEmissive(new Color(1f, 0.5f, 0f, 1f));
        material.setEmissiveStrength(4f);
        material.setNormalScale(1.5f);
        material.setOcclusionStrength(0.8f);
        material.setAlphaMode(Material.AlphaMode.BLEND);
        material.setAlphaCutoff(0.25f);
        material.setDoubleSided(true);
        material.setTiling(2f, 3f);
        material.setOffset(0.5f, -0.25f);
        material.setTexture(Material.Slot.BASE_COLOR, "textures/rock.png");
        material.setTexture(Material.Slot.NORMAL, "textures/rock_n.png");
        return material;
    }

    @Test
    void shouldRoundTripEveryField() {
        Material original = everything();

        Material read = MaterialIO.fromJson(MaterialIO.toJson(original));

        assertEquals("Wet \"Rock\"\\ 1", read.getName());
        assertEquals(new Color(0.25f, 0.5f, 0.75f, 0.5f), read.getBaseColor());
        assertEquals(0.3f, read.getMetallic());
        assertEquals(0.15f, read.getRoughness());
        assertEquals(new Color(1f, 0.5f, 0f, 1f), read.getEmissive());
        assertEquals(4f, read.getEmissiveStrength());
        assertEquals(1.5f, read.getNormalScale());
        assertEquals(0.8f, read.getOcclusionStrength());
        assertEquals(Material.AlphaMode.BLEND, read.getAlphaMode());
        assertEquals(0.25f, read.getAlphaCutoff());
        assertTrue(read.isDoubleSided());
        assertEquals(2f, read.getTilingU());
        assertEquals(3f, read.getTilingV());
        assertEquals(0.5f, read.getOffsetU());
        assertEquals(-0.25f, read.getOffsetV());
        assertEquals("textures/rock.png", read.getTexture(Material.Slot.BASE_COLOR));
        assertEquals("textures/rock_n.png", read.getTexture(Material.Slot.NORMAL));
        assertEquals("", read.getTexture(Material.Slot.EMISSIVE));
    }

    @Test
    void shouldWriteTheSameTextForTheSameMaterial() {
        assertEquals(MaterialIO.toJson(everything()), MaterialIO.toJson(MaterialIO.fromJson(MaterialIO.toJson(everything()))));
    }

    @Test
    void shouldKeepDefaultsForFieldsAFileLeavesOut() {
        Material read = MaterialIO.fromJson("{\"version\": 1, \"metallic\": 0.9}");

        assertEquals(0.9f, read.getMetallic());
        assertEquals(Material.DEFAULT_ROUGHNESS, read.getRoughness());
        assertEquals(Color.WHITE, read.getBaseColor());
    }

    @Test
    void shouldRefuseFilesItDoesNotUnderstandAndNameTheProblem() {
        assertTrue(message("not json at all").startsWith("Not a material file"));
        assertTrue(message("[1, 2]").contains("expected a JSON object"));
        assertTrue(message("{\"metallic\": 1}").contains("no version"));
        assertTrue(message("{\"version\": 2}").contains("version 2 is not supported"));
        assertTrue(message("{\"version\": 1, \"metallic\": 3}").contains("metallic must be within 0.0 to 1.0"));
        assertTrue(message("{\"version\": 1, \"metallic\": \"high\"}").contains("metallic must be a number"));
        assertTrue(message("{\"version\": 1, \"baseColor\": [1, 1, 1]}").contains("baseColor must be a list of 4 numbers"));
        assertTrue(message("{\"version\": 1, \"alphaMode\": \"GLASS\"}").contains("alphaMode must be one of"));
        assertTrue(message("{\"version\": 1, \"textures\": {\"SHINY\": \"a.png\"}}").contains("unknown texture slot 'SHINY'"));
        assertTrue(message("{\"version\": 1, \"doubleSided\": \"yes\"}").contains("doubleSided must be true or false"));
    }

    private static String message(String json) {
        return assertThrows(MaterialFormatException.class, () -> MaterialIO.fromJson(json)).getMessage();
    }
}
