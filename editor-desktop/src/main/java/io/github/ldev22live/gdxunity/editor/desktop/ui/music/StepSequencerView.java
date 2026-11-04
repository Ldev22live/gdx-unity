package io.github.ldev22live.gdxunity.editor.desktop.ui.music;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import io.github.ldev22live.gdxunity.editor.audio.MusicDocument;
import io.github.ldev22live.gdxunity.editor.audio.MusicSession;
import io.github.ldev22live.gdxunity.engine.audio.song.Channel;
import io.github.ldev22live.gdxunity.engine.audio.song.Note;
import io.github.ldev22live.gdxunity.engine.audio.song.Pattern;
import io.github.ldev22live.gdxunity.engine.audio.song.Song;
import io.github.ldev22live.gdxunity.engine.audio.song.StepGrid;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/**
 * The channel rack with its step sequencer: one row per channel with a mute box, its name and a row of sixteenth-note
 * steps. Click a step to switch it, drag along a row to paint steps on or off, Ctrl+click a lit step to cycle its
 * velocity, right-click to clear one. Click a name to choose the channel the piano roll and instrument panel edit.
 * The wheel scrolls rows; Shift+wheel scrolls the steps of a long pattern.
 */
final class StepSequencerView extends ShapeWidget {
    private static final float ROW_HEIGHT = 28f;
    private static final float HEADER_HEIGHT = 20f;
    private static final float MUTE_SIZE = 16f;
    private static final float NAME_WIDTH = 130f;
    private static final float STEP_WIDTH = 24f;
    private static final float STEP_GAP = 3f;
    private static final float BEAT_GAP = 6f;
    private static final float MARGIN = 6f;
    /** Velocities a Ctrl+click on a lit step steps through. */
    private static final float[] VELOCITY_CYCLE = {1f, 0.7f, 0.4f};
    private static final float DIM_VELOCITY = 0.35f;
    /** Side of the small square that marks a piano roll note in a step. */
    private static final float NOTE_MARK = 6f;

    private final MusicSession session;
    private int scrollRows;
    private int scrollSteps;
    private int paintChannelId = -1;
    private boolean paintOn;
    private final TreeSet<Integer> paintedSteps = new TreeSet<>();

    StepSequencerView(Skin skin, MusicSession session) {
        super(skin);
        this.session = Objects.requireNonNull(session, "session cannot be null");
        addListener(new StepInput());
    }

    /** True when the channel has a note that starts inside the step, whatever its pitch or exact position. */
    private static boolean startsPianoRollNote(Pattern pattern, Channel channel, int step) {
        int from = step * Song.STEP_TICKS;
        for (Note note : pattern.getNotes(channel.getId())) {
            if (note.tick() >= from && note.tick() < from + Song.STEP_TICKS) {
                return true;
            }
        }
        return false;
    }

    private float stepX(int step) {
        int visible = step - scrollSteps;
        return MARGIN + NAME_WIDTH + visible * (STEP_WIDTH + STEP_GAP) + (visible / 4) * BEAT_GAP;
    }

    private float rowBottom(int row) {
        return getHeight() - HEADER_HEIGHT - (row - scrollRows + 1) * ROW_HEIGHT;
    }

    private int stepAt(float x) {
        float relative = x - MARGIN - NAME_WIDTH;
        if (relative < 0f) {
            return -1;
        }
        float group = 4 * (STEP_WIDTH + STEP_GAP) + BEAT_GAP;
        int groups = (int) (relative / group);
        float inGroup = relative - groups * group;
        int within = (int) (inGroup / (STEP_WIDTH + STEP_GAP));
        if (within > 3 || inGroup - within * (STEP_WIDTH + STEP_GAP) > STEP_WIDTH) {
            return -1;
        }
        return scrollSteps + groups * 4 + within;
    }

    private int rowAt(float y) {
        float fromTop = getHeight() - HEADER_HEIGHT - y;
        if (fromTop < 0f) {
            return -1;
        }
        return scrollRows + (int) (fromTop / ROW_HEIGHT);
    }

    private Pattern pattern() {
        return session.hasSong() ? session.getSelectedPattern() : null;
    }

    private int stepCount() {
        Pattern pattern = pattern();
        return pattern == null ? 0 : StepGrid.stepCount(pattern);
    }

