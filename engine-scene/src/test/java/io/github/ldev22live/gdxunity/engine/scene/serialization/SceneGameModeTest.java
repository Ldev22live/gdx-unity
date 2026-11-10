package io.github.ldev22live.gdxunity.engine.scene.serialization;

import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SceneGameModeTest {
    private final SceneSerializer serializer = new SceneSerializer(new ComponentSerializerRegistry());

    @Test
    void shouldRoundTripEveryGameModeThroughTheSceneFile() {
        for (GameMode mode : GameMode.values()) {
            Scene scene = new Scene("S");
            scene.setGameMode(mode);

            Scene back = serializer.fromJson(serializer.toJson(scene));

            assertEquals(mode, back.getGameMode());
        }
    }

    @Test
    void shouldWriteNoGameModeForASceneThatHasNoneAndReadOldFilesWithoutOne() {
        String json = serializer.toJson(new Scene("Old"));

        assertFalse(json.contains("gameMode"), json);
        assertNull(serializer.fromJson(json).getGameMode());
    }

    @Test
    void shouldRefuseAnUnknownGameModeValue() {
        String json = serializer.toJson(new Scene("S")).replace("\"entities\"", "\"gameMode\": \"4d\", \"entities\"");

        assertThrows(SceneSerializationException.class, () -> serializer.fromJson(json));
    }

    @Test
    void shouldKnowTheFileValueOfEachMode() {
        assertEquals("2.5d", GameMode.MODE_2_5D.getFileValue());
        assertEquals(GameMode.MODE_3D, GameMode.fromFileValue("3d"));
        assertNull(GameMode.fromFileValue("x"));
        assertTrue(GameMode.MODE_2D.getLabel().contains("2D"));
    }
}
