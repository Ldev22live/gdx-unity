package io.github.ldev22live.gdxunity.editor.desktop.ui;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.play.ProfilePlayStep;
import io.github.ldev22live.gdxunity.engine.render.play.ProfileFix;
import io.github.ldev22live.gdxunity.engine.render.play.ProfileProblem;
import io.github.ldev22live.gdxunity.engine.scene.Scene;

import java.util.List;
import java.util.Objects;

/**
 * The footer's buttons for the safe fixes of a refused Play (TD-088, roadmap M3b.5), beside Customize and Play: after "Cannot play: No
 * player", for example, a "Make Hero the player" button. Each is one undoable command on the authored scene and only runs when
 * clicked. Nothing is shown when the last Play started or had nothing to fix, and opening another scene forgets the offer.
 */
public class ProfileFixButtons extends Table {
    /** At most this many fixes are offered at once; the rest follow once these are done and Play is tried again. */
    static final int MAX_BUTTONS = 3;
    private static final float BUTTON_HEIGHT = 26f;
    /** Space after each fix button, so the last one stands clear of Customize. */
    private static final float BUTTON_GAP = 12f;

    private final EditorState editorState;
    private final ProfilePlayStep step;
    private final Skin skin;
    private List<ProfileFix> shown = List.of();
    private List<ProfileProblem> shownProblems = List.of();

    public ProfileFixButtons(EditorState editorState, ProfilePlayStep step, Skin skin) {
        super(skin);
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.step = Objects.requireNonNull(step, "step cannot be null");
        this.skin = Objects.requireNonNull(skin, "skin cannot be null");
        // Next to Customize: the buttons gather at the right of their cell
        right();
        editorState.addListener(new EditorState.Listener() {
            @Override
            public void onActiveSceneChanged(Scene oldScene, Scene newScene) {
                // Play and Stop switch scenes too, and a Play that starts has no fixes; another scene's fixes would be wrong here
                if (!editorState.isPlayModeActive()) {
                    step.clearProblems();
                }
            }
        });
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        // A refused Play changes no editor state a listener hears; the step replaces its problem list whenever the offer changes, so
        // comparing the list itself each frame costs nothing
        if (step.getLastProblems() != shownProblems) {
            shownProblems = step.getLastProblems();
            rebuild(step.getLastFixes());
        }
    }

    private void rebuild(List<ProfileFix> fixes) {
        shown = List.copyOf(fixes);
        clearChildren();
        for (ProfileFix fix : shown.subList(0, Math.min(MAX_BUTTONS, shown.size()))) {
            TextButton button = new TextButton(fix.label(), skin);
            button.setColor(skin.getColor("warning"));
            button.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    step.applyFix(editorState, fix);
                }
            });
            add(button).height(BUTTON_HEIGHT).padRight(BUTTON_GAP);
        }
        invalidateHierarchy();
    }

    /** The labels of the buttons shown, for the smoke check. */
    public List<String> getShownLabels() {
        return shown.subList(0, Math.min(MAX_BUTTONS, shown.size())).stream().map(ProfileFix::label).toList();
    }
}
