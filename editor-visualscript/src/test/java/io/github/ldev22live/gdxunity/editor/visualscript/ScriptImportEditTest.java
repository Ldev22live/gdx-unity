package io.github.ldev22live.gdxunity.editor.visualscript;

import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.visualscript.Graph;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphCodeGenerator;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphSerializer;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphSourceEmbed;
import io.github.ldev22live.gdxunity.engine.visualscript.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Importing a script reads its embedded graph; a line edited by hand afterwards is not read back, but it is found and named (TD-065, part B),
 * so the edit is not dropped without notice.
 */
class ScriptImportEditTest {
    private static final NodeRegistry REGISTRY = NodeRegistry.createDefault();

    @TempDir
    Path project;

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    private static Graph example(String name) throws IOException {
        return GraphSerializer.fromJson(Files.readString(new File("../assets/graphs/samples/" + name + ".graph").toPath()));
    }

    private File write(String fileName, String text) throws IOException {
        Path file = project.resolve("incoming").resolve(fileName);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
        return file.toFile();
    }

    @Test
    void shouldReportNoEditForAScriptThatIsExactlyWhatItsGraphGenerates() throws IOException {
        Graph graph = example("ShooterShip");
        String java = new GraphCodeGenerator(REGISTRY).generate(graph, GraphCodeGenerator.Language.JAVA);
        String kotlin = new GraphCodeGenerator(REGISTRY).generate(graph, GraphCodeGenerator.Language.KOTLIN);
        GraphAssets assets = new GraphAssets(project.toFile());

        GraphAssets.ImportResult fromJava = assets.importScriptChecked(write("ShooterShip.java", java));
        GraphAssets.ImportResult fromKotlin = assets.importScriptChecked(write("ShooterShip.kt", kotlin));

        assertNull(fromJava.edit());
        assertNull(fromKotlin.edit());
        assertNotNull(fromJava.asset());
    }

    @Test
    void shouldNameTheFirstLineThatWasEditedByHandAndStillImportTheEmbeddedGraph() throws IOException {
        Graph graph = example("ShooterShip");
        String java = new GraphCodeGenerator(REGISTRY).generate(graph, GraphCodeGenerator.Language.JAVA);
        String edited = java.replace("onUpdate(float deltaTime) {", "onUpdate(float deltaTime) { // my change");
        GraphAssets assets = new GraphAssets(project.toFile());

        GraphAssets.ImportResult result = assets.importScriptChecked(write("ShooterShip.java", edited));

        GraphSourceEmbed.Edit edit = result.edit();
        assertNotNull(edit);
        assertTrue(edit.text().contains("// my change"), edit.text());
        assertEquals(edited.split("\n")[edit.line() - 1].strip(), edit.text());
        Graph imported = assets.load(result.asset());
        assertEquals(graph.getNodes().size(), imported.getNodes().size(), "the embedded graph is what was saved");
    }

    @Test
    void shouldReportALineThatWasAddedOrRemoved() {
        String generated = "a\nb\nc\n";

        GraphSourceEmbed.Edit added = GraphSourceEmbed.findEdit("a\nb\nc\nextra\n", generated);
        GraphSourceEmbed.Edit removed = GraphSourceEmbed.findEdit("a\nb\n", generated);

        assertEquals(4, added.line());
        assertTrue(added.text().contains("extra"), added.text());
        assertEquals(3, removed.line());
        assertTrue(removed.text().contains("missing"), removed.text());
    }

    @Test
    void shouldIgnoreLineEndingsAndTrailingSpaces() {
        assertNull(GraphSourceEmbed.findEdit("a  \r\nb\r\n", "a\nb\n"));
    }

    @Test
    void shouldImportAGraphWhoseNodesThisProjectDoesNotHaveWithoutACheck() throws IOException {
        Graph graph = example("ShooterShip");
        // A graph naming a node type that does not exist cannot be regenerated; it still imports
        String unknownNode = GraphSourceEmbed.append("// code\n", GraphSerializer.fromJson(GraphSerializer.toJson(graph)
            .replace("event.tick", "event.noSuchThing")));
        GraphAssets assets = new GraphAssets(project.toFile());

        GraphAssets.ImportResult result = assets.importScriptChecked(write("Odd.java", unknownNode));

        assertNull(result.edit());
        assertTrue(result.asset().getPath().startsWith("graphs/"), result.asset().getPath());
    }
}
