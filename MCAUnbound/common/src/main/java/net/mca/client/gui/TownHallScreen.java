package net.mca.client.gui;

import net.mca.cobalt.network.NetworkHandler;
import net.mca.nation.MCAUnboundConfig;
import net.mca.network.c2s.AttemptLeadershipRequest;
import net.mca.network.c2s.HighlightVillagerRequest;
import net.mca.network.s2c.TownHallDataResponse;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Town Hall block GUI.
 *
 * <ul>
 *   <li>Village name, resident count, and current leader (NPC or player)</li>
 *   <li>Scrollable resident list with per-villager hearts, colour-coded by threshold</li>
 *   <li>Simple face glyph avatar next to each villager name</li>
 *   <li>Click a villager row to highlight them in-world (Glowing for 30 s)</li>
 *   <li>Pending village supply requests section</li>
 *   <li>"Attempt Leadership" button when the player is eligible</li>
 * </ul>
 */
public class TownHallScreen extends ExtendedScreen {

    // ── Village data ──────────────────────────────────────────────────────────
    private String villageName = "Unknown";
    private int maxPopulation  = 0;

    // ── Per-villager data: UUID string → hearts / name ────────────────────────
    private final Map<String, Integer> villagerHearts = new LinkedHashMap<>();
    private final Map<String, String>  villagerNames  = new LinkedHashMap<>();

    // ── Leadership ────────────────────────────────────────────────────────────
    private boolean hasLeader      = false;
    private String  leaderName     = "";
    private boolean leaderIsPlayer = false;
    private boolean isPlayerLeader = false;
    private int     electionState  = 0;

    // ── Block pos (for sending action packets) ────────────────────────────────
    private BlockPos blockPos = BlockPos.ORIGIN;

    // ── List scroll state ────────────────────────────────────────────────────
    private int scrollOffset = 0;
    private static final int VISIBLE_ROWS = 9;
    private static final int ROW_HEIGHT   = 14;
    /** Y-start of the first rendered row (set in render(), used in mouseClicked) */
    private int listStartY = 80;

    // ── Derived eligibility flags ─────────────────────────────────────────────
    private boolean isResident          = false;
    private boolean meetsLeaderThreshold = false;

    // ── Ordered UUID list (built from villagerNames keyset each render) ────────
    private List<String> orderedUuids = new ArrayList<>();

    // ── Pending requests ──────────────────────────────────────────────────────
    /** [itemName, remaining, total] display strings */
    private final List<String[]> pendingRequests = new ArrayList<>();

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
        int bottomY = height - 28;

