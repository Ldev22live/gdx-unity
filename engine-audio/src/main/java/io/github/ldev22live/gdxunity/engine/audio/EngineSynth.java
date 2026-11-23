package io.github.ldev22live.gdxunity.engine.audio;

import java.util.Objects;
import java.util.SplittableRandom;

/**
 * A synthesized combustion engine that runs for as long as it is rendered, for car sounds that follow the car (TD-080).
 * The game sets the crankshaft speed and the throttle ({@link #setRpm}, {@link #setThrottle}); {@link #render} makes the sound.
 *
 * <p>How it is made, from the cylinders outwards:
 * <ul>
 *   <li>every cylinder firing is a short, sharp pressure pulse; they come {@link EngineProfile#firingHz} times a second, and
 *       each cylinder's pulse is a little louder or softer than the next one's, which is what gives a V8 its burble;</li>
 *   <li>the pulse train goes through the exhaust, a low-pass filter that opens with throttle and revs, plus a fixed pipe
 *       resonance, so the engine is dull and quiet when the driver lifts off and bright and loud under load;</li>
 *   <li>intake roar (filtered noise that grows with throttle and revs), a faint tone at the crankshaft's own speed, a
 *       rev limiter that cuts the engine in and out at the redline and exhaust crackle on the overrun;</li>
 *   <li>a soft clipper adds grit when the engine is loaded.</li>
 * </ul>
 *
 * <p>The setters may be called from the game thread while another thread calls {@link #render}; they share only two
 * volatile fields, and the sound follows with a short lag (the engine's inertia), so fast changes never click. Nothing here
 * has been listened to by the developers who wrote it: the tests check pitch, loudness and range, not taste.
 */
public final class EngineSynth {
    /** Output sample rate, Hz. */
    public static final int SAMPLE_RATE = SfxrGenerator.SAMPLE_RATE;

    /** How quickly the sounding rpm follows the requested rpm, seconds (the flywheel's inertia). */
    private static final float RPM_RESPONSE = 0.035f;
    /** How quickly the sounding throttle follows the requested throttle, seconds. */
    private static final float THROTTLE_RESPONSE = 0.06f;
    /** Filter settings are recomputed this often, in samples. */
    private static final int CONTROL_PERIOD = 64;
    /** Share of a firing's pulse over which it rises, so the pulse does not click. */
    private static final float PULSE_ONSET = 40f;
    /** Pulse decay (per firing period) with the throttle shut and wide open: closed throttle gives a duller, shorter thump. */
    private static final float PULSE_DECAY_CLOSED = 9f;
    private static final float PULSE_DECAY_OPEN = 4.5f;
    /** Exhaust filter cutoff, Hz, at the lowest throttle and the largest throttle, before the profile's brightness. */
    private static final float EXHAUST_CUTOFF_CLOSED = 420f;
    private static final float EXHAUST_CUTOFF_OPEN = 1900f;
    /** Extra exhaust cutoff, Hz, per rpm. */
    private static final float EXHAUST_CUTOFF_PER_RPM = 0.18f;
    /** Fixed resonance of the pipes, Hz, its sharpness and how much of it is mixed in. */
    private static final float PIPE_RESONANCE_HZ = 210f;
    private static final float PIPE_Q = 3f;
    private static final float PIPE_MIX = 0.7f;
    /** Intake roar: centre frequency at idle, extra Hz per rpm and its loudness at full throttle and redline. */
    private static final float INTAKE_CENTRE_HZ = 700f;
    private static final float INTAKE_HZ_PER_RPM = 0.3f;
    private static final float INTAKE_LEVEL = 0.35f;
    /** Loudness of the tone at the crankshaft's own speed. */
    private static final float CRANK_LEVEL = 0.07f;
    /** The rev limiter cuts in this close to the redline (share of it), flutters this often and drops to this level. */
    private static final float LIMITER_START = 0.985f;
    private static final float LIMITER_HZ = 13f;
    private static final float LIMITER_FLOOR = 0.2f;
    /** Exhaust crackle: only below this throttle and above this share of the redline; pops per second at crackle 1, and decay. */
    private static final float CRACKLE_MAX_THROTTLE = 0.15f;
    private static final float CRACKLE_MIN_REV_SHARE = 0.35f;
    private static final float CRACKLE_RATE = 14f;
    private static final float CRACKLE_DECAY_SECONDS = 0.022f;
    private static final float CRACKLE_LEVEL = 1.1f;
    /** Overall loudness at closed throttle (a share of the loaded level) and the soft clipper's extra drive at full throttle. */
    private static final float CLOSED_THROTTLE_LEVEL = 0.5f;
    private static final float LOAD_DRIVE = 1.6f;
    /** Output gain before the final clamp. */
    private static final float OUTPUT_LEVEL = 0.8f;
    /** Pole of the DC blocker (about 10 Hz at 44.1 kHz). */
    private static final float DC_POLE = 0.9986f;

