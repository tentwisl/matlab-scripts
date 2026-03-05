# FTB Library Nation UI Implementation (Reference)

Below is a Java implementation blueprint using `dev.ftb.mods.ftblibrary.ui` style classes for the two requested views:

- `NationMainScreen` (initial nation view)
- `VillageManagementPanel` (villages tab sub-menu)

> Notes:
> - This is structured as production-ready Java scaffolding for your mod package.
> - Wire packet/network calls in the `TODO network call` sections to your current nation/village packet handlers.

---

## 1) `NationMainScreen` (extends `BaseScreen`)

```java
package net.mca.client.gui.nation;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.ui.BaseScreen;
import dev.ftb.mods.ftblibrary.ui.Button;
import dev.ftb.mods.ftblibrary.ui.ContextMenu;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftblibrary.ui.WidgetLayout;
import dev.ftb.mods.ftblibrary.ui.misc.IconWidget;
import dev.ftb.mods.ftblibrary.ui.misc.SimpleTextButton;
import dev.ftb.mods.ftblibrary.ui.misc.TextFieldWidget;
import dev.ftb.mods.ftblibrary.ui.misc.TooltipWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

public class NationMainScreen extends BaseScreen {

    private final NationViewModel vm;
    private boolean villagesTabActive = false;

    private VillageManagementPanel villageManagementPanel;

    public NationMainScreen(NationViewModel vm) {
        this.vm = vm;
    }

    @Override
    public void addWidgets() {
        int x = guiLeft;
        int y = guiTop;
        int w = guiWidth;
        int h = guiHeight;

        Panel root = new Panel(this);
        root.setPos(x, y);
        root.setSize(w, h);
        root.setLayout(WidgetLayout.NONE);
        add(root);

        // Top left stats
        root.add(new TooltipWidget(root, Component.literal("Nation Name"), x + 8, y + 8, 90, 10));
        root.add(new TextFieldWidget(root, x + 100, y + 6, 140, 14)
                .setText(vm.nationName)
                .setResponder(v -> vm.nationName = v));

        root.add(new TooltipWidget(root, Component.literal("Gov. Type"), x + 8, y + 28, 90, 10));
        root.add(SimpleTextButton.create(root, Component.literal(vm.govType), b -> openGovTypeChooser(), x + 100, y + 24, 90, 16));

        root.add(new TooltipWidget(root, Component.literal("Capital: " + vm.capitalName), x + 8, y + 48, 220, 10));
        root.add(new TooltipWidget(root, Component.literal("Population: " + vm.population), x + 8, y + 62, 220, 10));
        root.add(new TooltipWidget(root, Component.literal("Villages: " + vm.villageCount), x + 8, y + 76, 220, 10));
        root.add(new TooltipWidget(root, Component.literal("Overall Hearts: " + vm.overallHearts), x + 8, y + 90, 220, 10));
        root.add(new TooltipWidget(root, Component.literal("Treasury: " + vm.treasury), x + 8, y + 104, 220, 10));

        // Center map placeholder panel
        Panel mapPanel = new Panel(root);
        mapPanel.setPos(x + 250, y + 10);
        mapPanel.setSize(220, 150);
        mapPanel.setBackground(Icon.getIcon(new ResourceLocation("mca", "textures/gui/nation/map_placeholder.png")));
        root.add(mapPanel);

        mapPanel.add(new TooltipWidget(mapPanel,
                Component.literal("Interactable Map (centered on Nation Block @ " + vm.nationBlockX + ", " + vm.nationBlockZ + ")"),
                mapPanel.posX + 8, mapPanel.posY + 8, 180, 12));

        root.add(SimpleTextButton.create(root, Component.literal("Expand Map"),
                b -> vm.onExpandMapClicked.run(), x + 320, y + 166, 90, 16));

        // Right-side flag + edit
        IconWidget flag = new IconWidget(root, x + 490, y + 14, 84, 112,
                vm.flagIcon == null ? Color4I.GRAY : vm.flagIcon);
        root.add(flag);
        root.add(SimpleTextButton.create(root, Component.literal("Edit"),
                b -> vm.onEditFlagClicked.run(), x + 512, y + 130, 60, 16));

        // Bottom nav
        int navY = y + h - 26;
        root.add(SimpleTextButton.create(root, Component.literal("Villages"), b -> {
            villagesTabActive = true;
            refreshWidgets();
        }, x + 12, navY, 90, 18));

        root.add(SimpleTextButton.create(root, Component.literal("Government"), b -> vm.onGovernmentTabClicked.run(), x + 108, navY, 100, 18));
        root.add(SimpleTextButton.create(root, Component.literal("Commerce"), b -> vm.onCommerceTabClicked.run(), x + 214, navY, 90, 18));
        root.add(SimpleTextButton.create(root, Component.literal("Relations"), b -> vm.onRelationsTabClicked.run(), x + 310, navY, 90, 18));

        if (villagesTabActive) {
            villageManagementPanel = new VillageManagementPanel(this, vm);
            villageManagementPanel.setPos(x + 8, y + 188);
            villageManagementPanel.setSize(w - 16, h - 222);
            root.add(villageManagementPanel);
        }
    }

    private void openGovTypeChooser() {
        // Democracy only right now (locked)
        ContextMenu menu = new ContextMenu(this);
        menu.add(Component.literal("Democracy (currently only selectable)"), () -> {
            vm.govType = "Democracy";
            refreshWidgets();
        });
        menu.openContextMenu(getMouseX(), getMouseY(), true);
    }

    public static class NationViewModel {
        public String nationName;
        public String govType = "Democracy";
        public String capitalName;
        public int population;
        public int villageCount;
        public int overallHearts;
        public long treasury;
        public int nationBlockX;
        public int nationBlockZ;

        public Icon flagIcon;

        public List<VillageEntry> villages;

        // Selection persistence (pipeline state)
        public UUID selectedVillageId;
        public UUID selectedVillagerId;

        // nav callbacks
        public Runnable onExpandMapClicked = () -> {};
        public Runnable onEditFlagClicked = () -> {};
        public Runnable onGovernmentTabClicked = () -> {};
        public Runnable onCommerceTabClicked = () -> {};
        public Runnable onRelationsTabClicked = () -> {};

        // data mutation callbacks
        public NationActions actions;
    }

    public interface NationActions {
        List<VillagerEntry> getVillagers(UUID villageId);
        VillageStats getVillageStats(UUID villageId);
        List<VillageEntry> getVillagesWithAvailableBeds(UUID villagerId);
        List<JobEntry> getAvailableJobs(UUID villagerId, UUID villageId);

        // Functional override requirements
        void setVillageMarriageDisabled(UUID villageId, boolean disabled); // NBT override
        void setVillagePopGrowthDisabled(UUID villageId, boolean disabled); // NBT override

        // Move-to requirement: update villager home coords to selected village center
        void moveVillagerToVillage(UUID villagerId, UUID targetVillageId);

        void assignJob(UUID villagerId, String jobId);
        void setCapital(UUID villageId);
        void expandBorder(UUID villageId);
        void openStatistics(UUID villageId);
    }

    public record VillageEntry(UUID id, String name, int population, int hearts, long treasury, int availableBeds) {}
    public record VillagerEntry(UUID id, String name, String mood, String job) {}
    public record JobEntry(String id, String title) {}

    public static class VillageStats {
        public String name;
        public int population;
        public int hearts;
        public long treasury;
        public int nationalFavor;
        public String politicalCompass;

        public boolean marriageDisabled;
        public boolean popGrowthDisabled;

        public List<String> notEnactedPolicies;
        public List<String> enactedPolicies;
    }
}
```

