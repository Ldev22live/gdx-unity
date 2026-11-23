package samples.blockworld;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.engine.render.components.TextComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.audio.AudioSourceComponent;
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A small block world in the style of a sandbox game: a world made of equal cubes that you walk around in first person,
 * dig out and build with. WASD walks, the arrow keys look, Space jumps, F breaks the block in the crosshair, G places the
 * selected block against it, 1 to 6 choose the block and R builds the world again.
 *
 * <p>The world is a grid of block kinds ({@link #cells}); only blocks with an open side are drawn, one pooled entity each, so
 * digging reveals the blocks behind and a few hundred entities cover the whole world. Collision is the player's box against
 * the grid, one axis at a time. Put this script on one entity; it also moves the entity named "Main Camera".
 */
public class BlockWorld extends ScriptComponent {
    /** World size in blocks along X and Z, and its height. */
    static final int SIZE_X = 16;
    static final int SIZE_Z = 16;
    static final int HEIGHT = 12;
    /** Edge length of one block, in scene units. */
    static final float BLOCK = 100f;
    /** Player box: half width, height and eye height above the feet. */
    static final float HALF_WIDTH = 30f;
    static final float BODY_HEIGHT = 170f;
    static final float EYE_HEIGHT = 150f;
    /** Walking speed, jump speed and gravity, in units per second (and squared). A jump clears a little over one block. */
    static final float WALK_SPEED = 380f;
    static final float JUMP_SPEED = 820f;
    static final float GRAVITY = 2300f;
    static final float MAX_FALL_SPEED = 1600f;
    /** A foot this close under a block boundary counts as on it when landing (absorbs rounding), in blocks. */
    static final float SNAP_TOLERANCE = 0.001f;
    /** Largest frame time simulated in one step, so a stutter cannot move the player through a wall. */
    static final float MAX_STEP = 1f / 30f;
    /** Turning and looking rates in degrees per second, and the furthest the player can look up or down. */
    static final float TURN_RATE = 110f;
    static final float LOOK_RATE = 80f;
    static final float MAX_PITCH = 89f;
    /** How far the player can reach, and how finely the sight line is stepped to find the block it hits. */
    static final float REACH = 520f;
    static final float RAY_STEP = 8f;
    /** The selection frame is a little larger than a block so it shows on every face. */
    static final float SELECTION_SIZE = BLOCK + 4f;
    /** Brightness variation between neighbouring blocks of one kind, standing in for a texture. */
    static final float SHADE_RANGE = 0.07f;
    /** The terrain: height of the flat area round the spawn, and how far the hills stand out from it. */
    static final int BASE_HEIGHT = 3;
    static final float FLAT_RADIUS = 2.5f;
    static final float HILL_RAMP = 4f;
    /** Trees stand at these columns: x, z. Trunk height is {@link #TRUNK}. */
    static final int[][] TREES = {{3, 3}, {12, 4}, {4, 12}, {12, 12}, {8, 2}};
    static final int TRUNK = 4;

    /** The kinds of block; the first is empty space. Colour is the kind's base colour. */
    enum Kind {
        AIR("Air", 0f, 0f, 0f),
        GRASS("Grass", 0.36f, 0.62f, 0.25f),
        DIRT("Dirt", 0.47f, 0.33f, 0.21f),
        STONE("Stone", 0.5f, 0.5f, 0.52f),
        PLANKS("Planks", 0.72f, 0.56f, 0.32f),
        WOOD("Wood", 0.4f, 0.28f, 0.16f),
        LEAVES("Leaves", 0.2f, 0.45f, 0.18f),
        SAND("Sand", 0.85f, 0.8f, 0.55f);

        final String label;
        final float red;
        final float green;
        final float blue;

        Kind(String label, float red, float green, float blue) {
            this.label = label;
            this.red = red;
            this.green = green;
            this.blue = blue;
        }
    }

    /** The blocks keys 1 to 6 select. */
    static final Kind[] HOTBAR = {Kind.GRASS, Kind.DIRT, Kind.STONE, Kind.PLANKS, Kind.WOOD, Kind.LEAVES};

    private final Kind[] kinds = Kind.values();
    private final byte[] cells = new byte[SIZE_X * HEIGHT * SIZE_Z];
    private final Map<Integer, Entity> shown = new HashMap<>();
    private final Deque<Entity> pool = new ArrayDeque<>();
    private Entity camera;
    private Entity selection;
    private TextComponent hotbarText;
    private TextComponent statsText;
    private float x;
    private float y;
    private float z;
    private float velocityY;
    private float yaw;
    private float pitch;
    private boolean grounded;
    private int selected;
    private int broken;
    private int placed;
    private int created;
    /** Cell the sight line hits (or -1), and the empty cell in front of it where a block would be placed (or -1). */
    private int targetCell = -1;
    private int placeCell = -1;

    @Override
    public void onStart() {
        super.onStart();
        camera = getScene().findEntityByName("Main Camera");
        selection = getScene().findEntityByName("Selection");
        hotbarText = text("Hotbar Text");
        statsText = text("Stats Text");
        if (camera == null || selection == null) {
            throw new IllegalStateException("BlockWorld needs entities named 'Main Camera' and 'Selection'");
        }
        selection.getComponent(MeshPrimitiveComponent.class).setSize(SELECTION_SIZE, SELECTION_SIZE, SELECTION_SIZE);
        buildWorld();
    }

    private TextComponent text(String name) {
        Entity entity = getScene().findEntityByName(name);
        if (entity == null || entity.getComponent(TextComponent.class) == null) {
            throw new IllegalStateException("BlockWorld needs an entity named '" + name + "' with a Text component");
        }
        return entity.getComponent(TextComponent.class);
    }

    @Override
    public void onUpdate(float deltaTime) {
        if (Gdx.input.isKeyJustPressed(Keys.R)) {
            buildWorld();
            return;
        }
        float step = Math.min(deltaTime, MAX_STEP);
        look(step);
        walk(step);
        aim();
        choose();
        if (targetCell >= 0 && Gdx.input.isKeyJustPressed(Keys.F)) {
            breakTarget();
        } else if (placeCell >= 0 && Gdx.input.isKeyJustPressed(Keys.G)) {
            placeAtTarget();
        }
        placeCamera();
        updateHud();
    }

    // --- The world ---

    /** Clears the world, grows the terrain and the trees, and puts the player on the ground at the middle. */
    private void buildWorld() {
        java.util.Arrays.fill(cells, (byte) 0);
        for (int cx = 0; cx < SIZE_X; cx++) {
            for (int cz = 0; cz < SIZE_Z; cz++) {
                int top = terrainHeight(cx, cz);
                for (int cy = 0; cy < top; cy++) {
                    Kind kind = cy == top - 1 ? (top <= BASE_HEIGHT - 1 ? Kind.SAND : Kind.GRASS)
                        : cy >= top - 3 ? Kind.DIRT : Kind.STONE;
                    cells[index(cx, cy, cz)] = (byte) kind.ordinal();
                }
            }
        }
        for (int[] tree : TREES) {
            growTree(tree[0], tree[1]);
        }
        for (Map.Entry<Integer, Entity> entry : shown.entrySet()) {
            entry.getValue().setActive(false);
            pool.push(entry.getValue());
        }
        shown.clear();
        for (int i = 0; i < cells.length; i++) {
            refresh(i);
        }
        // The middle of a block, so the player's box starts over one column and digging it out drops the player
        x = cellCentreX(SIZE_X / 2);
        z = cellCentreZ(SIZE_Z / 2);
        y = terrainHeight(SIZE_X / 2, SIZE_Z / 2) * BLOCK;
        velocityY = 0f;
        yaw = 0f;
        pitch = -20f;
        selected = 0;
        broken = 0;
        placed = 0;
        grounded = false;
        targetCell = -1;
        placeCell = -1;
        placeCamera();
        updateHud();
    }

    /** Hills away from the flat spawn area, with sand where the ground is lowest. */
    static int terrainHeight(int cx, int cz) {
        float dx = cx - (SIZE_X - 1) / 2f;
        float dz = cz - (SIZE_Z - 1) / 2f;
        float distance = (float) Math.sqrt(dx * dx + dz * dz);
        float hills = 1.6f * MathUtils.sin(cx * 0.55f) + 1.4f * MathUtils.cos(cz * 0.45f) + 0.8f * MathUtils.sin((cx + cz) * 0.3f);
        float weight = MathUtils.clamp((distance - FLAT_RADIUS) / HILL_RAMP, 0f, 1f);
        return Math.max(2, BASE_HEIGHT + Math.round(hills * weight));
    }

    private void growTree(int cx, int cz) {
        int base = terrainHeight(cx, cz);
        for (int i = 0; i < TRUNK; i++) {
            set(cx, base + i, cz, Kind.WOOD);
        }
        for (int layer = 0; layer < 3; layer++) {
            int reach = layer == 2 ? 1 : 2;
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    boolean corner = Math.abs(dx) == reach && Math.abs(dz) == reach;
                    if (!(corner && reach == 2 && layer == 1) && get(cx + dx, base + TRUNK - 2 + layer, cz + dz) == Kind.AIR) {
                        set(cx + dx, base + TRUNK - 2 + layer, cz + dz, Kind.LEAVES);
                    }
                }
            }
        }
    }

    private static int index(int cx, int cy, int cz) {
        return (cy * SIZE_Z + cz) * SIZE_X + cx;
    }

    private static boolean inside(int cx, int cy, int cz) {
        return cx >= 0 && cx < SIZE_X && cy >= 0 && cy < HEIGHT && cz >= 0 && cz < SIZE_Z;
    }

    /** The block at a cell; outside the world the sides and the floor count as stone and the sky as air. */
    private Kind get(int cx, int cy, int cz) {
        if (cy >= HEIGHT) {
            return Kind.AIR;
        }
        return inside(cx, cy, cz) ? kinds[cells[index(cx, cy, cz)]] : Kind.STONE;
    }

    private void set(int cx, int cy, int cz, Kind kind) {
        if (inside(cx, cy, cz)) {
            cells[index(cx, cy, cz)] = (byte) kind.ordinal();
        }
    }

    // --- Drawing the blocks that can be seen ---

    /** Shows or hides the entity of one cell, by whether the block is there and has an open side. */
    private void refresh(int cell) {
        int cx = cell % SIZE_X;
        int cz = cell / SIZE_X % SIZE_Z;
        int cy = cell / (SIZE_X * SIZE_Z);
        Kind kind = kinds[cells[cell]];
        boolean visible = kind != Kind.AIR && (get(cx + 1, cy, cz) == Kind.AIR || get(cx - 1, cy, cz) == Kind.AIR
            || get(cx, cy + 1, cz) == Kind.AIR || get(cx, cy - 1, cz) == Kind.AIR
            || get(cx, cy, cz + 1) == Kind.AIR || get(cx, cy, cz - 1) == Kind.AIR);
        Entity entity = shown.get(cell);
        if (!visible) {
            if (entity != null) {
                entity.setActive(false);
                pool.push(entity);
                shown.remove(cell);
            }
            return;
        }
        if (entity == null) {
            entity = pool.isEmpty() ? newBlockEntity() : pool.pop();
            shown.put(cell, entity);
            entity.getTransform().setLocalPosition(cellCentreX(cx), (cy + 0.5f) * BLOCK, cellCentreZ(cz));
            entity.setActive(true);
        }
        float shade = 1f + SHADE_RANGE * (((cx * 7 + cy * 13 + cz * 29) % 11) / 5f - 1f);
        entity.getComponent(MeshPrimitiveComponent.class).setColor(new Color(
            MathUtils.clamp(kind.red * shade, 0f, 1f), MathUtils.clamp(kind.green * shade, 0f, 1f),
            MathUtils.clamp(kind.blue * shade, 0f, 1f), 1f));
    }

    private Entity newBlockEntity() {
        Entity entity = getScene().createEntity("Block " + created++);
        entity.addComponent(new MeshPrimitiveComponent(MeshPrimitiveComponent.Shape.BOX, BLOCK, BLOCK, BLOCK, Color.WHITE));
        return entity;
    }

    /** Refreshes a cell and its six neighbours: they are the only ones whose open sides can have changed. */
    private void refreshAround(int cx, int cy, int cz) {
        int[][] offsets = {{0, 0, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int[] o : offsets) {
            if (inside(cx + o[0], cy + o[1], cz + o[2])) {
                refresh(index(cx + o[0], cy + o[1], cz + o[2]));
            }
        }
    }

    private static float cellCentreX(int cx) {
        return (cx - SIZE_X / 2f + 0.5f) * BLOCK;
    }

    private static float cellCentreZ(int cz) {
        return (cz - SIZE_Z / 2f + 0.5f) * BLOCK;
    }

    private static int cellOfX(float worldX) {
        return (int) Math.floor(worldX / BLOCK + SIZE_X / 2f);
    }

    private static int cellOfY(float worldY) {
        return (int) Math.floor(worldY / BLOCK);
    }

    private static int cellOfZ(float worldZ) {
        return (int) Math.floor(worldZ / BLOCK + SIZE_Z / 2f);
    }

    // --- The player ---

    private void look(float deltaTime) {
        if (Gdx.input.isKeyPressed(Keys.LEFT)) yaw += TURN_RATE * deltaTime;
        if (Gdx.input.isKeyPressed(Keys.RIGHT)) yaw -= TURN_RATE * deltaTime;
        if (Gdx.input.isKeyPressed(Keys.UP)) pitch += LOOK_RATE * deltaTime;
        if (Gdx.input.isKeyPressed(Keys.DOWN)) pitch -= LOOK_RATE * deltaTime;
        pitch = MathUtils.clamp(pitch, -MAX_PITCH, MAX_PITCH);
    }

    private void walk(float deltaTime) {
        float ahead = 0f;
        float right = 0f;
        if (Gdx.input.isKeyPressed(Keys.W)) ahead += 1f;
        if (Gdx.input.isKeyPressed(Keys.S)) ahead -= 1f;
        if (Gdx.input.isKeyPressed(Keys.D)) right += 1f;
        if (Gdx.input.isKeyPressed(Keys.A)) right -= 1f;
        float length = (float) Math.sqrt(ahead * ahead + right * right);
        if (length > 0f) {
            ahead /= length;
            right /= length;
        }
        float radians = yaw * MathUtils.degreesToRadians;
        float sin = MathUtils.sin(radians);
        float cos = MathUtils.cos(radians);
        // Forward is (-sin, -cos) in X and Z; right is (cos, -sin)
        float moveX = (-sin * ahead + cos * right) * WALK_SPEED * deltaTime;
        float moveZ = (-cos * ahead - sin * right) * WALK_SPEED * deltaTime;
        if (grounded && Gdx.input.isKeyJustPressed(Keys.SPACE)) {
            velocityY = JUMP_SPEED;
            AudioSourceComponent.play(getScene(), "jump");
        }
        velocityY = Math.max(velocityY - GRAVITY * deltaTime, -MAX_FALL_SPEED);
        x += moveAxis(0, moveX);
        z += moveAxis(2, moveZ);
        float moveY = velocityY * deltaTime;
        float movedY = moveAxis(1, moveY);
        grounded = moveY < 0f && movedY > moveY;
        if (movedY != moveY) {
            velocityY = 0f;
        }
        y += movedY;
        if (grounded) {
            // Land flush on the block instead of hovering up to a frame's fall above it
            y = (float) Math.floor(y / BLOCK + SNAP_TOLERANCE) * BLOCK;
        }
    }

    /** How far the player can move along an axis (0 = X, 1 = Y, 2 = Z) before the box meets a block; all of it if nothing is in the way. */
    private float moveAxis(int axis, float delta) {
        if (delta == 0f) {
            return 0f;
        }
        float nx = axis == 0 ? x + delta : x;
        float ny = axis == 1 ? y + delta : y;
        float nz = axis == 2 ? z + delta : z;
        return overlapsBlock(nx, ny, nz) ? 0f : delta;
    }

    /** Whether the player's box standing at the given feet position overlaps any solid block. */
    private boolean overlapsBlock(float px, float py, float pz) {
        int fromX = cellOfX(px - HALF_WIDTH);
        int toX = cellOfX(px + HALF_WIDTH - 0.01f);
        int fromY = cellOfY(py);
        int toY = cellOfY(py + BODY_HEIGHT - 0.01f);
        int fromZ = cellOfZ(pz - HALF_WIDTH);
        int toZ = cellOfZ(pz + HALF_WIDTH - 0.01f);
        for (int cx = fromX; cx <= toX; cx++) {
            for (int cy = fromY; cy <= toY; cy++) {
                for (int cz = fromZ; cz <= toZ; cz++) {
                    if (get(cx, cy, cz) != Kind.AIR) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // --- Breaking and building ---

    /** Finds the block in the crosshair and the empty cell in front of it by stepping along the line of sight. */
    private void aim() {
        float radians = pitch * MathUtils.degreesToRadians;
        float yawRadians = yaw * MathUtils.degreesToRadians;
        float dirX = -MathUtils.cos(radians) * MathUtils.sin(yawRadians);
        float dirY = MathUtils.sin(radians);
        float dirZ = -MathUtils.cos(radians) * MathUtils.cos(yawRadians);
        float eyeY = y + EYE_HEIGHT;
        int lastEmpty = -1;
        targetCell = -1;
        for (float distance = 0f; distance <= REACH; distance += RAY_STEP) {
            int cx = cellOfX(x + dirX * distance);
            int cy = cellOfY(eyeY + dirY * distance);
            int cz = cellOfZ(z + dirZ * distance);
            if (!inside(cx, cy, cz)) {
                if (cy >= 0 && cy < HEIGHT) {
                    break;
                }
                continue;
            }
            if (kinds[cells[index(cx, cy, cz)]] != Kind.AIR) {
                targetCell = index(cx, cy, cz);
                break;
            }
            lastEmpty = index(cx, cy, cz);
        }
        placeCell = targetCell >= 0 ? lastEmpty : -1;
        if (targetCell >= 0) {
            selection.setActive(true);
            selection.getTransform().setLocalPosition(cellCentreX(targetCell % SIZE_X),
                (targetCell / (SIZE_X * SIZE_Z) + 0.5f) * BLOCK, cellCentreZ(targetCell / SIZE_X % SIZE_Z));
        } else {
            selection.setActive(false);
        }
    }

    private void breakTarget() {
        int cx = targetCell % SIZE_X;
        int cz = targetCell / SIZE_X % SIZE_Z;
        int cy = targetCell / (SIZE_X * SIZE_Z);
        if (cy == 0) {
            return; // the bottom layer stays, so nobody digs out of the world
        }
        cells[targetCell] = (byte) Kind.AIR.ordinal();
        broken++;
        refreshAround(cx, cy, cz);
        AudioSourceComponent.play(getScene(), "break");
    }

    private void placeAtTarget() {
        int cx = placeCell % SIZE_X;
        int cz = placeCell / SIZE_X % SIZE_Z;
        int cy = placeCell / (SIZE_X * SIZE_Z);
        cells[placeCell] = (byte) HOTBAR[selected].ordinal();
        if (overlapsBlock(x, y, z)) {
            cells[placeCell] = (byte) Kind.AIR.ordinal(); // never build inside yourself
            return;
        }
        placed++;
        refreshAround(cx, cy, cz);
        AudioSourceComponent.play(getScene(), "place");
    }

    private void choose() {
        for (int i = 0; i < HOTBAR.length; i++) {
            if (Gdx.input.isKeyJustPressed(Keys.NUM_1 + i)) {
                selected = i;
            }
        }
    }

    private void placeCamera() {
        camera.getTransform().setLocalPosition(x, y + EYE_HEIGHT, z);
        camera.getTransform().setLocalEulerAngles(yaw, pitch, 0f);
    }

    private void updateHud() {
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < HOTBAR.length; i++) {
            bar.append(i == selected ? "[" : " ").append(i + 1).append(' ').append(HOTBAR[i].label).append(i == selected ? "]" : " ");
        }
        hotbarText.setText(bar.toString());
        statsText.setText(String.format(Locale.ROOT, "Broken %d   Placed %d   Height %d", broken, placed,
            Math.round(y / BLOCK)));
    }
}
