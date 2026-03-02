package net.mca.client.gui;

import net.mca.cobalt.network.NetworkHandler;
import net.mca.nation.Nation;
import net.mca.nation.NationStats;
import net.mca.network.c2s.GetNationsDebugRequest;
import net.mca.network.s2c.NationsDebugResponse;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Nations Debug Screen — accessible via a debug key binding or command.
 * Shows all known nations with their full stats, relations, and recent events.
 *
 * Layout:
 * ┌─────────────────────────────────────────────────────────┐
 * │  [Nations Debug]           [Refresh]   [Close]          │
 * ├──────────────┬──────────────────────────────────────────┤
 * │ Nation List  │ Selected Nation Detail                   │
 * │ (scrollable) │ Name / Gov / Leader                      │
 * │              │ Stats: Pop / Military / Economy          │
 * │              │ Approval / PolCapital / Science          │
 * │              │ Districts / Cities                       │
 * │              │ Recent Events (last 10)                  │
 * └──────────────┴──────────────────────────────────────────┘
 */
public class NationDebugScreen extends ExtendedScreen {

    private final List<Nation> nations       = new ArrayList<>();
    private int selectedIndex                = 0;
    private int scrollOffset                 = 0;
    private boolean aiSeeded                 = false;
    private int totalNations                 = 0;

    // Panel geometry (set in init)
    private int listPanelWidth;
    private int detailPanelX;

    public NationDebugScreen() {
        super(Text.literal("[DEBUG] Nations Overview"));
    }

    @Override
    protected void init() {
        super.init();
        listPanelWidth = width / 3;
        detailPanelX   = listPanelWidth + 5;

        // Refresh button
        addDrawableChild(ButtonWidget.builder(Text.literal("Refresh"), btn -> refresh())
                .dimensions(width - 130, 4, 60, 16).build());

        // Close button
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), btn -> close())
                .dimensions(width - 65, 4, 60, 16).build());

        // Request initial data
        refresh();
    }

    private void refresh() {
        NetworkHandler.sendToServer(new GetNationsDebugRequest());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx);
        super.render(ctx, mouseX, mouseY, delta);

        // Title
        ctx.drawTextWithShadow(textRenderer, "[DEBUG] Nations Overview  — Count: " + totalNations + " | AI Seeded: " + aiSeeded,
                4, 6, 0xFFCC44);

        // Divider
        ctx.fill(listPanelWidth, 25, listPanelWidth + 1, height - 5, 0xFF666666);
        ctx.fill(0, 25, width, 26, 0xFF666666);

        renderNationList(ctx, mouseX, mouseY);
        renderNationDetail(ctx);
    }

    private void renderNationList(DrawContext ctx, int mouseX, int mouseY) {
        int y = 30;
        for (int i = scrollOffset; i < nations.size() && y < height - 10; i++) {
            Nation n = nations.get(i);
            boolean selected = (i == selectedIndex);
            int bg = selected ? 0x44FFFFFF : (i % 2 == 0 ? 0x22FFFFFF : 0x11FFFFFF);
            ctx.fill(2, y - 1, listPanelWidth - 2, y + 9, bg);

            // Clickable area — detect click in mousePressed instead (no lambda capture for i here)
            int color = n.isAiControlled() ? 0xAADDFF : 0xFFFFAA;
            ctx.drawTextWithShadow(textRenderer,
                    (i + 1) + ". " + n.getName() + " [" + n.getGovernmentType().name().charAt(0) + "]",
                    4, y, color);
            y += 11;
        }
    }

    private void renderNationDetail(DrawContext ctx) {
        if (nations.isEmpty() || selectedIndex >= nations.size()) {
            ctx.drawCenteredTextWithShadow(textRenderer, "No nation selected.", detailPanelX + (width - detailPanelX) / 2, 50, 0x888888);
            return;
        }
        Nation n = nations.get(selectedIndex);
        NationStats s = n.getStats();

        int x = detailPanelX + 4;
        int y = 30;
        int lineH = 11;

        ctx.drawTextWithShadow(textRenderer, n.getName(), x, y, 0xFFFF55); y += lineH + 2;
        ctx.drawTextWithShadow(textRenderer, "Gov: " + n.getGovernmentType().getDisplayName() + " | Personality: " + n.getPersonality().name(), x, y, 0xCCCCCC); y += lineH;
        ctx.drawTextWithShadow(textRenderer, "AI: " + n.isAiControlled() + " | Puppet: " + n.isPuppet() + " | Founded Day: " + n.getFoundedDay(), x, y, 0xAAAAAA); y += lineH;

        y += 4;
        ctx.drawTextWithShadow(textRenderer, "── Stats ──", x, y, 0xFFCC44); y += lineH;
        ctx.drawTextWithShadow(textRenderer, "Population:    " + s.getTotalPopulation(), x, y, 0xCCCCCC); y += lineH;
        ctx.drawTextWithShadow(textRenderer, "Military:      " + s.getMilitaryStrength(), x, y, 0xCCCCCC); y += lineH;
        ctx.drawTextWithShadow(textRenderer, "Economy:       " + s.getEconomicOutput(), x, y, 0xCCCCCC); y += lineH;
        ctx.drawTextWithShadow(textRenderer, "Science Lv:    " + s.getScienceLevel(), x, y, 0xCCCCCC); y += lineH;
        ctx.drawTextWithShadow(textRenderer, "Approval:      " + s.getApprovalRating() + "%", x, y, s.getApprovalRating() < 30 ? 0xFF4444 : 0xCCCCCC); y += lineH;
        ctx.drawTextWithShadow(textRenderer, "Pol. Capital:  " + s.getPoliticalCapital(), x, y, 0xCCCCCC); y += lineH;

        y += 4;
        ctx.drawTextWithShadow(textRenderer, "── Heir ──", x, y, 0xFFCC44); y += lineH;
        ctx.drawTextWithShadow(textRenderer,
                "Has Heir: " + n.getHeir().hasHeir() + " | Active: " + n.getHeir().isActive(),
                x, y, 0xCCCCCC); y += lineH;

        y += 4;
        ctx.drawTextWithShadow(textRenderer, "── Recent Events ──", x, y, 0xFFCC44); y += lineH;
        List<String> events = n.getRecentEvents();
        for (int i = 0; i < Math.min(8, events.size()); i++) {
            ctx.drawTextWithShadow(textRenderer, events.get(i), x, y, 0x999999); y += lineH;
        }
        if (events.isEmpty()) {
            ctx.drawTextWithShadow(textRenderer, "(no events yet)", x, y, 0x666666);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Handle clicking on nation list
        if (mouseX < listPanelWidth && mouseY > 25) {
            int idx = (int)((mouseY - 30) / 11) + scrollOffset;
            if (idx >= 0 && idx < nations.size()) {
                selectedIndex = idx;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (mouseX < listPanelWidth) {
            scrollOffset = Math.max(0, Math.min(nations.size() - 1, scrollOffset - (int)amount));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    public void loadData(NationsDebugResponse response) {
        NbtCompound root = response.getData();
        aiSeeded    = root.getBoolean("aiSeeded");
        totalNations = root.getInt("nationCount");
        nations.clear();
        NbtList nList = root.getList("nations", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < nList.size(); i++) nations.add(new Nation(nList.getCompound(i)));
        if (selectedIndex >= nations.size()) selectedIndex = 0;
    }

    @Override
    public boolean shouldPause() { return false; }
}
