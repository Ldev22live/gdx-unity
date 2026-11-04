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
import io.github.ldev22live.gdxunity.editor.audio.GridSnap;
import io.github.ldev22live.gdxunity.editor.audio.MusicDocument;
import io.github.ldev22live.gdxunity.editor.audio.MusicSession;
import io.github.ldev22live.gdxunity.editor.audio.PianoRollOps;
import io.github.ldev22live.gdxunity.engine.audio.song.Channel;
import io.github.ldev22live.gdxunity.engine.audio.song.Note;
import io.github.ldev22live.gdxunity.engine.audio.song.Pattern;
import io.github.ldev22live.gdxunity.engine.audio.song.Song;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The piano roll: the notes of the selected channel in the selected pattern on a keyboard-by-time grid.
 * <ul>
 *   <li>Click empty space: draw a note (drag sideways to set its length). Click a note: select; drag to move.
 *       Drag a note's right edge: resize. Shift+click adds to the selection, Ctrl+drag box-selects.</li>
 *   <li>Right-click a note: delete it (or the whole selection if it is part of it).</li>
 *   <li>Click the keyboard: hear a note. Drag in the velocity lane: set velocity. Click the ruler: move the play head.</li>
 *   <li>Keys: Delete, Ctrl+A select all, Ctrl+D duplicate, Q quantize, arrows nudge (Shift for an octave or a bar),
 *       Ctrl+Z / Ctrl+Y undo / redo.</li>
 *   <li>Wheel: scroll pitch; Shift+wheel: scroll time; Ctrl+wheel: zoom time.</li>
 * </ul>
 */
final class PianoRollView extends ShapeWidget {
    private static final float KEY_WIDTH = 56f;
    private static final float RULER_HEIGHT = 18f;
    private static final float VELOCITY_HEIGHT = 56f;
    private static final float ROW_HEIGHT = 14f;
    private static final float DEFAULT_PIXELS_PER_TICK = 0.5f;
    private static final float MIN_PIXELS_PER_TICK = 0.1f;
    private static final float MAX_PIXELS_PER_TICK = 3f;
    private static final float ZOOM_STEP = 1.15f;
    private static final float RESIZE_ZONE = 6f;
    private static final float NEW_NOTE_VELOCITY = 0.8f;
    private static final float VELOCITY_BAR_WIDTH = 4f;
    private static final int PITCH_SCROLL_STEP = 3;
    private static final int DEFAULT_TOP_PITCH = 83;
    private static final int MIDDLE_C = 60;
    /** Smallest grid line spacing drawn, in pixels, so a fine grid does not turn into a smear when zoomed out. */
    private static final float MIN_GRID_SPACING = 6f;
    private static final float KEY_PREVIEW_VELOCITY = 0.8f;

    private enum Drag { NONE, KEYBOARD, MOVE, RESIZE, CREATE, BOX, VELOCITY, SCRUB }

    private final MusicSession session;
    private float pixelsPerTick = DEFAULT_PIXELS_PER_TICK;
    private int scrollTick;
    private int topPitch = DEFAULT_TOP_PITCH;
    private int viewKey = Integer.MIN_VALUE;

    private Drag drag = Drag.NONE;
    private List<Note> dragNotes;
    private Set<Note> dragSelection;
    private Note dragTarget;
    private int grabTick;
    private int grabPitch;
    private int createStart;
    private int createPitch;
    private PianoRollOps.Result preview;
    private int heldPitch = -1;
    /** Pitch of the preview note sounding while the mouse is down, or -1. */
    private int previewPitch = -1;
    private float boxX0;
    private float boxY0;
    private float boxX1;
    private float boxY1;

    PianoRollView(Skin skin, MusicSession session) {
        super(skin);
        this.session = Objects.requireNonNull(session, "session cannot be null");
        addListener(new RollInput());
    }

    // ---- coordinates ----

    private float gridTop() {
        return getHeight() - RULER_HEIGHT;
    }

