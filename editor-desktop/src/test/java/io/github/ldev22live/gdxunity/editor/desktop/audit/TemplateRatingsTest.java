package io.github.ldev22live.gdxunity.editor.desktop.audit;

import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.editor.core.project.TemplateGallery;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The done-when of roadmap M1.7: the New Game gallery shows the right label for every template, driven by the matrix and not by hand.
 * The gallery's rating and the table in the feature matrix come from the same evaluation; a changed scene changes the label with no
 * other edit.
 */
class TemplateRatingsTest {
    private static final File ASSETS = new File("../assets");

    @TempDir
    Path temp;

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    @Test
    void shouldGiveEveryOfferedTemplateTheMatrixLabelOfItsSceneWithItsReasons() throws IOException {
        EditabilityAudit audit = TemplateMatrixTest.editorAudit();
        TemplateRatings ratings = new TemplateRatings(ASSETS, audit);
        Map<TemplateMatrix.Label, Integer> counts = new EnumMap<>(TemplateMatrix.Label.class);
        int samples = 0;
        for (TemplateGallery.Choice choice : TemplateGallery.choices(ASSETS)) {
            if (choice.kind() != TemplateGallery.Kind.SAMPLE) {
                continue;
            }
            samples++;
            File scene = new File(ASSETS, choice.scene());
            TemplateMatrix.Entry entry = TemplateMatrix.evaluate(ASSETS, scene, audit.audit(scene));
            TemplateRatings.Rating rating = ratings.rate(choice);
            assertEquals(entry.label(), rating.label(), choice.title());
            assertTrue(rating.headline().startsWith(rating.label().text() + ": "), rating.headline());
            assertFalse(rating.reasons().isEmpty(), choice.title() + ": every label says why");
            for (TemplateMatrix.Domain failed : entry.failures()) {
                assertTrue(rating.reasons().stream().anyMatch(r -> r.startsWith(failed.label() + ": ")), choice.title() + ": " + failed);
            }
            for (TemplateMatrix.Flattened item : entry.flattened()) {
                assertTrue(rating.reasons().contains("Flattened " + item.describe()), choice.title() + ": " + item);
            }
            counts.merge(rating.label(), 1, Integer::sum);
        }
        // 45 with the two MP.3 copies: the tiled platformer keeps its Java player (Partially Editable), the terrain one has no FAIL;
        // 46 with Platformer Profile 3D and 47 with First-person Range (M3b.7), made of shapes with no scripts
        // 48 with the Poly Haven Showcase (downloaded CC0 assets), Partially Editable: its glTF models have no mesh editor (R7a)
        assertEquals(48, samples);
        assertEquals(Map.of(TemplateMatrix.Label.INCOMPLETE, 7, TemplateMatrix.Label.PARTIALLY_EDITABLE, 22,
            TemplateMatrix.Label.CHECKS_PENDING, 19), counts, "no template is Fully Customizable while the gates are unchecked");
    }

    @Test
    void shouldChangeTheLabelWhenTheSceneChangesWithNoOtherEdit() throws IOException {
        Files.createDirectories(temp.resolve("samples"));
        Path scene = temp.resolve("samples/s.json");
        TemplateGallery.Choice choice = new TemplateGallery.Choice(TemplateGallery.Kind.SAMPLE, "S", "Test", GameMode.MODE_3D, "",
            "samples/s.json", null, 1, 1, false);
        TemplateRatings ratings = new TemplateRatings(temp.toFile(), TemplateMatrixTest.editorAudit());

        Files.writeString(scene, scene("{\"type\":\"DirectionalLightComponent\",\"enabled\":true,\"data\":{}}"));
        TemplateRatings.Rating lit = ratings.rate(choice);
        assertEquals(TemplateMatrix.Label.CHECKS_PENDING, lit.label());
        assertTrue(lit.reasons().get(0).startsWith("Not yet checked: "), lit.reasons().toString());

        Files.writeString(scene, scene("{\"type\":\"ModelComponent\",\"enabled\":true,\"data\":{\"model\":\"models/vale-terrain.gltf\"}}"));
        TemplateRatings.Rating baked = ratings.rate(choice);
        assertEquals(TemplateMatrix.Label.INCOMPLETE, baked.label());
        assertTrue(baked.reasons().contains("Flattened baked glTF ground (vale-terrain): no editable replacement in the template"),
            baked.reasons().toString());
    }

    @Test
    void shouldRefuseToRateAnEmptyStart() throws IOException {
        TemplateRatings ratings = new TemplateRatings(ASSETS, TemplateMatrixTest.editorAudit());
        TemplateGallery.Choice start = TemplateGallery.emptyStarts(ASSETS).get(0);
        assertThrows(IllegalArgumentException.class, () -> ratings.rate(start));
    }

    private static String scene(String component) {
        return "{\"schemaVersion\":1,\"name\":\"S\",\"gameMode\":\"3D\",\"entities\":[{\"id\":\"a\",\"name\":\"A\",\"active\":true,"
            + "\"parentId\":null,\"transform\":{},\"components\":[" + component + "]}]}";
    }
}