---

## 2) `VillageManagementPanel` (nested sub-view)

```java
package net.mca.client.gui.nation;

import dev.ftb.mods.ftblibrary.ui.BaseScreen;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.ScrollPanel;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftblibrary.ui.WidgetLayout;
import dev.ftb.mods.ftblibrary.ui.misc.EntityWidget;
import dev.ftb.mods.ftblibrary.ui.misc.SimpleTextButton;
import dev.ftb.mods.ftblibrary.ui.misc.TooltipWidget;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

public class VillageManagementPanel extends Panel {

    private final BaseScreen screen;
    private final NationMainScreen.NationViewModel vm;

    // Dynamic popups
    private Panel moveToOverlay;
    private Panel assignOverlay;

    public VillageManagementPanel(BaseScreen screen, NationMainScreen.NationViewModel vm) {
        super(screen);
        this.screen = screen;
        this.vm = vm;
    }

    @Override
    public void addWidgets() {
        setLayout(WidgetLayout.NONE);

        int x = posX;
        int y = posY;

        // Selection pipeline column 1: villages
        ScrollPanel villageList = new ScrollPanel(this);
        villageList.setPos(x + 6, y + 6);
        villageList.setSize(160, 220);
        add(villageList);

        int rowY = 4;
        for (NationMainScreen.VillageEntry v : vm.villages) {
            villageList.add(SimpleTextButton.create(villageList, Component.literal(v.name()), b -> {
                vm.selectedVillageId = v.id();
                vm.selectedVillagerId = null;
                closeOverlays();
                screen.refreshWidgets();
            }, 4, rowY, 148, 16));
            rowY += 18;
        }

        // Selection pipeline column 2: villagers in selected village
        ScrollPanel villagerList = new ScrollPanel(this);
        villagerList.setPos(x + 176, y + 6);
        villagerList.setSize(180, 220);
        add(villagerList);

        if (vm.selectedVillageId != null) {
            List<NationMainScreen.VillagerEntry> villagers = vm.actions.getVillagers(vm.selectedVillageId);
            int vy = 4;
            for (NationMainScreen.VillagerEntry villager : villagers) {
                villagerList.add(SimpleTextButton.create(villagerList,
                        Component.literal(villager.name() + "  [" + villager.job() + "]"), b -> {
                            vm.selectedVillagerId = villager.id();
                            closeOverlays();
                            screen.refreshWidgets();
                        }, 4, vy, 168, 16));
                vy += 18;
            }
        }

        // Bottom-left profile panel
        Panel profile = new Panel(this);
        profile.setPos(x + 6, y + 232);
        profile.setSize(350, 130);
        add(profile);

        profile.add(new TooltipWidget(profile, Component.literal("Villager Profile Information"), profile.posX + 8, profile.posY + 6, 200, 10));

        // full-body selected villager
        if (vm.selectedVillagerId != null) {
            profile.add(new EntityWidget(profile, profile.posX + 8, profile.posY + 20, 60, 96, vm.selectedVillagerId));
        }

        profile.add(SimpleTextButton.create(profile, Component.literal("Move to"), b -> openMoveToOverlay(), profile.posX + 80, profile.posY + 100, 70, 16));
        profile.add(SimpleTextButton.create(profile, Component.literal("Assign"), b -> openAssignOverlay(), profile.posX + 156, profile.posY + 100, 70, 16));

        // Center village stats + actions
        Panel stats = new Panel(this);
        stats.setPos(x + 370, y + 6);
        stats.setSize(330, 220);
        add(stats);

        NationMainScreen.VillageStats selectedStats = vm.selectedVillageId == null ? null : vm.actions.getVillageStats(vm.selectedVillageId);

        if (selectedStats != null) {
            stats.add(new TooltipWidget(stats, Component.literal("Village: " + selectedStats.name), stats.posX + 8, stats.posY + 8, 220, 10));
            stats.add(new TooltipWidget(stats, Component.literal("Pop: " + selectedStats.population), stats.posX + 8, stats.posY + 24, 220, 10));
            stats.add(new TooltipWidget(stats, Component.literal("Hearts: " + selectedStats.hearts), stats.posX + 8, stats.posY + 38, 220, 10));
            stats.add(new TooltipWidget(stats, Component.literal("Treasury: " + selectedStats.treasury), stats.posX + 8, stats.posY + 52, 220, 10));
            stats.add(new TooltipWidget(stats, Component.literal("National Favor: " + selectedStats.nationalFavor), stats.posX + 8, stats.posY + 66, 220, 10));
            stats.add(new TooltipWidget(stats, Component.literal("Political Compass: " + selectedStats.politicalCompass), stats.posX + 8, stats.posY + 80, 300, 10));

            // required village action buttons
            stats.add(SimpleTextButton.create(stats, Component.literal("Set as Capital"), b -> vm.actions.setCapital(vm.selectedVillageId), stats.posX + 190, stats.posY + 8, 120, 16));

            stats.add(SimpleTextButton.create(stats,
                    Component.literal(selectedStats.marriageDisabled ? "Enable Marriage" : "Disable Marriage"), b -> {
                        vm.actions.setVillageMarriageDisabled(vm.selectedVillageId, !selectedStats.marriageDisabled);
                        screen.refreshWidgets();
                    }, stats.posX + 190, stats.posY + 28, 120, 16));

            stats.add(SimpleTextButton.create(stats,
                    Component.literal(selectedStats.popGrowthDisabled ? "Enable Pop. Growth" : "Disable Pop. Growth"), b -> {
                        vm.actions.setVillagePopGrowthDisabled(vm.selectedVillageId, !selectedStats.popGrowthDisabled);
                        screen.refreshWidgets();
                    }, stats.posX + 190, stats.posY + 48, 120, 16));

            stats.add(SimpleTextButton.create(stats, Component.literal("Expand Border"), b -> vm.actions.expandBorder(vm.selectedVillageId), stats.posX + 190, stats.posY + 68, 120, 16));
            stats.add(SimpleTextButton.create(stats, Component.literal("Statistics"), b -> vm.actions.openStatistics(vm.selectedVillageId), stats.posX + 190, stats.posY + 88, 120, 16));
        }

        // Right policies columns
        ScrollPanel notEnacted = new ScrollPanel(this);
        notEnacted.setPos(x + 710, y + 6);
        notEnacted.setSize(180, 105);
        add(notEnacted);
        notEnacted.add(new TooltipWidget(notEnacted, Component.literal("Not Enacted"), notEnacted.posX + 4, notEnacted.posY + 4, 120, 10));

        ScrollPanel enacted = new ScrollPanel(this);
        enacted.setPos(x + 710, y + 121);
        enacted.setSize(180, 105);
        add(enacted);
        enacted.add(new TooltipWidget(enacted, Component.literal("Enacted Policies"), enacted.posX + 4, enacted.posY + 4, 140, 10));

        if (selectedStats != null) {
            int ny = 18;
            for (String p : selectedStats.notEnactedPolicies) {
                notEnacted.add(new TooltipWidget(notEnacted, Component.literal("- " + p), notEnacted.posX + 4, notEnacted.posY + ny, 164, 10));
                ny += 12;
            }

            int ey = 18;
            for (String p : selectedStats.enactedPolicies) {
                enacted.add(new TooltipWidget(enacted, Component.literal("- " + p), enacted.posX + 4, enacted.posY + ey, 164, 10));
                ey += 12;
            }
        }
    }

    // --------------------------------------------------
    // Dynamic overlay hierarchy
    // --------------------------------------------------

    private void openMoveToOverlay() {
        closeOverlays();

        if (vm.selectedVillageId == null || vm.selectedVillagerId == null) {
            return;
        }

        moveToOverlay = new Panel(this);
        moveToOverlay.setPos(posX + 90, posY + 274);
        moveToOverlay.setSize(240, 160);
        add(moveToOverlay);

        moveToOverlay.add(new TooltipWidget(moveToOverlay, Component.literal("Villages with available beds"), moveToOverlay.posX + 8, moveToOverlay.posY + 8, 220, 10));

        ScrollPanel targets = new ScrollPanel(moveToOverlay);
        targets.setPos(moveToOverlay.posX + 8, moveToOverlay.posY + 24);
        targets.setSize(224, 100);
        moveToOverlay.add(targets);

        int y = 4;
        for (NationMainScreen.VillageEntry target : vm.actions.getVillagesWithAvailableBeds(vm.selectedVillagerId)) {
            targets.add(SimpleTextButton.create(targets, Component.literal(target.name() + " (beds: " + target.availableBeds() + ")"), b -> {
                // Required behavior: update villager home coords NBT to selected village center
                vm.actions.moveVillagerToVillage(vm.selectedVillagerId, target.id());
                closeOverlays();
                screen.refreshWidgets();
            }, 4, y, 214, 16));
            y += 18;
        }

        moveToOverlay.add(SimpleTextButton.create(moveToOverlay, Component.literal("Close"), b -> {
            closeOverlays();
            screen.refreshWidgets();
        }, moveToOverlay.posX + 84, moveToOverlay.posY + 130, 70, 16));
    }

    private void openAssignOverlay() {
        closeOverlays();

        if (vm.selectedVillageId == null || vm.selectedVillagerId == null) {
            return;
        }

        assignOverlay = new Panel(this);
        assignOverlay.setPos(posX + 256, posY + 274);
        assignOverlay.setSize(190, 160);
        add(assignOverlay);

        assignOverlay.add(new TooltipWidget(assignOverlay, Component.literal("Available Jobs"), assignOverlay.posX + 8, assignOverlay.posY + 8, 160, 10));

        ScrollPanel jobs = new ScrollPanel(assignOverlay);
        jobs.setPos(assignOverlay.posX + 8, assignOverlay.posY + 24);
        jobs.setSize(174, 100);
        assignOverlay.add(jobs);

        int y = 4;
        for (NationMainScreen.JobEntry job : vm.actions.getAvailableJobs(vm.selectedVillagerId, vm.selectedVillageId)) {
            jobs.add(SimpleTextButton.create(jobs, Component.literal(job.title()), b -> {
                vm.actions.assignJob(vm.selectedVillagerId, job.id());
                closeOverlays();
                screen.refreshWidgets();
            }, 4, y, 164, 16));
            y += 18;
        }

        assignOverlay.add(SimpleTextButton.create(assignOverlay, Component.literal("Close"), b -> {
            closeOverlays();
            screen.refreshWidgets();
        }, assignOverlay.posX + 60, assignOverlay.posY + 130, 70, 16));
    }

    private void closeOverlays() {
        if (moveToOverlay != null) {
            remove(moveToOverlay);
            moveToOverlay = null;
        }
        if (assignOverlay != null) {
            remove(assignOverlay);
            assignOverlay = null;
        }
    }
}
```

