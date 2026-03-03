package net.mca.client.gui;

import net.mca.cobalt.network.NetworkHandler;
import net.mca.network.c2s.AcceptVillageRequestPacket;
import net.mca.network.c2s.FulfillVillageRequestPacket;
import net.mca.network.s2c.HelpVillageDataResponse;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Help Village screen — opened when clicking "Help Village" on the NPC leader.
 *
 * Layout is computed once in computeSectionRowY() and reused by both
 * render() and rebuildButtons(), so button positions always match drawn rows.
 */
public class HelpVillageScreen extends ExtendedScreen {

    // ── Request data ─────────────────────────────────────────────────────────
    private record RequestEntry(int id, String itemName, int fulfilled, int total, boolean mine) {}

    private BlockPos townHallPos = BlockPos.ORIGIN;
    private String   leaderName  = "";

    private final List<RequestEntry> openRequests    = new ArrayList<>();
    private final List<RequestEntry> pendingRequests = new ArrayList<>();
    private final List<RequestEntry> othersRequests  = new ArrayList<>();

    // ── Layout constants ─────────────────────────────────────────────────────
    private static final int ROW_H   = 22;
    private static final int HDR_H   = 14;
    private static final int GAP     = 8;
    private static final int BTN_W   = 54;
    private static final int BTN_H   = 14;
    private static final int PANEL_W = 290;
    /** Right-side margin so the panel sits on the right like InteractScreen. */
    private static final int MARGIN  = 8;
    private static final int START_Y = 10;
    private static final int TITLE_H = 28; // height used by title block in render()

    public HelpVillageScreen() {
        super(Text.translatable("gui.mca.help_village.title"));
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    public void loadData(HelpVillageDataResponse response) {
        NbtCompound root = response.getData();
        townHallPos = BlockPos.fromLong(root.getLong("blockPos"));
        leaderName  = root.getString("leaderName");

        openRequests.clear();
        pendingRequests.clear();
        othersRequests.clear();

        NbtList openList = root.getList("openRequests", 10);
        for (int i = 0; i < openList.size(); i++) {
            NbtCompound e = openList.getCompound(i);
            openRequests.add(new RequestEntry(
                    e.getInt("id"), e.getString("itemName"),
                    e.getInt("fulfilled"), e.getInt("total"), false));
        }

        NbtList pendList = root.getList("pendingRequests", 10);
        for (int i = 0; i < pendList.size(); i++) {
            NbtCompound e = pendList.getCompound(i);
            boolean mine = e.getBoolean("mine");
            var entry = new RequestEntry(
                    e.getInt("id"), e.getString("itemName"),
                    e.getInt("fulfilled"), e.getInt("total"), mine);
            if (mine) pendingRequests.add(entry);
            else      othersRequests.add(entry);
        }

        rebuildButtons();
    }

    // ── Shared layout calculation ─────────────────────────────────────────────
    // Returns: [openFirstRowY, pendingFirstRowY, othersFirstRowY]

    private int[] computeSectionRowY() {
        int y = START_Y + TITLE_H;

        int openFirstRow = y + HDR_H;
        y = openFirstRow + Math.max(1, openRequests.size()) * ROW_H + GAP;

        int pendFirstRow = y + HDR_H;
        y = pendFirstRow + Math.max(1, pendingRequests.size()) * ROW_H + GAP;

        int othersFirstRow = y + HDR_H;

        return new int[]{openFirstRow, pendFirstRow, othersFirstRow};
    }

    // ── Buttons ───────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        super.init();
        rebuildButtons();
    }

    private void rebuildButtons() {
        clearChildren();
        if (width == 0) return;

        int[] rowY = computeSectionRowY();
        int leftX = width - MARGIN - PANEL_W;
        int btnX  = leftX + PANEL_W - BTN_W - 4;

        for (int i = 0; i < openRequests.size(); i++) {
            final int reqId = openRequests.get(i).id();
            int btnY = rowY[0] + i * ROW_H + (ROW_H - BTN_H) / 2;
            addDrawableChild(ButtonWidget.builder(Text.literal("Accept"), b ->
                    NetworkHandler.sendToServer(new AcceptVillageRequestPacket(townHallPos, reqId))
            ).dimensions(btnX, btnY, BTN_W, BTN_H).build());
        }

        for (int i = 0; i < pendingRequests.size(); i++) {
            final int reqId = pendingRequests.get(i).id();
            int btnY = rowY[1] + i * ROW_H + (ROW_H - BTN_H) / 2;
            addDrawableChild(ButtonWidget.builder(Text.literal("Fulfill"), b ->
                    NetworkHandler.sendToServer(new FulfillVillageRequestPacket(townHallPos, reqId))
            ).dimensions(btnX, btnY, BTN_W, BTN_H).build());
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> close())
                .dimensions(leftX + PANEL_W / 2 - 40, height - 26, 80, 20).build());
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // No renderBackground() — we don't want the dim overlay.
        super.render(ctx, mouseX, mouseY, delta);

