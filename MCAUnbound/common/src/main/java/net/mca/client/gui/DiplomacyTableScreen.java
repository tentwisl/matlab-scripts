package net.mca.client.gui;

import net.mca.cobalt.network.NetworkHandler;
import net.mca.nation.*;
import net.mca.network.c2s.FoundNationPacket;
import net.mca.network.s2c.DiplomacyTableDataResponse;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Multi-tab GUI for the Diplomacy Table block.
 * Tabs: [Overview] [Diplomacy] [Congress] [Cabinet] [Found Nation]
 */
public class DiplomacyTableScreen extends ExtendedScreen {

    private static final int TAB_OVERVIEW  = 0;
    private static final int TAB_DIPLOMACY = 1;
    private static final int TAB_CONGRESS  = 2;
    private static final int TAB_CABINET   = 3;
    private static final int TAB_FOUND     = 4;

    private int activeTab = TAB_OVERVIEW;

    // Nation data received from server
    private Nation playerNation;
    private final List<Nation> allNations = new ArrayList<>();
    private final List<DiplomacyRelation> relations = new ArrayList<>();
    private boolean hasPlayerNation = false;

    // "Found Nation" tab widgets
    private TextFieldWidget nationNameField;
    private String selectedGov = GovernmentType.MONARCHY.name();
    private String flagColor = "4488CC";

    public DiplomacyTableScreen() {
        super(Text.translatable("gui.mca.diplomacy_table.title"));
    }

    @Override
    protected void init() {
        super.init();
        buildTabButtons();
        rebuildContent();
    }

    // ── Tab bar ───────────────────────────────────────────────────────────────

    private void buildTabButtons() {
        String[] tabLabels = { "Overview", "Diplomacy", "Congress", "Cabinet", "Found Nation" };
        int tabW = 90;
        int tabX = (width - tabW * tabLabels.length) / 2;
        for (int i = 0; i < tabLabels.length; i++) {
            final int tabIndex = i;
            addDrawableChild(ButtonWidget.builder(Text.literal(tabLabels[i]), btn -> {
                activeTab = tabIndex;
                clearAndRebuild();
            }).dimensions(tabX + tabW * i, 10, tabW - 2, 18).build());
        }
    }

    private void clearAndRebuild() {
        clearChildren();
        buildTabButtons();
        rebuildContent();
    }

    private void rebuildContent() {
        switch (activeTab) {
            case TAB_OVERVIEW  -> buildOverviewTab();
            case TAB_DIPLOMACY -> buildDiplomacyTab();
            case TAB_CONGRESS  -> buildCongressTab();
            case TAB_CABINET   -> buildCabinetTab();
            case TAB_FOUND     -> buildFoundTab();
        }
    }

    // ── Tab: Overview ─────────────────────────────────────────────────────────

    private void buildOverviewTab() {
        // Read-only display — content drawn in render()
    }

    // ── Tab: Diplomacy ────────────────────────────────────────────────────────

    private void buildDiplomacyTab() {
        // Buttons to propose treaty / declare war built dynamically when data loads
    }

    // ── Tab: Congress ─────────────────────────────────────────────────────────

    private void buildCongressTab() {
        if (!hasPlayerNation || playerNation == null) return;
        // Placeholder congress member list
    }

    // ── Tab: Cabinet ──────────────────────────────────────────────────────────

    private void buildCabinetTab() {
        if (!hasPlayerNation || playerNation == null) return;
        // Placeholder cabinet role list
    }

    // ── Tab: Found Nation ─────────────────────────────────────────────────────

