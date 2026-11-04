package samples.collector;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.render.components.TextComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Rules of the collector: gems (entities whose name starts with "Gem") spin and bob; touching one with the
 * "Player" collects it; collecting them all stops the clock. R starts again.
 */
public class CollectorGame extends ScriptComponent {
    /** Distance between player and gem centres that counts as touching. */
    static final float PICKUP_DISTANCE = 60f;
    /** Gem spin in degrees per second. */
    static final float SPIN_SPEED = 120f;
    /** Gem bob height and speed (radians per second). */
    static final float BOB_HEIGHT = 10f;
    static final float BOB_RATE = 3f;

    private final List<Entity> gems = new ArrayList<>();
    private final List<Vector3> gemHomes = new ArrayList<>();
    private Entity player;
    private TextComponent gemsText;
    private TextComponent timeText;
    private TextComponent messageText;
    private float elapsed;
    private float spin;
    private int collected;
    private boolean finished;

    @Override
    public void onStart() {
        super.onStart();
        player = getScene().findEntityByName("Player");
        for (Entity entity : getScene().getAllEntities()) {
            if (entity.getName().startsWith("Gem")) {
                gems.add(entity);
                gemHomes.add(entity.getTransform().getLocalPosition());
            }
        }
        gemsText = getScene().findEntityByName("Collected Text").getComponent(TextComponent.class);
        timeText = getScene().findEntityByName("Time Text").getComponent(TextComponent.class);
        messageText = getScene().findEntityByName("Message Text").getComponent(TextComponent.class);
        restart();
    }

    @Override
    public void onUpdate(float deltaTime) {
        if (Gdx.input.isKeyJustPressed(Keys.R)) {
            restart();
            return;
        }
        spin += deltaTime;
        Vector3 playerPosition = player.getTransform().getWorldPosition();
        for (int i = 0; i < gems.size(); i++) {
            Entity gem = gems.get(i);
            if (!gem.isActiveSelf()) {
                continue;
            }
            Vector3 home = gemHomes.get(i);
            gem.getTransform().setLocalPosition(home.x, home.y + MathUtils.sin(spin * BOB_RATE + i) * BOB_HEIGHT, home.z);
            gem.getTransform().setLocalEulerAngles(spin * SPIN_SPEED, 0f, 0f);
            if (home.dst(playerPosition.x, home.y, playerPosition.z) < PICKUP_DISTANCE) {
                gem.setActive(false);
                collected++;
                gemsText.setText("Gems: " + collected + " / " + gems.size());
                if (collected == gems.size()) {
                    finished = true;
                    messageText.setText(String.format(Locale.ROOT, "All gems in %.1f s! Press R to play again", elapsed));
                }
            }
        }
        if (!finished) {
            elapsed += deltaTime;
            timeText.setText(String.format(Locale.ROOT, "Time: %.1f", elapsed));
        }
    }

    private void restart() {
        for (Entity gem : gems) {
            gem.setActive(true);
        }
        collected = 0;
        elapsed = 0f;
        finished = false;
        gemsText.setText("Gems: 0 / " + gems.size());
        messageText.setText("");
    }
}
