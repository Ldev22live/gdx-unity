package io.github.ldev22live.gdxunity.editor.visualscript;

import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.visualscript.BuiltInNodes;
import io.github.ldev22live.gdxunity.engine.visualscript.Graph;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphCodeGenerator;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphCodeGenerator.Language;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphLink;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphNode;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphSerializer;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphSourceEmbed;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphTypeScriptGenerator;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphVariable;
import io.github.ldev22live.gdxunity.engine.visualscript.NodeRegistry;
import io.github.ldev22live.gdxunity.engine.visualscript.PinDef;
import io.github.ldev22live.gdxunity.engine.visualscript.ValueType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scripts travel between the desktop and the web editor (TD-045). The web editor's {@code sourceInterchange.test.ts} does
 * the same checks against the same files: {@code engine-babylon/test/golden/desktop} is written by this side's
 * generators, {@code engine-babylon/test/golden} by the web's. Each side must produce, for the sample graphs, the code the
 * other side wrote, and read the graph out of the other side's file. Set the environment variable
 * {@code FORGEROOM_UPDATE_GOLDEN=1} to rewrite this side's files.
 */
class GraphSourceInterchangeTest {

    private static final NodeRegistry REGISTRY = NodeRegistry.createDefault();
    private static final File GRAPHS = new File("../assets/graphs");
    private static final File WEB_GOLDEN = new File("../engine-babylon/test/golden");
    private static final File DESKTOP_GOLDEN = new File(WEB_GOLDEN, "desktop");
    private static final List<String> SAMPLES = List.of("Greeter", "Hop", "Spinner");
    private static final String BLOCK_NOTE = "\n// The block below lets Forgeroom";
    private static final boolean UPDATE = "1".equals(System.getenv("FORGEROOM_UPDATE_GOLDEN"));

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    private record Output(String extension, Function<Graph, String> generate) {}

    private static List<Output> outputs() {
        GraphCodeGenerator jvm = new GraphCodeGenerator(REGISTRY);
        return List.of(
            new Output(GraphTypeScriptGenerator.EXTENSION, graph -> new GraphTypeScriptGenerator(REGISTRY).generate(graph)),
            new Output(Language.JAVA.getExtension(), graph -> jvm.generate(graph, Language.JAVA)),
            new Output(Language.KOTLIN.getExtension(), graph -> jvm.generate(graph, Language.KOTLIN)));
    }

    private static Graph sample(String name) throws IOException {
        return GraphSerializer.fromJson(Files.readString(new File(GRAPHS, name + ".graph").toPath()));
    }

    /** The code of a generated script, without the embedded graph. */
    private static String codeOf(String source) {
        int at = source.indexOf(BLOCK_NOTE);
        return at < 0 ? source : source.substring(0, at);
    }

    private static void assertSameGraph(Graph expected, Graph actual) {
        assertEquals(GraphSerializer.toJson(expected), GraphSerializer.toJson(actual));
    }

    @Test
    void shouldReadTheGraphBackFromEveryLanguage() throws IOException {
        Graph graph = sample("Hop");
        for (Output output : outputs()) {
            String source = output.generate().apply(graph);
            assertTrue(GraphSourceEmbed.hasGraph(source), output.extension());
            assertSameGraph(graph, GraphSourceEmbed.extract(source));
        }
    }

    @Test
    void shouldKeepNonAsciiAndQuotesInTheEmbeddedGraph() {
        Graph graph = new Graph("Gruß \"quoted\" $x");
        graph.putVariable(new GraphVariable("label", ValueType.STRING, "héllo \"wörld\" \\ ✓"));
        assertSameGraph(graph, GraphSourceEmbed.extract(GraphSourceEmbed.append("class A {}\n", graph)));
    }

