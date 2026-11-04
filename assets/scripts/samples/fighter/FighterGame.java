package samples.fighter;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.render.components.TextComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent;

import java.util.Random;

/**
 * Rules of the one-on-one fighter: the "Player" (keys) against the "CPU" (computer), best of three rounds of
 * {@link #ROUND_TIME} seconds. A round ends when someone's health reaches zero, or on time, when the healthier
 * fighter takes it. Health bars are boxes named "Health Bar (Player)" and "Health Bar (CPU)" that shrink towards
 * their outer end. R restarts the match.
 *
 * <p>Fighting rules (timing, damage, blocking) are in {@link Fighter}; this script reads the keys, runs the
 * computer opponent, checks hits and keeps score.
 */
public class FighterGame extends ScriptComponent {
    static final int ROUNDS_TO_WIN = 2;
    static final float ROUND_TIME = 60f;
    /** Seconds the round name shows before the fight, "FIGHT!" shows after it, and the result shows after a round. */
    static final float INTRO_TIME = 1.2f;
    static final float FIGHT_MESSAGE_TIME = 0.6f;
    static final float ROUND_OVER_TIME = 2.2f;
    /** The stage floor runs from -STAGE_HALF_WIDTH to +STAGE_HALF_WIDTH. */
    static final float STAGE_HALF_WIDTH = 560f;
    /** A move hits only if the defender's feet are within this height of the attacker's; jumping avoids low moves. */
    static final float HIT_HEIGHT_RANGE = Fighter.HEIGHT * 0.7f;
    /** Full width of a health bar, and the X of its outer end (the left end for the player, right for the CPU). */
    static final float BAR_WIDTH = 420f;
    static final float BAR_OUTER_X = 560f;
    /** The computer decides what to do this often, in seconds; shorter is a harder opponent. */
    static final float CPU_THINK_INTERVAL = 0.15f;
    /** Chance the computer blocks an attack it sees coming. */
    static final float CPU_BLOCK_CHANCE = 0.55f;
    /** Extra distance at which the computer already counts as in range. */
    static final float CPU_RANGE_MARGIN = 10f;
    /** Fixed seed: the computer plays the same way every match, which also keeps tests repeatable. */
    static final long CPU_SEED = 21L;
    /** Out of range, the share of decisions that walk in (the rest wait). */
    static final float CPU_APPROACH_CHANCE = 0.85f;
    /** In punch range, cumulative chances to punch, kick and block; anything above backs off. */
    static final float CPU_CLOSE_PUNCH = 0.45f;
    static final float CPU_CLOSE_KICK = 0.65f;
    static final float CPU_CLOSE_BLOCK = 0.85f;
    /** Between punch and kick range, cumulative chances to kick and to step in; anything above waits. */
    static final float CPU_MID_KICK = 0.4f;
    static final float CPU_MID_APPROACH = 0.8f;
    static final String START_MESSAGE = "Press Space to start";

    private enum State { WAITING, INTRO, FIGHTING, ROUND_OVER, MATCH_OVER }

    /** What the computer is doing until it next decides. */
    private enum Plan { APPROACH, RETREAT, BLOCK, WAIT, PUNCH, KICK }

    private Fighter player;
    private Fighter cpu;
    private Entity playerBar;
    private Entity cpuBar;
    private TextComponent playerHealthText;
    private TextComponent cpuHealthText;
    private TextComponent timerText;
    private TextComponent roundsText;
    private TextComponent messageText;
    private Random random;
    private State state;
    private Plan plan;
    private float thinkTimer;
    private float stateTimer;
    private float roundTimeLeft;
    private float fightMessageLeft;
    private int round;
    private int playerRounds;
    private int cpuRounds;

    @Override
    public void onStart() {
        super.onStart();
        player = new Fighter(getScene(), "Player");
        cpu = new Fighter(getScene(), "CPU");
        playerBar = required("Health Bar (Player)");
        cpuBar = required("Health Bar (CPU)");
        playerHealthText = text("Player Health Text");
        cpuHealthText = text("CPU Health Text");
        timerText = text("Timer Text");
        roundsText = text("Rounds Text");
        messageText = text("Message Text");
        restart();
    }

    private Entity required(String name) {
        Entity entity = getScene().findEntityByName(name);
        if (entity == null) {
            throw new IllegalStateException("FighterGame needs an entity named '" + name + "'");
        }
        return entity;
    }

    private TextComponent text(String name) {
        TextComponent text = required(name).getComponent(TextComponent.class);
        if (text == null) {
            throw new IllegalStateException("'" + name + "' needs a Text component");
        }
        return text;
    }

