package io.github.ldev22live.gdxunity.engine.scene.serialization;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Roadmap M9.3 (TD-093): the fixed text layout of scene files. */
class SceneTextFormatTest {

    @Test
    void shouldLayOutObjectsTwoSpacesPerLevelWithOneMemberPerLine() {
        String text = SceneTextFormat.format("{\"a\":1,\"b\":{\"c\":true,\"d\":null},\"e\":{},\"f\":[]}");
        assertEquals("""
            {
              "a": 1,
              "b": {
                "c": true,
                "d": null
              },
              "e": {},
              "f": []
            }
            """, text);
    }

    @Test
    void shouldKeepEveryValueExactlyAsWritten() {
        String text = SceneTextFormat.format("{\"x\":1.0,\"y\":-0.96,\"z\":1.0E-5,\"n\":12,\"s\":\"a \\\"q\\\" \\u00e9 \\\\ ,:[]{}\"}");
        assertTrue(text.contains("\"x\": 1.0,"), text);
        assertTrue(text.contains("\"y\": -0.96,"), text);
        assertTrue(text.contains("\"z\": 1.0E-5,"), text);
        assertTrue(text.contains("\"n\": 12,"), text);
        assertTrue(text.contains("\"s\": \"a \\\"q\\\" \\u00e9 \\\\ ,:[]{}\""), text);
        assertEquals("a \"q\" é \\ ,:[]{}", new JsonReader().parse(text).getString("s"));
    }

