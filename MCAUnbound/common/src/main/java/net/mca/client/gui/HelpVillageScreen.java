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
 * Help Village screen — shown when the player clicks "Help Village" on the NPC leader.
 *
 * <ul>
 *   <li>Open requests: each shows item name, amount needed, and an [Accept] button</li>
 *   <li>Pending requests accepted by this player: show remaining/total and [Fulfill] button</li>
 *   <li>Pending requests accepted by others: show "Pending" label (no action)</li>
 * </ul>
 */
public class HelpVillageScreen extends ExtendedScreen {

    // ── Data ────────────────────────────────────────────────────────────────────
    private BlockPos townHallPos = BlockPos.ORIGIN;
    private String leaderName   = "";

    /** [id, itemName, totalAmount, fulfilledAmount, category] */
    private final List<int[]>    openIds        = new ArrayList<>();
    private final List<String[]> openEntries    = new ArrayList<>(); // [itemName, amountStr]
    private final List<int[]>    pendingMyIds   = new ArrayList<>();
    private final List<String[]> pendingMyEntries = new ArrayList<>(); // [itemName, remainStr]
    private final List<String[]> pendingOtherEntries = new ArrayList<>();

    // ── Layout ──────────────────────────────────────────────────────────────────
    private static final int ROW_H = 22;

    public HelpVillageScreen() {
        super(Text.translatable("gui.mca.help_village.title"));
    }

    public void loadData(HelpVillageDataResponse response) {
        NbtCompound root = response.getData();
        townHallPos = BlockPos.fromLong(root.getLong("blockPos"));
        leaderName  = root.getString("leaderName");

        openIds.clear();
        openEntries.clear();
        pendingMyIds.clear();
        pendingMyEntries.clear();
        pendingOtherEntries.clear();

        NbtList openList = root.getList("openRequests", 10);
        for (int i = 0; i < openList.size(); i++) {
            NbtCompound e = openList.getCompound(i);
            int id     = e.getInt("id");
            String name = e.getString("itemName");
            int total  = e.getInt("total");
            openIds.add(new int[]{id});
            openEntries.add(new String[]{name, "Need: " + total});
        }

        NbtList pendingList = root.getList("pendingRequests", 10);
        for (int i = 0; i < pendingList.size(); i++) {
            NbtCompound e    = pendingList.getCompound(i);
            int id           = e.getInt("id");
            String name      = e.getString("itemName");
            int total        = e.getInt("total");
            int fulfilled    = e.getInt("fulfilled");
            int remaining    = Math.max(0, total - fulfilled);
            boolean mine     = e.getBoolean("mine");
            if (mine) {
                pendingMyIds.add(new int[]{id});
                pendingMyEntries.add(new String[]{name, remaining + "/" + total + " remaining"});
            } else {
                pendingOtherEntries.add(new String[]{name, "Pending"});
            }
        }

        rebuildButtons();
    }

