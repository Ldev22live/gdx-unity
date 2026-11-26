package io.github.ldev22live.gdxunity.editor.desktop.ui.viewport;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import io.github.ldev22live.gdxunity.editor.desktop.ui.EditorSkinFactory;
import io.github.ldev22live.gdxunity.editor.sculpt.MeshPartEditor;
import io.github.ldev22live.gdxunity.editor.sculpt.MeshPartEditor.Edge;
import io.github.ldev22live.gdxunity.editor.sculpt.MeshPartEditor.Face;
import io.github.ldev22live.gdxunity.editor.sculpt.MeshPartEditor.Geometry;
import io.github.ldev22live.gdxunity.editor.sculpt.MeshPartEditor.Kind;
import io.github.ldev22live.gdxunity.editor.sculpt.MeshPartEditor.Tool;
import io.github.ldev22live.gdxunity.editor.sculpt.MeshPartEditor.Vertex;

import java.util.List;

/** A detached primitive-local wireframe with element picking and command controls. */
final class MeshEditPanel extends Table {
    private final List<MeshPartEditor> primitives;
    private final SelectBox<Integer> primitive;
    private final SelectBox<Kind> kind;
    private final SelectBox<Tool> tool;
    private final TextField index, x, y, z;
    private final Label status;
    private final TextureRegionDrawable pixel;

    MeshEditPanel(Skin skin, List<MeshPartEditor> primitives) {
        super(skin);
        this.primitives = primitives;
        pixel = (TextureRegionDrawable)skin.getDrawable(EditorSkinFactory.WHITE_PIXEL);
        primitive = new SelectBox<>(skin);
        Integer[] ids = new Integer[primitives.size()];
        for (int i = 0; i < ids.length; i++) ids[i] = i;
        primitive.setItems(ids);
        kind = new SelectBox<>(skin);
        kind.setItems(Kind.values());
        kind.setSelected(Kind.Face);
        tool = new SelectBox<>(skin);
        tool.setItems(Tool.values());
        index = new TextField("0", skin);
        x = new TextField("0.1", skin);
        y = new TextField("0", skin);
        z = new TextField("0", skin);
        status = new Label("Pick an element in the preview or enter its index.", skin);
        add("Primitive").left();
        add(primitive);
        add(kind);
        add(index).width(64).row();
        add(new Preview()).colspan(4).growX().height(100).row();
        add(tool);
        add(x).width(56);
        add(y).width(56);
        add(z).width(56).row();
        add(status).colspan(4).width(410).left().row();
        status.setWrap(true);
        add(button("Apply", skin, this::apply));
        add(button("Undo", skin, () -> status.setText(editor().undo() ? "Undone" : "Nothing to undo")));
        add(button("Redo", skin, () -> status.setText(editor().redo() ? "Redone" : "Nothing to redo")));
        tool.addListener(new ChangeListener() {
            public void changed(ChangeEvent event, Actor actor) {
                boolean translate = tool.getSelected() == Tool.Translate;
                if (!translate) kind.setSelected(Kind.Face);
                x.setDisabled(tool.getSelected() == Tool.Delete);
                y.setDisabled(!translate);
                z.setDisabled(!translate);
                status.setText(switch (tool.getSelected()) {
                    case Translate -> "Enter X, Y and Z offsets in source units.";
                    case Extrude -> "Enter the signed distance along the face normal.";
                    case Bevel, Inset -> "Enter a fraction between 0 and 1.";
                    case Delete -> "Remove the selected triangle.";
                    case Material -> "Enter a material slot index, or -1 for default.";
                });
            }
        });
    }

    private MeshPartEditor editor() { return primitives.get(primitive.getSelected()); }

    private void apply() {
        try {
            Tool operation = tool.getSelected();
            float first = operation == Tool.Delete ? 0 : Float.parseFloat(x.getText());
            boolean move = operation == Tool.Translate;
            boolean changed = editor().apply(operation, kind.getSelected(), Integer.parseInt(index.getText()), first,
                move ? Float.parseFloat(y.getText()) : 0, move ? Float.parseFloat(z.getText()) : 0);
            if (operation == Tool.Material) {
                status.setText("Face " + index.getText() + " ? material slot " + (int)first);
            } else {
                status.setText(changed ? operation + " applied" : "No change");
            }
        } catch (IllegalArgumentException e) {
            status.setText(e.getMessage());
        }
    }

    private static TextButton button(String label, Skin skin, Runnable action) {
        TextButton button = new TextButton(label, skin);
        button.addListener(new ChangeListener() {
            public void changed(ChangeEvent event, Actor actor) { action.run(); }
        });
        return button;
    }

