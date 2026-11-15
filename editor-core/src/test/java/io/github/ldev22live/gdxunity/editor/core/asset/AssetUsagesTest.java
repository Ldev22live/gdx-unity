package io.github.ldev22live.gdxunity.editor.core.asset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Content Browser's "used by" list (roadmap M1.5). */
class AssetUsagesTest {
    @TempDir
    Path project;

    private void write(String relative, String text) throws IOException {
        Path file = project.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    @Test
    void shouldListTheFilesThatNameTheAssetExactlyInNameOrder() throws IOException {
        write("models/rock.glb", "glTF");
        write("scenes/b.json", "{\"entities\":[{\"components\":[{\"data\":{\"model\":\"models/rock.glb\"}}]}]}");
        write("prefabs/a.prefab", "{\"entities\":[{\"components\":[{\"data\":{\"model\":\"models/rock.glb\"}}]}]}");
        write("scripts/Rocks.java", "class Rocks { String path = \"models/rock.glb\"; }");
        write("scenes/other.json", "{\"model\":\"models/rock.glb2\",\"text\":\"see models/rock.glb in the docs\"}");
        write("materials/m.material", "{\"baseColor\":\"textures/x.png\"}");
        write("build/scenes/copy.json", "{\"model\":\"models/rock.glb\"}");
        write("notes/readme.txt", "models/rock.glb");

        assertEquals(List.of("prefabs/a.prefab", "scenes/b.json", "scripts/Rocks.java"), AssetUsages.usersOf(project.toFile(), "models/rock.glb"),
            "only exact names count; build output and other kinds of file are not searched; the asset itself is not its own user");
        assertEquals(List.of("materials/m.material"), AssetUsages.usersOf(project.toFile(), "textures/x.png"));
        assertEquals(List.of(), AssetUsages.usersOf(project.toFile(), "sounds/none.wav"));
    }

    @Test
    void shouldStillListAFileThatMentionsTheAssetButIsNotReadableJson() throws IOException {
        write("scenes/broken.json", "{ \"model\": \"models/rock.glb\", oops");

        assertEquals(List.of("scenes/broken.json"), AssetUsages.usersOf(project.toFile(), "models/rock.glb"),
            "a use is not hidden because the file does not parse");
    }

    @Test
    void shouldFindWhichSampleScenesPlayASongInTheRealProject() throws IOException {
        List<String> users = AssetUsages.usersOf(new File("../assets"), "music/lofi-evening.song");

        assertTrue(users.stream().anyMatch(path -> path.startsWith("samples/") && path.endsWith(".json")), users.toString());
    }
}
