package io.github.ldev22live.gdxunity.engine.core.profile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Where a frame's time goes (roadmap M9.6): named sections, nested under a parent, timed with {@code begin}/{@code end} and kept for
 * the last {@link #getHistorySize()} frames, so a panel can show one frame's breakdown and how frame times moved.
 * <p>
 * A section timed several times in one frame (one per viewport pane, one per component of a type) adds up. With a
 * {@link CounterSource} each section also records how much each counter (draw calls, vertices) grew while it was open, which is what
 * a frame debugger shows per pass. Frame values ({@link #value}) are numbers that belong to the frame as a whole, such as the entity
 * count.
 * <p>
 * Nothing is recorded while not {@linkplain #setRecording recording} or while {@linkplain #setPaused paused}: then every call returns
 * at once, so the hooks can stay in the frame loop. All storage is allocated when the profiler is made (and when a counter source is
 * set); recording a frame allocates nothing. Not thread-safe: use it from the thread that runs the frame.
 */
public final class FrameProfiler {
    /** The parent of a top-level section. */
    public static final int ROOT = -1;
    /** Frames kept by default: five seconds at 60 frames a second. */
    public static final int DEFAULT_HISTORY = 300;
    /** Sections one profiler can hold; registering more fails. Enough for the editor's passes and every component type. */
    public static final int MAX_SECTIONS = 128;
    /** Frame values one profiler can hold. */
    public static final int MAX_VALUES = 16;

    /**
     * Running totals read when a section begins and ends, such as LibGDX's {@code GLProfiler} draw calls. Only the growth during the
     * section is recorded, so a source never needs resetting except to keep its numbers small ({@link #frameStarted()}).
     */
    public interface CounterSource {
        /** How many counters; fixed for the source's life. */
        int size();

        /** A counter's name, such as "Draw calls". */
        String name(int counter);

        /** A counter's running total now. */
        long read(int counter);

        /** Called when a recorded frame begins; a source may reset its totals here. */
        default void frameStarted() {
        }
    }

    private final int historySize;
    private final LongSupplier clock;
    private final List<String> sectionNames = new ArrayList<>();
    private final int[] sectionParents = new int[MAX_SECTIONS];
    private final int[] sectionDepths = new int[MAX_SECTIONS];
    private final List<String> valueNames = new ArrayList<>();

    private final long[] frameNanos;
    private final long[] periodNanos;
    private final long[][] sectionNanos;
    private final int[][] sectionCalls;
    private final long[][] values;
    private long[][][] sectionCounts;

    private final long[] openSince = new long[MAX_SECTIONS];
    private final boolean[] open = new boolean[MAX_SECTIONS];
    private long[][] countersAtBegin;
    private CounterSource counters;

    private boolean recording;
    private boolean paused;
    private boolean inFrame;
    private long frameStart;
    private long lastFrameStart = -1L;
    /** Slot of the frame being recorded, or of the newest one between frames. */
    private int slot = -1;
    private int stored;
    private long recordedFrames;

    /** A profiler keeping {@link #DEFAULT_HISTORY} frames, timed with {@link System#nanoTime()}. */
    public FrameProfiler() {
        this(DEFAULT_HISTORY, System::nanoTime);
    }

    /**
     * @param historySize frames kept, at least 1
     * @param clock       nanoseconds, monotonic; a test passes its own
     */
    public FrameProfiler(int historySize, LongSupplier clock) {
        if (historySize < 1) {
            throw new IllegalArgumentException("historySize must be at least 1: " + historySize);
        }
        this.historySize = historySize;
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
        frameNanos = new long[historySize];
        periodNanos = new long[historySize];
        sectionNanos = new long[historySize][MAX_SECTIONS];
        sectionCalls = new int[historySize][MAX_SECTIONS];
        values = new long[historySize][MAX_VALUES];
    }

    /**
     * The id of the section called {@code name} under {@code parent}, registering it the first time. The same name under another
     * parent is another section.
     *
     * @param parent {@link #ROOT} or a section id
     * @throws IllegalStateException when {@link #MAX_SECTIONS} sections exist already
     */
    public int section(String name, int parent) {
        Objects.requireNonNull(name, "name cannot be null");
        if (parent != ROOT) {
            checkSection(parent);
        }
        for (int i = 0; i < sectionNames.size(); i++) {
            if (sectionParents[i] == parent && sectionNames.get(i).equals(name)) {
                return i;
            }
        }
        int id = sectionNames.size();
        if (id == MAX_SECTIONS) {
            throw new IllegalStateException("The profiler holds at most " + MAX_SECTIONS + " sections; cannot add " + name);
        }
        sectionNames.add(name);
        sectionParents[id] = parent;
        sectionDepths[id] = parent == ROOT ? 0 : sectionDepths[parent] + 1;
        return id;
    }

    /**
     * The id of the frame value called {@code name}, registering it the first time.
     *
     * @throws IllegalStateException when {@link #MAX_VALUES} values exist already
     */
    public int value(String name) {
        Objects.requireNonNull(name, "name cannot be null");
        int existing = valueNames.indexOf(name);
        if (existing >= 0) {
            return existing;
        }
        if (valueNames.size() == MAX_VALUES) {
            throw new IllegalStateException("The profiler holds at most " + MAX_VALUES + " values; cannot add " + name);
        }
        valueNames.add(name);
        return valueNames.size() - 1;
    }

    /**
     * Sets where per-section counts come from, or {@code null} for none. Frames already recorded lose their counts, since they were
     * counted with another source.
     */
    public void setCounterSource(CounterSource source) {
        if (inFrame) {
            throw new IllegalStateException("The counter source cannot change during a frame");
        }
        counters = source;
        if (source == null) {
            sectionCounts = null;
            countersAtBegin = null;
            return;
        }
        int size = source.size();
        if (size < 0) {
            throw new IllegalArgumentException("A counter source cannot have a negative size: " + size);
        }
        sectionCounts = new long[historySize][MAX_SECTIONS][size];
        countersAtBegin = new long[MAX_SECTIONS][size];
    }

    /** The counter source, or {@code null}. */
    public CounterSource getCounterSource() {
        return counters;
    }

    /** Starts or stops recording; stopping in a frame drops that frame. */
    public void setRecording(boolean recording) {
        this.recording = recording;
        if (!recording) {
            inFrame = false;
            lastFrameStart = -1L;
            Arrays.fill(open, false);
        }
    }

    public boolean isRecording() {
        return recording;
    }

    /** Pausing keeps the history as it is, to study one frame; resuming starts recording new frames again. */
    public void setPaused(boolean paused) {
        this.paused = paused;
        if (paused) {
            inFrame = false;
            lastFrameStart = -1L;
            Arrays.fill(open, false);
        }
    }

    public boolean isPaused() {
        return paused;
    }

    /** True between {@link #beginFrame()} and {@link #endFrame()} of a frame that is being recorded. */
    public boolean isInFrame() {
        return inFrame;
    }

    /** Starts a frame; does nothing unless recording and not paused. A frame left open is dropped. */
    public void beginFrame() {
        if (!recording || paused) {
            return;
        }
        long now = clock.getAsLong();
        if (!inFrame) {
            // The newest finished frame's period ends now; an unfinished frame's slot is reused instead
            if (lastFrameStart >= 0 && stored > 0) {
                periodNanos[slot] = now - lastFrameStart;
            }
            slot = (slot + 1) % historySize;
        }
        Arrays.fill(sectionNanos[slot], 0L);
        Arrays.fill(sectionCalls[slot], 0);
        Arrays.fill(values[slot], 0L);
        if (sectionCounts != null) {
            for (long[] counts : sectionCounts[slot]) {
                Arrays.fill(counts, 0L);
            }
        }
        Arrays.fill(open, false);
        periodNanos[slot] = 0L;
        lastFrameStart = now;
        frameStart = now;
        inFrame = true;
        if (counters != null) {
            counters.frameStarted();
        }
    }

    /** Opens a section in the current frame. Opening one that is open restarts it. */
    public void begin(int section) {
        if (!inFrame) {
            return;
        }
        checkSection(section);
        open[section] = true;
        if (counters != null) {
            long[] start = countersAtBegin[section];
            for (int c = 0; c < start.length; c++) {
                start[c] = counters.read(c);
            }
        }
        openSince[section] = clock.getAsLong();
    }

    /** Closes a section and adds its time (and counter growth) to the frame; a section that is not open is ignored. */
    public void end(int section) {
        if (!inFrame) {
            return;
        }
        long now = clock.getAsLong();
        checkSection(section);
        if (!open[section]) {
            return;
        }
        open[section] = false;
        sectionNanos[slot][section] += now - openSince[section];
        sectionCalls[slot][section]++;
        if (counters != null) {
            long[] start = countersAtBegin[section];
            long[] counts = sectionCounts[slot][section];
            for (int c = 0; c < start.length; c++) {
                counts[c] += counters.read(c) - start[c];
            }
        }
    }

    /** Sets a frame value for the current frame. */
    public void set(int value, long amount) {
        if (!inFrame) {
            return;
        }
        checkValue(value);
        values[slot][value] = amount;
    }

    /** Adds to a frame value for the current frame. */
    public void add(int value, long amount) {
        if (!inFrame) {
            return;
        }
        checkValue(value);
        values[slot][value] += amount;
    }

    /** Ends the frame and keeps it in the history. Sections still open are dropped. */
    public void endFrame() {
        if (!inFrame) {
            return;
        }
        frameNanos[slot] = clock.getAsLong() - frameStart;
        inFrame = false;
        Arrays.fill(open, false);
        stored = Math.min(stored + 1, historySize);
        recordedFrames++;
    }

    /** Clears the history; the sections and values stay registered. */
    public void clear() {
        stored = 0;
        slot = -1;
        inFrame = false;
        lastFrameStart = -1L;
        Arrays.fill(open, false);
    }

    public int getHistorySize() {
        return historySize;
    }

    /**
     * Finished frames in the history, at most {@link #getHistorySize()}. During a frame the one being recorded has taken the oldest
     * frame's place, so a full history then holds one fewer.
     */
    public int getFrameCount() {
        return inFrame && stored == historySize ? historySize - 1 : stored;
    }

    /** Frames recorded since the profiler was made, including those that dropped out of the history. */
    public long getRecordedFrames() {
        return recordedFrames;
    }

    public int getSectionCount() {
        return sectionNames.size();
    }

    public String getSectionName(int section) {
        checkSection(section);
        return sectionNames.get(section);
    }

    /** The section's parent, or {@link #ROOT}. */
    public int getSectionParent(int section) {
        checkSection(section);
        return sectionParents[section];
    }

    /** 0 for a top-level section. */
    public int getSectionDepth(int section) {
        checkSection(section);
        return sectionDepths[section];
    }

    public int getValueCount() {
        return valueNames.size();
    }

    public String getValueName(int value) {
        checkValue(value);
        return valueNames.get(value);
    }

    /** Counters per section: the counter source's size, 0 without one. */
    public int getCounterCount() {
        return counters == null ? 0 : counters.size();
    }

    public String getCounterName(int counter) {
        if (counters == null || counter < 0 || counter >= counters.size()) {
            throw new IllegalArgumentException("No counter " + counter);
        }
        return counters.name(counter);
    }

    /** From {@code beginFrame} to {@code endFrame}: the work the frame loop measured. {@code framesAgo} 0 is the newest frame. */
    public long getFrameNanos(int framesAgo) {
        return frameNanos[slotOf(framesAgo)];
    }

    /**
     * From this frame's start to the next recorded frame's start: the frame's period, with its waiting for vsync and the GPU. Known
     * once the next frame begins, so during a frame (when a panel draws) every finished frame has one; 0 while not known, and for a
     * frame after which recording paused or stopped.
     */
    public long getPeriodNanos(int framesAgo) {
        return periodNanos[slotOf(framesAgo)];
    }

    public long getSectionNanos(int framesAgo, int section) {
        checkSection(section);
        return sectionNanos[slotOf(framesAgo)][section];
    }

    /** How often the section was timed in that frame. */
    public int getSectionCalls(int framesAgo, int section) {
        checkSection(section);
        return sectionCalls[slotOf(framesAgo)][section];
    }

    /** How much a counter grew while the section was open in that frame. */
    public long getSectionCount(int framesAgo, int section, int counter) {
        checkSection(section);
        if (sectionCounts == null || counter < 0 || counter >= counters.size()) {
            throw new IllegalArgumentException("No counter " + counter);
        }
        return sectionCounts[slotOf(framesAgo)][section][counter];
    }

    public long getValue(int framesAgo, int value) {
        checkValue(value);
        return values[slotOf(framesAgo)][value];
    }

    private int slotOf(int framesAgo) {
        int available = getFrameCount();
        if (framesAgo < 0 || framesAgo >= available) {
            throw new IllegalArgumentException("No frame " + framesAgo + " frames ago; " + available + " in the history");
        }
        // Between frames the newest is at slot; during a frame the newest finished one is just before it
        int newest = inFrame ? slot - 1 : slot;
        return Math.floorMod(newest - framesAgo, historySize);
    }

    private void checkSection(int section) {
        if (section < 0 || section >= sectionNames.size()) {
            throw new IllegalArgumentException("No section " + section);
        }
    }

    private void checkValue(int value) {
        if (value < 0 || value >= valueNames.size()) {
            throw new IllegalArgumentException("No value " + value);
        }
    }
}
