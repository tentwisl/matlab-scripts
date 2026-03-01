package net.mca.client.gui;

import net.mca.cobalt.network.NetworkHandler;
import net.mca.nation.*;
import net.mca.nation.cabinet.CabinetRole;
import net.mca.nation.congress.CongressMember;
import net.mca.network.c2s.*;
import net.mca.network.s2c.DiplomacyTableDataResponse;
import net.mca.network.s2c.NearbyVillagersResponse;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Multi-tab GUI for the Diplomacy Table block.
 *
 * Tabs: [Overview] [Diplomacy] [Congress] [Cabinet] [Found Nation]
 *
 * Cabinet tab:
 *   Left panel — 7 roles with current appointee name and [Dismiss] button.
 *   Right panel — nearby MCA villagers; clicking [Appoint as …] when a role
 *                 is selected sends AppointCabinetPacket.
 *
 * Congress tab:
 *   Left panel — current congress members with [Remove] button.
 *   Right panel — nearby villagers; [Add to Congress] button.
 *
 * Found Nation tab:
 *   Nation name field, government type buttons, 4×4 color palette for base
 *   color, pattern picker, secondary-color palette, up to 4 layers shown in
 *   a list, and a live flag preview rectangle.
 */
public class DiplomacyTableScreen extends ExtendedScreen {

    // ── Tab constants ──────────────────────────────────────────────────────────
    private static final int TAB_OVERVIEW  = 0;
    private static final int TAB_DIPLOMACY = 1;
    private static final int TAB_CONGRESS  = 2;
    private static final int TAB_CABINET   = 3;
    private static final int TAB_FOUND     = 4;

    private int activeTab = TAB_OVERVIEW;

    // ── Server data ───────────────────────────────────────────────────────────
    private Nation playerNation;
    private final List<Nation>            allNations  = new ArrayList<>();
    private final List<DiplomacyRelation> relations   = new ArrayList<>();
    private boolean hasPlayerNation = false;

    // ── Nearby villager list (appointment) ────────────────────────────────────
    private final List<NearbyVillagersResponse.VillagerEntry> nearbyVillagers = new ArrayList<>();
    /** Currently selected role for cabinet appointment; null = no selection. */
    private CabinetRole selectedCabinetRole = null;

    // ── "Found Nation" state ───────────────────────────────────────────────────
    private TextFieldWidget nationNameField;
    private GovernmentType  selectedGov      = GovernmentType.MONARCHY;
    // Flag layers built up by the player (max 4)
    private final List<FlagLayer> draftLayers = new ArrayList<>();
    // Currently selected pattern + color for the next layer to add
    private FlagLayer.Pattern draftPattern   = FlagLayer.Pattern.SOLID;
    private String            draftColorHex  = "3355AA";

    // 16 standard Minecraft dye colors (in dye order)
    private static final int[] DYE_COLORS_ARGB = {
        0xFFF9FFFE, // white
        0xFFF9801D, // orange
        0xFFc74ebd, // magenta
        0xFF3ab3da, // light blue
        0xFFfed83d, // yellow
        0xFF80c71f, // lime
        0xFFf38baa, // pink
        0xFF474f52, // gray
        0xFF9d9d97, // light gray
        0xFF169c9c, // cyan
        0xFF8932b8, // purple
        0xFF3c44aa, // blue
        0xFF835432, // brown
        0xFF5e7c16, // green
        0xFFb02e26, // red
        0xFF1d1d21  // black
    };
    private static final String[] DYE_HEX = {
        "F9FFFE","F9801D","C74EBD","3AB3DA",
        "FED83D","80C71F","F38BAA","474F52",
        "9D9D97","169C9C","8932B8","3C44AA",
        "835432","5E7C16","B02E26","1D1D21"
    };
    private static final String[] DYE_NAMES = {
        "White","Orange","Magenta","Light Blue",
        "Yellow","Lime","Pink","Gray",
        "Light Gray","Cyan","Purple","Blue",
        "Brown","Green","Red","Black"
    };

    // ── Flag pattern display names ─────────────────────────────────────────────
    private static final FlagLayer.Pattern[] PATTERNS = FlagLayer.Pattern.values();

    // ─────────────────────────────────────────────────────────────────────────

    public DiplomacyTableScreen() {
        super(Text.translatable("gui.mca.diplomacy_table.title"));
    }

