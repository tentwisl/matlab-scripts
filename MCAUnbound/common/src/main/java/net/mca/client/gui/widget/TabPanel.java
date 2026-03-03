package net.mca.client.gui.widget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A lightweight tab-strip + content area widget.
 *
 * <p>Usage:
 * <pre>
 *   TabPanel tabs = new TabPanel(x, y, width, height, tabH);
 *   tabs.addTab("Talk",    "#FFDD44", this::buildTalkContent);
 *   tabs.addTab("Actions", "#88CCFF", this::buildActionsContent);
 *   tabs.addTab("Profile", "#AAFFAA", this::buildProfileContent);
 *   tabs.init(screen::addDrawableChild);
 * </pre>
 */
public class TabPanel implements Drawable {

    // ── Tab descriptor ────────────────────────────────────────────────────────
    public record Tab(String label, int accentColor, Consumer<ScrollPane> builder) {}

    // ── State ─────────────────────────────────────────────────────────────────
    private final int x, y, width, height, tabH;
    private final List<Tab> tabs = new ArrayList<>();
    private int activeTab = 0;

    /** Consumer registered so we can add child widgets to the parent screen. */
    private Consumer<ClickableWidget> adder;

    /** ScrollPane for the active tab's content. */
    private ScrollPane contentPane;

    // ── Tab button references ─────────────────────────────────────────────────
    private final List<ClickableWidget> tabButtons = new ArrayList<>();

    public TabPanel(int x, int y, int width, int height, int tabH) {
        this.x      = x;
        this.y      = y;
        this.width  = width;
        this.height = height;
        this.tabH   = tabH;
    }

    public TabPanel addTab(String label, int accentColor, Consumer<ScrollPane> builder) {
        tabs.add(new Tab(label, accentColor, builder));
        return this;
    }

    /** Must be called after addTab() calls; pass screen's addDrawableChild method. */
    public void init(Consumer<ClickableWidget> widgetAdder) {
        this.adder = widgetAdder;
        rebuild();
    }

    /** Called when active tab changes or content needs refresh. */
    private void rebuild() {
        // Remove old tab buttons and content pane from parent (caller must clear first)
        tabButtons.clear();

        int tabW = tabs.isEmpty() ? 0 : width / tabs.size();

        for (int i = 0; i < tabs.size(); i++) {
            final int idx = i;
            Tab tab = tabs.get(i);

            ClickableWidget btn = ButtonWidget.builder(Text.literal(tab.label()), b -> {
                activeTab = idx;
                rebuildContent();
            }).dimensions(x + i * tabW, y, tabW - 1, tabH).build();

            tabButtons.add(btn);
            if (adder != null) adder.accept(btn);
        }

        rebuildContent();
    }

    private void rebuildContent() {
        int contentY = y + tabH;
        int contentH = height - tabH;
        contentPane = new ScrollPane(x, contentY, width, contentH);
        if (activeTab < tabs.size()) {
            tabs.get(activeTab).builder().accept(contentPane);
        }
        contentPane.init(adder);
    }

    // ── Drawable ──────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Panel background
        ctx.fill(x, y, x + width, y + height, 0xAA000000);

        // Tab strip background
        ctx.fill(x, y, x + width, y + tabH, 0xCC111111);

        // Active tab highlight
        if (activeTab < tabs.size()) {
            int tabW = width / tabs.size();
            int accent = tabs.get(activeTab).accentColor();
            ctx.fill(x + activeTab * tabW, y + tabH - 2, x + (activeTab + 1) * tabW - 1, y + tabH, accent);
        }

        // Separator line below tabs
        ctx.fill(x, y + tabH - 1, x + width, y + tabH, 0xFF444444);

        // Content
        if (contentPane != null) contentPane.render(ctx, mouseX, mouseY, delta);
    }

    // ── Event forwarding ──────────────────────────────────────────────────────

    public boolean mouseScrolled(double mx, double my, double amount) {
        if (contentPane != null) return contentPane.mouseScrolled(mx, my, amount);
        return false;
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (contentPane != null) return contentPane.mouseClicked(mx, my, button);
        return false;
    }

    public void setActiveTab(int idx) {
        if (idx >= 0 && idx < tabs.size()) {
            activeTab = idx;
            rebuildContent();
        }
    }

    public ScrollPane getContentPane() { return contentPane; }
}
