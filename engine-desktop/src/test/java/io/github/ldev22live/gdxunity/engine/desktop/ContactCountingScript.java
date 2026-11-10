package io.github.ldev22live.gdxunity.engine.desktop;

import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent;

import java.util.ArrayList;
import java.util.List;

/** Test script: writes down the 3D contact callbacks it receives, as "kind:other". */
public class ContactCountingScript extends ScriptComponent {
    final List<String> contacts = new ArrayList<>();

    @Override
    public void onCollisionEnter3D(Entity other) {
        contacts.add("collisionEnter:" + other.getName());
    }

    @Override
    public void onCollisionExit3D(Entity other) {
        contacts.add("collisionExit:" + other.getName());
    }
}
