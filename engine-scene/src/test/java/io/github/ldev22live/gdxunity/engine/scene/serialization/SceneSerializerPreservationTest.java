package io.github.ldev22live.gdxunity.engine.scene.serialization;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.lighting.DirectionalLightComponent;
import io.github.ldev22live.gdxunity.engine.scene.lighting.serialization.DirectionalLightComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.scenario.OwnerComponent;
import io.github.ldev22live.gdxunity.engine.scene.scenario.OwnerComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Nothing in a scene file is dropped by a desktop load and save (TD-106, roadmap A12.1). */
class SceneSerializerPreservationTest {
    private static final String SUN = "11111111-1111-4111-8111-111111111111";
    private static final String HOUSE = "22222222-2222-4222-8222-222222222222";
    private static final String PLAYER_1 = "33333333-3333-4333-8333-333333333333";

    /** A scene with members this engine does not know at every level, in a file another client could have written. */
    private static final String FOREIGN = """
        {"schemaVersion": 1, "name": "Foreign", "gameMode": "3d", "active": true,
         "scenario": {"version": 1, "players": [{"id": "%3$s", "name": "P1", "color": "#2f6fdf"}], "objectives": [], "rules": []},
         "futureSetting": {"nested": {"list": [1, "two", null, {"deep": true}]}, "explicitNull": null},
         "entities": [
          {"id": "%1$s", "name": "Sun", "active": true, "parentId": null, "tags": ["sky", "light"],
           "transform": {"position": {"x": 0, "y": 800, "z": 0}, "rotation": {"x": 0, "y": 0, "z": 0, "w": 1},
                         "scale": {"x": 1, "y": 1, "z": 1}, "pivot": {"x": 0.5}},
           "components": [
            {"type": "DirectionalLightComponent", "enabled": true, "editorNote": "kept",
             "data": {"directionX": 0.0, "directionY": -1.0, "directionZ": 0.0, "colorR": 1.0, "colorG": 1.0, "colorB": 1.0,
                      "colorA": 1.0, "intensity": 1.0, "castShadows": true, "futureSoftness": 0.25, "futureNull": null}}]},
          {"id": "%2$s", "name": "House", "active": true, "parentId": null,
           "transform": {"position": {"x": 600, "y": 0, "z": -600}, "rotation": {"x": 0, "y": 0, "z": 0, "w": 1},
                         "scale": {"x": 1, "y": 1, "z": 1}},
           "components": [
            {"type": "OwnerComponent", "enabled": true, "data": {"player": "%3$s"}},
            {"type": "FutureComponent", "enabled": false, "data": {"text": "quote \\" and slash \\\\", "n": 3, "list": [], "obj": {}}},
            {"type": "DataLessFutureComponent", "enabled": true}]}]}
        """.formatted(SUN, HOUSE, PLAYER_1);

    private final SceneSerializer serializer = new SceneSerializer(registry());

    private static ComponentSerializerRegistry registry() {
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        registry.register(new DirectionalLightComponentSerializer());
        registry.register(new OwnerComponentSerializer());
        return registry;
    }

    private List<SceneSemanticDiff.Difference> lossesAfterRoundTrip(String original, Scene scene) {
        return new SceneSemanticDiff(SceneSemanticDiff.DESKTOP).compare(original, serializer.toJson(scene));
    }

    @Test
    void shouldKeepEveryUnknownMemberAtEveryLevelThroughLoadAndSave() {
        Scene scene = serializer.fromJson(FOREIGN);

        assertEquals(List.of(), lossesAfterRoundTrip(FOREIGN, scene));
        JsonValue saved = new JsonReader().parse(serializer.toJson(scene));
        assertTrue(saved.get("futureSetting").has("explicitNull") && saved.get("futureSetting").get("explicitNull").isNull(),
            "an explicit null stays an explicit null");
        assertEquals(4, saved.get("futureSetting").get("nested").get("list").size, "arrays with mixed values survive");
        JsonValue house = saved.get("entities").get(1);
        assertEquals("quote \" and slash \\", house.get("components").get(1).get("data").getString("text"),
            "escaped strings survive");
        assertTrue(house.get("components").get(2).get("data") == null, "an entry without data is written without data");
    }

