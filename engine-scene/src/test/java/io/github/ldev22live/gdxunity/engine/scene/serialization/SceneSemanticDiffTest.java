package io.github.ldev22live.gdxunity.engine.scene.serialization;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The round-trip comparison allows only its documented normalizations (TD-106, roadmap A12.2). */
class SceneSemanticDiffTest {
    private static final String ENTITY = "{\"id\": \"e1\", \"name\": \"Sun\", \"components\": [%s]}";

    private static String scene(String top, String component) {
        return "{\"schemaVersion\": 1" + top + ", \"entities\": [" + ENTITY.formatted(component) + "]}";
    }

    private static List<String> differences(List<SceneSemanticDiff.Normalization> normalizations, String a, String b) {
        return new SceneSemanticDiff(normalizations).compare(a, b).stream().map(Object::toString).toList();
    }

    @Test
    void shouldIgnoreMemberOrderAndNumberTextOnly() {
        String a = scene(", \"name\": \"S\", \"x\": 1", "{\"type\": \"T\", \"data\": {\"a\": 1, \"b\": 0.9659}}");
        String b = "{\"x\": 1.0, \"entities\": [{\"components\": [{\"data\": {\"b\": 0.96590000, \"a\": 1.0}, \"type\": \"T\"}], "
            + "\"name\": \"Sun\", \"id\": \"e1\"}], \"name\": \"S\", \"schemaVersion\": 1}";

        assertEquals(List.of(), differences(List.of(), a, b));
    }

    @Test
    void shouldReportMissingAddedAndChangedWithTheirPaths() {
        String a = scene(", \"keep\": {\"n\": null}", "{\"type\": \"T\", \"data\": {\"a\": 1, \"gone\": \"x\"}}");
        String b = scene(", \"keep\": {}, \"extra\": 2", "{\"type\": \"T\", \"data\": {\"a\": 2}}");

        assertEquals(List.of(
            "MISSING keep.n was null",
            "ADDED extra now 2",
            "CHANGED entities[e1 Sun].components[T#0].data.a was 1 now 2",
            "MISSING entities[e1 Sun].components[T#0].data.gone was \"x\""), differences(List.of(), a, b));
    }

    @Test
    void shouldNeverTreatNullEmptyAndMissingAsTheSameUnlessAFieldIsNamed() {
        String withNull = scene("", "{\"type\": \"SpriteAnimatorComponent\", \"data\": {\"slice\": null, \"tag\": \"\"}}");
        String without = scene("", "{\"type\": \"SpriteAnimatorComponent\", \"data\": {}}");

        assertEquals(2, differences(List.of(), without, withNull).size(), "with no normalization both count");
        assertEquals(List.of("ADDED entities[e1 Sun].components[SpriteAnimatorComponent#0].data.tag now \"\""),
            differences(SceneSemanticDiff.DESKTOP, without, withNull), "only the named field (slice) and value (null) are allowed");
    }

    @Test
    void shouldCompareSixteenBitMasksAndDirectionsOnlyForTheNamedFields() {
        String a = scene("", "{\"type\": \"BoxCollider2DComponent\", \"data\": {\"maskBits\": 65535, \"group\": 65535}}");
        String b = scene("", "{\"type\": \"BoxCollider2DComponent\", \"data\": {\"maskBits\": -1, \"group\": -1}}");
        assertEquals(List.of("CHANGED entities[e1 Sun].components[BoxCollider2DComponent#0].data.group was 65535 now -1"),
            differences(SceneSemanticDiff.DESKTOP, a, b));

        String light = scene("", "{\"type\": \"DirectionalLightComponent\", \"data\": {\"directionX\": -0.4, \"directionY\": -1, "
            + "\"directionZ\": -0.6}}");
        String unit = scene("", "{\"type\": \"DirectionalLightComponent\", \"data\": {\"directionX\": -0.32444286, "
            + "\"directionY\": -0.81110716, \"directionZ\": -0.48666432}}");
        String turned = scene("", "{\"type\": \"DirectionalLightComponent\", \"data\": {\"directionX\": 0.32444286, "
            + "\"directionY\": -0.81110716, \"directionZ\": -0.48666432}}");
        assertEquals(List.of(), differences(SceneSemanticDiff.DESKTOP, light, unit), "the same direction");
        assertEquals(3, differences(SceneSemanticDiff.DESKTOP, light, turned).size(),
            "a different direction is a change in every component that differs");
    }
}
