package net.mca.client.gui;

import net.mca.MCA;
import net.mca.entity.VillagerEntityMCA;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class NationManagementScreen extends ExtendedScreen {
    private enum View { MAIN, VILLAGES }

    private View currentView = View.MAIN;
    private final List<VillageEntry> villages = new ArrayList<>();
    private int selectedVillage = -1;
    private int selectedVillager = -1;
    private boolean showMoveToOverlay = false;
    private boolean showAssignOverlay = false;
    private final NationFlagData flagData = new NationFlagData();

    private final boolean devMode = SharedConstants.isDevelopment;
    private boolean designMode = false;
    private String draggingKey;
    private int dragOffsetX;
    private int dragOffsetY;
    private Map<String, NationLayoutConfig.LayoutRect> layout = new LinkedHashMap<>();

    public NationManagementScreen() {
        super(Text.literal("Nation Management"));
        seedMockData();
    }

    @Override
    protected void init() {
        super.init();
        reloadLayout();
        rebuildWidgets();
    }

    private void reloadLayout() {
        layout = NationLayoutConfig.loadOrCreateDefaults(width, height);
    }

    private void rebuildWidgets() {
        clearChildren();
        int cx = width / 2;
        int bottomY = height - 28;

        addDrawableChild(ButtonWidget.builder(Text.literal("Exit"), b -> close()).dimensions(cx - 40, bottomY, 80, 20).build());
        if (devMode) {
            addDrawableChild(ButtonWidget.builder(Text.literal("Reload Layout"), b -> {
                reloadLayout();
                refreshWidgets();
            }).dimensions(cx + 50, bottomY, 110, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Design: " + (designMode ? "ON" : "OFF")), b -> {
                designMode = !designMode;
                refreshWidgets();
            }).dimensions(cx - 170, bottomY, 120, 20).build());
        }

        if (currentView == View.MAIN) {
            addDrawableChild(ButtonWidget.builder(Text.literal("Villages"), b -> {
                currentView = View.VILLAGES;
                showMoveToOverlay = false;
                showAssignOverlay = false;
                refreshWidgets();
            }).dimensions(cx - 190, bottomY - 28, 84, 20).build());

            NationLayoutConfig.LayoutRect action = layout.get("ActionButtons");
            addDrawableChild(ButtonWidget.builder(Text.literal("Expand Map"), b -> {})
                    .dimensions(action.x + 80, action.y + 10, 84, 18).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Edit"), b -> openFlagEditor())
                    .dimensions(action.x + 170, action.y + 10, 54, 18).build());
            return;
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> {
            currentView = View.MAIN;
            showMoveToOverlay = false;
            showAssignOverlay = false;
            refreshWidgets();
        }).dimensions(cx - 86, bottomY, 72, 20).build());

        VillageEntry v = getSelectedVillage();
        VillagerEntry villager = getSelectedVillager();
        NationLayoutConfig.LayoutRect action = layout.get("ActionButtons");

        addDrawableChild(ButtonWidget.builder(Text.literal("Move to"), b -> {
            if (v != null && villager != null) {
                showAssignOverlay = false;
                showMoveToOverlay = !showMoveToOverlay;
                refreshWidgets();
            }
        }).dimensions(action.x, action.y, 70, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Assign"), b -> {
            if (v != null && villager != null) {
                showMoveToOverlay = false;
                showAssignOverlay = !showAssignOverlay;
                refreshWidgets();
            }
        }).dimensions(action.x + 76, action.y, 70, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Set as Capital"), b -> {
            if (v != null) {
                for (VillageEntry e : villages) e.isCapital = false;
                v.isCapital = true;
            }
        }).dimensions(action.x + 152, action.y, 118, 18).build());

        addDrawableChild(ButtonWidget.builder(Text.literal(v != null && v.marriageDisabled ? "Enable Marriage" : "Disable Marriage"), b -> {
            if (v != null) {
                v.marriageDisabled = !v.marriageDisabled;
                refreshWidgets();
            }
        }).dimensions(action.x + 152, action.y + 22, 118, 18).build());

        addDrawableChild(ButtonWidget.builder(Text.literal(v != null && v.popGrowthDisabled ? "Enable Pop. Growth" : "Disable Pop. Growth"), b -> {
            if (v != null) {
                v.popGrowthDisabled = !v.popGrowthDisabled;
                refreshWidgets();
            }
        }).dimensions(action.x + 152, action.y + 44, 118, 18).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        if (currentView == View.MAIN) {
            renderMainView(context);
        } else {
            renderVillagesView(context, mouseX, mouseY);
        }

        if (designMode) {
            for (Map.Entry<String, NationLayoutConfig.LayoutRect> e : layout.entrySet()) {
                NationLayoutConfig.LayoutRect r = e.getValue();
                drawOutline(context, r.x, r.y, r.w, r.h, 0xFFFF3030);
                context.drawTextWithShadow(textRenderer, e.getKey(), r.x + 2, r.y + 2, 0xFFFF8080);
            }
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderMainView(DrawContext context) {
        int cx = width / 2;
        int panelX = cx - 200;
        int panelY = 18;
        int panelW = 420;
        int panelH = 154;
        context.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xCC101018);
        context.fill(panelX, panelY, panelX + panelW, panelY + 1, 0xFF55556A);

        context.drawTextWithShadow(textRenderer, "Nation Name: Aurora Pact", panelX + 8, panelY + 8, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, "Gov. Type: Democracy", panelX + 8, panelY + 26, 0xD7D7FF);
        context.drawTextWithShadow(textRenderer, "Capital: Barepost", panelX + 8, panelY + 44, 0xD0D0D0);
        context.drawTextWithShadow(textRenderer, "Population: " + villages.stream().mapToInt(v -> uniqueVillagers(v).size()).sum(), panelX + 8, panelY + 60, 0xD0D0D0);
        context.drawTextWithShadow(textRenderer, "Village Count: " + villages.size(), panelX + 8, panelY + 76, 0xD0D0D0);
        context.drawTextWithShadow(textRenderer, "Treasury: 1000", panelX + 8, panelY + 92, 0xD0D0D0);

        NationLayoutConfig.LayoutRect map = layout.get("MapWidget");
        context.fill(map.x, map.y, map.x + map.w, map.y + map.h, 0xCC1A1A24);
        context.drawCenteredTextWithShadow(textRenderer, "Interactable Map", map.x + map.w / 2, map.y + 12, 0xCCCCFF);

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

        NationLayoutConfig.LayoutRect villageList = layout.get("VillageList");
        NationLayoutConfig.LayoutRect villagerList = layout.get("VillagerList");
        NationLayoutConfig.LayoutRect profile = layout.get("ProfilePanel");

        context.fill(villageList.x, villageList.y, villageList.x + villageList.w, villageList.y + villageList.h, 0xAA181822);
        context.fill(villagerList.x, villagerList.y, villagerList.x + villagerList.w, villagerList.y + villagerList.h, 0xAA181822);

        context.drawTextWithShadow(textRenderer, "Villages in Nation", villageList.x + 6, villageList.y + 6, 0xFFFFFF);
        int y = villageList.y + 22;
        for (int i = 0; i < villages.size(); i++) {
            VillageEntry v = villages.get(i);
            int bg = i == selectedVillage ? 0x774C6A4C : 0x44303038;
            context.fill(villageList.x + 4, y - 2, villageList.x + villageList.w - 6, y + 10, bg);
            context.drawTextWithShadow(textRenderer, v.name + (v.isCapital ? " (Capital)" : ""), villageList.x + 8, y, 0xDCDCDC);
            y += 14;
        }

        context.drawTextWithShadow(textRenderer, "Villagers", villagerList.x + 6, villagerList.y + 6, 0xFFFFFF);
        VillageEntry selectedV = getSelectedVillage();
        y = villagerList.y + 22;
        if (selectedV != null) {
            List<VillagerEntry> unique = uniqueVillagers(selectedV);
            for (int i = 0; i < unique.size(); i++) {
                VillagerEntry villager = unique.get(i);
                int bg = i == selectedVillager ? 0x775A5A86 : 0x44303038;
                context.fill(villagerList.x + 4, y - 2, villagerList.x + villagerList.w - 6, y + 10, bg);
                context.drawTextWithShadow(textRenderer, villager.name + " - " + villager.job, villagerList.x + 8, y, 0xDCDCDC);
                y += 14;
            }
        }

        context.fill(profile.x, profile.y, profile.x + profile.w, profile.y + profile.h, 0xAA181822);
        context.drawTextWithShadow(textRenderer, "Villager Profile Information", profile.x + 8, profile.y + 6, 0xFFFFFF);
        if (selectedV != null && getSelectedVillager() != null) {
            VillagerEntry p = getSelectedVillager();
            VillagerEntityMCA entity = findVillagerEntity(p.uuid);
            context.fill(profile.x + 8, profile.y + 20, profile.x + 64, profile.y + 84, 0xAA22222E);
            if (entity != null) {
                InventoryScreen.drawEntity(context, profile.x + 36, profile.y + 82, 28,
                        (float) (profile.x + 36 - mouseX), (float) (profile.y + 34 - mouseY), entity);
            }
            context.drawTextWithShadow(textRenderer, "Name: " + p.name, profile.x + 74, profile.y + 22, 0xD0D0D0);
            context.drawTextWithShadow(textRenderer, "Mood: " + p.mood, profile.x + 74, profile.y + 36, 0x9BCBFF);
            context.drawTextWithShadow(textRenderer, "Job: " + p.job, profile.x + 74, profile.y + 50, 0xD0D0D0);
            context.drawTextWithShadow(textRenderer, "Marital: " + getMaritalStatus(entity, p), profile.x + 74, profile.y + 64, 0xD0D0D0);
            context.drawTextWithShadow(textRenderer, String.format(Locale.ROOT, "Villager Bank: %.2f", getVillagerBank(entity, p)), profile.x + 74, profile.y + 78, 0xD0D0D0);
        }
    }

    private void drawOutline(DrawContext c, int x, int y, int w, int h, int color) {
        c.fill(x, y, x + w, y + 1, color);
        c.fill(x, y + h - 1, x + w, y + h, color);
        c.fill(x, y, x + 1, y + h, color);
        c.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (designMode && button == 0) {
            for (Map.Entry<String, NationLayoutConfig.LayoutRect> e : layout.entrySet()) {
                if (e.getValue().contains(mouseX, mouseY)) {
                    draggingKey = e.getKey();
                    dragOffsetX = (int) mouseX - e.getValue().x;
                    dragOffsetY = (int) mouseY - e.getValue().y;
                    return true;
                }
            }
        }

        if (currentView == View.VILLAGES) {
            NationLayoutConfig.LayoutRect villageList = layout.get("VillageList");
            NationLayoutConfig.LayoutRect villagerList = layout.get("VillagerList");
            if (mouseX >= villageList.x + 4 && mouseX <= villageList.x + villageList.w - 4
                    && mouseY >= villageList.y + 20 && mouseY <= villageList.y + villageList.h) {
                int idx = ((int) mouseY - (villageList.y + 20)) / 14;
                if (idx >= 0 && idx < villages.size()) {
                    selectedVillage = idx;
                    selectedVillager = -1;
                    refreshWidgets();
                    return true;
                }
            }
            VillageEntry v = getSelectedVillage();
            if (v != null && mouseX >= villagerList.x + 4 && mouseX <= villagerList.x + villagerList.w - 4
                    && mouseY >= villagerList.y + 20 && mouseY <= villagerList.y + villagerList.h) {
                List<VillagerEntry> unique = uniqueVillagers(v);
                int idx = ((int) mouseY - (villagerList.y + 20)) / 14;
                if (idx >= 0 && idx < unique.size()) {
                    selectedVillager = idx;
                    refreshWidgets();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (designMode && draggingKey != null) {
            NationLayoutConfig.LayoutRect r = layout.get(draggingKey);
            if (r != null) {
                r.setPos((int) mouseX - dragOffsetX, (int) mouseY - dragOffsetY);
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (designMode && draggingKey != null) {
            NationLayoutConfig.LayoutRect r = layout.get(draggingKey);
            if (r != null) {
                String msg = String.format(Locale.ROOT, "%s => x=%d, y=%d, w=%d, h=%d", draggingKey, r.x, r.y, r.w, r.h);
                if (client != null && client.player != null) {
                    client.player.sendMessage(Text.literal("[NationLayout] " + msg), false);
                }
                MCA.LOGGER.info("[NationLayout] {}", msg);
                try {
                    Path path = NationLayoutConfig.layoutPath();
                    NationLayoutConfig.save(path, layout);
                } catch (Exception e) {
                    MCA.LOGGER.warn("Failed to save layout", e);
                }
            }
            draggingKey = null;
            refreshWidgets();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
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
        for (VillagerEntry e : village.villagers) byUuid.putIfAbsent(e.uuid, e);
        return new ArrayList<>(byUuid.values());
    }

    private VillagerEntityMCA findVillagerEntity(UUID villagerUuid) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return null;
        Box box = mc.player.getBoundingBox().expand(256);
        List<VillagerEntityMCA> list = mc.world.getEntitiesByClass(VillagerEntityMCA.class, box, v -> v.getUuid().equals(villagerUuid));
        return list.isEmpty() ? null : list.get(0);
    }

    private String getMaritalStatus(VillagerEntityMCA entity, VillagerEntry fallback) {
        if (entity == null) return fallback.maritalStatus;
        if (!entity.getRelationships().isMarried()) return "Single";
        return entity.getRelationships().getPartnerName().map(t -> "Married to " + t.getString()).orElse("Married");
    }

    private double getVillagerBank(VillagerEntityMCA entity, VillagerEntry fallback) {
        return entity == null ? fallback.bankBalance : entity.getVillagerBankBalance();
    }

    private void openFlagEditor() {
        client.setScreen(new FlagEditorScreen(this, flagData));
    }

    private void seedMockData() {
        VillageEntry a = new VillageEntry(UUID.fromString("11111111-1111-1111-1111-111111111111"), "Barepost", 14, 52, 500, 61, 0.46, 0.28, 0.53, 0.37, 3);
        a.villagers.add(new VillagerEntry(UUID.fromString("aaaaaaaa-1111-1111-1111-111111111111"), "Rolf", "Passive", "Guard", 0.41, 0.24, 0.58, 0.34, "Single", 50.50));
        a.villagers.add(new VillagerEntry(UUID.fromString("bbbbbbbb-1111-1111-1111-111111111111"), "Peder", "Happy", "Archer", 0.36, 0.42, 0.31, 0.66, "Married to Anja", 73.25));
        a.isCapital = true;
        VillageEntry b = new VillageEntry(UUID.fromString("22222222-2222-2222-2222-222222222222"), "Grayyard", 9, 44, 500, 49, 0.35, 0.41, 0.47, 0.51, 1);
        b.villagers.add(new VillagerEntry(UUID.fromString("dddddddd-2222-2222-2222-222222222222"), "Anja", "Fine", "None", 0.38, 0.39, 0.44, 0.52, "Single", 22.75));
        b.villagers.add(new VillagerEntry(UUID.fromString("eeeeeeee-2222-2222-2222-222222222222"), "Jorn", "Depressed", "Blacksmith", 0.42, 0.31, 0.48, 0.53, "Married to Peder", 105.10));
        villages.clear();
        villages.add(a);
        villages.add(b);
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
            addDrawableChild(ButtonWidget.builder(Text.literal("Randomize"), b -> randomizeFlag()).dimensions(cx - 130, y, 80, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("+ Layer"), b -> flag.layers.add("Layer " + (flag.layers.size() + 1))).dimensions(cx - 44, y, 72, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Save"), b -> client.setScreen(parent)).dimensions(cx + 34, y, 60, 20).build());
        }

        private void randomizeFlag() {
            List<Identifier> patterns = Registries.BANNER_PATTERN.getIds().stream().toList();
            if (!patterns.isEmpty()) flag.patternId = patterns.get(random.nextInt(patterns.size())).toString();
            String[] colors = {"Red", "Blue", "Green", "Yellow", "Black", "White", "Purple", "Cyan"};
            flag.baseColor = colors[random.nextInt(colors.length)];
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
            context.drawTextWithShadow(textRenderer, "Layers: " + flag.layers.size(), cx - 148, top + 64, 0xD0D0D0);
        }
    }
}
