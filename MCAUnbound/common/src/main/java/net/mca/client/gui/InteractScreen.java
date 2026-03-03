package net.mca.client.gui;

import net.mca.MCA;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.client.gui.widget.PaneEntries;
import net.mca.client.gui.widget.PaneEntries.ButtonSpec;
import net.mca.client.gui.widget.ScrollPane;
import net.mca.client.gui.widget.TabPanel;
import net.mca.entity.VillagerLike;
import net.mca.entity.ai.Genetics;
import net.mca.entity.ai.Memories;
import net.mca.entity.ai.Traits;
import net.mca.entity.ai.brain.VillagerBrain;
import net.mca.entity.ai.relationship.CompassionateEntity;
import net.mca.entity.ai.relationship.RelationshipState;
import net.mca.entity.interaction.Constraint;
import net.mca.network.c2s.*;
import net.mca.resources.data.analysis.Analysis;
import net.mca.resources.data.dialogue.Question;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Redesigned villager interaction screen.
 *
 * <pre>
 * ┌─────────────────────────────────────────────────────────────┐
 * │  [Portrait 80×80]  Name · Mood · Job · Village · ♥ Hearts  │
 * ├─────────────────────────────────────────────────────────────┤
 * │  [ Talk ]  [ Actions ]  [ Profile ]  ← tab strip           │
 * ├─────────────────────────────────────────────────────────────┤
 * │  Tab content (ScrollPane, auto-sized)                       │
 * └─────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * All dialogue overlap issues are resolved: the "Talk" tab shows interaction
 * buttons directly without triggering the MCA dialogue overlay.
 */
public class InteractScreen extends AbstractDynamicScreen {
    public static final Identifier ICON_TEXTURES = MCA.locate("textures/gui.png");


    // ── Constants ─────────────────────────────────────────────────────────────
    private static final int PORTRAIT_SIZE = 80;
    private static final int HEADER_H      = 96;  // portrait + padding
    private static final int TAB_H         = 22;
    private static final int PANEL_W       = 280;

    // ── Villager reference ─────────────────────────────────────────────────────
    private final VillagerLike<?> villager;
    private final PlayerEntity    player = Objects.requireNonNull(
            MinecraftClient.getInstance().player);

    // ── Server-side data ──────────────────────────────────────────────────────
    private String         father;
    private String         mother;
    private RelationshipState marriageState;
    private String         spouseLabel;

    private String  profession   = "";
    private String  villageName  = "";
    private boolean isNpcLeader  = false;

    // ── Dialogue overlay ──────────────────────────────────────────────────────
    private List<String>       dialogAnswers    = null;
    private String             dialogAnswerHover;
    private List<OrderedText>  dialogQuestionText;
    private String             dialogQuestionId;

    private static Analysis<?> analysis;

    // ── Tab panel (rebuilt every init) ───────────────────────────────────────
    private TabPanel tabPanel;
    private int      timeSinceLastClick;

    // ── Gift mode ─────────────────────────────────────────────────────────────
    private boolean inGiftMode;