    /** The step the play position is on, or -1 when not playing this pattern. */
    private double playPositionSteps() {
        if (!session.hasSong() || !session.getPlayer().isPlaying() || session.isSongMode()) {
            return -1.0;
        }
        return session.getPlayer().getPositionTicks() / Song.STEP_TICKS;
    }

    @Override
    protected void drawFilled(ShapeRenderer s) {
        s.setColor(color("surface"));
        s.rect(0f, 0f, getWidth(), getHeight());
        Pattern pattern = pattern();
        if (pattern == null) {
            return;
        }
        List<Channel> channels = session.getDocument().getSong().getChannels();
        int steps = stepCount();
        for (int row = scrollRows; row < channels.size(); row++) {
            float y = rowBottom(row);
            if (y + ROW_HEIGHT < 0f) {
                break;
            }
            Channel channel = channels.get(row);
            boolean selected = channel.getId() == session.getSelectedChannelId();
            if (selected) {
                s.setColor(color("border-muted"));
                s.rect(0f, y, getWidth(), ROW_HEIGHT);
            }
            s.setColor(channel.isMuted() ? color("warning") : color("success"));
            s.rect(MARGIN, y + (ROW_HEIGHT - MUTE_SIZE) / 2f, MUTE_SIZE, MUTE_SIZE);
            for (int step = scrollSteps; step < steps; step++) {
                float x = stepX(step);
                if (x > getWidth()) {
                    break;
                }
                Note note = StepGrid.noteAt(pattern, channel, step);
                if (note == null) {
                    s.setColor(((step / 4) % 2 == 0) ? color("surface-variant") : color("surface-elevated"));
                } else {
                    Color accent = color("accent");
                    float bright = DIM_VELOCITY + (1f - DIM_VELOCITY) * note.velocity();
                    s.setColor(accent.r * bright, accent.g * bright, accent.b * bright, 1f);
                }
                s.rect(x, y + 3f, STEP_WIDTH, ROW_HEIGHT - 6f);
                if (note == null && startsPianoRollNote(pattern, channel, step)) {
                    // A note drawn in the piano roll starts in this step: mark it so melodic rows do not look empty
                    s.setColor(color("text-secondary"));
                    s.rect(x + (STEP_WIDTH - NOTE_MARK) / 2f, y + (ROW_HEIGHT - NOTE_MARK) / 2f, NOTE_MARK, NOTE_MARK);
                }
            }
        }
        s.setColor(color("subheader"));
        s.rect(0f, getHeight() - HEADER_HEIGHT, getWidth(), HEADER_HEIGHT);
        double position = playPositionSteps();
        if (position >= 0.0) {
            float x = stepX((int) position) + (float) (position - Math.floor(position)) * STEP_WIDTH;
            s.setColor(color("error"));
            s.rect(x, 0f, 2f, getHeight() - HEADER_HEIGHT);
        }
    }

    @Override
    protected void drawLines(ShapeRenderer s) {
        s.setColor(color("border"));
        s.rect(0f, 0f, getWidth(), getHeight());
    }

    @Override
    protected void drawText(Batch batch, BitmapFont font, float baseX, float baseY) {
        Pattern pattern = pattern();
        if (pattern == null) {
            text(batch, font, "Create or open a song to use the step sequencer", color("text-muted"), baseX, baseY, MARGIN, getHeight() - 8f);
            return;
        }
        int steps = stepCount();
        for (int step = scrollSteps; step < steps; step += 4) {
            float x = stepX(step);
            if (x > getWidth()) {
                break;
            }
            text(batch, font, String.valueOf(step / 4 + 1), color("text-muted"), baseX, baseY, x + 2f, getHeight() - 5f);
        }
        List<Channel> channels = session.getDocument().getSong().getChannels();
        for (int row = scrollRows; row < channels.size(); row++) {
            float y = rowBottom(row);
            if (y + ROW_HEIGHT < 0f) {
                break;
            }
            Channel channel = channels.get(row);
            Color c = channel.isMuted() ? color("text-muted") : color("text-primary");
            text(batch, font, channel.getName(), c, baseX, baseY, MARGIN + MUTE_SIZE + 8f, y + ROW_HEIGHT / 2f + 5f);
        }
    }