    private float noteX(int tick) {
        return KEY_WIDTH + (tick - scrollTick) * pixelsPerTick;
    }

    private int tickAt(float x) {
        return scrollTick + Math.round((x - KEY_WIDTH) / pixelsPerTick);
    }

    private float rowBottom(int pitch) {
        return gridTop() - (topPitch - pitch + 1) * ROW_HEIGHT;
    }

    private int pitchAt(float y) {
        return topPitch - (int) Math.floor((gridTop() - y) / ROW_HEIGHT);
    }

    private int visiblePitchRows() {
        return Math.max((int) ((gridTop() - VELOCITY_HEIGHT) / ROW_HEIGHT), 1);
    }

    private Pattern pattern() {
        return session.hasSong() ? session.getSelectedPattern() : null;
    }

    private Channel channel() {
        return session.hasSong() ? session.getDocument().getSong().getChannel(session.getSelectedChannelId()) : null;
    }

    private List<Note> notes() {
        Pattern pattern = pattern();
        Channel channel = channel();
        return pattern == null || channel == null ? List.of() : pattern.getNotes(channel.getId());
    }

    /** What to draw: the notes as they will be once the current drag ends. */
    private List<Note> shownNotes() {
        return preview != null ? preview.notes() : notes();
    }

    private Set<Note> shownSelection() {
        return preview != null ? preview.selection() : session.getSelectedNotes();
    }

    private int snapTicks() {
        return session.getSnap().getTicks();
    }

    /** Centres the view on the channel's notes when another channel or pattern is chosen. */
    private void followSelection() {
        Pattern pattern = pattern();
        Channel channel = channel();
        int key = pattern == null || channel == null ? -1 : pattern.getId() * 1000 + channel.getId();
        if (key == viewKey) {
            return;
        }
        viewKey = key;
        scrollTick = 0;
        List<Note> notes = notes();
        if (notes.isEmpty()) {
            topPitch = DEFAULT_TOP_PITCH;
            return;
        }
        int low = Note.MAX_PITCH;
        int high = Note.MIN_PITCH;
        for (Note note : notes) {
            low = Math.min(low, note.pitch());
            high = Math.max(high, note.pitch());
        }
        topPitch = Math.min(Note.MAX_PITCH, (low + high) / 2 + visiblePitchRows() / 2);
    }

    // ---- drawing ----

    @Override
    protected void drawFilled(ShapeRenderer s) {
        followSelection();
        s.setColor(color("surface"));
        s.rect(0f, 0f, getWidth(), getHeight());
        Pattern pattern = pattern();
        if (pattern == null || channel() == null) {
            return;
        }
        float right = getWidth();
        for (int pitch = topPitch; pitch >= Math.max(topPitch - visiblePitchRows(), 0); pitch--) {
            float y = rowBottom(pitch);
            s.setColor(Note.isBlackKey(pitch) ? color("surface-variant") : color("surface-elevated"));
            s.rect(KEY_WIDTH, y, right - KEY_WIDTH, ROW_HEIGHT);
            s.setColor(Note.isBlackKey(pitch) ? color("black") : color("text-primary"));
            float keyLength = Note.isBlackKey(pitch) ? KEY_WIDTH * 0.62f : KEY_WIDTH - 1f;
            s.rect(0f, y + 1f, keyLength, ROW_HEIGHT - 1f);
        }
        drawGridFill(s, pattern);
        List<Note> shown = shownNotes();
        Set<Note> selected = shownSelection();
        for (Note note : shown) {
            float x = noteX(note.tick());
            float w = Math.max(note.length() * pixelsPerTick, 2f);
            if (x + w < KEY_WIDTH || x > right || note.pitch() > topPitch || note.pitch() < topPitch - visiblePitchRows()) {
                continue;
            }
            float clippedX = Math.max(x, KEY_WIDTH);
            Color base = selected.contains(note) ? color("warning") : color("accent");
            float bright = 0.45f + 0.55f * note.velocity();
            s.setColor(base.r * bright, base.g * bright, base.b * bright, 1f);
            s.rect(clippedX, rowBottom(note.pitch()) + 1f, w - (clippedX - x), ROW_HEIGHT - 2f);
        }
        s.setColor(color("surface-variant"));
        s.rect(KEY_WIDTH, 0f, right - KEY_WIDTH, VELOCITY_HEIGHT);
        for (Note note : shown) {
            float x = noteX(note.tick());
            if (x < KEY_WIDTH || x > right) {
                continue;
            }
            s.setColor(selected.contains(note) ? color("warning") : color("accent"));
            s.rect(x, 2f, VELOCITY_BAR_WIDTH, (VELOCITY_HEIGHT - 6f) * note.velocity() + 1f);
        }
        s.setColor(color("subheader"));
        s.rect(0f, gridTop(), right, RULER_HEIGHT);
        if (session.getPlayer().isPlaying() && !session.isSongMode()) {
            s.setColor(color("error"));
            s.rect(noteX((int) session.getPlayer().getPositionTicks()), VELOCITY_HEIGHT - 2f, 2f, gridTop() - VELOCITY_HEIGHT + 2f);
        }
        if (heldPitch >= 0) {
            s.setColor(color("accent"));
            s.rect(0f, rowBottom(heldPitch) + 1f, KEY_WIDTH - 1f, ROW_HEIGHT - 1f);
        }
    }