    // ── Buttons ──────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        super.init();
        rebuildButtons();
    }

    private void rebuildButtons() {
        clearChildren();

        int cx    = width / 2;
        int baseY = 60;
        int leftX = cx - 130;

        // ── Open requests ──────────────────────────────────────────────────────
        int y = baseY + 20; // skip header row
        for (int i = 0; i < openEntries.size(); i++) {
            final int reqId = openIds.get(i)[0];
            addDrawableChild(ButtonWidget.builder(Text.literal("Accept"), b -> {
                NetworkHandler.sendToServer(new AcceptVillageRequestPacket(townHallPos, reqId));
            }).dimensions(leftX + 220, y + i * ROW_H, 50, 16).build());
        }

        // ── Pending (mine) requests ────────────────────────────────────────────
        int pendingBase = baseY + 20 + openEntries.size() * ROW_H + 30;
        for (int i = 0; i < pendingMyEntries.size(); i++) {
            final int reqId = pendingMyIds.get(i)[0];
            addDrawableChild(ButtonWidget.builder(Text.literal("Fulfill"), b -> {
                NetworkHandler.sendToServer(new FulfillVillageRequestPacket(townHallPos, reqId));
            }).dimensions(leftX + 220, pendingBase + i * ROW_H, 50, 16).build());
        }

        // ── Close ─────────────────────────────────────────────────────────────
        int bottomY = height - 28;
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> close())
                .dimensions(cx - 40, bottomY, 80, 20).build());
    }

    // ── Rendering ────────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx);
        super.render(ctx, mouseX, mouseY, delta);

        int cx    = width / 2;
        int leftX = cx - 130;
        int y     = 12;

        // Title
        ctx.drawCenteredTextWithShadow(textRenderer, "§6§lHelp Village — §r§6" + leaderName, cx, y, 0xFFFFFF);
        y += 18;

        ctx.drawCenteredTextWithShadow(textRenderer, "§7Support the village by fulfilling supply requests.", cx, y, 0xFFFFFF);
        y += 16;

        // ── Open requests ─────────────────────────────────────────────────────
        drawSectionHeader(ctx, leftX, y, "§e§lAvailable Requests");
        y += 14;

        if (openEntries.isEmpty()) {
            ctx.drawTextWithShadow(textRenderer, "§7No open requests right now.", leftX + 4, y + 2, 0xFFFFFF);
            y += ROW_H;
        } else {
            for (int i = 0; i < openEntries.size(); i++) {
                String[] e = openEntries.get(i);
                int bg = i % 2 == 0 ? 0x44000000 : 0x22000000;
                ctx.fill(leftX - 2, y - 1, leftX + 278, y + ROW_H - 3, bg);
                ctx.drawTextWithShadow(textRenderer, "§f" + e[0], leftX + 2, y + 2, 0xFFFFFF);
                ctx.drawTextWithShadow(textRenderer, "§7" + e[1], leftX + 130, y + 2, 0xFFFFFF);
                y += ROW_H;
            }
        }

        y += 10;

        // ── Pending: mine ─────────────────────────────────────────────────────
        drawSectionHeader(ctx, leftX, y, "§b§lYour Active Requests");
        y += 14;

        if (pendingMyEntries.isEmpty()) {
            ctx.drawTextWithShadow(textRenderer, "§7You have no accepted requests.", leftX + 4, y + 2, 0xFFFFFF);
            y += ROW_H;
        } else {
            for (int i = 0; i < pendingMyEntries.size(); i++) {
                String[] e = pendingMyEntries.get(i);
                int bg = i % 2 == 0 ? 0x44001133 : 0x22001133;
                ctx.fill(leftX - 2, y - 1, leftX + 278, y + ROW_H - 3, bg);
                ctx.drawTextWithShadow(textRenderer, "§f" + e[0], leftX + 2, y + 2, 0xFFFFFF);
                ctx.drawTextWithShadow(textRenderer, "§a" + e[1], leftX + 130, y + 2, 0xFFFFFF);
                y += ROW_H;
            }
        }

        y += 10;

        // ── Pending: others ───────────────────────────────────────────────────
        if (!pendingOtherEntries.isEmpty()) {
            drawSectionHeader(ctx, leftX, y, "§7§lOther Pending");
            y += 14;
            for (int i = 0; i < pendingOtherEntries.size(); i++) {
                String[] e = pendingOtherEntries.get(i);
                int bg = i % 2 == 0 ? 0x33000000 : 0x11000000;
                ctx.fill(leftX - 2, y - 1, leftX + 278, y + ROW_H - 3, bg);
                ctx.drawTextWithShadow(textRenderer, "§7" + e[0], leftX + 2, y + 2, 0xFFFFFF);
                ctx.drawTextWithShadow(textRenderer, "§7Pending", leftX + 130, y + 2, 0xFFFFFF);
                y += ROW_H;
            }
        }
    }

    private void drawSectionHeader(DrawContext ctx, int x, int y, String label) {
        ctx.fill(x - 2, y - 1, x + 278, y + 11, 0x99000000);
        ctx.drawTextWithShadow(textRenderer, label, x + 2, y + 1, 0xFFFFFF);
    }

    @Override
    public boolean shouldPause() { return false; }
}