    private void buildFoundTab() {
        if (hasPlayerNation) return; // already founded

        int cx = width / 2;
        int startY = 50;

        // Nation name field
        nationNameField = new TextFieldWidget(textRenderer, cx - 80, startY, 160, 20, Text.literal("Nation Name"));
        nationNameField.setMaxLength(32);
        nationNameField.setSuggestion("Enter nation name...");
        addDrawableChild(nationNameField);

        // Government type buttons
        GovernmentType[] govs = GovernmentType.values();
        int govBtnW = 80;
        int govStartX = cx - (govBtnW * govs.length) / 2;
        for (int i = 0; i < govs.length; i++) {
            GovernmentType gov = govs[i];
            addDrawableChild(ButtonWidget.builder(Text.literal(gov.getDisplayName()), btn -> {
                selectedGov = gov.name();
            }).dimensions(govStartX + govBtnW * i, startY + 30, govBtnW - 2, 18).build());
        }

        // Color presets (simple row of color buttons)
        String[] colors = { "4488CC", "CC4444", "44CC44", "CCAA22", "884499", "11AACC" };
        int colorBtnW = 30;
        int colorStartX = cx - (colorBtnW * colors.length) / 2;
        for (int i = 0; i < colors.length; i++) {
            final String color = colors[i];
            addDrawableChild(ButtonWidget.builder(Text.literal("  "), btn -> flagColor = color)
                    .dimensions(colorStartX + colorBtnW * i, startY + 60, colorBtnW - 2, 18).build());
        }

        // Submit button
        addDrawableChild(ButtonWidget.builder(Text.literal("Found Nation"), btn -> {
            String name = nationNameField != null ? nationNameField.getText().trim() : "";
            if (!name.isEmpty()) {
                NetworkHandler.sendToServer(new FoundNationPacket(name, selectedGov, flagColor));
                hasPlayerNation = true;
                clearAndRebuild();
            }
        }).dimensions(cx - 50, startY + 90, 100, 20).build());
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        super.render(context, mouseX, mouseY, delta);

        int cx = width / 2;
        int y  = 38;

        switch (activeTab) {
            case TAB_OVERVIEW  -> renderOverview(context, cx, y);
            case TAB_DIPLOMACY -> renderDiplomacy(context, cx, y);
            case TAB_CONGRESS  -> renderCongress(context, cx, y);
            case TAB_CABINET   -> renderCabinet(context, cx, y);
            case TAB_FOUND     -> renderFound(context, cx, y);
        }
    }

    private void renderOverview(DrawContext ctx, int cx, int y) {
        if (!hasPlayerNation || playerNation == null) {
            ctx.drawCenteredTextWithShadow(textRenderer, "No nation founded yet.", cx, y + 10, 0xAAAAAA);
            ctx.drawCenteredTextWithShadow(textRenderer, "Go to the 'Found Nation' tab.", cx, y + 22, 0x888888);
            return;
        }
        ctx.drawCenteredTextWithShadow(textRenderer, playerNation.getName(), cx, y, 0xFFFFAA);
        ctx.drawTextWithShadow(textRenderer, "Government: " + playerNation.getGovernmentType().getDisplayName(), cx - 100, y + 15, 0xCCCCCC);
        ctx.drawTextWithShadow(textRenderer, "Leader title: " + playerNation.getLeaderTitle(), cx - 100, y + 27, 0xCCCCCC);
        NationStats s = playerNation.getStats();
        ctx.drawTextWithShadow(textRenderer, "Population:  " + s.getTotalPopulation(), cx - 100, y + 39, 0xCCCCCC);
        ctx.drawTextWithShadow(textRenderer, "Military:    " + s.getMilitaryStrength(), cx - 100, y + 51, 0xCCCCCC);
        ctx.drawTextWithShadow(textRenderer, "Economy:     " + s.getEconomicOutput(), cx - 100, y + 63, 0xCCCCCC);
        ctx.drawTextWithShadow(textRenderer, "Science Lv:  " + s.getScienceLevel(), cx - 100, y + 75, 0xCCCCCC);
        ctx.drawTextWithShadow(textRenderer, "Approval:    " + s.getApprovalRating() + "%", cx - 100, y + 87, 0xCCCCCC);
        ctx.drawTextWithShadow(textRenderer, "Pol. Capital:" + s.getPoliticalCapital(), cx - 100, y + 99, 0xCCCCCC);

        // Recent events
        ctx.drawTextWithShadow(textRenderer, "-- Recent Events --", cx - 100, y + 118, 0xFFCC44);
        List<String> events = playerNation.getRecentEvents();
        for (int i = 0; i < Math.min(5, events.size()); i++) {
            ctx.drawTextWithShadow(textRenderer, events.get(i), cx - 100, y + 130 + i * 10, 0x999999);
        }
    }

