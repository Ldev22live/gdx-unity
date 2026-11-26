package io.github.ldev22live.gdxunity.engine.desktop;

import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.render.components.TextComponent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetCreditsTest {
    @Test
    void shouldShowAttributedAssetsWithSourceLicenceAndChanges() {
        var credits = AssetCredits.from(AssetSource.directory(new File("../assets")));
        assertNotNull(credits);
        String text = credits.getAllEntities().getFirst().getComponent(TextComponent.class).getText();
        assertTrue(text.contains("Warrior low poly by jjmoser"));
        assertTrue(text.contains("Thin Zombie by Rosswet Mobile"));
        assertTrue(text.contains("Horror sci-fi 3d map kit by Rosswet Mobile"));
        assertTrue(text.contains("https://opengameart.org/content/warrior-low-poly"));
        assertTrue(text.contains("https://creativecommons.org/licenses/by/3.0/"));
        assertTrue(text.contains("Changes: Converted room and cover to GLB"));
    }

    @Test
    void shouldOmitCreditsWhenThereIsNoManifest(@TempDir Path project) {
        assertNull(AssetCredits.from(AssetSource.directory(project.toFile())));
    }

    @Test
    void shouldCreditBothUpgradedScenesInTheirEditableHud() throws IOException {
        GdxNativesLoader.load();
        var project = AssetSource.directory(new File("../assets"));
        var range = GameSession.loadScene(project, "samples/first-person-range-3d.json");
        var courtyard = GameSession.loadScene(project, "samples/action-rpg-iso-25d.json");
        assertTrue(range.findEntityByName("Asset Credits").getComponent(TextComponent.class)
            .getText().contains("Rosswet Mobile (CC BY 3.0)"));
        assertTrue(courtyard.findEntityByName("Asset Credits").getComponent(TextComponent.class)
            .getText().contains("jjmoser (CC BY 3.0)"));
    }
}