        int leftX  = width - MARGIN - PANEL_W;
        int rightX = leftX + PANEL_W;
        int cx     = leftX + PANEL_W / 2;

        // Solid, opaque panel background
        ctx.fill(leftX - 4, START_Y - 4, rightX + 4, height - 28, 0xFF1A1A1A);
        // Thin border
        ctx.fill(leftX - 5, START_Y - 5, rightX + 5, START_Y - 4, 0xFF555555);
        ctx.fill(leftX - 5, height - 28, rightX + 5, height - 27, 0xFF555555);
        ctx.fill(leftX - 5, START_Y - 5, leftX - 4, height - 27, 0xFF555555);
        ctx.fill(rightX + 4, START_Y - 5, rightX + 5, height - 27, 0xFF555555);

        int y = START_Y;

        // Title (TITLE_H = 28 px consumed)
        ctx.drawCenteredTextWithShadow(textRenderer, "§6§lHelp Village", cx, y, 0xFFFFFF);
        y += 12;
        String sub = leaderName.isEmpty() ? "Village Supply Requests"
                : "Requests  |  Leader: §e" + leaderName;
        ctx.drawCenteredTextWithShadow(textRenderer, "§7" + sub, cx, y, 0xFFFFFF);
        y += 16; // total TITLE_H used = 12 + 16 = 28 ✓

        // ── Open requests ─────────────────────────────────────────────────────
        drawHeader(ctx, leftX, rightX, y, "§e§l Available Requests");
        y += HDR_H;
        if (openRequests.isEmpty()) {
            ctx.drawTextWithShadow(textRenderer, "§7No open requests at this time.", leftX + 6, y + 4, 0xFFFFFF);
            y += ROW_H;
        } else {
            for (int i = 0; i < openRequests.size(); i++) {
                drawRow(ctx, leftX, rightX, y, i, openRequests.get(i), false);
                y += ROW_H;
            }
        }
        y += GAP;

        // ── Pending (mine) ────────────────────────────────────────────────────
        drawHeader(ctx, leftX, rightX, y, "§b§l Your Active Requests");
        y += HDR_H;
        if (pendingRequests.isEmpty()) {
            ctx.drawTextWithShadow(textRenderer, "§7No accepted requests.", leftX + 6, y + 4, 0xFFFFFF);
            y += ROW_H;
        } else {
            for (int i = 0; i < pendingRequests.size(); i++) {
                drawRow(ctx, leftX, rightX, y, i, pendingRequests.get(i), true);
                y += ROW_H;
            }
        }
        y += GAP;

        // ── Others' pending ───────────────────────────────────────────────────
        if (!othersRequests.isEmpty()) {
            drawHeader(ctx, leftX, rightX, y, "§8§l In Progress (others)");
            y += HDR_H;
            for (int i = 0; i < othersRequests.size(); i++) {
                RequestEntry r = othersRequests.get(i);
                int bg = i % 2 == 0 ? 0x33333333 : 0x11333333;
                ctx.fill(leftX, y, rightX, y + ROW_H - 2, bg);
                ctx.drawTextWithShadow(textRenderer, "§7" + r.itemName(), leftX + 6, y + 4, 0xFFFFFF);
                ctx.drawTextWithShadow(textRenderer, "§8Pending", leftX + 130, y + 4, 0xFFFFFF);
                y += ROW_H;
            }
        }
    }

    private void drawHeader(DrawContext ctx, int x1, int x2, int y, String label) {
        ctx.fill(x1, y, x2, y + HDR_H - 1, 0xCC111111);
        ctx.drawTextWithShadow(textRenderer, label, x1 + 4, y + 2, 0xFFFFFF);
    }

    private void drawRow(DrawContext ctx, int leftX, int rightX, int y,
                         int idx, RequestEntry r, boolean showProgress) {
        int bg = idx % 2 == 0 ? 0x44FFAA00 : 0x22FFAA00;
        if (showProgress) bg = idx % 2 == 0 ? 0x440055AA : 0x220055AA;
        ctx.fill(leftX, y, rightX, y + ROW_H - 2, bg);
        ctx.drawTextWithShadow(textRenderer, "§f" + r.itemName(), leftX + 6, y + 4, 0xFFFFFF);

        if (showProgress) {
            // Progress bar
            int barW = 78;
            float pct = r.total() > 0 ? (float) r.fulfilled() / r.total() : 0f;
            ctx.fill(leftX + 128, y + 6, leftX + 128 + barW, y + 14, 0x55FFFFFF);
            ctx.fill(leftX + 128, y + 6, leftX + 128 + (int)(barW * pct), y + 14, 0xFF55FF55);
            ctx.drawTextWithShadow(textRenderer,
                    "§a" + r.fulfilled() + "§7/" + r.total(),
                    leftX + 212, y + 4, 0xFFFFFF);
        } else {
            ctx.drawTextWithShadow(textRenderer, "§7Need: §e" + r.total(), leftX + 130, y + 4, 0xFFFFFF);
        }
    }

    @Override
    public boolean shouldPause() { return false; }
}
