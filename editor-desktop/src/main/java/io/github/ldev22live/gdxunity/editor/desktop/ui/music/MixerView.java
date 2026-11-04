package io.github.ldev22live.gdxunity.editor.desktop.ui.music;

import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import io.github.ldev22live.gdxunity.editor.audio.MusicDocument;
import io.github.ldev22live.gdxunity.editor.audio.MusicSession;
import io.github.ldev22live.gdxunity.editor.desktop.ui.menu.ContextMenu;
import io.github.ldev22live.gdxunity.engine.audio.Effect;
import io.github.ldev22live.gdxunity.engine.audio.Effects;
import io.github.ldev22live.gdxunity.engine.audio.Mixer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The mixer: one column per insert and the master, each with a level meter, volume fader, pan, mute and solo, and
 * up to {@link Mixer#MAX_EFFECTS} effects. Click an effect to edit its settings on the right. Fader moves are applied
 * live and recorded as one undo step when released.
 */
final class MixerView extends Table {
    private static final float STRIP_WIDTH = 84f;
    private static final float DETAIL_WIDTH = 220f;
    private static final float PAD = 4f;
    private static final float METER_WIDTH = 8f;
    private static final int MASTER = -1;
    /** Width of the small button that removes an effect. */
    private static final float EFFECT_REMOVE_SIZE = 20f;

    private final Skin skin;
    private final MusicSession session;
    private final ContextMenu menu;
    private final List<MeterWidget> meters = new ArrayList<>();
    private Effect selectedEffect;
    private int selectedEffectStrip = Integer.MIN_VALUE;

    MixerView(Skin skin, MusicSession session) {
        super(skin);
        this.skin = Objects.requireNonNull(skin, "skin cannot be null");
        this.session = Objects.requireNonNull(session, "session cannot be null");
        this.menu = new ContextMenu(skin);
        setBackground("panel-bg");
        rebuild();
    }

    /** Rebuilds all columns from the song; call after the song or its effects changed. */
    void rebuild() {
        clearChildren();
        for (MeterWidget meter : meters) {
            meter.dispose();
        }
        meters.clear();
        if (!session.hasSong()) {
            add(new Label("Create or open a song to use the mixer", skin, "muted")).pad(10f);
            return;
        }
        Mixer mixer = session.getDocument().getSong().getMixer();
        Table strips = new Table(skin);
        strips.top().left();
        for (int i = 0; i < mixer.getStripCount(); i++) {
            strips.add(buildStrip(i)).width(STRIP_WIDTH).top().pad(PAD);
        }
        strips.add(buildStrip(MASTER)).width(STRIP_WIDTH).top().pad(PAD);
        ScrollPane scroll = new ScrollPane(strips, skin);
        scroll.setScrollingDisabled(false, true);
        add(scroll).grow();
        add(buildDetail()).width(DETAIL_WIDTH).top().pad(PAD);
    }

    private Table buildStrip(int index) {
        MusicDocument doc = session.getDocument();
        Mixer.Strip strip = doc.strip(index);
        Table column = new Table(skin);
        column.setBackground("elevated-bg");
        column.pad(PAD);
        Label name = new Label(strip.getName(), skin);
        name.setEllipsis(true);
        column.add(name).width(STRIP_WIDTH - 2 * PAD - 4f).padBottom(PAD).row();

        MeterWidget meter = new MeterWidget(skin, strip);
        meters.add(meter);
        ValueBar fader = new ValueBar(skin, "Vol", 0f, Mixer.MAX_VOLUME, 0.8f, true, false, "%.2f",
            strip::getVolume, strip::setVolume,
            (before, after) -> doc.recordChange("Set " + strip.getName() + " volume", before, after, strip::setVolume));
        Table faderRow = new Table(skin);
        faderRow.add(meter).width(METER_WIDTH * 2 + 2f).growY().padRight(PAD);
        faderRow.add(fader).growY();
        column.add(faderRow).growY().row();

        ValueBar pan = new ValueBar(skin, "Pan", -1f, 1f, 0f, false, false, "%.2f",
            strip::getPan, strip::setPan,
            (before, after) -> doc.recordChange("Set " + strip.getName() + " pan", before, after, strip::setPan));
        column.add(pan).growX().height(18f).padTop(PAD).row();

        Table buttons = new Table(skin);
        buttons.add(toggle("M", strip.isMuted(), muted -> doc.recordChange("Mute " + strip.getName(), strip.isMuted(), muted, strip::setMuted)))
            .size(30f, 22f).padRight(2f);
        buttons.add(toggle("S", strip.isSolo(), solo -> doc.recordChange("Solo " + strip.getName(), strip.isSolo(), solo, strip::setSolo)))
            .size(30f, 22f);
        column.add(buttons).padTop(PAD).row();

        for (Effect effect : strip.getEffects()) {
            Table effectRow = new Table(skin);
            TextButton open = new TextButton(effect.getType(), skin, effect == selectedEffect ? "active" : "flat");
            open.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    selectedEffect = effect;
                    selectedEffectStrip = index;
                    rebuild();
                }
            });
            TextButton remove = new TextButton("x", skin, "flat");
            remove.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    if (selectedEffect == effect) {
                        selectedEffect = null;
                    }
                    doc.removeEffect(index, effect);
                }
            });
            effectRow.add(open).width(STRIP_WIDTH - 2 * PAD - EFFECT_REMOVE_SIZE - 4f).height(20f);
            effectRow.add(remove).size(EFFECT_REMOVE_SIZE, 20f);
            column.add(effectRow).padTop(2f).row();
        }
        if (strip.getEffects().size() < Mixer.MAX_EFFECTS) {
            TextButton add = new TextButton("+ FX", skin, "flat");
            add.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    List<ContextMenu.Item> items = new ArrayList<>();
                    for (String type : Effects.types()) {
                        items.add(new ContextMenu.Item(type, "", true, () -> {
                            selectedEffect = doc.addEffect(index, type);
                            selectedEffectStrip = index;
                        }));
                    }
                    var position = add.localToStageCoordinates(new com.badlogic.gdx.math.Vector2(0f, 0f));
                    menu.show(add.getStage(), position.x, position.y, items);
                }
            });
            column.add(add).growX().height(20f).padTop(PAD).row();
        }
        return column;
    }

    private TextButton toggle(String text, boolean on, java.util.function.Consumer<Boolean> change) {
        TextButton button = new TextButton(text, skin, "flat");
        button.setChecked(on);
        button.setColor(on ? skin.getColor("warning") : skin.getColor("text-primary"));
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                change.accept(button.isChecked());
            }
        });
        return button;
    }

    /** Settings of the selected effect, one bar per parameter. */
    private Table buildDetail() {
        Table detail = new Table(skin);
        detail.setBackground("elevated-bg");
        detail.pad(PAD);
        detail.top().left();
        MusicDocument doc = session.getDocument();
        boolean stillThere = selectedEffect != null && selectedEffectStrip != Integer.MIN_VALUE
            && doc.strip(selectedEffectStrip).getEffects().contains(selectedEffect);
        if (!stillThere) {
            detail.add(new Label("Select an effect to edit it", skin, "muted")).pad(6f);
            return detail;
        }
        Effect effect = selectedEffect;
        detail.add(new Label(effect.getType() + " on " + doc.strip(selectedEffectStrip).getName(), skin, "section")).left().padBottom(6f).row();
        for (String parameter : effect.getParameterNames()) {
            float[] range = EffectRanges.range(effect.getType(), parameter);
            boolean log = range[2] > 0f;
            ValueBar bar = new ValueBar(skin, parameter, range[0], range[1], range[3], false, log, EffectRanges.format(effect.getType(), parameter),
                () -> effect.getParameter(parameter), value -> effect.setParameter(parameter, value),
                (before, after) -> doc.recordChange("Set " + effect.getType() + " " + parameter, before, after,
                    value -> effect.setParameter(parameter, value)));
            detail.add(bar).growX().height(22f).padBottom(3f).row();
        }
        return detail;
    }

    void dispose() {
        for (MeterWidget meter : meters) {
            meter.dispose();
        }
        meters.clear();
    }

    /** Parameter ranges of the built-in effects, for the sliders. */
    private static final class EffectRanges {
        private EffectRanges() { }

        /** {min, max, logarithmic (1 or 0), default}. */
        static float[] range(String type, String parameter) {
            return switch (type + "." + parameter) {
                case "delay.time" -> new float[] {0.02f, 1f, 0f, 0.3f};
                case "delay.feedback" -> new float[] {0f, 0.95f, 0f, 0.4f};
                case "filter.mode" -> new float[] {0f, 2f, 0f, 0f};
                case "filter.cutoff" -> new float[] {20f, 20000f, 1f, 4000f};
                case "filter.resonance" -> new float[] {0.5f, 20f, 0f, 0.7f};
                default -> new float[] {0f, 1f, 0f, 0.25f};
            };
        }

        static String format(String type, String parameter) {
            return switch (type + "." + parameter) {
                case "delay.time" -> "%.2f s";
                case "filter.mode" -> "%.0f";
                case "filter.cutoff" -> "%.0f Hz";
                default -> "%.2f";
            };
        }
    }

    /** Two thin bars showing the recent peak of a strip. */
    private static final class MeterWidget extends ShapeWidget {
        private static final float CLIP_LEVEL = 1f;
        private final Mixer.Strip strip;

        MeterWidget(Skin skin, Mixer.Strip strip) {
            super(skin);
            this.strip = strip;
        }

        @Override
        protected void drawFilled(ShapeRenderer s) {
            s.setColor(color("surface-variant"));
            s.rect(0f, 0f, getWidth(), getHeight());
            drawBar(s, 0f, strip.getPeakLeft());
            drawBar(s, METER_WIDTH + 2f, strip.getPeakRight());
        }

        private void drawBar(ShapeRenderer s, float x, float peak) {
            float level = Math.min(peak, 1.2f);
            s.setColor(peak > CLIP_LEVEL ? color("error") : color("success"));
            s.rect(x, 0f, METER_WIDTH, getHeight() * Math.min(level / 1.2f, 1f));
        }
    }
}
