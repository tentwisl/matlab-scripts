package net.mca.entity.interaction.dynamicdialogue;

import net.mca.entity.VillagerEntityMCA;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.random.Random;

import java.util.List;

/**
 * Generates ambient villager-to-villager conversations that nearby players can overhear.
 * Called periodically from VillagerEntityMCA tick logic. Conversations are context-aware
 * based on job, mood, personality, time of day, and weather.
 */
public final class AmbientChatter {

    private static final Random RANDOM = Random.create();
    private static final int CHATTER_RANGE = 24;
    private static final int PLAYER_HEAR_RANGE = 32;

    private AmbientChatter() {
    }

    /**
     * Attempt to generate ambient chatter between this villager and a nearby one.
     * Returns true if chatter occurred (caller should set cooldown).
     */
    public static boolean tryChatter(VillagerEntityMCA speaker, ServerWorld world) {
        List<VillagerEntityMCA> nearby = world.getEntitiesByClass(
                VillagerEntityMCA.class,
                new Box(speaker.getBlockPos()).expand(CHATTER_RANGE),
                v -> !v.getUuid().equals(speaker.getUuid()) && v.isAlive() && !v.isBaby());

        if (nearby.isEmpty()) {
            return false;
        }

        VillagerEntityMCA listener = nearby.get(RANDOM.nextInt(nearby.size()));
        String[] exchange = pickExchange(speaker, listener, world);
        if (exchange == null) {
            return false;
        }

        // Broadcast to nearby players
        List<? extends PlayerEntity> players = world.getPlayers();
        for (PlayerEntity player : players) {
            if (player.squaredDistanceTo(speaker) <= PLAYER_HEAR_RANGE * PLAYER_HEAR_RANGE) {
                sendChatterLine(player, speaker.getName().getString(), exchange[0]);
                sendChatterLine(player, listener.getName().getString(), exchange[1]);
            }
        }

        return true;
    }

    private static void sendChatterLine(PlayerEntity player, String npcName, String line) {
        MutableText name = Text.literal(npcName).formatted(Formatting.YELLOW);
        MutableText sep = Text.literal(": ").formatted(Formatting.DARK_GRAY);
        MutableText body = Text.literal(line).formatted(Formatting.GRAY).formatted(Formatting.ITALIC);
        player.sendMessage(name.append(sep).append(body), false);
    }

    private static String[] pickExchange(VillagerEntityMCA speaker, VillagerEntityMCA listener, ServerWorld world) {
        boolean isRaining = world.isRaining();
        boolean isNight = world.isNight();

        NpcTrait speakerTrait = mapPersonality(speaker);
        NpcTrait listenerTrait = mapPersonality(listener);

        // Build pool of possible exchanges based on context
        // Each exchange is [speaker line, listener line]
        List<String[]> pool = new java.util.ArrayList<>();

        // Universal small talk
        pool.add(new String[]{"Nice day, isn't it?", "Could be worse. Could always be worse."});
        pool.add(new String[]{"Did you sleep well?", "Like a log. You?"});
        pool.add(new String[]{"Quiet morning so far.", "Let's hope it stays that way."});

        // Weather-aware
        if (isRaining) {
            pool.add(new String[]{"This rain won't let up.", "At least the crops will be happy."});
            pool.add(new String[]{"I forgot to bring my things inside.", "Again? You always do that."});
        }

        // Night-aware
        if (isNight) {
            pool.add(new String[]{"Hear that? Something moved out there.", "Probably nothing. ...Probably."});
            pool.add(new String[]{"I should be asleep.", "Then why aren't you?"});
            pool.add(new String[]{"The stars are bright tonight.", "Makes you think about things, doesn't it?"});
        }

        // Job-based exchanges
        addJobExchanges(pool, speaker, listener);

        // Personality-based color
        addPersonalityExchanges(pool, speakerTrait, listenerTrait);

        // Mood-based
        addMoodExchanges(pool, speaker, listener);

        if (pool.isEmpty()) {
            return null;
        }

        return pool.get(RANDOM.nextInt(pool.size()));
    }

