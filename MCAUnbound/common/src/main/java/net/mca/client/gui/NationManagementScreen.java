package net.mca.client.gui;

import net.mca.entity.VillagerEntityMCA;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Nation management UI.
 *
 * Main view: nation overview + map/flag placeholders.
 * Villages tab: village/villager pipeline, profile actions, overlays and village policy toggles.
 */
public class NationManagementScreen extends ExtendedScreen {

    private enum View {
        MAIN,
        VILLAGES
    }

    private View currentView = View.MAIN;

    // Mock state (replace with synced server data / packets)
    private final List<VillageEntry> villages = new ArrayList<>();
    private int selectedVillage = -1;
    private int selectedVillager = -1;

    // Overlay state for villages sub-menu
    private boolean showMoveToOverlay = false;
    private boolean showAssignOverlay = false;

    // Flag state
    private final NationFlagData flagData = new NationFlagData();

    public NationManagementScreen() {
        super(Text.literal("Nation Management"));
        seedMockData();
    }

    @Override
    protected void init() {
        super.init();
        rebuildWidgets();
    }

    private void rebuildWidgets() {
        clearChildren();

        int cx = width / 2;
        int bottomY = height - 28;

        addDrawableChild(ButtonWidget.builder(Text.literal("Exit"), b -> close())
                .dimensions(cx - 40, bottomY, 80, 20).build());

        if (currentView == View.MAIN) {
            addDrawableChild(ButtonWidget.builder(Text.literal("Villages"), b -> {
                currentView = View.VILLAGES;
                showMoveToOverlay = false;
                showAssignOverlay = false;
                rebuildWidgets();
            }).dimensions(cx - 190, bottomY - 28, 84, 20).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("Government"), b -> {})
                    .dimensions(cx - 100, bottomY - 28, 92, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Commerce"), b -> {})
                    .dimensions(cx - 2, bottomY - 28, 84, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Relations"), b -> {})
                    .dimensions(cx + 88, bottomY - 28, 84, 20).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("Expand Map"), b -> {})
                    .dimensions(cx - 6, 214, 84, 18).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Edit"), b -> openFlagEditor())
                    .dimensions(cx + 170, 186, 54, 18).build());
            return;
        }

        // Villages sub-menu widgets
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> {
            currentView = View.MAIN;
            showMoveToOverlay = false;
            showAssignOverlay = false;
            rebuildWidgets();
        }).dimensions(cx - 86, bottomY, 72, 20).build());

        VillageEntry v = getSelectedVillage();
        VillagerEntry villager = getSelectedVillager();

        addDrawableChild(ButtonWidget.builder(Text.literal("Move to"), b -> {
            if (v != null && villager != null) {
                showAssignOverlay = false;
                showMoveToOverlay = !showMoveToOverlay;
                rebuildWidgets();
            }
        }).dimensions(cx - 276, bottomY - 84, 70, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Assign"), b -> {
            if (v != null && villager != null) {
                showMoveToOverlay = false;
                showAssignOverlay = !showAssignOverlay;
                rebuildWidgets();
            }
        }).dimensions(cx - 200, bottomY - 84, 70, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Set as Capital"), b -> {
            if (v != null) {
                for (VillageEntry e : villages) e.isCapital = false;
                v.isCapital = true;
            }
        }).dimensions(cx - 6, 108, 118, 18).build());

        addDrawableChild(ButtonWidget.builder(Text.literal(v != null && v.marriageDisabled ? "Enable Marriage" : "Disable Marriage"), b -> {
            if (v != null) {
                v.marriageDisabled = !v.marriageDisabled;
                // TODO network call: persist village NBT boolean override (marriage)
                rebuildWidgets();
            }
        }).dimensions(cx - 6, 130, 118, 18).build());

        addDrawableChild(ButtonWidget.builder(Text.literal(v != null && v.popGrowthDisabled ? "Enable Pop. Growth" : "Disable Pop. Growth"), b -> {
            if (v != null) {
                v.popGrowthDisabled = !v.popGrowthDisabled;
                // TODO network call: persist village NBT boolean override (population growth)
                rebuildWidgets();
            }
        }).dimensions(cx - 6, 152, 118, 18).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Expand Border"), b -> {})
                .dimensions(cx - 6, 174, 118, 18).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Statistics"), b -> {})
                .dimensions(cx - 6, 196, 118, 18).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);

        if (currentView == View.MAIN) {
            renderMainView(context);
        } else {
            renderVillagesView(context, mouseX, mouseY);
        }

        // render widgets last so buttons stay on top/clickable
        super.render(context, mouseX, mouseY, delta);
    }

    private void renderMainView(DrawContext context) {
        int cx = width / 2;
        int panelX = cx - 210;
        int panelY = 26;
        int panelW = 420;
        int panelH = 220;

        context.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xB0101018);
        context.fill(panelX, panelY, panelX + panelW, panelY + 1, 0xFF55556A);

        context.drawTextWithShadow(textRenderer, "Nation Name: Aurora Pact", panelX + 8, panelY + 8, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, "Gov. Type: Democracy", panelX + 8, panelY + 26, 0xD7D7FF);
        context.drawTextWithShadow(textRenderer, "Capital: Barepost", panelX + 8, panelY + 44, 0xD0D0D0);
        context.drawTextWithShadow(textRenderer, "Population: " + villages.stream().mapToInt(v -> uniqueVillagers(v).size()).sum(), panelX + 8, panelY + 60, 0xD0D0D0);
        context.drawTextWithShadow(textRenderer, "Village Count: " + villages.size(), panelX + 8, panelY + 76, 0xD0D0D0);
        context.drawTextWithShadow(textRenderer, "Overall Hearts: 62", panelX + 8, panelY + 92, 0xD0D0D0);
        context.drawTextWithShadow(textRenderer, "Treasury: 1000", panelX + 8, panelY + 108, 0xD0D0D0);

        int mapX = panelX + 160;
        int mapY = panelY + 18;
        int mapW = 150;
        int mapH = 130;
        context.fill(mapX, mapY, mapX + mapW, mapY + mapH, 0xCC1A1A24);
        context.drawCenteredTextWithShadow(textRenderer, "Interactable Map", mapX + mapW / 2, mapY + 20, 0xCCCCFF);
        context.drawCenteredTextWithShadow(textRenderer, "Centered on Nation Block", mapX + mapW / 2, mapY + 36, 0xAAAAAA);
        context.drawCenteredTextWithShadow(textRenderer, "X: 124, Z: -62", mapX + mapW / 2, mapY + 50, 0xAAAAAA);

        int flagX = panelX + panelW - 84;
        int flagY = panelY + 18;
        context.fill(flagX, flagY, flagX + 60, flagY + 104, 0xCC202020);
        context.drawCenteredTextWithShadow(textRenderer, "Flag", flagX + 30, flagY + 34, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, flagData.baseColor, flagX + 30, flagY + 48, 0xDDDDDD);
        context.drawCenteredTextWithShadow(textRenderer, flagData.patternId, flagX + 30, flagY + 62, 0xDDDDDD);
    }

    private void renderVillagesView(DrawContext context, int mouseX, int mouseY) {
        int cx = width / 2;
        int baseX = cx - 380;
        int baseY = 24;

        context.fill(baseX, baseY, baseX + 760, baseY + 330, 0xB0101018);

        int listY = baseY + 8;
        int villagesX = baseX + 8;
        int villagersX = baseX + 180;

        context.fill(villagesX, listY, villagesX + 160, listY + 210, 0xAA181822);
        context.fill(villagersX, listY, villagersX + 180, listY + 210, 0xAA181822);

        context.drawTextWithShadow(textRenderer, "Villages in Nation", villagesX + 6, listY + 6, 0xFFFFFF);
        int y = listY + 22;
        for (int i = 0; i < villages.size(); i++) {
            VillageEntry v = villages.get(i);
            int bg = i == selectedVillage ? 0x774C6A4C : 0x44303038;
            context.fill(villagesX + 4, y - 2, villagesX + 154, y + 10, bg);
            context.drawTextWithShadow(textRenderer, v.name + (v.isCapital ? " (Capital)" : ""), villagesX + 8, y, 0xDCDCDC);
            y += 14;
        }

        context.drawTextWithShadow(textRenderer, "Villagers", villagersX + 6, listY + 6, 0xFFFFFF);
        VillageEntry selectedV = getSelectedVillage();
        y = listY + 22;
        if (selectedV != null) {
            List<VillagerEntry> unique = uniqueVillagers(selectedV);
            for (int i = 0; i < unique.size(); i++) {
                VillagerEntry villager = unique.get(i);
                int bg = i == selectedVillager ? 0x775A5A86 : 0x44303038;
                context.fill(villagersX + 4, y - 2, villagersX + 174, y + 10, bg);
                context.drawTextWithShadow(textRenderer, villager.name + " - " + villager.job, villagersX + 8, y, 0xDCDCDC);
                y += 14;
            }
        }

        // Profile panel
        int profileX = baseX + 8;
        int profileY = baseY + 224;
        context.fill(profileX, profileY, profileX + 460, profileY + 92, 0xAA181822);
        context.drawTextWithShadow(textRenderer, "Villager Profile Information", profileX + 8, profileY + 6, 0xFFFFFF);
        if (selectedV != null && getSelectedVillager() != null) {
            VillagerEntry p = getSelectedVillager();
            VillagerEntityMCA entity = findVillagerEntity(p.uuid);

            context.fill(profileX + 8, profileY + 20, profileX + 64, profileY + 84, 0xAA22222E);
            if (entity != null) {
                InventoryScreen.drawEntity(context, profileX + 36, profileY + 82, 28,
                        (float) (profileX + 36 - mouseX), (float) (profileY + 34 - mouseY), entity);
            } else {
                context.drawTextWithShadow(textRenderer, "NPC", profileX + 22, profileY + 50, 0xFFFFFF);
            }

            context.drawTextWithShadow(textRenderer, "Name: " + p.name, profileX + 74, profileY + 22, 0xD0D0D0);
            context.drawTextWithShadow(textRenderer, "Mood: " + p.mood, profileX + 74, profileY + 36, 0x9BCBFF);
            context.drawTextWithShadow(textRenderer, "Job: " + p.job, profileX + 74, profileY + 50, 0xD0D0D0);
            context.drawTextWithShadow(textRenderer, "Marital: " + getMaritalStatus(entity, p), profileX + 74, profileY + 64, 0xD0D0D0);
            context.drawTextWithShadow(textRenderer, String.format(Locale.ROOT, "Villager Bank: %.2f", getVillagerBank(entity, p)), profileX + 74, profileY + 78, 0xD0D0D0);

            // individual compass vertical
            context.drawTextWithShadow(textRenderer, "Compass:", profileX + 268, profileY + 22, 0xD0D0D0);
            context.drawTextWithShadow(textRenderer, String.format(Locale.ROOT, "N: %.2f", p.n), profileX + 268, profileY + 36, 0xD0D0D0);
            context.drawTextWithShadow(textRenderer, String.format(Locale.ROOT, "C: %.2f", p.c), profileX + 268, profileY + 48, 0xD0D0D0);
            context.drawTextWithShadow(textRenderer, String.format(Locale.ROOT, "A: %.2f", p.a), profileX + 268, profileY + 60, 0xD0D0D0);
            context.drawTextWithShadow(textRenderer, String.format(Locale.ROOT, "L: %.2f", p.l), profileX + 268, profileY + 72, 0xD0D0D0);
        }

        // Stats + actions panel
        int statsX = baseX + 370;
        int statsY = baseY + 8;
        context.fill(statsX, statsY, statsX + 250, statsY + 210, 0xAA181822);
        if (selectedV != null) {
            context.drawTextWithShadow(textRenderer, "Village: " + selectedV.name, statsX + 8, statsY + 10, 0xFFFFFF);
            context.drawTextWithShadow(textRenderer, "Pop: " + uniqueVillagers(selectedV).size(), statsX + 8, statsY + 24, 0xD0D0D0);
            context.drawTextWithShadow(textRenderer, "Hearts: " + selectedV.hearts, statsX + 8, statsY + 38, 0xD0D0D0);
            context.drawTextWithShadow(textRenderer, "Treasury: " + selectedV.treasury, statsX + 8, statsY + 52, 0xD0D0D0);
            context.drawTextWithShadow(textRenderer, "National Favor: " + selectedV.nationalFavor, statsX + 8, statsY + 66, 0xD0D0D0);

            // political compass vertical list (cleanup)
            context.drawTextWithShadow(textRenderer, "Political Compass:", statsX + 8, statsY + 80, 0xD0D0D0);
            context.drawTextWithShadow(textRenderer, String.format(Locale.ROOT, "N: %.2f", selectedV.n), statsX + 8, statsY + 94, 0xD0D0D0);
            context.drawTextWithShadow(textRenderer, String.format(Locale.ROOT, "C: %.2f", selectedV.c), statsX + 8, statsY + 106, 0xD0D0D0);
            context.drawTextWithShadow(textRenderer, String.format(Locale.ROOT, "A: %.2f", selectedV.a), statsX + 8, statsY + 118, 0xD0D0D0);
            context.drawTextWithShadow(textRenderer, String.format(Locale.ROOT, "L: %.2f", selectedV.l), statsX + 8, statsY + 130, 0xD0D0D0);
        }

        renderOverlays(context, baseX, baseY);
    }

    private void renderOverlays(DrawContext context, int baseX, int baseY) {
        VillageEntry selectedV = getSelectedVillage();
        VillagerEntry selected = getSelectedVillager();
        if (selectedV == null || selected == null) return;

        if (showMoveToOverlay) {
            int x = baseX + 92;
            int y = baseY + 278;
            context.fill(x, y, x + 224, y + 136, 0xE0151520);
            context.drawTextWithShadow(textRenderer, "Villages with available beds", x + 8, y + 8, 0xFFFFFF);
            int rowY = y + 24;
            for (VillageEntry v : villages) {
                if (v.availableBeds <= 0) continue;
                context.fill(x + 8, rowY - 1, x + 216, rowY + 11, 0x4440404A);
                context.drawTextWithShadow(textRenderer, v.name + " (beds: " + v.availableBeds + ")", x + 12, rowY, 0xD0D0D0);
                rowY += 14;
                if (rowY > y + 120) break;
            }
        }

        if (showAssignOverlay) {
            int x = baseX + 258;
            int y = baseY + 278;
            context.fill(x, y, x + 172, y + 136, 0xE0151520);
            context.drawTextWithShadow(textRenderer, "Available Jobs", x + 8, y + 8, 0xFFFFFF);
            int rowY = y + 24;
            for (String job : getAvailableProfessionIds()) {
                context.fill(x + 8, rowY - 1, x + 164, rowY + 11, 0x4440404A);
                context.drawTextWithShadow(textRenderer, humanizeJob(job), x + 12, rowY, 0xD0D0D0);
                rowY += 14;
                if (rowY > y + 120) break;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (currentView == View.VILLAGES) {
            int cx = width / 2;
            int baseX = cx - 380;
            int baseY = 24;
            int listY = baseY + 8;

            // villages list selection
            if (mouseX >= baseX + 12 && mouseX <= baseX + 162 && mouseY >= listY + 20 && mouseY <= listY + 210) {
                int idx = ((int) mouseY - (listY + 20)) / 14;
                if (idx >= 0 && idx < villages.size()) {
                    selectedVillage = idx;
                    selectedVillager = -1;
                    showMoveToOverlay = false;
                    showAssignOverlay = false;
                    rebuildWidgets();
                    return true;
                }
            }

            // villagers list selection
            VillageEntry v = getSelectedVillage();
            if (v != null && mouseX >= baseX + 184 && mouseX <= baseX + 354 && mouseY >= listY + 20 && mouseY <= listY + 210) {
                List<VillagerEntry> unique = uniqueVillagers(v);
                int idx = ((int) mouseY - (listY + 20)) / 14;
                if (idx >= 0 && idx < unique.size()) {
                    selectedVillager = idx;
                    showMoveToOverlay = false;
                    showAssignOverlay = false;
                    rebuildWidgets();
                    return true;
                }
            }

            if (handleOverlayClick(mouseX, mouseY)) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleOverlayClick(double mouseX, double mouseY) {
        int cx = width / 2;
        int baseX = cx - 380;
        int baseY = 24;

        VillageEntry selectedVillage = getSelectedVillage();
        VillagerEntry selected = getSelectedVillager();
        if (selectedVillage == null || selected == null) return false;

        if (showMoveToOverlay) {
            int x = baseX + 92;
            int y = baseY + 278;
            int rowY = y + 24;
            for (VillageEntry v : villages) {
                if (v.availableBeds <= 0) continue;
                if (mouseX >= x + 8 && mouseX <= x + 216 && mouseY >= rowY - 1 && mouseY <= rowY + 11) {
                    // TODO network call: update selected villager home to target village center NBT
                    showMoveToOverlay = false;
                    refreshWidgets();
                    return true;
                }
                rowY += 14;
                if (rowY > y + 120) break;
            }
        }

        if (showAssignOverlay) {
            int x = baseX + 258;
            int y = baseY + 278;
            int rowY = y + 24;
            List<String> jobs = getAvailableProfessionIds();
            for (String jobId : jobs) {
                if (mouseX >= x + 8 && mouseX <= x + 164 && mouseY >= rowY - 1 && mouseY <= rowY + 11) {
                    applyProfessionToSelectedVillager(jobId);
                    showAssignOverlay = false;
                    refreshWidgets();
                    return true;
                }
                rowY += 14;
                if (rowY > y + 120) break;
            }
        }

        return false;
    }

    private void refreshWidgets() {
        rebuildWidgets();
    }

    private VillageEntry getSelectedVillage() {
        return selectedVillage >= 0 && selectedVillage < villages.size() ? villages.get(selectedVillage) : null;
    }

    private VillagerEntry getSelectedVillager() {
        VillageEntry v = getSelectedVillage();
        if (v == null) return null;
        List<VillagerEntry> unique = uniqueVillagers(v);
        return selectedVillager >= 0 && selectedVillager < unique.size() ? unique.get(selectedVillager) : null;
    }

    private List<VillagerEntry> uniqueVillagers(VillageEntry village) {
        Map<UUID, VillagerEntry> byUuid = new LinkedHashMap<>();
        for (VillagerEntry e : village.villagers) {
            byUuid.putIfAbsent(e.uuid, e);
        }
        return new ArrayList<>(byUuid.values());
    }

    private void applyProfessionToSelectedVillager(String jobId) {
        VillagerEntry selected = getSelectedVillager();
        if (selected == null) return;

        VillagerEntityMCA entity = findVillagerEntity(selected.uuid);
        if (entity != null) {
            Identifier id = new Identifier(jobId);
            var profession = Registries.VILLAGER_PROFESSION.get(id);
            if (profession != null) {
                entity.setProfession(profession);
            }
        }

        VillageEntry village = getSelectedVillage();
        if (village != null) {
            for (int i = 0; i < village.villagers.size(); i++) {
                VillagerEntry e = village.villagers.get(i);
                if (e.uuid.equals(selected.uuid)) {
                    village.villagers.set(i, new VillagerEntry(e.uuid, e.name, e.mood, humanizeJob(jobId), e.n, e.c, e.a, e.l, e.maritalStatus, e.bankBalance));
                }
            }
        }
    }

    private List<String> getAvailableProfessionIds() {
        return Registries.VILLAGER_PROFESSION.stream()
                .map(Registries.VILLAGER_PROFESSION::getId)
                .filter(Objects::nonNull)
                .map(Identifier::toString)
                .filter(id -> !id.endsWith(":none") && !id.endsWith(":nitwit"))
                .sorted()
                .collect(Collectors.toList());
    }

    private String humanizeJob(String rawJob) {
        String clean = rawJob;
        int dot = clean.lastIndexOf('.');
        if (dot >= 0 && dot < clean.length() - 1) clean = clean.substring(dot + 1);
        int colon = clean.lastIndexOf(':');
        if (colon >= 0 && colon < clean.length() - 1) clean = clean.substring(colon + 1);
        clean = clean.replace('_', ' ');
        if (clean.isBlank()) return "None";
        StringBuilder sb = new StringBuilder(clean.length());
        boolean cap = true;
        for (char c : clean.toCharArray()) {
            if (c == ' ') { sb.append(c); cap = true; }
            else if (cap) { sb.append(Character.toUpperCase(c)); cap = false; }
            else sb.append(c);
        }
        return sb.toString();
    }

    private VillagerEntityMCA findVillagerEntity(UUID villagerUuid) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return null;
        Box box = client.player.getBoundingBox().expand(256);
        List<VillagerEntityMCA> list = client.world.getEntitiesByClass(VillagerEntityMCA.class, box, v -> v.getUuid().equals(villagerUuid));
        return list.isEmpty() ? null : list.get(0);
    }

    private String getMaritalStatus(VillagerEntityMCA entity, VillagerEntry fallback) {
        if (entity == null) return fallback.maritalStatus;
        if (!entity.getRelationships().isMarried()) return "Single";
        return entity.getRelationships().getPartnerName().map(t -> "Married to " + t.getString()).orElse("Married");
    }

    private double getVillagerBank(VillagerEntityMCA entity, VillagerEntry fallback) {
        if (entity == null) return fallback.bankBalance;
        return entity.getVillagerBankBalance();
    }

    private void openFlagEditor() {
        client.setScreen(new FlagEditorScreen(this, flagData));
    }

    private void seedMockData() {
        VillageEntry a = new VillageEntry(UUID.fromString("11111111-1111-1111-1111-111111111111"), "Barepost", 14, 52, 500, 61, 0.46, 0.28, 0.53, 0.37, 3);
        a.villagers.add(new VillagerEntry(UUID.fromString("aaaaaaaa-1111-1111-1111-111111111111"), "Rolf", "Passive", "Guard", 0.41, 0.24, 0.58, 0.34, "Single", 50.50));
        a.villagers.add(new VillagerEntry(UUID.fromString("bbbbbbbb-1111-1111-1111-111111111111"), "Peder", "Happy", "Archer", 0.36, 0.42, 0.31, 0.66, "Married to Anja", 73.25));
        a.villagers.add(new VillagerEntry(UUID.fromString("cccccccc-1111-1111-1111-111111111111"), "Marina", "Sad", "Farmer", 0.51, 0.21, 0.47, 0.32, "Single", 10.00));
        a.isCapital = true;

        VillageEntry b = new VillageEntry(UUID.fromString("22222222-2222-2222-2222-222222222222"), "Grayyard", 9, 44, 500, 49, 0.35, 0.41, 0.47, 0.51, 1);
        b.villagers.add(new VillagerEntry(UUID.fromString("dddddddd-2222-2222-2222-222222222222"), "Anja", "Fine", "None", 0.38, 0.39, 0.44, 0.52, "Single", 22.75));
        b.villagers.add(new VillagerEntry(UUID.fromString("eeeeeeee-2222-2222-2222-222222222222"), "Jorn", "Depressed", "Blacksmith", 0.42, 0.31, 0.48, 0.53, "Married to Peder", 105.10));

        Map<UUID, VillageEntry> dedup = new LinkedHashMap<>();
        dedup.putIfAbsent(a.uuid, a);
        dedup.putIfAbsent(b.uuid, b);
        villages.clear();
        villages.addAll(dedup.values());
    }

    private static class VillageEntry {
        private final UUID uuid;
        private final String name;
        private final int population;
        private final int hearts;
        private final int treasury;
        private final int nationalFavor;
        private final double n;
        private final double c;
        private final double a;
        private final double l;
        private final int availableBeds;

        private boolean isCapital;
        private boolean marriageDisabled;
        private boolean popGrowthDisabled;

        private final List<VillagerEntry> villagers = new ArrayList<>();

        private VillageEntry(UUID uuid, String name, int population, int hearts, int treasury, int nationalFavor, double n, double c, double a, double l, int availableBeds) {
            this.uuid = uuid;
            this.name = name;
            this.population = population;
            this.hearts = hearts;
            this.treasury = treasury;
            this.nationalFavor = nationalFavor;
            this.n = n;
            this.c = c;
            this.a = a;
            this.l = l;
            this.availableBeds = availableBeds;
        }
    }

    private record VillagerEntry(UUID uuid, String name, String mood, String job,
                                 double n, double c, double a, double l,
                                 String maritalStatus, double bankBalance) {
    }

    private static class NationFlagData {
        private String baseColor = "Blue";
        private String patternId = "base";
        private final List<String> layers = new ArrayList<>();
    }

    private static class FlagEditorScreen extends ExtendedScreen {
        private final NationManagementScreen parent;
        private final NationFlagData flag;
        private final Random random = new Random();
        private int r = 64;
        private int g = 90;
        private int b = 180;

        private FlagEditorScreen(NationManagementScreen parent, NationFlagData flag) {
            super(Text.literal("Flag Editor"));
            this.parent = parent;
            this.flag = flag;
        }

        @Override
        protected void init() {
            super.init();
            int cx = width / 2;
            int y = height / 2 + 40;
            addDrawableChild(ButtonWidget.builder(Text.literal("Randomize"), b -> randomizeFlag())
                    .dimensions(cx - 130, y, 80, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("+ Layer"), b -> addLayer())
                    .dimensions(cx - 44, y, 72, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Save"), b -> client.setScreen(parent))
                    .dimensions(cx + 34, y, 60, 20).build());
        }

        private void randomizeFlag() {
            List<Identifier> patterns = Registries.BANNER_PATTERN.getIds().stream().toList();
            if (!patterns.isEmpty()) {
                flag.patternId = patterns.get(random.nextInt(patterns.size())).toString();
            }
            String[] colors = {"Red", "Blue", "Green", "Yellow", "Black", "White", "Purple", "Cyan"};
            flag.baseColor = colors[random.nextInt(colors.length)];
            r = random.nextInt(256);
            g = random.nextInt(256);
            b = random.nextInt(256);
        }

        private void addLayer() {
            flag.layers.add("Layer " + (flag.layers.size() + 1) + " - #" + String.format("%02X%02X%02X", r, g, b));
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            renderBackground(context);
            super.render(context, mouseX, mouseY, delta);
            int cx = width / 2;
            int top = height / 2 - 110;
            context.fill(cx - 160, top, cx + 160, top + 220, 0xB0101018);
            context.drawCenteredTextWithShadow(textRenderer, "Nation Flag Editor", cx, top + 8, 0xFFFFFF);
            context.drawTextWithShadow(textRenderer, "Pattern: " + flag.patternId, cx - 148, top + 28, 0xD0D0D0);
            context.drawTextWithShadow(textRenderer, "Base Color: " + flag.baseColor, cx - 148, top + 44, 0xD0D0D0);

            context.drawTextWithShadow(textRenderer, "Color Picker (R/G/B)", cx - 148, top + 68, 0xD0D0D0);
            context.fill(cx - 148, top + 82, cx + 148, top + 102, (0xFF << 24) | (r << 16) | (g << 8) | b);
            context.drawTextWithShadow(textRenderer, "R:" + r + "  G:" + g + "  B:" + b, cx - 148, top + 108, 0xD0D0D0);

            context.drawTextWithShadow(textRenderer, "Layers:", cx - 148, top + 128, 0xD0D0D0);
            int y = top + 142;
            if (flag.layers.isEmpty()) {
                context.drawTextWithShadow(textRenderer, "(No layers)", cx - 148, y, 0x888888);
            } else {
                for (String layer : flag.layers) {
                    context.drawTextWithShadow(textRenderer, layer, cx - 148, y, 0xD0D0D0);
                    y += 12;
                    if (y > top + 196) break;
                }
            }
        }
    }
}
