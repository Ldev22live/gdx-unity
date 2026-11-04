package io.github.ldev22live.gdxunity.editor.desktop.ui.music;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import io.github.ldev22live.gdxunity.editor.audio.MusicDocument;
import io.github.ldev22live.gdxunity.editor.audio.MusicSession;
import io.github.ldev22live.gdxunity.engine.audio.song.Pattern;
import io.github.ldev22live.gdxunity.engine.audio.song.PlaylistClip;
import io.github.ldev22live.gdxunity.engine.audio.song.Song;

import java.util.Objects;

/**
 * The playlist: patterns placed along the song's timeline on tracks, one bar per column.
 * <ul>
 *   <li>Click an empty cell: place the selected pattern there. Click a clip: select it; drag to move (snaps to bars).
 *       Drag a clip's right edge: change its length (the pattern repeats to fill it).</li>
 *   <li>Right-click a clip: delete it. Click the ruler: move the play head.</li>
 *   <li>Wheel: scroll time; Ctrl+wheel: zoom.</li>
 * </ul>
 */
final class PlaylistView extends ShapeWidget {
    private static final float LABEL_WIDTH = 64f;
    private static final float RULER_HEIGHT = 18f;
    private static final float TRACK_HEIGHT = 30f;
    private static final float DEFAULT_PIXELS_PER_BAR = 72f;
    private static final float MIN_PIXELS_PER_BAR = 20f;
    private static final float MAX_PIXELS_PER_BAR = 240f;
    private static final float ZOOM_STEP = 1.15f;
    private static final float RESIZE_ZONE = 7f;
    private static final int VISIBLE_TRACKS = 8;

    private enum Drag { NONE, MOVE, RESIZE, SCRUB }

    private final MusicSession session;
    private float pixelsPerBar = DEFAULT_PIXELS_PER_BAR;
    private int scrollBars;
    private int selectedClip = -1;
    private Drag drag = Drag.NONE;
    private int grabBarOffset;
    private int previewIndex = -1;
    private PlaylistClip preview;

    PlaylistView(Skin skin, MusicSession session) {
        super(skin);
        this.session = Objects.requireNonNull(session, "session cannot be null");
        addListener(new ClipInput());
    }

    private float barX(int bar) {
        return LABEL_WIDTH + (bar - scrollBars) * pixelsPerBar;
    }

    private int barAt(float x) {
        return scrollBars + (int) Math.floor((x - LABEL_WIDTH) / pixelsPerBar);
    }

    private float trackBottom(int track) {
        return getHeight() - RULER_HEIGHT - (track + 1) * TRACK_HEIGHT;
    }

    private int trackAt(float y) {
        return (int) Math.floor((getHeight() - RULER_HEIGHT - y) / TRACK_HEIGHT);
    }

    private Song song() {
        return session.hasSong() ? session.getDocument().getSong() : null;
    }

    private PlaylistClip shown(int index) {
        return index == previewIndex && preview != null ? preview : song().getClips().get(index);
    }

    private int clipAt(float x, float y) {
        Song song = song();
        if (song == null) {
            return -1;
        }
        int tick = (int) ((scrollBars + (x - LABEL_WIDTH) / pixelsPerBar) * Song.TICKS_PER_BAR);
        int track = trackAt(y);
        for (int i = song.getClips().size() - 1; i >= 0; i--) {
            PlaylistClip clip = song.getClips().get(i);
            if (clip.track() == track && tick >= clip.startTick() && tick < clip.endTick()) {
                return i;
            }
        }
        return -1;
    }

