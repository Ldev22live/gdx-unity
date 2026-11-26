package io.github.ldev22live.gdxunity.engine.desktop;

import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.material.Material;
import io.github.ldev22live.gdxunity.engine.material.MaterialLibrary;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.environment.AtmosphereComponent;
import io.github.ldev22live.gdxunity.engine.scene.environment.AtmospherePreset;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SampleEnvironmentMaterialsTest {
    private final AssetSource project = AssetSource.directory(new File("../assets"));

    @BeforeAll
    static void initNatives() {
        GdxNativesLoader.load();
    }

    private record Surface(String scene, String entity, String material) {
    }

    @Test
    void shouldLoadTheClearDaySkyInTheActionRpgValley() throws IOException {
        Scene valley = GameSession.loadScene(project, "samples/action-rpg-valley-3d.json");
        var atmosphere = valley.findEntityByName("Sky Light").getComponent(AtmosphereComponent.class);
        assertNotNull(atmosphere);
        assertTrue(AtmospherePreset.CLEAR_DAY.matches(atmosphere));
    }

    @Test
    void shouldUseThePaverMapsOnSelectedPlayableSurfaces() throws IOException {
        String path = "materials/HexPaverB.material";
        for (Surface surface : List.of(
            new Surface("samples/platformer-profile-3d.json", "Start Platform", "HexPaverB"),
            new Surface("samples/platformer-profile-terrain-25d.json", "Low Platform", "HexPaverB"),
            new Surface("samples/platformer-profile-terrain-25d.json", "Step Platform", "HexPaverB"),
            new Surface("samples/first-person-range-3d.json", "Floor", "HexPaverBRange"))) {
            Scene scene = GameSession.loadScene(project, surface.scene());
            assertEquals("materials/" + surface.material() + ".material", scene.findEntityByName(surface.entity())
                .getComponent(MeshPrimitiveComponent.class).getMaterialPath());
        }
        Material material = new MaterialLibrary(project).get(path);
        assertNotNull(material);
        assertEquals(0f, material.getMetallic());
        for (Material.Slot channel : List.of(Material.Slot.BASE_COLOR, Material.Slot.NORMAL, Material.Slot.OCCLUSION)) {
            String texture = material.getTexture(channel);
            assertNotNull(texture, channel.name());
            assertTrue(project.resolve(new AssetReference(texture)).exists(), texture);
        }
    }

    @Test
    void shouldResolveTexturedEnvironmentMaterialsForPrioritySamples() throws IOException {
        var materials = new MaterialLibrary(project);
        for (Surface surface : List.of(
            new Surface("samples/action-rpg-valley-3d.json", "Crate", "WoodFloorDeck"),
            new Surface("samples/action-rpg-iso-25d.json", "East Wall", "RedBrick03"),
            new Surface("samples/platformer-profile-terrain-25d.json", "High Platform", "WoodFloorDeck"),
            new Surface("samples/platformer-profile-3d.json", "Stone 1", "RockyTerrain02"))) {
            Scene sample = GameSession.loadScene(project, surface.scene());
            String path = sample.findEntityByName(surface.entity()).getComponent(MeshPrimitiveComponent.class).getMaterialPath();
            assertEquals("materials/" + surface.material() + ".material", path);
            var material = materials.get(path);
            assertNotNull(material);
            for (Material.Slot channel : List.of(Material.Slot.BASE_COLOR, Material.Slot.NORMAL, Material.Slot.METALLIC_ROUGHNESS)) {
                String texture = material.getTexture(channel);
                assertNotNull(texture, channel.name());
                assertTrue(project.resolve(new AssetReference(texture)).exists(), texture);
            }
        }
    }
}
