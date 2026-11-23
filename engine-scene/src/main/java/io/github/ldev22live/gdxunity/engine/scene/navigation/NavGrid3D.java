package io.github.ldev22live.gdxunity.engine.scene.navigation;

import com.badlogic.gdx.math.Vector3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A baked walkable grid (roadmap M6.4, TD-092): the result of {@link NavMeshBaker}, and what {@link NavAgent3DComponent}s find their
 * paths on. Cell ({@code column}, {@code row}) covers x from {@code originX + column * cellSize} and z from
 * {@code originZ + row * cellSize}, one cell size wide. Each cell has at most one floor height (NaN for none) and is walkable or not;
 * two neighbouring walkable cells are connected when their floors differ by at most {@link #getClimb()}.
 *
 * <p>Paths are found with A* over the eight neighbours (a diagonal step needs both side cells connected, so paths never cut a corner),
 * then straightened: a waypoint is dropped whenever the straight line past it stays on connected walkable cells. Immutable.
 */
public final class NavGrid3D {
    /** How far, in cells, {@link #findPath} looks for the nearest walkable cell when a start or end is on an unwalkable one. */
    public static final int NEAREST_SEARCH_CELLS = 16;
    private static final float DIAGONAL = (float) Math.sqrt(2.0);
    private static final int[] DX = {1, -1, 0, 0, 1, 1, -1, -1};
    private static final int[] DZ = {0, 0, 1, -1, 1, -1, 1, -1};

    private final int columns;
    private final int rows;
    private final float originX;
    private final float originZ;
    private final float cellSize;
    private final float climb;
    private final float[] floor;
    private final boolean[] walkable;

    /**
     * @param floor    {@code columns * rows} floor heights, row by row; NaN where there is no floor
     * @param walkable whether each cell can be stood on; a cell without a floor never is
     */
    NavGrid3D(int columns, int rows, float originX, float originZ, float cellSize, float climb, float[] floor, boolean[] walkable) {
        if (columns < 1 || rows < 1) {
            throw new IllegalArgumentException("A grid needs at least one cell: " + columns + " by " + rows);
        }
        if (!(cellSize > 0f) || !(climb >= 0f)) {
            throw new IllegalArgumentException("Cell size must be above 0 and climb 0 or more: " + cellSize + ", " + climb);
        }
        Objects.requireNonNull(floor, "floor cannot be null");
        Objects.requireNonNull(walkable, "walkable cannot be null");
        if (floor.length != columns * rows || walkable.length != columns * rows) {
            throw new IllegalArgumentException("Expected " + columns * rows + " cells, got " + floor.length + " and " + walkable.length);
        }
        this.columns = columns;
        this.rows = rows;
        this.originX = originX;
        this.originZ = originZ;
        this.cellSize = cellSize;
        this.climb = climb;
        this.floor = floor.clone();
        this.walkable = walkable.clone();
        for (int i = 0; i < this.walkable.length; i++) {
            this.walkable[i] &= !Float.isNaN(this.floor[i]);
        }
    }

    public int getColumns() {
        return columns;
    }

    public int getRows() {
        return rows;
    }

    /** World x of the grid's low edge. */
    public float getOriginX() {
        return originX;
    }

    /** World z of the grid's low edge. */
    public float getOriginZ() {
        return originZ;
    }

    public float getCellSize() {
        return cellSize;
    }

    /** Largest floor height difference between two neighbouring cells that an agent walks across. */
    public float getClimb() {
        return climb;
    }

    /** The column holding world x, or -1 outside the grid. */
    public int columnOf(float x) {
        int column = (int) Math.floor((x - originX) / cellSize);
        return column >= 0 && column < columns ? column : -1;
    }

    /** The row holding world z, or -1 outside the grid. */
    public int rowOf(float z) {
        int row = (int) Math.floor((z - originZ) / cellSize);
        return row >= 0 && row < rows ? row : -1;
    }

    /** Whether world x, z lies over the grid. */
    public boolean contains(float x, float z) {
        return columnOf(x) >= 0 && rowOf(z) >= 0;
    }

    /** World x of a column's centre. */
    public float centerX(int column) {
        return originX + (column + 0.5f) * cellSize;
    }

    /** World z of a row's centre. */
    public float centerZ(int row) {
        return originZ + (row + 0.5f) * cellSize;
    }

    /** Floor height of a cell, or NaN when it has none. */
    public float floorAt(int column, int row) {
        return floor[index(column, row)];
    }

    /** Whether an agent can stand on a cell. */
    public boolean isWalkable(int column, int row) {
        return walkable[index(column, row)];
    }

    /** How many cells are walkable. */
    public int walkableCount() {
        int count = 0;
        for (boolean cell : walkable) {
            if (cell) count++;
        }
        return count;
    }

    /** Whether world x, z is over a walkable cell. */
    public boolean isWalkableAt(float x, float z) {
        int column = columnOf(x);
        int row = rowOf(z);
        return column >= 0 && row >= 0 && walkable[row * columns + column];
    }

    /**
     * A path from {@code from} to {@code to} as world waypoints on the floor, not including the start; empty when there is none
     * (either end is off the grid or more than {@link #NEAREST_SEARCH_CELLS} cells from a walkable cell, or the two are not
     * connected). An end on an unwalkable cell moves to the nearest walkable one. The last waypoint is {@code to}'s x and z when its
     * cell is walkable, else that nearest cell's centre.
     */
    public List<Vector3> findPath(Vector3 from, Vector3 to) {
        Objects.requireNonNull(from, "from cannot be null");
        Objects.requireNonNull(to, "to cannot be null");
        int start = nearestWalkable(from.x, from.z);
        int goal = nearestWalkable(to.x, to.z);
        if (start < 0 || goal < 0) {
            return List.of();
        }
        int[] cells = search(start, goal);
        if (cells == null) {
            return List.of();
        }
        boolean exactEnd = goal == cellIndexAt(to.x, to.z);
        float endX = exactEnd ? to.x : centerX(goal % columns);
        float endZ = exactEnd ? to.z : centerZ(goal / columns);
        List<float[]> points = new ArrayList<>();
        points.add(new float[] {from.x, from.z, start});
        for (int i = 1; i < cells.length - 1; i++) {
            points.add(new float[] {centerX(cells[i] % columns), centerZ(cells[i] / columns), cells[i]});
        }
        points.add(new float[] {endX, endZ, goal});
        List<Vector3> path = new ArrayList<>();
        int anchor = 0;
        while (anchor < points.size() - 1) {
            int next = anchor + 1;
            // The farthest point still in a straight, walkable line from the anchor
            for (int candidate = points.size() - 1; candidate > anchor + 1; candidate--) {
                if (straight(points.get(anchor), points.get(candidate))) {
                    next = candidate;
                    break;
                }
            }
            float[] point = points.get(next);
            path.add(new Vector3(point[0], floor[(int) point[2]], point[1]));
            anchor = next;
        }
        return Collections.unmodifiableList(path);
    }

    /**
     * The outline of the walkable area for drawing: every side of a walkable cell whose neighbour across it is not connected (or off
     * the grid), as line segments at the cell's floor height plus {@code lift}, six numbers each (x1, y1, z1, x2, y2, z2).
     */
    public float[] outline(float lift) {
        float[] lines = new float[64];
        int size = 0;
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int at = row * columns + column;
                if (!walkable[at]) continue;
                float y = floor[at] + lift;
                float x0 = originX + column * cellSize;
                float z0 = originZ + row * cellSize;
                float x1 = x0 + cellSize;
                float z1 = z0 + cellSize;
                // Sides: -x, +x, -z, +z
                for (int side = 0; side < 4; side++) {
                    int c = column + (side == 0 ? -1 : side == 1 ? 1 : 0);
                    int r = row + (side == 2 ? -1 : side == 3 ? 1 : 0);
                    boolean open = c >= 0 && r >= 0 && c < columns && r < rows && connected(at, r * columns + c);
                    if (open) continue;
                    if (size + 6 > lines.length) lines = Arrays.copyOf(lines, lines.length * 2);
                    float ax = side == 1 ? x1 : x0;
                    float az = side == 3 ? z1 : z0;
                    float bx = side == 0 ? x0 : x1;
                    float bz = side == 2 ? z0 : z1;
                    lines[size++] = ax;
                    lines[size++] = y;
                    lines[size++] = az;
                    lines[size++] = bx;
                    lines[size++] = y;
                    lines[size++] = bz;
                }
            }
        }
        return Arrays.copyOf(lines, size);
    }

    /** The walkable cell nearest world x, z within {@link #NEAREST_SEARCH_CELLS}, as an index, or -1. */
    int nearestWalkable(float x, float z) {
        int column = (int) Math.floor((x - originX) / cellSize);
        int row = (int) Math.floor((z - originZ) / cellSize);
        int best = -1;
        float bestDistance = Float.MAX_VALUE;
        for (int ring = 0; ring <= NEAREST_SEARCH_CELLS && best < 0; ring++) {
            for (int r = row - ring; r <= row + ring; r++) {
                for (int c = column - ring; c <= column + ring; c++) {
                    if (Math.max(Math.abs(r - row), Math.abs(c - column)) != ring || c < 0 || r < 0 || c >= columns || r >= rows) {
                        continue;
                    }
                    int at = r * columns + c;
                    if (!walkable[at]) continue;
                    float dx = centerX(c) - x;
                    float dz = centerZ(r) - z;
                    float distance = dx * dx + dz * dz;
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = at;
                    }
                }
            }
        }
        return best;
    }

    private int cellIndexAt(float x, float z) {
        int column = columnOf(x);
        int row = rowOf(z);
        return column < 0 || row < 0 ? -1 : row * columns + column;
    }

    /** Whether two cells can be walked between directly: both walkable, floors within the climb. */
    boolean connected(int a, int b) {
        return walkable[a] && walkable[b] && Math.abs(floor[a] - floor[b]) <= climb;
    }

    /** A* over connected neighbours; the cells from start to goal, or null when the goal cannot be reached. */
    private int[] search(int start, int goal) {
        int count = columns * rows;
        float[] cost = new float[count];
        Arrays.fill(cost, Float.POSITIVE_INFINITY);
        int[] cameFrom = new int[count];
        boolean[] closed = new boolean[count];
        CellHeap open = new CellHeap();
        cost[start] = 0f;
        cameFrom[start] = -1;
        open.push(start, heuristic(start, goal));
        while (!open.isEmpty()) {
            int current = open.pop();
            if (closed[current]) continue;
            if (current == goal) {
                int length = 0;
                for (int at = goal; at >= 0; at = cameFrom[at]) length++;
                int[] cells = new int[length];
                for (int at = goal, i = length - 1; at >= 0; at = cameFrom[at], i--) cells[i] = at;
                return cells;
            }
            closed[current] = true;
            int column = current % columns;
            int row = current / columns;
            for (int d = 0; d < DX.length; d++) {
                int c = column + DX[d];
                int r = row + DZ[d];
                if (c < 0 || r < 0 || c >= columns || r >= rows) continue;
                int next = r * columns + c;
                if (closed[next] || !connected(current, next)) continue;
                boolean diagonal = DX[d] != 0 && DZ[d] != 0;
                if (diagonal && !(connected(current, row * columns + c) && connected(current, r * columns + column))) {
                    continue;
                }
                float step = (diagonal ? DIAGONAL : 1f) * cellSize;
                float candidate = cost[current] + step;
                if (candidate < cost[next]) {
                    cost[next] = candidate;
                    cameFrom[next] = current;
                    open.push(next, candidate + heuristic(next, goal));
                }
            }
        }
        return null;
    }

    /** Octile distance in world units: never more than the true path length, so A* finds the shortest path. */
    private float heuristic(int from, int to) {
        int dx = Math.abs(from % columns - to % columns);
        int dz = Math.abs(from / columns - to / columns);
        return (Math.max(dx, dz) + (DIAGONAL - 1f) * Math.min(dx, dz)) * cellSize;
    }

    /**
     * Whether the straight line between two path points ({x, z, cell}) crosses only connected walkable cells, checked every quarter
     * cell. Walkable cells already keep the agent's radius from walls (the bake's clearance), so the line keeps it too.
     */
    private boolean straight(float[] a, float[] b) {
        float dx = b[0] - a[0];
        float dz = b[1] - a[1];
        float length = (float) Math.sqrt(dx * dx + dz * dz);
        int steps = Math.max(1, (int) Math.ceil(length / (cellSize * 0.25f)));
        int previous = (int) a[2];
        for (int i = 1; i <= steps; i++) {
            float t = (float) i / steps;
            int cell = cellIndexAt(a[0] + dx * t, a[1] + dz * t);
            if (cell < 0) return false;
            if (cell != previous) {
                if (!connected(previous, cell)) return false;
                // Moving diagonally between two cells must not slip past a blocked corner
                int pc = previous % columns, pr = previous / columns, cc = cell % columns, cr = cell / columns;
                if (pc != cc && pr != cr && !(connected(previous, pr * columns + cc) && connected(previous, cr * columns + pc))) {
                    return false;
                }
                previous = cell;
            }
        }
        return true;
    }

    private int index(int column, int row) {
        if (column < 0 || row < 0 || column >= columns || row >= rows) {
            throw new IndexOutOfBoundsException("Cell " + column + ", " + row + " is outside " + columns + " by " + rows);
        }
        return row * columns + column;
    }

    /** A binary min-heap of cells by priority; a cell may be pushed again with a better priority (stale entries are skipped). */
    private static final class CellHeap {
        private int[] cells = new int[64];
        private float[] priorities = new float[64];
        private int size;

        boolean isEmpty() {
            return size == 0;
        }

        void push(int cell, float priority) {
            if (size == cells.length) {
                cells = Arrays.copyOf(cells, size * 2);
                priorities = Arrays.copyOf(priorities, size * 2);
            }
            int at = size++;
            while (at > 0) {
                int parent = (at - 1) / 2;
                if (priorities[parent] <= priority) break;
                cells[at] = cells[parent];
                priorities[at] = priorities[parent];
                at = parent;
            }
            cells[at] = cell;
            priorities[at] = priority;
        }

        int pop() {
            int top = cells[0];
            size--;
            int cell = cells[size];
            float priority = priorities[size];
            int at = 0;
            while (true) {
                int child = 2 * at + 1;
                if (child >= size) break;
                if (child + 1 < size && priorities[child + 1] < priorities[child]) child++;
                if (priorities[child] >= priority) break;
                cells[at] = cells[child];
                priorities[at] = priorities[child];
                at = child;
            }
            cells[at] = cell;
            priorities[at] = priority;
            return top;
        }
    }
}
