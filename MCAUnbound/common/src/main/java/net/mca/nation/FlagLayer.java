package net.mca.nation;

import net.minecraft.nbt.NbtCompound;

import java.io.Serial;
import java.io.Serializable;

/**
 * One rendering layer of a nation flag.
 *
 * A flag is built from 1–4 stacked layers (bottom to top).
 * Each layer has a pattern that is drawn in the layer's color over the previous layers.
 *
 * Patterns are drawn client-side using simple DrawContext.fill() calls.
 */
public class FlagLayer implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** Available flag patterns. Each is rendered with DrawContext rects on the client. */
    public enum Pattern {
        /** Solid fill — whole flag area in this color. */
        SOLID,
        /** Horizontal stripe through the vertical centre. */
        STRIPE_H,
        /** Vertical stripe through the horizontal centre. */
        STRIPE_V,
        /** Cross: STRIPE_H + STRIPE_V combined. */
        CROSS,
        /** Top-left triangle (upper-left half of flag). */
        TRIANGLE_TOP_LEFT,
        /** Bottom half of the flag. */
        HALF_BOTTOM,
        /** Right half of the flag. */
        HALF_RIGHT,
        /** Thin border around the flag edge. */
        BORDER,
        /** Central diamond (rotated square). */
        DIAMOND,
        /** Chevron pointing up from the bottom. */
        CHEVRON_UP
    }

    private Pattern pattern;
    /** 0xRRGGBB hex string, e.g. "FF4400". */
    private String colorHex;

    public FlagLayer(Pattern pattern, String colorHex) {
        this.pattern  = pattern;
        this.colorHex = colorHex;
    }

    public FlagLayer(NbtCompound nbt) {
        try {
            this.pattern = Pattern.valueOf(nbt.getString("pattern"));
        } catch (IllegalArgumentException e) {
            this.pattern = Pattern.SOLID;
        }
        this.colorHex = nbt.getString("colorHex");
        if (this.colorHex == null || this.colorHex.isEmpty()) this.colorHex = "FFFFFF";
    }

    public NbtCompound save() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("pattern",  pattern.name());
        nbt.putString("colorHex", colorHex);
        return nbt;
    }

    public Pattern getPattern()  { return pattern; }
    public String  getColorHex() { return colorHex; }

    /** Return this layer's color as a packed 0xFFRRGGBB ARGB int for rendering. */
    public int getArgb() {
        try {
            int rgb = Integer.parseInt(colorHex, 16);
            return 0xFF000000 | rgb;
        } catch (NumberFormatException e) {
            return 0xFFFFFFFF;
        }
    }
}