    private final class Preview extends Actor {
        private Geometry cached;
        private float[] px, py;
        private int cachedIndex;
        private String indexText;
        private final Label materialSlot = new Label("", getSkin());
        private Geometry shownGeometry;
        private int shownFace = Integer.MIN_VALUE;
        private void showMaterialSlot() {
            int face = kind.getSelected() == Kind.Face ? cachedIndex : -1;
            if (shownGeometry == cached && shownFace == face) return;
            shownGeometry = cached;
            shownFace = face;
            if (face < 0 || face >= cached.faces().size()) {
                materialSlot.setText("");
            } else {
                int slot = cached.faces().get(face).material();
                materialSlot.setText(slot < 0 ? "Default material" : "Material slot " + slot);
            }
            materialSlot.pack();
        }
        Preview() {
            addListener(new ClickListener() {
                public void clicked(InputEvent event, float x, float y) {
                    project();
                    Geometry g = cached;
                    float nearest = Float.POSITIVE_INFINITY;
                    int picked = -1;
                    int count = switch (kind.getSelected()) {
                        case Vertex -> px.length;
                        case Edge -> g.edges().size();
                        case Face -> g.faces().size();
                    };
                    for (int i = 0; i < count; i++) {
                        float cx, cy;
                        switch (kind.getSelected()) {
                            case Vertex -> { cx = px[i]; cy = py[i]; }
                            case Edge -> {
                                Edge e = g.edges().get(i);
                                cx = (px[e.a()] + px[e.b()]) / 2;
                                cy = (py[e.a()] + py[e.b()]) / 2;
                            }
                            default -> {
                                Face f = g.faces().get(i);
                                cx = (px[f.a()] + px[f.b()] + px[f.c()]) / 3;
                                cy = (py[f.a()] + py[f.b()] + py[f.c()]) / 3;
                            }
                        }
                        float distance = (cx-x)*(cx-x) + (cy-y)*(cy-y);
                        if (distance < nearest) { nearest = distance; picked = i; }
                    }
                    if (picked >= 0) index.setText(Integer.toString(picked));
                }
            });
        }

        protected void sizeChanged() { cached = null; }

        private void project() {
            Geometry g = editor().geometry();
            if (cached == g) return;
            cached = g;
            px = new float[g.vertices().size()];
            py = new float[px.length];
            float left = Float.POSITIVE_INFINITY, right = -left, bottom = left, top = right;
            for (int i = 0; i < px.length; i++) {
                Vertex v = g.vertices().get(i);
                px[i] = (v.x() - v.z()) * 0.707107f;
                py[i] = v.y() * 0.816497f - (v.x() + v.z()) * 0.408248f;
                left = Math.min(left, px[i]); right = Math.max(right, px[i]);
                bottom = Math.min(bottom, py[i]); top = Math.max(top, py[i]);
            }
            float scale = Math.min((getWidth()-8)/Math.max(0.000001f,right-left),
                (getHeight()-8)/Math.max(0.000001f,top-bottom));
            for (int i = 0; i < px.length; i++) {
                px[i] = (px[i] - (left+right)/2)*scale + getWidth()/2;
                py[i] = (py[i] - (bottom+top)/2)*scale + getHeight()/2;
            }
        }

        public void draw(Batch batch, float alpha) {
            project();
            float tint = batch.getPackedColor();
            batch.setColor(.55f,.65f,.75f,alpha);
            for (Edge e : cached.edges()) line(batch,e.a(),e.b());
            if (!index.getText().equals(indexText)) {
                indexText = index.getText();
                try { cachedIndex = Integer.parseInt(indexText); }
                catch (NumberFormatException invalid) { cachedIndex = -1; }
            }
            int i = cachedIndex;
            batch.setColor(1,.75f,.2f,alpha);
            if (i >= 0) switch (kind.getSelected()) {
                case Vertex -> { if (i < px.length) pixel.draw(batch,getX()+px[i]-2,getY()+py[i]-2,4,4); }
                case Edge -> { if (i < cached.edges().size()) {
                    Edge e = cached.edges().get(i); line(batch,e.a(),e.b());
                } }
                case Face -> { if (i < cached.faces().size()) {
                    Face f = cached.faces().get(i);
                    line(batch,f.a(),f.b()); line(batch,f.b(),f.c()); line(batch,f.c(),f.a());
                } }
            }
            batch.setPackedColor(tint);
            showMaterialSlot();
            materialSlot.setPosition(getX(), getY());
            materialSlot.draw(batch, alpha);
            batch.setPackedColor(tint);
        }

        private void line(Batch batch,int a,int b) {
            float dx = px[b]-px[a], dy = py[b]-py[a];
            pixel.draw(batch,getX()+px[a],getY()+py[a],0,0,(float)Math.hypot(dx,dy),1,1,1,MathUtils.atan2Deg(dy,dx));
        }
    }
}
