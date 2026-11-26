package io.github.ldev22live.gdxunity.editor.sprite;

import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteDocument;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteFile;
import io.github.ldev22live.gdxunity.engine.sprite.SpriteLayer;
import io.github.ldev22live.gdxunity.engine.vector.VectorFile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The MC.3 2D house fixture ({@code fixtures/2d-sources/house}), a ForgeRoom original: {@code house.vector} is the source, drawn by
 * hand as path data; {@code house.sprite} is made from it here, one layer per part (Wall with its door, Roof, Window), each linked to
 * its shapes. The test fails when the committed files are not what this code makes from the drawing. Set the environment variable
 * {@code FORGEROOM_WRITE_FIXTURES=1} to write them (and the manifest's checksums) instead.
 */
class TwoDHouseFixtureTest {
    /** Tests run in the module folder. */
    static final Path FIXTURE = Path.of("..", "fixtures", "2d-sources", "house");
    /** Where the drawing lives in a project; the sprite's layer links name it. */
    static final String VECTOR_PATH = "vectors/house.vector";
    /** The sprite's layers, bottom to top, and the shapes drawn into each. */
    static final List<List<String>> LAYERS = List.of(List.of("Wall", "Wall", "Door"), List.of("Roof", "Roof"),
        List.of("Window", "Window"));
    private static final int SIZE = 48;

    @TempDir
    Path project;

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    @Test
    void shouldMatchWhatTheDrawingMakesAndTheManifestChecksums() throws Exception {
        String vectorText = VectorFile.write(VectorFile.read(Files.readString(FIXTURE.resolve("house.vector"), StandardCharsets.UTF_8)));
        Files.createDirectories(project.resolve("vectors"));
        Files.writeString(project.resolve(VECTOR_PATH), vectorText, StandardCharsets.UTF_8);

        SpriteSession sprite = new SpriteSession(new SpriteDocument(SIZE, SIZE));
        SpriteVectorSources sources = new SpriteVectorSources(sprite, project);
        for (int layer = 0; layer < LAYERS.size(); layer++) {
            List<String> spec = LAYERS.get(layer);
            if (layer > 0) {
                sprite.addLayer(spec.get(0));
            } else {
                sprite.renameLayer(0, spec.get(0));
            }
            sources.link(layer, VECTOR_PATH, spec.subList(1, spec.size()));
        }
        SpriteDocument doc = sprite.getDocument();
        for (int layer = 0; layer < LAYERS.size(); layer++) {
            SpriteLayer made = doc.getLayer(layer);
            assertEquals(LAYERS.get(layer).get(0), made.name());
            assertFalse(doc.isCelEmpty(layer, 0), made.name() + " is drawn");
            assertFalse(sources.isPaintedOver(layer));
        }
        String spriteText = SpriteFile.write(doc);

        if ("1".equals(System.getenv("FORGEROOM_WRITE_FIXTURES"))) {
            Files.writeString(FIXTURE.resolve("house.vector"), vectorText, StandardCharsets.UTF_8);
            Files.writeString(FIXTURE.resolve("house.sprite"), spriteText, StandardCharsets.UTF_8);
            Files.writeString(FIXTURE.resolve("manifest.json"), manifest(vectorText, spriteText), StandardCharsets.UTF_8);
        }
        assertEquals(vectorText, Files.readString(FIXTURE.resolve("house.vector"), StandardCharsets.UTF_8),
            "house.vector is in the form the editor saves");
        assertEquals(spriteText, Files.readString(FIXTURE.resolve("house.sprite"), StandardCharsets.UTF_8),
            "house.sprite is what the drawing makes");
        JsonValue outputs = new JsonReader().parse(Files.readString(FIXTURE.resolve("manifest.json"))).get("outputs");
        assertEquals(sha256(vectorText), outputs.getString("house.vector"));
        assertEquals(sha256(spriteText), outputs.getString("house.sprite"));
    }

    private static String manifest(String vectorText, String spriteText) throws NoSuchAlgorithmException {
        return "{\n"
            + "  \"schemaVersion\": 1,\n"
            + "  \"origin\": \"ForgeRoom original, made in this repository for roadmap MC.3 (TD-104)\",\n"
            + "  \"licence\": \"Apache-2.0\",\n"
            + "  \"madeWith\": {\n"
            + "    \"house.vector\": \"by hand: SVG path data in the .vector format\",\n"
            + "    \"house.sprite\": \"TwoDHouseFixtureTest (editor-sprite): each layer drawn from house.vector by SpriteVectorSources.link\"\n"
            + "  },\n"
            + "  \"projectPaths\": {\n"
            + "    \"house.vector\": \"" + VECTOR_PATH + "\",\n"
            + "    \"house.sprite\": \"sprites/house.sprite\"\n"
            + "  },\n"
            + "  \"outputs\": {\n"
            + "    \"house.vector\": \"" + sha256(vectorText) + "\",\n"
            + "    \"house.sprite\": \"" + sha256(spriteText) + "\"\n"
            + "  }\n"
            + "}\n";
    }

    static String sha256(String text) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }
}