    private void renderDiplomacy(DrawContext ctx, int cx, int y) {
        ctx.drawCenteredTextWithShadow(textRenderer, "Known Nations", cx, y, 0xFFFFAA);
        int listY = y + 15;
        for (Nation n : allNations) {
            if (playerNation != null && n.getNationId().equals(playerNation.getNationId())) continue;
            // Find relation status
            String status = "NEUTRAL";
            if (playerNation != null) {
                Optional<DiplomacyRelation> rel = relations.stream()
                        .filter(r -> (r.getNationA().equals(playerNation.getNationId()) && r.getNationB().equals(n.getNationId()))
                                  || (r.getNationB().equals(playerNation.getNationId()) && r.getNationA().equals(n.getNationId())))
                        .findFirst();
                if (rel.isPresent()) status = rel.get().getStatus().name();
            }
            ctx.drawTextWithShadow(textRenderer,
                    n.getName() + " [" + n.getGovernmentType().getDisplayName() + "] — " + status,
                    cx - 120, listY, 0xCCCCCC);
            listY += 12;
        }
    }

    private void renderCongress(DrawContext ctx, int cx, int y) {
        ctx.drawCenteredTextWithShadow(textRenderer, "Congress Members", cx, y, 0xFFFFAA);
        if (playerNation == null) return;
        int listY = y + 15;
        ctx.drawTextWithShadow(textRenderer,
                "Seats filled: " + playerNation.getCongress().size() + "/" + net.mca.nation.congress.CongressData.DEFAULT_SEATS,
                cx - 120, listY, 0xCCCCCC);
    }

    private void renderCabinet(DrawContext ctx, int cx, int y) {
        ctx.drawCenteredTextWithShadow(textRenderer, "Cabinet", cx, y, 0xFFFFAA);
        if (playerNation == null) return;
        int listY = y + 15;
        for (net.mca.nation.cabinet.CabinetRole role : net.mca.nation.cabinet.CabinetRole.values()) {
            boolean filled = playerNation.getCabinet().isFilled(role);
            ctx.drawTextWithShadow(textRenderer,
                    role.displayName + ": " + (filled ? "Appointed" : "Empty"),
                    cx - 120, listY, filled ? 0xAAFFAA : 0xFF8888);
            listY += 12;
        }
    }

    private void renderFound(DrawContext ctx, int cx, int y) {
        if (hasPlayerNation) {
            ctx.drawCenteredTextWithShadow(textRenderer, "You have already founded a nation.", cx, y + 10, 0xAAFFAA);
            return;
        }
        ctx.drawCenteredTextWithShadow(textRenderer, "Found a New Nation", cx, y, 0xFFFFAA);
        ctx.drawTextWithShadow(textRenderer, "Government:", cx - 80, y + 53, 0xCCCCCC);
        ctx.drawTextWithShadow(textRenderer, "Flag Color:", cx - 80, y + 83, 0xCCCCCC);
        // Selected government
        ctx.drawTextWithShadow(textRenderer, "Selected: " + selectedGov, cx + 40, y + 53, 0xFFCC44);
        ctx.drawTextWithShadow(textRenderer, "Selected: #" + flagColor, cx + 40, y + 83, 0xFFCC44);
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    public void loadData(DiplomacyTableDataResponse response) {
        NbtCompound root = response.getData();
        hasPlayerNation = root.getBoolean("hasPlayerNation");
        if (hasPlayerNation && root.contains("playerNation")) {
            playerNation = new Nation(root.getCompound("playerNation"));
        }
        allNations.clear();
        NbtList nList = root.getList("allNations", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < nList.size(); i++) allNations.add(new Nation(nList.getCompound(i)));

        relations.clear();
        NbtList rList = root.getList("relations", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < rList.size(); i++) relations.add(new DiplomacyRelation(rList.getCompound(i)));

        // If no nation yet, start on founding tab
        if (!hasPlayerNation) activeTab = TAB_FOUND;
        clearAndRebuild();
    }

    @Override
    public boolean shouldPause() { return false; }
}
