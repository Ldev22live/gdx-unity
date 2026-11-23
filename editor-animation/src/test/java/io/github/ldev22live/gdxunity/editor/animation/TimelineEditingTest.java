package io.github.ldev22live.gdxunity.editor.animation;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.command.ChangeTransformCommand;
import io.github.ldev22live.gdxunity.editor.core.gizmo.TransformSnapshot;
import io.github.ldev22live.gdxunity.editor.core.log.EditorLog;
import io.github.ldev22live.gdxunity.editor.core.play.PlayModeController;
import io.github.ldev22live.gdxunity.engine.animation.AnimatedProperties;
import io.github.ldev22live.gdxunity.engine.animation.AnimationClip;
import io.github.ldev22live.gdxunity.engine.animation.AnimationTrack;
import io.github.ldev22live.gdxunity.engine.animation.AnimatorComponent;
import io.github.ldev22live.gdxunity.engine.animation.AnimatorComponentSerializer;
import io.github.ldev22live.gdxunity.engine.animation.Interpolation;
import io.github.ldev22live.gdxunity.engine.animation.Keyframe;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetType;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineEditingTest {

    @TempDir
    Path assetRoot;

    private AnimationAssets assets;
    private EditorState editorState;
    private TimelineSession session;
    private Entity robot;
    private Entity arm;

    @BeforeAll
    static void init() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() throws Exception {
        assets = new AnimationAssets(assetRoot.toFile());
        editorState = new EditorState();
        editorState.getSerializer().getRegistry().register(new AnimatorComponentSerializer());
        session = new TimelineSession(editorState, assets, AnimatedProperties.createDefault());
        robot = editorState.getActiveScene().createEntity("Robot");
        arm = editorState.getActiveScene().createEntity("Arm");
        arm.getTransform().setParent(robot.getTransform());
        AssetReference clip = assets.create("Robot");
        robot.addComponent(new AnimatorComponent(clip.getPath()));
        assertTrue(session.open(clip, robot.getId()));
    }

    private TimelineDocument document() {
        return session.getDocument();
    }

    private void moveWithCommand(Entity entity, float x) {
        TransformSnapshot before = TransformSnapshot.capture(entity.getTransform());
        entity.getTransform().setLocalPosition(x, 0f, 0f);
        TransformSnapshot after = TransformSnapshot.capture(entity.getTransform());
        assertTrue(editorState.getCommandHistory().execute(
            new ChangeTransformCommand(editorState, entity.getId(), before, after, "Move")));
    }

    @Test
    void shouldCreateClipAssetsWithUniqueNames() throws Exception {
        AssetReference second = assets.create("Robot");

        assertEquals("animations/Robot 1.anim", second.getPath());
        assertEquals(AssetType.ANIMATION, AssetType.fromPath(second.getPath()));
        assertEquals("Robot", assets.load(document().getAsset()).getName());
    }

    @Test
    void shouldAddTracksAndKeysWithUndoAndSaveThem() throws Exception {
        AnimationTrack track = document().addTrack("Arm", AnimatedProperties.ROTATION_ROLL);
        assertNotNull(track);
        assertNull(document().addTrack("Arm", AnimatedProperties.ROTATION_ROLL), "one track per property");

        assertTrue(document().setKey(track, 0.51f, 45f));
        // Snapped to the 30 fps frame grid
        assertEquals(0.5f, track.getCurve().getKey(0).time(), 1e-6f);
        assertTrue(document().isDirty());

        assertTrue(document().undo());
        assertTrue(track.getCurve().isEmpty());
        assertTrue(document().redo());
        assertTrue(session.saveIfDirty());
        assertFalse(document().isDirty());

        AnimationClip reloaded = assets.load(document().getAsset());
        assertEquals(45f, reloaded.findTrack("Arm", AnimatedProperties.ROTATION_ROLL).getCurve().evaluate(0.5f));
    }

    @Test
    void shouldKeySelectedChildWithTransformTracksInOneStep() {
        arm.getTransform().setLocalPosition(10f, 20f, 30f);
        editorState.setSelectedEntityId(arm.getId());

        assertTrue(session.keySelection());

        assertEquals(9, document().tracksOf("Arm").size());
        assertEquals(20f, document().getClip().findTrack("Arm", AnimatedProperties.POSITION_Y).getCurve().evaluate(0f));
        assertTrue(document().undo());
        assertTrue(document().getClip().getTracks().isEmpty(), "tracks and keys undo together");
    }

    @Test
    void shouldRefuseToKeyEntityOutsideAnimatedTree() {
        Entity stranger = editorState.getActiveScene().createEntity("Stranger");
        editorState.setSelectedEntityId(stranger.getId());

        assertFalse(session.keySelection());
        assertTrue(editorState.getStatusMessage().contains("not Robot"), editorState.getStatusMessage());
    }

    @Test
    void shouldMoveDeleteAndRetimeKeys() {
        AnimationTrack track = document().addTrack("", AnimatedProperties.POSITION_X);
        document().setKey(track, 0f, 0f);
        document().setKey(track, 1f, 100f);
        document().setKey(track, 1.5f, 150f);

        List<TimelineDocument.KeyRef> moved = document().moveKeys(
            List.of(new TimelineDocument.KeyRef(track, 1f)), 0.5f);

        // Landing on the 1.5 s key replaces it
        assertEquals(List.of(new TimelineDocument.KeyRef(track, 1.5f)), moved);
        assertEquals(List.of(0f, 1.5f), track.getCurve().getKeys().stream().map(Keyframe::time).toList());
        assertEquals(100f, track.getCurve().evaluate(1.5f));

        assertTrue(document().setInterpolation(moved, Interpolation.CONSTANT));
        assertTrue(document().setInterpolation(List.of(new TimelineDocument.KeyRef(track, 0f)), Interpolation.CONSTANT));
        assertEquals(0f, track.getCurve().evaluate(1.4f));

        assertTrue(document().setKeyValue(moved.get(0), 7f));
        assertEquals(7f, track.getCurve().evaluate(1.5f));

        assertTrue(document().deleteKeys(moved));
        assertEquals(1, track.getCurve().size());

        assertFalse(document().setTiming(0f, 30));
        assertTrue(document().setTiming(4f, 24));
        assertEquals(4f, document().getClip().getDuration());
        document().undo();
        assertEquals(AnimationClip.DEFAULT_DURATION, document().getClip().getDuration());
    }

    @Test
    void shouldPreviewPoseAndRestoreAuthoredValues() {
        robot.getTransform().setLocalPosition(5f, 0f, 0f);
        AnimationTrack track = document().addTrack("", AnimatedProperties.POSITION_X);
        document().setKey(track, 0f, 0f);
        document().setKey(track, 2f, 200f);

        session.setTime(1f);
        assertTrue(session.isPreviewing());
        assertEquals(100f, robot.getTransform().getLocalPosition().x, 1e-3f);

        session.endPreview();
        assertFalse(session.isPreviewing());
        assertEquals(5f, robot.getTransform().getLocalPosition().x);
    }

    @Test
    void shouldPlayLoopAndReverseInEditor() {
        AnimationTrack track = document().addTrack("", AnimatedProperties.POSITION_X);
        document().setKey(track, 0f, 0f);
        document().setKey(track, 2f, 200f);
        document().setInterpolation(List.of(new TimelineDocument.KeyRef(track, 0f)), Interpolation.LINEAR);

        session.setPlaying(true);
        session.update(2.5f);
        assertEquals(0.5f, session.getTime(), 1e-5f);
        assertEquals(50f, robot.getTransform().getLocalPosition().x, 1e-3f);

        session.setReverse(true);
        session.setLooping(false);
        session.update(1f);
        assertEquals(0f, session.getTime());
        assertFalse(session.isPlaying(), "stops at the start when not looping");
    }

    @Test
    void shouldAutoKeyNewSceneEditsButNotUndo() {
        AnimationTrack track = document().addTrack("Arm", AnimatedProperties.POSITION_X);
        document().setKey(track, 0f, 0f);
        session.setAutoKey(true);
        session.setTime(1f);

        moveWithCommand(arm, 80f);

        assertEquals(80f, track.getCurve().evaluate(1f));
        assertEquals(2, track.getCurve().size());
        int keysBeforeUndo = track.getCurve().size();

        editorState.getCommandHistory().undo();

        assertEquals(keysBeforeUndo, track.getCurve().size(), "undoing a scene edit records no key");
    }

    @Test
    void shouldNotAutoKeyWhenNotPreviewing() {
        AnimationTrack track = document().addTrack("Arm", AnimatedProperties.POSITION_X);
        session.setAutoKey(true);

        moveWithCommand(arm, 80f);

        assertTrue(track.getCurve().isEmpty());
    }

    @Test
    void shouldEndPreviewBeforePlayAndAnimateInPlayMode() {
        AnimationTrack track = document().addTrack("Arm", AnimatedProperties.POSITION_Y);
        document().setKey(track, 0f, 0f);
        document().setKey(track, 2f, 100f);
        document().setInterpolation(List.of(new TimelineDocument.KeyRef(track, 0f)), Interpolation.LINEAR);
        session.setTime(2f);
        assertEquals(100f, arm.getTransform().getLocalPosition().y, 1e-3f);

        PlayModeController play = new PlayModeController(editorState);
        play.addExtension(new AnimationPlayModeExtension(session, new EditorLog()));

        assertTrue(play.play(), editorState.getStatusMessage());
        assertFalse(session.isPreviewing());
        assertFalse(document().isDirty(), "the open clip is saved before play");
        assertEquals(0f, arm.getTransform().getLocalPosition().y, "authored scene is back to its own values");

        Entity runtimeArm = play.getRuntimeScene().findEntityById(arm.getId());
        // Play mode clamps each frame's delta, so advance one second in 50 ms frames
        for (int frame = 0; frame < 20; frame++) {
            play.update(0.05f);
        }
        assertEquals(50f, runtimeArm.getTransform().getLocalPosition().y, 1e-2f);
        play.stop();
        assertNull(robot.getComponent(AnimatorComponent.class).getClip(), "the authored Animator is never bound");
    }

    @Test
    void shouldRefusePlayWhenClipIsMissing() {
        robot.addComponent(new AnimatorComponent("animations/Missing.anim"));
        PlayModeController play = new PlayModeController(editorState);
        play.addExtension(new AnimationPlayModeExtension(session, new EditorLog()));

        assertFalse(play.play());

        assertTrue(editorState.getStatusMessage().contains("Missing.anim"), editorState.getStatusMessage());
    }

    @Test
    void shouldWarnAboutTracksThatCannotPlay() throws Exception {
        document().addTrack("Leg", AnimatedProperties.POSITION_X);
        EditorLog log = new EditorLog();
        PlayModeController play = new PlayModeController(editorState);
        play.addExtension(new AnimationPlayModeExtension(session, log));

        assertTrue(play.play(), editorState.getStatusMessage());

        assertTrue(log.getEntries(EditorLog.Severity.WARNING).stream().anyMatch(e -> e.message().contains("no child 'Leg'")),
            log.getEntries(EditorLog.Severity.WARNING).toString());
        play.stop();
    }

    @Test
    void shouldEndPreviewWhenSceneChanges() {
        robot.getTransform().setLocalPosition(5f, 0f, 0f);
        AnimationTrack track = document().addTrack("", AnimatedProperties.POSITION_X);
        document().setKey(track, 0f, 50f);
        session.setTime(0f);
        Vector3 posed = robot.getTransform().getLocalPosition();
        assertEquals(50f, posed.x);

        editorState.setActiveScene(new io.github.ldev22live.gdxunity.engine.scene.Scene("Other"));

        assertFalse(session.isPreviewing());
        assertEquals(5f, robot.getTransform().getLocalPosition().x);
    }
}