    @Override
    public void onUpdate(float deltaTime) {
        if (Gdx.input.isKeyJustPressed(Keys.R)) {
            restart();
            return;
        }
        switch (state) {
            case WAITING -> {
                if (Gdx.input.isKeyJustPressed(Keys.SPACE)) {
                    startRound();
                }
            }
            case INTRO -> {
                stateTimer -= deltaTime;
                if (stateTimer <= 0f) {
                    state = State.FIGHTING;
                    messageText.setText("FIGHT!");
                    fightMessageLeft = FIGHT_MESSAGE_TIME;
                }
            }
            case FIGHTING -> fight(deltaTime);
            case ROUND_OVER -> {
                stateTimer -= deltaTime;
                if (stateTimer <= 0f) {
                    if (playerRounds == ROUNDS_TO_WIN || cpuRounds == ROUNDS_TO_WIN) {
                        state = State.MATCH_OVER;
                        messageText.setText((playerRounds > cpuRounds ? "You win the match!" : "The CPU wins the match!")
                            + "  Press R to play again");
                    } else {
                        startRound();
                    }
                }
            }
            case MATCH_OVER -> {
            }
        }
        // Knockback, falling and limbs keep animating between rounds too
        player.update(deltaTime);
        cpu.update(deltaTime);
        keepApartAndOnStage();
        updateHud();
    }

    private void fight(float deltaTime) {
        if (fightMessageLeft > 0f) {
            fightMessageLeft -= deltaTime;
            if (fightMessageLeft <= 0f) {
                messageText.setText("");
            }
        }
        roundTimeLeft = Math.max(0f, roundTimeLeft - deltaTime);
        face();
        controlPlayer(deltaTime);
        controlCpu(deltaTime);
        checkHit(player, cpu);
        checkHit(cpu, player);
        if (player.health == 0 || cpu.health == 0) {
            endRound("K.O.!");
        } else if (roundTimeLeft == 0f) {
            endRound("Time!");
        }
    }

    /** Fighters turn to face each other unless they are in the middle of something. */
    private void face() {
        if (!player.busy()) {
            player.facing = Math.signum(cpu.x - player.x) >= 0f ? 1f : -1f;
        }
        if (!cpu.busy()) {
            cpu.facing = Math.signum(player.x - cpu.x) >= 0f ? 1f : -1f;
        }
    }

    private void controlPlayer(float deltaTime) {
        float walk = 0f;
        if (Gdx.input.isKeyPressed(Keys.A) || Gdx.input.isKeyPressed(Keys.LEFT)) walk -= 1f;
        if (Gdx.input.isKeyPressed(Keys.D) || Gdx.input.isKeyPressed(Keys.RIGHT)) walk += 1f;
        boolean jump = Gdx.input.isKeyJustPressed(Keys.W) || Gdx.input.isKeyJustPressed(Keys.UP);
        boolean block = Gdx.input.isKeyPressed(Keys.S) || Gdx.input.isKeyPressed(Keys.DOWN);
        Fighter.Move attack = Gdx.input.isKeyJustPressed(Keys.J) ? Fighter.PUNCH
            : Gdx.input.isKeyJustPressed(Keys.K) ? Fighter.KICK : null;
        player.control(walk, jump, block, attack, deltaTime);
    }

    /**
     * The computer opponent: every {@link #CPU_THINK_INTERVAL} it looks at the distance and at what the player is
     * doing, and picks a plan: walk in, back off, block, wait, punch or kick.
     */
    private void controlCpu(float deltaTime) {
        thinkTimer -= deltaTime;
        if (thinkTimer <= 0f) {
            thinkTimer = CPU_THINK_INTERVAL;
            plan = think();
        }
        float towardsPlayer = cpu.facing;
        Fighter.Move attack = null;
        float walk = 0f;
        boolean block = false;
        switch (plan) {
            case APPROACH -> walk = towardsPlayer;
            case RETREAT -> walk = -towardsPlayer;
            case BLOCK -> block = true;
            case PUNCH -> attack = Fighter.PUNCH;
            case KICK -> attack = Fighter.KICK;
            case WAIT -> {
            }
        }
        if (attack != null) {
            // An attack is started once, not every frame of the plan
            plan = Plan.WAIT;
        }
        cpu.control(walk, false, block, attack, deltaTime);
    }

