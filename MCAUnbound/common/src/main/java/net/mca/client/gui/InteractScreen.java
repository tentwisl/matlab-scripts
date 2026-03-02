package net.mca.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.mca.MCA;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.entity.VillagerLike;
import net.mca.entity.ai.Genetics;
import net.mca.entity.ai.Memories;
import net.mca.entity.ai.Traits;
import net.mca.entity.ai.brain.VillagerBrain;
import net.mca.entity.ai.relationship.CompassionateEntity;
import net.mca.entity.ai.relationship.RelationshipState;
import net.mca.network.c2s.*;
import net.mca.resources.data.analysis.Analysis;
import net.mca.resources.data.dialogue.Question;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
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
 * Villager interaction screen — redesigned for MCA Unbound.
 *
 * <h3>Layout:</h3>
 * <pre>
 *  ┌─ Top-left Info Panel ──────────────────────────────┐
 *  │  Name — mood — trait — hearts — nation affiliation │
 *  └───────────────────────────────────────────────────-─┘
 *
 *  Primary buttons (centred column):
 *    Talk  |  Gift  |  Trade  |  Work Orders  |  Politics  |  Family
 *
 *  Sub-menus:
 *    Talk     → Joke, Greet, Flirt, Story, Ask, Kiss (heart-gated)
 *    Politics → Bribe, Discuss Leader, Campaign
 *    Family   → Marry, Procreate, Assign Heirs (monarch-gated)
 * </pre>
 */
public class InteractScreen extends AbstractDynamicScreen {
    public static final Identifier ICON_TEXTURES = MCA.locate("textures/gui.png");

    private final VillagerLike<?> villager;
    private final PlayerEntity player = Objects.requireNonNull(MinecraftClient.getInstance().player);

    private boolean inGiftMode;
    private int timeSinceLastClick;

    private String father;
    private String mother;

    private RelationshipState marriageState;
    private Text spouse;

    // Extra villager info from server
    private String profession  = "";
    private String villageName = "";
    private boolean isNpcLeader = false;

    private List<String> dialogAnswers;
    private String dialogAnswerHover;
    private List<OrderedText> dialogQuestionText;
    private String dialogQuestionId;

    private static Analysis<?> analysis;

    public InteractScreen(VillagerLike<?> villager) {
        super(Text.literal("Interact"));
        this.villager = villager;
    }

    public void setParents(String father, String mother) {
        this.father = father;
        this.mother = mother;
    }

    public void setSpouse(RelationshipState marriageState, String spouse) {
        this.marriageState = marriageState;
        this.spouse = spouse == null ? Text.translatable("gui.interact.label.parentUnknown") : Text.literal(spouse);
    }

    public void setProfessionAndVillage(String profession, String villageName, boolean isNpcLeader) {
        this.profession   = profession;
        this.villageName  = villageName;
        this.isNpcLeader  = isNpcLeader;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        Objects.requireNonNull(this.client).setScreen(null);
        NetworkHandler.sendToServer(new InteractionCloseRequest(villager.asEntity().getUuid()));
    }

    @Override
    public void init() {
        NetworkHandler.sendToServer(new GetInteractDataRequest(villager.asEntity().getUuid()));
    }

    @Override
    public void tick() {
        timeSinceLastClick++;
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float tickDelta) {
        super.render(context, mouseX, mouseY, tickDelta);

        drawInfoPanel(context);
        drawIcons(context);
        drawTextPopups(context);
    }

