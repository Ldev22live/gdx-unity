package io.github.ldev22live.gdxunity.editor.animation;

import io.github.ldev22live.gdxunity.editor.core.log.EditorLog;
import io.github.ldev22live.gdxunity.editor.core.play.PlayModeController;
import io.github.ldev22live.gdxunity.engine.animation.AnimationClip;
import io.github.ldev22live.gdxunity.engine.animation.AnimatorComponent;
import io.github.ldev22live.gdxunity.engine.animation.ClipSampler;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Starts and stops Animators with play (TD-031): ends the Timeline preview before the scene is copied, saves the open
 * clip, then loads each Animator's clip and binds it. A missing or unreadable clip prevents play with its reason;
 * tracks that cannot play (a renamed child, a removed component) are reported in the Output Log and skipped.
 */
public class AnimationPlayModeExtension implements PlayModeController.Extension {
    private static final String LOG_SOURCE = "Animation";

    private final TimelineSession session;
    private final EditorLog log;

    public AnimationPlayModeExtension(TimelineSession session, EditorLog log) {
        this.session = Objects.requireNonNull(session, "session cannot be null");
        this.log = Objects.requireNonNull(log, "log cannot be null");
    }

    @Override
    public void beforePlay(Scene authoredScene) {
        session.endPreview();
    }

    @Override
    public List<String> onPlay(Scene runtimeScene, PlayModeController controller) {
        if (!session.saveIfDirty()) {
            return List.of("the open animation clip could not be saved");
        }
        List<String> problems = new ArrayList<>();
        List<AnimatorComponent> toBind = new ArrayList<>();
        List<AnimationClip> clips = new ArrayList<>();
        for (Entity entity : runtimeScene.getAllEntities()) {
            for (AnimatorComponent animator : entity.getComponents(AnimatorComponent.class)) {
                if (!animator.isEnabled()) {
                    continue;
                }
                try {
                    AnimationClip clip = session.getAssets().load(new AssetReference(animator.getClipPath()));
                    for (String problem : ClipSampler.problems(clip, entity, session.getProperties())) {
                        log.warning(LOG_SOURCE, clip.getName() + " on " + entity.getName() + ": " + problem);
                    }
                    toBind.add(animator);
                    clips.add(clip);
                } catch (IOException | RuntimeException e) {
                    problems.add(entity.getName() + ": " + animator.getClipPath() + ": " + e.getMessage());
                }
            }
        }
        if (!problems.isEmpty()) {
            return problems;
        }
        for (int i = 0; i < toBind.size(); i++) {
            toBind.get(i).bind(clips.get(i), session.getProperties());
        }
        return List.of();
    }

    @Override
    public void onStop(Scene runtimeScene) {
        for (Entity entity : runtimeScene.getAllEntities()) {
            for (AnimatorComponent animator : entity.getComponents(AnimatorComponent.class)) {
                animator.unbind();
            }
        }
    }
}
