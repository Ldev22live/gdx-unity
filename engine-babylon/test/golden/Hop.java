package generated;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent;
import io.github.ldev22live.gdxunity.engine.scene.Transform;

/**
 * Generated from the graph "Hop" by Forgeroom (Visual Scripting > Generate Java).
 * Edit it freely; generating again writes the file anew. Bind it to an entity with an Entity Script Binding
 * component named generated.Hop.
 */
public class Hop extends ScriptComponent {
    private boolean up = false;
    private float n1DeltaTime = 0f;
    private float n2Timer = -1f;

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onUpdate(float deltaTime) {
        super.onUpdate(deltaTime);
        if (n2Timer >= 0f) {
            n2Timer -= deltaTime;
            if (n2Timer <= 0f) {
                n2Timer = -1f;
                runN2Completed();
            }
        }
        n1DeltaTime = deltaTime;
        runN1();
    }

    private void runN1() {
        if (n2Timer < 0f) n2Timer = 0.8f;
    }

    private void runN2Completed() {
        up = (!up);
        if (up) {
            setWorldPosition(getEntity(), getEntity().getTransform().getWorldPosition().add(new Vector3(0.0f, 120.0f, 0.0f)));
        } else {
            setWorldPosition(getEntity(), getEntity().getTransform().getWorldPosition().add(new Vector3(0.0f, -120.0f, 0.0f)));
        }
    }

    private static void setWorldPosition(Entity target, Vector3 world) {
        Transform parent = target.getTransform().getParent();
        if (parent == null) {
            target.getTransform().setLocalPosition(world);
            return;
        }
        target.getTransform().setLocalPosition(new Vector3(world).mul(parent.getWorldMatrix().inv()));
    }
}