    @Override
    protected void drawFilled(ShapeRenderer s) {
        s.setColor(color("surface"));
        s.rect(0f, 0f, getWidth(), getHeight());
        Song song = song();
        if (song == null) {
            return;
        }
        for (int track = 0; track < VISIBLE_TRACKS; track++) {
            float y = trackBottom(track);
            s.setColor(track % 2 == 0 ? color("surface-variant") : color("surface-elevated"));
            s.rect(LABEL_WIDTH, y, getWidth() - LABEL_WIDTH, TRACK_HEIGHT);
            s.setColor(color("subheader"));
            s.rect(0f, y, LABEL_WIDTH - 1f, TRACK_HEIGHT - 1f);
        }
        for (int i = 0; i < song.getClips().size(); i++) {
            PlaylistClip clip = shown(i);
            float x = barX(0) + (clip.startTick() / (float) Song.TICKS_PER_BAR) * pixelsPerBar;
            float w = (clip.lengthTicks() / (float) Song.TICKS_PER_BAR) * pixelsPerBar;
            if (x + w < LABEL_WIDTH || x > getWidth() || clip.track() >= VISIBLE_TRACKS) {
                continue;
            }
            float left = Math.max(x, LABEL_WIDTH);
            s.setColor(i == selectedClip ? color("warning") : color("accent"));
            s.rect(left, trackBottom(clip.track()) + 2f, w - (left - x), TRACK_HEIGHT - 4f);
        }
        s.setColor(color("subheader"));
        s.rect(0f, getHeight() - RULER_HEIGHT, getWidth(), RULER_HEIGHT);
        if (session.getPlayer().isPlaying() && session.isSongMode()) {
            float x = barX(0) + (float) (session.getPlayer().getPositionTicks() / Song.TICKS_PER_BAR) * pixelsPerBar;
            s.setColor(color("error"));
            s.rect(x, 0f, 2f, getHeight() - RULER_HEIGHT);
        }
    }

    @Override
    protected void drawLines(ShapeRenderer s) {
        s.setColor(color("border"));
        s.rect(0f, 0f, getWidth(), getHeight());
        if (song() == null) {
            return;
        }
        for (int bar = scrollBars; barX(bar) < getWidth(); bar++) {
            s.setColor(bar % 4 == 0 ? color("text-muted") : color("border-muted"));
            s.line(barX(bar), 0f, barX(bar), getHeight() - RULER_HEIGHT);
        }
        for (int i = 0; i < song().getClips().size(); i++) {
            PlaylistClip clip = shown(i);
            float x = barX(0) + (clip.startTick() / (float) Song.TICKS_PER_BAR) * pixelsPerBar;
            float w = (clip.lengthTicks() / (float) Song.TICKS_PER_BAR) * pixelsPerBar;
            if (x + w >= LABEL_WIDTH && x <= getWidth() && clip.track() < VISIBLE_TRACKS) {
                s.setColor(color("background"));
                s.rect(Math.max(x, LABEL_WIDTH), trackBottom(clip.track()) + 2f, w - Math.max(LABEL_WIDTH - x, 0f), TRACK_HEIGHT - 4f);
            }
        }
    }

    @Override
    protected void drawText(Batch batch, BitmapFont font, float baseX, float baseY) {
        Song song = song();
        if (song == null) {
            text(batch, font, "Create or open a song to arrange patterns", color("text-muted"), baseX, baseY, 8f, getHeight() - 8f);
            return;
        }
        for (int bar = scrollBars; barX(bar) < getWidth(); bar += pixelsPerBar < 36f ? 4 : 1) {
            text(batch, font, String.valueOf(bar + 1), color("text-muted"), baseX, baseY, barX(bar) + 3f, getHeight() - 4f);
        }
        for (int track = 0; track < VISIBLE_TRACKS; track++) {
            text(batch, font, "Track " + (track + 1), color("text-secondary"), baseX, baseY, 6f, trackBottom(track) + TRACK_HEIGHT / 2f + 5f);
        }
        for (int i = 0; i < song.getClips().size(); i++) {
            PlaylistClip clip = shown(i);
            Pattern pattern = song.getPattern(clip.patternId());
            float x = barX(0) + (clip.startTick() / (float) Song.TICKS_PER_BAR) * pixelsPerBar;
            if (pattern != null && x + 8f > LABEL_WIDTH && x < getWidth() && clip.track() < VISIBLE_TRACKS) {
                text(batch, font, pattern.getName(), color("text-on-accent"), baseX, baseY, Math.max(x, LABEL_WIDTH) + 5f,
                    trackBottom(clip.track()) + TRACK_HEIGHT / 2f + 5f);
            }
        }
    }