    private Plan think() {
        float distance = Math.abs(player.x - cpu.x);
        boolean threatened = player.windingUp() && distance <= Fighter.WIDTH + player.reach() + CPU_RANGE_MARGIN;
        if (threatened) {
            return random.nextFloat() < CPU_BLOCK_CHANCE ? Plan.BLOCK : Plan.RETREAT;
        }
        if (distance > Fighter.WIDTH + Fighter.KICK.reach()) {
            return random.nextFloat() < CPU_APPROACH_CHANCE ? Plan.APPROACH : Plan.WAIT;
        }
        float roll = random.nextFloat();
        if (distance <= Fighter.WIDTH + Fighter.PUNCH.reach() + CPU_RANGE_MARGIN) {
            return roll < CPU_CLOSE_PUNCH ? Plan.PUNCH : roll < CPU_CLOSE_KICK ? Plan.KICK
                : roll < CPU_CLOSE_BLOCK ? Plan.BLOCK : Plan.RETREAT;
        }
        // Between punch and kick range: kick, or step in
        return roll < CPU_MID_KICK ? Plan.KICK : roll < CPU_MID_APPROACH ? Plan.APPROACH : Plan.WAIT;
    }

    /** A move that is out and in range hits once; the limb reaches {@link Fighter.Move#reach()} past the body edge. */
    private void checkHit(Fighter attacker, Fighter defender) {
        if (!attacker.hitting() || defender.state == Fighter.State.KNOCKED_OUT) {
            return;
        }
        float distance = (defender.x - attacker.x) * attacker.facing;
        if (distance > 0f && distance <= Fighter.WIDTH + attacker.reach()
            && Math.abs(defender.y - attacker.y) <= HIT_HEIGHT_RANGE) {
            defender.takeHit(attacker);
        }
    }

    private void keepApartAndOnStage() {
        float gap = Math.abs(cpu.x - player.x);
        boolean overlapping = gap < Fighter.WIDTH && Math.abs(cpu.y - player.y) < Fighter.HEIGHT;
        if (overlapping) {
            float push = (Fighter.WIDTH - gap) / 2f;
            float side = cpu.x >= player.x ? 1f : -1f;
            cpu.x += push * side;
            player.x -= push * side;
        }
        float limit = STAGE_HALF_WIDTH - Fighter.WIDTH / 2f;
        player.x = Math.max(-limit, Math.min(limit, player.x));
        cpu.x = Math.max(-limit, Math.min(limit, cpu.x));
        player.apply();
        cpu.apply();
    }

    private void startRound() {
        round++;
        player.reset();
        cpu.reset();
        roundTimeLeft = ROUND_TIME;
        state = State.INTRO;
        stateTimer = INTRO_TIME;
        plan = Plan.WAIT;
        thinkTimer = 0f;
        messageText.setText("Round " + round);
    }

    private void endRound(String reason) {
        String result;
        if (player.health > cpu.health) {
            playerRounds++;
            result = "You take round " + round;
        } else if (cpu.health > player.health) {
            cpuRounds++;
            result = "The CPU takes round " + round;
        } else {
            result = "Round " + round + " is a draw";
        }
        messageText.setText(reason + "  " + result);
        state = State.ROUND_OVER;
        stateTimer = ROUND_OVER_TIME;
    }

    private void updateHud() {
        playerHealthText.setText("You " + player.health);
        cpuHealthText.setText("CPU " + cpu.health);
        timerText.setText(Integer.toString((int) Math.ceil(roundTimeLeft)));
        roundsText.setText("Rounds " + playerRounds + " - " + cpuRounds);
        placeBar(playerBar, player.health, -1f);
        placeBar(cpuBar, cpu.health, 1f);
    }

    /** Scales a bar to the health left, keeping its outer end ({@code side} -1 left, 1 right) in place. */
    private static void placeBar(Entity bar, int health, float side) {
        float share = health / (float) Fighter.MAX_HEALTH;
        bar.setActive(share > 0f);
        if (share > 0f) {
            Vector3 position = bar.getTransform().getLocalPosition();
            bar.getTransform().setLocalScale(share, 1f, 1f);
            bar.getTransform().setLocalPosition(side * (BAR_OUTER_X - BAR_WIDTH * share / 2f), position.y, position.z);
        }
    }

    private void restart() {
        random = new Random(CPU_SEED);
        round = 0;
        playerRounds = 0;
        cpuRounds = 0;
        player.reset();
        cpu.reset();
        roundTimeLeft = ROUND_TIME;
        state = State.WAITING;
        plan = Plan.WAIT;
        messageText.setText(START_MESSAGE);
        updateHud();
    }
}
