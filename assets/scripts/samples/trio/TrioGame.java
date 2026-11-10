package samples.trio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.Color;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.engine.render.components.TextComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.audio.AudioSourceComponent;
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent;
import samples.common.SideBody;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A 2.5D puzzle platformer with three heroes who share one body: switch at any time and the new hero appears where
 * the old one stood. Each hero has one strength:
 * <ul>
 *   <li><b>Mason</b> conjures crates (E) to stand on; at most {@link #MAX_CRATES}, the oldest disappears</li>
 *   <li><b>Scout</b> runs faster and can jump again in mid-air</li>
 *   <li><b>Brute</b> pushes "Heavy..." blocks and smashes "Cracked..." walls (E)</li>
 * </ul>
 * "Platform..." boxes are solid; "Hero Mason", "Hero Scout" and "Hero Brute" are the visible heroes; the empty
 * "Player" entity marks the shared position for the camera; "Goal" ends the level.
 */
public class TrioGame extends ScriptComponent {
    /** One hero's movement and look. */
    record Hero(String name, float runSpeed, float jumpSpeed, int airJumps, String help) {}

    static final Hero[] HEROES = {
        new Hero("Mason", 280f, 820f, 0, "E: conjure a crate"),
        new Hero("Scout", 360f, 820f, 1, "Space in mid-air: jump again"),
        new Hero("Brute", 240f, 720f, 0, "Push heavy blocks, E: smash cracked walls"),
    };
    static final float GRAVITY = 2000f;
    static final float FALL_LIMIT = -500f;
    /** Conjured crate size and how many can exist at once. */
    static final float CRATE_SIZE = 80f;
    static final int MAX_CRATES = 2;
    /** How far in front of the hero a crate appears (centre distance). */
    static final float CRATE_DISTANCE = 75f;
    /** How close the Brute must be to a cracked wall to smash it. */
    static final float SMASH_REACH = 40f;
    /** Collision half size shared by all heroes. */
    static final float HALF_WIDTH = 20f;
    static final float HALF_HEIGHT = 35f;

    private final Entity[] heroEntities = new Entity[HEROES.length];
    private final List<SideBody.Solid> solids = new ArrayList<>();
    private final List<SideBody> crates = new ArrayList<>();
    private final List<Entity> crateEntities = new ArrayList<>();
    private final Map<SideBody.Solid, SideBody> pushables = new HashMap<>();
    private final List<SideBody> heavyBlocks = new ArrayList<>();
    private final List<Entity> heavyEntities = new ArrayList<>();
    private final List<float[]> heavyHomes = new ArrayList<>();
    private final List<Entity> crackedWalls = new ArrayList<>();
    private SideBody.Solid goal;
    private Entity player;
    private SideBody body;
    private TextComponent partyText;
    private TextComponent messageText;
    private float startX;
    private float startY;
    private int hero;
    private int airJumpsLeft;
    private int facing = 1;
    private int nextCrate;
    private boolean finished;

    @Override
    public void onStart() {
        super.onStart();
        for (int i = 0; i < HEROES.length; i++) {
            heroEntities[i] = getScene().findEntityByName("Hero " + HEROES[i].name());
        }
        player = getScene().findEntityByName("Player");
        startX = player.getTransform().getLocalPosition().x;
        startY = player.getTransform().getLocalPosition().y;
        body = new SideBody(startX, startY, HALF_WIDTH, HALF_HEIGHT);
        solids.addAll(SideBody.solidsNamed(getScene(), "Platform", "Cracked"));
        for (Entity entity : getScene().getAllEntities()) {
            if (entity.getName().startsWith("Cracked")) {
                crackedWalls.add(entity);
            } else if (entity.getName().startsWith("Heavy")) {
                addPushable(entity, heavyBlocks, heavyEntities);
                heavyHomes.add(new float[] {heavyBlocks.get(heavyBlocks.size() - 1).x, heavyBlocks.get(heavyBlocks.size() - 1).y});
            }
        }
        for (int i = 0; i < MAX_CRATES; i++) {
            Entity crate = getScene().createEntity("Conjured Crate " + i);
            crate.addComponent(new MeshPrimitiveComponent(MeshPrimitiveComponent.Shape.BOX,
                CRATE_SIZE, CRATE_SIZE, CRATE_SIZE, new Color(0.55f, 0.75f, 1f, 1f)));
            crate.setActive(false);
            addPushable(crate, crates, crateEntities);
        }
        goal = new SideBody.Solid(getScene().findEntityByName("Goal"));
        partyText = getScene().findEntityByName("Party Text").getComponent(TextComponent.class);
        messageText = getScene().findEntityByName("Message Text").getComponent(TextComponent.class);
        restart();
    }

    /** Makes {@code entity} a falling, pushable box that others can stand on. */
    private void addPushable(Entity entity, List<SideBody> bodies, List<Entity> entities) {
        SideBody.Solid solid = new SideBody.Solid(entity);
        SideBody pushable = new SideBody(solid.x, solid.y, solid.halfWidth, solid.halfHeight);
        pushable.self = solid;
        solids.add(solid);
        pushables.put(solid, pushable);
        bodies.add(pushable);
        entities.add(entity);
    }

    @Override
    public void onUpdate(float deltaTime) {
        if (Gdx.input.isKeyJustPressed(Keys.R)) {
            restart();
            return;
        }
        if (finished) {
            return;
        }
        handleSwitching();
        Hero current = HEROES[hero];

        float direction = 0f;
        if (Gdx.input.isKeyPressed(Keys.A) || Gdx.input.isKeyPressed(Keys.LEFT)) direction -= 1f;
        if (Gdx.input.isKeyPressed(Keys.D) || Gdx.input.isKeyPressed(Keys.RIGHT)) direction += 1f;
        if (direction != 0f) {
            facing = (int) direction;
        }
        body.velocityX = direction * current.runSpeed();
        if (body.grounded) {
            airJumpsLeft = current.airJumps();
        }
        if (Gdx.input.isKeyJustPressed(Keys.SPACE) || Gdx.input.isKeyJustPressed(Keys.W) || Gdx.input.isKeyJustPressed(Keys.UP)) {
            if (body.grounded) {
                body.velocityY = current.jumpSpeed();
                AudioSourceComponent.play(getScene(), "jump");
            } else if (airJumpsLeft > 0) {
                airJumpsLeft--;
                body.velocityY = current.jumpSpeed();
                AudioSourceComponent.play(getScene(), "jump");
            }
        }
        if (Gdx.input.isKeyJustPressed(Keys.E)) {
            useAbility();
            AudioSourceComponent.play(getScene(), "ability");
        }

        stepPushables(crates, crateEntities, deltaTime);
        stepPushables(heavyBlocks, heavyEntities, deltaTime);
        body.step(deltaTime, GRAVITY, solids, this::push);
        body.moveEntity(player, 0f);
        body.moveEntity(heroEntities[hero], 0f);

        if (body.y < FALL_LIMIT) {
            body.x = startX;
            body.y = startY;
            body.velocityY = 0f;
        }
        if (body.touches(goal.x, goal.y, goal.halfWidth, goal.halfHeight)) {
            finished = true;
            AudioSourceComponent.play(getScene(), "win");
            messageText.setText("The party reached the top! Press R to play again");
        }
    }

    private void handleSwitching() {
        int chosen = hero;
        if (Gdx.input.isKeyJustPressed(Keys.NUM_1)) chosen = 0;
        if (Gdx.input.isKeyJustPressed(Keys.NUM_2)) chosen = 1;
        if (Gdx.input.isKeyJustPressed(Keys.NUM_3)) chosen = 2;
        if (Gdx.input.isKeyJustPressed(Keys.TAB)) chosen = (hero + 1) % HEROES.length;
        if (chosen != hero) {
            selectHero(chosen);
        }
    }

    private void selectHero(int index) {
        hero = index;
        for (int i = 0; i < heroEntities.length; i++) {
            heroEntities[i].setActive(i == index);
        }
        body.moveEntity(heroEntities[index], 0f);
        partyText.setText("Hero: " + HEROES[index].name() + "  (" + HEROES[index].help() + ")");
    }

    private void useAbility() {
        if (HEROES[hero].name().equals("Mason")) {
            conjureCrate();
        } else if (HEROES[hero].name().equals("Brute")) {
            smashWall();
        }
    }

    /** Places a crate in front of the Mason if the space is free; the oldest crate is reused when all are out. */
    private void conjureCrate() {
        float x = body.x + facing * CRATE_DISTANCE;
        float y = body.y - HALF_HEIGHT + CRATE_SIZE / 2f + 1f;
        float half = CRATE_SIZE / 2f;
        for (SideBody.Solid solid : solids) {
            if (solid.isSolid() && solid.entity != crateEntities.get(nextCrate) && solid.overlaps(x, y, half, half)) {
                messageText.setText("No room for a crate there");
                return;
            }
        }
        SideBody crate = crates.get(nextCrate);
        crate.x = x;
        crate.y = y;
        crate.velocityY = 0f;
        Entity entity = crateEntities.get(nextCrate);
        entity.setActive(true);
        crate.moveEntity(entity, 0f);
        crate.self.refresh();
        nextCrate = (nextCrate + 1) % MAX_CRATES;
        messageText.setText("");
    }

    private void smashWall() {
        for (Entity wall : crackedWalls) {
            SideBody.Solid solid = solidOf(wall);
            if (wall.isActiveSelf() && body.touches(solid.x, solid.y, solid.halfWidth + SMASH_REACH, solid.halfHeight)) {
                wall.setActive(false);
                return;
            }
        }
    }

    /** Crates can be pushed by every hero; heavy blocks only by the Brute. */
    private boolean push(SideBody.Solid solid, float amount) {
        SideBody pushed = pushables.get(solid);
        if (pushed == null || (heavyBlocks.contains(pushed) && !HEROES[hero].name().equals("Brute"))) {
            return false;
        }
        pushed.moveX(amount, solids, null);
        solid.x = pushed.x;
        solid.entity.getTransform().setLocalPosition(pushed.x, pushed.y, 0f);
        return true;
    }

    private void stepPushables(List<SideBody> bodies, List<Entity> entities, float deltaTime) {
        for (int i = 0; i < bodies.size(); i++) {
            if (!entities.get(i).isActiveSelf()) {
                continue;
            }
            SideBody pushable = bodies.get(i);
            pushable.velocityX = 0f;
            pushable.step(deltaTime, GRAVITY, solids, null);
            pushable.moveEntity(entities.get(i), 0f);
            pushable.self.refresh();
            if (pushable.y < FALL_LIMIT) {
                entities.get(i).setActive(false);
            }
        }
    }

    private SideBody.Solid solidOf(Entity entity) {
        for (SideBody.Solid solid : solids) {
            if (solid.entity == entity) {
                return solid;
            }
        }
        throw new IllegalStateException("'" + entity.getName() + "' is not a solid");
    }

    private void restart() {
        body.x = startX;
        body.y = startY;
        body.velocityX = 0f;
        body.velocityY = 0f;
        finished = false;
        nextCrate = 0;
        for (Entity crate : crateEntities) {
            crate.setActive(false);
        }
        for (Entity wall : crackedWalls) {
            wall.setActive(true);
        }
        for (int i = 0; i < heavyBlocks.size(); i++) {
            SideBody block = heavyBlocks.get(i);
            block.x = heavyHomes.get(i)[0];
            block.y = heavyHomes.get(i)[1];
            block.velocityY = 0f;
            block.moveEntity(heavyEntities.get(i), 0f);
            block.self.refresh();
        }
        body.moveEntity(player, 0f);
        selectHero(0);
        messageText.setText("");
    }
}
