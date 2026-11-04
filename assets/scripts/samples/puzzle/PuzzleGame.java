package samples.puzzle;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.engine.render.components.TextComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptComponent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A box-pushing puzzle on a grid: walk the "Player" around and push every crate onto a target tile. Crates can be
 * pushed but not pulled, and only one at a time, so a crate pushed into a corner is stuck: undo or restart the level.
 *
 * <p>Levels are the text grids in {@link #LEVELS}: {@code #} wall, {@code .} target, {@code $} crate,
 * {@code *} crate on a target, {@code @} player, {@code +} player on a target, space floor. Add a grid to add a
 * level. The level's walls, floor, crates and targets are built from pooled entities when it loads, and the
 * "Main Camera" is placed to fit it.
 */
public class PuzzleGame extends ScriptComponent {
    /** The levels, easiest first. Each has as many crates as targets; all of them can be solved. */
    static final String[][] LEVELS = {
        {
            "########",
            "#      #",
            "# .$ @ #",
            "#      #",
            "########",
        },
        {
            "#######",
            "#.   .#",
            "# $ $ #",
            "#  @  #",
            "#######",
        },
        {
            "#########",
            "#   #   #",
            "# $   $ #",
            "#  ###  #",
            "# . @ . #",
            "#########",
        },
        {
            "#########",
            "#   #   #",
            "# $ $ $ #",
            "##  #  ##",
            " # ... #",
            " #  @  #",
            " #######",
        },
    };
    /** Size of one grid cell in world units. */
    static final float CELL = 100f;
    /** Seconds a step takes to animate; keys pressed during a step are ignored. */
    static final float STEP_TIME = 0.1f;
    /** Pool sizes: enough for the biggest level. */
    static final int WALL_POOL = 48;
    static final int FLOOR_POOL = 48;
    static final int CRATE_POOL = 6;
    /** Camera distance from the level centre per cell of level size, and its viewing angle down from level. */
    static final float CAMERA_DISTANCE_PER_CELL = 0.95f;
    static final float CAMERA_PITCH = -55f;
    /** Rows count this much more than columns when fitting the camera, because the view is tilted along the rows. */
    static final float ROW_FIT_FACTOR = 1.4f;
    static final Color CRATE_COLOR = new Color(0.66f, 0.46f, 0.25f, 1f);
    static final Color CRATE_DONE_COLOR = new Color(0.35f, 0.75f, 0.35f, 1f);

    private final List<Entity> walls = new ArrayList<>();
    private final List<Entity> floors = new ArrayList<>();
    private final List<Entity> crates = new ArrayList<>();
    private final List<Entity> targets = new ArrayList<>();
    /** Undo history: each entry is player column, row, then each crate's column and row, then the move count. */
    private final Deque<int[]> history = new ArrayDeque<>();
    private final Vector3 from = new Vector3();
    private final Vector3 to = new Vector3();
    private Entity player;
    private Entity camera;
    private TextComponent levelText;
    private TextComponent movesText;
    private TextComponent messageText;
    private int level;
    private boolean[][] wall;
    private boolean[][] target;
    private int columns;
    private int rows;
    private int playerColumn;
    private int playerRow;
    private int[] crateColumns;
    private int[] crateRows;
    private int moves;
    private boolean solved;
    /** The step being animated: which crate moves with the player (-1 for none) and how far along it is (0 to 1). */
    private int movingCrate = -1;
    private float stepProgress = 1f;
    private int fromColumn;
    private int fromRow;

    @Override
    public void onStart() {
        super.onStart();
        player = required("Player");
        camera = required("Main Camera");
        levelText = text("Level Text");
        movesText = text("Moves Text");
        messageText = text("Message Text");
        for (int i = 0; i < WALL_POOL; i++) {
            walls.add(pooled("Wall Block " + (i + 1), new MeshPrimitiveComponent(MeshPrimitiveComponent.Shape.BOX,
                CELL, CELL, CELL, new Color(0.45f, 0.47f, 0.55f, 1f))));
        }
        for (int i = 0; i < FLOOR_POOL; i++) {
            floors.add(pooled("Floor Tile " + (i + 1), new MeshPrimitiveComponent(MeshPrimitiveComponent.Shape.BOX,
                CELL - 2f, 4f, CELL - 2f, new Color(0.82f, 0.8f, 0.74f, 1f))));
        }
        for (int i = 0; i < CRATE_POOL; i++) {
            targets.add(pooled("Target Tile " + (i + 1), new MeshPrimitiveComponent(MeshPrimitiveComponent.Shape.BOX,
                CELL * 0.7f, 6f, CELL * 0.7f, new Color(0.95f, 0.78f, 0.2f, 1f))));
            crates.add(pooled("Crate " + (i + 1), new MeshPrimitiveComponent(MeshPrimitiveComponent.Shape.BOX,
                CELL * 0.8f, CELL * 0.8f, CELL * 0.8f, CRATE_COLOR)));
        }
        loadLevel(0);
    }

    private Entity pooled(String name, MeshPrimitiveComponent mesh) {
        Entity entity = getScene().createEntity(name);
        entity.addComponent(mesh);
        entity.setActive(false);
        return entity;
    }

    private Entity required(String name) {
        Entity entity = getScene().findEntityByName(name);
        if (entity == null) {
            throw new IllegalStateException("PuzzleGame needs an entity named '" + name + "'");
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
        if (stepProgress < 1f) {
            stepProgress = Math.min(1f, stepProgress + deltaTime / STEP_TIME);
            placeMovingPieces();
            return;
        }
        if (Gdx.input.isKeyJustPressed(Keys.R)) {
            loadLevel(level);
            return;
        }
        if (Gdx.input.isKeyJustPressed(Keys.U) || Gdx.input.isKeyJustPressed(Keys.Z)) {
            undo();
            return;
        }
        if (solved) {
            if (Gdx.input.isKeyJustPressed(Keys.SPACE)) {
                loadLevel((level + 1) % LEVELS.length);
            }
            return;
        }
        if (Gdx.input.isKeyJustPressed(Keys.W) || Gdx.input.isKeyJustPressed(Keys.UP)) {
            step(0, -1);
        } else if (Gdx.input.isKeyJustPressed(Keys.S) || Gdx.input.isKeyJustPressed(Keys.DOWN)) {
            step(0, 1);
        } else if (Gdx.input.isKeyJustPressed(Keys.A) || Gdx.input.isKeyJustPressed(Keys.LEFT)) {
            step(-1, 0);
        } else if (Gdx.input.isKeyJustPressed(Keys.D) || Gdx.input.isKeyJustPressed(Keys.RIGHT)) {
            step(1, 0);
        }
    }

    /** Tries to move the player one cell, pushing a crate if one is in the way and the cell behind it is free. */
    private void step(int dx, int dy) {
        int column = playerColumn + dx;
        int row = playerRow + dy;
        if (wall[row][column]) {
            return;
        }
        int crate = crateAt(column, row);
        if (crate >= 0) {
            int behindColumn = column + dx;
            int behindRow = row + dy;
            if (wall[behindRow][behindColumn] || crateAt(behindColumn, behindRow) >= 0) {
                return;
            }
            history.push(snapshot());
            crateColumns[crate] = behindColumn;
            crateRows[crate] = behindRow;
        } else {
            history.push(snapshot());
        }
        fromColumn = playerColumn;
        fromRow = playerRow;
        playerColumn = column;
        playerRow = row;
        movingCrate = crate;
        stepProgress = 0f;
        moves++;
        placeMovingPieces();
        if (allCratesOnTargets()) {
            solved = true;
            boolean last = level == LEVELS.length - 1;
            messageText.setText("Solved in " + moves + " moves!  Press Space for "
                + (last ? "the first puzzle again" : "the next puzzle"));
        }
        updateHud();
    }

    private void undo() {
        if (history.isEmpty()) {
            return;
        }
        int[] state = history.pop();
        playerColumn = state[0];
        playerRow = state[1];
        for (int i = 0; i < crateColumns.length; i++) {
            crateColumns[i] = state[2 + i * 2];
            crateRows[i] = state[3 + i * 2];
        }
        moves = state[state.length - 1];
        solved = false;
        messageText.setText("");
        placeAll();
        updateHud();
    }

    private int[] snapshot() {
        int[] state = new int[3 + crateColumns.length * 2];
        state[0] = playerColumn;
        state[1] = playerRow;
        for (int i = 0; i < crateColumns.length; i++) {
            state[2 + i * 2] = crateColumns[i];
            state[3 + i * 2] = crateRows[i];
        }
        state[state.length - 1] = moves;
        return state;
    }

    private int crateAt(int column, int row) {
        for (int i = 0; i < crateColumns.length; i++) {
            if (crateColumns[i] == column && crateRows[i] == row) {
                return i;
            }
        }
        return -1;
    }

    private boolean allCratesOnTargets() {
        for (int i = 0; i < crateColumns.length; i++) {
            if (!target[crateRows[i]][crateColumns[i]]) {
                return false;
            }
        }
        return true;
    }

    /** Reads a level grid and lays out its pieces. */
    private void loadLevel(int index) {
        level = index;
        String[] grid = LEVELS[index];
        rows = grid.length;
        columns = 0;
        for (String line : grid) {
            columns = Math.max(columns, line.length());
        }
        wall = new boolean[rows][columns];
        target = new boolean[rows][columns];
        List<int[]> crateCells = new ArrayList<>();
        int targetCount = 0;
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                char c = column < grid[row].length() ? grid[row].charAt(column) : ' ';
                wall[row][column] = c == '#';
                target[row][column] = c == '.' || c == '*' || c == '+';
                if (target[row][column]) {
                    targetCount++;
                }
                if (c == '$' || c == '*') {
                    crateCells.add(new int[] {column, row});
                }
                if (c == '@' || c == '+') {
                    playerColumn = column;
                    playerRow = row;
                }
            }
        }
        if (crateCells.size() != targetCount || crateCells.size() > CRATE_POOL) {
            throw new IllegalStateException("Level " + (index + 1) + " needs as many crates as targets (at most "
                + CRATE_POOL + "): " + crateCells.size() + " crates, " + targetCount + " targets");
        }
        crateColumns = new int[crateCells.size()];
        crateRows = new int[crateCells.size()];
        for (int i = 0; i < crateCells.size(); i++) {
            crateColumns[i] = crateCells.get(i)[0];
            crateRows[i] = crateCells.get(i)[1];
        }
        layOutLevel();
        history.clear();
        moves = 0;
        solved = false;
        stepProgress = 1f;
        movingCrate = -1;
        messageText.setText("");
        placeAll();
        placeCamera();
        updateHud();
    }

    /** Puts walls, floor tiles under the reachable area, and targets in place; unused pool entities are hidden. */
    private void layOutLevel() {
        boolean[][] inside = reachable();
        int wallCount = 0;
        int floorCount = 0;
        int targetCount = 0;
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                if (wall[row][column]) {
                    show(walls, wallCount++, column, row, CELL / 2f, "walls");
                } else if (inside[row][column]) {
                    show(floors, floorCount++, column, row, -2f, "floor tiles");
                    if (target[row][column]) {
                        show(targets, targetCount++, column, row, 1f, "targets");
                    }
                }
            }
        }
        hideFrom(walls, wallCount);
        hideFrom(floors, floorCount);
        hideFrom(targets, targetCount);
        hideFrom(crates, crateColumns.length);
        for (int i = 0; i < crateColumns.length; i++) {
            crates.get(i).setActive(true);
        }
    }

    /** Cells the player can walk to, ignoring crates: everything inside the walls. */
    private boolean[][] reachable() {
        boolean[][] inside = new boolean[rows][columns];
        Deque<int[]> open = new ArrayDeque<>();
        open.push(new int[] {playerColumn, playerRow});
        while (!open.isEmpty()) {
            int[] cell = open.pop();
            int column = cell[0];
            int row = cell[1];
            if (column < 0 || row < 0 || column >= columns || row >= rows || wall[row][column] || inside[row][column]) {
                continue;
            }
            inside[row][column] = true;
            open.push(new int[] {column + 1, row});
            open.push(new int[] {column - 1, row});
            open.push(new int[] {column, row + 1});
            open.push(new int[] {column, row - 1});
        }
        return inside;
    }

    private void show(List<Entity> pool, int index, int column, int row, float y, String what) {
        if (index >= pool.size()) {
            throw new IllegalStateException("Level " + (level + 1) + " has more " + what + " than the pool of "
                + pool.size());
        }
        Entity entity = pool.get(index);
        entity.getTransform().setLocalPosition(worldX(column), y, worldZ(row));
        entity.setActive(true);
    }

    private static void hideFrom(List<Entity> pool, int first) {
        for (int i = first; i < pool.size(); i++) {
            pool.get(i).setActive(false);
        }
    }

    private void placeAll() {
        player.getTransform().setLocalPosition(worldX(playerColumn), CELL * 0.35f, worldZ(playerRow));
        for (int i = 0; i < crateColumns.length; i++) {
            placeCrate(i, crateColumns[i], crateRows[i]);
        }
    }

    /** Animates the current step: the player (and a pushed crate) slide from the old cell to the new one. */
    private void placeMovingPieces() {
        float t = MathUtils.clamp(stepProgress, 0f, 1f);
        from.set(worldX(fromColumn), CELL * 0.35f, worldZ(fromRow));
        to.set(worldX(playerColumn), CELL * 0.35f, worldZ(playerRow));
        player.getTransform().setLocalPosition(from.lerp(to, t));
        if (movingCrate >= 0) {
            int dx = playerColumn - fromColumn;
            int dy = playerRow - fromRow;
            float column = crateColumns[movingCrate] - dx * (1f - t);
            float row = crateRows[movingCrate] - dy * (1f - t);
            crates.get(movingCrate).getTransform().setLocalPosition(worldX(column), CELL * 0.4f, worldZ(row));
            setCrateColor(movingCrate);
        }
    }

    private void placeCrate(int index, float column, float row) {
        crates.get(index).getTransform().setLocalPosition(worldX(column), CELL * 0.4f, worldZ(row));
        setCrateColor(index);
    }

    private void setCrateColor(int index) {
        boolean done = target[crateRows[index]][crateColumns[index]];
        crates.get(index).getComponent(MeshPrimitiveComponent.class).setColor(done ? CRATE_DONE_COLOR : CRATE_COLOR);
    }

    /** Above and in front of the level centre, far enough back to fit the whole grid. */
    private void placeCamera() {
        float distance = Math.max(columns, rows * ROW_FIT_FACTOR) * CELL * CAMERA_DISTANCE_PER_CELL;
        camera.getTransform().setLocalPosition(0f, distance * -MathUtils.sinDeg(CAMERA_PITCH),
            distance * MathUtils.cosDeg(CAMERA_PITCH));
        camera.getTransform().setLocalEulerAngles(0f, CAMERA_PITCH, 0f);
    }

    /** The level is centred on the origin: column 0 is on the left, row 0 at the back (-Z). */
    private float worldX(float column) {
        return (column - (columns - 1) / 2f) * CELL;
    }

    private float worldZ(float row) {
        return (row - (rows - 1) / 2f) * CELL;
    }

    private void updateHud() {
        levelText.setText("Puzzle " + (level + 1) + " / " + LEVELS.length);
        movesText.setText("Moves " + moves);
    }
}
