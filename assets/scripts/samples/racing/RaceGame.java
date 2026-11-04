package samples.racing;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import io.github.ldev22live.gdxunity.engine.render.components.TextComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Rules of the race: the cars wait on the grid until the player presses accelerate, then after a 3-2-1 countdown
 * the "Player" car and the rival cars (entities whose name starts with
 * "Rival") race {@link #LAPS} laps of the circuit made by the "Road" pieces (see {@link RaceTrack}). Grass slows you
 * down, bumping into a rival costs speed, and the HUD shows lap, position, race time, best lap and speed. R restarts.
 *
 * <p>Put this script on one entity; it also drives the cars, so the cars need no scripts of their own.
 */
public class RaceGame extends ScriptComponent {
    static final int LAPS = 3;
    /** Seconds of countdown before the start, and how long "GO!" stays on screen afterwards. */
    static final float COUNTDOWN = 3f;
    static final float GO_MESSAGE_TIME = 1f;
    /** Racing speed of each rival, in scene order of their names (units per second). The player's top speed is 820. */
    static final float[] RIVAL_SPEEDS = {660f, 720f};
    /** Each rival keeps to its own line: units right (+) or left (-) of the centre line. */
    static final float[] RIVAL_LINES = {-50f, 50f};
    /** How far ahead on the track rivals aim, in units; larger values cut corners more smoothly. */
    static final float RIVAL_LOOK_AHEAD = 240f;
    /** Cars are treated as circles of this radius when they bump. */
    static final float CAR_RADIUS = 36f;
    /** Share of the player's speed kept after bumping a rival. */
    static final float BUMP_SPEED_KEPT = 0.5f;
    /** The player cannot leave this square around the origin (the edge of the grass). */
    static final float WORLD_HALF_SIZE = 2900f;
    /** Units per displayed metre: a car is 70 units long, about 4.4 m. */
    static final float UNITS_PER_METRE = 16f;
    /** Metres per second to kilometres per hour. */
    static final float MPS_TO_KMH = 3.6f;

    /** Shown on the grid; the race does not start by itself, so nobody misses the start. */
    static final String START_MESSAGE = "Press W or Up to start";

    private enum State { WAITING, COUNTDOWN, RACING, FINISHED }

    private final List<RaceCar> rivals = new ArrayList<>();
    private final Vector2 target = new Vector2();
    private RaceTrack track;
    private RaceCar player;
    private TextComponent lapText;
    private TextComponent positionText;
    private TextComponent timeText;
    private TextComponent bestText;
    private TextComponent speedText;
    private TextComponent messageText;
    private State state;
    private float countdown;
    private float raceTime;
    private float lapStartTime;
    private float bestLap;
    private float goMessageLeft;
    private int finishedCars;

    @Override
    public void onStart() {
        super.onStart();
        track = RaceTrack.fromScene(getScene());
        player = new RaceCar(getScene().findEntityByName("Player"));
        List<Entity> rivalEntities = new ArrayList<>();
        for (Entity entity : getScene().getAllEntities()) {
            if (entity.getName().startsWith("Rival")) {
                rivalEntities.add(entity);
            }
        }
        rivalEntities.sort(Comparator.comparing(Entity::getName));
        if (rivalEntities.size() > RIVAL_SPEEDS.length) {
            throw new IllegalStateException("RaceGame has settings for " + RIVAL_SPEEDS.length + " rivals, found "
                + rivalEntities.size());
        }
        for (Entity entity : rivalEntities) {
            rivals.add(new RaceCar(entity));
        }
        lapText = text("Lap Text");
        positionText = text("Position Text");
        timeText = text("Time Text");
        bestText = text("Best Text");
        speedText = text("Speed Text");
        messageText = text("Message Text");
        restart();
    }

    private TextComponent text(String name) {
        Entity entity = getScene().findEntityByName(name);
        if (entity == null || entity.getComponent(TextComponent.class) == null) {
            throw new IllegalStateException("RaceGame needs an entity named '" + name + "' with a Text component");
        }
        return entity.getComponent(TextComponent.class);
    }

    @Override
    public void onUpdate(float deltaTime) {
        if (Gdx.input.isKeyJustPressed(Keys.R)) {
            restart();
            return;
        }
        if (state == State.WAITING) {
            if (!Gdx.input.isKeyPressed(Keys.W) && !Gdx.input.isKeyPressed(Keys.UP)) {
                return;
            }
            state = State.COUNTDOWN;
        }
        if (state == State.COUNTDOWN) {
            countdown -= deltaTime;
            if (countdown > 0f) {
                messageText.setText(Integer.toString(MathUtils.ceil(countdown)));
                return;
            }
            state = State.RACING;
            goMessageLeft = GO_MESSAGE_TIME;
            messageText.setText("GO!");
        }
        if (goMessageLeft > 0f) {
            goMessageLeft -= deltaTime;
            if (goMessageLeft <= 0f && state == State.RACING) {
                messageText.setText("");
            }
        }
        if (state == State.RACING) {
            raceTime += deltaTime;
        }

        drivePlayer(deltaTime);
        for (int i = 0; i < rivals.size(); i++) {
            RaceCar rival = rivals.get(i);
            float speed = rival.finishPlace > 0 ? 0f : RIVAL_SPEEDS[i];
            track.pointAhead(rival.segment, rival.along, RIVAL_LOOK_AHEAD, RIVAL_LINES[i], target);
            rival.steerTowards(target, speed, deltaTime);
            if (rival.updateProgress(track) && rival.lap == LAPS) {
                rival.finishPlace = ++finishedCars;
            }
            bump(rival);
            rival.apply();
        }
        player.apply();
        updateHud();
    }

    private void drivePlayer(float deltaTime) {
        float throttle = 0f;
        float steer = 0f;
        if (Gdx.input.isKeyPressed(Keys.W) || Gdx.input.isKeyPressed(Keys.UP)) throttle += 1f;
        if (Gdx.input.isKeyPressed(Keys.S) || Gdx.input.isKeyPressed(Keys.DOWN)) throttle -= 1f;
        if (Gdx.input.isKeyPressed(Keys.A) || Gdx.input.isKeyPressed(Keys.LEFT)) steer += 1f;
        if (Gdx.input.isKeyPressed(Keys.D) || Gdx.input.isKeyPressed(Keys.RIGHT)) steer -= 1f;
        player.drive(throttle, steer, track.onRoad(player.x, player.z), deltaTime);
        player.x = MathUtils.clamp(player.x, -WORLD_HALF_SIZE, WORLD_HALF_SIZE);
        player.z = MathUtils.clamp(player.z, -WORLD_HALF_SIZE, WORLD_HALF_SIZE);

        if (player.updateProgress(track) && state == State.RACING) {
            float lapTime = raceTime - lapStartTime;
            lapStartTime = raceTime;
            if (bestLap == 0f || lapTime < bestLap) {
                bestLap = lapTime;
            }
            if (player.lap == LAPS) {
                player.finishPlace = ++finishedCars;
                state = State.FINISHED;
                messageText.setText("You finished " + ordinal(player.finishPlace) + " in " + clock(raceTime)
                    + "!  Press R to race again");
            }
        }
    }

    /** Pushes the player's car out of a rival it overlaps and takes away some of its speed. */
    private void bump(RaceCar rival) {
        float dx = player.x - rival.x;
        float dz = player.z - rival.z;
        float distance = (float) Math.sqrt(dx * dx + dz * dz);
        float minimum = CAR_RADIUS * 2f;
        if (distance >= minimum || distance == 0f) {
            return;
        }
        float push = (minimum - distance) / distance;
        player.x += dx * push;
        player.z += dz * push;
        player.speed *= BUMP_SPEED_KEPT;
    }

    private void updateHud() {
        lapText.setText("Lap " + Math.min(player.lap + 1, LAPS) + " / " + LAPS);
        positionText.setText("Position " + place() + " / " + (rivals.size() + 1));
        timeText.setText("Time " + clock(raceTime));
        bestText.setText("Best lap " + (bestLap == 0f ? "--" : clock(bestLap)));
        speedText.setText(Math.round(Math.abs(player.speed) / UNITS_PER_METRE * MPS_TO_KMH) + " km/h");
    }

    /** The player's place: finished cars by finishing order, the rest by how far round they are. */
    private int place() {
        if (player.finishPlace > 0) {
            return player.finishPlace;
        }
        float progress = player.progress(track);
        int place = 1;
        for (RaceCar rival : rivals) {
            if (rival.finishPlace > 0 || rival.progress(track) > progress) {
                place++;
            }
        }
        return place;
    }

    private void restart() {
        player.reset(track);
        for (RaceCar rival : rivals) {
            rival.reset(track);
        }
        state = State.WAITING;
        countdown = COUNTDOWN;
        raceTime = 0f;
        lapStartTime = 0f;
        bestLap = 0f;
        goMessageLeft = 0f;
        finishedCars = 0;
        messageText.setText(START_MESSAGE);
        updateHud();
    }

    /** Minutes, seconds and tenths, for example 1:05.3. */
    static String clock(float seconds) {
        int minutes = (int) (seconds / 60f);
        return String.format(Locale.ROOT, "%d:%04.1f", minutes, seconds - minutes * 60f);
    }

    static String ordinal(int place) {
        return switch (place) {
            case 1 -> "1st";
            case 2 -> "2nd";
            case 3 -> "3rd";
            default -> place + "th";
        };
    }
}
