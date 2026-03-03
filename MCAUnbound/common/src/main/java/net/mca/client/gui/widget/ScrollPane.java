package net.mca.client.gui.widget;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.widget.ClickableWidget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A scrollable content pane that manages a list of child entries.
 *
 * <p>Children are defined via {@link PaneEntry} objects (text rows or buttons).
 * The pane clips to its bounds and handles mouse-scroll.
 */
public class ScrollPane implements Drawable {

    // ── Entry types ───────────────────────────────────────────────────────────

    public interface PaneEntry {
        /** Height this entry occupies. */
        int height();
        /** Draw the entry at the given absolute Y. */
        void render(DrawContext ctx, net.minecraft.client.font.TextRenderer tr,
                    int x, int y, int width, int mouseX, int mouseY);
        /** Optional: add clickable widgets to the parent screen. Called during init. */
        default void addWidgets(Consumer<ClickableWidget> adder, int x, int y, int width) {}
    }

    // ── State ─────────────────────────────────────────────────────────────────
    private final int x, y, width, height;
    private final List<PaneEntry> entries = new ArrayList<>();
    private int scrollOffset = 0;

    public ScrollPane(int x, int y, int width, int height) {
        this.x      = x;
        this.y      = y;
        this.width  = width;
        this.height = height;
    }

    public ScrollPane add(PaneEntry entry) {
        entries.add(entry);
        return this;
    }

    /** Add all entries' widgets to the parent screen at their rendered positions. */
    public void init(Consumer<ClickableWidget> adder) {
        if (adder == null) return;
        int ey = y - scrollOffset;
        for (PaneEntry entry : entries) {
            if (ey + entry.height() >= y && ey < y + height) {
                entry.addWidgets(adder, x, ey, width);
            }
            ey += entry.height();
        }
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        net.minecraft.client.font.TextRenderer tr = mc.textRenderer;

        // Scissor clip to pane bounds
        ctx.enableScissor(x, y, x + width, y + height);

        int ey = y - scrollOffset;
        for (PaneEntry entry : entries) {
            if (ey + entry.height() >= y && ey < y + height) {
                entry.render(ctx, tr, x, ey, width, mouseX, mouseY);
            }
            ey += entry.height();
        }

        ctx.disableScissor();

        // Scroll bar (only if content overflows)
        int totalH = contentHeight();
        if (totalH > height) {
            int barH  = Math.max(16, (int) ((double) height / totalH * height));
            int barY  = y + (int) ((double) scrollOffset / (totalH - height) * (height - barH));
            ctx.fill(x + width - 4, y,    x + width, y + height, 0x33FFFFFF);
            ctx.fill(x + width - 4, barY, x + width, barY + barH, 0xAAFFFFFF);
        }
    }

    private int contentHeight() {
        return entries.stream().mapToInt(PaneEntry::height).sum();
    }

    // ── Events ────────────────────────────────────────────────────────────────

    public boolean mouseScrolled(double mx, double my, double amount) {
        if (mx < x || mx > x + width || my < y || my > y + height) return false;
        int maxScroll = Math.max(0, contentHeight() - height);
        scrollOffset = (int) Math.max(0, Math.min(scrollOffset - amount * 10, maxScroll));
        return true;
    }

    public boolean mouseClicked(double mx, double my, int button) {
        return false; // buttons handle their own clicks via ClickableWidget
    }

    public void resetScroll() { scrollOffset = 0; }
}