    @Test
    void shouldRefuseAFileWithoutAnEmbeddedGraph() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> GraphSourceEmbed.extract("class A {}\n"));
        assertTrue(error.getMessage().contains("no embedded graph"), error.getMessage());
    }

    @Test
    void shouldRefuseADamagedBlock() {
        String damaged = "class A {}\n" + GraphSourceEmbed.BEGIN + "\n// !!!not base64!!!\n" + GraphSourceEmbed.END + "\n";
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> GraphSourceEmbed.extract(damaged));
        assertTrue(error.getMessage().contains("damaged"), error.getMessage());
    }

    @Test
    void shouldNotChangeTheCodeAboveTheBlock() {
        assertTrue(GraphSourceEmbed.append("class A {}\n", new Graph("A")).startsWith("class A {}\n"));
    }

    @Test
    void shouldRefuseALoopInTypeScriptToo() {
        Graph graph = new Graph("loop");
        graph.addNode(new GraphNode("a", BuiltInNodes.EVENT_BEGIN_PLAY, 0f, 0f));
        graph.addNode(new GraphNode("b", BuiltInNodes.ACTION_PRINT, 0f, 0f));
        graph.addLink(new GraphLink("a", PinDef.EXEC_OUT, "b", PinDef.EXEC_IN));
        graph.addLink(new GraphLink("b", PinDef.EXEC_OUT, "b", PinDef.EXEC_IN));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> new GraphTypeScriptGenerator(REGISTRY).generate(graph));
        assertTrue(error.getMessage().contains("loop back"), error.getMessage());
    }

    @Test
    void shouldWriteSampleScriptsLikeTheWebEditorAndReadItsGraphs() throws IOException {
        for (String name : SAMPLES) {
            Graph graph = sample(name);
            for (Output output : outputs()) {
                String source = output.generate().apply(graph);
                File own = new File(DESKTOP_GOLDEN, name + output.extension());
                File web = new File(WEB_GOLDEN, name + output.extension());
                if (UPDATE) {
                    Files.createDirectories(DESKTOP_GOLDEN.toPath());
                    Files.writeString(own.toPath(), source);
                }
                assertTrue(own.isFile(), own + " is missing: run with FORGEROOM_UPDATE_GOLDEN=1");
                assertTrue(web.isFile(), web + " is missing: run the web test with FORGEROOM_UPDATE_GOLDEN=1");
                assertEquals(codeOf(Files.readString(own.toPath())), codeOf(source), name + output.extension() + " changed");
                assertEquals(codeOf(Files.readString(web.toPath())), codeOf(source),
                    name + output.extension() + " differs from the web editor's");
                assertSameGraph(graph, GraphSourceEmbed.extract(Files.readString(web.toPath())));
            }
        }
    }

    @Test
    void shouldImportAGeneratedScriptAsANewGraphAsset(@TempDir Path temp) throws IOException {
        GraphAssets assets = new GraphAssets(temp.toFile());
        Graph graph = sample("Greeter");
        File script = temp.resolve("Greeter.kt").toFile();
        Files.writeString(script.toPath(), new GraphCodeGenerator(REGISTRY).generate(graph, Language.KOTLIN));

        AssetReference first = assets.importScript(script);
        AssetReference second = assets.importScript(script);

        assertEquals("graphs/" + graph.getName() + ".graph", first.getPath());
        assertFalse(first.getPath().equals(second.getPath()), "the second import must not overwrite the first");
        assertSameGraph(graph, assets.load(first));
    }

    @Test
    void shouldExportTypeScriptToTheWebScriptFolder(@TempDir Path temp) throws IOException {
        GraphAssets assets = new GraphAssets(temp.toFile());
        Graph graph = sample("Spinner");
        AssetReference ts = assets.exportTypeScript(graph, REGISTRY);

        assertEquals("scripts/web/" + GraphCodeGenerator.className(graph) + ".ts", ts.getPath());
        String source = Files.readString(temp.resolve(ts.getPath()));
        assertTrue(source.contains("export class " + GraphCodeGenerator.className(graph) + " extends ScriptComponent"));
        assertSameGraph(graph, GraphSourceEmbed.extract(source));
    }

    @Test
    void shouldRefuseToImportAScriptWithoutAGraph(@TempDir Path temp) throws IOException {
        GraphAssets assets = new GraphAssets(temp.toFile());
        File script = temp.resolve("Plain.java").toFile();
        Files.writeString(script.toPath(), "public class Plain {}\n");
        assertThrows(IllegalArgumentException.class, () -> assets.importScript(script));
    }
}
