package net.mca.server.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.Memories;
import net.mca.nation.MCAUnboundConfig;
import net.mca.nation.NationManager;
import net.mca.server.world.data.Village;
import net.mca.server.world.data.VillageManager;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.UUID;

/**
 * Debug / admin commands for the MCA Unbound nation system.
 * <p>
 * Usage:
 * <pre>
 *   /mca-nation hearts get &lt;villagerUUID&gt;
 *   /mca-nation hearts set &lt;villagerUUID&gt; &lt;value&gt;
 *   /mca-nation reputation &lt;villageId&gt;
 *   /mca-nation setleader &lt;villageId&gt;
 *   /mca-nation setpresidentterm &lt;days&gt;
 *   /mca-nation villageinfo &lt;villageId&gt;
 *   /mca-nation config &lt;key&gt; &lt;value&gt;
 *   /mca-nation mail &lt;message&gt;
 *   /mca-nation debug
 * </pre>
 */
public class NationCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("mca-nation")
                .requires(s -> s.hasPermissionLevel(2))

                // /mca-nation hearts get <villagerUUID>
                .then(CommandManager.literal("hearts")
                        .then(CommandManager.literal("get")
                                .then(CommandManager.argument("villagerUUID", StringArgumentType.string())
                                        .executes(NationCommand::heartsGet)))
                        .then(CommandManager.literal("set")
                                .then(CommandManager.argument("villagerUUID", StringArgumentType.string())
                                        .then(CommandManager.argument("value", IntegerArgumentType.integer(-1000, 1000))
                                                .executes(NationCommand::heartsSet)))))

                // /mca-nation reputation <villageId>
                .then(CommandManager.literal("reputation")
                        .then(CommandManager.argument("villageId", IntegerArgumentType.integer())
                                .executes(NationCommand::reputation)))

                // /mca-nation setleader <villageId>
                .then(CommandManager.literal("setleader")
                        .then(CommandManager.argument("villageId", IntegerArgumentType.integer())
                                .executes(NationCommand::setLeader)))

                // /mca-nation setpresidentterm <days>
                .then(CommandManager.literal("setpresidentterm")
                        .then(CommandManager.argument("days", IntegerArgumentType.integer(1, 10000))
                                .executes(NationCommand::setPresidentTerm)))

                // /mca-nation villageinfo <villageId>
                .then(CommandManager.literal("villageinfo")
                        .then(CommandManager.argument("villageId", IntegerArgumentType.integer())
                                .executes(NationCommand::villageInfo)))

                // /mca-nation config <key> <value>
                .then(CommandManager.literal("config")
                        .then(CommandManager.argument("key", StringArgumentType.string())
                                .then(CommandManager.argument("value", IntegerArgumentType.integer())
                                        .executes(NationCommand::configSet))))

                // /mca-nation mail <message>
                .then(CommandManager.literal("mail")
                        .then(CommandManager.argument("message", StringArgumentType.greedyString())
                                .executes(NationCommand::sendMail)))

                // /mca-nation debug (dump state)
                .then(CommandManager.literal("debug")
                        .executes(NationCommand::debug))
        );

        // Alias: /SetPresidentTerm <days>
        dispatcher.register(CommandManager.literal("SetPresidentTerm")
                .requires(s -> s.hasPermissionLevel(2))
                .then(CommandManager.argument("days", IntegerArgumentType.integer(1, 10000))
                        .executes(NationCommand::setPresidentTerm)));
    }

    // ── Hearts ────────────────────────────────────────────────────────────────

    private static int heartsGet(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        String uuidStr = StringArgumentType.getString(ctx, "villagerUUID");
        try {
            UUID uuid = UUID.fromString(uuidStr);
            Entity entity = player.getServerWorld().getEntity(uuid);
            if (entity instanceof VillagerEntityMCA villager) {
                Memories mem = villager.getVillagerBrain().getMemoriesForPlayer(player);
                msg(ctx, "Hearts with " + villager.getName().getString() + ": " + mem.getHearts(), Formatting.GREEN);
            } else {
                msg(ctx, "Villager not found: " + uuidStr, Formatting.RED);
            }
        } catch (IllegalArgumentException e) {
            msg(ctx, "Invalid UUID: " + uuidStr, Formatting.RED);
        }
        return 1;
    }

    private static int heartsSet(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        String uuidStr = StringArgumentType.getString(ctx, "villagerUUID");
        int value = IntegerArgumentType.getInteger(ctx, "value");
        try {
            UUID uuid = UUID.fromString(uuidStr);
            Entity entity = player.getServerWorld().getEntity(uuid);
            if (entity instanceof VillagerEntityMCA villager) {
                Memories mem = villager.getVillagerBrain().getMemoriesForPlayer(player);
                int delta = value - mem.getHearts();
                mem.modHearts(delta);
                msg(ctx, "Set hearts with " + villager.getName().getString() + " to " + value, Formatting.GREEN);
            } else {
                msg(ctx, "Villager not found: " + uuidStr, Formatting.RED);
            }
        } catch (IllegalArgumentException e) {
            msg(ctx, "Invalid UUID: " + uuidStr, Formatting.RED);
        }
        return 1;
    }

    // ── Reputation ────────────────────────────────────────────────────────────

    private static int reputation(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        int villageId = IntegerArgumentType.getInteger(ctx, "villageId");
        VillageManager vm = VillageManager.get(player.getServerWorld());
        Village village = null;
        for (Village v : vm) {
            if (v.getId() == villageId) { village = v; break; }
        }
        if (village == null) {
            msg(ctx, "Village ID " + villageId + " not found.", Formatting.RED);
        } else {
            msg(ctx, "Village '" + village.getName() + "' (ID " + villageId + ")", Formatting.YELLOW);
            msg(ctx, "  Population: " + village.getPopulation(), Formatting.GRAY);
            // Reputation would require iterating villagers — placeholder for now
            msg(ctx, "  (Use /mca-nation hearts get <uuid> per villager to check)", Formatting.GRAY);
        }
        return 1;
    }

    // ── Set Leader ────────────────────────────────────────────────────────────

    private static int setLeader(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        int villageId = IntegerArgumentType.getInteger(ctx, "villageId");
        msg(ctx, "DEBUG: Forcibly set player as leader of village " + villageId, Formatting.GREEN);
        // TODO: wire to actual village leadership data once town hall flow is built
        return 1;
    }

    // ── Set President Term ────────────────────────────────────────────────────

    private static int setPresidentTerm(CommandContext<ServerCommandSource> ctx) {
        int days = IntegerArgumentType.getInteger(ctx, "days");
        MCAUnboundConfig.get().presidentTermLengthDays = days;
        msg(ctx, "President term length set to " + days + " Minecraft days.", Formatting.GREEN);
        return 1;
    }

    // ── Village Info ──────────────────────────────────────────────────────────

    private static int villageInfo(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        int villageId = IntegerArgumentType.getInteger(ctx, "villageId");
        VillageManager vm = VillageManager.get(player.getServerWorld());
        Village village = null;
        for (Village v : vm) {
            if (v.getId() == villageId) { village = v; break; }
        }
        if (village == null) {
            msg(ctx, "Village ID " + villageId + " not found.", Formatting.RED);
        } else {
            msg(ctx, "=== Village: " + village.getName() + " (ID " + villageId + ") ===", Formatting.GOLD);
            msg(ctx, "  Population:  " + village.getPopulation(), Formatting.WHITE);
            msg(ctx, "  Buildings:   " + village.getBuildings().size(), Formatting.WHITE);
            msg(ctx, "  Center:      " + village.getCenter(), Formatting.WHITE);
        }
        return 1;
    }

    // ── Config ────────────────────────────────────────────────────────────────

    private static int configSet(CommandContext<ServerCommandSource> ctx) {
        String key = StringArgumentType.getString(ctx, "key");
        int value = IntegerArgumentType.getInteger(ctx, "value");
        MCAUnboundConfig cfg = MCAUnboundConfig.get();
        switch (key) {
            case "residentHeartThreshold"         -> cfg.residentHeartThreshold = value;
            case "leaderHeartThreshold"           -> cfg.leaderHeartThreshold = value;
            case "leaderElectionWaitDays"         -> cfg.leaderElectionWaitDays = value;
            case "leaderElectionBaseFailChance"   -> cfg.leaderElectionBaseFailChance = value;
            case "nationFormationLeaderHeartThreshold" -> cfg.nationFormationLeaderHeartThreshold = value;
            case "minVillagesForNation"           -> cfg.minVillagesForNation = value;
            case "presidentTermLengthDays"        -> cfg.presidentTermLengthDays = value;
            case "congressionalRepsPerVillage"    -> cfg.congressionalRepsPerVillage = value;
            case "newbornStartingHearts"          -> cfg.newbornStartingHearts = value;
            case "favourQuestHeartsGain"          -> cfg.favourQuestHeartsGain = value;
            case "villageAutoClaimRadiusBase"     -> cfg.villageAutoClaimRadiusBase = value;
            case "defaultNationPopCap"            -> cfg.defaultNationPopCap = value;
            default -> { msg(ctx, "Unknown config key: " + key, Formatting.RED); return 0; }
        }
        msg(ctx, "Config '" + key + "' set to " + value, Formatting.GREEN);
        return 1;
    }

    // ── Mail ──────────────────────────────────────────────────────────────────

    private static int sendMail(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        String message = StringArgumentType.getString(ctx, "message");
        // Use MCA's built-in mail system
        net.mca.server.world.data.PlayerSaveData data = net.mca.server.world.data.PlayerSaveData.get(player);
        data.sendLetter(java.util.List.of(Text.Serializer.toJson(Text.literal(message))));
        msg(ctx, "Mail sent: " + message, Formatting.GREEN);
        return 1;
    }

    // ── Debug Dump ────────────────────────────────────────────────────────────

    private static int debug(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        ServerWorld world = player.getServerWorld();

        NationManager nm = NationManager.get(world);
        VillageManager vm = VillageManager.get(world);

        msg(ctx, "=== MCA Unbound Debug ===", Formatting.GOLD);
        msg(ctx, "Nations: " + nm.getAllNations().size(), Formatting.WHITE);
        msg(ctx, "AI nations seeded: " + nm.isAiNationsSeeded(), Formatting.WHITE);

        int villageCount = 0;
        for (Village v : vm) {
            villageCount++;
            msg(ctx, "  Village '" + v.getName() + "' ID=" + v.getId()
                    + " pop=" + v.getPopulation() + " buildings=" + v.getBuildings().size(), Formatting.GRAY);
        }
        msg(ctx, "Total villages: " + villageCount, Formatting.WHITE);

        MCAUnboundConfig cfg = MCAUnboundConfig.get();
        msg(ctx, "Config: residentHeart=" + cfg.residentHeartThreshold
                + " leaderHeart=" + cfg.leaderHeartThreshold
                + " presidentTerm=" + cfg.presidentTermLengthDays + "d", Formatting.AQUA);

        return 1;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void msg(CommandContext<ServerCommandSource> ctx, String text, Formatting color) {
        ctx.getSource().sendFeedback(() -> Text.literal(text).formatted(color), false);
    }
}
