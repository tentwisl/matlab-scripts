package net.mca.client.gui;

import net.mca.cobalt.network.NetworkHandler;
import net.mca.nation.MCAUnboundConfig;
import net.mca.network.c2s.AttemptLeadershipRequest;
import net.mca.network.s2c.TownHallDataResponse;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Town Hall block GUI.
 * Shows: village name, population, resident list with per-villager hearts,
 * current leader, and "Attempt Leadership" button when eligible.
 */
public class TownHallScreen extends ExtendedScreen {

    private String villageName = "Unknown";
    private int population = 0;
    private int maxPopulation = 0;

    // Per-villager data: UUID string -> hearts, names
    private final Map<String, Integer> villagerHearts = new LinkedHashMap<>();
    private final Map<String, String> villagerNames = new LinkedHashMap<>();

    // Leadership
    private boolean hasLeader = false;
    private String leaderName = "";
    private boolean isPlayerLeader = false;
    private int electionState = 0; // 0=NONE, 1=PENDING, 2=ACTIVE

    // Block pos for sending leadership request
    private BlockPos blockPos = BlockPos.ORIGIN;

    // Scroll offset for resident list
    private int scrollOffset = 0;
    private static final int VISIBLE_ROWS = 10;
    private static final int ROW_HEIGHT = 14;

    // Whether the player meets resident threshold (30 hearts with all)
    private boolean isResident = false;
    // Whether the player meets leader threshold (50 hearts with all)
    private boolean meetsLeaderThreshold = false;

    public TownHallScreen() {
        super(Text.translatable("gui.mca.town_hall.title"));
    }

    @Override
    protected void init() {
        super.init();
        rebuildButtons();
    }