    private final EngineProfile profile;
    private final SplittableRandom random;
    private final float[] cylinderBias;
    private final BiquadFilter exhaust = new BiquadFilter(SAMPLE_RATE);
    private final BiquadFilter pipe = new BiquadFilter(SAMPLE_RATE);
    private final BiquadFilter intake = new BiquadFilter(SAMPLE_RATE);
    private final BiquadFilter crackleFilter = new BiquadFilter(SAMPLE_RATE);
    private final float rpmCoefficient = smoothing(RPM_RESPONSE);
    private final float throttleCoefficient = smoothing(THROTTLE_RESPONSE);
    private final float crackleDecay = (float) Math.exp(-1.0 / (CRACKLE_DECAY_SECONDS * SAMPLE_RATE));

    private volatile float requestedRpm;
    private volatile float requestedThrottle;
    private float rpm;
    private float throttle;
    private float firePhase;
    private int cylinder;
    private float fireAmplitude = 1f;
    private float crankPhase;
    private float limiterPhase;
    private float limiterGate = 1f;
    private float crackleEnvelope;
    private float dcIn;
    private float dcOut;

    /**
     * @param profile the engine to sound like
     * @param seed    fixes the cylinders' unevenness and the noise, so the same engine sounds the same every time
     */
    public EngineSynth(EngineProfile profile, long seed) {
        this.profile = Objects.requireNonNull(profile, "profile cannot be null");
        this.random = new SplittableRandom(seed);
        this.cylinderBias = new float[profile.cylinders()];
        for (int i = 0; i < cylinderBias.length; i++) {
            cylinderBias[i] = (float) ((random.nextDouble() - 0.5) * 2.0 * profile.irregularity() * 0.6);
        }
        requestedRpm = profile.idleRpm();
        rpm = profile.idleRpm();
        crackleFilter.set(BiquadFilter.Type.LOW_PASS, 1800f, 0.8f);
        updateFilters();
    }

    public EngineProfile getProfile() {
        return profile;
    }

    /** The crankshaft speed the engine is sounding at now (it follows {@link #setRpm} with a short lag). */
    public float getRpm() {
        return rpm;
    }

    /**
     * Sets the crankshaft speed to sound at.
     *
     * @param rpm revolutions per minute; values outside the profile's idle to redline range are held at the nearest end
     * @throws IllegalArgumentException if {@code rpm} is not a number
     */
    public void setRpm(float rpm) {
        if (Float.isNaN(rpm)) {
            throw new IllegalArgumentException("rpm must be a number");
        }
        requestedRpm = Math.min(Math.max(rpm, profile.idleRpm()), profile.redlineRpm());
    }

    /**
     * Sets how hard the driver is pressing the pedal.
     *
     * @param throttle 0 (lifted off) to 1 (flat out); values outside are held at the nearest end
     * @throws IllegalArgumentException if {@code throttle} is not a number
     */
    public void setThrottle(float throttle) {
        if (Float.isNaN(throttle)) {
            throw new IllegalArgumentException("throttle must be a number");
        }
        requestedThrottle = Math.min(Math.max(throttle, 0f), 1f);
    }

