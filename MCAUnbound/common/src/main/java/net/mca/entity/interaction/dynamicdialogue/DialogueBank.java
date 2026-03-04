package net.mca.entity.interaction.dynamicdialogue;

import net.minecraft.util.math.random.Random;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class DialogueBank {
    private static final Random RANDOM = Random.create();

    private static final Map<DialogueSubtype, List<String>> PLAYER_OPTION_POOLS = new EnumMap<>(DialogueSubtype.class);
    private static final Map<ResponsePool, List<String>> NPC_RESPONSE_POOLS = new EnumMap<>(ResponsePool.class);
    private static final Map<NpcJob, List<String>> JOB_FLAVOR_POOLS = new EnumMap<>(NpcJob.class);

    private static final List<String> BURNOUT_RESPONSES = List.of(
            "I've talked enough for now. Maybe later.",
            "My head's spinning. Let's continue another time."
    );

    private static final List<String> REPETITIVE_RESPONSES = List.of(
            "You're repeating yourself.",
            "Please stop saying the same thing over and over."
    );

    static {
        PLAYER_OPTION_POOLS.put(DialogueSubtype.GREET_FRIENDLY, List.of("Hey! It's always good seeing you.", "Morning! Hope your day is going well."));
        PLAYER_OPTION_POOLS.put(DialogueSubtype.GREET_FORMAL, List.of("Good day to you, friend.", "Greetings. I trust your duties are going smoothly."));
        PLAYER_OPTION_POOLS.put(DialogueSubtype.GREET_CASUAL, List.of("Yo, how's it going?", "Hey there, what's up?"));
        PLAYER_OPTION_POOLS.put(DialogueSubtype.GREET_BOLD, List.of("There you are—I've been looking for you.", "Move aside, favorite villager coming through."));

        PLAYER_OPTION_POOLS.put(DialogueSubtype.JOKE_CHEESY, List.of("Are you redstone? Because you light up my circuits.", "I tried to bake bread today... the wheat said no."));
        PLAYER_OPTION_POOLS.put(DialogueSubtype.JOKE_DARK, List.of("At least the creeper only blew up my plans this time.", "If my luck gets worse, the void might start charging rent."));
        PLAYER_OPTION_POOLS.put(DialogueSubtype.JOKE_SELF_DEPRECATING, List.of("I got lost in my own house again.", "I missed a zombie with a bow at point-blank range."));
        PLAYER_OPTION_POOLS.put(DialogueSubtype.JOKE_META, List.of("Feels like someone keeps clicking dialogue options for me.", "Do you ever feel like our lives are made of code?"));

        PLAYER_OPTION_POOLS.put(DialogueSubtype.STORY_HEROIC, List.of("I held the gate all night while the village slept.", "I once crossed a ravine to rescue a trapped trader."));
        PLAYER_OPTION_POOLS.put(DialogueSubtype.STORY_TRAGIC, List.of("I lost everything in a raid, but kept moving forward.", "My first home burned down before sunrise."));
        PLAYER_OPTION_POOLS.put(DialogueSubtype.STORY_HUMOROUS, List.of("I fought a skeleton with a fish and somehow won.", "I tried taming a wolf and got adopted by three cats instead."));
        PLAYER_OPTION_POOLS.put(DialogueSubtype.STORY_MYSTERIOUS, List.of("I found footprints in snow that ended at a stone wall.", "At midnight, I heard bells in an empty cave."));

        PLAYER_OPTION_POOLS.put(DialogueSubtype.ROMANCE_SWEET, List.of("You make this whole village feel warmer.", "I feel calmer whenever I'm with you."));
        PLAYER_OPTION_POOLS.put(DialogueSubtype.ROMANCE_CHEESY, List.of("If hearts were emeralds, you'd be rich by now.", "Even a beacon isn't as bright as your smile."));
        PLAYER_OPTION_POOLS.put(DialogueSubtype.ROMANCE_BOLD, List.of("Come closer—I want this moment to be ours.", "Let's skip the small talk and enjoy each other."));
        PLAYER_OPTION_POOLS.put(DialogueSubtype.ROMANCE_SUGGESTIVE_ACTION, List.of("[Wink playfully]", "[Brush their shoulder]"));

        PLAYER_OPTION_POOLS.put(DialogueSubtype.CHAT_WORK, List.of("How's work at your station going today?", "Got any tough jobs lined up?"));
        PLAYER_OPTION_POOLS.put(DialogueSubtype.CHAT_VILLAGE, List.of("Any news from around the village square?", "How are folks holding up this week?"));
        PLAYER_OPTION_POOLS.put(DialogueSubtype.CHAT_WEATHER, List.of("Looks like rain by sundown, huh?", "Perfect weather for staying indoors and crafting."));
        PLAYER_OPTION_POOLS.put(DialogueSubtype.CHAT_PERSONAL, List.of("How have you been feeling lately?", "Anything on your mind today?"));

        PLAYER_OPTION_POOLS.put(DialogueSubtype.RUMORS_SPOOKY, List.of("People say the old mine has lights at night.", "I heard whispers near the cemetery after dark."));
        PLAYER_OPTION_POOLS.put(DialogueSubtype.RUMORS_DRAMA, List.of("Someone's been arguing at the market every morning.", "There's talk that two families won't trade with each other."));
        PLAYER_OPTION_POOLS.put(DialogueSubtype.RUMORS_TREASURE, List.of("A map fragment points to loot under the ruined tower.", "A traveler swore there's gold hidden by the river bend."));
        PLAYER_OPTION_POOLS.put(DialogueSubtype.RUMORS_WARNING, List.of("Scouts spotted danger near the northern path.", "I heard hostile mobs are gathering after sunset."));

        for (MainDialogueCategory category : MainDialogueCategory.values()) {
            NPC_RESPONSE_POOLS.put(ResponsePool.of(category, ReactionType.POSITIVE), List.of("That was lovely to hear.", "You always know what to say."));
            NPC_RESPONSE_POOLS.put(ResponsePool.of(category, ReactionType.NEUTRAL), List.of("Hmm... I see.", "Alright, noted."));
            NPC_RESPONSE_POOLS.put(ResponsePool.of(category, ReactionType.NEGATIVE), List.of("That didn't sit right with me.", "Let's talk about something else."));
            NPC_RESPONSE_POOLS.put(ResponsePool.of(category, ReactionType.REPETITIVE), REPETITIVE_RESPONSES);
        }

        JOB_FLAVOR_POOLS.put(NpcJob.VILLAGE_LEADER, List.of("Keep it concise—we have village matters to settle.", "If this concerns the town, speak clearly."));
        JOB_FLAVOR_POOLS.put(NpcJob.GUARD, List.of("Stay alert. The roads aren't safe after dusk.", "We can talk, but keep your eyes on the gates."));
        JOB_FLAVOR_POOLS.put(NpcJob.FARMER, List.of("The soil's good today. Rain might help tomorrow.", "Harvest season decides everything around here."));
        JOB_FLAVOR_POOLS.put(NpcJob.LEATHERWORKER, List.of("Fine leather takes patience and steady hands.", "I've been working hides since dawn."));
        JOB_FLAVOR_POOLS.put(NpcJob.NONE, List.of("I'm just trying to get through the day.", "Work comes and goes. People matter more."));
    }

    private DialogueBank() {
    }

    public static String randomPlayerOption(DialogueSubtype subtype) {
        return randomFromList(PLAYER_OPTION_POOLS.get(subtype));
    }

    public static String randomNpcResponse(MainDialogueCategory category, ReactionType reactionType) {
        return randomFromList(NPC_RESPONSE_POOLS.get(ResponsePool.of(category, reactionType)));
    }

    public static String randomBurnoutResponse() {
        return randomFromList(BURNOUT_RESPONSES);
    }

    public static String randomJobFlavor(NpcJob npcJob) {
        return randomFromList(JOB_FLAVOR_POOLS.get(npcJob));
    }

    private static String randomFromList(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "...";
        }
        return list.get(RANDOM.nextInt(list.size()));
    }

    public enum ResponsePool {
        GREET_POSITIVE,
        GREET_NEUTRAL,
        GREET_NEGATIVE,
        GREET_REPETITIVE,
        JOKE_POSITIVE,
        JOKE_NEUTRAL,
        JOKE_NEGATIVE,
        JOKE_REPETITIVE,
        STORY_POSITIVE,
        STORY_NEUTRAL,
        STORY_NEGATIVE,
        STORY_REPETITIVE,
        ROMANCE_POSITIVE,
        ROMANCE_NEUTRAL,
        ROMANCE_NEGATIVE,
        ROMANCE_REPETITIVE,
        CHAT_POSITIVE,
        CHAT_NEUTRAL,
        CHAT_NEGATIVE,
        CHAT_REPETITIVE,
        RUMORS_POSITIVE,
        RUMORS_NEUTRAL,
        RUMORS_NEGATIVE,
        RUMORS_REPETITIVE;

        public static ResponsePool of(MainDialogueCategory category, ReactionType reactionType) {
            return valueOf(category.name() + "_" + reactionType.name());
        }
    }
}
