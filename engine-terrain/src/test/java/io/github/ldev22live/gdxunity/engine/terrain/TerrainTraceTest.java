package io.github.ldev22live.gdxunity.engine.terrain;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The desktop half of the shared terrain trace (roadmap A14): {@code fixtures/contract-v1/terrain-trace/strokes.json} is a sequence
 * of strokes with every brush type on a flat terrain; this engine's {@link TerrainStroke} result, written by {@link TerrainIO#toJson},
 * is {@code expected.terrain} ({@code GDXUNITY_UPDATE_TRACE=1} rewrites it; otherwise this run must reproduce it byte for byte). The
 * web's {@code engine-babylon/test/terrainTrace.test.ts} replays the same strokes and must give the same terrain.
 */
class TerrainTraceTest {
    private static final Path DIR = Path.of("..", "fixtures", "contract-v1", "terrain-trace");

    @Test
    void shouldReplayTheSharedStrokesAndReproduceTheRecordedTerrain() throws IOException {
        JsonValue trace = new JsonReader().parse(Files.readString(DIR.resolve("strokes.json")));
        int resolution = trace.getInt("resolution");
        Terrain terrain = new Terrain(resolution, resolution, trace.getFloat("cellSize"));
        terrain.setName("Terrain Trace");
        for (JsonValue stroke = trace.get("strokes").child; stroke != null; stroke = stroke.next) {
            TerrainBrush brush = new TerrainBrush();
            brush.setType(TerrainBrush.Type.valueOf(stroke.getString("type")));
            brush.setRadius(stroke.getFloat("radius"));
            brush.setStrength(stroke.getFloat("strength"));
            brush.setInverted(stroke.getBoolean("inverted", false));
            if (stroke.has("surface")) {
                brush.setSurface(Surface.fromCode((byte) stroke.getInt("surface")));
            }
            TerrainStroke strokeOnTerrain = new TerrainStroke(terrain);
            JsonValue point = stroke.get("path").child;
            strokeOnTerrain.begin(brush, point.getFloat(0), point.getFloat(1));
            for (point = point.next; point != null; point = point.next) {
                strokeOnTerrain.move(point.getFloat(0), point.getFloat(1));
            }
            strokeOnTerrain.end();
        }

        String text = TerrainIO.toJson(terrain);
        Path file = DIR.resolve("expected.terrain");
        if ("1".equals(System.getenv("GDXUNITY_UPDATE_TRACE")) || !Files.exists(file)) {
            Files.writeString(file, text);
            return;
        }
        assertEquals(Files.readString(file).replace("\r\n", "\n"), text,
            "the desktop no longer reproduces expected.terrain; rerun with GDXUNITY_UPDATE_TRACE=1 and review");
    }
}
