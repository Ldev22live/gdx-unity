package io.github.ldev22live.gdxunity.editor.desktop;

import com.badlogic.gdx.Gdx;
import io.github.ldev22live.gdxunity.editor.core.log.EditorLog;
import io.github.ldev22live.gdxunity.editor.core.play.PlayModeController;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.desktop.GameAudio;
import io.github.ldev22live.gdxunity.engine.scene.Scene;

import java.util.List;
import java.util.Objects;

/**
 * Gives the played scene its sound effects (TD-043): binds every Audio Source to a {@link GameAudio} when Play starts
 * and closes the sound device when it stops. An Audio Source naming an unknown sound prevents play, with its reason.
 * A missing sound device is reported in the Output Log and does not prevent play.
 */
public class AudioPlayModeExtension implements PlayModeController.Extension {
    private static final String LOG_SOURCE = "Audio";

    private final EditorLog log;
    private final AssetSource assets;
    private GameAudio audio;

    /** @param assets the project's files, where Music Sources find their songs */
    public AudioPlayModeExtension(EditorLog log, AssetSource assets) {
        this.log = Objects.requireNonNull(log, "log cannot be null");
        this.assets = Objects.requireNonNull(assets, "assets cannot be null");
    }

    @Override
    public List<String> onPlay(Scene runtimeScene, PlayModeController controller) {
        audio = new GameAudio(runtimeScene, Gdx.audio != null, GameAudio.assetsOf(assets));
        List<String> problems = audio.bind();
        if (problems.isEmpty() && audio.getOutputError() != null) {
            log.warning(LOG_SOURCE, audio.getOutputError());
        }
        return problems;
    }

    @Override
    public void onStop(Scene runtimeScene) {
        if (audio != null) {
            GameAudio owned = audio;
            audio = null;
            owned.dispose();
        }
    }
}