    private final class ClipInput extends InputListener {
        @Override
        public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
            Song song = song();
            if (song == null) {
                return false;
            }
            getStage().setKeyboardFocus(PlaylistView.this);
            getStage().setScrollFocus(PlaylistView.this);
            MusicDocument doc = session.getDocument();
            if (y > getHeight() - RULER_HEIGHT) {
                if (x > LABEL_WIDTH && button == Input.Buttons.LEFT) {
                    drag = Drag.SCRUB;
                    session.setSongMode(true);
                    session.getPlayer().seek(Math.max(barAt(x), 0) * (double) Song.TICKS_PER_BAR);
                    return true;
                }
                return false;
            }
            if (x < LABEL_WIDTH) {
                return false;
            }
            int index = clipAt(x, y);
            if (button == Input.Buttons.RIGHT) {
                if (index >= 0) {
                    selectedClip = -1;
                    doc.deleteClip(index);
                    return true;
                }
                return false;
            }
            if (button != Input.Buttons.LEFT) {
                return false;
            }
            if (index >= 0) {
                selectedClip = index;
                PlaylistClip clip = song.getClips().get(index);
                float clipEnd = barX(0) + (clip.endTick() / (float) Song.TICKS_PER_BAR) * pixelsPerBar;
                drag = clipEnd - x <= RESIZE_ZONE ? Drag.RESIZE : Drag.MOVE;
                grabBarOffset = barAt(x) - clip.startTick() / Song.TICKS_PER_BAR;
                previewIndex = index;
                preview = clip;
                return true;
            }
            int track = trackAt(y);
            Pattern pattern = session.getSelectedPattern();
            if (pattern != null && track >= 0 && track < VISIBLE_TRACKS && barAt(x) >= 0) {
                if (doc.placeClip(pattern.getId(), track, barAt(x) * Song.TICKS_PER_BAR, pattern.getLengthTicks())) {
                    selectedClip = song.getClips().size() - 1;
                } else {
                    session.getPlayer().refresh();
                }
            }
            return true;
        }

        @Override
        public void touchDragged(InputEvent event, float x, float y, int pointer) {
            Song song = song();
            if (song == null) {
                return;
            }
            switch (drag) {
                case SCRUB -> session.getPlayer().seek(Math.max(barAt(x), 0) * (double) Song.TICKS_PER_BAR);
                case MOVE -> {
                    PlaylistClip original = song.getClips().get(previewIndex);
                    int track = Math.max(0, Math.min(trackAt(y), VISIBLE_TRACKS - 1));
                    int bar = Math.max(barAt(x) - grabBarOffset, 0);
                    PlaylistClip moved = new PlaylistClip(original.patternId(), track, bar * Song.TICKS_PER_BAR, original.lengthTicks());
                    preview = song.canPlaceClip(moved, previewIndex) ? moved : preview;
                }
                case RESIZE -> {
                    PlaylistClip original = song.getClips().get(previewIndex);
                    int endBar = Math.max(barAt(x) + 1, original.startTick() / Song.TICKS_PER_BAR + 1);
                    PlaylistClip resized = original.withLengthTicks(endBar * Song.TICKS_PER_BAR - original.startTick());
                    preview = song.canPlaceClip(resized, previewIndex) ? resized : preview;
                }
                default -> { }
            }
        }

        @Override
        public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
            if (previewIndex >= 0 && preview != null && drag != Drag.NONE && drag != Drag.SCRUB) {
                MusicDocument doc = session.getDocument();
                PlaylistClip original = song().getClips().get(previewIndex);
                if (!preview.equals(original)) {
                    if (drag == Drag.MOVE) {
                        doc.moveClip(previewIndex, preview.startTick(), preview.track());
                    } else {
                        doc.resizeClip(previewIndex, preview.lengthTicks());
                    }
                }
            }
            drag = Drag.NONE;
            previewIndex = -1;
            preview = null;
        }

        @Override
        public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY) {
            boolean ctrl = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
            if (ctrl) {
                float factor = amountY < 0 ? ZOOM_STEP : 1f / ZOOM_STEP;
                pixelsPerBar = Math.max(MIN_PIXELS_PER_BAR, Math.min(pixelsPerBar * factor, MAX_PIXELS_PER_BAR));
            } else {
                scrollBars = Math.max(0, scrollBars + (int) Math.signum(amountY) * 2);
            }
            return true;
        }

        @Override
        public boolean keyDown(InputEvent event, int keycode) {
            if ((keycode == Input.Keys.DEL || keycode == Input.Keys.FORWARD_DEL) && selectedClip >= 0 && session.hasSong()) {
                session.getDocument().deleteClip(selectedClip);
                selectedClip = -1;
                return true;
            }
            return false;
        }
    }
}