    @Test
    void shouldKeepAnUnknownComponentTypeAsAnInertPreservedComponent() {
        Scene scene = serializer.fromJson(FOREIGN);
        Entity house = scene.findEntityById(UUID.fromString(HOUSE));

        List<PreservedComponent> preserved = house.getComponents(PreservedComponent.class);

        assertEquals(List.of("FutureComponent", "DataLessFutureComponent"), preserved.stream().map(PreservedComponent::getTypeId).toList());
        assertEquals(false, preserved.get(0).isEnabled(), "its enabled flag survives");
        assertInstanceOf(OwnerComponent.class, house.getComponent(OwnerComponent.class));
        assertEquals(PLAYER_1, house.getComponent(OwnerComponent.class).getPlayerId());
    }

    @Test
    void shouldKeepUnknownFieldsWhenAnAdjacentKnownFieldIsEdited() {
        Scene scene = serializer.fromJson(FOREIGN);
        DirectionalLightComponent sun = scene.findEntityById(UUID.fromString(SUN)).getComponent(DirectionalLightComponent.class);

        sun.setIntensity(2.5f);
        scene.findEntityById(UUID.fromString(SUN)).getTransform().setLocalPosition(10f, 900f, 0f);
        JsonValue savedSun = new JsonReader().parse(serializer.toJson(scene)).get("entities").get(0);

        JsonValue data = savedSun.get("components").get(0).get("data");
        assertEquals(2.5f, data.getFloat("intensity"), "the edit is saved");
        assertEquals(0.25f, data.getFloat("futureSoftness"), "the unknown field next to it survives");
        assertTrue(data.get("futureNull").isNull());
        assertEquals("kept", savedSun.get("components").get(0).getString("editorNote"), "an unknown entry member survives");
        assertEquals(900f, savedSun.get("transform").get("position").getFloat("y"));
        assertEquals(0.5f, savedSun.get("transform").get("pivot").getFloat("x"), "an unknown transform member survives the move");
        assertEquals(2, savedSun.get("tags").size, "an unknown entity member survives");
    }

    @Test
    void shouldLetAnEditedKnownFieldWinOverAPreservedFieldOfTheSameName() {
        // castShadows is written by the serializer, so it is never preserved; an unknown member that later becomes a known one
        // must not be written twice either
        Scene scene = serializer.fromJson(FOREIGN);
        DirectionalLightComponent sun = scene.findEntityById(UUID.fromString(SUN)).getComponent(DirectionalLightComponent.class);
        sun.setPreservedData(io.github.ldev22live.gdxunity.engine.scene.PreservedFields.of(
            java.util.Map.of("castShadows", "true", "futureSoftness", "0.25")));
        sun.setCastShadows(false);

        String saved = serializer.toJson(scene);

        JsonValue data = new JsonReader().parse(saved).get("entities").get(0).get("components").get(0).get("data");
        assertEquals(false, data.getBoolean("castShadows"), "the editor's value is saved, not the preserved one");
        int count = 0;
        for (JsonValue member = data.child; member != null; member = member.next) {
            count += member.name.equals("castShadows") ? 1 : 0;
        }
        assertEquals(1, count, "never written twice");
    }

    @Test
    void shouldExposeTheScenarioBlockAndKeepItWhenNothingReadsIt() {
        Scene scene = serializer.fromJson(FOREIGN);

        assertNotNull(scene.getScenarioJson());
        assertEquals(1, new JsonReader().parse(scene.getScenarioJson()).getInt("version"));
        assertTrue(serializer.toJson(scene).contains("\"scenario\""));
    }

    @Test
    void shouldRefuseAComponentEntryWithoutAType() {
        String typeless = FOREIGN.replace("{\"type\": \"DataLessFutureComponent\", \"enabled\": true}", "{\"enabled\": true}");

        SceneSerializationException refused = assertThrows(SceneSerializationException.class, () -> serializer.fromJson(typeless));
        assertTrue(refused.getMessage().contains("no 'type'"), refused.getMessage());
    }

    @Test
    void shouldRefuseAnOwnerWithoutAPlayerUuid() {
        String broken = FOREIGN.replace("{\"player\": \"" + PLAYER_1 + "\"}", "{\"player\": \"Player 1\"}");

        assertThrows(SceneSerializationException.class, () -> serializer.fromJson(broken), "ownership is by stable id, never by name");
    }
}
