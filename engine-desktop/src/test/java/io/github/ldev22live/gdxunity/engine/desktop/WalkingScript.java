package io.github.ldev22live.gdxunity.engine.desktop;

import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent;

/** Test script: walks its character along +x at 2 m/s every frame and jumps once it stands on the ground. */
public class WalkingScript extends ScriptComponent {
    int jumps;

    @Override
    public void onUpdate(float deltaTime) {
        moveCharacter3D(new Vector3(200f, 0f, 0f));
        if (isGrounded3D() && jumps == 0 && jumpCharacter3D(300f)) {
            jumps++;
        }
    }
}
