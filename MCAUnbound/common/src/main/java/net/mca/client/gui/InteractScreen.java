package net.mca.client.gui;

import net.mca.MCA;
import net.mca.MCAClient;
import net.mca.ProfessionsMCA;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.client.gui.widget.PaneEntries;
import net.mca.client.gui.widget.PaneEntries.ButtonSpec;
import net.mca.client.gui.widget.ScrollPane;
import net.mca.client.gui.widget.TabPanel;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.VillagerLike;
import net.mca.entity.ai.Genetics;
import net.mca.entity.ai.Memories;
import net.mca.entity.ai.Traits;
import net.mca.entity.ai.brain.VillagerBrain;
import net.mca.entity.ai.relationship.AgeState;
import net.mca.entity.ai.relationship.CompassionateEntity;
import net.mca.entity.ai.relationship.Personality;
import net.mca.entity.ai.relationship.RelationshipState;
import net.mca.entity.interaction.dynamicdialogue.DialogueJsonManager;
import net.mca.entity.interaction.dynamicdialogue.DialogueCalculator;
import net.mca.entity.interaction.dynamicdialogue.MainDialogueCategory;
import net.mca.entity.interaction.dynamicdialogue.NpcJob;
import net.mca.entity.interaction.dynamicdialogue.NpcMood;
import net.mca.entity.interaction.dynamicdialogue.NpcTrait;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import net.minecraft.village.VillagerProfession;
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
    private static final int ANNOYED_THRESHOLD = -5;
    private static final int LOCKOUT_THRESHOLD = -15;
    private static final int BURNOUT_LOCKOUT_FATIGUE = 16;
    private static final long TALK_LOCKOUT_DURATION_TICKS = 12000L;
    private static final long GREETING_COOLDOWN_TICKS = 200L; // ~10 seconds

    // ── Villager reference ─────────────────────────────────────────────────────
    private final VillagerLike<?> villager;
    private final PlayerEntity    player = Objects.requireNonNull(
            MinecraftClient.getInstance().player);

    // ── Server-side data ──────────────────────────────────────────────────────
    private String         father;
    private String         mother;
    private RelationshipState marriageState;
    private String         spouseLabel;

    private String  profession              = "";
    private String  villageName             = "";
    private boolean isNpcLeader             = false;
    private boolean leaderConvinced         = false;
    private boolean playerIsOwnVillageLeader = false;

    // ── Dialogue overlay ──────────────────────────────────────────────────────
    private List<String>       dialogAnswers    = null;
    private String             dialogAnswerHover;
    private List<OrderedText>  dialogQuestionText;
    private String             dialogQuestionId;

    private static Analysis<?> analysis;

    // ── Tab panel (rebuilt every init) ───────────────────────────────────────
    private TabPanel tabPanel;
    private int      activeTabIndex     = 0;
    private int      timeSinceLastClick;

    // ── Gift mode ─────────────────────────────────────────────────────────────
    private boolean inGiftMode;

    private final DialogueJsonManager dialogueJsonManager = new DialogueJsonManager();

    private MainDialogueCategory selectedTalkCategory;
    private List<DialogueOptionEntry> activeDialogueOptions = List.of();

    public InteractScreen(VillagerLike<?> villager) {
        super(Text.literal("Interact"));
        this.villager = villager;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void init() {
        Memories memory = villager.getVillagerBrain().getMemoriesForPlayer(player);
        if (isTalkLockedOut(memory)) {
            sendVillagerChat(applyPlaceholders(
                    dialogueJsonManager.getEscalatedResponse(memory.getSessionHeartDelta(), true)
                            .orElse("I have nothing to say to you right now.")));
            close();
            return;
        }

        memory.setSessionHeartDelta(0);
        triggerInitialGreeting(memory);

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
        tabPanel.setActiveTab(activeTabIndex);

        tabPanel.init(this::addDrawableChild, () -> {
            // Capture selected tab BEFORE the rebuild wipes the panel reference.
            activeTabIndex = tabPanel.getActiveTab();
            buildTabPanel();
        });
    }

    // ── Tab content builders ──────────────────────────────────────────────────

    private void buildTalkTab(ScrollPane pane, Set<Constraint> c) {
        pane.add(PaneEntries.spacer(6));

        List<ButtonSpec> talkButtons = new ArrayList<>();

        // Issue 5: Babies only get Play (babble-type interactions)
        if (isBabyVillager()) {
            talkButtons.add(ButtonSpec.of("Play", () -> openDialogueCategory(MainDialogueCategory.PLAY)));
            pane.add(PaneEntries.buttonGrid(talkButtons, 2, 20));
            if (selectedTalkCategory != null && !activeDialogueOptions.isEmpty()) {
                pane.add(PaneEntries.spacer(8));
                pane.add(PaneEntries.label("§f  " + selectedTalkCategory.name() + " options", 0xAA111111));
                for (DialogueOptionEntry option : activeDialogueOptions) {
                    pane.add(PaneEntries.buttonRow(option.label(), option.playerLine(),
                            () -> onDialogueSubButtonClicked(option)));
                }
            }
            return;
        }

        talkButtons.add(ButtonSpec.of("Greet",   () -> openDialogueCategory(MainDialogueCategory.GREET)));
        talkButtons.add(ButtonSpec.of("Joke",    () -> openDialogueCategory(MainDialogueCategory.JOKE)));
        talkButtons.add(ButtonSpec.of("Story",   () -> openDialogueCategory(MainDialogueCategory.STORY)));

        if (isJuvenileVillager()) {
            talkButtons.add(ButtonSpec.of("Play", () -> openDialogueCategory(MainDialogueCategory.PLAY)));
        } else {
            boolean isAdult = c.contains(Constraint.ADULT);
            talkButtons.add(isAdult
                    ? ButtonSpec.of("Romance",  () -> openDialogueCategory(MainDialogueCategory.ROMANCE))
                    : ButtonSpec.disabled("Romance"));
        }

        talkButtons.add(ButtonSpec.of("Chat",    () -> openDialogueCategory(MainDialogueCategory.CHAT)));
        talkButtons.add(ButtonSpec.of("Rumors",   () -> openDialogueCategory(MainDialogueCategory.RUMORS)));

        // Issue 8: Don't show ASK for juvenile villagers
        if (!isJuvenileVillager()) {
            talkButtons.add(ButtonSpec.of("Ask", () -> openDialogueCategory(MainDialogueCategory.ASK)));
        }

        pane.add(PaneEntries.buttonGrid(talkButtons, 2, 20));

        if (selectedTalkCategory != null && !activeDialogueOptions.isEmpty()) {
            pane.add(PaneEntries.spacer(8));
            pane.add(PaneEntries.label("§f  " + selectedTalkCategory.name() + " options", 0xAA111111));
            for (DialogueOptionEntry option : activeDialogueOptions) {
                // Button label = subcategory label (e.g. "Friendly", "Heroic")
                // Tooltip = player's spoken line (flavor text preview)
                pane.add(PaneEntries.buttonRow(option.label(), option.playerLine(),
                        () -> onDialogueSubButtonClicked(option)));
            }

            if (selectedTalkCategory == MainDialogueCategory.ROMANCE) {
                pane.add(PaneEntries.spacer(4));
                pane.add(PaneEntries.divider());
                pane.add(PaneEntries.buttonRow("Hug", "Use default MCA hug dialogue logic",
                        () -> sendInteract("gui.button.hug")));
                pane.add(PaneEntries.buttonRow("Kiss", "Use default MCA kiss dialogue logic",
                        () -> sendInteract("gui.button.kiss")));
            }
        }
    }

    private void buildActionsTab(ScrollPane pane, Set<Constraint> c) {
        pane.add(PaneEntries.spacer(6));

        VillagerBrain<?> brain = villager.getVillagerBrain();
        Memories memory = brain.getMemoriesForPlayer(player);
        int hearts = memory.getHearts();
        boolean isMarriedToThisNpc = c.contains(Constraint.SPOUSE);
        boolean canCommandMovement = hearts >= 50;

        pane.add(PaneEntries.buttonRow("Gift Items", "Give a gift",
                () -> { inGiftMode = true; disableAllButtons(); }));

        boolean isTrader = c.contains(Constraint.TRADER);
        pane.add(isTrader
                ? PaneEntries.buttonRow("Trade", "Open trade menu",
                    () -> sendInteract("gui.button.trade"))
                : PaneEntries.buttonRow("Trade", "(Requires Trader)",
                    () -> {}, true));

        pane.add(PaneEntries.buttonRow("Divorce", "End your marriage with this NPC",
                () -> sendInteract("gui.button.divorceConfirm"), !isMarriedToThisNpc));
        pane.add(PaneEntries.buttonRow("Procreate", "Try to have a child together",
                () -> sendInteract("gui.button.procreate"), !isMarriedToThisNpc));

        pane.add(PaneEntries.divider());

        pane.add(PaneEntries.buttonRow("Follow", "Ask this villager to follow you",
                () -> sendInteract("gui.button.follow"), !canCommandMovement));
        pane.add(PaneEntries.buttonRow("Stay Here", "Ask this villager to stay put",
                () -> sendInteract("gui.button.stay"), !canCommandMovement));
        pane.add(PaneEntries.buttonRow("Pickup", "Pick up this baby villager",
                () -> sendInteract("gui.button.pick_up"), !isBabyVillager()));

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

        // Nation alliance proposal — shown only when the NPC is a village leader,
        // the player has 100 hearts with them, and the player is already a village leader.
        if (isNpcLeader && playerIsOwnVillageLeader) {
            if (leaderConvinced) {
                pane.add(PaneEntries.buttonRow("Alliance Proposed",
                        "This leader has agreed to join your nation",
                        () -> {}, true));
            } else if (hearts >= 100) {
                pane.add(PaneEntries.buttonRow("Propose Nation Alliance",
                        "Ask this leader to join your nation (requires 100 hearts)",
                        () -> NetworkHandler.sendToServer(
                                new ConvinceLeaderPacket(villager.asEntity().getUuid()))));
            } else {
                pane.add(PaneEntries.buttonRow("Propose Nation Alliance",
                        "Requires 100 hearts with this leader",
                        () -> {}, true));
            }
        }

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
        pane.add(PaneEntries.infoRow("Age",   villager.getAgeState().getName().getString(), 0xFFDDAA));
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

        // Relationship context
        String relationCtx = getRelationshipContext(c);
        if (!relationCtx.isEmpty()) {
            pane.add(PaneEntries.infoRow("Relation", relationCtx, 0xFFAA55));
        }

        String profDisplay = profession.isEmpty() ? "Jobless" : profession;
        int profColor = isNpcLeader ? 0xFFD700 : 0xFFFFFF;
        pane.add(PaneEntries.infoRow("Job",     profDisplay, profColor));

        int hc = hearts < 0 ? 0xFF5555 : hearts >= 100 ? 0xFFD700 : 0xFF6666;
        pane.add(PaneEntries.infoRow("Hearts",  hearts + " hearts", hc));
        int lastDelta = memory.getLastInteractionDelta();
        String deltaLabel = lastDelta > 0 ? "+" + lastDelta : Integer.toString(lastDelta);
        int deltaColor = lastDelta > 0 ? 0x77FF77 : (lastDelta < 0 ? 0xFF7777 : 0xBBBBBB);
        pane.add(PaneEntries.infoRow("Last Interaction", deltaLabel, deltaColor));

        String village = villageName.isEmpty() ? "None" : villageName;
        pane.add(PaneEntries.infoRow("Village", village, 0x55FF55));

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

    /**
     * Returns a human-readable relationship label for the Profile tab.
     */
    private String getRelationshipContext(Set<Constraint> c) {
        if (c.contains(Constraint.SPOUSE)) return "Your Spouse";
        if (c.contains(Constraint.ENGAGED)) return "Your Fiance";
        if (c.contains(Constraint.PARENT)) return "Your Child";
        if (c.contains(Constraint.KIDS)) return "Your Parent";
        if (c.contains(Constraint.FAMILY)) return "Family";
        return "";
    }

    /**
     * Determines the family relationship type for dialogue context.
     */
    private FamilyRelation getFamilyRelation(Set<Constraint> c) {
        if (c.contains(Constraint.SPOUSE) || c.contains(Constraint.ENGAGED)) return FamilyRelation.SPOUSE;
        if (c.contains(Constraint.PARENT)) return FamilyRelation.MY_CHILD;
        if (c.contains(Constraint.KIDS)) return FamilyRelation.MY_PARENT;
        if (c.contains(Constraint.FAMILY)) return FamilyRelation.FAMILY;
        return FamilyRelation.NONE;
    }

    private void triggerInitialGreeting(Memories memory) {
        long worldTime = villager.asEntity().getWorld().getTime();
        long lastGreeted = memory.getLastGreetedTime();
        boolean recentReopen = lastGreeted > 0 && (worldTime - lastGreeted) < GREETING_COOLDOWN_TICKS;
        memory.setLastGreetedTime(worldTime);

        // Issue 1: If re-opened recently, show a brief return-visit line instead
        if (recentReopen) {
            String[] returnLines = {
                    "Forget to tell me something, {player}?",
                    "Back again so soon?",
                    "Something else on your mind, {player}?",
                    "Oh, you're back. What is it?"
            };
            sendVillagerChat(applyPlaceholders(returnLines[villager.asEntity().getRandom().nextInt(returnLines.length)]));
            return;
        }

        Set<Constraint> c = getConstraints();
        FamilyRelation relation = getFamilyRelation(c);
        boolean firstMeeting = !memory.hasMet(); // Issue 3: hasMet is sole determinant

        // Issue 5: Babies only do gibberish greetings
        if (isBabyVillager()) {
            String greeting = buildBabyGreeting(relation);
            sendVillagerChat(applyPlaceholders(greeting));
            if (!memory.hasMet()) memory.setHasMet(true);
            return;
        }

        String greeting;
        if (relation != FamilyRelation.NONE && !firstMeeting) {
            greeting = buildFamilyGreeting(relation);
        } else if (!firstMeeting) {
            // Issue 2: Heart-tier greetings for non-family returning villagers
            greeting = buildHeartTierGreeting(memory.getHearts());
        } else {
            greeting = buildFirstGreeting(firstMeeting);
        }
        sendVillagerChat(applyPlaceholders(greeting));
        if (!memory.hasMet()) {
            memory.setHasMet(true);
        }
    }

    private String buildFamilyGreeting(FamilyRelation relation) {
        String npcName = villager.asEntity().getName().getString();
        NpcTrait trait = toNpcTrait(villager.getVillagerBrain().getPersonality());
        NpcMood mood = toNpcMood(villager.getVillagerBrain().getMood().getName());

        return switch (relation) {
            case SPOUSE -> switch (mood) {
                case HAPPY -> switch (trait) {
                    case FLIRTATIOUS -> "There's my favorite person. Come here, {player}.";
                    case SHY -> "Oh, {player}... I'm glad you're home.";
                    default -> "Welcome back, love. I missed you, {player}.";
                };
                case SAD -> "I've been thinking about things, {player}... can we talk?";
                case ANGRY -> "We need to talk, {player}. I'm not happy right now.";
                default -> "Hey there, {player}. Good to see you.";
            };
            case MY_CHILD -> switch (mood) {
                case HAPPY -> isJuvenileVillager()
                        ? "Mama! Papa! You're here! {player}!"
                        : "Hey, {player}! Glad you stopped by.";
                case SAD -> isJuvenileVillager()
                        ? "I missed you, {player}... where were you?"
                        : "Hi {player}... I've been having a rough day.";
                case ANGRY -> isJuvenileVillager()
                        ? "Hmph! I'm still mad, {player}!"
                        : "Not now, {player}. I need some space.";
                default -> isJuvenileVillager()
                        ? "Hi {player}! What are we doing today?"
                        : "Oh, hey {player}. What's up?";
            };
            case MY_PARENT -> "Good to see you. How are you feeling today, {player}?";
            case FAMILY -> "Family is always welcome. Hello, {player}.";
            case NONE -> "Hello, {player}.";
        };
    }

    private String buildBabyGreeting(FamilyRelation relation) {
        boolean isParent = relation == FamilyRelation.MY_PARENT
                || relation == FamilyRelation.SPOUSE; // spouse greeting a baby is still a parent
        String parentWord = isParent ? "mama" : "{player}";
        String[] babyLines = {
                "goo goo... " + parentWord + "!",
                "ba-ba... da-da!",
                "*reaches tiny hands toward " + parentWord + "*",
                "*coos and babbles happily*",
                "gaa... gaa gaa!",
                "*giggles at " + parentWord + "*"
        };
        return babyLines[villager.asEntity().getRandom().nextInt(babyLines.length)];
    }

    private String buildHeartTierGreeting(int hearts) {
        NpcTrait trait = toNpcTrait(villager.getVillagerBrain().getPersonality());
        NpcMood mood = toNpcMood(villager.getVillagerBrain().getMood().getName());
        var rng = villager.asEntity().getRandom();

        if (hearts >= 75) {
            // Close friend / best friend tier
            String[] lines = {
                    "{player}! My favorite person! How are you?",
                    "There you are, {player}! I was hoping you'd come by!",
                    "Always a pleasure to see you, dear {player}.",
                    "{player}! Come, come! I've been looking forward to your visit.",
                    "Ah, my dear friend {player}! What a wonderful surprise!"
            };
            return lines[rng.nextInt(lines.length)];
        } else if (hearts >= 50) {
            // Friend tier
            String[] lines = {
                    "Hey {player}! Good to see a friendly face.",
                    "Well if it isn't {player}! How've you been?",
                    "{player}! Always welcome here, friend.",
                    "Nice to see you again, {player}. What's new?"
            };
            return lines[rng.nextInt(lines.length)];
        } else if (hearts >= 20) {
            // Acquaintance tier
            String[] lines = {
                    "Oh, hello {player}. Nice to see you.",
                    "Hey there, {player}. What brings you by?",
                    "Ah, {player}. Good day to you."
            };
            return lines[rng.nextInt(lines.length)];
        } else if (hearts >= 0) {
            // Neutral
            String[] lines = {
                    "Hello, {player}.",
                    "Oh. {player}. What can I do for you?",
                    "Hmm? Oh, {player}. Hello."
            };
            return lines[rng.nextInt(lines.length)];
        } else {
            // Negative hearts — dislike
            String[] lines = {
                    "Oh. It's you, {player}.",
                    "What do you want, {player}?",
                    "*sighs* Yes, {player}?",
                    "I was having a perfectly nice day until now."
            };
            return lines[rng.nextInt(lines.length)];
        }
    }

    private String buildFirstGreeting(boolean firstMeeting) {
        NpcTrait trait = toNpcTrait(villager.getVillagerBrain().getPersonality());
        String playerName = getPlayerMCAName();
        String npcName = villager.asEntity().getName().getString();

        NpcJob npcJob = toNpcJob();
        // Issue 4: Derive job display from NpcJob when server profession data hasn't arrived yet
        String jobDisplay;
        if (profession != null && !profession.isBlank()) {
            jobDisplay = profession;
        } else if (npcJob != NpcJob.NONE) {
            // Convert enum name to title case (e.g. FLETCHER -> Fletcher)
            String raw = npcJob.name();
            jobDisplay = raw.charAt(0) + raw.substring(1).toLowerCase(Locale.ENGLISH).replace("_", " ");
        } else {
            jobDisplay = "Jobless";
        }
        String jobKey = getIntroJobKey(npcJob);

        Optional<VillagerEntityMCA> rival = findRival(villager.asEntity().getWorld(), villager.asEntity().getBlockPos(), npcJob);
        String rivalName = rival.map(r -> r.getName().getString()).orElse("");

        return dialogueJsonManager.buildIntroLine(firstMeeting, trait, playerName, npcName, jobKey, jobDisplay, rivalName);
    }

    private String getIntroJobKey(NpcJob npcJob) {
        if (npcJob == NpcJob.NONE) {
            return "jobless";
        }
        if (npcJob == NpcJob.VILLAGE_LEADER) {
            return "village_leader";
        }
        if (npcJob == NpcJob.GUARD || npcJob == NpcJob.ARCHER) {
            return "guard";
        }
        if (profession != null && !profession.isBlank()) {
            return profession.trim().toLowerCase(Locale.ENGLISH).replace(" ", "_");
        }
        return npcJob.name().toLowerCase(Locale.ENGLISH);
    }

    private Optional<VillagerEntityMCA> findRival(World world, BlockPos pos, NpcJob job) {
        if (job == NpcJob.NONE || job == NpcJob.GUARD || job == NpcJob.ARCHER || job == NpcJob.VILLAGE_LEADER) {
            return Optional.empty();
        }

        return world.getEntitiesByClass(VillagerEntityMCA.class,
                        new Box(pos).expand(64),
                        candidate -> !candidate.getUuid().equals(villager.asEntity().getUuid())
                                && candidate.getVillagerData().getProfession() == villager.getVillagerData().getProfession())
                .stream()
                .findFirst();
    }

    private void openDialogueCategory(MainDialogueCategory category) {
        Memories memory = villager.getVillagerBrain().getMemoriesForPlayer(player);
        if (isTalkLockedOut(memory)) {
            sendVillagerChat(applyPlaceholders(
                    dialogueJsonManager.getEscalatedResponse(memory.getSessionHeartDelta(), true)
                            .orElse("I have nothing to say to you right now.")));
            close();
            return;
        }

        selectedTalkCategory = category;
        String categoryKey = category.name().toLowerCase(Locale.ENGLISH);
        activeDialogueOptions = dialogueJsonManager.getPlayerOptions(categoryKey, isJuvenileVillager(), villager.getAgeState()).stream()
                .map(option -> new DialogueOptionEntry(
                        option.category(), option.subCategoryId(), option.label(), option.playerLine()))
                .toList();
        buildTabPanel();
    }

    private void onDialogueSubButtonClicked(DialogueOptionEntry option) {
        Memories memory = villager.getVillagerBrain().getMemoriesForPlayer(player);
        if (isTalkLockedOut(memory)) {
            sendVillagerChat(applyPlaceholders(
                    dialogueJsonManager.getEscalatedResponse(memory.getSessionHeartDelta(), true)
                            .orElse("I have nothing to say to you right now.")));
            close();
            return;
        }

        // Player speaks their line in chat (two-sided conversation)
        sendPlayerChat(option.playerLine());

        DialogueJsonManager.JsonSubCategory subCategory = dialogueJsonManager
                .getSubCategory(option.categoryKey(), option.subCategoryId(), isJuvenileVillager(), villager.getAgeState())
                .orElse(null);

        DialogueCalculator.JsonEvaluationResult result = DialogueCalculator.calculateFromJson(
                option.categoryKey(),
                subCategory,
                toNpcTrait(villager.getVillagerBrain().getPersonality()),
                toNpcMood(villager.getVillagerBrain().getMood().getName()),
                memory.getHearts(),
                toNpcJob(),
                memory.getLastUsedDialogueSubtype(),
                memory.getRepeatedDialogueCount(),
                memory.getAlternatingDialogueCount(),
                memory.getInteractionFatigue(),
                villager.asEntity().getRandom()
        );

        memory.modHearts(result.relationshipPointChange());
        memory.modInteractionFatigue(1);
        memory.setLastInteractionDelta(result.relationshipPointChange());

        // Bidirectional session tracking: positive interactions offset annoyance
        memory.modSessionHeartDelta(result.relationshipPointChange());

        if (result.relationshipPointChange() > 0) {
            villager.getVillagerBrain().modifyMoodValue(1);
        } else if (result.relationshipPointChange() < 0) {
            villager.getVillagerBrain().modifyMoodValue(-2);
        }

        String interactionKey = option.categoryKey() + ":" + option.subCategoryId();
        if (interactionKey.equalsIgnoreCase(memory.getLastUsedDialogueSubtype())) {
            memory.setRepeatedDialogueCount(memory.getRepeatedDialogueCount() + 1);
        } else {
            memory.setRepeatedDialogueCount(0);
        }

        boolean alternatingWithPrevious = !memory.getSecondLastUsedDialogueSubtype().isBlank()
                && interactionKey.equalsIgnoreCase(memory.getSecondLastUsedDialogueSubtype())
                && !interactionKey.equalsIgnoreCase(memory.getLastUsedDialogueSubtype());
        if (alternatingWithPrevious) {
            memory.setAlternatingDialogueCount(memory.getAlternatingDialogueCount() + 1);
        } else {
            memory.setAlternatingDialogueCount(0);
        }

        memory.setSecondLastUsedDialogueSubtype(memory.getLastUsedDialogueSubtype());
        memory.setLastUsedDialogueSubtype(interactionKey);

        boolean shouldLockOut = memory.getSessionHeartDelta() <= LOCKOUT_THRESHOLD
                || (memory.getSessionHeartDelta() < 0 && memory.getInteractionFatigue() >= BURNOUT_LOCKOUT_FATIGUE);

        if (shouldLockOut) {
            memory.setRefusingToTalkUntil(villager.asEntity().getWorld().getTime() + TALK_LOCKOUT_DURATION_TICKS);
        }

        String response = dialogueJsonManager
                .getEscalatedResponse(memory.getSessionHeartDelta(), false)
                .orElse(result.npcResponse());

        // Apply placeholders to all NPC responses
        response = applyPlaceholders(response);

        if (isJuvenileVillager() && option.categoryKey().equalsIgnoreCase("rumors") && memory.getSessionHeartDelta() > ANNOYED_THRESHOLD) {
            response = applyKidRumorNameFormatting(response);
        }

        sendVillagerChat(response);

        if (shouldLockOut) {
            close();
            return;
        }

        openDialogueCategory(selectedTalkCategory);
    }

    private boolean isTalkLockedOut(Memories memory) {
        long refusingToTalkUntil = memory.getRefusingToTalkUntil();
        if (refusingToTalkUntil <= 0L) {
            return false;
        }

        long worldTime = villager.asEntity().getWorld().getTime();
        if (worldTime < refusingToTalkUntil) {
            return true;
        }

        memory.setRefusingToTalkUntil(0L);
        memory.setSessionHeartDelta(0);
        return false;
    }

    /**
     * Returns the player's MCA-chosen name, falling back to gamertag.
     */
    private String getPlayerMCAName() {
        return MCAClient.getPlayerData(player.getUuid())
                .map(d -> d.getTrackedValue(VillagerLike.VILLAGER_NAME))
                .filter(n -> n != null && !n.isBlank())
                .orElse(player.getName().getString());
    }

    /**
     * Replaces {player}, {npc}, and {village} placeholders in any string.
     */
    private String applyPlaceholders(String message) {
        if (message == null) return "";
        return message
                .replace("{player}", getPlayerMCAName())
                .replace("{npc}", villager.asEntity().getName().getString())
                .replace("{village}", villageName.isEmpty() ? "the village" : villageName);
    }

    private String applyKidRumorNameFormatting(String message) {
        if (message == null || message.isBlank()) {
            return message;
        }

        List<String> nearbyNames = villager.asEntity().getWorld()
                .getEntitiesByClass(VillagerEntityMCA.class,
                        new Box(villager.asEntity().getBlockPos()).expand(20),
                        v -> !v.getUuid().equals(villager.asEntity().getUuid()))
                .stream()
                .map(v -> v.getName().getString())
                .distinct()
                .limit(3)
                .toList();

        String first = nearbyNames.size() > 0 ? nearbyNames.get(0) : "Alex";
        String second = nearbyNames.size() > 1 ? nearbyNames.get(1) : "Sam";
        String third = nearbyNames.size() > 2 ? nearbyNames.get(2) : "Jamie";

        return message.replace("{npc1}", first)
                .replace("{npc2}", second)
                .replace("{npc3}", third);
    }

    /**
     * Sends the player's spoken dialogue line to chat (two-sided conversation feel).
     */
    private void sendPlayerChat(String message) {
        String resolved = applyPlaceholders(message);
        MutableText name = Text.literal(getPlayerMCAName()).formatted(Formatting.AQUA);
        MutableText separator = Text.literal(": ").formatted(Formatting.GRAY);
        MutableText body = Text.literal(resolved).formatted(Formatting.WHITE);
        player.sendMessage(name.append(separator).append(body), false);
    }

    private void sendVillagerChat(String message) {
        MutableText name = Text.literal(villager.asEntity().getName().getString()).formatted(Formatting.GOLD);
        MutableText separator = Text.literal(": ").formatted(Formatting.GRAY);
        MutableText body = Text.literal(message).formatted(Formatting.GRAY);
        player.sendMessage(name.append(separator).append(body), false);
    }

    private boolean isJuvenileVillager() {
        AgeState ageState = villager.getAgeState();
        return ageState == AgeState.BABY || ageState == AgeState.TODDLER || ageState == AgeState.CHILD;
    }

    private boolean isBabyVillager() {
        return villager.getAgeState() == AgeState.BABY;
    }

    /**
     * Maps all MCA and vanilla professions to NpcJob.
     */
    private NpcJob toNpcJob() {
        if (isNpcLeader) {
            return NpcJob.VILLAGE_LEADER;
        }

        VillagerProfession job = villager.getVillagerData().getProfession();

        // MCA custom professions
        if (job == ProfessionsMCA.GUARD.get()) return NpcJob.GUARD;
        if (job == ProfessionsMCA.ARCHER.get()) return NpcJob.ARCHER;
        if (job == ProfessionsMCA.ADVENTURER.get()) return NpcJob.ADVENTURER;
        if (job == ProfessionsMCA.MERCENARY.get()) return NpcJob.MERCENARY;
        if (job == ProfessionsMCA.OUTLAW.get()) return NpcJob.OUTLAW;
        if (job == ProfessionsMCA.CULTIST.get()) return NpcJob.CULTIST;

        // Vanilla professions
        if (job == VillagerProfession.FARMER) return NpcJob.FARMER;
        if (job == VillagerProfession.LIBRARIAN) return NpcJob.LIBRARIAN;
        if (job == VillagerProfession.CLERIC) return NpcJob.CLERIC;
        if (job == VillagerProfession.ARMORER) return NpcJob.ARMORER;
        if (job == VillagerProfession.WEAPONSMITH) return NpcJob.WEAPONSMITH;
        if (job == VillagerProfession.TOOLSMITH) return NpcJob.TOOLSMITH;
        if (job == VillagerProfession.BUTCHER) return NpcJob.BUTCHER;
        if (job == VillagerProfession.LEATHERWORKER) return NpcJob.LEATHERWORKER;
        if (job == VillagerProfession.MASON) return NpcJob.MASON;
        if (job == VillagerProfession.SHEPHERD) return NpcJob.SHEPHERD;
        if (job == VillagerProfession.FISHERMAN) return NpcJob.FISHERMAN;
        if (job == VillagerProfession.FLETCHER) return NpcJob.FLETCHER;
        if (job == VillagerProfession.CARTOGRAPHER) return NpcJob.CARTOGRAPHER;
        if (job == VillagerProfession.NITWIT) return NpcJob.NITWIT;

        return NpcJob.NONE;
    }

    /**
     * Maps MCA Personality to NpcTrait with correct 1:1 semantics.
     * No more lumping ODD/LAZY/GREEDY into SERIOUS.
     */
    private NpcTrait toNpcTrait(Personality personality) {
        return switch (personality) {
            case WITTY, FRIENDLY -> NpcTrait.JOVIAL;
            case PEPPY, ATHLETIC -> NpcTrait.PEPPY;
            case GRUMPY, GLOOMY -> NpcTrait.GRUMPY;
            case FLIRTY -> NpcTrait.FLIRTATIOUS;
            case SHY, SENSITIVE -> NpcTrait.SHY;
            case CONFIDENT -> NpcTrait.SERIOUS;
            case ODD -> NpcTrait.ODD;
            case LAZY -> NpcTrait.LAZY;
            case GREEDY -> NpcTrait.GREEDY;
            case UNASSIGNED -> NpcTrait.NORMAL;
        };
    }

    private NpcMood toNpcMood(String moodName) {
        String normalized = moodName == null ? "" : moodName.toLowerCase(Locale.ENGLISH);
        if (normalized.contains("happy") || normalized.contains("overjoyed") || normalized.contains("fine")) {
            return NpcMood.HAPPY;
        }
        if (normalized.contains("angry") || normalized.contains("mad") || normalized.contains("furious")) {
            return NpcMood.ANGRY;
        }
        if (normalized.contains("sad") || normalized.contains("depressed") || normalized.contains("gloom")) {
            return NpcMood.SAD;
        }
        return NpcMood.NEUTRAL;
    }

    /**
     * DialogueOptionEntry now carries 4 fields:
     *   categoryKey  – the dialogue category (e.g. "greet")
     *   subCategoryId – the sub-category id (e.g. "friendly")
     *   label         – display label for the button (e.g. "Friendly")
     *   playerLine    – the player's spoken line (shown as tooltip, echoed to chat)
     */
    private record DialogueOptionEntry(String categoryKey, String subCategoryId, String label, String playerLine) {
    }

    private enum FamilyRelation {
        SPOUSE, MY_CHILD, MY_PARENT, FAMILY, NONE
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
                Text.literal("Hearts: ").formatted(Formatting.GRAY)
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

    public void setProfessionAndVillage(String profession, String villageName, boolean isNpcLeader,
                                        boolean leaderConvinced, boolean playerIsOwnVillageLeader) {
        this.profession               = profession;
        this.villageName              = villageName;
        this.isNpcLeader              = isNpcLeader;
        this.leaderConvinced          = leaderConvinced;
        this.playerIsOwnVillageLeader = playerIsOwnVillageLeader;
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
