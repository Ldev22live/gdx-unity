package io.github.ldev22live.gdxunity.engine.desktop;

import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent;

/** Test script: counts frames and moves its entity {@link #STEP} units right per frame. */
public class StepCountingScript extends ScriptComponent {
    static final float STEP = 10f;

    int updates;

    @Override
    public void onUpdate(float deltaTime) {
        updates++;
        Vector3 position = getTransform().getLocalPosition();
        getTransform().setLocalPosition(position.x + STEP, position.y, position.z);
    }
}
