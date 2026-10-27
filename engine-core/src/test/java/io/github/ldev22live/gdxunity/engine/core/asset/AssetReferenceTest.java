package io.github.ldev22live.gdxunity.engine.core.asset;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AssetReferenceTest {

    @Test
    void testNormalizeSeparators() {
        AssetReference ref = new AssetReference("textures\\characters\\hero.png");
        assertEquals("textures/characters/hero.png", ref.getPath());
        assertEquals("hero.png", ref.getFileName());
        assertEquals("png", ref.getExtension());
    }

    @Test
    void testStripLeadingSlash() {
        AssetReference ref = new AssetReference("/textures/bg.jpg");
        assertEquals("textures/bg.jpg", ref.getPath());
        assertEquals("bg.jpg", ref.getFileName());
        assertEquals("jpg", ref.getExtension());
    }

    @Test
    void testRejectTraversal() {
        assertThrows(IllegalArgumentException.class, () -> new AssetReference("../secret.png"));
        assertThrows(IllegalArgumentException.class, () -> new AssetReference("textures/../../secret.png"));
        assertThrows(IllegalArgumentException.class, () -> new AssetReference("..\\secret.png"));
    }

    @Test
    void testRejectInvalidPaths() {
        assertThrows(NullPointerException.class, () -> new AssetReference(null));
        assertThrows(IllegalArgumentException.class, () -> new AssetReference(""));
        assertThrows(IllegalArgumentException.class, () -> new AssetReference("   "));
        assertThrows(IllegalArgumentException.class, () -> new AssetReference("C:/absolute/path.png"));
    }

    @Test
    void testEqualityAndHashCode() {
        AssetReference ref1 = new AssetReference("textures/hero.png");
        AssetReference ref2 = new AssetReference("textures\\hero.png");
        AssetReference ref3 = new AssetReference("textures/enemy.png");

        assertEquals(ref1, ref2);
        assertEquals(ref1.hashCode(), ref2.hashCode());
        assertNotEquals(ref1, ref3);
    }
}