    /**
     * Top-left info panel: name, mood, trait, profession, hearts, village, nation.
     */
    private void drawInfoPanel(DrawContext context) {
        VillagerBrain<?> brain = villager.getVillagerBrain();
        Memories memory        = brain.getMemoriesForPlayer(player);
        int hearts             = memory.getHearts();

        int px = 6;
        int py = 6;
        int lh = 12;

        // Semi-transparent panel background (7 rows now)
        context.fill(px - 2, py - 2, px + 175, py + lh * 7 + 4, 0x88000000);

        // Row 1 — Name
        String displayName = villager.asEntity().getName().getString();
        if (inGiftMode) displayName = "[Gift Mode] " + displayName;
        context.drawTextWithShadow(textRenderer, Text.literal(displayName).formatted(Formatting.WHITE), px, py, 0xFFFFFF);

        // Row 2 — Mood
        context.drawTextWithShadow(textRenderer,
                Text.literal("Mood: ").formatted(Formatting.GRAY)
                        .append(brain.getMood().getText().copy().formatted(brain.getMood().getColor())),
                px, py + lh, 0xFFFFFF);

        // Row 3 — Personality + Traits
        MutableText traits = Text.literal("Trait: ").formatted(Formatting.GRAY)
                .append(brain.getPersonality().getName().copy().formatted(Formatting.WHITE));
        Set<Traits.Trait> traitSet = villager.getTraits().getTraits();
        if (!traitSet.isEmpty()) {
            traits.append(Text.literal(" / ").formatted(Formatting.DARK_GRAY));
            boolean first = true;
            for (Traits.Trait t : traitSet) {
                if (!first) traits.append(Text.literal(", ").formatted(Formatting.DARK_GRAY));
                traits.append(t.getName().copy().formatted(Formatting.AQUA));
                first = false;
            }
        }
        context.drawTextWithShadow(textRenderer, traits, px, py + lh * 2, 0xFFFFFF);

        // Row 4 — Profession / job
        String profDisplay = profession.isEmpty() ? "Jobless" : profession;
        Formatting profColor = isNpcLeader ? Formatting.GOLD : Formatting.WHITE;
        context.drawTextWithShadow(textRenderer,
                Text.literal("Job: ").formatted(Formatting.GRAY)
                        .append(Text.literal(profDisplay).formatted(profColor)),
                px, py + lh * 3, 0xFFFFFF);

        // Row 5 — Hearts
        int hc = hearts < 0 ? 0xFF5555 : hearts >= 100 ? 0xFFD700 : 0xFF6666;
        context.drawTextWithShadow(textRenderer,
                Text.literal("Hearts: ").formatted(Formatting.GRAY)
                        .append(Text.literal(String.valueOf(hearts)).styled(s -> s.withColor(hc))),
                px, py + lh * 4, 0xFFFFFF);

        // Row 6 — Village
        String villageDisplay = villageName.isEmpty() ? "None" : villageName;
        context.drawTextWithShadow(textRenderer,
                Text.literal("Village: ").formatted(Formatting.GRAY)
                        .append(Text.literal(villageDisplay).formatted(Formatting.GREEN)),
                px, py + lh * 5, 0xFFFFFF);

        // Row 7 — Nation affiliation (placeholder)
        context.drawTextWithShadow(textRenderer,
                Text.literal("Nation: ").formatted(Formatting.GRAY)
                        .append(Text.literal("Independent").formatted(Formatting.YELLOW)),
                px, py + lh * 6, 0xFFFFFF);
    }

    // ── Icon bar ──────────────────────────────────────────────────────────────

    private void drawIcons(DrawContext context) {
        final MatrixStack matrices = context.getMatrices();
        Memories memory = villager.getVillagerBrain().getMemoriesForPlayer(player);

        matrices.push();
        matrices.scale(iconScale, iconScale, iconScale);

        if (marriageState != null) drawIcon(context, ICON_TEXTURES, marriageState.getIcon());
        drawIcon(context, ICON_TEXTURES,
                memory.getHearts() < 0 ? "blackHeart" : memory.getHearts() >= 100 ? "goldHeart" : "redHeart");
        drawIcon(context, ICON_TEXTURES, "genes");
        if (canDrawParentsIcon()) drawIcon(context, ICON_TEXTURES, "parents");
        if (canDrawGiftIcon())    drawIcon(context, ICON_TEXTURES, "gift");
        if (analysis != null)     drawIcon(context, ICON_TEXTURES, "analysis");

        matrices.pop();
    }

    // ── Tooltips & Dialogue ───────────────────────────────────────────────────