    private final class StepInput extends InputListener {
        @Override
        public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
            Pattern pattern = pattern();
            if (pattern == null) {
                return false;
            }
            getStage().setKeyboardFocus(StepSequencerView.this);
            getStage().setScrollFocus(StepSequencerView.this);
            MusicDocument doc = session.getDocument();
            List<Channel> channels = doc.getSong().getChannels();
            int row = rowAt(y);
            if (row < 0 || row >= channels.size()) {
                return false;
            }
            Channel channel = channels.get(row);
            if (x < MARGIN + NAME_WIDTH) {
                if (x >= MARGIN && x <= MARGIN + MUTE_SIZE + 4f && button == Input.Buttons.LEFT) {
                    doc.setChannelMuted(channel, !channel.isMuted());
                } else {
                    session.selectChannel(channel.getId());
                    session.getPlayer().previewNoteOn(channel.getId(), channel.getStepPitch(), 1f);
                    session.getPlayer().previewNoteOff(channel.getId(), channel.getStepPitch());
                }
                return true;
            }
            int step = stepAt(x);
            if (step < 0 || step >= stepCount()) {
                return false;
            }
            session.selectChannel(channel.getId());
            boolean on = StepGrid.isStepOn(pattern, channel, step);
            boolean ctrl = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
            if (button == Input.Buttons.LEFT && ctrl && on) {
                float current = StepGrid.noteAt(pattern, channel, step).velocity();
                doc.setStepVelocity(pattern, channel, step, nextVelocity(current));
                return true;
            }
            paintChannelId = channel.getId();
            paintOn = button == Input.Buttons.LEFT && !on;
            paintedSteps.clear();
            paintedSteps.add(step);
            if (paintOn) {
                session.getPlayer().previewNoteOn(channel.getId(), channel.getStepPitch(), 1f);
                session.getPlayer().previewNoteOff(channel.getId(), channel.getStepPitch());
            }
            return true;
        }

        @Override
        public void touchDragged(InputEvent event, float x, float y, int pointer) {
            if (paintChannelId < 0) {
                return;
            }
            int step = stepAt(x);
            if (step >= 0 && step < stepCount()) {
                paintedSteps.add(step);
            }
        }

        @Override
        public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
            if (paintChannelId < 0) {
                return;
            }
            Pattern pattern = pattern();
            Channel channel = session.getDocument().getSong().getChannel(paintChannelId);
            if (pattern != null && channel != null) {
                applyPaint(pattern, channel);
            }
            paintChannelId = -1;
            paintedSteps.clear();
        }

        @Override
        public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY) {
            boolean shift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
            if (shift) {
                scrollSteps = Math.max(0, Math.min(scrollSteps + (int) Math.signum(amountY) * 4, Math.max(stepCount() - 4, 0)));
            } else {
                int rows = session.hasSong() ? session.getDocument().getSong().getChannels().size() : 0;
                scrollRows = Math.max(0, Math.min(scrollRows + (int) Math.signum(amountY), Math.max(rows - 1, 0)));
            }
            return true;
        }
    }

    private static float nextVelocity(float current) {
        for (int i = 0; i < VELOCITY_CYCLE.length; i++) {
            if (Math.abs(VELOCITY_CYCLE[i] - current) < 0.05f) {
                return VELOCITY_CYCLE[(i + 1) % VELOCITY_CYCLE.length];
            }
        }
        return VELOCITY_CYCLE[0];
    }

    /** One undo step for the whole drag: every painted step set to the state the first one chose. */
    private void applyPaint(Pattern pattern, Channel channel) {
        List<Note> notes = new ArrayList<>(pattern.getNotes(channel.getId()));
        for (int step : paintedSteps) {
            Note existing = StepGrid.noteAt(pattern, channel, step);
            if (paintOn && existing == null) {
                notes.add(new Note(step * Song.STEP_TICKS, Song.STEP_TICKS, channel.getStepPitch(), 1f));
            } else if (!paintOn && existing != null) {
                notes.remove(existing);
            }
        }
        session.getDocument().setNotes(pattern, channel, notes,
            (paintOn ? "Paint steps of " : "Clear steps of ") + channel.getName());
    }
}