// The block below lets Forgeroom (desktop or web) turn this script back into a graph. Keep it to be able to
// import the script into the other editor; changes made to the code above are not read back.
// forgeroom-graph:begin v1
// ewogICJzY2hlbWFWZXJzaW9uIjogMSwKICAia2luZCI6ICJncmFwaCIsCiAgIm5hbWUiOiAiSG9wIiwKICAidmFyaWFibGVzIjog
// WwogICAgewogICAgICAibmFtZSI6ICJ1cCIsCiAgICAgICJ0eXBlIjogIkJPT0xFQU4iLAogICAgICAidmFsdWUiOiBmYWxzZQog
// ICAgfQogIF0sCiAgIm5vZGVzIjogWwogICAgewogICAgICAiaWQiOiAibjEiLAogICAgICAidHlwZSI6ICJldmVudC50aWNrIiwK
// ICAgICAgIngiOiAwLAogICAgICAieSI6IDAsCiAgICAgICJsaXRlcmFscyI6IFtdLAogICAgICAicHJvcGVydGllcyI6IFtdCiAg
// ICB9LAogICAgewogICAgICAiaWQiOiAibjIiLAogICAgICAidHlwZSI6ICJmbG93LmRlbGF5IiwKICAgICAgIngiOiAyNDAsCiAg
// ICAgICJ5IjogMCwKICAgICAgImxpdGVyYWxzIjogWwogICAgICAgIHsKICAgICAgICAgICJwaW4iOiAic2Vjb25kcyIsCiAgICAg
// ICAgICAidHlwZSI6ICJGTE9BVCIsCiAgICAgICAgICAidmFsdWUiOiAwLjgKICAgICAgICB9CiAgICAgIF0sCiAgICAgICJwcm9w
// ZXJ0aWVzIjogW10KICAgIH0sCiAgICB7CiAgICAgICJpZCI6ICJuMyIsCiAgICAgICJ0eXBlIjogInZhcmlhYmxlLnNldCIsCiAg
// ICAgICJ4IjogNTIwLAogICAgICAieSI6IDAsCiAgICAgICJsaXRlcmFscyI6IFtdLAogICAgICAicHJvcGVydGllcyI6IFsKICAg
// ICAgICB7CiAgICAgICAgICAibmFtZSI6ICJ2YXJpYWJsZSIsCiAgICAgICAgICAidHlwZSI6ICJTVFJJTkciLAogICAgICAgICAg
// InZhbHVlIjogInVwIgogICAgICAgIH0KICAgICAgXQogICAgfSwKICAgIHsKICAgICAgImlkIjogIm40IiwKICAgICAgInR5cGUi
// OiAibG9naWMubm90IiwKICAgICAgIngiOiAzMDAsCiAgICAgICJ5IjogMTYwLAogICAgICAibGl0ZXJhbHMiOiBbXSwKICAgICAg
// InByb3BlcnRpZXMiOiBbXQogICAgfSwKICAgIHsKICAgICAgImlkIjogIm41IiwKICAgICAgInR5cGUiOiAidmFyaWFibGUuZ2V0
// IiwKICAgICAgIngiOiA4MCwKICAgICAgInkiOiAxNjAsCiAgICAgICJsaXRlcmFscyI6IFtdLAogICAgICAicHJvcGVydGllcyI6
// IFsKICAgICAgICB7CiAgICAgICAgICAibmFtZSI6ICJ2YXJpYWJsZSIsCiAgICAgICAgICAidHlwZSI6ICJTVFJJTkciLAogICAg
// ICAgICAgInZhbHVlIjogInVwIgogICAgICAgIH0KICAgICAgXQogICAgfSwKICAgIHsKICAgICAgImlkIjogIm42IiwKICAgICAg
// InR5cGUiOiAiZmxvdy5icmFuY2giLAogICAgICAieCI6IDgwMCwKICAgICAgInkiOiAwLAogICAgICAibGl0ZXJhbHMiOiBbXSwK
// ICAgICAgInByb3BlcnRpZXMiOiBbXQogICAgfSwKICAgIHsKICAgICAgImlkIjogIm43IiwKICAgICAgInR5cGUiOiAidmFyaWFi
// bGUuZ2V0IiwKICAgICAgIngiOiA1NjAsCiAgICAgICJ5IjogMjAwLAogICAgICAibGl0ZXJhbHMiOiBbXSwKICAgICAgInByb3Bl
// cnRpZXMiOiBbCiAgICAgICAgewogICAgICAgICAgIm5hbWUiOiAidmFyaWFibGUiLAogICAgICAgICAgInR5cGUiOiAiU1RSSU5H
// IiwKICAgICAgICAgICJ2YWx1ZSI6ICJ1cCIKICAgICAgICB9CiAgICAgIF0KICAgIH0sCiAgICB7CiAgICAgICJpZCI6ICJuOCIs
// CiAgICAgICJ0eXBlIjogInZlY3Rvci5tYWtlIiwKICAgICAgIngiOiA4MDAsCiAgICAgICJ5IjogMTYwLAogICAgICAibGl0ZXJh
// bHMiOiBbCiAgICAgICAgewogICAgICAgICAgInBpbiI6ICJ5IiwKICAgICAgICAgICJ0eXBlIjogIkZMT0FUIiwKICAgICAgICAg
// ICJ2YWx1ZSI6IDEyMAogICAgICAgIH0KICAgICAgXSwKICAgICAgInByb3BlcnRpZXMiOiBbXQogICAgfSwKICAgIHsKICAgICAg
// ImlkIjogIm45IiwKICAgICAgInR5cGUiOiAidmVjdG9yLm1ha2UiLAogICAgICAieCI6IDgwMCwKICAgICAgInkiOiAyODAsCiAg
// ICAgICJsaXRlcmFscyI6IFsKICAgICAgICB7CiAgICAgICAgICAicGluIjogInkiLAogICAgICAgICAgInR5cGUiOiAiRkxPQVQi
// LAogICAgICAgICAgInZhbHVlIjogLTEyMAogICAgICAgIH0KICAgICAgXSwKICAgICAgInByb3BlcnRpZXMiOiBbXQogICAgfSwK
// ICAgIHsKICAgICAgImlkIjogIm4xMCIsCiAgICAgICJ0eXBlIjogImFjdGlvbi5tb3ZlQnkiLAogICAgICAieCI6IDEwODAsCiAg
// ICAgICJ5IjogMCwKICAgICAgImxpdGVyYWxzIjogW10sCiAgICAgICJwcm9wZXJ0aWVzIjogW10KICAgIH0sCiAgICB7CiAgICAg
// ICJpZCI6ICJuMTEiLAogICAgICAidHlwZSI6ICJhY3Rpb24ubW92ZUJ5IiwKICAgICAgIngiOiAxMDgwLAogICAgICAieSI6IDE0
// MCwKICAgICAgImxpdGVyYWxzIjogW10sCiAgICAgICJwcm9wZXJ0aWVzIjogW10KICAgIH0KICBdLAogICJsaW5rcyI6IFsKICAg
// IHsKICAgICAgImZyb20iOiAibjEiLAogICAgICAiZnJvbVBpbiI6ICJ0aGVuIiwKICAgICAgInRvIjogIm4yIiwKICAgICAgInRv
// UGluIjogImV4ZWMiCiAgICB9LAogICAgewogICAgICAiZnJvbSI6ICJuMiIsCiAgICAgICJmcm9tUGluIjogImNvbXBsZXRlZCIs
// CiAgICAgICJ0byI6ICJuMyIsCiAgICAgICJ0b1BpbiI6ICJleGVjIgogICAgfSwKICAgIHsKICAgICAgImZyb20iOiAibjMiLAog
// ICAgICAiZnJvbVBpbiI6ICJ0aGVuIiwKICAgICAgInRvIjogIm42IiwKICAgICAgInRvUGluIjogImV4ZWMiCiAgICB9LAogICAg
// ewogICAgICAiZnJvbSI6ICJuNSIsCiAgICAgICJmcm9tUGluIjogInZhbHVlIiwKICAgICAgInRvIjogIm40IiwKICAgICAgInRv
// UGluIjogInZhbHVlIgogICAgfSwKICAgIHsKICAgICAgImZyb20iOiAibjQiLAogICAgICAiZnJvbVBpbiI6ICJyZXN1bHQiLAog
// ICAgICAidG8iOiAibjMiLAogICAgICAidG9QaW4iOiAidmFsdWUiCiAgICB9LAogICAgewogICAgICAiZnJvbSI6ICJuNyIsCiAg
// ICAgICJmcm9tUGluIjogInZhbHVlIiwKICAgICAgInRvIjogIm42IiwKICAgICAgInRvUGluIjogImNvbmRpdGlvbiIKICAgIH0s
// CiAgICB7CiAgICAgICJmcm9tIjogIm44IiwKICAgICAgImZyb21QaW4iOiAidmVjdG9yIiwKICAgICAgInRvIjogIm4xMCIsCiAg
// ICAgICJ0b1BpbiI6ICJvZmZzZXQiCiAgICB9LAogICAgewogICAgICAiZnJvbSI6ICJuOSIsCiAgICAgICJmcm9tUGluIjogInZl
// Y3RvciIsCiAgICAgICJ0byI6ICJuMTEiLAogICAgICAidG9QaW4iOiAib2Zmc2V0IgogICAgfSwKICAgIHsKICAgICAgImZyb20i
// OiAibjYiLAogICAgICAiZnJvbVBpbiI6ICJ0cnVlIiwKICAgICAgInRvIjogIm4xMCIsCiAgICAgICJ0b1BpbiI6ICJleGVjIgog
// ICAgfSwKICAgIHsKICAgICAgImZyb20iOiAibjYiLAogICAgICAiZnJvbVBpbiI6ICJmYWxzZSIsCiAgICAgICJ0byI6ICJuMTEi
// LAogICAgICAidG9QaW4iOiAiZXhlYyIKICAgIH0KICBdCn0=
// forgeroom-graph:end