        // Close
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> close())
                .dimensions(cx - 40, bottomY, 80, 20).build());

        // Attempt Leadership
        boolean canAttempt = !villagerHearts.isEmpty()
                && meetsLeaderThreshold
                && electionState == 0
                && !(hasLeader && leaderIsPlayer);

        if (canAttempt) {
            addDrawableChild(ButtonWidget.builder(Text.literal("Attempt Leadership"), b -> {
                NetworkHandler.sendToServer(new AttemptLeadershipRequest(blockPos));
                close();
            }).dimensions(cx - 68, bottomY - 22, 136, 20).build());
        }

        // Scroll buttons
        if (orderedUuids.size() > VISIBLE_ROWS) {
            int listX = width / 2 - 120;
            addDrawableChild(ButtonWidget.builder(Text.literal("▲"), b ->
                    scrollOffset = Math.max(0, scrollOffset - 1)
            ).dimensions(listX + 246, listStartY, 18, 18).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("▼"), b ->
                    scrollOffset = Math.min(orderedUuids.size() - VISIBLE_ROWS, scrollOffset + 1)
            ).dimensions(listX + 246, listStartY + VISIBLE_ROWS * ROW_HEIGHT - 18, 18, 18).build());
        }
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx);
        super.render(ctx, mouseX, mouseY, delta);

        MCAUnboundConfig cfg = MCAUnboundConfig.get();
        int cx = width / 2;
        int y  = 12;

        // Title
        ctx.drawCenteredTextWithShadow(textRenderer, "§6§lTown Hall — " + villageName, cx, y, 0xFFFFFF);
        y += 16;

        // Population (use actual resident count) + leader
        int residentCount = villagerNames.size();
        ctx.drawTextWithShadow(textRenderer, "Residents: " + residentCount + "/" + maxPopulation, cx - 120, y, 0xCCCCCC);

        if (hasLeader) {
            String prefix = leaderIsPlayer ? "§a[Player] " : "§b[NPC] ";
            ctx.drawTextWithShadow(textRenderer, "Leader: " + prefix + "§f" + leaderName, cx + 10, y, 0xFFFFFF);
        } else if (electionState > 0) {
            ctx.drawTextWithShadow(textRenderer, "§eElection in progress…", cx + 10, y, 0xFFFFFF);
        } else {
            ctx.drawTextWithShadow(textRenderer, "§cLeader: None", cx + 10, y, 0xFFFFFF);
        }
        y += 16;

        // Status line
        if (meetsLeaderThreshold) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "§aStatus: §fEligible for Leadership", cx, y, 0xFFFFFF);
        } else if (isResident) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "§bStatus: §fResident  (need §e" + cfg.leaderHeartThreshold + "§f ♥ with all for leadership)",
                    cx, y, 0xFFFFFF);
        } else {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "§6Status: §fOutsider  (need §e" + cfg.residentHeartThreshold + "§f ♥ with all to become resident)",
                    cx, y, 0xFFFFFF);
        }
        y += 18;

        // ── Resident list ─────────────────────────────────────────────────────
        int listX = cx - 120;
        listStartY = y;

        // Header row
        ctx.fill(listX - 4, y - 2, listX + 248, y + 12, 0x99000000);
        ctx.drawTextWithShadow(textRenderer, "§fVillager", listX + 14, y, 0xFFFFFF);
        ctx.drawTextWithShadow(textRenderer, "§fHearts", listX + 185, y, 0xFFFFFF);
        ctx.drawTextWithShadow(textRenderer, "§7[click to highlight]", listX + 60, y + 1, 0x888888);
        y += ROW_HEIGHT;

        orderedUuids = new ArrayList<>(villagerNames.keySet());
        int total = orderedUuids.size();
        int end   = Math.min(scrollOffset + VISIBLE_ROWS, total);

        for (int i = scrollOffset; i < end; i++) {
            String uuid    = orderedUuids.get(i);
            String name    = villagerNames.getOrDefault(uuid, "???");
            int hearts     = villagerHearts.getOrDefault(uuid, 0);

            boolean hovered = mouseX >= listX - 4 && mouseX < listX + 244
                    && mouseY >= y - 1 && mouseY < y + ROW_HEIGHT - 2;

            int rowBg = hovered ? 0x66FFFFFF : (i % 2 == 0 ? 0x44000000 : 0x22000000);
            ctx.fill(listX - 4, y - 1, listX + 244, y + ROW_HEIGHT - 2, rowBg);

            // Face marker (prevents flat color-block avatars).
            int faceColor = hovered ? 0xFFE8D6B5 : 0xFFD9C4A2;
            ctx.drawTextWithShadow(textRenderer, "☺", listX, y, faceColor);

            String display = name.length() > 24 ? name.substring(0, 22) + ".." : name;
            ctx.drawTextWithShadow(textRenderer, display, listX + 12, y, hovered ? 0xFFFFFF : 0xCCCCCC);

            int heartColor;
            if      (hearts >= cfg.leaderHeartThreshold)   heartColor = 0x55FF55;
            else if (hearts >= cfg.residentHeartThreshold)  heartColor = 0x55FFFF;
            else if (hearts > 0)                            heartColor = 0xFFFF55;
            else                                            heartColor = 0xFF5555;

            ctx.drawTextWithShadow(textRenderer, hearts + " ♥", listX + 185, y, heartColor);
            y += ROW_HEIGHT;
        }

        if (total == 0) {
            ctx.drawCenteredTextWithShadow(textRenderer, "§7No residents loaded", cx, y + 8, 0xFFFFFF);
            y += ROW_HEIGHT;
        } else if (total > VISIBLE_ROWS) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "§7" + (scrollOffset + 1) + "–" + end + " of " + total, cx, y + 2, 0xFFFFFF);
            y += ROW_HEIGHT;
        }

        y += 6;

        // ── Pending supply requests ───────────────────────────────────────────
        if (!pendingRequests.isEmpty()) {
            ctx.fill(listX - 4, y - 2, listX + 248, y + 12, 0x99220000);
            ctx.drawTextWithShadow(textRenderer, "§c§lPending Requests", listX, y, 0xFFFFFF);
            y += ROW_HEIGHT;

            for (int i = 0; i < pendingRequests.size(); i++) {
                String[] r = pendingRequests.get(i);
                int bg = i % 2 == 0 ? 0x44110000 : 0x22110000;
                ctx.fill(listX - 4, y - 1, listX + 244, y + ROW_HEIGHT - 2, bg);
                ctx.drawTextWithShadow(textRenderer, "§f" + r[0], listX, y, 0xFFFFFF);
                ctx.drawTextWithShadow(textRenderer, "§e" + r[1] + "§7/" + r[2], listX + 170, y, 0xFFFFFF);
                y += ROW_HEIGHT;
            }
        }
    }

    // ── Mouse interaction ─────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int cx = width / 2;
        int listX = cx - 120;

        if (mouseX >= listX - 4 && mouseX < listX + 244) {
            int rowY = listStartY + ROW_HEIGHT; // +ROW_HEIGHT to skip the header
            int total = orderedUuids.size();
            int end   = Math.min(scrollOffset + VISIBLE_ROWS, total);

            for (int i = scrollOffset; i < end; i++) {
                if (mouseY >= rowY - 1 && mouseY < rowY + ROW_HEIGHT - 2) {
                    String uuidStr = orderedUuids.get(i);
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        NetworkHandler.sendToServer(new HighlightVillagerRequest(uuid));
                    } catch (IllegalArgumentException ignored) { }
                    return true;
                }
                rowY += ROW_HEIGHT;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int total = orderedUuids.size();
        if (total > VISIBLE_ROWS) {
            scrollOffset -= (int) amount;
            scrollOffset = Math.max(0, Math.min(scrollOffset, total - VISIBLE_ROWS));
            rebuildButtons();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    public void loadData(TownHallDataResponse response) {
        NbtCompound root = response.getData();

        villageName   = root.getString("villageName");
        if (villageName.isEmpty()) villageName = "Unknown";
        maxPopulation = root.getInt("maxPopulation");
        // Note: we display villagerNames.size() as the resident count (accurate)

        villagerHearts.clear();
        villagerNames.clear();

        if (root.contains("villagerHearts")) {
            NbtCompound hn = root.getCompound("villagerHearts");
            for (String k : hn.getKeys()) villagerHearts.put(k, hn.getInt(k));
        }
        if (root.contains("villagerNames")) {
            NbtCompound nn = root.getCompound("villagerNames");
            for (String k : nn.getKeys()) villagerNames.put(k, nn.getString(k));
        }

        hasLeader      = root.getBoolean("hasLeader");
        leaderIsPlayer = root.getBoolean("leaderIsPlayer");
        leaderName     = hasLeader ? root.getString("leaderName") : "";
        isPlayerLeader = root.getBoolean("isPlayerLeader");
        electionState  = root.getInt("electionState");

        if (root.contains("blockPos")) {
            blockPos = BlockPos.fromLong(root.getLong("blockPos"));
        }

        // Pending supply requests
        pendingRequests.clear();
        if (root.contains("pendingRequests")) {
            NbtList pl = root.getList("pendingRequests", 10);
            for (int i = 0; i < pl.size(); i++) {
                NbtCompound e = pl.getCompound(i);
                String itemName = e.getString("itemName");
                int fulfilled   = e.getInt("fulfilled");
                int total       = e.getInt("total");
                pendingRequests.add(new String[]{itemName, String.valueOf(fulfilled), String.valueOf(total)});
            }
        }

        // Recalculate eligibility
        MCAUnboundConfig cfg = MCAUnboundConfig.get();
        isResident           = true;
        meetsLeaderThreshold = true;

        if (villagerHearts.isEmpty()) {
            isResident = meetsLeaderThreshold = false;
        } else {
            for (int h : villagerHearts.values()) {
                if (h < cfg.residentHeartThreshold)  isResident          = false;
                if (h < cfg.leaderHeartThreshold)     meetsLeaderThreshold = false;
            }
        }

        orderedUuids = new ArrayList<>(villagerNames.keySet());

        if (client != null) rebuildButtons();
    }

    @Override
    public boolean shouldPause() { return false; }
}
