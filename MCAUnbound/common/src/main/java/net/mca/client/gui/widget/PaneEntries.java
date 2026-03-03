package net.mca.client.gui.widget;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Factory methods for common {@link ScrollPane.PaneEntry} types.
 *
 * <p>All widths passed to addWidgets() account for the scroll-bar margin
 * (callers should subtract ~8 px for padding if they want flush buttons).
 */
public final class PaneEntries {

    private PaneEntries() {}

    // ── Spacer ────────────────────────────────────────────────────────────────
    public static ScrollPane.PaneEntry spacer(int pixels) {
        return new ScrollPane.PaneEntry() {
            @Override public int height() { return pixels; }
            @Override public void render(DrawContext ctx, TextRenderer tr, int x, int y, int w, int mx, int my) {}
        };
    }

    // ── Section label ─────────────────────────────────────────────────────────
    public static ScrollPane.PaneEntry label(String text, int bgColor) {
        return new ScrollPane.PaneEntry() {
            @Override public int height() { return 14; }
            @Override public void render(DrawContext ctx, TextRenderer tr, int x, int y, int w, int mx, int my) {
                ctx.fill(x, y, x + w, y + 13, bgColor);
                ctx.drawTextWithShadow(tr, text, x + 4, y + 2, 0xFFFFFF);
            }
        };
    }

    // ── Info row (key: value) ─────────────────────────────────────────────────
    public static ScrollPane.PaneEntry infoRow(String key, String value, int valueColor) {
        return new ScrollPane.PaneEntry() {
            @Override public int height() { return 12; }
            @Override public void render(DrawContext ctx, TextRenderer tr, int x, int y, int w, int mx, int my) {
                ctx.drawTextWithShadow(tr, "§7" + key + ": ", x + 4, y + 1, 0xFFFFFF);
                ctx.drawTextWithShadow(tr, value, x + 4 + tr.getWidth("§7" + key + ": "), y + 1, valueColor);
            }
        };
    }

    // ── Button row ────────────────────────────────────────────────────────────
    public static ScrollPane.PaneEntry buttonRow(String label, String tooltip, Runnable onClick) {
        return buttonRow(label, tooltip, onClick, false);
    }

    public static ScrollPane.PaneEntry buttonRow(String label, String tooltip, Runnable onClick, boolean disabled) {
        return new ScrollPane.PaneEntry() {
            @Override public int height() { return 20; }

            @Override
            public void render(DrawContext ctx, TextRenderer tr, int x, int y, int w, int mx, int my) {
                // The button widget handles its own rendering via addWidgets / screen
                // We draw a hover tint here for visual feedback
                boolean hovered = mx >= x && mx <= x + w - 8 && my >= y && my < y + 18;
                if (hovered && !disabled) ctx.fill(x, y, x + w - 8, y + 18, 0x22FFFFFF);
            }

            @Override
            public void addWidgets(Consumer<ClickableWidget> adder, int x, int y, int w) {
                ButtonWidget btn = ButtonWidget.builder(Text.literal(label), b -> {
                    if (!disabled) onClick.run();
                }).dimensions(x + 4, y + 1, w - 16, 18).build();
                btn.active = !disabled;
                adder.accept(btn);
            }
        };
    }

    // ── Grid of buttons (e.g. talk actions) ──────────────────────────────────
    /**
     * Lays out multiple buttons in a grid with {@code cols} columns.
     * Each button is {@code btnH} px tall.
     */
    public static ScrollPane.PaneEntry buttonGrid(List<ButtonSpec> specs, int cols, int btnH) {
        int rows = (int) Math.ceil((double) specs.size() / cols);
        int totalH = rows * (btnH + 4) + 4;

        return new ScrollPane.PaneEntry() {
            @Override public int height() { return totalH; }

            @Override
            public void render(DrawContext ctx, TextRenderer tr, int x, int y, int w, int mx, int my) {}

            @Override
            public void addWidgets(Consumer<ClickableWidget> adder, int x, int y, int w) {
                int btnW = (w - 8 - (cols - 1) * 4) / cols;
                for (int i = 0; i < specs.size(); i++) {
                    ButtonSpec spec = specs.get(i);
                    if (spec == null) continue;
                    int col = i % cols;
                    int row = i / cols;
                    int bx  = x + 4 + col * (btnW + 4);
                    int by  = y + 4 + row * (btnH + 4);
                    ButtonWidget btn = ButtonWidget.builder(Text.literal(spec.label()), b -> {
                        if (!spec.disabled()) spec.onClick().run();
                    }).dimensions(bx, by, btnW, btnH).build();
                    btn.active = !spec.disabled();
                    if (spec.disabled()) btn.setMessage(Text.literal("§7" + spec.label()));
                    adder.accept(btn);
                }
            }
        };
    }

    // ── ButtonSpec record ─────────────────────────────────────────────────────
    public record ButtonSpec(String label, Runnable onClick, boolean disabled) {
        public static ButtonSpec of(String label, Runnable onClick) {
            return new ButtonSpec(label, onClick, false);
        }
        public static ButtonSpec disabled(String label) {
            return new ButtonSpec(label, () -> {}, true);
        }
    }

    // ── Horizontal divider ────────────────────────────────────────────────────
    public static ScrollPane.PaneEntry divider() {
        return new ScrollPane.PaneEntry() {
            @Override public int height() { return 6; }
            @Override public void render(DrawContext ctx, TextRenderer tr, int x, int y, int w, int mx, int my) {
                ctx.fill(x + 4, y + 2, x + w - 4, y + 3, 0x55FFFFFF);
            }
        };
    }
}