    @Test
    void shouldWriteShortValueArraysOnOneLineAndLongOnesSixteenToALine() {
        assertEquals("{\n  \"k\": [1, 2, 3]\n}\n", SceneTextFormat.format("{\"k\":[1,2,3]}"));
        StringBuilder values = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            values.append(i == 0 ? "" : ",").append(i);
        }
        String text = SceneTextFormat.format("{\"cells\":[" + values + "]}");
        String[] lines = text.split("\n");
        assertEquals("  \"cells\": [", lines[1]);
        assertEquals("    0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,", lines[2]);
        assertEquals("    16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31,", lines[3]);
        assertEquals("    32, 33, 34, 35, 36, 37, 38, 39", lines[4]);
        assertEquals("  ]", lines[5]);
        int[] parsed = new JsonReader().parse(text).get("cells").asIntArray();
        assertEquals(40, parsed.length);
        assertEquals(39, parsed[39]);
    }

    @Test
    void shouldChangeOneLineWhenOneValueOfALongArrayChanges() {
        StringBuilder before = new StringBuilder();
        StringBuilder after = new StringBuilder();
        for (int i = 0; i < 240; i++) {
            before.append(i == 0 ? "" : ",").append(17);
            after.append(i == 0 ? "" : ",").append(i == 100 ? 3 : 17);
        }
        String[] a = SceneTextFormat.format("{\"cells\":[" + before + "]}").split("\n");
        String[] b = SceneTextFormat.format("{\"cells\":[" + after + "]}").split("\n");
        assertEquals(a.length, b.length);
        int changed = 0;
        for (int i = 0; i < a.length; i++) {
            changed += a[i].equals(b[i]) ? 0 : 1;
        }
        assertEquals(1, changed);
    }

    @Test
    void shouldPutEachObjectOfAnArrayOnItsOwnLines() {
        assertEquals("""
            [
              {
                "a": 1
              },
              [1, 2]
            ]
            """, SceneTextFormat.format("[{\"a\":1},[1,2]]"));
    }

    @Test
    void shouldGiveTheSameTextWhenFormattedTwice() {
        String once = SceneTextFormat.format("{ \"a\" : [ 1 , 2 ] ,\n\t\"b\" : { \"c\" : \"d\" } }");
        assertEquals(once, SceneTextFormat.format(once));
        assertFalse(once.contains("\t"));
        assertFalse(once.contains("\r"));
        assertTrue(once.endsWith("}\n"));
    }

    @Test
    void shouldRefuseTextThatIsNotOneJsonValueAndSayWhere() {
        IllegalArgumentException trailing = assertThrows(IllegalArgumentException.class, () -> SceneTextFormat.format("{} {}"));
        assertTrue(trailing.getMessage().contains("character 3"), trailing.getMessage());
        assertThrows(IllegalArgumentException.class, () -> SceneTextFormat.format("{\"a\":}"));
        assertThrows(IllegalArgumentException.class, () -> SceneTextFormat.format("{\"a\":1"));
        assertThrows(IllegalArgumentException.class, () -> SceneTextFormat.format("{a:1}"));
        IllegalArgumentException word = assertThrows(IllegalArgumentException.class, () -> SceneTextFormat.format("[1, maybe]"));
        assertTrue(word.getMessage().contains("'maybe'"), word.getMessage());
        assertThrows(IllegalArgumentException.class, () -> SceneTextFormat.format("\"open"));
        assertThrows(NullPointerException.class, () -> SceneTextFormat.format((String) null));
        assertThrows(NullPointerException.class, () -> SceneTextFormat.format((JsonValue) null));
    }

    @Test
    void shouldWriteWholeFloatsWithTheirPointWhereLibGdxWouldDropIt() {
        StringWriter exact = new StringWriter();
        Json json = new Json(JsonWriter.OutputType.json);
        json.setWriter(SceneTextFormat.newWriter(exact));
        json.writeObjectStart();
        json.writeValue("f", 1f);
        json.writeValue("g", 0.96f);
        json.writeValue("d", 2d);
        json.writeValue("i", 3);
        json.writeValue("l", 4L);
        json.writeObjectEnd();
        assertEquals("{\"f\":1.0,\"g\":0.96,\"d\":2.0,\"i\":3,\"l\":4}", exact.toString());

        StringWriter plain = new StringWriter();
        Json libGdx = new Json(JsonWriter.OutputType.json);
        libGdx.setWriter(new JsonWriter(plain));
        libGdx.writeObjectStart();
        libGdx.writeValue("f", 1f);
        libGdx.writeObjectEnd();
        assertEquals("{\"f\":1}", plain.toString(), "LibGDX's own writer drops the point; if this changes, newWriter may no longer be needed");
    }

    @Test
    void shouldLayOutAParsedTreeKeepingTheNumbersAsTheyWereRead() {
        JsonValue tree = new JsonReader().parse("{\"x\":0.0,\"y\":1.50,\"n\":3,\"s\":\"a\\\"b\",\"o\":null,\"b\":false,\"l\":[1.0,2]}");
        tree.addChild("made", new JsonValue(2.0));
        assertEquals("""
            {
              "x": 0.0,
              "y": 1.50,
              "n": 3,
              "s": "a\\"b",
              "o": null,
              "b": false,
              "l": [1.0, 2],
              "made": 2.0
            }
            """, SceneTextFormat.format(tree));
    }

    @Test
    void shouldSaveASceneInTheLayoutAndReadItBack() {
        Scene scene = new Scene("Layout");
        Entity box = scene.createEntity("Box");
        box.getTransform().setLocalPosition(1f, 2.5f, 0f);
        SceneSerializer serializer = new SceneSerializer();
        String text = serializer.toJson(scene);
        assertTrue(text.startsWith("{\n  \"schemaVersion\": 1,\n  \"name\": \"Layout\",\n"), text);
        assertTrue(text.contains("\n          \"x\": 1.0,\n          \"y\": 2.5,\n          \"z\": 0.0\n"), text);
        assertTrue(text.contains("\"components\": []"), text);
        assertTrue(text.endsWith("}\n"));
        Scene loaded = serializer.fromJson(text);
        assertEquals(2.5f, loaded.findEntityByName("Box").getTransform().getLocalPosition().y);
        assertEquals(text, serializer.toJson(loaded));
        JsonValue root = new JsonReader().parse(text);
        assertEquals("Layout", root.getString("name"));
    }
}
