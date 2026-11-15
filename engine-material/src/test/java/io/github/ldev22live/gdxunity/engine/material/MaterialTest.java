package io.github.ldev22live.gdxunity.engine.material;

import com.badlogic.gdx.graphics.Color;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaterialTest {

    @Test
    void shouldStartAsAMatteWhiteOpaqueSurface() {
        Material material = new Material();

        assertEquals(Color.WHITE, material.getBaseColor());
        assertEquals(0f, material.getMetallic());
        assertEquals(Material.DEFAULT_ROUGHNESS, material.getRoughness());
        assertEquals(Color.BLACK, material.getEmissive());
        assertEquals(Material.AlphaMode.OPAQUE, material.getAlphaMode());
        assertEquals("", material.getTexture(Material.Slot.NORMAL));
    }

    @Test
    void shouldRefuseValuesOutsideTheirRangeAndKeepTheOldValue() {
        Material material = new Material();
        material.setMetallic(0.4f);

        assertThrows(IllegalArgumentException.class, () -> material.setMetallic(1.1f));
        assertThrows(IllegalArgumentException.class, () -> material.setRoughness(-0.1f));
        assertThrows(IllegalArgumentException.class, () -> material.setRoughness(Float.NaN));
        Color overbright = new Color(Color.WHITE);
        overbright.r = 2f; // the constructor clamps, the field does not
        assertThrows(IllegalArgumentException.class, () -> material.setBaseColor(overbright));
        assertThrows(IllegalArgumentException.class, () -> material.setEmissiveStrength(Material.MAX_EMISSIVE_STRENGTH + 1f));
        assertThrows(IllegalArgumentException.class, () -> material.setTiling(0f, 1f));
        assertThrows(IllegalArgumentException.class, () -> material.setName("  "));
        assertThrows(NullPointerException.class, () -> material.setAlphaMode(null));
        assertEquals(0.4f, material.getMetallic());
    }

    @Test
    void shouldHandOutCopiesOfItsColoursSoCallersCannotChangeItBehindItsBack() {
        Material material = new Material();
        material.setBaseColor(new Color(0.2f, 0.4f, 0.6f, 1f));

        material.getBaseColor().set(Color.RED);

        assertEquals(0.2f, material.getBaseColor().r);
    }

    @Test
    void shouldCountEveryChangeInItsRevision() {
        Material material = new Material();
        int before = material.getRevision();

        material.setRoughness(0.2f);
        material.setTexture(Material.Slot.BASE_COLOR, "textures/a.png");

        assertEquals(before + 2, material.getRevision());
    }

    @Test
    void shouldEmptyATextureSlotWithABlankPath() {
        Material material = new Material();
        material.setTexture(Material.Slot.NORMAL, " textures/n.png ");
        assertEquals("textures/n.png", material.getTexture(Material.Slot.NORMAL));

        material.setTexture(Material.Slot.NORMAL, " ");

        assertEquals("", material.getTexture(Material.Slot.NORMAL));
    }

    @Test
    void shouldCopyEverythingAndShareNothing() {
        Material material = new Material();
        material.setName("Rock");
        material.setMetallic(0.1f);
        material.setAlphaMode(Material.AlphaMode.MASK);
        material.setDoubleSided(true);
        material.setTiling(3f, 4f);
        material.setTexture(Material.Slot.OCCLUSION, "textures/ao.png");

        Material copy = material.copy();
        material.setTexture(Material.Slot.OCCLUSION, "");

        assertNotSame(material, copy);
        assertEquals("Rock", copy.getName());
        assertEquals(0.1f, copy.getMetallic());
        assertEquals(Material.AlphaMode.MASK, copy.getAlphaMode());
        assertTrue(copy.isDoubleSided());
        assertEquals(4f, copy.getTilingV());
        assertEquals("textures/ao.png", copy.getTexture(Material.Slot.OCCLUSION));
    }
}
