package io.github.ldev22live.gdxunity.engine.vector;

import java.util.Locale;

/**
 * A straight (not premultiplied) colour, each channel from 0 to 1.
 *
 * @param r red
 * @param g green
 * @param b blue
 * @param a alpha (opacity)
 */
public record Rgba(float r, float g, float b, float a) {
    public static final Rgba BLACK = new Rgba(0f, 0f, 0f, 1f);

    public Rgba {
        for (float channel : new float[] {r, g, b, a}) {
            if (!(channel >= 0f && channel <= 1f)) {
                throw new IllegalArgumentException("Colour channels must be 0 to 1: " + r + ", " + g + ", " + b + ", " + a);
            }
        }
    }

    /** Parses {@code #rgb}, {@code #rrggbb} or {@code #rrggbbaa}. */
    public static Rgba hex(String text) {
        if (text == null || !text.startsWith("#")) {
            throw new IllegalArgumentException("Not a hex colour: " + text);
        }
        String digits = text.substring(1);
        if (digits.length() == 3) {
            digits = "" + digits.charAt(0) + digits.charAt(0) + digits.charAt(1) + digits.charAt(1) + digits.charAt(2)
                + digits.charAt(2);
        }
        if (digits.length() == 6) {
            digits += "ff";
        }
        if (digits.length() != 8) {
            throw new IllegalArgumentException("Not a hex colour: " + text);
        }
        try {
            long value = Long.parseLong(digits, 16);
            return new Rgba(((value >> 24) & 0xff) / 255f, ((value >> 16) & 0xff) / 255f, ((value >> 8) & 0xff) / 255f,
                (value & 0xff) / 255f);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Not a hex colour: " + text, e);
        }
    }

    /** {@code #rrggbb}, or {@code #rrggbbaa} when not opaque. */
    public String toHex() {
        String rgb = String.format(Locale.ROOT, "#%02x%02x%02x", channel(r), channel(g), channel(b));
        return a >= 1f ? rgb : rgb + String.format(Locale.ROOT, "%02x", channel(a));
    }

    /** This colour with its alpha multiplied by {@code opacity}. */
    public Rgba withOpacity(float opacity) {
        return new Rgba(r, g, b, a * Math.max(0f, Math.min(1f, opacity)));
    }

    private static int channel(float value) {
        return Math.round(value * 255f);
    }
}
