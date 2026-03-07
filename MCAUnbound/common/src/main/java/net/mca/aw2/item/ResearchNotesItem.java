package net.mca.aw2.item;

import net.mca.aw2.research.ResearchGoal;
import net.mca.aw2.research.ResearchManager;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Research Notes item. Contains information about a specific research goal.
 * Right-clicking with this item teaches the player about that research,
 * allowing them to then research it at the Research Table.
 *
 * Ported from AW2's ItemResearchNotes.
 */
public class ResearchNotesItem extends Item {
    public ResearchNotesItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (!world.isClient() && world instanceof ServerWorld serverWorld) {
            NbtCompound nbt = stack.getNbt();
            if (nbt != null && nbt.contains("ResearchId")) {
                Identifier researchId = new Identifier(nbt.getString("ResearchId"));
                ResearchManager manager = ResearchManager.get(serverWorld);

                if (manager.hasCompleted(user.getUuid(), researchId)) {
                    user.sendMessage(Text.literal("§cYou have already completed this research."), true);
                } else {
                    Map<Identifier, ResearchGoal> tree = manager.getResearchTree();
                    ResearchGoal goal = tree.get(researchId);
                    if (goal != null) {
                        user.sendMessage(Text.literal(String.format(
                                "§6[Research Notes]§r %s — %s | Category: %s | Time: %d ticks",
                                goal.getDisplayName(), goal.getDescription(),
                                goal.getCategory().getDisplayName(), goal.getResearchTime()
                        )), false);
                    }
                }
            } else {
                user.sendMessage(Text.literal("§cThese research notes are blank."), true);
            }
        }

        return TypedActionResult.success(stack, world.isClient());
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        NbtCompound nbt = stack.getNbt();
        if (nbt != null && nbt.contains("ResearchId")) {
            tooltip.add(Text.literal("Research: " + nbt.getString("ResearchName"))
                    .formatted(Formatting.GOLD));
            tooltip.add(Text.literal("Right-click to read")
                    .formatted(Formatting.GRAY));
        } else {
            tooltip.add(Text.literal("Blank research notes")
                    .formatted(Formatting.GRAY));
        }
    }

    /**
     * Creates research notes for a specific research goal.
     */
    public static ItemStack createForResearch(ResearchGoal goal) {
        ItemStack stack = new ItemStack(net.mca.aw2.AW2Items.RESEARCH_NOTES.get());
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putString("ResearchId", goal.getId().toString());
        nbt.putString("ResearchName", goal.getDisplayName());
        return stack;
    }
}