    @Override
    protected void init() {
        super.init();
        buildTabButtons();
        rebuildContent();
        // Request nearby villagers immediately so the lists are ready when switching tabs
        NetworkHandler.sendToServer(new GetNearbyVillagersRequest());
    }

    // ── Tab bar ───────────────────────────────────────────────────────────────

    private void buildTabButtons() {
        String[] labels = {"Overview", "Diplomacy", "Congress", "Cabinet", "Found Nation"};
        int tabW = Math.min(90, (width - 10) / labels.length);
        int tabX = (width - tabW * labels.length) / 2;
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            addDrawableChild(ButtonWidget.builder(Text.literal(labels[i]), b -> {
                activeTab = idx;
                clearAndRebuild();
            }).dimensions(tabX + tabW * i, 4, tabW - 2, 16).build());
        }
    }

    private void clearAndRebuild() {
        clearChildren();
        buildTabButtons();
        rebuildContent();
    }

    private void rebuildContent() {
        switch (activeTab) {
            case TAB_CONGRESS  -> buildCongressTab();
            case TAB_CABINET   -> buildCabinetTab();
            case TAB_FOUND     -> buildFoundTab();
            default            -> {} // Overview and Diplomacy are render-only
        }
    }

    // ── Tab: Overview ─────────────────────────────────────────────────────────
    // (render only)

    // ── Tab: Diplomacy ────────────────────────────────────────────────────────
    // (render only)

    // ── Tab: Congress ─────────────────────────────────────────────────────────

    private void buildCongressTab() {
        if (!hasPlayerNation || playerNation == null) return;

        int leftW  = width / 2 - 5;
        int rightX = width / 2 + 5;
        int startY = 30;

        // Left panel: current members with [Remove] buttons
        List<CongressMember> members = playerNation.getCongress().getMembers();
        for (int i = 0; i < members.size(); i++) {
            CongressMember m  = members.get(i);
            UUID           id = m.getEntityId();
            int            y  = startY + i * 20;
            addDrawableChild(ButtonWidget.builder(Text.literal("Remove"), b -> {
                NetworkHandler.sendToServer(new RemoveCongressPacket(id));
            }).dimensions(leftW - 52, y, 50, 16).build());
        }

        // Right panel: nearby villagers with [Add] button
        for (int i = 0; i < nearbyVillagers.size(); i++) {
            NearbyVillagersResponse.VillagerEntry v = nearbyVillagers.get(i);
            boolean alreadyMember = members.stream().anyMatch(m -> m.getEntityId().equals(v.uuid()));
            boolean full          = members.size() >= net.mca.nation.congress.CongressData.DEFAULT_SEATS;
            int y = startY + i * 20;
            ButtonWidget btn = ButtonWidget.builder(Text.literal("Add"), b -> {
                NetworkHandler.sendToServer(new AppointCongressPacket(v.uuid()));
            }).dimensions(rightX + leftW - 52, y, 48, 16).build();
            btn.active = !alreadyMember && !full;
            addDrawableChild(btn);
        }
    }

    // ── Tab: Cabinet ──────────────────────────────────────────────────────────

    private void buildCabinetTab() {
        if (!hasPlayerNation || playerNation == null) return;

        int leftW  = width / 2 - 5;
        int rightX = width / 2 + 5;
        int startY = 30;

        CabinetRole[] roles = CabinetRole.values();
        for (int i = 0; i < roles.length; i++) {
            CabinetRole role   = roles[i];
            boolean     filled = playerNation.getCabinet().isFilled(role);
            int         y      = startY + i * 20;

            if (filled) {
                // [Dismiss] button
                addDrawableChild(ButtonWidget.builder(Text.literal("Dismiss"), b -> {
                    NetworkHandler.sendToServer(new DismissCabinetPacket(role));
                    selectedCabinetRole = null;
                }).dimensions(leftW - 60, y, 58, 16).build());
            } else {
                // [Select] button — marks this role as target for appointment
                addDrawableChild(ButtonWidget.builder(Text.literal("Select ›"), b -> {
                    selectedCabinetRole = (selectedCabinetRole == role) ? null : role;
                    clearAndRebuild();
                }).dimensions(leftW - 60, y, 58, 16).build());
            }
        }

        // Right panel: nearby villagers with [Appoint] button (only if a role is selected)
        for (int i = 0; i < nearbyVillagers.size(); i++) {
            NearbyVillagersResponse.VillagerEntry v = nearbyVillagers.get(i);
            int y = startY + i * 20;
            ButtonWidget btn = ButtonWidget.builder(Text.literal("Appoint"), b -> {
                if (selectedCabinetRole != null) {
                    NetworkHandler.sendToServer(new AppointCabinetPacket(selectedCabinetRole, v.uuid()));
                    selectedCabinetRole = null;
                }
            }).dimensions(rightX + leftW - 60, y, 58, 16).build();
            btn.active = selectedCabinetRole != null;
            addDrawableChild(btn);
        }
    }

    // ── Tab: Found Nation ─────────────────────────────────────────────────────

    private void buildFoundTab() {
        if (hasPlayerNation) return;

        int cx     = width / 2;
        int startY = 28;
        int fieldY = startY;

        // Nation name
        nationNameField = new TextFieldWidget(textRenderer, cx - 80, fieldY, 160, 16, Text.literal("Nation Name"));
        nationNameField.setMaxLength(32);
        nationNameField.setSuggestion("Enter nation name…");
        addDrawableChild(nationNameField);

        // Government type buttons (all in one row)
        GovernmentType[] govs = GovernmentType.values();
        int govBtnW = Math.min(76, (width - 20) / govs.length);
        int govX    = (width - govBtnW * govs.length) / 2;
        int govY    = fieldY + 22;
        for (int i = 0; i < govs.length; i++) {
            GovernmentType gov = govs[i];
            int            bx  = govX + govBtnW * i;
            addDrawableChild(ButtonWidget.builder(Text.literal(gov.getDisplayName()), b -> {
                selectedGov = gov;
                clearAndRebuild();
            }).dimensions(bx, govY, govBtnW - 2, 14).build());
        }

        // ── 4×4 primary color palette ─────────────────────────────────────
        // (16 dye colors as small squares)
        // Rendered via drawColorSwatch() in render() but buttons registered here.
        int swatchSize = 14;
        int swatchGap  = 2;
        int paletteW   = (swatchSize + swatchGap) * 8;  // 8 per row
        int paletteX   = (width - paletteW) / 2;
        int paletteY   = govY + 22;

        for (int i = 0; i < 16; i++) {
            final String hex = DYE_HEX[i];
            int col = i % 8;
            int row = i / 8;
            int bx  = paletteX + col * (swatchSize + swatchGap);
            int by  = paletteY + row * (swatchSize + swatchGap);
            // We use tiny ButtonWidget as click target; visual color drawn in render()
            addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
                draftColorHex = hex;
            }).dimensions(bx, by, swatchSize, swatchSize).build());
        }

        // ── Pattern buttons ───────────────────────────────────────────────
        int patBtnW = Math.max(52, (width - 20) / PATTERNS.length);
        int patX    = (width - patBtnW * PATTERNS.length) / 2;
        int patY    = paletteY + swatchSize * 2 + swatchGap * 2 + 6;
        for (FlagLayer.Pattern p : PATTERNS) {
            addDrawableChild(ButtonWidget.builder(Text.literal(patternLabel(p)), b -> {
                draftPattern = p;
                clearAndRebuild();
            }).dimensions(patX, patY, patBtnW - 2, 14).build());
            patX += patBtnW;
        }

        // ── [Add Layer] / [Clear Layers] ──────────────────────────────────
        int ctrlY = patY + 20;
        addDrawableChild(ButtonWidget.builder(Text.literal("+ Add Layer"), b -> {
            if (draftLayers.size() < 4) {
                draftLayers.add(new FlagLayer(draftPattern, draftColorHex));
                clearAndRebuild();
            }
        }).dimensions(cx - 80, ctrlY, 78, 14).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Clear Layers"), b -> {
            draftLayers.clear();
            clearAndRebuild();
        }).dimensions(cx + 2, ctrlY, 78, 14).build());

        // ── Submit ────────────────────────────────────────────────────────
        int submitY = ctrlY + 20;
        addDrawableChild(ButtonWidget.builder(Text.literal("Found Nation!"), b -> {
            String name = nationNameField != null ? nationNameField.getText().trim() : "";
            if (!name.isEmpty()) {
                List<FlagLayer> layers = new ArrayList<>(draftLayers);
                if (layers.isEmpty()) layers.add(new FlagLayer(FlagLayer.Pattern.SOLID, draftColorHex));
                String encoded = encodeLayers(layers);
                NetworkHandler.sendToServer(
                        new FoundNationPacket(name, selectedGov.name(), draftColorHex, encoded));
                hasPlayerNation = true;
                clearAndRebuild();
            }
        }).dimensions(cx - 55, submitY, 110, 16).build());
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx);
        super.render(ctx, mouseX, mouseY, delta);

        int cx = width / 2;
        int y  = 26;

        switch (activeTab) {
            case TAB_OVERVIEW  -> renderOverview(ctx, cx, y);
            case TAB_DIPLOMACY -> renderDiplomacy(ctx, cx, y);
            case TAB_CONGRESS  -> renderCongress(ctx, cx, y);
            case TAB_CABINET   -> renderCabinet(ctx, cx, y);
            case TAB_FOUND     -> renderFound(ctx, cx, y);
        }
    }

    private void renderOverview(DrawContext ctx, int cx, int y) {
        if (!hasPlayerNation || playerNation == null) {
            ctx.drawCenteredTextWithShadow(textRenderer, "No nation founded yet. See 'Found Nation' tab.", cx, y + 10, 0xAAAAAA);
            return;
        }
        ctx.drawCenteredTextWithShadow(textRenderer, playerNation.getName(), cx, y, 0xFFFFAA);
        NationStats s = playerNation.getStats();
        int lx = cx - 110, ly = y + 14, lh = 11;
        ctx.drawTextWithShadow(textRenderer, "Government: " + playerNation.getGovernmentType().getDisplayName(), lx, ly, 0xCCCCCC); ly += lh;
        ctx.drawTextWithShadow(textRenderer, "Leader: " + playerNation.getLeaderTitle(), lx, ly, 0xCCCCCC); ly += lh;
        ctx.drawTextWithShadow(textRenderer, "Population:  " + s.getTotalPopulation(), lx, ly, 0xCCCCCC); ly += lh;
        ctx.drawTextWithShadow(textRenderer, "Military:    " + s.getMilitaryStrength(), lx, ly, 0xCCCCCC); ly += lh;
        ctx.drawTextWithShadow(textRenderer, "Economy:     " + s.getEconomicOutput(), lx, ly, 0xCCCCCC); ly += lh;
        ctx.drawTextWithShadow(textRenderer, "Science Lv:  " + s.getScienceLevel(), lx, ly, 0xCCCCCC); ly += lh;
        ctx.drawTextWithShadow(textRenderer, "Approval:    " + s.getApprovalRating() + "%", lx, ly, s.getApprovalRating() < 30 ? 0xFF5555 : 0xCCCCCC); ly += lh;
        ctx.drawTextWithShadow(textRenderer, "Pol. Capital:" + s.getPoliticalCapital(), lx, ly, 0xCCCCCC); ly += lh + 4;
        ctx.drawTextWithShadow(textRenderer, "── Recent Events ──", lx, ly, 0xFFCC44); ly += lh;
        for (int i = 0; i < Math.min(6, playerNation.getRecentEvents().size()); i++) {
            ctx.drawTextWithShadow(textRenderer, playerNation.getRecentEvents().get(i), lx, ly, 0x999999); ly += lh;
        }
        if (playerNation.getRecentEvents().isEmpty())
            ctx.drawTextWithShadow(textRenderer, "(no events yet)", lx, ly, 0x666666);
        // Mini flag preview top-right
        renderFlagPreview(ctx, width - 48, 28, 40, 56, playerNation.getFlagLayers(), playerNation.getFlagColorHex());
    }

    private void renderDiplomacy(DrawContext ctx, int cx, int y) {
        ctx.drawCenteredTextWithShadow(textRenderer, "Known Nations", cx, y, 0xFFFFAA);
        int ly = y + 14;
        for (Nation n : allNations) {
            if (playerNation != null && n.getNationId().equals(playerNation.getNationId())) continue;
            String status = "NEUTRAL";
            if (playerNation != null) {
                Optional<DiplomacyRelation> rel = relations.stream()
                        .filter(r -> (r.getNationA().equals(playerNation.getNationId()) && r.getNationB().equals(n.getNationId()))
                                  || (r.getNationB().equals(playerNation.getNationId()) && r.getNationA().equals(n.getNationId())))
                        .findFirst();
                if (rel.isPresent()) status = rel.get().getStatus().name();
            }
            int color = switch (status) {
                case "AT_WAR"  -> 0xFF4444;
                case "HOSTILE" -> 0xFF8844;
                case "FRIENDLY", "ALLIANCE", "TRADE_AGREEMENT" -> 0x88FF88;
                default        -> 0xCCCCCC;
            };
            ctx.drawTextWithShadow(textRenderer,
                    n.getName() + " [" + n.getGovernmentType().getDisplayName() + "]  ─  " + status,
                    cx - 130, ly, color);
            ly += 12;
        }
        if (allNations.isEmpty() || (allNations.size() == 1 && playerNation != null))
            ctx.drawCenteredTextWithShadow(textRenderer, "(no other nations known yet)", cx, y + 20, 0x666666);
    }

    private void renderCongress(DrawContext ctx, int cx, int y) {
        if (!hasPlayerNation || playerNation == null) {
            ctx.drawCenteredTextWithShadow(textRenderer, "Found a nation first.", cx, y + 10, 0xAAAAAA);
            return;
        }
        int leftW  = width / 2 - 5;
        int rightX = width / 2 + 5;

        // Left panel header
        ctx.drawTextWithShadow(textRenderer, "── Congress Members ──", 4, y, 0xFFCC44);
        int seated = playerNation.getCongress().size();
        ctx.drawTextWithShadow(textRenderer,
                "Seats: " + seated + "/" + net.mca.nation.congress.CongressData.DEFAULT_SEATS,
                4, y + 11, 0xCCCCCC);

        List<CongressMember> members = playerNation.getCongress().getMembers();
        for (int i = 0; i < members.size(); i++) {
            CongressMember m  = members.get(i);
            String         id = m.getEntityId().toString().substring(0, 8) + "…";
            ctx.drawTextWithShadow(textRenderer,
                    (i + 1) + ". " + id + (m.isPlayer() ? " [Player]" : ""),
                    4, y + 26 + i * 20, 0xCCCCCC);
        }
        if (members.isEmpty())
            ctx.drawTextWithShadow(textRenderer, "(none — appoint from right panel)", 4, y + 26, 0x888888);

        // Right panel header
        ctx.drawTextWithShadow(textRenderer, "── Nearby Villagers ──", rightX, y, 0xFFCC44);
        if (nearbyVillagers.isEmpty())
            ctx.drawTextWithShadow(textRenderer, "(none in range)", rightX, y + 14, 0x888888);
        for (int i = 0; i < nearbyVillagers.size(); i++) {
            NearbyVillagersResponse.VillagerEntry v = nearbyVillagers.get(i);
            ctx.drawTextWithShadow(textRenderer, v.name() + " · " + shortProfession(v.profession()),
                    rightX, y + 26 + i * 20, 0xCCCCCC);
        }
    }

    private void renderCabinet(DrawContext ctx, int cx, int y) {
        if (!hasPlayerNation || playerNation == null) {
            ctx.drawCenteredTextWithShadow(textRenderer, "Found a nation first.", cx, y + 10, 0xAAAAAA);
            return;
        }
        int rightX = width / 2 + 5;

        // Left panel header
        ctx.drawTextWithShadow(textRenderer, "── Cabinet Roles ──", 4, y, 0xFFCC44);
        if (selectedCabinetRole != null)
            ctx.drawTextWithShadow(textRenderer, "Appointing for: " + selectedCabinetRole.displayName, 4, y + 11, 0xFFDD55);

        CabinetRole[] roles = CabinetRole.values();
        for (int i = 0; i < roles.length; i++) {
            CabinetRole role   = roles[i];
            boolean     filled = playerNation.getCabinet().isFilled(role);
            boolean     sel    = role == selectedCabinetRole;
            int         ry     = y + 26 + i * 20;
            int         fg     = sel ? 0xFFFF55 : (filled ? 0xAAFFAA : 0xFF8888);
            ctx.drawTextWithShadow(textRenderer,
                    role.displayName + ": " + (filled ? "Appointed" : "Empty"),
                    4, ry + 2, fg);
        }

        // Right panel header
        ctx.drawTextWithShadow(textRenderer, "── Nearby Villagers ──", rightX, y, 0xFFCC44);
        if (selectedCabinetRole == null)
            ctx.drawTextWithShadow(textRenderer, "← Select a role first", rightX, y + 11, 0x888888);
        if (nearbyVillagers.isEmpty())
            ctx.drawTextWithShadow(textRenderer, "(none in range)", rightX, y + 26, 0x888888);
        for (int i = 0; i < nearbyVillagers.size(); i++) {
            NearbyVillagersResponse.VillagerEntry v = nearbyVillagers.get(i);
            ctx.drawTextWithShadow(textRenderer, v.name() + " · " + shortProfession(v.profession()),
                    rightX, y + 26 + i * 20, 0xCCCCCC);
        }
    }

    private void renderFound(DrawContext ctx, int cx, int y) {
        if (hasPlayerNation) {
            ctx.drawCenteredTextWithShadow(textRenderer, "You have already founded a nation.", cx, y + 10, 0xAAFFAA);
            return;
        }

        int govY    = y + 22;
        // Gov label
        ctx.drawCenteredTextWithShadow(textRenderer, "Government: " + selectedGov.getDisplayName(), cx, govY - 2, 0xFFCC44);

        // Color palette label
        int swatchSize = 14;
        int swatchGap  = 2;
        int paletteW   = (swatchSize + swatchGap) * 8;
        int paletteX   = (width - paletteW) / 2;
        int paletteY   = govY + 22;

        ctx.drawTextWithShadow(textRenderer, "Base Color:  #" + draftColorHex, paletteX, paletteY - 10, 0xCCCCCC);
        // Draw the 16 color swatches (2 rows of 8)
        for (int i = 0; i < 16; i++) {
            int col = i % 8;
            int row = i / 8;
            int bx  = paletteX + col * (swatchSize + swatchGap);
            int by  = paletteY + row * (swatchSize + swatchGap);
            boolean selected = DYE_HEX[i].equals(draftColorHex);
            // Outer border (white if selected, dark if not)
            ctx.fill(bx - 1, by - 1, bx + swatchSize + 1, by + swatchSize + 1,
                    selected ? 0xFFFFFFFF : 0xFF222222);
            // Color fill
            ctx.fill(bx, by, bx + swatchSize, by + swatchSize, DYE_COLORS_ARGB[i]);
        }

        // Pattern label
        int patY = paletteY + swatchSize * 2 + swatchGap * 2 + 6;
        ctx.drawTextWithShadow(textRenderer, "Pattern:", paletteX, patY - 10, 0xCCCCCC);
        // Highlight selected pattern button is handled by button active state → render loop

        // Layers list
        int ctrlY  = patY + 20;
        int layersX = paletteX;
        int layerY  = ctrlY + 22;
        ctx.drawTextWithShadow(textRenderer, "Layers (max 4):", layersX, layerY - 12, 0xCCCCCC);
        if (draftLayers.isEmpty()) {
            ctx.drawTextWithShadow(textRenderer, "(no layers — will default to solid base color)", layersX, layerY, 0x888888);
        } else {
            for (int i = 0; i < draftLayers.size(); i++) {
                FlagLayer fl = draftLayers.get(i);
                ctx.drawTextWithShadow(textRenderer,
                        (i + 1) + ". " + patternLabel(fl.getPattern()) + "  #" + fl.getColorHex(),
                        layersX, layerY + i * 10, 0xCCCCCC);
            }
        }

        // Flag preview (right side)
        int previewX = width - 60;
        int previewY = govY;
        ctx.drawTextWithShadow(textRenderer, "Preview", previewX, previewY - 10, 0xCCCCCC);
        List<FlagLayer> preview = new ArrayList<>(draftLayers);
        if (preview.isEmpty()) preview.add(new FlagLayer(FlagLayer.Pattern.SOLID, draftColorHex));
        renderFlagPreview(ctx, previewX, previewY, 48, 64, preview, draftColorHex);
    }

    // ── Flag preview renderer ─────────────────────────────────────────────────

    /**
     * Renders a simple flag preview using DrawContext fill calls.
     * {@code layers} are drawn bottom-to-top at position (x, y) with size (w × h).
     * If layers is empty, falls back to a solid {@code fallbackColorHex} fill.
     */
    private void renderFlagPreview(DrawContext ctx, int x, int y, int w, int h,
                                   List<FlagLayer> layers, String fallbackColorHex) {
        // Black border
        ctx.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF000000);

        // Draw each layer
        for (FlagLayer layer : layers) {
            int argb = layer.getArgb();
            drawFlagPattern(ctx, x, y, w, h, layer.getPattern(), argb);
        }

        // If no layers, draw fallback solid
        if (layers.isEmpty()) {
            try {
                int rgb = Integer.parseInt(fallbackColorHex, 16);
                ctx.fill(x, y, x + w, y + h, 0xFF000000 | rgb);
            } catch (NumberFormatException ignored) {
                ctx.fill(x, y, x + w, y + h, 0xFFAAAAAA);
            }
        }
    }

    private void drawFlagPattern(DrawContext ctx, int x, int y, int w, int h,
                                 FlagLayer.Pattern pattern, int argb) {
        int stripe = Math.max(2, h / 5);
        int mid    = h / 2;
        int midX   = w / 2;
        int border = 3;
        switch (pattern) {
            case SOLID         -> ctx.fill(x, y, x + w, y + h, argb);
            case STRIPE_H      -> ctx.fill(x, y + mid - stripe / 2, x + w, y + mid + stripe / 2, argb);
            case STRIPE_V      -> ctx.fill(x + midX - border, y, x + midX + border, y + h, argb);
            case CROSS         -> {
                ctx.fill(x, y + mid - stripe / 2, x + w, y + mid + stripe / 2, argb);
                ctx.fill(x + midX - border, y, x + midX + border, y + h, argb);
            }
            case TRIANGLE_TOP_LEFT -> {
                // Approximate diagonal with stacked horizontal strips
                for (int row = 0; row < h; row++) {
                    int lineW = (int)((double)(h - row) / h * w);
                    ctx.fill(x, y + row, x + lineW, y + row + 1, argb);
                }
            }
            case HALF_BOTTOM   -> ctx.fill(x, y + h / 2, x + w, y + h, argb);
            case HALF_RIGHT    -> ctx.fill(x + w / 2, y, x + w, y + h, argb);
            case BORDER        -> {
                ctx.fill(x, y, x + w, y + border, argb);
                ctx.fill(x, y + h - border, x + w, y + h, argb);
                ctx.fill(x, y, x + border, y + h, argb);
                ctx.fill(x + w - border, y, x + w, y + h, argb);
            }
            case DIAMOND       -> {
                int dw = w / 3, dh = h / 3;
                for (int row = 0; row < h; row++) {
                    int dist = Math.abs(row - mid);
                    int half = (int)((1.0 - (double) dist / mid) * dw);
                    ctx.fill(x + midX - half, y + row, x + midX + half, y + row + 1, argb);
                }
            }
            case CHEVRON_UP    -> {
                for (int row = 0; row < h / 2; row++) {
                    int half = (int)((double) row / (h / 2) * (w / 2));
                    ctx.fill(x + midX - half, y + h - row - 1, x + midX + half, y + h - row, argb);
                }
            }
        }
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

        if (!hasPlayerNation) activeTab = TAB_FOUND;
        clearAndRebuild();
    }

    public void loadNearbyVillagers(NearbyVillagersResponse response) {
        nearbyVillagers.clear();
        nearbyVillagers.addAll(response.getEntries());
        clearAndRebuild();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String patternLabel(FlagLayer.Pattern p) {
        return switch (p) {
            case SOLID            -> "Solid";
            case STRIPE_H         -> "Horiz. Stripe";
            case STRIPE_V         -> "Vert. Stripe";
            case CROSS            -> "Cross";
            case TRIANGLE_TOP_LEFT -> "Triangle";
            case HALF_BOTTOM      -> "Half Bottom";
            case HALF_RIGHT       -> "Half Right";
            case BORDER           -> "Border";
            case DIAMOND          -> "Diamond";
            case CHEVRON_UP       -> "Chevron";
        };
    }

    private static String shortProfession(String raw) {
        int idx = raw.lastIndexOf(':');
        return idx >= 0 ? raw.substring(idx + 1) : raw;
    }

    private static String encodeLayers(List<FlagLayer> layers) {
        StringBuilder sb = new StringBuilder();
        for (FlagLayer fl : layers) {
            if (sb.length() > 0) sb.append(',');
            sb.append(fl.getPattern().name()).append(':').append(fl.getColorHex());
        }
        return sb.toString();
    }

    @Override
    public boolean shouldPause() { return false; }
}