    private void drawGridFill(ShapeRenderer s, Pattern pattern) {
        int end = pattern.getLengthTicks();
        s.setColor(color("surface"));
        s.rect(Math.max(noteX(end), KEY_WIDTH), VELOCITY_HEIGHT, getWidth(), gridTop() - VELOCITY_HEIGHT);
    }

    @Override
    protected void drawLines(ShapeRenderer s) {
        s.setColor(color("border"));
        s.rect(0f, 0f, getWidth(), getHeight());
        Pattern pattern = pattern();
        if (pattern == null || channel() == null) {
            return;
        }
        int gridTicks = snapTicks();
        while (gridTicks * pixelsPerTick < MIN_GRID_SPACING) {
            gridTicks *= 2;
        }
        int first = Math.max(scrollTick / gridTicks * gridTicks, 0);
        for (int tick = first; tick <= pattern.getLengthTicks(); tick += gridTicks) {
            float x = noteX(tick);
            if (x > getWidth()) {
                break;
            }
            if (x < KEY_WIDTH) {
                continue;
            }
            boolean bar = tick % Song.TICKS_PER_BAR == 0;
            boolean beat = tick % Song.TICKS_PER_BEAT == 0;
            s.setColor(bar ? color("text-muted") : beat ? color("border") : color("border-muted"));
            s.line(x, VELOCITY_HEIGHT, x, gridTop());
        }
        s.setColor(color("border"));
        s.line(KEY_WIDTH, VELOCITY_HEIGHT, getWidth(), VELOCITY_HEIGHT);
        if (drag == Drag.BOX) {
            s.setColor(color("warning"));
            s.rect(Math.min(boxX0, boxX1), Math.min(boxY0, boxY1), Math.abs(boxX1 - boxX0), Math.abs(boxY1 - boxY0));
        }
    }

    @Override
    protected void drawText(Batch batch, BitmapFont font, float baseX, float baseY) {
        if (pattern() == null || channel() == null) {
            text(batch, font, "Select a channel in the rack to edit its notes", color("text-muted"), baseX, baseY, 8f, getHeight() - 8f);
            return;
        }
        for (int pitch = topPitch; pitch >= Math.max(topPitch - visiblePitchRows(), 0); pitch--) {
            if (pitch % 12 == 0) {
                text(batch, font, Note.pitchName(pitch), color("surface"), baseX, baseY, 4f, rowBottom(pitch) + ROW_HEIGHT - 2f);
            }
        }
        int gridTicks = Song.TICKS_PER_BAR;
        while (gridTicks * pixelsPerTick < 28f) {
            gridTicks *= 2;
        }
        for (int tick = Math.max(scrollTick / gridTicks * gridTicks, 0); tick <= pattern().getLengthTicks(); tick += gridTicks) {
            float x = noteX(tick);
            if (x > getWidth()) {
                break;
            }
            if (x >= KEY_WIDTH) {
                text(batch, font, String.valueOf(tick / Song.TICKS_PER_BAR + 1), color("text-muted"), baseX, baseY, x + 3f, getHeight() - 4f);
            }
        }
        text(batch, font, "Velocity", color("text-muted"), baseX, baseY, 4f, VELOCITY_HEIGHT - 6f);
    }