    private static void addJobExchanges(List<String[]> pool, VillagerEntityMCA speaker, VillagerEntityMCA listener) {
        String speakerProf = speaker.getVillagerData().getProfession().toString();
        String listenerProf = listener.getVillagerData().getProfession().toString();

        // Guard-related
        if (speakerProf.contains("guard") || speakerProf.contains("archer")) {
            pool.add(new String[]{"Anything suspicious on your end?", "All clear. For now."});
            pool.add(new String[]{"Stay sharp tonight.", "Always am."});
        }

        // Farmer-related
        if (speakerProf.contains("farmer")) {
            pool.add(new String[]{"The wheat's coming in nicely.", "About time. We needed a good harvest."});
            pool.add(new String[]{"My back's killing me from planting.", "That's the farmer's life."});
        }

        // Librarian/Cleric knowledge
        if (speakerProf.contains("librarian") || speakerProf.contains("cleric")) {
            pool.add(new String[]{"I read something interesting last night.", "Oh? Do tell."});
            pool.add(new String[]{"There's more to this world than meets the eye.", "You always say that."});
        }

        // Trade/merchant types
        if (speakerProf.contains("armorer") || speakerProf.contains("weaponsmith") || speakerProf.contains("toolsmith")) {
            pool.add(new String[]{"Business has been steady.", "Better steady than nothing."});
        }

        // Different professions talking shop
        if (!speakerProf.equals(listenerProf)) {
            pool.add(new String[]{"How's your line of work treating you?", "Can't complain. Well, I can, but I won't."});
        }
    }

    private static void addPersonalityExchanges(List<String[]> pool, NpcTrait speakerTrait, NpcTrait listenerTrait) {
        if (speakerTrait == NpcTrait.JOVIAL) {
            pool.add(new String[]{"Want to hear a joke?", "Oh no, not again..."});
            pool.add(new String[]{"Life's too short to be serious!", "Easy for you to say."});
        }

        if (speakerTrait == NpcTrait.GRUMPY) {
            pool.add(new String[]{"People keep bothering me today.", "Maybe try smiling once in a while."});
            pool.add(new String[]{"Everything's too loud.", "...It's perfectly quiet right now."});
        }

        if (speakerTrait == NpcTrait.ODD) {
            pool.add(new String[]{"Did you know chickens dream about flying?", "...How do you know that?"});
            pool.add(new String[]{"I counted every block in my house. Twice.", "Why?"});
        }

        if (speakerTrait == NpcTrait.PEPPY) {
            pool.add(new String[]{"I have so much energy today!", "I can tell. Please stand still."});
            pool.add(new String[]{"Race you to the well!", "We're adults. ...Fine, you're on."});
        }

        if (speakerTrait == NpcTrait.GREEDY && listenerTrait != NpcTrait.GREEDY) {
            pool.add(new String[]{"I found something valuable yesterday.", "And you're not sharing, are you?"});
        }

        if (speakerTrait == NpcTrait.SHY) {
            pool.add(new String[]{"Um... nice weather.", "You okay? You seem nervous."});
        }

        if (speakerTrait == NpcTrait.FLIRTATIOUS && listenerTrait != NpcTrait.GRUMPY) {
            pool.add(new String[]{"You look nice today.", "Oh, stop it. ...But don't actually stop."});
        }

        if (speakerTrait == NpcTrait.LAZY) {
            pool.add(new String[]{"I need a nap.", "You just woke up an hour ago."});
            pool.add(new String[]{"Work is overrated.", "Someone's gotta keep the village running."});
        }
    }

    private static void addMoodExchanges(List<String[]> pool, VillagerEntityMCA speaker, VillagerEntityMCA listener) {
        String speakerMood = speaker.getVillagerBrain().getMood().getName().toLowerCase(java.util.Locale.ENGLISH);
        String listenerMood = listener.getVillagerBrain().getMood().getName().toLowerCase(java.util.Locale.ENGLISH);

        if (speakerMood.contains("happy") && listenerMood.contains("sad")) {
            pool.add(new String[]{"Cheer up! Things will get better.", "Easy for you to say..."});
        }

        if (speakerMood.contains("sad")) {
            pool.add(new String[]{"I've been feeling down lately.", "Want to talk about it?"});
        }

        if (speakerMood.contains("angry") && listenerMood.contains("angry")) {
            pool.add(new String[]{"This village is driving me crazy.", "Tell me about it."});
        }
    }

    private static NpcTrait mapPersonality(VillagerEntityMCA villager) {
        var personality = villager.getVillagerBrain().getPersonality();
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
}
