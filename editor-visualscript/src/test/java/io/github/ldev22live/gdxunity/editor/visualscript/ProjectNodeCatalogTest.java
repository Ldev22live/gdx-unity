package io.github.ldev22live.gdxunity.editor.visualscript;

import io.github.ldev22live.gdxunity.engine.visualscript.NodeRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The method nodes of a project: its catalog file and its compiled script classes (TD-060, milestone B). */
class ProjectNodeCatalogTest {
    private static final String VECTOR_LEN = "java:com.badlogic.gdx.math.Vector3#len()";
    private static final String QUATERNION_LEN = "java:com.badlogic.gdx.math.Quaternion#len()";
    private static final String SCORE_ADD = "java:game.Score#add(int)";

    @TempDir
    Path project;

    private File compileScore() throws IOException {
        Path source = project.resolve("src/game/Score.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "package game; public class Score { public int total; public void add(int n) { total += n; } }");
        File classes = project.resolve("build/scripts").toFile();
        assertTrue(classes.mkdirs());
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "the tests run on a JDK");
        assertEquals(0, compiler.run(null, null, null, "-d", classes.getPath(), source.toString()));
        return classes;
    }

    @Test
    void shouldOfferTheEngineClassesWhenTheProjectHasNoCatalogFile() {
        NodeRegistry registry = NodeRegistry.createBuiltInOnly();

        ProjectNodeCatalog.Result result = new ProjectNodeCatalog(registry, project.toFile(), project.resolve("build/scripts").toFile()).refresh();

        assertNotNull(registry.get(VECTOR_LEN));
        assertEquals(0, result.projectClasses());
        assertTrue(result.nodes() > 100, "nodes: " + result.nodes());
    }

    @Test
    void shouldOfferTheMethodsOfCompiledProjectScripts() throws IOException {
        File classes = compileScore();
        NodeRegistry registry = NodeRegistry.createBuiltInOnly();

        ProjectNodeCatalog.Result result = new ProjectNodeCatalog(registry, project.toFile(), classes).refresh();

        assertEquals(1, result.projectClasses());
        assertNotNull(registry.get(SCORE_ADD));
        assertEquals("Java: Score", registry.get(SCORE_ADD).getCategory());
    }

    @Test
    void shouldReadTheCatalogFileAndReportClassesThatAreMissing() throws IOException {
        Files.createDirectories(project.resolve("graphs"));
        Files.writeString(project.resolve("graphs/classes.json"), "{\"schemaVersion\":1,\"kind\":\"classCatalog\","
            + "\"classes\":[\"com.badlogic.gdx.math.Quaternion\",\"no.such.Thing\"]}");
        NodeRegistry registry = NodeRegistry.createBuiltInOnly();

        ProjectNodeCatalog.Result result = new ProjectNodeCatalog(registry, project.toFile(), project.resolve("build/scripts").toFile()).refresh();

        assertNotNull(registry.get(QUATERNION_LEN));
        assertNull(registry.get(VECTOR_LEN), "only the listed classes");
        assertEquals(java.util.List.of("no.such.Thing"), result.missingClasses());
        assertTrue(result.summary().contains("no.such.Thing"), result.summary());
    }

    @Test
    void shouldFallBackToTheEngineClassesAndSayWhyWhenTheCatalogFileIsBroken() throws IOException {
        Files.createDirectories(project.resolve("graphs"));
        Files.writeString(project.resolve("graphs/classes.json"), "not json at all");
        NodeRegistry registry = NodeRegistry.createBuiltInOnly();

        ProjectNodeCatalog.Result result = new ProjectNodeCatalog(registry, project.toFile(), project.resolve("build/scripts").toFile()).refresh();

        assertNotNull(registry.get(VECTOR_LEN));
        assertEquals(1, result.problems().size());
        assertTrue(result.problems().get(0).startsWith("graphs/classes.json ignored"), result.problems().get(0));
    }

    @Test
    void shouldReplaceTheNodesOnEachRefreshSoARemovedClassDisappears() throws IOException {
        File classes = compileScore();
        NodeRegistry registry = NodeRegistry.createBuiltInOnly();
        ProjectNodeCatalog catalog = new ProjectNodeCatalog(registry, project.toFile(), classes);
        catalog.refresh();
        assertNotNull(registry.get(SCORE_ADD));
        Files.delete(classes.toPath().resolve("game/Score.class"));

        ProjectNodeCatalog.Result result = catalog.refresh();

        assertNull(registry.get(SCORE_ADD));
        assertEquals(0, result.projectClasses());
        assertNotNull(registry.get(VECTOR_LEN), "the engine nodes are back");
        catalog.close();
    }
}