    public InteractScreen(VillagerLike<?> villager) {
        super(Text.literal("Interact"));
        this.villager = villager;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void init() {
        NetworkHandler.sendToServer(new GetInteractDataRequest(villager.asEntity().getUuid()));
        buildTabPanel();
    }

    private void buildTabPanel() {
        clearChildren();

        Set<Constraint> c = getConstraints();
        int panelX = width - PANEL_W - 8;
        int panelY = HEADER_H + 4;
        int panelH = height - panelY - 4;

        tabPanel = new TabPanel(panelX, panelY, PANEL_W, panelH, TAB_H)
                .addTab("Talk",    0x44FFDD00, pane -> buildTalkTab(pane, c))
                .addTab("Actions", 0x4488CCFF, pane -> buildActionsTab(pane, c))
                .addTab("Profile", 0x4488FF88, pane -> buildProfileTab(pane, c));

        tabPanel.init(this::addDrawableChild);
    }

    // ── Tab content builders ──────────────────────────────────────────────────

    private void buildTalkTab(ScrollPane pane, Set<Constraint> c) {
        pane.add(PaneEntries.spacer(6));

        List<ButtonSpec> talkButtons = new ArrayList<>();
        talkButtons.add(ButtonSpec.of("Greet",   () -> sendInteract("gui.button.greet")));
        talkButtons.add(ButtonSpec.of("Joke",    () -> sendInteract("gui.button.joke")));
        talkButtons.add(ButtonSpec.of("Story",   () -> sendInteract("gui.button.story")));

        boolean isAdult = c.contains(Constraint.ADULT);
        talkButtons.add(isAdult
                ? ButtonSpec.of("Flirt",  () -> sendInteract("gui.button.flirt"))
                : ButtonSpec.disabled("Flirt"));

        talkButtons.add(ButtonSpec.of("Chat",    () -> {})); // placeholder
        talkButtons.add(ButtonSpec.of("Rumors",  () -> {})); // placeholder
        talkButtons.add(ButtonSpec.of("Ask",     () -> {})); // placeholder

        boolean canKiss = c.contains(Constraint.HEARTS_100);
        talkButtons.add(canKiss
                ? ButtonSpec.of("Kiss ♥", () -> sendInteract("gui.button.kiss"))
                : ButtonSpec.disabled("Kiss (100 ♥)"));

        pane.add(PaneEntries.buttonGrid(talkButtons, 2, 20));
    }

    private void buildActionsTab(ScrollPane pane, Set<Constraint> c) {
        pane.add(PaneEntries.spacer(6));

        pane.add(PaneEntries.buttonRow("Gift Items", "Give a gift",
                () -> { inGiftMode = true; disableAllButtons(); }));

        boolean isTrader = c.contains(Constraint.TRADER);
        pane.add(isTrader
                ? PaneEntries.buttonRow("Trade", "Open trade menu",
                    () -> NetworkHandler.sendToServer(
                            new InteractionVillagerMessage("gui.button.trade", villager.asEntity().getUuid())))
                : PaneEntries.buttonRow("Trade", "(Requires Trader)",
                    () -> {}, true));

        pane.add(PaneEntries.divider());

        // Work Orders (only if player is village leader)
        boolean isVillageLeader = c.contains(Constraint.VILLAGE_LEADER);
        if (isVillageLeader) {
            pane.add(PaneEntries.label("§e  Village Leader Options", 0xAA222200));
            pane.add(PaneEntries.buttonRow("Work Orders", "Assign tasks",
                    () -> setLayout("work")));
        }

        // Help Village (only if talking to NPC leader)
        if (isNpcLeader) {
            pane.add(PaneEntries.buttonRow("Help Village", "View supply requests",
                    () -> NetworkHandler.sendToServer(
                            new OpenHelpVillageRequest(villager.asEntity().getUuid()))));
        }

        pane.add(PaneEntries.divider());

        pane.add(PaneEntries.buttonRow("Politics", "Placeholder — coming soon",
                () -> {}, true));

        pane.add(PaneEntries.buttonRow("Family Tree", "View family tree",
                () -> MinecraftClient.getInstance().setScreen(
                        new FamilyTreeScreen(villager.asEntity().getUuid()))));
    }

    private void buildProfileTab(ScrollPane pane, Set<Constraint> c) {
        VillagerBrain<?> brain = villager.getVillagerBrain();
        Memories memory = brain.getMemoriesForPlayer(player);
        int hearts = memory.getHearts();

        pane.add(PaneEntries.spacer(4));
        pane.add(PaneEntries.label("§f  Villager Info", 0xAA111111));

        pane.add(PaneEntries.infoRow("Name",  villager.asEntity().getName().getString(), 0xFFFFFF));
        pane.add(PaneEntries.infoRow("Mood",  brain.getMood().getText().getString(),
                brain.getMood().getColor().getColorValue()));

        // Trait
        String traitStr = brain.getPersonality().getName().getString();
        Set<Traits.Trait> traitSet = villager.getTraits().getTraits();
        if (!traitSet.isEmpty()) {
            traitStr += " / " + traitSet.stream()
                    .map(t -> t.getName().getString())
                    .collect(Collectors.joining(", "));
        }
        pane.add(PaneEntries.infoRow("Trait", traitStr, 0x55FFFF));

        String profDisplay = profession.isEmpty() ? "Jobless" : profession;
        int profColor = isNpcLeader ? 0xFFD700 : 0xFFFFFF;
        pane.add(PaneEntries.infoRow("Job",     profDisplay, profColor));

        int hc = hearts < 0 ? 0xFF5555 : hearts >= 100 ? 0xFFD700 : 0xFF6666;
        pane.add(PaneEntries.infoRow("Hearts",  hearts + " ♥", hc));

        String village = villageName.isEmpty() ? "None" : villageName;
        pane.add(PaneEntries.infoRow("Village", village, 0x55FF55));
        pane.add(PaneEntries.infoRow("Nation",  "Independent", 0xFFAA00));

        if (father != null || mother != null) {
            pane.add(PaneEntries.divider());
            pane.add(PaneEntries.label("§f  Family", 0xAA111111));
            if (father != null) pane.add(PaneEntries.infoRow("Father", father, 0xFFFFFF));
            if (mother != null) pane.add(PaneEntries.infoRow("Mother", mother, 0xFFFFFF));
        }

        if (spouseLabel != null) {
            pane.add(PaneEntries.infoRow("Partner", spouseLabel, 0xFF88AA));
        }

        pane.add(PaneEntries.divider());
        pane.add(PaneEntries.label("§f  Genes", 0xAA111111));
        for (Genetics.Gene gene : villager.getGenetics()) {
            String key   = gene.getType().getTranslationKey();
            int    value = (int) (gene.get() * 100);
            pane.add(PaneEntries.infoRow(
                    Text.translatable(key).getString(), value + "%", 0xAAAAFF));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void sendInteract(String buttonId) {
        NetworkHandler.sendToServer(
                new InteractionVillagerMessage(buttonId, villager.asEntity().getUuid()));
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float tickDelta) {
        super.render(context, mouseX, mouseY, tickDelta);

        int leftX  = width - PANEL_W - 8;
        int rightX = leftX + PANEL_W;

        // ── Header panel ──────────────────────────────────────────────────────
        context.fill(leftX - 4, 2, rightX + 4, HEADER_H, 0x99000000);

        // Villager portrait
        int portraitX = leftX + 4;
        try {
            InventoryScreen.drawEntity(context,
                    portraitX + PORTRAIT_SIZE / 2,
                    8 + PORTRAIT_SIZE,
                    PORTRAIT_SIZE / 2,
                    (float) (portraitX + PORTRAIT_SIZE / 2) - mouseX,
                    (float) (8 + PORTRAIT_SIZE / 3) - mouseY,
                    villager.asEntity());
        } catch (Exception ignored) { /* graceful fallback */ }

        // Name + quick stats to the right of portrait
        int tx = portraitX + PORTRAIT_SIZE + 8;
        int ty = 8;
        int lh = 12;

        VillagerBrain<?> brain  = villager.getVillagerBrain();
        Memories         memory = brain.getMemoriesForPlayer(player);
        int              hearts = memory.getHearts();

        String displayName = villager.asEntity().getName().getString();
        if (inGiftMode) displayName = "[Gift Mode] " + displayName;
        context.drawTextWithShadow(textRenderer,
                Text.literal(displayName).formatted(Formatting.WHITE), tx, ty, 0xFFFFFF);

        context.drawTextWithShadow(textRenderer,
                Text.literal("Mood: ").formatted(Formatting.GRAY)
                        .append(brain.getMood().getText().copy().formatted(brain.getMood().getColor())),
                tx, ty + lh, 0xFFFFFF);

        String profDisplay = profession.isEmpty() ? "Jobless" : profession;
        int profColor = isNpcLeader ? 0xFFD700 : 0xFFFFFF;
        context.drawTextWithShadow(textRenderer,
                Text.literal("Job: ").formatted(Formatting.GRAY)
                        .append(Text.literal(profDisplay).styled(s -> s.withColor(profColor))),
                tx, ty + lh * 2, 0xFFFFFF);

        int hc = hearts < 0 ? 0xFF5555 : hearts >= 100 ? 0xFFD700 : 0xFF6666;
        context.drawTextWithShadow(textRenderer,
                Text.literal("♥ ").styled(s -> s.withColor(hc))
                        .append(Text.literal(String.valueOf(hearts)).styled(s -> s.withColor(hc))),
                tx, ty + lh * 3, 0xFFFFFF);

        String vname = villageName.isEmpty() ? "Independent" : villageName;
        context.drawTextWithShadow(textRenderer,
                Text.literal("Village: ").formatted(Formatting.GRAY)
                        .append(Text.literal(vname).formatted(Formatting.GREEN)),
                tx, ty + lh * 4, 0xFFFFFF);

        // Tab panel
        if (tabPanel != null) tabPanel.render(context, mouseX, mouseY, tickDelta);

        // Dialogue overlay
        drawDialogueOverlay(context);
    }

    private void drawDialogueOverlay(DrawContext context) {
        if (dialogAnswers == null || dialogQuestionText == null) return;

        int bgX1 = width / 2 - 90;
        int bgX2 = width / 2 + 90;
        int bgY1 = height / 2 - 50 - 10 * dialogQuestionText.size();
        int bgY2 = height / 2 - 25 + 10 * dialogAnswers.size();
        context.fill(bgX1, bgY1, bgX2, bgY2, 0xDD111111);
        context.fill(bgX1, bgY2 - 1, bgX2, bgY2, 0xFFAAAAAA);

        int i = -dialogQuestionText.size();
        for (OrderedText t : dialogQuestionText) {
            i++;
            context.drawCenteredTextWithShadow(textRenderer, t,
                    width / 2, height / 2 - 50 + i * 10, 0xFFFFFFFF);
        }

        context.drawHorizontalLine(width / 2 - 80, width / 2 + 80, height / 2 - 40, 0xAAFFFFFF);

        dialogAnswerHover = null;
        int y = height / 2 - 36;
        for (String a : dialogAnswers) {
            boolean hover = hoveringOver(width / 2 - 90, y - 3, 180, 10);
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.translatable(Question.getTranslationKey(dialogQuestionId, a)),
                    width / 2, y, hover ? 0xFFD7D784 : 0xAAFFFFFF);
            if (hover) dialogAnswerHover = a;
            y += 10;
        }
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public void tick() { timeSinceLastClick++; }

    @Override
    public boolean mouseScrolled(double x, double y, double d) {
        if (tabPanel != null && tabPanel.mouseScrolled(x, y, d)) return true;
        // Also cycle hotbar
        if (d < 0) player.getInventory().selectedSlot = player.getInventory().selectedSlot == 8 ? 0 : player.getInventory().selectedSlot + 1;
        else if (d > 0) player.getInventory().selectedSlot = player.getInventory().selectedSlot == 0 ? 8 : player.getInventory().selectedSlot - 1;
        return super.mouseScrolled(x, y, d);
    }

    @Override
    public boolean mouseClicked(double posX, double posY, int button) {
        super.mouseClicked(posX, posY, button);
        // Dialogue answer selection
        if (button == 0 && dialogAnswerHover != null && dialogQuestionText != null) {
            NetworkHandler.sendToServer(new InteractionDialogueMessage(
                    villager.asEntity().getUuid(), dialogQuestionId, dialogAnswerHover));
        }
        // Gift mode
        if (inGiftMode && button == 1) {
            NetworkHandler.sendToServer(
                    new InteractionVillagerMessage("gui.button.gift", villager.asEntity().getUuid()));
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyChar, int keyCode, int unknown) {
        if (keyChar == GLFW.GLFW_KEY_ESCAPE) {
            if (inGiftMode) { inGiftMode = false; buildTabPanel(); }
            else close();
            return true;
        }
        return false;
    }

    @Override
    public void close() {
        Objects.requireNonNull(client).setScreen(null);
        NetworkHandler.sendToServer(new InteractionCloseRequest(villager.asEntity().getUuid()));
    }

    @Override
    public boolean shouldPause() { return false; }

    // ── AbstractDynamicScreen overrides ───────────────────────────────────────
    // We no longer use the JSON-layout button system for this screen.

    @Override
    protected void buttonPressed(Button b) { /* handled by TabPanel / PaneEntries */ }

    // ── Server response setters ───────────────────────────────────────────────

    @Override
    public void setConstraints(Set<Constraint> constraints) {
        super.setConstraints(constraints);
        buildTabPanel(); // rebuild with updated constraint set
    }

    public void setParents(String father, String mother) {
        this.father = father;
        this.mother = mother;
    }

    public void setSpouse(RelationshipState state, String spouse) {
        this.marriageState  = state;
        this.spouseLabel    = spouse;
    }

    public void setProfessionAndVillage(String profession, String villageName, boolean isNpcLeader) {
        this.profession  = profession;
        this.villageName = villageName;
        this.isNpcLeader = isNpcLeader;
        buildTabPanel();
    }

    public void setDialogue(String id, List<String> answers) {
        this.dialogQuestionId = id;
        this.dialogAnswers    = answers;
    }

    public void setLastPhrase(MutableText questionText, boolean silent) {
        MutableText text = silent
                ? villager.transformMessage(questionText)
                : villager.sendChatMessage(questionText, player);
        this.dialogQuestionText = textRenderer.wrapLines(text, 170);
    }

    public static void setAnalysis(Analysis<?> a) { InteractScreen.analysis = a; }

    // ── Legacy compatibility — unused but kept for interface conformance ───────

    protected boolean hoveringOver(int x, int y, int w, int h) {
        double mx = MinecraftClient.getInstance().mouse.getX()
                * this.width / MinecraftClient.getInstance().getWindow().getWidth();
        double my = MinecraftClient.getInstance().mouse.getY()
                * this.height / MinecraftClient.getInstance().getWindow().getHeight();
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