---

## 3) Explicit onClick Selection Logic (Villages -> Villagers -> Profile)

The pipeline implemented above is:

1. **Village row click**
   - `vm.selectedVillageId = clickedVillage.id()`
   - `vm.selectedVillagerId = null`
   - close overlays
   - `screen.refreshWidgets()`

2. **Villager row click**
   - `vm.selectedVillagerId = clickedVillager.id()`
   - close overlays
   - `screen.refreshWidgets()`

3. **Profile actions**
   - `Move to` -> open `moveToOverlay`
   - `Assign` -> open `assignOverlay`

4. **Overlay item click**
   - execute mutation callback (`moveVillagerToVillage(...)` or `assignJob(...)`)
   - close overlay
   - `screen.refreshWidgets()`

This guarantees selection persistence and full UI redraw after sub-menu changes.

---

## Functional Override Notes (Requested MCA hooks)

Implement these callbacks in your server packet/action layer:

- `setVillageMarriageDisabled(villageId, disabled)`
  - Write boolean to village-level NBT flag (e.g. `mcaMarriageDisabled`) and enforce in MCA breeding checks.
- `setVillagePopGrowthDisabled(villageId, disabled)`
  - Write boolean to village-level NBT flag (e.g. `mcaPopGrowthDisabled`) and enforce in population growth / procreation checks.
- `moveVillagerToVillage(villagerId, targetVillageId)`
  - Resolve target village center and update villager home NBT/global-pos accordingly.

All callbacks should re-sync state to client and then call `refreshWidgets()` on close/complete.