    private void rebuildButtons() {
        clearChildren();

        int cx = width / 2;
        int bottomY = height - 30;

        // Close button
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> close())
                .dimensions(cx - 42, bottomY, 84, 20).build());

        // Attempt Leadership button — only show if:
        // 1. No leader exists, 2. No election in progress, 3. Player meets threshold
        if (!hasLeader && electionState == 0 && meetsLeaderThreshold && !villagerHearts.isEmpty()) {
            addDrawableChild(ButtonWidget.builder(Text.literal("Attempt Leadership"), b -> {
                NetworkHandler.sendToServer(new AttemptLeadershipRequest(blockPos));
                close();
            }).dimensions(cx - 70, bottomY - 24, 140, 20).build());
        }

        // Scroll buttons if needed
        int totalResidents = villagerNames.size();
        if (totalResidents > VISIBLE_ROWS) {
            int listX = cx - 120;
            int listTopY = 80;
            addDrawableChild(ButtonWidget.builder(Text.literal("^"), b -> {
                scrollOffset = Math.max(0, scrollOffset - 1);
            }).dimensions(listX + 245, listTopY, 20, 20).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("v"), b -> {
                scrollOffset = Math.min(totalResidents - VISIBLE_ROWS, scrollOffset + 1);
            }).dimensions(listX + 245, listTopY + VISIBLE_ROWS * ROW_HEIGHT - 20, 20, 20).build());
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx);
        super.render(ctx, mouseX, mouseY, delta);

        int cx = width / 2;
        int y = 12;

        // ── Title ──
        ctx.drawCenteredTextWithShadow(textRenderer, "Town Hall — " + villageName, cx, y, 0xFFFFAA);
        y += 16;

        // ── Village Info Row ──
        String popStr = "Population: " + population + "/" + maxPopulation;
        ctx.drawTextWithShadow(textRenderer, popStr, cx - 120, y, 0xCCCCCC);

        if (hasLeader) {
            ctx.drawTextWithShadow(textRenderer, "Leader: " + leaderName,
                    cx + 20, y, 0xAAFFAA);
        } else if (electionState > 0) {
            ctx.drawTextWithShadow(textRenderer, "Election in progress...",
                    cx + 20, y, 0xFFFF55);
        } else {
            ctx.drawTextWithShadow(textRenderer, "Leader: None",
                    cx + 20, y, 0xFF8888);
        }
        y += 16;

        // ── Status line ──
        MCAUnboundConfig config = MCAUnboundConfig.get();
        if (isResident && meetsLeaderThreshold) {
            ctx.drawCenteredTextWithShadow(textRenderer, "Status: Eligible for Leadership", cx, y, 0x55FF55);
        } else if (isResident) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "Status: Resident (need " + config.leaderHeartThreshold + " hearts with all for leadership)",
                    cx, y, 0x55FFFF);
        } else {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "Status: Outsider (need " + config.residentHeartThreshold + " hearts with all to become resident)",
                    cx, y, 0xFFAA55);
        }
        y += 18;

        // ── Resident List Header ──
        int listX = cx - 120;
        ctx.fill(listX - 4, y - 2, listX + 248, y + 12, 0x88000000);
        ctx.drawTextWithShadow(textRenderer, "Villager", listX, y, 0xFFFFFF);
        ctx.drawTextWithShadow(textRenderer, "Hearts", listX + 180, y, 0xFFFFFF);
        y += ROW_HEIGHT;

        // ── Resident List ──
        List<String> uuids = new ArrayList<>(villagerNames.keySet());
        int totalResidents = uuids.size();
        int end = Math.min(scrollOffset + VISIBLE_ROWS, totalResidents);

        for (int i = scrollOffset; i < end; i++) {
            String uuid = uuids.get(i);
            String name = villagerNames.getOrDefault(uuid, "???");
            int hearts = villagerHearts.getOrDefault(uuid, 0);

            // Row background (alternating)
            int rowColor = (i % 2 == 0) ? 0x44000000 : 0x22000000;
            ctx.fill(listX - 4, y - 1, listX + 248, y + ROW_HEIGHT - 2, rowColor);

            // Name
            String displayName = name.length() > 24 ? name.substring(0, 22) + ".." : name;
            ctx.drawTextWithShadow(textRenderer, displayName, listX, y, 0xCCCCCC);

            // Hearts with color coding
            int heartColor;
            if (hearts >= config.leaderHeartThreshold) {
                heartColor = 0x55FF55; // Green - meets leader threshold
            } else if (hearts >= config.residentHeartThreshold) {
                heartColor = 0x55FFFF; // Cyan - meets resident threshold
            } else if (hearts > 0) {
                heartColor = 0xFFFF55; // Yellow - positive but below threshold
            } else {
                heartColor = 0xFF5555; // Red - zero or negative
            }
            ctx.drawTextWithShadow(textRenderer, hearts + " \u2764", listX + 180, y, heartColor);

            y += ROW_HEIGHT;
        }

        // Scroll indicator
        if (totalResidents > VISIBLE_ROWS) {
            String scrollInfo = (scrollOffset + 1) + "-" + end + " of " + totalResidents;
            ctx.drawCenteredTextWithShadow(textRenderer, scrollInfo, cx, y + 4, 0x888888);
        }

        // Empty state
        if (totalResidents == 0) {
            ctx.drawCenteredTextWithShadow(textRenderer, "No residents found", cx, y + 10, 0x888888);
        }
    }

    public void loadData(TownHallDataResponse response) {
        NbtCompound root = response.getData();
        villageName = root.getString("villageName");
        if (villageName.isEmpty()) villageName = "Unknown";
        population = root.getInt("population");
        maxPopulation = root.getInt("maxPopulation");

        // Per-villager hearts
        villagerHearts.clear();
        villagerNames.clear();
        if (root.contains("villagerHearts")) {
            NbtCompound heartsNbt = root.getCompound("villagerHearts");
            for (String key : heartsNbt.getKeys()) {
                villagerHearts.put(key, heartsNbt.getInt(key));
            }
        }
        if (root.contains("villagerNames")) {
            NbtCompound namesNbt = root.getCompound("villagerNames");
            for (String key : namesNbt.getKeys()) {
                villagerNames.put(key, namesNbt.getString(key));
            }
        }

        // Leadership
        hasLeader = root.getBoolean("hasLeader");
        if (hasLeader) {
            leaderName = root.getString("leaderName");
        }
        isPlayerLeader = root.getBoolean("isPlayerLeader");
        electionState = root.getInt("electionState");

        // Block position
        if (root.contains("blockPos")) {
            blockPos = BlockPos.fromLong(root.getLong("blockPos"));
        }

        // Calculate resident/leader status from hearts
        MCAUnboundConfig config = MCAUnboundConfig.get();
        isResident = true;
        meetsLeaderThreshold = true;

        if (villagerHearts.isEmpty()) {
            isResident = false;
            meetsLeaderThreshold = false;
        } else {
            for (int hearts : villagerHearts.values()) {
                if (hearts < config.residentHeartThreshold) {
                    isResident = false;
                }
                if (hearts < config.leaderHeartThreshold) {
                    meetsLeaderThreshold = false;
                }
            }
        }

        // Rebuild buttons now that we have data
        if (client != null) {
            rebuildButtons();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int totalResidents = villagerNames.size();
        if (totalResidents > VISIBLE_ROWS) {
            scrollOffset -= (int) amount;
            scrollOffset = Math.max(0, Math.min(scrollOffset, totalResidents - VISIBLE_ROWS));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean shouldPause() { return false; }
}
