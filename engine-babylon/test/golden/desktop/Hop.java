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
// ewoic2NoZW1hVmVyc2lvbiI6IDEsCiJraW5kIjogImdyYXBoIiwKIm5hbWUiOiAiSG9wIiwKInZhcmlhYmxlcyI6IFsKCXsgIm5h
// bWUiOiAidXAiLCAidHlwZSI6ICJCT09MRUFOIiwgInZhbHVlIjogZmFsc2UgfQpdLAoibm9kZXMiOiBbCgl7CgkJImlkIjogIm4x
// IiwKCQkidHlwZSI6ICJldmVudC50aWNrIiwKCQkieCI6IDAuMCwKCQkieSI6IDAuMCwKCQkibGl0ZXJhbHMiOiBbXSwKCQkicHJv
// cGVydGllcyI6IFtdCgl9LAoJewoJCSJpZCI6ICJuMiIsCgkJInR5cGUiOiAiZmxvdy5kZWxheSIsCgkJIngiOiAyNDAuMCwKCQki
// eSI6IDAuMCwKCQkibGl0ZXJhbHMiOiBbCgkJCXsgInBpbiI6ICJzZWNvbmRzIiwgInR5cGUiOiAiRkxPQVQiLCAidmFsdWUiOiAw
// LjgwMDAwMDAxMTkyMDkyOSB9CgkJXSwKCQkicHJvcGVydGllcyI6IFtdCgl9LAoJewoJCSJpZCI6ICJuMyIsCgkJInR5cGUiOiAi
// dmFyaWFibGUuc2V0IiwKCQkieCI6IDUyMC4wLAoJCSJ5IjogMC4wLAoJCSJsaXRlcmFscyI6IFtdLAoJCSJwcm9wZXJ0aWVzIjog
// WwoJCQl7ICJuYW1lIjogInZhcmlhYmxlIiwgInR5cGUiOiAiU1RSSU5HIiwgInZhbHVlIjogInVwIiB9CgkJXQoJfSwKCXsKCQki
// aWQiOiAibjQiLAoJCSJ0eXBlIjogImxvZ2ljLm5vdCIsCgkJIngiOiAzMDAuMCwKCQkieSI6IDE2MC4wLAoJCSJsaXRlcmFscyI6
// IFtdLAoJCSJwcm9wZXJ0aWVzIjogW10KCX0sCgl7CgkJImlkIjogIm41IiwKCQkidHlwZSI6ICJ2YXJpYWJsZS5nZXQiLAoJCSJ4
// IjogODAuMCwKCQkieSI6IDE2MC4wLAoJCSJsaXRlcmFscyI6IFtdLAoJCSJwcm9wZXJ0aWVzIjogWwoJCQl7ICJuYW1lIjogInZh
// cmlhYmxlIiwgInR5cGUiOiAiU1RSSU5HIiwgInZhbHVlIjogInVwIiB9CgkJXQoJfSwKCXsKCQkiaWQiOiAibjYiLAoJCSJ0eXBl
// IjogImZsb3cuYnJhbmNoIiwKCQkieCI6IDgwMC4wLAoJCSJ5IjogMC4wLAoJCSJsaXRlcmFscyI6IFtdLAoJCSJwcm9wZXJ0aWVz
// IjogW10KCX0sCgl7CgkJImlkIjogIm43IiwKCQkidHlwZSI6ICJ2YXJpYWJsZS5nZXQiLAoJCSJ4IjogNTYwLjAsCgkJInkiOiAy
// MDAuMCwKCQkibGl0ZXJhbHMiOiBbXSwKCQkicHJvcGVydGllcyI6IFsKCQkJeyAibmFtZSI6ICJ2YXJpYWJsZSIsICJ0eXBlIjog
// IlNUUklORyIsICJ2YWx1ZSI6ICJ1cCIgfQoJCV0KCX0sCgl7CgkJImlkIjogIm44IiwKCQkidHlwZSI6ICJ2ZWN0b3IubWFrZSIs
// CgkJIngiOiA4MDAuMCwKCQkieSI6IDE2MC4wLAoJCSJsaXRlcmFscyI6IFsKCQkJeyAicGluIjogInkiLCAidHlwZSI6ICJGTE9B
// VCIsICJ2YWx1ZSI6IDEyMC4wIH0KCQldLAoJCSJwcm9wZXJ0aWVzIjogW10KCX0sCgl7CgkJImlkIjogIm45IiwKCQkidHlwZSI6
// ICJ2ZWN0b3IubWFrZSIsCgkJIngiOiA4MDAuMCwKCQkieSI6IDI4MC4wLAoJCSJsaXRlcmFscyI6IFsKCQkJeyAicGluIjogInki
// LCAidHlwZSI6ICJGTE9BVCIsICJ2YWx1ZSI6IC0xMjAuMCB9CgkJXSwKCQkicHJvcGVydGllcyI6IFtdCgl9LAoJewoJCSJpZCI6
// ICJuMTAiLAoJCSJ0eXBlIjogImFjdGlvbi5tb3ZlQnkiLAoJCSJ4IjogMTA4MC4wLAoJCSJ5IjogMC4wLAoJCSJsaXRlcmFscyI6
// IFtdLAoJCSJwcm9wZXJ0aWVzIjogW10KCX0sCgl7CgkJImlkIjogIm4xMSIsCgkJInR5cGUiOiAiYWN0aW9uLm1vdmVCeSIsCgkJ
// IngiOiAxMDgwLjAsCgkJInkiOiAxNDAuMCwKCQkibGl0ZXJhbHMiOiBbXSwKCQkicHJvcGVydGllcyI6IFtdCgl9Cl0sCiJsaW5r
// cyI6IFsKCXsgImZyb20iOiAibjEiLCAiZnJvbVBpbiI6ICJ0aGVuIiwgInRvIjogIm4yIiwgInRvUGluIjogImV4ZWMiIH0sCgl7
// ICJmcm9tIjogIm4yIiwgImZyb21QaW4iOiAiY29tcGxldGVkIiwgInRvIjogIm4zIiwgInRvUGluIjogImV4ZWMiIH0sCgl7ICJm
// cm9tIjogIm4zIiwgImZyb21QaW4iOiAidGhlbiIsICJ0byI6ICJuNiIsICJ0b1BpbiI6ICJleGVjIiB9LAoJeyAiZnJvbSI6ICJu
// NSIsICJmcm9tUGluIjogInZhbHVlIiwgInRvIjogIm40IiwgInRvUGluIjogInZhbHVlIiB9LAoJeyAiZnJvbSI6ICJuNCIsICJm
// cm9tUGluIjogInJlc3VsdCIsICJ0byI6ICJuMyIsICJ0b1BpbiI6ICJ2YWx1ZSIgfSwKCXsgImZyb20iOiAibjciLCAiZnJvbVBp
// biI6ICJ2YWx1ZSIsICJ0byI6ICJuNiIsICJ0b1BpbiI6ICJjb25kaXRpb24iIH0sCgl7ICJmcm9tIjogIm44IiwgImZyb21QaW4i
// OiAidmVjdG9yIiwgInRvIjogIm4xMCIsICJ0b1BpbiI6ICJvZmZzZXQiIH0sCgl7ICJmcm9tIjogIm45IiwgImZyb21QaW4iOiAi
// dmVjdG9yIiwgInRvIjogIm4xMSIsICJ0b1BpbiI6ICJvZmZzZXQiIH0sCgl7ICJmcm9tIjogIm42IiwgImZyb21QaW4iOiAidHJ1
// ZSIsICJ0byI6ICJuMTAiLCAidG9QaW4iOiAiZXhlYyIgfSwKCXsgImZyb20iOiAibjYiLCAiZnJvbVBpbiI6ICJmYWxzZSIsICJ0
// byI6ICJuMTEiLCAidG9QaW4iOiAiZXhlYyIgfQpdCn0=
// forgeroom-graph:end