    private void drawTextPopups(DrawContext context) {
        if (hoveringOverIcon("redHeart")) {
            int h = villager.getVillagerBrain().getMemoriesForPlayer(player).getHearts();
            drawHoveringIconText(context, Text.literal(h + " hearts"), "redHeart");
        }
        if (marriageState != null && hoveringOverIcon("married") && villager instanceof CompassionateEntity<?>) {
            String ms = marriageState.base().getIcon().toLowerCase(Locale.ENGLISH);
            drawHoveringIconText(context, Text.translatable("gui.interact.label." + ms, spouse), "married");
        }
        if (canDrawParentsIcon() && hoveringOverIcon("parents")) {
            drawHoveringIconText(context, Text.translatable("gui.interact.label.parents",
                    father == null ? Text.translatable("gui.interact.label.parentUnknown") : father,
                    mother == null ? Text.translatable("gui.interact.label.parentUnknown") : mother
            ), "parents");
        }
        if (canDrawGiftIcon() && hoveringOverIcon("gift"))
            drawHoveringIconText(context, Text.translatable("gui.interact.label.gift"), "gift");

        if (hoveringOverIcon("genes")) {
            List<Text> lines = new LinkedList<>();
            lines.add(Text.literal("Genes"));
            for (Genetics.Gene gene : villager.getGenetics()) {
                String key = gene.getType().getTranslationKey();
                int value = (int) (gene.get() * 100);
                lines.add(Text.translatable("gene.tooltip", Text.translatable(key), value));
            }
            drawHoveringIconText(context, lines, "genes");
        }
        if (hoveringOverIcon("analysis") && analysis != null) {
            List<Text> lines = new LinkedList<>();
            lines.add(Text.translatable("analysis.title").formatted(Formatting.GRAY));
            for (Analysis.AnalysisElement d : analysis) {
                lines.add(Text.translatable("analysis." + d.getKey())
                        .append(Text.literal(": " + (d.isPositive() ? "+" : "") + d.getValue()))
                        .formatted(d.isPositive() ? Formatting.GREEN : Formatting.RED));
            }
            lines.add(Text.translatable("analysis.total").append(": " + analysis.getTotalAsString()));
            drawHoveringIconText(context, lines, "analysis");
        }

        // Dialogue overlay (centre)
        if (dialogQuestionText != null) {
            context.fill(width / 2 - 85, height / 2 - 50 - 10 * dialogQuestionText.size(),
                    width / 2 + 85, height / 2 - 30 + 10 * dialogAnswers.size(), 0x77000000);
            int i = -dialogQuestionText.size();
            for (OrderedText t : dialogQuestionText) {
                i++;
                context.drawTextWithShadow(textRenderer, t,
                        width / 2 - textRenderer.getWidth(t) / 2, height / 2 - 50 + i * 10, 0xFFFFFFFF);
            }
            dialogAnswerHover = null;
            context.drawHorizontalLine(width / 2 - 75, width / 2 + 75, height / 2 - 40, 0xAAFFFFFF);
            int y = height / 2 - 35;
            for (String a : dialogAnswers) {
                boolean hover = hoveringOver(width / 2 - 100, y - 3, 200, 10);
                context.drawCenteredTextWithShadow(textRenderer,
                        Text.translatable(Question.getTranslationKey(dialogQuestionId, a)),
                        width / 2, y, hover ? 0xFFD7D784 : 0xAAFFFFFF);
                if (hover) dialogAnswerHover = a;
                y += 10;
            }
        }
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseScrolled(double x, double y, double d) {
        if (d < 0)      player.getInventory().selectedSlot = player.getInventory().selectedSlot == 8 ? 0 : player.getInventory().selectedSlot + 1;
        else if (d > 0) player.getInventory().selectedSlot = player.getInventory().selectedSlot == 0 ? 8 : player.getInventory().selectedSlot - 1;
        return super.mouseScrolled(x, y, d);
    }

    @Override
    public boolean mouseClicked(double posX, double posY, int button) {
        super.mouseClicked(posX, posY, button);
        if (button == 0 && dialogAnswerHover != null && dialogQuestionText != null) {
            NetworkHandler.sendToServer(new InteractionDialogueMessage(
                    villager.asEntity().getUuid(), dialogQuestionId, dialogAnswerHover));
        }
        if (inGiftMode && button == 1) {
            NetworkHandler.sendToServer(new InteractionVillagerMessage("gui.button.gift", villager.asEntity().getUuid()));
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyChar, int keyCode, int unknown) {
        if (keyChar == GLFW.GLFW_KEY_ESCAPE) {
            if (inGiftMode) { inGiftMode = false; setLayout("main"); }
            else close();
            return true;
        }
        return false;
    }

    // ── Button handling ───────────────────────────────────────────────────────

    @Override
    protected void buttonPressed(Button button) {
        String id = button.identifier();
        if (timeSinceLastClick <= 2) return;
        timeSinceLastClick = 0;

        switch (id) {
            // ── Sub-menu navigation ───────────────────────────────────────
            case "gui.button.talk" -> {
                // Do NOT send InteractionDialogueInitMessage here — that causes
                // the dialogue greeting overlay to appear on top of talk buttons.
                setLayout("talk");
                return;
            }
            case "gui.button.politics" -> { setLayout("politics"); return; }
            case "gui.button.family"   -> { setLayout("family");   return; }
            case "gui.button.help_village" -> {
                NetworkHandler.sendToServer(new OpenHelpVillageRequest(villager.asEntity().getUuid()));
                return;
            }
            case "gui.button.work_orders" -> {
                setLayout("work");
                disableButton("gui.button." + villager.getVillagerBrain().getCurrentJob().name().toLowerCase(Locale.ENGLISH));
                return;
            }
            case "gui.button.interact" -> { setLayout("interact"); return; }
            case "gui.button.command"  -> {
                setLayout("command");
                disableButton("gui.button." + villager.getVillagerBrain().getMoveState().name().toLowerCase(Locale.ENGLISH));
                return;
            }
            case "gui.button.clothing"    -> { setLayout("clothing");    return; }
            case "gui.button.familyTree"  -> { MinecraftClient.getInstance().setScreen(new FamilyTreeScreen(villager.asEntity().getUuid())); return; }
            case "gui.button.professions" -> { setLayout("professions"); return; }
            case "gui.button.locations"   -> { setLayout("locations");   return; }
            case "gui.button.backarrow" -> {
                if (inGiftMode) { inGiftMode = false; }
                String active = getActiveScreen();
                // Navigate back: submenu → main
                if (active.equals("talk") || active.equals("politics") || active.equals("family")
                        || active.equals("interact") || active.equals("work") || active.equals("locations")) {
                    setLayout("main");
                } else {
                    setLayout("main");
                }
                return;
            }
            case "gui.button.gift" -> { inGiftMode = true; disableAllButtons(); return; }
        }

        // ── Placeholder talk buttons ───────────────────────────────────────
        if (id.equals("gui.button.ask") || id.equals("gui.button.chat") ||
                id.equals("gui.button.rumors")) {
            // Placeholder — no action yet
            return;
        }

        // ── Server-notified buttons ───────────────────────────────────────
        if (button.notifyServer() && !button.targetServer()) {
            NetworkHandler.sendToServer(new InteractionVillagerMessage(id, villager.asEntity().getUuid()));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean hoveringOverText(int x, int y, int w) {
        return hoveringOver(x + 8, y - 16, w, 16);
    }

    private boolean canDrawParentsIcon() { return father != null || mother != null; }
    private boolean canDrawGiftIcon()    { return false; }

    public void setDialogue(String dialogue, List<String> answers) {
        dialogQuestionId = dialogue;
        dialogAnswers = answers;
    }

    public void setLastPhrase(MutableText questionText, boolean silent) {
        MutableText text = silent
                ? villager.transformMessage(questionText)
                : villager.sendChatMessage(questionText, player);
        dialogQuestionText = textRenderer.wrapLines(text, 160);
    }

    public static void setAnalysis(Analysis<?> analysis) {
        InteractScreen.analysis = analysis;
    }
}
