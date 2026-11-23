package io.github.ldev22live.gdxunity.editor.desktop;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The command that hands a new project to a second editor (roadmap M1.4, TD-086). */
class EditorRelaunchTest {
    private static final File PROJECT = new File("projects/My Game").getAbsoluteFile();

    @Test
    void shouldStartThePackagedLauncherOnTheProjectAndSceneWhenThereIsOne() {
        EditorRelaunch.Origin packaged = new EditorRelaunch.Origin("C:/Forgeroom/ForgeroomEditor.exe", "C:/Forgeroom/runtime",
            List.of("-Xmx2g"), "C:/Forgeroom/app/editor.jar", true);
        assertEquals(List.of("C:/Forgeroom/ForgeroomEditor.exe", "--project", PROJECT.getPath(), "--scene", "scenes/Main.json"),
            EditorRelaunch.command(packaged, PROJECT, "scenes/Main.json"));
    }

    @Test
    void shouldStartTheSameJavaWithTheSameOptionsAndClassPathButNoDebuggerInADevelopmentRun() {
        EditorRelaunch.Origin development = new EditorRelaunch.Origin(null, "/opt/jdk", List.of("-Dfile.encoding=UTF-8",
            "-agentlib:jdwp=transport=dt_socket,server=y,address=5005", "-Xmx2g"), "a.jar:b.jar", false);
        assertEquals(List.of("/opt/jdk" + File.separator + "bin" + File.separator + "java", "-Dfile.encoding=UTF-8", "-Xmx2g",
                "-cp", "a.jar:b.jar", EditorLauncher.class.getName(), "--project", PROJECT.getPath(), "--scene", "scenes/x.json"),
            EditorRelaunch.command(development, PROJECT, "scenes/x.json"));
    }

    @Test
    void shouldUseJavaExeOnWindowsAndTreatABlankLauncherPathAsNone() {
        EditorRelaunch.Origin windows = new EditorRelaunch.Origin(" ", "C:/jdk", List.of(), "a.jar", true);
        assertEquals("C:/jdk" + File.separator + "bin" + File.separator + "java.exe",
            EditorRelaunch.command(windows, PROJECT, "scenes/x.json").get(0));
    }
}