    /**
     * Renders the next block, replacing what is in {@code out}.
     *
     * @param out    receives mono samples, each within -1 to 1
     * @param frames how many samples to make
     * @throws IllegalArgumentException if {@code frames} is negative or more than {@code out} holds
     */
    public void render(float[] out, int frames) {
        Objects.requireNonNull(out, "out cannot be null");
        if (frames < 0 || frames > out.length) {
            throw new IllegalArgumentException("frames must be within 0 to " + out.length + ": " + frames);
        }
        float targetRpm = requestedRpm;
        float targetThrottle = requestedThrottle;
        float redline = profile.redlineRpm();
        for (int i = 0; i < frames; i++) {
            rpm += (targetRpm - rpm) * rpmCoefficient;
            throttle += (targetThrottle - throttle) * throttleCoefficient;
            if (i % CONTROL_PERIOD == 0) {
                updateFilters();
            }
            float revShare = (rpm - profile.idleRpm()) / (redline - profile.idleRpm());

            firePhase += profile.firingHz(rpm) / SAMPLE_RATE;
            if (firePhase >= 1f) {
                firePhase -= 1f;
                cylinder = (cylinder + 1) % cylinderBias.length;
                float jitter = (float) ((random.nextDouble() - 0.5) * profile.irregularity() * 0.4);
                fireAmplitude = 1f + cylinderBias[cylinder] + jitter;
            }
            float decay = PULSE_DECAY_CLOSED + (PULSE_DECAY_OPEN - PULSE_DECAY_CLOSED) * throttle;
            float onset = Math.min(1f, firePhase * PULSE_ONSET);
            float pulse = onset * (float) Math.exp(-firePhase * decay) * fireAmplitude * (0.3f + 0.7f * throttle);

            float body = exhaust.process(pulse) + PIPE_MIX * pipe.process(pulse);
            float noise = (float) (random.nextDouble() * 2.0 - 1.0);
            float roar = intake.process(noise) * INTAKE_LEVEL * throttle * (0.15f + 0.85f * revShare);

            crankPhase += rpm / 60f / SAMPLE_RATE;
            crankPhase -= (float) Math.floor(crankPhase);
            float crank = (float) Math.sin(2.0 * Math.PI * crankPhase) * CRANK_LEVEL;

            float crackle = 0f;
            if (profile.crackle() > 0f && throttle < CRACKLE_MAX_THROTTLE && revShare > CRACKLE_MIN_REV_SHARE
                && random.nextDouble() < profile.crackle() * CRACKLE_RATE / SAMPLE_RATE) {
                crackleEnvelope = 1f;
            }
            if (crackleEnvelope > 1e-4f) {
                crackle = crackleFilter.process(noise) * crackleEnvelope * CRACKLE_LEVEL;
                crackleEnvelope *= crackleDecay;
            }

            float gateTarget = 1f;
            if (rpm >= redline * LIMITER_START && throttle > 0.5f) {
                limiterPhase += LIMITER_HZ / SAMPLE_RATE;
                limiterPhase -= (float) Math.floor(limiterPhase);
                gateTarget = limiterPhase < 0.5f ? 1f : LIMITER_FLOOR;
            }
            limiterGate += (gateTarget - limiterGate) * 0.01f;

            float loudness = CLOSED_THROTTLE_LEVEL + (1f - CLOSED_THROTTLE_LEVEL) * throttle;
            float raw = (body + roar + crank) * limiterGate * loudness + crackle;
            float drive = 1f + LOAD_DRIVE * throttle;
            float shaped = (float) Math.tanh(raw * drive) / drive;
            // DC blocker: the pulse train is all one sign, which would otherwise show up as a steady offset
            dcOut = shaped - dcIn + DC_POLE * dcOut;
            dcIn = shaped;
            out[i] = Math.min(Math.max(dcOut * OUTPUT_LEVEL, -1f), 1f);
        }
    }

    private void updateFilters() {
        float cutoff = (EXHAUST_CUTOFF_CLOSED + (EXHAUST_CUTOFF_OPEN - EXHAUST_CUTOFF_CLOSED) * throttle
            + EXHAUST_CUTOFF_PER_RPM * rpm) * profile.brightness();
        exhaust.set(BiquadFilter.Type.LOW_PASS, cutoff, 0.9f);
        pipe.set(BiquadFilter.Type.BAND_PASS, PIPE_RESONANCE_HZ + 0.03f * rpm, PIPE_Q);
        intake.set(BiquadFilter.Type.BAND_PASS, INTAKE_CENTRE_HZ + INTAKE_HZ_PER_RPM * rpm, 0.9f);
    }

    /** One-pole smoothing coefficient per sample for a response time in seconds. */
    private static float smoothing(float seconds) {
        return (float) (1.0 - Math.exp(-1.0 / (seconds * SAMPLE_RATE)));
    }
}
