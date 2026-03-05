package net.mca.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

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
            addDrawableChild(ButtonWidget.builder(Text.literal("Edit"), b -> {})
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
        }).dimensions(cx - 6, 82, 118, 18).build());

        addDrawableChild(ButtonWidget.builder(Text.literal(v != null && v.marriageDisabled ? "Enable Marriage" : "Disable Marriage"), b -> {
            if (v != null) {
                v.marriageDisabled = !v.marriageDisabled;
                // TODO network call: persist village NBT boolean override (marriage)
                rebuildWidgets();
            }
        }).dimensions(cx - 6, 104, 118, 18).build());

        addDrawableChild(ButtonWidget.builder(Text.literal(v != null && v.popGrowthDisabled ? "Enable Pop. Growth" : "Disable Pop. Growth"), b -> {
            if (v != null) {
                v.popGrowthDisabled = !v.popGrowthDisabled;
                // TODO network call: persist village NBT boolean override (population growth)
                rebuildWidgets();
            }
        }).dimensions(cx - 6, 126, 118, 18).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Expand Border"), b -> {})
                .dimensions(cx - 6, 148, 118, 18).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Statistics"), b -> {})
                .dimensions(cx - 6, 170, 118, 18).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        super.render(context, mouseX, mouseY, delta);

        if (currentView == View.MAIN) {
            renderMainView(context);
        } else {
            renderVillagesView(context);
        }
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
        context.drawTextWithShadow(textRenderer, "Population: 37", panelX + 8, panelY + 60, 0xD0D0D0);
        context.drawTextWithShadow(textRenderer, "Village Count: " + villages.size(), panelX + 8, panelY + 76, 0xD0D0D0);
        context.drawTextWithShadow(textRenderer, "Overall Hearts: 62", panelX + 8, panelY + 92, 0xD0D0D0);
        context.drawTextWithShadow(textRenderer, "Treasury: 1248", panelX + 8, panelY + 108, 0xD0D0D0);

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
        context.drawCenteredTextWithShadow(textRenderer, "Flag", flagX + 30, flagY + 46, 0xFFFFFF);
    }

    private void renderVillagesView(DrawContext context) {
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
            for (int i = 0; i < selectedV.villagers.size(); i++) {
                VillagerEntry villager = selectedV.villagers.get(i);
                int bg = i == selectedVillager ? 0x775A5A86 : 0x44303038;
                context.fill(villagersX + 4, y - 2, villagersX + 174, y + 10, bg);
                context.drawTextWithShadow(textRenderer, villager.name + " - " + villager.job, villagersX + 8, y, 0xDCDCDC);
                y += 14;
            }
        }

        // Profile panel
        int profileX = baseX + 8;
        int profileY = baseY + 224;
        context.fill(profileX, profileY, profileX + 352, profileY + 92, 0xAA181822);
        context.drawTextWithShadow(textRenderer, "Villager Profile Information", profileX + 8, profileY + 6, 0xFFFFFF);
        if (selectedV != null && getSelectedVillager() != null) {
            VillagerEntry p = getSelectedVillager();
            context.fill(profileX + 8, profileY + 20, profileX + 44, profileY + 78, 0xFF2C2C38);
            context.drawTextWithShadow(textRenderer, "NPC", profileX + 14, profileY + 44, 0xFFFFFF);
            context.drawTextWithShadow(textRenderer, "Name: " + p.name, profileX + 54, profileY + 26, 0xD0D0D0);
            context.drawTextWithShadow(textRenderer, "Mood: " + p.mood, profileX + 54, profileY + 42, 0x9BCBFF);
            context.drawTextWithShadow(textRenderer, "Job: " + p.job, profileX + 54, profileY + 58, 0xD0D0D0);
        }

        // Stats + actions panel
        int statsX = baseX + 370;
        int statsY = baseY + 8;
        context.fill(statsX, statsY, statsX + 250, statsY + 210, 0xAA181822);
        if (selectedV != null) {
            context.drawTextWithShadow(textRenderer, "Village: " + selectedV.name, statsX + 8, statsY + 10, 0xFFFFFF);
            context.drawTextWithShadow(textRenderer, "Pop: " + selectedV.population, statsX + 8, statsY + 26, 0xD0D0D0);
            context.drawTextWithShadow(textRenderer, "Hearts: " + selectedV.hearts, statsX + 8, statsY + 42, 0xD0D0D0);
            context.drawTextWithShadow(textRenderer, "Treasury: " + selectedV.treasury, statsX + 8, statsY + 58, 0xD0D0D0);
            context.drawTextWithShadow(textRenderer, "National Favor: " + selectedV.nationalFavor, statsX + 8, statsY + 74, 0xD0D0D0);
            context.drawTextWithShadow(textRenderer, "Political Compass: " + selectedV.politicalCompass, statsX + 8, statsY + 90, 0xD0D0D0);
        }

        // Policies columns
        int polX = baseX + 628;
        context.fill(polX, statsY, polX + 124, statsY + 100, 0xAA181822);
        context.fill(polX, statsY + 110, polX + 124, statsY + 210, 0xAA181822);
        context.drawTextWithShadow(textRenderer, "Not Enacted", polX + 6, statsY + 8, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, "Enacted", polX + 6, statsY + 118, 0xFFFFFF);

        if (selectedV != null) {
            int py = statsY + 24;
            for (String p : selectedV.notEnactedPolicies) {
                context.drawTextWithShadow(textRenderer, "- " + p, polX + 6, py, 0xD0D0D0);
                py += 12;
                if (py > statsY + 92) break;
            }
            py = statsY + 134;
            for (String p : selectedV.enactedPolicies) {
                context.drawTextWithShadow(textRenderer, "- " + p, polX + 6, py, 0xD0D0D0);
                py += 12;
                if (py > statsY + 202) break;
            }
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
            String[] jobs = {"Guard", "Farmer", "Archer", "Blacksmith", "Trader", "Librarian"};
            for (String job : jobs) {
                context.fill(x + 8, rowY - 1, x + 164, rowY + 11, 0x4440404A);
                context.drawTextWithShadow(textRenderer, job, x + 12, rowY, 0xD0D0D0);
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
                int idx = ((int) mouseY - (listY + 20)) / 14;
                if (idx >= 0 && idx < v.villagers.size()) {
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
            String[] jobs = {"Guard", "Farmer", "Archer", "Blacksmith", "Trader", "Librarian"};
            for (String ignored : jobs) {
                if (mouseX >= x + 8 && mouseX <= x + 164 && mouseY >= rowY - 1 && mouseY <= rowY + 11) {
                    // TODO network call: assign selected villager job
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
        return selectedVillager >= 0 && selectedVillager < v.villagers.size() ? v.villagers.get(selectedVillager) : null;
    }

    private void seedMockData() {
        VillageEntry a = new VillageEntry("Barepost", 14, 52, 420, 61, "N:0.46 C:0.28 A:0.53 L:0.37", 3);
        a.villagers.add(new VillagerEntry("Rolf", "Passive", "Guard"));
        a.villagers.add(new VillagerEntry("Peder", "Happy", "Archer"));
        a.villagers.add(new VillagerEntry("Marina", "Sad", "Farmer"));
        a.notEnactedPolicies.add("Citizen Dividend");
        a.notEnactedPolicies.add("Open Market Charter");
        a.enactedPolicies.add("Village Militia");
        a.enactedPolicies.add("Granary Support");
        a.isCapital = true;

        VillageEntry b = new VillageEntry("Grayyard", 9, 44, 300, 49, "N:0.35 C:0.41 A:0.47 L:0.51", 1);
        b.villagers.add(new VillagerEntry("Anja", "Fine", "None"));
        b.villagers.add(new VillagerEntry("Jorn", "Depressed", "Blacksmith"));
        b.notEnactedPolicies.add("Border Watch");
        b.enactedPolicies.add("Road Tax");

        villages.add(a);
        villages.add(b);
    }

    private static class VillageEntry {
        private final String name;
        private final int population;
        private final int hearts;
        private final int treasury;
        private final int nationalFavor;
        private final String politicalCompass;
        private final int availableBeds;

        private boolean isCapital;
        private boolean marriageDisabled;
        private boolean popGrowthDisabled;

        private final List<VillagerEntry> villagers = new ArrayList<>();
        private final List<String> notEnactedPolicies = new ArrayList<>();
        private final List<String> enactedPolicies = new ArrayList<>();

        private VillageEntry(String name, int population, int hearts, int treasury, int nationalFavor, String politicalCompass, int availableBeds) {
            this.name = name;
            this.population = population;
            this.hearts = hearts;
            this.treasury = treasury;
            this.nationalFavor = nationalFavor;
            this.politicalCompass = politicalCompass;
            this.availableBeds = availableBeds;
        }
    }

    private record VillagerEntry(String name, String mood, String job) {
    }
}
