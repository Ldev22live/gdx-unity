package samples.ruins;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.render.components.TextComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent;
import samples.common.SideBody;

import java.util.ArrayList;
import java.util.List;

/**
 * Rules of the 2.5D ruins platformer. Scene conventions:
 * <ul>
 *   <li>"Platform..." boxes are solid; "Mover X..." and "Mover Y..." are solid and slide sideways or up and down</li>
 *   <li>"Orb..." are collected on touch; "Spikes..." send the player back to the last "Checkpoint..." touched</li>
 *   <li>"Goal" ends the level; "Player" is moved by this script</li>
 * </ul>
 */
public class RuinsGame extends ScriptComponent {
    static final float RUN_SPEED = 300f;
    static final float JUMP_SPEED = 820f;
    static final float GRAVITY = 2000f;
    /** Below this height the player has fallen into a pit. */
    static final float FALL_LIMIT = -500f;
    /** Mover travel from its start position, and seconds per back-and-forth cycle. */
    static final float MOVER_RANGE = 120f;
    static final float MOVER_PERIOD = 4f;
    /** Orb spin in degrees per second. */
    static final float ORB_SPIN = 180f;

    private final List<Entity> movers = new ArrayList<>();
    private final List<Vector3> moverHomes = new ArrayList<>();
    private final List<Entity> orbs = new ArrayList<>();
    private final List<SideBody.Solid> hazards = new ArrayList<>();
    private final List<SideBody.Solid> checkpoints = new ArrayList<>();
    private List<SideBody.Solid> solids;
    private SideBody.Solid goal;
    private Entity player;
    private SideBody body;
    private TextComponent collectedText;
    private TextComponent deathsText;
    private TextComponent messageText;
    private float startX;
    private float startY;
    private float respawnX;
    private float respawnY;
    private float time;
    private int collected;
    private int deaths;
    private boolean finished;

    @Override
    public void onStart() {
        super.onStart();
        for (Entity entity : getScene().getAllEntities()) {
            String name = entity.getName();
            if (name.startsWith("Mover")) {
                movers.add(entity);
                moverHomes.add(entity.getTransform().getLocalPosition());
            } else if (name.startsWith("Orb")) {
                orbs.add(entity);
            } else if (name.startsWith("Spikes")) {
                hazards.add(new SideBody.Solid(entity));
            } else if (name.startsWith("Checkpoint")) {
                checkpoints.add(new SideBody.Solid(entity));
            }
        }
        solids = SideBody.solidsNamed(getScene(), "Platform", "Mover");
        goal = new SideBody.Solid(getScene().findEntityByName("Goal"));
        player = getScene().findEntityByName("Player");
        SideBody.Solid playerBox = new SideBody.Solid(player);
        body = new SideBody(playerBox.x, playerBox.y, playerBox.halfWidth, playerBox.halfHeight);
        startX = playerBox.x;
        startY = playerBox.y;
        collectedText = getScene().findEntityByName("Collected Text").getComponent(TextComponent.class);
        deathsText = getScene().findEntityByName("Deaths Text").getComponent(TextComponent.class);
        messageText = getScene().findEntityByName("Message Text").getComponent(TextComponent.class);
        restart();
    }

    @Override
    public void onUpdate(float deltaTime) {
        if (Gdx.input.isKeyJustPressed(Keys.R)) {
            restart();
            return;
        }
        time += deltaTime;
        moveMovers();
        spinOrbs(deltaTime);
        if (finished) {
            return;
        }

        float direction = 0f;
        if (Gdx.input.isKeyPressed(Keys.A) || Gdx.input.isKeyPressed(Keys.LEFT)) direction -= 1f;
        if (Gdx.input.isKeyPressed(Keys.D) || Gdx.input.isKeyPressed(Keys.RIGHT)) direction += 1f;
        body.velocityX = direction * RUN_SPEED;
        if (body.grounded && (Gdx.input.isKeyJustPressed(Keys.SPACE) || Gdx.input.isKeyJustPressed(Keys.W)
            || Gdx.input.isKeyJustPressed(Keys.UP))) {
            body.velocityY = JUMP_SPEED;
        }
        body.step(deltaTime, GRAVITY, solids, null);
        body.moveEntity(player, 0f);

        for (Entity orb : orbs) {
            Vector3 position = orb.getTransform().getWorldPosition();
            if (orb.isActiveSelf() && body.touches(position.x, position.y, 15f, 15f)) {
                orb.setActive(false);
                collected++;
                updateTexts();
            }
        }
        for (SideBody.Solid checkpoint : checkpoints) {
            if (body.touches(checkpoint.x, checkpoint.y, checkpoint.halfWidth, checkpoint.halfHeight)) {
                respawnX = checkpoint.x;
                respawnY = checkpoint.y + 20f;
            }
        }
        for (SideBody.Solid hazard : hazards) {
            if (body.touches(hazard.x, hazard.y, hazard.halfWidth, hazard.halfHeight)) {
                die();
            }
        }
        if (body.y < FALL_LIMIT) {
            die();
        }
        if (body.touches(goal.x, goal.y, goal.halfWidth, goal.halfHeight)) {
            finished = true;
            messageText.setText("You made it out! " + collected + "/" + orbs.size() + " orbs, " + deaths
                + " deaths. Press R to play again");
        }
    }

    /** Slides each mover along a sine wave; "Mover Y" ones move vertically. */
    private void moveMovers() {
        for (int i = 0; i < movers.size(); i++) {
            Entity mover = movers.get(i);
            Vector3 home = moverHomes.get(i);
            float offset = MOVER_RANGE * MathUtils.sin(time * MathUtils.PI2 / MOVER_PERIOD);
            if (mover.getName().startsWith("Mover Y")) {
                mover.getTransform().setLocalPosition(home.x, home.y + offset, home.z);
            } else {
                mover.getTransform().setLocalPosition(home.x + offset, home.y, home.z);
            }
        }
        for (SideBody.Solid solid : solids) {
            solid.refresh();
        }
    }

    private void spinOrbs(float deltaTime) {
        for (Entity orb : orbs) {
            orb.getTransform().rotate(ORB_SPIN * deltaTime, 0f, 0f);
        }
    }

    private void die() {
        deaths++;
        body.x = respawnX;
        body.y = respawnY;
        body.velocityX = 0f;
        body.velocityY = 0f;
        updateTexts();
    }

    private void restart() {
        body.x = startX;
        body.y = startY;
        body.velocityX = 0f;
        body.velocityY = 0f;
        respawnX = startX;
        respawnY = startY;
        collected = 0;
        deaths = 0;
        finished = false;
        for (Entity orb : orbs) {
            orb.setActive(true);
        }
        body.moveEntity(player, 0f);
        messageText.setText("");
        updateTexts();
    }

    private void updateTexts() {
        collectedText.setText("Orbs: " + collected + " / " + orbs.size());
        deathsText.setText("Deaths: " + deaths);
    }
}