    // ---- editing ----

    private void commit(PianoRollOps.Result result, String description) {
        session.getDocument().setNotes(pattern(), channel(), result.notes(), description);
        session.setSelectedNotes(result.selection());
    }

    private boolean shiftDown() {
        return Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
    }

    private boolean ctrlDown() {
        return Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
    }

    private Note noteNear(float x) {
        Note best = null;
        float bestDistance = VELOCITY_BAR_WIDTH + 4f;
        for (Note note : notes()) {
            float distance = Math.abs(noteX(note.tick()) + VELOCITY_BAR_WIDTH / 2f - x);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = note;
            }
        }
        return best;
    }

    private final class RollInput extends InputListener {
        @Override
        public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
            if (pattern() == null || channel() == null) {
                return false;
            }
            getStage().setKeyboardFocus(PianoRollView.this);
            getStage().setScrollFocus(PianoRollView.this);
            if (button == Input.Buttons.RIGHT) {
                return deleteAt(x, y);
            }
            if (button != Input.Buttons.LEFT) {
                return false;
            }
            if (y > gridTop()) {
                drag = Drag.SCRUB;
                session.getPlayer().seek(Math.max(0, session.getSnap().snap(tickAt(x))));
                return true;
            }
            if (x < KEY_WIDTH && y >= VELOCITY_HEIGHT) {
                heldPitch = Math.max(Note.MIN_PITCH, Math.min(pitchAt(y), Note.MAX_PITCH));
                session.getPlayer().previewNoteOn(channel().getId(), heldPitch, KEY_PREVIEW_VELOCITY);
                previewPitch = heldPitch;
                drag = Drag.KEYBOARD;
                return true;
            }
            if (y < VELOCITY_HEIGHT) {
                return startVelocityDrag(x, y);
            }
            return startGridDrag(x, y);
        }

        private boolean deleteAt(float x, float y) {
            if (y < VELOCITY_HEIGHT || x < KEY_WIDTH || y > gridTop()) {
                return false;
            }
            Note hit = PianoRollOps.noteAt(notes(), tickAt(x), pitchAt(y));
            if (hit == null) {
                return false;
            }
            Set<Note> targets = session.getSelectedNotes().contains(hit) ? new LinkedHashSet<>(session.getSelectedNotes()) : Set.of(hit);
            session.getDocument().setNotes(pattern(), channel(), PianoRollOps.delete(notes(), targets), "Delete notes");
            session.setSelectedNotes(Set.of());
            return true;
        }

        private boolean startVelocityDrag(float x, float y) {
            Note near = noteNear(x);
            if (near == null) {
                return false;
            }
            dragNotes = notes();
            dragSelection = session.getSelectedNotes().contains(near) ? new LinkedHashSet<>(session.getSelectedNotes()) : Set.of(near);
            drag = Drag.VELOCITY;
            updateVelocity(y);
            return true;
        }

        private void updateVelocity(float y) {
            float velocity = (y - 3f) / (VELOCITY_HEIGHT - 6f);
            preview = PianoRollOps.setVelocity(dragNotes, dragSelection, velocity);
        }

        private boolean startGridDrag(float x, float y) {
            int tick = tickAt(x);
            int pitch = pitchAt(y);
            if (pitch < Note.MIN_PITCH || pitch > Note.MAX_PITCH || tick < 0 || tick >= pattern().getLengthTicks()) {
                return false;
            }
            Note hit = PianoRollOps.noteAt(notes(), tick, pitch);
            dragNotes = notes();
            if (hit != null) {
                if (shiftDown()) {
                    Set<Note> toggled = new LinkedHashSet<>(session.getSelectedNotes());
                    if (!toggled.remove(hit)) {
                        toggled.add(hit);
                    }
                    session.setSelectedNotes(toggled);
                } else if (!session.getSelectedNotes().contains(hit)) {
                    session.setSelectedNotes(Set.of(hit));
                }
                dragSelection = new LinkedHashSet<>(session.getSelectedNotes());
                dragTarget = hit;
                grabTick = tick;
                grabPitch = pitch;
                drag = noteX(hit.endTick()) - x <= RESIZE_ZONE ? Drag.RESIZE : Drag.MOVE;
                session.getPlayer().previewNoteOn(channel().getId(), hit.pitch(), hit.velocity());
                previewPitch = hit.pitch();
                heldPitch = -1;
                return true;
            }
            if (ctrlDown()) {
                drag = Drag.BOX;
                boxX0 = x;
                boxY0 = y;
                boxX1 = x;
                boxY1 = y;
                return true;
            }
            createStart = session.getSnap().floor(tick);
            createPitch = pitch;
            Note created = new Note(createStart, snapTicks(), pitch, NEW_NOTE_VELOCITY);
            preview = PianoRollOps.add(dragNotes, created);
            drag = Drag.CREATE;
            session.getPlayer().previewNoteOn(channel().getId(), pitch, NEW_NOTE_VELOCITY);
            previewPitch = pitch;
            return true;
        }

        @Override
        public void touchDragged(InputEvent event, float x, float y, int pointer) {
            switch (drag) {
                case SCRUB -> session.getPlayer().seek(Math.max(0, session.getSnap().snap(tickAt(x))));
                case MOVE -> {
                    int dt = session.getSnap().snap(tickAt(x) - grabTick);
                    int dp = pitchAt(y) - grabPitch;
                    preview = PianoRollOps.move(dragNotes, dragSelection, dt, dp);
                }
                case RESIZE -> {
                    int delta = session.getSnap().snap(tickAt(x) - dragTarget.endTick());
                    preview = dragSelection.size() > 1
                        ? PianoRollOps.resizeAll(dragNotes, dragSelection, delta)
                        : PianoRollOps.resize(dragNotes, dragTarget, dragTarget.length() + delta);
                }
                case CREATE -> {
                    int end = Math.max(session.getSnap().snap(tickAt(x)), createStart + snapTicks());
                    Note created = new Note(createStart, Math.min(end, pattern().getLengthTicks()) - createStart, createPitch, NEW_NOTE_VELOCITY);
                    preview = PianoRollOps.add(dragNotes, created);
                }
                case BOX -> {
                    boxX1 = x;
                    boxY1 = y;
                }
                case VELOCITY -> updateVelocity(y);
                default -> { }
            }
        }

        @Override
        public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
            if (button != Input.Buttons.LEFT) {
                return;
            }
            if (previewPitch >= 0 && channel() != null) {
                // Releasing the mouse ends the note that was sounding under it
                session.getPlayer().previewNoteOff(channel().getId(), previewPitch);
            }
            previewPitch = -1;
            switch (drag) {
                case MOVE -> {
                    if (preview != null && !preview.notes().equals(dragNotes)) {
                        commit(preview, "Move notes");
                    }
                }
                case RESIZE -> {
                    if (preview != null && !preview.notes().equals(dragNotes)) {
                        commit(preview, "Resize notes");
                    }
                }
                case CREATE -> commit(preview, "Draw note");
                case VELOCITY -> {
                    if (preview != null && !preview.notes().equals(dragNotes)) {
                        commit(preview, "Change velocity");
                    }
                }
                case BOX -> {
                    int t0 = tickAt(Math.min(boxX0, boxX1));
                    int t1 = tickAt(Math.max(boxX0, boxX1));
                    Set<Note> inside = PianoRollOps.notesIn(notes(), t0, t1, pitchAt(Math.max(boxY0, boxY1)), pitchAt(Math.min(boxY0, boxY1)));
                    session.setSelectedNotes(inside);
                }
                default -> { }
            }
            drag = Drag.NONE;
            preview = null;
            dragNotes = null;
            dragSelection = null;
            dragTarget = null;
            heldPitch = -1;
            createPitch = -1;
        }

        @Override
        public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY) {
            if (ctrlDown()) {
                float factor = amountY < 0 ? ZOOM_STEP : 1f / ZOOM_STEP;
                pixelsPerTick = Math.max(MIN_PIXELS_PER_TICK, Math.min(pixelsPerTick * factor, MAX_PIXELS_PER_TICK));
            } else if (shiftDown()) {
                scrollTick = Math.max(0, scrollTick + (int) Math.signum(amountY) * Song.TICKS_PER_BEAT);
            } else {
                topPitch = Math.max(visiblePitchRows() - 1, Math.min(topPitch - (int) Math.signum(amountY) * PITCH_SCROLL_STEP, Note.MAX_PITCH));
            }
            return true;
        }

        @Override
        public boolean keyDown(InputEvent event, int keycode) {
            MusicDocument doc = session.getDocument();
            if (doc == null) {
                return false;
            }
            boolean ctrl = ctrlDown();
            if (ctrl && keycode == Input.Keys.Z) {
                doc.undo();
                return true;
            }
            if (ctrl && (keycode == Input.Keys.Y || (keycode == Input.Keys.Z && shiftDown()))) {
                doc.redo();
                return true;
            }
            if (pattern() == null || channel() == null) {
                return false;
            }
            Set<Note> selection = session.getSelectedNotes();
            switch (keycode) {
                case Input.Keys.DEL, Input.Keys.FORWARD_DEL -> {
                    if (!selection.isEmpty()) {
                        doc.setNotes(pattern(), channel(), PianoRollOps.delete(notes(), selection), "Delete notes");
                        session.setSelectedNotes(Set.of());
                    }
                }
                case Input.Keys.A -> {
                    if (ctrl) {
                        session.setSelectedNotes(new LinkedHashSet<>(notes()));
                    }
                }
                case Input.Keys.D -> {
                    if (ctrl && !selection.isEmpty()) {
                        int span = selectionSpan(selection);
                        commit(PianoRollOps.duplicate(notes(), selection, span), "Duplicate notes");
                    }
                }
                case Input.Keys.Q -> commit(PianoRollOps.quantize(notes(), selection, session.getSnap(), 1f), "Quantize");
                case Input.Keys.UP -> nudge(selection, 0, shiftDown() ? 12 : 1);
                case Input.Keys.DOWN -> nudge(selection, 0, shiftDown() ? -12 : -1);
                case Input.Keys.RIGHT -> nudge(selection, shiftDown() ? Song.TICKS_PER_BAR : snapTicks(), 0);
                case Input.Keys.LEFT -> nudge(selection, shiftDown() ? -Song.TICKS_PER_BAR : -snapTicks(), 0);
                case Input.Keys.ESCAPE -> session.setSelectedNotes(Set.of());
                default -> {
                    return false;
                }
            }
            return true;
        }

        private void nudge(Set<Note> selection, int ticks, int pitches) {
            if (!selection.isEmpty()) {
                commit(PianoRollOps.move(notes(), selection, ticks, pitches), "Move notes");
            }
        }
    }

    /** Distance from the first selected note's start to the end of the last one, rounded up to whole grid cells. */
    private int selectionSpan(Set<Note> selection) {
        int start = Integer.MAX_VALUE;
        int end = 0;
        for (Note note : selection) {
            start = Math.min(start, note.tick());
            end = Math.max(end, note.endTick());
        }
        int grid = GridSnap.SIXTEENTH.getTicks();
        return Math.max(((end - start) + grid - 1) / grid * grid, grid);
    }
}
